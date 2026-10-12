"""Invocation-owned async filesystem source lifecycle."""

from __future__ import annotations

import sys
from contextlib import AbstractAsyncContextManager
from types import TracebackType
from typing import TYPE_CHECKING, TypeAlias

import typer
from fsspec.asyn import AsyncFileSystem

from ._diagnostics import _render_diagnostic_prefix, _render_diagnostic_value

if TYPE_CHECKING:
    from collections.abc import Iterable, Mapping

    from fsspec import AbstractFileSystem

    from ._app import AsyncFilesystemSource

_ExcInfo: TypeAlias = tuple[
    type[BaseException] | None,
    BaseException | None,
    TracebackType | None,
]
_EMPTY_EXC_INFO: _ExcInfo = (None, None, None)


def _source_exception(
    command: str,
    name: str,
    stage: str,
    error: Exception,
) -> None:
    prefix = _render_diagnostic_prefix(command)
    rendered_name = _render_diagnostic_value(name)
    rendered_class = _render_diagnostic_value(type(error).__name__)
    rendered_message = _render_diagnostic_value(str(error))
    typer.echo(
        f"{prefix} {rendered_name}: source {stage} failure ({rendered_class}): {rendered_message}",
        err=True,
    )


def _render_exit_failures(
    command: str,
    failures: Iterable[tuple[str, Exception]],
    *,
    preserve_control: bool,
) -> BaseException | None:
    render_control = None
    for name, error in failures:
        try:
            _source_exception(command, name, "exit", error)
        except Exception:  # noqa: BLE001, PERF203, S110 - attempt every diagnostic.
            pass
        except BaseException as error:  # noqa: BLE001 - preserve control flow.
            if not preserve_control and render_control is None:
                render_control = error
    return render_control


class _SourceInvocation:
    """Acquire, expose, and release sources for one command invocation."""

    def __init__(
        self,
        command: str,
        sources: Mapping[str, AsyncFilesystemSource],
    ) -> None:
        self._command = command
        self._sources = sources
        self._entered: list[tuple[str, AbstractAsyncContextManager[AbstractFileSystem]]] = []
        self._failure_exc_info: _ExcInfo = _EMPTY_EXC_INFO

    async def acquire(
        self,
        names: Iterable[str],
    ) -> Mapping[str, AsyncFileSystem] | None:
        """Acquire distinct mapped names sequentially in the given order."""
        filesystems: dict[str, AsyncFileSystem] = {}
        for name in names:
            try:
                manager = self._sources[name]()
            except Exception as error:  # noqa: BLE001 - classify source failures.
                _source_exception(self._command, name, "factory", error)
                self._remember_failure(error)
                return None

            if not (
                isinstance(manager, AbstractAsyncContextManager)
                and callable(getattr(manager, "__aenter__", None))
                and callable(getattr(manager, "__aexit__", None))
            ):
                prefix = _render_diagnostic_prefix(self._command)
                rendered_name = _render_diagnostic_value(name)
                typer.echo(
                    f"{prefix} {rendered_name}: source factory returned incompatible async context manager",
                    err=True,
                )
                return None

            try:
                filesystem = await manager.__aenter__()
            except Exception as error:  # noqa: BLE001 - classify source failures.
                _source_exception(self._command, name, "entry", error)
                self._remember_failure(error)
                return None

            self._entered.append((name, manager))
            if not (
                isinstance(filesystem, AsyncFileSystem)
                and filesystem.async_impl is True
                and filesystem.asynchronous is True
            ):
                prefix = _render_diagnostic_prefix(self._command)
                rendered_name = _render_diagnostic_value(name)
                typer.echo(
                    f"{prefix} {rendered_name}: source yielded incompatible async filesystem",
                    err=True,
                )
                return None
            filesystems[name] = filesystem
        return filesystems

    def _remember_failure(self, error: Exception) -> None:
        self._failure_exc_info = (type(error), error, error.__traceback__)

    async def close(self, active_exc_info: _ExcInfo) -> bool:
        """Exit every entered source and preserve control-flow precedence."""
        exit_exc_info = active_exc_info if active_exc_info[1] is not None else self._failure_exc_info
        primary_control = (
            active_exc_info[1]
            if active_exc_info[1] is not None and not isinstance(active_exc_info[1], Exception)
            else None
        )
        cleanup_control = None
        exit_failures: list[tuple[str, Exception]] = []
        entered, self._entered = self._entered, []

        for name, manager in reversed(entered):
            try:
                await manager.__aexit__(*exit_exc_info)
            except Exception as error:  # noqa: BLE001, PERF203 - every exit runs.
                exit_failures.append((name, error))
            except BaseException as error:  # noqa: BLE001 - preserve control flow.
                if primary_control is None and cleanup_control is None:
                    cleanup_control = error

        render_control = _render_exit_failures(
            self._command,
            exit_failures,
            preserve_control=(primary_control is not None or cleanup_control is not None),
        )

        if primary_control is None:
            if cleanup_control is not None:
                raise cleanup_control
            if render_control is not None:
                raise render_control
        return bool(exit_failures)

    async def close_with_command_error(self, command_error: Exception | None) -> bool:
        """Close after the command, letting a captured command error take precedence.

        The command error is captured, not raised, so it never flows through
        ``__aexit__``. Fold it into the exit ``exc_info`` only when no live
        exception is propagating, or the live one is an ordinary ``Exception``.
        """
        active_exc_info: _ExcInfo = sys.exc_info()
        if command_error is not None and (active_exc_info[1] is None or isinstance(active_exc_info[1], Exception)):
            active_exc_info = (
                type(command_error),
                command_error,
                command_error.__traceback__,
            )
        return await self.close(active_exc_info)
