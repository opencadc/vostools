"""An in-memory OpenCADC VOSpace simulator wired onto a respx router.

The simulator maintains a node tree and blob store and serves the exact
requests the filesystem makes: capabilities, node GET/PUT/POST/DELETE with child
listings, synchronous transfer negotiation, and negotiated byte GET/PUT. It
lets namespace, copy/move, and the fsspec reusable-abstract tests run against a
faithful, hermetic backend.
"""

from __future__ import annotations

import re
from typing import TYPE_CHECKING
from urllib.parse import quote, unquote, urlsplit
from xml.etree import ElementTree as ET
from xml.sax.saxutils import escape, quoteattr

import httpx
from conftest import (
    AUTHORITY,
    BASE_URL,
    NODES_URL,
    SYNC_URL,
    mock_capabilities,
    stream_body,
    target_path,
    transfer_details,
)

from vosfs import paths

if TYPE_CHECKING:
    import respx

_NODES_PREFIX = urlsplit(NODES_URL).path
_NS = 'xmlns:vos="http://www.ivoa.net/xml/VOSpace/v2.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"'


class VOSpaceSim:
    """A minimal, stateful VOSpace backend for hermetic tests."""

    def __init__(self) -> None:
        """Start with an empty root container."""
        self.nodes: dict[str, str] = {}
        self.wire_types: dict[str, str] = {}
        self.authorities: dict[str, str] = {}
        self.blobs: dict[str, bytes] = {}
        self.targets: dict[str, str] = {}
        self.properties: dict[str, dict[str, str]] = {}
        self.node_update_requests: list[httpx.Request] = []
        self.byte_requests: list[httpx.Request] = []
        self.node_update_status = 200
        self.delete_requests: list[str] = []
        self.delete_statuses: dict[str, int] = {}
        self._transition_node("/", wire_type="ContainerNode")

    def add_container(self, path: str) -> VOSpaceSim:
        """Seed a container node and return self for chaining."""
        self._transition_node(path, wire_type="ContainerNode")
        return self

    def add_file(
        self,
        path: str,
        content: bytes = b"",
        *,
        wire_type: str = "DataNode",
    ) -> VOSpaceSim:
        """Seed a data node with content and return self for chaining."""
        self._transition_node(
            path,
            wire_type=wire_type,
            content=content,
        )
        return self

    def add_link(self, path: str, target: str) -> VOSpaceSim:
        """Seed a LinkNode and return self for chaining."""
        self._transition_node(path, wire_type="LinkNode", target=target)
        return self

    def with_authority(self, path: str, authority: str) -> VOSpaceSim:
        """Override one node URI authority and return self for chaining."""
        self.authorities[path] = authority
        return self

    def _transition_node(
        self,
        path: str,
        *,
        wire_type: str | None,
        content: bytes | None = None,
        target: str | None = None,
        preserve_identity: bool = False,
    ) -> None:
        """Create, replace, or delete a node across every per-path state map."""
        if wire_type is None:
            self.nodes.pop(path, None)
            self.wire_types.pop(path, None)
            self.authorities.pop(path, None)
            self.blobs.pop(path, None)
            self.targets.pop(path, None)
            self.properties.pop(path, None)
            return
        properties = self.properties.get(path) if preserve_identity else None
        authority = self.authorities.get(path) if preserve_identity else None
        self.nodes[path] = "link" if target is not None else "container" if content is None else "data"
        self.wire_types[path] = wire_type
        if authority is None:
            self.authorities.pop(path, None)
        else:
            self.authorities[path] = authority
        if content is None:
            self.blobs.pop(path, None)
        else:
            self.blobs[path] = content
        if target is None:
            self.targets.pop(path, None)
        else:
            self.targets[path] = target
        self.properties[path] = dict(properties or {})

    def install(self, router: respx.Router) -> None:
        """Register every simulator route on ``router``."""
        mock_capabilities(router)
        router.route(url__regex=rf"^{re.escape(NODES_URL)}").mock(side_effect=self._node_op)
        router.post(SYNC_URL).mock(side_effect=self._negotiate)
        router.get(url__regex=rf"^{re.escape(BASE_URL)}/details").mock(side_effect=self._details)
        router.route(url__regex=rf"^{re.escape(BASE_URL)}/files").mock(side_effect=self._byte_op)

    # -- node operations -----------------------------------------------------

    def _node_op(self, request: httpx.Request) -> httpx.Response:  # noqa: PLR0911
        path = self._node_path(request)
        if request.method == "GET":
            # Cavern honours ``limit=0`` by omitting a container's children.
            return self._node_get(path, children=request.url.params.get("limit") != "0")
        if request.method == "PUT":
            self._transition_node(path, wire_type="ContainerNode")
            return httpx.Response(201)
        if request.method == "POST":
            return self._node_post(path, request)
        if request.method == "DELETE":
            self.delete_requests.append(path)
            if status := self.delete_statuses.get(path):
                return httpx.Response(status)
            if path not in self.nodes:
                return httpx.Response(404)
            self._transition_node(path, wire_type=None)
            return httpx.Response(200)
        return httpx.Response(405)  # pragma: no cover - unused verbs

    def _node_post(self, path: str, request: httpx.Request) -> httpx.Response:
        self.node_update_requests.append(request)
        if path not in self.nodes:
            return httpx.Response(404)
        if self.node_update_status != 200:
            return httpx.Response(self.node_update_status)
        if _wire_type(request.content) != self.wire_types[path]:
            return httpx.Response(400, text="node xsi:type does not match stored node")
        self.properties.setdefault(path, {}).update(_properties(request.content))
        return httpx.Response(200)

    def _node_get(self, path: str, *, children: bool = True) -> httpx.Response:
        kind = self.nodes.get(path)
        if kind is None:
            return httpx.Response(404)
        if kind == "container":
            return httpx.Response(200, content=self._container_document(path, children=children))
        return httpx.Response(200, content=self._data_document(path))

    def _node_path(self, request: httpx.Request) -> str:
        suffix = request.url.path[len(_NODES_PREFIX) :]
        return paths.strip_protocol(unquote(suffix)) if suffix else "/"

    def _uri(self, path: str) -> str:
        authority = self.authorities.get(path, AUTHORITY)
        return f"vos://{authority}" if path == "/" else f"vos://{authority}{path}"

    def _container_document(self, path: str, *, children: bool = True) -> bytes:
        children_xml = "".join(
            self._child_element(child)
            for child in (sorted(self.nodes) if children else ())
            if child != path and paths.parent(child) == path
        )
        return (
            f'<vos:node {_NS} xsi:type="vos:ContainerNode" uri="{self._uri(path)}">'
            f"<vos:properties>{self._property_elements(path)}</vos:properties>"
            f"<vos:nodes>{children_xml}</vos:nodes></vos:node>"
        ).encode()

    def _child_element(self, path: str) -> str:
        if self.nodes[path] == "container":
            return (
                f'<vos:node {_NS} xsi:type="vos:ContainerNode" uri="{self._uri(path)}">'
                f"<vos:properties>{self._property_elements(path)}</vos:properties>"
                f"</vos:node>"
            )
        return self._data_element(path)

    def _data_element(self, path: str) -> str:
        if self.nodes[path] == "link":
            return (
                f'<vos:node {_NS} xsi:type="vos:LinkNode" uri="{self._uri(path)}">'
                f"<vos:target>{escape(self.targets[path])}</vos:target></vos:node>"
            )
        length = len(self.blobs.get(path, b""))
        length_uri = "ivo://ivoa.net/vospace/core#length"
        wire_type = self.wire_types[path]
        return (
            f'<vos:node {_NS} xsi:type="vos:{wire_type}" uri="{self._uri(path)}">'
            f'<vos:properties><vos:property uri="{length_uri}">{length}</vos:property>'
            f"{self._property_elements(path)}"
            f"</vos:properties></vos:node>"
        )

    def _property_elements(self, path: str) -> str:
        return "".join(
            f"<vos:property uri={quoteattr(uri)}>{escape(value)}</vos:property>"
            for uri, value in self.properties.get(path, {}).items()
        )

    def _data_document(self, path: str) -> bytes:
        return self._data_element(path).encode()

    # -- byte transfer -------------------------------------------------------

    def _negotiate(self, request: httpx.Request) -> httpx.Response:
        if request.url.params.get("REQUEST") == "redirect":
            target = request.url.params.get("TARGET", "")
            path = "/" + target.split("://", 1)[-1].split("/", 1)[-1]
            endpoint = f"{BASE_URL}/files?p={quote(path)}"
            return httpx.Response(303, headers={"Location": endpoint})
        target = target_path(request.content)
        location = f"{BASE_URL}/details?t={quote(target)}"
        return httpx.Response(303, headers={"Location": location})

    def _details(self, request: httpx.Request) -> httpx.Response:
        path = request.url.params["t"]
        endpoint = f"{BASE_URL}/files?p={quote(path)}"
        return httpx.Response(200, content=transfer_details(endpoint))

    def _byte_op(self, request: httpx.Request) -> httpx.Response:
        self.byte_requests.append(request)
        path = request.url.params["p"]
        if request.method == "PUT":
            self._transition_node(
                path,
                wire_type="DataNode",
                content=request.content,
                preserve_identity=True,
            )
            return httpx.Response(201)
        if path in self.targets:
            target = urlsplit(self.targets[path])
            path = target.path or "/"
        content = self.blobs.get(path)
        if content is None:
            return httpx.Response(404)
        if content == b"":
            return httpx.Response(204)
        return httpx.Response(200, content=stream_body(content))


def _properties(content: bytes | None) -> dict[str, str]:
    root = ET.fromstring(content or b"")  # noqa: S314 - test fixture XML generated by this suite
    namespace = "{http://www.ivoa.net/xml/VOSpace/v2.0}"
    return {
        element.get("uri", ""): element.text or ""
        for element in root.findall(f"{namespace}properties/{namespace}property")
    }


def _wire_type(content: bytes | None) -> str:
    root = ET.fromstring(content or b"")  # noqa: S314 - test fixture XML generated by this suite
    xsi_type = root.get("{http://www.w3.org/2001/XMLSchema-instance}type", "")
    return xsi_type.rsplit(":", 1)[-1]
