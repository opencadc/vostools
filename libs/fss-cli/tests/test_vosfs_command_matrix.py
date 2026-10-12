"""Hermetic compatibility evidence for the native vosfs source form."""

import inspect
import re
import time
from urllib.parse import quote, unquote

import httpx
import pytest
from vosfs import VOSpaceFileSystem

from fss_cli import App

from ._matrix_support import (
    _exercise_cat_profile,
    _exercise_cp_locked_profile,
    _exercise_locked_profile,
    _exercise_long_listing_profile,
    _exercise_mkdir_p_locked_profile,
    _exercise_rm_force_profile,
    _exercise_rm_locked_profile,
    _exercise_rm_verbose_profile,
    _exercise_rmdir_locked_profile,
    _exercise_stat_incomplete_profile,
    _exercise_unlink_locked_profile,
    _invoke,
)
from ._matrix_support import _exercise_mkdir_locked_profile as _exercise_mkdir_profile
from ._vosfs_matrix_support import (
    _AUTHORITY,
    _BASE_URL,
    _CAPABILITIES,
    _SYNC_CAPABILITIES,
    _StrictMockTransport,
    _vos_child,
    _vos_container,
    _vos_data,
    _vosfs_source,
)

_BLOB_PAYLOAD = b"vos-cat\0\xff\xfe"
_DOCS = _vos_container(
    "/docs",
    _vos_child("DataNode", "/docs/notes.txt")
    + _vos_child("DataNode", "/docs/.hidden")
    + _vos_child("DataNode", "/docs/guide.md"),
)
_LONG_DOCS = _vos_container(
    "/docs",
    _vos_child("DataNode", "/docs/notes.txt", length=1536, mtime="2026-07-17T18:00:00Z")
    + _vos_child("DataNode", "/docs/.hidden", length=7)
    + _vos_child("DataNode", "/docs/guide.md", length=8, mtime="2026-07-17T18:00:00Z")
    + _vos_child("LinkNode", "/docs/shortcut", target="/docs/guide.md"),
)
_BLOB = _vos_data("/docs/blob.bin", length=len(_BLOB_PAYLOAD))
_SUBDIR = _vos_container("/docs/subdir")
_ROOT = _vos_container("/")
_DOCS_WITH_EMPTY = _vos_container(
    "/docs",
    _vos_child("DataNode", "/docs/notes.txt")
    + _vos_child("DataNode", "/docs/.hidden")
    + _vos_child("DataNode", "/docs/guide.md")
    + _vos_child("ContainerNode", "/docs/empty"),
)
_EMPTY = _vos_container("/docs/empty")
_NOTES = _vos_data("/docs/notes.txt")


_RESPONSES: dict[tuple[str, str], httpx.Response] = {
    ("GET", "/arc/capabilities"): httpx.Response(200, content=_CAPABILITIES),
    ("GET", "/arc/nodes"): httpx.Response(200, content=_ROOT),
    ("GET", "/arc/nodes/docs"): httpx.Response(200, content=_DOCS),
    ("GET", "/arc/nodes/docs/missing"): httpx.Response(404, text="not found"),
    ("PUT", "/arc/nodes/docs/subdir"): httpx.Response(201, content=_SUBDIR),
    ("GET", "/arc/nodes/docs/subdir"): httpx.Response(200, content=_SUBDIR),
    ("PUT", "/arc/nodes/docs/notes.txt"): httpx.Response(409, text="conflict"),
    ("PUT", "/arc/nodes/docs/notes.txt/child"): httpx.Response(404, text="not found"),
    ("PUT", "/arc/nodes/docs/absent/child"): httpx.Response(404, text="not found"),
}
_LONG_RESPONSES: dict[tuple[str, str], httpx.Response] = {
    ("GET", "/arc/capabilities"): httpx.Response(200, content=_CAPABILITIES),
    ("GET", "/arc/nodes/docs"): httpx.Response(200, content=_LONG_DOCS),
}


