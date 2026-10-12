"""Recursive-find execution for the central ``find`` callback."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from typing import TYPE_CHECKING, Literal, TypeGuard, cast

from ._command import (
    _call,
    _collate,
    _Failure,
    _MappedOperand,
    _run_single_operand_text,
)
from ._metadata import valid_display_text
from ._walk import _IncompatibleListingError, _ListedRow, _walk

if TYPE_CHECKING:
    from fsspec.asyn import AsyncFileSystem

    from ._app import AsyncFilesystemSource


@dataclass(frozen=True)
class _FindRequest:
    maxdepth: int | None
    kind: Literal["f", "d"]
    operand: _MappedOperand


def _render_paths(request: _FindRequest, paths: list[str]) -> str:
    if request.maxdepth == 0:
        root = request.operand.path.rstrip("/")
        paths = [path for path in paths if path.rstrip("/") == root]
    paths.sort(key=_collate)
    return "".join(f"{path}\n" for path in dict.fromkeys(paths))


def _selected_paths(
    request: _FindRequest,
    root_info: object,
    rows: list[_ListedRow],
) -> list[str] | None:
    """Select displayable file or directory paths, or ``None`` if invalid.

    File entries are every non-directory row entry, as fsspec's ``_find``
    reports them; ``--type d`` reports the operand itself when it is a
    directory plus every listed directory.
    """
    infos: list[Mapping[object, object]] = []
    if request.kind == "d":
        if not isinstance(root_info, Mapping):
            return None
        infos.append(cast("Mapping[object, object]", root_info))
    for row in rows:
        if request.kind == "d":
            infos.extend(row.directories.values())
        infos.extend(row.files.values())
    paths: list[str] = []
    try:
        for info in infos:
            path = info.get("name")
            kind = info.get("type")
            if not _valid_path(path) or type(kind) is not str:
                return None
            if (kind == "directory") == (request.kind == "d"):
                paths.append(path)
    except Exception:  # noqa: BLE001 - fail closed on hostile mapping consumption.
        return None
    return paths


def _valid_path(path: object) -> TypeGuard[str]:
    return type(path) is str and valid_display_text(path)


async def _search(
    request: _FindRequest,
    filesystem: AsyncFileSystem,
) -> str | _Failure:
    try:
        root_info = await _call(filesystem, "_info", request.operand.path) if request.kind == "d" else None
        rows = await _walk(
            filesystem,
            request.operand.path,
            maxdepth=1 if request.maxdepth == 0 else request.maxdepth,
        )
    except _IncompatibleListingError:
        return _Failure(request.operand)
    except Exception as error:  # noqa: BLE001 - classify awaited backend failure.
        return _Failure(request.operand, backend_error=error)
    paths = _selected_paths(request, root_info, rows)
    if paths is None:
        return _Failure(request.operand)
    return _render_paths(request, paths)


async def _run_find(
    command: str,
    request: _FindRequest,
    sources: Mapping[str, AsyncFilesystemSource],
) -> None:
    await _run_single_operand_text(
        command,
        request.operand,
        sources,
        lambda filesystem: _search(request, filesystem),
    )
