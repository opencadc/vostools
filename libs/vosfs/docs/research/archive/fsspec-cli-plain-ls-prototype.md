# Plain `ls` interoperability prototype

> **Historical, non-normative.** Issue
> [#242](https://github.com/shinybrar/vosfs/issues/242) retired the live-testing
> lane recorded here. It defines no current CI, release, or support requirement.

<!-- pyml disable line-length -->

Prototyped: 2026-07-15

Question: [shinybrar/vosfs#80](https://github.com/shinybrar/vosfs/issues/80)

Locked profile: [`fsspec-cli` plain `ls` command contract](../../design/fsspec-cli/commands.md#ls)

Client baseline: fsspec 2026.6.0

Prototype tip: `057538d4236c801a1c3a6a2d67d404748cd02950`

Status: **Locked verdict; prototype disposed.**

## Question

Can one Typer handler implement the locked plain-`ls` profile across Local,
Memory, hermetic `VOSpaceFileSystem`, and one narrow live OpenCADC listing
without backend-specific command logic?

## Locked finding

Yes, for the tested file-and-directory capability floor. One command handler
used only the supplied filesystem's public synchronous `info(path)` and, for
directories, `ls(path, detail=False)`. It produced the locked behavior across
Local, Memory, hermetic VOS, and the live VOS observation without filesystem
construction, backend-type checks, private hooks, or retry fallbacks. This was
the synchronous prototype surface; production directly awaits the documented
fsspec underscore coroutines selected later by issue #92.

This is evidence that a backend-agnostic plain-`ls` client is viable for tested
fsspec implementations. It is not evidence that every fsspec implementation
is compatible. Unsupported operations, incompatible result shapes, and the
remaining interoperability boundaries below remain explicit outcomes.

After the prototype verdict, one additional standing constraint was locked:
all production CLI orchestration and filesystem calls are async-only. The
synchronous prototype below remains evidence about command semantics, backend
result shapes, and the tested compatibility floor. No synchronous prototype
code is an implementation precursor. [Define the async execution boundary for
fsspec-cli](https://github.com/shinybrar/vosfs/issues/90) owns event-loop,
filesystem-instance, and host-embedding evidence. [Issue #92](https://github.com/shinybrar/vosfs/issues/92)
and [ADR 0002](../../adr/0002-own-async-filesystems-per-invocation.md)
subsequently lock the production contract.

## Superseded prototype seam

```python
App(filesystems: Mapping[str, AbstractFileSystem]).typer_app
```

The disposed prototype used host-owned live filesystem instances; production
injection and ownership are superseded by
[ADR 0002](../../adr/0002-own-async-filesystems-per-invocation.md). A host Typer
application can still mount the returned app with `add_typer`; no console script
or shell-installable surface was added.

The prototype preserves raw command arguments so the handler owns `-A`, `--`,
unsupported-option diagnostics, and multiple operands while exact `--help`
remains framework-owned.

## Environment

| Item | Observed value |
| --- | --- |
| OS | macOS 15.7.7 (`24G720`), arm64 |
| Python | 3.13.5 |
| fsspec | 2026.6.0 |
| Typer | 0.27.0, supplied transiently with `uv --with` |
| Locales | `C`; `sv_SE.UTF-8` |

## Acceptance matrix

| Area | Evidence | Result |
| --- | --- | --- |
| Single operands | Root, non-empty directory, empty directory, file, explicit dot file | Passed across Local, isolated Memory, and hermetic VOS. Literal root passed on Memory and VOS; Local used a temporary sandbox root. |
| Hidden entries | Default omission, explicit dot operand, `-A`, repeated/grouped `-A`, no synthetic dots, `-a` rejection | Passed. |
| Multiple operands | Cross-filesystem operands, duplicates, files-first grouping, independently sorted directories, empty headers, exact blank lines | Passed. |
| Backend calls | `info` for every valid operand; `ls(detail=False)` only for directories; complete invalid preflight makes zero calls | Passed through a supplied recording filesystem boundary. |
| Preflight | Missing operand, full mapped grammar, unknown names, option ordering, delimiter, NUL/newline, unchanged path spelling | Passed. |
| Ordering | C locale, controlled Swedish locale, and raw-string tie-break for equal collation keys | Passed without changing process locale. |
| Runtime failures | Every stable category, escaped fallback, continuation, diagnostic order, successful-output retention, per-operand atomicity, exit 1 | Passed. |
| Bad backend results | Malformed `info`, malformed names-only `ls`, unrelated/nested/empty child names, NUL/newline children | Passed as `incompatible result`. |
| Output | Single and multiple grammar, files-only, directories-only, all-failed, redirected/TTY identity, BrokenPipe, generic write failure, accepted-prefix preservation | Passed. |
| Host embedding | Mounted below `data` with `host.add_typer`; raw options, delimiter, and help retained behavior | Passed. |
| Live gate | One read-only OpenCADC staging directory listing through the same handler | Passed. |

The hermetic matrix contains 18 backend/scenario cases. The complete executable
prototype suite contains 88 tests.

## Commands and observed results

Historical disposable demo command:

```console
uv run --locked --with typer==0.27.0 \
  python prototypes/fsspec_cli_plain_ls_demo.py ls memory:/docs
guide.md
notes.txt
```

Historical prototype evidence command:

```console
uv run --locked --with typer==0.27.0 \
  python -m pytest --no-cov -q tests/test_fsspec_cli_plain_ls_prototype.py
88 passed in 0.65s
```

Focused hermetic matrix, host embedding, and non-default locale evidence:

```console
uv run --locked --with typer==0.27.0 \
  python -m pytest --no-cov -q tests/test_fsspec_cli_plain_ls_prototype.py \
  -k 'backend_matrix or embedded or swedish'
23 passed, 53 deselected in 0.42s
```

Focused terminal and output-failure evidence:

```console
uv run --locked --with typer==0.27.0 \
  python -m pytest --no-cov -q tests/test_fsspec_cli_plain_ls_prototype.py \
  -k 'output_failure_keeps or public_seam'
6 passed, 77 deselected in 0.54s
```

Full repository suite with transient Typer, so the prototype module is not
skipped:

```console
uv run --locked --with typer==0.27.0 \
  python -m pytest --no-cov -q
493 passed, 50 skipped, 2 deselected, 2 warnings in 6.03s
```

The repository's normal locked environment intentionally has no Typer
dependency. After prototype disposal, its standard coverage gate passes:

```console
uv run --locked pytest -q
405 passed, 50 skipped, 2 deselected, 2 warnings in 11.47s
Required test coverage of 90.0% reached. Total coverage: 96.98%
```

## Live OpenCADC evidence

Observation time: `2026-07-16T02:50:11Z`

- Endpoint: `https://staging.canfar.net/arc`
- Operand: `open:/home/brars`
- Operation: read-only
- Exit: `0`
- Standard error: empty
- Listed entries: `4`
- Recorded calls: `info("/home/brars")`, then
  `ls("/home/brars", detail=False)`

The same handler used by every hermetic case performed the live operation. No
VOS-specific handler branch was present. Entry names and credential material
are intentionally not published. This is one observation, not a universal
OpenCADC or VOSpace guarantee.

## Terminal and output failures

Redirected stdout and a pseudo-terminal both produced the exact bytes
`b"a.txt\nz.txt\n"`, exited `0`, and wrote no stderr. A closed-reader pipe
exited `1` without a diagnostic or traceback. A different stdout `OSError`
exited `1`, preserved any already accepted prefix, and emitted the exact
escaped output-failure diagnostic. Already-known backend diagnostics were
emitted before an output failure stopped stdout.

## Interoperability boundaries and remaining fog

### Evidence does not support

- Every existing or future fsspec implementation.
- Async execution, event-loop ownership, or async host embedding; the
  synchronous prototype cannot prove those production requirements.
- Non-file/non-directory node types.
- `ls -l`, metadata decoration, recursion, columns, color, or quoting.
- Filesystem construction, authentication, packaging, or shell installation.
- Performance or broad live-service guarantees.

### Locked configuration boundary

CPython and macOS cannot apply host `LC_COLLATE` to strings containing embedded
NUL. The prototype proves diagnostic rendering for one NUL-containing
configured name, where no relative ordering exists, but makes no claim for
ordering that name among two or more configured filesystems. A raw Unicode
fallback was tested and removed because it would invent non-locale order.

Such a mapped name is already unreachable because explicit operands containing
NUL fail preflight. The human verdict therefore locks NUL-containing mapping
names as invalid; production `App` construction must reject them before
command execution.

### Local root caveat

`LocalFileSystem` has no chroot option, so a literal `local:/` would enumerate
the host root and cannot be a hermetic test. The Local matrix uses a temporary
directory as its dataset root. Isolated Memory and hermetic VOS prove the
literal mapped-root behavior.

### Fog transferred to later Wayfinder questions

- Async execution evidence: [issue #90](https://github.com/shinybrar/vosfs/issues/90)
- Async host and lifecycle contract: [issue #92](https://github.com/shinybrar/vosfs/issues/92)
- Source lifecycle failure behavior: [Acquire every referenced async filesystem source before filesystem work](../../adr/0003-acquire-referenced-async-filesystem-sources.md)
- Tested-status vocabulary and version policy: [issue #81](https://github.com/shinybrar/vosfs/issues/81)
- Long-format decision: [why strict POSIX was the wrong bar](../../design/fsspec-cli/lessons.md#12-strict-posix-was-the-wrong-bar)
- Production tracer sequencing: [issue #83](https://github.com/shinybrar/vosfs/issues/83)

## Verdict and prototype disposal

Deleted prototype-only artifacts:

- `prototypes/fsspec_cli_plain_ls.py`
- `prototypes/fsspec_cli_plain_ls_demo.py`
- `prototypes/README.md`
- `tests/test_fsspec_cli_plain_ls_prototype.py`

Locked verdict: **viable semantics for the tested synchronous file/directory
floor, with no universal-fsspec claim; NUL-containing mapping names are
invalid; all production CLI work is async-only.**

All prototype-only code and tests were deleted after the verdict. This report
retains the evidence; no production CLI implementation was added by issue #80.

## Evidence links

- [Locked command contract](../../design/fsspec-cli/commands.md#ls)
- [Portable capability floor](../fsspec-cli-plain-ls-capability-floor.md)
- [Issue #80](https://github.com/shinybrar/vosfs/issues/80)
