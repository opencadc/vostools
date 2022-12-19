# ***********************************************************************
# ******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
# *************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
#
#  (c) 2022.                            (c) 2022.
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

"""cat VOSpace DataNode to stdout"""
import sys
import logging
from ..vos import Client
from ..commonparser import CommonParser, set_logging_level_from_args, \
    exit_on_exception, URI_DESCRIPTION


def _cat(uri, cert_filename=None, head=None, insecure=False):
    """Cat out the given uri stored in VOSpace.

    :param uri: the VOSpace URI that will be piped to stdout.
    :type uri: basestring
    :param cert_filename: filename of the PEM certificate used to gain access.
    :param insecure: SSL server certificates not checked
    :type cert_filename: basestring
    """

    fh = None
    try:
        view = head and 'header' or 'data'
        c = Client(vospace_certfile=cert_filename, insecure=insecure)
        fh = c.open(uri, view=view)
        if c.is_remote_file(uri):
            sys.stdout.write(fh.read(return_response=True).text)
            sys.stdout.write('\n\n')
        else:
            fh = open(uri, str("r"))
            sys.stdout.write(fh.read())
    finally:
        if fh:
            fh.close()


DESCRIPTION = """Write the content of source (eg. vos:Node/filename) to stdout.

{}

Accepts cutout syntax for FITS files; see vcp --help for syntax details
""".format(URI_DESCRIPTION)


def vcat():
    parser = CommonParser(description=DESCRIPTION)
    parser.add_argument("source", help="source to cat to stdout out.",
                        nargs="+")
    parser.add_argument("--head", action="store_true",
                        help="retrieve only the headers of file from vospace. "
                             "Might return an error if server does not "
                             "support operation on a given file type.")
    parser.add_argument("-q",
                        help="run quietly, exit on error without message",
                        action="store_true")

    args = parser.parse_args()
    set_logging_level_from_args(args)

    logger = logging.getLogger()

    exit_code = 0

    try:
        for uri in args.source:
            if not uri.startswith('vos') and args.head:
                logger.error('FITS header not supported for local source {}'.
                             format(uri))
                exit_code = 1
                continue
            try:
                _cat(uri, cert_filename=args.certfile, head=args.head,
                     insecure=args.insecure)
            except Exception as e:
                exit_code = getattr(e, 'errno', -1)
                if not args.q:
                    logger.error(str(e))
    except KeyboardInterrupt as ke:
        exit_on_exception(ke)

    sys.exit(exit_code)


vcat.__doc__ = DESCRIPTION
