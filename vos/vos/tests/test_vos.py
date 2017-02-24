# Test the vos Client class
 
import os
import unittest
import requests
from xml.etree import ElementTree
from mock import Mock, patch, MagicMock, call, mock_open
from vos import Client, Connection, Node, VOFile

NODE_XML = """
        <vos:node xmlns:xs='http://www.w3.org/2001/XMLSchema-instance'
                  xmlns:vos='http://www.ivoa.net/xml/VOSpace/v2.0'
                  xs:type='vos:ContainerNode'
                  uri='vos://foo.com!vospace/bar'>
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

    def off_quota(self):
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
        Client.VOSPACE_CERTFILE = "some-cert-file.pem"
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

    @patch('vos.vos.RetrySession.get', Mock(return_value=Mock(spec=requests.Response, status_code=404)))
    def test_open(self):
        # Invalid mode raises OSError
        with self.assertRaises(OSError):
            client = Client()
            client.open('vos://foo/bar', mode=-1)

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
        
        
    @patch('vos.vos.Node.get_info', Mock(return_value={'name':'aa'}))
    def test_get_info_list(self):
        # list tuples of a LinkNode
        mock_node = MagicMock(type='vos:DataNode')
        mock_node.return_value = mock_node
        mock_node.name = 'testnode'
        mock_node.get_info.return_value = {'name':'aa'}
        mock_link_node = Mock(type='vos:LinkNode')
        mock_link_node.target = 'vos:/somefile'
        client = Client()
        client.get_node = MagicMock(side_effect=[mock_link_node, mock_node])
        self.assertEquals({'testnode':mock_node.get_info.return_value}.items(), 
                          client.get_info_list('vos:/somenode'))
        
                

    def test_nodetype(self):
        mock_node = MagicMock(id=333)
        mock_node.type = 'vos:ContainerNode'
        client = Client()
        client.get_node = Mock(return_value=mock_node)
        self.assertEquals('vos:ContainerNode', client._node_type('vos:/somenode'))
        self.assertTrue(client.isdir('vos:/somenode'))
        
        mock_node.type = 'vos:DataNode'
        self.assertEquals('vos:DataNode', client._node_type('vos:/somenode'))
        self.assertTrue(client.isfile('vos:/somenode'))
        
        # through a link
        mock_node.type = 'vos:ContainerNode'
        mock_link_node = Mock(type='vos:LinkNode')
        mock_link_node.target = 'vos:/somefile'
        client.get_node = Mock(side_effect=
                [mock_link_node, mock_node, mock_link_node, mock_node])
        self.assertEquals('vos:ContainerNode', client._node_type('vos:/somenode'))
        self.assertTrue(client.isdir('vos:/somenode'))
        
        # through an external link - not sure why the type is DataNode in this case???
        mock_link_node.target = '/somefile'
        client.get_node = Mock(side_effect=[mock_link_node, mock_link_node])
        self.assertEquals('vos:DataNode', client._node_type('vos:/somenode'))
        self.assertTrue(client.isfile('vos:/somenode'))
        

    def test_glob(self):
        # test the pattern matches in directories and file names
        
        # simple test for listing of directory, no wild cards
        
        # NOTE: Mock class also has a 'name' attribute so we cannot 
        # instantiate a mock node with Mock(name='blah'). There are
        # two other wasy to do it as seen below
        mock_node = MagicMock(type='vos:ContainerNode')
        mock_node.configure_mock(name='anode')
        client = Client()
        client.get_node = Mock(return_value=mock_node)
        self.assertEquals(['vos:/anode/'], client.glob('vos:/anode/'))
        
        # simple test for file listing of file
        mock_node = MagicMock(type='vos:DataNode')
        mock_node.configure_mock(name='afile')
        client = Client()
        client.get_node = Mock(return_value=mock_node)
        self.assertEquals(['vos:/afile'], client.glob('vos:/afile'))
        
        # create a mock directory structure on the form
        # /anode/abc /anode/def - > anode/a* should return 
        # /anode/adc
        
        mock_node = MagicMock(type='vos:ContainerNode')
        mock_node.configure_mock(name='anode')
        mock_child_node1 = Mock(type='vos:DataNode')
        mock_child_node1.name = 'abc'
        mock_child_node2 = Mock(type='vos:DataNode')
        mock_child_node2.name = 'def'
        # because we use wild characters in the root node,
        # we need to create a corresponding node for the base node
        mock_base_node = Mock(type='vos:ContainerNode')
        mock_base_node.name = 'vos:'
        mock_base_node.node_list = [mock_node]
        mock_node.node_list = [mock_base_node, mock_child_node1, mock_child_node2]
        client = Client()
        client.get_node = Mock(side_effect=[mock_node, mock_base_node, mock_node])
        self.assertEquals(['vos:/anode/abc'], client.glob('vos:/anode/a*'))
        self.assertEquals(['vos:/anode/abc'], client.glob('vos:/*node/abc'))
        
        # test nodes:
        # /anode/.test1 /bnode/sometests /bnode/blah
        # /[a,c]node/*test* should return /bnode/somtests (.test1 is filtered
        # out as a special file)

       
        mock_child_node1 = Mock(type='vos:DataNode')
        mock_child_node1.name = '.test1'
        mock_node1 = MagicMock(type='vos:ContainerNode')
        mock_node1.configure_mock(name='anode')
        mock_node1.node_list = [mock_child_node1]
        
        mock_child_node2 = Mock(type='vos:DataNode')
        mock_child_node2.name = 'sometests'
        mock_child_node3 = Mock(type='vos:DataNode')
        mock_child_node3.name = 'blah'
        mock_node2 = MagicMock(type='vos:ContainerNode')
        mock_node2.configure_mock(name='bnode')
        mock_node2.node_list = [mock_child_node2, mock_child_node3]
        
        # because we use wild characters in the root node,
        # we need to create a corresponding node for the base node
        mock_base_node = Mock(type='vos:DataNode')
        mock_base_node.name = 'vos:'
        mock_base_node.node_list = [mock_node1, mock_node2]
        client = Client()
        client.get_node = Mock(side_effect=[mock_base_node, mock_node1, mock_node2])
        self.assertEquals(['vos:/bnode/sometests'], 
                          client.glob('vos:/[a,b]node/*test*'))
        
        
    @patch('vos.vos.compute_md5')
    @patch('__main__.open', MagicMock(), create=True)
    def test_copy(self, computed_md5_mock):
        # the md5sum of the file being copied
        md5sum = 'd41d8cd98f00b204e9800998ecf84eee'
        # patch the compute_md5 function in vos to return the above value
        computed_md5_mock.return_value = md5sum
        
        #mock the props of the corresponding node        
        props = MagicMock()
        props.get.return_value = md5sum
        #add props to the mocked node
        node = MagicMock(spec=Node)
        node.props = props

        
        # mock one by one the chain of connection.session.response.headers
        conn = MagicMock(spec=Connection)
        session = MagicMock()
        response = MagicMock()
        headers = MagicMock()
        headers.get.return_value = md5sum
        response.headers = headers
        session.get.return_value = response
        conn.session = session
        
        test_client = Client()
        # use the mocked connection instead of the real one
        test_client.conn = conn
        get_node_url_mock = Mock(return_value=['http://cadc.ca/test', 'http://cadc.ca/test'])
        test_client.get_node_url = get_node_url_mock
            
        #patch Client.get_node to return our mocked node
        get_node_mock = Mock(return_value=node)
        test_client.get_node = get_node_mock
        
        # time to test...
        vospaceLocation = 'vos://test/foo'
        osLocation = '/tmp/foo'
        # copy from vospace
        test_client.copy(vospaceLocation, osLocation)
        get_node_url_mock.assert_called_once_with(vospaceLocation, method='GET', 
                                              cutout=None, view='data')
        computed_md5_mock.assert_called_once_with(osLocation)
        get_node_mock.assert_called_once_with(vospaceLocation)
        
        # copy to vospace
        get_node_url_mock.reset_mock()
        computed_md5_mock.reset_mock()
        test_client.copy(osLocation, vospaceLocation)
        get_node_url_mock.assert_called_once_with(vospaceLocation, 'PUT')
        computed_md5_mock.assert_called_once_with(osLocation)

        # error tests - md5sum mismatch
        computed_md5_mock.return_value = '000bad000'
        with self.assertRaises(OSError):
            test_client.copy(vospaceLocation, osLocation)
        
        with self.assertRaises(OSError):
            test_client.copy(osLocation, vospaceLocation)
    
    # patch sleep to stop the test from sleeping and slowing down execution
    @patch('vos.vos.time.sleep', MagicMock(), create=True)
    @patch('vos.vos.VOFile')
    def test_transfer_error(self, mock_vofile):
        vofile = MagicMock()
        mock_vofile.return_value = vofile

        vospace_url = 'https://somevospace.server/vospace'
        session = Mock()
        session.get.side_effect = [Mock(content='COMPLETED')] 
        conn = Mock(spec=Connection)
        conn.session = session

        test_client = Client()

        # use the mocked connection instead of the real one
        test_client.conn = conn
        
        # job successfully completed
        vofile.read.side_effect = ['QUEUED', 'COMPLETED']
        self.assertFalse(test_client.get_transfer_error(
                vospace_url +'/results/transferDetails', 'vos://vospace'))
        session.get.assert_called_once_with(vospace_url + '/phase', allow_redirects=False)
        
        # job suspended
        session.reset_mock()
        session.get.side_effect = [Mock(content='COMPLETED')]
        vofile.read.side_effect = ['QUEUED', 'SUSPENDED']
        with self.assertRaises(OSError):
            test_client.get_transfer_error(
                vospace_url +'/results/transferDetails', 'vos://vospace')
        #check arguments for session.get calls
        self.assertEquals([call(vospace_url + '/phase', allow_redirects=False)], 
                          session.get.call_args_list )

        # job encountered an internal error
        session.reset_mock()
        vofile.read.side_effect = Mock(side_effect=['QUEUED', 'ERROR'])
        session.get.side_effect = [Mock(content='COMPLETED'), Mock(content='InternalFault')]
        with self.assertRaises(OSError):
            test_client.get_transfer_error(
                vospace_url +'/results/transferDetails', 'vos://vospace')
        self.assertEquals([call(vospace_url + '/phase', allow_redirects=False),
                           call(vospace_url + '/error')], 
                          session.get.call_args_list )

        # job encountered an unsupported link error
        session.reset_mock()
        link_file = 'testlink.fits'
        vofile.read.side_effect = Mock(side_effect=['QUEUED', 'ERROR'])
        session.get.side_effect = [Mock(content='COMPLETED'), 
                                   Mock(content="Unsupported link target: " + link_file)]
        self.assertEquals(link_file, test_client.get_transfer_error(
            vospace_url +'/results/transferDetails', 'vos://vospace'))
        self.assertEquals([call(vospace_url + '/phase', allow_redirects=False),
                   call(vospace_url + '/error')], 
                  session.get.call_args_list )
          

    def test_add_props(self):
        old_node = Node(NODE_XML)
        new_node = Node(NODE_XML)
        new_node.props['quota'] = '1000'
        new_node.create = Mock(return_value=new_node.node)

        data = str(new_node)
        headers = {'size': str(len(data))}

        client = Client()
        client.get_node = Mock(return_value=old_node)
        client.get_node_url = Mock(return_value='http://foo.com/bar')
        client.conn = Mock()

        with patch('vos.Client', client) as mock:
            mock.add_props(new_node)
            mock.conn.session.post.assert_called_with('http://foo.com/bar',
                                                      headers=headers, data=data)

    def test_create(self):
        uri = 'vos://foo.com!vospace/bar'
        client = Client()
        node = Node(client.fix_uri(uri))
        node2 = Node(str(node))
        self.assertEquals(node, node2)
        data = str(node)
        headers = {'size': str(len(data))}

        client = Client()
        client.protocol = 'https'
        client.get_node_url = Mock(return_value='http://foo.com/bar')
        session_mock = MagicMock()
        client.conn = Mock()
        client.conn.session = session_mock
        session_mock.put.return_value = Mock(content=str(node))

        result = client.create(uri)
        print(node)
        print(result)
        self.assertEquals(node, result)
        session_mock.put.assert_called_with('https://www.canfar.phys.uvic.ca/vospace/nodes/bar',
                                                 headers=headers, data=data)

    def test_update(self):
        node = Node(NODE_XML)

        resp = Mock()
        resp.headers.get = Mock(return_value="https://www.canfar.phys.uvic.ca/vospace")

        client = Client()
        client.get_node_url = Mock(return_value='https://www.canfar.phys.uvic.ca/vospace')
        client.conn = Mock()
        client.conn.session.post = Mock(return_value=resp)
        client.get_transfer_error = Mock()
        client.protocol = 'https'

        data = str(node)
        property_url = 'https://{0}'.format(node.endpoints.properties)

        with patch('vos.Client', client) as mock:
            result = mock.update(node, False)
            self.assertEqual(result, 0)
            mock.conn.session.post.assert_called_with('https://www.canfar.phys.uvic.ca/vospace',
                                                      data=data, allow_redirects=False)

        call1 = call(property_url, allow_redirects=False, data=data,
                     headers={'Content-type': 'text/xml'})
        call2 = call('https://www.canfar.phys.uvic.ca/vospace/phase', allow_redirects=False, data="PHASE=RUN",
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
        uri = "vos://foo.com!vospace/bar"

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
        with patch('os.access'):
            client = Client(vospace_certfile="SomeFile")
        uri1 = 'notvos://cadc.nrc.ca!vospace/nosuchfile1?limit=0'
        url = 'https://www.canfar.phys.uvic.ca/vospace/nodes/nosuchfile1?limit=0'
        client.conn.session.delete = Mock()
        client.delete(uri1)
        client.conn.session.delete.assert_called_once_with(url)


class TestNode(unittest.TestCase):
    """Test the vos Node class.
    """

    def test_compute_md5(self):
        pass
        #from vos import vos
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


