"""Buffered and sliced reads share response validation and byte-length semantics."""

import re

import httpx
import pytest
import respx
from conftest import (
    AUTHORITY,
    BASE_URL,
    NODES_URL,
    data_node_response,
    data_xml,
    make_fs,
    mock_transfers,
    stream_body,
)
from vospace_sim import VOSpaceSim

from vosfs import VOSpaceError, staging


@pytest.mark.parametrize(
    ("content_range", "body"),
    [("bytes 5-6/10", b"fg"), ("bytes 0-1/10", b"a"), (None, b"ab")],
)
def test_buffered_open_rejects_invalid_partial_bytes(
    router: respx.Router, content_range: str | None, body: bytes
) -> None:
    mock_transfers(router, {"/f": b"abcdefghij"})
    headers = {} if content_range is None else {"Content-Range": content_range}
    router.route(url__regex=rf"^{re.escape(BASE_URL)}/files").mock(
        side_effect=lambda _request: httpx.Response(206, headers=headers, content=stream_body(body))
    )
    fs = make_fs(router)
    try:
        with (
            fs.open("/f", "rb", block_size=2, cache_type="none") as handle,
            pytest.raises(VOSpaceError, match="206"),
        ):
            handle.read(2)
    finally:
        fs.close()


@pytest.mark.parametrize("honour_range", [False, True])
def test_missing_length_preserves_contents_and_negative_slices(router: respx.Router, honour_range: bool) -> None:
    files = {"/f": b"abcdefghij"}
    mock_transfers(router, files, honour_range=honour_range)
    router.get(url__regex=rf"^{re.escape(NODES_URL)}(?:/[^?]*)?(?:\?.*)?$").mock(
        side_effect=lambda request: (
            httpx.Response(200, content=data_xml(f"vos://{AUTHORITY}/f"))
            if request.url.path.endswith("/f")
            else data_node_response(request, files)
        )
    )
    fs = make_fs(router)
    try:
        with fs.open("/f", "rb", block_size=2) as handle:
            assert handle.read() == files["/f"]
            handle.seek(-3, 2)
            assert handle.read() == b"hij"
        assert fs.cat_file("/f", start=-3) == b"hij"
        assert fs.cat_ranges(["/f"], [-3], [-1]) == [b"hi"]
    finally:
        fs.close()


def test_internal_link_size_is_not_its_target_length(router: respx.Router) -> None:
    sim = VOSpaceSim().add_file("/target", b"abcdefghij").add_link("/link", f"vos://{AUTHORITY}/target")
    sim.install(router)
    fs = make_fs(router)
    try:
        assert fs.cat_file("/link", start=-3) == b"hij"
        with fs.open("/link", "rb", block_size=2) as handle:
            assert handle.read() == b"abcdefghij"
    finally:
        fs.close()


def test_buffered_open_stages_only_once_when_ranges_stop(router: respx.Router) -> None:
    mock_transfers(router, {"/f": b"abcdefghij"})
    requests = []

    def read(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        if len(requests) == 1:
            return httpx.Response(206, headers={"Content-Range": "bytes 0-1/10"}, content=stream_body(b"ab"))
        return httpx.Response(200, content=stream_body(b"abcdefghij"))

    router.route(url__regex=rf"^{re.escape(BASE_URL)}/files").mock(side_effect=read)
    fs = make_fs(router)
    try:
        with fs.open("/f", "rb", block_size=2, cache_type="none") as handle:
            assert handle.read(2) == b"ab"
            assert handle.read(2) == b"cd"
            handle.seek(7)
            assert handle.read() == b"hij"
        assert len(requests) == 2
    finally:
        fs.close()


def test_staging_setup_failure_releases_the_response(router: respx.Router, monkeypatch: pytest.MonkeyPatch) -> None:
    mock_transfers(router, {"/f": b"abcdefghij"})
    fs = make_fs(router, max_connections=1)

    def fail() -> str:
        message = "disk full"
        raise OSError(message)

    try:
        with fs.open("/f", "rb", block_size=2) as handle:
            with monkeypatch.context() as patch:
                patch.setattr(staging, "new_temp_path", fail)
                with pytest.raises(OSError, match="disk full"):
                    handle.read(2)
            assert fs._pool._budget._available == 1
            assert handle.read(2) == b"ab"
    finally:
        fs.close()
