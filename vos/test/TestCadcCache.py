# Test the cadcCache module.

import io
import logging
import os
import errno
import stat
import sys
import threading
import thread
import time
import copy
from contextlib import nested
import shutil
import unittest
from mock import Mock, MagicMock, patch
import functools
import pdb

from vos import CadcCache
from vos.SharedLock import SharedLock, TimeoutError, RecursionError

# To run individual tests, set the value of skipTests to True, and comment
# out the @unittest.skipIf line at the top of the test to be run.
skipTests = False
testDir = "/tmp/testcache"

class Object(object):
    pass

class IOProxyForTest(CadcCache.IOProxy):
    """
    Subclass of the IOProxy class. Used for both testing the
    IOProxy class and as an IOProxy object when testing the Cache class.
    """
    def __init__(self):
        CadcCache.IOProxy.__init__(self)
        self.size = 0
        self.md5 = 'd41d8cd98f00b204e9800998ecf8427e'

    def getMD5(self):
        return self.md5

    def getSize(self):
        return self.size

    def delNode(self, force = False):
        return

    def verifyMetaData(self, md5sum):
        """Generic test returns true"""
        return True

    def writeToBacking(self):
        return '0xabcdef'

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

    def writeToBacking(self):
        return 0xabcdef

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
                testIOProxy.writeToBacking();
            with self.assertRaises(NotImplementedError):
                testIOProxy.readFromBacking();

    #@unittest.skipIf(skipTests, "Individual tests")
    def test_writeToCache(self):
        """Test the IOProxy writeToCache method
        """
        with CadcCache.Cache(testDir, 100, True) as testCache:
            testIOProxy = CadcCache.IOProxy()

            testIOProxy.cache = testCache
            testIOProxy.cacheFile = Object()
            testIOProxy.cacheFile.readThread = Object()
            testIOProxy.cacheFile.readThread.aborted = False
            testIOProxy.cacheFile.readThread.mandatoryEnd = \
                    testCache.IO_BLOCK_SIZE
            testIOProxy.cacheFile.metaData = Object()
            testIOProxy.cacheFile.metaData.setReadBlocks = Mock()
            testIOProxy.cacheFile.fileLock = threading.RLock()
            testIOProxy.cacheFile.fileCondition = \
                    CadcCache.CacheCondition(testIOProxy.cacheFile.fileLock)
            testIOProxy.cacheFile.path = "atestfile"

            # Write to beginning of the file
            with nested( patch('os.lseek'), patch('os.write')) as mocks:
                mock_lseek = mocks[0]
                mock_write = mocks[1]
                mock_lseek.return_value = 0
                mock_write.return_value = 3
                testIOProxy.cacheFile.fileSize = 3
                self.assertEqual(3, testIOProxy.writeToCache("abc", 0))
                testIOProxy.cacheFile.metaData.setReadBlocks.\
                        assert_called_once_with(0,0)
                mock_lseek.assert_called_once_with(None, 0, os.SEEK_CUR)
                mock_write.assert_called_once_with(None, "abc")

            # Write to after the beginning of the output
            with nested( patch('os.lseek'), patch('os.write')) as mocks:
                mock_lseek = mocks[0]
                mock_write = mocks[1]
                mock_lseek.return_value = 0
                mock_write.return_value = 3
                testIOProxy.cacheFile.fileSize = testCache.IO_BLOCK_SIZE + 3
                self.assertEqual(3, testIOProxy.writeToCache("abc",
                    testCache.IO_BLOCK_SIZE))
                mock_lseek.assert_called_with(None, testCache.IO_BLOCK_SIZE, 
                        os.SEEK_SET)
                self.assertEqual(mock_lseek.call_count, 2)

            # Test an erroneous seek to the middle of a block
            with nested( patch('os.lseek'), patch('os.write')) as mocks:
                mock_lseek = mocks[0]
                mock_lseek.return_value = 0
                with self.assertRaises(CadcCache.CacheError) as cm:
                    testIOProxy.writeToCache("abc", 3)

            # Test an attempt to write past the end fo the file.
            with nested( patch('os.lseek'), patch('os.write')) as mocks:
                mock_lseek = mocks[0]
                mock_lseek.return_value = 0
                testIOProxy.cacheFile.fileSize = 3
                with self.assertRaises(CadcCache.CacheError) as cm:
                    testIOProxy.writeToCache("abcdef", 0)

            # Test a seek when the last block write to the end of the file.
            with nested( patch('os.lseek'), patch('os.write')) as mocks:
                mock_lseek = mocks[0]
                mock_write = mocks[1]
                testIOProxy.cacheFile.fileSize = \
                        testCache.IO_BLOCK_SIZE * 2 + 10
                mock_lseek.return_value = testIOProxy.cacheFile.fileSize
                mock_write.return_value = 6
                testIOProxy.cacheFile.metaData.setReadBlocks.call_count = 0
                self.assertEqual(testIOProxy.writeToCache("abcdef", 0), 6)
                self.assertEqual( testIOProxy.cacheFile.metaData.
                        setReadBlocks.call_count, 0)

            # Write the second block, and the first 10 bytes of the third block
            # (to the nd of file). This should result in the second and third
            # blocks being marked complete.
            with nested( patch('os.lseek'), patch('os.write')) as mocks:
                mock_lseek = mocks[0]
                mock_write = mocks[1]
                testIOProxy.cacheFile.fileSize = \
                        (testCache.IO_BLOCK_SIZE * 2) + 10
                mock_lseek.return_value = 0
                mock_write.return_value = testCache.IO_BLOCK_SIZE + 10
                buffer = bytearray(testCache.IO_BLOCK_SIZE + 10)
                testIOProxy.cacheFile.metaData.setReadBlocks.call_count = 0
                self.assertEqual(testCache.IO_BLOCK_SIZE + 10, 
                        testIOProxy.writeToCache(buffer, testCache.IO_BLOCK_SIZE))
                testIOProxy.cacheFile.metaData.setReadBlocks.\
                        assert_called_once_with(1,2)

            # do a write which gets aborted
            testIOProxy.cacheFile.readThread.aborted = True
            with nested( patch('os.lseek'), patch('os.write')) as mocks:
                mock_lseek = mocks[0]
                mock_write = mocks[1]
                testIOProxy.cacheFile.fileSize = \
                        (testCache.IO_BLOCK_SIZE * 2) + 10
                mock_lseek.return_value = 0
                mock_write.return_value = testCache.IO_BLOCK_SIZE + 10
                buffer = bytearray(testCache.IO_BLOCK_SIZE + 10)
                testIOProxy.cacheFile.metaData.setReadBlocks.call_count = 0
                with self.assertRaises(CadcCache.CacheAborted):
                        testIOProxy.writeToCache(buffer, testCache.IO_BLOCK_SIZE)
                testIOProxy.cacheFile.metaData.setReadBlocks.\
                        assert_called_once_with(1,2)



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
            with nested( patch('os.lseek'), patch('os.read')) as mocks:
                mock_lseek = mocks[0]
                mock_read = mocks[1]
                mock_lseek.return_value = 0
                mock_read.return_value = "abc"
                testIOProxy.cache = testCache
                buffer = testIOProxy.readFromCache(0, 10)
                self.assertEqual("abc", buffer)
                mock_lseek.assert_called_once_with(None, 0, os.SEEK_CUR)

            # Read from the current position in the file
            with nested( patch('os.lseek'), patch('os.read')) as mocks:
                mock_lseek = mocks[0]
                mock_read = mocks[1]
                mock_lseek.return_value = 3
                mock_read.return_value = "def"
                buffer = testIOProxy.readFromCache(3, 10)
                self.assertEqual("def", buffer)
                mock_lseek.assert_called_once_with(None, 0, os.SEEK_CUR)

            # Read from a different position in the file
            with nested( patch('os.lseek'), patch('os.read')) as mocks:
                mock_lseek = mocks[0]
                mock_read = mocks[1]
                mock_lseek.return_value = 3
                mock_read.return_value = "ghi"
                buffer = testIOProxy.readFromCache(10, 10)
                self.assertEqual("ghi", buffer)
                mock_lseek.assert_called_with(None, 10, os.SEEK_SET)
                mock_read.assert_once_called_with(None, 10)
                self.assertEqual(mock_lseek.call_count, 2)


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

