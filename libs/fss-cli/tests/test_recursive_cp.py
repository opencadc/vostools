"""Verified recursive ``cp`` tests through ``App(sources).typer_app``."""

from __future__ import annotations

import asyncio
import tempfile
from collections.abc import Mapping
from contextlib import asynccontextmanager
from pathlib import Path
from types import MethodType
from typing import TYPE_CHECKING, NoReturn

import pytest
import typer
from fsspec.asyn import AsyncFileSystem
from typer.testing import CliRunner

from fss_cli import App

from ._support import _cp_verified, _outcomes_by_file

if TYPE_CHECKING:
    from collections.abc import Callable


class _TreeFileSystem(AsyncFileSystem):
    cachable = False

    def __init__(
        self,
        entries: dict[str, bytes | None],
        calls: list[tuple[object, ...]],
        metadata: dict[str, object] | None = None,
    ) -> None:
        super().__init__(asynchronous=True)
        self.entries = entries
        self.calls = calls
        self.metadata = metadata or {}

    def _metadata(self, path: str) -> dict[str, object]:
        if path in self.metadata:
            scripted = self.metadata[path]
            if isinstance(scripted, BaseException):
                raise scripted
            assert isinstance(scripted, dict)
            return scripted
        if path not in self.entries:
            raise FileNotFoundError(path)
        payload = self.entries[path]
        return {
            "name": path,
            "type": "directory" if payload is None else "file",
            "size": 0 if payload is None else len(payload),
        }

    async def _info(self, path: str, **kwargs: object) -> dict[str, object]:
        del kwargs
        self.calls.append(("info", path))
        return self._metadata(path)

    def _children(self, path: str) -> list[dict[str, object]]:
        prefix = path.rstrip("/")
        return [
            self._metadata(candidate)
            for candidate in sorted(self.entries)
            if candidate != path and candidate.startswith(f"{prefix}/") and "/" not in candidate[len(prefix) + 1 :]
        ]

    async def _ls(
        self,
        path: str,
        detail: bool = True,  # noqa: FBT002 - fsspec hook signature.
        **kwargs: object,
    ) -> list[dict[str, object]]:
        del detail, kwargs
        self.calls.append(("ls", path))
        if path not in self.entries:
            raise FileNotFoundError(path)
        if self.entries[path] is not None:
            return [self._metadata(path)]
        return self._children(path)

    async def _mkdir(
        self,
        path: str,
        create_parents: bool = True,  # noqa: FBT002 - fsspec hook signature.
        **kwargs: object,
    ) -> None:
        del kwargs
        self.calls.append(("mkdir", path, create_parents))
        self.entries[path] = None

    async def _get_file(self, remote: str, local: str, **kwargs: object) -> None:
        del kwargs
        self.calls.append(("get_file", remote))
        payload = self.entries[remote]
        assert isinstance(payload, bytes)
        Path(local).write_bytes(payload)  # noqa: ASYNC240

    async def _put_file(
        self,
        local: str,
        remote: str,
        mode: str = "overwrite",
        **kwargs: object,
    ) -> None:
        del kwargs
        self.calls.append(("put_file", remote, mode))
        self.entries[remote] = Path(local).read_bytes()  # noqa: ASYNC240

    async def _cp_file(self, path1: str, path2: str, **kwargs: object) -> None:
        del kwargs
        self.calls.append(("cp_file", path1, path2))
        payload = self.entries[path1]
        assert isinstance(payload, bytes)
        self.entries[path2] = payload


def _source(
    entries: dict[str, bytes | None],
    calls: list[tuple[object, ...]],
    *,
    metadata: dict[str, object] | None = None,
    configure: Callable[[_TreeFileSystem], None] | None = None,
):
    @asynccontextmanager
    async def source():
        filesystem = _TreeFileSystem(entries, calls, metadata)
        if configure is not None:
            configure(filesystem)
        yield filesystem

    return source


def _invoke(
    arguments: list[str],
    sources: dict[str, object],
):
    return CliRunner().invoke(App(sources).typer_app, ["cp", *arguments])  # type: ignore[arg-type]


def test_recursive_copy_does_not_omit_fields_hidden_by_items() -> None:
    class HiddenItems(dict):
        def items(self):
            return []

    entries = {"/": None, "/docs": None, "/docs/important": b"payload", "/out": None}

    def configure(filesystem):
        original = filesystem._ls

        async def ls(_self, path, *args, **kwargs):
            if path != "/docs":
                return await original(path, *args, **kwargs)
            return [HiddenItems({"name": "/docs/important", "type": "file", "size": 7})]

        filesystem._ls = MethodType(ls, filesystem)

    source = _source(entries, [], configure=configure)
    result = _invoke(["-R", "memory:/docs", "memory:/out/copy"], {"memory": source})
    assert result.exit_code == 0
    copied = CliRunner().invoke(App({"memory": source}).typer_app, ["size", "memory:/out/copy/important"])
    assert (copied.exit_code, copied.stdout) == (0, "7\tmemory:/out/copy/important\n")


