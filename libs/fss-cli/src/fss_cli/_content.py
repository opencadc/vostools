"""Bounded content comparison for resumable recursive copies."""

from __future__ import annotations

import asyncio
import base64
import binascii
import hashlib
import re
import tempfile
from contextlib import suppress
from pathlib import Path
from typing import TYPE_CHECKING

from ._command import _call, _drain_current_operation

if TYPE_CHECKING:
    from fsspec.asyn import AsyncFileSystem

_HASH_CHUNK = 1024 * 1024
_MD5_BYTES = 16


def _md5(tokens: tuple[tuple[str, object], ...]) -> bytes | None:
    values = dict(tokens)
    value = values.get("md5")
    if isinstance(value, str) and re.fullmatch(r"[0-9a-fA-F]{32}", value):
        return bytes.fromhex(value)
    value = values.get("content-md5")
    if isinstance(value, (str, bytes)):
        try:
            digest = base64.b64decode(value, validate=True)
        except (ValueError, binascii.Error):
            return None
        if len(digest) == _MD5_BYTES:
            return digest
    return None


def matching_checksum(source: tuple[tuple[str, object], ...], destination: tuple[tuple[str, object], ...]) -> bool:
    """Accept compatible explicit MD5 values, never opaque ETags or sizes."""
    digest = _md5(source)
    return digest is not None and digest == _md5(destination)


def describe_checksums(source: tuple[tuple[str, object], ...], destination: tuple[tuple[str, object], ...]) -> str:
    """Describe the content-identity decision for a debug record, without values."""
    source_digest = _md5(source)
    destination_digest = _md5(destination)
    if source_digest is None or destination_digest is None:
        return "no compatible MD5 checksum on both sides; comparing SHA-256"
    if source_digest == destination_digest:
        return "MD5 checksums equal"
    return "MD5 checksums differ; comparing SHA-256"


def _digest(path: str) -> bytes:
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        while chunk := stream.read(_HASH_CHUNK):
            digest.update(chunk)
    return digest.digest()


async def same_contents(filesystem: AsyncFileSystem, destination: str, staged_source: str) -> bool:
    """Compare staged bytes without keeping object contents in memory."""
    directory = tempfile.TemporaryDirectory(prefix="fss-cli-compare-")
    try:
        local = str(Path(directory.name) / "destination")
        await _call(filesystem, "_get_file", destination, local)
        identical = await _drain_current_operation(asyncio.to_thread(lambda: _digest(staged_source) == _digest(local)))
    except BaseException:
        with suppress(BaseException):
            directory.cleanup()
        raise
    directory.cleanup()
    return identical
