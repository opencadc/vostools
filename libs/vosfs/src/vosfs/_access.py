"""Translate OpenCADC access properties without claiming POSIX ownership."""

from __future__ import annotations

import re
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from collections.abc import Mapping

_CORE = "ivo://ivoa.net/vospace/core#"
_LOCKED = "ivo://cadc.nrc.ca/vospace/core#islocked"
_GROUP_PREFIXES = ("ivo://cadc.nrc.ca/gms?", "ivo://cadc.nrc.ca/gms#")


def _groups(value: str) -> tuple[str, ...]:
    return tuple(
        next(
            (group[len(prefix) :] for prefix in _GROUP_PREFIXES if group.startswith(prefix)),
            group,
        )
        for group in value.split()
        if group != "NONE"
    )


def _bit(known: bool | None, character: str) -> str:  # noqa: FBT001 - tri-state metadata, not an option.
    return "?" if known is None else character if known else "-"


def access_info(properties: Mapping[str, str], node_type: str) -> dict[str, object]:
    """Preserve distinct ACL groups and an explicitly advisory access summary."""
    result: dict[str, object] = {}
    creator = properties.get(_CORE + "creator")
    if creator:
        common_name = re.match(r"(?i)^cn=([^,\\]+)(?:,|$)", creator)
        result["owner"] = common_name.group(1).strip() if common_name else creator
    for property_name, field in (
        ("groupread", "read_groups"),
        ("groupwrite", "write_groups"),
    ):
        if _CORE + property_name in properties:
            result[field] = _groups(properties[_CORE + property_name])
    public = {"true": True, "false": False}.get(properties.get(_CORE + "ispublic", ""))
    locked = {"true": True, "false": False}.get(properties.get(_LOCKED, ""))
    if public is not None:
        result["public"] = public
    if locked is not None:
        result["locked"] = locked
    if result:
        owner = True if creator else None
        read = bool(result["read_groups"]) if "read_groups" in result else None
        write = bool(result["write_groups"]) if "write_groups" in result else None
        kind = {"data": "-", "container": "d", "link": "l"}[node_type]
        result["permissions"] = (
            kind
            + _bit(owner, "r")
            + _bit(False if locked else owner, "w")
            + "-"
            + _bit(read, "r")
            + _bit(False if locked else write, "w")
            + "-"
            + _bit(public, "r")
            + "--"
        )
    return result
