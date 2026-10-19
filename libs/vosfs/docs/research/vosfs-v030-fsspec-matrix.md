# `vosfs` v0.3.0 fsspec compatibility matrix

<!-- pyml disable line-length -->

Researched: 2026-07-10
Reconciled with executable evidence: 2026-07-21
Contract target: `vosfs` v0.3.0
Client contract: fsspec 2026.6.0
Server boundary: `opencadc/vos` commit `cf976ce8141dd3341631b7f3e07aa38443d42f58`

Status: **Informative evidence.** `docs/design/trd.md` is the sole normative
v0.3.0 contract and controls if this research matrix differs.

## Status vocabulary

The status in every table mirrors the classifications in the v0.3.0 RFC.

| Code | Meaning |
| --- | --- |
| **N — native** | Backed by an implemented and tested operation in the OpenCADC VOSpace profile. |
| **D — client-derived** | Supported by composing only native operations, including a documented client-side fallback. |
| **C — extension-conditional** | Exposed only when a wired OpenCADC extension is advertised and the required behavior is verified. v0.3.0 publishes no C rows. |
| **U — unsupported** | Deliberately rejected with `NotImplementedError` because the profile lacks the required semantics, no approved derivation supplies them, or the behavior is outside the v0.3.0 product scope. |

“Supported” means externally observable behaviour is tested against fsspec 2026.6.0. It does not imply VOSpace portability beyond `opencadc/vos`, atomicity where the server does not provide it, or efficient byte ranges.

## Exact `opencadc/vos` boundary

