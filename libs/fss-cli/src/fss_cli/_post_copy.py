"""Post-copy destination verification shared by file and recursive ``cp``."""

from __future__ import annotations

import asyncio
import time
from collections.abc import Mapping
from contextvars import ContextVar
from dataclasses import dataclass
from typing import TYPE_CHECKING

from ._command import (
    _CommandFailureError,
    _drain_current_operation,
    _MappedOperand,
    _render_operand_diagnostic,
)
from ._diagnostics import _render_diagnostic_value
from ._manifest import _TOKEN_ALIASES

if TYPE_CHECKING:
    from fsspec.asyn import AsyncFileSystem

_DEFAULT_METADATA_WAIT_SECONDS = 60
_METADATA_POLL_SECONDS = 5
_report_gate: ContextVar[asyncio.Event | None] = ContextVar("fss_cli_report_gate", default=None)
_report_ready: ContextVar[bool] = ContextVar("fss_cli_report_ready", default=False)


@dataclass(frozen=True)
class _TransferProof:
    expected_size: int
    tokens: tuple[tuple[str, frozenset[str | bytes]], ...]


@dataclass(frozen=True)
class _PreviousFile:
    """The file a resolved destination held before the copy, from resolution ``_info``."""

    size: int | None
    tokens: Mapping[str, frozenset[str | bytes]]


@dataclass(frozen=True)
class _Verdict:
    """One classified post-copy ``_info``; ``text`` is ``None`` once verified."""

    text: str | None
    stale: bool = False


@dataclass(frozen=True)
class _Unverified:
    """Why a copied file did not verify; ``error`` is a raised ``_info`` failure."""

    text: str
    error: Exception | None = None


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


def _post_copy_verdict(  # noqa: PLR0911 - one return per verification outcome.
    info: object,
    proof: _TransferProof,
    previous: _PreviousFile | None,
    mutation: str,
) -> _Verdict:
    """Classify one post-copy destination ``_info`` against the frozen proof.

    Stale means the reported metadata still looks like no size, or like the
    file that was there before this copy; only a stale verdict is polled.
    """
    if not isinstance(info, Mapping) or not isinstance(info.get("type"), str):
        return _Verdict("verification failure")
    if info["type"] != "file":
        return _Verdict(f"not a file after {mutation}")
    observed = _require_file_size(info)
    if observed is None:
        return _Verdict("verification failure")
    expected = proof.expected_size
    if observed != expected:
        if observed == 0 or (previous is not None and observed == previous.size):
            return _Verdict(f"not yet verified (size {observed}, expected {expected})", stale=True)
        return _Verdict(f"size mismatch (expected {expected}, observed {observed})")
    tokens = _verification_tokens(info)
    differing = [name for name, values in proof.tokens if name in tokens and tokens[name] != values]
    if not differing:
        return _Verdict(None)
    changed = [name for name in differing if previous is None or previous.tokens.get(name) != tokens[name]]
    if changed:
        return _Verdict(f"metadata mismatch ({changed[0]})")
    return _Verdict(f"not yet verified ({differing[0]} unchanged)", stale=True)


async def _await_verified(  # noqa: PLR0913 - one explicit post-copy proof boundary.
    filesystem: AsyncFileSystem,
    path: str,
    proof: _TransferProof,
    previous: _PreviousFile | None,
    *,
    mutation: str,
    wait: int,
) -> _Unverified | None:
    """Read destination ``_info`` until it matches the proof, stops being stale, or ``wait`` ends."""
    deadline = time.monotonic() + wait
    while True:
        try:
            info = await _drain_current_operation(filesystem._info(path))
        except Exception as error:  # noqa: BLE001 - post-copy verify is residue-bearing.
            rendered_class = _render_diagnostic_value(type(error).__name__)
            return _Unverified(f"verification failure ({rendered_class})", error)
        verdict = _post_copy_verdict(info, proof, previous, mutation)
        if verdict.text is None:
            return None
        remaining = deadline - time.monotonic()
        if not verdict.stale or remaining <= 0:
            return _Unverified(verdict.text)
        await asyncio.sleep(min(_METADATA_POLL_SECONDS, remaining))


async def report_verified(  # noqa: PLR0913 - shared per-file outcome and verification contract.
    command: str,
    filesystem: AsyncFileSystem,
    target: _MappedOperand,
    proof: _TransferProof,
    previous: _PreviousFile | None,
    *,
    mutation: str,
    wait: int,
) -> _Unverified | None:
    """Report a completed transfer and its proof in this operand's output turn."""
    await _await_report_turn()
    verb = "copied" if mutation == "copy" else "uploaded"
    _report(command, target, f"{verb} {proof.expected_size} bytes")
    unverified = await _await_verified(filesystem, target.path, proof, previous, mutation=mutation, wait=wait)
    if unverified is None:
        _report(command, target, f"verified {proof.expected_size} bytes")
    return unverified


def _resolved_operand(destination: _MappedOperand, resolved: str) -> _MappedOperand:
    return _MappedOperand(
        spelling=f"{destination.name}:{resolved}",
        name=destination.name,
        path=resolved,
    )


async def _await_report_turn() -> None:
    """Let earlier multi-source copies finish printing before this one does.

    The wait happens once per copy, immediately before its first outcome line,
    so the byte transfer itself still overlaps and a failed write still aborts
    the rest of that copy.
    """
    gate = _report_gate.get()
    if gate is None or _report_ready.get():
        return
    await gate.wait()
    _report_ready.set(True)


def _report(command: str, operand: _MappedOperand, outcome: str) -> None:
    """Write one copy outcome line to stderr; a failed write ends the invocation."""
    try:
        _render_operand_diagnostic(command, operand, outcome)
    except Exception as error:
        raise _CommandFailureError(error=error, render=False, propagate=error) from error
