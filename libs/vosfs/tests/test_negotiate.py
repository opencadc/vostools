"""Tests for synchronous byte negotiation and credential routing (section 7)."""

import itertools

import httpx
import pytest
import respx
from conftest import (
    AUTHORITY,
    BASE_URL,
    NODES_URL,
    SYNC_URL,
    make_fs,
    mock_capabilities,
)

from vosfs import _transfer, negotiate
from vosfs.capabilities import ANONYMOUS_METHOD, CERTIFICATE_METHOD, TOKEN_METHOD
from vosfs.negotiate import (
    DIRECTION_PULL,
    PROTOCOL_HTTPS_GET,
    NegotiatedEndpoint,
    choose_protocol,
    parse_transfer_details,
    validate_redirect,
)

VOS = 'xmlns:vos="http://www.ivoa.net/xml/VOSpace/v2.0"'
XSI = 'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"'
ROOT = (
    f'<vos:node {VOS} {XSI} xsi:type="vos:ContainerNode" uri="vos://{AUTHORITY}">'
    "<vos:properties/><vos:nodes/></vos:node>"
).encode()


def _details(endpoint: str, *, security_method: str | None = None) -> bytes:
    security = f'<vos:securityMethod standardID="{security_method}"/>' if security_method else ""
    return f"""<vos:transfer {VOS} version="2.1">
      <vos:target>vos://{AUTHORITY}/file.txt</vos:target>
      <vos:direction>pullFromVoSpace</vos:direction>
      <vos:protocol uri="ivo://ivoa.net/vospace/core#httpsget">
        <vos:endpoint>{endpoint}</vos:endpoint>
        {security}
      </vos:protocol>
    </vos:transfer>""".encode()


# --- pure helpers ---------------------------------------------------------------


def test_parse_transfer_details_anonymous() -> None:
    endpoints = parse_transfer_details(_details("https://h.test/files/x"))
    assert endpoints == [NegotiatedEndpoint("https://h.test/files/x", ANONYMOUS_METHOD)]


def test_parse_transfer_details_with_security_method() -> None:
    protocols = parse_transfer_details(_details("https://h.test/f", security_method=TOKEN_METHOD))
    assert protocols[0].security_method == TOKEN_METHOD


def test_parse_transfer_details_without_endpoint_raises() -> None:
    empty = f'<vos:transfer {VOS} version="2.1"></vos:transfer>'.encode()
    with pytest.raises(OSError, match="no usable protocol"):
        parse_transfer_details(empty)


def test_choose_protocol_prefers_compatible() -> None:
    endpoints = [
        NegotiatedEndpoint("https://h/cert", CERTIFICATE_METHOD),
        NegotiatedEndpoint("https://h/anon", ANONYMOUS_METHOD),
    ]
    assert choose_protocol(endpoints, TOKEN_METHOD).url == "https://h/anon"


def test_choose_protocol_no_match_raises() -> None:
    endpoints = [NegotiatedEndpoint("https://h/cert", CERTIFICATE_METHOD)]
    with pytest.raises(OSError, match="no negotiated endpoint"):
        choose_protocol(endpoints, TOKEN_METHOD)


@pytest.mark.parametrize(
    ("location", "sending_bearer"),
    [
        (None, False),
        ("ftp://h/x", False),
        ("https://u:p@h/x", False),
        ("http://h/x", True),
    ],
)
def test_validate_redirect_rejections(location: str | None, sending_bearer: bool) -> None:
    with pytest.raises(OSError, match=r"redirect|Location|bearer"):
        validate_redirect(location, base=SYNC_URL, sending_bearer=sending_bearer)


def test_validate_redirect_resolves_relative() -> None:
    # An absolute-path Location resolves against the host root, per URL rules.
    resolved = validate_redirect("/synctrans/results", base=SYNC_URL, sending_bearer=False)
    assert resolved == "https://staging.canfar.net/synctrans/results"


def test_parse_transfer_details_skips_protocol_without_endpoint() -> None:
    doc = f"""<vos:transfer {VOS} version="2.1">
      <vos:protocol uri="ivo://ivoa.net/vospace/core#httpsget"/>
    </vos:transfer>""".encode()
    with pytest.raises(OSError, match="no usable protocol"):
        parse_transfer_details(doc)


# --- negotiation through the filesystem -----------------------------------------


def _mock_negotiation(router: respx.Router, endpoint: str, *, security_method: str | None = None) -> None:
    mock_capabilities(router)
    router.get(NODES_URL).mock(return_value=httpx.Response(200, content=ROOT))
    details_url = f"{BASE_URL}/synctrans/results"
    router.post(SYNC_URL).mock(
        return_value=httpx.Response(303, headers={"Location": details_url}),
    )
    router.get(details_url).mock(
        return_value=httpx.Response(200, content=_details(endpoint, security_method=security_method)),
    )


