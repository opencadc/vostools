"""Same-source two-operand ``cp`` tests through the public embedded-command seam."""

from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
from pathlib import Path
from types import MappingProxyType, MethodType, SimpleNamespace
from typing import NoReturn

import pytest
import typer
from typer.testing import CliRunner

from fss_cli import App

from ._support import (
    _cp_verified,
    _invoke,
    _RecordingFileSystem,
    _RecordingSource,
    _source_must_not_run,
)


def _file_source(  # noqa: PLR0913 - compact recording fixture.
    events: list[tuple[object, ...]] | None = None,
    *,
    content: bytes = b"payload",
    source_path: str = "/docs/notes.txt",
    parent: str = "/docs",
    file_contents: dict[str, bytes] | None = None,
    directories: set[str] | None = None,
    info_by_path: dict[str, object] | None = None,
    **kwargs: object,
) -> _RecordingSource:
    contents = dict(file_contents or {})
    contents.setdefault(source_path, content)
    dirs = set(directories or ())
    dirs.add(parent)
    dirs.add("/")
    return _RecordingSource(
        events if events is not None else [],
        file_contents=contents,
        directories=dirs,
        info_by_path=info_by_path or {},
        get_file_content=content,
        **kwargs,  # type: ignore[arg-type]
    )


def test_cp_copies_one_file_without_stdout() -> None:
    events: list[tuple[object, ...]] = []
    source = _file_source(events)

    result = _invoke(
        "cp",
        ["memory:/docs/notes.txt", "memory:/docs/copy.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == _cp_verified("memory:/docs/copy.txt", "copied", 7)
    assert source.file_contents["/docs/copy.txt"] == b"payload"
    assert source.file_contents["/docs/notes.txt"] == b"payload"
    cp_events = [event for event in events if event[0] == "cp_file"]
    assert len(cp_events) == 1
    assert cp_events[0][2:4] == ("/docs/notes.txt", "/docs/copy.txt")
    assert not [event for event in events if event[0] == "get_file"]


def test_cp_preserves_backend_error_when_its_diagnostic_write_fails(
    monkeypatch,
) -> None:
    backend_error = PermissionError("denied")
    renderer_error = RuntimeError("stderr failed")
    source = _file_source(
        info_by_path={"/docs/notes.txt": backend_error},
    )

    def fail_diagnostic(
        _message: object = None,
        *args: object,
        **kwargs: object,
    ) -> None:
        del args
        if kwargs.get("err") is True:
            raise renderer_error
        raise AssertionError

    monkeypatch.setattr(typer, "echo", fail_diagnostic)

    result = _invoke(
        "cp",
        ["memory:/docs/notes.txt", "memory:/docs/copy.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.exception is renderer_error
    assert result.stdout == ""
    assert result.stderr == ""
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is PermissionError
    assert exception is backend_error
    assert traceback is not None


def test_cp_reuses_destination_directory_info_for_same_source_parent() -> None:
    events: list[tuple[object, ...]] = []
    source = _file_source(events, directories={"/", "/docs", "/target"})

    result = _invoke(
        "cp",
        ["memory:/docs/notes.txt", "memory:/target"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("memory:/target/notes.txt", "copied", 7),
    )
    assert [event[2] for event in events if event[0] == "info"] == [
        "/docs/notes.txt",
        "/target",
        "/target/notes.txt",
        "/target/notes.txt",
    ]


def test_cp_copies_multiple_files_into_existing_directory_in_argv_order() -> None:
    source = _file_source(
        file_contents={
            "/docs/first.txt": b"first",
            "/docs/second.txt": b"second",
        },
        directories={"/", "/docs", "/target"},
    )

    result = _invoke(
        "cp",
        [
            "memory:/docs/first.txt",
            "memory:/docs/second.txt",
            "memory:/target",
        ],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("memory:/target/first.txt", "copied", 5) + _cp_verified("memory:/target/second.txt", "copied", 6),
    )
    assert source.file_contents["/target/first.txt"] == b"first"
    assert source.file_contents["/target/second.txt"] == b"second"
    assert [event[2:4] for event in source.events if event[0] == "cp_file"] == [
        ("/docs/first.txt", "/target/first.txt"),
        ("/docs/second.txt", "/target/second.txt"),
    ]


def test_cp_reuses_destination_directory_info_for_multi_source_parents() -> None:
    events: list[tuple[object, ...]] = []
    source = _file_source(
        events,
        file_contents={
            "/docs/first.txt": b"first",
            "/docs/second.txt": b"second",
        },
        directories={"/", "/docs", "/target"},
    )

    result = _invoke(
        "cp",
        [
            "memory:/docs/first.txt",
            "memory:/docs/second.txt",
            "memory:/target",
        ],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("memory:/target/first.txt", "copied", 5) + _cp_verified("memory:/target/second.txt", "copied", 6),
    )
    assert sorted(event[2] for event in events if event[0] == "info") == sorted(
        [
            "/target",
            "/docs/first.txt",
            "/target",
            "/target/first.txt",
            "/target/first.txt",
            "/docs/second.txt",
            "/target",
            "/target/second.txt",
            "/target/second.txt",
        ]
    )


def test_cp_acquires_multi_source_names_once_in_argv_order() -> None:
    events: list[tuple[object, ...]] = []
    acquisition: list[str] = []
    first = _file_source(
        events,
        file_contents={"/docs/first.txt": b"first"},
        directories={"/", "/docs"},
    )
    second = _file_source(
        events,
        file_contents={"/docs/second.txt": b"second"},
        directories={"/", "/docs"},
    )
    destination = _file_source(
        events,
        source_path="/other.txt",
        directories={"/", "/target"},
    )

    def named_source(name: str, source: _RecordingSource):
        def factory():
            acquisition.append(name)
            return source()

        return factory

    result = _invoke(
        "cp",
        [
            "first:/docs/first.txt",
            "second:/docs/second.txt",
            "destination:/target",
        ],
        sources={
            "first": named_source("first", first),
            "second": named_source("second", second),
            "destination": named_source("destination", destination),
        },
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("destination:/target/first.txt", "uploaded", 5)
        + _cp_verified("destination:/target/second.txt", "uploaded", 6),
    )
    assert acquisition == ["first", "second", "destination"]
    assert first.call_count == second.call_count == destination.call_count == 1
    assert destination.file_contents["/target/first.txt"] == b"first"
    assert destination.file_contents["/target/second.txt"] == b"second"


def test_cp_replaces_duplicate_basenames_in_argv_order() -> None:
    first = _file_source(
        file_contents={"/first/item.txt": b"first"},
        source_path="/first/item.txt",
        parent="/first",
    )
    second = _file_source(
        file_contents={"/second/item.txt": b"second"},
        source_path="/second/item.txt",
        parent="/second",
    )
    destination = _file_source(
        source_path="/other.txt",
        directories={"/", "/target"},
    )

    result = _invoke(
        "cp",
        [
            "first:/first/item.txt",
            "second:/second/item.txt",
            "destination:/target",
        ],
        sources={"first": first, "second": second, "destination": destination},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("destination:/target/item.txt", "uploaded", 5)
        + _cp_verified("destination:/target/item.txt", "uploaded", 6),
    )
    assert destination.file_contents["/target/item.txt"] == b"second"


def test_cp_leaves_completed_multi_source_targets_after_later_failure() -> None:
    first = _file_source(
        file_contents={"/docs/first.txt": b"first"},
        directories={"/", "/docs"},
    )
    second = _file_source(
        file_contents={"/docs/second.txt": b"second"},
        directories={"/", "/docs"},
    )
    destination = _file_source(
        source_path="/other.txt",
        directories={"/", "/target"},
        put_file_by_path={"/target/second.txt": OSError("upload failed")},
    )

    result = _invoke(
        "cp",
        [
            "first:/docs/first.txt",
            "second:/docs/second.txt",
            "destination:/target",
        ],
        sources={"first": first, "second": second, "destination": destination},
    )

    assert result.exit_code == 1
    assert destination.file_contents["/target/first.txt"] == b"first"
    assert "/target/second.txt" not in destination.file_contents
    assert first.file_contents["/docs/first.txt"] == b"first"
    assert second.file_contents["/docs/second.txt"] == b"second"


def test_cp_rejects_other_source_type_mid_multi_source_sequence() -> None:
    source = _file_source(
        file_contents={"/docs/first.txt": b"first"},
        directories={"/", "/docs", "/target"},
        info_by_path={
            "/docs/other": MappingProxyType({"type": "link", "size": 5}),
        },
    )

    result = _invoke(
        "cp",
        [
            "memory:/docs/first.txt",
            "memory:/docs/other",
            "memory:/target",
        ],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        _cp_verified("memory:/target/first.txt", "copied", 5) + "cp: memory:/docs/other: incompatible result\n",
    )
    assert source.file_contents["/target/first.txt"] == b"first"
    assert "/target/other" not in source.file_contents
    assert not any(event[0] in {"rm", "rmdir"} for event in source.events)


def test_cp_rejects_directory_source_mid_multi_source_sequence() -> None:
    source = _file_source(
        file_contents={"/docs/first.txt": b"first"},
        directories={"/", "/docs", "/docs/nested", "/target"},
    )

    result = _invoke(
        "cp",
        [
            "memory:/docs/first.txt",
            "memory:/docs/nested",
            "memory:/target",
        ],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        _cp_verified("memory:/target/first.txt", "copied", 5) + "cp: memory:/docs/nested: is a directory\n",
    )
    assert source.file_contents["/target/first.txt"] == b"first"
    assert "/target/nested" not in source.file_contents
    assert [event[0] for event in source.events].count("cp_file") == 1
    assert not any(event[0] in {"rm", "rmdir"} for event in source.events)


def test_cp_replaces_existing_multi_source_target_file() -> None:
    source = _file_source(
        file_contents={
            "/docs/first.txt": b"first",
            "/docs/second.txt": b"second",
            "/target/second.txt": b"stale",
        },
        directories={"/", "/docs", "/target"},
    )

    result = _invoke(
        "cp",
        [
            "memory:/docs/first.txt",
            "memory:/docs/second.txt",
            "memory:/target",
        ],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("memory:/target/first.txt", "copied", 5) + _cp_verified("memory:/target/second.txt", "copied", 6),
    )
    assert source.file_contents["/target/first.txt"] == b"first"
    assert source.file_contents["/target/second.txt"] == b"second"
    assert source.file_contents["/docs/first.txt"] == b"first"
    assert source.file_contents["/docs/second.txt"] == b"second"


@pytest.mark.parametrize(
    ("label", "payload"),
    [
        ("empty", b""),
        ("binary", b"\x00\xff\xfe multi"),
        ("large", b"x" * (1 << 20)),
    ],
    ids=["empty", "binary", "large"],
)
def test_cp_copies_empty_binary_and_large_multi_source_payloads(
    label: str,
    payload: bytes,
) -> None:
    source = _file_source(
        file_contents={
            f"/docs/{label}-a.bin": payload,
            f"/docs/{label}-b.bin": payload,
        },
        directories={"/", "/docs", "/target"},
    )

    result = _invoke(
        "cp",
        [
            f"memory:/docs/{label}-a.bin",
            f"memory:/docs/{label}-b.bin",
            "memory:/target",
        ],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified(f"memory:/target/{label}-a.bin", "copied", len(payload))
        + _cp_verified(f"memory:/target/{label}-b.bin", "copied", len(payload)),
    )
    assert source.file_contents[f"/target/{label}-a.bin"] == payload
    assert source.file_contents[f"/target/{label}-b.bin"] == payload
    assert source.file_contents[f"/docs/{label}-a.bin"] == payload
    assert source.file_contents[f"/docs/{label}-b.bin"] == payload


def test_cp_rejects_other_cross_source_type_mid_multi_source_sequence() -> None:
    first = _file_source(
        file_contents={"/docs/first.txt": b"first"},
        directories={"/", "/docs"},
    )
    other = _file_source(
        source_path="/docs/other",
        info_by_path={
            "/docs/other": MappingProxyType({"type": "link", "size": 5}),
        },
        directories={"/", "/docs"},
    )
    destination = _file_source(
        source_path="/other.txt",
        directories={"/", "/target"},
    )

    result = _invoke(
        "cp",
        [
            "first:/docs/first.txt",
            "other:/docs/other",
            "destination:/target",
        ],
        sources={"first": first, "other": other, "destination": destination},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        _cp_verified("destination:/target/first.txt", "uploaded", 5) + "cp: other:/docs/other: incompatible result\n",
    )
    assert destination.file_contents["/target/first.txt"] == b"first"
    assert "/target/other" not in destination.file_contents
    assert not any(event[0] in {"rm", "rmdir"} for event in first.events)
    assert not any(event[0] in {"rm", "rmdir"} for event in other.events)


def test_cp_rejects_file_as_multi_source_target_before_copy() -> None:
    source = _file_source(
        file_contents={
            "/docs/first.txt": b"first",
            "/docs/second.txt": b"second",
            "/docs/target": b"target",
        },
    )

    result = _invoke(
        "cp",
        [
            "memory:/docs/first.txt",
            "memory:/docs/second.txt",
            "memory:/docs/target",
        ],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: memory:/docs/target: not a directory\n",
    )
    assert not any(event[0] == "cp_file" for event in source.events)


def test_cp_reuses_configured_name_for_mixed_multi_source_sequence() -> None:
    shared = _file_source(
        file_contents={"/docs/first.txt": b"first", "/docs/third.txt": b"third"},
        directories={"/", "/docs", "/target"},
    )
    other = _file_source(
        file_contents={"/docs/second.txt": b"second"},
        directories={"/", "/docs"},
    )

    result = _invoke(
        "cp",
        [
            "shared:/docs/first.txt",
            "other:/docs/second.txt",
            "shared:/docs/third.txt",
            "shared:/target",
        ],
        sources={"shared": shared, "other": other},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("shared:/target/first.txt", "copied", 5)
        + _cp_verified("shared:/target/second.txt", "uploaded", 6)
        + _cp_verified("shared:/target/third.txt", "copied", 5),
    )
    assert shared.call_count == other.call_count == 1
    assert shared.file_contents["/target/first.txt"] == b"first"
    assert shared.file_contents["/target/second.txt"] == b"second"
    assert shared.file_contents["/target/third.txt"] == b"third"


def test_cp_keeps_verified_target_after_later_multi_source_verification_failure() -> None:
    first = _file_source(
        file_contents={"/docs/first.txt": b"first"},
        directories={"/", "/docs"},
    )
    second = _file_source(
        file_contents={"/docs/second.txt": b"second"},
        directories={"/", "/docs"},
        info_by_path={"/docs/second.txt": MappingProxyType({"type": "file", "size": 6, "checksum": "source-token"})},
    )
    destination = _file_source(
        source_path="/other.txt",
        directories={"/", "/target"},
        post_info_by_path={
            "/target/second.txt": MappingProxyType({"type": "file", "size": 6, "checksum": "destination-token"})
        },
    )

    def corrupt_upload(_local_path: str, remote_path: str) -> None:
        filesystem = destination.contexts[0].filesystem
        filesystem._file_contents[remote_path] = b"wrong!"
        filesystem._pending_cp_verify.add(remote_path)
        destination.file_contents[remote_path] = b"wrong!"

    destination.put_file_by_path = {"/target/second.txt": corrupt_upload}
    result = _invoke(
        "cp",
        [
            "first:/docs/first.txt",
            "second:/docs/second.txt",
            "destination:/target",
        ],
        sources={"first": first, "second": second, "destination": destination},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        _cp_verified("destination:/target/first.txt", "uploaded", 5)
        + "cp: destination:/target/second.txt: uploaded 6 bytes\n"
        + "cp: destination:/target/second.txt: metadata mismatch (checksum); destination residue may remain\n",
    )
    assert destination.file_contents["/target/first.txt"] == b"first"
    assert destination.file_contents["/target/second.txt"] == b"wrong!"


def test_cp_cleans_later_cross_source_stage_on_multi_source_cancellation() -> None:
    temporary_paths: list[str] = []
    first = _file_source(
        file_contents={"/docs/first.txt": b"first"},
        directories={"/", "/docs"},
    )
    second = _file_source(
        file_contents={"/docs/second.txt": b"second"},
        directories={"/", "/docs"},
    )
    destination = _file_source(
        source_path="/other.txt",
        directories={"/", "/target"},
    )

    def cancel_staging(local_path: str) -> NoReturn:
        temporary_paths.append(local_path)
        Path(local_path).write_bytes(b"second")
        raise asyncio.CancelledError

    second.get_file_by_path = {"/docs/second.txt": cancel_staging}

    with pytest.raises(asyncio.CancelledError):
        _invoke(
            "cp",
            [
                "first:/docs/first.txt",
                "second:/docs/second.txt",
                "destination:/target",
            ],
            sources={"first": first, "second": second, "destination": destination},
        )

    assert destination.file_contents["/target/first.txt"] == b"first"
    assert "/target/second.txt" not in destination.file_contents
    assert len(temporary_paths) == 1
    assert not Path(temporary_paths[0]).exists()
    assert len(first.exit_calls) == len(second.exit_calls) == len(destination.exit_calls) == 1


def test_cp_drains_current_download_before_staging_and_source_cleanup() -> None:
    source = _file_source()
    destination = _file_source(
        source_path="/other.txt",
        parent="/out",
        directories={"/", "/out"},
    )
    order: list[str] = []
    temporary_paths: list[str] = []

    @asynccontextmanager
    async def source_factory():
        async with source() as filesystem:
            owner = asyncio.current_task()
            assert owner is not None

            async def get_file(
                self: _RecordingFileSystem,
                remote: str,
                local: str,
                **kwargs: object,
            ) -> None:
                del self, remote, kwargs
                temporary_paths.append(local)
                owner.cancel()
                await asyncio.sleep(0)
                Path(local).write_bytes(b"payload")  # noqa: ASYNC240
                order.append("drained")

            filesystem._get_file = MethodType(get_file, filesystem)  # type: ignore[method-assign]
            try:
                yield filesystem
            finally:
                order.append("source exit")

    @asynccontextmanager
    async def destination_factory():
        async with destination() as filesystem:
            try:
                yield filesystem
            finally:
                order.append("destination exit")

    with pytest.raises(asyncio.CancelledError):
        _invoke(
            "cp",
            ["source:/docs/notes.txt", "destination:/out/copy.txt"],
            sources={
                "source": source_factory,
                "destination": destination_factory,
            },
        )

    assert order == ["drained", "destination exit", "source exit"]
    assert len(temporary_paths) == 1
    assert not Path(temporary_paths[0]).exists()
    assert "/out/copy.txt" not in destination.file_contents


def test_cp_requires_existing_directory_for_multiple_sources() -> None:
    source = _file_source(
        file_contents={"/docs/first.txt": b"first", "/docs/second.txt": b"second"},
        directories={"/", "/docs"},
        info_by_path={"/missing": FileNotFoundError("missing")},
    )

    result = _invoke(
        "cp",
        [
            "memory:/docs/first.txt",
            "memory:/docs/second.txt",
            "memory:/missing",
        ],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: memory:/missing: not found\n",
    )
    assert not any(event[0] == "cp_file" for event in source.events)


@pytest.mark.parametrize(
    "payload",
    [
        b"",
        b"\0\xff cross-source",
        b"x" * (1 << 20),
    ],
    ids=["empty", "binary", "large"],
)
def test_cp_copies_payload_between_distinct_configured_sources(payload: bytes) -> None:
    source = _file_source(content=payload)
    destination = _file_source(
        source_path="/other.txt",
        parent="/out",
        directories={"/", "/out"},
    )

    result = _invoke(
        "cp",
        ["source:/docs/notes.txt", "destination:/out/copy.txt"],
        sources={"source": source, "destination": destination},
    )

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == _cp_verified("destination:/out/copy.txt", "uploaded", len(payload))
    assert source.file_contents["/docs/notes.txt"] == payload
    assert destination.file_contents["/out/copy.txt"] == payload
    assert [event[0] for event in source.events].count("get_file") == 1
    assert [event[0] for event in destination.events].count("put_file") == 1
    assert [event[0] for event in destination.events].count("get_file") == 0


def test_cp_reuses_destination_directory_info_for_cross_source_parent() -> None:
    source = _file_source()
    destination = _file_source(
        source_path="/other.txt",
        directories={"/", "/target"},
    )

    result = _invoke(
        "cp",
        ["source:/docs/notes.txt", "destination:/target"],
        sources={"source": source, "destination": destination},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("destination:/target/notes.txt", "uploaded", 7),
    )
    assert [event[2] for event in destination.events if event[0] == "info"] == [
        "/target",
        "/target/notes.txt",
        "/target/notes.txt",
    ]


def test_cp_rejects_cross_source_same_path_on_shared_backend_before_mutation() -> None:
    source = _file_source()
    filesystem = _RecordingFileSystem(source, 1)

    @asynccontextmanager
    async def shared_filesystem() -> _RecordingFileSystem:
        yield filesystem

    result = _invoke(
        "cp",
        ["left:/docs/notes.txt", "right:/docs/notes.txt"],
        sources={"left": shared_filesystem, "right": shared_filesystem},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: left:/docs/notes.txt: same path\n",
    )
    assert not any(event[0] in {"get_file", "put_file"} for event in source.events)


def test_cp_accepts_same_size_cross_source_destination_without_shared_token() -> None:
    source = _file_source(content=b"correct")
    destination = _file_source(
        source_path="/other.txt",
        parent="/out",
        directories={"/", "/out"},
    )

    def corrupt_upload(_local_path: str, remote_path: str) -> None:
        filesystem = destination.contexts[0].filesystem
        filesystem._file_contents[remote_path] = b"corrupt"
        destination.file_contents[remote_path] = b"corrupt"

    destination.put_file_by_path = {"/out/copy.txt": corrupt_upload}
    result = _invoke(
        "cp",
        ["source:/docs/notes.txt", "destination:/out/copy.txt"],
        sources={"source": source, "destination": destination},
    )

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == _cp_verified("destination:/out/copy.txt", "uploaded", 7)
    assert source.file_contents["/docs/notes.txt"] == b"correct"
    assert destination.file_contents["/out/copy.txt"] == b"corrupt"
    assert [event[0] for event in source.events].count("get_file") == 1
    assert [event[0] for event in destination.events].count("get_file") == 0


def test_cp_hides_local_temporary_path_in_cross_source_staging_diagnostic() -> None:
    staged_paths: list[str] = []

    def fail_staging(local_path: str) -> None:
        staged_paths.append(local_path)
        raise OSError(f"local staging failed: {local_path}")  # noqa: EM102, TRY003

    source = _file_source(get_file_by_path={"/docs/notes.txt": fail_staging})
    destination = _file_source(
        source_path="/other.txt",
        parent="/out",
        directories={"/", "/out"},
    )

    result = _invoke(
        "cp",
        ["source:/docs/notes.txt", "destination:/out/copy.txt"],
        sources={"source": source, "destination": destination},
    )

    assert result.exit_code == 1
    assert result.stderr == "cp: source:/docs/notes.txt: staging failure (OSError)\n"
    assert len(staged_paths) == 1
    assert staged_paths[0] not in result.stderr


@pytest.mark.parametrize(
    ("destination_info", "verdict"),
    [
        (MappingProxyType({"type": "directory", "size": 0}), "not a file after upload"),
        (MappingProxyType({"type": "file", "size": 3}), "size mismatch (expected 6, observed 3)"),
    ],
    ids=["wrong-type", "truncated"],
)
def test_cp_rejects_invalid_cross_source_destination_after_upload(
    destination_info: MappingProxyType,
    verdict: str,
) -> None:
    source = _file_source(content=b"abcdef")
    destination = _file_source(
        source_path="/other.txt",
        parent="/out",
        directories={"/", "/out"},
        post_info_by_path={"/out/copy.txt": destination_info},
    )

    result = _invoke(
        "cp",
        ["source:/docs/notes.txt", "destination:/out/copy.txt"],
        sources={"source": source, "destination": destination},
    )

    assert result.exit_code == 1
    assert result.stderr == (
        "cp: destination:/out/copy.txt: uploaded 6 bytes\n"
        f"cp: destination:/out/copy.txt: {verdict}; destination residue may remain\n"
    )


def test_cp_appends_basename_when_destination_is_directory() -> None:
    source = _file_source(directories={"/", "/docs", "/docs/out"})

    result = _invoke(
        "cp",
        ["memory:/docs/notes.txt", "memory:/docs/out"],
        sources={"memory": source},
    )

    assert result.exit_code == 0
    assert source.file_contents["/docs/out/notes.txt"] == b"payload"
    cp_events = [event for event in source.events if event[0] == "cp_file"]
    assert cp_events[0][2:4] == ("/docs/notes.txt", "/docs/out/notes.txt")


@pytest.mark.parametrize("source_path", ["/", "///"])
def test_cp_preserves_root_source_destination(source_path: str) -> None:
    source = _file_source(
        source_path=source_path,
        parent="/",
    )
    destination = _file_source(
        source_path="/other.txt",
        directories={"/", "/out"},
    )

    result = _invoke(
        "cp",
        [f"source:{source_path}", "destination:/out"],
        sources={"source": source, "destination": destination},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("destination:/out/", "uploaded", 7),
    )
    put_events = [event for event in destination.events if event[0] == "put_file"]
    assert put_events[0][2] == "/out/"


def test_cp_replaces_existing_destination_file() -> None:
    source = _file_source(
        file_contents={
            "/docs/notes.txt": b"new-bytes",
            "/docs/copy.txt": b"old-bytes",
        }
    )

    result = _invoke(
        "cp",
        ["memory:/docs/notes.txt", "memory:/docs/copy.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 0
    assert source.file_contents["/docs/copy.txt"] == b"new-bytes"
    assert source.file_contents["/docs/notes.txt"] == b"new-bytes"


def test_cp_rejects_same_path_before_mutation() -> None:
    source = _file_source()

    result = _invoke(
        "cp",
        ["memory:/docs/notes.txt", "memory:/docs/notes.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "cp: memory:/docs/notes.txt: same path\n"
    assert [event[0] for event in source.events].count("cp_file") == 0


def test_cp_rejects_directory_destination_collision_before_mutation() -> None:
    source = _file_source(
        directories={"/", "/docs", "/docs/out", "/docs/out/notes.txt"},
    )

    result = _invoke(
        "cp",
        ["memory:/docs/notes.txt", "memory:/docs/out"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "cp: memory:/docs/out: incompatible result\n"
    assert [event[0] for event in source.events].count("cp_file") == 0


def test_cp_rejects_missing_parent() -> None:
    source = _file_source(
        info_by_path={"/missing": FileNotFoundError("missing")},
        directories={"/"},
    )

    result = _invoke(
        "cp",
        ["memory:/docs/notes.txt", "memory:/missing/copy.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "cp: memory:/missing/copy.txt: not found\n"
    assert [event[0] for event in source.events].count("cp_file") == 0


def test_cp_rejects_parent_that_is_a_file() -> None:
    source = _file_source(
        file_contents={
            "/docs/notes.txt": b"payload",
            "/docs/parent": b"not-a-dir",
        },
        directories={"/", "/docs"},
    )

    result = _invoke(
        "cp",
        ["memory:/docs/notes.txt", "memory:/docs/parent/copy.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "cp: memory:/docs/parent/copy.txt: not a directory\n"
    assert [event[0] for event in source.events].count("cp_file") == 0


def test_cp_rejects_directory_source() -> None:
    source = _RecordingSource(
        [],
        info_by_path={"/docs": MappingProxyType({"type": "directory", "size": 0})},
        directories={"/", "/docs"},
    )

    result = _invoke(
        "cp",
        ["memory:/docs", "memory:/docs/copy"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "cp: memory:/docs: is a directory\n"
    assert [event[0] for event in source.events].count("cp_file") == 0


def test_cp_acquires_destination_before_cross_source_backend_work() -> None:
    source = _file_source()

    result = _invoke(
        "cp",
        ["alpha:/docs/notes.txt", "beta:/two"],
        sources={"alpha": source, "beta": _source_must_not_run},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert "beta: source factory failure" in result.stderr
    assert [event[0] for event in source.events] == ["factory", "enter", "exit"]


def test_cp_uses_distinct_names_even_when_backends_are_similar() -> None:
    left = _file_source()
    right = _file_source(
        source_path="/other.txt",
        parent="/docs",
        directories={"/", "/docs"},
    )

    result = _invoke(
        "cp",
        ["alpha:/docs/notes.txt", "beta:/docs/copy.txt"],
        sources={"alpha": left, "beta": right},
    )

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == _cp_verified("beta:/docs/copy.txt", "uploaded", 7)
    assert left.call_count == 1
    assert right.call_count == 1
    assert right.file_contents["/docs/copy.txt"] == b"payload"


def test_cp_rejects_one_operand() -> None:
    result = _invoke("cp", ["memory:/one"])

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == "cp: missing mapped filesystem operand\n"


@pytest.mark.parametrize(
    ("arguments", "diagnostic"),
    [
        (
            ["memory:/docs/first.txt", "other:/docs/second.txt", "memory:/target"],
            "cp: other:/docs/second.txt: unknown filesystem (known: memory)\n",
        ),
        (
            ["memory:/docs/first.txt", "memory:relative", "memory:/target"],
            "cp: memory:relative: invalid mapped filesystem operand\n",
        ),
        (
            ["memory:/docs/first.txt", "memory:/docs/second.txt", "memory:target"],
            "cp: memory:target: invalid mapped filesystem operand\n",
        ),
    ],
    ids=["unknown-source", "malformed-source", "malformed-target"],
)
def test_cp_rejects_invalid_multi_source_operand_before_source_entry(
    arguments: list[str],
    diagnostic: str,
) -> None:
    result = _invoke("cp", arguments)

    assert (result.exit_code, result.stdout, result.stderr) == (2, "", diagnostic)


def test_cp_accepts_operands_after_option_terminator() -> None:
    source = _file_source()

    result = _invoke(
        "cp",
        ["--", "memory:/docs/notes.txt", "memory:/docs/copy.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == _cp_verified("memory:/docs/copy.txt", "copied", 7)


@pytest.mark.parametrize(
    ("label", "payload"),
    [
        ("empty", b""),
        ("binary", b"\x00\xff\xfe binary"),
        ("large", b"x" * (1 << 20)),
    ],
)
def test_cp_copies_empty_binary_and_large_payloads(label: str, payload: bytes) -> None:
    del label
    source = _file_source(content=payload)

    result = _invoke(
        "cp",
        ["memory:/docs/notes.txt", "memory:/docs/copy.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 0
    assert source.file_contents["/docs/copy.txt"] == payload


def test_cp_uses_pre_copy_metadata_snapshot_without_redownloading_source() -> None:
    source = _file_source(content=b"original")

    def copy_then_mutate(path1: str, path2: str) -> None:
        filesystem = source.contexts[0].filesystem
        filesystem._file_contents[path2] = filesystem._file_contents[path1]
        source.file_contents[path2] = filesystem._file_contents[path1]
        filesystem._file_contents[path1] = b"changed-after-copy"
        source.file_contents[path1] = b"changed-after-copy"

    source.cp_file_hook = copy_then_mutate

    result = _invoke(
        "cp",
        ["memory:/docs/notes.txt", "memory:/docs/copy.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 0
    assert result.stdout == ""
    assert result.stderr == _cp_verified("memory:/docs/copy.txt", "copied", 8)
    assert source.file_contents["/docs/notes.txt"] == b"changed-after-copy"
    assert source.file_contents["/docs/copy.txt"] == b"original"
    assert not [event for event in source.events if event[0] == "get_file"]


def test_cp_reports_truncated_destination_as_size_mismatch() -> None:
    source = _file_source(content=b"abcdef")

    def truncate_destination(path1: str, path2: str) -> None:
        del path1
        filesystem = source.contexts[0].filesystem
        filesystem._file_contents[path2] = b"abc"
        source.file_contents[path2] = b"abc"

    source.cp_file_hook = truncate_destination

    result = _invoke(
        "cp",
        ["memory:/docs/notes.txt", "memory:/docs/copy.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stderr == (
        "cp: memory:/docs/copy.txt: copied 6 bytes\n"
        "cp: memory:/docs/copy.txt: size mismatch (expected 6, observed 3); destination residue may remain\n"
    )


def test_cp_reports_copy_exception_as_uncertain_residue() -> None:
    source = _file_source(cp_file_error=RuntimeError("relay-failed"))

    result = _invoke(
        "cp",
        ["memory:/docs/notes.txt", "memory:/docs/copy.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == ("cp: memory:/docs/copy.txt: uncertain mutation state; destination residue may remain\n")


def test_cp_never_deletes_source_on_failure() -> None:
    source = _file_source(cp_file_error=OSError("boom"))

    result = _invoke(
        "cp",
        ["memory:/docs/notes.txt", "memory:/docs/copy.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert source.file_contents["/docs/notes.txt"] == b"payload"
    assert [event[0] for event in source.events].count("rm_file") == 0
    assert [event[0] for event in source.events].count("rm") == 0


@pytest.mark.parametrize(
    "control",
    [asyncio.CancelledError(), type("_ControlFlow", (BaseException,), {})("stop")],
)
def test_cp_preserves_control_flow(control: BaseException) -> None:
    source = _file_source(cp_file_error=control)

    with pytest.raises(type(control)) as caught:
        _invoke(
            "cp",
            ["memory:/docs/notes.txt", "memory:/docs/copy.txt"],
            sources={"memory": source},
        )

    assert type(caught.value) is type(control)
    if not isinstance(control, asyncio.CancelledError):
        assert caught.value is control


def test_cp_refuses_an_active_same_thread_event_loop(monkeypatch) -> None:
    real_run = asyncio.run
    recording_run = pytest.importorskip("unittest.mock").Mock(wraps=real_run)

    async def invoke() -> object:
        monkeypatch.setattr(asyncio, "run", recording_run)
        return _invoke("cp", ["memory:/a", "memory:/b"])

    result = real_run(invoke())

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "cp: cannot run from an active event loop\n"
    assert recording_run.call_count == 0


def test_cp_reports_unknown_names_with_locale_sorted_known_names() -> None:
    result = _invoke(
        "cp",
        ["other:/a", "other:/b"],
        sources={
            "zeta": _source_must_not_run,
            "alpha": _source_must_not_run,
        },
    )

    assert result.exit_code == 2
    assert result.stdout == ""
    assert result.stderr == ("cp: other:/a: unknown filesystem (known: alpha, zeta)\n")


@pytest.mark.parametrize(
    "capabilities",
    [
        None,
        {},
        {"recursion": {}},
        {"recursion": {"remove": True}},
    ],
)
def test_cp_recursive_copy_defaults_enabled_for_partial_capabilities(
    capabilities,
) -> None:
    result = CliRunner().invoke(
        App(
            {"memory": _source_must_not_run},
            capabilities=capabilities,
        ).typer_app,
        ["cp", "-R", "memory:/", "memory:/out"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        2,
        "",
        "cp: memory:/: source root unsupported\n",
    )


def test_cp_cancels_without_claiming_success() -> None:
    source = _file_source()

    def cancel(_path1: str, _path2: str) -> NoReturn:
        raise asyncio.CancelledError

    source.cp_file_hook = cancel

    with pytest.raises(asyncio.CancelledError):
        _invoke(
            "cp",
            ["memory:/docs/notes.txt", "memory:/docs/copy.txt"],
            sources={"memory": source},
        )
    assert "/docs/copy.txt" not in source.contexts[0].filesystem._file_contents


def test_cp_uses_exact_configured_name_identity() -> None:
    source = _file_source()

    result = _invoke(
        "cp",
        ["vault:/docs/notes.txt", "vault:/docs/copy.txt"],
        sources={"vault": source},
    )

    assert result.exit_code == 0
    assert source.call_count == 1


def test_cp_accepts_same_size_destination_without_shared_token() -> None:
    source = _file_source(content=b"abcdef")

    def corrupt_same_size(path1: str, path2: str) -> None:
        filesystem = source.contexts[0].filesystem
        wrong = b"x" * len(filesystem._file_contents[path1])
        filesystem._file_contents[path2] = wrong
        source.file_contents[path2] = wrong

    source.cp_file_hook = corrupt_same_size

    result = _invoke(
        "cp",
        ["memory:/docs/notes.txt", "memory:/docs/copy.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 0
    assert result.stderr == _cp_verified("memory:/docs/copy.txt", "copied", 6)
    assert source.file_contents["/docs/copy.txt"] == b"xxxxxx"
    assert not [event for event in source.events if event[0] == "get_file"]


def test_cp_accepts_matching_normalized_metadata_tokens() -> None:
    source = _file_source(
        content=b"abcdef",
        info_by_path={
            "/docs/notes.txt": MappingProxyType(
                {
                    "type": "file",
                    "size": 6,
                    "ETag": '"etag-token"',
                    "md5": b"md5-token",
                    "content-md5": "content-token",
                    "checksum": "checksum-token",
                }
            )
        },
        post_info_by_path={
            "/docs/copy.txt": MappingProxyType(
                {
                    "type": "file",
                    "size": 6,
                    "etag": '"etag-token"',
                    "md5": b"md5-token",
                    "content_md5": "content-token",
                    "checksum": "checksum-token",
                }
            )
        },
    )

    result = _invoke(
        "cp",
        ["memory:/docs/notes.txt", "memory:/docs/copy.txt"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("memory:/docs/copy.txt", "copied", 6),
    )
    assert not [event for event in source.events if event[0] == "get_file"]
    mutation_index = next(index for index, event in enumerate(source.events) if event[0] == "cp_file")
    assert [event[:3] for event in source.events[mutation_index + 1 :] if event[0] == "info"] == [
        ("info", 1, "/docs/copy.txt")
    ]


def test_cp_rejects_when_any_shared_metadata_token_mismatches() -> None:
    source = _file_source(
        content=b"abcdef",
        info_by_path={
            "/docs/notes.txt": MappingProxyType(
                {
                    "type": "file",
                    "size": 6,
                    "ETag": "matching-etag",
                    "content_md5": "source-content-token",
                }
            )
        },
        post_info_by_path={
            "/docs/copy.txt": MappingProxyType(
                {
                    "type": "file",
                    "size": 6,
                    "etag": "matching-etag",
                    "content-md5": "destination-content-token",
                }
            )
        },
    )

    result = _invoke(
        "cp",
        ["memory:/docs/notes.txt", "memory:/docs/copy.txt"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: memory:/docs/copy.txt: copied 6 bytes\n"
        "cp: memory:/docs/copy.txt: metadata mismatch (content-md5); destination residue may remain\n",
    )
    assert not [event for event in source.events if event[0] == "get_file"]


def test_cp_freezes_source_metadata_before_same_source_mutation() -> None:
    source_info: dict[str, object] = {
        "type": "file",
        "size": 7,
        "checksum": "source-token",
    }
    source = _file_source(
        info_by_path={"/docs/notes.txt": source_info},
        post_info_by_path={
            "/docs/copy.txt": MappingProxyType({"type": "file", "size": 7, "checksum": "destination-token"})
        },
    )

    def copy_and_clear_source_metadata(path1: str, path2: str) -> None:
        filesystem = source.contexts[0].filesystem
        filesystem._file_contents[path2] = filesystem._file_contents[path1]
        filesystem._pending_cp_verify.add(path2)
        source.file_contents[path2] = source.file_contents[path1]
        source_info.clear()

    source.cp_file_hook = copy_and_clear_source_metadata
    result = _invoke(
        "cp",
        ["memory:/docs/notes.txt", "memory:/docs/copy.txt"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: memory:/docs/copy.txt: copied 7 bytes\n"
        "cp: memory:/docs/copy.txt: metadata mismatch (checksum); destination residue may remain\n",
    )


def test_cp_freezes_source_metadata_before_cross_source_mutation() -> None:
    source_info: dict[str, object] = {
        "type": "file",
        "size": 7,
        "checksum": "source-token",
    }
    source = _file_source(info_by_path={"/docs/notes.txt": source_info})
    destination = _file_source(
        source_path="/other.txt",
        parent="/out",
        directories={"/", "/out"},
        post_info_by_path={
            "/out/copy.txt": MappingProxyType({"type": "file", "size": 7, "checksum": "destination-token"})
        },
    )

    def upload_and_change_source_metadata(local_path: str, remote_path: str) -> None:
        filesystem = destination.contexts[0].filesystem
        content = Path(local_path).read_bytes()
        filesystem._file_contents[remote_path] = content
        filesystem._pending_cp_verify.add(remote_path)
        destination.file_contents[remote_path] = content
        source_info["checksum"] = "destination-token"

    destination.put_file_by_path = {"/out/copy.txt": upload_and_change_source_metadata}
    result = _invoke(
        "cp",
        ["source:/docs/notes.txt", "destination:/out/copy.txt"],
        sources={"source": source, "destination": destination},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: destination:/out/copy.txt: uploaded 7 bytes\n"
        "cp: destination:/out/copy.txt: metadata mismatch (checksum); destination residue may remain\n",
    )
    assert len(source.get_file_paths) == 1
    assert not [event for event in destination.events if event[0] == "get_file"]


class _StringToken(str):
    __slots__ = ()


class _BytesToken(bytes):
    pass


@pytest.mark.parametrize(
    "token",
    [_StringToken("source-token"), _BytesToken(b"source-token")],
    ids=["str-subclass", "bytes-subclass"],
)
def test_cp_ignores_str_and_bytes_subclass_tokens_after_mutation(
    token: str | bytes,
) -> None:
    source = _file_source(
        info_by_path={
            "/docs/notes.txt": {
                "type": "file",
                "size": 7,
                "checksum": token,
            }
        },
        post_info_by_path={
            "/docs/copy.txt": MappingProxyType({"type": "file", "size": 7, "checksum": "destination-token"})
        },
    )

    result = _invoke(
        "cp",
        ["memory:/docs/notes.txt", "memory:/docs/copy.txt"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("memory:/docs/copy.txt", "copied", 7),
    )
    assert source.file_contents["/docs/copy.txt"] == b"payload"
    assert not [event for event in source.events if event[0] == "get_file"]


def test_cp_reports_post_copy_destination_type_mismatch() -> None:
    source = _file_source(
        post_info_by_path={"/docs/copy.txt": MappingProxyType({"type": "directory", "size": 0})},
    )

    result = _invoke(
        "cp",
        ["memory:/docs/notes.txt", "memory:/docs/copy.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stderr == (
        "cp: memory:/docs/copy.txt: copied 7 bytes\n"
        "cp: memory:/docs/copy.txt: not a file after copy; destination residue may remain\n"
    )


def test_cp_reports_post_copy_destination_info_failure() -> None:
    source = _file_source(
        post_info_by_path={
            "/docs/copy.txt": PermissionError("verify-denied"),
        }
    )

    result = _invoke(
        "cp",
        ["memory:/docs/notes.txt", "memory:/docs/copy.txt"],
        sources={"memory": source},
    )

    assert result.exit_code == 1
    assert result.stderr == (
        "cp: memory:/docs/copy.txt: copied 7 bytes\n"
        "cp: memory:/docs/copy.txt: verification failure (PermissionError); destination residue may remain\n"
    )


def test_cp_reports_cross_source_temporary_cleanup_failure(monkeypatch) -> None:
    source = _file_source()
    destination = _file_source(
        source_path="/other.txt",
        parent="/out",
        directories={"/", "/out"},
    )
    cleaned: list[str] = []

    def fail_cleanup(path: str) -> OSError:
        cleaned.append(path)
        Path(path).unlink(missing_ok=True)
        return OSError("unlink-denied")

    monkeypatch.setattr("fss_cli._copy_file.remove_staging", fail_cleanup)

    result = _invoke(
        "cp",
        ["source:/docs/notes.txt", "destination:/out/copy.txt"],
        sources={"source": source, "destination": destination},
    )

    assert result.exit_code == 1
    assert result.stderr == (
        _cp_verified("destination:/out/copy.txt", "uploaded", 7)
        + "cp: destination:/out/copy.txt: staging failure (OSError); destination residue may remain\n"
    )
    assert len(cleaned) == 1
    assert not Path(cleaned[0]).exists()


def test_cp_cleanup_failure_does_not_mask_verification_failure(
    monkeypatch,
) -> None:
    source = _file_source(
        info_by_path={"/docs/notes.txt": MappingProxyType({"type": "file", "size": 7, "checksum": "source-token"})}
    )
    destination = _file_source(
        source_path="/other.txt",
        parent="/out",
        directories={"/", "/out"},
        post_info_by_path={
            "/out/copy.txt": MappingProxyType(
                {
                    "type": "file",
                    "size": 7,
                    "checksum": "destination-token",
                }
            )
        },
    )
    cleaned: list[str] = []

    def fail_cleanup(path: str) -> OSError:
        cleaned.append(path)
        Path(path).unlink(missing_ok=True)
        return OSError("unlink-denied")

    monkeypatch.setattr("fss_cli._copy_file.remove_staging", fail_cleanup)

    result = _invoke(
        "cp",
        ["source:/docs/notes.txt", "destination:/out/copy.txt"],
        sources={"source": source, "destination": destination},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: destination:/out/copy.txt: uploaded 7 bytes\n"
        "cp: destination:/out/copy.txt: metadata mismatch (checksum); destination residue may remain\n",
    )
    assert len(cleaned) == 1
    assert not Path(cleaned[0]).exists()


class _ControlFlow(BaseException):
    pass


class _SecondaryControlFlow(BaseException):
    pass


def test_cp_propagates_cleanup_control_flow_after_descriptor_close_error(
    monkeypatch,
) -> None:
    primary = OSError("descriptor-close")
    cleanup_control = _SecondaryControlFlow("cleanup-control")
    cleaned: list[str] = []
    source = _file_source()
    destination = _file_source(
        source_path="/other.txt",
        parent="/out",
        directories={"/", "/out"},
    )

    def fail_close(_descriptor: int) -> NoReturn:
        raise primary

    def fail_cleanup(path: str) -> NoReturn:
        cleaned.append(path)
        Path(path).unlink(missing_ok=True)
        raise cleanup_control

    monkeypatch.setattr("fss_cli._copy_file.os.close", fail_close)
    monkeypatch.setattr("fss_cli._copy_file.remove_staging", fail_cleanup)

    with pytest.raises(_SecondaryControlFlow) as caught:
        _invoke(
            "cp",
            ["source:/docs/notes.txt", "destination:/out/copy.txt"],
            sources={"source": source, "destination": destination},
        )

    assert caught.value is cleanup_control
    assert len(cleaned) == 1
    assert not Path(cleaned[0]).exists()
    assert not source.get_file_paths


def test_cp_propagates_cleanup_control_flow_after_staging_download_error(
    monkeypatch,
) -> None:
    primary = OSError("staging-download")
    cleanup_control = _SecondaryControlFlow("cleanup-control")
    cleaned: list[str] = []

    def fail_download(_local_path: str) -> NoReturn:
        raise primary

    def fail_cleanup(path: str) -> NoReturn:
        cleaned.append(path)
        Path(path).unlink(missing_ok=True)
        raise cleanup_control

    source = _file_source(get_file_by_path={"/docs/notes.txt": fail_download})
    destination = _file_source(
        source_path="/other.txt",
        parent="/out",
        directories={"/", "/out"},
    )
    monkeypatch.setattr("fss_cli._copy_file.remove_staging", fail_cleanup)

    with pytest.raises(_SecondaryControlFlow) as caught:
        _invoke(
            "cp",
            ["source:/docs/notes.txt", "destination:/out/copy.txt"],
            sources={"source": source, "destination": destination},
        )

    assert caught.value is cleanup_control
    assert cleaned == source.get_file_paths
    assert len(cleaned) == 1
    assert not Path(cleaned[0]).exists()


@pytest.mark.parametrize(
    "secondary",
    [OSError("cleanup-error"), _SecondaryControlFlow("cleanup-control")],
    ids=["ordinary-cleanup", "control-flow-cleanup"],
)
def test_cp_preserves_descriptor_close_control_flow_over_cleanup_failure(
    monkeypatch,
    secondary: BaseException,
) -> None:
    primary = _ControlFlow("descriptor-close")
    cleaned: list[str] = []
    source = _file_source()
    destination = _file_source(
        source_path="/other.txt",
        parent="/out",
        directories={"/", "/out"},
    )

    def fail_close(_descriptor: int) -> NoReturn:
        raise primary

    def fail_cleanup(path: str) -> NoReturn:
        cleaned.append(path)
        Path(path).unlink(missing_ok=True)
        raise secondary

    monkeypatch.setattr("fss_cli._copy_file.os", SimpleNamespace(close=fail_close))
    monkeypatch.setattr("fss_cli._copy_file.remove_staging", fail_cleanup)

    with pytest.raises(_ControlFlow) as caught:
        _invoke(
            "cp",
            ["source:/docs/notes.txt", "destination:/out/copy.txt"],
            sources={"source": source, "destination": destination},
        )

    assert caught.value is primary
    assert len(cleaned) == 1
    assert not Path(cleaned[0]).exists()
    assert not source.get_file_paths


def test_cp_preserves_staging_download_control_flow_over_cleanup_failure(
    monkeypatch,
) -> None:
    primary = _ControlFlow("staging-download")
    secondary = _SecondaryControlFlow("cleanup-control")
    cleaned: list[str] = []

    def fail_download(_local_path: str) -> NoReturn:
        raise primary

    def fail_cleanup(path: str) -> NoReturn:
        cleaned.append(path)
        Path(path).unlink(missing_ok=True)
        raise secondary

    source = _file_source(get_file_by_path={"/docs/notes.txt": fail_download})
    destination = _file_source(
        source_path="/other.txt",
        parent="/out",
        directories={"/", "/out"},
    )
    monkeypatch.setattr("fss_cli._copy_file.remove_staging", fail_cleanup)

    with pytest.raises(_ControlFlow) as caught:
        _invoke(
            "cp",
            ["source:/docs/notes.txt", "destination:/out/copy.txt"],
            sources={"source": source, "destination": destination},
        )

    assert caught.value is primary
    assert cleaned == source.get_file_paths
    assert len(cleaned) == 1
    assert not Path(cleaned[0]).exists()


@pytest.mark.parametrize("boundary", ["upload", "verification"])
def test_cp_preserves_post_staging_control_flow_over_cleanup_failure(
    monkeypatch,
    boundary: str,
) -> None:
    primary = _ControlFlow(boundary)
    secondary = _SecondaryControlFlow("cleanup-control")
    cleaned: list[str] = []

    def fail_cleanup(path: str) -> NoReturn:
        cleaned.append(path)
        Path(path).unlink(missing_ok=True)
        raise secondary

    source = _file_source()
    destination = _file_source(
        source_path="/other.txt",
        parent="/out",
        directories={"/", "/out"},
        put_file_by_path=({"/out/copy.txt": primary} if boundary == "upload" else None),
        post_info_by_path=({"/out/copy.txt": primary} if boundary == "verification" else None),
    )
    monkeypatch.setattr("fss_cli._copy_file.remove_staging", fail_cleanup)

    with pytest.raises(_ControlFlow) as caught:
        _invoke(
            "cp",
            ["source:/docs/notes.txt", "destination:/out/copy.txt"],
            sources={"source": source, "destination": destination},
        )

    assert caught.value is primary
    assert cleaned == source.get_file_paths
    assert len(cleaned) == 1
    assert not Path(cleaned[0]).exists()


@pytest.mark.parametrize(
    "control",
    [asyncio.CancelledError(), _ControlFlow("stop")],
)
def test_cp_removes_cross_source_temporary_on_upload_control_flow(
    control: BaseException,
) -> None:
    source = _file_source()
    destination = _file_source(
        source_path="/other.txt",
        parent="/out",
        directories={"/", "/out"},
        put_file_by_path={"/out/copy.txt": control},
    )

    with pytest.raises(type(control)) as caught:
        _invoke(
            "cp",
            ["source:/docs/notes.txt", "destination:/out/copy.txt"],
            sources={"source": source, "destination": destination},
        )

    assert type(caught.value) is type(control)
    if not isinstance(control, asyncio.CancelledError):
        assert caught.value is control
    assert len(source.get_file_paths) == 1
    assert not Path(source.get_file_paths[0]).exists()
    assert not [event for event in destination.events if event[0] == "get_file"]
