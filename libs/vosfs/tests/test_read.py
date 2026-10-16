"""Tests for the read contract (section 8)."""

import asyncio
import tempfile
from pathlib import Path

import httpx
import pytest
import respx
from conftest import BASE_URL, SYNC_URL, make_fs, mock_transfers, stream_body
from vospace_sim import VOSpaceSim

from vosfs import staging
from vosfs.errors import VOSpaceError


def test_signed_byte_url_is_redacted_in_connection_errors(router: respx.Router) -> None:
    endpoint = f"{BASE_URL}/files/preauth:SYNTHETIC_TOKEN/f"
    router.get(endpoint).mock(side_effect=httpx.ConnectError("simulated"))
    mock_transfers(router, {"/f": b"abc"})
    router.post(SYNC_URL).mock(return_value=httpx.Response(303, headers={"Location": endpoint}))
    fs = make_fs(router)
    try:
        with pytest.raises(ConnectionError) as caught:
            fs.cat_file("/f")
        assert "SYNTHETIC_TOKEN" not in str(caught.value)
    finally:
        fs.close()


def test_ranged_read_clips_at_eof(router: respx.Router) -> None:
    mock_transfers(router, {"/f": b"abcdefghij"}, honour_range=True)
    fs = make_fs(router)
    try:
        assert fs.cat_file("/f", start=8, end=20) == b"ij"
    finally:
        fs.close()


@pytest.mark.parametrize("start", [-2, 2])
def test_ranged_read_rejects_incomplete_intervals(router: respx.Router, start: int) -> None:
    router.get(f"{BASE_URL}/files", params={"p": "/f"}).mock(
        return_value=httpx.Response(206, content=stream_body(b"ab"), headers={"Content-Range": "bytes 0-1/10"})
    )
    mock_transfers(router, {"/f": b"abcdefghij"})
    fs = make_fs(router)
    try:
        with pytest.raises(VOSpaceError, match="does not match request"):
            fs.cat_file("/f", start=start)
    finally:
        fs.close()


def test_ranged_read_past_eof_is_empty(router: respx.Router) -> None:
    mock_transfers(router, {"/f": b"abcdefghij"}, honour_range=True)
    fs = make_fs(router)
    try:
        assert fs.cat_file("/f", start=20) == b""
    finally:
        fs.close()


async def test_get_file_streams_to_disk(router: respx.Router, tmp_path: Path) -> None:
    mock_transfers(router, {"/data.bin": b"hello world"})
    fs = make_fs(router, asynchronous=True)
    local = tmp_path / "out.bin"
    await fs._get_file("/data.bin", str(local))
    assert local.read_bytes() == b"hello world"
    await fs.aclose()


async def test_get_file_empty_204(router: respx.Router, tmp_path: Path) -> None:
    mock_transfers(router, {"/empty": b""})
    fs = make_fs(router, asynchronous=True)
    local = tmp_path / "empty"
    await fs._get_file("/empty", str(local))
    assert local.read_bytes() == b""
    await fs.aclose()


async def test_cat_file_whole(router: respx.Router) -> None:
    mock_transfers(router, {"/f": b"abcdef"})
    fs = make_fs(router, asynchronous=True)
    assert await fs._cat_file("/f") == b"abcdef"
    await fs.aclose()


@pytest.mark.parametrize("operation", ["cat_file", "get_file", "open"])
def test_internal_link_byte_reads(router: respx.Router, tmp_path: Path, operation: str) -> None:
    sim = VOSpaceSim().add_file("/target", b"linked bytes").add_link("/link", "vos://example.test!vault/target")
    sim.install(router)
    fs = make_fs(router)

    if operation == "cat_file":
        assert fs.cat_file("/link") == b"linked bytes"
    elif operation == "get_file":
        local = tmp_path / "linked.bin"
        fs.get_file("/link", local)
        assert local.read_bytes() == b"linked bytes"
    else:
        with fs.open("/link", "rb") as handle:
            assert handle.read() == b"linked bytes"

    fs.close()


