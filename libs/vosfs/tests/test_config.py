"""Tests for credential resolution and constructor validation (sections 3, 3.1)."""

from pathlib import Path

import pytest
import respx
from conftest import BASE_URL, make_fs

from vosfs import config
from vosfs.config import Credential, resolve_credential, validate_endpoint

# --- credential precedence and mutual exclusion ---------------------------------


def test_explicit_token_wins_and_ignores_environment() -> None:
    cred = resolve_credential(
        token="literal",
        tokenfile=None,
        certfile=None,
        environ={config.ENV_CERT_FILE: "/x.pem", config.ENV_TOKEN: "envtok"},
    )
    assert cred == Credential(method="token", token_literal="literal")


def test_explicit_tokenfile_and_certfile() -> None:
    assert resolve_credential(token=None, tokenfile="/t", certfile=None, environ={}) == Credential(
        method="token", token_file="/t"
    )
    assert resolve_credential(token=None, tokenfile=None, certfile="/c.pem", environ={}) == Credential(
        method="certificate", certfile="/c.pem"
    )


def test_multiple_explicit_credentials_rejected() -> None:
    with pytest.raises(ValueError, match="mutually exclusive"):
        resolve_credential(token="a", tokenfile=None, certfile="/c.pem", environ={})


def test_environment_fallbacks() -> None:
    assert resolve_credential(token=None, tokenfile=None, certfile=None, environ={config.ENV_TOKEN: "e"}) == Credential(
        method="token", token_env=config.ENV_TOKEN
    )
    assert resolve_credential(
        token=None,
        tokenfile=None,
        certfile=None,
        environ={config.ENV_TOKEN_FILE: "/tf"},
    ) == Credential(method="token", token_file="/tf")
    assert resolve_credential(
        token=None,
        tokenfile=None,
        certfile=None,
        environ={config.ENV_CERT_FILE: "/cf.pem"},
    ) == Credential(method="certificate", certfile="/cf.pem")


def test_multiple_environment_sources_rejected() -> None:
    with pytest.raises(ValueError, match="at most one"):
        resolve_credential(
            token=None,
            tokenfile=None,
            certfile=None,
            environ={config.ENV_TOKEN: "e", config.ENV_CERT_FILE: "/c.pem"},
        )


def test_no_credential_is_anonymous() -> None:
    cred = resolve_credential(token=None, tokenfile=None, certfile=None, environ={})
    assert cred.method == "anonymous"


# --- rereading token material ---------------------------------------------------


def test_read_literal_bearer_strips() -> None:
    assert Credential(method="token", token_literal="abc\n").read_bearer() == "abc"


def test_read_env_bearer_is_reread(monkeypatch: pytest.MonkeyPatch) -> None:
    cred = Credential(method="token", token_env=config.ENV_TOKEN)
    monkeypatch.setenv(config.ENV_TOKEN, "first")
    assert cred.read_bearer() == "first"
    monkeypatch.setenv(config.ENV_TOKEN, "second")
    assert cred.read_bearer() == "second"
    monkeypatch.delenv(config.ENV_TOKEN)
    with pytest.raises(PermissionError):
        cred.read_bearer()


def test_read_file_bearer_is_reread(tmp_path: Path) -> None:
    token_path = tmp_path / "tok"
    token_path.write_text("t1\n")
    cred = Credential(method="token", token_file=str(token_path))
    assert cred.read_bearer() == "t1"
    token_path.write_text("t2")
    assert cred.read_bearer() == "t2"


def test_read_missing_file_bearer_raises(tmp_path: Path) -> None:
    cred = Credential(method="token", token_file=str(tmp_path / "absent"))
    with pytest.raises(PermissionError):
        cred.read_bearer()


def test_read_bearer_on_non_token_raises() -> None:
    with pytest.raises(PermissionError):
        Credential(method="anonymous").read_bearer()


def test_read_bearer_on_empty_token_credential_raises() -> None:
    with pytest.raises(PermissionError):
        Credential(method="token").read_bearer()


# --- endpoint validation --------------------------------------------------------


def test_endpoint_trailing_slash_removed() -> None:
    assert validate_endpoint("https://h.test/arc/", has_credential=True) == "https://h.test/arc"


def test_endpoint_http_allowed_when_anonymous() -> None:
    assert validate_endpoint("http://h.test/arc", has_credential=False) == "http://h.test/arc"


@pytest.mark.parametrize(
    ("url", "has_credential"),
    [
        ("http://h.test/arc", True),
        ("https://u:p@h.test/arc", False),
        ("https://h.test/arc?x=1", False),
        ("https://h.test/arc#f", False),
        ("ftp://h.test/arc", False),
        ("/relative/arc", False),
        ("", False),
    ],
)
def test_endpoint_rejections(url: str, has_credential: bool) -> None:
    with pytest.raises(ValueError):  # noqa: PT011
        validate_endpoint(url, has_credential=has_credential)


# --- timeouts -------------------------------------------------------------------


def test_timeouts_none() -> None:
    assert config.resolve_timeouts(None) is None


def test_timeouts_valid() -> None:
    assert config.resolve_timeouts({"connect": 5, "read": 2.5}) == {
        "connect": 5.0,
        "read": 2.5,
    }


@pytest.mark.parametrize(
    "bad",
    [
        {"nope": 1},
        {"connect": 0},
        {"connect": -1},
        {"connect": float("inf")},
        {"connect": float("nan")},
        {"connect": True},
    ],
)
def test_timeouts_rejections(bad: dict) -> None:
    with pytest.raises(ValueError):  # noqa: PT011
        config.resolve_timeouts(bad)


# --- forbidden options ----------------------------------------------------------


def test_reject_forbidden_options() -> None:
    with pytest.raises(ValueError, match="unsupported option"):
        config.reject_forbidden_options({"client_kwargs": {}})


def test_reject_forbidden_options_clean() -> None:
    config.reject_forbidden_options({"use_listings_cache": True})


# --- constructor integration ----------------------------------------------------


def test_constructor_rejects_http_with_credential() -> None:
    router = respx.Router(base_url=BASE_URL)
    with pytest.raises(ValueError):  # noqa: PT011
        make_fs(router, endpoint_override="http://insecure.test/arc", token="t")


def test_constructor_rejects_forbidden_option() -> None:
    router = respx.Router(base_url=BASE_URL)
    with pytest.raises(ValueError):  # noqa: PT011
        make_fs(router, client_kwargs={})


def test_constructor_resolves_environment_and_excludes_from_serialization(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv(config.ENV_TOKEN, "env-token")
    router = respx.Router(base_url=BASE_URL)
    fs = make_fs(router)
    assert fs._credential == Credential(method="token", token_env=config.ENV_TOKEN)
    # Environment credentials are not serialized; reconstruction re-resolves them.
    assert "token" not in fs.storage_options
