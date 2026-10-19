"""Credential resolution and constructor validation for the vos filesystem.

This module implements the contract's construction rules (sections 3 and 3.1):
credential precedence and mutual exclusion, environment fallbacks, endpoint
validation, timeout validation, and refusal of unsupported transport options.
All checks are pure and run before any network I/O.
"""

from __future__ import annotations

import math
import os
from dataclasses import dataclass
from pathlib import Path
from typing import TYPE_CHECKING
from urllib.parse import urlsplit, urlunsplit

if TYPE_CHECKING:
    from collections.abc import Mapping

# Environment fallbacks, in the exact mapping fixed by the contract. These are
# variable names, not secrets.
ENV_TOKEN = "VOSFS_TOKEN"  # noqa: S105
ENV_TOKEN_FILE = "VOSFS_TOKEN_FILE"  # noqa: S105
ENV_CERT_FILE = "VOSFS_CERT_FILE"

# Storage options that would smuggle in an unsupported credential or client
# surface. The public API accepts none of them.
FORBIDDEN_OPTIONS = frozenset(
    {
        "auth",
        "client",
        "http_client",
        "ssl_context",
        "sslcontext",
        "cookies",
        "cookie_jar",
        "headers",
        "client_kwargs",
        "cert",
        "verify",
    },
)

_TIMEOUT_KEYS = ("connect", "read", "write", "pool")


@dataclass(frozen=True)
class Credential:
    """A resolved, single credential source.

    Exactly one of the token sources or ``certfile`` is set, unless the method
    is anonymous. Token material is reread on demand so rotated tokens are
    always current; the credential itself holds only how to reread it.
    """

    method: str  # "anonymous" | "token" | "certificate"
    token_literal: str | None = None
    token_env: str | None = None
    token_file: str | None = None
    certfile: str | None = None

    def read_bearer(self) -> str:
        """Return the current bearer token, rereading its source each call.

        Raises:
            PermissionError: If the configured token source is unavailable.
        """
        if self.method != "token":
            msg = "no bearer token is configured"
            raise PermissionError(msg)
        if self.token_literal is not None:
            return self.token_literal.strip()
        if self.token_env is not None:
            value = os.environ.get(self.token_env, "").strip()
            if not value:
                msg = f"environment variable {self.token_env} is not set"
                raise PermissionError(msg)
            return value
        if self.token_file is not None:
            try:
                return Path(self.token_file).read_text(encoding="utf-8").strip()
            except OSError as exc:
                msg = f"unable to read token file: {exc}"
                raise PermissionError(msg) from exc
        msg = "no bearer token is configured"
        raise PermissionError(msg)


def _at_most_one(pairs: tuple[tuple[str, str | None], ...], error: str) -> list[str]:
    """Return the names of configured sources, refusing more than one.

    Args:
        pairs: Candidate ``(name, value)`` sources; a truthy value counts.
        error: The message prefix for the mutual-exclusion failure.

    Raises:
        ValueError: If more than one source carries a truthy value.
    """
    named = [name for name, value in pairs if value]
    if len(named) > 1:
        msg = f"{error}; got {', '.join(named)}"
        raise ValueError(msg)
    return named


def resolve_credential(
    *,
    token: str | None,
    tokenfile: str | None,
    certfile: str | None,
    environ: Mapping[str, str],
) -> Credential:
    """Resolve the single active credential from options and the environment.

    Explicit options suppress every credential environment variable. Otherwise
    at most one environment source may be configured. After resolution the
    token, token file, and certificate sources are mutually exclusive.

    Raises:
        ValueError: If more than one credential source is configured.
    """
    explicit = _at_most_one(
        (("token", token), ("tokenfile", tokenfile), ("certfile", certfile)),
        "token, tokenfile, and certfile are mutually exclusive",
    )
    if explicit:
        if token:
            return Credential(method="token", token_literal=token)
        if tokenfile:
            return Credential(method="token", token_file=tokenfile)
        return Credential(method="certificate", certfile=certfile)

    return _resolve_from_environment(environ)


def _resolve_from_environment(environ: Mapping[str, str]) -> Credential:
    """Resolve a credential from at most one environment source."""
    env_token = environ.get(ENV_TOKEN, "").strip()
    env_token_file = environ.get(ENV_TOKEN_FILE, "").strip()
    env_cert_file = environ.get(ENV_CERT_FILE, "").strip()
    _at_most_one(
        (
            (ENV_TOKEN, env_token),
            (ENV_TOKEN_FILE, env_token_file),
            (ENV_CERT_FILE, env_cert_file),
        ),
        "at most one credential environment variable may be set",
    )
    if env_token:
        return Credential(method="token", token_env=ENV_TOKEN)
    if env_token_file:
        return Credential(method="token", token_file=env_token_file)
    if env_cert_file:
        return Credential(method="certificate", certfile=env_cert_file)
    return Credential(method="anonymous")


def validate_endpoint(endpoint_url: str, *, has_credential: bool) -> str:
    """Validate and normalize the service base URL.

    Args:
        endpoint_url: The caller-supplied absolute service base URL.
        has_credential: Whether any credential is configured.

    Returns:
        The URL with any trailing slash removed.

    Raises:
        ValueError: If the URL is not an absolute http(s) URL, carries
            userinfo, a query, or a fragment, or is not https while a
            credential is configured.
    """
    if not isinstance(endpoint_url, str) or not endpoint_url:
        msg = "endpoint_url is required and must be a non-empty string"
        raise ValueError(msg)
    parts = urlsplit(endpoint_url)
    if parts.scheme not in ("http", "https") or not parts.netloc:
        msg = "endpoint_url must be an absolute http(s) URL, for example https://staging.canfar.net/arc"
        raise ValueError(msg)
    if parts.username or parts.password:
        msg = "endpoint_url must not contain userinfo"
        raise ValueError(msg)
    if parts.query:
        msg = "endpoint_url must not contain a query component"
        raise ValueError(msg)
    if parts.fragment:
        msg = "endpoint_url must not contain a fragment component"
        raise ValueError(msg)
    if has_credential and parts.scheme != "https":
        msg = "endpoint_url must use https when a credential is configured"
        raise ValueError(msg)
    normalized_path = parts.path.rstrip("/")
    return urlunsplit((parts.scheme, parts.netloc, normalized_path, "", ""))


def resolve_timeouts(timeouts: Mapping[str, float] | None) -> dict[str, float] | None:
    """Validate optional connect/read/write/pool timeouts.

    Raises:
        ValueError: On an unknown key or a non-finite, non-positive value.
    """
    if timeouts is None:
        return None
    resolved: dict[str, float] = {}
    for key, value in timeouts.items():
        if key not in _TIMEOUT_KEYS:
            msg = f"unknown timeout {key!r}; expected one of {_TIMEOUT_KEYS}"
            raise ValueError(msg)
        if not isinstance(value, (int, float)) or isinstance(value, bool) or not math.isfinite(value) or value <= 0:
            msg = f"timeout {key!r} must be a finite positive number, got {value!r}"
            raise ValueError(msg)
        resolved[key] = float(value)
    return resolved


def reject_forbidden_options(options: Mapping[str, object]) -> None:
    """Refuse unsupported credential and client options.

    Raises:
        ValueError: If any forbidden option is present.
    """
    present = sorted(FORBIDDEN_OPTIONS.intersection(options))
    if present:
        names = ", ".join(present)
        msg = f"unsupported option(s): {names}; use only token, tokenfile, or certfile"
        raise ValueError(msg)
