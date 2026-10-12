"""Shared rendering for stable command diagnostics."""

_ESCAPES = {code: f"\\x{code:02x}" for code in (*range(0x20), 0x7F)}
_ESCAPES[ord("\\")] = "\\\\"


def _render_diagnostic_value(value: str) -> str:
    return value.translate(_ESCAPES)


def _render_diagnostic_prefix(command: str) -> str:
    return f"{_render_diagnostic_value(command)}:"
