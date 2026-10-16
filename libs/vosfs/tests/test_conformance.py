"""Tests for the fsspec conformance surface (section 11)."""

import asyncio
from collections import Counter
from pathlib import Path

import fsspec
import httpx
import pytest
import respx
from conftest import BASE_URL, SYNC_URL, make_fs, make_sim_fs, target_path
from fsspec.callbacks import Callback
from vospace_sim import VOSpaceSim

# --- client-derived traversal ---------------------------------------------------


def test_walk_find_glob_du(router: respx.Router) -> None:
    sim = (
        VOSpaceSim()
        .add_container("/d")
        .add_file("/d/a.txt", b"aaa")
        .add_container("/d/sub")
        .add_file("/d/sub/b.txt", b"bb")
    )
    fs = make_sim_fs(router, sim, asynchronous=False)
    assert sorted(fs.find("/d")) == ["/d/a.txt", "/d/sub/b.txt"]
    assert fs.glob("/d/*.txt") == ["/d/a.txt"]
    assert fs.du("/d") == 5
    walked = {root: (sorted(dirs), sorted(files)) for root, dirs, files in fs.walk("/d")}
    assert walked["/d"] == (["sub"], ["a.txt"])
    fs.close()


def test_ukey_and_checksum(router: respx.Router) -> None:
    sim = VOSpaceSim().add_file("/f", b"data")
    fs = make_sim_fs(router, sim, asynchronous=False)
    assert isinstance(fs.ukey("/f"), str)
    assert isinstance(fs.checksum("/f"), int)
    fs.close()


def test_read_block(router: respx.Router) -> None:
    sim = VOSpaceSim().add_file("/f", b"0123456789")
    fs = make_sim_fs(router, sim, asynchronous=False)
    assert fs.read_block("/f", 2, 3) == b"234"
    fs.close()


def test_recursive_get_materializes_tree_without_container_byte_negotiation(
    router: respx.Router,
    tmp_path: Path,
) -> None:
    sim = (
        VOSpaceSim()
        .add_container("/tree")
        .add_container("/tree/empty")
        .add_container("/tree/nested")
        .add_file("/tree/root.bin", b"root-bytes")
        .add_file("/tree/nested/leaf.bin", b"leaf-bytes")
    )
    fs = make_sim_fs(router, sim, asynchronous=False)
    target = tmp_path / "download"
    try:
        fs.get("/tree", str(target), recursive=True)

        assert target.is_dir()
        assert (target / "empty").is_dir()
        assert list((target / "empty").iterdir()) == []
        assert (target / "root.bin").read_bytes() == b"root-bytes"
        assert (target / "nested" / "leaf.bin").read_bytes() == b"leaf-bytes"

        def negotiated_target(call: httpx.Request) -> str:
            target = call.url.params.get("TARGET")
            if target:
                return "/" + target.split("://", 1)[-1].split("/", 1)[-1]
            return target_path(call.content)

        negotiations = Counter(
            negotiated_target(call.request)
            for call in router.calls
            if call.request.method == "POST" and str(call.request.url).startswith(SYNC_URL)
        )
        byte_gets = Counter(
            call.request.url.params["p"]
            for call in router.calls
            if call.request.method == "GET" and call.request.url.path.endswith("/files")
        )
        expected = Counter({"/tree/root.bin": 1, "/tree/nested/leaf.bin": 1})
        assert negotiations == expected
        assert byte_gets == expected
    finally:
        fs.close()


def test_get_paired_list_materializes_containers(
    router: respx.Router,
    tmp_path: Path,
) -> None:
    sim = VOSpaceSim().add_container("/empty").add_file("/file.bin", b"paired")
    fs = make_sim_fs(router, sim, asynchronous=False)
    local_empty = tmp_path / "empty"
    local_file = tmp_path / "file.bin"
    try:
        fs.get(["/empty", "/file.bin"], [str(local_empty), str(local_file)])
        assert local_empty.is_dir()
        assert list(local_empty.iterdir()) == []
        assert local_file.read_bytes() == b"paired"
    finally:
        fs.close()


# --- facades --------------------------------------------------------------------


async def test_async_facade_hooks(router: respx.Router) -> None:
    sim = VOSpaceSim().add_file("/f", b"async")
    sim.install(router)
    fs = make_fs(router, asynchronous=True)
    assert (await fs._info("/f"))["type"] == "file"
    assert await fs._cat_file("/f") == b"async"
    await fs.aclose()


