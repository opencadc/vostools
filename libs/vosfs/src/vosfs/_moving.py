"""Client-derived move over the OpenCADC VOSpace profile.

The service exposes no atomic rename, so a move is four explicit phases:

1. **plan** — resolve every policy question before touching the destination;
2. **copy** — recreate the source tree at the destination;
3. **verify** — prove each destination exists with the expected type and size;
4. **delete** — only then remove the sources, leaves-first.

Deletion never runs on unverified state. A failure after step 2 keeps *both*
paths and reports which entries completed, because a move that has copied but
not deleted is recoverable while one that deleted without copying is not.
"""

from __future__ import annotations

import asyncio
import contextlib
import errno
import functools
import time
from dataclasses import dataclass
from typing import TYPE_CHECKING, Any, cast
from urllib.parse import urljoin

from vosfs import _coordination as coordination
from vosfs import errors, nodes, paths, transport
from vosfs.xmlio import local_name, safe_parse

if TYPE_CHECKING:
    from collections.abc import Sequence

    from vosfs.filesystem import VOSpaceFileSystem


@dataclass(frozen=True)
class MoveEntry:
    """One source, destination, and verification expectation."""

    source: str
    destination: str
    kind: str
    size: int


@dataclass(frozen=True)
class MovePlan:
    """A preflighted client-derived move with no unresolved policy."""

    source: str
    destination: str
    entries: tuple[MoveEntry, ...]
    recursive: bool
    maxdepth: int | None


async def plan(  # noqa: PLR0913 - one parameter per resolved move policy.
    filesystem: VOSpaceFileSystem,
    path1: str,
    path2: str,
    *,
    recursive: bool,
    maxdepth: int | None,
    file_only: bool,
) -> MovePlan:
    """Resolve all move policy before any destination mutation."""
    source = filesystem._strip_protocol(path1)
    destination = filesystem._strip_protocol(path2)
    source_info = await filesystem._info(source)
    if source_info.get("islink"):
        msg = "moving a LinkNode is unsupported"
        raise NotImplementedError(msg)
    if file_only and source_info["type"] == "directory":
        raise IsADirectoryError(errno.EISDIR, "move source is a container", source)
    if source == destination:
        if source_info["type"] == "directory":
            msg = f"move destination already exists: {destination}"
            raise FileExistsError(msg)
        return MovePlan(
            source=source,
            destination=destination,
            entries=(),
            recursive=False,
            maxdepth=maxdepth,
        )
    if not file_only and (source == "/" or destination.startswith(f"{source}/")):
        msg = f"move destination is within the source: {destination}"
        raise ValueError(msg)
    recursive = recursive or source_info["type"] == "directory"
    source_paths = await filesystem._expand_path(
        source,
        recursive=recursive,
        maxdepth=maxdepth,
    )
    # ``_expand_path`` lists every container it walks, so each entry's info is
    # answered from the directory cache rather than one node GET per entry.
    source_manifest = [
        (
            path,
            source_info if path == source else await filesystem._info(path),
        )
        for path in source_paths
    ]
    if any(info.get("islink") for _path, info in source_manifest):
        msg = "moving a LinkNode is unsupported"
        raise NotImplementedError(msg)
    if await filesystem._exists(destination):
        msg = f"move destination already exists: {destination}"
        raise FileExistsError(msg)
    destination_paths = coordination.remap(source_paths, destination)
    entries = tuple(
        MoveEntry(
            source=path,
            destination=copied_path,
            kind=info["type"],
            size=int(info["size"]),
        )
        for (path, info), copied_path in zip(
            source_manifest,
            destination_paths,
            strict=True,
        )
    )
    return MovePlan(source, destination, entries, recursive, maxdepth)