def _target_path(content: bytes | None) -> str:
    match = re.search(r"<[^>]*target[^>]*>([^<]+)</", (content or b"").decode())
    if match is None:
        return "/"
    prefix = f"vos://{_AUTHORITY}"
    return match.group(1).strip()[len(prefix) :] or "/"


def _transfer_details(endpoint: str) -> bytes:
    return (
        f'<vos:transfer xmlns:vos="http://www.ivoa.net/xml/VOSpace/v2.0" version="2.1">'
        f'<vos:protocol uri="ivo://ivoa.net/vospace/core#httpsget">'
        f"<vos:endpoint>{endpoint}</vos:endpoint></vos:protocol></vos:transfer>"
    ).encode()


class _CatMockTransport(httpx.MockTransport):
    def __init__(self, payload: bytes) -> None:
        self.requests: list[tuple[str, str]] = []
        self.closed = False
        self._payload = payload
        super().__init__(self._respond)

    def _file_response(self, path: str) -> httpx.Response:
        if path != "/docs/blob.bin":
            return httpx.Response(404, text="not found")

        async def _stream() -> object:
            yield self._payload

        return httpx.Response(200, content=_stream())

    async def _respond(self, request: httpx.Request) -> httpx.Response:
        if "range" in {name.lower() for name in request.headers}:
            message = f"unexpected Range header: {request.headers!r}"
            raise AssertionError(message)
        call = (request.method, request.url.path)
        self.requests.append(call)
        handlers = {
            ("GET", "/arc/capabilities"): lambda: httpx.Response(200, content=_SYNC_CAPABILITIES),
            ("GET", "/arc/nodes/docs/blob.bin"): lambda: httpx.Response(200, content=_BLOB),
            ("GET", "/arc/nodes/docs/blob.bin.missing"): lambda: httpx.Response(404, text="not found"),
            ("POST", "/arc/synctrans"): lambda: httpx.Response(
                303,
                headers={"Location": (f"{_BASE_URL}/details?t={quote(_target_path(request.content))}")},
            ),
            ("GET", "/arc/details"): lambda: httpx.Response(
                200,
                content=_transfer_details(f"{_BASE_URL}/files?p={quote(unquote(request.url.params['t']))}"),
            ),
            ("GET", "/arc/files"): lambda: self._file_response(unquote(request.url.params["p"])),
        }
        handler = handlers.get(call)
        if handler is None:
            message = f"unplanned mocked request: {call!r}"
            raise AssertionError(message)
        return handler()

    async def aclose(self) -> None:
        self.closed = True
        await super().aclose()


