# Test the NodeCache class
import tempfile
import unittest
import hashlib

from vos.md5_cache import MD5Cache
from mock import patch, MagicMock, call, mock_open

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
        self.assertEquals(0x00123, md5_cache.update('somefile', 0x00123, 23,
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
        self.assertEquals(cursor_mock.fetchone.return_value,
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
        with patch('six.moves.builtins.open',
                   mock_open(read_data=b''.join(buffer))):
            self.assertEqual(expect_md5.hexdigest(),
                             cache.compute_md5('fakefile', 4))


def run():
    suite1 = unittest.TestLoader().loadTestsFromTestCase(TestMD5Cache)
    allTests = unittest.TestSuite([suite1])
    return unittest.TextTestRunner(verbosity=2).run(allTests)
