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

"""
A common commandline parser for the VOS command line tool set.
"""
import logging
import argparse
import os
import signal
import sys
import traceback
from .version import version
from .vosconfig import _CONFIG_PATH


def signal_handler(signum, frame):
    """Exit without calling cleanup handlers, flushing stdio buffers, etc. """
    logging.info('Received signal {}. Exiting.'.format(signum))
    os._exit(signum)


signal.signal(signal.SIGINT, signal_handler)    # Ctrl-C
# Disabled due to unexpected SIGPIPE signals that do not warn the user
# signal.signal(signal.SIGPIPE, signal_handler)   # Pipe gone (head, more etc)


def exit_on_exception(ex, message=None):
    """
    Exit program due to an exception, print the exception and exit with error
    code.
    :param ex:
    :param message: error message to display
    :return:
    """
    # Note: this could probably be updated to use an appropriate logging
    # handler instead of writing to stderr
    if message:
        sys.stderr.write('ERROR:: {}\n'.format(message))
    else:
        sys.stderr.write('ERROR:: {}\n'.format(str(ex)))
    tb = traceback.format_exc()
    logging.debug(tb)
    sys.exit(getattr(ex, 'errno', -1)) if getattr(ex, 'errno',
                                                  -1) else sys.exit(-1)


def set_logging_level_from_args(args):
    """Display version, set logging verbosity"""

    # Logger verbosity
    if args.debug:
        args.log_level = logging.DEBUG
    elif args.verbose:
        args.log_level = logging.INFO
    elif args.warning:
        args.log_level = logging.WARNING
    else:
        args.log_level = logging.ERROR

    log_format = "%(levelname)s %(module)s %(message)s"
    if args.log_level < logging.INFO:
        log_format = (
            "%(levelname)s %(asctime)s %(thread)d vos-" + str(version) +
            " %(module)s.%(funcName)s.%(lineno)d %(message)s")
    logging.basicConfig(format=log_format, level=args.log_level)
    logger = logging.getLogger('root')
    logger.setLevel(args.log_level)

    if args.vos_debug:
        logger = logging.getLogger('vos')
        logger.setLevel(logging.DEBUG)

    if sys.version_info[1] > 6:
        logging.getLogger().addHandler(logging.NullHandler())


class CommonParser(argparse.ArgumentParser):
    """Class to hold and parse common command-line arguments for vos clients"""

    add_option = argparse.ArgumentParser.add_argument

    def __init__(self, *args, **kwargs):
        # call the parent constructor
        if os.path.isfile(_CONFIG_PATH):
            epilog = 'Default service settings in {}.'.format(_CONFIG_PATH)
        else:
            epilog = ''
        super(CommonParser, self).__init__(
            *args,
            formatter_class=argparse.RawDescriptionHelpFormatter,
            epilog=epilog,
            **kwargs)
        # inherit the VOS client version
        self.version = version
        self.log_level = logging.ERROR

        # now add on the common parameters
        self.add_argument(
            "--certfile",
            help="filename of your CADC X509 authentication certificate",
            default=os.path.join(os.getenv("HOME", "."), ".ssl/cadcproxy.pem"))
        self.add_argument(
            "--token",
            help="authentication token string (alternative to certfile)",
            default=None)
        self.add_argument("--version", action="version",
                          version=version)
        self.add_argument("-d", "--debug", action="store_true", default=False,
                          help=argparse.SUPPRESS)
        self.add_argument("--vos-debug", action="store_true",
                          help="Print on vos debug messages.")
        self.add_argument("-v", "--verbose", action="store_true",
                          default=False,
                          help="print verbose messages")
        self.add_argument("-w", "--warning", action="store_true",
                          default=False,
                          help="print warning messages only")
        self.add_argument('-k', '--insecure', action='store_true',
                          help=argparse.SUPPRESS)


URI_DESCRIPTION = \
    'Remote resources are identified either by their full URIs \n' \
    '(vos://cadc.nrc.ca~vault/<path>) or by shorter equivalent URIs with \n' \
    'the scheme representing the name of the service (vault:<path>). \n' \
    'Due to historical reasons, the `vos` scheme can be used to refer \n' \
    'to the `vault` service, ie vos:<path> and vault:<path> are equivalent.\n'
