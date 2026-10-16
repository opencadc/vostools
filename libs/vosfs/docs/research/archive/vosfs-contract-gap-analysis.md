# `vosfs` contract gap analysis: primary-source research

<!-- pyml disable line-length -->

Researched: 2026-07-10
Audit target: `docs/design/trd.md` v0.2 as read on 2026-07-10
Scope: contract research plus the approved planning target; this note does not change the TRD.
Wayfinder map: [Define the vosfs v0.3.0 capability contract](https://github.com/shinybrar/vosfs/issues/29)

Disposition: **Historical input, superseded by the v0.3.0 capability
contract.** The gaps and questions below describe the earlier v0.2 draft and
must not be read as unresolved v0.3.0 requirements. The final decisions are in
`docs/design/trd.md` and the focused v0.3.0 research notes.

## Approved v0.3.0 target

After review, v0.3.0 is scoped to compatibility with the CANFAR staging Cavern service, with broader VOSpace 2.1 and maximum-fsspec compatibility retained as non-normative roadmap material. HTTPX is selected as the core HTTP client. The caller supplies the service base URL and authentication configuration rather than `vosfs` acquiring credentials.

A public read-only snapshot on 2026-07-10 confirmed that the staging service publishes a [Cavern Swagger description](https://staging.canfar.net/arc/service.yaml), [VOSI capabilities](https://staging.canfar.net/arc/capabilities), and a public [root node](https://staging.canfar.net/arc/nodes) with authority `cadc.nrc.ca~arc`. It advertises node, transfer, synchronous-transfer, direct-file, recursive-delete, recursive-property, and packaging bindings. The advertised `/protocols`, `/views`, and `/properties` resources returned `404`; those gaps are part of the v0.3.0 interoperability target, not facts to reinterpret as standard-conforming behavior.

## Executive conclusion

The TRD has the right architectural centre—an async fsspec backend with negotiated VOSpace transfers—but it is not yet safe to publish as the user capability contract. Several normative statements contradict VOSpace 2.1 or current fsspec, and several claims of scientific-stack compatibility are not backed by acceptance criteria.

The release should be held behind three corrections:

1. Separate **VOSpace conformance** from **interop fallbacks for non-conforming deployments**. `copyNode` and recursive container deletion are mandatory VOSpace behaviour; a client may still offer fallbacks, but must not describe those operations as optional parts of the Recommendation. ([VOSpace 2.1 §§6.2.3, 6.2.4 and Appendix B](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html))
2. Define a byte/view, URI, and transfer-job state machine. VOSpace authorities are logical registry identifiers, StructuredDataNode exports may transform bytes, and asynchronous transfer negotiation requires job creation, `PHASE=RUN`, result discovery, byte movement, and terminal/error handling. ([VOSpace 2.1 §§2, 3.4, 3.6 and 6.4](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html))
3. Turn “maximum fsspec compatibility” into a versioned compatibility matrix and executable gates. The current TRD misses current fsspec hook semantics, the packaged fsspec backend test harness, JSON reconstruction, half-open ranges, write modes, cache/lifecycle behaviour, and several downstream-specific operations. ([current `AsyncFileSystem`](https://github.com/fsspec/filesystem_spec/blob/0907962e6b94eea3b4ba86a6cc20b36ddb29f2dd/fsspec/asyn.py#L300-L378), [current fsspec abstract tests](https://github.com/fsspec/filesystem_spec/tree/0907962e6b94eea3b4ba86a6cc20b36ddb29f2dd/fsspec/tests/abstract))

For the HTTP layer, **HTTPX is the lower-risk 0.1 choice**, provided the implementation uses an internal transport seam and a pool of clients keyed by TLS configuration rather than one global client. Niquests remains viable, especially if per-request mTLS proves decisive, but its default redirect behaviour and dual buffered/streaming response model add hazards to this particular protocol. The choice should be locked by a focused spike described below, not by a generic feature comparison.

## P0: normative corrections required before publication

### 1. Third-party transfer directions are server-initiated, not client relays

TRD R3.12 and R10.4 say that VOSpace 2.1 made `pullToVoSpace` and `pushFromVoSpace` client-orchestrated and therefore requires a client relay. That is not what the Recommendation specifies. It explicitly classifies those two directions as **service-initiated**: the client supplies endpoint URLs and the VOSpace service performs the transfer. Both operations are optional. Conversely, `pushToVoSpace` and `pullFromVoSpace` are the client/agent-initiated directions. ([VOSpace 2.1 §§3.6.3–3.6.4](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html), [§§6.4.2 and 6.4.4](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html))

Required TRD change:

- Present client relay as the portable fsspec fallback or a deliberate 0.1 product scope choice.
- Preserve optional server-to-server transfer as a discoverable VOSpace capability; do not say the spec forbids relying on it.
- State whether cross-service `cp`/`mv` will attempt the optional service-initiated path before relaying.

### 2. `copyNode` and recursive delete are mandatory server behaviour

TRD R3.14 calls `copyNode` specified-but-optional. VOSpace Appendix B lists `copyNode`, `moveNode`, and `deleteNode` in the mandatory operation set; only `pushToVoSpace`, `pullToVoSpace`, and `pushFromVoSpace` are optional. The `deleteNode` contract also says deleting a ContainerNode deletes its children. A deployment returning `405` for `copyNode` or refusing recursive container deletion is non-conforming, even when `vosfs` can recover with a relay or leaves-first deletion. ([VOSpace 2.1 §§6.2.2–6.2.4 and Appendix B items 39, 44](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html))

Required TRD change:

- Keep both fallbacks, but label them **non-conforming-deployment interop fallbacks**.
- Report conformance separately from operational success, for example `capabilities.conformance_violations` or diagnostic logging.
- Narrow R4.4: an absent optional capability is not an error; an absent mandatory VOSpace operation is a conformance error even if a fallback lets the fsspec call complete.

### 3. The capabilities model conflates three different mechanisms

The Recommendation defines:

- node `capabilities` as third-party interfaces attached to a node;
- mandatory service metadata resources at `/protocols`, `/views`, and `/properties`; and
- registry standard/resource identifiers for the REST bindings, including nodes, transfers, and synchronous transfer.

The TRD instead makes a VOSI `capabilities` document the single source for nodes, transfers, direct files, recursive delete, packaging, and security methods, without defining how its URL is discovered while registry resolution is out of scope. VOSpace 2.1 itself does not define a mandatory `GET /capabilities` binding. ([VOSpace 2.1 §§3.3, 4 and 6.1](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html))

Required TRD change:

- Define a bootstrap contract: either explicit binding URLs, a supplied VOSI-capabilities URL, or a documented derivation/probe policy from `endpoint_url`.
- Require `/protocols`, `/views`, and `/properties` handling independently of VOSI.
- Keep node capabilities, standard REST-binding discovery, and vendor extensions in separate typed collections with provenance (`standard`, `advertised extension`, `probe`, `override`).
- Treat security methods advertised for a service interface separately from the `securityMethod` values in transfer protocol requests/responses.

### 4. The URL grammar conflicts with the VOS URI contract

R5.9 says the entire text after `vos://` is a path. In VOSpace, that first component is a naming authority derived from the service's IVO registry identifier; it makes a node identifier globally unique and is used to resolve the corresponding service. The authority is not the HTTP host and is not a path segment. The Recommendation also permits `!` and `~` spellings for an authority's resource-key separators and requires a service to accept either. ([VOSpace 2.1 §2 and §2.1](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html))

`endpoint_url` can still select the HTTP service, as intended, but the filesystem needs a rule for the logical VOS authority:

- parse and validate the authority in `vos://authority/path`;
- strip it before producing an fsspec path;
- reject a URI whose authority does not identify the configured space, unless multi-space operation is explicitly supported;
- allow unqualified fsspec paths only when a canonical authority was supplied or learned; and
- define root, percent encoding, `!`/`~` equivalence, query propagation, and fragment removal.

The safest constructor contract is `endpoint_url` plus an optional explicit `authority`; reading the root node can fill a missing authority, but it must not be the only way to construct a valid create request.

### 5. XML requirements are both too strong and incomplete

R3.6 says a Node requires a full element sequence even when empty. The normative XSD marks `properties`, `accepts`, `provides`, and `capabilities` optional; ContainerNode alone requires `nodes`, and LinkNode requires `target`. The schema uses the **v2.0 namespace with `version="2.1"`** for 2.1 compatibility. The TRD does not lock that namespace/version rule or `Content-Type: text/xml`. ([VOSpace 2.1 Appendix A XSD and §4](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html), [§6 preamble](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html))

Required TRD change:

- Replace “full sequence” with the exact per-type XSD contract and distinguish minimal requests from full/max-detail responses.
- Require namespace `http://www.ivoa.net/xml/VOSpace/v2.0` plus `version="2.1"` on 2.1 documents.
- Require schema-valid generated XML in tests and safe, bounded parsing at runtime.
- Resolve the dependency contradiction: `defusedxml` protects parsing but does not perform XSD validation. Either validate generated fixtures in CI with an XSD-capable tool, or add a runtime validator; do not claim runtime XSD validation with `defusedxml` alone. ([ElementTree security warning](https://docs.python.org/3/library/xml.etree.elementtree.html), [lxml XML Schema validation](https://lxml.de/validation.html#xmlschema))

### 6. Views are part of the byte contract

The TRD negotiates protocols but never specifies the View. That is unsafe for a filesystem abstraction. An UnstructuredDataNode must return the original bit pattern, but a StructuredDataNode may return a transformed representation. `binaryview` import and `defaultview` export are optional, and `defaultview` lets the service choose a representation. A View's `original` flag is the protocol's signal that the bit pattern is preserved. ([VOSpace 2.1 §§3.1 and 3.4](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html))

Required TRD change:

- Define view selection for reads and writes.
- Define whether random-access fsspec reads are available only for an `original=true` view whose size/ranges describe that same representation.
- Define behaviour when no byte-stable view is available: sequential read only, `type="other"`, or a clear unsupported-operation error.
- Bind `size`, checksum, cache identity, and `content_type` to the selected representation, not blindly to node properties.

### 7. LinkNode policy exceeds both the spec and declared scope

VOSpace requires all URI ancestors to resolve as ContainerNodes, and create/delete explicitly return `LinkFound` when a parent is a LinkNode. A LinkNode target may be another VOSpace or an arbitrary external URI. Therefore the TRD note that servers resolve links used as path components is not portable, while mandatory default-following can require registry resolution or unsafe arbitrary-URI dereferencing—both beyond the current non-goals. ([VOSpace 2.1 §§2, 3.1, 6.2.1 and 6.2.4](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html))

Required decision:

- Choose one safe portable baseline, preferably `type="other"`, `islink=True`, and no external dereference by default.
- If local same-space following is offered, make its boundary explicit and opt-in/controlled; cross-space resolution needs a resolver supplied by a higher layer.
- Add `LinkFound` to error translation. The core fault list also includes `InternalFault`, `InvalidToken`, `InvalidData`, `NodeBusy`, `ProtocolNotSupported`, `TransferFailed`, and `OperationNotSupported`; extension-only faults must be identified as such rather than presented as core. ([VOSpace 2.1 §§6.4–6.5](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html))

### 8. Permissions are an extension, not a portable VOSpace API

R12.2 says “VOSpace permissions are group ACLs” and defines a portable `set_permissions`. The Recommendation explicitly says that no operation for modifying access policy is included. It does define standard metadata property identifiers such as `groupread`, `groupwrite`, and `publicread`, but property acceptance, validation, and access-control effects remain service policy. ([VOSpace 2.1 §§3.2.4 and 5](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html))

Required TRD change:

- Move `set_permissions` to a typed extension/adaptor contract.
- Do not infer semantics solely from an unknown property's URI or returned value; require an advertised/configured mapping and encoding.
- Correct the standard-property examples: `length`, `date`, `groupread`, `groupwrite`, and `publicread` are in Appendix C, while `MD5` and `contentType` are not part of that core table. Preserve them as vendor properties where encountered. ([VOSpace 2.1 Appendix C](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html))

## P0/P1: current fsspec contract gaps

### Half-open ranges and capability detection

fsspec range APIs use Python half-open intervals: `start=2, end=7` means five bytes, and HTTP's inclusive header must therefore be `Range: bytes=2-6`. S3FS sends `end - 1`; ADLFS tests the five-byte result directly. TRD R7.3 currently says `bytes=start-end`, an off-by-one corruption/over-fetch bug. ([S3FS range source](https://github.com/fsspec/s3fs/blob/b7ec8db5170d8a9afeeadc9efb7b4292a335bf6b/s3fs/core.py#L2868-L2881), [ADLFS range tests](https://github.com/fsspec/adlfs/blob/e6c5cb459fb982455e7a903e58ebd3afbf057711/adlfs/tests/test_fetch_range.py#L14-L48))

`Accept-Ranges: bytes` is advisory. RFC 9110 says a client may try a Range without it and must not assume a future partial response merely because it received it. Capability detection therefore needs a real `GET` probe (for example `bytes=0-0`) and validation of `206`, `Content-Range`, and body length; absence of the HEAD header is not “unsupported”. ([RFC 9110 §§14.2–14.4](https://www.rfc-editor.org/rfc/rfc9110.html#section-14.2))

The contract must also cover `start=None`, `end=None`, negative `start` for suffix reads (used by current Zarr), empty ranges, zero-length objects, `416`, a server ignoring Range with `200`, and representation changes between `info()` and later ranges. Current Zarr calls `_cat_file` for normal/offset/suffix ranges and `_cat_ranges` for batches. ([Zarr `FsspecStore.get`](https://github.com/zarr-developers/zarr-python/blob/31817c681dc747d4d723af072e9562c624def553/src/zarr/storage/_fsspec.py#L330-L360), [`get_partial_values`](https://github.com/zarr-developers/zarr-python/blob/31817c681dc747d4d723af072e9562c624def553/src/zarr/storage/_fsspec.py#L421-L460))

### Required hook corrections

- Implement `_mv_file`, not only `_mv`. Current `AsyncFileSystem._mv_file` falls back to copy then remove, while `_mv` is the bulk coordinator. Server-side VOSpace move belongs in the per-file hook. ([fsspec source](https://github.com/fsspec/filesystem_spec/blob/0907962e6b94eea3b4ba86a6cc20b36ddb29f2dd/fsspec/asyn.py#L359-L410))
- Preserve `_pipe_file(mode="create")`: current fsspec's reusable tests require `FileExistsError` when the target exists and overwrite semantics otherwise. ([abstract pipe test](https://github.com/fsspec/filesystem_spec/blob/0907962e6b94eea3b4ba86a6cc20b36ddb29f2dd/fsspec/tests/abstract/pipe.py))
- A custom read-file subclass is not mandatory merely to get seekable reads: the base `_open` returns `AbstractBufferedFile`, whose `_fetch_range` delegates to `fs.cat_file`. A custom file class is needed when write/finalization or specialised caching semantics require it. ([fsspec `_open`](https://github.com/fsspec/filesystem_spec/blob/0907962e6b94eea3b4ba86a6cc20b36ddb29f2dd/fsspec/spec.py#L1280-L1301), [`AbstractBufferedFile`](https://github.com/fsspec/filesystem_spec/blob/0907962e6b94eea3b4ba86a6cc20b36ddb29f2dd/fsspec/spec.py#L1849-L1915))
- `_cat_ranges` already has a bounded-concurrency default over `_cat_file`; require its semantics and tests rather than requiring a redundant override. Override only for a measured optimisation such as merged ranges. ([fsspec source](https://github.com/fsspec/filesystem_spec/blob/0907962e6b94eea3b4ba86a6cc20b36ddb29f2dd/fsspec/asyn.py#L512-L559))
- Retain the write warning: base `AbstractBufferedFile._initiate_upload` and `_upload_chunk` do no work, so an inherited buffered writer can discard data. ([fsspec source](https://github.com/fsspec/filesystem_spec/blob/0907962e6b94eea3b4ba86a6cc20b36ddb29f2dd/fsspec/spec.py#L2040-L2110))

### Serialization and lifecycle are broader than pickle

The TRD correctly identifies `AbstractFileSystem.__reduce__`, but current fsspec also exposes `to_json()`/`from_json()`, and current Zarr reconstructs a native async filesystem through JSON when converting a synchronous instance. A module-level callable may be pickleable but is not accepted by fsspec's default JSON encoder. Use an import-path string or another primitive descriptor for credential providers if Zarr/fsspec JSON reconstruction is part of the compatibility promise. ([fsspec reduction and cache source](https://github.com/fsspec/filesystem_spec/blob/0907962e6b94eea3b4ba86a6cc20b36ddb29f2dd/fsspec/spec.py#L35-L193), [fsspec JSON encoder](https://github.com/fsspec/filesystem_spec/blob/0907962e6b94eea3b4ba86a6cc20b36ddb29f2dd/fsspec/json.py#L1-L40), [Zarr async conversion](https://github.com/zarr-developers/zarr-python/blob/31817c681dc747d4d723af072e9562c624def553/src/zarr/storage/_fsspec.py#L60-L90))

`weakref.finalize` is not an adequate lifecycle contract by itself: fsspec's default instance cache holds a strong reference until explicitly cleared, and fsspec's async guide recommends explicitly awaiting resource destruction. Require idempotent `aclose()`, a sync close bridge for synchronous instances, ownership rules, and tests for both `asynchronous=True` and generated-sync lifecycles. A finalizer should be only a best-effort safety net. ([fsspec instance cache](https://github.com/fsspec/filesystem_spec/blob/0907962e6b94eea3b4ba86a6cc20b36ddb29f2dd/fsspec/spec.py#L35-L102), [fsspec async guide](https://github.com/fsspec/filesystem_spec/blob/0907962e6b94eea3b4ba86a6cc20b36ddb29f2dd/docs/source/async.rst), [S3FS lazy-session pattern](https://github.com/fsspec/s3fs/blob/b7ec8db5170d8a9afeeadc9efb7b4292a335bf6b/s3fs/core.py#L576-L713))

### Cache and consistency contract

`cachable=True` and directory caching make invalidation observable behaviour. Every successful or partially successful create, write, delete, copy, move, property update, and recursive fallback must invalidate the target and all affected parents; failures must not leave positive/negative entries claiming success. The TRD mentions `invalidate_cache(path)` but not mutation rules, listing expiry, refresh semantics, or concurrent mutation. Reference async backends explicitly invalidate directory caches around mutations. ([GCSFS source](https://github.com/fsspec/gcsfs/blob/89f469da36cf4f993d27055adbc60de99565f42c/gcsfs/core.py), [ADLFS invalidation](https://github.com/fsspec/adlfs/blob/e6c5cb459fb982455e7a903e58ebd3afbf057711/adlfs/spec.py#L1898-L1908))

### Use fsspec's released backend test harness

R15 defines VOSpace wire tests but omits fsspec's reusable backend suite. Current fsspec ships abstract copy/get/open/pipe/put tests specifically for implementations. `vosfs` should derive endpoint-backed fixtures from these tests and version-pin the tested contract. ([fsspec abstract tests](https://github.com/fsspec/filesystem_spec/tree/0907962e6b94eea3b4ba86a6cc20b36ddb29f2dd/fsspec/tests/abstract), [fsspec copying guide](https://github.com/fsspec/filesystem_spec/blob/0907962e6b94eea3b4ba86a6cc20b36ddb29f2dd/docs/source/copying.rst))

## HTTP client choice: HTTPX versus Niquests

Source snapshots used here are HTTPX 0.28.1 at `b5addb64` and Niquests 3.20.1 at `46209c05`.

| Contract pressure | HTTPX | Niquests | Consequence for `vosfs` |
| --- | --- | --- | --- |
| Native async pooling | `AsyncClient`, shareable between tasks, async streaming and `aclose()` are first-class. ([API](https://www.python-httpx.org/api/#asyncclient)) | `AsyncSession` provides pooling and async calls; streamed calls return an async response while non-streamed calls return a buffered response. ([source](https://github.com/jawah/niquests/blob/46209c05bca3bdd2592fb0cc6d75f66442143966/src/niquests/async_session.py#L108-L170), [request overloads](https://github.com/jawah/niquests/blob/46209c05bca3bdd2592fb0cc6d75f66442143966/src/niquests/async_session.py#L1003-L1065)) | HTTPX has the simpler single response model for a small internal adapter. |
| Redirect default | `follow_redirects=False` on clients and sends. ([source](https://github.com/encode/httpx/blob/b5addb64f0161ff6bfe94c124ef76f6a1fba5254/httpx/_client.py#L1307-L1373)) | General request/GET/POST/PUT helpers default to following redirects; HEAD defaults false. ([source](https://github.com/jawah/niquests/blob/46209c05bca3bdd2592fb0cc6d75f66442143966/src/niquests/async_session.py#L1046-L1063), [method helpers](https://github.com/jawah/niquests/blob/46209c05bca3bdd2592fb0cc6d75f66442143966/src/niquests/async_session.py#L1163-L1179)) | HTTPX is fail-safe for VOSpace's overloaded 303 flows. Niquests must force `allow_redirects=False` at the adapter boundary for every operation. |
| mTLS | Current guidance is an `ssl.SSLContext` with `load_cert_chain`; the older `cert=` argument is deprecated. TLS configuration is client/transport-level. ([SSL docs](https://www.python-httpx.org/advanced/ssl/#client-side-certificates), [deprecation source](https://github.com/encode/httpx/blob/b5addb64f0161ff6bfe94c124ef76f6a1fba5254/httpx/_config.py#L23-L70)) | Accepts `cert` and `verify` per request and can change connection configuration. ([adapter source](https://github.com/jawah/niquests/blob/46209c05bca3bdd2592fb0cc6d75f66442143966/src/niquests/adapters.py#L1894-L1924)) | HTTPX needs separate clients/transports keyed by TLS credential. Niquests makes per-endpoint mTLS easier, but this must be tested under concurrency and pooling. |
| Authentication refresh | Supports custom sync and async auth flows. ([auth docs](https://www.python-httpx.org/advanced/authentication/#custom-authentication-schemes)) | Supports sync/async auth callables in `AsyncSession`. ([source](https://github.com/jawah/niquests/blob/46209c05bca3bdd2592fb0cc6d75f66442143966/src/niquests/async_session.py#L1003-L1065)) | Keep the public credential model client-neutral; implement one-refresh/one-retry and concurrency control in `vosfs`, not in storage options as a live client auth object. |
| Streaming upload/download | Async byte streaming and explicit streaming contexts; large request bodies can be generators. ([async docs](https://www.python-httpx.org/async/), [client docs](https://www.python-httpx.org/advanced/clients/#monitoring-upload-progress)) | Supports async bodies and streamed responses. ([source](https://github.com/jawah/niquests/blob/46209c05bca3bdd2592fb0cc6d75f66442143966/src/niquests/async_session.py#L1003-L1065)) | Both are viable; the spike must prove bounded memory and replay behaviour. |
| Retries | Built-in transport retries cover connect errors/timeouts only; status/read/write retry policy remains application work. ([transport docs](https://www.python-httpx.org/advanced/transports/#http-transport)) | Adapter/urllib3 retry facilities exist, but VOSpace operation idempotency still cannot be delegated generically. ([adapter source](https://github.com/jawah/niquests/blob/46209c05bca3bdd2592fb0cc6d75f66442143966/src/niquests/adapters.py)) | Implement operation-aware retries in `vosfs`; never blindly replay UWS job-creation POSTs or non-rewindable uploads. |
| Test seam | Official `MockTransport` and custom async transports. ([transport docs](https://www.python-httpx.org/advanced/transports/#mock-transports)) | No equivalent is part of the TRD's current `respx` setup; tests would need an adapter fake or a real local HTTP server. | Existing `respx` choice already commits the test design to HTTPX. If client neutrality is desired, test the internal adapter contract, not client calls throughout filesystem code. |

### Recommendation and required spike

Select HTTPX for 0.1 unless a two-client spike fails one of these cases:

1. node service uses one mTLS context while the negotiated endpoint is pre-authorized (no auth);
2. node service uses bearer auth while a negotiated endpoint uses a distinct mTLS context;
3. read endpoint redirects across origins: preserve `Range`, but do not leak `Authorization` or cookies;
4. write endpoint returns 303: capture `Location`, send one body-preserving upload under the extension's explicit semantics, and prove no zero-byte success;
5. a streamed upload is retried only when its body is rewindable and the operation is safe;
6. cancellation and `aclose()` leave no open connection or background task.

This will likely yield an internal `HttpTransport` protocol plus a lazy client pool keyed by `(origin, TLS configuration, proxy configuration)`. Do not expose either `httpx.AsyncClient` or `niquests.AsyncSession` as public storage options. `client_kwargs` must reject invariants such as automatic redirect following and default authorization headers that could leak to negotiated endpoints.

### Correct the redirect requirement

RFC 9110 supersedes RFC 7231. A 303 means the redirected retrieval is performed with GET/HEAD; 307 and 308 preserve method and body. Therefore “always reissue redirects with method and body preserved” is not a general HTTP rule. It can be a narrowly documented behaviour of an advertised VOSpace extension whose 303 is known to identify the upload target. Standard VOSpace itself also uses 303 for UWS job and transfer-details locations, where following with GET is correct. ([RFC 9110 §§15.4.4, 15.4.8, 15.4.9](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.4.4), [VOSpace 2.1 §§6.2 and 6.4](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html))

## Downstream compatibility: claims that need executable boundaries

| Consumer | Current primary-source contract | Missing TRD gate/boundary |
| --- | --- | --- |
| pandas | Non-HTTP remote URLs are handled through fsspec and `storage_options` are passed to the filesystem. ([pandas remote-files docs](https://pandas.pydata.org/pandas-docs/stable/user_guide/io.html#reading-writing-remote-files)) | Test both `read_csv` and a write (`to_csv`) with text wrapping, storage options, and a fresh process. State which pandas IO functions/engines are in 0.1. |
| NumPy | NumPy IO accepts file/file-like objects; NumPy does not advertise arbitrary fsspec URL dispatch. ([`numpy.load`](https://numpy.org/doc/stable/reference/generated/numpy.load.html), [`numpy.loadtxt`](https://numpy.org/doc/stable/reference/generated/numpy.loadtxt.html)) | Promise `np.load(fs.open(..., "rb"))` / `loadtxt` file-object interop, not transparent `np.load("vos://...")`; exclude memory mapping of remote files. Add `.npy` and `.npz` seek tests. |
| Dask | fsspec supplies `__dask_tokenize__` and pickle reconstruction from storage options. ([fsspec source](https://github.com/fsspec/filesystem_spec/blob/0907962e6b94eea3b4ba86a6cc20b36ddb29f2dd/fsspec/spec.py#L172-L193)) | A pickle round-trip is necessary but insufficient. Run an actual fresh worker process/distributed task after construction and token rotation; test deterministic tokenization and no live client in the graph. |
| Zarr v3 | Direct `FsspecStore` construction requires `async_impl`; current URL/mapping helpers can wrap sync filesystems with modern fsspec. Reads use `_cat_file`, writes `_pipe_file`, deletes `_rm`, listing `_find`/`_ls`, and partial batches `_cat_ranges`. ([current source](https://github.com/zarr-developers/zarr-python/blob/31817c681dc747d4d723af072e9562c624def553/src/zarr/storage/_fsspec.py)) | Correct the blanket “hard TypeError” claim and pin tested Zarr/fsspec versions. Add create/read/update/delete/list plus partial-value tests, not just `async_impl=True`. |
| PyArrow/Parquet | `FSSpecHandler` calls `info`, `find`, `mkdir(create_parents=...)`, recursive `rm`, `mv`, `copy`, `isfile`, and `open` for read/write/append. ([PyArrow 24 `FSSpecHandler` source](https://arrow.apache.org/docs/_modules/pyarrow/fs.html#FSSpecHandler)) | Test dataset discovery, footer seeks, write, copy/move, and explicitly decide append support. Correct `size` and ranged `open` alone do not cover the handler. |
| `fsspec.fuse` | The adapter calls `info`, `ls`, `mkdir`, `rmdir`, `touch`, `open`, `rm`, and optional `chmod`; its write path seeks to the supplied offset before each write. ([current source](https://github.com/fsspec/filesystem_spec/blob/0907962e6b94eea3b4ba86a6cc20b36ddb29f2dd/fsspec/fuse.py#L20-L148)) | Base `AbstractBufferedFile` is seekable only in read mode. Either provide a write object that supports FUSE offset writes, constrain/replace the FUSE adapter, or remove general FUSE write compatibility from 0.1. Test a real mount where CI permits it. |

## Required 0.1 acceptance additions

The following gates turn the corrected contract into a finite work package:

1. **VOSpace documents:** generated node/transfer documents validate against the pinned Recommendation XSD; tests cover v2.0 namespace + `version="2.1"`, every node type, unknown properties, views/protocols/security methods, XML media types, and malformed/oversized responses.
2. **URI corpus:** authority aliases (`!`/`~`), mismatches, root, Unicode/percent encoding, reserved characters, query/fragment handling, `.auto`, `.null`, dot segments, and no path escape/double decoding.
3. **Metadata discovery:** `/protocols`, `/views`, `/properties`, configured/VOSI binding discovery, provenance, cache expiry, and mandatory-operation conformance diagnostics.
4. **Transfer state machine:** async create redirect, `PHASE=RUN`, all UWS terminal/error/abort paths, result-link discovery, sync negotiation forms, multiple one-use endpoints, job deadline/cancellation, and byte-transfer completion/finalization.
5. **Views and bytes:** UnstructuredDataNode byte identity; StructuredDataNode original and transformed views; size/checksum/range binding to the selected representation.
6. **HTTP correctness:** half-open/suffix/empty ranges, 200/206/416, malformed `Content-Range`, ignored Range, cross-origin redirects without credential leakage, 303 extension upload, bounded streaming, per-endpoint auth, timeout/cancellation, and response closure.
7. **Retries:** respect `Retry-After`; capped jitter; one provider refresh under concurrent 401s; no duplicate job creation; body rewind checks; no retries for permanent faults.
8. **fsspec contract:** run the released abstract tests; add `_mv_file`, `_pipe_file(mode="create")`, sync and async facades, pickle and JSON round-trips, instance-cache/lifecycle, dircache invalidation, glob/find/walk/touch/rmdir, callback/progress, and text/binary mode tests.
9. **Downstream matrix:** pandas read/write, NumPy file-object `.npy`/`.npz`, Dask fresh worker process, Zarr CRUD/partial values, PyArrow dataset/Parquet read-write, and a bounded FUSE claim.
10. **Live conformance report:** distinguish mandatory failures, optional unsupported features, advertised extensions, probes, and fallback use. A successful fallback must not erase evidence that the server violated a mandatory VOSpace requirement.

## Sharp unresolved decisions for the wayfinder map

1. **What does a `vos://` URL select?** Lock the relationship among logical authority, HTTP endpoint, optional registry resolver, and unqualified paths.
2. **Which VOSpace views satisfy filesystem byte semantics?** Decide the support level for StructuredDataNode and transformed/no-original views.
3. **What is the portable LinkNode policy?** Decide no-follow, same-space follow, and external/cross-space resolver boundaries.
4. **What is the exact UWS transfer lifecycle?** Specify when endpoints become usable, how client byte completion becomes terminal job completion, deadlines, abort, and cleanup.
5. **What are the write semantics?** Lock overwrite/create/append/exclusive behaviour, atomicity, partial destination cleanup, and property preservation/clearing.
6. **How are service and endpoint credentials represented?** Prefer JSON-safe provider import paths; decide TLS client-pool keys and pre-authorized endpoint detection.
7. **HTTPX or Niquests?** Run the six-case spike above and record an ADR. The recommendation is HTTPX unless per-request TLS makes the client-pool design untenable.
8. **What does “maximum compatibility” mean for 0.1?** Publish a method/mode/downstream matrix with supported, fallback, extension-only, and explicitly unsupported cells.
9. **Is FUSE write support truly in 0.1?** It requires offset-write semantics absent from the proposed buffered upload design.
10. **Runtime XSD validation or CI-only validation?** Choose the dependency/performance boundary and rewrite R3.6 accordingly.

## Reference-project lessons

- S3FS, GCSFS, and ADLFS are useful async exemplars: per-file hooks, lazy sessions, specialised buffered files, bounded concurrency, explicit error translation, and cache invalidation recur across them. ([S3FS](https://github.com/fsspec/s3fs/tree/b7ec8db5170d8a9afeeadc9efb7b4292a335bf6b), [GCSFS](https://github.com/fsspec/gcsfs/tree/89f469da36cf4f993d27055adbc60de99565f42c), [ADLFS](https://github.com/fsspec/adlfs/tree/e6c5cb459fb982455e7a903e58ebd3afbf057711))
- AlluxioFS at the audited revision subclasses synchronous `AbstractFileSystem`, so it is useful for fallback and buffered-file ideas but not as the primary `AsyncFileSystem` template. ([source](https://github.com/fsspec/alluxiofs/blob/7b2ebb42000f0e7f8e57c80b8f3c1940a01a2d71/alluxiofs/core.py#L80-L100))
- None of these implementations substitutes for testing `vosfs` against fsspec's current abstract suite and VOSpace's own wire contract.

## Source/version note

The VOSpace normative baseline is Recommendation 2.1 (2018-06-20). Code observations are pinned to the commit links above so that later contract drift can be distinguished from research error. Downstream “stable” documentation was current on 2026-07-10; the implementation plan should pin minimum and latest-tested versions rather than treating moving `main` branches as a permanent contract.
