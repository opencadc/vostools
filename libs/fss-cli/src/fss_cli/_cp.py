"""Typed request planning and async execution for verified ``cp``."""

from __future__ import annotations

import logging
import os
import tempfile
import time
from collections.abc import Mapping
from contextlib import suppress
from dataclasses import dataclass
from pathlib import Path
from typing import TYPE_CHECKING

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
from ._diagnostics import _render_diagnostic_value
from ._logging import _location, _log_file_operation, _redact
from ._manifest import _TOKEN_ALIASES, _shared_tokens_match
from ._path import _lexical_basename, _lexical_join, _lexical_parent
from ._recursive_cp import _canonical_operand, _run_recursive_cp

if TYPE_CHECKING:
    from collections.abc import Collection

    from fsspec.asyn import AsyncFileSystem

    from ._app import AsyncFilesystemSource

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


@dataclass(frozen=True)
class _CpFailure(_Failure):
    """A copy failure: the shared shape plus this command's transfer state.

    ``category`` carries a copy-specific diagnostic that has no equivalent in
    the shared vocabulary; ``residue`` marks a failure that may have left a
    partial or unverified destination behind.
    """

    category: str | None = None
    residue: bool = False


@dataclass(frozen=True)
class _TransferProof:
    expected_size: int
    tokens: tuple[tuple[str, frozenset[str | bytes]], ...]


def _cp_plan(
    command: str,
    operands: tuple[str, ...],
    known_names: Collection[str],
    *,
    recursive: bool,
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


def _require_file_size(info: object) -> int | None:
    if not isinstance(info, Mapping):
        return None
    result_type = info.get("type")
    if not isinstance(result_type, str) or result_type != "file":
        return None
    size = info.get("size")
    if not isinstance(size, int) or isinstance(size, bool) or size < 0:
        return None
    return size


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
) -> tuple[_TransferProof, str] | _CpFailure:
    """Read and validate the source, then resolve the destination path."""
    try:
        source_info = await _drain_current_operation(source_filesystem._info(request.source.path))
    except Exception as error:  # noqa: BLE001 - classify awaited backend failure.
        return _CpFailure(request.source, backend_error=error)

    expected_size = _require_source_file_size(request.source, source_info)
    if isinstance(expected_size, _CpFailure):
        return expected_size
    proof = _freeze_transfer_proof(source_info, expected_size)

    resolved, resolution_failure = await _resolve_destination(
        request.destination,
        request.source.path,
        destination_filesystem,
    )
    if resolution_failure is not None:
        return resolution_failure
    return proof, resolved


async def _resolve_destination(  # noqa: C901, PLR0911, PLR0912 - explicit target branches.
    destination: _MappedOperand,
    source_path: str,
    filesystem: AsyncFileSystem,
) -> tuple[str, _CpFailure | None]:
    known_directory: str | None = None
    try:
        dest_info = await _drain_current_operation(filesystem._info(destination.path))
    except FileNotFoundError:
        resolved = destination.path
    except Exception as error:  # noqa: BLE001 - classify awaited backend failure.
        return destination.path, _CpFailure(destination, backend_error=error)
    else:
        if not isinstance(dest_info, Mapping) or not isinstance(dest_info.get("type"), str):
            return destination.path, _CpFailure(destination, incompatible="result")
        dest_type = dest_info["type"]
        if dest_type == "directory":
            known_directory = destination.path
            resolved = _lexical_join(
                destination.path,
                _lexical_basename(source_path),
            )
        elif dest_type == "file":
            resolved = destination.path
        else:
            return destination.path, _CpFailure(destination, incompatible="result")

    if resolved != destination.path:
        try:
            collision = await _drain_current_operation(filesystem._info(resolved))
        except FileNotFoundError:
            collision = None
        except Exception as error:  # noqa: BLE001 - classify awaited backend failure.
            return resolved, _CpFailure(destination, backend_error=error)
        if collision is not None:
            if not isinstance(collision, Mapping) or not isinstance(collision.get("type"), str):
                return resolved, _CpFailure(destination, incompatible="result")
            if collision["type"] == "directory":
                return resolved, _CpFailure(destination, incompatible="result")
            if collision["type"] != "file":
                return resolved, _CpFailure(destination, incompatible="result")

    parent = _lexical_parent(resolved)
    if parent == known_directory:
        return resolved, None
    try:
        parent_info = await _drain_current_operation(filesystem._info(parent))
    except Exception as error:  # noqa: BLE001 - classify awaited backend failure.
        return resolved, _CpFailure(destination, backend_error=error)

    if not isinstance(parent_info, Mapping) or not isinstance(parent_info.get("type"), str):
        return resolved, _CpFailure(destination, incompatible="result")
    if parent_info["type"] != "directory":
        category = "not a directory" if parent_info["type"] == "file" else "incompatible result"
        return resolved, _CpFailure(destination, category=category)

    return resolved, None


