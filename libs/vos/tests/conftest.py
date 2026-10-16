import os
import shutil
import tempfile

# vos reads ~/.config/vos/vos-config at import time and cadcutils reads
# ~/.netrc, so HOME must point at an empty directory before any test module
# imports vos. A fixture would run too late.
_TEST_HOME = tempfile.mkdtemp(prefix="vos-test-home-")
os.environ["HOME"] = _TEST_HOME
os.environ.pop("VOSPACE_CONFIG_FILE", None)


def pytest_unconfigure(config):
    shutil.rmtree(_TEST_HOME, ignore_errors=True)
