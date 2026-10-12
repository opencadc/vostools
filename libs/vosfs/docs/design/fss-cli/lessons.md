# `fss-cli` implementation lessons

Status: **Durable rationale.** These are the findings that cost real
investigation, stated once and independently of any single command. They
explain *why* [`contract.md`](contract.md) and [`commands.md`](commands.md) are
shaped the way they are. Changing one of these rules means re-doing the
research behind it.

## 1. `isinstance` is the wrong check for backend results

`bool` subclasses `int`. A backend returning `True` from `_size` passes
`isinstance(value, int)` and renders as size 1.

Every result check therefore uses `type(value) is int`, never `isinstance`.
The same applies to `type(result) is list` for batch results — a `tuple` or a
custom sequence is not a `list`, and accepting it silently changes the
length and ordering guarantees the command depends on.

## 2. Validate the whole result before writing any of it

Commands buffer completely, then write once. The reason is not tidiness: a
partially emitted record cannot be retracted from a pipe. If element 7 of a
listing is malformed, elements 1–6 must not already be on stdout.

This is why "no partial block for that operand" appears throughout `ls -l`, and
why `cat` guarantees per-operand atomicity across validation *and* staging.

## 3. Cancellation must not orphan a started operation

An `await` that is cancelled leaves the backend call in flight. If the CLI then
exits, the operation completes unobserved — and for a mutation, the caller has
no idea whether it happened.

`_drain_current_operation` wraps each started operation in a shielded task, so
caller control flow (cancellation, `KeyboardInterrupt`, an escaping exception)
propagates only *after* the in-flight hook resolves. This is why it appears at
roughly 25 call sites rather than being inlined per command.

## 4. A dispatched mutation has three outcomes, not two

Success and failure are insufficient. Once a mutation call has been dispatched,
a subsequent failure to *observe* the result leaves state **uncertain** — the
mutation may have applied.

Hence the post-mutation absence proof used by `rmdir`, `unlink`, and `rm`:

- absence after the call proves success, **even if the call itself raised**;
- presence after a call that raised is confirmed failure;
- an error that is not `FileNotFoundError` during the check is *uncertain*, and
  is reported as such rather than as either outcome.

Reporting an uncertain state as failure would be a lie in the safe direction,
but still a lie — a caller retrying on "failure" can double-apply.

## 5. Cross-source `mv` is rejected because deletion cannot be proven

A correct cross-source move must delete the *same generation* of the source it
copied. Proving that needs a source-supplied immutable generation token whose
contract guarantees a new value on any replacement or in-place mutation.

No tested source form supplies one. The probes:

- **Local** — an in-place four-byte edit followed by restoring `st_mtime_ns`
  left the candidate `(type, size, ino, mtime)` fingerprint *exactly*
  unchanged. `created` does not repair it: fsspec uses birth time when
  available and ctime otherwise, so the contract varies by platform.
- **Memory** — an in-place edit through `r+b` left the complete before and
  after `info` mappings comparing equal. Removal is unconditional.

Even with a token, revalidating and then deleting leaves a
time-of-check/time-of-use window. Without a source-owned *conditional* delete,
the deletion of the revalidated generation cannot be guaranteed.

So `mv` rejects distinct source names during synchronous preflight, before any
event-loop entry. Directory movement is explicit `cp -R` then `rm -R`, each
with its own contract and its own result. A future positive profile must state
the residual race and must not claim atomic rename, transactionality, or
rollback.

## 6. Configured source names define the boundary — not backend identity

Two sources mapped to the same backend class, protocol, or even the same
callable are still *different sources*. Shared implementation never makes a
cross-source operation same-source.

This keeps the boundary something the host declares and the CLI can check
synchronously, rather than something inferred from runtime object identity.

## 7. Capability gating belongs in the signature, not in a runtime check

`cp -R` and `rm -R` are gated by selecting a *different annotated callback*,
so the option does not exist when the capability is off. Typer then rejects it
before operand parsing or source acquisition, and `--help` never mentions it.

A runtime `if not capability: error` would have to run after parsing, would
show the option in help, and would need its own diagnostic. Encoding policy in
the signature deletes all three problems.

## 8. Batch when the backend offers a batch hook

