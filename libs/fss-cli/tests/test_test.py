"""``test`` predicate command tests through the public embedded-command seam."""

from __future__ import annotations

from contextlib import AbstractAsyncContextManager
from dataclasses import dataclass
from typing import TYPE_CHECKING, Literal

import pytest
from fsspec.asyn import AsyncFileSystem

from ._support import _invoke

if TYPE_CHECKING:
    from types import TracebackType


@dataclass(frozen=True)
class _PredicateCall:
    operation: Literal["exists", "isdir", "isfile"]
    path: str


class _TestControl(BaseException):
    pass


class _PredicateFileSystem(AsyncFileSystem):
    cachable = False

    def __init__(self, source: _PredicateSource) -> None:
        super().__init__(asynchronous=True)
        self.source = source

    async def _exists(self, path: str, **kwargs: object) -> object:
        assert not kwargs
        return self._respond("exists", path)

    async def _isdir(self, path: str) -> object:
        return self._respond("isdir", path)

    async def _isfile(self, path: str) -> object:
        return self._respond("isfile", path)

    def _respond(
        self,
        operation: Literal["exists", "isdir", "isfile"],
        path: str,
    ) -> object:
        self.source.calls.append(_PredicateCall(operation, path))
        if self.source.error is not None:
            raise self.source.error
        return self.source.result


class _PredicateSource:
    def __init__(
        self,
        *,
        result: object = True,
        error: BaseException | None = None,
        exit_error: BaseException | None = None,
    ) -> None:
        self.result = result
        self.error = error
        self.exit_error = exit_error
        self.calls: list[_PredicateCall] = []
        self.lifecycle: list[str] = []
        self.exit_calls: list[
            tuple[
                type[BaseException] | None,
                BaseException | None,
                TracebackType | None,
            ]
        ] = []

    def __call__(self) -> _PredicateContext:
        self.lifecycle.append("factory")
        return _PredicateContext(self)


class _PredicateContext(AbstractAsyncContextManager[_PredicateFileSystem]):
    def __init__(self, source: _PredicateSource) -> None:
        self.source = source
        self.filesystem = _PredicateFileSystem(source)

    async def __aenter__(self) -> _PredicateFileSystem:
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


@pytest.mark.parametrize(
    ("selector", "operation"),
    [("-e", "exists"), ("-d", "isdir"), ("-f", "isfile")],
)
@pytest.mark.parametrize(("predicate_result", "exit_code"), [(True, 0), (False, 1)])
def test_test_uses_one_matching_hook_without_output(
    selector: str,
    operation: Literal["exists", "isdir", "isfile"],
    predicate_result: bool,
    exit_code: int,
) -> None:
    source = _PredicateSource(result=predicate_result)

    result = _invoke(
        "test",
        [selector, "memory:/docs/a.txt"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (exit_code, "", "")
    assert source.calls == [_PredicateCall(operation, "/docs/a.txt")]
    assert source.lifecycle == ["factory", "enter", "exit"]
    assert source.exit_calls[0] == (None, None, None)


def test_test_accepts_an_interspersed_selector_and_option_terminator() -> None:
    source = _PredicateSource(result=True)

    result = _invoke(
        "test",
        ["memory:/docs", "-d", "--"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert source.calls == [_PredicateCall("isdir", "/docs")]


def test_test_help_comes_from_typed_callback() -> None:
    result = _invoke("test", ["--help"])

    assert (result.exit_code, result.stderr) == (0, "")
    help_text = result.stdout
    assert "Usage:" in help_text
    assert "root test [OPTIONS] {name:/path}" in help_text
    assert "Evaluate a file predicate" in help_text
    for selector in ("-e", "-d", "-f"):
        assert selector in help_text


@pytest.mark.parametrize(
    ("arguments", "diagnostic"),
    [
        (["memory:/a"], "test: exactly one predicate selector is required\n"),
        (
            ["-e", "-d", "memory:/a"],
            "test: exactly one predicate selector is required\n",
        ),
        (["-ed", "memory:/a"], "test: exactly one predicate selector is required\n"),
        (
            ["-e", "memory:relative"],
            "test: memory:relative: invalid mapped filesystem operand\n",
        ),
        (
            ["-e", "unknown:/a"],
            "test: unknown:/a: unknown filesystem (known: memory)\n",
        ),
        (["-e", "--", "--help"], "test: --help: invalid mapped filesystem operand\n"),
    ],
)
def test_test_preflight_failures_are_stable_and_source_free(
    arguments: list[str],
    diagnostic: str,
) -> None:
    result = _invoke("test", arguments)

    assert (result.exit_code, result.stdout, result.stderr) == (2, "", diagnostic)


@pytest.mark.parametrize(
    ("arguments", "contexts"),
    [
        ([], ("Missing argument", "name:/path")),
        (["-e"], ("Missing argument", "name:/path")),
        (["-x", "memory:/a"], ("No such option", "-x")),
        (
            ["-e", "memory:/a", "memory:/b"],
            ("unexpected extra argument", "memory:/b"),
        ),
    ],
)
def test_test_leaves_usage_failures_to_typer(
    arguments: list[str],
    contexts: tuple[str, ...],
) -> None:
    result = _invoke("test", arguments)

    assert (result.exit_code, result.stdout) == (2, "")
    diagnostic = result.stderr
    for context in contexts:
        assert context in diagnostic


def test_test_accepts_a_repeated_selector_through_typer() -> None:
    source = _PredicateSource(result=True)

    result = _invoke(
        "test",
        ["-e", "-e", "memory:/a"],
        sources={"memory": source},
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert source.calls == [_PredicateCall("exists", "/a")]


@pytest.mark.parametrize("predicate_result", [None, 0, 1, "true", []])
def test_test_rejects_non_boolean_results(predicate_result: object) -> None:
    source = _PredicateSource(result=predicate_result)

    result = _invoke("test", ["-e", "memory:/a"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "test: memory:/a: incompatible result\n",
    )
    assert source.calls == [_PredicateCall("exists", "/a")]


@pytest.mark.parametrize(
    ("error", "diagnostic"),
    [
        (FileNotFoundError(), "not found"),
        (PermissionError(), "permission denied"),
        (NotImplementedError(), "unsupported operation"),
        (RuntimeError("bad"), "backend failure (RuntimeError): bad"),
    ],
)
def test_test_reports_backend_failures_and_passes_them_to_cleanup(
    error: Exception,
    diagnostic: str,
) -> None:
    source = _PredicateSource(error=error)

    result = _invoke("test", ["-f", "memory:/a"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        f"test: memory:/a: {diagnostic}\n",
    )
    assert source.exit_calls[0][0] is type(error)
    assert source.exit_calls[0][1] is error
    assert source.exit_calls[0][2] is not None


def test_test_false_result_remains_silent_when_source_exit_fails() -> None:
    source = _PredicateSource(result=False, exit_error=OSError("cleanup"))

    result = _invoke("test", ["-e", "memory:/a"], sources={"memory": source})

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "test: memory: source exit failure (OSError): cleanup\n",
    )


def test_test_cleans_up_then_propagates_backend_control_flow() -> None:
    control = _TestControl("stop")
    source = _PredicateSource(error=control)

    with pytest.raises(_TestControl) as caught:
        _invoke("test", ["-e", "memory:/a"], sources={"memory": source})

    assert caught.value is control
    assert source.lifecycle == ["factory", "enter", "exit"]
