"""Typed async execution for ``rmdir``."""

from __future__ import annotations

import errno
from collections.abc import Mapping
from dataclasses import dataclass
from typing import TYPE_CHECKING

from ._command import (
    _Failure,
    _MappedOperand,
    _raise_operand_failures,
    _render_backend_failure,
    _render_operand_diagnostic,
    _run_mapped_command,
)

if TYPE_CHECKING:
    from fsspec.asyn import AsyncFileSystem

    from ._app import AsyncFilesystemSource


@dataclass(frozen=True)
class _RmdirFailure(_Failure):
    """A failure raised on the empty-directory removal path.

    Structurally identical to the base failure; the distinct type is how ``rm``
    tells a directory-path failure from a file-path one, because the two render
    different diagnostic vocabularies for the same condition.
    """


async def _require_directory(
    operand: _MappedOperand,
    filesystem: AsyncFileSystem,
) -> _RmdirFailure | None:
    try:
        info = await filesystem._info(operand.path)
    except Exception as error:  # noqa: BLE001 - classify awaited backend failure.
        return _RmdirFailure(operand, backend_error=error)

    if not isinstance(info, Mapping) or not isinstance(info.get("type"), str):
        return _RmdirFailure(operand, incompatible="result")

    result_type = info["type"]
    if result_type == "file":
        return _RmdirFailure(operand, incompatible="directory")
    if result_type != "directory":
        return _RmdirFailure(operand, incompatible="result")
    return None


async def _observe_post_mutation(
    operand: _MappedOperand,
    filesystem: AsyncFileSystem,
    mutation_error: Exception | None,
) -> _RmdirFailure | None:
    try:
        await filesystem._info(operand.path)
    except FileNotFoundError:
        # Absence proves success, including after a mutation-call exception.
        return None
    except Exception as error:  # noqa: BLE001 - never hide non-not-found errors.
        if mutation_error is not None:
            return _RmdirFailure(operand, backend_error=mutation_error, uncertain=True)
        return _RmdirFailure(operand, backend_error=error, uncertain=True)
    if mutation_error is not None:
        # Presence after a mutation-call exception is confirmed failure.
        return _RmdirFailure(operand, backend_error=mutation_error)
    return _RmdirFailure(operand, incompatible="result")


async def _remove_empty_directory(
    operand: _MappedOperand,
    filesystem: AsyncFileSystem,
) -> _RmdirFailure | None:
    directory_failure = await _require_directory(operand, filesystem)
    if directory_failure is not None:
        return directory_failure

    rmdir = getattr(filesystem, "_rmdir", None)
    if not callable(rmdir):
        return _RmdirFailure(
            operand,
            backend_error=NotImplementedError(f"{type(filesystem).__name__} lacks async _rmdir"),
        )

    mutation_error: Exception | None = None
    try:
        await rmdir(operand.path)
    except Exception as error:  # noqa: BLE001 - mutation may leave uncertain state.
        mutation_error = error

    return await _observe_post_mutation(operand, filesystem, mutation_error)


def _render_rmdir_backend_failure(
    command: str,
    operand: _MappedOperand,
    error: Exception,
) -> None:
    if isinstance(error, OSError) and error.errno == errno.ENOTEMPTY:
        _render_operand_diagnostic(command, operand, "directory not empty")
        return
    _render_backend_failure(command, operand, error)


def _render_failure(command: str, failure: _RmdirFailure) -> None:
    if failure.uncertain:
        _render_operand_diagnostic(command, failure.operand, "uncertain state")
    elif failure.incompatible == "directory":
        _render_operand_diagnostic(command, failure.operand, "not a directory")
    elif failure.incompatible == "result":
        _render_operand_diagnostic(command, failure.operand, "incompatible result")
    elif isinstance(failure.backend_error, NotADirectoryError):
        _render_operand_diagnostic(command, failure.operand, "not a directory")
    elif failure.backend_error is None:
        _render_operand_diagnostic(command, failure.operand, "incompatible result")
    else:
        _render_rmdir_backend_failure(command, failure.operand, failure.backend_error)


async def _trace_operands(
    operands: tuple[_MappedOperand, ...],
    filesystems: Mapping[str, AsyncFileSystem],
) -> tuple[_RmdirFailure, ...]:
    failures = []
    for operand in operands:
        result = await _remove_empty_directory(operand, filesystems[operand.name])
        if isinstance(result, _RmdirFailure):
            failures.append(result)
    return tuple(failures)


async def _run_rmdir(
    command: str,
    operands: tuple[_MappedOperand, ...],
    sources: Mapping[str, AsyncFilesystemSource],
) -> None:
    async def operation(filesystems: Mapping[str, AsyncFileSystem]) -> None:
        failures = await _trace_operands(operands, filesystems)
        if failures:
            _raise_operand_failures(command, failures, _render_failure)

    await _run_mapped_command(command, operands, sources, operation)
