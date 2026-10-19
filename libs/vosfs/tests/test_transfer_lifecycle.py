"""Transfer failure and cancellation must release shared runtime resources."""

import asyncio
import re

import httpx
import pytest
import respx
from conftest import BASE_URL, make_fs
from vospace_sim import VOSpaceSim

from vosfs import VOSpaceError


@pytest.mark.parametrize("budget", [1, 2, 3])
@pytest.mark.parametrize("fail", [False, True])
async def test_copy_completes_or_fails_without_exhausting_connections(
    router: respx.Router, budget: int, fail: bool
) -> None:
    sim = VOSpaceSim().add_file("/src", b"payload")
    sim.install(router)
    if fail:
        router.route(url__regex=rf"^{re.escape(BASE_URL)}/files").mock(
            side_effect=lambda request: (
                httpx.Response(503, text="backend unavailable") if request.method == "PUT" else sim._byte_op(request)
            )
        )
    fs = make_fs(router, asynchronous=True, max_connections=budget)
    try:
        if fail:
            with pytest.raises(VOSpaceError, match="503"):
                await asyncio.wait_for(fs._cp_file("/src", "/dst"), timeout=1)
        else:
            await asyncio.wait_for(fs._cp_file("/src", "/dst"), timeout=1)
            assert sim.blobs["/dst"] == b"payload"
        assert fs._pool._budget._available == budget
    finally:
        await fs.aclose()


@pytest.mark.parametrize("cancel_creator", [False, True])
async def test_cancelled_container_creation_does_not_poison_later_writes(
    router: respx.Router, monkeypatch: pytest.MonkeyPatch, cancel_creator: bool
) -> None:
    sim = VOSpaceSim()
    sim.install(router)
    fs = make_fs(router, asynchronous=True)
    original = fs._create_missing_container
    entered, release = asyncio.Event(), asyncio.Event()

    async def create(path: str) -> None:
        entered.set()
        await release.wait()
        await original(path)

    monkeypatch.setattr(fs, "_create_missing_container", create)
    creator = asyncio.create_task(fs._makedirs("/parent", exist_ok=True))
    await entered.wait()
    waiter = asyncio.create_task(fs._makedirs("/parent", exist_ok=True))
    await asyncio.sleep(0)
    cancelled = creator if cancel_creator else waiter
    cancelled.cancel()
    with pytest.raises(asyncio.CancelledError):
        await cancelled
    release.set()
    try:
        await asyncio.wait_for(asyncio.gather(creator, waiter, return_exceptions=True), timeout=1)
        await asyncio.wait_for(fs._pipe_file("/parent/file", b"retry"), timeout=1)
        assert sim.blobs["/parent/file"] == b"retry"
    finally:
        await fs.aclose()
