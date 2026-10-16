# Command reference

Every command takes operands spelled `name:/path`. `name` is a key from the
source mapping you passed to `App`; `/path` is handed to that backend
literally.

`--` ends option parsing. A mapped operand always begins with `name:`, and a
source name may not start with `-`.

## At a glance

| Command | Does |
| --- | --- |
| [`ls`](#ls) | List directory contents; `-l` for long form |
| [`du`](#du) | Recursive byte usage |
| [`find`](#find) | Recursive path list |
| [`tree`](#tree) | Recursive tree drawing |
| [`size`](#size) | Exact byte counts |
| [`test`](#test) | Existence/type predicate as an exit status |
| [`info`](#info) | Full normalized metadata, including backend extras |
| [`stat`](#stat) | Reduced BSD/macOS-shaped status line |
| [`head` / `tail`](#head-tail) | Leading or trailing bytes |
| [`cat`](#cat) | Concatenate files (and stdin) to stdout |
| [`cp`](#cp) | Copy files, or one directory with `-R` |
| [`mv`](#mv) | Move or rename within one filesystem |
| [`mkdir`](#mkdir) | Create directories |
| [`rmdir`](#rmdir) | Remove empty directories |
| [`unlink`](#unlink) | Remove one file |
| [`rm`](#rm) | Remove files; `-d` empty dirs; guarded `-R` |

## Listing

### `ls` {#ls}

```bash
myapp fs ls data:/project
myapp fs ls -A data:/project          # include dot entries
myapp fs ls -l data:/project          # long form
myapp fs ls -lh data:/project         # long form, human-readable sizes
```

A file operand prints itself; a directory prints its sorted children. With
several operands, files come first, then one headed block per directory.

On a terminal, directory names are bold, including VOSpace containers and
object-store buckets. Links and files stay plain. Piped or redirected output is
never styled, and `NO_COLOR=1` or `TERM=dumb` turns the bold off on a terminal
too. Only bold is added; the names themselves never change.

Long listing keeps the shell order: permissions, link count, owner, group,
size, modification time, and name. Missing values show `-`; missing permission
bits show `?`, for example `-?????????` for a file without a mode. Recent dates
show a time; old dates show the year. No allocated-block total is invented.
Numeric local identities resolve to names when available.

For VOSpace, the owner is a creator display identity. The group field preserves
both access lists, for example `r=OSSOS,w=NONE`; this is not a POSIX owning
group. Permission characters summarize VOSpace access and do not imply POSIX
execute or chmod semantics. Use `info` to inspect lock state, all access fields,
and the original properties. Direct links show `name -> target`.

`-h` means human-readable, not help.

### `du`

```bash
myapp fs du data:/project             # per-path usage
myapp fs du -s data:/project          # total only
myapp fs du -sh data:/project         # total, human-readable
```

!!! warning "`du` is recursive and can be expensive"

    `du` lists the whole subtree, one listing per directory, and sums the
    sizes those listings report. Directories at the same depth are listed
    concurrently, but against a remote store that is still one request per
    directory. **`-s` changes the output, not the traversal cost.**

`du`, `find`, and `tree` fail if any directory in the subtree cannot be
listed, rather than treating it as empty.

### `find`

```bash
myapp fs find data:/project
myapp fs find --maxdepth 2 data:/project
myapp fs find --type d data:/project      # directories instead of files
```

No predicates, globbing, or `-exec`. Paths print exactly as the backend
returned them.

### `tree`

```bash
myapp fs tree data:/project
myapp fs tree --maxdepth 2 data:/project
```

One remote listing request per reached directory — bound it on large trees.

## Metadata

### `size`

```bash
myapp fs size data:/a.csv
myapp fs size data:/a.csv data:/b.csv archive:/c.csv
```

Prints `<bytes>\t<operand>`. With several operands it batches per filesystem
into one request each, rather than one request per file. Output keeps the
source name so identical paths on different filesystems stay distinguishable.

### `test`

```bash
if myapp fs test -e data:/project/output.csv; then
    echo "already done"
fi
```

`-e` exists, `-d` is a directory, `-f` is a regular file. Exactly one is
required. No output — the answer is the exit status: **`0` for true, `1` for
false**.

!!! warning "A false predicate exits `1`, the same code as a real failure"

    `test` cannot distinguish "the file is not there" from "the backend
    errored" by status alone. A genuine failure also writes a diagnostic to
    stderr, so check stderr if you need to tell them apart.

### `info`

```bash
myapp fs info data:/a.csv
```

Everything the backend reports: normalized fields plus backend-specific values
under `extra` (checksums, ETags, storage class, VOSpace properties). Sparse
fields stay `None`.

### `stat`

```bash
myapp fs stat data:/a.csv
```

A reduced BSD/macOS-shaped line:

```text
-rw-r--r-- 1 brars staff 1497 "Jul 17 18:00:00 2026" /a.csv
```

Deliberately narrower than `info`: it needs the full local-rich field set
(`type`, a string `name`, `mode`, `nlink`, `uid`, `gid`, `size`, `mtime`), and
rejects symlinks. A backend that does not report all of them reports
`incompatible result`. Use `info` for remote backends.

## Reading bytes

### `head`, `tail` {#head-tail}

```bash
myapp fs head -c 512 data:/big.log
myapp fs tail -c 512 data:/big.log
```

Byte counts only — there is no `-n` line mode. A count larger than the file
prints the whole file; `-c 0` checks the file exists and prints nothing.

!!! note "Bounded request, not necessarily a bounded transfer"

    `-c N` bounds what the CLI *asks for*. Whether the backend transfers only
    those bytes is up to the backend: `vosfs` may keep a validated `206`, or
    fall back to a whole-object body when the endpoint answers `200`.

### `cat`

```bash
myapp fs cat data:/a.txt data:/b.txt > combined.txt
myapp fs cat data:/archive.tar.gz | tar -tz
cat local.txt | myapp fs cat - data:/appendix.txt
```

Binary-safe: no decoding, no separators, no newline insertion. A bare `-` (or
no operand) reads stdin.

Each remote object is staged through one local temporary at a time, so memory
stays bounded no matter how large the file is. Temporaries are always removed —
after success, failure, cancellation, or a broken pipe.

## Copying and moving

### `cp`

```bash
myapp fs cp data:/a.csv data:/backup/a.csv          # one file
myapp fs cp data:/a.csv data:/b.csv data:/backup/   # several into a directory
myapp fs cp local:/a.csv archive:/2026/a.csv        # across filesystems
myapp fs cp -R data:/project archive:/project       # a whole directory
myapp fs cp --metadata-wait 300 local:/a.csv archive:/2026/a.csv
```

Every copy verifies destination metadata after writing. Cross-filesystem copies
stage through one local temporary.

On a terminal, each file being copied draws one byte progress bar on stderr.
A cross-filesystem copy uses the same bar for both halves: it reads
`download <source>` first, then `upload <destination>`. When the file is done
its bar is removed. With `cp -R`, every file in flight has its own bar, so
several move together:

```text
cp: archive:/project/a.bin: uploaded 8388608 bytes
upload archive:/project/b.bin     ━━━━━━━━━━━━━━━━━━━━━━━━━━━━╸━━━━━━━━━━━━━━━━━━━━━━━━━ 0:00:01
upload archive:/project/sub/c.bin ━━━━━━━━━━━━━━━━━━━━━━━━━━━━╸━━━━━━━━━━━━━━━━━━━━━━━━━ 0:00:01
```

There is no bar when stderr is redirected, or when `NO_COLOR` is set. A backend
that does not report progress shows no bar either.

Each file then gets two lines on stderr, printed above any bars still moving
and naming the file that was written — `archive:/2026/a.csv`, not the
directory you typed. Stdout stays empty.

```text
cp: archive:/2026/a.csv: uploaded 52428800 bytes
cp: archive:/2026/a.csv: verified 52428800 bytes
```

The first line says `copied` instead of `uploaded` for a single file copied
within one filesystem; `cp -R` always says `uploaded`. Exit
status `0` means the destination reports a file of the source's size, and every
checksum or ETag both sides report agrees.

Some storage reports a new file's size a little after the write finishes. So
while the destination still shows no size, or the size it had before this copy,
`cp` keeps checking every 5 seconds for up to `--metadata-wait` seconds
(default 60; `0` checks once). If it never catches up:

```text
cp: archive:/2026/a.csv: uploaded 52428800 bytes
cp: archive:/2026/a.csv: not yet verified (size 0, expected 52428800); destination residue may remain
```

That is exit `1`, and it does **not** mean the bytes are bad: the upload
finished, but the destination's metadata had not caught up yet. The file is
left in place. Check it later with `info` or `size`, or rerun with a longer
`--metadata-wait`, which uploads again.

These are reported straight away, without waiting:

| Second line | Meaning |
| --- | --- |
| `size mismatch (expected <n>, observed <m>)` | The destination reports a different size |
| `metadata mismatch (<token>)` | Sizes agree, but `etag`, `md5`, `content-md5`, or `checksum` differs |
| `verification failure (<Class>)` | Reading the destination's metadata failed |
| `not a file after upload` | The destination is not a file (`after copy` within one filesystem) |

Each ends with `; destination residue may remain`: the written file is left
where it is, never deleted. An ETag is compared as an opaque string, so a
multipart upload's `<md5>-<parts>` ETag can disagree with a single-part one for
the same bytes.

`cp -R` requires the `recursion.copy` capability (on by default). It freezes a
manifest of the source tree — bounded at 10,000 entries — before mutating
anything, preserves empty directories, and rejects symlinks and special entries
*before* any write.

Repeating `cp -R` skips content-identical files. Compatible MD5 checksums avoid
both download and upload. Otherwise, same-size files are compared using bounded
local staging and SHA-256; different contents are copied using the staged
source. Equal sizes or timestamps alone do not authorize a skip. If a candidate
cannot be read, the copy fails explicitly.

Without checksums, equality checking reads both files: it saves destination
writes, not necessarily network traffic. Extra destination files are retained.
All skipped entries still participate in final verification.

`cp -R` prints the two lines for each file it writes, as soon as that file is
checked, so lines from parallel files can interleave. It waits for lagging
metadata on each file the same way, under the same `--metadata-wait`. A
skipped identical file prints nothing.

Files are transferred concurrently, up to 16 at a time. After the first
failure no new transfer starts; transfers already running finish, and the
failure reported is the first one in source-tree order.

It is **not** a snapshot, transaction, mirror, or rollback, and does not
preserve POSIX metadata.

### `mv`

```bash
myapp fs mv data:/draft.csv data:/final.csv
myapp fs mv data:/a.csv data:/b.csv data:/archive/
```

Within **one** filesystem only.

!!! failure "Cross-filesystem `mv` is rejected"

    ```text
    mv: cross-source move unsupported
    ```

    A correct move must delete the same version of the file it copied, and no
    tested backend supplies a change token strong enough to prove that. Rather
    than risk deleting a file that changed mid-move, `mv` refuses — before
    touching anything.

    Do it explicitly instead: `cp` then `rm`, so the delete is your decision.

## Namespace

### `mkdir`

```bash
myapp fs mkdir data:/newdir
myapp fs mkdir -p data:/a/b/c        # create parents
```

Silent on success.

### `rmdir`

```bash
myapp fs rmdir data:/emptydir
```

Empty directories only. `-p` is unsupported.

### `unlink`

```bash
myapp fs unlink data:/a.csv
```

Exactly one file. A directory operand is an error.

### `rm`

```bash
myapp fs rm data:/a.csv
myapp fs rm -f data:/maybe-missing.csv    # ignore missing
myapp fs rm -v data:/a.csv                # print what was removed
myapp fs rm -d data:/emptydir             # also remove empty directories
myapp fs rm -R data:/project              # recursive, if enabled
```

`-d` cannot be combined with other options. `-f` cannot be combined with `-v`
unless `-R`/`-r` is also given. `-v` may be supplied only once; repeated `-f`
is idempotent, and `-f` with no operands succeeds silently.

!!! danger "`rm -R` is off unless the host enabled it"

    It requires the `recursion.remove` capability. Removal is **leaves-first
    and non-atomic**: entries at the same depth are removed concurrently, a
    directory only after all its children are confirmed gone. A failure
    partway through leaves earlier removals done and the rest present or
    uncertain. There is no prompt, undo, or trash.

    Root paths and any path containing `.` or `..` are rejected outright.

## When something fails

Failures print one line per operand on stderr (`cp` also prints its
[per-file outcome lines](#cp) there):

```text
ls: data:/missing: not found
cp: data:/src: permission denied
rm: data:/f: uncertain mutation state
```

`uncertain` means the operation was *sent* but its outcome could not be
confirmed — it may have applied. Check the actual state before retrying.

Exit statuses are documented in
[Integration](integration.md#exit-statuses). The short version: `2` means
nothing was touched, `1` means something went wrong, and `141` means a pipe
closed on one of the streaming commands (`cat`, `head`, `tail`, `rm -v`) — the
buffered commands report a broken pipe as `1`.
