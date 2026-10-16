# Changelog

## Unreleased

### Features

* `mv` calls `_mv_file` when the backend overrides it, and otherwise a
  class-declared `_mv`.
* `stat` accepts an integer, float, datetime, or ISO-8601 `mtime`, using the
  same UTC normalization as listings for values without a timezone.
* `tail` requests the suffix with `start=-n`.
* A recursive copy skips the SHA-256 download when both sides have an MD5
  and the digests differ.
* Same-source `cp -R` copies each file with `_cp_file` unless the sizes match
  and an MD5 is missing, in which case it still stages to compare bytes.
  An unsupported copy hook falls back to client download and upload.
  Cross-source copies still stage. Destination listing is optional when
  direct metadata queries are available; sparse lookups remain concurrent.
* Multi-source `cp` runs under the shared concurrency limit and prints
  outcomes in operand order.
* `ls` preserves direct link identity before listing directory children.
  Shared listing validation rejects duplicate lexical paths and accepts
  backend names with or without a leading slash.

* **fsspec-cli:** add source-free `basename string` lexical command (#123)
* **fsspec-cli:** add optional `basename` suffix operand ([#124](https://github.com/shinybrar/vosfs/issues/124))
* **fsspec-cli:** add source-free `dirname string` lexical command (#125)
* Add mapped-file `cat` with whole-object `_get_file` staging and bounded
  binary stdout.
* **fsspec-cli:** add binary stdin and `-` sequencing for `cat`
  ([#127](https://github.com/shinybrar/vosfs/issues/127))
* **fsspec-cli:** add base `mkdir` without parent creation ([#128](https://github.com/shinybrar/vosfs/issues/128)).
  Passing results claim only source-default creation semantics, not POSIX mode
  or umask behavior.
* **fsspec-cli:** add parent-creating `mkdir -p` through `_makedirs` ([#129](https://github.com/shinybrar/vosfs/issues/129)).
  Passing results claim only source-default creation semantics, not POSIX mode
  or umask behavior.
* **fsspec-cli:** add base empty-directory `rmdir` ([#130](https://github.com/shinybrar/vosfs/issues/130))
* **fsspec-cli:** add XSI `unlink` for one mapped file ([#131](https://github.com/shinybrar/vosfs/issues/131))
* **fsspec-cli:** add base file-only `rm` ([#132](https://github.com/shinybrar/vosfs/issues/132)).
  Reuses the unlink confirmed `_rm_file` absence boundary for one or more files;
  force, directory, recursion, verbose, and interactive options remain unsupported.

* **fsspec-cli:** add verified same-source two-operand `cp` ([#137](https://github.com/shinybrar/vosfs/issues/137)).
  Passing rows prove target resolution, replacement, bytes, diagnostics, cleanup,
  and partial state only — not POSIX mode, ownership, link identity, or timestamps.
* **fsspec-cli:** add verified two-operand recursive `cp -R` / `cp -r`
  ([#286](https://github.com/shinybrar/vosfs/issues/286)).
  All admitted routes use a bounded manifest and complete source and
  destination metadata verification. Same-source files use `_cp_file` when a
  local byte compare is unnecessary; every other file is staged on the host.
* **fsspec-cli:** add snapshotted application recursion capabilities. Recursive
  copy defaults on; recursive removal defaults off. Capability policy is
  explicit constructor input, not backend discovery or loaded configuration.
* **fsspec-cli:** add capability-gated guarded recursive `rm -R` / `rm -r`
  ([#288](https://github.com/shinybrar/vosfs/issues/288)). The command builds a
  bounded pre-mutation manifest, uses only four generic async hooks to plan and
  remove entries, proves final absence, and reports possible residue without
  promising rollback or atomicity.

### Changed

* Parameterize private command orchestration with concrete command labels
  without adding a supported command or public runner.

## [0.10.0](https://github.com/shinybrar/vosfs/compare/fsspec-cli-v0.9.0...fsspec-cli-v0.10.0) (2026-10-04)


### Features

* log transfers and cut round trips in tree and bulk operations ([#347](https://github.com/shinybrar/vosfs/issues/347)) ([4463709](https://github.com/shinybrar/vosfs/commit/44637094edcb8f55dc91bcc12b6bf054ad3f0151))

## [0.9.0](https://github.com/shinybrar/vosfs/compare/fsspec-cli-v0.8.0...fsspec-cli-v0.9.0) (2026-10-02)


### ⚠ BREAKING CHANGES

* **storage:** resume verified copies and restore shell listings ([#343](https://github.com/shinybrar/vosfs/issues/343))

### Features

* **storage:** resume verified copies and restore shell listings ([#343](https://github.com/shinybrar/vosfs/issues/343)) ([0bbe991](https://github.com/shinybrar/vosfs/commit/0bbe9916a096b61838d669a673233963b81ae99d))

## [0.8.0](https://github.com/shinybrar/vosfs/compare/fsspec-cli-v0.7.0...fsspec-cli-v0.8.0) (2026-08-01)


### ⚠ BREAKING CHANGES

* remove audited over-engineering across vosfs and fsspec-cli ([#339](https://github.com/shinybrar/vosfs/issues/339))

### Code Refactoring

* remove audited over-engineering across vosfs and fsspec-cli ([#339](https://github.com/shinybrar/vosfs/issues/339)) ([ed0c368](https://github.com/shinybrar/vosfs/commit/ed0c368b29de7978398b36d5391e263e1bd57059))

## [0.7.0](https://github.com/shinybrar/vosfs/compare/fsspec-cli-v0.6.0...fsspec-cli-v0.7.0) (2026-07-25)


### Features

* **vosfs:** honour HTTP Range when the byte endpoint returns 206 ([#335](https://github.com/shinybrar/vosfs/issues/335)) ([8a186cb](https://github.com/shinybrar/vosfs/commit/8a186cb8d45fc456d9ad91f31f3f48d32b8bf82a))

## [0.6.0](https://github.com/shinybrar/vosfs/compare/fsspec-cli-v0.5.0...fsspec-cli-v0.6.0) (2026-07-25)


### ⚠ BREAKING CHANGES

* **fsspec-cli:** adopt Typer-owned commands ([#319](https://github.com/shinybrar/vosfs/issues/319))

### Features

* **fsspec-cli:** adopt Typer-owned commands ([#319](https://github.com/shinybrar/vosfs/issues/319)) ([bdf94ca](https://github.com/shinybrar/vosfs/commit/bdf94ca811e23484af0522f5dc854772e4fb1401))

## [0.5.0](https://github.com/shinybrar/vosfs/compare/fsspec-cli-v0.4.0...fsspec-cli-v0.5.0) (2026-07-22)


### Features

* **fsspec-cli:** add fsspec-compatible recursive operations ([#294](https://github.com/shinybrar/vosfs/issues/294)) ([9e5314d](https://github.com/shinybrar/vosfs/commit/9e5314db4706894d31d54d245392f43b9556cfbb))

## [0.4.0](https://github.com/shinybrar/vosfs/compare/fsspec-cli-v0.3.0...fsspec-cli-v0.4.0) (2026-07-21)


### Features

* **vosfs:** complete OpenCADC profile hardening ([#279](https://github.com/shinybrar/vosfs/issues/279)) ([df0a65d](https://github.com/shinybrar/vosfs/commit/df0a65d0559cab1eff9b544d5c7927417f87ea07))

## [0.3.0](https://github.com/shinybrar/vosfs/compare/fsspec-cli-v0.2.1...fsspec-cli-v0.3.0) (2026-07-21)


### Features

* **fsspec-cli:** add adaptive long listings ([#219](https://github.com/shinybrar/vosfs/issues/219)) ([86df794](https://github.com/shinybrar/vosfs/commit/86df794ce88e909ec5187008a722a783838db480))
* **fsspec-cli:** add du command ([#221](https://github.com/shinybrar/vosfs/issues/221)) ([eb02501](https://github.com/shinybrar/vosfs/commit/eb025011e85f4c3d742f412123400e349981af9b))
* **fsspec-cli:** add head and tail commands ([#228](https://github.com/shinybrar/vosfs/issues/228)) ([b16741d](https://github.com/shinybrar/vosfs/commit/b16741d0b35ae583895ed389d55f928bafd3e92e))
* **fsspec-cli:** add info command ([#230](https://github.com/shinybrar/vosfs/issues/230)) ([69e535b](https://github.com/shinybrar/vosfs/commit/69e535bbb470bcdd0a712044b3034097ebd84872))
* **fsspec-cli:** add info normalization layer ([#216](https://github.com/shinybrar/vosfs/issues/216)) ([b507a6e](https://github.com/shinybrar/vosfs/commit/b507a6e111405f5f81fd9cf4951957129181e449))
* **fsspec-cli:** add recursive find command ([#223](https://github.com/shinybrar/vosfs/issues/223)) ([6b657ed](https://github.com/shinybrar/vosfs/commit/6b657ed598a80c03bab229fce182596578bf2417))
* **fsspec-cli:** add size and test commands ([#226](https://github.com/shinybrar/vosfs/issues/226)) ([a45eeaa](https://github.com/shinybrar/vosfs/commit/a45eeaa6ba15ce63a148457701d0e5e820f5aebc))
* **fsspec-cli:** add tree command ([#229](https://github.com/shinybrar/vosfs/issues/229)) ([ebb2ae9](https://github.com/shinybrar/vosfs/commit/ebb2ae9d0a1771fabc2cdee3d595217145074fc3))
* **fsspec-cli:** complete backend-specific extension seam ([#240](https://github.com/shinybrar/vosfs/issues/240)) ([a9ace0f](https://github.com/shinybrar/vosfs/commit/a9ace0f5f630eb32dde5b9f2fde3c1f3b1e2a897))
* **fsspec-cli:** verify transfers with metadata ([#233](https://github.com/shinybrar/vosfs/issues/233)) ([4934375](https://github.com/shinybrar/vosfs/commit/4934375d10cda11879d739a60fa20ae7cbe729b6))

## [0.2.1](https://github.com/shinybrar/vosfs/compare/fsspec-cli-v0.2.0...fsspec-cli-v0.2.1) (2026-07-18)


### Documentation

* **fsspec-cli:** reconcile Issue 120 catalog and later release ([#147](https://github.com/shinybrar/vosfs/issues/147)) ([#184](https://github.com/shinybrar/vosfs/issues/184)) ([d1e1918](https://github.com/shinybrar/vosfs/commit/d1e191895644657729cf7ea72e74fb688f7c6a8b))

## [0.2.0](https://github.com/shinybrar/vosfs/compare/fsspec-cli-v0.1.1...fsspec-cli-v0.2.0) (2026-07-17)


### Features

* **fsspec-cli:** add base file-only rm ([#163](https://github.com/shinybrar/vosfs/issues/163)) ([f84e51e](https://github.com/shinybrar/vosfs/commit/f84e51e0dd3d2e210a26b9e5a7f2bf3288e7619c))
* **fsspec-cli:** add base mkdir without parent creation ([#154](https://github.com/shinybrar/vosfs/issues/154)) ([6243660](https://github.com/shinybrar/vosfs/commit/6243660f668ac9e1a9685b0bf602a8d7132a76d8))
* **fsspec-cli:** add binary mapped-file cat ([#156](https://github.com/shinybrar/vosfs/issues/156)) ([6df90ff](https://github.com/shinybrar/vosfs/commit/6df90ff3c8a6c1c07f1bb1e3ab892ddaa4fa03a2))
* **fsspec-cli:** add confirmed-removal rm -v ([#136](https://github.com/shinybrar/vosfs/issues/136)) ([#179](https://github.com/shinybrar/vosfs/issues/179)) ([e2ff33f](https://github.com/shinybrar/vosfs/commit/e2ff33fe882129a6df2ff51d08a05139a626ffb5))
* **fsspec-cli:** add empty-directory rmdir ([#157](https://github.com/shinybrar/vosfs/issues/157)) ([5567c84](https://github.com/shinybrar/vosfs/commit/5567c84bc1346debe7091d68d17ecde9647a5123))
* **fsspec-cli:** add exact rm -f profile ([#133](https://github.com/shinybrar/vosfs/issues/133)) ([#170](https://github.com/shinybrar/vosfs/issues/170)) ([5aecd2e](https://github.com/shinybrar/vosfs/commit/5aecd2edb22a03f50168453af090e9d607f60628))
* **fsspec-cli:** add mkdir -p through delegated _makedirs ([#129](https://github.com/shinybrar/vosfs/issues/129)) ([#161](https://github.com/shinybrar/vosfs/issues/161)) ([3380746](https://github.com/shinybrar/vosfs/commit/338074602ef2adc5317f4794afc75e1f7025a27e))
* **fsspec-cli:** add optional basename suffix operand ([#124](https://github.com/shinybrar/vosfs/issues/124)) ([#159](https://github.com/shinybrar/vosfs/issues/159)) ([98b8fe3](https://github.com/shinybrar/vosfs/commit/98b8fe3c3051d5adecf0ab7c8dfdcaa290ddea2f))
* **fsspec-cli:** add reduced BSD/macOS-shaped stat ([#146](https://github.com/shinybrar/vosfs/issues/146)) ([2971c43](https://github.com/shinybrar/vosfs/commit/2971c4347fed67e2c18d7547d79bbd5696e1055c))
* **fsspec-cli:** add rm -d for files and empty directories ([#134](https://github.com/shinybrar/vosfs/issues/134)) ([#176](https://github.com/shinybrar/vosfs/issues/176)) ([7252765](https://github.com/shinybrar/vosfs/commit/7252765e56cfd3c4fa91c8c6f8040978ff692be8))
* **fsspec-cli:** add same-source file mv ([#141](https://github.com/shinybrar/vosfs/issues/141)) ([#171](https://github.com/shinybrar/vosfs/issues/171)) ([b0cdbbb](https://github.com/shinybrar/vosfs/commit/b0cdbbb0b02437e7c4d1aeea23e989ea5944c7d6))
* **fsspec-cli:** add source-free basename string command ([#153](https://github.com/shinybrar/vosfs/issues/153)) ([c074e37](https://github.com/shinybrar/vosfs/commit/c074e370fd3727f1e53b446395829889cf3a03d6))
* **fsspec-cli:** add source-free dirname string ([#125](https://github.com/shinybrar/vosfs/issues/125)) ([#160](https://github.com/shinybrar/vosfs/issues/160)) ([4cb5e56](https://github.com/shinybrar/vosfs/commit/4cb5e56ffdf57c092c8078eb4ab6f49c7642580c))
* **fsspec-cli:** add verified cross-source cp ([#138](https://github.com/shinybrar/vosfs/issues/138)) ([#172](https://github.com/shinybrar/vosfs/issues/172)) ([063f174](https://github.com/shinybrar/vosfs/commit/063f1741b5e2a323119769c4a1f3e36754428bac))
* **fsspec-cli:** add verified same-source two-operand cp ([#137](https://github.com/shinybrar/vosfs/issues/137)) ([#164](https://github.com/shinybrar/vosfs/issues/164)) ([ef371b5](https://github.com/shinybrar/vosfs/commit/ef371b588f5e61cb6b5de6e6034d5eb1a68c3a6f))
* **fsspec-cli:** add XSI unlink for one mapped file ([#155](https://github.com/shinybrar/vosfs/issues/155)) ([371bf98](https://github.com/shinybrar/vosfs/commit/371bf98504046813e2a601f04f2d8a05c0b27a61))
* **fsspec-cli:** binary stdin and dash sequencing for cat ([#162](https://github.com/shinybrar/vosfs/issues/162)) ([82ab5d5](https://github.com/shinybrar/vosfs/commit/82ab5d5fab8e9435f2b09eae6e3b7cff5c5c9bde)), closes [#127](https://github.com/shinybrar/vosfs/issues/127)
* **fsspec-cli:** exact multi-source cp with type=file ([#139](https://github.com/shinybrar/vosfs/issues/139)) ([d4194a9](https://github.com/shinybrar/vosfs/commit/d4194a93f50c5bdfe458fd23e9fef21514adc4d7))
* **fsspec-cli:** multi-file same-source mv into directory ([#144](https://github.com/shinybrar/vosfs/issues/144)) ([#180](https://github.com/shinybrar/vosfs/issues/180)) ([afcf3e1](https://github.com/shinybrar/vosfs/commit/afcf3e15cccecc8131bf6bf533eada1dce3a3b64))
* **fsspec-cli:** reject cross-source mv ([#142](https://github.com/shinybrar/vosfs/issues/142)) ([#174](https://github.com/shinybrar/vosfs/issues/174)) ([50a8f9f](https://github.com/shinybrar/vosfs/commit/50a8f9fd40040da316ce848ad64956f9dd618a62))


### Bug Fixes

* **ci:** unwedge Windows hangs in cat subprocess tests ([440a6f2](https://github.com/shinybrar/vosfs/commit/440a6f25547b9290bd0ba86e197ee61408d73857))


### Documentation

* **fsspec-cli:** reject directory mv after research ([#143](https://github.com/shinybrar/vosfs/issues/143)) ([fceef2d](https://github.com/shinybrar/vosfs/commit/fceef2d8963f88a31dd64ffc88352de40672cbc8))
* **fsspec-cli:** reject recursive cp -R admission ([#140](https://github.com/shinybrar/vosfs/issues/140)) ([#173](https://github.com/shinybrar/vosfs/issues/173)) ([be2fcb1](https://github.com/shinybrar/vosfs/commit/be2fcb14cec39ddac1ab99dde7df7ad9a6b59785))
* **fsspec-cli:** reject recursive rm -R/-r admission ([#135](https://github.com/shinybrar/vosfs/issues/135)) ([#175](https://github.com/shinybrar/vosfs/issues/175)) ([dc5eedb](https://github.com/shinybrar/vosfs/commit/dc5eedb3c76b8ffd13b9df223de2680e2132fb9c))

## [0.1.1](https://github.com/shinybrar/vosfs/compare/fsspec-cli-v0.1.0...fsspec-cli-v0.1.1) (2026-07-17)


### Features

* **fsspec-cli:** activate independent release automation ([#148](https://github.com/shinybrar/vosfs/issues/148)) ([0a9f730](https://github.com/shinybrar/vosfs/commit/0a9f7302dcc700fcd46ccc170b47ba7640cc83ac)), closes [#107](https://github.com/shinybrar/vosfs/issues/107) [#108](https://github.com/shinybrar/vosfs/issues/108)
* **fsspec-cli:** add async Typer boundary ([#111](https://github.com/shinybrar/vosfs/issues/111)) ([adfef30](https://github.com/shinybrar/vosfs/commit/adfef30158ab1d2a72dd5f9d924d3dd65b362415)), closes [#100](https://github.com/shinybrar/vosfs/issues/100)
* **fsspec-cli:** add deterministic ls renderer ([#115](https://github.com/shinybrar/vosfs/issues/115)) ([460d9fc](https://github.com/shinybrar/vosfs/commit/460d9fc37e54d7ca4672e5131a7e102a1a28a749)), closes [#103](https://github.com/shinybrar/vosfs/issues/103)
* **fsspec-cli:** add directory listing engine ([#117](https://github.com/shinybrar/vosfs/issues/117)) ([3efeb52](https://github.com/shinybrar/vosfs/commit/3efeb52e40f700aeb6983364dfae13de75e44c72)), closes [#102](https://github.com/shinybrar/vosfs/issues/102)
* **fsspec-cli:** manage source lifecycle ([#116](https://github.com/shinybrar/vosfs/issues/116)) ([1660dbe](https://github.com/shinybrar/vosfs/commit/1660dbeff0b34083de74af210dcd17f306bd2320)), closes [#101](https://github.com/shinybrar/vosfs/issues/101)


### Bug Fixes

* **ci:** accept generated release changelogs ([308d45d](https://github.com/shinybrar/vosfs/commit/308d45d60da2b68686e6161f920c6616f5d066bd))

## 0.1.0 (2026-07-17)


### Features

* **fsspec-cli:** activate independent release automation ([#148](https://github.com/shinybrar/vosfs/issues/148)) ([0a9f730](https://github.com/shinybrar/vosfs/commit/0a9f7302dcc700fcd46ccc170b47ba7640cc83ac)), closes [#107](https://github.com/shinybrar/vosfs/issues/107) [#108](https://github.com/shinybrar/vosfs/issues/108)
* **fsspec-cli:** add async Typer boundary ([#111](https://github.com/shinybrar/vosfs/issues/111)) ([adfef30](https://github.com/shinybrar/vosfs/commit/adfef30158ab1d2a72dd5f9d924d3dd65b362415)), closes [#100](https://github.com/shinybrar/vosfs/issues/100)
* **fsspec-cli:** add deterministic ls renderer ([#115](https://github.com/shinybrar/vosfs/issues/115)) ([460d9fc](https://github.com/shinybrar/vosfs/commit/460d9fc37e54d7ca4672e5131a7e102a1a28a749)), closes [#103](https://github.com/shinybrar/vosfs/issues/103)
* **fsspec-cli:** add directory listing engine ([#117](https://github.com/shinybrar/vosfs/issues/117)) ([3efeb52](https://github.com/shinybrar/vosfs/commit/3efeb52e40f700aeb6983364dfae13de75e44c72)), closes [#102](https://github.com/shinybrar/vosfs/issues/102)
* **fsspec-cli:** manage source lifecycle ([#116](https://github.com/shinybrar/vosfs/issues/116)) ([1660dbe](https://github.com/shinybrar/vosfs/commit/1660dbeff0b34083de74af210dcd17f306bd2320)), closes [#101](https://github.com/shinybrar/vosfs/issues/101)


### Bug Fixes

* **ci:** accept generated release changelogs ([308d45d](https://github.com/shinybrar/vosfs/commit/308d45d60da2b68686e6161f920c6616f5d066bd))

## 0.0.0 (unreleased bootstrap)

No GitHub Release or tag was published for this bootstrap marker. Release
Please owns the first real `0.1.0` entry and every later version.
