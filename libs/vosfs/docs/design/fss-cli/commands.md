# `fss-cli` command contracts

Status: **Normative.** Each section states one command's *delta* from
[`contract.md`](contract.md), which supplies operand parsing, diagnostics,
result validation, buffered output, exit status, and source lifecycle for all
of them.

Executable evidence is the test suite under `src/fss-cli/tests/`; per-row
status is in [`matrix.md`](matrix.md). Where prose and a passing gate disagree,
the gate wins.

## Summary

| Command | Form | Backend hooks awaited |
| --- | --- | --- |
| `ls` | `ls [-A] [--] name:/path...` | `_info`, `_ls(detail=True)` |
| `ls -l` | `ls [-Alh] [--] name:/path...` | `_info`, `_ls(detail=True)` |
| `du` | `du [-sh] [--] name:/path` | `_ls(detail=True)` |
| `find` | `find [--maxdepth N] [--type f\|d] [--] name:/path` | `_ls(detail=True)`; `--type d` also `_info` |
| `tree` | `tree [--maxdepth N] [--] name:/path` | `_ls(detail=True)` |
| `size` | `size [--] name:/path...` | `_size` or `_sizes` |
| `test` | `test -e\|-d\|-f [--] name:/path` | `_exists` / `_isdir` / `_isfile` |
| `info` | `info [--] name:/path` | `_info` |
| `stat` | `stat [--] name:/path...` | `_info` |
| `head` | `head -c N [--] name:/path` | `_cat_file`; `-c 0` uses `_info` |
| `tail` | `tail -c N [--] name:/path` | `_info`, `_cat_file` |
| `cat` | `cat [--] name:/path\|-...` | `_info`, `_get_file` |
| `cp` | `cp [--] SOURCE... DESTINATION` | `_info`, `_cp_file` or `_get_file`+`_put_file` |
| `cp -R` | `cp -R\|-r [--] name:/dir name:/target` | `_info`, `_ls(detail=True)`, transfer hooks |
| `mv` | `mv [--] name:/path... name:/path` | `_info`, `_mv_file` or `_mv` |
| `mkdir` | `mkdir [-p] [--] name:/path...` | `_mkdir` or `_makedirs`, `_info` |
| `rmdir` | `rmdir [--] name:/path...` | `_info`, `_rmdir` |
| `unlink` | `unlink [--] name:/path` | `_info`, `_rm_file` |
| `rm` | `rm [-dfv] [-R\|-r] [--] [name:/path...]` | `_info`, `_rm_file`, `_rmdir` |

## Listing

### `ls`

Names only. One operand: a file writes its exact mapped operand; a directory
writes its sorted immediate child basenames with no header; an empty directory
writes nothing.

Several operands: successful non-directories form the first block, one per
line. Each successful directory then forms a block headed by its exact mapped
operand plus `:`. Blocks are joined by exactly one empty line, with no leading
or trailing blank. An empty directory still writes its header.

```text
$ ls local:/a.txt memory:/docs
local:/a.txt

memory:/docs:
guide.md
```

`-A` includes entries beginning with a dot, excluding `.` and `..`.

The detailed listing is read for each child's `name`, `type`, and `islink`
only; metadata that plain `ls` does not print is not validated. A names-only
list is an incompatible result. With [`contract.md`](contract.md) §6 emphasis
on, directory children and the operand spelling of each directory block header
(not its `:`) are bold. In `ls -l` the name cell is the only styled cell of a
row; a link's `name -> target` stays plain.

### `ls -l` / `ls -lh`

Long listing through the §10 normalization layer with stable shell columns. `-h`
requires a long listing:

```text
ls: -h: requires long listing
```

Grouping matches plain `ls`: files first, then one block per directory. A
malformed detailed list or a normalization failure produces no partial block
for that operand; other operands keep their complete results and the final
status is `1`.

```text
-?????????  -  -  -  12  -  report.txt

memory:/docs:
-?????????  -  -  -  1K  -  guide.md
d?????????  -  -  -   -  -  sub
```

Direct link operands render their own metadata and target without following
it. Recent timestamps use local month/day/time; timestamps older than 180 days
or more than one hour in the future show the year. Neither layout changes the
underlying timestamp or substitutes creation time.

### `du`

Recursive exact-byte usage summed from the shared concurrent walk (see
below): each non-directory entry contributes its listed `size`, which must be
an exact non-negative integer. `-s` renders one record for the operand path as
spelled; without `-s`, the per-entry sizes are rendered as `<size>\t<path>`,
collated. `-h` renders sizes through the shared 1024-base helper and changes no
other field.

