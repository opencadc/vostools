"""``head`` and ``tail`` tests through the public embedded-command seam."""

from __future__ import annotations

import sys
from collections.abc import Iterator, Mapping
from contextlib import AbstractAsyncContextManager
from dataclasses import dataclass
from typing import TYPE_CHECKING, Literal, NoReturn

import pytest
from click.utils import strip_ansi
from fsspec.asyn import AsyncFileSystem

from ._matrix_support import _memory_source
from ._support import _invoke

if TYPE_CHECKING:
    from types import TracebackType


@dataclass(frozen=True)
class _ReadCall:
    operation: Literal["info", "cat_file"]
    path: str
    start: int | None = None
    end: int | None = None


class _ReadControl(BaseException):
    pass


class _ReadFileSystem(AsyncFileSystem):
    cachable = False

    def __init__(self, source: _ReadSource) -> None:
        super().__init__(asynchronous=True)
        self.source = source

    async def _info(self, path: str, **kwargs: object) -> object:
        assert not kwargs
        self.source.calls.append(_ReadCall("info", path))
        if self.source.info_error is not None:
            raise self.source.info_error
        return self.source.info_result

    async def _cat_file(
        self,
        path: str,
        start: int | None = None,
        end: int | None = None,
        **kwargs: object,
    ) -> object:
        assert not kwargs
        self.source.calls.append(_ReadCall("cat_file", path, start, end))
        if self.source.cat_error is not None:
            raise self.source.cat_error
        return self.source.payload

    async def _get_file(self, *args: object, **kwargs: object) -> NoReturn:
        del args, kwargs
        raise AssertionError


class _ReadSource:
    def __init__(
        self,
        *,
        info_result: object = None,
        payload: object = b"abc",
        info_error: BaseException | None = None,
        cat_error: BaseException | None = None,
        exit_error: BaseException | None = None,
    ) -> None:
        self.info_result = {"size": 6} if info_result is None else info_result
        self.payload = payload
        self.info_error = info_error
        self.cat_error = cat_error
        self.exit_error = exit_error
        self.calls: list[_ReadCall] = []
        self.lifecycle: list[str] = []
        self.exit_calls: list[
            tuple[
                type[BaseException] | None,
                BaseException | None,
                TracebackType | None,
            ]
        ] = []

    def __call__(self) -> _ReadContext:
        self.lifecycle.append("factory")
        return _ReadContext(self)


class _ReadContext(AbstractAsyncContextManager[_ReadFileSystem]):
    def __init__(self, source: _ReadSource) -> None:
        self.source = source
        self.filesystem = _ReadFileSystem(source)

    async def __aenter__(self) -> _ReadFileSystem:
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


def test_head_requests_exact_leading_range_and_emits_binary_bytes() -> None:
    payload = b"a\0\xff"
    source = _ReadSource(payload=payload)

    result = _invoke("head", ["-c", "003", "memory:/blob"], sources={"memory": source})

    assert (result.exit_code, result.stdout_bytes, result.stderr) == (0, payload, "")
    assert source.calls == [_ReadCall("cat_file", "/blob", 0, 3)]
    assert source.lifecycle == ["factory", "enter", "exit"]


@pytest.mark.parametrize("command", ["head", "tail"])
def test_byte_count_rejects_the_interpreter_configured_digit_limit(
    command: Literal["head", "tail"],
) -> None:
    previous_limit = sys.get_int_max_str_digits()
    limit = sys.int_info.str_digits_check_threshold
    oversized = "9" * (limit + 1)
    try:
        sys.set_int_max_str_digits(limit)
        result = _invoke(command, ["-c", oversized, "memory:/blob"])
    finally:
        sys.set_int_max_str_digits(previous_limit)

    assert (result.exit_code, result.stdout_bytes) == (2, b"")
    diagnostic = strip_ansi(result.stderr)
    assert "Invalid value" in diagnostic
    assert "-c" in diagnostic