def test_repeating_recursive_copy_skips_identical_contents() -> None:
    entries = {"/": None, "/docs": None, "/docs/f": b"payload", "/out": None}
    calls = []
    source = _source(entries, calls)
    first = _invoke(["-R", "memory:/docs", "memory:/out"], {"memory": source})
    assert first.exit_code == 0
    calls.clear()
    repeated = _invoke(["-R", "memory:/docs", "memory:/out"], {"memory": source})
    assert repeated.exit_code == 0
    assert not any(call[0] == "put_file" for call in calls)


def test_recursive_copy_skips_matching_content_checksums_without_downloads() -> None:
    entries = {
        "/": None,
        "/docs": None,
        "/docs/f": b"abc",
        "/out": None,
        "/out/docs": None,
        "/out/docs/f": b"abc",
    }
    metadata = {
        path: {
            "name": path,
            "type": "file",
            "size": 3,
            "md5": "900150983cd24fb0d6963f7d28e17f72",
        }
        for path in ("/docs/f", "/out/docs/f")
    }
    calls = []
    source = _source(entries, calls, metadata=metadata)
    result = _invoke(["-R", "memory:/docs", "memory:/out"], {"memory": source})
    assert result.exit_code == 0
    assert not any(call[0] in {"get_file", "put_file"} for call in calls)


def test_recursive_copy_replaces_same_size_different_contents() -> None:
    entries = {
        "/": None,
        "/docs": None,
        "/docs/f": b"new",
        "/out": None,
        "/out/docs": None,
        "/out/docs/f": b"old",
    }
    calls = []
    source = _source(entries, calls)
    result = _invoke(["-R", "memory:/docs", "memory:/out"], {"memory": source})
    assert result.exit_code == 0
    assert [call for call in calls if call[0] == "put_file"] == [("put_file", "/out/docs/f", "overwrite")]
    assert [call for call in calls if call[0] == "get_file" and call[1] == "/docs/f"] == [("get_file", "/docs/f")]


def test_recursive_copy_does_not_trust_equal_etags_as_content_checksums() -> None:
    entries = {
        "/": None,
        "/docs": None,
        "/docs/f": b"new",
        "/out": None,
        "/out/docs": None,
        "/out/docs/f": b"old",
    }
    metadata = {
        path: {"name": path, "type": "file", "size": 3, "ETag": "opaque"} for path in ("/docs/f", "/out/docs/f")
    }
    calls = []
    result = _invoke(
        ["-R", "memory:/docs", "memory:/out"],
        {"memory": _source(entries, calls, metadata=metadata)},
    )
    assert result.exit_code == 0
    assert any(call[0] == "put_file" for call in calls)


@pytest.mark.parametrize(
    "tokens",
    [
        (
            {"md5": "900150983cd24fb0d6963f7d28e17f72"},
            {"md5": "900150983CD24FB0D6963F7D28E17F72"},
        ),
        ({"ETag": "source-version"}, {"ETag": "destination-version"}),
    ],
)
def test_verified_skips_accept_compatible_content_with_distinct_tokens(tokens) -> None:
    entries = {
        "/": None,
        "/docs": None,
        "/docs/f": b"abc",
        "/out": None,
        "/out/docs": None,
        "/out/docs/f": b"abc",
    }
    metadata = {
        path: {"name": path, "type": "file", "size": 3, **token}
        for path, token in zip(("/docs/f", "/out/docs/f"), tokens, strict=True)
    }
    calls = []
    result = _invoke(
        ["-R", "memory:/docs", "memory:/out"],
        {"memory": _source(entries, calls, metadata=metadata)},
    )
    assert (result.exit_code, result.stderr) == (0, "")
    assert not any(call[0] == "put_file" for call in calls)


