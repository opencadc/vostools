"""Guarded recursive ``rm`` tests through the public application seam."""

from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager
from typing import TYPE_CHECKING, NoReturn
from unittest.mock import Mock

import pytest
from fsspec.asyn import AsyncFileSystem
from typer.testing import CliRunner, Result

from fss_cli import App, AsyncFilesystemSource

from ._ansi import strip_ansi
from ._support import _RecordingSource

if TYPE_CHECKING:
    from pathlib import Path


def _invoke_recursive_rm(
    arguments: list[str],
    *,
    sources: dict[str, AsyncFilesystemSource],
    enabled: bool = True,
) -> Result:
    return CliRunner().invoke(
        App(
            sources,
            capabilities={"recursion": {"remove": enabled}},
        ).typer_app,
        ["rm", *arguments],
    )


@pytest.mark.parametrize("enabled", [None, False])
@pytest.mark.parametrize("option", ["-R", "-r"])
def test_recursive_rm_disabled_is_policy_first_and_source_free(
    enabled: bool | None,
    option: str,
) -> None:
    source_calls = 0

    def source_must_not_run() -> NoReturn:
        nonlocal source_calls
        source_calls += 1
        raise AssertionError

    capabilities = None if enabled is None else {"recursion": {"remove": enabled}}
    result = CliRunner().invoke(
        App(
            {"memory": source_must_not_run},
            capabilities=capabilities,
        ).typer_app,
        ["rm", option, "not-a-mapped-operand"],
    )

    assert (result.exit_code, result.stdout) == (2, "")
    assert f"No such option: {option}" in strip_ansi(result.stderr)
    assert source_calls == 0


@pytest.mark.parametrize(
    "arguments",
    [
        ["-vf", "-R", "source:/dir"],
        ["-fv", "-r", "source:/dir"],
        ["-ffv", "-R", "source:/dir"],
        ["-f", "-vf", "-r", "source:/dir"],
    ],
)
def test_recursive_rm_disabled_rejects_after_grouped_valid_prefixes(
    arguments: list[str],
) -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events)

    result = _invoke_recursive_rm(
        arguments,
        sources={"source": source},
        enabled=False,
    )

    assert (result.exit_code, result.stdout) == (2, "")
    assert "No such option: -R" in strip_ansi(result.stderr) or "No such option: -r" in strip_ansi(result.stderr)
    assert source.call_count == 0
    assert events == []


