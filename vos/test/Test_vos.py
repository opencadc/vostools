# from builtins import object
# Test the vos Client class

import os
import unittest
from xml.etree import ElementTree
from mock import Mock, patch, MagicMock, call
from vos.vos import Client, Connection, Node, VOFile

NODE_XML = """
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


class Object(object):
    pass


class TestClient(unittest.TestCase):
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

    def test_init_client(self):
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

    def test_open(self):
        # Invalid mode raises OSError
        with self.assertRaises(OSError):
            client = Client()
            client.open('vos://foo/bar', mode=666)

        with self.assertRaises(OSError):
            client = Client()
            client.get_node_url = Mock(return_value=None)
            client.open(None, url=None)

        conn = Connection()
        mock_vofile = VOFile(['http://foo.com/bar'], conn, 'GET')
        client = Client()
        client.get_node_url = Mock(return_value=mock_vofile)
        vofile = client.open(None, url=None)
        self.assertEquals(vofile.url.URLs[0], 'http://foo.com/bar')

    def test_add_props(self):
        old_node = Node(NODE_XML)
        new_node = Node(NODE_XML)
        new_node.props['quota'] = '1000'
        new_node.create = Mock(return_value=new_node.node)

        data = str(new_node)
        headers = {'size': len(data)}

        client = Client()
        client.get_node = Mock(return_value=old_node)
        client.get_node_url = Mock(return_value='http://foo.com/bar')
        client.conn = Mock()

        with patch('vos.Client', client) as mock:
            mock.add_props(new_node)
            mock.conn.session.post.assert_called_with('http://foo.com/bar',
                                                      headers=headers, data=data)

    def test_create(self):
        node = Node(NODE_XML)

        client = Client()
        client.get_node = Mock(return_value=node)

        data = str(node)
        headers = {'size': len(data)}

        client = Client()
        client.get_node_url = Mock(return_value='http://foo.com/bar')
        client.conn = Mock()

        with patch('vos.Client', client) as mock:
            result = mock.create(node)
            self.assertTrue(result)
            mock.conn.session.put.assert_called_with('http://foo.com/bar',
                                                     headers=headers, data=data)

    def test_update(self):
        node = Node(NODE_XML)

        resp = Mock()
        resp.headers.get = Mock(return_value="http://www.canfar.phys.uvic.ca/vospace")

        client = Client()
        client.get_node_url = Mock(return_value='http://www.canfar.phys.uvic.ca/vospace')
        client.conn = Mock()
        client.conn.session.post = Mock(return_value=resp)
        client.get_transfer_error = Mock()

        data = str(node)
        property_url = 'https://{0}'.format(node.endpoints.properties)

        with patch('vos.Client', client) as mock:
            result = mock.update(node, False)
            self.assertEqual(result, 0)
            mock.conn.session.post.assert_called_with('http://www.canfar.phys.uvic.ca/vospace',
                                                      data=data, allow_redirects=False)

        call1 = call(property_url, allow_redirects=False, data=data,
                     headers={'Content-type': 'text/xml'})
        call2 = call('http://www.canfar.phys.uvic.ca/vospace/phase', allow_redirects=False, data="PHASE=RUN",
                     headers={'Content-type': "text/text"})
        calls = [call1, call2]

        with patch('vos.Client', client) as mock:
            result = mock.update(node, True)
            self.assertEqual(result, 0)
            mock.conn.session.post.assert_has_calls(calls)

    def test_getNode(self):
        """

        @return:
        """
        uri = "vos://cadc.nrc.ca!vospace/stuff"

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

        mock_vofile.read = Mock(return_value=NODE_XML.format(uri, ''))
        my_node = client.get_node(uri, limit=0, force=False)
        self.assertEqual(uri, my_node.uri)
        self.assertEqual(len(my_node.node_list), 0)

        mock_vofile.read = Mock(return_value=NODE_XML.format(uri, nodes))

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


class TestNode(unittest.TestCase):
    """Test the vos Node class.
    """

    def test_compute_md5(self):
        from vos import vos
        # mock_file = MagicMock(spec=file, wraps=StringIO('a'))
        # foo = mock_file.open()
        # self.assertEqual(foo.read, 'a')
        #     # hash = vos.compute_md5('/dev/null')
        # # self.assertEqual(hash, '098f6bcd4621d373cade4e832627b4f6')

        # import mock
        # my_mock = mock.MagicMock()
        # with mock.patch('__builtin__.open', my_mock):
        #     manager = my_mock.return_value.__enter__.return_value
        #     manager.read.return_value = 'some data'
        # with open('foo') as h:
        #     data = h.read()
        # print data

        # with mock.patch('__builtin__.open') as my_mock:
        #     my_mock.return_value.__enter__ = lambda s: s
        #     my_mock.return_value.__exit__ = mock.Mock()
        #     my_mock.return_value.read.return_value = 'some data'
        # with open('foo') as h:
        #     data = h.read()

        # mocked_open = unittest.mock.mock_open(read_data='foo')
        # with unittest.mock.patch('vos.open', mocked_open, create=True):
        #     hash = vos.compute_md5('foo')
        #     self.assertEqual(hash, '098f6bcd4621d373cade4e832627b4f6')

    def test_node_eq(self):
        # None node raises LoookupError
        with self.assertRaises(LookupError):
            node = Node(None)

        # Node equality
        node1 = Node(ElementTree.Element(Node.NODE))
        node2 = Node(ElementTree.Element(Node.NODE))
        self.assertNotEqual(node1, 'foo')
        self.assertEquals(node1, node2)

        node1.props = {'foo': 'bar'}
        self.assertNotEqual(node1, node2)
        node2.props = {'foo': 'bar'}
        self.assertEquals(node1, node2)

    def test_node_set_property(self):
        node = Node(NODE_XML)
        properties = node.node.find(Node.PROPERTIES)
        property_list = properties.findall(Node.PROPERTY)
        self.assertEqual(len(property_list), 1)

        node.set_property('key', 'value')
        properties = node.node.find(Node.PROPERTIES)
        property_list = properties.findall(Node.PROPERTY)
        self.assertEqual(len(property_list), 2)

        found = False
        for prop in property_list:
            uri = prop.get('uri')
            if uri.endswith('key'):
                found = True
        self.assertTrue(found)

    def test_chwgrp(self):
        node = Node(NODE_XML)
        self.assertEquals('', node.groupwrite)

        node.chwgrp('foo')
        self.assertEquals('foo', node.groupwrite)

    def test_chrgrp(self):
        node = Node(NODE_XML)
        self.assertEquals('', node.groupread)

        node.chrgrp('foo')
        self.assertEquals('foo', node.groupread)

    def test_change_prop(self):
        # Add a new property
        node = Node(NODE_XML)
        quota = TestNode.get_node_property(node, 'quota')
        self.assertIsNone(quota)

        node.change_prop('quota', '1000')
        quota = TestNode.get_node_property(node, 'quota')
        self.assertIsNotNone(quota)
        self.assertEqual('1000', quota.text)

        # Change a current property
        node.change_prop('quota', '2000')
        quota = TestNode.get_node_property(node, 'quota')
        self.assertIsNotNone(quota)
        self.assertEqual('2000', quota.text)

        # Delete a property
        #node.change_prop('quota', None)
        #quota = TestVos.get_node_property(node, 'quota')
        #self.assertIsNone(quota)

    def test_clear_properties(self):
        # Add a new property
        node = Node(NODE_XML)
        node.set_property('quota', '1000')
        properties = node.node.find(Node.PROPERTIES)
        property_list = properties.findall(Node.PROPERTY)
        self.assertTrue(len(property_list) >= 1)

        # Clear the property
        node.clear_properties()
        properties = node.node.find(Node.PROPERTIES)
        property_list = properties.findall(Node.PROPERTY)
        self.assertTrue(len(property_list) == 0)

    @staticmethod
    def get_node_property(node, key):
        properties = node.node.find(Node.PROPERTIES)
        if properties is None:
            return None
        property_list = properties.findall(Node.PROPERTY)
        if len(property_list) == 0:
            return None

        for prop in property_list:
            name = Node.get_prop_name(prop.get('uri'))
            if name == key:
                return prop
        return None


class TestVOFile(unittest.TestCase):
    """Test the vos VOFile class.
    """

    def test_seek(self):
        my_mock = MagicMock()
        with patch('vos.VOFile.open', my_mock):
            url_list = ['http://foo.com']
            conn = Connection()
            method = 'GET'
            vofile = VOFile(url_list, conn, method, size=25)

            vofile.seek(10, os.SEEK_CUR)
            self.assertEquals(10, vofile._fpos)
            vofile.seek(5, os.SEEK_SET)
            self.assertEquals(5, vofile._fpos)
            vofile.seek(10, os.SEEK_END)
            self.assertEquals(15, vofile._fpos)


def run():
    suite1 = unittest.TestLoader().loadTestsFromTestCase(TestClient)
    all_tests = unittest.TestSuite([suite1])
    return unittest.TextTestRunner(verbosity=2).run(all_tests)


if __name__ == "__main__":
    run()
