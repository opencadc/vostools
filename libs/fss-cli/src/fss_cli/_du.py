"""Disk-usage execution for the central ``du`` callback."""

from __future__ import annotations

from dataclasses import dataclass
from typing import TYPE_CHECKING

from ._command import (
    _collate,
    _Failure,
    _MappedOperand,
    _run_single_operand_text,
    _valid_size,
)
from ._listing import format_size
from ._metadata import valid_display_text
from ._walk import _IncompatibleListingError, _ListedRow, _walk

if TYPE_CHECKING:
    from collections.abc import Mapping

    from fsspec.asyn import AsyncFileSystem

    from ._app import AsyncFilesystemSource


@dataclass(frozen=True)
class _DuRequest:
    summarize: bool
    human_readable: bool
    operand: _MappedOperand


def _file_sizes(rows: list[_ListedRow]) -> dict[str, int] | None:
    """Collect each listed file's size by reported name, or ``None`` if invalid.

    Like fsspec's ``_du``, every non-directory entry counts and a name listed
    twice counts once.
    """
    sizes: dict[str, int] = {}
    try:
        for row in rows:
            for info in row.files.values():
                path = info.get("name")
                size = info.get("size")
                if type(path) is not str or not valid_display_text(path) or not _valid_size(size):
                    return None
                sizes[path] = size
    except Exception:  # noqa: BLE001 - fail closed on hostile mapping behavior.
        return None
    return sizes


def _render_sizes(request: _DuRequest, sizes: Mapping[str, int]) -> str:
    if request.summarize:
        size = format_size(sum(sizes.values()), human_readable=request.human_readable)
        return f"{size}\t{request.operand.path}\n"
    return "".join(
        f"{format_size(size, human_readable=request.human_readable)}\t{path}\n"
        for path, size in sorted(sizes.items(), key=lambda entry: _collate(entry[0]))
    )


async def _measure(
    request: _DuRequest,
    filesystem: AsyncFileSystem,
) -> str | _Failure:
    try:
        rows = await _walk(filesystem, request.operand.path)
    except _IncompatibleListingError:
        return _Failure(request.operand)
    except Exception as error:  # noqa: BLE001 - classify awaited backend failure.
        return _Failure(request.operand, backend_error=error)
    sizes = _file_sizes(rows)
    if sizes is None:
        return _Failure(request.operand)
    return _render_sizes(request, sizes)


async def _run_du(
    command: str,
    request: _DuRequest,
    sources: Mapping[str, AsyncFilesystemSource],
) -> None:
    await _run_single_operand_text(
        command,
        request.operand,
        sources,
        lambda filesystem: _measure(request, filesystem),
    )
