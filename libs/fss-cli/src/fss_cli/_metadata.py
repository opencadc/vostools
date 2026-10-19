"""Snapshot backend mappings through their authoritative iteration interface."""

import math
from collections.abc import Mapping
from datetime import datetime, timezone
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


def _supported_time(normalized: float) -> float | None:
    if not math.isfinite(normalized):
        return None
    try:
        datetime.fromtimestamp(normalized, tz=timezone.utc)
    except (OverflowError, OSError, ValueError):
        return None
    return normalized


def _datetime_time(value: datetime) -> float | None:
    if value.tzinfo is None or value.utcoffset() is None:
        value = value.replace(tzinfo=timezone.utc)
    try:
        return _supported_time(value.timestamp())
    except (OverflowError, OSError, ValueError):
        return None


def normalize_time(value: object) -> float | None:
    if type(value) is int or type(value) is float:
        try:
            normalized = float(value)
        except OverflowError:
            return None
        return _supported_time(normalized)
    if isinstance(value, datetime):
        return _datetime_time(value)
    if not isinstance(value, str):
        return None

    spelling = value[:-1] + "+00:00" if value.endswith(("Z", "z")) else value
    try:
        parsed = datetime.fromisoformat(spelling)
    except ValueError:
        return None
    return _datetime_time(parsed)
