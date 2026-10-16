"""Typed ``cp`` registration through the mounted application seam."""

from __future__ import annotations

from typing import NoReturn

import pytest
from typer.testing import CliRunner

from fss_cli import App

from ._ansi import strip_ansi
from ._support import _source_must_not_run


@pytest.mark.parametrize(
    ("capabilities", "summary", "recursive"),
    [
        (None, "Copy files or one directory.", True),
        (
            {"recursion": {"copy": False}},
            "Copy one or more files.",
            False,
        ),
    ],
)
def test_cp_help_comes_from_capability_selected_typed_callback(
    capabilities,
    summary: str,
    recursive: bool,
) -> None:
    result = CliRunner().invoke(
        App(
            {"memory": _source_must_not_run},
            capabilities=capabilities,
        ).typer_app,
        ["cp", "--help"],
        env={"FORCE_COLOR": "1"},
    )
    help_text = strip_ansi(result.stdout)

    assert (result.exit_code, result.stderr) == (0, "")
    assert "Usage:" in help_text
    assert "SOURCE... DESTINATION" in help_text
    assert summary in help_text
    assert ("-R" in help_text) is recursive
    assert ("-r" in help_text) is recursive


@pytest.mark.parametrize(
    ("capabilities", "arguments", "contexts"),
    [
        (None, [], ("Missing argument", "SOURCE... DESTINATION")),
        (
            None,
            ["--preserve", "memory:/one", "memory:/two"],
            ("No such option", "--preserve"),
        ),
        (
            {"recursion": {"copy": False}},
            ["-R", "memory:/one", "memory:/two"],
            ("No such option", "-R"),
        ),
    ],
)
def test_typer_rejects_cp_syntax_before_source_acquisition(
    capabilities,
    arguments: list[str],
    contexts: tuple[str, ...],
) -> None:
    source_calls = 0

    def source() -> NoReturn:
        nonlocal source_calls
        source_calls += 1
        raise AssertionError

    result = CliRunner().invoke(
        App(
            {"memory": source},
            capabilities=capabilities,
        ).typer_app,
        ["cp", *arguments],
        env={"FORCE_COLOR": "1"},
    )
    diagnostic = strip_ansi(result.stderr)

    assert (result.exit_code, result.stdout_bytes) == (2, b"")
    for context in contexts:
        assert context in diagnostic
    assert source_calls == 0


def test_cp_option_terminator_leaves_following_token_as_an_operand() -> None:
    result = CliRunner().invoke(
        App({"memory": _source_must_not_run}).typer_app,
        ["cp", "--", "--help", "memory:/target"],
        env={"FORCE_COLOR": "1"},
    )

    assert (result.exit_code, result.stdout_bytes) == (2, b"")
    assert strip_ansi(result.stderr) == ("cp: --help: invalid mapped filesystem operand\n")
