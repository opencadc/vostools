"""Move behavior for the VOSpace namespace contract."""

import asyncio
from dataclasses import replace

import httpx
import pytest
import respx
from conftest import (
    AUTHORITY,
    BASE_URL,
    NODES_URL,
    call_urls,
    container_xml,
    data_child,
    make_fs,
    make_sim_fs,
)
from namespace_support import _install_percent_mutation_routes
from vospace_sim import VOSpaceSim

from vosfs import errors, nodes
from vosfs.xmlio import safe_parse


async def test_mv_file_copies_then_deletes_data(router: respx.Router) -> None:
    sim = VOSpaceSim().add_file("/src", b"moved")
    fs = make_sim_fs(router, sim)

    await fs._mv_file("/src", "/dst")

    assert sim.blobs["/dst"] == b"moved"
    assert "/src" not in sim.nodes
    await fs.aclose()


async def test_mv_file_same_data_path_is_noop(router: respx.Router) -> None:
    sim = VOSpaceSim().add_file("/src", b"source")
    fs = make_sim_fs(router, sim)

    await fs._mv_file("/src", "/src")

    assert [call.request for call in router.calls if call.request.method != "GET"] == []
    assert sim.nodes["/src"] == "data"
    assert sim.blobs["/src"] == b"source"
    assert sim.delete_requests == []
    await fs.aclose()


@pytest.mark.parametrize("destination", ["/src", "/src/dest"])
async def test_mv_file_rejects_container_before_mutation(
    router: respx.Router,
    destination: str,
) -> None:
    sim = VOSpaceSim().add_container("/src").add_file("/src/a", b"a")
    fs = make_sim_fs(router, sim)

    with pytest.raises(IsADirectoryError, match="source is a container"):
        await fs._mv_file("/src", destination)

    assert [call.request for call in router.calls if call.request.method != "GET"] == []
    assert sim.nodes["/src"] == "container"
    assert sim.blobs["/src/a"] == b"a"
    assert destination == "/src" or destination not in sim.nodes
    assert sim.delete_requests == []
    await fs.aclose()


async def test_mv_file_rejects_existing_data_destination_before_mutation(
    router: respx.Router,
) -> None:
    sim = VOSpaceSim().add_file("/src", b"source").add_file("/dst", b"existing")
    fs = make_sim_fs(router, sim)

    with pytest.raises(FileExistsError, match="move destination already exists"):
        await fs._mv_file("/src", "/dst")

    assert [call.request for call in router.calls if call.request.method != "GET"] == []
    assert sim.nodes["/src"] == "data"
    assert sim.blobs["/src"] == b"source"
    assert sim.nodes["/dst"] == "data"
    assert sim.blobs["/dst"] == b"existing"
    assert sim.delete_requests == []
    await fs.aclose()


@pytest.mark.parametrize("destination_state", ["absent", "existing", "same"])
async def test_mv_file_rejects_link_before_mutation(
    router: respx.Router,
    destination_state: str,
) -> None:
    target = f"vos://{AUTHORITY}/target"
    sim = VOSpaceSim().add_file("/target", b"target").add_link("/src", target)
    destination = "/src" if destination_state == "same" else "/dst"
    if destination_state == "existing":
        sim.add_file(destination, b"existing")
    fs = make_sim_fs(router, sim)

    with pytest.raises(NotImplementedError, match="moving a LinkNode"):
        await fs._mv_file("/src", destination)

    node_requests = [call.request for call in router.calls if str(call.request.url).startswith(NODES_URL)]
    assert ("GET", f"{NODES_URL}/src?limit=0") in [(request.method, str(request.url)) for request in node_requests]
    assert [call.request for call in router.calls if call.request.method != "GET"] == []
    assert sim.nodes["/src"] == "link"
    assert sim.targets["/src"] == target
    if destination_state == "absent":
        assert destination not in sim.nodes
        assert destination not in sim.blobs
    elif destination_state == "existing":
        assert sim.nodes[destination] == "data"
        assert sim.blobs[destination] == b"existing"
    assert sim.delete_requests == []
    await fs.aclose()


def test_move_requires_absent_destination(router: respx.Router) -> None:
    sim = VOSpaceSim().add_file("/src", b"x").add_file("/dst", b"exists")
    sim.install(router)
    fs = make_fs(router)
    with pytest.raises(FileExistsError):
        fs.mv("/src", "/dst")
    fs.close()


