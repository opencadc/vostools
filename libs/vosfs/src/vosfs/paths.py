"""VOSpace filesystem path identity.

This module implements the path normalization and validation rules. The
service is selected only by ``endpoint_url``; the apparent authority after the
``vos://`` protocol marker is part of the filesystem path, not a service or
VOSpace authority.

User paths are literal. ``strip_protocol`` does not percent-decode them, so
fsspec may apply it more than once without changing the name. Bytes on the
wire are still encoded by :func:`encode_url_path` and decoded once, by
:func:`decode_url_path`, when a node URI is read back from the service.
"""

from __future__ import annotations

from urllib.parse import quote, unquote

_SCHEME_PREFIX = "vos:"
# Percent-encodings that would smuggle a path separator into a single segment.
_ENCODED_SEPARATORS = ("%2f", "%5c")


def strip_protocol(path: str) -> str:
    """Return the internal path for a user-supplied ``path``.

    The inputs ``vos://a/b``, ``vos:///a/b``, ``/a/b``, and ``a/b`` all
    normalize to ``/a/b``. ``vos://``, ``vos:///``, ``/``, and the empty path
    represent root and normalize to ``/``. Percent-escapes are kept as typed.
    ``?`` and ``#`` are allowed in a name.

    Args:
        path: A user-supplied VOSpace path in any accepted form.

    Returns:
        The normalized path: a leading slash and the literal segments, or
        ``/`` for root.

    Raises:
        ValueError: If the path contains userinfo, a NUL byte, an encoded path
            separator, or a ``.`` or ``..`` segment.
    """
    if "\x00" in path:
        msg = "path must not contain a NUL byte"
        raise ValueError(msg)

    remainder = _strip_scheme(path)
    remainder = _absorb_authority(remainder)
    # Collapse any run of leading slashes into a single root slash.
    remainder = "/" + remainder.lstrip("/")

    segments = [segment for segment in remainder.split("/") if segment]
    for segment in segments:
        _reject_segment(segment)
    if not segments:
        return "/"
    return "/" + "/".join(segments)


def decode_url_path(path: str) -> str:
    """Decode one service URI path into an internal path, exactly once.

    ``path`` is the path component of a node ``uri`` (already split from the
    scheme and authority). Encoded separators are rejected before decoding so
    a segment cannot become two.
    """
    raw_segments = [segment for segment in path.split("/") if segment]
    for segment in raw_segments:
        _reject_segment(segment)
    decoded = [_decode_segment(segment) for segment in raw_segments]
    if not decoded:
        return "/"
    return "/" + "/".join(decoded)


def _strip_scheme(path: str) -> str:
    """Remove a leading ``vos:`` scheme marker, case-insensitively."""
    if path[: len(_SCHEME_PREFIX)].lower() == _SCHEME_PREFIX:
        return path[len(_SCHEME_PREFIX) :]
    return path


def _absorb_authority(remainder: str) -> str:
    """Fold a ``//authority`` marker into the path, rejecting userinfo.

    The authority position after ``//`` is treated as leading path content, so
    ``//a/b`` becomes ``/a/b``. A ``user@host`` style authority carries
    userinfo, which is rejected.
    """
    if not remainder.startswith("//"):
        return remainder
    authority = remainder[2:].split("/", 1)[0]
    if "@" in authority:
        msg = "path must not contain userinfo"
        raise ValueError(msg)
    return "/" + remainder[2:]


def _reject_segment(segment: str) -> None:
    """Reject a NUL, a relative segment, or an encoded separator."""
    lowered = segment.lower()
    for encoded in _ENCODED_SEPARATORS:
        if encoded in lowered:
            msg = "path must not contain an encoded path separator"
            raise ValueError(msg)
    if segment in ("..", "."):
        msg = "path must not contain a relative segment"
        raise ValueError(msg)
    if "\x00" in segment:
        msg = "path must not contain a NUL byte"
        raise ValueError(msg)


def _decode_segment(segment: str) -> str:
    """Percent-decode one wire segment and reject what decoding reveals."""
    decoded = unquote(segment, encoding="utf-8", errors="strict")
    if decoded in ("..", "."):
        msg = "path must not contain a relative segment"
        raise ValueError(msg)
    if "\x00" in decoded or "/" in decoded or "\\" in decoded:
        msg = "path must not contain a NUL byte"
        raise ValueError(msg)
    return decoded


def parent(path: str) -> str:
    """Return the parent of an already-normalized path, or ``/`` for root."""
    if path == "/":
        return "/"
    head = path.rsplit("/", 1)[0]
    return head or "/"


def segments(path: str) -> list[str]:
    """Return the segments of an already-normalized path (no further decoding)."""
    return [segment for segment in path.split("/") if segment]


def encode_url_path(path: str) -> str:
    """Return the normalized path with each segment percent-encoded for a URL.

    Operates on the internal path and encodes exactly once, so a literal
    percent in a name is sent as ``%25``. Root becomes an empty string so it
    can be appended to a base URL.
    """
    return "".join(f"/{quote(segment, safe='')}" for segment in segments(path))
