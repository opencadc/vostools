# Test the vos Client class

import os
import requests
import hashlib
from datetime import datetime, timedelta

from mock import Mock, patch, MagicMock, call
from vos.storage_inventory import Client
from cadcutils.exceptions import NotFoundException
import pytest


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

TEST_SERVICE_RESOURCE_ID = 'ivo://cadc.nrc.ca/mystorage'


class Object(object):
    pass


@patch('vos.vos.net.BaseWsClient', Mock())
def test_init_client():
    # No parameters uses cert in ~/.ssl giving authenticated / https
    # create a fake pem file
    certfile = '/tmp/some-cert-file.pem'
    open(certfile, 'w+')
    Client.VOSPACE_CERTFILE = "some-cert-file.pem"
    with patch('os.access'):
        client = Client(TEST_SERVICE_RESOURCE_ID,
                        certfile=certfile)
    assert client.get_endpoints().conn.subject.certificate
    assert not client.get_endpoints().conn.vo_token

    # Supplying an empty string for certfile implies anonymous / http
    client = Client(TEST_SERVICE_RESOURCE_ID, certfile='')
    assert client.get_endpoints().conn.subject.anon
    assert not client.get_endpoints().conn.vo_token

    # Specifying a token implies authenticated / http
    client = Client(TEST_SERVICE_RESOURCE_ID,
                    token='a_token_string')
    assert client.get_endpoints().conn.subject.anon
    assert client.get_endpoints().conn.vo_token

    # Specifying both a certfile and token implies token (auth) / http
    with patch('os.access'):
        client = Client(TEST_SERVICE_RESOURCE_ID,
                        certfile=certfile, token='a_token_string')
    assert client.get_endpoints().conn.subject.anon
    assert client.get_endpoints().conn.vo_token


