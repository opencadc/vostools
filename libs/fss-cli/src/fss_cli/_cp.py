"""Typed request planning and async execution for verified ``cp``."""

from __future__ import annotations

import asyncio
import logging
import time
from collections.abc import Mapping
from dataclasses import dataclass
from functools import partial
from pathlib import Path
from typing import TYPE_CHECKING

from . import _copy_file
from ._command import (
    _CommandFailureError,
    _drain_current_operation,
    _Failure,
    _MappedOperand,
    _parse_mapped_operand,
    _render_backend_failure,
    _render_operand_diagnostic,
    _run_mapped_command,
    _usage_error,
)
from ._concurrent import _run_bounded
from ._copy_file import copy_on_backend, create_staging, discard_staging
from ._diagnostics import _render_diagnostic_value
from ._logging import _location, _log_file_operation, _redact
from ._manifest import _shared_tokens_match
from ._path import _lexical_basename, _lexical_join, _lexical_parent
from ._post_copy import (
    _DEFAULT_METADATA_WAIT_SECONDS,
    _PreviousFile,
    _report_gate,
    _report_ready,
    _require_file_size,
    _resolved_operand,
    _TransferProof,
    _verification_tokens,
    report_verified,
)
from ._progress import _transfer_display
from ._recursive_cp import _canonical_operand, _run_recursive_cp

if TYPE_CHECKING:
    from collections.abc import Collection

    from fsspec.asyn import AsyncFileSystem
    from fsspec.callbacks import Callback

    from ._app import AsyncFilesystemSource
    from ._progress import _Transfer, _TransferDisplay

_MIN_OPERAND_COUNT = 2
_LOGGER = logging.getLogger(__name__)


@dataclass(frozen=True)
class _CpRequest:
    source: _MappedOperand
    destination: _MappedOperand


@dataclass(frozen=True)
class _CpPlan:
    requests: tuple[_CpRequest, ...]
    require_directory: bool
    recursive: bool
    metadata_wait: int = _DEFAULT_METADATA_WAIT_SECONDS


@dataclass(frozen=True)
class _CpFailure(_Failure):
    """A copy failure: the shared shape plus this command's transfer state.

    ``category`` carries a copy-specific diagnostic that has no equivalent in
    the shared vocabulary; ``residue`` marks a failure that may have left a
    partial or unverified destination behind. ``verdict`` is a post-copy
    verification outcome, rendered verbatim against the resolved destination.
    """

    category: str | None = None
    residue: bool = False
    verdict: str | None = None


@dataclass(frozen=True)
class _PreparedTransfer:
    proof: _TransferProof
    resolved: str
    previous: _PreviousFile | None


def _cp_plan(
    command: str,
    operands: tuple[str, ...],
    known_names: Collection[str],
    *,
    recursive: bool,
    metadata_wait: int = _DEFAULT_METADATA_WAIT_SECONDS,
) -> _CpPlan:
    if len(operands) < _MIN_OPERAND_COUNT:
        _usage_error(command, "missing mapped filesystem operand")
    if recursive and len(operands) > _MIN_OPERAND_COUNT:
        _usage_error(command, "extra operand")

    mapped = tuple(_parse_mapped_operand(command, operand, known_names) for operand in operands)
    if recursive:
        mapped = (
            _canonical_operand(command, mapped[0], source=True),
            _canonical_operand(command, mapped[1], source=False),
        )
    destination = mapped[-1]
    return _CpPlan(
        requests=tuple(_CpRequest(source=source, destination=destination) for source in mapped[:-1]),
        require_directory=len(mapped) > _MIN_OPERAND_COUNT,
        recursive=recursive,
        metadata_wait=metadata_wait,
    )


async def _require_directory(
    destination: _MappedOperand,
    filesystem: AsyncFileSystem,
) -> _CpFailure | None:
    try:
        info = await _drain_current_operation(filesystem._info(destination.path))
    except Exception as error:  # noqa: BLE001 - classify awaited backend failure.
        return _CpFailure(destination, backend_error=error)
    if not isinstance(info, Mapping) or not isinstance(info.get("type"), str):
        return _CpFailure(destination, incompatible="result")
    if info["type"] == "file":
        return _CpFailure(destination, category="not a directory")
    if info["type"] != "directory":
        return _CpFailure(destination, incompatible="result")
    return None


