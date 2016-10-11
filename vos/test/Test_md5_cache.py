# Test the NodeCache class

import unittest
import hashlib

from vos.md5_cache import MD5_Cache
from mock import patch, MagicMock, Mock, call

class TestMD5Cache(unittest.TestCase):
    """Test the TestMD5Cache class.
    """

    @patch('vos.md5_cache.sqlite3.connect')
    def test_sqlite3(self, mock_sqlite3):
        """ tests interactions with sqlite3 db"""
        
        # test constructor
        sql_conn_mock = MagicMock()
        mock_sqlite3.return_value = sql_conn_mock
        
        md5_cache = MD5_Cache()
        sql_conn_mock.execute.assert_called_once_with(
            'create table if not exists md5_cache (fname text' +\
            ' PRIMARY KEY NOT NULL , md5 text, st_size int, st_mtime int)')

        
        # test delete
        sql_conn_mock.reset_mock()
        md5_cache.delete('somefile')
        sql_conn_mock.execute.assert_called_once_with(
           'DELETE from md5_cache WHERE fname = ?', ('somefile',))
        
        # test update
        sql_conn_mock.reset_mock()
        self.assertEquals(0x00123, md5_cache.update('somefile', 0x00123, 23, 'Jan 01 2001'))
        call1 = call('DELETE from md5_cache WHERE fname = ?', ('somefile',))
        call2 = call('INSERT INTO md5_cache (fname, md5, st_size, st_mtime) VALUES ( ?, ?, ?, ?)', ('somefile', 291, 23, 'Jan 01 2001'))
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
           'SELECT md5, st_size, st_mtime FROM md5_cache WHERE fname = ? ', ('somefile',))


    @patch('__builtin__.open')
    def test_computeMD5(self, mock_file):
        md5_cache = MD5_Cache()
        file_mock = MagicMock()
        buffer = ['abcd', 'efgh', '']
        file_mock.read.side_effect = buffer
        mock_file.return_value = file_mock
        
        expect_md5 = hashlib.md5()
        for b in buffer:
            expect_md5.update(b)
        
        md5_cache = MD5_Cache()
        self.assertEquals(expect_md5.hexdigest(), md5_cache.computeMD5('fakefile', 4))

def run():
    suite1 = unittest.TestLoader().loadTestsFromTestCase(TestMD5Cache)
    allTests = unittest.TestSuite([suite1])
    return unittest.TextTestRunner(verbosity=2).run(allTests)

if __name__ == "__main__":
    run()
