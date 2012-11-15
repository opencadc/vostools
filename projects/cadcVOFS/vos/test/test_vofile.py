# Test the vos module
import unittest
from vos import vos
from httplib import HTTPResponse
from mock import Mock, MagicMock

class TestVOFile(unittest.TestCase):

    def test_retry_successfull(self):
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
        mockConn = Mock(name="Connection")
        mockHttpRequest = Mock(name="HttpRequest")

        # set a 503 response first followed by a 200 response
        mockHttpRequest.getresponse = MagicMock(name="getresponse mock")
	generator = mockHttpRequest.getresponse.iter.return_value
	iterator = iter([mockHttpResponse503, mockHttpResponse200])
	generator.__iter__.return_value = iterator
        mockConn.getConnection.return_value = mockHttpRequest
        vofile = vos.VOFile("Some URL", mockConn, "GET")

        # check the response
        #TODO self.assertEqual("Testing", vofile.read(), "Incorrect returned value from read")
        #mockHttpResponse503.getheader.assert_called_with("Retry-After", 5)
        # 1 retry -> getheader in HttpResponse503 was called 2 times in the order shown below.
	#TODO call is only available in mock 1.0. Uncomment when this version available
        #expected = [call('Content-Length', 0), call('Retry-After', 5)]
        #self.assertEquals( expected, mockHttpResponse503.getheader.call_args_list)


    def test_fail_max_retry(self):
        # this tests the read function when HTTP requests keep returning 503s
        # read call fails when it reaches the maximum number of retries, in this case set to 2

        # mock the 503 responses
        mockHttpResponse = Mock(name="HttpResponse")
        mockHttpResponse.getheader.return_value = 1 # try again after 1 sec 
        mockHttpResponse.status = 503
        mockHttpResponse.read.return_value = "Testing"
        mockConn = Mock(name="Connection")
        mockHttpRequest = Mock(name="HttpRequest")
        mockHttpRequest.getresponse.return_value = mockHttpResponse
        mockConn.getConnection.return_value = mockHttpRequest
        vofile = vos.VOFile("Some URL", mockConn, "GET")
        # set number of retries to 1 and check the IOError was thrown
        vofile.maxRetries = 1
        with self.assertRaises(IOError) as cm:
            vofile.read()
        mockHttpResponse.getheader.assert_called_with("Retry-After", 5)
        # 1 retry -> getheader in HttpResponse was called 4 times in the order shown below.
	#TODO call is only available in mock 1.0. Uncomment when this version available
        #expected = [call('Content-Length', 0), call('Retry-After', 5), call('Content-Length', 0), call('Retry-After', 5)]
        #self.assertEquals( expected, mockHttpResponse.getheader.call_args_list)

    def test_retry_412_successfull(self):
        # this tests the read function when first HTTP request returns a 412 but the second one 
        # is successfull and returns a 200

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
        mockConn = Mock(name="Connection")
        mockHttpRequest = Mock(name="HttpRequest")

        # set a 412 response first followed by a 200 response
        mockHttpRequest.getresponse = MagicMock(side_effect=[mockHttpResponse412, mockHttpResponse200])
        mockConn.getConnection.return_value = mockHttpRequest
        vofile = vos.VOFile("Some URL", mockConn, "GET")
        vofile.currentRetryDelay = 2

        # check the response
        #self.assertEqual("Testing", vofile.read(), "Incorrect returned value from read")
        # 1 retry -> getheader in HttpResponse412 was called once as follows.
	#TODO call is only available in mock 1.0. Uncomment when this version available
        #expected = [call('Content-Length', 0)]
        #self.assertEquals( expected, mockHttpResponse412.getheader.call_args_list)


suite = unittest.TestLoader().loadTestsFromTestCase(TestVOFile)
unittest.TextTestRunner(verbosity=2).run(suite)
