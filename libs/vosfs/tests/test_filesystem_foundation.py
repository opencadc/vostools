"""Foundation: class contract, protocol registration, and the transport seam."""

import fsspec
import httpx
import pytest
import respx
from conftest import BASE_URL, make_fs
from fsspec.asyn import AsyncFileSystem

from vosfs import VOSpaceFileSystem


def test_is_registered_async_fsspec_filesystem() -> None:
    assert issubclass(VOSpaceFileSystem, AsyncFileSystem)
    assert VOSpaceFileSystem.async_impl is True
    assert VOSpaceFileSystem.protocol == "vos"
    assert VOSpaceFileSystem.cachable is True


def test_vos_protocol_resolves_through_entry_point() -> None:
    assert fsspec.get_filesystem_class("vos") is VOSpaceFileSystem


def test_root_marker_matches_absolute_vos_paths() -> None:
    assert VOSpaceFileSystem.root_marker == "/"
    assert VOSpaceFileSystem._parent("/") == "/"
    assert VOSpaceFileSystem._parent("/a") == "/"
    assert VOSpaceFileSystem._parent("/a/b") == "/a"
    assert VOSpaceFileSystem._strip_protocol("vos://") == "/"


def test_strip_protocol_normalizes_on_the_class() -> None:
    assert VOSpaceFileSystem._strip_protocol("vos://a/b") == "/a/b"
    assert VOSpaceFileSystem._strip_protocol("vos:///a/b") == "/a/b"
    assert VOSpaceFileSystem._strip_protocol("a/b") == "/a/b"
    assert VOSpaceFileSystem._strip_protocol("vos://") == "/"
    assert type(VOSpaceFileSystem._strip_protocol("/plain")) is str


def test_endpoint_trailing_slash_normalized() -> None:
    router = respx.Router(base_url=BASE_URL)
    fs = make_fs(router)
    assert fs.endpoint_url == BASE_URL


async def test_transport_seam_is_injectable(router: respx.Router) -> None:
    router.get("/capabilities").mock(return_value=httpx.Response(200, text="CAPS"))
    fs = make_fs(router, asynchronous=True)
    client = await fs._pool.client()
    response = await client.get(fs.endpoint_url + "/capabilities")
    assert response.status_code == 200
    assert response.text == "CAPS"
    await fs.aclose()


def test_seam_and_loop_absent_from_storage_options() -> None:
    router = respx.Router(base_url=BASE_URL)
    fs = make_fs(router, token="secret-token")
    assert "transport" not in fs.storage_options
    assert "loop" not in fs.storage_options
    # A literal token is intentionally part of the serialized options.
    assert fs.storage_options.get("token") == "secret-token"


async def test_client_disables_redirects(router: respx.Router) -> None:
    fs = make_fs(router, asynchronous=True)
    client = await fs._pool.client()
    assert client.follow_redirects is False
    await fs.aclose()


def test_invalid_endpoint_rejected() -> None:
    router = respx.Router(base_url=BASE_URL)
    transport = httpx.MockTransport(router.async_handler)
    with pytest.raises(ValueError):  # noqa: PT011
        VOSpaceFileSystem("", transport=transport, skip_instance_cache=True)
