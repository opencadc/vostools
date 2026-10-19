# Implementation handoff

<!-- pyml disable line-length -->

Status: **Reusable execution contract.** System-agnostic. Handed to a subagent
together with a **spec**; it defines *how* to take a spec from its first ticket
to a fully merged, review-clean whole. It hard-codes no project, path, or
command — the spec and the project's own contributing guide supply those.

> **All agents and subagents in this process communicate in `/caveman` style** —
> terse, fragments fine, every technical fact and identifier intact, no filler,
> no narration. This applies to implementers, reviewers, and the doc-check
> subagent alike.

## What you have been given

- A **spec** — the source of truth for *what* to build and the invariants it must
  hold. It wins on every question of behavior.
- An **epic** whose sub-tickets carry a dependency graph (`blocked_by` edges).
- **This handoff** — the *how*. It wins on process only.

Read the spec and the ticket first. Do not restate spec detail here.

## Two loops

Work advances in **waves**, not one ticket at a time. A *wave* is every ticket
with no open blocker. Implement the whole wave in parallel, review the wave
**once**, merge, and the merges unblock the next wave. The heavy multi-reviewer
suite runs **once** at the very end, over the finished whole.

```text
per wave:    worktrees (parallel) → /implement each → ONE /code-review over the wave → fix once → merge wave → next wave
end of spec: thermo + ponytail + deslop + code-review + improve-architecture + simplify → fix → repeat until ALL happy
```

## Per-wave loop

### 1. Start the whole unblocked wave in parallel

From the epic's dependency graph, take **every ticket with no open blocker** —
the current wave — and start them **in parallel**, each in its own git
**worktree**/branch. Always a worktree; never the default checkout; never a
worktree shared across tickets. One ticket = one branch = one PR.

### 2. `/implement` each (concurrently)

Invoke `/implement` per ticket. Build to the spec; keep the validation gate
(lint, types, tests, docs build, any live/release gate the spec names) green;
apply **deterministic auto-fixes** (formatter, lint `--fix`, deslop/simplify's
mechanical rewrites) **inline** — those are never review rounds.

**Docs-only tickets** are not code: give them a **small doc-check subagent**, not
`/implement`'s heavy path. It verifies the docs are up to date, **factually
correct against the code**, and follow the repo's language style/standard. That
subagent *is* their review — they skip `/code-review` (step 3).

### 3. One `/code-review` for the whole wave

Run **exactly one** deep `/code-review` over the wave's **combined diff** (all the
wave's branches), scoped to the diff — not per ticket, not the whole repo.
**Incorporate its feedback once**, blocking merges **only on
correctness/security/spec**; roll style, simplification, and architecture nits
**forward to the end-of-spec gate**.

### 4. Merge the wave

Gate green, CI green, the one review's blocking findings addressed, hooks never
bypassed. Merge the wave's PRs; tick the epic checklist; delete the worktrees.

### 5. Advance

Sync the default branch. The merges unblock the **next wave** — repeat from step 1
until no tickets remain.

## Spec-completion gate (once, after the last wave)

Only when **every** sub-ticket is merged, run the full suite as the **last gate**
— the single place the heavy, cross-cutting quality reviews live.

Spawn these **in parallel, each its own subagent**, over the spec's whole change:

- `/thermo-nuclear-code-quality-review`
- `/ponytail-review`
- `/deslop`
- `/code-review`
- `/improve-codebase-architecture`
- `/simplify`

Then **iterate until every reviewer is happy**: fix the findings, **re-run only
the reviewers that flagged something** (over the changed files), and repeat until
all pass and the gate is green. Converge — don't re-run the whole suite each
round, and **cap the rounds** (e.g. 3); leftover nits become follow-up tickets.
(Run whichever reviewers the environment provides; note any unavailable.)

## Keeping it fast

- **One `/code-review` per wave**, not per ticket, scoped to the diff.
- **Severity-gate**: block only on correctness/security/spec; every quality nit
  defers to the final gate.
- **Parallelize the whole wave** in worktrees, driven by the dependency graph.
- **Docs get a small factual/style subagent**, never the review suite.
- **Auto-apply mechanical fixes** (format, lint `--fix`, deterministic
  deslop/simplify) without a deliberation round.

## Keep artifacts lean

**Do not generate a frivolous trail** of notes, reports, or summaries. Keep
context where the work lives: update the **ticket / sub-ticket itself** (its body
or a single comment) only when context is genuinely needed. The spec, the epic,
and the tickets are the record — nothing runs a parallel paper trail beside them.

## Rules of engagement

- **All agents/subagents speak in `/caveman` style.**
- **Spec wins on *what*; this handoff wins on *how*.** If they conflict, the spec
  is right about behavior — fix the process, or raise the spec; never silently
  deviate.
- **Always a worktree; one ticket = one branch = one PR; whole wave in parallel.**
- **One `/code-review` per wave**, blocking only on correctness/security/spec.
- **Docs tickets get a small factual/style subagent**, not a code review.
- **The heavy suite runs once, at the end**, and loops (capped) until all pass.
- **Never bypass hooks or gates; never weaken a test to make it pass.**
- **Out-of-scope findings become follow-up tickets**, never silent scope creep.
- **Behavior-preserving refactors must prove it**: the suite is unchanged and
  green before and after.
- **Keep context in the tickets** — no frivolous artifact trail.
