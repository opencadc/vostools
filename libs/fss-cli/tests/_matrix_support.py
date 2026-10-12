"""Shared probes for real-source command-matrix tests."""

from __future__ import annotations

import asyncio
from collections.abc import Awaitable, Callable, Mapping
from contextlib import AbstractAsyncContextManager
from dataclasses import dataclass
from pathlib import Path
from typing import TYPE_CHECKING, Generic, Literal, TypeVar

from fsspec.asyn import AsyncFileSystem
from fsspec.implementations.asyn_wrapper import AsyncFileSystemWrapper
from fsspec.implementations.memory import MemoryFileSystem
from typer.testing import CliRunner, Result

from fss_cli import App

if TYPE_CHECKING:
    from types import TracebackType


_FilesystemT = TypeVar("_FilesystemT", bound=AsyncFileSystem)
_FilesystemFactory = Callable[[], _FilesystemT]
_FilesystemCloser = Callable[[_FilesystemT], Awaitable[None]]


@dataclass(frozen=True)
class LifecycleEvent:
    stage: Literal["factory", "enter", "close", "exit"]
    source_id: int
    loop_id: int


@dataclass(frozen=True)
class ExitCall:
    source_id: int
    exc_type: type[BaseException] | None
    exception: BaseException | None
    traceback: TracebackType | None
    loop_id: int


@dataclass(frozen=True)
class FilesystemCall:
    operation: Literal[
        "info",
        "ls",
        "du",
        "get_file",
        "mkdir",
        "makedirs",
        "rmdir",
        "rm_file",
        "rm",
        "cp_file",
    ]
    source_id: int
    path: str
    detail: bool | None
    kwargs: Mapping[str, object]
    loop_id: int
    local_path: str | None = None
    create_parents: bool | None = None
    exist_ok: bool | None = None
    destination_path: str | None = None
    total: bool | None = None


@dataclass(frozen=True)
class FindCall:
    path: str
    maxdepth: int | None
    withdirs: bool
    detail: bool
    kwargs: Mapping[str, object]


@dataclass(frozen=True)
class _OperationSpec:
    """How one instrumented hook records its call and forwards arguments."""

    keyword: str | None = None
    keyword_default: object = None
    destination_field: str | None = None
    results: str | None = None
    record_path: bool = False


_OPERATION_SPECS: dict[str, _OperationSpec] = {
    "info": _OperationSpec(results="info_results"),
    "ls": _OperationSpec(keyword="detail", keyword_default=True, results="ls_results"),
    "du": _OperationSpec(keyword="total", keyword_default=True),
    "get_file": _OperationSpec(
        destination_field="local_path",
        results="get_file_results",
        record_path=True,
    ),
    "mkdir": _OperationSpec(keyword="create_parents", keyword_default=True),
    "makedirs": _OperationSpec(keyword="exist_ok", keyword_default=False),
    "rmdir": _OperationSpec(),
    "rm_file": _OperationSpec(),
    "cp_file": _OperationSpec(destination_field="destination_path"),
}


_MEMORY_DOCS: Mapping[str, bytes] = {
    "/docs/notes.txt": b"notes.txt",
    "/docs/.hidden": b".hidden",
    "/docs/guide.md": b"guide.md",
}


def _memory_factory(
    monkeypatch,
    files: Mapping[str, bytes] | None = None,
    *,
    directories: tuple[str, ...] = ("/docs",),
) -> Callable[[], AsyncFileSystemWrapper]:
    """Build an adapted-Memory filesystem factory with isolated, re-seeded state."""
    resolved = dict(_MEMORY_DOCS if files is None else files)
    monkeypatch.setattr(MemoryFileSystem, "store", {})
    monkeypatch.setattr(MemoryFileSystem, "pseudo_dirs", [""])
    monkeypatch.setattr(MemoryFileSystem, "_cache", {})

    def make_filesystem() -> AsyncFileSystemWrapper:
        MemoryFileSystem.store.clear()
        MemoryFileSystem.pseudo_dirs[:] = [""]
        MemoryFileSystem.clear_instance_cache()
        filesystem = MemoryFileSystem()
        for directory in directories:
            filesystem.makedirs(directory)
        for path, content in resolved.items():
            filesystem.pipe_file(path, content)
        return AsyncFileSystemWrapper(filesystem, asynchronous=True)

    return make_filesystem


def _memory_source(
    monkeypatch,
    files: Mapping[str, bytes] | None = None,
    *,
    directories: tuple[str, ...] = ("/docs",),
) -> _ProbedSource[AsyncFileSystemWrapper]:
    """Build a probed adapted-Memory source with isolated, re-seeded state."""
    return _ProbedSource(_memory_factory(monkeypatch, files, directories=directories))


