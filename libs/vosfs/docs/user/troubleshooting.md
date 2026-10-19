# Troubleshooting

Organized by what you saw, not by exception class. For the exception-to-cause
table, see the [User Guide](guide.md#errors).

## Getting a credential

Most first-time problems are credential problems. `vosfs` **consumes** tokens
and certificates — it never acquires or refreshes them.

=== "CADC proxy certificate"

    Install the CADC tools and fetch a proxy certificate. It is valid for a
    limited period and must be renewed:

    ```bash
    pip install cadcutils
    cadc-get-cert -u YOUR_CADC_USERNAME
    ```

    That writes `~/.ssl/cadcproxy.pem`. Point `vosfs` at it with an
    **absolute** path:

    ```bash
    export VOSFS_CERT_FILE="$HOME/.ssl/cadcproxy.pem"
    ```

=== "Bearer token"

    If your environment issues OIDC access tokens, use one directly:

    ```bash
    export VOSFS_TOKEN_FILE=/run/secrets/vos-token
    ```

    Both variables are reread from the environment before every request, and
    neither value is captured into the filesystem's storage options, so
    neither is serialized. The equivalent *constructor* option `token=` is
    different: a literal token passed there **is** included in fsspec pickle
    and JSON output, so prefer `tokenfile=` or an environment source when the
    filesystem may be serialized.

Set **exactly one** credential source. Any explicit constructor option
(`token`, `tokenfile`, `certfile`) makes `vosfs` ignore *all* credential
environment variables.

## `PermissionError` on the first call

In order of likelihood:

1. **The certificate expired.** Proxy certificates are short-lived. Re-run
   `cadc-get-cert`. Check the expiry with
   `openssl x509 -enddate -noout -in ~/.ssl/cadcproxy.pem`.
2. **No credential was picked up.** Confirm the variable is exported in *this*
   shell: `echo $VOSFS_CERT_FILE`. An unset credential means anonymous access,
   which is refused for private paths.
3. **You set both a constructor option and an environment variable.** The
   explicit option wins and the variable is ignored — including when the
   explicit one is wrong.
4. **You do not have access to that path.** Try your own home:
   `fs.ls("/home/YOUR_CADC_USERNAME")`.

## `FileNotFoundError` on a path that exists

The authority after `vos://` is **part of the path**, not a server selector.
These are all the same path `/a/b`:

```text
vos://a/b     vos:///a/b     /a/b     a/b
```

So `vos://myproject/data.csv` means `/myproject/data.csv` — it does not mean
"host `myproject`". If you meant a different service, change `endpoint_url`.

## `ValueError` mentioning `endpoint_url`

`endpoint_url` must be an absolute service base URL, and must use `https`
whenever a credential is configured. There is no registry or shortname lookup:

```python
fs = fsspec.filesystem("vos", endpoint_url="https://staging.canfar.net/arc")
```

## A path with `~` is not found

`~` is not expanded. Python libraries do not do shell expansion. Use an
absolute path, or expand it yourself:

```python
from pathlib import Path
certfile = str(Path("~/.ssl/cadcproxy.pem").expanduser())
```

## `NotImplementedError`

The operation is outside the OpenCADC profile and fails fast, before any remote
mutation:

| You tried | Why it fails | Instead |
| --- | --- | --- |
| Appending, or `"a"` / `"r+"` mode | One whole `PUT` per file | Read, modify, write the whole object |
| `touch(truncate=False)` | Needs a partial update | Use `touch()` (truncating) |
| `rm(..., maxdepth=...)` | Bounded-depth deletion is not modeled | `rm(recursive=True)`, or delete explicitly |
| Moving a `LinkNode` | Not in the profile | Recreate the link at the target |
| Reading an external `LinkNode`'s bytes | Target is outside the service | Fetch the target yourself |

## Things that are absent rather than raising

These do not raise `NotImplementedError` — there is simply nothing to call, or
the behavior silently differs from what you may expect:

| Expectation | Reality |
| --- | --- |
| A ranged read transfers only those bytes | On backends that answer `206` (for example minoc/`vault`), yes. On Cavern, `Range` is ignored and `vosfs` falls back to a whole-object read then local slice. |
| `blockcache::` / `cached::` wrappers | Not supported; use `simplecache::` or `filecache::` instead. |
| FUSE mounting | Not provided. Use the fsspec API or [`fss-cli`](cli/index.md). |
| `created` timestamps, `open_async` | Not part of the profile. |

## `TimeoutError` or `ConnectionError`

`vosfs` never retries automatically — retry policy is yours. Whole-object
transfer means a large file is one long request, so raise the timeout rather
than the retry count:

```python
fs = fsspec.filesystem(
    "vos",
    endpoint_url="https://staging.canfar.net/arc",
    timeouts={"read": 600.0},
)
```

A bulk `get`, `put`, `copy`, or `cat` runs at most `batch_size` transfers at
once (32 by default). If you raise `batch_size` far above the HTTP pool's 100
connections, queued transfers can wait past the `pool` timeout and fail; keep
it at or below 100.

## `OSError` with `errno.ENOSPC`

A storage quota is exhausted. Free space or request more; this is not a client
problem.

## Dask reads fail or return wrong partitions

Pass `blocksize=None`. Dask otherwise splits files by byte offset, which Cavern
cannot serve. One file becomes one partition.

## Everything hangs, or fails after `fork()`

A live filesystem belongs to the process that constructed it. Do not use an
inherited instance in a child process or worker — reconstruct it there from the
same storage options, pickle, or fsspec JSON.

Also make sure you are not calling synchronous methods from inside a running
event loop; construct with `asynchronous=True` and await the `_`-prefixed
methods instead.

## Writes appear truncated after a failure

Once a `PUT` has begun, failure is uncertain and may have truncated the
destination. `vosfs` invalidates its cached state but does not roll back. After
a failed write, verify with `fs.info(path)` before assuming either outcome.

For coordinated `put`/`pipe`, cancellation stops later items and waits for
started writes to close — but a dispatched `PUT` remains uncertain.

## Still stuck

Check the [User Guide](guide.md) for the full capability and error tables, and
the [capability contract](https://github.com/opencadc/vostools/blob/main/libs/vosfs/docs/design/trd.md)
for the normative surface. Bug reports go to
[the issue tracker](https://github.com/opencadc/vostools/issues).
