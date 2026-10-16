# vosfs transfer-endpoint variability: graceful degradation vs. direct-`/files` fallback

Researched: 2026-07-18 (America/Vancouver)

Status: **Informative evidence** for Phase 3 of epic #205. The approved `vosfs` capability contract in [`../design/trd.md`](../design/trd.md) remains normative and controls if this note differs.

Research for **Phase 3 of epic #205**. Primary-source research + analysis only; no
repo files modified. This note is *informative evidence* for a possible TRD
change; `docs/design/trd.md` remains the sole normative contract.

**Question.** How should `vosfs` handle VOSpace deployments that vary in
transfer-endpoint availability, and should it add a direct-`/files` fallback?

- **Option A** — graceful degradation only: when no usable sync-transfer binding
  is advertised, disable byte I/O with an actionable `NotImplementedError` (vosfs
  already does this on a missing binding); add hermetic tests for the "no transfer
  binding" deployment.
- **Option B** — also design a capability-gated **direct-`/files`** transfer mode
  for deployments that advertise a direct file binding but not `/synctrans`.

**Bottom line up front.** Direct `/files` construction is **technically feasible**
against Cavern for a *credentialed* caller (the pre-auth token is optional; a
tokenless request falls back to normal node authorization) — so Option B is *not*
impossible, contradicting a naive "the pre-auth model forbids it" reading.
**However**, the "deployment that advertises `/files` but not `/synctrans`" case
has **zero first-party evidence** of existing; Option B is a capability-contract
change (new minor version) that reintroduces credential-routing risk vosfs
deliberately removed, cannot cover the anonymous-private case, and builds on a
`-proto` (prototype) capability ID. **Recommendation: Option A now; Option B
deferred to roadmap, gated on real evidence + a new capability contract.**

Sources fetched successfully: IVOA REC (broad + targeted re-fetch), Cavern
`web.xml`, `FileAction.java`, `CavernURLGenerator.java`, `GetAction.java`,
`capabilities.xml`, plus a registry/deployment web search.

---

## 1. The IVOA VOSpace 2.1 transfer / byte-access model (cited)

Source: [IVOA VOSpace 2.1 Recommendation, 2018-06-20](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html)
(fetched broad, then re-fetched focused on §4 bindings and §6 operations).

### 1.1 Byte access is obtained ONLY through transfer negotiation

The spec defines **no client-constructible direct byte URL**. A client obtains a
byte endpoint by POSTing a transfer document and reading the endpoints the service
fills in:

- §1.2 (Typical use): the client HTTP POSTs "a XML description of this transfer
  request"; the service "will reply with a redirect to a location with an amended
  version of the transfer representation that contains … URL endpoints that the
  user may HTTP PUT the data file to."
- §3.6.4 (Client-initiated transfers): "the service selects the Protocols from the
  request that it is capable of handling, and builds a Transfer results response
  containing the selected Protocol elements **filling in valid endpoint addresses
  for each** of them."
- §3.6.1: "Each protocol on the result **must contain an endpoint**." A transfer
  MAY return the same protocol URI with **different endpoints**.

**The endpoint lives only inside a transfer response.** The spec provides no
pattern for a client to construct one.

### 1.2 Endpoints are ephemeral / single-use by design

