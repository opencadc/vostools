"""Typed async execution for same-source file ``mv``."""

from __future__ import annotations

import inspect
import time
from dataclasses import dataclass
from typing import TYPE_CHECKING

from fsspec.asyn import AsyncFileSystem

from ._command import (
    _call,
    _CommandFailureError,
    _MappedOperand,
    _run_mapped_command,
    _usage_error,
)
from ._cp import (
    _CpFailure,
    _CpRequest,
    _log_verified_transfer,
    _prepare_transfer,
    _render_failure,
    _require_directory,
    _verify_transfer,
)

if TYPE_CHECKING:
    from collections.abc import Mapping as MappingType

    from ._app import AsyncFilesystemSource


@dataclass(frozen=True)
class _MvPlan:
    operands: tuple[_MappedOperand, ...]
    requests: tuple[_CpRequest, ...]
    require_directory: bool


def _plan_mv(
    command: str,
    operands: tuple[_MappedOperand, ...],
) -> _MvPlan:
    sources = operands[:-1]
    destination = operands[-1]
    if any(source.name != destination.name for source in sources):
        _usage_error(command, "cross-source move unsupported")
    return _MvPlan(
        operands=operands,
        requests=tuple(_CpRequest(source, destination) for source in sources),
        require_directory=len(sources) > 1,
    )


def _declared_move(filesystem: AsyncFileSystem) -> str | None:
    """Return the async move hook this filesystem actually implements.

    ``_mv_file`` is fsspec's hook. ``getattr`` sees an override inherited from
    a parent. The stock ``AsyncFileSystem._mv_file`` (copy, then delete) does
    not count: a class that declares ``_mv`` keeps that operation, and a
    filesystem with neither stays refused.
    """
    mv_file = getattr(filesystem, "_mv_file", None)
    implementation = getattr(mv_file, "__func__", mv_file)
    if inspect.iscoroutinefunction(mv_file) and implementation is not AsyncFileSystem._mv_file:
        return "_mv_file"
    declared = type(filesystem).__dict__.get("_mv")
    if inspect.iscoroutinefunction(declared):
        return "_mv"
    return None


async def _confirmed_mv_file(request: _CpRequest, filesystem: AsyncFileSystem) -> _CpFailure | None:
    started = time.monotonic()
    prepared = await _prepare_transfer(request, filesystem, filesystem)
    if isinstance(prepared, _CpFailure):
        return prepared
    proof, resolved = prepared.proof, prepared.resolved
    if request.source.path == resolved:
        return None
    operation = _declared_move(filesystem)
    if operation is None:
        return _CpFailure(
            request.destination,
            backend_error=NotImplementedError("_mv_file or _mv must be configured by source form"),
        )

    try:
        await _call(filesystem, operation, request.source.path, resolved)
    except Exception as operation_error:  # noqa: BLE001
        return _CpFailure(
            request.destination,
            backend_error=operation_error,
            uncertain=True,
            residue=True,
        )

    failure = await _verify_transfer(
        filesystem,
        filesystem,
        request.source.path,
        resolved,
        proof,
        request.destination,
        require_source_absent=True,
    )
    if failure is None:
        _log_verified_transfer("moved", request, resolved, proof, started)
    return failure


async def _run_mv(
    command: str,
    plan: _MvPlan,
    sources: MappingType[str, AsyncFilesystemSource],
) -> None:
    async def operation(filesystems: MappingType[str, AsyncFileSystem]) -> None:
        filesystem = filesystems[plan.requests[0].source.name]
        failure = None
        if plan.require_directory:
            failure = await _require_directory(
                plan.requests[0].destination,
                filesystem,
            )
        if failure is None:
            for request in plan.requests:
                failure = await _confirmed_mv_file(request, filesystem)
                if failure is not None:
                    break
        if failure is not None:
            try:
                _render_failure(command, failure)
            except Exception as error:
                raise _CommandFailureError(
                    error=failure.backend_error,
                    render=False,
                    propagate=error,
                ) from error
            raise _CommandFailureError(
                error=failure.backend_error,
                render=False,
            )

    await _run_mapped_command(command, plan.operands, sources, operation)
