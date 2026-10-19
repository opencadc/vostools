# BSD/macOS `stat` viability over fsspec `_info`

<!-- pyml disable line-length -->

Researched: 2026-07-17

Question: [Define a reduced BSD and macOS stat compatibility profile](https://github.com/shinybrar/vosfs/issues/145)

Parent: [Issue #120](https://github.com/shinybrar/vosfs/issues/120)

Client contract: fsspec 2026.6.0 at
[`a2457004d03e0312f715f90f58873de5ab195a37`](https://github.com/fsspec/filesystem_spec/tree/a2457004d03e0312f715f90f58873de5ab195a37)

Status: **Decision evidence retained.** Issue #145 selected the
[reduced BSD/macOS `stat` command profile](../design/fsspec-cli/commands.md#stat).
This document remains the field-shape and reference evidence. It is not
production capability negotiation and must not be cited as `ls -l` evidence.

## Answer

POSIX Issue 8 defines no `stat` utility. macOS/BSD `stat(1)` is a FreeBSD-derived
extension. Its **default** no-option line requires device, inode, mode, nlink,
owner, group, rdev, size, atime, mtime, ctime, birthtime, blksize, blocks, and
flags. The pinned fsspec `_info` / `info` contract promises only `name`, `size`
(or `None`), and `type`; every other key is backend-specific.

Adapted Local exposes a Local-rich row for ordinary non-link files and
directories (`mode`, `nlink`, `uid`, `gid`, `size`, `mtime`, `ino`, `name`,
`type`, `created`, `islink`). It does **not** expose `st_dev`, `st_rdev`,
`st_atime`, `st_ctime`, `st_blocks`, `st_blksize`, or `st_flags` through
fsspec. Direct-path symlink `info` follows the target and mixes link identity
with target metadata, so it is not an honest no-`-L` link row.

Adapted Memory exposes `name`/`size`/`type` and file `created` only. Directories
lack `created`. There is no mode, nlink, uid, gid, or mtime key on `_info`.

Native `vosfs` maps DataNode/ContainerNode to file/directory with conditional
string `mtime` and integer `size`. LinkNode is `type="other"` plus `islink` and
`target`. Mode, nlink, uid, and gid are absent.

Therefore the macOS/BSD **default** format, `-l`/`-F` (`ls -lT` shape), `-x`,
`-r`, `-s`, and open `-f` format language are not truthful over a common
source-independent `_info` shape. A reduced fixed grammar that consumes only
Local-rich authoritative keys is viable as an explicitly non-POSIX,
non-default BSD/macOS-shaped profile. Memory and `vosfs` lack that complete
shape today and remain `unverified` for a positive row (and must fail
validation when exercised under the locked profile).

## Exact research tuple

Research ran on macOS 15.7.7 (build 24G720) arm64 with CPython 3.13.5,
`fsspec-cli` 0.1.1, fsspec 2026.6.0, Typer 0.27.0, and vosfs 0.4.0. Immutable
source citations below use fsspec commit `a2457004…` and repository paths at
[`e2ff33fe882129a6df2ff51d08a05139a626ffb5`](https://github.com/shinybrar/vosfs/commit/e2ff33fe882129a6df2ff51d08a05139a626ffb5)
(includes #136). The resolved set is locked in that commit's
[`uv.lock`](https://github.com/shinybrar/vosfs/blob/e2ff33fe882129a6df2ff51d08a05139a626ffb5/uv.lock).

### Host `stat(1)` reference (observed)

| Fact | Value |
| --- | --- |
| Path | `/usr/bin/stat` |
| SHA-256 | `57c7e2742cee418bad76bba59ebce7679755a02cffe94f58e4993912d698bebc` |
| Synopsis (man) | `stat [-FLnq] [-f format \| -l \| -r \| -s \| -x] [-t timefmt] [file ...]` |
| Default format (man + binary strings) | `%d %i %Sp %l %Su %Sg %r %z "%Sa" "%Sm" "%Sc" "%SB" %k %b %#Xf %N` |
| `-l` format | `%Sp %l %Su %Sg %Z %Sm %N%SY` |
| Default time format | `%b %e %T %Y` |

GNU coreutils `stat` was not present on the research host (`stat --version`
fails with `illegal option`). Linux-host CI remains a supported **runtime**
platform for `fsspec-cli`; it is not the output-format reference for this
profile.

### Published Apple / FreeBSD source (immutable citations)

Observed `/usr/bin/stat` on macOS 15.7.7 embeds the same default format string
as published Apple `file_cmds`. Mapping that host build to one Apple OSS tag is
**unverified**; the citations below document the FreeBSD-derived format macros,
not a claim that build 24G720 was compiled from that exact tag.

| Source | Immutable identity |
| --- | --- |
| Apple `file_cmds` tag `file_cmds-479` | Tag object `d434d2b20bf3fb4617cb2947ac29739a8f86106b` peels to commit [`659a8a301e2acf0343f8b8673a154a2ca4d07084`](https://github.com/apple-oss-distributions/file_cmds/tree/659a8a301e2acf0343f8b8673a154a2ca4d07084); `stat/stat.c` blob [`8aa79dcdf71001cba72366880e1180e1a6740e60`](https://github.com/apple-oss-distributions/file_cmds/blob/659a8a301e2acf0343f8b8673a154a2ca4d07084/stat/stat.c) |
| FreeBSD `usr.bin/stat/stat.c` at `release/14.3.0` tip for that path | Commit [`1d386b48a555f61cb7325543adbbb5c3f3407a66`](https://github.com/freebsd/freebsd-src/commit/1d386b48a555f61cb7325543adbbb5c3f3407a66) (LS_FORMAT / TIME_FORMAT match Apple) |

Apple `DEF_FORMAT` expands (with birthtime + flags) to the same default string
observed in the host binary. `LS_FORMAT` is `%Sp %l %Su %Sg %Z %Sm %N%SY`.
`TIME_FORMAT` is `%b %e %T %Y`.

## fsspec `_info` contract

[`AbstractFileSystem.info`](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/spec.py)
documents keys `name`, `size`, and `type`, with other keys filesystem-specific.
[`AsyncFileSystem._info`](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/asyn.py)
defaults to `NotImplementedError`. Adapted Local/Memory obtain awaitable `_info`
through
[`AsyncFileSystemWrapper`](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/asyn_wrapper.py)
wrapping synchronous `info`. Native `vosfs` implements `_info` directly.

## Field provenance (candidate → truth)

| Candidate BSD field | Semantic | Local adapted `_info` | Memory adapted `_info` | Native `vosfs` `_info` | Fabrication risk |
| --- | --- | --- | --- | --- | --- |
| `%N` name | Pathname argument / entry name | `name: str` | `name: str` | `name: str` | Display must not invent names |
| `%z` size | Byte length | `size: int` | `size: int` | `size: int` (containers/links often 0; missing DataNode length currently becomes 0) | Must not treat logical size as blocks |
| `%Sp` mode string | File type + permission bits | `mode: int` → `stat.filemode` | absent | absent | No default mode |
| `%l` nlink | Hard-link count | `nlink: int` | absent | absent | No zero placeholder |
| `%Su` / `%Sg` | Owner / group names | `uid`/`gid: int`; names via `pwd`/`grp` are presentation | absent | absent (`creator` is not POSIX uid) | No host-user inference for remote stores |
| `%Sm` mtime | Last modification time | `mtime: float` (epoch seconds) | absent on `_info` (`created` is not mtime; `modified()` is separate and directories raise) | optional `mtime: str` ISO-like when `core#date` present | Must not relabel `created` as mtime |
| `%d` device | `st_dev` | absent | absent | absent | Cannot emit default format |
| `%i` inode | `st_ino` | `ino: int` present but unused by reduced profile | absent | absent | Optional richness, not common floor |
| `%r` rdev | Device special files | absent | absent | absent | No device rows |
| `%Sa`/`%Sc`/`%SB` | atime / ctime / birth | absent (`created` is birth-or-ctime fallback, not distinct fields) | file `created` datetime only | absent | Cannot emit default times |
| `%k`/`%b`/`%f` | blksize / blocks / flags | absent | absent | absent | Same as `ls -l` `total` blocker |
| `%Y` link target | Symlink text | `destination` when `islink`, but other fields follow target on direct `_info` | not modeled | `target` on LinkNode with `type="other"` | Link rows excluded from reduced profile |
| Entry type | file / directory / other | `type` + `islink` | `type` file/dir only | file/dir/other | Links and `other` incompatible |

Authoritative Local mapping:
[`LocalFileSystem.info`](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/local.py#L78-L127).
Memory:
[`MemoryFileSystem.info`](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/memory.py#L149-L169).
`vosfs`:
[`to_info`](https://github.com/shinybrar/vosfs/blob/e2ff33fe882129a6df2ff51d08a05139a626ffb5/src/vosfs/nodes.py#L173-L206)
and
[`VOSpaceFileSystem._info`](https://github.com/shinybrar/vosfs/blob/e2ff33fe882129a6df2ff51d08a05139a626ffb5/src/vosfs/filesystem.py#L245-L249).

## Hermetic probes (macOS research host)

Probes used disposable Local paths and in-process Memory through
`AsyncFileSystemWrapper(..., asynchronous=True)` awaiting `_info`. Observed
shapes (types abbreviated):

| Operand | Keys present |
| --- | --- |
| Local file | `name:str size:int type:file created:float islink:bool mode:int uid:int gid:int mtime:float ino:int nlink:int` |
| Local directory | same keys; `type:directory` |
| Local symlink (direct `_info`) | Local-rich keys with `islink:True`, `destination:str`, but mode/mtime/size/ino describe the **target** |
| Memory file | `name size type created:datetime` |
| Memory directory | `name size type` only |

Native `vosfs` shapes were not re-probed live; mapping evidence is the pinned
`to_info` source above plus retained
[long-listing viability](fsspec-cli-ls-long-viability.md) hermetic RESpx results.
Linux Local `_info` key presence was **not** re-measured in this research pass;
the Local mapping is pinned to fsspec source, while numeric uid/gid and
`pwd`/`grp` presentation remain host-dependent.

## Relation to `ls -l`

Issue #82 rejected POSIX `ls -l` because allocated blocks, ACL state, and
complete common-row facts are unavailable. This `stat` research reuses that
metadata evidence but selects a **separate**, deliberately non-POSIX command
surface. Nothing here admitted `-l` on `ls`, which was rejected at the time;
`ls -l` is now supported best-effort — see
[why strict POSIX was the wrong bar](../design/fsspec-cli/lessons.md#12-strict-posix-was-the-wrong-bar).

## Primary sources

- Observed macOS 15.7.7 `/usr/bin/stat` man page and binary (SHA-256 above)
- Apple `file_cmds` commit `659a8a301e2acf0343f8b8673a154a2ca4d07084`
- FreeBSD path commit `1d386b48a555f61cb7325543adbbb5c3f3407a66`
- fsspec 2026.6.0 commit `a2457004d03e0312f715f90f58873de5ab195a37`
- Repository commit `e2ff33fe882129a6df2ff51d08a05139a626ffb5` (`vosfs` nodes + filesystem)
- [POSIX Issue 8 utilities index](https://pubs.opengroup.org/onlinepubs/9799919799.2024edition/idx/utilities.html) (no `stat` utility)
