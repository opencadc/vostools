"""Shared hermetic test harness for vosfs.

Every hermetic test drives the filesystem through the single internal HTTP
transport seam: a ``respx`` router is injected as an ``httpx`` mock transport
via the ``transport`` constructor option, which never appears in
``storage_options``. Unmatched requests raise, so tests cannot reach the
network.
"""

from __future__ import annotations

import re
from importlib.metadata import version
from typing import TYPE_CHECKING, Any
from urllib.parse import quote, unquote, urlsplit

import httpx
import pytest
import respx

from vosfs import VOSpaceFileSystem

if TYPE_CHECKING:
    from collections.abc import AsyncIterator, Iterator

BASE_URL = "https://staging.canfar.net/arc"
NODES_URL = f"{BASE_URL}/nodes"
SYNC_URL = f"{BASE_URL}/synctrans"
AUTHORITY = "example.test!vault"

# Derived from the installed package so skip reasons never drift from the
# shipped version. The unsupported capabilities they describe are fixed by the
# capability contract; the version label simply tracks the release under test.
_VERSION = f"vosfs v{version('vosfs')}"

_QUESTION_MARK = (
    f"unsupported in {_VERSION} (TRD sections 4 and 11): '?' is a literal "
    "name character, so question-mark glob paths cannot be expressed"
)


def pytest_collection_modifyitems(items: list[pytest.Item]) -> None:
    """Skip abstract-suite glob edge cases the path grammar cannot express."""
    for item in items:
        if "_glob_edge_cases" not in item.name:
            continue
        callspec = getattr(item, "callspec", None)
        if callspec is not None and "?" in callspec.params.get("path", ""):
            item.add_marker(pytest.mark.skip(reason=_QUESTION_MARK))


CAPABILITIES = f"""<?xml version="1.0" encoding="UTF-8"?>
<vosi:capabilities xmlns:vosi="http://www.ivoa.net/xml/VOSICapabilities/v1.0"
                   xmlns:vs="http://www.ivoa.net/xml/VODataService/v1.1"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <capability standardID="ivo://ivoa.net/std/VOSpace/v2.0#nodes">
    <interface xsi:type="vs:ParamHTTP" role="std">
      <accessURL use="base">{NODES_URL}</accessURL>
    </interface>
    <interface xsi:type="vs:ParamHTTP" role="std">
      <accessURL use="base">{NODES_URL}</accessURL>
      <securityMethod standardID="ivo://ivoa.net/sso#token"/>
    </interface>
    <interface xsi:type="vs:ParamHTTP" role="std">
      <accessURL use="base">{NODES_URL}</accessURL>
      <securityMethod standardID="ivo://ivoa.net/sso#tls-with-certificate"/>
    </interface>
  </capability>
  <capability standardID="ivo://ivoa.net/std/VOSpace#sync-2.1">
    <interface xsi:type="vs:ParamHTTP" role="std">
      <accessURL use="full">{SYNC_URL}</accessURL>
    </interface>
    <interface xsi:type="vs:ParamHTTP" role="std">
      <accessURL use="full">{SYNC_URL}</accessURL>
      <securityMethod standardID="ivo://ivoa.net/sso#token"/>
      <securityMethod standardID="ivo://ivoa.net/sso#tls-with-certificate"/>
    </interface>
  </capability>
</vosi:capabilities>
""".encode()


_XMLNS = 'xmlns:vos="http://www.ivoa.net/xml/VOSpace/v2.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"'


def data_child(uri: str, length: int | None = None) -> str:
    """Return a DataNode child element for embedding in a container listing."""
    properties = (
        "<vos:properties/>"
        if length is None
        else (
            '<vos:properties><vos:property uri="ivo://ivoa.net/vospace/core#length">'
            f"{length}</vos:property></vos:properties>"
        )
    )
    return f'<vos:node xsi:type="vos:DataNode" uri="{uri}">{properties}</vos:node>'


