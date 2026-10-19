"""XSI ``unlink`` tests through the public embedded-command seam."""

from __future__ import annotations

import asyncio
from typing import TYPE_CHECKING, NoReturn

import pytest
import typer

from ._support import _invoke, _RecordingSource, _source_must_not_run

if TYPE_CHECKING:
    from collections.abc import Callable


def test_unlink_removes_one_file_without_stdout() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke("unlink", ["memory:/docs/notes.txt"], sources={"memory": source})

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


def test_unlink_passes_nonfinal_dot_and_separator_spelling_to_backend() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke(
        "unlink",
        ["memory:/docs//./notes.txt/"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert [event[2] for event in events if event[0] in {"info", "rm_file"}] == [
        "/docs//./notes.txt/",
        "/docs//./notes.txt/",
        "/docs//./notes.txt/",
    ]


def test_unlink_rejects_a_missing_mapped_filesystem_operand() -> None:
    result = _invoke("unlink", [])

    assert (result.exit_code, result.stdout) == (2, "")
    assert "Missing argument" in result.stderr
    assert "name:/path" in result.stderr


def test_unlink_rejects_extra_operands_without_entering_sources() -> None:
    result = _invoke("unlink", ["memory:/one", "memory:/two"])

    assert (result.exit_code, result.stdout) == (2, "")
    assert "unexpected extra argument" in result.stderr
    assert "memory:/two" in result.stderr


@pytest.mark.parametrize(
    "second",
    ["malformed", "other:/two", "memory:/", "memory:/."],
)
def test_unlink_reports_extra_operand_before_second_operand_validation(
    second: str,
) -> None:
    result = _invoke("unlink", ["memory:/one", second])

    assert (result.exit_code, result.stdout) == (2, "")
    assert "unexpected extra argument" in result.stderr
    assert second in result.stderr


@pytest.mark.parametrize(
    "option",
    [
        "-f",
        "-r",
        "-R",
        "-d",
        "-v",
        "-i",
        "-l",
        "--force",
        "-A",
        "-h",
        "--help=value",
        "-fr",
        "-fd",
    ],
)
def test_unlink_rejects_every_option_without_entering_sources(option: str) -> None:
    result = _invoke("unlink", [option, "memory:/file"])

    assert (result.exit_code, result.stdout) == (2, "")
    if option == "--help=value":
        assert "does not take a value" in result.stderr
    else:
        assert "No such option" in result.stderr
        context = option[2:] if option.startswith("--") else option[:2]
        assert context in result.stderr


def test_unlink_accepts_operand_after_option_terminator() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke(
        "unlink",
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


def test_unlink_treats_dashed_tokens_after_terminator_as_operands() -> None:
    result = _invoke("unlink", ["--", "-f"])

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == "unlink: -f: invalid mapped filesystem operand\n"


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
def test_unlink_rejects_root_and_final_dot_paths_before_source_entry(
    path: str,
    rendered: str,
) -> None:
    result = _invoke("unlink", [path])

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == f"unlink: {rendered}: rejected path\n"


@pytest.mark.parametrize(
    ("arguments", "rendered"),
    [
        (["memory:"], "memory:"),
        (["memory:relative"], "memory:relative"),
        (["/bare"], "/bare"),
        ([":/path"], ":/path"),
        (["memory:/bad\0path"], "memory:/bad\\x00path"),
        (["memory:/bad\npath"], "memory:/bad\\x0apath"),
    ],
)
def test_unlink_rejects_malformed_mapped_filesystem_operands(
    arguments: list[str],
    rendered: str,
) -> None:
    result = _invoke("unlink", arguments)

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == (f"unlink: {rendered}: invalid mapped filesystem operand\n")


def test_unlink_reports_unknown_names_with_locale_sorted_known_names() -> None:
    result = _invoke(
        "unlink",
        ["other:/file"],
        sources={
            "zeta": _source_must_not_run,
            "alpha": _source_must_not_run,
        },
    )

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == ("unlink: other:/file: unknown filesystem (known: alpha, zeta)\n")


@pytest.mark.parametrize("info_result", [{"type": "directory"}, {"type": "link"}, {}])
def test_unlink_rejects_non_file_types(info_result: object) -> None:
    source = _RecordingSource([], info_result=info_result)

    result = _invoke("unlink", ["memory:/docs"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    expected = (
        "unlink: memory:/docs: is a directory\n"
        if info_result == {"type": "directory"}
        else "unlink: memory:/docs: incompatible result\n"
    )
    assert result.stderr == expected
    assert [event[0] for event in source.events].count("rm_file") == 0


def test_unlink_rejects_a_missing_file() -> None:
    source = _RecordingSource([], info_error=FileNotFoundError("missing"))

    result = _invoke("unlink", ["memory:/missing"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "unlink: memory:/missing: not found\n"
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
def test_unlink_maps_pre_mutation_failures_to_locked_categories(
    error_factory: Callable[[], Exception],
    category: str,
) -> None:
    error = error_factory()
    source = _RecordingSource([], info_error=error)

    result = _invoke("unlink", ["memory:/docs/notes.txt"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == f"unlink: memory:/docs/notes.txt: {category}\n"
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
def test_unlink_reports_uncertain_mutation_after_delete_attempt(
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

    result = _invoke("unlink", ["memory:/docs/notes.txt"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == ("unlink: memory:/docs/notes.txt: uncertain mutation state\n")


def test_unlink_rejects_when_post_check_shows_the_file_still_present() -> None:
    source = _RecordingSource(
        [],
        post_info_by_path={"/docs/notes.txt": {"type": "file"}},
    )

    result = _invoke("unlink", ["memory:/docs/notes.txt"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == ("unlink: memory:/docs/notes.txt: uncertain mutation state\n")


def test_unlink_rejects_ambiguous_post_check_shapes() -> None:
    source = _RecordingSource(
        [],
        post_info_by_path={"/docs/notes.txt": {"type": "directory"}},
    )

    result = _invoke("unlink", ["memory:/docs/notes.txt"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == ("unlink: memory:/docs/notes.txt: uncertain mutation state\n")


def test_unlink_refuses_an_active_same_thread_event_loop(monkeypatch) -> None:
    real_run = asyncio.run
    recording_run = pytest.importorskip("unittest.mock").Mock(wraps=real_run)

    async def invoke() -> object:
        monkeypatch.setattr(asyncio, "run", recording_run)
        return _invoke("unlink", ["memory:/file"])

    result = real_run(invoke())

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "unlink: cannot run from an active event loop\n"
    assert recording_run.call_count == 0


class _ControlFlow(BaseException):
    pass


@pytest.mark.parametrize(
    "control",
    [asyncio.CancelledError(), _ControlFlow("stop")],
)
def test_unlink_preserves_control_flow_unchanged(control: BaseException) -> None:
    source = _RecordingSource([], rm_file_error=control)

    with pytest.raises(type(control)) as caught:
        _invoke("unlink", ["memory:/docs/notes.txt"], sources={"memory": source})

    assert type(caught.value) is type(control)
    if not isinstance(control, asyncio.CancelledError):
        assert caught.value is control
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is type(control)
    assert exception is control
    assert traceback is not None


def test_unlink_preserves_backend_error_when_its_diagnostic_write_fails(
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

    result = _invoke("unlink", ["memory:/file"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.exception is renderer_error
    assert result.stdout == ""
    assert result.stderr == ""
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is PermissionError
    assert exception is backend_error
    assert traceback is not None


def test_unlink_stops_acquisition_after_a_source_factory_failure() -> None:
    def factory_failure() -> NoReturn:
        message = "factory"
        raise ValueError(message)

    result = _invoke(
        "unlink",
        ["alpha:/one"],
        sources={"alpha": factory_failure, "beta": _source_must_not_run},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == ("unlink: alpha: source factory failure (ValueError): factory\n")


def test_unlink_reports_source_exit_failures() -> None:
    source = _RecordingSource([], exit_error=OSError("exit"))

    result = _invoke("unlink", ["memory:/file"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "unlink: memory: source exit failure (OSError): exit\n"


def test_unlink_help_comes_from_typed_callback() -> None:
    result = _invoke("unlink", ["--help"])

    assert result.exit_code == 0
    assert "Usage:" in result.stdout
    assert result.stderr == ""


def test_unlink_accepts_hidden_file_paths_that_are_not_final_dot_components() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke("unlink", ["memory:/.hidden"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == ""
    assert [event[2] for event in events if event[0] in {"info", "rm_file"}] == [
        "/.hidden",
        "/.hidden",
        "/.hidden",
    ]


def test_unlink_never_calls_rm_or_rmdir_primitives() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events, trap_rmdir=True)

    result = _invoke("unlink", ["memory:/docs/notes.txt"], sources={"memory": source})

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
