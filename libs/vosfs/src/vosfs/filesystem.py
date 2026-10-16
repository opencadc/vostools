"""The public ``vos`` fsspec filesystem for the OpenCADC VOSpace profile.

This module hosts :class:`VOSpaceFileSystem`, the single public class of the
package. Behaviour is layered across cohesive internal modules (paths,
configuration, transport, and so on); this class composes them into the fsspec
async filesystem contract.
"""

from __future__ import annotations

import asyncio
import datetime
import errno
import functools
import hashlib
import logging
import os
import time
from pathlib import Path
from typing import TYPE_CHECKING, Any, cast, overload
from urllib.parse import quote, urlencode, urlsplit

import httpx
from fsspec.asyn import AsyncFileSystem, _run_coros_in_chunks, sync
from fsspec.callbacks import DEFAULT_CALLBACK

from vosfs import (
    _coordination as coordination,
)
from vosfs import (
    _file,
    _integrity,
    _moving,
    _removal,
    _transfer,
    capabilities,
    config,
    errors,
    nodes,
    paths,
    staging,
    transport,
)
from vosfs.transport import ClientPool, LoopRuntimes, build_timeout

if TYPE_CHECKING:
    import io
    from collections.abc import (
        Mapping,
        Sequence,
    )

    from fsspec.callbacks import Callback

    from vosfs.capabilities import ServiceBindings
    from vosfs.nodes import Node

_SECURITY_METHOD_BY_CREDENTIAL = {
    "anonymous": capabilities.ANONYMOUS_METHOD,
    "token": capabilities.TOKEN_METHOD,
    "certificate": capabilities.CERTIFICATE_METHOD,
}
_GET_CONTAINER_MARKER = "_vosfs_materialize_get_container"
#: Default fsspec bulk concurrency. fsspec otherwise derives it from the open-file
#: limit (often thousands), far beyond the HTTP pool: queued transfers then wait
#: on the pool past its timeout and fail spuriously with ``PoolTimeout``.
DEFAULT_BATCH_SIZE = 32
#: Immediate children requested per container-listing GET. Vault's resume cursor
#: is inclusive, so a page of one child cannot advance. Two thousand ordinary
#: children stay under the 8 MiB XML parse cap.
LISTING_PAGE_SIZE = 2000

logger = logging.getLogger(__name__)


def _query(params: dict[str, str]) -> str:
    """Percent-encode ``params`` so a resume URI is one query value.

    ``quote`` leaves a space as ``%20``. ``urlencode``'s default ``quote_plus`` would emit ``+``.
    """
    return urlencode(params, quote_via=quote)


def _resume_page(
    raw: list[nodes.Node],
    *,
    resume: str,
    first_uri: str,
) -> tuple[list[nodes.Node], str, bool]:
    """Return new children from one later page, the next cursor, and whether to stop.

    Vault's cursor is inclusive. A page that restarts at the first child ignored
    the cursor. A page that starts somewhere else treated the cursor as exclusive.
    """
    if not raw or (raw[0].uri == first_uri and raw[0].uri != resume):
        return [], resume, True
    new_children = raw[1:] if raw[0].uri == resume else list(raw)
    next_resume = raw[-1].uri
    if not new_children or next_resume == resume:
        return [], resume, True
    return new_children, next_resume, len(raw) != LISTING_PAGE_SIZE


