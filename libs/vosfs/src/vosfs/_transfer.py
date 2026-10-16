"""The OpenCADC VOSpace synchronous byte-transfer protocol.

Reading or writing bytes is never a direct request to the node URL. The client
first negotiates a transfer: it POSTs a transfer document to the service's sync
binding, follows an approved 303 chain to the transfer-details document, and
picks a protocol whose security method matches the configured credential. Only
then does it issue the single byte GET/HEAD/PUT against the negotiated
endpoint.

Credential routing is decided by the *negotiated* security method, not by the
filesystem's own credential: a pre-authorized endpoint receives no credential
at all. That distinction is the reason this layer is separate from the fsspec
adapter in :mod:`vosfs.filesystem`.
"""

from __future__ import annotations

import contextlib
import logging
import re
from functools import partial
from pathlib import Path
from typing import TYPE_CHECKING, Any, NoReturn, cast
from urllib.parse import quote, urlencode, urlsplit

import httpx

from vosfs import _coordination as coordination
from vosfs import _integrity, capabilities, errors, negotiate, nodes, staging, transport

if TYPE_CHECKING:
    from collections.abc import AsyncIterator, Callable, Mapping, Sequence

    from vosfs.filesystem import VOSpaceFileSystem
    from vosfs.negotiate import NegotiatedEndpoint
    from vosfs.nodes import Node

DEFAULT_CONTENT_TYPE = "application/octet-stream"
_CONTENT_RANGE = re.compile(r"bytes\s+(\d+)-(\d+)/(\d+|\*)\s*$", re.IGNORECASE)
#: Ranged GETs issued together for one object once the endpoint has proven 206.
RANGE_CONCURRENCY = 8
COPY_CONNECTIONS = 2

logger = logging.getLogger(__name__)


class _RedirectFormUnsupportedError(Exception):
    """The service did not answer a read with a direct byte redirect."""


async def negotiate_endpoint(
    filesystem: VOSpaceFileSystem, path: str, *, direction: str, protocol_uri: str
) -> NegotiatedEndpoint:
    """Negotiate a byte endpoint for one logical transfer of ``path``.

    A pull tries the URL-parameter ``REQUEST=redirect`` form first. A service
    that answers with an error, an XML body, or a redirect to transfer details
    is remembered, and later pulls use the XML document. A push always uses
    the XML document. Redirect loops and more than five hops fail.
    """
    if direction == negotiate.DIRECTION_PULL and not filesystem._redirect_reads_unsupported:
        try:
            return await _negotiate_redirect_read(filesystem, path, protocol_uri=protocol_uri)
        except _RedirectFormUnsupportedError:
            filesystem._redirect_reads_unsupported = True
    return await _negotiate_document(filesystem, path, direction=direction, protocol_uri=protocol_uri)


async def _negotiate_redirect_read(
    filesystem: VOSpaceFileSystem,
    path: str,
    *,
    protocol_uri: str,
) -> NegotiatedEndpoint:
    """POST the URL-parameter pull form and accept only a direct byte redirect."""
    bindings = await filesystem._get_bindings()
    sync_url = bindings.require_sync()
    authority = await filesystem._require_authority()
    target = f"vos://{authority}{path}"
    query = urlencode(
        {
            "TARGET": target,
            "DIRECTION": negotiate.DIRECTION_PULL,
            "PROTOCOL": protocol_uri,
            "SECURITYMETHOD": filesystem._security_method(),
            "REQUEST": "redirect",
        },
        quote_via=quote,
    )
    post = await filesystem._send_to_service("POST", f"{sync_url}?{query}", content=b"", headers=nodes.XML_HEADERS)
    if post.status_code >= transport.HTTP_BAD_REQUEST or post.status_code == transport.HTTP_OK:
        raise _RedirectFormUnsupportedError
    if post.status_code != transport.HTTP_SEE_OTHER:
        raise _RedirectFormUnsupportedError
    location = negotiate.validate_redirect(
        post.headers.get("location"),
        base=sync_url,
        sending_bearer=False,
    )
    if not negotiate.is_direct_byte_endpoint(location):
        raise _RedirectFormUnsupportedError
    return _negotiated(
        path,
        negotiate.DIRECTION_PULL,
        negotiate.NegotiatedEndpoint(location, capabilities.ANONYMOUS_METHOD),
    )


