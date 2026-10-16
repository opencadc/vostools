"""Tests for the write contract (section 9)."""

import base64
import gc
import gzip
import hashlib
import io
import posixpath
from collections import Counter
from pathlib import Path
from typing import cast

import httpx
import pytest
import respx
from conftest import (
    NODES_URL,
    ROOT_CONTAINER,
    SYNC_URL,
    make_fs,
    mock_capabilities,
    mock_transfers,
    target_path,
    transfer_details,
)
from vospace_sim import VOSpaceSim

_ENDPOINT = "https://staging.canfar.net/arc/files/put-target"


def _mock_put(router: respx.Router, response: httpx.Response) -> None:
    """Wire capabilities, authority, and negotiation to a fixed PUT response."""
    mock_capabilities(router)
    router.get(NODES_URL).mock(return_value=httpx.Response(200, content=ROOT_CONTAINER))
    router.post(SYNC_URL).mock(
        return_value=httpx.Response(303, headers={"Location": "https://staging.canfar.net/arc/d"}),
    )
    router.get(url="https://staging.canfar.net/arc/d").mock(
        return_value=httpx.Response(200, content=transfer_details(_ENDPOINT)),
    )
    router.put(_ENDPOINT).mock(return_value=response)


def _assert_coordinated_write_requests(
    router: respx.Router,
    *,
    containers: list[str],
    data_nodes: list[str],
) -> None:
    """Prove remote containers precede one negotiation and PUT per data node."""
    requests = [call.request for call in router.calls]
    container_requests = [
        (index, request.url.path.removeprefix("/arc/nodes"))
        for index, request in enumerate(requests)
        if request.method == "PUT" and request.url.path.startswith("/arc/nodes")
    ]
    negotiations = [
        (index, target_path(request.content))
        for index, request in enumerate(requests)
        if request.method == "POST" and request.url.path == "/arc/synctrans"
    ]
    byte_puts = [
        (index, request.url.params["p"])
        for index, request in enumerate(requests)
        if request.method == "PUT" and request.url.path == "/arc/files"
    ]

    assert Counter(path for _, path in container_requests) == Counter(containers)
    assert Counter(path for _, path in negotiations) == Counter(data_nodes)
    assert Counter(path for _, path in byte_puts) == Counter(data_nodes)

    container_index = {path: index for index, path in container_requests}
    negotiation_index = {path: index for index, path in negotiations}
    byte_put_index = {path: index for index, path in byte_puts}
    for container in containers:
        parent = posixpath.dirname(container) or "/"
        if parent in container_index:
            assert container_index[parent] < container_index[container]
    for data_node in data_nodes:
        for container in containers:
            if data_node.startswith(f"{container}/"):
                assert container_index[container] < negotiation_index[data_node]
        assert negotiation_index[data_node] < byte_put_index[data_node]


# --- round trips ----------------------------------------------------------------


async def test_pipe_file_overwrite(router: respx.Router) -> None:
    files: dict[str, bytes] = {}
    mock_transfers(router, files)
    fs = make_fs(router, asynchronous=True)
    await fs._pipe_file("/f", b"hello")
    assert files["/f"] == b"hello"
    assert await fs._cat_file("/f") == b"hello"
    await fs.aclose()


async def test_put_file_round_trip(router: respx.Router, tmp_path: Path) -> None:
    files: dict[str, bytes] = {}
    mock_transfers(router, files)
    local = tmp_path / "src.bin"
    local.write_bytes(b"payload-bytes")
    fs = make_fs(router, asynchronous=True)
    await fs._put_file(str(local), "/dest.bin")
    assert files["/dest.bin"] == b"payload-bytes"
    await fs.aclose()


async def test_direct_byte_endpoint_303_is_put_once_without_credentials(
    router: respx.Router,
) -> None:
    mock_capabilities(router)
    router.get(NODES_URL).mock(return_value=httpx.Response(200, content=ROOT_CONTAINER))
    endpoint = "https://staging.canfar.net/arc/files/preauth:TESTTOKEN/out.bin"
    router.post(SYNC_URL).mock(return_value=httpx.Response(303, headers={"Location": endpoint}))
    seen: list[tuple[str | None, bytes]] = []

    def byte_put(request: httpx.Request) -> httpx.Response:
        seen.append((request.headers.get("authorization"), request.content))
        return httpx.Response(201)

    direct = router.put(endpoint).mock(side_effect=byte_put)
    fs = make_fs(router, asynchronous=True, token="service-token")

    await fs._pipe_file("/out.bin", b"payload")

    assert direct.call_count == 1
    assert seen == [(None, b"payload")]
    await fs.aclose()