§3.6.3 (Service-initiated transfers): "The server **SHALL be allowed to only use
each Protocol option once.** This allows a data source to issue one time URLs for
a Transfer, and cancel each URL once it has been used." The only stable,
reusable, anonymous URL in the model is the **optional** public-share protocol
(§3.5.4, "Implementation of this protocol is optional"; "The endpoint MUST be
anonymously accessible").

### 1.3 Mandatory vs. optional — the spec does NOT hard-mandate any capability

The targeted re-fetch is explicit: the REC **does not use RFC-2119 MUST/SHALL to
require** that a conformant service implement each capability. It *defines the
REST bindings* (§4) and lets services "implement capabilities suitable for their
architecture rather than a rigid mandatory checklist." Findings per capability:

| Capability | Standard ID (§4) | Spec status |
| --- | --- | --- |
| nodes | `ivo://ivoa.net/std/VOSpace/v2.0#nodes` | Binding defined; no explicit universal MUST |
| async transfers | `ivo://ivoa.net/std/VOSpace/v2.0#transfers` | Binding defined; "contingent on service design" |
| **sync transfer** | `ivo://ivoa.net/std/VOSpace#sync-2.1` | **"introduced" in 2.1; not mandated as required**. §3.6.2 URL-parameter form is "optional" |
| protocols / views / properties | `…#protocols` / `#views` / `#properties` | Service-metadata ops (§6.1); no blanket MUST |

There is **no defined "minimal conformant service"** in the fetched text (Appendix
B "Compliance matrix" exists but is out of the excerpt). The load-bearing point:
**a VOSpace service may legitimately omit synchronous transfer (`sync-2.1`)** — it
is an optional 2.1 addition, not a required capability. So the premise of the
question ("no usable sync-transfer binding") is *spec-legal*, even if not observed
in practice (§3 below).

> Note on evidence hygiene: the first (broad) sub-model fetch asserted several
> capabilities were "mandatory." The targeted re-fetch contradicts that — the
> spec only *defines* bindings. This matches vosfs's own IVOA evaluation, which
> treats `/protocols`, `/views`, `/properties` as optional/unimplemented in the
> OpenCADC profile (`docs/research/vosfs-ivoa-2.1-evaluation.md` §4). I trust the
> targeted re-fetch (and the corroborating repo evidence) over the broad one.

**Consequence for the design.** Option A (negotiate-or-disable) is the
spec-faithful posture: byte access is a negotiated capability, and its absence is
a legitimate service configuration to degrade against — not an error to work
around by constructing URLs the spec never defined.

---

## 2. Is direct `/files` construction feasible for OpenCADC? (decisive)

**Decisive finding: YES, technically feasible for a credentialed caller — the
pre-auth token is OPTIONAL, not required.** This is the honest answer and it
refutes the simplistic "OpenCADC's pre-auth model makes direct `/files`
impossible." Evidence, at pinned commit
[`cf976ce8`](https://github.com/opencadc/vos/tree/cf976ce8141dd3341631b7f3e07aa38443d42f58):

### 2.1 `/files` is a real, wired, advertised binding

- **Wiring** — [`web.xml`](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/WEB-INF/web.xml):
  `/files/*` → `ca.nrc.cadc.rest.RestServlet`, dispatching
  `org.opencadc.cavern.files.GetAction` (GET/HEAD) and `PutAction` (PUT). The
  files servlet carries **no init-param** for tokens/URL-generation/pre-auth.
- **Advertised** — [`capabilities.xml`](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/capabilities.xml):
  the direct-files capability standard ID is **`ivo://ivoa.net/std/VOSpace#files-proto`**
  (a **`-proto`/prototype** ID; confirmed as a deliberate `-proto` constant in
  [`VOS.java`](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos/src/main/java/org/opencadc/vospace/VOS.java#L126-L130)),
  with `use="base"` access URL and security methods **anonymous + cookie + TLS
  cert + token** — the same set as `/nodes`, `/synctrans`, `/transfers`.

### 2.2 The pre-auth token on `/files` is OPTIONAL, with fallback to node authz

- [`FileAction.parsePath()`](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/FileAction.java)
  only consumes a token *if the first path segment starts with `preauth:`*
  (`/files/preauth:TOKEN/path`); otherwise it parses `/files/<path>` normally and
  `initAction()` runs `checkReadable()` / `checkWritable()` against the standard
  `VOSpaceAuthorizer`.
- [`GetAction`](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/GetAction.java)
  authorizes a tokenless request via
  `authorizer.hasSingleNodeReadPermission(node, caller)` using the current
  authenticated subject — i.e. a **plain authenticated GET to `/files/<path>`
  (no token) succeeds** (and a public node is anonymously readable).
- [`CavernURLGenerator`](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/CavernURLGenerator.java)
  builds the *negotiated* URL as `baseURL/preauth:TOKEN/path` (token =
  `generateToken(resourceURI, ReadGrant|WriteGrant, callingUser)`, validated on
  return via `validateToken`); but for the plain case it emits `baseURL/path`.

### 2.3 The server itself already redirects to a constructed-looking `/files` URL

`GET /nodes/<path>?view=data` returns a concrete **303 to the sibling
`/files/<path>`** (per `docs/research/opencadc-vos-supported-api.md` line 107,
citing `GetNodeAction`). So the canonical byte endpoint for an authorized caller
*is* `/files/<path>` — the server points clients there directly, no per-transfer
negotiation body required.

### 2.4 Therefore, precisely stated

For the **Cavern POSIX profile**, an authenticated caller (or an anonymous caller
on a public node) **can** construct `{files_base}/{percent-encoded-path}` and get
working HEAD/GET/PUT. Feasibility is real. But feasibility ≠ advisability — the
caveats in §4 are what actually decide the question.

---

## 3. Prevalence: how real is the "no transfer binding" case? (honest gaps)

**Assessment: the specific case Option B targets — a deployment that advertises a
direct `/files` binding but NOT `/synctrans` — has NO first-party evidence of
existing. It is a hypothetical, not an observed deployment.**

- **CADC/CANFAR (Cavern)** advertises **all** of `nodes`, `sync-2.1`,
  `transfers`, and `files-proto` together in the shipped
  [`capabilities.xml`](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/capabilities.xml)
  template, and the staging deployment advertises `/transfers` and `/synctrans`
  (`docs/research/opencadc-vos-supported-api.md` lines 54-57, 65). CANFAR is the
  concrete "VOSpace 2.1 server declared in the VO registry" the search surfaced
  ([CANFAR VOSpace docs](https://www.opencadc.org/canfar/latest/platform/storage/vospace/),
  [Cloud access to interoperable IVOA-compliant VOSpace storage, arXiv:1806.04986](https://arxiv.org/abs/1806.04986)).
- **No deployment inventory** I could reach ([opencadc/deployments](https://github.com/opencadc/deployments)
  Helm charts, the IVOA registry references) shows a Cavern configured to disable
  `/synctrans` while keeping `/files`. The Cavern template couples them.
- **Direction of the realistic risk is the inverse.** Because `sync-2.1` is an
  *optional* 2.1 addition (§1.3) and `/files` is a `-proto` extension, the
  plausible degradation is a *metadata-oriented or older* VOSpace that has
  `/nodes` but **neither** a usable `/synctrans` **nor** `/files-proto` — not one
  that keeps `/files` alone. Option B does nothing for that case.
- **The more probable "no usable sync-transfer" scenario is credential mismatch,
  not a missing endpoint:** `/synctrans` is advertised but only with security
  methods the caller can't satisfy (e.g. cookie-only), so negotiation legitimately
  fails. vosfs already handles this by failing negotiation before byte I/O
  (`trd.md` §7, "when no returned endpoint matches the configured credential
  source, the transfer fails before byte I/O").

**Honest gaps.** (1) I did not enumerate every VOSpace server in the IVOA registry;
there may be non-OpenCADC 2.1 servers with unusual capability sets, but I found no
first-party evidence of one that has `/files` without `/synctrans`. (2) A site
*could* hand-edit its capabilities to expose `/files-proto` only — nothing forbids
it — but that is a possibility, not a documented reality. Building a capability
mode for an unobserved configuration is speculative (YAGNI).

---

## 4. Option B design + contract implications, and why they cut against it

Feasible (§2) but costly and risky. The specific problems:

### 4.1 It is a capability-contract change requiring a new minor version

Option B contradicts three normative TRD clauses:

- **§5:** "The client **MUST NOT** construct a `/files` URL directly."
- **§7:** every byte read/write **MUST** perform `/synctrans` negotiation, with
  **credential routing determined by the selected negotiated security method**.
- **§16:** "direct construction of `/files` URLs" is explicitly out of scope.

Per **§17**, "a capability or constructor contract change requires a new minor
version." So Option B cannot ship as a patch; it is a deliberate v0.4.x-class
contract expansion with its own acceptance gates.

### 4.2 It reintroduces the credential-routing risk vosfs designed out

Today the *negotiated security method* tells vosfs exactly which credential (if
any) an endpoint should receive, and pre-authorized endpoints get **no** caller
credential (`trd.md` §7; `opencadc-vos-supported-api.md` lines 130-135, 157). A
direct-`/files` mode forces vosfs to *guess* credential routing from the `/files`
capability's advertised security methods against a self-constructed URL — exactly
the "match protocol/origin and hope" pattern the fsspec tribal-knowledge corpus
flags as a recurring leak source (`fsspec-backend-tribal-knowledge.md` lesson 16:
redirect/pre-signed credential forwarding leaks; lesson 10: "range support is a
capability, not a header guess" — the same principle applies to byte-endpoint
construction). Negotiation is the mechanism that *removes* the guess.

### 4.3 It cannot cover the anonymous-private case, and probing is unreliable

- Anonymous access to a **private** node requires the **preauth token**, and the
  token is only mintable by the service during negotiation
  (`CavernURLGenerator.generateToken`). Direct construction cannot produce it —
  so Option B structurally cannot serve the very case where negotiation is most
  valuable.
- **HEAD/GET asymmetry:** Cavern HEAD may return metadata when the *parent* is
  public even though GET of the private node 403s (`opencadc-vos-supported-api.md`
  lines 158-161). A direct-`/files` client that probes with HEAD can be misled.

### 4.4 It bakes in a Cavern-POSIX assumption that doesn't generalize

Direct `/files` streaming works because Cavern serves bytes from its own POSIX
filesystem (`GetAction`). The negotiation + preauth indirection exists precisely
so a deployment can serve bytes from a **different host** (cloud/object storage,
signed URLs, one-time endpoints per §1.2/§3.6.3). Constructing `{files_base}/path`
assumes same-host, same-backend byte serving — a Cavern-filesystem-specific
shortcut. The tribal-knowledge corpus is emphatic that "compatible service does
not prove compatible behavior" and that vosfs should "name and test the exact
OpenCADC profile, not generic" (lesson 18). Option B trades that discipline for a
shortcut whose only payoff is a phantom deployment.

### 4.5 It rests on a `-proto` capability

`files-proto` is explicitly a prototype/extension ID. Anchoring a published
capability contract on a `-proto` binding invites breakage when the ID or its
semantics change — a poor foundation for a MUST-level contract clause.

### 4.6 What Option B would actually cost vs. buy

- **Cost:** resolve a third (`files-proto`) binding; a new credential-routing code
  path + security model for self-constructed URLs; redirect/leak hardening; a full
  hermetic test matrix (anon-public, auth, private-denied, HEAD/GET asymmetry,
  cross-origin credential rules); a new minor-version contract + live gate; ongoing
  maintenance of a second byte path.
- **Buy:** byte I/O for a deployment class with **no observed instances**, and even
  then only for the authenticated / public-anonymous subset (not anon-private).

Classic negative-ROI / YAGNI.

---

## 5. Option A design + contract implications (cheap, spec-faithful, complete)

Option A is *already most of the way there* and is spec-faithful (§1):

- vosfs already "**MUST NOT** guess a missing operation URL. A missing binding
  disables only its dependent operation and raises an actionable
  `NotImplementedError`" (`trd.md` §5). An absent/unusable `/synctrans` binding
  therefore already disables byte reads/writes with `NotImplementedError`, while
  node metadata/listing/create/delete (which need only the `/nodes` binding) keep
  working — genuine *graceful degradation*, not all-or-nothing failure.
- **Work to do (small):**
  1. Make the disabled-byte-I/O error **actionable**: name the missing capability
     (`ivo://ivoa.net/std/VOSpace#sync-2.1`), the `endpoint_url`, and the fact
     that the deployment advertises no usable synchronous-transfer binding for the
     configured credential's security method.
  2. Add **hermetic tests** for the "no transfer binding" deployment: a
     capabilities fixture with `/nodes` present and `/synctrans` absent (and a
     variant where `/synctrans` is present but advertises only unusable security
     methods) → assert metadata ops succeed and every byte op raises
     `NotImplementedError` (or the credential-mismatch failure) **before any
     network byte I/O**, with the actionable message.
  3. Document, in the supported-profile section, that byte access is a *negotiated
     capability* and its absence is a supported-degradation mode, not a bug.
- **Contract impact:** none beyond wording — this *confirms and tightens* the
  existing §5 behavior; it can ship in a patch (§17: "an implementation fix that
  restores this document's behavior may ship in a patch release").

---

## 6. RECOMMENDATION

**Adopt Option A now. Explicitly defer Option B to the roadmap, gated on (a)
first-party evidence of a real deployment that advertises a direct file binding
but no usable `/synctrans`, and (b) a resolved credential-routing/security design,
shipped only via a new published capability contract (new minor version).**

Rationale, in priority order:

1. **Spec-faithful.** IVOA 2.1 makes byte access a *negotiated* capability with no
   client-constructible URL and no mandated sync transfer (§1). Degrading when the
   negotiated capability is absent is the model's intended behavior; constructing
   `/files` URLs is outside the model.
2. **Prevalence is unproven.** The exact deployment Option B targets has zero
   first-party evidence (§3). The realistic degradations are "neither sync nor
   files" (Option B doesn't help) or "sync advertised but credentials unusable"
   (already handled). Building for a phantom config is YAGNI.
3. **Feasible ≠ worth it.** Direct `/files` *is* constructible against Cavern for
   credentialed callers (§2) — I do not overclaim impossibility — but Option B
   reintroduces credential-routing/leak risk vosfs deliberately removed, cannot
   cover anon-private, relies on a `-proto` ID, and bakes in a Cavern-POSIX
   assumption (§4). Its cost/benefit is strongly negative.
4. **Option A is cheap and complete.** It confirms and tightens existing §5
   behavior, needs only an actionable error + hermetic tests, and ships in a patch
   (§5).

**Security considerations (carry into whichever path):** keep credential routing
keyed to the *negotiated* security method; never send bearer/X.509/cookie to a
pre-authorized or anonymous endpoint; never let successful HEAD imply readable
bytes; redact preauth tokens from URLs/logs/fixtures. These are exactly the
invariants Option B would put under pressure and Option A leaves intact.

**If Option B is ever revisited**, scope it minimally as an *extension-conditional*
capability (per the TRD's own vocabulary, §2): resolve the `files-proto` binding,
gate the mode strictly on its advertised security methods, forbid it for
anonymous-private, forbid cross-origin credential leakage, and prove it against a
real deployment in the live gate — never enable it by constructing URLs the
capabilities document does not advertise.

---

## Appendix. Primary sources

- IVOA VOSpace 2.1 REC (§1.2, §3.5.4, §3.6/§3.6.1-3.6.4, §4, §6.1, §6.4):
  <https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html>
- Cavern `web.xml` (`/files`, `/synctrans`, `/transfers` wiring):
  <https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/WEB-INF/web.xml>
- Cavern `FileAction.java` (optional `preauth:` token; fallback to `VOSpaceAuthorizer`):
  <https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/FileAction.java>
- Cavern `GetAction.java` (`hasSingleNodeReadPermission`; tokenless auth GET works):
  <https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/GetAction.java>
- Cavern `CavernURLGenerator.java` (preauth token construction/validation):
  <https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/CavernURLGenerator.java>
- Cavern `capabilities.xml` (`files-proto`, `sync-2.1`, `transfers`, `nodes` + security methods):
  <https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/capabilities.xml>
- Cavern `VOS.java` (`-proto` standard-ID constants):
  <https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos/src/main/java/org/opencadc/vospace/VOS.java#L116-L130>
- CANFAR VOSpace (deployment evidence): <https://www.opencadc.org/canfar/latest/platform/storage/vospace/>
  and arXiv:1806.04986 <https://arxiv.org/abs/1806.04986>; deployments repo
  <https://github.com/opencadc/deployments>
- Repo context (informative): `docs/design/trd.md` §5/§7/§16/§17;
  `docs/research/opencadc-vos-supported-api.md` (lines 67, 107, 124-135, 158-161);
  `docs/research/vosfs-ivoa-2.1-evaluation.md` §4;
  `docs/research/fsspec-backend-tribal-knowledge.md` lessons 10, 16, 18.
