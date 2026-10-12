"""Typed ``head`` and ``tail`` runtime."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from typing import TYPE_CHECKING

from ._command import (
    _binary_stdout,
    _CommandFailureError,
    _MappedOperand,
    _run_mapped_command,
    _valid_size,
    _write_binary,
)

if TYPE_CHECKING:
    from collections.abc import Awaitable, Callable

    from fsspec.asyn import AsyncFileSystem

    from ._app import AsyncFilesystemSource


@dataclass(frozen=True)
class _ByteRangeRequest:
    count: int
    operand: _MappedOperand


async def _read_head(
    request: _ByteRangeRequest,
    filesystem: AsyncFileSystem,
) -> bytes:
    if request.count == 0:
        try:
            info = await filesystem._info(request.operand.path)
        except Exception as error:
            raise _CommandFailureError(request.operand, error) from error
        if _is_file(request.operand, info):
            return b""
    try:
        result = await filesystem._cat_file(
            request.operand.path,
            start=0,
            end=request.count,
        )
    except Exception as error:
        raise _CommandFailureError(request.operand, error) from error
    if type(result) is not bytes or len(result) > request.count:
        raise _CommandFailureError(request.operand)
    return result


def _info_field(operand: _MappedOperand, info: object, key: str) -> object:
    if not isinstance(info, Mapping):
        raise _CommandFailureError(operand)
    try:
        return info.get(key)
    except Exception:  # noqa: BLE001 - hostile metadata mapping boundary.
        raise _CommandFailureError(operand) from None


def _size_from_info(operand: _MappedOperand, info: object) -> int:
    size = _info_field(operand, info, "size")
    if not _valid_size(size):
        raise _CommandFailureError(operand)
    return size


def _is_file(operand: _MappedOperand, info: object) -> bool:
    """Return whether ``info`` proves a regular file, so a zero read needs no bytes.

    Any other kind falls back to the bounded read, so the backend still owns
    the diagnostic for directories and other non-file operands.
    """
    kind = _info_field(operand, info, "type")
    return type(kind) is str and kind == "file"


async def _read_tail(
    request: _ByteRangeRequest,
    filesystem: AsyncFileSystem,
) -> bytes:
    try:
        info = await filesystem._info(request.operand.path)
    except Exception as error:
        raise _CommandFailureError(request.operand, error) from error
    size = _size_from_info(request.operand, info)
    if request.count == 0 and _is_file(request.operand, info):
        return b""
    try:
        result = await filesystem._cat_file(
            request.operand.path,
            # A negative start is an fsspec suffix offset, not "from the start".
            start=max(0, size - request.count),
            end=None,
        )
    except Exception as error:
        raise _CommandFailureError(request.operand, error) from error
    if type(result) is not bytes or len(result) > request.count:
        raise _CommandFailureError(request.operand)
    return result


def _emit(payload: bytes) -> None:
    if not payload:
        return
    stdout = _binary_stdout()
    _write_binary(stdout, payload)
    stdout.flush()


async def _run_byte_range(
    command: str,
    count: int,
    operand: _MappedOperand,
    sources: Mapping[str, AsyncFilesystemSource],
    operation: Callable[
        [_ByteRangeRequest, AsyncFileSystem],
        Awaitable[bytes],
    ],
) -> None:
    request = _ByteRangeRequest(count, operand)

    async def execute(filesystems: Mapping[str, AsyncFileSystem]) -> None:
        payload = await operation(request, filesystems[operand.name])
        try:
            _emit(payload)
        except Exception as error:
            raise _CommandFailureError(error=error) from error

    await _run_mapped_command(command, (operand,), sources, execute)
