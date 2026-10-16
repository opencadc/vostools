# `vosfs` v0.3.0 OpenCADC Capability Contract

<!-- pyml disable line-length -->

Status: **Approved and implemented**

Implementation status: **Implemented and released**

Contract version vs package version: this v0.3.0 capability contract has governed
every shipped `vosfs` release from v0.3.0 through v0.4.0. Later implementation
hardening preserves this public capability boundary, so the contract remains at
v0.3.0 independently of the package version.

Contract target: **`vosfs` v0.3.0**

Last updated: **2026-07-21**

The key words **MUST**, **MUST NOT**, **REQUIRED**, **SHOULD**, **SHOULD NOT**,
and **MAY** in this document are to be interpreted as described by
[RFC 2119](https://www.rfc-editor.org/rfc/rfc2119) and
[RFC 8174](https://www.rfc-editor.org/rfc/rfc8174) when, and only when, they
appear in all capitals.

This document is the publication contract for `vosfs` v0.3.0. A capability is
not considered implemented or releasable until its acceptance gates pass.
This is the sole normative v0.3.0 contract; linked research notes are
informative evidence and defer to this document if they differ.

## 1. Purpose and compatibility claim

`vosfs` provides an asynchronous
[`fsspec`](https://github.com/fsspec/filesystem_spec) filesystem for the
OpenCADC Cavern VOSpace service. It enables fsspec-aware Python tools and a
bounded set of scientific-stack consumers to read, write, inspect, and mutate
OpenCADC VOSpace paths.

The server contract is the **OpenCADC VOSpace profile** evidenced by
[`opencadc/vos` commit `cf976ce8141dd3341631b7f3e07aa38443d42f58`](https://github.com/opencadc/vos/tree/cf976ce8141dd3341631b7f3e07aa38443d42f58).
The [IVOA VOSpace 2.1 Recommendation](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html)
defines the wire vocabulary used by the implemented OpenCADC operations; it
does not enlarge the v0.3.0 capability surface.

`vosfs` v0.3.0 **MUST NOT** claim generic VOSpace 2.1 conformance or
compatibility with unrelated VOSpace implementations.

The executable compatibility baseline is:

- Python 3.10 through 3.14;
- fsspec 2026.6.0; and
- the OpenCADC source snapshot named above.

Exact downstream-library versions are release evidence, not permanent parts
of this contract.

## 2. Capability vocabulary

Every public behavior has exactly one classification.

| Class | Meaning |
| --- | --- |
| **Native** | Backed by an implemented and tested operation in the OpenCADC VOSpace profile. |
| **Client-derived** | Supported by composing only native operations, including an explicitly documented fallback. |
| **Extension-conditional** | Exposed only when a wired OpenCADC extension is advertised and its required behavior is verified. |
| **Unsupported** | Deliberately excluded because the profile lacks the required semantics, no approved client-derived behavior supplies them, or the behavior is outside the v0.3.0 product scope. |

There are no extension-conditional public operations in the v0.3.0 release
set. OpenCADC extensions that are implemented and tested may be classified as
native; all other extensions remain unsupported.

An unsupported call **MUST** raise `NotImplementedError` before causing a
remote mutation.

## 3. Public filesystem construction

The public constructor **MUST** accept these transport options.

| Option | Type and default | Contract |
| --- | --- | --- |
| `endpoint_url` | required `str` | Absolute OpenCADC service base URL, for example `https://staging.canfar.net/arc`. No registry lookup is performed. |
| `token` | `str | None` | Literal bearer token, including an OIDC access token. |
| `tokenfile` | `str | None` | File whose bearer token is reread before every authenticated request. |
| `certfile` | `str | None` | Combined X.509 certificate-chain and private-key PEM file. |
| `timeouts` | mapping or `None` | Optional finite positive `connect`, `read`, `write`, and `pool` inactivity limits. |
| `trust_env` | `bool = True` | Controls HTTPX proxy and CA environment handling. |

The filesystem **MUST** also preserve fsspec's supported `asynchronous`,
`loop`, `batch_size`, `skip_instance_cache`, `use_listings_cache`,
`listings_expiry_time`, and `max_paths` options.

The default HTTP timeouts are:

| Timeout | Seconds |
| --- | ---: |
| Connect | 10 |
| Pool | 10 |
| Read | 60 |
| Write | 60 |

`endpoint_url` **MUST** use `https` when any credential is configured. It
**MUST NOT** contain userinfo, a query, or a fragment. A trailing slash is
normalized away.

### 3.1 Credential resolution

The environment fallbacks are exactly:

| Constructor option | Environment fallback |
| --- | --- |
| `token` | `VOSFS_TOKEN` |
| `tokenfile` | `VOSFS_TOKEN_FILE` |
| `certfile` | `VOSFS_CERT_FILE` |

If any explicit credential option is provided, all credential environment
variables **MUST** be ignored. Otherwise, zero or exactly one environment
source may be configured. After precedence resolution, `token`, `tokenfile`,
and `certfile` are mutually exclusive. Invalid combinations **MUST** raise
`ValueError` before network I/O. No credential means anonymous access.

`VOSFS_TOKEN` **MUST** be reread before every authenticated request. The
contents named by `tokenfile` **MUST** also be reread before every request.
`vosfs` consumes access tokens but **MUST NOT** acquire or refresh them.

The public API **MUST NOT** accept an auth object, live HTTP client, SSL
context, callable provider, cookie jar, custom authorization headers, or
`client_kwargs`. Cookie auth, HTTP Basic auth, delegated proxy headers, and
interactive identity flows are unsupported.

A literal `token` is included in fsspec pickle and JSON serialization. User
documentation **MUST** state this and **SHOULD** recommend `tokenfile` or an
environment source.

## 4. Filesystem URL and VOSpace identity

The service is selected only by `endpoint_url`. The apparent authority after
the `vos://` protocol marker is part of the filesystem path, not a service or
VOSpace authority.

These inputs **MUST** normalize to the same internal path `/a/b`:

- `vos://a/b`
- `vos:///a/b`
- `/a/b`
- `a/b`

`vos://`, `vos:///`, `/`, and the empty path represent root.

User paths are literal. Path parsing **MUST NOT** percent-decode them, so
fsspec may apply `_strip_protocol` more than once without changing the name.
`?` and `#` are allowed in a name. Parsing **MUST**:

- reject userinfo, NUL, an encoded path separator, and any `.` or `..`
  segment;
- preserve Unicode and literal percent characters; and
- percent-encode individual path segments when constructing HTTP URLs.

A node URI returned by the service **MUST** be decoded exactly once when it
is turned into an internal path. That decode is not applied to user input.

A `?` that fsspec treats as a single-character glob remains a glob metacharacter
at the fsspec layer. Abstract glob edge cases that use `?` stay outside this
contract, because that character is a literal name character here.

The logical VOSpace authority used inside XML documents **MUST** be discovered
from the URI returned by the root node. It **MUST** be cached per filesystem
instance and every later node URI **MUST** match it. The caller does not supply
this authority.

## 5. Supported OpenCADC service profile

`vosfs` **MUST** discover service bindings by fetching
`endpoint_url + "/capabilities"` on first I/O. It **MUST** resolve only the node
and synchronous-transfer bindings and validate the configured credential
against their advertised security methods.

Binding selection **MUST** use these exact capability identifiers, not display
names or URL suffix matching:

- nodes: `ivo://ivoa.net/std/VOSpace/v2.0#nodes`, standard-role ParamHTTP
  interface, base access URL; and
- synchronous transfer: `ivo://ivoa.net/std/VOSpace#sync-2.1`, standard-role
  ParamHTTP interface, full access URL.

The supported security-method identifiers are the empty anonymous method,
`ivo://ivoa.net/sso#token`, and
`ivo://ivoa.net/sso#tls-with-certificate`. Advertised cookie authentication is
not a supported credential source. The internal binding cache and parsed
capability model **MUST NOT** be exposed as a public runtime-capabilities API.

The service-binding cache is immutable for the filesystem instance. Directory
cache invalidation **MUST NOT** refresh it. Reconstruction or a new instance
fetches it again.

| Resource | v0.3.0 class | Supported use |
| --- | --- | --- |
| `/capabilities` | Native | Discover the node and synchronous-transfer bindings and their security methods. |
| `/nodes/*` | Native | GET node metadata/listing, PUT node creation, and DELETE one file or empty container. No property-write API is exposed. |
| `/synctrans` | Native | Synchronous `pullFromVoSpace` and `pushToVoSpace` negotiation only. |
| Negotiated `/files/*` endpoint | Native | HEAD/GET whole bytes and PUT create-or-truncate bytes. The URL is consumed only when returned by negotiation. |

`vosfs` **MUST NOT** guess a missing operation URL. A missing binding disables
only its dependent operation and raises an actionable `NotImplementedError`.

Byte access is a *negotiated capability*: `sync-2.1` is an optional IVOA 2.1
addition, so a deployment MAY advertise no usable synchronous-transfer binding.
That is a **supported degradation**, not a fault — node metadata operations
(which need only the node binding) continue to work, and only byte read and
write are disabled. The raised `NotImplementedError` **MUST** name the missing
`#sync-2.1` capability, the `endpoint_url`, and the configured security method.
Constructing a `/files` byte URL directly as a fallback remains out of scope
(section 16); the evidence is in
[transfer-endpoint variability](../research/vosfs-transfer-endpoint-variability.md).

The client **MUST NOT** construct a `/files` URL directly.

### 5.1 Explicitly excluded server resources

The following resources and behaviors are unsupported in v0.3.0, including
when OpenCADC implements them:

- asynchronous `/transfers` jobs other than a same-service `moveNode`
  (`keepBytes=false`), including its UWS wait and `PHASE=ABORT` lifecycle;
- `/async-delete` and `/async-setprops` jobs;
- `/pkg` package downloads;
- `/protocols`, `/views`, and `/properties` metadata resources;
- search and server-side sort;
- `copyNode`, `pullToVoSpace`, `pushFromVoSpace`, and bidirectional mounts.

`/synctrans` is retained as synchronous negotiation even though Cavern uses
UWS components internally. From the `vosfs` perspective it is only a POST,
303 interpretation, and GET of the synchronous result. `vosfs` **MUST NOT**
start or poll a job.

## 6. XML and node model

XML request bodies **MUST** be UTF-8 with
`Content-Type: text/xml; charset=utf-8` and `Accept: text/xml`. The runtime
parser **MUST** reject external entities, bound response bodies before parsing,
and validate the fields required by this contract. Generated documents and
recorded XML fixtures **MUST** validate against the pinned VOSpace 2.1 schema
in tests; runtime XSD validation is not required.

Generated node and transfer documents **MUST** use the
`http://www.ivoa.net/xml/VOSpace/v2.0` XML namespace and carry
`version="2.1"` on the VOSpace document root.

### 6.1 fsspec metadata mapping

| OpenCADC node | `type` | `size` | Additional fields |
| --- | --- | ---: | --- |
| `DataNode` | `file` | Required integer byte length | `mtime`, `md5`, `content_type`, `uri`, `properties` when available. |
| `ContainerNode` | `directory` | `0` | `mtime`, `uri`, `properties` when available. |
| `LinkNode` | `other` | `0` | `islink=True`, `target`, `uri`, `properties` and access fields when available. |

Every node kind can additionally expose `owner` (creator display identity),
`read_groups` and `write_groups` (tuples, including known empty tuples), `public`,
`locked`, and `permissions` when corresponding properties are available.
Missing properties remain unknown. Simple unescaped initial creator CNs are
shown as the CN; other identities remain verbatim. Raw identities and group
URIs remain in properties. Only recognized CADC GMS query/fragment prefixes are
shortened. The ten-character permission summary is advisory: owner read/write,
separate group access, public reading, no execute claim; locks remove displayed
write permission. It **MUST NOT** be reported as a real POSIX `mode` or `gid`.
See [ADR 0008](../../../../docs/adr/0008-render-shell-shaped-long-listings.md).

`name` **MUST** be the full normalized filesystem path. URI-keyed node
properties, including unknown properties, **MUST** be preserved in a read-only
`properties` mapping. Server-computed properties remain read-only.

`StructuredDataNode` and `UnstructuredDataNode` **MUST** be exposed as opaque
files. v0.3.0 provides no structured-view or representation-selection API.

Listings contain immediate children only and are read in pages. Each container
listing GET sends a fixed ``limit``. A further page sends ``uri`` set to the
previous page's last child, and that inclusive cursor child is not repeated.
A page shorter or longer than the limit is the remainder of the listing.
Metadata lookups stay on ``limit=0``. Client traversal
**MUST NOT** follow LinkNodes. Byte reads MAY follow an internal LinkNode whose
target has the discovered VOSpace authority; an external LinkNode **MUST**
raise `NotImplementedError`.

Copying an internal LinkNode whose target has the discovered VOSpace authority
materializes its target bytes as a DataNode. Copying an external or non-VOS
LinkNode **MUST** raise `NotImplementedError` before destination mutation.
Client-derived move of any LinkNode is unsupported and **MUST** raise
`NotImplementedError` before copy or delete mutation.

Generic property writes, permission mutation, public link creation, `chmod`,
and `chown` are unsupported. The implementation carries no node-update
primitive: property writes of any kind are outside the capability contract.

## 7. Synchronous byte negotiation

Every logical byte read or write **MUST** perform synchronous negotiation.
A push **MUST** POST a VOSpace 2.1 transfer document. A pull **MUST** try the
URL-parameter form first (`TARGET`, `DIRECTION=pullFromVoSpace`, `PROTOCOL`,
`SECURITYMETHOD`, `REQUEST=redirect`). A direct `303` to a byte URL completes
the pull. A `4xx`, a `200` XML body, or a `303` to transfer details means the
service does not support that form; the instance remembers that and later
pulls use the XML document. The XML path is:

1. construct a VOSpace 2.1 transfer document with one authority-qualified
   target;
2. use `pullFromVoSpace` plus an HTTPS GET protocol for reads, or
   `pushToVoSpace` plus an HTTPS PUT protocol for writes;
3. POST the document to the discovered `/synctrans` binding with automatic
   redirects disabled;
4. interpret the 303 `Location` as transfer details or a byte endpoint;
5. GET transfer details when required and choose a compatible returned
   protocol; and
6. perform the one byte GET, HEAD, or PUT against the negotiated endpoint.

A negotiation POST **MUST NOT** be replayed for a different logical transfer.
One open file **MAY** reuse the endpoint negotiated for that file, and
**MUST** negotiate again after `401`, `403`, or expiry. A negotiated endpoint
**MUST NOT** be reused for a different path.

Every HTTP redirect outside this approved synchronous-transfer 303 chain
**MUST** fail without following it.

Redirect targets **MUST** be absolute `http` or `https` URLs without userinfo.
Bearer targets **MUST** use `https`. Redirect loops and more than five hops
**MUST** fail.

Credential routing is determined by the selected negotiated security method,
not origin alone:

- an anonymous or pre-authorized endpoint receives no Authorization header,
  Cookie, or caller X.509 certificate, whether same-origin or cross-origin;
- a token endpoint receives a freshly resolved bearer header only when the
  negotiated method is `ivo://ivoa.net/sso#token`; a cross-origin token target
  additionally **MUST** use HTTPS;
- a certificate endpoint uses the X.509 client only when the negotiated method
  is `ivo://ivoa.net/sso#tls-with-certificate`, whether same-origin or
  cross-origin, and **MUST** use HTTPS; and
- when no returned endpoint matches the configured credential source, the
  transfer fails before byte I/O.

## 8. Read contract

Some OpenCADC byte backends honour HTTP byte ranges (for example minoc behind
`vault`); others ignore `Range` and answer `200` with the whole object
(Cavern). `vosfs` **MUST** decide from the byte response, not from
`Accept-Ranges` alone.

- `_get_file` **MUST** stream one negotiated whole-object GET to the local
  destination with bounded memory and byte-level callback updates.
- `_cat_file(path, start, end)` **MUST** support `None`, zero, negative
  bounds, empty slices, and EOF clipping with Python half-open semantics. When
  `start`/`end` select a partial read that can be expressed as one HTTP
  `Range`, the byte GET **MUST** include that header. A `206` with a matching
  `Content-Range` and body length **MUST** return those bytes. A `200` or
  `204` **MUST** be treated as a whole-object body and sliced locally. Bounds
  that cannot be expressed as one HTTP `Range` (for example mixed negative
  indices) **MUST** use one whole GET and local slicing.
- `_cat_ranges` **MUST** be overridden to group ranges by object. Per object it
  **MUST** negotiate once, then issue ranged GETs while responses are `206`.
  The first `200`/`204` for that object **MUST** fall back to one whole-object
  staging download (or the already-received whole body) and local slicing for
  every range of that object. `max_gap` is accepted for fsspec call
  compatibility but **MUST NOT** force coalescing that would change these
  rules.
- `open("rb")` **MUST** download once into a disk-backed temporary file and
  then provide local `read`, `readinto`, `readline`, iteration, `tell`, and
  `seek(0/1/2)` semantics. That default stays whole-object. When the caller
  passes `block_size` or `cache_type`, `open("rb")` **MUST** use a buffered
  file that negotiates once and sends ranged GETs. A `200` on any of those
  reads **MUST** stage the body of that same response, and later reads of
  that file **MUST** stay local. Buffered reads **MUST** use the same `206`
  validation and EOF rules as `cat_file`. If the data length is unknown,
  buffered open **MUST** stage the object to determine its length. A
  LinkNode's metadata size **MUST NOT** be used as its target's byte length.
- `open("r")` **MUST** use fsspec text wrapping over that staged binary file.
- Empty files returned as HTTP 204 **MUST** read as `b""`.

Byte requests **MUST** use `Accept-Encoding: identity` and consume raw response
bytes so HTTP content decoding cannot alter filesystem content.

Python half-open `[start, end)` **MUST** map to inclusive HTTP
`bytes=start-(end-1)` (and to standard open/suffix forms when one bound is
absent).

206 validation checks the full interval against the reported total, including
EOF clipping, suffixes, and open-ended requests. Unknown totals permit only
exactly matching closed intervals. A 416 yields an empty slice only when its
`Content-Range: bytes */TOTAL` proves the object empty or start beyond EOF.
Malformed or inconsistent responses fail without returning unvalidated bytes.

`block_size`, `cache_type`, and `cache_options` configure the opt-in buffered
file. They do not guarantee partial network reads when the endpoint ignores
ranges. `vosfs` **MUST NOT** advertise `blockcache::` / `cached::` support.

`open_async` and memory mapping are unsupported.

## 9. Write contract

- `_put_file` **MUST** stream one local file through one negotiated whole PUT
  with bounded memory and byte-level callback updates.
- `_put_file(mode="create")` **MUST** use the same non-atomic `_info`
  existence preflight and `FileExistsError` behavior as create-mode piping.
- `_pipe_file(mode="overwrite")` **MUST** issue one whole PUT and create or
  truncate the target DataNode.
- `_pipe_file(mode="create")` **MUST** use `_info` as an existence preflight
  and raise `FileExistsError` for an existing path. The check is non-atomic.
- Coordinated `put` and `pipe` **MUST** create missing remote `ContainerNode`
  parents top-down at most once per operation before writing descendants. A
  recursive `put` **MUST** also materialize empty source directories. This does
  not change the single-file `put_file` or `pipe_file` hooks.
- `open("wb")` and `open("w")` **MUST** stage into a disk-backed temporary file
  and issue one PUT only when close completes successfully.
- `open("xb")` and `open("x")` **MUST** add the same non-atomic existence
  preflight.
- The upload **MUST** preserve raw bytes, provide `Content-Length` when known,
  and use the caller's content type or `application/octet-stream`.
- A provided or returned MD5 digest **MUST** be validated. HTTP 412 is an
  integrity failure.

A byte PUT success is HTTP 201. A 3xx response to the byte PUT **MUST** fail.
The upload body is single-use and **MUST NOT** be replayed automatically.

OpenCADC may truncate the destination before a failed upload completes. A
failed PUT **MUST** be reported as an uncertain write that may have truncated
the target. `vosfs` **MUST NOT** issue a cleanup DELETE.

Append modes, every mode containing `+`, `autocommit=False`, transactions,
offset writes, conditional create, atomic replacement, resumable upload, and
multipart upload are unsupported.

Binary and text compression wrappers **MUST** own the full staged-write close
operation: finish compression before one upload, discard on a failed context,
and discard abandoned wrappers during collection. Compression does not alter
these ownership guarantees.

## 10. Namespace and mutation contract

- `_mkdir` **MUST** create one ContainerNode and preserve normal
  `FileExistsError` and missing-parent behavior.
- `_makedirs` **MUST** discover missing ancestors, create them top-down, honor
  `exist_ok`, and tolerate concurrent creation only when the resulting node is
  a ContainerNode.
- `_rm_file` **MUST** delete one non-container node.
- Non-recursive `rm` and `rmdir` MAY delete a ContainerNode only after listing
  and proving it empty. The check and delete are non-atomic. A non-empty
  container **MUST** fail without deleting descendants.
- Recursive `rm` **MUST** traverse client-side and DELETE files and empty
  containers leaves-first. It **MUST NOT** call `/async-delete`. Independent
  siblings MAY be listed and deleted concurrently, but a container **MUST** be
  deleted only after every child is confirmed deleted, and no new request may
  start after a failure.
- `rm(..., maxdepth=<number>)` is unsupported.
- `_cp_file` **MUST** stream a negotiated GET into a negotiated PUT when the
  source size is known and the connection budget permits two slots, taking
  both connection slots only after both
  endpoints exist. It **MUST** check the streamed MD5 against a source
  `contentMD5` when one is present and against a digest the server returns.
  If the copy fails, a destination node this call created **MUST** be
  deleted after releasing the transfer's connections. An unknown source size
  or a one-connection budget **MUST** fall back to a temporary file.
  It MAY overwrite an existing DataNode, preserves bytes only, and does not
  copy server-only properties.
- Recursive copy **MUST** create containers and relay files client-side. It is
  non-atomic.
- Move of a DataNode or ContainerNode **MUST** require an absent destination.
  When `maxdepth` is unset and the service advertises `/transfers`, the
  client **MUST** try a `moveNode` job with the destination URI as `direction`
  and `keepBytes=false`. It **MUST** start the job with `PHASE=RUN` on its
  `/phase` resource and request `PHASE=ABORT` there on cancellation or timeout.
  Otherwise it **MUST** copy or recreate the
  source and delete the source only after destination success. A missing
  `/transfers` binding, a cross-service move, or a job rejected as
  unsupported, including a terminal unsupported-operation error, **MUST** use
  that client-side move. Permission errors and uncertain job outcomes **MUST
  NOT** trigger a competing client transfer.
- Move of a LinkNode is unsupported and **MUST** raise `NotImplementedError`
  after resolving source metadata but before copy or delete mutation.
- `touch(truncate=True)` **MUST** PUT zero bytes. `touch(truncate=False)` is
  unsupported.

Move and recursive operations do not roll back successful items. A failed
source deletion may leave both move paths. Errors **MUST** identify completed
and failed paths.

Every successful mutation **MUST** invalidate the affected path and all
affected parents. Move invalidates both source and destination trees. Failed
or unattempted paths **MUST NOT** be represented as successfully mutated in
the directory cache.

## 11. fsspec capability matrix

`VOSpaceFileSystem` **MUST** subclass `fsspec.asyn.AsyncFileSystem`, set
`async_impl = True`, `protocol = "vos"`, and `cachable = True`, and register the
`vos` protocol through the `fsspec.specs` entry-point group.

| Public API or hook | Class | v0.3.0 behavior |
| --- | --- | --- |
| `info`, `stat` / `_info` | Native | Node metadata mapping in section 6. Missing paths raise `FileNotFoundError`. |
| `ls`, `listdir` / `_ls` | Native | Immediate children read in `limit` pages, with detailed and path-only forms. |
| `exists`, `lexists`, `isfile`, `isdir`, `size`, `sizes` | Client-derived | Derived from `_info`; `lexists` does not dereference links. |
| `modified` | Client-derived | Return the OpenCADC modification date. |
| `created` | Unsupported | Raise `NotImplementedError`; no creation timestamp is fabricated. |
| `walk`, `find`, `glob`, `expand_path` | Client-derived | Client traversal with fsspec `maxdepth`, detail, and error semantics. Glob supports `*`, `[]`, and `**`. Traversal never follows links. |
| Question-mark glob paths | Unsupported | `?` is a literal name character, so a single-character `?` glob is not a path this filesystem can name. |
| `du`, `disk_usage`, `tree` | Client-derived | Client traversal over paged listings; potentially expensive. |
| `checksum`, `ukey` | Client-derived | fsspec metadata token; no separate public content-checksum API. |
| `cat_file` | Client-derived | Ranged GET when bounds allow; `206` returns the partial body, `200`/`204` whole-object slice locally. |
| `cat_ranges` | Client-derived | Per-object negotiation; ranged GETs while `206`, else one whole-object stage and local slices. |
| `cat`, `head`, `tail`, `read_block` | Client-derived | Built from `cat_file` / staged seek; network cost follows the byte backend. |
| `get_file` | Native | Bounded negotiated GET to one local target. |
| `get`, `download` | Client-derived | fsspec expansion and coordinator over `_get_file`. |
| `open("rb"/"r")` | Client-derived | Disk-staged, seekable whole-object read. |
| `open_async` | Unsupported | Raise `NotImplementedError`; async consumers use coroutine hooks. |
| Remote Range/206 | Client-derived | Sent for partial `cat_file` / `cat_ranges` reads; accepted only on validated `206`. |
| `pipe_file(mode="overwrite")`, `write_bytes` | Native | Negotiated whole PUT with create-or-truncate behavior. |
| `pipe_file(mode="create")` | Client-derived | `_info` preflight followed by negotiated PUT; explicitly non-atomic. |
| `pipe` | Client-derived | Bounded fsspec coordinator over `_pipe_file`; creates required remote parents top-down once per operation. |
| `put_file(mode="overwrite")`, `upload` | Native | Bounded negotiated PUT from one local file. |
| `put_file(mode="create")` | Client-derived | `_info` preflight followed by negotiated PUT; explicitly non-atomic. |
| `put` | Client-derived | fsspec expansion and coordinator over `_put_file`; creates required remote parents top-down once per operation and preserves empty directories. |
| `open("wb"/"w"/"xb"/"x")` | Client-derived | Disk-staged upload on successful close. |
| Append and `+` modes | Unsupported | Raise `NotImplementedError` before mutation. |
| `mkdir` | Native | Create one ContainerNode. |
| `makedirs`, `mkdirs` | Client-derived | Top-down ancestor creation. |
| `rm_file` | Native | Delete one non-container node. |
| `rm`, `rmdir` | Client-derived | Empty-check or leaves-first client deletion; no async UWS. |
| `cp_file`, `copy`, `cp` | Client-derived | Bounded byte relay and client recursion. |
| `mv`, `move`, `rename` (DataNode and ContainerNode) | Client-derived | Non-atomic copy/recreate then delete; no overwrite. |
| `mv`, `move`, `rename` (LinkNode) | Unsupported | Resolve source metadata, then raise `NotImplementedError` before copy or delete mutation. |
| `touch(truncate=True)` | Native | Create or truncate to zero bytes. |
| `touch(truncate=False)` | Unsupported | Raise `NotImplementedError`. |
| Blocking facade | Client-derived | fsspec mirrors supported coroutine hooks when `asynchronous=False`. |
| Async facade | Client-derived | fsspec exposes the supported coroutine hooks when `asynchronous=True`; no blocking facade on that instance. |
| Callbacks | Client-derived | File transfers report bytes; bulk coordinators branch callbacks by file. |
| Directory cache | Client-derived | Standard fsspec cache options plus mutation invalidation. |
| Pickle, `to_json`, `from_json` | Client-derived | Primitive constructor state and fresh live resources after reconstruction. |
| `simplecache::vos://` | Client-derived | Whole-file read and write wrapper. |
| `filecache::vos://` | Client-derived | Whole-file read wrapper only. |
| `blockcache::`, `cached::` | Unsupported | Range-oriented wrappers are outside the release claim. |
| FUSE | Unsupported | No mount or offset-write claim. |

## 12. HTTPX transport and lifecycle

HTTPX is the sole production HTTP client. The filesystem **MUST** lazily own a
client pool keyed by TLS configuration:

1. one validating no-client-certificate client for anonymous, bearer, and
   pre-authorized requests; and
2. when `certfile` is configured, one validating client whose fresh
   `ssl.SSLContext` loads that combined PEM.

Bearer credentials are per-request headers and are not pool keys. Clients
**MUST** use `follow_redirects=False`, no client-level auth, no default
Authorization header, no cookie jar, and transport retries set to zero.

Lazy client construction **MUST** be concurrency-safe and create at most one
client per TLS key per filesystem instance and event loop.

Request economy. Every request is a network round trip, so:

- metadata lookups (`info`, `exists`, `isdir`, `modified`, authority
  discovery, and read-target validation) **MUST** request the node with
  `limit=0` and **MUST NOT** download a container's child listing; an entry
  already present in its parent's cached listing MAY be answered from the
  directory cache;
- tree walks (`find`, `glob`, `du`, `walk`-derived expansion) **SHOULD** list
  all containers of one depth concurrently, applying `on_error` at every
  depth, and `du` **MUST** take sizes from the listings;
- bulk operations default to `batch_size=32` when the caller passes none, so
  queued transfers cannot outwait the HTTP `pool` timeout; the pool keeps as
  many idle connections as it may open (100).

`aclose()` **MUST** be public and idempotent. It closes every realized client,
clears the pool, evicts the instance from fsspec's instance cache, and makes
later I/O fail as closed. Synchronous `close()` **MUST** bridge through the
filesystem's fsspec loop. A finalizer MAY warn or attempt best-effort cleanup
but is not the lifecycle contract.

Pickle and fsspec JSON **MUST** contain only primitive constructor options.
Live clients, loops, locks, responses, service bindings, temporary files, and
directory caches **MUST NOT** be serialized. Reconstruction creates fresh live
state and resolves environment credentials again. A caller-supplied runtime
`loop` is accepted for fsspec compatibility but **MUST** be omitted from
serialized storage options and recreated after reconstruction.

## 13. Errors, redirects, retries, and cancellation

| Failure | Required exception |
| --- | --- |
| Invalid input, node type, or option | `ValueError` |
| Authentication or authorization | `PermissionError` |
| Missing node or parent | `FileNotFoundError` |
| Existing destination or conflict | `FileExistsError` |
| Unsupported operation or missing required binding | `NotImplementedError` |
| Quota exhaustion | `OSError` with `errno.ENOSPC` |
| Locked or busy node | `BlockingIOError` |
| HTTP timeout | `TimeoutError` |
| Connection failure | `ConnectionError` |

One public `VOSpaceError(OSError)` **MUST** represent every remaining
OpenCADC, integrity, HTTP, and partial-completion failure. It **MUST** retain
the HTTP status, symbolic fault when present, retry guidance, and completed
and failed paths when applicable.

Error bodies **MUST** be bounded to 8 KiB before parsing or reporting.
Credentials, Cookie values, and pre-authorized URL tokens **MUST** be redacted
from exceptions, logs, representations, and recorded fixtures.

HTTPX automatic retries are disabled. `vosfs` v0.3.0 **MUST NOT** automatically
replay a capabilities or node request, negotiation POST, mutation, byte GET,
or byte PUT. Callers own higher-level retry policy.

Cancellation **MUST** propagate unchanged, close the active response and local
temporary file, and start no new request. No background transfer task may
remain.

## 14. Scientific-stack compatibility

These are narrow release claims, not blanket claims for every API in each
project.

| Consumer | Required v0.3.0 gate | Explicit boundary |
| --- | --- | --- |
| pandas | `read_csv("vos://...", storage_options=...)` and `DataFrame.to_csv(...)` in a fresh process. | No blanket Excel, SQL, or engine claim. |
| NumPy | Round-trip `.npy` and `.npz` through file objects; `loadtxt` through a file object. | `numpy.load("vos://...")` and remote `mmap_mode` are unsupported. |
| Dask | CSV read/write through a fresh worker process with `blocksize=None`; deterministic reconstruction and tokenization. | Partitioned remote-range reads are unsupported. |
| Zarr v3 | `FsspecStore` create, read, overwrite, list, delete, and partial-value reads. | Partial reads transfer the complete object. Zarr v3 requires Python >= 3.11; the gate does not apply on 3.10. |
| PyArrow/Parquet | `FSSpecHandler` dataset discovery and Parquet read/write. | Footer seeks stage complete objects; append streams are unsupported. |
| `fsspec.fuse` | No release gate. | Unsupported in v0.3.0. |

## 15. Executable acceptance contract

### 15.1 Hermetic tests

The implementation **SHOULD** have one internal HTTP transport seam.
Production constructs HTTPX transports; tests inject RESpx/HTTPX mock
transports. The injection point is test-internal and **MUST NOT** appear in
`storage_options`.

Hermetic tests **MUST** cover:

1. capabilities, node GET/PUT/POST/DELETE, synchronous negotiation, and
   negotiated byte GET/HEAD/PUT;
2. exact XML, headers, redirect interpretation, endpoint credential routing,
   raw-byte identity, empty files, and bounded bodies;
3. every exception mapping, timeout, cancellation, uncertain write, and
   partial-completion path;
4. every supported coroutine hook with `asynchronous=True`, every mirrored
   public method with `asynchronous=False`, and staged `open()` through its
   supported synchronous seam;
5. fsspec 2026.6.0's reusable abstract open, pipe, copy, get, and put
   tests, with skips mapped to an explicit unsupported row;
6. pickle and fsspec JSON round-trips in a fresh process before and after
   client creation;
7. instance-cache eviction, close behavior, directory-cache invalidation, and
   environment credential reconstruction; and
8. all scientific-stack gates in section 14.

The current hermetic baseline collects 580 tests: 574 pass and six skip. All
six skips belong to the reusable-suite ledger below, which is exact for fsspec
2026.6.0 and `vosfs` v0.5.0:

| Reusable suite | Supported pass | Unsupported skip | Exact skip reason |
| --- | ---: | ---: | --- |
| open | 1 | 0 | — |
| pipe | 1 | 0 | — |
| copy | 43 | 2 | `fil?1`, non-recursive and recursive: `?` is a literal name character, not a glob. |
| get | 43 | 2 | `fil?1`, non-recursive and recursive: `?` is a literal name character, not a glob. |
| put | 43 | 2 | `fil?1`, non-recursive and recursive: `?` is a literal name character, not a glob. |
| **Total** | **131** | **6** | One Unsupported question-mark glob capability; no other reusable-suite skip. |

The supported get count includes the list-source hashed-name case; its earlier
teardown failure did not establish a backend gap. All reusable missing-parent
put cases run. Coordinated missing-parent pipe and put behavior is additionally
covered by the focused write tests. Move remains supported through dedicated
hermetic tests because fsspec 2026.6.0 publishes no reusable move suite.

Replay tests **MUST** block unmatched network access and require every expected
RESpx route to be called.

### 15.2 Recorded interaction fixtures

Service interactions MAY be captured through an explicit, opt-in, project-owned
HTTPX transport recorder. RESpx supplies strict routing, pass-through support,
call history, and replay; the project owns recording, fixture persistence, and
sanitization.

Recording is disabled by default and in normal CI. Before a fixture is
committed, the recorder **MUST** replace or remove:

- Authorization, Proxy-Authorization, Cookie, and Set-Cookie values;
- certificate material and username/password values;
- pre-authorized tokens embedded in URL paths, queries, headers, or XML;
- personal usernames, subject DNs, groups, and owner fields;
- unique test roots, request IDs, transfer IDs, dates, and volatile service
  headers; and
- any response content not intentionally part of the deterministic test.

Sanitized fixtures **MUST** be manually reviewed. They are regression evidence,
not release evidence.

## 16. Out of scope and roadmap

The following are not v0.3.0 capabilities:

- a CLI, registry/shortname resolution, or credential acquisition;
- generic VOSpace portability or full VOSpace 2.1 conformance;
- asynchronous UWS, native move, recursive-delete jobs, or bulk property jobs;
- direct construction of `/files` URLs;
- cross-service or cross-filesystem orchestration;
- block caching (`blockcache::` / `cached::`), append/update modes,
  transactions, conditional writes, atomic replacement, resumable upload, or
  multipart upload;
- server-side copy, search, sort, pagination, general views, package download,
  and persistent Structured/Unstructured subtype semantics;
- public property, permission, or link-creation APIs; and
- FUSE and remote memory mapping.

Future work may add one of these only through a new published capability
contract and implementation gates.

## 17. Stability and publication policy

This RFC becomes a release claim only after every required gate passes and the
release evidence records the tested OpenCADC, fsspec, Python, HTTPX, RESpx, and
scientific-stack versions.

Now that v0.3.0 is released:

- patch releases **MUST NOT** rename constructor options, remove a supported
  matrix row, weaken an exception guarantee, or change path identity;
- a capability or constructor contract change requires a new minor version;
  and
- an implementation fix that restores this document's behavior may ship in a
  patch release.

## Appendix A. Informative source evidence

The following sources explain why the contract has this shape; they are not
additional requirements:

- [`opencadc/vos` supported API audit](../research/opencadc-vos-supported-api.md)
- [fsspec and scientific-stack matrix](../research/vosfs-v030-fsspec-matrix.md)
- [HTTPX transport contract](../research/vosfs-v030-httpx-contract.md)
- [integration-test plan](../research/vosfs-v030-integration-test-plan.md)
- [initial cross-project gap analysis](../research/archive/vosfs-contract-gap-analysis.md)
- [IVOA VOSpace 2.1 fidelity evaluation](../research/vosfs-ivoa-2.1-evaluation.md)
- [OpenCADC synchronous transfer wiring](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/WEB-INF/web.xml#L147-L175)
- [OpenCADC push/pull integration test](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-test-vos/src/main/java/org/opencadc/conformance/vos/TransferTest.java#L147-L274)
- [`vostools` upload path](https://github.com/opencadc/vostools/blob/e54b472581d67b4c1db533cab95e955d2e9a7c5a/vos/vos/vos.py#L1904-L1919)
- [HTTPX async client documentation](https://www.python-httpx.org/async/)
- [RESPX user guide](https://lundberg.github.io/respx/guide/)
