# Integrating fss-cli

This page covers what you need to embed `fss-cli` correctly: the seam, the
source contract, lifecycle ownership, capability policy, extensions, and the
exit statuses your callers will see.

## The seam

There is exactly one stable entry point:

```python
App(sources, *, capabilities=None, extensions=()).typer_app
```

It returns a `typer.Typer` you mount wherever you want:

```python
app = typer.Typer()
app.add_typer(App(sources).typer_app, name="fs")
```

## Sources

A source is a **callable returning a fresh async context manager** that yields
one `AbstractFileSystem` per command invocation.

```python
from contextlib import asynccontextmanager

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
```

!!! warning "A fresh filesystem per invocation, not a shared one"

    The callable is invoked **per command run**, and the library owns the
    yielded filesystem for exactly that run. Do not close over one long-lived
    instance: `fss-cli` acquires and releases on the invocation's own event
    loop, and a filesystem bound to a different loop will fail.

    This is also why `skip_instance_cache=True` matters — without it fsspec may
    hand back a cached instance owned by another loop.

Source names must be non-empty strings with no colon, NUL, or newline, and must
not start with `-`. `App` validates this at construction, so a bad name is a
startup error, not a runtime surprise.

### Lifecycle guarantees

- Every source referenced by the operand list is acquired **before** any
  filesystem work, once each, in first-appearance order.
- Sources are released in reverse order. Every exit runs even if an earlier one
  raises.
- Acquisition, command body, and release all happen on the same event loop.
- A source-exit failure produces a nonzero status **even if the command body
  succeeded** — a cleanup failure is a real failure.

!!! danger "Do not call from inside a running event loop"

    The commands are synchronous Typer callbacks that own `asyncio.run`. Called
    from an already-running loop they exit `1` with
    `<command>: cannot run from an active event loop`.

## Capabilities

Recursive operations are explicit constructor policy:

```python
guarded = App(
    {"data": data_source},
    capabilities={"recursion": {"copy": True, "remove": True}},
)
```

| Capability | Default | When disabled |
| --- | --- | --- |
| `recursion.copy` | `True` | `cp` omits `-R`/`-r` from its signature entirely |
| `recursion.remove` | `False` | `rm` omits `-R`/`-r` from its signature entirely |

Because the *callback signature* changes, Typer rejects a disabled option with
status `2` before any operand or source work, and `--help` never mentions it.
The command never infers this from a backend type or protocol.

!!! danger "`recursion.remove` is your assertion, not a default"

    Enabling recursive removal asserts that every configured target satisfies
    the guarded-removal contract. Removal is **leaves-first and non-atomic**,
    with entries at one depth removed concurrently: a failure or cancellation
    can leave earlier confirmed removals in place and the rest present or
    uncertain. There is no prompt, rollback, retry, trash,
    or recovery.

## Extensions

Extensions are ordinary annotated callbacks. Their name, docstring, and
annotations define the command through Typer:

```python
import typer
from fss_cli import CommandCallback, CommandContext


def about() -> None:
    """Describe this host."""
    typer.echo("Example filesystem host")


def source_names(ctx: typer.Context) -> None:
    """List configured source names."""
    context = ctx.find_object(CommandContext)
    assert context is not None
    typer.echo("\n".join(context.sources))


extensions: list[CommandCallback] = [about, source_names]
app = App({"data": data_source}, extensions=extensions)
```

Source-free callbacks need no context parameter. Source-aware callbacks read
the frozen snapshot from `CommandContext`, which exposes **only** the source
mapping — never capability policy or private helpers.

## Exit statuses

Anything shelling out to your CLI needs these:

| Status | Meaning |
| ---: | --- |
| `0` | Every operand completed and cleanup succeeded. |
| `1` | A backend, incompatible-result, output, or source-lifecycle failure. |
| `2` | Usage or operand preflight failed; **no filesystem work ran**. |
| `141` | `128 + SIGPIPE` — a broken pipe was the only failure. |

