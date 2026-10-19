# Agent skills

## Issue tracker

Issues are tracked in GitHub; external PRs are not a triage surface. See `docs/agents/issue-tracker.md`.

## Triage labels

Uses `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, and `wontfix`. See `docs/agents/triage-labels.md`.

## Domain docs

See `docs/agents/domain.md`.

## Cursor Cloud specific instructions

This repo is a `uv` workspace with three library packages under `libs/`: `vos`
(`libs/vos`), `vosfs` (`libs/vosfs`) and `fss-cli` (`libs/fss-cli`). There
are no long-running services, servers, or databases; "running" the products
means exercising the libraries and the `vcp`/`vls`/... commands from `vos`.

- Sync with `uv sync --locked --all-packages`. Plain `uv sync --locked` installs
  only the root tooling, which makes the `ty` hook fail with unresolved imports.
  CI runs hooks via `uv run --all-packages`.
- The full local validation gate (lint/format/type/test/docs/build) is documented
  in `CONTRIBUTING.md`; run those exact commands rather than re-deriving them.
- Offline tests (`uv run --package <package> pytest libs/<dir>/tests`) are
  deterministic and need no network or credentials. The vos tests under
  `libs/vos/tests/integration` need CADC credentials and are not collected by
  default.
- `uv run --all-packages zensical build --strict --clean` builds docs to
  `site/`; do not commit `site/` or `dist/` (both are gitignored).
