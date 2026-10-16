"""Scientific-stack compatibility gates (section 14).

These are narrow hermetic gates that prove each named consumer round-trips
through ``vos://`` URLs and the injected transport seam.
"""

import httpx
import numpy as np
import pytest
import respx
from conftest import BASE_URL
from vospace_sim import VOSpaceSim

from vosfs import VOSpaceFileSystem


def _setup(router: respx.Router) -> tuple[VOSpaceSim, dict[str, object]]:
    sim = VOSpaceSim()
    sim.install(router)
    transport = httpx.MockTransport(router.async_handler)
    options = {
        "endpoint_url": BASE_URL,
        "transport": transport,
        "skip_instance_cache": True,
    }
    return sim, options


def _fs(router: respx.Router) -> tuple[VOSpaceSim, VOSpaceFileSystem]:
    sim, options = _setup(router)
    return sim, VOSpaceFileSystem(**options)  # type: ignore[arg-type]


# --- pandas ---------------------------------------------------------------------


def test_pandas_csv_round_trip(router: respx.Router) -> None:
    pd = pytest.importorskip("pandas")
    _sim, options = _setup(router)
    frame = pd.DataFrame({"a": [1, 2, 3], "b": ["x", "y", "z"]})
    frame.to_csv("vos://data.csv", index=False, storage_options=options)
    restored = pd.read_csv("vos://data.csv", storage_options=options)
    pd.testing.assert_frame_equal(restored, frame)


# --- numpy ----------------------------------------------------------------------


def test_numpy_npy_round_trip(router: respx.Router) -> None:
    import fsspec

    _sim, options = _setup(router)
    array = np.arange(12, dtype="int64").reshape(3, 4)
    with fsspec.open("vos://a.npy", "wb", **options) as handle:
        np.save(handle, array)
    with fsspec.open("vos://a.npy", "rb", **options) as handle:
        restored = np.load(handle)
    assert np.array_equal(restored, array)


def test_numpy_npz_round_trip(router: respx.Router) -> None:
    import fsspec

    _sim, options = _setup(router)
    with fsspec.open("vos://a.npz", "wb", **options) as handle:
        np.savez(handle, x=np.arange(4), y=np.ones(3))
    with fsspec.open("vos://a.npz", "rb", **options) as handle:
        loaded = np.load(handle)
        assert np.array_equal(loaded["x"], np.arange(4))
        assert np.array_equal(loaded["y"], np.ones(3))


def test_numpy_loadtxt_through_file_object(router: respx.Router) -> None:
    import fsspec

    _sim, options = _setup(router)
    with fsspec.open("vos://a.txt", "wb", **options) as handle:
        handle.write(b"1 2 3\n4 5 6\n")
    with fsspec.open("vos://a.txt", "rb", **options) as handle:
        restored = np.loadtxt(handle)
    assert np.array_equal(restored, np.array([[1, 2, 3], [4, 5, 6]], dtype="float64"))


# --- dask -----------------------------------------------------------------------


def test_dask_csv_round_trip(router: respx.Router) -> None:
    pd = pytest.importorskip("pandas")
    dd = pytest.importorskip("dask.dataframe")
    _sim, options = _setup(router)
    frame = pd.DataFrame({"a": [1, 2, 3, 4], "b": [10, 20, 30, 40]})
    frame.to_csv("vos://d.csv", index=False, storage_options=options)
    lazy = dd.read_csv("vos://d.csv", storage_options=options, blocksize=None)
    result = lazy.compute(scheduler="synchronous")
    pd.testing.assert_frame_equal(result.reset_index(drop=True), frame)


def test_dask_deterministic_tokenization(router: respx.Router) -> None:
    from dask.base import tokenize

    _sim, filesystem = _fs(router)
    assert tokenize(filesystem) == tokenize(filesystem)


# --- zarr v3 --------------------------------------------------------------------


def test_zarr_store_round_trip(router: respx.Router) -> None:
    zarr = pytest.importorskip("zarr")
    _sim, options = _setup(router)
    filesystem = VOSpaceFileSystem(asynchronous=True, **options)  # type: ignore[arg-type]
    store = zarr.storage.FsspecStore(filesystem, path="/z")
    root = zarr.open_group(store=store, mode="w")
    array = root.create_array("data", shape=(10,), dtype="int32")
    array[:] = np.arange(10, dtype="int32")

    reopened = zarr.open_group(store=store, mode="r")
    assert np.array_equal(reopened["data"][:], np.arange(10, dtype="int32"))
    assert reopened["data"][3:6].tolist() == [3, 4, 5]  # partial-value read


# --- pyarrow / parquet ----------------------------------------------------------


def test_pyarrow_parquet_round_trip(router: respx.Router) -> None:
    pa = pytest.importorskip("pyarrow")
    pq = pytest.importorskip("pyarrow.parquet")
    from pyarrow.fs import FSSpecHandler, PyFileSystem

    _sim, filesystem = _fs(router)
    pa_fs = PyFileSystem(FSSpecHandler(filesystem))
    table = pa.table({"a": [1, 2, 3], "b": ["x", "y", "z"]})
    pq.write_table(table, "/data.parquet", filesystem=pa_fs)
    restored = pq.read_table("/data.parquet", filesystem=pa_fs)
    assert restored.equals(table)