class _MakedirsMockTransport(httpx.MockTransport):
    def __init__(self) -> None:
        self.requests: list[tuple[str, str]] = []
        self.closed = False
        self.nodes: dict[str, str] = {
            "/": "container",
            "/docs": "container",
            "/docs/notes.txt": "data",
            "/docs/.hidden": "data",
            "/docs/guide.md": "data",
            "/docs/empty": "container",
        }
        super().__init__(self._respond)

    def _node_path(self, url_path: str) -> str:
        prefix = "/arc/nodes"
        if not url_path.startswith(prefix):
            message = f"unexpected node url: {url_path!r}"
            raise AssertionError(message)
        node_path = url_path[len(prefix) :]
        return node_path or "/"

    def _container_xml(self, node_path: str) -> bytes:
        return f"""<vos:node
    xmlns:vos="http://www.ivoa.net/xml/VOSpace/v2.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:type="vos:ContainerNode" uri="vos://{_AUTHORITY}{node_path}">
  <vos:properties/>
  <vos:nodes/>
</vos:node>
""".encode()

    def _data_xml(self, node_path: str) -> bytes:
        return f"""<vos:node
    xmlns:vos="http://www.ivoa.net/xml/VOSpace/v2.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:type="vos:DataNode" uri="vos://{_AUTHORITY}{node_path}">
  <vos:properties/>
</vos:node>
""".encode()

    async def _respond(self, request: httpx.Request) -> httpx.Response:  # noqa: PLR0911
        call = (request.method, request.url.path)
        self.requests.append(call)
        if call == ("GET", "/arc/capabilities"):
            return httpx.Response(200, content=_CAPABILITIES)
        if call == ("GET", "/arc/nodes"):
            return httpx.Response(200, content=_ROOT)
        if call[0] == "GET" and call[1].startswith("/arc/nodes"):
            node_path = self._node_path(call[1])
            if node_path not in self.nodes:
                return httpx.Response(404, text="not found")
            if self.nodes[node_path] == "container":
                return httpx.Response(200, content=self._container_xml(node_path))
            return httpx.Response(200, content=self._data_xml(node_path))
        if call[0] == "PUT" and call[1].startswith("/arc/nodes"):
            node_path = self._node_path(call[1])
            parent = node_path.rsplit("/", 1)[0] or "/"
            if parent not in self.nodes or self.nodes[parent] != "container":
                return httpx.Response(404, text="not found")
            if node_path in self.nodes and self.nodes[node_path] == "data":
                return httpx.Response(409, text="conflict")
            self.nodes[node_path] = "container"
            return httpx.Response(201, content=self._container_xml(node_path))
        message = f"unplanned mocked request: {call!r}"
        raise AssertionError(message)

    async def aclose(self) -> None:
        self.closed = True
        await super().aclose()


class _RmdirMockTransport(_StrictMockTransport):
    def __init__(self) -> None:
        self._empty_deleted = False
        super().__init__()

    async def _respond(self, request: httpx.Request) -> httpx.Response:
        call = (request.method, request.url.path)
        self.requests.append(call)
        if call == ("GET", "/arc/capabilities"):
            return httpx.Response(200, content=_CAPABILITIES)
        if call == ("GET", "/arc/nodes/docs"):
            return httpx.Response(200, content=_DOCS_WITH_EMPTY)
        if call == ("GET", "/arc/nodes/docs/notes.txt"):
            return httpx.Response(200, content=_NOTES)
        if call == ("GET", "/arc/nodes/docs/empty"):
            if self._empty_deleted:
                return httpx.Response(404, text="not found")
            return httpx.Response(200, content=_EMPTY)
        if call == ("DELETE", "/arc/nodes/docs/empty"):
            self._empty_deleted = True
            return httpx.Response(200, text="deleted")
        message = f"unplanned mocked request: {call!r}"
        raise AssertionError(message)


class _UnlinkMockTransport(httpx.MockTransport):
    def __init__(self) -> None:
        self.requests: list[tuple[str, str]] = []
        self.closed = False
        self.nodes = {
            "/docs": "container",
            "/docs/notes.txt": "data",
            "/docs/.hidden": "data",
            "/docs/guide.md": "data",
        }
        super().__init__(self._respond)

    def _node_path(self, url_path: str) -> str:
        prefix = "/arc/nodes"
        if not url_path.startswith(prefix):
            message = f"unexpected node url: {url_path!r}"
            raise AssertionError(message)
        node_path = url_path[len(prefix) :]
        return node_path or "/"

    async def _respond(self, request: httpx.Request) -> httpx.Response:
        call = (request.method, request.url.path)
        self.requests.append(call)
        if call == ("GET", "/arc/capabilities"):
            return httpx.Response(200, content=_CAPABILITIES)
        if call[0] == "GET" and call[1].startswith("/arc/nodes"):
            node_path = self._node_path(call[1])
            if node_path not in self.nodes:
                return httpx.Response(404, text="not found")
            if self.nodes[node_path] == "container":
                return httpx.Response(200, content=_DOCS)
            return httpx.Response(
                200,
                content=f"""<vos:node
    xmlns:vos="http://www.ivoa.net/xml/VOSpace/v2.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:type="vos:DataNode" uri="vos://{_AUTHORITY}{node_path}">
  <vos:properties/>
</vos:node>
""".encode(),
            )
        if call[0] == "DELETE" and call[1].startswith("/arc/nodes"):
            node_path = self._node_path(call[1])
            if node_path in self.nodes and self.nodes[node_path] == "data":
                del self.nodes[node_path]
                return httpx.Response(200)
            return httpx.Response(404, text="not found")
        message = f"unplanned mocked request: {call!r}"
        raise AssertionError(message)

    async def aclose(self) -> None:
        self.closed = True
        await super().aclose()


