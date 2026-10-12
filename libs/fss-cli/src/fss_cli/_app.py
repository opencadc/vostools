"""Embedded Typer application for mapped async fsspec sources."""

from __future__ import annotations

import asyncio
from collections.abc import Callable, Coroutine, Mapping, Sequence
from contextlib import AbstractAsyncContextManager
from dataclasses import dataclass
from types import MappingProxyType
from typing import Annotated, Any, Literal, TypeAlias, TypedDict

import typer
from fsspec import AbstractFileSystem

from ._cat import _run_cat, _StdinOperand
from ._command import (
    _MappedOperand,
    _parse_mapped_operand,
    _usage_error,
)
from ._cp import _cp_plan, _run_cp
from ._diagnostics import _render_diagnostic_prefix, _render_diagnostic_value
from ._du import _DuRequest, _run_du
from ._find import _FindRequest, _run_find
from ._head_tail import _read_head, _read_tail, _run_byte_range
from ._info import _run_info
from ._ls import _LsRequest, _run_ls
from ._mkdir import _MkdirRequest, _run_mkdir
from ._mv import _plan_mv, _run_mv
from ._path import _has_dot_segment, _has_final_dot_segment, _is_root
from ._rm import _RmRequest, _run_rm
from ._rmdir import _run_rmdir
from ._size import _run_size
from ._stat import _run_stat
from ._test import _run_test
from ._tree import _run_tree, _TreeRequest
from ._unlink import _run_unlink

AsyncFilesystemSource: TypeAlias = Callable[[], AbstractAsyncContextManager[AbstractFileSystem]]


class RecursionCapabilities(TypedDict, total=False):
    """Application policy for recursive core commands."""

    copy: bool
    remove: bool


class AppCapabilities(TypedDict, total=False):
    """Application-level core command policy."""

    recursion: RecursionCapabilities


CommandCallback: TypeAlias = Callable[..., None]


@dataclass(frozen=True)
class CommandContext:
    """Immutable application context available to command callbacks."""

    sources: Mapping[str, AsyncFilesystemSource]


@dataclass(frozen=True)
class _Capabilities:
    recursive_copy: bool = True
    recursive_remove: bool = False


# Shared parameter annotations. Typer reads the metavar, option spellings, and
# help text from these aliases, so every command that takes the same operand or
# option shape stays spelled and documented identically.
_Operand: TypeAlias = Annotated[str, typer.Argument(metavar="name:/path")]
_Operands: TypeAlias = Annotated[list[str], typer.Argument(metavar="name:/path")]
_OptionalOperands: TypeAlias = Annotated[
    list[str] | None,
    typer.Argument(metavar="name:/path"),
]
_CatOperands: TypeAlias = Annotated[
    list[str] | None,
    typer.Argument(metavar="name:/path|-"),
]
_CpOperands: TypeAlias = Annotated[
    list[str],
    typer.Argument(metavar="SOURCE... DESTINATION"),
]
_MvOperands: TypeAlias = Annotated[
    list[str],
    typer.Argument(
        metavar="name:/path",
        help="One or more source files followed by one destination.",
    ),
]
_ByteCount: TypeAlias = Annotated[
    int,
    typer.Option("-c", metavar="N", min=0, help="Number of bytes to display."),
]
_MaxDepth: TypeAlias = Annotated[
    int | None,
    typer.Option(
        "--maxdepth",
        metavar="N",
        min=0,
        help="Descend at most N levels below the operand.",
    ),
]
_FindType: TypeAlias = Annotated[
    Literal["f", "d"],
    typer.Option("--type", metavar="f|d", help="Report files (f) or directories (d)."),
]
_AlmostAll: TypeAlias = Annotated[
    bool,
    typer.Option("-A", help="Include entries whose name begins with a dot."),
]
_LongListing: TypeAlias = Annotated[
    bool,
    typer.Option("-l", help="Use the long listing format."),
]
_HumanSizes: TypeAlias = Annotated[
    bool,
    typer.Option("-h", help="Print sizes in human-readable units."),
]
_Summarize: TypeAlias = Annotated[
    bool,
    typer.Option("-s", help="Print only the total for the operand."),
]
_CreateParents: TypeAlias = Annotated[
    bool,
    typer.Option("-p", help="Create missing parent directories."),
]
_CopyRecursive: TypeAlias = Annotated[
    bool,
    typer.Option("-R", "-r", help="Copy one directory and its descendants."),
]
_TestExists: TypeAlias = Annotated[
    bool,
    typer.Option("-e", help="Succeed when the operand exists."),
]
_TestDirectory: TypeAlias = Annotated[
    bool,
    typer.Option("-d", help="Succeed when the operand is a directory."),
]
_TestFile: TypeAlias = Annotated[
    bool,
    typer.Option("-f", help="Succeed when the operand is a regular file."),
]
# The `rm` help text is compatibility surface: with recursion disabled the
# rendered help must contain no `-R` or `-r` spelling at all.
_RmDirectory: TypeAlias = Annotated[
    bool,
    typer.Option("-d", help="Remove empty directories."),
]
_RmForce: TypeAlias = Annotated[
    bool,
    typer.Option("-f", help="Ignore missing operands."),
]
_RmVerbose: TypeAlias = Annotated[
    int,
    typer.Option("-v", count=True, help="Print removed operands."),
]
_RmRecursive: TypeAlias = Annotated[
    bool,
    typer.Option("-R", "-r", help="Remove directory trees with guarded traversal."),
]


