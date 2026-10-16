"""Hermetic Local, Memory, and native-vosfs evidence for ``info``."""

from __future__ import annotations

import os
import pprint
from datetime import datetime, timezone
from typing import TYPE_CHECKING

import httpx
from fsspec.implementations.asyn_wrapper import AsyncFileSystemWrapper
from fsspec.implementations.local import LocalFileSystem
from fsspec.implementations.memory import MemoryFileSystem
from typer.testing import CliRunner
from vosfs import VOSpaceFileSystem

from fss_cli import App

from ._matrix_support import (
    _ProbedSource,
)
from ._vosfs_matrix_support import (
    _AUTHORITY,
    _BASE_URL,
    _CAPABILITIES,
    _close_vosfs,
    _StrictMockTransport,
    _vos_document,
)

if TYPE_CHECKING:
    from pathlib import Path

    from fsspec.asyn import AsyncFileSystem

_VOS_INFO = _vos_document(
    "DataNode",
    "/docs/report.txt",
    "<vos:properties>"
    '<vos:property uri="ivo://ivoa.net/vospace/core#length">3</vos:property>'
    '<vos:property uri="ivo://ivoa.net/vospace/core#mtime">'
    "2026-07-17T18:00:00Z</vos:property>"
    '<vos:property uri="ivo://ivoa.net/vospace/core#MD5">abc123</vos:property>'
    '<vos:property uri="ivo://ivoa.net/vospace/core#contenttype">'
    "text/plain</vos:property>"
    '<vos:property uri="ivo://example.test/project">science</vos:property>'
    "</vos:properties>",
)
_RESPONSES: dict[tuple[str, str], httpx.Response] = {
    ("GET", "/arc/capabilities"): httpx.Response(200, content=_CAPABILITIES),
    ("GET", "/arc/nodes/docs/report.txt"): httpx.Response(200, content=_VOS_INFO),
}


def _reject_sync_info(filesystem: AsyncFileSystem) -> AsyncFileSystem:
    def reject_sync(*args: object, **kwargs: object) -> object:
        del args, kwargs
        message = "info must not use a public synchronous facade"
        raise AssertionError(message)

    filesystem.info = reject_sync
    return filesystem


def _expected(values: dict[str, object]) -> str:
    return f"{pprint.pformat(values, width=80, sort_dicts=True)}\n"


def _exercise_info(
    source_name: str,
    source: _ProbedSource[AsyncFileSystem],
    path: str,
    expected: str,
) -> None:
    result = CliRunner().invoke(
        App({source_name: source}).typer_app,
        ["info", f"{source_name}:{path}"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, expected, "")
    info_calls = [call for call in source.calls if call.operation == "info"]
    assert [(call.path, dict(call.kwargs)) for call in info_calls] == [(path, {})]
    assert not any(call.operation == "ls" for call in source.calls)
    assert [event.stage for event in source.lifecycle] == ["factory", "enter", "exit"]
    if source._close is not None:
        assert [event.stage for event in source.close_calls] == ["close"]
    assert [(call.exc_type, call.exception) for call in source.exit_calls] == [(None, None)]


def test_adapted_memory_info_preserves_sparse_shape_and_datetime_extra(
    monkeypatch,
) -> None:
    monkeypatch.setattr(MemoryFileSystem, "store", {})
    monkeypatch.setattr(MemoryFileSystem, "pseudo_dirs", [""])
    monkeypatch.setattr(MemoryFileSystem, "_cache", {})
    created = datetime(2026, 7, 17, 18, tzinfo=timezone.utc)

    def make_filesystem() -> AsyncFileSystemWrapper:
        MemoryFileSystem.store.clear()
        MemoryFileSystem.pseudo_dirs[:] = [""]
        MemoryFileSystem.clear_instance_cache()
        filesystem = MemoryFileSystem()
        filesystem.pipe_file("/docs/report.txt", b"abc")
        filesystem.store["/docs/report.txt"].created = created
        return _reject_sync_info(AsyncFileSystemWrapper(filesystem, asynchronous=True))

    source = _ProbedSource(make_filesystem)
    expected = _expected(
        {
            "name": "report.txt",
            "kind": "file",
            "size": 3,
            "mtime": None,
            "mode": None,
            "nlink": None,
            "owner": None,
            "group": None,
            "link_target": None,
            "extra": {"created": created},
        }
    )

    _exercise_info("memory", source, "/docs/report.txt", expected)

    assert all(
        isinstance(filesystem, AsyncFileSystemWrapper) and isinstance(filesystem.sync_fs, MemoryFileSystem)
        for filesystem in source.filesystems
    )


def test_adapted_local_info_preserves_rich_shape_and_local_extras(
    tmp_path: Path,
) -> None:
    report = tmp_path / "report.txt"
    report.write_bytes(b"abc")
    report.chmod(0o640)
    os.utime(report, (1_784_311_200, 1_784_311_200))
    metadata = report.stat(follow_symlinks=False)
    path = report.resolve().as_posix()
    source = _ProbedSource(
        lambda: _reject_sync_info(
            AsyncFileSystemWrapper(
                LocalFileSystem(skip_instance_cache=True),
                asynchronous=True,
            )
        )
    )
    expected = _expected(
        {
            "name": "report.txt",
            "kind": "file",
            "size": 3,
            "mtime": float(metadata.st_mtime),
            "mode": metadata.st_mode,
            "nlink": metadata.st_nlink,
            "owner": metadata.st_uid,
            "group": metadata.st_gid,
            "link_target": None,
            "extra": {
                "created": getattr(metadata, "st_birthtime", metadata.st_ctime),
                "ino": metadata.st_ino,
            },
        }
    )

    _exercise_info("local", source, path, expected)

    assert all(
        isinstance(filesystem, AsyncFileSystemWrapper) and isinstance(filesystem.sync_fs, LocalFileSystem)
        for filesystem in source.filesystems
    )


def test_native_vosfs_info_exposes_remote_extras_over_mocked_transport() -> None:
    transports: list[_StrictMockTransport] = []

    def make_filesystem() -> AsyncFileSystem:
        transport = _StrictMockTransport(_RESPONSES)
        transports.append(transport)
        return _reject_sync_info(
            VOSpaceFileSystem(
                _BASE_URL,
                transport=transport,
                asynchronous=True,
                skip_instance_cache=True,
                trust_env=False,
            )
        )

    source = _ProbedSource(make_filesystem, close=_close_vosfs)
    properties = {
        "ivo://ivoa.net/vospace/core#length": "3",
        "ivo://ivoa.net/vospace/core#mtime": "2026-07-17T18:00:00Z",
        "ivo://ivoa.net/vospace/core#MD5": "abc123",
        "ivo://ivoa.net/vospace/core#contenttype": "text/plain",
        "ivo://example.test/project": "science",
    }
    expected = _expected(
        {
            "name": "report.txt",
            "kind": "file",
            "size": 3,
            "mtime": 1_784_311_200.0,
            "mode": None,
            "nlink": None,
            "owner": None,
            "group": None,
            "link_target": None,
            "extra": {
                "uri": f"vos://{_AUTHORITY}/docs/report.txt",
                "md5": "abc123",
                "content_type": "text/plain",
                "properties": properties,
            },
        }
    )

    _exercise_info("vos", source, "/docs/report.txt", expected)

    assert transports[0].requests == [
        ("GET", "/arc/capabilities"),
        ("GET", "/arc/nodes/docs/report.txt"),
    ]
    assert transports[0].closed
    assert all(isinstance(fs, VOSpaceFileSystem) for fs in source.filesystems)