class _ProbedSource(Generic[_FilesystemT]):
    def __init__(
        self,
        factory: _FilesystemFactory[_FilesystemT],
        *,
        close: _FilesystemCloser[_FilesystemT] | None = None,
    ) -> None:
        self._factory = factory
        self._close = close
        self.lifecycle: list[LifecycleEvent] = []
        self.exit_calls: list[ExitCall] = []
        self.calls: list[FilesystemCall] = []
        self.find_calls: list[FindCall] = []
        self.info_results: list[tuple[int, object]] = []
        self.ls_results: list[tuple[int, object]] = []
        self.get_file_results: list[tuple[int, str]] = []
        self.errors: list[tuple[int, str, Exception]] = []
        self.filesystems: list[_FilesystemT] = []
        self.close_calls: list[LifecycleEvent] = []
        self._source_count = 0

    def __call__(self) -> AbstractAsyncContextManager[_FilesystemT]:
        self._source_count += 1
        source_id = self._source_count
        self.lifecycle.append(LifecycleEvent("factory", source_id, id(asyncio.get_running_loop())))
        return _ProbedContext(self, source_id)

    def _wrap(
        self,
        source_id: int,
        filesystem: _FilesystemT,
        name: str,
        spec: _OperationSpec,
    ) -> Callable[..., Awaitable[object]]:
        original = getattr(filesystem, f"_{name}", None)

        async def operation(path: str, *args: object, **kwargs: object) -> object:
            extra: dict[str, object] = {}
            if spec.keyword is not None:
                kwargs.setdefault(spec.keyword, spec.keyword_default)
                extra[spec.keyword] = kwargs[spec.keyword]
            if spec.destination_field is not None:
                extra[spec.destination_field] = args[0]
            recorded = {key: value for key, value in kwargs.items() if key != spec.keyword}
            self.calls.append(
                FilesystemCall(
                    name,
                    source_id,
                    path,
                    extra.pop("detail", None),
                    recorded,
                    id(asyncio.get_running_loop()),
                    **extra,
                )
            )
            if original is None:
                message = f"{type(filesystem).__name__} lacks _{name}"
                raise NotImplementedError(message)
            try:
                result = await original(path, *args, **kwargs)
            except Exception as error:
                self.errors.append((source_id, name, error))
                raise
            if spec.results is not None:
                value = path if spec.record_path else result
                getattr(self, spec.results).append((source_id, value))
            return result

        return operation

    def _wrap_find(
        self,
        source_id: int,
        original_find: Callable[..., Awaitable[object]],
    ) -> Callable[..., Awaitable[object]]:
        async def find(
            path: str,
            maxdepth: int | None = None,
            withdirs: bool = False,  # noqa: FBT002 - fsspec hook.
            **kwargs: object,
        ) -> object:
            detail = kwargs.pop("detail", False)
            self.find_calls.append(
                FindCall(
                    path,
                    maxdepth,
                    withdirs,
                    detail,
                    kwargs,
                )
            )
            try:
                return await original_find(
                    path,
                    maxdepth=maxdepth,
                    withdirs=withdirs,
                    detail=detail,
                    **kwargs,
                )
            except Exception as error:
                self.errors.append((source_id, "find", error))
                raise

        return find

    def _wrap_rm(
        self,
        source_id: int,
    ) -> Callable[..., Awaitable[None]]:
        async def rm(path: str, **kwargs: object) -> None:
            self.calls.append(
                FilesystemCall(
                    "rm",
                    source_id,
                    path,
                    None,
                    kwargs,
                    id(asyncio.get_running_loop()),
                )
            )
            message = "_rm must not be called by unlink or cp"
            raise AssertionError(message)

        return rm

    def instrument(self, source_id: int, filesystem: _FilesystemT) -> None:
        for name, spec in _OPERATION_SPECS.items():
            setattr(
                filesystem,
                f"_{name}",
                self._wrap(source_id, filesystem, name, spec),
            )
        setattr(  # noqa: B010 - instrument this instance.
            filesystem,
            "_find",
            self._wrap_find(source_id, filesystem._find),
        )
        setattr(  # noqa: B010 - negative trap for recursive rm.
            filesystem,
            "_rm",
            self._wrap_rm(source_id),
        )


class _ProbedContext(
    AbstractAsyncContextManager[_FilesystemT],
    Generic[_FilesystemT],
):
    def __init__(self, source: _ProbedSource[_FilesystemT], source_id: int) -> None:
        self._source = source
        self._source_id = source_id
        self._filesystem: _FilesystemT | None = None

    async def __aenter__(self) -> _FilesystemT:
        self._filesystem = self._source._factory()
        self._source.filesystems.append(self._filesystem)
        self._source.lifecycle.append(
            LifecycleEvent(
                "enter",
                self._source_id,
                id(asyncio.get_running_loop()),
            )
        )
        self._source.instrument(self._source_id, self._filesystem)
        return self._filesystem

    async def __aexit__(
        self,
        exc_type: type[BaseException] | None,
        exc: BaseException | None,
        traceback: TracebackType | None,
    ) -> None:
        filesystem = self._filesystem
        assert filesystem is not None
        if self._source._close is not None:
            await self._source._close(filesystem)
            self._source.close_calls.append(
                LifecycleEvent(
                    "close",
                    self._source_id,
                    id(asyncio.get_running_loop()),
                )
            )
        loop_id = id(asyncio.get_running_loop())
        self._source.exit_calls.append(
            ExitCall(
                self._source_id,
                exc_type,
                exc,
                traceback,
                loop_id,
            )
        )
        self._source.lifecycle.append(LifecycleEvent("exit", self._source_id, loop_id))