def _snapshot_capabilities(capabilities: AppCapabilities | None) -> _Capabilities:
    if capabilities is None:
        return _Capabilities()
    if not isinstance(capabilities, Mapping):
        msg = "capabilities must be a mapping"
        raise TypeError(msg)
    for name in capabilities:
        if name != "recursion":
            msg = f"capabilities.{name}: unknown capability"
            raise ValueError(msg)

    recursion = capabilities.get("recursion", {})
    if not isinstance(recursion, Mapping):
        msg = "capabilities.recursion must be a mapping"
        raise TypeError(msg)
    for name in recursion:
        if name not in {"copy", "remove"}:
            msg = f"capabilities.recursion.{name}: unknown capability"
            raise ValueError(msg)
    for name in ("copy", "remove"):
        if name in recursion and type(recursion[name]) is not bool:
            msg = f"capabilities.recursion.{name} must be a bool"
            raise TypeError(msg)
    return _Capabilities(
        recursive_copy=recursion.get("copy", True),
        recursive_remove=recursion.get("remove", False),
    )


def _validate_source_name(name: object) -> None:
    if not isinstance(name, str):
        msg = "async filesystem source names must be strings"
        raise TypeError(msg)
    if not name or any(character in name for character in (":", "\0", "\n")):
        msg = "async filesystem source names must be non-empty and contain no colon, NUL, or newline"
        raise ValueError(msg)
    if name.startswith("-"):
        msg = "async filesystem source names must not start with '-'"
        raise ValueError(msg)


def _ensure_no_active_event_loop(command: str) -> None:
    try:
        asyncio.get_running_loop()
    except RuntimeError:
        return
    prefix = _render_diagnostic_prefix(command)
    typer.echo(
        f"{prefix} cannot run from an active event loop",
        err=True,
        color=True,
    )
    raise typer.Exit(1)


def _run_async_command(
    command: str,
    operation: Callable[[], Coroutine[Any, Any, None]],
) -> None:
    """Run one preflighted command coroutine from a synchronous callback."""
    _ensure_no_active_event_loop(command)
    asyncio.run(operation())