def test_comparison_cleanup_cannot_replace_cancellation(monkeypatch) -> None:
    original = asyncio.CancelledError()
    directory_type = tempfile.TemporaryDirectory

    class FailingCleanup(directory_type):
        def cleanup(self):
            super().cleanup()
            message = "cleanup failed"
            raise OSError(message)

    def configure(filesystem):
        download = filesystem._get_file

        async def get_file(_self, remote, local, **kwargs):
            if remote == "/out/docs/f":
                raise original
            await download(remote, local, **kwargs)

        filesystem._get_file = MethodType(get_file, filesystem)

    monkeypatch.setattr(tempfile, "TemporaryDirectory", FailingCleanup)
    entries = {
        "/": None,
        "/docs": None,
        "/docs/f": b"abc",
        "/out": None,
        "/out/docs": None,
        "/out/docs/f": b"abc",
    }
    with pytest.raises(asyncio.CancelledError):
        _invoke(
            ["-R", "memory:/docs", "memory:/out"],
            {"memory": _source(entries, [], configure=configure)},
        )


def test_recursive_copy_rejects_oversized_listing_without_reading_entries() -> None:
    fetched = []

    class Info(Mapping):
        def __init__(self, index):
            self.index = index

        def __len__(self):
            fetched.append(self.index)
            return 3

        def __iter__(self):
            fetched.append(self.index)
            return iter(("name", "type", "size"))

        def __getitem__(self, name):
            fetched.append(self.index)
            return {"name": f"/docs/f{self.index}", "type": "file", "size": 1}[name]

    def configure(filesystem):
        async def ls(_self, path, *_args, **_kwargs):
            del path
            return [Info(index) for index in range(20_000)]

        filesystem._ls = MethodType(ls, filesystem)

    entries = {"/": None, "/docs": None, "/out": None}
    result = _invoke(
        ["-R", "memory:/docs", "memory:/out"],
        {"memory": _source(entries, [], configure=configure)},
    )
    assert (result.exit_code, result.stderr, fetched) == (
        1,
        "cp: memory:/docs: source tree exceeds 10000 entries\n",
        [],
    )


def test_recursive_cp_reports_source_factory_failure() -> None:
    def fail_factory() -> NoReturn:
        message = "factory"
        raise ValueError(message)

    result = _invoke(
        ["-R", "broken:/docs", "broken:/out"],
        {"broken": fail_factory},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: broken: source factory failure (ValueError): factory\n",
    )


def test_recursive_cp_preserves_backend_error_when_diagnostic_write_fails(
    monkeypatch,
) -> None:
    backend_error = PermissionError("denied")
    renderer_error = RuntimeError("stderr failed")
    exit_calls: list[tuple[object, ...]] = []
    filesystem = _TreeFileSystem(
        {"/": None, "/docs": None, "/out": None},
        [],
        {"/docs": backend_error},
    )

    class RecordingSource:
        async def __aenter__(self) -> _TreeFileSystem:
            return filesystem

        async def __aexit__(self, *exc_info: object) -> None:
            exit_calls.append(exc_info)

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
        ["-R", "memory:/docs", "memory:/out"],
        {"memory": RecordingSource},
    )

    assert result.exit_code == 1
    assert result.exception is renderer_error
    assert result.stdout == ""
    assert result.stderr == ""
    exception_type, exception, traceback = exit_calls[0]
    assert exception_type is PermissionError
    assert exception is backend_error
    assert traceback is not None


def test_recursive_cp_reports_source_exit_failure_after_verified_copy() -> None:
    entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/notes.txt": b"notes",
        "/out": None,
    }

    @asynccontextmanager
    async def source():
        yield _TreeFileSystem(entries, [])
        message = "cleanup"
        raise OSError(message)

    result = _invoke(
        ["-R", "memory:/docs", "memory:/out"],
        {"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        _cp_verified("memory:/out/docs/notes.txt", "copied", 5)
        + "cp: memory: source exit failure (OSError): cleanup\n",
    )
    assert entries["/out/docs/notes.txt"] == b"notes"


def test_recursive_cp_copies_nested_and_empty_directories_through_host_staging() -> None:
    entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/empty": None,
        "/docs/nested": None,
        "/docs/nested/notes.txt": b"notes",
        "/target": None,
    }
    calls: list[tuple[object, ...]] = []

    result = _invoke(
        ["-R", "memory:/docs", "memory:/target"],
        {"memory": _source(entries, calls)},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("memory:/target/docs/nested/notes.txt", "copied", 5),
    )
    assert entries["/target/docs/empty"] is None
    assert entries["/target/docs/nested/notes.txt"] == b"notes"
    # One breadth-first listing per source directory, for the manifest and again
    # for source revalidation. Destination proof lists the written tree too.
    assert sorted(call for call in calls if call[0] == "ls" and str(call[1]).startswith("/docs")) == [
        ("ls", "/docs"),
        ("ls", "/docs"),
        ("ls", "/docs/empty"),
        ("ls", "/docs/empty"),
        ("ls", "/docs/nested"),
        ("ls", "/docs/nested"),
    ]
    assert ("ls", "/target/docs") in calls
    mutations = [call for call in calls if call[0] in {"mkdir", "cp_file"}]
    # The root is created first; siblings at one depth are issued together.
    assert mutations[0] == ("mkdir", "/target/docs", False)
    assert sorted(mutations[1:3]) == [
        ("mkdir", "/target/docs/empty", False),
        ("mkdir", "/target/docs/nested", False),
    ]
    assert mutations[3:] == [
        ("cp_file", "/docs/nested/notes.txt", "/target/docs/nested/notes.txt"),
    ]


