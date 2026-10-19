"""``cp`` Rich progress bars and the post-copy metadata wait, over scripted fake filesystems."""

from __future__ import annotations

import asyncio
import io
import sys
import time
from contextlib import asynccontextmanager
from pathlib import Path
from types import MappingProxyType, MethodType, SimpleNamespace
from typing import TYPE_CHECKING, Any, NoReturn

import pytest
import typer
import typer.main
from fsspec.asyn import AsyncFileSystem
from fsspec.callbacks import DEFAULT_CALLBACK
from rich import progress as rich_progress
from rich.text import Text

from fss_cli import App
from fss_cli._progress import _draws_progress, _StderrProxy, _TaskCallback

from ._ansi import strip_ansi
from ._support import _cp_verified, _invoke, _outcomes_by_file, _source_must_not_run
from .test_recursive_cp import _source as _tree_source
from .test_recursive_cp import _TreeFileSystem

if TYPE_CHECKING:
    from collections.abc import AsyncIterator, Awaitable, Callable

    from fsspec.callbacks import Callback


class _Clock:
    """A monotonic clock that only moves when ``cp`` sleeps."""

    def __init__(self) -> None:
        self.now = 0.0
        self.sleeps: list[float] = []

    def monotonic(self) -> float:
        return self.now

    async def sleep(self, seconds: float) -> None:
        self.sleeps.append(seconds)
        self.now += seconds


@pytest.fixture
def clock(monkeypatch: pytest.MonkeyPatch) -> _Clock:
    fake = _Clock()
    monkeypatch.setattr("fss_cli._post_copy.time", SimpleNamespace(monotonic=fake.monotonic))
    monkeypatch.setattr("fss_cli._post_copy.asyncio", SimpleNamespace(sleep=fake.sleep))
    return fake


class _Bars:
    """Task calls on a non-drawing ``Progress``, interleaved with ``cp``'s stderr lines."""

    def __init__(self) -> None:
        self.events: list[tuple[object, ...]] = []
        self.columns: tuple[object, ...] = ()
        self.options: dict[str, Any] = {}
        self.created = 0
        self.stderr: object = None
        self.stderr_during_copy: object = None
        self.visible_peak: list[str] = []
        self.progress: rich_progress.Progress

    def live_descriptions(self) -> list[str]:
        return [str(task.description) for task in self.progress.tasks if task.visible]


@pytest.fixture
def bars(monkeypatch: pytest.MonkeyPatch) -> _Bars:
    recorded = _Bars()
    original_progress = rich_progress.Progress
    original_echo = typer.echo

    class RecordingProgress(original_progress):
        def __init__(self, *columns: Any, **options: Any) -> None:
            recorded.columns, recorded.options = columns, options
            recorded.created += 1
            recorded.stderr = sys.stderr
            recorded.progress = self
            super().__init__(*columns, **{**options, "disable": True})

        def add_task(self, description: str, *args: Any, **kwargs: Any) -> rich_progress.TaskID:
            recorded.stderr_during_copy = sys.stderr
            task = super().add_task(description, *args, **kwargs)
            recorded.events.append(("add", task, description, kwargs.get("visible")))
            return task

        def reset(self, task_id: rich_progress.TaskID, **kwargs: Any) -> None:
            super().reset(task_id, **kwargs)
            recorded.events.append(("reset", task_id, kwargs.get("description"), kwargs.get("visible")))

        def update(self, task_id: rich_progress.TaskID, **kwargs: Any) -> None:
            super().update(task_id, **kwargs)
            recorded.events.append(("update", task_id, kwargs.get("completed"), kwargs.get("total")))
            recorded.visible_peak = max(recorded.visible_peak, recorded.live_descriptions(), key=len)

        def remove_task(self, task_id: rich_progress.TaskID) -> None:
            super().remove_task(task_id)
            recorded.events.append(("remove", task_id))

    def echo(message: object = None, **kwargs: Any) -> None:
        if kwargs.get("err") is True:
            recorded.events.append(("line", str(message)))
        original_echo(message, **kwargs)

    monkeypatch.setattr("fss_cli._progress._draws_progress", lambda: True)
    monkeypatch.setattr("fss_cli._progress.rich_progress.Progress", RecordingProgress)
    monkeypatch.setattr(typer, "echo", echo)
    return recorded


