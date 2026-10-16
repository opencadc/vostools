"""Source acquisition and one-file tracer tests through the public seam."""

import asyncio
from typing import NoReturn

import pytest
from fsspec.asyn import AsyncFileSystem

from ._support import _invoke, _RecordingSource, _source_must_not_run


def test_ls_traces_one_file_on_its_source_invocation_loop() -> None:
    events: list[tuple[object, ...]] = []

    result = _invoke(
        "ls",
        ["memory:/path:with:colons"],
        sources={"memory": _RecordingSource(events)},
    )

    assert result.exit_code == 0
    assert result.stdout == "memory:/path:with:colons\n"
    assert result.stderr == ""
    assert [event[0] for event in events] == ["factory", "enter", "info", "exit"]
    assert {event[-1] for event in events if event[0] != "factory"} == {events[1][-1]}


def test_ls_acquires_distinct_names_before_reusing_sources_for_files() -> None:
    events: list[tuple[object, ...]] = []
    shared_source = _RecordingSource(events)

    result = _invoke(
        "ls",
        ["alpha:/one", "alpha:/three", "beta:/two"],
        sources={
            "beta": shared_source,
            "alpha": shared_source,
            "unused": _source_must_not_run,
        },
    )

    assert result.exit_code == 0
    assert result.stdout == "alpha:/one\nalpha:/three\nbeta:/two\n"
    assert result.stderr == ""
    assert [(event[0], *event[1:-1]) for event in events] == [
        ("factory",),
        ("enter", 1),
        ("factory",),
        ("enter", 2),
        ("info", 1, "/one"),
        ("info", 1, "/three"),
        ("info", 2, "/two"),
        ("exit", 2),
        ("exit", 1),
    ]


def test_ls_stops_acquisition_after_a_source_factory_failure() -> None:
    events: list[tuple[object, ...]] = []
    factory_error = ValueError("factory\\\0\r\n")
    first = _RecordingSource(events, exit_result=True)

    def broken_source() -> NoReturn:
        raise factory_error

    result = _invoke(
        "ls",
        ["first:/one", "broken:/two", "later:/three"],
        sources={
            "first": first,
            "broken": broken_source,
            "later": _source_must_not_run,
        },
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == ("ls: broken: source factory failure (ValueError): factory\\\\\\x00\\x0d\\x0a\n")
    assert [event[0] for event in events] == ["factory", "enter", "exit"]
    exception_type, exception, traceback = first.exit_calls[0]
    assert exception_type is ValueError
    assert exception is factory_error
    assert traceback is not None


@pytest.mark.parametrize(
    "incompatible_manager",
    [
        object(),
        type("MissingExit", (), {"__aenter__": lambda _self: None})(),
        type("NonCallable", (), {"__aenter__": 1, "__aexit__": 2})(),
    ],
)
def test_ls_rejects_an_incompatible_source_context_manager(
    incompatible_manager: object,
) -> None:
    def source() -> object:
        return incompatible_manager

    result = _invoke("ls", ["broken:/file"], sources={"broken": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == ("ls: broken: source factory returned incompatible async context manager\n")


def test_ls_stops_after_source_entry_failure_without_exiting_failed_entry() -> None:
    events: list[tuple[object, ...]] = []
    entry_error = LookupError("entry\\\0\r\n")
    first = _RecordingSource(events)

    class BrokenContext:
        async def __aenter__(self) -> NoReturn:
            events.append(("broken-enter", id(asyncio.get_running_loop())))
            raise entry_error

        async def __aexit__(self, *exc_info: object) -> NoReturn:
            raise AssertionError

    def broken_source() -> BrokenContext:
        events.append(("broken-factory",))
        return BrokenContext()

    result = _invoke(
        "ls",
        ["first:/one", "broken:/two", "later:/three"],
        sources={
            "first": first,
            "broken": broken_source,
            "later": _source_must_not_run,
        },
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == ("ls: broken: source entry failure (LookupError): entry\\\\\\x00\\x0d\\x0a\n")
    assert [event[0] for event in events] == [
        "factory",
        "enter",
        "broken-factory",
        "broken-enter",
        "exit",
    ]
    exception_type, exception, traceback = first.exit_calls[0]
    assert exception_type is LookupError
    assert exception is entry_error
    assert traceback is not None


@pytest.mark.parametrize(
    "filesystem_factory",
    [
        object,
        lambda: AsyncFileSystem(asynchronous=False, skip_instance_cache=True),
        lambda: _async_filesystem_with_flag("async_impl", value=False),
        lambda: _async_filesystem_with_flag("asynchronous", value=1),
    ],
)
def test_ls_exits_a_source_that_yields_an_incompatible_filesystem(
    filesystem_factory,
) -> None:
    events: list[tuple[object, ...]] = []

    class YieldingContext:
        async def __aenter__(self) -> object:
            events.append(("enter", id(asyncio.get_running_loop())))
            return filesystem_factory()

        async def __aexit__(self, *exc_info: object) -> None:
            events.append(("exit", id(asyncio.get_running_loop())))

    def source() -> YieldingContext:
        events.append(("factory",))
        return YieldingContext()

    result = _invoke("ls", ["broken:/file"], sources={"broken": source})

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == ("ls: broken: source yielded incompatible async filesystem\n")
    assert [event[0] for event in events] == ["factory", "enter", "exit"]
    assert events[1][-1] == events[2][-1]


def _async_filesystem_with_flag(
    name: str,
    *,
    value: object,
) -> AsyncFileSystem:
    filesystem = AsyncFileSystem(asynchronous=True, skip_instance_cache=True)
    setattr(filesystem, name, value)
    return filesystem


@pytest.mark.parametrize(
    "info_result",
    [None, {}, {"type": 1}, {"type": "other"}],
)
def test_ls_rejects_a_non_file_info_result(info_result: object) -> None:
    events: list[tuple[object, ...]] = []

    result = _invoke(
        "ls",
        ["memory:/file"],
        sources={"memory": _RecordingSource(events, info_result)},
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert result.stderr == "ls: memory:/file: incompatible result\n"
    assert [event[0] for event in events] == ["factory", "enter", "info", "exit"]