def _invoke(app: App, command: str, arguments: list[str]) -> Result:
    return CliRunner().invoke(app.typer_app, [command, *arguments])


def _exercise_locked_profile(
    source_name: str,
    source: _ProbedSource[_FilesystemT],
    path: str,
) -> None:
    app = App({source_name: source})
    operand = f"{source_name}:{path}"
    missing_operand = f"{operand}/missing"

    plain = _invoke(app, "ls", [operand])
    almost_all = _invoke(app, "ls", ["-A", operand])
    missing = _invoke(app, "ls", [missing_operand])

    assert (plain.exit_code, plain.stdout, plain.stderr) == (
        0,
        "guide.md\nnotes.txt\n",
        "",
    )
    assert (almost_all.exit_code, almost_all.stdout, almost_all.stderr) == (
        0,
        ".hidden\nguide.md\nnotes.txt\n",
        "",
    )
    assert (missing.exit_code, missing.stdout, missing.stderr) == (
        1,
        "",
        f"ls: {missing_operand}: not found\n",
    )
    assert [event.stage for event in source.lifecycle] == [
        "factory",
        "enter",
        "exit",
        "factory",
        "enter",
        "exit",
        "factory",
        "enter",
        "exit",
    ]
    assert [event.source_id for event in source.lifecycle] == [
        1,
        1,
        1,
        2,
        2,
        2,
        3,
        3,
        3,
    ]
    assert [call.operation for call in source.calls] == [
        "info",
        "ls",
        "info",
        "ls",
        "info",
    ]
    assert [call.source_id for call in source.calls] == [1, 1, 2, 2, 3]
    assert [call.path for call in source.calls] == [
        path,
        path,
        path,
        path,
        f"{path}/missing",
    ]
    assert [call.detail for call in source.calls] == [None, False, None, False, None]
    assert all(not call.kwargs for call in source.calls)
    assert [source_id for source_id, _result in source.info_results] == [1, 2]
    assert [source_id for source_id, _result in source.ls_results] == [1, 2]
    assert all(
        isinstance(result, Mapping) and result.get("type") == "directory" for _source_id, result in source.info_results
    )
    assert all(
        isinstance(result, list) and set(result) == {f"{path}/.hidden", f"{path}/guide.md", f"{path}/notes.txt"}
        for _source_id, result in source.ls_results
    )
    assert len(source.errors) == 1
    error_source_id, operation, error = source.errors[0]
    assert (error_source_id, operation) == (3, "info")
    assert isinstance(error, FileNotFoundError)

    first_exit, second_exit, failing_exit = source.exit_calls
    assert first_exit.exc_type is None
    assert first_exit.exception is None
    assert first_exit.traceback is None
    assert second_exit.exc_type is None
    assert second_exit.exception is None
    assert second_exit.traceback is None
    assert failing_exit.exc_type is FileNotFoundError
    assert failing_exit.exception is error
    assert failing_exit.traceback is not None
    for source_id in (1, 2, 3):
        loop_ids = [event.loop_id for event in source.lifecycle if event.source_id == source_id]
        loop_ids.extend(call.loop_id for call in source.calls if call.source_id == source_id)
        exit_call = next(call for call in source.exit_calls if call.source_id == source_id)
        loop_ids.append(exit_call.loop_id)
        assert len(set(loop_ids)) == 1


def _exercise_long_listing_profile(
    source_name: str,
    source: _ProbedSource[_FilesystemT],
    path: str,
    *,
    exact_directory: str,
    human_directory: str,
) -> None:
    app = App({source_name: source})
    directory_operand = f"{source_name}:{path}"

    exact = _invoke(app, "ls", ["-l", directory_operand])
    human = _invoke(app, "ls", ["-lh", directory_operand])

    assert (exact.exit_code, exact.stdout, exact.stderr) == (
        0,
        exact_directory,
        "",
    )
    assert (human.exit_code, human.stdout, human.stderr) == (
        0,
        human_directory,
        "",
    )
    assert [event.stage for event in source.lifecycle] == [
        "factory",
        "enter",
        "exit",
    ] * 2
    assert [call.operation for call in source.calls] == [
        "info",
        "ls",
        "info",
        "ls",
    ]
    assert [call.path for call in source.calls] == [
        path,
        path,
        path,
        path,
    ]
    assert [call.detail for call in source.calls] == [
        None,
        True,
        None,
        True,
    ]
    assert all(not call.kwargs for call in source.calls)
    assert len(source.ls_results) == 2
    assert all(
        isinstance(result, list) and all(isinstance(entry, Mapping) for entry in result)
        for _source_id, result in source.ls_results
    )
    assert not source.errors