@pytest.mark.parametrize("operation", ["cat_file", "get_file", "open"])
def test_external_link_byte_reads_are_rejected_before_transfer(
    router: respx.Router, tmp_path: Path, operation: str
) -> None:
    sim = VOSpaceSim().add_link("/link", "vos://external.example!vault/target")
    sim.install(router)
    fs = make_fs(router, token="service-token")

    def attempt_read() -> None:
        if operation == "cat_file":
            fs.cat_file("/link")
        elif operation == "get_file":
            fs.get_file("/link", tmp_path / "external.bin")
        else:
            with fs.open("/link", "rb"):
                pass

    with pytest.raises(NotImplementedError, match="external LinkNode"):
        attempt_read()

    assert sim.byte_requests == []
    assert not any(call.request.url.path == "/arc/synctrans" for call in router.calls)
    external_calls = [call for call in router.calls if call.request.url.host != "staging.canfar.net"]
    assert external_calls == []
    assert list(tmp_path.iterdir()) == []
    fs.close()


@pytest.mark.parametrize(
    ("target", "secrets"),
    [
        (
            "vos://user:password@external.example!vault/target",
            ("user", "password"),
        ),
        (
            "https://external.example/files/preauth:secret-token/data?token=query-token",
            ("secret-token", "query-token"),
        ),
    ],
)
def test_external_link_error_redacts_target(router: respx.Router, target: str, secrets: tuple[str, ...]) -> None:
    sim = VOSpaceSim().add_link("/link", target)
    sim.install(router)
    fs = make_fs(router)

    with pytest.raises(NotImplementedError) as caught:
        fs.cat_file("/link")

    for rendered in (str(caught.value), repr(caught.value)):
        assert target not in rendered
        assert all(secret not in rendered for secret in secrets)
    fs.close()


@pytest.mark.parametrize("operation", ["cat_ranges", "open"])
def test_external_link_rejection_precedes_staging(
    router: respx.Router,
    monkeypatch: pytest.MonkeyPatch,
    operation: str,
) -> None:
    sim = VOSpaceSim().add_link("/link", "vos://external.example!vault/target")
    sim.install(router)
    fs = make_fs(router)
    temp_calls = 0
    unlink_calls = 0
    original_new_temp_path = staging.new_temp_path
    original_unlink = Path.unlink

    def counted_new_temp_path() -> str:
        nonlocal temp_calls
        temp_calls += 1
        return original_new_temp_path()

    def counted_unlink(path: Path, *args: object, **kwargs: object) -> None:
        nonlocal unlink_calls
        unlink_calls += 1
        original_unlink(path, *args, **kwargs)

    monkeypatch.setattr(staging, "new_temp_path", counted_new_temp_path)
    monkeypatch.setattr(Path, "unlink", counted_unlink)

    def attempt_read() -> None:
        if operation == "cat_ranges":
            fs.cat_ranges(["/link"], [0], [1], on_error="raise")
        else:
            with fs.open("/link", "rb"):
                pass

    with pytest.raises(NotImplementedError, match="external LinkNode"):
        attempt_read()

    assert temp_calls == 0
    assert unlink_calls == 0
    fs.close()


@pytest.mark.parametrize(
    ("start", "end", "expected"),
    [
        (0, 3, b"abc"),
        (2, None, b"cdef"),
        (None, 2, b"ab"),
        (-2, None, b"ef"),
        (3, 3, b""),
        (0, 100, b"abcdef"),
    ],
)
async def test_cat_file_slicing(router: respx.Router, start: int | None, end: int | None, expected: bytes) -> None:
    mock_transfers(router, {"/f": b"abcdef"})
    fs = make_fs(router, asynchronous=True)
    assert await fs._cat_file("/f", start, end) == expected
    await fs.aclose()


async def test_negative_slice_uses_size_and_absolute_range(router: respx.Router) -> None:
    mock_transfers(router, {"/f": b"abcdef"}, honour_range=True)
    fs = make_fs(router, asynchronous=True)
    assert await fs._cat_file("/f", -4, -1) == b"cde"
    byte_calls = [call for call in router.calls if "/files" in str(call.request.url)]
    assert len(byte_calls) == 1
    assert byte_calls[0].request.headers["range"] == "bytes=2-4"
    await fs.aclose()


