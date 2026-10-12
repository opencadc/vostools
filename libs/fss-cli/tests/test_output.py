"""Plain ``ls`` rendering tests through the public embedded-command seam."""

import locale

import pytest
import typer
from typer.testing import CliRunner

from fss_cli import App

from ._support import _invoke, _RecordingSource


def test_ls_processes_operands_in_order_then_renders_sorted_output_blocks() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_by_path={
            "/z-dir": {"type": "directory"},
            "/b.txt": {"type": "file"},
            "/a.txt": {"type": "file"},
            "/empty": {"type": "directory"},
        },
        ls_by_path={
            "/z-dir": ["/z-dir/second", "/z-dir/first"],
            "/empty": [],
        },
    )

    result = _invoke(
        "ls",
        [
            "memory:/z-dir",
            "memory:/b.txt",
            "memory:/a.txt",
            "memory:/empty",
        ],
        sources={"memory": source},
    )

    assert result.exit_code == 0
    assert result.stdout == ("memory:/a.txt\nmemory:/b.txt\n\nmemory:/empty:\n\nmemory:/z-dir:\nfirst\nsecond\n")
    assert result.stderr == ""
    assert [(event[0], event[2]) for event in events if event[0] in {"info", "ls"}] == [
        ("info", "/z-dir"),
        ("info", "/b.txt"),
        ("info", "/a.txt"),
        ("info", "/empty"),
        ("ls", "/z-dir"),
        ("ls", "/empty"),
    ]


def test_ls_keeps_duplicates_and_successes_while_reporting_every_failure(
    monkeypatch,
) -> None:
    collation_keys = {
        "alpha:/a-file": "file",
        "beta:/z-file": "file",
        "beta:/ok-dir": "directory",
        "z": "child",
        "a": "child",
    }
    monkeypatch.setattr(locale, "strxfrm", collation_keys.__getitem__)
    events: list[tuple[object, ...]] = []
    alpha = _RecordingSource(
        events,
        info_by_path={
            "/missing": FileNotFoundError(),
            "/a-file": {"type": "file"},
            "/denied": PermissionError(),
        },
    )
    beta = _RecordingSource(
        events,
        info_by_path={
            "/z-file": {"type": "file"},
            "/bad-dir": {"type": "directory"},
            "/ok-dir": {"type": "directory"},
        },
        ls_by_path={
            "/bad-dir": ["/bad-dir/accepted", "/bad-dir/nested/rejected"],
            "/ok-dir": ["/ok-dir/z", "/ok-dir/a"],
        },
    )

    result = _invoke(
        "ls",
        [
            "beta:/z-file",
            "alpha:/missing",
            "beta:/bad-dir",
            "alpha:/a-file",
            "beta:/z-file",
            "alpha:/denied",
            "beta:/ok-dir",
        ],
        sources={"alpha": alpha, "beta": beta},
    )

    assert result.exit_code == 1
    assert result.stderr == (
        "ls: alpha:/missing: not found\nls: beta:/bad-dir: incompatible result\nls: alpha:/denied: permission denied\n"
    )
    assert result.stdout == ("alpha:/a-file\nbeta:/z-file\nbeta:/z-file\n\nbeta:/ok-dir:\na\nz\n")
    assert [(event[0], event[2]) for event in events if event[0] in {"info", "ls"}] == [
        ("info", "/z-file"),
        ("info", "/missing"),
        ("info", "/bad-dir"),
        ("info", "/a-file"),
        ("info", "/z-file"),
        ("info", "/denied"),
        ("info", "/ok-dir"),
        ("ls", "/bad-dir"),
        ("ls", "/ok-dir"),
    ]


def test_ls_writes_verbatim_bytes_for_tty_and_redirected_execution() -> None:
    operand = "memory:/\x1b[31mred\x1b[0m"
    source = _RecordingSource([])
    app = App({"memory": source}).typer_app
    runner = CliRunner()

    redirected = runner.invoke(app, ["ls", operand], color=False)
    tty = runner.invoke(app, ["ls", operand], color=True)

    assert redirected.exit_code == tty.exit_code == 0
    assert redirected.stdout == tty.stdout == f"{operand}\n"
    assert redirected.stderr == tty.stderr == ""


