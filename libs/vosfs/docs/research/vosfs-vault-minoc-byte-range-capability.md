# vault/minoc vs. arc/cavern: byte-range support and capability delta

<!-- pyml disable line-length -->

Researched: 2026-07-24 (America/Vancouver)
Probed deployments: `https://cadc-west-01.canfar.net/vault`, `https://ws-uv.canfar.net/arc`
Client under test: `vosfs` @ `fa194eb`

Status: **Informative evidence** (probed 2026-07-24). Live claims below are
unchanged. Contract/code follow-up: TRD §8 +
[ADR 0007](../../../../docs/adr/0007-validate-range-support-from-206.md) now require
response-validated `Range` for partial `cat_*` (`206` keep / `200` fallback).
Staged `open` and `blockcache::` remain whole-object / unsupported.

**Questions.**

1. Does `cadc-west-01/vault` support HTTP byte ranges, and does its header
   response differ from `arc`?
2. How does this deployment differ from what `VOSpaceFileSystem` implements —
   extra capabilities, or missing ones?

## 1. Executive summary

- **Yes — the two deployments differ, and decisively.** `vault` bytes are served
  by **minoc**, which honours `Range` with a correct `206`. `arc` bytes are
  served by **cavern**, which **silently ignores `Range` and returns `200` with
  the whole object**. This is the header difference hypothesized in the request,
  confirmed by a real GET probe rather than by `Accept-Ranges` alone.
- **Range is per-backend, not per-profile.** Cavern ignores `Range` (`200` whole
  body); minoc honours it (`206`). The old TRD blanket ban was Cavern-true and
  profile-false; TRD §8 / ADR 0007 now decide from the byte response.
- **One live defect found.** `vosfs` **cannot use the anonymous credential
  against any current OpenCADC deployment**, `vault` or `arc`, because
  `capabilities._accepts` discards the empty `<securityMethod />` element that is
  precisely the IVOA encoding for "anonymous is supported". `vault`'s public
  archive needs no credential, yet `vosfs` refuses it. See §4.
- **`vault` is anonymous-readable end to end** — nodes, `synctrans`
  negotiation, and the byte GET all succeed with no credential.

## 2. Deployment identity

The two services are different implementations, not two instances of one.

| | `arc` | `vault` (`cadc-west-01`) |
| --- | --- | --- |
| VOSpace REST layer | `OpenCADC/cadc-rest + vos/cavern-0.10` | `OpenCADC/cadc-rest + storage-inventory/vault-1.0` |
| Byte server | cavern itself, same host | **minoc** (`storage-inventory/minoc-1.0`), *different* host |
| VOSpace authority | `cadc.nrc.ca~arc` | `cadc.nrc.ca~vault` |
| Backing store | POSIX filesystem | storage-inventory artifact store |
| Anonymous read | no (`/arc/files` → 400/403) | **yes**, fully |

This matters because `vosfs`'s server boundary is pinned to `opencadc/vos`
commit `cf976ce8`, i.e. **cavern**. minoc lives in a different repository
(`opencadc/storage-inventory`) and is outside the audited boundary. The
capability differences below follow from that.

## 3. The byte-range answer

### 3.1 Header response, side by side

`HEAD` on the negotiated byte endpoint:

| Header | `arc` (cavern) | `vault` (minoc) |
| --- | --- | --- |
| `accept-ranges` | **absent** | **`bytes`** |
| `digest` | absent | **`md5=qYdJhrb3YzQflVK8Dm2Vpg==`** |
| `x-artifact-id` | absent | **`e7d228a7-…`** |
| `content-length` | `8404` | `14400` |
| `last-modified` | present | present |
| `content-disposition` | present | present |

So the request's expectation is right: the header response does distinguish the
two, and `accept-ranges: bytes` is the marker.

### 3.2 But `Accept-Ranges` alone is not proof — so it was probed

