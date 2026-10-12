"""Run fss-cli compatibility tests from fresh installed wheels."""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

_REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
_PYTEST_REQUIREMENT = "pytest>=9.1.1"


def _run(
    command: list[str],
    *,
    cwd: Path,
    environment: dict[str, str],
) -> None:
    subprocess.run(  # noqa: S603 - command components are fixed local paths.
        command,
        cwd=cwd,
        env=environment,
        check=True,
    )


def _environment() -> dict[str, str]:
    environment = os.environ.copy()
    for name in (
        "PYTHONHOME",
        "PYTHONPATH",
        "UV_PROJECT",
        "UV_PROJECT_ENVIRONMENT",
        "VIRTUAL_ENV",
    ):
        environment.pop(name, None)
    environment.update(
        {
            "PYTHONDONTWRITEBYTECODE": "1",
            "PYTHONNOUSERSITE": "1",
            "PYTHONUNBUFFERED": "1",
        }
    )
    return environment


def _only_artifact(directory: Path, pattern: str) -> Path:
    matches = list(directory.glob(pattern))
    if len(matches) != 1:
        message = f"expected one {pattern!r} artifact, found {matches!r}"
        raise RuntimeError(message)
    return matches[0].resolve()


def _create_environment(
    uv: str,
    root: Path,
    name: str,
    wheels: list[Path],
    environment: dict[str, str],
) -> Path:
    venv = root / name
    _run(
        [sys.executable, "-m", "venv", "--without-pip", str(venv)],
        cwd=root,
        environment=environment,
    )
    python = venv / "bin" / "python"
    _run(
        [
            uv,
            "pip",
            "install",
            "--python",
            str(python),
            *(str(wheel) for wheel in wheels),
            _PYTEST_REQUIREMENT,
        ],
        cwd=root,
        environment=environment,
    )
    _run(
        [uv, "pip", "check", "--python", str(python)],
        cwd=root,
        environment=environment,
    )
    return python


def _build_artifacts(
    uv: str,
    root: Path,
    environment: dict[str, str],
) -> tuple[Path, Path, Path]:
    cli_dist = root / "dist" / "fss-cli"
    vosfs_dist = root / "dist" / "vosfs"
    rebuilt_dist = root / "dist" / "fss-cli-from-sdist"
    cli_dist.mkdir(parents=True)
    vosfs_dist.mkdir(parents=True)
    rebuilt_dist.mkdir(parents=True)

    _run(
        [
            uv,
            "build",
            "--package",
            "fss-cli",
            "--wheel",
            "--out-dir",
            str(cli_dist),
        ],
        cwd=_REPOSITORY_ROOT,
        environment=environment,
    )
    _run(
        [
            uv,
            "build",
            "--package",
            "fss-cli",
            "--sdist",
            "--out-dir",
            str(cli_dist),
        ],
        cwd=_REPOSITORY_ROOT,
        environment=environment,
    )
    _run(
        [
            uv,
            "build",
            "--package",
            "vosfs",
            "--wheel",
            "--out-dir",
            str(vosfs_dist),
        ],
        cwd=_REPOSITORY_ROOT,
        environment=environment,
    )

    cli_wheel = _only_artifact(cli_dist, "fss_cli-*.whl")
    cli_sdist = _only_artifact(cli_dist, "fss_cli-*.tar.gz")
    vosfs_wheel = _only_artifact(vosfs_dist, "vosfs-*.whl")
    _run(
        [
            uv,
            "build",
            "--wheel",
            "--out-dir",
            str(rebuilt_dist),
            str(cli_sdist),
        ],
        cwd=root,
        environment=environment,
    )
    _only_artifact(rebuilt_dist, "fss_cli-*.whl")
    return cli_wheel, cli_sdist, vosfs_wheel


def _extract_tests(source_distribution: Path, root: Path) -> Path:
    source_root = root / "source"
    source_root.mkdir()
    shutil.unpack_archive(source_distribution, source_root)
    extracted = [path for path in source_root.iterdir() if path.is_dir()]
    if len(extracted) != 1:
        message = f"expected one extracted source tree, found {extracted!r}"
        raise RuntimeError(message)
    tests = extracted[0] / "tests"
    if not tests.is_dir():
        message = f"source distribution has no tests directory: {tests}"
        raise RuntimeError(message)
    return tests


def _run_pytest(
    python: Path,
    tests: list[Path],
    *,
    cwd: Path,
    environment: dict[str, str],
) -> None:
    cwd.mkdir()
    _run(
        [python, "-I", "-m", "pytest", "-q", *(str(test) for test in tests)],
        cwd=cwd,
        environment=environment,
    )


def main() -> None:
    uv = shutil.which("uv")
    if uv is None:
        message = "installed-wheel gate requires uv on PATH"
        raise RuntimeError(message)

    with tempfile.TemporaryDirectory(prefix="fss-cli-wheel-") as temporary:
        root = Path(temporary).resolve()
        if root.is_relative_to(_REPOSITORY_ROOT):
            message = f"gate root must be outside repository: {root}"
            raise RuntimeError(message)

        environment = _environment()
        cli_wheel, cli_sdist, vosfs_wheel = _build_artifacts(
            uv,
            root,
            environment,
        )
        tests = _extract_tests(cli_sdist, root)
        environment.update(
            {
                "FSS_CLI_INSTALLED_WHEEL_GATE": "1",
                "FSS_CLI_REPOSITORY_ROOT": str(_REPOSITORY_ROOT),
                "FSS_CLI_SDIST": str(cli_sdist),
                "FSS_CLI_WHEEL": str(cli_wheel),
            }
        )

        core_python = _create_environment(
            uv,
            root,
            "core-environment",
            [cli_wheel],
            environment,
        )
        _run_pytest(
            core_python,
            [
                tests / "test_distribution.py",
                tests / "test_command_matrix.py",
                tests / "test_cat.py",
                tests / "test_cat_process.py",
                tests / "test_rmdir.py",
                tests / "test_mkdir.py",
                tests / "test_rm.py",
                tests / "test_recursive_rm.py",
                tests / "test_cp.py",
                tests / "test_recursive_cp.py",
                tests / "test_recursive_cp_adapters.py",
                tests / "test_recursive_cp_failures.py",
                tests / "test_recursive_cp_lifecycle.py",
                tests / "test_mv.py",
                tests / "test_unlink.py",
                tests / "test_stat.py",
            ],
            cwd=root / "core-tests",
            environment=environment,
        )

        vosfs_python = _create_environment(
            uv,
            root,
            "vosfs-environment",
            [cli_wheel, vosfs_wheel],
            environment,
        )
        vosfs_environment = {
            **environment,
            "FSS_CLI_VOSFS_WHEEL": str(vosfs_wheel),
        }
        _run_pytest(
            vosfs_python,
            [
                tests / "test_distribution.py",
                tests / "test_vosfs_command_matrix.py",
                tests / "test_vosfs_recursive_command_matrix.py",
                tests / ("test_vosfs_command_matrix.py::test_native_vosfs_rm_d_profile_uses_only_mocked_transport"),
            ],
            cwd=root / "vosfs-tests",
            environment=vosfs_environment,
        )


if __name__ == "__main__":
    main()
