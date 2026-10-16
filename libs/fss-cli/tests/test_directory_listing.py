"""Directory listing tests through the public embedded-command seam."""

import asyncio
import io
import locale
import sys
import threading
from collections.abc import Callable, Coroutine

import pytest
import typer

from fss_cli import _ls

from ._support import _files, _invoke, _RecordingFileSystem, _RecordingSource

_BOLD = "\x1b[1m"
_NORMAL = "\x1b[22m"


def test_ls_lists_one_directory_through_detail_async_operations() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        {"type": "directory"},
        ls_result=_files("/docs/notes.txt", "/docs/guide.md"),
    )

    result = _invoke("ls", ["memory:/docs"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stdout == "guide.md\nnotes.txt\n"
    assert result.stderr == ""
    assert [(event[0], *event[2:-1]) for event in events] == [
        ("factory",),
        ("enter",),
        ("info", "/docs"),
        ("ls", "/docs", True),
        ("exit",),
    ]


def test_ls_writes_nothing_for_an_empty_directory() -> None:
    source = _RecordingSource(
        [],
        {"type": "directory"},
        ls_result=[],
    )

    result = _invoke("ls", ["memory:/empty"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == ""


@pytest.mark.parametrize(
    "listing",
    [
        None,
        "/docs/guide.md",
        {"name": "/docs/guide.md"},
        (_files("/docs/guide.md")[0],),
        [1],
        ["/docs/guide.md"],
        [*_files("/docs/guide.md"), None],
        [{"name": 1, "type": "file"}],
        [{"type": "file"}],
    ],
)
def test_ls_rejects_non_concrete_detail_lists(listing: object) -> None:
    source = _RecordingSource(
        [],
        {"type": "directory"},
        ls_result=listing,
    )

    result = _invoke("ls", ["memory:/docs"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "ls: memory:/docs: incompatible result\n"


@pytest.mark.parametrize(
    "child",
    [
        "memory:///docs/guide.md",
        "/other/guide.md",
        "/docs/nested/guide.md",
        "/docs/",
        "/docs/bad\0name",
        "/docs/bad\nname",
    ],
)
def test_ls_rejects_non_immediate_lexical_children(child: str) -> None:
    source = _RecordingSource(
        [],
        {"type": "directory"},
        ls_result=_files(child),
    )

    result = _invoke("ls", ["memory:/docs"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "ls: memory:/docs: incompatible result\n"


@pytest.mark.parametrize(
    ("path", "listing", "stdout"),
    [
        ("/docs/", "/docs/guide.md", "guide.md\n"),
        ("/docs///", "/docs/guide.md", "guide.md\n"),
        ("/", "/guide.md", "guide.md\n"),
    ],
)
def test_ls_validates_root_and_trailing_slash_children(
    path: str,
    listing: str,
    stdout: str,
) -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        {"type": "directory"},
        ls_result=_files(listing),
    )

    result = _invoke("ls", [f"memory:{path}"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stdout == stdout
    assert result.stderr == ""
    assert [(event[0], event[2]) for event in events if event[0] in {"info", "ls"}] == [
        ("info", path),
        ("ls", path),
    ]


def test_ls_omits_dot_prefixed_directory_children_by_default() -> None:
    source = _RecordingSource(
        [],
        {"type": "directory"},
        ls_result=_files(
            "/docs/.hidden",
            "/docs/visible",
            "/docs/.",
            "/docs/..",
        ),
    )

    result = _invoke("ls", ["memory:/docs"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stdout == "visible\n"
    assert result.stderr == ""


def test_ls_almost_all_includes_hidden_children_but_not_dot_entries() -> None:
    source = _RecordingSource(
        [],
        {"type": "directory"},
        ls_result=_files(
            "/docs/visible",
            "/docs/..",
            "/docs/.hidden",
            "/docs/.",
        ),
    )

    result = _invoke("ls", ["-A", "memory:/docs"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stdout == ".hidden\nvisible\n"
    assert result.stderr == ""


def test_ls_preserves_duplicate_directory_children() -> None:
    source = _RecordingSource(
        [],
        {"type": "directory"},
        ls_result=_files("/docs/guide.md", "/docs/guide.md"),
    )

    result = _invoke("ls", ["memory:/docs"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stdout == "guide.md\nguide.md\n"
    assert result.stderr == ""


def test_ls_validates_hidden_children_before_filtering() -> None:
    source = _RecordingSource(
        [],
        {"type": "directory"},
        ls_result=_files("/docs/visible", "/docs/.nested/bad"),
    )

    result = _invoke("ls", ["memory:/docs"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "ls: memory:/docs: incompatible result\n"


def test_ls_keeps_an_explicitly_named_dot_prefixed_file_operand() -> None:
    source = _RecordingSource([], {"type": "file"})

    result = _invoke("ls", ["memory:/.hidden"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stdout == "memory:/.hidden\n"
    assert result.stderr == ""


def test_ls_uses_current_collation_with_raw_string_ties(monkeypatch) -> None:
    collation_keys = {"zeta": "0", "beta": "1", "alpha": "1"}
    monkeypatch.setattr(locale, "strxfrm", collation_keys.__getitem__)
    source = _RecordingSource(
        [],
        {"type": "directory"},
        ls_result=_files("/docs/beta", "/docs/zeta", "/docs/alpha"),
    )

    result = _invoke("ls", ["memory:/docs"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stdout == "zeta\nalpha\nbeta\n"
    assert result.stderr == ""


def test_ls_preserves_a_collation_failure_without_a_backend_diagnostic(
    monkeypatch,
) -> None:
    collation_error = ValueError("collation failed")

    def fail_collation(_name: str) -> str:
        raise collation_error

    monkeypatch.setattr(locale, "strxfrm", fail_collation)
    source = _RecordingSource(
        [],
        {"type": "directory"},
        ls_result=_files("/docs/guide.md"),
    )

    result = _invoke("ls", ["memory:/docs"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.exception is collation_error
    assert result.stdout == ""
    assert result.stderr == ""
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is ValueError
    assert exception is collation_error
    assert traceback is not None


@pytest.mark.parametrize("stage", ["info", "ls"])
@pytest.mark.parametrize(
    ("error_factory", "category"),
    [
        (FileNotFoundError, "not found"),
        (PermissionError, "permission denied"),
        (NotADirectoryError, "not a directory"),
        (NotImplementedError, "unsupported operation"),
        (RuntimeError, "backend failure (RuntimeError): "),
        (
            lambda: RuntimeError("backend\\\0\r\n"),
            "backend failure (RuntimeError): backend\\\\\\x00\\x0d\\x0a",
        ),
    ],
)
def test_ls_maps_runtime_failures_to_locked_categories(
    stage: str,
    error_factory: Callable[[], Exception],
    category: str,
) -> None:
    error = error_factory()
    source = _RecordingSource(
        [],
        {"type": "directory"},
        info_error=error if stage == "info" else None,
        ls_result=[],
        ls_error=error if stage == "ls" else None,
    )

    result = _invoke("ls", ["memory:/docs"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == f"ls: memory:/docs: {category}\n"
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is type(error)
    assert exception is error
    assert traceback is not None


def test_ls_preserves_backend_error_when_its_diagnostic_write_fails(
    monkeypatch,
) -> None:
    backend_error = PermissionError("denied")
    renderer_error = RuntimeError("stderr failed")
    source = _RecordingSource([], info_error=backend_error)

    def fail_diagnostic(
        _message: object = None,
        *args: object,
        **kwargs: object,
    ) -> None:
        del args
        if kwargs.get("err") is True:
            raise renderer_error
        raise AssertionError

    monkeypatch.setattr(typer, "echo", fail_diagnostic)

    result = _invoke("ls", ["memory:/file"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.exception is renderer_error
    assert result.stdout == ""
    assert result.stderr == ""
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is PermissionError
    assert exception is backend_error
    assert traceback is not None


def test_ls_buffers_a_whole_directory_before_writing_stdout() -> None:
    source = _RecordingSource(
        [],
        {"type": "directory"},
        ls_result=_files("/docs/accepted.txt", "/docs/nested/rejected.txt"),
    )

    result = _invoke("ls", ["memory:/docs"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "ls: memory:/docs: incompatible result\n"


class _ControlFlow(BaseException):
    pass


@pytest.mark.parametrize(
    "control",
    [asyncio.CancelledError(), _ControlFlow("stop")],
)
def test_ls_preserves_directory_listing_control_flow_unchanged(
    control: BaseException,
) -> None:
    source = _RecordingSource(
        [],
        {"type": "directory"},
        ls_error=control,
    )

    with pytest.raises(type(control)) as caught:
        _invoke("ls", ["memory:/docs"], sources={"memory": source})

    assert type(caught.value) is type(control)
    if not isinstance(control, asyncio.CancelledError):
        assert caught.value is control
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is type(control)
    assert exception is control
    assert traceback is not None


def test_ls_drains_started_operations_then_stops_on_cancellation(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    events: list[tuple[object, ...]] = []
    started = threading.Event()
    release: asyncio.Event
    alpha = _RecordingSource(events)
    beta = _RecordingSource(events)
    real_run = asyncio.run

    async def blocking_info(
        filesystem: _RecordingFileSystem,
        path: str,
        **kwargs: object,
    ) -> object:
        assert not kwargs
        filesystem.source.events.append(("info-start", path))
        started.set()
        await release.wait()
        filesystem.source.events.append(("info-done", path))
        return {"type": "file"}

    def cancelling_run(coroutine: Coroutine[object, object, None]) -> None:
        async def supervise() -> None:
            nonlocal release
            release = asyncio.Event()
            command_task = asyncio.create_task(coroutine)
            assert await asyncio.to_thread(started.wait, 5)
            command_task.cancel("listing cancelled")
            asyncio.get_running_loop().call_later(0.01, release.set)
            await command_task

        real_run(supervise())

    monkeypatch.setattr(_RecordingFileSystem, "_info", blocking_info)
    monkeypatch.setattr(asyncio, "run", cancelling_run)

    with pytest.raises(asyncio.CancelledError):
        _invoke(
            "ls",
            ["alpha:/one", "beta:/two"],
            sources={"alpha": alpha, "beta": beta},
        )

    # Operand reads overlap, so both are in flight when cancellation arrives;
    # each started read is still drained before the sources exit.
    hooks = {"info-start", "info-done"}
    assert [event[:2] if event[0] in hooks else event[:1] for event in events] == [
        ("factory",),
        ("enter",),
        ("factory",),
        ("enter",),
        ("info-start", "/one"),
        ("info-start", "/two"),
        ("info-done", "/one"),
        ("info-done", "/two"),
        ("exit",),
        ("exit",),
    ]


def test_ls_ignores_metadata_that_only_a_long_listing_prints() -> None:
    source = _RecordingSource(
        [],
        {"type": "directory"},
        ls_result=[
            {"name": "/docs/guide.md", "type": "file", "owner": "bad\nowner", "permissions": "nonsense"},
        ],
    )

    result = _invoke("ls", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (0, "guide.md\n", "")


def _emphasis_fixture() -> _RecordingSource:
    return _RecordingSource(
        [],
        info_by_path={
            "/docs": {"type": "directory"},
            "/file": {"type": "file"},
            "/linked": {"type": "directory", "islink": True},
        },
        ls_by_path={
            "/docs": [
                {"name": "/docs/sub", "type": "directory"},
                {"name": "/docs/bucket", "type": "dir"},
                {"name": "/docs/link", "type": "directory", "islink": True},
                {"name": "/docs/odd", "type": ["directory"]},
                {"name": "/docs/a.txt", "type": "file"},
                {"name": "/docs/\x1b[31mred\x1b[0m", "type": "directory"},
            ],
        },
    )


def test_ls_bolds_directory_children_and_headers_when_emphasis_is_on(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(_ls, "_emphasis_enabled", lambda: True)

    result = _invoke(
        "ls",
        ["memory:/docs", "memory:/file", "memory:/linked"],
        sources={"memory": _emphasis_fixture()},
    )

    assert result.exit_code == 0
    assert result.stdout == (
        "memory:/file\nmemory:/linked\n\n"
        f"{_BOLD}memory:/docs{_NORMAL}:\n"
        f"{_BOLD}\x1b[31mred\x1b[0m{_NORMAL}\n"
        "a.txt\n"
        f"{_BOLD}bucket{_NORMAL}\n"
        "link\n"
        "odd\n"
        f"{_BOLD}sub{_NORMAL}\n"
    )
    assert result.stderr == ""


def test_ls_output_is_unstyled_when_emphasis_is_off() -> None:
    result = _invoke(
        "ls",
        ["memory:/docs", "memory:/file", "memory:/linked"],
        sources={"memory": _emphasis_fixture()},
    )

    assert result.exit_code == 0
    assert result.stdout == (
        "memory:/file\nmemory:/linked\n\nmemory:/docs:\n\x1b[31mred\x1b[0m\na.txt\nbucket\nlink\nodd\nsub\n"
    )


def test_long_ls_bolds_only_directory_name_cells_when_emphasis_is_on(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(_ls, "_emphasis_enabled", lambda: True)
    source = _RecordingSource(
        [],
        {"type": "directory"},
        ls_result=[
            {"name": "/docs/sub", "type": "directory", "size": 0},
            {"name": "/docs/a.txt", "type": "file", "size": 12},
        ],
    )

    result = _invoke("ls", ["-l", "memory:/docs"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stdout == (f"-?????????  -  -  -  12  -  a.txt\nd?????????  -  -  -   0  -  {_BOLD}sub{_NORMAL}\n")


class _Stdout(io.StringIO):
    def __init__(self, *, tty: bool) -> None:
        super().__init__()
        self._tty = tty

    def isatty(self) -> bool:
        super().isatty()
        return self._tty


@pytest.mark.parametrize(
    ("tty", "environment", "enabled"),
    [
        pytest.param(True, {"TERM": "xterm-256color"}, True, id="tty"),
        pytest.param(True, {}, True, id="tty-without-term"),
        pytest.param(True, {"TERM": "xterm", "NO_COLOR": ""}, True, id="empty-no-color"),
        pytest.param(False, {"TERM": "xterm"}, False, id="pipe"),
        pytest.param(True, {"TERM": "xterm", "NO_COLOR": "1"}, False, id="no-color"),
        pytest.param(True, {"TERM": "dumb"}, False, id="dumb-term"),
        pytest.param(True, {"TERM": "unknown"}, False, id="unknown-term"),
    ],
)
def test_emphasis_requires_a_tty_without_no_color_or_a_dumb_term(
    monkeypatch: pytest.MonkeyPatch,
    tty: bool,
    environment: dict[str, str],
    enabled: bool,
) -> None:
    monkeypatch.delenv("NO_COLOR", raising=False)
    monkeypatch.delenv("TERM", raising=False)
    for name, value in environment.items():
        monkeypatch.setenv(name, value)
    monkeypatch.setattr(sys, "stdout", _Stdout(tty=tty))

    assert _ls._emphasis_enabled() is enabled


def test_emphasis_is_off_for_a_missing_or_closed_stdout(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(sys, "stdout", None)
    assert _ls._emphasis_enabled() is False

    closed = _Stdout(tty=True)
    closed.close()
    monkeypatch.setattr(sys, "stdout", closed)
    assert _ls._emphasis_enabled() is False