This repo's own archived analysis already flags the trap
([`archive/vosfs-contract-gap-analysis.md:134`](archive/vosfs-contract-gap-analysis.md)):
`Accept-Ranges` is advisory under [RFC 9110 §14.3](https://www.rfc-editor.org/rfc/rfc9110.html#section-14.3),
and capability must be established by a real `GET` with validation of `206`,
`Content-Range`, and body length. That probe was run.

**`vault` → minoc, `Range: bytes=0-9` on a 14 400-byte object:**

```text
HTTP/1.1 206
accept-ranges: bytes
content-range: bytes 0-9/14400
content-length: 10
→ 10 bytes received: "SIMPLE  = "
```

**`arc` → cavern, `Range: bytes=0-9` on an 8 404-byte object:**

```text
HTTP/1.1 200 OK
content-length: 8404
server: OpenCADC/cadc-rest + vos/cavern-0.10
→ 8404 bytes received  (the entire object)
```

Range support on minoc is genuine: `206`, well-formed `Content-Range` with the
correct total, and a body of exactly the requested length. Verified on both a
small object and a 15 387 840-byte object (`content-range: bytes 0-9/15387840`).

Cavern's non-support was confirmed three ways, so it is not an artifact of the
pre-authorized URL form: the `preauth:` endpoint, the plain
`/arc/files/<path>` endpoint with a client certificate, and a suffix range
(`Range: bytes=-100`) all returned `200` with all 8 404 bytes.

**The cavern behaviour is the dangerous one.** Silently ignoring `Range` and
answering `200` with the full body means a client that assumed partial-content
semantics would corrupt reads. Correct client rule (now TRD §8 / ADR 0007):
keep a partial body only on validated `206`; treat `200` as whole-object and
slice locally.

### 3.3 Cost of whole-object strategy on `vault` (pre-Range client)

Measured 2026-07-24 against `vault` with a client certificate, reading 10 bytes
from a 15.4 MB object (client then always downloaded whole):

| Path | Bytes over the wire | Wall clock |
| --- | --- | --- |
| `vosfs` `cat_ranges([...], [0], [10])` (pre-fix) | 15 387 840 | 2.59 s |
| Direct minoc `Range: bytes=0-9` | 10 | 0.16 s |

### 3.4 Contract resolution

Detection must be a real `206` (plus `Content-Range` / body length), not
`Accept-Ranges`. That is now normative in TRD §8 and ADR 0007 for
`cat_file` / `cat_ranges`. Staged `open` is still whole-object.

## 4. Defect: anonymous access is rejected against every real deployment

`vault` needs no credential. `vosfs` refuses it anyway:

```python
fs = fsspec.filesystem("vos", endpoint_url="https://cadc-west-01.canfar.net/vault")
fs.info("/APASS/north/091106/n091106.0101.wcs")
# PermissionError: the ivo://ivoa.net/std/VOSpace/v2.0#nodes binding does not
# advertise a supported security method for the configured credential
```

**Root cause.** Both deployments advertise anonymous with an explicit *empty*
`<securityMethod />` element listed *alongside* the authenticated methods:

```xml
<interface xsi:type="vs:ParamHTTP" role="std">
  <accessURL use="base">https://cadc-west-01.canfar.net/vault/nodes</accessURL>
  <securityMethod />                                              <!-- anonymous -->
  <securityMethod standardID="ivo://ivoa.net/sso#cookie" />
  <securityMethod standardID="ivo://ivoa.net/sso#tls-with-certificate" />
  <securityMethod standardID="ivo://ivoa.net/sso#token" />
</interface>
```

[`capabilities._accepts`](../../src/vosfs/capabilities.py) collects the advertised
`standardID`s, **discards the empty string**, and then treats anonymous as "the
interface had no security methods at all":

```python
advertised.discard("")
if security_method == ANONYMOUS_METHOD:
    return not advertised          # False whenever ANY authenticated method is listed
```

Because `cookie`/`cert`/`token` remain in the set, `not advertised` is `False`,
no interface matches, and `_resolve` raises `PermissionError`. The module
docstring encodes the same conflation — "The empty string is the anonymous
method (an interface with no `securityMethod` children)" — merging two distinct
wire encodings:

1. *no* `securityMethod` children → anonymous-only interface;
2. an explicit empty `<securityMethod />` → anonymous is **one of several**
   accepted methods.

Only (1) is handled. Note the package is already inconsistent with itself:
[`negotiate._security_method_of`](../../src/vosfs/negotiate.py) correctly maps a
missing `standardID` to `ANONYMOUS_METHOD` for transfer *protocols*, which is why
byte negotiation works once bindings resolve.

**Impact.**

- The anonymous credential path is effectively dead code against production.
  Both `arc` and `vault` advertise the empty element, so *no* current OpenCADC
  deployment resolves anonymously.
- `vault`'s entire public archive (`ALMA`, `APASS`, `CFHTSG`, …, all
  `ispublic=true`) is unreachable through `vosfs` without a certificate it does
  not need.
