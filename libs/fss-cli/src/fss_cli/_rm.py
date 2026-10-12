"""Typed async execution for ``rm`` profiles."""

from __future__ import annotations

from dataclasses import dataclass
from typing import TYPE_CHECKING

from ._command import (
    _binary_stdout,
    _CommandFailureError,
    _first_backend_error,
    _MappedOperand,
    _render_output_failure,
    _run_mapped_command,
    _write_binary,
)
from ._recursive_rm import (
    _RecursiveRmFailure,
    _remove_recursive,
    _render_recursive_failure,
)
from ._rmdir import _remove_empty_directory, _RmdirFailure
from ._rmdir import _render_failure as _render_rmdir_failure
from ._unlink import _confirmed_rm_file, _render_failure, _UnlinkFailure

if TYPE_CHECKING:
    from collections.abc import Mapping

    from fsspec.asyn import AsyncFileSystem

    from ._app import AsyncFilesystemSource


@dataclass(frozen=True)
class _RmRequest:
    force: bool
    directory: bool
    recursive: bool
    verbose: bool
    operands: tuple[_MappedOperand, ...]


def _write_verbose_line(spelling: str) -> None:
    chunk = f"{spelling}\n".encode()
    stdout = _binary_stdout()
    _write_binary(stdout, chunk)
    stdout.flush()


def _render_rm_failure(
    command: str,
    failure: _UnlinkFailure | _RmdirFailure | _RecursiveRmFailure,
) -> None:
    if isinstance(failure, _RecursiveRmFailure):
        _render_recursive_failure(command, failure)
    elif isinstance(failure, _RmdirFailure):
        _render_rmdir_failure(command, failure)
    else:
        _render_failure(command, failure)


def _render_rm_failure_or_raise(
    command: str,
    failure: _UnlinkFailure | _RmdirFailure | _RecursiveRmFailure,
) -> None:
    try:
        _render_rm_failure(command, failure)
    except Exception as error:
        raise _CommandFailureError(
            error=failure.backend_error,
            render=False,
            propagate=error,
        ) from error


async def _remove_directory_entry(
    operand: _MappedOperand,
    filesystem: AsyncFileSystem,
) -> _UnlinkFailure | _RmdirFailure | None:
    failure = await _confirmed_rm_file(operand, filesystem)
    if isinstance(failure, _UnlinkFailure) and failure.incompatible == "directory":
        return await _remove_empty_directory(operand, filesystem)
    return failure


async def _trace_operands(
    command: str,
    request: _RmRequest,
    filesystems: Mapping[str, AsyncFileSystem],
    failures: list[_UnlinkFailure | _RmdirFailure | _RecursiveRmFailure],
) -> Exception | None:
    for operand in request.operands:
        filesystem = filesystems[operand.name]
        if request.recursive:
            result = await _remove_recursive(operand, filesystem)
        elif request.directory:
            result = await _remove_directory_entry(operand, filesystem)
        else:
            result = await _confirmed_rm_file(operand, filesystem)
        force_missing = request.force and (
            (
                isinstance(result, _UnlinkFailure)
                and not result.uncertain
                and isinstance(result.backend_error, FileNotFoundError)
            )
            or (isinstance(result, _RecursiveRmFailure) and result.root_missing)
        )
        if result is not None and not force_missing:
            failures.append(result)
            if request.verbose:
                _render_rm_failure_or_raise(command, result)
            continue
        if request.verbose and result is None:
            try:
                _write_verbose_line(operand.spelling)
            except BrokenPipeError as error:
                return error
            except Exception as error:  # noqa: BLE001 - stdout boundary.
                try:
                    _render_output_failure(command, error)
                except Exception as render_error:
                    raise _CommandFailureError(
                        error=error,
                        render=False,
                        propagate=render_error,
                    ) from render_error
                return error
    return None


async def _run_rm(
    command: str,
    request: _RmRequest,
    sources: Mapping[str, AsyncFilesystemSource],
) -> None:
    async def operation(filesystems: Mapping[str, AsyncFileSystem]) -> None:
        failures: list[_UnlinkFailure | _RmdirFailure | _RecursiveRmFailure] = []
        output_error = await _trace_operands(command, request, filesystems, failures)
        backend_error = _first_backend_error(failures)
        if not request.verbose:
            for failure in failures:
                _render_rm_failure_or_raise(command, failure)
        if failures or output_error is not None:
            raise _CommandFailureError(
                error=backend_error if backend_error is not None else output_error,
                render=False,
            )

    await _run_mapped_command(command, request.operands, sources, operation)
