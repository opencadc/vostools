# Test the cadcCache module.

import io
import logging
import os
import errno
import stat
import threading
import thread
import time
import copy
from contextlib import nested
import shutil
import unittest
from mock import Mock, MagicMock, patch

import CadcCache
from SharedLock import SharedLock, TimeoutError, RecursionError

###import vos.fuse

###import traceback
###from vos.fuse import FuseOSError
###from mock import patch
###from ctypes import create_string_buffer

# To run individual tests, set the value of skipTests to True, and comment
# out the @unittest.skipIf line at the top of the test to be run.
skipTests = False
testDir = "/tmp/testcache"

class IOProxyForTest(CadcCache.IOProxy):
    """
    Subclass of the IOProxy class. Used for both testing the
    IOProxy class and as an IOProxy object when testing the Cache class.
    """

    def getMD5(self):
        return 'd41d8cd98f00b204e9800998ecf8427e'

    def getSize(self):
        return 0

    def delNode(self, force = False):
        return

    def verifyMetaData(self, md5sum):
        """Generic test returns true"""
        return True

    def writeToBacking(self, md5, size, mtime):
        return

    def readFromBacking(self, offset = None, size = None):
        return

class IOProxyFor100K(CadcCache.IOProxy):
    """
    Subclass of the CadcCache.IOProxy class. Used for both testing the
    IOProxy class and as an IOProxy object when testing the CadcCache.Cache class.
    """

    def getMD5(self):
        return '4c6426ac7ef186464ecbb0d81cbfcb1e'

    def getSize(self):
        return 102400

    def delNode(self, force = False):
        return

    def verifyMetaData(self, md5sum):
        """Generic test returns true"""
        return True

    def writeToBacking(self, md5, size, mtime):
        return

    def readFromBacking(self, offset = None, size = None):
	if offset > 102400 or offset + size > 102400:
	    raise CadcCache.CacheError("Attempt to read beyond the end of file.")
        return ['\0'] * size
        