def test_sync_facade_mirrors(router: respx.Router) -> None:
    sim = VOSpaceSim().add_file("/f", b"sync")
    fs = make_sim_fs(router, sim, asynchronous=False)
    assert fs.info("/f")["type"] == "file"
    assert fs.cat_file("/f") == b"sync"
    fs.close()


async def test_open_async_unsupported(router: respx.Router) -> None:
    sim = VOSpaceSim().add_file("/f", b"x")
    sim.install(router)
    fs = make_fs(router, asynchronous=True)
    with pytest.raises(NotImplementedError):
        await fs.open_async("/f")
    await fs.aclose()


# --- callbacks ------------------------------------------------------------------


async def test_get_file_reports_byte_callback(router: respx.Router, tmp_path: object) -> None:
    sim = VOSpaceSim().add_file("/f", b"0123456789")
    sim.install(router)
    fs = make_fs(router, asynchronous=True)
    seen: dict[str, int] = {"bytes": 0}

    class Recorder(Callback):
        def relative_update(self, inc: int = 1) -> None:
            seen["bytes"] += inc

    local = tmp_path / "out"  # type: ignore[operator]
    await fs._get_file("/f", str(local), callback=Recorder())
    assert seen["bytes"] == 10
    await fs.aclose()


# --- directory cache + mutation invalidation ------------------------------------


def test_listings_cache_invalidated_on_mutation(router: respx.Router) -> None:
    sim = VOSpaceSim().add_container("/d").add_file("/d/a", b"a")
    sim.install(router)
    fs = make_fs(router, use_listings_cache=True)
    assert fs.ls("/d", detail=False) == ["/d/a"]
    fs.pipe_file("/d/b", b"b")  # mutation invalidates the parent listing
    assert sorted(fs.ls("/d", detail=False)) == ["/d/a", "/d/b"]
    fs.close()


def test_recursive_removal_clears_subtree_cache(router: respx.Router) -> None:
    sim = VOSpaceSim().add_container("/d").add_container("/d/sub").add_file("/d/sub/x", b"x")
    sim.install(router)
    fs = make_fs(router, use_listings_cache=True)
    fs.ls("/d/sub")  # cache a descendant listing
    assert "/d/sub" in fs.dircache
    fs.rm("/d", recursive=True)
    # The whole subtree is evicted, not just /d and its immediate parent.
    assert "/d/sub" not in fs.dircache
    fs.close()


# --- cache wrappers -------------------------------------------------------------


def test_filecache_wrapper_reads(router: respx.Router, tmp_path: object) -> None:
    sim = VOSpaceSim().add_file("/f.txt", b"cached-bytes")
    sim.install(router)
    transport = httpx.MockTransport(router.async_handler)
    options = {
        "vos": {
            "endpoint_url": BASE_URL,
            "transport": transport,
            "skip_instance_cache": True,
        },
        "filecache": {"cache_storage": str(tmp_path)},  # type: ignore[arg-type]
    }
    with fsspec.open("filecache::vos://f.txt", **options) as handle:
        assert handle.read() == b"cached-bytes"


def test_simplecache_wrapper_round_trip(router: respx.Router, tmp_path: object) -> None:
    files: dict[str, bytes] = {}
    sim = VOSpaceSim()
    sim.blobs = files
    sim.install(router)
    transport = httpx.MockTransport(router.async_handler)
    options = {
        "vos": {
            "endpoint_url": BASE_URL,
            "transport": transport,
            "skip_instance_cache": True,
        },
        "simplecache": {"cache_storage": str(tmp_path)},  # type: ignore[arg-type]
    }
    with fsspec.open("simplecache::vos://w.txt", mode="wb", **options) as handle:
        handle.write(b"written-through")
    assert files["/w.txt"] == b"written-through"


# --- cancellation ---------------------------------------------------------------


async def test_cancellation_propagates(router: respx.Router) -> None:
    async def slow_stream() -> object:
        await asyncio.sleep(0.5)
        yield b"never"

    # Register the slow byte route first so it wins respx's first-match ordering.
    router.route(url__regex=rf"^{BASE_URL}/files").mock(
        side_effect=lambda _r: httpx.Response(200, content=slow_stream()),
    )
    VOSpaceSim().add_file("/slow").install(router)
    fs = make_fs(router, asynchronous=True)
    task = asyncio.create_task(fs._cat_file("/slow"))
    await asyncio.sleep(0.05)
    task.cancel()
    with pytest.raises(asyncio.CancelledError):
        await task
    await fs.aclose()
