import unittest

from hamcrest import assert_that, equal_to

import vos.vos as vos


class UrlparseTest(unittest.TestCase):
    def test_urlparse(self):
        parts = vos.URLParser("http://www.test.com/path")
        assert_that(parts.scheme, equal_to("http"))
        assert_that(parts.netloc, equal_to("www.test.com"))
        assert_that(parts.path, equal_to("/path"))

    def test_urlparse_naming_authority(self):
        parts = vos.URLParser("https://cadc.nrc.ca!vospace/path")
        assert_that(parts.scheme, equal_to("https"))
        assert_that(parts.netloc, equal_to("cadc.nrc.ca!vospace"))
        assert_that(parts.path, equal_to("/path"))

    def test_urlparse_multi_depth_path(self):
        parts = vos.URLParser("http://cadc.nrc.ca!vospace/multi/depth/path.ext")
        assert_that(parts.scheme, equal_to("http"))
        assert_that(parts.netloc, equal_to("cadc.nrc.ca!vospace"))
        assert_that(parts.path, equal_to("/multi/depth/path.ext"))


if __name__ == '__main__':
    unittest.main()
