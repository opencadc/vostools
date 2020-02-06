# Test the vos Client class

import os
import unittest
import pytest
import requests
from xml.etree import ElementTree
from mock import Mock, patch, MagicMock, call
from vos.storage_inventory import Client
from vos import Connection, Node, VOFile
from vos import vos as vos
from six.moves.urllib.parse import urlparse
from six.moves import urllib
import warnings


# The following is a temporary workaround for Python issue 25532
# (https://bugs.python.org/issue25532)
call.__wrapped__ = None

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

    DATA_SERVICE_RESOURCE_ID = 'ivo://cadc.nrc.ca/data'

    def test_init_client(self):
        # No parameters uses cert in ~/.ssl giving authenticated / https
        # create a fake pem file
        certfile = '/tmp/some-cert-file.pem'
        open(certfile, 'w+')
        Client.VOSPACE_CERTFILE = "some-cert-file.pem"
        with patch('os.access'):
            client = Client(TestClient.DATA_SERVICE_RESOURCE_ID, certfile=certfile)
        self.assertTrue(client.conn.subject.certificate)
        self.assertFalse(client.conn.vo_token)

        # Supplying an empty string for certfile implies anonymous / http
        client = Client(TestClient.DATA_SERVICE_RESOURCE_ID, certfile='')
        self.assertTrue(client.conn.subject.anon)
        self.assertFalse(client.conn.vo_token)

        # Specifying a token implies authenticated / http
        client = Client(TestClient.DATA_SERVICE_RESOURCE_ID, token='a_token_string')
        self.assertTrue(client.conn.subject.anon)
        self.assertTrue(client.conn.vo_token)

        # Specifying both a certfile and token implies token (auth) / http
        with patch('os.access'):
            client = Client(TestClient.DATA_SERVICE_RESOURCE_ID, certfile=certfile,
                            token='a_token_string')
        self.assertTrue(client.conn.subject.anon)
        self.assertTrue(client.conn.vo_token)

    patch('vos.EndPoints.nodes', Mock())

    @patch('vos.vos.md5_cache.MD5Cache.compute_md5')
    @patch('__main__.open', MagicMock(), create=True)
    @pytest.mark.skip
    def test_copy(self, computed_md5_mock):
        # the md5sum of the file being copied
        md5sum = 'd41d8cd98f00b204e9800998ecf84eee'
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
        headers = MagicMock()
        headers.get.return_value = md5sum
        response.headers = headers
        session.get.return_value = response
        conn.session = session

        test_client = Client()
        # use the mocked connection instead of the real one
        test_client.conn = conn
        get_node_url_mock = Mock(
            return_value=['http://cadc.ca/test', 'http://cadc.ca/test'])
        test_client.get_node_url = get_node_url_mock
        mock_update = Mock()
        test_client.update = mock_update

        # patch Client.get_node to return our mocked node
        get_node_mock = Mock(return_value=node)
        test_client.get_node = get_node_mock

        # time to test...
        storageLocation = 'cadc:TEST/foo'
        osLocation = '/tmp/foo'
        if os.path.isfile(osLocation):
            os.remove(osLocation)
        # copy from vospace
        test_client.copy(storageLocation, osLocation)
        get_node_url_mock.assert_called_once_with(storageLocation,
                                                  method='GET',
                                                  cutout=None, view='data')
        computed_md5_mock.assert_called_once_with(osLocation)
        assert get_node_mock.called

        # repeat - local file and vospace file are now the same -> only
        # get_node is called to get the md5 of remote file
        get_node_url_mock.reset_mock()
        computed_md5_mock.reset_mock()
        get_node_mock.reset_mock()
        props.reset_mock()
        props.get.return_value = md5sum
        test_client.copy(storageLocation, osLocation)
        assert not get_node_url_mock.called
        computed_md5_mock.assert_called_once_with(osLocation)
        get_node_mock.assert_called_once_with(storageLocation)

        # change the content of local files to trigger a new copy
        get_node_url_mock.reset_mock()
        computed_md5_mock.reset_mock()
        computed_md5_mock.side_effect = ['d002233', md5sum]
        get_node_mock.reset_mock()
        test_client.copy(storageLocation, osLocation)
        get_node_url_mock.assert_called_once_with(storageLocation,
                                                  method='GET',
                                                  cutout=None, view='data')
        computed_md5_mock.assert_called_with(osLocation)
        get_node_mock.assert_called_once_with(storageLocation)

        # copy to vospace when md5 sums are the same -> only update occurs
        get_node_url_mock.reset_mock()
        computed_md5_mock.reset_mock()
        computed_md5_mock.side_effect = None
        computed_md5_mock.return_value = md5sum
        test_client.copy(osLocation, storageLocation)
        mock_update.assert_called_once()
        assert not get_node_url_mock.called

        # make md5 different
        get_node_url_mock.reset_mock()
        get_node_url_mock.return_value =\
            ['http://cadc.ca/test', 'http://cadc.ca/test']
        computed_md5_mock.reset_mock()
        mock_update.reset_mock()
        props.reset_mock()
        # props.get.side_effect = ['d00223344', md5sum]
        props.get.side_effect = ['d00223344', 88, md5sum, 'text/plain']
        test_client.copy(osLocation, storageLocation)
        assert not mock_update.called
        get_node_url_mock.assert_called_once_with(storageLocation, 'PUT')
        computed_md5_mock.assert_called_once_with(osLocation)

        # copy 0 size file -> delete and create on client but no bytes
        # transferred
        get_node_url_mock.reset_mock()
        computed_md5_mock.reset_mock()
        computed_md5_mock.return_value = vos.ZERO_MD5
        props.get.side_effect = [md5sum]
        mock_delete = Mock()
        mock_create = Mock()
        test_client.delete = mock_delete
        test_client.create = mock_create
        test_client.copy(osLocation, storageLocation)
        mock_create.assert_called_once_with(storageLocation)
        mock_delete.assert_called_once_with(storageLocation)
        assert not get_node_url_mock.called

        # copy new 0 size file -> reate on client but no bytes transferred
        get_node_url_mock.reset_mock()
        computed_md5_mock.reset_mock()
        mock_delete.reset_mock()
        mock_create.reset_mock()
        computed_md5_mock.return_value = vos.ZERO_MD5
        props.get.side_effect = [None]
        mock_delete = Mock()
        mock_create = Mock()
        test_client.delete = mock_delete
        test_client.create = mock_create
        test_client.copy(osLocation, storageLocation)
        mock_create.assert_called_once_with(storageLocation)
        assert not mock_delete.called
        assert not get_node_url_mock.called

        # error tests - md5sum mismatch
        props.get.side_effect = [md5sum]
        computed_md5_mock.return_value = '000bad000'
        with self.assertRaises(OSError):
            test_client.copy(storageLocation, osLocation)

        with self.assertRaises(OSError):
            test_client.copy(osLocation, storageLocation)

        # requests just the headers
        props.get.side_effect = [None]
        get_node_url_mock = Mock(
            return_value=['http://cadc.ca/test', 'http://cadc.ca/test'])
        test_client.get_node_url = get_node_url_mock
        computed_md5_mock.reset_mock()
        computed_md5_mock.side_effect = ['d002233', md5sum]
        get_node_mock.reset_mock()
        test_client.copy(storageLocation, osLocation, head=True)
        get_node_url_mock.assert_called_once_with(storageLocation,
                                                  method='GET',
                                                  cutout=None, view='header')

    # patch sleep to stop the test from sleeping and slowing down execution
    @patch('vos.vos.time.sleep', MagicMock(), create=True)
    @patch('vos.vos.VOFile')
    @pytest.mark.skip
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