Status `2` is a useful guarantee for destructive commands: if you get `2`,
nothing was touched.

!!! warning "`test` uses `1` as an answer, not an error"

    A false `test -e|-d|-f` predicate exits `1` with empty output. That is the
    normal negative answer, not a failure. A real failure also writes a
    diagnostic to stderr — check stderr, not just the status.

### Which commands can exit `141`

Only the streaming commands, where a closed reader is worth distinguishing from
a real failure:

```bash
myapp fs cat data:/huge.log | head -5   # 141
```

| Broken pipe gives | Commands |
| --- | --- |
| `141` | `cat`, `head`, `tail`, `rm -v` |
| `1` | `ls`, `du`, `find`, `tree`, `info`, `size`, `stat` |

The second group formats and buffers its whole output before a single write, so
a broken pipe there is an ordinary output failure with nothing partially
emitted. If you are checking for `141`, check for `1` as well unless you know
which command ran.

## Diagnostics

Failures go to stderr as one line per operand:

```text
<command>: <operand>: <category>
```

Stable categories: `not found`, `file exists`, `permission denied`,
`is a directory`, `not a directory`, `unsupported operation`,
`incompatible result`, `backend failure (<Class>): <message>`, and
`uncertain …` variants.

!!! note "`uncertain` is a third outcome"

    A category beginning `uncertain` means a mutation was **dispatched** but its
    result could not be observed. The operation may have applied. Do not treat
    it as a plain failure and blindly retry — a retry can double-apply.

`cp` is the one command that also writes to stderr when it succeeds: two
outcome lines per file written (`uploaded` or `copied`, then `verified`),
including every file of a `cp -R`, in the same `<command>: <operand>: …` form.
When stderr is a terminal and `NO_COLOR` is unset or empty, it also draws Rich
progress bars there, one per file in flight. Its stdout stays empty. A host that treats any stderr output as
failure should check the exit status instead. The
[`cp` reference](commands.md#cp) lists every outcome line and the
`--metadata-wait` option.

Control characters in an operand are escaped as `\xNN` before rendering, so a
hostile or malformed path cannot inject terminal escapes.

## Logging

Commands emit standard-library log records under the `fss_cli` logger
hierarchy. The library installs only a `NullHandler`, so nothing is printed
unless your host configures a handler:

```python
import logging

logging.getLogger("fss_cli").setLevel(logging.INFO)  # or DEBUG
logging.getLogger("fss_cli").addHandler(logging.StreamHandler())
```

At **INFO**, `cp`, `cp -R`, and `mv` emit one record per file with the source
and destination (as `name:/path`), the byte count, the duration, and an
outcome:

| Outcome | Meaning |
| --- | --- |
| `copied` | Same-filesystem `cp`, verified |
| `staged` | Copied through a local temporary file (cross-filesystem `cp`, every `cp -R` write) |
| `skipped` | `cp -R` found identical content and wrote nothing |
| `moved` | `mv`, verified |
| `verified` | One record per `cp -R`, after the whole tree passed final verification |

The same values are attached to each record as the `outcome`, `source`,
`destination`, `bytes`, and `duration` attributes. At **DEBUG**, `cp -R` also
records its content-identity decisions (which checksum tokens were compared and
whether they matched) and staging paths.

Logged locations drop any URL user information, query string, and fragment,
and escape control characters. Storage options and credentials are never
logged.

## Shell completion

`App.typer_app` is built with `add_completion=False`, because a mounted
sub-app is the wrong place to install completion. Enable it on **your** root
app instead:

```python
app = typer.Typer(add_completion=True)
app.add_typer(App(sources).typer_app, name="fs")
```

## Reference

The full normative contract lives in
[`libs/vosfs/docs/design/fss-cli/`](https://github.com/opencadc/vostools/blob/main/libs/vosfs/docs/design/fss-cli/),
with architecture decisions in
[`docs/adr/`](https://github.com/opencadc/vostools/blob/main/docs/adr/).