- The failure is a `PermissionError` at capability-parse time, before any I/O, so
  it reads like a credential problem rather than a client parsing bug.

**Confirmed working with a credential.** Identical endpoint with
`certfile=~/.ssl/cadcproxy.pem`:

```text
credential: certificate
info: {'name': '/APASS/north/091106/n091106.0101.wcs', 'type': 'file', 'size': 14400}
cat_file[0:10] = b'SIMPLE  = '
```

So `vault` is otherwise fully supported by the existing implementation — nodes,
listing, negotiation, and byte reads all work unmodified. The anonymous gap is
the only hard blocker.

## 5. Capability delta

### 5.1 Capabilities `vault` offers that `vosfs` does not use

| Capability | Evidence | Note |
| --- | --- | --- |
| **HTTP `Range`/206** | §3.2 | Adopted for partial `cat_*` (TRD §8 / ADR 0007). Staged `open` still whole-object. |
| **`digest: md5=` on read responses** | minoc `HEAD`/`GET` | [`_integrity.verify_returned_digest`](../../src/vosfs/_integrity.py) exists but is wired only into `write_whole`. Reads are unverified even when the server supplies the digest. |
| **`HEAD` on the byte endpoint** | returns `content-length` + `digest` | Could answer size/checksum without a node `GET`. `vosfs` always reads the node document. |
| **`/vault/files/<path>` one-hop `303`** | `303` → minoc, `accept-ranges: bytes` on the redirect, and **`Range` is forwarded** to yield `206` | 2 requests vs. 4 for full `synctrans` negotiation. Direct evidence for "Option B" in [`vosfs-transfer-endpoint-variability.md`](vosfs-transfer-endpoint-variability.md), which currently discusses direct-`/files` without any `vault`/minoc data. |
| **Multi-site endpoints** | `transferDetails` returns two pre-auth endpoints on *different* hosts (`ws-uv`, `ws-cadc`) | Failover/locality opportunity. [`choose_protocol`](../../src/vosfs/negotiate.py) takes the first match and never retries the second. `arc` instead returns pre-auth + non-pre-auth on the *same* host. |
| **`core#MD5`, `core#content-date` node properties** | `vault` DataNode documents carry both; `arc` DataNodes carry neither | Already surfaced as `info["md5"]` via [`nodes.to_info`](../../src/vosfs/nodes.py). A field that is populated on `vault` and always `None` on `arc` — free win, no change needed. |

### 5.2 Capabilities `arc` has that `vault` lacks

| standardID | `arc` | `vault` |
| --- | --- | --- |
| `vos://cadc.nrc.ca~vospace/CADC/std/Pkg-1.0` (`/arc/pkg`, bulk zip/tar) | `full` | **absent** |

`vosfs` binds neither, so this is informational — but it rules out any future
package-download shortcut being portable across deployments.

### 5.3 Latent portability trap: `accessURL use=` disagrees

Both services advertise the same capability set otherwise, but **not with the
same `use` attribute**:

| standardID | `arc` `use` | `vault` `use` |
| --- | --- | --- |
| `VOSpace#recursive-delete-proto` | `full` | **`base`** |
| `VOSpace#recursive-nodeprops-proto` | `full` | **`base`** |
| `v2.0#nodes` | `base` | `base` |
| `VOSpace#sync-2.1` | `full` | `full` |
| `VOSpace#files-proto` | `base` | `base` |

