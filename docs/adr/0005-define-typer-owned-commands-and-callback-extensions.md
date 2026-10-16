# Define Typer-owned commands and callback extensions

Status: Accepted

Question: [Redesign fsspec-cli around Typer-owned commands](https://github.com/shinybrar/vosfs/issues/299)

## Decision

The stable host seam remains:

```python
App(
    sources,
    *,
    capabilities={"recursion": {"copy": True, "remove": False}},
    extensions=[...],
).typer_app
```

Each `App` builds one Typer application and registers every first-party command
centrally as an annotated synchronous callback. Typer and Click own token
parsing, type conversion, help, and usage errors. Invalid typed input exits
with status 2 before source acquisition. Command callbacks retain semantic
validation, mapped-source validation, filesystem execution, output,
diagnostics, and command compatibility profiles.

`extensions=` accepts synchronous annotated `CommandCallback` functions, not
registrar objects. Core commands register first, followed by callbacks in
caller order. Typer derives each extension's command name, help, and parameters
from its function name, docstring, and annotations, including Typer's existing
duplicate-name behavior. Omitting extensions preserves the core surface.

A public `CommandContext` contains only the immutable source-mapping snapshot.
A source-aware extension callback retrieves it through `typer.Context`; a
source-free callback needs no context parameter. Installing this embedded
context does not overwrite a parent Typer application's context object.
Extensions receive neither application capabilities nor private lifecycle,
validation, mapped-operand, or diagnostic APIs.

Application capabilities retain the semantics accepted in
[ADR 0002](./0002-own-async-filesystems-per-invocation.md): `App` validates and
deep-snapshots them at construction, and they control admission of core command
features without describing backend support. Separate `App` instances keep
their source snapshots and capabilities isolated.

Source ownership, eager first-appearance acquisition, complete reverse cleanup,
failure precedence, and current-operation cancellation safety remain governed
by [ADR 0002](./0002-own-async-filesystems-per-invocation.md) and
[ADR 0003](./0003-acquire-referenced-async-filesystem-sources.md).
Callback registration introduces no second invocation or lifecycle seam.

This decision supersedes only the `CommandExtension` registrar and
`register(typer_app, sources)` clauses in
[ADR 0004](./0004-add-opt-in-command-extensions.md). Its explicit opt-in,
omission behavior, core-first caller order, immutable source snapshots,
application-capability separation, and Typer command-conflict decisions remain
accepted.

The redesign is breaking `fsspec-cli` 0.6.0 work. Release Please therefore sets
`bump-minor-pre-major` for `fsspec-cli`, making a breaking change from 0.5.x
advance to 0.6.0 rather than 1.0.0. This policy does not apply to `vosfs`.

## Consequences

- Raw-token parsers, synthetic help metadata, command tuple catalogs, and
  registrar protocols have no role in the completed command surface.
- Command annotations and docstrings are the authoritative parsing and help
  definition.
- Extensions remain ordinary Typer callbacks while mapped commands reuse the
  accepted invocation-owned source lifecycle.
