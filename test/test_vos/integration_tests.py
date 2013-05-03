"""
Integration level tests.  Requires internet connection to access
real vospace data.
"""
import unittest
import cStringIO

from astropy.io import fits
from hamcrest import assert_that, equal_to, has_length

import vos


class IntegrationTest(unittest.TestCase):
    def test_open(self):
        client = vos.Client()
        uri = "vos://cadc.nrc.ca~vospace/drusk/testdata.fits"
        vofile = client.open(uri, view="data")

        rawdata = cStringIO.StringIO(vofile.read())
        hdulist = fits.open(rawdata)

        assert_that(hdulist, has_length(2))

        assert_that(hdulist[0].header["FILENAME"],
                    equal_to("u5780205r_cvt.c0h"))

        assert_that(hdulist[0].data.shape, equal_to((4, 200, 200)))
        assert_that(hdulist[1].data.shape, equal_to((4,)))

    def test_open_cutout(self):
        client = vos.Client()
        uri = "vos://cadc.nrc.ca~vospace/drusk/testdata.fits"
        vofile = client.open(uri, view="cutout", cutout="[0][80:140,50:120]")

        rawdata = cStringIO.StringIO(vofile.read())
        hdulist = fits.open(rawdata)

        assert_that(hdulist, has_length(1))

        assert_that(hdulist[0].header["FILENAME"],
                    equal_to("u5780205r_cvt.c0h"))

        # NOTE: Pixel range is inclusive
        assert_that(hdulist[0].data.shape, equal_to((4, 71, 61)))


if __name__ == '__main__':
    unittest.main()