async def _stage_remote(
    filesystem: AsyncFileSystem,
    remote: str,
    prefix: str,
) -> tuple[str | None, Exception | None]:
    try:
        descriptor, temporary = tempfile.mkstemp(prefix=prefix)
    except Exception as error:  # noqa: BLE001 - local staging creation boundary.
        return None, error
    try:
        os.close(descriptor)
    except Exception as error:  # noqa: BLE001 - local staging descriptor boundary.
        _remove_temporary(temporary)
        return None, error
    except BaseException:
        _discard_temporary(temporary)
        raise

    try:
        await _drain_current_operation(filesystem._get_file(remote, temporary))
    except Exception as error:  # noqa: BLE001 - staging download boundary.
        _remove_temporary(temporary)
        return None, error
    except BaseException:
        _discard_temporary(temporary)
        raise
    return temporary, None


def _remove_temporary(path: str) -> Exception | None:
    try:
        Path(path).unlink()
    except FileNotFoundError:
        return None
    except Exception as error:  # noqa: BLE001 - local staging cleanup boundary.
        return error
    return None


def _discard_temporary(path: str) -> None:
    with suppress(BaseException):
        _remove_temporary(path)


def _verification_tokens(info: object) -> dict[str, frozenset[str | bytes]]:
    if not isinstance(info, Mapping):
        return {}
    tokens: dict[str, frozenset[str | bytes]] = {}
    for normalized, aliases in _TOKEN_ALIASES:
        values = frozenset(
            value for alias in aliases if (type(value := info.get(alias)) is str or type(value) is bytes)
        )
        if values:
            tokens[normalized] = values
    return tokens


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


async def _confirmed_cross_source_cp_file(  # noqa: C901 - explicit copy outcomes.
    request: _CpRequest,
    source_filesystem: AsyncFileSystem,
    destination_filesystem: AsyncFileSystem,
) -> _CpFailure | None:
    started = time.monotonic()
    prepared = await _prepare_transfer(
        request,
        source_filesystem,
        destination_filesystem,
    )
    if isinstance(prepared, _CpFailure):
        return prepared
    proof, resolved = prepared

    if source_filesystem is destination_filesystem and request.source.path == resolved:
        return _CpFailure(request.source, incompatible="same_path")

    temporary, staging_error = await _stage_remote(
        source_filesystem,
        request.source.path,
        "fss-cli-cp-",
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
    mutated = False
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
            mutated = True
            try:
                await _drain_current_operation(
                    destination_filesystem._put_file(
                        temporary,
                        resolved,
                        mode="overwrite",
                    )
                )
            except Exception as error:  # noqa: BLE001 - mutation may leave residue.
                primary_failure = _CpFailure(
                    request.destination,
                    backend_error=error,
                    uncertain=True,
                    residue=True,
                )

        if primary_failure is None:
            primary_failure = await _verify_transfer(
                source_filesystem,
                destination_filesystem,
                request.source.path,
                resolved,
                proof,
                request.destination,
                require_source_absent=False,
            )
    except BaseException:
        _discard_temporary(temporary)
        raise

    cleanup_error = _remove_temporary(temporary)
    if primary_failure is not None:
        return primary_failure
    if cleanup_error is not None:
        return _CpFailure(
            request.destination if mutated else request.source,
            backend_error=cleanup_error,
            category="staging failure",
            residue=mutated,
        )
    _log_verified_transfer("staged", request, resolved, proof, started)
    return None


async def _confirmed_cp_file(
    request: _CpRequest,
    filesystem: AsyncFileSystem,
) -> _CpFailure | None:
    started = time.monotonic()
    prepared = await _prepare_transfer(request, filesystem, filesystem)
    if isinstance(prepared, _CpFailure):
        return prepared
    proof, resolved = prepared

    if request.source.path == resolved:
        return _CpFailure(request.source, incompatible="same_path")

    try:
        await _drain_current_operation(
            filesystem._cp_file(
                request.source.path,
                resolved,
            )
        )
    except Exception as error:  # noqa: BLE001 - mutation may leave destination residue.
        return _CpFailure(
            request.destination,
            backend_error=error,
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
        require_source_absent=False,
    )
    if failure is None:
        _log_verified_transfer("copied", request, resolved, proof, started)
    return failure


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


async def _run_cp(
    command: str,
    plan: _CpPlan,
    sources: Mapping[str, AsyncFilesystemSource],
) -> None:
    async def operation(filesystems: Mapping[str, AsyncFileSystem]) -> None:
        if plan.recursive:
            await _run_recursive_cp(
                command,
                plan.requests[0].source,
                plan.requests[0].destination,
                filesystems,
            )
            return

        failure = None
        if plan.require_directory:
            failure = await _require_directory(
                plan.requests[0].destination,
                filesystems[plan.requests[0].destination.name],
            )
        for request in plan.requests if failure is None else ():
            if request.source.name == request.destination.name:
                failure = await _confirmed_cp_file(
                    request,
                    filesystems[request.source.name],
                )
            else:
                failure = await _confirmed_cross_source_cp_file(
                    request,
                    filesystems[request.source.name],
                    filesystems[request.destination.name],
                )
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

    await _run_mapped_command(
        command,
        (
            *(request.source for request in plan.requests),
            plan.requests[0].destination,
        ),
        sources,
        operation,
    )
