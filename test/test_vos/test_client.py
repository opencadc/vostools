import httplib
import unittest

from hamcrest import assert_that, equal_to
from mock import Mock, ANY

import vos

TEST_ENDPOINT = "http://www.testendpoint.ca"

TEST_URI_1 = "vos://naming_authority/mydata/file1"
TEST_URI_2 = "vos://cadc.nrc.ca!vospace/mydata/file1"
TEST_VOSPACE = "vos:jkavelaars"

class ClientTest(unittest.TestCase):
    def setUp(self):
        self.conn = Mock(spec=vos.Connection)

        # Set certFile to blank because person running this may or may not
        # have a certification file.
        self.undertest = vos.Client(vospace_certfile="", conn=self.conn)

    def test_open_basic_vofile(self):
        vofile = self.undertest.open(TEST_URI_1)
        assert_that(vofile.name, equal_to("file1"))

    def test_getNodeURL_viewdata(self):
        self.undertest.transfer = Mock(return_value=TEST_ENDPOINT)

        url = self.undertest.get_node_url(TEST_URI_1, view="data")
        assert_that(url, equal_to(TEST_ENDPOINT))
        self.undertest.transfer.assert_called_with(TEST_URI_1, "pullFromVoSpace")

        url = self.undertest.get_node_url(TEST_URI_1, view="data", method="PUT")
        assert_that(url, equal_to(TEST_ENDPOINT))
        self.undertest.transfer.assert_called_with(TEST_URI_1, "pushToVoSpace")

    def test_getNodeURL_cadcshortcut(self):
        # Create client with different params for this test
        shortcut_url = "shortcut_url"

        http_con = Mock(spec=httplib.HTTPConnection)
        http_response = Mock(status=303, spec=httplib.HTTPResponse)
        http_response.getheader.return_value = shortcut_url
        http_con.getresponse.return_value = http_response

        conn = Mock(spec=vos.Connection)
        conn.get_connection.return_value = http_con

        self.undertest = vos.Client(vospace_certfile="", conn=conn, cadc_short_cut=True)
        self.undertest.transfer = Mock(return_value=TEST_ENDPOINT)

        url = self.undertest.get_node_url(TEST_URI_2, view="data")
        assert_that(not self.undertest.transfer.called)
        http_response.getheader.assert_called_once_with("Location", ANY)
        assert_that(url, equal_to(shortcut_url))

    def test_getNodeURL_cutout(self):
        self.undertest.transfer = Mock(return_value="http://www.cadc.hia.nrc.gc.ca"
                                                    "/data/pub/CFHT/1616220")

        uri = "vos://cadc.nrc.ca~vospace/OSSOS/dbimages/1616220/1616220o.fits.fz"
        url = self.undertest.get_node_url(uri, view="cutout",
                                        cutout="[1][100:300,200:400]")

        assert_that(url,
                    equal_to("http://www.cadc.hia.nrc.gc.ca/data/pub/CFHT/1616220"
                             "?cutout=[1][100:300,200:400]"))

    def test_getNodeURL_cutout_with_runid(self):
        self.undertest.transfer = Mock(return_value="http://www.cadc.hia.nrc.gc.ca"
                                                    "/data/pub/CFHT/1616220?RUNID=abc123")

        uri = "vos://cadc.nrc.ca~vospace/OSSOS/dbimages/1616220/1616220o.fits.fz"
        url = self.undertest.get_node_url(uri, view="cutout",
                                        cutout="[1][100:300,200:400]")

        assert_that(url,
                    equal_to("http://www.cadc.hia.nrc.gc.ca/data/pub/CFHT/1616220"
                             "?RUNID=abc123"
                             "&cutout=[1][100:300,200:400]"))

if __name__ == '__main__':
    unittest.main()
