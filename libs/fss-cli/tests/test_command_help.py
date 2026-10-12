"""Help-surface completeness across the registered command set.

Typer vendors its own Click, so these tests duck-type the generated command
objects instead of importing Typer's private Click fork.
"""

from __future__ import annotations

from typing import Any

import pytest
import typer.main

from fss_cli import App

from ._support import _source_must_not_run


def _commands(**capabilities: Any) -> dict[str, Any]:
    app = App({"memory": _source_must_not_run}, **capabilities)
    return dict(typer.main.get_command(app.typer_app).commands)


def _undocumented_options(command: Any) -> list[str]:
    """Return option spellings on ``command`` that carry no help text."""
    return [
        parameter.opts[0]
        for parameter in command.params
        if any(spelling.startswith("-") for spelling in parameter.opts) and not getattr(parameter, "help", None)
    ]


def test_every_registered_command_has_a_summary() -> None:
    undocumented = [name for name, command in _commands().items() if not command.help]

    assert undocumented == []


@pytest.mark.parametrize(
    "capabilities",
    [
        {},
        {"capabilities": {"recursion": {"copy": True, "remove": True}}},
        {"capabilities": {"recursion": {"copy": False, "remove": False}}},
    ],
)
def test_every_registered_option_has_help_text(capabilities: dict[str, Any]) -> None:
    missing = {
        name: undocumented
        for name, command in _commands(**capabilities).items()
        if (undocumented := _undocumented_options(command))
    }

    assert missing == {}
