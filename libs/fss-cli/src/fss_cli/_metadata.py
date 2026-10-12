"""Snapshot backend mappings through their authoritative iteration interface."""

from collections.abc import Mapping
from typing import TypeVar

_Key = TypeVar("_Key")
_Value = TypeVar("_Value")


def valid_display_text(value: str) -> bool:
    """Reject separators that can split or overwrite output records."""
    return not any(character in value for character in ("\0", "\r", "\n"))


def snapshot_mapping(value: Mapping[_Key, _Value]) -> dict[_Key, _Value]:
    """Freeze indexed values, rejecting incomplete or changing enumeration."""
    expected = len(value)
    result: dict[_Key, _Value] = {}
    count = 0
    for key in value:
        if count >= expected:
            message = "mapping enumerates more entries than its length"
            raise ValueError(message)
        result[key] = value[key]
        count += 1
    if count != expected or len(result) != expected or len(value) != expected:
        message = "mapping enumeration does not match its length"
        raise ValueError(message)
    return result
