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
import unittest

from io import StringIO
from unittest.mock import Mock, patch, MagicMock, call
from vos import commands

# the following is a temporary workaround for python issue
# 25532 (https://bugs.python.org/issue25532)
call.__wrapped__ = None


class MyExitError(Exception):

    def __init__(self):
        self.message = "MyExitError"


class TestVls(unittest.TestCase):
    @patch('sys.exit', Mock(side_effect=[MyExitError]))
    @patch('vos.vos.Client')
    def test_vls(self, vos_client_mock):
        # vls command with sort == None, order == None
        mock_node1 = MagicMock(type='vos:DataNode')
        mock_node1.name = 'node1'
        mock_node1.props = {'length': 100, 'date': 50000}
        mock_node1.isdir.return_value = False
        mock_node1.get_info.return_value = ''
        mock_node1.islink.return_value = False

        mock_node2 = MagicMock(type='vos:DataNode')
        mock_node2.name = 'node2'
        mock_node2.props = {'length': 30, 'date': 70000}
        mock_node2.isdir.return_value = False
        mock_node2.get_info.return_value = ''
        mock_node2.islink.return_value = False

        mock_node3_link = MagicMock(type='vos:DataNode')
        mock_node3_link.name = 'node3_link'
        mock_node3_link.props = {'length': 60, 'date': 20000}
        mock_node3_link.isdir.return_value = False
        mock_node3_link.get_info.return_value = ''
        mock_node3_link.islink.return_value = True

        mock_node3 = MagicMock(type='vos:DataNode')
        mock_node3.name = 'node3'
        mock_node3.props = {'length': 60, 'date': 20000}
        mock_node3.isdir.return_value = False
        mock_node3.get_info.return_value = ''
        mock_node3.islink.return_value = False

        vos_client_mock.return_value.glob.return_value = \
            ['target2', 'target3_link', 'target1']

        # vls command with sort == None (i.e. sort by node name), order == None
        out = 'node1\nnode2\nnode3\n'
        with patch('sys.stdout', new_callable=StringIO) as stdout_mock:
            vos_client_mock.return_value.get_node = \
                MagicMock(side_effect=[mock_node2, mock_node3, mock_node1])
            sys.argv = ['vls', 'vos:/CADCRegtest1']
            cmd_attr = getattr(commands, 'vls')
            cmd_attr()
            assert out == stdout_mock.getvalue()

        # vls command with sort == size (i.e. not None), order == None
        out = 'node1\nnode3\nnode2\n'
        with patch('sys.stdout', new_callable=StringIO) as stdout_mock:
            vos_client_mock.return_value.get_node = \
                MagicMock(side_effect=[mock_node2, mock_node3, mock_node1])
            sys.argv = ['vls', '-S', 'vos:/CADCRegtest1']
            cmd_attr = getattr(commands, 'vls')
            cmd_attr()
            assert out == stdout_mock.getvalue()

        # vls command with sort == size, order == reverse
        out = 'node2\nnode3\nnode1\n'
        with patch('sys.stdout', new_callable=StringIO) as stdout_mock:
            vos_client_mock.return_value.get_node = \
                MagicMock(side_effect=[mock_node2, mock_node3, mock_node1])
            sys.argv = ['vls', '-S', '-r', 'vos:/CADCRegtest1']
            cmd_attr = getattr(commands, 'vls')
            cmd_attr()
            assert out == stdout_mock.getvalue()

        # vls command with sort == None, order == reverse
        out = 'node3\nnode2\nnode1\n'
        with patch('sys.stdout', new_callable=StringIO) as stdout_mock:
            vos_client_mock.return_value.get_node = \
                MagicMock(side_effect=[mock_node2, mock_node3, mock_node1])
            sys.argv = ['vls', '-r', 'vos:/CADCRegtest1']
            cmd_attr = getattr(commands, 'vls')
            cmd_attr()
            assert out == stdout_mock.getvalue()