@pytest.mark.parametrize(
    ("count", "expected_start", "payload"),
    [("2", -2, b"ef"), ("20", -20, b"abcdef"), ("0", 0, b"")],
)
def test_tail_reads_size_then_requests_exact_suffix_range(
    count: str,
    expected_start: int,
    payload: bytes,
) -> None:
    source = _ReadSource(info_result={"size": 6}, payload=payload)

    result = _invoke("tail", ["memory:/blob", "-c", count], sources={"memory": source})

    assert (result.exit_code, result.stdout_bytes, result.stderr) == (0, payload, "")
    assert source.calls == [
        _ReadCall("info", "/blob"),
        _ReadCall("cat_file", "/blob", expected_start, None),
    ]


@pytest.mark.parametrize(
    ("count", "expected"),
    [("8", b"ABCDEF"), ("6", b"ABCDEF"), ("20", b"ABCDEF"), ("2", b"EF")],
)
def test_tail_count_beyond_size_emits_the_whole_memory_object(
    monkeypatch: pytest.MonkeyPatch,
    count: str,
    expected: bytes,
) -> None:
    # A negative start is an fsspec suffix offset: 8 of 6 bytes once gave "EF".
    source = _memory_source(monkeypatch, {"/blob": b"ABCDEF"}, directories=())

    result = _invoke("tail", ["-c", count, "memory:/blob"], sources={"memory": source})

    assert (result.exit_code, result.stdout_bytes, result.stderr) == (0, expected, "")


@pytest.mark.parametrize("command", ["head", "tail"])
def test_zero_count_on_a_file_reads_metadata_but_no_bytes(
    command: Literal["head", "tail"],
) -> None:
    source = _ReadSource(info_result={"size": 6, "type": "file"})

    result = _invoke(command, ["-c", "0", "memory:/a"], sources={"memory": source})

    assert (result.exit_code, result.stdout_bytes, result.stderr) == (0, b"", "")
    assert source.calls == [_ReadCall("info", "/a")]


@pytest.mark.parametrize("command", ["head", "tail"])
@pytest.mark.parametrize("kind", ["directory", "other", None, True])
def test_zero_count_on_a_non_file_keeps_the_backend_read_diagnostic(
    command: Literal["head", "tail"],
    kind: object,
) -> None:
    source = _ReadSource(
        info_result={"size": 6, "type": kind},
        payload=b"",
        cat_error=IsADirectoryError(),
    )

    result = _invoke(command, ["-c", "0", "memory:/a"], sources={"memory": source})

    assert (result.exit_code, result.stdout_bytes, result.stderr) == (
        1,
        b"",
        f"{command}: memory:/a: is a directory\n",
    )
    start = 0
    end = 0 if command == "head" else None
    assert source.calls == [
        _ReadCall("info", "/a"),
        _ReadCall("cat_file", "/a", start, end),
    ]


@pytest.mark.parametrize(
    ("info_result", "info_error", "diagnostic"),
    [
        (None, FileNotFoundError(), "not found"),
        (None, PermissionError(), "permission denied"),
        (["file"], None, "incompatible result"),
    ],
)
def test_head_zero_count_reports_metadata_failures_without_reading(
    info_result: object,
    info_error: Exception | None,
    diagnostic: str,
) -> None:
    source = _ReadSource(info_error=info_error)
    if info_result is not None:
        source.info_result = info_result

    result = _invoke("head", ["-c", "0", "memory:/a"], sources={"memory": source})

    assert (result.exit_code, result.stdout_bytes, result.stderr) == (
        1,
        b"",
        f"head: memory:/a: {diagnostic}\n",
    )
    assert source.calls == [_ReadCall("info", "/a")]


@pytest.mark.parametrize("command", ["head", "tail"])
@pytest.mark.parametrize(
    ("path", "expected"),
    [
        ("/blob", (0, b"", "")),
        ("/missing", (1, b"", "{command}: memory:/missing: not found\n")),
        ("/dir", (1, b"", "{command}: memory:/dir: not found\n")),
    ],
)
def test_zero_count_on_memory_keeps_existing_operand_semantics(
    monkeypatch: pytest.MonkeyPatch,
    command: Literal["head", "tail"],
    path: str,
    expected: tuple[int, bytes, str],
) -> None:
    # Memory's own read diagnostic for a directory is "not found"; the zero
    # count must not turn a failing operand into a silent success.
    source = _memory_source(monkeypatch, {"/blob": b"ABCDEF"}, directories=("/dir",))

    result = _invoke(command, ["-c", "0", f"memory:{path}"], sources={"memory": source})

    status, stdout, stderr = expected
    assert (result.exit_code, result.stdout_bytes, result.stderr) == (
        status,
        stdout,
        stderr.format(command=command),
    )


