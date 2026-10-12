"""Reduced BSD/macOS ``stat`` execution for typed callbacks.

Owner and group names resolve through the local ``pwd``/``grp`` account
databases on a best-effort basis: they describe the local namespace, not the
remote source's, and fall back to the numeric id when a name is unknown or when
the host lacks these POSIX-only modules.
"""

from __future__ import annotations

import math
import stat as stat_module
import time
from collections.abc import Mapping
from dataclasses import dataclass
from functools import partial
from typing import TYPE_CHECKING, cast

from ._accounts import group_name as _group_name
from ._accounts import owner_name as _owner_name
from ._command import (
    _binary_stdout,
    _CommandFailureError,
    _Failure,
    _first_backend_error,
    _MappedOperand,
    _render_failure,
    _run_mapped_command,
    _write_binary,
)
from ._concurrent import _run_bounded

if TYPE_CHECKING:
    from fsspec.asyn import AsyncFileSystem

    from ._app import AsyncFilesystemSource

_MONTHS = (
    "Jan",
    "Feb",
    "Mar",
    "Apr",
    "May",
    "Jun",
    "Jul",
    "Aug",
    "Sep",
    "Oct",
    "Nov",
    "Dec",
)


@dataclass(frozen=True)
class _StatSuccess:
    operand: _MappedOperand
    line: bytes


def _format_mtime(mtime: float) -> str:
    local = time.localtime(mtime)
    month = _MONTHS[local.tm_mon - 1]
    return f"{month} {local.tm_mday:2d} {local.tm_hour:02d}:{local.tm_min:02d}:{local.tm_sec:02d} {local.tm_year}"


def _validate_info(  # noqa: C901, PLR0911, PLR0912 - locked Local-rich shape checks.
    info: object,
) -> Mapping[str, object] | None:
    if not isinstance(info, Mapping):
        return None
    mapping = cast("Mapping[str, object]", info)
    result_type = mapping.get("type")
    if result_type not in {"file", "directory"} or type(result_type) is not str:
        return None
    if "islink" in mapping:
        islink = mapping["islink"]
        if type(islink) is not bool or islink:
            return None
    name = mapping.get("name")
    if type(name) is not str:
        return None
    size = mapping.get("size")
    if type(size) is not int or size < 0:
        return None
    mode = mapping.get("mode")
    if type(mode) is not int:
        return None
    nlink = mapping.get("nlink")
    if type(nlink) is not int or nlink < 1:
        return None
    uid = mapping.get("uid")
    if type(uid) is not int or uid < 0:
        return None
    gid = mapping.get("gid")
    if type(gid) is not int or gid < 0:
        return None
    mtime = mapping.get("mtime")
    if type(mtime) is int:
        checked_mtime: float = float(mtime)
    elif type(mtime) is float:
        checked_mtime = mtime
    else:
        return None
    if not math.isfinite(checked_mtime):
        return None
    try:
        time.localtime(checked_mtime)
    except (OverflowError, OSError, ValueError):
        return None
    return mapping


def _render_line(operand: _MappedOperand, info: Mapping[str, object]) -> bytes:
    mode = cast("int", info["mode"])
    nlink = cast("int", info["nlink"])
    uid = cast("int", info["uid"])
    gid = cast("int", info["gid"])
    size = cast("int", info["size"])
    mtime = cast("float", info["mtime"])
    line = (
        f"{stat_module.filemode(mode)} {nlink} {_owner_name(uid)} {_group_name(gid)} "
        f'{size} "{_format_mtime(mtime)}" {operand.path}\n'
    )
    return line.encode()


def _write_line(line: bytes) -> None:
    stdout = _binary_stdout()
    _write_binary(stdout, line)
    stdout.flush()


async def _read_operand(
    operand: _MappedOperand,
    filesystem: AsyncFileSystem,
) -> _StatSuccess | _Failure:
    try:
        info = await filesystem._info(operand.path)
    except Exception as error:  # noqa: BLE001 - classify awaited backend failure.
        return _Failure(operand, backend_error=error)

    validated = _validate_info(info)
    if validated is None:
        return _Failure(operand, incompatible="result")
    return _StatSuccess(operand, _render_line(operand, validated))


async def _trace_operands(
    command: str,
    operands: tuple[_MappedOperand, ...],
    filesystems: Mapping[str, AsyncFileSystem],
) -> None:
    # Reads overlap under the shared bound; lines and diagnostics are still
    # emitted strictly in operand order, and an operand whose read raised
    # propagates at its own position, after every earlier operand's output.
    outcomes = await _run_bounded([partial(_read_operand, operand, filesystems[operand.name]) for operand in operands])
    failures: list[_Failure] = []
    for outcome in outcomes:
        if outcome is None:  # pragma: no cover - only after a raised read.
            break
        if outcome.error is not None:
            raise outcome.error
        result = cast("_StatSuccess | _Failure", outcome.value)
        if isinstance(result, _Failure):
            failures.append(result)
            try:
                _render_failure(command, result)
            except Exception as error:
                raise _CommandFailureError(
                    error=result.backend_error,
                    render=False,
                    propagate=error,
                ) from error
            continue
        try:
            _write_line(result.line)
        except Exception as error:
            raise _CommandFailureError(error=error) from error

    if failures:
        raise _CommandFailureError(
            error=_first_backend_error(failures),
            render=False,
        )


async def _run_stat(
    command: str,
    operands: tuple[_MappedOperand, ...],
    sources: Mapping[str, AsyncFilesystemSource],
) -> None:
    async def execute(filesystems: Mapping[str, AsyncFileSystem]) -> None:
        await _trace_operands(command, operands, filesystems)

    await _run_mapped_command(
        command,
        operands,
        sources,
        execute,
        broken_pipe_exit_code=1,
    )
