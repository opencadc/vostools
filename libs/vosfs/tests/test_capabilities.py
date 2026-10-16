"""Tests for VOSI capability discovery and the binding cache (section 5)."""

import httpx
import pytest
import respx
from conftest import BASE_URL, make_fs
from conftest import CAPABILITIES as FULL_CAPABILITIES

from vosfs import capabilities
from vosfs.capabilities import (
    CERTIFICATE_METHOD,
    TOKEN_METHOD,
    parse_bindings,
)


def _caps(security_method: str) -> capabilities.ServiceBindings:
    return parse_bindings(FULL_CAPABILITIES, security_method=security_method)


# --- binding resolution ---------------------------------------------------------


def test_resolves_anonymous_bindings() -> None:
    bindings = _caps(capabilities.ANONYMOUS_METHOD)
    assert bindings.require_nodes() == f"{BASE_URL}/nodes"
    assert bindings.require_sync() == f"{BASE_URL}/synctrans"


def test_resolves_token_and_certificate_bindings() -> None:
    assert _caps(TOKEN_METHOD).require_nodes() == f"{BASE_URL}/nodes"
    assert _caps(CERTIFICATE_METHOD).require_sync() == f"{BASE_URL}/synctrans"


def test_missing_binding_raises_not_implemented() -> None:
    only_nodes = b"""<vosi:capabilities
        xmlns:vosi="http://www.ivoa.net/xml/VOSICapabilities/v1.0"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
      <capability standardID="ivo://ivoa.net/std/VOSpace/v2.0#nodes">
        <interface xsi:type="ParamHTTP" role="std">
          <accessURL use="base">https://h.test/arc/nodes</accessURL>
        </interface>
      </capability>
    </vosi:capabilities>"""
    bindings = parse_bindings(only_nodes, security_method=capabilities.ANONYMOUS_METHOD)
    assert bindings.require_nodes() == "https://h.test/arc/nodes"
    with pytest.raises(NotImplementedError, match="synchronous-transfer"):
        bindings.require_sync()


def test_credential_not_advertised_raises_permission_error() -> None:
    token_only = b"""<vosi:capabilities
        xmlns:vosi="http://www.ivoa.net/xml/VOSICapabilities/v1.0"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
      <capability standardID="ivo://ivoa.net/std/VOSpace/v2.0#nodes">
        <interface xsi:type="ParamHTTP" role="std">
          <accessURL use="base">https://h.test/arc/nodes</accessURL>
          <securityMethod standardID="ivo://ivoa.net/sso#token"/>
        </interface>
      </capability>
    </vosi:capabilities>"""
    with pytest.raises(PermissionError, match="security method"):
        parse_bindings(token_only, security_method=capabilities.ANONYMOUS_METHOD)


def test_non_param_http_and_non_std_interfaces_ignored() -> None:
    mixed = b"""<vosi:capabilities
        xmlns:vosi="http://www.ivoa.net/xml/VOSICapabilities/v1.0"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
      <capability standardID="ivo://ivoa.net/std/VOSpace/v2.0#nodes">
        <interface xsi:type="WebBrowser" role="std">
          <accessURL use="base">https://h.test/arc/browser</accessURL>
        </interface>
        <interface xsi:type="ParamHTTP" role="secondary">
          <accessURL use="base">https://h.test/arc/secondary</accessURL>
        </interface>
        <interface xsi:type="ParamHTTP" role="std">
          <accessURL use="base">https://h.test/arc/nodes</accessURL>
        </interface>
      </capability>
    </vosi:capabilities>"""
    bindings = parse_bindings(mixed, security_method=capabilities.ANONYMOUS_METHOD)
    assert bindings.require_nodes() == "https://h.test/arc/nodes"