def _require_source_file_size(
    source: _MappedOperand,
    info: object,
) -> int | _CpFailure:
    if not isinstance(info, Mapping):
        return _CpFailure(source, incompatible="result")
    result_type = info.get("type")
    if not isinstance(result_type, str):
        return _CpFailure(source, incompatible="result")
    if result_type == "directory":
        return _CpFailure(source, incompatible="directory")
    if result_type != "file":
        return _CpFailure(source, incompatible="result")
    size = _require_file_size(info)
    if size is None:
        return _CpFailure(source, incompatible="result")
    return size


async def _prepare_transfer(
    request: _CpRequest,
    source_filesystem: AsyncFileSystem,
    destination_filesystem: AsyncFileSystem,
) -> _PreparedTransfer | _CpFailure:
    """Read and validate the source, then resolve the destination path."""
    try:
        source_info = await _drain_current_operation(source_filesystem._info(request.source.path))
    except Exception as error:  # noqa: BLE001 - classify awaited backend failure.
        return _CpFailure(request.source, backend_error=error)

    expected_size = _require_source_file_size(request.source, source_info)
    if isinstance(expected_size, _CpFailure):
        return expected_size
    proof = _freeze_transfer_proof(source_info, expected_size)

    resolution = await _resolve_destination(
        request.destination,
        request.source.path,
        destination_filesystem,
    )
    if isinstance(resolution, _CpFailure):
        return resolution
    resolved, previous_info = resolution
    previous = (
        None
        if previous_info is None
        else _PreviousFile(
            size=_require_file_size(previous_info),
            tokens=_verification_tokens(previous_info),
        )
    )
    return _PreparedTransfer(proof, resolved, previous)


async def _resolve_destination(  # noqa: C901, PLR0911, PLR0912 - explicit target branches.
    destination: _MappedOperand,
    source_path: str,
    filesystem: AsyncFileSystem,
) -> tuple[str, Mapping[object, object] | None] | _CpFailure:
    """Resolve the target path and return the file ``_info`` it already holds, if any."""
    known_directory: str | None = None
    previous: Mapping[object, object] | None = None
    try:
        dest_info = await _drain_current_operation(filesystem._info(destination.path))
    except FileNotFoundError:
        resolved = destination.path
    except Exception as error:  # noqa: BLE001 - classify awaited backend failure.
        return _CpFailure(destination, backend_error=error)
    else:
        if not isinstance(dest_info, Mapping) or not isinstance(dest_info.get("type"), str):
            return _CpFailure(destination, incompatible="result")
        dest_type = dest_info["type"]
        if dest_type == "directory":
            known_directory = destination.path
            resolved = _lexical_join(
                destination.path,
                _lexical_basename(source_path),
            )
        elif dest_type == "file":
            resolved = destination.path
            previous = dest_info
        else:
            return _CpFailure(destination, incompatible="result")

    if resolved != destination.path:
        try:
            collision = await _drain_current_operation(filesystem._info(resolved))
        except FileNotFoundError:
            collision = None
        except Exception as error:  # noqa: BLE001 - classify awaited backend failure.
            return _CpFailure(destination, backend_error=error)
        if collision is not None:
            if not isinstance(collision, Mapping) or not isinstance(collision.get("type"), str):
                return _CpFailure(destination, incompatible="result")
            if collision["type"] == "directory":
                return _CpFailure(destination, incompatible="result")
            if collision["type"] != "file":
                return _CpFailure(destination, incompatible="result")
            previous = collision

    parent = _lexical_parent(resolved)
    if parent == known_directory:
        return resolved, previous
    try:
        parent_info = await _drain_current_operation(filesystem._info(parent))
    except Exception as error:  # noqa: BLE001 - classify awaited backend failure.
        return _CpFailure(destination, backend_error=error)

    if not isinstance(parent_info, Mapping) or not isinstance(parent_info.get("type"), str):
        return _CpFailure(destination, incompatible="result")
    if parent_info["type"] != "directory":
        category = "not a directory" if parent_info["type"] == "file" else "incompatible result"
        return _CpFailure(destination, category=category)

    return resolved, previous


