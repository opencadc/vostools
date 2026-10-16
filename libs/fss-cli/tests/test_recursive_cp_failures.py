"""Recursive ``cp`` failure, cleanup, and cancellation tests."""

from __future__ import annotations

import asyncio
import os
import tempfile
from pathlib import Path
from types import MethodType
from typing import NoReturn

import pytest

from ._support import _cp_verified, _outcomes_by_file
from .test_recursive_cp import _invoke, _source, _TreeFileSystem


def _file_info(name: object, **fields: object) -> dict[object, object]:
    return {"name": name, "type": "file", "size": 1, **fields}


class _ListSubclass(list):
    pass


_MALFORMED_LISTINGS: dict[str, object] = {
    "duplicate": [_file_info("/docs/f"), _file_info("/docs/f")],
    "duplicate-directory": [
        {"name": "/docs/d", "type": "directory", "size": 0},
        _file_info("/docs/d/"),
    ],
    "outside": [_file_info("/outside/f")],
    "nested": [_file_info("/docs/deeper/f")],
    "self": [{"name": "/docs", "type": "directory", "size": 0}],
    "tuple": (_file_info("/docs/f"),),
    "list-subclass": _ListSubclass([_file_info("/docs/f")]),
    "none": None,
    "non-mapping": [("name", "/docs/f")],
    "non-string-name": [_file_info(b"/docs/f")],
    "non-string-type": [{"name": "/docs/f", "type": None, "size": 1}],
}


@pytest.mark.parametrize("shape", list(_MALFORMED_LISTINGS))
def test_recursive_cp_rejects_malformed_listing_shapes_before_mutation(
    shape: str,
) -> None:
    entries: dict[str, bytes | None] = {"/": None, "/docs": None, "/out": None}
    calls: list[tuple[object, ...]] = []

    def configure(filesystem: _TreeFileSystem) -> None:
        async def ls(
            self: _TreeFileSystem,
            path: str,
            *args: object,
            **kwargs: object,
        ) -> object:
            del args, kwargs
            self.calls.append(("ls", path))
            return _MALFORMED_LISTINGS[shape]

        filesystem._ls = MethodType(ls, filesystem)  # type: ignore[method-assign]

    result = _invoke(
        ["-R", "memory:/docs", "memory:/out/copy"],
        {"memory": _source(entries, calls, configure=configure)},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: memory:/docs: incompatible result\n",
    )
    assert [call for call in calls if call[0] == "ls"] == [("ls", "/docs")]
    assert not [call for call in calls if call[0] in {"mkdir", "put_file"}]


def test_recursive_cp_fails_when_an_advertised_directory_vanishes() -> None:
    entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/nested": None,
        "/docs/file": b"x",
        "/out": None,
    }
    calls: list[tuple[object, ...]] = []

    def configure(filesystem: _TreeFileSystem) -> None:
        original = filesystem._ls

        async def ls(
            self: _TreeFileSystem,
            path: str,
            detail: bool = True,  # noqa: FBT002 - fsspec hook signature.
            **kwargs: object,
        ) -> object:
            if path == "/docs/nested":
                self.calls.append(("ls", path))
                raise FileNotFoundError(path)
            return await original(path, detail, **kwargs)

        filesystem._ls = MethodType(ls, filesystem)  # type: ignore[method-assign]

    result = _invoke(
        ["-R", "memory:/docs", "memory:/out/copy"],
        {"memory": _source(entries, calls, configure=configure)},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: memory:/docs: not found\n",
    )
    assert not [call for call in calls if call[0] in {"mkdir", "get_file", "put_file"}]


