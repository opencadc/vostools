"""Recursive-command compatibility evidence for native vosfs."""

import asyncio
from collections.abc import Callable

import httpx
import pytest
from fsspec.implementations.asyn_wrapper import AsyncFileSystemWrapper
from fsspec.implementations.local import LocalFileSystem
from fsspec.implementations.memory import MemoryFileSystem
from typer.testing import CliRunner
from vosfs import VOSpaceFileSystem

from fss_cli import App

from ._matrix_support import (
    _exercise_recursive_rm_profile,
    _invoke,
    _ProbedSource,
)
from ._vosfs_matrix_support import (
    _AUTHORITY,
    _BASE_URL,
    _CAPABILITIES,
    _close_vosfs,
    _vosfs_source,
)
from .test_vosfs_command_matrix import _CpMockTransport


class _RecursiveRmMockTransport(httpx.MockTransport):
    def __init__(self) -> None:
        self.requests: list[tuple[str, str]] = []
        self.closed = False
        self.events: list[str] = []
        self.delete_errors: dict[str, int] = {}
        self.disappear_before_delete: set[str] = set()
        self.add_before_delete: dict[str, tuple[str, str]] = {}
        self.cancel_delete_path: str | None = None
        self.nodes = {
            "/docs": "container",
            "/docs/empty": "container",
            "/docs/nested": "container",
            "/docs/nested/a.txt": "data",
            "/docs/z.txt": "data",
        }
        super().__init__(self._respond)

    def _node_path(self, url_path: str) -> str:
        prefix = "/arc/nodes"
        if not url_path.startswith(prefix):
            message = f"unexpected recursive-rm url: {url_path!r}"
            raise AssertionError(message)
        return url_path[len(prefix) :] or "/"

    def _node_xml(self, path: str) -> bytes:
        node_type = self.nodes[path]
        xsi_type = {
            "container": "vos:ContainerNode",
            "data": "vos:DataNode",
            "link": "vos:LinkNode",
            "special": "vos:UnknownNode",
        }[node_type]
        children = []
        if node_type == "container":
            prefix = f"{path}/"
            for child in sorted(self.nodes):
                if child.startswith(prefix) and "/" not in child[len(prefix) :]:
                    child_kind = self.nodes[child]
                    child_type = {
                        "container": "vos:ContainerNode",
                        "data": "vos:DataNode",
                        "link": "vos:LinkNode",
                        "special": "vos:UnknownNode",
                    }[child_kind]
                    target = f"<vos:target>vos://{_AUTHORITY}/docs/z.txt</vos:target>" if child_kind == "link" else ""
                    children.append(
                        f'<vos:node xsi:type="{child_type}" '
                        f'uri="vos://{_AUTHORITY}{child}"><vos:properties/>'
                        + ("<vos:nodes/>" if child_type == "vos:ContainerNode" else "")
                        + target
                        + "</vos:node>"
                    )
        nodes = f"<vos:nodes>{''.join(children)}</vos:nodes>" if node_type == "container" else ""
        return f"""<vos:node
    xmlns:vos="http://www.ivoa.net/xml/VOSpace/v2.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:type="{xsi_type}" uri="vos://{_AUTHORITY}{path}">
  <vos:properties/>{nodes}
</vos:node>
""".encode()

    async def _respond(  # noqa: C901, PLR0911 - scenario-configurable transport.
        self,
        request: httpx.Request,
    ) -> httpx.Response:
        call = (request.method, request.url.path)
        self.requests.append(call)
        if call == ("GET", "/arc/capabilities"):
            return httpx.Response(200, content=_CAPABILITIES)
        if call[1].startswith("/arc/nodes"):
            path = self._node_path(call[1])
            if call[0] == "GET":
                if path not in self.nodes:
                    return httpx.Response(404, text="not found")
                return httpx.Response(200, content=self._node_xml(path))
            if call[0] == "DELETE":
                if path == self.cancel_delete_path:
                    current = asyncio.current_task()
                    owner = next(task for task in asyncio.all_tasks() if task is not current)
                    owner.cancel()
                    await asyncio.sleep(0)
                    self.events.append("delete-drained")
                if path in self.disappear_before_delete:
                    self.nodes.pop(path, None)
                    return httpx.Response(404, text="not found")
                if path in self.delete_errors:
                    return httpx.Response(
                        self.delete_errors[path],
                        text="injected delete failure",
                    )
                addition = self.add_before_delete.pop(path, None)
                if addition is not None:
                    child, kind = addition
                    self.nodes[child] = kind
                prefix = f"{path}/"
                if path not in self.nodes or any(child.startswith(prefix) for child in self.nodes):
                    return httpx.Response(409, text="not empty")
                del self.nodes[path]
                return httpx.Response(200, text="deleted")
        message = f"unplanned mocked request: {call!r}"
        raise AssertionError(message)

    async def aclose(self) -> None:
        self.closed = True
        self.events.append("closed")
        await super().aclose()


