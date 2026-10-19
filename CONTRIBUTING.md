# Contributing to vostools

This guide defines the contribution workflow for humans and automated agents.
Agents must also follow `AGENTS.md`; repository configuration and required CI
checks are the executable enforcement of this policy. If they disagree, fix
the policy and configuration together in the same pull request.

The repository is a uv workspace with three packages under `libs/`: `vos`,
`vosfs` and `fss-cli`.

## Ground rules

- Work on a branch. Do not push changes directly to `main`.
- External contributors should fork the repository, push their branch to the
  fork, and open a pull request against this repository. Maintainers and agents
  may branch in a canonical clone.
- Keep each pull request focused.
- Never bypass hooks with `--no-verify`.
- Use `uv` for Python versions, environments, dependencies, and project
  commands. Do not maintain a parallel `pip`, Conda, or requirements-file
  workflow.

## Set up the repository

Install [uv](https://docs.astral.sh/uv/getting-started/installation/), clone the
repository, and run:

```bash
uv sync --locked --all-packages
uv run pre-commit install --install-hooks \
  --hook-type pre-commit \
  --hook-type commit-msg
```

The root `dev` dependency group must contain every contributor tool used by the
hooks or CI. Each package's own `dev` group holds only what its tests need. The
hook configuration must cover Ruff formatting and linting, ty type checks,
Commitizen message validation, general file-safety checks, and Markdown checks.

## Make a change

- Support every Python version allowed by the package's
  `project.requires-python`. CI tests 3.10 to 3.14 on Linux and 3.12 on macOS.
  Linux and macOS are the supported host platforms; other platforms are
  untested and unsupported.
- Keep each package's module under `libs/<package>/src/` and its tests under
  `libs/<package>/tests/`, so tests never ship in wheels. Add type annotations
  to public APIs.
- Use Ruff as the only Python formatter and linter, and ty as the type checker.
  The shared policy lives in the root `pyproject.toml`; a package that is not
  yet clean extends it with a narrower rule set that only ever shrinks.
- Add or update pytest tests for observable behavior. Tests must be
  deterministic and offline.
- Keep each package at or above its configured coverage floor (`vosfs`
  requires 90% branch coverage).
- Update user-facing Markdown in the same pull request as behavior changes.
  Do not document commands or APIs that do not exist.
- Do not edit `uv.lock` by hand. Use `uv add`, `uv add --dev`, `uv remove`, or
  `uv lock`, and commit `pyproject.toml` and `uv.lock` together.

All hand-authored Markdown files must pass the configured Markdown lint,
trailing-whitespace, and end-of-file checks. Public documentation under
`libs/vosfs/docs/user/` must also pass a strict Zensical build. Generated
`CHANGELOG.md` files are excluded from PyMarkdown. Do not commit generated site
output.

## Validate the change

Run focused tests while working. Before opening or updating a pull request, run
the complete local gate:

```bash
uv lock --check
uv run --all-packages pre-commit run --all-files
uv run pytest tests/repo
uv run --all-packages zensical build --strict --clean
uv run --package vos pytest libs/vos/tests
uv run --package vosfs pytest libs/vosfs/tests
uv run --package fss-cli pytest libs/fss-cli/tests
uv build --no-sources --all-packages
```

If a hook changes files, review the changes, stage them, and run the gate
again. The pull request must pass the same required CI checks before merge.

## Commit messages

Every commit and pull request title must follow
[Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/):

```text
<type>(optional-scope): <imperative description>
```

Allowed types are `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`,
`build`, `ci`, `chore`, and `revert`. Scopes are `vos`, `vosfs`, `fss-cli` and
`repo`; comma-separate several. Use `!` and a `BREAKING CHANGE:` footer for a
breaking change.

Humans should use Commitizen's interactive prompt:

```bash
uv run cz commit
```

Agents and other non-interactive automation may create the message directly;
the `commit-msg` hook must still validate it. Keep commits small and logical,
and do not mix unrelated changes.

## Releases

Release Please opens one pull request per package (`libs/vos`, `libs/vosfs`,
`libs/fss-cli`) on `shinybrar/vostools` and `opencadc/vostools`. Merging that
pull request tags `<package>-vX.Y.Z` and opens a draft GitHub Release. Only
`opencadc/vostools` then dispatches publication of that package path and a
build of the single documentation site.

`cibuild.yml` still publishes a legacy `vos=<version>` tag. It stays until the
vos PyPI trusted publisher is moved to `pypi.yml`.

Do not push the historical `vosfs-v*` or `fsspec-cli-v*` tags. An annotated
`vos-v3.7.0` tag, if you want one, belongs on `e96b7630` (the release-please
baseline), not on the older `vos=3.7` commit `b74878d8`. Pushing it onto the
old commit would make the next vos release include the entire modernization
history.

## Open the pull request

The pull request description must explain what changed, why it changed, and how
it was validated. Include documentation and lockfile changes when required,
then wait for required CI and review before merge.
