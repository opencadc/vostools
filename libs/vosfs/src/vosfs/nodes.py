"""VOSpace 2.1 XML node model: safe reader, document writer, and fsspec mapping.

This module implements the XML and node-model contract from the v0.3.0 TRD
(section 6). It provides a hardened reader for VOSpace 2.1 ``node`` and
container-listing documents, a small set of writers for the request bodies the
filesystem sends, and the fsspec metadata mapping that turns a parsed
:class:`Node` into an fsspec ``info`` dict.

Parsing is deliberately defensive: input is bounded before it reaches the XML
parser, and a document type declaration is rejected before any entity is read,
so a hostile response cannot mount an XXE or entity-expansion attack. Runtime
XSD validation is intentionally *not* performed; the pinned VOSpace 2.1 schema
is exercised only by the test suite.

Generated documents use the ``http://www.ivoa.net/xml/VOSpace/v2.0`` namespace
and carry ``version="2.1"`` on the document root, per the contract. The reader
and the serializer both use :mod:`xml.etree.ElementTree`, so no XML library
beyond the standard library is required at runtime.
"""

from __future__ import annotations

import xml.etree.ElementTree as ET
from dataclasses import dataclass
from types import MappingProxyType
from typing import TYPE_CHECKING, Any

from vosfs._access import access_info
from vosfs.xmlio import safe_parse

if TYPE_CHECKING:
    from collections.abc import Mapping

VOSPACE_NS = "http://www.ivoa.net/xml/VOSpace/v2.0"
VOSPACE_VERSION = "2.1"
XSI_NS = "http://www.w3.org/2001/XMLSchema-instance"
XML_HEADERS = {"Content-Type": "text/xml; charset=utf-8", "Accept": "text/xml"}

# VOSpace property URIs promoted to first-class :class:`Node` fields.
# ``#length``, ``#mtime``, and ``#date`` are IVOA VOSpace 2.1 standard
# properties. ``#MD5`` and ``#contenttype`` are OpenCADC-profile *extensions*:
# the IVOA 2.1 standard-property enumeration does not include them, and vosfs
# exposes them only because the OpenCADC wire emits them.
LENGTH_PROPERTY_URI = "ivo://ivoa.net/vospace/core#length"
MTIME_PROPERTY_URI = "ivo://ivoa.net/vospace/core#mtime"
DATE_PROPERTY_URI = "ivo://ivoa.net/vospace/core#date"
MD5_PROPERTY_URI = "ivo://ivoa.net/vospace/core#MD5"  # OpenCADC extension
CONTENT_TYPE_PROPERTY_URI = "ivo://ivoa.net/vospace/core#contenttype"  # OpenCADC extension

# Directions this profile emits during synchronous byte negotiation.
_ALLOWED_DIRECTIONS = ("pullFromVoSpace", "pushToVoSpace")

# The ``xsi:type`` local name of each OpenCADC node kind, mapped to the fsspec
# node model. Structured and unstructured data nodes are opaque files.
_NODE_TYPE_BY_XSI_LOCAL = {
    "ContainerNode": "container",
    "DataNode": "data",
    "StructuredDataNode": "data",
    "UnstructuredDataNode": "data",
    "LinkNode": "link",
}

# fsspec ``type`` value for each parsed node kind.
_INFO_TYPE_BY_NODE_TYPE = {
    "data": "file",
    "container": "directory",
    "link": "other",
}

# ElementTree serializes namespaces by prefix from a process-global registry.
# Registering the VOSpace and XML-Schema-instance prefixes keeps generated
# documents readable and, critically, keeps the ``vos:`` prefix used inside
# ``xsi:type`` attribute values bound to the VOSpace namespace so the QName
# resolves during schema validation.
ET.register_namespace("vos", VOSPACE_NS)
ET.register_namespace("xsi", XSI_NS)

_NODE_TAG = f"{{{VOSPACE_NS}}}node"
_NODES_TAG = f"{{{VOSPACE_NS}}}nodes"
_PROPERTIES_TAG = f"{{{VOSPACE_NS}}}properties"
_PROPERTY_TAG = f"{{{VOSPACE_NS}}}property"
_TARGET_TAG = f"{{{VOSPACE_NS}}}target"
_TRANSFER_TAG = f"{{{VOSPACE_NS}}}transfer"
_DIRECTION_TAG = f"{{{VOSPACE_NS}}}direction"
_PROTOCOL_TAG = f"{{{VOSPACE_NS}}}protocol"
_XSI_TYPE_ATTR = f"{{{XSI_NS}}}type"


