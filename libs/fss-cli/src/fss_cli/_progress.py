"""One shared Rich progress display for a ``cp`` invocation."""

from __future__ import annotations

import os
import sys
from contextlib import contextmanager, redirect_stderr, suppress
from typing import TYPE_CHECKING

from fsspec.callbacks import DEFAULT_CALLBACK, Callback
from rich import progress as rich_progress
from rich.console import Console
from rich.file_proxy import FileProxy
from rich.markup import escape

from ._diagnostics import _render_diagnostic_value

if TYPE_CHECKING:
    from collections.abc import Generator


def _draws_progress() -> bool:
    """Draw only on a terminal stderr, and never when ``NO_COLOR`` is set to a value.

    Rich's own terminal detection is not used: ``FORCE_COLOR`` makes it treat
    a pipe as a terminal, and a pipe must never receive a bar.
    """
    if os.environ.get("NO_COLOR"):
        return False
    try:
        return sys.stderr.isatty()
    except (AttributeError, ValueError):
        return False


class _StderrProxy(FileProxy):
    """Rich's line proxy, reporting the text attributes click checks before writing to a stream.

    Without them click treats the proxy as incompatible and writes to the
    proxied file's binary buffer, through the live bars.
    """

    @property
    def encoding(self) -> str:
        return getattr(self.rich_proxied_file, "encoding", None) or "utf-8"

    @property
    def errors(self) -> str:
        return getattr(self.rich_proxied_file, "errors", None) or "strict"


class _TaskCallback(Callback):
    """Mirror one fsspec transfer's byte counts onto a Rich task."""

    def __init__(self, progress: rich_progress.Progress, task: rich_progress.TaskID) -> None:
        super().__init__()
        self._progress = progress
        self._task = task

    def call(self, hook_name: str | None = None, **kwargs: object) -> None:
        del hook_name, kwargs
        with suppress(KeyError):  # A backend reporting after its hook returned.
            self._progress.update(self._task, total=self.size, completed=self.value, visible=True)


class _Transfer:
    """One file's bar: hidden until its backend reports bytes, removed by ``finish``."""

    def __init__(self, progress: rich_progress.Progress | None) -> None:
        self._progress = progress
        self._task: rich_progress.TaskID | None = None

    def phase(self, verb: str, label: str) -> Callback:
        """Point the bar at the next hook call and return that call's callback."""
        progress = self._progress
        if progress is None:
            return DEFAULT_CALLBACK
        description = f"{verb} {escape(_render_diagnostic_value(label))}"
        if self._task is None:
            self._task = progress.add_task(description, total=None, visible=False)
        else:
            progress.reset(self._task, total=None, completed=0, visible=False, description=description)
        return _TaskCallback(progress, self._task)

    def finish(self) -> None:
        if self._progress is not None and self._task is not None:
            self._progress.remove_task(self._task)
            # Lines printed next redraw the last rendered frame, which still holds this bar.
            self._progress.refresh()
        self._task = None


class _TransferDisplay:
    """The invocation's bars, or none when stderr is not a terminal."""

    def __init__(self, progress: rich_progress.Progress | None = None) -> None:
        self._progress = progress

    @contextmanager
    def transfer(self) -> Generator[_Transfer, None, None]:
        transfer = _Transfer(self._progress)
        try:
            yield transfer
        finally:
            transfer.finish()


_NO_DISPLAY = _TransferDisplay()


@contextmanager
def _transfer_display() -> Generator[_TransferDisplay, None, None]:
    """Run one ``cp`` invocation under a shared stderr ``Progress`` when bars are drawn.

    Stderr writes during the invocation go through Rich so outcome lines print
    above the live bars instead of through them.
    """
    if not _draws_progress():
        yield _NO_DISPLAY
        return
    console = Console(file=sys.stderr)
    progress = rich_progress.Progress(
        rich_progress.TextColumn("[bold blue]{task.description}"),
        rich_progress.BarColumn(bar_width=None),
        rich_progress.TimeRemainingColumn(),
        console=console,
        transient=True,
        redirect_stdout=False,
        redirect_stderr=False,
    )
    with progress, redirect_stderr(_StderrProxy(console, sys.stderr)):
        yield _TransferDisplay(progress)
