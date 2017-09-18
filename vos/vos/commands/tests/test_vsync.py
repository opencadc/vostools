from __future__ import (absolute_import, division, print_function,
                        unicode_literals)

import sys
import unittest
from six import StringIO
from mock import patch, Mock, call
from vos.commands import vsync

# The following is a temporary workaround for Python issue 25532 (https://bugs.python.org/issue25532)
call.__wrapped__ = None


class MyExitError(Exception):

    def __init__(self):
        self.message = "MyExitError"

class TestVsync(unittest.TestCase):
    """Test the vsync script
    """

    @patch('sys.exit', Mock(side_effect=[MyExitError, MyExitError, MyExitError,
                                         MyExitError, MyExitError, MyExitError]))
    def test_all(self):
        """Test basic operation of the vsync"""

        with patch('sys.stdout', new_callable=StringIO) as stdout_mock:
            usage = "vsync usage"
            sys.argv = 'vsync --help'.split()
            with self.assertRaises(MyExitError):
                vsync()
            #self.assertEqual(usage, stdout_mock.getvalue()) TODO

    #TODO ad: add complete set off tests when vsync is reworked
