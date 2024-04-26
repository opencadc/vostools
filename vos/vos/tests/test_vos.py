# ***********************************************************************
# ******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
# *************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
#
#  (c) 2024.                            (c) 2024.
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

# Test the vos Client class

import os
import unittest
import pytest
import requests
from xml.etree import ElementTree
from unittest.mock import Mock, patch, MagicMock, call
from vos import Client, Connection, Node, VOFile, vosconfig
from vos import vos as vos
from urllib.parse import urlparse, unquote
from io import BytesIO
import hashlib
import tempfile


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


@patch('vos.vos.net.ws.WsCapabilities.get_access_url',
       Mock(return_value='http://foo.com/vospace'))
def test_get_node_url():
    client = Client()
    with pytest.raises(TypeError):
        client.get_node_url('vos://cadc.nrc.ca!vospace/auser', sort='Blah')
    with pytest.raises(ValueError):
        client.get_node_url('vos://cadc.nrc.ca!vospace/auser', order='Blah')

    response = Mock(spec=requests.Response)
    response.status_code = 303

    resource_id = 'ivo://cadc.nrc.ca/vospace'
    session_mock = Mock(spec=requests.Session, get=Mock(return_value=response))
    session_mock.headers = Mock()
    client._endpoints[resource_id] = vos.EndPoints(resource_id_uri=resource_id)
    client._endpoints[resource_id].conn.ws_client._session = session_mock
    equery = urlparse(client.get_node_url('vos://cadc.nrc.ca!vospace/auser',
                      sort=vos.SortNodeProperty.DATE)).query
    assert (unquote(equery) ==
            'sort={}'.format(vos.SortNodeProperty.DATE.value))

    equery = urlparse(client.get_node_url('vos://cadc.nrc.ca!vospace/auser',
                      sort=vos.SortNodeProperty.LENGTH, order='asc')).query
    args = unquote(equery).split('&')
    assert (2 == len(args))
    assert ('order=asc' in args)
    assert ('sort={}'.format(vos.SortNodeProperty.LENGTH.value) in args)

    equery = urlparse(client.get_node_url('vos://cadc.nrc.ca!vospace/auser',
                                          order='desc')).query
    assert ('order=desc' == unquote(equery))

    # test files URL
    transfer_url = 'https://mystorage.org/minoc/files/abc:VOS/002'
    response.headers = {'Location': transfer_url}
    mock_session = Mock(spec=requests.Session, get=Mock(return_value=response))
    client.get_session = Mock(return_value=mock_session)
    client._fs_type = False
    assert transfer_url == \
        client.get_node_url('vos://cadc.nrc.ca!vospace/auser',
                            view='header')

    # test fits header
    assert {'META': 'true'} == client._get_soda_params(view='header')

    # test pixel cutouts
    pcutout = '[1][100:125,100:175]'
    assert {'SUB': pcutout} == client._get_soda_params(cutout=pcutout)

    # test sky coordinates
    scutout = '1.1,2.2,3.3'
    assert {'CIRCLE':  scutout} == client._get_soda_params(cutout='CIRCLE='+scutout)