async def test_negotiate_read_returns_endpoint(router: respx.Router) -> None:
    endpoint = f"{BASE_URL}/files/abc"
    _mock_negotiation(router, endpoint)
    fs = make_fs(router, asynchronous=True)
    negotiated = await _transfer.negotiate_endpoint(
        fs, "/file.txt", direction=DIRECTION_PULL, protocol_uri=PROTOCOL_HTTPS_GET
    )
    assert negotiated == NegotiatedEndpoint(endpoint, ANONYMOUS_METHOD)
    await fs.aclose()


async def test_negotiate_accepts_direct_open_cadc_byte_endpoint(
    router: respx.Router,
) -> None:
    mock_capabilities(router)
    router.get(NODES_URL).mock(return_value=httpx.Response(200, content=ROOT))
    endpoint = f"{BASE_URL}/files/preauth:token/file.txt"
    router.post(SYNC_URL).mock(
        return_value=httpx.Response(303, headers={"Location": endpoint}),
    )
    fs = make_fs(router, asynchronous=True, token="service-token")

    negotiated = await _transfer.negotiate_endpoint(
        fs, "/file.txt", direction=DIRECTION_PULL, protocol_uri=PROTOCOL_HTTPS_GET
    )

    assert negotiated == NegotiatedEndpoint(endpoint, ANONYMOUS_METHOD)
    assert not any(call.request.url == endpoint for call in router.calls)
    await fs.aclose()


async def test_negotiate_follows_transfer_result_redirect(
    router: respx.Router,
) -> None:
    mock_capabilities(router)
    router.get(NODES_URL).mock(return_value=httpx.Response(200, content=ROOT))
    details_url = f"{BASE_URL}/transfers/job/results/transferDetails"
    result_url = f"{BASE_URL}/xfer/job"
    endpoint = f"{BASE_URL}/files/job"
    router.post(SYNC_URL).mock(
        return_value=httpx.Response(303, headers={"Location": details_url}),
    )
    router.get(details_url).mock(
        return_value=httpx.Response(303, headers={"Location": result_url}),
    )
    router.get(result_url).mock(
        return_value=httpx.Response(200, content=_details(endpoint, security_method=CERTIFICATE_METHOD)),
    )
    fs = make_fs(router, asynchronous=True, certfile="/tmp/proxy.pem")  # noqa: S108
    negotiated = await _transfer.negotiate_endpoint(
        fs, "/file.txt", direction=DIRECTION_PULL, protocol_uri=PROTOCOL_HTTPS_GET
    )
    assert negotiated == NegotiatedEndpoint(endpoint, CERTIFICATE_METHOD)
    await fs.aclose()


async def test_negotiate_rejects_redirect_loop(router: respx.Router) -> None:
    mock_capabilities(router)
    router.get(NODES_URL).mock(return_value=httpx.Response(200, content=ROOT))
    location = f"{BASE_URL}/transfers/loop/results/transferDetails"
    router.post(SYNC_URL).mock(
        return_value=httpx.Response(303, headers={"Location": location}),
    )
    router.get(location).mock(
        return_value=httpx.Response(303, headers={"Location": location}),
    )
    fs = make_fs(router, asynchronous=True)
    with pytest.raises(OSError, match="redirect loop"):
        await _transfer.negotiate_endpoint(fs, "/file.txt", direction=DIRECTION_PULL, protocol_uri=PROTOCOL_HTTPS_GET)
    await fs.aclose()


async def test_negotiate_rejects_more_than_five_redirects(
    router: respx.Router,
) -> None:
    mock_capabilities(router)
    router.get(NODES_URL).mock(return_value=httpx.Response(200, content=ROOT))
    locations = [f"{BASE_URL}/transfers/hop-{index}" for index in range(6)]
    router.post(SYNC_URL).mock(
        return_value=httpx.Response(303, headers={"Location": locations[0]}),
    )
    for current, following in itertools.pairwise(locations):
        router.get(current).mock(
            return_value=httpx.Response(303, headers={"Location": following}),
        )
    fs = make_fs(router, asynchronous=True)
    with pytest.raises(OSError, match="more than five"):
        await _transfer.negotiate_endpoint(fs, "/file.txt", direction=DIRECTION_PULL, protocol_uri=PROTOCOL_HTTPS_GET)
    await fs.aclose()


async def test_negotiate_non_303_maps_error(router: respx.Router) -> None:
    mock_capabilities(router)
    router.get(NODES_URL).mock(return_value=httpx.Response(200, content=ROOT))
    router.post(SYNC_URL).mock(return_value=httpx.Response(400, text="bad transfer"))
    fs = make_fs(router, asynchronous=True)
    with pytest.raises(OSError, match="400"):
        await _transfer.negotiate_endpoint(fs, "/file.txt", direction=DIRECTION_PULL, protocol_uri=PROTOCOL_HTTPS_GET)
    await fs.aclose()