`du` costs one listing per directory and no per-file metadata request. `-s`
changes the output, not the traversal cost. A missing operand reports
`not found`.

**Shared walk.** `du`, `find`, `tree`, and the `cp -R` manifest list the tree
breadth-first with `_ls(detail=True)`, issuing the listings of one depth
together under a bound of 16. A listing failure at *any* depth fails the
command; fsspec's inherited `_walk` forwards `on_error` only to the top level
and would report an unreadable subdirectory as empty. Entries must be exact
mappings with string `name` and `type`, lexical children of the listed
directory, unique, and free of `.`/`..` segments. Backends' own `_find` and
`_du` are deliberately not used, trading any backend-specific optimization for
one consistent, fail-closed traversal.

### `find`

Recursive paths, one per line, collated, from the shared walk. `--type f`
(default) selects non-directory entries; `--type d` selects entries whose
`type` is `directory` and adds the operand itself when one `_info` reports it
as a directory. `--maxdepth 0` is implemented as `maxdepth=1` plus a
post-filter to the operand itself.

Each returned spelling is rendered exactly. The command adds no source name and
never invents a root entry a backend did not return. There are no predicates,
globbing, or `-exec`.

### `tree`

One buffered Unicode hierarchy rendered from the shared walk. `--maxdepth N`
bounds recursion. fsspec's own `tree()` returns a synchronous string, so this
command renders from listings instead. The walk performs one listing request
per reached directory.

## Metadata

### `size`

For exactly one operand, awaits one `_size`. For two or more, groups operands
by source in first-appearance order and awaits one `_sizes(paths)` per group,
preserving that group's operand order and duplicates. The CLI MUST NOT call
`_size` per item on the multi-operand path.

`_size` MUST return an exact non-negative `int`. A `_sizes` result MUST be an
exact `list` of the same length as the submitted paths, containing only exact
non-negative ints.

Output retains the source name so equal paths from different filesystems stay
unambiguous:

```text
5\tmemory:/docs/a.txt
7\tlocal:/tmp/b.bin
```

### `test`

Exactly one *distinct* predicate is required:

```text
test: exactly one predicate selector is required
```

Repeating the **same** selector (`test -e -e`) is idempotent and still selects
one predicate; two **different** selectors, or none, is a usage error.

Awaits `_exists`, `_isdir`, or `_isfile` respectively. The result MUST be an
exact `bool`. No stdout; the answer is the exit status.

### `info`

Awaits one `_info` and pretty-prints every normalized field plus
backend-specific values under `extra`. Sparse fields stay `None`; bytes,
datetimes, tuples, and mappings keep their Python representation rather than
being forced through JSON.

Mapping traversal snapshots `len(mapping)`, enumerates through `for key in
mapping`, and retrieves through `mapping[key]` — it does **not** consult an
overridden `items()`. If the enumerated count and final size disagree with the
snapshot, the whole result is incompatible.

### `stat`

The stricter reduced BSD/macOS-shaped, Local-rich view. One line per operand:

```text
<mode> <nlink> <owner> <group> <size> "<mtime>" <pathname>
```

`<mode>` is `stat.filemode(mode)`; `<owner>`/`<group>` resolve through the local
`pwd`/`grp` databases, falling back to the numeric id; `<mtime>` uses fixed
C-locale month abbreviations in `%b %e %H:%M:%S %Y`, always double-quoted.
Fields are separated by single ASCII spaces.

This is **not** host `DEF_FORMAT` or `LS_FORMAT`. It requires the Local-rich
field set (`mode`, `nlink`, `uid`, `gid`, `size`, `mtime`); a backend missing
any of them yields `incompatible result`. `info` is the backend-neutral view;
`stat` is deliberately narrower and unchanged.

## Reading bytes

### `head` / `tail`

`head -c N` awaits one `_cat_file(path, 0, N)`. `tail -c N` awaits `_info` for
the size, then one bounded `_cat_file(path, max(0, size - N))`; the clamp keeps
a count larger than the object from becoming a negative (suffix) offset.
`head -c 0` and `tail -c 0` read no content when `_info` reports a file.

The result MUST be exact `bytes` no longer than `N`. Byte arrays, memory views,
and text are incompatible. Compatible bytes are written unchanged to binary
stdout with no newline, encoding, or text conversion.

