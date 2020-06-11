# Test the vos module
from six.moves import builtins
import requests
import unittest2 as unittest
from mock import Mock, MagicMock, patch
import os

from vos import vos, Connection, vosconfig

# To run individual tests, set the value of skipTests to True, and comment
# out the @unittest.skipIf line at the top of the test to be run.
skipTests = False


class Object(builtins.object):
    pass


class TestVOFile(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        super(TestVOFile, cls).setUpClass()
        # make sure we are using the default config file
        os.environ['VOSPACE_CONFIG_FILE'] = vosconfig._DEFAULT_CONFIG_PATH

    def __init__(self, *args, **kwargs):
        super(TestVOFile, self).__init__(*args, **kwargs)
        self.responses = []
        self.headers = {
            ('Content-MD5', None): 12345,
            ('Content-Length', 10): 10,
            ('X-CADC-Content-Length', 0): 10,
        }

    @patch.object(Connection, 'get_connection')
    @unittest.skipIf(skipTests, "Individual tests")
    def test_retry_successfull(self, mock_get_connection):
        # this tests the read function when first HTTP request returns a
        # 503 but the second one is successfull and returns a 200

        # mock the 503 response
        mockHttpResponse503 = Mock(name="HttpResponse503")
        mockHttpResponse503.getheader.return_value = 1
        mockHttpResponse503.status = 503
        mockHttpResponse503.read.return_value = "Fail"
        mockHttpResponse503.len.return_value = 10

        # mock the 200 response
        mockHttpResponse200 = Mock(name="HttpResponse200")
        mockHttpResponse200.getheader.return_value = 1
        mockHttpResponse200.status = 200
        mockHttpResponse200.read.return_value = "Testing"
        mockHttpResponse200.len.return_value = 10
        conn = Connection(resource_id='ivo://cadc.nrc.ca/vault')
        mockHttpRequest = Mock(name="HttpRequest")

        # set a 503 response first followed by a 200 response
        mockHttpRequest.getresponse = MagicMock(name="getresponse mock")
        generator = mockHttpRequest.getresponse.iter.return_value
        iterator = iter([mockHttpResponse503, mockHttpResponse200])
        generator.__iter__.return_value = iterator
        conn.get_connection.return_value = mockHttpRequest
        vos.VOFile(["Some URL"], conn, "GET")

        # check the response
        # TODO self.assertEqual("Testing", vofile.read(),
        # "Incorrect returned value from read")
        # mockHttpResponse503.getheader.assert_called_with("Retry-After", 5)
        # 1 retry -> getheader in HttpResponse503 was called 2 times in the
        # order shown below.
        # TODO call is only available in mock 1.0. Uncomment when this
        # version available
        # expected = [call('Content-Length', 0), call('Retry-After', 5)]
        # self.assertEquals( expected,
        # mockHttpResponse503.getheader.call_args_list)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_fail_max_retry(self):
        # this tests the read function when HTTP requests keep returning 503s
        # read call fails when it reaches the maximum number of retries, in
        # this case set to 2

        # mock the 503 responses
        mock_resp = Mock(name="503 resp")
        mock_resp.status_code = 503
        mock_resp.content = "Testing"

        headers = {'Content-Length': 10,
                   'X-CADC-Content-Length': 5, 'Retry-After': 4}

        def getheader(name, default):
            return headers[name]

        mock_resp.headers = MagicMock()
        mock_resp.headers.get.side_effect = getheader
        mock_resp.text = 'Try again later'

        conn = Connection(resource_id='ivo://cadc.nrc.ca/vault')
        conn.session.send = Mock(return_value=mock_resp)
        vofile = vos.VOFile(["Some URL"], conn, "GET")
        req = requests.Request("GET", "http://some/url")
        vofile.request = req.prepare()

        # set number of retries to 1 and check the OSError was thrown
        vofile.maxRetries = 1
        with self.assertRaises(OSError):
            vofile.read()
        mock_resp.headers.get.assert_called_with('Retry-After', 5)

        # 1 retry -> getheader in HttpResponse was called 4 times in the
        # order shown below.
        # TODO call is only available in mock 1.0. Uncomment when this version
        # available
        # expected = [call('Content-Length', 10), call('Retry-After', 3)]
        # self.assertEquals( expected,
        # mock_resp.mock_headers.get.call_args_list)

    @patch.object(Connection, 'get_connection')
    @unittest.skipIf(skipTests, "Individual tests")
    def test_retry_412_successfull(self, mock_get_connection):
        # this tests the read function when first HTTP request returns a
        # 412 but the second one
        # is successful and returns a 200

        # mock the 412 response
        mockHttpResponse412 = Mock(name="HttpResponse412")
        mockHttpResponse412.getheader.return_value = 1
        mockHttpResponse412.status = 412
        mockHttpResponse412.read.return_value = "Fail"

        # mock the 200 response
        mockHttpResponse200 = Mock(name="HttpResponse200")
        mockHttpResponse200.getheader.return_value = 1
        mockHttpResponse200.status = 200
        mockHttpResponse200.read.return_value = "Testing"
        conn = Connection(resource_id='ivo://cadc.nrc.ca/vault')
        mockHttpRequest = Mock(name="HttpRequest")

        # set a 412 response first followed by a 200 response
        mockHttpRequest.getresponse = MagicMock(
            side_effect=[mockHttpResponse412, mockHttpResponse200])
        conn.get_connection.return_value = mockHttpRequest
        vofile = vos.VOFile(["Some URL"], conn, "GET")
        vofile.currentRetryDelay = 2

        # check the response
        # self.assertEqual("Testing", vofile.read(), "Incorrect returned value
        # from read")
        # 1 retry -> getheader in HttpResponse412 was called once as follows.
        # TODO call is only available in mock 1.0. Uncomment when this version
        # available
        # expected = [call('Content-Length', 0)]
        # self.assertEquals( expected,
        # mockHttpResponse412.getheader.call_args_list)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_multiple_urls(self):

        transfer_urls = ['http://url1.ca', 'http://url2.ca', 'http://url3.ca']

        # mock the 200 response
        mock_resp_200 = requests.Response()
        mock_resp_200.status_code = 200
        mock_resp_200.headers = {'Content-Length': 10}
        mock_resp_200.raw = Mock(read=Mock(return_value="abcd"))

        # mock the 404 response
        mock_resp_404 = requests.Response()
        mock_resp_404.status_code = 404
        mock_resp_404.headers = {'Content-Length': 10}

        # mock the 503 response
        mock_resp_503 = requests.Response()
        mock_resp_503.status_code = 503
        mock_resp_503.headers = {'Content-Length': 10, 'Content-MD5': 12345,
                                 'Retry-After': 1}

        conn = Connection(resource_id='ivo://cadc.nrc.ca/vault')

        # test successful - use first url
        self.responses = [mock_resp_200]
        vofile = vos.VOFile(transfer_urls, conn, "GET")
        with patch('vos.vos.net.ws.Session.send',
                   Mock(side_effect=self.responses)):
            vofile.read()
        assert(vofile.url == transfer_urls[0])
        assert(vofile.urlIndex == 0)
        assert(len(vofile.URLs) == 3)

        # test first url busy
        self.responses = [mock_resp_503, mock_resp_200]
        vofile = vos.VOFile(transfer_urls, conn, "GET")
        with patch('vos.vos.net.ws.Session.send',
                   Mock(side_effect=self.responses)):
            vofile.read()
        assert(vofile.url == transfer_urls[1])
        assert(vofile.urlIndex == 1)
        assert(len(vofile.URLs) == 3)

        # test first url error - ignored internally, second url busy,
        # third url works
        # test 404 which raises OSError
        self.responses = [mock_resp_404, mock_resp_503, mock_resp_200]
        vofile = vos.VOFile(transfer_urls, conn, "GET")
        # with self.assertRaises(exceptions.NotFoundException) as ex:
        with patch('vos.vos.net.ws.requests.Session.send',
                   Mock(side_effect=self.responses)):
            vofile.read()
        assert(vofile.url == transfer_urls[2])
        assert(vofile.urlIndex == 2)
        assert(len(vofile.URLs) == 3)

        # all urls busy first time, first one successful second time
        self.responses = [mock_resp_503, mock_resp_503,
                          mock_resp_503, mock_resp_200]
        vofile = vos.VOFile(transfer_urls, conn, "GET")
        with patch('vos.vos.net.ws.requests.Session.send',
                   Mock(side_effect=self.responses)):
            vofile.read()
        assert(vofile.url == transfer_urls[2])
        assert(vofile.urlIndex == 2)
        assert(len(vofile.URLs) == 3)

    @unittest.skipIf(skipTests, "Individual tests")
    @patch.object(Connection, 'get_connection')
    def test_checkstatus(self, mock_get_connection):
        # Verify the md5sum and size are extracted from the HTTP header
        conn = Connection(resource_id='ivo://cadc.nrc.ca/vault')
        # test successful - use first url
        vofile = vos.VOFile(None, conn, "GET")
        mock_resp = Object
        mock_resp.status_code = 200
        mock_resp.headers = {
            'Content-MD5': 12345, 'Content-Length': 10,
            'X-CADC-Content-Length': 10
        }
        vofile.resp = mock_resp

        self.assertTrue(vofile.checkstatus())
        self.assertEqual(vofile.get_file_info(), (10, 12345))

    def side_effect(self, foo, stream=True, verify=False):
        # removes first in the list
        # mock the 200 response
        response = self.responses.pop(0)
        return response

    def get_headers(self, arg):
        return self.headers[arg]


def run():
    suite = unittest.TestLoader().loadTestsFromTestCase(TestVOFile)
    return unittest.TextTestRunner(verbosity=2).run(suite)
