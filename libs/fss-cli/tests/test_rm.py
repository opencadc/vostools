"""Base ``rm``, ``rm -d``, and ``rm -f`` tests through the public seam."""

from __future__ import annotations

import asyncio
import errno
import re
from typing import TYPE_CHECKING, NoReturn

import pytest
import typer

from fss_cli._rm import _write_verbose_line

from ._support import _invoke, _RecordingSource, _source_must_not_run

if TYPE_CHECKING:
    from collections.abc import Callable


def test_rm_removes_one_file_without_stdout() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke("rm", ["memory:/docs/notes.txt"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == ""
    assert [(event[0], *event[2:-1]) for event in events] == [
        ("factory",),
        ("enter",),
        ("info", "/docs/notes.txt"),
        ("rm_file", "/docs/notes.txt"),
        ("info", "/docs/notes.txt"),
        ("exit",),
    ]
    assert not any(event[0] in {"rm", "rmdir", "ls"} for event in events)


def test_rm_removes_many_files_without_stdout() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke(
        "rm",
        ["memory:/docs/a.txt", "memory:/docs/b.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == ""
    assert [event[2] for event in events if event[0] in {"info", "rm_file"}] == [
        "/docs/a.txt",
        "/docs/a.txt",
        "/docs/a.txt",
        "/docs/b.txt",
        "/docs/b.txt",
        "/docs/b.txt",
    ]


def test_rm_acquires_distinct_sources_before_reusing_them() -> None:
    events: list[tuple[object, ...]] = []
    shared_source = _RecordingSource(events)

    result = _invoke(
        "rm",
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
        ("info", 1, "/one"),
        ("rm_file", 1, "/one"),
        ("info", 1, "/one"),
        ("info", 2, "/two"),
        ("rm_file", 2, "/two"),
        ("info", 2, "/two"),
        ("info", 1, "/three"),
        ("rm_file", 1, "/three"),
        ("info", 1, "/three"),
        ("exit", 2),
        ("exit", 1),
    ]


def test_rm_continues_after_an_earlier_success() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_by_path={
            "/docs/good": {"type": "file"},
            "/docs/bad": {"type": "directory"},
        },
    )

    result = _invoke(
        "rm",
        ["memory:/docs/good", "memory:/docs/bad"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rm: memory:/docs/bad: is a directory\n"
    assert [event[2] for event in events if event[0] == "rm_file"] == ["/docs/good"]


def test_rm_continues_after_an_earlier_failure() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_by_path={
            "/docs/bad": {"type": "directory"},
            "/docs/good": {"type": "file"},
        },
    )

    result = _invoke(
        "rm",
        ["memory:/docs/bad", "memory:/docs/good"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rm: memory:/docs/bad: is a directory\n"
    assert [event[2] for event in events if event[0] == "rm_file"] == ["/docs/good"]


def test_rm_processes_repeated_operands_independently() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke(
        "rm",
        ["memory:/docs/notes.txt", "memory:/docs/notes.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rm: memory:/docs/notes.txt: not found\n"
    assert [event[0] for event in events if event[0] in {"info", "rm_file"}] == [
        "info",
        "rm_file",
        "info",
        "info",
    ]


def test_rm_rejects_a_missing_mapped_filesystem_operand() -> None:
    result = _invoke("rm", [])

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == "rm: missing mapped filesystem operand\n"


def test_rm_force_without_operands_succeeds_without_source_entry() -> None:
    source_calls = 0

    def source_must_not_run() -> object:
        nonlocal source_calls
        source_calls += 1
        raise AssertionError

    result = _invoke("rm", ["-f"], sources={"memory": source_must_not_run})

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == ""
    assert source_calls == 0


def test_rm_force_ignores_missing_operands_and_removes_later_files() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_by_path={"/docs/missing.txt": FileNotFoundError("missing")},
    )

    result = _invoke(
        "rm",
        ["-f", "memory:/docs/missing.txt", "memory:/docs/notes.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == ""
    assert [event[2] for event in events if event[0] == "rm_file"] == ["/docs/notes.txt"]


def test_rm_force_succeeds_when_all_operands_are_missing() -> None:
    source = _RecordingSource(
        [],
        info_by_path={
            "/docs/first.txt": FileNotFoundError(),
            "/docs/second.txt": FileNotFoundError(),
        },
    )

    result = _invoke(
        "rm",
        ["-f", "memory:/docs/first.txt", "memory:/docs/second.txt"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert [event[0] for event in source.events].count("rm_file") == 0


@pytest.mark.parametrize(
    "arguments",
    [["-f", "-f"], ["-ff"], ["-fff"]],
)
def test_rm_force_accepts_repeated_and_grouped_flags(arguments: list[str]) -> None:
    result = _invoke("rm", arguments)

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == ""


@pytest.mark.parametrize(
    ("error_factory", "category"),
    [
        (PermissionError, "permission denied"),
        (RuntimeError, "backend failure (RuntimeError): "),
    ],
)
def test_rm_force_reports_non_missing_pre_mutation_failures(
    error_factory: Callable[[], Exception],
    category: str,
) -> None:
    source = _RecordingSource([], info_error=error_factory())

    result = _invoke("rm", ["-f", "memory:/docs/notes.txt"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == f"rm: memory:/docs/notes.txt: {category}\n"


@pytest.mark.parametrize(
    "error",
    [TimeoutError("request timed out"), RuntimeError("service timeout")],
)
def test_rm_force_reports_timeouts_before_mutation(error: Exception) -> None:
    source = _RecordingSource([], info_error=error)

    result = _invoke("rm", ["-f", "memory:/docs/notes.txt"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == (f"rm: memory:/docs/notes.txt: backend failure ({type(error).__name__}): {error}\n")
    assert [event[0] for event in source.events].count("rm_file") == 0


def test_rm_force_continues_mixed_operands_in_order() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_by_path={
            "/docs/missing.txt": FileNotFoundError("missing"),
            "/docs/failing.txt": PermissionError("denied"),
        },
    )

    result = _invoke(
        "rm",
        [
            "-f",
            "memory:/docs/missing.txt",
            "memory:/docs/existing.txt",
            "memory:/docs/failing.txt",
        ],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rm: memory:/docs/failing.txt: permission denied\n"
    assert [(event[0], event[2]) for event in events if event[0] in {"info", "rm_file"}] == [
        ("info", "/docs/missing.txt"),
        ("info", "/docs/existing.txt"),
        ("rm_file", "/docs/existing.txt"),
        ("info", "/docs/existing.txt"),
        ("info", "/docs/failing.txt"),
    ]


def test_rm_force_confirms_many_file_removals() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke(
        "rm",
        ["-f", "memory:/docs/a.txt", "memory:/docs/b.txt", "memory:/docs/c.txt"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert [(event[0], event[2]) for event in events if event[0] in {"info", "rm_file"}] == [
        ("info", "/docs/a.txt"),
        ("rm_file", "/docs/a.txt"),
        ("info", "/docs/a.txt"),
        ("info", "/docs/b.txt"),
        ("rm_file", "/docs/b.txt"),
        ("info", "/docs/b.txt"),
        ("info", "/docs/c.txt"),
        ("rm_file", "/docs/c.txt"),
        ("info", "/docs/c.txt"),
    ]


def test_rm_force_accepts_operand_after_option_terminator() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke("rm", ["-f", "--", "name:/file"], sources={"name": source})

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert [event[0] for event in events] == [
        "factory",
        "enter",
        "info",
        "rm_file",
        "info",
        "exit",
    ]


def test_rm_force_option_after_operand_is_parsed_by_typer() -> None:
    source = _RecordingSource([])

    result = _invoke("rm", ["memory:/file", "-f"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert source.call_count == 1


@pytest.mark.parametrize(
    ("info_result", "category"),
    [
        ({"type": "directory"}, "is a directory"),
        ({"type": "link"}, "incompatible result"),
    ],
)
def test_rm_force_preserves_non_file_failures(
    info_result: object,
    category: str,
) -> None:
    source = _RecordingSource([], info_result=info_result)

    result = _invoke("rm", ["-f", "memory:/docs"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == f"rm: memory:/docs: {category}\n"


def test_rm_force_preserves_uncertain_mutation_failure() -> None:
    source = _RecordingSource([], rm_file_error=PermissionError())

    result = _invoke("rm", ["-f", "memory:/docs/notes.txt"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rm: memory:/docs/notes.txt: uncertain mutation state\n"


def test_rm_force_keeps_post_mutation_not_found_uncertain() -> None:
    source = _RecordingSource([], rm_file_error=FileNotFoundError("raced removal"))

    result = _invoke("rm", ["-f", "memory:/docs/notes.txt"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rm: memory:/docs/notes.txt: uncertain mutation state\n"


def test_rm_force_uses_distinct_sources_and_skips_missing_operands() -> None:
    events: list[tuple[object, ...]] = []
    alpha = _RecordingSource(
        events,
        info_by_path={"/docs/missing.txt": FileNotFoundError("missing")},
    )
    beta = _RecordingSource(events)

    result = _invoke(
        "rm",
        ["-f", "alpha:/docs/missing.txt", "beta:/docs/notes.txt"],
        sources={"alpha": alpha, "beta": beta},
    )

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == ""
    assert alpha.call_count == beta.call_count == 1
    assert [event[2] for event in events if event[0] == "rm_file"] == ["/docs/notes.txt"]


def test_rm_force_preserves_cancellation() -> None:
    control = asyncio.CancelledError()
    source = _RecordingSource([], rm_file_error=control)

    with pytest.raises(asyncio.CancelledError) as caught:
        _invoke("rm", ["-f", "memory:/docs/notes.txt"], sources={"memory": source})

    assert type(caught.value) is asyncio.CancelledError
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is asyncio.CancelledError
    assert exception is control
    assert traceback is not None


def test_rm_force_reports_cleanup_failure() -> None:
    source = _RecordingSource([], exit_error=OSError("cleanup failed"))

    result = _invoke("rm", ["-f", "memory:/docs/notes.txt"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rm: memory: source exit failure (OSError): cleanup failed\n"


def test_rm_accepts_operand_after_option_terminator() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke(
        "rm",
        ["--", "memory:/docs/notes.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == ""
    assert [event[0] for event in events] == [
        "factory",
        "enter",
        "info",
        "rm_file",
        "info",
        "exit",
    ]


def test_rm_treats_dashed_tokens_after_terminator_as_operands() -> None:
    result = _invoke("rm", ["--", "-f"])

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == "rm: -f: invalid mapped filesystem operand\n"


@pytest.mark.parametrize(
    ("path", "rendered"),
    [
        ("memory:/", "memory:/"),
        ("memory:/.", "memory:/."),
        ("memory:/..", "memory:/.."),
        ("memory:/docs/.", "memory:/docs/."),
        ("memory:/docs/..", "memory:/docs/.."),
        ("memory:/./", "memory:/./"),
        ("memory:/docs/./", "memory:/docs/./"),
        ("memory:/docs/../", "memory:/docs/../"),
    ],
)
def test_rm_rejects_root_and_final_dot_paths_before_source_entry(
    path: str,
    rendered: str,
) -> None:
    source_calls = 0

    def source_must_not_run() -> object:
        nonlocal source_calls
        source_calls += 1
        raise AssertionError

    result = _invoke("rm", [path], sources={"memory": source_must_not_run})

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == f"rm: {rendered}: rejected path\n"
    assert source_calls == 0


@pytest.mark.parametrize(
    "arguments",
    [
        ["memory:/docs/a.txt", "memory:/"],
        ["memory:/", "memory:/docs/a.txt"],
        ["memory:/docs/a.txt", "memory:/docs/."],
        ["memory:/docs/..", "memory:/docs/a.txt"],
    ],
)
def test_rm_rejects_whole_argv_destructive_guards_before_any_factory(
    arguments: list[str],
) -> None:
    source_calls = 0

    def source_must_not_run() -> object:
        nonlocal source_calls
        source_calls += 1
        raise AssertionError

    result = _invoke("rm", arguments, sources={"memory": source_must_not_run})

    assert result.exit_code == 2
    assert result.stdout == ""
    assert "rejected path" in result.stderr
    assert source_calls == 0


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
        (["--", "-f"], "-f"),
        (["--", "--"], "--"),
    ],
)
def test_rm_rejects_malformed_mapped_filesystem_operands(
    arguments: list[str],
    rendered: str,
) -> None:
    result = _invoke("rm", arguments)

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == (f"rm: {rendered}: invalid mapped filesystem operand\n")


def test_rm_reports_unknown_names_with_locale_sorted_known_names() -> None:
    result = _invoke(
        "rm",
        ["other:/file"],
        sources={
            "zeta": _source_must_not_run,
            "alpha": _source_must_not_run,
        },
    )

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == ("rm: other:/file: unknown filesystem (known: alpha, zeta)\n")


@pytest.mark.parametrize("info_result", [{"type": "directory"}, {"type": "link"}, {}])
def test_rm_rejects_non_file_types_without_calling_rm_file(info_result: object) -> None:
    source = _RecordingSource([], info_result=info_result)

    result = _invoke("rm", ["memory:/docs"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    expected = (
        "rm: memory:/docs: is a directory\n"
        if info_result == {"type": "directory"}
        else "rm: memory:/docs: incompatible result\n"
    )
    assert result.stderr == expected
    assert [event[0] for event in source.events].count("rm_file") == 0


def test_rm_rejects_a_missing_file() -> None:
    source = _RecordingSource([], info_error=FileNotFoundError("missing"))

    result = _invoke("rm", ["memory:/missing"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rm: memory:/missing: not found\n"
    assert [event[0] for event in source.events].count("rm_file") == 0


@pytest.mark.parametrize(
    ("error_factory", "category"),
    [
        (FileNotFoundError, "not found"),
        (PermissionError, "permission denied"),
        (IsADirectoryError, "is a directory"),
        (NotImplementedError, "unsupported operation"),
        (RuntimeError, "backend failure (RuntimeError): "),
    ],
)
def test_rm_maps_pre_mutation_failures_to_locked_categories(
    error_factory: Callable[[], Exception],
    category: str,
) -> None:
    error = error_factory()
    source = _RecordingSource([], info_error=error)

    result = _invoke("rm", ["memory:/docs/notes.txt"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == f"rm: memory:/docs/notes.txt: {category}\n"
    assert [event[0] for event in source.events].count("rm_file") == 0


@pytest.mark.parametrize("stage", ["rm_file", "post_info"])
@pytest.mark.parametrize(
    "error_factory",
    [
        FileNotFoundError,
        PermissionError,
        IsADirectoryError,
        NotImplementedError,
        RuntimeError,
    ],
)
def test_rm_reports_uncertain_mutation_after_delete_attempt(
    stage: str,
    error_factory: Callable[[], Exception],
) -> None:
    if stage == "post_info" and error_factory is FileNotFoundError:
        pytest.skip("post-check FileNotFoundError confirms success")
    error = error_factory()
    source = _RecordingSource(
        [],
        rm_file_error=error if stage == "rm_file" else None,
        post_info_by_path={"/docs/notes.txt": error if stage == "post_info" else FileNotFoundError()},
    )

    result = _invoke("rm", ["memory:/docs/notes.txt"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == ("rm: memory:/docs/notes.txt: uncertain mutation state\n")


def test_rm_rejects_when_post_check_shows_the_file_still_present() -> None:
    source = _RecordingSource(
        [],
        post_info_by_path={"/docs/notes.txt": {"type": "file"}},
    )

    result = _invoke("rm", ["memory:/docs/notes.txt"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == ("rm: memory:/docs/notes.txt: uncertain mutation state\n")


def test_rm_refuses_an_active_same_thread_event_loop(monkeypatch) -> None:
    real_run = asyncio.run
    recording_run = pytest.importorskip("unittest.mock").Mock(wraps=real_run)

    async def invoke() -> object:
        monkeypatch.setattr(asyncio, "run", recording_run)
        return _invoke("rm", ["memory:/file"])

    result = real_run(invoke())

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rm: cannot run from an active event loop\n"
    assert recording_run.call_count == 0


class _ControlFlow(BaseException):
    pass


@pytest.mark.parametrize(
    "control",
    [asyncio.CancelledError(), _ControlFlow("stop")],
)
def test_rm_preserves_control_flow_unchanged(control: BaseException) -> None:
    source = _RecordingSource([], rm_file_error=control)

    with pytest.raises(type(control)) as caught:
        _invoke("rm", ["memory:/docs/notes.txt"], sources={"memory": source})

    assert type(caught.value) is type(control)
    if not isinstance(control, asyncio.CancelledError):
        assert caught.value is control
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is type(control)
    assert exception is control
    assert traceback is not None


@pytest.mark.parametrize(
    "control",
    [asyncio.CancelledError(), _ControlFlow("stop")],
)
def test_rm_preserves_earlier_removal_when_later_operand_is_cancelled(
    control: BaseException,
) -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        rm_file_by_path={"/docs/second.txt": control},
    )

    with pytest.raises(type(control)) as caught:
        _invoke(
            "rm",
            ["memory:/docs/first.txt", "memory:/docs/second.txt"],
            sources={"memory": source},
        )

    assert type(caught.value) is type(control)
    if not isinstance(control, asyncio.CancelledError):
        assert caught.value is control
    assert [event[2] for event in events if event[0] == "rm_file"] == [
        "/docs/first.txt",
        "/docs/second.txt",
    ]
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is type(control)
    assert exception is control
    assert traceback is not None


def test_rm_preserves_backend_error_when_its_diagnostic_write_fails(
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

    result = _invoke("rm", ["memory:/file"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.exception is renderer_error
    assert result.stdout == ""
    assert result.stderr == ""
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is PermissionError
    assert exception is backend_error
    assert traceback is not None


def test_rm_stops_acquisition_after_a_source_factory_failure() -> None:
    events: list[tuple[object, ...]] = []
    factory_error = ValueError("factory")
    first = _RecordingSource(events, exit_result=True)

    def broken_source() -> NoReturn:
        raise factory_error

    result = _invoke(
        "rm",
        ["first:/one", "broken:/two", "later:/three"],
        sources={
            "first": first,
            "broken": broken_source,
            "later": _source_must_not_run,
        },
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == ("rm: broken: source factory failure (ValueError): factory\n")
    assert [event[0] for event in events] == ["factory", "enter", "exit"]


def test_rm_reports_source_exit_failures_in_reverse_order() -> None:
    events: list[tuple[object, ...]] = []
    alpha = _RecordingSource(events, exit_error=OSError("alpha exit"))
    beta = _RecordingSource(events, exit_error=RuntimeError("beta exit"))

    result = _invoke(
        "rm",
        ["alpha:/one", "beta:/two"],
        sources={"alpha": alpha, "beta": beta},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == (
        "rm: beta: source exit failure (RuntimeError): beta exit\n"
        "rm: alpha: source exit failure (OSError): alpha exit\n"
    )


@pytest.mark.parametrize(
    "arguments",
    [["--help"], ["-d", "--help"], ["-f", "--help"], ["-v", "--help"]],
)
def test_rm_leaves_exact_help_to_the_framework(arguments: list[str]) -> None:
    result = _invoke("rm", arguments)

    assert result.exit_code == 0
    assert "Usage:" in result.stdout
    assert result.stderr == ""


def test_rm_help_describes_directory_profile() -> None:
    result = _invoke("rm", ["--help"])

    assert result.exit_code == 0
    plain_help = re.sub(r"\x1b\[[0-?]*[ -/]*[@-~]", "", result.stdout)
    assert "Remove files" in " ".join(plain_help.split())
    assert result.stderr == ""


def test_rm_help_describes_force_profile() -> None:
    result = _invoke("rm", ["--help"])

    assert result.exit_code == 0
    plain_help = re.sub(r"\x1b\[[0-?]*[ -/]*[@-~]", "", result.stdout)
    assert "Remove files" in " ".join(plain_help.split())
    assert result.stderr == ""


def test_rm_help_describes_verbose_profile() -> None:
    result = _invoke("rm", ["--help"])

    assert result.exit_code == 0
    plain_help = re.sub(r"\x1b\[[0-?]*[ -/]*[@-~]", "", result.stdout)
    assert "Remove files" in " ".join(plain_help.split())
    assert result.stderr == ""


def test_rm_accepts_hidden_file_paths_that_are_not_final_dot_components() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke("rm", ["memory:/.hidden"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == ""
    assert [event[2] for event in events if event[0] in {"info", "rm_file"}] == [
        "/.hidden",
        "/.hidden",
        "/.hidden",
    ]


def test_rm_passes_nonfinal_dot_and_repeated_separator_spelling_literally() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke(
        "rm",
        ["memory:/docs//./notes.txt/"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert [event[2] for event in events if event[0] in {"info", "rm_file"}] == [
        "/docs//./notes.txt/",
        "/docs//./notes.txt/",
        "/docs//./notes.txt/",
    ]


def test_rm_never_calls_rm_or_rmdir_primitives() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events, trap_rmdir=True)

    result = _invoke("rm", ["memory:/docs/notes.txt"], sources={"memory": source})

    assert result.exit_code == 0
    assert [event[0] for event in events if event[0] == "rm_file"] == ["rm_file"]
    assert not any(event[0] in {"rm", "rmdir"} for event in events)

    filesystem = source.contexts[0].filesystem

    async def prove_traps() -> None:
        with pytest.raises(AssertionError, match="_rm must not be called by file-only removal"):
            await filesystem._rm("/docs/notes.txt")
        with pytest.raises(AssertionError, match="_rmdir must not be called by file-only removal"):
            await filesystem._rmdir("/docs")

    asyncio.run(prove_traps())
    assert [event[0] for event in events if event[0] in {"rm", "rmdir"}] == [
        "rm",
        "rmdir",
    ]


def test_rm_reuses_unlink_confirmed_removal_boundary() -> None:
    from fss_cli._rm import _confirmed_rm_file as rm_confirmed
    from fss_cli._unlink import _confirmed_rm_file as unlink_confirmed

    assert rm_confirmed is unlink_confirmed


def test_rm_d_removes_mixed_files_and_empty_directories_without_stdout() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_by_path={
            "/docs/file.txt": {"type": "file"},
            "/docs/empty": {"type": "directory"},
        },
    )

    result = _invoke(
        "rm",
        ["-d", "memory:/docs/file.txt", "memory:/docs/empty"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert [(event[0], event[2]) for event in events if event[0] in {"rm_file", "rmdir"}] == [
        ("rm_file", "/docs/file.txt"),
        ("rmdir", "/docs/empty"),
    ]
    assert not any(event[0] in {"rm", "ls"} for event in events)


def test_rm_d_continues_after_non_empty_directory_failure() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_by_path={
            "/docs/not-empty": {"type": "directory"},
            "/docs/file.txt": {"type": "file"},
        },
        rmdir_by_path={"/docs/not-empty": OSError(errno.ENOTEMPTY, "directory not empty")},
    )

    result = _invoke(
        "rm",
        ["-d", "memory:/docs/not-empty", "memory:/docs/file.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rm: memory:/docs/not-empty: directory not empty\n"
    assert [event[2] for event in events if event[0] == "rm_file"] == ["/docs/file.txt"]


def test_rm_d_continues_after_an_earlier_success() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_by_path={
            "/docs/file.txt": {"type": "file"},
            "/docs/not-empty": {"type": "directory"},
        },
        rmdir_by_path={"/docs/not-empty": OSError(errno.ENOTEMPTY, "directory not empty")},
    )

    result = _invoke(
        "rm",
        ["-d", "memory:/docs/file.txt", "memory:/docs/not-empty"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rm: memory:/docs/not-empty: directory not empty\n"
    assert [event[2] for event in events if event[0] == "rm_file"] == ["/docs/file.txt"]
    assert any(event[0] == "rmdir" and event[2] == "/docs/not-empty" for event in events)


@pytest.mark.parametrize(
    ("source", "expected"),
    [
        (
            _RecordingSource([], info_error=FileNotFoundError("missing")),
            "rm: memory:/docs/item: not found\n",
        ),
        (
            _RecordingSource([], info_result={"type": "link"}),
            "rm: memory:/docs/item: incompatible result\n",
        ),
        (
            _RecordingSource([], info_result={}),
            "rm: memory:/docs/item: incompatible result\n",
        ),
    ],
)
def test_rm_d_rejects_missing_and_non_qualifying_types(
    source: _RecordingSource,
    expected: str,
) -> None:
    result = _invoke("rm", ["-d", "memory:/docs/item"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (1, "", expected)
    assert not any(event[0] in {"rm_file", "rmdir", "rm", "ls"} for event in source.events)


def test_rm_d_rejects_a_source_without_async_rmdir() -> None:
    events: list[tuple[object, ...]] = []
    recording = _RecordingSource(events, info_result={"type": "directory"})

    class _StripRmdir:
        def __call__(self) -> object:
            manager = recording()

            class _Wrapped:
                async def __aenter__(self) -> object:
                    filesystem = await manager.__aenter__()
                    filesystem._rmdir = None  # type: ignore[method-assign]
                    return filesystem

                async def __aexit__(self, *exc_info: object) -> object:
                    return await manager.__aexit__(*exc_info)

            return _Wrapped()

    result = _invoke(
        "rm",
        ["-d", "memory:/docs/empty"],
        sources={"memory": _StripRmdir()},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "rm: memory:/docs/empty: unsupported operation\n",
    )
    assert not any(event[0] in {"rm", "rm_file", "rmdir", "ls"} for event in events)


def test_rm_d_reports_uncertain_empty_directory_removal() -> None:
    source = _RecordingSource(
        [],
        info_result={"type": "directory"},
        post_info_error=PermissionError("verify denied"),
    )

    result = _invoke("rm", ["-d", "memory:/docs/empty"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "rm: memory:/docs/empty: uncertain state\n",
    )


def test_rm_d_preserves_directory_removal_cancellation() -> None:
    control = asyncio.CancelledError()
    source = _RecordingSource(
        [],
        info_result={"type": "directory"},
        rmdir_error=control,
    )

    with pytest.raises(asyncio.CancelledError) as caught:
        _invoke("rm", ["-d", "memory:/docs/empty"], sources={"memory": source})

    assert type(caught.value) is asyncio.CancelledError


@pytest.mark.parametrize(
    "arguments",
    [
        ["-df", "memory:/file"],
        ["-fd", "memory:/file"],
        ["-d", "-f", "memory:/file"],
        ["-d", "-v", "memory:/file"],
    ],
)
def test_rm_d_rejects_unsupported_option_combinations_before_source_entry(
    arguments: list[str],
) -> None:
    result = _invoke("rm", arguments)

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == "rm: -d: cannot combine with other options\n"


def test_rm_d_repeated_option_is_parsed_by_typer() -> None:
    source = _RecordingSource([], info_result={"type": "directory"})

    result = _invoke("rm", ["-dd", "memory:/file"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert source.call_count == 1


def test_rm_d_without_operands_rejects_without_source_entry() -> None:
    source_calls = 0

    def source_must_not_run() -> object:
        nonlocal source_calls
        source_calls += 1
        raise AssertionError

    result = _invoke("rm", ["-d"], sources={"memory": source_must_not_run})

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == "rm: missing mapped filesystem operand\n"
    assert source_calls == 0


def test_rm_d_uses_distinct_sources_for_files_and_empty_directories() -> None:
    events: list[tuple[object, ...]] = []
    alpha = _RecordingSource(events)
    beta = _RecordingSource(events, info_result={"type": "directory"})

    result = _invoke(
        "rm",
        ["-d", "alpha:/docs/notes.txt", "beta:/docs/empty"],
        sources={"alpha": alpha, "beta": beta},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert alpha.call_count == beta.call_count == 1
    assert [(event[0], event[2]) for event in events if event[0] in {"rm_file", "rmdir"}] == [
        ("rm_file", "/docs/notes.txt"),
        ("rmdir", "/docs/empty"),
    ]
    assert not any(event[0] in {"rm", "ls"} for event in events)


@pytest.mark.parametrize(
    ("error_factory", "category"),
    [
        (PermissionError, "permission denied"),
        (RuntimeError, "backend failure (RuntimeError): "),
    ],
)
def test_rm_d_reports_access_and_service_failures(
    error_factory: Callable[[], Exception],
    category: str,
) -> None:
    source = _RecordingSource([], info_error=error_factory())

    result = _invoke("rm", ["-d", "memory:/docs/item"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == f"rm: memory:/docs/item: {category}\n"
    assert not any(event[0] in {"rm_file", "rmdir", "rm", "ls"} for event in source.events)


def test_rm_d_reports_cleanup_failure() -> None:
    source = _RecordingSource(
        [],
        info_result={"type": "directory"},
        exit_error=OSError("cleanup failed"),
    )

    result = _invoke("rm", ["-d", "memory:/docs/empty"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rm: memory: source exit failure (OSError): cleanup failed\n"


@pytest.mark.parametrize(
    ("arguments", "rendered"),
    [
        (["-d", "memory:"], "memory:"),
        (["-d", "memory:relative"], "memory:relative"),
        (["-d", "/bare"], "/bare"),
        (["-d", ":/path"], ":/path"),
        (["-d", "-"], "-"),
        (["-d", "memory:/bad\0path"], "memory:/bad\\x00path"),
        (["-d", "memory:/bad\npath"], "memory:/bad\\x0apath"),
        (["-d", "--", "-f"], "-f"),
        (["-d", "--", "--"], "--"),
    ],
)
def test_rm_d_rejects_malformed_mapped_filesystem_operands(
    arguments: list[str],
    rendered: str,
) -> None:
    result = _invoke("rm", arguments)

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == (f"rm: {rendered}: invalid mapped filesystem operand\n")


@pytest.mark.parametrize("path", ["memory:/", "memory:/docs/.", "memory:/docs/.."])
def test_rm_d_rejects_root_and_final_dot_paths_before_source_entry(path: str) -> None:
    source_calls = 0

    def source_must_not_run() -> object:
        nonlocal source_calls
        source_calls += 1
        raise AssertionError

    result = _invoke("rm", ["-d", "memory:/file", path], sources={"memory": source_must_not_run})

    assert result.exit_code == 2
    assert result.stdout == ""
    assert "rejected path" in result.stderr
    assert source_calls == 0


def test_rm_verbose_prints_one_confirmed_removal() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke(
        "rm",
        ["-v", "memory:/docs/notes.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 0
    assert result.stdout == "memory:/docs/notes.txt\n"
    assert result.stderr == ""
    assert [event[0] for event in events if event[0] in {"info", "rm_file"}] == [
        "info",
        "rm_file",
        "info",
    ]


def test_rm_verbose_prints_many_confirmed_removals_in_order() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke(
        "rm",
        ["-v", "memory:/docs/a.txt", "memory:/docs/b.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 0
    assert result.stdout == "memory:/docs/a.txt\nmemory:/docs/b.txt\n"
    assert result.stderr == ""
    assert [event[2] for event in events if event[0] == "rm_file"] == [
        "/docs/a.txt",
        "/docs/b.txt",
    ]


def test_rm_verbose_keeps_base_rm_silent_without_flag() -> None:
    result = _invoke(
        "rm",
        ["memory:/docs/notes.txt"],
        sources={"memory": _RecordingSource([])},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")


def test_rm_verbose_prints_nothing_for_missing_directory_or_other_types() -> None:
    source = _RecordingSource(
        [],
        info_by_path={
            "/docs/missing.txt": FileNotFoundError("missing"),
            "/docs/dir": {"type": "directory"},
            "/docs/link": {"type": "link"},
        },
    )

    result = _invoke(
        "rm",
        [
            "-v",
            "memory:/docs/missing.txt",
            "memory:/docs/dir",
            "memory:/docs/link",
        ],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == (
        "rm: memory:/docs/missing.txt: not found\n"
        "rm: memory:/docs/dir: is a directory\n"
        "rm: memory:/docs/link: incompatible result\n"
    )


def test_rm_verbose_prints_nothing_for_uncertain_post_state() -> None:
    source = _RecordingSource([], rm_file_error=PermissionError())

    result = _invoke(
        "rm",
        ["-v", "memory:/docs/notes.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rm: memory:/docs/notes.txt: uncertain mutation state\n"


def test_rm_verbose_interleaves_success_output_and_diagnostics() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_by_path={
            "/docs/good": {"type": "file"},
            "/docs/bad": {"type": "directory"},
            "/docs/later": {"type": "file"},
        },
    )

    result = _invoke(
        "rm",
        [
            "-v",
            "memory:/docs/good",
            "memory:/docs/bad",
            "memory:/docs/later",
        ],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == "memory:/docs/good\nmemory:/docs/later\n"
    assert result.stderr == "rm: memory:/docs/bad: is a directory\n"
    assert [event[2] for event in events if event[0] == "rm_file"] == [
        "/docs/good",
        "/docs/later",
    ]


def test_rm_verbose_uses_distinct_sources() -> None:
    events: list[tuple[object, ...]] = []
    alpha = _RecordingSource(events)
    beta = _RecordingSource(events)

    result = _invoke(
        "rm",
        ["-v", "alpha:/one", "beta:/two"],
        sources={"alpha": alpha, "beta": beta},
    )

    assert result.exit_code == 0
    assert result.stdout == "alpha:/one\nbeta:/two\n"
    assert result.stderr == ""
    assert alpha.call_count == beta.call_count == 1


def test_rm_verbose_stops_later_mutation_after_stdout_failure(
    monkeypatch,
) -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)
    written: list[bytes] = []

    def fail_after_first(spelling: str) -> None:
        chunk = f"{spelling}\n".encode()
        written.append(chunk)
        if len(written) == 1:
            return
        message = "disk full"
        raise OSError(message)

    monkeypatch.setattr("fss_cli._rm._write_verbose_line", fail_after_first)

    result = _invoke(
        "rm",
        ["-v", "memory:/docs/a.txt", "memory:/docs/b.txt", "memory:/docs/c.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stderr == "rm: output: output failure (OSError): disk full\n"
    assert written == [b"memory:/docs/a.txt\n", b"memory:/docs/b.txt\n"]
    assert [event[2] for event in events if event[0] == "rm_file"] == [
        "/docs/a.txt",
        "/docs/b.txt",
    ]


def test_rm_verbose_reports_short_write_and_stops_later_mutation(
    monkeypatch,
) -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    class _PrefixStdout:
        def write(self, chunk: bytes) -> int:
            return max(0, len(chunk) - 1)

        def flush(self) -> None:
            return None

    monkeypatch.setattr("fss_cli._rm._binary_stdout", _PrefixStdout)

    result = _invoke(
        "rm",
        ["-v", "memory:/docs/a.txt", "memory:/docs/b.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stderr == "rm: output: output failure (OSError): short write\n"
    assert [event[2] for event in events if event[0] == "rm_file"] == ["/docs/a.txt"]


def test_rm_verbose_line_uses_shared_binary_writer(
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

    monkeypatch.setattr("fss_cli._rm._binary_stdout", lambda: stdout)
    monkeypatch.setattr("fss_cli._rm._write_binary", write_binary)

    _write_verbose_line("memory:/docs/notes.txt")

    assert events == [
        ("write", stdout, b"memory:/docs/notes.txt\n"),
        ("flush",),
    ]


def test_rm_verbose_keeps_broken_pipe_silent_but_reports_exit_failure(
    monkeypatch,
) -> None:
    broken_pipe = BrokenPipeError()
    source = _RecordingSource([], exit_error=OSError("cleanup"))

    def break_stdout(spelling: str) -> None:
        del spelling
        raise broken_pipe

    monkeypatch.setattr("fss_cli._rm._write_verbose_line", break_stdout)
    result = _invoke(
        "rm",
        ["-v", "memory:/docs/notes.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rm: memory: source exit failure (OSError): cleanup\n"
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is BrokenPipeError
    assert exception is broken_pipe
    assert traceback is not None


def test_rm_verbose_solo_broken_pipe_is_silent_status_141(
    monkeypatch,
) -> None:
    broken_pipe = BrokenPipeError()
    source = _RecordingSource([])

    def break_stdout(spelling: str) -> None:
        del spelling
        raise broken_pipe

    monkeypatch.setattr("fss_cli._rm._write_verbose_line", break_stdout)
    result = _invoke(
        "rm",
        ["-v", "memory:/docs/notes.txt"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (141, "", "")
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is BrokenPipeError
    assert exception is broken_pipe
    assert traceback is not None


def test_rm_verbose_preserves_backend_error_when_diagnostic_write_fails(
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

    result = _invoke(
        "rm",
        ["-v", "memory:/file"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.exception is renderer_error
    assert result.stdout == ""
    assert result.stderr == ""
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is PermissionError
    assert exception is backend_error
    assert traceback is not None


def test_rm_verbose_preserves_cancellation() -> None:
    control = asyncio.CancelledError()
    source = _RecordingSource([], rm_file_error=control)

    with pytest.raises(asyncio.CancelledError) as caught:
        _invoke("rm", ["-v", "memory:/docs/notes.txt"], sources={"memory": source})

    assert type(caught.value) is asyncio.CancelledError
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is asyncio.CancelledError
    assert exception is control
    assert traceback is not None


def test_rm_verbose_reports_cleanup_failure() -> None:
    source = _RecordingSource([], exit_error=OSError("cleanup failed"))

    result = _invoke(
        "rm",
        ["-v", "memory:/docs/notes.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == "memory:/docs/notes.txt\n"
    assert result.stderr == "rm: memory: source exit failure (OSError): cleanup failed\n"


@pytest.mark.parametrize(
    "arguments",
    [
        ["-v"],
        ["-v", "-v", "memory:/file"],
        ["-f", "-v", "memory:/file"],
        ["-v", "-f", "memory:/file"],
        ["-d", "-v", "memory:/file"],
        ["-v", "-d", "memory:/file"],
        ["-fv", "memory:/file"],
    ],
)
def test_rm_verbose_rejects_unsupported_shapes_before_source_entry(
    arguments: list[str],
) -> None:
    source_calls = 0

    def source_must_not_run() -> object:
        nonlocal source_calls
        source_calls += 1
        raise AssertionError

    result = _invoke("rm", arguments, sources={"memory": source_must_not_run})

    assert result.exit_code == 2
    assert result.stdout == ""
    assert source_calls == 0


def test_rm_verbose_option_after_operand_is_parsed_by_typer() -> None:
    source = _RecordingSource([])

    result = _invoke("rm", ["memory:/file", "-v"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "memory:/file\n",
        "",
    )


def test_rm_verbose_rejects_root_and_final_dot_paths_before_source_entry() -> None:
    source_calls = 0

    def source_must_not_run() -> object:
        nonlocal source_calls
        source_calls += 1
        raise AssertionError

    result = _invoke(
        "rm",
        ["-v", "memory:/."],
        sources={"memory": source_must_not_run},
    )

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == "rm: memory:/.: rejected path\n"
    assert source_calls == 0


def test_rm_verbose_rejects_malformed_mappings_with_zero_factories() -> None:
    source_calls = 0

    def source_must_not_run() -> object:
        nonlocal source_calls
        source_calls += 1
        raise AssertionError

    result = _invoke(
        "rm",
        ["-v", "not-a-mapping"],
        sources={"memory": source_must_not_run},
    )

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == "rm: not-a-mapping: invalid mapped filesystem operand\n"
    assert source_calls == 0


def test_rm_verbose_accepts_operand_after_option_terminator() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke("rm", ["-v", "--", "name:/file"], sources={"name": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "name:/file\n",
        "",
    )
    assert [event[0] for event in events] == [
        "factory",
        "enter",
        "info",
        "rm_file",
        "info",
        "exit",
    ]
