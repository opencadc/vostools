import unittest
import sys
import logging
from vos.commonparser import CommonParser
from vos.commonparser import set_logging_level_from_args, exit_on_exception
from vos.version import version
from mock import patch, Mock
from six import StringIO


class TestCommonParser(unittest.TestCase):
    """Test the CommonParser class.
    """

    def test_all(self):
        """Test CommonParser."""
        common_parser = CommonParser()
        # hijack the arguments
        sys.argv = ['myapp', '-w']
        args = common_parser.parse_args()
        set_logging_level_from_args(args)
        self.assertEqual(version, common_parser.version)
        self.assertEqual(logging.WARNING, logging.getLogger('root').level)

        # Remove all handlers associated with the root logger object.
        for handler in logging.root.handlers[:]:
            logging.root.removeHandler(handler)
        common_parser = CommonParser()
        sys.argv = ['myapp', '-d']
        args = common_parser.parse_args()
        set_logging_level_from_args(args)
        self.assertEqual(logging.DEBUG, logging.getLogger().level)
        self.assertEqual(version, common_parser.version)

        common_parser = CommonParser()
        sys.argv = ['myapp', '--version']
        with patch('vos.commonparser.sys.exit', Mock()):
            common_parser.parse_args()

    def test_exit_on_exception(self):
        try:
            # Exceptions needs a context, hence raising it
            raise RuntimeError('Test')
        except Exception as e:
            with patch('sys.exit'):
                with patch('sys.stderr', new_callable=StringIO) as stderr_mock:
                    exit_on_exception(e)
            self.assertEqual('ERROR:: Test\n', stderr_mock.getvalue())

            with patch('sys.exit'):
                with patch('sys.stderr', new_callable=StringIO) as stderr_mock:
                    exit_on_exception(e, 'Error message')
            self.assertEqual('ERROR:: Error message\n', stderr_mock.getvalue())
