"""Live read negotiation against arc and vault.

Not collected by the default suite. Run with credentials:

    uv run --package vosfs pytest libs/vosfs/tests/integration
"""

from __future__ import annotations

import os
from pathlib import Path

import pytest

from vosfs import VOSpaceFileSystem

pytestmark = pytest.mark.integration

_ENDPOINTS = ("https://ws-cadc.canfar.net/arc", "https://ws-cadc.canfar.net/vault")


def _credential() -> dict[str, str]:
    certfile = os.environ.get("VOSFS_CERT_FILE", str(Path.home() / ".ssl" / "cadcproxy.pem"))
    if not Path(certfile).is_file():
        pytest.skip("no CADC certificate for a live VOSpace read")
    return {"certfile": certfile}


@pytest.mark.parametrize("endpoint_url", _ENDPOINTS)
async def test_pull_uses_the_redirect_form(endpoint_url: str) -> None:
    """A live pull that the service accepts must not fall back to the XML form."""
    filesystem = VOSpaceFileSystem(endpoint_url, asynchronous=True, skip_instance_cache=True, **_credential())
    try:
        entries = await filesystem._ls("/", detail=True)
        files = [entry for entry in entries if entry.get("type") == "file"]
        if not files:
            pytest.skip("no file at the service root")
        await filesystem._cat_file(str(files[0]["name"]), start=0, end=1)
    finally:
        await filesystem.aclose()
    assert filesystem._redirect_reads_unsupported is False
