"""Source cleanup and control-flow tests through the public seam."""

import asyncio
from collections.abc import Iterable
from typing import NoReturn

import pytest
import typer
from typer.testing import CliRunner

from fss_cli import App

from ._support import _invoke, _RecordingSource, _source_must_not_run


def _fail_diagnostic_writes(
    monkeypatch: pytest.MonkeyPatch,
    failures: Iterable[BaseException],
    events: list[tuple[object, ...]] | None = None,
) -> list[object]:
    diagnostics: list[object] = []
    remaining_failures = iter(failures)
    real_echo = typer.echo

    def failing_echo(
        message: object = None,
        *args: object,
        **kwargs: object,
    ) -> None:
        if kwargs.get("err") is not True:
            real_echo(message, *args, **kwargs)
            return
        diagnostics.append(message)
        if events is not None:
            events.append(("diagnostic", message))
        raise next(remaining_failures)

    monkeypatch.setattr(typer, "echo", failing_echo)
    return diagnostics


def test_ls_reports_every_source_exit_failure_in_reverse_order() -> None:
    events: list[tuple[object, ...]] = []
    alpha = _RecordingSource(events, exit_error=OSError("alpha\\\0\r\n"))
    beta = _RecordingSource(events, exit_error=RuntimeError("beta failure"))

    result = _invoke(
        "ls",
        ["alpha:/one", "beta:/two"],
        sources={"alpha": alpha, "beta": beta},
    )

    assert result.exit_code == 1
    assert result.stdout == "alpha:/one\nbeta:/two\n"
    assert result.stderr == (
        "ls: beta: source exit failure (RuntimeError): beta failure\n"
        "ls: alpha: source exit failure (OSError): alpha\\\\\\x00\\x0d\\x0a\n"
    )
    assert len(alpha.exit_calls) == 1
    assert len(beta.exit_calls) == 1


def test_ls_finishes_cleanup_before_a_diagnostic_write_can_fail(
    monkeypatch,
) -> None:
    events: list[tuple[object, ...]] = []
    primary = SystemExit(7)
    diagnostic_control = _ControlFlow("diagnostic stop")
    alpha = _RecordingSource(
        events,
        info_error=primary,
        exit_error=OSError("alpha exit"),
    )
    beta = _RecordingSource(events, exit_error=RuntimeError("beta exit"))
    diagnostics = _fail_diagnostic_writes(
        monkeypatch,
        (diagnostic_control, diagnostic_control),
        events,
    )
    result = _invoke(
        "ls",
        ["alpha:/one", "beta:/two"],
        sources={"alpha": alpha, "beta": beta},
    )

    assert result.exception is primary
    assert result.exit_code == 7
    assert len(alpha.exit_calls) == len(beta.exit_calls) == 1
    assert diagnostics == [
        "ls: beta: source exit failure (RuntimeError): beta exit",
        "ls: alpha: source exit failure (OSError): alpha exit",
    ]
    assert [event[0] for event in events][-4:] == [
        "exit",
        "exit",
        "diagnostic",
        "diagnostic",
    ]


def test_ls_propagates_first_diagnostic_control_after_every_render(
    monkeypatch,
) -> None:
    events: list[tuple[object, ...]] = []
    first_control = _ControlFlow("first diagnostic")
    later_control = _ControlFlow("later diagnostic")
    controls = iter((first_control, later_control))
    alpha = _RecordingSource(events, exit_error=OSError("alpha exit"))
    beta = _RecordingSource(events, exit_error=RuntimeError("beta exit"))
    diagnostics = _fail_diagnostic_writes(monkeypatch, controls)
    with pytest.raises(_ControlFlow) as caught:
        _invoke(
            "ls",
            ["alpha:/one", "beta:/two"],
            sources={"alpha": alpha, "beta": beta},
        )

    assert caught.value is first_control
    assert len(alpha.exit_calls) == len(beta.exit_calls) == 1
    assert len(diagnostics) == 2


