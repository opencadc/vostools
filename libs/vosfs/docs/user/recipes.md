# Recipes

Task-first snippets for common scientific-Python work against VOSpace. Each one
is complete and runnable once you have a credential configured — see
[Getting a credential](troubleshooting.md#getting-a-credential) if you do not.

All of them assume:

```python
import fsspec

STORAGE = {"endpoint_url": "https://staging.canfar.net/arc"}
```

## Read a CSV into pandas

```python
import pandas as pd

frame = pd.read_csv("vos://project/data.csv", storage_options=STORAGE)
```

Writing back is symmetric:

```python
frame.to_csv("vos://project/derived.csv", index=False, storage_options=STORAGE)
```

## Upload a directory of files

```python
fs = fsspec.filesystem("vos", **STORAGE)
try:
    fs.put("./reduced", "/project/reduced", recursive=True)
finally:
    fs.close()
```

Parent containers are created automatically, and empty directories are
preserved. Each file is one whole `PUT`.

## Download a whole remote tree

```python
fs = fsspec.filesystem("vos", **STORAGE)
try:
    fs.get("/project/results", "./results", recursive=True)
finally:
    fs.close()
```

## Read a FITS file

`astropy` needs a seekable file object. `vosfs` stages the object to a local
temporary file on open, so ordinary `fits.open` works:

```python
from astropy.io import fits

with fsspec.open("vos://project/image.fits", "rb", **STORAGE) as handle:
    with fits.open(handle) as hdul:
        header = hdul[0].header
        data = hdul[0].data
```

!!! note "Staged ``open`` downloads the whole file"

    ``fits.open`` over ``fsspec.open`` uses staged ``open``, which is still a
    whole-object download. For header-only access on range-capable backends,
    prefer ``cat_file`` / ``cat_ranges`` with explicit bounds.

## Process many files without loading them all

```python
fs = fsspec.filesystem("vos", **STORAGE)
try:
    for path in fs.glob("/project/night-*/calib.fits"):
        with fs.open(path, "rb") as handle:
            process(handle)          # staged to a temp file, then removed
finally:
    fs.close()
```

## Write results as Parquet

```python
import pyarrow as pa
import pyarrow.parquet as pq
from pyarrow.fs import FSSpecHandler, PyFileSystem

from vosfs import VOSpaceFileSystem

fs = VOSpaceFileSystem(**STORAGE)
pa_fs = PyFileSystem(FSSpecHandler(fs))

table = pa.table({"ra": [10.1, 10.2], "dec": [41.0, 41.1]})
pq.write_table(table, "/project/catalog.parquet", filesystem=pa_fs)
```

Note that PyArrow paths omit the `vos://` prefix.

## Checkpoint an array to Zarr

```python
import numpy as np
import zarr

from vosfs import VOSpaceFileSystem

fs = VOSpaceFileSystem(**STORAGE, asynchronous=True)
store = zarr.storage.FsspecStore(fs, path="/project/cube.zarr")

root = zarr.open_group(store=store, mode="w")
array = root.create_array("cube", shape=(100, 100), dtype="float32")
array[:] = np.random.default_rng(0).random((100, 100), dtype="float32")
```

Requires Python 3.11+. Each chunk is one whole object, so prefer larger chunks
than you would use on local disk.

## Read a big table with Dask

```python
import dask.dataframe as dd

# blocksize=None: staged open is whole-object, so each file is one partition.
lazy = dd.read_csv(
    "vos://project/night-*/phot.csv",
    storage_options=STORAGE,
    blocksize=None,
)
result = lazy.groupby("filter").flux.mean().compute()
```

!!! warning "Always pass `blocksize=None`"

    Without it Dask tries to split files by byte offset, which Cavern cannot
    serve. One file per partition is the correct model here.

## Cache remote files between runs

For a script you run repeatedly over the same inputs, let fsspec cache them
locally:

```python
with fsspec.open(
    "filecache::vos://project/big.fits",
    "rb",
    vos=STORAGE,
    filecache={"cache_storage": "/tmp/vos-cache"},
) as handle:
    process(handle)
```

`simplecache::` and `filecache::` both work. `blockcache::` and `cached::` are
**not** claimed even when the byte endpoint honours `Range`.

## Check before you write

```python
fs = fsspec.filesystem("vos", **STORAGE)
try:
    if fs.exists("/project/output.csv"):
        raise SystemExit("refusing to overwrite existing output")
    fs.pipe_file("/project/output.csv", payload)
finally:
    fs.close()
```

## Use it from a script that must not hang

Close deterministically when the work is done, and reconstruct rather than
inherit across processes:

```python
fs = fsspec.filesystem("vos", **STORAGE)
try:
    data = fs.cat_file("/project/data.csv")
finally:
    fs.close()
```

!!! danger "Never share a filesystem across `fork()`"

    A live filesystem belongs to the process that built it. In a child process
    or worker, reconstruct it from the same storage options (or from pickle /
    fsspec JSON) instead of inheriting the instance.