class App:
    """Embedded core commands plus explicitly selected extensions."""

    typer_app: typer.Typer

    def __init__(
        self,
        sources: Mapping[str, AsyncFilesystemSource],
        *,
        capabilities: AppCapabilities | None = None,
        extensions: Sequence[CommandCallback] = (),
    ) -> None:
        """Snapshot sources and register the requested command surface."""
        self._sources = MappingProxyType(dict(sources))
        if not self._sources:
            msg = "at least one async filesystem source is required"
            raise ValueError(msg)
        for name in self._sources:
            _validate_source_name(name)
        self._capabilities = _snapshot_capabilities(capabilities)

        self.typer_app = typer.Typer(add_completion=False)
        self._register_commands()
        for extension in extensions:
            self.typer_app.command()(extension)

    # Operand preflight shared by the command callbacks.

    def _mapped(self, command: str, spelling: str) -> _MappedOperand:
        """Parse one ``name:/path`` operand against the configured sources."""
        return _parse_mapped_operand(command, spelling, self._sources)

    def _mapped_all(
        self,
        command: str,
        spellings: Sequence[str],
    ) -> tuple[_MappedOperand, ...]:
        """Parse every ``name:/path`` operand in invocation order."""
        return tuple(self._mapped(command, spelling) for spelling in spellings)

    def _destructive_operand(self, command: str, spelling: str) -> _MappedOperand:
        """Parse one operand and reject roots and final dot segments."""
        operand = self._mapped(command, spelling)
        if _is_root(operand.path) or _has_final_dot_segment(operand.path):
            rendered = _render_diagnostic_value(spelling)
            _usage_error(command, f"{rendered}: rejected path")
        return operand

    # Command registration.

    def _register_commands(self) -> None:
        @self.typer_app.callback()
        def root(ctx: typer.Context) -> None:
            ctx.obj = CommandContext(self._sources)

        self._register_query_commands()
        self._register_listing_commands()
        self._register_read_commands()
        self._register_transfer_commands()
        self._register_mutation_commands()

    def _register_query_commands(self) -> None:
        """Register the metadata and predicate commands."""

        @self.typer_app.command()
        def info(operand: _Operand) -> None:
            """Display normalized file information."""
            mapped = self._mapped("info", operand)
            _run_async_command(
                "info",
                lambda: _run_info("info", mapped, self._sources),
            )

        @self.typer_app.command()
        def size(operands: _Operands) -> None:
            """Display exact file sizes."""
            mapped = self._mapped_all("size", operands)
            _run_async_command(
                "size",
                lambda: _run_size("size", mapped, self._sources),
            )

        @self.typer_app.command()
        def stat(operands: _Operands) -> None:
            """Display file status."""
            mapped = self._mapped_all("stat", operands)
            _run_async_command(
                "stat",
                lambda: _run_stat("stat", mapped, self._sources),
            )

        @self.typer_app.command()
        def test(
            operand: _Operand,
            *,
            exists: _TestExists = False,
            directory: _TestDirectory = False,
            file: _TestFile = False,
        ) -> None:
            """Evaluate a file predicate."""
            mapped = self._mapped("test", operand)
            selected: list[Literal["e", "d", "f"]] = [
                predicate
                for predicate, enabled in (
                    ("e", exists),
                    ("d", directory),
                    ("f", file),
                )
                if enabled
            ]
            if len(selected) != 1:
                _usage_error("test", "exactly one predicate selector is required")
            _run_async_command(
                "test",
                lambda: _run_test("test", selected[0], mapped, self._sources),
            )

    def _register_listing_commands(self) -> None:
        """Register the directory listing and traversal commands."""

        @self.typer_app.command()
        def ls(
            operands: _Operands,
            *,
            include_almost_all: _AlmostAll = False,
            long_listing: _LongListing = False,
            human_readable: _HumanSizes = False,
        ) -> None:
            """List directory contents."""
            self._listing(
                operands,
                include_almost_all=include_almost_all,
                long_listing=long_listing,
                human_readable=human_readable,
            )

        @self.typer_app.command()
        def du(
            operand: _Operand,
            *,
            summarize: _Summarize = False,
            human_readable: _HumanSizes = False,
        ) -> None:
            """Estimate file space usage."""
            mapped = self._mapped("du", operand)
            _run_async_command(
                "du",
                lambda: _run_du(
                    "du",
                    _DuRequest(
                        summarize=summarize,
                        human_readable=human_readable,
                        operand=mapped,
                    ),
                    self._sources,
                ),
            )

        @self.typer_app.command()
        def find(
            operand: _Operand,
            maxdepth: _MaxDepth = None,
            kind: _FindType = "f",
        ) -> None:
            """Find files recursively."""
            mapped = self._mapped("find", operand)
            _run_async_command(
                "find",
                lambda: _run_find(
                    "find",
                    _FindRequest(maxdepth=maxdepth, kind=kind, operand=mapped),
                    self._sources,
                ),
            )

        @self.typer_app.command()
        def tree(operand: _Operand, maxdepth: _MaxDepth = None) -> None:
            """Display a recursive directory tree."""
            mapped = self._mapped("tree", operand)
            _run_async_command(
                "tree",
                lambda: _run_tree(
                    "tree",
                    _TreeRequest(maxdepth=maxdepth, operand=mapped),
                    self._sources,
                ),
            )

    def _listing(
        self,
        operands: Sequence[str],
        *,
        include_almost_all: bool,
        long_listing: bool,
        human_readable: bool,
    ) -> None:
        """Preflight and run one ``ls`` invocation."""
        mapped = self._mapped_all("ls", operands)
        if human_readable and not long_listing:
            _usage_error("ls", "-h: requires long listing")
        _run_async_command(
            "ls",
            lambda: _run_ls(
                "ls",
                _LsRequest(
                    include_almost_all=include_almost_all,
                    long_listing=long_listing,
                    human_readable=human_readable,
                    operands=mapped,
                ),
                self._sources,
            ),
        )

    def _register_read_commands(self) -> None:
        """Register the byte-reading commands."""

        @self.typer_app.command()
        def head(operand: _Operand, count: _ByteCount) -> None:
            """Display leading bytes."""
            mapped = self._mapped("head", operand)
            _run_async_command(
                "head",
                lambda: _run_byte_range(
                    "head",
                    count,
                    mapped,
                    self._sources,
                    _read_head,
                ),
            )

        @self.typer_app.command()
        def tail(operand: _Operand, count: _ByteCount) -> None:
            """Display trailing bytes."""
            mapped = self._mapped("tail", operand)
            _run_async_command(
                "tail",
                lambda: _run_byte_range(
                    "tail",
                    count,
                    mapped,
                    self._sources,
                    _read_tail,
                ),
            )

        @self.typer_app.command()
        def cat(operands: _CatOperands = None) -> None:
            """Concatenate files to standard output."""
            parsed = tuple(
                _StdinOperand() if operand == "-" else _parse_mapped_operand("cat", operand, self._sources)
                for operand in operands or ("-",)
            )
            _run_async_command(
                "cat",
                lambda: _run_cat("cat", parsed, self._sources),
            )

    def _register_transfer_commands(self) -> None:
        """Register ``cp`` in its capability-selected form, plus ``mv``."""
        if self._capabilities.recursive_copy:

            @self.typer_app.command()
            def cp(operands: _CpOperands, *, recursive: _CopyRecursive = False) -> None:
                """Copy files or one directory."""
                self._copy(operands, recursive=recursive)

        else:

            @self.typer_app.command()
            def cp(operands: _CpOperands) -> None:
                """Copy one or more files."""
                self._copy(operands, recursive=False)

        @self.typer_app.command()
        def mv(operands: _MvOperands) -> None:
            """Move or rename files on one mapped filesystem."""
            if len(operands) < 2:  # noqa: PLR2004 - command arity.
                message = "requires at least one source and one destination"
                raise typer.BadParameter(message, param_hint="name:/path")
            plan = _plan_mv("mv", self._mapped_all("mv", operands))
            _run_async_command(
                "mv",
                lambda: _run_mv("mv", plan, self._sources),
            )

    def _copy(self, operands: Sequence[str], *, recursive: bool) -> None:
        """Preflight and run one shared ``cp`` invocation."""
        plan = _cp_plan("cp", tuple(operands), self._sources, recursive=recursive)
        _run_async_command("cp", lambda: _run_cp("cp", plan, self._sources))

    def _register_mutation_commands(self) -> None:
        """Register the namespace creation and removal commands."""

        @self.typer_app.command()
        def mkdir(operands: _Operands, *, parents: _CreateParents = False) -> None:
            """Create directories."""
            mapped = self._mapped_all("mkdir", operands)
            _run_async_command(
                "mkdir",
                lambda: _run_mkdir(
                    "mkdir",
                    _MkdirRequest(create_parents=parents, operands=mapped),
                    self._sources,
                ),
            )

        @self.typer_app.command()
        def rmdir(operands: _Operands) -> None:
            """Remove empty directories."""
            mapped = tuple(self._destructive_operand("rmdir", operand) for operand in operands)
            _run_async_command(
                "rmdir",
                lambda: _run_rmdir("rmdir", mapped, self._sources),
            )

        @self.typer_app.command()
        def unlink(operand: _Operand) -> None:
            """Remove a single file."""
            mapped = self._destructive_operand("unlink", operand)
            _run_async_command(
                "unlink",
                lambda: _run_unlink("unlink", mapped, self._sources),
            )

        if self._capabilities.recursive_remove:

            @self.typer_app.command(name="rm")
            def recursive_rm(
                operands: _OptionalOperands = None,
                *,
                directory: _RmDirectory = False,
                force: _RmForce = False,
                verbose: _RmVerbose = 0,
                recursive: _RmRecursive = False,
            ) -> None:
                """Remove files or directories with guarded -R or -r."""
                self._remove(
                    operands,
                    directory=directory,
                    force=force,
                    verbose=verbose,
                    recursive=recursive,
                )

        else:

            @self.typer_app.command(name="rm")
            def rm(
                operands: _OptionalOperands = None,
                *,
                directory: _RmDirectory = False,
                force: _RmForce = False,
                verbose: _RmVerbose = 0,
            ) -> None:
                """Remove files; -d removes empty directories."""
                self._remove(
                    operands,
                    directory=directory,
                    force=force,
                    verbose=verbose,
                )

    def _remove(
        self,
        operands: Sequence[str] | None,
        *,
        directory: bool,
        force: bool,
        verbose: int,
        recursive: bool = False,
    ) -> None:
        """Preflight option combinations and paths, then run one ``rm``."""
        if verbose > 1:
            _usage_error("rm", "-v: may be supplied once")
        if directory and (force or verbose or recursive):
            _usage_error("rm", "-d: cannot combine with other options")
        if not recursive and force and verbose:
            _usage_error("rm", "-f: cannot combine with -v")
        spellings = list(operands or [])
        if not spellings and not force:
            _usage_error("rm", "missing mapped filesystem operand")
        mapped = self._mapped_all("rm", spellings)
        for spelling, operand in zip(spellings, mapped, strict=True):
            rejected = _is_root(operand.path) or (
                _has_dot_segment(operand.path) if recursive else _has_final_dot_segment(operand.path)
            )
            if rejected:
                rendered = _render_diagnostic_value(spelling)
                _usage_error("rm", f"{rendered}: rejected path")
        _run_async_command(
            "rm",
            lambda: _run_rm(
                "rm",
                _RmRequest(
                    force=force,
                    directory=directory,
                    recursive=recursive,
                    verbose=bool(verbose),
                    operands=mapped,
                ),
                self._sources,
            ),
        )
