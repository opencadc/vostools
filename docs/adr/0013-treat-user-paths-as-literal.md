# Treat user paths as literal

Status: Accepted

Question: Decoding a user path once, then tagging it so fsspec does not decode
it again, needs an adapter around every bulk hook. fsspec applies
`_strip_protocol` more than once. A second decode changes the name.

## Decision

**User paths are literal. `strip_protocol` does not percent-decode them.**
`?` and `#` are allowed in a name. NUL, encoded separators, and `.` / `..`
segments are still rejected.

Wire encoding stays in `encode_url_path`. A node URI from the service is
decoded once when it becomes an internal path. The path-marker adapter and
its coordinator overrides are removed.

This is a breaking change. A caller who typed `file%20name` to mean `file name`
now addresses a node whose name contains the percent sign. The name `file name`
is typed as `file name` and encoded on the wire.

## Consequences

- fsspec bulk helpers can call `_strip_protocol` repeatedly.
- There is no deprecation period. The previous decode-once rule is retired.
- fss-cli accepts `str` subclasses in hook results, so a specialised path
  string is not rejected as a non-string.