async def _stage_remote(
    filesystem: AsyncFileSystem,
    remote: str,
    prefix: str,
    callback: Callback,
) -> tuple[str | None, Exception | None]:
    try:
        temporary = create_staging(prefix)
    except Exception as error:  # noqa: BLE001 - local staging creation boundary.
        return None, error
    try:
        await _drain_current_operation(filesystem._get_file(remote, temporary, callback=callback))
    except Exception as error:  # noqa: BLE001 - staging download boundary.
        _copy_file.remove_staging(temporary)
        return None, error
    except BaseException:
        discard_staging(temporary)
        raise
    return temporary, None


def _freeze_transfer_proof(
    source_info: object,
    expected_size: int,
) -> _TransferProof:
    return _TransferProof(
        expected_size=expected_size,
        tokens=tuple(_verification_tokens(source_info).items()),
    )


async def _verify_transfer(  # noqa: PLR0913 - one explicit transfer-proof boundary.
    source_filesystem: AsyncFileSystem,
    destination_filesystem: AsyncFileSystem,
    source_path: str,
    destination_path: str,
    proof: _TransferProof,
    destination: _MappedOperand,
    *,
    require_source_absent: bool,
) -> _CpFailure | None:
    try:
        destination_info = await _drain_current_operation(destination_filesystem._info(destination_path))
    except Exception as error:  # noqa: BLE001 - post-copy verify is residue-bearing.
        return _CpFailure(
            destination,
            backend_error=error,
            residue=True,
            category="verification failure",
        )

    verified_size = _require_file_size(destination_info)
    if (
        verified_size is None
        or verified_size != proof.expected_size
        or not _shared_tokens_match(proof.tokens, _verification_tokens(destination_info))
    ):
        return _CpFailure(
            destination,
            category="verification failure",
            residue=True,
        )

    if require_source_absent:
        try:
            await _drain_current_operation(source_filesystem._info(source_path))
        except FileNotFoundError:
            return None
        except Exception as error:  # noqa: BLE001 - post-move absence proof.
            return _CpFailure(
                destination,
                backend_error=error,
                category="verification failure",
                residue=True,
            )
        return _CpFailure(
            destination,
            category="verification failure",
            residue=True,
        )
    return None


async def _await_verified_copy(  # noqa: PLR0913 - maps the common verification result to command diagnostics.
    command: str,
    filesystem: AsyncFileSystem,
    prepared: _PreparedTransfer,
    destination: _MappedOperand,
    *,
    mutation: str,
    wait: int,
) -> _CpFailure | None:
    unverified = await report_verified(
        command,
        filesystem,
        destination,
        prepared.proof,
        prepared.previous,
        mutation=mutation,
        wait=wait,
    )
    if unverified is None:
        return None
    return _CpFailure(destination, backend_error=unverified.error, residue=True, verdict=unverified.text)


async def _confirmed_cross_source_cp_file(  # noqa: C901, PLR0913 - explicit copy outcomes.
    command: str,
    request: _CpRequest,
    source_filesystem: AsyncFileSystem,
    destination_filesystem: AsyncFileSystem,
    *,
    transfer: _Transfer,
    metadata_wait: int,
) -> _CpFailure | None:
    started = time.monotonic()
    prepared = await _prepare_transfer(
        request,
        source_filesystem,
        destination_filesystem,
    )
    if isinstance(prepared, _CpFailure):
        return prepared
    proof, resolved = prepared.proof, prepared.resolved
    target = _resolved_operand(request.destination, resolved)

    if source_filesystem is destination_filesystem and request.source.path == resolved:
        return _CpFailure(request.source, incompatible="same_path")

    temporary, staging_error = await _stage_remote(
        source_filesystem,
        request.source.path,
        "fss-cli-cp-",
        transfer.phase("download", request.source.spelling),
    )
    if staging_error is not None or temporary is None:
        return _CpFailure(
            request.source,
            backend_error=staging_error,
            category="staging failure",
        )
    _LOGGER.debug(
        "staged %s through %s",
        _location(request.source.name, request.source.path),
        _redact(temporary),
    )

    primary_failure: _CpFailure | None = None
    try:
        try:
            staged_size = Path(temporary).stat().st_size  # noqa: ASYNC240
        except Exception as error:  # noqa: BLE001 - local staging boundary.
            primary_failure = _CpFailure(
                request.source,
                backend_error=error,
                category="staging failure",
            )
        else:
            if staged_size != proof.expected_size:
                primary_failure = _CpFailure(
                    request.source,
                    category="verification failure",
                )

        if primary_failure is None:
            try:
                await _drain_current_operation(
                    destination_filesystem._put_file(
                        temporary,
                        resolved,
                        mode="overwrite",
                        callback=transfer.phase("upload", target.spelling),
                    )
                )
            except Exception as error:  # noqa: BLE001 - mutation may leave residue.
                primary_failure = _CpFailure(
                    request.destination,
                    backend_error=error,
                    uncertain=True,
                    residue=True,
                )

        transfer.finish()
        if primary_failure is None:
            primary_failure = await _await_verified_copy(
                command,
                destination_filesystem,
                prepared,
                target,
                mutation="upload",
                wait=metadata_wait,
            )
    except BaseException:
        discard_staging(temporary)
        raise

    cleanup_error = _copy_file.remove_staging(temporary)
    if primary_failure is not None:
        return primary_failure
    if cleanup_error is not None:
        return _CpFailure(
            request.destination,
            backend_error=cleanup_error,
            category="staging failure",
            residue=True,
        )
    _log_verified_transfer("staged", request, resolved, proof, started)
    return None