`size` groups operands by source and awaits one `_sizes(paths)` per group
rather than one `_size` per operand. For a remote backend that is the
difference between one round trip and N.

The grouping preserves first-appearance order and duplicates so output can be
reassembled in original operand order. What fsspec's *inherited* `_sizes` does
internally is backend-owned and does not change the CLI's contract.

## 9. One CLI hook call is not one remote request

`head -c N` awaits one bounded `_cat_file`. That bounds what the CLI *asks
for*; the physical transfer follows the backend: `vosfs` may send `Range` and
keep a `206`, or fall back to a whole-object body when the endpoint answers
`200`.

Likewise a recursive command performs one listing request per reached
directory. fsspec's inherited `_walk` awaits those one at a time and forwards
`on_error` only to the top level, so the CLI walks breadth-first itself: one
bounded concurrent batch of `_ls(detail=True)` per depth, failing on an
unreadable directory at any depth. `du -s` changes the output, not the
traversal cost.

Document the *request the CLI makes*, never the network behavior it cannot
control.

## 10. Bound the work before starting it

Recursive `cp` freezes a manifest of the source tree — bounded at 10,000
entries — *before* any mutation, and rejects links and special entries at
manifest time. Recursive `rm` builds its complete manifest before removing
anything, then removes leaves-first.

Building the plan first means the rejection happens before, not during, the
mutation. It also makes the entry bound a clean refusal rather than an
out-of-memory failure halfway through a tree.

## 11. Recursive removal is non-atomic — say so

`rm -R` removes leaves-first, concurrently within one depth, and can leave earlier confirmed removals in place and the rest present or
uncertain if it fails or is cancelled. There is no prompt, rollback, retry,
trash, or recovery.

That is why it is off by default and why enabling it is the *host's* assertion
that every configured target satisfies the profile's concurrency and
containment assumptions.

## 12. Strict POSIX was the wrong bar

`fss-cli` originally rejected anything it could not implement completely —
`ls -l` was refused outright, because no single backend supplies every POSIX
column.

That produced a CLI that said "no" to the things users most wanted. The bar is
now a shell-compatible *experience*: render what the backend actually supplies,
in the shape a shell user expects, with unknown markers for missing fields. The two safety rules in
[`contract.md` §1](contract.md#1-what-fss-cli-claims) — never fabricate,
adaptive richness — are what make "best effort" honest rather than sloppy.

The cost of getting this wrong in either direction is real: fabricating a `0`
size or a fake mode is worse than showing an unknown marker, and refusing the command
is worse than a partial answer.

## 13. `-h` is human-readable, not help

In size-bearing commands (`ls -l`, `du`), `-h` means human-readable, matching
`ls -h` and `du -h`. `--help` remains the only help spelling. Reserving `-h`
for help would have made the CLI incompatible with the muscle memory of every
shell user for the commands where it matters most.

## 14. Keep `fail` when a gate reaches a real contradiction

The tested matrix once recorded base `mkdir` on Memory as `fail`. That row was
*reached* and contradicted the profile — so it stayed `fail` rather than being
softened to `unverified` or quietly dropped.

The distinction that makes the matrix worth keeping:

- test setup or CI infrastructure prevented observation → `unverified`;
- the command ran and violated its contract → `fail`;
- an explicitly excluded behavior proved its rejection → `unsupported`.

`unverified` is neutral and never means `unsupported`. A missing row is
`unverified` by definition — which is why the matrix lists only rows with
evidence.

## 15. Evidence is scoped to a tuple, not to a name

A result for plain `ls` says nothing about another command. A result for an
adapted Local source says nothing about a raw `LocalFileSystem`. A result for
one dependency set says nothing about a later one.

**A protocol name alone never identifies a tested row.** This is why the matrix
is row-oriented rather than backend-columned: a source-independent command
rejection is recorded once, instead of being duplicated across backends that
were never entered.

## 16. The matrix is evidence, never a runtime input

It MUST NOT be loaded at runtime, exposed as capability negotiation, or used to
replace real operation and result validation. The moment a CLI branches on
"what the matrix says", the matrix stops describing the system and starts
defining it — and every untested backend silently becomes a supported one.