async def _negotiate_document(
    filesystem: VOSpaceFileSystem, path: str, *, direction: str, protocol_uri: str
) -> NegotiatedEndpoint:
    """POST an XML transfer document and follow its 303 chain."""
    bindings = await filesystem._get_bindings()
    sync_url = bindings.require_sync()
    authority = await filesystem._require_authority()
    target = f"vos://{authority}{path}"
    document = nodes.build_transfer_document(target, direction=direction, protocols=[protocol_uri])
    post = await filesystem._send_to_service("POST", sync_url, content=document, headers=nodes.XML_HEADERS)
    filesystem._raise_for_status(post, path=path, allowed=(transport.HTTP_SEE_OTHER,))
    location = negotiate.validate_redirect(
        post.headers.get("location"),
        base=sync_url,
        sending_bearer=False,
    )
    seen: set[str] = set()
    for _redirect_count in range(1, 6):
        if location in seen:
            msg = "synchronous-transfer redirect loop"
            raise errors.VOSpaceError(msg)
        seen.add(location)
        if negotiate.is_direct_byte_endpoint(location):
            return _negotiated(
                path,
                direction,
                negotiate.NegotiatedEndpoint(location, capabilities.ANONYMOUS_METHOD),
            )
        location = negotiate.validate_redirect(
            location,
            base=location,
            sending_bearer=(
                filesystem._credential.method == "token" and transport.same_origin(location, filesystem.endpoint_url)
            ),
        )
        details = await filesystem._send_to_service("GET", location, headers=nodes.XML_HEADERS)
        filesystem._raise_for_status(details, path=path, allowed=(transport.HTTP_OK, transport.HTTP_SEE_OTHER))
        if details.status_code == transport.HTTP_OK:
            return _negotiated(
                path,
                direction,
                negotiate.choose_protocol(
                    negotiate.parse_transfer_details(details.content),
                    filesystem._security_method(),
                ),
            )
        location = negotiate.validate_redirect(
            details.headers.get("location"),
            base=location,
            sending_bearer=False,
        )
    msg = "synchronous-transfer negotiation returned more than five redirects"
    raise errors.VOSpaceError(msg)


def _negotiated(path: str, direction: str, endpoint: NegotiatedEndpoint) -> NegotiatedEndpoint:
    """Record the negotiated endpoint at DEBUG without query or token material."""
    logger.debug(
        "negotiated %s for %s: endpoint %s, security method %s",
        direction,
        path,
        errors.loggable_url(endpoint.url),
        endpoint.security_method or "anonymous",
    )
    return endpoint


