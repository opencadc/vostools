# vosfs vs. IVOA VOSpace 2.1 — Improvement Analysis

Feeds a `vosfs` capability-contract (TRD) update. Research + analysis only; no
repo files were modified.

**Reference standard:** IVOA VOSpace 2.1 Recommendation
(`REC-VOSpace-2.1`, 2018-06-20), fetched twice (broad + targeted). Fetches
succeeded and returned node-type, property, protocol, security-method,
transfer, view, capability, and fault material; exact URI/fault strings quoted
below are from those fetches.

**Scope guardrail (honored throughout):** The TRD targets the *OpenCADC
VOSpace profile only* and states vosfs **"MUST NOT claim generic VOSpace 2.1
conformance"** (trd.md:43-44). IVOA 2.1 is used here only as the *reference
vocabulary* for the operations vosfs already implements, per trd.md:39-41
("defines the wire vocabulary used by the implemented OpenCADC operations; it
does not enlarge the v0.3.0 capability surface"). No finding below recommends
blanket IVOA conformance.

---

## 1. Classification summary

| # | Finding | Class | REC anchor | vosfs anchor |
|---|---|---|---|---|
| F1 | Resolved: `mv` of a LinkNode is unsupported and raises `NotImplementedError` after source resolution but before mutation; the prior byte-materializing behavior and link-recreation promise are superseded. | CORRECTNESS-FIX (resolved) | §3 node types; §5 moveNode | filesystem.py:1483-1499,1566-1708; trd.md:265-266 |
| F2 | `mtime`/`modified` read only from `#date`; IVOA canonical modification property is `#mtime` | FIDELITY (borderline correctness for `modified`) | §3.2 props | nodes.py:41,312; filesystem.py:301-308 |
| F3 | Fault vocabulary `_KNOWN_FAULTS` incomplete vs IVOA 2.1 | FIDELITY | §5 faults | errors.py:162-172 |
| F4 | `#MD5` and `#contenttype` promoted as first-class fields are OpenCADC extensions, **not** IVOA-standard property URIs | FIDELITY (document) | §3.2 props | nodes.py:40,42 |
| F5 | Async `/transfers`, `copyNode`, views, `/protocols`, `/properties`, search, pagination, structured views, permission/property writes | DELIBERATE-EXCLUSION | §5, §6, §8 | trd.md:197-208, 610-628 |
| F6 | Native server-side move via `/transfers` (atomic `mv`) | ROADMAP | §5 moveNode | filesystem.py:1483-1499,1566-1708; trd.md:216,619 |
| F7 | LinkNode creation (`symlink`, link-preserving move) | ROADMAP | §3 LinkNode | nodes.py (no link writer); trd.md:265-268,627 |
| F8 | `xsi:type` QName prefix trusted un-resolved; update deny-list omits IVOA server-computed timestamps | FIDELITY / hardening | §3 xsi:type; §3.2 props | nodes.py:320-353, 69-84 |
| F9 | Protocol IDs, direction keywords, security-method IDs, capability standard-IDs, XML namespace/version — **all correct & complete for scope** | FIDELITY (pass) | §3.5/§3.6/§8 | negotiate.py:26-27; capabilities.py:21-29; nodes.py:33-34,45 |
| F10 | `/pkg` bulk download; `/async-delete`; public property-update API | ROADMAP | (OpenCADC ext.) | trd.md:200-208 |

---

## 2. Vocabulary / model fidelity — item by item

### 2.1 Node types — CORRECT mapping, one durable-identity caveat

REC defines `vos:Node`, `vos:DataNode`, `vos:UnstructuredDataNode`,
`vos:StructuredDataNode`, `vos:ContainerNode`, `vos:LinkNode` carried on the
`xsi:type` attribute. vosfs maps them at nodes.py:49-55:

| IVOA `xsi:type` | vosfs node_type | fsspec `type` | Verdict |
|---|---|---|---|
| `vos:ContainerNode` | `container` | `directory` | correct |
| `vos:DataNode` | `data` | `file` | correct |
| `vos:StructuredDataNode` | `data` (opaque) | `file` | correct-in-profile* |
| `vos:UnstructuredDataNode` | `data` (opaque) | `file` | correct-in-profile* |
| `vos:LinkNode` | `link` | `other` + `islink`+`target` | correct |

\* Collapsing Structured/Unstructured to opaque `data` is a **deliberate,
correct** choice: OpenCADC "reconstructs every regular file as base `DataNode`"
(opencadc-vos-supported-api.md:101), so the IVOA subtype distinction is not
durable server-side. Documented at trd.md:240-242. No gap.

### 2.2 Property URIs — mixed: correct where IVOA-standard, two OpenCADC extensions

REC §3.2 standard system properties (quoted from fetch):
`#mtime` "data modification time", `#ctime` "status change … time",
`#btime` "initial creation time", `#length` "length or size of a resource",
`#quota`, `#availableSpace`; plus Dublin-Core `#date`, `#format`, `#type`, etc.
The REC fetch confirms: *"The specification does not list `#contenttype` or
`#md5` in the standard properties enumeration."*

vosfs promotes four URIs to first-class `Node` fields (nodes.py:39-42):

| vosfs constant | URI | IVOA-standard? | Verdict |
|---|---|---|---|
| `LENGTH_PROPERTY_URI` | `…core#length` | **Yes** (`#length`) | correct (F-pass) |
| `DATE_PROPERTY_URI` | `…core#date` | Yes, but as generic Dublin-Core date; IVOA modification-time is `#mtime` | **F2** |
| `MD5_PROPERTY_URI` | `…core#MD5` | **No** — OpenCADC extension | **F4** |
| `CONTENT_TYPE_PROPERTY_URI` | `…core#contenttype` | **No** — OpenCADC extension | **F4** |

**F2 (FIDELITY, borderline correctness):** `Node.mtime` and therefore
`info["mtime"]` / `modified()` derive solely from `#date` (nodes.py:312,
filesystem.py:301-308). IVOA 2.1 defines `#mtime` as the canonical
modification timestamp; `#date` is *"point or period of time associated with an
event"*. Within the OpenCADC profile this is correct (Cavern populates `#date`,
not `#mtime`), but the mapping reads the *less* canonical URI. If a server
populated `#mtime` and omitted `#date`, `modified()` raises
`VOSpaceError` "no modification date" (filesystem.py:306-307). **Low-cost,
in-profile-safe fix:** prefer `#mtime`, fall back to `#date`. Keeps OpenCADC
behavior identical while aligning with the IVOA property the REC blesses.