A bounded `_cat_file` request is **not** a promise of a ranged physical
transfer. Backends may read a whole object and slice locally — `vosfs` does
when the byte endpoint answers `200` (OpenCADC Cavern serves no HTTP Range).

### `cat`

Concatenates mapped files and stdin to binary stdout. A bare `-` operand, or no
operand at all, reads binary stdin.

Each remote object is staged through exactly one secure local temporary at a
time, so peak CLI memory stays bounded independently of object size. Bytes are
forwarded in bounded chunks with no decoding, headers, separators, or newline
insertion.

Per-operand atomicity covers validation and staging: no bytes from a failed
staging operand reach stdout, though earlier successfully forwarded bytes
remain emitted. Temporaries are always closed and removed — after success,
failure, cancellation, broken pipe, or cleanup failure — and never appear in
diagnostics.

## Copying and moving

### `cp` (files)

Metadata-verified file copy. Same-source copies try `_cp_file`. If that hook
raises `NotImplementedError`, they use the same client transfer as cross-source
copies: stage through one host-local temporary and await `_get_file` then
`_put_file(..., mode="overwrite")`. Multiple sources require an existing
destination directory.

If both configured names resolve to the same filesystem object *and* the same
path, the command rejects `same path` **before** staging or upload.

```text
cp [--metadata-wait SECONDS] SOURCE... DESTINATION
```

`--metadata-wait` (default `60`, minimum `0`) bounds how long `cp` polls
destination `_info` after each successful file write. It applies to every file
`cp -R` uploads too.

#### Progress

One `rich.progress.Progress` is shared by the whole invocation and drawn on
stderr:

```python
rich_progress.Progress(
    rich_progress.TextColumn("[bold blue]{task.description}"),
    rich_progress.BarColumn(bar_width=None),
    rich_progress.TimeRemainingColumn(),
)
```

It exists only when stderr is a terminal (`isatty()`) and `NO_COLOR` is unset
or empty. Otherwise no `Progress` is constructed, every hook receives fsspec's
`DEFAULT_CALLBACK`, and redirected stderr receives no bar bytes. Rich's own
terminal detection is not consulted, because `FORCE_COLOR` makes it treat a
pipe as a terminal. The display is transient: it is erased when `cp` ends.

Each file gets at most one task. A small `fsspec.callbacks.Callback` subclass
turns the backend's `set_size` and `relative_update` calls into the task's
total and completed bytes:

| Route | Hooks that receive the callback | Task description |
| --- | --- | --- |
| Cross-source file | `_get_file`, then `_put_file` | `download <source operand>`, then `upload <resolved destination>` on the same task |
| Same-source file | `_cp_file`, only when it declares a `callback` parameter | `copy <resolved destination>` |
| Same-source file, no `callback` parameter | none | no task |
| `cp -R`, each file | `_get_file`, then `_put_file` | `download <name>:<source file>`, then `upload <name>:<destination file>` |

A task stays hidden until its backend first reports through the callback, so
a backend that never calls it shows no bar, and no byte progress is invented.
Moving to the upload resets the task and hides it again. The task is removed
when the file's transfer ends, before that file's outcome lines. Paths get the
usual diagnostic escaping and then `rich.markup.escape`: the description is
markup, and a `[` in a path is data.

While the display is live, stderr is routed through Rich's line proxy, so
outcome lines and diagnostics print above the bars instead of through them.
Stdout stays empty.

#### Outcome lines

After the write hook returns, `cp` writes two stderr lines per file. The
operand is the resolved file (`<name>:<path>`), not the typed directory, and
`<n>` is the frozen source size:

```text
cp: <name>:<resolved>: uploaded <n> bytes     # cross-source, after _put_file
cp: <name>:<resolved>: copied <n> bytes       # same-source, after _cp_file
```

The second line is the verification outcome:

| Outcome | Status | Second line |
| --- | ---: | --- |
| File, size matches, shared tokens match | `0` | `verified <n> bytes` |
| Still stale when the wait ends (size) | `1` | `not yet verified (size <observed>, expected <n>); destination residue may remain` |
| Still stale when the wait ends (token) | `1` | `not yet verified (<token> unchanged); destination residue may remain` |
| Size is any other value | `1` | `size mismatch (expected <n>, observed <observed>); destination residue may remain` |
| Size matches, a shared token differs | `1` | `metadata mismatch (<token>); destination residue may remain` |
| `_info` raises | `1` | `verification failure (<ClassName>); destination residue may remain` |
| Not a file | `1` | `not a file after upload` (cross-source) or `not a file after copy` (same-source), then `; destination residue may remain` |
| Not a mapping, no string `type`, or an invalid `size` | `1` | `verification failure; destination residue may remain` |

