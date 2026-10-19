"""Transfer log records emitted under the ``fss_cli`` logger hierarchy."""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from typing import TYPE_CHECKING

import pytest
from fsspec.implementations.asyn_wrapper import AsyncFileSystemWrapper
from fsspec.implementations.local import LocalFileSystem
from fsspec.implementations.memory import MemoryFileSystem
from typer.testing import CliRunner

from fss_cli import App
from fss_cli._logging import _redact

from ._support import _cp_verified, _RecordingSource, _verified_uploads

if TYPE_CHECKING:
    from collections.abc import AsyncIterator, Callable, Mapping
    from pathlib import Path

    from typer.testing import Result


def _memory(
    monkeypatch: pytest.MonkeyPatch,
    files: Mapping[str, bytes],
    directories: tuple[str, ...] = (),
) -> Callable[[], object]:
    """Return a source over one isolated Memory store that persists across runs."""
    monkeypatch.setattr(MemoryFileSystem, "store", {})
    monkeypatch.setattr(MemoryFileSystem, "pseudo_dirs", [""])
    monkeypatch.setattr(MemoryFileSystem, "_cache", {})
    filesystem = MemoryFileSystem(skip_instance_cache=True)
    for directory in directories:
        filesystem.makedirs(directory, exist_ok=True)
    for path, content in files.items():
        filesystem.pipe_file(path, content)

    @asynccontextmanager
    async def source() -> AsyncIterator[AsyncFileSystemWrapper]:
        yield AsyncFileSystemWrapper(filesystem, asynchronous=True)

    return source


def _local() -> Callable[[], object]:
    @asynccontextmanager
    async def source() -> AsyncIterator[AsyncFileSystemWrapper]:
        yield AsyncFileSystemWrapper(LocalFileSystem(skip_instance_cache=True), asynchronous=True)

    return source


def _run(sources: Mapping[str, object], arguments: list[str]) -> Result:
    return CliRunner().invoke(App(sources).typer_app, arguments)  # type: ignore[arg-type]


def _info_records(caplog: pytest.LogCaptureFixture) -> list[logging.LogRecord]:
    return [
        record for record in caplog.records if record.name.startswith("fss_cli.") and record.levelno == logging.INFO
    ]


def _summary(record: logging.LogRecord) -> tuple[object, ...]:
    return (
        record.__dict__["outcome"],
        record.__dict__["source"],
        record.__dict__["destination"],
        record.__dict__["bytes"],
    )


def test_package_logger_has_only_a_null_handler() -> None:
    handlers = logging.getLogger("fss_cli").handlers
    assert [type(handler) for handler in handlers] == [logging.NullHandler]


