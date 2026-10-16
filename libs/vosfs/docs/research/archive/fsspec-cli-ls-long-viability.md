# Honest long-listing viability across Local, Memory, and `vosfs`

<!-- pyml disable line-length -->

Researched: 2026-07-15

Question: [shinybrar/vosfs#78](https://github.com/shinybrar/vosfs/issues/78)

Client contract: fsspec 2026.6.0

Status: **Decision evidence retained.** Issue #82 selected a strict rejection
of `ls -l`; that stance was later reversed — see
[why strict POSIX was the wrong bar](../design/fsspec-cli/lessons.md#12-strict-posix-was-the-wrong-bar)
and the [long-listing contract](../design/fsspec-cli/commands.md#ls--l--ls--lh).
This document remains the metadata evidence behind the original decision.

## Answer

No initial backend can implement a complete POSIX Issue 8 `ls -l` from its fsspec-visible metadata without inventing facts.

- Every directory listing produced by `-l` needs an allocated-block `total` status line. None of Local, Memory, or `vosfs` exposes allocated space and its unit through detailed fsspec metadata. Logical byte size is not an honest substitute. ([POSIX `ls` STDOUT](https://pubs.opengroup.org/onlinepubs/9799919799/utilities/ls.html#tag_20_73_10), [Local result mapping](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/local.py#L78-L127), [Memory result mapping](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/memory.py#L43-L105), [`vosfs` result mapping](https://github.com/shinybrar/vosfs/blob/8bc1df60eb4df69eb439480ab3685f275180effc/src/vosfs/nodes.py#L173-L206))
- Local has the raw base-stat values needed for ordinary regular-file and directory rows: mode, link count, UID, GID, size, modification time, and name. It does not expose allocated blocks, device identity, or whether an alternate access-control method exists. Its symbolic-link details also do not match POSIX no-`-L` semantics. ([Local source](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/local.py#L78-L127), [POSIX mode and link forms](https://pubs.opengroup.org/onlinepubs/9799919799/utilities/ls.html#tag_20_73_10))
- Memory has honest name, file/directory type, and logical size. A file has a public `modified()` value, but a directory does not. It has no mode, link count, owner, group, allocated-block, device, or symbolic-link metadata. ([Memory listing and info](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/memory.py#L43-L105), [Memory `info`](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/memory.py#L149-L169), [Memory `modified`](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/memory.py#L241-L253))
- `vosfs` has honest names and node kinds. Data size and modification time are usable only when the corresponding raw VOSpace properties exist; mode, link count, owning group, allocated blocks, and POSIX permission bits are absent. A LinkNode carries its target, but its exposed size is zero rather than the target-string byte length POSIX assigns to a symbolic link. ([`vosfs` parser and mapping](https://github.com/shinybrar/vosfs/blob/8bc1df60eb4df69eb439480ab3685f275180effc/src/vosfs/nodes.py#L284-L317), [VOSpace 2.1 node semantics](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html#tth_sEc3.1), [POSIX `<sys/stat.h>`](https://pubs.opengroup.org/onlinepubs/9799919799/basedefs/sys_stat.h.html))

The evidence therefore supports reduced, explicitly scoped capability slices—most notably a Local base-stat row for non-link regular files and directories—but not a complete POSIX `-l` profile. Issue #82 rejected exposing those slices under `-l`; V1 keeps the option unsupported and performs no source or backend work.

## POSIX Issue 8 floor

For a non-device entry, plain `-l` requires this ordered row:

```text
file-mode  links  owner  group  size  date-and-time  pathname
```

For character and block devices, implementation-defined device information replaces size. Separators and padding can vary, but the seven fields and their order are normative. Owner and group are names when resolvable and numeric UID/GID otherwise. The timestamp is the last data-modification time by default. ([POSIX `ls` long format](https://pubs.opengroup.org/onlinepubs/9799919799/utilities/ls.html#tag_20_73_10))

The file-mode field contains:

1. one entry-type character;
2. three owner permission characters;
3. three group permission characters;
4. three other permission characters; and
5. an empty suffix when the file is known to have no alternate/additional access-control method, or one implementation-chosen printable nonblank marker when it has one.

The standard type characters cover regular files, directories, symbolic links, FIFOs, sockets, and character/block devices. Permission positions include read, write, execute/search, set-ID, and the XSI restricted-deletion forms. Unknown ACL state is not evidence that the optional suffix is empty. ([POSIX mode field](https://pubs.opengroup.org/onlinepubs/9799919799/utilities/ls.html#tag_20_73_10))

For every list of files *within a directory*, `-l` also requires a preceding `total N` status line. `N` describes occupied filesystem space for all displayed entries in 512-byte units, or 1024-byte units with `-k`, rounded up when needed. It is neither entry count nor logical-size sum. POSIX does not prescribe `st_blocks` as the implementation mechanism; `<sys/stat.h>` makes `st_blocks` an XSI field and does not standardize its unit. The backend must instead provide authoritative allocation and unit information, or an equivalent filesystem-specific calculation. A standalone non-directory operand, or a directory treated as an entry with `-d`, does not need this status line. ([POSIX directory status line](https://pubs.opengroup.org/onlinepubs/9799919799/utilities/ls.html#tag_20_73_10), [POSIX `<sys/stat.h>`](https://pubs.opengroup.org/onlinepubs/9799919799/basedefs/sys_stat.h.html))

Without `-L`, a symbolic-link row describes the link itself: its size is the byte length of the stored link pathname, and the final field is `link-name -> stored-target`. With `-L`, target metadata replaces link metadata while the displayed pathname remains the link name. ([POSIX `ls` link form](https://pubs.opengroup.org/onlinepubs/9799919799/utilities/ls.html#tag_20_73_10), [POSIX `st_size`](https://pubs.opengroup.org/onlinepubs/9799919799/basedefs/sys_stat.h.html))

The per-file block prefix belongs to `-s`; it is not part of plain `-l`. The directory `total` line is nevertheless part of plain `-l`. Device-info representation, the alternate-access marker character, non-POSIX-locale timestamp shape, padding, and mode extension characters are implementation choices rather than missing required facts. ([POSIX `ls` options and output](https://pubs.opengroup.org/onlinepubs/9799919799/utilities/ls.html#tag_20_73_04))

## Raw facts versus presentation

Formatting cannot repair missing metadata.

| POSIX output | Required raw fact | Presentation that does not fabricate |
| --- | --- | --- |
| Entry type and nine base permission characters | File type plus complete mode bits | Python `stat.filemode()` can encode a supplied mode as the ten-character base form. ([Python `stat.filemode`](https://docs.python.org/3.13/library/stat.html#stat.filemode)) |
| Alternate-access marker | Authoritative knowledge of whether another access-control method applies | Choose the implementation marker only after presence is known; absence cannot be inferred from missing metadata. ([POSIX mode field](https://pubs.opengroup.org/onlinepubs/9799919799/utilities/ls.html#tag_20_73_10)) |
| Owner and group | Authoritative identity or numeric UID/GID | Resolve Local numeric IDs through the Unix account/group databases; POSIX requires numeric fallback when resolution fails. Python's `pwd` and `grp` modules are Unix-only, so host lookup is presentation for Local, not a generic remote resolver. ([Python `pwd.getpwuid`](https://docs.python.org/3.13/library/pwd.html#pwd.getpwuid), [Python `grp.getgrgid`](https://docs.python.org/3.13/library/grp.html#grp.getgrgid), [POSIX numeric fallback](https://pubs.opengroup.org/onlinepubs/9799919799/utilities/ls.html#tag_20_73_10)) |
| Date and time | Selected timestamp with known temporal meaning | Convert to the selected timezone and locale, then choose the recent or old/future POSIX form. A creation timestamp is not a modification timestamp. ([POSIX timestamp format](https://pubs.opengroup.org/onlinepubs/9799919799/utilities/ls.html#tag_20_73_10)) |
| Directory `total` | Allocated space plus its unit for every displayed entry | Unit conversion and rounding are presentation; summing logical byte sizes is fabrication. ([POSIX directory status line](https://pubs.opengroup.org/onlinepubs/9799919799/utilities/ls.html#tag_20_73_10)) |
| Pathname | Operand spelling or contained-entry name according to command context | Selection and display can transform an authoritative returned name; it cannot recover a name that was not returned. ([POSIX pathname rules](https://pubs.opengroup.org/onlinepubs/9799919799/utilities/ls.html#tag_20_73_10)) |

## Backend evidence matrix

“Conditional” means the backend has an honest value for only some entry kinds or only when an optional raw property is present. It is not a value the renderer may fill with a placeholder.

| Required fact | Local | Memory | `vosfs` |
| --- | --- | --- | --- |
| Name | Yes | Yes | Yes |
| Entry type | Yes for ordinary files/directories and native special types via `mode`; link paths have inconsistent follow behavior | Yes for files/directories; no link model | Yes for DataNode/ContainerNode; LinkNode is `type="other"` plus `islink=True` |
| Base permission bits | Yes, `mode` | No | No |
| Alternate-access state | No | No | No |
| Hard-link count | Yes, `nlink` | No | No |
| Owner | Yes, numeric `uid`; name resolution is presentation | No | Conditional OpenCADC owner-display value under the VOSpace `creator` property; not a guaranteed POSIX owner field |
| Owning group | Yes, numeric `gid`; name resolution is presentation | No | No; `groupread`/`groupwrite` are ACL lists, not an owning group |
| Logical size / POSIX `st_size` | Yes for regular files and directories; wrong for no-`-L` links | Yes for files; zero for directories | Conditional for DataNodes with raw integer `length`; zero for containers and LinkNodes; missing DataNode length also becomes zero |
| Modification time | Yes; link semantics depend on call shape | Files only through separate `modified()`; directories have none | Conditional `mtime` for non-links when `core#date` exists; LinkNode mapping omits it |
| Allocated space and unit | No | No | No |
| Character/block device identity | No `rdev` in result | Not modeled | Not modeled |
| Link target | Yes, `destination`, but the remaining link metadata is not POSIX-correct | Not modeled | Yes, VOSpace target URI |

The fsspec abstract contract promises only `name`, `size` (or `None` when unknown), and `type`; all other keys are backend-specific. It therefore cannot itself establish long-format compatibility. ([fsspec `ls`](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/spec.py#L326-L365), [fsspec `info`](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/spec.py#L682-L714))

### Local

For a normal path, `LocalFileSystem.info()` gets an OS stat result and exposes `mode`, `uid`, `gid`, `mtime`, `ino`, and `nlink` alongside name, size, type, creation time, and link state. It does not copy `st_blocks`, `st_rdev`, or an ACL/alternate-access indicator into the result. ([Local `info`](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/local.py#L78-L127))

Local symbolic links are not an honest no-`-L` row source:

- Direct `info(link-string)` first detects the link and then replaces the stat result with the target stat. Mode, UID, GID, mtime, link count, type, and size therefore describe the target while `islink` remains true. ([Local direct-path branch](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/local.py#L98-L127))
- `ls(directory, detail=True)` passes an `os.DirEntry`; mode, UID, GID, mtime, and link count remain from the link's non-following stat, but size is explicitly replaced with target size. A broken target gets size zero. ([Local directory-entry branch](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/local.py#L78-L97))

Neither shape supplies the link's own complete POSIX row. An implementation could bypass fsspec and call `os.lstat`, but that would be a Local-specific native adapter, not generic fsspec interoperability.

### Memory

Memory listing and info dictionaries contain name, size, and type. File rows additionally contain creation time; directory rows do not. The class tracks both created and modified datetimes on its in-memory file object, but only the separate public `modified(path)` method returns modification time, and it raises `FileNotFoundError` for a directory. No source path supplies mode, UID, GID, link count, allocated blocks, or link target. ([Memory `ls`](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/memory.py#L43-L105), [Memory `info`](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/memory.py#L149-L169), [Memory `modified`](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/memory.py#L241-L253), [Memory file timestamps](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/memory.py#L286-L311))

Creation time cannot be relabeled as modification time. Fetching `modified()` per file is honest but still leaves directory rows and the other required columns unavailable.

### `vosfs`

`vosfs` maps DataNode to file, ContainerNode to directory, and LinkNode to fsspec `other`. It always returns name, type, size, and node URI. Non-links may add `mtime` and preserved properties; links return early with `islink` and target, omitting even properties or an available date. ([node-kind map and `to_info`](https://github.com/shinybrar/vosfs/blob/8bc1df60eb4df69eb439480ab3685f275180effc/src/vosfs/nodes.py#L47-L62), [`to_info`](https://github.com/shinybrar/vosfs/blob/8bc1df60eb4df69eb439480ab3685f275180effc/src/vosfs/nodes.py#L173-L206))

Data size is honest when the raw `ivo://ivoa.net/vospace/core#length` property is present and parses as an integer. When it is absent, the current parser returns zero rather than fsspec's unknown-size `None`; a consumer can distinguish honest zero from fallback zero only by checking whether the preserved property exists. Containers and links are assigned zero. VOSpace 2.1 states that ContainerNode and LinkNode have no data bytes, but POSIX assigns a symbolic link's `st_size` to the byte length of its stored target pathname, so LinkNode zero is not POSIX link size. ([`vosfs` size parser](https://github.com/shinybrar/vosfs/blob/8bc1df60eb4df69eb439480ab3685f275180effc/src/vosfs/nodes.py#L390-L409), [VOSpace 2.1 node types](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html#tth_sEc3.1), [POSIX `<sys/stat.h>`](https://pubs.opengroup.org/onlinepubs/9799919799/basedefs/sys_stat.h.html))

Modification time is likewise conditional. The parser promotes `core#date` to `mtime` only when present. The general VOSpace standard describes `core#date` broadly and separately defines `core#mtime`; current OpenCADC Cavern supplies `core#date` from the backing filesystem's last-modified timestamp, so the value is honest for that implementation but not guaranteed by fsspec or VOSpace generally. ([`vosfs` property promotion](https://github.com/shinybrar/vosfs/blob/8bc1df60eb4df69eb439480ab3685f275180effc/src/vosfs/nodes.py#L38-L42), [OpenCADC filesystem mapping](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/nodes/NodeUtil.java#L536-L557), [VOSpace standard properties](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html#tth_sEcC))

OpenCADC can serialize an owner display string under `core#creator`, and `vosfs` preserves it for non-link nodes. That is conditional, service-specific owner evidence—not a stable fsspec UID or VOSpace owner field. `groupread`, `groupwrite`, and `publicread` describe access policy; they do not identify one POSIX owning group or encode owner/group/other `rwx` mode bits. ([OpenCADC node serialization](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos/src/main/java/org/opencadc/vospace/io/NodeWriter.java#L325-L372), [VOSpace standard-property meanings](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html#tth_sEcC), [`vosfs` property preservation](https://github.com/shinybrar/vosfs/blob/8bc1df60eb4df69eb439480ab3685f275180effc/src/vosfs/nodes.py#L197-L206))

## Viability evidence applied by issue #82

These are evidence slices, not selected profiles:

| Candidate scope | Evidence |
| --- | --- |
| Complete POSIX directory `ls -l` | **Not viable for Local, Memory, or `vosfs`** because no backend exposes authoritative allocated space and unit for `total`. Local also lacks ACL-state knowledge; Memory and `vosfs` lack several row fields. |
| Complete POSIX row for a standalone regular-file operand | **Not unconditionally viable.** Local supplies the seven visible base values but cannot establish the alternate-access suffix. Memory and `vosfs` lack mode, link count, and owning identities. |
| Local base-stat row for non-link regular files/directories | **Viable as a reduced slice:** base ten-character mode, link count, numeric or resolved owner/group, size, mtime, and name. It excludes the ACL suffix, directory `total`, device rows, and symlinks. |
| Sparse fsspec details | Name, type, and size are the common abstract floor. Memory file mtime and OpenCADC-backed `vosfs` length/date can enrich individual rows conditionally. This is detailed metadata, not the POSIX long-format column contract. |

Issue #82 selected strict whole-invocation rejection during option preflight.
Reduced Local rows, sparse placeholders, variable columns, and silent fallback
are not `ls -l` profiles. The locked profile records exact diagnostics, exit
status, and the evidence required to admit `-l` in a future version.

## Hermetic probes

Probes ran on macOS 15.7.7 arm64 with Python 3.13.5 and the repository-locked fsspec 2026.6.0. The lock identifies the release artifact, and GitHub tag `2026.6.0` resolves to source commit `a2457004d03e0312f715f90f58873de5ab195a37`. ([repository lock](https://github.com/shinybrar/vosfs/blob/8bc1df60eb4df69eb439480ab3685f275180effc/uv.lock#L464-L468), [fsspec tag](https://github.com/fsspec/filesystem_spec/releases/tag/2026.6.0))

Local and Memory used disposable in-process filesystems. A Local symlink stored `very_long_target_name` (21 bytes) and pointed to a three-byte regular file:

| Observation | Result |
| --- | --- |
| Native `lstat` | link size 21; allocated blocks available natively |
| `LocalFileSystem.info(link)` | `islink=True`, type file, target mode, target mtime, target link count, size 3; no blocks key |
| `LocalFileSystem.ls(parent, detail=True)` | `islink=True`, type other, link mode/mtime/link count, target size 3; no blocks key |
| `MemoryFileSystem.info(file)` | name, size 3, type file, created datetime |
| `MemoryFileSystem.info(directory)` | name, size 0, type directory |
| `MemoryFileSystem.modified(file)` | aware UTC datetime |
| `MemoryFileSystem.modified(directory)` | `FileNotFoundError` |

The `vosfs` metadata mapping and filesystem paths were exercised through the repository's strict RESpx routes; unmatched requests cannot reach a service. The focused result was `12 passed` for data/container/link mapping plus info/listing/modified behavior. It confirmed that a minimal data node omits `mtime` and reports size zero, a minimal container reports size zero without `mtime`, and a LinkNode reports `type="other"`, size zero, `islink`, and target while omitting `mtime` and properties. ([mapping tests](https://github.com/shinybrar/vosfs/blob/8bc1df60eb4df69eb439480ab3685f275180effc/tests/test_nodes.py#L276-L337), [filesystem tests](https://github.com/shinybrar/vosfs/blob/8bc1df60eb4df69eb439480ab3685f275180effc/tests/test_nodeops.py#L41-L139))

## Primary sources

- The Open Group Base Specifications Issue 8 (POSIX.1-2024): [`ls`](https://pubs.opengroup.org/onlinepubs/9799919799/utilities/ls.html), [`<sys/stat.h>`](https://pubs.opengroup.org/onlinepubs/9799919799/basedefs/sys_stat.h.html), and [file-format notation](https://pubs.opengroup.org/onlinepubs/9799919799/basedefs/V1_chap05.html)
- fsspec 2026.6.0 source at [`a2457004d03e0312f715f90f58873de5ab195a37`](https://github.com/fsspec/filesystem_spec/tree/a2457004d03e0312f715f90f58873de5ab195a37): [`AbstractFileSystem`](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/spec.py), [`LocalFileSystem`](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/local.py), and [`MemoryFileSystem`](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/implementations/memory.py)
- Python 3.13 standard library: [`stat`](https://docs.python.org/3.13/library/stat.html), [`pwd`](https://docs.python.org/3.13/library/pwd.html), and [`grp`](https://docs.python.org/3.13/library/grp.html)
- IVOA Recommendation: [VOSpace 2.1](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html)
- OpenCADC `vos` source at [`cf976ce8141dd3341631b7f3e07aa38443d42f58`](https://github.com/opencadc/vos/tree/cf976ce8141dd3341631b7f3e07aa38443d42f58): [`NodeUtil`](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/nodes/NodeUtil.java) and [`NodeWriter`](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos/src/main/java/org/opencadc/vospace/io/NodeWriter.java)
- `vosfs` source and tests at [`8bc1df60eb4df69eb439480ab3685f275180effc`](https://github.com/shinybrar/vosfs/tree/8bc1df60eb4df69eb439480ab3685f275180effc): [`nodes.py`](https://github.com/shinybrar/vosfs/blob/8bc1df60eb4df69eb439480ab3685f275180effc/src/vosfs/nodes.py), [`filesystem.py`](https://github.com/shinybrar/vosfs/blob/8bc1df60eb4df69eb439480ab3685f275180effc/src/vosfs/filesystem.py), and focused metadata tests
