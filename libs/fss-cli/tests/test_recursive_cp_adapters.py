"""Backend-neutral recursive-copy evidence through the public ``App`` seam."""

from __future__ import annotations

import ast
import asyncio
from contextlib import asynccontextmanager
from dataclasses import fields
from pathlib import Path
from typing import TYPE_CHECKING, Literal, NoReturn

import pytest
from fsspec import AbstractFileSystem
from fsspec.asyn import AsyncFileSystem
from fsspec.implementations.asyn_wrapper import AsyncFileSystemWrapper
from typer.testing import CliRunner

from fss_cli import App

from ._support import _cp_verified

if TYPE_CHECKING:
    from collections.abc import Awaitable


def _metadata(entries: dict[str, bytes | None], path: str) -> dict[str, object]:
    if path not in entries:
        raise FileNotFoundError(path)
    payload = entries[path]
    return {
        "name": path,
        "type": "directory" if payload is None else "file",
        "size": 0 if payload is None else len(payload),
        "islink": False,
        "opaque-backend-field": {"arbitrary": path},
    }


def _listing(
    entries: dict[str, bytes | None],
    path: str,
) -> list[dict[str, object]]:
    prefix = path.rstrip("/")
    children = []
    for candidate in sorted(entries):
        if candidate == path or not candidate.startswith(f"{prefix}/"):
            continue
        if "/" in candidate[len(prefix) + 1 :]:
            continue
        children.append(_metadata(entries, candidate))
    return children


class _NativeAdapter(AsyncFileSystem):
    cachable = False

    def __init__(
        self,
        entries: dict[str, bytes | None],
        events: list[tuple[object, ...]],
        *,
        ls_form: Literal["coroutine", "awaitable"] = "coroutine",
        ls_failure: Exception | None = None,
        mutate_returned_metadata: bool = False,
    ) -> None:
        super().__init__(asynchronous=True)
        self.entries = entries
        self.events = events
        self.ls_form = ls_form
        self.ls_failure = ls_failure
        self.mutate_returned_metadata = mutate_returned_metadata
        self.returned: list[dict[str, object]] = []

    async def _info(self, path: str, **kwargs: object) -> dict[str, object]:
        del kwargs
        self.events.append(("info", path))
        return _metadata(self.entries, path)

    def _ls(
        self,
        path: str,
        detail: bool = True,  # noqa: FBT002 - fsspec hook signature.
        **kwargs: object,
    ) -> Awaitable[list[dict[str, object]]]:
        del kwargs
        self.events.append(("ls", path, detail))
        if self.ls_failure is not None:
            raise self.ls_failure
        if self.mutate_returned_metadata:
            # Rewrite every mapping handed out earlier: retained references
            # would change the frozen manifest.
            for info in self.returned:
                info["type"] = "directory"
                info["size"] = 999
        listing = _listing(self.entries, path)
        self.returned.extend(listing)

        async def resolve() -> list[dict[str, object]]:
            return listing

        if self.ls_form == "awaitable":
            return asyncio.ensure_future(resolve())
        return resolve()

    async def _mkdir(
        self,
        path: str,
        create_parents: bool = True,  # noqa: FBT002 - fsspec hook signature.
        **kwargs: object,
    ) -> None:
        del kwargs
        self.events.append(("mkdir", path, create_parents))
        self.entries[path] = None

    async def _get_file(self, remote: str, local: str, **kwargs: object) -> None:
        del kwargs
        self.events.append(("get_file", remote))
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
        self.events.append(("put_file", remote, mode))
        self.entries[remote] = Path(local).read_bytes()  # noqa: ASYNC240

    async def _forbidden(self, *args: object, **kwargs: object) -> NoReturn:
        del args, kwargs
        message = "forbidden recursive-copy operation"
        raise AssertionError(message)

    _copy = _forbidden
    _cp_file = _forbidden
    _rm = _forbidden
    _rm_file = _forbidden
    _rmdir = _forbidden

    def info(self, *args: object, **kwargs: object) -> NoReturn:
        del args, kwargs
        message = "public sync facade called"
        raise AssertionError(message)

    ls = info
    walk = info
    mkdir = info
    get_file = info
    put_file = info
    copy = info
    cp_file = info
    rm = info


