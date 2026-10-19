# vostools

Python tools for [VOSpace](https://www.ivoa.net/documents/VOSpace/), primarily the CADC / CANFAR services. The repository is a [uv](https://docs.astral.sh/uv/) workspace with three packages under `libs/`.

| Package | What it is | Docs |
| --- | --- | --- |
| [`vos`](libs/vos/) | Client library and the `vcp`, `vls`, `vsync`, … commands | [libs/vos/README.md](libs/vos/README.md) |
| [`vosfs`](libs/vosfs/) | Async fsspec filesystem for OpenCADC VOSpace | [User guide](libs/vosfs/docs/user/index.md) |
| [`fss-cli`](libs/fss-cli/) | Library-only POSIX-shaped commands for async fsspec filesystems | [CLI guide](libs/vosfs/docs/user/cli/index.md) |

Architecture decisions are in [`docs/adr/`](docs/adr/). [CONTRIBUTING.md](CONTRIBUTING.md) is the full local gate.

## Set up

```bash
uv sync --locked --all-packages
uv run pre-commit install --install-hooks --hook-type pre-commit --hook-type commit-msg
```

## Test

```bash
uv run --package vos pytest libs/vos/tests
uv run --package vosfs pytest libs/vosfs/tests
uv run --package fss-cli pytest libs/fss-cli/tests
```

`uv run --package vos -p 3.14 pytest libs/vos/tests` uses a specific Python (uv downloads it if needed). The vos live tests need CADC credentials and are not collected by default:

```bash
uv run --package vos pytest libs/vos/tests/integration
```

## Documentation

The vosfs and fss-cli user documentation is one Zensical site:

```bash
uv run --all-packages zensical build --strict --clean
```

## Lint, types, and commits

The root `pyproject.toml` holds the shared ruff, ty, and commitizen configuration. `libs/vos` extends it with a narrower rule set until it matches that policy.

```bash
uv run pre-commit run --all-files
```

Commit messages and pull request titles are [Conventional Commits](https://www.conventionalcommits.org/) with scopes `vos`, `vosfs`, `fss-cli`, or `repo`, for example `fix(vos): handle missing node properties`.

## Continuous integration

`quality.yml` always runs the hooks, repository contract tests, a strict Zensical build, package builds, and distribution metadata checks. Its `Required` job is the only check branch protection needs.

Each package has a path-filtered workflow (`ci-vos.yml`, `ci-vosfs.yml`, `ci-fss-cli.yml`) that runs when that package's tree or a shared workspace file changes. `ci-fss-cli.yml` also watches `libs/vosfs/**` because fss-cli's tests depend on vosfs. Those workflows test Python 3.10–3.14 on Linux and 3.12 on macOS.
