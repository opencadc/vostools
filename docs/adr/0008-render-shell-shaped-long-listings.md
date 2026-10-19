# Render long listings in shell column order

Status: Accepted

The long listing currently omits unsupported columns. This changes their
positions across backends and prevents the familiar interpretation of `ls -l`.
The user requested shell-shaped output rather than a copy of the legacy VOSpace
listing, which has separate read-group and write-group columns.

## Decision

Render mode, link count, owner, group, size, modification time, and pathname in
that order. Retain unsupported columns with explicit unknown markers instead
of inventing values. Render old or sufficiently future timestamps with a year;
recent timestamps show hours and minutes in the local timezone.

Real POSIX modes and identities remain authoritative when supplied. VOSpace
access metadata is normalized by vosfs, not interpreted by fsspec-cli. Read
and write ACL groups remain distinct and are represented together in the
single group column as `r=GROUPS,w=GROUPS` when no owning group is available.
This is an access summary, not an assertion of POSIX group ownership. Multiple
groups are comma-separated; known empty groups use `NONE`, absent groups use
an unknown marker.

VOSpace permissions are an advisory summary of its access properties, not
POSIX execute semantics. Unknown bits remain unknown. Lock state is preserved
in normalized metadata rather than adding a legacy `LOCKED` column to `ls`.
The full identities and access properties remain available through `info`.

Directly addressed links can be rendered without following their target.
All displayed strings are validated before output so newline and carriage
return characters cannot create or overwrite records.

## Consequences

- This supersedes adaptive column omission in the CLI contract and the sparse
  metadata presentation clause of ADR 0006. Sparse metadata stays valid.
- The output change needs release notes because scripts may parse columns.
- No allocated-block `total` is fabricated from logical byte size.
- No new command, formatter registry, backend-class dispatch, or capability
  setting is introduced.

Normative detail belongs in `libs/vosfs/docs/design/fss-cli/contract.md` and
`libs/vosfs/docs/design/fss-cli/commands.md`.
