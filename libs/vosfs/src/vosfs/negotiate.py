"""Synchronous byte-transfer negotiation helpers (contract section 7).

These pure helpers support the filesystem's negotiation flow: build an
authority-qualified transfer target, parse the transfer-details document into
its negotiated protocols, validate a redirect target, and choose the protocol
whose security method is compatible with the configured credential. Credential
routing follows the negotiated security method, not origin alone.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import TYPE_CHECKING
from urllib.parse import urljoin, urlsplit

from vosfs.capabilities import ANONYMOUS_METHOD
from vosfs.errors import VOSpaceError
from vosfs.xmlio import local_name, safe_parse

if TYPE_CHECKING:
    import xml.etree.ElementTree as ET

DIRECTION_PULL = "pullFromVoSpace"
DIRECTION_PUSH = "pushToVoSpace"

PROTOCOL_HTTPS_GET = "ivo://ivoa.net/vospace/core#httpsget"
PROTOCOL_HTTPS_PUT = "ivo://ivoa.net/vospace/core#httpsput"


@dataclass(frozen=True)
class NegotiatedEndpoint:
    """A negotiated byte endpoint and the security method that routes credentials."""

    url: str
    security_method: str


def parse_transfer_details(data: bytes) -> list[NegotiatedEndpoint]:
    """Parse the negotiated endpoints from a transfer-details document.

    A protocol without a ``securityMethod`` is treated as a pre-authorized,
    anonymous endpoint.

    Raises:
        ValueError: If the document is malformed.
        VOSpaceError: If it advertises no usable protocol endpoint.
    """
    root = safe_parse(data)
    endpoints = [
        NegotiatedEndpoint(url=endpoint, security_method=_security_method_of(element))
        for element in root.iter()
        if local_name(element.tag) == "protocol" and (endpoint := _endpoint_of(element)) is not None
    ]
    if not endpoints:
        msg = "the transfer negotiation returned no usable protocol endpoint"
        raise VOSpaceError(msg)
    return endpoints


def choose_protocol(endpoints: list[NegotiatedEndpoint], credential_method: str) -> NegotiatedEndpoint:
    """Return the first endpoint compatible with the configured credential.

    A pre-authorized (anonymous) endpoint is always usable; otherwise the
    endpoint's security method must equal the credential's method.

    Raises:
        VOSpaceError: If no returned endpoint matches the credential source.
    """
    for endpoint in endpoints:
        if endpoint.security_method in (ANONYMOUS_METHOD, credential_method):
            return endpoint
    msg = "no negotiated endpoint matches the configured credential source"
    raise VOSpaceError(msg)


def validate_redirect(location: str | None, *, base: str, sending_bearer: bool) -> str:
    """Resolve and validate one redirect target from the transfer 303 chain.

    Args:
        location: The raw ``Location`` header value, possibly relative.
        base: The URL the redirect was returned from, for resolving a relative
            target.
        sending_bearer: Whether the next request will carry a bearer header, in
            which case the target must be https.

    Returns:
        The absolute, validated redirect URL.

    Raises:
        VOSpaceError: If the target is missing, relative-unresolvable, not an
            absolute http(s) URL, carries userinfo, or is not https while a
            bearer is sent.
    """
    if not location:
        msg = "the synchronous-transfer redirect is missing a Location"
        raise VOSpaceError(msg)
    resolved = urljoin(base, location)
    parts = urlsplit(resolved)
    if parts.scheme not in ("http", "https") or not parts.netloc:
        msg = f"redirect target is not an absolute http(s) URL: {resolved!r}"
        raise VOSpaceError(msg)
    if parts.username or parts.password:
        msg = "redirect target must not contain userinfo"
        raise VOSpaceError(msg)
    if sending_bearer and parts.scheme != "https":
        msg = "a bearer redirect target must use https"
        raise VOSpaceError(msg)
    return resolved


def is_direct_byte_endpoint(url: str) -> bool:
    """Return whether ``url`` is an OpenCADC ``/files`` byte location."""
    segments = [segment for segment in urlsplit(url).path.split("/") if segment]
    return "files" in segments[:-1] or (bool(segments) and segments[-1] == "files")


def _endpoint_of(protocol: ET.Element) -> str | None:
    for child in protocol:
        if local_name(child.tag) == "endpoint" and child.text:
            return child.text.strip()
    return None


def _security_method_of(protocol: ET.Element) -> str:
    for child in protocol:
        if local_name(child.tag) == "securityMethod":
            return child.get("standardID", ANONYMOUS_METHOD)
    return ANONYMOUS_METHOD
