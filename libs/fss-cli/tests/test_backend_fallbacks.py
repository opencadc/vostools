"""Backend-neutral fallback and operand-identity regressions through public commands."""

from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from types import MethodType
from typing import TYPE_CHECKING

import pytest
from fsspec.asyn import AsyncFileSystem
from fsspec.implementations.asyn_wrapper import AsyncFileSystemWrapper
from fsspec.implementations.local import LocalFileSystem
from typer.testing import CliRunner

from fss_cli import App
from fss_cli._listing import _normalize_time
from fss_cli._metadata import normalize_time
from fss_cli._results import infos_for_paths

from .test_recursive_cp import _invoke, _source, _TreeFileSystem

if TYPE_CHECKING:
    from collections.abc import AsyncGenerator
    from pathlib import Path


def test_recursive_copy_falls_back_when_backend_copy_is_unimplemented(monkeypatch: pytest.MonkeyPatch) -> None:
    entries = {"/": None, "/docs": None, "/docs/f": b"payload", "/out": None}
    calls = []

    def configure(fs: _TreeFileSystem) -> None:
        monkeypatch.setattr(fs, "_cp_file", MethodType(AsyncFileSystem._cp_file, fs))

    result = _invoke(
        ["-R", "memory:/docs", "memory:/out/copy"], {"memory": _source(entries, calls, configure=configure)}
    )
    assert result.exit_code == 0, result.stderr
    assert entries["/out/copy/f"] == b"payload"
    assert ("get_file", "/docs/f") in calls


@pytest.mark.parametrize("error_type", [PermissionError, NotImplementedError])
def test_recursive_copy_uses_direct_info_when_destination_listing_is_unavailable(
    error_type: type[Exception], monkeypatch: pytest.MonkeyPatch
) -> None:
    entries = {"/": None, "/docs": None, "/docs/f": b"payload", "/out": None}

    def configure(fs: _TreeFileSystem) -> None:
        original = fs._ls

        async def ls(self: _TreeFileSystem, path: str, **kwargs: object) -> object:
            del self, kwargs
            if path.startswith("/out"):
                message = "no listing"
                raise error_type(message)
            return await original(path)

        monkeypatch.setattr(fs, "_ls", MethodType(ls, fs))

    result = _invoke(["-R", "memory:/docs", "memory:/out/copy"], {"memory": _source(entries, [], configure=configure)})
    assert result.exit_code == 0, result.stderr
    assert entries["/out/copy/f"] == b"payload"


@pytest.mark.parametrize("flags", [[], ["-l"]])
def test_ls_preserves_a_local_directory_link(tmp_path: Path, flags: list[str]) -> None:
    target = tmp_path / "target"
    target.mkdir()
    (target / "child").write_bytes(b"content")
    link = tmp_path / "link"
    link.symlink_to(target, target_is_directory=True)

    @asynccontextmanager
    async def source() -> AsyncGenerator[AsyncFileSystemWrapper]:
        yield AsyncFileSystemWrapper(LocalFileSystem(), asynchronous=True)

    result = CliRunner().invoke(App({"local": source}).typer_app, ["ls", *flags, f"local:{link}"])
    assert result.exit_code == 0, result.stderr
    assert "link" in result.stdout
    assert "child" not in result.stdout
    if flags:
        assert f"link -> {target}" in result.stdout


def test_copy_report_order_survives_an_early_middle_failure(monkeypatch: pytest.MonkeyPatch) -> None:
    entries = {"/": None, "/docs": None, "/docs/a": b"a", "/docs/c": b"c", "/out": None}

    def configure(fs: _TreeFileSystem) -> None:
        original = fs._cp_file

        async def copy(self: _TreeFileSystem, first: str, second: str, **kwargs: object) -> None:
            del self
            if first == "/docs/a":
                for _ in range(20):
                    await asyncio.sleep(0)
            await original(first, second, **kwargs)

        monkeypatch.setattr(fs, "_cp_file", MethodType(copy, fs))

    result = CliRunner().invoke(
        App({"memory": _source(entries, [], configure=configure)}).typer_app,
        ["cp", "memory:/docs/a", "memory:/docs/missing", "memory:/docs/c", "memory:/out"],
    )
    assert result.exit_code == 1
    assert result.stderr.index("/out/a: copied") < result.stderr.index("/out/c: copied")