class _ScriptedFileSystem(AsyncFileSystem):
    """Files and directories in memory, with scripted ``_info`` after a write.

    ``after_write[path]`` is consumed one entry per post-write ``_info``; the
    last entry repeats. An exception entry is raised.
    """

    cachable = False

    def __init__(
        self,
        files: dict[str, bytes] | None = None,
        infos: dict[str, dict[str, object]] | None = None,
        after_write: dict[str, list[object]] | None = None,
    ) -> None:
        super().__init__(asynchronous=True)
        self.files = dict(files or {})
        self.infos = dict(infos or {})
        self.after_write = {path: list(script) for path, script in (after_write or {}).items()}
        self.written: set[str] = set()
        self.events: list[tuple[str, str]] = []
        self.callbacks: list[tuple[str, Callback]] = []

    async def _info(self, path: str, **kwargs: object) -> object:
        del kwargs
        self.events.append(("info", path))
        if path in self.written and path in self.after_write:
            script = self.after_write[path]
            entry = script.pop(0) if len(script) > 1 else script[0]
            if isinstance(entry, BaseException):
                raise entry
            return MappingProxyType(entry) if isinstance(entry, dict) else entry
        if path in self.files:
            info = {"type": "file", "size": len(self.files[path]), **self.infos.get(path, {})}
            return MappingProxyType(info)
        if path == "/" or any(name.startswith(f"{path.rstrip('/')}/") for name in self.files):
            return MappingProxyType({"type": "directory", "size": 0})
        raise FileNotFoundError(path)

    def _record_callback(self, hook: str, callback: Callback) -> None:
        self.callbacks.append((hook, callback))

    async def _get_file(
        self,
        rpath: str,
        lpath: str,
        callback: Callback = DEFAULT_CALLBACK,
        **kwargs: object,
    ) -> None:
        del kwargs
        self.events.append(("get_file", rpath))
        self._record_callback("get_file", callback)
        payload = self.files[rpath]
        callback.set_size(len(payload))
        Path(lpath).write_bytes(payload)  # noqa: ASYNC240
        callback.relative_update(len(payload))

    async def _put_file(
        self,
        lpath: str,
        rpath: str,
        mode: str = "overwrite",
        callback: Callback = DEFAULT_CALLBACK,
        **kwargs: object,
    ) -> None:
        del mode, kwargs
        self.events.append(("put_file", rpath))
        self._record_callback("put_file", callback)
        payload = Path(lpath).read_bytes()  # noqa: ASYNC240
        callback.set_size(len(payload))
        self.files[rpath] = payload
        self.written.add(rpath)
        callback.relative_update(len(payload))

    async def _cp_file(self, path1: str, path2: str, **kwargs: object) -> None:
        del kwargs
        self.events.append(("cp_file", path2))
        self.files[path2] = self.files[path1]
        self.written.add(path2)


class _SilentFileSystem(_ScriptedFileSystem):
    """Accepts fsspec callbacks and never reports bytes through them."""

    async def _get_file(self, rpath: str, lpath: str, **kwargs: object) -> None:
        del kwargs
        await super()._get_file(rpath, lpath)

    async def _put_file(self, lpath: str, rpath: str, mode: str = "overwrite", **kwargs: object) -> None:
        del kwargs
        await super()._put_file(lpath, rpath, mode)


class _ProgressCopyFileSystem(_ScriptedFileSystem):
    async def _cp_file(
        self,
        path1: str,
        path2: str,
        callback: Callback = DEFAULT_CALLBACK,
        **kwargs: object,
    ) -> None:
        del kwargs
        self.events.append(("cp_file", path2))
        self._record_callback("cp_file", callback)
        callback.set_size(len(self.files[path1]))
        self.files[path2] = self.files[path1]
        self.written.add(path2)
        callback.relative_update(len(self.files[path1]))


def _source(filesystem: AsyncFileSystem):
    @asynccontextmanager
    async def factory() -> AsyncIterator[AsyncFileSystem]:
        yield filesystem

    return factory


_PAYLOAD = b"0123456789"
_SIZE = len(_PAYLOAD)
_RESIDUE = "; destination residue may remain\n"


