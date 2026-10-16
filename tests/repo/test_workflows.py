"""Static contracts for the GitHub Actions workflows."""

import re
from pathlib import Path

import pytest
import yaml

ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS = sorted((ROOT / ".github" / "workflows").glob("*.yml"))
QUALITY = ROOT / ".github" / "workflows" / "quality.yml"
PACKAGE_WORKFLOWS = {
    "vos": ROOT / ".github" / "workflows" / "ci-vos.yml",
    "vosfs": ROOT / ".github" / "workflows" / "ci-vosfs.yml",
    "fss-cli": ROOT / ".github" / "workflows" / "ci-fss-cli.yml",
}
SHARED_PATHS = {
    "pyproject.toml",
    "uv.lock",
    ".pre-commit-config.yaml",
    ".github/**",
    "tests/repo/**",
}
UPSTREAM_GUARD = "github.repository == 'opencadc/vostools'"
PINNED = re.compile(r"^[\w.-]+/[\w./-]+@[0-9a-f]{40}$")


def load(path: Path) -> dict:
    return yaml.safe_load(path.read_text())


def triggers(workflow: dict) -> dict:
    # YAML 1.1 reads the bare key `on` as the boolean True.
    return workflow.get("on", workflow.get(True))


def steps(workflow: dict) -> list[dict]:
    return [step for job in workflow["jobs"].values() for step in job.get("steps", [])]


@pytest.mark.parametrize("path", WORKFLOWS, ids=lambda path: path.name)
def test_third_party_actions_are_pinned_to_a_commit(path: Path) -> None:
    for step in steps(load(path)):
        if "uses" in step and not step["uses"].startswith("./"):
            assert PINNED.match(step["uses"]), step["uses"]


@pytest.mark.parametrize("path", WORKFLOWS, ids=lambda path: path.name)
def test_workflows_default_to_read_only_contents(path: Path) -> None:
    assert load(path)["permissions"] == {"contents": "read"}


@pytest.mark.parametrize("path", WORKFLOWS, ids=lambda path: path.name)
def test_checkouts_do_not_persist_credentials(path: Path) -> None:
    for step in steps(load(path)):
        if step.get("uses", "").startswith("actions/checkout@"):
            assert step["with"]["persist-credentials"] is False, step


@pytest.mark.parametrize("path", WORKFLOWS, ids=lambda path: path.name)
def test_every_job_has_a_timeout(path: Path) -> None:
    for name, job in load(path)["jobs"].items():
        assert "uses" in job or "timeout-minutes" in job, name


@pytest.mark.parametrize("path", WORKFLOWS, ids=lambda path: path.name)
def test_publishing_runs_only_on_the_upstream_repository(path: Path) -> None:
    for name, job in load(path)["jobs"].items():
        publishes = job.get("permissions", {}).get("id-token") == "write" or any(
            step.get("uses", "").startswith("pypa/gh-action-pypi-publish@") for step in job.get("steps", [])
        )
        if publishes:
            assert UPSTREAM_GUARD in job.get("if", ""), name
            assert "shinybrar/vostools" not in job["if"]


def test_quality_always_runs_and_exposes_required() -> None:
    workflow = load(QUALITY)
    on = triggers(workflow)
    jobs = workflow["jobs"]

    assert "pull_request" in on
    assert on["push"]["branches"] == ["main"]
    assert on["schedule"]
    assert "workflow_dispatch" in on
    assert "workflow_call" in on
    assert "paths" not in on.get("pull_request", {})
    assert jobs["required"]["if"] == "always()"
    assert set(jobs["required"]["needs"]) == {"quality", "pr-title"}


def test_quality_runs_contract_tests_and_docs() -> None:
    text = QUALITY.read_text()

    assert "pytest tests/repo" in text
    assert "zensical build --strict" in text
    assert "uv build --no-sources --all-packages" in text


@pytest.mark.parametrize(("package", "path"), PACKAGE_WORKFLOWS.items())
def test_package_workflow_uses_path_filters(package: str, path: Path) -> None:
    on = triggers(load(path))
    distribution = package

    for event in ("pull_request", "push"):
        paths = set(on[event]["paths"])
        assert f"libs/{package}/**" in paths
        assert paths >= SHARED_PATHS
        if package == "fss-cli":
            assert "libs/vosfs/**" in paths
    assert load(path)["jobs"]["test"]["with"] == {
        "package": package,
        "distribution": distribution,
    }


def test_no_custom_github_scripts() -> None:
    scripts = ROOT / ".github" / "scripts"
    assert not scripts.exists()


def test_quality_has_no_live_test_or_credential_wiring() -> None:
    workflow = QUALITY.read_text()

    assert set(re.findall(r"secrets\.(\w+)", workflow)) <= {"CODECOV_TOKEN"}
    for forbidden in ("CADC_USERNAME", "CADC_PASSWORD", "VOSFS_TEST_", "VOSFS_CERT_FILE", "integration"):
        assert forbidden not in workflow
