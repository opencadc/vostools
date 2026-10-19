"""Process-boundary evidence for mapped-file ``cat`` binary output."""

import os
import subprocess
import sys
import tempfile
from pathlib import Path

import pytest

from ._process_support import (
    _REPO_ROOT,
    _TIMEOUT,
    _environment,
    _run_redirected,
)

_SIGPIPE_EXIT = 141  # 128 + SIGPIPE (13): sole broken pipe on stdout.
_NATIVE_NEWLINE = os.linesep.encode()
_OUTPUT_ERROR = b"cat: output: output failure (OSError): disk\\\\bad\\x0aline" + _NATIVE_NEWLINE
_CHILD_PATH = Path(__file__).with_name("_cat_process_child.py")


def _command(mode: str, *operands: str) -> list[str]:
    return [sys.executable, str(_CHILD_PATH), mode, "cat", *operands]


def _run(
    mode: str,
    *operands: str,
    stdin: bytes | None = None,
    tracking_path: Path | None = None,
    tmpdir: Path | None = None,
) -> subprocess.CompletedProcess[bytes]:
    return _run_redirected(
        _command(mode, *operands),
        stdin=stdin,
        tracking_path=tracking_path,
        tmpdir=tmpdir,
    )


def _run_broken_stdout_pipe(
    mode: str,
    *operands: str,
    stdin: bytes | None = None,
    tracking_path: Path | None = None,
    tmpdir: Path | None = None,
) -> subprocess.CompletedProcess[bytes]:
    read_fd, write_fd = os.pipe()
    os.close(read_fd)
    environment = _environment(tracking_path=tracking_path, tmpdir=tmpdir)
    try:
        return subprocess.run(  # noqa: S603 - fixed child command.
            _command(mode, *operands),
            cwd=_REPO_ROOT,
            env=environment,
            input=stdin,
            stdout=write_fd,
            stderr=subprocess.PIPE,
            timeout=_TIMEOUT,
            check=False,
        )
    finally:
        os.close(write_fd)


def test_public_seam_cat_emits_all_byte_values() -> None:
    result = _run("bytes", "memory:/bytes")

    assert result.returncode == 0
    assert result.stdout == bytes(range(256))
    assert result.stderr == b""


def test_public_seam_cat_emits_empty_content() -> None:
    result = _run("empty", "memory:/empty")

    assert result.returncode == 0
    assert result.stdout == b""
    assert result.stderr == b""


def test_public_seam_cat_broken_pipe_is_silent_runtime_failure() -> None:
    result = _run_broken_stdout_pipe("normal", "memory:/docs")

    assert result.returncode == _SIGPIPE_EXIT
    assert result.stderr == b""


@pytest.mark.parametrize(
    ("mode", "operands", "stdin", "expected_stdout"),
    [
        pytest.param(
            "stdin-leading-broken",
            ("-", "memory:/docs"),
            b"stdin-bytes",
            b"",
            id="leading",
        ),
        pytest.param(
            "stdin-middle-broken",
            ("memory:/left", "-", "memory:/right"),
            b"S",
            b"",
            id="middle",
        ),
        pytest.param(
            "stdin-trailing-broken",
            ("memory:/docs", "-"),
            b"stdin-bytes",
            b"",
            id="trailing",
        ),
    ],
)
def test_public_seam_cat_broken_pipe_during_stdin_at_each_position_is_silent(
    mode: str,
    operands: tuple[str, ...],
    stdin: bytes,
    expected_stdout: bytes,
) -> None:
    with tempfile.TemporaryDirectory() as tmpdir:
        with tempfile.NamedTemporaryFile(delete=False) as tracking:
            tracking_path = Path(tracking.name)
        try:
            result = _run_broken_stdout_pipe(
                mode,
                *operands,
                stdin=stdin,
                tracking_path=tracking_path,
                tmpdir=Path(tmpdir),
            )
            tracking_events = tracking_path.read_text(encoding="ascii").splitlines()
        finally:
            tracking_path.unlink(missing_ok=True)

        assert result.returncode == _SIGPIPE_EXIT
        assert result.stdout is None or result.stdout == expected_stdout
        assert result.stderr == b""
        assert tracking_events[0] == "source-enter"
        assert tracking_events[-1] == "source-exit"
        assert "get-file:/right" not in tracking_events
        assert list(Path(tmpdir).iterdir()) == []