class TestCacheAborted(unittest.TestCase):
    @unittest.skipIf(skipTests, "Individual tests")
    def test_str(self):
        e = CadcCache.CacheAborted("a string")
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


# The test method names seem to influence the order, the tests are executed 
# alphabeticaly. Test failures make a bit more sense if low level stuff 
# fails first since some of the high level tests depend on low level tests. 
# To that end I have added the following digits into the test name to force an
# order:
# _00_ Very basic stuff with no dependancies and class constructors.
# _01_ Second level stuff which depends on the _00_ stuff
# _02_ Open methods - lots of test use open.
# _03_ release methods - lots of test use release.
# _04_ high level methods that use open and release as test set up

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
    def test__04_unlink(self):
        testIOProxy = IOProxyForTest()
        with CadcCache.Cache(testDir, 100, True) as testCache:
            # Unlink a non existing file. Should do nothing and not cause an 
            # error.
            testCache.unlinkFile("/dir1/dir2/nosuchfile")

            # Unlink a file which is open
            with testCache.open("/dir1/dir2/file", False, testIOProxy):
                with patch('os.remove') as mockedRemove:
                    testCache.unlinkFile("/dir1/dir2/file")
                self.assertEqual(mockedRemove.call_count, 2)

            #unlink a relative path, which should throw an exception.
            with self.assertRaises(ValueError):
                testCache.unlinkFile("dir1/file")

    @unittest.skipIf(skipTests, "Individual tests")
    def test_04_renameFile(self):
        testIOProxy = IOProxyForTest()
        with CadcCache.Cache(testDir, 100, True) as testCache:
            # Rename a non-existing file. Should do nothing as this could be an
            # existing file which is not cached. Not failing is the only
            # requirement.
            testCache.renameFile("/dir1/dir2/file", "/dir1/dir3/file")

            # Rename an existing but inactive file. 
            dataFile = os.path.join(testCache.dataDir, "dir1/dir2/file")
            metaDataFile = os.path.join(testCache.metaDataDir, "dir1/dir2/file")
            newDataFile = os.path.join(testCache.dataDir, "dir1/dir3/file")
            newMetaDataFile = os.path.join(testCache.metaDataDir, "dir1/dir3/file")
            self.makeTestFile(dataFile, self.testSize)
            self.makeTestFile(metaDataFile, 0)
            self.assertTrue(os.path.exists(dataFile))
            self.assertTrue(os.path.exists(metaDataFile))
            testCache.renameFile("/dir1/dir2/file", "/dir1/dir3/file")
            self.assertFalse(os.path.exists(dataFile))
            self.assertFalse(os.path.exists(metaDataFile))
            self.assertTrue(os.path.exists(newDataFile))
            self.assertTrue(os.path.exists(newMetaDataFile))
    
            # Rename an existing active file.
            with testCache.open("/dir1/dir2/file2", True, testIOProxy) as fh:
                testCache.renameFile("/dir1/dir2/file2", "/dir1/dir3/file3")
                self.assertEqual(fh.cacheDataFile, os.path.join(
                        testCache.dataDir, "dir1/dir3/file3"))
                self.assertEqual(fh.cacheMetaDataFile, os.path.join(
                        testCache.metaDataDir, "dir1/dir3/file3"))

            # Rename an existing active file. This time there should be a meta
            # data file because of the initial open.
            with testCache.open("/dir1/dir2/file", True, testIOProxy) as fh:
                pass
            with testCache.open("/dir1/dir2/file", True, testIOProxy) as fh:
                testCache.renameFile("/dir1/dir2/file", "/dir1/dir3/file3")
                self.assertEqual(fh.cacheDataFile, os.path.join(
                        testCache.dataDir, "dir1/dir3/file3"))
                self.assertEqual(fh.cacheMetaDataFile, os.path.join(
                        testCache.metaDataDir, "dir1/dir3/file3"))
            self.assertFalse(os.path.exists(dataFile))
            self.assertFalse(os.path.exists(metaDataFile))
            self.assertTrue(os.path.exists(newDataFile))
            self.assertTrue(os.path.exists(newMetaDataFile))

            # Rename into the same file again to test overwrite
            self.assertTrue(os.path.exists(newDataFile))
            self.assertTrue(os.path.exists(newMetaDataFile))
            with testCache.open("/dir1/dir2/file", True, testIOProxy) as fh:
                testCache.renameFile("/dir1/dir2/file", "/dir1/dir3/file3")
                self.assertEqual(fh.cacheDataFile, os.path.join(
                        testCache.dataDir, "dir1/dir3/file3"))
                self.assertEqual(fh.cacheMetaDataFile, os.path.join(
                        testCache.metaDataDir, "dir1/dir3/file3"))
            self.assertFalse(os.path.exists(dataFile))
            self.assertFalse(os.path.exists(metaDataFile))
            self.assertTrue(os.path.exists(newDataFile))
            self.assertTrue(os.path.exists(newMetaDataFile))



            # Rename a file with relative paths. Should fail.
            with self.assertRaises(ValueError):
                testCache.renameFile("dir1/file", "/dir2/file")
            with self.assertRaises(ValueError):
                testCache.renameFile("/dir1/file", "dir2/file")

            # Rename a directory. Should fail.
            with self.assertRaises(ValueError):
                testCache.renameFile("/dir1/dir2", "/dir3")


            # Cause an error when the meta data file is rename. This should
            # raise an exception and not rename either file.
            with testCache.open("/dir1/dir2/file", True, testIOProxy) as fh:
                try:
                    newMetaDataFile = os.path.join(testCache.metaDataDir, 
                            "dir1/dir3/file4")
                    newDataFile = os.path.join(testCache.dataDir, 
                            "dir1/dir3/file4")
                    self.makeTestFile(dataFile, self.testSize)
                    self.makeTestFile(metaDataFile, 0)
                    self.assertTrue(os.path.exists(dataFile))
                    self.assertTrue(os.path.exists(metaDataFile))
                    self.assertFalse(os.path.exists(newDataFile))
                    self.assertFalse(os.path.exists(newMetaDataFile))
                    # Make the directory where the meta-data is non-writeable.
                    os.chmod(os.path.dirname(metaDataFile), stat.S_IRUSR)

                    with self.assertRaises(OSError):
                        testCache.renameFile("/dir1/dir2/file", 
                                "/dir1/dir3/file4")
                    self.assertEqual(fh.cacheDataFile, os.path.join(
                            testCache.dataDir, "dir1/dir2/file"))
                    self.assertEqual(fh.cacheMetaDataFile, os.path.join(
                            testCache.metaDataDir, "dir1/dir2/file"))
                    os.chmod(os.path.dirname(metaDataFile), stat.S_IRWXU)
                    self.assertTrue(os.path.exists(dataFile))
                    self.assertTrue(os.path.exists(metaDataFile))
                    self.assertFalse(os.path.exists(newDataFile))
                    self.assertFalse(os.path.exists(newMetaDataFile))
                finally:
                    try:
                        os.chmod(os.path.dirname(metaDataFile), stat.S_IRWXU)
                        os.remove(dataFile)
                        os.remove(metaDataFile)
                        fh.release()
                    except:
                        pass


    @unittest.skipIf(skipTests, "Individual tests")
    def test_04_renameFile2(self):

        testIOProxy = IOProxyForTest()
        with CadcCache.Cache(testDir, 100, True) as testCache:

            # Rename an existing but inactive file. renaming the meta data file
            # will cause an error
            dataFile = os.path.join(testCache.dataDir, "dir1/dir2/file")
            metaDataFile = os.path.join(testCache.metaDataDir, "dir1/dir2/file")
            newDataFile = os.path.join(testCache.dataDir, "dir1/dir5/file")
            newMetaDataFile = os.path.join(testCache.metaDataDir, "dir1/dir5/file")
            self.makeTestFile(dataFile, self.testSize)
            self.makeTestFile(metaDataFile, 0)
            self.assertTrue(os.path.exists(dataFile))
            self.assertTrue(os.path.exists(metaDataFile))
            try:
                os.chmod(os.path.dirname(metaDataFile), stat.S_IRUSR)
                with self.assertRaises(OSError):
                    testCache.renameFile("/dir1/dir2/file", "/dir1/dir5/file")
            finally:
                os.chmod(os.path.dirname(metaDataFile), stat.S_IRWXU)
            self.assertTrue(os.path.exists(dataFile))
            self.assertTrue(os.path.exists(metaDataFile))
            self.assertFalse(os.path.exists(newDataFile))
            self.assertFalse(os.path.exists(newMetaDataFile))

            # Rename a file to a directory. Should cause an error.
            with self.assertRaises(ValueError):
                testCache.renameFile("/dir1/dir2", "/")

            # Rename a file to a directory. For this one the meta data file is
            # a directory.
            with patch('os.path.isdir') as mockedisdir:
                def returnFalse(arg):
                    mockedisdir.side_effect = returnTrue
                    return False

                def returnTrue(arg):
                    return True

                mockedisdir.side_effect = returnFalse
                with self.assertRaises(ValueError):
                    testCache.renameFile("/something", "/something")

    @unittest.skipIf(skipTests, "Individual tests")
    def test_04_renameDir(self):
        """Rename a whole directory.
        """
        testIOProxy1 = IOProxyForTest()
        testIOProxy2 = IOProxyForTest()
        testIOProxy3 = IOProxyForTest()

        with CadcCache.Cache(testDir, 100, True) as testCache:
            with self.assertRaises(ValueError):
                testCache.renameDir("adir", "anotherDir")
            with self.assertRaises(ValueError):
                testCache.renameDir("/adir", "anotherDir")

            with testCache.open("/dir1/dir2/file1", True, testIOProxy1) as fh1:
             with testCache.open("/dir1/dir2/file2", True, testIOProxy2) as fh2:
              with testCache.open("/dir2/dir2/file1", True, testIOProxy3) as fh3:
                with self.assertRaises(ValueError):
                    testCache.renameDir("/dir1/dir2/file1", "/dir1/dir3")
                testCache.renameDir("/dir1/dir2", "/dir1/dir3")
                self.assertEqual(fh1.cacheDataFile, os.path.join(
                        testCache.dataDir, "dir1/dir3/file1"))
                self.assertEqual(fh1.cacheMetaDataFile, os.path.join(
                        testCache.metaDataDir, "dir1/dir3/file1"))
                self.assertEqual(fh2.cacheDataFile, os.path.join(
                        testCache.dataDir, "dir1/dir3/file2"))
                self.assertEqual(fh2.cacheMetaDataFile, os.path.join(
                        testCache.metaDataDir, "dir1/dir3/file2"))
                self.assertEqual(fh3.cacheDataFile, os.path.join(
                        testCache.dataDir, "dir2/dir2/file1"))
                self.assertEqual(fh3.cacheMetaDataFile, os.path.join(
                        testCache.metaDataDir, "dir2/dir2/file1"))
                self.assertTrue(os.path.exists(fh1.cacheDataFile))
                self.assertTrue(os.path.exists(fh2.cacheDataFile))
                self.assertTrue(os.path.exists(fh3.cacheDataFile))
             with testCache.open("/dir1/dir2/file2", True, testIOProxy2) as fh2:
                with self.assertRaises(OSError):
                    testCache.renameDir("/dir1/dir2", "/dir1/dir3")

            # Rename a directory to a file. For this one, the meta data dir
            # shows up as a file.
            with patch('os.path.isfile') as mockedisfile:
                def returnFalse(arg):
                    mockedisfile.side_effect = returnTrue
                    return False

                def returnTrue(arg):
                    return True

                mockedisfile.side_effect = returnFalse
                with self.assertRaises(ValueError):
                    testCache.renameDir("/something", "/something")


    @unittest.skipIf(skipTests, "Individual tests")
    def test_04_getAttr(self):
        """test the getAttr method."""

        testIOProxy = IOProxyForTest()
        with CadcCache.Cache(testDir, 100, False) as testCache:
            # Try to get the attributes for a non-existing file.
            self.assertEqual(testCache.getAttr("/no/such/file"), None)

            # Try to get the attribute of an existing, open and modified file.
            # This should return the cache file attributes.
            with testCache.open("/dir1/dir2/file", True, testIOProxy) as fh:
                self.assertTrue(fh.fileModified)
                self.assertTrue(os.path.exists(testCache.dataDir + 
                        "/dir1/dir2/file"))
                attribs = testCache.getAttr( "/dir1/dir2/file")
                self.assertTrue(attribs is not None)
                fsAttribs = os.stat(fh.cacheDataFile )
                self.assertEqual(fsAttribs.st_ino, attribs["st_ino"])
            # The file is closed but still exists. Not attribute information
            # should be returned since VOSpace is the better source.
            self.assertTrue(os.path.exists(testCache.dataDir + 
                    "/dir1/dir2/file"))
            self.assertEqual(testCache.getAttr("/dir1/dir2/file"), None)

            # Test when a file is opened but not modified. Should return none
            # since vospace is the better source for information.
            with testCache.open("/dir1/dir2/file", False, testIOProxy) as fh:
                self.assertFalse(fh.fileModified)
                self.assertEqual(testCache.getAttr("/dir1/dir2/file"), None)



    @unittest.skipIf(skipTests, "Individual tests")
    def test_01_getFileHandle(self):
        """Test the getFileHandle method returns the same file handle for
           a file which is opened multiple times.
        """
        testIOProxy = IOProxyForTest()
        testIOProxy2 = IOProxyForTest()
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

            # Replace an already open file.
            self.assertFalse(testFile.obsolete)
            testFile2 = testCache.open("/dir1/dir2/file", True, testIOProxy2)
            self.assertTrue(testFile.obsolete)
            self.assertFalse(testFile2.obsolete)
            self.assertEqual(1, testFile.refCount)
            self.assertEqual(1, testFile2.refCount)
            self.assertFalse(testFile2 == testFile)


            testFile.release()
            self.assertEqual(0, testFile.refCount)

            # Relative path should cause an error.
            with self.assertRaises(ValueError):
                testCache.open("dir1/dir2/file", False, testIOProxy)

            # Replace an already open file, this time cleaning up the meta data
            # file throws an error.
            testFile = testCache.open("/dir1/dir2/file", False, 
                testIOProxy)
            with patch("os.remove") as mockedRemove:
                mockedRemove.side_effect = OSError(-1,-1)
                with self.assertRaises(OSError):
                    testFile2 = testCache.open("/dir1/dir2/file", True, 
                            testIOProxy2)



    @unittest.skipIf(skipTests, "Individual tests")
    def test_00_constructor1(self):
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
    def test_00_constructor2(self):
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
    def test_00_constructor3(self):
        """ Constructor with a file where the cache directory should be."""

        # create the file
        open(testDir, 'a').close()

        with self.assertRaises(CadcCache.CacheError) as cm:
            CadcCache.Cache(testDir, 100)

        self.assertTrue(str(cm.exception).find("is not a directory") > 0)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_00_constructor4(self):
        """ Constructor with a file where the cache data directory should be."""
        os.mkdir(testDir)
        open(testDir + "/data", 'a').close()

        with self.assertRaises(CadcCache.CacheError) as cm:
            CadcCache.Cache(testDir, 100)

        self.assertTrue(str(cm.exception).find("is not a directory") > 0)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_00_constructor5(self):
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
    def test_00_constructor6(self):
        """ Constructor with a file where the cache meta data directory 
            should be.
        """
        os.mkdir(testDir)
        open(testDir + "/metaData", 'a').close()

        with self.assertRaises(CadcCache.CacheError) as cm:
            CadcCache.Cache(testDir, 100)

        self.assertTrue(str(cm.exception).find("is not a directory") > 0)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_00_constructor7(self):
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
        directories = [ "dir1", "dir2", "dir3" ]
        files = [ "f1", "f2", "f3" ]
        for dir in directories:
            os.mkdir("/".join([ testDir , dir ]))
            for f in files:
                fh = open("/".join([ testDir, dir, f ]),  'a')
                fh.seek(1000)
                fh.write ("a")
                fh.close()

    @unittest.skipIf(skipTests, "Individual tests")
    def test_02_open1(self):
        # IOProxy - getMD5 fails with ENOENT
        class IOProxy_getMD5_ENOENT(IOProxyForTest):
            def getMD5(self):
                e = OSError("test failure")
                e.errno = errno.ENOENT
                raise e

        with CadcCache.Cache(testDir, 100) as testObject:
            ioObject = IOProxyFor100K()
            fh = testObject.open("/dir1/dir2/file", False, ioObject)
            self.assertFalse(fh.fullyCached)
            self.assertFalse(fh.fileModified)
            fh.release()

            ioObject = IOProxy_getMD5_ENOENT()
            with self.assertRaises(OSError):
                fh = testObject.open("/dir1/dir2/file", False, ioObject)
            fh.release()

    @unittest.skipIf(skipTests, "Individual tests")
    def test_02_open2(self):
        """ Open a new file"""
        with CadcCache.Cache(testDir, 100) as testObject:
            ioObject = IOProxyForTest()
            ioObject2 = IOProxyForTest()
            fh = testObject.open("/dir1/dir2/file", True, ioObject)
            self.assertTrue(fh.fullyCached)
            self.assertTrue(fh.fileModified)
            fh.release()

            fh = testObject.open("/dir1/dir2/file", False, ioObject)
            #existing meta data should be there 
            self.assertTrue(fh.fullyCached)
            self.assertFalse(fh.fileModified)
            fh2 = testObject.open("/dir1/dir2/file", True, ioObject2)
            self.assertTrue(fh2.fullyCached)
            self.assertTrue(fh2.fileModified)
            #meta data deleted.
            fh.release()
            fh2.release()

    @unittest.skipIf(skipTests, "Individual tests")
    def test_02_open3(self):
        """ Open a new file"""
        with CadcCache.Cache(testDir, 100) as testObject:
            ioObject = IOProxyForTest()
            ioObject2 = IOProxyForTest()
            fh = testObject.open("/dir1/dir2/file", True, ioObject)
            self.assertTrue(fh.fullyCached)
            self.assertTrue(fh.fileModified)
            fh.release()

            fh = testObject.open("/dir1/dir2/file", False, ioObject)
            fh2 = testObject.open("/dir1/dir2/file", True, ioObject2)
            fh.release()
            fh2.release()


    @unittest.skipIf(skipTests, "Individual tests")
    def test_03_release1(self):
        """Fails getting md5."""
        with CadcCache.Cache(testDir, 100) as testObject:
            ioObject = IOProxyForTest()
            ioObject.verifyMetaData = Mock(return_value=False)
            fh = testObject.open("/dir1/dir2/file", False, ioObject)
            fh.release()

            # Test flushnode raising an exception
            ioObject = IOProxyForTest()
            ioObject.verifyMetaData = Mock(return_value=False)
            fh = testObject.open("/dir1/dir2/file", False, ioObject)
            fh.fileModified = True
            fh.fileCondition.setTimeout = Mock(side_effect=Exception("failed"))
            with self.assertRaises(Exception) as cm:
                fh.release()


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
    def test_03_release2(self):
        class IOProxy_writeToBacking_slow(IOProxyForTest):
            def verifyMetaData(self, md5sum):
                """ test returns False """
                return False

            def writeToBacking(self, fh, mtime):
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
            fh = testObject.open("/dir1/dir2/file", False, ioObject2)
            fh.release()
            assert not ioObject2.writeToBacking.called, \
                    'writeToBacking was called and should not have been'


    def release2_sub1(self, testObject, ioObject):
        fh = testObject.open("/dir1/dir2/file", False, ioObject)
        fh.release()

    @unittest.skipIf(skipTests, "Individual tests")
    def test_03_release3(self):
        """Successful write to backing"""

        with CadcCache.Cache(testDir, 100) as testObject:
            # This should really flush the data to the backing
            ioObject = IOProxyForTest()
            ioObject.writeToBacking = MagicMock(return_value="1234")
            fh = testObject.open("/dir1/dir2/file", False, ioObject)
            self.makeTestFile(os.path.join(testObject.dataDir, 
                    "dir1/dir2/file"), self.testSize)
            fh.fileModified = True
            info = os.stat(fh.cacheDataFile)
            fh.release()
            ioObject.writeToBacking.assert_called_once_with()

    @unittest.skipIf(skipTests, "Individual tests")
    def test_03_release4(self):
        """Test aborting an in-progress cache read thread when a file is
           closed."""

        with CadcCache.Cache(testDir, 100, timeout=1) as testObject:
            # This should really flush the data to the backing
            ioObject = IOProxyForTest()
            fh = testObject.open("/dir1/dir2/file", True, ioObject)
            fh.readThread = CadcCache.CacheReadThread(0,0,0,fh)

            # This release should timeout, but the aborted flag should be set.
            self.assertFalse(fh.readThread.aborted)
            with self.assertRaises(CadcCache.CacheRetry):
                fh.release()
            self.assertTrue(fh.readThread.aborted)
            fh.readThread = None
            fh.release()

    @unittest.skipIf(skipTests, "Individual tests")
    def test_03_release5(self):
        """Flush returns an error.
        """

        with CadcCache.Cache(testDir, 100) as testObject:
            # This should really flush the data to the backing
            ioObject = IOProxyForTest()
            ioObject.writeToBacking = MagicMock(
                    side_effect=Exception("message"))
            fh = testObject.open("/dir1/dir2/file", False, ioObject)
            self.makeTestFile(os.path.join(testObject.dataDir, 
                    "dir1/dir2/file"), self.testSize)
            fh.fileModified = True
            info = os.stat(fh.cacheDataFile)
            with self.assertRaises(Exception):
                fh.release()




    @unittest.skipIf(skipTests, "Individual tests")
    def test_04_read1(self):
        """Test reading from a file which is not cached."""

        with CadcCache.Cache(testDir, 100) as testCache:
            ioObject = IOProxyFor100K()
            fh = testCache.open("/dir1/dir2/file", False, ioObject)
            data = fh.read(100,0)

            # Read beyond the end of the file.
            with self.assertRaises(ValueError):
                data = fh.read(100, 1024*1024)
            fh.release()


    @unittest.skipIf(skipTests, "Individual tests")
    def test_04_write1(self):
        """Test writing to a file which is not cached."""

        with CadcCache.Cache(testDir, 100) as testCache:
            ioObject = IOProxyFor100K()
            with testCache.open("/dir1/dir2/file", True, ioObject) as fh:
                fh.write( "abcd", 4, 30000)
                data = fh.read( 4, 30000)
                self.assertEqual(data[:], "abcd")


    @unittest.skipIf(skipTests, "Individual tests")
    def test_01_makeCached(self):
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

            def isNewReadBest(self, first, last):
                return False

        def sideEffectTrue(firstByte, lastByte):
            fh.readThread.isNewReadBest.side_effect = sideEffectFalse
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
            with testCache.open("/dir1/dir2/file", False, ioObject) as fh:
                fh.fullyCached = True
                oldMetaData = copy.deepcopy(fh.metaData)
                fh.metaData.getRange = Mock()
                fh.metaData.persist = Mock()
                fh.makeCached(0,1)
                self.assertEqual(fh.metaData.getRange.call_count, 0)
                fh.metaData = oldMetaData
                fh.fullyCached = False

            # Check that the block range correctly maps to bytes when
            # isNewReadBest is called. The call exits with a timeout.
            #with testCache.open("/dir1/dir2/file", False, ioObject) as fh:
            fh = testCache.open("/dir1/dir2/file", False, ioObject)
            fh.readThread=CadcCache.CacheReadThread(0,0,0,fh)
            fh.readThread.isNewReadBest = Mock()
            fh.readThread.isNewReadBest.side_effect = sideEffectTrue
            fh.fileCondition.setTimeout()
            fh.metaData.delete()
            with self.assertRaises(CadcCache.CacheRetry):
                fh.makeCached(0,1)
            self.assertEqual(fh.readThread.isNewReadBest.call_count, 1)
            fh.readThread=None
            fh.release()

            # The required range is cached. The fn exists after calling
            # getRange.
            with testCache.open("/dir1/dir2/file", False, ioObject) as fh:
                fh.readThread=CadcCache.CacheReadThread(0,0,0,fh)
                oldMetaData = fh.metaData
                fh.metaData = copy.deepcopy(oldMetaData)
                fh.readThread.isNewReadBest = Mock()
                fh.metaData.getRange = Mock(return_value=(None,None))
                fh.makeCached(0,1)
                self.assertEqual(fh.metaData.getRange.call_count, 1)
                self.assertEqual(fh.readThread.isNewReadBest.call_count, 0)
                fh.metaData = oldMetaData
                fh.readThread=None

            # Check that the block range correctly maps to bytes when
            # isNewReadBest is called. The call exits when data is available.
            with testCache.open("/dir1/dir2/file", False, ioObject) as fh:
                oldMetaData = copy.deepcopy(fh.metaData)
                fh.metaData.persist = Mock()
                fh.readThread=CadcCache.CacheReadThread(0,0,0,fh)
                fh.readThread.isNewReadBest = Mock()
                fh.readThread.isNewReadBest.side_effect = sideEffectTrue
                fh.fileCondition.setTimeout()
                fh.metaData.delete()
                t1 = threading.Thread(target=self.notifyReMockRange,
                        args=[fh.fileCondition,fh])
                t1.start()
                fh.makeCached(0,1)
                fh.metaData = oldMetaData
                fh.readThread=None

            # This call will cause the existing thread be be aborted, and a new
            # thread to be started. This will look like the data is available
            # immediately after the thread aborts, and so no new thread will
            # start. This test fails by timing out in the condition wait, 
            # which throws an exception.
            with testCache.open("/dir1/dir2/file", False, ioObject) as fh:
                oldMetaData = copy.deepcopy(fh.metaData)
                fh.readThread=CadcCache.CacheReadThread(0,0,0,fh)
                fh.readThread.isNewReadBest = Mock()
                fh.readThread.isNewReadBest.side_effect = sideEffectFalse
                fh.fileCondition.setTimeout()
                fh.metaData.delete()
                t1 = threading.Thread(target=self.notifyReMockRange,
                        args=[fh.fileCondition,fh])
                t1.start()
                fh.makeCached(0,1)
                fh.metaData = oldMetaData
                fh.readThread=None

            # This call will cause the existing thread be be aborted, and a new
            # thread to be started. The data will not seem to be availble, so a
            # new thread will be started. This test fails by timing out in the
            # condition wait, which throws an exception.
            with testCache.open("/dir1/dir2/file", False, ioObject) as fh:
                fh.fileCondition.setTimeout()
                fh.metaData.delete()
                t1 = threading.Thread(target=self.notifyAfter1S,
                        args=[fh.fileCondition,fh])
                t1.start()
                with patch('vos.CadcCache.CacheReadThread') as mockedClass:
                    mockedClass.return_value = CacheReadThreadMock(fh)
                    fh.makeCached(0,1)

            # This call will cause the optional end to be before the end of the
            # file because some data near the end of the file has been cached.
            with testCache.open("/dir1/dir2/file", False, ioObject) as fh:
                fh.fileCondition.setTimeout()
                fh.metaData.delete()
                t1 = threading.Thread(target=self.notifyAfter1S,
                        args=[fh.fileCondition,fh])
                t1.start()
                with patch('vos.CadcCache.CacheReadThread') as mockedClass:
                    realClass = mockedClass.returnValue
                    mockedClass.return_value = CacheReadThreadMock(fh)
                    fh.metaData.setReadBlocks(6, 6)
                    fh.metaData.md5sum = 12345
                    fh.fullyCached = False
                    fh.makeCached(0,1)
                    # TODO figure out a way to test the result. The init method
                    # of CacheReadThreadMock should be with arguments which get
                    # the first block as mandatory, and everything except the
                    # last block as optional


    @unittest.skipIf(skipTests, "Individual tests")
    def test_04_fsync(self):
        """ Test the fsync method"""

        testIOProxy = IOProxyForTest()

        testCache = CadcCache.Cache(cacheDir = testDir, maxCacheSize = 4)
        with testCache.open("/dir1/dir2/file", False, testIOProxy) as testFile:
            testFile.fsync()


    def notifyReMockRange(self,cond,fh):
        time.sleep(1)
        # Make getRange return None,None
        with cond:
            fh.metaData.getRange = Mock(return_value=(None,None))
            cond.notify_all()

    def notifyAfter1S(self,cond,fh):
        time.sleep(1)
        # Make getRange return None,None
        with cond:
            cond.notify_all()


    @unittest.skipIf(skipTests, "Individual tests")
    def test_00_determineCacheSize(self):
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
    def test_00_checkCacheSpace(self):
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

    @unittest.skipIf(skipTests, "Individual tests")
    def test_04_removeEmptyDirs(self):
        """ Test cache cleanup """

        with CadcCache.Cache(testDir, 100, timeout=2) as testCache:
            # Remove directories that are not in the cache.
            with self.assertRaises(ValueError):
                testCache.removeEmptyDirs("/tmp/noshuchdir")

            os.makedirs(testDir + "/dir1/dir2/dir3")
            os.makedirs(testDir + "/dir1/dir2/dir4")
            testCache.removeEmptyDirs(testDir + "/dir1/dir2/dir3")
            self.assertFalse(os.path.exists(testDir + "/dir1/dir2/dir3"))
            self.assertTrue(os.path.exists(testDir + "/dir1/dir2"))
            self.assertTrue(os.path.exists(testDir + "/dir1/dir2/dir4"))
            testCache.removeEmptyDirs(testDir + "/dir1/dir2/dir4")
            self.assertFalse(os.path.exists(testDir + "/dir1"))
            self.assertTrue(os.path.exists(testDir))

            # Have os.rmdir throw an error other than ENOTEMPTY and ENOENT
            os.makedirs(testDir + "/dir1/dir2/dir3")
            with patch('os.rmdir') as rmdir:
                rmdir.side_effect = OSError(-1,-1)
                with self.assertRaises(OSError):
                    testCache.removeEmptyDirs(testDir + "/dir1/dir2/dir4")

    @unittest.skipIf(skipTests, "Individual tests")
    def test_04_truncate(self):
        """ Test file truncate"""
        testIOProxy = IOProxyForTest()
        testIOProxy.writeToBacking = Mock(wraps=testIOProxy.writeToBacking)
        testIOProxy.readFromBacking = Mock(wraps=testIOProxy.readFromBacking)
        with CadcCache.Cache(testDir, 100, timeout=2) as testCache:
            # Expand a new file
            with testCache.open("/dir1/dir2/file", True, testIOProxy) as \
                    testFile:
                testFile.truncate(10)
                self.assertTrue(testFile.fileModified)
                self.assertTrue(testFile.fullyCached)
                self.assertEqual(os.path.getsize(testFile.cacheDataFile), 10)
            testIOProxy.writeToBacking.assert_called_once_with()
            self.assertEqual(testIOProxy.readFromBacking.call_count, 0)
            testIOProxy.size  = 10

            # Set a file to its current size
            testIOProxy.writeToBacking.reset_mock()
            testIOProxy.readFromBacking.reset_mock()
            self.assertEqual(os.path.getsize(testFile.cacheDataFile), 10)
            with testCache.open("/dir1/dir2/file", False, testIOProxy) as \
                    testFile:
                testFile.truncate(10)
                self.assertFalse(testFile.fileModified)
                self.assertFalse(testFile.fullyCached)
            # This first assert is odd - testIOProxy returns zero bytes from
            # readFromBacking, so the file gets set to 0 size and not changed
            # because truncate thinks it isn't changing the file size of 10
            # bytes.
            self.assertEqual(os.path.getsize(testFile.cacheDataFile), 0)
            self.assertEqual(testIOProxy.writeToBacking.call_count, 0)
            self.assertEqual(testIOProxy.readFromBacking.call_count, 0)

            # Expand a file from 10 bytes
            testIOProxy.writeToBacking.reset_mock()
            testIOProxy.readFromBacking.reset_mock()
            with testCache.open("/dir1/dir2/file", False, testIOProxy) as \
                    testFile:
                testFile.truncate(testCache.IO_BLOCK_SIZE * 2 + 20)
                self.assertTrue(testFile.fileModified)
                self.assertTrue(testFile.fullyCached)
                self.assertEqual(os.path.getsize(testFile.cacheDataFile),
                    testCache.IO_BLOCK_SIZE * 2 + 20)
            testIOProxy.writeToBacking.assert_called_once_with()
            self.assertEqual(testIOProxy.readFromBacking.call_count, 1)
            testIOProxy.size  = testCache.IO_BLOCK_SIZE * 2 + 20
            testIOProxy.md5 = '0xabcdef'


            # Shrink a big file down. The file should be cached locally and not
            # read from backing.
            testIOProxy.writeToBacking.reset_mock()
            testIOProxy.readFromBacking.reset_mock()
            with testCache.open("/dir1/dir2/file", False, testIOProxy) as \
                    testFile:
                self.assertFalse(testFile.fileModified)
                testFile.truncate(testCache.IO_BLOCK_SIZE * 2 + 21)
                self.assertTrue(testFile.fullyCached)
                self.assertEqual(os.path.getsize(testFile.cacheDataFile),
                        testCache.IO_BLOCK_SIZE * 2 + 21)
                # Set up for the shrink test
                testIOProxy.writeToBacking.reset_mock()
                testIOProxy.readFromBacking.reset_mock()
                testFile.truncate(10)
                self.assertTrue(testFile.fileModified)
                self.assertTrue(testFile.fullyCached)
                self.assertEqual(os.path.getsize(testFile.cacheDataFile), 10)
            testIOProxy.writeToBacking.assert_called_once_with()
            self.assertEqual(testIOProxy.readFromBacking.call_count, 0)
        