async def execute(
    filesystem: VOSpaceFileSystem,
    move_plan: MovePlan,
    **kwargs: Any,  # noqa: ANN401 - fsspec forwards copy options
) -> None:
    """Copy, verify, and only then delete one preflighted move plan.

    A same-service move with no ``maxdepth`` tries a server ``moveNode`` job
    first. The client-side copy remains when ``/transfers`` is not advertised,
    the job is rejected, or only part of the tree should move.
    """
    if not move_plan.entries:
        return
    kwargs.pop("on_error", None)
    if move_plan.maxdepth is None and await _server_move(filesystem, move_plan):
        completed, failed = await verify_destinations(filesystem, move_plan)
        if failed:
            filesystem._invalidate(move_plan.destination)
            msg = (
                f"server move verification failed ({len(completed)} completed, {len(failed)} failed); "
                "source may have moved"
            )
            raise errors.VOSpaceError(msg, completed=completed, failed=failed)
        filesystem._invalidate(move_plan.source)
        filesystem._invalidate(move_plan.destination)
        filesystem._forget_containers(move_plan.source)
        return
    try:
        await filesystem._copy(
            [entry.source for entry in move_plan.entries],
            [entry.destination for entry in move_plan.entries],
            recursive=move_plan.recursive,
            maxdepth=move_plan.maxdepth,
            on_error="raise",
            **kwargs,
        )
    except Exception as exc:
        completed, failed = await verify_destinations(filesystem, move_plan)
        filesystem._invalidate(move_plan.destination)
        msg = f"move copy failed ({len(completed)} completed, {len(failed)} failed)"
        raise errors.VOSpaceError(msg, completed=completed, failed=failed) from exc
    completed, failed = await verify_destinations(filesystem, move_plan)
    if failed:
        filesystem._invalidate(move_plan.destination)
        msg = f"move copy is incomplete ({len(completed)} completed, {len(failed)} failed); source is kept"
        raise errors.VOSpaceError(msg, completed=completed, failed=failed)
    await remove_sources(
        filesystem,
        move_plan.entries,
        allow_nonempty=move_plan.maxdepth is not None,
    )
    filesystem._invalidate(move_plan.source)
    filesystem._invalidate(move_plan.destination)


async def verify_destinations(
    filesystem: VOSpaceFileSystem,
    move_plan: MovePlan,
) -> tuple[list[str], list[str]]:
    """Return destination paths whose type and file size did or did not verify.

    A moved tree is re-listed fresh (one listing per container, walked
    concurrently) instead of one metadata request per entry; an entry the
    listing does not show, or a single moved file, is looked up directly.
    """
    filesystem._invalidate(move_plan.destination)
    listed: dict[str, dict[str, Any]] = {}
    if any(entry.kind == "directory" for entry in move_plan.entries):
        try:
            listed = cast(
                "dict[str, dict[str, Any]]",
                await filesystem._find(
                    move_plan.destination,
                    withdirs=True,
                    detail=True,
                ),
            )
        except OSError:
            listed = {}

    async def observed(entry: MoveEntry) -> dict[str, Any] | None:
        info = listed.get(entry.destination)
        if info:
            return info
        try:
            return await filesystem._info(entry.destination)
        except OSError:
            return None

    infos = await coordination.run_bounded(
        [functools.partial(observed, entry) for entry in move_plan.entries],
        coordination.effective_limit(filesystem.batch_size, len(move_plan.entries)),
    )
    completed: list[str] = []
    failed: list[str] = []
    for entry, copied in zip(move_plan.entries, infos, strict=True):
        type_matches = copied is not None and copied["type"] == entry.kind
        size_matches = type_matches and (entry.kind == "directory" or copied["size"] == entry.size)
        if size_matches:
            completed.append(entry.destination)
        else:
            failed.append(entry.destination)
    return completed, failed


async def remove_sources(
    filesystem: VOSpaceFileSystem,
    entries: Sequence[MoveEntry],
    *,
    allow_nonempty: bool,
) -> None:
    """Remove verified moved entries leaves-first, retaining bounded descendants.

    Entries of one depth are removed concurrently; a depth starts only after
    every deeper entry is gone. After a failure no further entry starts.
    """
    completed: list[str] = []
    failed: list[str] = []
    causes: list[OSError] = []
    limit = coordination.effective_limit(filesystem.batch_size, len(entries))
    depths = sorted({entry.source.count("/") for entry in entries}, reverse=True)

    async def remove(path: str) -> None:
        if failed:
            return
        try:
            await filesystem._rm_one(path, recursive=False)
        except OSError as exc:
            if allow_nonempty and exc.errno == errno.ENOTEMPTY:
                return
            filesystem._invalidate(path)
            failed.append(path)
            causes.append(exc)
            return
        completed.append(path)

    for depth in depths:
        await coordination.run_bounded(
            [functools.partial(remove, entry.source) for entry in entries if entry.source.count("/") == depth],
            limit,
        )
        if failed:
            msg = f"move source deletion failed ({len(completed)} completed, {len(failed)} failed)"
            raise errors.VOSpaceError(
                msg,
                completed=completed,
                failed=failed,
            ) from causes[0]