@pytest.mark.parametrize(
    ("mode", "operands", "stdin", "expected_stdout"),
    [
        pytest.param(
            "stdin-leading-prefix",
            ("-", "memory:/docs"),
            b"abcd",
            b"ab",
            id="leading",
        ),
        pytest.param(
            "stdin-middle-prefix",
            ("memory:/left", "-", "memory:/right"),
            b"mid",
            b"Lm",
            id="middle",
        ),
        pytest.param(
            "stdin-trailing-prefix",
            ("memory:/docs", "-"),
            b"tail",
            b"payloadta",
            id="trailing",
        ),
    ],
)
def test_public_seam_cat_prefix_stdout_failure_during_stdin_at_each_position(
    mode: str,
    operands: tuple[str, ...],
    stdin: bytes,
    expected_stdout: bytes,
) -> None:
    with tempfile.TemporaryDirectory() as tmpdir:
        with tempfile.NamedTemporaryFile(delete=False) as tracking:
            tracking_path = Path(tracking.name)
        try:
            result = _run(
                mode,
                *operands,
                stdin=stdin,
                tracking_path=tracking_path,
                tmpdir=Path(tmpdir),
            )
            tracking_events = tracking_path.read_text(encoding="ascii").splitlines()
        finally:
            tracking_path.unlink(missing_ok=True)

        assert result.returncode == 1
        assert result.stdout == expected_stdout
        assert result.stderr == _OUTPUT_ERROR
        assert tracking_events[0] == "source-enter"
        assert tracking_events[-1] == "source-exit"
        assert "get-file:/right" not in tracking_events
        assert list(Path(tmpdir).iterdir()) == []


@pytest.mark.parametrize(
    ("mode", "operands"),
    [
        pytest.param("fail", ("memory:/prefix",), id="nothing-accepted"),
        pytest.param("prefix", ("memory:/prefix",), id="accepted-prefix-preserved"),
    ],
)
def test_public_seam_cat_reports_mapped_stdout_failures(
    mode: str,
    operands: tuple[str, ...],
) -> None:
    expected_stdout = b"" if mode == "fail" else b"abc"
    result = _run(mode, *operands)

    assert result.returncode == 1
    assert result.stdout == expected_stdout
    assert result.stderr == _OUTPUT_ERROR


def test_cat_output_failure_keeps_already_known_backend_diagnostics() -> None:
    result = _run("runtime-and-fail", "memory:/missing", "memory:/docs")

    assert result.returncode == 1
    assert result.stdout == b""
    assert result.stderr == (b"cat: memory:/missing: not found" + _NATIVE_NEWLINE + _OUTPUT_ERROR)


def test_public_seam_cat_operand_free_reads_binary_stdin_pipe() -> None:
    payload = b"\xff\xfe\0pipe-stdin"
    result = _run("stdin", stdin=payload)

    assert result.returncode == 0
    assert result.stdout == payload
    assert result.stderr == b""


def test_public_seam_cat_preserves_file_stdin_file_order_over_pipe() -> None:
    result = _run(
        "mixed",
        "memory:/left",
        "-",
        "memory:/right",
        stdin=b"S",
    )

    assert result.returncode == 0
    assert result.stdout == b"LSR"
    assert result.stderr == b""


def test_public_seam_cat_repeated_dash_second_sees_eof_on_pipe() -> None:
    payload = b"once-only"
    result = _run("repeat-dash", "-", "-", stdin=payload)

    assert result.returncode == 0
    assert result.stdout == payload
    assert result.stderr == b""


def test_public_seam_cat_broken_pipe_during_stdin_is_silent() -> None:
    result = _run_broken_stdout_pipe("stdin", stdin=b"stdin-bytes")

    assert result.returncode == _SIGPIPE_EXIT
    assert result.stderr == b""
