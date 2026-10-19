"""Disk-backed staging files for whole-object reads and writes.

Seekable ``open`` reads still stage one whole-object download into a temporary
file. Partial ``cat_file`` / ``cat_ranges`` reads may use HTTP ``Range`` instead
(see :mod:`vosfs._transfer`). A staged write buffers into a temporary file that
is uploaded once on a successful close.

Both views subclass the standard buffered IO wrappers so they inherit the full
file-object protocol (``read``/``readinto``/``readline``/iteration/``seek``/
``tell`` and, for writes, ``write``) and only add temporary-file cleanup and the
upload-on-clean-close commit behaviour.
"""

from __future__ import annotations

import contextlib
import io
import os
import tempfile
from pathlib import Path
from typing import TYPE_CHECKING

from fsspec.compression import compr
from fsspec.core import get_compression

if TYPE_CHECKING:
    from collections.abc import Awaitable, Callable, Sequence
    from types import TracebackType
    from typing import Any


def new_temp_path() -> str:
    """Create an empty disk-backed temporary file and return its path."""
    handle, path = tempfile.mkstemp(prefix="vosfs-")
    os.close(handle)
    return path


def unlink_temp_path(path: str) -> None:
    """Best-effort unlink one operation-owned temporary file."""
    with contextlib.suppress(OSError):
        Path(path).unlink()


async def read_ranges(
    download: Callable[[str], Awaitable[None]],
    ranges: Sequence[tuple[int, int | None, int | None]],
) -> list[tuple[int, bytes]]:
    """Download one object once and return its indexed local byte slices."""
    path = new_temp_path()
    try:
        await download(path)
        with Path(path).open("rb") as local:  # noqa: ASYNC230 - disk-backed staging
            size = os.fstat(local.fileno()).st_size
            values: list[tuple[int, bytes]] = []
            for index, start, end in ranges:
                first, stop, _step = slice(start, end).indices(size)
                local.seek(first)
                values.append((index, local.read(max(0, stop - first))))
            return values
    finally:
        unlink_temp_path(path)


class StagedReadFile(io.BufferedReader):
    """A seekable read-only view over a downloaded temporary file.

    Inherits the buffered-reader protocol; the temporary file is removed when
    the view is closed. All read and seek operations are local, so no network
    I/O happens after construction.
    """

    def __init__(self, path: str) -> None:
        """Open ``path`` for binary reading; it is unlinked on close."""
        self._path = path
        raw = io.FileIO(path, "rb")
        try:
            super().__init__(raw)
            self.size = os.fstat(self.fileno()).st_size
        except BaseException:
            raw.close()
            unlink_temp_path(path)
            raise

    def close(self) -> None:
        """Close the staged file and remove its temporary backing file."""
        try:
            super().close()
        finally:
            unlink_temp_path(self._path)


class StagedWriteFile(io.BufferedRandom):
    """A writable buffer that uploads once, only on a successful close.

    Writes accumulate in a disk-backed temporary file. On a clean ``close`` (or
    a clean ``with`` exit) the ``on_commit`` callback uploads it; if the context
    block raises, or the object is merely discarded or garbage-collected, the
    buffer is dropped without any upload, so a failed write never issues a PUT.
    ``r+b`` backing lets consumers that seek back and read (zip and archive
    writers) work while still uploading exactly once.
    """

    def __init__(self, on_commit: Callable[[str], None]) -> None:
        """Open a read/write temporary buffer; ``on_commit`` receives its path."""
        self._path = new_temp_path()
        super().__init__(io.FileIO(self._path, "r+"))
        self._on_commit = on_commit
        self._done = False
        self._outer_owns_finish = False

    def close(self) -> None:
        """Close locally, committing only when no outer wrapper owns the finish.

        The temporary file is removed even if the final flush (``super().close``)
        or the upload raises, so a failed commit never leaks a staging file. A
        flush failure skips the upload, since the buffer is then incomplete. An
        outer text wrapper separately selects commit or discard after it closes.
        """
        if self._done or self.closed:
            return
        if self._outer_owns_finish:
            super().close()
        else:
            self._finish(upload=True)

    def _finish(self, *, upload: bool) -> None:
        """Close, optionally upload, and unlink through one terminal path."""
        if self._done:
            return
        self._done = True
        try:
            if not self.closed:
                super().close()
            if upload:
                self._on_commit(self._path)
        finally:
            unlink_temp_path(self._path)

    def _handoff_to_outer(self) -> None:
        """Give an outer wrapper ownership of the terminal upload decision."""
        self._outer_owns_finish = True

    def _commit(self) -> None:
        """Upload and clean up after an outer wrapper closes successfully."""
        self._finish(upload=True)

    def discard(self) -> None:
        """Discard the buffer without uploading (used on a failed write)."""
        self._finish(upload=False)

    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc_val: BaseException | None,
        exc_tb: TracebackType | None,
    ) -> None:
        """Commit on a clean exit; discard when the block raised."""
        if exc_type is None:
            self.close()
        else:
            self.discard()

    def __del__(self) -> None:
        """Drop an unfinished buffer without uploading on garbage collection."""
        if not self._done:
            with contextlib.suppress(Exception):
                self.discard()