def test_access_url_falls_back_when_use_absent() -> None:
    no_use = b"""<vosi:capabilities
        xmlns:vosi="http://www.ivoa.net/xml/VOSICapabilities/v1.0"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
      <capability standardID="ivo://ivoa.net/std/VOSpace/v2.0#nodes">
        <interface xsi:type="ParamHTTP" role="std">
          <accessURL>https://h.test/arc/nodes</accessURL>
        </interface>
      </capability>
    </vosi:capabilities>"""
    bindings = parse_bindings(no_use, security_method=capabilities.ANONYMOUS_METHOD)
    assert bindings.require_nodes() == "https://h.test/arc/nodes"


def test_interface_without_access_url_yields_no_binding() -> None:
    no_url = b"""<vosi:capabilities
        xmlns:vosi="http://www.ivoa.net/xml/VOSICapabilities/v1.0"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
      <capability standardID="ivo://ivoa.net/std/VOSpace/v2.0#nodes">
        <interface xsi:type="ParamHTTP" role="std"/>
      </capability>
    </vosi:capabilities>"""
    bindings = parse_bindings(no_url, security_method=capabilities.ANONYMOUS_METHOD)
    with pytest.raises(NotImplementedError, match="node"):
        bindings.require_nodes()


# --- discovery through the filesystem -------------------------------------------


async def test_discovery_fetches_once_and_caches(router: respx.Router) -> None:
    route = router.get("/capabilities").mock(
        return_value=httpx.Response(200, content=FULL_CAPABILITIES),
    )
    fs = make_fs(router, asynchronous=True)
    first = await fs._get_bindings()
    second = await fs._get_bindings()
    assert first is second
    assert route.call_count == 1
    assert first.require_nodes() == f"{BASE_URL}/nodes"
    await fs.aclose()


async def test_discovery_sends_bearer_when_token_configured(
    router: respx.Router,
) -> None:
    route = router.get("/capabilities").mock(
        return_value=httpx.Response(200, content=FULL_CAPABILITIES),
    )
    fs = make_fs(router, asynchronous=True, token="abc123")
    await fs._get_bindings()
    assert route.calls.last.request.headers["Authorization"] == "Bearer abc123"
    await fs.aclose()


async def test_discovery_maps_error_status(router: respx.Router) -> None:
    router.get("/capabilities").mock(return_value=httpx.Response(500, text="boom"))
    fs = make_fs(router, asynchronous=True)
    with pytest.raises(OSError, match="500"):
        await fs._get_bindings()
    await fs.aclose()


async def test_discovery_uses_certificate_client(router: respx.Router) -> None:
    router.get("/capabilities").mock(
        return_value=httpx.Response(200, content=FULL_CAPABILITIES),
    )
    fs = make_fs(router, asynchronous=True, certfile="/tmp/proxy.pem")  # noqa: S108
    bindings = await fs._get_bindings()
    assert bindings.require_sync() == f"{BASE_URL}/synctrans"
    await fs.aclose()


async def test_discovery_maps_transport_error(router: respx.Router) -> None:
    router.get("/capabilities").mock(side_effect=httpx.ConnectError("down"))
    fs = make_fs(router, asynchronous=True)
    with pytest.raises(ConnectionError):
        await fs._get_bindings()
    await fs.aclose()


def test_binding_rejects_mismatched_access_url_use() -> None:
    doc = f"""<?xml version="1.0"?>
<vosi:capabilities xmlns:vosi="http://www.ivoa.net/xml/VOSICapabilities/v1.0"
                   xmlns:vs="http://www.ivoa.net/xml/VODataService/v1.1"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <capability standardID="{capabilities.NODES_STANDARD_ID}">
    <interface xsi:type="vs:ParamHTTP" role="std">
      <accessURL use="full">https://h/nodes</accessURL>
    </interface>
  </capability>
</vosi:capabilities>""".encode()
    bindings = parse_bindings(doc, security_method=capabilities.ANONYMOUS_METHOD)
    assert bindings.nodes_url is None
    with pytest.raises(NotImplementedError):
        bindings.require_nodes()