async def test_cat_file_uses_206_partial_body(router: respx.Router) -> None:
    mock_transfers(router, {"/f": b"abcdefghij"}, honour_range=True)
    fs = make_fs(router, asynchronous=True)
    assert await fs._cat_file("/f", 2, 6) == b"cdef"
    byte_calls = [c for c in router.calls if "/files" in str(c.request.url)]
    assert len(byte_calls) == 1
    assert byte_calls[0].request.headers["range"] == "bytes=2-5"
    await fs.aclose()


async def test_cat_file_200_ignores_range_and_slices(
    router: respx.Router,
) -> None:
    mock_transfers(router, {"/f": b"abcdefghij"}, honour_range=False)
    fs = make_fs(router, asynchronous=True)
    assert await fs._cat_file("/f", 2, 6) == b"cdef"
    byte_calls = [c for c in router.calls if "/files" in str(c.request.url)]
    assert len(byte_calls) == 1
    assert byte_calls[0].request.headers["range"] == "bytes=2-5"
    await fs.aclose()


async def test_cat_ranges_one_get_per_object(router: respx.Router) -> None:
    mock_transfers(router, {"/f": b"abcdefghij"})
    fs = make_fs(router, asynchronous=True)
    result = await fs._cat_ranges(["/f", "/f", "/f"], [0, 2, 5], [2, 4, 8])
    assert result == [b"ab", b"cd", b"fgh"]
    # Only one byte GET for the single object, despite three ranges.
    byte_calls = [c for c in router.calls if "/files" in str(c.request.url)]
    assert len(byte_calls) == 1
    await fs.aclose()


async def test_cat_ranges_issues_one_get_per_range_when_206(
    router: respx.Router,
) -> None:
    mock_transfers(router, {"/f": b"abcdefghij"}, honour_range=True)
    fs = make_fs(router, asynchronous=True)
    result = await fs._cat_ranges(["/f", "/f", "/f"], [0, 2, 5], [2, 4, 8])
    assert result == [b"ab", b"cd", b"fgh"]
    byte_calls = [c for c in router.calls if "/files" in str(c.request.url)]
    assert len(byte_calls) == 3
    assert [c.request.headers["range"] for c in byte_calls] == [
        "bytes=0-1",
        "bytes=2-3",
        "bytes=5-7",
    ]
    await fs.aclose()


async def test_cat_ranges_groups_multiple_objects(router: respx.Router) -> None:
    mock_transfers(router, {"/a": b"aaaa", "/b": b"bbbb"})
    fs = make_fs(router, asynchronous=True)
    result = await fs._cat_ranges(["/a", "/b", "/a"], [0, 1, 2], [2, 3, 4])
    assert result == [b"aa", b"bb", b"aa"]
    await fs.aclose()


@pytest.mark.parametrize(
    ("call_batch_size", "filesystem_batch_size", "expected_active"),
    [(2, None, 2), (-1, None, 3), (None, -1, 3)],
)
async def test_cat_ranges_bounds_active_staged_objects(  # noqa: PLR0913 - parametrized public seam
    router: respx.Router,
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    call_batch_size: int | None,
    filesystem_batch_size: int | None,
    expected_active: int,
) -> None:
    active = 0
    maximum_active = 0
    maximum_temps = 0
    release = asyncio.Event()

    def temp_count() -> int:
        return sum(path.name.startswith("vosfs-") for path in tmp_path.iterdir())

    class BlockingStream(httpx.AsyncByteStream):
        def __init__(self, content: bytes) -> None:
            self.content = content

        async def __aiter__(self):
            nonlocal active, maximum_active, maximum_temps
            active += 1
            maximum_active = max(maximum_active, active)
            maximum_temps = max(maximum_temps, temp_count())
            if active == expected_active:
                release.set()
            await release.wait()
            try:
                yield self.content
            finally:
                active -= 1

    router.route(url__regex=rf"^{BASE_URL}/files").mock(
        side_effect=lambda request: httpx.Response(
            200,
            stream=BlockingStream(request.url.params["p"].encode() * 4),
        )
    )
    mock_transfers(router, {"/a": b"", "/b": b"", "/c": b""})
    monkeypatch.setattr(tempfile, "tempdir", str(tmp_path))
    fs = make_fs(router, asynchronous=True, batch_size=filesystem_batch_size)

    result = await asyncio.wait_for(
        fs._cat_ranges(
            ["/a", "/b", "/c", "/a"],
            [0, 0, 0, 1],
            [2, 2, 2, 3],
            batch_size=call_batch_size,
        ),
        timeout=1,
    )

    assert result == [b"/a", b"/b", b"/c", b"a/"]
    assert maximum_active == expected_active
    assert maximum_temps == expected_active
    assert temp_count() == 0
    await fs.aclose()


