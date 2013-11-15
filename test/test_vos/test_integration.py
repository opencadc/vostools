"""
Integration level tests.  Requires internet connection to access
real vospace data.
"""
import tempfile
import unittest
import cStringIO

from astropy.io import fits

from hamcrest import assert_that, equal_to, has_length

import vos

data = "This is some data for the test."

class IntegrationTest(unittest.TestCase):
    def test_open(self):
        client = vos.Client()
        filename = tempfile.NamedTemporaryFile()
        uri = client.rootNode+'/'+filename
        vofile = client.open(uri)
        vofile.write(data)
        vofile.close()
        vofile = client.open(uri, view="data")
        assert data==vofile.read()

if __name__ == '__main__':
    unittest.main()
