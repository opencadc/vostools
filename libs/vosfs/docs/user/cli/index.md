# fss-cli

`fss-cli` turns filesystems you already use in Python into shell-shaped
commands inside **your own** command-line tool.

If you have an [fsspec](https://filesystem-spec.readthedocs.io/) filesystem —
`vosfs`, S3, GCS, local disk, or anything else — `fss-cli` gives you `ls`,
`cp`, `rm`, `du`, `find`, `cat`, and more against it, without writing any of
them yourself.

!!! info "It is a library, not a program"

    `fss-cli` installs no `fss-cli` executable. It hands your application
    a [Typer](https://typer.tiangolo.com/) app that you mount into your own CLI.
    You decide which filesystems are reachable and what they are called.

## Install

```bash
uv add "git+https://github.com/opencadc/vostools@main#subdirectory=libs/fss-cli"
```

## The whole idea in one example

```python
from contextlib import asynccontextmanager

import fsspec
import typer
from fsspec.implementations.asyn_wrapper import AsyncFileSystemWrapper
from fss_cli import App


@asynccontextmanager
async def data_source():
    """Yield one async-capable filesystem for a single command invocation."""
    yield AsyncFileSystemWrapper(fsspec.filesystem("memory"))


app = typer.Typer()
app.add_typer(App({"data": data_source}).typer_app, name="fs")

if __name__ == "__main__":
    app()
```

That is a complete CLI. Every path is spelled `name:/path`, where `name` is a
key from the mapping you passed:

```bash
python app.py fs ls data:/
python app.py fs cp data:/report.csv data:/backup/report.csv
python app.py fs du -sh data:/project
```

The `name:` prefix is not decoration — it is how one command reaches two
different filesystems at once:

```bash
python app.py fs cp local:/results.csv archive:/2026/results.csv
```

## With a real backend

The example above uses `memory` so it runs with no setup. A real host wires up
the filesystems it actually serves — here local disk plus a VOSpace archive:

```python
from contextlib import asynccontextmanager

import fsspec
import typer
from fsspec.implementations.asyn_wrapper import AsyncFileSystemWrapper
from fss_cli import App
from vosfs import VOSpaceFileSystem


@asynccontextmanager
async def local_source():
    yield AsyncFileSystemWrapper(fsspec.filesystem("file"))


@asynccontextmanager
async def archive_source():
    fs = VOSpaceFileSystem(
        endpoint_url="https://staging.canfar.net/arc",
        asynchronous=True,
        skip_instance_cache=True,
    )
    try:
        yield fs
    finally:
        await fs.aclose()


app = typer.Typer()
app.add_typer(
    App({"local": local_source, "archive": archive_source}).typer_app,
    name="fs",
)
```

```bash
python app.py fs ls -lh archive:/project
python app.py fs cp local:/reduced.fits archive:/project/reduced.fits
python app.py fs du -sh archive:/project
```

Credentials come from the backend, not from `fss-cli` — `vosfs` picks up
`VOSFS_CERT_FILE` or `VOSFS_TOKEN_FILE` as usual. See
[Integration](integration.md#sources) for why each source is a *fresh*
filesystem per invocation.

## Where to go next

<div class="grid cards" markdown>

-   :material-power-plug:{ .lg .middle } __[Integration](integration.md)__

    ---

    Sources, lifecycle, capabilities, extensions, and exit statuses — what you
    need to embed this correctly.

-   :material-console:{ .lg .middle } __[Command reference](commands.md)__

    ---

    Every command, its options, and what it actually does against a remote
    backend.

-   :material-api:{ .lg .middle } __[API reference](api-reference.md)__

    ---

    `App`, `AppCapabilities`, `CommandContext`, and the rest of the public
    surface.

</div>

## What it does not claim

`fss-cli` is not POSIX, GNU, or BSD compatible, and does not work with every
fsspec backend. It provides a shell-compatible *experience*: it renders what a
backend can actually supply, in the shape a shell user expects, and omits the
rest rather than inventing values.

Supported host platforms are Linux and macOS. The commands and source forms
with test evidence behind them are listed in the
[tested command matrix](https://github.com/opencadc/vostools/blob/main/libs/vosfs/docs/design/fss-cli/matrix.md).