class _MissingListingAdapter(_NativeAdapter):
    _ls = None  # type: ignore[assignment]


class _OversizedListingAdapter(_NativeAdapter):
    async def _ls(  # type: ignore[override]
        self,
        path: str,
        detail: bool = True,  # noqa: FBT002 - fsspec hook signature.
        **kwargs: object,
    ) -> list[dict[str, object]]:
        del kwargs
        self.events.append(("ls", path, detail))
        bad: dict[str, object] = {
            "name": f"{path}/bad",
            "type": "file",
            "size": True,
            "islink": False,
        }
        return [
            bad,
            *(
                {
                    "name": f"{path}/file-{index}",
                    "type": "file",
                    "size": 0,
                    "islink": False,
                }
                for index in range(9_999)
            ),
        ]


class _DuplicateThenFailureListingAdapter(_NativeAdapter):
    async def _ls(  # type: ignore[override]
        self,
        path: str,
        detail: bool = True,  # noqa: FBT002 - fsspec hook signature.
        **kwargs: object,
    ) -> list[dict[str, object]]:
        del kwargs
        self.events.append(("ls", path, detail))
        if path != "/dataset":
            message = "later backend failure"
            raise OSError(message)
        child = {"name": "/dataset/a", "type": "directory", "size": 0}
        return [child, dict(child)]


class _SyncAdapter(AbstractFileSystem):
    cachable = False

    def __init__(
        self,
        entries: dict[str, bytes | None],
        events: list[tuple[object, ...]],
    ) -> None:
        super().__init__(skip_instance_cache=True)
        self.entries = entries
        self.events = events

    def info(self, path: str, **kwargs: object) -> dict[str, object]:
        del kwargs
        self.events.append(("info", path))
        return _metadata(self.entries, path)

    def ls(
        self,
        path: str,
        detail: bool = True,  # noqa: FBT002 - fsspec hook signature.
        **kwargs: object,
    ) -> list[object]:
        del kwargs
        self.events.append(("ls", path, detail))
        listing = _listing(self.entries, path)
        return listing if detail else [entry["name"] for entry in listing]

    def mkdir(
        self,
        path: str,
        create_parents: bool = True,  # noqa: FBT002 - fsspec hook signature.
        **kwargs: object,
    ) -> None:
        del kwargs
        self.events.append(("mkdir", path, create_parents))
        self.entries[path] = None

    def get_file(self, remote: str, local: str, **kwargs: object) -> None:
        del kwargs
        self.events.append(("get_file", remote))
        payload = self.entries[remote]
        assert isinstance(payload, bytes)
        Path(local).write_bytes(payload)

    def put_file(
        self,
        local: str,
        remote: str,
        mode: str = "overwrite",
        **kwargs: object,
    ) -> None:
        del kwargs
        self.events.append(("put_file", remote, mode))
        self.entries[remote] = Path(local).read_bytes()

    def _forbidden(self, *args: object, **kwargs: object) -> NoReturn:
        del args, kwargs
        message = "forbidden recursive-copy operation"
        raise AssertionError(message)

    copy = _forbidden
    cp_file = _forbidden
    rm = _forbidden


def _native_source(  # noqa: PLR0913 - explicit adapter failure controls.
    entries: dict[str, bytes | None],
    events: list[tuple[object, ...]],
    *,
    ls_form: Literal["coroutine", "awaitable"] = "coroutine",
    ls_failure: Exception | None = None,
    missing_ls: bool = False,
    mutate_returned_metadata: bool = False,
):
    @asynccontextmanager
    async def source():
        adapter = (
            _MissingListingAdapter(entries, events)
            if missing_ls
            else _NativeAdapter(
                entries,
                events,
                ls_form=ls_form,
                ls_failure=ls_failure,
                mutate_returned_metadata=mutate_returned_metadata,
            )
        )
        yield adapter

    return source