async def test_cat_ranges_rejects_invalid_batch_size_before_io(
    router: respx.Router,
) -> None:
    fs = make_fs(router, asynchronous=True)
    with pytest.raises(ValueError, match="batch_size"):
        await fs._cat_ranges(["/a"], [0], [1], batch_size=0)
    assert len(router.calls) == 0
    await fs.aclose()


async def test_cat_ranges_honors_invalid_filesystem_batch_size_before_io(
    router: respx.Router,
) -> None:
    fs = make_fs(router, asynchronous=True, batch_size=0)
    with pytest.raises(ValueError, match="batch_size"):
        await fs._cat_ranges(["/a"], [0], [1])
    assert len(router.calls) == 0
    await fs.aclose()


@pytest.mark.parametrize("on_error", ["ignore", "omit"])
async def test_cat_ranges_rejects_invalid_on_error_before_io(router: respx.Router, on_error: str) -> None:
    fs = make_fs(router, asynchronous=True)
    with pytest.raises(ValueError, match="on_error"):
        await fs._cat_ranges(["/a"], [0], [1], on_error=on_error)
    assert len(router.calls) == 0
    await fs.aclose()


async def test_cat_ranges_rejects_cardinality_mismatch_before_io(
    router: respx.Router,
) -> None:
    fs = make_fs(router, asynchronous=True)
    with pytest.raises(ValueError, match="same length"):
        await fs._cat_ranges(["/a", "/b"], [0], [1, 2])
    assert len(router.calls) == 0
    await fs.aclose()


async def test_cat_ranges_broadcasts_scalar_bounds(router: respx.Router) -> None:
    # Regression: fsspec permits a single int for starts/ends applied to all paths.
    mock_transfers(router, {"/a": b"0123456789", "/b": b"abcdefghij"})
    fs = make_fs(router, asynchronous=True)
    assert await fs._cat_ranges(["/a", "/b"], 0, 3) == [b"012", b"abc"]
    await fs.aclose()


async def test_read_consumes_raw_bytes_despite_content_encoding(
    router: respx.Router,
) -> None:
    import gzip

    from vospace_sim import VOSpaceSim

    raw = gzip.compress(b"plain payload")

    async def gz_body() -> object:
        yield raw

    # Register a streamable byte GET declaring Content-Encoding: gzip first so it
    # wins over the simulator's route; httpx would content-decode it via aread().
    router.route(url__regex=rf"^{BASE_URL}/files").mock(
        return_value=httpx.Response(200, content=gz_body(), headers={"Content-Encoding": "gzip"}),
    )
    VOSpaceSim().add_file("/f", raw).install(router)
    fs = make_fs(router, asynchronous=True)
    # cat_file must return the RAW (gzipped) bytes, not the httpx-decoded body.
    assert await fs._cat_file("/f") == raw
    await fs.aclose()


async def test_identity_encoding_header_sent(router: respx.Router) -> None:
    mock_transfers(router, {"/f": b"x"})
    fs = make_fs(router, asynchronous=True)
    await fs._cat_file("/f")
    byte_call = next(c for c in router.calls if "/files" in str(c.request.url))
    assert byte_call.request.headers["Accept-Encoding"] == "identity"
    await fs.aclose()


def test_open_rb_is_seekable(router: respx.Router) -> None:
    mock_transfers(router, {"/f": b"0123456789"})
    fs = make_fs(router)
    with fs.open("/f", "rb") as handle:
        assert handle.read(3) == b"012"
        assert handle.tell() == 3
        handle.seek(5)
        assert handle.read() == b"56789"
        handle.seek(-2, 2)
        assert handle.read() == b"89"
        handle.seek(0)
        buffer = bytearray(4)
        assert handle.readinto(buffer) == 4
        assert bytes(buffer) == b"0123"
    fs.close()