@pytest.mark.parametrize("locked", ["/docs/a", "/docs/a/b", "/docs/z/y/x"])
def test_recursive_cp_fails_on_unreadable_nested_directory_before_mutation(
    locked: str,
) -> None:
    entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/a": None,
        "/docs/a/b": None,
        "/docs/a/b/deep.txt": b"deep",
        "/docs/top.txt": b"top",
        "/docs/z": None,
        "/docs/z/y": None,
        "/docs/z/y/x": None,
        "/out": None,
    }
    calls: list[tuple[object, ...]] = []

    def configure(filesystem: _TreeFileSystem) -> None:
        original = filesystem._ls

        async def ls(
            self: _TreeFileSystem,
            path: str,
            detail: bool = True,  # noqa: FBT002 - fsspec hook signature.
            **kwargs: object,
        ) -> object:
            if path == locked:
                self.calls.append(("ls", path))
                message = "denied"
                raise PermissionError(message)
            return await original(path, detail, **kwargs)

        filesystem._ls = MethodType(ls, filesystem)  # type: ignore[method-assign]

    result = _invoke(
        ["-R", "memory:/docs", "memory:/out/copy"],
        {"memory": _source(entries, calls, configure=configure)},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: memory:/docs: permission denied\n",
    )
    assert ("ls", locked) in calls
    assert not [call for call in calls if call[0] in {"mkdir", "get_file", "put_file"}]
    assert "/out/copy" not in entries


@pytest.mark.parametrize(
    ("error", "category"),
    [
        (PermissionError("denied"), "permission denied"),
        (NotImplementedError("missing"), "unsupported operation"),
        (NotADirectoryError("backend-specific"), "not a directory"),
    ],
)
def test_recursive_cp_classifies_destination_preflight_failures(
    error: Exception,
    category: str,
) -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/file": b"x",
    }
    # The existing directory receives the source as /out/copy/docs; that
    # resolved root exists, so its entries are read before any mutation.
    destination_entries: dict[str, bytes | None] = {
        "/": None,
        "/out": None,
        "/out/copy": None,
        "/out/copy/docs": None,
    }
    destination_calls: list[tuple[object, ...]] = []
    metadata = {"/out/copy/docs/file": error}

    result = _invoke(
        ["-R", "source:/docs", "destination:/out/copy"],
        {
            "source": _source(source_entries, []),
            "destination": _source(
                destination_entries,
                destination_calls,
                metadata=metadata,
            ),
        },
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        f"cp: destination:/out/copy: {category}\n",
    )
    assert "/out/copy/docs/file" not in destination_entries
    assert not [call for call in destination_calls if call[0] in {"mkdir", "put_file"}]


def test_recursive_cp_reports_directory_creation_failure_with_residue() -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/file": b"x",
    }
    destination_entries: dict[str, bytes | None] = {"/": None, "/out": None}

    def configure(filesystem: _TreeFileSystem) -> None:
        async def mkdir(
            self: _TreeFileSystem,
            path: str,
            create_parents: bool = True,  # noqa: FBT002
            **kwargs: object,
        ) -> NoReturn:
            del self, path, create_parents, kwargs
            message = "mkdir"
            raise OSError(message)

        filesystem._mkdir = MethodType(mkdir, filesystem)  # type: ignore[method-assign]

    result = _invoke(
        ["-R", "source:/docs", "destination:/out/copy"],
        {
            "source": _source(source_entries, []),
            "destination": _source(destination_entries, [], configure=configure),
        },
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: destination:/out/copy: mutation failure; destination residue may remain\n",
    )
    assert "/out/copy" not in destination_entries


def test_recursive_cp_reports_staging_creation_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/file": b"x",
    }
    destination_entries: dict[str, bytes | None] = {
        "/": None,
        "/out": None,
        "/out/copy": None,
    }

    def fail_mkstemp(*args: object, **kwargs: object) -> NoReturn:
        del args, kwargs
        message = "staging"
        raise OSError(message)

    monkeypatch.setattr("fss_cli._recursive_cp.tempfile.mkstemp", fail_mkstemp)
    result = _invoke(
        ["-R", "source:/docs", "destination:/out/copy"],
        {
            "source": _source(source_entries, []),
            "destination": _source(destination_entries, []),
        },
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: source:/docs: staging failure (OSError); destination residue may remain\n",
    )


def test_recursive_cp_reports_staging_stat_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/file": b"x",
    }
    destination_entries: dict[str, bytes | None] = {"/": None, "/out": None}
    real_stat = Path.stat

    def fail_temporary_stat(path: Path, *args: object, **kwargs: object):
        if path.name.startswith("fss-cli-cp-recursive-"):
            message = "stat"
            raise OSError(message)
        return real_stat(path, *args, **kwargs)

    monkeypatch.setattr("fss_cli._recursive_cp.Path.stat", fail_temporary_stat)
    result = _invoke(
        ["-R", "source:/docs", "destination:/out/copy"],
        {
            "source": _source(source_entries, []),
            "destination": _source(destination_entries, []),
        },
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: source:/docs: staging failure (OSError); destination residue may remain\n",
    )
    assert "/out/copy/file" not in destination_entries


