"""Bounded operand concurrency for ``ls`` and ``stat`` through the public seam.

Operand reads overlap, but stdout and stderr stay in operand order whatever
order the reads complete in.
"""

from __future__ import annotations

import asyncio
import time
from contextlib import AbstractAsyncContextManager, asynccontextmanager
from typing import TYPE_CHECKING, NoReturn

import pytest
from fsspec.asyn import AsyncFileSystem

from ._support import _invoke

if TYPE_CHECKING:
    from collections.abc import AsyncIterator, Callable, Iterator, Mapping

_BOUND = 16
_RICH: dict[str, object] = {
    "type": "file",
    "islink": False,
    "mode": 33188,
    "nlink": 1,
    "uid": 424242,
    "gid": 424242,
    "mtime": 1784325970.7683342,
}


class _HostileMapping(dict[str, object]):
    def get(self, key: object, default: object = None) -> NoReturn:
        del key, default
        message = "hostile"
        raise RuntimeError(message)


class _OverlapFileSystem(AsyncFileSystem):
    """Answer later operands first, recording starts, completions, and overlap."""

    cachable = False

    def __init__(self, info: Mapping[str, object], paths: tuple[str, ...]) -> None:
        super().__init__(asynchronous=True)
        self.results = info
        # Earlier operands yield more often, so reads finish in reverse order
        # whenever they actually overlap.
        self.delays = {path: len(paths) - index for index, path in enumerate(paths)}
        self.started: list[str] = []
        self.completed: list[str] = []
        self.in_flight = 0
        self.peak = 0

    async def _info(self, path: str, **kwargs: object) -> object:
        assert not kwargs
        self.started.append(path)
        self.in_flight += 1
        self.peak = max(self.peak, self.in_flight)
        try:
            for _ in range(self.delays[path]):
                await asyncio.sleep(0)
        finally:
            self.in_flight -= 1
        self.completed.append(path)
        result = self.results[path]
        if isinstance(result, Exception):
            raise result
        return result

    async def _ls(
        self,
        path: str,
        detail: bool = True,  # noqa: FBT002 - matches the fsspec hook signature.
        **kwargs: object,
    ) -> object:
        assert not detail
        assert not kwargs
        return [f"{path}/child"]


def _source(
    filesystem: _OverlapFileSystem,
) -> Callable[[], AbstractAsyncContextManager[_OverlapFileSystem]]:
    @asynccontextmanager
    async def acquire() -> AsyncIterator[_OverlapFileSystem]:
        yield filesystem

    return acquire


def _overlap(
    info: Mapping[str, object],
) -> tuple[_OverlapFileSystem, list[str]]:
    paths = tuple(info)
    return _OverlapFileSystem(info, paths), [f"memory:{path}" for path in paths]


def _stat_line(path: str) -> str:
    return f'-rw-r--r-- 1 424242 424242 1 "Jul 17 22:06:10 2026" {path}\n'


@pytest.fixture(autouse=True)
def _pin_stat_rendering(monkeypatch: pytest.MonkeyPatch) -> Iterator[None]:
    monkeypatch.setenv("TZ", "UTC")
    time.tzset()

    def missing(identifier: int) -> NoReturn:
        raise KeyError(identifier)

    monkeypatch.setattr("fss_cli._accounts.pwd.getpwuid", missing)
    monkeypatch.setattr("fss_cli._accounts.grp.getgrgid", missing)
    yield
    monkeypatch.undo()
    time.tzset()


def test_stat_overlaps_reads_but_writes_in_operand_order() -> None:
    info: dict[str, object] = {
        f"/f{index:02d}": {**_RICH, "name": f"/f{index:02d}", "size": 1} for index in range(_BOUND + 4)
    }
    info["/f03"] = FileNotFoundError("gone")
    info["/f07"] = {"type": "file"}
    filesystem, operands = _overlap(info)

    result = _invoke("stat", operands, sources={"memory": _source(filesystem)})

    expected = "".join(_stat_line(path) for path in info if path not in {"/f03", "/f07"})
    assert (result.exit_code, result.stdout) == (1, expected)
    assert result.stderr == ("stat: memory:/f03: not found\nstat: memory:/f07: incompatible result\n")
    assert filesystem.started == list(info)
    assert filesystem.completed[0] == "/f15"
    assert filesystem.peak == _BOUND


def test_stat_emits_earlier_lines_before_a_later_read_raises() -> None:
    info: dict[str, object] = {
        "/a": {**_RICH, "name": "/a", "size": 1},
        "/b": _HostileMapping(_RICH),
        "/c": {**_RICH, "name": "/c", "size": 1},
    }
    filesystem, operands = _overlap(info)

    result = _invoke("stat", operands, sources={"memory": _source(filesystem)})

    assert type(result.exception) is RuntimeError
    assert (result.stdout, result.stderr) == (_stat_line("/a"), "")


def test_ls_overlaps_reads_but_renders_blocks_and_diagnostics_in_order() -> None:
    info: dict[str, object] = {
        "/z-dir": {"type": "directory"},
        "/missing": FileNotFoundError(),
        "/b-file": {"type": "file"},
        "/denied": PermissionError(),
        "/a-dir": {"type": "directory"},
    }
    filesystem, operands = _overlap(info)

    result = _invoke("ls", operands, sources={"memory": _source(filesystem)})

    assert result.exit_code == 1
    assert result.stdout == ("memory:/b-file\n\nmemory:/a-dir:\nchild\n\nmemory:/z-dir:\nchild\n")
    assert result.stderr == ("ls: memory:/missing: not found\nls: memory:/denied: permission denied\n")
    assert filesystem.started == list(info)
    assert filesystem.completed == list(reversed(info))