@dataclass(frozen=True)
class Node:
    """An immutable view of a single parsed VOSpace node.

    Attributes:
        node_type: One of ``"data"``, ``"container"``, or ``"link"``.
            Structured and unstructured data nodes are reported as ``"data"``.
        uri: The node identifier URI carried by the document.
        size: The byte length for a data node, or ``0`` for containers and
            links.
        mtime: The IVOA ``mtime`` core property, falling back to the ``date``
            property, if present, else ``None``.
        md5: The ``MD5`` core property, if present, else ``None``.
        content_type: The ``contenttype`` core property, if present, else
            ``None``.
        target: The link target URI for a link node, else ``None``.
        properties: A read-only mapping of every URI-keyed property, including
            unknown and server-computed ones, preserved verbatim.
    """

    node_type: str
    uri: str
    size: int
    mtime: str | None
    md5: str | None
    content_type: str | None
    target: str | None
    properties: Mapping[str, str]


def parse_node(data: bytes) -> Node:
    """Parse a single ``node`` document into a :class:`Node`.

    Args:
        data: The raw XML response body.

    Returns:
        The parsed node.

    Raises:
        ValueError: If the body exceeds the bounded-parse limit, is malformed,
            uses a DTD or external entity, is not a ``node`` document, or
            declares an unknown node type.
    """
    root = safe_parse(data)
    return _node_from_element(root)


def parse_container(data: bytes) -> tuple[Node, list[Node]]:
    """Parse a node document once, returning the node and its immediate children.

    Avoids parsing the same body twice when a caller needs both the container
    node and its listing.
    """
    root = safe_parse(data)
    return _node_from_element(root), _children_of(root)


def _children_of(root: ET.Element) -> list[Node]:
    """Return the immediate ``<nodes>`` children of a parsed document root."""
    nodes_element = root.find(_NODES_TAG)
    if nodes_element is None:
        return []
    return [_node_from_element(child) for child in nodes_element.findall(_NODE_TAG)]


def to_info(node: Node, name: str) -> dict[str, Any]:
    """Map a :class:`Node` to an fsspec ``info`` dict.

    Args:
        node: The parsed node.
        name: The full normalized filesystem path, supplied by the caller and
            used verbatim as the ``name`` field.

    Returns:
        An fsspec info dict following the TRD section 6.1 mapping: data nodes
        become files with size and optional ``mtime``/``md5``/``content_type``;
        containers become directories; links become ``other`` entries carrying
        ``islink`` and ``target``.
    """
    info: dict[str, Any] = {
        "name": name,
        "type": _INFO_TYPE_BY_NODE_TYPE[node.node_type],
        "size": node.size,
        "uri": node.uri,
    }
    info.update(access_info(node.properties, node.node_type))
    if node.properties:
        info["properties"] = node.properties
    if node.node_type == "link":
        info["islink"] = True
        info["target"] = node.target
        return info
    if node.mtime is not None:
        info["mtime"] = node.mtime
    if node.node_type == "data":
        if node.md5 is not None:
            info["md5"] = node.md5
        if node.content_type is not None:
            info["content_type"] = node.content_type
    return info


def build_container_document(uri: str) -> bytes:
    """Build a ``ContainerNode`` PUT body for creating a directory.

    Args:
        uri: The node identifier URI of the container to create.

    Returns:
        A UTF-8 XML document with an empty required ``<nodes>`` element.
    """
    root = _new_node_element(uri, xsi_type="vos:ContainerNode")
    ET.SubElement(root, _NODES_TAG)
    return _serialize(root)


def build_transfer_document(
    target_uri: str,
    *,
    direction: str,
    protocols: list[str],
) -> bytes:
    """Build a synchronous ``transfer`` negotiation body.

    Args:
        target_uri: The authority-qualified target node URI.
        direction: Either ``"pullFromVoSpace"`` or ``"pushToVoSpace"``.
        protocols: The candidate transfer protocol URIs.

    Returns:
        A UTF-8 ``transfer`` document with one ``<target>`` and one
        ``<protocol>`` per supplied URI.

    Raises:
        ValueError: If ``direction`` is not a supported transfer direction.
    """
    if direction not in _ALLOWED_DIRECTIONS:
        msg = f"direction must be one of {_ALLOWED_DIRECTIONS}"
        raise ValueError(msg)
    root = ET.Element(_TRANSFER_TAG)
    root.set("version", VOSPACE_VERSION)
    ET.SubElement(root, _TARGET_TAG).text = target_uri
    ET.SubElement(root, _DIRECTION_TAG).text = direction
    for protocol in protocols:
        ET.SubElement(root, _PROTOCOL_TAG).set("uri", protocol)
    return _serialize(root)


def build_move_document(source_uri: str, destination_uri: str) -> bytes:
    """Build an internal move with a destination URI as its transfer direction."""
    root = ET.Element(_TRANSFER_TAG, version=VOSPACE_VERSION)
    ET.SubElement(root, _TARGET_TAG).text = source_uri
    ET.SubElement(root, _DIRECTION_TAG).text = destination_uri
    ET.SubElement(root, f"{{{VOSPACE_NS}}}keepBytes").text = "false"
    return _serialize(root)


