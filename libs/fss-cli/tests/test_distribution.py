"""Tests for the installed fss-cli distribution boundary."""

from __future__ import annotations

import json
import os
import re
import site
import sys
import tarfile
import zipfile
from email.parser import BytesParser
from importlib.metadata import PackageNotFoundError, distribution
from pathlib import Path

import pytest

import fss_cli

_GATE_ENVIRONMENT = "FSS_CLI_INSTALLED_WHEEL_GATE"

pytestmark = pytest.mark.skipif(
    os.environ.get(_GATE_ENVIRONMENT) != "1",
    reason="requires the isolated installed-wheel gate",
)


def _required_path(name: str) -> Path:
    value = os.environ.get(name)
    assert value is not None
    return Path(value).resolve()


def test_distribution_imports_only_from_installed_site_packages() -> None:
    repository = _required_path("FSS_CLI_REPOSITORY_ROOT")
    module_path = Path(fss_cli.__file__).resolve()
    site_packages = [Path(path).resolve() for path in site.getsitepackages()]

    assert sys.flags.isolated == 1
    assert "PYTHONPATH" not in os.environ
    assert not Path.cwd().resolve().is_relative_to(repository)
    assert any(module_path.is_relative_to(path) for path in site_packages)
    assert not module_path.is_relative_to(repository)
    import_paths = (Path(path or ".").resolve() for path in sys.path)
    assert all(not path.is_relative_to(repository) for path in import_paths)


def test_distribution_has_only_locked_runtime_metadata() -> None:
    installed = distribution("fss-cli")
    requirements = {
        re.split(r"[\s(<>=!~;\[]", requirement, maxsplit=1)[0].lower().replace("_", "-")
        for requirement in installed.requires or ()
    }

    assert requirements == {"fsspec", "typer"}
    assert [entry_point for entry_point in installed.entry_points if entry_point.group == "console_scripts"] == []


def test_distribution_is_non_editable_and_matches_built_wheel() -> None:
    wheel = _required_path("FSS_CLI_WHEEL")
    installed = distribution("fss-cli")
    direct_url_text = installed.read_text("direct_url.json")
    assert direct_url_text is not None
    direct_url = json.loads(direct_url_text)

    assert direct_url["url"] == wheel.as_uri()
    assert direct_url.get("dir_info", {}).get("editable") is not True
    assert "archive_info" in direct_url

    with zipfile.ZipFile(wheel) as archive:
        wheel_files = {member.filename for member in archive.infolist() if not member.is_dir()}
    installed_files = {str(path) for path in installed.files or ()}
    generated_files = {
        path
        for path in installed_files
        if path.endswith(("/INSTALLER", "/REQUESTED", "/direct_url.json", "/uv_cache.json"))
    }
    assert installed_files - generated_files == wheel_files


def test_distribution_artifacts_match_installed_version() -> None:
    wheel = _required_path("FSS_CLI_WHEEL")
    source_distribution = _required_path("FSS_CLI_SDIST")
    installed = distribution("fss-cli")
    installed_version = installed.version

    assert f"-{installed_version}-" in wheel.name
    assert source_distribution.name == f"fss_cli-{installed_version}.tar.gz"

    with tarfile.open(source_distribution, "r:gz") as archive:
        package_info_name = next(name for name in archive.getnames() if name.endswith("/PKG-INFO"))
        package_info_file = archive.extractfile(package_info_name)
        assert package_info_file is not None
        package_info = BytesParser().parse(package_info_file)
    assert package_info["Name"] == "fss-cli"
    assert package_info["Version"] == installed_version


def test_vosfs_integration_uses_a_separately_installed_wheel() -> None:
    wheel_value = os.environ.get("FSS_CLI_VOSFS_WHEEL")
    if wheel_value is None:
        pytest.skip("requires the isolated vosfs wheel environment")

    wheel = Path(wheel_value).resolve()
    installed = distribution("vosfs")
    direct_url_text = installed.read_text("direct_url.json")
    assert direct_url_text is not None
    direct_url = json.loads(direct_url_text)

    assert direct_url["url"] == wheel.as_uri()
    assert direct_url.get("dir_info", {}).get("editable") is not True
    assert all(not requirement.lower().startswith(("fss-cli", "fss_cli")) for requirement in installed.requires or ())


def test_core_environment_does_not_install_vosfs() -> None:
    if os.environ.get("FSS_CLI_VOSFS_WHEEL") is not None:
        pytest.skip("vosfs is expected in the separate integration environment")

    with pytest.raises(PackageNotFoundError):
        distribution("vosfs")
