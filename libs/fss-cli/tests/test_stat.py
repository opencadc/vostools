"""Reduced BSD/macOS ``stat`` tests through the public seam."""

from __future__ import annotations

import asyncio
import math

import pytest
import typer

from fss_cli._stat import _format_mtime, _write_line

from ._support import _invoke, _RecordingSource, _source_must_not_run


@pytest.fixture(autouse=True)
def _pin_utc_timezone() -> None:
    import os
    import time

    previous = os.environ.get("TZ")
    os.environ["TZ"] = "UTC"
    time.tzset()
    try:
        yield
    finally:
        if previous is None:
            os.environ.pop("TZ", None)
        else:
            os.environ["TZ"] = previous
        time.tzset()


@pytest.fixture(autouse=True)
def _unresolvable_owner_group(monkeypatch: pytest.MonkeyPatch) -> None:
    # Goldens use decimal uid/gid; host account DB must not rename them.
    def missing_user(uid: int) -> None:
        raise KeyError(uid)

    def missing_group(gid: int) -> None:
        raise KeyError(gid)

    monkeypatch.setattr("fss_cli._accounts.pwd.getpwuid", missing_user)
    monkeypatch.setattr("fss_cli._accounts.grp.getgrgid", missing_group)


# Goldens use unresolvable uid/gid (see _unresolvable_owner_group).
_RICH_FILE: dict[str, object] = {
    "name": "/stat-file",
    "size": 3,
    "type": "file",
    "islink": False,
    "mode": 33188,
    "nlink": 1,
    "uid": 424242,
    "gid": 424242,
    "mtime": 1784325970.7683342,
}
_RICH_DIR: dict[str, object] = {
    "name": "/stat-dir",
    "size": 96,
    "type": "directory",
    "islink": False,
    "mode": 16877,
    "nlink": 3,
    "uid": 424242,
    "gid": 424242,
    "mtime": 1784325970.768656,
}

# Locked under TZ=UTC; independent of private _stat formatters.
_GOLDEN_FILE = '-rw-r--r-- 1 424242 424242 3 "Jul 17 22:06:10 2026" /stat-file\n'
_GOLDEN_DIR = 'drwxr-xr-x 3 424242 424242 96 "Jul 17 22:06:10 2026" /stat-dir\n'
_GOLDEN_A = '-rw-r--r-- 1 424242 424242 1 "Jul 17 22:06:10 2026" /stat-a\n'
_GOLDEN_B = '-rw-r--r-- 1 424242 424242 2 "Jul 17 22:06:10 2026" /stat-b\n'


def test_stat_help_matches_locked_usage_and_draft() -> None:
    result = _invoke("stat", ["--help"])

    assert result.exit_code == 0
    plain_help = result.stdout
    assert "Usage:" in plain_help
    assert "root stat [OPTIONS] {name:/path}" in plain_help
    assert "Display file status" in plain_help