`cp -R` writes the same two lines for every transferred file. The first is
`copied` for a backend copy and `uploaded` for a staged client transfer. A file's lines
print as soon as its own write and wait finish, so the lines of parallel files
interleave while each file's two lines stay in order. A skipped identical file
prints nothing.

Destination metadata may come from validated parent listings. Unsupported or
denied listings fall back to direct `_info` calls; missing or sparse rows are
also queried directly under the shared concurrency limit. Duplicate lexical
paths and malformed listing rows fail validation. Backend paths may include
or omit the leading slash, without changing which parent they belong to.

`<token>` is the normalized name that differed: `etag`, `md5`, `content-md5`,
or `checksum`. A raising write hook prints neither line and keeps
`uncertain mutation state; destination residue may remain` against the typed
destination operand.

`not yet verified` says only that the reported metadata had not caught up when
the wait ended. `size mismatch` and `metadata mismatch` are disagreements in
the reported metadata. No line claims the bytes are missing or corrupt.

#### The metadata verification proof

Expected size and recognized source tokens are frozen into an immutable proof
immediately after source validation, before destination resolution or mutation.
The destination `_info` that resolution already reads is kept too: if the
resolved path was a file, its size and tokens are the *previous* state. After
the transfer, the verifier requires:

1. the staged source size matches the pre-transfer source `_info`;
2. the destination is a file of that exact size; and
3. every **shared** recognized metadata token matches exactly, under the
   normalized names `ETag` / `etag`, `md5`, `content-md5` / `content_md5`, and
   `checksum`. Tokens must be exact `str` or `bytes`.

With no shared recognized token, exact type and size are the truthful proof.
**No cryptographic strength is claimed** — this is an agreement check on
whatever both ends happen to report, not a content hash. An ETag is compared as
an opaque string: a multipart `<md5>-<parts>` ETag is not stripped or treated
as an MD5.

`_info` is read immediately after the write returns, then every 5 seconds until
the proof matches, the result stops being stale, or `--metadata-wait` seconds
have passed. `0` reads once and never sleeps. A result is **stale**, and is
polled, only when:

- the observed size is `0` and the frozen size is not;
- a file existed before the copy and the observed size is still its size; or
- the size matches and every differing shared token still equals the value the
  previous file reported.

A frozen size of `0` with an observed size of `0` is verified. Every other
outcome in the table above is final on the first read that produces it.
Interrupting the wait leaves the destination in place.

The source temporary is the transfer bridge, **not** a verification download.
There is no destination download, FIFO, pipe, worker thread, synchronous open,
or second temporary. Recursive copy has the separately profiled content comparison described below;
non-recursive file copy does not download the destination.

Staging cleanup runs after success, ordinary failure, and escaping control
flow. An ordinary cleanup failure is reported only when no transfer or
verification failure already exists, and never masks escaping control flow.
Staging errors disclose only the error class — never local temporary paths or
source content.

Status `0` proves source retention, destination type and byte count, and
agreement of every shared recognized token, and is returned only after a
`verified` line. A stale `_info` is never success. A failed upload or later
verification reports that **destination residue may remain**. The command never
deletes the destination to simulate rollback and never claims atomicity.

### `cp -R` / `cp -r`

Verified two-operand directory copy, available only when
`capabilities.recursion.copy` is enabled (default on).

Builds a **frozen manifest** of the source tree, bounded at **10,000 entries**,
before any mutation, through the shared walk. Preserves empty directories. Rejects links and special
entries *before* mutating. Verifies the source manifest and destination
metadata before reporting success. Supports same-source and cross-source
routes through one backend-neutral runner over required async hooks.

After validating a walk row and its collection shapes, the advertised child
count is checked against remaining capacity before child metadata is fetched.
An oversized collection reports the entry limit before inspecting individual
children. Enumeration is also bounded by the advertised length, so an
inconsistent mapping cannot cause an unlimited snapshot.

Existing destination files are skipped only after content validation. A shared
explicit MD5 hexadecimal value or base64 Content-MD5 can prove equality, with
matching size. ETags, generic checksum tokens of unknown algorithm, sizes, and
timestamps alone never authorize a skip. MD5 is an accidental-corruption check,
not an authenticity guarantee.

