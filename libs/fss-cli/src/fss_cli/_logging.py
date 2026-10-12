"""Standard-library transfer records for embedding hosts.

Commands log under the ``fss_cli`` logger hierarchy so a host can route
them through its own handlers; the library itself installs only a
``NullHandler`` and never prints a record. One INFO record describes each file
operation; DEBUG records describe content-identity decisions and staging.

Records never carry credentials: every logged path or operand passes through
:func:`_redact`, which drops URL user information, query strings, and
fragments, and escapes control characters. Storage options are never logged.
"""

from __future__ import annotations

import re
import time
from typing import TYPE_CHECKING

from ._diagnostics import _render_diagnostic_value

if TYPE_CHECKING:
    import logging

_USERINFO = re.compile(r"(?<=//)[^/?#@]*@")


def _redact(value: str) -> str:
    """Return ``value`` safe to log: no userinfo, query, fragment, or controls."""
    value = value.split("?", 1)[0].split("#", 1)[0]
    return _render_diagnostic_value(_USERINFO.sub("", value))


def _location(name: str, path: str) -> str:
    """Render one mapped location the way a user spells it, redacted."""
    return _redact(f"{name}:{path}")


def _log_file_operation(  # noqa: PLR0913 - one structured record per file.
    logger: logging.Logger,
    outcome: str,
    source: str,
    destination: str,
    size: int | None,
    started: float,
) -> None:
    """Emit the INFO record for one completed file operation.

    Args:
        logger: The emitting module's logger.
        outcome: ``copied``, ``staged``, ``skipped``, ``moved``, or
            ``verified``.
        source: The redacted source location.
        destination: The redacted destination location.
        size: Bytes the operation concerned, when known.
        started: The :func:`time.monotonic` value when the operation began.
    """
    duration = time.monotonic() - started
    logger.info(
        "%s %s -> %s (%s bytes, %.3fs)",
        outcome,
        source,
        destination,
        "?" if size is None else size,
        duration,
        extra={
            "outcome": outcome,
            "source": source,
            "destination": destination,
            "bytes": size,
            "duration": duration,
        },
    )