def _cross_source(
    after_write: list[object],
    *,
    source_infos: dict[str, object] | None = None,
    existing: bytes | None = None,
    existing_infos: dict[str, object] | None = None,
) -> tuple[_ScriptedFileSystem, _ScriptedFileSystem]:
    source = _ScriptedFileSystem(
        files={"/in/data.bin": _PAYLOAD},
        infos={"/in/data.bin": source_infos or {}},
    )
    destination = _ScriptedFileSystem(
        files={"/out/keep": b"", **({"/out/data.bin": existing} if existing is not None else {})},
        infos={"/out/data.bin": existing_infos or {}},
        after_write={"/out/data.bin": after_write},
    )
    return source, destination


def _run_cross(source: AsyncFileSystem, destination: AsyncFileSystem, *options: str):
    return _invoke(
        "cp",
        [*options, "src:/in/data.bin", "dst:/out/"],
        sources={"src": _source(source), "dst": _source(destination)},
    )


def _file(size: int, **tokens: object) -> dict[str, object]:
    return {"type": "file", "size": size, **tokens}


def _post_copy_infos(filesystem: _ScriptedFileSystem) -> int:
    write = next(index for index, event in enumerate(filesystem.events) if event[0] in {"put_file", "cp_file"})
    return sum(1 for event in filesystem.events[write:] if event == ("info", "/out/data.bin"))


def test_cp_polls_a_stale_zero_size_until_verified(clock: _Clock) -> None:
    source, destination = _cross_source([_file(0), _file(0), _file(_SIZE)])

    result = _run_cross(source, destination)

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("dst:/out/data.bin", "uploaded", _SIZE),
    )
    assert clock.sleeps == [5, 5]
    assert _post_copy_infos(destination) == 3
    assert destination.files["/out/data.bin"] == _PAYLOAD


def test_cp_polls_the_previous_size_of_an_overwritten_file(clock: _Clock) -> None:
    source, destination = _cross_source([_file(3), _file(_SIZE)], existing=b"old")

    result = _run_cross(source, destination)

    assert (result.exit_code, result.stderr) == (0, _cp_verified("dst:/out/data.bin", "uploaded", _SIZE))
    assert clock.sleeps == [5]


def test_cp_polls_an_unchanged_token_on_an_unchanged_size(clock: _Clock) -> None:
    source, destination = _cross_source(
        [_file(_SIZE, etag="before"), _file(_SIZE, etag="after")],
        source_infos={"ETag": "after"},
        existing=b"9876543210",
        existing_infos={"etag": "before"},
    )

    result = _run_cross(source, destination)

    assert (result.exit_code, result.stderr) == (0, _cp_verified("dst:/out/data.bin", "uploaded", _SIZE))
    assert clock.sleeps == [5]


def test_cp_reports_not_yet_verified_when_the_wait_ends(clock: _Clock) -> None:
    source, destination = _cross_source([_file(0)])

    result = _run_cross(source, destination, "--metadata-wait", "12")

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        f"cp: dst:/out/data.bin: uploaded {_SIZE} bytes\n"
        f"cp: dst:/out/data.bin: not yet verified (size 0, expected {_SIZE}){_RESIDUE}",
    )
    assert clock.sleeps == [5, 5, 2]
    assert _post_copy_infos(destination) == 4
    assert destination.files["/out/data.bin"] == _PAYLOAD


def test_cp_reports_an_unchanged_token_as_not_yet_verified(clock: _Clock) -> None:
    source, destination = _cross_source(
        [_file(_SIZE, etag="before")],
        source_infos={"ETag": "after"},
        existing=b"9876543210",
        existing_infos={"etag": "before"},
    )

    result = _run_cross(source, destination, "--metadata-wait", "5")

    assert (result.exit_code, result.stderr) == (
        1,
        f"cp: dst:/out/data.bin: uploaded {_SIZE} bytes\n"
        f"cp: dst:/out/data.bin: not yet verified (etag unchanged){_RESIDUE}",
    )
    assert clock.sleeps == [5]


