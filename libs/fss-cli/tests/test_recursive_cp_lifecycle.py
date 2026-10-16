"""Recursive ``cp`` lifecycle and source-manager tests."""

from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
from pathlib import Path
from types import MethodType
from typing import TYPE_CHECKING, NoReturn

import pytest

from ._support import _cp_verified
from .test_recursive_cp import _invoke, _source, _TreeFileSystem

if TYPE_CHECKING:
    from collections.abc import AsyncIterator, Callable


@pytest.mark.parametrize("name", [".", ".."])
def test_recursive_cp_rejects_walk_dot_segments_before_mutation(name: str) -> None:
    entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        f"/docs/{name}": None,
        "/out": None,
    }
    calls: list[tuple[object, ...]] = []

    result = _invoke(
        ["-R", "memory:/docs", "memory:/out/copy"],
        {"memory": _source(entries, calls)},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: memory:/docs: incompatible result\n",
    )
    assert not [call for call in calls if call[0] in {"mkdir", "put_file"}]


@pytest.mark.parametrize(
    "tokens",
    [
        {"checksum": 7},
        {"ETag": "one", "etag": "two"},
        {"content-md5": "one", "content_md5": "two"},
    ],
)
def test_recursive_cp_rejects_invalid_recognized_tokens(
    tokens: dict[str, object],
) -> None:
    entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/file": b"x",
        "/out": None,
    }
    metadata = {
        "/docs/file": {
            "name": "/docs/file",
            "type": "file",
            "size": 1,
            **tokens,
        }
    }

    result = _invoke(
        ["-R", "memory:/docs", "memory:/out/copy"],
        {"memory": _source(entries, [], metadata=metadata)},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: memory:/docs: incompatible result\n",
    )


def test_recursive_cp_drains_late_listing_before_source_exit() -> None:
    entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/out": None,
    }
    events: list[str] = []

    @asynccontextmanager
    async def source():
        filesystem = _TreeFileSystem(entries, [])
        owner = asyncio.current_task()
        assert owner is not None

        async def ls(
            self: _TreeFileSystem,
            path: str,
            *args: object,
            **kwargs: object,
        ) -> list[object]:
            del self, path, args, kwargs
            owner.cancel()
            await asyncio.sleep(0)
            events.append("listing drained")
            return []

        filesystem._ls = MethodType(ls, filesystem)  # type: ignore[method-assign]
        try:
            yield filesystem
        finally:
            events.append("source exit")

    with pytest.raises(asyncio.CancelledError):
        _invoke(
            ["-R", "memory:/docs", "memory:/out/copy"],
            {"memory": source},
        )

    assert events == ["listing drained", "source exit"]


def test_recursive_cp_cleans_staging_when_primary_rendering_escapes(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class Stop(BaseException):
        pass

    control = Stop()
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/file": b"x",
    }
    destination_entries: dict[str, bytes | None] = {"/": None, "/out": None}
    temporary_paths: list[str] = []

    def configure(filesystem: _TreeFileSystem) -> None:
        async def get_file(
            self: _TreeFileSystem,
            remote: str,
            local: str,
            **kwargs: object,
        ) -> NoReturn:
            del self, remote, kwargs
            temporary_paths.append(local)
            Path(local).write_bytes(b"partial")  # noqa: ASYNC240
            message = "download"
            raise OSError(message)

        filesystem._get_file = MethodType(get_file, filesystem)  # type: ignore[method-assign]

    def fail_render(*args: object, **kwargs: object) -> NoReturn:
        del args, kwargs
        raise control

    monkeypatch.setattr("fss_cli._recursive_cp._render_failure", fail_render)
    with pytest.raises(Stop) as caught:
        _invoke(
            ["-R", "source:/docs", "destination:/out/copy"],
            {
                "source": _source(source_entries, [], configure=configure),
                "destination": _source(destination_entries, []),
            },
        )

    assert caught.value is control
    assert len(temporary_paths) == 1
    assert not Path(temporary_paths[0]).exists()