def byte_routing(filesystem: VOSpaceFileSystem, endpoint: NegotiatedEndpoint) -> tuple[dict[str, str], bool]:
    """Return the headers and cert flag for a negotiated byte request.

    Credentials are routed by the negotiated security method: a
    pre-authorized or anonymous endpoint gets nothing; a token endpoint gets
    a freshly resolved bearer header over https; a certificate endpoint uses
    the X.509 client over https.

    The negotiated endpoint URL is validated first: it must be an absolute
    ``http``/``https`` URL without userinfo. A ``user:pass@host`` endpoint is
    rejected before the request is built, because HTTPX would otherwise
    derive a ``Basic`` ``Authorization`` header from the URL userinfo and
    defeat the credential-routing guarantee.
    """
    method = endpoint.security_method
    parts = urlsplit(endpoint.url)
    if parts.scheme not in ("http", "https") or not parts.netloc:
        msg = f"negotiated byte endpoint is not an absolute http(s) URL: {endpoint.url!r}"
        raise errors.VOSpaceError(msg)
    if parts.username or parts.password:
        msg = "negotiated byte endpoint must not contain userinfo"
        raise errors.VOSpaceError(msg)
    if method == capabilities.ANONYMOUS_METHOD:
        return {}, False
    scheme = parts.scheme
    if method == capabilities.TOKEN_METHOD:
        if scheme != "https":
            msg = "a token byte endpoint must use https"
            raise errors.VOSpaceError(msg)
        bearer = filesystem._credential.read_bearer()
        return {"Authorization": f"Bearer {bearer}"}, False
    if method == capabilities.CERTIFICATE_METHOD:
        if scheme != "https":
            msg = "a certificate byte endpoint must use https"
            raise errors.VOSpaceError(msg)
        return {}, True
    msg = f"unsupported negotiated security method: {method!r}"  # pragma: no cover
    raise errors.VOSpaceError(msg)  # pragma: no cover


async def byte_send(  # noqa: PLR0913 - one parameter per HTTP request element.
    filesystem: VOSpaceFileSystem,
    endpoint: NegotiatedEndpoint,
    method: str,
    *,
    content: bytes | None = None,
    headers: Mapping[str, str] | None = None,
    stream: bool = False,
    accounted: bool = False,
) -> httpx.Response:
    """Perform the one byte GET/HEAD/PUT against a negotiated endpoint.

    A redirect (3xx) response fails: only the approved synchronous-transfer
    303 chain may redirect.
    """
    filesystem._ensure_usable()
    request_headers, use_cert = byte_routing(filesystem, endpoint)
    request_headers.update(headers or {})
    request = httpx.Request(method, endpoint.url, headers=request_headers, content=content)
    try:
        response = await filesystem._pool.send(
            request,
            use_cert=use_cert,
            stream=stream,
            accounted=accounted,
        )
    except httpx.HTTPError as exc:
        raise errors.transport_exception(exc, path=endpoint.url) from exc
    if response.is_redirect:
        if stream:
            await response.aclose()
        msg = f"unexpected redirect from byte endpoint: {response.status_code}"
        raise errors.VOSpaceError(msg, status=response.status_code)
    return response


# -- reading bytes -------------------------------------------------------


async def validate_read_target(filesystem: VOSpaceFileSystem, path: str) -> Node:
    """Reject external LinkNodes before synchronous transfer negotiation.

    The node is fetched first and without its child listing: parsing it
    records the VOSpace authority, so a first read needs no separate root GET.
    """
    node = filesystem._parse_and_note(await filesystem._get_node_document(path, children=False))
    if node.node_type == "link":
        await reject_external_link(filesystem, cast("str", node.target))
    return node


async def reject_external_link_info(filesystem: VOSpaceFileSystem, info: Mapping[str, Any]) -> None:
    """Apply :func:`reject_external_link` to an fsspec info dict, if a link."""
    if info.get("islink"):
        await reject_external_link(filesystem, info.get("target") or "")


async def reject_external_link(filesystem: VOSpaceFileSystem, target: str) -> None:
    """Raise ``NotImplementedError`` unless ``target`` is in this VOSpace."""
    authority = await filesystem._require_authority()
    parts = urlsplit(target)
    if parts.scheme != "vos" or parts.netloc != authority:
        msg = "external LinkNode byte reads are unsupported"
        raise NotImplementedError(msg)


async def preflight_read_target(filesystem: VOSpaceFileSystem, path: str) -> Node | None:
    """Validate byte-read capability and LinkNode target before staging.

    A cached listing entry is enough to reject an external link, so a read
    after ``ls`` does not fetch the node again.
    """
    (await filesystem._get_bindings()).require_sync()
    cached = filesystem._cached_info(path)
    if cached is not None:
        await reject_external_link_info(filesystem, cached)
        return None
    return await validate_read_target(filesystem, path)