class TestIOProxy(unittest.TestCase):
    """Test the IOProxy class.
    """

    def setUp(self):
        if os.path.exists(testDir):
            if os.path.isdir(testDir): 
                shutil.rmtree(testDir)
            else:
                os.remove(testDir)

    def tearDown(self):
        self.setUp()


    """
    Test the IOProxy class.
    """
    @unittest.skipIf(skipTests, "Individual tests")
    def test_basic(self):
        """Test the IOProxy abstract methods
        """
        with CadcCache.Cache(testDir, 100, True) as testCache:
            testIOProxy = CadcCache.IOProxy()
            with self.assertRaises(NotImplementedError):
                testIOProxy.getMD5()
            with self.assertRaises(NotImplementedError):
                testIOProxy.getSize();
            with self.assertRaises(NotImplementedError):
                testIOProxy.delNode()
            with self.assertRaises(NotImplementedError):
                testIOProxy.writeToBacking("a", 1, 1.);
            with self.assertRaises(NotImplementedError):
                testIOProxy.readFromBacking();

    @unittest.skipIf(skipTests, "Individual tests")
    def test_writeToCache(self):
        """Test the IOProxy writeToCache method
        """
        with CadcCache.Cache(testDir, 100, True) as testCache:
            testIOProxy = CadcCache.IOProxy()

            # Write to beginning of the output
            testIOProxy.cacheFileStream = io.BufferedIOBase()
            testIOProxy.cacheFileStream.tell = Mock(return_value = 0)
            testIOProxy.cacheFileStream.write = Mock(return_value = 3)
            testIOProxy.cache = testCache
            self.assertEqual(3, testIOProxy.writeToCache("abc", 0))

            # Write to after the beginning of the output
            testIOProxy.cacheFileStream = io.BufferedIOBase()
            testIOProxy.cacheFileStream.tell = Mock(return_value = 3)
            testIOProxy.cacheFileStream.write = Mock(return_value = 3)
            testIOProxy.cacheFileStream.seek = Mock(return_value = 1)
            self.assertEqual(3, testIOProxy.writeToCache("abc",
                testCache.IO_BLOCK_SIZE))

            # Test an erroneous seek to the middle of a block
            testIOProxy.cacheFileStream = io.BufferedIOBase()
            testIOProxy.cacheFileStream.tell = Mock(return_value = 0)
            testIOProxy.cacheFileStream.write = Mock(return_value = 3)
            testIOProxy.cacheFileStream.seek = Mock(return_value = 1)
            with self.assertRaises(CadcCache.CacheError) as cm:
                testIOProxy.writeToCache("abc", 3)

            # Test a completed block write
            testIOProxy.cacheFileStream = io.BufferedIOBase()
            testIOProxy.cacheFileStream.tell = Mock(return_value=0)
            testIOProxy.cacheFileStream.write = Mock(
                    return_value = testCache.IO_BLOCK_SIZE * 2)
            testIOProxy.cacheFileStream.seek = Mock(return_value = 1)
            buffer = bytearray(testCache.IO_BLOCK_SIZE * 2)
            self.assertEqual(testCache.IO_BLOCK_SIZE * 2, 
                    testIOProxy.writeToCache(buffer, testCache.IO_BLOCK_SIZE))
            # TODO assert the bitmap changed correctly - bit 0 not changed, bit
            # 1 & 2 changed.


            # test a subclass
            testIOProxy = IOProxyForTest()
            self.assertEqual(testIOProxy.getMD5(),
                    "d41d8cd98f00b204e9800998ecf8427e")

    @unittest.skipIf(skipTests, "Individual tests")
    def test_blockInfo(self):
        testIOProxy = IOProxyForTest()
        with CadcCache.Cache(testDir, 100, True) as testCache:
            testFile = testCache.open("/dir1/dir2/file", False, 
                testIOProxy)
            self.assertEqual((0, 0), testIOProxy.blockInfo(0, 0))
            self.assertEqual((0, 1), testIOProxy.blockInfo(0, 1))
            self.assertEqual((0, 1), testIOProxy.blockInfo(1, 1))
            self.assertEqual((0, 1), 
		    testIOProxy.blockInfo( testCache.IO_BLOCK_SIZE - 1, 1))
            self.assertEqual((1, 1), 
		    testIOProxy.blockInfo( testCache.IO_BLOCK_SIZE, 1))
            self.assertEqual((0, 1), 
                    testIOProxy.blockInfo(0, testCache.IO_BLOCK_SIZE))
            self.assertEqual((0, 2), 
                    testIOProxy.blockInfo(0, testCache.IO_BLOCK_SIZE + 1))
            self.assertEqual((0, 2), 
                    testIOProxy.blockInfo(100, testCache.IO_BLOCK_SIZE))
            self.assertEqual((2, 3), 
                    testIOProxy.blockInfo(testCache.IO_BLOCK_SIZE * 2, 
                    testCache.IO_BLOCK_SIZE * 3))
            self.assertEqual((2, 4), 
                    testIOProxy.blockInfo(100 + testCache.IO_BLOCK_SIZE * 2, 
                    testCache.IO_BLOCK_SIZE * 3 + 100))


    @unittest.skipIf(skipTests, "Individual tests")
    def test_readFromCache(self):
        """ Test the readFromCache method.
        """
        with CadcCache.Cache(testDir, 100, True) as testCache:
            # read from the start of the file.
            testIOProxy = IOProxyForTest()
            testIOProxy.cacheFileStream = io.BufferedIOBase()
            testIOProxy.cacheFileStream.tell = Mock(return_value = 0)
            testIOProxy.cacheFileStream.read = Mock(return_value = "abc")
            testIOProxy.cacheFileStream.seek = Mock(return_value = 1)
            testIOProxy.cache = testCache
            buffer = testIOProxy.readFromCache(0, 10)
            self.assertEqual("abc", buffer)
            self.assertFalse(testIOProxy.cacheFileStream.seek.called)

            # Read from the current position in the file
            testIOProxy.cacheFileStream.read = Mock(return_value = "def")
            testIOProxy.cacheFileStream.tell = Mock(return_value = 3)
            buffer = testIOProxy.readFromCache(3, 10)
            self.assertEqual("def", buffer)
            self.assertFalse(testIOProxy.cacheFileStream.seek.called)

            # Read from a different position in the file
            testIOProxy.cacheFileStream.read = Mock(return_value = "ghi")
            testIOProxy.cacheFileStream.tell = Mock(return_value = 3)
            buffer = testIOProxy.readFromCache(0, 10)
            self.assertEqual("ghi", buffer)
            testIOProxy.cacheFileStream.seek.assert_called_once_with(0)



class TestCacheError(unittest.TestCase):
    @unittest.skipIf(skipTests, "Individual tests")
    def test_str(self):
        e = CadcCache.CacheError("a string")
        self.assertEqual("'a string'", str(e))

class TestCacheRetry(unittest.TestCase):
    @unittest.skipIf(skipTests, "Individual tests")
    def test_str(self):
        e = CadcCache.CacheRetry("a string")
        self.assertEqual("'a string'", str(e))


