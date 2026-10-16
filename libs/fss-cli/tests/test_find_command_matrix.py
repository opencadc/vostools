"""Hermetic Local, Memory, and native-vosfs evidence for ``find``."""

from pathlib import Path
from typing import TypeVar

import httpx
import pytest
from fsspec.asyn import AsyncFileSystem
from fsspec.implementations.asyn_wrapper import AsyncFileSystemWrapper
from fsspec.implementations.local import LocalFileSystem
from fsspec.implementations.memory import MemoryFileSystem
from typer.testing import CliRunner
from vosfs import VOSpaceFileSystem

from fss_cli import App

from ._matrix_support import (
    _memory_source,
    _ProbedSource,
)
from ._vosfs_matrix_support import (
    _CAPABILITIES,
    _vos_child,
    _vos_container,
    _vosfs_source,
)

_FilesystemT = TypeVar("_FilesystemT", bound=AsyncFileSystem)
_DOCS = _vos_container(
    "/docs",
    _vos_child("DataNode", "/docs/a.txt", length=1)
    + _vos_child("ContainerNode", "/docs/sub")
    + _vos_child("ContainerNode", "/docs/empty"),
)
_SUB = _vos_container("/docs/sub", _vos_child("DataNode", "/docs/sub/b.txt", length=1))
_EMPTY = _vos_container("/docs/empty")
_RESPONSES: dict[tuple[str, str], httpx.Response] = {
    ("GET", "/arc/capabilities"): httpx.Response(200, content=_CAPABILITIES),
    ("GET", "/arc/nodes/docs"): httpx.Response(200, content=_DOCS),
    ("GET", "/arc/nodes/docs/sub"): httpx.Response(200, content=_SUB),
    ("GET", "/arc/nodes/docs/empty"): httpx.Response(200, content=_EMPTY),
}


def _exercise_find_profile(  # noqa: PLR0913 - matrix golden expectations.
    source_name: str,
    source: _ProbedSource[_FilesystemT],
    path: str,
    *,
    recursive_files: str,
    direct_files: str,
    directories: str,
    root_directory: str,
) -> None:
    app = App({source_name: source})
    operand = f"{source_name}:{path}"
    runner = CliRunner()

    recursive = runner.invoke(app.typer_app, ["find", operand])
    direct = runner.invoke(
        app.typer_app,
        ["find", "--maxdepth", "1", operand],
    )
    dirs = runner.invoke(app.typer_app, ["find", "--type", "d", operand])
    root = runner.invoke(
        app.typer_app,
        ["find", "--maxdepth", "0", "--type", "d", operand],
    )

    assert (recursive.exit_code, recursive.stdout, recursive.stderr) == (
        0,
        recursive_files,
        "",
    )
    assert (direct.exit_code, direct.stdout, direct.stderr) == (
        0,
        direct_files,
        "",
    )
    assert (dirs.exit_code, dirs.stdout, dirs.stderr) == (0, directories, "")
    assert (root.exit_code, root.stdout, root.stderr) == (0, root_directory, "")
    assert [event.stage for event in source.lifecycle] == [
        "factory",
        "enter",
        "exit",
    ] * 4
    # One listing per directory; siblings of one depth are listed
    # concurrently, so only their set (not their start order) is fixed.
    root_info = ("info", path, None)
    root_ls = ("ls", path, True)
    nested = sorted(("ls", f"{path}/{name}", True) for name in ("empty", "sub"))
    expected_by_run = [
        ([root_ls], nested),
        ([root_ls], []),
        ([root_info, root_ls], nested),
        ([root_info, root_ls], []),
    ]
    for source_id, (ordered, concurrent) in enumerate(expected_by_run, start=1):
        calls = [(call.operation, call.path, call.detail) for call in source.calls if call.source_id == source_id]
        assert calls[: len(ordered)] == ordered
        assert sorted(calls[len(ordered) :]) == concurrent
    assert all(not call.kwargs for call in source.calls)
    assert not source.find_calls
    assert not source.errors


def test_adapted_local_find_profile_uses_native_temporary_storage(
    tmp_path: Path,
) -> None:
    root = tmp_path / "docs"
    (root / "sub").mkdir(parents=True)
    (root / "empty").mkdir()
    (root / "a.txt").write_text("a", encoding="utf-8")
    (root / "sub" / "b.txt").write_text("b", encoding="utf-8")
    path = root.resolve().as_posix()
    source = _ProbedSource(
        lambda: AsyncFileSystemWrapper(
            LocalFileSystem(skip_instance_cache=True),
            asynchronous=True,
        )
    )

    _exercise_find_profile(
        "local",
        source,
        path,
        recursive_files=f"{path}/a.txt\n{path}/sub/b.txt\n",
        direct_files=f"{path}/a.txt\n",
        directories=f"{path}\n{path}/empty\n{path}/sub\n",
        root_directory=f"{path}\n",
    )

    assert all(isinstance(fs, AsyncFileSystemWrapper) for fs in source.filesystems)
    assert all(isinstance(fs.sync_fs, LocalFileSystem) for fs in source.filesystems)


def test_adapted_memory_find_profile_has_isolated_state(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = _memory_source(
        monkeypatch,
        {"/docs/a.txt": b"a", "/docs/sub/b.txt": b"b"},
        directories=("/docs/sub", "/docs/empty"),
    )

    _exercise_find_profile(
        "memory",
        source,
        "/docs",
        recursive_files="/docs/a.txt\n/docs/sub/b.txt\n",
        direct_files="/docs/a.txt\n",
        directories="/docs\n/docs/empty\n/docs/sub\n",
        root_directory="/docs\n",
    )

    assert all(isinstance(fs, AsyncFileSystemWrapper) for fs in source.filesystems)
    assert all(isinstance(fs.sync_fs, MemoryFileSystem) for fs in source.filesystems)


@pytest.mark.parametrize("arguments", [[], ["--type", "d"]])
def test_adapted_memory_find_reports_a_missing_operand_as_not_found(
    monkeypatch: pytest.MonkeyPatch,
    arguments: list[str],
) -> None:
    source = _memory_source(monkeypatch)

    result = CliRunner().invoke(
        App({"memory": source}).typer_app,
        ["find", *arguments, "memory:/docs/missing"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "find: memory:/docs/missing: not found\n",
    )
    assert [(call.operation, call.path) for call in source.calls] == [
        ("info" if arguments else "ls", "/docs/missing"),
    ]
    assert source.exit_calls[0].exc_type is FileNotFoundError


def test_native_vosfs_find_profile_uses_only_mocked_transport() -> None:
    source, transports = _vosfs_source(_RESPONSES)

    _exercise_find_profile(
        "vos",
        source,
        "/docs",
        recursive_files="/docs/a.txt\n/docs/sub/b.txt\n",
        direct_files="/docs/a.txt\n",
        directories="/docs\n/docs/empty\n/docs/sub\n",
        root_directory="/docs\n",
    )

    assert all(isinstance(fs, VOSpaceFileSystem) for fs in source.filesystems)
    assert all(fs._pool.closed is True for fs in source.filesystems)
    assert all(transport.closed for transport in transports)
