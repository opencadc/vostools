"""``tree`` command tests through the public embedded-command seam."""

from __future__ import annotations

import asyncio
import locale
import sys
from collections.abc import Awaitable, Callable, Iterator, Mapping, Sequence
from contextlib import AbstractAsyncContextManager
from typing import TYPE_CHECKING

import pytest
import typer
from fsspec.asyn import AsyncFileSystem
from typer.main import get_command

from fss_cli import App

from ._ansi import strip_ansi
from ._support import _invoke

if TYPE_CHECKING:
    from collections.abc import Coroutine
    from types import TracebackType


_ListingHook = Callable[["_TreeSource", str], Awaitable[None]]


class _TreeControl(BaseException):
    pass


class _ListSubclass(list[object]):
    pass


class _StrSubclass(str):
    __slots__ = ()


class _LyingLengthEntry(dict[str, object]):
    def __len__(self) -> int:
        return super().__len__() + 1


class _ExplodingEntry(dict[str, object]):
    def __iter__(self) -> Iterator[str]:
        raise RuntimeError


def _entry(name: object, kind: object = "file") -> dict[str, object]:
    return {"name": name, "type": kind, "size": 0}


def _listings(
    rows: Sequence[tuple[str, Sequence[str], Sequence[str]]],
) -> dict[str, object]:
    """Derive one ``_ls(detail=True)`` listing per fsspec-shaped walk row."""
    listings: dict[str, object] = {}
    for root, directories, files in rows:
        listing = [_entry(f"{root}/{name}", "directory") for name in directories]
        listing.extend(_entry(f"{root}/{name}" if name else root) for name in files)
        listings[root] = listing
    return listings


class _TreeFileSystem(AsyncFileSystem):
    cachable = False

    def __init__(self, source: _TreeSource) -> None:
        super().__init__(asynchronous=True)
        self.source = source

    def _ls(  # type: ignore[override]
        self,
        path: str,
        detail: bool = True,  # noqa: FBT002 - matches the fsspec hook signature.
        **kwargs: object,
    ) -> object:
        self.source.ls_calls.append((path, detail, kwargs))
        if self.source.invoke_error is not None:
            raise self.source.invoke_error
        return self._listing(path)

    async def _listing(self, path: str) -> object:
        if self.source.hook is not None:
            await self.source.hook(self.source, path)
        if path not in self.source.listings:
            raise FileNotFoundError(path)
        scripted = self.source.listings[path]
        if isinstance(scripted, BaseException):
            raise scripted
        return scripted


class _TreeSource:
    def __init__(
        self,
        *,
        listings: Mapping[str, object] | None = None,
        invoke_error: BaseException | None = None,
        hook: _ListingHook | None = None,
        exit_error: BaseException | None = None,
    ) -> None:
        self.listings = listings if listings is not None else {"/docs": []}
        self.invoke_error = invoke_error
        self.hook = hook
        self.exit_error = exit_error
        self.lifecycle: list[str] = []
        self.ls_calls: list[tuple[str, bool, dict[str, object]]] = []
        self.exit_calls: list[
            tuple[
                type[BaseException] | None,
                BaseException | None,
                TracebackType | None,
            ]
        ] = []

    def __call__(self) -> _TreeContext:
        self.lifecycle.append("factory")
        return _TreeContext(self)


class _TreeContext(AbstractAsyncContextManager[_TreeFileSystem]):
    def __init__(self, source: _TreeSource) -> None:
        self.source = source
        self.filesystem = _TreeFileSystem(source)

    async def __aenter__(self) -> _TreeFileSystem:
        self.source.lifecycle.append("enter")
        return self.filesystem

    async def __aexit__(
        self,
        exc_type: type[BaseException] | None,
        exc: BaseException | None,
        traceback: TracebackType | None,
    ) -> None:
        self.source.exit_calls.append((exc_type, exc, traceback))
        self.source.lifecycle.append("exit")
        if self.source.exit_error is not None:
            raise self.source.exit_error


_TREE_ROWS: list[tuple[str, list[str], list[str]]] = [
    ("/docs", ["z-dir", "a-dir"], ["z.txt", "a.txt"]),
    ("/docs/a-dir", ["nested"], ["b.txt"]),
    ("/docs/a-dir/nested", [], ["c.txt"]),
    ("/docs/z-dir", [], []),
]

_TREE_LEVELS: list[list[str]] = [
    ["/docs"],
    ["/docs/z-dir", "/docs/a-dir"],
    ["/docs/a-dir/nested"],
]

