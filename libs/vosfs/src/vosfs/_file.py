"""Opt-in ranged reads for ``open``.

The default ``open("rb")`` still stages the whole object. Passing ``block_size``
or ``cache_type`` selects :class:`VOSFile`, which negotiates one read endpoint
and issues ranged GETs against it. A ``200`` means the service ignored
``Range``; the body already in that response is staged and later reads stay
local. ``401`` and ``403`` negotiate a fresh endpoint once.
"""

from __future__ import annotations

from typing import TYPE_CHECKING

from fsspec.asyn import sync
from fsspec.spec import AbstractBufferedFile

from vosfs import _transfer, negotiate, staging

if TYPE_CHECKING:
    from vosfs.filesystem import VOSpaceFileSystem
    from vosfs.negotiate import NegotiatedEndpoint


class VOSFile(AbstractBufferedFile):
    """A buffered read that reuses one negotiated byte endpoint."""

    def __init__(  # noqa: PLR0913 - fsspec buffered-file signature
        self,
        fs: VOSpaceFileSystem,
        path: str,
        mode: str = "rb",
        block_size: int | str = "default",
        cache_type: str = "readahead",
        cache_options: dict[str, object] | None = None,
        **kwargs: object,
    ) -> None:
        """Negotiate the read endpoint, then let fsspec buffer ``block_size`` chunks."""
        self._vos_path = fs._strip_protocol(path)  # noqa: SLF001 - same filesystem implementation
        self._endpoint: NegotiatedEndpoint | None = None
        self._fallback: staging.StagedReadFile | None = None
        self._size = 0
        sync(fs.loop, self._prepare, fs)
        super().__init__(
            fs,
            path,
            mode=mode,
            block_size=block_size,
            cache_type=cache_type,
            cache_options=cache_options,
            size=self._size,
            **kwargs,
        )

    async def _prepare(self, fs: VOSpaceFileSystem) -> None:
        """Reject an external link and negotiate the endpoint once."""
        await _transfer.preflight_read_target(fs, self._vos_path)
        info = await fs._info(self._vos_path)  # noqa: SLF001 - same filesystem implementation
        size = _transfer.byte_size(info)
        if size is None:
            response = await _transfer.open_read_stream(fs, self._vos_path, target_validated=True)
            self._fallback = await _transfer.stage_response(response)
            self._size = self._fallback.size
            return
        self._size = size
        self._endpoint = await self._negotiate(fs)

    async def _negotiate(self, fs: VOSpaceFileSystem) -> NegotiatedEndpoint:
        return await _transfer.negotiate_endpoint(
            fs,
            self._vos_path,
            direction=negotiate.DIRECTION_PULL,
            protocol_uri=negotiate.PROTOCOL_HTTPS_GET,
        )

    def _fetch_range(self, start: int, end: int) -> bytes:
        """Return ``start:end`` from the endpoint, or from the staged body."""
        if self._fallback is None:
            data = sync(self.fs.loop, self._read_range, start, end)
            if self._fallback is None:
                return data
        self._fallback.seek(start)
        return self._fallback.read(max(0, end - start))

    async def _read_range(self, start: int, end: int) -> bytes:
        """GET one range, staging the whole body when the server returns 200."""
        fs: VOSpaceFileSystem = self.fs
        if end <= start:
            return b""
        if self._endpoint is None:
            self._endpoint = await self._negotiate(fs)
        header = f"bytes={start}-{end - 1}"
        try:
            result = await _transfer.read_range(fs, self._endpoint, self._vos_path, header)
        except PermissionError:
            self._endpoint = await self._negotiate(fs)
            result = await _transfer.read_range(fs, self._endpoint, self._vos_path, header)
        if isinstance(result, bytes):
            return result
        self._fallback = await _transfer.stage_response(result)
        self._endpoint = None
        return b""

    def close(self) -> None:
        """Close the buffered file and any staged fallback."""
        fallback = self._fallback
        self._fallback = None
        if fallback is not None:
            fallback.close()
        super().close()
