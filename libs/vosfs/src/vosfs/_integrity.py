"""Local-file digests and server-returned MD5 verification for writes.

VOSpace uploads are one whole ``PUT``, so the only integrity evidence available
is an MD5 computed over the staged bytes and whatever digest the service
chooses to echo back. This module owns both halves of that check.
"""

from __future__ import annotations

import base64
import contextlib
import hashlib
import logging
from pathlib import Path
from typing import TYPE_CHECKING

from vosfs import errors

if TYPE_CHECKING:
    from collections.abc import AsyncIterator

    import httpx
    from fsspec.callbacks import Callback

READ_CHUNK = 1 << 20

logger = logging.getLogger(__name__)


async def file_body(
    path: str,
    callback: Callback,
    digest: hashlib._Hash | None = None,
) -> AsyncIterator[bytes]:
    """Yield a local file in bounded chunks, reporting progress via callback.

    When ``digest`` is given, every yielded chunk is also hashed, so the upload
    reads the file once instead of a separate whole-file hashing pass that
    would block the event loop for the full file before the first byte is sent.
    """
    # Local-disk reads are fast and bounded; async file I/O would add a dependency.
    with Path(path).open("rb") as handle:  # noqa: ASYNC230 - staging from local disk
        for chunk in iter(lambda: handle.read(READ_CHUNK), b""):
            if digest is not None:
                digest.update(chunk)
            yield chunk
            callback.relative_update(len(chunk))


def new_md5() -> hashlib._Hash:
    """Return an MD5 accumulator usable on FIPS hosts."""
    return hashlib.md5(usedforsecurity=False)


def verify_returned_digest(
    response: httpx.Response,
    expected: bytes | None,
    path: str,
) -> None:
    """Validate a server-returned MD5 digest against the uploaded bytes.

    A service that returns no digest is not an error: whole-object ``PUT`` has
    no negotiated integrity contract, so an absent header simply yields no
    evidence either way.
    """
    if expected is None:
        return
    header = response.headers.get("content-md5") or response.headers.get("digest")
    if header is None:
        logger.debug("no server MD5 returned for %s; nothing to compare", path)
        return
    returned = decode_digest(header)
    if returned is not None and returned != expected:
        logger.debug(
            "MD5 mismatch for %s: sent %s, server %s",
            path,
            expected.hex(),
            returned.hex(),
        )
        msg = f"MD5 mismatch after writing {path}"
        raise errors.VOSpaceError(msg, status=response.status_code)
    if returned is not None:
        logger.debug("MD5 verified for %s: %s", path, expected.hex())


def decode_digest(header: str) -> bytes | None:
    """Decode an MD5 digest header from hex or base64, or ``None`` if unusable."""
    value = header.split("=", 1)[1].strip() if header.lower().startswith("md5=") else header.strip()
    with contextlib.suppress(ValueError):
        return bytes.fromhex(value)
    with contextlib.suppress(ValueError):
        return base64.b64decode(value, validate=True)
    return None
