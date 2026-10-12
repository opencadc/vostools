"""Suite-wide fixtures for the packaged fss-cli tests."""

import pytest

from ._network_guard import _block_network


@pytest.fixture(autouse=True)
def _prohibit_unplanned_network(monkeypatch: pytest.MonkeyPatch) -> None:
    _block_network(monkeypatch)
