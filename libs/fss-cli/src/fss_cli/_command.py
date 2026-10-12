"""Shared execution support for mapped-source command modules."""

from __future__ import annotations

import asyncio
import inspect
import locale
import sys
from contextlib import suppress
from dataclasses import dataclass
from typing import (
    TYPE_CHECKING,
    Literal,
    NoReturn,
    Protocol,
    TypeAlias,
    TypeGuard,
    TypeVar,
    cast,
)

import typer

from ._diagnostics import _render_diagnostic_prefix, _render_diagnostic_value
from ._sources import _SourceInvocation

if TYPE_CHECKING:
    from collections.abc import (
        Awaitable,
        Callable,
        Collection,
        Iterable,
        Mapping,
        Sequence,
    )

    from fsspec.asyn import AsyncFileSystem

    from ._app import AsyncFilesystemSource
    from ._recursive_rm import _RecursiveRmFailure

# 128 + SIGPIPE (13): lets pipeline consumers distinguish a closed reader from
# an ordinary command failure when the broken pipe is the sole failure.
_BROKEN_PIPE_EXIT_CODE = 141
_ResultT = TypeVar("_ResultT")


def _usage_error(command: str, diagnostic: str) -> NoReturn:
    """Emit one stable usage diagnostic on stderr and exit ``2``."""
    prefix = _render_diagnostic_prefix(command)
    typer.echo(f"{prefix} {diagnostic}", err=True, color=True)
    raise typer.Exit(2)


@dataclass(frozen=True)
class _MappedOperand:
    """A parsed ``name:/path`` operand selecting one mapped source."""

    spelling: str
    name: str
    path: str


class _BinaryWriter(Protocol):
    """The write/flush surface of a binary stdout stream."""

    def write(self, data: bytes) -> int: ...

    def flush(self) -> None: ...


def _binary_stdout() -> _BinaryWriter:
    """Return the process binary stdout buffer, or raise if unavailable."""
    buffer = getattr(sys.stdout, "buffer", None)
    if buffer is None:
        message = "stdout has no binary buffer"
        raise OSError(message)
    return buffer


def _write_binary(stdout: _BinaryWriter, payload: bytes) -> None:
    """Write one complete byte payload or reject a short write."""
    written = stdout.write(payload)
    if written != len(payload):
        message = "short write"
        raise OSError(message)


# The result shapes a command can reject without a backend exception. Commands
# translate these into their own diagnostic vocabulary when rendering.
_Incompatible: TypeAlias = Literal["directory", "result", "same_path"]


@dataclass(frozen=True)
class _Failure:
    """One operand-scoped command failure.

    ``backend_error`` carries an exception raised by a filesystem hook;
    ``incompatible`` marks a hook that returned successfully with a result the
    command's contract rejects. ``uncertain`` marks a failure observed after a
    mutation was dispatched, so the remote state is not known to be unchanged.
    """

    operand: _MappedOperand
    backend_error: Exception | None = None
    incompatible: _Incompatible | None = None
    uncertain: bool = False


# Commands that need to tell apart the code path a failure came from subclass
# `_Failure` as a tag; the shared helpers stay usable through this bound.
_FailureT = TypeVar("_FailureT", bound=_Failure)


class _CommandFailureError(Exception):
    """One expected command or output failure crossing the invocation boundary."""

    def __init__(
        self,
        operand: _MappedOperand | None = None,
        error: Exception | None = None,
        *,
        render: bool = True,
        propagate: Exception | None = None,
    ) -> None:
        self.operand = operand
        self.error = error
        self.render = render
        self.propagate = propagate


async def _drain_current_operation(operation: Awaitable[_ResultT]) -> _ResultT:
    """Drain one started operation before propagating caller control flow."""

    async def capture() -> tuple[BaseException | None, _ResultT | None]:
        try:
            return None, await operation
        except BaseException as error:  # noqa: BLE001 - preserve exact control flow.
            return error, None

    task = asyncio.create_task(capture())
    try:
        error, result = await asyncio.shield(task)
    except BaseException:
        while not task.done():
            with suppress(BaseException):
                await asyncio.shield(task)
        with suppress(BaseException):
            task.result()
        raise
    if error is not None:
        raise error
    return cast("_ResultT", result)


async def _call(
    filesystem: AsyncFileSystem,
    operation: str,
    *args: object,
    **kwargs: object,
) -> object:
    """Await one named async hook, refusing absent or synchronous forms."""
    method = getattr(filesystem, operation, None)
    if not callable(method):
        raise NotImplementedError
    result = method(*args, **kwargs)
    if not inspect.isawaitable(result):
        raise NotImplementedError
    return await _drain_current_operation(result)


def _render_operand_diagnostic(
    command: str,
    operand: _MappedOperand,
    category: str,
) -> None:
    prefix = _render_diagnostic_prefix(command)
    rendered_operand = _render_diagnostic_value(operand.spelling)
    typer.echo(f"{prefix} {rendered_operand}: {category}", err=True, color=True)


def _render_failure(command: str, failure: _Failure) -> None:
    if failure.backend_error is None:
        _render_operand_diagnostic(command, failure.operand, "incompatible result")
    else:
        _render_backend_failure(command, failure.operand, failure.backend_error)


def _render_backend_failure(
    command: str,
    operand: _MappedOperand,
    error: Exception,
) -> None:
    _render_operand_diagnostic(command, operand, _backend_category(error))


