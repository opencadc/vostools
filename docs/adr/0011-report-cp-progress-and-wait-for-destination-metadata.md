# Report `cp` progress and wait for destination metadata

> **Partially supersedes
> [Release fsspec-cli independently inside the vosfs workspace](./0001-release-fsspec-cli-independently.md):**
> only its list of runtime dependencies is replaced; `fss-cli` now also
> requires `rich` directly. Neither distribution depends on the other at
> runtime.

Status: Accepted

A file `cp` printed nothing until it finished, and a destination whose `_info`
lagged behind a successful write failed with one generic
`verification failure` line naming the typed operand. Users could not tell a
slow transfer from a stuck one, or a metadata race from a real disagreement.

## Decision

Draw byte progress with one shared `rich.progress.Progress` per `cp`
invocation on stderr, and only when stderr is a terminal and `NO_COLOR` is not
set to a value. fsspec's `callback` still drives it: a small
`fsspec.callbacks.Callback` subclass updates one Rich task per file. A
cross-source file keeps one task whose description moves from the download to
the upload; a same-source file gets one only when `_cp_file` declares a
`callback` parameter. Parallel `cp -R` transfers each hold a live task on the
same display, and a task is removed when its file finishes. `rich` is a
declared runtime dependency even though Typer already requires it, so the
command never imports an undeclared transitive package.

After each file is written, print the resolved destination and byte count,
then poll destination `_info` every 5 seconds for at most `--metadata-wait`
seconds (default 60) while it still reports no size, the size it had before
the copy, or, at the right size, the shared tokens it had before the copy.
Any other disagreement is reported at once and never retried into success.
The command never deletes the destination and never says the bytes are bad.
`cp -R` applies the same wait to every file it uploads.

## Consequences

- `cp` success is no longer silent: each written file gets two stderr lines,
  printed above the live bars. Stdout stays empty.
- Status `0` still requires a destination file whose size equals the frozen
  source size and whose shared tokens match. A run that ends while metadata
  is still stale exits `1` with `not yet verified`.
- A backend that never calls the callback shows no bar; the outcome lines
  still print.
- `cp -R` keeps its final destination proof after the per-file checks, so each
  copied file is read once more. `mv` keeps its existing verification.

Normative detail belongs in `libs/vosfs/docs/design/fss-cli/commands.md`.