def container_child(uri: str) -> str:
    """Return a ContainerNode child element for embedding in a listing."""
    return f'<vos:node xsi:type="vos:ContainerNode" uri="{uri}"><vos:properties/></vos:node>'


def data_xml(uri: str, length: int | None = None) -> bytes:
    """Return a standalone DataNode document with an optional length property."""
    child = data_child(uri, length)
    return child.replace("<vos:node ", f"<vos:node {_XMLNS} ", 1).encode()


def container_xml(uri: str, children: str = "") -> bytes:
    """Return a ContainerNode listing document with the given child elements."""
    return (
        f'<vos:node {_XMLNS} xsi:type="vos:ContainerNode" uri="{uri}">'
        f"<vos:properties/><vos:nodes>{children}</vos:nodes></vos:node>"
    ).encode()


ROOT_CONTAINER = container_xml(f"vos://{AUTHORITY}")


def call_urls(router: respx.Router, method: str, prefix: str) -> list[str]:
    """Return the recorded request URLs matching one method and URL prefix."""
    return [
        str(call.request.url)
        for call in router.calls
        if call.request.method == method and str(call.request.url).startswith(prefix)
    ]


async def stream_body(data: bytes) -> AsyncIterator[bytes]:
    """Yield ``data`` once so a mock response is genuinely streamable."""
    yield data


def make_sim_fs(
    router: respx.Router,
    sim: Any,
    *,
    asynchronous: bool = True,
) -> VOSpaceFileSystem:
    """Install ``sim`` on ``router`` and build a filesystem over it."""
    sim.install(router)
    return make_fs(router, asynchronous=asynchronous)


def mock_capabilities(router: respx.Router) -> None:
    """Register the standard capabilities response on the router."""
    router.get("/capabilities").mock(return_value=httpx.Response(200, content=CAPABILITIES))


def transfer_details(endpoint: str) -> bytes:
    """Return a transfer-details document advertising one anonymous endpoint."""
    return (
        f'<vos:transfer xmlns:vos="http://www.ivoa.net/xml/VOSpace/v2.0" version="2.1">'
        f'<vos:protocol uri="ivo://ivoa.net/vospace/core#httpsget">'
        f"<vos:endpoint>{endpoint}</vos:endpoint></vos:protocol></vos:transfer>"
    ).encode()


def target_path(content: bytes | None) -> str:
    """Extract the internal path from a transfer document's target element."""
    match = re.search(r"<[^>]*target[^>]*>([^<]+)</", (content or b"").decode())
    if match is None:
        return "/"
    prefix = f"vos://{AUTHORITY}"
    return match.group(1).strip()[len(prefix) :] or "/"


def data_node_response(request: httpx.Request, files: dict[str, bytes]) -> httpx.Response:
    """Return root or DataNode metadata for the transfer test helper."""
    raw_path = request.url.raw_path.split(b"?", 1)[0].decode()
    suffix = unquote(raw_path.removeprefix(urlsplit(NODES_URL).path))
    path = suffix or "/"
    if path == "/":
        return httpx.Response(200, content=ROOT_CONTAINER)
    if path not in files:
        return httpx.Response(404)
    document = data_xml(f"vos://{AUTHORITY}{path}", len(files[path]))
    return httpx.Response(200, content=document)


