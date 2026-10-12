"""Subprocess fixture for public-seam ``ls`` output tests."""

from __future__ import annotations

import io
import sys
from contextlib import asynccontextmanager
from functools import partial
from typing import TYPE_CHECKING, TextIO

from fsspec.asyn import AsyncFileSystem

from fss_cli import App

if TYPE_CHECKING:
    from collections.abc import AsyncIterator


def _real_stdout() -> TextIO:
    stream = sys.__stdout__
    if stream is None:
        msg = "the subprocess requires a real stdout stream"
        raise RuntimeError(msg)
    return stream


class _FileSystem(AsyncFileSystem):
    def __init__(self, mode: str) -> None:
        super().__init__(asynchronous=True)
        self._mode = mode

    async def _info(self, path: str, **kwargs: object) -> dict[str, str]:
        del kwargs
        if path == "/missing":
            raise FileNotFoundError(path)
        return {"type": "directory"}

    async def _ls(
        self,
        path: str,
        detail: bool = True,  # noqa: FBT002 - matches the fsspec hook signature.
        **kwargs: object,
    ) -> list[str]:
        del path, detail, kwargs
        if self._mode == "tty":
            return ["/docs/\x1b[31mred\x1b[0m"]
        return ["/docs/z.txt", "/docs/a.txt"]


@asynccontextmanager
async def _source(mode: str) -> AsyncIterator[_FileSystem]:
    yield _FileSystem(mode)


class _PrefixThenFailure(io.TextIOBase):
    def __init__(self, accepted_characters: int) -> None:
        self._remaining = accepted_characters

    @property
    def encoding(self) -> str:
        return _real_stdout().encoding or "utf-8"

    @property
    def errors(self) -> str:
        return _real_stdout().errors or "strict"

    def writable(self) -> bool:
        return True

    def isatty(self) -> bool:
        return False

    def write(self, value: str) -> int:
        if not isinstance(value, str):
            raise TypeError
        accepted = min(self._remaining, len(value))
        if accepted:
            stream = _real_stdout()
            stream.write(value[:accepted])
            stream.flush()
            self._remaining -= accepted
        if accepted != len(value):
            message = "disk\\bad\nline"
            raise OSError(message)
        return len(value)

    def flush(self) -> None:
        _real_stdout().flush()


def _configure_stdout(mode: str) -> None:
    if mode == "fail":
        sys.stdout = _PrefixThenFailure(0)
    elif mode == "prefix":
        sys.stdout = _PrefixThenFailure(len("a.txt\n"))
    elif mode == "runtime-and-fail":
        sys.stdout = _PrefixThenFailure(0)
    elif mode not in {"normal", "tty"}:
        msg = f"unknown child mode: {mode}"
        raise RuntimeError(msg)


def main() -> None:
    mode = sys.argv.pop(1)
    _configure_stdout(mode)
    App({"memory": partial(_source, mode)}).typer_app()


if __name__ == "__main__":
    main()