async def test_byte_send_anonymous_has_no_auth(router: respx.Router) -> None:
    seen: dict[str, str | None] = {}

    def capture(request: httpx.Request) -> httpx.Response:
        seen["auth"] = request.headers.get("authorization")
        return httpx.Response(200, content=b"hello")

    router.get(f"{BASE_URL}/files/abc").side_effect = capture
    fs = make_fs(router, asynchronous=True, token="secret")
    endpoint = NegotiatedEndpoint(f"{BASE_URL}/files/abc", ANONYMOUS_METHOD)
    response = await _transfer.byte_send(fs, endpoint, "GET")
    assert response.content == b"hello"
    assert seen["auth"] is None
    await fs.aclose()


async def test_byte_send_token_endpoint_sends_bearer(router: respx.Router) -> None:
    seen: dict[str, str | None] = {}

    def capture(request: httpx.Request) -> httpx.Response:
        seen["auth"] = request.headers.get("authorization")
        return httpx.Response(200)

    router.get("https://cross.test/files/x").side_effect = capture
    fs = make_fs(router, asynchronous=True, token="tok")
    endpoint = NegotiatedEndpoint("https://cross.test/files/x", TOKEN_METHOD)
    await _transfer.byte_send(fs, endpoint, "GET")
    assert seen["auth"] == "Bearer tok"
    await fs.aclose()


async def test_byte_send_token_endpoint_requires_https(router: respx.Router) -> None:
    fs = make_fs(router, asynchronous=True, token="tok")
    endpoint = NegotiatedEndpoint("http://insecure.test/files/x", TOKEN_METHOD)
    with pytest.raises(OSError, match="https"):
        await _transfer.byte_send(fs, endpoint, "GET")
    await fs.aclose()


async def test_byte_send_certificate_endpoint_requires_https(
    router: respx.Router,
) -> None:
    fs = make_fs(router, asynchronous=True, certfile="/tmp/p.pem")  # noqa: S108
    endpoint = NegotiatedEndpoint("http://insecure.test/files/x", CERTIFICATE_METHOD)
    with pytest.raises(OSError, match="https"):
        await _transfer.byte_send(fs, endpoint, "GET")
    await fs.aclose()


async def test_byte_send_rejects_redirect(router: respx.Router) -> None:
    router.get(f"{BASE_URL}/files/x").mock(
        return_value=httpx.Response(302, headers={"Location": "https://evil.test/"}),
    )
    fs = make_fs(router, asynchronous=True)
    endpoint = NegotiatedEndpoint(f"{BASE_URL}/files/x", ANONYMOUS_METHOD)
    with pytest.raises(OSError, match="redirect"):
        await _transfer.byte_send(fs, endpoint, "GET")
    await fs.aclose()


async def test_bearer_not_leaked_to_cross_origin_redirect(router: respx.Router) -> None:
    seen: dict[str, str | None] = {}
    mock_capabilities(router)
    router.get(NODES_URL).mock(return_value=httpx.Response(200, content=ROOT))
    cross = "https://evil.test/details"
    router.post(SYNC_URL).mock(
        return_value=httpx.Response(303, headers={"Location": cross}),
    )

    def capture(request: httpx.Request) -> httpx.Response:
        seen["auth"] = request.headers.get("authorization")
        return httpx.Response(200, content=_details(f"{BASE_URL}/files/x"))

    router.get(cross).side_effect = capture
    fs = make_fs(router, asynchronous=True, token="secret-token")
    await _transfer.negotiate_endpoint(fs, "/file.txt", direction=DIRECTION_PULL, protocol_uri=PROTOCOL_HTTPS_GET)
    # The bearer reaches the same-origin POST but never the cross-origin details GET.
    assert seen["auth"] is None
    await fs.aclose()


async def test_redirect_read_is_remembered_unsupported_after_a_details_redirect(
    router: respx.Router,
) -> None:
    mock_capabilities(router)
    router.get(NODES_URL).mock(return_value=httpx.Response(200, content=ROOT))
    router.post(url__regex=rf"^{SYNC_URL}").mock(
        side_effect=lambda _request: httpx.Response(
            303,
            headers={"Location": f"{BASE_URL}/details?t=/file.txt"},
        )
    )
    router.get(url__regex=rf"^{BASE_URL}/details").mock(
        return_value=httpx.Response(200, content=_details(f"{BASE_URL}/files/x"))
    )
    fs = make_fs(router, asynchronous=True)
    first = await _transfer.negotiate_endpoint(
        fs, "/file.txt", direction=DIRECTION_PULL, protocol_uri=PROTOCOL_HTTPS_GET
    )
    assert first.url == f"{BASE_URL}/files/x"
    assert fs._redirect_reads_unsupported is True
    posts_after_first = [call for call in router.calls if call.request.method == "POST"]
    await _transfer.negotiate_endpoint(fs, "/file.txt", direction=DIRECTION_PULL, protocol_uri=PROTOCOL_HTTPS_GET)
    posts_after_second = [call for call in router.calls if call.request.method == "POST"]
    assert len(posts_after_first) == 2
    assert len(posts_after_second) == len(posts_after_first) + 1
    assert "REQUEST" not in str(posts_after_second[-1].request.url)
    await fs.aclose()


def test_negotiate_module_exposes_directions() -> None:
    assert negotiate.DIRECTION_PUSH == "pushToVoSpace"
