"""Private orchestration tests for concrete command labels."""

import asyncio
from collections.abc import Callable
from typing import NoReturn

import pytest
import typer

import fss_cli._app as app_module
from fss_cli._cat import _run_cat
from fss_cli._command import _MappedOperand, _parse_mapped_operand
from fss_cli._ls import _LsRequest, _run_ls
from fss_cli._sources import _SourceInvocation
from fss_cli._stat import _run_stat

from ._support import _RecordingSource

_COMMAND = "future\\command\0\r\n"
_RENDERED_COMMAND = "future\\\\command\\x00\\x0d\\x0a"


def test_mapped_operand_diagnostic_escapes_concrete_command_label(capsys) -> None:
    with pytest.raises(typer.Exit) as caught:
        _parse_mapped_operand(_COMMAND, "bad", {"memory"})

    assert caught.value.exit_code == 2
    assert capsys.readouterr().err == (f"{_RENDERED_COMMAND}: bad: invalid mapped filesystem operand\n")


def test_active_loop_refusal_escapes_concrete_command_label(capsys) -> None:
    ensure_no_active_event_loop = getattr(
        app_module,
        "_ensure_no_active_event_loop",
        None,
    )
    assert ensure_no_active_event_loop is not None

    async def invoke() -> None:
        ensure_no_active_event_loop(_COMMAND)

    with pytest.raises(typer.Exit) as caught:
        asyncio.run(invoke())

    assert caught.value.exit_code == 1
    assert capsys.readouterr().err == (f"{_RENDERED_COMMAND}: cannot run from an active event loop\n")


class _EntryFailure:
    async def __aenter__(self) -> NoReturn:
        message = "entry"
        raise LookupError(message)

    async def __aexit__(self, *exc_info: object) -> None:
        del exc_info


class _IncompatibleYield:
    async def __aenter__(self) -> object:
        return object()

    async def __aexit__(self, *exc_info: object) -> None:
        del exc_info


def _factory_failure() -> NoReturn:
    message = "factory"
    raise ValueError(message)


@pytest.mark.parametrize(
    ("source", "diagnostic"),
    [
        (
            _factory_failure,
            "source: source factory failure (ValueError): factory",
        ),
        (
            object,
            "source: source factory returned incompatible async context manager",
        ),
        (
            _EntryFailure,
            "source: source entry failure (LookupError): entry",
        ),
        (
            _IncompatibleYield,
            "source: source yielded incompatible async filesystem",
        ),
    ],
)
def test_source_acquisition_diagnostic_uses_concrete_command_label(
    source: Callable[[], object],
    diagnostic: str,
    capsys,
) -> None:
    invocation = _SourceInvocation(_COMMAND, {"source": source})

    async def exercise() -> None:
        assert await invocation.acquire(("source",)) is None
        await invocation.close((None, None, None))

    asyncio.run(exercise())

    assert capsys.readouterr().err == f"{_RENDERED_COMMAND}: {diagnostic}\n"


def test_source_exit_diagnostic_uses_concrete_command_label(capsys) -> None:
    source = _RecordingSource([], exit_error=OSError("exit"))
    invocation = _SourceInvocation(_COMMAND, {"source": source})

    async def exercise() -> None:
        assert await invocation.acquire(("source",)) is not None
        assert await invocation.close((None, None, None)) is True

    asyncio.run(exercise())

    assert capsys.readouterr().err == (f"{_RENDERED_COMMAND}: source: source exit failure (OSError): exit\n")


def test_output_failure_diagnostic_uses_concrete_command_label(
    monkeypatch,
    capsys,
) -> None:
    output_error = OSError("write")
    source = _RecordingSource([])
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
    operand = _parse_mapped_operand(_COMMAND, "memory:/file", {"memory"})

    with pytest.raises(typer.Exit) as caught:
        asyncio.run(
            _run_ls(
                _COMMAND,
                _LsRequest(
                    include_almost_all=False,
                    long_listing=False,
                    human_readable=False,
                    operands=(operand,),
                ),
                {"memory": source},
            )
        )

    assert caught.value.exit_code == 1
    assert capsys.readouterr().err == (f"{_RENDERED_COMMAND}: output: output failure (OSError): write\n")


def test_cat_output_failure_diagnostic_uses_concrete_command_label(
    monkeypatch,
    capsys,
) -> None:
    output_error = OSError("write")
    source = _RecordingSource([], get_file_content=b"data")

    class _FailStdout:
        def write(self, chunk: bytes) -> int:
            del chunk
            raise output_error

        def flush(self) -> None:
            return None

    monkeypatch.setattr("fss_cli._cat._binary_stdout", _FailStdout)

    with pytest.raises(typer.Exit) as caught:
        asyncio.run(
            _run_cat(
                _COMMAND,
                (_MappedOperand("memory:/file", "memory", "/file"),),
                {"memory": source},
            )
        )

    assert caught.value.exit_code == 1
    assert capsys.readouterr().err == (f"{_RENDERED_COMMAND}: output: output failure (OSError): write\n")


def test_stat_output_failure_diagnostic_uses_concrete_command_label(
    monkeypatch,
    capsys,
) -> None:
    output_error = OSError("write")
    source = _RecordingSource(
        [],
        info_result={
            "name": "/file",
            "size": 1,
            "type": "file",
            "islink": False,
            "mode": 33188,
            "nlink": 1,
            "uid": 0,
            "gid": 0,
            "mtime": 0,
        },
    )

    def fail_stdout(line: bytes) -> None:
        del line
        raise output_error

    monkeypatch.setattr("fss_cli._stat._write_line", fail_stdout)

    with pytest.raises(typer.Exit) as caught:
        asyncio.run(
            _run_stat(
                _COMMAND,
                (_MappedOperand("memory:/file", "memory", "/file"),),
                {"memory": source},
            )
        )

    assert caught.value.exit_code == 1
    assert capsys.readouterr().err == (f"{_RENDERED_COMMAND}: output: output failure (OSError): write\n")