class TestClient(unittest.TestCase):
    """Test the vos Client class.
    """
    @classmethod
    def setUpClass(cls):
        super(TestClient, cls).setUpClass()
        # make sure we are using the default config file
        os.environ['VOSPACE_CONFIG_FILE'] = vosconfig._DEFAULT_CONFIG_PATH

    def off_quota(self):
        """
        Test that a 413 raised by the server gets a reasonable error to the
        user.
        @return:
        """
        with patch('vos.vos.VOFile') as mockVOFile:
            mockVOFile.open = Mock()
            mockVOFile.read = Mock()
            mockVOFile.write = Mock()
        client = Client(resource_id='ivo://cadc.nrc.ca/vault')
        client.conn = Mock()
        client.transfer(uri='vos:test', direction="pushToVoSpace")

    def test_init_client(self):
        # No parameters uses cert in ~/.ssl giving authenticated / https
        # create a fake pem file
        certfile = '/tmp/some-cert-file.pem'
        open(certfile, 'w+')
        Client.VOSPACE_CERTFILE = "some-cert-file.pem"
        with patch('os.access'):
            client = Client(vospace_certfile=certfile)
        client.is_remote_file = Mock()
        client.get_session(uri='vos://cadc.nrc.ca~vault')
        conn = client._endpoints['ivo://cadc.nrc.ca/vault'].conn
        self.assertTrue(conn.subject.certificate)
        self.assertFalse(conn.vo_token)

        # Supplying an empty string for certfile implies anonymous / http
        client = Client(vospace_certfile='')
        client.is_remote_file = Mock()
        client.get_session(uri='vos://cadc.nrc.ca~vault')
        conn = client._endpoints['ivo://cadc.nrc.ca/vault'].conn
        self.assertTrue(conn.subject.anon)
        self.assertFalse(conn.vo_token)

        # Specifying a token implies authenticated / http
        client = Client(vospace_token='a_token_string')
        client.is_remote_file = Mock()
        client.get_session(uri='vos://cadc.nrc.ca~vault')
        conn = client._endpoints['ivo://cadc.nrc.ca/vault'].conn
        self.assertTrue(conn.subject.anon)
        self.assertTrue(conn.vo_token)

        # Specifying both a certfile and token implies token (auth) / http
        with patch('os.access'):
            client = Client(vospace_certfile=certfile,
                            vospace_token='a_token_string')
        client.get_session(uri='vos://cadc.nrc.ca~vault')
        conn = client._endpoints['ivo://cadc.nrc.ca/vault'].conn
        self.assertTrue(conn.subject.anon)
        self.assertTrue(conn.vo_token)

        # update auth for specific service
        with patch('os.access'):
            client.set_auth(uri='vos://cadc.nrc.ca~vault',
                            vospace_certfile=certfile)
        conn = client._endpoints['ivo://cadc.nrc.ca/vault'].conn
        self.assertTrue(conn.subject.certificate)
        self.assertFalse(conn.vo_token)

    @patch('vos.vos.net.ws.WsCapabilities.get_access_url',
           Mock(return_value='http://foo.com/vospace'))
    @patch('vos.vos.net.ws.RetrySession.send',
           Mock(return_value=Mock(spec=requests.Response, status_code=404)))
    def test_open(self):
        # Invalid mode raises OSError
        with self.assertRaises(OSError):
            client = Client()
            client.open('vos://foo/bar', mode=-10000)

        with self.assertRaises(OSError):
            client = Client()
            client.get_node_url = Mock(return_value=None)
            client.open(None, url=None)

        conn = Connection(resource_id='ivo://cadc.nrc.ca/vault')
        mock_vofile = VOFile(['http://foo.com/bar'], conn, 'GET')
        client = Client()
        client.get_node_url = Mock(return_value=mock_vofile)
        endpoints_mock = Mock(conn=conn)
        client.get_endpoints = Mock(return_value=endpoints_mock)
        vofile = client.open(None, url=None)
        self.assertEqual(vofile.url.URLs[0], 'http://foo.com/bar')

    @patch('vos.vos.Node.get_info', Mock(return_value={'name': 'aa'}))
    def test_get_info_list(self):
        # list tuples of a LinkNode
        mock_node = MagicMock(type='vos:DataNode')
        mock_node.return_value = mock_node
        mock_node.name = 'testnode'
        mock_node.get_info.return_value = {'name': 'aa'}
        mock_link_node = Mock(type='vos:LinkNode')
        mock_link_node.target = 'vos:/somefile'
        client = Client()
        client.get_node = MagicMock(side_effect=[mock_link_node, mock_node])
        self.assertEqual([mock_node],
                         client.get_children_info('vos:/somenode'))

    def test_nodetype(self):
        mock_node = MagicMock(id=333)
        mock_node.type = 'vos:ContainerNode'
        client = Client()
        client.get_node = Mock(return_value=mock_node)
        self.assertEqual('vos:ContainerNode',
                         client._node_type('vos:/somenode'))
        self.assertTrue(client.isdir('vos:/somenode'))

        mock_node.type = 'vos:DataNode'
        self.assertEqual('vos:DataNode', client._node_type('vos:/somenode'))
        self.assertTrue(client.isfile('vos:/somenode'))

        # through a link
        mock_node.type = 'vos:ContainerNode'
        mock_link_node = Mock(type='vos:LinkNode')
        mock_link_node.target = 'vos:/somefile'
        client.get_node = Mock(side_effect=[mock_link_node, mock_node,
                                            mock_link_node, mock_node])
        self.assertEqual('vos:ContainerNode',
                         client._node_type('vos:/somenode'))
        self.assertTrue(client.isdir('vos:/somenode'))

        # through an external link - not sure why the type is DataNode in
        # this case???
        mock_link_node.target = '/somefile'
        client.get_node = Mock(side_effect=[mock_link_node, mock_link_node])
        self.assertEqual('vos:DataNode', client._node_type('vos:/somenode'))
        self.assertTrue(client.isfile('vos:/somenode'))

    patch('vos.EndPoints.nodes', Mock())

    @patch('vos.vos.net.ws.WsCapabilities.get_access_url',
           Mock(return_value='http://foo.com/vospace'))
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
        self.assertEqual(['vos:/anode/'], client.glob('vos:/anode/'))

        # simple test for file listing of file
        mock_node = MagicMock(type='vos:DataNode')
        mock_node.configure_mock(name='afile')
        client = Client()
        client.get_node = Mock(return_value=mock_node)
        self.assertEqual(['vos:/afile'], client.glob('vos:/afile'))

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
        mock_node.node_list = [mock_base_node, mock_child_node1,
                               mock_child_node2]
        client = Client()
        client.get_node = Mock(
            side_effect=[mock_node, mock_base_node, mock_node])
        self.assertEqual(['vos:/anode/abc'], client.glob('vos:/anode/a*'))
        self.assertEqual(['vos:/anode/abc'], client.glob('vos:/*node/abc'))

        # test nodes:
        # /anode/.test1 /bnode/sometests /bnode/blah
        # /[a,c]node/*test* should return /bnode/somtests (.test1 is filtered
        # out as a special file)

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
        client.is_remote_file = Mock()
        client.get_node = Mock(
            side_effect=[mock_base_node, mock_node1, mock_node2])
        self.assertEqual(['vos:/bnode/sometests'],
                         client.glob('vos:/[a,b]node/*test*'))

    @patch('vos.vos.md5_cache.MD5Cache.compute_md5')
    @patch('__main__.open', MagicMock(), create=True)
    def test_copy(self, computed_md5_mock):

        def is_remote_file(uri):
            return True if ':' in uri else False

        file_content = 'File content'.encode('utf-8')
        # the md5sum of the file being copied
        transfer_md5 = hashlib.md5()
        transfer_md5.update(file_content)
        md5sum = transfer_md5.hexdigest()
        # patch the compute_md5 function in vos to return the above value
        computed_md5_mock.return_value = md5sum

        # mock the props of the corresponding node
        props = MagicMock()
        props.get.return_value = md5sum
        # add props to the mocked node
        node = MagicMock(spec=Node)
        node.props = {'MD5': md5sum, 'length': len(file_content)}

        # mock one by one the chain of connection.session.response.headers
        session = MagicMock()
        response = MagicMock()
        headers = MagicMock()
        headers.get.return_value = md5sum
        response.headers = headers
        session.get.return_value = response
        response.iter_content.return_value = BytesIO(file_content)

        test_client = Client()
        test_client.get_session = Mock(return_value=session)
        # use the mocked connection instead of the real one
        node_location_url = 'http://cadc.ca/test'
        get_node_url_mock = Mock(return_value=node_location_url)
        test_client.get_node_url = get_node_url_mock
        mock_update = Mock()
        test_client.update = mock_update

        # patch Client.get_node to return our mocked node
        get_node_mock = Mock(return_value=node)
        test_client.get_node = get_node_mock

        # time to test...
        vospaceLocation = 'vos://authority~test/foo'
        osLocation = '/tmp/foo'
        if os.path.isfile(osLocation):
            os.remove(osLocation)
        # copy from vospace
        test_client.is_remote_file = is_remote_file
        test_client._get_si_client = Mock()
        test_client._get_si_client.return_value.download_file = (
            Mock(return_value=('foo', md5sum, len(file_content))))
        cp_size = test_client.copy(vospaceLocation, osLocation)
        get_node_url_mock.assert_called_once_with(vospaceLocation,
                                                  method='GET',
                                                  cutout=None, view='data')
        si_calls = [call('vos://authority~test/foo'),
                    call().download_file(url=node_location_url, dest=osLocation, params={})]
        assert si_calls == test_client._get_si_client.mock_calls
        assert not computed_md5_mock.called, 'MD5 should be computed on the fly'
        assert cp_size == len(file_content)

        # change the content of local files to trigger a new copy
        get_node_url_mock.reset_mock()
        get_node_mock.reset_mock()

        computed_md5_mock.reset_mock()
        test_client._get_si_client.reset_mock()
        computed_md5_mock.return_value = 'd002233'
        response.iter_content.return_value = BytesIO(file_content)
        test_client.copy(vospaceLocation, osLocation)
        get_node_url_mock.assert_called_once_with(vospaceLocation,
                                                  method='GET',
                                                  cutout=None, view='data')
        si_calls = [call('vos://authority~test/foo'),
                    call().download_file(url=node_location_url,
                                         dest=osLocation, params={})]
        assert si_calls == test_client._get_si_client.mock_calls

        # change the content of local files to trigger a new copy
        get_node_url_mock.reset_mock()
        get_node_url_mock.return_value = 'https://mysite.com/files/node123/cutout'
        computed_md5_mock.reset_mock()
        test_client._get_si_client.reset_mock()
        computed_md5_mock.return_value = 'd002233'
        # computed_md5_mock.side_effect = ['d002233', md5sum]
        get_node_mock.reset_mock()
        response.iter_content.return_value = BytesIO(file_content)
        session.get.return_value = response
        test_client.get_session = Mock(return_value=session)
        # client must be a vault client
        test_client._fs_type = False
        test_client.copy('{}{}'.format(vospaceLocation,
                                       '[1][10:60]'), osLocation)

        si_calls = [call('vos://authority~test/foo'),
                    call().download_file(url='https://mysite.com/files/node123/cutout',
                                         dest=osLocation, params={'SUB': '[1][10:60]'})]
        assert si_calls == test_client._get_si_client.mock_calls

        # test cavern does not support SODA operations
        test_client._fs_type = True
        with pytest.raises(ValueError):
            test_client.copy('{}{}'.format(vospaceLocation, '[1][10:60]'), osLocation)
            with pytest.raises(ValueError):
                test_client.copy(vospaceLocation, osLocation, head=True)

        test_client._fs_type = False
        # copy to vospace when md5 sums are the same -> only update occurs
        open(osLocation, 'wb').write(file_content)
        get_node_url_mock.reset_mock()
        computed_md5_mock.reset_mock()
        computed_md5_mock.side_effect = None
        computed_md5_mock.return_value = md5sum
        test_client.copy(osLocation, vospaceLocation)
        mock_update.assert_called_once()
        assert not get_node_url_mock.called

        # make md5 different on destination
        get_node_url_mock.reset_mock()
        get_node_url_mock.return_value =\
            ['http://cadc.ca/test', 'http://cadc.ca/test']
        computed_md5_mock.reset_mock()
        mock_update.reset_mock()
        computed_md5_mock.return_value = md5sum
        to_update_node = MagicMock()
        to_update_node.props = {'MD5': 'abcde', 'length': len(file_content)}
        test_client.get_node = Mock(side_effect=[to_update_node, node])
        test_client._get_si_client.return_value.upload_file = (
            Mock(return_value=('foo', md5sum, len(file_content))))
        cp_size = test_client.copy(osLocation, vospaceLocation)
        assert not mock_update.called
        get_node_url_mock.assert_called_once_with(vospaceLocation, method='PUT',
                                                  full_negotiation=True)
        computed_md5_mock.assert_called_once_with(osLocation)
        assert cp_size == len(file_content)

        # copy 0 size file -> delete and create node but no bytes
        # transferred
        get_node_url_mock.reset_mock()
        computed_md5_mock.reset_mock()
        test_client.get_node = Mock(return_value=node)
        node.props['length'] = 0
        mock_delete = Mock()
        mock_create = Mock()
        test_client.delete = mock_delete
        test_client.create = mock_create
        with patch('vos.vos.os.stat', Mock()) as stat_mock:
            stat_mock.return_value = Mock(st_size=0)
            cp_size = test_client.copy(osLocation, vospaceLocation)
        mock_create.assert_called_once_with(vospaceLocation)
        mock_delete.assert_called_once_with(vospaceLocation)
        assert not get_node_url_mock.called
        assert 0 == cp_size

        # copy new 0 size file -> create node but no bytes transferred
        get_node_url_mock.reset_mock()
        computed_md5_mock.reset_mock()
        mock_delete.reset_mock()
        mock_create.reset_mock()
        test_client.get_node = Mock(side_effect=[Exception(), node])
        mock_delete = Mock()
        mock_create = Mock()
        test_client.delete = mock_delete
        test_client.create = mock_create
        with patch('vos.vos.os.stat', Mock()) as stat_mock:
            stat_mock.return_value = Mock(st_size=0)
            cp_size = test_client.copy(osLocation, vospaceLocation)
        mock_create.assert_called_once_with(vospaceLocation)
        assert not mock_delete.called
        assert not get_node_url_mock.called
        assert cp_size == 0

        # requests just the headers when md5 not provided in the header
        props.get.side_effect = [None]
        get_node_url_mock = Mock(
            return_value=['http://cadc.ca/test', 'http://cadc.ca/test'])
        test_client.get_node_url = get_node_url_mock
        get_node_mock.reset_mock()
        headers.get.return_value = None
        test_client.copy(vospaceLocation, osLocation, head=True)
        get_node_url_mock.assert_called_once_with(vospaceLocation,
                                                  method='GET',
                                                  cutout=None, view='header')

        # repeat headers request when md5 provided in the header
        props.get.side_effect = md5sum
        get_node_url_mock = Mock(
            return_value=['http://cadc.ca/test', 'http://cadc.ca/test'])
        test_client.get_node_url = get_node_url_mock
        get_node_mock.reset_mock()
        response.iter_content.return_value = BytesIO(file_content)
        test_client.copy(vospaceLocation, osLocation, head=True)
        get_node_url_mock.assert_called_once_with(vospaceLocation,
                                                  method='GET',
                                                  cutout=None, view='header')

    def test_add_props(self):
        old_node = Node(ElementTree.fromstring(NODE_XML))
        old_node.uri = 'vos:sometest'
        new_node = Node(ElementTree.fromstring(NODE_XML))
        new_node.props['quota'] = '1000'
        new_node.create = Mock(return_value=new_node.node)

        data = str(new_node)
        headers = {'size': str(len(data))}

        client = Client()
        client.get_node = Mock(return_value=old_node)
        client.get_node_url = Mock(return_value='http://foo.com/bar')
        mock_session = Mock()
        client.get_session = Mock(return_value=mock_session)

        client.add_props(new_node)
        client.get_session.assert_called_with('vos://foo.com!vospace/bar')
        mock_session.post.assert_called_with('http://foo.com/bar',
                                             headers=headers,
                                             data=data)

    @patch('vos.vos.net.ws.WsCapabilities.get_access_url',
           Mock(return_value='http://www.canfar.phys.uvic.ca/vospace/nodes'))
    def test_create(self):
        uri = 'vos://create.vospace.auth!vospace/bar'
        client = Client()
        node = Node(client.fix_uri(uri))
        node2 = Node(str(node))
        self.assertEqual(node, node2)
        data = str(node)
        headers = {'size': str(len(data)), 'Content-Type': 'text/xml'}

        client = Client()
        # client.get_node_url = Mock(return_value='http://foo.com/bar')
        session_mock = MagicMock()
        client.get_session = Mock(return_value=session_mock)
        session_mock.put.return_value = Mock(content=str(node))

        result = client.create(uri)
        self.assertEqual(node, result)
        session_mock.put.assert_called_with(
            'http://www.canfar.phys.uvic.ca/vospace/nodes/bar',
            headers=headers, data=data)

    def test_update(self):
        node = Node(ElementTree.fromstring(NODE_XML))

        resp = Mock()
        resp.headers.get = Mock(
            return_value="https://www.canfar.phys.uvic.ca/vospace")

        session = Mock(spec=vos.Connection)
        session.post = Mock(return_value=resp)
        session.get = Mock()
        client = Client()
        client.get_session = Mock(return_value=session)
        client.get_node_url = Mock(
            return_value='https://www.canfar.phys.uvic.ca/vospace')
        client.get_transfer_error = Mock()
        client.protocol = 'https'

        data = str(node)
        property_url = 'https://www.canfar.phys.uvic.ca/vospace/async-setprops'
        endpoints_mock = Mock(nodes='https://www.canfar.phys.uvic.ca/vospace/nodes')
        endpoints_mock.recursive_props = property_url
        client.get_endpoints = Mock(return_value=endpoints_mock)
        client.get_session.return_value = session
        result = client.update(node, False)
        self.assertEqual(result, (1, 0))
        session.post.assert_called_with(
            'https://www.canfar.phys.uvic.ca/vospace',
            data=data, allow_redirects=False)

        resp.status_code = 303
        resp.headers = {'location': 'https://www.canfar.phys.uvic.ca/vospace/job'}
        client._run_recursive_job = Mock(return_value=(4, 3))
        session.post = Mock(return_value=resp)
        result = client.update(node, True)
        assert (4, 3) == result
        session.post.assert_called_with(
            'https://www.canfar.phys.uvic.ca/vospace/async-setprops',
            data=data, allow_redirects=False,
            headers={'Content-type': 'text/xml'})

    def test_getNode(self):
        """

        @return:
        """
        uri = "vos://foo.com!vospace/bar"

        nodes = (' <vos:nodes>\n'
                 '<vos:node uri="vos://cadc.nrc.ca!vospace/mydir/file123" '
                 'xs:type="vos:DataNode">\n'
                 '   <vos:properties>\n'
                 '       <vos:property '
                 'uri="ivo://ivoa.net/vospace/core#date">2016-05-10T09:52:13'
                 '</vos:property>\n'
                 '   </vos:properties>\n'
                 '</vos:node>\n'
                 '<vos:node uri="vos://cadc.nrc.ca!vospace/mydir/file456" '
                 'xs:type="vos:DataNode">\n'
                 '   <vos:properties>\n'
                 '       <vos:property uri="ivo://ivoa.net/vospace/core#date">'
                 '2016-05-19T09:52:14</vos:property>\n'
                 '   </vos:properties>\n'
                 '</vos:node>\n'
                 '</vos:nodes>\n')

        mock_vofile = Mock()
        client = Client()
        client.open = Mock(return_value=mock_vofile)
        client.is_remote_file = Mock()

        mock_vofile.read = Mock(
            return_value=NODE_XML.format(uri, '').encode('UTF-8'))
        my_node = client.get_node(uri, limit=0, force=False)
        self.assertEqual(uri, my_node.uri)
        self.assertEqual(len(my_node.node_list), 0)

        mock_vofile.read = Mock(
            return_value=NODE_XML.format(uri, nodes).encode('UTF-8'))

        my_node = client.get_node(uri, limit=2, force=True)
        self.assertEqual(uri, my_node.uri)
        self.assertEqual(len(my_node.node_list), 2)

        my_node = client.get_node(uri, limit=2, force=False)
        self.assertEqual(uri, my_node.uri)
        self.assertEqual(len(my_node.node_list), 2)

    def test_move(self):
        mock_resp_403 = Mock(name="mock_resp_303")
        mock_resp_403.status_code = 403

        client = Client()
        client.is_remote_file = Mock()

        uri1 = 'notvos://cadc.nrc.ca!vault/nosuchfile1'
        uri2 = 'notvos://cadc.nrc.ca!vault/nosuchfile2'

        with self.assertRaises(AttributeError):
            client.move(uri1, uri2)

    @patch('vos.vos.net.ws.WsCapabilities.get_access_url',
           Mock(return_value='https://ws.canfar.net/vault/nodes'))
    def test_delete(self):
        certfile = '/tmp/SomeCert.pem'
        open(certfile, 'w+')
        with patch('os.access'):
            client = Client(vospace_certfile=certfile)
        uri1 = 'vos://cadc.nrc.ca!vault/nosuchfile1'
        url = 'https://ws.canfar.net/vault/nodes/nosuchfile1'
        mock_session = Mock()
        client.get_session = Mock(return_value=mock_session)
        client.delete(uri1)
        mock_session.delete.assert_called_once_with(url)

    @patch('vos.vos.net.ws.WsCapabilities.get_access_url',
           Mock(return_value='http://www.canfar.phys.uvic.ca/vospace/nodes'))
    def test_mkdir(self):
        uri = 'vos://create.vospace.auth!vospace/bar'
        client = Client()
        node = Node(client.fix_uri(uri), Node.CONTAINER_NODE)
        headers = {'Content-Type': 'text/xml'}

        client = Client()
        session_mock = MagicMock()
        client.get_session = Mock(return_value=session_mock)

        client.mkdir(uri)
        session_mock.put.assert_called_with(
            'http://www.canfar.phys.uvic.ca/vospace/nodes/bar',
            headers=headers, data=str(node))

    @patch('vos.vos.net.ws.WsCapabilities.get_access_url',
           Mock(return_value='http://foo.com/vospace'))
    def test_success_failure_case(self):
        with pytest.raises(OSError):
            client = Client()
            client.status('vos:test/node.fits', code='abc')


