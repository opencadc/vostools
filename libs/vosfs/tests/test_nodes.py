"""Tests for the VOSpace 2.1 XML node model (contract section 6).

Generated documents and authored XML fixtures are validated against the pinned
VOSpace 2.1 schema via lxml. The runtime module itself performs no XSD
validation; only these tests do.
"""

from __future__ import annotations

from pathlib import Path

import pytest
from lxml import etree

from vosfs.nodes import (
    CONTENT_TYPE_PROPERTY_URI,
    DATE_PROPERTY_URI,
    LENGTH_PROPERTY_URI,
    MTIME_PROPERTY_URI,
    VOSPACE_NS,
    VOSPACE_VERSION,
    XML_HEADERS,
    Node,
    build_container_document,
    build_transfer_document,
    parse_container,
    parse_node,
    to_info,
)
from vosfs.xmlio import safe_parse

FIXTURE_DIR = Path(__file__).parent / "fixtures" / "nodes"
SCHEMA_DIR = Path(__file__).parent / "fixtures" / "schema"

# The wrapper schema xs:includes the pinned VOSpace-2.1.xsd verbatim, so it
# validates both node documents (via the added global element) and transfer
# documents (via the schema's own global element).
_SCHEMA = etree.XMLSchema(etree.parse(str(SCHEMA_DIR / "vospace-2.1-node-element.xsd")))


def assert_schema_valid(document: bytes) -> None:
    """Assert an XML document validates against the pinned VOSpace 2.1 schema."""
    tree = etree.fromstring(document)
    assert _SCHEMA.validate(tree), str(_SCHEMA.error_log)


def read_fixture(name: str) -> bytes:
    return (FIXTURE_DIR / name).read_bytes()


# --------------------------------------------------------------------------- #
# Constants
# --------------------------------------------------------------------------- #


def test_constants() -> None:
    assert VOSPACE_NS == "http://www.ivoa.net/xml/VOSpace/v2.0"
    assert VOSPACE_VERSION == "2.1"
    assert XML_HEADERS == {
        "Content-Type": "text/xml; charset=utf-8",
        "Accept": "text/xml",
    }


# --------------------------------------------------------------------------- #
# Authored fixtures validate against the pinned schema
# --------------------------------------------------------------------------- #


@pytest.mark.parametrize(
    "name",
    ["data_node.xml", "link_node.xml", "container_listing.xml"],
)
def test_fixtures_validate_against_schema(name: str) -> None:
    assert_schema_valid(read_fixture(name))


# --------------------------------------------------------------------------- #
# parse_node: each node kind
# --------------------------------------------------------------------------- #


def test_parse_data_node() -> None:
    node = parse_node(read_fixture("data_node.xml"))
    assert node.node_type == "data"
    assert node.uri == "vos://cadc.nrc.ca!vault/user/report.fits"
    assert node.size == 10485760
    assert node.md5 == "d41d8cd98f00b204e9800998ecf8427e"
    assert node.mtime == "2024-05-01T12:34:56.000"
    assert node.content_type == "application/fits"
    assert node.target is None
    # Every URI-keyed property is preserved, including unknown and admin ones.
    assert node.properties[LENGTH_PROPERTY_URI] == "10485760"
    custom = node.properties["ivo://cadc.nrc.ca/vospace/custom#experiment"]
    assert custom == "orion-survey"
    assert node.properties["ivo://ivoa.net/vospace/core#quota"] == "53687091200"


def test_mtime_prefers_ivoa_mtime_over_date() -> None:
    def prop(uri: str, value: str) -> str:
        return f'<vos:property uri="{uri}">{value}</vos:property>'

    document = (
        f'<vos:node xmlns:vos="{VOSPACE_NS}" '
        'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
        'xsi:type="vos:DataNode" uri="vos://cadc.nrc.ca!vault/a" version="2.1">'
        "<vos:properties>"
        + prop(MTIME_PROPERTY_URI, "2025-01-02T03:04:05.000")
        + prop(DATE_PROPERTY_URI, "1999-01-01T00:00:00.000")
        + prop(LENGTH_PROPERTY_URI, "10")
        + "</vos:properties></vos:node>"
    ).encode()
    node = parse_node(document)
    assert node.mtime == "2025-01-02T03:04:05.000"


