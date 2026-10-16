"""Tests for VOSpace namespace and mutation primitives (section 10)."""

import httpx
import pytest
import respx
from conftest import (
    AUTHORITY,
    NODES_URL,
    container_xml,
    data_child,
    make_fs,
    make_sim_fs,
    mock_capabilities,
)
from namespace_support import _install_percent_mutation_routes
from vospace_sim import VOSpaceSim

# --- mkdir / makedirs -----------------------------------------------------------


async def test_mkdir_creates_container(router: respx.Router) -> None:
    sim = VOSpaceSim()
    fs = make_sim_fs(router, sim)
    await fs._mkdir("/dir", create_parents=False)
    assert sim.nodes["/dir"] == "container"
    await fs.aclose()


async def test_delete_then_recreate_discards_stale_node_authority(
    router: respx.Router,
) -> None:
    sim = VOSpaceSim().add_container("/dir").with_authority("/dir", "old.example!vault")
    fs = make_sim_fs(router, sim)

    await fs._delete_node("/dir")
    await fs._create_container("/dir")

    assert (await fs._info("/dir"))["uri"] == f"vos://{AUTHORITY}/dir"
    await fs.aclose()


async def test_makedirs_creates_ancestors_top_down(router: respx.Router) -> None:
    sim = VOSpaceSim()
    fs = make_sim_fs(router, sim)
    await fs._makedirs("/a/b/c", exist_ok=False)
    assert sim.nodes["/a"] == "container"
    assert sim.nodes["/a/b"] == "container"
    assert sim.nodes["/a/b/c"] == "container"
    await fs.aclose()


async def test_makedirs_exist_ok_false_on_existing(router: respx.Router) -> None:
    sim = VOSpaceSim().add_container("/a")
    fs = make_sim_fs(router, sim)
    with pytest.raises(FileExistsError):
        await fs._makedirs("/a", exist_ok=False)
    await fs.aclose()


async def test_makedirs_exist_ok_true_tolerates_existing(router: respx.Router) -> None:
    sim = VOSpaceSim().add_container("/a")
    fs = make_sim_fs(router, sim)
    await fs._makedirs("/a/b", exist_ok=True)
    assert sim.nodes["/a/b"] == "container"
    await fs.aclose()


# --- rm / rmdir -----------------------------------------------------------------


async def test_rm_file(router: respx.Router) -> None:
    sim = VOSpaceSim().add_file("/f.txt", b"data")
    fs = make_sim_fs(router, sim)
    await fs._rm_file("/f.txt")
    assert "/f.txt" not in sim.nodes
    await fs.aclose()


async def test_rmdir_empty(router: respx.Router) -> None:
    sim = VOSpaceSim().add_container("/empty")
    fs = make_sim_fs(router, sim)
    await fs._rmdir("/empty")
    assert "/empty" not in sim.nodes
    await fs.aclose()


async def test_rmdir_non_empty_fails(router: respx.Router) -> None:
    sim = VOSpaceSim().add_container("/d").add_file("/d/f", b"x")
    fs = make_sim_fs(router, sim)
    with pytest.raises(OSError, match="not empty"):
        await fs._rmdir("/d")
    assert "/d" in sim.nodes  # not deleted
    listing_calls = [call for call in router.calls if "limit=1" in str(call.request.url)]
    assert listing_calls
    await fs.aclose()


async def test_ls_refresh_and_returned_copies(router: respx.Router) -> None:
    sim = VOSpaceSim().add_container("/d").add_file("/d/f", b"x")
    fs = make_sim_fs(router, sim)
    listing = await fs._ls("/d", detail=True)
    assert listing[0]["name"] == "/d/f"
    listing.clear()
    listing_again = await fs._ls("/d", detail=True)
    assert [entry["name"] for entry in listing_again] == ["/d/f"]
    listing_again[0]["name"] = "mutated"
    assert (await fs._ls("/d", detail=True))[0]["name"] == "/d/f"

    before = len(router.calls)
    assert (await fs._info("/d/f"))["size"] == 1
    assert len(router.calls) == before

    sim.add_file("/d/g", b"yy")
    assert [entry["name"] for entry in await fs._ls("/d")] == ["/d/f"]
    refreshed = [entry["name"] for entry in await fs._ls("/d", refresh=True)]
    assert refreshed == ["/d/f", "/d/g"]
    await fs.aclose()


async def test_walk_bottom_up(router: respx.Router) -> None:
    sim = (
        VOSpaceSim()
        .add_container("/tree")
        .add_container("/tree/sub")
        .add_file("/tree/sub/a", b"a")
        .add_file("/tree/b", b"b")
    )
    fs = make_sim_fs(router, sim)
    topdown = [root async for root, _dirs, _files in fs._walk("/tree")]
    bottomup = [root async for root, _dirs, _files in fs._walk("/tree", topdown=False)]
    assert topdown[0] == "/tree"
    assert bottomup[-1] == "/tree"
    assert bottomup.index("/tree/sub") < bottomup.index("/tree")
    await fs.aclose()


