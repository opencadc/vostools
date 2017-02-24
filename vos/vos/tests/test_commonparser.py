# Test the NodeCache class

import unittest
import sys
import logging

from vos.commonparser import CommonParser
from vos.version import version
from mock import patch, Mock

class TestCommonParser(unittest.TestCase):
    """Test the CommonParser class.
    """

    def test_all(self):
        """Test CommonParser."""
        commonParser = CommonParser()
        # hijack the arguments
        sys.argv = ['myapp', '-w']
        commonParser.process_informational_options()
        self.assertEquals(logging.WARNING, commonParser.log_level)
        self.assertEquals(version, commonParser.version)
        self.assertEquals(logging.WARNING, logging.getLogger().level)

        # Remove all handlers associated with the root logger object.
        for handler in logging.root.handlers[:]:
            logging.root.removeHandler(handler)
        commonParser = CommonParser()
        sys.argv = ['myapp', '-d']
        commonParser.process_informational_options()
        self.assertEquals(logging.DEBUG, commonParser.log_level)
        self.assertEquals(version, commonParser.version)
        
        sys.argv = ['myapp', '--version']
        with patch('vos.commonparser.sys.exit', Mock()):
            commonParser.process_informational_options()


def run():
    suite1 = unittest.TestLoader().loadTestsFromTestCase(TestCommonParser)
    allTests = unittest.TestSuite([suite1])
    return unittest.TextTestRunner(verbosity=2).run(allTests)

if __name__ == "__main__":
    run()