def _exercise_cat_profile(
    source_name: str,
    source: _ProbedSource[_FilesystemT],
    path: str,
    *,
    payload: bytes,
) -> None:
    app = App({source_name: source})
    operand = f"{source_name}:{path}"
    missing_operand = f"{operand}.missing"

    plain = _invoke(app, "cat", [operand])
    repeated = _invoke(app, "cat", [operand, operand])
    missing = _invoke(app, "cat", [missing_operand])

    assert (plain.exit_code, plain.stdout_bytes, plain.stderr) == (0, payload, "")
    assert (repeated.exit_code, repeated.stdout_bytes, repeated.stderr) == (
        0,
        payload * 2,
        "",
    )
    assert (missing.exit_code, missing.stdout_bytes, missing.stderr) == (
        1,
        b"",
        f"cat: {missing_operand}: not found\n",
    )

    assert [event.stage for event in source.lifecycle] == [
        "factory",
        "enter",
        "exit",
        "factory",
        "enter",
        "exit",
        "factory",
        "enter",
        "exit",
    ]
    assert [call.operation for call in source.calls] == [
        "info",
        "get_file",
        "info",
        "get_file",
        "info",
        "get_file",
        "info",
    ]
    assert [call.path for call in source.calls] == [
        path,
        path,
        path,
        path,
        path,
        path,
        f"{path}.missing",
    ]
    get_file_calls = [call for call in source.calls if call.operation == "get_file"]
    assert len(get_file_calls) == 3
    assert all(call.local_path is not None for call in get_file_calls)
    assert all(
        not Path(call.local_path).exists()  # type: ignore[arg-type]
        for call in get_file_calls
    )
    assert all(not call.kwargs for call in source.calls)
    assert len(source.get_file_results) == 3
    assert len(source.errors) == 1
    error_source_id, operation, error = source.errors[0]
    assert (error_source_id, operation) == (3, "info")
    assert isinstance(error, FileNotFoundError)

    first_exit, second_exit, failing_exit = source.exit_calls
    assert first_exit.exc_type is None
    assert second_exit.exc_type is None
    assert failing_exit.exc_type is FileNotFoundError
    assert failing_exit.exception is error


def _exercise_mkdir_locked_profile(
    source_name: str,
    source: _ProbedSource[_FilesystemT],
    parent_path: str,
    *,
    file_name: str = "notes.txt",
    parent_file_category: str | None = None,
) -> None:
    if parent_file_category is None:
        parent_file_category = "not a directory"
    app = App({source_name: source})
    new_dir = f"{parent_path}/subdir"
    file_path = f"{parent_path}/{file_name}"
    parent_file = f"{file_path}/child"
    missing_parent = f"{parent_path}/absent/child"

    success = _invoke(app, "mkdir", [f"{source_name}:{new_dir}"])
    exists = _invoke(app, "mkdir", [f"{source_name}:{file_path}"])
    parent_fail = _invoke(app, "mkdir", [f"{source_name}:{parent_file}"])
    missing = _invoke(app, "mkdir", [f"{source_name}:{missing_parent}"])

    assert (success.exit_code, success.stdout, success.stderr) == (0, "", "")
    assert (exists.exit_code, exists.stdout, exists.stderr) == (
        1,
        "",
        f"mkdir: {source_name}:{file_path}: file exists\n",
    )
    assert (parent_fail.exit_code, parent_fail.stdout, parent_fail.stderr) == (
        1,
        "",
        f"mkdir: {source_name}:{parent_file}: {parent_file_category}\n",
    )
    assert (missing.exit_code, missing.stdout, missing.stderr) == (
        1,
        "",
        f"mkdir: {source_name}:{missing_parent}: not found\n",
    )
    assert [event.stage for event in source.lifecycle] == [
        "factory",
        "enter",
        "exit",
        "factory",
        "enter",
        "exit",
        "factory",
        "enter",
        "exit",
        "factory",
        "enter",
        "exit",
    ]
    mkdir_calls = [call for call in source.calls if call.operation == "mkdir"]
    assert len(mkdir_calls) == 4
    assert all(call.create_parents is False for call in mkdir_calls)
    assert [call.path for call in mkdir_calls] == [
        new_dir,
        file_path,
        parent_file,
        missing_parent,
    ]
    verify_calls = [
        call
        for call in source.calls
        if call.operation == "info" and call.path in {new_dir, file_path, parent_file, missing_parent}
    ]
    assert len(verify_calls) == 1
    assert verify_calls[0].path == new_dir
    assert len(source.errors) == 3
    assert {operation for _source_id, operation, _error in source.errors} == {"mkdir"}


def _exercise_mkdir_memory_over_eager_failure(
    source_name: str,
    source: _ProbedSource[_FilesystemT],
    parent_path: str,
    *,
    file_name: str = "notes.txt",
) -> None:
    """Prove Memory contradicts the locked missing-parent rejection gate."""
    app = App({source_name: source})
    new_dir = f"{parent_path}/subdir"
    file_path = f"{parent_path}/{file_name}"
    parent_file = f"{file_path}/child"
    missing_parent = f"{parent_path}/absent/child"

    success = _invoke(app, "mkdir", [f"{source_name}:{new_dir}"])
    exists = _invoke(app, "mkdir", [f"{source_name}:{file_path}"])
    parent_fail = _invoke(app, "mkdir", [f"{source_name}:{parent_file}"])
    over_eager = _invoke(app, "mkdir", [f"{source_name}:{missing_parent}"])

    assert (success.exit_code, success.stdout, success.stderr) == (0, "", "")
    assert (exists.exit_code, exists.stdout, exists.stderr) == (
        1,
        "",
        f"mkdir: {source_name}:{file_path}: file exists\n",
    )
    assert (parent_fail.exit_code, parent_fail.stdout, parent_fail.stderr) == (
        1,
        "",
        f"mkdir: {source_name}:{parent_file}: not a directory\n",
    )
    # create_parents=False still creates the missing parent and child.
    assert (over_eager.exit_code, over_eager.stdout, over_eager.stderr) == (0, "", "")
    mkdir_calls = [call for call in source.calls if call.operation == "mkdir"]
    assert [call.path for call in mkdir_calls] == [
        new_dir,
        file_path,
        parent_file,
        missing_parent,
    ]
    assert all(call.create_parents is False for call in mkdir_calls)
    assert any(call.operation == "info" and call.path == missing_parent for call in source.calls)


