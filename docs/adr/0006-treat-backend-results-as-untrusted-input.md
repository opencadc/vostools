# Treat backend results as untrusted input

Status: Accepted

Question: The CLI validates every field of every value an fsspec hook returns —
exact types, path shapes, mapping sizes, list lengths. That volume of checking
had no recorded decision behind it, so it read as defensive noise and was
repeatedly proposed for removal.

## Decision

**`fsspec-cli` trusts the host's choice of backend, and does not trust the
values that backend returns.**

The host decides which filesystems are reachable, by constructing `App` with a
source mapping. That is a trust decision, and the CLI does not second-guess it:
it never inspects backend type, protocol, or class to decide what a command may
do.

Every *result* crossing the hook boundary is validated before use:

- exact type checks for integers (`type(value) is int`, so `bool` cannot pass);
  `str`, `list`, and `Mapping` accept subclasses;
- exact container types and lengths for batch results;
- path shape checks (consistent lexical parent, no NUL, newline, carriage
  return, or dot segment) on values that will be joined, compared, or rendered;
  backend-returned names may omit a leading slash, as S3-style names do, but
  lexical aliases must not conceal duplicate entries;
- mapping traversal through `for key in mapping` and `mapping[key]`, with a
  snapshot of `len(mapping)` compared against the number of entries actually
  enumerated.

A value that fails validation produces `incompatible result` and status `1`. It
is never coerced, defaulted, dropped, or partially emitted.

## Why

fsspec's `AbstractFileSystem` fixes hook *names*, not result *shapes*. Backends
are third-party code of widely varying maturity, and the CLI composes their
results into two kinds of dangerous operation:

1. **Rendering to a shell.** Paths from a backend reach a terminal and,
   through pipes, other programs. An unescaped control character or newline in
   a returned path is a correctness and safety problem regardless of intent.
2. **Deriving mutations.** Recursive `cp` and `rm` build a manifest from
   `_walk` / `_ls` results and then *delete or overwrite* along those paths. A
   malformed or duplicated entry is not a rendering bug; it is data loss.

The failure mode being defended against is overwhelmingly a **buggy or
unfinished backend**, not a malicious one — a backend that returns `None` for a
sparse size, a `tuple` where fsspec documents a `list`, or a relative path from
a walk. Those are the bugs that showed up in practice, and they surface as a
clear diagnostic instead of a traceback or a wrong deletion.

## Consequences

- Result validation stays, and is not treated as removable defensiveness. The
  `# noqa: BLE001` boundaries that classify hook exceptions are part of the same
  decision.
- Validation lives at the boundary, in the command that awaits the hook — not
  scattered through rendering.
- A backend that is merely *sparse* (no `mode`, no `mtime`) is **not** invalid.
  Missing optional metadata is normalized to `None` and displayed as unknown
  in the stable shell columns defined by [ADR 0008](0008-render-shell-shaped-long-listings.md);
  only a value of the wrong *shape* is an incompatible result. See
  [`libs/vosfs/docs/design/fss-cli/contract.md` §10](../../libs/vosfs/docs/design/fss-cli/contract.md#10-metadata-normalization).
- The CLI cannot accept a backend-declared capability registry: that would mean
  believing a backend's self-description instead of validating what it returns.
- The cost is real — validation is a visible share of the recursive-copy module
  — and is accepted for the mutation paths. Read-only commands validate the
  shapes they render and no more.
