# Portable fsspec capability floor for plain `ls`

<!-- pyml disable line-length -->

Researched: 2026-07-15

Question: [shinybrar/vosfs#76](https://github.com/shinybrar/vosfs/issues/76)

Client contract: fsspec 2026.6.0

Status: **Decision evidence.** This document identifies the portable backend
surface for issue #79 to turn into the plain-`ls` command compatibility profile.

## Answer

A generic plain `ls` is viable without branching on filesystem type. The
portable public surface is synchronous `info(path)` and
`ls(path, detail=False)`. All three initial backends provide both operations.

The command must pass `detail=False` explicitly: `LocalFileSystem` defaults it
to false, while `MemoryFileSystem`, `AbstractFileSystem`, and
`VOSpaceFileSystem` default it to true. Backend order is not portable.

Two file-operand strategies remain viable:

| Strategy | Operations | Evidence and tradeoff |
| --- | --- | --- |
| Direct listing | Call `ls(path, detail=False)` for every operand. | One call; file operands return a singleton on all three initial backends. The abstract `ls` contract does not explicitly guarantee that behavior. |
| Classify, then list | Call `info(path)`; call names-only `ls` only when `type == "directory"`; otherwise use the returned `name`. | Uses documented metadata to distinguish files from directories, but adds an operation for directory operands. |

Issue #79 owns this selection because it owns file-versus-directory behavior.
This research does not choose between them.

Both strategies require public methods that complete synchronously. An
`asynchronous=True` instance is outside the initial floor. Neither strategy
needs `exists`, `isdir`, `isfile`, private hooks, traversal, or backend-specific
fallbacks.

## Result floor

fsspec 2026.6.0 defines detailed entries with three fields:

| Field | Contract |
| --- | --- |
| `name` | Full filesystem path without protocol. |
| `type` | `"file"`, `"directory"`, or another backend type. |
| `size` | Byte size, or `None` when unknown. |

Names-only `ls` returns `list[str]`; `[]` is a valid empty-directory result.
Returned names are opaque backend paths until presentation. The command passes
the resolved mapped path unchanged.

Plain `ls` does not need `size` or optional metadata. Local adds mode, UID, GID,
and timestamps. Memory omits mode, UID, GID, and `mtime` but includes `created`.
`vosfs` may add OpenCADC VOSpace profile metadata. Those fields cannot affect
plain output.

## Capability and error floor

`AbstractFileSystem` has no portable command-capability registry. Static checks
are insufficient because its callable `ls` method raises `NotImplementedError`.
Executing the real operation is the probe: `NotImplementedError` means the
selected strategy is unavailable, and malformed consumed fields mean the
implementation is incompatible. Neither result triggers a retry, emulation, or
fabricated output.

The abstract `ls` contract does not standardize exceptions. Missing operands
raise `FileNotFoundError` on all three initial backends, but a descendant below
a file is `NotADirectoryError` locally and `FileNotFoundError` in Memory and
`vosfs`. The command may recognize standard exceptions when supplied, but must
not infer meaning from errno values or messages.

| Backend result | Portable classification |
| --- | --- |
| `NotImplementedError` | Selected command strategy unavailable. |
| `FileNotFoundError` | Operand not found. |
| `PermissionError` | Access denied. |
| `NotADirectoryError` | Invalid path through a non-directory, when distinguished. |
| Other backend exception | Backend operation failed; preserve the cause. |
| Wrong consumed shape | Incompatible implementation. |

Exact diagnostics, multi-operand continuation, stdout, sorting, and exit status
remain issue #79 decisions.

## Initial backend evidence

Hermetic probes used Python 3.13.5 and fsspec 2026.6.0. Local and Memory used
temporary in-process data. `vosfs` used the repository's `respx`/
`MockTransport` simulator, whose unmatched requests cannot reach the network.

| Behavior | Local | Memory | `vosfs` |
| --- | --- | --- | --- |
| `info` supplies `name`, `type`, and `size` | Yes | Yes | Yes |
| `ls(directory, detail=False)` returns immediate full paths | Yes | Yes | Yes |
| `ls(file, detail=False)` returns a singleton | Yes | Yes | Yes |
| Empty directory returns `[]` | Yes | Yes | Yes |
| Missing operand raises `FileNotFoundError` | Yes | Yes | Yes |
| Listing order is guaranteed | No | No | No |
| Default instance offers synchronous `info` and `ls` | Yes | Yes | Yes |

Local preserves `os.scandir` order, Memory sorts names-only results, and
`vosfs` preserves the server document order. Directory sizes and optional
metadata also differ. Sorting and detailed fields therefore belong to their
own command-profile decisions.

For uncached `vosfs`, direct `ls` reads one node document after any first-I/O
capability discovery. The classify-then-list strategy reads two node documents
for a directory because `info` does not populate the listing cache.

## Primary sources

- fsspec 2026.6.0 [`AbstractFileSystem.ls`](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/spec.py#L326-L365) and [`info`](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/spec.py#L682-L714)
- fsspec 2026.6.0 [`LocalFileSystem.ls`](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/local.py#L59-L76) and [`MemoryFileSystem.ls`](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/memory.py#L43-L105)
- vosfs [`_info`, `_ls`, and `_fetch_listing`](https://github.com/shinybrar/vosfs/blob/a5d31ee3c47d105ca22c3e51b09d046db207b04d/src/vosfs/filesystem.py#L245-L277) and [`to_info`](https://github.com/shinybrar/vosfs/blob/a5d31ee3c47d105ca22c3e51b09d046db207b04d/src/vosfs/nodes.py#L173-L206)
- vosfs [error mapping](https://github.com/shinybrar/vosfs/blob/a5d31ee3c47d105ca22c3e51b09d046db207b04d/src/vosfs/errors.py#L243-L312), [path normalization](https://github.com/shinybrar/vosfs/blob/a5d31ee3c47d105ca22c3e51b09d046db207b04d/src/vosfs/paths.py#L20-L56), and [hermetic transport boundary](https://github.com/shinybrar/vosfs/blob/a5d31ee3c47d105ca22c3e51b09d046db207b04d/tests/conftest.py#L1-L7)