async def open_read_stream(
    filesystem: VOSpaceFileSystem, path: str, *, target_validated: bool = False
) -> httpx.Response:
    """Negotiate a read and return the open, streaming byte response."""
    (await filesystem._get_bindings()).require_sync()
    if not target_validated:
        await preflight_read_target(filesystem, path)
    endpoint = await negotiate_endpoint(
        filesystem,
        path,
        direction=negotiate.DIRECTION_PULL,
        protocol_uri=negotiate.PROTOCOL_HTTPS_GET,
    )
    response = await byte_send(filesystem, endpoint, "GET", headers=transport.IDENTITY_ENCODING, stream=True)
    if response.status_code not in (transport.HTTP_OK, transport.HTTP_NO_CONTENT):
        try:
            await _raise_byte_error(response, path)
        finally:
            await response.aclose()
    return response


async def read_whole(filesystem: VOSpaceFileSystem, path: str) -> bytes:
    """Download one whole object into memory (an empty 204 reads as ``b''``).

    Consumes the raw response bytes so HTTP content decoding can never alter
    filesystem content, matching the streamed ``_get_file`` path.
    """
    response = await open_read_stream(filesystem, path)
    try:
        return await _raw_body(response)
    finally:
        await response.aclose()


def http_range_header(start: int | None, end: int | None) -> str | None:
    """Map Python half-open bounds to an HTTP ``Range`` value, or ``None``.

    ``None`` means the caller should issue a whole GET (no partial request, or
    bounds that need the object size before they can be expressed).
    """
    if start is None and end is None:
        return None
    if start is not None and start < 0:
        return f"bytes={start}" if end is None else None
    if end is not None and end < 0:
        return None
    first = 0 if start is None else start
    if end is None:
        return f"bytes={first}-"
    if end <= first:
        return None
    return f"bytes={first}-{end - 1}"


def byte_size(info: Mapping[str, Any]) -> int | None:
    """Return a declared data length, never a link's or missing property's zero."""
    if info.get("type") != "file" or nodes.LENGTH_PROPERTY_URI not in info.get("properties", {}):
        return None
    size = info.get("size")
    if type(size) is int and size >= 0:
        return size
    return None


def _absolute_bound(bound: int | None, size: int) -> int | None:
    """Resolve one Python slice bound against ``size``."""
    if bound is None or bound >= 0:
        return bound
    return max(size + bound, 0)


async def _concrete_bounds(
    filesystem: VOSpaceFileSystem,
    path: str,
    start: int | None,
    end: int | None,
) -> tuple[int | None, int | None]:
    """Turn negative slice bounds into absolute bounds using ``_info`` size.

    Unknown size leaves the bounds unchanged, so the caller keeps today's
    whole-object fallback.
    """
    if not ((start is not None and start < 0) or (end is not None and end < 0)):
        return start, end
    size = byte_size(await filesystem._info(path))
    if size is None:
        return start, end
    return _absolute_bound(start, size), _absolute_bound(end, size)


async def read_slice(
    filesystem: VOSpaceFileSystem,
    path: str,
    start: int | None = None,
    end: int | None = None,
) -> bytes:
    """Return one object slice, using ``Range`` when the byte endpoint agrees."""
    if _is_empty_slice(start, end):
        await preflight_read_target(filesystem, path)
        return b""
    start, end = await _concrete_bounds(filesystem, path, start, end)
    if _is_empty_slice(start, end):
        await preflight_read_target(filesystem, path)
        return b""
    range_header = http_range_header(start, end)
    if range_header is None:
        return (await read_whole(filesystem, path))[start:end]
    values = await read_grouped_ranges(filesystem, path, [(0, start, end)])
    return values[0][1]


