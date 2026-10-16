"""Cancellation and ownership tests through fsspec's async-facade hooks."""

from __future__ import annotations

import asyncio
import gc
import traceback
import warnings
from typing import TYPE_CHECKING, Any

import httpx
import pytest
import respx
from conftest import BASE_URL, SYNC_URL
from fsspec.callbacks import Callback
from vospace_sim import VOSpaceSim

from vosfs import VOSpaceError, VOSpaceFileSystem

if TYPE_CHECKING:
    from collections.abc import Awaitable, Callable
    from pathlib import Path


@pytest.mark.parametrize("repeat_cancel", [False, True])
@pytest.mark.parametrize("operation", ["pipe", "put"])
async def test_cancelled_upload_waits_for_started_requests(
    operation: str,
    repeat_cancel: bool,
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    router = respx.Router(base_url=BASE_URL, assert_all_mocked=True)
    VOSpaceSim().install(router)
    request_started = asyncio.Event()
    release_requests = asyncio.Event()
    request_tasks: list[asyncio.Task[object]] = []
    coordinator_tasks: list[asyncio.Future[Any]] = []
    requests: list[tuple[str, str]] = []
    ensure_future = asyncio.ensure_future

    def record_task(
        awaitable: Awaitable[Any],
        *args: Any,
        **kwargs: Any,
    ) -> asyncio.Future[Any]:
        task = ensure_future(awaitable, *args, **kwargs)
        coordinator_tasks.append(task)
        return task

    monkeypatch.setattr(asyncio, "ensure_future", record_task)

    async def transport(request: httpx.Request) -> httpx.Response:
        requests.append((request.method, str(request.url)))
        if request.method == "POST" and str(request.url) == SYNC_URL:
            task = asyncio.current_task()
            assert task is not None
            request_tasks.append(task)
            if len(request_tasks) == 2:
                request_started.set()
            await release_requests.wait()
        return await router.async_handler(request)

    fs = VOSpaceFileSystem(
        BASE_URL,
        transport=httpx.MockTransport(transport),
        asynchronous=True,
        skip_instance_cache=True,
    )
    remote: list[str] = [f"/{name}.bin" for name in ("a", "b", "c")]
    if operation == "pipe":
        upload = fs._pipe(dict.fromkeys(remote, b"data"), batch_size=2)
    else:
        local = [tmp_path / f"{name}.bin" for name in ("a", "b", "c")]
        for path in local:
            path.write_bytes(b"data")
        upload = fs._put([str(path) for path in local], remote, batch_size=2)

    task = asyncio.create_task(upload)
    await asyncio.wait_for(request_started.wait(), timeout=1)
    task.cancel()
    if repeat_cancel:
        asyncio.get_running_loop().call_soon(task.cancel)
    with pytest.raises(asyncio.CancelledError) as cancelled:
        await task

    finished_before_return = all(child.done() for child in request_tasks)
    request_count = len(requests)
    release_requests.set()
    await asyncio.gather(*request_tasks, return_exceptions=True)
    await asyncio.sleep(0)
    request_count_after_release = len(requests)
    await fs.aclose()

    assert type(cancelled.value) is asyncio.CancelledError
    assert len(request_tasks) == 2
    assert len(coordinator_tasks) == 2
    assert all(child.done() for child in coordinator_tasks)
    assert finished_before_return
    assert request_count_after_release == request_count


@pytest.mark.parametrize("operation", ["pipe", "put"])
async def test_fatal_upload_stops_unstarted_writes_without_leaks(
    operation: str,
    tmp_path: Path,
) -> None:
    router = respx.Router(base_url=BASE_URL, assert_all_mocked=True)
    VOSpaceSim().add_container("/fail").install(router)
    transfer_count = 0

    async def transport(request: httpx.Request) -> httpx.Response:
        nonlocal transfer_count
        if request.method == "POST" and str(request.url) == SYNC_URL:
            transfer_count += 1
            return httpx.Response(500, text="forced transfer failure")
        return await router.async_handler(request)

    fs = VOSpaceFileSystem(
        BASE_URL,
        transport=httpx.MockTransport(transport),
        asynchronous=True,
        skip_instance_cache=True,
    )
    remote: list[str] = [f"/fail/{name}.bin" for name in ("a", "b", "c")]
    callback = Callback()
    if operation == "pipe":
        upload = fs._pipe(dict.fromkeys(remote, b"data"), batch_size=1)
    else:
        local = [tmp_path / f"{name}.bin" for name in ("a", "b", "c")]
        for path in local:
            path.write_bytes(b"data")
        upload = fs._put(
            [str(path) for path in local],
            remote,
            batch_size=1,
            callback=callback,
        )

    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always", RuntimeWarning)
        task = asyncio.create_task(upload)
        with pytest.raises(VOSpaceError, match="forced transfer failure") as error:
            await task
        if error.value.__traceback__ is not None:
            traceback.clear_frames(error.value.__traceback__)
            error.value.__traceback__ = None
        del task, upload, error
        gc.collect()

    await fs.aclose()

    assert transfer_count == 1
    assert not any("was never awaited" in str(item.message) for item in caught)
    if operation == "put":
        assert (callback.size, callback.value) == (3, 1)


async def test_cancelled_put_waits_for_callback_prelude(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Path,
) -> None:
    prelude_started = asyncio.Event()
    release_prelude = asyncio.Event()
    prelude_finished = asyncio.Event()

    class PreludeCallback(Callback):
        def branch_coro(
            self,
            fn: Callable[..., Awaitable[object]],
        ) -> Callable[..., Awaitable[object]]:
            async def wrapped(*args: object, **kwargs: object) -> object:
                prelude_started.set()
                try:
                    await release_prelude.wait()
                    return await fn(*args, **kwargs)
                finally:
                    prelude_finished.set()

            return wrapped

    router = respx.Router(base_url=BASE_URL, assert_all_mocked=True)
    fs = VOSpaceFileSystem(
        BASE_URL,
        transport=httpx.MockTransport(router.async_handler),
        asynchronous=True,
        skip_instance_cache=True,
    )

    async def put_file(*_args: object, **_kwargs: object) -> None:
        pass

    monkeypatch.setattr(fs, "_put_file", put_file)
    local = tmp_path / "data.bin"
    local.write_bytes(b"data")
    task = asyncio.create_task(
        fs._put(
            [str(local)],
            ["/remote/data.bin"],
            callback=PreludeCallback(),
            batch_size=1,
        )
    )
    await asyncio.wait_for(prelude_started.wait(), timeout=1)
    task.cancel()
    with pytest.raises(asyncio.CancelledError):
        await task

    finished_before_return = prelude_finished.is_set()
    release_prelude.set()
    await prelude_finished.wait()
    await fs.aclose()

    assert finished_before_return


async def test_cancelled_dispatched_put_closes_response_and_invalidates_cache(
    tmp_path: Path,
) -> None:
    router = respx.Router(base_url=BASE_URL, assert_all_mocked=True)
    VOSpaceSim().add_container("/cached").install(router)
    response_started = asyncio.Event()
    response_finished = asyncio.Event()
    response_closed = asyncio.Event()
    release_response = asyncio.Event()
    requests: list[httpx.Request] = []

    class BlockingResponse(httpx.AsyncByteStream):
        async def __aiter__(self):  # type: ignore[no-untyped-def]
            response_started.set()
            try:
                await release_response.wait()
                yield b""
            finally:
                response_finished.set()

        async def aclose(self) -> None:
            response_closed.set()

    async def transport(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        if request.method == "PUT" and request.url.path == "/arc/files":
            return httpx.Response(201, stream=BlockingResponse())
        return await router.async_handler(request)

    fs = VOSpaceFileSystem(
        BASE_URL,
        transport=httpx.MockTransport(transport),
        asynchronous=True,
        skip_instance_cache=True,
        use_listings_cache=True,
    )
    fs.dircache["/"] = []
    fs.dircache["/cached"] = []
    fs.dircache["/cached/data.bin"] = []
    local = tmp_path / "data.bin"
    local.write_bytes(b"data")
    task = asyncio.create_task(fs._put([str(local)], ["/cached/data.bin"]))
    await asyncio.wait_for(response_started.wait(), timeout=1)
    task.cancel()
    with pytest.raises(asyncio.CancelledError):
        await task

    cache_after_return = list(fs.dircache)
    finished_before_return = response_finished.is_set()
    closed_before_return = response_closed.is_set()
    release_response.set()
    await fs.aclose()

    assert finished_before_return
    assert closed_before_return
    assert cache_after_return == ["/"]
    assert not any(request.method == "DELETE" for request in requests)
