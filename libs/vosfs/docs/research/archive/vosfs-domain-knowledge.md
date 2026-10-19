# vosfs repository domain knowledge

<!-- pyml disable line-length -->

Researched: 2026-07-25 (America/Vancouver)
Sources: repo docs and source only (see §8)

Status: **Informative synthesis of domain docs.** [`../design/trd.md`](../design/trd.md)
remains the sole normative contract for `vosfs` behavior; [`../design/fsspec-cli/contract.md`](../design/fsspec-cli/contract.md)
is normative for `fsspec-cli`. Linked research notes are evidence, not requirements.

## 1. Executive summary

- **Two library products, one workspace.** `vosfs` (`src/vosfs/`) is an async
  fsspec filesystem for the OpenCADC VOSpace profile (`vos` protocol). `fsspec-cli`
  (`src/fsspec-cli/`) is a library-only Typer command surface hosts embed; it
  declares only `fsspec` + `typer` at runtime and imports no `vosfs` code
  ([`docs/adr/0002-own-async-filesystems-per-invocation.md`](../adr/0002-own-async-filesystems-per-invocation.md),
  [`src/fsspec-cli/pyproject.toml`](../../src/fsspec-cli/pyproject.toml)).
- **Normative backend boundary.** The v0.3.0 capability contract in
  [`docs/design/trd.md`](../design/trd.md) bounds behavior to the OpenCADC
  VOSpace profile evidenced by pinned `opencadc/vos` commit `cf976ce8` (Cavern).
  IVOA VOSpace 2.1 supplies wire vocabulary only; generic VOSpace 2.1 conformance
  is explicitly out of scope.
- **Transfer model.** All byte I/O goes through synchronous `/synctrans`
  negotiation to a **negotiated byte endpoint**. Writes and staged `open` are
  whole-object; partial `cat_file` / `cat_ranges` may send HTTP `Range`.
  Direct `/files` URL construction is forbidden.
- **Capability taxonomy.** Every public behavior is exactly one of native,
  client-derived, extension-conditional, or unsupported
  ([`docs/CONTEXT.md`](../CONTEXT.md), TRD §2). v0.3.0 ships no
  extension-conditional operations.
- **Byte-range policy (current).** TRD §8 and
  [ADR 0007](../adr/0007-validate-range-support-from-206.md): send `Range` for
  partial `cat_*` reads; keep the body only on validated `206`; treat `200`/`204`
  as whole-object fallback. Staged `open` and `blockcache::` stay whole-object /
  unsupported. Live minoc vs Cavern evidence:
  [`vosfs-vault-minoc-byte-range-capability.md`](vosfs-vault-minoc-byte-range-capability.md).

## 2. Products, seams, and ownership

| | **`vosfs`** | **`fsspec-cli`** |
| --- | --- | --- |
| Package path | `src/vosfs/` | `src/fsspec-cli/src/fsspec_cli/` |
| Role | Async `AbstractFileSystem` for OpenCADC VOSpace | POSIX-shaped Typer commands over host-configured async filesystems |
| Entry point | `fsspec.specs`: `vos = vosfs:VOSpaceFileSystem` ([`pyproject.toml`](../../pyproject.toml)) | None; library-only, no executable ([`src/fsspec-cli/README.md`](../../src/fsspec-cli/README.md)) |
| Runtime deps | `fsspec`, `httpx`, `defusedxml` | `fsspec`, `typer` only |
| Normative contract | [`docs/design/trd.md`](../design/trd.md) | [`docs/design/fsspec-cli/contract.md`](../design/fsspec-cli/contract.md) |
| Release | `vX.Y.Z` tags; docs deploy on `vosfs` publish | `fsspec-cli-vX.Y.Z` tags; no docs deploy ([ADR 0001](../adr/0001-release-fsspec-cli-independently.md)) |

**Stable v1 host seam** (embedded command library):

```python
App(sources, *, capabilities=None, extensions=[...]).typer_app
```

([`docs/CONTEXT.md`](../CONTEXT.md) — *Embedded command library*; ADR 0002/0005)

