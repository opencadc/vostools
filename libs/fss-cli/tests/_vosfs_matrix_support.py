"""Shared mocked-VOSpace scaffolding for the native-vosfs matrix rows.

Split from ``_matrix_support`` so the core scaffolding stays importable in the
installed-wheel gate's core environment, which installs neither ``httpx`` nor
``vosfs``.
"""

from __future__ import annotations

from typing import TYPE_CHECKING

import httpx

from ._matrix_support import _ProbedSource

if TYPE_CHECKING:
    from collections.abc import Callable

    from vosfs import VOSpaceFileSystem

_BASE_URL = "https://example.test/arc"
_NODES_URL = f"{_BASE_URL}/nodes"
_SYNC_URL = f"{_BASE_URL}/synctrans"
_AUTHORITY = "example.test!vault"
_CAPABILITIES = f"""<?xml version="1.0" encoding="UTF-8"?>
<vosi:capabilities xmlns:vosi="http://www.ivoa.net/xml/VOSICapabilities/v1.0"
                   xmlns:vs="http://www.ivoa.net/xml/VODataService/v1.1"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <capability standardID="ivo://ivoa.net/std/VOSpace/v2.0#nodes">
    <interface xsi:type="vs:ParamHTTP" role="std">
      <accessURL use="base">{_NODES_URL}</accessURL>
    </interface>
  </capability>
</vosi:capabilities>
""".encode()
_SYNC_CAPABILITIES = f"""<?xml version="1.0" encoding="UTF-8"?>
<vosi:capabilities xmlns:vosi="http://www.ivoa.net/xml/VOSICapabilities/v1.0"
                   xmlns:vs="http://www.ivoa.net/xml/VODataService/v1.1"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <capability standardID="ivo://ivoa.net/std/VOSpace/v2.0#nodes">
    <interface xsi:type="vs:ParamHTTP" role="std">
      <accessURL use="base">{_NODES_URL}</accessURL>
    </interface>
  </capability>
  <capability standardID="ivo://ivoa.net/std/VOSpace#sync-2.1">
    <interface xsi:type="vs:ParamHTTP" role="std">
      <accessURL use="full">{_SYNC_URL}</accessURL>
    </interface>
  </capability>
</vosi:capabilities>
""".encode()


def _vos_properties(length: int | None = None, mtime: str | None = None) -> str:
    inner = ""
    if length is not None:
        inner += f'<vos:property uri="ivo://ivoa.net/vospace/core#length">{length}</vos:property>'
    if mtime is not None:
        inner += f'<vos:property uri="ivo://ivoa.net/vospace/core#mtime">{mtime}</vos:property>'
    return f"<vos:properties>{inner}</vos:properties>" if inner else "<vos:properties/>"


def _vos_document(kind: str, path: str, inner: str = "") -> bytes:
    return (
        '<vos:node xmlns:vos="http://www.ivoa.net/xml/VOSpace/v2.0" '
        'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
        f'xsi:type="vos:{kind}" uri="vos://{_AUTHORITY}{path}">'
        f"{inner}</vos:node>"
    ).encode()


def _vos_child(
    kind: str,
    path: str,
    *,
    length: int | None = None,
    mtime: str | None = None,
    target: str | None = None,
) -> str:
    inner = _vos_properties(length, mtime)
    if kind == "ContainerNode":
        inner += "<vos:nodes/>"
    if target is not None:
        inner += f"<vos:target>vos://{_AUTHORITY}{target}</vos:target>"
    return f'<vos:node xsi:type="vos:{kind}" uri="vos://{_AUTHORITY}{path}">{inner}</vos:node>'


def _vos_container(path: str, children: str = "") -> bytes:
    return _vos_document(
        "ContainerNode",
        path,
        f"<vos:properties/><vos:nodes>{children}</vos:nodes>",
    )


def _vos_data(path: str, *, length: int | None = None, mtime: str | None = None) -> bytes:
    return _vos_document("DataNode", path, _vos_properties(length, mtime))


def _vos_link(path: str, target: str) -> bytes:
    return _vos_document(
        "LinkNode",
        path,
        f"<vos:properties/><vos:target>vos://{_AUTHORITY}{target}</vos:target>",
    )


class _StrictMockTransport(httpx.MockTransport):
    """Serve only planned (method, path) responses and record every call."""

    def __init__(
        self,
        responses: dict[tuple[str, str], httpx.Response] | None = None,
    ) -> None:
        self.requests: list[tuple[str, str]] = []
        self.closed = False
        self._responses = responses or {}
        super().__init__(self._respond)

    async def _respond(self, request: httpx.Request) -> httpx.Response:
        call = (request.method, request.url.path)
        self.requests.append(call)
        response = self._responses.get(call)
        if response is None:
            message = f"unplanned mocked request: {call!r}"
            raise AssertionError(message)
        return response

    async def aclose(self) -> None:
        self.closed = True
        await super().aclose()


async def _close_vosfs(filesystem: VOSpaceFileSystem) -> None:
    await filesystem.aclose()


def _vosfs_source(
    responses: dict[tuple[str, str], httpx.Response] | None = None,
    *,
    transport_factory: Callable[[], httpx.MockTransport] | None = None,
) -> tuple[_ProbedSource[VOSpaceFileSystem], list[httpx.MockTransport]]:
    """Build a probed native-vosfs source backed by one mocked transport."""
    from vosfs import VOSpaceFileSystem

    transports: list[httpx.MockTransport] = []

    def make_filesystem() -> VOSpaceFileSystem:
        transport = transport_factory() if transport_factory is not None else _StrictMockTransport(responses)
        transports.append(transport)
        return VOSpaceFileSystem(
            _BASE_URL,
            transport=transport,
            asynchronous=True,
            skip_instance_cache=True,
            trust_env=False,
        )

    return _ProbedSource(make_filesystem, close=_close_vosfs), transports
