"""Lifecycle and process-boundary reconstruction gates (contract section 12)."""

from __future__ import annotations

import asyncio
import multiprocessing
import os
import pickle
from concurrent.futures import ProcessPoolExecutor
from typing import TYPE_CHECKING, Any

import httpx
import pytest
from conftest import BASE_URL, CAPABILITIES, make_fs
from dask.base import tokenize

from vosfs import VOSpaceFileSystem, _transfer
from vosfs.capabilities import ANONYMOUS_METHOD
from vosfs.negotiate import NegotiatedEndpoint

if TYPE_CHECKING:
    import respx

    from vosfs.transport import ClientPool


class _CountingTransport(httpx.AsyncBaseTransport):
    """Record whether a direct send reached the injected transport."""

    def __init__(self, counter: Any) -> None:
        self._counter = counter

    async def handle_async_request(self, request: httpx.Request) -> httpx.Response:
        with self._counter.get_lock():
            self._counter.value += 1
        return httpx.Response(200, request=request)


async def _direct_send(filesystem: VOSpaceFileSystem, kind: str) -> None:
    if kind == "service":
        await filesystem._send_to_service("GET", f"{filesystem.endpoint_url}/capabilities")
        return
    await _transfer.byte_send(
        filesystem,
        NegotiatedEndpoint(f"{BASE_URL}/files/data", ANONYMOUS_METHOD),
        "GET",
    )


def _runtime_probe(filesystem: VOSpaceFileSystem) -> dict[str, Any]:
    """Describe reconstructed state without performing remote I/O."""
    return {
        "pid": filesystem._pid,
        "process": os.getpid(),
        "token": tokenize(filesystem),
        "has_loop": filesystem._loop is not None,
        "clients": filesystem._pool._clients,
        "client_lock": type(filesystem._pool._lock).__name__,
        "transport": filesystem._pool._injected,
        "bindings": filesystem._bindings,
        "bindings_lock": type(filesystem._bindings_lock).__name__,
        "authority": filesystem._authority,
        "cache": list(filesystem.dircache),
    }


def _fork_probe(filesystem: VOSpaceFileSystem, connection: Any) -> None:
    """Try to consume an inherited cached listing in a forked child."""
    try:
        asyncio.run(filesystem._ls("/"))
    except Exception as exc:  # noqa: BLE001 - send the observable child failure
        connection.send((type(exc).__name__, str(exc)))
    else:
        connection.send((None, None))
    finally:
        connection.close()


def _fork_send_probe(filesystem: VOSpaceFileSystem, kind: str, connection: Any) -> None:
    """Try to reach one inherited direct-send seam in a forked child."""
    try:
        asyncio.run(_direct_send(filesystem, kind))
    except Exception as exc:  # noqa: BLE001 - send the observable child failure
        connection.send((type(exc).__name__, str(exc)))
    else:
        connection.send((None, None))
    finally:
        connection.close()


async def test_concurrent_close_is_idempotent_and_blocks_cached_io(
    router: respx.Router,
) -> None:
    filesystem = make_fs(router, asynchronous=True)
    filesystem.dircache["/"] = [{"name": "/cached", "type": "file", "size": 1}]

    await asyncio.gather(filesystem.aclose(), filesystem.aclose())

    with pytest.raises(ValueError, match="closed"):
        await filesystem._ls("/")


async def test_pickle_and_json_reconstruct_only_constructor_state(
    router: respx.Router,
) -> None:
    router.get("/capabilities").mock(return_value=httpx.Response(200, content=CAPABILITIES))
    filesystem = VOSpaceFileSystem(
        BASE_URL,
        transport=httpx.MockTransport(router.async_handler),
        asynchronous=True,
        token="serialization-review-literal",
    )
    constructor_token = tokenize(filesystem)
    await filesystem._get_bindings()
    filesystem._authority = "example.test!vault"
    filesystem.dircache["/"] = []
    assert tokenize(filesystem) == constructor_token
    assert VOSpaceFileSystem(BASE_URL, asynchronous=True, token="serialization-review-literal") is filesystem

    pickled = pickle.loads(pickle.dumps(filesystem))  # noqa: S301 - trusted round-trip
    restored_json = VOSpaceFileSystem.from_json(filesystem.to_json())
    assert isinstance(restored_json, VOSpaceFileSystem)
    assert pickled is not filesystem
    assert restored_json is not filesystem
    assert restored_json is not pickled

    for restored in (pickled, restored_json):
        assert restored._loop is None
        assert restored._pool._clients == {}
        # Reconstruction builds fresh, unbound locks alongside the fresh pool.
        assert isinstance(restored._pool._lock, asyncio.Lock)
        assert restored._pool._injected is None
        assert restored._bindings is None
        assert isinstance(restored._bindings_lock, asyncio.Lock)
        assert restored._authority is None
        assert list(restored.dircache) == []
    await asyncio.gather(filesystem.aclose(), pickled.aclose(), restored_json.aclose())


