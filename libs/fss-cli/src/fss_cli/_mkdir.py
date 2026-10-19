"""Typed async execution for ``mkdir``."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from typing import TYPE_CHECKING

from ._command import (
    _backend_category,
    _Failure,
    _MappedOperand,
    _raise_operand_failures,
    _render_operand_diagnostic,
    _run_mapped_command,
)
from ._command import _render_failure as _render_base_failure

if TYPE_CHECKING:
    from fsspec.asyn import AsyncFileSystem

    from ._app import AsyncFilesystemSource


@dataclass(frozen=True)
class _MkdirRequest:
    create_parents: bool
    operands: tuple[_MappedOperand, ...]


async def _run_mkdir(
    command: str,
    request: _MkdirRequest,
    sources: Mapping[str, AsyncFilesystemSource],
) -> None:
    async def operation(filesystems: Mapping[str, AsyncFileSystem]) -> None:
        failures = await _trace_operands(request, filesystems)
        if failures:
            _raise_operand_failures(command, failures, _render_failure)

    await _run_mapped_command(command, request.operands, sources, operation)


async def _trace_operands(
    request: _MkdirRequest,
    filesystems: Mapping[str, AsyncFileSystem],
) -> tuple[_Failure, ...]:
    failures = []
    for operand in request.operands:
        result = await _create_operand(
            operand,
            filesystems[operand.name],
            create_parents=request.create_parents,
        )
        if isinstance(result, _Failure):
            failures.append(result)
    return tuple(failures)


async def _create_operand(
    operand: _MappedOperand,
    filesystem: AsyncFileSystem,
    *,
    create_parents: bool,
) -> _Failure | None:
    try:
        if create_parents:
            await filesystem._makedirs(
                operand.path,
                exist_ok=True,
            )
        else:
            await filesystem._mkdir(
                operand.path,
                create_parents=False,
            )
    except Exception as error:  # noqa: BLE001 - classify awaited backend failure.
        return _Failure(operand, backend_error=error)

    try:
        info = await filesystem._info(operand.path)
    except Exception as error:  # noqa: BLE001 - post-mutation verify is uncertain.
        return _Failure(operand, backend_error=error, uncertain=True)

    if not isinstance(info, Mapping) or not isinstance(info.get("type"), str):
        return _Failure(operand, uncertain=True)

    if info["type"] != "directory":
        return _Failure(operand, uncertain=True)

    return None


def _render_failure(command: str, failure: _Failure) -> None:
    if not failure.uncertain:
        _render_base_failure(command, failure)
        return
    if failure.backend_error is None:
        category = "uncertain state (incompatible result)"
    else:
        category = f"uncertain state ({_backend_category(failure.backend_error)})"
    _render_operand_diagnostic(command, failure.operand, category)
