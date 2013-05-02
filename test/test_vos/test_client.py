import unittest

from hamcrest import assert_that, equal_to
from mock import Mock

import vos

TEST_URI = "vos://naming_authority/mydata/file1"


class ClientTest(unittest.TestCase):
    def setUp(self):
        self.conn = Mock(spec=vos.Connection)

        self.undertest = vos.Client(conn=self.conn)

    def test_open_basic_vofile(self):
        vofile = self.undertest.open(TEST_URI)
        assert_that(vofile.name, equal_to("file1"))

    def test_getNodeURL_viewdata(self):
        test_endpoint = "http://www.testendpoint.ca"
        self.undertest.transfer = Mock(return_value=test_endpoint)

        url = self.undertest.getNodeURL(TEST_URI, view="data")
        assert_that(url, equal_to(test_endpoint))
        self.undertest.transfer.assert_called_with(TEST_URI, "pullFromVoSpace")

        url = self.undertest.getNodeURL(TEST_URI, view="data", method="PUT")
        assert_that(url, equal_to(test_endpoint))
        self.undertest.transfer.assert_called_with(TEST_URI, "pushToVoSpace")


if __name__ == '__main__':
    unittest.main()