@pytest.mark.parametrize("value", ["2026-10-16T00:00:00", datetime(2026, 10, 16)])  # noqa: DTZ001 - naive input regression.
def test_stat_and_listing_normalize_naive_time_as_utc(value: object) -> None:
    expected = datetime(2026, 10, 16, tzinfo=timezone.utc).timestamp()
    assert normalize_time(value) == _normalize_time(value) == expected


@pytest.mark.parametrize("duplicate", ["/dst/f", "/dst//f", "dst/f"])
def test_duplicate_destination_rows_are_rejected_before_info_is_used(
    monkeypatch: pytest.MonkeyPatch, duplicate: str
) -> None:
    fs = _TreeFileSystem({"/dst": None, "/dst/f": b"data"}, [])

    async def ls(path: str, **kwargs: object) -> object:
        del path, kwargs
        return [{"name": "/dst/f", "type": "directory"}, {"name": duplicate, "type": "file", "size": 4}]

    monkeypatch.setattr(fs, "_ls", ls)
    found, error = asyncio.run(infos_for_paths(fs, ["/dst/f"], missing_ok=False))
    assert error is not None
    assert not found


def test_sparse_destination_metadata_keeps_bounded_concurrency(monkeypatch: pytest.MonkeyPatch) -> None:
    paths = [f"/dst/{i}" for i in range(20)]
    fs = _TreeFileSystem({"/dst": None, **dict.fromkeys(paths, b"data")}, [])
    original = fs._info
    active = peak = 0

    async def ls(path: str, **kwargs: object) -> object:
        del path, kwargs
        return [{"name": path, "type": "file"} for path in paths]

    async def info(path: str, **kwargs: object) -> object:
        nonlocal active, peak
        active += 1
        peak = max(peak, active)
        await asyncio.sleep(0)
        active -= 1
        return await original(path, **kwargs)

    monkeypatch.setattr(fs, "_ls", ls)
    monkeypatch.setattr(fs, "_info", info)
    found, error = asyncio.run(infos_for_paths(fs, paths, missing_ok=False))
    assert error is None
    assert len(found) == 20
    assert 1 < peak <= 16


def test_find_accepts_backend_names_without_a_leading_slash(monkeypatch: pytest.MonkeyPatch) -> None:
    entries = {"/bucket/docs": None, "/bucket/docs/f": b"payload"}

    def configure(fs: _TreeFileSystem) -> None:
        original = fs._ls

        async def ls(path: str, **kwargs: object) -> object:
            del kwargs
            return [{**row, "name": str(row["name"]).lstrip("/")} for row in await original(path)]

        monkeypatch.setattr(fs, "_ls", ls)

    result = CliRunner().invoke(
        App({"s3like": _source(entries, [], configure=configure)}).typer_app,
        ["find", "s3like:/bucket/docs"],
    )
    assert result.exit_code == 0, result.stderr
    assert "bucket/docs/f" in result.stdout


def test_metadata_index_matches_backend_names_without_a_leading_slash(monkeypatch: pytest.MonkeyPatch) -> None:
    calls = []
    fs = _TreeFileSystem({"/bucket/docs": None, "/bucket/docs/f": b"payload"}, calls)

    async def ls(path: str, **kwargs: object) -> object:
        del path, kwargs
        return [{"name": "bucket/docs/f", "type": "file", "size": 7}]

    monkeypatch.setattr(fs, "_ls", ls)
    found, error = asyncio.run(infos_for_paths(fs, ["/bucket/docs/f"], missing_ok=False))
    assert error is None
    assert found["/bucket/docs/f"] == {"name": "bucket/docs/f", "type": "file", "size": 7}
    assert not calls