def test_move_copies_then_deletes_source(router: respx.Router) -> None:
    sim = VOSpaceSim().add_file("/src", b"moved")
    sim.install(router)
    fs = make_fs(router)
    fs.mv("/src", "/dst")
    assert sim.blobs["/dst"] == b"moved"
    assert "/src" not in sim.nodes
    fs.close()


@pytest.mark.parametrize("destination_state", ["absent", "existing", "same"])
def test_move_rejects_link_before_mutation(
    router: respx.Router,
    destination_state: str,
) -> None:
    target = f"vos://{AUTHORITY}/target"
    sim = VOSpaceSim().add_file("/target", b"target").add_link("/src", target)
    destination = "/src" if destination_state == "same" else "/dst"
    if destination_state == "existing":
        sim.add_file(destination, b"existing")
    sim.install(router)
    fs = make_fs(router)

    with pytest.raises(NotImplementedError, match="moving a LinkNode"):
        fs.mv("/src", destination)

    node_requests = [call.request for call in router.calls if str(call.request.url).startswith(NODES_URL)]
    assert ("GET", f"{NODES_URL}/src?limit=0") in [(request.method, str(request.url)) for request in node_requests]
    assert [call.request for call in router.calls if call.request.method != "GET"] == []
    assert sim.nodes["/src"] == "link"
    assert sim.targets["/src"] == target
    if destination_state == "absent":
        assert destination not in sim.nodes
        assert destination not in sim.blobs
    elif destination_state == "existing":
        assert sim.nodes[destination] == "data"
        assert sim.blobs[destination] == b"existing"
    assert sim.delete_requests == []
    fs.close()


def test_move_preserves_literal_percent_destination_before_source_delete(
    router: respx.Router,
) -> None:
    files = {"/move-source": b"moved"}
    _created, deleted = _install_percent_mutation_routes(router, files)
    fs = make_fs(router)

    fs.mv("/move-source", "vos://moved%41")

    assert files == {"/moved%41": b"moved"}
    assert deleted == ["/move-source"]
    assert all("movedA" not in str(call.request.url) for call in router.calls)
    byte_puts = call_urls(router, "PUT", f"{BASE_URL}/files")
    assert byte_puts == [f"{BASE_URL}/files?p=/moved%2541"]
    fs.close()


def test_recursive_move_keeps_source_when_one_child_copy_fails(
    router: respx.Router,
) -> None:
    source_listing = container_xml(
        f"vos://{AUTHORITY}/src",
        data_child(f"vos://{AUTHORITY}/src/a", 1) + data_child(f"vos://{AUTHORITY}/src/b", 0),
    )
    files = {"/src/a": b"a"}
    _created, deleted = _install_percent_mutation_routes(
        router,
        files,
        listings={"/src": source_listing},
        data_nodes={"/src/a", "/src/b"},
    )
    fs = make_fs(router)

    with pytest.raises(errors.VOSpaceError, match="copy failed") as excinfo:
        fs.mv("/src", "/dest", recursive=True)

    assert excinfo.value.completed == ["/dest", "/dest/a"]
    assert excinfo.value.failed == ["/dest/b"]
    assert deleted == []
    assert files["/src/a"] == b"a"
    fs.close()


