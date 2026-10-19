# Reduced BSD/macOS `stat` fixture vectors

<!-- pyml disable line-length -->

Status: **Research golden vectors for Issue #145 / #146**

Profile: [reduced BSD/macOS `stat` command profile](../design/fsspec-cli/commands.md#stat)

These vectors are implementation inputs. They are not a runtime matrix and must
not become production capability negotiation. Expected stdout uses the locked
grammar; owner/group names in Local examples are illustrative and MUST be
resolved from the test host's `pwd`/`grp` for the recorded numeric ids at
runtime.

Notation: `UID` / `GID` mean the decimal strings or resolved names for the
fixture's `uid`/`gid`. `MTIME` means the quoted local C-locale rendering of the
fixture epoch under `%b %e %H:%M:%S %Y`.

## V1 — single Local-rich file success

Input argv: `stat local:/tmp/stat-file`

Locked `_info` shape:

```json
{
  "name": "/tmp/stat-file",
  "size": 3,
  "type": "file",
  "islink": false,
  "mode": 33188,
  "nlink": 1,
  "uid": 502,
  "gid": 20,
  "mtime": 1784325970.7683342
}
```

Expected stdout pattern:

```text
-rw-r--r-- 1 UID GID 3 "MTIME" /tmp/stat-file\n
```

With `stat.filemode(33188) == "-rw-r--r--"`.

## V2 — single Local-rich directory success

Input argv: `stat local:/tmp/stat-dir`

```json
{
  "name": "/tmp/stat-dir",
  "size": 96,
  "type": "directory",
  "islink": false,
  "mode": 16877,
  "nlink": 3,
  "uid": 502,
  "gid": 20,
  "mtime": 1784325970.768656
}
```

Expected stdout pattern:

```text
drwxr-xr-x 3 UID GID 96 "MTIME" /tmp/stat-dir\n
```

## V3 — multiple operands, mixed success then missing

Input argv: `stat local:/tmp/a local:/tmp/missing local:/tmp/b`

- `/tmp/a` and `/tmp/b` return Local-rich file shapes (sizes 1 and 2).
- `/tmp/missing` raises `FileNotFoundError`.

Expected: success line for `a`, diagnostic for missing, success line for `b`,
exit status `1`. No placeholders for the missing operand.

## V4 — symlink incompatible

Input: Local-rich mapping with `"islink": true` and `"destination": "file.txt"`
(even when other keys look complete).

Expected stderr diagnostic ending in `incompatible result`, empty stdout for
that operand, status `1`.

## V5 — Memory incomplete shape

Input: Memory `_info` `{"name":"/file.txt","size":3,"type":"file","created": "..."}`
missing `mode`/`nlink`/`uid`/`gid`/`mtime`.

Expected: `incompatible result` (not a sparse line).

## V6 — vosfs conditional / incomplete

Input examples that MUST NOT render as success under the locked shape:

- DataNode info with only `name`/`type`/`size`/`uri` (no mode/nlink/uid/gid).
- DataNode with string `mtime` but still missing mode/owner keys.
- LinkNode `type="other"`, `islink=true`, `target=...`.

Expected: `incompatible result` for each.

## V7 — malformed types

Examples:

- `"size": null`
- `"size": True` / `"uid": False` / `"mode": True` / `"nlink": True` /
  `"gid": False` (`bool` must fail exact-int checks)
- `"mtime": True` (`bool` rejected even though it subclasses `int`)
- `"mtime": "2026-07-17T00:00:00Z"` (string on a Local-like row)
- `"mode": "33188"`
- `"type": "other"` without being rejected earlier as non-file/dir

Expected: `incompatible result`.

## V8 — locale / time boundary

Same `mtime` epoch rendered with the profile's C-locale formatter under pinned
`TZ=UTC` must match golden bytes on Linux and macOS CI. Ambient non-C
`LC_TIME` or unpinned host `TZ` MUST NOT be used for byte-for-byte claims.
Include at least one vector with newline/backslash/NUL in a backend message to
lock diagnostic escaping (`\\n`, `\\\\`, `\\0`).

## V9 — output failure

After one successful buffered line is accepted by stdout, a subsequent short
write/broken pipe MUST stop further operand output, preserve accepted bytes,
run cleanup, and exit `1`.

## V10 — source-free option rejection

argv examples: `stat -l local:/x`, `stat -f %N local:/x`, `stat -x local:/x`,
`stat --format=%n local:/x`.

Expected each: status `2`, empty stdout, one unsupported-option diagnostic,
zero source factory calls.

## V11 — source-free operand rejection

argv examples: `stat`, `stat /tmp/x`, `stat local:tmp`, `stat unknown:/x`.

Expected: status `2`, empty stdout, locked operand diagnostic, zero factories.