_TREE_OUTPUT = "/docs\n├── a-dir\n│   ├── nested\n│   │   └── c.txt\n│   └── b.txt\n├── z-dir\n├── a.txt\n└── z.txt\n"


def _listed(levels: Sequence[Sequence[str]]) -> list[tuple[str, bool, dict]]:
    return [(path, True, {}) for level in levels for path in level]


def test_tree_lists_each_directory_once_breadth_first_and_renders_exactly() -> None:
    source = _TreeSource(listings=_listings(_TREE_ROWS))

    result = _invoke("tree", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        _TREE_OUTPUT,
        "",
    )
    assert source.lifecycle == ["factory", "enter", "exit"]
    assert source.ls_calls == _listed(_TREE_LEVELS)


def test_tree_lists_sibling_directories_concurrently_with_deterministic_output() -> None:
    siblings = {"/docs/z-dir", "/docs/a-dir"}
    both_started = asyncio.Event()
    in_flight: set[str] = set()
    peak: list[int] = []
    completed: list[str] = []

    async def overlap(source: _TreeSource, path: str) -> None:
        del source
        if path not in siblings:
            return
        in_flight.add(path)
        peak.append(len(in_flight))
        if in_flight == siblings:
            both_started.set()
        await asyncio.wait_for(both_started.wait(), timeout=5)
        # The first-submitted sibling finishes last.
        for _ in range(5 if path == "/docs/z-dir" else 0):
            await asyncio.sleep(0)
        in_flight.discard(path)
        completed.append(path)

    source = _TreeSource(listings=_listings(_TREE_ROWS), hook=overlap)

    result = _invoke("tree", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        _TREE_OUTPUT,
        "",
    )
    assert max(peak) == 2
    assert completed == ["/docs/a-dir", "/docs/z-dir"]
    assert source.ls_calls == _listed(_TREE_LEVELS)


def test_tree_reports_the_first_failure_in_breadth_first_order() -> None:
    later_failed = asyncio.Event()

    async def fail_out_of_order(source: _TreeSource, path: str) -> None:
        del source
        if path == "/docs/a-dir":
            later_failed.set()
        elif path == "/docs/z-dir":
            await asyncio.wait_for(later_failed.wait(), timeout=5)
            for _ in range(5):
                await asyncio.sleep(0)

    listings = _listings(_TREE_ROWS)
    listings["/docs/z-dir"] = PermissionError("first")
    listings["/docs/a-dir"] = RuntimeError("second")
    source = _TreeSource(listings=listings, hook=fail_out_of_order)

    result = _invoke("tree", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "tree: memory:/docs: permission denied\n",
    )
    assert source.ls_calls == _listed(_TREE_LEVELS[:2])
    assert source.exit_calls[0][1] is listings["/docs/z-dir"]


def test_tree_fails_when_a_nested_directory_cannot_be_listed() -> None:
    denied = PermissionError("denied")
    listings = _listings(_TREE_ROWS)
    listings["/docs/a-dir/nested"] = denied
    source = _TreeSource(listings=listings)

    result = _invoke("tree", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "tree: memory:/docs: permission denied\n",
    )
    assert source.ls_calls == _listed(_TREE_LEVELS)
    assert source.lifecycle == ["factory", "enter", "exit"]
    assert source.exit_calls[0][1] is denied