def test_recursive_move_keeps_source_when_copy_omits_one_child(
    router: respx.Router,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    sim = VOSpaceSim().add_container("/src").add_file("/src/a", b"a").add_file("/src/b", b"b")
    byte_op = sim._byte_op

    def incomplete_copy(request: httpx.Request) -> httpx.Response:
        if request.method == "PUT" and request.url.params["p"] == "/dest/b":
            return httpx.Response(201)
        return byte_op(request)

    monkeypatch.setattr(sim, "_byte_op", incomplete_copy)
    sim.install(router)
    fs = make_fs(router)

    with pytest.raises(errors.VOSpaceError, match="incomplete") as excinfo:
        fs.mv("/src", "/dest", recursive=True)

    assert excinfo.value.completed == ["/dest", "/dest/a"]
    assert excinfo.value.failed == ["/dest/b"]
    assert sim.nodes["/dest"] == "container"
    assert sim.blobs["/dest/a"] == b"a"
    assert "/dest/b" not in sim.nodes
    assert sim.blobs["/src/a"] == b"a"
    assert sim.blobs["/src/b"] == b"b"
    assert sim.delete_requests == []
    fs.close()


def test_recursive_move_maxdepth_retains_excluded_descendants(
    router: respx.Router,
) -> None:
    sim = (
        VOSpaceSim()
        .add_container("/src")
        .add_file("/src/top", b"top")
        .add_container("/src/sub")
        .add_file("/src/sub/deep", b"deep")
    )
    sim.install(router)
    fs = make_fs(router)

    fs.mv("/src", "/dest", recursive=True, maxdepth=1)

    assert sim.blobs["/dest/top"] == b"top"
    assert sim.nodes["/dest/sub"] == "container"
    assert "/dest/sub/deep" not in sim.nodes
    assert sim.blobs["/src/sub/deep"] == b"deep"
    assert sim.nodes["/src"] == "container"
    assert sim.nodes["/src/sub"] == "container"
    assert "/src/top" not in sim.nodes
    assert sim.delete_requests == ["/src/top"]
    fs.close()


def test_move_rejects_destination_within_source_before_mutation(
    router: respx.Router,
) -> None:
    sim = VOSpaceSim().add_container("/src").add_file("/src/a", b"a")
    sim.install(router)
    fs = make_fs(router)

    with pytest.raises(ValueError, match="within the source"):
        fs.mv("/src", "/src/dest", recursive=True)

    assert [call.request for call in router.calls if call.request.method != "GET"] == []
    assert sim.blobs["/src/a"] == b"a"
    assert "/src/dest" not in sim.nodes
    assert sim.delete_requests == []
    fs.close()


def test_recursive_move_rejects_child_link_before_mutation(
    router: respx.Router,
) -> None:
    target = f"vos://{AUTHORITY}/target"
    sim = (
        VOSpaceSim()
        .add_file("/target", b"target")
        .add_container("/src")
        .add_file("/src/a", b"a")
        .add_link("/src/link", target)
    )
    sim.install(router)
    fs = make_fs(router)

    with pytest.raises(NotImplementedError, match="moving a LinkNode"):
        fs.mv("/src", "/dest", recursive=True)

    assert [call.request for call in router.calls if call.request.method != "GET"] == []
    assert sim.nodes["/src/link"] == "link"
    assert sim.blobs["/src/a"] == b"a"
    assert "/dest" not in sim.nodes
    assert sim.delete_requests == []
    fs.close()


def test_move_same_path_is_noop(router: respx.Router) -> None:
    sim = VOSpaceSim().add_file("/src", b"x")
    sim.install(router)
    fs = make_fs(router)
    fs.mv("/src", "/src")
    assert sim.blobs["/src"] == b"x"
    fs.close()


def test_move_nonempty_directory_same_path_is_rejected_before_mutation(
    router: respx.Router,
) -> None:
    sim = VOSpaceSim().add_container("/src").add_file("/src/a", b"a")
    sim.install(router)
    fs = make_fs(router)

    with pytest.raises(FileExistsError, match="destination already exists"):
        fs.mv("/src", "/src")

    assert [call.request for call in router.calls if call.request.method != "GET"] == []
    assert sim.nodes["/src"] == "container"
    assert sim.blobs["/src/a"] == b"a"
    assert sim.delete_requests == []
    fs.close()


def test_move_empty_directory_creates_destination(router: respx.Router) -> None:
    # Regression: a non-recursive move of an empty container must not delete the
    # source without creating the destination (a data-loss bug).
    sim = VOSpaceSim().add_container("/emptydir")
    sim.install(router)
    fs = make_fs(router)
    fs.mv("/emptydir", "/dest")
    assert "/emptydir" not in sim.nodes
    assert sim.nodes["/dest"] == "container"
    fs.close()


async def test_server_move_renames_without_copying_bytes(router: respx.Router) -> None:
    sim = VOSpaceSim().add_file("/src", b"moved")
    fs = make_sim_fs(router, sim)
    fs._bindings = replace(await fs._get_bindings(), transfers_url=f"{BASE_URL}/transfers")
    job = f"{BASE_URL}/transfers/job-1"

    def submit(request: httpx.Request) -> httpx.Response:
        document = safe_parse(request.content)
        assert document.findtext(f"{{{nodes.VOSPACE_NS}}}target") == f"vos://{AUTHORITY}/src"
        assert document.findtext(f"{{{nodes.VOSPACE_NS}}}direction") == f"vos://{AUTHORITY}/dst"
        assert document.findtext(f"{{{nodes.VOSPACE_NS}}}keepBytes") == "false"
        assert document.find(f"{{{nodes.VOSPACE_NS}}}protocol") is None
        return httpx.Response(303, headers={"Location": job})

    def run(request: httpx.Request) -> httpx.Response:
        assert request.content == b"PHASE=RUN"
        content = sim.blobs["/src"]
        sim._transition_node("/src", wire_type=None)
        sim.add_file("/dst", content)
        return httpx.Response(303, headers={"Location": job})

    router.post(f"{BASE_URL}/transfers").mock(side_effect=submit)
    start = router.post(f"{job}/phase").mock(side_effect=run)
    router.get(url__regex=rf"^{job}").mock(
        return_value=httpx.Response(
            200,
            content=(
                b"<uws:job xmlns:uws='http://www.ivoa.net/xml/UWS/v1.0'><uws:phase>COMPLETED</uws:phase></uws:job>"
            ),
        )
    )
    await fs._mv_file("/src", "/dst")
    assert "/src" not in sim.nodes
    assert sim.blobs["/dst"] == b"moved"
    assert start.called
    assert not any("/files" in str(call.request.url) for call in router.calls)
    await fs.aclose()


async def test_cancelled_server_move_aborts_the_job(router: respx.Router) -> None:
    sim = VOSpaceSim().add_file("/src", b"moved")
    fs = make_sim_fs(router, sim)
    fs._bindings = replace(await fs._get_bindings(), transfers_url=f"{BASE_URL}/transfers")
    job = f"{BASE_URL}/transfers/job-1"
    aborted = asyncio.Event()
    polling = asyncio.Event()
    router.post(f"{BASE_URL}/transfers").mock(return_value=httpx.Response(303, headers={"Location": job}))

    async def job_get(_request: httpx.Request) -> httpx.Response:
        polling.set()
        await aborted.wait()
        return httpx.Response(
            200,
            content=b"<uws:job xmlns:uws='http://www.ivoa.net/xml/UWS/v1.0'><uws:phase>ABORTED</uws:phase></uws:job>",
        )

    def phase(request: httpx.Request) -> httpx.Response:
        if request.content == b"PHASE=ABORT":
            aborted.set()
        else:
            assert request.content == b"PHASE=RUN"
        return httpx.Response(303, headers={"Location": job})

    router.get(url__regex=rf"^{job}").mock(side_effect=job_get)
    router.post(f"{job}/phase").mock(side_effect=phase)
    task = asyncio.create_task(fs._mv_file("/src", "/dst"))
    await asyncio.wait_for(polling.wait(), timeout=1)
    task.cancel()
    with pytest.raises(asyncio.CancelledError):
        await task
    assert aborted.is_set()
    assert sim.blobs["/src"] == b"moved"
    await fs.aclose()


@pytest.mark.parametrize("rejection", ["absent", "http", "job"])
async def test_unsupported_move_job_keeps_client_transfer_fallback(router: respx.Router, rejection: str) -> None:
    sim = VOSpaceSim().add_file("/src", b"moved")
    fs = make_sim_fs(router, sim)
    if rejection != "absent":
        fs._bindings = replace(await fs._get_bindings(), transfers_url=f"{BASE_URL}/transfers")
        if rejection == "http":
            router.post(f"{BASE_URL}/transfers").mock(return_value=httpx.Response(501))
        else:
            job = f"{BASE_URL}/transfers/unsupported"
            router.post(f"{BASE_URL}/transfers").mock(return_value=httpx.Response(303, headers={"Location": job}))
            router.post(f"{job}/phase").mock(return_value=httpx.Response(303, headers={"Location": job}))
            router.get(url__regex=rf"^{job}").mock(
                return_value=httpx.Response(
                    200, content=b"<job><phase>ERROR</phase><errorSummary>moveNode not implemented</errorSummary></job>"
                )
            )
    try:
        await fs._mv_file("/src", "/dst")
        assert sim.blobs["/dst"] == b"moved"
        assert "/src" not in sim.nodes
        assert sim.byte_requests
    finally:
        await fs.aclose()
