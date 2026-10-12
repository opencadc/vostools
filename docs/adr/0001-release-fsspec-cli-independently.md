# Release fsspec-cli independently inside the vosfs workspace

> **Partially superseded by
> [Own async filesystems per command invocation](./0002-own-async-filesystems-per-invocation.md):**
> only the live-instance `App(filesystems)` injection and ownership clauses are
> replaced.
> Independent release and workspace decisions remain accepted.
>
> **Partially superseded by
> [Rename fsspec-cli to fss-cli](./0010-rename-fsspec-cli-to-fss-cli.md):**
> the PyPI deferral is replaced; the package is now `fss-cli`.

To support co-development now without coupling future extraction to `vosfs`,
`fsspec-cli` will be a self-contained uv workspace member at `src/fsspec-cli`
with its own `pyproject.toml`, `README.md`, `CHANGELOG.md`, `src/fsspec_cli`, and
`tests`, while sharing the repository's single `uv.lock`. It starts at version
`0.1.0` and Release Please gives it separate release PRs, a changelog, and
`fsspec-cli-vX.Y.Z` tags so it can release on its own schedule; each GitHub
Release contains only its wheel and sdist and does not trigger the `vosfs`
documentation deployment, while `vosfs` keeps its existing `vX.Y.Z` tag lineage
and versioned-documentation deployment. The library-only distribution's v1
integration seam and filesystem lifecycle are defined by the superseding ADR;
it defines no project script or package-owned shell executable and declares
only generic `fsspec` and `typer` runtime dependencies, so neither distribution
depends on the other at runtime.

## Consequences

- PyPI publication is deferred because the
  [`fsspec-cli` name](https://pypi.org/project/fsspec-cli/) belongs to an
  unrelated project.
- VOS hosts install both distributions and provide a configured async
  filesystem source; integration tests own VOS compatibility.
- CI tests the built `fsspec-cli` wheel in isolation so the shared workspace
  environment cannot hide undeclared dependencies.