def test_cp_zero_metadata_wait_reads_info_once(clock: _Clock) -> None:
    source, destination = _cross_source([_file(0), _file(_SIZE)])

    result = _run_cross(source, destination, "--metadata-wait", "0")

    assert result.exit_code == 1
    assert result.stderr.endswith(f"not yet verified (size 0, expected {_SIZE}){_RESIDUE}")
    assert clock.sleeps == []
    assert _post_copy_infos(destination) == 1


def test_cp_reports_a_third_size_without_polling(clock: _Clock) -> None:
    source, destination = _cross_source([_file(4), _file(_SIZE)])

    result = _run_cross(source, destination)

    assert (result.exit_code, result.stderr) == (
        1,
        f"cp: dst:/out/data.bin: uploaded {_SIZE} bytes\n"
        f"cp: dst:/out/data.bin: size mismatch (expected {_SIZE}, observed 4){_RESIDUE}",
    )
    assert clock.sleeps == []
    assert _post_copy_infos(destination) == 1


def test_cp_reports_a_shared_token_mismatch_without_polling(clock: _Clock) -> None:
    source, destination = _cross_source(
        [_file(_SIZE, etag="other"), _file(_SIZE, etag="source")],
        source_infos={"ETag": "source"},
    )

    result = _run_cross(source, destination)

    assert (result.exit_code, result.stderr) == (
        1,
        f"cp: dst:/out/data.bin: uploaded {_SIZE} bytes\ncp: dst:/out/data.bin: metadata mismatch (etag){_RESIDUE}",
    )
    assert clock.sleeps == []


def test_cp_reports_a_token_that_is_neither_source_nor_previous(clock: _Clock) -> None:
    source, destination = _cross_source(
        [_file(_SIZE, md5="third")],
        source_infos={"md5": "source"},
        existing=b"9876543210",
        existing_infos={"md5": "before"},
    )

    result = _run_cross(source, destination)

    assert result.stderr.endswith(f"metadata mismatch (md5){_RESIDUE}")
    assert clock.sleeps == []


def test_cp_ignores_tokens_reported_on_one_side_only(clock: _Clock) -> None:
    source, destination = _cross_source([_file(_SIZE, etag="destination-only")])

    result = _run_cross(source, destination)

    assert (result.exit_code, result.stderr) == (0, _cp_verified("dst:/out/data.bin", "uploaded", _SIZE))
    assert clock.sleeps == []


def test_cp_verifies_an_empty_file_without_polling(clock: _Clock) -> None:
    source = _ScriptedFileSystem(files={"/in/data.bin": b""})
    destination = _ScriptedFileSystem(files={"/out/keep": b""}, after_write={"/out/data.bin": [_file(0)]})

    result = _run_cross(source, destination)

    assert (result.exit_code, result.stderr) == (0, _cp_verified("dst:/out/data.bin", "uploaded", 0))
    assert clock.sleeps == []


@pytest.mark.parametrize(
    ("entry", "verdict"),
    [
        (PermissionError("denied"), "verification failure (PermissionError)"),
        ({"type": "directory", "size": 0}, "not a file after upload"),
        ({"type": "file", "size": -1}, "verification failure"),
        ("not-a-mapping", "verification failure"),
    ],
    ids=["info-raises", "not-a-file", "invalid-size", "incompatible"],
)
def test_cp_does_not_poll_unusable_post_copy_info(
    clock: _Clock,
    entry: object,
    verdict: str,
) -> None:
    source, destination = _cross_source([entry])

    result = _run_cross(source, destination)

    assert (result.exit_code, result.stderr) == (
        1,
        f"cp: dst:/out/data.bin: uploaded {_SIZE} bytes\ncp: dst:/out/data.bin: {verdict}{_RESIDUE}",
    )
    assert clock.sleeps == []


def test_cp_interrupt_during_the_wait_keeps_the_destination(monkeypatch: pytest.MonkeyPatch) -> None:
    async def interrupt(_seconds: float) -> None:
        raise KeyboardInterrupt

    monkeypatch.setattr("fss_cli._post_copy.asyncio", SimpleNamespace(sleep=interrupt))
    source, destination = _cross_source([_file(0)])

    result = _run_cross(source, destination)

    assert result.exit_code == 130
    assert result.stderr.startswith(f"cp: dst:/out/data.bin: uploaded {_SIZE} bytes\n")
    assert "verified" not in result.stderr
    assert destination.files["/out/data.bin"] == _PAYLOAD


