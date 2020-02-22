# Test the vos Client class

import os
import unittest
import requests

from cadcutils import net
from mock import Mock, patch, MagicMock, call
from vos import Connection, Node
from vos.storage_inventory import Client


# The following is a temporary workaround for Python issue 25532
# (https://bugs.python.org/issue25532)
call.__wrapped__ = None

TRANSFER_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\
<vos:transfer xmlns:vos=\"http://www.ivoa.net/xml/VOSpace/v2.0\" " \
               "version=\"2.1\">\
  <vos:target>vos://example.com!vospace/mydir/myfile</vos:target>\
  <vos:direction>pullFromVoSpace</vos:direction>\
  <vos:protocol uri=\"ivo://ivoa.net/vospace/core#httpget\">\
    <vos:securityMethod uri=\"ivo://ivoa.net/sso#anon\" />\
  </vos:protocol>\
  <vos:protocol uri=\"ivo://ivoa.net/vospace/core#httpsget\">\
    <vos:securityMethod uri=\"ivo://ivoa.net/sso#tls-with-certificate\" />\
  </vos:protocol>\
  <vos:keepBytes>true</vos:keepBytes>\
</vos:transfer>"


class Object(object):
    pass


class TestClient(unittest.TestCase):
    """Test the vos Client class.
    """

    TEST_SERVICE_RESOURCE_ID = 'ivo://cadc.nrc.ca/vault'

    def test_init_client(self):
        # No parameters uses cert in ~/.ssl giving authenticated / https
        # create a fake pem file
        certfile = '/tmp/some-cert-file.pem'
        open(certfile, 'w+')
        Client.VOSPACE_CERTFILE = "some-cert-file.pem"
        with patch('os.access'):
            client = Client(TestClient.TEST_SERVICE_RESOURCE_ID,
                            certfile=certfile)
        self.assertTrue(client.conn.subject.certificate)
        self.assertFalse(client.conn.vo_token)

        # Supplying an empty string for certfile implies anonymous / http
        client = Client(TestClient.TEST_SERVICE_RESOURCE_ID, certfile='')
        self.assertTrue(client.conn.subject.anon)
        self.assertFalse(client.conn.vo_token)

        # Specifying a token implies authenticated / http
        client = Client(TestClient.TEST_SERVICE_RESOURCE_ID,
                        token='a_token_string')
        self.assertTrue(client.conn.subject.anon)
        self.assertTrue(client.conn.vo_token)

        # Specifying both a certfile and token implies token (auth) / http
        with patch('os.access'):
            client = Client(TestClient.TEST_SERVICE_RESOURCE_ID,
                            certfile=certfile, token='a_token_string')
        self.assertTrue(client.conn.subject.anon)
        self.assertTrue(client.conn.vo_token)

    patch('vos.EndPoints.nodes', Mock())

    @patch('vos.vos.md5_cache.MD5Cache.compute_md5')
    @patch('__main__.open', MagicMock(), create=True)
    def test_copy(self, computed_md5_mock):
        # the md5sum of the file being copied
        md5sum = 'd41d8cd98f00b204e9800998ecf84eee'
        test_file_content = 'test file 123'
        expected_test_file_size = len(test_file_content)
        # patch the compute_md5 function in vos to return the above value
        computed_md5_mock.return_value = md5sum

        # mock the props of the corresponding node
        props = MagicMock()
        props.get.return_value = md5sum
        # add props to the mocked node
        node = MagicMock(spec=Node)
        node.props = props

        # mock one by one the chain of connection.session.response.headers
        conn = MagicMock(spec=Connection)
        session = MagicMock()
        response = MagicMock()
        mock_post_response = MagicMock(spec=requests.Response)
        headers = {
            'Location': 'https://gohere.com',
            'content-disposition': 'mytestfiledownload.txt',
            'Content-MD5': md5sum
        }
        response.headers = headers
        session.get.return_value = response
        response.status_code = 200
        response.text = TRANSFER_XML
        response.iter_content.return_value = test_file_content.encode()

        mock_post_response.status_code = 303
        mock_post_response.headers = headers
        session.post.return_value = mock_post_response

        conn.session = session

        test_client = Client(TestClient.TEST_SERVICE_RESOURCE_ID, conn=conn)

        # Mock out the endpoints to avoid a true Registry service call.
        nodes_url = 'https://ws-cadc.canfar.net/minoc/nodes'
        endpoints_mock = Mock()
        endpoints_mock.nodes = nodes_url
        test_client.get_endpoints = Mock(return_value=endpoints_mock)

        mock_metadata_response = MagicMock()
        mock_metadata_response.headers = {
            'Content-Length': 88,
            'Content-Type': 'application/fits',
            'Content-MD5': 'd41d8cd98f00b204e9800998ecf8427e'
        }

        mock_ws_client = Mock(spec=net.BaseWsClient)
        test_client._get_ws_client = Mock(return_value=mock_ws_client)
        mock_ws_client.head.return_value = mock_metadata_response

        # time to test...
        storageLocation = 'cadc:TEST/foo'
        osLocation = '/tmp/foo'
        if os.path.isfile(osLocation):
            os.remove(osLocation)
        self.assertFalse(os.path.exists(osLocation))
        # copy from Storage Inventory, returns file size
        actual_file_size = test_client.copy(storageLocation, osLocation)
        self.assertTrue(os.path.isfile(osLocation))
        self.assertEqual(expected_test_file_size, actual_file_size,
                         'incorrect file size of copied file')
        computed_md5_mock.assert_called_once_with(osLocation)
        self.assertTrue(os.path.isfile(osLocation))

        # repeat - local file and vospace file are now the same -> only
        # get_node is called to get the md5 of remote file
        computed_md5_mock.reset_mock()
        props.reset_mock()
        props.get.return_value = md5sum
        actual_file_size = test_client.copy(storageLocation, osLocation)
        self.assertEqual(expected_test_file_size, actual_file_size,
                         'incorrect file size of copied file')
        computed_md5_mock.assert_called_once_with(osLocation)

        # change the content of local files to trigger a new copy
        # computed_md5_mock.reset_mock()
        computed_md5_mock.side_effect = [md5sum]
        test_client.copy(storageLocation, osLocation)
        computed_md5_mock.assert_called_with(osLocation)

        # copy to vospace when md5 sums are the same -> only update occurs
        computed_md5_mock.reset_mock()
        computed_md5_mock.side_effect = ['d41d8cd98f00b204e9800998ecf8427e',
                                         md5sum]
        computed_md5_mock.return_value = md5sum
        test_client.copy(osLocation, storageLocation)

        # make md5 different
        computed_md5_mock.reset_mock()
        computed_md5_mock.side_effect = ['d41d8cd98f00b204e9800998ecf8427e',
                                         md5sum]
        props.reset_mock()
        props.get.side_effect = ['d00223344', 88, md5sum, 'text/plain']
        test_client.copy(osLocation, storageLocation)
        computed_md5_mock.assert_called_once_with(osLocation)

        # copy 0 size file -> delete and create on client but no bytes
        # transferred
        computed_md5_mock.reset_mock()
        computed_md5_mock.side_effect = ['d41d8cd98f00b204e9800998ecf8427e',
                                         md5sum]
        props.get.side_effect = [md5sum]
        test_client.copy(osLocation, storageLocation)

        # copy new 0 size file -> reate on client but no bytes transferred
        computed_md5_mock.reset_mock()
        computed_md5_mock.side_effect = ['d41d8cd98f00b204e9800998ecf8427e',
                                         md5sum]
        props.get.side_effect = [None]
        test_client.copy(osLocation, storageLocation)

        # error tests - md5sum mismatch
        headers['Content-MD5'] = '0000bad0000'
        with self.assertRaises(IOError):
            test_client.copy(storageLocation, osLocation)

        headers['Content-MD5'] = md5sum
        computed_md5_mock.reset_mock()
        computed_md5_mock.side_effect = None
        computed_md5_mock.return_value = '0000bad0000'
        with self.assertRaises(IOError):
            test_client.copy(osLocation, storageLocation)

        # requests just the headers
        props.get.side_effect = [None]
        computed_md5_mock.reset_mock()
        computed_md5_mock.return_value = md5sum
        test_client.copy(storageLocation, osLocation, head=True)

    @patch('vos.vos.net.ws.WsCapabilities.get_access_url',
           Mock(return_value='https://www.cadc-ccda.hia-iha.nrc-cnrc.gc.ca/'
                             'minoc/files'))
    def test_delete(self):
        conn = MagicMock(spec=Connection)
        certfile = '/tmp/SomeCert.pem'
        open(certfile, 'w+')
        with patch('os.access'):
            client = Client(TestClient.TEST_SERVICE_RESOURCE_ID,
                            certfile=certfile, conn=conn)
        uri1 = 'iris:nosuchfile1'
        url = 'https://www.cadc-ccda.hia-iha.nrc-cnrc.gc.ca/minoc/files/' \
              'iris:nosuchfile1'
        client.conn.session.delete = Mock()
        client.delete(uri1)
        client.conn.session.delete.assert_called_once_with(url)

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

        test_client = Client(TestClient.TEST_SERVICE_RESOURCE_ID, conn=conn)

        # use the mocked connection instead of the real one
        # test_client.conn = conn

        # job successfully completed
        vofile.read.side_effect = [b'QUEUED', b'COMPLETED']
        self.assertFalse(test_client.get_transfer_error(
            vospace_url + '/results/transferDetails', 'vos://vospace'))
        session.get.assert_called_once_with(vospace_url + '/phase',
                                            allow_redirects=False)

        # job suspended
        session.reset_mock()
        session.get.side_effect = [Mock(content=b'COMPLETED')]
        vofile.read.side_effect = [b'QUEUED', b'SUSPENDED']
        with self.assertRaises(OSError):
            test_client.get_transfer_error(
                vospace_url + '/results/transferDetails', 'vos://vospace')
        # check arguments for session.get calls
        self.assertEquals(
            [call(vospace_url + '/phase', allow_redirects=False)],
            session.get.call_args_list)

        # job encountered an internal error
        session.reset_mock()
        vofile.read.side_effect = Mock(side_effect=[b'QUEUED', b'ERROR'])
        session.get.side_effect = [Mock(content=b'COMPLETED'),
                                   Mock(text='InternalFault')]
        with self.assertRaises(OSError):
            test_client.get_transfer_error(
                vospace_url + '/results/transferDetails', 'vos://vospace')
        self.assertEquals([call(vospace_url + '/phase', allow_redirects=False),
                           call(vospace_url + '/error')],
                          session.get.call_args_list)

        # job encountered an unsupported link error
        session.reset_mock()
        link_file = 'testlink.fits'
        vofile.read.side_effect = Mock(side_effect=[b'QUEUED', b'ERROR'])
        session.get.side_effect = [Mock(content=b'COMPLETED'),
                                   Mock(
                                       text="Unsupported link target: " +
                                            link_file)]
        self.assertEquals(link_file, test_client.get_transfer_error(
            vospace_url + '/results/transferDetails', 'vos://vospace'))
        self.assertEquals([call(vospace_url + '/phase', allow_redirects=False),
                           call(vospace_url + '/error')],
                          session.get.call_args_list)
