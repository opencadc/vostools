# Add opt-in command extensions through `App`

Status: Accepted

> **Partially superseded by
> [Define Typer-owned commands and callback extensions](./0005-define-typer-owned-commands-and-callback-extensions.md):**
> the `CommandExtension` registrar and `register(...)` clauses are replaced by
> annotated callbacks. Explicit opt-in, omission behavior, core-first caller
> order, immutable source snapshots, application-capability separation, and
> Typer's command-conflict behavior remain accepted.

Question: [Add the backend-specific extension seam](https://github.com/shinybrar/vosfs/issues/191)

## Decision

The stable v1 host seam is:

```python
App(sources, *, capabilities=capabilities, extensions=[...]).typer_app
```

[ADR 0002](0002-own-async-filesystems-per-invocation.md) defines application
capabilities on that constructor for core command policy. That parameter does
not amend extension behavior. Omitting `extensions` preserves the core command
surface. As superseded by ADR 0005, `App` snapshots the source mapping,
registers core commands first, then registers each selected annotated callback
in caller order. Source-aware callbacks retrieve the immutable snapshot from
the public `CommandContext` installed on `typer.Context`; source-free callbacks
need no context parameter. Extensions do not receive core capability
configuration.

An extension callback defines one command through its name, docstring, and
annotations. It adds no public runner, lifecycle policy, backend registry,
extension capability metadata, or async invocation seam. Each extension
command detects its required filesystem operation by calling it; backend type
and protocol do not select extension commands or behavior.

This decision amends only the exact `App(sources).typer_app` constructor wording
in [ADR 0002](0002-own-async-filesystems-per-invocation.md) and
[ADR 0003](0003-acquire-referenced-async-filesystem-sources.md). Their source
ownership, validation, acquisition, cleanup, diagnostics, control-flow, and
single-host-seam decisions remain accepted.

The first proof is the opt-in `sign` extension. It calls `filesystem.sign(path)`
and converts `NotImplementedError` into one unsupported-operation diagnostic
and status `1` without a traceback.

## Considered alternatives

- A string registry or entry-point discovery would add naming, loading, and
  conflict policy before another consumer exists.
- Backend inspection or protocol dispatch would make command behavior depend
  on backend identity instead of explicit application policy or the called
  extension operation.
- A public async runner or lifecycle object would create a second host seam and
  contradict the invocation-owned lifecycle.

## Consequences

- Hosts opt into backend-specific commands explicitly at app construction.
- Core capability policy does not register or remove extension commands.
- Third-party extension authors receive only Typer registration and immutable
  source configuration; source instances remain invocation-owned.
- Each extension command owns a compatibility profile and tested-source matrix.
- Command-name conflict policy remains Typer's existing behavior until a real
  conflict requires a narrower rule.
