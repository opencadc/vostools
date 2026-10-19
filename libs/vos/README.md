# vos

[![PyPI](https://img.shields.io/pypi/v/vos.svg)](https://pypi.python.org/pypi/vos)
[![Python](https://img.shields.io/pypi/pyversions/vos.svg)](https://pypi.python.org/pypi/vos)
[![License: AGPL-3.0-or-later](https://img.shields.io/badge/license-AGPL--3.0--or--later-blue)](LICENSE)

`vos` is a Python library and command-line tools for [VOSpace](https://www.ivoa.net/documents/VOSpace/). The default configuration targets the VOSpace provided by the [Canadian Advanced Network For Astronomical Research](http://www.canfar.net/) (CANFAR).

There are two ways to use it:

1. The command-line tools, for example `vcp`.
2. The library: `import vos`.

Authentication to the CANFAR VOSpace service uses X.509 certificates, header tokens, or a username and password. Access control is managed by the CADC Group Management Service (GMS). Retrieve a certificate with `cadc-get-cert` from the `cadcutils` package, which `vos` depends on.

More background is in the [CANFAR VOSpace documentation](http://www.canfar.net/docs/vospace/).

## Requirements

- A CANFAR VOSpace account for write access. Read access can be anonymous.
- Python 3.10 through 3.14.

## Install

```bash
pip install vos
```

From a checkout of this repository:

```bash
uv pip install ./libs/vos
```

## Tutorial

1. Get a [CANFAR account](http://www.canfar.phys.uvic.ca/canfar/auth/request.html).
2. Install `vos`.
3. Retrieve an X.509 certificate with `cadc-get-cert`.
4. Use the tools.

Command line:

```bash
vls -l vos:
vcp vos:jkavelaars/test.txt ./
vchmod g+q vos:VOSPACE/foo/bar.txt 'GROUP1, GROUP2, GROUP3'
vmkdir --help
```

The other commands are `vrm`, `vrmdir`, `vsync`, `vcat`, `vln`, and `vlock`. `pydoc vos.commands` lists them.

From Python:

```python
import vos

client = vos.Client()
client.listdir("vos:jkavelaars")
```

## Integration tests

The live tests run against the CADC VOSpace and need test-account credentials. They are not collected by the default test run. From the repository root:

```bash
uv run --package vos pytest libs/vos/tests/integration
```
