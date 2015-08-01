# Test the vos module
import unittest2
from vos import vos, Connection
import httplib
from httplib import HTTPResponse
from mock import Mock, MagicMock, patch

class TestVOFile(unittest2.TestCase):

    def __init__(self, *args, **kwargs):
        super(TestVOFile, self).__init__(*args, **kwargs)
        self.responses = []


    @patch.object(Connection,'get_connection')
    def test_retry_successfull(self,mock_get_connection):
        # this tests the read function when first HTTP request returns a 503 but the second one 
        # is successfull and returns a 200

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
        conn = Connection()
        mockHttpRequest = Mock(name="HttpRequest")

        # set a 503 response first followed by a 200 response
        mockHttpRequest.getresponse = MagicMock(name="getresponse mock")
        generator = mockHttpRequest.getresponse.iter.return_value
        iterator = iter([mockHttpResponse503, mockHttpResponse200])
        generator.__iter__.return_value = iterator
        conn.get_connection.return_value = mockHttpRequest
        #vofile = vos.VOFile(["Some URL"], mockConn, "GET")
        vofile = vos.VOFile(["Some URL"], conn, "GET")

        # check the response
        #TODO self.assertEqual("Testing", vofile.read(), "Incorrect returned value from read")
        #mockHttpResponse503.getheader.assert_called_with("Retry-After", 5)
        # 1 retry -> getheader in HttpResponse503 was called 2 times in the order shown below.
	#TODO call is only available in mock 1.0. Uncomment when this version available
        #expected = [call('Content-Length', 0), call('Retry-After', 5)]
        #self.assertEquals( expected, mockHttpResponse503.getheader.call_args_list)

    @patch.object(Connection,'get_connection')
    def test_fail_max_retry(self,mock_get_connection):
        # this tests the read function when HTTP requests keep returning 503s
        # read call fails when it reaches the maximum number of retries, in this case set to 2

        # mock the 503 responses
        mockHttpResponse = Mock(name="HttpResponse")
        mockHttpResponse.getheader.return_value = 1 # try again after 1 sec 
        mockHttpResponse.status = 503
        mockHttpResponse.read.return_value = "Testing"
        conn = Connection()
        mockHttpRequest = Mock(name="HttpRequest")
        mockHttpRequest.getresponse.return_value = mockHttpResponse
        conn.get_connection.return_value = mockHttpRequest
        vofile = vos.VOFile(["Some URL"], conn, "GET")
        # set number of retries to 1 and check the OSError was thrown
        vofile.maxRetries = 1
        with self.assertRaises(OSError) as cm:
            vofile.read()
        mockHttpResponse.getheader.assert_called_with("Retry-After", 5)
        # 1 retry -> getheader in HttpResponse was called 4 times in the order shown below.
	#TODO call is only available in mock 1.0. Uncomment when this version available
        #expected = [call('Content-Length', 0), call('Retry-After', 5), call('Content-Length', 0), call('Retry-After', 5)]
        #self.assertEquals( expected, mockHttpResponse.getheader.call_args_list)

    @patch.object(Connection,'get_connection')
    def test_retry_412_successfull(self,mock_get_connection):
        # this tests the read function when first HTTP request returns a 412 but the second one 
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
        conn = Connection()
        mockHttpRequest = Mock(name="HttpRequest")

        # set a 412 response first followed by a 200 response
        mockHttpRequest.getresponse = MagicMock(side_effect=[mockHttpResponse412, mockHttpResponse200])
        conn.get_connection.return_value = mockHttpRequest
        vofile = vos.VOFile(["Some URL"], conn, "GET")
        vofile.currentRetryDelay = 2

        # check the response
        #self.assertEqual("Testing", vofile.read(), "Incorrect returned value from read")
        # 1 retry -> getheader in HttpResponse412 was called once as follows.
	#TODO call is only available in mock 1.0. Uncomment when this version available
        #expected = [call('Content-Length', 0)]
        #self.assertEquals( expected, mockHttpResponse412.getheader.call_args_list)


    @patch.object(Connection,'get_connection')
    def test_multiple_urls(self,mock_get_connection):
        
        transferURLs = ['http://url1.ca', 'http://url2.ca', 'http://url3.ca']
        # mock the 503 response
        mockHttpResponse503 = Mock(name="HttpResponse503")
        mockHttpResponse503.getheader.return_value = 3
        mockHttpResponse503.status = 503
        mockHttpResponse503.read.return_value = "Try again"

        # mock the 200 response
        mockHttpResponse200 = Mock(name="HttpResponse200")
        mockHttpResponse200.getheader.return_value = 1
        mockHttpResponse200.status = 200
        mockHttpResponse200.read.return_value = "Testing"

        
        # mock the 412 response
        mockHttpResponse404 = Mock(name="HttpResponse404")
        mockHttpResponse404.getheader.return_value = 1
        mockHttpResponse404.status = 404
        mockHttpResponse404.read.return_value = "Fail"                
        
        
        conn = Connection()
        mockHttpRequest = Mock(name="HttpRequest")
        
        # test successful - use first url
        self.responses = [mockHttpResponse200]
        mockHttpRequest.getresponse = Mock(side_effect=self.side_effect)
        conn.get_connection.return_value = mockHttpRequest
        vofile = vos.VOFile(transferURLs, conn, "GET")
        vofile.read()
        assert(vofile.url == transferURLs[0])
        assert(vofile.urlIndex == 0)
        assert(len(vofile.URLs) == 3)
        
        # test first url busy
        self.responses = [mockHttpResponse503, mockHttpResponse200]
        mockHttpRequest.getresponse = Mock(side_effect=self.side_effect)
        conn.get_connection.return_value = mockHttpRequest
        vofile = vos.VOFile(transferURLs, conn, "GET")
        vofile.read()
        assert(vofile.url == transferURLs[1])
        assert(vofile.urlIndex == 1)
        assert(len(vofile.URLs) == 3)
        
        #test first url error - ignored internally, second url busy, third url works
        self.responses = [mockHttpResponse404, mockHttpResponse503, mockHttpResponse200]
        mockHttpRequest.getresponse = Mock(side_effect=self.side_effect)
        conn.get_connection.return_value = mockHttpRequest
        vofile = vos.VOFile(transferURLs, conn, "GET")
        vofile.read()
        assert(vofile.url == transferURLs[2])
        assert(vofile.urlIndex == 1)
        assert(len(vofile.URLs) == 2)
        
        # all urls busy first time, first one successfull second time
        self.responses = [mockHttpResponse503, mockHttpResponse503, mockHttpResponse503, mockHttpResponse200]
        mockHttpRequest.getresponse = Mock(side_effect=self.side_effect)
        conn.get_connection.return_value = mockHttpRequest
        vofile = vos.VOFile(transferURLs, conn, "GET")
        vofile.read()
        assert(vofile.url == transferURLs[0])
        assert(vofile.urlIndex == 0)
        assert(len(vofile.URLs) == 3)
        assert(3 == vofile.totalRetryDelay)
        assert(1 == vofile.retries)
        
    @patch.object(Connection,'get_connection')
    def test_checkstatus(self,mock_get_connection):
        # Verify the md5sum and size are extracted from the HTTP header
        conn = Connection()
        # test successful - use first url
        vofile = vos.VOFile(None, conn, "GET")
        mockHttpResponse200 = Mock(name="HttpResponse200")
        mockHttpResponse200.getheader = Mock(side_effect=SideEffect({
                ('Content-MD5', None): 12345,
                ('Content-Length', 0): 10,
                }, name="mockHttpResponse200.getheader"))
        mockHttpResponse200.status = 200
        vofile.resp = mockHttpResponse200

        self.assertTrue(vofile.checkstatus())
        self.assertEqual(vofile.get_file_info(), (10, 12345))

        
        
        
    def side_effect(self):
        #removes first in the list
         # mock the 200 response
        return self.responses.pop(0)
        
class SideEffect(object):
    """ The controller is a dictionary with a list as a key and a value. When
        the arguments to the call match the list, the value is returned.
    """
    def __init__(self, controller, name=None, default=None):
        self.controller = controller
        self.default = default
        self.name = name

    def __call__(self, *args, **keywords):
        if args in self.controller:
            return self.controller[args]
        elif self.default is not None:
            return self.default
        else:
            if self.name is None:
                name = ""
            else:
                name = self.name
            raise ValueError("Mock side effect " + name + " arguments not in Controller: " 
                    + str(args) + ":" + str(keywords) + ": " + 
                    str(self.controller) + "***")

        
def run():
    suite = unittest2.TestLoader().loadTestsFromTestCase(TestVOFile)
    return unittest2.TextTestRunner(verbosity=2).run(suite)

if __name__ == "__main__":
    run()
