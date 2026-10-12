"""Subprocess helper proving hash-seed-independent ``info`` rendering."""

from __future__ import annotations

import sys
from contextlib import asynccontextmanager
from types import MappingProxyType

from fsspec.asyn import AsyncFileSystem
from typer.testing import CliRunner

from fss_cli import App


class _InfoFileSystem(AsyncFileSystem):
    cachable = False

    def __init__(self) -> None:
        super().__init__(asynchronous=True)

    async def _info(self, path: str, **kwargs: object) -> object:
        assert path == "/x"
        assert kwargs == {}
        return {
            "name": path,
            "type": "file",
            "properties": MappingProxyType({"z": (2, 1), "a": {"charlie", "alpha", "bravo"}}),
            "keyed": MappingProxyType(
                {
                    frozenset({"bravo", "alpha"}): "frozenset key",
                    ("tuple", frozenset({"delta", "charlie"})): "tuple key",
                }
            ),
        }


@asynccontextmanager
async def _source():
    yield _InfoFileSystem()


result = CliRunner().invoke(App({"memory": _source}).typer_app, ["info", "memory:/x"])
sys.stdout.write(result.stdout)
sys.stderr.write(result.stderr)
raise SystemExit(result.exit_code)
