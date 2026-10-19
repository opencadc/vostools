"""``find`` command tests through the public embedded-command seam."""

import locale
from collections.abc import Iterator, Mapping
from types import MappingProxyType

import pytest
import typer

from ._ansi import strip_ansi
from ._support import _invoke, _RecordingSource


class _ListSubclass(list[object]):
    pass


class _ExplodingInfo(dict[str, object]):
    def get(self, key: str, default: object = None) -> object:
        del key, default
        raise RuntimeError


class _ExplodingEntry(dict[str, object]):
    def __iter__(self) -> Iterator[str]:
        raise RuntimeError


class _LyingLengthEntry(dict[str, object]):
    def __len__(self) -> int:
        return super().__len__() + 1


class _FindControl(BaseException):
    pass


def _entry(name: object, kind: object = "file") -> dict[str, object]:
    return {"name": name, "type": kind, "size": 0}


_DOCS_INFO = MappingProxyType({"name": "/docs", "type": "directory", "size": 0})

_DOCS_LISTINGS: Mapping[str, object] = MappingProxyType(
    {
        "/docs": [
            _entry("/docs/sub", "directory"),
            _entry("/docs/a.txt"),
            _entry("/docs/link", "other"),
            _entry("/docs/empty", "directory"),
        ],
        "/docs/sub": [_entry("/docs/sub/b.txt")],
        "/docs/empty": [],
    }
)


def _source(
    listings: Mapping[str, object] | None = None,
    *,
    info: Mapping[str, object] | None = None,
    exit_error: BaseException | None = None,
) -> tuple[list[tuple[object, ...]], _RecordingSource]:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        ls_by_path=_DOCS_LISTINGS if listings is None else listings,
        ls_error=FileNotFoundError(),
        info_by_path={"/docs": _DOCS_INFO} if info is None else info,
        exit_error=exit_error,
    )
    return events, source


def _calls(events: list[tuple[object, ...]]) -> list[tuple[object, ...]]:
    return [(event[0], *event[2:-1]) for event in events if event[0] in {"ls", "info"}]


def _listed(*paths: str) -> list[tuple[object, ...]]:
    return [("ls", path, True) for path in paths]


def test_find_renders_recursive_file_paths_after_one_listing_per_directory() -> None:
    events, source = _source()

    result = _invoke("find", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "/docs/a.txt\n/docs/link\n/docs/sub/b.txt\n",
        "",
    )
    assert [(event[0], *event[2:-1]) for event in events] == [
        ("factory",),
        ("enter",),
        *_listed("/docs", "/docs/sub", "/docs/empty"),
        ("exit",),
    ]


def test_find_fails_when_a_nested_directory_cannot_be_listed() -> None:
    denied = PermissionError("denied")
    events, source = _source({**_DOCS_LISTINGS, "/docs/empty": denied})

    result = _invoke("find", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "find: memory:/docs: permission denied\n",
    )
    assert _calls(events) == _listed("/docs", "/docs/sub", "/docs/empty")
    assert source.exit_calls[0][1] is denied


