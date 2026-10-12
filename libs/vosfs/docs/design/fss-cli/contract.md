# `fss-cli` shared command contract

Status: **Normative.** Every command in
[`commands.md`](commands.md) inherits this document. A command section states
only its *delta* from these rules.

Client baseline: **fsspec 2026.6.0**, **Typer 0.27.0**.

The key words **MUST**, **MUST NOT**, **REQUIRED**, **SHOULD**, **SHOULD NOT**,
and **MAY** are interpreted as described by
[RFC 2119](https://www.rfc-editor.org/rfc/rfc2119) and
[RFC 8174](https://www.rfc-editor.org/rfc/rfc8174) only when capitalized.

## 1. What `fss-cli` claims

`fss-cli` provides a shell-compatible *experience*: the useful subset of a
file utility, honestly rendered. It does **not** claim POSIX, GNU, BSD/macOS,
or all-fsspec compatibility. Supported host platforms are Linux and macOS.

Two rules make best-effort output safe:

1. **Never fabricate.** A field a backend does not report is omitted, or shown
   as a neutral `-`. A zero, a fabricated POSIX mode, or a substituted timestamp is
   never invented. Explicit advisory access summaries are labeled in the
   backend contract and remain separate from real POSIX modes.
2. **Adaptive richness.** Output is as rich as the backend allows and no
   richer. The command does not change between backends — only the data does.

## 2. Interface ownership

Interface ownership is fixed by
[ADR 0005](../../../../../docs/adr/0005-define-typer-owned-commands-and-callback-extensions.md).

**Typer owns** option parsing, type conversion, argument arity, the `--`
terminator, `--help`, and framework usage errors. Typer's exact rendered
wording is **not** compatibility surface; tests assert on its parameter
context, not its panel text.

**Command code owns** mapped-operand validation, semantic validation,
filesystem work, stdout bytes, and the stable diagnostics below.

Every first-party command is one annotated callback. Commands never branch on
backend type, consult a capability registry, or read the tested-command matrix
at runtime.

## 3. Mapped operands

A filesystem operand is spelled `name:/path`, where `name` selects one
host-configured source and `/path` is passed to that backend **literally**.

An operand is rejected before any event-loop entry or source acquisition when
it has an empty name, no colon, a path not starting with `/`, or contains NUL
or newline:

```text
<command>: <operand>: invalid mapped filesystem operand
```

An unknown source name lists the configured names in locale order:

```text
<command>: <operand>: unknown filesystem (known: local, memory)
```

Every preflight failure has status `2`, empty stdout, and performs no source
or filesystem work.

Source names MUST be non-empty strings containing no colon, NUL, or newline,
and MUST NOT start with `-`. `App` validates this at construction.

## 4. Diagnostics

Every diagnostic goes to stderr and is terminated by one newline. Each
interpolated operand or option token is rendered by first replacing `\` with
`\\`, then escaping every control character (code point below U+0020, or
U+007F DELETE) as a lowercase `\xNN` hex sequence. Every other character is
unchanged. Literal command text and stable category names are not transformed.
**This is the only diagnostic escaping algorithm in the CLI.**

The per-operand form is:

```text
<command>: <operand>: <category>
```

Stable categories derived from a backend exception:

| Exception | Category |
| --- | --- |
| `FileNotFoundError` | `not found` |
| `FileExistsError` | `file exists` |
| `PermissionError` | `permission denied` |
| `IsADirectoryError` | `is a directory` |
| `NotADirectoryError` | `not a directory` |
| `NotImplementedError` | `unsupported operation` |
| anything else | `backend failure (<ClassName>): <message>` |

Two categories are not backend exceptions:

- `incompatible result` — the hook returned successfully with a value the
  command's contract rejects (see §5).
- an `uncertain …` variant — the failure was observed *after* a mutation was
  dispatched, so remote state is not known to be unchanged.

Output failures use the operand-free form:

```text
<command>: output: output failure (<ClassName>): <message>
```

## 5. Result validation

A backend result is validated **completely before any related stdout write**.
Commands use exact type checks (`type(value) is int`), never `isinstance`,
because `bool` subclasses `int` — a backend returning `True` must not pass as
size 1. No value is ever coerced, defaulted, dropped, or partially emitted.

## 6. Standard output

All output for one command is formatted and buffered before its first stdout
write, so a validation failure never leaves a partial record. TTY and
redirected invocations MUST produce byte-equivalent content for the same
locale and backend results.

Ordered listings sort by locale collation with the raw string as a
deterministic tie-break.

Commands MUST NOT quote, escape, color, or decorate paths, and MUST NOT add
the mapped source name to a backend-returned path unless a command section
says otherwise.

## 7. Exit status

| Status | Meaning |
| ---: | --- |
| `0` | Every operand completed and cleanup succeeded. |
| `1` | A backend, incompatible-result, output, or source-lifecycle failure. |
| `2` | Usage or operand preflight failed; no filesystem work ran. |
| `141` | `128 + SIGPIPE`, when a broken pipe is the sole failure. |

Status `141` lets a pipeline consumer (`| head`) distinguish a closed reader
from a real command failure. A `BrokenPipeError` emits no diagnostic for the
output failure itself. Commands whose output is a single buffered write use
`1` rather than `141`.

## 8. Source lifecycle

Per [ADR 0002](../../../../../docs/adr/0002-own-async-filesystems-per-invocation.md) and
[ADR 0003](../../../../../docs/adr/0003-acquire-referenced-async-filesystem-sources.md):

- The host supplies each source as a callable returning a **fresh** async
  context manager per invocation. The library owns the yielded filesystem for
  exactly one invocation and never caches it.
- Every source referenced by the operand list is acquired **before** any
  filesystem work, once each, in the order each first appears.
- Sources are released in reverse acquisition order. Every exit runs even if an
  earlier one fails.
- Acquisition and release happen on the same event loop as the command body.
- A command invoked from an already-running event loop fails with status `1`
  and `<command>: cannot run from an active event loop`.
- A source-entry or source-exit failure produces status `1` even when the
  command body succeeded.

## 9. Cancellation

A started backend operation is drained before caller control flow propagates.
Cancellation never orphans an in-flight hook call, and never reports success
for work whose outcome was not observed. See
[`lessons.md` §3](lessons.md#3-cancellation-must-not-orphan-a-started-operation).

## 10. Metadata normalization

One pure adapter turns an fsspec `info(detail=True)` / `ls(detail=True)`
mapping — whose shape differs per backend — into a normalized `ListingRow`.
Long listing and info render from it; strict Local-rich stat retains its
separate required-field validation. It is the shared normalization seam for
heterogeneous listing metadata.

| `ListingRow` field | fsspec key(s) | Typically present on |
| --- | --- | --- |
| `name` | basename of `name` | all |
| `kind` | `type` (+ `islink`) | all |
| `size` | `size` (may be `None`) | all |
| `mtime` | `mtime` / `LastModified` / `last_modified` | Local, object stores, vosfs |
| `mode` | `mode` (st_mode int) | Local only |
| `nlink` | `nlink` | Local only |
| `owner` / `group` | explicit `owner` / `group`, then `uid` / `gid`; group may summarize `read_groups` / `write_groups` | Local, vosfs |
| `permissions` | advisory ten-character access summary, separate from `mode` | vosfs |
| `link_target` | `destination` / `target` | Local, vosfs |
| `extra` | every other key (`ETag`, `md5`, `uri`, …) | backend-specific |

**Time normalization** coerces epoch floats, `datetime` values, and ISO-8601
strings to epoch seconds. Precedence is `mtime` → `LastModified` /
`last_modified`. `created` is **never** substituted for `mtime`. Timezone-less
strings and naive `datetime` values are interpreted as UTC. An unparseable or
absent time yields `None`.

**Stable shell columns**: long listing renders mode, link count, owner, group,
size, modification time, and name in that order, even for sparse metadata.
Unknown scalar fields show `-`; unknown permission bits show `?` after the
known file-kind character. No allocated-block total is inferred from bytes.
Real modes take precedence over advisory `permissions`. Numeric identities
resolve through the local account databases, falling back to numeric values.
Resolution is presentation only, not a claim about a remote account namespace.

When no owning group is reported, access group lists render in one field as
`r=GROUPS,w=GROUPS`. Known empty lists render `NONE`, absent lists render `?`.
VOSpace group and creator interpretation lives in vosfs, never in this library.
Full access fields and raw properties remain in info's `extra` mapping.
All displayed strings MUST be free of NUL, newline, and carriage return.

See [ADR 0008](../../../../../docs/adr/0008-render-shell-shaped-long-listings.md), which
supersedes adaptive column omission. This is a parsable-output change.

## 11. Flag conventions

- `--help` shows help. **`-h` means human-readable** in size-bearing commands
  (`ls -l`, `du`); it is *not* a global help alias.
- `-l` long listing, `-A` include dot entries, `-c N` byte count,
  `-s` summarize, `-p` create parents, `-R`/`-r` recursive, `-f` force,
  `-v` verbose, `-d` directory.
- Unknown options fail closed through Typer with status `2` before source
  acquisition.
- Every option carries help text; `test_command_help.py` enforces this for the
  whole registered surface.

## 12. Application capabilities

Capabilities are explicit constructor policy, validated and snapshotted at
construction. There is no file, environment, plugin, or matrix loader.

| Capability | Default | Effect when false |
| --- | --- | --- |
| `recursion.copy` | `True` | The `cp` callback omits `-R`/`-r` entirely. |
| `recursion.remove` | `False` | The `rm` callback omits `-R`/`-r` entirely. |

Because the *callback signature* differs, Typer rejects a disabled option
before operand or source work, and `--help` does not mention it. The command
never infers this policy from a backend type, protocol, or matrix row.

## 13. Extensions

Extensions are ordinary synchronous annotated callbacks passed through
`extensions=`. Their function name, docstring, and annotations define the
command. Source-free callbacks need no context parameter; source-aware
callbacks read the frozen source snapshot from `CommandContext` via
`typer.Context`. `CommandContext` exposes only the source mapping — never
capability policy or private lifecycle helpers.

The extension seam exists for genuinely **backend-specific** commands
(presigned URLs, object versions, storage class). Core commands are
backend-neutral and are registered directly.

## 14. Evidence rules

Hermetic evidence is required for every claimed `pass` or `unsupported` row in
[`matrix.md`](matrix.md). It MUST:

- prohibit unplanned network access;
- use deterministic Local temporary storage, isolated Memory state, or a fully
  mocked `vosfs` transport;
- exercise the public `App(sources).typer_app` seam, not a private helper;
- run against the declared Python and operating-system CI matrix; and
- test an isolated built wheel before release, so undeclared dependency leakage
  cannot satisfy a result accidentally.

The three tested source forms are:

- `local / adapted async` — `AsyncFileSystemWrapper(LocalFileSystem(), asynchronous=True)`
- `memory / adapted async` — `AsyncFileSystemWrapper(MemoryFileSystem(), asynchronous=True)`
- `vosfs / native async` — a fresh `VOSpaceFileSystem(asynchronous=True, skip_instance_cache=True)`

Raw synchronous instances, wrong-mode async filesystems, and host-owned
reusable instances are outside these forms. **A protocol name alone never
identifies a tested row.**

Credentials, tokens, certificates, and entry names MUST NOT appear in evidence.