def test_ls_treats_ordinary_diagnostic_write_failures_as_status_one(
    monkeypatch,
) -> None:
    events: list[tuple[object, ...]] = []
    alpha = _RecordingSource(events, exit_error=OSError("alpha exit"))
    beta = _RecordingSource(events, exit_error=RuntimeError("beta exit"))
    diagnostics = _fail_diagnostic_writes(
        monkeypatch,
        (RuntimeError("diagnostic write"), RuntimeError("diagnostic write")),
    )
    result = _invoke(
        "ls",
        ["alpha:/one", "beta:/two"],
        sources={"alpha": alpha, "beta": beta},
    )

    assert result.exit_code == 1
    assert len(alpha.exit_calls) == len(beta.exit_calls) == 1
    assert len(diagnostics) == 2


def test_ls_renders_source_names_and_empty_exception_messages() -> None:
    source_name = "broken\\source\r"

    def broken_source() -> NoReturn:
        raise RuntimeError

    result = _invoke(
        "ls",
        [f"{source_name}:/file"],
        sources={source_name: broken_source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == ("ls: broken\\\\source\\x0d: source factory failure (RuntimeError): \n")


def test_ls_reports_acquisition_before_cleanup_failures() -> None:
    events: list[tuple[object, ...]] = []
    first = _RecordingSource(events, exit_error=OSError("cleanup"))
    acquisition_error = ValueError("acquire")

    def broken_source() -> NoReturn:
        raise acquisition_error

    result = _invoke(
        "ls",
        ["first:/one", "broken:/two"],
        sources={"first": first, "broken": broken_source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == (
        "ls: broken: source factory failure (ValueError): acquire\nls: first: source exit failure (OSError): cleanup\n"
    )
    assert len(first.exit_calls) == 1


def test_ls_retains_a_command_diagnostic_when_cleanup_fails() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events, None, exit_error=OSError("cleanup"))

    result = _invoke("ls", ["memory:/file"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == (
        "ls: memory:/file: incompatible result\nls: memory: source exit failure (OSError): cleanup\n"
    )


class _ControlFlow(BaseException):
    pass


@pytest.mark.parametrize(
    "control",
    [asyncio.CancelledError(), _ControlFlow("stop")],
)
def test_ls_cleans_up_then_propagates_info_control_flow_unchanged(
    control: BaseException,
) -> None:
    events: list[tuple[object, ...]] = []
    alpha = _RecordingSource(events, info_error=control, exit_result=True)
    beta = _RecordingSource(events, exit_result=True)

    with pytest.raises(type(control)) as caught:
        _invoke(
            "ls",
            ["alpha:/one", "beta:/two"],
            sources={"alpha": alpha, "beta": beta},
        )

    assert type(caught.value) is type(control)
    if not isinstance(control, asyncio.CancelledError):
        assert caught.value is control
    assert [event[0] for event in events] == [
        "factory",
        "enter",
        "factory",
        "enter",
        # Both operands' reads start concurrently; beta's is drained too.
        "info",
        "info",
        "exit",
        "exit",
    ]
    for source in (alpha, beta):
        assert len(source.exit_calls) == 1
        exception_type, exception, traceback = source.exit_calls[0]
        assert exception_type is type(control)
        assert exception is control
        assert traceback is not None


def test_ls_cleans_up_then_propagates_system_exit_unchanged() -> None:
    events: list[tuple[object, ...]] = []
    control = SystemExit(7)
    source = _RecordingSource(events, info_error=control)

    result = _invoke("ls", ["memory:/file"], sources={"memory": source})

    assert result.exception is control
    assert result.exit_code == 7
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is SystemExit
    assert exception is control
    assert traceback is not None


def test_ls_propagates_factory_control_flow_after_cleaning_prior_sources() -> None:
    events: list[tuple[object, ...]] = []
    control = _ControlFlow("factory stop")
    first = _RecordingSource(events)

    def broken_source() -> NoReturn:
        raise control

    with pytest.raises(_ControlFlow) as caught:
        _invoke(
            "ls",
            ["first:/one", "broken:/two", "later:/three"],
            sources={
                "first": first,
                "broken": broken_source,
                "later": _source_must_not_run,
            },
        )

    assert caught.value is control
    exception_type, exception, traceback = first.exit_calls[0]
    assert exception_type is _ControlFlow
    assert exception is control
    assert traceback is not None


def test_ls_propagates_entry_control_flow_without_exiting_failed_entry() -> None:
    events: list[tuple[object, ...]] = []
    control = _ControlFlow("entry stop")
    first = _RecordingSource(events)

    class BrokenContext:
        async def __aenter__(self) -> NoReturn:
            raise control

        async def __aexit__(self, *exc_info: object) -> NoReturn:
            raise AssertionError

    with pytest.raises(_ControlFlow) as caught:
        _invoke(
            "ls",
            ["first:/one", "broken:/two"],
            sources={"first": first, "broken": BrokenContext},
        )

    assert caught.value is control
    exception_type, exception, traceback = first.exit_calls[0]
    assert exception_type is _ControlFlow
    assert exception is control
    assert traceback is not None


def test_ls_passes_keyboard_interrupt_to_cleanup_before_typer_handles_it() -> None:
    events: list[tuple[object, ...]] = []
    control = KeyboardInterrupt()
    source = _RecordingSource(events, info_error=control)

    result = _invoke("ls", ["memory:/file"], sources={"memory": source})

    assert result.exit_code != 0
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is KeyboardInterrupt
    assert exception is control
    assert traceback is not None


def test_ls_propagates_the_first_cleanup_control_flow_after_all_exits() -> None:
    events: list[tuple[object, ...]] = []
    later_control = _ControlFlow("later cleanup")
    first_control = _ControlFlow("first cleanup")
    alpha = _RecordingSource(events, exit_error=later_control)
    beta = _RecordingSource(events, exit_error=first_control)

    with pytest.raises(_ControlFlow) as caught:
        _invoke(
            "ls",
            ["alpha:/one", "beta:/two"],
            sources={"alpha": alpha, "beta": beta},
        )

    assert caught.value is first_control
    assert len(alpha.exit_calls) == 1
    assert len(beta.exit_calls) == 1


def test_ls_preserves_primary_control_flow_across_cleanup_failures() -> None:
    events: list[tuple[object, ...]] = []
    primary = SystemExit(7)
    alpha = _RecordingSource(
        events,
        info_error=primary,
        exit_error=RuntimeError("alpha exit"),
    )
    beta = _RecordingSource(events, exit_error=_ControlFlow("beta exit"))

    result = _invoke(
        "ls",
        ["alpha:/one", "beta:/two"],
        sources={"alpha": alpha, "beta": beta},
    )

    assert result.exception is primary
    assert result.exit_code == 7
    assert result.stdout == ""
    assert result.stderr == ("ls: alpha: source exit failure (RuntimeError): alpha exit\n")
    assert len(alpha.exit_calls) == 1
    assert len(beta.exit_calls) == 1


def test_ls_cleanup_control_flow_precedes_an_ordinary_command_error() -> None:
    events: list[tuple[object, ...]] = []
    command_error = ValueError("info failure")
    cleanup_control = _ControlFlow("cleanup stop")
    alpha = _RecordingSource(events, info_error=command_error)
    beta = _RecordingSource(events, exit_error=cleanup_control)

    with pytest.raises(_ControlFlow) as caught:
        _invoke(
            "ls",
            ["alpha:/one", "beta:/two"],
            sources={"alpha": alpha, "beta": beta},
        )

    assert caught.value is cleanup_control
    for source in (alpha, beta):
        exception_type, exception, traceback = source.exit_calls[0]
        assert exception_type is ValueError
        assert exception is command_error
        assert traceback is not None


def test_ls_ignores_truthy_exit_suppression_for_an_incompatible_result() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events, None, exit_result=True)

    result = _invoke("ls", ["memory:/file"], sources={"memory": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "ls: memory:/file: incompatible result\n"
    assert len(source.exit_calls) == 1


def test_each_invocation_owns_a_fresh_source_context_and_filesystem() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)
    typer_app = App({"memory": source}).typer_app
    runner = CliRunner()

    first = runner.invoke(typer_app, ["ls", "memory:/file"])
    second = runner.invoke(typer_app, ["ls", "memory:/file"])

    assert first.exit_code == second.exit_code == 0
    assert first.stdout == second.stdout == "memory:/file\n"
    assert source.call_count == 2
    assert len(source.exit_calls) == 2
    assert source.contexts[0] is not source.contexts[1]
    assert source.contexts[0].filesystem is not source.contexts[1].filesystem
