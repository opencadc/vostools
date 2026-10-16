"""Shared subprocess scaffolding for the process-boundary tests."""

import errno
import os
import pty
import subprocess
import termios
from collections.abc import Mapping
from contextlib import suppress
from pathlib import Path

_REPO_ROOT = Path(__file__).resolve().parents[3]
_TIMEOUT = 5


def _environment(
    *,
    tracking_path: Path | None = None,
    tmpdir: Path | None = None,
    overrides: Mapping[str, str | None] | None = None,
) -> dict[str, str]:
    environment = os.environ.copy()
    environment.update(
        {
            "LANG": "C",
            "LC_ALL": "C",
            "PYTHONDONTWRITEBYTECODE": "1",
            "PYTHONUNBUFFERED": "1",
        }
    )
    for name, value in (overrides or {}).items():
        if value is None:
            environment.pop(name, None)
        else:
            environment[name] = value
    if tracking_path is not None:
        environment["FSS_CLI_CAT_PROCESS_TRACKING"] = str(tracking_path)
    if tmpdir is not None:
        environment["TMPDIR"] = str(tmpdir)
        environment["TEMP"] = str(tmpdir)
        environment["TMP"] = str(tmpdir)
    return environment


def _run_redirected(
    command: list[str],
    *,
    stdin: bytes | None = None,
    tracking_path: Path | None = None,
    tmpdir: Path | None = None,
    overrides: Mapping[str, str | None] | None = None,
) -> subprocess.CompletedProcess[bytes]:
    environment = _environment(tracking_path=tracking_path, tmpdir=tmpdir, overrides=overrides)
    if stdin is None:
        return subprocess.run(  # noqa: S603 - fixed interpreter and child source.
            command,
            cwd=_REPO_ROOT,
            env=environment,
            stdin=subprocess.DEVNULL,
            capture_output=True,
            timeout=_TIMEOUT,
            check=False,
        )
    return subprocess.run(  # noqa: S603 - fixed interpreter and child source.
        command,
        cwd=_REPO_ROOT,
        env=environment,
        input=stdin,
        capture_output=True,
        timeout=_TIMEOUT,
        check=False,
    )


def _run_pty(
    command: list[str],
    *,
    overrides: Mapping[str, str | None] | None = None,
) -> tuple[int, bytes, bytes]:
    master_fd, slave_fd = pty.openpty()
    try:
        attributes = termios.tcgetattr(slave_fd)
        attributes[1] &= ~termios.ONLCR
        attributes[3] &= ~termios.ECHO
        termios.tcsetattr(slave_fd, termios.TCSANOW, attributes)

        result = subprocess.run(  # noqa: S603 - fixed child command.
            command,
            cwd=_REPO_ROOT,
            env=_environment(overrides=overrides),
            stdin=subprocess.DEVNULL,
            stdout=slave_fd,
            stderr=subprocess.PIPE,
            timeout=_TIMEOUT,
            check=False,
        )
        chunks = [os.read(master_fd, 65536)]
        os.close(slave_fd)
        slave_fd = -1

        while True:
            try:
                chunk = os.read(master_fd, 65536)
            except OSError as error:
                if error.errno == errno.EIO:
                    break
                raise
            if not chunk:
                break
            chunks.append(chunk)
        return result.returncode, b"".join(chunks), result.stderr
    finally:
        for descriptor in (master_fd, slave_fd):
            if descriptor >= 0:
                with suppress(OSError):
                    os.close(descriptor)
