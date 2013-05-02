import unittest

from hamcrest import assert_that, equal_to
from mock import Mock

import vos

TEST_URI = "vos://naming_authority/mydata/file1"


class ClientTest(unittest.TestCase):
    def setUp(self):
        self.conn = Mock()
        self.undertest = vos.Client(conn=self.conn)

    def test_open_basic_vofile(self):
        vofile = self.undertest.open(TEST_URI)
        assert_that(vofile.name, equal_to("file1"))


if __name__ == '__main__':
    unittest.main()
