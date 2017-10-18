"""
Command line scripts for interacting with the VOSpace service.

These commands are designed to mimic their unix conterparts.
see:  command --help for details

"""
from .vcat import vcat
from .vchmod import vchmod
from .vcp import vcp
from .vln import vln
from .vlock import vlock
from .vls import vls
from .vmkdir import vmkdir
from .vmv import vmv
from .vrm import vrm
from .vrmdir import vrmdir
from .vsync import vsync
from .vtag import vtag

__all__ = ['vcp', 'vcat', 'vchmod', 'vln', 'vlock', 'vls', 'vmkdir',
           'vmv', 'vrm', 'vrmdir', 'vsync', 'vtag']
