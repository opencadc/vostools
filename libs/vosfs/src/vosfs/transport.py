"""HTTPX client pool and lifecycle for the vos filesystem.

HTTPX is the sole production HTTP client. The pool is keyed by TLS
configuration and built lazily and concurrency-safely: one validating
no-client-certificate client for anonymous, bearer, and pre-authorized
requests, and — when a certificate is configured — one validating client whose
fresh ``ssl.SSLContext`` loads the combined PEM. Bearer credentials are
per-request headers, never pool keys.
"""

from __future__ import annotations

import asyncio
import contextlib
import ssl
from collections import deque
from typing import TYPE_CHECKING, Any, cast
from urllib.parse import urlsplit

import httpx

if TYPE_CHECKING:
    from collections.abc import AsyncGenerator, Callable, Mapping

# Default HTTP timeouts in seconds, fixed by the contract (section 3).
DEFAULT_TIMEOUTS: dict[str, float] = {
    "connect": 10.0,
    "read": 60.0,
    "write": 60.0,
    "pool": 10.0,
}


def build_timeout(overrides: Mapping[str, float] | None) -> httpx.Timeout:
    """Return an ``httpx.Timeout`` from the defaults plus any overrides."""
    values = dict(DEFAULT_TIMEOUTS)
    if overrides:
        values.update(overrides)
    return httpx.Timeout(
        connect=values["connect"],
        read=values["read"],
        write=values["write"],
        pool=values["pool"],
    )


HTTP_OK = 200
HTTP_BAD_REQUEST = 400
HTTP_CREATED = 201
HTTP_NO_CONTENT = 204
HTTP_PARTIAL_CONTENT = 206
HTTP_RANGE_NOT_SATISFIABLE = 416
HTTP_SEE_OTHER = 303
HTTP_PRECONDITION_FAILED = 412
IDENTITY_ENCODING = {"Accept-Encoding": "identity"}


def same_origin(a: str, b: str) -> bool:
    """Whether two URLs share the same scheme, host, and (defaulted) port."""

    def origin(url: str) -> tuple[str, str | None, int]:
        parts = urlsplit(url)
        port = parts.port or (443 if parts.scheme == "https" else 80)
        return (parts.scheme, parts.hostname, port)

    return origin(a) == origin(b)


class ConnectionBudget:
    """A FIFO weighted semaphore for in-flight HTTP requests.

    Waiting for a slot does not time out. A caller that needs two connections
    (a streamed copy) asks for both at once, so it cannot hold one while
    waiting for the other.
    """

    def __init__(self, limit: int) -> None:
        """Hold at most ``limit`` connection slots."""
        self.limit = limit
        self._available = limit
        self._waiters: deque[tuple[int, asyncio.Future[None]]] = deque()

    async def acquire(self, weight: int = 1) -> None:
        """Take ``weight`` slots, waiting in arrival order until they are free."""
        if weight < 1 or weight > self.limit:
            msg = f"connection weight {weight} is outside 1..{self.limit}"
            raise ValueError(msg)
        if not self._waiters and self._available >= weight:
            self._available -= weight
            return
        future: asyncio.Future[None] = asyncio.get_running_loop().create_future()
        self._waiters.append((weight, future))
        try:
            await future
        except asyncio.CancelledError:
            self._waiters = deque(item for item in self._waiters if item[1] is not future)
            if future.done() and not future.cancelled():
                self._available += weight
            self._wake_waiters()
            raise

    def release(self, weight: int = 1) -> None:
        """Return ``weight`` slots and wake the oldest waiter that now fits."""
        self._available += weight
        self._wake_waiters()

    def _wake_waiters(self) -> None:
        """Grant available slots after either a release or queue cancellation."""
        while self._waiters and self._available >= self._waiters[0][0]:
            needed, future = self._waiters.popleft()
            if future.done():
                continue
            self._available -= needed
            future.set_result(None)


