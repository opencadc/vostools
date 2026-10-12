# `fss-cli` design documentation

`fss-cli` turns host-configured async [fsspec](https://filesystem-spec.readthedocs.io/)
filesystems into POSIX-shaped [Typer](https://typer.tiangolo.com/) commands a
host embeds in its own CLI.

## The four documents

| Document | Holds | Read it when |
| --- | --- | --- |
| [`contract.md`](contract.md) | Invariants every command inherits: operand syntax, the Typer/command ownership split, diagnostics, result validation, exit status, source lifecycle, metadata normalization, capabilities, extensions. | You are implementing or changing **any** command. |
| [`commands.md`](commands.md) | One section per command: its form, the backend hooks it awaits, its output, and its delta from the contract. | You need the exact behavior of one command. |
| [`lessons.md`](lessons.md) | The findings that cost real investigation, stated independently of any one command. | Before changing a rule that looks arbitrary — it probably isn't. |
| [`matrix.md`](matrix.md) | Which command and source-form combinations have qualifying evidence. | You are making a compatibility claim or cutting a release. |

Architecture decisions live in [`../../adr/`](../../../../../docs/adr/). The `vosfs` backend
is governed by its own separate contract, [`../trd.md`](../trd.md).

## Reading order

Start with [`contract.md` §1–§3](contract.md) for what the CLI claims and how
operands work, then the command you care about in
[`commands.md`](commands.md). If a rule looks over-engineered, check
[`lessons.md`](lessons.md) before removing it.

## What is normative

The **tests are the executable evidence.** These documents describe the
contract the tests enforce; where prose and a passing gate disagree, the gate
wins and the prose is the bug. Per-command tests live in
`src/fss-cli/tests/`.

## History

This directory replaces 41 separate design documents (350,168 bytes), which had
accumulated the same invariants restated in 15–25 files each — the RFC 2119
preamble in 18, the client baseline in 25, the source-lifecycle paragraph in
19. Six of those documents had already declared themselves superseded or
non-normative in their own front matter.

The condensation preserved the normative content and the rationale, and dropped
the duplication, the hand-transcribed CI metadata, and the 88 matrix rows whose
status was `unverified` (which the matrix's own rules define as the meaning of
an absent row). Git history holds the originals; `main` before this change is
the last commit that carries them.
