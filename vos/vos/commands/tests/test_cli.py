from __future__ import (absolute_import, division, print_function,
                        unicode_literals)

import sys
import os
import unittest
from six import StringIO
from mock import Mock, patch
from vos import commands as cmds

THIS_DIR = os.path.dirname(os.path.realpath(__file__))
TESTDATA_DIR = os.path.join(THIS_DIR, 'data')


class MyExitError(Exception):

    def __init__(self):
        self.message = "MyExitError"


# to capture the output of executing a command, sys.exit is patched to
# throw an MyExitError exception. The number of such exceptions is based
# on the number of commands and the number of times they are invoked
outputs = [MyExitError] * (len(cmds.__all__) + 3)


class TestCli(unittest.TestCase):
    """
    Basic tests of the command line interface for various vos commands.
    For each command it tests the invocation of the command without arguments
    and with the --help flag against a known output.
    """

    @patch('sys.exit', Mock(side_effect=outputs))
    def test_cli_noargs(self):
        """Test the invocation of a command without arguments"""

        # get a list of all available commands
        for cmd in cmds.__all__:
            with patch('sys.stdout', new_callable=StringIO) as stdout_mock:
                with open(os.path.join(TESTDATA_DIR, '{}.txt'.format(cmd)),
                          'r') as f:
                    usage = f.read()
                sys.argv = '{}'.format(cmd).split()
                with self.assertRaises(MyExitError):
                    cmd_attr = getattr(cmds, cmd)
                    cmd_attr()
                    self.assertTrue(stdout_mock.getvalue().contains(usage))

    @patch('sys.exit', Mock(side_effect=outputs))
    def test_cli_help_arg(self):
        """Test the invocation of a command with --help argument"""

        # get a list of all available commands
        for cmd in cmds.__all__:
            with patch('sys.stdout', new_callable=StringIO) as stdout_mock:
                with open(os.path.join(
                        TESTDATA_DIR, 'help_{}.txt'.format(cmd)), 'r') as f:
                    usage = f.read()
                sys.argv = '{}'.format(cmd).split()
                with self.assertRaises(MyExitError):
                    cmd_attr = getattr(cmds, cmd)
                    cmd_attr()
                    self.assertEqual(usage, stdout_mock.getvalue())
