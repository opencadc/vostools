# Test the vos Client class

import os
import unittest
from mock import Mock, MagicMock, patch
import vos
from vos.vos import Client, Connection

class Object(object):
    pass

class TestVos(unittest.TestCase):
    """Test the vos Client class.
    """

    def test_init(self):
        # No parameters uses cert in ~/.ssl giving authenticated / https
        with patch('os.access'):
            client = Client()
        self.assertEqual(client.protocol,"https")
        self.assertTrue(client.conn.vospace_certfile)
        self.assertIsNone(client.conn.vospace_cookie)

        # Supplying an empty string for certfile implies anonymous / http
        client = Client(vospace_certfile='')
        self.assertEqual(client.protocol,"http")
        self.assertIsNone(client.conn.vospace_certfile)
        self.assertIsNone(client.conn.vospace_cookie)

        # Specifying a certfile implies authenticated / https
        with patch('os.access'):
            client = Client(vospace_certfile='/path/to/cert')
        self.assertEqual(client.protocol,"https")
        self.assertTrue(client.conn.vospace_certfile)
        self.assertIsNone(client.conn.vospace_cookie)

        # Specifying a cookie implies authenticated / http
        client = Client(vospace_cookie='a_cookie_string')
        self.assertEqual(client.protocol,"http")
        self.assertIsNone(client.conn.vospace_certfile)
        self.assertTrue(client.conn.vospace_cookie)

        # Specifying both a certfile and cookie implies cookie (auth) / http
        with patch('os.access'):
            client = Client(vospace_certfile='/path/to/cert',
                            vospace_cookie='a_cookie_string')
        self.assertEqual(client.protocol,"http")
        self.assertIsNone(client.conn.vospace_certfile)
        self.assertTrue(client.conn.vospace_cookie)

    def test_getNode(self):
        client = Client()
        uri =  'vos://cadc.nrc.ca!vospace'
        myNode = client.getNode(uri, limit=0, force=False)
        self.assertEqual(uri, myNode.uri)
        self.assertEqual(len(myNode.getNodeList()), 0)

        myNode = client.getNode(uri, limit=10, force=True)
        self.assertEqual(uri, myNode.uri)
        self.assertEqual(len(myNode.getNodeList()), 10)

        myNode = client.getNode(uri, limit=10, force=False)
        self.assertEqual(uri, myNode.uri)
        self.assertEqual(len(myNode.getNodeList()), 10)

    def test_move(self):
        client = Client()
        uri1 =  'notvos://cadc.nrc.ca!vospace/nosuchfile1'
        uri2 =  'notvos://cadc.nrc.ca!vospace/nosuchfile2'

        with patch('vos.vos.VOFile') as mockVOFile:
            mockVOFile.write=Mock()
            mockVOFile.read=Mock()
            client.getTransferError=Mock(return_value=False)
            self.assertTrue(client.move(uri1, uri2))
            client.getTransferError=Mock(return_value=True)
            self.assertFalse(client.move(uri1, uri2))

    def test_delete(self):
        client = Client()
        uri1 =  'notvos://cadc.nrc.ca!vospace/nosuchfile1'

        myObject=Object()
        myObject.close = Mock()
        client.open = Mock(return_value=myObject)
        client.delete(uri1)
        client.open.assert_called_once_with(uri1, mode=os.O_TRUNC)
        myObject.close.assert_called_once_with()




def run():
    suite1 = unittest.TestLoader().loadTestsFromTestCase(TestVos)
    allTests = unittest.TestSuite([suite1])
    return(unittest.TextTestRunner(verbosity=2).run(allTests))

if __name__ == "__main__":
    run()