| Responsibility | Owner |
| --- | --- |
| Backend config, credentials, source factories | Host |
| Per-invocation filesystem lifecycle (enter/use/exit) | `fsspec-cli` `App` |
| OpenCADC HTTP, negotiation, node XML | `vosfs` |
| Core command recursion policy (`copy` on, `remove` off by default) | Host via `AppCapabilities` |
| Backend hook result shapes at CLI boundary | Validated by `fsspec-cli`; not trusted ([ADR 0006](../adr/0006-treat-backend-results-as-untrusted-input.md)) |

**Integration pattern.** A VOS host installs both packages, maps a named
`AsyncFilesystemSource` that yields `VOSpaceFileSystem(asynchronous=True,
skip_instance_cache=True)`, and embeds `App(...).typer_app` in its Typer app
(ADR 0002). VOS compatibility tests live in the host; `fsspec-cli` dev group
lists `vosfs` only for tests ([`src/fsspec-cli/pyproject.toml`](../../src/fsspec-cli/pyproject.toml)).

**Out of scope for `vosfs` TRD** (§16): a CLI, registry lookup, credential
acquisition, cross-filesystem orchestration. The CLI product exists separately
as `fsspec-cli`; it is not part of the `vosfs` capability contract.

## 3. OpenCADC VOSpace profile and normative capability contract

### 3.1 Profile boundary