class TestSharedLock(unittest.TestCase):
    """Test the SharedLock class.
    """

    @unittest.skipIf(skipTests, "Individual tests")
    def test_Exceptions(self):
        e = TimeoutError("timeout")
        self.assertEqual(str(e), "'timeout'")

        e = RecursionError("recursion")
        self.assertEqual(str(e), "'recursion'")

    @unittest.skipIf(skipTests, "Individual tests")
    def test_simpleLock(self):
        """Test a simple lock release sequence
        """

        # no argument test.
        lock = SharedLock()
        self.assertEqual(0, len(lock.lockersList))
        self.assertTrue(lock.exclusiveLock is None)
        lock.acquire()
        self.assertEqual(1, len(lock.lockersList))
        self.assertTrue(lock.exclusiveLock is None)
        lock.release()
        self.assertEqual(0, len(lock.lockersList))
        self.assertTrue(lock.exclusiveLock is None)

        # Lock with a timeout
        lock.acquire(timeout=5)
        self.assertEqual(1, len(lock.lockersList))
        self.assertTrue(lock.exclusiveLock is None)

        # Try to acquire a lock twice. Should fail.
        with self.assertRaises(RecursionError) as e:
            lock.acquire(timeout=5)
        self.assertTrue(lock.exclusiveLock is None)
        lock.release()



    @unittest.skipIf(skipTests, "Individual tests")
    @patch('threading.current_thread')
    def test_simpleLock2(self,mock_current_thread):
        """Test acquiring shared locks from a different thread.
        """

        mock_current_thread.return_value = 'thread1'
        lock = SharedLock()
        lock.acquire()
        self.assertEqual(1, len(lock.lockersList))
        self.assertTrue(lock.exclusiveLock is None)
        mock_current_thread.return_value = 'thread2'
        lock.acquire()
        self.assertEqual(2, len(lock.lockersList))
        self.assertTrue(lock.exclusiveLock is None)
        lock.release()
        self.assertEqual(1, len(lock.lockersList))
        self.assertTrue(lock.exclusiveLock is None)
        with self.assertRaises(KeyError):
            lock.release()
        mock_current_thread.return_value = 'thread1'
        lock.release()
        self.assertEqual(0, len(lock.lockersList))
        self.assertTrue(lock.exclusiveLock is None)


    @unittest.skipIf(skipTests, "Individual tests")
    @patch('threading.current_thread')
    def test_exclusiveLock(self,mock_current_thread):

        # Try to acquire an exclusive lock.
        mock_current_thread.return_value = 'thread1'
        lock = SharedLock()
        lock.acquire(shared=False)
        self.assertEqual(lock.exclusiveLock, 'thread1')
        self.assertEqual(0, len(lock.lockersList))
        lock.release()
        self.assertEqual(0, len(lock.lockersList))
        self.assertTrue(lock.exclusiveLock is None)

        # Get an exclusive lock and then attempt to get a shared lock with a
        # timeout.
        lock = SharedLock()
        lock.acquire(shared=False)
        with self.assertRaises(TimeoutError):
            lock.acquire(shared=True, timeout=1)
        self.assertEqual(lock.exclusiveLock, 'thread1')
        self.assertEqual(0, len(lock.lockersList))
        lock.release()
        self.assertEqual(0, len(lock.lockersList))
        self.assertTrue(lock.exclusiveLock is None)

        # Get a shared lock, and then attempt to get an exclusive lock with
        # a timeout
        lock.acquire(shared=True)
        self.assertEqual(1, len(lock.lockersList))
        self.assertTrue(lock.exclusiveLock is None)
        with self.assertRaises(TimeoutError):
            lock.acquire(shared=False, timeout=1)


    @unittest.skipIf(skipTests, "Individual tests")
    def test_no_timeout(self):
        """Test acquiring shared locks from a different thread without a wait.
        """
        lock = SharedLock()
        # Get a shared lock and then attempt to get an exclusive lock without
        # a timeout.
        t1 = threading.Thread(target=self.getShared, args=[lock])
        t1.start()
        time.sleep(1)
        self.assertTrue(lock.exclusiveLock is None)
        self.assertEqual(1, len(lock.lockersList))
        lock.acquire(shared=False)
        self.assertTrue(lock.exclusiveLock is not None)
        self.assertEqual(0, len(lock.lockersList))
        t1.join()
        lock.release()
        self.assertEqual(0, len(lock.lockersList))
        self.assertTrue(lock.exclusiveLock is None)

        # Get an exclusive lock and then attempt to get an exclusive lock 
        # without a timeout.
        t1 = threading.Thread(target=self.getExclusive, args=[lock])
        t1.start()
        time.sleep(1)
        self.assertTrue(lock.exclusiveLock is not None)
        self.assertEqual(0, len(lock.lockersList))
        lock.acquire(shared=False)
        self.assertTrue(lock.exclusiveLock is not None)
        self.assertEqual(0, len(lock.lockersList))
        t1.join()
        lock.release()
        self.assertEqual(0, len(lock.lockersList))
        self.assertTrue(lock.exclusiveLock is None)

    def getShared(self,lock):
        lock.acquire(shared=True)
        time.sleep(5)
        lock.release()

    def getExclusive(self,lock):
        lock.acquire(shared=False)
        time.sleep(5)
        lock.release()

    @unittest.skipIf(skipTests, "Individual tests")
    def test_with(self):
        """Test the with construct.
        """

        lock = SharedLock()
        with lock(shared=True):
            self.assertTrue(lock.exclusiveLock is None)
            self.assertEqual(1,len(lock.lockersList))

        with lock(shared=False):
            self.assertTrue(lock.exclusiveLock is not None)
            self.assertEqual(0,len(lock.lockersList))