async def read_grouped_ranges(
    filesystem: VOSpaceFileSystem,
    path: str,
    ranges: Sequence[tuple[int, int | None, int | None]],
) -> list[tuple[int, bytes]]:
    """Return indexed slices for one object, ranging while responses are ``206``.

    The first range is requested alone. Once it returns a validated ``206`` the
    endpoint has proven Range support, so the remaining ranges are requested
    concurrently (bounded by :data:`RANGE_CONCURRENCY`) on the same negotiated
    endpoint. Any ``200``/``204`` falls back to one whole-object read sliced
    locally for every range of the object.
    """
    await preflight_read_target(filesystem, path)
    if not ranges:
        return []
    concrete: list[tuple[int, int | None, int | None]] = []
    for index, raw_start, raw_end in ranges:
        bounded_start, bounded_end = await _concrete_bounds(filesystem, path, raw_start, raw_end)
        concrete.append((index, bounded_start, bounded_end))
    ranges = concrete

    async def download(temp_path: str) -> None:
        await filesystem._download_file(path, temp_path, target_validated=True)

    range_headers = [http_range_header(start, end) for _index, start, end in ranges]
    if any(header is None for header in range_headers):
        return await staging.read_ranges(download, ranges)
    headers = cast("list[str]", range_headers)

    endpoint = await negotiate_endpoint(
        filesystem,
        path,
        direction=negotiate.DIRECTION_PULL,
        protocol_uri=negotiate.PROTOCOL_HTTPS_GET,
    )
    first = await read_range(filesystem, endpoint, path, headers[0])
    if isinstance(first, httpx.Response):
        return await _slice_streamed_object(first, ranges)

    async def fetch(range_header: str) -> bytes | None:
        result = await read_range(filesystem, endpoint, path, range_header)
        if isinstance(result, httpx.Response):
            await result.aclose()
            return None
        return result

    rest = await coordination.run_bounded(
        [partial(fetch, header) for header in headers[1:]],
        RANGE_CONCURRENCY,
    )
    if any(body is None for body in rest):
        logger.debug("byte endpoint stopped honouring Range for %s", path)
        return await staging.read_ranges(download, ranges)
    bodies = [first, *cast("list[bytes]", rest)]
    return [(index, body) for (index, _s, _e), body in zip(ranges, bodies, strict=True)]


async def read_range(
    filesystem: VOSpaceFileSystem,
    endpoint: NegotiatedEndpoint,
    path: str,
    range_header: str,
) -> bytes | httpx.Response:
    """Issue one ranged GET: validated bytes, or the open whole-object response."""
    headers = {**transport.IDENTITY_ENCODING, "Range": range_header}
    response = await byte_send(filesystem, endpoint, "GET", headers=headers, stream=True)
    keep_open = False
    try:
        status = response.status_code
        if status == transport.HTTP_PARTIAL_CONTENT:
            body = await _raw_body(response)
            _validate_partial(body, response.headers.get("content-range"), range_header)
            return body
        if status in (transport.HTTP_OK, transport.HTTP_NO_CONTENT):
            logger.debug("byte endpoint ignored Range for %s; reading whole", path)
            keep_open = True
            return response
        if status == transport.HTTP_RANGE_NOT_SATISFIABLE and _empty_range(
            response.headers.get("content-range"), range_header
        ):
            return b""
        await _raise_byte_error(response, path)
    finally:
        if not keep_open:
            await response.aclose()


def _is_empty_slice(start: int | None, end: int | None) -> bool:
    """Whether non-negative bounds select no bytes regardless of object size."""
    first = 0 if start is None else start
    return first >= 0 and end is not None and 0 <= end <= first


def _empty_range(content_range: str | None, range_header: str) -> bool:
    match = re.fullmatch(r"bytes \*/(\d+)", content_range or "", re.IGNORECASE)
    if match is None:
        return False
    total = int(match.group(1))
    start = range_header.removeprefix("bytes=").split("-", 1)[0]
    return total == 0 or (bool(start) and int(start) >= total)


