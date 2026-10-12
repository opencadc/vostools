# vosfs

[![CI](https://github.com/opencadc/vostools/actions/workflows/quality.yml/badge.svg)](https://github.com/opencadc/vostools/actions/workflows/quality.yml)
[![Python](https://img.shields.io/badge/python-3.10%2B-blue)](https://www.python.org/)
[![License: AGPL-3.0-or-later](https://img.shields.io/badge/license-AGPL--3.0--or--later-blue)](LICENSE)

`vosfs` is an asynchronous [`fsspec`](https://filesystem-spec.readthedocs.io/)
filesystem for the **OpenCADC Cavern VOSpace** service: it registers the `vos`
protocol so fsspec-aware Python tools — and pandas, NumPy, Dask, Zarr, and
PyArrow — can read, write, inspect, and mutate OpenCADC VOSpace paths.

Supported host platforms are Linux and macOS. Other platforms are untested and
unsupported.

## Install

```bash
uv add "git+https://github.com/opencadc/vostools@main#subdirectory=libs/vosfs"
```

## Quickstart

The normal synchronous fsspec API is available even though network I/O is
implemented asynchronously. For an authenticated first request, point
`VOSFS_CERT_FILE` at a combined proxy-certificate and private-key PEM file:

```bash
export VOSFS_CERT_FILE=/absolute/path/to/cadcproxy.pem
```

```python
import fsspec

fs = fsspec.filesystem("vos", endpoint_url="https://staging.canfar.net/arc")
try:
    print(fs.ls("/home/<cadc-username>"))
finally:
    fs.close()
```

## Capabilities

| fsspec surface | Support |
| --- | --- |
| `ls`, `info`, `exists`, `walk`, `find`, `glob` | Listing and metadata |
| `cat_file`, `get`, `open("rb")` | Whole-object staged reads |
| `pipe_file`, `put`, `open("wb")` | Whole-object staged writes |
| `mkdir`, `makedirs`, `rm`, `rmdir` | Namespace creation and deletion |
| `cp` | Client-side copy; an internal same-authority LinkNode materializes target bytes, while an external or non-VOS LinkNode raises `NotImplementedError` before mutation |
| `mv` | DataNode/ContainerNode copy/recreate then delete; LinkNode move raises `NotImplementedError` before mutation |
| Remote byte ranges, append modes, FUSE | Unsupported |

`vosfs` targets the OpenCADC VOSpace profile only; it does not claim generic
IVOA VOSpace 2.1 conformance. The normative surface — including the full fsspec
capability matrix — is the capability contract in
[`docs/design/trd.md`](docs/design/trd.md).

## Documentation

The [vosfs documentation](https://opencadc.github.io/vostools/) contains the User
Guide, task-first [recipes](docs/user/recipes.md), symptom-keyed
[troubleshooting](docs/user/troubleshooting.md), the public API reference, and
the [`fsspec-cli`](docs/user/cli/index.md) integration guide.

The design contract for `fsspec-cli` lives in
[`docs/design/fsspec-cli/`](docs/design/fsspec-cli/) and the architecture
decisions in [`docs/adr/`](../../docs/adr/).

See [CONTRIBUTING.md](../../CONTRIBUTING.md) to work on the project. Public
documentation source lives under `docs/user/`;
validate it locally from the repository root with:

```bash
uv run --all-packages zensical build --strict --clean
```

## License

Copyright (c) 2026 National Research Council of Canada / Government of Canada.

`vosfs` is distributed under the terms of the
[GNU Affero General Public License v3.0 or later](LICENSE) (AGPL-3.0-or-later).
`vosfs` 0.11.0 and earlier releases were published under the BSD 3-Clause
License.