def test_tree_drains_in_flight_sibling_listings_before_source_exit_on_cancellation(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    started = asyncio.Event()
    release = asyncio.Event()

    async def block(source: _TreeSource, path: str) -> None:
        if path == "/docs":
            return
        source.lifecycle.append(f"ls-start {path}")
        if len(source.lifecycle) == 4:
            started.set()
        await release.wait()
        source.lifecycle.append(f"ls-done {path}")

    source = _TreeSource(
        listings=_listings([("/docs", ["z-dir", "a-dir"], [])]),
        hook=block,
    )
    real_run = asyncio.run

    def cancelling_run(coroutine: Coroutine[object, object, None]) -> None:
        async def supervise() -> None:
            command_task = asyncio.create_task(coroutine)
            await asyncio.wait_for(started.wait(), timeout=5)
            command_task.cancel("original tree cancellation")
            asyncio.get_running_loop().call_later(0.01, release.set)
            await command_task

        real_run(supervise())

    monkeypatch.setattr(asyncio, "run", cancelling_run)

    with pytest.raises(asyncio.CancelledError) as caught:
        _invoke("tree", ["memory:/docs"], sources={"memory": source})

    assert type(caught.value) is asyncio.CancelledError
    assert source.lifecycle == [
        "factory",
        "enter",
        "ls-start /docs/z-dir",
        "ls-start /docs/a-dir",
        "ls-done /docs/z-dir",
        "ls-done /docs/a-dir",
        "exit",
    ]
    assert source.exit_calls[0][0] is asyncio.CancelledError


@pytest.mark.parametrize(
    ("arguments", "backend_depth", "stdout"),
    [
        (["--maxdepth", "0", "memory:/docs"], 1, "/docs\n"),
        (
            ["--maxdepth", "1", "memory:/docs"],
            1,
            "/docs\n├── a-dir\n├── z-dir\n├── a.txt\n└── z.txt\n",
        ),
        (
            ["--maxdepth", "2", "memory:/docs"],
            2,
            "/docs\n├── a-dir\n│   ├── nested\n│   └── b.txt\n├── z-dir\n├── a.txt\n└── z.txt\n",
        ),
        (
            ["--maxdepth", "3", "memory:/docs", "--maxdepth", "1"],
            1,
            "/docs\n├── a-dir\n├── z-dir\n├── a.txt\n└── z.txt\n",
        ),
        (
            ["--maxdepth", "0001", "--", "memory:/docs"],
            1,
            "/docs\n├── a-dir\n├── z-dir\n├── a.txt\n└── z.txt\n",
        ),
        (
            ["--maxdepth=1", "memory:/docs"],
            1,
            "/docs\n├── a-dir\n├── z-dir\n├── a.txt\n└── z.txt\n",
        ),
        (
            ["--maxdepth", "+1", "memory:/docs"],
            1,
            "/docs\n├── a-dir\n├── z-dir\n├── a.txt\n└── z.txt\n",
        ),
        (
            ["--maxdepth", "\u0661", "memory:/docs"],
            1,
            "/docs\n├── a-dir\n├── z-dir\n├── a.txt\n└── z.txt\n",
        ),
    ],
)
def test_tree_depth_contract_limits_listed_levels(
    arguments: list[str],
    backend_depth: int,
    stdout: str,
) -> None:
    source = _TreeSource(listings=_listings(_TREE_ROWS))

    result = _invoke("tree", arguments, sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (0, stdout, "")
    assert source.ls_calls == _listed(_TREE_LEVELS[:backend_depth])


@pytest.mark.parametrize(
    ("listings", "operand"),
    [
        ({"/empty": []}, "memory:/empty"),
        ({"/file.txt": [_entry("/file.txt")]}, "memory:/file.txt"),
        ({"/file.txt": [_entry("/file.txt/")]}, "memory:/file.txt"),
    ],
)
def test_tree_empty_directory_and_file_root_render_only_the_operand(
    listings: dict[str, object],
    operand: str,
) -> None:
    source = _TreeSource(listings=listings)

    result = _invoke("tree", [operand], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        f"{operand.partition(':')[2]}\n",
        "",
    )
    assert [call[0] for call in source.ls_calls] == [operand.partition(":")[2]]


@pytest.mark.parametrize(
    ("operand", "listings", "stdout"),
    [
        (
            "memory:/",
            {"/": [_entry("/docs", "directory")], "/docs": []},
            "/\n└── docs\n",
        ),
        (
            "memory:/docs/",
            {"/docs/": [_entry("/docs/a.txt")]},
            "/docs/\n└── a.txt\n",
        ),
        (
            "memory:/docs",
            {"/docs": [_entry("/docs/sub/", "directory")], "/docs/sub": []},
            "/docs\n└── sub\n",
        ),
    ],
)
def test_tree_preserves_operand_root_spelling_after_relationship_validation(
    operand: str,
    listings: dict[str, object],
    stdout: str,
) -> None:
    source = _TreeSource(listings=listings)

    result = _invoke("tree", [operand], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (0, stdout, "")


def test_tree_renders_a_valid_chain_deeper_than_the_python_recursion_limit() -> None:
    depth = sys.getrecursionlimit() + 25
    rows: list[tuple[str, list[str], list[str]]] = []
    path = "/root"
    for index in range(depth):
        name = f"d{index}"
        rows.append((path, [name], []))
        path = f"{path}/{name}"
    rows.append((path, [], ["leaf"]))
    source = _TreeSource(listings=_listings(rows))
    expected = "/root\n" + "".join(f"{'    ' * index}└── d{index}\n" for index in range(depth))
    expected += f"{'    ' * depth}└── leaf\n"

    result = _invoke("tree", ["memory:/root"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (0, expected, "")
    assert source.ls_calls == _listed([[row[0]] for row in rows])


def test_tree_orders_each_group_by_locale_then_raw(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = _TreeSource(
        listings=_listings(
            [
                ("/docs", ["z-dir", "a-dir"], ["z", "a"]),
                ("/docs/z-dir", [], []),
                ("/docs/a-dir", [], []),
            ]
        )
    )
    transformed = {
        "z-dir": "directory",
        "a-dir": "directory",
        "z": "file",
        "a": "file",
    }
    monkeypatch.setattr(locale, "strxfrm", transformed.__getitem__)

    result = _invoke("tree", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "/docs\n├── a-dir\n├── z-dir\n├── a\n└── z\n",
        "",
    )


@pytest.mark.parametrize("arguments", [["--help"], ["--maxdepth", "2", "--help"]])
def test_tree_help_comes_from_typed_callback(arguments: list[str]) -> None:
    result = _invoke("tree", arguments)

    help_text = strip_ansi(result.stdout)
    assert (result.exit_code, result.stderr) == (0, "")
    assert "Usage: root tree [OPTIONS] {name:/path}" in help_text
    assert "Display a recursive directory tree" in help_text
    assert "name:/path" in help_text
    assert "--maxdepth" in help_text
    assert "N [x>=0]" in help_text


@pytest.mark.parametrize(
    ("arguments", "contexts"),
    [
        ([], ("Missing argument", "name:/path")),
        (["-L", "1", "memory:/docs"], ("No such option", "-L")),
        (["--maxdepth"], ("requires an argument", "--maxdepth")),
        (
            ["--maxdepth", "-1", "memory:/docs"],
            ("Invalid value", "--maxdepth", "x>=0"),
        ),
        (
            ["--maxdepth", "1.0", "memory:/docs"],
            ("Invalid value", "--maxdepth", "int range"),
        ),
        (
            ["memory:/a", "memory:/b"],
            ("unexpected extra argument", "memory:/b"),
        ),
        (["--", "--help"], ("tree: --help: invalid mapped filesystem operand",)),
    ],
)
def test_tree_usage_failures_are_typer_owned_and_source_free(
    arguments: list[str],
    contexts: tuple[str, ...],
) -> None:
    result = _invoke("tree", arguments)

    assert (result.exit_code, result.stdout) == (2, "")
    diagnostic = strip_ansi(result.stderr)
    for context in contexts:
        assert context in diagnostic


def test_tree_rejects_a_runtime_oversized_depth_deterministically() -> None:
    value = "9" * 5000

    result = _invoke("tree", ["--maxdepth", value, "memory:/docs"])

    assert (result.exit_code, result.stdout) == (2, "")
    diagnostic = strip_ansi(result.stderr)
    assert "Invalid value" in diagnostic
    assert "--maxdepth" in diagnostic


@pytest.mark.parametrize(
    "listings",
    [
        {"/docs": None},
        {"/docs": ()},
        {"/docs": {}},
        {"/docs": "listing"},
        {"/docs": [None]},
        {"/docs": [("/docs/a", "file")]},
        {"/docs": [{"type": "file"}]},
        {"/docs": [{"name": "/docs/a"}]},
        {"/docs": [_entry(1)]},
        {"/docs": [_entry("/docs/a", None)]},
        {"/docs": [_LyingLengthEntry(_entry("/docs/a"))]},
        {"/docs": [_ExplodingEntry(_entry("/docs/a"))]},
        {"/docs": [_entry("/wrong/a")]},
        {"/docs": [_entry("/docs/sub/a")]},
        {"/docs": [_entry("/docs/bad\nname")]},
        {"/docs": [_entry("/docs/bad\0name", "directory")], "/docs/bad\0name": []},
        {"/docs": [_entry("/docs/same"), _entry("/docs/same")]},
        {"/docs": [_entry("/docs/same", "directory"), _entry("/docs/same/")]},
        {"/docs": [_entry("/docs"), _entry("/docs/other")]},
        {"/docs": [_entry("/docs/sub", "directory")], "/docs/sub": None},
        {"/docs": [_entry("/docs/sub", "directory")], "/docs/sub": [_entry("/x")]},
    ],
)
def test_tree_rejects_malformed_or_impossible_listings_atomically(
    listings: dict[str, object],
) -> None:
    source = _TreeSource(listings=listings)

    result = _invoke("tree", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "tree: memory:/docs: incompatible result\n",
    )
    assert source.lifecycle == ["factory", "enter", "exit"]


def test_tree_rejects_a_synchronous_listing_hook_as_unsupported() -> None:
    class _SyncListingFileSystem(_TreeFileSystem):
        def _ls(  # type: ignore[override]
            self,
            path: str,
            detail: bool = True,  # noqa: FBT002 - matches the fsspec hook signature.
            **kwargs: object,
        ) -> object:
            self.source.ls_calls.append((path, detail, kwargs))
            return []

    source = _TreeSource()

    class _SyncContext(_TreeContext):
        def __init__(self, source: _TreeSource) -> None:
            super().__init__(source)
            self.filesystem = _SyncListingFileSystem(source)

    def factory() -> _TreeContext:
        source.lifecycle.append("factory")
        return _SyncContext(source)

    result = _invoke("tree", ["memory:/docs"], sources={"memory": factory})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "tree: memory:/docs: unsupported operation\n",
    )
    assert source.ls_calls == [("/docs", True, {})]
    assert source.lifecycle == ["factory", "enter", "exit"]


def _failing_source(stage: str, error: BaseException) -> _TreeSource:
    listings = _listings([("/docs", ["sub"], []), ("/docs/sub", [], [])])
    if stage == "await":
        listings["/docs"] = error
    elif stage == "nested":
        listings["/docs/sub"] = error
    return _TreeSource(
        listings=listings,
        invoke_error=error if stage == "invoke" else None,
    )


@pytest.mark.parametrize(
    ("stage", "error"),
    [
        ("invoke", RuntimeError("invoke")),
        ("await", RuntimeError("await")),
        ("nested", RuntimeError("nested")),
    ],
)
def test_tree_reports_every_ordinary_listing_failure_as_backend_failure(
    stage: str,
    error: Exception,
) -> None:
    source = _failing_source(stage, error)

    result = _invoke("tree", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        f"tree: memory:/docs: backend failure (RuntimeError): {error}\n",
    )
    assert source.exit_calls[0][1] is error


@pytest.mark.parametrize("stage", ["invoke", "await", "nested"])
def test_tree_cleans_up_then_preserves_listing_control_flow(stage: str) -> None:
    control = _TreeControl(stage)
    source = _failing_source(stage, control)

    with pytest.raises(_TreeControl) as caught:
        _invoke("tree", ["memory:/docs"], sources={"memory": source})

    assert caught.value is control
    assert source.lifecycle == ["factory", "enter", "exit"]
    assert source.exit_calls[0][1] is control


@pytest.mark.parametrize(
    "control",
    [
        asyncio.CancelledError("listing cancellation"),
        KeyboardInterrupt("listing interrupt"),
        SystemExit(23),
    ],
)
def test_tree_preserves_exact_nested_listing_control_flow_through_public_app(
    control: BaseException,
) -> None:
    source = _failing_source("nested", control)
    app = App({"memory": source})
    command = get_command(app.typer_app)

    with (
        command.make_context("fs", ["tree", "memory:/docs"]) as context,
        pytest.raises(type(control)) as caught,
    ):
        command.invoke(context)

    if isinstance(control, asyncio.CancelledError):
        assert type(caught.value) is asyncio.CancelledError
    else:
        assert caught.value is control
        assert caught.value.args == control.args
    assert source.lifecycle == ["factory", "enter", "exit"]
    assert source.exit_calls[0][1] is control
    assert source.exit_calls[0][1].args == control.args


def test_tree_cleans_up_after_output_failure(monkeypatch: pytest.MonkeyPatch) -> None:
    output_error = OSError("write failed")
    source = _TreeSource()
    real_echo = typer.echo

    def fail_stdout(
        message: object = None,
        *_args: object,
        **kwargs: object,
    ) -> None:
        if kwargs.get("err") is True:
            real_echo(message, err=True)
            return
        raise output_error

    monkeypatch.setattr(typer, "echo", fail_stdout)
    result = _invoke("tree", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "tree: output: output failure (OSError): write failed\n",
    )
    assert source.exit_calls[0][1] is output_error


def test_tree_retains_complete_output_when_source_exit_fails() -> None:
    source = _TreeSource(exit_error=OSError("cleanup"))

    result = _invoke("tree", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "/docs\n",
        "tree: memory: source exit failure (OSError): cleanup\n",
    )
