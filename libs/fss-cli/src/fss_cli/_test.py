"""Predicate execution for typed ``test``."""

from __future__ import annotations

from typing import TYPE_CHECKING, Literal

from ._command import (
    _CommandFailureError,
    _MappedOperand,
    _run_mapped_command,
)

if TYPE_CHECKING:
    from collections.abc import Mapping

    from fsspec.asyn import AsyncFileSystem

    from ._app import AsyncFilesystemSource

_Predicate = Literal["e", "d", "f"]


async def _evaluate(
    predicate: _Predicate,
    operand: _MappedOperand,
    filesystem: AsyncFileSystem,
) -> object:
    path = operand.path
    if predicate == "e":
        result = await filesystem._exists(path)
    elif predicate == "d":
        result = await filesystem._isdir(path)
    else:
        result = await filesystem._isfile(path)
    return result


async def _run_test(
    command: str,
    predicate: _Predicate,
    operand: _MappedOperand,
    sources: Mapping[str, AsyncFilesystemSource],
) -> None:
    async def execute(filesystems: Mapping[str, AsyncFileSystem]) -> None:
        try:
            result = await _evaluate(
                predicate,
                operand,
                filesystems[operand.name],
            )
        except Exception as error:
            raise _CommandFailureError(operand, error) from error
        if type(result) is not bool:
            raise _CommandFailureError(operand)
        if not result:
            raise _CommandFailureError(render=False)

    await _run_mapped_command(command, (operand,), sources, execute)