_MOVE_DEADLINE_S = 30.0


async def _server_move(filesystem: VOSpaceFileSystem, move_plan: MovePlan) -> bool:
    """Run a same-service moveNode job. Return False when the service cannot."""
    transfers = (await filesystem._get_bindings()).transfers_url
    if transfers is None:
        return False
    authority = await filesystem._require_authority()
    document = nodes.build_move_document(
        f"vos://{authority}{paths.encode_url_path(move_plan.source)}",
        f"vos://{authority}{paths.encode_url_path(move_plan.destination)}",
    )
    posted = await filesystem._send_to_service("POST", transfers, content=document, headers=nodes.XML_HEADERS)
    if posted.status_code in (404, 405, 501) or (
        posted.status_code == transport.HTTP_BAD_REQUEST and _unsupported_move(errors.bounded_text(posted.content))
    ):
        return False
    filesystem._raise_for_status(posted, path=transfers, allowed=(transport.HTTP_SEE_OTHER,))
    location = posted.headers.get("location")
    job = urljoin(transfers, location) if location else ""
    if not job or not transport.same_origin(job, filesystem.endpoint_url):
        msg = "move job has a missing or cross-origin Location; its state is uncertain"
        raise errors.VOSpaceError(msg)
    try:
        await _set_job_phase(filesystem, job, "RUN")
        await _wait_for_job(filesystem, job)
    except NotImplementedError:
        return False
    except (asyncio.CancelledError, TimeoutError):
        with contextlib.suppress(Exception):
            await _set_job_phase(filesystem, job, "ABORT")
        raise
    return True


async def _set_job_phase(filesystem: VOSpaceFileSystem, job: str, phase: str) -> None:
    response = await filesystem._send_to_service(
        "POST",
        f"{job.rstrip('/')}/phase",
        content=f"PHASE={phase}".encode(),
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    filesystem._raise_for_status(response, path=job, allowed=(transport.HTTP_OK, transport.HTTP_SEE_OTHER))


async def _wait_for_job(filesystem: VOSpaceFileSystem, job: str) -> None:
    """Poll one UWS job until it completes, fails, or the deadline passes."""
    deadline = time.monotonic() + _MOVE_DEADLINE_S
    separator = "&" if "?" in job else "?"
    phase = "PENDING"
    while time.monotonic() < deadline:
        response = await filesystem._send_to_service("GET", f"{job}{separator}WAIT=5&PHASE={phase}")
        filesystem._raise_for_status(response, path=job, allowed=(transport.HTTP_OK,))
        phase, summary = _job_phase(response.content)
        if phase == "COMPLETED":
            return
        if phase == "ERROR":
            _raise_job_error(summary)
        if phase in {"ABORTED", "HELD", "SUSPENDED"}:
            msg = f"move job {phase}: {summary}"
            raise errors.VOSpaceError(msg)
        await asyncio.sleep(0.05)
    msg = f"move job did not finish within {_MOVE_DEADLINE_S:.0f}s: {job}"
    raise TimeoutError(msg)


def _job_phase(data: bytes) -> tuple[str, str]:
    """Return the UWS phase and any error summary text."""
    root = safe_parse(data)
    phase = "PENDING"
    summary = ""
    for element in root.iter():
        name = local_name(element.tag)
        if name == "phase" and element.text:
            phase = element.text.strip().upper()
        elif name == "errorSummary":
            summary = "".join(element.itertext()).strip()
    return phase, summary


def _raise_job_error(summary: str) -> None:
    """Map a move job's error summary onto the matching Python exception."""
    text = summary.lower()
    if _unsupported_move(text):
        raise NotImplementedError(summary)
    if "permission" in text or "denied" in text:
        raise PermissionError(summary)
    if "not found" in text:
        raise FileNotFoundError(summary)
    if "exist" in text:
        raise FileExistsError(summary)
    raise errors.VOSpaceError(summary or "move job failed")


def _unsupported_move(summary: str) -> bool:
    text = summary.lower()
    return any(fault in text for fault in ("not implemented", "not supported", "operationnotsupported"))
