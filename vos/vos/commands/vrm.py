# ***********************************************************************
# ******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
# *************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
#
#  (c) 2024.                            (c) 2024.
#  Government of Canada                 Gouvernement du Canada
#  National Research Council            Conseil national de recherches
#  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
#  All rights reserved                  Tous droits réservés
#
#  NRC disclaims any warranties,        Le CNRC dénie toute garantie
#  expressed, implied, or               énoncée, implicite ou légale,
#  statutory, of any kind with          de quelque nature que ce
#  respect to the software,             soit, concernant le logiciel,
#  including without limitation         y compris sans restriction
#  any warranty of merchantability      toute garantie de valeur
#  or fitness for a particular          marchande ou de pertinence
#  purpose. NRC shall not be            pour un usage particulier.
#  liable in any event for any          Le CNRC ne pourra en aucun cas
#  damages, whether direct or           être tenu responsable de tout
#  indirect, special or general,        dommage, direct ou indirect,
#  consequential or incidental,         particulier ou général,
#  arising from the use of the          accessoire ou fortuit, résultant
#  software.  Neither the name          de l'utilisation du logiciel. Ni
#  of the National Research             le nom du Conseil National de
#  Council of Canada nor the            Recherches du Canada ni les noms
#  names of its contributors may        de ses  participants ne peuvent
#  be used to endorse or promote        être utilisés pour approuver ou
#  products derived from this           promouvoir les produits dérivés
#  software without specific prior      de ce logiciel sans autorisation
#  written permission.                  préalable et particulière
#                                       par écrit.
#
#  This file is part of the             Ce fichier fait partie du projet
#  OpenCADC project.                    OpenCADC.
#
#  OpenCADC is free software:           OpenCADC est un logiciel libre ;
#  you can redistribute it and/or       vous pouvez le redistribuer ou le
#  modify it under the terms of         modifier suivant les termes de
#  the GNU Affero General Public        la “GNU Affero General Public
#  License as published by the          License” telle que publiée
#  Free Software Foundation,            par la Free Software Foundation
#  either version 3 of the              : soit la version 3 de cette
#  License, or (at your option)         licence, soit (à votre gré)
#  any later version.                   toute version ultérieure.
#
#  OpenCADC is distributed in the       OpenCADC est distribué
#  hope that it will be useful,         dans l’espoir qu’il vous
#  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
#  without even the implied             GARANTIE : sans même la garantie
#  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
#  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
#  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
#  General Public License for           Générale Publique GNU Affero
#  more details.                        pour plus de détails.
#
#  You should have received             Vous devriez avoir reçu une
#  a copy of the GNU Affero             copie de la Licence Générale
#  General Public License along         Publique GNU Affero avec
#  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
#  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
#                                       <http://www.gnu.org/licenses/>.
#
#  $Revision: 4 $
#
# ***********************************************************************
#

"""remove a vospace data node, fails if container node or node is locked."""
import logging
import sys

from ..commonparser import set_logging_level_from_args, exit_on_exception, \
    CommonParser, URI_DESCRIPTION
from .. import vos

DESCRIPTION = """remove a vospace data node; fails if container node or node is locked.

{}

eg. vrm vos:/root/node   -- deletes a data node""".format(URI_DESCRIPTION)


def vrm():
    parser = CommonParser(description=DESCRIPTION)
    parser.add_argument(
        "-R", "--recursive", action="store_true",
        help="Delete a file or directory even if it's not empty.",
        default=False)
    parser.add_argument('node',
                        help='file, link or possibly directory to delete from VOSpace',
                        nargs='+')

    args = parser.parse_args()
    set_logging_level_from_args(args)

    try:
        for node in args.node:
            client = vos.Client(
                vospace_certfile=args.certfile,
                vospace_token=args.token,
                insecure=args.insecure)
            if not client.is_remote_file(node):
                raise Exception(
                    '{} is not a valid VOSpace handle'.format(node))
            if args.recursive:
                successes, failures = client.recursive_delete(node)
                if failures:
                    logging.error('WARN. deleted count: {}, failed count: '
                                  '{}\n'.format(successes, failures))
                    sys.exit(-1)
                else:
                    logging.info(
                        'DONE. deleted count: {}\n'.format(successes))
            else:
                if not node.endswith('/'):
                    if client.get_node(node).islink():
                        logging.info('deleting link {}'.format(node))
                        client.delete(node)
                    elif client.isfile(node):
                        logging.info('deleting {}'.format(node))
                        client.delete(node)
                elif client.isdir(node):
                    raise Exception('{} is a directory'.format(node))
                else:
                    raise Exception('{} is not a directory'.format(node))

    except Exception as ex:
        exit_on_exception(ex)


vrm.__doc__ = DESCRIPTION
