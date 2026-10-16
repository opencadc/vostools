"""Process-boundary evidence for plain ``ls`` output behavior."""

import os
import subprocess
import sys
from pathlib import Path

import pytest

from ._process_support import (
    _REPO_ROOT,
    _TIMEOUT,
    _environment,
    _run_pty,
    _run_redirected,
)

_NATIVE_NEWLINE = os.linesep.encode()
_BOLD = b"\x1b[1m"
_NORMAL = b"\x1b[22m"
_RED_NAME = b"\x1b[31mred\x1b[0m"
_COLOR_TERMINAL: dict[str, str | None] = {"TERM": "xterm-256color", "NO_COLOR": None}
_EXPECTED_REDIRECTED_STDOUT = _NATIVE_NEWLINE.join([_RED_NAME, b"a.txt", b"link", b""])
_EXPECTED_TTY_STDOUT = _NATIVE_NEWLINE.join([_BOLD + _RED_NAME + _NORMAL, b"a.txt", b"link", b""])
_OUTPUT_ERROR = b"ls: output: output failure (OSError): disk\\\\bad\\x0aline" + _NATIVE_NEWLINE
_CHILD_PATH = Path(__file__).with_name("_output_process_child.py")


def _command(mode: str) -> list[str]:
    operands = ["memory:/docs"]
    if mode == "runtime-and-fail":
        operands = ["memory:/missing", *operands]
    return [sys.executable, str(_CHILD_PATH), mode, "ls", *operands]


def test_public_seam_broken_pipe_is_silent_runtime_failure() -> None:
    read_fd, write_fd = os.pipe()
    os.close(read_fd)
    try:
        result = subprocess.run(  # noqa: S603 - fixed child command.
            _command("normal"),
            cwd=_REPO_ROOT,
            env=_environment(),
            stdin=subprocess.DEVNULL,
            stdout=write_fd,
            stderr=subprocess.PIPE,
            timeout=_TIMEOUT,
            check=False,
        )
    finally:
        os.close(write_fd)

    assert result.returncode == 1
    assert result.stderr == b""


def test_public_seam_redirected_output_stays_verbatim_on_a_color_terminal() -> None:
    redirected = _run_redirected(_command("tty"), overrides=_COLOR_TERMINAL)

    assert redirected.returncode == 0
    assert redirected.stdout == _EXPECTED_REDIRECTED_STDOUT
    assert redirected.stderr == b""


@pytest.mark.parametrize(
    "overrides",
    [
        pytest.param(_COLOR_TERMINAL, id="color-terminal"),
        pytest.param({**_COLOR_TERMINAL, "NO_COLOR": ""}, id="empty-no-color"),
    ],
)
def test_public_seam_tty_adds_only_bold_around_directory_names(
    overrides: dict[str, str | None],
) -> None:
    returncode, stdout, stderr = _run_pty(_command("tty"), overrides=overrides)

    assert returncode == 0
    assert stdout == _EXPECTED_TTY_STDOUT
    assert stdout.replace(_BOLD, b"").replace(_NORMAL, b"") == _EXPECTED_REDIRECTED_STDOUT
    assert stderr == b""


@pytest.mark.parametrize(
    "overrides",
    [
        pytest.param({**_COLOR_TERMINAL, "NO_COLOR": "1"}, id="no-color"),
        pytest.param({"TERM": "dumb", "NO_COLOR": None}, id="dumb-term"),
    ],
)
def test_public_seam_tty_matches_redirected_output_when_emphasis_is_off(
    overrides: dict[str, str | None],
) -> None:
    redirected = _run_redirected(_command("tty"), overrides=overrides)
    returncode, stdout, stderr = _run_pty(_command("tty"), overrides=overrides)

    assert returncode == redirected.returncode == 0
    assert stdout == redirected.stdout == _EXPECTED_REDIRECTED_STDOUT
    assert stderr == redirected.stderr == b""


@pytest.mark.parametrize(
    ("mode", "expected_stdout"),
    [
        pytest.param("fail", b"", id="nothing-accepted"),
        pytest.param(
            "prefix",
            b"a.txt" + _NATIVE_NEWLINE,
            id="accepted-prefix-preserved",
        ),
    ],
)
def test_public_seam_reports_other_stdout_failures(
    mode: str,
    expected_stdout: bytes,
) -> None:
    result = _run_redirected(_command(mode))

    assert result.returncode == 1
    assert result.stdout == expected_stdout
    assert result.stderr == _OUTPUT_ERROR


def test_output_failure_keeps_already_known_backend_diagnostics() -> None:
    result = _run_redirected(_command("runtime-and-fail"))

    assert result.returncode == 1
    assert result.stdout == b""
    assert result.stderr == (b"ls: memory:/missing: not found" + _NATIVE_NEWLINE + _OUTPUT_ERROR)
