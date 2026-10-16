"""Tests for node metadata, listing, and derived predicates (sections 6.1, 11)."""

import httpx
import pytest
import respx
from conftest import AUTHORITY, NODES_URL, make_fs, mock_capabilities

NS = 'xmlns:vos="http://www.ivoa.net/xml/VOSpace/v2.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"'


def _data_node(path: str, *, length: int = 123) -> bytes:
    return f"""<vos:node {NS} xsi:type="vos:DataNode" uri="vos://{AUTHORITY}/{path}">
      <vos:properties>
        <vos:property uri="ivo://ivoa.net/vospace/core#length">{length}</vos:property>
        <vos:property uri="ivo://ivoa.net/vospace/core#date">2024-01-02T03:04:05.000</vos:property>
        <vos:property uri="ivo://ivoa.net/vospace/core#MD5">d41d8cd9</vos:property>
      </vos:properties>
    </vos:node>""".encode()


def _container(uri_path: str, children: str = "") -> bytes:
    suffix = f"/{uri_path}" if uri_path else ""
    return f"""<vos:node {NS} xsi:type="vos:ContainerNode" uri="vos://{AUTHORITY}{suffix}">
      <vos:properties/>
      <vos:nodes>{children}</vos:nodes>
    </vos:node>""".encode()


ROOT_LISTING = _container(
    "",
    children=f"""
      <vos:node xsi:type="vos:ContainerNode" uri="vos://{AUTHORITY}/dir"><vos:properties/></vos:node>
      <vos:node xsi:type="vos:DataNode" uri="vos://{AUTHORITY}/file.txt">
        <vos:properties>
          <vos:property uri="ivo://ivoa.net/vospace/core#length">10</vos:property>
        </vos:properties>
      </vos:node>""",
)


async def test_info_of_data_node(router: respx.Router) -> None:
    mock_capabilities(router)
    router.get(f"{NODES_URL}/file.txt").mock(
        return_value=httpx.Response(200, content=_data_node("file.txt", length=123)),
    )
    fs = make_fs(router, asynchronous=True)
    info = await fs._info("vos://file.txt")
    assert info["name"] == "/file.txt"
    assert info["type"] == "file"
    assert info["size"] == 123
    assert info["uri"] == f"vos://{AUTHORITY}/file.txt"
    assert info["mtime"] == "2024-01-02T03:04:05.000"
    assert info["md5"] == "d41d8cd9"
    # The read-only properties mapping preserves every URI-keyed property.
    assert info["properties"]["ivo://ivoa.net/vospace/core#length"] == "123"
    with pytest.raises(TypeError):
        info["properties"]["x"] = "y"
    await fs.aclose()


async def test_info_missing_raises_file_not_found(router: respx.Router) -> None:
    mock_capabilities(router)
    router.get(f"{NODES_URL}/gone").mock(return_value=httpx.Response(404, text="not found"))
    fs = make_fs(router, asynchronous=True)
    with pytest.raises(FileNotFoundError):
        await fs._info("/gone")
    await fs.aclose()


async def test_ls_lists_immediate_children(router: respx.Router) -> None:
    mock_capabilities(router)
    router.get(NODES_URL).mock(return_value=httpx.Response(200, content=ROOT_LISTING))
    fs = make_fs(router, asynchronous=True)
    entries = await fs._ls("/", detail=True)
    names = sorted(entry["name"] for entry in entries)
    assert names == ["/dir", "/file.txt"]
    types = {entry["name"]: entry["type"] for entry in entries}
    assert types == {"/dir": "directory", "/file.txt": "file"}
    await fs.aclose()


async def test_ls_path_only(router: respx.Router) -> None:
    mock_capabilities(router)
    router.get(NODES_URL).mock(return_value=httpx.Response(200, content=ROOT_LISTING))
    fs = make_fs(router, asynchronous=True)
    assert sorted(await fs._ls("/", detail=False)) == ["/dir", "/file.txt"]
    await fs.aclose()


async def test_ls_on_a_file_returns_the_file(router: respx.Router) -> None:
    mock_capabilities(router)
    router.get(f"{NODES_URL}/file.txt").mock(
        return_value=httpx.Response(200, content=_data_node("file.txt")),
    )
    fs = make_fs(router, asynchronous=True)
    entries = await fs._ls("/file.txt", detail=True)
    assert [entry["name"] for entry in entries] == ["/file.txt"]
    await fs.aclose()