async def _raise_byte_error(response: httpx.Response, path: str) -> NoReturn:
    """Raise the mapped exception for a failed byte response."""
    body = errors.bounded_text(await response.aread())
    raise errors.http_exception(
        response.status_code,
        body=body,
        fault=errors.extract_fault(body),
        path=path,
        retry_after=errors.parse_retry_after(response.headers.get("retry-after")),
    )


async def _raw_body(response: httpx.Response) -> bytes:
    if response.status_code == transport.HTTP_NO_CONTENT:
        return b""
    chunks = response.aiter_raw(_integrity.READ_CHUNK)
    return b"".join([chunk async for chunk in chunks])


def _validate_partial(body: bytes, content_range: str | None, range_header: str) -> None:
    if content_range is None:
        msg = "206 Partial Content without Content-Range"
        raise errors.VOSpaceError(msg, status=transport.HTTP_PARTIAL_CONTENT)
    match = _CONTENT_RANGE.fullmatch(content_range.strip())
    if match is None:
        msg = f"malformed Content-Range: {content_range!r}"
        raise errors.VOSpaceError(msg, status=transport.HTTP_PARTIAL_CONTENT)
    first, last = int(match.group(1)), int(match.group(2))
    if last < first or len(body) != last - first + 1:
        msg = "206 body does not match Content-Range"
        raise errors.VOSpaceError(msg, status=transport.HTTP_PARTIAL_CONTENT)
    total = None if match.group(3) == "*" else int(match.group(3))
    spec = range_header.removeprefix("bytes=")
    start, stop = spec.split("-", 1)
    if not start or not stop:
        if total is None:
            msg = "206 suffix or open range requires a known object length"
            raise errors.VOSpaceError(msg, status=transport.HTTP_PARTIAL_CONTENT)
        want_first = max(0, total - int(stop)) if not start else int(start)
        want_last = total - 1
    else:
        want_first, want_last = int(start), int(stop)
        if total is not None:
            want_last = min(want_last, total - 1)
    if (total is not None and last >= total) or first != want_first or last != want_last:
        msg = "206 Content-Range does not match request"
        raise errors.VOSpaceError(msg, status=transport.HTTP_PARTIAL_CONTENT)


async def _slice_streamed_object(
    response: httpx.Response,
    ranges: Sequence[tuple[int, int | None, int | None]],
) -> list[tuple[int, bytes]]:
    """Stage one whole-object response to disk and return local slices."""
    with await stage_response(response) as local:
        values = []
        for index, start, end in ranges:
            first, stop, _step = slice(start, end).indices(local.size)
            local.seek(first)
            values.append((index, local.read(max(0, stop - first))))
        return values


async def stage_response(response: httpx.Response) -> staging.StagedReadFile:
    """Consume and close a whole-object response into an owned seekable file."""
    temp_path = None
    try:
        try:
            temp_path = staging.new_temp_path()
            with Path(temp_path).open("wb") as local:  # noqa: ASYNC230 - disk-backed staging
                if response.status_code != transport.HTTP_NO_CONTENT:
                    async for chunk in response.aiter_raw(_integrity.READ_CHUNK):
                        local.write(chunk)
        finally:
            await response.aclose()
        return staging.StagedReadFile(temp_path)
    except BaseException:
        if temp_path is not None:
            staging.unlink_temp_path(temp_path)
        raise


# -- writing bytes -------------------------------------------------------


