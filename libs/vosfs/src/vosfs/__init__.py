"""An asynchronous fsspec filesystem for the OpenCADC VOSpace profile.

``vosfs`` provides the ``vos`` protocol for fsspec-aware Python tools. See the
v0.3.0 capability contract in ``docs/design/trd.md`` for the normative surface.
The ``vos`` protocol is registered with fsspec through the ``fsspec.specs``
entry-point group declared in ``pyproject.toml``.

Transfer progress is reported through standard-library logging under the
``vosfs`` logger: one ``INFO`` record per completed file operation and
``DEBUG`` records for negotiation and integrity checks. The package installs
only a ``NullHandler``; applications choose whether and where records appear.
"""

import logging

from vosfs.errors import VOSpaceError
from vosfs.filesystem import VOSpaceFileSystem

logging.getLogger(__name__).addHandler(logging.NullHandler())

__all__ = ["VOSpaceError", "VOSpaceFileSystem"]
