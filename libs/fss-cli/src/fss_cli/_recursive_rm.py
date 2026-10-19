"""Capability-gated, manifest-verified recursive ``rm``."""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from functools import partial
from typing import TYPE_CHECKING, cast

from ._command import (
    _backend_category,
    _call,
    _render_operand_diagnostic,
)
from ._concurrent import _gather_bounded, _outcome_value, _run_bounded
from ._metadata import snapshot_mapping
from ._path import (
    _has_dot_segment,
    _lexical_parent,
)

if TYPE_CHECKING:
    from fsspec.asyn import AsyncFileSystem

    from ._command import _MappedOperand

_REQUIRED_HOOKS = ("_info", "_ls", "_rm_file", "_rmdir")


class _IncompatibleManifestError(Exception):
    pass


class _UnsupportedEntryError(Exception):
    pass


@dataclass(frozen=True)
class _ManifestEntry:
    path: str
    kind: str


@dataclass(frozen=True)
class _Manifest:
    root: str
    entries: tuple[_ManifestEntry, ...]


@dataclass(frozen=True)
class _RecursiveRmFailure:
    operand: _MappedOperand
    category: str
    backend_error: Exception | None = None
    root_missing: bool = False


def _has_required_hooks(filesystem: AsyncFileSystem) -> bool:
    return all(callable(getattr(filesystem, name, None)) for name in _REQUIRED_HOOKS)


def _is_contained(root: str, path: str) -> bool:
    return path == root or path.startswith(f"{root}/")


def _freeze_mapping(value: object) -> Mapping[object, object]:
    if not isinstance(value, Mapping):
        raise _IncompatibleManifestError
    try:
        return cast("Mapping[object, object]", snapshot_mapping(value))
    except Exception as error:
        raise _IncompatibleManifestError from error


def _entry_fields(value: object) -> tuple[str, str]:
    info = _freeze_mapping(value)
    name = info.get("name")
    if type(name) is not str:
        raise _IncompatibleManifestError
    islink = info.get("islink", False)
    if type(islink) is not bool:
        raise _IncompatibleManifestError
    if islink:
        raise _UnsupportedEntryError
    kind = info.get("type")
    if type(kind) is not str:
        raise _IncompatibleManifestError
    return name, kind


def _root_entry(path: str, value: object) -> _ManifestEntry:
    name, kind = _entry_fields(value)
    if name.rstrip("/") != path:
        raise _IncompatibleManifestError
    if kind != "directory":
        raise NotADirectoryError(path)
    return _ManifestEntry(path, kind)


def _listed_entry(parent: str, root: str, value: object) -> _ManifestEntry:
    name, kind = _entry_fields(value)
    if kind not in {"file", "directory"}:
        raise _UnsupportedEntryError
    if (
        not name
        or name.rstrip("/") != name
        or "\0" in name
        or "\n" in name
        or "\r" in name
        or _has_dot_segment(name)
        or not _is_contained(root, name)
        or _lexical_parent(name) != parent
    ):
        raise _IncompatibleManifestError
    return _ManifestEntry(name, kind)


def _frozen_listing(
    directory: str,
    root: str,
    result: object,
    seen: set[str],
) -> list[_ManifestEntry]:
    """Validate one directory's ``_ls(detail=True)`` result into child entries."""
    if not isinstance(result, list):
        raise _IncompatibleManifestError
    frozen: list[_ManifestEntry] = []
    expected_length = len(result)
    try:
        for value in result:
            entry = _listed_entry(directory, root, value)
            if entry.path in seen:
                raise _IncompatibleManifestError  # noqa: TRY301
            seen.add(entry.path)
            frozen.append(entry)
    except (_IncompatibleManifestError, _UnsupportedEntryError):
        raise
    except Exception as error:
        raise _IncompatibleManifestError from error
    if len(result) != expected_length or len(frozen) != expected_length:
        raise _IncompatibleManifestError
    return frozen


async def _manifest(
    filesystem: AsyncFileSystem,
    root: str,
    root_info: object,
) -> _Manifest:
    """List the tree breadth-first, then freeze it in leaves-first order.

    Every directory of one depth is listed concurrently under the shared
    bound; a listing failure at any depth fails the plan. Entries are emitted
    depth-first in path order with each directory after its children.
    """
    root_entry = _root_entry(root, root_info)
    seen = {root}
    children: dict[str, list[_ManifestEntry]] = {}
    level = [root_entry]
    while level:
        listings = await _gather_bounded(
            [partial(_call, filesystem, "_ls", directory.path, detail=True) for directory in level]
        )
        next_level: list[_ManifestEntry] = []
        for directory, result in zip(level, listings, strict=True):
            frozen = _frozen_listing(directory.path, root, result, seen)
            children[directory.path] = frozen
            next_level.extend(entry for entry in frozen if entry.kind == "directory")
        level = next_level

    entries: list[_ManifestEntry] = []
    stack = [(root_entry, False)]
    while stack:
        candidate, visited = stack.pop()
        if candidate.kind == "file" or visited:
            entries.append(candidate)
            continue
        stack.append((candidate, True))
        stack.extend(
            (entry, False)
            for entry in sorted(
                children[candidate.path],
                key=lambda item: item.path,
                reverse=True,
            )
        )

    manifest = _Manifest(root, tuple(entries))
    _revalidate_manifest(manifest)
    return manifest