@patch('vos.storage_inventory.StorageEndPoints')
def test_copy_from(endpoints_mock):
    ep_mock = Mock()

    # -------------- LOCAL ----------------
    # local copy from
    ep_mock.transfer = None
    ep_mock.files = 'https://url1/files'

    # mock the head response
    test_file_content_1 = 'test file 123'.encode()
    tmp_md5 = hashlib.md5()
    tmp_md5.update(test_file_content_1)
    test_file1_md5 = tmp_md5.hexdigest()
    mock_head_resp = MagicMock(spec=requests.Response)
    headers = {
        'Location': 'https://gohere.com',
        'content-disposition': 'attachment; filename="mytestfiledownload.txt"',
        'Content-Length': len(test_file_content_1),
        'Content-MD5': test_file1_md5,
        'Date': 'Thu, 22 Oct 2020 02:07:31 GMT'
    }
    mock_head_resp.headers = headers
    mock_head_resp.status_code = 200
    ep_mock.session = Mock()
    ep_mock.session.head.return_value = mock_head_resp
    endpoints_mock.return_value = ep_mock

    # mock the get response
    mock_get_resp = MagicMock(spec=requests.Response)
    mock_get_resp.headers = headers
    mock_get_resp.status_code = 200

    mock_get_resp.iter_content.return_value = [test_file_content_1]
    ep_mock.session.get.return_value = mock_get_resp

    # time to test...
    storage_location = 'cadc:TEST/foo'
    os_location = '/tmp/foo'
    if os.path.isfile(os_location):
        os.remove(os_location)
    assert not os.path.exists(os_location)
    test_client = Client(TEST_SERVICE_RESOURCE_ID)
    response = test_client.copy(storage_location, os_location)
    assert os.path.isfile(os_location)
    assert len(test_file_content_1) == response, 'Got incorrect file size'
    ep_mock.session.get.assert_called_with(
        '{}/{}'.format(ep_mock.files, storage_location),
        stream=True, timeout=(3.05, 120))

    # no gets for subsequent calls since the local file is current
    ep_mock.session.head.reset_mock()
    ep_mock.session.get.reset_mock()
    response = test_client.copy(storage_location, os_location)
    assert os.path.isfile(os_location)
    assert len(test_file_content_1) == response, 'Got incorrect file size'
    ep_mock.session.get.assert_not_called()

    # gets for when file is not current (no date in the header)
    ep_mock.session.head.reset_mock()
    ep_mock.session.get.reset_mock()
    del headers['Date']
    response = test_client.copy(storage_location, os_location)
    assert os.path.isfile(os_location)
    assert len(test_file_content_1) == response, 'Got incorrect file size'
    ep_mock.session.get.assert_called_with(
        '{}/{}'.format(ep_mock.files, storage_location),
        stream=True, timeout=(3.05, 120))

    # gets for when file is not current (more recent Date in the header)
    ep_mock.session.head.reset_mock()
    ep_mock.session.get.reset_mock()
    now = datetime.utcnow()
    headers['Date'] = now.strftime('%a, %d %b %Y %H:%M:%S GMT')
    response = test_client.copy(storage_location, os_location)
    assert os.path.isfile(os_location)
    assert len(test_file_content_1) == response, 'Got incorrect file size'
    ep_mock.session.get.assert_called_with(
        '{}/{}'.format(ep_mock.files, storage_location),
        stream=True, timeout=(3.05, 120))

    # local file is newer but remote content different -> update local
    ep_mock.session.head.reset_mock()
    ep_mock.session.get.reset_mock()
    test_file_content_2 = 'This is the new test content'.encode()
    mock_get_resp.iter_content.return_value = [test_file_content_2]
    headers['Date'] = 'Thu, 22 Oct 2020 02:07:31 GMT'
    headers['Content-Length'] = len(test_file_content_2)
    tmp_md5 = hashlib.md5()
    tmp_md5.update(test_file_content_2)
    test_file2_md5 = tmp_md5.hexdigest()
    headers['Content-MD5'] = test_file2_md5
    response = test_client.copy(storage_location, os_location)
    assert os.path.isfile(os_location)
    assert os.stat(os_location).st_size == len(test_file_content_2)
    assert len(test_file_content_2) == response, 'Got incorrect file size'
    ep_mock.session.get.assert_called_with(
        '{}/{}'.format(ep_mock.files, storage_location),
        stream=True, timeout=(3.05, 120))

    # -------------- GLOBAL ----------------
    # check it uses content disposition for the name of the file when not
    # specified
    ep_mock.files = None
    ep_mock.transfer = 'https://global.transfer'
    ep_mock.session.head.reset_mock()
    ep_mock.session.get.reset_mock()
    headers['content-disposition'] = \
        'attachment; filename="mytestfiledownload.txt"'
    dest_dir = '/tmp'
    expected_file = os.path.join(dest_dir, 'mytestfiledownload.txt')
    if os.path.isfile(expected_file):
        os.remove(expected_file)
    locations = ['https://location1', 'https://location2']
    with patch('vos.storage_inventory.Transfer') as tm:
        transfer_mock = Mock()
        transfer_mock.transfer.return_value = locations
        tm.return_value = transfer_mock
        response = test_client.copy(storage_location, dest_dir)
    assert os.path.isfile(expected_file)
    assert os.stat(expected_file).st_size == len(test_file_content_2)
    assert len(test_file_content_2) == response, 'Got incorrect file size'
    ep_mock.session.get.assert_called_with(
        locations[0], stream=True, timeout=(3.05, 120))
    os.remove(expected_file)

    # check it uses the path of the URI when destination is directory and
    # not content-disposition found
    if os.path.isfile(os_location):
        os.remove(os_location)
    ep_mock.session.head.reset_mock()
    ep_mock.session.get.reset_mock()
    del headers['content-disposition']
    with patch('vos.storage_inventory.Transfer') as tm:
        transfer_mock = Mock()
        transfer_mock.transfer.return_value = locations
        tm.return_value = transfer_mock
        response = test_client.copy(storage_location, dest_dir)
    assert os.path.isfile(os_location)  # /tmp/foo is expected file
    assert os.stat(os_location).st_size == len(test_file_content_2)
    assert len(test_file_content_2) == response, 'Got incorrect file size'
    ep_mock.session.get.assert_called_with(
        locations[0], stream=True, timeout=(3.05, 120))

    # cleanup
    os.remove(os_location)

    # FAILURE
    ep_mock.session.head.reset_mock()
    headers['Content-Length'] = 555
    ep_mock.session.head.side_effect = [mock_head_resp, mock_head_resp]
    with pytest.raises(OSError) as e:
        with patch('vos.storage_inventory.Stream') as sm:
            with patch('vos.storage_inventory.Transfer') as tm:
                stream_mock = Mock()
                # none of the PUT URLs is successfull
                stream_mock.download.side_effect = [OSError(), OSError()]
                sm.return_value = stream_mock
                transfer_mock = Mock()
                transfer_mock.transfer.return_value = locations
                tm.return_value = transfer_mock
                test_client.copy(storage_location, os_location)
    assert 'Failed to copy {} -> {}: '.format(
        storage_location, os_location) == str(e.value)