class VOSpaceFileSystem(AsyncFileSystem):
    """An asynchronous fsspec filesystem for the OpenCADC VOSpace profile.

    The service is selected only by ``endpoint_url``. The apparent authority
    after the ``vos://`` protocol marker is part of the filesystem path, not a
    service or VOSpace authority.
    """

    protocol = "vos"
    root_marker = "/"
    async_impl = True
    cachable = True
    # ``transport`` is the single internal HTTP transport seam and ``loop`` is a
    # runtime handle; neither is part of the serialized ``storage_options``.
    _strip_tokenize_options = ("transport", "loop")

    def __init__(  # noqa: PLR0913 - the public transport options are the contract (section 3)
        self,
        endpoint_url: str,
        *,
        token: str | None = None,
        tokenfile: str | None = None,
        certfile: str | None = None,
        timeouts: Mapping[str, float] | None = None,
        trust_env: bool = True,
        transport: httpx.AsyncBaseTransport | None = None,
        asynchronous: bool = False,
        loop: Any = None,  # noqa: ANN401 - fsspec passes an opaque event loop
        batch_size: int | None = None,
        max_connections: int = 100,
        **storage_options: Any,  # noqa: ANN401 - fsspec passthrough options
    ) -> None:
        """Construct a filesystem bound to one OpenCADC VOSpace deployment.

        Args:
            endpoint_url: Absolute OpenCADC service base URL.
            token: Literal bearer token, including an OIDC access token.
            tokenfile: File whose bearer token is reread before every request.
            certfile: Combined X.509 certificate-chain and private-key PEM file.
            timeouts: Optional finite positive ``connect``/``read``/``write``/
                ``pool`` inactivity limits.
            trust_env: Whether HTTPX honours proxy and CA environment handling.
            transport: Test-only HTTP transport injected at the internal seam.
                Never appears in ``storage_options``.
            asynchronous: fsspec async-mode flag.
            loop: fsspec event loop handle; not serialized.
            batch_size: fsspec bulk-operation concurrency hint.
            max_connections: How many HTTP connections one event loop may hold.
                A streamed body holds its connection until the response is closed.
            storage_options: Remaining fsspec options (``skip_instance_cache``,
                ``use_listings_cache``, ``listings_expiry_time``, ``max_paths``).
        """
        config.reject_forbidden_options(storage_options)
        if type(max_connections) is not int or max_connections < 1:
            msg = "max_connections must be a positive integer"
            raise ValueError(msg)
        self.max_connections = max_connections
        super().__init__(
            asynchronous=asynchronous,
            loop=loop,
            batch_size=batch_size,
            **storage_options,
        )
        if batch_size is None:
            self.batch_size = DEFAULT_BATCH_SIZE
        self._credential = config.resolve_credential(
            token=token,
            tokenfile=tokenfile,
            certfile=certfile,
            environ=os.environ,
        )
        self.endpoint_url = config.validate_endpoint(
            endpoint_url,
            has_credential=self._credential.method != "anonymous",
        )
        self.timeouts = config.resolve_timeouts(timeouts)
        self.trust_env = trust_env
        self._closed = False
        self._runtimes = LoopRuntimes(
            lambda: ClientPool(
                certfile=self._credential.certfile,
                trust_env=trust_env,
                timeout=build_timeout(self.timeouts),
                injected_transport=transport,
                max_connections=max_connections,
            ),
        )
        self._bindings: ServiceBindings | None = None
        self._authority: str | None = None
        self._listing_indexes: dict[str, tuple[int, dict[str, dict[str, Any]]]] = {}
        self._redirect_reads_unsupported = False

    @property
    def _pool(self) -> ClientPool:
        """Return the HTTP client pool for the running event loop."""
        return self._runtimes.current().pool

    @property
    def _bindings_lock(self) -> asyncio.Lock:
        """Return the bindings lock for the running event loop."""
        return self._runtimes.current().bindings_lock

    def _ensure_usable(self) -> None:
        """Reject closed or fork-inherited runtime state before using it."""
        if self._pid != os.getpid():
            msg = (
                "VOSpaceFileSystem cannot be used after fork; reconstruct it "
                "in the child process from pickle or fsspec JSON"
            )
            raise RuntimeError(msg)
        if self._closed or self._pool.closed:
            msg = "I/O operation on closed filesystem"
            raise ValueError(msg)

    def __dask_tokenize__(self) -> tuple[Any, tuple[Any, ...], dict[str, Any]]:
        """Tokenize primitive constructor state, independent of worker identity."""
        return type(self), self.storage_args, self.storage_options

    def __reduce__(self) -> tuple[Any, tuple[Any, ...]]:
        """Reconstruct outside fsspec's live instance cache."""
        constructor, (cls, args, options) = super().__reduce__()
        return constructor, (cls, args, {**options, "skip_instance_cache": True})

    def to_dict(self, *, include_password: bool = True) -> dict[str, Any]:
        """Serialize constructor state for fresh fsspec reconstruction.

        ``include_password=False`` drops a literal ``token``. fsspec only
        removes the key ``password``, so a bearer token would otherwise be
        written into catalogs and JSON dumps. Pickle and the default
        ``include_password=True`` keep the token, so a reconstructed
        filesystem can still authenticate.
        """
        state = super().to_dict(include_password=include_password)
        state["skip_instance_cache"] = True
        if not include_password:
            state.pop("token", None)
        return state

    @overload
    @classmethod
    def _strip_protocol(cls, path: str) -> str: ...

    @overload
    @classmethod
    def _strip_protocol(cls, path: list[str]) -> list[str]: ...

    @classmethod
    def _strip_protocol(cls, path: str | list[str]) -> str | list[str]:
        """Normalize a user path, or a list of paths, without percent-decoding.

        fsspec's bulk coordinators (for example ``get`` with a list of sources)
        forward a list through this hook and may apply it more than once. Each
        element is normalized independently, and a second call leaves the path
        unchanged.
        """
        if isinstance(path, list):
            return [paths.strip_protocol(item) for item in path]
        return paths.strip_protocol(path)

    def _security_method(self) -> str:
        """Return the security-method identifier for the configured credential."""
        return _SECURITY_METHOD_BY_CREDENTIAL[self._credential.method]

    async def _get_bindings(self) -> ServiceBindings:
        """Return the service bindings, discovering them once per instance.

        The binding cache is immutable for the instance: it is fetched on first
        I/O, is never refreshed by directory-cache invalidation, and is rebuilt
        only by reconstruction.
        """
        self._ensure_usable()
        if self._bindings is not None:
            return self._bindings
        async with self._bindings_lock:
            if self._bindings is None:
                self._bindings = await self._discover_bindings()
            return self._bindings

    async def _discover_bindings(self) -> ServiceBindings:
        """Fetch and parse the VOSI capabilities document."""
        response = await self._send_to_service("GET", self.endpoint_url + "/capabilities")
        self._raise_for_status(response, path="/capabilities", allowed=(transport.HTTP_OK,))
        return capabilities.parse_bindings(
            response.content,
            security_method=self._security_method(),
            endpoint_url=self.endpoint_url,
        )

    async def _send_to_service(
        self,
        method: str,
        url: str,
        *,
        content: bytes | None = None,
        headers: Mapping[str, str] | None = None,
        stream: bool = False,
    ) -> httpx.Response:
        """Send a request to the service, applying the credential same-origin only.

        The configured credential is attached only when ``url`` is same-origin as
        ``endpoint_url``: a bearer header for the token method, or the certificate
        client for the certificate method. A redirect ``Location`` (for example
        the transfer-details URL) therefore can never route a bearer token or
        client certificate to another host.
        """
        self._ensure_usable()
        request_headers = dict(headers or {})
        use_cert = False
        if transport.same_origin(url, self.endpoint_url):
            if self._credential.method == "token":
                bearer = self._credential.read_bearer()
                request_headers["Authorization"] = f"Bearer {bearer}"
            elif self._credential.method == "certificate":
                use_cert = True
        request = httpx.Request(method, url, headers=request_headers, content=content)
        try:
            return await self._pool.send(request, use_cert=use_cert, stream=stream)
        except httpx.HTTPError as exc:
            raise errors.transport_exception(exc, path=url) from exc

    def _raise_for_status(
        self,
        response: httpx.Response,
        *,
        path: str,
        allowed: tuple[int, ...],
    ) -> None:
        """Raise the mapped exception when a response status is not allowed."""
        if response.status_code not in allowed:
            body = errors.bounded_text(response.content)
            raise errors.http_exception(
                response.status_code,
                body=body,
                fault=errors.extract_fault(body),
                path=path,
                retry_after=errors.parse_retry_after(response.headers.get("retry-after")),
            )

    # -- node metadata and listing -------------------------------------------

    async def _get_node_document(self, path: str, *, children: bool = True) -> bytes:
        """GET the node document for ``path`` or raise the mapped exception.

        With ``children=False`` the request carries VOSpace ``limit=0`` so a
        container answers with its own metadata only. Metadata lookups must not
        download (and parse) a container's entire child listing: that costs
        time proportional to the directory size and fails outright once the
        listing exceeds the XML parse limit. A service that ignores ``limit``
        returns the children, which parsing a single node simply skips.
        """
        bindings = await self._get_bindings()
        url = bindings.require_nodes() + paths.encode_url_path(path)
        if not children:
            url += "?" + _query({"limit": "0"})
        response = await self._send_to_service("GET", url, headers=nodes.XML_HEADERS)
        self._raise_for_status(response, path=path, allowed=(transport.HTTP_OK,))
        return response.content

    def _parse_and_note(self, data: bytes) -> Node:
        """Parse a node document and record or validate its VOSpace authority."""
        node = nodes.parse_node(data)
        self._note_authority(node.uri)
        return node

    def _note_authority(self, uri: str) -> None:
        """Record the VOSpace authority on first sight, then require it to match.

        The authority is discovered from the URI returned by the root node,
        cached per instance, and every later node URI must carry the same one.
        """
        authority = _authority_of(uri)
        if self._authority is None:
            self._authority = authority
        elif authority != self._authority:
            msg = f"node URI authority {authority!r} does not match {self._authority!r}"
            raise errors.VOSpaceError(msg)

    async def _info(self, path: str, **_kwargs: Any) -> dict[str, Any]:  # noqa: ANN401 - fsspec hook signature
        """Return the fsspec metadata for ``path`` or raise ``FileNotFoundError``.

        An entry already present in its parent's cached listing is returned
        from the directory cache, which mutations invalidate; otherwise one
        childless node GET answers.
        """
        path = self._strip_protocol(path)
        cached = self._cached_info(path)
        if cached is not None:
            return cached
        node = self._parse_and_note(await self._get_node_document(path, children=False))
        return nodes.to_info(node, path)

    def _cached_info(self, path: str) -> dict[str, Any] | None:
        """Return a copy of ``path``'s entry from its parent's cached listing."""
        if path == "/":
            return None
        parent = paths.parent(path)
        try:
            siblings = self.dircache[parent]
        except KeyError:
            self._listing_indexes.pop(parent, None)
            return None
        entry = self._listing_index(parent, siblings).get(path)
        if entry is None:
            return None
        return dict(entry)

    def _listing_index(self, parent: str, siblings: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
        """Return a name-to-entry map for one cached listing, rebuilt when replaced."""
        identity = id(siblings)
        recorded = self._listing_indexes.get(parent)
        if recorded is not None and recorded[0] == identity:
            return recorded[1]
        built = {str(entry["name"]): entry for entry in siblings}
        self._listing_indexes[parent] = (identity, built)
        return built

    async def _ls(
        self,
        path: str,
        detail: bool = True,  # noqa: FBT001, FBT002 - fsspec hook signature
        refresh: bool = False,  # noqa: FBT001, FBT002 - fsspec hook signature
        **_kwargs: Any,  # noqa: ANN401 - fsspec hook signature
    ) -> list[Any]:
        """List the immediate children of ``path`` (or the node itself if a file).

        Container listings are assembled from ``limit`` pages and then served
        from and stored in the standard fsspec directory cache; mutations
        invalidate the affected entries. ``refresh=True`` skips that cache.
        The returned list and its dicts are copies, so a caller cannot mutate
        the cache by editing the result.
        """
        self._ensure_usable()
        path = self._strip_protocol(path)
        if refresh:
            self.dircache.pop(path, None)
            self._listing_indexes.pop(path, None)
        try:
            entries = self.dircache[path]
        except KeyError:
            entries = await self._fetch_listing(path)
        if detail:
            return [dict(entry) for entry in entries]
        return [entry["name"] for entry in entries]

    async def _fetch_listing(self, path: str) -> list[dict[str, Any]]:
        """Fetch a listing, caching a container only after every page succeeds."""
        try:
            return await self._load_listing(path)
        except BaseException:
            self.dircache.pop(path, None)
            raise

    async def _load_listing(self, path: str) -> list[dict[str, Any]]:
        """Read immediate children in pages, or the node itself when it is a file."""
        node, children = await self._read_listing_page(path, resume=None)
        if node.node_type != "container":
            return [nodes.to_info(node, path)]
        collected = list(children)
        if len(children) == LISTING_PAGE_SIZE:
            collected.extend(await self._remaining_pages(path, children))
        entries = [self._child_info(path, child) for child in collected]
        self.dircache[path] = entries
        return entries

    async def _remaining_pages(self, path: str, first_page: list[nodes.Node]) -> list[nodes.Node]:
        """Return children after the first full page, without repeating the cursor."""
        collected: list[nodes.Node] = []
        first_uri = first_page[0].uri
        resume = first_page[-1].uri
        while True:
            page_node, raw = await self._read_listing_page(path, resume=resume)
            if page_node.node_type != "container":
                message = f"listing page for {path} is not a container"
                raise errors.VOSpaceError(message)
            new_children, resume, done = _resume_page(raw, resume=resume, first_uri=first_uri)
            collected.extend(new_children)
            if done:
                return collected

    async def _read_listing_page(
        self,
        path: str,
        *,
        resume: str | None,
        limit: int = LISTING_PAGE_SIZE,
    ) -> tuple[nodes.Node, list[nodes.Node]]:
        """GET and parse one listing page, recording its VOSpace authority."""
        node, children = nodes.parse_container(await self._get_listing_page(path, resume=resume, limit=limit))
        self._note_authority(node.uri)
        return node, children

    async def _get_listing_page(self, path: str, *, resume: str | None, limit: int = LISTING_PAGE_SIZE) -> bytes:
        """GET one bounded container page, resuming at ``resume`` when given."""
        bindings = await self._get_bindings()
        url = bindings.require_nodes() + paths.encode_url_path(path)
        params = {"limit": str(limit)}
        if resume is not None:
            params["uri"] = resume
        url += "?" + _query(params)
        response = await self._send_to_service("GET", url, headers=nodes.XML_HEADERS)
        self._raise_for_status(response, path=path, allowed=(transport.HTTP_OK,))
        return response.content

    def _child_info(self, parent: str, child: Node) -> dict[str, Any]:
        """Build the info dict for a listing child under ``parent``."""
        child_path = self._listing_child_path(parent, child.uri)
        return nodes.to_info(child, child_path)

    def _listing_child_path(self, parent: str, uri: str) -> str:
        """Validate and return one server-listed immediate child path."""
        parts = urlsplit(uri)
        if (
            parts.scheme != "vos"
            or not parts.netloc
            or parts.username
            or parts.password
            or parts.query
            or parts.fragment
            or not parts.path.startswith("/")
            or parts.path.endswith("/")
            or "//" in parts.path
        ):
            msg = f"listed child URI is not canonical: {uri!r}"
            raise errors.VOSpaceError(msg)
        self._note_authority(uri)
        try:
            child_path = paths.decode_url_path(parts.path)
        except ValueError as exc:
            msg = f"listed child URI is not canonical: {uri!r}"
            raise errors.VOSpaceError(msg) from exc
        if child_path == "/" or paths.parent(child_path) != parent:
            msg = f"listed child is not an immediate descendant of {parent}: {uri!r}"
            raise errors.VOSpaceError(msg)
        return child_path

    async def _modified(self, path: str) -> datetime.datetime:
        """Return the OpenCADC modification date for ``path``."""
        path = self._strip_protocol(path)
        node = self._parse_and_note(await self._get_node_document(path, children=False))
        if node.mtime is None:
            msg = f"no modification date is available for {path}"
            raise errors.VOSpaceError(msg)
        return _parse_datetime(node.mtime)

    # -- synchronous byte negotiation ----------------------------------------

    async def _require_authority(self) -> str:
        """Return the discovered VOSpace authority, discovering it if needed."""
        if self._authority is None:
            self._parse_and_note(await self._get_node_document("/", children=False))
        if self._authority is None:  # pragma: no cover - root always carries a URI
            msg = "unable to discover the VOSpace authority"
            raise errors.VOSpaceError(msg)
        return self._authority

    async def _get(
        self,
        rpath: str | list[str],
        lpath: str | list[str],
        recursive: bool = False,  # noqa: FBT001, FBT002 - fsspec hook signature
        callback: Callback = DEFAULT_CALLBACK,
        maxdepth: int | None = None,
        **kwargs: Any,  # noqa: ANN401 - fsspec hook signature
    ) -> list[Any] | None:
        """Use fsspec's coordinator while marking entries for container handling."""
        kwargs[_GET_CONTAINER_MARKER] = True
        return await AsyncFileSystem._get(  # noqa: SLF001 - inherited seam
            self,
            rpath,
            lpath,
            recursive=recursive,
            callback=callback,
            maxdepth=maxdepth,
            **kwargs,
        )

    async def _find(
        self,
        path: str,
        maxdepth: int | None = None,
        withdirs: bool = False,  # noqa: FBT001, FBT002 - fsspec hook signature
        **kwargs: Any,  # noqa: ANN401 - fsspec hook signature
    ) -> list[str] | dict[str, dict[str, Any]]:
        """Find paths with a concurrent breadth-first walk."""
        return await coordination.find_concurrently(
            self,
            path,
            maxdepth=maxdepth,
            withdirs=withdirs,
            **kwargs,
        )

    async def _du(
        self,
        path: str,
        total: bool = True,  # noqa: FBT001, FBT002 - fsspec hook signature
        maxdepth: int | None = None,
        **kwargs: Any,  # noqa: ANN401 - fsspec hook signature
    ) -> int | dict[str, int]:
        """Measure paths from listing sizes, without one ``info`` per file.

        fsspec's inherited ``_du`` walks the tree and then requests ``info``
        for every path it found, one at a time, although each listing already
        carries the sizes. Only an entry the walk could not describe (an
        unlistable operand that is itself a file) costs one more lookup.
        """
        found = cast(
            "dict[str, dict[str, Any]]",
            await self._find(
                path,
                maxdepth=maxdepth,
                detail=True,
                **kwargs,
            ),
        )
        sizes: dict[str, int] = {}
        for name, info in found.items():
            described = info if "size" in info else await self._info(name)
            sizes[str(described["name"])] = described["size"]
        if total:
            return sum(sizes.values())
        return sizes

    async def _get_file(
        self,
        rpath: str,
        lpath: str,
        **kwargs: Any,  # noqa: ANN401 - fsspec hook signature
    ) -> None:
        """Stream one negotiated whole-object GET to a local file."""
        rpath = self._strip_protocol(rpath)
        target_validated = False
        if kwargs.pop(_GET_CONTAINER_MARKER, False):
            info = await self._info(rpath)
            if info["type"] == "directory":
                Path(lpath).mkdir(parents=True, exist_ok=True)  # noqa: ASYNC240
                return
            # The metadata already in hand is enough to reject an external
            # LinkNode, so the read needs no second node GET.
            await _transfer.reject_external_link_info(self, info)
            target_validated = True
        callback: Callback = kwargs.get("callback", DEFAULT_CALLBACK)
        started = time.monotonic()
        size = await self._download_file(rpath, lpath, callback=callback, target_validated=target_validated)
        _log_transfer("downloaded", rpath, lpath, size, started)

    async def _download_file(
        self,
        rpath: str,
        lpath: str,
        *,
        callback: Callback = DEFAULT_CALLBACK,
        target_validated: bool = False,
    ) -> int:
        """Stream one read to disk, optionally reusing a safe preflight.

        Returns:
            The number of bytes written.
        """
        response = await _transfer.open_read_stream(
            self,
            rpath,
            target_validated=target_validated,
        )
        written = 0
        try:
            size = response.headers.get("content-length")
            callback.set_size(int(size) if size is not None else None)
            with Path(lpath).open("wb") as local:  # noqa: ASYNC230 - staging to local disk
                if response.status_code == transport.HTTP_NO_CONTENT:
                    return 0
                async for chunk in response.aiter_raw(_integrity.READ_CHUNK):
                    local.write(chunk)
                    written += len(chunk)
                    callback.relative_update(len(chunk))
        finally:
            await response.aclose()
        return written

    async def _cat_file(
        self,
        path: str,
        start: int | None = None,
        end: int | None = None,
        **_kwargs: Any,  # noqa: ANN401 - fsspec hook signature
    ) -> bytes:
        """Return a byte slice, using HTTP Range when the endpoint returns 206."""
        return await _transfer.read_slice(self, self._strip_protocol(path), start, end)

    async def _cat_ranges(  # noqa: PLR0913 - fsspec hook signature
        self,
        paths: list[str],
        starts: int | Sequence[int | None] | None,
        ends: int | Sequence[int | None] | None,
        max_gap: int | None = None,  # noqa: ARG002 - accepted for fsspec compatibility
        batch_size: int | None = None,
        on_error: str = "return",
        **_kwargs: Any,  # noqa: ANN401 - fsspec hook signature
    ) -> list[bytes | BaseException]:
        """Return each range, ranging per object while responses are 206.

        A scalar ``starts``/``ends`` is broadcast to every path, matching
        fsspec's ``cat_ranges`` contract. A ``200``/``204`` falls back to one
        whole-object stage for that object.
        """
        count = len(paths)
        start_list = _broadcast(starts, count)
        end_list = _broadcast(ends, count)
        if len(start_list) != count or len(end_list) != count:
            msg = "paths, starts, and ends must have the same length"
            raise ValueError(msg)
        if on_error not in ("return", "raise"):
            msg = "on_error must be 'return' or 'raise'"
            raise ValueError(msg)
        effective_batch_size = batch_size if batch_size is not None else self.batch_size
        if effective_batch_size is not None and effective_batch_size != -1 and effective_batch_size <= 0:
            msg = "batch_size must be a positive integer or -1"
            raise ValueError(msg)
        if count == 0:
            return []

        stripped = [self._strip_protocol(rpath) for rpath in paths]
        grouped: dict[str, list[tuple[int, int | None, int | None]]] = {}
        for index, (stripped_path, start, end) in enumerate(zip(stripped, start_list, end_list, strict=True)):
            grouped.setdefault(stripped_path, []).append((index, start, end))

        grouped_items = list(grouped.items())
        object_results = cast(
            "list[list[tuple[int, bytes]] | BaseException]",
            await _run_coros_in_chunks(
                [_transfer.read_grouped_ranges(self, path, ranges) for path, ranges in grouped_items],
                batch_size=effective_batch_size,
                return_exceptions=on_error == "return",
            ),
        )
        results: list[bytes | BaseException] = [b""] * count
        for ranges, outcome in zip(grouped.values(), object_results, strict=True):
            if isinstance(outcome, BaseException):
                for index, _start, _end in ranges:
                    results[index] = outcome
            else:
                for index, value in outcome:
                    results[index] = value
        return results

    def open(
        self,
        path: str,
        mode: str = "rb",
        block_size: int | None = None,
        cache_options: dict[str, Any] | None = None,
        compression: str | None = None,
        **kwargs: Any,  # noqa: ANN401 - fsspec public signature
    ) -> io.IOBase:
        """Open a staged file, preserving failed-block discard for text writes."""
        if not ("w" in mode or "x" in mode) or ("b" in mode and compression is None):
            return super().open(
                path,
                mode=mode,
                block_size=block_size,
                cache_options=cache_options,
                compression=compression,
                **kwargs,
            )

        normalized_path = self._strip_protocol(path)
        binary_mode = mode.replace("t", "") if "b" in mode else mode.replace("t", "") + "b"
        text_kwargs = {key: kwargs.pop(key) for key in ("encoding", "errors", "newline") if key in kwargs}
        staged = cast(
            "staging.StagedWriteFile",
            super().open(
                normalized_path,
                mode=binary_mode,
                block_size=block_size,
                cache_options=cache_options,
                compression=None,
                **kwargs,
            ),
        )
        return staging.wrap_write(staged, normalized_path, mode, compression, **text_kwargs)

    def _open(
        self,
        path: str,
        mode: str = "rb",
        block_size: int | None = None,
        autocommit: bool = True,  # noqa: FBT001, FBT002 - fsspec hook signature
        cache_options: Any = None,  # noqa: ANN401 - fsspec hook signature
        **_kwargs: Any,  # noqa: ANN401 - fsspec hook signature
    ) -> staging.StagedReadFile | staging.StagedWriteFile | _file.VOSFile:
        """Open ``path``, disk-staging the whole object for read or write.

        Only binary modes reach here directly; fsspec wraps text mode over the
        staged binary file. Append and update (``+``) modes are unsupported, and
        a staged write always uploads on close: deferred commit
        (``autocommit=False``) is unsupported because this profile has no
        stage-then-commit primitive.
        """
        if "a" in mode or "+" in mode:
            msg = f"append and update modes are unsupported: {mode!r}"
            raise NotImplementedError(msg)
        path = self._strip_protocol(path)
        cache_type = _kwargs.pop("cache_type", None)
        if "r" in mode and (block_size is not None or cache_type is not None):
            return _file.VOSFile(
                self,
                path,
                mode=mode,
                block_size=block_size if block_size is not None else "default",
                cache_type=cache_type if isinstance(cache_type, str) else "readahead",
                cache_options=cache_options,
            )
        if not autocommit and ("w" in mode or "x" in mode):
            msg = "deferred commit (autocommit=False) is unsupported"
            raise NotImplementedError(msg)
        if "r" in mode:
            sync(self.loop, _transfer.preflight_read_target, self, path)
            temp_path = staging.new_temp_path()
            try:
                started = time.monotonic()
                size = sync(
                    self.loop,
                    self._download_file,
                    path,
                    temp_path,
                    target_validated=True,
                )
                _log_transfer("staged", path, temp_path, size, started)
                return staging.StagedReadFile(temp_path)
            except BaseException:
                staging.unlink_temp_path(temp_path)
                raise
        if "w" in mode or "x" in mode:
            if "x" in mode and self.exists(path):
                msg = f"path already exists: {path}"
                raise FileExistsError(msg)
            return staging.StagedWriteFile(
                lambda temp: self.put_file(temp, path, mode="overwrite"),
            )
        msg = f"unsupported open mode: {mode!r}"
        raise NotImplementedError(msg)

    async def open_async(
        self,
        path: str,
        mode: str = "rb",
        **_kwargs: Any,  # noqa: ANN401 - fsspec hook signature
    ) -> None:
        """Reject asynchronous file opening; async consumers use coroutine hooks."""
        msg = "open_async is unsupported; use the coroutine hooks instead"
        raise NotImplementedError(msg)

    # -- writing bytes -------------------------------------------------------

    async def _put(  # noqa: PLR0913 - fsspec hook signature
        self,
        lpath: str | list[str],
        rpath: str | list[str],
        recursive: bool = False,  # noqa: FBT001, FBT002 - fsspec hook signature
        callback: Callback = DEFAULT_CALLBACK,
        batch_size: int | None = None,
        maxdepth: int | None = None,
        **kwargs: Any,  # noqa: ANN401 - fsspec hook signature
    ) -> list[Any] | None:
        """Use fsspec's upload coordinator with one shared parent-creation scope."""
        async with coordination.write_scope(self):
            return await AsyncFileSystem._put(  # noqa: SLF001 - inherited seam
                self,
                lpath,
                rpath,
                recursive=recursive,
                callback=cast("Callback", coordination.DeferredBranchCallback(callback, self)),
                batch_size=batch_size,
                maxdepth=maxdepth,
                **kwargs,
            )

    async def _pipe(
        self,
        path: str | Mapping[str, bytes],
        value: bytes | None = None,
        batch_size: int | None = None,
        **kwargs: Any,  # noqa: ANN401 - fsspec hook signature
    ) -> list[Any] | None:
        """Pipe bytes with one shared parent-creation scope.

        Each write is created only when fsspec awaits it, so a failure does
        not leave the remaining writes as never-awaited coroutines.
        """
        if isinstance(path, str):
            mapping: Mapping[str, bytes] = {path: value if value is not None else b""}
        else:
            mapping = path
        async with coordination.write_scope(self):
            return await _run_coros_in_chunks(
                [
                    coordination.defer(
                        self,
                        functools.partial(self._pipe_file, key, item, **kwargs),
                    )
                    for key, item in mapping.items()
                ],
                batch_size=batch_size if batch_size is not None else self.batch_size,
                nofiles=True,
            )

    async def _pipe_file(
        self,
        path: str,
        value: bytes,
        mode: str = "overwrite",
        **kwargs: Any,  # noqa: ANN401 - fsspec hook signature
    ) -> None:
        """Write ``value`` to ``path`` with one whole PUT (create or overwrite)."""
        path = self._strip_protocol(path)
        coordination.join_write_scope(self)
        if mode == "create" and await self._exists(path):
            msg = f"path already exists: {path}"
            raise FileExistsError(msg)
        await self._ensure_parent(path)
        started = time.monotonic()
        await _transfer.write_whole(
            self,
            path,
            value,
            size=len(value),
            content_type=kwargs.get("content_type"),
            expected_digest=hashlib.md5(value, usedforsecurity=False).digest(),
        )
        _log_transfer("uploaded", "<bytes>", path, len(value), started)

    async def _put_file(
        self,
        lpath: str,
        rpath: str,
        mode: str = "overwrite",
        **kwargs: Any,  # noqa: ANN401 - fsspec hook signature
    ) -> None:
        """Stream one local file through one negotiated whole PUT."""
        callback: Callback = kwargs.get("callback", DEFAULT_CALLBACK)
        rpath = self._strip_protocol(rpath)
        coordination.join_write_scope(self)
        if mode == "create" and await self._exists(rpath):
            msg = f"path already exists: {rpath}"
            raise FileExistsError(msg)
        await self._ensure_parent(rpath)
        started = time.monotonic()
        size = await self._upload_file(lpath, rpath, callback=callback, content_type=kwargs.get("content_type"))
        _log_transfer("uploaded", lpath, rpath, size, started)

    async def _upload_file(
        self,
        lpath: str,
        rpath: str,
        *,
        callback: Callback = DEFAULT_CALLBACK,
        content_type: str | None = None,
    ) -> int:
        """Stream one local file through one negotiated PUT, hashing in flight.

        The MD5 is accumulated while the body streams and compared with any
        server-returned digest once the PUT completes, so the file is read once
        and the event loop never blocks on a whole-file hashing pass.

        Returns:
            The number of bytes uploaded.
        """
        size = Path(lpath).stat().st_size  # noqa: ASYNC240 - local-disk stat, not remote I/O
        callback.set_size(size)
        digest = _integrity.new_md5()
        await _transfer.write_whole(
            self,
            rpath,
            _integrity.file_body(lpath, callback, digest),
            size=size,
            content_type=content_type,
            expected_digest=digest.digest,
        )
        return size

    def touch(
        self,
        path: str,
        truncate: bool = True,  # noqa: FBT001, FBT002 - fsspec signature
        **_kwargs: Any,  # noqa: ANN401 - fsspec signature
    ) -> None:
        """Create or truncate ``path`` to zero bytes.

        Raises:
            NotImplementedError: If ``truncate`` is false, which this profile
                cannot express without an atomic modification-time update.
        """
        if not truncate:
            msg = "touch(truncate=False) is unsupported"
            raise NotImplementedError(msg)
        self.pipe_file(path, b"", mode="overwrite")

    # -- namespace and mutation ----------------------------------------------

    async def _create_container(self, path: str) -> None:
        """PUT one ContainerNode at ``path``."""
        bindings = await self._get_bindings()
        authority = await self._require_authority()
        document = nodes.build_container_document(f"vos://{authority}{path}")
        url = bindings.require_nodes() + paths.encode_url_path(path)
        try:
            response = await self._send_to_service("PUT", url, content=document, headers=nodes.XML_HEADERS)
            self._raise_for_status(response, path=path, allowed=(transport.HTTP_OK, transport.HTTP_CREATED))
        finally:
            self._invalidate(path)

    async def _delete_node(self, path: str) -> None:
        """DELETE the node at ``path``."""
        bindings = await self._get_bindings()
        url = bindings.require_nodes() + paths.encode_url_path(path)
        try:
            response = await self._send_to_service("DELETE", url)
            self._raise_for_status(
                response,
                path=path,
                allowed=(transport.HTTP_OK, transport.HTTP_NO_CONTENT),
            )
        finally:
            self._invalidate(path)
            self._forget_containers(path)
        logger.info("removed %s", path, extra={"vosfs_outcome": "removed"})

    async def _mkdir(
        self,
        path: str,
        create_parents: bool = True,  # noqa: FBT001, FBT002 - fsspec hook signature
        **_kwargs: Any,  # noqa: ANN401 - fsspec hook signature
    ) -> None:
        """Create one ContainerNode, optionally creating missing ancestors."""
        path = self._strip_protocol(path)
        parent = paths.parent(path)
        if create_parents and parent != path and not await self._exists(parent):
            await self._makedirs(parent, exist_ok=True)
        await self._create_container(path)

    async def _makedirs(self, path: str, exist_ok: bool = False) -> None:  # noqa: FBT001, FBT002
        """Create ``path`` and every missing ancestor.

        The parent chain is created first. ``_info`` skips a container that
        already exists, and concurrent callers share one creation task per path.
        """
        path = self._strip_protocol(path)
        if path in ("/", ""):
            return
        if not exist_ok and await self._exists(path):
            msg = f"path already exists: {path}"
            raise FileExistsError(msg)
        await self._materialize_container(path)

    async def _ensure_parent(self, path: str) -> None:
        """Create ``path``'s parent containers when they are missing."""
        parent = paths.parent(path)
        if parent not in ("/", path):
            await self._makedirs(parent, exist_ok=True)

    async def _materialize_container(self, path: str) -> None:
        """Share creation; a cancelled waiter cannot cancel the creator's state."""
        containers = self._runtimes.current().containers
        existing = containers.get(path)
        if existing is not None:
            await asyncio.shield(existing)
            return
        future: asyncio.Future[None] = asyncio.get_running_loop().create_future()
        containers[path] = future
        try:
            await self._create_missing_container(path)
        except BaseException as exc:
            if containers.get(path) is future:
                containers.pop(path, None)
            if not future.done():
                future.set_exception(exc)
                future.exception()
            raise
        if not future.done():
            future.set_result(None)

    async def _create_missing_container(self, path: str) -> None:
        """Create ``path`` after its parent, unless it is already a container."""
        parent = paths.parent(path)
        if parent not in ("/", path):
            await self._materialize_container(parent)
        try:
            info = await self._info(path)
        except FileNotFoundError:
            await self._ensure_container(path)
            return
        if info["type"] != "directory":
            msg = f"write parent is not a directory: {path}"
            raise FileExistsError(msg)

    async def _ensure_container(self, path: str) -> None:
        """Create a container, tolerating a concurrently-created container."""
        try:
            await self._create_container(path)
        except FileExistsError:
            info = await self._info(path)
            if info["type"] != "directory":
                raise

    async def _rm_file(self, path: str, **_kwargs: Any) -> None:  # noqa: ANN401 - fsspec hook signature
        """Delete one non-container node, refusing a container target.

        ``rm_file`` is the file-only primitive; deleting a directory must go
        through ``rm``/``rmdir`` so the empty-check or leaves-first contract is
        honoured. Guarding here stops a stray ``rm_file`` on a container from
        issuing a recursive server-side ``DELETE``.
        """
        path = self._strip_protocol(path)
        if (await self._info(path))["type"] == "directory":
            raise IsADirectoryError(errno.EISDIR, "path is a container", path)
        await self._delete_node(path)

    async def _rmdir(self, path: str) -> None:
        """Delete an empty container after proving it empty (non-atomic).

        A cached listing is reused. Otherwise one page with ``limit=1`` is
        enough to prove a container empty or occupied, without downloading a
        large listing.
        """
        path = self._strip_protocol(path)
        cached = self.dircache.get(path)
        if cached is None:
            node, children = await self._read_listing_page(path, resume=None, limit=1)
            occupied = node.node_type != "container" or bool(children)
        else:
            occupied = bool(cached)
        if occupied:
            raise OSError(errno.ENOTEMPTY, "directory not empty", path)
        await self._delete_node(path)

    async def _rm(
        self,
        path: str | list[str],
        recursive: bool = False,  # noqa: FBT001, FBT002 - fsspec hook signature
        batch_size: int | None = None,
        maxdepth: int | None = None,
        **kwargs: Any,  # noqa: ANN401, ARG002 - fsspec hook signature
    ) -> None:
        """Delete files and, with ``recursive``, empty-check or leaves-first trees.

        Independent targets are removed concurrently, at most ``batch_size`` at
        a time; after a failure no further target starts and the first failing
        target, in argument order, is raised. Nested or repeated targets keep
        the sequential order.
        """
        if maxdepth is not None:
            msg = "rm(maxdepth=...) is unsupported"
            raise NotImplementedError(msg)
        targets = [self._strip_protocol(target) for target in (path if isinstance(path, list) else [path])]
        if _overlapping(targets):
            # A target inside (or equal to) another must observe the earlier
            # removal, exactly as the sequential contract describes.
            for target in targets:
                await self._rm_one(target, recursive=recursive)
            return
        await coordination.run_bounded(
            [functools.partial(self._rm_one, target, recursive=recursive) for target in targets],
            coordination.effective_limit(
                batch_size if batch_size is not None else self.batch_size,
                len(targets),
            ),
        )

    async def _rm_one(self, path: str, *, recursive: bool) -> None:
        """Delete a single path, dispatching by node type and recursion."""
        info = await self._info(path)
        if info["type"] != "directory":
            await self._delete_node(path)
        elif recursive:
            await _removal.remove_tree(self, path)
        else:
            await self._rmdir(path)

    async def _cp_file(self, path1: str, path2: str, **_kwargs: Any) -> None:  # noqa: ANN401
        """Copy one object with a bounded read-to-write relay (bytes only).

        A container source recreates the destination container (fsspec's
        recursive coordinator passes container paths here too), so empty
        directories are preserved and a container is never read as bytes. For a
        file, the destination's parent container is created if missing.
        """
        path1 = self._strip_protocol(path1)
        path2 = self._strip_protocol(path2)
        # Resolve the source first so a missing source fails before any
        # destination container is created (no orphaned parent on error).
        # A cached listing entry (from the coordinator's expansion) suffices.
        source = await self._info(path1)
        await _transfer.reject_external_link_info(self, source)
        source_is_dir = source["type"] == "directory"
        # Then materialize the destination's parent, for both a directory and a
        # file target, so copying into a not-yet-created subtree (for example a
        # recursive glob into ``target/newdir``) never orphans an intermediate
        # ContainerNode.
        # Inside a bulk copy the parents are materialized once per operation;
        # otherwise every concurrent sibling copy would race to create them.
        coordination.join_write_scope(self)
        if source_is_dir:
            await self._makedirs(path2, exist_ok=True)
            return
        await self._ensure_parent(path2)
        size = _transfer.byte_size(source)
        source_md5 = source.get("md5")
        if size is not None and self.max_connections >= _transfer.COPY_CONNECTIONS:
            started = time.monotonic()
            await _transfer.copy_stream(
                self,
                path1,
                path2,
                size=size,
                source_md5=source_md5 if type(source_md5) is str else None,
            )
            _log_transfer("copied", path1, path2, size, started)
            return
        # Staging supplies Content-Length and needs only one connection at a time.
        temp_path = staging.new_temp_path()
        started = time.monotonic()
        try:
            await self._download_file(
                path1,
                temp_path,
                target_validated=True,
            )
            size = await self._upload_file(temp_path, path2)
        finally:
            staging.unlink_temp_path(temp_path)
        _log_transfer("copied", path1, path2, size, started)

    async def _mv_file(self, path1: str, path2: str) -> None:
        """Move one DataNode through the shared async executor."""
        await self._move(path1, path2, file_only=True)

    async def _copy(  # noqa: PLR0913 - fsspec hook signature
        self,
        path1: str | list[str],
        path2: str | list[str],
        recursive: bool = False,  # noqa: FBT001, FBT002 - fsspec hook signature
        on_error: str | None = None,
        maxdepth: int | None = None,
        batch_size: int | None = None,
        **kwargs: Any,  # noqa: ANN401 - fsspec hook signature
    ) -> None:
        """Copy paths inside the inherited-fsspec coordinator seam.

        The copy runs in one write scope, so destination containers shared by
        many concurrently copied files are checked and created once.
        """
        async with coordination.write_scope(self):
            await AsyncFileSystem._copy(  # noqa: SLF001 - inherited seam
                self,
                path1,
                path2,
                recursive=recursive,
                on_error=on_error,
                maxdepth=maxdepth,
                batch_size=batch_size,
                **kwargs,
            )

    def mv(
        self,
        path1: str,
        path2: str,
        recursive: bool = False,  # noqa: FBT001, FBT002 - fsspec signature
        maxdepth: int | None = None,
        **kwargs: Any,  # noqa: ANN401 - fsspec signature
    ) -> None:
        """Move a DataNode or ContainerNode; reject a LinkNode before mutation.

        Supported moves require an absent destination. The source is copied or
        recreated and deleted only after the destination genuinely exists; a
        failed source deletion may leave both paths. A directory move always
        recurses so the whole tree is recreated. A LinkNode raises
        ``NotImplementedError`` after source resolution and before mutation.
        """
        sync(
            self.loop,
            self._move,
            path1,
            path2,
            recursive=recursive,
            maxdepth=maxdepth,
            **kwargs,
        )

    async def _move(
        self,
        path1: str,
        path2: str,
        recursive: bool = False,  # noqa: FBT001, FBT002 - fsspec hook signature
        maxdepth: int | None = None,
        *,
        file_only: bool = False,
        **kwargs: Any,  # noqa: ANN401 - fsspec hook signature
    ) -> None:
        """Plan and execute one client-derived move."""
        started = time.monotonic()
        plan = await _moving.plan(
            self,
            path1,
            path2,
            recursive=recursive,
            maxdepth=maxdepth,
            file_only=file_only,
        )
        await _moving.execute(self, plan, **kwargs)
        if plan.entries:
            logger.info(
                "moved %s -> %s (%d entries, %.3f s)",
                plan.source,
                plan.destination,
                len(plan.entries),
                time.monotonic() - started,
                extra={"vosfs_outcome": "moved"},
            )

    def _forget_containers(self, path: str) -> None:
        """Drop remembered container creations at ``path`` and below.

        A delete must be able to recreate the same container. Creations stay
        remembered until then so a service that still answers ``404`` for a
        container this process just created is not created again.
        """
        containers = self._runtimes.current().containers
        prefix = "/" if path == "/" else f"{path}/"
        for key in [key for key in containers if key == path or key.startswith(prefix)]:
            containers.pop(key, None)

    def _invalidate(self, path: str) -> None:
        """Invalidate the directory cache for ``path``, its subtree, and parent.

        Clearing the whole subtree keeps a recursively moved or removed tree from
        leaving stale descendant listings behind, and clearing the parent lets
        its listing pick up the mutation (contract section 10). fsspec's base
        ``invalidate_cache`` is a no-op outside a transaction, so the directory
        cache is pruned directly.
        """
        prefix = "/" if path == "/" else f"{path}/"
        parent = paths.parent(path)
        stale = [entry for entry in list(self.dircache) if entry in (path, parent) or entry.startswith(prefix)]
        for entry in stale:
            self.dircache.pop(entry, None)

    def invalidate_cache(self, path: str | None = None) -> None:
        """Clear all directory state, or one path, its subtree, and parent."""
        if path is None:
            self.dircache.clear()
        else:
            self._invalidate(self._strip_protocol(path))

    async def aclose(self) -> None:
        """Close every realized HTTP client and evict the instance (idempotent).

        After this call the instance is removed from fsspec's instance cache and
        any later HTTP I/O fails as closed.
        """
        if self._pid != os.getpid():
            self._ensure_usable()
        self._closed = True
        await self._pool.aclose()
        # Evict just this instance; fsspec's public API only clears the whole cache.
        type(self)._cache.pop(self._fs_token, None)  # noqa: SLF001

    def close(self) -> None:
        """Synchronously close the filesystem, bridging through the fsspec loop.

        Raises:
            RuntimeError: If called on an ``asynchronous=True`` instance, which
                must be closed with ``await aclose()`` instead.
        """
        if self.asynchronous:
            msg = "use 'await aclose()' to close an asynchronous filesystem"
            raise RuntimeError(msg)
        sync(self.loop, self.aclose)


def _log_transfer(outcome: str, source: str, destination: str, size: int, started: float) -> None:
    """Emit the one INFO record for a completed single-file transfer."""
    seconds = time.monotonic() - started
    logger.info(
        "%s %s -> %s (%d bytes, %.3f s)",
        outcome,
        source,
        destination,
        size,
        seconds,
        extra={
            "vosfs_outcome": outcome,
            "vosfs_source": source,
            "vosfs_destination": destination,
            "vosfs_bytes": size,
            "vosfs_seconds": seconds,
        },
    )


def _overlapping(targets: list[str]) -> bool:
    """Whether any removal target repeats or lies inside another target."""
    unique = set(targets)
    if len(unique) != len(targets):
        return True
    return any(
        ancestor in unique
        for target in targets
        for ancestor in ["/", *_ancestors_top_down(target)[:-1]]
        if target != "/"
    )


def _broadcast(value: int | Sequence[int | None] | None, count: int) -> list[int | None]:
    """Broadcast a scalar range bound to every path, or return the sequence."""
    if value is None or isinstance(value, int):
        return [value] * count
    return list(value)


def _authority_of(uri: str) -> str:
    """Return the VOSpace authority carried by a ``vos://authority/...`` URI."""
    _scheme, separator, rest = uri.partition("://")
    if not separator or not rest:
        msg = f"node URI is not a VOSpace URI: {uri!r}"
        raise errors.VOSpaceError(msg)
    return rest.split("/", 1)[0]


def _parse_datetime(value: str) -> datetime.datetime:
    """Parse an ISO 8601 modification date, tolerating a trailing ``Z``."""
    return datetime.datetime.fromisoformat(value.strip().replace("Z", "+00:00"))


def _ancestors_top_down(path: str) -> list[str]:
    """Return ``path`` and its ancestors from the topmost down to ``path``."""
    result: list[str] = []
    current = ""
    for segment in paths.segments(path):
        current = f"{current}/{segment}"
        result.append(current)
    return result