def test_same_source_copy_logs_one_verified_record_per_file(
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
) -> None:
    source = _memory(monkeypatch, {"/docs/a.txt": b"abc", "/docs/b.txt": b"hello"})
    caplog.set_level(logging.INFO, logger="fss_cli")

    result = _run(
        {"memory": source},
        ["cp", "memory:/docs/a.txt", "memory:/docs/b.txt", "memory:/"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("memory:/a.txt", "copied", 3) + _cp_verified("memory:/b.txt", "copied", 5),
    )
    records = _info_records(caplog)
    assert [_summary(record) for record in records] == [
        ("copied", "memory:/docs/a.txt", "memory:/a.txt", 3),
        ("copied", "memory:/docs/b.txt", "memory:/b.txt", 5),
    ]
    assert all(record.name == "fss_cli._cp" for record in records)
    assert all(type(record.__dict__["duration"]) is float for record in records)
    assert records[0].getMessage().startswith("copied memory:/docs/a.txt -> memory:/a.txt (3 bytes, ")


def test_cross_source_copy_logs_a_staged_record_and_debug_staging(
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
    tmp_path: Path,
) -> None:
    source = _memory(monkeypatch, {"/docs/a.txt": b"abc"})
    caplog.set_level(logging.DEBUG, logger="fss_cli")

    result = _run(
        {"memory": source, "local": _local()},
        ["cp", "memory:/docs/a.txt", f"local:{tmp_path.as_posix()}"],
    )

    assert (result.exit_code, result.stderr) == (
        0,
        _cp_verified(f"local:{tmp_path.as_posix()}/a.txt", "uploaded", 3),
    )
    assert [_summary(record) for record in _info_records(caplog)] == [
        ("staged", "memory:/docs/a.txt", f"local:{tmp_path.as_posix()}/a.txt", 3),
    ]
    assert any(
        record.levelno == logging.DEBUG and record.getMessage().startswith("staged ") for record in caplog.records
    )


def test_move_logs_a_moved_record(caplog: pytest.LogCaptureFixture) -> None:
    source = _RecordingSource(
        [],
        file_contents={"/docs/notes.txt": b"payload"},
        directories={"/", "/docs"},
    )
    caplog.set_level(logging.INFO, logger="fss_cli")

    result = _run({"memory": source}, ["mv", "memory:/docs/notes.txt", "memory:/docs/moved.txt"])

    assert (result.exit_code, result.stderr) == (0, "")
    assert [_summary(record) for record in _info_records(caplog)] == [
        ("moved", "memory:/docs/notes.txt", "memory:/docs/moved.txt", 7),
    ]


def test_recursive_copy_rerun_logs_skipped_identical_files(
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
) -> None:
    source = _memory(
        monkeypatch,
        {"/docs/a.txt": b"abc", "/docs/sub/b.txt": b"hello"},
        directories=("/out",),
    )
    caplog.set_level(logging.DEBUG, logger="fss_cli")
    arguments = ["cp", "-R", "memory:/docs", "memory:/out"]

    first = _run({"memory": source}, arguments)

    assert first.exit_code == 0
    assert _verified_uploads(first.stderr) == {"memory:/out/docs/a.txt": 3, "memory:/out/docs/sub/b.txt": 5}
    assert sorted(_summary(record) for record in _info_records(caplog)) == [
        ("copied", "memory:/docs/a.txt", "memory:/out/docs/a.txt", 3),
        ("copied", "memory:/docs/sub/b.txt", "memory:/out/docs/sub/b.txt", 5),
        ("verified", "memory:/docs", "memory:/out/docs", 8),
    ]
    assert _info_records(caplog)[-1].__dict__["outcome"] == "verified"

    caplog.clear()
    rerun = _run({"memory": source}, arguments)

    assert (rerun.exit_code, rerun.stderr) == (0, "")
    assert sorted(_summary(record) for record in _info_records(caplog)) == [
        ("skipped", "memory:/docs/a.txt", "memory:/out/docs/a.txt", 3),
        ("skipped", "memory:/docs/sub/b.txt", "memory:/out/docs/sub/b.txt", 5),
        ("verified", "memory:/docs", "memory:/out/docs", 8),
    ]
    identity = [
        record.getMessage()
        for record in caplog.records
        if record.levelno == logging.DEBUG and record.getMessage().startswith("content identity")
    ]
    assert any("SHA-256 of staged contents equal" in message for message in identity)


def test_records_never_contain_query_strings_or_tokens(
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
) -> None:
    source = _memory(monkeypatch, {"/docs/a.txt?token=secret": b"abc"})
    caplog.set_level(logging.DEBUG, logger="fss_cli")

    result = _run(
        {"memory": source},
        ["cp", "memory:/docs/a.txt?token=secret", "memory:/b.txt?token=secret#x"],
    )

    assert (result.exit_code, result.stderr) == (0, _cp_verified("memory:/b.txt?token=secret#x", "copied", 3))
    assert _info_records(caplog)
    for record in caplog.records:
        rendered = f"{record.getMessage()} {record.__dict__}"
        assert "secret" not in rendered
        assert "token" not in rendered


@pytest.mark.parametrize(
    ("value", "expected"),
    [
        ("memory:/a.txt", "memory:/a.txt"),
        ("vos:/a.txt?token=secret", "vos:/a.txt"),
        ("vos:/a.txt#fragment?token=x", "vos:/a.txt"),
        ("s3:/https://user:pw@host/key?sig=1", "s3:/https://host/key"),
        ("memory:/line\nbreak", "memory:/line\\x0abreak"),
    ],
)
def test_redaction_drops_credentials_queries_and_controls(value: str, expected: str) -> None:
    assert _redact(value) == expected


def test_no_handler_means_nothing_is_printed(
    monkeypatch: pytest.MonkeyPatch,
    capfd: pytest.CaptureFixture[str],
) -> None:
    source = _memory(monkeypatch, {"/docs/a.txt": b"abc"}, directories=("/out",))
    logger = logging.getLogger("fss_cli")
    monkeypatch.setattr(logger, "level", logging.DEBUG)
    monkeypatch.setattr(logging.getLogger(), "handlers", [])

    result = _run({"memory": source}, ["cp", "-R", "memory:/docs", "memory:/out"])

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified("memory:/out/docs/a.txt", "copied", 3),
    )
    assert capfd.readouterr() == ("", "")