class _CommitOnClose(io.IOBase):
    """Own commit/discard after the complete outer IO stack closes."""

    def _take_ownership(self, staged: StagedWriteFile) -> None:
        self._staged = staged
        self._discard_on_close = False
        staged._handoff_to_outer()  # noqa: SLF001 - same-module lifecycle peer

    def __del__(self) -> None:
        """Discard abandoned wrappers instead of committing during collection."""
        self._discard_on_close = True
        with contextlib.suppress(Exception):
            self.close()

    def close(self) -> None:
        """Commit only after the complete outer stack closes cleanly."""
        try:
            super().close()
        except BaseException:
            self._staged.discard()
            raise
        if self._discard_on_close:
            self._staged.discard()
        else:
            self._staged._commit()  # noqa: SLF001 - same-module lifecycle peer

    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc_val: BaseException | None,
        exc_tb: TracebackType | None,
    ) -> None:
        """Close normally, but discard when the context block raised."""
        if exc_type is not None:
            self._discard_on_close = True
        super().__exit__(exc_type, exc_val, exc_tb)


class StagedTextWriteFile(_CommitOnClose, io.TextIOWrapper):
    """Text wrapper that owns its staged buffer's terminal upload decision."""

    def __init__(
        self,
        buffer: Any,  # noqa: ANN401 - fsspec compression wrappers are file-like
        staged: StagedWriteFile,
        *,
        encoding: str | None = None,
        errors: str | None = None,
        newline: str | None = None,
    ) -> None:
        """Wrap ``buffer`` and take ownership of staged commit or discard."""
        self._take_ownership(staged)
        super().__init__(buffer, encoding=encoding, errors=errors, newline=newline)


class StagedBinaryWriteFile(_CommitOnClose, io.BufferedWriter):
    """Binary compression wrapper with explicit staging ownership."""

    def __init__(self, buffer: io.BufferedIOBase, staged: StagedWriteFile) -> None:
        """Own the complete binary compression stack."""
        self._take_ownership(staged)
        super().__init__(buffer)


def wrap_write(  # noqa: PLR0913 - explicit compression and TextIOWrapper settings.
    staged: StagedWriteFile,
    path: str,
    mode: str,
    compression: str | None,
    *,
    encoding: str | None = None,
    errors: str | None = None,
    newline: str | None = None,
) -> io.IOBase:
    """Build the complete write stack, discarding if assembly fails."""
    try:
        buffer: io.BufferedIOBase = staged
        resolved = get_compression(path, compression)
        if resolved is not None:
            buffer = compr[resolved](buffer, mode=mode[0])
        if "b" in mode:
            return StagedBinaryWriteFile(buffer, staged)
        return StagedTextWriteFile(buffer, staged, encoding=encoding, errors=errors, newline=newline)
    except BaseException:
        with contextlib.suppress(BaseException):
            staged.discard()
        raise