async def test_recursive_rm_deletes_leaves_first(router: respx.Router) -> None:
    sim = (
        VOSpaceSim()
        .add_container("/tree")
        .add_container("/tree/sub")
        .add_file("/tree/sub/a", b"a")
        .add_file("/tree/b", b"b")
    )
    fs = make_sim_fs(router, sim)
    await fs._rm("/tree", recursive=True)
    assert not any(p.startswith("/tree") for p in sim.nodes)
    await fs.aclose()


@pytest.mark.parametrize(
    ("parent", "child_uri"),
    [
        ("/tree", f"vos://{AUTHORITY}/tree/sub/escape"),
        ("/tree", f"vos://{AUTHORITY}/tree/%2E%2E/escape"),
        ("/tree", f"vos://{AUTHORITY}/sibling"),
        ("/", f"vos://{AUTHORITY}/top/escape"),
        ("/", f"vos://{AUTHORITY}/%2E%2E"),
        ("/", "vos://other.example!vault/escape"),
    ],
)
async def test_recursive_rm_rejects_non_immediate_listing_children(
    router: respx.Router, parent: str, child_uri: str
) -> None:
    suffix = "" if parent == "/" else parent
    document = container_xml(f"vos://{AUTHORITY}{suffix}", data_child(child_uri, 1))
    mock_capabilities(router)
    router.get(NODES_URL + suffix).mock(return_value=httpx.Response(200, content=document))
    deletes = router.delete(url__regex=rf"^{NODES_URL}").mock(return_value=httpx.Response(200))
    fs = make_fs(router, asynchronous=True)

    with pytest.raises(OSError, match="recursive removal failed") as excinfo:
        await fs._rm(parent, recursive=True)

    assert excinfo.value.completed == []
    assert excinfo.value.failed == [parent]
    assert deletes.call_count == 0
    await fs.aclose()


async def test_recursive_rm_reports_leaves_first_partial_completion(
    router: respx.Router,
) -> None:
    sim = VOSpaceSim().add_container("/tree").add_file("/tree/a", b"a").add_file("/tree/b", b"b")
    sim.delete_statuses["/tree/b"] = 500
    fs = make_sim_fs(router, sim)

    with pytest.raises(OSError, match="recursive removal failed") as excinfo:
        await fs._rm("/tree", recursive=True)

    assert excinfo.value.completed == ["/tree/a"]
    assert excinfo.value.failed == ["/tree/b"]
    assert sim.delete_requests == ["/tree/a", "/tree/b"]
    assert "/tree" in sim.nodes
    await fs.aclose()


async def test_non_recursive_rm_on_non_empty_fails(router: respx.Router) -> None:
    sim = VOSpaceSim().add_container("/d").add_file("/d/f", b"x")
    fs = make_sim_fs(router, sim)
    with pytest.raises(OSError, match="not empty"):
        await fs._rm("/d", recursive=False)
    await fs.aclose()


async def test_rm_maxdepth_unsupported(router: respx.Router) -> None:
    sim = VOSpaceSim().add_container("/d")
    fs = make_sim_fs(router, sim)
    with pytest.raises(NotImplementedError):
        await fs._rm("/d", recursive=True, maxdepth=2)
    await fs.aclose()


def test_mkdir_preserves_literal_percent_parent(router: respx.Router) -> None:
    created, _deleted = _install_percent_mutation_routes(router, {})
    fs = make_fs(router)

    fs.mkdir("vos://root/100%41/new", create_parents=True)

    assert "/root/100%2541" in created
    assert "/root/100%2541/new" in created
    assert not any("100A" in path for path in created)
    fs.close()


async def test_rm_file_refuses_a_container(router: respx.Router) -> None:
    sim = VOSpaceSim().add_container("/dir")
    fs = make_sim_fs(router, sim)
    with pytest.raises(IsADirectoryError):
        await fs._rm_file("/dir")
    await fs.aclose()


async def test_incomplete_listing_refresh_evicts_stale_cached_entry(
    router: respx.Router,
) -> None:
    malformed = container_xml(
        f"vos://{AUTHORITY}/tree",
        data_child(f"vos://{AUTHORITY}/tree/sub/escape", 1),
    )
    mock_capabilities(router)
    router.get(f"{NODES_URL}/tree").mock(return_value=httpx.Response(200, content=malformed))
    fs = make_fs(router, asynchronous=True)
    complete = [{"name": "/tree/known", "type": "file", "size": 1}]
    fs.dircache["/tree"] = complete

    with pytest.raises(OSError, match="immediate descendant"):
        await fs._fetch_listing("/tree")

    assert "/tree" not in fs.dircache
    await fs.aclose()


def test_invalidate_cache_clears_path_or_all_without_io(router: respx.Router) -> None:
    fs = make_fs(router)
    fs.dircache["/"] = []
    fs.dircache["/tree"] = []
    fs.dircache["/tree/sub"] = []
    fs.dircache["/other"] = []

    fs.invalidate_cache("/tree")
    assert list(fs.dircache) == ["/other"]
    fs.invalidate_cache()
    assert list(fs.dircache) == []
    assert len(router.calls) == 0
    fs.close()