async def test_cat_file_missing_raises(router: respx.Router) -> None:
    mock_transfers(router, {})
    fs = make_fs(router, asynchronous=True)
    with pytest.raises(FileNotFoundError):
        await fs._cat_file("/missing")
    await fs.aclose()


async def test_cat_ranges_on_error_raise(router: respx.Router) -> None:
    mock_transfers(router, {})
    fs = make_fs(router, asynchronous=True)
    with pytest.raises(FileNotFoundError):
        await fs._cat_ranges(["/missing"], [0], [1], on_error="raise")
    await fs.aclose()


async def test_cat_ranges_on_error_return(router: respx.Router) -> None:
    mock_transfers(router, {"/ok": b"hello"})
    fs = make_fs(router, asynchronous=True)
    result = await fs._cat_ranges(["/ok", "/missing"], [0, 0], [2, 1])
    assert result[0] == b"he"
    assert isinstance(result[1], FileNotFoundError)
    await fs.aclose()


def test_open_text_mode(router: respx.Router) -> None:
    mock_transfers(router, {"/f": b"line1\nline2\n"})
    fs = make_fs(router)
    with fs.open("/f", "r") as handle:
        assert handle.readline() == "line1\n"
        assert list(handle) == ["line2\n"]
    fs.close()


def test_cat_head_tail(router: respx.Router) -> None:
    mock_transfers(router, {"/f": b"abcdefghij"})
    fs = make_fs(router)
    assert fs.cat("/f") == b"abcdefghij"
    assert fs.head("/f", 3) == b"abc"
    assert fs.tail("/f", 2) == b"ij"
    fs.close()


def test_literal_percent_targets_survive_scalar_list_and_bulk_coordinators(
    router: respx.Router,
) -> None:
    from conftest import AUTHORITY, BASE_URL, SYNC_URL, call_urls

    internal = "/authority/dir/100%41"
    mock_transfers(router, {internal: b"literal-percent"})
    fs = make_fs(router)

    assert fs.cat("vos://authority/dir/100%41") == b"literal-percent"
    assert fs.cat(["vos://authority/dir/100%41"]) == {internal: b"literal-percent"}
    assert fs.cat_ranges(["vos://authority/dir/100%41"], [0], [7]) == [b"literal"]

    posts = [
        call.request.url.params["TARGET"]
        for call in router.calls
        if call.request.method == "POST" and str(call.request.url).startswith(SYNC_URL)
    ]
    assert posts == [f"vos://{AUTHORITY}{internal}"] * 3
    assert [
        call.request.url.params["p"]
        for call in router.calls
        if call.request.method == "GET" and str(call.request.url).startswith(f"{BASE_URL}/files")
    ] == [internal, internal, internal]
    assert call_urls(router, "GET", f"{BASE_URL}/files") == [f"{BASE_URL}/files?p=/authority/dir/100%2541"] * 3
    fs.close()


def test_literal_percent_target_survives_wildcard_expansion(
    router: respx.Router,
) -> None:
    from conftest import (
        AUTHORITY,
        BASE_URL,
        NODES_URL,
        call_urls,
        container_xml,
        data_child,
    )

    internal = "/authority/dir/100%41"
    listing = container_xml(
        f"vos://{AUTHORITY}/authority/dir",
        data_child(f"vos://{AUTHORITY}/authority/dir/100%2541", 15),
    )
    router.get(f"{NODES_URL}/authority/dir").mock(return_value=httpx.Response(200, content=listing))
    mock_transfers(router, {internal: b"literal-percent"})
    fs = make_fs(router)

    matches = fs.glob("vos://authority/dir/*")
    assert matches == [internal]
    assert isinstance(matches[0], str)
    assert fs.cat("vos://authority/dir/*") == {internal: b"literal-percent"}
    assert fs.cat(matches[0]) == b"literal-percent"
    byte_urls = call_urls(router, "GET", f"{BASE_URL}/files")
    assert byte_urls == [f"{BASE_URL}/files?p=/authority/dir/100%2541"] * 2
    fs.close()