class TestCadcCacheReadThread(unittest.TestCase):
    """Test the CadcCache.CacheTreadThread class
    """
    class MyIoObject(CadcCache.IOProxy):
        def readFromBacking(self, size = None, offset = 0, 
            blockSize = CadcCache.Cache.IO_BLOCK_SIZE):
            pass
    
    class MyFileHandle(CadcCache.FileHandle):
        def __init__(self, path, cache, ioObject):
            CadcCache.FileHandle.__init__(self, path, cache, ioObject)
            self.ioObject = ioObject
            self.fileCondition = threading.Condition()
            self.fileSize = 0
            pass

    def setUp(self):
        pass

    def tearDown(self):
        pass
    
    
    @unittest.skipIf(skipTests, "Individual tests")
    def test_isNewReadBest(self):
        with CadcCache.Cache(testDir, 100, timeout=2) as testCache:
            dataBlock = 64*1024
            CadcCache.CacheReadThread.CONTINUE_MAX_SIZE = dataBlock
            start = dataBlock
            mandatoryEnd = 4 * dataBlock
            optionEnd = 7 * dataBlock
            ioObject = TestCadcCacheReadThread.MyIoObject()
            fh = TestCadcCacheReadThread.MyFileHandle("/dir1/dir2/file", 
                    testCache, ioObject)
            fh.metaData = Object()
            fh.metaData.getRange = Mock(return_value=(None,None))
            ioObject.setCacheFile(fh)
            crt = CadcCache.CacheReadThread(start = start, 
                                            mandatorySize = mandatoryEnd - start, 
                                            optionSize = optionEnd - start, 
                                            fileHandle = fh)
            crt.execute()
            
            # test when either start or end or requested interval is outside 
            # [start, start + optionSize)
            self.assertTrue(crt.isNewReadBest(0, dataBlock))
            self.assertEquals(mandatoryEnd, crt.mandatoryEnd)
            
            self.assertTrue(crt.isNewReadBest(start + dataBlock, optionEnd + dataBlock))
            # mandatoryEnd becomes optionEnd in this case
            self.assertEquals(optionEnd, crt.mandatoryEnd)
            crt.mandatoryEnd = mandatoryEnd #reset for next tests
            
            #current byte between current start and mandatory
            #requested start between current start and currentByte
            crt.setCurrentByte(3*dataBlock)
            #requested end between current start and current byte
            self.assertFalse(crt.isNewReadBest(dataBlock, dataBlock))
            self.assertEquals(mandatoryEnd, crt.mandatoryEnd)
            #requested end between current byte and current mandatory
            self.assertFalse(crt.isNewReadBest(dataBlock, 3 * dataBlock))
            self.assertEquals(mandatoryEnd, crt.mandatoryEnd)
            #requested end between current mandatory and current optional
            self.assertFalse(crt.isNewReadBest(dataBlock, 5 * dataBlock))
            #requested end becomes the current mandatory
            self.assertEquals(start + 5 * dataBlock, crt.mandatoryEnd)
            crt.mandatoryEnd = mandatoryEnd #reset for next tests
            
            #requested start between currentByte and current mandatory
            #requested end between current byte and current mandatory
            self.assertFalse(crt.isNewReadBest(3 * dataBlock, dataBlock))
            self.assertEquals(mandatoryEnd, crt.mandatoryEnd)
            #requested end between current mandatory and current optional
            self.assertFalse(crt.isNewReadBest(4 * dataBlock, 2 * dataBlock))
            #requested end becomes the current mandatory
            self.assertEquals(start + 5 * dataBlock, crt.mandatoryEnd)
            crt.mandatoryEnd = mandatoryEnd #reset for next tests
            
            #requested start between currentByte and current mandatory
            #requested end between current byte and current mandatory
            self.assertFalse(crt.isNewReadBest(3 * dataBlock, dataBlock))
            self.assertEquals(mandatoryEnd, crt.mandatoryEnd)
            #requested end between current mandatory and current optional
            self.assertFalse(crt.isNewReadBest(4 * dataBlock, 2 * dataBlock))
            #requested end becomes the current mandatory
            self.assertEquals(start + 5 * dataBlock, crt.mandatoryEnd)
            crt.mandatoryEnd = mandatoryEnd #reset for next tests
            
            #requested start between mandatoryEnd and optionalEnd
            #distance between mandatoryEnd and star is less than CONTINUE_MAX_SIZE
            self.assertFalse(crt.isNewReadBest(mandatoryEnd + dataBlock, dataBlock))
            self.assertEquals(mandatoryEnd + 2 * dataBlock, crt.mandatoryEnd) 
            crt.mandatoryEnd = mandatoryEnd #reset for next tests
            
            #distance between mandatoryEnd and star is less than CONTINUE_MAX_SIZE
            self.assertTrue(crt.isNewReadBest(mandatoryEnd + 2 * dataBlock, dataBlock))
            self.assertEquals(mandatoryEnd, crt.mandatoryEnd)
            
            
            #current byte between mandatory and optional
            crt.setCurrentByte(5 * dataBlock)
            #request start and end between start and mandatory
            self.assertFalse(crt.isNewReadBest(start + dataBlock, dataBlock))
            self.assertEquals(mandatoryEnd, crt.mandatoryEnd)
            
            #request end between mandatory and current byte
            self.assertFalse(crt.isNewReadBest(start + dataBlock, 4 * dataBlock))
            self.assertEquals(6 * dataBlock, crt.mandatoryEnd)
            crt.mandatoryEnd = mandatoryEnd #reset for next tests
        
            # request end after mandatory
            self.assertFalse(crt.isNewReadBest(start + dataBlock, 5 * dataBlock))
            self.assertEquals(7 * dataBlock, crt.mandatoryEnd)
            crt.mandatoryEnd = mandatoryEnd #reset for next tests
            
            #start between mandatory and current byte
            # end between mandatory and current byte
            self.assertFalse(crt.isNewReadBest(mandatoryEnd, dataBlock))
            self.assertEquals(5 * dataBlock, crt.mandatoryEnd)
            #end between current byte and optional byte
            self.assertFalse(crt.isNewReadBest(mandatoryEnd + dataBlock, dataBlock))
            self.assertEquals(6 * dataBlock, crt.mandatoryEnd)
            crt.mandatoryEnd = mandatoryEnd #reset for next tests
            
            #start after current byte but with less then CONTINUE_MAX_SIZE
            self.assertFalse(crt.isNewReadBest(5 * dataBlock, dataBlock))
            self.assertEquals(6 * dataBlock, crt.mandatoryEnd)
            crt.mandatoryEnd = mandatoryEnd #reset for next tests
            
            # start after current byte but with more then  CONTINUE_MAX_SIZE
            self.assertFalse(crt.isNewReadBest(start + dataBlock, dataBlock))
            self.assertEquals(mandatoryEnd, crt.mandatoryEnd)
        

logging.getLogger('CadcCache').setLevel(logging.DEBUG)
logging.getLogger('CadcCache').addHandler(logging.StreamHandler())

suite1 = unittest.TestLoader().loadTestsFromTestCase(TestCacheCondtion)
suite2 = unittest.TestLoader().loadTestsFromTestCase(TestSharedLock)
suite3 = unittest.TestLoader().loadTestsFromTestCase(TestCacheError)
suite4 = unittest.TestLoader().loadTestsFromTestCase(TestCacheRetry)
suite5 = unittest.TestLoader().loadTestsFromTestCase(TestCacheAborted)
suite6 = unittest.TestLoader().loadTestsFromTestCase(TestIOProxy)
suite7 = unittest.TestLoader().loadTestsFromTestCase(TestCadcCacheReadThread)
suite8 = unittest.TestLoader().loadTestsFromTestCase(TestCadcCache)
alltests = unittest.TestSuite([suite1, suite2, suite3, suite4, suite5, 
        suite6, suite7, suite8])
unittest.TextTestRunner(verbosity=2).run(alltests)