@pytest.mark.parametrize(
    ("command", "summary"),
    [("head", "Display leading bytes"), ("tail", "Display trailing bytes")],
)
def test_byte_range_help_comes_from_typed_callback_metadata(
    command: Literal["head", "tail"],
    summary: str,
) -> None:
    result = _invoke(command, ["--help"])
    help_text = strip_ansi(result.stdout)

    assert (result.exit_code, result.stderr) == (0, "")
    assert f"Usage: root {command} [OPTIONS] {{name:/path}}" in help_text
    assert summary in help_text
    assert "name:/path" in help_text
    assert "<str>" in help_text
    assert "-c" in help_text
    assert "N [x>=0]" in help_text


@pytest.mark.parametrize("command", ["head", "tail"])
@pytest.mark.parametrize(
    ("arguments", "contexts"),
    [
        ([], ("Missing argument", "name:/path")),
        (["memory:/a"], ("Missing option", "-c")),
        (["-c"], ("requires an argument", "-c")),
        (["-c", "1"], ("Missing argument", "name:/path")),
        (["-c", "nope", "memory:/a"], ("Invalid value", "-c", "int range")),
        (["-c", "-1", "memory:/a"], ("Invalid value", "-c", "x>=0")),
        (["--unknown", "-c", "1", "memory:/a"], ("No such option", "--unknown")),
        (
            ["-c", "1", "memory:/a", "memory:/b"],
            ("unexpected extra argument", "memory:/b"),
        ),
    ],
)
def test_typer_rejects_byte_range_syntax_before_source_acquisition(
    command: Literal["head", "tail"],
    arguments: list[str],
    contexts: tuple[str, ...],
) -> None:
    result = _invoke(command, arguments)

    assert (result.exit_code, result.stdout_bytes) == (2, b"")
    for context in contexts:
        assert context in strip_ansi(result.stderr)


@pytest.mark.parametrize("command", ["head", "tail"])
@pytest.mark.parametrize(
    ("operand", "diagnostic"),
    [
        ("memory", "memory: invalid mapped filesystem operand"),
        ("unknown:/a", "unknown:/a: unknown filesystem (known: memory)"),
    ],
)
def test_mapped_validation_precedes_event_loop_check_and_source_acquisition(
    monkeypatch: pytest.MonkeyPatch,
    command: Literal["head", "tail"],
    operand: str,
    diagnostic: str,
) -> None:
    def event_loop_check_must_not_run(command: str) -> NoReturn:
        del command
        raise AssertionError

    monkeypatch.setattr(
        "fss_cli._app._ensure_no_active_event_loop",
        event_loop_check_must_not_run,
    )

    result = _invoke(command, ["-c", "1", operand])

    assert (result.exit_code, result.stdout_bytes, result.stderr) == (
        2,
        b"",
        f"{command}: {diagnostic}\n",
    )


def test_head_reports_source_acquisition_failure_without_filesystem_work() -> None:
    events: list[str] = []
    source_error = OSError("acquire")

    def broken_source() -> NoReturn:
        events.append("factory")
        raise source_error

    result = _invoke(
        "head",
        ["-c", "1", "broken:/a"],
        sources={"broken": broken_source},
    )

    assert (result.exit_code, result.stdout_bytes, result.stderr) == (
        1,
        b"",
        "head: broken: source factory failure (OSError): acquire\n",
    )
    assert events == ["factory"]