def _revalidate_manifest(manifest: _Manifest) -> None:
    if not manifest.entries or manifest.entries[-1] != _ManifestEntry(manifest.root, "directory"):
        raise _IncompatibleManifestError
    seen: set[str] = set()
    directories = {manifest.root}
    for entry in manifest.entries:
        if entry.path in seen or not _is_contained(manifest.root, entry.path):
            raise _IncompatibleManifestError
        seen.add(entry.path)
        if entry.kind == "directory":
            directories.add(entry.path)
    for entry in manifest.entries[:-1]:
        if _lexical_parent(entry.path) not in directories:
            raise _IncompatibleManifestError


def _read_failure(
    operand: _MappedOperand,
    error: Exception,
    *,
    root_missing: bool = False,
) -> _RecursiveRmFailure:
    return _RecursiveRmFailure(
        operand,
        _backend_category(error),
        backend_error=error,
        root_missing=root_missing,
    )


async def _plan(
    operand: _MappedOperand,
    filesystem: AsyncFileSystem,
) -> _Manifest | _RecursiveRmFailure:
    if not _has_required_hooks(filesystem):
        return _RecursiveRmFailure(operand, "unsupported operation")
    root = operand.path.rstrip("/")
    try:
        root_info = await _call(filesystem, "_info", root)
    except Exception as error:  # noqa: BLE001 - classify read boundary.
        return _read_failure(
            operand,
            error,
            root_missing=isinstance(error, FileNotFoundError),
        )
    try:
        return await _manifest(filesystem, root, root_info)
    except _UnsupportedEntryError:
        return _RecursiveRmFailure(operand, "unsupported operation")
    except _IncompatibleManifestError:
        return _RecursiveRmFailure(operand, "incompatible result")
    except Exception as error:  # noqa: BLE001 - classify planning boundary.
        return _read_failure(operand, error)


async def _remove_entry(
    operand: _MappedOperand,
    filesystem: AsyncFileSystem,
    manifest: _Manifest,
    entry: _ManifestEntry,
) -> _RecursiveRmFailure | None:
    """Remove one entry, then prove its absence."""
    if not _is_contained(manifest.root, entry.path):
        return _RecursiveRmFailure(operand, "incompatible result")
    operation = "_rm_file" if entry.kind == "file" else "_rmdir"
    try:
        await _call(filesystem, operation, entry.path)
    except Exception as error:  # noqa: BLE001 - mutation may be partial.
        return _RecursiveRmFailure(
            operand,
            "recursive removal incomplete; residue possible",
            backend_error=error,
        )
    try:
        await _call(filesystem, "_info", entry.path)
    except FileNotFoundError:
        return None
    except Exception as error:  # noqa: BLE001 - absence remains uncertain.
        return _RecursiveRmFailure(
            operand,
            "recursive removal incomplete; residue possible",
            backend_error=error,
        )
    return _RecursiveRmFailure(
        operand,
        "recursive removal incomplete; residue possible",
    )


def _depth(manifest: _Manifest, entry: _ManifestEntry) -> int:
    return entry.path[len(manifest.root) :].count("/")


async def _mutate(
    operand: _MappedOperand,
    filesystem: AsyncFileSystem,
    manifest: _Manifest,
) -> _RecursiveRmFailure | None:
    """Remove the manifest leaves-first, one depth at a time.

    Every entry of the deepest remaining depth is removed and proven absent
    concurrently under the shared bound, so a directory is removed only after
    all of its children are confirmed gone. After the first failure no new
    removal starts; in-flight ones finish and the first failure in manifest
    order is reported.
    """
    try:
        _revalidate_manifest(manifest)
    except _IncompatibleManifestError:
        return _RecursiveRmFailure(operand, "incompatible result")

    levels: dict[int, list[_ManifestEntry]] = {}
    for entry in manifest.entries:
        levels.setdefault(_depth(manifest, entry), []).append(entry)
    for depth in sorted(levels, reverse=True):
        outcomes = await _run_bounded(
            [partial(_remove_entry, operand, filesystem, manifest, entry) for entry in levels[depth]],
            stop=lambda failure: failure is not None,
        )
        for outcome in outcomes:
            if outcome is None:
                continue
            failure = _outcome_value(outcome)
            if failure is not None:
                return failure
    return None


async def _remove_recursive(
    operand: _MappedOperand,
    filesystem: AsyncFileSystem,
) -> _RecursiveRmFailure | None:
    plan = await _plan(operand, filesystem)
    if isinstance(plan, _RecursiveRmFailure):
        return plan
    return await _mutate(operand, filesystem, plan)


def _render_recursive_failure(
    command: str,
    failure: _RecursiveRmFailure,
) -> None:
    _render_operand_diagnostic(command, failure.operand, failure.category)
