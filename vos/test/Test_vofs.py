import os
import shutil
import unittest
from vos import vofs
from mock import Mock, MagicMock, patch
from vos.fuse import FuseOSError
from vos.CadcCache import CacheRetry
from errno import EIO


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
        fileHandle = vofs.FileHandle(None)
        fileHandle.readOnly = True
        # Write some data at the start of the file. File is read only so it
        # returns 0
        self.assertEqual( testfs.write( "/dir1/dir2/file", "abcd", 4, 0,
                fileHandle), 0)

        """Write to a locked file"""
        fileHandle.readOnly = False
        fileHandle.locked = True
        with self.assertRaises(FuseOSError):
                testfs.write( "/dir1/dir2/file", "abcd", 4, 0, fileHandle)

    def testWrite2(self):
        """ Write to a read-only file system"""
        opt.readonly = True
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        fileHandle = vofs.FileHandle(None)
        fileHandle.readOnly = False
        # Write some data at the start of the file.
        self.assertEqual(testfs.write( "/dir1/dir2/file", "abcd", 4, 0,
                fileHandle), 0)

    def testWrite3(self):
        """Test a successfull write."""

        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        fileHandle = vofs.FileHandle(Object())
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
        fileHandle = vofs.FileHandle(Object())
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
        fileHandle = vofs.FileHandle(Object())
        fileHandle.cacheFileHandle.read = Mock()
        fileHandle.cacheFileHandle.read.side_effect = CacheRetry("fake")
        with self.assertRaises(FuseOSError):
            testfs.read( "/dir1/dir2/file", 4, 2048, fileHandle)

        # Read with success.
        fileHandle = vofs.FileHandle(Object())
        fileHandle.cacheFileHandle.read = Mock()
        fileHandle.cacheFileHandle.read.return_value = "abcd"
        self.assertEqual( testfs.read( "/dir1/dir2/file", 4, 2048, fileHandle),
                "abcd")










suite1 = unittest.TestLoader().loadTestsFromTestCase(TestVOFS)
alltests = unittest.TestSuite([suite1])
unittest.TextTestRunner(verbosity=2).run(alltests)