def test_find_directories_fails_when_a_nested_directory_cannot_be_listed() -> None:
    denied = PermissionError("denied")
    events, source = _source({**_DOCS_LISTINGS, "/docs/sub": denied})

    result = _invoke(
        "find",
        ["--type", "d", "memory:/docs"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "find: memory:/docs: permission denied\n",
    )
    assert _calls(events) == [
        ("info", "/docs"),
        *_listed("/docs", "/docs/sub", "/docs/empty"),
    ]
    assert source.exit_calls[0][1] is denied


@pytest.mark.parametrize("arguments", [["memory:/missing"], ["--type", "d", "memory:/missing"]])
def test_find_reports_a_missing_operand_as_not_found(arguments: list[str]) -> None:
    _events, source = _source(info={"/missing": FileNotFoundError()})

    result = _invoke("find", arguments, sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "find: memory:/missing: not found\n",
    )
    assert source.exit_calls[0][0] is FileNotFoundError


def test_find_orders_paths_by_locale_then_raw_spelling(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    _events, source = _source(
        {
            "/docs": [
                _entry("/docs/b.txt"),
                _entry("/docs/z.txt"),
                _entry("/docs/a.txt"),
            ]
        }
    )
    transformed = {
        "/docs/z.txt": "0",
        "/docs/a.txt": "1",
        "/docs/b.txt": "1",
    }
    monkeypatch.setattr(locale, "strxfrm", transformed.__getitem__)

    result = _invoke("find", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "/docs/z.txt\n/docs/a.txt\n/docs/b.txt\n",
        "",
    )


def test_find_does_not_misclassify_an_internal_locale_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    internal_error = RuntimeError("locale failure")
    _events, source = _source({"/docs": [_entry("/docs/a.txt")]})

    def fail_locale(_path: str) -> str:
        raise internal_error

    monkeypatch.setattr(locale, "strxfrm", fail_locale)
    result = _invoke("find", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (1, "", "")
    assert result.exception is internal_error
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is RuntimeError
    assert exception is internal_error
    assert traceback is not None


_ALL_FILES = "/docs/a.txt\n/docs/link\n/docs/sub/b.txt\n"
_DIRECT_FILES = "/docs/a.txt\n/docs/link\n"
_RECURSIVE = _listed("/docs", "/docs/sub", "/docs/empty")


@pytest.mark.parametrize(
    ("arguments", "calls", "stdout"),
    [
        (["--maxdepth", "2", "memory:/docs"], _RECURSIVE, _ALL_FILES),
        (["--maxdepth", "0002", "memory:/docs"], _RECURSIVE, _ALL_FILES),
        (["memory:/docs", "--type", "f"], _RECURSIVE, _ALL_FILES),
        (
            ["--type", "d", "memory:/docs"],
            [("info", "/docs"), *_RECURSIVE],
            "/docs\n/docs/empty\n/docs/sub\n",
        ),
        (
            ["--maxdepth", "3", "memory:/docs", "--maxdepth", "1"],
            _listed("/docs"),
            _DIRECT_FILES,
        ),
        (["--type", "d", "--type", "f", "memory:/docs"], _RECURSIVE, _ALL_FILES),
        (["--", "memory:/docs"], _RECURSIVE, _ALL_FILES),
        (["--maxdepth=1", "memory:/docs"], _listed("/docs"), _DIRECT_FILES),
        (["--maxdepth", "+1", "memory:/docs"], _listed("/docs"), _DIRECT_FILES),
        (
            ["--maxdepth", "\u0661", "--type=f", "memory:/docs"],
            _listed("/docs"),
            _DIRECT_FILES,
        ),
    ],
)
def test_find_accepts_locked_interspersed_options_and_call_shapes(
    arguments: list[str],
    calls: list[tuple[object, ...]],
    stdout: str,
) -> None:
    events, source = _source()

    result = _invoke("find", arguments, sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (0, stdout, "")
    assert _calls(events) == calls


@pytest.mark.parametrize(
    ("arguments", "listings", "info", "calls", "stdout"),
    [
        (
            ["--maxdepth", "0", "memory:/docs"],
            {"/docs": [_entry("/docs/child.txt")]},
            None,
            _listed("/docs"),
            "",
        ),
        (
            ["--maxdepth", "0", "--type", "f", "memory:/file.txt"],
            {"/file.txt": [_entry("/file.txt")]},
            None,
            _listed("/file.txt"),
            "/file.txt\n",
        ),
        (
            ["--type", "d", "--maxdepth", "0", "memory:/docs/"],
            {"/docs/": [_entry("/docs/child", "directory")]},
            {"/docs/": _DOCS_INFO},
            [("info", "/docs/"), *_listed("/docs/")],
            "/docs\n",
        ),
    ],
)
def test_find_maxdepth_zero_lists_one_level_and_filters_to_the_root(
    arguments: list[str],
    listings: dict[str, object],
    info: dict[str, object] | None,
    calls: list[tuple[object, ...]],
    stdout: str,
) -> None:
    events, source = _source(listings, info=info)

    result = _invoke("find", arguments, sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (0, stdout, "")
    assert _calls(events) == calls


@pytest.mark.parametrize("arguments", [["--help"], ["--type", "d", "--help"]])
def test_find_help_comes_from_typed_callback(arguments: list[str]) -> None:
    result = _invoke("find", arguments)

    help_text = strip_ansi(result.stdout)
    assert (result.exit_code, result.stderr) == (0, "")
    assert "Usage: root find [OPTIONS] {name:/path}" in help_text
    assert "Find files recursively" in help_text
    assert "name:/path" in help_text
    assert "--maxdepth" in help_text
    assert "--type" in help_text
    assert "f|d" in help_text


@pytest.mark.parametrize(
    ("arguments", "contexts"),
    [
        ([], ("Missing argument", "name:/path")),
        (["-x", "memory:/docs"], ("No such option", "-x")),
        (["--maxdepth"], ("requires an argument", "--maxdepth")),
        (["--type"], ("requires an argument", "--type")),
        (
            ["--maxdepth", "-1", "memory:/docs"],
            ("Invalid value", "--maxdepth", "x>=0"),
        ),
        (
            ["--maxdepth", "1.0", "memory:/docs"],
            ("Invalid value", "--maxdepth", "int range"),
        ),
        (
            ["--maxdepth", "", "memory:/docs"],
            ("Invalid value", "--maxdepth", "int range"),
        ),
        (
            ["--maxdepth", " ", "memory:/docs"],
            ("Invalid value", "--maxdepth", "int range"),
        ),
        (
            ["--type", "x", "memory:/docs"],
            ("Invalid value", "--type", "not one of", "f", "d"),
        ),
        (
            ["memory:relative"],
            ("find: memory:relative: invalid mapped filesystem operand",),
        ),
        (
            ["unknown:/docs"],
            ("find: unknown:/docs: unknown filesystem (known: memory)",),
        ),
        (
            ["memory:/a", "memory:/b"],
            ("unexpected extra argument", "memory:/b"),
        ),
        (
            ["--", "--help"],
            ("find: --help: invalid mapped filesystem operand",),
        ),
    ],
)
def test_find_usage_failures_are_typer_owned_and_source_free(
    arguments: list[str],
    contexts: tuple[str, ...],
) -> None:
    result = _invoke("find", arguments)

    assert (result.exit_code, result.stdout) == (2, "")
    diagnostic = strip_ansi(result.stderr)
    for context in contexts:
        assert context in diagnostic


def test_find_rejects_a_depth_too_large_for_the_runtime_deterministically() -> None:
    value = "9" * 5000

    result = _invoke("find", ["--maxdepth", value, "memory:/docs"])

    assert (result.exit_code, result.stdout) == (2, "")
    diagnostic = strip_ansi(result.stderr)
    assert "Invalid value" in diagnostic
    assert "--maxdepth" in diagnostic


@pytest.mark.parametrize(
    "listings",
    [
        {"/docs": None},
        {"/docs": ()},
        {"/docs": "docs/a.txt"},
        {"/docs": {"/docs/a.txt": {}}},
        {"/docs": [1]},
        {"/docs": [_entry(1)]},
        {"/docs": [_entry("/docs/a.txt", None)]},
        {"/docs": [{"name": "/docs/a.txt"}]},
        {"/docs": [_ExplodingEntry(_entry("/docs/a.txt"))]},
        {"/docs": [_entry("/docs/bad\nname")]},
        {"/docs": [_entry("/docs/bad\0name")]},
        {"/docs": [_entry("/elsewhere/a.txt")]},
        {"/docs": [_entry("/docs/a.txt"), _entry("/docs/a.txt")]},
        {"/docs": [_entry("/docs/sub", "directory")], "/docs/sub": None},
        {
            "/docs": [_entry("/docs/sub", "directory")],
            "/docs/sub": [_entry("/docs/sub/bad\nname")],
        },
    ],
)
def test_find_rejects_incompatible_file_listings_atomically(
    listings: dict[str, object],
) -> None:
    _events, source = _source(listings)

    result = _invoke("find", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "find: memory:/docs: incompatible result\n",
    )


@pytest.mark.parametrize(
    ("info", "listing"),
    [
        (None, []),
        ([], []),
        ({"type": "directory"}, []),
        ({"name": 1, "type": "directory"}, []),
        ({"name": "/docs"}, []),
        ({"name": "/docs", "type": True}, []),
        (_ExplodingInfo({"name": "/docs", "type": "directory"}), []),
        ({"name": "/docs\nbad", "type": "directory"}, []),
        (_DOCS_INFO, [_entry("/docs/bad\nname", "directory")]),
        (_DOCS_INFO, [_entry("/docs/bad\0name", "directory")]),
        (_DOCS_INFO, [_entry("/docs/a.txt", None)]),
        (_DOCS_INFO, None),
    ],
)
def test_find_rejects_incompatible_directory_results_atomically(
    info: object,
    listing: object,
) -> None:
    _events, source = _source(
        {
            "/docs": listing,
            "/docs/bad\nname": [],
            "/docs/bad\0name": [],
        },
        info={"/docs": info},
    )

    result = _invoke(
        "find",
        ["--type", "d", "memory:/docs"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "find: memory:/docs: incompatible result\n",
    )


def test_find_rejects_an_inconsistent_listing_entry_atomically_and_cleans_up() -> None:
    events, source = _source(
        {
            "/docs": [
                _entry("/docs/good", "directory"),
                _LyingLengthEntry(_entry("/docs/bad", "directory")),
            ]
        }
    )

    result = _invoke(
        "find",
        ["--type", "d", "memory:/docs"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "find: memory:/docs: incompatible result\n",
    )
    assert [(event[0], *event[2:-1]) for event in events] == [
        ("factory",),
        ("enter",),
        ("info", "/docs"),
        *_listed("/docs"),
        ("exit",),
    ]


def test_find_validates_the_complete_result_before_output() -> None:
    _events, source = _source({"/docs": [_entry("/docs/good"), _entry("/docs/bad\nname")]})

    result = _invoke("find", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "find: memory:/docs: incompatible result\n",
    )


@pytest.mark.parametrize(
    ("error", "diagnostic"),
    [
        (FileNotFoundError(), "not found"),
        (PermissionError(), "permission denied"),
        (NotADirectoryError(), "not a directory"),
        (NotImplementedError(), "unsupported operation"),
        (
            RuntimeError("bad\\\0\n"),
            r"backend failure (RuntimeError): bad\\\x00\x0a",
        ),
    ],
)
def test_find_reports_backend_failures_and_passes_them_to_cleanup(
    error: Exception,
    diagnostic: str,
) -> None:
    _events, source = _source({"/docs": error})

    result = _invoke("find", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        f"find: memory:/docs: {diagnostic}\n",
    )
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is type(error)
    assert exception is error
    assert traceback is not None


def test_find_cleans_up_after_an_output_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    output_error = OSError("write failed")
    _events, source = _source({"/docs": [_entry("/docs/a")]})
    real_echo = typer.echo

    def fail_stdout(
        message: object = None,
        *args: object,
        **kwargs: object,
    ) -> None:
        if kwargs.get("err") is True:
            real_echo(message, *args, **kwargs)
            return
        raise output_error

    monkeypatch.setattr(typer, "echo", fail_stdout)
    result = _invoke("find", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "find: output: output failure (OSError): write failed\n",
    )
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is OSError
    assert exception is output_error
    assert traceback is not None


def test_find_keeps_broken_pipe_silent_and_cleans_up(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    broken_pipe = BrokenPipeError()
    _events, source = _source({"/docs": [_entry("/docs/a")]})

    def break_stdout(*args: object, **kwargs: object) -> None:
        del args, kwargs
        raise broken_pipe

    monkeypatch.setattr(typer, "echo", break_stdout)
    result = _invoke("find", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (1, "", "")
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is BrokenPipeError
    assert exception is broken_pipe
    assert traceback is not None


def test_find_retains_complete_output_when_source_exit_fails() -> None:
    _events, source = _source(
        {"/docs": [_entry("/docs/a")]},
        exit_error=OSError("cleanup"),
    )

    result = _invoke("find", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "/docs/a\n",
        "find: memory: source exit failure (OSError): cleanup\n",
    )


@pytest.mark.parametrize("stage", ["root", "nested", "info"])
def test_find_cleans_up_then_propagates_backend_control_flow(stage: str) -> None:
    control = _FindControl("stop")
    listings: dict[str, object] = {
        "/docs": [_entry("/docs/sub", "directory")],
        "/docs/sub": [],
    }
    info: dict[str, object] = {"/docs": _DOCS_INFO}
    if stage == "root":
        listings["/docs"] = control
    elif stage == "nested":
        listings["/docs/sub"] = control
    else:
        info["/docs"] = control
    events, source = _source(listings, info=info, exit_error=OSError("cleanup"))

    with pytest.raises(_FindControl) as caught:
        _invoke(
            "find",
            ["--type", "d", "memory:/docs"],
            sources={"memory": source},
        )

    assert caught.value is control
    assert events[-1][0] == "exit"
    assert source.exit_calls[0][1] is control
