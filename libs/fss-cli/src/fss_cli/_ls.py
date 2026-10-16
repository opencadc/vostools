"""Listing execution for the central ``ls`` callback."""

from __future__ import annotations

import os
import sys
from collections.abc import Mapping
from dataclasses import dataclass
from functools import partial
from typing import TYPE_CHECKING, Generic, TypeAlias, TypeVar, cast

import typer

from ._command import (
    _collate,
    _CommandFailureError,
    _drain_current_operation,
    _Failure,
    _first_backend_error,
    _MappedOperand,
    _render_failure,
    _render_output_failure,
    _run_mapped_command,
)
from ._concurrent import _gather_bounded
from ._listing import ListingRow, emphasize, render_listing, to_listing
from ._metadata import valid_display_text

if TYPE_CHECKING:
    from collections.abc import Awaitable, Callable

    from fsspec.asyn import AsyncFileSystem

    from ._app import AsyncFilesystemSource


@dataclass(frozen=True)
class _LsRequest:
    include_almost_all: bool
    long_listing: bool
    human_readable: bool
    operands: tuple[_MappedOperand, ...]


_PayloadT = TypeVar("_PayloadT")


@dataclass(frozen=True)
class _FileResult(Generic[_PayloadT]):
    operand: _MappedOperand
    value: _PayloadT


@dataclass(frozen=True)
class _DirectoryResult(Generic[_PayloadT]):
    operand: _MappedOperand
    values: tuple[_PayloadT, ...]


@dataclass(frozen=True)
class _PlainName:
    text: str
    directory: bool = False


_PlainResult: TypeAlias = _FileResult[_PlainName] | _DirectoryResult[_PlainName]
_LongResult: TypeAlias = _FileResult[ListingRow] | _DirectoryResult[ListingRow]


def _emphasis_enabled() -> bool:
    """Decide once per invocation whether directory names may be bold."""
    stdout = sys.stdout
    try:
        interactive = stdout is not None and stdout.isatty()
    except (AttributeError, ValueError):
        return False
    return interactive and not os.environ.get("NO_COLOR") and os.environ.get("TERM") not in {"dumb", "unknown"}


async def _run_ls(
    command: str,
    request: _LsRequest,
    sources: Mapping[str, AsyncFilesystemSource],
) -> None:
    emphasis = _emphasis_enabled()

    async def execute(filesystems: Mapping[str, AsyncFileSystem]) -> None:
        failures, format_output = await _trace_and_prepare(request, filesystems, emphasis=emphasis)
        backend_error = _first_backend_error(failures)
        output_error = None
        try:
            output = format_output()
            for failure in failures:
                _render_failure(command, failure)
            if output:
                try:
                    typer.echo(output, nl=False, color=True)
                except BrokenPipeError as error:
                    output_error = error
                except Exception as error:  # noqa: BLE001 - output boundary.
                    output_error = error
                    _render_output_failure(command, error)
        except Exception as error:  # Preserve backend/output cause through cleanup.
            command_error = backend_error if backend_error is not None else output_error
            if command_error is None:
                command_error = error
            raise _CommandFailureError(
                error=command_error,
                render=False,
                propagate=error,
            ) from error
        if failures or output_error is not None:
            raise _CommandFailureError(
                error=backend_error if backend_error is not None else output_error,
                render=False,
            )

    await _run_mapped_command(
        command,
        request.operands,
        sources,
        execute,
        broken_pipe_exit_code=1,
    )


async def _trace_and_prepare(
    request: _LsRequest,
    filesystems: Mapping[str, AsyncFileSystem],
    *,
    emphasis: bool,
) -> tuple[tuple[_Failure, ...], Callable[[], str]]:
    # The reader/renderer pairing is fixed per mode; branching keeps each pair
    # monomorphic while the deferred formatter preserves the caller's error
    # flow (formatting still happens inside the caller's try block).
    multiple_operands = len(request.operands) > 1
    if request.long_listing:

        def render_long(rows: tuple[ListingRow, ...]) -> str:
            return render_listing(
                rows,
                human_readable=request.human_readable,
                emphasize_directories=emphasis,
            ).removesuffix("\n")

        long_successes, failures = await _trace_operands(request, filesystems, _read_long_operand)
        return failures, partial(
            _format_successes,
            long_successes,
            render_long,
            multiple_operands=multiple_operands,
            emphasis=emphasis,
        )

    def render_plain(names: tuple[_PlainName, ...]) -> str:
        return "\n".join(emphasize(name.text) if emphasis and name.directory else name.text for name in names)

    plain_successes, failures = await _trace_operands(request, filesystems, _read_plain_operand)
    return failures, partial(
        _format_successes,
        plain_successes,
        render_plain,
        multiple_operands=multiple_operands,
        emphasis=emphasis,
    )


