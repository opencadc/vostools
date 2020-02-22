from __future__ import (absolute_import, division, print_function,
                        unicode_literals)

import sys
import os
import unittest
from mock import patch, MagicMock
from vos import commands as cmds
from vos.commonparser import CommonParser
from vos.commands.vrm import delete_nodes, delete_files

THIS_DIR = os.path.dirname(os.path.realpath(__file__))
TESTDATA_DIR = os.path.join(THIS_DIR, 'data')


class MyExitError(Exception):

    def __init__(self):
        self.message = "MyExitError"


# to capture the output of executing a command, sys.exit is patched to
# throw an MyExitError exception. The number of such exceptions is based
# on the number of commands and the number of times they are invoked
outputs = [MyExitError] * (len(cmds.__all__) + 3)


class TestVRM(unittest.TestCase):
    """
    Basic tests of the vrm command.
    """

    @patch('vos.vos.Client')
    def test_delete_nodes(self, client_mock):
        """Test the delete_nodes function in vrm"""

        # happy path, islink
        mock_node = MagicMock()
        mock_node.islink.return_value = True
        client_mock.return_value.get_node.return_value = mock_node
        delete_mock = MagicMock()
        client_mock.return_value.delete = delete_mock
        sys.argv = ['vrm', 'vos://cadc.nrc.ca/TEST/test.txt']
        parser = CommonParser()
        parser.add_argument(
            "--resource-id", default=None,
            help="resource ID of the Storage Inventory service to be used")
        parser.add_argument(
            'source',
            help='file, dataNode or linkNode to delete from VOSpace',
            nargs='+')
        args = parser.parse_args()
        delete_nodes(args)
        delete_mock.assert_called_once()

        # happy path, isfile
        mock_node.islink.return_value = False
        client_mock.return_value.get_node.return_value = mock_node
        isfile_mock = MagicMock()
        isfile_mock.return_value.isfile.return_value = True
        client_mock.return_value.isfile = isfile_mock
        sys.argv = ['vrm', 'vos://cadc.nrc.ca/TEST/test.txt']
        parser = CommonParser()
        parser.add_argument(
            "--resource-id", default=None,
            help="resource ID of the Storage Inventory service to be used")
        parser.add_argument(
            'source',
            help='file, dataNode or linkNode to delete from VOSpace',
            nargs='+')
        args = parser.parse_args()
        delete_nodes(args)
        isfile_mock.assert_called_once()

        # not a vos node
        sys.argv = ['vrm', 'ad://cadc.nrc.ca/TEST/']
        parser = CommonParser()
        parser.add_argument(
            "--resource-id", default=None,
            help="resource ID of the Storage Inventory service to be used")
        parser.add_argument(
            'source',
            help='file, dataNode or linkNode to delete from VOSpace',
            nargs='+')
        args = parser.parse_args()
        with self.assertRaises(Exception) as ex:
            delete_nodes(args)
        self.assertTrue('not a valid VOSpace' in str(ex.exception))

        # isdir
        mock_node.isdir.return_value = True
        client_mock.return_value.get_node.return_value = mock_node
        sys.argv = ['vrm', 'vos://cadc.nrc.ca/TEST/']
        parser = CommonParser()
        parser.add_argument(
            "--resource-id", default=None,
            help="resource ID of the Storage Inventory service to be used")
        parser.add_argument(
            'source',
            help='file, dataNode or linkNode to delete from VOSpace',
            nargs='+')
        args = parser.parse_args()
        with self.assertRaises(Exception) as ex:
            delete_nodes(args)
        self.assertTrue('is a directory' in str(ex.exception))

        # not a directory
        client_mock.return_value.isdir.return_value = False
        sys.argv = ['vrm', 'vos://cadc.nrc.ca/TEST/']
        parser = CommonParser()
        parser.add_argument(
            "--resource-id", default=None,
            help="resource ID of the Storage Inventory service to be used")
        parser.add_argument(
            'source',
            help='file, dataNode or linkNode to delete from VOSpace',
            nargs='+')
        args = parser.parse_args()
        with self.assertRaises(Exception) as ex:
            delete_nodes(args)
        self.assertTrue('is not a directory' in str(ex.exception))

    @patch('vos.storage_inventory.Client')
    def test_delete_files(self, client_mock):
        """Test the delete_files function in vrm"""

        # happy path
        client_mock.return_value.isdir.return_value = False
        delete_mock = MagicMock()
        client_mock.return_value.delete = delete_mock
        sys.argv = ['vrm', '--resource-id',
                    'ivo:/cadc.nrc.ca/tbd/minoc', 'ad:TEST/test.txt']
        parser = CommonParser()
        parser.add_argument(
            "--resource-id", default=None,
            help="resource ID of the Storage Inventory service to be used")
        parser.add_argument(
            'source',
            help='file, dataNode or linkNode to delete from VOSpace',
            nargs='+')
        args = parser.parse_args()
        delete_files(args)
        delete_mock.assert_called_once()

        # not a storage file
        client_mock.return_value.delete_files.return_value = None
        sys.argv = ['vrm', '--resource-id',
                    'ivo:/cadc.nrc.ca/tbd/minoc', 'vos:TEST/test.txt']
        parser = CommonParser()
        parser.add_argument(
            "--resource-id", default=None,
            help="resource ID of the Storage Inventory service to be used")
        parser.add_argument(
            'source',
            help='file, dataNode or linkNode to delete from VOSpace',
            nargs='+')
        args = parser.parse_args()
        with self.assertRaises(Exception) as ex:
            delete_files(args)
        self.assertTrue('not a valid storage' in str(ex.exception))

        # files end with '/'
        client_mock.return_value.delete_files.return_value = None
        sys.argv = ['vrm', '--resource-id',
                    'ivo:/cadc.nrc.ca/tbd/minoc', 'ad:TEST/']
        parser = CommonParser()
        parser.add_argument(
            "--resource-id", default=None,
            help="resource ID of the Storage Inventory service to be used")
        parser.add_argument(
            'source',
            help='file, dataNode or linkNode to delete from VOSpace',
            nargs='+')
        args = parser.parse_args()
        with self.assertRaises(Exception) as ex:
            delete_files(args)
        self.assertTrue('not a valid storage file' in str(ex.exception))

        # isdir
        client_mock.return_value.isdir.return_value = True
        client_mock.return_value.delete_files.return_value = None
        sys.argv = ['vrm', '--resource-id',
                    'ivo:/cadc.nrc.ca/tbd/minoc', 'ad:TEST/test.txt']
        parser = CommonParser()
        parser.add_argument(
            "--resource-id", default=None,
            help="resource ID of the Storage Inventory service to be used")
        parser.add_argument(
            'source',
            help='file, dataNode or linkNode to delete from VOSpace',
            nargs='+')
        args = parser.parse_args()
        with self.assertRaises(Exception) as ex:
            delete_files(args)
        self.assertTrue('a directory' in str(ex.exception))

        # wild card in file URI is not supported
        client_mock.return_value.isdir.return_value = False
        client_mock.return_value.delete_files.return_value = None
        sys.argv = ['vrm', '--resource-id',
                    'ivo:/cadc.nrc.ca/tbd/minoc', 'ad:TEST/*.txt']
        parser = CommonParser()
        parser.add_argument(
            "--resource-id", default=None,
            help="resource ID of the Storage Inventory service to be used")
        parser.add_argument(
            'source',
            help='file, dataNode or linkNode to delete from VOSpace',
            nargs='+')
        args = parser.parse_args()
        with self.assertRaises(Exception) as ex:
            delete_files(args)
        self.assertTrue('Wild card not supported' in str(ex.exception))

        # deleting a local file is not supported
        client_mock.return_value.isdir.return_value = False
        client_mock.return_value.delete_files.return_value = None
        sys.argv = ['vrm', '--resource-id',
                    'ivo:/cadc.nrc.ca/tbd/minoc', './TEST/test.txt']
        parser = CommonParser()
        parser.add_argument(
            "--resource-id", default=None,
            help="resource ID of the Storage Inventory service to be used")
        parser.add_argument(
            'source',
            help='file, dataNode or linkNode to delete from VOSpace',
            nargs='+')
        args = parser.parse_args()
        with self.assertRaises(Exception) as ex:
            delete_files(args)
        self.assertTrue('not a valid storage file' in str(ex.exception))