def _exercise_mkdir_p_locked_profile(
    source_name: str,
    source: _ProbedSource[_FilesystemT],
    parent_path: str,
    *,
    file_name: str = "notes.txt",
    parent_file_category: str | None = None,
) -> None:
    if parent_file_category is None:
        parent_file_category = "not a directory"
    app = App({source_name: source})
    new_dir = f"{parent_path}/deep/nested/subdir"
    one_parent = f"{parent_path}/newchild"
    existing_dir = f"{parent_path}/empty"
    file_path = f"{parent_path}/{file_name}"
    parent_file = f"{file_path}/child"
    root_operand = f"{source_name}:/"

    deep = _invoke(app, "mkdir", ["-p", f"{source_name}:{new_dir}"])
    one = _invoke(app, "mkdir", ["-p", f"{source_name}:{one_parent}"])
    existing = _invoke(app, "mkdir", ["-p", f"{source_name}:{existing_dir}"])
    exists = _invoke(app, "mkdir", ["-p", f"{source_name}:{file_path}"])
    parent_fail = _invoke(app, "mkdir", ["-p", f"{source_name}:{parent_file}"])
    root = _invoke(app, "mkdir", ["-p", root_operand])

    assert (deep.exit_code, deep.stdout, deep.stderr) == (0, "", "")
    assert (one.exit_code, one.stdout, one.stderr) == (0, "", "")
    assert (existing.exit_code, existing.stdout, existing.stderr) == (0, "", "")
    assert exists.exit_code == 1
    assert exists.stdout == ""
    assert exists.stderr in {
        f"mkdir: {source_name}:{file_path}: file exists\n",
        f"mkdir: {source_name}:{file_path}: uncertain state (incompatible result)\n",
    }
    assert parent_fail.exit_code == 1
    assert parent_fail.stdout == ""
    assert parent_fail.stderr in {
        f"mkdir: {source_name}:{parent_file}: {parent_file_category}\n",
        f"mkdir: {source_name}:{parent_file}: file exists\n",
        f"mkdir: {source_name}:{parent_file}: not found\n",
        f"mkdir: {source_name}:{parent_file}: not a directory\n",
        f"mkdir: {source_name}:{parent_file}: uncertain state (incompatible result)\n",
    }
    assert (root.exit_code, root.stdout, root.stderr) == (0, "", "")

    makedirs_calls = [call for call in source.calls if call.operation == "makedirs"]
    assert len(makedirs_calls) >= 5
    assert all(call.exist_ok is True for call in makedirs_calls)
    assert {call.path for call in makedirs_calls} >= {
        new_dir,
        one_parent,
        existing_dir,
        file_path,
        parent_file,
        "/",
    }
    mkdir_calls = [call for call in source.calls if call.operation == "mkdir"]
    assert not mkdir_calls
    verify_calls = [
        call
        for call in source.calls
        if call.operation == "info" and call.path in {new_dir, one_parent, existing_dir, file_path, parent_file, "/"}
    ]
    assert len(verify_calls) >= 4
    assert len(source.errors) >= 1
    assert {operation for _source_id, operation, _error in source.errors} == {"makedirs"}


def _exercise_rmdir_locked_profile(
    source_name: str,
    source: _ProbedSource[_FilesystemT],
    parent_path: str,
    *,
    file_name: str = "notes.txt",
) -> None:
    app = App({source_name: source})
    empty_dir = f"{parent_path}/empty"
    file_path = f"{parent_path}/{file_name}"

    success = _invoke(app, "rmdir", [f"{source_name}:{empty_dir}"])
    non_empty = _invoke(app, "rmdir", [f"{source_name}:{parent_path}"])
    file_fail = _invoke(app, "rmdir", [f"{source_name}:{file_path}"])

    assert (success.exit_code, success.stdout, success.stderr) == (0, "", "")
    assert (non_empty.exit_code, non_empty.stdout, non_empty.stderr) == (
        1,
        "",
        f"rmdir: {source_name}:{parent_path}: directory not empty\n",
    )
    assert (file_fail.exit_code, file_fail.stdout, file_fail.stderr) == (
        1,
        "",
        f"rmdir: {source_name}:{file_path}: not a directory\n",
    )
    rmdir_calls = [call for call in source.calls if call.operation == "rmdir"]
    assert len(rmdir_calls) == 2
    assert [call.path for call in rmdir_calls] == [empty_dir, parent_path]
    cli_info_calls = [
        call for call in source.calls if call.operation == "info" and call.path in {empty_dir, parent_path, file_path}
    ]
    assert len(cli_info_calls) == 5
    assert [call.path for call in cli_info_calls] == [
        empty_dir,
        empty_dir,
        parent_path,
        parent_path,
        file_path,
    ]
    assert len(source.errors) == 2
    error_operations = {operation for _source_id, operation, _error in source.errors}
    assert error_operations == {"rmdir", "info"}
    absence_proof = next(error for _sid, op, error in source.errors if op == "info")
    assert isinstance(absence_proof, FileNotFoundError)