async def _confirmed_cp_file(
    command: str,
    request: _CpRequest,
    filesystem: AsyncFileSystem,
    *,
    transfer: _Transfer,
    metadata_wait: int,
) -> _CpFailure | None:
    started = time.monotonic()
    prepared = await _prepare_transfer(request, filesystem, filesystem)
    if isinstance(prepared, _CpFailure):
        return prepared
    proof, resolved = prepared.proof, prepared.resolved
    target = _resolved_operand(request.destination, resolved)

    if request.source.path == resolved:
        return _CpFailure(request.source, incompatible="same_path")

    try:
        copied = await copy_on_backend(
            filesystem, request.source.path, resolved, partial(transfer.phase, "copy", target.spelling)
        )
    except Exception as error:  # noqa: BLE001 - mutation may leave destination residue.
        return _CpFailure(
            request.destination,
            backend_error=error,
            uncertain=True,
            residue=True,
        )

    if not copied:
        return await _confirmed_cross_source_cp_file(
            command,
            request,
            filesystem,
            filesystem,
            transfer=transfer,
            metadata_wait=metadata_wait,
        )
    transfer.finish()
    failure = await _await_verified_copy(
        command,
        filesystem,
        prepared,
        target,
        mutation="copy",
        wait=metadata_wait,
    )
    if failure is not None:
        return failure
    _log_verified_transfer("copied", request, resolved, proof, started)
    return None


def _log_verified_transfer(
    outcome: str,
    request: _CpRequest,
    resolved: str,
    proof: _TransferProof,
    started: float,
) -> None:
    """Record one verified file operation for an embedding host."""
    _log_file_operation(
        _LOGGER,
        outcome,
        _location(request.source.name, request.source.path),
        _location(request.destination.name, resolved),
        proof.expected_size,
        started,
    )


def _render_staging_category(error: Exception) -> str:
    rendered_class = _render_diagnostic_value(type(error).__name__)
    return f"staging failure ({rendered_class})"


def _render_failure(  # noqa: C901 - stable diagnostic categories.
    command: str, failure: _CpFailure
) -> None:
    suffix = "; destination residue may remain" if failure.residue else ""
    if failure.uncertain:
        _render_operand_diagnostic(
            command,
            failure.operand,
            f"uncertain mutation state{suffix}",
        )
    elif failure.verdict is not None:
        _render_operand_diagnostic(command, failure.operand, f"{failure.verdict}{suffix}")
    elif failure.incompatible == "directory":
        _render_operand_diagnostic(command, failure.operand, "is a directory")
    elif failure.incompatible == "same_path":
        _render_operand_diagnostic(command, failure.operand, "same path")
    elif failure.incompatible == "result":
        _render_operand_diagnostic(command, failure.operand, "incompatible result")
    elif failure.category == "verification failure":
        _render_operand_diagnostic(
            command,
            failure.operand,
            f"verification failure{suffix}",
        )
    elif failure.category == "not a directory":
        _render_operand_diagnostic(command, failure.operand, "not a directory")
    elif failure.category == "staging failure" and failure.backend_error is not None:
        _render_operand_diagnostic(
            command,
            failure.operand,
            f"{_render_staging_category(failure.backend_error)}{suffix}",
        )
    elif isinstance(failure.backend_error, IsADirectoryError):
        _render_operand_diagnostic(command, failure.operand, "is a directory")
    elif isinstance(failure.backend_error, NotADirectoryError):
        _render_operand_diagnostic(command, failure.operand, "not a directory")
    elif failure.backend_error is None:
        _render_operand_diagnostic(
            command,
            failure.operand,
            failure.category or "incompatible result",
        )
    else:
        _render_backend_failure(command, failure.operand, failure.backend_error)


