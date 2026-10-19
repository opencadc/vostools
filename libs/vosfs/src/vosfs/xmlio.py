"""Shared safe XML parsing for VOSpace response documents.

Every VOSpace document (nodes, listings, capabilities, transfer details) is
parsed through :func:`safe_parse`. The body is bounded before parsing. A
document type declaration is rejected as soon as the parser sees it, before
any entity is read, so a hostile response cannot expand an entity bomb or
pull in an external DTD. CPython's bundled expat (2.4.1 or newer, which
``requires-python`` already implies) is the second layer: it refuses entity
amplification and does not fetch external entities.
"""

from __future__ import annotations

import xml.etree.ElementTree as ET
from xml.etree.ElementTree import ParseError

#: Default 8 MiB ceiling applied to a response body before it is parsed.
DEFAULT_LIMIT = 8_388_608


class _NoDTDBuilder(ET.TreeBuilder):
    """Reject a ``DOCTYPE`` before the parser reads its entities."""

    def doctype(self, name: str, pubid: str, system: str) -> None:
        del name, pubid, system
        msg = "XML document type declarations are forbidden"
        raise ValueError(msg)


def safe_parse(data: bytes, *, limit: int = DEFAULT_LIMIT) -> ET.Element:
    """Bound and parse ``data``, returning the document root.

    Args:
        data: The raw XML body.
        limit: Maximum accepted size in bytes, enforced before parsing.

    Returns:
        The parsed root element.

    Raises:
        ValueError: If the body exceeds ``limit``, declares a document type,
            or is not well-formed XML.
    """
    if len(data) > limit:
        msg = f"XML body exceeds {limit} byte limit"
        raise ValueError(msg)
    parser = ET.XMLParser(target=_NoDTDBuilder())  # noqa: S314 - DOCTYPE is rejected before entities are read
    try:
        parser.feed(data)
        root = parser.close()
    except ParseError as exc:
        msg = "malformed XML document"
        raise ValueError(msg) from exc
    return root


def local_name(tag: str) -> str:
    """Return the local name of a possibly namespace-qualified element tag."""
    return tag.rsplit("}", 1)[-1]
