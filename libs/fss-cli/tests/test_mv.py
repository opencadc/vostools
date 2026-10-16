"""Same-source file ``mv`` tests through public embedded-command seam."""

from __future__ import annotations

import asyncio
import re

import pytest
from typer.testing import CliRunner

from fss_cli import App

from ._support import (
    _RecordingContext,
    _RecordingFileSystem,
    _RecordingSource,
    _source_must_not_run,
)

_MOVE_FAILED = "move failed"
_ANSI_ESCAPE = re.compile(r"\x1b\[[0-?]*[ -/]*[@-~]")


def _source(
    *,
    contents: dict[str, bytes] | None = None,
    directories: set[str] | None = None,
    **kwargs: object,
) -> _RecordingSource:
    return _RecordingSource(
        [],
        file_contents=contents or {"/docs/notes.txt": b"payload"},
        directories=directories or {"/", "/docs"},
        **kwargs,  # type: ignore[arg-type]
    )


def _invoke(source: _RecordingSource, *arguments: str):
    return CliRunner().invoke(App({"memory": source}).typer_app, ["mv", *arguments])


def _plain(text: str) -> str:
    return " ".join(_ANSI_ESCAPE.sub("", text).replace("│", " ").split())


def test_mv_help_comes_from_typed_callback() -> None:
    result = CliRunner().invoke(App({"memory": _source_must_not_run}).typer_app, ["mv", "--help"])
    help_text = _plain(result.stdout)

    assert result.exit_code == 0
    assert "Usage:" in help_text
    assert "name:/path" in help_text
    assert "Move or rename files" in help_text
    assert "source files followed by one destination" in help_text
    assert result.stderr == ""


def test_mv_moves_one_file_without_stdout() -> None:
    source = _source()

    result = _invoke(source, "memory:/docs/notes.txt", "memory:/docs/moved.txt")

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert source.file_contents == {"/docs/moved.txt": b"payload"}
    assert not source.get_file_paths


def test_mv_accepts_matching_metadata_tokens_and_proves_source_absence() -> None:
    source = _source(
        info_by_path={
            "/docs/notes.txt": {
                "type": "file",
                "size": 7,
                "ETag": "etag-token",
                "content-md5": b"content-token",
            },
            "/docs/moved.txt": {
                "type": "file",
                "size": 7,
                "etag": "etag-token",
                "content_md5": b"content-token",
            },
        }
    )

    result = _invoke(source, "memory:/docs/notes.txt", "memory:/docs/moved.txt")

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert not source.get_file_paths
    mutation_index = next(index for index, event in enumerate(source.events) if event[0] == "mv")
    assert [event[:3] for event in source.events[mutation_index + 1 :] if event[0] == "info"] == [
        ("info", 1, "/docs/moved.txt"),
        ("info", 1, "/docs/notes.txt"),
    ]


def test_mv_rejects_mismatched_metadata_token() -> None:
    source = _source(
        info_by_path={
            "/docs/notes.txt": {
                "type": "file",
                "size": 7,
                "checksum": "source-token",
            },
            "/docs/moved.txt": {
                "type": "file",
                "size": 7,
                "checksum": "destination-token",
            },
        }
    )

    result = _invoke(source, "memory:/docs/notes.txt", "memory:/docs/moved.txt")

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "mv: memory:/docs/moved.txt: verification failure; destination residue may remain\n",
    )
    assert not source.get_file_paths


def test_mv_freezes_source_metadata_before_mutation() -> None:
    source_info: dict[str, object] = {
        "type": "file",
        "size": 7,
        "checksum": "source-token",
    }
    source = _source(
        info_by_path={
            "/docs/notes.txt": source_info,
            "/docs/moved.txt": {
                "type": "file",
                "size": 7,
                "checksum": "destination-token",
            },
        }
    )

    def move_and_clear_source_metadata(path1: str, path2: str) -> None:
        filesystem = source.contexts[0].filesystem
        filesystem._file_contents[path2] = filesystem._file_contents.pop(path1)
        source.file_contents[path2] = source.file_contents.pop(path1)
        filesystem._removed_paths.add(path1)
        source_info.clear()

    source.mv_hook = move_and_clear_source_metadata
    result = _invoke(source, "memory:/docs/notes.txt", "memory:/docs/moved.txt")

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "mv: memory:/docs/moved.txt: verification failure; destination residue may remain\n",
    )
    assert source.file_contents == {"/docs/moved.txt": b"payload"}
    assert not source.get_file_paths


