"""``info`` command tests through the public embedded-command seam."""

from __future__ import annotations

import asyncio
import os
import subprocess
import sys
from collections.abc import ItemsView, Mapping
from datetime import datetime, timezone
from pathlib import Path
from types import MappingProxyType
from typing import TYPE_CHECKING

import pytest
from typer.main import get_command

from fss_cli import App

from ._support import _invoke, _RecordingSource

if TYPE_CHECKING:
    from collections.abc import Callable, Iterator

_INFO = MappingProxyType(
    {
        "name": "/docs/report.txt",
        "type": "file",
        "size": 3,
        "mtime": "2026-07-17T18:00:00Z",
        "mode": 0o100644,
        "nlink": 1,
        "uid": 1000,
        "gid": "science",
        "ETag": b"abc",
        "created": datetime(2026, 7, 16, 18, tzinfo=timezone.utc),
        "properties": MappingProxyType({"z": (2, 1), "a": {"b", "a"}}),
    }
)
_OUTPUT = (
    "{'extra': {'ETag': b'abc',\n"
    "           'created': datetime.datetime(2026, 7, 16, 18, 0, "
    "tzinfo=datetime.timezone.utc),\n"
    "           'properties': {'a': {'a', 'b'}, 'z': (2, 1)}},\n"
    " 'group': 'science',\n"
    " 'kind': 'file',\n"
    " 'link_target': None,\n"
    " 'mode': 33188,\n"
    " 'mtime': 1784311200.0,\n"
    " 'name': 'report.txt',\n"
    " 'nlink': 1,\n"
    " 'owner': 1000,\n"
    " 'size': 3}\n"
)


class _HashableMapping(dict[object, object]):
    __hash__ = object.__hash__


class _ContainerList(list[object]):
    pass


class _HashableContainerList(_ContainerList):
    __hash__ = object.__hash__


class _ContainerTuple(tuple[object, ...]):
    __slots__ = ()


class _ContainerSet(set[object]):
    pass


class _ContainerFrozenSet(frozenset[object]):
    pass


class _SamePresentation:
    def __repr__(self) -> str:
        return "same-key"


class _CoreOnlyMapping(Mapping[object, object]):
    def __init__(self) -> None:
        self._values = {"visible": 1, "hidden": 2}

    def __getitem__(self, key: object) -> object:
        return self._values[key]

    def __iter__(self) -> Iterator[object]:
        return iter(self._values)

    def __len__(self) -> int:
        return len(self._values)

    def items(self) -> ItemsView[object, object]:
        visible: dict[object, object] = {"visible": 1}
        return visible.items()


class _StatefulPresentation:
    def __init__(self, first: str, later: str) -> None:
        self.first = first
        self.later = later
        self.repr_calls = 0

    def __repr__(self) -> str:
        self.repr_calls += 1
        return self.first if self.repr_calls == 1 else self.later


class _ReprFailure:
    def __init__(self, error: BaseException) -> None:
        self.error = error

    def __repr__(self) -> str:
        raise self.error


def _mapping_cycle() -> object:
    value = _HashableMapping()
    value["self"] = value
    return value


def _list_cycle() -> object:
    value = _ContainerList()
    value.append(value)
    return value


def _tuple_cycle() -> object:
    holder = _ContainerList()
    value = _ContainerTuple((holder,))
    holder.append(value)
    return value


def _set_cycle() -> object:
    holder = _HashableContainerList()
    value = _ContainerSet({holder})
    holder.append(value)
    return value


def _frozenset_cycle() -> object:
    holder = _HashableContainerList()
    value = _ContainerFrozenSet({holder})
    holder.append(value)
    return value


def test_info_help_matches_locked_usage() -> None:
    result = _invoke("info", ["--help"])

    assert result.exit_code == 0
    plain_help = result.stdout
    assert "Usage:" in plain_help
    assert "root info [OPTIONS] {name:/path}" in plain_help
    assert "Display normalized file information" in plain_help


