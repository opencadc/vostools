"""Bounded, order-preserving concurrency for independent backend operations.

Remote backends pay one round trip per hook call, so commands that issue many
independent calls run them concurrently under one fixed bound. Results are
always returned in submission order, so output and the reported failure stay
deterministic regardless of completion order.

Per :doc:`lessons.md §3 <../../../vosfs/docs/design/fss-cli/lessons>`, a started
operation is never orphaned: after the first failure no new operation starts,
and every in-flight one is drained before control flow propagates.
"""

from __future__ import annotations

import asyncio
from contextlib import suppress
from dataclasses import dataclass
from typing import TYPE_CHECKING, Generic, TypeVar, cast

if TYPE_CHECKING:
    from collections.abc import Awaitable, Callable, Sequence

_CONCURRENCY = 16
_ResultT = TypeVar("_ResultT")


@dataclass(frozen=True)
class _Outcome(Generic[_ResultT]):
    """The observed result of one started operation.

    Exactly one of ``value`` or ``error`` is meaningful: ``error`` is set when
    the operation raised an ordinary exception.
    """

    value: _ResultT | None = None
    error: Exception | None = None


async def _capture(
    operation: Callable[[], Awaitable[_ResultT]],
) -> _Outcome[_ResultT] | BaseException:
    """Run one operation, returning control-flow exceptions as values.

    A task must never raise ``KeyboardInterrupt`` or ``SystemExit`` itself:
    asyncio re-raises those out of the event loop, bypassing the drain below.
    """
    try:
        return _Outcome(value=await operation())
    except Exception as error:  # noqa: BLE001 - reported in submission order.
        return _Outcome(error=error)
    except BaseException as error:  # noqa: BLE001 - preserve exact control flow.
        return error


async def _drain(tasks: Sequence[asyncio.Task[object]]) -> None:
    """Wait for every task to finish, even while this waiter is cancelled."""
    if not tasks:
        return
    aggregate = asyncio.gather(*tasks, return_exceptions=True)
    while not aggregate.done():
        with suppress(BaseException):
            await asyncio.shield(aggregate)


class _BoundedRun(Generic[_ResultT]):
    """Scheduler state for one :func:`_run_bounded` call."""

    def __init__(
        self,
        operations: Sequence[Callable[[], Awaitable[_ResultT]]],
        stop: Callable[[_ResultT], bool] | None,
        limit: int,
    ) -> None:
        self.operations = operations
        self.stop = stop
        self.limit = limit
        self.outcomes: list[_Outcome[_ResultT] | None] = [None] * len(operations)
        self.pending: dict[asyncio.Task[_Outcome[_ResultT] | BaseException], int] = {}
        self.next_index = 0
        self.stopped = False
        self.control: BaseException | None = None

    def launch(self) -> None:
        while not self.stopped and len(self.pending) < self.limit and self.next_index < len(self.operations):
            operation = self.operations[self.next_index]
            self.pending[asyncio.create_task(_capture(operation))] = self.next_index
            self.next_index += 1

    def interrupt(self) -> None:
        self.stopped = True
        for task in self.pending:
            task.cancel()

    def record(self, task: asyncio.Task[_Outcome[_ResultT] | BaseException]) -> None:
        index = self.pending.pop(task)
        result = asyncio.CancelledError() if task.cancelled() else task.result()
        if isinstance(result, BaseException):
            if self.control is None:
                self.control = result
            self.interrupt()
            return
        self.outcomes[index] = result
        if result.error is not None or (self.stop is not None and self.stop(cast("_ResultT", result.value))):
            self.stopped = True

    async def run(self) -> list[_Outcome[_ResultT] | None]:
        try:
            self.launch()
            while self.pending:
                done, _ = await asyncio.wait(self.pending, return_when=asyncio.FIRST_COMPLETED)
                for task in done:
                    self.record(task)
                self.launch()
        except BaseException:
            self.interrupt()
            await _drain(tuple(self.pending))
            raise
        if self.control is not None:
            raise self.control
        return self.outcomes


async def _run_bounded(
    operations: Sequence[Callable[[], Awaitable[_ResultT]]],
    *,
    stop: Callable[[_ResultT], bool] | None = None,
    limit: int = _CONCURRENCY,
) -> list[_Outcome[_ResultT] | None]:
    """Run operations with at most ``limit`` in flight, in submission order.

    Operations start in submission order. Once one raises an ordinary
    exception, or returns a value for which ``stop`` is true, no further
    operation starts; every operation already started is awaited. The result
    holds one outcome per started operation and ``None`` for each operation
    that never started. Because operations start in order, every operation
    before a stopping one has an outcome, so a caller scanning the result in
    order meets the first failure in submission order before any ``None``.

    A control-flow exception (cancellation, ``KeyboardInterrupt``) raised by
    an operation or delivered to the caller cancels the other started
    operations — each drains its in-flight hook call through
    ``_drain_current_operation`` — and propagates only after every started
    operation has finished.

    Args:
        operations: Zero-argument callables, each starting one operation.
        stop: Optional predicate marking a successful value as a failure that
            stops further starts.
        limit: Maximum number of operations in flight.

    Returns:
        Outcomes in submission order, ``None`` for unstarted operations.
    """
    return await _BoundedRun(operations, stop, limit).run()


def _outcome_value(outcome: _Outcome[_ResultT] | None) -> _ResultT:
    """Return a started operation's value, re-raising its failure."""
    if outcome is None:
        message = "operation never started"
        raise RuntimeError(message)
    if outcome.error is not None:
        raise outcome.error
    return cast("_ResultT", outcome.value)


async def _gather_bounded(
    operations: Sequence[Callable[[], Awaitable[_ResultT]]],
    *,
    limit: int = _CONCURRENCY,
) -> list[_ResultT]:
    """Run operations under the bound and return their values in order.

    Raises:
        Exception: The failure of the first failing operation in submission
            order, after every started operation has finished.
    """
    outcomes = await _run_bounded(operations, limit=limit)
    for outcome in outcomes:
        if outcome is not None and outcome.error is not None:
            raise outcome.error
    return [_outcome_value(outcome) for outcome in outcomes]