def test_mv_calls_inherited_mv_file_override() -> None:
    class MoveFileFileSystem(_RecordingFileSystem):
        async def _mv_file(self, path1: str, path2: str, **kwargs: object) -> None:
            await _RecordingFileSystem._mv(self, path1, path2, **kwargs)

    class InheritedMoveFileFileSystem(MoveFileFileSystem):
        pass

    class InheritedMoveFileContext(_RecordingContext):
        def __init__(self, source: _RecordingSource, source_id: int) -> None:
            super().__init__(source, source_id)
            self.filesystem = InheritedMoveFileFileSystem(source, source_id)

    class InheritedMoveFileSource(_RecordingSource):
        def __call__(self) -> InheritedMoveFileContext:
            self.call_count += 1
            self.events.append(("factory", self.call_count))
            context = InheritedMoveFileContext(self, self.call_count)
            self.contexts.append(context)
            return context

    source = InheritedMoveFileSource(
        [],
        file_contents={"/docs/notes.txt": b"payload"},
        directories={"/", "/docs"},
    )

    result = _invoke(source, "memory:/docs/notes.txt", "memory:/docs/moved.txt")

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert source.file_contents == {"/docs/moved.txt": b"payload"}
    assert [event[0] for event in source.events if event[0] == "mv"] == ["mv"]


def test_mv_rejects_inherited_async_move_operation() -> None:
    class InheritedMoveFileSystem(_RecordingFileSystem):
        pass

    class InheritedMoveContext(_RecordingContext):
        def __init__(self, source: _RecordingSource, source_id: int) -> None:
            super().__init__(source, source_id)
            self.filesystem = InheritedMoveFileSystem(source, source_id)

    class InheritedMoveSource(_RecordingSource):
        def __call__(self) -> InheritedMoveContext:
            self.call_count += 1
            self.events.append(("factory", self.call_count))
            context = InheritedMoveContext(self, self.call_count)
            self.contexts.append(context)
            return context

    source = InheritedMoveSource(
        [],
        file_contents={"/docs/notes.txt": b"payload"},
        directories={"/", "/docs"},
    )

    result = _invoke(source, "memory:/docs/notes.txt", "memory:/docs/moved.txt")

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "mv: memory:/docs/moved.txt: unsupported operation\n",
    )
    assert source.file_contents == {"/docs/notes.txt": b"payload"}
    assert not [event for event in source.events if event[0] == "mv"]
    assert not [event for event in source.events if event[0] == "get_file"]


def test_mv_resolves_directory_target_and_replaces_file() -> None:
    source = _source(
        contents={"/docs/notes.txt": b"new", "/docs/out/notes.txt": b"old"},
        directories={"/", "/docs", "/docs/out"},
    )

    result = _invoke(source, "memory:/docs/notes.txt", "memory:/docs/out")

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert source.file_contents == {"/docs/out/notes.txt": b"new"}