def test_recursive_cp_supports_distinct_configured_sources() -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/notes.txt": b"notes",
    }
    destination_entries: dict[str, bytes | None] = {"/": None, "/out": None}
    calls: list[tuple[object, ...]] = []

    result = _invoke(
        ["-r", "source:/docs", "destination:/out/copy"],
        {
            "source": _source(source_entries, calls),
            "destination": _source(destination_entries, calls),
        },
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("destination:/out/copy/notes.txt", "uploaded", 5),
    )
    assert source_entries["/docs/notes.txt"] == b"notes"
    assert destination_entries["/out/copy/notes.txt"] == b"notes"


def test_recursive_cp_rejects_third_operand_before_source_entry() -> None:
    calls: list[tuple[object, ...]] = []

    result = _invoke(
        ["-R", "memory:/one", "memory:/two", "memory:/three"],
        {"memory": _source({"/": None}, calls)},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        2,
        "",
        "cp: extra operand\n",
    )
    assert calls == []


def test_recursive_cp_rejects_destination_inside_source_before_mutation() -> None:
    entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/nested": None,
    }
    calls: list[tuple[object, ...]] = []

    result = _invoke(
        ["-R", "memory:/docs", "memory:/docs/nested"],
        {"memory": _source(entries, calls)},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: memory:/docs/nested: destination is inside source\n",
    )
    assert not [call for call in calls if call[0] in {"mkdir", "put_file"}]


@pytest.mark.parametrize(
    ("parent_entry", "parent_metadata", "diagnostic"),
    [
        (None, None, "not found"),
        (b"parent", None, "not a directory"),
        (
            None,
            {"name": "/parent", "type": "directory", "islink": True},
            "not a directory",
        ),
    ],
)
def test_recursive_cp_rejects_missing_file_or_link_resolved_parent(
    parent_entry: bytes | None,
    parent_metadata: dict[str, object] | None,
    diagnostic: str,
) -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/file": b"x",
    }
    destination_entries: dict[str, bytes | None] = {"/": None}
    metadata = None
    if parent_entry is not None or parent_metadata is not None:
        destination_entries["/parent"] = parent_entry
        metadata = {"/parent": parent_metadata} if parent_metadata is not None else None
    calls: list[tuple[object, ...]] = []

    result = _invoke(
        ["-R", "source:/docs", "destination:/parent/copy"],
        {
            "source": _source(source_entries, calls),
            "destination": _source(
                destination_entries,
                calls,
                metadata=metadata,
            ),
        },
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        f"cp: destination:/parent/copy: {diagnostic}\n",
    )
    assert not [call for call in calls if call[0] in {"ls", "mkdir", "put_file"}]


@pytest.mark.parametrize(
    ("root_entry", "root_metadata", "diagnostic"),
    [
        (b"existing", None, "destination type conflict"),
        (
            None,
            {"name": "/out/copy", "type": "directory", "islink": True},
            "unsupported entry type",
        ),
    ],
)
def test_recursive_cp_rejects_existing_resolved_root_file_or_link(
    root_entry: bytes | None,
    root_metadata: dict[str, object] | None,
    diagnostic: str,
) -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/file": b"x",
    }
    destination_entries: dict[str, bytes | None] = {
        "/": None,
        "/out": None,
        "/out/copy": root_entry,
    }
    metadata = {"/out/copy": root_metadata} if root_metadata is not None else None
    calls: list[tuple[object, ...]] = []

    result = _invoke(
        ["-R", "source:/docs", "destination:/out/copy"],
        {
            "source": _source(source_entries, calls),
            "destination": _source(
                destination_entries,
                calls,
                metadata=metadata,
            ),
        },
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        f"cp: destination:/out/copy: {diagnostic}\n",
    )
    assert not [call for call in calls if call[0] in {"ls", "mkdir", "put_file"}]


