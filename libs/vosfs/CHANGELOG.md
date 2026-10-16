# Changelog

## [Unreleased]

### ⚠ BREAKING CHANGES

* User paths are literal. `strip_protocol` no longer percent-decodes them.
  `?` and `#` are allowed in a name. A node URI from the service is still
  decoded once. See ADR 0013.

### Features

* Drop a literal token from `to_dict` / `to_json` when `include_password` is
  false. Pickle still carries the token.
* Read pulls try `REQUEST=redirect` and remember a service that does not
  support it. A cached listing answers the external-link check.
* Same-service move starts a VOSpace `moveNode` job and requests its abort
  on cancellation or timeout. Explicit unsupported-job errors retain the
  client-side copy, verify, and delete fallback.
* `open("rb", block_size=...)` or `cache_type` reads with one negotiated
  endpoint with the same partial-response validation as `cat_file`. A `200`
  stages that response; unknown byte lengths also use staging.
* One connection budget per event loop (`max_connections`, default 100).
  Listing and removal concurrency follow `batch_size`.
  Copies stage with a one-connection budget and release connections before
  failure cleanup. Cancelling queued requests or parent creation cannot leave
  later operations waiting on orphaned state.
* Parse XML with the standard library. `defusedxml` is no longer a dependency.
* Rebuild loop-bound HTTP clients when the running event loop changes.

## [0.11.0](https://github.com/shinybrar/vosfs/compare/v0.10.0...v0.11.0) (2026-10-03)


### Features

