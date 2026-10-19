"""fsspec reusable abstract suites wired against vosfs (TRD section 15.1 item 5).

Each abstract suite from :mod:`fsspec.tests.abstract` is subclassed against a
simulator-backed synchronous :class:`~vosfs.VOSpaceFileSystem`. A single
:class:`VOSpaceFixtures` mixin supplies the ``fs``/``fs_join``/``fs_path``
fixtures; the inherited scenario fixtures build their trees through that same
filesystem, so state persists in the in-memory simulator.

The pinned fsspec 2026.6.0 suites collect 137 cases: 131 supported cases run and
six question-mark glob cases are skipped as one explicit Unsupported capability.
The six skips are the ``fil?1`` non-recursive and recursive rows in copy, get,
and put. ``?`` is a literal name character, so those glob paths are outside
the contract.

List-source get, including the hashed-name fixture, runs as supported evidence;
its former failure was fixture teardown, not a backend capability gap. All
missing-parent put cases also run after coordinated writes gained top-down
parent materialization.
"""

from __future__ import annotations

import posixpath
from typing import TYPE_CHECKING

import pytest
import respx
from conftest import BASE_URL, make_fs
from fsspec.tests.abstract import (
    AbstractCopyTests,
    AbstractFixtures,
    AbstractGetTests,
    AbstractOpenTests,
    AbstractPipeTests,
    AbstractPutTests,
)
from vospace_sim import VOSpaceSim

if TYPE_CHECKING:
    from collections.abc import Iterator

    from vosfs import VOSpaceFileSystem

# The six question-mark glob edge cases are skipped by the
# ``pytest_collection_modifyitems`` hook in ``conftest.py``.


class VOSpaceFixtures(AbstractFixtures):
    """Bind the abstract fixtures to a fresh simulator-backed filesystem."""

    @pytest.fixture
    def fs(self) -> Iterator[VOSpaceFileSystem]:
        sim = VOSpaceSim()
        router = respx.Router(base_url=BASE_URL, assert_all_mocked=True)
        sim.install(router)
        filesystem = make_fs(router)
        yield filesystem
        filesystem.close()
        router.reset()

    @pytest.fixture
    def fs_join(self):
        # VOSpace paths are always POSIX; join with a forward slash regardless of
        # the host platform.
        return posixpath.join

    @pytest.fixture
    def fs_path(self) -> str:
        return "/"


class TestCopy(VOSpaceFixtures, AbstractCopyTests):
    """Remote-to-remote copy suite."""

    # Copying a file below a not-yet-created directory, and recursive/directory
    # copies, are supported: ``_cp_file`` creates the destination file's parent
    # and materializes intermediate ContainerNodes, so these inherited tests run.


class TestGet(VOSpaceFixtures, AbstractGetTests):
    """Remote-to-local download suite."""

    # test_get_list_of_files_to_{existing,new}_directory now run: _strip_protocol
    # normalizes fsspec's forwarded list of sources (see the list branch on
    # VOSpaceFileSystem._strip_protocol).


class TestPut(VOSpaceFixtures, AbstractPutTests):
    """Local-to-remote upload suite."""


class TestPipe(VOSpaceFixtures, AbstractPipeTests):
    """Whole-object pipe suite."""


class TestOpen(VOSpaceFixtures, AbstractOpenTests):
    """File-handle open suite."""
