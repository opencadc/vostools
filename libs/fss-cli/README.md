# fsspec-cli

[![CI](https://github.com/opencadc/vostools/actions/workflows/quality.yml/badge.svg)](https://github.com/opencadc/vostools/actions/workflows/quality.yml)
[![Python](https://img.shields.io/badge/python-3.10%2B-blue)](https://www.python.org/)
[![License: AGPL-3.0-or-later](https://img.shields.io/badge/license-AGPL--3.0--or--later-blue)](LICENSE)

`fsspec-cli` is a **library-only** package that turns host-configured async
[`fsspec`](https://github.com/fsspec/filesystem_spec) filesystems into
POSIX-shaped [Typer](https://typer.tiangolo.com/) commands you embed in your own
CLI. It installs no executable and no module entry point.

## Install

```bash
uv add "git+https://github.com/opencadc/vostools@main#subdirectory=libs/fss-cli"
```

## Quickstart

```python
from contextlib import asynccontextmanager

import fsspec
import typer
from fsspec.implementations.asyn_wrapper import AsyncFileSystemWrapper
from fsspec_cli import App


@asynccontextmanager
async def data_source():
    # One fresh async-capable filesystem per command invocation.
    yield AsyncFileSystemWrapper(fsspec.filesystem("memory"))


app = typer.Typer()
app.add_typer(App({"data": data_source}).typer_app, name="fs")

if __name__ == "__main__":
    app()
```

Operands are spelled `name:/path`, naming one configured source:

```bash
python app.py fs ls data:/
python app.py fs cp local:/results.csv archive:/2026/results.csv
```

`memory` keeps the example runnable with no setup; a real host maps the
filesystems it serves. See the
[Overview](https://opencadc.github.io/vostools/cli/) for the same app wired to
local disk plus a remote VOSpace archive.

## Commands

`ls` · `du` · `find` · `tree` · `size` · `test` · `info` · `stat` ·
`head` · `tail` · `cat` · `cp` · `mv` · `mkdir` · `rmdir` · `unlink` · `rm`.

Recursive `cp -R` is on by default; recursive `rm -R` is **off** by default and
must be enabled explicitly:

```python
App({"data": data_source}, capabilities={"recursion": {"remove": True}})
```

## Public API

`App`, `AppCapabilities`, `RecursionCapabilities`, `AsyncFilesystemSource`,
`CommandCallback`, and `CommandContext`.

## Documentation

| For | Read |
| --- | --- |
| Embedding it: sources, lifecycle, capabilities, exit statuses | [Integration guide](https://opencadc.github.io/vostools/cli/integration/) |
| What each command does | [Command reference](https://opencadc.github.io/vostools/cli/commands/) |
| The public API | [API reference](https://opencadc.github.io/vostools/cli/api-reference/) |
| The normative contract and its rationale | [`docs/design/fsspec-cli/`](../vosfs/docs/design/fsspec-cli/) |
| Architecture decisions | [`docs/adr/`](../../docs/adr/) |

## Scope

`fsspec-cli` provides a shell-compatible *experience*: it renders what a backend
can actually supply, in the shape a shell user expects, and omits the rest
rather than inventing values. It does **not** claim POSIX, GNU, BSD/macOS, or
all-fsspec compatibility. Supported host platforms are Linux and macOS.

## License

Copyright (c) 2026 National Research Council of Canada / Government of Canada.

`fsspec-cli` is distributed under the terms of the
[GNU Affero General Public License v3.0 or later](LICENSE) (AGPL-3.0-or-later).
`fsspec-cli` 0.10.0 and earlier releases were published under the BSD 3-Clause
License.

## Long listings and repeated copies

`ls -l` keeps shell column order even when metadata is sparse. Unknown scalar
fields show `-`, unknown permission bits show `?`, and old dates show the year.
VOSpace access groups share one field (`r=OSSOS,w=NONE`) rather than implying
POSIX group ownership; raw access and lock metadata remain available in `info`.

Recursive `cp -R` skips files only after compatible MD5 metadata or staged
SHA-256 comparison establishes equality. Equal size alone is insufficient.
Without content checksums, comparing existing files still downloads both
contents, but avoids unnecessary uploads and reuses a differing staged source.