def test_cp_waits_for_same_source_copies_too(clock: _Clock) -> None:
    filesystem = _ScriptedFileSystem(
        files={"/in/data.bin": _PAYLOAD, "/out/keep": b""},
        after_write={"/out/data.bin": [_file(0), _file(_SIZE)]},
    )

    result = _invoke("cp", ["fs:/in/data.bin", "fs:/out"], sources={"fs": _source(filesystem)})

    assert (result.exit_code, result.stderr) == (0, _cp_verified("fs:/out/data.bin", "copied", _SIZE))
    assert clock.sleeps == [5]
    assert filesystem.callbacks == []


def test_cp_moves_one_bar_from_the_download_to_the_upload(clock: _Clock, bars: _Bars) -> None:
    del clock
    source, destination = _cross_source([_file(_SIZE)])

    result = _run_cross(source, destination)

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("dst:/out/data.bin", "uploaded", _SIZE),
    )
    ((get_hook, download),) = source.callbacks
    ((put_hook, upload),) = destination.callbacks
    assert (get_hook, put_hook) == ("get_file", "put_file")
    assert isinstance(download, _TaskCallback)
    assert isinstance(upload, _TaskCallback)
    assert bars.events == [
        ("add", 0, "download src:/in/data.bin", False),
        ("update", 0, 0, _SIZE),
        ("update", 0, _SIZE, _SIZE),
        ("reset", 0, "upload dst:/out/data.bin", False),
        ("update", 0, 0, _SIZE),
        ("update", 0, _SIZE, _SIZE),
        ("remove", 0),
        ("line", f"cp: dst:/out/data.bin: uploaded {_SIZE} bytes"),
        ("line", f"cp: dst:/out/data.bin: verified {_SIZE} bytes"),
    ]


def test_cp_draws_one_shared_rich_progress_on_stderr(clock: _Clock, bars: _Bars) -> None:
    del clock
    source, destination = _cross_source([_file(_SIZE)])

    assert _run_cross(source, destination).exit_code == 0

    text, bar, remaining = bars.columns
    assert isinstance(text, rich_progress.TextColumn)
    assert text.text_format == "[bold blue]{task.description}"
    assert isinstance(bar, rich_progress.BarColumn)
    assert bar.bar_width is None
    assert isinstance(remaining, rich_progress.TimeRemainingColumn)
    assert bars.options["console"].file is bars.stderr
    assert (bars.options["transient"], bars.options["redirect_stdout"], bars.options["redirect_stderr"]) == (
        True,
        False,
        False,
    )
    assert isinstance(bars.stderr_during_copy, _StderrProxy)
    assert bars.created == 1


def test_cp_escapes_paths_in_the_bar_description(clock: _Clock, bars: _Bars) -> None:
    del clock
    source = _ScriptedFileSystem(files={"/in/[bold]data.bin": _PAYLOAD})
    destination = _ScriptedFileSystem(files={"/out/keep": b""})

    result = _invoke(
        "cp",
        ["src:/in/[bold]data.bin", "dst:/out/"],
        sources={"src": _source(source), "dst": _source(destination)},
    )

    assert result.exit_code == 0
    descriptions = [event[2] for event in bars.events if event[0] in {"add", "reset"}]
    assert [Text.from_markup(f"[bold blue]{description}").plain for description in descriptions] == [
        "download src:/in/[bold]data.bin",
        "upload dst:/out/[bold]data.bin",
    ]


def test_cp_gives_a_same_source_copy_that_accepts_a_callback_one_bar(bars: _Bars) -> None:
    filesystem = _ProgressCopyFileSystem(files={"/in/data.bin": _PAYLOAD, "/out/keep": b""})

    result = _invoke("cp", ["fs:/in/data.bin", "fs:/out/data.bin"], sources={"fs": _source(filesystem)})

    assert (result.exit_code, result.stderr) == (0, _cp_verified("fs:/out/data.bin", "copied", _SIZE))
    ((hook, callback),) = filesystem.callbacks
    assert (hook, type(callback)) == ("cp_file", _TaskCallback)
    assert bars.events[0] == ("add", 0, "copy fs:/out/data.bin", False)
    assert bars.events[-3:] == [
        ("remove", 0),
        ("line", f"cp: fs:/out/data.bin: copied {_SIZE} bytes"),
        ("line", f"cp: fs:/out/data.bin: verified {_SIZE} bytes"),
    ]


