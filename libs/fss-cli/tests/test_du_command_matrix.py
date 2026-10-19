"""Hermetic Local, Memory, and native-vosfs evidence for ``du``."""

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
    _vos_child("DataNode", "/docs/notes.txt", length=1536)
    + _vos_child("DataNode", "/docs/.hidden", length=7)
    + _vos_child("DataNode", "/docs/guide.md", length=8)
    + _vos_child("LinkNode", "/docs/shortcut", target="/docs/guide.md"),
)
_RESPONSES: dict[tuple[str, str], httpx.Response] = {
    ("GET", "/arc/capabilities"): httpx.Response(200, content=_CAPABILITIES),
    ("GET", "/arc/nodes/docs"): httpx.Response(200, content=_DOCS),
}


def _exercise_du_profile(  # noqa: PLR0913 - matrix golden expectations.
    source_name: str,
    source: _ProbedSource[_FilesystemT],
    path: str,
    *,
    exact_output: str,
    human_output: str,
    total: int,
    human_total: str,
) -> None:
    app = App({source_name: source})
    operand = f"{source_name}:{path}"
    runner = CliRunner()

    exact = runner.invoke(app.typer_app, ["du", operand])
    human = runner.invoke(app.typer_app, ["du", "-h", operand])
    summary = runner.invoke(app.typer_app, ["du", "-s", operand])
    human_summary = runner.invoke(app.typer_app, ["du", "-sh", operand])

    assert (exact.exit_code, exact.stdout, exact.stderr) == (0, exact_output, "")
    assert (human.exit_code, human.stdout, human.stderr) == (0, human_output, "")
    assert (summary.exit_code, summary.stdout, summary.stderr) == (
        0,
        f"{total}\t{path}\n",
        "",
    )
    assert (human_summary.exit_code, human_summary.stdout, human_summary.stderr) == (
        0,
        f"{human_total}\t{path}\n",
        "",
    )
    assert [event.stage for event in source.lifecycle] == [
        "factory",
        "enter",
        "exit",
    ] * 4
    # One listing GET per directory; no per-file metadata lookups.
    assert [(call.operation, call.source_id, call.path, call.detail, call.kwargs) for call in source.calls] == [
        ("ls", source_id, path, True, {}) for source_id in range(1, 5)
    ]
    assert not source.errors


def test_adapted_local_du_profile_uses_native_temporary_storage(
    tmp_path: Path,
) -> None:
    root = tmp_path / "docs"
    root.mkdir()
    for name in ("notes.txt", ".hidden", "guide.md"):
        (root / name).write_text(name, encoding="utf-8")
    path = root.resolve().as_posix()
    source = _ProbedSource(
        lambda: AsyncFileSystemWrapper(
            LocalFileSystem(skip_instance_cache=True),
            asynchronous=True,
        )
    )

    _exercise_du_profile(
        "local",
        source,
        path,
        exact_output=f"7\t{path}/.hidden\n8\t{path}/guide.md\n9\t{path}/notes.txt\n",
        human_output=(f"7B\t{path}/.hidden\n8B\t{path}/guide.md\n9B\t{path}/notes.txt\n"),
        total=24,
        human_total="24B",
    )

    assert all(isinstance(fs, AsyncFileSystemWrapper) for fs in source.filesystems)
    assert all(isinstance(fs.sync_fs, LocalFileSystem) for fs in source.filesystems)


def test_adapted_memory_du_profile_has_isolated_state(
    monkeypatch,
) -> None:
    source = _memory_source(monkeypatch)

    _exercise_du_profile(
        "memory",
        source,
        "/docs",
        exact_output="7\t/docs/.hidden\n8\t/docs/guide.md\n9\t/docs/notes.txt\n",
        human_output="7B\t/docs/.hidden\n8B\t/docs/guide.md\n9B\t/docs/notes.txt\n",
        total=24,
        human_total="24B",
    )

    assert all(isinstance(fs, AsyncFileSystemWrapper) for fs in source.filesystems)
    assert all(isinstance(fs.sync_fs, MemoryFileSystem) for fs in source.filesystems)


@pytest.mark.parametrize("arguments", [[], ["-s"]])
def test_adapted_memory_du_reports_a_missing_operand_as_not_found(
    monkeypatch: pytest.MonkeyPatch,
    arguments: list[str],
) -> None:
    source = _memory_source(monkeypatch)

    result = CliRunner().invoke(
        App({"memory": source}).typer_app,
        ["du", *arguments, "memory:/docs/missing"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "du: memory:/docs/missing: not found\n",
    )
    assert [(call.operation, call.path) for call in source.calls] == [
        ("ls", "/docs/missing"),
    ]
    assert source.exit_calls[0].exc_type is FileNotFoundError


def test_native_vosfs_du_profile_uses_only_mocked_transport() -> None:
    source, transports = _vosfs_source(_RESPONSES)

    _exercise_du_profile(
        "vos",
        source,
        "/docs",
        exact_output=("7\t/docs/.hidden\n8\t/docs/guide.md\n1536\t/docs/notes.txt\n0\t/docs/shortcut\n"),
        human_output=("7B\t/docs/.hidden\n8B\t/docs/guide.md\n1.5K\t/docs/notes.txt\n0B\t/docs/shortcut\n"),
        total=1551,
        human_total="1.5K",
    )

    assert all(isinstance(fs, VOSpaceFileSystem) for fs in source.filesystems)
    assert all(fs._pool.closed is True for fs in source.filesystems)
    expected_requests = [
        ("GET", "/arc/capabilities"),
        ("GET", "/arc/nodes/docs"),
    ]
    assert [transport.requests for transport in transports] == [
        expected_requests,
    ] * 4
    assert all(transport.closed for transport in transports)
