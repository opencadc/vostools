"""Library-only POSIX-shaped commands for async fsspec filesystems."""

import logging

from ._app import (
    App,
    AppCapabilities,
    AsyncFilesystemSource,
    CommandCallback,
    CommandContext,
    RecursionCapabilities,
)

# Library default: records reach only the handlers a host configures.
logging.getLogger(__name__).addHandler(logging.NullHandler())

__all__ = [
    "App",
    "AppCapabilities",
    "AsyncFilesystemSource",
    "CommandCallback",
    "CommandContext",
    "RecursionCapabilities",
]