def test_recursive_cp_cleans_staging_before_reverse_exits_during_control_flow(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class Stop(BaseException):
        pass

    control = Stop()
    events: list[str] = []
    temporary_paths: list[str] = []
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/file": b"x",
    }
    destination_entries: dict[str, bytes | None] = {"/": None, "/out": None}

    def configure(filesystem: _TreeFileSystem) -> None:
        async def get_file(
            self: _TreeFileSystem,
            remote: str,
            local: str,
            **kwargs: object,
        ) -> NoReturn:
            del self, remote, kwargs
            temporary_paths.append(local)
            Path(local).write_bytes(b"partial")  # noqa: ASYNC240
            raise control

        filesystem._get_file = MethodType(get_file, filesystem)  # type: ignore[method-assign]

    def managed_source(
        name: str,
        entries: dict[str, bytes | None],
        *,
        configure_source: Callable[[_TreeFileSystem], None] | None = None,
    ):
        @asynccontextmanager
        async def source():
            filesystem = _TreeFileSystem(entries, [])
            if configure_source is not None:
                configure_source(filesystem)
            try:
                yield filesystem
            finally:
                events.append(f"{name} exit")

        return source

    real_unlink = Path.unlink

    def recording_unlink(path: Path, *args: object, **kwargs: object) -> None:
        if "fss-cli-cp-recursive-" in path.name:
            events.append("staging cleanup")
        real_unlink(path, *args, **kwargs)

    monkeypatch.setattr(Path, "unlink", recording_unlink)
    with pytest.raises(Stop) as caught:
        _invoke(
            ["-R", "source:/docs", "destination:/out/copy"],
            {
                "source": managed_source(
                    "source",
                    source_entries,
                    configure_source=configure,
                ),
                "destination": managed_source(
                    "destination",
                    destination_entries,
                ),
            },
        )

    assert caught.value is control
    assert events == ["staging cleanup", "destination exit", "source exit"]
    assert len(temporary_paths) == 1
    assert not Path(temporary_paths[0]).exists()


def test_recursive_cp_passes_operation_failure_to_reverse_exits_after_cleanup(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    operation_error = OSError("download")
    events: list[str] = []
    exit_calls: list[tuple[str, BaseException]] = []
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/file": b"x",
    }
    destination_entries: dict[str, bytes | None] = {"/": None, "/out": None}

    def configure(filesystem: _TreeFileSystem) -> None:
        async def get_file(
            self: _TreeFileSystem,
            remote: str,
            local: str,
            **kwargs: object,
        ) -> NoReturn:
            del self, remote, local, kwargs
            raise operation_error

        filesystem._get_file = MethodType(get_file, filesystem)  # type: ignore[method-assign]

    def managed_source(
        name: str,
        entries: dict[str, bytes | None],
        *,
        configure_source: Callable[[_TreeFileSystem], None] | None = None,
    ) -> object:
        @asynccontextmanager
        async def source() -> AsyncIterator[_TreeFileSystem]:
            filesystem = _TreeFileSystem(entries, [])
            if configure_source is not None:
                configure_source(filesystem)
            try:
                yield filesystem
            except BaseException as error:
                exit_calls.append((name, error))
                events.append(f"{name} exit")
                raise

        return source

    real_unlink = Path.unlink

    def recording_unlink(path: Path, *args: object, **kwargs: object) -> None:
        if "fss-cli-cp-recursive-" in path.name:
            events.append("staging cleanup")
        real_unlink(path, *args, **kwargs)

    monkeypatch.setattr(Path, "unlink", recording_unlink)
    result = _invoke(
        ["-R", "source:/docs", "destination:/out/copy"],
        {
            "source": managed_source(
                "source",
                source_entries,
                configure_source=configure,
            ),
            "destination": managed_source("destination", destination_entries),
        },
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: source:/docs: transfer failure; destination residue may remain\n",
    )
    assert events == ["staging cleanup", "destination exit", "source exit"]
    assert [name for name, _ in exit_calls] == ["destination", "source"]
    for _, error in exit_calls:
        assert error is operation_error


@pytest.mark.parametrize(
    ("error", "category"),
    [
        (NotADirectoryError("backend-specific"), "not a directory"),
        (IsADirectoryError("backend-specific"), "is a directory"),
        (FileExistsError("backend-specific"), "file exists"),
    ],
)
def test_recursive_cp_uses_shared_backend_categories(
    error: OSError,
    category: str,
) -> None:
    entries: dict[str, bytes | None] = {"/": None, "/docs": None, "/out": None}
    metadata = {"/docs": error}

    result = _invoke(
        ["-R", "memory:/docs", "memory:/out/copy"],
        {"memory": _source(entries, [], metadata=metadata)},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        f"cp: memory:/docs: {category}\n",
    )


