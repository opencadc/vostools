# Licensed under a 3-clause BSD style license - see LICENSE.rst

"""
This application mounts a VOSpace service to the local file system
 using fuse.
"""
try:
    _PACKAGE_SETUP_
except NameError:
    # not in setup mode
    from vofs import *
