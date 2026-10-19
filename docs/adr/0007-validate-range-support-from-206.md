# Validate Range support from HTTP 206

Status: Accepted

Question: Some OpenCADC byte backends (minoc behind `vault`) honour `Range`
with a correct `206`. Others (Cavern) ignore `Range` and return `200` with the
whole object. How should `vosfs` expose partial reads without corrupting
Cavern results or trusting advisory headers?

## Decision

**Send `Range` for partial `cat_file` / `cat_ranges` reads when the bounds map
to one HTTP range. Keep the partial body only on a validated `206`. Treat
`200`/`204` as a whole-object fallback and slice locally.**

A known total in Content-Range determines the expected clipped interval,
including suffix and open-ended ranges. Reject bodies whose endpoints or lengths
do not match it. An unknown total is accepted only for an exactly matching
closed interval. A 416 is an empty slice only when `bytes */TOTAL` proves the
requested start is beyond EOF (or the object is empty); otherwise it fails.

Do not treat `Accept-Ranges: bytes` as proof of support. Do not assume a `200`
body is a partial. The default `open("rb")` stays whole-object. An opt-in
buffered open (`block_size` or `cache_type`) may send `Range`; if a later
read returns `200` instead of `206`, that response body is staged and the
file stops sending ranges. `blockcache::` / `cached::` remain unsupported
until a separate contract claims them.

## Consequences

- `vault`/minoc partial reads transfer only the requested bytes.
- Cavern keeps correct results via whole-object fallback (same as before for
  correctness; `cat_ranges` still stages to disk on `200`).
- Callers must not infer block-cache safety from this ADR alone.

## Notes

Normative detail lives in [`libs/vosfs/docs/design/trd.md`](../../libs/vosfs/docs/design/trd.md) §8.
Informative deployment probes:
[`libs/vosfs/docs/research/vosfs-vault-minoc-byte-range-capability.md`](../../libs/vosfs/docs/research/vosfs-vault-minoc-byte-range-capability.md).
