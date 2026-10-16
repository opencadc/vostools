# `fsspec-cli` async execution boundary

<!-- pyml disable line-length -->

Researched: 2026-07-15

Question: [shinybrar/vosfs#90](https://github.com/shinybrar/vosfs/issues/90)

Status: **Research complete; issue #92 locked the host-integration contract. No production code is added by this ticket.**

## Source baseline

| Component | Version / commit | Role in this question |
| --- | --- | --- |
| fsspec | [`2026.6.0` / `a2457004d03e0312f715f90f58873de5ab195a37`](https://github.com/fsspec/filesystem_spec/tree/a2457004d03e0312f715f90f58873de5ab195a37) | Async filesystem contract, sync bridge, and sync-to-async wrapper. |
| Typer | [`0.27.0` / `60af34b60ab2650a74af32c6ce340c5cfbceb3d8`](https://github.com/fastapi/typer/tree/60af34b60ab2650a74af32c6ce340c5cfbceb3d8) | Locked host-facing command application; this release vendors an adapted Click 8.3.1 core. |
| Click | [`8.4.2` / `b2e30a175449cfda909ee4fbf4a29a6a071cad53`](https://github.com/pallets/click/tree/b2e30a175449cfda909ee4fbf4a29a6a071cad53) | Upstream reference; its callback and teardown semantics corroborate Typer's vendored core. |
| CPython | `3.10.20` through `3.14.6` (pinned sources below) | `asyncio` runner and nested-loop behavior across the supported Python floor and current releases. |
| `vosfs` | [`3860fcf7d0156a5c6b0c206ca4753443054f699f`](https://github.com/shinybrar/vosfs/tree/3860fcf7d0156a5c6b0c206ca4753443054f699f) | Top-priority native-async filesystem and concrete resource lifecycle. |

The installed local fsspec and Click versions were also inspected. The links above are immutable upstream source evidence.

## Executive finding

One backend-agnostic **async command core is viable**. For plain `ls`, it can await the selected filesystem's `_info(path)` and `_ls(path, detail=False)` coroutine hooks and keep every locked result-shape, ordering, diagnostic, and output rule. It need not inspect a backend class or protocol.

Raw synchronous filesystems are not part of that surface. Local and Memory may participate only through an explicit async adapter supplied by the host. fsspec's `AsyncFileSystemWrapper` can provide that adapter by moving each synchronous call to `asyncio.to_thread`, but fsspec labels it experimental. Automatic wrapping inside `fsspec-cli` would silently choose experimental thread and lifecycle behavior for the host, so it is not recommended.

The research found one hard contradiction among the earlier locks:

1. Typer 0.27.0's vendored, adapted Click 8.3.1 core invokes callbacks synchronously and does not await a returned coroutine; upstream Click 8.4.2 independently corroborates the same behavior.
2. `asyncio.run()` is therefore needed at some synchronous framework boundary unless the public Typer seam changes, but it owns a new loop and cannot run inside another loop in the same thread.
3. The injected filesystems were host-owned live instances, so `fsspec-cli` could not close them.
4. `vosfs` lazily creates loop-associated HTTP clients and requires `await fs.aclose()`; its async instance deliberately rejects synchronous `close()`.

Consequently, the old live-instance seam, literal end-to-end async execution,
host-owned reusable instances, and deterministic resource cleanup could not all
hold at once. [Issue #92](https://github.com/shinybrar/vosfs/issues/92)
resolved the contradiction; [ADR 0002](../../adr/0002-own-async-filesystems-per-invocation.md)
is the sole normative host-integration and lifecycle contract.

## Primary-source facts

### 1. The fsspec async surface is the underscore coroutine surface

fsspec documents that async implementations derive from `AsyncFileSystem`, advertise `async_impl`, and expose `async def` variants under underscore-prefixed names. It explicitly says `asynchronous=True` is the mode for calling those methods directly with `await`. It also warns that async resource creation should be deferred and resource destruction may need to be awaited. ([async overview](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/docs/source/async.rst#L6-L21), [async construction and cleanup](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/docs/source/async.rst#L65-L87))

`AsyncFileSystem` lists `_ls` and `_info` among the expected coroutine hooks, sets `async_impl=True`, and records whether an instance was constructed in asynchronous mode. `asynchronous=False` attaches the instance to fsspec's background loop; `asynchronous=True` leaves that sync-bridge loop unset. ([expected hooks](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/asyn.py#L302-L340), [mode and loop state](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/asyn.py#L343-L357), [`_info` and `_ls`](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/asyn.py#L742-L746))

The public names such as `info` and `ls` on an async implementation are synchronous mirrors. fsspec creates them with `sync_wrapper`, which submits a coroutine to another loop and blocks the calling thread for its result. That bridge rejects a call made from the same running loop. ([sync bridge](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/asyn.py#L63-L120), [sync method mirroring](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/asyn.py#L971-L1004))

**Fact-derived boundary:** production command logic that is async-only must await `_info`, `_ls`, and later command-specific underscore hooks. It must not call `info`, `ls`, `fsspec.asyn.sync`, `sync_wrapper`, or `get_loop`.

The underscore naming means this is a deliberately selected, version-tested fsspec interface, not a universal promise for all `AbstractFileSystem` implementations. The per-command compatibility matrix must retain the exact fsspec version used for each result.

This conflicted with the original plain-`ls` blanket ban on private hooks.
Issue #92 explicitly supersedes only that blanket: production may directly
await fsspec's documented, version-tested `_info`, `_ls`, and later
command-specific underscore coroutines. Other private hooks remain outside the
profile.

### 2. Raw Local and Memory are synchronous; the official wrapper uses threads

`AbstractFileSystem` defaults `async_impl=False`. Both `LocalFileSystem` and `MemoryFileSystem` derive directly from it and implement ordinary `def info` and `def ls` methods. ([base flags](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/spec.py#L103-L119), [Local methods](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/local.py#L19-L78), [Memory methods](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/memory.py#L17-L43), [Memory `info`](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/memory.py#L149-L169))

`AsyncFileSystemWrapper` derives from `AsyncFileSystem`, retains the supplied sync instance, and installs underscore coroutine wrappers for its public synchronous methods. Each wrapper awaits `asyncio.to_thread`; an optional semaphore limits concurrent calls. If `asynchronous` is omitted, the wrapper guesses its mode from whether construction occurs under a running loop. ([wrapper implementation](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/asyn_wrapper.py#L11-L97))

fsspec calls the wrapper experimental, says it is for interfaces that expect `AsyncFileSystem`, and makes no speed claim. ([wrapper status and limitations](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/docs/source/async.rst#L156-L188)) Python documents `asyncio.to_thread` as a way to await otherwise blocking I/O without blocking the event-loop thread. ([`asyncio.to_thread`](https://github.com/python/cpython/blob/6cb20a219a860eaf687b2d968b41c480c7461909/Doc/library/asyncio-task.rst#L1001-L1049))

**Fact-derived boundary:** a wrapped Local or Memory instance gives the CLI an awaitable surface, but the backend remains synchronous work executed in a worker thread. “Async-only CLI” can truthfully mean that the command core never blocks the event-loop thread; it cannot mean that every supported backend performs native non-blocking I/O.

### 3. Typer and Click do not await command callbacks

Typer 0.27.0 does not dispatch through the independently installed Click 8.4.2 core. It imports its own `_click` package, explicitly described as adapted from Click 8.3.1. ([Typer import](https://github.com/fastapi/typer/blob/60af34b60ab2650a74af32c6ce340c5cfbceb3d8/typer/main.py#L18-L24), [vendored-core provenance](https://github.com/fastapi/typer/blob/60af34b60ab2650a74af32c6ce340c5cfbceb3d8/typer/_click/__init__.py#L1-L9))

Typer converts a registered callback into an ordinary wrapper and directly returns `callback(**use_params)`. Its vendored `Context.invoke` then directly calls the wrapper, and vendored `Command.invoke` returns that result. None detects or awaits an awaitable. ([Typer callback wrapper](https://github.com/fastapi/typer/blob/60af34b60ab2650a74af32c6ce340c5cfbceb3d8/typer/main.py#L1496-L1527), [vendored callback invocation](https://github.com/fastapi/typer/blob/60af34b60ab2650a74af32c6ce340c5cfbceb3d8/typer/_click/core.py#L474-L489), [vendored command dispatch](https://github.com/fastapi/typer/blob/60af34b60ab2650a74af32c6ce340c5cfbceb3d8/typer/_click/core.py#L734-L746))

Typer's `add_typer` support composes one Typer application beneath another; during materialization it recursively rebuilds groups and builds each command callback independently. An unnamed mounted group may even lose its callback. The async bridge therefore belongs in each concrete command adapter, not in `Typer.__call__` or a group callback. ([Typer composition](https://github.com/fastapi/typer/blob/60af34b60ab2650a74af32c6ce340c5cfbceb3d8/docs/tutorial/subcommands/add-typer.md#L47-L69), [recursive materialization](https://github.com/fastapi/typer/blob/60af34b60ab2650a74af32c6ce340c5cfbceb3d8/typer/main.py#L1283-L1357), [per-command callback construction](https://github.com/fastapi/typer/blob/60af34b60ab2650a74af32c6ce340c5cfbceb3d8/typer/main.py#L1392-L1434))

Typer's vendored context teardown is synchronous: `Context.close` drains a regular `ExitStack`, not an `AsyncExitStack`. External Click 8.4.2 has the same direct callback and synchronous-teardown behavior. ([vendored context teardown](https://github.com/fastapi/typer/blob/60af34b60ab2650a74af32c6ce340c5cfbceb3d8/typer/_click/core.py#L265-L376), [Click 8.4.2 callback invocation](https://github.com/pallets/click/blob/b2e30a175449cfda909ee4fbf4a29a6a071cad53/src/click/core.py#L846-L907), [Click 8.4.2 teardown](https://github.com/pallets/click/blob/b2e30a175449cfda909ee4fbf4a29a6a071cad53/src/click/core.py#L675-L708))

**Hard consequence:** an `async def` Typer callback is not a working async integration. With the locked Typer version and dependency boundary, some synchronous adapter must enter async execution, or the public interface must change.

### 4. Python's normal runner owns one loop and forbids nesting

`asyncio.run` creates a new event loop, finalizes async generators, shuts down the default executor, and closes the loop. It fails when another event loop is already running in the same thread. The same ownership and same-thread nesting rule is visible in pinned runner sources for [Python 3.10.20](https://github.com/python/cpython/blob/842e987df856a5d4db37933c62a3456930a19092/Lib/asyncio/runners.py#L8-L52), [3.11.15](https://github.com/python/cpython/blob/2340a037f7450e70fccfe411e6531afb4d57a312/Lib/asyncio/runners.py#L21-L100), [3.12.13](https://github.com/python/cpython/blob/3bb231a6a5dc02b95658877318bf61501a7209e9/Lib/asyncio/runners.py#L20-L100), [3.13.14](https://github.com/python/cpython/blob/fd17997c3866d61e0e7bd8201b1d8f35b40a40bd/Lib/asyncio/runners.py#L20-L101), and [3.14.6](https://github.com/python/cpython/blob/c63aec69bd59c55314c06c23f4c22c03de76fe45/Lib/asyncio/runners.py#L21-L105).

`asyncio.Runner` permits several top-level coroutine calls on one owned loop, but `Runner.run` has the same same-thread running-loop prohibition and closes its loop, async generators, and executor when the context exits. ([Runner lifecycle](https://github.com/python/cpython/blob/6cb20a219a860eaf687b2d968b41c480c7461909/Lib/asyncio/runners.py#L20-L94), [Runner close](https://github.com/python/cpython/blob/6cb20a219a860eaf687b2d968b41c480c7461909/Lib/asyncio/runners.py#L64-L79))

`Runner` was added in Python 3.11, while this repository currently supports Python 3.10. Unless the independent `fsspec-cli` project chooses a newer floor, a production boundary cannot depend on `Runner`; `asyncio.run` is the portable built-in runner. ([Runner version](https://github.com/python/cpython/blob/6cb20a219a860eaf687b2d968b41c480c7461909/Doc/library/asyncio-runner.rst#L74-L116))

Moving the adapter to a new thread would avoid the literal nested-loop exception, but a synchronous Click callback would still block its caller while waiting. It would also move injected filesystem work to a loop that may not own the filesystem's resources. That is not a generic async-host solution.

### 5. `vosfs` proves that loop and resource ownership matter

`VOSpaceFileSystem` is a native `AsyncFileSystem`; it implements `_info` and `_ls` as coroutines. It defaults `asynchronous=False`, so an async-only host must explicitly construct it with `asynchronous=True`. ([class and constructor](https://github.com/shinybrar/vosfs/blob/3860fcf7d0156a5c6b0c206ca4753443054f699f/src/vosfs/filesystem.py#L53-L107), [plain-list hooks](https://github.com/shinybrar/vosfs/blob/3860fcf7d0156a5c6b0c206ca4753443054f699f/src/vosfs/filesystem.py#L245-L264))

Its HTTP clients are lazy and explicitly scoped per filesystem instance and event loop. `aclose()` closes them and permanently marks the pool closed. `VOSpaceFileSystem.aclose()` also evicts the instance from fsspec's cache; synchronous `close()` rejects an `asynchronous=True` instance and tells the caller to await `aclose()`. ([lazy loop-scoped pool](https://github.com/shinybrar/vosfs/blob/3860fcf7d0156a5c6b0c206ca4753443054f699f/src/vosfs/transport.py#L47-L105), [pool cleanup](https://github.com/shinybrar/vosfs/blob/3860fcf7d0156a5c6b0c206ca4753443054f699f/src/vosfs/transport.py#L162-L174), [filesystem cleanup](https://github.com/shinybrar/vosfs/blob/3860fcf7d0156a5c6b0c206ca4753443054f699f/src/vosfs/filesystem.py#L856-L876))

fsspec's instance cache can retain filesystem instances after user references disappear; async instances are cached by thread identity, not event-loop identity. `skip_instance_cache=True` avoids that reuse, while `clear_instance_cache` clears the class-wide cache. ([cache identity and retention](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/spec.py#L36-L100), [cache opt-out](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/spec.py#L131-L154))

`App` cannot generically prove that a borrowed live instance has never realized
a resource on some prior loop. Thread-keyed instance caching makes that missing
evidence material. The locked source contract avoids borrowing such instances:
each source creates and yields a fresh invocation-owned filesystem on the loop
that will use and close it.

**Hard consequence:** a Typer callback cannot create a temporary loop, use a host-owned reusable `VOSpaceFileSystem`, close the loop, and leave deterministic cleanup to the host. The host no longer has the owning loop on which to await cleanup. Closing the filesystem inside the callback instead transfers lifecycle ownership to `fsspec-cli` and makes the injected instance unusable for later host work.

## Decision resolution

Issue #92 selected the invocation-owned source model from these facts. ADR 0002
is canonical; the backend implications and implementation handoff below apply
the research without restating that contract.

## Backend implications

| Filesystem | Raw instance | Async-compatible form | Research implication |
| --- | --- | --- | --- |
| Local | Reject: sync `info`/`ls`, `async_impl=False`. | Host source yielding `AsyncFileSystemWrapper(LocalFileSystem(), asynchronous=True)`. | Plain `ls` remains testable through awaited thread offload. Matrix status must say `adapted async`; wrapper is experimental. |
| Memory | Reject: sync `info`/`ls`, `async_impl=False`. | Host source yielding `AsyncFileSystemWrapper(MemoryFileSystem(), asynchronous=True)`. | Same boundary as Local. Global in-process store semantics do not become native async semantics. |
| `vosfs` | Reject default `asynchronous=False`; accept a native source constructing in async mode. | Source constructs `VOSpaceFileSystem(..., asynchronous=True, skip_instance_cache=True)` inside the invocation loop and awaits `aclose()` on exit. | Native async plain `ls` is viable with deterministic same-loop cleanup. |
| Other fsspec backend | Reject raw sync or wrong-mode instances. | Native async or explicit host-supplied async adapter that passes generic checks. | Compatibility is command-, backend-, and version-tested; never universal. |

## Implementation handoff

The later tracer should prove, test-first:

- invalid command preflight enters no source;
- sources yielding raw Local, raw Memory, or wrong-mode native async instances
  fail validation before filesystem I/O;
- sources yielding wrapped Local, wrapped Memory, and a native
  `VOSpaceFileSystem(asynchronous=True, skip_instance_cache=True)` pass the same
  structural validation;
- one handler awaits `_info` and `_ls` and never touches their public sync mirrors;
- `NotImplementedError` and incompatible result shapes retain the locked command outcomes;
- neither production code nor tests branch on backend type or protocol;
- ordinary Typer invocation and parent `add_typer` composition preserve the locked output and exit behavior;
- invocation under an already-running loop follows the selected explicit contract, with no raw nested-loop traceback or un-awaited coroutine warning;
- resource cleanup is awaited on the owning loop, `vosfs` is closed exactly
  once by its source, and no fsspec instance-cache entry leaks a loop-bound
  closed instance; and
- the compatibility matrix records native versus adapted async evidence with exact versions.

## Scope boundary

This research does not add a console script, construct or authenticate
filesystems inside `fsspec-cli`, alter `vosfs`, or implement commands. It does
not claim every fsspec backend is async-compatible. ADR 0002 records the locked
ownership contract; [ADR 0003](../../adr/0003-acquire-referenced-async-filesystem-sources.md)
records lifecycle-failure semantics. The tested matrix and production tracer
sequencing remain with their linked Wayfinder tickets.