def test_mtime_falls_back_to_date_when_ivoa_mtime_absent() -> None:
    # The bundled container fixture carries only #date, exercising the fallback.
    node = parse_node(read_fixture("container_listing.xml"))
    assert node.mtime == "2024-05-01T00:00:00.000"


def test_parse_link_node() -> None:
    node = parse_node(read_fixture("link_node.xml"))
    assert node.node_type == "link"
    assert node.target == "vos://cadc.nrc.ca!vault/user/report.fits"
    assert node.size == 0


def test_parse_container_node() -> None:
    node = parse_node(read_fixture("container_listing.xml"))
    assert node.node_type == "container"
    assert node.size == 0
    assert node.mtime == "2024-05-01T00:00:00.000"


@pytest.mark.parametrize("local_name", ["StructuredDataNode", "UnstructuredDataNode"])
def test_structured_and_unstructured_are_opaque_files(local_name: str) -> None:
    document = (
        f'<vos:node xmlns:vos="{VOSPACE_NS}" '
        'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
        f'xsi:type="vos:{local_name}" uri="vos://x/f" version="2.1">'
        f'<vos:properties><vos:property uri="{LENGTH_PROPERTY_URI}">7</vos:property>'
        "</vos:properties></vos:node>"
    ).encode()
    node = parse_node(document)
    assert node.node_type == "data"
    assert node.size == 7
    assert to_info(node, "/x/f")["type"] == "file"


def test_parse_data_node_without_length_defaults_to_zero() -> None:
    document = (
        f'<vos:node xmlns:vos="{VOSPACE_NS}" '
        'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
        'xsi:type="vos:DataNode" uri="vos://x/f" version="2.1"/>'
    ).encode()
    node = parse_node(document)
    assert node.size == 0
    assert node.mtime is None
    assert dict(node.properties) == {}


def test_property_without_uri_is_ignored_and_nil_becomes_empty() -> None:
    document = (
        f'<vos:node xmlns:vos="{VOSPACE_NS}" '
        'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
        'xsi:type="vos:DataNode" uri="vos://x/f" version="2.1"><vos:properties>'
        "<vos:property>orphan</vos:property>"
        f'<vos:property uri="{CONTENT_TYPE_PROPERTY_URI}"></vos:property>'
        "</vos:properties></vos:node>"
    ).encode()
    node = parse_node(document)
    assert node.content_type == ""
    assert list(node.properties) == [CONTENT_TYPE_PROPERTY_URI]


def test_parsed_properties_are_read_only() -> None:
    node = parse_node(read_fixture("data_node.xml"))
    with pytest.raises(TypeError):
        node.properties["x"] = "y"  # type: ignore[index]


# --------------------------------------------------------------------------- #
# parse_node: rejection paths
# --------------------------------------------------------------------------- #


def test_oversized_body_is_rejected() -> None:
    # ``parse_node``/``parse_container`` route through this bounded parser.
    with pytest.raises(ValueError, match="limit"):
        safe_parse(b"<vos:node/>" * 10, limit=8)


def test_malformed_xml_is_rejected() -> None:
    with pytest.raises(ValueError, match="malformed"):
        parse_node(b"<vos:node this is not xml")


def test_external_entity_is_rejected() -> None:
    payload = (
        b'<?xml version="1.0"?>'
        b'<!DOCTYPE node [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>'
        b'<vos:node xmlns:vos="' + VOSPACE_NS.encode() + b'" uri="vos://x">&xxe;</vos:node>'
    )
    with pytest.raises(ValueError, match="document type"):
        parse_node(payload)


def test_internal_dtd_is_rejected() -> None:
    payload = b'<?xml version="1.0"?><!DOCTYPE node [<!ENTITY a "x">]><node>&a;</node>'
    with pytest.raises(ValueError, match="document type"):
        safe_parse(payload)


def test_external_system_dtd_is_rejected() -> None:
    payload = b'<?xml version="1.0"?><!DOCTYPE node SYSTEM "http://example.invalid/x.dtd"><node/>'
    with pytest.raises(ValueError, match="document type"):
        safe_parse(payload)