class TestCacheCondtion(unittest.TestCase):
    @unittest.skipIf(skipTests, "Individual tests")
    def test_all(self):
	lock = threading.Lock()
	cond = CadcCache.CacheCondition(lock, 1)
	self.assertEqual(cond.timeout,1)
	self.assertTrue(cond.threadSpecificData.endTime is None)
	self.assertTrue(cond.acquire())
	self.assertFalse(cond.acquire(False))
	cond.release()
	self.assertTrue(cond.acquire(blocking=0))
	cond.release()
	cond.setTimeout()
	self.assertTrue(cond.threadSpecificData.endTime is not None)
	with cond:
	    self.assertFalse(cond.acquire(False))
            with self.assertRaises(CadcCache.CacheRetry):
		cond.wait()
		cond.wait()

	cond.clearTimeout()
	self.assertTrue(cond.threadSpecificData.endTime is None)

	# A wait without a timeout set.
	cond = CadcCache.CacheCondition(lock, 30)
	self.assertTrue(cond.threadSpecificData.endTime is None)
	with cond:
	    t1 = threading.Thread(target=self.notifyAfter1S, args=[cond])
	    t1.start()
	    cond.wait()

	# A wait with a timeout set.
	cond = CadcCache.CacheCondition(lock, 30)
	cond.setTimeout()
	self.assertTrue(cond.threadSpecificData.endTime is not None)
	with cond:
	    t1 = threading.Thread(target=self.notifyAfter1S, args=[cond])
	    t1.start()
	    cond.wait()

    def notifyAfter1S(self,cond):
	time.sleep(1)
	with cond:
	    cond.notify_all()




	