async def _trace_operands(
    request: _LsRequest,
    filesystems: Mapping[str, AsyncFileSystem],
    read: Callable[
        ...,
        Awaitable[_FileResult[_PayloadT] | _DirectoryResult[_PayloadT] | _Failure],
    ],
) -> tuple[
    tuple[_FileResult[_PayloadT] | _DirectoryResult[_PayloadT], ...],
    tuple[_Failure, ...],
]:
    # Operands are independent reads, so they overlap under the shared bound;
    # results come back in operand order and nothing is written until all are
    # in, so output and diagnostics are unchanged by completion order.
    results = await _gather_bounded(
        [
            partial(
                read,
                operand,
                filesystems[operand.name],
                include_almost_all=request.include_almost_all,
            )
            for operand in request.operands
        ]
    )
    successes: list[_FileResult[_PayloadT] | _DirectoryResult[_PayloadT]] = []
    failures = []
    for result in results:
        if isinstance(result, _Failure):
            failures.append(result)
        else:
            successes.append(result)
    return tuple(successes), tuple(failures)


async def _classify_operand(
    operand: _MappedOperand,
    filesystem: AsyncFileSystem,
) -> Mapping[str, object] | _Failure:
    # fsspec's native async API intentionally exposes underscore coroutines.
    try:
        info = await _drain_current_operation(filesystem._info(operand.path))
    except Exception as error:  # noqa: BLE001 - classify awaited backend failure.
        return _Failure(operand, backend_error=error)

    if not isinstance(info, Mapping):
        return _Failure(operand)
    kind = info.get("type")
    if type(kind) is not str or (kind not in {"file", "directory"} and info.get("islink") is not True):
        return _Failure(operand)
    return cast("Mapping[str, object]", info)


async def _read_plain_operand(
    operand: _MappedOperand,
    filesystem: AsyncFileSystem,
    *,
    include_almost_all: bool,
) -> _PlainResult | _Failure:
    info = await _classify_operand(operand, filesystem)
    if isinstance(info, _Failure):
        return info
    if info["type"] != "directory" or info.get("islink") is True:
        return _FileResult(operand=operand, value=_PlainName(operand.spelling))

    listing = await _list_directory(operand, filesystem, detail=True)
    if isinstance(listing, _Failure):
        return listing
    children = _directory_lines(
        operand.path,
        listing,
        include_almost_all=include_almost_all,
    )
    if children is None:
        return _Failure(operand)
    return _DirectoryResult(operand=operand, values=children)


async def _read_long_operand(
    operand: _MappedOperand,
    filesystem: AsyncFileSystem,
    *,
    include_almost_all: bool,
) -> _LongResult | _Failure:
    info = await _classify_operand(operand, filesystem)
    if isinstance(info, _Failure):
        return info
    if info["type"] != "directory" or info.get("islink") is True:
        row = _listing_row(info)
        if row is None or not row.name or "\0" in row.name or "\n" in row.name:
            return _Failure(operand)
        return _FileResult(operand=operand, value=row)

    listing = await _list_directory(operand, filesystem, detail=True)
    if isinstance(listing, _Failure):
        return listing
    rows = _directory_rows(
        operand.path,
        listing,
        include_almost_all=include_almost_all,
    )
    if rows is None:
        return _Failure(operand)
    return _DirectoryResult(operand=operand, values=rows)


def _listing_row(info: Mapping[str, object]) -> ListingRow | None:
    try:
        return to_listing(info)
    except (TypeError, ValueError):
        return None


