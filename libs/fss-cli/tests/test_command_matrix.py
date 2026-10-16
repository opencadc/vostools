"""Hermetic evidence independent of the vosfs integration dependency."""

import os
import socket
import time
from contextlib import AbstractAsyncContextManager
from pathlib import Path

import pytest
from fsspec.asyn import AsyncFileSystem
from fsspec.implementations.asyn_wrapper import AsyncFileSystemWrapper
from fsspec.implementations.local import LocalFileSystem
from fsspec.implementations.memory import MemoryFileSystem
from typer.testing import CliRunner

from fss_cli import App

from ._ansi import strip_ansi
from ._matrix_support import (
    _exercise_cat_profile,
    _exercise_cp_locked_profile,
    _exercise_locked_profile,
    _exercise_long_listing_profile,
    _exercise_mkdir_locked_profile,
    _exercise_mkdir_memory_over_eager_failure,
    _exercise_mkdir_p_locked_profile,
    _exercise_multi_source_cp_locked_profile,
    _exercise_recursive_rm_profile,
    _exercise_rm_directory_profile,
    _exercise_rm_force_profile,
    _exercise_rm_locked_profile,
    _exercise_rm_verbose_profile,
    _exercise_rmdir_locked_profile,
    _exercise_stat_incomplete_profile,
    _exercise_stat_locked_profile,
    _exercise_unlink_locked_profile,
    _invoke,
    _memory_source,
    _ProbedSource,
)
from ._support import _cp_verified, _source_must_not_run, _verified_uploads

_SYNC_MV_MESSAGE = "public sync mv must not be called"


def _populate_local(root: Path) -> None:
    root.mkdir()
    for name in ("notes.txt", ".hidden", "guide.md"):
        child = root / name
        child.write_text(name, encoding="utf-8")
        child.chmod(0o644)
        os.utime(child, (1_784_311_200, 1_784_311_200))


def _populate_local_with_empty(root: Path) -> None:
    _populate_local(root)
    (root / "empty").mkdir()


def test_hermetic_guard_rejects_name_resolution() -> None:
    with pytest.raises(AssertionError) as caught:
        socket.getaddrinfo("example.test", 443)

    assert str(caught.value) == ("hermetic command-matrix tests prohibit network access")


def _local_command_path(root: Path) -> str:
    return root.resolve().as_posix()


def test_adapted_local_plain_ls_profile_uses_native_temporary_storage(
    tmp_path: Path,
) -> None:
    root = tmp_path / "docs"
    _populate_local(root)
    path = _local_command_path(root)
    source = _ProbedSource(
        lambda: AsyncFileSystemWrapper(
            LocalFileSystem(skip_instance_cache=True),
            asynchronous=True,
        )
    )

    _exercise_locked_profile("local", source, path)

    assert all(isinstance(fs, AsyncFileSystemWrapper) for fs in source.filesystems)
    assert all(isinstance(fs.sync_fs, LocalFileSystem) for fs in source.filesystems)
    assert all(fs.asynchronous is True for fs in source.filesystems)
    assert len({id(fs) for fs in source.filesystems}) == 3
    assert len({id(fs.sync_fs) for fs in source.filesystems}) == 3


