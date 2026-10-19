"""Base ``rmdir`` tests through the public embedded-command seam."""

import asyncio
import errno
from collections.abc import Callable
from typing import NoReturn

import pytest
import typer

from ._support import _invoke, _RecordingSource, _source_must_not_run


def test_rmdir_removes_one_empty_directory_without_stdout() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events, info_result={"type": "directory"})

    result = _invoke("rmdir", ["memory:/docs/empty"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == ""
    assert [(event[0], *event[2:-1]) for event in events] == [
        ("factory",),
        ("enter",),
        ("info", "/docs/empty"),
        ("rmdir", "/docs/empty"),
        ("info", "/docs/empty"),
        ("exit",),
    ]
    assert [event[0] for event in events].count("ls") == 0


def test_rmdir_passes_nonfinal_dot_and_separator_spelling_to_backend() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events, info_result={"type": "directory"})

    result = _invoke(
        "rmdir",
        ["memory:/docs//./empty/"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert [event[2] for event in events if event[0] in {"info", "rmdir"}] == [
        "/docs//./empty/",
        "/docs//./empty/",
        "/docs//./empty/",
    ]


def test_rmdir_acquires_distinct_sources_before_reusing_them() -> None:
    events: list[tuple[object, ...]] = []
    shared_source = _RecordingSource(events, info_result={"type": "directory"})

    result = _invoke(
        "rmdir",
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
        ("rmdir", 1, "/one"),
        ("info", 1, "/one"),
        ("info", 2, "/two"),
        ("rmdir", 2, "/two"),
        ("info", 2, "/two"),
        ("info", 1, "/three"),
        ("rmdir", 1, "/three"),
        ("info", 1, "/three"),
        ("exit", 2),
        ("exit", 1),
    ]


def test_rmdir_continues_after_an_earlier_success() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_result={"type": "directory"},
        rmdir_by_path={"/docs/bad": OSError(errno.ENOTEMPTY, "directory not empty")},
    )

    result = _invoke(
        "rmdir",
        ["memory:/docs/good", "memory:/docs/bad"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rmdir: memory:/docs/bad: directory not empty\n"
    assert [event[2] for event in events if event[0] == "rmdir"] == [
        "/docs/good",
        "/docs/bad",
    ]


def test_rmdir_continues_after_an_earlier_failure() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_result={"type": "directory"},
        rmdir_by_path={"/docs/bad": OSError(errno.ENOTEMPTY, "directory not empty")},
    )

    result = _invoke(
        "rmdir",
        ["memory:/docs/bad", "memory:/docs/good"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rmdir: memory:/docs/bad: directory not empty\n"
    assert [event[2] for event in events if event[0] == "rmdir"] == [
        "/docs/bad",
        "/docs/good",
    ]


def test_rmdir_processes_repeated_operands_independently() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events, info_result={"type": "directory"})

    result = _invoke(
        "rmdir",
        ["memory:/docs/empty", "memory:/docs/empty"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rmdir: memory:/docs/empty: not found\n"
    assert [event[0] for event in events if event[0] in {"info", "rmdir"}] == [
        "info",
        "rmdir",
        "info",
        "info",
    ]


def test_rmdir_rejects_a_missing_mapped_filesystem_operand() -> None:
    result = _invoke("rmdir", [])

    assert (result.exit_code, result.stdout) == (2, "")
    assert "Missing argument" in result.stderr
    assert "name:/path" in result.stderr


@pytest.mark.parametrize(
    "option",
    ["-p", "-f", "-r", "-R", "--parents", "-h", "--help=value"],
)
def test_rmdir_rejects_unsupported_options_without_entering_sources(
    option: str,
) -> None:
    result = _invoke("rmdir", [option, "memory:/docs/empty"])

    assert (result.exit_code, result.stdout) == (2, "")
    if option == "--help=value":
        assert "does not take a value" in result.stderr
    else:
        assert "No such option" in result.stderr
        context = option.removeprefix("--")
        assert context in result.stderr


@pytest.mark.parametrize(
    ("path", "rendered"),
    [
        ("memory:/", "memory:/"),
        ("memory:/.", "memory:/."),
        ("memory:/..", "memory:/.."),
        ("memory:/docs/.", "memory:/docs/."),
        ("memory:/docs/..", "memory:/docs/.."),
    ],
)
def test_rmdir_rejects_root_and_final_dot_paths_before_source_entry(
    path: str,
    rendered: str,
) -> None:
    source_calls = 0

    def source_must_not_run() -> object:
        nonlocal source_calls
        source_calls += 1
        raise AssertionError

    result = _invoke("rmdir", [path], sources={"memory": source_must_not_run})

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == f"rmdir: {rendered}: rejected path\n"
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
        (["--", "-p"], "-p"),
        (["--", "--"], "--"),
    ],
)
def test_rmdir_rejects_malformed_mapped_filesystem_operands(
    arguments: list[str],
    rendered: str,
) -> None:
    result = _invoke("rmdir", arguments)

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == (f"rmdir: {rendered}: invalid mapped filesystem operand\n")


def test_rmdir_reports_unknown_names_with_locale_sorted_known_names() -> None:
    result = _invoke(
        "rmdir",
        ["other:/docs/empty"],
        sources={
            "zeta": _source_must_not_run,
            "alpha": _source_must_not_run,
        },
    )

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == ("rmdir: other:/docs/empty: unknown filesystem (known: alpha, zeta)\n")


@pytest.mark.parametrize(
    ("info_result", "category"),
    [
        ({"type": "file"}, "not a directory"),
        ({"type": "link"}, "incompatible result"),
        ({}, "incompatible result"),
    ],
)
def test_rmdir_rejects_non_directory_types(info_result: object, category: str) -> None:
    source = _RecordingSource([], info_result=info_result)

    result = _invoke("rmdir", ["memory:/docs/notes.txt"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == f"rmdir: memory:/docs/notes.txt: {category}\n"
    assert [event[0] for event in source.events].count("rmdir") == 0


def test_rmdir_rejects_a_missing_directory() -> None:
    source = _RecordingSource([], info_error=FileNotFoundError("missing"))

    result = _invoke("rmdir", ["memory:/missing"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rmdir: memory:/missing: not found\n"
    assert [event[0] for event in source.events].count("rmdir") == 0


def test_rmdir_rejects_a_source_without_async_rmdir() -> None:
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
        "rmdir",
        ["memory:/docs/empty"],
        sources={"memory": _StripRmdir()},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rmdir: memory:/docs/empty: unsupported operation\n"
    assert [event[0] for event in events].count("rmdir") == 0


@pytest.mark.parametrize(
    ("error_factory", "category"),
    [
        (FileNotFoundError, "not found"),
        (PermissionError, "permission denied"),
        (NotADirectoryError, "not a directory"),
        (NotImplementedError, "unsupported operation"),
        (
            lambda: OSError(errno.ENOTEMPTY, "directory not empty"),
            "directory not empty",
        ),
        (RuntimeError, "backend failure (RuntimeError): "),
    ],
)
def test_rmdir_maps_pre_mutation_failures_to_locked_categories(
    error_factory: Callable[[], Exception],
    category: str,
) -> None:
    error = error_factory()
    source = _RecordingSource([], info_error=error)

    result = _invoke("rmdir", ["memory:/docs/empty"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == f"rmdir: memory:/docs/empty: {category}\n"


@pytest.mark.parametrize(
    ("error_factory", "category"),
    [
        (FileNotFoundError, "not found"),
        (PermissionError, "permission denied"),
        (NotADirectoryError, "not a directory"),
        (NotImplementedError, "unsupported operation"),
        (
            lambda: OSError(errno.ENOTEMPTY, "directory not empty"),
            "directory not empty",
        ),
        (RuntimeError, "backend failure (RuntimeError): "),
    ],
)
def test_rmdir_treats_mutation_exception_with_path_still_present_as_confirmed_failure(
    error_factory: Callable[[], Exception],
    category: str,
) -> None:
    error = error_factory()
    source = _RecordingSource(
        [],
        info_result={"type": "directory"},
        rmdir_error=error,
    )

    result = _invoke("rmdir", ["memory:/docs/empty"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == f"rmdir: memory:/docs/empty: {category}\n"
    assert [event[0] for event in source.events if event[0] == "info"] == [
        "info",
        "info",
    ]


def test_rmdir_treats_mutation_exception_with_proven_absence_as_success() -> None:
    events: list[tuple[object, ...]] = []

    class _AbsentAfterError(_RecordingSource):
        def __call__(self) -> object:
            context = super().__call__()
            filesystem = context.filesystem

            async def rmdir(path: str, **kwargs: object) -> None:
                del kwargs
                self.events.append(
                    (
                        "rmdir",
                        filesystem.source_id,
                        path,
                        id(asyncio.get_running_loop()),
                    )
                )
                filesystem._removed_paths.add(path)
                filesystem._pending_rmdir_verify.add(path)
                message = "timed out after delete"
                raise TimeoutError(message)

            filesystem._rmdir = rmdir  # type: ignore[method-assign]
            return context

    source = _AbsentAfterError(
        events,
        info_result={"type": "directory"},
        post_info_by_path={"/docs/empty": FileNotFoundError("gone")},
    )

    result = _invoke("rmdir", ["memory:/docs/empty"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == ""


def test_rmdir_reports_uncertain_state_when_mutation_and_post_check_are_ambiguous() -> None:
    events: list[tuple[object, ...]] = []

    class _UncertainAfterError(_RecordingSource):
        def __call__(self) -> object:
            context = super().__call__()
            filesystem = context.filesystem

            async def rmdir(path: str, **kwargs: object) -> None:
                del kwargs
                self.events.append(
                    (
                        "rmdir",
                        filesystem.source_id,
                        path,
                        id(asyncio.get_running_loop()),
                    )
                )
                filesystem._pending_rmdir_verify.add(path)
                message = "timed out"
                raise TimeoutError(message)

            filesystem._rmdir = rmdir  # type: ignore[method-assign]
            return context

    source = _UncertainAfterError(
        events,
        info_result={"type": "directory"},
        post_info_by_path={"/docs/empty": PermissionError("denied during verify")},
    )

    result = _invoke("rmdir", ["memory:/docs/empty"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rmdir: memory:/docs/empty: uncertain state\n"


@pytest.mark.parametrize(
    ("error_factory", "category"),
    [
        (PermissionError, "uncertain state"),
        (NotImplementedError, "uncertain state"),
        (RuntimeError, "uncertain state"),
    ],
)
def test_rmdir_reports_uncertain_state_for_ambiguous_post_check_after_void_rmdir(
    error_factory: Callable[[], Exception],
    category: str,
) -> None:
    error = error_factory()
    source = _RecordingSource(
        [],
        info_result={"type": "directory"},
        post_info_by_path={"/docs/empty": error},
    )

    result = _invoke("rmdir", ["memory:/docs/empty"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == f"rmdir: memory:/docs/empty: {category}\n"


def test_rmdir_rejects_when_post_check_shows_the_directory_still_present() -> None:
    source = _RecordingSource(
        [],
        info_result={"type": "directory"},
        post_info_by_path={"/docs/empty": {"type": "directory"}},
    )

    result = _invoke("rmdir", ["memory:/docs/empty"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rmdir: memory:/docs/empty: incompatible result\n"


def test_rmdir_rejects_ambiguous_post_check_shapes() -> None:
    source = _RecordingSource(
        [],
        info_result={"type": "directory"},
        post_info_by_path={"/docs/empty": {"type": "file"}},
    )

    result = _invoke("rmdir", ["memory:/docs/empty"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rmdir: memory:/docs/empty: incompatible result\n"


def test_rmdir_refuses_an_active_same_thread_event_loop(monkeypatch) -> None:
    real_run = asyncio.run

    async def invoke() -> object:
        def refuse_nested_run(_coro: object) -> object:
            raise AssertionError

        monkeypatch.setattr(asyncio, "run", refuse_nested_run)
        return _invoke("rmdir", ["memory:/docs/empty"])

    result = real_run(invoke())

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "rmdir: cannot run from an active event loop\n"


class _ControlFlow(BaseException):
    pass


@pytest.mark.parametrize(
    "control",
    [asyncio.CancelledError(), _ControlFlow("stop")],
)
def test_rmdir_preserves_control_flow_unchanged(control: BaseException) -> None:
    source = _RecordingSource([], info_result={"type": "directory"}, rmdir_error=control)

    with pytest.raises(type(control)) as caught:
        _invoke("rmdir", ["memory:/docs/empty"], sources={"memory": source})

    assert type(caught.value) is type(control)
    if not isinstance(control, asyncio.CancelledError):
        assert caught.value is control
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is type(control)
    assert exception is control
    assert traceback is not None


def test_rmdir_preserves_backend_error_when_its_diagnostic_write_fails(
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

    result = _invoke("rmdir", ["memory:/docs/empty"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.exception is renderer_error
    assert result.stdout == ""
    assert result.stderr == ""
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is PermissionError
    assert exception is backend_error
    assert traceback is not None


def test_rmdir_stops_acquisition_after_a_source_factory_failure() -> None:
    events: list[tuple[object, ...]] = []
    factory_error = ValueError("factory")
    first = _RecordingSource(events, exit_result=True)

    def broken_source() -> NoReturn:
        raise factory_error

    result = _invoke(
        "rmdir",
        ["first:/one", "broken:/two", "later:/three"],
        sources={
            "first": first,
            "broken": broken_source,
            "later": _source_must_not_run,
        },
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == ("rmdir: broken: source factory failure (ValueError): factory\n")
    assert [event[0] for event in events] == ["factory", "enter", "exit"]


def test_rmdir_reports_source_exit_failures_in_reverse_order() -> None:
    events: list[tuple[object, ...]] = []
    alpha = _RecordingSource(events, info_result={"type": "directory"}, exit_error=OSError("alpha exit"))
    beta = _RecordingSource(events, info_result={"type": "directory"}, exit_error=RuntimeError("beta exit"))

    result = _invoke(
        "rmdir",
        ["alpha:/one", "beta:/two"],
        sources={"alpha": alpha, "beta": beta},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == (
        "rmdir: beta: source exit failure (RuntimeError): beta exit\n"
        "rmdir: alpha: source exit failure (OSError): alpha exit\n"
    )


def test_rmdir_accepts_hidden_directory_paths_that_are_not_final_dot_components() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events, info_result={"type": "directory"})

    result = _invoke("rmdir", ["memory:/.hidden"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == ""
    assert [event[2] for event in events if event[0] in {"info", "rmdir"}] == [
        "/.hidden",
        "/.hidden",
        "/.hidden",
    ]
