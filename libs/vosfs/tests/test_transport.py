"""Tests for the HTTPX client pool, lifecycle, and serialization (section 12)."""

import asyncio
import pickle

import httpcore
import httpx
import pytest
import respx
from conftest import BASE_URL, make_fs

from vosfs import VOSpaceFileSystem
from vosfs.transport import DEFAULT_TIMEOUTS, ClientPool, ConnectionBudget, build_timeout


def _pool(**kwargs: object) -> ClientPool:
    defaults = {
        "certfile": None,
        "trust_env": True,
        "timeout": build_timeout(None),
        "injected_transport": None,
    }
    defaults.update(kwargs)
    return ClientPool(**defaults)  # type: ignore[arg-type]


async def test_cancelled_budget_head_wakes_a_smaller_waiter() -> None:
    budget = ConnectionBudget(2)
    await budget.acquire()
    copy = asyncio.create_task(budget.acquire(2))
    await asyncio.sleep(0)
    read = asyncio.create_task(budget.acquire())
    await asyncio.sleep(0)
    copy.cancel()
    with pytest.raises(asyncio.CancelledError):
        await copy
    await asyncio.wait_for(read, timeout=1)
    assert budget._available == 0
    budget.release(2)
    assert budget._available == 2


# --- timeouts -------------------------------------------------------------------


def test_default_timeout_values() -> None:
    timeout = build_timeout(None)
    assert timeout.connect == DEFAULT_TIMEOUTS["connect"]
    assert timeout.read == DEFAULT_TIMEOUTS["read"]
    assert timeout.write == DEFAULT_TIMEOUTS["write"]
    assert timeout.pool == DEFAULT_TIMEOUTS["pool"]


def test_timeout_overrides() -> None:
    timeout = build_timeout({"read": 5.0})
    assert timeout.read == 5.0
    assert timeout.connect == DEFAULT_TIMEOUTS["connect"]


# --- lazy, TLS-keyed, single-instance construction ------------------------------


async def test_client_is_built_once_per_key(router: respx.Router) -> None:
    transport = httpx.MockTransport(router.async_handler)
    pool = _pool(injected_transport=transport)
    first = await pool.client()
    second = await pool.client()
    assert first is second
    assert first.follow_redirects is False
    assert first.auth is None
    await pool.aclose()


async def test_plain_production_client_builds_without_network() -> None:
    pool = _pool()  # no injected transport, no certfile
    client = await pool.client()
    assert isinstance(client, httpx.AsyncClient)
    await pool.aclose()


def test_filesystem_requests_use_the_environment_proxy(monkeypatch) -> None:
    used = []

    async def proxy_request(_self, request):
        used.append(request.url.host)
        message = "offline proxy"
        raise httpcore.ProxyError(message)

    async def direct_request(_self, _request):
        message = "request bypassed configured proxy"
        raise AssertionError(message)

    for name in (
        "HTTP_PROXY",
        "HTTPS_PROXY",
        "ALL_PROXY",
        "NO_PROXY",
        "http_proxy",
        "https_proxy",
        "all_proxy",
        "no_proxy",
    ):
        monkeypatch.delenv(name, raising=False)
    monkeypatch.setenv("HTTPS_PROXY", "http://proxy.invalid:8080")
    monkeypatch.setattr(httpcore.AsyncHTTPProxy, "handle_async_request", proxy_request)
    monkeypatch.setattr(httpcore.AsyncConnectionPool, "handle_async_request", direct_request)
    fs = VOSpaceFileSystem(BASE_URL, trust_env=True, skip_instance_cache=True)
    try:
        with pytest.raises(ConnectionError):
            fs.info("/f")
        assert used == [b"staging.canfar.net"]
    finally:
        fs.close()


async def test_cert_client_requested_without_certfile_raises() -> None:
    transport = httpx.MockTransport(respx.Router().async_handler)
    pool = _pool(injected_transport=transport)
    with pytest.raises(ValueError, match="no certificate"):
        await pool.client(use_cert=True)
    await pool.aclose()


# --- no cookie jar --------------------------------------------------------------


async def test_no_cookie_is_resent_after_set_cookie(router: respx.Router) -> None:
    seen: dict[str, str | None] = {}
    router.get("/a").mock(return_value=httpx.Response(200, headers={"Set-Cookie": "sid=abc; Path=/"}))

    def capture(request: httpx.Request) -> httpx.Response:
        seen["cookie"] = request.headers.get("cookie")
        return httpx.Response(200)

    router.get("/b").side_effect = capture
    transport = httpx.MockTransport(router.async_handler)
    pool = _pool(injected_transport=transport)
    await pool.send(httpx.Request("GET", f"{BASE_URL}/a"))
    await pool.send(httpx.Request("GET", f"{BASE_URL}/b"))
    assert seen["cookie"] is None
    await pool.aclose()


# --- close semantics ------------------------------------------------------------