class TestCadcCache(unittest.TestCase):
    """Test the CadcCache.CadcCache class
    """

    testMD5="0dfbe8aa4c20b52e1b8bf3cb6cbdf193"
    testSize=128*1024

    def setUp(self):
        if os.path.exists(testDir):
            if os.path.isdir(testDir): 
                shutil.rmtree(testDir)
            else:
                os.remove(testDir)

    def tearDown(self):
        self.setUp()


    @unittest.skipIf(skipTests, "Individual tests")
    def test_getFileHandle(self):
        """Test the getFileHandle method returns the same file handle for
           a file which is opened multiple times.
        """
        testIOProxy = IOProxyForTest()
        testIOProxy = IOProxyForTest()
        with CadcCache.Cache(testDir, 100, True) as testCache:
            testFile = testCache.open("/dir1/dir2/file", False, 
                testIOProxy)
            self.assertEqual(1, testFile.refCount)

            testFile2 = testCache.open("/dir1/dir2/file", False, 
                testIOProxy)
            self.assertEqual(2, testFile.refCount)
            self.assertTrue(testFile is testFile2)

            testFile2.release()
            self.assertEqual(1, testFile.refCount)

            testFile.release()
            self.assertEqual(0, testFile.refCount)

            # Relative path should cause an error.
            with self.assertRaises(ValueError):
                testCache.open("dir1/dir2/file", False, testIOProxy)


    @unittest.skipIf(skipTests, "Individual tests")
    def test_constructor1(self):
        # Constructor with a non-existing cache directory.
        testObject = CadcCache.Cache(testDir, 100)
        self.assertTrue(os.path.isdir(testDir))
        self.assertTrue(os.access(testDir, os.R_OK | os.W_OK | os.X_OK))

        # Constructor with an existing cache directory.
        testObject = CadcCache.Cache(testDir, 100)
        self.assertTrue(os.path.isdir(testDir))
        self.assertTrue(os.access(testDir, os.R_OK | os.W_OK | os.X_OK))

        self.setUp_testDirectory()

        testObject = CadcCache.Cache(testDir, 100)
        self.assertTrue(os.path.isdir(testDir))
        self.assertTrue(os.access(testDir, os.R_OK | os.W_OK | os.X_OK))

    @unittest.skipIf(skipTests, "Individual tests")
    def test_constructor2(self):
        # Constructor with an existing cache directory and bad permissions.
        testObject = CadcCache.Cache(testDir, 100)
        self.assertTrue(os.path.isdir(testDir))
        self.assertTrue(os.access(testDir, os.R_OK | os.W_OK | os.X_OK))
        os.chmod(testDir, stat.S_IRUSR)
        try:
            with self.assertRaises(CadcCache.CacheError) as cm:
                CadcCache.Cache(testDir, 100)
        finally:
            os.chmod(testDir, stat.S_IRWXU)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_constructor3(self):
        """ Constructor with a file where the cache directory should be."""

        # create the file
        open(testDir, 'a').close()

        with self.assertRaises(CadcCache.CacheError) as cm:
            CadcCache.Cache(testDir, 100)

        self.assertTrue(str(cm.exception).find("is not a directory") > 0)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_constructor4(self):
        """ Constructor with a file where the cache data directory should be."""
        os.mkdir(testDir)
        open(testDir + "/data", 'a').close()

        with self.assertRaises(CadcCache.CacheError) as cm:
            CadcCache.Cache(testDir, 100)

        self.assertTrue(str(cm.exception).find("is not a directory") > 0)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_constructor5(self):
        """ Constructor with a read-only directory where the cache data directory should be."""
        os.mkdir(testDir)
        os.mkdir(testDir + "/data")
        os.chmod(testDir + "/data", stat.S_IRUSR)

        try:
            with self.assertRaises(CadcCache.CacheError) as cm:
                CadcCache.Cache(testDir, 100)

            self.assertTrue(str(cm.exception).find("permission") > 0)
        finally:
            os.chmod(testDir + "/data/", stat.S_IRWXU)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_constructor6(self):
        """ Constructor with a file where the cache meta data directory 
            should be.
        """
        os.mkdir(testDir)
        open(testDir + "/metaData", 'a').close()

        with self.assertRaises(CadcCache.CacheError) as cm:
            CadcCache.Cache(testDir, 100)

        self.assertTrue(str(cm.exception).find("is not a directory") > 0)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_constructor7(self):
        """ Constructor with a read-only directory where the cache meta data 
            directory should be."""
        os.mkdir(testDir)
        os.mkdir(testDir + "/metaData")
        os.chmod(testDir + "/metaData", stat.S_IRUSR)

        try:
            with self.assertRaises(CadcCache.CacheError) as cm:
                CadcCache.Cache(testDir, 100)

            self.assertTrue(str(cm.exception).find("permission") > 0)
        finally:
            os.chmod(testDir + "/metaData/", stat.S_IRWXU)

    def setUp_testDirectory(self):
        directories = { "dir1", "dir2", "dir3" }
        files = { "f1", "f2", "f3" }
        for dir in directories:
            os.mkdir("/".join([ testDir , dir ]))
            for f in files:
                fd = open("/".join([ testDir, dir, f ]),  'a')
                fd.seek(1000)
                fd.write ("a")
                fd.close()

    @unittest.skipIf(skipTests, "Individual tests")
    def test_open1(self):
        # IOProxy - getMD5 fails with ENOENT
        class IOProxy_getMD5_ENOENT(IOProxyForTest):
            def getMD5(self):
                e = OSError("test failure")
                e.errno = errno.ENOENT
                raise e

        with CadcCache.Cache(testDir, 100) as testObject:
            ioObject = IOProxyForTest()
            fd = testObject.open("/dir1/dir2/file", False, ioObject)
            self.assertFalse(fd.fullyCached)
            self.assertFalse(fd.fileModified)
            fd.release()

            ioObject = IOProxy_getMD5_ENOENT()
            with self.assertRaises(OSError):
                fd = testObject.open("/dir1/dir2/file", False, ioObject)
            fd.release()

    @unittest.skipIf(skipTests, "Individual tests")
    def test_open2(self):
        """ Open a new file"""
        with CadcCache.Cache(testDir, 100) as testObject:
            ioObject = IOProxyForTest()
            ioObject2 = IOProxyForTest()
            fd = testObject.open("/dir1/dir2/file", True, ioObject)
            self.assertTrue(fd.fullyCached)
            self.assertTrue(fd.fileModified)
            fd.release()

            fd = testObject.open("/dir1/dir2/file", False, ioObject)
            fd2 = testObject.open("/dir1/dir2/file", True, ioObject2)
            fd.release()
            fd2.release()



    @unittest.skipIf(skipTests, "Individual tests")
    def test_getmd5(self):
        with CadcCache.Cache(testDir, 100) as testCache:
            ioObject = IOProxyForTest()
            self.makeTestFile(os.path.join(testCache.dataDir, 
                    "dir1/dir2/file"), self.testSize)
            fd = testCache.open("/dir1/dir2/file", False, ioObject)
            result = fd.getmd5()
            # Check the md5sum and size are correct, and the modification
            # time is roughly nowish.
            self.assertEqual((self.testMD5, self.testSize), result[0:2])
            self.assertTrue(abs(result[2] - time.time()) < 10)

            fd.release()

    @unittest.skipIf(skipTests, "Individual tests")
    def test_release1(self):
        """Fails getting md5."""
        with CadcCache.Cache(testDir, 100) as testObject:
            ioObject = IOProxyForTest()
            ioObject.verifyMetaData = Mock(return_value=False)
            fd = testObject.open("/dir1/dir2/file", False, ioObject)
            fd.release()

            # Test flushnode raising an exception
            ioObject = IOProxyForTest()
            ioObject.verifyMetaData = Mock(return_value=False)
            fd = testObject.open("/dir1/dir2/file", False, ioObject)
            fd.fileModified = True
            fd.getmd5 = Mock(side_effect=Exception("failed"))
            with self.assertRaises(Exception) as cm:
                fd.release()

    def makeTestFile(self, name, size):
        try:
            os.makedirs(os.path.dirname(name))
        except OSError:
            pass
        with nested(open(name, 'w'), open('/dev/zero')) as (w,r):
            buff = r.read(size)
            self.assertEqual(len(buff), size)
            w.write(buff)


    @unittest.skipIf(skipTests, "Individual tests")
    def test_release2(self):
        class IOProxy_writeToBacking_slow(IOProxyForTest):
            def verifyMetaData(self, md5sum):
                """ test returns False """
                return False

            def writeToBacking(self, fd, mtime):
                time.sleep(4)
                return


        with CadcCache.Cache(testDir, 100) as testObject:
            # Release a slow to write file.
            ioObject = IOProxy_writeToBacking_slow()
            thread.start_new_thread(self.release2_sub1, 
                    (testObject, ioObject))
            time.sleep(1)

            ioObject2 = IOProxyForTest()
            ioObject2.writeToBacking = MagicMock()
            fd = testObject.open("/dir1/dir2/file", False, ioObject2)
            fd.release()
            assert not ioObject2.writeToBacking.called, \
                    'writeToBacking was called and should not have been'

    def release2_sub1(self, testObject, ioObject):
        fd = testObject.open("/dir1/dir2/file", False, ioObject)
        fd.release()

    @unittest.skipIf(skipTests, "Individual tests")
    def test_release3(self):
        """Successful write to backing"""

        with CadcCache.Cache(testDir, 100) as testObject:
            # This should really flush the data to the backing
            ioObject = IOProxyForTest()
            ioObject.writeToBacking = MagicMock()
            self.makeTestFile(os.path.join(testObject.dataDir, 
                    "dir1/dir2/file"), self.testSize)
            fd = testObject.open("/dir1/dir2/file", False, ioObject)
            fd.fileModified = True
            info = os.stat(fd.cacheDataFile)
            fd.release()
            ioObject.writeToBacking.assert_called_once_with(self.testMD5,
                    self.testSize, info.st_mtime)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_read1(self):
        """Test reading from a file which is not cached."""

	with CadcCache.Cache(testDir, 100) as testCache:
	    ioObject = IOProxyFor100K()
	    fd = testCache.open("/dir1/dir2/file", False, ioObject)
	    data = fd.read(100,0)
	    fd.release()

    #@unittest.skipIf(skipTests, "Individual tests")
    def test_makeCached(self):
        """initializing the cached file
	"""

	class CacheReadThreadMock(threading.Thread):
	    """ Mock class for the CachedReadThread class defined in CadcCache.
	    """

	    def __init__(self, fileHandle):
		threading.Thread.__init__(self, target=self.execute)
		self.fileHandle = fileHandle

	    def execute(self):
		with self.fileHandle.fileCondition:
		    self.fileHandle.fileCondition.notify_all()
		    self.fileHandle.metaData.setReadBlocks(0,6)
		    self.fileHandle.readThread = None

	    def checkProgress(self, first, last):
		return False

	def sideEffectTrue(firstByte, lastByte):
	    fd.readThread.checkProgress.side_effect = sideEffectFalse
	    self.assertEqual(firstByte,0)
	    self.assertEqual(lastByte,testCache.IO_BLOCK_SIZE)
	    return True

	def sideEffectFalse(firstByte, lastByte):
	    self.assertEqual(firstByte,0)
	    self.assertEqual(lastByte,testCache.IO_BLOCK_SIZE)
	    return False

	def threadExecuteMock(thread):
	    thread.fileHandle.readThread = None
	    thread.fileHandle.fileCondition.notify_all()
	    thread.fileHandle.metaData.setReadBlocks(0,6)

	with CadcCache.Cache(testDir, 100, timeout=2) as testCache:
	    ioObject = IOProxyFor100K()
	    # Fully cached, makeCached does mostly nothing.
	    with testCache.open("/dir1/dir2/file", False, ioObject) as fd:
		fd.fullyCached = True
		oldMetaData = copy.deepcopy(fd.metaData)
		fd.metaData.getRange = Mock()
		fd.metaData.persist = Mock()
		fd.makeCached(0,1)
		self.assertEqual(fd.metaData.getRange.call_count, 0)
		fd.metaData = oldMetaData
		fd.fullyCached = False

	    # Check that the block range correctly maps to bytes when
	    # checkProgress is called. The call exits with a timeout.
	    with testCache.open("/dir1/dir2/file", False, ioObject) as fd:
		fd.readThread=CadcCache.CacheReadThread(fd,0,0,0)
		fd.readThread.checkProgress = Mock()
		fd.readThread.checkProgress.side_effect = sideEffectTrue
		fd.fileCondition.setTimeout()
		fd.metaData.delete()
		with self.assertRaises(CadcCache.CacheRetry):
		    fd.makeCached(0,1)
		self.assertEqual(fd.readThread.checkProgress.call_count, 1)
		fd.readThread=None

	    # The required range is cached. The fn exists after calling
	    # getRange.
	    with testCache.open("/dir1/dir2/file", False, ioObject) as fd:
		fd.readThread=CadcCache.CacheReadThread(fd,0,0,0)
		oldMetaData = fd.metaData
		fd.metaData = copy.deepcopy(oldMetaData)
		fd.readThread.checkProgress = Mock()
		fd.metaData.getRange = Mock(return_value=(None,None))
		fd.makeCached(0,1)
		self.assertEqual(fd.metaData.getRange.call_count, 1)
		self.assertEqual(fd.readThread.checkProgress.call_count, 0)
		fd.metaData = oldMetaData
		fd.readThread=None

	    # Check that the block range correctly maps to bytes when
	    # checkProgress is called. The call exits when data is available.
	    with testCache.open("/dir1/dir2/file", False, ioObject) as fd:
		oldMetaData = copy.deepcopy(fd.metaData)
		fd.metaData.persist = Mock()
		fd.readThread=CadcCache.CacheReadThread(fd,0,0,0)
		fd.readThread.checkProgress = Mock()
		fd.readThread.checkProgress.side_effect = sideEffectTrue
		fd.fileCondition.setTimeout()
		fd.metaData.delete()
		t1 = threading.Thread(target=self.notifyReMockRange,
			args=[fd.fileCondition,fd])
		t1.start()
		fd.makeCached(0,1)
		fd.metaData = oldMetaData
		fd.readThread=None

	    # This call will cause the existing thread be be aborted, and a new
	    # thread to be started. This will look like the data is available
	    # immediately after the thread aborts, and so no new thread will
	    # start. This test fails by timing out in the condition wait, 
	    # which throws an exception.
	    with testCache.open("/dir1/dir2/file", False, ioObject) as fd:
		oldMetaData = copy.deepcopy(fd.metaData)
		fd.readThread=CadcCache.CacheReadThread(fd,0,0,0)
		fd.readThread.checkProgress = Mock()
		fd.readThread.checkProgress.side_effect = sideEffectFalse
		fd.fileCondition.setTimeout()
		fd.metaData.delete()
		t1 = threading.Thread(target=self.notifyReMockRange,
			args=[fd.fileCondition,fd])
		t1.start()
		fd.makeCached(0,1)
		fd.metaData = oldMetaData
		fd.readThread=None

	    # This call will cause the existing thread be be aborted, and a new
	    # thread to be started. The data will not seem to be availble, so a
	    # new thread will be started. This test fails by timing out in the
	    # condition wait, which throws an exception.
	    with testCache.open("/dir1/dir2/file", False, ioObject) as fd:
		fd.fileCondition.setTimeout()
		fd.metaData.delete()
		t1 = threading.Thread(target=self.notifyAfter1S,
			args=[fd.fileCondition,fd])
		t1.start()
		with patch('CadcCache.CacheReadThread') as mockedClass:
		    mockedClass.return_value = CacheReadThreadMock(fd)
		    fd.makeCached(0,1)

	    # This call will cause the optional end to be before the end of the
	    # file because some data near the end of the file has been cached.
	    with testCache.open("/dir1/dir2/file", False, ioObject) as fd:
		fd.fileCondition.setTimeout()
		fd.metaData.delete()
		t1 = threading.Thread(target=self.notifyAfter1S,
			args=[fd.fileCondition,fd])
		t1.start()
		with patch('CadcCache.CacheReadThread') as mockedClass:
		    realClass = mockedClass.returnValue
		    mockedClass.return_value = CacheReadThreadMock(fd)
		    fd.metaData.setReadBlocks(6, 6)
		    fd.metaData.md5sum = 12345
		    fd.makeCached(0,1)
		    # TODO figure out a way to test the result. The init method
		    # of CacheReadThreadMock should be with arguments which get
		    # the first block as mandatory, and everything except the
		    # last block as optional


    def notifyReMockRange(self,cond,fd):
	time.sleep(1)
	# Make getRange return None,None
	with cond:
	    fd.metaData.getRange = Mock(return_value=(None,None))
	    cond.notify_all()

    def notifyAfter1S(self,cond,fd):
	time.sleep(1)
	# Make getRange return None,None
	with cond:
	    cond.notify_all()

    @unittest.skipIf(skipTests, "Individual tests")
    def test_determineCacheSize(self):
        """ Test checking the cache space """
        if os.path.exists(testDir):
            os.rmdir(testDir)
        cache = CadcCache.Cache(cacheDir = testDir, maxCacheSize = 4)
        testVospaceFile1 = "/dir1/dir2/file1";
        testVospaceFile2 = "/dir1/dir2/file2";
        testFile1 = cache.dataDir +"/dir1/dir2/file1"
        testFile2 = cache.dataDir + "/dir1/dir2/file2"
        # add files to the cache
        self.makeTestFile(testFile1, 3*1024*1024)
        self.makeTestFile(testFile2, 2*1024*1024)
        
        #get the total size (5M) and the oldest file (testFile1)
        self.assertEquals((testFile1, 5*1024*1024), cache.determineCacheSize())
        
        # mark file1 as in use
        cache.fileHandleDict[testVospaceFile1] = None
        #get the total size (5M) and the oldest not in use file (testFile2)
        self.assertEquals((testFile2, 5*1024*1024), cache.determineCacheSize())
        
        # mark file2 as in use
        cache.fileHandleDict[testVospaceFile2] = None
        #get the total size (5M) and but no files not in use
        self.assertEquals((None, 5*1024*1024), cache.determineCacheSize())
        
    
    @unittest.skipIf(skipTests, "Individual tests")
    def test_checkCacheSpace(self):
        """ Test cache cleanup """
        if os.path.exists(testDir):
            os.rmdir(testDir)
        cache = CadcCache.Cache(cacheDir = testDir, maxCacheSize = 4)
        testVospaceFile1 = "/dir1/dir2/file1";
        testVospaceFile2 = "/dir1/dir2/file2";
        testFile1 = cache.dataDir +"/dir1/dir2/file1"
        testFile2 = cache.dataDir + "/dir1/dir2/file2"
        # add files to the cache
        self.makeTestFile(testFile1, 3*1024*1024)
        self.makeTestFile(testFile2, 2*1024*1024)
        
        # cleanup time. file1 should disapper
        cache.checkCacheSpace()
        #get the total remaining size (5M) of the remaining file (file2)
        self.assertEquals((testFile2, 2*1024*1024), cache.determineCacheSize())
 
        # add file1 back -> file2 is now the oldest and is going to get deleted
        self.makeTestFile(testFile1, 3*1024*1024)
        cache.checkCacheSpace()
        #get the total size (3M) of the remaining file (file1)
        self.assertEquals((testFile1, 3*1024*1024), cache.determineCacheSize())
        
        #add file2 back and mark file 1 as in use. file2 is going to be deleted
        self.makeTestFile(testFile2, 2*1024*1024)
        cache.fileHandleDict[testVospaceFile1] = None
        cache.checkCacheSpace()
        #get the total size (3M) of the remaining file (file1) but file1 is in
        # use
        self.assertEquals((None, 3*1024*1024), cache.determineCacheSize())
        
        # add file2 back but also mark it as in use.
        self.makeTestFile(testFile2, 2*1024*1024)
        cache.fileHandleDict[testVospaceFile2] = None
        cache.checkCacheSpace()
        # no files deleted as all of them are in use
        self.assertEquals((None, 5*1024*1024), cache.determineCacheSize())

logging.getLogger('CadcCache').setLevel(logging.DEBUG)
logging.getLogger('CadcCache').addHandler(logging.StreamHandler())

suite1 = unittest.TestLoader().loadTestsFromTestCase(TestIOProxy)
suite2 = unittest.TestLoader().loadTestsFromTestCase(TestCadcCache)
suite3 = unittest.TestLoader().loadTestsFromTestCase(TestCacheError)
suite4 = unittest.TestLoader().loadTestsFromTestCase(TestCacheRetry)
suite5 = unittest.TestLoader().loadTestsFromTestCase(TestSharedLock)
suite6 = unittest.TestLoader().loadTestsFromTestCase(TestCacheCondtion)
alltests = unittest.TestSuite([suite1, suite2, suite3, suite4, suite5, suite6])
unittest.TextTestRunner(verbosity=2).run(alltests)

