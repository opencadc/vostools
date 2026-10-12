# vosfs

This context defines the project-specific language used by the `vosfs`
capability contract and implementation backlog.

## Language

**OpenCADC VOSpace profile**:
The VOSpace behavior implemented by the pinned `opencadc/vos` Cavern source
and tests that bounds the `vosfs` capability contract in
[`docs/design/trd.md`](design/trd.md).
_Avoid_: Full VOSpace 2.1 conformance, generic VOSpace support

**Native capability**:
A `vosfs` behavior backed by one implemented and tested operation in the
OpenCADC VOSpace profile.
_Avoid_: Spec-mandated capability

**Client-derived capability**:
A supported `vosfs` behavior composed only from native capabilities, including
documented client-side fallbacks.
_Avoid_: Server capability, native fallback

**Extension-conditional capability**:
A wired OpenCADC extension that `vosfs` exposes only when the deployed service
advertises it and the required behavior is verified.
_Avoid_: Portable VOSpace capability, guaranteed extension

**Unsupported capability**:
A behavior deliberately excluded from the capability contract because the
OpenCADC VOSpace profile lacks the required semantics, no approved
client-derived behavior supplies them, or the behavior is outside the
contract's product scope.
_Avoid_: Unimplemented endpoint

**Application capability**:
Explicit application policy passed to and snapshotted by the embedded command
library. It controls admission of core command features, not backend support.
Copy recursion defaults enabled and remove recursion defaults disabled; no
registry or configuration loader supplies these values.
_Avoid_: Backend capability, protocol discovery

**Embedded command library**:
The separately installable, library-only `fss-cli` uv workspace member at
`src/fss-cli`, with its Python package at `src/fss-cli/src/fss_cli`,
that turns named async filesystem sources into POSIX-shaped Typer commands. Its
sole stable v1 seam for host tools is
`App(sources, *, capabilities=None, extensions=[...]).typer_app`; capabilities
are validated application policy for core commands, while extensions add
opt-in commands without receiving that policy or changing the core surface
when omitted. The embedded command library owns each yielded filesystem only
for one command invocation, while hosts own source configuration and cleanup
declaration.
_Avoid_: vosfs CLI, fss-cli executable

**Async filesystem source**:
A host-configured async context-manager factory that yields one
`AbstractFileSystem` for a command invocation. The embedded command library
enters and exits the source on the invocation loop, making the yielded
filesystem invocation-owned.
_Avoid_: Live filesystem instance, backend constructor, process-wide filesystem

**Core command**:
A first-party embedded command defined by one annotated callback registered on
an `App` instance. Typer owns its token parsing, type conversion, help, and
usage errors; the callback owns semantic validation and filesystem execution
behavior.
_Avoid_: Raw-token command, catalog entry

**Command extension**:
An opt-in synchronous annotated callback passed through `App(...,
extensions=[...])`. Its name, docstring, and annotations define the Typer
command, and a source-aware callback retrieves the immutable source snapshot
as `CommandContext` through `typer.Context`.
_Avoid_: Registrar, plugin registry, nested command group

**Mapped filesystem operand**:
An explicit `name:/path` command argument whose name selects one async
filesystem source from the embedded command library's configured mapping.
Plain `ls` accepts one or more mapped filesystem operands and has no implicit
current or default filesystem.
_Avoid_: fsspec URL, protocol URL, mount point, bare path

**Command compatibility profile**:
The locked command-owned execution behavior after Typer has parsed and
converted one embedded command's annotated parameters, independent of backend
type. A profile defines semantic validation, consumed async operations, result
shapes, output, diagnostics, and exit behavior that tested source forms must
share.
_Avoid_: Generic POSIX support, backend-specific command mode

**Tested command matrix**:
The version-scoped evidence ledger for command compatibility profiles across
specific native or adapted async filesystem source forms. Missing, stale, or
incomplete evidence is unverified rather than unsupported, and one command's
status makes no claim about another command.
_Avoid_: fsspec compatibility list, runtime capability registry

**Negotiated byte endpoint**:
The temporary or pre-authorized byte URL returned by `/synctrans` for one
`pullFromVoSpace` read or `pushToVoSpace` write.
_Avoid_: Files URL, configured byte endpoint

**Service base URL**:
The caller-supplied HTTP URL identifying one OpenCADC VOSpace deployment, such
as `https://example.invalid/arc`.
_Avoid_: VOSpace authority, `vos://` authority

**Filesystem path**:
The complete path after the `vos://` protocol marker; its first component does
not select a service or VOSpace authority.
_Avoid_: VOSURI, service-qualified URL

**VOSpace authority**:
The logical authority carried by server-returned VOSURIs and required inside
VOSpace XML documents.
_Avoid_: Service base URL, fsspec URL authority

**tokenfile**:
The public `vosfs` option naming a file whose bearer token is reread before
each authenticated request.
_Avoid_: `token_file`, token provider

**certfile**:
The public `vosfs` option naming a combined X.509 certificate-chain and
private-key PEM file.
_Avoid_: `cert_path`, `key_path`, SSL context

**Whole-object staged read**:
A read that downloads one complete remote object into a disk-backed temporary
file before exposing local seek and range behavior. Used by `open` and by
`cat_ranges` when the byte endpoint answers `200`/`204` instead of `206`.
_Avoid_: Block-cached read, assumed partial network read

**Response-validated ranged read**:
A partial `cat_file` / `cat_ranges` read that sends HTTP `Range` and keeps the
partial body only when the negotiated byte endpoint returns a validated `206`
with matching `Content-Range` and body length. A `200`/`204` is a whole-object
fallback, never treated as a partial.
_Avoid_: `Accept-Ranges`-only detection, assumed Range support

**Staged write**:
A buffered file write committed by one whole-object PUT when the file closes
successfully.
_Avoid_: Multipart upload, resumable upload, atomic write

**Recursive removal**:
A deletion explicitly requested with `recursive=True` and implemented by
client-side traversal followed by leaves-first node DELETE requests.
_Avoid_: `/async-delete`, implicit container deletion

**Synchronous transfer negotiation**:
One `/synctrans` POST followed through its 303 result to obtain a negotiated
byte endpoint, without starting, polling, or aborting an asynchronous job.
_Avoid_: Async transfer job, direct files URL construction

**Internal LinkNode**:
A VOSpace link whose target has the same discovered VOSpace authority and can
be resolved by the OpenCADC service without cross-service I/O.
_Avoid_: Filesystem alias, external link

**Service binding**:
An operation URL and security method advertised by the deployment's VOSI
capabilities document for one approved OpenCADC operation.
_Avoid_: Probed endpoint, guessed resource path

**VOSpaceError**:
The single public `OSError` subclass for OpenCADC failures that have no precise
standard Python filesystem exception.
_Avoid_: HTTP error, UWS error
