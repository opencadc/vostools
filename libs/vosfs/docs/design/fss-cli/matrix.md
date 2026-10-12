# `fss-cli` tested command matrix

Status: **Evidence record.** Records which command-and-source-form
combinations have qualifying evidence. This is a narrow compatibility claim,
not a statement that `fss-cli` works with every fsspec backend.

Evidence rules, the three tested source forms, and the hermetic gate are
defined in [`contract.md` §14](contract.md#14-evidence-rules).

## How to read this

**A row that is not listed is `unverified`.** `unverified` is neutral: it does
not block adding a backend, and it never means `unsupported`. It blocks only a
release claim that explicitly requires that row. Rows are listed by command and
source form, not by backend column, so a source-independent rejection is
recorded once instead of duplicated across backends that were never entered.

| Status | Meaning |
| --- | --- |
| `pass` | The positive contract passed every required gate for this exact build, dependency set, source form, and platform. |
| `fail` | A qualifying test **reached** the behavior and contradicted the contract. Kept as `fail` — see [`lessons.md` §14](lessons.md#14-keep-fail-when-a-gate-reaches-a-real-contradiction). |
| `unsupported` | The contract deliberately excludes the behavior and a negative test proves its complete rejection. |

An infrastructure or test-setup failure that prevents observation is
inconclusive: `unverified`, not `fail`.

## Freshness

Evidence is current only for its recorded version and source-form identity.
Changing the contract, the relevant implementation, fsspec, Typer, the backend
distribution, the adapter mode, or the declared platform set makes the affected
row `unverified` until its gates run again. There is no wall-clock expiry;
every release candidate obtains new evidence for its exact build.

Declared supported host platforms: **Linux and macOS**.

## Rows with evidence

Evidence is the named test module at the recorded commit, run under both the
hermetic gate and the isolated installed-wheel gate. Full run metadata —
runner image, Python patch version, resolved dependency set — lives in the
CI run itself and in that commit's `uv.lock`; it is deliberately **not**
transcribed here.

| Command surface | Scope | Source form | Status | Evidence |
| --- | --- | --- | --- | --- |
| Plain `ls` | source | `local`, `memory`, `vosfs` | `pass` | `test_listing.py`, `test_command_matrix.py` |
| Plain `cat` | source | `local`, `memory` | `pass` | `test_cat.py`, `test_cat_process.py` |
| `cat` stdin and `-` | stdin / mixed | `memory` | `pass` | `test_cat.py`, `test_cat_process.py` |
| Base `mkdir` | source | `local`, `vosfs` | `pass` | `test_mkdir.py` |
| Base `mkdir` | source | `memory` | **`fail`** | `test_mkdir.py` — reached contradiction, not softened |
| Base `rmdir` | source | `local`, `memory`, `vosfs` | `pass` | `test_rmdir.py` |
| Base file-only `rm` | source | `local`, `memory`, `vosfs` | `pass` | `test_rm.py` |
| `rm -d` | source | `vosfs` | `pass` | `test_vosfs_command_matrix.py` (mocked transport) |
| Cross-source `cp` | source pair | `local`↔`memory` | `pass` | `test_cp.py`, `test_command_matrix.py` |
| Multi-source `cp` | source | `local`, `memory` | `pass` | `test_cp.py`, `test_command_matrix.py` |
| Reduced `stat` | source | `local` | `pass` | `test_stat.py`, `test_command_matrix.py` |

### Proven rejections

| Rejected surface | Scope | Status | Evidence |
| --- | --- | --- | --- |
| `rmdir -p` | command preflight | `unsupported` | `test_rmdir.py` |
| `cat -u` | command preflight | `unsupported` | `test_cat.py` |
| Base `rm` option rejection | command preflight | `unsupported` | `test_rm.py` |
| Same-source directory `mv` | source | `unsupported` | `test_mv.py` — `_info` only; no target resolution, staging, or `_mv` |
| Same-source multi-file `mv` shape | command preflight | `unsupported` | `test_mv.py` |
| Cross-source `mv` | command preflight | `unsupported` | `test_mv.py` — see [`lessons.md` §5](lessons.md#5-cross-source-mv-is-rejected-because-deletion-cannot-be-proven) |
| Reduced `stat` option and operand forms | command preflight | `unsupported` | `test_stat.py`, `test_command_matrix.py` |
| `cp -R` / `rm -R` when the capability is disabled | command preflight | `unsupported` | `test_typed_cp.py`, `test_typed_rm.py` |

## CI and release policy

Every release candidate re-runs every required gate for its exact build. A
manually edited status cannot override a failing gate: the tests and their
required CI checks are the executable evidence, and this document is a
reader-facing index of them.

The matrix MUST NOT be loaded at runtime or used as capability negotiation —
see [`lessons.md` §16](lessons.md#16-the-matrix-is-evidence-never-a-runtime-input).

## Rejected alternatives

- **Machine-readable source plus generated Markdown.** No consumer exists. A
  generator would add a build step and a schema to maintain for one document.
  Revisit when something actually reads it.
- **One backend-column table.** Columns force a cell for every
  backend/command pair, including pairs that were never entered, which reads as
  a claim. Rows record exactly the claims that have evidence.
- **A backend-declared capability registry.** That inverts the trust model: the
  CLI would believe a backend's self-description instead of validating results.
  See [`docs/adr/0006-treat-backend-results-as-untrusted-input.md`](../../../../../docs/adr/0006-treat-backend-results-as-untrusted-input.md).
