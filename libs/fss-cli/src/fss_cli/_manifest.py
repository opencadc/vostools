"""Frozen, validated manifests built from a backend directory walk.

Recursive copy plans the whole transfer before it mutates anything, so this
module turns a backend's breadth-first ``_ls`` listings into an immutable manifest of
entries — or refuses. Per
:doc:`ADR 0006 <../../../../docs/adr/0006-treat-backend-results-as-untrusted-input>`,
every field of every returned row is validated here: a malformed path or a
duplicated child is not a rendering bug when the caller will go on to *delete
or overwrite* along these paths.

Three refusals are distinguished, because the command renders them differently:
an incompatible result, an entry type the profile excludes (a link or special
file), and a tree larger than the bound.
"""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from typing import TYPE_CHECKING, TypeAlias, cast

from ._metadata import snapshot_mapping
from ._path import (
    _has_dot_segment,
    _lexical_join,
    _lexical_relative,
    _same_lexical_path,
)
from ._walk import _IncompatibleListingError, _ListedRow, _ListingLimitError, _walk

if TYPE_CHECKING:
    from fsspec.asyn import AsyncFileSystem


_MAX_ENTRIES = 10_000
_WALK_ROW_LENGTH = 3
_TOKEN_ALIASES = (
    ("etag", ("ETag", "etag")),
    ("md5", ("md5",)),
    ("content-md5", ("content-md5", "content_md5")),
    ("checksum", ("checksum",)),
)


class _IncompatibleResultError(Exception):
    pass


class _UnsupportedEntryError(Exception):
    pass


class _EntryLimitError(Exception):
    pass


@dataclass(frozen=True)
class _ManifestEntry:
    relative: str
    path: str
    kind: str
    size: int | None
    tokens: tuple[tuple[str, str | bytes], ...]


_Manifest: TypeAlias = tuple[_ManifestEntry, ...]


@dataclass(frozen=True)
class _WalkRow:
    root: str
    entries: tuple[_ManifestEntry, ...]
    directory_paths: tuple[str, ...]


def _shared_tokens_match(
    source_tokens: tuple[tuple[str, object], ...],
    destination_tokens: Mapping[str, object],
) -> bool:
    """Check that every verification token present on both sides agrees."""
    return all(destination_tokens[name] == value for name, value in source_tokens if name in destination_tokens)


def _tokens(info: Mapping[object, object]) -> tuple[tuple[str, str | bytes], ...]:
    tokens: list[tuple[str, str | bytes]] = []
    for normalized, aliases in _TOKEN_ALIASES:
        present = [alias for alias in aliases if alias in info]
        if len(present) > 1:
            raise _IncompatibleResultError
        if present:
            value = info[present[0]]
            if type(value) is not str and type(value) is not bytes:
                raise _IncompatibleResultError
            tokens.append((normalized, value))
    return tuple(tokens)


def _entry(
    relative: str,
    path: str,
    info: object,
    *,
    expected_kind: str | None = None,
) -> _ManifestEntry:
    if not isinstance(info, Mapping):
        raise _IncompatibleResultError
    name = info.get("name")
    if type(name) is not str or not _same_lexical_path(name, path):
        raise _IncompatibleResultError
    try:
        typed_info = snapshot_mapping(cast("Mapping[object, object]", info))
    except Exception as error:
        raise _IncompatibleResultError from error
    islink = typed_info.get("islink", False)
    if type(islink) is not bool:
        raise _IncompatibleResultError
    kind = typed_info.get("type")
    if type(kind) is not str:
        raise _IncompatibleResultError
    if islink or kind not in {"directory", "file"}:
        raise _UnsupportedEntryError
    if expected_kind is not None and kind != expected_kind:
        raise _IncompatibleResultError
    size = None
    if kind == "file":
        size = typed_info.get("size")
        if not isinstance(size, int) or isinstance(size, bool) or size < 0:
            raise _IncompatibleResultError
    return _ManifestEntry(relative, path, kind, size, _tokens(typed_info))