def test_recursive_cp_merges_existing_tree_and_replaces_files() -> None:
    entries: dict[str, bytes | None] = {
        "/": None,
        "/docs/": None,
        "/docs/empty": None,
        "/docs/notes.txt": b"new",
        "/target": None,
        "/target//": None,
        "/target/docs": None,
        "/target/docs/extra.txt": b"keep",
        "/target/docs/notes.txt": b"old",
    }
    calls: list[tuple[object, ...]] = []

    result = _invoke(
        ["-r", "memory:/docs/", "memory:/target//"],
        {"memory": _source(entries, calls)},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("memory:/target/docs/notes.txt", "uploaded", 3),
    )
    assert entries["/target/docs/notes.txt"] == b"new"
    assert entries["/target/docs/extra.txt"] == b"keep"
    assert entries["/target/docs/empty"] is None
    assert ("info", "/docs/") in calls
    assert ("info", "/target//") in calls
    assert ("ls", "/docs/") in calls


@pytest.mark.parametrize(
    ("arguments", "diagnostic"),
    [
        (["-R", "memory:/", "memory:/out"], "cp: memory:/: source root unsupported\n"),
        (
            ["-R", "memory:/docs/../secret", "memory:/out"],
            "cp: memory:/docs/../secret: dot segment unsupported\n",
        ),
        (
            ["-R", "memory:/docs", "memory:/out/./copy"],
            "cp: memory:/out/./copy: dot segment unsupported\n",
        ),
    ],
)
def test_recursive_cp_path_and_option_preflight_is_source_free(
    arguments: list[str],
    diagnostic: str,
) -> None:
    calls: list[tuple[object, ...]] = []

    result = _invoke(arguments, {"memory": _source({"/": None}, calls)})

    assert (result.exit_code, result.stdout, result.stderr) == (2, "", diagnostic)
    assert calls == []


@pytest.mark.parametrize(
    ("metadata", "diagnostic"),
    [
        (
            {"name": "/docs/link", "type": "file", "size": 1, "islink": True},
            "unsupported entry type",
        ),
        (
            {"name": "/docs/link", "type": "other", "size": 1},
            "unsupported entry type",
        ),
        (
            {"name": "/wrong", "type": "file", "size": 1},
            "incompatible result",
        ),
    ],
)
def test_recursive_cp_rejects_manifest_entry_before_mutation(
    metadata: dict[str, object],
    diagnostic: str,
) -> None:
    entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/link": b"x",
        "/out": None,
    }
    calls: list[tuple[object, ...]] = []

    result = _invoke(
        ["-R", "memory:/docs", "memory:/out/copy"],
        {"memory": _source(entries, calls, metadata={"/docs/link": metadata})},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        f"cp: memory:/docs: {diagnostic}\n",
    )
    assert not [call for call in calls if call[0] in {"mkdir", "put_file"}]


def test_recursive_cp_rejects_destination_type_conflict_before_mutation() -> None:
    entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/nested": None,
        "/out": None,
        "/out/copy": None,
        "/out/copy/docs": None,
        "/out/copy/docs/nested": b"file",
    }
    calls: list[tuple[object, ...]] = []

    result = _invoke(
        ["-R", "memory:/docs", "memory:/out/copy"],
        {"memory": _source(entries, calls)},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: memory:/out/copy: destination type conflict\n",
    )
    assert not [call for call in calls if call[0] in {"mkdir", "put_file"}]


def test_recursive_cp_reports_source_change_after_transfer() -> None:
    entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/notes.txt": b"notes",
        "/out": None,
    }
    calls: list[tuple[object, ...]] = []

    def configure(filesystem: _TreeFileSystem) -> None:
        original = filesystem._cp_file

        async def cp_file(
            self: _TreeFileSystem,
            path1: str,
            path2: str,
            **kwargs: object,
        ) -> None:
            await original(path1, path2, **kwargs)
            self.entries["/docs/notes.txt"] = b"changed"

        filesystem._cp_file = MethodType(cp_file, filesystem)  # type: ignore[method-assign]

    result = _invoke(
        ["-R", "memory:/docs", "memory:/out/copy"],
        {"memory": _source(entries, calls, configure=configure)},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        _cp_verified("memory:/out/copy/notes.txt", "copied", 5)
        + "cp: memory:/docs: source changed; destination residue may remain\n",
    )
    assert entries["/out/copy/notes.txt"] == b"notes"


