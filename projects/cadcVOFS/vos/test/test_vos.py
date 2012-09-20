# Test the vos module
import unittest

class TestVOS(unittest.TestCase):

    def test_download(self):
        print "****************************************"  
        print "*                                      *"  
    	print "*          To be implemented           *"
        print "*                                      *"  
        print "****************************************"  

        
suite = unittest.TestLoader().loadTestsFromTestCase(TestVOS)
unittest.TextTestRunner(verbosity=2).run(suite)
