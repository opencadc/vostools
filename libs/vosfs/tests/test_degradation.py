"""Graceful degradation for deployments advertising no transfer binding.

Byte access is a negotiated capability (TRD section 5). A VOSpace deployment
may legitimately advertise no synchronous-transfer (``#sync-2.1``) binding —
`sync-2.1` is an optional IVOA 2.1 addition. When it is absent, node metadata
operations still work and only byte read/write is disabled, with an actionable
error that names the missing capability. These tests prove that supported
degradation, and that byte I/O fails before any negotiation network call.
"""

import httpx
import pytest
import respx
from conftest import AUTHORITY, NODES_URL, make_fs

# Capabilities advertising the node binding but NOT #sync-2.1.
_NODES_ONLY_CAPABILITIES = (
    '<?xml version="1.0" encoding="UTF-8"?>'
    '<vosi:capabilities xmlns:vosi="http://www.ivoa.net/xml/VOSICapabilities/v1.0" '
    'xmlns:vs="http://www.ivoa.net/xml/VODataService/v1.1" '
    'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">'
    '<capability standardID="ivo://ivoa.net/std/VOSpace/v2.0#nodes">'
    '<interface xsi:type="vs:ParamHTTP" role="std">'
    f'<accessURL use="base">{NODES_URL}</accessURL>'
    "</interface></capability></vosi:capabilities>"
).encode()

_DATA_NODE = (
    '<vos:node xmlns:vos="http://www.ivoa.net/xml/VOSpace/v2.0" '
    'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
    f'xsi:type="vos:DataNode" uri="vos://{AUTHORITY}/file.txt" version="2.1">'
    "<vos:properties>"
    '<vos:property uri="ivo://ivoa.net/vospace/core#length">11</vos:property>'
    "</vos:properties></vos:node>"
).encode()


def _no_sync_binding(router: respx.Router) -> None:
    router.get("/capabilities").mock(
        return_value=httpx.Response(200, content=_NODES_ONLY_CAPABILITIES),
    )


async def test_metadata_works_without_a_transfer_binding(
    router: respx.Router,
) -> None:
    _no_sync_binding(router)
    router.get(f"{NODES_URL}/file.txt").mock(
        return_value=httpx.Response(200, content=_DATA_NODE),
    )
    fs = make_fs(router, asynchronous=True)
    info = await fs._info("vos://file.txt")
    assert info["type"] == "file"
    assert info["size"] == 11
    await fs.aclose()


async def test_byte_read_is_disabled_with_an_actionable_error(
    router: respx.Router,
) -> None:
    _no_sync_binding(router)
    fs = make_fs(router, asynchronous=True)
    # `_cat_file` reaches `require_sync` before any negotiation POST, so no
    # `/synctrans` route is registered; if one were called, the strict router
    # would raise instead of the expected NotImplementedError.
    with pytest.raises(NotImplementedError) as caught:
        await fs._cat_file("vos://file.txt")
    message = str(caught.value)
    assert "synchronous-transfer binding" in message
    assert "ivo://ivoa.net/std/VOSpace#sync-2.1" in message
    assert "metadata operations remain available" in message
    await fs.aclose()


async def test_byte_write_is_disabled_with_an_actionable_error(
    router: respx.Router,
) -> None:
    _no_sync_binding(router)
    fs = make_fs(router, asynchronous=True)
    with pytest.raises(NotImplementedError, match="synchronous-transfer binding"):
        await fs._pipe_file("vos://file.txt", b"hello world")
    await fs.aclose()
