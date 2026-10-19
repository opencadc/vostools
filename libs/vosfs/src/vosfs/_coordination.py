"""Inherited fsspec coordination for bulk reads and writes."""

from __future__ import annotations

import asyncio
import contextlib
import contextvars
from functools import partial
from typing import TYPE_CHECKING, Any, TypeVar

from fsspec.utils import other_paths

if TYPE_CHECKING:
    from collections.abc import (
        AsyncIterator,
        Awaitable,
        Callable,
        Generator,
        Sequence,
    )

    from fsspec.callbacks import Callback

_T = TypeVar("_T")

#: Directory listings issued concurrently by one tree walk.
LISTING_CONCURRENCY = 32


def operation_limit(filesystem: Any, fallback: int) -> int:  # noqa: ANN401 - any async filesystem
    """Return ``filesystem.batch_size`` when it is a positive int, else ``fallback``."""
    batch_size = getattr(filesystem, "batch_size", None)
    if type(batch_size) is int and batch_size > 0:
        return batch_size
    return fallback


def effective_limit(batch_size: int | None, count: int) -> int:
    """Return a positive concurrency bound from an fsspec ``batch_size`` hint."""
    if batch_size is None or batch_size == -1:
        return max(count, 1)
    return max(batch_size, 1)


async def run_bounded(
    factories: Sequence[Callable[[], Awaitable[_T]]],
    limit: int,
) -> list[_T]:
    """Run coroutine factories with at most ``limit`` in flight, in input order.

    After the first failure no further factory is started; already running
    ones finish, and the failure with the lowest input index is raised, so the
    reported error does not depend on scheduling.
    """
    results: list[Any] = [None] * len(factories)
    failures: dict[int, Exception] = {}
    semaphore = asyncio.Semaphore(limit)

    async def run(index: int, factory: Callable[[], Awaitable[_T]]) -> None:
        async with semaphore:
            if failures:
                return
            try:
                results[index] = await factory()
            except Exception as exc:  # noqa: BLE001 - re-raised in input order below
                failures[index] = exc

    tasks = [asyncio.ensure_future(run(i, f)) for i, f in enumerate(factories)]
    try:
        await asyncio.gather(*tasks)
    except BaseException:
        for task in tasks:
            task.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)
        raise
    if failures:
        raise failures[min(failures)]
    return results


async def find_concurrently(
    filesystem: Any,  # noqa: ANN401 - the adapter or any fsspec async filesystem
    path: str,
    maxdepth: int | None = None,
    withdirs: bool = False,  # noqa: FBT001, FBT002 - fsspec hook signature
    **kwargs: Any,  # noqa: ANN401 - fsspec hook signature
) -> list[str] | dict[str, dict[str, Any]]:
    """Implement fsspec ``_find`` with one bounded concurrent listing per level.

    fsspec's inherited ``_find`` walks depth-first and awaits one listing at a
    time, so a tree of ``D`` containers costs ``D`` sequential round trips.
    This walks breadth-first, listing every container of one depth together,
    and returns the same result. ``on_error`` applies at every depth; fsspec's
    nested ``_walk`` calls drop it and silently treat an unreadable descendant
    as empty even when ``on_error="raise"``.
    """
    if maxdepth is not None and maxdepth < 1:
        msg = "maxdepth must be at least 1"
        raise ValueError(msg)
    path = filesystem._strip_protocol(path)  # noqa: SLF001 - fsspec hook
    detail = kwargs.pop("detail", False)
    on_error = kwargs.pop("on_error", "omit")
    out: dict[str, dict[str, Any]] = {}
    if withdirs and path != "" and await filesystem._isdir(path):  # noqa: SLF001
        out[path] = await filesystem._info(path)  # noqa: SLF001

    limit = operation_limit(filesystem, LISTING_CONCURRENCY)
    listing = _listing_reader(filesystem, on_error, kwargs, limit)
    level = [path]
    depth = 1
    while level:
        listings = await run_bounded(
            [partial(listing, directory) for directory in level],
            limit,
        )
        level = _collect_level(level, listings, out, withdirs=withdirs)
        if maxdepth is not None and depth >= maxdepth:
            break
        depth += 1

    if not out and await filesystem._isfile(path):  # noqa: SLF001
        out[path] = {}
    names = sorted(out)
    if not detail:
        return names
    return {name: out[name] for name in names}


def _listing_reader(
    filesystem: Any,  # noqa: ANN401 - any fsspec async filesystem
    on_error: Any,  # noqa: ANN401 - fsspec accepts "omit", "raise", or a callable
    kwargs: dict[str, Any],
    limit: int,
) -> Callable[[str], Awaitable[list[dict[str, Any]]]]:
    """Return one container lister applying ``on_error`` like fsspec ``_walk``."""
    semaphore = asyncio.Semaphore(limit)

    async def listing(directory: str) -> list[dict[str, Any]]:
        async with semaphore:
            try:
                return await filesystem._ls(directory, detail=True, **kwargs)  # noqa: SLF001
            except OSError as exc:
                if on_error == "raise":
                    raise
                if callable(on_error):
                    on_error(exc)
                return []

    return listing