@pytest.mark.parametrize(("asynchronous", "has_loop"), [(True, False), (False, True)])
def test_spawn_reconstructs_fresh_runtime_with_stable_dask_token(
    router: respx.Router,
    asynchronous: bool,
    has_loop: bool,
) -> None:
    filesystem = make_fs(router, asynchronous=asynchronous, token="literal")
    expected_token = tokenize(filesystem)
    context = multiprocessing.get_context("spawn")

    with ProcessPoolExecutor(max_workers=1, mp_context=context) as executor:
        result = executor.submit(_runtime_probe, filesystem).result(timeout=30)

    assert result == {
        "pid": result["process"],
        "process": result["process"],
        "token": expected_token,
        "has_loop": has_loop,
        "clients": {},
        "client_lock": "Lock",
        "transport": None,
        "bindings": None,
        "bindings_lock": "Lock",
        "authority": None,
        "cache": [],
    }


@pytest.mark.skipif(not hasattr(os, "fork"), reason="fork is unavailable")
def test_forked_live_instance_fails_before_cached_io(router: respx.Router) -> None:
    filesystem = make_fs(router, asynchronous=True)
    filesystem.dircache["/"] = [{"name": "/cached", "type": "file", "size": 1}]
    context = multiprocessing.get_context("fork")
    parent, child = context.Pipe(duplex=False)
    process = context.Process(target=_fork_probe, args=(filesystem, child))

    process.start()
    child.close()
    result = parent.recv()
    process.join(timeout=10)

    assert process.exitcode == 0
    assert result[0] == "RuntimeError"
    assert "reconstruct" in result[1]


@pytest.mark.parametrize("kind", ["service", "byte"])
def test_forked_live_instance_fails_before_direct_send(kind: str) -> None:
    context = multiprocessing.get_context("fork")
    counter = context.Value("i", 0)
    filesystem = VOSpaceFileSystem(
        BASE_URL,
        transport=_CountingTransport(counter),
        asynchronous=True,
        skip_instance_cache=True,
    )
    parent, child = context.Pipe(duplex=False)
    process = context.Process(target=_fork_send_probe, args=(filesystem, kind, child))

    process.start()
    child.close()
    result = parent.recv()
    process.join(timeout=10)

    assert process.exitcode == 0
    assert result[0] == "RuntimeError"
    assert "reconstruct" in result[1]
    assert counter.value == 0


@pytest.mark.parametrize("kind", ["service", "byte"])
async def test_closed_instance_fails_before_direct_send(kind: str) -> None:
    context = multiprocessing.get_context("spawn")
    counter = context.Value("i", 0)
    filesystem = VOSpaceFileSystem(
        BASE_URL,
        transport=_CountingTransport(counter),
        asynchronous=True,
        skip_instance_cache=True,
    )
    await filesystem.aclose()

    with pytest.raises(ValueError, match="closed"):
        await _direct_send(filesystem, kind)
    assert counter.value == 0


def test_cached_instance_rebinds_runtime_on_a_new_event_loop(router: respx.Router) -> None:
    """One cached filesystem must not keep the previous loop's clients."""
    VOSpaceFileSystem.clear_instance_cache()
    injected = httpx.MockTransport(router.async_handler)

    async def open_client() -> tuple[VOSpaceFileSystem, ClientPool]:
        filesystem = VOSpaceFileSystem(
            BASE_URL,
            transport=injected,
            asynchronous=True,
        )
        filesystem.dircache["/kept"] = []
        filesystem._authority = "example.test!vault"
        pool = filesystem._pool
        await pool.client()
        return filesystem, pool

    try:
        filesystem, first_pool = asyncio.run(open_client())

        async def reuse() -> ClientPool:
            again = VOSpaceFileSystem(BASE_URL, transport=injected, asynchronous=True)
            assert again is filesystem
            second_pool = again._pool
            assert second_pool is not first_pool
            assert again.dircache["/kept"] == []
            assert again._authority == "example.test!vault"
            await second_pool.client()
            return second_pool

        asyncio.run(reuse())
    finally:
        VOSpaceFileSystem.clear_instance_cache()


def test_dask_token_depends_only_on_constructor_state(router: respx.Router) -> None:
    first = make_fs(router, asynchronous=True, token="one")
    same = make_fs(router, asynchronous=True, token="one")
    changed = make_fs(router, asynchronous=True, token="two")

    assert tokenize(first) == tokenize(same)
    assert tokenize(first) != tokenize(changed)