def mock_transfers(  # noqa: C901 - one handler per transfer step
    router: respx.Router,
    files: dict[str, bytes],
    *,
    honour_range: bool = False,
) -> None:
    """Wire path-aware synchronous negotiation and byte endpoints.

    Each key in ``files`` is an internal path served at a per-path byte
    endpoint; the negotiation POST and transfer-details GET route to it.

    When ``honour_range`` is true, a ``Range: bytes=...`` GET returns ``206``
    with a matching ``Content-Range`` (minoc-like). Otherwise ``Range`` is
    ignored and the whole object is returned as ``200`` (Cavern-like).
    """
    mock_capabilities(router)

    router.get(url__regex=rf"^{re.escape(NODES_URL)}(?:/[^?]*)?(?:\?.*)?$").mock(
        side_effect=lambda request: data_node_response(request, files)
    )

    def negotiate_post(request: httpx.Request) -> httpx.Response:
        if request.url.params.get("REQUEST") == "redirect":
            target = request.url.params.get("TARGET", "")
            internal = "/" + target.split("://", 1)[-1].split("/", 1)[-1]
            if internal == "/":
                internal = "/"
            endpoint = f"{BASE_URL}/files?p={quote(internal if internal.startswith('/') else '/' + internal)}"
            return httpx.Response(303, headers={"Location": endpoint})
        details = f"{BASE_URL}/details?t={quote(target_path(request.content))}"
        return httpx.Response(303, headers={"Location": details})

    router.post(SYNC_URL).mock(side_effect=negotiate_post)

    def details_get(request: httpx.Request) -> httpx.Response:
        path = request.url.params["t"]
        endpoint = f"{BASE_URL}/files?p={quote(path)}"
        return httpx.Response(200, content=transfer_details(endpoint))

    router.get(url__regex=rf"^{re.escape(BASE_URL)}/details").mock(side_effect=details_get)

    def byte_op(request: httpx.Request) -> httpx.Response:
        path = request.url.params["p"]
        if request.method in ("PUT", "POST"):
            files[path] = request.content
            return httpx.Response(201)
        if path not in files:
            return httpx.Response(404)
        content = files[path]
        if content == b"":
            return httpx.Response(204)
        if honour_range:
            ranged = _ranged_response(request, content)
            if ranged is not None:
                return ranged
        # An async-generator body makes the mock response genuinely streamable.
        return httpx.Response(200, content=stream_body(content))

    router.route(url__regex=rf"^{re.escape(BASE_URL)}/files").mock(side_effect=byte_op)


def _ranged_response(request: httpx.Request, content: bytes) -> httpx.Response | None:
    """Return a 206 for a simple ``bytes=`` Range, or None to fall through."""
    raw = request.headers.get("range")
    if raw is None or not raw.startswith("bytes="):
        return None
    spec = raw.removeprefix("bytes=")
    size = len(content)
    if spec.startswith("-"):
        length = int(spec[1:])
        start = max(0, size - length)
        end = size - 1
    elif spec.endswith("-"):
        start = int(spec[:-1])
        end = size - 1
    else:
        start_s, end_s = spec.split("-", 1)
        start = int(start_s)
        end = int(end_s)
    if start < 0 or end < start or start >= size:
        return httpx.Response(416, headers={"Content-Range": f"bytes */{size}"})

    end = min(end, size - 1)

    body = content[start : end + 1]
    return httpx.Response(
        206,
        content=stream_body(body),
        headers={
            "Content-Range": f"bytes {start}-{end}/{size}",
            "Content-Length": str(len(body)),
        },
    )


@pytest.fixture
def router() -> Iterator[respx.Router]:
    """A respx router bound to the test service base URL.

    Unmatched requests raise (blocking network access). Tests that want to
    assert every route was called set ``router.assert_all_called`` or use
    ``assert_all_called=True`` per route.
    """
    router = respx.Router(base_url=BASE_URL, assert_all_mocked=True)
    yield router
    router.reset()


def make_fs(
    router: respx.Router,
    *,
    asynchronous: bool = False,
    endpoint_override: str | None = None,
    **options: Any,
) -> VOSpaceFileSystem:
    """Build a filesystem whose transport seam is the given respx router."""
    transport = httpx.MockTransport(router.async_handler)
    return VOSpaceFileSystem(
        endpoint_override or BASE_URL,
        transport=transport,
        asynchronous=asynchronous,
        skip_instance_cache=True,
        **options,
    )
