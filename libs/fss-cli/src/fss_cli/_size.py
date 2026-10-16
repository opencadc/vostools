"""Exact-size execution for typed ``size``."""

from __future__ import annotations

from typing import TYPE_CHECKING

import typer

from ._command import (
    _CommandFailureError,
    _MappedOperand,
    _run_mapped_command,
    _valid_size,
)

if TYPE_CHECKING:
    from collections.abc import Mapping

    from fsspec.asyn import AsyncFileSystem

    from ._app import AsyncFilesystemSource


def _group_operands(
    operands: tuple[_MappedOperand, ...],
) -> dict[str, list[tuple[int, _MappedOperand]]]:
    groups: dict[str, list[tuple[int, _MappedOperand]]] = {}
    for index, operand in enumerate(operands):
        groups.setdefault(operand.name, []).append((index, operand))
    return groups


async def _measure(
    operands: tuple[_MappedOperand, ...],
    filesystems: Mapping[str, AsyncFileSystem],
) -> str:
    if len(operands) == 1:
        return await _measure_one(operands[0], filesystems)
    return await _measure_many(operands, filesystems)


async def _measure_one(
    operand: _MappedOperand,
    filesystems: Mapping[str, AsyncFileSystem],
) -> str:
    try:
        result = await filesystems[operand.name]._size(operand.path)
    except Exception as error:
        raise _CommandFailureError(operand, error) from error
    if not _valid_size(result):
        raise _CommandFailureError(operand)
    return f"{result}\t{operand.spelling}\n"


async def _measure_many(
    operands: tuple[_MappedOperand, ...],
    filesystems: Mapping[str, AsyncFileSystem],
) -> str:
    sizes: dict[int, int] = {}
    for name, group in _group_operands(operands).items():
        paths = [operand.path for _, operand in group]
        try:
            result = await filesystems[name]._sizes(paths)
        except Exception as error:
            raise _CommandFailureError(group[0][1], error) from error
        if type(result) is not list or len(result) != len(group):
            raise _CommandFailureError(group[0][1])
        for (index, operand), size in zip(group, result, strict=True):
            if not _valid_size(size):
                raise _CommandFailureError(operand)
            sizes[index] = size

    return "".join(f"{sizes[index]}\t{operand.spelling}\n" for index, operand in enumerate(operands))


async def _run_size(
    command: str,
    operands: tuple[_MappedOperand, ...],
    sources: Mapping[str, AsyncFilesystemSource],
) -> None:
    async def execute(filesystems: Mapping[str, AsyncFileSystem]) -> None:
        result = await _measure(operands, filesystems)
        try:
            typer.echo(result, nl=False, color=True)
        except Exception as error:
            raise _CommandFailureError(error=error) from error

    await _run_mapped_command(
        command,
        operands,
        sources,
        execute,
        broken_pipe_exit_code=1,
    )