* log transfers and cut round trips in tree and bulk operations ([#347](https://github.com/shinybrar/vosfs/issues/347)) ([4463709](https://github.com/shinybrar/vosfs/commit/44637094edcb8f55dc91bcc12b6bf054ad3f0151))

## [0.10.0](https://github.com/shinybrar/vosfs/compare/v0.9.0...v0.10.0) (2026-10-02)


### ⚠ BREAKING CHANGES

* **storage:** resume verified copies and restore shell listings ([#343](https://github.com/shinybrar/vosfs/issues/343))

### Features

* **storage:** resume verified copies and restore shell listings ([#343](https://github.com/shinybrar/vosfs/issues/343)) ([0bbe991](https://github.com/shinybrar/vosfs/commit/0bbe9916a096b61838d669a673233963b81ae99d))

## [0.9.0](https://github.com/shinybrar/vosfs/compare/v0.8.0...v0.9.0) (2026-08-01)


### ⚠ BREAKING CHANGES

* remove audited over-engineering across vosfs and fsspec-cli ([#339](https://github.com/shinybrar/vosfs/issues/339))

### Code Refactoring

* remove audited over-engineering across vosfs and fsspec-cli ([#339](https://github.com/shinybrar/vosfs/issues/339)) ([ed0c368](https://github.com/shinybrar/vosfs/commit/ed0c368b29de7978398b36d5391e263e1bd57059))

## [0.8.0](https://github.com/shinybrar/vosfs/compare/v0.7.0...v0.8.0) (2026-07-25)


### Features

* **vosfs:** honour HTTP Range when the byte endpoint returns 206 ([#335](https://github.com/shinybrar/vosfs/issues/335)) ([8a186cb](https://github.com/shinybrar/vosfs/commit/8a186cb8d45fc456d9ad91f31f3f48d32b8bf82a))

## [0.7.0](https://github.com/shinybrar/vosfs/compare/v0.6.0...v0.7.0) (2026-07-25)


### ⚠ BREAKING CHANGES

* **fsspec-cli:** adopt Typer-owned commands ([#319](https://github.com/shinybrar/vosfs/issues/319))

### Features

* **fsspec-cli:** adopt Typer-owned commands ([#319](https://github.com/shinybrar/vosfs/issues/319)) ([bdf94ca](https://github.com/shinybrar/vosfs/commit/bdf94ca811e23484af0522f5dc854772e4fb1401))


### Bug Fixes

* **release:** isolate fsspec-only shared files ([#327](https://github.com/shinybrar/vosfs/issues/327)) ([23412e9](https://github.com/shinybrar/vosfs/commit/23412e91c84852c9996f279435386941c8bf9ba0))
* **release:** scope shared meta files out of the vosfs package ([#331](https://github.com/shinybrar/vosfs/issues/331)) ([e01ba4d](https://github.com/shinybrar/vosfs/commit/e01ba4db129af51af3e8fae9b598d216aa950eb4))

## [0.6.0](https://github.com/shinybrar/vosfs/compare/v0.5.0...v0.6.0) (2026-07-22)


### Features

* **fsspec-cli:** add fsspec-compatible recursive operations ([#294](https://github.com/shinybrar/vosfs/issues/294)) ([9e5314d](https://github.com/shinybrar/vosfs/commit/9e5314db4706894d31d54d245392f43b9556cfbb))

## [0.5.0](https://github.com/shinybrar/vosfs/compare/v0.4.0...v0.5.0) (2026-07-21)


### Features

* **fsspec-cli:** complete backend-specific extension seam ([#240](https://github.com/shinybrar/vosfs/issues/240)) ([a9ace0f](https://github.com/shinybrar/vosfs/commit/a9ace0f5f630eb32dde5b9f2fde3c1f3b1e2a897))
* **vosfs:** complete OpenCADC profile hardening ([#279](https://github.com/shinybrar/vosfs/issues/279)) ([df0a65d](https://github.com/shinybrar/vosfs/commit/df0a65d0559cab1eff9b544d5c7927417f87ea07))
* **vosfs:** support recursive get ([#224](https://github.com/shinybrar/vosfs/issues/224)) ([dd856e8](https://github.com/shinybrar/vosfs/commit/dd856e818852f540e3c39969df1aabfce9d2b3d4))
* **vosfs:** wire private node updates ([#220](https://github.com/shinybrar/vosfs/issues/220)) ([f78ba7e](https://github.com/shinybrar/vosfs/commit/f78ba7e5b6b4019e7961e90577f4f4d25f218ffc)), closes [#113](https://github.com/shinybrar/vosfs/issues/113)


### Bug Fixes

* **ci:** unwedge Windows hangs in cat subprocess tests ([440a6f2](https://github.com/shinybrar/vosfs/commit/440a6f25547b9290bd0ba86e197ee61408d73857))
* **vosfs:** discard aborted text writes ([#217](https://github.com/shinybrar/vosfs/issues/217)) ([b1e1270](https://github.com/shinybrar/vosfs/commit/b1e12702e5dea187213479f0eacc2bf937b0d52f))
* **vosfs:** graceful degradation when no transfer binding is advertised ([#212](https://github.com/shinybrar/vosfs/issues/212)) ([#214](https://github.com/shinybrar/vosfs/issues/214)) ([44095f8](https://github.com/shinybrar/vosfs/commit/44095f8dbaca69acab44f4c0c0ec0456f2388160))
* **vosfs:** integrate coordinated write hardening ([#264](https://github.com/shinybrar/vosfs/issues/264)) ([82ec596](https://github.com/shinybrar/vosfs/commit/82ec5962f8d9dbe9388625acd912d23058dc144a))

## [0.4.0](https://github.com/shinybrar/vosfs/compare/v0.3.3...v0.4.0) (2026-07-17)

> **Note:** This release changed no `vosfs` package source. Every feature below
> is `fsspec-cli` or CI/release-automation work; `vosfs` behavior is unchanged
> from v0.3.0.


### Features

* **fsspec-cli:** activate independent release automation ([#148](https://github.com/shinybrar/vosfs/issues/148)) ([0a9f730](https://github.com/shinybrar/vosfs/commit/0a9f7302dcc700fcd46ccc170b47ba7640cc83ac)), closes [#107](https://github.com/shinybrar/vosfs/issues/107) [#108](https://github.com/shinybrar/vosfs/issues/108)
* **fsspec-cli:** add async Typer boundary ([#111](https://github.com/shinybrar/vosfs/issues/111)) ([adfef30](https://github.com/shinybrar/vosfs/commit/adfef30158ab1d2a72dd5f9d924d3dd65b362415)), closes [#100](https://github.com/shinybrar/vosfs/issues/100)
* **fsspec-cli:** add deterministic ls renderer ([#115](https://github.com/shinybrar/vosfs/issues/115)) ([460d9fc](https://github.com/shinybrar/vosfs/commit/460d9fc37e54d7ca4672e5131a7e102a1a28a749)), closes [#103](https://github.com/shinybrar/vosfs/issues/103)
* **fsspec-cli:** add directory listing engine ([#117](https://github.com/shinybrar/vosfs/issues/117)) ([3efeb52](https://github.com/shinybrar/vosfs/commit/3efeb52e40f700aeb6983364dfae13de75e44c72)), closes [#102](https://github.com/shinybrar/vosfs/issues/102)
* **fsspec-cli:** manage source lifecycle ([#116](https://github.com/shinybrar/vosfs/issues/116)) ([1660dbe](https://github.com/shinybrar/vosfs/commit/1660dbeff0b34083de74af210dcd17f306bd2320)), closes [#101](https://github.com/shinybrar/vosfs/issues/101)


### Bug Fixes

* **ci:** accept generated release changelogs ([308d45d](https://github.com/shinybrar/vosfs/commit/308d45d60da2b68686e6161f920c6616f5d066bd))


### Documentation

* add Cursor Cloud dev environment setup notes ([#121](https://github.com/shinybrar/vosfs/issues/121)) ([13d479d](https://github.com/shinybrar/vosfs/commit/13d479d48ac5f98337b235ef06b34c19548269c5))
* add fsspec research and CLI specification ([852d696](https://github.com/shinybrar/vosfs/commit/852d696c37cb79b5da175eadeb5fcfe9a0fded32))
* **architecture:** define fsspec-cli release boundary ([#86](https://github.com/shinybrar/vosfs/issues/86)) ([8bc1df6](https://github.com/shinybrar/vosfs/commit/8bc1df60eb4df69eb439480ab3685f275180effc)), closes [#77](https://github.com/shinybrar/vosfs/issues/77)
* **design:** define plain ls command profile ([#88](https://github.com/shinybrar/vosfs/issues/88)) ([534d84c](https://github.com/shinybrar/vosfs/commit/534d84cb3fb738613705ad3fc935ba6c7f55e2da)), closes [#79](https://github.com/shinybrar/vosfs/issues/79)
* **fsspec-cli:** define async source failure contract ([#97](https://github.com/shinybrar/vosfs/issues/97)) ([8f751af](https://github.com/shinybrar/vosfs/commit/8f751afee707710ba8ae2608a99fbeea97e9e049)), closes [#94](https://github.com/shinybrar/vosfs/issues/94)
* **fsspec-cli:** define tested command matrix ([#98](https://github.com/shinybrar/vosfs/issues/98)) ([fc69367](https://github.com/shinybrar/vosfs/commit/fc6936716b580c071b8a57c1a81792ba041de43b)), closes [#81](https://github.com/shinybrar/vosfs/issues/81)
* **fsspec-cli:** lock async host lifecycle contract ([#95](https://github.com/shinybrar/vosfs/issues/95)) ([3ec55e1](https://github.com/shinybrar/vosfs/commit/3ec55e10fca295ba84a7800e6a0736c9bf561e1a)), closes [#92](https://github.com/shinybrar/vosfs/issues/92)
* **fsspec-cli:** lock strict ls -l rejection ([#96](https://github.com/shinybrar/vosfs/issues/96)) ([124ddac](https://github.com/shinybrar/vosfs/commit/124ddaca10cd1ba6d65390f570acb3030e3149e5)), closes [#82](https://github.com/shinybrar/vosfs/issues/82)
* **research:** assess long-listing viability ([#87](https://github.com/shinybrar/vosfs/issues/87)) ([f8a9f96](https://github.com/shinybrar/vosfs/commit/f8a9f96baa8cf2a8f1a37679302abcf8e6a1fe91)), closes [#78](https://github.com/shinybrar/vosfs/issues/78)
* **research:** define fsspec-cli async execution boundary ([#93](https://github.com/shinybrar/vosfs/issues/93)) ([03dbca2](https://github.com/shinybrar/vosfs/commit/03dbca2b401815d3eda90c8a381a019306cbbecb)), closes [#90](https://github.com/shinybrar/vosfs/issues/90)
* **research:** define plain ls capability floor ([#84](https://github.com/shinybrar/vosfs/issues/84)) ([bc87ddf](https://github.com/shinybrar/vosfs/commit/bc87ddfb1d09bf26ef2cc1d2eedf75ec378bfecf)), closes [#76](https://github.com/shinybrar/vosfs/issues/76)
* **research:** lock plain ls interoperability verdict ([#91](https://github.com/shinybrar/vosfs/issues/91)) ([3860fcf](https://github.com/shinybrar/vosfs/commit/3860fcf7d0156a5c6b0c206ca4753443054f699f)), closes [#80](https://github.com/shinybrar/vosfs/issues/80)

## [0.3.3](https://github.com/shinybrar/vosfs/compare/v0.3.2...v0.3.3) (2026-07-14)


### Documentation

* push versioned documentation deployment ([#73](https://github.com/shinybrar/vosfs/issues/73)) ([ac50ac0](https://github.com/shinybrar/vosfs/commit/ac50ac0cffafa3bc370468809fc1a6dd13517f7c))

## [0.3.2](https://github.com/shinybrar/vosfs/compare/v0.3.1...v0.3.2) (2026-07-14)


### Bug Fixes

* provide repository context to release publisher ([#71](https://github.com/shinybrar/vosfs/issues/71)) ([09a74de](https://github.com/shinybrar/vosfs/commit/09a74de7d02ab6292b34bf63acd8a06d947a30dc))

## [0.3.1](https://github.com/shinybrar/vosfs/compare/v0.3.0...v0.3.1) (2026-07-14)


### Bug Fixes

* correct CADC certificate output option ([#68](https://github.com/shinybrar/vosfs/issues/68)) ([6b4beec](https://github.com/shinybrar/vosfs/commit/6b4beec8c1a603a53b586613f682542a79be6ef4))
* prepare v0.3.1 release ([#67](https://github.com/shinybrar/vosfs/issues/67)) ([72bc2aa](https://github.com/shinybrar/vosfs/commit/72bc2aa39eb18a172ed32f4d719aaf8edb68e6a1))
* use canonical CADC authentication host ([#69](https://github.com/shinybrar/vosfs/issues/69)) ([0cd3a90](https://github.com/shinybrar/vosfs/commit/0cd3a90b2c027b35757924898c1887eb0ce9b363))

## [0.3.0](https://github.com/shinybrar/vosfs/compare/v0.2.0...v0.3.0) (2026-07-11)


### Features

* VOSpace fsspec filesystem ([#64](https://github.com/shinybrar/vosfs/issues/64)) ([f7854a0](https://github.com/shinybrar/vosfs/commit/f7854a0e1005227aa70ad28fdad361cf8d8b4b22))


### Bug Fixes

* **ci:** exclude generated changelog from markdown lint ([#62](https://github.com/shinybrar/vosfs/issues/62)) ([77b5d2b](https://github.com/shinybrar/vosfs/commit/77b5d2be207eb293f1bd5266700d8b1932091ad7)), closes [#29](https://github.com/shinybrar/vosfs/issues/29)


### Documentation

* **trd:** add technical requirements document and research ([#44](https://github.com/shinybrar/vosfs/issues/44)) ([2dc8329](https://github.com/shinybrar/vosfs/commit/2dc83294c857123320fbfbe3ffeee705905d56ab)), closes [#29](https://github.com/shinybrar/vosfs/issues/29)

## [0.2.0](https://github.com/shinybrar/vosfs/compare/v0.1.0...v0.2.0) (2026-07-10)


### Features

* **build:** added uv build system ([5df81be](https://github.com/shinybrar/vosfs/commit/5df81bef1afc4b5d2993571f3e90d3b38a4e8fa0))


### Bug Fixes

* **ci:** validate generated release pull requests ([#28](https://github.com/shinybrar/vosfs/issues/28)) ([9804fb7](https://github.com/shinybrar/vosfs/commit/9804fb72213880077c2dec0a5e7aa875b26b6fc5)), closes [#25](https://github.com/shinybrar/vosfs/issues/25)


### Documentation

* add contribution workflow ([60761d0](https://github.com/shinybrar/vosfs/commit/60761d045b9d76ead7b21a9760be6c62b8119b36))
* build public documentation foundation ([4893389](https://github.com/shinybrar/vosfs/commit/4893389b76ba2478818c1e4adecfcd99f8cf584c))
* build public documentation foundation ([06fefff](https://github.com/shinybrar/vosfs/commit/06fefff54107c2fec445418e8593dcdb3f5f6206)), closes [#13](https://github.com/shinybrar/vosfs/issues/13)
* **research:** capture Python baseline practices ([936f89b](https://github.com/shinybrar/vosfs/commit/936f89b70f24d952e0e54cc21e237fd6eb9511a9))