def test_recursive_cp_reports_staging_descriptor_close_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/file": b"x",
    }
    destination_entries: dict[str, bytes | None] = {"/": None, "/out": None}
    real_mkstemp = tempfile.mkstemp
    real_close = os.close
    staging_descriptor: int | None = None

    def capture_mkstemp(*args: object, **kwargs: object) -> tuple[int, str]:
        nonlocal staging_descriptor
        descriptor, path = real_mkstemp(*args, **kwargs)
        staging_descriptor = descriptor
        return descriptor, path

    def fail_staging_close(descriptor: int) -> None:
        if descriptor == staging_descriptor:
            real_close(descriptor)
            message = "close"
            raise OSError(message)
        real_close(descriptor)

    monkeypatch.setattr(
        "fss_cli._recursive_cp.tempfile.mkstemp",
        capture_mkstemp,
    )
    monkeypatch.setattr("fss_cli._recursive_cp.os.close", fail_staging_close)
    result = _invoke(
        ["-R", "source:/docs", "destination:/out/copy"],
        {
            "source": _source(source_entries, []),
            "destination": _source(destination_entries, []),
        },
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: source:/docs: staging failure (OSError); destination residue may remain\n",
    )
    assert "/out/copy/file" not in destination_entries


def test_recursive_cp_rejects_staged_size_change_before_upload() -> None:
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
        ) -> None:
            del self, remote, kwargs
            Path(local).write_bytes(b"changed")  # noqa: ASYNC240

        filesystem._get_file = MethodType(get_file, filesystem)  # type: ignore[method-assign]

    result = _invoke(
        ["-R", "source:/docs", "destination:/out/copy"],
        {
            "source": _source(source_entries, [], configure=configure),
            "destination": _source(destination_entries, []),
        },
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: source:/docs: source changed; destination residue may remain\n",
    )
    assert "/out/copy/file" not in destination_entries


def test_recursive_cp_reports_cleanup_failure_after_success(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/file": b"x",
    }
    destination_entries: dict[str, bytes | None] = {"/": None, "/out": None}
    temporary_paths: list[Path] = []
    real_unlink = Path.unlink

    def fail_temporary_unlink(path: Path, missing_ok: bool = False) -> None:  # noqa: FBT002
        if path.name.startswith("fss-cli-cp-recursive-"):
            temporary_paths.append(path)
            message = "cleanup"
            raise OSError(message)
        real_unlink(path, missing_ok=missing_ok)

    monkeypatch.setattr("fss_cli._recursive_cp.Path.unlink", fail_temporary_unlink)
    result = _invoke(
        ["-R", "source:/docs", "destination:/out/copy"],
        {
            "source": _source(source_entries, []),
            "destination": _source(destination_entries, []),
        },
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        _cp_verified("destination:/out/copy/file", "uploaded", 1)
        + "cp: source:/docs: staging cleanup failure (OSError); "
        "host staging residue may remain; destination residue may remain\n",
    )
    assert destination_entries["/out/copy/file"] == b"x"
    assert len(temporary_paths) == 1
    real_unlink(temporary_paths[0], missing_ok=True)


