# `vosfs` v0.3.0 integration and cassette test plan

> **Historical, non-normative.** Issue
> [#242](https://github.com/shinybrar/vosfs/issues/242) retired this live-testing
> plan. It defines no current CI, release, or contributor requirement.

<!-- pyml disable line-length -->

Researched: 2026-07-10
Contract target: `vosfs` v0.3.0
Live service: `https://staging.canfar.net/arc` by default

Status: **Informative test-plan evidence.** `docs/design/trd.md` is the sole
normative v0.3.0 contract and controls if this plan differs.

## Decision

The v0.3.0 test contract has three required lanes:

1. deterministic RESpx replay on every pull request, including external forks;
2. a live OpenCADC profile gate against the configurable staging endpoint on
   every trusted main-branch push and before release; and
3. scientific-stack compatibility tests against both replay and the live
   service.

The live gate is authoritative for OpenCADC interoperability. Cassettes are an
offline regression aid, not a substitute for the live gate.

The tested wire profile is deliberately limited to service discovery plus
`/nodes`, `/synctrans`, and the negotiated `/files` endpoints. Tests MUST NOT
invoke `/transfers`, `/async-delete`, `/async-setprops`, `/pkg`, or any other
asynchronous UWS resource. The `/synctrans` interaction is treated only as a
synchronous POST/303 negotiation chain; the client does not create, start,
poll, abort, or delete a UWS job.

## Primary-source basis

This plan was checked against:

- RESpx 0.23.1 at commit
  [`57d8c29705fdbbaeb5cd216f1ea3bb0386d7ba16`](https://github.com/lundberg/respx/tree/57d8c29705fdbbaeb5cd216f1ea3bb0386d7ba16);
- OpenCADC `cadctools` at commit
  [`688e957387bedc89edc85cb3e12fa0af4eb8a9f5`](https://github.com/opencadc/cadctools/tree/688e957387bedc89edc85cb3e12fa0af4eb8a9f5).

RESpx documents itself as a router that captures HTTPX requests and returns
mocked responses or side effects. It also supports explicit pass-through to a
real server. Its documented interface does not define persistent cassette
serialization or secret sanitization. The project therefore MUST use RESpx
only for strict replay and MUST own the small record/sanitize/load layer.
([RESpx guide](https://lundberg.github.io/respx/guide/#mock-httpx),
[strict routing assertions](https://lundberg.github.io/respx/guide/#assert-all-mocked),
[pass-through API](https://lundberg.github.io/respx/api/#pass_through))

`cadc-get-cert` is a console entry point supplied by `cadcutils`. It retrieves
an X.509 proxy from the CADC Credential Delegation service and writes it to one
requested file. The CLI accepts a non-interactive `--netrc-file` source, while
`--user` explicitly prompts for a password and is unsuitable for CI. The same
PEM path is used as the certificate and private-key input by `cadcutils`, which
establishes the combined-PEM convention used by this plan.
([entry point](https://github.com/opencadc/cadctools/blob/688e957387bedc89edc85cb3e12fa0af4eb8a9f5/cadcutils/setup.cfg#L61-L64),
[certificate retrieval and output](https://github.com/opencadc/cadctools/blob/688e957387bedc89edc85cb3e12fa0af4eb8a9f5/cadcutils/cadcutils/net/auth.py#L302-L395),
[authentication arguments](https://github.com/opencadc/cadctools/blob/688e957387bedc89edc85cb3e12fa0af4eb8a9f5/cadcutils/cadcutils/util/utils.py#L347-L366),
[combined-PEM use](https://github.com/opencadc/cadctools/blob/688e957387bedc89edc85cb3e12fa0af4eb8a9f5/cadcutils/cadcutils/net/ws.py#L424-L450))

## Test configuration

The test harness MUST recognize these values:

| Setting | Contract |
| --- | --- |
| `VOSFS_TEST_ENDPOINT` | Optional live service base URL. It defaults to `https://staging.canfar.net/arc` and remains configurable without changing tests or fixtures. |
| `VOSFS_CERT_FILE` | Combined proxy PEM used by the live filesystem. Local integration tests default to the readable file `~/.ssl/cadcproxy.pem`; CI always supplies an explicit temporary path. |
| `VOSFS_TEST_PREFIX` | Optional namespace prefix. The default is `/vosfs-ci`; every run creates a unique child and MUST remain confined to it. |
| `VOSFS_RECORD_CASSETTES` | Explicit opt-in for recording. Absence, an empty value, or false means no real network and no fixture writes. |
| `VOSFS_CADC_CREDENTIAL_HOST` | Non-secret CI configuration naming the CADC Credential Delegation host used both by `cadc-get-cert --host` and the temporary netrc entry. |
| `VOSFS_CADC_USERNAME` | GitHub Actions secret used only while minting the temporary proxy PEM. |
| `VOSFS_CADC_PASSWORD` | GitHub Actions secret used only while minting the temporary proxy PEM. |

The live test harness MUST fail at collection or setup when the endpoint is
invalid, the certificate is missing or unreadable, or a caller explicitly
selected live tests without usable credentials. It MUST NOT silently convert a
requested live run into skips or cassette replay.

`cadcutils` is a development-only dependency used to bootstrap the proxy. It
MUST NOT become a `vosfs` runtime dependency, and the live tests MUST exercise
`vosfs`'s HTTPX transport rather than a `cadcutils` VOSpace client.

## Credential lifecycle

### Local development

Local live runs MAY use `~/.ssl/cadcproxy.pem`. The harness MUST only read that
file and pass its path as `certfile`; it MUST never copy it into the repository,
put it in a test artifact, inspect its private-key text, or include its path in
a cassette. A caller may override the path through `VOSFS_CERT_FILE`.

### Trusted GitHub Actions

The trusted live job MUST:

1. install `cadcutils` from the development lock, not dynamically add it to the
   runtime environment;
2. create a runner-temporary netrc with mode `0600`, containing the configured
   Credential Delegation host and the username/password GitHub secrets;
3. invoke `cadc-get-cert` with that netrc, the same explicit credential host,
   a runner-temporary output path, and the shortest practical validity period;
4. set the resulting combined proxy PEM to mode `0600` and expose only its path
   to the live test process through `VOSFS_CERT_FILE`; and
5. delete both temporary files in an unconditional final step.

The job MUST NOT pass the password on a command line, enable shell tracing,
print the netrc, print the PEM, cache either file, or upload either file as an
artifact. The secrets MUST be scoped only to the certificate-minting step. The
live test process receives the certificate path, never the username or
password. Job logs and pytest failure output MUST redact all authentication
material and negotiated pre-authorization URLs.

## Per-run namespace and cleanup

Every live scenario MUST run beneath a new root whose name includes the
workflow run identifier, run attempt, worker identifier, and an unpredictable
suffix. A local run uses the same shape with a local process identifier and
random suffix. Tests MUST NOT share or reuse a fixed mutable namespace.

Before creating the root, the harness MUST verify that it does not exist. A
collision fails setup; the harness MUST NOT delete or adopt the existing path.
Parallel test workers receive disjoint children below the run root.

Cleanup is part of the hard gate:

- every test registers its created paths immediately;
- a `finally` fixture and a job-level always-run cleanup step delete files and
  nested containers leaves-first, then delete the run root;
- cleanup uses only the approved `/nodes` GET and DELETE operations and MUST
  NOT call `/async-delete`;
- the final assertion verifies that the run root returns not-found; and
- a cleanup failure fails the integration job and reports only the sanitized
  run-root path needed for operator cleanup.

The live workflow MUST use `cancel-in-progress: false` and a finite job timeout
so a newer push does not routinely interrupt cleanup. A manually cancelled or
infrastructure-killed run can still leave data; its unique path makes that
residue identifiable without risking deletion of another run.

## Live OpenCADC profile gate

The live gate MUST run each behavior through the native async filesystem and
through the generated blocking facade. The second facade may use a smaller
data set, but neither may be replaced by a mocked transport.

One live run MUST prove all of the following:

1. Fetch service capabilities and resolve the node and synchronous-transfer
   bindings without probing unsupported resources.
2. Confirm the unique root is absent, create it as a ContainerNode, read its
   metadata, and list it as empty.
3. Create two nested containers in parent-first order, list their immediate
   children, and distinguish files from directories through `info()`.
4. Invoke the private node-update primitive to POST one mutable,
   non-administrative property on a test DataNode or ContainerNode, then verify
   that public `info()` returns it through the read-only properties mapping.
5. Upload a small deterministic non-empty object by POSTing a
   `pushToVoSpace` request to `/synctrans`, interpreting the synchronous 303
   chain, and PUTting bytes to the returned `/files` endpoint.
6. Read that object by POSTing a `pullFromVoSpace` request to `/synctrans` and
   GETting the returned `/files` endpoint; assert exact byte, length, content
   type, and available checksum metadata.
7. Overwrite the object with a shorter payload and prove the old suffix was
   truncated. Create and read a zero-byte object through the same public
   filesystem surface.
8. Exercise whole-object fallback for positive, negative, empty, clipped, and
   out-of-bounds `cat_file` slices without asserting remote Range support.
9. Prove a missing node maps to `FileNotFoundError`, a duplicate exclusive
   create maps to `FileExistsError`, and a non-empty container cannot be
   removed non-recursively.
10. Exercise client-derived copy, DataNode/ContainerNode move, unsupported
    LinkNode move, and recursive removal. Copy and supported move MUST preserve
    bytes; supported move MUST leave the source absent. LinkNode move MUST raise
    `NotImplementedError` before mutation. Recursive removal MUST traverse and
    DELETE leaves-first without invoking asynchronous UWS.
11. Round-trip the filesystem through pickle and fsspec JSON in a fresh
    process, then perform a real metadata read and byte read with reconstructed
    clients.
12. Close the filesystem twice, prove later I/O fails as closed, and prove a
    newly constructed instance can still access the live namespace.
13. Run the five supported pandas, NumPy, Dask, Zarr v3, and PyArrow/Parquet
    acceptance seams inside the unique live namespace.
14. Assert from the test call ledger that no request path begins with
    `/transfers`, `/async-delete`, `/async-setprops`, or `/pkg`.
15. Delete all remaining paths leaves-first and verify the run root is absent.

Negotiated `/files` URLs are data, not configuration. The live test MUST use
the returned URL and security method exactly as negotiated and MUST NOT
construct a `/files` URL from the VOSpace path.

## Cassette recording boundary

RESpx MUST NOT be presented as a VCR. Its pass-through flag sends a matched
request to the real server, but the project still owns fixture persistence,
normalization, and safety. Recording therefore uses a small internal HTTPX
transport wrapper around the real transport:

- it forwards each request unchanged;
- it tees the complete request and response for fixture-sized scenarios only;
- it stores the ordered interaction in a temporary, untracked location;
- it applies normalization and redaction before any repository file is
  written; and
- it refuses to emit a fixture when a secret check or normalization invariant
  fails.

The recorder is test infrastructure only. It MUST NOT be reachable through
`VOSpaceFileSystem` constructor options or serialized storage options. The
recording payload limit MUST be small and explicit; large-object streaming and
backpressure are verified by dedicated hermetic transport tests and the live
gate, not by buffering large live responses into cassettes.

Recording MUST require all of the following: the explicit record flag, an
explicitly selected recording test, a live endpoint, and a readable
certificate. Ordinary pytest, CI pull requests, and downstream replay tests
MUST be network-denied and fixture-read-only. Automated CI MUST NOT update
cassettes. A maintainer records locally, reviews the sanitized diff, and
commits it deliberately.

## Fixture format and sanitization

Each cassette MUST be a small, versioned, human-reviewable document containing
one named scenario and an ordered list of interactions. Each interaction
contains only the normalized request method, URL, selected headers and body,
followed by the response status, selected headers and body. XML is stored in a
stable canonical form; binary bodies are limited to fixed, repository-owned
test payloads.

The sanitizer MUST apply these rules consistently across URLs, headers, XML,
plain text, and nested error documents:

| Sensitive or unstable value | Required fixture representation |
| --- | --- |
| `Authorization`, `Proxy-Authorization` | Remove the value. Preserve only an assertion such as `credential: bearer-present` when the scenario needs to verify routing. |
| `Cookie`, `Set-Cookie` | Remove the header and value entirely. A fixture containing either header fails generation. |
| Client certificate or private key | Never observable to the recorder and never serialized. Any PEM marker in fixture output fails generation. |
| `/files/preauth:<token>/...` and equivalent query/body values | Replace every occurrence with one stable scenario-local placeholder while preserving referential equality between the 303 response and subsequent request. |
| Username, account URI, owner/group identity, and X.509 subject DN | Replace with typed placeholders such as account, owner, group, and subject. Raw identity strings fail generation. |
| Unique run root and child paths | Replace the full run root with one path placeholder; preserve relative child names needed by assertions. |
| Service and negotiated origins | Replace with separate service-origin and file-origin placeholders so replay can test same-origin and cross-origin rules. |
| `Date`, `Last-Modified`, expiry times, and XML timestamps | Normalize to fixed valid timestamps while preserving ordering when the test depends on it. |
| Request, trace, correlation, transaction, and server instance IDs | Remove or replace with stable typed placeholders. |
| Server banners and volatile transport headers | Drop unless they are part of the published client behavior under test. |

The sanitizer MUST scan the completed fixture for the original username,
run-root suffix, credential host secrets, Authorization values, cookie names
and values, PEM markers, and unnormalized `preauth:` segments. It fails closed
if any value remains. The recorder MUST never use production or personal file
contents; it records only deterministic public test bytes.

Every cassette change requires manual review for:

1. the expected HTTP methods and approved endpoint families only;
2. absence of credentials, identities, opaque tokens, and unrelated server
   metadata;
3. minimal response bodies and headers;
4. stable placeholders rather than weakened wildcard matching; and
5. a clear reason for any wire-contract change.

## RESpx replay gate

The cassette loader MUST translate normalized interactions into RESpx routes;
the persisted file is not handed directly to the filesystem. Replay MUST use
`assert_all_mocked=True` and `assert_all_called=True`, network access MUST be
disabled, and unexpected or unused interactions MUST fail the test. RESpx
matches routes in added order, so an ordered scenario can represent repeated
POST and GET requests without broad catch-all routes.

Route matching MUST include the HTTP method, normalized path and query, all
security-relevant header presence or absence, and the canonical XML request
body. It MUST NOT match a live secret value. The loader substitutes safe local
origins and scenario-local placeholder values, then returns the recorded
status, selected headers, and response body.

The required cassette scenarios are:

1. capability and binding discovery;
2. empty-root, nested-container, listing, and metadata CRUD;
3. synchronous push negotiation followed by whole-file PUT;
4. synchronous pull negotiation followed by whole-file GET;
5. overwrite, zero-byte file, and whole-object slice fallback;
6. client-derived copy and DataNode/ContainerNode move, unsupported LinkNode
   move, and leaves-first recursive delete;
7. not-found, conflict, non-empty-directory, malformed XML, and bounded error
   responses; and
8. same-origin, cross-origin, and pre-authorized endpoint credential routing.

Scenarios SHOULD remain independent rather than forming one long stateful
cassette. Shared builders may derive repeated node and transfer XML, but a
test's expected calls MUST stay visible and strict.

## fsspec and scientific-stack gates

The supported fsspec abstract backend tests and downstream tests MUST run in
two modes:

- **replay mode** on every pull request and push, using strict RESpx routes and
  no credentials; and
- **live mode** in the trusted integration job, using a unique subdirectory of
  that run's namespace.

The exact downstream acceptance set is:

| Consumer | Required behavior |
| --- | --- |
| fsspec | Run the reusable abstract `open`, `pipe`, `copy`, `get`, and `put` suites plus dedicated `mv` tests through their supported seams. The current hermetic baseline collects 592 tests: 586 passed and six skipped. The abstract subset is 131 passed and the same six `fil?1` copy/get/put skips because the existing path grammar reserves `?` as a query delimiter. |
| pandas | Write a DataFrame with `to_csv("vos://...")`, reconstruct the filesystem in a fresh process, and read it with `read_csv`; assert columns, dtypes used by the fixture, row order, and values. |
| NumPy | Round-trip `.npy` and `.npz` data through `fs.open`, and read a text array through a file object. Direct arbitrary-URL dispatch and remote `mmap_mode` remain outside the claim. |
| Dask | Write and read CSV with `blocksize=None` through a fresh process/worker boundary; assert fsspec serialization and partition contents. No distributed range-read claim is made. |
| Zarr v3 | Use `FsspecStore` to create, write, list, partially read, overwrite, and delete an array. Partial reads MUST be correct while the call ledger proves the v0.3.0 whole-object fallback. |
| PyArrow/Parquet | Use `PyFileSystem(FSSpecHandler(fs))` for Parquet write, dataset discovery, and read; assert schema and values. Footer seeks operate on the staged whole-object reader. |

FUSE, append and update modes, remote Range/206 behavior, block-oriented cache
wrappers, native server copy/move, and all asynchronous UWS resources are not
release gates and MUST remain explicit unsupported assertions.

## GitHub Actions policy

| Event | Required jobs | Secret policy |
| --- | --- | --- |
| `pull_request`, including external forks | Static checks, unit/wire tests, strict cassette replay, supported fsspec abstract tests, and all downstream replay tests. | No repository secret is requested or exposed. No network recording occurs. |
| Trusted `push` to the default branch | All pull-request jobs plus certificate minting, the full live OpenCADC profile gate, and every downstream live test. | Username/password are available only to the mint step; the test step receives only the temporary PEM path. |
| Trusted release workflow or release tag | Require a passing live and replay result for the exact release commit; rerun both when the prior result cannot be proven to cover that SHA. Artifact publication remains blocked on them. | Same temporary credential lifecycle as the trusted push. |
| Trusted `workflow_dispatch` | Permit an operator to run the same live and replay jobs for diagnosis or staging verification. | Use protected repository/environment secrets and normal log redaction. |

The repository MUST NOT use `pull_request_target` for these tests. External
forks get the complete replay/downstream contract without access to secrets;
live checks occur only after code reaches a trusted push or protected release
context. A scheduled live run MAY provide additional drift detection but does
not replace the required trusted-push and release gates.

Branch protection and release automation MUST treat the replay gate and live
integration gate as required checks. A staging outage is therefore a real hard
failure under the stated assumption that the configured service is available;
the job MUST not silently mark the gate successful or switch to cassettes.

## Acceptance criteria

This plan is satisfied when:

1. a local developer can run the live suite against the default staging URL
   with the readable `~/.ssl/cadcproxy.pem` and can override both values;
2. trusted GitHub Actions can mint a temporary combined proxy PEM from
   username/password secrets without exposing either secret or persisting the
   certificate;
3. every live run creates one collision-resistant namespace, confines all
   writes to it, deletes it leaves-first, and fails if cleanup is incomplete;
4. live tests exercise only capability discovery, `/nodes`, `/synctrans`, and
   negotiated `/files` URLs, with no asynchronous UWS traffic;
5. cassette recording is explicitly opt-in, project-owned, sanitized, bounded,
   and manually reviewed;
6. replay is network-free and fails on unexpected or unused RESpx routes;
7. fork pull requests pass the complete replay and downstream gates without
   secrets;
8. trusted pushes and releases pass the live core and live downstream gates
   for the exact commit; and
9. no fixture, log, cache, artifact, traceback, or test report contains a
   password, token, cookie, pre-authorization token, account identity, subject
   DN, private key, or proxy certificate.