| Concept | Definition (primary source) |
| --- | --- |
| **OpenCADC VOSpace profile** | VOSpace behavior implemented by pinned `opencadc/vos` Cavern source; bounds the `vosfs` contract ([`docs/CONTEXT.md`](../CONTEXT.md), TRD §1) |
| **Server contract snapshot** | [`opencadc/vos` @ `cf976ce8141dd3341631b7f3e07aa38443d42f58`](https://github.com/opencadc/vos/tree/cf976ce8141dd3341631b7f3e07aa38443d42f58) (TRD §1) |
| **IVOA VOSpace 2.1** | Wire vocabulary for implemented operations; does **not** enlarge v0.3.0 surface (TRD §1) |
| **Explicit non-claim** | Generic VOSpace 2.1 conformance or unrelated VOSpace implementations (TRD §1, §16) |

Contract version stays at **v0.3.0** independently of package version (currently
`vosfs` 0.7.0 per [`pyproject.toml`](../../pyproject.toml)); later hardening
preserves the public boundary (TRD header).

### 3.2 Supported server resources (native)

Discovered via `endpoint_url + "/capabilities"` on first I/O (TRD §5):

| Binding | Capability ID | Use |
| --- | --- | --- |
| Nodes | `ivo://ivoa.net/std/VOSpace/v2.0#nodes` | GET/PUT/POST/DELETE on `/nodes/*` |
| Synchronous transfer | `ivo://ivoa.net/std/VOSpace#sync-2.1` | POST `/synctrans` for pull/push negotiation |
| Negotiated byte endpoint | Returned URL only | HEAD/GET/PUT whole bytes |

Supported credentials: anonymous (empty method), bearer token, TLS client cert.
Cookie auth is not supported (TRD §3.1, §5).

Missing `#sync-2.1` binding disables byte read/write only; node ops continue
(TRD §5 — supported degradation).

### 3.3 Explicitly excluded server resources (unsupported)

From TRD §5.1 and §16:

- Async `/transfers` jobs, native server-side move
- `/async-delete`, `/async-setprops`
- `/pkg`, `/protocols`, `/views`, `/properties`
- Search, server-side sort, paginated listing
- `copyNode`, `pullToVoSpace`, `pushFromVoSpace`, bidirectional mounts
- UWS job lifecycle (poll/abort/cleanup)
- Direct `/files` URL construction
- Public property/permission/link APIs; FUSE; remote mmap

## 4. Domain glossary (CONTEXT.md vocabulary)

Terms below are defined in [`docs/CONTEXT.md`](../CONTEXT.md). Use these names;
avoid the listed synonyms.

| Term | Meaning |
| --- | --- |
| **Native capability** | One implemented, tested OpenCADC profile operation |
| **Client-derived capability** | Composed only from native ops + documented fallbacks |
| **Extension-conditional capability** | Wired only when service advertises and behavior is verified |
| **Unsupported capability** | Deliberately excluded from contract scope |
| **Application capability** | Host policy snapshotted by `fsspec-cli` (`recursion.copy` / `recursion.remove`); not backend discovery |
| **Embedded command library** | The `fsspec-cli` package and its `App(...).typer_app` seam |
| **Async filesystem source** | Host factory → async context manager → one `AbstractFileSystem` per invocation |
| **Mapped filesystem operand** | `name:/path` selects a configured source |
| **Service base URL** | Caller-supplied HTTP URL (e.g. `https://…/arc`); not `vos://` authority |
| **Filesystem path** | Path after `vos://`; first segment is **not** a service selector |
| **VOSpace authority** | Logical authority in server XML; discovered from root node, cached per instance |
| **Negotiated byte endpoint** | Temporary/pre-auth URL from `/synctrans`; not a constructed `/files` URL |
| **Whole-object staged read** | Full download to disk-backed temp before local seek/range |
| **Staged write** | Buffered write; one whole-object PUT on successful close |
| **Synchronous transfer negotiation** | One `/synctrans` POST → 303 → optional transfer details GET → byte endpoint |
| **Recursive removal** | Client-side traversal + leaves-first DELETE; no `/async-delete` |
| **Internal LinkNode** | Same-authority link resolvable without cross-service I/O |
| **Service binding** | Operation URL + security method from VOSI capabilities |
| **VOSpaceError** | Single public `OSError` subclass for remaining OpenCADC failures |

## 5. Architecture decision records

| ADR | One-line decision |
| --- | --- |
| [0001](../adr/0001-release-fsspec-cli-independently.md) | `fsspec-cli` is an independent uv workspace member with separate Release Please tags (`fsspec-cli-vX.Y.Z`); no runtime dependency between packages. |
| [0002](../adr/0002-own-async-filesystems-per-invocation.md) | Host supplies `AsyncFilesystemSource` factories; `App` owns yielded filesystem for one invocation; stable seam `App(sources, *, capabilities, extensions).typer_app`. |
| [0003](../adr/0003-acquire-referenced-async-filesystem-sources.md) | Acquire all referenced sources sequentially before any filesystem I/O; reverse-order exit; deterministic diagnostics and status precedence. |
| [0004](../adr/0004-add-opt-in-command-extensions.md) | Opt-in `extensions=[...]` adds commands without changing core surface or receiving application capabilities. |
| [0005](../adr/0005-define-typer-owned-commands-and-callback-extensions.md) | Typer owns parsing/help; annotated callbacks own semantics; extensions are synchronous `CommandCallback` functions, not registrars. |
| [0006](../adr/0006-treat-backend-results-as-untrusted-input.md) | CLI trusts host source choice but validates every hook result shape before use (exact types, path safety). |
| [0007](../adr/0007-validate-range-support-from-206.md) | Partial `cat_*` may send `Range`; keep body only on validated `206`; `200`/`204` whole-object fallback. |

Partial supersession: ADR 0002 replaces live-instance injection clauses in 0001;
ADR 0005 replaces registrar clauses in 0004. Independent-release and opt-in
extension decisions remain accepted.

## 6. Reads, writes, and transfers (domain level)

### 6.1 Synchronous transfer negotiation

Every logical byte read or write (TRD §7):

```text
1. Build VOSpace 2.1 transfer XML (authority-qualified target)
2. Direction: pullFromVoSpace + httpsget (read) or pushToVoSpace + httpsput (write)
3. POST to discovered /synctrans (redirects disabled)
4. Follow 303 Location → transfer details or byte endpoint
5. GET transfer details if needed; choose compatible protocol/security method
6. HEAD/GET/PUT on negotiated endpoint only
```

Constraints: no automatic replay of negotiation POST; no endpoint caching across
transfers; redirect chain limited to approved synctrans 303 flow; credential routing
follows negotiated security method, not origin (TRD §7).

### 6.2 Reads (whole-object and response-validated Range)

| Surface | Behavior | Class |
| --- | --- | --- |
| `_get_file` | Stream one negotiated whole GET to local path | Native |
| `_cat_file` | `Range` when bounds allow; `206` keeps partial, else whole + slice | Client-derived |
| `_cat_ranges` | Per-object negotiate; ranged while `206`, else one whole stage | Client-derived |
| `open("rb"/"r")` | Whole-object download to disk-backed temp; local seek/read | Client-derived |

TRD §8: `Accept-Encoding: identity`; empty 204 → `b""`; `block_size`/`cache_*`
do not turn staged `open` into a ranged transport.

### 6.3 Staged write

| Surface | Behavior | Class |
| --- | --- | --- |
| `_put_file` / `_pipe_file(mode="overwrite")` | One negotiated whole PUT | Native |
| `_pipe_file(mode="create")` | `_info` preflight then PUT (non-atomic) | Client-derived |
| `open("wb"/"w"/"xb"/"x")` | Temp file; PUT on successful close | Client-derived |

Upload: raw bytes, `Content-Length` when known, MD5 validated on success or 412
(TRD §9). Failed PUT reported as uncertain write (OpenCADC may truncate).

### 6.4 Namespace mutations (selected)

| Operation | Mechanism | Class |
| --- | --- | --- |
| `mkdir` | PUT ContainerNode | Native |
| `makedirs` | Top-down ancestor creation | Client-derived |
| `rm_file` | DELETE one non-container | Native |
| `rm` recursive | Client traversal, leaves-first DELETE | Client-derived |
| `cp_file` / recursive `cp` | Bounded byte relay | Client-derived |
| `mv` (DataNode/ContainerNode) | Copy/recreate then delete source | Client-derived |
| `touch(truncate=True)` | PUT zero bytes | Native |

Move never calls `/transfers`. Recursive `rm` never calls `/async-delete`
([`docs/CONTEXT.md`](../CONTEXT.md) — *Recursive removal*).

## 7. Capability classification summary

From TRD §2 and §11 matrix (representative rows):

| Class | Examples |
| --- | --- |
| **Native** | `_info`, `_ls`, `_get_file`, `_put_file`, `_pipe_file(overwrite)`, `_rm_file`, `mkdir`, `touch(truncate=True)`, capabilities/nodes/synctrans negotiation |
| **Client-derived** | `exists`, `walk`, `glob`, `cat_file`, `cat_ranges`, `open(r/w)`, coordinated `put`/`pipe`/`get`, `makedirs`, `rm`/`rmdir`, `cp`/`mv`, blocking/async facades, directory cache, pickle/JSON |
| **Extension-conditional** | None in v0.3.0 release set (TRD §2) |
| **Unsupported** | `open_async`, append/`+` modes, `created`, question-mark globs, LinkNode move, external LinkNode copy, FUSE, `blockcache::`/`cached::`, async UWS, direct `/files` URLs, public property APIs |

Unsupported calls **must** raise `NotImplementedError` before remote mutation
(TRD §2).

**Scientific-stack gates** (TRD §14): narrow claims for pandas CSV, NumPy
file-object I/O, Dask CSV, Zarr v3 `FsspecStore`, PyArrow Parquet — each with
explicit boundaries (e.g. no remote `mmap_mode`; staged `open` still
whole-object even when `cat_*` can range).

## 8. Byte-range / Range-header policy

### 8.1 Normative position (controlling)

TRD §8, matrix row "Remote Range/206" (Client-derived), and
[ADR 0007](../adr/0007-validate-range-support-from-206.md):

- Partial `cat_file` / `cat_ranges` **MAY** send `Range`.
- Keep the partial body only on validated `206` (`Content-Range` + length).
- `200`/`204` → whole-object fallback + local slice (Cavern-safe).
- Do not trust `Accept-Ranges` alone. Staged `open` stays whole-object.
- `blockcache::` / `cached::` remain unsupported (TRD §16).

### 8.2 Informative deployment evidence

[`vosfs-vault-minoc-byte-range-capability.md`](vosfs-vault-minoc-byte-range-capability.md)
(probed 2026-07-24):

| Deployment | Byte server | Range behavior |
| --- | --- | --- |
| `arc` (Cavern) | Same host | Ignores `Range`; returns `200` with full body |
| `vault` (minoc) | Separate minoc host | Honours `Range`; returns `206` with correct `Content-Range` |

That evidence drove the contract change. The same note still records a separate
anonymous-credential parsing defect in
[`src/vosfs/capabilities.py`](../../src/vosfs/capabilities.py) (not Range scope).

## 9. Engineering and process constraints shaping the domain

From [`CONTRIBUTING.md`](../../CONTRIBUTING.md) and [`AGENTS.md`](../../AGENTS.md):

| Constraint | Effect on domain |
| --- | --- |
| `uv` workspace with two packages | Co-development; shared lockfile; `--all-packages` sync required |
| Offline deterministic tests | Hermetic RESpx/HTTPX mocks for `vosfs`; no network in CI |
| `vosfs` ≥90% branch coverage | Capability matrix backed by executable gates (TRD §15) |
| `fsspec-cli` wheel tested in isolation | Undeclared deps cannot hide behind workspace |
| Docs under `docs/user/` + Zensical strict build | User-facing behavior must match implemented surface |
| Single-context domain layout | [`docs/CONTEXT.md`](../CONTEXT.md) + [`docs/adr/`](../adr/) + TRD; agents read glossary before exploring ([`docs/agents/domain.md`](../agents/domain.md)) |

## 10. `vosfs` source module map

Primary implementation under `src/vosfs/` ([`src/vosfs/__init__.py`](../../src/vosfs/__init__.py)
exports `VOSpaceFileSystem`, `VOSpaceError`):

| Module | Domain concern |
| --- | --- |
| `filesystem.py` | fsspec hooks, read/write staging orchestration |
| `capabilities.py` | VOSI binding resolution, credential vs securityMethod matching |
| `negotiate.py` | `/synctrans` POST, 303 chain, protocol selection |
| `nodes.py` | Node XML ↔ fsspec `info` mapping |
| `staging.py` | Disk-backed temp files for staged read/write |
| `transport.py` | HTTPX client pool, redirect policy |
| `_transfer.py` | Byte GET/PUT against negotiated endpoint |
| `_removal.py`, `_moving.py`, `_coordination.py` | Client-derived delete/move/copy |
| `_integrity.py` | MD5 validation (write path) |
| `paths.py`, `config.py`, `xmlio.py`, `errors.py` | Path grammar, options, XML I/O, exception mapping |

## 11. Sources

- [`docs/CONTEXT.md`](../CONTEXT.md) — ubiquitous language
- [`docs/agents/domain.md`](../agents/domain.md) — agent consumption rules
- [`docs/design/trd.md`](../design/trd.md) — normative `vosfs` v0.3.0 contract
- [`docs/design/fsspec-cli/contract.md`](../design/fsspec-cli/contract.md) — normative CLI contract
- [`docs/design/fsspec-cli/README.md`](../design/fsspec-cli/README.md) — CLI doc map
- [`docs/adr/`](../adr/) — ADRs 0001–0007
- [`CONTRIBUTING.md`](../../CONTRIBUTING.md), [`AGENTS.md`](../../AGENTS.md)
- [`README.md`](../../README.md), [`src/fsspec-cli/README.md`](../../src/fsspec-cli/README.md)
- [`pyproject.toml`](../../pyproject.toml), [`src/fsspec-cli/pyproject.toml`](../../src/fsspec-cli/pyproject.toml)
- [`docs/research/vosfs-vault-minoc-byte-range-capability.md`](vosfs-vault-minoc-byte-range-capability.md) — informative deployment probes
