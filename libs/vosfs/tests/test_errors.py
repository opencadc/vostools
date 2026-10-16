"""Tests for the VOSpace error taxonomy (contract section 13)."""

from __future__ import annotations

import errno

import httpx
import pytest

from vosfs import errors

# --------------------------------------------------------------------------- #
# bounded_text
# --------------------------------------------------------------------------- #


def test_max_error_body_is_8_kib() -> None:
    assert errors.MAX_ERROR_BODY == 8192


def test_bounded_text_truncates_to_the_limit() -> None:
    assert len(errors.bounded_text(b"A" * 10000)) == errors.MAX_ERROR_BODY


def test_bounded_text_honors_a_custom_limit() -> None:
    assert errors.bounded_text(b"abcdef", limit=3) == "abc"


def test_bounded_text_strips_surrounding_whitespace() -> None:
    assert errors.bounded_text(b"  hello world  ") == "hello world"


def test_bounded_text_replaces_undecodable_bytes() -> None:
    text = errors.bounded_text(b"ok\xff\xfe")
    assert text.startswith("ok")
    assert "�" in text


# --------------------------------------------------------------------------- #
# redact
# --------------------------------------------------------------------------- #


def test_redact_leaves_ordinary_text_unchanged() -> None:
    ordinary = "nothing secret here /home/user/data.fits"
    assert errors.redact(ordinary) == ordinary


def test_redact_hides_authorization_header() -> None:
    result = errors.redact("Authorization: Bearer abc.def-123")
    assert "abc.def-123" not in result
    assert "<redacted>" in result


def test_redact_hides_bare_bearer_token() -> None:
    result = errors.redact("token is Bearer secrettoken123 ok")
    assert "secrettoken123" not in result
    assert "Bearer <redacted>" in result


def test_redact_hides_cookie_value() -> None:
    result = errors.redact("Cookie: session=xyz; theme=dark")
    assert "xyz" not in result
    assert "dark" not in result
    assert "<redacted>" in result


def test_redact_hides_set_cookie_value() -> None:
    result = errors.redact("Set-Cookie: token=abc; Path=/")
    assert "abc" not in result
    assert "<redacted>" in result


@pytest.mark.parametrize(
    "name",
    [
        "token",
        "signature",
        "sig",
        "access_token",
        "X-Amz-Signature",
        "X-Amz-Credential",
    ],
)
def test_redact_hides_url_token_parameters(name: str) -> None:
    result = errors.redact(f"https://host/path?{name}=TOPSECRET&keep=1")
    assert "TOPSECRET" not in result
    assert "keep=1" in result
    assert "<redacted>" in result


def test_redact_hides_opaque_preauth_path_segment() -> None:
    secret = "ABCDEFGHIJKLMNOPQRSTUVWXYZ012345"
    result = errors.redact(f"https://ws/vault/preauth/{secret}/file")
    assert secret not in result
    assert "/preauth/<redacted>/file" in result


# --------------------------------------------------------------------------- #
# VOSpaceError
# --------------------------------------------------------------------------- #


def test_vospace_error_is_an_os_error() -> None:
    assert issubclass(errors.VOSpaceError, OSError)


def test_vospace_error_defaults_partial_lists_to_empty() -> None:
    exc = errors.VOSpaceError("boom")
    assert exc.status is None
    assert exc.fault is None
    assert exc.retry_after is None
    assert exc.completed == []
    assert exc.failed == []


def test_vospace_error_retains_context() -> None:
    exc = errors.VOSpaceError(
        "partial move failed",
        status=500,
        fault="InternalFault",
        retry_after=3.0,
        completed=["/a"],
        failed=["/b"],
    )
    assert exc.status == 500
    assert exc.fault == "InternalFault"
    assert exc.retry_after == 3.0
    assert exc.completed == ["/a"]
    assert exc.failed == ["/b"]


def test_vospace_error_string_includes_status_and_fault() -> None:
    exc = errors.VOSpaceError("boom", status=500, fault="InternalFault")
    text = str(exc)
    assert "(HTTP 500)" in text
    assert "[InternalFault]" in text
    assert exc.args[0] == text


def test_vospace_error_string_omits_absent_status_and_fault() -> None:
    exc = errors.VOSpaceError("boom")
    assert str(exc) == "boom"


def test_vospace_error_message_is_redacted() -> None:
    exc = errors.VOSpaceError("Authorization: Bearer LEAKED", status=502)
    assert "LEAKED" not in str(exc)
    assert "<redacted>" in str(exc)


def test_vospace_error_repr_is_redacted() -> None:
    exc = errors.VOSpaceError("Cookie: session=LEAKED", status=502, fault="Boom")
    text = repr(exc)
    assert "LEAKED" not in text
    assert "VOSpaceError(" in text
    assert "status=502" in text
    assert "fault='Boom'" in text


# --------------------------------------------------------------------------- #
# http_exception
# --------------------------------------------------------------------------- #


@pytest.mark.parametrize(
    ("status", "expected"),
    [
        (401, PermissionError),
        (403, PermissionError),
        (404, FileNotFoundError),
        (409, FileExistsError),
        (423, BlockingIOError),
    ],
)
def test_http_exception_maps_standard_statuses(
    status: int,
    expected: type[Exception],
) -> None:
    exc = errors.http_exception(status)
    assert isinstance(exc, expected)
    assert not isinstance(exc, errors.VOSpaceError)
    assert f"(HTTP {status})" in str(exc)