**F4 (FIDELITY, document-only):** `#MD5` and `#contenttype` are correct for the
OpenCADC wire but are **not** IVOA-standard URIs. The TRD/spec should label
them explicitly as OpenCADC-profile property extensions so a reader does not
mistake them for the IVOA core set. (`#length`, `#groupread`, `#groupwrite`,
`#publicread`, `#quota` used in the deny-list *are* IVOA-standard — see 2.6.)
Note also both lookups are case-sensitive dict hits (nodes.py:312-314); fine
because OpenCADC emits the exact casing, but that is a profile assumption worth
a one-line spec note.

### 2.3 Protocol identifiers — CORRECT and complete (F9 pass)

REC §3.5: `#httpget`, `#httpput`, `#httpsget`, `#httpsput` under
`ivo://ivoa.net/vospace/core#`. vosfs uses exactly `…core#httpsget` and
`…core#httpsput` (negotiate.py:26-27) and never requests the plaintext
variants — the right choice for a credentialed profile. No gap.

### 2.4 Transfer directions — CORRECT (F9 pass)

REC §8 external directions: `pullFromVoSpace`, `pushToVoSpace`,
`pullToVoSpace`, `pushFromVoSpace`. vosfs emits only `pullFromVoSpace` /
`pushToVoSpace` (nodes.py:45; negotiate.py:23-24) and *validates* the direction
before building the document (nodes.py:243-245). `pullToVoSpace` /
`pushFromVoSpace` are correctly excluded — OpenCADC throws "not implemented"
for both (opencadc-vos-supported-api.md:91). No gap.

### 2.5 Security methods — CORRECT (F9 pass)

REC §3.6.1 quotes `ivo://ivoa.net/sso#tls-with-certificate` and `#BasicAA`, and
defers the full list to the IVOA SSO profile. vosfs supports:
anonymous (empty), `ivo://ivoa.net/sso#token`,
`ivo://ivoa.net/sso#tls-with-certificate` (capabilities.py:27-29), and
**deliberately rejects cookie auth** (`…sso#cookie`) as a credential source
(trd.md:178-179). All three identifiers are correct IVOA SSO strings; cookie
exclusion is a sound security decision. Credential routing keys off the
negotiated `securityMethod` rather than origin (negotiate.py:61-76,
filesystem.py:373-414) — matches the REC's per-endpoint security-method model.
No gap.

