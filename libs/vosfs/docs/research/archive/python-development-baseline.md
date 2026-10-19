# Python Development Baseline: primary-source research

Research ticket: [#6](https://github.com/shinybrar/vosfs/issues/6)
Researched: 2026-07-09

This note records tool constraints for the Development Baseline. It is not a
configuration change. Repository facts used here: vosfs has a `src` layout,
declares `requires-python = ">=3.10"`, uses uv, and currently has `pytest` as
its only development dependency.

## Verified constraints

| Area | Primary-source fact | Consequence for vosfs |
| --- | --- | --- |
| uv | The `dev` dependency group is installed by default. `uv.lock` records exact, cross-platform resolutions, should be version-controlled, and `uv lock --check` fails when it is stale. | Put baseline developer tools in `[dependency-groups].dev`, retain `uv.lock`, and make CI verify rather than rewrite it. ([dependency groups](https://docs.astral.sh/uv/concepts/projects/dependencies/), [lockfiles](https://docs.astral.sh/uv/concepts/projects/layout/), [lock check](https://docs.astral.sh/uv/concepts/projects/sync/)) |
| pytest | pytest 9 supports native TOML in `[tool.pytest]`; `testpaths` intentionally limits discovery. | The existing pytest 9 dependency can keep its configuration in `pyproject.toml`. ([configuration](https://docs.pytest.org/en/stable/reference/customize.html)) |
| pre-commit | `.pre-commit-config.yaml` is the project configuration; `repo: local` is supported; `pre-commit run --all-files` is the documented CI invocation. | Local hooks can delegate to the uv-managed toolchain, and CI must run all files rather than rely on developers installing a Git hook. ([configuration and CI](https://pre-commit.com/)) |
| Ruff | Ruff reads `[tool.ruff]`; it chooses the closest configuration and does not merge parent files. With a project configuration, an omitted `target-version` is inferred from `project.requires-python`. | Keep one root Ruff configuration and use the existing `>=3.10` declaration as the target-version authority. ([configuration](https://docs.astral.sh/ruff/configuration/), [settings](https://docs.astral.sh/ruff/settings/)) |
| ty | ty reads `[tool.ty]` from `pyproject.toml`; `ty.toml` would take precedence. It derives its target Python version from the lower bound of `requires-python`, detects the `src` layout, and `uv run` supplies its virtual environment. | Configure ty in `pyproject.toml` and invoke it through uv; do not add a competing `ty.toml` or duplicate its Python version without a real exception. ([configuration](https://docs.astral.sh/ty/configuration/), [module discovery](https://docs.astral.sh/ty/modules/), [Python version](https://docs.astral.sh/ty/python-version/)) |
| Zensical | `zensical.toml` is the native configuration; `zensical build --strict` exits nonzero on validation warnings. | CI can validate Markdown without publishing a GitHub Pages site. ([setup](https://zensical.org/docs/setup/basics/), [strict validation](https://zensical.org/docs/setup/validation/)) |
| Release Please | Release Please parses Conventional Commits into release PRs. Its manifest configuration is source-controlled, supports `release-type: "python"`, and its Python strategy updates a present `pyproject.toml`. A Python manifest package needs a `package-name`. | The release convention and release files must be defined before enabling the workflow; the current root package is a valid Python strategy target, but the first release needs an explicit bootstrap/version decision. ([action](https://github.com/googleapis/release-please-action), [manifest configuration](https://github.com/googleapis/release-please/blob/main/docs/manifest-releaser.md), [Python strategy](https://github.com/googleapis/release-please/blob/main/src/strategies/python.ts)) |
| GitHub Actions | GitHub recommends `setup-python` for consistent Python setup, least-privilege `GITHUB_TOKEN` permissions, and full commit-SHA pins for immutable action revisions. | CI should declare only the permissions needed by each workflow and pin every third-party action by full SHA. ([Python workflows](https://docs.github.com/en/actions/tutorials/build-and-test-code/python), [token permissions](https://docs.github.com/en/actions/tutorials/authenticate-with-github_token), [secure use](https://docs.github.com/en/actions/reference/security/secure-use)) |

## Recommended baseline decisions

1. **Make uv the only tool installer.** Add `pytest`, `pre-commit`, `ruff`,
   `ty`, and `zensical` to the `dev` group; commit every resulting `uv.lock`
   update. CI should use `uv sync --locked` (or an equivalent locked `uv run`)
   before checks. This fails on dependency/lock drift instead of silently
   resolving a different environment.

2. **Use `pyproject.toml` for Python-tool policy.** Add `[tool.pytest]`,
   `[tool.ruff]`, and `[tool.ty]` there. Set pytest `testpaths` once a `tests/`
   tree exists. Start with Ruff's default lint rules and explicitly record any
   extensions; do not create a separate `ruff.toml` or `ty.toml` that changes
   discovery precedence.

3. **Have pre-commit call the locked uv tools.** Use local hooks for Ruff
   formatting, Ruff linting, and `ty check`, so hook behavior comes from the
   same dev group and `pyproject.toml` configuration as CI. Run
   `uv run --locked pre-commit run --all-files` in CI. Formatting may fix
   changed files locally; the CI invocation must fail if it would leave a
   diff.

4. **Make documentation a strict validation check only.** Add a minimal
   `zensical.toml` with the required site metadata and run
   `uv run --locked zensical build --strict` in CI. This is sufficient for the
   stated initial scope; GitHub Pages deployment remains out of scope.

5. **Bootstrap Release Please deliberately.** Use the Python strategy with a
   root package entry, the project package name (`vosfs`), and a manifest
   initialised to the current `0.1.0` version. Require a release-PR CI run of
   `uv lock --check`; if the version metadata makes the lock stale, regenerate
   and include `uv.lock` in that release PR. Do not assume Release Please
   updates `uv.lock`—its Python strategy updates package/version files, while
   uv owns lock resolution.

6. **Keep CI releases isolated.** Normal checks need only `contents: read`.
   Give the Release Please workflow the documented write permissions only at
   that workflow/job, pin every `uses:` reference to a reviewed full SHA, and
   decide separately whether its `GITHUB_TOKEN` behaviour is sufficient for
   follow-on workflow triggers.

## Deliberately unresolved

This research does not choose the Python/platform matrix, required-check
names, branch-protection/bypass policy, or PR-to-issue enforcement mechanism.
Those are governance decisions for the Development Baseline specification,
not tool facts.