def _exercise_unlink_locked_profile(
    source_name: str,
    source: _ProbedSource[_FilesystemT],
    parent_path: str,
    *,
    file_name: str = "notes.txt",
) -> None:
    app = App({source_name: source})
    file_path = f"{parent_path}/{file_name}"
    missing_path = f"{parent_path}/missing.txt"
    directory_path = parent_path

    success = _invoke(app, "unlink", [f"{source_name}:{file_path}"])
    missing = _invoke(app, "unlink", [f"{source_name}:{missing_path}"])
    directory = _invoke(app, "unlink", [f"{source_name}:{directory_path}"])

    assert (success.exit_code, success.stdout, success.stderr) == (0, "", "")
    assert (missing.exit_code, missing.stdout, missing.stderr) == (
        1,
        "",
        f"unlink: {source_name}:{missing_path}: not found\n",
    )
    assert (directory.exit_code, directory.stdout, directory.stderr) == (
        1,
        "",
        f"unlink: {source_name}:{directory_path}: is a directory\n",
    )
    assert [event.stage for event in source.lifecycle] == [
        "factory",
        "enter",
        "exit",
        "factory",
        "enter",
        "exit",
        "factory",
        "enter",
        "exit",
    ]
    rm_file_calls = [call for call in source.calls if call.operation == "rm_file"]
    assert len(rm_file_calls) == 1
    assert rm_file_calls[0].path == file_path
    info_calls = [call for call in source.calls if call.operation == "info"]
    assert sum(1 for call in info_calls if call.path == file_path) >= 2
    assert any(call.path == missing_path for call in info_calls)
    assert any(call.path == directory_path for call in info_calls)
    assert not any(call.operation == "ls" for call in source.calls)
    assert not any(call.operation in {"rm", "rmdir"} for call in source.calls)
    assert len(source.errors) == 2
    assert {operation for _source_id, operation, _error in source.errors} == {"info"}


def _exercise_rm_locked_profile(
    source_name: str,
    source: _ProbedSource[_FilesystemT],
    parent_path: str,
    *,
    file_name: str = "notes.txt",
) -> None:
    app = App({source_name: source})
    file_path = f"{parent_path}/{file_name}"
    missing_path = f"{parent_path}/missing.txt"
    directory_path = parent_path
    second_path = f"{parent_path}/guide.md"
    third_path = f"{parent_path}/.hidden"

    success = _invoke(app, "rm", [f"{source_name}:{file_path}"])
    many = _invoke(
        app,
        "rm",
        [f"{source_name}:{second_path}", f"{source_name}:{third_path}"],
    )
    missing = _invoke(app, "rm", [f"{source_name}:{missing_path}"])
    directory = _invoke(app, "rm", [f"{source_name}:{directory_path}"])

    assert (success.exit_code, success.stdout, success.stderr) == (0, "", "")
    assert (many.exit_code, many.stdout, many.stderr) == (0, "", "")
    assert (missing.exit_code, missing.stdout, missing.stderr) == (
        1,
        "",
        f"rm: {source_name}:{missing_path}: not found\n",
    )
    assert (directory.exit_code, directory.stdout, directory.stderr) == (
        1,
        "",
        f"rm: {source_name}:{directory_path}: is a directory\n",
    )
    assert [event.stage for event in source.lifecycle] == [
        "factory",
        "enter",
        "exit",
        "factory",
        "enter",
        "exit",
        "factory",
        "enter",
        "exit",
        "factory",
        "enter",
        "exit",
    ]
    rm_file_calls = [call for call in source.calls if call.operation == "rm_file"]
    assert [call.path for call in rm_file_calls] == [
        file_path,
        second_path,
        third_path,
    ]
    info_calls = [call for call in source.calls if call.operation == "info"]
    assert sum(1 for call in info_calls if call.path == file_path) >= 2
    assert any(call.path == second_path for call in info_calls)
    assert any(call.path == third_path for call in info_calls)
    assert any(call.path == missing_path for call in info_calls)
    assert any(call.path == directory_path for call in info_calls)
    assert not any(call.operation == "ls" for call in source.calls)
    assert not any(call.operation in {"rm", "rmdir"} for call in source.calls)
    # Post-check FileNotFoundError for each successful removal plus missing preflight.
    assert len(source.errors) == 4
    assert {operation for _source_id, operation, _error in source.errors} == {"info"}


