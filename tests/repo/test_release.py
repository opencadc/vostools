"""Contracts for release-please and the guarded publication workflows."""

import json
import re
from pathlib import Path

import pytest
import yaml

ROOT = Path(__file__).resolve().parents[2]
CONFIG = json.loads((ROOT / "release-please-config.json").read_text())
MANIFEST = json.loads((ROOT / ".release-please-manifest.json").read_text())
PACKAGES = {"libs/vos": "vos", "libs/vosfs": "vosfs", "libs/fss-cli": "fss-cli"}
UPSTREAM_GUARD = "github.repository == 'opencadc/vostools'"
BASELINE = "e96b7630e1af4286afd7b57ff14ed34ea4b8e3a7"


def workflow(name: str) -> dict:
    loaded = yaml.safe_load((ROOT / ".github" / "workflows" / name).read_text())
    return loaded


def triggers(loaded: dict) -> dict:
    return loaded.get("on", loaded.get(True))


def test_manifest_records_only_released_versions() -> None:
    assert set(MANIFEST) == set(PACKAGES)
    assert MANIFEST == {"libs/vos": "3.7.0", "libs/vosfs": "0.11.0", "libs/fss-cli": "0.10.0"}
    assert "packages" not in MANIFEST
    assert "release-type" not in MANIFEST


def test_release_please_config_is_separate_from_the_manifest() -> None:
    assert CONFIG["separate-pull-requests"] is True
    assert CONFIG["include-component-in-tag"] is True
    assert CONFIG["include-v-in-tag"] is True
    assert CONFIG["draft"] is True
    assert CONFIG["last-release-sha"] == BASELINE
    assert set(CONFIG["packages"]) == set(PACKAGES)


@pytest.mark.parametrize(("path", "component"), PACKAGES.items())
def test_each_package_bumps_its_own_pyproject_and_lock_entry(path: str, component: str) -> None:
    package = CONFIG["packages"][path]

    assert package["release-type"] == "python"
    assert package["component"] == component
    assert package["package-name"] == component
    assert package["changelog-path"] == "CHANGELOG.md"
    assert package["extra-files"] == [
        {
            "type": "toml",
            "path": "/uv.lock",
            "jsonpath": f"$.package[?(@.name.value=='{component}')].version",
        }
    ]


def test_pre_1_0_packages_bump_minor_for_a_breaking_change() -> None:
    for path in ("libs/vosfs", "libs/fss-cli"):
        package = CONFIG["packages"][path]
        assert package["bump-minor-pre-major"] is True
        assert package["bump-patch-for-minor-pre-major"] is False


def test_release_workflow_dispatches_one_package_path_and_one_docs_event() -> None:
    text = (ROOT / ".github" / "workflows" / "cd.yml").read_text()
    loaded = workflow("cd.yml")
    job = loaded["jobs"]["release-please"]

    assert triggers(loaded)["push"]["branches"] == ["main"]
    assert UPSTREAM_GUARD in job["if"]
    assert "pypi-release" in text
    assert "docs-publish" in text
    assert "client_payload:{path:$path, package:$package, tag_name:$tag, sha:$sha}" in text
    assert text.count('event_type:"docs-publish"') == 1
    assert "RELEASE_PLEASE_TOKEN" in text


def test_pypi_workflow_publishes_only_the_dispatched_package() -> None:
    text = (ROOT / ".github" / "workflows" / "pypi.yml").read_text()
    loaded = workflow("pypi.yml")
    jobs = loaded["jobs"]

    assert triggers(loaded)["repository_dispatch"]["types"] == ["pypi-release"]
    assert 'uv build --no-sources --package "$PACKAGE"' in text
    assert "environment" in jobs["publish"]
    assert jobs["publish"]["environment"]["name"] == "pypi-${{ needs.build.outputs.package }}"
    assert jobs["publish"]["permissions"]["id-token"] == "write"
    assert "--draft=false" in text
    assert text.index("gh-action-pypi-publish@") < text.index("--draft=false")
    for name, job in jobs.items():
        assert UPSTREAM_GUARD in job["if"], name


def test_docs_workflow_builds_one_site() -> None:
    names = sorted(path.name for path in (ROOT / ".github" / "workflows").glob("docs*.yml"))
    text = (ROOT / ".github" / "workflows" / "docs.yml").read_text()

    assert names == ["docs.yml"]
    assert triggers(workflow("docs.yml"))["repository_dispatch"]["types"] == ["docs-publish"]
    assert "zensical build --strict" in text
    assert UPSTREAM_GUARD in workflow("docs.yml")["jobs"]["docs"]["if"]
    assert "libs/vos" not in text
    assert "libs/fss-cli" not in text


def test_legacy_vos_publisher_stays_until_cutover() -> None:
    text = (ROOT / ".github" / "workflows" / "cibuild.yml").read_text()
    loaded = workflow("cibuild.yml")

    assert "vos=<version>" in text
    assert "pypi.yml" in text
    assert UPSTREAM_GUARD in loaded["jobs"]["build-n-publish"]["if"]
    assert "contains(github.ref, '=')" in loaded["jobs"]["build-n-publish"]["if"]


def test_privileged_jobs_run_only_on_the_upstream_repository() -> None:
    for path in (ROOT / ".github" / "workflows").glob("*.yml"):
        for name, job in workflow(path.name)["jobs"].items():
            permissions = job.get("permissions", {})
            steps = job.get("steps", [])
            privileged = (
                permissions.get("id-token") == "write"
                or permissions.get("contents") == "write"
                or permissions.get("pull-requests") == "write"
                or any(
                    "release-please-action@" in step.get("uses", "")
                    or step.get("uses", "").startswith("pypa/gh-action-pypi-publish@")
                    for step in steps
                )
            )
            if privileged:
                assert UPSTREAM_GUARD in job.get("if", ""), f"{path.name}:{name}"
                assert re.search(r"opencadc/vostools", job["if"])