def test_mv_passes_literal_source_destination_and_parent_paths_to_backend() -> None:
    source = _source(
        contents={"/docs//./notes.txt": b"payload"},
        directories={"/", "/docs//.", "/out//."},
    )

    result = _invoke(
        source,
        "memory:/docs//./notes.txt",
        "memory:/out//./moved.txt",
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert source.file_contents == {"/out//./moved.txt": b"payload"}
    assert [(event[2], event[3]) for event in source.events if event[0] == "mv"] == [
        ("/docs//./notes.txt", "/out//./moved.txt")
    ]
    assert ("info", 1, "/out//.") in [event[:3] for event in source.events if event[0] == "info"]


def test_mv_moves_multiple_files_into_existing_directory_in_argv_order() -> None:
    source = _source(
        contents={
            "/docs/one.txt": b"one",
            "/docs/two.txt": b"two",
            "/docs/out/one.txt": b"old",
        },
        directories={"/", "/docs", "/docs/out"},
    )

    result = _invoke(
        source,
        "memory:/docs/one.txt",
        "memory:/docs/two.txt",
        "memory:/docs/out",
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert source.file_contents == {
        "/docs/out/one.txt": b"one",
        "/docs/out/two.txt": b"two",
    }
    assert source.call_count == 1
    assert [(event[2], event[3]) for event in source.events if event[0] == "mv"] == [
        ("/docs/one.txt", "/docs/out/one.txt"),
        ("/docs/two.txt", "/docs/out/two.txt"),
    ]


def test_mv_multiple_files_replaces_duplicate_basenames_in_argv_order() -> None:
    source = _source(
        contents={"/left/item.txt": b"left", "/right/item.txt": b"right"},
        directories={"/", "/left", "/right", "/out"},
    )

    result = _invoke(
        source,
        "memory:/left/item.txt",
        "memory:/right/item.txt",
        "memory:/out",
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert source.file_contents == {"/out/item.txt": b"right"}


@pytest.mark.parametrize(
    ("destination", "expected"),
    [
        ("memory:/docs/missing", "mv: memory:/docs/missing: not found\n"),
        (
            "memory:/docs/existing.txt",
            "mv: memory:/docs/existing.txt: not a directory\n",
        ),
    ],
)
def test_mv_multiple_files_requires_existing_directory_destination(destination: str, expected: str) -> None:
    source = _source(
        contents={
            "/docs/one.txt": b"one",
            "/docs/two.txt": b"two",
            "/docs/existing.txt": b"x",
        },
        directories={"/", "/docs"},
        info_by_path=(
            {"/docs/missing": FileNotFoundError("/docs/missing")} if destination.endswith("/missing") else None
        ),
    )

    result = _invoke(source, "memory:/docs/one.txt", "memory:/docs/two.txt", destination)

    assert (result.exit_code, result.stdout, result.stderr) == (1, "", expected)
    assert source.file_contents["/docs/one.txt"] == b"one"
    assert source.file_contents["/docs/two.txt"] == b"two"
    assert not [event for event in source.events if event[0] == "mv"]


def test_mv_multiple_files_stops_after_failure_and_preserves_prior_move() -> None:
    source = _source(
        contents={"/docs/one.txt": b"one", "/docs/two.txt": b"two"},
        directories={"/", "/docs", "/docs/out"},
        mv_by_path={"/docs/two.txt": OSError(_MOVE_FAILED)},
    )

    result = _invoke(
        source,
        "memory:/docs/one.txt",
        "memory:/docs/two.txt",
        "memory:/docs/out",
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "mv: memory:/docs/out: uncertain mutation state; destination residue may remain\n",
    )
    assert source.file_contents == {
        "/docs/out/one.txt": b"one",
        "/docs/two.txt": b"two",
    }


def test_mv_multiple_files_same_path_noop_then_moves_remaining() -> None:
    source = _source(
        contents={"/docs/out/keep.txt": b"keep", "/docs/other.txt": b"other"},
        directories={"/", "/docs", "/docs/out"},
    )

    result = _invoke(
        source,
        "memory:/docs/out/keep.txt",
        "memory:/docs/other.txt",
        "memory:/docs/out",
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert source.file_contents == {
        "/docs/out/keep.txt": b"keep",
        "/docs/out/other.txt": b"other",
    }
    assert [(event[2], event[3]) for event in source.events if event[0] == "mv"] == [
        ("/docs/other.txt", "/docs/out/other.txt")
    ]


def test_mv_multiple_files_missing_source_preserves_prior_move() -> None:
    source = _source(
        contents={"/docs/one.txt": b"one"},
        directories={"/", "/docs", "/docs/out"},
        info_by_path={"/docs/missing.txt": FileNotFoundError("/docs/missing.txt")},
    )

    result = _invoke(
        source,
        "memory:/docs/one.txt",
        "memory:/docs/missing.txt",
        "memory:/docs/out",
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "mv: memory:/docs/missing.txt: not found\n",
    )
    assert source.file_contents == {"/docs/out/one.txt": b"one"}


def test_mv_multiple_files_directory_source_preserves_prior_move() -> None:
    source = _source(
        contents={"/docs/one.txt": b"one"},
        directories={"/", "/docs", "/docs/nested", "/docs/out"},
        info_by_path={"/docs/nested": {"type": "directory"}},
    )

    result = _invoke(
        source,
        "memory:/docs/one.txt",
        "memory:/docs/nested",
        "memory:/docs/out",
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "mv: memory:/docs/nested: is a directory\n",
    )
    assert source.file_contents == {"/docs/out/one.txt": b"one"}


def test_mv_multiple_files_destination_verification_failure_preserves_prior() -> None:
    source = _source(
        contents={"/docs/one.txt": b"one", "/docs/two.txt": b"two"},
        directories={"/", "/docs", "/docs/out"},
    )

    def corrupt_two(_path1: str, path2: str) -> None:
        filesystem = source.contexts[0].filesystem
        filesystem._file_contents[path2] = b"x"
        source.file_contents[path2] = b"x"

    source.mv_by_path = {"/docs/two.txt": corrupt_two}
    result = _invoke(
        source,
        "memory:/docs/one.txt",
        "memory:/docs/two.txt",
        "memory:/docs/out",
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "mv: memory:/docs/out: verification failure; destination residue may remain\n",
    )
    assert source.file_contents == {
        "/docs/out/one.txt": b"one",
        "/docs/two.txt": b"two",
        "/docs/out/two.txt": b"x",
    }


def test_mv_multiple_files_source_retained_after_later_destination() -> None:
    source = _source(
        contents={"/docs/one.txt": b"one", "/docs/two.txt": b"two"},
        directories={"/", "/docs", "/docs/out"},
    )

    def copy_without_deletion(path1: str, path2: str) -> None:
        filesystem = source.contexts[0].filesystem
        filesystem._file_contents[path2] = filesystem._file_contents[path1]
        source.file_contents[path2] = source.file_contents[path1]

    source.mv_by_path = {"/docs/two.txt": copy_without_deletion}
    result = _invoke(
        source,
        "memory:/docs/one.txt",
        "memory:/docs/two.txt",
        "memory:/docs/out",
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "mv: memory:/docs/out: verification failure; destination residue may remain\n",
    )
    assert source.file_contents == {
        "/docs/out/one.txt": b"one",
        "/docs/two.txt": b"two",
        "/docs/out/two.txt": b"two",
    }


def test_mv_multiple_files_cancellation_closes_source_without_downloads() -> None:
    source = _source(
        contents={"/docs/one.txt": b"one", "/docs/two.txt": b"two"},
        directories={"/", "/docs", "/docs/out"},
        mv_by_path={"/docs/two.txt": asyncio.CancelledError()},
    )

    with pytest.raises(asyncio.CancelledError):
        _invoke(
            source,
            "memory:/docs/one.txt",
            "memory:/docs/two.txt",
            "memory:/docs/out",
        )
    assert source.file_contents == {
        "/docs/out/one.txt": b"one",
        "/docs/two.txt": b"two",
    }
    assert not source.get_file_paths
    assert len(source.exit_calls) == 1
    assert isinstance(source.exit_calls[0][1], asyncio.CancelledError)


def test_mv_replaces_direct_existing_file_target() -> None:
    source = _source(contents={"/docs/notes.txt": b"new", "/docs/moved.txt": b"old"})

    result = _invoke(source, "memory:/docs/notes.txt", "memory:/docs/moved.txt")

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert source.file_contents == {"/docs/moved.txt": b"new"}


def test_mv_rejects_same_backend_under_different_configured_name() -> None:
    source = _source()

    result = CliRunner().invoke(
        App({"memory": source, "alias": source}).typer_app,
        ["mv", "memory:/docs/notes.txt", "alias:/docs/moved.txt"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        2,
        "",
        "mv: cross-source move unsupported\n",
    )
    assert source.call_count == 0


def test_mv_rejects_cross_source_without_factories_or_mutation() -> None:
    source = _source(contents={"/docs/notes.txt": b"payload"})
    destination = _source(contents={"/docs/moved.txt": b"original"})
    source_contents_before = dict(source.file_contents)
    destination_contents_before = dict(destination.file_contents)

    result = CliRunner().invoke(
        App({"source": source, "destination": destination}).typer_app,
        ["mv", "source:/docs/notes.txt", "destination:/docs/moved.txt"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        2,
        "",
        "mv: cross-source move unsupported\n",
    )
    assert source.file_contents == source_contents_before
    assert destination.file_contents == destination_contents_before
    assert source.events == []
    assert destination.events == []
    assert source.call_count == 0
    assert destination.call_count == 0


def test_mv_same_path_is_noop_after_resolution() -> None:
    source = _source()

    result = _invoke(source, "memory:/docs/notes.txt", "memory:/docs/notes.txt")

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert source.file_contents == {"/docs/notes.txt": b"payload"}
    assert not [event for event in source.events if event[0] == "mv"]


def test_mv_directory_target_spelling_resolves_to_same_path_noop() -> None:
    source = _source()

    result = _invoke(source, "memory:/docs/notes.txt", "memory:/docs")

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert source.file_contents == {"/docs/notes.txt": b"payload"}
    assert not [event for event in source.events if event[0] == "mv"]


def test_mv_rejects_missing_destination_parent_without_mutation() -> None:
    source = _source(info_by_path={"/absent": FileNotFoundError("/absent")})

    result = _invoke(source, "memory:/docs/notes.txt", "memory:/absent/moved.txt")

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "mv: memory:/absent/moved.txt: not found\n",
    )
    assert source.file_contents == {"/docs/notes.txt": b"payload"}
    assert not [event for event in source.events if event[0] == "mv"]


@pytest.mark.parametrize(
    ("arguments", "contexts"),
    [
        (
            ("-f", "memory:/docs/notes.txt", "memory:/docs/moved.txt"),
            ("No such option", "-f"),
        ),
        (
            ("-i", "memory:/docs/notes.txt", "memory:/docs/moved.txt"),
            ("No such option", "-i"),
        ),
        (
            ("--interactive", "memory:/docs/notes.txt", "memory:/docs/moved.txt"),
            ("No such option", "interactive"),
        ),
        (
            ("--help=value",),
            ("does not take a value", "help"),
        ),
        (
            (),
            ("Missing argument", "name:/path"),
        ),
        (
            ("memory:/docs/notes.txt",),
            ("at least one source and one destination", "name:/path"),
        ),
    ],
)
def test_mv_leaves_usage_failures_to_typer(
    arguments: tuple[str, ...],
    contexts: tuple[str, ...],
) -> None:
    result = CliRunner().invoke(
        App({"memory": _source_must_not_run}).typer_app,
        ["mv", *arguments],
    )

    assert (result.exit_code, result.stdout) == (2, "")
    stderr = _plain(result.stderr)
    for context in contexts:
        assert context in stderr


@pytest.mark.parametrize(
    ("arguments", "diagnostic"),
    [
        (
            ("memory:/docs/notes.txt", "other:/docs/moved.txt"),
            "mv: cross-source move unsupported\n",
        ),
        (
            (
                "memory:/docs/notes.txt",
                "other:/docs/second.txt",
                "memory:/docs/out",
            ),
            "mv: cross-source move unsupported\n",
        ),
        (
            (
                "memory:/docs/notes.txt",
                "memory:/docs/second.txt",
                "other:/docs/out",
            ),
            "mv: cross-source move unsupported\n",
        ),
        (
            ("memory:relative", "memory:/docs/moved.txt"),
            "mv: memory:relative: invalid mapped filesystem operand\n",
        ),
        (
            (
                "memory:/docs/one.txt",
                "unknown:/docs/two.txt",
                "memory:/docs/out",
            ),
            "mv: unknown:/docs/two.txt: unknown filesystem (known: memory, other)\n",
        ),
        (
            ("not-mapped", "memory:/docs/one.txt", "memory:/docs/out"),
            "mv: not-mapped: invalid mapped filesystem operand\n",
        ),
        (
            ("--", "-f", "memory:/docs/moved.txt"),
            "mv: -f: invalid mapped filesystem operand\n",
        ),
    ],
)
def test_mv_validates_every_mapped_input_before_source_acquisition(arguments: tuple[str, ...], diagnostic: str) -> None:
    result = CliRunner().invoke(
        App({"memory": _source_must_not_run, "other": _source_must_not_run}).typer_app,
        ["mv", *arguments],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (2, "", diagnostic)


def test_mv_rejects_directory_source_before_mutation() -> None:
    source = _source(
        contents={},
        directories={"/", "/docs"},
        info_by_path={"/docs": {"type": "directory"}},
    )

    result = _invoke(source, "memory:/docs", "memory:/docs/moved.txt")

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "mv: memory:/docs: is a directory\n",
    )
    assert not [event for event in source.events if event[0] == "mv"]
    assert not [event for event in source.events if event[0] == "get_file"]
    assert [event[:3] for event in source.events if event[0] == "info"] == [("info", 1, "/docs")]


def test_mv_reports_mutation_exception_as_uncertain_residue() -> None:
    source = _source(mv_error=OSError("move failed"))

    result = _invoke(source, "memory:/docs/notes.txt", "memory:/docs/moved.txt")

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "mv: memory:/docs/moved.txt: uncertain mutation state; destination residue may remain\n",
    )
    assert source.file_contents == {"/docs/notes.txt": b"payload"}
    assert not source.get_file_paths


def test_mv_reports_source_deletion_failure_after_destination_creation() -> None:
    source = _source()

    def copy_then_source_deletion_fails(path1: str, path2: str) -> None:
        filesystem = source.contexts[0].filesystem
        filesystem._file_contents[path2] = filesystem._file_contents[path1]
        source.file_contents[path2] = source.file_contents[path1]
        raise PermissionError(_MOVE_FAILED)

    source.mv_hook = copy_then_source_deletion_fails
    result = _invoke(source, "memory:/docs/notes.txt", "memory:/docs/moved.txt")

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "mv: memory:/docs/moved.txt: uncertain mutation state; destination residue may remain\n",
    )
    assert source.file_contents == {
        "/docs/notes.txt": b"payload",
        "/docs/moved.txt": b"payload",
    }


def test_mv_rejects_destination_type_mismatch_and_source_residue() -> None:
    source = _source()

    def make_directory(_path1: str, path2: str) -> None:
        filesystem = source.contexts[0].filesystem
        filesystem._directories.add(path2)
        source.directories.add(path2)

    source.mv_hook = make_directory
    result = _invoke(source, "memory:/docs/notes.txt", "memory:/docs/moved.txt")

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "mv: memory:/docs/moved.txt: verification failure; destination residue may remain\n",
    )
    assert source.file_contents == {"/docs/notes.txt": b"payload"}
    assert "/docs/moved.txt" in source.directories


def test_mv_rejects_destination_size_mismatch_after_source_removal() -> None:
    source = _source()

    def truncate_and_remove_source(path1: str, path2: str) -> None:
        filesystem = source.contexts[0].filesystem
        filesystem._file_contents.pop(path1)
        source.file_contents.pop(path1)
        filesystem._file_contents[path2] = b"short"
        source.file_contents[path2] = b"short"
        filesystem._removed_paths.add(path1)

    source.mv_hook = truncate_and_remove_source
    result = _invoke(source, "memory:/docs/notes.txt", "memory:/docs/moved.txt")

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "mv: memory:/docs/moved.txt: verification failure; destination residue may remain\n",
    )
    assert source.file_contents == {"/docs/moved.txt": b"short"}


def test_mv_accepts_same_size_changed_content_without_shared_token() -> None:
    source = _source()

    def replace_and_remove_source(path1: str, path2: str) -> None:
        filesystem = source.contexts[0].filesystem
        filesystem._file_contents.pop(path1)
        source.file_contents.pop(path1)
        filesystem._file_contents[path2] = b"invalid"
        source.file_contents[path2] = b"invalid"
        filesystem._removed_paths.add(path1)

    source.mv_hook = replace_and_remove_source
    result = _invoke(source, "memory:/docs/notes.txt", "memory:/docs/moved.txt")

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert source.file_contents == {"/docs/moved.txt": b"invalid"}
    assert not source.get_file_paths


def test_mv_rejects_source_retained_after_complete_destination() -> None:
    source = _source()

    def copy_without_deletion(path1: str, path2: str) -> None:
        filesystem = source.contexts[0].filesystem
        filesystem._file_contents[path2] = filesystem._file_contents[path1]
        source.file_contents[path2] = source.file_contents[path1]

    source.mv_hook = copy_without_deletion
    result = _invoke(source, "memory:/docs/notes.txt", "memory:/docs/moved.txt")

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "mv: memory:/docs/moved.txt: verification failure; destination residue may remain\n",
    )
    assert source.file_contents == {
        "/docs/notes.txt": b"payload",
        "/docs/moved.txt": b"payload",
    }


def test_mv_cancellation_closes_source_without_verification_downloads() -> None:
    source = _source(mv_error=asyncio.CancelledError())

    with pytest.raises(asyncio.CancelledError):
        _invoke(source, "memory:/docs/notes.txt", "memory:/docs/moved.txt")
    assert not source.get_file_paths
    assert len(source.exit_calls) == 1
    assert isinstance(source.exit_calls[0][1], asyncio.CancelledError)