def _node_from_element(element: ET.Element) -> Node:
    """Build a :class:`Node` from a ``<node>`` element.

    Args:
        element: A VOSpace ``node`` element.

    Returns:
        The parsed node.

    Raises:
        ValueError: If the element is not a ``node``, lacks a ``uri``, declares
            an unknown node type, or a data node has a non-integer length.
    """
    if element.tag != _NODE_TAG:
        msg = f"expected a VOSpace node element, got {element.tag!r}"
        raise ValueError(msg)
    node_type = _node_type_of(element)
    uri = element.get("uri")
    if uri is None:
        msg = "node element is missing a uri attribute"
        raise ValueError(msg)
    properties = _parse_properties(element)
    target = _parse_target(element) if node_type == "link" else None
    size = _parse_size(properties) if node_type == "data" else 0
    return Node(
        node_type=node_type,
        uri=uri,
        size=size,
        mtime=properties.get(MTIME_PROPERTY_URI) or properties.get(DATE_PROPERTY_URI),
        md5=properties.get(MD5_PROPERTY_URI),
        content_type=properties.get(CONTENT_TYPE_PROPERTY_URI),
        target=target,
        properties=MappingProxyType(properties),
    )


def _node_type_of(element: ET.Element) -> str:
    """Return the coarse fsspec node type from ``xsi:type``.

    The ``xsi:type`` value is a namespace-prefixed QName such as
    ``vos:ContainerNode``; the kind is taken from the local name after the
    final colon.

    The QName prefix is trusted rather than namespace-resolved: stdlib
    :mod:`xml.etree.ElementTree` discards the prefix-to-namespace bindings after
    parsing and does not resolve prefixes inside attribute *values*, so a
    hypothetical ``other:ContainerNode`` bound to a non-VOSpace namespace would
    classify by its local name. This leniency is acceptable for the trusted
    OpenCADC profile, which only ever emits the ``vos:`` prefix bound to
    :data:`VOSPACE_NS`; resolving it would require a non-stdlib parser.

    Args:
        element: A VOSpace ``node`` element.

    Returns:
        The coarse fsspec node type.

    Raises:
        ValueError: If the element carries no ``xsi:type`` or an unknown one.
    """
    xsi_type = element.get(_XSI_TYPE_ATTR)
    if xsi_type is None:
        msg = "node element is missing an xsi:type attribute"
        raise ValueError(msg)
    local_name = xsi_type.rsplit(":", 1)[-1]
    try:
        return _NODE_TYPE_BY_XSI_LOCAL[local_name]
    except KeyError:
        msg = f"unknown node type {xsi_type!r}"
        raise ValueError(msg) from None


def _parse_properties(element: ET.Element) -> dict[str, str]:
    """Collect every URI-keyed property of a node element.

    Args:
        element: A VOSpace ``node`` element.

    Returns:
        A mapping of property URI to value; nil or empty properties yield an
        empty-string value and properties without a ``uri`` are ignored.
    """
    properties: dict[str, str] = {}
    container = element.find(_PROPERTIES_TAG)
    if container is None:
        return properties
    for entry in container.findall(_PROPERTY_TAG):
        property_uri = entry.get("uri")
        if property_uri is None:
            continue
        properties[property_uri] = entry.text or ""
    return properties


def _parse_target(element: ET.Element) -> str:
    """Return a link node's target URI.

    Args:
        element: A ``LinkNode`` element.

    Returns:
        The stripped ``<target>`` text.

    Raises:
        ValueError: If the target element is absent or empty.
    """
    target = element.find(_TARGET_TAG)
    text = (target.text or "").strip() if target is not None else ""
    if not text:
        msg = "link node is missing a target"
        raise ValueError(msg)
    return text


def _parse_size(properties: Mapping[str, str]) -> int:
    """Return the integer byte length from a data node's properties.

    Args:
        properties: The node's parsed properties.

    Returns:
        The parsed ``length`` property, or ``0`` when it is absent.

    Raises:
        ValueError: If the ``length`` property is present but not an integer.
    """
    raw = properties.get(LENGTH_PROPERTY_URI)
    if raw is None:
        return 0
    try:
        return int(raw)
    except ValueError as exc:
        msg = f"length property is not an integer: {raw!r}"
        raise ValueError(msg) from exc


def _new_node_element(uri: str, *, xsi_type: str | None = None) -> ET.Element:
    """Create a ``<node>`` root element with the VOSpace version attribute.

    Args:
        uri: The node identifier URI.
        xsi_type: An optional ``xsi:type`` QName (for example
            ``"vos:ContainerNode"``); omitted for a bare base node.

    Returns:
        The new root element.
    """
    root = ET.Element(_NODE_TAG)
    if xsi_type is not None:
        root.set(_XSI_TYPE_ATTR, xsi_type)
    root.set("uri", uri)
    root.set("version", VOSPACE_VERSION)
    return root


def _serialize(root: ET.Element) -> bytes:
    """Serialize a document root to a UTF-8 XML byte string."""
    return ET.tostring(root, encoding="utf-8", xml_declaration=True)
