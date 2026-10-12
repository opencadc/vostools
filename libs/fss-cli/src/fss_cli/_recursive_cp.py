"""Verified two-operand recursive ``cp``."""

from __future__ import annotations

import logging
import os
import tempfile
import time
from collections.abc import Mapping
from contextlib import suppress
from dataclasses import dataclass, replace
from functools import partial
from pathlib import Path
from types import MappingProxyType
from typing import TYPE_CHECKING, cast

from ._command import (
    _backend_category,
    _call,
    _CommandFailureError,
    _MappedOperand,
    _render_operand_diagnostic,
    _usage_error,
)
from ._concurrent import _outcome_value, _run_bounded
from ._content import describe_checksums, matching_checksum, same_contents
from ._diagnostics import _render_diagnostic_value
from ._logging import _location, _log_file_operation, _redact
from ._manifest import (
    _MAX_ENTRIES,
    _entry,
    _EntryLimitError,
    _IncompatibleResultError,
    _Manifest,
    _manifest,
    _ManifestEntry,
    _shared_tokens_match,
    _UnsupportedEntryError,
)
from ._path import (
    _has_dot_segment,
    _is_root,
    _is_same_or_descendant,
    _lexical_basename,
    _lexical_join,
    _lexical_parent,
)

if TYPE_CHECKING:
    from fsspec.asyn import AsyncFileSystem

    from ._concurrent import _Outcome

_LOGGER = logging.getLogger(__name__)


@dataclass(frozen=True)
class _RecursiveCpFailure:
    operand: _MappedOperand
    category: str | None = None
    backend_error: Exception | None = None
    residue: bool = False
    rendered: bool = False


@dataclass(frozen=True)
class _DestinationPlan:
    missing_directories: tuple[_ManifestEntry, ...]
    files: Mapping[str, _ManifestEntry]


@dataclass(frozen=True)
class _TransferResult:
    """One file transfer's observed outcome, rendered later in manifest order.

    ``skipped`` holds the destination metadata proof of a verified skip;
    ``cleanup_error`` records a staging cleanup failure alongside, or instead
    of, the transfer ``failure``.
    """

    failure: _RecursiveCpFailure | None = None
    cleanup_error: Exception | None = None
    skipped: _ManifestEntry | None = None

    @property
    def failed(self) -> bool:
        return self.failure is not None or self.cleanup_error is not None


def _canonical_operand(
    command: str,
    operand: _MappedOperand,
    *,
    source: bool,
) -> _MappedOperand:
    if _has_dot_segment(operand.path):
        rendered = _render_diagnostic_value(operand.spelling)
        _usage_error(command, f"{rendered}: dot segment unsupported")
    if source and _is_root(operand.path):
        rendered = _render_diagnostic_value(operand.spelling)
        _usage_error(command, f"{rendered}: source root unsupported")
    return operand


def _render_failure(command: str, failure: _RecursiveCpFailure) -> None:
    if failure.rendered:
        return
    suffix = "; destination residue may remain" if failure.residue else ""
    _render_operand_diagnostic(
        command,
        failure.operand,
        f"{failure.category}{suffix}",
    )


def _read_failure(operand: _MappedOperand, error: Exception) -> _RecursiveCpFailure:
    return _RecursiveCpFailure(operand, _backend_category(error), backend_error=error)


def _staging_failure(source: _MappedOperand, error: Exception) -> _RecursiveCpFailure:
    rendered_class = _render_diagnostic_value(type(error).__name__)
    return _RecursiveCpFailure(
        source,
        f"staging failure ({rendered_class})",
        backend_error=error,
        residue=True,
    )


async def _optional_info(
    filesystem: AsyncFileSystem,
    path: str,
) -> tuple[object | None, Exception | None]:
    try:
        return await _call(filesystem, "_info", path), None
    except FileNotFoundError:
        return None, None
    except Exception as error:  # noqa: BLE001 - classify read boundary.
        return None, error


