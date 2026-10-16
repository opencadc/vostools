# User Guide

`vosfs` is an asynchronous [`fsspec`](https://filesystem-spec.readthedocs.io/)
filesystem for the **OpenCADC Cavern VOSpace** service. It exposes the `vos`
protocol so fsspec-aware Python tools can read, write, inspect, and mutate
OpenCADC VOSpace paths.

!!! warning "OpenCADC profile only"

    `vosfs` targets the OpenCADC VOSpace profile. It does **not** claim generic
    IVOA VOSpace 2.1 conformance or compatibility with unrelated VOSpace
    implementations.

## Constructing a filesystem

The service is selected only by `endpoint_url`, an absolute OpenCADC service
base URL. No registry or shortname lookup is performed.

Install `vosfs` first:

```bash
uv add "git+https://github.com/opencadc/vostools@main#subdirectory=libs/vosfs"
```

```python
import fsspec

fs = fsspec.filesystem(
    "vos",
    endpoint_url="https://staging.canfar.net/arc",
    max_connections=100,
)
```

`max_connections` (default 100) is the number of HTTP requests, including
streamed bodies, that one filesystem will run at once. A caller that omits it
gets that default. The value is part of the filesystem's stored settings.

The apparent authority after the `vos://` protocol marker is part of the
filesystem path, not a service selector: `vos://a/b`, `vos:///a/b`, `/a/b`, and
`a/b` all refer to the same path `/a/b`.

## Credentials

At most one credential source may be configured, and each is reread before
every authenticated request. `vosfs` consumes access tokens but never acquires
or refreshes them.

| Option | Meaning |
| --- | --- |
| `token` | A literal bearer token (including an OIDC access token). |
| `tokenfile` | A file whose bearer token is reread before every request. |
| `certfile` | A combined X.509 certificate-chain and private-key PEM file. |

If no credential option is given, `vosfs` falls back to exactly one of the
environment variables `VOSFS_TOKEN`, `VOSFS_TOKEN_FILE`, or `VOSFS_CERT_FILE`.
Providing any explicit credential option ignores all of these variables. With
no credential at all, access is anonymous. `endpoint_url` must use `https`
whenever a credential is configured.

!!! note "Serialization"

    A literal `token` is kept for pickle and for `to_json()` / `to_dict()`.
    `to_json(include_password=False)` omits it. Prefer `tokenfile` or an
    environment source when the filesystem may be serialized.

```python
fs = fsspec.filesystem(
    "vos",
    endpoint_url="https://staging.canfar.net/arc",
    tokenfile="/run/secrets/vos-token",
)
```

For a CADC proxy certificate, pass an absolute path to the combined
certificate-chain and private-key PEM file, or set the equivalent environment
variable before constructing the filesystem:

```bash
export VOSFS_CERT_FILE=/absolute/path/to/cadcproxy.pem
```

```python
fs = fsspec.filesystem(
    "vos",
    endpoint_url="https://staging.canfar.net/arc",
    certfile="/absolute/path/to/cadcproxy.pem",
)
```

Do not pass both forms: an explicit credential option ignores all credential
environment variables. Paths beginning with `~` are not expanded; use an
absolute path.

## Reading and writing

Partial `cat_file` / `cat_ranges` reads send HTTP `Range` when the bounds allow.
If the negotiated byte endpoint returns a validated `206`, only those bytes are
kept. If it returns `200`/`204` (Cavern ignores `Range`), `vosfs` treats the
body as a whole object and slices locally. Staged `open` still downloads the
whole object into a disk-backed temporary file. Writes issue one whole `PUT`.

```python
with fs.open("/project/data.csv", "rb") as handle:
    header = handle.readline()

fs.pipe_file("/project/notes.txt", b"hello")
fs.get("/project/data.csv", "local.csv")
fs.put("local.csv", "/project/copy.csv")
```

Download a complete remote tree with the same public method:

```python
fs.get("/project/results", "local-results", recursive=True)
```

Recursive `get` is client-derived. `vosfs` traverses the remote
`ContainerNode` tree client-side, materializes every container as a local
directory (including empty containers), and downloads each `DataNode` through
the normal whole-object read path.

Coordinated `put` and `pipe` operations create any required remote
`ContainerNode` parents before writing descendant bytes. A recursive `put`
also reproduces the local directory tree, including empty directories:

```python
fs.put("local-results", "/project/archive/results", recursive=True)
fs.pipe(
    {
        "/project/generated/a.txt": b"a",
        "/project/generated/b.txt": b"b",
    }
)
```

Each destination file still uses one negotiated whole-object `PUT`; parent
creation is scoped to the coordinated operation and does not change the
single-file `put_file` or `pipe_file` hooks. Cancelling coordinated `put` or
`pipe` stops later batch items and waits for started writes to close. A
dispatched `PUT` remains uncertain, so `vosfs` invalidates cached destination
and parent state without attempting rollback.

`open("rb")` downloads once into a disk-backed temporary file and then provides
local `read`/`readinto`/`readline`/iteration/`tell`/`seek`. Pass `block_size`
or `cache_type` to open a ranged file instead: that open negotiates one byte
endpoint and reads it with `Range`. A `206` is accepted only when its interval
and byte count match the request. A `200` stages that response and later reads
of the same open stay local. An unknown byte length also uses staging; a link's
metadata size does not describe the bytes of its target. `open("wb")` and
`open("w")` disk-stage writes and upload once only after a successful close or
clean context exit. A write-block exception or local close failure before `PUT`
uploads nothing. Once `PUT` begins, failure is uncertain and may have truncated
the destination.

Same-service moves can use a server job when available. If the operation is
unsupported, vosfs copies through the client and verifies the destination
before deleting the source. Cancelling or timing out a server job requests its
abort; an uncertain outcome does not start another transfer. Client copies
also work with `max_connections=1` by staging the download before uploading.

## Scientific Python stack

Because `vosfs` registers the `vos` protocol, any fsspec-aware tool can read and
write `vos://` URLs directly. Pass the endpoint (and at most one credential) as
`storage_options`, or construct a `VOSpaceFileSystem` and hand it to the tool.

!!! tip "`storage_options` mirrors the constructor"

    Every keyword accepted by `VOSpaceFileSystem` — `endpoint_url`, `token`,
    `tokenfile`, `certfile`, `timeouts` — is valid inside `storage_options`.
    Omit the credential to resolve it from `VOSFS_TOKEN`, `VOSFS_TOKEN_FILE`, or
    `VOSFS_CERT_FILE` in the environment.

=== "pandas"

    ```python
    import pandas as pd

    storage_options = {"endpoint_url": "https://staging.canfar.net/arc"}

    frame = pd.DataFrame({"a": [1, 2, 3], "b": ["x", "y", "z"]})
    frame.to_csv("vos://project/data.csv", index=False, storage_options=storage_options)

    restored = pd.read_csv("vos://project/data.csv", storage_options=storage_options)
    ```

=== "NumPy"

    Save and load through an fsspec file object: `.npy` and `.npz` round-trip,
    and delimited text is read with `np.loadtxt`. Any NumPy reader or writer
    that accepts a file object works the same way.

    ```python
    import fsspec
    import numpy as np

    storage_options = {"endpoint_url": "https://staging.canfar.net/arc"}
    array = np.arange(12, dtype="int64").reshape(3, 4)

    with fsspec.open("vos://project/a.npy", "wb", **storage_options) as handle:
        np.save(handle, array)

    with fsspec.open("vos://project/a.npy", "rb", **storage_options) as handle:
        restored = np.load(handle)
    ```

=== "Dask"

    ```python
    import dask.dataframe as dd

    storage_options = {"endpoint_url": "https://staging.canfar.net/arc"}

    # blocksize=None: staged open is whole-object, so each file is one partition.
    lazy = dd.read_csv("vos://project/d.csv", storage_options=storage_options, blocksize=None)
    result = lazy.compute()
    ```

=== "Zarr"

    Zarr v3 reads and writes each chunk as a whole object. Use an asynchronous
    filesystem for the store (requires Python 3.11+).

    ```python
    import numpy as np
    import zarr

    from vosfs import VOSpaceFileSystem

    fs = VOSpaceFileSystem(
        endpoint_url="https://staging.canfar.net/arc",
        asynchronous=True,
    )
    store = zarr.storage.FsspecStore(fs, path="/project/array.zarr")

    root = zarr.open_group(store=store, mode="w")
    data = root.create_array("data", shape=(10,), dtype="int32")
    data[:] = np.arange(10, dtype="int32")

    reopened = zarr.open_group(store=store, mode="r")
    assert reopened["data"][3:6].tolist() == [3, 4, 5]
    ```

=== "PyArrow"

    Wrap the filesystem in a `PyFileSystem` and address paths without the
    `vos://` prefix.

    ```python
    import pyarrow as pa
    import pyarrow.parquet as pq
    from pyarrow.fs import FSSpecHandler, PyFileSystem

    from vosfs import VOSpaceFileSystem

    fs = VOSpaceFileSystem(endpoint_url="https://staging.canfar.net/arc")
    pa_fs = PyFileSystem(FSSpecHandler(fs))

    table = pa.table({"a": [1, 2, 3], "b": ["x", "y", "z"]})
    pq.write_table(table, "/project/data.parquet", filesystem=pa_fs)
    restored = pq.read_table("/project/data.parquet", filesystem=pa_fs)
    ```

!!! note "Whole-object staged ``open``"

    The default ``open`` still downloads whole objects. Pass ``block_size`` or
    ``cache_type`` for ranged reads of one open file. Formats that rely on
    random access work by staging locally. Partial ``cat_file`` /
    ``cat_ranges`` may use HTTP ``Range`` when the byte endpoint returns
    ``206``. For Dask over staged files, pass ``blocksize=None`` so each file
    is a single partition.

## Capability summary

Every public behavior carries exactly one classification.

!!! abstract "Normative source"

    The tables below are a reader-friendly digest. The full, authoritative
    capability matrix lives in the
    [capability contract](https://github.com/opencadc/vostools/blob/main/libs/vosfs/docs/design/trd.md);
    where the two differ, the contract wins.

### Supported

Native operations and the client-derived behaviors composed from them.

<div class="grid cards" markdown>

-   :material-magnify:{ .lg .middle } __Inspect__

    ---

    `info` · `ls` · `exists` · `isfile` · `isdir` · `size` · `modified`

-   :material-file-tree:{ .lg .middle } __Traverse__

    ---

    Client-derived from `ls`:

    `walk` · `find` · `glob` · `du` · `tree`

-   :material-download:{ .lg .middle } __Read__

    ---

    `cat` · `cat_file` · `cat_ranges` · `head` · `tail` · `read_block` · `get` · `get_file`

    Whole-object transfer; ranges are sliced client-side.

-   :material-upload:{ .lg .middle } __Write__

    ---

    `pipe` · `pipe_file` · `put` · `put_file`

    One whole `PUT` per file.

-   :material-file-document-edit-outline:{ .lg .middle } __Open__

    ---

    `open("rb" / "r" / "wb" / "w" / "xb" / "x")`

    Backed by a disk-staged temporary file.

-   :material-folder-plus-outline:{ .lg .middle } __Directories__

    ---

    `mkdir` · `makedirs`

-   :material-delete-outline:{ .lg .middle } __Delete__

    ---

    `rm` · `rmdir` · `rm_file`

    Empty-check or leaves-first client deletion.

-   :material-content-copy:{ .lg .middle } __Copy__

    ---

    `cp` · `copy` · `cp_file`

-   :material-swap-horizontal:{ .lg .middle } __Move__

    ---

    `mv` · `move` · `rename`

    DataNode/ContainerNode: non-atomic copy/recreate then delete, no overwrite.

    LinkNode: unsupported; raises `NotImplementedError` before mutation.

-   :material-fingerprint:{ .lg .middle } __Identity & metadata__

    ---

    `touch(truncate=True)` · `checksum` · `ukey`

-   :material-package-variant:{ .lg .middle } __Serialization__

    ---

    `pickle` · `to_json`

-   :material-cached:{ .lg .middle } __Cache wrappers__

    ---

    `simplecache::vos://` · `filecache::vos://`

</div>

### Unsupported

!!! failure "These raise `NotImplementedError` before any remote mutation, or are absent entirely"

    The filesystem never silently degrades — an unsupported call fails fast.

| Operation / feature | Why |
| --- | --- |
| `blockcache::` / `cached::` wrappers | Not claimed; use `simplecache::` / `filecache::` |
| Append, `+` mode, offset / atomic / resumable / multipart writes | One whole `PUT` per file |
| `touch(truncate=False)` | Would require a partial update |
| `rm(..., maxdepth=...)` | Bounded-depth deletion is not modeled |
| `created` timestamps, `open_async` | Not provided by the profile |
| Server-side copy, move, search, sort, pagination | Absent from the OpenCADC profile |
| Public property, permission, and link-creation APIs | Out of v0.3.0 scope |
| FUSE mounting | Not supported |

## Errors

Failures map to the closest standard Python exception. Anything without a
precise match is raised as the single public `vosfs.VOSpaceError`.

!!! tip "Looking up a failure you actually hit?"

    [Troubleshooting](troubleshooting.md) is organized by symptom rather than by
    exception class.

| Exception | Raised when |
| --- | --- |
| `ValueError` | Invalid input — a malformed path, bad option, or oversized/ill-formed XML |
| `PermissionError` | Authentication or authorization was refused |
| `FileNotFoundError` | A referenced node or its parent does not exist |
| `FileExistsError` | The target already exists and the operation will not overwrite |
| `NotImplementedError` | The operation is unsupported, or the service advertises no matching binding |
| `OSError` (`errno.ENOSPC`) | A storage quota is exhausted |
| `BlockingIOError` | The node is locked |
| `TimeoutError` | An HTTP request exceeded its timeout |
| `ConnectionError` | The underlying connection failed |
| `vosfs.VOSpaceError` | Every remaining OpenCADC, integrity, HTTP, and partial-completion failure |

!!! info "`vosfs.VOSpaceError`"

    A subclass of `OSError`. It carries the HTTP status, a symbolic fault code,
    retry guidance, and — for partial failures — the completed and failed paths.

!!! note "Redaction and retries"

    Credentials and pre-authorized URL tokens are redacted from every exception
    and log. `vosfs` never retries automatically; callers own higher-level retry
    policy.

## Logging

`vosfs` reports transfer progress through the standard-library `logging`
module under the `vosfs` logger. It installs only a `NullHandler`, so nothing
is printed unless your application configures logging.

- `INFO`: one record per completed file operation, for example
  `downloaded /data/a.fits -> /tmp/a.fits (2880 bytes, 0.214 s)`. Outcomes are
  `downloaded`, `uploaded`, `copied`, `staged` (an `open("rb")` download),
  `removed`, and `moved`. Each record also carries the structured attributes
  `vosfs_outcome`, `vosfs_source`, `vosfs_destination`, `vosfs_bytes`, and
  `vosfs_seconds` where they apply.
- `DEBUG`: the negotiated byte endpoint and security method, Range fallbacks,
  and MD5 comparisons after uploads.

Logged URLs never include a query string or fragment, and pre-authorized
tokens are redacted.

```python
import logging

logging.basicConfig(level=logging.INFO)
logging.getLogger("vosfs").setLevel(logging.DEBUG)
```

## Closing

`aclose()` (async) and `close()` (sync) release every HTTP client, evict the
instance from fsspec's cache, and make later I/O fail as closed. Both calls are
idempotent.

A live filesystem belongs to the process that constructed it. Do not use an
inherited instance after `fork()`; reconstruct it in the child or worker from
pickle, fsspec JSON, or the original storage options. Reconstruction creates
fresh event-loop, lock, HTTP-client, service-binding, and cache state. Dask
tokenization depends only on the serialized constructor options, not that live
runtime state.

Close a filesystem deterministically when its work is finished:

```python
fs = fsspec.filesystem("vos", endpoint_url="https://staging.canfar.net/arc")
try:
    data = fs.cat_file("/project/data.csv")
finally:
    fs.close()
```
