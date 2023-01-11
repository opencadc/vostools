# ***********************************************************************
# ******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
# *************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
#
#  (c) 2023.                            (c) 2023.
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

import sys
import os
import unittest
from io import StringIO
from unittest.mock import Mock, patch
from vos import commands as cmds

THIS_DIR = os.path.dirname(os.path.realpath(__file__))
TESTDATA_DIR = os.path.join(THIS_DIR, 'data')


class MyExitError(Exception):

    def __init__(self):
        self.message = "MyExitError"


# to capture the output of executing a command, sys.exit is patched to
# throw an MyExitError exception. The number of such exceptions is based
# on the number of commands and the number of times they are invoked
outputs = [MyExitError] * (len(cmds.__all__) + 3)


class TestCli(unittest.TestCase):
    """
    Basic tests of the command line interface for various vos commands.
    For each command it tests the invocation of the command without arguments
    and with the --help flag against a known output.
    """

    @patch('sys.exit', Mock(side_effect=outputs))
    def test_cli_noargs(self):
        """Test the invocation of a command without arguments"""

        # get a list of all available commands
        for cmd in cmds.__all__:
            with patch('sys.stdout', new_callable=StringIO) as stdout_mock:
                with open(os.path.join(TESTDATA_DIR, '{}.txt'.format(cmd)),
                          'r') as f:
                    usage = f.read()
                sys.argv = '{}'.format(cmd).split()
                with self.assertRaises(MyExitError):
                    cmd_attr = getattr(cmds, cmd)
                    cmd_attr()
                    self.assertTrue(stdout_mock.getvalue().contains(usage))

    @patch('sys.exit', Mock(side_effect=outputs))
    def test_cli_help_arg(self):
        """Test the invocation of a command with --help argument"""

        # get a list of all available commands
        for cmd in cmds.__all__:
            with patch('sys.stdout', new_callable=StringIO) as stdout_mock:
                with open(os.path.join(
                        TESTDATA_DIR, 'help_{}.txt'.format(cmd)), 'r') as f:
                    usage = f.read()
                sys.argv = '{}'.format(cmd).split()
                with self.assertRaises(MyExitError):
                    cmd_attr = getattr(cmds, cmd)
                    cmd_attr()
                    self.assertEqual(usage, stdout_mock.getvalue())
