# Rename fsspec-cli to fss-cli

> **Partially supersedes
> [Release fsspec-cli independently inside the vosfs workspace](./0001-release-fsspec-cli-independently.md):**
> only its PyPI deferral is replaced. Independent releases remain accepted.

When `vosfs` and `fsspec-cli` moved into the `opencadc/vostools` workspace, the
command library was renamed `fss-cli` so it can be published on PyPI, where the
[`fsspec-cli` name](https://pypi.org/project/fsspec-cli/) belongs to an
unrelated project and `fss-cli` is free. The distribution is `fss-cli`, the
import package is `fss_cli`, its logger hierarchy is `fss_cli`, and the
workspace directory, commit scope and release component are all `fss-cli`. The
library's API, commands and behaviour are otherwise unchanged.

## Consequences

- Hosts change `fsspec-cli` to `fss-cli` in their requirements and
  `fsspec_cli` to `fss_cli` in imports and logging configuration. No
  compatibility alias is shipped, so the rename is a breaking change.
- `fss-cli` releases are tagged `fss-cli-vX.Y.Z` and can be published to PyPI.
  The imported `fsspec-cli-vX.Y.Z` tags keep their names, because they match the
  existing GitHub Releases of `shinybrar/vosfs`.
- CHANGELOG entries, earlier ADRs and research notes keep the old name, because
  they record what was true when they were written.
