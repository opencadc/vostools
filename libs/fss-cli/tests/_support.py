"""Recording filesystem sources for public-seam command tests."""

import asyncio
from collections.abc import Callable, Mapping
from pathlib import Path
from types import MappingProxyType
from typing import NoReturn

from fsspec.asyn import AsyncFileSystem
from typer.testing import CliRunner, Result

from fss_cli import App, AsyncFilesystemSource


def _source_must_not_run() -> NoReturn:
    raise AssertionError


def _invoke(
    command: str,
    arguments: list[str],
    *,
    sources: Mapping[str, AsyncFilesystemSource] | None = None,
    env: Mapping[str, str] | None = None,
    stdin: bytes | str | None = None,
) -> Result:
    if sources is None:
        sources = {"memory": _source_must_not_run}
    return CliRunner().invoke(
        App(sources).typer_app,
        [command, *arguments],
        env=env,
        input=stdin,
    )


class _RecordingFileSystem(AsyncFileSystem):
    cachable = False

    def __init__(
        self,
        source: "_RecordingSource",
        source_id: int,
    ) -> None:
        super().__init__(asynchronous=True)
        self.source = source
        self.source_id = source_id
        self._pending_mkdir_verify: set[str] = set()
        self._pending_rmdir_verify: set[str] = set()
        self._pending_unlink_verify: set[str] = set()
        self._pending_cp_verify: set[str] = set()
        self._created_dirs: set[str] = set()
        self._removed_paths: set[str] = set()
        self._file_contents: dict[str, bytes] = dict(source.file_contents)
        self._directories: set[str] = set(source.directories)

    def _consume_post_info(self, path: str) -> object | None:
        if path in self.source.post_info_by_path:
            scripted = self.source.post_info_by_path[path]
            if isinstance(scripted, BaseException):
                raise scripted
            return scripted
        return None

    def _post_rmdir_info(self, path: str) -> object:
        self._pending_rmdir_verify.discard(path)
        try:
            scripted = self._consume_post_info(path)
        except BaseException:
            self._removed_paths.discard(path)
            raise
        if scripted is not None:
            self._removed_paths.discard(path)
            return scripted
        if self.source.post_info_error is not None:
            self._removed_paths.discard(path)
            raise self.source.post_info_error
        raise FileNotFoundError(path)

    def _post_mkdir_info(self, path: str) -> object | None:
        self._pending_mkdir_verify.discard(path)
        scripted = self._consume_post_info(path)
        if scripted is not None:
            return scripted
        if self.source.post_info_result is not None:
            return self.source.post_info_result
        return None

    def _post_unlink_info(self, path: str) -> object:
        self._pending_unlink_verify.discard(path)
        scripted = self._consume_post_info(path)
        if scripted is not None:
            return scripted
        if self.source.post_info_error is not None:
            raise self.source.post_info_error
        raise FileNotFoundError(path)

    def _post_cp_verify_info(self, path: str) -> object:
        self._pending_cp_verify.discard(path)
        scripted = self._consume_post_info(path)
        if scripted is not None:
            return scripted
        if self.source.post_info_error is not None:
            raise self.source.post_info_error
        if path in self._file_contents:
            return MappingProxyType({"type": "file", "size": len(self._file_contents[path])})
        if path in self._directories or path in self._created_dirs:
            return MappingProxyType({"type": "directory", "size": 0})
        raise FileNotFoundError(path)

    async def _info(  # noqa: C901, PLR0911 - scripted multi-command recorder.
        self, path: str, **kwargs: object
    ) -> object:
        del kwargs
        self.source.events.append(("info", self.source_id, path, id(asyncio.get_running_loop())))
        if path in self._pending_rmdir_verify:
            return self._post_rmdir_info(path)
        if path in self._pending_unlink_verify:
            return self._post_unlink_info(path)
        if path in self._pending_cp_verify:
            return self._post_cp_verify_info(path)
        if path in self._removed_paths:
            raise FileNotFoundError(path)
        if path in self.source.info_by_path:
            scripted = self.source.info_by_path[path]
            if isinstance(scripted, BaseException):
                raise scripted
            return scripted
        if path in self._pending_mkdir_verify:
            verified = self._post_mkdir_info(path)
            if verified is not None:
                return verified
            # Explicit None post-verify must not invent success via _created_dirs.
            if self.source.info_error is not None:
                raise self.source.info_error
            return self.source.info_result
        if path in self._file_contents:
            return MappingProxyType({"type": "file", "size": len(self._file_contents[path])})
        if path in self._directories or path in self._created_dirs:
            return MappingProxyType({"type": "directory", "size": 0})
        if self.source.info_error is not None:
            raise self.source.info_error
        return self.source.info_result

    async def _ls(
        self,
        path: str,
        detail: bool = True,  # noqa: FBT002 - matches the fsspec hook signature.
        **kwargs: object,
    ) -> object:
        del kwargs
        self.source.events.append(
            (
                "ls",
                self.source_id,
                path,
                detail,
                id(asyncio.get_running_loop()),
            )
        )
        if path in self.source.ls_by_path:
            scripted = self.source.ls_by_path[path]
            if isinstance(scripted, BaseException):
                raise scripted
            return scripted
        if self.source.ls_error is not None:
            raise self.source.ls_error
        return self.source.ls_result

    async def _du(
        self,
        path: str,
        total: bool = True,  # noqa: FBT002 - matches the fsspec hook signature.
        **kwargs: object,
    ) -> object:
        del kwargs
        self.source.events.append(("du", self.source_id, path, total, id(asyncio.get_running_loop())))
        if self.source.du_error is not None:
            raise self.source.du_error
        return self.source.du_result

    async def _get_file(
        self,
        rpath: str,
        lpath: str,
        **kwargs: object,
    ) -> None:
        del kwargs
        self.source.events.append(
            (
                "get_file",
                self.source_id,
                rpath,
                id(asyncio.get_running_loop()),
            )
        )
        self.source.get_file_paths.append(lpath)
        if rpath in self.source.get_file_by_path:
            scripted = self.source.get_file_by_path[rpath]
            if isinstance(scripted, BaseException):
                raise scripted
            if callable(scripted):
                scripted(lpath)
                return
            with Path(lpath).open("wb") as handle:  # noqa: ASYNC230
                handle.write(scripted)
            return
        if rpath in self._file_contents:
            with Path(lpath).open("wb") as handle:  # noqa: ASYNC230
                handle.write(self._file_contents[rpath])
            return
        if self.source.get_file_error is not None:
            raise self.source.get_file_error
        if self.source.get_file_hook is not None:
            self.source.get_file_hook(rpath, lpath)
            return
        with Path(lpath).open("wb") as handle:  # noqa: ASYNC230
            handle.write(self.source.get_file_content)

    async def _put_file(
        self,
        lpath: str,
        rpath: str,
        mode: str = "overwrite",
        **kwargs: object,
    ) -> None:
        del kwargs
        self.source.events.append(
            (
                "put_file",
                self.source_id,
                rpath,
                mode,
                id(asyncio.get_running_loop()),
            )
        )
        if rpath in self.source.put_file_by_path:
            scripted = self.source.put_file_by_path[rpath]
            if isinstance(scripted, BaseException):
                raise scripted
            if callable(scripted):
                scripted(lpath, rpath)
                return
        elif self.source.put_file_error is not None:
            raise self.source.put_file_error
        if self.source.put_file_hook is not None:
            self.source.put_file_hook(lpath, rpath)
            self._pending_cp_verify.add(rpath)
            return
        content = Path(lpath).read_bytes()  # noqa: ASYNC240
        self._file_contents[rpath] = content
        self.source.file_contents[rpath] = content
        self._pending_cp_verify.add(rpath)

    async def _mkdir(
        self,
        path: str,
        create_parents: bool = True,  # noqa: FBT002 - matches the fsspec hook signature.
        **kwargs: object,
    ) -> None:
        del kwargs
        self.source.events.append(
            (
                "mkdir",
                self.source_id,
                path,
                create_parents,
                id(asyncio.get_running_loop()),
            )
        )
        if path in self.source.mkdir_by_path:
            scripted = self.source.mkdir_by_path[path]
            if isinstance(scripted, BaseException):
                raise scripted
        elif path in self._created_dirs:
            raise FileExistsError(path)
        if self.source.mkdir_error is not None:
            raise self.source.mkdir_error
        self._created_dirs.add(path)
        self._pending_mkdir_verify.add(path)

    async def _makedirs(
        self,
        path: str,
        exist_ok: bool = False,  # noqa: FBT002 - matches the fsspec hook signature.
        **kwargs: object,
    ) -> None:
        del kwargs
        self.source.events.append(
            (
                "makedirs",
                self.source_id,
                path,
                exist_ok,
                id(asyncio.get_running_loop()),
            )
        )
        if path in self.source.makedirs_by_path:
            scripted = self.source.makedirs_by_path[path]
            if isinstance(scripted, BaseException):
                raise scripted
        elif path in self._created_dirs:
            if not exist_ok:
                raise FileExistsError(path)
            self._pending_mkdir_verify.add(path)
            return
        if self.source.makedirs_error is not None:
            raise self.source.makedirs_error
        self._created_dirs.add(path)
        self._pending_mkdir_verify.add(path)

    async def _rmdir(self, path: str, **kwargs: object) -> None:
        del kwargs
        self.source.events.append(("rmdir", self.source_id, path, id(asyncio.get_running_loop())))
        if self.source.trap_rmdir:
            message = "_rmdir must not be called by file-only removal"
            raise AssertionError(message)
        if path in self.source.rmdir_by_path:
            scripted = self.source.rmdir_by_path[path]
            if isinstance(scripted, BaseException):
                raise scripted
        elif self.source.rmdir_error is not None:
            raise self.source.rmdir_error
        self._removed_paths.add(path)
        self._pending_rmdir_verify.add(path)

    async def _rm_file(self, path: str, **kwargs: object) -> None:
        del kwargs
        self.source.events.append(("rm_file", self.source_id, path, id(asyncio.get_running_loop())))
        if path in self.source.rm_file_by_path:
            scripted = self.source.rm_file_by_path[path]
            if isinstance(scripted, BaseException):
                raise scripted
        elif self.source.rm_file_error is not None:
            raise self.source.rm_file_error
        self._removed_paths.add(path)
        self._pending_unlink_verify.add(path)

    async def _rm(self, path: str, **kwargs: object) -> None:
        del kwargs
        self.source.events.append(("rm", self.source_id, path, id(asyncio.get_running_loop())))
        message = "_rm must not be called by file-only removal"
        raise AssertionError(message)

    async def _cp_file(self, path1: str, path2: str, **kwargs: object) -> None:
        del kwargs
        self.source.events.append(
            (
                "cp_file",
                self.source_id,
                path1,
                path2,
                id(asyncio.get_running_loop()),
            )
        )
        if path1 in self.source.cp_file_by_path:
            scripted = self.source.cp_file_by_path[path1]
            if isinstance(scripted, BaseException):
                raise scripted
            if callable(scripted):
                scripted(path1, path2)
                return
        if self.source.cp_file_error is not None:
            raise self.source.cp_file_error
        if self.source.cp_file_hook is not None:
            self.source.cp_file_hook(path1, path2)
            self._pending_cp_verify.add(path2)
            return
        if path1 in self._file_contents:
            content = self._file_contents[path1]
        elif path1 in self.source.get_file_by_path and isinstance(self.source.get_file_by_path[path1], bytes):
            content = self.source.get_file_by_path[path1]
        else:
            content = self.source.get_file_content
        self._file_contents[path2] = content
        self.source.file_contents[path2] = content
        self._pending_cp_verify.add(path2)

    async def _mv(self, path1: str, path2: str, **kwargs: object) -> None:
        del kwargs
        self.source.events.append(("mv", self.source_id, path1, path2, id(asyncio.get_running_loop())))
        if path1 in self.source.mv_by_path:
            scripted = self.source.mv_by_path[path1]
            if isinstance(scripted, BaseException):
                raise scripted
            if callable(scripted):
                scripted(path1, path2)
                return
        if self.source.mv_error is not None:
            raise self.source.mv_error
        if self.source.mv_hook is not None:
            self.source.mv_hook(path1, path2)
            return
        self._file_contents[path2] = self._file_contents.pop(path1)
        self.source.file_contents[path2] = self.source.file_contents.pop(path1)
        self._removed_paths.add(path1)


