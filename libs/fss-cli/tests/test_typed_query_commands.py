"""Typed command registration through the mounted application seam."""

from __future__ import annotations

import pytest
from typer.testing import CliRunner

from fss_cli import App

from ._support import _source_must_not_run


@pytest.mark.parametrize(
    ("command", "summary", "parameters"),
    [
        ("info", "Display normalized file information", ("name:/path",)),
        ("size", "Display exact file sizes", ("name:/path",)),
        ("test", "Evaluate a file predicate", ("name:/path", "-e", "-d", "-f")),
        ("stat", "Display file status", ("name:/path",)),
        ("mkdir", "Create directories", ("name:/path", "-p")),
        ("rmdir", "Remove empty directories", ("name:/path",)),
        ("unlink", "Remove a single file", ("name:/path",)),
    ],
)
def test_help_comes_from_typed_callback_metadata(
    command: str,
    summary: str,
    parameters: tuple[str, ...],
) -> None:
    result = CliRunner().invoke(
        App({"memory": _source_must_not_run}).typer_app,
        [command, "--help"],
    )
    help_text = result.stdout

    assert (result.exit_code, result.stderr) == (0, "")
    assert "Usage:" in help_text
    assert f"root {command} [OPTIONS]" in help_text
    assert summary in help_text
    for parameter in parameters:
        assert parameter in help_text


@pytest.mark.parametrize(
    ("command", "arguments", "contexts"),
    [
        ("info", ["--unknown", "memory:/a"], ("No such option", "unknown")),
        ("size", [], ("Missing argument", "name:/path")),
        ("test", ["-e"], ("Missing argument", "name:/path")),
        ("stat", [], ("Missing argument", "name:/path")),
        ("mkdir", [], ("Missing argument", "name:/path")),
        (
            "rmdir",
            ["--parents", "memory:/docs"],
            ("No such option", "parents"),
        ),
        (
            "unlink",
            ["memory:/one", "memory:/two"],
            ("unexpected extra argument", "memory:/two"),
        ),
    ],
)
def test_typer_rejects_syntax_before_source_acquisition(
    command: str,
    arguments: list[str],
    contexts: tuple[str, ...],
) -> None:
    result = CliRunner().invoke(
        App({"memory": _source_must_not_run}).typer_app,
        [command, *arguments],
    )

    assert (result.exit_code, result.stdout_bytes) == (2, b"")
    diagnostic = result.stderr
    for context in contexts:
        assert context in diagnostic
