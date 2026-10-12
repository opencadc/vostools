"""Typed async execution for ``unlink``."""

from __future__ import annotations

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
class _UnlinkFailure(_Failure):
    """A failure raised on the single-file removal path.

    Structurally identical to the base failure; the distinct type is how ``rm``
    tells a file-path failure from a directory-path one, because the two render
    different diagnostic vocabularies for the same condition.
    """


async def _confirmed_rm_file(  # noqa: PLR0911 - explicit outcome branches.
    operand: _MappedOperand,
    filesystem: AsyncFileSystem,
) -> _UnlinkFailure | None:
    """Remove one source-reported file and confirm absence."""
    try:
        info = await filesystem._info(operand.path)
    except Exception as error:  # noqa: BLE001 - classify awaited backend failure.
        return _UnlinkFailure(operand, backend_error=error)

    if not isinstance(info, Mapping) or not isinstance(info.get("type"), str):
        return _UnlinkFailure(operand, incompatible="result")

    result_type = info["type"]
    if result_type == "directory":
        return _UnlinkFailure(operand, incompatible="directory")
    if result_type != "file":
        return _UnlinkFailure(operand, incompatible="result")

    try:
        await filesystem._rm_file(operand.path)
    except Exception as error:  # noqa: BLE001 - mutation may have partially applied.
        return _UnlinkFailure(operand, backend_error=error, uncertain=True)

    try:
        await filesystem._info(operand.path)
    except FileNotFoundError:
        return None
    except Exception as error:  # noqa: BLE001 - never hide non-not-found errors.
        return _UnlinkFailure(operand, backend_error=error, uncertain=True)
    else:
        return _UnlinkFailure(operand, uncertain=True)


def _render_failure(command: str, failure: _UnlinkFailure) -> None:
    if failure.uncertain:
        _render_operand_diagnostic(command, failure.operand, "uncertain mutation state")
    elif failure.incompatible == "directory":
        _render_operand_diagnostic(command, failure.operand, "is a directory")
    elif failure.incompatible == "result" or failure.backend_error is None:
        _render_operand_diagnostic(command, failure.operand, "incompatible result")
    else:
        _render_backend_failure(command, failure.operand, failure.backend_error)


async def _run_unlink(
    command: str,
    operand: _MappedOperand,
    sources: Mapping[str, AsyncFilesystemSource],
) -> None:
    async def operation(filesystems: Mapping[str, AsyncFileSystem]) -> None:
        failure = await _confirmed_rm_file(operand, filesystems[operand.name])
        if failure is not None:
            _raise_operand_failures(command, (failure,), _render_failure)

    await _run_mapped_command(command, (operand,), sources, operation)