def test_recursive_cp_preserves_control_over_cleanup_base_exception(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class Stop(BaseException):
        pass

    primary = Stop()
    cleanup = Stop()
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
            raise primary

        filesystem._get_file = MethodType(get_file, filesystem)  # type: ignore[method-assign]

    def fail_unlink(*args: object, **kwargs: object) -> NoReturn:
        del args, kwargs
        raise cleanup

    monkeypatch.setattr("fss_cli._recursive_cp.Path.unlink", fail_unlink)
    with pytest.raises(Stop) as caught:
        _invoke(
            ["-R", "source:/docs", "destination:/out/copy"],
            {
                "source": _source(source_entries, [], configure=configure),
                "destination": _source(destination_entries, []),
            },
        )

    assert caught.value is primary


def test_recursive_cp_propagates_cleanup_base_exception_after_success(
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
    temporary_paths: list[Path] = []
    real_unlink = Path.unlink

    def fail_unlink(path: Path, *args: object, **kwargs: object) -> NoReturn:
        del args, kwargs
        temporary_paths.append(path)
        raise control

    monkeypatch.setattr("fss_cli._recursive_cp.Path.unlink", fail_unlink)
    with pytest.raises(Stop) as caught:
        _invoke(
            ["-R", "source:/docs", "destination:/out/copy"],
            {
                "source": _source(source_entries, []),
                "destination": _source(destination_entries, []),
            },
        )

    assert caught.value is control
    assert destination_entries["/out/copy/file"] == b"x"
    assert len(temporary_paths) == 1
    real_unlink(temporary_paths[0], missing_ok=True)


@pytest.mark.parametrize(
    "phase",
    [
        "source info",
        "target info",
        "listing",
        "destination preflight",
        "mkdir",
        "download",
        "upload",
        "source revalidation info",
        "source revalidation listing",
        "destination proof",
    ],
)
def test_recursive_cp_drains_current_operation_on_cancellation(  # noqa: C901, PLR0915 - explicit phase matrix.
    phase: str,
) -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/file": b"x",
    }
    destination_entries: dict[str, bytes | None] = {"/": None, "/out": None}
    if phase == "destination preflight":
        # Per-entry preflight reads happen only below an existing target, which
        # then receives the source as /out/copy/docs.
        destination_entries["/out/copy"] = None
        destination_entries["/out/copy/docs"] = None
    drained = False
    uploaded = False
    temporary_paths: list[str] = []
    owner_task: asyncio.Task[object] | None = None

    async def cancel_then_resume() -> None:
        nonlocal drained
        assert owner_task is not None
        owner_task.cancel()
        await asyncio.sleep(0)
        drained = True

    def configure_source(filesystem: _TreeFileSystem) -> None:
        nonlocal owner_task
        owner_task = asyncio.current_task()
        assert owner_task is not None
        original_info = filesystem._info
        info_calls = 0

        async def info(
            self: _TreeFileSystem,
            path: str,
            **kwargs: object,
        ) -> dict[str, object]:
            nonlocal info_calls
            del self
            info_calls += 1
            if (phase == "source info" and info_calls == 1) or (
                phase == "source revalidation info" and info_calls == 2
            ):
                await cancel_then_resume()
            return await original_info(path, **kwargs)

        original_ls = filesystem._ls
        ls_calls = 0

        async def ls(
            self: _TreeFileSystem,
            path: str,
            detail: bool = True,  # noqa: FBT002
            **kwargs: object,
        ) -> list[dict[str, object]]:
            nonlocal ls_calls
            del self
            ls_calls += 1
            if (phase == "listing" and ls_calls == 1) or (phase == "source revalidation listing" and ls_calls == 2):
                await cancel_then_resume()
            return await original_ls(path, detail, **kwargs)

        original_get = filesystem._get_file

        async def get_file(
            self: _TreeFileSystem,
            remote: str,
            local: str,
            **kwargs: object,
        ) -> None:
            del self
            temporary_paths.append(local)
            if phase == "download":
                await cancel_then_resume()
            await original_get(remote, local, **kwargs)

        filesystem._info = MethodType(info, filesystem)  # type: ignore[method-assign]
        filesystem._ls = MethodType(ls, filesystem)  # type: ignore[method-assign]
        filesystem._get_file = MethodType(get_file, filesystem)  # type: ignore[method-assign]

    def configure_destination(filesystem: _TreeFileSystem) -> None:
        nonlocal owner_task
        if owner_task is None:
            owner_task = asyncio.current_task()
        assert owner_task is not None
        original_info = filesystem._info
        path_calls: dict[str, int] = {}

        async def info(
            self: _TreeFileSystem,
            path: str,
            **kwargs: object,
        ) -> dict[str, object]:
            del self
            path_calls[path] = path_calls.get(path, 0) + 1
            should_cancel = (
                (phase == "target info" and path == "/out/copy" and path_calls[path] == 1)
                or (phase == "destination preflight" and path == "/out/copy/docs/file" and path_calls[path] == 1)
                or (phase == "destination proof" and path == "/out/copy/file" and uploaded)
            )
            if should_cancel:
                await cancel_then_resume()
            return await original_info(path, **kwargs)

        original_mkdir = filesystem._mkdir

        async def mkdir(
            self: _TreeFileSystem,
            path: str,
            create_parents: bool = True,  # noqa: FBT002
            **kwargs: object,
        ) -> None:
            del self
            if phase == "mkdir":
                await cancel_then_resume()
            await original_mkdir(path, create_parents, **kwargs)

        original_put = filesystem._put_file

        async def put_file(
            self: _TreeFileSystem,
            local: str,
            remote: str,
            mode: str = "overwrite",
            **kwargs: object,
        ) -> None:
            nonlocal uploaded
            del self
            if phase == "upload":
                await cancel_then_resume()
            await original_put(local, remote, mode, **kwargs)
            uploaded = True

        filesystem._info = MethodType(info, filesystem)  # type: ignore[method-assign]
        filesystem._mkdir = MethodType(mkdir, filesystem)  # type: ignore[method-assign]
        filesystem._put_file = MethodType(put_file, filesystem)  # type: ignore[method-assign]

    with pytest.raises(asyncio.CancelledError):
        _invoke(
            ["-R", "source:/docs", "destination:/out/copy"],
            {
                "source": _source(source_entries, [], configure=configure_source),
                "destination": _source(
                    destination_entries,
                    [],
                    configure=configure_destination,
                ),
            },
        )

    assert drained
    assert not [path for path in temporary_paths if Path(path).exists()]
    if phase in {"source info", "target info", "listing"}:
        assert "/out/copy" not in destination_entries
    elif phase == "destination preflight":
        assert "/out/copy/docs/file" not in destination_entries
    elif phase in {"mkdir", "download"}:
        assert destination_entries["/out/copy"] is None
        assert "/out/copy/file" not in destination_entries
    else:
        assert destination_entries["/out/copy/file"] == b"x"


def test_recursive_cp_classifies_final_source_listing_failure() -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/file": b"x",
    }
    destination_entries: dict[str, bytes | None] = {"/": None, "/out": None}

    def configure(filesystem: _TreeFileSystem) -> None:
        original = filesystem._ls
        calls = 0

        async def ls(
            self: _TreeFileSystem,
            path: str,
            detail: bool = True,  # noqa: FBT002
            **kwargs: object,
        ) -> list[dict[str, object]]:
            nonlocal calls
            del self
            calls += 1
            if calls == 2:
                message = "final listing"
                raise OSError(message)
            return await original(path, detail, **kwargs)

        filesystem._ls = MethodType(ls, filesystem)  # type: ignore[method-assign]

    result = _invoke(
        ["-R", "source:/docs", "destination:/out/copy"],
        {
            "source": _source(source_entries, [], configure=configure),
            "destination": _source(destination_entries, []),
        },
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        _cp_verified("destination:/out/copy/file", "uploaded", 1)
        + "cp: source:/docs: source revalidation failure; destination residue may remain\n",
    )
    assert destination_entries["/out/copy/file"] == b"x"


