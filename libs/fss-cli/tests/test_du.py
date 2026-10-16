"""``du`` command tests through the public embedded-command seam."""

from collections.abc import Iterator, Mapping
from types import MappingProxyType

import pytest
import typer

from ._ansi import strip_ansi
from ._support import _invoke, _RecordingSource


class _ExplodingEntry(dict[str, object]):
    def __iter__(self) -> Iterator[str]:
        raise RuntimeError


class _DuControl(BaseException):
    pass


def _file(name: object, size: object) -> dict[str, object]:
    return {"name": name, "type": "file", "size": size}


def _directory(name: str, size: object = 0) -> dict[str, object]:
    return {"name": name, "type": "directory", "size": size}


def _source(
    listings: Mapping[str, object],
    *,
    exit_error: BaseException | None = None,
) -> tuple[list[tuple[object, ...]], _RecordingSource]:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(
        events,
        ls_by_path=listings,
        ls_error=FileNotFoundError(),
        exit_error=exit_error,
    )
    return events, source


def _events(events: list[tuple[object, ...]]) -> list[tuple[object, ...]]:
    return [(event[0], *event[2:-1]) for event in events]


_DOCS_LISTINGS: Mapping[str, object] = MappingProxyType(
    {
        "/docs": [
            _directory("/docs/sub", 4096),
            _file("/docs/a.txt", 2),
            {"name": "/docs/link", "type": "other", "size": 0},
        ],
        "/docs/sub": [_file("/docs/sub/b.bin", 1536)],
    }
)


def test_du_renders_exact_listed_file_sizes_after_one_listing_per_directory() -> None:
    events, source = _source(_DOCS_LISTINGS)

    result = _invoke("du", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "2\t/docs/a.txt\n0\t/docs/link\n1536\t/docs/sub/b.bin\n",
        "",
    )
    assert _events(events) == [
        ("factory",),
        ("enter",),
        ("ls", "/docs", True),
        ("ls", "/docs/sub", True),
        ("exit",),
    ]