@pytest.mark.parametrize(
    ("command", "info_result", "expected_calls"),
    [
        ("head", {"size": 9}, [_ReadCall("cat_file", "/a", 0, 2)]),
        (
            "tail",
            {"size": 9},
            [_ReadCall("info", "/a"), _ReadCall("cat_file", "/a", -2, None)],
        ),
    ],
)
def test_byte_range_commands_accept_the_option_terminator(
    command: Literal["head", "tail"],
    info_result: object,
    expected_calls: list[_ReadCall],
) -> None:
    source = _ReadSource(info_result=info_result, payload=b"xy")

    result = _invoke(command, ["-c", "2", "--", "memory:/a"], sources={"memory": source})

    assert (result.exit_code, result.stdout_bytes, result.stderr) == (0, b"xy", "")
    assert source.calls == expected_calls


@pytest.mark.parametrize("info_result", [None, {}, {"size": True}, {"size": -1}, {"size": 1.0}])
def test_tail_rejects_incompatible_info_before_read(info_result: object) -> None:
    source = _ReadSource(info_result=info_result)
    if info_result is None:
        source.info_result = None

    result = _invoke("tail", ["-c", "2", "memory:/a"], sources={"memory": source})

    assert (result.exit_code, result.stdout_bytes, result.stderr) == (
        1,
        b"",
        "tail: memory:/a: incompatible result\n",
    )
    assert source.calls == [_ReadCall("info", "/a")]


class _HostileMapping(Mapping[str, object]):
    def __init__(self, error: BaseException) -> None:
        self.error = error

    def __getitem__(self, key: str) -> object:
        if key == "size":
            return 6
        raise KeyError(key)

    def __iter__(self) -> Iterator[str]:
        return iter(("size",))

    def __len__(self) -> int:
        return 1

    def get(self, key: str, default: object = None) -> object:
        del key, default
        raise self.error


def test_tail_treats_an_ordinary_hostile_mapping_as_incompatible() -> None:
    source = _ReadSource(info_result=_HostileMapping(RuntimeError("hostile")))

    result = _invoke("tail", ["-c", "2", "memory:/a"], sources={"memory": source})

    assert (result.exit_code, result.stdout_bytes, result.stderr) == (
        1,
        b"",
        "tail: memory:/a: incompatible result\n",
    )
    assert source.calls == [_ReadCall("info", "/a")]
    assert source.exit_calls == [(None, None, None)]


def test_tail_preserves_control_flow_from_a_hostile_mapping() -> None:
    control = _ReadControl("stop")
    source = _ReadSource(info_result=_HostileMapping(control))

    with pytest.raises(_ReadControl) as caught:
        _invoke("tail", ["-c", "2", "memory:/a"], sources={"memory": source})

    assert caught.value is control
    assert source.lifecycle == ["factory", "enter", "exit"]
    assert source.exit_calls[0][1] is control


@pytest.mark.parametrize("command", ["head", "tail"])
@pytest.mark.parametrize("payload", [bytearray(b"ab"), "ab", b"abc"])
def test_byte_range_commands_reject_incompatible_payloads_atomically(
    command: Literal["head", "tail"],
    payload: object,
) -> None:
    source = _ReadSource(info_result={"size": 2}, payload=payload)

    result = _invoke(command, ["-c", "2", "memory:/a"], sources={"memory": source})

    assert (result.exit_code, result.stdout_bytes, result.stderr) == (
        1,
        b"",
        f"{command}: memory:/a: incompatible result\n",
    )


@pytest.mark.parametrize(
    ("command", "info_error", "cat_error", "expected_calls"),
    [
        ("head", None, PermissionError(), [_ReadCall("cat_file", "/a", 0, 2)]),
        ("tail", FileNotFoundError(), None, [_ReadCall("info", "/a")]),
        (
            "tail",
            None,
            NotImplementedError(),
            [_ReadCall("info", "/a"), _ReadCall("cat_file", "/a", -2, None)],
        ),
    ],
)
def test_byte_range_backend_failures_stop_without_output(
    command: Literal["head", "tail"],
    info_error: Exception | None,
    cat_error: Exception | None,
    expected_calls: list[_ReadCall],
) -> None:
    source = _ReadSource(info_error=info_error, cat_error=cat_error)
    error = info_error or cat_error
    assert error is not None

    result = _invoke(command, ["-c", "2", "memory:/a"], sources={"memory": source})

    categories = {
        PermissionError: "permission denied",
        FileNotFoundError: "not found",
        NotImplementedError: "unsupported operation",
    }
    assert (result.exit_code, result.stdout_bytes, result.stderr) == (
        1,
        b"",
        f"{command}: memory:/a: {categories[type(error)]}\n",
    )
    assert source.calls == expected_calls
    assert source.exit_calls[0][1] is error