def test_entity_bomb_is_rejected() -> None:
    payload = (
        b'<?xml version="1.0"?>'
        b'<!DOCTYPE l [<!ENTITY a "aaaaaaaaaa">'
        b'<!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;">'
        b'<!ENTITY c "&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;">'
        b'<!ENTITY d "&c;&c;&c;&c;&c;&c;&c;&c;&c;&c;">'
        b'<!ENTITY e "&d;&d;&d;&d;&d;&d;&d;&d;&d;&d;">'
        b'<!ENTITY f "&e;&e;&e;&e;&e;&e;&e;&e;&e;&e;">'
        b'<!ENTITY g "&f;&f;&f;&f;&f;&f;&f;&f;&f;&f;">'
        b'<!ENTITY h "&g;&g;&g;&g;&g;&g;&g;&g;&g;&g;">]>'
        b"<l>&h;</l>"
    )
    with pytest.raises(ValueError, match="document type"):
        safe_parse(payload)


def test_cdata_may_contain_a_doctype_string() -> None:
    root = safe_parse(b"<l><![CDATA[<!DOCTYPE x>]]></l>")
    assert root.text == "<!DOCTYPE x>"


def test_missing_xsi_type_is_rejected() -> None:
    document = f'<vos:node xmlns:vos="{VOSPACE_NS}" uri="vos://x"/>'.encode()
    with pytest.raises(ValueError, match="xsi:type"):
        parse_node(document)


def test_unknown_node_type_is_rejected() -> None:
    document = (
        f'<vos:node xmlns:vos="{VOSPACE_NS}" '
        'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
        'xsi:type="vos:MysteryNode" uri="vos://x"/>'
    ).encode()
    with pytest.raises(ValueError, match="unknown node type"):
        parse_node(document)


def test_non_node_root_is_rejected() -> None:
    document = f'<vos:transfer xmlns:vos="{VOSPACE_NS}"/>'.encode()
    with pytest.raises(ValueError, match="node element"):
        parse_node(document)


def test_missing_uri_is_rejected() -> None:
    document = (
        f'<vos:node xmlns:vos="{VOSPACE_NS}" '
        'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
        'xsi:type="vos:DataNode"/>'
    ).encode()
    with pytest.raises(ValueError, match="uri"):
        parse_node(document)


def test_non_integer_length_is_rejected() -> None:
    document = (
        f'<vos:node xmlns:vos="{VOSPACE_NS}" '
        'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
        'xsi:type="vos:DataNode" uri="vos://x/f"><vos:properties>'
        f'<vos:property uri="{LENGTH_PROPERTY_URI}">not-a-number</vos:property>'
        "</vos:properties></vos:node>"
    ).encode()
    with pytest.raises(ValueError, match="length"):
        parse_node(document)


def test_link_node_missing_target_is_rejected() -> None:
    document = (
        f'<vos:node xmlns:vos="{VOSPACE_NS}" '
        'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
        'xsi:type="vos:LinkNode" uri="vos://x/l"/>'
    ).encode()
    with pytest.raises(ValueError, match="target"):
        parse_node(document)


# --------------------------------------------------------------------------- #
# parse_container
# --------------------------------------------------------------------------- #


def test_parse_container_returns_node_and_immediate_children() -> None:
    node, children = parse_container(read_fixture("container_listing.xml"))
    assert node.node_type == "container"
    kinds = [child.node_type for child in children]
    assert kinds == ["data", "data", "data", "container", "link"]
    assert children[0].size == 10485760
    assert children[-1].target == "vos://cadc.nrc.ca!vault/user/report.fits"


def test_parse_container_without_nodes_element_is_empty() -> None:
    document = (
        f'<vos:node xmlns:vos="{VOSPACE_NS}" '
        'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
        'xsi:type="vos:DataNode" uri="vos://x/f"/>'
    ).encode()
    node, children = parse_container(document)
    assert node.node_type == "data"
    assert children == []


# --------------------------------------------------------------------------- #
# to_info
# --------------------------------------------------------------------------- #