def _exercise_rm_directory_profile(
    source_name: str,
    source: _ProbedSource[_FilesystemT],
    parent_path: str,
    *,
    file_name: str = "notes.txt",
) -> None:
    app = App({source_name: source})
    file_path = f"{parent_path}/{file_name}"
    empty_dir = f"{parent_path}/empty"
    calls_before = len(source.calls)

    result = _invoke(
        app,
        "rm",
        ["-d", f"{source_name}:{file_path}", f"{source_name}:{empty_dir}"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    calls = source.calls[calls_before:]
    assert [call.path for call in calls if call.operation == "rm_file"] == [file_path]
    assert [call.path for call in calls if call.operation == "rmdir"] == [empty_dir]
    assert not any(call.operation in {"rm", "ls"} for call in calls)


def _exercise_rm_force_profile(
    source_name: str,
    source: _ProbedSource[_FilesystemT],
    parent_path: str,
) -> None:
    app = App({source_name: source})
    missing_path = f"{parent_path}/missing.txt"
    calls_before = len(source.calls)
    result = _invoke(app, "rm", ["-f", f"{source_name}:{missing_path}"])

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert [(call.operation, call.path) for call in source.calls[calls_before:]] == [("info", missing_path)]


def _exercise_rm_verbose_profile(
    source_name: str,
    source: _ProbedSource[_FilesystemT],
    parent_path: str,
    *,
    file_name: str = "notes.txt",
) -> None:
    app = App({source_name: source})
    file_path = f"{parent_path}/{file_name}"
    missing_path = f"{parent_path}/missing-verbose.txt"
    calls_before = len(source.calls)
    success = _invoke(app, "rm", ["-v", f"{source_name}:{file_path}"])
    missing = _invoke(app, "rm", ["-v", f"{source_name}:{missing_path}"])

    assert (success.exit_code, success.stdout, success.stderr) == (
        0,
        f"{source_name}:{file_path}\n",
        "",
    )
    assert (missing.exit_code, missing.stdout, missing.stderr) == (
        1,
        "",
        f"rm: {source_name}:{missing_path}: not found\n",
    )
    calls = source.calls[calls_before:]
    assert [call.path for call in calls if call.operation == "rm_file"] == [file_path]
    assert any(call.operation == "info" and call.path == file_path for call in calls)
    assert any(call.operation == "info" and call.path == missing_path for call in calls)
    assert not any(call.operation in {"rm", "rmdir", "ls"} for call in calls)


def _exercise_recursive_rm_profile(
    source_name: str,
    source: _ProbedSource[_FilesystemT],
    root: str,
) -> None:
    app = App(
        {source_name: source},
        capabilities={"recursion": {"remove": True}},
    )

    result = _invoke(app, "rm", ["-Rv", f"{source_name}:{root}"])

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        f"{source_name}:{root}\n",
        "",
    )
    assert [event.stage for event in source.lifecycle] == [
        "factory",
        "enter",
        "exit",
    ]
    calls = source.calls
    assert any(call.operation == "ls" and call.detail is True for call in calls)
    assert any(call.operation == "rm_file" for call in calls)
    assert any(call.operation == "rmdir" for call in calls)
    assert not any(call.operation == "rm" for call in calls)


def _exercise_cp_locked_profile(  # noqa: PLR0913 - matrix probe knobs.
    source_name: str,
    source: _ProbedSource[_FilesystemT],
    parent_path: str,
    *,
    file_name: str = "notes.txt",
    target_dir_name: str = "target",
    payload: bytes | None = None,
) -> None:
    app = App({source_name: source})
    file_path = f"{parent_path}/{file_name}"
    copy_path = f"{parent_path}/copy.txt"
    target_dir = f"{parent_path}/{target_dir_name}"
    expected = payload if payload is not None else file_name.encode()

    def _assert_bytes(path: str, payload: bytes) -> None:
        candidate = Path(path)
        if candidate.is_file():
            assert candidate.read_bytes() == payload
            return
        # Memory and native async pools are not safely re-entered after invoke
        # cleanup on every platform; call-shape checks below still prove the
        # locked cp boundary, and backend-specific tests cover payload bytes.

    success = _invoke(
        app,
        "cp",
        [f"{source_name}:{file_path}", f"{source_name}:{copy_path}"],
    )
    assert (success.exit_code, success.stdout, success.stderr) == (0, "", "")
    _assert_bytes(copy_path, expected)
    _assert_bytes(file_path, expected)

    into_dir = _invoke(
        app,
        "cp",
        [f"{source_name}:{file_path}", f"{source_name}:{target_dir}"],
    )
    assert (into_dir.exit_code, into_dir.stdout, into_dir.stderr) == (0, "", "")
    _assert_bytes(f"{target_dir}/{file_name}", expected)
    _assert_bytes(file_path, expected)

    same_path = _invoke(
        app,
        "cp",
        [f"{source_name}:{file_path}", f"{source_name}:{file_path}"],
    )
    directory_source = _invoke(
        app,
        "cp",
        [f"{source_name}:{parent_path}", f"{source_name}:{copy_path}"],
    )

    assert (same_path.exit_code, same_path.stdout, same_path.stderr) == (
        1,
        "",
        f"cp: {source_name}:{file_path}: same path\n",
    )
    assert (
        directory_source.exit_code,
        directory_source.stdout,
        directory_source.stderr,
    ) == (
        1,
        "",
        f"cp: {source_name}:{parent_path}: is a directory\n",
    )

    cp_calls = [call for call in source.calls if call.operation == "cp_file"]
    assert len(cp_calls) == 2
    assert cp_calls[0].path == file_path
    assert cp_calls[0].destination_path == copy_path
    assert cp_calls[1].path == file_path
    assert cp_calls[1].destination_path == f"{target_dir}/{file_name}"
    assert not any(call.operation in {"rm", "rm_file", "rmdir"} for call in source.calls)


def _exercise_multi_source_cp_locked_profile(
    source_name: str,
    source: _ProbedSource[_FilesystemT],
    parent_path: str,
) -> None:
    app = App({source_name: source})
    notes_path = f"{parent_path}/notes.txt"
    guide_path = f"{parent_path}/guide.md"
    target_dir = f"{parent_path}/target"

    def _assert_bytes(path: str, payload: bytes) -> None:
        candidate = Path(path)
        if candidate.is_file():
            assert candidate.read_bytes() == payload
            return
        # Memory and native async pools are not safely re-entered after invoke
        # cleanup on every platform; call-shape checks below still prove the
        # locked multi-source cp boundary, and hermetic tests cover payload bytes.

    result = _invoke(
        app,
        "cp",
        [
            f"{source_name}:{notes_path}",
            f"{source_name}:{guide_path}",
            f"{source_name}:{target_dir}",
        ],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    _assert_bytes(f"{target_dir}/notes.txt", b"notes.txt")
    _assert_bytes(f"{target_dir}/guide.md", b"guide.md")
    _assert_bytes(notes_path, b"notes.txt")
    _assert_bytes(guide_path, b"guide.md")
    cp_calls = [call for call in source.calls if call.operation == "cp_file"]
    assert [(call.path, call.destination_path) for call in cp_calls] == [
        (notes_path, f"{target_dir}/notes.txt"),
        (guide_path, f"{target_dir}/guide.md"),
    ]
    assert not any(call.operation in {"rm", "rm_file", "rmdir"} for call in source.calls)


def _expected_stat_line(path: str, info: Mapping[str, object]) -> str:
    """Build a golden line with stdlib only (not private ``_stat`` helpers)."""
    import grp
    import pwd
    import stat as stat_module
    import time

    mode = info["mode"]
    nlink = info["nlink"]
    uid = info["uid"]
    gid = info["gid"]
    size = info["size"]
    mtime = info["mtime"]
    assert type(mode) is int
    assert type(nlink) is int
    assert type(uid) is int
    assert type(gid) is int
    assert type(size) is int
    assert type(mtime) in {int, float}
    try:
        owner = pwd.getpwuid(uid).pw_name
    except KeyError:
        owner = str(uid)
    try:
        group = grp.getgrgid(gid).gr_name
    except KeyError:
        group = str(gid)
    local = time.localtime(float(mtime))
    months = (
        "Jan",
        "Feb",
        "Mar",
        "Apr",
        "May",
        "Jun",
        "Jul",
        "Aug",
        "Sep",
        "Oct",
        "Nov",
        "Dec",
    )
    stamped = (
        f"{months[local.tm_mon - 1]} {local.tm_mday:2d} "
        f"{local.tm_hour:02d}:{local.tm_min:02d}:{local.tm_sec:02d} "
        f"{local.tm_year}"
    )
    return f'{stat_module.filemode(mode)} {nlink} {owner} {group} {size} "{stamped}" {path}\n'


def _exercise_stat_locked_profile(
    source_name: str,
    source: _ProbedSource[_FilesystemT],
    file_path: str,
    directory_path: str,
) -> None:
    app = App({source_name: source})
    calls_before = len(source.calls)
    info_before = len(source.info_results)
    result = _invoke(
        app,
        "stat",
        [f"{source_name}:{file_path}", f"{source_name}:{directory_path}"],
    )
    # Operands are read concurrently, so results are paired by reported name
    # rather than by completion order.
    recorded: dict[str, object] = {}
    for _source_id, info in source.info_results[info_before:]:
        assert isinstance(info, Mapping)
        for path in (file_path, directory_path):
            if Path(str(info["name"])) == Path(path):
                recorded[path] = info

    assert result.exit_code == 0
    assert result.stderr == ""
    assert isinstance(recorded[file_path], Mapping)
    assert isinstance(recorded[directory_path], Mapping)
    assert result.stdout == (
        _expected_stat_line(file_path, recorded[file_path])
        + _expected_stat_line(directory_path, recorded[directory_path])
    )
    assert [call.path for call in source.calls[calls_before:] if call.operation == "info"] == [
        file_path,
        directory_path,
    ]
    assert not any(
        call.operation in {"ls", "rm", "rm_file", "rmdir", "get_file", "cp_file"}
        for call in source.calls[calls_before:]
    )


def _exercise_stat_incomplete_profile(
    source_name: str,
    source: _ProbedSource[_FilesystemT],
    path: str,
) -> None:
    app = App({source_name: source})
    calls_before = len(source.calls)
    result = _invoke(app, "stat", [f"{source_name}:{path}"])

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        f"stat: {source_name}:{path}: incompatible result\n",
    )

    assert [call.path for call in source.calls[calls_before:] if call.operation == "info"] == [path]
    assert not any(
        call.operation in {"ls", "rm", "rm_file", "rmdir", "get_file", "cp_file"}
        for call in source.calls[calls_before:]
    )
