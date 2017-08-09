# Test the NodeCache class
import unittest
import sys
import logging
from vos.commonparser import CommonParser
from vos.commonparser import set_logging_level_from_args
from vos.version import version
from mock import patch, Mock


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
        self.assertEquals(version, common_parser.version)
        self.assertEquals(logging.WARNING, logging.getLogger('root').level)

        # Remove all handlers associated with the root logger object.
        for handler in logging.root.handlers[:]:
            logging.root.removeHandler(handler)
        common_parser = CommonParser()
        sys.argv = ['myapp', '-d']
        args = common_parser.parse_args()
        set_logging_level_from_args(args)
        self.assertEquals(logging.DEBUG, logging.getLogger().level)
        self.assertEquals(version, common_parser.version)

        common_parser = CommonParser()
        sys.argv = ['myapp', '--version']
        with patch('vos.commonparser.sys.exit', Mock()):
            common_parser.parse_args()


def run():
    suite1 = unittest.TestLoader().loadTestsFromTestCase(TestCommonParser)
    all_tests = unittest.TestSuite([suite1])
    return unittest.TextTestRunner(verbosity=2).run(all_tests)

if __name__ == "__main__":
    run()