async def test_aclose_is_idempotent_and_blocks_later_io(router: respx.Router) -> None:
    transport = httpx.MockTransport(router.async_handler)
    pool = _pool(injected_transport=transport)
    await pool.client()
    await pool.aclose()
    await pool.aclose()  # idempotent
    assert pool.closed is True
    with pytest.raises(ValueError, match="closed"):
        await pool.client()
    with pytest.raises(ValueError, match="closed"):
        await pool.send(httpx.Request("GET", f"{BASE_URL}/x"))


async def test_filesystem_aclose_evicts_instance_cache(router: respx.Router) -> None:
    transport = httpx.MockTransport(router.async_handler)
    fs = VOSpaceFileSystem(BASE_URL, transport=transport, asynchronous=True)
    assert fs._fs_token in type(fs)._cache
    await fs.aclose()
    assert fs._fs_token not in type(fs)._cache


def test_sync_close_bridges_through_loop(router: respx.Router) -> None:
    fs = make_fs(router)  # synchronous instance
    fs.close()
    assert fs._pool.closed is True


async def test_async_close_rejects_sync_close(router: respx.Router) -> None:
    fs = make_fs(router, asynchronous=True)
    with pytest.raises(RuntimeError, match="aclose"):
        fs.close()
    await fs.aclose()


# --- serialization --------------------------------------------------------------


def test_pickle_round_trip_before_and_after_client_creation(
    router: respx.Router,
) -> None:
    fs = make_fs(router, token="lit")
    restored = pickle.loads(pickle.dumps(fs))  # noqa: S301 - trusted local round-trip
    assert restored.endpoint_url == fs.endpoint_url
    assert restored._credential.token_literal == "lit"
    # Live pool is never serialized; reconstruction builds a fresh one.
    assert restored._pool is not fs._pool


async def test_pickle_round_trip_after_client_realized(router: respx.Router) -> None:
    fs = make_fs(router, asynchronous=True)
    await fs._pool.client()  # realize a live client
    restored = pickle.loads(pickle.dumps(fs))  # noqa: S301
    assert restored.endpoint_url == fs.endpoint_url
    assert not restored._pool.closed
    await fs.aclose()


def test_json_round_trip(router: respx.Router) -> None:
    fs = make_fs(router, token="lit")
    blob = fs.to_json()
    assert "transport" not in blob
    restored = VOSpaceFileSystem.from_json(blob)
    assert isinstance(restored, VOSpaceFileSystem)
    assert restored.endpoint_url == fs.endpoint_url


def test_redacted_json_omits_literal_token_and_pickle_keeps_it(router: respx.Router) -> None:
    fs = make_fs(router, token="SECRET-TOKEN")
    redacted = fs.to_json(include_password=False)
    assert "SECRET-TOKEN" not in redacted
    assert "token" not in fs.to_dict(include_password=False)
    assert fs.to_dict()["token"] == "SECRET-TOKEN"
    restored = pickle.loads(pickle.dumps(fs))  # noqa: S301 - trusted local round-trip
    assert restored._credential.token_literal == "SECRET-TOKEN"


# --- review-fix regression: close/build must be serialized ----------------------


async def test_client_after_close_raises(router: respx.Router) -> None:
    pool = _pool(injected_transport=httpx.MockTransport(router.async_handler))
    await pool.aclose()
    with pytest.raises(ValueError, match="closed"):
        await pool.client()


async def test_build_and_close_race_leaves_no_open_client(router: respx.Router) -> None:
    import asyncio

    pool = _pool(injected_transport=httpx.MockTransport(router.async_handler))
    lock = pool._lock
    await lock.acquire()
    close = asyncio.create_task(pool.aclose())
    await asyncio.sleep(0)
    build = asyncio.create_task(pool.client())
    await asyncio.sleep(0)
    lock.release()

    result = await asyncio.gather(close, build, return_exceptions=True)

    assert isinstance(result[1], ValueError)
    assert pool.closed is True
    assert pool._clients == {}


def test_max_connections_is_serialized_and_rejected_when_invalid(router: respx.Router) -> None:
    fs = make_fs(router, max_connections=4)
    assert fs.to_dict()["max_connections"] == 4
    with pytest.raises(ValueError, match="max_connections"):
        make_fs(router, max_connections=0)


async def test_streamed_response_holds_its_connection_until_close() -> None:
    import asyncio

    started = asyncio.Event()
    release = asyncio.Event()

    async def handler(request: httpx.Request) -> httpx.Response:
        del request
        started.set()
        await release.wait()
        return httpx.Response(200, content=b"ok")

    pool = _pool(injected_transport=httpx.MockTransport(handler), max_connections=1)
    first = asyncio.create_task(pool.send(httpx.Request("GET", "https://example.test/a"), stream=True))
    await asyncio.wait_for(started.wait(), timeout=1)
    second_done = False

    async def second() -> None:
        nonlocal second_done
        await pool.send(httpx.Request("GET", "https://example.test/b"))
        second_done = True

    follower = asyncio.create_task(second())
    await asyncio.sleep(0)
    assert second_done is False
    release.set()
    response = await first
    await asyncio.sleep(0)
    assert second_done is False
    await response.aclose()
    await follower
    assert second_done is True
    await pool.aclose()
