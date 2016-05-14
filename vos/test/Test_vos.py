# Test the vos Client class

import unittest

from mock import Mock, patch, MagicMock

from vos.vos import Client, Connection


class Object(object):
    pass


class TestVos(unittest.TestCase):
    """Test the vos Client class.
    """

    def off_test_quota(self):
        """
        Test that a 413 raised by the server gets a reasonable error to the user.
        @return:
        """
        with patch('vos.vos.VOFile') as mockVOFile:
            mockVOFile.open = Mock()
            mockVOFile.read = Mock()
            mockVOFile.write = Mock()
        client = Client()
        client.conn = Mock()
        client.transfer(uri='vos:test', direction="pushToVoSpace")

    def test_init(self):
        # No parameters uses cert in ~/.ssl giving authenticated / https
        with patch('os.access'):
            client = Client()
        self.assertEqual(client.protocol, "https")
        self.assertTrue(client.conn.vospace_certfile)
        self.assertIsNone(client.conn.vospace_token)

        # Supplying an empty string for certfile implies anonymous / http
        client = Client(vospace_certfile='')
        self.assertEqual(client.protocol, "http")
        self.assertIsNone(client.conn.vospace_certfile)
        self.assertIsNone(client.conn.vospace_token)

        # Specifying a certfile implies authenticated / https
        with patch('os.access'):
            client = Client(vospace_certfile='/path/to/cert')
        self.assertEqual(client.protocol, "https")
        self.assertTrue(client.conn.vospace_certfile)
        self.assertIsNone(client.conn.vospace_token)

        # Specifying a token implies authenticated / http
        client = Client(vospace_token='a_token_string')
        self.assertEqual(client.protocol, "http")
        self.assertIsNone(client.conn.vospace_certfile)
        self.assertTrue(client.conn.vospace_token)

        # Specifying both a certfile and token implies token (auth) / http
        with patch('os.access'):
            client = Client(vospace_certfile='/path/to/cert',
                            vospace_token='a_token_string')
        self.assertEqual(client.protocol, "http")
        self.assertIsNone(client.conn.vospace_certfile)
        self.assertTrue(client.conn.vospace_token)

    def test_getNode(self):
        """

        @return:
        """
        uri = "vos://cadc.nrc.ca!vospace/stuff"
        xml = """
        <vos:node xmlns:xs='http://www.w3.org/2001/XMLSchema-instance'
                  xmlns:vos='http://www.ivoa.net/xml/VOSpace/v2.0'
                  xs:type='vos:ContainerNode'
                  uri='{0}'>
            <vos:properties>
                <vos:property uri='ivo://ivoa.net/vospace/core#description'>
                    Stuff
                </vos:property>
            </vos:properties>
            <vos:accepts/>
            <vos:provides/>
            <vos:capabilities/>
            {1}
        </vos:node>
        """

        nodes = """
            <vos:nodes>
                <vos:node uri="vos://cadc.nrc.ca!vospace/mydir/file123" xs:type="vos:DataNode">
                    <vos:properties>
                        <vos:property uri='ivo://ivoa.net/vospace/core#date'>2016-05-10T09:52:13</vos:property>
                    </vos:properties>
                </vos:node>
                <vos:node uri="vos://cadc.nrc.ca!vospace/mydir/file456" xs:type="vos:DataNode">
                    <vos:properties>
                        <vos:property uri='ivo://ivoa.net/vospace/core#date'>2016-05-19T09:52:14</vos:property>
                    </vos:properties>
                </vos:node>
            </vos:nodes>
        """

        mock_vofile = Mock()
        client = Client()
        client.open = Mock(return_value=mock_vofile)

        mock_vofile.read = Mock(return_value=xml.format(uri, ''))
        my_node = client.get_node(uri, limit=0, force=False)
        self.assertEqual(uri, my_node.uri)
        self.assertEqual(len(my_node.node_list), 0)

        mock_vofile.read = Mock(return_value=xml.format(uri, nodes))

        my_node = client.get_node(uri, limit=2, force=True)
        self.assertEqual(uri, my_node.uri)
        self.assertEqual(len(my_node.node_list), 2)

        my_node = client.get_node(uri, limit=2, force=False)
        self.assertEqual(uri, my_node.uri)
        self.assertEqual(len(my_node.node_list), 2)

    def test_move(self):
        mock_resp_403 = Mock(name="mock_resp_303")
        mock_resp_403.status_code = 403

        conn = Connection()
        conn.session.post = Mock(return_value=mock_resp_403)
        client = Client(conn=conn)

        uri1 = 'notvos://cadc.nrc.ca!vospace/nosuchfile1'
        uri2 = 'notvos://cadc.nrc.ca!vospace/nosuchfile2'

        with self.assertRaises(OSError):
            client.move(uri1, uri2)

    def test_delete(self):
        client = Client()
        uri1 = 'notvos://cadc.nrc.ca!vospace/nosuchfile1?limit=0'
        url = 'https://www.canfar.phys.uvic.ca/vospace/nodes/nosuchfile1?limit=0'
        client.conn.session.delete = Mock()
        client.delete(uri1)
        client.conn.session.delete.assert_called_once_with(url)


def run():
    suite1 = unittest.TestLoader().loadTestsFromTestCase(TestVos)
    allTests = unittest.TestSuite([suite1])
    return (unittest.TextTestRunner(verbosity=2).run(allTests))


if __name__ == "__main__":
    run()