def test_recursive_cp_detects_source_mutation_before_transfer() -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/file": b"x",
    }
    destination_entries: dict[str, bytes | None] = {"/": None, "/out": None}
    source_calls: list[tuple[object, ...]] = []
    destination_calls: list[tuple[object, ...]] = []

    def configure(filesystem: _TreeFileSystem) -> None:
        original = filesystem._mkdir

        async def mkdir(
            self: _TreeFileSystem,
            path: str,
            create_parents: bool = True,  # noqa: FBT002 - fsspec hook signature.
            **kwargs: object,
        ) -> None:
            del self
            await original(path, create_parents, **kwargs)
            if path == "/out/copy":
                source_entries["/docs/file"] = b"changed"

        filesystem._mkdir = MethodType(mkdir, filesystem)  # type: ignore[method-assign]

    result = _invoke(
        ["-R", "source:/docs", "destination:/out/copy"],
        {
            "source": _source(source_entries, source_calls),
            "destination": _source(
                destination_entries,
                destination_calls,
                configure=configure,
            ),
        },
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: source:/docs: source changed; destination residue may remain\n",
    )
    assert ("get_file", "/docs/file") in source_calls
    assert not [call for call in destination_calls if call[0] == "put_file"]
    assert destination_entries["/out/copy"] is None


def test_recursive_cp_reports_partial_destination_residue_after_upload_failure() -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/first.txt": b"first",
        "/docs/second.txt": b"second",
    }
    destination_entries: dict[str, bytes | None] = {"/": None, "/out": None}
    calls: list[tuple[object, ...]] = []

    def configure(filesystem: _TreeFileSystem) -> None:
        original = filesystem._put_file

        async def put_file(
            self: _TreeFileSystem,
            local: str,
            remote: str,
            mode: str = "overwrite",
            **kwargs: object,
        ) -> None:
            del self
            if remote.endswith("second.txt"):
                message = "upload failed"
                raise OSError(message)
            await original(local, remote, mode, **kwargs)

        filesystem._put_file = MethodType(put_file, filesystem)  # type: ignore[method-assign]

    result = _invoke(
        ["-R", "source:/docs", "destination:/out/copy"],
        {
            "source": _source(source_entries, calls),
            "destination": _source(destination_entries, calls, configure=configure),
        },
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        _cp_verified("destination:/out/copy/first.txt", "uploaded", 5)
        + "cp: destination:/out/copy: mutation failure; destination residue may remain\n",
    )
    assert destination_entries["/out/copy/first.txt"] == b"first"
    assert "/out/copy/second.txt" not in destination_entries


def test_recursive_cp_drains_cancelled_download_before_source_exit() -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/notes.txt": b"notes",
    }
    destination_entries: dict[str, bytes | None] = {"/": None, "/out": None}
    calls: list[tuple[object, ...]] = []
    drained = False

    def configure(filesystem: _TreeFileSystem) -> None:
        owner = asyncio.current_task()
        assert owner is not None

        async def get_file(
            self: _TreeFileSystem,
            remote: str,
            local: str,
            **kwargs: object,
        ) -> None:
            nonlocal drained
            del self, remote, kwargs
            owner.cancel()
            await asyncio.sleep(0)
            Path(local).write_bytes(b"notes")  # noqa: ASYNC240
            drained = True

        filesystem._get_file = MethodType(get_file, filesystem)  # type: ignore[method-assign]

    with pytest.raises(asyncio.CancelledError):
        _invoke(
            ["-R", "source:/docs", "destination:/out/copy"],
            {
                "source": _source(source_entries, calls, configure=configure),
                "destination": _source(destination_entries, calls),
            },
        )

    assert drained
    assert "/out/copy/notes.txt" not in destination_entries


def test_recursive_cp_propagates_non_cancel_control_flow() -> None:
    class Stop(BaseException):
        pass

    control = Stop()
    entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/notes.txt": b"notes",
        "/out": None,
    }
    calls: list[tuple[object, ...]] = []

    def configure(filesystem: _TreeFileSystem) -> None:
        async def fail(
            self: _TreeFileSystem,
            path1: str,
            path2: str,
            **kwargs: object,
        ) -> NoReturn:
            del self, path1, path2, kwargs
            raise control

        filesystem._cp_file = MethodType(fail, filesystem)  # type: ignore[method-assign]

    with pytest.raises(Stop) as caught:
        _invoke(
            ["-R", "memory:/docs", "memory:/out/copy"],
            {"memory": _source(entries, calls, configure=configure)},
        )

    assert caught.value is control