def test_stat_renders_one_local_rich_file_line() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events, info_result=_RICH_FILE)

    result = _invoke("stat", ["memory:/stat-file"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stderr == ""
    assert result.stdout == _GOLDEN_FILE
    assert [(event[0], event[2]) for event in events if event[0] == "info"] == [("info", "/stat-file")]
    assert not any(event[0] == "ls" for event in events)


def test_stat_renders_one_local_rich_directory_line() -> None:
    source = _RecordingSource([], info_result=_RICH_DIR)

    result = _invoke("stat", ["memory:/stat-dir"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stderr == ""
    assert result.stdout == _GOLDEN_DIR


def test_stat_renders_multiple_operands_in_argv_order() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_by_path={
            "/stat-a": {**_RICH_FILE, "name": "/stat-a", "size": 1},
            "/stat-b": {**_RICH_FILE, "name": "/stat-b", "size": 2},
        },
    )

    result = _invoke(
        "stat",
        ["memory:/stat-a", "memory:/stat-b"],
        sources={"memory": source},
    )

    assert result.exit_code == 0
    assert result.stderr == ""
    assert result.stdout == _GOLDEN_A + _GOLDEN_B
    assert [event[2] for event in events if event[0] == "info"] == [
        "/stat-a",
        "/stat-b",
    ]


def test_stat_continues_after_missing_path() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_by_path={
            "/stat-a": {**_RICH_FILE, "name": "/stat-a", "size": 1},
            "/stat-missing": FileNotFoundError("gone"),
            "/stat-b": {**_RICH_FILE, "name": "/stat-b", "size": 2},
        },
    )

    result = _invoke(
        "stat",
        ["memory:/stat-a", "memory:/stat-missing", "memory:/stat-b"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stderr == "stat: memory:/stat-missing: not found\n"
    assert result.stdout == _GOLDEN_A + _GOLDEN_B
    assert [event[2] for event in events if event[0] == "info"] == [
        "/stat-a",
        "/stat-missing",
        "/stat-b",
    ]


def test_stat_rejects_symlink_as_incompatible() -> None:
    source = _RecordingSource(
        [],
        info_result={**_RICH_FILE, "islink": True, "destination": "file.txt"},
    )

    result = _invoke("stat", ["memory:/stat-link"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "stat: memory:/stat-link: incompatible result\n"


def test_stat_rejects_incomplete_memory_shape() -> None:
    source = _RecordingSource(
        [],
        info_result={
            "name": "/file.txt",
            "size": 3,
            "type": "file",
            "created": "unused",
        },
    )

    result = _invoke("stat", ["memory:/file.txt"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "stat: memory:/file.txt: incompatible result\n"


@pytest.mark.parametrize(
    "info",
    [
        {**_RICH_FILE, "size": None},
        {**_RICH_FILE, "size": True},
        {**_RICH_FILE, "uid": False},
        {**_RICH_FILE, "mode": True},
        {**_RICH_FILE, "nlink": True},
        {**_RICH_FILE, "gid": False},
        {**_RICH_FILE, "mtime": True},
        {**_RICH_FILE, "mtime": "2026-07-17T00:00:00Z"},
        {**_RICH_FILE, "mode": "33188"},
        {**_RICH_FILE, "type": "other"},
        {**_RICH_FILE, "nlink": 0},
        {**_RICH_FILE, "mtime": math.nan},
        {**_RICH_FILE, "mtime": math.inf},
        {**_RICH_FILE, "mtime": 1e100},
        {k: v for k, v in _RICH_FILE.items() if k != "mode"},
    ],
)
def test_stat_rejects_malformed_info(info: dict[str, object]) -> None:
    source = _RecordingSource([], info_result=info)

    result = _invoke("stat", ["memory:/stat-file"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "stat: memory:/stat-file: incompatible result\n"


def test_stat_falls_back_to_decimal_when_owner_lookup_overflows(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    def overflow_user(uid: int) -> None:
        raise OverflowError(uid)

    def overflow_group(gid: int) -> None:
        raise OverflowError(gid)

    monkeypatch.setattr("fss_cli._accounts.pwd.getpwuid", overflow_user)
    monkeypatch.setattr("fss_cli._accounts.grp.getgrgid", overflow_group)
    source = _RecordingSource([], info_result=_RICH_FILE)

    result = _invoke("stat", ["memory:/stat-file"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stderr == ""
    assert result.stdout == _GOLDEN_FILE


def test_stat_ignores_extra_info_keys() -> None:
    info = {
        **_RICH_FILE,
        "ino": 99,
        "created": 1.0,
        "uri": "vos://x",
        "properties": {"k": "v"},
    }
    source = _RecordingSource([], info_result=info)

    result = _invoke("stat", ["memory:/stat-file"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stderr == ""
    assert result.stdout == _GOLDEN_FILE


def test_stat_escapes_backend_message_control_characters() -> None:
    source = _RecordingSource(
        [],
        info_error=OSError("bad\\\0\r\npath"),
    )

    result = _invoke("stat", ["memory:/stat-x"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == ("stat: memory:/stat-x: backend failure (OSError): bad\\\\\\x00\\x0d\\x0apath\n")


def test_stat_acquires_distinct_sources_before_reuse() -> None:
    events: list[tuple[object, ...]] = []
    shared = _RecordingSource(events, info_result=_RICH_FILE)

    result = _invoke(
        "stat",
        ["alpha:/one", "beta:/two", "alpha:/three"],
        sources={
            "beta": shared,
            "alpha": shared,
            "unused": _source_must_not_run,
        },
    )

    assert result.exit_code == 0
    assert [(event[0], *event[1:-1]) for event in events] == [
        ("factory",),
        ("enter", 1),
        ("factory",),
        ("enter", 2),
        ("info", 1, "/one"),
        ("info", 2, "/two"),
        ("info", 1, "/three"),
        ("exit", 2),
        ("exit", 1),
    ]


@pytest.mark.parametrize(
    "arguments",
    [
        ["-l", "memory:/x"],
        ["-f", "%N", "memory:/x"],
        ["-x", "memory:/x"],
        ["--format=%n", "memory:/x"],
        ["-L", "memory:/x"],
        ["-r", "memory:/x"],
        ["-s", "memory:/x"],
        ["-n", "memory:/x"],
        ["-q", "memory:/x"],
        ["-t", "%Y", "memory:/x"],
        ["--printf=%n", "memory:/x"],
    ],
)
def test_stat_rejects_unsupported_options_source_free(arguments: list[str]) -> None:
    result = _invoke("stat", arguments)

    assert result.exit_code == 2
    assert result.stdout == ""
    diagnostic = result.stderr
    assert "No such option" in diagnostic
    option = arguments[0].split("=", 1)[0]
    assert option.removeprefix("--") in diagnostic


@pytest.mark.parametrize(
    ("arguments", "diagnostic"),
    [
        (["/stat-x"], "/stat-x: invalid mapped filesystem operand"),
        (["local:tmp"], "local:tmp: invalid mapped filesystem operand"),
        (["unknown:/x"], "unknown:/x: unknown filesystem (known: memory)"),
    ],
)
def test_stat_rejects_operand_shapes_source_free(
    arguments: list[str],
    diagnostic: str,
) -> None:
    result = _invoke("stat", arguments)

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == f"stat: {diagnostic}\n"


def test_stat_leaves_missing_operand_to_typer() -> None:
    result = _invoke("stat", [])

    assert (result.exit_code, result.stdout) == (2, "")
    diagnostic = result.stderr
    assert "Missing argument" in diagnostic
    assert "name:/path" in diagnostic


def test_stat_accepts_option_delimiter() -> None:
    source = _RecordingSource([], info_result=_RICH_FILE)

    result = _invoke("stat", ["--", "memory:/stat-file"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stdout == _GOLDEN_FILE


def test_stat_stops_after_stdout_short_write(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_by_path={
            "/stat-a": {**_RICH_FILE, "name": "/stat-a", "size": 1},
            "/stat-b": {**_RICH_FILE, "name": "/stat-b", "size": 2},
        },
    )
    accepted: list[bytes] = []
    writes = 0

    class _Stdout:
        def write(self, chunk: bytes) -> int:
            nonlocal writes
            writes += 1
            if writes == 1:
                accepted.append(chunk)
                return len(chunk)
            return max(0, len(chunk) - 1)

        def flush(self) -> None:
            return None

    monkeypatch.setattr("fss_cli._stat._binary_stdout", _Stdout)

    result = _invoke(
        "stat",
        ["memory:/stat-a", "memory:/stat-b"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stderr == "stat: output: output failure (OSError): short write\n"
    assert accepted == [_GOLDEN_A.encode()]
    assert [event[2] for event in events if event[0] == "info"] == [
        "/stat-a",
        "/stat-b",
    ]


def test_stat_line_uses_shared_binary_writer(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    events: list[tuple[object, ...]] = []

    class _Stdout:
        def write(self, chunk: bytes) -> int:
            raise AssertionError(chunk)

        def flush(self) -> None:
            events.append(("flush",))

    stdout = _Stdout()

    def write_binary(writer: object, payload: bytes) -> None:
        events.append(("write", writer, payload))

    monkeypatch.setattr("fss_cli._stat._binary_stdout", lambda: stdout)
    monkeypatch.setattr("fss_cli._stat._write_binary", write_binary)

    _write_line(b"stat output\n")

    assert events == [
        ("write", stdout, b"stat output\n"),
        ("flush",),
    ]


def test_stat_keeps_broken_pipe_silent_but_reports_exit_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    broken_pipe = BrokenPipeError()
    source = _RecordingSource([], info_result=_RICH_FILE, exit_error=OSError("cleanup"))

    def break_stdout(line: bytes) -> None:
        del line
        raise broken_pipe

    monkeypatch.setattr("fss_cli._stat._write_line", break_stdout)
    result = _invoke("stat", ["memory:/stat-file"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "stat: memory: source exit failure (OSError): cleanup\n"
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is BrokenPipeError
    assert exception is broken_pipe
    assert traceback is not None


def test_stat_preserves_cancellation() -> None:
    control = asyncio.CancelledError()
    source = _RecordingSource([], info_error=control)

    with pytest.raises(asyncio.CancelledError) as caught:
        _invoke("stat", ["memory:/stat-file"], sources={"memory": source})

    assert type(caught.value) is asyncio.CancelledError
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is asyncio.CancelledError
    assert exception is control
    assert traceback is not None


def test_stat_preserves_backend_error_when_diagnostic_write_fails(
    monkeypatch: pytest.MonkeyPatch,
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

    result = _invoke("stat", ["memory:/file"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.exception is renderer_error
    assert result.stdout == ""
    assert result.stderr == ""
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is PermissionError
    assert exception is backend_error
    assert traceback is not None


def test_stat_mtime_format_uses_space_padded_day() -> None:
    # 2026-07-05 12:00:00 UTC
    assert _format_mtime(1783252800) == "Jul  5 12:00:00 2026"
