# Licensed under a 3-clause BSD style license - see LICENSE.rst

"""
This is Virtual Observatory Space (VOSpace) client
"""

try:
    _PACKAGE_SETUP_
except NameError:
    # not in setup mode
    from .vos import Client, Connection, Node, VOFile