### 2.6 Capability standard identifiers — CORRECT (F9 pass)

REC standard IDs: `ivo://ivoa.net/std/VOSpace/v2.0#nodes` and
`ivo://ivoa.net/std/VOSpace#sync-2.1`. vosfs pins both exactly
(capabilities.py:21-22) and selects by *identifier*, standard-role ParamHTTP
interface, and `use=base`/`use=full` access URL (capabilities.py:83-84,146-162)
— not by display name or URL suffix (trd.md:170-174). No gap.

### 2.7 XML namespace / version — CORRECT (F9 pass)

Generated documents use `http://www.ivoa.net/xml/VOSpace/v2.0` with
`version="2.1"` on the root (nodes.py:33-34,247,450). This is the documented
IVOA 2.1 convention (the schema namespace remained at `…/v2.0` while the
document `version` attribute advances to `2.1`). defusedxml rejects DTD/XXE
(xmlio.py:42) and bodies are bounded pre-parse (xmlio.py:37-39). No gap.

### 2.8 Fault vocabulary — INCOMPLETE (F3, FIDELITY)

REC per-operation faults (from targeted fetch) and their HTTP mappings:

| IVOA fault | HTTP | In vosfs `_KNOWN_FAULTS`? |
|---|---|---|
| `NodeNotFound` | 404 | yes |
| `ContainerNotFound` | 404 | yes |
| `PermissionDenied` | 403 | yes |
| `InvalidURI` | 400 | yes |
| `DuplicateNode` | 409 | yes |
| `InvalidArgument` | 400 | yes |
| `TypeNotSupported` | 400 | **no** |
| `LinkFound` | 400 | **no** |
| `ViewNotSupported` | (transfer) | **no** |
| `ProtocolNotSupported` | (transfer) | **no** |
| `InvalidToken` | (auth) | **no** |
| `InternalFault` | 500 | **no** |
| `QuotaExceeded` | (OpenCADC 413) | yes (also drives ENOSPC) |
| `NodeLocked` | (OpenCADC 423) | yes |
| `ServiceBusy` | (OpenCADC 503) | yes |

`_KNOWN_FAULTS` is explicitly "intentionally small and best-effort"
(errors.py:160-172) and only (a) drives the quota→`ENOSPC` mapping and (b)
enriches the `fault` diagnostic string; it never changes control flow. HTTP
status still maps correctly regardless (403→`PermissionError`,
409→`FileExistsError`, etc., errors.py:39-45). **Therefore this is FIDELITY,
not a functional bug** — but adding `TypeNotSupported`, `LinkFound`,
`ViewNotSupported`, `ProtocolNotSupported`, `InvalidToken`, `InvalidData`,
`InternalFault` costs nothing, stays in scope, and improves the symbolic fault
surfaced on `VOSpaceError.fault`. Ties to **#113 (hardening)**.

Note: vosfs correctly declines to *remap* a server 400 to `ValueError` even
though IVOA 400 faults exist — trd.md:475 reserves `ValueError` for
*client-side* invalid input, and errors.py:289 routes server 400 to
`VOSpaceError` with the status retained. That divergence from a naive IVOA
reading is intentional and documented (errors.py:255-260). No change needed.

---

## 3. Correctness gaps (in scope now)

### F1 — LinkNode move is explicitly unsupported (resolved)

As originally evaluated, TRD §6 promised that client-derived move recreated a
LinkNode, while the implementation instead relayed the target bytes into a new
DataNode and then deleted the source link. That behavior was a correctness gap:
the repository had no LinkNode creation primitive, so the promise could not be
implemented.

That defect and promise are superseded. Current `mv` resolves source metadata
before destination handling and raises `NotImplementedError` for a LinkNode
before copy, delete, rollback, or cleanup mutation. This applies when the
destination is absent, exists, or is the source path itself. Copying an internal
LinkNode whose target has the discovered VOSpace authority continues to
materialize its target bytes as a DataNode; copying an external or non-VOS
LinkNode raises `NotImplementedError` before destination mutation.

The resolution selected the previously recommended unsupported behavior rather
than byte-materializing move semantics. LinkNode creation and link-preserving
move remain outside the current profile after #202 was closed as not planned.
IVOA relevance remains: LinkNode is an IVOA §3 node type and moveNode is an IVOA
§5 operation. Ties to **#63 (LinkNode)** and its implementation ticket #260.

### (No other functional correctness gap found)