def test_to_info_for_data_node() -> None:
    node = parse_node(read_fixture("data_node.xml"))
    info = to_info(node, "/user/report.fits")
    assert info["name"] == "/user/report.fits"
    assert info["type"] == "file"
    assert info["size"] == 10485760
    assert info["uri"] == "vos://cadc.nrc.ca!vault/user/report.fits"
    assert info["mtime"] == "2024-05-01T12:34:56.000"
    assert info["md5"] == "d41d8cd98f00b204e9800998ecf8427e"
    assert info["content_type"] == "application/fits"
    assert info["properties"][LENGTH_PROPERTY_URI] == "10485760"
    assert "islink" not in info


def test_to_info_for_minimal_data_node_omits_optional_fields() -> None:
    node = Node(
        node_type="data",
        uri="vos://x/f",
        size=3,
        mtime=None,
        md5=None,
        content_type=None,
        target=None,
        properties={},
    )
    info = to_info(node, "/f")
    assert info == {"name": "/f", "type": "file", "size": 3, "uri": "vos://x/f"}


def test_to_info_for_container() -> None:
    node = parse_node(read_fixture("container_listing.xml"))
    info = to_info(node, "/user")
    assert info["type"] == "directory"
    assert info["size"] == 0
    assert info["mtime"] == "2024-05-01T00:00:00.000"
    assert "properties" in info
    assert "md5" not in info
    assert "content_type" not in info


def test_to_info_for_minimal_container_omits_optional_fields() -> None:
    node = parse_node(build_container_document("vos://x/dir"))
    info = to_info(node, "/dir")
    assert info == {
        "name": "/dir",
        "type": "directory",
        "size": 0,
        "uri": "vos://x/dir",
    }


def test_to_info_for_link_node() -> None:
    node = parse_node(read_fixture("link_node.xml"))
    info = to_info(node, "/user/shortcut")
    assert info["type"] == "other"
    assert info["size"] == 0
    assert info["islink"] is True
    assert info["target"] == "vos://cadc.nrc.ca!vault/user/report.fits"
    assert info["uri"] == "vos://cadc.nrc.ca!vault/user/shortcut"
    # LinkNode properties are not promised.
    assert "properties" not in info
    assert "mtime" not in info


# --------------------------------------------------------------------------- #
# build_container_document
# --------------------------------------------------------------------------- #


def test_build_container_document_is_valid_bytes() -> None:
    document = build_container_document("vos://cadc.nrc.ca!vault/user/new")
    assert isinstance(document, bytes)
    assert_schema_valid(document)
    tree = etree.fromstring(document)
    assert tree.get("version") == "2.1"
    assert tree.tag == f"{{{VOSPACE_NS}}}node"
    assert tree.get("uri") == "vos://cadc.nrc.ca!vault/user/new"
    assert tree.find(f"{{{VOSPACE_NS}}}nodes") is not None


# --------------------------------------------------------------------------- #
# build_transfer_document
# --------------------------------------------------------------------------- #


@pytest.mark.parametrize("direction", ["pullFromVoSpace", "pushToVoSpace"])
def test_build_transfer_document_is_valid(direction: str) -> None:
    document = build_transfer_document(
        "vos://cadc.nrc.ca!vault/user/report.fits",
        direction=direction,
        protocols=[
            "ivo://ivoa.net/vospace/core#httpget",
            "ivo://ivoa.net/vospace/core#httpsget",
        ],
    )
    assert isinstance(document, bytes)
    assert_schema_valid(document)
    tree = etree.fromstring(document)
    assert tree.tag == f"{{{VOSPACE_NS}}}transfer"
    assert tree.get("version") == "2.1"
    assert tree.findtext(f"{{{VOSPACE_NS}}}direction") == direction
    assert tree.findtext(f"{{{VOSPACE_NS}}}target") == ("vos://cadc.nrc.ca!vault/user/report.fits")
    protocols = tree.findall(f"{{{VOSPACE_NS}}}protocol")
    assert len(protocols) == 2


def test_build_transfer_document_rejects_bad_direction() -> None:
    with pytest.raises(ValueError, match="direction"):
        build_transfer_document("vos://x", direction="sideways", protocols=[])