def _sync_source(
    entries: dict[str, bytes | None],
    events: list[tuple[object, ...]],
):
    @asynccontextmanager
    async def source():
        yield AsyncFileSystemWrapper(
            _SyncAdapter(entries, events),
            asynchronous=True,
        )

    return source


@pytest.mark.parametrize("direction", ["native-to-sync", "sync-to-native"])
def test_backend_neutral_harness_copies_between_minimal_adapters(
    direction: str,
) -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/dataset": None,
        "/dataset/empty": None,
        "/dataset/file.bin": b"payload",
    }
    destination_entries: dict[str, bytes | None] = {"/": None, "/landing": None}
    source_events: list[tuple[object, ...]] = []
    destination_events: list[tuple[object, ...]] = []
    if direction == "native-to-sync":
        source = _native_source(source_entries, source_events)
        destination = _sync_source(destination_entries, destination_events)
    else:
        source = _sync_source(source_entries, source_events)
        destination = _native_source(destination_entries, destination_events)

    result = CliRunner().invoke(
        App({"nebula": source, "quartz": destination}).typer_app,
        ["cp", "-R", "nebula:/dataset", "quartz:/landing/copy"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("quartz:/landing/copy/file.bin", "uploaded", 7),
    )
    assert destination_entries["/landing/copy/file.bin"] == b"payload"
    assert destination_entries["/landing/copy/empty"] is None
    assert not [event for event in source_events if event[0] in {"mkdir", "put_file"}]
    assert not [event for event in destination_events if event[0] == "get_file"]


def test_backend_neutral_harness_accepts_awaitable_returning_listing() -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/dataset": None,
        "/dataset/file.bin": b"payload",
    }
    destination_entries: dict[str, bytes | None] = {"/": None, "/landing": None}

    result = CliRunner().invoke(
        App(
            {
                "arbitrary-source": _native_source(
                    source_entries,
                    [],
                    ls_form="awaitable",
                ),
                "arbitrary-target": _native_source(destination_entries, []),
            }
        ).typer_app,
        [
            "cp",
            "-r",
            "arbitrary-source:/dataset",
            "arbitrary-target:/landing/copy",
        ],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("arbitrary-target:/landing/copy/file.bin", "uploaded", 7),
    )
    assert destination_entries["/landing/copy/file.bin"] == b"payload"