def test_literal_percent_target_survives_recursive_wildcard_get(
    router: respx.Router,
    tmp_path: Path,
) -> None:
    from conftest import (
        AUTHORITY,
        BASE_URL,
        NODES_URL,
        call_urls,
        container_xml,
        data_child,
        data_xml,
    )

    internal = "/authority/dir/100%41"
    listing = container_xml(
        f"vos://{AUTHORITY}/authority/dir",
        data_child(f"vos://{AUTHORITY}/authority/dir/100%2541", 15),
    )
    child = data_xml(f"vos://{AUTHORITY}/authority/dir/100%2541", 15)
    router.get(f"{NODES_URL}/authority/dir").mock(return_value=httpx.Response(200, content=listing))
    correct_node = router.get(f"{NODES_URL}/authority/dir/100%2541").mock(
        return_value=httpx.Response(200, content=child)
    )
    wrong_node = router.get(f"{NODES_URL}/authority/dir/100A").mock(return_value=httpx.Response(404))
    mock_transfers(router, {internal: b"literal-percent"})
    fs = make_fs(router)

    fs.get("vos://authority/dir/*", str(tmp_path) + "/", recursive=True)

    assert (tmp_path / "100%41").read_bytes() == b"literal-percent"
    assert correct_node.called
    assert not wrong_node.called
    byte_urls = call_urls(router, "GET", f"{BASE_URL}/files")
    assert byte_urls == [f"{BASE_URL}/files?p=/authority/dir/100%2541"]
    fs.close()


def test_literal_percent_directory_survives_recursive_get(
    router: respx.Router,
    tmp_path: Path,
) -> None:
    from conftest import (
        AUTHORITY,
        BASE_URL,
        NODES_URL,
        call_urls,
        container_child,
        container_xml,
        data_child,
        data_xml,
    )

    root_listing = container_xml(
        f"vos://{AUTHORITY}/root",
        container_child(f"vos://{AUTHORITY}/root/100%2541"),
    )
    percent_listing = container_xml(
        f"vos://{AUTHORITY}/root/100%2541",
        data_child(f"vos://{AUTHORITY}/root/100%2541/child", 5),
    )
    child = data_xml(f"vos://{AUTHORITY}/root/100%2541/child", 5)
    router.get(f"{NODES_URL}/root").mock(return_value=httpx.Response(200, content=root_listing))
    correct_dir = router.get(f"{NODES_URL}/root/100%2541").mock(
        return_value=httpx.Response(200, content=percent_listing)
    )
    router.get(f"{NODES_URL}/root/100%2541/child").mock(return_value=httpx.Response(200, content=child))
    wrong_dir = router.get(f"{NODES_URL}/root/100A").mock(return_value=httpx.Response(404))
    mock_transfers(router, {"/root/100%41/child": b"child"})
    fs = make_fs(router)

    expected_globs = {
        "*": ["/root/100%41/child"],
        "[c]hild": ["/root/100%41/child"],
        "**": ["/root/100%41", "/root/100%41/child"],
    }
    for pattern, expected in expected_globs.items():
        assert fs.glob(f"vos://root/100%41/{pattern}") == expected
    assert fs.expand_path("vos://root", recursive=True) == [
        "/root",
        "/root/100%41",
        "/root/100%41/child",
    ]
    target = tmp_path / "download"
    fs.get("vos://root", str(target), recursive=True)

    assert (target / "100%41" / "child").read_bytes() == b"child"
    assert correct_dir.called
    assert not wrong_dir.called
    byte_urls = call_urls(router, "GET", f"{BASE_URL}/files")
    assert byte_urls == [f"{BASE_URL}/files?p=/root/100%2541/child"]
    fs.close()