async def write_whole(  # noqa: PLR0913 - one parameter per PUT request element.
    filesystem: VOSpaceFileSystem,
    path: str,
    body: Any,  # noqa: ANN401 - httpx accepts bytes or an async byte iterator
    *,
    size: int | None,
    content_type: str | None,
    expected_digest: bytes | Callable[[], bytes] | None,
    accounted: bool = False,
    endpoint: NegotiatedEndpoint | None = None,
) -> None:
    """Perform one negotiated whole PUT, validating status and integrity.

    ``expected_digest`` may be a callable resolved only after the PUT returns,
    so a digest accumulated while the body streams is compared once complete.
    ``endpoint`` skips a second negotiation when the caller already has one.
    """
    if endpoint is None:
        endpoint = await negotiate_endpoint(
            filesystem,
            path,
            direction=negotiate.DIRECTION_PUSH,
            protocol_uri=negotiate.PROTOCOL_HTTPS_PUT,
        )
    headers = {"Content-Type": content_type or DEFAULT_CONTENT_TYPE}
    if size is not None:
        headers["Content-Length"] = str(size)
    try:
        response = await byte_send(
            filesystem,
            endpoint,
            "PUT",
            content=body,
            headers=headers,
            accounted=accounted,
        )
        if response.status_code == transport.HTTP_PRECONDITION_FAILED:
            msg = f"integrity check failed for {path}"
            raise errors.VOSpaceError(msg, status=transport.HTTP_PRECONDITION_FAILED)
        if response.status_code != transport.HTTP_CREATED:
            detail = errors.bounded_text(response.content)
            msg = (
                f"uncertain write to {path}: HTTP {response.status_code}; the target may have been truncated. {detail}"
            )
            raise errors.VOSpaceError(msg, status=response.status_code)
        expected = (
            expected_digest if expected_digest is None or isinstance(expected_digest, bytes) else expected_digest()
        )
        _integrity.verify_returned_digest(response, expected, path)
    finally:
        # Once PUT dispatch begins, success, failure, and cancellation can all
        # leave remote state changed. Never promise rollback; evict stale views.
        filesystem._invalidate(path)


async def copy_stream(
    filesystem: VOSpaceFileSystem,
    source: str,
    destination: str,
    *,
    size: int,
    source_md5: str | None,
) -> None:
    """Stream one object from a negotiated GET into a negotiated PUT.

    Both endpoints are negotiated before two connection slots are taken, so
    the copy cannot hold a download while waiting for an upload slot. The
    streamed MD5 is checked against the source ``contentMD5`` when the source
    has one, and against the digest the server returns. A destination this
    call created is deleted if the copy fails.
    """
    pull = await negotiate_endpoint(
        filesystem,
        source,
        direction=negotiate.DIRECTION_PULL,
        protocol_uri=negotiate.PROTOCOL_HTTPS_GET,
    )
    push = await negotiate_endpoint(
        filesystem,
        destination,
        direction=negotiate.DIRECTION_PUSH,
        protocol_uri=negotiate.PROTOCOL_HTTPS_PUT,
    )
    existed = await filesystem._exists(destination)
    digest = _integrity.new_md5()
    mutation_started = False

    async def chunks() -> AsyncIterator[bytes]:
        if response.status_code == transport.HTTP_NO_CONTENT:
            return
        async for chunk in response.aiter_raw(_integrity.READ_CHUNK):
            digest.update(chunk)
            yield chunk

    try:
        async with filesystem._pool.reserve(COPY_CONNECTIONS):
            response = await byte_send(
                filesystem,
                pull,
                "GET",
                headers=transport.IDENTITY_ENCODING,
                stream=True,
                accounted=True,
            )
            try:
                if response.status_code not in (transport.HTTP_OK, transport.HTTP_NO_CONTENT):
                    await _raise_byte_error(response, source)
                mutation_started = True
                await write_whole(
                    filesystem,
                    destination,
                    chunks(),
                    size=size,
                    content_type=None,
                    expected_digest=digest.digest,
                    accounted=True,
                    endpoint=push,
                )
            finally:
                await response.aclose()
        if source_md5 is not None and digest.hexdigest() != source_md5.lower():
            msg = f"MD5 mismatch after copying {source} to {destination}"
            raise errors.VOSpaceError(msg)  # noqa: TRY301 - the shared cleanup owns both integrity and PUT failure.
    except BaseException:
        if mutation_started and not existed:
            with contextlib.suppress(OSError):
                await filesystem._delete_node(destination)
        raise
