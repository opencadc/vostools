"""Round-trip budgets and bounded concurrency of tree and bulk operations.

These tests pin the request shape of the performance-sensitive paths: a tree
walk lists each container once and lists one depth concurrently, metadata
lookups never download a child listing, and bulk work overlaps independent
requests without breaking ordering guarantees.
"""

from __future__ import annotations

import asyncio
import errno
import re
from collections import Counter
from typing import TYPE_CHECKING
from urllib.parse import unquote, urlsplit

import httpx
import pytest
from conftest import BASE_URL, NODES_URL, mock_transfers
from vospace_sim import VOSpaceSim

from vosfs import VOSpaceError, VOSpaceFileSystem
from vosfs import _coordination as coordination
from vosfs.filesystem import DEFAULT_BATCH_SIZE

if TYPE_CHECKING:
    import respx

_NODES_PATH = urlsplit(NODES_URL).path


class _Tracking(httpx.AsyncBaseTransport):
    """Delay every request briefly and record peak in-flight requests."""

    def __init__(self, router: respx.Router) -> None:
        self.inner = httpx.MockTransport(router.async_handler)
        self.inflight = 0
        self.peak = 0
        self.requests: list[httpx.Request] = []

    async def handle_async_request(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        self.inflight += 1
        self.peak = max(self.peak, self.inflight)
        try:
            await asyncio.sleep(0.002)
            return await self.inner.handle_async_request(request)
        finally:
            self.inflight -= 1

    def node_gets(self) -> Counter[str]:
        """Count node GETs by decoded node path (``?limit=0`` marked)."""
        counts: Counter[str] = Counter()
        for request in self.requests:
            url = str(request.url)
            if request.method == "GET" and url.startswith(NODES_URL):
                path = unquote(request.url.path.removeprefix(_NODES_PATH)) or "/"
                suffix = "?limit=0" if request.url.params.get("limit") == "0" else ""
                counts[path + suffix] += 1
        return counts

    def reset(self) -> None:
        self.requests.clear()
        self.peak = 0


def _tree(dirs: int = 3, files: int = 4) -> VOSpaceSim:
    sim = VOSpaceSim().add_container("/w")
    for d in range(dirs):
        sim.add_container(f"/w/d{d}")
        for f in range(files):
            sim.add_file(f"/w/d{d}/f{f}", b"x" * (f + 1))
    return sim


async def _fs(
    router: respx.Router, sim: VOSpaceSim | None = None, **options: object
) -> tuple[VOSpaceFileSystem, _Tracking]:
    if sim is not None:
        sim.install(router)
    tracking = _Tracking(router)
    fs = VOSpaceFileSystem(
        BASE_URL,
        transport=tracking,
        asynchronous=True,
        skip_instance_cache=True,
        **options,
    )
    return fs, tracking


# -- tree walks ----------------------------------------------------------------


async def test_find_lists_each_container_once_and_one_depth_concurrently(
    router: respx.Router,
) -> None:
    fs, tracking = await _fs(router, _tree(dirs=6))

    found = await fs._find("/w")

    assert found == sorted(f"/w/d{d}/f{f}" for d in range(6) for f in range(4))
    listings = [path for path in tracking.node_gets() if not path.endswith("?limit=0")]
    assert sorted(listings) == ["/w", *(f"/w/d{d}" for d in range(6))]
    assert all(count == 1 for count in tracking.node_gets().values())
    assert tracking.peak > 1
    await fs.aclose()


async def test_find_respects_maxdepth_and_withdirs(router: respx.Router) -> None:
    fs, _tracking = await _fs(router, _tree(dirs=2, files=1))

    shallow = await fs._find("/w", maxdepth=1, withdirs=True)

    assert shallow == ["/w", "/w/d0", "/w/d1"]
    with pytest.raises(ValueError, match="maxdepth"):
        await fs._find("/w", maxdepth=0)
    await fs.aclose()


def _deny_listing(router: respx.Router, path: str) -> None:
    router.get(url__regex=rf"^{re.escape(NODES_URL + path)}(?:\?.*)?$").mock(
        return_value=httpx.Response(403, text="denied")
    )


async def test_find_raise_applies_to_nested_containers(router: respx.Router) -> None:
    # fsspec's nested ``_walk`` drops ``on_error``; an unreadable descendant
    # must not be silently reported as empty when the caller asked to raise.
    _deny_listing(router, "/w/d1")
    fs, _tracking = await _fs(router, _tree())

    with pytest.raises(PermissionError):
        await fs._find("/w", on_error="raise")
    await fs.aclose()


async def test_find_omits_or_reports_nested_errors_by_default(
    router: respx.Router,
) -> None:
    _deny_listing(router, "/w/d1")
    fs, _tracking = await _fs(router, _tree())
    seen: list[BaseException] = []

    omitted = await fs._find("/w")
    reported = await fs._find("/w", on_error=seen.append)

    assert omitted == reported
    assert not any(path.startswith("/w/d1/") for path in omitted)
    assert len(seen) == 1
    assert isinstance(seen[0], PermissionError)
    await fs.aclose()


async def test_du_sums_listing_sizes_without_per_file_lookups(
    router: respx.Router,
) -> None:
    fs, tracking = await _fs(router, _tree())

    total = await fs._du("/w")
    sizes = await fs._du("/w", total=False)

    assert total == 3 * (1 + 2 + 3 + 4)
    assert sizes["/w/d2/f3"] == 4
    assert len(sizes) == 12
    assert not any("/f" in path for path in tracking.node_gets())
    await fs.aclose()


async def test_du_of_a_file_operand(router: respx.Router) -> None:
    fs, _tracking = await _fs(router, VOSpaceSim().add_file("/one", b"12345"))

    assert await fs._du("/one") == 5
    await fs.aclose()


# -- metadata -----------------------------------------------------------------


async def test_info_never_downloads_a_child_listing(router: respx.Router) -> None:
    sim = VOSpaceSim().add_container("/big")
    for index in range(50):
        sim.add_file(f"/big/{index:03}", b"")
    fs, tracking = await _fs(router, sim)

    info = await fs._info("/big")

    assert info["type"] == "directory"
    assert all(path.endswith("?limit=0") for path in tracking.node_gets())
    await fs.aclose()


async def test_info_is_answered_from_a_cached_parent_listing(
    router: respx.Router,
) -> None:
    fs, tracking = await _fs(router, _tree())
    await fs._ls("/w/d0")
    tracking.reset()

    info = await fs._info("/w/d0/f2")
    info["size"] = -1  # the caller's copy must not alias the cache

    assert tracking.requests == []
    assert (await fs._info("/w/d0/f2"))["size"] == 3
    await fs.aclose()


async def test_mutation_invalidates_cached_parent_listing(router: respx.Router) -> None:
    fs, tracking = await _fs(router, _tree())
    await fs._ls("/w/d0")
    await fs._pipe_file("/w/d0/f2", b"longer content")
    tracking.reset()

    assert (await fs._info("/w/d0/f2"))["size"] == len(b"longer content")
    assert tracking.requests != []
    await fs.aclose()


# -- removal ------------------------------------------------------------------


async def test_rm_of_many_paths_runs_concurrently(router: respx.Router) -> None:
    sim = VOSpaceSim()
    for index in range(10):
        sim.add_file(f"/f{index}", b"x")
    fs, tracking = await _fs(router, sim)

    await fs._rm([f"/f{index}" for index in range(10)])

    assert not any(path.startswith("/f") for path in sim.nodes)
    assert tracking.peak > 1
    await fs.aclose()


async def test_rm_of_many_paths_raises_first_failure_in_argument_order(
    router: respx.Router,
) -> None:
    sim = VOSpaceSim().add_file("/a", b"x").add_file("/b", b"x")
    fs, _tracking = await _fs(router, sim)

    with pytest.raises(FileNotFoundError, match="/missing1"):
        await fs._rm(["/a", "/missing1", "/b", "/missing2"], batch_size=1)
    assert "/a" not in sim.nodes
    assert "/b" in sim.nodes  # nothing new starts after the failure
    await fs.aclose()


async def test_rm_with_nested_targets_keeps_sequential_order(
    router: respx.Router,
) -> None:
    sim = VOSpaceSim().add_container("/d").add_file("/d/f", b"x")
    fs, _tracking = await _fs(router, sim)

    with pytest.raises(FileNotFoundError):
        await fs._rm(["/d", "/d/f"], recursive=True)
    assert "/d" not in sim.nodes
    await fs.aclose()


async def test_recursive_rm_is_concurrent_and_leaves_first(
    router: respx.Router,
) -> None:
    sim = _tree()
    sim.add_container("/w/d0/deeper").add_file("/w/d0/deeper/z", b"z")
    fs, tracking = await _fs(router, sim)

    await fs._rm("/w", recursive=True)

    order = sim.delete_requests
    assert sim.nodes.keys() == {"/"}
    for index, deleted in enumerate(order):
        later = order[index + 1 :]
        assert not any(path.startswith(f"{deleted}/") for path in later)
    assert tracking.peak > 1
    await fs.aclose()


async def test_recursive_rm_failure_reports_confirmed_progress(
    router: respx.Router,
) -> None:
    sim = _tree(dirs=1, files=3)
    sim.delete_statuses["/w/d0/f1"] = 500
    fs, _tracking = await _fs(router, sim)

    with pytest.raises(VOSpaceError) as excinfo:
        await fs._rm("/w", recursive=True)

    assert excinfo.value.failed == ["/w/d0/f1"]
    assert set(excinfo.value.completed) == {"/w/d0/f0", "/w/d0/f2"}
    assert "/w/d0" in sim.nodes
    assert "/w" in sim.nodes
    await fs.aclose()


# -- copy and transfer --------------------------------------------------------


async def test_recursive_copy_creates_each_container_once(
    router: respx.Router,
) -> None:
    sim = _tree()
    fs, tracking = await _fs(router, sim)

    await fs._copy("/w", "/copied", recursive=True)

    puts = Counter(
        unquote(request.url.path)
        for request in tracking.requests
        if request.method == "PUT" and str(request.url).startswith(NODES_URL)
    )
    assert puts == Counter(f"{_NODES_PATH}{path}" for path in ["/copied", "/copied/d0", "/copied/d1", "/copied/d2"])
    assert sim.blobs["/copied/d1/f3"] == b"xxxx"
    await fs.aclose()


async def test_recursive_get_needs_no_per_file_node_lookup(router: respx.Router, tmp_path: object) -> None:
    fs, tracking = await _fs(router, _tree())

    await fs._get("/w", f"{tmp_path}/out", recursive=True)

    assert not any("/f" in path for path in tracking.node_gets())
    await fs.aclose()


async def test_recursive_get_rejects_external_link_from_listing(router: respx.Router, tmp_path: object) -> None:
    sim = VOSpaceSim().add_container("/d").add_link("/d/l", "vos://elsewhere/x")
    fs, _tracking = await _fs(router, sim)

    with pytest.raises(NotImplementedError, match="external LinkNode"):
        await fs._get("/d", f"{tmp_path}/out", recursive=True)
    assert sim.byte_requests == []
    await fs.aclose()


async def test_empty_slice_reads_no_bytes(router: respx.Router) -> None:
    sim = VOSpaceSim().add_file("/f", b"abcdef")
    fs, _tracking = await _fs(router, sim)

    assert await fs._cat_file("/f", 3, 3) == b""
    assert await fs._cat_file("/f", None, 0) == b""
    with pytest.raises(FileNotFoundError):
        await fs._cat_file("/missing", 2, 1)
    assert sim.byte_requests == []
    await fs.aclose()


async def test_cat_ranges_fetches_ranges_concurrently_after_first_206(
    router: respx.Router,
) -> None:
    blob = bytes(range(256)) * 64
    mock_transfers(router, {"/big": blob}, honour_range=True)
    fs, tracking = await _fs(router)
    starts = [i * 1000 for i in range(6)]
    ends = [start + 10 for start in starts]

    result = await fs._cat_ranges(["/big"] * 6, starts, ends)

    assert result == [blob[s:e] for s, e in zip(starts, ends, strict=True)]
    negotiations = [r for r in tracking.requests if r.method == "POST"]
    assert len(negotiations) == 1
    assert tracking.peak > 1
    await fs.aclose()


async def test_cat_ranges_falls_back_when_a_later_range_is_ignored(
    router: respx.Router,
) -> None:
    blob = b"0123456789" * 10
    served = 0

    def flaky(_request: httpx.Request) -> httpx.Response:
        nonlocal served
        served += 1
        if served == 1:
            return httpx.Response(
                206,
                content=blob[0:5],
                headers={"Content-Range": f"bytes 0-4/{len(blob)}"},
            )
        return httpx.Response(200, content=blob)

    # Registered first, so it wins over the byte route ``mock_transfers`` adds.
    router.route(url__regex=rf"^{re.escape(BASE_URL)}/files").mock(side_effect=flaky)
    mock_transfers(router, {"/f": blob}, honour_range=True)
    fs, _tracking = await _fs(router)

    result = await fs._cat_ranges(["/f"] * 3, [0, 20, 40], [5, 25, 45])

    assert result == [blob[0:5], blob[20:25], blob[40:45]]
    await fs.aclose()


# -- defaults and helpers -----------------------------------------------------


def test_default_batch_size_bounds_bulk_concurrency() -> None:
    default = VOSpaceFileSystem(BASE_URL, skip_instance_cache=True)
    explicit = VOSpaceFileSystem(BASE_URL, skip_instance_cache=True, batch_size=4)

    assert default.batch_size == DEFAULT_BATCH_SIZE
    assert explicit.batch_size == 4
    assert "batch_size" not in default.storage_options


async def test_run_bounded_cancels_children_when_cancelled() -> None:
    started = asyncio.Event()
    cancelled: list[int] = []

    async def worker(index: int) -> None:
        started.set()
        try:
            await asyncio.sleep(10)
        except asyncio.CancelledError:
            cancelled.append(index)
            raise

    task = asyncio.ensure_future(coordination.run_bounded([lambda i=i: worker(i) for i in range(3)], 2))
    await started.wait()
    task.cancel()
    with pytest.raises(asyncio.CancelledError):
        await task
    assert sorted(cancelled) == [0, 1]


def test_effective_limit() -> None:
    assert coordination.effective_limit(None, 5) == 5
    assert coordination.effective_limit(-1, 0) == 1
    assert coordination.effective_limit(8, 100) == 8


async def test_move_removes_sources_after_verifying_destination(
    router: respx.Router,
) -> None:
    sim = _tree(dirs=2, files=2)
    fs, _tracking = await _fs(router, sim)

    await fs._move("/w", "/moved", recursive=True)

    assert sim.blobs["/moved/d1/f1"] == b"xx"
    assert not any(path.startswith("/w") for path in sim.nodes)
    await fs.aclose()


async def test_move_source_deletion_failure_reports_progress(
    router: respx.Router,
) -> None:
    sim = _tree(dirs=1, files=2)
    sim.delete_statuses["/w/d0/f1"] = 500
    fs, _tracking = await _fs(router, sim)

    with pytest.raises(VOSpaceError, match="deletion failed") as excinfo:
        await fs._move("/w", "/moved", recursive=True)

    assert excinfo.value.failed == ["/w/d0/f1"]
    assert "/w/d0/f0" in excinfo.value.completed
    assert "/w" in sim.nodes
    assert getattr(excinfo.value, "errno", None) != errno.ENOTEMPTY
    await fs.aclose()
