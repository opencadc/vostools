"""Bounded, order-preserving concurrency shared by remote-heavy commands."""

from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
from functools import partial
from typing import TYPE_CHECKING

import pytest
from fsspec.asyn import AsyncFileSystem
from typer.testing import CliRunner

from fss_cli import App
from fss_cli._concurrent import _CONCURRENCY, _gather_bounded, _run_bounded

if TYPE_CHECKING:
    from collections.abc import AsyncIterator


class _Control(BaseException):
    pass


class _Probe:
    def __init__(self) -> None:
        self.active = 0
        self.peak = 0
        self.started: list[int] = []
        self.finished: list[int] = []

    async def run(self, index: int, delay: float = 0.0) -> int:
        self.started.append(index)
        self.active += 1
        self.peak = max(self.peak, self.active)
        try:
            await asyncio.sleep(delay)
        finally:
            self.active -= 1
            self.finished.append(index)
        return index


def test_values_keep_submission_order_under_the_bound() -> None:
    probe = _Probe()
    count = _CONCURRENCY + 4

    values = asyncio.run(_gather_bounded([partial(probe.run, index, (count - index) / 1000) for index in range(count)]))

    assert values == list(range(count))
    assert probe.peak == _CONCURRENCY
    assert probe.started == list(range(count))


def test_first_failure_in_submission_order_wins_and_stops_new_starts() -> None:
    probe = _Probe()

    async def fail(index: int, delay: float) -> int:
        await probe.run(index, delay)
        message = f"failure {index}"
        raise OSError(message)

    operations = [
        partial(probe.run, 0, 0.01),
        partial(fail, 1, 0.02),
        partial(fail, 2, 0.0),
        *(partial(probe.run, index) for index in range(3, 40)),
    ]

    with pytest.raises(OSError, match="failure 1"):
        asyncio.run(_gather_bounded(operations))
    # Every started operation finished; nothing started after the stop.
    assert sorted(probe.finished) == sorted(probe.started)
    assert len(probe.started) <= _CONCURRENCY + 1


def test_stop_predicate_records_outcomes_and_unstarted_operations() -> None:
    probe = _Probe()

    outcomes = asyncio.run(
        _run_bounded(
            [partial(probe.run, index) for index in range(5)],
            stop=lambda value: value == 1,
            limit=1,
        )
    )

    assert [None if outcome is None else outcome.value for outcome in outcomes] == [
        0,
        1,
        None,
        None,
        None,
    ]


def test_control_flow_cancels_and_drains_siblings_before_propagating() -> None:
    drained: list[str] = []

    async def slow() -> None:
        try:
            await asyncio.sleep(1)
        finally:
            drained.append("slow")

    async def control() -> None:
        await asyncio.sleep(0)
        raise _Control

    with pytest.raises(_Control):
        asyncio.run(_run_bounded([slow, control]))
    assert drained == ["slow"]


def test_caller_cancellation_drains_started_operations() -> None:
    finished: list[int] = []

    async def operation(index: int) -> None:
        try:
            await asyncio.sleep(1)
        finally:
            finished.append(index)

    async def main() -> None:
        task = asyncio.create_task(_run_bounded([partial(operation, index) for index in range(3)]))
        await asyncio.sleep(0.01)
        task.cancel()
        with pytest.raises(asyncio.CancelledError):
            await task

    asyncio.run(main())
    assert sorted(finished) == [0, 1, 2]


class _RemovalFileSystem(AsyncFileSystem):
    """A flat directory whose removals overlap only if run concurrently."""

    cachable = False

    def __init__(self, names: list[str]) -> None:
        super().__init__(asynchronous=True)
        self.present = {"/docs", *(f"/docs/{name}" for name in names)}
        self.active = 0
        self.peak = 0
        self.order: list[tuple[str, str]] = []

    async def _info(self, path: str, **kwargs: object) -> dict[str, object]:
        del kwargs
        if path not in self.present:
            raise FileNotFoundError(path)
        kind = "directory" if path == "/docs" else "file"
        return {"name": path, "type": kind, "size": 0}

    async def _ls(self, path: str, **kwargs: object) -> list[dict[str, object]]:
        del kwargs
        return [
            {"name": child, "type": "file", "size": 0} for child in sorted(self.present) if child.startswith(f"{path}/")
        ]

    async def _rm_file(self, path: str, **kwargs: object) -> None:
        del kwargs
        self.active += 1
        self.peak = max(self.peak, self.active)
        await asyncio.sleep(0.001)
        self.active -= 1
        self.order.append(("rm_file", path))
        self.present.discard(path)

    async def _rmdir(self, path: str) -> None:
        assert self.present == {path}, "directory removed before its children"
        self.order.append(("rmdir", path))
        self.present.discard(path)


def test_recursive_rm_removes_siblings_concurrently_and_directories_last() -> None:
    filesystem = _RemovalFileSystem([f"f{index:02}" for index in range(20)])

    @asynccontextmanager
    async def source() -> AsyncIterator[_RemovalFileSystem]:
        yield filesystem

    result = CliRunner().invoke(
        App(
            {"memory": source},
            capabilities={"recursion": {"remove": True}},
        ).typer_app,
        ["rm", "-R", "memory:/docs"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert 1 < filesystem.peak <= _CONCURRENCY
    assert filesystem.order[-1] == ("rmdir", "/docs")
    assert filesystem.present == set()


@pytest.mark.parametrize("name", ["/docs/.", "/docs/.."])
def test_walk_rejects_dot_segment_listing_entries(name: str) -> None:
    from fss_cli._walk import _IncompatibleListingError, _walk

    class _DotFileSystem(AsyncFileSystem):
        cachable = False

        async def _ls(self, path: str, **kwargs: object) -> list[dict[str, object]]:
            del path, kwargs
            return [{"name": name, "type": "directory"}]

    with pytest.raises(_IncompatibleListingError):
        asyncio.run(_walk(_DotFileSystem(asynchronous=True), "/docs"))