def _collect_level(
    level: list[str],
    listings: list[list[dict[str, Any]]],
    out: dict[str, dict[str, Any]],
    *,
    withdirs: bool,
) -> list[str]:
    """Record one depth's entries in ``out`` and return the next depth."""
    children: list[str] = []
    for directory, entries in zip(level, listings, strict=True):
        for info in entries:
            pathname = info["name"].rstrip("/")
            if info["type"] == "directory" and pathname != directory:
                children.append(pathname)
                if withdirs:
                    out[info["name"]] = info
            else:
                out[info["name"]] = info
    return children


class WriteState:
    """Operation-scoped upload state and owned child tasks."""

    def __init__(self, owner: object) -> None:
        """Initialize state owned by one bulk write."""
        self.owner = owner
        self.owner_task = asyncio.current_task()
        self.active = True
        self.tasks: set[asyncio.Task[object]] = set()


_WRITE_STATE: contextvars.ContextVar[WriteState | None] = contextvars.ContextVar(
    "vosfs_coordinated_write_state",
    default=None,
)


@contextlib.asynccontextmanager
async def write_scope(owner: object) -> AsyncIterator[None]:
    """Bind one bulk write and drain every child before returning."""
    state = WriteState(owner)
    token = _WRITE_STATE.set(state)
    body_error: BaseException | None = None
    try:
        try:
            yield
        except BaseException as exc:  # noqa: BLE001 - drain before propagation
            body_error = exc
    finally:
        state.active = False
        try:
            cleanup = asyncio.create_task(_drain_write_tasks(state))
            while not cleanup.done():
                try:
                    await asyncio.shield(cleanup)
                except asyncio.CancelledError as exc:  # noqa: PERF203 - drain loop
                    body_error = exc
            cleanup.result()
        finally:
            _WRITE_STATE.reset(token)
    if body_error is not None:
        raise body_error


def join_write_scope(owner: object) -> WriteState | None:
    """Return owner state and register the calling child task."""
    state = _WRITE_STATE.get()
    if state is None or state.owner is not owner:
        return None
    task = asyncio.current_task()
    if task is not None and task is not state.owner_task and task not in state.tasks:
        state.tasks.add(task)
        task.add_done_callback(partial(_discard_write_task, state))
    if not state.active:
        raise asyncio.CancelledError
    return state


def _discard_write_task(state: WriteState, task: asyncio.Task[object]) -> None:
    """Retrieve child outcome and release completed task ownership."""
    with contextlib.suppress(asyncio.CancelledError):
        task.exception()
    state.tasks.discard(task)


async def _drain_write_tasks(state: WriteState) -> None:
    """Cancel and await registered children, including late joiners."""
    observed: set[asyncio.Task[object]] | None = None
    while observed != state.tasks:
        observed = set(state.tasks)
        await asyncio.sleep(0)
        registered = list(state.tasks)
        for task in registered:
            if not task.done():
                task.cancel()
        if registered:
            await asyncio.gather(*registered, return_exceptions=True)


def defer(owner: object, factory: Callable[[], Awaitable[Any]]) -> _DeferredAwaitable:
    """Return an awaitable that creates its coroutine only once awaited."""
    return _DeferredAwaitable(owner, factory)


class _DeferredAwaitable:
    """Create a callback coroutine only after fsspec schedules its task."""

    __slots__ = ("_factory", "_owner")

    def __init__(
        self,
        owner: object,
        factory: Callable[[], Awaitable[Any]],
    ) -> None:
        self._owner = owner
        self._factory = factory

    def __await__(self) -> Generator[Any, None, Any]:
        join_write_scope(self._owner)
        return self._factory().__await__()


class DeferredBranchCallback:
    """Delegate progress while making each callback prelude task-owned."""

    def __init__(self, callback: Callback, owner: object) -> None:
        """Wrap ``callback`` on behalf of one bulk write owned by ``owner``."""
        self._callback = callback
        self._owner = owner

    def set_size(self, size: int) -> None:
        """Forward the total size to the wrapped callback."""
        self._callback.set_size(size)

    def relative_update(self, inc: int = 1) -> None:
        """Forward a progress increment to the wrapped callback."""
        self._callback.relative_update(inc)

    def branch_coro(
        self,
        function: Callable[..., Awaitable[Any]],
    ) -> Callable[..., _DeferredAwaitable]:
        """Branch ``function`` while deferring its coroutine to its own task."""
        wrapped = self._callback.branch_coro(function)

        def deferred(
            path1: str,
            path2: str,
            **kwargs: Any,  # noqa: ANN401 - fsspec forwards hook options
        ) -> _DeferredAwaitable:
            return _DeferredAwaitable(
                self._owner,
                partial(wrapped, path1, path2, **kwargs),
            )

        return deferred


def remap(source_paths: list[str], destination: str) -> list[str]:
    """Map source paths beneath one destination."""
    return list(other_paths(source_paths, destination))