def test_open_wb_uploads_on_close(router: respx.Router) -> None:
    files: dict[str, bytes] = {}
    mock_transfers(router, files)
    fs = make_fs(router)
    with fs.open("/out.txt", "wb") as handle:
        handle.write(b"chunk1")
        handle.write(b"chunk2")
    assert files["/out.txt"] == b"chunk1chunk2"
    fs.close()


def test_compressed_binary_write_is_readable_after_close(router: respx.Router) -> None:
    mock_transfers(router, {})
    fs = make_fs(router)
    try:
        with fs.open("/out.gz", "wb", compression="gzip") as handle:
            handle.write(b"payload")
        assert gzip.decompress(fs.cat_file("/out.gz")) == b"payload"
    finally:
        fs.close()


def test_abandoned_compressed_write_does_not_create_a_file(
    router: respx.Router,
) -> None:
    mock_transfers(router, {})
    fs = make_fs(router)
    try:
        handle = fs.open("/abandoned.gz", "wb", compression="gzip")
        handle.write(b"partial")
        del handle
        gc.collect()
        assert not fs.exists("/abandoned.gz")
    finally:
        fs.close()


def test_open_w_text(router: respx.Router) -> None:
    files: dict[str, bytes] = {}
    mock_transfers(router, files)
    fs = make_fs(router)
    with fs.open("/t.txt", "w", encoding="utf-8") as handle:
        handle.write("héllo")
    put_bodies = [call.request.content for call in router.calls if call.request.method == "PUT"]
    assert put_bodies == ["héllo".encode()]
    fs.close()


def test_open_w_text_infers_compression_from_normalized_path(
    router: respx.Router,
) -> None:
    files: dict[str, bytes] = {}
    mock_transfers(router, files)
    fs = make_fs(router)
    with fs.open(
        "/encoded.gz",
        "w",
        encoding="utf-8",
        compression="infer",
    ) as handle:
        handle.write("compressed")
    assert gzip.decompress(files["/encoded.gz"]) == b"compressed"
    fs.close()


def test_open_w_text_no_upload_on_error(router: respx.Router) -> None:
    files: dict[str, bytes] = {}
    mock_transfers(router, files)
    fs = make_fs(router)

    def write_then_fail() -> None:
        with fs.open("/never.txt", "w", encoding="utf-8") as handle:
            handle.write("partial")
            msg = "boom"
            raise RuntimeError(msg)

    with pytest.raises(RuntimeError):
        write_then_fail()
    put_calls = [c for c in router.calls if c.request.method == "PUT"]
    assert put_calls == []
    fs.close()