def test_http_exception_maps_413_to_enospc() -> None:
    exc = errors.http_exception(413)
    assert type(exc) is OSError
    assert exc.errno == errno.ENOSPC


def test_http_exception_maps_quota_body_to_enospc() -> None:
    exc = errors.http_exception(500, body="quota exceeded for this user")
    assert type(exc) is OSError
    assert exc.errno == errno.ENOSPC


@pytest.mark.parametrize("status", [400, 412, 500, 503])
def test_http_exception_falls_back_to_vospace_error(status: int) -> None:
    exc = errors.http_exception(status, retry_after=1.5)
    assert isinstance(exc, errors.VOSpaceError)
    assert exc.status == status
    assert exc.retry_after == 1.5


def test_http_exception_non_quota_fault_is_vospace_error() -> None:
    exc = errors.http_exception(500, fault="InternalFault")
    assert isinstance(exc, errors.VOSpaceError)
    assert exc.fault == "InternalFault"


def test_http_exception_includes_path_and_redacted_body() -> None:
    exc = errors.http_exception(
        404,
        body="Authorization: Bearer LEAKED",
        path="/a/b",
    )
    text = str(exc)
    assert "/a/b" in text
    assert "LEAKED" not in text
    assert "<redacted>" in text


def test_http_exception_without_path_or_body() -> None:
    exc = errors.http_exception(404)
    text = str(exc)
    assert " for " not in text
    assert ":" not in text.replace("(HTTP 404)", "")


# --------------------------------------------------------------------------- #
# transport_exception
# --------------------------------------------------------------------------- #


def test_transport_exception_maps_read_timeout() -> None:
    exc = errors.transport_exception(httpx.ReadTimeout("slow"), path="/a")
    assert isinstance(exc, TimeoutError)
    assert "/a" in str(exc)


def test_transport_exception_maps_connect_error() -> None:
    exc = errors.transport_exception(httpx.ConnectError("refused"))
    assert isinstance(exc, ConnectionError)


def test_transport_exception_maps_connect_timeout_to_connection_error() -> None:
    exc = errors.transport_exception(httpx.ConnectTimeout("ct"))
    assert isinstance(exc, ConnectionError)
    assert not isinstance(exc, TimeoutError)


def test_transport_exception_maps_other_transport_error() -> None:
    exc = errors.transport_exception(httpx.ReadError("read"))
    assert isinstance(exc, ConnectionError)


def test_transport_exception_falls_back_to_vospace_error() -> None:
    exc = errors.transport_exception(RuntimeError("Bearer SECRETTOKEN"))
    assert isinstance(exc, errors.VOSpaceError)
    assert "SECRETTOKEN" not in str(exc)


def test_redact_masks_aws_security_token() -> None:
    # Regression: a pre-authorized S3-style URL carries a temporary STS session
    # token in X-Amz-Security-Token, which must be redacted like other secrets.
    url = "https://host/files?X-Amz-Credential=AKIA&X-Amz-Security-Token=FwoGSESSIONSECRET123&X-Amz-Signature=deadbeef"
    redacted = errors.redact(url)
    assert "FwoGSESSIONSECRET123" not in redacted
    assert "AKIA" not in redacted
    assert "deadbeef" not in redacted


# --- review-fix regressions: preauth redaction, Retry-After, symbolic fault ----


def test_redact_masks_preauth_colon_path_token() -> None:
    url = "https://ws/minoc/files/preauth:AbC123secretToken456/cadc:TEST/f.fits"
    out = errors.redact(url)
    assert "AbC123secretToken456" not in out
    assert "preauth:<redacted>" in out


def test_parse_retry_after_reads_delta_seconds() -> None:
    assert errors.parse_retry_after("12") == 12.0
    assert errors.parse_retry_after("  30 ") == 30.0


def test_parse_retry_after_ignores_dates_negatives_and_none() -> None:
    assert errors.parse_retry_after("Wed, 21 Oct 2099 07:28:00 GMT") is None
    assert errors.parse_retry_after("-5") is None
    assert errors.parse_retry_after(None) is None


def test_extract_fault_matches_known_tokens_only() -> None:
    assert errors.extract_fault("the QuotaExceeded ceiling was hit") == "QuotaExceeded"
    assert errors.extract_fault("NodeLocked by another writer") == "NodeLocked"
    assert errors.extract_fault("a LinkFound during traversal") == "LinkFound"
    assert errors.extract_fault("TypeNotSupported for this node") == "TypeNotSupported"
    assert errors.extract_fault("an ordinary message") is None


def test_http_exception_quota_fault_maps_to_enospc() -> None:
    exc = errors.http_exception(400, body="QuotaExceeded", fault="QuotaExceeded")
    assert isinstance(exc, OSError)
    assert exc.errno == errno.ENOSPC


def test_http_exception_carries_retry_after_and_fault() -> None:
    exc = errors.http_exception(503, fault="ServiceBusy", retry_after=30.0)
    assert isinstance(exc, errors.VOSpaceError)
    assert exc.retry_after == 30.0
    assert exc.fault == "ServiceBusy"