def test_recursive_cp_classifies_destination_proof_failure() -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/file": b"x",
    }
    destination_entries: dict[str, bytes | None] = {"/": None, "/out": None}

    def configure(filesystem: _TreeFileSystem) -> None:
        original = filesystem._info

        async def info(
            self: _TreeFileSystem,
            path: str,
            **kwargs: object,
        ) -> dict[str, object]:
            if path == "/out/copy/file" and path in self.entries:
                message = "proof"
                raise OSError(message)
            return await original(path, **kwargs)

        filesystem._info = MethodType(info, filesystem)  # type: ignore[method-assign]

    result = _invoke(
        ["-R", "source:/docs", "destination:/out/copy"],
        {
            "source": _source(source_entries, []),
            "destination": _source(destination_entries, [], configure=configure),
        },
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: destination:/out/copy/file: uploaded 1 bytes\n"
        "cp: destination:/out/copy/file: verification failure (OSError); destination residue may remain\n",
    )
    assert destination_entries["/out/copy/file"] == b"x"


def test_recursive_cp_accepts_matching_shared_tokens() -> None:
    source_entries: dict[str, bytes | None] = {
        "/": None,
        "/docs": None,
        "/docs/file": b"x",
    }
    source_metadata = {
        "/docs/file": {
            "name": "/docs/file",
            "type": "file",
            "size": 1,
            "checksum": "same",
        }
    }
    destination_entries: dict[str, bytes | None] = {
        "/": None,
        "/out": None,
        "/out/copy": None,
        "/out/copy/file": b"x",
    }
    destination_metadata = {
        "/out/copy/file": {
            "name": "/out/copy/file",
            "type": "file",
            "size": 1,
            "checksum": "same",
        }
    }

    result = _invoke(
        ["-R", "source:/docs", "destination:/out/copy"],
        {
            "source": _source(source_entries, [], metadata=source_metadata),
            "destination": _source(
                destination_entries,
                [],
                metadata=destination_metadata,
            ),
        },
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("destination:/out/copy/docs/file", "uploaded", 1),
    )
    assert destination_entries["/out/copy/file"] == b"x"