The negotiation 303 chain (filesystem.py:321-371), redirect validation
(negotiate.py:79-111), hop cap (`range(1,6)` + loop detection,
filesystem.py:348-371), whole-object read/slice (filesystem.py:507-550),
create-or-truncate PUT with 412-integrity + uncertain-write reporting
(filesystem.py:602-632), and transferDetails `<protocol>/<endpoint>/
<securityMethod standardID>` parsing (negotiate.py:38-58,114-125) all match the
IVOA 2.1 synchronous-transfer and byte-endpoint model as scoped. These are
solid.

---

## 4. Deliberately-excluded capabilities (F5 / F10) — confirm + document

For each IVOA 2.1 feature vosfs excludes, the exclusion is a **conscious
decision** (TRD §5.1/§16) **and**, for most, OpenCADC itself cannot support a
low-cost partial adoption. The double-justification is the key documentation
point.

| IVOA 2.1 feature | Conscious exclusion? | Low-cost partial adoption in-profile? | Evidence |
|---|---|---|---|
| Async `/transfers` jobs (UWS) | Yes (trd.md:201,617) | No — full UWS lifecycle needed | supported-api.md:33-52 |
| `copyNode` (server copy) | Yes (trd.md:207) | **Impossible** — OpenCADC throws "copyNode is not implemented" | supported-api.md:93 |
| Native `moveNode` | Yes (trd.md:201,207) | Possible but needs async UWS → **F6 roadmap** | supported-api.md:92 |
| Views (`#defaultview`/`#binaryview`/`#anyview`) | Yes (trd.md:203) | No — OpenCADC `/views` 404s, `getViews()` throws | supported-api.md:72,107 |
| `/protocols` endpoint | Yes (trd.md:203) | No — OpenCADC unimplemented (404) | supported-api.md:72 |
| `/properties` endpoint | Yes (trd.md:203) | No — OpenCADC unimplemented (404) | supported-api.md:72 |
| Search (`#search` std-ID) | Yes (trd.md:204) | No — OpenCADC maps no search servlet | supported-api.md:73 |
| Paginated / sorted listing | Yes (trd.md:204) | **Impossible** — OpenCADC declares pagination unsupported and *throws*; `sort`/`order` throw | fsspec-matrix.md:37; supported-api.md:84 |
| Structured/Unstructured views | Yes (trd.md:240-242) | No — OpenCADC normalizes to base DataNode | supported-api.md:101 |
| Permission / generic property writes | Yes (trd.md:251,255-258) | Partial primitive already exists (private POST) → **F10/#65** | supported-api.md:86 |
| LinkNode creation | Yes (trd.md:268,627) | Possible — OpenCADC supports it → **F7 roadmap/#63** | supported-api.md:100 |
| `/pkg` package download | Yes (trd.md:202) | Possible — OpenCADC supports → **F10 roadmap** | supported-api.md:70 |
| `/async-delete`, `/async-setprops` | Yes (trd.md:200) | Possible but async UWS → **F10 roadmap** | supported-api.md:68-69 |

