from __future__ import (absolute_import, division, print_function,
                        unicode_literals)

import sys
import os
from mock import patch, MagicMock, Mock
from vos.commands import vrm
import pytest
from six import StringIO

THIS_DIR = os.path.dirname(os.path.realpath(__file__))
TESTDATA_DIR = os.path.join(THIS_DIR, 'data')


class MyExitError(Exception):

    def __init__(self):
        self.message = 'MyExitError'


@patch('sys.exit', Mock(side_effect=[MyExitError]))
@patch('vos.vos.Client')
def test_delete_nodes(client_mock):
    """Test the delete_nodes function in vrm"""

    # happy path, islink
    mock_node = MagicMock()
    mock_node.islink.return_value = True
    client_mock.return_value.get_node.return_value = mock_node
    delete_mock = MagicMock()
    client_mock.return_value.delete = delete_mock
    sys.argv = 'vrm vos://cadc.nrc.ca~vault/TEST/test.txt'.split()
    vrm()
    delete_mock.assert_called_once()

    # happy path, isfile
    mock_node.islink.return_value = False
    client_mock.return_value.get_node.return_value = mock_node
    isfile_mock = MagicMock()
    isfile_mock.return_value.isfile.return_value = True
    client_mock.return_value.isfile = isfile_mock
    sys.argv = 'vrm vos://cadc.nrc.ca/TEST/test.txt'.split()
    vrm()
    isfile_mock.assert_called_once()

    # not a vos node
    sys.argv = 'vrm ad://cadc.nrc.ca/TEST/'.split()
    with patch('sys.stderr', new_callable=StringIO) as stderr_mock:
        with pytest.raises(Exception):
            vrm()
    assert 'ERROR:: ad resource name not found in the vos config file\n' == \
           stderr_mock.getvalue()

    # isdir
    mock_node.isdir.return_value = True
    client_mock.return_value.get_node.return_value = mock_node
    sys.argv = 'vrm vos://cadc.nrc.ca/TEST/'.split()
    with patch('sys.stderr', new_callable=StringIO) as stderr_mock:
        with pytest.raises(Exception):
            vrm()
    assert 'is a directory' in stderr_mock.getvalue()

    # not a directory
    client_mock.return_value.isdir.return_value = False
    sys.argv = 'vrm vos://cadc.nrc.ca/TEST/'.split()
    with patch('sys.stderr', new_callable=StringIO) as stderr_mock:
        with pytest.raises(Exception):
            vrm()
    assert 'is not a directory' in stderr_mock.getvalue()


@patch('sys.exit', Mock(side_effect=[MyExitError]))
@patch('vos.storage_inventory.Client')
def test_delete_files(client_mock):
    """Test the delete_files function in vrm"""

    # happy path
    delete_mock = MagicMock()
    client_mock.return_value.delete = delete_mock
    sys.argv = \
        'vrm --resource-id ivo:/cadc.nrc.ca/tbd/minoc ad:TEST/test.txt'.split()
    vrm()
    delete_mock.assert_called_once()

    # files end with '/'
    client_mock.return_value.delete_files.return_value = None
    sys.argv = 'vrm --resource-id ivo:/cadc.nrc.ca/tbd/minoc ad:TEST/'.split()
    with patch('sys.stderr', new_callable=StringIO) as stderr_mock:
        with pytest.raises(Exception):
            vrm()
    assert 'ERROR:: ad:TEST/ is a directory\n' == stderr_mock.getvalue()

    # deleting a local file is not supported
    client_mock.return_value.isdir.return_value = False
    client_mock.return_value.delete_files.return_value = None
    sys.argv = 'vrm --resource-id ivo:/cadc.nrc.ca/tbd/minoc ' \
               './TEST/test.txt'.split()
    with patch('sys.stderr', new_callable=StringIO) as stderr_mock:
        with pytest.raises(Exception):
            vrm()
    assert 'not a valid storage file' in stderr_mock.getvalue()
