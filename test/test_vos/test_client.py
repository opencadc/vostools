import unittest

from hamcrest import assert_that, equal_to
from mock import Mock

import vos

TEST_ENDPOINT = "http://www.testendpoint.ca"

TEST_URI_1 = "vos://naming_authority/mydata/file1"
TEST_URI_2 = "vos://cadc.nrc.ca!vospace/mydata/file1"


class ClientTest(unittest.TestCase):
    def setUp(self):
        self.conn = Mock(spec=vos.Connection)

        # Set certFile to None because person running this may or may not
        # have a certification file.
        self.undertest = vos.Client(certFile=None, conn=self.conn)

    def test_open_basic_vofile(self):
        vofile = self.undertest.open(TEST_URI_1)
        assert_that(vofile.name, equal_to("file1"))

    def test_getNodeURL_viewdata(self):
        self.undertest.transfer = Mock(return_value=TEST_ENDPOINT)

        url = self.undertest.getNodeURL(TEST_URI_1, view="data")
        assert_that(url, equal_to(TEST_ENDPOINT))
        self.undertest.transfer.assert_called_with(TEST_URI_1, "pullFromVoSpace")

        url = self.undertest.getNodeURL(TEST_URI_1, view="data", method="PUT")
        assert_that(url, equal_to(TEST_ENDPOINT))
        self.undertest.transfer.assert_called_with(TEST_URI_1, "pushToVoSpace")

    def test_getNodeURL_cadcshortcut(self):
        # Create client with different params for this test
        self.undertest = vos.Client(certFile=None, conn=self.conn, cadc_short_cut=True)
        self.undertest.transfer = Mock(return_value=TEST_ENDPOINT)

        url = self.undertest.getNodeURL(TEST_URI_2, view="data")
        assert_that(not self.undertest.transfer.called)
        assert_that(url,
                    equal_to("http://%s/%s/vospace/mydata/file1" % (vos.vos.SERVER, vos.Client.DWS)))

    def test_getNodeURL_cutout(self):
        self.undertest.transfer = Mock(return_value="http://www.cadc.hia.nrc.gc.ca"
                                                    "/data/pub/CFHT/1616220")

        uri = "vos://cadc.nrc.ca~vospace/OSSOS/dbimages/1616220/1616220o.fits.fz"
        url = self.undertest.getNodeURL(uri, view="cutout",
                                        cutout="[1][100:300,200:400]")

        assert_that(url,
                    equal_to("http://www.cadc.hia.nrc.gc.ca/data/pub/CFHT/1616220"
                             "?cutout=[1][100:300,200:400]"))

    def test_getNodeURL_cutout_with_runid(self):
        self.undertest.transfer = Mock(return_value="http://www.cadc.hia.nrc.gc.ca"
                                                    "/data/pub/CFHT/1616220?RUNID=abc123")

        uri = "vos://cadc.nrc.ca~vospace/OSSOS/dbimages/1616220/1616220o.fits.fz"
        url = self.undertest.getNodeURL(uri, view="cutout",
                                        cutout="[1][100:300,200:400]")

        assert_that(url,
                    equal_to("http://www.cadc.hia.nrc.gc.ca/data/pub/CFHT/1616220"
                             "?RUNID=abc123"
                             "&cutout=[1][100:300,200:400]"))

if __name__ == '__main__':
    unittest.main()