def _classify_source_info(
    operand: _MappedOperand,
    info: object,
) -> _RecursiveCpFailure | None:
    entry = _classify_existing(operand, operand.path, info, require_name=False)
    if isinstance(entry, _RecursiveCpFailure):
        return entry
    if entry.kind == "file":
        return _RecursiveCpFailure(operand, "not a directory")
    return None


def _classify_existing(  # noqa: PLR0911 - stable metadata categories.
    operand: _MappedOperand,
    path: str,
    info: object,
    *,
    require_name: bool = True,
) -> _ManifestEntry | _RecursiveCpFailure:
    if not require_name:
        if not isinstance(info, Mapping):
            return _RecursiveCpFailure(operand, "incompatible result")
        typed_info = cast("Mapping[object, object]", info)
        kind = typed_info.get("type")
        if type(kind) is not str:
            return _RecursiveCpFailure(operand, "incompatible result")
        islink = typed_info.get("islink", False)
        if type(islink) is not bool:
            return _RecursiveCpFailure(operand, "incompatible result")
        if islink or kind not in {"directory", "file"}:
            return _RecursiveCpFailure(operand, "unsupported entry type")
        return _ManifestEntry("", path, kind, None, ())
    try:
        entry = _entry("", path, info)
    except _UnsupportedEntryError as error:
        return _RecursiveCpFailure(operand, "unsupported entry type", backend_error=error)
    except _IncompatibleResultError as error:
        return _RecursiveCpFailure(operand, "incompatible result", backend_error=error)
    return entry


def _destination_path(root: str, relative: str) -> str:
    return root if not relative else _lexical_join(root, relative)


def _remove_staging(path: str) -> Exception | None:
    try:
        Path(path).unlink(missing_ok=True)
    except Exception as error:  # noqa: BLE001 - diagnostic boundary.
        return error
    return None


def _render_cleanup_failure(
    command: str,
    source: _MappedOperand,
    error: Exception,
) -> None:
    rendered_class = _render_diagnostic_value(type(error).__name__)
    _render_operand_diagnostic(
        command,
        source,
        f"staging cleanup failure ({rendered_class}); host staging residue may remain; destination residue may remain",
    )


def _cleanup_staging(
    command: str,
    source: _MappedOperand,
    path: str,
) -> Exception | None:
    error = _remove_staging(path)
    if error is not None:
        _render_cleanup_failure(command, source, error)
    return error


def _directory_depth(entry: _ManifestEntry) -> int:
    return 0 if not entry.relative else entry.relative.count("/") + 1


def _cleanup_under_control(command: str, source: _MappedOperand, path: str) -> None:
    with suppress(BaseException):  # Original control flow wins.
        _cleanup_staging(command, source, path)


