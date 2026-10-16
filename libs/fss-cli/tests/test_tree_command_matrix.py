"""Hermetic Local, Memory, and native-vosfs evidence for ``tree``."""

from __future__ import annotations

from contextlib import AbstractAsyncContextManager
from dataclasses import dataclass
from typing import TYPE_CHECKING

import httpx
from fsspec.asyn import AsyncFileSystem
from fsspec.implementations.asyn_wrapper import AsyncFileSystemWrapper
from fsspec.implementations.local import LocalFileSystem
from fsspec.implementations.memory import MemoryFileSystem
from typer.testing import CliRunner
from vosfs import VOSpaceFileSystem

from fss_cli import App

from ._matrix_support import (
    _memory_factory,
)
from ._vosfs_matrix_support import (
    _BASE_URL,
    _CAPABILITIES,
    _close_vosfs,
    _StrictMockTransport,
    _vos_child,
    _vos_container,
)

_DOCS = _vos_container(
    "/docs",
    _vos_child("DataNode", "/docs/a.txt", length=1)
    + _vos_child("ContainerNode", "/docs/sub")
    + _vos_child("ContainerNode", "/docs/empty"),
)
_SUB = _vos_container("/docs/sub", _vos_child("DataNode", "/docs/sub/b.txt", length=1))
_EMPTY = _vos_container("/docs/empty")

if TYPE_CHECKING:
    from collections.abc import Awaitable, Callable
    from pathlib import Path
    from types import TracebackType

_RESPONSES: dict[tuple[str, str], httpx.Response] = {
    ("GET", "/arc/capabilities"): httpx.Response(200, content=_CAPABILITIES),
    ("GET", "/arc/nodes/docs"): httpx.Response(200, content=_DOCS),
    ("GET", "/arc/nodes/docs/sub"): httpx.Response(200, content=_SUB),
    ("GET", "/arc/nodes/docs/empty"): httpx.Response(200, content=_EMPTY),
}


@dataclass(frozen=True)
class _LsCall:
    path: str
    detail: bool
    kwargs: dict[str, object]


class _TreeProfileSource:
    def __init__(
        self,
        factory: Callable[[], AsyncFileSystem],
        *,
        close: Callable[[AsyncFileSystem], Awaitable[None]] | None = None,
        denied: frozenset[str] = frozenset(),
    ) -> None:
        self._factory = factory
        self._close = close
        self.denied = denied
        self.lifecycle: list[str] = []
        self.calls: list[_LsCall] = []
        self.filesystems: list[AsyncFileSystem] = []
        self.exit_calls: list[
            tuple[
                type[BaseException] | None,
                BaseException | None,
                TracebackType | None,
            ]
        ] = []

    def __call__(self) -> _TreeProfileContext:
        self.lifecycle.append("factory")
        return _TreeProfileContext(self)


class _TreeProfileContext(AbstractAsyncContextManager[AsyncFileSystem]):
    def __init__(self, source: _TreeProfileSource) -> None:
        self.source = source
        self.filesystem: AsyncFileSystem | None = None

    async def __aenter__(self) -> AsyncFileSystem:
        filesystem = self.source._factory()
        self.filesystem = filesystem
        self.source.filesystems.append(filesystem)
        self.source.lifecycle.append("enter")
        self._instrument_ls(filesystem)
        return filesystem

    def _instrument_ls(self, filesystem: AsyncFileSystem) -> None:
        original_ls = filesystem._ls

        async def ls(
            path: str,
            detail: bool = True,  # noqa: FBT002 - matches the fsspec hook signature.
            **kwargs: object,
        ) -> object:
            self.source.calls.append(_LsCall(path, detail, kwargs))
            if path in self.source.denied:
                raise PermissionError(path)
            return await original_ls(path, detail=detail, **kwargs)

        setattr(filesystem, "_ls", ls)  # noqa: B010 - probe.

    async def __aexit__(
        self,
        exc_type: type[BaseException] | None,
        exc: BaseException | None,
        traceback: TracebackType | None,
    ) -> None:
        filesystem = self.filesystem
        assert filesystem is not None
        if self.source._close is not None:
            await self.source._close(filesystem)
            self.source.lifecycle.append("close")
        self.source.exit_calls.append((exc_type, exc, traceback))
        self.source.lifecycle.append("exit")