async def _list_directory(
    operand: _MappedOperand,
    filesystem: AsyncFileSystem,
    *,
    detail: bool,
) -> object | _Failure:
    try:
        return await _drain_current_operation(
            filesystem._ls(
                operand.path,
                detail=detail,
            )
        )
    except Exception as error:  # noqa: BLE001 - classify awaited backend failure.
        return _Failure(operand, backend_error=error)


def _sort_key(
    result: _FileResult[_PayloadT] | _DirectoryResult[_PayloadT],
) -> tuple[str, str]:
    return _collate(result.operand.spelling)


def _format_successes(
    successes: tuple[_FileResult[_PayloadT] | _DirectoryResult[_PayloadT], ...],
    render: Callable[[tuple[_PayloadT, ...]], str],
    *,
    multiple_operands: bool,
    emphasis: bool,
) -> str:
    if not successes:
        return ""
    if not multiple_operands:
        result = successes[0]
        values = (result.value,) if isinstance(result, _FileResult) else result.values
        rendered = render(values)
        return f"{rendered}\n" if rendered else ""

    files = sorted(
        (result for result in successes if isinstance(result, _FileResult)),
        key=_sort_key,
    )
    directories = sorted(
        (result for result in successes if isinstance(result, _DirectoryResult)),
        key=_sort_key,
    )
    blocks: list[str] = []
    file_values = tuple(result.value for result in files)
    if file_values:
        blocks.append(render(file_values))
    for result in directories:
        rendered = render(result.values)
        spelling = result.operand.spelling
        header = f"{emphasize(spelling) if emphasis else spelling}:"
        blocks.append(f"{header}\n{rendered}" if rendered else header)
    return _join_blocks(blocks)


def _join_blocks(blocks: list[str]) -> str:
    return "\n\n".join(blocks) + "\n" if blocks else ""


def _directory_basename(path: str, name: object) -> str | None:
    if not isinstance(name, str):
        return None

    comparison_path = path.rstrip("/")
    prefix = "/" if not comparison_path else f"{comparison_path}/"
    if not name.startswith(prefix):
        return None
    basename = name[len(prefix) :]
    if not basename or "/" in basename or not valid_display_text(basename):
        return None
    return basename


def _directory_lines(
    path: str,
    listing: object,
    *,
    include_almost_all: bool,
) -> tuple[_PlainName, ...] | None:
    # Plain names print no metadata, so only name, type and islink are read;
    # the rest of each record is deliberately left unvalidated.
    if not isinstance(listing, list):
        return None

    names = []
    for info in listing:
        if not isinstance(info, Mapping):
            return None
        typed_info = cast("Mapping[str, object]", info)
        basename = _directory_basename(path, typed_info.get("name"))
        if basename is None:
            return None
        kind = typed_info.get("type")
        directory = type(kind) is str and kind in {"directory", "dir"} and typed_info.get("islink") is not True
        names.append(_PlainName(basename, directory=directory))

    if include_almost_all:
        selected = (name for name in names if name.text not in {".", ".."})
    else:
        selected = (name for name in names if not name.text.startswith("."))
    return tuple(sorted(selected, key=lambda name: _collate(name.text)))


def _directory_rows(
    path: str,
    listing: object,
    *,
    include_almost_all: bool,
) -> tuple[ListingRow, ...] | None:
    if not isinstance(listing, list):
        return None

    entries: list[tuple[str, Mapping[str, object]]] = []
    for info in listing:
        if not isinstance(info, Mapping):
            return None
        typed_info = cast("Mapping[str, object]", info)
        basename = _directory_basename(path, typed_info.get("name"))
        if basename is None:
            return None
        entries.append((basename, typed_info))

    if include_almost_all:
        selected = (entry for entry in entries if entry[0] not in {".", ".."})
    else:
        selected = (entry for entry in entries if not entry[0].startswith("."))
    sorted_entries = sorted(selected, key=lambda entry: _collate(entry[0]))
    rows = []
    for _basename, info in sorted_entries:
        row = _listing_row(info)
        if row is None:
            return None
        rows.append(row)
    return tuple(rows)
