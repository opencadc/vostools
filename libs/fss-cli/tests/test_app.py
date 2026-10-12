"""Construction and preflight tests for the public embedded-command seam."""

import asyncio
import re
from dataclasses import FrozenInstanceError, dataclass
from typing import Annotated, cast
from unittest.mock import Mock

import pytest
import typer
from typer.testing import CliRunner

from fss_cli import (
    App,
    AppCapabilities,
    AsyncFilesystemSource,
    CommandCallback,
    CommandContext,
    RecursionCapabilities,
)

from ._ansi import strip_ansi
from ._support import _invoke, _source_must_not_run


def test_app_rejects_an_empty_source_mapping() -> None:
    with pytest.raises(ValueError) as error:  # noqa: PT011 - message is not API.
        App({})
    assert type(error.value) is ValueError


@pytest.mark.parametrize(
    ("name", "error_type"),
    [
        (1, TypeError),
        ("", ValueError),
        ("bad:name", ValueError),
        ("bad\0name", ValueError),
        ("bad\nname", ValueError),
        ("-", ValueError),
        ("-source", ValueError),
    ],
)
def test_app_rejects_invalid_source_names(name, error_type) -> None:
    with pytest.raises(error_type) as error:
        App({name: _source_must_not_run})
    assert type(error.value) is error_type


def test_public_exports_include_application_capability_types() -> None:
    def callback() -> None:
        pass

    recursion: RecursionCapabilities = {"copy": True, "remove": False}
    capabilities: AppCapabilities = {"recursion": recursion}
    typed_callback: CommandCallback = callback

    assert capabilities == {"recursion": {"copy": True, "remove": False}}
    assert AsyncFilesystemSource is not None
    assert typed_callback() is None
    assert __import__("fss_cli").__all__ == [
        "App",
        "AppCapabilities",
        "AsyncFilesystemSource",
        "CommandCallback",
        "CommandContext",
        "RecursionCapabilities",
    ]


@pytest.mark.parametrize(
    ("capabilities", "error_type", "message"),
    [
        ([], TypeError, "capabilities must be a mapping"),
        ({"recursion": []}, TypeError, "capabilities.recursion must be a mapping"),
        (
            {"recursion": {"copy": 1}},
            TypeError,
            "capabilities.recursion.copy must be a bool",
        ),
        (
            {"recursion": {"remove": None}},
            TypeError,
            "capabilities.recursion.remove must be a bool",
        ),
        (
            {"future": {}},
            ValueError,
            "capabilities.future: unknown capability",
        ),
        (
            {"recursion": {"future": True}},
            ValueError,
            "capabilities.recursion.future: unknown capability",
        ),
    ],
)
def test_app_rejects_malformed_or_unknown_capabilities(
    capabilities: object,
    error_type: type[Exception],
    message: str,
) -> None:
    with pytest.raises(error_type, match=f"^{re.escape(message)}$"):
        App(
            {"memory": _source_must_not_run},
            capabilities=cast("AppCapabilities", capabilities),
        )


def test_app_snapshots_nested_capabilities_at_construction() -> None:
    recursion: RecursionCapabilities = {"copy": False}
    capabilities: AppCapabilities = {"recursion": recursion}
    typer_app = App(
        {"memory": _source_must_not_run},
        capabilities=capabilities,
    ).typer_app
    recursion["copy"] = True
    capabilities.clear()

    result = CliRunner().invoke(
        typer_app,
        ["cp", "-R", "bad", "also-bad"],
        env={"FORCE_COLOR": "1"},
    )

    assert (result.exit_code, result.stdout_bytes) == (2, b"")
    assert "No such option: -R" in strip_ansi(result.stderr)


def test_ls_rejects_a_missing_mapped_filesystem_operand() -> None:
    result = _invoke("ls", [])

    assert (result.exit_code, result.stdout) == (2, "")
    diagnostic = strip_ansi(result.stderr)
    assert "Missing argument" in diagnostic
    assert "name:/path" in diagnostic


def test_ls_refuses_an_active_same_thread_event_loop(monkeypatch) -> None:
    real_run = asyncio.run
    recording_run = Mock(wraps=real_run)

    async def invoke() -> object:
        monkeypatch.setattr(asyncio, "run", recording_run)
        return _invoke("ls", ["memory:/docs"])

    result = real_run(invoke())

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "ls: cannot run from an active event loop\n"
    assert recording_run.call_count == 0


def test_ls_typer_failure_precedes_command_coroutine(
    monkeypatch,
) -> None:
    real_run = asyncio.run
    recording_run = Mock(wraps=real_run)

    monkeypatch.setattr(asyncio, "run", recording_run)
    result = _invoke("ls", [])

    assert result.exit_code == 2
    assert recording_run.call_count == 0