Without compatible content checksums, same-size candidates are downloaded to
bounded disk staging and compared using SHA-256 off the event loop. Different
sizes bypass comparison. A differing candidate reuses its staged source for
upload; it is not downloaded twice. An unreadable candidate fails rather than
being silently skipped or overwritten. Missing files are copied normally.
Destination preflight metadata is reused, avoiding a second pre-transfer info
request per file. Skips retain source revalidation and destination verification.
Without checksums, skipping saves writes but still reads both objects.

This policy is specific to recursive copy; the preceding non-recursive profile
keeps its existing metadata proof. See
[ADR 0009](../../../../../docs/adr/0009-skip-content-identical-recursive-copies.md).

Dot segments and a source root operand are rejected up front:

```text
cp: <operand>: dot segment unsupported
cp: <operand>: source root unsupported
```

Up to 16 files transfer at once. Each in-flight file holds one live task on
the invocation's shared display (see [Progress](#progress)), and the task is
removed when that file finishes, so a large tree shows only the files that are
moving. After each upload, the file's `uploaded` line prints, its destination
`_info` is polled under the same rules and `--metadata-wait` budget as a file
copy, and the file's second line prints: `verified <n> bytes` or the verdict
from [Outcome lines](#outcome-lines). The first file that fails to verify
stops new transfers; files already in flight finish and print their own lines,
and the command exits `1`. After every file verifies, the source is
revalidated and the whole destination tree is checked once more before status
`0`.

```text
cp -R [--metadata-wait SECONDS] SOURCE DESTINATION
```

It does **not** promise a snapshot, transaction, rollback, exact mirror, or
POSIX metadata preservation.

### `mv`

Metadata-verified move on **one** mapped filesystem: single file to a target,
or several files into an existing directory. Requires at least one source and
one destination.

**Cross-source `mv` is rejected**, source-free:

```text
mv: cross-source move unsupported
```

See [`lessons.md` §5](lessons.md#5-cross-source-mv-is-rejected-because-deletion-cannot-be-proven).

## Namespace mutation

### `mkdir`

Awaits `_mkdir(create_parents=False)`, or `_makedirs(exist_ok=True)` with `-p`,
then verifies with `_info` that the path is a directory. Successful
invocations emit **no stdout** — no banner, confirmation, or created-path
listing. A post-mutation verification failure is reported as uncertain state.

### `rmdir`

Removes empty directories only. Verifies the operand is a directory via
`_info`, awaits `_rmdir`, then re-checks: absence proves success, including
after a mutation-call exception. Presence after a mutation exception is
confirmed failure. `ENOTEMPTY` renders as `directory not empty`. No stdout on
success. `-p` is unsupported.

### `unlink`

XSI single-file removal. Verifies the operand is a file, awaits `_rm_file`,
then requires an absence check. A directory operand renders `is a directory`.

### `rm`

Removes files. `-d` also removes empty directories, `-f` ignores missing
operands, `-v` prints each removed operand. Option combinations are validated
before any work:

```text
rm: -v: may be supplied once
rm: -d: cannot combine with other options
rm: -f: cannot combine with -v
rm: missing mapped filesystem operand
```

Repeated and grouped `-f` flags are idempotent (`rm -f -f` is `rm -f`), and
`-f` with zero operands succeeds without entering a source or writing output.
`-v` may be supplied only once. Typer accepts a registered option before or
after operands.

`rm -R` / `-r` requires `capabilities.recursion.remove` (default **off**). It
builds a bounded complete manifest through `_info` and `_ls(detail=True)`,
rejects roots, dot segments, links, special entries, and containment failures,
then removes leaves-first through `_rm_file` and `_rmdir`. Success requires an
absence check after every primitive plus a final root-absence proof.

Recursive removal is **leaves-first and non-atomic**. Entries of one depth are
removed concurrently (bound 16), deepest depth first, so a directory is
removed only after every child is confirmed absent; after the first failure,
in manifest order, nothing new starts. Failure or cancellation can leave
earlier confirmed removals in place and the rest present or uncertain.
There is no prompt, rollback, retry, trash, or recovery. A root operand and any
dot segment are rejected as `rejected path`.

## Deliberately out of scope

No portable fsspec surface exists for these, so they are not provided:

`df` (no free-space API) · `chmod` / `chown` (no portable mode or owner
*write*) · `truncate` (no partial truncate) · `file` (content sniffing) ·
`wc -l` / `-w` (needs streaming and counting) · symlink *creation* ·
`rmdir -p` · cross-source `mv`.

The package ships no console entry point and no module executable: it is a
library that hands the host a `typer.Typer` to mount.
