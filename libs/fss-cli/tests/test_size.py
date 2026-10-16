"""``size`` command tests through the public embedded-command seam."""

from __future__ import annotations

from contextlib import AbstractAsyncContextManager
from dataclasses import dataclass
from typing import TYPE_CHECKING, Literal

import pytest
import typer
from fsspec.asyn import AsyncFileSystem

from ._support import _invoke

if TYPE_CHECKING:
    from types import TracebackType


@dataclass(frozen=True)
class _SizeCall:
    operation: Literal["size", "sizes"]
    paths: tuple[str, ...]


class _SizeControl(BaseException):
    pass


_DEFAULT_BATCH = object()


class _SizeFileSystem(AsyncFileSystem):
    cachable = False

    def __init__(self, source: _SizeSource) -> None:
        super().__init__(asynchronous=True)
        self.source = source

    async def _size(self, path: str) -> object:
        self.source.calls.append(_SizeCall("size", (path,)))
        if self.source.error is not None:
            raise self.source.error
        return self.source.single_result

    async def _sizes(
        self,
        paths: list[str],
        batch_size: int | None = None,
    ) -> object:
        assert batch_size is None
        self.source.calls.append(_SizeCall("sizes", tuple(paths)))
        if self.source.error is not None:
            raise self.source.error
        return self.source.batch_result


class _SizeSource:
    def __init__(
        self,
        *,
        single_result: object = 1,
        batch_result: object = _DEFAULT_BATCH,
        error: BaseException | None = None,
        exit_error: BaseException | None = None,
    ) -> None:
        self.single_result = single_result
        self.batch_result = [1] if batch_result is _DEFAULT_BATCH else batch_result
        self.error = error
        self.exit_error = exit_error
        self.calls: list[_SizeCall] = []
        self.lifecycle: list[str] = []
        self.exit_calls: list[
            tuple[
                type[BaseException] | None,
                BaseException | None,
                TracebackType | None,
            ]
        ] = []

    def __call__(self) -> _SizeContext:
        self.lifecycle.append("factory")
        return _SizeContext(self)


class _SizeContext(AbstractAsyncContextManager[_SizeFileSystem]):
    def __init__(self, source: _SizeSource) -> None:
        self.source = source
        self.filesystem = _SizeFileSystem(source)

    async def __aenter__(self) -> _SizeFileSystem:
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


