# fsspec backend tribal knowledge for `vosfs`

<!-- pyml disable line-length -->

Researched: 2026-07-15 (America/Vancouver)

Primary corpus: repositories, source, documentation, Issues, Discussions, pull
requests, changelogs, and tests owned by the
[`fsspec` GitHub organization](https://github.com/fsspec)

Status: **Informative evidence.** The approved
[`vosfs` v0.3.0 capability contract](../design/trd.md) remains normative and
controls if this note differs.

## Executive findings

Successful fsspec backends are not thin protocol adapters. They are semantic
translators with five hard jobs:

1. make `ls`, `info`, paths, errors, and recursive operations agree exactly;
2. keep sync, async, process, thread, instance-cache, and resource lifecycles
   separate;
3. bound concurrency and memory while preserving order, callbacks, partial
   completion, and cancellation;
4. treat caching, retries, writes, moves, and transactions as correctness
   protocols rather than performance toggles; and
5. prove behavior through fsspec's abstract tests plus backend-specific failure
   and live-service gates.

Most damaging bugs recur at boundaries, not basic GET/PUT calls: a filtered
listing cached as complete; an `exists()` call hiding authorization failure; a
fork inheriting a dead IO loop; a wrapper closing the backing file too soon; a
text wrapper committing after an exception; an unbounded bulk call exhausting
connections; a retry replaying an uncertain mutation; or a pseudo-directory
causing recursive delete to escape its requested subtree.

For current `vosfs`, highest-value changes are:

- fix [text-mode staged-write abort semantics](https://github.com/shinybrar/vosfs/issues/66);
- make distinct-object `_cat_ranges` reads bounded-concurrent while retaining
  one whole GET per object;
- add spawn/fork, explicit-close, cancellation, and interpreter-shutdown gates;
- add complete cache-poisoning and recursive-delete containment matrices;
- keep `open_async`, transactions, append, remote ranges, and automatic retries
  unsupported until their real protocols exist; and
- test minimum and newest supported fsspec, not one pinned release only.

## Method and limits

### Organization census

GitHub GraphQL and REST APIs were queried on 2026-07-15. The live organization
contained **22 repositories**, **0 archived repositories**, and **0 forks**.
Their issue census was **2,914 issues**: **873 open** and **2,041 closed**.
Discussions totaled **82**. Backend plus core repositories held **57** of those
Discussions; `kerchunk` held 24 and `community` held one.

The API inventory classified 12 direct storage backends:

`adlfs`, `alluxiofs`, `dropboxdrivefs`, `gcsfs`, `gdrive-fsspec`, `ipfsspec`,
`opendalfs`, `ossfs`, `s3fs`, `sshfs`, `swiftspec`, and `tosfs`.

The user examples `gcfs` and `tofs` correspond to organization repositories
[`gcsfs`](https://github.com/fsspec/gcsfs) and
[`tosfs`](https://github.com/fsspec/tosfs). No current organization repository
is named `gcfs` or `tofs`. There is also no separate `httpfs` or `referencefs`
repository: `HTTPFileSystem` and `ReferenceFileSystem` are bundled core
implementations in
[`filesystem_spec`](https://github.com/fsspec/filesystem_spec/tree/1253ee2d2eb291d8e1274b80881a2659c6afbe76/fsspec/implementations).

### Reading strategy

It is neither reproducible nor honest to claim every comment in 2,914 issues
was manually read. Coverage used four layers:

1. census every repository and issue/discussion count;
2. inventory all 82 Discussion metadata records, titles, categories, and opening
   body extracts, then read relevant full bodies, answers, and comment threads;
3. inspect up to eight most-commented issues for every direct backend, producing
   88 issue metadata records before duplicates and quiet-repository shortfalls;
4. inspect top-commented results for six cross-org searches: async/event-loop
   (88 matches), retry/timeout (72), multipart upload (26), listing/performance
   (26), serialization/pickle (7), and path/protocol (356), then read the
   decisive issue bodies, maintainer comments, linked fixes, tests, and source.

The promoted evidence set contains **65 unique fsspec Issue threads** and **15
unique fsspec Discussion threads** cited after body/comment/source review, plus
one current `vosfs` Issue. This is **65 of 2,914 Issues** and **15 of 82
Discussions** at in-depth cited level; all 82 Discussion metadata/title/opening
body records were inventoried before selection. Search results are a purposive
sample, biased toward recurring and high-impact failures. Quiet repositories are
represented explicitly instead of being erased by popularity sorting.

### Evidence rules

- Every behavioral conclusion cites a first-party fsspec source, test, Issue,
  Discussion, or pull request.
- Current source claims use immutable commit
  [`1253ee2`](https://github.com/fsspec/filesystem_spec/tree/1253ee2d2eb291d8e1274b80881a2659c6afbe76).
- “High” confidence means current source/tests or repeated backend evidence.
  “Medium” means one backend incident or unresolved design discussion.
- Backend-specific quirks are not generalized unless another backend or core
  contract supports the same lesson.

## Repository coverage

| Repository | Role | Issues / Discussions | Evidence and coverage result |
| --- | --- | ---: | --- |
| [`adlfs`](https://github.com/fsspec/adlfs) | Azure Blob and ADLS Gen2 backend | 255 / 3 | Async existence misclassification, recursive-delete/version skew, credential forwarding, and bulk memory pressure reviewed ([#265](https://github.com/fsspec/adlfs/issues/265), [#389](https://github.com/fsspec/adlfs/issues/389), [#57](https://github.com/fsspec/adlfs/issues/57), [Discussion #417](https://github.com/fsspec/adlfs/discussions/417)). |
| [`alluxiofs`](https://github.com/fsspec/alluxiofs) | Alluxio distributed-cache backend | 0 / 0 | No Issue or Discussion evidence exists in repository. Source/README and PR-only history were inventoried. Treat maturity and behavior as source-test claims only, not community incident evidence. |
| [`community`](https://github.com/fsspec/community) | Cross-project discussion | 8 / 1 | Discussion surface inventoried; not a backend. |
| [`cookiecutter-fsspec`](https://github.com/fsspec/cookiecutter-fsspec) | Backend template | 1 / 0 | Template and issue surface inventoried; useful bootstrap, not proof of compliance. |
| [`dropboxdrivefs`](https://github.com/fsspec/dropboxdrivefs) | Dropbox backend | 15 / 0 | `ls` accidentally recursive and loop lifecycle failure reviewed ([#12](https://github.com/fsspec/dropboxdrivefs/issues/12), [#6](https://github.com/fsspec/dropboxdrivefs/issues/6)). |
| [`filesystem_spec`](https://github.com/fsspec/filesystem_spec) | Core contract and bundled implementations | 836 / 38 | Current source, developer/async/features docs, changelog, abstract tests, all Discussion bodies, and focused Issue threads reviewed. |
| [`fsspec-proxy`](https://github.com/fsspec/fsspec-proxy) | HTTP proxy/client transporting bytes through configured fsspec filesystems | 1 / 0 | Adjacent deployment adapter, not one storage protocol backend. Included because it exercises serialization, credentials, and remote filesystem ownership across a service boundary. |
| [`gcsfs`](https://github.com/fsspec/gcsfs) | Google Cloud Storage backend | 363 / 2 | Multi-process hangs, long-running write retries, recursive-delete escape, and async/caching design reviewed ([#379](https://github.com/fsspec/gcsfs/issues/379), [#316](https://github.com/fsspec/gcsfs/issues/316), [#346](https://github.com/fsspec/gcsfs/issues/346), [Discussion #602](https://github.com/fsspec/gcsfs/discussions/602)). |
| [`gdrive-fsspec`](https://github.com/fsspec/gdrive-fsspec) | Google Drive backend | 33 / 2 | Resumable upload boundary, backing-file lifetime, authentication, and live-test gaps reviewed ([#33](https://github.com/fsspec/gdrive-fsspec/issues/33), [#17](https://github.com/fsspec/gdrive-fsspec/issues/17), [Discussion #66](https://github.com/fsspec/gdrive-fsspec/discussions/66)). |
| [`GSoC-kechunk-2022`](https://github.com/fsspec/GSoC-kechunk-2022) | Historical GSoC work | 10 / 0 | Inventoried; not a backend or current contract owner. |
| [`ipfsspec`](https://github.com/fsspec/ipfsspec) | Read-only IPFS backend | 13 / 0 | Missing metadata, path/gateway variation, hangs, and async slowdown reviewed ([#17](https://github.com/fsspec/ipfsspec/issues/17), [#12](https://github.com/fsspec/ipfsspec/issues/12), [#39](https://github.com/fsspec/ipfsspec/issues/39)). |
| [`jupyter-fsspec`](https://github.com/fsspec/jupyter-fsspec) | Jupyter integration | 69 / 0 | Consumer/integration surface inventoried; not a storage backend. |
| [`jupyter-projspec`](https://github.com/fsspec/jupyter-projspec) | Jupyter project integration | 0 / 0 | No backend evidence. |
| [`kerchunk`](https://github.com/fsspec/kerchunk) | Reference-data generator and core `ReferenceFileSystem` consumer | 277 / 24 | All Discussion bodies inventoried; relevant async, range, chained-options, and scientific-stack incidents included as downstream evidence. |
| [`opendalfs`](https://github.com/fsspec/opendalfs) | OpenDAL bridge to many storage services | 9 / 0 | Release tracker, typed/package work, and missing efficient offset reads reviewed ([#6](https://github.com/fsspec/opendalfs/issues/6), [#28](https://github.com/fsspec/opendalfs/issues/28)). Bridge breadth does not replace per-service fsspec conformance. |
| [`ossfs`](https://github.com/fsspec/ossfs) | Alibaba OSS backend | 61 / 0 | Buffered-upload offset corruption, authentication, emulator/live divergence, and fsspec version adoption reviewed ([#60](https://github.com/fsspec/ossfs/issues/60), [#75](https://github.com/fsspec/ossfs/issues/75), [#28](https://github.com/fsspec/ossfs/issues/28)). |
| [`projspec`](https://github.com/fsspec/projspec) | Project-description tooling | 25 / 0 | Inventoried; not a backend. |
| [`s3fs`](https://github.com/fsspec/s3fs) | Amazon S3 and compatible object stores | 531 / 12 | Largest backend incident corpus: retries, batching, cache identity/invalidation, credentials, multipart completion, large move, and packaging reviewed. |
| [`sshfs`](https://github.com/fsspec/sshfs) | asyncssh SSH/SFTP backend | 26 / 0 | GC shutdown deadlock, corruption report, permission translation, dependency API drift, and instance caching reviewed ([#63](https://github.com/fsspec/sshfs/issues/63), [#22](https://github.com/fsspec/sshfs/issues/22), [#21](https://github.com/fsspec/sshfs/issues/21), [#54](https://github.com/fsspec/sshfs/issues/54)). |
| [`swiftspec`](https://github.com/fsspec/swiftspec) | OpenStack Swift backend | 9 / 0 | `_strip_protocol`, root/account URL, large-object, temporary-URL, and bulk-delete gaps reviewed ([#14](https://github.com/fsspec/swiftspec/issues/14), [#6](https://github.com/fsspec/swiftspec/issues/6), [#2](https://github.com/fsspec/swiftspec/issues/2), [#12](https://github.com/fsspec/swiftspec/issues/12)). |
| [`tosfs`](https://github.com/fsspec/tosfs) | Tinder Object Storage backend | 180 / 0 | Low-comment, PR-driven repository. Retry hardening and fsspec abstract-test fixes reviewed ([#299](https://github.com/fsspec/tosfs/issues/299), [#122](https://github.com/fsspec/tosfs/issues/122), [#57](https://github.com/fsspec/tosfs/issues/57)). |
| [`universal_pathlib`](https://github.com/fsspec/universal_pathlib) | Path-like consumer over fsspec | 192 / 0 | Consumer pressure on path identity, instance reuse, caching, and signatures reviewed; not a backend. |

GitHub currently marks none of these repositories archived. Historical URLs in
older issues often use previous owners such as `dask/*`, `intake/*`, or
`iterative/*`; GitHub redirects them to current canonical repositories. This
study uses current canonical `fsspec/*` URLs and does not infer abandonment from
an old owner in a preserved comment.

## Cross-backend durable lessons

### 1. Metadata and namespace semantics are one contract

**High confidence.** `ls(detail=True)` and `info()` must return a protocol-free,
full `name`, `type`, and byte `size` (or `None`) with mutually consistent
results. Derived `exists`, `isfile`, `isdir`, `walk`, `find`, `glob`, and `du`
amplify any mismatch ([core source](https://github.com/fsspec/filesystem_spec/blob/1253ee2d2eb291d8e1274b80881a2659c6afbe76/fsspec/spec.py#L326-L365), [Discussion #2000](https://github.com/fsspec/filesystem_spec/discussions/2000)).

Real failures show the pattern:

- Dropbox `ls()` returned recursive descendants instead of immediate children,
  violating cross-backend expectations ([dropboxdrivefs #12](https://github.com/fsspec/dropboxdrivefs/issues/12)).
- ADLFS returned `False` for an existing file because an async metadata path was
  not awaited; directory checks also risked listing thousands of children when
  a cheaper existence primitive was available ([adlfs #265](https://github.com/fsspec/adlfs/issues/265)).
- IPFS gateway metadata lacked a previously assumed `ETag`, so file-versus-dir
  classification failed intermittently ([ipfsspec #17](https://github.com/fsspec/ipfsspec/issues/17)).
- HTTP `isfile()` historically meant “reachable” rather than a trustworthy
  type test, making a parquet reference directory look like a JSON file
  ([filesystem_spec #1919](https://github.com/fsspec/filesystem_spec/issues/1919)).

**For `vosfs`:** keep `_info` as canonical node translation and `_ls` as one
immediate ContainerNode listing. Test exact equality between an item returned by
`ls(detail=True)` and `info(item["name"])`, including DataNode, ContainerNode,
LinkNode, empty root, Unicode, and unknown properties. Never implement
`exists()` as a broad catch around transport errors; authorization, timeout,
parse, and integrity failures are not absence. Core's base `exists()` catches
every exception, a known source of misleading false results
([core source](https://github.com/fsspec/filesystem_spec/blob/1253ee2d2eb291d8e1274b80881a2659c6afbe76/fsspec/spec.py#L668-L675), [filesystem_spec #873](https://github.com/fsspec/filesystem_spec/issues/873)).

### 2. Pseudo-directories make destructive operations dangerous

**High confidence.** Object stores synthesize directories from prefixes and may
also contain zero-byte marker keys ending in `/`. A recursive deletion bug in
gcsfs derived a parent from such a marker and attempted deletion above the
requested subtree, sometimes up to the bucket
([gcsfs #346](https://github.com/fsspec/gcsfs/issues/346)). ADLFS `mkdir()` once
created an empty file, showing how file/directory projection leaks into mutation
semantics ([adlfs #137](https://github.com/fsspec/adlfs/issues/137)). Recursive
remove also broke when adlfs and fsspec versions drifted
([adlfs #389](https://github.com/fsspec/adlfs/issues/389)).

**For `vosfs`:** VOSpace has real ContainerNodes, so do not import object-store
marker behavior. Preserve leaves-first recursive removal. Before every DELETE,
assert normalized candidate is equal to or below requested root. Test trailing
slashes, empty child names, a LinkNode beside a ContainerNode, shared prefixes
(`/a` versus `/ab`), root refusal, partial failures, and concurrent disappearance.
Keep `maxdepth` unsupported where deleting the root would exceed requested
depth.

### 3. Paths are backend-specific, but normalization must be internally exact

**High confidence.** `url_to_fs` intentionally returns different forms because
some backends retain protocol while most strip it; chained URLs and embedded
credentials become constructor options ([Discussion #1356](https://github.com/fsspec/filesystem_spec/discussions/1356)). Core `_strip_protocol` also handles
tuple protocols, `protocol::` chains, trailing separators, and root markers
([core source](https://github.com/fsspec/filesystem_spec/blob/1253ee2d2eb291d8e1274b80881a2659c6afbe76/fsspec/spec.py#L196-L220)).

Backends fail when private-looking hooks are treated as optional. Zarr v3
rejected swiftspec because `_strip_protocol()` retained `swift://`
([swiftspec #14](https://github.com/fsspec/swiftspec/issues/14)). IPFS public
gateways failed when listing used a non-CID path
([ipfsspec #39](https://github.com/fsspec/ipfsspec/issues/39)).

**For `vosfs`:** retain current published path identity: `vos://a/b`,
`vos:///a/b`, `/a/b`, and `a/b` all mean `/a/b`; service selection remains an
`endpoint_url` option, not URL authority. Add round trips through
`_strip_protocol`, `unstrip_protocol`, `url_to_fs`, `OpenFile`, `simplecache`,
Zarr `FsspecStore`, and fsspec JSON. Reject ambiguous `::`, encoded separators,
root escape, query, fragment, userinfo, and double-decoding before network I/O.

### 4. Sync and async are separate usage modes, not interchangeable methods

**High confidence.** Async backends implement underscored coroutines and core
mirrors selected hooks into blocking methods. Internal async code must `await`
coroutines rather than call generated blocking wrappers
([async docs](https://github.com/fsspec/filesystem_spec/blob/1253ee2d2eb291d8e1274b80881a2659c6afbe76/docs/source/async.rst), [mirror source](https://github.com/fsspec/filesystem_spec/blob/1253ee2d2eb291d8e1274b80881a2659c6afbe76/fsspec/asyn.py#L302-L351)). Calling `sync()` from its running event loop cannot work because asyncio loops are non-reentrant
([Discussion #1570](https://github.com/fsspec/filesystem_spec/discussions/1570)).

`async_impl` describes backend capability; `asynchronous` distinguishes the
instance's intended context and instance-cache identity. Backend behavior and
documentation drifted enough that gcsfs users could accidentally mix modes
([gcsfs Discussion #602](https://github.com/fsspec/gcsfs/discussions/602)).

`open_async` is not a general async random-access file protocol. Current core
calls it experimental, wrapper filesystems break it, and open issues show a
streaming file reporting seek positions while reading wrong bytes
([core source](https://github.com/fsspec/filesystem_spec/blob/1253ee2d2eb291d8e1274b80881a2659c6afbe76/fsspec/asyn.py#L978-L981), [filesystem_spec #1772](https://github.com/fsspec/filesystem_spec/issues/1772), [#1414](https://github.com/fsspec/filesystem_spec/issues/1414), [#1709](https://github.com/fsspec/filesystem_spec/issues/1709)).

**For `vosfs`:** keep `open_async` explicitly unsupported. Async consumers use
stateless coroutine hooks; synchronous file consumers use disk-staged `open()`.
Test every supported hook on `asynchronous=True`, every generated facade on
`asynchronous=False`, and the exact error when blocking calls run on the same
loop. Never call public sync methods from coroutine implementations.

### 5. Event loops, sessions, and live clients do not survive `fork()`

**High confidence.** Constructing an async-backed filesystem can create a loop
in a dedicated thread. That thread and its locks do not survive process fork.
An otherwise unused parent `HTTPFileSystem` was sufficient to hang
`ProcessPoolExecutor`; `reset_lock()` helped only when called inside the child
before touching fsspec ([filesystem_spec #1298](https://github.com/fsspec/filesystem_spec/issues/1298)). GCS users saw the same deadlock across PyTorch and preloaded web workers
([gcsfs #379](https://github.com/fsspec/gcsfs/issues/379)). Current fsspec records
the creating PID and rejects inherited async-instance use
([core source](https://github.com/fsspec/filesystem_spec/blob/1253ee2d2eb291d8e1274b80881a2659c6afbe76/fsspec/asyn.py#L343-L357)).

**For `vosfs`:** serialized filesystems must rebuild clients, locks, loops,
bindings, and caches in the receiving process. Add `spawn` success, `fork`
fail-fast, pickled-before-use, pickled-after-use, and parent-created/child-used
tests. Documentation should say: construct/reconstruct inside spawned workers;
do not pass a live filesystem or file object through fork.

### 6. Explicit resource ownership beats garbage collection

**High confidence.** Interpreter shutdown can stop fsspec's daemon IO thread
before filesystem finalizers run. sshfs then blocked in `_finalize()`/`close()`
while waiting on a dead loop ([sshfs #63](https://github.com/fsspec/sshfs/issues/63)). Core issues question remote close in `__del__` and require liveness checks to avoid deadlock ([filesystem_spec #1685](https://github.com/fsspec/filesystem_spec/issues/1685), [#1723](https://github.com/fsspec/filesystem_spec/issues/1723)). Explicit session close also avoids unclosed transport warnings
([Discussion #1405](https://github.com/fsspec/filesystem_spec/discussions/1405)).

File ownership must extend through lazy consumers. A Google Drive file closed
by a context before xarray finished reading produced `I/O operation on closed
file` ([gdrive-fsspec #17](https://github.com/fsspec/gdrive-fsspec/issues/17)).
`open_files()` provides grouped context ownership, but a lazy dataset still
needs an owner that remains alive through computation
([Discussion #1961](https://github.com/fsspec/filesystem_spec/discussions/1961)).

**For `vosfs`:** retain explicit idempotent `aclose()` and blocking `close()`;
never depend on finalizers for network close or write commit. Add tests for
close-before-first-I/O, concurrent client creation versus close, double close,
I/O after close, close on async instance, cancellation during streamed GET/PUT,
and interpreter shutdown with warnings-as-errors. File wrappers own only their
temporary files; filesystem owns HTTP clients.

### 7. Concurrency must be bounded at every fan-out

**High confidence.** Bulk operations need bounded batches. An s3fs recursive
download of roughly 120,000 objects launched an excessive number of tasks,
causing disconnections and open-file/event-loop pressure. The resolution
separated local-file limits from remote-request limits and made batch size
configurable ([s3fs #537](https://github.com/fsspec/s3fs/issues/537)). Core now
provides `_run_coros_in_chunks` and a `batch_size` contract
([async source](https://github.com/fsspec/filesystem_spec/blob/1253ee2d2eb291d8e1274b80881a2659c6afbe76/fsspec/asyn.py#L220-L247)). ADLFS also saw 2–3x memory growth when async `put` parallelism increased
([adlfs #57](https://github.com/fsspec/adlfs/issues/57)). Async did not
automatically improve IPFS workload speed; throttling made it slower
([ipfsspec #12](https://github.com/fsspec/ipfsspec/issues/12)).

**For `vosfs`:** use bounded concurrency for bulk metadata, copy relay,
recursive removal, and one-whole-GET-per-distinct-object `_cat_ranges`. Preserve
input order and per-object deduplication. `batch_size=1` must be fully serial;
larger values must cap active negotiations, HTTP responses, temp files, and
memory together. Do not optimize request count while leaving response bodies or
local file descriptors unbounded. Propagate cancellation and stop scheduling
new work after the first fatal error.

Current `_cat_ranges` correctly deduplicates paths but reads each distinct
object serially and ignores `batch_size`. Bounded concurrency across distinct
objects is a safe performance opportunity because each object still receives
one whole download; multiple ranges within one object still share bytes.

### 8. Listings and globbing need cost models

**High confidence.** List APIs usually accumulate complete results. That blocks
progress and can require millions of entries; a proposed iterator API remains
open because partial iteration interacts awkwardly with directory caching
([filesystem_spec #632](https://github.com/fsspec/filesystem_spec/issues/632)).
Generic glob often calls `find` then filters. This is efficient for object stores
with one prefix listing, but costly on hierarchical servers where each directory
requires a call ([filesystem_spec #1355](https://github.com/fsspec/filesystem_spec/issues/1355)).

**For `vosfs`:** one OpenCADC ContainerNode listing is unpaged and hierarchical.
Derived recursive `find`, `glob`, `walk`, `du`, and `tree` are potentially
expensive and must not claim server-side search. Honor `maxdepth` before descent,
allow top-down pruning, report callbacks where fsspec supports them, and never
cache a partial traversal as a complete directory. Future server pagination or
search needs a new capability contract, iterator/callback semantics, and cache
completeness marker.

### 9. Directory cache, instance cache, and byte cache are different systems

**High confidence.** Conflating caches causes security and correctness faults:

- An infinite listing cache is stale when external writers mutate storage;
  callers need `use_listings_cache=False`, TTL, bounds, and forced refresh
  ([features docs](https://github.com/fsspec/filesystem_spec/blob/1253ee2d2eb291d8e1274b80881a2659c6afbe76/docs/source/features.rst), [Discussion #1872](https://github.com/fsspec/filesystem_spec/discussions/1872)).
- A prefix-filtered or paginated subset must never become the authoritative
  complete listing. Core recently tracked glob prefix optimization poisoning
  dircache ([filesystem_spec #2054](https://github.com/fsspec/filesystem_spec/issues/2054)); s3fs had a related invalidation/find sequence replace a parent listing with one child
  ([s3fs #492](https://github.com/fsspec/s3fs/issues/492)).
- fsspec returns the same filesystem instance for identical constructor tokens.
  Environment credential changes do not change that token, so tests accidentally
  reused an authenticated S3 session until instance cache was cleared or skipped
  ([s3fs #461](https://github.com/fsspec/s3fs/issues/461)).
- Generic cached filesystems are not all thread/process safe; only
  `simplecache` makes that explicit guarantee
  ([features docs](https://github.com/fsspec/filesystem_spec/blob/1253ee2d2eb291d8e1274b80881a2659c6afbe76/docs/source/features.rst), [filesystem_spec #1107](https://github.com/fsspec/filesystem_spec/issues/1107)).
- `filecache` write behavior has had missing-target failures where
  `simplecache` worked; wrappers do not share equivalent write contracts
  ([filesystem_spec #1534](https://github.com/fsspec/filesystem_spec/issues/1534)).
- A wrapper can bypass a whole-file cache when downstream code calls
  `cat_file`/`cat_ranges` instead of `open`, generating many unexpected remote
  calls. Optimize and test every public access path used by consumers
  ([Discussion #1869](https://github.com/fsspec/filesystem_spec/discussions/1869#discussioncomment-13562205)).
- Safe shared cache population needs explicit in-progress versus ready state,
  a temporary file on the destination filesystem, and atomic publication.
  Alluxio's cache implements this pattern and falls back to remote reads when
  cache fill fails
  ([atomic publish](https://github.com/fsspec/alluxiofs/blob/7b2ebb42000f0e7f8e57c80b8f3c1940a01a2d71/alluxiofs/client/cache.py#L221-L303), [fallback](https://github.com/fsspec/alluxiofs/blob/7b2ebb42000f0e7f8e57c80b8f3c1940a01a2d71/alluxiofs/client/cache.py#L642-L768)).

**For `vosfs`:** retain distinct immutable service-binding cache, mutable
directory cache, fsspec instance cache, and optional outer whole-file cache.
Every successful mutation invalidates target, descendants when relevant, and
all affected parents. Failed/unattempted paths remain cached only if still true.
Never cache negotiated byte endpoints. Environment-backed credentials must be
resolved per request as promised; reconstruction must not reuse live auth state.
Support `simplecache::vos://` as whole-file read/write and `filecache::vos://`
only for the explicitly tested read seam. Keep block/range caches unsupported.

### 10. Range support is a capability, not a header guess

**High confidence.** Random access requires reliable size plus exact byte-range
fetch. Servers may omit or lie about `Accept-Ranges`; others return 416 for valid
ranges. Core maintainers recommend trusting an explicit “none” but acknowledge
that headers are not universally truthful
([Discussion #1629](https://github.com/fsspec/filesystem_spec/discussions/1629), [filesystem_spec #1626](https://github.com/fsspec/filesystem_spec/issues/1626)). A remote object changing between size lookup and read can silently truncate or combine versions unless the client validates size/version
([filesystem_spec #1541](https://github.com/fsspec/filesystem_spec/issues/1541)).

Cache choice must match access pattern: readahead for sequential reads, block
cache for random reads, whole-file cache for non-range servers
([cache source](https://github.com/fsspec/filesystem_spec/blob/1253ee2d2eb291d8e1274b80881a2659c6afbe76/fsspec/caching.py)). Async streaming and async random-access files are separate, unresolved interfaces
([filesystem_spec #1772](https://github.com/fsspec/filesystem_spec/issues/1772)).

**For `vosfs`:** current whole-object staged read is correct for Cavern. Never
send `Range`; accept `block_size`, `cache_type`, and `cache_options` only for call
compatibility; disclose one full transfer. Keep `cat_file` Python slice behavior,
including negative, empty, clipped, and EOF ranges. `_cat_ranges` may combine
ranges per object locally but must not imply transport coalescing. If OpenCADC
later adds ranges, require immutable object/version evidence, exact 206/416
tests, changed-object detection, and a new published capability.

### 11. Buffered writes are state machines

**High confidence.** A correct writer initiates once, uploads each full block
once, finalizes once, and commits only after clean close. It must retain original
errors, abort or clean partial server state where possible, and never retry an
uncertain finalization blindly. Core `AbstractBufferedFile` models
`_initiate_upload`, `_upload_chunk(final=...)`, `commit`, and `discard`
([core source](https://github.com/fsspec/filesystem_spec/blob/1253ee2d2eb291d8e1274b80881a2659c6afbe76/fsspec/spec.py#L2027-L2106)).

Backend incidents expose boundary failures:

- Google Drive resumable upload failed above one 8 MiB block because response
  header case/range handling and final state were wrong
  ([gdrive-fsspec #33](https://github.com/fsspec/gdrive-fsspec/issues/33)).
- OSS used the wrong buffered-file position and received
  `PositionNotEqualToLength` after compression crossed a block boundary
  ([ossfs #60](https://github.com/fsspec/ossfs/issues/60)).
- S3/GCS multipart uploads expose the new object only after completion, but an
  uncertain response means completion may have succeeded, and orphaned parts
  can remain billable until expiry or cleanup
  ([s3fs Discussion #883](https://github.com/fsspec/s3fs/discussions/883)).
- `vosfs` text mode currently wraps `StagedWriteFile` in `TextIOWrapper`; wrapper
  close commits even when the outer `with` block raised
  ([vosfs #66](https://github.com/shinybrar/vosfs/issues/66)).

**For `vosfs`:** fix text abort before expanding write features. Prefer fsspec's
buffered-file commit/discard model or another explicit owner that sees outer
context success; do not infer clean close from garbage collection. Test binary
and text success, block exception, encode/flush exception, upload exception,
double close, discard, temp cleanup, cancellation, zero bytes, exact
`Content-Length`, and integrity mismatch. Keep append, update, multipart,
resumable upload, `autocommit=False`, and transactions unsupported until server
semantics exist.

### 12. Transactions and exclusive create are weaker than their names

**High confidence.** fsspec transactions defer writes and then call
`commit()`/`discard()`; they are only semi-atomic. Reads do not necessarily see
uncommitted targets, wrappers may bypass transaction context, compression has
broken commit/discard propagation, and thread safety remains unresolved
([features docs](https://github.com/fsspec/filesystem_spec/blob/1253ee2d2eb291d8e1274b80881a2659c6afbe76/docs/source/features.rst), [filesystem_spec #1584](https://github.com/fsspec/filesystem_spec/issues/1584), [#1823](https://github.com/fsspec/filesystem_spec/issues/1823), [#180](https://github.com/fsspec/filesystem_spec/issues/180)). Base exclusive-create fallback checks existence then writes, so it races without a native conditional primitive
([core source](https://github.com/fsspec/filesystem_spec/blob/1253ee2d2eb291d8e1274b80881a2659c6afbe76/fsspec/spec.py#L1300-L1323)).

**For `vosfs`:** preserve explicit “non-atomic” wording for `mode="create"`,
exclusive open, empty-container deletion, move, and copy. Do not advertise a
transaction simply because writes stage locally; there is no server commit set
or rollback. Race tests should prove no silent broadening into overwrite or
recursive delete.

### 13. Retry policy must follow operation phase and idempotency

**High confidence for principle; medium for exact policy.** Long cloud jobs do
encounter transient disconnects, 400/401/504 sequences, timeouts, and broken
pipes. gcsfs expanded retryable transport exceptions and exponential backoff,
allowing multi-terabyte writes to complete
([gcsfs #316](https://github.com/fsspec/gcsfs/issues/316)). s3fs found a second
failure plane: request-start retries did not cover disconnects while consuming
the response stream, so downloads needed resume/restart logic as well as bounded
batching ([s3fs #537](https://github.com/fsspec/s3fs/issues/537)). TOS explicitly
made bucket-type discovery retryable and hardened fetch exception handling
([tosfs #299](https://github.com/fsspec/tosfs/issues/299), [#122](https://github.com/fsspec/tosfs/issues/122)).

Blind retry remains unsafe. DNS failure may not be classified retriable by an
underlying SDK ([s3fs Discussion #1033](https://github.com/fsspec/s3fs/discussions/1033)); a timeout after PUT/finalize may mean success despite no response
([s3fs Discussion #883](https://github.com/fsspec/s3fs/discussions/883)).

**For `vosfs`:** current “no automatic replay” policy is conservative and
correct for v0.3.0 because `/synctrans` negotiation and whole PUT lack replay
tokens or conditional idempotency. Keep HTTPX transport retries at zero. Carry
`Retry-After`, status, fault, and partial-completion evidence to callers. Any
future retries need a per-phase table:

| Phase | Default | Proof required before retry |
| --- | --- | --- |
| capabilities / node GET | no replay in v0.3.0 | explicitly adopted idempotent-read policy, bounded attempts, cancellation, fresh auth |
| `/synctrans` POST | never | server idempotency key or safe status lookup |
| negotiated byte GET | never in v0.3.0 | endpoint reusable, version stable, local partial output reset/resume verified |
| node mutation / DELETE | never | conditional request or operation token plus postcondition check |
| byte PUT | never | conditional create/version or upload session with definitive commit status |

### 14. Move/copy semantics must disclose protocol cost and partial completion

**High confidence.** Remote “rename” may be server-native, copy-plus-delete, or
client byte relay. Large s3fs moves behaved very differently from AWS CLI on
S3-compatible storage ([s3fs Discussion #896](https://github.com/fsspec/s3fs/discussions/896)). Core default transfer methods are correct loops but often slow; backends should override only when the protocol offers a verified better primitive
([core source](https://github.com/fsspec/filesystem_spec/blob/1253ee2d2eb291d8e1274b80881a2659c6afbe76/fsspec/spec.py#L925-L1059)). Cross-filesystem rsync also loses options or cannot distinguish two instances of the same protocol with different credentials
([filesystem_spec #1856](https://github.com/fsspec/filesystem_spec/issues/1856)).

**For `vosfs`:** current copy is a negotiated GET-to-PUT relay. DataNode and
ContainerNode move is copy/recreate then delete; LinkNode move is unsupported
and raises `NotImplementedError` after source metadata resolution but before
mutation. Keep the byte-only metadata claim, absent-destination rule,
non-atomicity, and completed/failed path reporting for supported moves. Source
must remain if destination creation or verification fails; a failed source
delete may leave both. Never infer cross-service orchestration from matching
`vos://` protocol.

### 15. Error translation is public compatibility

**High confidence.** Unsupported methods should raise `NotImplementedError`;
read-only mutation may raise `PermissionError` or a read-only `OSError`
([developer docs](https://github.com/fsspec/filesystem_spec/blob/1253ee2d2eb291d8e1274b80881a2659c6afbe76/docs/source/developer.rst)). A backend must distinguish missing, forbidden, conflict, quota, busy, timeout, transport, parse, integrity, and uncertain mutation. Broad `exists()` suppression turns all of them into false absence
([filesystem_spec #1379](https://github.com/fsspec/filesystem_spec/issues/1379)). SSH permission failures at root and S3 inaccurate FileNotFound behavior show how downstream code depends on precision
([sshfs #21](https://github.com/fsspec/sshfs/issues/21), [s3fs #253](https://github.com/fsspec/s3fs/issues/253)).

**For `vosfs`:** retain precise built-ins plus one `VOSpaceError(OSError)` for
remaining OpenCADC faults. Do not translate cancellation. Preserve bounded,
redacted status/fault/retry/partial-completion data and chain the original cause.
Test each mapping through `info`, `exists`, list, read, write, close, recursive
bulk, and wrappers.

### 16. Credentials interact with redirects, caching, and serialization

**High confidence.** Instance reuse can pin credentials selected from mutable
environment state ([s3fs #461](https://github.com/fsspec/s3fs/issues/461)). HTTP
redirect chains can leak or duplicate authorization: NASA/S3 pre-signed targets
failed when caller Basic auth was forwarded to a target that already carried
signature auth, and pre-signed URLs expired quickly
([filesystem_spec #550](https://github.com/fsspec/filesystem_spec/issues/550)).
Different wrapper layers also need exact per-protocol options; matching protocol
alone cannot represent two credential identities
([filesystem_spec #1856](https://github.com/fsspec/filesystem_spec/issues/1856)).

**For `vosfs`:** current service-binding security-method selection and
credential routing are stronger than common generic HTTP behavior. Keep bearer
and X.509 credentials off anonymous/pre-authorized endpoints, block cross-origin
bearer over HTTP, reject userinfo, disable cookies, never cache negotiated URLs,
and redact path/query tokens. Serialize primitive constructor policy only; warn
that a literal token is serialized, and prefer `tokenfile` or environment
resolution. Add cross-origin, double-auth, redirect-loop, signed-query redaction,
pickle/JSON, instance-cache, and changed-tokenfile tests.

Credential discovery should also fail with the original actionable cause.
Silent fallback from broken configured credentials to anonymous access turns a
setup problem into a misleading permission failure
([gcsfs #231](https://github.com/fsspec/gcsfs/issues/231)). `vosfs` correctly
allows anonymous only when no credential source wins precedence; preserve that
explicit boundary.

### 17. Serialization reconstructs policy, never live state

**High confidence.** fsspec serialization reconstructs from class plus storage
arguments/options. Live sessions, loops, locks, open responses, file buffers,
and mutable caches do not belong in serialized state
([features docs](https://github.com/fsspec/filesystem_spec/blob/1253ee2d2eb291d8e1274b80881a2659c6afbe76/docs/source/features.rst), [instance source](https://github.com/fsspec/filesystem_spec/blob/1253ee2d2eb291d8e1274b80881a2659c6afbe76/fsspec/spec.py#L36-L100)). Prefer serializing `OpenFile`, which can reopen, rather than an active file; writable file pickling is unsafe and cached file state can bloat distributed graphs
([OpenFile source](https://github.com/fsspec/filesystem_spec/blob/1253ee2d2eb291d8e1274b80881a2659c6afbe76/fsspec/core.py#L32-L59), [filesystem_spec #1747](https://github.com/fsspec/filesystem_spec/issues/1747)).

**For `vosfs`:** test pickle and `to_json`/`from_json` in a fresh process both
before and after first I/O. Reconstructed instances need new loop, lock, clients,
bindings, directory cache, and environment credentials; deterministic Dask token
must change when content-affecting storage options change. Closed state and live
temp files must not serialize.

### 18. Call signatures and API evolution break downstream backends quietly

**High confidence.** Implementations with signatures different from base
methods break optional arguments and wrappers
([filesystem_spec #1100](https://github.com/fsspec/filesystem_spec/issues/1100)). Callback rollout required careful, staged signature adoption across backends because stray kwargs might be ignored or forwarded into an SDK
([filesystem_spec #668](https://github.com/fsspec/filesystem_spec/issues/668)).
Version skew caused ADLFS recursive removal failure
([adlfs #389](https://github.com/fsspec/adlfs/issues/389)); dependency API change
still breaks sshfs config parsing ([sshfs #54](https://github.com/fsspec/sshfs/issues/54)).

**For `vosfs`:** mirror base names, parameters, defaults, callbacks, and kwargs
for every supported hook. Test minimum and newest fsspec versions and a
pre-release/nightly compatibility lane. Review every fsspec changelog for async
mirroring, ranges, callbacks, serialization, caching, paths, and abstract tests
([current changelog](https://github.com/fsspec/filesystem_spec/blob/1253ee2d2eb291d8e1274b80881a2659c6afbe76/docs/source/changelog.rst)). Pin only when a known incompatibility has an issue and removal condition.

Installed-package behavior needs its own gate. Duplicate entry-point owners for
one protocol can override each other based on install/import order
([adlfs #302](https://github.com/fsspec/adlfs/issues/302)); private dependency
APIs and narrow async-SDK version windows have repeatedly broken backends
([sshfs #54](https://github.com/fsspec/sshfs/issues/54), [s3fs #357](https://github.com/fsspec/s3fs/issues/357)). Test the built wheel, entry-point discovery, lower/upper dependency windows, and a clean environment rather than only editable source.

“Compatible service” also does not prove compatible behavior. S3-compatible
vendors have differed on pagination and checksum requirements
([s3fs #279](https://github.com/fsspec/s3fs/issues/279), [#931](https://github.com/fsspec/s3fs/issues/931)). `vosfs` must continue naming and testing the exact OpenCADC VOSpace profile, not generic HTTP or generic VOSpace compatibility.

### 19. Observability must describe logical and physical I/O separately

**Medium-high confidence.** Users asked for progress callbacks because long
operations otherwise look like black boxes. fsspec added byte callbacks for
file transfers and branched callbacks for bulk work
([filesystem_spec #668](https://github.com/fsspec/filesystem_spec/issues/668)).
Cache users also need to distinguish requested bytes, fetched bytes, hits, and
misses; current discussion shows demand but no universal contract
([Discussion #1527](https://github.com/fsspec/filesystem_spec/discussions/1527)).

**For `vosfs`:** keep callbacks as contract, not logs. Report transferred bytes
for one GET/PUT, completed files for coordinators, and relay bytes for copy.
Future metrics should separate logical requested bytes from whole-object network
bytes, negotiations, cache hits, retries, and partial completions. Logs must use
stable operation/path identifiers while redacting credentials and pre-authorized
URLs. Do not emit per-chunk info logs by default.

### 20. Compliance needs reusable tests plus live semantics

**High confidence.** fsspec now ships reusable abstract `open`, `pipe`, `copy`,
`get`, `put`, and `mv` suites, but the full implementer compliance request
remains open ([abstract tests](https://github.com/fsspec/filesystem_spec/tree/1253ee2d2eb291d8e1274b80881a2659c6afbe76/fsspec/tests/abstract), [filesystem_spec #650](https://github.com/fsspec/filesystem_spec/issues/650)). Quiet backends demonstrate why mocked SDK tests alone are insufficient: Google Drive lacked a practical emulator and large-upload behavior remained uncertain
([gdrive-fsspec #33](https://github.com/fsspec/gdrive-fsspec/issues/33)); OSS tracked emulator versus real-service differences
([ossfs #28](https://github.com/fsspec/ossfs/issues/28)); sshfs test infrastructure itself introduced a race and hang
([sshfs #63](https://github.com/fsspec/sshfs/issues/63)).

**For `vosfs`:** keep three gates:

1. strict hermetic HTTP/XML tests with unmatched network blocked;
2. reusable fsspec abstract tests with every skip mapped to an explicit
   unsupported capability; and
3. authoritative OpenCADC staging tests for exact release commit, including
   scientific-stack consumers and unconditional cleanup.

Do not promote an emulator result to service truth. Do not promote one live
success to deterministic error/race coverage.

Use a FIPS-permitted deterministic hash anywhere `vosfs` creates cache keys or
tokens. Even MD5 used only for non-security identity has failed on FIPS hosts
([filesystem_spec #380](https://github.com/fsspec/filesystem_spec/issues/380)).

## Backend-specific quirks: useful warnings, not universal rules

| Backend | Quirk | Durable interpretation for `vosfs` |
| --- | --- | --- |
| Azure (`adlfs`) | Hierarchical namespace, Blob versus Data Lake APIs, async credential objects, and SDK parallel transfer settings change listing and memory behavior ([#57](https://github.com/fsspec/adlfs/issues/57), [#265](https://github.com/fsspec/adlfs/issues/265)). | Do not copy Azure SDK concurrency or pseudo-directory logic. Copy tests for bounded memory, async awaits, and cheap existence. |
| Alluxio (`alluxiofs`) | Backend itself is a distributed cache and has no repository Issue/Discussion corpus. | Avoid drawing maturity conclusions from silence. Require code/tests and real integration evidence before adopting patterns. |
| Dropbox (`dropboxdrivefs`) | Provider listing API encouraged recursive result leakage into `ls` ([#12](https://github.com/fsspec/dropboxdrivefs/issues/12)). | Adapt provider result shape to fsspec; never expose provider defaults directly. |
| GCS (`gcsfs`) | Prefix directories, marker objects, gRPC/hierarchical bucket fork behavior, and upload retry classes are provider-specific ([#346](https://github.com/fsspec/gcsfs/issues/346), [#379](https://github.com/fsspec/gcsfs/issues/379), [#316](https://github.com/fsspec/gcsfs/issues/316)). | Reuse containment, lifecycle, and retry reasoning; not bucket semantics or retry list. |
| Google Drive (`gdrive-fsspec`) | Duplicate names, resumable 308 responses, OAuth UX, and no reliable emulator complicate correctness ([#33](https://github.com/fsspec/gdrive-fsspec/issues/33)). | VOSpace names are not Drive identities. Reuse full-block/finalize/live-test gates. |
| IPFS (`ipfsspec`) | Gateways differ in metadata and accepted path forms; content is immutable but provider availability varies ([#17](https://github.com/fsspec/ipfsspec/issues/17), [#39](https://github.com/fsspec/ipfsspec/issues/39)). | Do not infer metadata from optional headers. Validate each negotiated OpenCADC response shape. |
| OpenDAL (`opendalfs`) | One bridge exposes many providers; efficient offset read depends on bridge plumbing ([#28](https://github.com/fsspec/opendalfs/issues/28)). | Generic adapters do not erase protocol capability differences. Keep explicit OpenCADC profile. |
| Alibaba OSS (`ossfs`) | Append-style SDK position rules exposed buffered offset bugs ([#60](https://github.com/fsspec/ossfs/issues/60)). | Test `loc`, buffer position, uploaded offset, and size independently even though `vosfs` does whole PUT. |
| S3 (`s3fs`) | Multipart, pseudo-directories, enormous prefix listings, S3-compatible servers, and aiobotocore dependency coupling dominate incidents ([#537](https://github.com/fsspec/s3fs/issues/537), [#357](https://github.com/fsspec/s3fs/issues/357)). | Use its scale/failure lessons; do not import S3 atomicity, multipart, or retry assumptions into Cavern. |
| SSH/SFTP (`sshfs`) | Long-lived socket/server resources and asyncssh dependency behavior make cleanup and permissions visible ([#63](https://github.com/fsspec/sshfs/issues/63), [#54](https://github.com/fsspec/sshfs/issues/54)). | Use explicit lifecycle and dependency-compat gates. VOSpace HTTP transport needs different error mapping. |
| Swift (`swiftspec`) | Account/container URL identity, temporary URLs, segmented large objects, and bulk delete remain distinct capabilities ([#6](https://github.com/fsspec/swiftspec/issues/6), [#2](https://github.com/fsspec/swiftspec/issues/2)). | Do not label unimplemented OpenCADC extensions “generic fsspec gaps”; classify capabilities explicitly. |
| TOS (`tosfs`) | Issue corpus is low-comment and development is PR/stability-workflow driven ([#122](https://github.com/fsspec/tosfs/issues/122), [#299](https://github.com/fsspec/tosfs/issues/299)). | Inspect changelog/tests/PRs as well as Issues; quiet Issues do not mean bug-free. |

## Prioritized `vosfs` recommendations

### P0: correctness before more capabilities

1. Resolve [#66](https://github.com/shinybrar/vosfs/issues/66): text-mode
   exception must issue no PUT. Add flush/encoding failure tests, not only a
   manual `raise` after `write`.
2. Add recursive-delete containment assertions and adversarial path matrix.
   Prove no candidate escapes normalized requested root.
3. Add error-boundary tests showing `exists` returns `False` only for genuine
   absence, never 401/403/429/5xx, timeout, parse, integrity, or cancellation.
4. Add cache-poisoning tests: filtered/failed/partial listing never replaces a
   complete parent; mutations invalidate target, descendants, and parents.
5. Add explicit-close/cancellation tests with warnings-as-errors and no leaked
   response, client, temp file, task, or cached closed instance.

### P1: bounded performance

1. Run distinct `_cat_ranges` whole-object reads through a bounded coordinator.
   Deduplicate paths, preserve result order, honor `batch_size` and `on_error`,
   cancel safely, and retain one GET per object.
2. Audit recursive copy, move, delete, get, put, and pipe for total active HTTP
   requests, negotiations, temp files, and buffered bytes. One shared
   `batch_size` must not hide a larger nested fan-out.
3. Add operation counters in tests: node calls, negotiations, byte calls, bytes
   requested, bytes transferred, max concurrency. Use them as performance
   contracts without promising production metrics API yet.
4. Benchmark deep/wide trees and repeated metadata operations against staging.
   Report request count and peak memory, not elapsed time alone.

### P1: compatibility and lifecycle

1. Run abstract fsspec suites for every supported row and document every skip.
2. Add minimum/latest fsspec CI plus a non-blocking upstream pre-release lane.
3. Add fresh-process pickle/JSON/Dask gates before and after client creation;
   add spawn success and fork fail-fast cases.
4. Add wrapper gates for `simplecache`, `filecache` read, Zarr v3, PyArrow,
   pandas, NumPy, Dask worker reconstruction, and chained URL option routing.
5. Document `async_impl`, `asynchronous`, generated sync facade, no
   `open_async`, and explicit close in one user-facing lifecycle section.

### P2: future contracts, only with server proof

1. Automatic retries: adopt only after phase-specific idempotency and uncertain
   success handling exist.
2. Remote ranges/block cache: adopt only with exact server range/version tests.
3. Native move/delete/jobs: adopt only through advertised binding, job cleanup,
   cancellation, idempotency, and partial-completion contracts.
4. Transactions, atomic create, append/update, multipart/resumable upload, and
   conditional writes: remain unsupported until server primitives exist.
5. Pagination/search: add iterator/callback/cache-completeness semantics before
   scalable-listing claims.

## Regression-test checklist

### Construction, identity, and paths

- [ ] Constructor calls `AsyncFileSystem` base initialization and retains
  fsspec cache/transaction options.
- [ ] `protocol` registration and entry point resolve in fresh process.
- [ ] All published path spellings normalize identically; root forms agree.
- [ ] Unicode, percent escapes, encoded separators, `..`, `::`, userinfo,
  query, fragment, NUL, and trailing slash have explicit outcomes.
- [ ] Service URL and VOSpace authority never cross roles.
- [ ] Pickle/JSON/Dask tokens depend on every content-affecting constructor
  option and contain no live state.

### Metadata, listing, and traversal

- [ ] `ls(detail=True)` item equals `info(name)` for every node type.
- [ ] `ls(detail=False)` returns immediate normalized child names only.
- [ ] `name`, `type`, and `size` are always present and stable.
- [ ] Missing, forbidden, timeout, corrupt XML, unsupported subtype, and
  cancellation are distinct.
- [ ] `exists` suppresses only genuine missing-node result.
- [ ] `walk`, `find`, `glob`, `du`, `tree`, `maxdepth`, `topdown`, `on_error`,
  links, empty tree, deep tree, wide tree, and root match fsspec semantics.
- [ ] No partial/filtered/error listing enters dircache as complete.

### Reads and ranges

- [ ] Whole GET only; server fixture rejects any `Range` header.
- [ ] `cat_file` covers `None`, zero, positive, negative, reversed, empty,
  out-of-bounds, exact EOF, empty object, and changed object.
- [ ] `_cat_ranges` broadcasts scalars, validates lengths, deduplicates objects,
  preserves order, honors `on_error`, and caps concurrency.
- [ ] Repeated seeks/readinto/readline/iteration use one staged download.
- [ ] Identity encoding and raw bytes prevent transparent decompression.
- [ ] Temp file disappears on close, constructor failure, read failure, and
  cancellation.

### Writes

- [ ] Binary and text clean close each issue exactly one PUT.
- [ ] Binary and text block exception issue no PUT.
- [ ] Text encode/flush exception issues no PUT.
- [ ] Zero bytes, large bytes, seek-and-rewrite, content length, content type,
  digest, and callback bytes are exact.
- [ ] Negotiation, local read, upload, response, integrity, cancellation, and
  close failures retain correct state and clean temp files.
- [ ] Double close cannot double upload; `__del__` cannot upload.
- [ ] `create`/`x` race is documented non-atomic and never silently overwrites
  an observed existing target.
- [ ] Append, `+`, deferred commit, transaction, multipart, and resumable modes
  fail before mutation.

### Mutation and partial completion

- [ ] `mkdir`, `makedirs`, `touch`, `rm_file`, `rmdir`, recursive `rm`, copy,
  and move invalidate exact cache entries.
- [ ] Recursive delete never considers an ancestor or sibling-prefix path.
- [ ] Copy preserves bytes and documented metadata boundary.
- [ ] Move keeps source until destination exists and is verified.
- [ ] Source-delete failure reports both surviving paths.
- [ ] Bulk operations identify completed, failed, and unattempted paths; no
  rollback is implied.
- [ ] Cancellation starts no new mutation and reports only confirmed success.

### Async, concurrency, lifecycle

- [ ] Every coroutine hook works on `asynchronous=True` instance.
- [ ] Every mirrored blocking method works on `asynchronous=False` instance.
- [ ] Blocking wrapper on same running loop fails immediately and clearly.
- [ ] `batch_size=1`, small, large, and invalid values have explicit behavior.
- [ ] Peak active requests/temp files remain bounded under failures.
- [ ] `aclose` and `close` are idempotent; closed instance is evicted and
  rejects later I/O.
- [ ] Close racing first client construction cannot leak client.
- [ ] Spawn reconstructs cleanly; forked live instance fails fast without hang.
- [ ] Interpreter shutdown and warnings-as-errors produce no resource warning
  or deadlock.

### Auth, redirects, retries, and security

- [ ] Anonymous, token, tokenfile, environment token, and certificate precedence
  are isolated.
- [ ] Tokenfile/environment token refresh occurs at required request boundary.
- [ ] Pre-authorized target receives no caller auth or cookie.
- [ ] Cross-origin token/certificate routing obeys selected advertised method
  and HTTPS rules.
- [ ] Redirect loop, relative/invalid/userinfo target, expired signed URL,
  double auth, and credential mismatch fail safely.
- [ ] Secrets and signed query/path tokens are redacted from error, log, repr,
  fixture, pickle warning, and partial-completion report.
- [ ] No automatic transport retry occurs; `Retry-After` and uncertain outcome
  survive in error evidence.

### Compatibility and release

- [ ] All applicable abstract fsspec tests pass; each skip cites one unsupported
  matrix row.
- [ ] Minimum and newest supported fsspec both pass.
- [ ] pandas, NumPy, Dask fresh worker, Zarr v3, and PyArrow gates pass within
  published boundaries.
- [ ] `simplecache` read/write and `filecache` read pass; block caches fail
  explicitly.
- [ ] Hermetic suite blocks unmatched network and asserts every route used.
- [ ] Exact release commit passes authoritative OpenCADC live suite; cleanup
  reports residue and runs unconditionally.

## Evidence gaps and confidence

- Organization state is a 2026-07-15 snapshot. Repositories, issues, and counts
  will drift.
- No organization repositories were archived at snapshot time. “Active” cannot
  be inferred from archive flag alone; several quiet repositories rely more on
  PRs, external provider SDKs, or sparse maintainer time.
- `alluxiofs` has no Issues or Discussions. Its incident history is unknown,
  not empty.
- Many older issue comments predate current fsspec behavior. They establish
  recurring failure modes; immutable current source/test links establish the
  present contract.
- GitHub search ranks and truncates results. Theme counts are discovery aids,
  not statistical prevalence measures.
- Provider behavior in S3, GCS, Azure, Drive, IPFS, Swift, TOS, OSS, SSH, and
  Alluxio cannot prove OpenCADC behavior. Recommendations transfer invariant
  client lessons only.
- Retry and async-file APIs remain active design areas. Exact future fsspec
  interfaces may change; `vosfs` should gate behavior, not predict API shape.
- This research did not run other providers' live credentials. It used their
  first-party incident reports, maintainer analysis, source, and tests. `vosfs`
  recommendations still require its own hermetic and OpenCADC live validation.

## Durable decision summary

`vosfs` should remain deliberately narrower than cloud object-store backends.
Success means exact OpenCADC semantics through fsspec, not maximum method count.
Whole-object staging, no automatic replay, explicit unsupported operations,
strict credential routing, and non-atomic mutation disclosure are strengths.

Next quality step is boundary hardening: abort-safe text writes, bounded bulk
work, cache completeness, process/lifecycle safety, destructive containment,
precise errors, and versioned compliance. Only server-proven primitives should
unlock ranges, transactions, native jobs, atomic create, multipart, or retries.