def test_open_w_text_close_failure_no_upload_or_temp_leak(
    router: respx.Router,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    files: dict[str, bytes] = {}
    mock_transfers(router, files)
    fs = make_fs(router)
    handle = cast("io.TextIOWrapper", fs.open("/never.txt", "w", encoding="utf-8"))
    staged_path = Path(handle.buffer.name)
    handle.write("partial")

    def fail_after_buffer_close(writer: io.TextIOWrapper) -> None:
        writer.buffer.close()
        msg = "forced flush failure"
        raise OSError(msg)

    with monkeypatch.context() as patch:
        patch.setattr(type(handle), "flush", fail_after_buffer_close)
        with pytest.raises(OSError, match="forced flush failure"):
            handle.close()

    put_calls = [call for call in router.calls if call.request.method == "PUT"]
    assert (put_calls, staged_path.exists()) == ([], False)
    fs.close()


def test_open_wb_no_upload_on_error(router: respx.Router) -> None:
    files: dict[str, bytes] = {}
    mock_transfers(router, files)
    fs = make_fs(router)

    def write_then_fail() -> None:
        with fs.open("/never.txt", "wb") as handle:
            handle.write(b"partial")
            msg = "boom"
            raise RuntimeError(msg)

    with pytest.raises(RuntimeError):
        write_then_fail()
    assert "/never.txt" not in files
    put_calls = [c for c in router.calls if c.request.method == "PUT"]
    assert put_calls == []
    fs.close()


def test_touch_puts_zero_bytes(router: respx.Router) -> None:
    files: dict[str, bytes] = {}
    mock_transfers(router, files)
    fs = make_fs(router)
    fs.touch("/empty")
    assert files["/empty"] == b""
    fs.close()


def test_touch_no_truncate_unsupported(router: respx.Router) -> None:
    mock_transfers(router, {})
    fs = make_fs(router)
    with pytest.raises(NotImplementedError):
        fs.touch("/x", truncate=False)
    fs.close()


def test_put_preserves_literal_percent_after_destination_remapping(
    router: respx.Router,
    tmp_path: Path,
) -> None:
    from conftest import BASE_URL, call_urls

    source = tmp_path / "README.md"
    source.write_bytes(b"literal-percent")
    files: dict[str, bytes] = {}
    mock_transfers(router, files)
    router.get(f"{NODES_URL}/100%2541").mock(return_value=httpx.Response(404))
    fs = make_fs(router)

    fs.put(str(source), "vos://100%41")

    assert files == {"/100%41": b"literal-percent"}
    byte_urls = call_urls(router, "PUT", f"{BASE_URL}/files")
    assert byte_urls == [f"{BASE_URL}/files?p=/100%2541"]
    fs.close()


def test_pipe_mapping_materializes_shared_remote_parents_once(
    router: respx.Router,
) -> None:
    sim = VOSpaceSim()
    sim.install(router)
    fs = make_fs(router)

    data_nodes = ["/batch/nested/a.bin", "/batch/nested/b.bin"]
    fs.pipe(
        {data_nodes[0]: b"a", data_nodes[1]: b"b"},
        batch_size=2,
        mode="overwrite",
    )

    _assert_coordinated_write_requests(
        router,
        containers=["/batch", "/batch/nested"],
        data_nodes=data_nodes,
    )

    assert fs.find("/batch") == data_nodes
    assert fs.cat_file("/batch/nested/a.bin") == b"a"
    assert fs.cat_file("/batch/nested/b.bin") == b"b"
    fs.close()


def test_pipe_mapping_rejects_file_parent_before_data_writes(
    router: respx.Router,
) -> None:
    sim = VOSpaceSim().add_file("/batch", b"not-a-container")
    sim.install(router)
    fs = make_fs(router)

    with pytest.raises(FileExistsError, match="not a directory"):
        fs.pipe(
            {"/batch/a.bin": b"a", "/batch/b.bin": b"b"},
            batch_size=2,
        )

    assert not any(call.request.url.path in {"/arc/synctrans", "/arc/files"} for call in router.calls)
    fs.close()


def test_put_tree_materializes_empty_directories_before_data(
    router: respx.Router,
    tmp_path: Path,
) -> None:
    source = tmp_path / "source"
    (source / "empty").mkdir(parents=True)
    (source / "nested").mkdir()
    (source / "root.bin").write_bytes(b"root-bytes")
    (source / "nested" / "leaf.bin").write_bytes(b"leaf-bytes")
    sim = VOSpaceSim()
    sim.install(router)
    fs = make_fs(router)

    fs.put(str(source), "/uploads/tree", recursive=True, batch_size=2)

    containers = [
        "/uploads",
        "/uploads/tree",
        "/uploads/tree/empty",
        "/uploads/tree/nested",
    ]
    data_nodes = ["/uploads/tree/nested/leaf.bin", "/uploads/tree/root.bin"]
    _assert_coordinated_write_requests(
        router,
        containers=containers,
        data_nodes=data_nodes,
    )
    assert all(fs.isdir(path) for path in containers)
    assert fs.ls("/uploads/tree/empty", detail=False) == []
    assert fs.cat_file(data_nodes[0]) == b"leaf-bytes"
    assert fs.cat_file(data_nodes[1]) == b"root-bytes"
    fs.close()


def test_recursive_put_preserves_literal_percent_in_remapped_containers(
    router: respx.Router,
    tmp_path: Path,
) -> None:
    from conftest import AUTHORITY, BASE_URL, call_urls, container_xml

    source = tmp_path / "tree"
    (source / "empty").mkdir(parents=True)
    (source / "c.bin").write_bytes(b"content")
    files: dict[str, bytes] = {}
    mock_transfers(router, files)

    def node_op(request: httpx.Request) -> httpx.Response:
        if request.method == "GET":
            if "/destA" in str(request.url):
                path = request.url.path.split("/nodes", 1)[1]
                return httpx.Response(200, content=container_xml(f"vos://{AUTHORITY}{path}"))
            return httpx.Response(404)
        return httpx.Response(201)

    router.route(url__regex=rf"^{NODES_URL}/").mock(side_effect=node_op)
    fs = make_fs(router)

    fs.put(str(source), "vos://dest%41/", recursive=True)

    node_put_urls = call_urls(router, "PUT", f"{NODES_URL}/")
    assert node_put_urls == [
        f"{NODES_URL}/dest%2541",
        f"{NODES_URL}/dest%2541/tree",
        f"{NODES_URL}/dest%2541/tree/empty",
    ]
    assert all("destA" not in url for url in node_put_urls)
    node_get_urls = call_urls(router, "GET", f"{NODES_URL}/")
    assert all("destA" not in url for url in node_get_urls)
    assert files == {"/dest%41/tree/c.bin": b"content"}
    byte_put_urls = call_urls(router, "PUT", f"{BASE_URL}/files")
    assert byte_put_urls == [f"{BASE_URL}/files?p=/dest%2541/tree/c.bin"]
    fs.close()


# --- create / exclusive preflight -----------------------------------------------


async def test_pipe_create_new_path(router: respx.Router) -> None:
    files: dict[str, bytes] = {}
    mock_transfers(router, files)
    router.get(f"{NODES_URL}/f").mock(return_value=httpx.Response(404))
    fs = make_fs(router, asynchronous=True)
    await fs._pipe_file("/f", b"new", mode="create")
    assert files["/f"] == b"new"
    await fs.aclose()


async def test_pipe_create_existing_path(router: respx.Router) -> None:
    mock_transfers(router, {"/f": b"existing"})
    fs = make_fs(router, asynchronous=True)
    with pytest.raises(FileExistsError):
        await fs._pipe_file("/f", b"nope", mode="create")
    await fs.aclose()


def test_open_xb_existing_path(router: respx.Router) -> None:
    mock_transfers(router, {"/f": b"existing"})
    fs = make_fs(router)
    with pytest.raises(FileExistsError):
        fs.open("/f", "xb")
    fs.close()


# --- integrity and failures -----------------------------------------------------


async def test_precondition_failed_is_integrity_error(router: respx.Router) -> None:
    _mock_put(router, httpx.Response(412, text="checksum mismatch"))
    fs = make_fs(router, asynchronous=True)
    with pytest.raises(OSError, match="integrity"):
        await fs._pipe_file("/f", b"data")
    await fs.aclose()


async def test_failed_put_is_uncertain_write(router: respx.Router) -> None:
    _mock_put(router, httpx.Response(500, text="server error"))
    fs = make_fs(router, asynchronous=True)
    with pytest.raises(OSError, match="uncertain"):
        await fs._pipe_file("/f", b"data")
    await fs.aclose()


async def test_returned_md5_mismatch_raises(router: respx.Router) -> None:
    wrong = base64.b64encode(hashlib.md5(b"other").digest()).decode()  # noqa: S324
    _mock_put(router, httpx.Response(201, headers={"Content-MD5": wrong}))
    fs = make_fs(router, asynchronous=True)
    with pytest.raises(OSError, match="MD5"):
        await fs._pipe_file("/f", b"data")
    await fs.aclose()


async def test_returned_md5_match_succeeds(router: respx.Router) -> None:
    good = hashlib.md5(b"data").hexdigest()  # noqa: S324
    _mock_put(router, httpx.Response(201, headers={"Content-MD5": good}))
    fs = make_fs(router, asynchronous=True)
    await fs._pipe_file("/f", b"data")
    await fs.aclose()


async def test_content_type_is_sent(router: respx.Router) -> None:
    _mock_put(router, httpx.Response(201))
    fs = make_fs(router, asynchronous=True)
    await fs._pipe_file("/f", b"data", content_type="text/csv")
    put_call = next(c for c in router.calls if c.request.method == "PUT")
    assert put_call.request.headers["Content-Type"] == "text/csv"
    await fs.aclose()


def test_append_mode_unsupported(router: respx.Router) -> None:
    mock_transfers(router, {})
    fs = make_fs(router)
    with pytest.raises(NotImplementedError):
        fs.open("/f", "ab")
    fs.close()


def test_open_write_autocommit_false_is_unsupported(router: respx.Router) -> None:
    files: dict[str, bytes] = {}
    mock_transfers(router, files)
    fs = make_fs(router)
    with pytest.raises(NotImplementedError, match="autocommit"):
        fs.open("/x.txt", "wb", autocommit=False)
    fs.close()