def test_cp_adds_no_bar_for_a_same_source_copy_without_a_callback(clock: _Clock, bars: _Bars) -> None:
    del clock
    filesystem = _ScriptedFileSystem(files={"/in/data.bin": _PAYLOAD, "/out/keep": b""})

    result = _invoke("cp", ["fs:/in/data.bin", "fs:/out/data.bin"], sources={"fs": _source(filesystem)})

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("fs:/out/data.bin", "copied", _SIZE),
    )
    assert bars.events == [
        ("line", f"cp: fs:/out/data.bin: copied {_SIZE} bytes"),
        ("line", f"cp: fs:/out/data.bin: verified {_SIZE} bytes"),
    ]


def test_cp_keeps_a_bar_hidden_until_the_backend_reports_bytes(clock: _Clock, bars: _Bars) -> None:
    del clock
    source = _SilentFileSystem(files={"/in/data.bin": _PAYLOAD})
    destination = _SilentFileSystem(files={"/out/keep": b""})

    result = _run_cross(source, destination)

    assert result.exit_code == 0
    assert [event[0] for event in bars.events] == ["add", "reset", "remove", "line", "line"]
    assert bars.visible_peak == []


def test_cp_recursive_shows_one_live_bar_per_in_flight_file(bars: _Bars) -> None:
    names = ("a.bin", "b.bin", "c.bin")
    source_entries: dict[str, bytes | None] = {"/": None, "/docs": None}
    source_entries.update({f"/docs/{name}": name.encode() * 4 for name in names})
    destination_entries: dict[str, bytes | None] = {"/": None, "/out": None}
    gate = asyncio.Event()
    arrived: list[str] = []
    live_at_gate: list[list[str]] = []

    async def get_file(
        self: _TreeFileSystem,
        remote: str,
        local: str,
        callback: Callback = DEFAULT_CALLBACK,
        **kwargs: object,
    ) -> None:
        del kwargs
        payload = self.entries[remote]
        assert isinstance(payload, bytes)
        callback.set_size(len(payload))
        callback.relative_update(1)
        arrived.append(remote)
        if len(arrived) == len(names):
            live_at_gate.append(bars.live_descriptions())
            gate.set()
        await gate.wait()
        Path(local).write_bytes(payload)  # noqa: ASYNC240
        callback.relative_update(len(payload) - 1)

    async def put_file(
        self: _TreeFileSystem,
        local: str,
        remote: str,
        mode: str = "overwrite",
        callback: Callback = DEFAULT_CALLBACK,
        **kwargs: object,
    ) -> None:
        del mode, kwargs
        payload = Path(local).read_bytes()  # noqa: ASYNC240
        callback.set_size(len(payload))
        self.entries[remote] = payload
        callback.relative_update(len(payload))

    def configure(hook: str, method: Callable[..., Awaitable[None]]) -> Callable[[_TreeFileSystem], None]:
        def install(filesystem: _TreeFileSystem) -> None:
            setattr(filesystem, hook, MethodType(method, filesystem))

        return install

    result = _invoke(
        "cp",
        ["-R", "src:/docs", "dst:/out/copy"],
        sources={
            "src": _tree_source(source_entries, [], configure=configure("_get_file", get_file)),
            "dst": _tree_source(destination_entries, [], configure=configure("_put_file", put_file)),
        },
    )

    assert (result.exit_code, result.stdout) == (0, "")
    assert live_at_gate == [[f"download src:/docs/{name}" for name in names]]
    assert _outcomes_by_file(result.stderr) == {
        f"dst:/out/copy/{name}": ["uploaded 20 bytes", "verified 20 bytes"] for name in names
    }
    for task, name in enumerate(names):
        mine = [event for event in bars.events if event[:2] in {("add", task), ("reset", task), ("remove", task)}]
        assert mine == [
            ("add", task, f"download src:/docs/{name}", False),
            ("reset", task, f"upload dst:/out/copy/{name}", False),
            ("remove", task),
        ]
        removed = bars.events.index(("remove", task))
        assert removed < bars.events.index(("line", f"cp: dst:/out/copy/{name}: uploaded 20 bytes"))
    assert bars.progress.tasks == []
    assert bars.created == 1