def _recursive_rm_vos_source(
    configure: Callable[[_RecursiveRmMockTransport], None] | None = None,
) -> tuple[_ProbedSource[VOSpaceFileSystem], list[_RecursiveRmMockTransport]]:
    def make_transport() -> _RecursiveRmMockTransport:
        transport = _RecursiveRmMockTransport()
        if configure is not None:
            configure(transport)
        return transport

    return _vosfs_source(transport_factory=make_transport)


def test_native_vosfs_recursive_rm_profile_uses_only_mocked_transport() -> None:
    source, transports = _recursive_rm_vos_source()

    _exercise_recursive_rm_profile("vos", source, "/docs")

    assert all(isinstance(fs, VOSpaceFileSystem) for fs in source.filesystems)
    assert transports[0].nodes == {}
    assert all(transport.closed for transport in transports)
    assert not any(
        "async-delete" in path or "synctrans" in path
        for transport in transports
        for _method, path in transport.requests
    )


@pytest.mark.parametrize(
    ("kind", "category"),
    [
        ("link", "unsupported operation"),
        ("special", "backend failure"),
    ],
)
def test_native_vosfs_recursive_rm_rejects_link_or_special_before_delete(
    kind: str,
    category: str,
) -> None:
    def configure(transport: _RecursiveRmMockTransport) -> None:
        transport.nodes["/docs/bad"] = kind

    source, transports = _recursive_rm_vos_source(configure)

    result = _invoke(
        App(
            {"vos": source},
            capabilities={"recursion": {"remove": True}},
        ),
        "rm",
        ["-R", "vos:/docs"],
    )

    assert result.exit_code == 1
    assert result.stdout == ""
    assert category in result.stderr
    assert not any(method == "DELETE" for method, _path in transports[0].requests)
    assert transports[0].closed