@patch('vos.storage_inventory.StorageEndPoints')
def test_copy_to(endpoints_mock):
    ep_mock = Mock()

    def mock_put(url, data):
        """
        Mock function to put data without transferring the bytes
        :param url:
        :param data:
        :return:
        """
        mock_put.url = url
        hash = hashlib.md5()
        mock_put.data = data.read(64000)  # This only handles small files
        hash.update(mock_put.data)
        # mock the get response
        mock_put_resp = MagicMock(spec=requests.Response)
        mock_put_resp.status_code = 200
        return mock_put_resp
    # static function variables
    mock_put.data = None
    mock_put.url = None

    # -------------- LOCAL ----------------
    # local copy to with new file
    ep_mock.transfer = None
    ep_mock.files = 'https://url1/files'

    source = '/tmp/mystoragetest.txt'
    if os.path.isfile(source):
        os.remove(source)
    test_file_content_1 = 'test file 123'.encode()
    tmp_md5 = hashlib.md5()
    tmp_md5.update(test_file_content_1)
    test_file1_md5 = tmp_md5.hexdigest()
    open(source, 'wb').write(test_file_content_1)

    mock_head_resp = MagicMock(spec=requests.Response)
    headers = {
        'Location': 'https://gohere.com',
        'content-disposition': 'attachment; filename="mytestfiledownload.txt"',
        'Content-Length': len(test_file_content_1),
        'Content-MD5': test_file1_md5,
        'Date': 'Thu, 22 Oct 2020 02:07:31 GMT'
    }
    mock_head_resp.headers = headers
    mock_head_resp.status_code = 200
    ep_mock.session = Mock()
    ep_mock.session.head.side_effect = [NotFoundException(), mock_head_resp]
    endpoints_mock.return_value = ep_mock

    ep_mock.session.put = mock_put

    # time to test...
    storage_location = 'cadc:TEST/foo'
    os_location = source
    assert os.path.isfile(os_location)
    test_client = Client(TEST_SERVICE_RESOURCE_ID)
    response = test_client.copy(os_location, storage_location)
    assert len(test_file_content_1) == response, 'Got incorrect file size'
    assert '{}/{}'.format(ep_mock.files, storage_location) == \
           ep_mock.session.put.url
    assert test_file_content_1 == ep_mock.session.put.data

    # no puts for subsequent calls since the local file is current
    ep_mock.session.head.reset_mock()
    ep_mock.session.head.side_effect = [mock_head_resp]
    mock_put.data = None
    mock_put.url = None
    response = test_client.copy(os_location, storage_location)
    assert len(test_file_content_1) == response, 'Got incorrect file size'
    assert mock_put.data is None
    assert mock_put.url is None

    # puts for when file is not current (no date in the header)
    ep_mock.session.head.reset_mock()
    del headers['Date']
    ep_mock.session.head.side_effect = [mock_head_resp, mock_head_resp]
    mock_put.data = None
    mock_put.url = None
    response = test_client.copy(os_location, storage_location)
    assert len(test_file_content_1) == response, 'Got incorrect file size'
    assert '{}/{}'.format(ep_mock.files, storage_location) == \
           ep_mock.session.put.url
    assert test_file_content_1 == ep_mock.session.put.data

    # gets for when file is not current (more recent Date in the header)
    ep_mock.session.head.reset_mock()
    ep_mock.session.head.side_effect = [mock_head_resp, mock_head_resp]
    now = datetime.utcnow()
    headers['Date'] = now.strftime('%a, %d %b %Y %H:%M:%S GMT')
    response = test_client.copy(os_location, storage_location)
    assert os.path.isfile(os_location)
    assert len(test_file_content_1) == response, 'Got incorrect file size'
    assert '{}/{}'.format(ep_mock.files, storage_location) == \
           ep_mock.session.put.url
    assert test_file_content_1 == ep_mock.session.put.data

    # local file is different
    ep_mock.session.head.reset_mock()
    headers['Date'] = 'Thu, 22 Oct 2020 02:07:31 GMT'
    test_file_content_2 = 'This is the new test content'.encode()
    tmp_md5 = hashlib.md5()
    tmp_md5.update(test_file_content_2)
    test_file2_md5 = tmp_md5.hexdigest()
    open(source, 'wb').write(test_file_content_2)
    # create the second head response after update
    headers2 = {
        'Location': 'https://gohere.com',
        'content-disposition': 'attachment; filename="mytestfiledownload.txt"',
        'Content-Length': len(test_file_content_2),
        'Content-MD5': test_file2_md5,
        'Date': 'Thu, 22 Oct 2020 02:07:31 GMT'
    }
    mock_head2_resp = MagicMock(spec=requests.Response)
    mock_head2_resp.headers = headers2
    mock_head_resp.status_code = 200
    ep_mock.session.head.side_effect = [mock_head_resp, mock_head2_resp]
    response = test_client.copy(os_location, storage_location)
    assert len(test_file_content_2) == response, 'Got incorrect file size'
    assert '{}/{}'.format(ep_mock.files, storage_location) == \
           ep_mock.session.put.url
    assert test_file_content_2 == ep_mock.session.put.data

    # -------------- GLOBAL ----------------
    ep_mock.files = None
    ep_mock.transfer = 'https://global.transfer'
    ep_mock.session.head.reset_mock()
    locations = ['https://location1', 'https://location2']
    ep_mock.session.head.side_effect = [mock_head_resp, mock_head2_resp]
    with patch('vos.storage_inventory.Transfer') as tm:
        transfer_mock = Mock()
        transfer_mock.transfer.return_value = locations
        tm.return_value = transfer_mock
        response = test_client.copy(os_location, storage_location)
    assert len(test_file_content_2) == response, 'Got incorrect file size'
    assert locations[0] == ep_mock.session.put.url
    assert test_file_content_2 == ep_mock.session.put.data

    # repeat the test. Even if sizes are the same, because the remote
    # is older bytes are transferred again
    ep_mock.files = None
    ep_mock.session.put.url = None
    ep_mock.session.put.data = None
    ep_mock.transfer = 'https://global.transfer'
    ep_mock.session.head.reset_mock()
    locations = ['https://location1', 'https://location2']
    ep_mock.session.head.side_effect = [mock_head_resp, mock_head2_resp]
    with patch('vos.storage_inventory.Transfer') as tm:
        transfer_mock = Mock()
        transfer_mock.transfer.return_value = locations
        tm.return_value = transfer_mock
        response = test_client.copy(os_location, storage_location)
    assert len(test_file_content_2) == response, 'Got incorrect file size'
    assert locations[0] == ep_mock.session.put.url
    assert test_file_content_2 == ep_mock.session.put.data

    # repeat the test. Make size the same but remote copy more recent
    # so no bytes are being transferred
    ep_mock.files = None
    ep_mock.session.put.url = None
    ep_mock.session.put.data = None
    ep_mock.transfer = 'https://global.transfer'
    ep_mock.session.head.reset_mock()
    locations = ['https://location1', 'https://location2']
    future = datetime.utcnow() + timedelta(days=3)
    headers2['Date'] = future.strftime('%a, %d %b %Y %H:%M:%S GMT')
    ep_mock.session.head.side_effect = [mock_head2_resp]
    with patch('vos.storage_inventory.Transfer') as tm:
        transfer_mock = Mock()
        transfer_mock.transfer.return_value = locations
        tm.return_value = transfer_mock
        response = test_client.copy(os_location, storage_location)
    assert len(test_file_content_2) == response, 'Got incorrect file size'
    assert ep_mock.session.put.url is None
    assert ep_mock.session.put.data is None

    # FAILURE
    ep_mock.session.head.reset_mock()
    headers2['Content-Length'] = 555
    ep_mock.session.head.side_effect = [mock_head2_resp, mock_head2_resp]
    with pytest.raises(OSError) as e:
        with patch('vos.storage_inventory.Stream') as sm:
            with patch('vos.storage_inventory.Transfer') as tm:
                stream_mock = Mock()
                # none of the PUT URLs is successfull
                stream_mock.upload.side_effect = [OSError, OSError]
                sm.return_value = stream_mock
                transfer_mock = Mock()
                transfer_mock.transfer.return_value = locations
                tm.return_value = transfer_mock
                test_client.copy(os_location, storage_location)
    assert 'Failed to copy {} -> {}: '.format(
        os_location, storage_location) == str(e.value)


@patch('vos.storage_inventory.StorageEndPoints')
def test_vrm(endpoints_mock):
    ep_mock = Mock()

    # -------------- LOCAL ----------------
    ep_mock.transfer = None
    ep_mock.files = 'https://url1/files'
    endpoints_mock.return_value = ep_mock

    storage_location = 'cadc:TEST/foo'
    test_client = Client(TEST_SERVICE_RESOURCE_ID)
    test_client.delete(storage_location)
    ep_mock.conn.ws_client.delete.assert_called_with(
        '{}/{}'.format(ep_mock.files, storage_location))

    # test not found
    mock_delete_resp = MagicMock(spec=requests.Response)
    mock_delete_resp.status_code = 404
    ep_mock.conn.ws_client.delete.side_effect = \
        [NotFoundException('Not found')]
    with pytest.raises(NotFoundException) as e:
        test_client.delete(storage_location)
    assert 'Not found' in str(e.value)

    # -------------- GLOBAL ----------------
    ep_mock.transfer = ['https://url1/files', 'https://url2/files']
    ep_mock.files = None

    # TODO temporary
    with pytest.raises(NotImplementedError):
        test_client.delete(storage_location)
