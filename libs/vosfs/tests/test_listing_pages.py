"""Paged container listings stay under the XML parse cap."""

from __future__ import annotations

import re
from typing import TYPE_CHECKING

import httpx
import pytest
from conftest import AUTHORITY, NODES_URL, make_fs, mock_capabilities

from vosfs import VOSpaceError
from vosfs.filesystem import LISTING_PAGE_SIZE
from vosfs.xmlio import DEFAULT_LIMIT

if TYPE_CHECKING:
    import respx

NS = 'xmlns:vos="http://www.ivoa.net/xml/VOSpace/v2.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"'
_LENGTH = "ivo://ivoa.net/vospace/core#length"


def _document(children: list[tuple[str, int]]) -> bytes:
    body = "".join(
        f'<vos:node {NS} xsi:type="vos:DataNode" uri="vos://{AUTHORITY}/big/{name}">'
        f'<vos:properties><vos:property uri="{_LENGTH}">{length}</vos:property></vos:properties>'
        f"</vos:node>"
        for name, length in children
    )
    return (
        f'<vos:node {NS} xsi:type="vos:ContainerNode" uri="vos://{AUTHORITY}/big">'
        f"<vos:properties/><vos:nodes>{body}</vos:nodes></vos:node>"
    ).encode()


def _install(
    router: respx.Router,
    names: list[str],
    *,
    mode: str = "inclusive",
) -> list[httpx.Request]:
    requests: list[httpx.Request] = []
    children = [(name, index + 1) for index, name in enumerate(names)]

    def respond(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        if "limit" not in request.url.params:
            return httpx.Response(200, content=b"x" * (DEFAULT_LIMIT + 1))
        if mode == "over-cap":
            return httpx.Response(200, content=b"x" * (DEFAULT_LIMIT + 1))
        cursor = request.url.params.get("uri")
        if cursor is not None and mode == "reject-resume":
            return httpx.Response(400, text="InvalidURI")
        uris = [f"vos://{AUTHORITY}/big/{name}" for name in names]
        if cursor is None or mode == "repeat":
            start = 0
        elif cursor not in uris:
            return httpx.Response(400, text="InvalidURI")
        elif mode == "exclusive":
            start = uris.index(cursor) + 1
        else:
            start = uris.index(cursor)
        selected = children if mode == "ignore-limit" else children[start : start + int(request.url.params["limit"])]
        return httpx.Response(200, content=_document(selected))

    router.get(url__regex=rf"^{re.escape(NODES_URL)}/big(?:\?.*)?$").mock(side_effect=respond)
    return requests


async def _list(router: respx.Router, *, detail: bool) -> list:
    filesystem = make_fs(router, asynchronous=True)
    try:
        return await filesystem._ls("/big", detail=detail)
    finally:
        await filesystem.aclose()


async def test_unpaged_body_over_the_cap_is_listed_from_pages(router: respx.Router) -> None:
    mock_capabilities(router)
    names = [f"c{index:04d}" for index in range(LISTING_PAGE_SIZE + 1)]
    requests = _install(router, names)

    filesystem = make_fs(router, asynchronous=True)
    detailed = await filesystem._ls("/big", detail=True)
    plain = await filesystem._ls("/big", detail=False)
    await filesystem.aclose()

    assert [entry["name"] for entry in detailed] == [f"/big/{name}" for name in names]
    assert plain == [entry["name"] for entry in detailed]
    assert detailed[-1]["size"] == len(names)
    assert len(requests) == 2
    assert all(request.url.params.get("limit") == str(LISTING_PAGE_SIZE) for request in requests)
    assert requests[1].url.params["uri"] == f"vos://{AUTHORITY}/big/{names[LISTING_PAGE_SIZE - 1]}"


async def test_exact_page_stops_after_the_inclusive_cursor_child(router: respx.Router) -> None:
    mock_capabilities(router)
    names = [f"c{index:04d}" for index in range(LISTING_PAGE_SIZE)]
    requests = _install(router, names)

    listed = await _list(router, detail=False)

    assert listed == [f"/big/{name}" for name in names]
    assert len(requests) == 2
    assert listed.count(listed[-1]) == 1


async def test_repeated_first_page_is_not_appended(router: respx.Router) -> None:
    mock_capabilities(router)
    names = [f"c{index:04d}" for index in range(LISTING_PAGE_SIZE)]
    requests = _install(router, names, mode="repeat")

    listed = await _list(router, detail=False)

    assert listed == [f"/big/{name}" for name in names]
    assert len(requests) == 2


async def test_one_response_longer_than_a_page_is_complete(router: respx.Router) -> None:
    mock_capabilities(router)
    names = [f"c{index:04d}" for index in range(LISTING_PAGE_SIZE + 3)]
    requests = _install(router, names, mode="ignore-limit")

    listed = await _list(router, detail=False)

    assert listed == [f"/big/{name}" for name in names]
    assert len(requests) == 1


async def test_exclusive_resume_keeps_the_following_children(router: respx.Router) -> None:
    mock_capabilities(router)
    names = [f"c{index:04d}" for index in range(LISTING_PAGE_SIZE + 2)]
    _install(router, names, mode="exclusive")

    listed = await _list(router, detail=False)

    assert listed == [f"/big/{name}" for name in names]


async def test_rejected_resume_after_a_full_page_fails_the_listing(router: respx.Router) -> None:
    mock_capabilities(router)
    names = [f"c{index:04d}" for index in range(LISTING_PAGE_SIZE)]
    _install(router, names, mode="reject-resume")
    filesystem = make_fs(router, asynchronous=True)

    with pytest.raises(VOSpaceError, match="HTTP 400"):
        await filesystem._ls("/big")

    assert "/big" not in filesystem.dircache
    await filesystem.aclose()


async def test_response_over_the_cap_is_not_retried(router: respx.Router) -> None:
    mock_capabilities(router)
    requests = _install(router, ["only"], mode="over-cap")
    filesystem = make_fs(router, asynchronous=True)

    with pytest.raises(ValueError, match="XML body exceeds"):
        await filesystem._ls("/big")

    assert len(requests) == 1
    assert "/big" not in filesystem.dircache
    await filesystem.aclose()


def test_page_size_can_advance_an_inclusive_cursor() -> None:
    assert LISTING_PAGE_SIZE >= 2
