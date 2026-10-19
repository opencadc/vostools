"""Backend-neutral copy dispatch and owned staging shared by both copy commands."""

from __future__ import annotations

import inspect
import os
import tempfile
from contextlib import suppress
from pathlib import Path
from typing import TYPE_CHECKING

from ._command import _call

if TYPE_CHECKING:
    from collections.abc import Callable

    from fsspec.asyn import AsyncFileSystem
    from fsspec.callbacks import Callback


async def copy_on_backend(
    filesystem: AsyncFileSystem, source: str, destination: str, callback: Callable[[], Callback]
) -> bool:
    """Try the copy hook; only an explicitly unsupported operation permits staging."""
    try:
        parameters = inspect.signature(filesystem._cp_file).parameters
    except (TypeError, ValueError):
        parameters = {}
    kwargs = {"callback": callback()} if "callback" in parameters else {}
    try:
        await _call(filesystem, "_cp_file", source, destination, **kwargs)
    except NotImplementedError:
        return False
    return True


def create_staging(prefix: str) -> str:
    """Create an owned staging file, cleaning up if descriptor handoff fails."""
    descriptor, path = tempfile.mkstemp(prefix=prefix)
    try:
        os.close(descriptor)
    except Exception:
        remove_staging(path)
        raise
    except BaseException:
        discard_staging(path)
        raise
    return path


def remove_staging(path: str) -> Exception | None:
    """Return an ordinary cleanup failure without masking the transfer result."""
    try:
        Path(path).unlink()
    except FileNotFoundError:
        return None
    except Exception as error:  # noqa: BLE001 - caller owns cleanup diagnostics.
        return error
    return None


def discard_staging(path: str) -> None:
    """Clean up while preserving the caller's active control-flow exception."""
    with suppress(BaseException):
        remove_staging(path)