class _RecordingSource:
    def __init__(  # noqa: PLR0913 - configurable external-boundary recording fake.
        self,
        events: list[tuple[object, ...]],
        info_result: object = MappingProxyType({"type": "file"}),
        *,
        info_error: BaseException | None = None,
        ls_result: object = None,
        ls_error: BaseException | None = None,
        du_result: object = MappingProxyType({"/file": 0}),
        du_error: BaseException | None = None,
        mkdir_error: BaseException | None = None,
        makedirs_error: BaseException | None = None,
        rmdir_error: BaseException | None = None,
        rm_file_error: BaseException | None = None,
        cp_file_error: BaseException | None = None,
        put_file_error: BaseException | None = None,
        mv_error: BaseException | None = None,
        trap_rmdir: bool = False,
        post_info_result: object | None = MappingProxyType({"type": "directory"}),
        post_info_error: BaseException | None = None,
        exit_result: object = None,
        exit_error: BaseException | None = None,
        info_by_path: Mapping[str, object] | None = None,
        ls_by_path: Mapping[str, object] | None = None,
        mkdir_by_path: Mapping[str, object] | None = None,
        makedirs_by_path: Mapping[str, object] | None = None,
        rmdir_by_path: Mapping[str, object] | None = None,
        rm_file_by_path: Mapping[str, object] | None = None,
        cp_file_by_path: Mapping[str, object] | None = None,
        put_file_by_path: Mapping[str, object] | None = None,
        mv_by_path: Mapping[str, object] | None = None,
        post_info_by_path: Mapping[str, object] | None = None,
        get_file_content: bytes = b"",
        get_file_error: BaseException | None = None,
        get_file_by_path: Mapping[str, object] | None = None,
        get_file_hook: Callable[[str, str], None] | None = None,
        cp_file_hook: Callable[[str, str], None] | None = None,
        put_file_hook: object | None = None,
        mv_hook: Callable[[str, str], None] | None = None,
        file_contents: Mapping[str, bytes] | None = None,
        directories: set[str] | None = None,
    ) -> None:
        self.events = events
        self.info_result = info_result
        self.info_error = info_error
        self.ls_result = ls_result
        self.ls_error = ls_error
        self.du_result = du_result
        self.du_error = du_error
        self.mkdir_error = mkdir_error
        self.makedirs_error = makedirs_error
        self.rmdir_error = rmdir_error
        self.rm_file_error = rm_file_error
        self.cp_file_error = cp_file_error
        self.put_file_error = put_file_error
        self.mv_error = mv_error
        self.trap_rmdir = trap_rmdir
        self.post_info_result = post_info_result
        self.post_info_error = post_info_error
        self.exit_result = exit_result
        self.exit_error = exit_error
        self.info_by_path = info_by_path or {}
        self.ls_by_path = ls_by_path or {}
        self.mkdir_by_path = mkdir_by_path or {}
        self.makedirs_by_path = makedirs_by_path or {}
        self.rmdir_by_path = rmdir_by_path or {}
        self.rm_file_by_path = rm_file_by_path or {}
        self.cp_file_by_path = cp_file_by_path or {}
        self.put_file_by_path = put_file_by_path or {}
        self.mv_by_path = mv_by_path or {}
        self.post_info_by_path = post_info_by_path or {}
        self.get_file_content = get_file_content
        self.get_file_error = get_file_error
        self.get_file_by_path = get_file_by_path or {}
        self.get_file_hook = get_file_hook
        self.cp_file_hook = cp_file_hook
        self.put_file_hook = put_file_hook
        self.mv_hook = mv_hook
        self.file_contents = dict(file_contents or {})
        self.directories = set(directories or ())
        self.get_file_paths: list[str] = []
        self.exit_calls: list[tuple[object, object, object]] = []
        self.contexts: list[_RecordingContext] = []
        self.call_count = 0

    def __call__(self) -> "_RecordingContext":
        self.call_count += 1
        self.events.append(("factory", self.call_count))
        context = _RecordingContext(self, self.call_count)
        self.contexts.append(context)
        return context


class _RecordingContext:
    def __init__(
        self,
        source: _RecordingSource,
        source_id: int,
    ) -> None:
        self.source = source
        self.source_id = source_id
        self.filesystem = _RecordingFileSystem(source, source_id)

    async def __aenter__(self) -> _RecordingFileSystem:
        self.source.events.append(("enter", self.source_id, id(asyncio.get_running_loop())))
        return self.filesystem

    async def __aexit__(self, *exc_info: object) -> object:
        self.source.exit_calls.append(exc_info)
        self.source.events.append(("exit", self.source_id, id(asyncio.get_running_loop())))
        if self.source.exit_error is not None:
            raise self.source.exit_error
        return self.source.exit_result