@dataclass(frozen=True)
class _RecursiveCopy:
    command: str
    source: _MappedOperand
    destination: _MappedOperand
    source_filesystem: AsyncFileSystem
    destination_filesystem: AsyncFileSystem

    async def _resolve_target(  # noqa: C901, PLR0911, PLR0912
        self,
    ) -> tuple[str, object | None, _RecursiveCpFailure | None]:
        destination_info, error = await _optional_info(
            self.destination_filesystem,
            self.destination.path,
        )
        if error is not None:
            return self.destination.path, None, _read_failure(self.destination, error)

        known_parent = None
        resolved = self.destination.path
        resolved_info = destination_info
        if destination_info is not None:
            entry = _classify_existing(
                self.destination,
                self.destination.path,
                destination_info,
                require_name=False,
            )
            if isinstance(entry, _RecursiveCpFailure):
                return resolved, None, entry
            if entry.kind == "directory":
                known_parent = self.destination.path
                resolved = _lexical_join(
                    self.destination.path,
                    _lexical_basename(self.source.path),
                )
                resolved_info, error = await _optional_info(
                    self.destination_filesystem,
                    resolved,
                )
                if error is not None:
                    return resolved, None, _read_failure(self.destination, error)

        parent = _lexical_parent(resolved)
        if parent != known_parent:
            parent_info, error = await _optional_info(
                self.destination_filesystem,
                parent,
            )
            if error is not None:
                return resolved, None, _read_failure(self.destination, error)
            if parent_info is None:
                return (
                    resolved,
                    None,
                    _RecursiveCpFailure(self.destination, "not found"),
                )
            parent_entry = _classify_existing(
                self.destination,
                parent,
                parent_info,
                require_name=False,
            )
            if isinstance(parent_entry, _RecursiveCpFailure):
                if parent_entry.category == "unsupported entry type":
                    return (
                        resolved,
                        None,
                        _RecursiveCpFailure(self.destination, "not a directory"),
                    )
                return resolved, None, parent_entry
            if parent_entry.kind != "directory":
                return (
                    resolved,
                    None,
                    _RecursiveCpFailure(self.destination, "not a directory"),
                )

        if resolved_info is not None:
            root_entry = _classify_existing(
                self.destination,
                resolved,
                resolved_info,
                require_name=False,
            )
            if isinstance(root_entry, _RecursiveCpFailure):
                return resolved, None, root_entry
            if root_entry.kind == "file":
                return (
                    resolved,
                    None,
                    _RecursiveCpFailure(
                        self.destination,
                        "destination type conflict",
                    ),
                )

        if self.source.name == self.destination.name and _is_same_or_descendant(
            self.source.path,
            resolved,
        ):
            return (
                resolved,
                None,
                _RecursiveCpFailure(
                    self.destination,
                    "destination is inside source",
                ),
            )
        return resolved, resolved_info, None

    async def _preflight_destination(
        self,
        root: str,
        manifest: _Manifest,
        root_info: object | None,
    ) -> tuple[_DestinationPlan, _RecursiveCpFailure | None]:
        """Classify every destination path before any mutation.

        A missing destination root makes every entry missing, so no per-entry
        request is made. Otherwise the root's resolution metadata is reused
        and every other entry is read concurrently, then classified in
        manifest order.
        """
        if root_info is None:
            missing = tuple(entry for entry in manifest if entry.kind == "directory")
            return _DestinationPlan(missing, MappingProxyType({})), None
        paths = [_destination_path(root, entry.relative) for entry in manifest]

        async def read(path: str) -> tuple[object | None, Exception | None]:
            if path == root:
                return root_info, None
            return await _optional_info(self.destination_filesystem, path)

        outcomes = await _run_bounded(
            [partial(read, path) for path in paths],
            stop=lambda result: result[1] is not None,
        )
        missing: list[_ManifestEntry] = []
        files: dict[str, _ManifestEntry] = {}
        empty = _DestinationPlan((), MappingProxyType({}))
        for entry, path, outcome in zip(manifest, paths, outcomes, strict=True):
            info, error = _outcome_value(outcome)
            if error is not None:
                return empty, _read_failure(self.destination, error)
            if info is None:
                if entry.kind == "directory":
                    missing.append(entry)
                continue
            existing = self._existing_destination(entry, path, info)
            if isinstance(existing, _RecursiveCpFailure):
                return empty, existing
            if existing.kind == "file":
                files[path] = existing
        return _DestinationPlan(tuple(missing), MappingProxyType(files)), None

    def _existing_destination(
        self,
        entry: _ManifestEntry,
        path: str,
        info: object,
    ) -> _ManifestEntry | _RecursiveCpFailure:
        existing = _classify_existing(self.destination, path, info)
        if isinstance(existing, _RecursiveCpFailure):
            return existing
        if existing.kind != entry.kind:
            return _RecursiveCpFailure(self.destination, "destination type conflict")
        return existing

    def _log_identity(
        self,
        source_entry: _ManifestEntry,
        destination_path: str,
        existing: _ManifestEntry,
    ) -> None:
        if not _LOGGER.isEnabledFor(logging.DEBUG):
            return
        if existing.size != source_entry.size:
            basis = f"sizes differ ({source_entry.size} != {existing.size}); content comparison skipped"
        else:
            basis = describe_checksums(source_entry.tokens, existing.tokens)
        _LOGGER.debug(
            "content identity %s -> %s: source tokens [%s], destination tokens [%s]; %s",
            _location(self.source.name, source_entry.path),
            _location(self.destination.name, destination_path),
            ", ".join(name for name, _ in source_entry.tokens),
            ", ".join(name for name, _ in existing.tokens),
            basis,
        )

    async def _transfer(  # noqa: C901, PLR0912
        self,
        source_entry: _ManifestEntry,
        destination_path: str,
        existing: _ManifestEntry | None,
    ) -> _TransferResult:
        started = time.monotonic()
        source_location = _location(self.source.name, source_entry.path)
        destination_location = _location(self.destination.name, destination_path)
        if existing is not None:
            self._log_identity(source_entry, destination_path, existing)
        if (
            existing is not None
            and existing.size == source_entry.size
            and matching_checksum(source_entry.tokens, existing.tokens)
        ):
            _log_file_operation(
                _LOGGER,
                "skipped",
                source_location,
                destination_location,
                source_entry.size,
                started,
            )
            return _TransferResult(skipped=existing)
        temporary = None
        try:
            descriptor, temporary = tempfile.mkstemp(prefix="fss-cli-cp-recursive-")
        except Exception as error:  # noqa: BLE001 - staging creation boundary.
            return _TransferResult(_staging_failure(self.source, error))
        _LOGGER.debug("staging %s through %s", source_location, _redact(temporary))

        failure = None
        try:
            try:
                os.close(descriptor)
            except Exception as error:  # noqa: BLE001 - descriptor boundary.
                failure = _staging_failure(self.source, error)

            if failure is None:
                try:
                    await _call(
                        self.source_filesystem,
                        "_get_file",
                        source_entry.path,
                        temporary,
                    )
                except Exception as error:  # noqa: BLE001 - stable transfer category.
                    failure = _RecursiveCpFailure(
                        self.source,
                        "transfer failure",
                        backend_error=error,
                        residue=True,
                    )

            if failure is None:
                try:
                    staged_size = Path(temporary).stat().st_size  # noqa: ASYNC240
                except Exception as error:  # noqa: BLE001 - staging stat boundary.
                    failure = _staging_failure(self.source, error)
                else:
                    if staged_size != source_entry.size:
                        failure = _RecursiveCpFailure(
                            self.source,
                            "source changed",
                            residue=True,
                        )

            identical = False
            if failure is None and existing is not None and existing.size == source_entry.size:
                try:
                    identical = await same_contents(self.destination_filesystem, destination_path, temporary)
                except Exception as error:  # noqa: BLE001 - content verification boundary.
                    failure = _RecursiveCpFailure(
                        self.destination,
                        "verification failure",
                        backend_error=error,
                        residue=True,
                    )
                else:
                    _LOGGER.debug(
                        "content identity %s -> %s: SHA-256 of staged contents %s",
                        source_location,
                        destination_location,
                        "equal" if identical else "differ",
                    )

            if failure is None and not identical:
                try:
                    await _call(
                        self.destination_filesystem,
                        "_put_file",
                        temporary,
                        destination_path,
                        mode="overwrite",
                    )
                except Exception as error:  # noqa: BLE001 - stable mutation category.
                    failure = _RecursiveCpFailure(
                        self.destination,
                        "mutation failure",
                        backend_error=error,
                        residue=True,
                    )
        except BaseException:
            _cleanup_under_control(self.command, self.source, temporary)
            raise

        cleanup_error = _remove_staging(temporary)
        if failure is not None or cleanup_error is not None:
            return _TransferResult(failure, cleanup_error)
        _log_file_operation(
            _LOGGER,
            "skipped" if identical else "staged",
            source_location,
            destination_location,
            source_entry.size,
            started,
        )
        return _TransferResult(skipped=existing if identical else None)

    async def _create_directories(
        self,
        root: str,
        missing: tuple[_ManifestEntry, ...],
    ) -> _RecursiveCpFailure | None:
        """Create missing directories one depth at a time, parents first."""
        levels: dict[int, list[_ManifestEntry]] = {}
        for entry in missing:
            levels.setdefault(_directory_depth(entry), []).append(entry)
        for depth in sorted(levels):
            outcomes = await _run_bounded(
                [
                    partial(
                        _call,
                        self.destination_filesystem,
                        "_mkdir",
                        _destination_path(root, entry.relative),
                        create_parents=False,
                    )
                    for entry in sorted(levels[depth], key=lambda item: item.relative)
                ]
            )
            for outcome in outcomes:
                if outcome is not None and outcome.error is not None:
                    return _RecursiveCpFailure(
                        self.destination,
                        "mutation failure",
                        backend_error=outcome.error,
                        residue=True,
                    )
        return None

    def _render_transfer_failure(
        self,
        result: _TransferResult,
    ) -> _RecursiveCpFailure | None:
        """Render one transfer's diagnostics in their original order."""
        if result.failure is not None:
            _render_failure(self.command, result.failure)
        if result.cleanup_error is not None:
            _render_cleanup_failure(self.command, self.source, result.cleanup_error)
        if result.failure is not None:
            return replace(result.failure, rendered=True)
        if result.cleanup_error is not None:
            return _RecursiveCpFailure(self.source, backend_error=result.cleanup_error, rendered=True)
        return None

    def _settle_transfers(
        self,
        files: list[_ManifestEntry],
        outcomes: list[_Outcome[_TransferResult] | None],
    ) -> dict[str, _ManifestEntry] | _RecursiveCpFailure:
        """Render the first failed transfer in manifest order, or collect skips.

        A later transfer that was already in flight when the first failure was
        observed still reports its own host staging residue.
        """
        skipped: dict[str, _ManifestEntry] = {}
        first: _RecursiveCpFailure | None = None
        for entry, outcome in zip(files, outcomes, strict=True):
            if outcome is None:
                continue
            if outcome.error is not None:
                raise outcome.error
            result = cast("_TransferResult", outcome.value)
            if first is not None:
                if result.cleanup_error is not None:
                    _render_cleanup_failure(self.command, self.source, result.cleanup_error)
                continue
            first = self._render_transfer_failure(result)
            if first is None and result.skipped is not None:
                skipped[entry.relative] = result.skipped
        return first if first is not None else skipped

    async def _mutate(
        self,
        root: str,
        manifest: _Manifest,
        plan: _DestinationPlan,
    ) -> dict[str, _ManifestEntry] | _RecursiveCpFailure:
        failure = await self._create_directories(root, plan.missing_directories)
        if failure is not None:
            return failure
        files = [entry for entry in manifest if entry.kind == "file"]
        outcomes = await _run_bounded(
            [
                partial(
                    self._transfer,
                    entry,
                    _destination_path(root, entry.relative),
                    plan.files.get(_destination_path(root, entry.relative)),
                )
                for entry in files
            ],
            stop=lambda result: result.failed,
        )
        return self._settle_transfers(files, outcomes)

    async def _revalidate_source(self, frozen: _Manifest) -> _RecursiveCpFailure | None:
        try:
            current_info = await _call(
                self.source_filesystem,
                "_info",
                self.source.path,
            )
            current = await _manifest(
                self.source_filesystem,
                self.source.path,
                current_info,
            )
        except Exception as error:  # noqa: BLE001 - stable revalidation category.
            return _RecursiveCpFailure(
                self.source,
                "source revalidation failure",
                backend_error=error,
                residue=True,
            )
        if current != frozen:
            return _RecursiveCpFailure(self.source, "source changed", residue=True)
        return None

    async def _verify_destination(
        self,
        root: str,
        manifest: _Manifest,
        skipped: Mapping[str, _ManifestEntry],
    ) -> _RecursiveCpFailure | None:
        paths = [_destination_path(root, entry.relative) for entry in manifest]
        outcomes = await _run_bounded([partial(_call, self.destination_filesystem, "_info", path) for path in paths])
        try:
            for source_entry, path, outcome in zip(manifest, paths, outcomes, strict=True):
                destination_entry = _entry(
                    source_entry.relative,
                    path,
                    _outcome_value(outcome),
                    expected_kind=source_entry.kind,
                )
                proof = skipped.get(source_entry.relative)
                tokens_match = (
                    destination_entry.tokens == proof.tokens
                    if proof is not None
                    else _shared_tokens_match(source_entry.tokens, dict(destination_entry.tokens))
                )
                if destination_entry.size != source_entry.size or not tokens_match:
                    return _RecursiveCpFailure(
                        self.destination,
                        "verification failure",
                        residue=True,
                    )
        except Exception as error:  # noqa: BLE001 - stable verification category.
            return _RecursiveCpFailure(
                self.destination,
                "verification failure",
                backend_error=error,
                residue=True,
            )
        return None

    async def run(self) -> _RecursiveCpFailure | None:  # noqa: C901, PLR0911
        started = time.monotonic()
        try:
            source_info = await _call(
                self.source_filesystem,
                "_info",
                self.source.path,
            )
        except Exception as error:  # noqa: BLE001 - classify read boundary.
            return _read_failure(self.source, error)
        failure = _classify_source_info(self.source, source_info)
        if failure is not None:
            return failure

        root, root_info, failure = await self._resolve_target()
        if failure is not None:
            return failure

        try:
            manifest = await _manifest(
                self.source_filesystem,
                self.source.path,
                source_info,
            )
        except _UnsupportedEntryError as error:
            return _RecursiveCpFailure(self.source, "unsupported entry type", backend_error=error)
        except _EntryLimitError as error:
            return _RecursiveCpFailure(
                self.source,
                f"source tree exceeds {_MAX_ENTRIES} entries",
                backend_error=error,
            )
        except _IncompatibleResultError as error:
            return _RecursiveCpFailure(self.source, "incompatible result", backend_error=error)
        except Exception as error:  # noqa: BLE001 - classify walk boundary.
            return _read_failure(self.source, error)

        plan, failure = await self._preflight_destination(root, manifest, root_info)
        if failure is not None:
            return failure
        skipped = await self._mutate(root, manifest, plan)
        if isinstance(skipped, _RecursiveCpFailure):
            return skipped
        failure = await self._revalidate_source(manifest)
        if failure is not None:
            return failure
        failure = await self._verify_destination(root, manifest, skipped)
        if failure is None:
            _log_file_operation(
                _LOGGER,
                "verified",
                _location(self.source.name, self.source.path),
                _location(self.destination.name, root),
                sum(entry.size or 0 for entry in manifest),
                started,
            )
        return failure


async def _run_recursive_cp(
    command: str,
    source_operand: _MappedOperand,
    destination_operand: _MappedOperand,
    filesystems: Mapping[str, AsyncFileSystem],
) -> None:
    failure = await _RecursiveCopy(
        command,
        source_operand,
        destination_operand,
        filesystems[source_operand.name],
        filesystems[destination_operand.name],
    ).run()
    if failure is not None:
        try:
            _render_failure(command, failure)
        except Exception as error:
            raise _CommandFailureError(
                error=failure.backend_error,
                render=False,
                propagate=error,
            ) from error
        raise _CommandFailureError(error=failure.backend_error, render=False)
