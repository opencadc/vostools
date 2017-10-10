# Licensed under a 3-clause BSD style license - see LICENSE.rst

"""
 A Virtual Observatory Space (VOSpace) client library.

 The vos package includes a set of library classes that are useful for
 interacting with a VOSpace web service:
 (http://ivoa.net/documents/VOSpace/).  The libraries have been developed
 against the CADC (http://www.cadc-ccda.hia-iha.nrc-cnrc.gc.ca/vospace)
 VOSpace implementation as used by the CANFAR (http://www.canfar.net) project.

 The Client class is the most useful for the majority of interacations with
 the VOSpace service


"""
from .vos import Client, Connection, Node, VOFile  # noqa
