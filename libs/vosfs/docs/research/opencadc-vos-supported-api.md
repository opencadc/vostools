# `opencadc/vos` supported API surface

<!-- pyml disable line-length -->

## Scope and evidence

Status: **Informative server audit.** This note records what OpenCADC
implements. `docs/design/trd.md` is the sole normative `vosfs` v0.3.0 contract
and controls every product-scope consequence.

This note bounds the `vosfs` contract to behavior implemented by the deployable
`cavern` server in `opencadc/vos`. The repository also contains reusable model,
client, server, and conformance-test libraries; `vault` is described but is not
implemented in this repository. ([repository README](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/README.md#L16-L22))

The source snapshot is commit
[`cf976ce8141dd3341631b7f3e07aa38443d42f58`](https://github.com/opencadc/vos/commit/cf976ce8141dd3341631b7f3e07aa38443d42f58),
the Cavern 0.10.1 merge committed on 2026-07-08. Source and tests at that commit
are authoritative. Public staging observations below were made on 2026-07-10
and are corroboration only.

Status meanings:

- **Supported**: a concrete Cavern path reaches implementation code and is
  covered by an integration or conformance test.
- **Partial**: implementation exists, but a material sub-operation is rejected,
  normalized, configuration-dependent, or not covered by the Cavern tests.
- **Unsupported**: no servlet is mapped or the implementation deliberately
  rejects the operation.
- **Unknown**: the exact behavior lives in a dependency or deployment
  configuration and is not established by this repository.

## Correction: `/transfers` is implemented

`/transfers` is **not** globally “Not Implemented.” Cavern maps
`/transfers/*` to a UWS `JobServlet` accepting GET, POST, and DELETE, configured
with `AsyncTransferManager` and `InlineTransferHandler`.
`AsyncTransferManager` executes `TransferRunner` in a six-thread executor.
([servlet and manager](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/WEB-INF/web.xml#L70-L95),
[mapping](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/WEB-INF/web.xml#L276-L279),
[executor](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/uws/AsyncTransferManager.java#L81-L96))

The accurate statement is: **the async UWS transfer resource is implemented,
but its transfer-direction surface is partial**. Push-to-VOSpace,
pull-from-VOSpace, same-service move, and a configuration-dependent
bidirectional/SSHFS negotiation path reach implementations. Pull-to-VOSpace and
push-from-VOSpace throw `UnsupportedOperationException`; internal copy throws
`copyNode is not implemented`.
([dispatch](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/transfers/TransferRunner.java#L271-L291),
[copy rejection](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/transfers/InternalTransferAction.java#L124-L138),
[pull-to rejection](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/transfers/PullToVOSpaceAction.java#L82-L90),
[push-from rejection](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/transfers/PushFromVOSpaceAction.java#L82-L90))

The staging `/capabilities` document advertises `/transfers`, and an anonymous
GET currently returns `403 anonymous job listing not permitted`, not a 404/501.
([staging capabilities](https://staging.canfar.net/arc/capabilities),
[staging transfers](https://staging.canfar.net/arc/transfers))

## Endpoint matrix

| Resource | Status | Implemented surface | Contract consequence |
|---|---|---|---|
| `/nodes/*` | **Supported** | GET metadata/listing, PUT create, POST property update, DELETE single node. ([wiring](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/WEB-INF/web.xml#L44-L68)) | v0.3.0 uses GET/PUT/DELETE publicly and retains a private, constrained POST node-update primitive; no generic public property API. |
| `/transfers/*` | **Partial** | Async UWS GET/POST/DELETE backed by `TransferRunner`; operation limits are in the transfer matrix below. ([wiring](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/WEB-INF/web.xml#L70-L95)) | Deliberately outside v0.3.0 with all asynchronous UWS, including native move. |
| `/synctrans/*` | **Partial** | Sync UWS GET/POST/DELETE, executes the same `TransferRunner`; POST executes immediately. ([wiring](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/WEB-INF/web.xml#L147-L176), [executor](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/uws/SyncTransferManager.java#L81-L95)) | Preferred negotiation path for push-to and pull-from. Do not infer support for rejected directions. |
| `/xfer/*` | **Supported, internal** | GET renders transfer details for UWS job results. ([mapping](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/WEB-INF/web.xml#L291-L294), [servlet contract](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/transfers/TransferDetailsServlet.java#L108-L133)) | Follow result links; do not construct `/xfer` as a public binding. |
| `/files/*` | **Supported extension** | HEAD/GET/PUT direct bytes. ([wiring](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/WEB-INF/web.xml#L189-L205)) | v0.3.0 consumes only an exact byte endpoint returned by `/synctrans`; it never constructs this URL. |
| `/async-delete/*` | **Supported extension** | UWS GET/POST/DELETE using `RecursiveDeleteJobManager`; integration tests cover recursive deletion and permission failures. ([wiring](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/WEB-INF/web.xml#L97-L118), [tests](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-test-vos/src/main/java/org/opencadc/conformance/vos/RecursiveNodeDeleteTest.java#L145-L258)) | Deliberately outside v0.3.0; recursive removal traverses client-side and uses leaves-first node DELETE. |
| `/async-setprops/*` | **Supported extension** | UWS GET/POST/DELETE with XML node input and a recursive property runner. ([wiring](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/WEB-INF/web.xml#L120-L145), [tests](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-test-vos/src/main/java/org/opencadc/conformance/vos/RecursiveNodePropsTest.java#L150-L337)) | Optional bulk metadata feature, not needed for baseline fsspec operations. |
| `/pkg/*` | **Supported extension** | Sync UWS GET streams package output after a package-view transfer; TAR and ZIP, single/multiple targets, containers, and links are tested. ([wiring](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/WEB-INF/web.xml#L207-L220), [flow](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/PackageDownload.md#L15-L31), [tests](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-test-vos/src/main/java/org/opencadc/conformance/vos/PackageTest.java#L236-L316)) | Optional bulk-download API; not ordinary file `open`. |
| `/capabilities`, `/availability` | **Supported** | VOSI GET/HEAD capability document and availability GET are wired. ([wiring](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/WEB-INF/web.xml#L222-L258), [mappings](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/WEB-INF/web.xml#L301-L311)) | v0.3.0 fetches `/capabilities` and resolves only node and synchronous-transfer bindings; `/availability` is not required. |
| `/protocols`, `/views`, `/properties` | **Unsupported** | They appear in the Swagger file but have no servlet mapping; the Java client methods all throw “Feature under construction.” ([only actual mappings](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/WEB-INF/web.xml#L260-L317), [client stubs](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-client/src/main/java/org/opencadc/vospace/client/VOSpaceClient.java#L418-L428)) | Do not probe or require these resources. Staging returned 404 for all three on 2026-07-10. |
| Search endpoint | **Unsupported** | The model defines a search standard ID, but Cavern maps no search servlet. ([standard IDs](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos/src/main/java/org/opencadc/vospace/VOS.java#L116-L130), [actual mappings](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/WEB-INF/web.xml#L260-L317)) | No glob/search acceleration contract. |

`/files`, `/async-delete`, and `/async-setprops` deliberately use `-proto`
standard IDs in the source. ([constants](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos/src/main/java/org/opencadc/vospace/VOS.java#L126-L130))

## Operation matrix

| Operation or behavior | Status | Pinned evidence | `vosfs` boundary |
|---|---|---|---|
| Get node metadata | **Supported** | GET returns XML by default and can render JSON for `Accept: application/json`; `detail=min` suppresses properties, while `detail=max` adds readable/writable tags. ([GET action](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/actions/GetNodeAction.java#L120-L250), [media types](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/actions/NodeAction.java#L208-L229)) | XML is the stable baseline; JSON need not be required. |
| List direct children | **Supported; Cavern ignores paging** | Container GET attaches an iterator; Cavern returns all children unless `limit=0`. A resume `uri` is rejected and positive limits are otherwise ignored by persistence. ([GET listing](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/actions/GetNodeAction.java#L151-L213), [persistence](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/nodes/FileSystemNodePersistence.java#L325-L344), [Cavern test flags](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/intTest/java/org/opencadc/cavern/NodesTest.java#L95-L104)) | Listings request a fixed `limit` and an inclusive `uri` cursor. A service that ignores `limit` and returns one document under the XML parse cap is the whole listing. Cavern still does not resume. |
| Sort/order listing | **Unsupported** | Any `sort` or `order` parameter throws `UnsupportedOperationException`. ([source](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/actions/GetNodeAction.java#L151-L163)) | Sort client-side when needed. |
| Create node | **Supported** | PUT creates and returns 201; normal creation assigns caller ownership and inherits configured parent permissions. ([create](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/actions/CreateNodeAction.java#L125-L228)) | Supports ContainerNode and DataNode creation used by v0.3.0; public link creation is outside scope. |
| Update metadata | **Supported** | POST merges mutable properties and permission fields, forbids type changes, and returns 200. ([update](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/actions/UpdateNodeAction.java#L96-L145), [merge](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/actions/UpdateNodeAction.java#L147-L196)) | v0.3.0 keeps a private primitive restricted to explicitly supplied mutable, non-administrative properties; there is no public metadata or permission mutation surface. |
| Delete a file or empty container | **Supported** | Node DELETE resolves one node and persistence uses `Files.delete`; successful DELETE is tested as 200. ([action](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/actions/DeleteNodeAction.java#L91-L116), [persistence](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/nodes/FileSystemNodePersistence.java#L512-L526), [test expectation](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-test-vos/src/main/java/org/opencadc/conformance/vos/VOSTest.java#L279-L295)) | Use node DELETE for non-recursive removal. |
| Delete non-empty container | **Unsupported via `/nodes`; supported via extension** | POSIX delete converts `DirectoryNotEmptyException` to an error; `/async-delete` recursively walks children. ([plain delete](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/nodes/FileSystemNodePersistence.java#L512-L526), [recursive runner](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/async/RecursiveDeleteNodeRunner.java#L136-L177)) | v0.3.0 explicitly walks children and deletes leaves-first; it never calls `/async-delete`. |
| Push-to-VOSpace upload negotiation | **Supported** | Negotiation creates/validates a DataNode and direct PUT endpoints are exercised with checksum rejection and success. ([negotiation](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/transfers/PushToVOSpaceNegotiation.java#L101-L156), [test](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-test-vos/src/main/java/org/opencadc/conformance/vos/TransferTest.java#L147-L231)) | Supported write path. |
| Pull-from-VOSpace download negotiation | **Supported** | Negotiation resolves LinkNodes to DataNodes, checks read permission, and tests download bytes and metadata. ([negotiation](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/transfers/PullFromVOSpaceNegotiation.java#L145-L182), [test](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-test-vos/src/main/java/org/opencadc/conformance/vos/TransferTest.java#L233-L269)) | Supported read path. |
| Pull-to-VOSpace / push-from-VOSpace | **Unsupported** | Both action classes immediately throw “not implemented.” ([pull-to](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/transfers/PullToVOSpaceAction.java#L82-L90), [push-from](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/transfers/PushFromVOSpaceAction.java#L82-L90)) | Exclude service-initiated third-party transfers. |
| Same-service move | **Supported** | Internal transfer with `keepBytes=false` validates permissions and destination, calls persistence move, and completes the job; integration tests cover recursive container move, overwrite rejection, and circular move rejection. ([implementation](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/transfers/InternalTransferAction.java#L133-L231), [tests](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-test-vos/src/main/java/org/opencadc/conformance/vos/TransferTest.java#L382-L535)) | Deliberately outside v0.3.0 with `/transfers`; DataNode and ContainerNode move is client-derived copy/recreate then delete with no overwrite. LinkNode move is unsupported and raises `NotImplementedError` before mutation. |
| Same-service copy | **Unsupported** | `keepBytes=true` throws `copyNode is not implemented`. ([source](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/transfers/InternalTransferAction.java#L133-L138)) | Implement copy as client-side read/write; do not create a copy job. |
| Bidirectional/container mount | **Partial / not functional** | Negotiation accepts one ContainerNode, but the only generated container protocol is optional SSHFS; the Cavern README calls the SSHFS setup “NOT FUNCTIONAL.” ([negotiation](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/transfers/BiDirectionalTransferNegotiation.java#L99-L121), [generator](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/CavernURLGenerator.java#L256-L287), [README](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/README.md#L149-L154)) | Exclude from the RFC. |

## Node types and properties

| Surface | Status | Evidence and consequence |
|---|---|---|
| `ContainerNode`, `DataNode`, `LinkNode` | **Supported** | Cavern creates POSIX directories, regular files, and symbolic links for these types. ([persistence creation](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/nodes/NodeUtil.java#L203-L252)) Internal VOSpace links are tested for node and byte-transfer resolution. ([node test setup](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/intTest/java/org/opencadc/cavern/NodesTest.java#L95-L107), [transfer tests](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-test-vos/src/main/java/org/opencadc/conformance/vos/TransferTest.java#L276-L380)) |
| `StructuredDataNode`, `UnstructuredDataNode` identity | **Unsupported as a durable distinction** | The XML reader accepts both subclasses, but Cavern reconstructs every regular file as base `DataNode`; no Cavern test exercises either subtype. ([reader](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos/src/main/java/org/opencadc/vospace/io/NodeReader.java#L264-L271), [backend normalization](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/nodes/NodeUtil.java#L499-L506)) Treat all regular objects as opaque DataNodes. |
| Arbitrary URI properties | **Supported on containers/data; unsupported on links** | Non-special properties are persisted as extended attributes, while assigning properties to a LinkNode throws. ([source](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/nodes/NodeUtil.java#L283-L298), [disabled link-property tests](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/intTest/java/org/opencadc/cavern/NodesTest.java#L95-L100)) |
| Immutable/admin properties | **Supported with policy** | Available space, length, MD5, content date, date, creator, and quota are immutable to ordinary updates; creator/quota are admin properties. ([sets](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/nodes/FileSystemNodePersistence.java#L120-L135)) |
| Owner/public/group permissions/inheritance | **Supported** | Authorization grants public read, owner/allocation-owner access, read groups, and write groups; writes require owner/allocation-owner/write-group membership. ([read rules](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/auth/VOSpaceAuthorizer.java#L168-L220), [write rules](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/auth/VOSpaceAuthorizer.java#L222-L258), [permission test enablement](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/intTest/java/org/opencadc/cavern/NodesTest.java#L102-L107)) |
| Node locking | **Unsupported** | Cavern declares the lock property special but unsupported and disables locking tests. ([property](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/nodes/NodeUtil.java#L129-L145), [test flag](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/intTest/java/org/opencadc/cavern/NodesTest.java#L95-L100)) |
| Stable node ID | **Unsupported** | Cavern explicitly does not preserve `Node.id` except for root. ([source](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/nodes/NodeUtil.java#L159-L180)) |
| General views | **Unsupported** | `FileSystemNodePersistence.getViews()` throws and `/views` is absent. Transfer validation only special-cases default and CADC data view before consulting this unsupported method. ([persistence](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/nodes/FileSystemNodePersistence.java#L293-L301), [validation](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/transfers/VOSpaceTransfer.java#L123-L136)) `GET /nodes/path?view=data` is a concrete 303 redirect to sibling `/files/path`. ([source](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/actions/GetNodeAction.java#L120-L136)) |

## Direct bytes, redirects, and ranges

- HEAD returns 200 and sets `Content-Length`, `Content-Disposition`,
  `Content-Type`, optional `Content-Encoding`, `Last-Modified`, and digest.
  GET streams the entire file; an empty DataNode returns 204.
  ([HEAD implementation](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/HeadAction.java#L109-L177),
  [GET implementation](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/GetAction.java#L102-L133),
  [tests](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-test-vos/src/main/java/org/opencadc/conformance/vos/FilesTest.java#L202-L232))
- PUT creates a missing DataNode or truncates an existing one, streams the body,
  computes MD5, rejects a supplied mismatching digest with 412, persists content
  type and digest, and returns 201. It provides no append, resumable, multipart,
  or atomic-replace protocol; failure cleanup truncates the target.
  ([PUT implementation](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/PutAction.java#L181-L299),
  [failure cleanup](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/PutAction.java#L317-L337),
  [checksum test](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-test-vos/src/main/java/org/opencadc/conformance/vos/TransferTest.java#L198-L231))
- **Byte ranges are unsupported by Cavern.** The GET action never reads the
  `Range` header, seeks, or emits 206/`Content-Range`; it opens the file at byte
  zero and copies to EOF. The entire Cavern/conformance source contains no HTTP
  range implementation or test. ([GET implementation](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/GetAction.java#L102-L125))
  `vosfs` therefore never sends a `Range` header. It performs an ordinary
  whole-object GET and slices locally or stages the complete object on disk.
- Transfer negotiation returns concrete endpoints matching requested protocol
  and security method. Anonymous endpoints may embed a one-purpose preauth token;
  unsupported security methods are omitted. Anonymous writes are not offered
  without preauthorization.
  ([endpoint generation](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/CavernURLGenerator.java#L172-L253),
  [token validation](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/CavernURLGenerator.java#L290-L310))
- Async job creation returns a 303 job URL; the client POSTs `PHASE=RUN`, polls
  UWS phases, and reads terminal job state. Sync negotiation POST returns 303 and
  following it yields transfer details or a direct endpoint. Multiple protocol
  endpoints may be returned, and the client test selects an available one.
  ([async lifecycle test](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-test-vos/src/main/java/org/opencadc/conformance/vos/TransferTest.java#L712-L783),
  [sync test helper](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-test-vos/src/main/java/org/opencadc/conformance/vos/TransferTest.java#L785-L811),
  [redirect generation](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/transfers/TransferRunner.java#L383-L435))

## Authentication and errors

- The shipped capability template advertises anonymous, cookie, TLS client
  certificate, and token security methods for nodes, transfers, synchronous
  transfers, and direct files. Runtime endpoint selection is filtered by the
  configured `IdentityManager`, so the exact enabled set is deployment-specific.
  ([capabilities](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/capabilities.xml#L26-L54),
  [files capability](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/webapp/capabilities.xml#L86-L94),
  [identity delegation](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/nodes/PosixIdentityManager.java#L147-L151))
- Public nodes are anonymously readable; private reads require ownership,
  allocation ownership, or read/write group membership. Writes require an
  authenticated owner/allocation owner/write-group member. A preauthorized byte
  endpoint carries its authorization in the URL and must not receive unrelated
  service credentials. ([authorization](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/auth/VOSpaceAuthorizer.java#L176-L258), [preauth path parsing](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cavern/src/main/java/org/opencadc/cavern/files/FileAction.java#L150-L174))
- Cavern tests expose an important asymmetry: HEAD may return metadata when the
  parent is public even when GET of the private DataNode returns 403. A caller
  must not interpret successful HEAD as proof that bytes are readable.
  ([test](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-test-vos/src/main/java/org/opencadc/conformance/vos/FilesTest.java#L320-L357))
- Node faults are symbolic plain-text names mapped to exception types:
  invalid/type/view/option faults become illegal arguments, missing nodes become
  not-found, duplicates become already-exists, permissions become access-control,
  locks become locked, oversized documents become byte-limit errors, and busy
  becomes transient. Exact HTTP mapping is delegated to `cadc-rest` and is
  therefore **unknown from this repository alone**.
  ([fault mapping](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/NodeFault.java#L89-L150))
- Tested node/direct-byte statuses are: GET missing 404 with `text/plain`; node
  create 201, update 200, delete 200; direct HEAD/GET 200, empty GET 204, denied
  GET 403, invalid external-link GET 400, direct PUT 201, checksum mismatch 412.
  ([node test helpers](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-test-vos/src/main/java/org/opencadc/conformance/vos/VOSTest.java#L190-L295),
  [file tests](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-test-vos/src/main/java/org/opencadc/conformance/vos/FilesTest.java#L202-L232),
  [empty/permission/link tests](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-test-vos/src/main/java/org/opencadc/conformance/vos/FilesTest.java#L275-L407))
- Transfer failures are persisted as UWS `ErrorSummary`. Quick/synchronous
  parameter requests additionally return `text/plain`; source maps bad input
  400, not found 404, conflict 409, locked 423, quota 413, permission 403,
  unauthenticated 401, unsupported operation 405, transient 503, and unexpected
  errors 500. ([mapping](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/transfers/TransferRunner.java#L235-L350), [plain-text quick errors](https://github.com/opencadc/vos/blob/cf976ce8141dd3341631b7f3e07aa38443d42f58/cadc-vos-server/src/main/java/org/opencadc/vospace/server/transfers/TransferRunner.java#L438-L474))

## Finite RFC boundary

For the approved `vosfs` v0.3.0 contract scoped to this Cavern surface:

- include node metadata and paged immediate-child listings, ContainerNode/DataNode creation, a
  private constrained POST update primitive, single node deletion,
  capabilities discovery, synchronous push/pull negotiation, and negotiated
  whole-byte reads/writes;
- implement recursive removal as client traversal and leaves-first node
  deletion, copy as client-side read/write, and DataNode/ContainerNode move as
  non-atomic copy/recreate then delete; reject LinkNode move with
  `NotImplementedError` before mutation;
- exclude service-initiated pull-to/push-from transfers, persistent
  Structured/Unstructured subtype semantics, node locking, server-side
  sort/search, general views, SSHFS/bidirectional mounts, and `/protocols`,
  `/views`, `/properties`, all asynchronous UWS resources, native move, public
  link creation, and public property or permission mutation;
- state explicitly that Cavern lacks HTTP byte ranges, append/resume/multipart
  uploads, and atomic replacement. Scientific readers that seek can be correct
  only through disk-backed whole-object staging; and
- interpret only the approved synchronous-transfer 303 chain manually, select
  credentials from the negotiated `securityMethod`, and send no credential to
  anonymous or pre-authorized endpoints.
