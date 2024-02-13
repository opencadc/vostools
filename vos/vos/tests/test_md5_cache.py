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

# Test the NodeCache class
import tempfile
import unittest
import hashlib

from vos.md5_cache import MD5Cache
from unittest.mock import patch, MagicMock, call, mock_open

# The following is a temporary workaround for Python issue 25532
# (https://bugs.python.org/issue25532)
call.__wrapped__ = None


class TestMD5Cache(unittest.TestCase):
    """Test the TestMD5Cache class.
    """

    @patch('vos.md5_cache.sqlite3.connect')
    def test_sqlite3(self, mock_sqlite3):
        """ tests interactions with sqlite3 db"""

        # test constructor
        sql_conn_mock = MagicMock()
        mock_sqlite3.return_value = sql_conn_mock

        md5_cache = MD5Cache()
        sql_conn_mock.execute.assert_called_once_with(
            'create table if not exists md5_cache (filename text'
            ' PRIMARY KEY NOT NULL , md5 text, st_size int, st_mtime int)')

        # test delete
        sql_conn_mock.reset_mock()
        md5_cache.delete('somefile')
        sql_conn_mock.execute.assert_called_once_with(
            'DELETE from md5_cache WHERE filename = ?', ('somefile',))

        # test update
        sql_conn_mock.reset_mock()
        self.assertEqual(0x00123, md5_cache.update('somefile', 0x00123, 23,
                                                   'Jan 01 2001'))
        call1 = call('DELETE from md5_cache WHERE filename = ?', ('somefile',))
        call2 = call(
            'INSERT INTO md5_cache (filename, md5, st_size, st_mtime) '
            'VALUES ( ?, ?, ?, ?)', ('somefile', 291, 23, 'Jan 01 2001'))
        calls = [call1, call2]
        sql_conn_mock.execute.assert_has_calls(calls)

        # test get
        sql_conn_mock.reset_mock()
        cursor_mock = MagicMock()
        cursor_mock.fetchone.return_value = ['0x0023', '23', 'Jan 01 2000']
        sql_conn_mock.execute.return_value = cursor_mock
        self.assertEqual(cursor_mock.fetchone.return_value,
                         md5_cache.get('somefile'))
        sql_conn_mock.execute.assert_called_once_with(
            'SELECT md5, st_size, st_mtime FROM md5_cache WHERE filename = ? ',
            ('somefile',))

    def test_compute_md5(self):
        file_mock = MagicMock()
        buffer = [b'abcd', b'efgh', b'']
        file_mock.read.side_effect = buffer

        expect_md5 = hashlib.md5()
        for b in buffer:
            expect_md5.update(b)
        cahce_file = tempfile.NamedTemporaryFile()
        cache = MD5Cache(cahce_file.name)
        with patch('builtins.open',
                   mock_open(read_data=b''.join(buffer))):
            self.assertEqual(expect_md5.hexdigest(),
                             cache.compute_md5('fakefile', 4))


def run():
    suite1 = unittest.TestLoader().loadTestsFromTestCase(TestMD5Cache)
    allTests = unittest.TestSuite([suite1])
    return unittest.TextTestRunner(verbosity=2).run(allTests)