class _Terminal(io.StringIO):
    def isatty(self) -> bool:
        return True


@pytest.mark.parametrize(
    ("stream", "no_color", "drawn"),
    [
        (_Terminal(), None, True),
        (_Terminal(), "", True),
        (_Terminal(), "1", False),
        (io.StringIO(), None, False),
    ],
    ids=["tty", "tty-empty-no-color", "tty-no-color", "pipe"],
)
def test_bars_draw_only_on_a_terminal_without_no_color(
    monkeypatch: pytest.MonkeyPatch,
    stream: io.StringIO,
    no_color: str | None,
    *,
    drawn: bool,
) -> None:
    monkeypatch.setattr("fss_cli._progress.sys", SimpleNamespace(stderr=stream))
    if no_color is None:
        monkeypatch.delenv("NO_COLOR", raising=False)
    else:
        monkeypatch.setenv("NO_COLOR", no_color)

    assert _draws_progress() is drawn


def _refuse_progress(*_args: object, **_kwargs: object) -> NoReturn:
    message = "no Progress off a terminal"
    raise AssertionError(message)


def test_cp_draws_no_bar_when_stderr_is_not_a_terminal(monkeypatch: pytest.MonkeyPatch, clock: _Clock) -> None:
    del clock
    monkeypatch.setattr("fss_cli._progress.rich_progress.Progress", _refuse_progress)
    source, destination = _cross_source([_file(_SIZE)])

    result = _run_cross(source, destination)

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("dst:/out/data.bin", "uploaded", _SIZE),
    )
    assert [callback for _hook, callback in source.callbacks + destination.callbacks] == [
        DEFAULT_CALLBACK,
        DEFAULT_CALLBACK,
    ]


