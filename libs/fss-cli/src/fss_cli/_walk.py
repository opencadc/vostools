"""Breadth-first directory walks over bounded concurrent ``_ls`` listings.

fsspec's inherited ``_walk`` lists one directory at a time, and its nested
calls do not forward ``on_error``: with ``on_error="raise"`` an unreadable
*descendant* is silently treated as empty, so a walk can report success with
content missing. Commands that walk a tree therefore list it here instead:
every directory of one depth is listed concurrently under the shared bound,
and a listing failure at **any** depth raises.

Rows have the shape fsspec's ``_walk(detail=True)`` yields — the listed root
plus child directory and file info mappings keyed by basename, with a
file-like entry for the root itself keyed ``""`` — so each command keeps its
own row validation. Listing results are untrusted (ADR 0006): only the shape
needed to form a row is checked here; callers validate the rest.
"""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from functools import partial
from typing import TYPE_CHECKING, cast

from ._command import _call
from ._concurrent import _outcome_value, _run_bounded
from ._metadata import snapshot_mapping
from ._path import _has_dot_segment, _lexical_parent, _same_lexical_path

if TYPE_CHECKING:
    from collections.abc import Callable

    from fsspec.asyn import AsyncFileSystem


class _IncompatibleListingError(Exception):
    """A listing whose shape cannot form a walk row."""


class _ListingLimitError(Exception):
    """A listing longer than the caller's remaining entry capacity."""


@dataclass(frozen=True)
class _ListedRow:
    """One listed directory, shaped like an fsspec ``_walk(detail=True)`` row."""

    root: str
    directories: Mapping[str, Mapping[object, object]]
    files: Mapping[str, Mapping[object, object]]


def _info_fields(value: object) -> tuple[Mapping[object, object], str, str]:
    if not isinstance(value, Mapping):
        raise _IncompatibleListingError
    try:
        info = snapshot_mapping(cast("Mapping[object, object]", value))
    except Exception as error:
        raise _IncompatibleListingError from error
    name = info.get("name")
    kind = info.get("type")
    if type(name) is not str or type(kind) is not str:
        raise _IncompatibleListingError
    return info, name, kind


def _listed_row(
    root: str,
    listing: object,
    capacity: int | None,
) -> tuple[_ListedRow, tuple[str, ...]]:
    """Freeze one ``_ls(detail=True)`` result into a row and its child paths.

    The listing length is checked against ``capacity`` before any entry is
    read, so an oversized listing is refused without materializing it.
    """
    if type(listing) is not list:
        raise _IncompatibleListingError
    if capacity is not None and len(listing) > capacity:
        raise _ListingLimitError
    expected_length = len(listing)
    stripped_root = root.rstrip("/")
    directories: dict[str, Mapping[object, object]] = {}
    files: dict[str, Mapping[object, object]] = {}
    children: list[str] = []
    for value in listing:
        info, name, kind = _info_fields(value)
        pathname = name.rstrip("/")
        if pathname == stripped_root:
            collection, key = files, ""
        elif _has_dot_segment(pathname) or not _same_lexical_path(_lexical_parent(pathname), root):
            raise _IncompatibleListingError
        else:
            key = pathname.rsplit("/", 1)[-1]
            collection = directories if kind == "directory" else files
        if key in directories or key in files:
            raise _IncompatibleListingError
        collection[key] = info
        if collection is directories:
            children.append(pathname)
    if len(listing) != expected_length:
        raise _IncompatibleListingError
    return _ListedRow(root, directories, files), tuple(children)


async def _walk(
    filesystem: AsyncFileSystem,
    root: str,
    *,
    maxdepth: int | None = None,
    accept: Callable[[_ListedRow], None] | None = None,
    capacity: Callable[[], int] | None = None,
) -> list[_ListedRow]:
    """List ``root`` and its descendants breadth-first.

    Every directory of one depth is listed concurrently under the shared
    bound. Rows are frozen and passed to ``accept`` in breadth-first order,
    before any directory of the next depth is listed, so a caller rejecting a
    row stops the walk before it reads further.

    Args:
        filesystem: The async filesystem to list.
        root: The directory (or file) to walk, passed to ``_ls`` literally.
        maxdepth: Number of directory levels to list; ``None`` is unbounded.
        accept: Optional validator for each row; raising stops the walk.
        capacity: Optional remaining-entry budget checked before each listing
            is materialized.

    Returns:
        Every listed row in breadth-first order.

    Raises:
        _IncompatibleListingError: A listing has an unusable shape.
        _ListingLimitError: A listing exceeds the remaining capacity.
        Exception: The first listing failure in breadth-first order.
    """
    rows: list[_ListedRow] = []
    level = [root]
    depth = 1
    while level:
        outcomes = await _run_bounded(
            [partial(_call, filesystem, "_ls", directory, detail=True) for directory in level]
        )
        next_level: list[str] = []
        # Settle in breadth-first order, so an earlier malformed listing wins
        # over a later sibling's listing failure.
        for directory, outcome in zip(level, outcomes, strict=True):
            listing = _outcome_value(outcome)
            row, children = _listed_row(
                directory,
                listing,
                None if capacity is None else capacity(),
            )
            if accept is not None:
                accept(row)
            rows.append(row)
            next_level.extend(children)
        if maxdepth is not None and depth >= maxdepth:
            break
        level = next_level
        depth += 1
    return rows