async def _copy_one_request(
    command: str,
    request: _CpRequest,
    filesystems: Mapping[str, AsyncFileSystem],
    transfer: _Transfer,
    metadata_wait: int,
) -> _CpFailure | None:
    """Copy one planned request on one backend or across two."""
    if request.source.name == request.destination.name:
        return await _confirmed_cp_file(
            command,
            request,
            filesystems[request.source.name],
            transfer=transfer,
            metadata_wait=metadata_wait,
        )
    return await _confirmed_cross_source_cp_file(
        command,
        request,
        filesystems[request.source.name],
        filesystems[request.destination.name],
        transfer=transfer,
        metadata_wait=metadata_wait,
    )


def _copy_destination_key(request: _CpRequest, *, into_directory: bool) -> tuple[str, str]:
    """The file path two copies must not write at the same time."""
    path = request.destination.path
    if into_directory:
        path = _lexical_join(path, _lexical_basename(request.source.path))
    return request.destination.name, path


async def _copy_requests_concurrently(
    command: str,
    plan: _CpPlan,
    filesystems: Mapping[str, AsyncFileSystem],
    display: _TransferDisplay,
) -> _CpFailure | None:
    """Copy every planned file under the shared limit, printing in argv order.

    Copies of the same destination path take a lock in submission order, so a
    repeated basename still finishes as the last operand.
    """
    locks: dict[tuple[str, str], asyncio.Lock] = {}
    keys: list[tuple[str, str]] = []
    for request in plan.requests:
        key = _copy_destination_key(request, into_directory=plan.require_directory)
        keys.append(key)
        locks.setdefault(key, asyncio.Lock())
    # Copies of one destination path stay in argv order. Outcome lines are
    # written as each copy produces them, so a failed write or an interrupt
    # still lands inside that copy.
    gates = [asyncio.Event() for _request in plan.requests]
    if gates:
        gates[0].set()

    async def run_one(index: int, request: _CpRequest) -> _CpFailure | None:
        gate_token = _report_gate.set(gates[index])
        ready_token = _report_ready.set(False)
        try:
            async with locks[keys[index]]:
                with display.transfer() as transfer:
                    result = await _copy_one_request(
                        command,
                        request,
                        filesystems,
                        transfer,
                        plan.metadata_wait,
                    )
            await gates[index].wait()
            return result
        finally:
            _report_gate.reset(gate_token)
            _report_ready.reset(ready_token)
            if index + 1 < len(gates):
                gates[index + 1].set()

    outcomes = await _run_bounded(
        [partial(run_one, index, request) for index, request in enumerate(plan.requests)],
        stop=lambda result: result is not None,
    )
    for outcome in outcomes:
        if outcome is None:
            break
        if outcome.error is not None:
            raise outcome.error
        if outcome.value is not None:
            return outcome.value
    return None


async def _copy_requests(
    command: str,
    plan: _CpPlan,
    filesystems: Mapping[str, AsyncFileSystem],
    display: _TransferDisplay,
) -> None:
    if plan.recursive:
        await _run_recursive_cp(
            command,
            plan.requests[0].source,
            plan.requests[0].destination,
            filesystems,
            display=display,
            metadata_wait=plan.metadata_wait,
        )
        return

    failure = None
    if plan.require_directory:
        failure = await _require_directory(
            plan.requests[0].destination,
            filesystems[plan.requests[0].destination.name],
        )
    if failure is None and plan.requests:
        failure = await _copy_requests_concurrently(command, plan, filesystems, display)
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


async def _run_cp(
    command: str,
    plan: _CpPlan,
    sources: Mapping[str, AsyncFilesystemSource],
) -> None:
    async def operation(filesystems: Mapping[str, AsyncFileSystem]) -> None:
        with _transfer_display() as display:
            await _copy_requests(command, plan, filesystems, display)

    await _run_mapped_command(
        command,
        (
            *(request.source for request in plan.requests),
            plan.requests[0].destination,
        ),
        sources,
        operation,
    )
