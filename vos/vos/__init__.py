# Licensed under a 3-clause BSD style license - see LICENSE.rst

"""
 A Virtual Observatory Space (VOSpace) client library.

 The vos package includes a set of library classes that are useful for
 interacting with a VOSpace web service:
 (http://ivoa.net/documents/VOSpace/).

 The vos.Client class is used to programmatically interact with VOSpace
 services to list directories and files, copy files, delete files and
 directories etc. The packages also installs a number of unix-like file
 commands prefix with letter 'v': vls, vmkdir, vrm, etc.


"""
from .vos import Client, Connection, Node, VOFile  # noqa