def test_head_retains_primary_backend_failure_when_cleanup_also_fails() -> None:
    source = _ReadSource(
        cat_error=PermissionError("read"),
        exit_error=OSError("cleanup"),
    )

    result = _invoke("head", ["-c", "2", "memory:/a"], sources={"memory": source})

    assert (result.exit_code, result.stdout_bytes, result.stderr) == (
        1,
        b"",
        "head: memory:/a: permission denied\nhead: memory: source exit failure (OSError): cleanup\n",
    )
    assert isinstance(source.exit_calls[0][1], PermissionError)


def test_head_reports_short_binary_write_and_cleans_up(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = _ReadSource(payload=b"abc")

    class _ShortStdout:
        def write(self, payload: bytes) -> int:
            return len(payload) - 1

        def flush(self) -> None:
            raise AssertionError

    monkeypatch.setattr("fss_cli._head_tail._binary_stdout", _ShortStdout)
    result = _invoke("head", ["-c", "3", "memory:/a"], sources={"memory": source})

    assert (result.exit_code, result.stderr) == (
        1,
        "head: output: output failure (OSError): short write\n",
    )
    assert isinstance(source.exit_calls[0][1], OSError)


def test_head_reports_binary_flush_failure_and_cleans_up(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    flush_error = OSError("flush failed")
    source = _ReadSource(payload=b"abc")

    class _FlushFailingStdout:
        def write(self, payload: bytes) -> int:
            return len(payload)

        def flush(self) -> NoReturn:
            raise flush_error

    monkeypatch.setattr("fss_cli._head_tail._binary_stdout", _FlushFailingStdout)
    result = _invoke("head", ["-c", "3", "memory:/a"], sources={"memory": source})

    assert (result.exit_code, result.stderr) == (
        1,
        "head: output: output failure (OSError): flush failed\n",
    )
    assert source.exit_calls[0][1] is flush_error


def test_tail_uses_cat_broken_pipe_status_when_it_is_the_only_failure(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    broken_pipe = BrokenPipeError()
    source = _ReadSource(payload=b"ef")

    class _BrokenStdout:
        def write(self, payload: bytes) -> NoReturn:
            del payload
            raise broken_pipe

        def flush(self) -> None:
            raise AssertionError

    monkeypatch.setattr("fss_cli._head_tail._binary_stdout", _BrokenStdout)
    result = _invoke("tail", ["-c", "2", "memory:/a"], sources={"memory": source})

    assert (result.exit_code, result.stdout_bytes, result.stderr) == (141, b"", "")
    assert source.exit_calls[0][1] is broken_pipe


def test_tail_cleanup_failure_overrides_broken_pipe_status(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = _ReadSource(payload=b"ef", exit_error=OSError("cleanup"))

    class _BrokenStdout:
        def write(self, payload: bytes) -> NoReturn:
            del payload
            raise BrokenPipeError

        def flush(self) -> None:
            raise AssertionError

    monkeypatch.setattr("fss_cli._head_tail._binary_stdout", _BrokenStdout)
    result = _invoke("tail", ["-c", "2", "memory:/a"], sources={"memory": source})

    assert (result.exit_code, result.stdout_bytes, result.stderr) == (
        1,
        b"",
        "tail: memory: source exit failure (OSError): cleanup\n",
    )


def test_head_cleans_up_then_propagates_backend_control_flow() -> None:
    control = _ReadControl("stop")
    source = _ReadSource(cat_error=control)

    with pytest.raises(_ReadControl) as caught:
        _invoke("head", ["-c", "2", "memory:/a"], sources={"memory": source})

    assert caught.value is control
    assert source.lifecycle == ["factory", "enter", "exit"]
