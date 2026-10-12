"""Normalized metadata execution for typed ``info``."""

from __future__ import annotations

import pprint
from collections.abc import Mapping
from dataclasses import dataclass
from typing import TYPE_CHECKING, cast

from ._command import (
    _binary_stdout,
    _CommandFailureError,
    _MappedOperand,
    _run_mapped_command,
    _write_binary,
)
from ._listing import ListingRow, to_listing

if TYPE_CHECKING:
    from fsspec.asyn import AsyncFileSystem

    from ._app import AsyncFilesystemSource

_PRETTY_WIDTH = 80


@dataclass(frozen=True, order=True)
class _StablePresentation:
    sort_type: str
    text: str

    def __repr__(self) -> str:
        return self.text


def _canonical_value(value: object, active: set[int]) -> object:
    if not isinstance(value, (Mapping, list, tuple, set, frozenset)):
        return value

    identity = id(value)
    if identity in active:
        message = "recursive metadata containers are not supported"
        raise ValueError(message)
    active.add(identity)
    try:
        if isinstance(value, Mapping):
            canonical: object = _canonical_mapping(cast("Mapping[object, object]", value), active)
        elif isinstance(value, list):
            canonical = [_canonical_value(item, active) for item in value]
        elif isinstance(value, tuple):
            canonical = tuple(_canonical_value(item, active) for item in value)
        elif isinstance(value, set):
            canonical = _canonical_set(cast("set[object]", value), active, frozen=False)
        else:
            canonical = _canonical_set(value, active, frozen=True)
        return canonical
    finally:
        active.remove(identity)


def _canonical_mapping(
    value: Mapping[object, object],
    active: set[int],
) -> dict[object, object]:
    source_length = len(value)
    entries: list[tuple[object, object]] = []
    spellings: set[str] = set()
    for key in value:
        item = value[key]
        canonical_key = _canonical_value(key, active)
        hash(canonical_key)
        spelling = _pretty(canonical_key)
        if spelling in spellings:
            message = "distinct mapping keys have the same presentation"
            raise ValueError(message)
        spellings.add(spelling)
        entries.append(
            (
                _StablePresentation(
                    f"{type(key).__module__}.{type(key).__qualname__}",
                    spelling,
                ),
                _canonical_value(item, active),
            )
        )

    if len(entries) != source_length:
        message = "mapping iteration does not match its reported length"
        raise ValueError(message)
    canonical = dict(entries)
    if len(canonical) != source_length:
        message = "canonical mapping keys are not distinct"
        raise ValueError(message)
    return canonical


def _canonical_set(
    value: set[object] | frozenset[object],
    active: set[int],
    *,
    frozen: bool,
) -> _StablePresentation:
    members = sorted(_pretty(_canonical_value(item, active)) for item in value)
    if frozen:
        spelling = "frozenset()" if not members else f"frozenset({{{', '.join(members)}}})"
    else:
        spelling = "set()" if not members else f"{{{', '.join(members)}}}"
    return _StablePresentation(f"{type(value).__module__}.{type(value).__qualname__}", spelling)


def _pretty(value: object) -> str:
    return pprint.pformat(value, width=_PRETTY_WIDTH, sort_dicts=True)


def _render_info(row: ListingRow) -> bytes:
    values = {
        "name": row.name,
        "kind": row.kind,
        "size": row.size,
        "mtime": row.mtime,
        "mode": row.mode,
        "nlink": row.nlink,
        "owner": row.owner,
        "group": row.group,
        "link_target": row.link_target,
        "extra": _canonical_value(row.extra, set()),
    }
    rendered = _pretty(values)
    return f"{rendered}\n".encode()


def _normalize_info(result: object) -> bytes | None:
    if not isinstance(result, Mapping):
        return None
    try:
        if any(type(key) is not str for key in result):
            return None
        row = to_listing(cast("Mapping[str, object]", result))
        return _render_info(row)
    except Exception:  # noqa: BLE001 - malformed mapping or printable value.
        return None


async def _read_info(
    operand: _MappedOperand,
    filesystem: AsyncFileSystem,
) -> bytes:
    try:
        result = await filesystem._info(operand.path)
    except Exception as error:
        raise _CommandFailureError(operand, error) from error
    payload = _normalize_info(result)
    if payload is None:
        raise _CommandFailureError(operand)
    return payload


async def _run_info(
    command: str,
    operand: _MappedOperand,
    sources: Mapping[str, AsyncFilesystemSource],
) -> None:
    async def execute(filesystems: Mapping[str, AsyncFileSystem]) -> None:
        payload = await _read_info(operand, filesystems[operand.name])
        try:
            stdout = _binary_stdout()
            _write_binary(stdout, payload)
            stdout.flush()
        except Exception as error:
            raise _CommandFailureError(error=error) from error

    await _run_mapped_command(
        command,
        (operand,),
        sources,
        execute,
        broken_pipe_exit_code=1,
    )