**Spec action:** for each row, record *both* "out of v0.3.0 scope" *and* the
server-side reason (throws / 404 / normalized), so future readers see the
exclusion is not an oversight. Several exclusions are doubly-safe (the profile
server can't support them anyway).

---

## 5. Roadmap opportunities (future; require new capability contract + server support)

All carry the scope caveat: each needs a **new published capability contract +
implementation gates** (trd.md:626-628), and OpenCADC server support.

| Item | Value | Server support | New contract needs | Issue link |
|---|---|---|---|---|
| **F6** Native async move via `/transfers` | Atomic `mv`; removes the client copy+delete window (filesystem.py:1483-1499,1566-1708) | OpenCADC **implements** same-service move (supported-api.md:92) | Async UWS phase-start/poll/abort lifecycle (trd.md:216,619 excludes it) | new; complements move semantics |
| **F7** LinkNode creation | Could enable real `symlink` and link-preserving move under a future capability contract. | OpenCADC supports LinkNode create (supported-api.md:100) | `build_link_document` writer + PUT LinkNode + external/internal target policy | **extends #63** |
| **F10a** Public node-update / property-write API | Exposes the *already-present* private POST primitive (nodes.py:255-281) for titles/descriptions | OpenCADC POST update supported (supported-api.md:86) | Public method + allowed-property policy surface | **extends #65** |
| **F10b** `/pkg` bulk download | Efficient recursive `get` of a container as TAR/ZIP | OpenCADC supports (supported-api.md:70) | Package-view transfer + stream contract | new |
| **F10c** `/async-delete` recursive rm | Fewer round-trips than client leaves-first walk | OpenCADC supports (supported-api.md:68) | Async UWS lifecycle | new |

---

## 6. Additional hardening (F8) — low priority, in-scope

- **`xsi:type` prefix trusted, not namespace-resolved** (nodes.py:320-353):
  a hypothetical `other:ContainerNode` bound to a non-VOSpace namespace would
  classify by local name. Documented as acceptable for the trusted profile
  (nodes.py:328-333); stdlib ElementTree cannot resolve attribute-value QNames.
  A defended parser would tighten this. Ties **#113**.
- **Update deny-list omits IVOA server-computed timestamps**
  (nodes.py:69-84): the list refuses `owner/group/groupread/groupwrite/
  publicread/permission/quota/length/md5/checksum/creator/type` — this matches
  the TRD §6 requirement exactly (trd.md:256-258) and covers the IVOA
  access-control (`#groupread`/`#groupwrite`/`#publicread`) and system
  (`#quota`/`#length`) fragments. It does **not** refuse IVOA `#mtime`/`#ctime`/
  `#btime`/`#date`/`#availableSpace`. OpenCADC rejects those server-side
  (immutable), so no functional gap, but adding them client-side is cheap
  defense-in-depth. Ties **#65 + #113**.
- **Cancellation of a staged read** (relevant to **#66 text-abort**): `_open`
  for read fully downloads via `self.get_file(...)` *before* returning the
  `StagedReadFile` (filesystem.py:576-579), so a text-mode consumer cannot abort
  mid-download through the returned file object. Not IVOA-derived, but the
  whole-object staging model is the reason #66 exists; any abort contract must
  target the `get_file`/negotiation coroutine, not the file wrapper. The
  streaming byte path itself does honor cancellation/`aclose` (filesystem.py:
  439-444,482-483,504-505) per trd.md:499-501.

---

## 7. Prioritized shortlist (top 8)

| Rank | Finding | Class | One-line rationale | Issue |
|---|---|---|---|---|
| 1 | **F1** LinkNode move rejection | CORRECTNESS-FIX (resolved) | Source metadata is resolved and `NotImplementedError` is raised before mutation. | **#63 / #260** |
| 2 | **F2** source `mtime`/`modified` from `#mtime` then fall back to `#date` | FIDELITY | Aligns with the IVOA-canonical modification property at zero behavior change in-profile | — |
| 3 | **F3** complete `_KNOWN_FAULTS` (TypeNotSupported, LinkFound, ViewNotSupported, ProtocolNotSupported, InvalidToken, InvalidData, InternalFault) | FIDELITY | Zero-cost, in-scope diagnostic fidelity on `VOSpaceError.fault` | **#113** |
| 4 | **F4** label `#MD5`/`#contenttype` as OpenCADC extensions in the spec | FIDELITY (doc) | Prevents mis-reading OpenCADC URIs as IVOA-standard core properties | — |
| 5 | **F5** document each exclusion with its *server-side* reason too | DELIBERATE-EXCLUSION | Proves exclusions are conscious + often doubly-safe (server throws/404s) | — |
| 6 | **F6** native async server-side move | ROADMAP | Only path to atomic `mv`; OpenCADC already implements move | new (rel. #65) |
| 7 | **F7** LinkNode creation primitive | ROADMAP | Could enable real `symlink` and link-preserving move under a future contract. | **extends #63** |
| 8 | **F8** xsi:type resolution + timestamp deny-list hardening | FIDELITY/hardening | Cheap defense-in-depth on parse + private update primitive | **#113 + #65** |

---

## 8. Bottom line

vosfs's **vocabulary fidelity is strong**: protocol IDs, transfer directions,
security-method IDs, capability standard-IDs, node-type mapping, and XML
namespace/version all match IVOA 2.1 exactly for the operations it implements
(§2.3-2.7). The exclusions are genuinely conscious and mostly reinforced by
OpenCADC's own limits (§4).

The **single most important correctness gap, F1, is resolved**: the TRD and
implementation now classify LinkNode move as unsupported and raise
`NotImplementedError` after source resolution but before mutation. Copying an
internal LinkNode whose target has the discovered VOSpace authority materializes
its target bytes as a DataNode; copying an external or non-VOS LinkNode raises
`NotImplementedError` before destination mutation. LinkNode creation and
link-preserving move remain outside the current profile. Everything else is
fidelity polish (F2-F4, F8), exclusion documentation (F5), or roadmap (F6-F7-F10).
