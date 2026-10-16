# `vosfs` v0.3.0 HTTPX transport contract

Status: **Informative research recommendation.** `docs/design/trd.md` is the
sole normative v0.3.0 contract. Capitalized requirement words below restate the
recommended transport constraints and defer to that RFC if they differ.

## Pinned evidence and scope

This contract was checked against:

- OpenCADC `vos` commit
  [`cf976ce8141dd3341631b7f3e07aa38443d42f58`](https://github.com/opencadc/vos/tree/cf976ce8141dd3341631b7f3e07aa38443d42f58);
- HTTPX 0.28.1 commit
  [`26d48e0634e6ee9cdc0533996db289ce4b430177`](https://github.com/encode/httpx/tree/26d48e0634e6ee9cdc0533996db289ce4b430177);
- fsspec 2026.6.0 commit
  [`a2457004d03e0312f715f90f58873de5ab195a37`](https://github.com/fsspec/filesystem_spec/tree/a2457004d03e0312f715f90f58873de5ab195a37).

The v0.3.0 transport surface is the OpenCADC path needed by an fsspec backend:

- node metadata and mutations through `/nodes/*`;
- synchronous `pushToVoSpace` and `pullFromVoSpace` negotiation through
  `/synctrans`; and
- byte `HEAD`, `GET`, and `PUT` through the exact `/files/*` endpoint returned
  by negotiation, including pre-authorized file URLs.

OpenCADC maps `/nodes`, `/files`, `/synctrans`, and `/transfers` as real
servlets, so `/transfers` is not globally unimplemented. The v0.3.0 read/write
path uses `/synctrans`, but all asynchronous UWS resources, including
`/transfers` and `/async-delete`, are deliberately out of scope.  ([servlet mappings](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/WEB-INF/web.xml#L260-L279),
[client direction selection](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-client/src/main/java/org/opencadc/vospace/client/VOSpaceClient.java#L402-L415),
[synchronous negotiation](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-client/src/main/java/org/opencadc/vospace/client/VOSpaceClient.java#L505-L572))

## Decision

HTTPX is sufficient, but one `AsyncClient` is not sufficient for every
supported credential route.  v0.3.0 SHOULD use a lazy pool keyed by TLS
configuration.  In ordinary use the pool contains:

1. one client with the default validating TLS context and no client
   certificate, used for anonymous, bearer-token, and pre-authorized requests;
2. when X.509 is configured, one client whose validating `SSLContext` has the
   caller's certificate chain loaded.

Bearer credentials, including OIDC access tokens, are request headers, not
client-pool keys. Origin is
not a pool key because HTTPX already pools connections by origin inside one
transport.  TLS configuration is a key because HTTPX installs one SSL context
on the entire connection pool, not per request.  ([HTTPX async transport](https://github.com/encode/httpx/blob/26d48e0634e6ee9cdc0533996db289ce4b430177/httpx/_transports/default.py#L279-L311),
[client-certificate configuration](https://github.com/encode/httpx/blob/26d48e0634e6ee9cdc0533996db289ce4b430177/docs/advanced/ssl.md#L60-L70))

The no-certificate client is required even for an X.509-configured filesystem:
OpenCADC can return an anonymous, pre-authorized `/files/preauth:<token>/...`
endpoint.  Sending the caller's certificate or bearer token to that endpoint
would violate the negotiated security boundary.  ([OpenCADC endpoint generation](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/CavernURLGenerator.java#L217-L247),
[pre-authorized path parsing](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/FileAction.java#L150-L170))

## Public configuration and serialization

The transport-facing constructor state MUST consist only of JSON-safe values:

- `endpoint_url`: required absolute `http` or `https` URL supplied by the
  caller; no registry lookup. Bearer and X.509 credentials require `https`.
- `token`: optional literal bearer token, including an OIDC access token.
- `tokenfile`: optional path whose bearer token is read immediately before
  each authenticated request.
- `certfile`: optional combined certificate-chain and private-key PEM path for
  X.509.
- `timeouts`: optional mapping of finite numeric `connect`, `read`, `write`,
  and `pool` inactivity limits.
- `trust_env`: boolean controlling HTTPX proxy and CA environment handling;
  defaults to `True`.

The matching environment fallbacks are `VOSFS_TOKEN`, `VOSFS_TOKEN_FILE`, and
`VOSFS_CERT_FILE`. If any explicit credential argument is present, environment
credential variables are ignored. Otherwise, exactly zero or one environment
source may be set. Zero sources means anonymous access; conflicting sources
MUST fail during construction. An environment token is reread before every
authenticated request, as is the content named by `tokenfile`.

All access tokens use `Authorization: Bearer <token>`. `vosfs` consumes an
access token and MUST NOT acquire or refresh one. A rotating environment token
or `tokenfile` is the v0.3.0 refresh seam. The Authorization header is the
recommended bearer-token transport and tokens must be protected from
disclosure.  ([RFC 6750 section 2.1](https://www.rfc-editor.org/rfc/rfc6750.html#section-2.1),
[RFC 6750 security considerations](https://www.rfc-editor.org/rfc/rfc6750.html#section-5))

`token`, `tokenfile`, and `certfile` are mutually exclusive. X.509 MUST build a
fresh validating `ssl.SSLContext` lazily and call
`load_cert_chain(certfile)`; HTTPX's older `cert=` argument is deprecated.
([HTTPX SSL construction](https://github.com/encode/httpx/blob/26d48e0634e6ee9cdc0533996db289ce4b430177/httpx/_config.py#L23-L69))

The following MUST NOT appear in `storage_options`: `AsyncClient`,
`AsyncHTTPTransport`, `SSLContext`, response/request objects, streams, locks,
callables, or the live client pool.  There MUST NOT be a public `client_kwargs`
escape hatch in v0.3.0.  fsspec pickle reconstruction uses only constructor
arguments and `storage_options`, while its JSON representation serializes the
same options and warns that tokens are included.  ([fsspec reduction](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/spec.py#L180-L193),
[fsspec JSON contract and warning](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/spec.py#L1444-L1500),
[JSON encoder](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/json.py#L1-L34))

A literal `token` is consequently present in pickle and JSON output. The user
documentation MUST say so and SHOULD recommend `tokenfile` or environment
configuration. Environment-derived secrets MUST NOT be copied into
`storage_options`.

## Service binding discovery

The first filesystem I/O MUST fetch `endpoint_url + "/capabilities"` and cache
the parsed VOSI document for the filesystem instance. The client MUST resolve
only the approved node and synchronous-transfer bindings and MUST validate the
configured credential source
against each binding's advertised security methods. A missing binding disables
only its dependent operation; the client MUST NOT guess an operation URL or
probe `/protocols`, `/views`, or `/properties`. Directory-cache invalidation
MUST NOT refresh this service-binding cache.

## Client creation and lifecycle

- The filesystem MUST retain fsspec instance caching with `cachable=True`.
- Clients MUST be created on first request, never in `__init__`.
- Lazy creation MUST be concurrency-safe and MUST create at most one client per
  TLS key per filesystem instance and event loop.
- One `AsyncClient` MAY be shared by concurrent tasks; a client MUST NOT be
  created inside a per-request loop.  ([HTTPX task-sharing contract](https://github.com/encode/httpx/blob/26d48e0634e6ee9cdc0533996db289ce4b430177/httpx/_client.py#L1307-L1318),
  [pooling guidance](https://github.com/encode/httpx/blob/26d48e0634e6ee9cdc0533996db289ce4b430177/docs/async.md#L47-L65))
- Every client MUST use `follow_redirects=False`, no client-level auth, no
  client-level `Authorization` header, and no caller-supplied cookie jar.
- Requests MUST be constructed with explicit absolute URLs and explicit
  headers.  Auth and cookies MUST NOT be inherited from client defaults.
- `aclose()` MUST be public, idempotent, close every realized client, clear the
  pool, evict the instance from fsspec's instance cache, and make later I/O
  fail as closed. A synchronous `close()` MUST bridge to `aclose()` through
  the filesystem's fsspec loop. HTTPX closes its
  transports idempotently, and fsspec's sync bridge submits a coroutine to the
  bound I/O loop.  ([HTTPX close](https://github.com/encode/httpx/blob/26d48e0634e6ee9cdc0533996db289ce4b430177/httpx/_client.py#L1978-L1988),
  [fsspec sync bridge](https://github.com/fsspec/filesystem_spec/blob/a2457004d03e0312f715f90f58873de5ab195a37/fsspec/asyn.py#L63-L119))
- A finalizer MAY warn or schedule best-effort cleanup, but MUST NOT be the
  primary lifecycle because async close must be awaited.
- Pickle and fsspec JSON reconstruction MUST restore only primitive constructor
  options. The reconstructed instance MUST have fresh HTTP clients, loop,
  locks, capability bindings, and directory cache; environment credential
  sources MUST be resolved again.

## Request contract

### Node and negotiation XML

- XML request bodies MUST be UTF-8 bytes with
  `Content-Type: text/xml; charset=utf-8` and `Accept: text/xml`.
- XML responses MUST be bounded before parsing and MUST be closed on every
  success, error, parse failure, and cancellation path.
- Bearer-token requests MUST add exactly one per-request Authorization header.
  Anonymous requests MUST add none.

OpenCADC's client sends node and transfer documents as UTF-8 `text/xml`, and
the server defaults node responses to `text/xml`.  ([node request](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-client/src/main/java/org/opencadc/vospace/client/VOSpaceClient.java#L297-L316),
[transfer request](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-client/src/main/java/org/opencadc/vospace/client/VOSpaceClient.java#L533-L540),
[server media type](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/actions/NodeAction.java#L208-L229))

### Redirects and credential routing

- Automatic redirect following MUST remain disabled.
- A 303 from `/synctrans` MUST be interpreted as a negotiated result or byte
  endpoint.  It MUST NOT blindly replay the POST body or be treated as an
  ordinary page redirect.  OpenCADC returns both direct endpoint and transfer
  details locations this way.  ([transfer redirects](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/transfers/TransferRunner.java#L390-L430))
- The client MUST follow only the synchronous negotiation result needed to
  obtain transfer details or a byte endpoint. It MUST NOT start, poll, abort,
  or manage an asynchronous UWS job.
- Redirect targets MUST be absolute `http` or `https` URLs without userinfo.
  Bearer-token targets MUST use `https`. Loops and more than five hops MUST
  fail.
- An anonymous or pre-authorized endpoint MUST use the no-certificate client
  and receive neither Authorization nor Cookie, regardless of origin.
- A token endpoint MUST use the no-certificate client plus a freshly resolved
  bearer header only when its negotiated method is
  `ivo://ivoa.net/sso#token`. Cross-origin token endpoints MUST use HTTPS.
- An X.509 endpoint MUST use the certificate client only when its negotiated
  method is `ivo://ivoa.net/sso#tls-with-certificate`, regardless of origin,
  and MUST use HTTPS.
- When no returned endpoint matches the configured credential source, the
  transfer MUST fail before byte I/O.

HTTPX itself strips Authorization on most cross-origin redirects but preserves
the remaining headers, changes 303 to GET, and sources cookies from its client
jar.  Manual handling is required here because negotiation, upload, and
pre-authorization need stricter semantics.  ([HTTPX redirect method and headers](https://github.com/encode/httpx/blob/26d48e0634e6ee9cdc0533996db289ce4b430177/httpx/_client.py#L494-L582))

### Byte reads and range fallback

- `/files` requests MUST set `Accept-Encoding: identity` and consume raw bytes,
  not HTTPX's decoded byte iterator.  OpenCADC exposes a node's stored
  `Content-Encoding`; automatic decompression would change filesystem bytes.
  ([OpenCADC response metadata](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/HeadAction.java#L147-L176),
  [HTTPX default content codings](https://github.com/encode/httpx/blob/26d48e0634e6ee9cdc0533996db289ce4b430177/httpx/_client.py#L117-L122),
  [raw async iterator](https://github.com/encode/httpx/blob/26d48e0634e6ee9cdc0533996db289ce4b430177/httpx/_models.py#L1037-L1063))
- v0.3.0 MUST classify OpenCADC range reads as unsupported.  Its GET action
  opens the file and copies the complete stream, with no Range parsing or 206
  response path.  ([OpenCADC GET implementation](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/GetAction.java#L102-L132))
- fsspec `start`/`end` reads MUST therefore issue an ordinary whole-object GET
  without a `Range` header, then slice locally above the HTTP adapter. The
  capability document MUST not claim network-efficient random access.
- `HEAD /files/...` 200 supplies metadata.  `GET` 200 supplies bytes and 204 is
  a valid empty file.  All streamed responses MUST use an async context or an
  unconditional `Response.aclose()` finally path.  ([HTTPX streaming cleanup](https://github.com/encode/httpx/blob/26d48e0634e6ee9cdc0533996db289ce4b430177/docs/async.md#L67-L105))

### Upload streaming and replay

- `PUT /files/...` MUST use an async byte iterator and bounded buffers.
  `Content-Length` MUST be supplied when known; otherwise HTTPX chunked
  transfer is allowed. `Content-Type` MUST use the caller's value when
  supplied and otherwise default to `application/octet-stream`.
- A 201 response is success.  If the request carries an expected MD5 digest,
  a 412 is integrity failure and MUST be surfaced; a response digest, when
  present, MUST be checked.
- Every 3xx response outside the approved `/synctrans` 303 interpretation MUST
  fail. In particular, a redirect from the byte PUT MUST never be followed.
- Each async generator is single-use.  Any explicit replay MUST construct a
  fresh body from byte zero; HTTPX raises `StreamConsumed` when an async
  generator is iterated again.  ([HTTPX async request streaming](https://github.com/encode/httpx/blob/26d48e0634e6ee9cdc0533996db289ce4b430177/docs/async.md#L107-L116),
  [one-shot stream enforcement](https://github.com/encode/httpx/blob/26d48e0634e6ee9cdc0533996db289ce4b430177/httpx/_content.py#L67-L89))
- v0.3.0 MUST NOT automatically replay uploads.  OpenCADC truncates the target,
  streams the body, computes MD5, returns 201, and truncates again after a
  failed integrity check; a transport failure can therefore leave an
  ambiguous mutation.  ([OpenCADC PUT implementation](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/PutAction.java#L218-L249),
  [success and cleanup](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/PutAction.java#L287-L323))

## Timeouts, retries, and cancellation

- Every client MUST use an explicit HTTPX timeout with default inactivity
  limits: connect 10 seconds, pool 10 seconds, read 60 seconds, and write 60
  seconds.  The values MUST be independently configurable as finite positive
  numbers.  HTTPX defines these as four distinct timeout classes.
  ([HTTPX timeout semantics](https://github.com/encode/httpx/blob/26d48e0634e6ee9cdc0533996db289ce4b430177/docs/advanced/timeouts.md#L41-L68))
- `AsyncHTTPTransport(retries=0)` MUST be used.  v0.3.0 performs no automatic
  status, connection, XML mutation, negotiation POST, or upload retry.  HTTPX's
  transport retry knob covers only connect errors/timeouts, not the broader
  operation semantics.  ([HTTPX retry boundary](https://github.com/encode/httpx/blob/26d48e0634e6ee9cdc0533996db289ce4b430177/docs/advanced/transports.md#L1-L23))
- A token file is read before each request, so v0.3.0 does not need a hidden
  401 refresh retry.  Static-token 401 responses MUST not be retried.
- Cancellation MUST propagate unchanged.  It MUST close any active response,
  remove any operation-owned staged temporary file, MUST NOT start a replay,
  and MUST NOT leave a background transfer task or issue a cleanup request.
  HTTPX's stream context closes responses in `finally`; the adapter MUST retain
  that structure.  ([HTTPX stream context](https://github.com/encode/httpx/blob/26d48e0634e6ee9cdc0533996db289ce4b430177/httpx/_client.py#L1542-L1592))

## Error translation and uncertain writes

- Invalid input MUST raise `ValueError`; authentication or authorization
  failure MUST raise `PermissionError`; missing nodes MUST raise
  `FileNotFoundError`; conflicts MUST raise `FileExistsError`; unsupported
  operations MUST raise `NotImplementedError`; quota MUST raise
  `OSError(errno.ENOSPC)`; and lock or busy faults MUST raise
  `BlockingIOError`.
- One public `VOSpaceError(OSError)` MUST carry the HTTP status, symbolic
  OpenCADC fault, retry guidance, and bulk partial-completion details for every
  remaining failure.
- Error bodies MUST be limited to 8 KiB before parsing or reporting and MUST
  redact credentials and pre-authorized URL tokens.
- A failed byte PUT MUST be reported as an uncertain write that may have
  truncated the destination. `vosfs` MUST NOT issue a cleanup DELETE, retry the
  upload, or conceal that uncertainty.

## Required test seams and acceptance cases

The implementation SHOULD have one internal HTTP adapter seam.  Production
constructs `AsyncHTTPTransport`; unit tests inject an `AsyncBaseTransport` or
`MockTransport`.  The transport factory is test-internal and MUST NOT enter
`storage_options`.  HTTPX's official mock accepts an async handler and reads the
request body before invoking it.  ([MockTransport](https://github.com/encode/httpx/blob/26d48e0634e6ee9cdc0533996db289ce4b430177/httpx/_transports/mock.py#L15-L35))

The RFC acceptance suite MUST cover:

1. anonymous, literal `token`, `VOSFS_TOKEN`, explicit and environment
   `tokenfile`, and explicit and environment combined-PEM `certfile`;
2. exactly one lazy client per realized TLS key under concurrent first use;
3. pickle and fsspec JSON round trips before and after client creation, with no
   live state retained;
4. negotiated token routing on same-origin and HTTPS cross-origin endpoints,
   anonymous/pre-authorized no-credential routing, and negotiated
   X.509-versus-no-certificate client selection;
5. 303 `/synctrans` interpretation without POST replay or asynchronous UWS
   lifecycle;
6. UTF-8 XML headers and bounded error bodies;
7. raw byte identity when `Content-Encoding` is present, 204 empty files,
   absence of the `Range` header, and whole-object slicing;
8. bounded streaming PUT, 201 success, 412 digest failure, one-shot body
   rejection, cancellation, and no automatic replay;
9. connect/read/write/pool timeout mapping and zero automatic retries; and
10. idempotent async close, synchronous close bridge, response closure on every
    error path, and failure of I/O after close.

`MockTransport` is sufficient for request shape, redirects, auth routing, and
close assertions.  A local HTTPS server with test certificates is required for
real mTLS presentation, cross-origin TLS behavior, chunked streaming,
backpressure, and timeout/cancellation tests; a buffering mock cannot prove
those properties.

## Explicitly outside this transport contract

- credential acquisition, OIDC flows, refresh-token exchange, cookies, HTTP
  Basic, and delegated proxy headers;
- `/transfers`, `/async-delete`, and every asynchronous UWS lifecycle;
- server-side move, copy, and third-party transfer;
- network-efficient Range support on OpenCADC `/files`; and
- append, update, offset, resumable, or multipart uploads; and
- automatic mutation or upload retries.