def test_du_fails_when_a_nested_directory_cannot_be_listed() -> None:
    denied = PermissionError("denied")
    events, source = _source({**_DOCS_LISTINGS, "/docs/sub": denied})

    result = _invoke("du", ["-s", "memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "du: memory:/docs: permission denied\n",
    )
    assert _events(events) == [
        ("factory",),
        ("enter",),
        ("ls", "/docs", True),
        ("ls", "/docs/sub", True),
        ("exit",),
    ]
    assert source.exit_calls[0][1] is denied


@pytest.mark.parametrize("arguments", [["memory:/missing"], ["-s", "memory:/missing"]])
def test_du_reports_a_missing_operand_as_not_found(arguments: list[str]) -> None:
    _events_list, source = _source(_DOCS_LISTINGS)

    result = _invoke("du", arguments, sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "du: memory:/missing: not found\n",
    )
    assert source.exit_calls[0][0] is FileNotFoundError


def test_du_accepts_an_empty_listing_without_output() -> None:
    events, source = _source({"/empty": []})

    result = _invoke("du", ["memory:/empty"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert _events(events) == [
        ("factory",),
        ("enter",),
        ("ls", "/empty", True),
        ("exit",),
    ]


def test_du_reports_a_file_operand_from_its_own_listing_entry() -> None:
    events, source = _source({"/file": [_file("/file", 3)]})

    result = _invoke("du", ["memory:/file"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (0, "3\t/file\n", "")
    assert [event for event in _events(events) if event[0] == "ls"] == [("ls", "/file", True)]


@pytest.mark.parametrize(
    ("arguments", "stdout"),
    [
        (["-h", "memory:/docs"], "1.5K\t/docs/a\n"),
        (["-s", "memory:/docs"], "1536\t/docs\n"),
        (["-sh", "memory:/docs"], "1.5K\t/docs\n"),
        (["-hhs", "memory:/docs"], "1.5K\t/docs\n"),
        (["memory:/docs", "-s", "-h"], "1.5K\t/docs\n"),
        (["--", "memory:/docs"], "1536\t/docs/a\n"),
        (["-s", "memory:/docs/"], "1536\t/docs/\n"),
    ],
)
def test_du_accepts_grouped_repeated_and_interspersed_options(
    arguments: list[str],
    stdout: str,
) -> None:
    listing = [_file("/docs/a", 1536), _directory("/docs/empty", 4096)]
    events, source = _source({"/docs": listing, "/docs/": listing, "/docs/empty": []})

    result = _invoke("du", arguments, sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (0, stdout, "")
    operand = next(argument for argument in arguments if argument.startswith("memory:"))
    root = operand.partition(":")[2]
    assert [event for event in _events(events) if event[0] == "ls"] == [
        ("ls", root, True),
        ("ls", "/docs/empty", True),
    ]


@pytest.mark.parametrize("arguments", [["--help"], ["-s", "--help"]])
def test_du_help_comes_from_typed_callback(arguments: list[str]) -> None:
    result = _invoke("du", arguments)

    plain_help = strip_ansi(result.stdout)
    assert (result.exit_code, result.stderr) == (0, "")
    assert "Usage: root du [OPTIONS] {name:/path}" in plain_help
    assert "Estimate file space usage" in plain_help
    assert "name:/path" in plain_help
    assert "-s" in plain_help
    assert "-h" in plain_help


@pytest.mark.parametrize(
    ("arguments", "contexts"),
    [
        ([], ("Missing argument", "name:/path")),
        (["-x", "memory:/docs"], ("No such option", "-x")),
        (["-sx", "memory:/docs"], ("No such option", "-x")),
        (["--summary", "memory:/docs"], ("No such option", "--summary")),
        (["--help=value", "memory:/docs"], ("does not take a value", "--help")),
        (
            ["memory:relative"],
            ("du: memory:relative: invalid mapped filesystem operand",),
        ),
        (
            ["unknown:/docs"],
            ("du: unknown:/docs: unknown filesystem (known: memory)",),
        ),
        (
            ["memory:/a", "memory:/b"],
            ("unexpected extra argument", "memory:/b"),
        ),
        (["--", "--help"], ("du: --help: invalid mapped filesystem operand",)),
    ],
)
def test_du_usage_failures_are_typer_owned_and_source_free(
    arguments: list[str],
    contexts: tuple[str, ...],
) -> None:
    result = _invoke("du", arguments)

    assert (result.exit_code, result.stdout) == (2, "")
    diagnostic = strip_ansi(result.stderr)
    for context in contexts:
        assert context in diagnostic


def test_du_validates_dash_operand_after_option_terminator() -> None:
    result = _invoke("du", ["--", "-"])

    assert (result.exit_code, result.stdout, result.stderr) == (
        2,
        "",
        "du: -: invalid mapped filesystem operand\n",
    )


_INCOMPATIBLE_LISTINGS: list[dict[str, object]] = [
    {"/docs": None},
    {"/docs": 3},
    {"/docs": {"/docs/a": 1}},
    {"/docs": [("/docs/a", 1)]},
    {"/docs": [_file(1, 2)]},
    {"/docs": [{"name": "/docs/a", "size": 1}]},
    {"/docs": [{"name": "/docs/a", "type": "file"}]},
    {"/docs": [_file("/docs/a", None)]},
    {"/docs": [{"name": "/docs/a", "type": "file", "size": True}]},
    {"/docs": [_file("/docs/a", -1)]},
    {"/docs": [_file("/docs/a", 1.5)]},
    {"/docs": [_file("/docs/a", "1")]},
    {"/docs": [_file("/docs/bad\nname", 1)]},
    {"/docs": [_file("/docs/bad\0name", 1)]},
    {"/docs": [_file("/elsewhere/a", 1)]},
    {"/docs": [_file("/docs/a", 1), _file("/docs/a", 1)]},
    {"/docs": [_ExplodingEntry(_file("/docs/a", 1))]},
    {"/docs": [_directory("/docs/sub")], "/docs/sub": [_file("/docs/sub/a", -1)]},
    {"/docs": [_directory("/docs/sub")], "/docs/sub": None},
]


@pytest.mark.parametrize("listings", _INCOMPATIBLE_LISTINGS)
def test_du_rejects_incompatible_detail_listings_atomically(
    listings: dict[str, object],
) -> None:
    _events_list, source = _source(listings)

    result = _invoke("du", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "du: memory:/docs: incompatible result\n",
    )


@pytest.mark.parametrize("listings", _INCOMPATIBLE_LISTINGS)
def test_du_rejects_incompatible_summary_listings(
    listings: dict[str, object],
) -> None:
    _events_list, source = _source(listings)

    result = _invoke("du", ["-s", "memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "du: memory:/docs: incompatible result\n",
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
def test_du_reports_backend_failures_and_passes_them_to_cleanup(
    error: Exception,
    diagnostic: str,
) -> None:
    _events_list, source = _source({"/docs": error})

    result = _invoke("du", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        f"du: memory:/docs: {diagnostic}\n",
    )
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is type(error)
    assert exception is error
    assert traceback is not None


def test_du_validates_the_complete_listing_before_output() -> None:
    _events_list, source = _source({"/docs": [_file("/docs/good", 1), _file("/docs/bad", -1)]})

    result = _invoke("du", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "du: memory:/docs: incompatible result\n",
    )


def test_du_cleans_up_after_an_output_failure(monkeypatch: pytest.MonkeyPatch) -> None:
    output_error = OSError("write failed")
    _events_list, source = _source({"/docs": [_file("/docs/a", 1)]})
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
    result = _invoke("du", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "du: output: output failure (OSError): write failed\n",
    )
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is OSError
    assert exception is output_error
    assert traceback is not None


def test_du_keeps_broken_pipe_silent_and_cleans_up(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    broken_pipe = BrokenPipeError()
    _events_list, source = _source({"/docs": [_file("/docs/a", 1)]})

    def break_stdout(*args: object, **kwargs: object) -> None:
        del args, kwargs
        raise broken_pipe

    monkeypatch.setattr(typer, "echo", break_stdout)
    result = _invoke("du", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (1, "", "")
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is BrokenPipeError
    assert exception is broken_pipe
    assert traceback is not None


def test_du_retains_complete_output_when_source_exit_fails() -> None:
    _events_list, source = _source(
        {"/docs": [_file("/docs/a", 1)]},
        exit_error=OSError("cleanup"),
    )

    result = _invoke("du", ["memory:/docs"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "1\t/docs/a\n",
        "du: memory: source exit failure (OSError): cleanup\n",
    )


@pytest.mark.parametrize("nested", [False, True])
def test_du_cleans_up_then_propagates_backend_control_flow(nested: bool) -> None:
    control = _DuControl("stop")
    listings: dict[str, object] = {"/docs": control}
    if nested:
        listings = {"/docs": [_directory("/docs/sub")], "/docs/sub": control}
    events, source = _source(listings, exit_error=OSError("cleanup"))

    with pytest.raises(_DuControl) as caught:
        _invoke("du", ["memory:/docs"], sources={"memory": source})

    assert caught.value is control
    assert events[-1][0] == "exit"
    assert source.exit_calls[0][1] is control