def test_size_uses_one_size_call_for_one_operand() -> None:
    source = _SizeSource(single_result=1536)

    result = _invoke("size", ["memory:/docs/a.txt"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "1536\tmemory:/docs/a.txt\n",
        "",
    )
    assert source.calls == [_SizeCall("size", ("/docs/a.txt",))]
    assert source.lifecycle == ["factory", "enter", "exit"]


def test_size_batches_by_first_source_reference_and_preserves_operand_order() -> None:
    memory = _SizeSource(batch_result=[2, 4])
    local = _SizeSource(batch_result=[3, 5])

    result = _invoke(
        "size",
        [
            "memory:/a",
            "local:/b",
            "memory:/a",
            "local:/c",
        ],
        sources={"memory": memory, "local": local},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "2\tmemory:/a\n3\tlocal:/b\n4\tmemory:/a\n5\tlocal:/c\n",
        "",
    )
    assert memory.calls == [_SizeCall("sizes", ("/a", "/a"))]
    assert local.calls == [_SizeCall("sizes", ("/b", "/c"))]
    assert memory.lifecycle == ["factory", "enter", "exit"]
    assert local.lifecycle == ["factory", "enter", "exit"]


def test_size_help_comes_from_typed_callback() -> None:
    result = _invoke("size", ["--help"])

    assert (result.exit_code, result.stderr) == (0, "")
    help_text = result.stdout
    assert "Usage:" in help_text
    assert "root size [OPTIONS] {name:/path}" in help_text
    assert "Display exact file sizes" in help_text


@pytest.mark.parametrize(
    ("arguments", "diagnostic"),
    [
        (
            ["memory:relative"],
            "size: memory:relative: invalid mapped filesystem operand\n",
        ),
        (["unknown:/a"], "size: unknown:/a: unknown filesystem (known: memory)\n"),
        (["--", "--help"], "size: --help: invalid mapped filesystem operand\n"),
    ],
)
def test_size_preflight_failures_are_stable_and_source_free(
    arguments: list[str],
    diagnostic: str,
) -> None:
    result = _invoke("size", arguments)

    assert (result.exit_code, result.stdout, result.stderr) == (2, "", diagnostic)


@pytest.mark.parametrize(
    ("arguments", "contexts"),
    [
        ([], ("Missing argument", "name:/path")),
        (["-h", "memory:/a"], ("No such option", "-h")),
        (["--sizes", "memory:/a"], ("No such option", "sizes")),
    ],
)
def test_size_leaves_usage_failures_to_typer(
    arguments: list[str],
    contexts: tuple[str, ...],
) -> None:
    result = _invoke("size", arguments)

    assert (result.exit_code, result.stdout) == (2, "")
    diagnostic = result.stderr
    for context in contexts:
        assert context in diagnostic


def test_size_accepts_the_option_terminator() -> None:
    source = _SizeSource(single_result=0)

    result = _invoke("size", ["--", "memory:/zero"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        0,
        "0\tmemory:/zero\n",
        "",
    )


@pytest.mark.parametrize("size_result", [None, True, -1, 1.5, "1"])
def test_size_rejects_incompatible_single_results(size_result: object) -> None:
    source = _SizeSource(single_result=size_result)

    result = _invoke("size", ["memory:/a"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "size: memory:/a: incompatible result\n",
    )


@pytest.mark.parametrize(
    "batch_result",
    [None, (), [1], [1, True], [1, -1], [1, 2.0], [1, 2, 3]],
)
def test_size_rejects_incompatible_batch_results_atomically(
    batch_result: object,
) -> None:
    source = _SizeSource(batch_result=batch_result)

    result = _invoke(
        "size",
        ["memory:/a", "memory:/b"],
        sources={"memory": source},
    )

    expected_operand = "memory:/b" if batch_result in ([1, True], [1, -1], [1, 2.0]) else "memory:/a"
    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        f"size: {expected_operand}: incompatible result\n",
    )
    assert source.calls == [_SizeCall("sizes", ("/a", "/b"))]


@pytest.mark.parametrize(
    ("error", "diagnostic"),
    [
        (FileNotFoundError(), "not found"),
        (PermissionError(), "permission denied"),
        (NotImplementedError(), "unsupported operation"),
        (RuntimeError("bad"), "backend failure (RuntimeError): bad"),
    ],
)
def test_size_reports_backend_failure_and_passes_it_to_cleanup(
    error: Exception,
    diagnostic: str,
) -> None:
    source = _SizeSource(error=error)

    result = _invoke("size", ["memory:/a"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        f"size: memory:/a: {diagnostic}\n",
    )
    assert source.exit_calls[0][0] is type(error)
    assert source.exit_calls[0][1] is error
    assert source.exit_calls[0][2] is not None


def test_size_batch_failure_is_atomic_and_stops_in_source_order() -> None:
    error = OSError("batch failed")
    memory = _SizeSource(batch_result=[1])
    local = _SizeSource(error=error)
    later = _SizeSource(batch_result=[3])

    result = _invoke(
        "size",
        ["memory:/a", "local:/b", "later:/c"],
        sources={"memory": memory, "local": local, "later": later},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "size: local:/b: backend failure (OSError): batch failed\n",
    )
    assert memory.calls == [_SizeCall("sizes", ("/a",))]
    assert local.calls == [_SizeCall("sizes", ("/b",))]
    assert later.calls == []
    for source in (memory, local, later):
        assert source.lifecycle == ["factory", "enter", "exit"]
        assert source.exit_calls[0][1] is error


def test_size_cleans_up_after_output_failure(monkeypatch: pytest.MonkeyPatch) -> None:
    output_error = OSError("write failed")
    source = _SizeSource(single_result=1)
    real_echo = typer.echo

    def fail_stdout(message: object = None, *args: object, **kwargs: object) -> None:
        del args
        if kwargs.get("err") is True:
            real_echo(message, err=True)
            return
        raise output_error

    monkeypatch.setattr(typer, "echo", fail_stdout)
    result = _invoke("size", ["memory:/a"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "size: output: output failure (OSError): write failed\n",
    )
    assert source.exit_calls[0][1] is output_error


def test_size_keeps_broken_pipe_silent_and_cleans_up(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    broken_pipe = BrokenPipeError()
    source = _SizeSource(single_result=1)

    def break_stdout(*args: object, **kwargs: object) -> None:
        del args, kwargs
        raise broken_pipe

    monkeypatch.setattr(typer, "echo", break_stdout)
    result = _invoke("size", ["memory:/a"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (1, "", "")
    assert source.exit_calls[0][1] is broken_pipe


def test_size_retains_complete_output_when_source_exit_fails() -> None:
    source = _SizeSource(single_result=1, exit_error=OSError("cleanup"))

    result = _invoke("size", ["memory:/a"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "1\tmemory:/a\n",
        "size: memory: source exit failure (OSError): cleanup\n",
    )


def test_size_cleans_up_then_propagates_backend_control_flow() -> None:
    control = _SizeControl("stop")
    source = _SizeSource(error=control)

    with pytest.raises(_SizeControl) as caught:
        _invoke("size", ["memory:/a"], sources={"memory": source})

    assert caught.value is control
    assert source.lifecycle == ["factory", "enter", "exit"]