def test_adapted_memory_plain_ls_profile_has_isolated_state(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = _memory_source(monkeypatch)

    _exercise_locked_profile("memory", source, "/docs")

    assert all(isinstance(fs, AsyncFileSystemWrapper) for fs in source.filesystems)
    assert all(isinstance(fs.sync_fs, MemoryFileSystem) for fs in source.filesystems)
    assert all(fs.asynchronous is True for fs in source.filesystems)
    assert len({id(fs) for fs in source.filesystems}) == 3
    assert len({id(fs.sync_fs) for fs in source.filesystems}) == 3


def test_adapted_local_long_listing_profile_is_rich_and_uses_detail(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(
        "fss_cli._listing.time.localtime",
        time.gmtime,
    )
    root = tmp_path / "docs"
    _populate_local(root)
    path = _local_command_path(root)
    source = _ProbedSource(
        lambda: AsyncFileSystemWrapper(
            LocalFileSystem(skip_instance_cache=True),
            asynchronous=True,
        )
    )
    import grp
    import pwd

    owner = pwd.getpwuid(os.getuid()).pw_name
    group = grp.getgrgid(os.getgid()).gr_name

    _exercise_long_listing_profile(
        "local",
        source,
        path,
        exact_directory=(
            f"-rw-r--r--  1  {owner}  {group}  8  Jul 17 18:00  guide.md\n"
            f"-rw-r--r--  1  {owner}  {group}  9  Jul 17 18:00  notes.txt\n"
        ),
        human_directory=(
            f"-rw-r--r--  1  {owner}  {group}  8B  Jul 17 18:00  guide.md\n"
            f"-rw-r--r--  1  {owner}  {group}  9B  Jul 17 18:00  notes.txt\n"
        ),
    )

    assert all(isinstance(fs, AsyncFileSystemWrapper) for fs in source.filesystems)
    assert all(isinstance(fs.sync_fs, LocalFileSystem) for fs in source.filesystems)


def test_adapted_memory_long_listing_profile_is_sparse_and_uses_detail(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = _memory_source(monkeypatch)

    _exercise_long_listing_profile(
        "memory",
        source,
        "/docs",
        exact_directory=("-?????????  -  -  -  8  -  guide.md\n-?????????  -  -  -  9  -  notes.txt\n"),
        human_directory=("-?????????  -  -  -  8B  -  guide.md\n-?????????  -  -  -  9B  -  notes.txt\n"),
    )

    assert all(isinstance(fs, AsyncFileSystemWrapper) for fs in source.filesystems)
    assert all(isinstance(fs.sync_fs, MemoryFileSystem) for fs in source.filesystems)


def test_adapted_local_base_mkdir_profile_uses_native_temporary_storage(
    tmp_path: Path,
) -> None:
    root = tmp_path / "docs"
    _populate_local(root)
    path = _local_command_path(root)
    source = _ProbedSource(
        lambda: AsyncFileSystemWrapper(
            LocalFileSystem(skip_instance_cache=True),
            asynchronous=True,
        )
    )

    _exercise_mkdir_locked_profile("local", source, path)


def test_adapted_memory_base_mkdir_profile_over_eager_parent_creation(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = _memory_source(monkeypatch)

    _exercise_mkdir_memory_over_eager_failure("memory", source, "/docs")


def test_mkdir_m_option_rejection_is_source_free() -> None:
    source_calls = 0

    def source_must_not_run() -> AbstractAsyncContextManager[AsyncFileSystem]:
        nonlocal source_calls
        source_calls += 1
        raise AssertionError

    result = _invoke(
        App({"memory": source_must_not_run}),
        "mkdir",
        ["-m", "755", "memory:/docs/new"],
    )

    assert (result.exit_code, result.stdout) == (2, "")
    assert "No such option" in result.stderr
    assert "-m" in result.stderr
    assert source_calls == 0


def test_mkdir_pm_option_rejection_is_source_free() -> None:
    source_calls = 0

    def source_must_not_run() -> AbstractAsyncContextManager[AsyncFileSystem]:
        nonlocal source_calls
        source_calls += 1
        raise AssertionError

    result = _invoke(
        App({"memory": source_must_not_run}),
        "mkdir",
        ["-pm", "memory:/docs/new"],
    )

    assert (result.exit_code, result.stdout) == (2, "")
    assert "No such option" in result.stderr
    assert "-m" in result.stderr
    assert source_calls == 0


def test_mkdir_parents_option_rejection_is_source_free() -> None:
    source_calls = 0

    def source_must_not_run() -> AbstractAsyncContextManager[AsyncFileSystem]:
        nonlocal source_calls
        source_calls += 1
        raise AssertionError

    result = _invoke(
        App({"memory": source_must_not_run}),
        "mkdir",
        ["--parents", "memory:/docs/new"],
    )

    assert (result.exit_code, result.stdout) == (2, "")
    assert "No such option" in result.stderr
    assert "parents" in result.stderr
    assert source_calls == 0


def test_mkdir_p_after_operand_is_accepted_by_typer(tmp_path: Path) -> None:
    source = _ProbedSource(
        lambda: AsyncFileSystemWrapper(
            LocalFileSystem(skip_instance_cache=True),
            asynchronous=True,
        )
    )

    result = _invoke(
        App({"local": source}),
        "mkdir",
        [
            f"local:{tmp_path}/a",
            "-p",
            f"local:{tmp_path}/b",
        ],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert (tmp_path / "a").is_dir()
    assert (tmp_path / "b").is_dir()


def test_adapted_local_mkdir_p_profile_uses_native_temporary_storage(
    tmp_path: Path,
) -> None:
    root = tmp_path / "docs"
    _populate_local_with_empty(root)
    path = _local_command_path(root)
    source = _ProbedSource(
        lambda: AsyncFileSystemWrapper(
            LocalFileSystem(skip_instance_cache=True),
            asynchronous=True,
        )
    )

    _exercise_mkdir_p_locked_profile("local", source, path)


def test_adapted_memory_mkdir_p_profile_has_isolated_state(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = _memory_source(monkeypatch, directories=("/docs", "/docs/empty"))

    _exercise_mkdir_p_locked_profile("memory", source, "/docs")


def test_typer_rejects_ls_long_option_spelling_without_source_work() -> None:
    source_calls = 0

    def source_must_not_run() -> AbstractAsyncContextManager[AsyncFileSystem]:
        nonlocal source_calls
        source_calls += 1
        raise AssertionError

    result = _invoke(
        App({"memory": source_must_not_run}),
        "ls",
        ["--long", "memory:/docs"],
    )

    assert (result.exit_code, result.stdout) == (2, "")
    diagnostic = strip_ansi(result.stderr)
    assert "No such option: --long" in diagnostic
    assert source_calls == 0


def test_adapted_local_plain_cat_profile(tmp_path: Path) -> None:
    root = tmp_path / "docs"
    root.mkdir()
    payload = bytes(range(256)) + b"\nno-final"
    target = root / "blob.bin"
    target.write_bytes(payload)
    path = _local_command_path(target)
    source = _ProbedSource(
        lambda: AsyncFileSystemWrapper(
            LocalFileSystem(skip_instance_cache=True),
            asynchronous=True,
        )
    )

    _exercise_cat_profile("local", source, path, payload=payload)

    assert all(isinstance(fs, AsyncFileSystemWrapper) for fs in source.filesystems)
    assert all(isinstance(fs.sync_fs, LocalFileSystem) for fs in source.filesystems)
    assert all(fs.asynchronous is True for fs in source.filesystems)


def test_adapted_memory_plain_cat_profile(monkeypatch: pytest.MonkeyPatch) -> None:
    payload = b"\xff\xfe\0memory-cat"
    source = _memory_source(monkeypatch, {"/docs/blob.bin": payload}, directories=())

    _exercise_cat_profile("memory", source, "/docs/blob.bin", payload=payload)

    assert all(isinstance(fs, AsyncFileSystemWrapper) for fs in source.filesystems)
    assert all(isinstance(fs.sync_fs, MemoryFileSystem) for fs in source.filesystems)
    assert all(fs.asynchronous is True for fs in source.filesystems)


def test_cat_u_rejection_is_source_free() -> None:
    source = _source_must_not_run
    app = App({"memory": source})
    result = _invoke(app, "cat", ["-u", "memory:/file"])

    assert (result.exit_code, result.stdout) == (2, "")
    assert "No such option" in result.stderr
    assert "-u" in result.stderr


def test_adapted_memory_cat_stdin_dash_mixed_order(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = _memory_source(
        monkeypatch,
        {"/docs/left.bin": b"L", "/docs/right.bin": b"R"},
        directories=(),
    )
    app = App({"memory": source})
    result = CliRunner().invoke(
        app.typer_app,
        ["cat", "memory:/docs/left.bin", "-", "memory:/docs/right.bin"],
        input=b"S",
    )

    assert (result.exit_code, result.stdout_bytes, result.stderr) == (0, b"LSR", "")
    assert [event.stage for event in source.lifecycle] == ["factory", "enter", "exit"]
    assert [call.operation for call in source.calls] == [
        "info",
        "get_file",
        "info",
        "get_file",
    ]


def test_adapted_local_base_rmdir_profile_uses_native_temporary_storage(
    tmp_path: Path,
) -> None:
    root = tmp_path / "docs"
    _populate_local_with_empty(root)
    path = _local_command_path(root)
    source = _ProbedSource(
        lambda: AsyncFileSystemWrapper(
            LocalFileSystem(skip_instance_cache=True),
            asynchronous=True,
        )
    )

    _exercise_rmdir_locked_profile("local", source, path)


def test_adapted_memory_base_rmdir_profile_has_isolated_state(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = _memory_source(monkeypatch, directories=("/docs", "/docs/empty"))

    _exercise_rmdir_locked_profile("memory", source, "/docs")


def test_rmdir_option_rejection_is_source_free() -> None:
    source_calls = 0

    def source_must_not_run() -> AbstractAsyncContextManager[AsyncFileSystem]:
        nonlocal source_calls
        source_calls += 1
        raise AssertionError

    result = _invoke(
        App({"memory": source_must_not_run}),
        "rmdir",
        ["-p", "memory:/docs/empty"],
    )

    assert (result.exit_code, result.stdout) == (2, "")
    assert "No such option" in result.stderr
    assert "-p" in result.stderr
    assert source_calls == 0


def test_adapted_local_unlink_profile_uses_native_temporary_storage(
    tmp_path: Path,
) -> None:
    root = tmp_path / "docs"
    _populate_local(root)
    path = _local_command_path(root)
    source = _ProbedSource(
        lambda: AsyncFileSystemWrapper(
            LocalFileSystem(skip_instance_cache=True),
            asynchronous=True,
        )
    )

    _exercise_unlink_locked_profile("local", source, path)


def test_adapted_memory_unlink_profile_has_isolated_state(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = _memory_source(monkeypatch)

    _exercise_unlink_locked_profile("memory", source, "/docs")


def test_unlink_option_rejection_is_source_free() -> None:
    source_calls = 0

    def source_must_not_run() -> AbstractAsyncContextManager[AsyncFileSystem]:
        nonlocal source_calls
        source_calls += 1
        raise AssertionError

    result = _invoke(
        App({"memory": source_must_not_run}),
        "unlink",
        ["-f", "memory:/docs/notes.txt"],
    )

    assert (result.exit_code, result.stdout) == (2, "")
    assert "No such option" in result.stderr
    assert "-f" in result.stderr
    assert source_calls == 0


def test_adapted_local_base_rm_profile_uses_native_temporary_storage(
    tmp_path: Path,
) -> None:
    root = tmp_path / "docs"
    _populate_local_with_empty(root)
    path = _local_command_path(root)
    source = _ProbedSource(
        lambda: AsyncFileSystemWrapper(
            LocalFileSystem(skip_instance_cache=True),
            asynchronous=True,
        )
    )

    _exercise_rm_locked_profile("local", source, path)
    (root / "notes.txt").write_text("notes.txt", encoding="utf-8")
    _exercise_rm_directory_profile("local", source, path)
    _exercise_rm_force_profile("local", source, path)
    (Path(path) / "notes.txt").write_text("notes.txt", encoding="utf-8")
    _exercise_rm_verbose_profile("local", source, path)


def test_adapted_local_cp_profile_uses_native_temporary_storage(
    tmp_path: Path,
) -> None:
    root = tmp_path / "docs"
    _populate_local(root)
    (root / "target").mkdir()
    path = _local_command_path(root)
    source = _ProbedSource(
        lambda: AsyncFileSystemWrapper(
            LocalFileSystem(skip_instance_cache=True),
            asynchronous=True,
        )
    )

    _exercise_cp_locked_profile("local", source, path)


def test_adapted_local_multi_source_cp_profile_uses_native_temporary_storage(
    tmp_path: Path,
) -> None:
    root = tmp_path / "docs"
    _populate_local(root)
    (root / "target").mkdir()
    path = _local_command_path(root)
    source = _ProbedSource(
        lambda: AsyncFileSystemWrapper(
            LocalFileSystem(skip_instance_cache=True),
            asynchronous=True,
        )
    )

    _exercise_multi_source_cp_locked_profile("local", source, path)


def test_adapted_memory_base_rm_profile_has_isolated_state(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = _memory_source(monkeypatch, directories=("/docs", "/docs/empty"))

    _exercise_rm_locked_profile("memory", source, "/docs")
    _exercise_rm_directory_profile("memory", source, "/docs")
    _exercise_rm_force_profile("memory", source, "/docs")
    _exercise_rm_verbose_profile("memory", source, "/docs")


def test_adapted_memory_cp_profile_has_isolated_state(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = _memory_source(monkeypatch, directories=("/docs", "/docs/target"))

    _exercise_cp_locked_profile("memory", source, "/docs")


def test_adapted_memory_multi_source_cp_profile_has_isolated_state(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = _memory_source(monkeypatch, directories=("/docs", "/docs/target"))

    _exercise_multi_source_cp_locked_profile("memory", source, "/docs")


@pytest.mark.parametrize("direction", ["local-to-memory", "memory-to-local"])
def test_cross_source_cp_between_adapted_local_and_memory(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    direction: str,
) -> None:
    monkeypatch.setattr(MemoryFileSystem, "store", {})
    monkeypatch.setattr(MemoryFileSystem, "pseudo_dirs", [""])
    monkeypatch.setattr(MemoryFileSystem, "_cache", {})
    payload = b"\0\xff cross-source"
    local_root = tmp_path / "local"
    local_root.mkdir()
    memory = MemoryFileSystem()
    memory.makedirs("/docs")

    if direction == "local-to-memory":
        (local_root / "source.bin").write_bytes(payload)
        source_operand = f"local:{_local_command_path(local_root / 'source.bin')}"
        destination_operand = "memory:/docs/copy.bin"
    else:
        memory.pipe_file("/docs/source.bin", payload)
        source_operand = "memory:/docs/source.bin"
        destination_path = local_root / "copy.bin"
        destination_operand = f"local:{_local_command_path(destination_path)}"

    app = App(
        {
            "local": _ProbedSource(
                lambda: AsyncFileSystemWrapper(LocalFileSystem(skip_instance_cache=True), asynchronous=True)
            ),
            "memory": _ProbedSource(lambda: AsyncFileSystemWrapper(memory, asynchronous=True)),
        }
    )

    result = _invoke(app, "cp", [source_operand, destination_operand])

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "",
        _cp_verified(destination_operand, "uploaded", len(payload)),
    )
    if direction == "local-to-memory":
        assert memory.cat("/docs/copy.bin") == payload
    else:
        assert (local_root / "copy.bin").read_bytes() == payload


def test_adapted_local_recursive_cp_profile_uses_native_temporary_storage(
    tmp_path: Path,
) -> None:
    source_root = tmp_path / "source"
    destination_parent = tmp_path / "out"
    source_root.mkdir()
    destination_parent.mkdir()
    (source_root / "empty").mkdir()
    (source_root / "nested").mkdir()
    (source_root / "nested" / "notes.txt").write_bytes(b"notes")
    source = _ProbedSource(
        lambda: AsyncFileSystemWrapper(
            LocalFileSystem(skip_instance_cache=True),
            asynchronous=True,
        )
    )

    result = _invoke(
        App({"local": source}),
        "cp",
        [
            "-R",
            f"local:{_local_command_path(source_root)}",
            f"local:{_local_command_path(destination_parent / 'copy')}",
        ],
    )

    assert (result.exit_code, result.stdout) == (0, "")
    copied = _local_command_path(destination_parent / "copy" / "nested" / "notes.txt")
    assert _verified_uploads(result.stderr) == {f"local:{copied}": 5}
    assert (destination_parent / "copy" / "nested" / "notes.txt").read_bytes() == (b"notes")
    assert (destination_parent / "copy" / "empty").is_dir()
    assert all(isinstance(fs, AsyncFileSystemWrapper) for fs in source.filesystems)
    assert all(isinstance(fs.sync_fs, LocalFileSystem) for fs in source.filesystems)


@pytest.mark.parametrize("entry_kind", ["symlink", "fifo"])
def test_adapted_local_recursive_cp_rejects_real_link_and_special_entries(
    tmp_path: Path,
    entry_kind: str,
) -> None:
    source_root = tmp_path / "source"
    destination_parent = tmp_path / "out"
    source_root.mkdir()
    destination_parent.mkdir()
    if entry_kind == "symlink":
        outside = tmp_path / "outside.txt"
        outside.write_bytes(b"outside")
        (source_root / "entry").symlink_to(outside)
    else:
        os.mkfifo(source_root / "entry")
    source = _ProbedSource(
        lambda: AsyncFileSystemWrapper(
            LocalFileSystem(skip_instance_cache=True),
            asynchronous=True,
        )
    )

    result = _invoke(
        App({"local": source}),
        "cp",
        [
            "-R",
            f"local:{_local_command_path(source_root)}",
            f"local:{_local_command_path(destination_parent / 'copy')}",
        ],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        f"cp: local:{_local_command_path(source_root)}: unsupported entry type\n",
    )
    assert not (destination_parent / "copy").exists()


def test_adapted_memory_recursive_cp_profile_has_isolated_state(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(MemoryFileSystem, "store", {})
    monkeypatch.setattr(MemoryFileSystem, "pseudo_dirs", [""])
    monkeypatch.setattr(MemoryFileSystem, "_cache", {})
    memory = MemoryFileSystem()
    memory.makedirs("/source/empty")
    memory.makedirs("/source/nested")
    memory.pipe_file("/source/nested/notes.txt", b"notes")
    source = _ProbedSource(lambda: AsyncFileSystemWrapper(memory, asynchronous=True))

    result = _invoke(
        App({"memory": source}),
        "cp",
        ["-r", "memory:/source", "memory:/copy"],
    )

    assert (result.exit_code, result.stdout) == (0, "")
    assert _verified_uploads(result.stderr) == {"memory:/copy/nested/notes.txt": 5}
    assert memory.cat("/copy/nested/notes.txt") == b"notes"
    assert memory.isdir("/copy/empty")
    assert all(isinstance(fs, AsyncFileSystemWrapper) for fs in source.filesystems)
    assert all(isinstance(fs.sync_fs, MemoryFileSystem) for fs in source.filesystems)


@pytest.mark.parametrize(
    ("source_form", "destination_form"),
    [
        ("local", "local"),
        ("local", "memory"),
        ("memory", "local"),
        ("memory", "memory"),
    ],
)
def test_recursive_cp_between_distinct_adapted_source_names(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    source_form: str,
    destination_form: str,
) -> None:
    monkeypatch.setattr(MemoryFileSystem, "store", {})
    monkeypatch.setattr(MemoryFileSystem, "pseudo_dirs", [""])
    monkeypatch.setattr(MemoryFileSystem, "_cache", {})
    memory = MemoryFileSystem()
    local_source = tmp_path / "source"
    local_destination = tmp_path / "destination"
    local_source.mkdir()
    local_destination.mkdir()
    (local_source / "empty").mkdir()
    (local_source / "notes.txt").write_bytes(b"notes")
    memory.makedirs("/source/empty")
    memory.pipe_file("/source/notes.txt", b"notes")
    memory.makedirs("/destination")

    def source_factory(form: str) -> AsyncFileSystemWrapper:
        filesystem = LocalFileSystem(skip_instance_cache=True) if form == "local" else memory
        return AsyncFileSystemWrapper(filesystem, asynchronous=True)

    source = _ProbedSource(lambda: source_factory(source_form))
    destination = _ProbedSource(lambda: source_factory(destination_form))
    source_path = _local_command_path(local_source) if source_form == "local" else "/source"
    destination_path = (
        _local_command_path(local_destination / "copy") if destination_form == "local" else "/destination/copy"
    )

    result = _invoke(
        App({"source": source, "destination": destination}),
        "cp",
        ["-R", f"source:{source_path}", f"destination:{destination_path}"],
    )

    assert (result.exit_code, result.stdout) == (0, "")
    assert _verified_uploads(result.stderr) == {f"destination:{destination_path}/notes.txt": 5}
    if destination_form == "local":
        assert (local_destination / "copy" / "notes.txt").read_bytes() == b"notes"
        assert (local_destination / "copy" / "empty").is_dir()
    else:
        assert memory.cat("/destination/copy/notes.txt") == b"notes"
        assert memory.isdir("/destination/copy/empty")
    assert source.filesystems[0] is not destination.filesystems[0]


def test_cp_unprofiled_option_rejection_is_source_free() -> None:
    source_calls = 0

    def source_must_not_run() -> AbstractAsyncContextManager[AsyncFileSystem]:
        nonlocal source_calls
        source_calls += 1
        raise AssertionError

    result = CliRunner().invoke(
        App({"memory": source_must_not_run}).typer_app,
        ["cp", "-L", "memory:/docs/notes.txt", "memory:/docs/copy.txt"],
        env={"FORCE_COLOR": "1"},
    )

    assert (result.exit_code, result.stdout_bytes) == (2, b"")
    assert "No such option: -L" in strip_ansi(result.stderr)
    assert source_calls == 0


def test_rm_force_profile_option_rejection_is_source_free() -> None:
    source_calls = 0

    def source_must_not_run() -> AbstractAsyncContextManager[AsyncFileSystem]:
        nonlocal source_calls
        source_calls += 1
        raise AssertionError

    result = _invoke(
        App({"memory": source_must_not_run}),
        "rm",
        ["-f", "-i", "memory:/docs/notes.txt"],
    )

    assert (result.exit_code, result.stdout) == (2, "")
    assert "No such option: -i" in strip_ansi(result.stderr)
    assert source_calls == 0


def test_rm_verbose_profile_option_rejection_is_source_free() -> None:
    source_calls = 0

    def source_must_not_run() -> AbstractAsyncContextManager[AsyncFileSystem]:
        nonlocal source_calls
        source_calls += 1
        raise AssertionError

    result = _invoke(
        App({"memory": source_must_not_run}),
        "rm",
        ["-v", "-f", "memory:/docs/notes.txt"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        2,
        "",
        "rm: -f: cannot combine with -v\n",
    )
    assert source_calls == 0


def test_adapted_local_mv_remains_unverified_without_exact_operation(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    root = tmp_path / "docs"
    root.mkdir()
    source_path = root / "notes.txt"
    target_path = root / "moved.txt"
    source_path.write_bytes(b"payload")
    source = _ProbedSource(
        lambda: AsyncFileSystemWrapper(
            LocalFileSystem(skip_instance_cache=True),
            asynchronous=True,
        )
    )

    def reject_sync_mv(*_args: object, **_kwargs: object) -> None:
        raise AssertionError(_SYNC_MV_MESSAGE)

    monkeypatch.setattr(AsyncFileSystemWrapper, "mv", reject_sync_mv)
    result = _invoke(
        App({"local": source}),
        "mv",
        [f"local:{source_path}", f"local:{target_path}"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        f"mv: local:{target_path}: unsupported operation\n",
    )
    assert "_mv" not in type(source.filesystems[0]).__dict__
    assert source_path.read_bytes() == b"payload"
    assert not target_path.exists()
    assert not any(call.operation == "get_file" for call in source.calls)


def test_adapted_memory_mv_remains_unverified_without_exact_operation(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(MemoryFileSystem, "store", {})
    monkeypatch.setattr(MemoryFileSystem, "pseudo_dirs", [""])
    monkeypatch.setattr(MemoryFileSystem, "_cache", {})

    def make_filesystem() -> AsyncFileSystemWrapper:
        MemoryFileSystem.clear_instance_cache()
        filesystem = MemoryFileSystem()
        filesystem.pipe_file("/docs/notes.txt", b"payload")
        return AsyncFileSystemWrapper(filesystem, asynchronous=True)

    source = _ProbedSource(make_filesystem)

    def reject_sync_mv(*_args: object, **_kwargs: object) -> None:
        raise AssertionError(_SYNC_MV_MESSAGE)

    monkeypatch.setattr(AsyncFileSystemWrapper, "mv", reject_sync_mv)
    result = _invoke(
        App({"memory": source}),
        "mv",
        ["memory:/docs/notes.txt", "memory:/docs/moved.txt"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "mv: memory:/docs/moved.txt: unsupported operation\n",
    )
    filesystem = source.filesystems[0]
    assert "_mv" not in type(filesystem).__dict__
    assert filesystem.sync_fs.cat("/docs/notes.txt") == b"payload"
    assert not filesystem.sync_fs.exists("/docs/moved.txt")
    assert not any(call.operation == "get_file" for call in source.calls)


def test_adapted_local_multi_file_mv_remains_unverified_without_exact_operation(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    root = tmp_path / "docs"
    root.mkdir()
    notes_path = root / "notes.txt"
    guide_path = root / "guide.md"
    target_dir = root / "target"
    notes_path.write_bytes(b"notes")
    guide_path.write_bytes(b"guide")
    target_dir.mkdir()
    source = _ProbedSource(
        lambda: AsyncFileSystemWrapper(
            LocalFileSystem(skip_instance_cache=True),
            asynchronous=True,
        )
    )

    def reject_sync_mv(*_args: object, **_kwargs: object) -> None:
        raise AssertionError(_SYNC_MV_MESSAGE)

    monkeypatch.setattr(AsyncFileSystemWrapper, "mv", reject_sync_mv)
    result = _invoke(
        App({"local": source}),
        "mv",
        [f"local:{notes_path}", f"local:{guide_path}", f"local:{target_dir}"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        f"mv: local:{target_dir}: unsupported operation\n",
    )
    assert "_mv" not in type(source.filesystems[0]).__dict__
    assert notes_path.read_bytes() == b"notes"
    assert guide_path.read_bytes() == b"guide"
    assert not (target_dir / "notes.txt").exists()
    assert not (target_dir / "guide.md").exists()
    assert not any(call.operation == "get_file" for call in source.calls)


def test_adapted_memory_multi_file_mv_remains_unverified_without_exact_operation(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(MemoryFileSystem, "store", {})
    monkeypatch.setattr(MemoryFileSystem, "pseudo_dirs", [""])
    monkeypatch.setattr(MemoryFileSystem, "_cache", {})

    def make_filesystem() -> AsyncFileSystemWrapper:
        MemoryFileSystem.clear_instance_cache()
        filesystem = MemoryFileSystem()
        filesystem.makedirs("/docs/target")
        filesystem.pipe_file("/docs/notes.txt", b"notes")
        filesystem.pipe_file("/docs/guide.md", b"guide")
        return AsyncFileSystemWrapper(filesystem, asynchronous=True)

    source = _ProbedSource(make_filesystem)

    def reject_sync_mv(*_args: object, **_kwargs: object) -> None:
        raise AssertionError(_SYNC_MV_MESSAGE)

    monkeypatch.setattr(AsyncFileSystemWrapper, "mv", reject_sync_mv)
    result = _invoke(
        App({"memory": source}),
        "mv",
        [
            "memory:/docs/notes.txt",
            "memory:/docs/guide.md",
            "memory:/docs/target",
        ],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "mv: memory:/docs/target: unsupported operation\n",
    )
    filesystem = source.filesystems[0]
    assert "_mv" not in type(filesystem).__dict__
    assert filesystem.sync_fs.cat("/docs/notes.txt") == b"notes"
    assert filesystem.sync_fs.cat("/docs/guide.md") == b"guide"
    assert not filesystem.sync_fs.exists("/docs/target/notes.txt")
    assert not filesystem.sync_fs.exists("/docs/target/guide.md")
    assert not any(call.operation == "get_file" for call in source.calls)


def test_adapted_memory_recursive_rm_profile_has_isolated_state(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(MemoryFileSystem, "store", {})
    monkeypatch.setattr(MemoryFileSystem, "pseudo_dirs", [""])
    monkeypatch.setattr(MemoryFileSystem, "_cache", {})

    def make_filesystem() -> AsyncFileSystemWrapper:
        MemoryFileSystem.store.clear()
        MemoryFileSystem.pseudo_dirs[:] = [""]
        MemoryFileSystem.clear_instance_cache()
        filesystem = MemoryFileSystem()
        filesystem.makedirs("/docs/nested/empty")
        filesystem.pipe_file("/docs/nested/a.txt", b"a")
        filesystem.pipe_file("/docs/z.txt", b"z")
        wrapped = AsyncFileSystemWrapper(filesystem, asynchronous=True)

        def forbid_public_facade(*args: object, **kwargs: object) -> None:
            del args, kwargs
            message = "recursive rm must not call a public sync facade"
            raise AssertionError(message)

        for name in ("info", "ls", "rm", "rm_file", "rmdir", "find", "walk"):
            setattr(wrapped, name, forbid_public_facade)
        return wrapped

    source = _ProbedSource(make_filesystem)

    _exercise_recursive_rm_profile("memory", source, "/docs")

    filesystem = source.filesystems[0]
    assert isinstance(filesystem, AsyncFileSystemWrapper)
    assert isinstance(filesystem.sync_fs, MemoryFileSystem)
    assert filesystem.asynchronous is True
    assert not filesystem.sync_fs.exists("/docs")


def test_adapted_local_stat_profile_uses_native_temporary_storage(
    tmp_path: Path,
) -> None:
    import os
    import time

    previous = os.environ.get("TZ")
    os.environ["TZ"] = "UTC"
    time.tzset()
    try:
        root = tmp_path / "docs"
        root.mkdir()
        file_path = root / "notes.txt"
        file_path.write_text("abc", encoding="utf-8")
        directory_path = root / "subdir"
        directory_path.mkdir()
        source = _ProbedSource(
            lambda: AsyncFileSystemWrapper(
                LocalFileSystem(skip_instance_cache=True),
                asynchronous=True,
            )
        )

        _exercise_stat_locked_profile(
            "local",
            source,
            _local_command_path(file_path),
            _local_command_path(directory_path),
        )

        assert all(isinstance(fs, AsyncFileSystemWrapper) for fs in source.filesystems)
        assert all(isinstance(fs.sync_fs, LocalFileSystem) for fs in source.filesystems)
        assert all(fs.asynchronous is True for fs in source.filesystems)
    finally:
        if previous is None:
            os.environ.pop("TZ", None)
        else:
            os.environ["TZ"] = previous
        time.tzset()


def test_adapted_memory_stat_profile_fails_closed_on_incomplete_info(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = _memory_source(monkeypatch, {"/docs/notes.txt": b"abc"}, directories=())

    _exercise_stat_incomplete_profile("memory", source, "/docs/notes.txt")

    assert all(isinstance(fs, AsyncFileSystemWrapper) for fs in source.filesystems)
    assert all(isinstance(fs.sync_fs, MemoryFileSystem) for fs in source.filesystems)


def test_stat_option_rejection_is_source_free() -> None:
    source_calls = 0

    def source_must_not_run() -> AbstractAsyncContextManager[AsyncFileSystem]:
        nonlocal source_calls
        source_calls += 1
        raise AssertionError

    result = _invoke(
        App({"memory": source_must_not_run}),
        "stat",
        ["-l", "memory:/docs/notes.txt"],
    )

    assert (result.exit_code, result.stdout) == (2, "")
    diagnostic = result.stderr
    assert "No such option" in diagnostic
    assert "-l" in diagnostic
    assert source_calls == 0