def test_native_vosfs_plain_ls_profile_uses_only_mocked_transport() -> None:
    source, transports = _vosfs_source(_RESPONSES)

    _exercise_locked_profile("vos", source, "/docs")

    assert all(isinstance(fs, VOSpaceFileSystem) for fs in source.filesystems)
    assert all(fs.asynchronous is True for fs in source.filesystems)
    assert all(fs._pool.closed is True for fs in source.filesystems)
    assert [call.source_id for call in source.close_calls] == [1, 2, 3]
    for close_call in source.close_calls:
        exit_call = next(call for call in source.exit_calls if call.source_id == close_call.source_id)
        assert close_call.loop_id == exit_call.loop_id
    assert [transport.requests for transport in transports] == [
        [
            ("GET", "/arc/capabilities"),
            ("GET", "/arc/nodes/docs"),
            ("GET", "/arc/nodes/docs"),
        ],
        [
            ("GET", "/arc/capabilities"),
            ("GET", "/arc/nodes/docs"),
            ("GET", "/arc/nodes/docs"),
        ],
        [
            ("GET", "/arc/capabilities"),
            ("GET", "/arc/nodes/docs/missing"),
        ],
    ]
    assert all(transport.closed for transport in transports)


def test_native_vosfs_long_listing_profile_is_remote_and_uses_detail(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(
        "fss_cli._listing.time.localtime",
        time.gmtime,
    )
    source, transports = _vosfs_source(_LONG_RESPONSES)

    _exercise_long_listing_profile(
        "vos",
        source,
        "/docs",
        exact_directory=(
            "-?????????  -  -  -     8  Jul 17 18:00  guide.md\n"
            "-?????????  -  -  -  1536  Jul 17 18:00  notes.txt\n"
            f"l?????????  -  -  -     0  -             shortcut -> vos://{_AUTHORITY}/docs/guide.md\n"
        ),
        human_directory=(
            "-?????????  -  -  -    8B  Jul 17 18:00  guide.md\n"
            "-?????????  -  -  -  1.5K  Jul 17 18:00  notes.txt\n"
            f"l?????????  -  -  -    0B  -             shortcut -> vos://{_AUTHORITY}/docs/guide.md\n"
        ),
    )

    assert all(isinstance(fs, VOSpaceFileSystem) for fs in source.filesystems)
    assert all(fs._pool.closed is True for fs in source.filesystems)
    assert [transport.requests for transport in transports] == [
        [
            ("GET", "/arc/capabilities"),
            ("GET", "/arc/nodes/docs"),
            ("GET", "/arc/nodes/docs"),
        ],
        [
            ("GET", "/arc/capabilities"),
            ("GET", "/arc/nodes/docs"),
            ("GET", "/arc/nodes/docs"),
        ],
    ]
    assert all(transport.closed for transport in transports)


def test_native_vosfs_plain_cat_profile_uses_only_mocked_transport() -> None:
    source, transports = _vosfs_source(
        transport_factory=lambda: _CatMockTransport(_BLOB_PAYLOAD),
    )

    _exercise_cat_profile("vos", source, "/docs/blob.bin", payload=_BLOB_PAYLOAD)

    assert all(isinstance(fs, VOSpaceFileSystem) for fs in source.filesystems)
    assert all(fs.asynchronous is True for fs in source.filesystems)
    assert all(fs._pool.closed is True for fs in source.filesystems)
    assert [call.source_id for call in source.close_calls] == [1, 2, 3]
    assert all(transport.closed for transport in transports)
    assert all(
        ("GET", "/arc/files") in transport.requests or ("GET", "/arc/nodes/docs/blob.bin.missing") in transport.requests
        for transport in transports
    )


def test_native_vosfs_base_mkdir_profile_uses_only_mocked_transport() -> None:
    source, transports = _vosfs_source(_RESPONSES)

    _exercise_mkdir_profile("vos", source, "/docs", parent_file_category="not found")

    assert all(isinstance(fs, VOSpaceFileSystem) for fs in source.filesystems)
    assert [transport.requests for transport in transports] == [
        [
            ("GET", "/arc/capabilities"),
            ("GET", "/arc/nodes"),
            ("PUT", "/arc/nodes/docs/subdir"),
            ("GET", "/arc/nodes/docs/subdir"),
        ],
        [
            ("GET", "/arc/capabilities"),
            ("GET", "/arc/nodes"),
            ("PUT", "/arc/nodes/docs/notes.txt"),
        ],
        [
            ("GET", "/arc/capabilities"),
            ("GET", "/arc/nodes"),
            ("PUT", "/arc/nodes/docs/notes.txt/child"),
        ],
        [
            ("GET", "/arc/capabilities"),
            ("GET", "/arc/nodes"),
            ("PUT", "/arc/nodes/docs/absent/child"),
        ],
    ]
    assert all(transport.closed for transport in transports)


def test_native_vosfs_mkdir_p_profile_uses_only_mocked_transport() -> None:
    source, transports = _vosfs_source(transport_factory=_MakedirsMockTransport)

    _exercise_mkdir_p_locked_profile(
        "vos",
        source,
        "/docs",
        parent_file_category="file exists",
    )

    assert all(isinstance(fs, VOSpaceFileSystem) for fs in source.filesystems)
    assert all(transport.closed for transport in transports)
    makedirs_calls = [call for call in source.calls if call.operation == "makedirs"]
    assert makedirs_calls
    assert all(call.exist_ok is True for call in makedirs_calls)
    assert not any(call.operation == "mkdir" for call in source.calls)


def test_native_vosfs_base_rmdir_profile_uses_only_mocked_transport() -> None:
    source, transports = _vosfs_source(transport_factory=_RmdirMockTransport)

    _exercise_rmdir_locked_profile("vos", source, "/docs")

    assert all(isinstance(fs, VOSpaceFileSystem) for fs in source.filesystems)
    assert all(transport.closed for transport in transports)
    assert [call.operation for call in source.calls if call.operation == "rmdir"] == [
        "rmdir",
        "rmdir",
    ]


def test_native_vosfs_rm_d_profile_uses_only_mocked_transport() -> None:
    source, transports = _vosfs_source(transport_factory=_RmdirMockTransport)

    result = _invoke(App({"vos": source}), "rm", ["-d", "vos:/docs/empty"])

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert all(isinstance(fs, VOSpaceFileSystem) for fs in source.filesystems)
    assert [call.path for call in source.calls if call.operation == "rmdir"] == ["/docs/empty"]
    assert all(transport.closed for transport in transports)


def test_native_vosfs_unlink_profile_uses_only_mocked_transport() -> None:
    source, transports = _vosfs_source(transport_factory=_UnlinkMockTransport)

    _exercise_unlink_locked_profile("vos", source, "/docs")

    assert all(isinstance(fs, VOSpaceFileSystem) for fs in source.filesystems)
    assert all(fs.asynchronous is True for fs in source.filesystems)
    assert all(fs._pool.closed is True for fs in source.filesystems)
    assert "/docs/notes.txt" not in transports[0].nodes
    assert all(transport.closed for transport in transports)


def test_native_vosfs_base_rm_profile_uses_only_mocked_transport() -> None:
    source, transports = _vosfs_source(transport_factory=_UnlinkMockTransport)

    _exercise_rm_locked_profile("vos", source, "/docs")
    _exercise_rm_force_profile("vos", source, "/docs")
    _exercise_rm_verbose_profile("vos", source, "/docs")

    assert all(isinstance(fs, VOSpaceFileSystem) for fs in source.filesystems)
    assert all(fs.asynchronous is True for fs in source.filesystems)
    assert all(fs._pool.closed is True for fs in source.filesystems)
    assert "/docs/notes.txt" not in transports[0].nodes
    assert "/docs/guide.md" not in transports[1].nodes
    assert "/docs/.hidden" not in transports[1].nodes
    assert all(transport.closed for transport in transports)


class _CpMockTransport(httpx.MockTransport):
    def __init__(self) -> None:
        self.requests: list[tuple[str, str]] = []
        self.closed = False
        self.nodes: dict[str, str] = {
            "/": "container",
            "/docs": "container",
            "/docs/empty": "container",
            "/docs/target": "container",
            "/docs/notes.txt": "data",
            "/docs/.hidden": "data",
            "/docs/guide.md": "data",
        }
        self.blobs: dict[str, bytes] = {
            "/docs/notes.txt": b"notes.txt",
            "/docs/.hidden": b".hidden",
            "/docs/guide.md": b"guide.md",
        }
        super().__init__(self._respond)

    def _node_path(self, url_path: str) -> str:
        prefix = "/arc/nodes"
        if not url_path.startswith(prefix):
            message = f"unexpected node url: {url_path!r}"
            raise AssertionError(message)
        node_path = url_path[len(prefix) :]
        return node_path or "/"

    def _data_xml(self, path: str) -> bytes:
        length = len(self.blobs.get(path, b""))
        return f"""<vos:node
    xmlns:vos="http://www.ivoa.net/xml/VOSpace/v2.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:type="vos:DataNode" uri="vos://{_AUTHORITY}{path}">
  <vos:properties>
    <vos:property uri="ivo://ivoa.net/vospace/core#length">{length}</vos:property>
  </vos:properties>
</vos:node>
""".encode()

    def _container_xml(self, path: str) -> bytes:
        children = []
        for child, kind in sorted(self.nodes.items()):
            if child == path or not child.startswith(path.rstrip("/") + "/"):
                continue
            rest = child[len(path.rstrip("/")) + 1 :]
            if "/" in rest:
                continue
            xsi = {
                "container": "vos:ContainerNode",
                "data": "vos:DataNode",
                "link": "vos:LinkNode",
            }[kind]
            length = ""
            if kind == "data":
                length = (
                    f'<vos:property uri="ivo://ivoa.net/vospace/core#length">{len(self.blobs[child])}</vos:property>'
                )
            children.append(
                f'<vos:node xsi:type="{xsi}" uri="vos://{_AUTHORITY}{child}">'
                f"<vos:properties>{length}</vos:properties>"
                + (f"<vos:target>vos://{_AUTHORITY}/docs/notes.txt</vos:target>" if kind == "link" else "")
                + "</vos:node>"
            )
        body = "".join(children)
        return f"""<vos:node
    xmlns:vos="http://www.ivoa.net/xml/VOSpace/v2.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:type="vos:ContainerNode" uri="vos://{_AUTHORITY}{path if path != "/" else ""}">
  <vos:properties/>
  <vos:nodes>{body}</vos:nodes>
</vos:node>
""".encode()

    def _file_response(self, path: str) -> httpx.Response:
        content = self.blobs.get(path)
        if content is None:
            return httpx.Response(404, text="not found")
        if content == b"":
            return httpx.Response(204)

        async def _stream() -> object:
            yield content

        return httpx.Response(200, content=_stream())

    async def _respond(  # noqa: C901, PLR0911 - stateful transfer mock.
        self, request: httpx.Request
    ) -> httpx.Response:
        if "range" in {name.lower() for name in request.headers}:
            message = f"unexpected Range header: {request.headers!r}"
            raise AssertionError(message)
        call = (request.method, request.url.path)
        self.requests.append(call)
        if call == ("GET", "/arc/capabilities"):
            return httpx.Response(200, content=_SYNC_CAPABILITIES)
        if call == ("POST", "/arc/synctrans"):
            return httpx.Response(
                303,
                headers={"Location": (f"{_BASE_URL}/details?t={quote(_target_path(request.content))}")},
            )
        if call == ("GET", "/arc/details"):
            return httpx.Response(
                200,
                content=_transfer_details(f"{_BASE_URL}/files?p={quote(unquote(request.url.params['t']))}"),
            )
        if call[0] == "GET" and call[1] == "/arc/files":
            return self._file_response(unquote(request.url.params["p"]))
        if call[0] == "PUT" and call[1] == "/arc/files":
            path = unquote(request.url.params["p"])
            self.nodes[path] = "data"
            self.blobs[path] = request.content
            return httpx.Response(201)
        if call[0] == "GET" and call[1].startswith("/arc/nodes"):
            node_path = self._node_path(call[1])
            kind = self.nodes.get(node_path)
            if kind is None:
                return httpx.Response(404, text="not found")
            if kind == "container":
                return httpx.Response(200, content=self._container_xml(node_path))
            return httpx.Response(200, content=self._data_xml(node_path))
        if call[0] == "PUT" and call[1].startswith("/arc/nodes"):
            node_path = self._node_path(call[1])
            self.nodes[node_path] = "container"
            return httpx.Response(201, content=self._container_xml(node_path))
        message = f"unplanned mocked request: {call!r}"
        raise AssertionError(message)

    async def aclose(self) -> None:
        self.closed = True
        await super().aclose()


def test_native_vosfs_same_source_cp_profile_uses_only_mocked_transport() -> None:
    source, transports = _vosfs_source(transport_factory=_CpMockTransport)

    _exercise_cp_locked_profile("vos", source, "/docs", payload=b"notes.txt")

    assert all(isinstance(fs, VOSpaceFileSystem) for fs in source.filesystems)
    assert all(fs.asynchronous is True for fs in source.filesystems)
    assert all(fs._pool.closed is True for fs in source.filesystems)
    assert all(transport.closed for transport in transports)
    assert any(call.operation == "cp_file" for call in source.calls)
    assert transports[0].blobs["/docs/copy.txt"] == b"notes.txt"
    assert transports[0].blobs["/docs/notes.txt"] == b"notes.txt"
    assert transports[1].blobs["/docs/target/notes.txt"] == b"notes.txt"
    assert transports[1].blobs["/docs/notes.txt"] == b"notes.txt"


def test_native_vosfs_mv_remains_unverified_without_exact_operation() -> None:
    """No source-form `_mv`; matrix row must remain unverified."""
    assert "_mv" not in VOSpaceFileSystem.__dict__
    assert not inspect.iscoroutinefunction(getattr(VOSpaceFileSystem, "_mv", None))


def test_native_vosfs_stat_profile_fails_closed_on_incomplete_info() -> None:
    source, transports = _vosfs_source(transport_factory=_UnlinkMockTransport)

    _exercise_stat_incomplete_profile("vos", source, "/docs/notes.txt")

    assert all(isinstance(fs, VOSpaceFileSystem) for fs in source.filesystems)
    assert all(fs.asynchronous is True for fs in source.filesystems)
    assert all(fs._pool.closed is True for fs in source.filesystems)
    assert all(transport.closed for transport in transports)
    assert any(call.operation == "info" for call in source.calls)
