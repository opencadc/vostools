import os
import shutil
import unittest
from vos import vofs, vos
from mock import Mock, MagicMock, patch
from vos.fuse import FuseOSError
from vos.CadcCache import CacheRetry
from errno import EIO, EAGAIN


class Object(object):
    pass

class TestVOFS(unittest.TestCase):
    testMountPoint = "/tmp/testfs"
    testCacheDir = "/tmp/testCache"
    def initOpt(self):
        opt = Object
        opt.readonly = False
        return opt

    def setUp(self):
        global opt
        opt = self.initOpt()
        if os.path.exists(self.testCacheDir):
            shutil.rmtree(self.testCacheDir)

    def tearDown(self):
        self.setUp()

    def testWrite1(self):
        """ Write to a read-only or locked file"""
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        fileHandle = vofs.HandleWrapper(None, True)
        # Write some data at the start of the file. File is read only so it
        # returns 0
        self.assertEqual( testfs.write( "/dir1/dir2/file", "abcd", 4, 0,
                fileHandle), 0)

    def testWrite2(self):
        """ Write to a read-only file system"""
        opt.readonly = True
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        fileHandle = vofs.HandleWrapper(None, False)
        # Write some data at the start of the file.
        self.assertEqual(testfs.write( "/dir1/dir2/file", "abcd", 4, 0,
                fileHandle), 0)

    def testWrite3(self):
        """Test a successfull write."""

        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        fileHandle = vofs.HandleWrapper(Object(), False)
        fileHandle.cacheFileHandle.write = Mock()
        fileHandle.cacheFileHandle.write.return_value = 4
        self.assertEqual(testfs.write( "/dir1/dir2/file", "abcd", 4, 0,
                fileHandle), 4)
        fileHandle.cacheFileHandle.write.return_value = 4
        fileHandle.cacheFileHandle.write.assert_called_once_with("abcd", 4, 0)

        fileHandle.cacheFileHandle.write.call_count = 0
        self.assertEqual(testfs.write( "/dir1/dir2/file", "abcd", 4, 2048,
                fileHandle), 4)
        fileHandle.cacheFileHandle.write.return_value = 4
        fileHandle.cacheFileHandle.write.assert_called_once_with("abcd", 4, 2048)

    def testWrite4(self):
        """Test a timout during write"""

        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        fileHandle = vofs.HandleWrapper(Object(), False)
        fileHandle.cacheFileHandle.write = Mock()
        fileHandle.cacheFileHandle.write.side_effect = CacheRetry("fake")
        with self.assertRaises(FuseOSError):
            testfs.write( "/dir1/dir2/file", "abcd", 4, 2048, fileHandle)

    def testRead1(self):
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)

        # Read with a null file handle.
        with self.assertRaises(FuseOSError):
            testfs.read( "/dir1/dir2/file", 4, 2048)

        # Read with a timeout.
        fileHandle = vofs.HandleWrapper(Object())
        fileHandle.cacheFileHandle.read = Mock()
        fileHandle.cacheFileHandle.read.side_effect = CacheRetry("fake")
        with self.assertRaises(FuseOSError):
            testfs.read( "/dir1/dir2/file", 4, 2048, fileHandle)

        # Read with success.
        fileHandle = vofs.HandleWrapper(Object())
        fileHandle.cacheFileHandle.read = Mock()
        fileHandle.cacheFileHandle.read.return_value = "abcd"
        self.assertEqual( testfs.read( "/dir1/dir2/file", 4, 2048, fileHandle),
                "abcd")
        
    
    def test_open(self):
        myVofs = vofs.VOFS("vos:", self.testCacheDir, opt)
        file = "/dir1/dir2/file"
        
        # call once without the use of mocks
        fh = myVofs.open( file, os.O_RDWR | os.O_CREAT, None)
        self.assertEqual(self.testCacheDir + "/data" + file, fh.cacheFileHandle.cacheDataFile)
        self.assertEqual(self.testCacheDir + "/metaData" + file, fh.cacheFileHandle.cacheMetaDataFile)

        # non existing file open in read/write mode should fail
        with self.assertRaises(FuseOSError):
            myVofs.open(file, os.O_RDWR, None)
            
        # test with mocks so we can assert other parts of the file
        with patch('vos.vofs.MyIOProxy') as mock1:
            with patch('vos.CadcCache.Cache') as mock2:
                myVofs = vofs.VOFS("vos:", self.testCacheDir, opt)
                #myVofs.cache = mock2
                fh = myVofs.open( file, os.O_RDWR | os.O_CREAT, None)
                mock1.assert_called_with(file, myVofs.client, None)
                #mock2.open.assert_called_with(file, True, mock1)
                
                
    def test_close(self):
        file = "/dir1/dir2/file"
        fh = MagicMock(name="filehandler")
        fh.release.side_effect=CacheRetry(Exception())
        myVofs = vofs.VOFS("vos:", self.testCacheDir, opt)
        with self.assertRaises(FuseOSError):
            myVofs.release(file, fh)
        


suite1 = unittest.TestLoader().loadTestsFromTestCase(TestVOFS)
alltests = unittest.TestSuite([suite1])
unittest.TextTestRunner(verbosity=2).run(alltests)