def _backend_category(error: Exception) -> str:
    for error_type, category in (
        (FileNotFoundError, "not found"),
        (FileExistsError, "file exists"),
        (PermissionError, "permission denied"),
        (IsADirectoryError, "is a directory"),
        (NotADirectoryError, "not a directory"),
        (NotImplementedError, "unsupported operation"),
    ):
        if isinstance(error, error_type):
            return category
    rendered_class = _render_diagnostic_value(type(error).__name__)
    rendered_message = _render_diagnostic_value(str(error))
    return f"backend failure ({rendered_class}): {rendered_message}"


def _first_backend_error(
    failures: Iterable[_Failure | _RecursiveRmFailure],
) -> Exception | None:
    """Return the first backend exception among ``failures``, if any."""
    return next(
        (failure.backend_error for failure in failures if failure.backend_error is not None),
        None,
    )


def _raise_operand_failures(
    command: str,
    failures: Sequence[_FailureT],
    render: Callable[[str, _FailureT], None],
) -> NoReturn:
    """Render every operand failure, then fail the invocation exactly once.

    An exception escaping ``render`` (an output failure) propagates instead of
    the command failure, but the invocation still reports the backend error it
    had already observed.
    """
    backend_error = _first_backend_error(failures)
    try:
        for failure in failures:
            render(command, failure)
    except Exception as error:
        raise _CommandFailureError(
            error=backend_error,
            render=False,
            propagate=error,
        ) from error
    raise _CommandFailureError(error=backend_error, render=False)


def _render_output_failure(command: str, error: Exception) -> None:
    prefix = _render_diagnostic_prefix(command)
    rendered_class = _render_diagnostic_value(type(error).__name__)
    rendered_message = _render_diagnostic_value(str(error))
    typer.echo(
        f"{prefix} output: output failure ({rendered_class}): {rendered_message}",
        err=True,
        color=True,
    )


async def _run_mapped_command(
    command: str,
    operands: tuple[_MappedOperand, ...],
    sources: Mapping[str, AsyncFilesystemSource],
    operation: Callable[[Mapping[str, AsyncFileSystem]], Awaitable[None]],
    *,
    broken_pipe_exit_code: int = _BROKEN_PIPE_EXIT_CODE,
) -> None:
    """Acquire referenced sources, run one command, and own final status."""
    invocation = _SourceInvocation(command, sources)
    acquired = False
    failure: _CommandFailureError | None = None
    try:
        filesystems = await invocation.acquire(tuple(dict.fromkeys(operand.name for operand in operands)))
        acquired = filesystems is not None
        if filesystems is not None:
            await operation(filesystems)
    except _CommandFailureError as error:
        failure = error
        if error.render and error.operand is not None:
            _render_failure(command, _Failure(error.operand, error.error))
        elif (
            error.render
            and error.error is not None
            and not isinstance(
                error.error,
                BrokenPipeError,
            )
        ):
            _render_output_failure(command, error.error)
    finally:
        cleanup_failed = await invocation.close_with_command_error(failure.error if failure is not None else None)

    if failure is not None and failure.propagate is not None:
        raise failure.propagate
    if not acquired or failure is not None or cleanup_failed:
        if failure is not None and isinstance(failure.error, BrokenPipeError) and not cleanup_failed:
            raise typer.Exit(broken_pipe_exit_code)
        raise typer.Exit(1)


async def _run_single_operand_text(
    command: str,
    operand: _MappedOperand,
    sources: Mapping[str, AsyncFilesystemSource],
    operation: Callable[[AsyncFileSystem], Awaitable[str | _Failure]],
) -> None:
    """Run one mapped async operation with buffered text output and cleanup."""

    async def execute(filesystems: Mapping[str, AsyncFileSystem]) -> None:
        result = await operation(filesystems[operand.name])
        if isinstance(result, _Failure):
            raise _CommandFailureError(operand, result.backend_error)
        if not result:
            return
        try:
            typer.echo(result, nl=False, color=True)
        except BrokenPipeError as error:
            raise _CommandFailureError(error=error, render=False) from error
        except Exception as error:
            raise _CommandFailureError(error=error) from error

    await _run_mapped_command(
        command,
        (operand,),
        sources,
        execute,
        broken_pipe_exit_code=1,
    )


def _collate(value: str) -> tuple[str, str]:
    """Return the locale-aware sort key used for every ordered CLI listing.

    The raw value breaks ties so that strings the current locale considers
    equal still order deterministically.
    """
    return locale.strxfrm(value), value


def _valid_size(value: object) -> TypeGuard[int]:
    """Accept only an exact non-negative ``int`` byte count.

    ``type(...) is int`` rather than ``isinstance``: ``bool`` subclasses
    ``int``, so a backend returning ``True`` would otherwise pass as size 1.
    """
    return type(value) is int and value >= 0


def _parse_mapped_operand(
    command: str,
    argument: str,
    known_names: Collection[str],
) -> _MappedOperand:
    """Parse and validate one ``name:/path`` operand against the known sources."""
    name, separator, path = argument.partition(":")
    if not name or not separator or not path.startswith("/") or "\0" in argument or "\n" in argument:
        rendered = _render_diagnostic_value(argument)
        _usage_error(command, f"{rendered}: invalid mapped filesystem operand")

    if name not in known_names:
        rendered_operand = _render_diagnostic_value(argument)
        rendered_names = ", ".join(
            _render_diagnostic_value(candidate) for candidate in sorted(known_names, key=_collate)
        )
        _usage_error(
            command,
            f"{rendered_operand}: unknown filesystem (known: {rendered_names})",
        )

    return _MappedOperand(spelling=argument, name=name, path=path)