def test_native_vosfs_recursive_rm_reports_concurrent_disappearance() -> None:
    def configure(transport: _RecursiveRmMockTransport) -> None:
        transport.disappear_before_delete.add("/docs/empty")

    source, transports = _recursive_rm_vos_source(configure)

    result = _invoke(
        App(
            {"vos": source},
            capabilities={"recursion": {"remove": True}},
        ),
        "rm",
        ["-R", "vos:/docs"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "rm: vos:/docs: recursive removal incomplete; residue possible\n",
    )
    # Leaves-first by depth: the deeper file is removed before the vanished
    # sibling directory fails; the root is never removed.
    assert "/docs/empty" not in transports[0].nodes
    assert "/docs/nested/a.txt" not in transports[0].nodes
    assert "/docs" in transports[0].nodes
    assert transports[0].closed


def test_native_vosfs_recursive_rm_reports_partial_delete_success() -> None:
    def configure(transport: _RecursiveRmMockTransport) -> None:
        transport.delete_errors["/docs/nested/a.txt"] = 503

    source, transports = _recursive_rm_vos_source(configure)

    result = _invoke(
        App(
            {"vos": source},
            capabilities={"recursion": {"remove": True}},
        ),
        "rm",
        ["-R", "vos:/docs"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "rm: vos:/docs: recursive removal incomplete; residue possible\n",
    )
    # The deepest level fails first, so no shallower entry is removed.
    assert "/docs/empty" in transports[0].nodes
    assert "/docs/nested/a.txt" in transports[0].nodes
    assert transports[0].closed


def test_native_vosfs_recursive_rm_reports_concurrent_addition() -> None:
    def configure(transport: _RecursiveRmMockTransport) -> None:
        transport.add_before_delete["/docs"] = ("/docs/late", "data")

    source, transports = _recursive_rm_vos_source(configure)

    result = _invoke(
        App(
            {"vos": source},
            capabilities={"recursion": {"remove": True}},
        ),
        "rm",
        ["-R", "vos:/docs"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "rm: vos:/docs: recursive removal incomplete; residue possible\n",
    )
    assert transports[0].nodes == {"/docs": "container", "/docs/late": "data"}
    assert transports[0].closed


def test_native_vosfs_recursive_rm_drains_cancelled_delete() -> None:
    def configure(transport: _RecursiveRmMockTransport) -> None:
        transport.cancel_delete_path = "/docs/empty"

    source, transports = _recursive_rm_vos_source(configure)

    with pytest.raises(asyncio.CancelledError):
        _invoke(
            App(
                {"vos": source},
                capabilities={"recursion": {"remove": True}},
            ),
            "rm",
            ["-R", "vos:/docs"],
        )

    assert transports[0].events == ["delete-drained", "closed"]
    # The cancelled delete was drained after the deeper level was removed.
    assert "/docs/empty" not in transports[0].nodes
    assert "/docs/nested/a.txt" not in transports[0].nodes
    assert "/docs" in transports[0].nodes


def test_native_vosfs_recursive_cp_profile_uses_only_mocked_transport() -> None:
    source, transports = _vosfs_source(transport_factory=_CpMockTransport)

    result = CliRunner().invoke(
        App({"vos": source}).typer_app,
        ["cp", "-R", "vos:/docs", "vos:/copy"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    assert transports[0].blobs["/copy/notes.txt"] == b"notes.txt"
    assert transports[0].nodes["/copy/empty"] == "container"
    assert transports[0].blobs["/docs/notes.txt"] == b"notes.txt"
    assert transports[0].closed
    assert source.filesystems[0]._pool.closed is True


def test_native_vosfs_recursive_cp_rejects_mocked_link_node_before_mutation() -> None:
    transports: list[_CpMockTransport] = []

    def make_filesystem() -> VOSpaceFileSystem:
        transport = _CpMockTransport()
        transport.nodes["/docs/shortcut"] = "link"
        transports.append(transport)
        return VOSpaceFileSystem(
            _BASE_URL,
            transport=transport,
            asynchronous=True,
            skip_instance_cache=True,
            trust_env=False,
        )

    source = _ProbedSource(make_filesystem, close=_close_vosfs)

    result = CliRunner().invoke(
        App({"vos": source}).typer_app,
        ["cp", "-R", "vos:/docs", "vos:/copy"],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (
        1,
        "",
        "cp: vos:/docs: unsupported entry type\n",
    )
    assert all(method == "GET" for method, _path in transports[0].requests)
    assert "/copy" not in transports[0].nodes
    assert transports[0].closed
    assert source.filesystems[0]._pool.closed is True


@pytest.mark.parametrize(
    ("source_form", "destination_form"),
    [
        ("vos", "vos"),
        ("vos", "local"),
        ("vos", "memory"),
        ("local", "vos"),
        ("memory", "vos"),
    ],
)
def test_recursive_cp_between_distinct_native_vosfs_and_adapted_sources(
    tmp_path,
    monkeypatch: pytest.MonkeyPatch,
    source_form: str,
    destination_form: str,
) -> None:
    monkeypatch.setattr(MemoryFileSystem, "store", {})
    monkeypatch.setattr(MemoryFileSystem, "pseudo_dirs", [""])
    monkeypatch.setattr(MemoryFileSystem, "_cache", {})
    memory = MemoryFileSystem()
    memory.makedirs("/source/empty")
    memory.pipe_file("/source/notes.txt", b"notes.txt")
    memory.makedirs("/destination")
    local_source = tmp_path / "source"
    local_destination = tmp_path / "destination"
    local_source.mkdir()
    local_destination.mkdir()
    (local_source / "empty").mkdir()
    (local_source / "notes.txt").write_bytes(b"notes.txt")
    transports: list[_CpMockTransport] = []

    def make_vosfs() -> VOSpaceFileSystem:
        transport = _CpMockTransport()
        transports.append(transport)
        return VOSpaceFileSystem(
            _BASE_URL,
            transport=transport,
            asynchronous=True,
            skip_instance_cache=True,
            trust_env=False,
        )

    def make_source(form: str) -> _ProbedSource:
        if form == "vos":
            return _ProbedSource(make_vosfs, close=_close_vosfs)
        if form == "local":
            return _ProbedSource(
                lambda: AsyncFileSystemWrapper(LocalFileSystem(skip_instance_cache=True), asynchronous=True)
            )
        return _ProbedSource(lambda: AsyncFileSystemWrapper(memory, asynchronous=True))

    source = make_source(source_form)
    destination = make_source(destination_form)
    source_path = {
        "vos": "/docs",
        "local": local_source.as_posix(),
        "memory": "/source",
    }[source_form]
    destination_path = {
        "vos": "/copy",
        "local": (local_destination / "copy").as_posix(),
        "memory": "/destination/copy",
    }[destination_form]

    result = CliRunner().invoke(
        App({"source": source, "destination": destination}).typer_app,
        [
            "cp",
            "-r",
            f"source:{source_path}",
            f"destination:{destination_path}",
        ],
    )

    assert (result.exit_code, result.stdout, result.stderr) == (0, "", "")
    if destination_form == "vos":
        destination_transport = transports[-1]
        assert destination_transport.blobs["/copy/notes.txt"] == b"notes.txt"
        assert destination_transport.nodes["/copy/empty"] == "container"
    elif destination_form == "local":
        assert (local_destination / "copy" / "notes.txt").read_bytes() == b"notes.txt"
        assert (local_destination / "copy" / "empty").is_dir()
    else:
        assert memory.cat("/destination/copy/notes.txt") == b"notes.txt"
        assert memory.isdir("/destination/copy/empty")
    assert all(transport.closed for transport in transports)