async def test_derived_predicates(router: respx.Router) -> None:
    mock_capabilities(router)
    router.get(f"{NODES_URL}/file.txt").mock(
        return_value=httpx.Response(200, content=_data_node("file.txt", length=7)),
    )
    fs = make_fs(router, asynchronous=True)
    assert await fs._exists("/file.txt") is True
    assert await fs._isfile("/file.txt") is True
    assert await fs._isdir("/file.txt") is False
    assert await fs._size("/file.txt") == 7
    await fs.aclose()


async def test_modified_returns_datetime(router: respx.Router) -> None:
    mock_capabilities(router)
    router.get(f"{NODES_URL}/file.txt").mock(
        return_value=httpx.Response(200, content=_data_node("file.txt")),
    )
    fs = make_fs(router, asynchronous=True)
    modified = await fs._modified("/file.txt")
    assert modified.year == 2024
    assert modified.month == 1
    await fs.aclose()


async def test_modified_without_date_raises(router: respx.Router) -> None:
    mock_capabilities(router)
    no_date = f"""<vos:node {NS} xsi:type="vos:DataNode" uri="vos://{AUTHORITY}/f">
      <vos:properties>
        <vos:property uri="ivo://ivoa.net/vospace/core#length">1</vos:property>
      </vos:properties>
    </vos:node>""".encode()
    router.get(f"{NODES_URL}/f").mock(return_value=httpx.Response(200, content=no_date))
    fs = make_fs(router, asynchronous=True)
    with pytest.raises(OSError, match="modification date"):
        await fs._modified("/f")
    await fs.aclose()


async def test_authority_mismatch_raises(router: respx.Router) -> None:
    mock_capabilities(router)
    router.get(NODES_URL).mock(return_value=httpx.Response(200, content=_container("")))
    other = f"""<vos:node {NS} xsi:type="vos:DataNode" uri="vos://other.test!vault/x">
      <vos:properties>
        <vos:property uri="ivo://ivoa.net/vospace/core#length">1</vos:property>
      </vos:properties>
    </vos:node>""".encode()
    router.get(f"{NODES_URL}/x").mock(return_value=httpx.Response(200, content=other))
    fs = make_fs(router, asynchronous=True)
    await fs._info("/")  # records the authority
    with pytest.raises(OSError, match="authority"):
        await fs._info("/x")
    await fs.aclose()


def test_created_raises_not_implemented(router: respx.Router) -> None:
    mock_capabilities(router)
    fs = make_fs(router)
    with pytest.raises(NotImplementedError):
        fs.created("/file.txt")
    fs.close()


def test_info_preserves_creator_and_distinct_access_groups(router) -> None:
    from conftest import NODES_URL, make_fs, mock_capabilities

    mock_capabilities(router)
    router.get(f"{NODES_URL}/f").mock(
        return_value=httpx.Response(
            200,
            content=b"""
<vos:node xmlns:vos="http://www.ivoa.net/xml/VOSpace/v2.0"
 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 xsi:type="vos:DataNode" uri="vos://example.test!vault/f">
 <vos:properties>
  <vos:property uri="ivo://ivoa.net/vospace/core#creator">CN=jkavelaars,OU=cadc</vos:property>
  <vos:property uri="ivo://ivoa.net/vospace/core#groupread">ivo://cadc.nrc.ca/gms?OSSOS ivo://cadc.nrc.ca/gms#collab</vos:property>
  <vos:property uri="ivo://ivoa.net/vospace/core#groupwrite">NONE</vos:property>
  <vos:property uri="ivo://ivoa.net/vospace/core#ispublic">true</vos:property>
  <vos:property uri="ivo://cadc.nrc.ca/vospace/core#islocked">false</vos:property>
 </vos:properties>
</vos:node>""",
        )
    )
    fs = make_fs(router)
    try:
        info = fs.info("/f")
        assert (
            info["owner"],
            info["read_groups"],
            info["write_groups"],
            info["permissions"],
            info["locked"],
        ) == ("jkavelaars", ("OSSOS", "collab"), (), "-rw-r--r--", False)
    finally:
        fs.close()
