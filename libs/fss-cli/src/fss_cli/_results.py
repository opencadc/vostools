"""Validate untrusted fsspec results once, at the hook boundary.

Integers stay exact (``bool`` is not an integer). ``str``, ``list`` and
``Mapping`` accept subclasses, because backends may return specialised
strings and lists. A mapping is snapshotted once.
"""

from __future__ import annotations

from collections.abc import Mapping
from functools import partial
from typing import TYPE_CHECKING, cast

from ._command import _call
from ._concurrent import _gather_bounded
from ._metadata import snapshot_mapping, valid_display_text
from ._path import _has_dot_segment, _lexical_components, _lexical_parent, _same_lexical_path

if TYPE_CHECKING:
    from fsspec.asyn import AsyncFileSystem


class _BadResultError(Exception):
    """A hook result does not have the shape the command can use."""


def snapshot_fields(value: object) -> tuple[Mapping[str, object], str, str]:
    """Snapshot one metadata mapping and return it with its name and type.

    ``str`` subclasses are accepted for both fields. The size is not checked.
    """
    if not isinstance(value, Mapping):
        raise _BadResultError
    try:
        raw = snapshot_mapping(cast("Mapping[object, object]", value))
    except Exception as error:
        raise _BadResultError from error
    name = raw.get("name")
    kind = raw.get("type")
    if not isinstance(name, str) or not isinstance(kind, str):
        raise _BadResultError
    return cast("Mapping[str, object]", raw), name, kind


def snapshot_listing(root: str, listing: object) -> dict[tuple[str, ...], Mapping[str, object]]:
    """Index a complete detail listing, refusing invalid or duplicated paths."""
    if not isinstance(listing, list):
        raise _BadResultError
    expected = len(listing)
    indexed: dict[tuple[str, ...], Mapping[str, object]] = {}
    for value in listing:
        info, name, _kind = snapshot_fields(value)
        path = name.rstrip("/")
        identity = _lexical_components(path)
        if (
            not name
            or not valid_display_text(name)
            or _has_dot_segment(name)
            or (not _same_lexical_path(path, root) and not _same_lexical_path(_lexical_parent(path), root))
            or identity in indexed
        ):
            raise _BadResultError
        indexed[identity] = info
    if len(listing) != expected or len(indexed) != expected:
        raise _BadResultError
    return indexed


async def _info(filesystem: AsyncFileSystem, path: str, *, missing_ok: bool) -> object | None:
    try:
        return await _call(filesystem, "_info", path)
    except FileNotFoundError:
        if not missing_ok:
            raise
        return None


async def info_or_missing(filesystem: AsyncFileSystem, path: str) -> tuple[object | None, Exception | None]:
    """Read optional metadata while preserving the command's error classification."""
    try:
        return await _info(filesystem, path, missing_ok=True), None
    except Exception as error:  # noqa: BLE001 - metadata acquisition boundary.
        return None, error


async def infos_for_paths(
    filesystem: AsyncFileSystem, paths: list[str], *, missing_ok: bool
) -> tuple[dict[str, object | None], Exception | None]:
    """Use validated parent listings, then bounded direct lookup for every gap."""
    parents = list(dict.fromkeys(_lexical_parent(path) for path in paths))

    async def listing(parent: str) -> dict[tuple[str, ...], Mapping[str, object]]:
        try:
            result = await _call(filesystem, "_ls", parent, detail=True)
        except (FileNotFoundError, PermissionError, NotImplementedError):
            return {}
        return snapshot_listing(parent, result)

    try:
        listings = dict(
            zip(parents, await _gather_bounded([partial(listing, parent) for parent in parents]), strict=True)
        )
        found: dict[str, object | None] = {}
        gaps: list[str] = []
        for path in paths:
            row = listings[_lexical_parent(path)].get(_lexical_components(path))
            if row is None or (row["type"] == "file" and row.get("size") is None):
                gaps.append(path)
            else:
                found[path] = row
        values = await _gather_bounded([partial(_info, filesystem, path, missing_ok=missing_ok) for path in gaps])
        found.update(zip(gaps, values, strict=True))
    except Exception as error:  # noqa: BLE001 - command owns diagnostics and mutation policy.
        return {}, error
    return found, None
