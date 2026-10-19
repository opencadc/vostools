"""ANSI normalization for CLI assertions."""

import typer


def strip_ansi(value: str) -> str:
    """Return CLI output without terminal styling."""
    return typer._click.utils.strip_ansi(value)