def test_recursive_cp_reports_incompatible_source_context_manager() -> None:
    def source() -> object:
        return object()

    result = _invoke(
        ["-R", "broken:/docs", "broken:/out"],
        {"broken": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: broken: source factory returned incompatible async context manager\n",
    )


def test_recursive_cp_reports_source_entry_failure() -> None:
    class Manager:
        async def __aenter__(self) -> NoReturn:
            message = "entry"
            raise ValueError(message)

        async def __aexit__(self, *args: object) -> None:
            del args

    result = _invoke(
        ["-R", "broken:/docs", "broken:/out"],
        {"broken": Manager},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: broken: source entry failure (ValueError): entry\n",
    )


def test_recursive_cp_reports_incompatible_yielded_filesystem() -> None:
    @asynccontextmanager
    async def source():
        yield object()

    result = _invoke(
        ["-R", "broken:/docs", "broken:/out"],
        {"broken": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: broken: source yielded incompatible async filesystem\n",
    )


def test_recursive_cp_reports_reverse_source_exit_failures() -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/file": b"x",
    }
    destination_entries: dict[str, bytes | None] = {"/": None, "/out": None}

    def exiting_source(
        entries: dict[str, bytes | None],
        error: Exception,
    ):
        @asynccontextmanager
        async def source():
            yield _TreeFileSystem(entries, [])
            raise error

        return source

    result = _invoke(
        ["-R", "alpha:/docs", "beta:/out/copy"],
        {
            "alpha": exiting_source(source_entries, OSError("alpha exit")),
            "beta": exiting_source(
                destination_entries,
                RuntimeError("beta exit"),
            ),
        },
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        _cp_verified("beta:/out/copy/file", "uploaded", 1) + "cp: beta: source exit failure (RuntimeError): beta exit\n"
        "cp: alpha: source exit failure (OSError): alpha exit\n",
    )
    assert destination_entries["/out/copy/file"] == b"x"


def test_recursive_cp_keeps_acquisition_failure_primary_before_exit_failure() -> None:
    @asynccontextmanager
    async def alpha():
        try:
            yield _TreeFileSystem({"/": None, "/docs": None}, [])
        finally:
            message = "alpha exit"
            raise OSError(message)

    def beta() -> NoReturn:
        message = "beta factory"
        raise ValueError(message)

    result = _invoke(
        ["-R", "alpha:/docs", "beta:/out"],
        {"alpha": alpha, "beta": beta},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: beta: source factory failure (ValueError): beta factory\n"
        "cp: alpha: source exit failure (OSError): alpha exit\n",
    )


@pytest.mark.parametrize("phase", ["invocation", "await", "nested"])
def test_recursive_cp_classifies_listing_failures(phase: str) -> None:
    entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/nested": None,
        "/out": None,
    }
    calls: list[tuple[object, ...]] = []

    def configure(filesystem: _TreeFileSystem) -> None:
        if phase == "invocation":

            def ls(self: _TreeFileSystem, *args: object, **kwargs: object) -> NoReturn:
                del self, args, kwargs
                raise OSError(phase)

        elif phase == "await":

            async def ls(
                self: _TreeFileSystem,
                *args: object,
                **kwargs: object,
            ) -> NoReturn:
                del self, args, kwargs
                raise OSError(phase)

        else:
            original = filesystem._ls

            async def ls(  # type: ignore[misc]
                self: _TreeFileSystem,
                path: str,
                *args: object,
                **kwargs: object,
            ) -> object:
                del self
                if path == "/docs/nested":
                    raise OSError(phase)
                return await original(path, *args, **kwargs)

        filesystem._ls = MethodType(ls, filesystem)  # type: ignore[method-assign, possibly-unbound-attribute]

    result = _invoke(
        ["-R", "memory:/docs", "memory:/out/copy"],
        {"memory": _source(entries, calls, configure=configure)},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        f"cp: memory:/docs: backend failure (OSError): {phase}\n",
    )
    assert not [call for call in calls if call[0] in {"mkdir", "put_file"}]


def test_recursive_cp_drains_sibling_listings_after_failure_before_source_exit() -> None:
    entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/a": None,
        "/docs/b": None,
        "/out": None,
    }
    events: list[str] = []

    @asynccontextmanager
    async def source():
        filesystem = _TreeFileSystem(entries, [])
        original = filesystem._ls

        async def ls(
            self: _TreeFileSystem,
            path: str,
            *args: object,
            **kwargs: object,
        ) -> object:
            del self
            if path == "/docs/a":
                message = "listing"
                raise OSError(message)
            if path == "/docs/b":
                for _ in range(3):
                    await asyncio.sleep(0)
                events.append("sibling drained")
            return await original(path, *args, **kwargs)

        filesystem._ls = MethodType(ls, filesystem)  # type: ignore[method-assign]
        try:
            yield filesystem
        finally:
            events.append("source exit")

    result = _invoke(
        ["-R", "memory:/docs", "memory:/out/copy"],
        {"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: memory:/docs: backend failure (OSError): listing\n",
    )
    assert events == ["sibling drained", "source exit"]