@pytest.mark.parametrize(
    ("option", "context"),
    [
        ("--long", "--long"),
        ("-a", "-a"),
        ("--all", "--all"),
        ("-x", "-x"),
        ("-lx", "-x"),
        ("--help=value", "does not take a value"),
    ],
)
def test_typer_rejects_unsupported_ls_options(
    option: str,
    context: str,
) -> None:
    result = _invoke("ls", [option, "memory:/docs"])

    assert (result.exit_code, result.stdout) == (2, "")
    assert context in strip_ansi(result.stderr)


@pytest.mark.parametrize(
    "supported_options",
    [
        ["-A"],
        ["-AA"],
        ["-A", "-AAA"],
        ["memory:/docs", "-A"],
        ["-l"],
        ["-ll"],
        ["-Al"],
        ["-lh"],
        ["-h", "-l"],
        ["-hAl"],
    ],
)
def test_ls_accepts_repeated_grouped_and_interspersed_supported_options(
    supported_options: list[str],
) -> None:
    result = _invoke("ls", [*supported_options, "bad"])

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == "ls: bad: invalid mapped filesystem operand\n"


@pytest.mark.parametrize(
    "arguments",
    [["memory:/docs", "-h"], ["-Ah", "memory:/docs"]],
)
def test_ls_rejects_human_sizes_without_long_mode(
    arguments: list[str],
) -> None:
    result = _invoke("ls", arguments)

    assert (result.exit_code, result.stdout, result.stderr) == (
        2,
        "",
        "ls: -h: requires long listing\n",
    )


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
        (["--", "-l"], "-l"),
        (["--", "-A"], "-A"),
        (["--", "--"], "--"),
    ],
)
def test_ls_rejects_malformed_mapped_filesystem_operands(
    arguments: list[str],
    rendered: str,
) -> None:
    result = _invoke("ls", arguments)

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == (f"ls: {rendered}: invalid mapped filesystem operand\n")


def test_ls_reports_unknown_names_with_locale_sorted_known_names() -> None:
    result = _invoke(
        "ls",
        ["other:/docs"],
        sources={
            "zeta": _source_must_not_run,
            "alpha": _source_must_not_run,
        },
    )

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == ("ls: other:/docs: unknown filesystem (known: alpha, zeta)\n")


def test_app_snapshots_its_source_mapping_once() -> None:
    sources = {"memory": _source_must_not_run}
    typer_app = App(sources).typer_app
    sources.clear()
    sources["later"] = _source_must_not_run

    result = CliRunner().invoke(typer_app, ["ls", "later:/docs"])

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == ("ls: later:/docs: unknown filesystem (known: memory)\n")


def test_source_aware_callback_keeps_parent_context_and_frozen_sources() -> None:
    @dataclass(frozen=True)
    class HostContext:
        label: str

    observed: list[tuple[str, ...]] = []

    def inspect_sources(ctx: typer.Context) -> None:
        """Inspect configured sources."""
        command_context = ctx.find_object(CommandContext)
        host_context = ctx.find_object(HostContext)
        assert command_context is not None
        assert host_context is not None
        observed.append((host_context.label, *command_context.sources))
        with pytest.raises(TypeError):
            cast("dict[str, AsyncFilesystemSource]", command_context.sources)["later"] = _source_must_not_run
        with pytest.raises(FrozenInstanceError):
            command_context.sources = {}  # type: ignore[misc]

    parent = typer.Typer(add_completion=False)

    @parent.callback()
    def parent_root(ctx: typer.Context) -> None:
        ctx.obj = HostContext("host")

    sources = {"memory": _source_must_not_run}
    parent.add_typer(
        App(sources, extensions=[inspect_sources]).typer_app,
        name="fs",
    )
    sources.clear()

    result = CliRunner().invoke(parent, ["fs", "inspect-sources"])

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert observed == [("host", "memory")]


def test_source_free_callback_and_help_are_defined_by_callback_metadata() -> None:
    def echo_label(
        label: Annotated[str, typer.Argument(help="Host label")],
        prefix: Annotated[str, typer.Option("--prefix")] = "",
    ) -> None:
        """Echo one host label."""
        typer.echo(f"{prefix}{label}")

    parent = typer.Typer(add_completion=False)
    parent.add_typer(
        App({"memory": _source_must_not_run}, extensions=[echo_label]).typer_app,
        name="fs",
    )

    result = CliRunner().invoke(
        parent,
        ["fs", "echo-label", "--prefix", "host-", "hello"],
    )
    help_result = CliRunner().invoke(parent, ["fs", "echo-label", "--help"])
    help_text = strip_ansi(help_result.stdout)

    assert (result.exit_code, result.stdout, result.stderr) == (0, "host-hello\n", "")
    assert (help_result.exit_code, help_result.stderr) == (0, "")
    assert "Echo one host label." in help_text
    assert "Host label" in help_text
    assert "--prefix" in help_text