def test_info_renders_every_normalized_field_and_python_extra_value() -> None:
    events: list[tuple[object, ...]] = []
    source = _RecordingSource(events, info_result=_INFO)

    result = _invoke("info", ["memory:/docs/report.txt"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (0, _OUTPUT, "")
    assert [(event[0], event[2]) for event in events if event[0] == "info"] == [("info", "/docs/report.txt")]
    assert not any(event[0] == "ls" for event in events)
    assert source.call_count == 1


def test_info_rendering_is_stable_across_python_hash_seeds() -> None:
    child = Path(__file__).with_name("_info_process_child.py")
    outputs = []
    for seed in ("1", "987654"):
        environment = {**os.environ, "PYTHONHASHSEED": seed}
        completed = subprocess.run(  # noqa: S603 - fixed interpreter and script.
            [sys.executable, str(child)],
            check=True,
            capture_output=True,
            env=environment,
            text=True,
        )
        assert completed.stderr == ""
        outputs.append(completed.stdout)

    assert (
        outputs
        == [
            "{'extra': {'keyed': {frozenset({'alpha', 'bravo'}): "
            "'frozenset key',\n"
            "                     ('tuple', frozenset({'charlie', 'delta'})): "
            "'tuple key'},\n"
            "           'properties': {'a': {'alpha', 'bravo', 'charlie'}, "
            "'z': (2, 1)}},\n"
            " 'group': None,\n"
            " 'kind': 'file',\n"
            " 'link_target': None,\n"
            " 'mode': None,\n"
            " 'mtime': None,\n"
            " 'name': 'x',\n"
            " 'nlink': None,\n"
            " 'owner': None,\n"
            " 'size': None}\n"
        ]
        * 2
    )


def test_info_accepts_the_option_delimiter() -> None:
    source = _RecordingSource([], info_result=_INFO)

    result = _invoke(
        "info",
        ["--", "memory:/docs/report.txt"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, _OUTPUT, "")


@pytest.mark.parametrize(
    ("arguments", "diagnostic"),
    [
        (["bare"], "bare: invalid mapped filesystem operand"),
        (["memory:relative"], "memory:relative: invalid mapped filesystem operand"),
        (["unknown:/x"], "unknown:/x: unknown filesystem (known: memory)"),
        (["--", "-x"], "-x: invalid mapped filesystem operand"),
    ],
)
def test_info_rejects_invalid_argv_before_source_entry(
    arguments: list[str],
    diagnostic: str,
) -> None:
    result = _invoke("info", arguments)

    assert (result.exit_code, result.stdout, result.stderr) == (
        2,
        "",
        f"info: {diagnostic}\n",
    )


@pytest.mark.parametrize(
    ("arguments", "contexts"),
    [
        ([], ("Missing argument", "name:/path")),
        (["-x", "memory:/x"], ("No such option", "-x")),
        (["--long", "memory:/x"], ("No such option", "long")),
        (["--help=value"], ("does not take a value", "help")),
        (
            ["memory:/one", "memory:/two"],
            ("unexpected extra argument", "memory:/two"),
        ),
    ],
)
def test_info_leaves_usage_failures_to_typer(
    arguments: list[str],
    contexts: tuple[str, ...],
) -> None:
    result = _invoke("info", arguments)

    assert (result.exit_code, result.stdout) == (2, "")
    diagnostic = result.stderr
    for context in contexts:
        assert context in diagnostic


@pytest.mark.parametrize(
    "result",
    [
        None,
        [],
        {"type": "file"},
        {"name": 3, "type": "file"},
        {"name": "/x", "type": "file", 1: "not a string key"},
    ],
)
def test_info_rejects_malformed_result(result: object) -> None:
    source = _RecordingSource([], info_result=result)

    invocation = _invoke("info", ["memory:/x"], sources={"memory": source})

    assert (invocation.exit_code, invocation.stdout, invocation.stderr) == (
        1,
        "",
        "info: memory:/x: incompatible result\n",
    )


def test_info_rejects_a_recursive_extra_value() -> None:
    info: dict[str, object] = {"name": "/x", "type": "file"}
    info["cycle"] = info
    source = _RecordingSource([], info_result=info)

    result = _invoke("info", ["memory:/x"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "info: memory:/x: incompatible result\n",
    )


def test_info_rejects_a_recursive_mapping_key_graph() -> None:
    key = _HashableMapping()
    key[key] = "self"
    source = _RecordingSource(
        [],
        info_result={"name": "/x", "type": "file", "keyed": {key: "value"}},
    )

    result = _invoke("info", ["memory:/x"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "info: memory:/x: incompatible result\n",
    )


@pytest.mark.parametrize(
    "cycle",
    [_mapping_cycle, _list_cycle, _tuple_cycle, _set_cycle, _frozenset_cycle],
    ids=["mapping", "list", "tuple", "set", "frozenset"],
)
def test_info_rejects_recursive_container_subclasses(
    cycle: Callable[[], object],
) -> None:
    source = _RecordingSource(
        [],
        info_result={"name": "/x", "type": "file", "cycle": cycle()},
    )

    result = _invoke("info", ["memory:/x"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "info: memory:/x: incompatible result\n",
    )


def test_info_accepts_a_shared_acyclic_container_subclass() -> None:
    shared = _ContainerList([{"leaf": 1}])
    source = _RecordingSource(
        [],
        info_result={
            "name": "/x",
            "type": "file",
            "left": shared,
            "right": shared,
        },
    )

    result = _invoke("info", ["memory:/x"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stderr == ""
    assert "'left': [{'leaf': 1}], 'right': [{'leaf': 1}]" in result.stdout


def test_info_rejects_distinct_mapping_keys_with_one_presentation() -> None:
    first = _SamePresentation()
    second = _SamePresentation()
    source = _RecordingSource(
        [],
        info_result={
            "name": "/x",
            "type": "file",
            "keyed": {first: 1, second: 2},
        },
    )

    result = _invoke("info", ["memory:/x"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "info: memory:/x: incompatible result\n",
    )


def test_info_uses_the_authoritative_mapping_core_interface() -> None:
    source = _RecordingSource(
        [],
        info_result={
            "name": "/x",
            "type": "file",
            "keyed": _CoreOnlyMapping(),
        },
    )

    result = _invoke("info", ["memory:/x"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stderr == ""
    assert "'keyed': {'hidden': 2, 'visible': 1}" in result.stdout


def test_info_freezes_each_validated_mapping_key_spelling() -> None:
    first = _StatefulPresentation("first-key", "changed-first-key")
    second = _StatefulPresentation("second-key", "changed-second-key")
    source = _RecordingSource(
        [],
        info_result={
            "name": "/x",
            "type": "file",
            "keyed": {first: 1, second: 2},
        },
    )

    result = _invoke("info", ["memory:/x"], sources={"memory": source})

    assert result.exit_code == 0
    assert result.stderr == ""
    assert "'keyed': {first-key: 1, second-key: 2}" in result.stdout
    assert (first.repr_calls, second.repr_calls) == (1, 1)


def test_info_turns_an_ordinary_repr_failure_into_an_atomic_incompatible_result() -> None:
    error = RuntimeError("repr failed")
    source = _RecordingSource(
        [],
        info_result={"name": "/x", "type": "file", "opaque": _ReprFailure(error)},
    )

    result = _invoke("info", ["memory:/x"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "info: memory:/x: incompatible result\n",
    )
    assert source.exit_calls == [(None, None, None)]


@pytest.mark.parametrize(
    "control",
    [
        asyncio.CancelledError("repr cancellation"),
        KeyboardInterrupt("repr interrupt"),
        SystemExit(29),
    ],
)
def test_info_preserves_repr_control_flow_through_cleanup_and_direct_caller(
    control: BaseException,
) -> None:
    source = _RecordingSource(
        [],
        info_result={
            "name": "/x",
            "type": "file",
            "opaque": _ReprFailure(control),
        },
    )
    command = get_command(App({"memory": source}).typer_app)

    with (
        command.make_context("fs", ["info", "memory:/x"]) as context,
        pytest.raises(type(control)) as caught,
    ):
        command.invoke(context)

    if isinstance(control, asyncio.CancelledError):
        assert type(caught.value) is asyncio.CancelledError
    else:
        assert caught.value is control
        assert caught.value.args == control.args
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is type(control)
    assert exception is control
    assert exception.args == control.args
    assert traceback is not None


def test_info_maps_an_ordinary_backend_failure() -> None:
    source = _RecordingSource([], info_error=FileNotFoundError("gone"))

    result = _invoke("info", ["memory:/missing"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "info: memory:/missing: not found\n",
    )


def test_info_writes_and_flushes_one_complete_binary_payload(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = _RecordingSource([], info_result=_INFO)
    calls: list[tuple[str, bytes | None]] = []

    class _Stdout:
        def write(self, payload: bytes) -> int:
            calls.append(("write", payload))
            return len(payload)

        def flush(self) -> None:
            calls.append(("flush", None))

    monkeypatch.setattr("fss_cli._info._binary_stdout", _Stdout)

    result = _invoke("info", ["memory:/docs/report.txt"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert calls == [("write", _OUTPUT.encode()), ("flush", None)]


def test_info_reports_a_short_write_and_still_cleans_up(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = _RecordingSource([], info_result=_INFO)

    class _ShortStdout:
        def write(self, payload: bytes) -> int:
            return len(payload) - 1

        def flush(self) -> None:
            message = "short writes must not flush"
            raise AssertionError(message)

    monkeypatch.setattr("fss_cli._info._binary_stdout", _ShortStdout)

    result = _invoke("info", ["memory:/docs/report.txt"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "info: output: output failure (OSError): short write\n",
    )
    assert len(source.exit_calls) == 1


def test_info_keeps_broken_pipe_silent_but_reports_cleanup_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    broken_pipe = BrokenPipeError("closed")
    source = _RecordingSource([], info_result=_INFO, exit_error=OSError("cleanup"))

    class _BrokenStdout:
        def write(self, payload: bytes) -> int:
            del payload
            raise broken_pipe

        def flush(self) -> None:
            message = "broken writes must not flush"
            raise AssertionError(message)

    monkeypatch.setattr("fss_cli._info._binary_stdout", _BrokenStdout)

    result = _invoke("info", ["memory:/docs/report.txt"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "info: memory: source exit failure (OSError): cleanup\n",
    )
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is BrokenPipeError
    assert exception is broken_pipe
    assert traceback is not None


def test_info_propagates_control_flow_unchanged_after_cleanup() -> None:
    control = asyncio.CancelledError("stop info")
    source = _RecordingSource([], info_error=control)

    with pytest.raises(asyncio.CancelledError) as caught:
        _invoke("info", ["memory:/x"], sources={"memory": source})

    assert type(caught.value) is asyncio.CancelledError
    exception_type, exception, traceback = source.exit_calls[0]
    assert exception_type is asyncio.CancelledError
    assert exception is control
    assert traceback is not None