@pytest.mark.parametrize(
    ("entry_count", "expected"),
    [
        (10_000, (0, "")),
        (10_001, (1, "cp: memory:/source: source tree exceeds 10000 entries\n")),
    ],
)
def test_recursive_cp_enforces_exact_manifest_entry_limit(
    entry_count: int,
    expected: tuple[int, str],
) -> None:
    entries: dict[str, bytes | None] = {"/": None, "/source": None, "/out": None}
    for index in range(entry_count - 1):
        entries[f"/source/d{index:05d}"] = None
    calls: list[tuple[object, ...]] = []

    children = [
        {"name": path, "type": "directory", "size": 0} for path in sorted(entries) if path.startswith("/source/")
    ]

    def configure(filesystem: _TreeFileSystem) -> None:
        async def ls(
            self: _TreeFileSystem,
            path: str,
            detail: bool = True,  # noqa: FBT002 - fsspec hook signature.
            **kwargs: object,
        ) -> list[dict[str, object]]:
            del detail, kwargs
            self.calls.append(("ls", path))
            # Avoid rescanning every entry for each of the leaf directories.
            return [dict(child) for child in children] if path == "/source" else []

        filesystem._ls = MethodType(ls, filesystem)  # type: ignore[method-assign]

    result = _invoke(
        ["-R", "memory:/source", "memory:/out/copy"],
        {"memory": _source(entries, calls, configure=configure)},
    )

    assert (result.exit_code, result.stderr) == expected
    assert result.stdout == ""
    if entry_count == 10_001:
        assert not [call for call in calls if call[0] in {"mkdir", "put_file"}]
        # The oversized root listing is refused before any child is listed.
        assert [call for call in calls if call[0] == "ls"] == [("ls", "/source")]


def test_recursive_cp_reports_an_unchanged_shared_token_as_not_yet_verified() -> None:
    entries: dict[str, bytes | None] = {
        "/": None,
        "/source": None,
        "/source/notes.txt": b"notes",
        "/out": None,
        "/out/copy": None,
        "/out/copy/source": None,
        "/out/copy/source/notes.txt": b"old!!",
    }
    metadata = {
        "/source/notes.txt": {
            "name": "/source/notes.txt",
            "type": "file",
            "size": 5,
            "checksum": "source-token",
        },
        "/out/copy/source/notes.txt": {
            "name": "/out/copy/source/notes.txt",
            "type": "file",
            "size": 5,
            "checksum": "destination-token",
        },
    }

    result = _invoke(
        ["-R", "--metadata-wait", "0", "memory:/source", "memory:/out/copy"],
        {"memory": _source(entries, [], metadata=metadata)},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: memory:/out/copy/source/notes.txt: uploaded 5 bytes\n"
        "cp: memory:/out/copy/source/notes.txt: not yet verified (checksum unchanged); "
        "destination residue may remain\n",
    )


def test_recursive_cp_orders_transfer_and_staging_cleanup_failures(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/source": None,
        "/source/notes.txt": b"notes",
    }
    destination_entries: dict[str, bytes | None] = {"/": None, "/out": None}
    temporary_paths: list[str] = []

    def configure(filesystem: _TreeFileSystem) -> None:
        async def get_file(
            self: _TreeFileSystem,
            remote: str,
            local: str,
            **kwargs: object,
        ) -> None:
            del self, remote, kwargs
            temporary_paths.append(local)
            Path(local).write_bytes(b"partial")  # noqa: ASYNC240
            message = "download failed"
            raise OSError(message)

        filesystem._get_file = MethodType(get_file, filesystem)  # type: ignore[method-assign]

    real_unlink = Path.unlink

    def fail_temporary_unlink(path: Path, missing_ok: bool = False) -> None:  # noqa: FBT002
        if path.name.startswith("fss-cli-cp-recursive-"):
            message = "cleanup failed"
            raise OSError(message)
        real_unlink(path, missing_ok=missing_ok)

    monkeypatch.setattr("fss_cli._recursive_cp.Path.unlink", fail_temporary_unlink)
    result = _invoke(
        ["-R", "source:/source", "destination:/out/copy"],
        {
            "source": _source(source_entries, [], configure=configure),
            "destination": _source(destination_entries, []),
        },
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: source:/source: transfer failure; destination residue may remain\n"
        "cp: source:/source: staging cleanup failure (OSError); "
        "host staging residue may remain; destination residue may remain\n",
    )
    assert len(temporary_paths) == 1
    real_unlink(Path(temporary_paths[0]), missing_ok=True)