def test_duplicate_names_follow_core_first_and_extension_caller_order() -> None:
    def head() -> None:
        typer.echo("first")

    def later_head() -> None:
        typer.echo("second")

    later_head.__name__ = "head"
    parent = typer.Typer(add_completion=False)
    parent.add_typer(
        App(
            {"memory": _source_must_not_run},
            extensions=[head, later_head],
        ).typer_app,
        name="fs",
    )

    result = CliRunner().invoke(parent, ["fs", "head"])

    assert (result.exit_code, result.stdout, result.stderr) == (0, "second\n", "")


def test_app_instances_keep_extension_contexts_isolated() -> None:
    def source_names(ctx: typer.Context) -> None:
        command_context = ctx.find_object(CommandContext)
        assert command_context is not None
        typer.echo(",".join(command_context.sources))

    parent = typer.Typer(add_completion=False)
    parent.add_typer(
        App({"alpha": _source_must_not_run}, extensions=[source_names]).typer_app,
        name="first",
    )
    parent.add_typer(
        App({"beta": _source_must_not_run}, extensions=[source_names]).typer_app,
        name="second",
    )

    first_result = CliRunner().invoke(parent, ["first", "source-names"])
    second_result = CliRunner().invoke(parent, ["second", "source-names"])

    assert (first_result.exit_code, first_result.stdout, first_result.stderr) == (
        0,
        "alpha\n",
        "",
    )
    assert (second_result.exit_code, second_result.stdout, second_result.stderr) == (
        0,
        "beta\n",
        "",
    )


def test_ls_escapes_each_known_name_in_an_unknown_name_diagnostic() -> None:
    result = _invoke(
        "ls",
        ["other:/docs"],
        sources={"known\\name\r": _source_must_not_run},
    )

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == ("ls: other:/docs: unknown filesystem (known: known\\\\name\\x0d)\n")


@pytest.mark.parametrize("arguments", [["-A"], ["-AA", "--"]])
def test_ls_reports_a_missing_operand_after_supported_option_syntax(
    arguments: list[str],
) -> None:
    result = _invoke("ls", arguments)

    assert (result.exit_code, result.stdout) == (2, "")
    assert "Missing argument" in strip_ansi(result.stderr)


@pytest.mark.parametrize(
    ("arguments", "diagnostic"),
    [
        (["bare", "-l"], "ls: bare: invalid mapped filesystem operand\n"),
        (
            ["unknown:/docs", "-l"],
            "ls: unknown:/docs: unknown filesystem (known: memory)\n",
        ),
        (["-l", "bare"], "ls: bare: invalid mapped filesystem operand\n"),
    ],
)
def test_ls_reports_only_the_first_preflight_error_in_argument_order(
    arguments: list[str],
    diagnostic: str,
) -> None:
    result = _invoke("ls", arguments)

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == diagnostic


@pytest.mark.parametrize("arguments", [["--help"], ["-l", "--help"]])
def test_ls_leaves_exact_help_to_the_framework(arguments: list[str]) -> None:
    result = _invoke("ls", arguments)

    assert result.exit_code == 0
    assert "Usage:" in result.stdout
    assert result.stderr == ""


@pytest.mark.parametrize("operand", ["--help", "--help=value"])
def test_ls_treats_help_tokens_after_the_option_delimiter_as_operands(
    operand: str,
) -> None:
    result = _invoke("ls", ["--", operand])

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == (f"ls: {operand}: invalid mapped filesystem operand\n")


def test_ls_preserves_typer_failures_when_mounted_below_a_parent_app() -> None:
    parent = typer.Typer(add_completion=False)
    parent.add_typer(
        App({"memory": _source_must_not_run}).typer_app,
        name="data",
    )

    result = CliRunner().invoke(
        parent,
        ["data", "ls", "--long", "memory:/docs"],
    )

    assert (result.exit_code, result.stdout) == (2, "")
    diagnostic = strip_ansi(result.stderr)
    assert "Usage: root data ls" in diagnostic
    assert "No such option: --long" in diagnostic


def test_typer_preflight_precedes_active_loop_refusal() -> None:
    async def invoke() -> object:
        return _invoke("ls", ["-l"])

    result = asyncio.run(invoke())

    assert (result.exit_code, result.stdout) == (2, "")
    assert "Missing argument" in strip_ansi(result.stderr)


def test_ls_renders_all_diagnostic_control_characters_in_order() -> None:
    result = _invoke("ls", ["memory:/bad\\\0\r\n"])

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == ("ls: memory:/bad\\\\\\x00\\x0d\\x0a: invalid mapped filesystem operand\n")


@pytest.mark.parametrize(
    "valid_prefix",
    [
        ["memory:/"],
        ["memory:/path:with:colons"],
        ["memory:/one", "memory:/two"],
        ["--", "memory:/docs"],
    ],
)
def test_ls_accepts_locked_operand_grammar_before_a_later_error(
    valid_prefix: list[str],
) -> None:
    result = _invoke("ls", [*valid_prefix, "bad"])

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == "ls: bad: invalid mapped filesystem operand\n"