[`capabilities._access_url`](../../src/vosfs/capabilities.py) is deliberately
strict — it will not substitute a `base` URL for a requested `full` one, and its
docstring correctly explains why ("a `base` URL is meant to have a node path
appended, a `full` URL is used verbatim"). The two bindings `vosfs` actually
resolves (`nodes` → `base`, `sync-2.1` → `full`) agree across both servers, so
**nothing is broken today**.

The trap is for later: any future binding of `async-delete` or `async-setprops`
with a hard-coded `use=` would resolve on one deployment and silently return
`None` on the other — surfacing as a `NotImplementedError` about a missing
capability on a server that plainly advertises it. Whoever implements recursive
server-side delete should accept either `use` for these two, or resolve `use`
per deployment.

### 5.4 Node document shape

`arc` DataNodes include empty `<vos:accepts />` / `<vos:provides />`; `vault`
DataNodes include them too, so this is *not* a difference — but `vault` adds
`core#MD5` and `core#content-date` properties that `arc` omits entirely.
[`nodes._node_from_element`](../../src/vosfs/nodes.py) reads properties by URI
and tolerates absence, so both parse cleanly.

## 6. Reproduction

Anonymous, no credential needed for every `vault` command:

```bash
curl -sS -I 'https://cadc-west-01.canfar.net/vault/files/APASS/north/091106/n091106.0101.wcs'
```

Full negotiation then range probe:

```bash
printf '<?xml version="1.0" encoding="UTF-8"?>\n<vos:transfer xmlns:vos="http://www.ivoa.net/xml/VOSpace/v2.0">\n<vos:target>vos://cadc.nrc.ca~vault/APASS/north/091106/n091106.0101.wcs</vos:target>\n<vos:direction>pullFromVoSpace</vos:direction>\n<vos:protocol uri="ivo://ivoa.net/vospace/core#httpsget"/>\n</vos:transfer>\n' > /tmp/xfer.xml
LOC=$(curl -sS -o /dev/null -D - -X POST 'https://cadc-west-01.canfar.net/vault/synctrans' -H 'Content-Type: text/xml' --data-binary @/tmp/xfer.xml | awk 'tolower($1)=="location:"{print $2}' | tr -d '\r')
EP=$(curl -sS -L "$LOC" | grep -oE 'https://ws-uv[^<]*' | head -1)
curl -sS -D - -o /dev/null -H 'Range: bytes=0-9' "$EP"
```

The single-request equivalent, showing `Range` passthrough through the `303`:

```bash
curl -sS -L -D - -o /dev/null -H 'Range: bytes=0-9' 'https://cadc-west-01.canfar.net/vault/files/APASS/north/091106/n091106.0101.wcs'
```

## 7. Open questions for the contract owner

1. ~~Range policy~~ — resolved: response-validated `206` for partial `cat_*`
   (TRD §8 / ADR 0007). Staged `open` / `blockcache::` still open if desired.
2. Should the anonymous `<securityMethod />` fix (§4) land as a standalone bug
   fix? It still blocks all anonymous use.
3. Does the server boundary widen from `opencadc/vos` (cavern) to include
   `opencadc/storage-inventory` (minoc/vault)? §5 is only actionable if it does.
4. Should read-path `digest: md5=` verification be adopted where offered, given
   `_integrity` already has the decoder?

## 8. Sources

- Live responses from `https://cadc-west-01.canfar.net/vault` and
  `https://ws-uv.canfar.net/arc`, 2026-07-24 (quoted verbatim above).
- [RFC 9110 §14 — Range Requests](https://www.rfc-editor.org/rfc/rfc9110.html#section-14)
- [IVOA VOSpace 2.1 Recommendation](https://www.ivoa.net/documents/VOSpace/20180620/REC-VOSpace-2.1.html)
- [`opencadc/storage-inventory`](https://github.com/opencadc/storage-inventory) — minoc/vault
- [`opencadc/vos`](https://github.com/opencadc/vos) — cavern, the audited boundary
- In-repo: [`../design/trd.md`](../design/trd.md) §8,
  [`docs/adr/0007-validate-range-support-from-206.md`](../../../../docs/adr/0007-validate-range-support-from-206.md),
  [`vosfs-transfer-endpoint-variability.md`](vosfs-transfer-endpoint-variability.md),
  [`archive/vosfs-contract-gap-analysis.md`](archive/vosfs-contract-gap-analysis.md) L134