def _walk_row(
    source_path: str,
    value: object,
    *,
    entry_capacity: int,
) -> _WalkRow:
    if type(value) is not tuple or len(value) != _WALK_ROW_LENGTH:
        raise _IncompatibleResultError
    root, directories, files = value
    if (
        type(root) is not str
        or not root.startswith("/")
        or "\0" in root
        or "\n" in root
        or "\r" in root
        or _has_dot_segment(root)
        or not isinstance(directories, Mapping)
        or not isinstance(files, Mapping)
    ):
        raise _IncompatibleResultError

    entries: list[_ManifestEntry] = []
    directory_paths: list[str] = []
    child_names: set[str] = set()
    for collection, kind in ((directories, "directory"), (files, "file")):
        if len(collection) > entry_capacity - len(entries):
            raise _EntryLimitError
        try:
            children = snapshot_mapping(collection)
        except Exception as error:
            raise _IncompatibleResultError from error
        for name, info in children.items():
            if (
                type(name) is not str
                or not name
                or name in {".", ".."}
                or "/" in name
                or "\0" in name
                or "\n" in name
                or "\r" in name
                or name in child_names
            ):
                raise _IncompatibleResultError
            child_names.add(name)
            path = _lexical_join(root, name)
            entry = _entry(
                _relative_path(source_path, path),
                path,
                info,
                expected_kind=kind,
            )
            entries.append(entry)
            if len(entries) > entry_capacity:
                raise _EntryLimitError
            if kind == "directory":
                directory_paths.append(path)
    return _WalkRow(root, tuple(entries), tuple(directory_paths))


class _ManifestBuilder:
    """Accept each frozen row once and retain only the final entry index."""

    def __init__(self, root: _ManifestEntry) -> None:
        self.entries = {"": root}
        self.roots: set[str] = set()
        self.expected_roots = {root.path}

    @property
    def capacity(self) -> int:
        return _MAX_ENTRIES - len(self.entries)

    def accept(self, row: _WalkRow) -> None:
        if (
            row.root in self.roots
            or row.root not in self.expected_roots
            or any(entry.relative in self.entries for entry in row.entries)
        ):
            raise _IncompatibleResultError
        self.roots.add(row.root)
        self.expected_roots.update(row.directory_paths)
        self.entries.update((entry.relative, entry) for entry in row.entries)

    def finish(self) -> _Manifest:
        if self.roots != self.expected_roots:
            raise _IncompatibleResultError
        return tuple(sorted(self.entries.values(), key=lambda item: item.relative))


async def _walk_rows(
    filesystem: AsyncFileSystem,
    root: _ManifestEntry,
) -> _Manifest:
    builder = _ManifestBuilder(root)

    def accept(row: _ListedRow) -> None:
        builder.accept(
            _walk_row(
                root.path,
                (row.root, row.directories, row.files),
                entry_capacity=builder.capacity,
            )
        )

    try:
        await _walk(
            filesystem,
            root.path,
            accept=accept,
            capacity=lambda: builder.capacity,
        )
    except _IncompatibleListingError as error:
        raise _IncompatibleResultError from error
    except _ListingLimitError as error:
        raise _EntryLimitError from error
    return builder.finish()


def _relative_path(root: str, path: str) -> str:
    relative = _lexical_relative(root, path)
    if relative is None:
        raise _IncompatibleResultError
    return relative


async def _manifest(
    filesystem: AsyncFileSystem,
    path: str,
    source_info: object,
) -> _Manifest:
    if not isinstance(source_info, Mapping):
        raise _IncompatibleResultError
    reported_path = source_info.get("name")
    if type(reported_path) is not str or not _same_lexical_path(reported_path, path):
        raise _IncompatibleResultError
    root_entry = _entry("", reported_path, source_info, expected_kind="directory")
    return await _walk_rows(filesystem, root_entry)