def test_direct_byte_endpoint_303_is_consumed_once_without_credentials(
    router: respx.Router,
) -> None:
    from conftest import (
        AUTHORITY,
        NODES_URL,
        ROOT_CONTAINER,
        SYNC_URL,
        data_xml,
        mock_capabilities,
    )

    mock_capabilities(router)
    router.get(NODES_URL).mock(return_value=httpx.Response(200, content=ROOT_CONTAINER))
    router.get(f"{NODES_URL}/d.bin").mock(
        return_value=httpx.Response(200, content=data_xml(f"vos://{AUTHORITY}/d.bin"))
    )
    endpoint = "http://download.test/files/preauth:TESTTOKEN/d.bin"
    post = router.post(SYNC_URL).mock(return_value=httpx.Response(303, headers={"Location": endpoint}))
    seen_auth: list[str | None] = []

    class DirectBytes(httpx.AsyncByteStream):
        closed = False

        async def __aiter__(self):
            yield b"direct-bytes"

        async def aclose(self) -> None:
            self.closed = True

    stream = DirectBytes()

    def byte_get(request: httpx.Request) -> httpx.Response:
        seen_auth.append(request.headers.get("authorization"))
        return httpx.Response(200, stream=stream)

    direct = router.get(endpoint).mock(side_effect=byte_get)
    fs = make_fs(router, token="service-token")
    assert fs.cat_file("/d.bin") == b"direct-bytes"
    assert post.call_count == 1
    assert direct.call_count == 1
    assert seen_auth == [None]
    assert stream.closed
    fs.close()


def test_open_block_size_reuses_one_ranged_endpoint(router: respx.Router) -> None:
    mock_transfers(router, {"/f": b"abcdefghij"}, honour_range=True)
    fs = make_fs(router)
    with fs.open("/f", "rb", block_size=4) as handle:
        assert handle.read(4) == b"abcd"
        assert handle.read(4) == b"efgh"
    posts = [call for call in router.calls if call.request.method == "POST"]
    ranged = [
        call.request.headers.get("range")
        for call in router.calls
        if call.request.method == "GET" and "/files" in str(call.request.url)
    ]
    assert len(posts) == 1
    assert ranged
    assert all(value is not None and value.startswith("bytes=") for value in ranged)
    fs.close()


def test_open_block_size_stages_a_200_body(router: respx.Router) -> None:
    mock_transfers(router, {"/f": b"abcdefghij"}, honour_range=False)
    fs = make_fs(router)
    with fs.open("/f", "rb", block_size=4) as handle:
        assert handle.read() == b"abcdefghij"
    byte_gets = [call for call in router.calls if call.request.method == "GET" and "/files" in str(call.request.url)]
    assert len(byte_gets) == 1
    fs.close()


def test_failed_staged_read_removes_temporary_file(
    router: respx.Router,
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    mock_transfers(router, {})
    monkeypatch.setattr(tempfile, "tempdir", str(tmp_path))
    fs = make_fs(router)

    with pytest.raises(FileNotFoundError):
        fs.open("/missing", "rb")

    assert list(tmp_path.glob("vosfs-*")) == []
    fs.close()


def test_error_response_carries_retry_after_and_fault(router: respx.Router) -> None:
    # End-to-end: a 503 with a Retry-After header and a fault body flows through
    # _raise_for_status into VOSpaceError's fields.
    from conftest import NODES_URL, mock_capabilities

    from vosfs import errors

    mock_capabilities(router)
    router.get(f"{NODES_URL}/x").mock(
        return_value=httpx.Response(503, headers={"Retry-After": "42"}, text="ServiceBusy: try again later")
    )
    fs = make_fs(router)
    with pytest.raises(errors.VOSpaceError) as excinfo:
        fs.info("/x")
    assert excinfo.value.retry_after == 42.0
    assert excinfo.value.fault == "ServiceBusy"
    assert excinfo.value.status == 503
    fs.close()


async def test_cat_after_listing_does_not_fetch_the_node_again(router: respx.Router) -> None:
    from conftest import NODES_URL, make_sim_fs

    sim = VOSpaceSim().add_container("/d").add_file("/d/f", b"abc")
    fs = make_sim_fs(router, sim)
    listing = await fs._ls("/d")
    assert listing
    assert fs._cached_info("/d/f") is not None
    node_gets = [
        call for call in router.calls if call.request.method == "GET" and str(call.request.url).startswith(NODES_URL)
    ]
    await fs._cat_file("/d/f")
    later = [
        call for call in router.calls if call.request.method == "GET" and str(call.request.url).startswith(NODES_URL)
    ]
    assert [str(call.request.url) for call in later] == [str(call.request.url) for call in node_gets]
    await fs.aclose()