@pytest.mark.parametrize("option", ["-R", "-r"])
def test_recursive_rm_enabled_removes_a_complete_nested_manifest(option: str) -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_by_path={
            "/docs": {"name": "/docs", "type": "directory"},
            "/docs/sub": {"name": "/docs/sub", "type": "directory"},
            "/docs/sub/empty": {
                "name": "/docs/sub/empty",
                "type": "directory",
            },
            "/docs/sub/a.txt": {"name": "/docs/sub/a.txt", "type": "file"},
            "/docs/z.txt": {"name": "/docs/z.txt", "type": "file"},
        },
        ls_by_path={
            "/docs": [
                {"name": "/docs/z.txt", "type": "file"},
                {"name": "/docs/sub", "type": "directory"},
            ],
            "/docs/sub": [
                {"name": "/docs/sub/empty", "type": "directory"},
                {"name": "/docs/sub/a.txt", "type": "file"},
            ],
            "/docs/sub/empty": [],
        },
    )

    result = _invoke_recursive_rm(
        [option, "memory:/docs"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    observed = [(event[0], event[2]) for event in events if event[0] in {"info", "ls", "rm_file", "rmdir"}]
    assert observed[:4] == [
        ("info", "/docs"),
        ("ls", "/docs"),
        ("ls", "/docs/sub"),
        ("ls", "/docs/sub/empty"),
    ]
    removals = observed[4:]
    assert sorted(removals) == sorted(
        [
            ("rm_file", "/docs/sub/a.txt"),
            ("info", "/docs/sub/a.txt"),
            ("rmdir", "/docs/sub/empty"),
            ("info", "/docs/sub/empty"),
            ("rmdir", "/docs/sub"),
            ("info", "/docs/sub"),
            ("rm_file", "/docs/z.txt"),
            ("info", "/docs/z.txt"),
            ("rmdir", "/docs"),
            ("info", "/docs"),
        ]
    )
    # Each primitive precedes its absence proof, and every directory is
    # removed only after each descendant's absence was proven.
    for path in ("/docs/sub/a.txt", "/docs/sub/empty", "/docs/sub", "/docs/z.txt"):
        mutation = next(i for i, event in enumerate(removals) if event[1] == path)
        proof = max(i for i, event in enumerate(removals) if event[1] == path)
        assert removals[mutation][0] in {"rm_file", "rmdir"}
        assert mutation < proof
        parent = path.rsplit("/", 1)[0]
        assert proof < removals.index(("rmdir", parent))
    assert removals[-2:] == [("rmdir", "/docs"), ("info", "/docs")]
    assert not any(event[0] == "rm" for event in events)


def test_recursive_rm_help_tracks_snapshotted_application_policy() -> None:
    disabled = CliRunner().invoke(
        App({"memory": lambda: None}).typer_app,  # type: ignore[dict-item]
        ["rm", "--help"],
    )
    enabled = CliRunner().invoke(
        App(
            {"memory": lambda: None},  # type: ignore[dict-item]
            capabilities={"recursion": {"remove": True}},
        ).typer_app,
        ["rm", "--help"],
    )

    disabled_help = " ".join(strip_ansi(disabled.stdout).split())
    enabled_help = " ".join(strip_ansi(enabled.stdout).split())
    assert disabled.exit_code == enabled.exit_code == 0
    assert "-d removes empty directories" in disabled_help
    assert "guarded -R or -r" not in disabled_help
    assert "guarded -R or -r" in enabled_help
    assert disabled.stderr == enabled.stderr == ""


@pytest.mark.parametrize("arguments", [["--help"], ["-R", "--help"]])
def test_recursive_rm_framework_help_short_circuits_normally(
    arguments: list[str],
) -> None:
    result = CliRunner().invoke(
        App(
            {"memory": lambda: None},  # type: ignore[dict-item]
            capabilities={"recursion": {"remove": True}},
        ).typer_app,
        ["rm", *arguments],
    )

    assert result.exit_code == 0
    assert "Usage:" in result.stdout
    assert result.stderr == ""


@pytest.mark.parametrize(
    "arguments",
    [
        ["-Rf"],
        ["-rff"],
        ["-R", "-r", "-f"],
        ["-fR"],
    ],
)
def test_recursive_rm_force_combinations_allow_zero_operands(
    arguments: list[str],
) -> None:
    result = _invoke_recursive_rm(
        arguments,
        sources={"memory": lambda: None},  # type: ignore[dict-item]
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")


@pytest.mark.parametrize(
    "arguments",
    [
        ["-Rv", "-v", "memory:/docs"],
        ["-Rvv", "memory:/docs"],
        ["-R", "-d", "memory:/docs"],
        ["-d", "-R", "memory:/docs"],
        ["--recursive", "memory:/docs"],
    ],
)
def test_recursive_rm_rejects_unprofiled_option_shapes_source_free(
    arguments: list[str],
) -> None:
    source = _RecordingSource([])

    result = _invoke_recursive_rm(arguments, sources={"memory": source})

    assert result.exit_code == 2
    assert result.stdout == ""
    assert source.call_count == 0


def test_recursive_rm_option_after_operand_is_parsed_by_typer() -> None:
    source = _RecordingSource(
        [],
        info_by_path={"/docs": {"name": "/docs", "type": "directory"}},
        ls_by_path={"/docs": []},
    )

    result = _invoke_recursive_rm(
        ["memory:/docs", "-R"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert source.call_count == 1


@pytest.mark.parametrize(
    "path",
    ["/", "////", "/.", "/..", "/docs/./item", "/docs/../item", "/docs/item/.."],
)
def test_recursive_rm_rejects_roots_and_any_dot_segment_source_free(
    path: str,
) -> None:
    source = _RecordingSource([])

    result = _invoke_recursive_rm(
        ["-R", f"memory:{path}"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        2,
        "",
        f"rm: memory:{path}: rejected path\n",
    )
    assert source.call_count == 0


@pytest.mark.parametrize(
    ("operand", "diagnostic"),
    [
        (
            "not-mapped",
            "rm: not-mapped: invalid mapped filesystem operand\n",
        ),
        (
            "unknown:/docs",
            "rm: unknown:/docs: unknown filesystem (known: memory)\n",
        ),
    ],
)
def test_recursive_rm_rejects_invalid_or_unknown_operands_source_free(
    operand: str,
    diagnostic: str,
) -> None:
    source = _RecordingSource([])

    result = _invoke_recursive_rm(
        ["-R", operand],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        2,
        "",
        diagnostic,
    )
    assert source.call_count == 0


def test_recursive_rm_accepts_trailing_slashes_but_uses_normalized_root() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_by_path={"/docs": {"name": "/docs/", "type": "directory"}},
        ls_by_path={"/docs": []},
    )

    result = _invoke_recursive_rm(
        ["-vR", "memory:/docs///"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "memory:/docs///\n",
        "",
    )
    assert [event[2] for event in events if event[0] in {"info", "ls", "rmdir"}] == [
        "/docs",
        "/docs",
        "/docs",
        "/docs",
    ]


def test_recursive_rm_allows_force_and_verbose_before_recursive_option() -> None:
    source = _RecordingSource(
        [],
        info_by_path={"/docs": {"name": "/docs", "type": "directory"}},
        ls_by_path={"/docs": []},
    )

    result = _invoke_recursive_rm(
        ["-f", "-v", "-R", "memory:/docs"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "memory:/docs\n",
        "",
    )


@pytest.mark.parametrize(
    ("root_info", "listing", "category"),
    [
        (
            {"name": "/docs", "type": "directory", "islink": True},
            [],
            "unsupported operation",
        ),
        ({"name": "/docs", "type": "other"}, [], "not a directory"),
        ({"name": "/docs", "type": "file"}, [], "not a directory"),
        ({"name": "/other", "type": "directory"}, [], "incompatible result"),
        (
            {"name": "/docs", "type": "directory", "islink": 1},
            [],
            "incompatible result",
        ),
        (
            {"name": "/docs", "type": "directory"},
            [{"name": "/docs/link", "type": "other", "islink": True}],
            "unsupported operation",
        ),
        (
            {"name": "/docs", "type": "directory"},
            [{"name": "/docs/device", "type": "special"}],
            "unsupported operation",
        ),
        (
            {"name": "/docs", "type": "directory"},
            [{"name": "/outside", "type": "file"}],
            "incompatible result",
        ),
        (
            {"name": "/docs", "type": "directory"},
            [{"name": "/docs/a/b", "type": "file"}],
            "incompatible result",
        ),
        (
            {"name": "/docs", "type": "directory"},
            [{"name": "/docs", "type": "directory"}],
            "incompatible result",
        ),
        (
            {"name": "/docs", "type": "directory"},
            [{"name": "/docs/a", "type": "file"}, {"name": "/docs/a", "type": "file"}],
            "incompatible result",
        ),
        (
            {"name": "/docs", "type": "directory"},
            {"name": "/docs/a", "type": "file"},
            "incompatible result",
        ),
    ],
)
def test_recursive_rm_rejects_unsafe_or_malformed_manifests_before_mutation(
    root_info: object,
    listing: object,
    category: str,
) -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_by_path={"/docs": root_info},
        ls_by_path={"/docs": listing},
    )

    result = _invoke_recursive_rm(
        ["-R", "memory:/docs"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        f"rm: memory:/docs: {category}\n",
    )
    assert not any(event[0] in {"rm_file", "rmdir", "rm"} for event in events)


class _MutatingListing(list[object]):
    def __iter__(self):
        iterator = super().__iter__()
        first = next(iterator)
        yield first
        self.append({"name": "/docs/late", "type": "file"})
        yield from iterator


def test_recursive_rm_rejects_a_listing_that_mutates_during_consumption() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_by_path={"/docs": {"name": "/docs", "type": "directory"}},
        ls_by_path={"/docs": _MutatingListing([{"name": "/docs/first", "type": "file"}])},
    )

    result = _invoke_recursive_rm(
        ["-R", "memory:/docs"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "rm: memory:/docs: incompatible result\n",
    )
    assert not any(event[0] in {"rm_file", "rmdir"} for event in events)


def test_recursive_rm_handles_a_deep_finite_manifest_iteratively() -> None:
    depth = 1_200
    paths = ["/docs"]
    for index in range(depth):
        paths.append(f"{paths[-1]}/d{index}")
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_by_path={"/docs": {"name": "/docs", "type": "directory"}},
        ls_by_path={
            path: ([{"name": paths[index + 1], "type": "directory"}] if index < depth else [])
            for index, path in enumerate(paths)
        },
    )

    result = _invoke_recursive_rm(
        ["-R", "memory:/docs"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert [event[2] for event in events if event[0] == "rmdir"] == list(reversed(paths))


def test_recursive_rm_force_suppresses_only_a_missing_root() -> None:
    missing_root = _RecordingSource(
        [],
        info_by_path={"/missing": FileNotFoundError()},
    )
    missing_listing = _RecordingSource(
        [],
        info_by_path={"/docs": {"name": "/docs", "type": "directory"}},
        ls_by_path={"/docs": FileNotFoundError()},
    )

    suppressed = _invoke_recursive_rm(
        ["-Rf", "memory:/missing"],
        sources={"memory": missing_root},
    )
    visible = _invoke_recursive_rm(
        ["-Rf", "memory:/docs"],
        sources={"memory": missing_listing},
    )

    assert (suppressed.exit_code, suppressed.stdout, suppressed.stderr) == (0, "", "")
    assert (visible.exit_code, visible.stdout, visible.stderr) == (
        1,
        "",
        "rm: memory:/docs: not found\n",
    )


def test_recursive_rm_partial_failure_continues_later_operands() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_by_path={
            "/bad": {"name": "/bad", "type": "directory"},
            "/good": {"name": "/good", "type": "directory"},
        },
        ls_by_path={
            "/bad": [{"name": "/bad/file", "type": "file"}],
            "/good": [],
        },
        rm_file_by_path={"/bad/file": PermissionError("denied")},
    )

    result = _invoke_recursive_rm(
        ["-R", "memory:/bad", "memory:/good"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "rm: memory:/bad: recursive removal incomplete; residue possible\n",
    )
    assert [event[2] for event in events if event[0] == "rmdir"] == ["/good"]


def test_recursive_rm_orders_overlapping_and_force_repeated_operands() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_by_path={
            "/docs": {"name": "/docs", "type": "directory"},
            "/docs/sub": {"name": "/docs/sub", "type": "directory"},
        },
        ls_by_path={"/docs/sub": [], "/docs": []},
    )

    result = _invoke_recursive_rm(
        [
            "-Rf",
            "memory:/docs/sub",
            "memory:/docs",
            "memory:/docs",
        ],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert [event[2] for event in events if event[0] == "rmdir"] == [
        "/docs/sub",
        "/docs",
    ]


def test_recursive_rm_final_absence_must_be_proved() -> None:
    source = _RecordingSource(
        [],
        info_by_path={"/docs": {"name": "/docs", "type": "directory"}},
        ls_by_path={"/docs": []},
        post_info_by_path={"/docs": {"name": "/docs", "type": "directory"}},
    )

    result = _invoke_recursive_rm(
        ["-Rv", "memory:/docs"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "rm: memory:/docs: recursive removal incomplete; residue possible\n",
    )


def test_recursive_rm_acquires_all_sources_then_exits_in_reverse_order() -> None:
    events: list[tuple[object, ...]] = []
    alpha = _RecordingSource(
        events,
        info_by_path={"/a": {"name": "/a", "type": "directory"}},
        ls_by_path={"/a": []},
    )
    beta = _RecordingSource(
        events,
        info_by_path={"/b": {"name": "/b", "type": "directory"}},
        ls_by_path={"/b": []},
    )

    result = _invoke_recursive_rm(
        ["-R", "alpha:/a", "beta:/b"],
        sources={"alpha": alpha, "beta": beta},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    stages = [event[0] for event in events]
    assert stages[:4] == ["factory", "enter", "factory", "enter"]
    assert stages[-2:] == ["exit", "exit"]
    assert [event[1] for event in events if event[0] == "exit"] == [1, 1]


def test_recursive_rm_source_has_no_backend_dispatch_or_forbidden_calls() -> None:
    import inspect

    import fss_cli._recursive_rm as recursive_rm

    source = inspect.getsource(recursive_rm)
    for forbidden in (
        "LocalFileSystem",
        "MemoryFileSystem",
        "VOSpaceFileSystem",
        "sync_fs",
        "protocol",
        '"_find"',
        '"_walk"',
        '"_rm"',
    ):
        assert forbidden not in source


def test_adapted_local_ancestor_swap_fixture_retains_unverified_race(
    tmp_path: Path,
) -> None:
    from fsspec.implementations.asyn_wrapper import AsyncFileSystemWrapper
    from fsspec.implementations.local import LocalFileSystem

    selected = tmp_path / "selected"
    preserved = tmp_path / "preserved"
    outside = tmp_path / "outside"
    selected.mkdir()
    outside.mkdir()
    (selected / "victim").write_text("inside")
    (outside / "victim").write_text("outside")

    filesystem = AsyncFileSystemWrapper(
        LocalFileSystem(skip_instance_cache=True),
        asynchronous=True,
    )
    rm_file = filesystem._rm_file
    swapped = False

    async def swap_then_remove(path: str, **kwargs: object) -> None:
        nonlocal swapped
        if not swapped:
            selected.rename(preserved)
            selected.symlink_to(outside, target_is_directory=True)
            swapped = True
        await rm_file(path, **kwargs)

    filesystem._rm_file = swap_then_remove  # type: ignore[method-assign]

    @asynccontextmanager
    async def source():
        yield filesystem

    result = _invoke_recursive_rm(
        ["-R", f"local:{selected}"],
        sources={"local": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        f"rm: local:{selected}: recursive removal incomplete; residue possible\n",
    )
    assert (preserved / "victim").read_text() == "inside"
    assert not (outside / "victim").exists()


class _CancellingFileSystem(AsyncFileSystem):
    cachable = False

    def __init__(self, stage: str, events: list[str]) -> None:
        super().__init__(asynchronous=True)
        self.stage = stage
        self.events = events
        self.info_calls: dict[str, int] = {}
        self.removed: set[str] = set()

    async def _cancel_invocation(self, stage: str) -> None:
        current = asyncio.current_task()
        outer = next(task for task in asyncio.all_tasks() if task is not current)
        outer.cancel()
        await asyncio.sleep(0)
        self.events.append(f"{stage}-drained")

    async def _info(self, path: str, **kwargs: object) -> object:
        del kwargs
        operation = "verify" if path in self.removed else "info"
        self.events.append(f"{operation}:{path}")
        self.info_calls[path] = self.info_calls.get(path, 0) + 1
        if self.stage == "root-info" and path == "/docs":
            await self._cancel_invocation("root-info")
        if self.stage == "file-verify" and path == "/docs/file" and path in self.removed:
            await self._cancel_invocation("file-verify")
            raise FileNotFoundError(path)
        if self.stage == "root-verify" and path == "/docs" and path in self.removed:
            await self._cancel_invocation("root-verify")
            raise FileNotFoundError(path)
        if path in self.removed:
            raise FileNotFoundError(path)
        if path == "/docs":
            return {"name": path, "type": "directory"}
        if path == "/docs/file":
            return {"name": path, "type": "file"}
        raise FileNotFoundError(path)

    async def _ls(
        self,
        path: str,
        detail: bool = True,  # noqa: FBT002 - fsspec hook signature.
        **kwargs: object,
    ) -> object:
        del detail, kwargs
        self.events.append(f"ls:{path}")
        if self.stage == "listing":
            await self._cancel_invocation("listing")
        return [{"name": "/docs/file", "type": "file"}]

    async def _rm_file(self, path: str, **kwargs: object) -> None:
        del kwargs
        self.events.append(f"rm_file:{path}")
        if self.stage == "file-remove":
            await self._cancel_invocation("file-remove")
        self.removed.add(path)

    async def _rmdir(self, path: str, **kwargs: object) -> None:
        del kwargs
        self.events.append(f"rmdir:{path}")
        if self.stage == "root-remove":
            await self._cancel_invocation("root-remove")
        self.removed.add(path)


def _cancelling_source(
    stage: str,
    events: list[str],
):
    @asynccontextmanager
    async def source():
        filesystem = _CancellingFileSystem(stage, events)
        try:
            yield filesystem
        finally:
            events.append("exit")

    return source


@pytest.mark.parametrize(
    ("stage", "required", "forbidden"),
    [
        ("root-info", "root-info-drained", "ls:/docs"),
        ("listing", "listing-drained", "rm_file:/docs/file"),
        ("file-remove", "file-remove-drained", "info:/docs/file"),
        ("file-verify", "file-verify-drained", "rmdir:/docs"),
        ("root-remove", "root-remove-drained", "verify:/docs"),
        ("root-verify", "root-verify-drained", "success-output"),
    ],
)
def test_recursive_rm_drains_current_hook_before_cleanup_on_cancellation(
    stage: str,
    required: str,
    forbidden: str,
) -> None:
    events: list[str] = []

    with pytest.raises(asyncio.CancelledError):
        _invoke_recursive_rm(
            ["-Rv", "memory:/docs"],
            sources={"memory": _cancelling_source(stage, events)},
        )

    assert required in events
    assert events[-1] == "exit"
    assert events.index(required) < events.index("exit")
    assert forbidden not in events


def test_recursive_rm_cancellation_after_final_proof_keeps_absence() -> None:
    events: list[tuple[object, ...]] = []
    control = asyncio.CancelledError()
    source = _RecordingSource(
        events,
        info_by_path={"/docs": {"name": "/docs", "type": "directory"}},
        ls_by_path={"/docs": []},
        exit_error=control,
    )

    with pytest.raises(asyncio.CancelledError) as caught:
        _invoke_recursive_rm(
            ["-R", "memory:/docs"],
            sources={"memory": source},
        )

    # Python 3.10 may rewrap CancelledError at the asyncio.run boundary (#241).
    assert type(caught.value) is asyncio.CancelledError
    assert source.exit_calls == [(None, None, None)]
    assert [event[0] for event in events].count("rmdir") == 1
    assert [event[0] for event in events].count("info") == 2


def test_recursive_rm_snapshots_enabled_policy_at_app_construction() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_by_path={"/docs": {"name": "/docs", "type": "directory"}},
        ls_by_path={"/docs": []},
    )
    recursion = {"remove": True}
    typer_app = App(
        {"memory": source},
        capabilities={"recursion": recursion},
    ).typer_app
    recursion["remove"] = False

    result = CliRunner().invoke(typer_app, ["rm", "-R", "memory:/docs"])

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert any(event[0] == "rmdir" for event in events)


class _MissingMutationHookFileSystem(AsyncFileSystem):
    cachable = False

    def __init__(self, events: list[str]) -> None:
        super().__init__(asynchronous=True)
        self.events = events
        self._rm_file = None  # type: ignore[method-assign]

    async def _info(self, path: str, **kwargs: object) -> object:
        del path, kwargs
        self.events.append("info")
        return {"name": "/docs", "type": "directory"}

    async def _ls(
        self,
        path: str,
        detail: bool = True,  # noqa: FBT002 - fsspec hook signature.
        **kwargs: object,
    ) -> object:
        del path, detail, kwargs
        self.events.append("ls")
        return []

    async def _rmdir(self, path: str, **kwargs: object) -> None:
        del path, kwargs
        self.events.append("rmdir")


def test_recursive_rm_missing_mutation_hook_fails_before_filesystem_work() -> None:
    events: list[str] = []

    @asynccontextmanager
    async def source():
        yield _MissingMutationHookFileSystem(events)

    result = _invoke_recursive_rm(
        ["-R", "memory:/docs"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "rm: memory:/docs: unsupported operation\n",
    )
    assert events == []


def test_recursive_rm_verbose_output_failure_stops_later_mutation(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        info_by_path={
            "/one": {"name": "/one", "type": "directory"},
            "/two": {"name": "/two", "type": "directory"},
        },
        ls_by_path={"/one": [], "/two": []},
    )
    output_error = OSError("closed")
    write = Mock(side_effect=output_error)
    monkeypatch.setattr("fss_cli._rm._write_verbose_line", write)

    result = _invoke_recursive_rm(
        ["-Rv", "memory:/one", "memory:/two"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "rm: output: output failure (OSError): closed\n",
    )
    assert [event[2] for event in events if event[0] == "rmdir"] == ["/one"]
    assert source.exit_calls[0][1] is output_error


def test_recursive_rm_preserves_partial_failure_before_cleanup_failure() -> None:
    source = _RecordingSource(
        [],
        info_by_path={"/docs": {"name": "/docs", "type": "directory"}},
        ls_by_path={
            "/docs": [{"name": "/docs/file", "type": "file"}],
        },
        rm_file_error=PermissionError("denied"),
        exit_error=OSError("cleanup failed"),
    )

    result = _invoke_recursive_rm(
        ["-R", "memory:/docs"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "rm: memory:/docs: recursive removal incomplete; residue possible\n"
        "rm: memory: source exit failure (OSError): cleanup failed\n",
    )
