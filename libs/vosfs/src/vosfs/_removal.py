"""Client-derived recursive removal over the OpenCADC VOSpace profile.

The service exposes no recursive ``DELETE`` this client is willing to rely on,
so a tree is removed leaves-first from the client. Independent work runs
concurrently: sibling subtrees are listed and emptied in parallel and the
entries of one container are deleted together, but a container is deleted only
after every one of its children is confirmed gone. Removal is **non-atomic**:
after a failure no new request starts, in-flight deletions finish, and the
error reports every confirmed deletion rather than pretending the tree is gone
or that nothing was touched.
"""

from __future__ import annotations

import asyncio
from typing import TYPE_CHECKING, Any

from vosfs import _coordination as coordination
from vosfs import errors

if TYPE_CHECKING:
    from collections.abc import Awaitable, Callable

    from vosfs.filesystem import VOSpaceFileSystem

#: Listing and DELETE requests in flight for one recursive removal.
REMOVAL_CONCURRENCY = 32

#: Marks a request that was skipped or failed, as opposed to its result.
_SKIPPED = object()


def removal_error(
    path: str,
    completed: list[str],
    cause: Exception,
) -> errors.VOSpaceError:
    """Return a partial-completion error for a recursive removal failure."""
    return errors.VOSpaceError(
        f"recursive removal failed at {path}: {cause}",
        status=getattr(cause, "status", None),
        fault=getattr(cause, "fault", None),
        retry_after=getattr(cause, "retry_after", None),
        completed=list(completed),
        failed=[path],
    )


async def remove_tree(filesystem: VOSpaceFileSystem, path: str) -> None:
    """Delete a container and its descendants leaves-first, client-side."""
    removal = _TreeRemoval(filesystem)
    await removal.subtree(path)
    if removal.failure is not None:
        failed_path, cause = removal.failure
        raise removal_error(failed_path, removal.completed, cause) from cause


class _TreeRemoval:
    """One recursive removal: bounded requests and first-failure bookkeeping."""

    def __init__(self, filesystem: VOSpaceFileSystem) -> None:
        self.filesystem = filesystem
        self.semaphore = asyncio.Semaphore(coordination.operation_limit(filesystem, REMOVAL_CONCURRENCY))
        self.completed: list[str] = []
        self.failure: tuple[str, Exception] | None = None

    async def subtree(self, path: str) -> None:
        """Empty ``path`` concurrently, then delete it if nothing failed."""
        children = await self._step(path, lambda: self.filesystem._ls(path, detail=True))
        if children is _SKIPPED:
            return
        await asyncio.gather(
            *(
                self.subtree(child["name"]) if child["type"] == "directory" else self.delete(child["name"])
                for child in children
            )
        )
        if self.failure is None:
            await self.delete(path)

    async def delete(self, path: str) -> None:
        """Delete one node and record it only once the service confirms it."""
        if await self._step(path, lambda: self.filesystem._delete_node(path)) is _SKIPPED:
            return
        self.completed.append(path)

    async def _step(self, path: str, operation: Callable[[], Awaitable[Any]]) -> Any:  # noqa: ANN401 - the operation's own result
        """Run one request unless a failure already stopped the removal.

        Returns the request's result, or :data:`_SKIPPED` when the request was
        not started or failed (the first failure is recorded).
        """
        async with self.semaphore:
            if self.failure is not None:
                return _SKIPPED
            try:
                return await operation()
            except Exception as exc:  # noqa: BLE001 - reported with progress below
                if self.failure is None:
                    self.failure = (path, exc)
                return _SKIPPED
