"""Base ``mkdir`` tests through the public embedded-command seam."""

import asyncio
from collections.abc import Callable
from typing import NoReturn
from unittest.mock import Mock

import pytest
import typer

from ._support import _invoke, _RecordingSource, _source_must_not_run


def test_mkdir_creates_one_directory_without_stdout() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke("mkdir", ["memory:/docs/new"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == ""
    assert [(event[0], *event[2:-1]) for event in events] == [
        ("factory",),
        ("enter",),
        ("mkdir", "/docs/new", False),
        ("info", "/docs/new"),
        ("exit",),
    ]


def test_mkdir_passes_literal_separator_and_dot_spelling_to_backend() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke(
        "mkdir",
        ["memory:/docs//./new/"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert [event[2] for event in events if event[0] in {"mkdir", "info"}] == [
        "/docs//./new/",
        "/docs//./new/",
    ]


def test_mkdir_acquires_distinct_sources_before_reusing_them() -> None:
    events: list[tuple[object, ...]] = []
    shared_source = _RecordingSource(events)

    result = _invoke(
        "mkdir",
        ["alpha:/one", "beta:/two", "alpha:/three"],
        sources={
            "beta": shared_source,
            "alpha": shared_source,
            "unused": _source_must_not_run,
        },
    )

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == ""
    assert [(event[0], *event[1:-1]) for event in events] == [
        ("factory",),
        ("enter", 1),
        ("factory",),
        ("enter", 2),
        ("mkdir", 1, "/one", False),
        ("info", 1, "/one"),
        ("mkdir", 2, "/two", False),
        ("info", 2, "/two"),
        ("mkdir", 1, "/three", False),
        ("info", 1, "/three"),
        ("exit", 2),
        ("exit", 1),
    ]


def test_mkdir_continues_after_an_earlier_success() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        mkdir_by_path={"/docs/bad": FileNotFoundError("missing parent")},
    )

    result = _invoke(
        "mkdir",
        ["memory:/docs/good", "memory:/docs/bad"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "mkdir: memory:/docs/bad: not found\n"
    assert [event[2] for event in events if event[0] == "mkdir"] == [
        "/docs/good",
        "/docs/bad",
    ]


def test_mkdir_continues_after_an_earlier_failure() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        mkdir_by_path={"/docs/bad": FileNotFoundError("missing parent")},
    )

    result = _invoke(
        "mkdir",
        ["memory:/docs/bad", "memory:/docs/good"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "mkdir: memory:/docs/bad: not found\n"
    assert [event[2] for event in events if event[0] == "mkdir"] == [
        "/docs/bad",
        "/docs/good",
    ]
    assert ("info", 1, "/docs/good") in [(event[0], event[1], event[2]) for event in events if event[0] == "info"]


def test_mkdir_rejects_root_operand_when_it_already_exists() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        mkdir_by_path={"/": FileExistsError("/")},
    )

    result = _invoke("mkdir", ["memory:/"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "mkdir: memory:/: file exists\n"
    assert sum(1 for event in events if event[0] == "mkdir") == 1
    assert not any(event[0] == "info" for event in events)


def test_mkdir_rejects_duplicate_operands_on_the_second_attempt() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke(
        "mkdir",
        ["memory:/docs/new", "memory:/docs/new"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "mkdir: memory:/docs/new: file exists\n"
    assert [event[0] for event in events].count("mkdir") == 2


def test_mkdir_asserts_create_parents_false_at_the_operation_boundary() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke("mkdir", ["memory:/docs/new"], sources={"memory": source})

    assert result.exit_code == 0
    mkdir_events = [event for event in events if event[0] == "mkdir"]
    assert len(mkdir_events) == 1
    assert mkdir_events[0][3] is False


@pytest.mark.parametrize(
    ("post_info", "category"),
    [
        ({"type": "file"}, "uncertain state (incompatible result)"),
        ({"type": "link"}, "uncertain state (incompatible result)"),
        (None, "uncertain state (incompatible result)"),
        ({"name": "/docs/new"}, "uncertain state (incompatible result)"),
    ],
)
def test_mkdir_rejects_malformed_post_verify_state(
    post_info: object,
    category: str,
) -> None:
    source = _RecordingSource([], post_info_result=post_info)

    result = _invoke("mkdir", ["memory:/docs/new"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == f"mkdir: memory:/docs/new: {category}\n"


def test_mkdir_rejects_missing_post_verify_result() -> None:
    source = _RecordingSource([], post_info_result=None)

    result = _invoke("mkdir", ["memory:/docs/new"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == ("mkdir: memory:/docs/new: uncertain state (incompatible result)\n")


@pytest.mark.parametrize(
    ("error_factory", "category"),
    [
        (FileNotFoundError, "not found"),
        (FileExistsError, "file exists"),
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
def test_mkdir_maps_confirmed_mkdir_failures_to_locked_categories(
    error_factory: Callable[[], Exception],
    category: str,
) -> None:
    error = error_factory()
    source = _RecordingSource([], mkdir_error=error)

    result = _invoke("mkdir", ["memory:/docs/new"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == f"mkdir: memory:/docs/new: {category}\n"


@pytest.mark.parametrize(
    ("error_factory", "category"),
    [
        (FileNotFoundError, "uncertain state (not found)"),
        (FileExistsError, "uncertain state (file exists)"),
        (PermissionError, "uncertain state (permission denied)"),
        (NotADirectoryError, "uncertain state (not a directory)"),
        (NotImplementedError, "uncertain state (unsupported operation)"),
        (RuntimeError, "uncertain state (backend failure (RuntimeError): )"),
        (
            lambda: RuntimeError("backend\\\0\r\n"),
            "uncertain state (backend failure (RuntimeError): backend\\\\\\x00\\x0d\\x0a)",
        ),
    ],
)
def test_mkdir_maps_post_success_verify_failures_to_uncertain_state(
    error_factory: Callable[[], Exception],
    category: str,
) -> None:
    error = error_factory()
    source = _RecordingSource(
        [],
        post_info_by_path={"/docs/new": error},
    )

    result = _invoke("mkdir", ["memory:/docs/new"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == f"mkdir: memory:/docs/new: {category}\n"


def test_mkdir_help_discloses_source_default_mode_divergence() -> None:
    result = _invoke("mkdir", ["--help"])

    assert result.exit_code == 0
    assert "Create directories" in result.stdout
    assert result.stderr == ""


def test_mkdir_rejects_a_missing_mapped_filesystem_operand() -> None:
    result = _invoke("mkdir", [])

    assert (result.exit_code, result.stdout) == (2, "")
    assert "Missing argument" in result.stderr
    assert "name:/path" in result.stderr


@pytest.mark.parametrize(
    ("option", "context"),
    [
        ("-m", "-m"),
        ("-pm", "-m"),
        ("--parents", "parents"),
        ("--mode", "mode"),
        ("-h", "-h"),
        ("--help=value", "does not take a value"),
    ],
)
def test_mkdir_rejects_unsupported_options_without_entering_sources(
    option: str,
    context: str,
) -> None:
    result = _invoke("mkdir", [option, "memory:/docs/new"])

    assert (result.exit_code, result.stdout) == (2, "")
    assert context in result.stderr


@pytest.mark.parametrize(
    ("arguments", "rendered"),
    [
        (["memory:"], "memory:"),
        (["memory:relative"], "memory:relative"),
        (["/bare"], "/bare"),
        ([":/path"], ":/path"),
        (["-"], "-"),
        (["memory:/bad\0path"], "memory:/bad\\x00path"),
        (["memory:/bad\npath"], "memory:/bad\\x0apath"),
        (["--", "-p"], "-p"),
        (["--", "--"], "--"),
    ],
)
def test_mkdir_rejects_malformed_mapped_filesystem_operands(
    arguments: list[str],
    rendered: str,
) -> None:
    result = _invoke("mkdir", arguments)

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == (f"mkdir: {rendered}: invalid mapped filesystem operand\n")


def test_mkdir_reports_unknown_names_with_locale_sorted_known_names() -> None:
    result = _invoke(
        "mkdir",
        ["other:/docs/new"],
        sources={
            "zeta": _source_must_not_run,
            "alpha": _source_must_not_run,
        },
    )

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == ("mkdir: other:/docs/new: unknown filesystem (known: alpha, zeta)\n")


def test_mkdir_refuses_an_active_same_thread_event_loop(monkeypatch) -> None:
    real_run = asyncio.run
    recording_run = Mock(wraps=real_run)

    async def invoke() -> object:
        monkeypatch.setattr(asyncio, "run", recording_run)
        return _invoke("mkdir", ["memory:/docs/new"])

    result = real_run(invoke())

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "mkdir: cannot run from an active event loop\n"
    assert recording_run.call_count == 0


class _ControlFlow(BaseException):
    pass


@pytest.mark.parametrize(
    "control",
    [asyncio.CancelledError(), _ControlFlow("stop")],
)
def test_mkdir_preserves_control_flow_unchanged(control: BaseException) -> None:
    source = _RecordingSource([], mkdir_error=control)

    with pytest.raises(type(control)) as caught:
        _invoke("mkdir", ["memory:/docs/new"], sources={"memory": source})

    assert type(caught.value) is type(control)
    if not isinstance(control, asyncio.CancelledError):
        assert caught.value is control
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is type(control)
    assert exception is control
    assert traceback is not None


def test_mkdir_preserves_backend_error_when_its_diagnostic_write_fails(
    monkeypatch,
) -> None:
    backend_error = PermissionError("denied")
    renderer_error = RuntimeError("stderr failed")
    source = _RecordingSource([], mkdir_error=backend_error)

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

    result = _invoke("mkdir", ["memory:/docs/new"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.exception is renderer_error
    assert result.stdout == ""
    assert result.stderr == ""
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is PermissionError
    assert exception is backend_error
    assert traceback is not None


def test_mkdir_keeps_diagnostic_control_flow_active_during_cleanup(
    monkeypatch,
) -> None:
    backend_error = PermissionError("denied")
    control = _ControlFlow("stderr stopped")
    source = _RecordingSource([], mkdir_error=backend_error)

    def fail_diagnostic(
        _message: object = None,
        *args: object,
        **kwargs: object,
    ) -> NoReturn:
        del args, kwargs
        raise control

    monkeypatch.setattr(typer, "echo", fail_diagnostic)

    with pytest.raises(_ControlFlow) as caught:
        _invoke("mkdir", ["memory:/docs/new"], sources={"memory": source})

    assert caught.value is control
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is _ControlFlow
    assert exception is control
    assert traceback is not None


def test_mkdir_stops_acquisition_after_a_source_factory_failure() -> None:
    events: list[tuple[object, ...]] = []
    factory_error = ValueError("factory")
    first = _RecordingSource(events, exit_result=True)

    def broken_source() -> NoReturn:
        raise factory_error

    result = _invoke(
        "mkdir",
        ["first:/one", "broken:/two", "later:/three"],
        sources={
            "first": first,
            "broken": broken_source,
            "later": _source_must_not_run,
        },
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == ("mkdir: broken: source factory failure (ValueError): factory\n")
    assert [event[0] for event in events] == ["factory", "enter", "exit"]


def test_mkdir_reports_source_exit_failures_in_reverse_order() -> None:
    events: list[tuple[object, ...]] = []
    alpha = _RecordingSource(events, exit_error=OSError("alpha exit"))
    beta = _RecordingSource(events, exit_error=RuntimeError("beta exit"))

    result = _invoke(
        "mkdir",
        ["alpha:/one", "beta:/two"],
        sources={"alpha": alpha, "beta": beta},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == (
        "mkdir: beta: source exit failure (RuntimeError): beta exit\n"
        "mkdir: alpha: source exit failure (OSError): alpha exit\n"
    )


def test_mkdir_p_delegates_one_makedirs_call_for_deep_parents() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke("mkdir", ["-p", "memory:/a/b/c/new"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == ""
    assert [event[0] for event in events if event[0] in {"makedirs", "mkdir"}] == ["makedirs"]
    assert events[[event[0] for event in events].index("makedirs")][3] is True
    assert ("info", 1, "/a/b/c/new") in [(event[0], event[1], event[2]) for event in events if event[0] == "info"]


def test_mkdir_p_treats_existing_directory_as_success() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    first = _invoke("mkdir", ["-p", "memory:/docs/existing"], sources={"memory": source})
    second = _invoke("mkdir", ["-p", "memory:/docs/existing"], sources={"memory": source})

    assert first.exit_code == 0
    assert second.exit_code == 0
    makedirs_events = [event for event in events if event[0] == "makedirs"]
    assert len(makedirs_events) == 2
    assert all(event[3] is True for event in makedirs_events)


def test_mkdir_p_rejects_existing_leaf_file() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        makedirs_by_path={"/docs/notes.txt": FileExistsError("/docs/notes.txt")},
    )

    result = _invoke("mkdir", ["-p", "memory:/docs/notes.txt"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "mkdir: memory:/docs/notes.txt: file exists\n"
    assert not any(event[0] == "info" for event in events)


def test_mkdir_p_rejects_intermediate_file_parent() -> None:
    source = _RecordingSource(
        [],
        makedirs_by_path={
            "/docs/notes.txt/child": NotADirectoryError("/docs/notes.txt"),
        },
    )

    result = _invoke(
        "mkdir",
        ["-p", "memory:/docs/notes.txt/child"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == ("mkdir: memory:/docs/notes.txt/child: not a directory\n")


def test_mkdir_p_succeeds_when_root_already_exists() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke("mkdir", ["-p", "memory:/"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == ""
    assert [event[0] for event in events if event[0] == "makedirs"] == ["makedirs"]


@pytest.mark.parametrize(
    "arguments",
    [
        ["-p", "memory:/docs/new"],
        ["-pp", "memory:/docs/new"],
        ["-p", "-p", "memory:/docs/new"],
    ],
)
def test_mkdir_p_accepts_grouped_and_repeated_parent_options(
    arguments: list[str],
) -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke("mkdir", arguments, sources={"memory": source})

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == ""
    assert any(event[0] == "makedirs" for event in events)


def test_mkdir_p_accepts_parent_option_after_first_operand() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke(
        "mkdir",
        ["memory:/docs/a", "-p", "memory:/docs/b"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert [event[0] for event in events].count("makedirs") == 2


def test_mkdir_p_acquires_distinct_sources_before_reusing_them() -> None:
    events: list[tuple[object, ...]] = []
    shared_source = _RecordingSource(events)

    result = _invoke(
        "mkdir",
        ["-p", "alpha:/one", "beta:/two", "alpha:/three"],
        sources={
            "beta": shared_source,
            "alpha": shared_source,
            "unused": _source_must_not_run,
        },
    )

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == ""
    assert [(event[0], *event[1:-1]) for event in events] == [
        ("factory",),
        ("enter", 1),
        ("factory",),
        ("enter", 2),
        ("makedirs", 1, "/one", True),
        ("info", 1, "/one"),
        ("makedirs", 2, "/two", True),
        ("info", 2, "/two"),
        ("makedirs", 1, "/three", True),
        ("info", 1, "/three"),
        ("exit", 2),
        ("exit", 1),
    ]


def test_mkdir_p_treats_repeated_operands_as_idempotent_success() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke(
        "mkdir",
        ["-p", "memory:/docs/new", "memory:/docs/new"],
        sources={"memory": source},
    )

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == ""
    assert [event[0] for event in events].count("makedirs") == 2


@pytest.mark.parametrize(
    "control",
    [asyncio.CancelledError(), _ControlFlow("stop")],
)
def test_mkdir_p_preserves_control_flow_unchanged(control: BaseException) -> None:
    source = _RecordingSource([], makedirs_error=control)

    with pytest.raises(type(control)) as caught:
        _invoke("mkdir", ["-p", "memory:/docs/new"], sources={"memory": source})

    assert type(caught.value) is type(control)
    if not isinstance(control, asyncio.CancelledError):
        assert caught.value is control
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is type(control)
    assert exception is control
    assert traceback is not None


def test_mkdir_p_reports_source_exit_failures_in_reverse_order() -> None:
    events: list[tuple[object, ...]] = []
    alpha = _RecordingSource(events, exit_error=OSError("alpha exit"))
    beta = _RecordingSource(events, exit_error=RuntimeError("beta exit"))

    result = _invoke(
        "mkdir",
        ["-p", "alpha:/one", "beta:/two"],
        sources={"alpha": alpha, "beta": beta},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == (
        "mkdir: beta: source exit failure (RuntimeError): beta exit\n"
        "mkdir: alpha: source exit failure (OSError): alpha exit\n"
    )


def test_mkdir_p_continues_after_partial_success() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        makedirs_by_path={"/docs/bad": PermissionError("denied")},
    )

    result = _invoke(
        "mkdir",
        ["-p", "memory:/docs/good", "memory:/docs/bad", "memory:/docs/also-good"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "mkdir: memory:/docs/bad: permission denied\n"
    assert [event[2] for event in events if event[0] == "makedirs"] == [
        "/docs/good",
        "/docs/bad",
        "/docs/also-good",
    ]


@pytest.mark.parametrize(
    ("post_info", "category"),
    [
        ({"type": "file"}, "uncertain state (incompatible result)"),
        ({"type": "link"}, "uncertain state (incompatible result)"),
        (None, "uncertain state (incompatible result)"),
        ({"name": "/docs/new"}, "uncertain state (incompatible result)"),
    ],
)
def test_mkdir_p_rejects_malformed_post_verify_state(
    post_info: object,
    category: str,
) -> None:
    source = _RecordingSource([], post_info_result=post_info)

    result = _invoke("mkdir", ["-p", "memory:/docs/new"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == f"mkdir: memory:/docs/new: {category}\n"


@pytest.mark.parametrize(
    ("error_factory", "category"),
    [
        (FileNotFoundError, "not found"),
        (FileExistsError, "file exists"),
        (PermissionError, "permission denied"),
        (NotADirectoryError, "not a directory"),
        (NotImplementedError, "unsupported operation"),
        (RuntimeError, "backend failure (RuntimeError): "),
    ],
)
def test_mkdir_p_maps_confirmed_makedirs_failures_to_locked_categories(
    error_factory: Callable[[], Exception],
    category: str,
) -> None:
    error = error_factory()
    source = _RecordingSource([], makedirs_error=error)

    result = _invoke("mkdir", ["-p", "memory:/docs/new"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == f"mkdir: memory:/docs/new: {category}\n"


def test_mkdir_p_asserts_exist_ok_true_at_the_operation_boundary() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke("mkdir", ["-p", "memory:/docs/new"], sources={"memory": source})

    assert result.exit_code == 0
    makedirs_events = [event for event in events if event[0] == "makedirs"]
    assert len(makedirs_events) == 1
    assert makedirs_events[0][3] is True


def test_mkdir_p_help_discloses_source_default_mode_divergence() -> None:
    result = _invoke("mkdir", ["--help"])

    assert result.exit_code == 0
    assert "Create directories" in result.stdout
    assert result.stderr == ""


def test_mkdir_without_p_still_rejects_missing_parent() -> None:
    source = _RecordingSource(
        [],
        mkdir_by_path={"/docs/absent/child": FileNotFoundError("missing parent")},
    )

    result = _invoke("mkdir", ["memory:/docs/absent/child"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stderr == "mkdir: memory:/docs/absent/child: not found\n"
    assert not any(event[0] == "makedirs" for event in source.events)