def _exercise_tree_profile(
    source_name: str,
    source: _TreeProfileSource,
    path: str,
) -> None:
    app = App({source_name: source})
    operand = f"{source_name}:{path}"
    runner = CliRunner()

    recursive = runner.invoke(app.typer_app, ["tree", operand])
    direct = runner.invoke(
        app.typer_app,
        ["tree", "--maxdepth", "1", operand],
    )

    assert (recursive.exit_code, recursive.stdout, recursive.stderr) == (
        0,
        f"{path}\n├── empty\n├── sub\n│   └── b.txt\n└── a.txt\n",
        "",
    )
    assert (direct.exit_code, direct.stdout, direct.stderr) == (
        0,
        f"{path}\n├── empty\n├── sub\n└── a.txt\n",
        "",
    )
    listed = [call.path for call in source.calls]
    assert listed[0] == path
    assert sorted(listed[1:3]) == [f"{path}/empty", f"{path}/sub"]
    assert listed[3:] == [path]
    assert all(call.detail is True and not call.kwargs for call in source.calls)
    expected_lifecycle = ["factory", "enter"]
    if source._close is not None:
        expected_lifecycle.append("close")
    expected_lifecycle.append("exit")
    assert source.lifecycle == expected_lifecycle * 2
    assert all(call[1] is None for call in source.exit_calls)


def test_adapted_local_tree_profile_uses_native_temporary_storage(
    tmp_path: Path,
) -> None:
    root = tmp_path / "docs"
    (root / "sub").mkdir(parents=True)
    (root / "empty").mkdir()
    (root / "a.txt").write_text("a", encoding="utf-8")
    (root / "sub" / "b.txt").write_text("b", encoding="utf-8")
    path = root.resolve().as_posix()
    source = _TreeProfileSource(
        lambda: AsyncFileSystemWrapper(
            LocalFileSystem(skip_instance_cache=True),
            asynchronous=True,
        )
    )

    _exercise_tree_profile("local", source, path)

    adapted_filesystems = [fs for fs in source.filesystems if isinstance(fs, AsyncFileSystemWrapper)]
    assert adapted_filesystems == source.filesystems
    assert all(isinstance(fs.sync_fs, LocalFileSystem) for fs in adapted_filesystems)


def test_adapted_memory_tree_profile_has_isolated_state(
    monkeypatch,
) -> None:
    source = _TreeProfileSource(
        _memory_factory(
            monkeypatch,
            {"/docs/a.txt": b"a", "/docs/sub/b.txt": b"b"},
            directories=("/docs/sub", "/docs/empty"),
        )
    )

    _exercise_tree_profile("memory", source, "/docs")

    adapted_filesystems = [fs for fs in source.filesystems if isinstance(fs, AsyncFileSystemWrapper)]
    assert adapted_filesystems == source.filesystems
    assert all(isinstance(fs.sync_fs, MemoryFileSystem) for fs in adapted_filesystems)


def test_adapted_memory_tree_fails_when_a_nested_directory_cannot_be_listed(
    monkeypatch,
) -> None:
    source = _TreeProfileSource(
        _memory_factory(
            monkeypatch,
            {"/docs/a.txt": b"a", "/docs/sub/b.txt": b"b"},
            directories=("/docs/sub", "/docs/empty"),
        ),
        denied=frozenset({"/docs/sub"}),
    )

    result = CliRunner().invoke(
        App({"memory": source}).typer_app,
        ["tree", "memory:/docs"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "tree: memory:/docs: permission denied\n",
    )
    assert sorted(call.path for call in source.calls) == [
        "/docs",
        "/docs/empty",
        "/docs/sub",
    ]
    assert source.lifecycle == ["factory", "enter", "exit"]
    assert isinstance(source.exit_calls[0][1], PermissionError)


def test_native_vosfs_tree_profile_uses_client_traversal_over_mocked_transport() -> None:
    transports: list[_StrictMockTransport] = []

    def make_filesystem() -> VOSpaceFileSystem:
        transport = _StrictMockTransport(_RESPONSES)
        transports.append(transport)
        return VOSpaceFileSystem(
            _BASE_URL,
            transport=transport,
            asynchronous=True,
            skip_instance_cache=True,
            trust_env=False,
        )

    source = _TreeProfileSource(make_filesystem, close=_close_vosfs)

    _exercise_tree_profile("vos", source, "/docs")

    vos_filesystems = [fs for fs in source.filesystems if isinstance(fs, VOSpaceFileSystem)]
    assert vos_filesystems == source.filesystems
    assert all(filesystem._pool.closed is True for filesystem in vos_filesystems)
    unbounded_paths = {path for _method, path in transports[0].requests}
    assert {
        "/arc/nodes/docs",
        "/arc/nodes/docs/sub",
        "/arc/nodes/docs/empty",
    } <= unbounded_paths
    direct_paths = {path for _method, path in transports[1].requests}
    assert "/arc/nodes/docs" in direct_paths
    assert "/arc/nodes/docs/sub" not in direct_paths
    assert "/arc/nodes/docs/empty" not in direct_paths
    assert all(transport.closed for transport in transports)