class TestNode(unittest.TestCase):
    """Test the vos Node class.
    """

    def test_node_eq(self):
        # None node raises LoookupError
        with self.assertRaises(LookupError):
            Node(None)

        # Node equality
        node1 = Node(ElementTree.Element(Node.NODE))
        node2 = Node(ElementTree.Element(Node.NODE))
        self.assertNotEqual(node1, 'foo')
        self.assertEqual(node1, node2)

        node1.props = {'foo': 'bar'}
        self.assertNotEqual(node1, node2)
        node2.props = {'foo': 'bar'}
        self.assertEqual(node1, node2)

    def test_node_set_property(self):
        node = Node(ElementTree.fromstring(NODE_XML))
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
        node = Node(ElementTree.fromstring(NODE_XML))
        self.assertEqual('', node.groupwrite)

        node.chwgrp('foo')
        self.assertEqual('foo', node.groupwrite)

    def test_chrgrp(self):
        node = Node(ElementTree.fromstring(NODE_XML))
        self.assertEqual('', node.groupread)

        node.chrgrp('foo')
        self.assertEqual('foo', node.groupread)

    def test_change_prop(self):
        # Add a new property
        node = Node(ElementTree.fromstring(NODE_XML))
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
        # node.change_prop('quota', None)
        # quota = TestVos.get_node_property(node, 'quota')
        # self.assertIsNone(quota)

    def test_clear_properties(self):
        # Add a new property
        node = Node(ElementTree.fromstring(NODE_XML))
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