def test_recursive_cp_reports_first_failure_in_manifest_order() -> None:  # noqa: C901 - scripted per-file timing.
    source_entries: dict[str, bytes | None] = {"/": None, "/docs": None}
    for index in range(20):
        source_entries[f"/docs/f{index:02d}"] = b"x"
    destination_entries: dict[str, bytes | None] = {"/": None, "/out": None}
    started: list[str] = []
    staged: list[str] = []
    events: list[str] = []

    async def yield_control(times: int) -> None:
        for _ in range(times):
            await asyncio.sleep(0)

    def configure_source(filesystem: _TreeFileSystem) -> None:
        original = filesystem._get_file

        async def get_file(
            self: _TreeFileSystem,
            remote: str,
            local: str,
            **kwargs: object,
        ) -> None:
            del self
            started.append(remote)
            staged.append(local)
            if remote == "/docs/f03":
                # Earlier in manifest order, but fails after the later one.
                await yield_control(10)
                events.append("f03 failed")
                message = "slow download"
                raise OSError(message)
            if remote != "/docs/f05":
                await yield_control(20)
            await original(remote, local, **kwargs)

        filesystem._get_file = MethodType(get_file, filesystem)  # type: ignore[method-assign]

    def configure_destination(filesystem: _TreeFileSystem) -> None:
        original = filesystem._put_file

        async def put_file(
            self: _TreeFileSystem,
            local: str,
            remote: str,
            mode: str = "overwrite",
            **kwargs: object,
        ) -> None:
            del self
            if remote == "/out/copy/f05":
                events.append("f05 failed")
                message = "fast upload"
                raise OSError(message)
            await original(local, remote, mode, **kwargs)

        filesystem._put_file = MethodType(put_file, filesystem)  # type: ignore[method-assign]

    result = _invoke(
        ["-R", "source:/docs", "destination:/out/copy"],
        {
            "source": _source(source_entries, [], configure=configure_source),
            "destination": _source(
                destination_entries,
                [],
                configure=configure_destination,
            ),
        },
    )

    assert events == ["f05 failed", "f03 failed"]
    # f03 precedes f05 in the manifest, so its category is the one rendered,
    # after the outcome lines of every transfer that was already in flight.
    assert (result.exit_code, result.stdout) == (1, "")
    assert result.stderr.endswith("cp: source:/docs: transfer failure; destination residue may remain\n")
    assert _outcomes_by_file(result.stderr) == {
        **{
            f"destination:/out/copy/f{index:02d}": ["uploaded 1 bytes", "verified 1 bytes"]
            for index in range(16)
            if index not in {3, 5}
        },
        "source:/docs": ["transfer failure; destination residue may remain"],
    }
    # Only the first bound's worth of transfers ever started: none began
    # after the f05 failure was observed, and in-flight ones finished.
    assert started == [f"/docs/f{index:02d}" for index in range(16)]
    assert sorted(path for path in destination_entries if path.startswith("/out/copy/")) == [
        f"/out/copy/f{index:02d}" for index in range(16) if index not in {3, 5}
    ]
    assert not [path for path in staged if Path(path).exists()]