def test_recursive_cp_skips_entry_preflight_when_destination_root_is_missing() -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/nested": None,
        "/docs/nested/notes.txt": b"notes",
        "/docs/top.txt": b"top",
    }
    destination_entries: dict[str, bytes | None] = {"/": None, "/out": None}
    destination_calls: list[tuple[object, ...]] = []

    result = _invoke(
        ["-R", "source:/docs", "destination:/out/copy"],
        {
            "source": _source(source_entries, []),
            "destination": _source(destination_entries, destination_calls),
        },
    )

    assert (result.exit_code, result.stdout) == (0, "")
    assert _outcomes_by_file(result.stderr) == {
        "destination:/out/copy/nested/notes.txt": ["uploaded 5 bytes", "verified 5 bytes"],
        "destination:/out/copy/top.txt": ["uploaded 3 bytes", "verified 3 bytes"],
    }
    first_mutation = next(index for index, call in enumerate(destination_calls) if call[0] == "mkdir")
    # Only target resolution reads metadata before mutation: nothing under the
    # missing root can exist, so no per-entry preflight is issued.
    assert destination_calls[:first_mutation] == [
        ("info", "/out/copy"),
        ("info", "/out"),
    ]
    # Each file is read by its post-upload check and again by the final
    # destination proof; each directory only by the final proof.
    assert sorted(
        call[1] for call in destination_calls if call[0] == "info" and str(call[1]).startswith("/out/copy")
    ) == [
        "/out/copy",
        "/out/copy/nested/notes.txt",
        "/out/copy/top.txt",
    ]
    assert ("ls", "/out/copy") in destination_calls
    assert ("ls", "/out/copy/nested") in destination_calls


def test_recursive_cp_overlaps_transfers_within_the_concurrency_bound() -> None:
    source_entries: dict[str, bytes | None] = {"/": None, "/docs": None}
    for index in range(20):
        source_entries[f"/docs/f{index:02d}"] = f"payload-{index:02d}".encode()
    destination_entries: dict[str, bytes | None] = {"/": None, "/out": None}
    in_flight = 0
    peak = 0

    def configure(filesystem: _TreeFileSystem) -> None:
        original = filesystem._get_file

        async def get_file(
            self: _TreeFileSystem,
            remote: str,
            local: str,
            **kwargs: object,
        ) -> None:
            nonlocal in_flight, peak
            del self
            in_flight += 1
            peak = max(peak, in_flight)
            for _ in range(5):
                await asyncio.sleep(0)
            await original(remote, local, **kwargs)
            in_flight -= 1

        filesystem._get_file = MethodType(get_file, filesystem)  # type: ignore[method-assign]

    result = _invoke(
        ["-R", "source:/docs", "destination:/out/copy"],
        {
            "source": _source(source_entries, [], configure=configure),
            "destination": _source(destination_entries, []),
        },
    )

    assert (result.exit_code, result.stdout) == (0, "")
    assert _outcomes_by_file(result.stderr) == {
        f"destination:/out/copy/f{index:02d}": ["uploaded 10 bytes", "verified 10 bytes"] for index in range(20)
    }
    assert peak == 16
    for index in range(20):
        assert destination_entries[f"/out/copy/f{index:02d}"] == (f"payload-{index:02d}".encode())


def test_recursive_cp_creates_missing_directories_parents_first_by_depth() -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/a": None,
        "/docs/a/x": None,
        "/docs/a/y": None,
        "/docs/b": None,
        "/docs/b/z": None,
        "/docs/c": None,
    }
    destination_entries: dict[str, bytes | None] = {"/": None, "/out": None}
    calls: list[tuple[object, ...]] = []
    parent_missing: list[str] = []
    in_flight: set[str] = set()
    batches: list[frozenset[str]] = []

    def configure(filesystem: _TreeFileSystem) -> None:
        async def mkdir(
            self: _TreeFileSystem,
            path: str,
            create_parents: bool = True,  # noqa: FBT002 - fsspec hook signature.
            **kwargs: object,
        ) -> None:
            del kwargs
            self.calls.append(("mkdir", path, create_parents))
            if path.rsplit("/", 1)[0] not in self.entries:
                parent_missing.append(path)
            in_flight.add(path)
            batches.append(frozenset(in_flight))
            for _ in range(3):
                await asyncio.sleep(0)
            in_flight.discard(path)
            self.entries[path] = None

        filesystem._mkdir = MethodType(mkdir, filesystem)  # type: ignore[method-assign]

    result = _invoke(
        ["-R", "source:/docs", "destination:/out/copy"],
        {
            "source": _source(source_entries, []),
            "destination": _source(destination_entries, calls, configure=configure),
        },
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert parent_missing == []
    assert all(call[2] is False for call in calls if call[0] == "mkdir")
    assert next(call[1] for call in calls if call[0] == "mkdir") == "/out/copy"
    # Siblings of one depth are all in flight together; depths never overlap.
    assert frozenset({"/out/copy/a", "/out/copy/b", "/out/copy/c"}) in batches
    assert frozenset({"/out/copy/a/x", "/out/copy/a/y", "/out/copy/b/z"}) in batches
    assert all(len({path.count("/") for path in batch}) == 1 for batch in batches)
