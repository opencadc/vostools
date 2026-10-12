# vosfs

`vosfs` is an asynchronous [`fsspec`](https://filesystem-spec.readthedocs.io/)
filesystem for the OpenCADC Cavern VOSpace service. It registers the `vos`
protocol so fsspec-aware Python tools — and a bounded set of scientific-stack
consumers (pandas, NumPy, Dask, Zarr, PyArrow) — can read, write, inspect, and
mutate OpenCADC VOSpace paths.

`vosfs` targets the OpenCADC VOSpace profile only; it does not claim generic
IVOA VOSpace 2.1 conformance.

## Install

```bash
uv add "git+https://github.com/opencadc/vostools@main#subdirectory=libs/vosfs"
```

## Authenticated quickstart

Set exactly one credential source. A CADC proxy certificate is a combined
certificate-chain and private-key PEM file; use an absolute path because shell
shortcuts such as `~` are not expanded by Python libraries.

```bash
export VOSFS_CERT_FILE=/absolute/path/to/cadcproxy.pem
```

The normal synchronous fsspec API is available even though `vosfs` implements
network I/O asynchronously.

```python
import fsspec

fs = fsspec.filesystem("vos", endpoint_url="https://staging.canfar.net/arc")
try:
    print(fs.ls("/home/<cadc-username>"))
finally:
    fs.close()
```

!!! tip "No credential yet?"

    A CADC proxy certificate comes from `cadc-get-cert`. See
    [Getting a credential](troubleshooting.md#getting-a-credential).

## Where to go next

- The [User Guide](guide.md) covers construction, credentials, and the
  supported and unsupported behavior.
- [Recipes](recipes.md) are task-first snippets: read a CSV into pandas, upload
  a directory, open a FITS file, checkpoint to Zarr.
- [Troubleshooting](troubleshooting.md) is organized by the error you saw.
- The [API Reference](api-reference.md) documents the public interface.
- [fss-cli](cli/index.md) turns this (or any async fsspec filesystem) into
  shell-shaped commands inside your own CLI.
- The normative surface is the capability contract in
  [`libs/vosfs/docs/design/trd.md`](https://github.com/opencadc/vostools/blob/main/libs/vosfs/docs/design/trd.md).

To contribute, follow the
[contributor guide](https://github.com/opencadc/vostools/blob/main/CONTRIBUTING.md).
