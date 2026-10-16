"""Contracts for the workspace packages that CI path filters cover."""

import re
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def test_every_libs_directory_has_a_package_workflow() -> None:
    packages = {path.parent.name for path in ROOT.glob("libs/*/pyproject.toml")}
    workflows = {
        path.name.removeprefix("ci-").removesuffix(".yml") for path in (ROOT / ".github" / "workflows").glob("ci-*.yml")
    }

    assert packages == workflows == {"vos", "vosfs", "fss-cli"}


def test_fss_cli_depends_on_vosfs_for_tests() -> None:
    config = tomllib.loads((ROOT / "libs" / "fss-cli" / "pyproject.toml").read_text())
    group = " ".join(config["dependency-groups"]["dev"])

    assert "vosfs" in group


def test_commit_scopes_name_every_package() -> None:
    pattern = tomllib.loads((ROOT / "pyproject.toml").read_text())["tool"]["commitizen"]["customize"]["schema_pattern"]
    scopes = set(re.search(r"\\\(\(([^)]*)\)", pattern).group(1).split("|"))

    assert scopes == {"vos", "vosfs", "fss-cli", "repo"}


def test_every_package_supports_the_ci_python_range() -> None:
    for pyproject in ROOT.glob("libs/*/pyproject.toml"):
        assert tomllib.loads(pyproject.read_text())["project"]["requires-python"] == ">=3.10", pyproject