@patch('vos.vos.net.ws.WsCapabilities.get_access_url',
       Mock(return_value='http://foo.com/vospace'))
class TestVOFile(unittest.TestCase):
    """Test the vos VOFile class.
    """
    @classmethod
    def setUpClass(cls):
        super(TestVOFile, cls).setUpClass()
        # make sure we are using the default config file
        os.environ['VOSPACE_CONFIG_FILE'] = vosconfig._DEFAULT_CONFIG_PATH

    def test_seek(self):
        my_mock = MagicMock()
        with patch('vos.VOFile.open', my_mock):
            url_list = ['http://foo.com']
            conn = Connection(resource_id='ivo://cadc.nrc.ca/vault')
            method = 'GET'
            vofile = VOFile(url_list, conn, method, size=25)

            vofile.seek(10, os.SEEK_CUR)
            self.assertEqual(10, vofile._fpos)
            vofile.seek(5, os.SEEK_SET)
            self.assertEqual(5, vofile._fpos)
            vofile.seek(10, os.SEEK_END)
            self.assertEqual(15, vofile._fpos)


class Md5File(unittest.TestCase):
    """Test the vos Md5File class.
    """
    def test_operations(self):
        tmpfile = tempfile.NamedTemporaryFile()
        txt = 'This is a test of the Md5File class'
        with open(tmpfile.name, 'w') as f:
            f.write(txt)

        binary_content = open(tmpfile.name, 'rb').read()
        with vos.Md5File(tmpfile.name, 'rb') as f:
            assert binary_content == f.read(10000)
        assert f.file.closed
        hash = hashlib.md5()
        hash.update(binary_content)
        assert f.md5_checksum == hash.hexdigest()