def test_ls_cleans_up_after_an_output_failure_and_reports_exit_failure(
    monkeypatch,
) -> None:
    output_error = RuntimeError("disk\\\0\r\n")
    source = _RecordingSource([], exit_error=OSError("cleanup"))
    real_echo = typer.echo

    def fail_stdout(
        message: object = None,
        *args: object,
        **kwargs: object,
    ) -> None:
        if kwargs.get("err") is True:
            real_echo(message, *args, **kwargs)
            return
        raise output_error

    monkeypatch.setattr(typer, "echo", fail_stdout)
    result = _invoke("ls", ["memory:/file"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == (
        "ls: output: output failure (RuntimeError): disk\\\\\\x00\\x0d\\x0a\n"
        "ls: memory: source exit failure (OSError): cleanup\n"
    )
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is RuntimeError
    assert exception is output_error
    assert traceback is not None


def test_ls_keeps_broken_pipe_silent_but_reports_an_exit_failure(
    monkeypatch,
) -> None:
    broken_pipe = BrokenPipeError()
    source = _RecordingSource([], exit_error=OSError("cleanup"))
    real_echo = typer.echo

    def break_stdout(
        message: object = None,
        *args: object,
        **kwargs: object,
    ) -> None:
        if kwargs.get("err") is True:
            real_echo(message, *args, **kwargs)
            return
        raise broken_pipe

    monkeypatch.setattr(typer, "echo", break_stdout)
    result = _invoke("ls", ["memory:/file"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == ("ls: memory: source exit failure (OSError): cleanup\n")
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is BrokenPipeError
    assert exception is broken_pipe
    assert traceback is not None


class _OutputControl(BaseException):
    pass


def test_ls_cleans_up_then_propagates_stdout_control_flow(
    monkeypatch,
) -> None:
    control = _OutputControl("stop")
    source = _RecordingSource([], exit_error=OSError("cleanup"))
    diagnostics: list[object] = []

    def control_stdout(
        message: object = None,
        *args: object,
        **kwargs: object,
    ) -> None:
        del args
        if kwargs.get("err") is True:
            diagnostics.append(message)
            return
        raise control

    monkeypatch.setattr(typer, "echo", control_stdout)
    with pytest.raises(_OutputControl) as caught:
        _invoke("ls", ["memory:/file"], sources={"memory": source})

    assert caught.value is control
    assert diagnostics == [
        "ls: memory: source exit failure (OSError): cleanup",
    ]
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is _OutputControl
    assert exception is control
    assert traceback is not None


def test_ls_escapes_ansi_in_a_preflight_diagnostic() -> None:
    operand = "\x1b[31mbad\x1b[0m"
    escaped = "\\x1b[31mbad\\x1b[0m"

    result = _invoke("ls", [operand])

    assert result.exit_code == 2
    assert result.stderr == (f"ls: {escaped}: invalid mapped filesystem operand\n")


def test_ls_escapes_ansi_in_a_backend_diagnostic() -> None:
    operand = "memory:/\x1b[31mfile\x1b[0m"
    message = "\x1b[32mfailed\x1b[0m"
    escaped_operand = "memory:/\\x1b[31mfile\\x1b[0m"
    escaped_message = "\\x1b[32mfailed\\x1b[0m"
    source = _RecordingSource([], info_error=RuntimeError(message))

    result = _invoke("ls", [operand], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stderr == (f"ls: {escaped_operand}: backend failure (RuntimeError): {escaped_message}\n")


def test_ls_escapes_ansi_in_a_source_diagnostic() -> None:
    name = "\x1b[31mmemory\x1b[0m"
    message = "\x1b[32mfailed\x1b[0m"
    escaped_name = "\\x1b[31mmemory\\x1b[0m"
    escaped_message = "\\x1b[32mfailed\\x1b[0m"

    def broken_source() -> None:
        raise RuntimeError(message)

    result = _invoke("ls", [f"{name}:/file"], sources={name: broken_source})

    assert result.exit_code == 1
    assert result.stderr == (f"ls: {escaped_name}: source factory failure (RuntimeError): {escaped_message}\n")


def test_ls_sorts_repeated_directory_blocks_with_raw_string_ties(
    monkeypatch,
) -> None:
    collation_keys = {
        "memory:/z": "same",
        "memory:/a": "same",
        "second": "same",
        "first": "same",
    }
    monkeypatch.setattr(locale, "strxfrm", collation_keys.__getitem__)

    def reject_locale_change(*_args: object) -> None:
        raise AssertionError

    monkeypatch.setattr(locale, "setlocale", reject_locale_change)
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_by_path={
            "/z": {"type": "directory"},
            "/a": {"type": "directory"},
        },
        ls_by_path={
            "/z": ["/z/second", "/z/first"],
            "/a": [],
        },
    )

    result = _invoke(
        "ls",
        ["memory:/z", "memory:/a", "memory:/z"],
        sources={"memory": source},
    )

    assert result.exit_code == 0
    assert result.stdout == ("memory:/a:\n\nmemory:/z:\nfirst\nsecond\n\nmemory:/z:\nfirst\nsecond\n")
    assert [(event[0], event[2]) for event in events if event[0] in {"info", "ls"}] == [
        ("info", "/z"),
        ("info", "/a"),
        ("info", "/z"),
        ("ls", "/z"),
        ("ls", "/a"),
        ("ls", "/z"),
    ]


def test_ls_finishes_presentation_before_writing_known_diagnostics(
    monkeypatch,
) -> None:
    backend_error = FileNotFoundError("missing")
    collation_error = ValueError("collation failed")

    def fail_collation(_value: str) -> str:
        raise collation_error

    monkeypatch.setattr(locale, "strxfrm", fail_collation)
    source = _RecordingSource(
        [],
        info_by_path={
            "/missing": backend_error,
            "/file": {"type": "file"},
        },
    )

    result = _invoke(
        "ls",
        ["memory:/missing", "memory:/file"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.exception is collation_error
    assert result.stdout == ""
    assert result.stderr == ""
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is FileNotFoundError
    assert exception is backend_error
    assert traceback is not None


def test_ls_passes_first_backend_error_to_cleanup_before_output_error(
    monkeypatch,
) -> None:
    backend_error = FileNotFoundError("missing")
    output_error = OSError("write failed")
    source = _RecordingSource(
        [],
        info_by_path={
            "/missing": backend_error,
            "/file": {"type": "file"},
        },
    )
    real_echo = typer.echo

    def fail_stdout(
        message: object = None,
        *args: object,
        **kwargs: object,
    ) -> None:
        if kwargs.get("err") is True:
            real_echo(message, *args, **kwargs)
            return
        raise output_error

    monkeypatch.setattr(typer, "echo", fail_stdout)
    result = _invoke(
        "ls",
        ["memory:/missing", "memory:/file"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stderr == ("ls: memory:/missing: not found\nls: output: output failure (OSError): write failed\n")
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is FileNotFoundError
    assert exception is backend_error
    assert traceback is not None


def test_ls_writes_no_stdout_when_every_operand_fails() -> None:
    source = _RecordingSource(
        [],
        info_by_path={
            "/missing": FileNotFoundError(),
            "/denied": PermissionError(),
        },
    )

    result = _invoke(
        "ls",
        ["memory:/missing", "memory:/denied"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == ("ls: memory:/missing: not found\nls: memory:/denied: permission denied\n")


def test_ls_preserves_successful_rendering_below_a_parent_typer_app() -> None:
    source = _RecordingSource(
        [],
        info_by_path={
            "/z-dir": {"type": "directory"},
            "/a-file": {"type": "file"},
        },
        ls_by_path={
            "/z-dir": ["/z-dir/z.txt", "/z-dir/a.txt"],
        },
    )
    parent = typer.Typer(add_completion=False)
    parent.add_typer(App({"memory": source}).typer_app, name="data")

    result = CliRunner().invoke(
        parent,
        ["data", "ls", "memory:/z-dir", "memory:/a-file"],
    )

    assert result.exit_code == 0
    assert result.stdout == ("memory:/a-file\n\nmemory:/z-dir:\na.txt\nz.txt\n")
    assert result.stderr == ""


def test_ls_orders_backend_output_and_reverse_cleanup_diagnostics(
    monkeypatch,
) -> None:
    backend_error = FileNotFoundError("missing")
    output_error = OSError("write failed")
    events: list[tuple[object, ...]] = []
    alpha = _RecordingSource(
        events,
        info_by_path={"/good": {"type": "file"}},
        exit_error=OSError("alpha close"),
    )
    beta = _RecordingSource(
        events,
        info_by_path={"/missing": backend_error},
        exit_error=RuntimeError("beta close"),
    )
    real_echo = typer.echo

    def accept_prefix_then_fail(
        message: object = None,
        *args: object,
        **kwargs: object,
    ) -> None:
        if kwargs.get("err") is True:
            real_echo(message, *args, **kwargs)
            return
        real_echo("alpha:", nl=False, color=True)
        raise output_error

    monkeypatch.setattr(typer, "echo", accept_prefix_then_fail)
    result = _invoke(
        "ls",
        ["alpha:/good", "beta:/missing"],
        sources={"alpha": alpha, "beta": beta},
    )

    assert result.exit_code == 1
    assert result.stdout == "alpha:"
    assert result.stderr == (
        "ls: beta:/missing: not found\n"
        "ls: output: output failure (OSError): write failed\n"
        "ls: beta: source exit failure (RuntimeError): beta close\n"
        "ls: alpha: source exit failure (OSError): alpha close\n"
    )
    assert [event[0] for event in events][-2:] == ["exit", "exit"]
    for source in (alpha, beta):
        exception_type, exception, traceback = source.exit_calls[0]
        assert exception_type is FileNotFoundError
        assert exception is backend_error
        assert traceback is not None


def test_ls_keeps_an_empty_directory_block_when_another_operand_fails() -> None:
    source = _RecordingSource(
        [],
        info_by_path={
            "/missing": FileNotFoundError(),
            "/empty": {"type": "directory"},
        },
        ls_by_path={"/empty": []},
    )

    result = _invoke(
        "ls",
        ["memory:/missing", "memory:/empty"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == "memory:/empty:\n"
    assert result.stderr == "ls: memory:/missing: not found\n"
