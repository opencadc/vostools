# Move nodes with a transfers job

Status: Accepted

Question: Moving bytes through the client is correct when the service cannot
rename, and expensive when it can. OpenCADC implements same-service `moveNode`
as a UWS job and rejects `copyNode`.

## Decision

**Try a `/transfers` job with `keepBytes=false` for a same-service move that
has no `maxdepth`.** Plan checks stay first: a link is refused, and the
destination must be absent. The transfer's `direction` is the destination URI;
both node paths are URI-encoded. The job URL must be same-origin. Start with
`PHASE=RUN` at the job's `/phase` resource, then poll with UWS `WAIT` and the
previous phase. On cancellation or timeout, POST `PHASE=ABORT` to `/phase`.

Fall back to the client-side copy-then-delete move when `maxdepth` is set,
`/transfers` is not advertised, or the job is rejected as unsupported, including
a terminal unsupported-operation error. Permission errors and uncertain job
outcomes propagate without starting a competing transfer.
`copyNode` stays client-side.

## Consequences

- A service that implements `moveNode` renames without shipping the bytes.
- A service that does not advertise `/transfers` keeps today's client move.
- Cancelling the caller aborts the job instead of leaving it running.
