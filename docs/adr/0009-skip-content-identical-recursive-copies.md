# Skip content-identical files during recursive copy

Status: Accepted

Repeated recursive copies should resume without rewriting already-copied
files. Existence, size, and modification time do not prove content equality.

## Decision

For recursive `cp`, skip an existing regular file only after content equality
has been established. Compatible explicit content checksums can supply that
proof. Opaque ETags, equal sizes, and equal timestamps cannot.

When compatible checksums are unavailable, stage both contents and compare
SHA-256 hashes using bounded local reads. Perform blocking local hashing off
the command loop. If the contents differ, reuse the staged source for the
upload instead of downloading it again. A missing destination is copied;
an unreadable destination fails explicitly rather than being assumed equal.

Preserve destination preflight, source-manifest revalidation, destination
verification, containment checks, and complete temporary cleanup. A skip is
part of the verified copy outcome, not a reason to bypass those checks.
Retain the destination metadata proof for every verified skip and check it
again at completion; source and destination opaque tokens need not match.
Recursive copy remains a merge, not an exact mirror, transaction, or snapshot.

Keep the manifest entry bound before materializing child metadata: after the
walk row and collection shapes are accepted, an advertised oversized child
collection is rejected without indexed metadata reads. This limit diagnostic
precedes validation of child values in that collection.

## Consequences

- Repeating a recursive copy can avoid all writes to identical files.
- Without content checksums, proving equality still transfers both objects to
  local staging; it saves destination writes, not necessarily network reads.
- MD5 equality is a practical accidental-corruption check, not an adversarial
  authenticity guarantee. SHA-256 comparison is the byte-comparison fallback.
- This changes recursive copying only. The existing non-recursive file-copy
  metadata proof and overwrite behavior remain separate.

Normative detail belongs in `libs/vosfs/docs/design/fss-cli/commands.md`.
