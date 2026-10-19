"""Transfer logging: one INFO record per file operation, secrets never logged."""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING
from xml.sax.saxutils import escape

import httpx
from conftest import (
    BASE_URL,
    SYNC_URL,
    make_fs,
    make_sim_fs,
    mock_capabilities,
    stream_body,
    transfer_details,
)
from conftest import data_node_response as _data_node_response
from vospace_sim import VOSpaceSim

if TYPE_CHECKING:
    from pathlib import Path

    import pytest
    import respx

_SECRET = "SECRETTOKEN0123456789abcdef"


def _info_records(caplog: pytest.LogCaptureFixture) -> list[logging.LogRecord]:
    return [record for record in caplog.records if record.name.startswith("vosfs") and record.levelno == logging.INFO]


async def test_get_put_and_copy_emit_one_info_record_per_file(
    router: respx.Router,
    tmp_path: Path,
    caplog: pytest.LogCaptureFixture,
) -> None:
    sim = VOSpaceSim().add_container("/d").add_file("/d/a", b"abc")
    fs = make_sim_fs(router, sim)
    local = tmp_path / "a"
    caplog.set_level(logging.INFO, logger="vosfs")

    await fs._get_file("/d/a", str(local))
    await fs._put_file(str(local), "/d/b")
    await fs._cp_file("/d/a", "/d/c")

    records = _info_records(caplog)
    assert [record.vosfs_outcome for record in records] == [
        "downloaded",
        "uploaded",
        "copied",
    ]
    assert [record.vosfs_bytes for record in records] == [3, 3, 3]
    assert records[0].vosfs_source == "/d/a"
    assert records[0].vosfs_destination == str(local)
    assert "/d/a -> /d/c (3 bytes" in records[2].getMessage()
    await fs.aclose()


async def test_recursive_get_logs_each_file(
    router: respx.Router,
    tmp_path: Path,
    caplog: pytest.LogCaptureFixture,
) -> None:
    sim = VOSpaceSim().add_container("/d")
    for name in ("x", "y", "z"):
        sim.add_file(f"/d/{name}", name.encode())
    fs = make_sim_fs(router, sim)
    caplog.set_level(logging.INFO, logger="vosfs")

    await fs._get("/d", str(tmp_path / "out"), recursive=True)

    sources = sorted(record.vosfs_source for record in _info_records(caplog))
    assert sources == ["/d/x", "/d/y", "/d/z"]
    await fs.aclose()


async def test_move_and_removal_are_logged(
    router: respx.Router,
    caplog: pytest.LogCaptureFixture,
) -> None:
    sim = VOSpaceSim().add_file("/src", b"m")
    fs = make_sim_fs(router, sim)
    caplog.set_level(logging.INFO, logger="vosfs")

    await fs._move("/src", "/dst")

    outcomes = [record.vosfs_outcome for record in _info_records(caplog)]
    assert outcomes == ["copied", "removed", "moved"]
    await fs.aclose()


def _preauth_router(router: respx.Router, files: dict[str, bytes]) -> None:
    """Negotiate a byte endpoint whose path and query both carry secrets."""
    mock_capabilities(router)
    router.get(url__regex=rf"^{BASE_URL}/nodes").mock(side_effect=lambda request: _data_node_response(request, files))
    router.post(SYNC_URL).mock(return_value=httpx.Response(303, headers={"Location": f"{BASE_URL}/details"}))
    endpoint = f"https://bytes.example/files/preauth:{_SECRET}/f?token={_SECRET}&x=1"
    router.get(f"{BASE_URL}/details").mock(return_value=httpx.Response(200, content=transfer_details(escape(endpoint))))
    router.get(host="bytes.example").mock(
        side_effect=lambda _request: httpx.Response(200, content=stream_body(files["/f"]))
    )


async def test_negotiation_debug_records_carry_no_secret_or_query(
    router: respx.Router,
    caplog: pytest.LogCaptureFixture,
) -> None:
    _preauth_router(router, {"/f": b"payload"})
    fs = make_fs(router, asynchronous=True)
    caplog.set_level(logging.DEBUG, logger="vosfs")

    assert await fs._cat_file("/f") == b"payload"

    messages = [record.getMessage() for record in caplog.records if record.name.startswith("vosfs")]
    negotiated = [message for message in messages if message.startswith("negotiated")]
    assert len(negotiated) == 1
    assert "https://bytes.example/files/preauth:<redacted>/f" in negotiated[0]
    assert all(_SECRET not in message for message in messages)
    assert all("?" not in message and "x=1" not in message for message in messages)
    await fs.aclose()


async def test_library_prints_nothing_without_a_configured_handler(
    router: respx.Router,
    tmp_path: Path,
    capsys: pytest.CaptureFixture[str],
) -> None:
    package_logger = logging.getLogger("vosfs")
    assert any(isinstance(h, logging.NullHandler) for h in package_logger.handlers)
    sim = VOSpaceSim().add_file("/a", b"abc")
    fs = make_sim_fs(router, sim)

    await fs._get_file("/a", str(tmp_path / "a"))

    captured = capsys.readouterr()
    assert captured.out == ""
    assert captured.err == ""
    await fs.aclose()
