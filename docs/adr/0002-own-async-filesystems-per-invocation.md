# Own async filesystems per command invocation

Status: Accepted

Typer invokes command callbacks synchronously, while `fsspec-cli` command work
is async-only and native `vosfs` resources require awaited cleanup on their
owning event loop. `fsspec-cli` therefore accepts a non-empty
`Mapping[str, AsyncFilesystemSource]` instead of live filesystem instances.
Each source is a reusable synchronous callable returning a fresh async context
manager that yields one `AbstractFileSystem` for a command invocation.

The host owns each source's backend configuration and cleanup declaration.
`App` owns the yielded filesystem only for that invocation: one command
coroutine enters every required source, validates and uses each yielded
filesystem, and exits every entered source on the same event loop before that
loop closes. A yielded filesystem never escapes the invocation or crosses into
a later invocation. The source context exit is the generic cleanup interface;
`App` does not discover or call a backend-specific cleanup method.

The stable v1 seam is the source mapping, snapshotted application capabilities,
and extension selection accepted by
[ADR 0004](0004-add-opt-in-command-extensions.md), with annotated extension
callbacks and their immutable source context defined by
[ADR 0005](0005-define-typer-owned-commands-and-callback-extensions.md):

```python
App(
    sources,
    *,
    capabilities={"recursion": {"copy": True, "remove": False}},
    extensions=[...],
).typer_app
```

The public `AppCapabilities` and `RecursionCapabilities` types are total-false
typed dictionaries. Only `recursion`, `copy`, and `remove` are accepted. Values
must be real booleans. `App` validates and deep-snapshots the input at
construction; later caller mutation cannot change command policy. There is no
file, environment, plugin, source, or matrix loader.

An omitted `capabilities` argument, omitted `recursion` group, or omitted
member means `capabilities.recursion.copy is True` and
`capabilities.recursion.remove is False`. Recursive-copy policy is therefore
backward-compatible. A true `remove` value is the embedding host's assertion
that every target in the configured source mapping meets the locked guarded
recursive-removal contract; it does not itself add that command behavior.
These are application policies, not per-source facts. Production MUST NOT
infer, override, or refine them by inspecting a backend class, wrapper class,
object identity, protocol string, registry, or yielded filesystem. The tested
command matrix remains evidence for a host's configuration decision;
production code does not load it.

Each concrete Typer command uses one zero-command-logic synchronous adapter to
check for an active same-thread event loop and, when none exists, run one
command coroutine. Direct invocation from an active same-thread loop produces
a stable configuration diagnostic. V1 exposes no public async invocation seam,
background loop, hidden runner thread, or nested-loop workaround.

After source entry and before filesystem I/O, `App` accepts only an
`AsyncFileSystem` with `async_impl is True` and `asynchronous is True`. Raw
synchronous and wrong-mode instances are rejected. A host source may explicitly
yield `AsyncFileSystemWrapper(raw, asynchronous=True)`, recorded as
`adapted async`; `fsspec-cli` never creates that wrapper or branches on its
class. Production command code awaits fsspec's version-tested, documented
underscore coroutines such as `_info` and `_ls`; it never calls their public
synchronous facades.

A native VOS source constructs a fresh
`VOSpaceFileSystem(asynchronous=True, skip_instance_cache=True)` inside the
invocation loop and awaits `aclose()` from its context-manager exit before that
loop closes. `fsspec-cli` imports no `vosfs` runtime dependency and owns no VOS
configuration or authentication behavior.

This decision supersedes only the live-instance injection and ownership clauses
in [Release fsspec-cli independently inside the vosfs workspace](./0001-release-fsspec-cli-independently.md).
The independent workspace, release, tag, artifact, dependency, publication,
and sole Typer-seam decisions remain accepted.

## Considered alternatives

- Borrowed live instances require every ordinary Typer host to own a persistent
  runner from filesystem creation through cleanup, making the common embedding
  path unsafe by default.
- Borrowed and managed catalogs plus pluggable runners are coherent but expose
  speculative v1 seams.
- A background fsspec sync bridge, nested event loop, or hidden runner thread
  violates the async-only direction and obscures resource ownership.

## Consequences

- Hosts trade cross-invocation filesystem reuse for deterministic same-loop
  creation, use, and cleanup.
- Source lifecycle failure ordering, diagnostics, and exit precedence follow
  [Acquire every referenced async filesystem source before filesystem work](./0003-acquire-referenced-async-filesystem-sources.md).
- Application capabilities configure core command policy without
  transferring source qualification or lifecycle ownership from the embedding
  host.
- Exact backend compatibility remains command-, backend-, and version-tested;
  no generic fsspec compatibility claim follows from this lifecycle contract.