def test_listing_metadata_is_frozen_before_requesting_the_next_listing() -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/dataset": None,
        "/dataset/file.bin": b"payload",
    }
    destination_entries: dict[str, bytes | None] = {"/": None, "/landing": None}

    result = CliRunner().invoke(
        App(
            {
                "source": _native_source(
                    source_entries,
                    [],
                    mutate_returned_metadata=True,
                ),
                "destination": _native_source(destination_entries, []),
            }
        ).typer_app,
        ["cp", "-R", "source:/dataset", "destination:/landing/copy"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("destination:/landing/copy/file.bin", "uploaded", 7),
    )
    assert destination_entries["/landing/copy/file.bin"] == b"payload"


def test_listing_capacity_precedes_reading_oversized_listing_entries() -> None:
    source_entries: dict[str, bytes | None] = {"/": None, "/dataset": None}
    destination_entries: dict[str, bytes | None] = {"/": None, "/landing": None}
    destination_events: list[tuple[object, ...]] = []

    @asynccontextmanager
    async def source():
        yield _OversizedListingAdapter(source_entries, [])

    result = CliRunner().invoke(
        App(
            {
                "source": source,
                "destination": _native_source(
                    destination_entries,
                    destination_events,
                ),
            }
        ).typer_app,
        ["cp", "-R", "source:/dataset", "destination:/landing/copy"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: source:/dataset: source tree exceeds 10000 entries\n",
    )
    assert not [event for event in destination_events if event[0] in {"mkdir", "put_file"}]


def test_duplicate_listing_entry_precedes_deeper_listing_failure() -> None:
    source_entries: dict[str, bytes | None] = {"/": None, "/dataset": None}
    destination_entries: dict[str, bytes | None] = {"/": None, "/landing": None}
    source_events: list[tuple[object, ...]] = []

    @asynccontextmanager
    async def source():
        yield _DuplicateThenFailureListingAdapter(source_entries, source_events)

    result = CliRunner().invoke(
        App(
            {
                "source": source,
                "destination": _native_source(destination_entries, []),
            }
        ).typer_app,
        ["cp", "-R", "source:/dataset", "destination:/landing/copy"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: source:/dataset: incompatible result\n",
    )
    # The malformed listing stops the walk before the next depth is listed.
    assert [event for event in source_events if event[0] == "ls"] == [("ls", "/dataset", True)]


@pytest.mark.parametrize(
    ("source", "diagnostic"),
    [
        ("read-failure", "permission denied"),
        ("missing-listing", "unsupported operation"),
    ],
)
def test_backend_neutral_read_phase_failures_are_stable_and_pre_mutation(
    source: str,
    diagnostic: str,
) -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/dataset": None,
        "/dataset/file.bin": b"payload",
    }
    destination_entries: dict[str, bytes | None] = {"/": None, "/landing": None}
    destination_events: list[tuple[object, ...]] = []
    source_factory = _native_source(
        source_entries,
        [],
        ls_failure=PermissionError("denied") if source == "read-failure" else None,
        missing_ls=source == "missing-listing",
    )

    result = CliRunner().invoke(
        App(
            {
                "source": source_factory,
                "destination": _native_source(
                    destination_entries,
                    destination_events,
                ),
            }
        ).typer_app,
        ["cp", "-R", "source:/dataset", "destination:/landing/copy"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        f"cp: source:/dataset: {diagnostic}\n",
    )
    assert not [event for event in destination_events if event[0] in {"mkdir", "put_file"}]
    assert "/landing/copy" not in destination_entries


def test_recursive_copy_production_has_no_backend_dispatch_or_sync_facades() -> None:
    from fss_cli import _recursive_cp

    source = Path(_recursive_cp.__file__).read_text(encoding="utf-8")
    tree = ast.parse(source)
    imported_modules = {
        node.module for node in ast.walk(tree) if isinstance(node, ast.ImportFrom) and node.module is not None
    }
    imported_modules.update(
        alias.name for node in ast.walk(tree) if isinstance(node, ast.Import) for alias in node.names
    )
    attributes = {node.attr for node in ast.walk(tree) if isinstance(node, ast.Attribute)}
    called_attributes = {
        node.func.attr for node in ast.walk(tree) if isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute)
    }

    assert not {
        "fsspec.implementations.local",
        "fsspec.implementations.memory",
        "fsspec.implementations.asyn_wrapper",
        "vosfs",
    }.intersection(imported_modules)
    assert not {"protocol", "sync_fs"}.intersection(attributes)
    assert not {
        "info",
        "ls",
        "walk",
        "mkdir",
        "get_file",
        "put_file",
        "copy",
        "cp_file",
        "rm",
    }.intersection(called_attributes)
    assert "source_filesystem is destination_filesystem" not in source
    assert "registry" not in source.casefold()

    runner = _recursive_cp._RecursiveCopy
    assert [field.name for field in fields(runner)] == [
        "command",
        "source",
        "destination",
        "source_filesystem",
        "destination_filesystem",
        "display",
        "metadata_wait",
    ]
    assert runner.__dataclass_params__.frozen is True
    assert [name for name, member in runner.__dict__.items() if callable(member) and not name.startswith("_")] == [
        "run"
    ]