def test_cp_recursive_draws_no_bar_when_stderr_is_not_a_terminal(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr("fss_cli._progress.rich_progress.Progress", _refuse_progress)
    callbacks: list[object] = []

    def record(filesystem: _TreeFileSystem) -> None:
        for hook in ("_get_file", "_put_file"):
            original = getattr(filesystem, hook)

            async def recorded(*args: object, _original: Any = original, **kwargs: object) -> None:
                callbacks.append(kwargs.get("callback"))
                await _original(*args, **kwargs)

            setattr(filesystem, hook, recorded)

    result = _invoke(
        "cp",
        ["-R", "src:/docs", "dst:/out/copy"],
        sources={
            "src": _tree_source({"/": None, "/docs": None, "/docs/a.bin": b"aa"}, [], configure=record),
            "dst": _tree_source({"/": None, "/out": None}, [], configure=record),
        },
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("dst:/out/copy/a.bin", "uploaded", 2),
    )
    assert callbacks == [DEFAULT_CALLBACK, DEFAULT_CALLBACK]


def test_cp_draws_no_bar_on_a_terminal_when_no_color_is_set(monkeypatch: pytest.MonkeyPatch, clock: _Clock) -> None:
    del clock
    terminal = _Terminal()
    monkeypatch.setattr("fss_cli._progress.sys", SimpleNamespace(stderr=terminal))
    monkeypatch.setattr("fss_cli._progress.rich_progress.Progress", _refuse_progress)
    source, destination = _cross_source([_file(_SIZE)])

    result = _invoke(
        "cp",
        ["src:/in/data.bin", "dst:/out/"],
        sources={"src": _source(source), "dst": _source(destination)},
        env={"NO_COLOR": "1"},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("dst:/out/data.bin", "uploaded", _SIZE),
    )
    assert terminal.getvalue() == ""


class _SlowFileSystem(_ScriptedFileSystem):
    """Holds each download open until the terminal shows a drawn bar."""

    def __init__(self, terminal: _Terminal, **kwargs: dict[str, bytes]) -> None:
        super().__init__(**kwargs)
        self.terminal = terminal

    async def _get_file(
        self,
        rpath: str,
        lpath: str,
        callback: Callback = DEFAULT_CALLBACK,
        **kwargs: object,
    ) -> None:
        del kwargs
        payload = self.files[rpath]
        callback.set_size(len(payload))
        callback.relative_update(len(payload) // 2)
        deadline = time.monotonic() + 5
        while "download" not in self.terminal.getvalue() and time.monotonic() < deadline:
            time.sleep(0.01)  # noqa: ASYNC251 - blocking lets Rich's refresh thread draw.
        Path(lpath).write_bytes(payload)  # noqa: ASYNC240
        callback.relative_update(len(payload) - len(payload) // 2)


def test_cp_draws_the_bar_and_prints_outcome_lines_above_it_on_a_terminal(
    monkeypatch: pytest.MonkeyPatch,
    clock: _Clock,
) -> None:
    del clock
    terminal = _Terminal()
    monkeypatch.setattr("fss_cli._progress.sys", SimpleNamespace(stderr=terminal))
    monkeypatch.delenv("NO_COLOR", raising=False)
    # Rich treats TERM=dumb as a terminal that must not receive a live bar.
    monkeypatch.setenv("TERM", "xterm-256color")
    source = _SlowFileSystem(terminal, files={"/in/data.bin": _PAYLOAD})
    destination = _ScriptedFileSystem(files={"/out/keep": b""})

    result = _run_cross(source, destination)

    drawn = strip_ansi(terminal.getvalue())
    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert "download src:/in/data.bin" in drawn
    assert drawn.index("download src:/in/data.bin") < drawn.index(f"cp: dst:/out/data.bin: uploaded {_SIZE} bytes")
    assert drawn.index(f"uploaded {_SIZE} bytes") < drawn.index(f"cp: dst:/out/data.bin: verified {_SIZE} bytes")


def test_cp_outcome_line_write_failure_ends_the_copy(monkeypatch: pytest.MonkeyPatch) -> None:
    renderer_error = RuntimeError("stderr failed")

    def fail_stderr(_message: object = None, **kwargs: object) -> None:
        if kwargs.get("err") is True:
            raise renderer_error

    monkeypatch.setattr(typer, "echo", fail_stderr)
    source, destination = _cross_source([_file(_SIZE)])

    result = _run_cross(source, destination)

    assert result.exit_code == 1
    assert result.exception is renderer_error
    assert _post_copy_infos(destination) == 0


def test_cp_metadata_wait_rejects_negative_seconds() -> None:
    result = _invoke("cp", ["--metadata-wait", "-1", "memory:/a", "memory:/b"])

    assert result.exit_code == 2
    assert "--metadata-wait" in strip_ansi(result.stderr)


@pytest.mark.parametrize("copy", [True, False], ids=["recursive-copy", "file-copy-only"])
def test_cp_declares_metadata_wait_in_both_signatures(*, copy: bool) -> None:
    app = App({"memory": _source_must_not_run}, capabilities={"recursion": {"copy": copy}})
    command = typer.main.get_command(app.typer_app).commands["cp"]

    (option,) = [parameter for parameter in command.params if "--metadata-wait" in parameter.opts]

    assert (option.default, option.metavar, option.help is not None) == (60, "SECONDS", True)


def test_cp_rejects_stale_info_for_every_source_in_a_multi_file_copy(clock: _Clock) -> None:
    source = _ScriptedFileSystem(files={"/in/a.bin": b"aa", "/in/b.bin": b"bbb"})
    destination = _ScriptedFileSystem(
        files={"/out/keep": b""},
        after_write={"/out/a.bin": [_file(2)], "/out/b.bin": [_file(0)]},
    )

    result = _invoke(
        "cp",
        ["--metadata-wait", "0", "src:/in/a.bin", "src:/in/b.bin", "dst:/out"],
        sources={"src": _source(source), "dst": _source(destination)},
    )

    assert (result.exit_code, result.stderr) == (
        1,
        _cp_verified("dst:/out/a.bin", "uploaded", 2)
        + "cp: dst:/out/b.bin: uploaded 3 bytes\n"
        + f"cp: dst:/out/b.bin: not yet verified (size 0, expected 3){_RESIDUE}",
    )
    assert clock.sleeps == []