class ClientPool:
    """A lazily-populated, TLS-keyed pool of HTTPX async clients.

    At most one client is created per TLS key per pool and event loop. Every
    client disables redirects, client-level auth, and transport retries, and
    holds no cookie jar: response cookies are discarded after each send so no
    ``Cookie`` header is ever derived from a prior response.
    """

    def __init__(
        self,
        *,
        certfile: str | None,
        trust_env: bool,
        timeout: httpx.Timeout,
        injected_transport: httpx.AsyncBaseTransport | None,
        max_connections: int = 100,
    ) -> None:
        """Configure the pool; no client is built until first use."""
        self._certfile = certfile
        self._trust_env = trust_env
        self._timeout = timeout
        self._injected = injected_transport
        self._max_connections = max_connections
        self._budget = ConnectionBudget(max_connections)
        self._clients: dict[bool, httpx.AsyncClient] = {}
        self._lock = asyncio.Lock()
        self._closed = False

    def _ensure_open(self) -> None:
        if self._closed:
            msg = "I/O operation on closed filesystem"
            raise ValueError(msg)

    async def client(self, *, use_cert: bool = False) -> httpx.AsyncClient:
        """Return the pooled client for the requested TLS configuration.

        Raises:
            ValueError: If the pool is closed, or a certificate client is
                requested without a configured certificate.
        """
        self._ensure_open()
        if use_cert and self._certfile is None:
            msg = "no certificate is configured for a certificate transfer"
            raise ValueError(msg)
        if use_cert in self._clients:
            return self._clients[use_cert]
        async with self._lock:
            # Re-check under the lock: a concurrent ``aclose`` may have run
            # between the fast-path check and lock acquisition, so a client must
            # never be built (and leaked) into an already-closed pool.
            self._ensure_open()
            if use_cert not in self._clients:
                self._clients[use_cert] = self._build(use_cert=use_cert)
            return self._clients[use_cert]

    def _build(self, *, use_cert: bool) -> httpx.AsyncClient:
        """Construct one client for the given TLS configuration."""
        return httpx.AsyncClient(
            transport=self._injected,
            verify=(self._certificate_context() if use_cert and self._injected is None else True),
            follow_redirects=False,
            trust_env=self._trust_env,
            timeout=self._timeout,
            limits=httpx.Limits(
                max_connections=self._max_connections,
                max_keepalive_connections=self._max_connections,
            ),
            auth=None,
        )

    def _certificate_context(self) -> ssl.SSLContext:  # pragma: no cover
        """Load the certificate into every direct and proxy TLS transport."""
        certfile = self._certfile
        if certfile is None:
            msg = "no certificate is configured for a certificate transfer"
            raise ValueError(msg)
        context = ssl.create_default_context()
        context.load_cert_chain(certfile)
        return context

    @contextlib.asynccontextmanager
    async def reserve(self, weight: int = 1) -> AsyncGenerator[None, None]:
        """Hold ``weight`` connection slots until the block exits."""
        await self._budget.acquire(weight)
        try:
            yield
        finally:
            self._budget.release(weight)

    async def send(
        self,
        request: httpx.Request,
        *,
        use_cert: bool = False,
        stream: bool = False,
        accounted: bool = False,
    ) -> httpx.Response:
        """Send one request through the pooled client, holding no cookies.

        The client's cookie jar is cleared after every send so a ``Set-Cookie``
        response can never cause a later request to carry a ``Cookie`` header.
        """
        client = await self.client(use_cert=use_cert)
        if not accounted:
            await self._budget.acquire()
        try:
            response = await client.send(request, stream=stream)
        except BaseException:
            if not accounted:
                self._budget.release()
            raise
        client.cookies.clear()
        if not stream or accounted:
            if not accounted:
                self._budget.release()
            return response
        release = self._budget.release
        original_aclose = response.aclose
        released = False

        async def aclose_and_release() -> None:
            nonlocal released
            try:
                await original_aclose()
            finally:
                if not released:
                    released = True
                    release()

        response.aclose = cast("Any", aclose_and_release)
        return response

    async def aclose(self) -> None:
        """Close every realized client and mark the pool closed (idempotent).

        The closed flag is set and the client map is drained under the same lock
        that guards lazy client creation, so a waiter cannot build and store an
        open client after the pool has been closed.
        """
        async with self._lock:
            self._closed = True
            clients = list(self._clients.values())
            self._clients.clear()
        for client in clients:
            await client.aclose()

    @property
    def closed(self) -> bool:
        """Whether the pool has been closed."""
        return self._closed


class LoopRuntime:
    """Loop-bound clients and locks for one filesystem.

    Directory caches, discovered bindings, the authority and credentials stay
    on the filesystem. This object is discarded, not closed, when a later call
    runs on a different event loop: the previous loop is already dead, so
    closing its sockets would raise.
    """

    def __init__(self, loop: asyncio.AbstractEventLoop | None, pool: ClientPool) -> None:
        """Bind one pool and its locks to ``loop`` (``None`` until a loop runs)."""
        self.loop = loop
        self.pool = pool
        self.bindings_lock = asyncio.Lock()
        self.containers: dict[str, asyncio.Future[None]] = {}


class LoopRuntimes:
    """The single live :class:`LoopRuntime` for one filesystem."""

    def __init__(self, pool_factory: Callable[[], ClientPool]) -> None:
        """Store the factory used to build a pool for each loop."""
        self._pool_factory = pool_factory
        self._current: LoopRuntime | None = None

    def current(self) -> LoopRuntime:
        """Return the runtime for the running loop, rebuilding it when the loop changes."""
        try:
            loop: asyncio.AbstractEventLoop | None = asyncio.get_running_loop()
        except RuntimeError:
            loop = None
        current = self._current
        if current is None:
            self._current = LoopRuntime(loop, self._pool_factory())
            return self._current
        if loop is None or current.loop is None or current.loop is loop:
            if current.loop is None:
                current.loop = loop
            return current
        # A different loop is running. Drop the old clients without closing them.
        self._current = LoopRuntime(loop, self._pool_factory())
        return self._current