`/transfers` is **not wholly unimplemented**. The repository mounts both asynchronous `/transfers` and synchronous `/synctrans` resources. Its runner negotiates `pushToVoSpace`, `pullFromVoSpace`, and bidirectional transfers and performs internal moves. The implementation explicitly throws for `copyNode`, `pullToVoSpace`, and `pushFromVoSpace`. v0.3.0 deliberately includes only synchronous push/pull negotiation; all asynchronous UWS resources are post-v0.3.0 even when OpenCADC implements them. ([declared endpoints](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/service.yaml#L238-L317), [direction dispatch](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/transfers/TransferRunner.java#L245-L289), [copy and move](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/transfers/InternalTransferAction.java#L120-L225), [unsupported server-initiated directions](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/transfers/PullToVOSpaceAction.java#L80-L91))

The implementation boundary available to `vosfs` is:

Every byte HEAD, GET, or PUT uses the exact endpoint returned for that logical
operation by `/synctrans`; `vosfs` never constructs or assumes a `/files` URL.

| Server surface | Proven behaviour used by v0.3.0 | Excluded behaviour |
| --- | --- | --- |
| `GET /nodes/{path}` | Node metadata and one ContainerNode's children; `detail`, `uri`, `limit`, and `view` parameters are accepted. | Cavern declares pagination unsupported and throws when batch listing is requested; v0.3.0 makes no scalable/paginated-listing claim. ([API](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/service.yaml#L107-L181), [Cavern test configuration](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/intTest/java/org/opencadc/cavern/NodesTest.java#L91-L103), [persistence rejection](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/nodes/FileSystemNodePersistence.java#L316-L334)) |
| `PUT`, `POST`, `DELETE /nodes/{path}` | Create a node, update its properties, and delete one file or empty ContainerNode. | Plain node DELETE does not remove a non-empty ContainerNode. ([API](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/service.yaml#L148-L230), [delete action](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/actions/DeleteNodeAction.java#L91-L116), [persistence](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/nodes/FileSystemNodePersistence.java#L512-L526)) |
| `/async-delete` | OpenCADC implements recursive deletion through a UWS job. | Deliberately post-v0.3.0; recursive `rm` uses client traversal and base node DELETE requests. ([runner](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/async/RecursiveDeleteNodeRunner.java#L136-L177), [tests](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-test-vos/src/main/java/org/opencadc/conformance/vos/RecursiveNodeDeleteTest.java#L145-L258)) |
| `GET`, `HEAD`, `PUT /files/{path}` | Whole-object download, metadata headers, and create-or-truncate whole-object upload through the endpoint returned by `/synctrans`. PUT creates a missing DataNode and uses `TRUNCATE_EXISTING` for its bytes. ([API](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/service.yaml#L64-L106), [whole GET](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/GetAction.java#L104-L126), [PUT](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/PutAction.java#L186-L239)) | The client never constructs `/files` URLs. No Range/Content-Range implementation, append, offset write, multipart upload, or conditional create. |
| `/transfers` internal transfer | OpenCADC implements move within the configured VOSpace. | Deliberately post-v0.3.0 with all asynchronous UWS operations; `copyNode` is also explicitly unimplemented. |
| `/synctrans` byte negotiation | Always negotiate the `pushToVoSpace` or `pullFromVoSpace` endpoint used for file writes or reads. | `pullToVoSpace` and `pushFromVoSpace`; multiple targets. |

## Filesystem method matrix

The async hook names and inherited coordinators below are those in fsspec 2026.6.0. `AsyncFileSystem` mirrors supported coroutine hooks into blocking methods when `asynchronous=False`; its default bulk methods provide bounded concurrency and callback branching. ([async contract and mirroring](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/asyn.py#L268-L349), [bulk implementations](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/asyn.py#L359-L741), [derived traversal](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/asyn.py#L745-L963))

### Metadata, listing, and traversal

| Public API / async hook | Status | v0.3.0 contract |
| --- | --- | --- |
| `info`, `stat` / `_info` | **N** | `GET /nodes`; DataNodes are `type="file"` with integer size, ContainerNodes are `type="directory"` with size `0`, and LinkNodes are `type="other"` with size `0`, `islink=True`, and their target URI. Preserve URI-keyed properties in a read-only mapping and normalize `mtime`, `md5`, and `content_type`. Missing paths raise `FileNotFoundError`. |
| `ls`, `listdir` / `_ls` | **N** | One ContainerNode listing; both `detail=True` and `False`; stable names and immediate children only. No pagination or server-side sorting claim. |
| `exists`, `lexists`, `isfile`, `isdir`, `size`, `sizes` / `_exists`, `_isfile`, `_isdir`, `_size`, `_sizes` | **D** | Derived from `_info`. `lexists` is identical to `exists` because v0.3.0 does not dereference LinkNodes. |
| `modified` | **D** | Return the node's available modification date. |
| `created` | **U** | Cavern does not expose a distinct portable creation time; raise `NotImplementedError`, not a fabricated value. |
| `walk` / `_walk` | **D** | Recursive client traversal over `_ls`; honour `maxdepth`, `topdown`, `on_error`, and detailed/non-detailed forms. |
| `find` / `_find` | **D** | Client traversal over `_walk`; honour `maxdepth`, `withdirs`, and `detail`. |
| `glob` / `_glob` | **D** | fsspec expansion over `_find`, including `*`, `[]`, and `**`; no server-side glob claim. |
| Question-mark glob paths | **U** | `?` is the existing path grammar's URL query delimiter, so these patterns cannot be expressed without adding a new path grammar. |
| `expand_path` / `_expand_path` | **D** | fsspec expansion for literal, list, glob, `recursive`, and `maxdepth` inputs. |
| `du`, `disk_usage`, `tree` / `_du` | **D** | Client traversal and node sizes; potentially expensive and unpaginated. |
| `checksum`, `ukey` | **D** | fsspec metadata token only. v0.3.0 does not promise a content checksum API even when Cavern exposes an MD5 property. |

### Reads, ranges, and local download

| Public API / async hook | Status | v0.3.0 contract |
| --- | --- | --- |
| `cat_file`, `read_bytes` / `_cat_file(path, start, end)` | **D** | Negotiate and perform one whole-object GET without a `Range` header, then return Python's half-open slice `[start:end]`. Support `None`, zero, negative start/end, empty slices, and EOF clipping. Internal LinkNodes may resolve to target bytes; external links fail as unsupported. |
| `cat_ranges` / `_cat_ranges` | **D** | Override fsspec's per-range default: group ranges by object, perform at most one negotiated whole GET per object per call, preserve order and `on_error`, and slice locally. Accept `max_gap` for call compatibility; it does not alter whole-object transfer behavior. ([default source](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/asyn.py#L457-L559)) |
| `cat` / `_cat` | **D** | fsspec path expansion plus `_cat_file`; scalar input returns bytes and expanded/list input returns a path-to-result mapping. |
| `get_file`, `download` / `_get_file` | **N** | Negotiate and stream one whole byte GET to the local target with bounded memory; byte callback updates are required. |
| `get` / `_get` | **D** | fsspec file/list/glob/recursive coordinator over `_get_file`; honour target-shape and `maxdepth` rules. |
| `open("rb")`, `open("r")` | **D** | Stage one complete object in a disk-backed temporary file, then expose a normal seekable binary file; text mode is fsspec's `TextIOWrapper`. `read`, `readinto`, `readline`, iteration, and `seek(0/1/2)` are supported. |
| `head`, `tail`, `read_block` | **D** | Derived from the seekable read object; results are correct but cause whole-object transfer on a cold open. |
| HTTP `Range`, 206, suffix-range transport | **U** | No such server capability exists in the audited `/files` implementation. Do not send or advertise remote range requests in v0.3.0. |
| `open_async` | **U** | fsspec provides no default async file object. Async consumers use `_cat_file`, `_cat_ranges`, and other coroutine hooks. ([source](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/asyn.py#L959-L969)) |

### Writes and local upload

| Public API / async hook | Status | v0.3.0 contract |
| --- | --- | --- |
| `pipe_file`, `write_bytes` / `_pipe_file(mode="overwrite")` | **N** | Negotiate one whole PUT; create a missing DataNode or truncate existing bytes. |
| `_pipe_file(mode="create")` | **D** | `_info` / `GET /nodes` preflight, then negotiate and PUT; raise `FileExistsError` when already present. The check is explicitly non-atomic. ([test](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/tests/abstract/pipe.py)) |
| `pipe` / `_pipe` | **D** | Bounded bulk coordinator over `_pipe_file`; materialize required remote parents top-down at most once per operation. |
| `put_file`, `upload` / `_put_file(mode="overwrite")` | **N** | Negotiate and stream one local file through one whole PUT with bounded memory and byte callback updates. |
| `_put_file(mode="create")` | **D** | Same non-atomic `_info` preflight semantics as `_pipe_file(mode="create")`. |
| `put` / `_put` | **D** | fsspec file/list/glob/recursive coordinator over `_put_file`; materialize required remote parents top-down at most once per operation, preserve empty directories, and honour `maxdepth`. |
| `open("wb")`, `open("w")` | **D** | A seekable disk-backed temporary writer negotiates and uploads once on successful close. No bytes are silently discarded: fsspec's base upload hooks are no-ops, so `vosfs` must provide the staging writer. ([base file contract](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/spec.py#L1849-L2110)) |
| `open("xb")`, `open("x")` | **D** | Same writer plus non-atomic existence preflight; existing target raises `FileExistsError`. ([abstract open test](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/tests/abstract/open.py)) |
| `open("ab")`, `open("a")` | **U** | Reject append. Cavern PUT truncates, and v0.3.0 does not hide a download-rewrite race behind append semantics. |
| update modes containing `+` | **U** | No read/write update or remote offset-write primitive. |

### Namespace mutation

This direct-`vosfs` API matrix does not admit a corresponding `fsspec-cli`
profile. When this was written, `rm -R`/`rm -r` were rejected outright;
they are now gated behind the `recursion.remove` capability and are off by
default. See the
[`rm` contract](../design/fss-cli/commands.md#rm) and
[why recursive removal is non-atomic](../design/fss-cli/lessons.md#11-recursive-removal-is-sequential-and-non-atomic--say-so).

| Public API / async hook | Status | v0.3.0 contract |
| --- | --- | --- |
| `mkdir(create_parents=False)` / `_mkdir` | **N** | PUT one ContainerNode; existing target and missing parent map to normal Python filesystem errors. |
| `mkdir(create_parents=True)`, `makedirs`, `mkdirs` / `_makedirs` | **D** | Create missing ancestors in order, respecting `exist_ok`; tolerate a concurrent create only when the resulting node is a ContainerNode. |
| `rm_file` / `_rm_file` | **N** | DELETE one non-container node. |
| `rm(path, recursive=True, maxdepth=None)` / `_rm` | **D** | Traverse with `_walk`, then DELETE files and empty ContainerNodes leaves-first. Lists/globs are derived over literals. The operation is non-atomic and never invokes `/async-delete`. |
| `rm(container, recursive=False)` | **D** | List first and delete through `/nodes` only when the ContainerNode is empty. A non-empty container raises `OSError`. The check and delete are non-atomic. |
| `rm(..., maxdepth=<number>)` | **U** | A root DELETE would exceed the requested depth and the server has no depth-limited delete primitive. |
| `rmdir` | **D** | List and require an empty ContainerNode, then DELETE. The emptiness check and delete are non-atomic. Non-empty raises `OSError`. |
| `cp_file` / `_cp_file` | **D** | Negotiated whole-object GET-to-PUT relay; no native `copyNode`. Overwrite an existing DataNode. Preserve bytes, not server-only properties; copying an internal LinkNode materializes its target bytes as a DataNode. |
| `copy`, `cp` / `_copy` | **D** | fsspec list/glob/recursive expansion over the relay; create containers as needed and honour `maxdepth`. There is no atomic recursive copy. |
| `_mv_file` | **D / U** | Resolve source metadata first. For a DataNode, require an absent destination, copy its bytes, then DELETE the source only after destination success; a failed source delete can leave both paths. For a LinkNode, raise `NotImplementedError` before destination checks, same-path no-op, copy, delete, rollback, or cleanup mutation. |
| `mv`, `move`, `rename` | **D / U** | Coordinate recursive copy then leaves-first source removal for DataNodes and ContainerNodes. Resolve LinkNode source metadata, then raise `NotImplementedError` before copy or delete mutation. The supported move path is non-atomic, reports partial completion, and never invokes `/transfers`. |
| move with overwrite; cross-service move | **U** | Reject an existing destination and cross-filesystem orchestration. |
| `touch(truncate=True)` | **N** | Whole PUT of zero bytes, creating or truncating the DataNode. |
| `touch(truncate=False)` | **U** | Cavern has no content-preserving timestamp-touch primitive. |

## Cross-cutting compatibility

| Capability | Status | v0.3.0 contract |
| --- | --- | --- |
| Blocking facade | **D** | `asynchronous=False` exposes fsspec-mirrored blocking methods on its dedicated IO loop. Calling those blocking methods from that same running loop remains an fsspec error. |
| Async facade | **D** | `asynchronous=True` exposes supported coroutine hooks through fsspec; no generated blocking facade is promised on that instance. |
| Service binding discovery | **N** | On first I/O, fetch and cache `endpoint_url + "/capabilities"`; resolve only nodes and synchronous transfer. Missing bindings disable only dependent methods. Do not probe or guess `/protocols`, `/views`, or `/properties`. |
| Callbacks | **D** | `get_file` and `put_file` report bytes; inherited `get`/`put` branch callbacks and report completed files. Client-relay copy reports transferred bytes. Metadata-only methods do not synthesize progress. ([callback contract](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/callbacks.py#L1-L101)) |
| Directory cache | **D** | Support fsspec `use_listings_cache`, `listings_expiry_time`, and `max_paths`; every successful mutation invalidates the affected path and all affected parents. ([cache implementation](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/dircache.py#L1-L76)) |
| Bulk mutation failure | **D** | Do not roll back already successful items. Raise an error that identifies completed and failed paths, and invalidate caches only for mutations that reached success. |
| Error translation | **D** | Map invalid input, access denial, missing paths, conflicts, unsupported operations, quota, and lock/busy faults to the contract's standard Python exceptions. Use one public `VOSpaceError(OSError)` carrying status, fault, retry guidance, and partial completion for remaining failures. |
| Per-open `block_size`, `cache_type`, and `cache_options` | **D** | Accept them for fsspec call compatibility, but the staged reader performs one whole download and does not claim remote block/range efficiency. |
| `simplecache::vos://`, `filecache::vos://` | **D** | `simplecache` supports whole-file reads and writes; `filecache` is a whole-file read claim only. ([fsspec source](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/cached.py#L545-L825)) |
| `blockcache::vos://`, `cached::vos://` | **U** | Unsupported because these wrappers are block/range-oriented while the server supplies only whole GET. |
| Pickle / `__reduce__` | **D** | Round-trip constructor args/options only; never serialize a live HTTP client, loop, lock, or response. |
| `to_json`/`from_json`, `to_dict`/`from_dict` | **D** | Round-trip in a fresh process using primitive `endpoint_url`, `token`, `tokenfile`, `certfile`, timeout, trust, async, and cache options. Literal tokens serialize with a warning; environment secrets are re-resolved. ([fsspec serialization](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/spec.py#L1444-L1579), [JSON encoder](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/json.py#L1-L42)) |
| Dask tokenization | **D** | Deterministic `__dask_tokenize__` inherited from the constructor token; changing storage options changes the token. ([fsspec source](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/spec.py#L147-L193)) |
| Lifecycle | **D** | Retain `cachable=True`. Idempotent `aclose()` and blocking `close()` own all HTTP resources, evict the closed instance from fsspec's instance cache, and make later I/O fail as closed. Reconstruction creates fresh clients, loops, locks, bindings, and caches. |
| VOSpace-only metadata mutations | **U** | No public link-creation, generic property-write, permission-mutation, `/async-setprops`, or `/pkg` API is part of the v0.3.0 fsspec surface. |

## Scientific-stack acceptance matrix

These are intentionally narrow, executable compatibility claims. They do not imply that every IO function in each project accepts `vos://`.

| Consumer seam | Status | v0.3.0 release gate and boundary |
| --- | --- | --- |
| pandas | **D** | `pandas.read_csv("vos://...", storage_options=...)` and `DataFrame.to_csv("vos://...", storage_options=...)` in a fresh process. pandas routes non-HTTP remote URLs through fsspec. No blanket Excel/SQL/Parquet-engine claim. ([pandas remote files](https://pandas.pydata.org/pandas-docs/stable/user_guide/io.html#reading-writing-remote-files)) |
| NumPy | **D** | Round-trip `.npy` and `.npz` through `fs.open(..., "rb"/"wb")`; `loadtxt` through a file object. `numpy.load("vos://...")` and remote `mmap_mode` are **U** because NumPy accepts a filesystem path or file-like object but does not dispatch arbitrary fsspec URLs. ([`numpy.load`](https://numpy.org/doc/stable/reference/generated/numpy.load.html), [`numpy.save`](https://numpy.org/doc/stable/reference/generated/numpy.save.html)) |
| Dask | **D** | `dask.dataframe.read_csv` and `to_csv` through a fresh worker process with `blocksize=None`. Distributed pickle/token reconstruction is mandatory. Partitioned remote range reads are **U** because v0.3.0 has no efficient remote ranges. ([Dask remote data](https://docs.dask.org/en/stable/how-to/connect-to-remote-data.html)) |
| Zarr v3 `FsspecStore` | **D** | Create, read, overwrite, list, delete, and partial-value reads against the native async filesystem. Partial reads are correct through `_cat_file`/`_cat_ranges` but transfer whole objects. Zarr calls these async hooks and uses fsspec JSON reconstruction when adapting a synchronous filesystem. ([Zarr source](https://github.com/zarr-developers/zarr-python/blob/31817c681dc747d4d723af072e9562c624def553/src/zarr/storage/_fsspec.py#L60-L90), [read hooks](https://github.com/zarr-developers/zarr-python/blob/31817c681dc747d4d723af072e9562c624def553/src/zarr/storage/_fsspec.py#L330-L460)) |
| PyArrow/Parquet | **D** | `pyarrow.fs.PyFileSystem(pyarrow.fs.FSSpecHandler(fs))` dataset discovery plus Parquet read/write. Footer seeks operate on the whole-object staged reader. Append stream is **U**. The handler also exercises info/find/mkdir/rm/mv/copy/open, so those gates are not optional. ([PyArrow `FSSpecHandler`](https://arrow.apache.org/docs/_modules/pyarrow/fs.html#FSSpecHandler)) |
| `fsspec.fuse` | **U** | No v0.3.0 mount claim. The adapter requires a seekable write object for offset writes and calls `info`, `ls`, `mkdir`, `rmdir`, `touch`, `open`, and `rm`; a real mount and failure-semantics suite is required before support can be published. ([fsspec FUSE adapter](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/fuse.py#L20-L148)) |

## Executable release gate

v0.3.0 is compatible only when all **N** and **D** rows above pass against an `opencadc/vos` service and all **U** rows fail with the documented exception without destructive side effects. The suite must:

1. run every applicable reusable fsspec 2026.6.0 abstract test, skipping only cases mapped to **U** here and recording each skip by matrix row ([abstract suite](https://github.com/fsspec/filesystem_spec/tree/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/tests/abstract));
2. run every supported coroutine hook through `asynchronous=True`, every mirrored public method through the `asynchronous=False` facade, and staged `open()` through its supported synchronous seam;
3. prove whole-object fallback semantics with a server that rejects any `Range` header, including half-open, suffix, empty, and out-of-bounds slices;
4. prove non-atomic create/exclusive and `rmdir` races are documented and never broaden into silent overwrite or recursive deletion;
5. run the five supported scientific-stack rows exactly as scoped above; and
6. assert that FUSE remains unsupported rather than reporting it as untested support.

This matrix is the complete v0.3.0 fsspec/scientific-stack capability boundary. Broader VOSpace portability, true ranged reads, conditional writes, append/update, native copy, cross-service transfer, block caching, and FUSE are future contracts.

At the 2026-07-21 reconciliation, the hermetic suite collected 592 tests: 586
passed and six skipped. The reusable fsspec 2026.6.0 subset collected 137 cases:
131 supported cases passed and the same six question-mark glob rows skipped
with the path-grammar reason above. Copy, get, and put each contribute the same
two `fil?1` rows (non-recursive and recursive). The list-source get hashed-name
case and every missing-parent put case pass; coordinated missing-parent pipe
and put behavior is covered by dedicated hermetic tests.
