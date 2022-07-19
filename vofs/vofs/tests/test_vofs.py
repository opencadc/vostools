from __future__ import (absolute_import, division, print_function,
                        unicode_literals)
from builtins import str
from builtins import object
import copy
import os
import shutil
import stat
import ctypes
from fuse import FuseOSError, fuse_operations
from errno import EIO, EPERM, ENOENT
import unittest
from mock import Mock, MagicMock, patch, ANY
import vos
from cadcutils import exceptions
from vofs.CadcCache import Cache, CacheRetry, CacheAborted, FileHandle, \
    IOProxy, FlushNodeQueue
from vos.node_cache import NodeCache
from vofs.vofs import HandleWrapper, MyFuse, VOFS
from vofs import vofs

skipTests = False


class Object(object):
    pass


class MyFileHandle(FileHandle):
    def __init__(self, path, cache, ioObject):
        anIOProxy = IOProxy()
        anIOProxy.writeToBacking = Mock()
        super(MyFileHandle, self).__init__(path, cache, anIOProxy)

    def readData(self, start, mandatory, optional):
        self.setHeader(10, "12345")
        self.gotHeader = True
        with self.fileCondition:
            self.fileCondition.notify_all()
        return


class MyFileHandle2(FileHandle):
    def __init__(self, path, cache, ioObject):
        anIOProxy = IOProxy()
        anIOProxy.writeToBacking = Mock()
        super(MyFileHandle2, self).__init__(path, cache, anIOProxy)

    def readData(self, start, mandatory, optional):
        try:
            raise OSError(ENOENT, "No such file")
        except OSError:
            self.setReadException()
        with self.fileCondition:
            self.fileCondition.notify_all()
        return


class TestVOFS(unittest.TestCase):
    testMountPoint = "vos:tmp/testfs"
    testCacheDir = "/tmp/testCache"

    def __init__(self, *args, **kwargs):
        super(TestVOFS, self).__init__(*args, **kwargs)

    def initOpt(self):
        opt = Object
        opt.readonly = False
        opt.cache_nodes = False
        return opt

    def setUp(self):
        global opt
        opt = self.initOpt()
        if os.path.exists(self.testCacheDir):
            shutil.rmtree(self.testCacheDir)

    def tearDown(self):
        self.setUp()

    @unittest.skipIf(skipTests, "Individual tests")
    def test_init_(self):
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        self.assertTrue(testfs.opt is opt)
        self.assertEqual(testfs.root, self.testMountPoint)

        # Client connection fails
        with patch('vos.Client.__init__') as mock1:
            e = OSError()
            e.errno = EIO
            mock1.side_effect = e
            with self.assertRaises(FuseOSError):
                testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)

    @unittest.skipIf(skipTests, "Individual tests")
    def testWrite1(self):
        """ Write to a read-only or locked file"""
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        fileHandle = vofs.HandleWrapper(None, True)
        # Write some data at the start of the file. File is read only so it
        # returns 0

        with self.assertRaises(FuseOSError) as e:
            testfs.write("/dir1/dir2/file", "abcd", 4, 0, fileHandle.get_id())
        self.assertEqual(e.exception.errno, EPERM)

    @unittest.skipIf(skipTests, "Individual tests")
    def testWrite2(self):
        """ Write to a read-only file system"""
        opt.readonly = True
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        fileHandle = vofs.HandleWrapper(None, False)
        # Write some data at the start of the file.
        self.assertEqual(testfs.write("/dir1/dir2/file", "abcd", 4, 0,
                                      fileHandle), 0)

    @unittest.skipIf(skipTests, "Individual tests")
    def testWrite3(self):
        """Test a successfull write."""

        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        fileHandle = vofs.HandleWrapper(Object(), False)
        fileHandle.cache_file_handle.write = Mock()
        fileHandle.cache_file_handle.write.return_value = 4
        self.assertEqual(testfs.write("/dir1/dir2/file", "abcd", 4, 0,
                                      fileHandle.get_id()), 4)
        fileHandle.cache_file_handle.write.return_value = 4
        fileHandle.cache_file_handle.write.assert_called_once_with(
            "abcd", 4, 0)

        fileHandle.cache_file_handle.write.call_count = 0
        self.assertEqual(testfs.write("/dir1/dir2/file", "abcd", 4, 2048,
                                      fileHandle.get_id()), 4)
        fileHandle.cache_file_handle.write.return_value = 4
        fileHandle.cache_file_handle.write.assert_called_once_with(
            "abcd", 4, 2048)

    # @unittest.skipIf(skipTests, "Individual tests")
    # def testWrite4(self):
    #     """Test a timout during write"""
    #
    #     testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
    #     fileHandle = vofs.HandleWrapper(Object(), False)
    #     fileHandle.cache_file_handle.write = Mock()
    #     fileHandle.cache_file_handle.write.side_effect = CacheRetry("fake")
    #     with self.assertRaises(FuseOSError) as e:
    #         testfs.write( "/dir1/dir2/file", "abcd", 4, 2048,
    # fileHandle.get_id())
    #     self.assertEqual(e.exception.errno, EAGAIN)

    @unittest.skipIf(skipTests, "Individual tests")
    def testWrite5(self):
        """Test write to invaid file descriptor"""
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        with self.assertRaises(FuseOSError) as e:
            testfs.write("/dir1/dir2/file", "abcd", 4, 2048, -1)
        self.assertEqual(e.exception.errno, EIO)

    # @unittest.skipIf(skipTests, "Individual tests")
    def testRead1(self):
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)

        buf = ctypes.create_string_buffer(2048)
        # Read with a null file handle.
        with self.assertRaises(FuseOSError) as e:
            testfs.read("/dir1/dir2/file", 4, 2048, buf=buf)
        self.assertEqual(e.exception.errno, EIO)

        # Read with a timeout.
        # fileHandle = vofs.HandleWrapper(Object())
        # fileHandle.cache_file_handle.read = Mock()
        # fileHandle.cache_file_handle.read.side_effect = CacheRetry("fake")
        # with self.assertRaises(FuseOSError) as e:
        #     testfs.read( "/dir1/dir2/file", 4, 2048, fileHandle.get_id())
        # self.assertEqual(e.exception.errno, EAGAIN)

        # Read with success.
        fileHandle = vofs.HandleWrapper(Object())
        fileHandle.cache_file_handle.read = Mock()
        fileHandle.cache_file_handle.read.return_value = len("abcd")
        self.assertEqual(testfs.read(path="/dir1/dir2/file", buf=buf,
                                     size=4, offset=2048,
                                     file_id=fileHandle.get_id()), 4)

        # Read from an invalid file handle.
        with self.assertRaises(FuseOSError) as e:
            testfs.read(path="/dir1/dir2/file", buf=buf, size=4, offset=2048,
                        file_id=-1)
        self.assertEqual(e.exception.errno, EIO)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_open(self):
        myVofs = vofs.VOFS("vos:", self.testCacheDir, opt)
        file = "/dir1/dir2/file"
        file2 = "/dir1/dir2/file2"
        file3 = "/dir1/dir2/file2"
        myVofs.cache.getAttr = Mock()
        myVofs.cache.getAttr.return_value = None

        # getNode return not found
        myVofs.get_node = Mock()
        myVofs.get_node.side_effect = OSError(404, "NoFile")
        with patch('vofs.CadcCache.FileHandle') as mockFileHandle:
            mockFileHandle.return_value = MyFileHandle2(file, myVofs.cache,
                                                        None)
            mockFileHandle.return_value.readData = Mock(
                wraps=mockFileHandle.return_value.readData)
            fh = myVofs.open(file, os.O_RDWR | os.O_CREAT, None)
            self.assertEqual(self.testCacheDir + "/data" + file,
                             HandleWrapper.file_handle(
                                 fh).cache_file_handle.cacheDataFile)
            self.assertEqual(self.testCacheDir + "/metaData" + file,
                             HandleWrapper.file_handle(fh).cache_file_handle.
                             cacheMetaDataFile)
            self.assertFalse(HandleWrapper.file_handle(fh).read_only)
            self.assertEqual(
                mockFileHandle.return_value.readData.call_count, 1)
            myVofs.release(file, fh)

            # Try to open a file which doesn't exist.
            with self.assertRaises(FuseOSError):
                fh = myVofs.open(file2, os.O_RDWR, None)

            myVofs.get_node.side_effect = OSError(ENOENT, "no file")
            # Open where getNode returns an error
            with self.assertRaises(FuseOSError):
                fh = myVofs.open(file2, os.O_RDWR, None)

        # test file in the cache already
        myVofs.cache.getAttr = Mock()
        myVofs.cache.getAttr.return_value = Mock()
        # fhMock = Mock()
        # myVofs.cache.open = Mock()
        # myVofs.cache.open.return_value = fhMock
        with patch('vofs.vofs.MyIOProxy') as myIOProxy, patch(
                'vofs.CadcCache.FileHandle') as mockFileHandle:
            mockFileHandle.return_value = MyFileHandle(
                file, myVofs.cache, None)
            mockFileHandle.return_value.readData = Mock(
                wraps=mockFileHandle.return_value.readData)
            myMockIOObject = Object()
            myIOProxy.return_value = myMockIOObject
            fh = myVofs.open(file, os.O_RDWR, None)
            mockFileHandle.return_value.readData.assert_called_once_with(
                0, 0, None)
            self.assertFalse(HandleWrapper.file_handle(
                fh).cache_file_handle.fileModified)
            # TODO fullyCached = True or False?
            # self.assertFalse(
            # HandleWrapper.file_handle(fh).cache_file_handle.fullyCached)

            # test a read-only file
            mockFileHandle.return_value.readData.reset_mock()
            myVofs.cache.getAttr = Mock()
            myVofs.cache.getAttr.return_value = Mock()
            HandleWrapper.file_handle(
                fh).cache_file_handle.readData.reset_mock()
            fh = myVofs.open(file, os.O_RDONLY, None)
            self.assertTrue(HandleWrapper.file_handle(fh).read_only)
            self.assertEqual(
                mockFileHandle.return_value.readData.call_count, 0)

            # test a truncated file
            myVofs.cache.open = Mock(wraps=myVofs.cache.open)
            fh = myVofs.open(file, os.O_TRUNC, None)
            myVofs.cache.open.assert_called_once_with(file, False, True,
                                                      myMockIOObject, False)
            myVofs.cache.open.reset_mock()

            # Test a file with a locked parent opened read/write.
            myVofs.cache.getAttr.return_value = None
            nodeLocked = Object()
            nodeLocked.props = {'islocked': True, 'parent': True}
            nodeLocked.type = "vos:ContainerNode"
            nodeUnlocked = Object()
            nodeUnlocked.props = {'islocked': False, 'child': True}
            nodeUnlocked.type = "vos:DataNode"
            myVofs.getNode = Mock(side_effect=SideEffect({
                (os.path.dirname(file3),): nodeLocked,
                (file3,): nodeUnlocked}, name="myVofs.getNode", default=None))
            myMockIOObject.setSize = Mock()
            with self.assertRaises(FuseOSError):
                fh = myVofs.open(file3, os.O_RDWR, None)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_create(self):
        file = "/dir1/dir2/file"
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        testfs.cache.getAttr = Mock()
        testfs.cache.getAttr.return_value = None
        testfs.cache.open = Mock()
        node = Mock()
        node.groupread = True
        node.groupwrite = True
        node.chmod.return_value = False

        parentNode = Mock()
        parentNode.groupread = True
        parentNode.groupwrite = True
        testfs.client.open = Mock()
        testfs.client.get_node = Mock(side_effect=SideEffect({
            (self.testMountPoint+'/dir1/dir2/file',): node,
            (self.testMountPoint+'/dir1/dir2',): parentNode},
            name="testfs.getNode"))
        testfs.client.create = Mock(return_value=node)
        with self.assertRaises(OSError):
            testfs.create(file, os.O_RDWR)

        testfs.getNode = Mock(side_effect=FuseOSError(errno=5))
        testfs.client.create = Mock(return_value=node)
        with self.assertRaises(FuseOSError):
            testfs.create(file, os.O_RDWR)

        node.props.get = Mock(return_value=False)
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        testfs.client.open = Mock()
        testfs.open = Mock()
        testfs.getNode = Mock(side_effect=SideEffect({
            ('/dir1/dir2/file',): node,
            ('/dir1/dir2',): parentNode}, name="testfs.getNode"))
        testfs.client.create = Mock(return_value=node)
        testfs.create(file, os.O_RDWR)
        testfs.open.assert_called_once_with(file, os.O_WRONLY)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_release(self):
        file = "/dir1/dir2/file"

        def mockRelease():
            if mockRelease.callCount == 0:
                mockRelease.callCount = 1
                raise CacheRetry("Exception")
            else:
                return

        mockRelease.callCount = 0

        # Raise a timeout exception
        basefh = Object
        basefh.release = Mock(side_effect=mockRelease)
        basefh.fileModified = True
        basefh.path = file
        fh = HandleWrapper(basefh, False)
        myVofs = vofs.VOFS("vos:", self.testCacheDir, opt)
        with self.assertRaises(CacheRetry):
            myVofs.release(file, fh.get_id())

        # Raise an IO error.
        basefh.release = Mock(side_effect=Exception("Exception"))
        basefh.fileModified = True
        basefh.path = file
        fh = HandleWrapper(basefh, False)
        with self.assertRaises(Exception):
            myVofs.release(file, fh.get_id())

        # Release an invalid file descriptor
        # TODO should this throw a FuseOSError?
        with self.assertRaises(KeyError):
            myVofs.release(file, -1)
            # self.assertEqual(e.exception.errno, EIO)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_getattr(self):
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)

        # Get the attributes from vospace.
        node = Object()
        testfs.cache.getAttr = Mock(return_value=None)
        node.attr = "attributes"
        testfs.getNode = Mock(return_value=node)
        self.assertEqual(testfs.getattr("vos:/a/file/path"), "attributes")
        testfs.getNode.assert_called_once_with(
            "vos:/a/file/path", limit=0, force=False)
        testfs.cache.getAttr.assert_called_once_with("vos:/a/file/path")

        # Get attributes from a file modified in the cache.
        testfs.cache.getAttr.reset_mock()
        testfs.getNode.reset_mock()
        self.assertFalse(testfs.getNode.called)
        testfs.cache.getAttr = Mock(return_value="different")
        self.assertEqual(testfs.getattr("vos:/a/file/path2"), "different")
        testfs.cache.getAttr.assert_called_once_with("vos:/a/file/path2")
        self.assertFalse(testfs.getNode.called)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_unlink(self):
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        path = "/a/file/path"

        # Unlink a file which is not in vospace.
        testfs.getNode = Mock(return_value=None)
        testfs.cache.unlinkFile = Mock()
        testfs.client.delete = Mock()
        mocks = (testfs.getNode, testfs.cache.unlinkFile, testfs.client.delete)
        testfs.unlink(path)
        testfs.getNode.assert_called_once_with(path, force=False, limit=1)
        testfs.cache.unlinkFile.assert_called_once_with(path)
        self.assertFalse(testfs.client.delete.called)

        for mock in mocks:
            mock.reset_mock()

        # Unlink a file which is in vospace.
        node = Object
        node.props = {'islocked': False}
        testfs.getNode.return_value = node
        testfs.unlink(path)
        testfs.getNode.assert_called_once_with(path, force=False, limit=1)
        testfs.cache.unlinkFile.assert_called_once_with(path)
        testfs.client.delete.assert_called_once_with(self.testMountPoint+path)

        for mock in mocks:
            mock.reset_mock()

        # Unlink a file which is locked
        node = Object
        node.props = {'islocked': True}
        testfs.getNode.return_value = node
        with self.assertRaises(FuseOSError) as e:
            testfs.unlink(path)
        self.assertEqual(e.exception.errno, EPERM)
        testfs.getNode.assert_called_once_with(path, force=False, limit=1)
        self.assertFalse(testfs.cache.unlinkFile.called)
        self.assertFalse(testfs.client.delete.called)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_mkdir(self):
        path = "/a/file/path"
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        testfs.client = Object()
        testfs.client.mkdir = Mock()
        testfs.client.update = Mock()
        testfs.mkdir(path, stat.S_IRUSR)
        testfs.client.mkdir.assert_called_once_with('{}{}'.format(
            self.testMountPoint, path))

        # Try to make a directory in a locked parent.
        testfs.client.mkdir.reset_mock()
        testfs.client.mkdir = Mock(side_effect=OSError(1, 'read-only mode'))
        with self.assertRaises(FuseOSError) as e:
            testfs.mkdir(path, stat.S_IRUSR)
        self.assertEqual(e.exception.errno, EPERM)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_rmdir(self):
        path = "/a/file/path"
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        testfs.client = Object()
        testfs.client.delete = Mock()
        node = Object()
        node.isdir = Mock(return_value=True)
        node.props = Object()
        node._nodeList = None
        node.props.get = Mock(side_effect=SideEffect({
            ('islocked', False): False,
        }, name="node.props.get"))
        node.type = "vos:ContainerNode"
        node.name = "testNode"
        testfs.client.get_node = Mock(return_value=node)
        testfs.rmdir(path)
        testfs.client.delete.assert_called_once_with(self.testMountPoint+path)

        # Try deleting a node which is locked.
        node.props.get = Mock(side_effect=SideEffect({
            ('islocked', False): True,
        }, name="node.props.get"))
        testfs.client.delete.reset_mock()

        with self.assertRaises(FuseOSError) as e:
            testfs.rmdir(path)
        self.assertEqual(e.exception.errno, EPERM)
        self.assertFalse(testfs.client.delete.called)
        testfs.client = Object()

    @unittest.skipIf(skipTests, "Individual tests")
    def test_readdir(self):
        path = "/a/file/path"
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        testfs.client = Object()
        node = Object()
        node.node_list = []
        node.name = "testNode"
        testfs.client.get_node = Mock(return_value=node)
        self.assertEqual(testfs.readdir(path, None), ['.', '..'])

        # Try again with a timeout
        # TODO how to cause a thread read timeout?
        # testfs.condition.wait = Mock(side_effect=CacheRetry("test"))
        # foo = testfs.readdir(path, None)
        # with self.assertRaises(FuseOSError) as e:
        #     testfs.readdir(path, None)
        # self.assertEqual(e.exception.errno, EAGAIN)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_loaddir(self):
        path = "/a/file/path"
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        testfs.client = Object()
        node = Object()
        node.node_list = Mock(return_value=())
        testfs.condition.notify_all = Mock(wraps=testfs.condition.notify_all)
        testfs.client.get_node = Mock(return_value=node)
        testfs.load_dir(path)
        testfs.condition.notify_all.assert_called_once_with()

    @unittest.skipIf(skipTests, "Individual tests")
    def test_chmod(self):
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        node = Object
        node.groupread = "group"
        node.groupwrite = "group"
        node.attr = {'st_ctime': 1}
        node.name = "testNode"
        testfs.getNode = Mock(return_value=node)
        node.chmod = Mock(return_value=True)
        testfs.client.update = Mock()
        mocks = (testfs.getNode, node.chmod, testfs.client.update)

        testfs.chmod("vos:/a/file/path", stat.S_IRUSR)
        testfs.client.update.assert_called_once_with(node)
        self.assertEqual(testfs.getNode.call_count, 4)

        # Try again with unknown groups.
        node.groupread = "NONE"
        node.groupwrite = "NONE"

        for mock in mocks:
            mock.reset_mock()

        testfs.chmod("vos:/a/file/path", stat.S_IRUSR)
        testfs.client.update.assert_called_once_with(node)
        self.assertEqual(testfs.getNode.call_count, 4)

        # And again with a failure from client update
        for mock in mocks:
            mock.reset_mock()

        testfs.client.update.side_effect = FuseOSError(errno=5)

        with self.assertRaises(FuseOSError) as e:
            testfs.chmod("/a/file/path", stat.S_IRUSR)
        self.assertEqual(e.exception.errno, EIO)
        testfs.client.update.assert_called_once_with(node)
        self.assertEqual(testfs.getNode.call_count, 3)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_access(self):
        file = "/a/file/path"

        # File exists.
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        node = Object
        testfs.getNode = Mock(return_value=node)

        self.assertEqual(testfs.access(file, stat.S_IRUSR), 0)

        # File doesn't exist.
        testfs.getNode = Mock(side_effect=NotImplementedError("an error"))
        self.assertEqual(testfs.access(file, stat.S_IRUSR), -1)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_fsync(self):

        file = "/dir1/dir2/file"
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)

        testfs.client = Object()
        node = Mock(spec=vos.Node)
        node.isdir = Mock(return_value=False)
        node.props = Object
        node.props.get = Mock(side_effect=SideEffect({
            ('islocked', False): False,
            ('length',): 10,
            ('MD5',): 12354,
        }, name="node.props.get"))
        node.type = "vos:DataNode"
        node.name = "testNode"
        testfs.client.get_node = Mock(return_value=node)
        with patch('vofs.CadcCache.FileHandle') as mockFileHandle:
            mockFileHandle.return_value = MyFileHandle(
                file, testfs.cache, None)
            fh = testfs.open(file, os.O_RDWR | os.O_CREAT, None)
            HandleWrapper.file_handle(fh).cache_file_handle.fsync = \
                Mock(wraps=HandleWrapper.file_handle(
                    fh).cache_file_handle.fsync)
            testfs.fsync(file, False, fh)
            HandleWrapper.file_handle(
                fh).cache_file_handle.fsync.assert_called_once_with()
            HandleWrapper.file_handle(
                fh).cache_file_handle.fsync.assert_called_once_with()

    @unittest.skipIf(skipTests, "Individual tests")
    def test_fsync2(self):
        file = "/dir1/dir2/file"
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)

        testfs.client = Object()
        node = Mock(spec=vos.Node)
        node.isdir = Mock(return_value=False)
        node.props = Object
        node.props.get = Mock(side_effect=SideEffect({
            ('islocked', False): False,
            ('length',): 10,
            ('MD5',): 12354,
        }, name="node.props.get"))
        node.type = "vos:DataNode"
        testfs.client.get_node = Mock(return_value=node)
        # Try flushing on a read-only file.
        with patch('vofs.CadcCache.FileHandle') as mockFileHandle:
            mockFileHandle.return_value = MyFileHandle(
                file, testfs.cache, None)
            fh = testfs.open(file, os.O_RDONLY, None)
            self.assertFalse(HandleWrapper.file_handle(fh).cache_file_handle.
                             fileModified)
            HandleWrapper.file_handle(fh).cache_file_handle.fsync = \
                Mock(wraps=HandleWrapper.file_handle(fh).cache_file_handle.
                     fsync)
            with self.assertRaises(FuseOSError) as e:
                testfs.fsync(file, False, fh)
            self.assertEqual(e.exception.errno, EPERM)
            self.assertEqual(HandleWrapper.file_handle(fh).cache_file_handle.
                             fsync.call_count, 0)
            self.assertFalse(HandleWrapper.file_handle(fh).cache_file_handle.
                             fileModified)

            testfs.release(file, fh)

        # Try with an invalid file descriptor
        with self.assertRaises(FuseOSError) as e:
            testfs.fsync(file, False, -1)
        self.assertEqual(e.exception.errno, EIO)

        # Try flushing on a read-only file system.
        with patch('vofs.CadcCache.FileHandle') as mockFileHandle:
            myopt = copy.copy(opt)
            testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, myopt)
            mockFileHandle.return_value = MyFileHandle(
                file, testfs.cache, None)
            myopt.readonly = True
            testfs.client = Object()
            testfs.client.get_node = Mock(return_value=node)
            fh = testfs.open(file, os.O_RDONLY, None)
            HandleWrapper.file_handle(fh).cache_file_handle.fsync = \
                Mock(wraps=HandleWrapper.file_handle(fh).cache_file_handle.
                     fsync)
            testfs.fsync(file, False, fh)
            self.assertEqual(
                HandleWrapper.file_handle(fh).cache_file_handle.fsync.
                call_count, 0)
            testfs.release(file, fh)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_rename(self):
        src = "/dir1/dir2/file"
        dest = "/dir3/dir4/file2"

        # Successful rename
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        testfs.client = Object()
        testfs.client.move = Mock(return_value=True)
        testfs.cache.renameFile = Mock(wraps=testfs.cache.renameFile)
        # self.assertEqual(testfs.rename(src, dest), 0)
        # testfs.client.move.assert_called_once_with(src, dest)
        # self.assertEqual(testfs.cache.renameFile.call_count, 1)

        # Rename failes on vopace
        testfs.client.move.reset_mock()
        testfs.cache.renameFile.reset_mock()
        testfs.client.move.return_value = False
        # self.assertEqual(testfs.rename(src, dest), -1)
        # testfs.client.move.assert_called_once_with(src, dest)
        # self.assertEqual(testfs.cache.renameFile.call_count, 0)

        # Rename throws an exception
        testfs.client.move.reset_mock()
        testfs.cache.renameFile.reset_mock()
        testfs.client.move.side_effect = Exception("str")
        testfs.get_node = Mock(side_effect=exceptions.NotFoundException())
        with self.assertRaises(Exception):
            self.assertEqual(testfs.rename(src, dest), -1)
        testfs.client.move.assert_called_once_with(self.testMountPoint+src,
                                                   self.testMountPoint+dest)
        self.assertEqual(testfs.cache.renameFile.call_count, 0)

        # Rename throws an exception because the node is locked.
        testfs.client.move.reset_mock()
        testfs.cache.renameFile.reset_mock()
        testfs.client.move.side_effect = Exception(
            "the node is NodeLocked so won't work")
        with self.assertRaises(OSError):
            self.assertEqual(testfs.rename(src, dest), -1)
        testfs.client.move.assert_called_once_with(self.testMountPoint+src,
                                                   self.testMountPoint+dest)
        self.assertEqual(testfs.cache.renameFile.call_count, 0)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_truncate(self):
        callCount = [0]

        def mock_read(block_size):
            callCount[0] += 1
            if callCount[0] == 1:
                return "1234"
            else:
                return None

        file = "/dir1/dir2/file"
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        node = Mock(spec=vos.Node)
        node.isdir = Mock(return_value=False)
        node.props = Object
        node.props.get = Mock(side_effect=SideEffect({
            ('islocked', False): False,
            ('length',): 10,
            ('MD5',): 12354,
        }, name="node.props.get"))
        node.type = "vos:DataNode"
        node.uri = "vos:/dir1/dir2/file"
        node.name = "testNode"
        testfs.client = Object()
        testfs.client.get_node = Mock(return_value=node)
        testfs.client.close = Mock()
        testfs.client.read = Mock(side_effect=mock_read)
        testfs.client.copy = Mock()
        vos_VOFILE = Object()
        vos_VOFILE.close = Mock()
        vos_VOFILE.read = Mock(side_effect=mock_read)
        testfs.client.open = Mock(return_value=vos_VOFILE)
        testfs.client.nodeCache = Object()
        testfs.client.nodeCache = NodeCache()

        # Truncate a non-open file to 0 bytes
        testfs.cache.open = Mock(wraps=testfs.cache.open)
        origRelease = FileHandle.release
        origTruncate = FileHandle.truncate
        with patch('vofs.CadcCache.FileHandle.release') as mockRelease, \
                patch('vofs.CadcCache.FileHandle') as mockFileHandle:
            mockFileHandle.return_value = MyFileHandle(
                file, testfs.cache, None)
            mockFileHandle.return_value.readData = \
                Mock(wraps=mockFileHandle.return_value.readData)
            mockRelease.wraps = origRelease  # TODO This doesn't really work,
            # release is not called and so open
            # files are being leaked
            testfs.truncate(file, 0)
            self.assertEqual(testfs.cache.open.call_count, 1)
            self.assertEqual(testfs.cache.open.call_args[0][0], file)
            self.assertFalse(testfs.cache.open.call_args[0][1])
            self.assertTrue(testfs.cache.open.call_args[0][2])
            mockRelease.assert_called_once_with()
            self.assertEqual(
                mockFileHandle.return_value.readData.call_count, 1)

        # Truncate a non-open file past the start of the file.
        testfs.cache.open.reset_mock()
        with patch('vofs.CadcCache.FileHandle.release') as mockRelease:
            mockRelease.wraps = origRelease  # TODO This doesn't really work,
            # release is not called and so open
            # files are being leaked
            with patch('vofs.CadcCache.FileHandle.truncate') as mockTruncate:
                mockTruncate.wraps = origTruncate  # TODO Same issue as the
                # mockRelease TODO above.
                testfs.truncate(file, 5)
                self.assertEqual(testfs.cache.open.call_args[0][0], file)
                self.assertFalse(testfs.cache.open.call_args[0][1])
                mockTruncate.assert_called_once_with(5)
            mockRelease.assert_called_once_with()

        # Truncate with an exception returned by the CadcCache truncate
        testfs.cache.open.reset_mock()
        with patch('vofs.CadcCache.FileHandle.release') as mockRelease, \
                patch('vofs.CadcCache.FileHandle.readData'):
            mockRelease.wraps = origRelease  # TODO This doesn't really work,
            # release is not called and so open
            # files are being leaked
            with patch('vofs.CadcCache.FileHandle.truncate') as mockTruncate:
                mockTruncate.side_effect = NotImplementedError("an error")
                with self.assertRaises(NotImplementedError):
                    testfs.truncate(file, 5)
                self.assertEqual(testfs.cache.open.call_args[0][0], file)
                self.assertFalse(testfs.cache.open.call_args[0][1])
            mockRelease.assert_called_once_with()

        # Truncate an already opened file given the file handle.
        with patch('vofs.CadcCache.FileHandle.release') as mockRelease, \
                patch('vofs.CadcCache.FileHandle.readData'):
            mockRelease.wraps = origRelease  # TODO This doesn't really work,
            # release is not called and so open
            # files are being leaked
            try:
                fh = testfs.open(file, os.O_RDWR | os.O_CREAT, None)
                testfs.cache.open.reset_mock()
                with patch(
                        'vofs.CadcCache.FileHandle.truncate') as mockTruncate:
                    mockTruncate.wraps = origTruncate  # TODO Same issue as the
                    # mockRelease TODO above.
                    testfs.truncate(file, 20, fh)
                    # Open and release should not be called, truncate should be
                    # called.
                    self.assertEqual(testfs.cache.open.call_count, 0)
                    mockTruncate.assert_called_once_with(20)
                self.assertEqual(mockRelease.call_count, 0)
            finally:
                testfs.release(file, fh)

        # Create a new file system for testing. This is required because of the
        # leaked file handles from the previous tests.

        testfs2 = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        testfs2.client = testfs.client
        testfs2.cache.open = Mock(wraps=testfs2.cache.open)

        # Truncate a read only file handle.
        with patch('vofs.CadcCache.FileHandle.release') as mockRelease, \
                patch('vofs.CadcCache.FileHandle') as mockFileHandle:
            mockRelease.wraps = origRelease
            mockFileHandle.return_value = MyFileHandle(file, testfs2.cache,
                                                       None)
            mockFileHandle.return_value.readData = \
                Mock(wraps=mockFileHandle.return_value.readData)
            try:
                fh = testfs2.open(file, os.O_RDONLY, None)
                testfs2.cache.open.reset_mock()
                with patch(
                        'vofs.CadcCache.FileHandle.truncate') as mockTruncate:
                    mockTruncate.wraps = origTruncate
                    with self.assertRaises(FuseOSError):
                        testfs2.truncate(file, 20, fh)
                    # Open, release and truncate should not be called.
                    self.assertEqual(testfs2.cache.open.call_count, 0)
                    self.assertEqual(mockTruncate.call_count, 0)
                self.assertEqual(mockRelease.call_count, 0)
            finally:
                testfs2.release(file, fh)

        # Truncate with an invalid file descriptor.
        with self.assertRaises(KeyError):
            testfs2.truncate(file, 20, -1)

    def test_getNode(self):
        file = "/dir1/dir2/file"
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        node = Mock(spec=vos.Node)
        node.name = "testNode"
        testfs.client = Object()
        testfs.client.get_node = Mock(return_value=node)
        node = testfs.getNode(file, force=True, limit=10)
        testfs.client.get_node.assert_called_once_with(
            self.testMountPoint+file, force=True, limit=10)

        err = OSError()
        err.errno = 1
        testfs.client.get_node = Mock(side_effect=err)
        with self.assertRaises(OSError):
            node = testfs.getNode(file, force=True, limit=10)

    def test_init(self):
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        testfs.init("/")
        self.assertTrue(isinstance(
            testfs.cache.flushNodeQueue, FlushNodeQueue))
        testfs.destroy("/")
        self.assertEqual(testfs.cache.flushNodeQueue, None)


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
            raise ValueError(
                "Mock side effect " + name + " arguments not in Controller: "
                + str(args) + ":" + str(keywords) + ": " +
                str(self.controller) + "***")


class TestMyIOProxy(unittest.TestCase):
    @unittest.skipIf(skipTests, "Individual tests")
    def testWriteToBacking(self):
        # Submit a write request for the whole file.
        with Cache(TestVOFS.testCacheDir, 100, timeout=1) as testCache:
            client = Object
            client.copy = Mock()
            client.copy = Mock(return_value=12345)
            vofsObj = Mock()
            vofsObj.client = client
            node = Object
            node.uri = "vos:/dir1/dir2/file"
            node.props = {"MD5": 12345}
            vofsObj.get_node = Mock(return_value=node)
            testProxy = vofs.MyIOProxy(vofsObj, None)
            path = "/dir1/dir2/file"
            with FileHandle(path, testCache, testProxy) as testFileHandle:
                testProxy.cacheFile = testFileHandle
                self.assertEqual(testProxy.writeToBacking(), 12345)
            client.copy.assert_called_once_with(
                testCache.dataDir + "/dir1/dir2/file", node.uri, send_md5=True)

    # @unittest.skipIf(skipTests, "Individual tests")
    def testReadFromBacking(self):
        callCount = [0]

        def mock_read(block_size):
            callCount[0] += 1
            if callCount[0] == 1:
                return "1234"
            else:
                return None

        with Cache(TestVOFS.testCacheDir, 100, timeout=1) as testCache:
            client = Object
            vos_VOFILE = Object()
            vos_VOFILE.URLs = ["url0", "URL1"]
            vos_VOFILE.urlIndex = 0
            vos_VOFILE.open = Mock()
            vos_VOFILE.read = Mock(side_effect=mock_read)
            vos_VOFILE.close = Mock()
            client.open = Mock(return_value=vos_VOFILE)
            path = "/dir1/dir2/file"
            myVofs = Mock()
            myVofs.cacheFile = Mock()
            myVofs.cacheFile.path = path
            myVofs.client = client
            client.getFileInfo = Mock(return_value=(123, "456", 45))
            testProxy = vofs.MyIOProxy(myVofs, None)

            with FileHandle(path, testCache, testProxy) as \
                    testFileHandle:
                testProxy.writeToCache = Mock(return_value=4)
                testProxy.cacheFile = testFileHandle
                testProxy.cacheFile.readThread = Mock()
                testProxy.cacheFile.readThread.aborted = False
                try:

                    # Submit a request for the whole file
                    testProxy.readFromBacking()
                    client.open.assert_called_once_with(
                        path, mode=os.O_RDONLY, view="data", size=None,
                        range=None)
                    self.assertEqual(vos_VOFILE.close.call_count, 1)
                    self.assertEqual(vos_VOFILE.read.call_count, 2)

                    # Submit a range request
                    vos_VOFILE.close.reset_mock()
                    vos_VOFILE.read.reset_mock()
                    callCount[0] = 0
                    testProxy.readFromBacking(100, 200)
                    self.assertEqual(client.open.call_count, 1)
                    vos_VOFILE.open.assert_called_once_with(
                        "url0", bytes="bytes=200-299")
                    self.assertEqual(vos_VOFILE.close.call_count, 1)
                    self.assertEqual(vos_VOFILE.read.call_count, 2)

                    # Submit a request which gets aborted.
                    vos_VOFILE.open.reset_mock()
                    vos_VOFILE.close.reset_mock()
                    vos_VOFILE.read.reset_mock()
                    callCount[0] = 0
                    testProxy.writeToCache.side_effect = CacheAborted(
                        "aborted")
                    testProxy.readFromBacking(150, 200)
                    self.assertEqual(client.open.call_count, 1)
                    vos_VOFILE.open.assert_called_once_with(
                        "url0", bytes="bytes=200-349")
                    self.assertEqual(vos_VOFILE.close.call_count, 1)
                    self.assertEqual(vos_VOFILE.read.call_count, 1)

                    # Submit a request with size = None and offset > 0
                    vos_VOFILE.open.reset_mock()
                    vos_VOFILE.close.reset_mock()
                    vos_VOFILE.read.reset_mock()
                    callCount[0] = 0
                    testProxy.readFromBacking(None, 1)
                    self.assertEqual(client.open.call_count, 1)
                    vos_VOFILE.open.assert_called_once_with("url0",
                                                            bytes="bytes=1-")
                    self.assertEqual(vos_VOFILE.close.call_count, 1)
                    self.assertEqual(vos_VOFILE.read.call_count, 1)

                    # Do a read which fails. This should result in a
                    # renegotiation.
                    vos_VOFILE.open.reset_mock()
                    vos_VOFILE.close.reset_mock()
                    vos_VOFILE.read.reset_mock()
                    callCount[0] = 0
                    self.assertTrue(testProxy.lastVOFile is not None)
                    testProxy.lastVOFile.read = Mock(side_effect=OSError)
                    # This throws an exception because read will be called
                    # twice, the first is caught and error handling occurs, the
                    # second is not caught.
                    with self.assertRaises(OSError):
                        testProxy.readFromBacking(None, 1)
                    self.assertEqual(client.open.call_count, 2)
                    vos_VOFILE.open.assert_called_with("url0",
                                                       bytes="bytes=1-")
                    self.assertEqual(vos_VOFILE.close.call_count, 1)
                    self.assertEqual(vos_VOFILE.read.call_count, 2)
                    self.assertTrue(type(testProxy.exception) is OSError)
                    testProxy.exception = None

                except Exception as e:
                    print("unexpected exception", e)

                finally:
                    testProxy.cacheFile.readThread = None

    @unittest.skipIf(skipTests, "Individual tests")
    def test_readFromBackingErrorHandling(self):
        client = Object
        vos_VOFILE = Object()
        vos_VOFILE.URLs = ["url0", "URL1"]
        vos_VOFILE.urlIndex = 0
        vos_VOFILE.open = Mock()
        mock_resp1 = Mock()
        mock_resp1.iter_content = Mock(return_value="bar")
        mock_resp2 = Mock()
        mock_resp2.iter_content = Mock(return_value="foo")
        returns = [mock_resp1, mock_resp2]

        def side_effect(*args, **kwds):
            return returns.pop(0)

        vos_VOFILE.read = MagicMock(side_effect=side_effect)
        vos_VOFILE.close = Mock()
        client.open = Mock(return_value=vos_VOFILE)
        myVofs = Mock()
        myVofs.client = client
        testProxy = vofs.MyIOProxy(myVofs, None)
        testProxy.writeToCache = Mock()
        path = "/dir1/dir2/file"
        cacheFile = Object()
        cacheFile.path = path
        cacheFile.gotHeader = True
        cacheFile.cache = Object()
        testProxy.setCacheFile(cacheFile)
        testProxy.readFromBacking()

    @unittest.skipIf(skipTests, "Individual tests")
    def test_getMD5(self):
        testProxy = vofs.MyIOProxy(None, "vos:/anode")
        node = vos.Node(
            "vos:/anode", properties={"MD5": "1234", "length": "1"})
        testProxy.vofs = Object()
        testProxy.vofs.get_node = Mock(side_effect=SideEffect({
            ('vos:/anode',): node, }, name="testfs.getNode"))
        self.assertEqual(testProxy.get_md5(), "1234")

        testProxy.md5 = "789"
        self.assertEqual(testProxy.get_md5(), "789")

    @unittest.skipIf(skipTests, "Individual tests")
    def test_getSize(self):
        testProxy = vofs.MyIOProxy(None, "vos:/anode")
        node = vos.Node(
            "vos:/anode", properties={"MD5": "1234", "length": "1"})
        testProxy.vofs = Object()
        testProxy.vofs.get_node = Mock(side_effect=SideEffect({
            ('vos:/anode',): node, }, name="testfs.getNode"))
        self.assertEqual(testProxy.getSize(), 1)

        testProxy.size = 27
        self.assertEqual(testProxy.getSize(), 27)

    @unittest.skipIf(skipTests, "Individual tests")
    @patch("fuse._libfuse")
    def test_MyFuse(self, mock_libfuse):
        # Tests the ctor to make sure the arguments are passed
        # correctly to the os
        mock_libfuse.fuse_main_real.return_value = False
        fuseops = fuse_operations()
        MyFuse(VOFS("vos:", "/tmp/vos_", None,
                    cache_limit=100,
                    cache_max_flush_threads=3),
               "/tmp/vospace",
               fsname="vos:",
               nothreads=5,
               foreground=False)
        # if it was easy to pick inside the args memory structures we would
        # have checked the the real arguments passed to the fuse library
        # instead of ANY
        mock_libfuse.fuse_main_real.assert_called_with(5, ANY, ANY,
                                                       ctypes.sizeof(fuseops),
                                                       None)

        MyFuse(VOFS("vos:", "/tmp/vos_", None,
                    cache_limit=100,
                    cache_max_flush_threads=3),
               "/tmp/vospace",
               fsname="vos:",
               nothreads=5,
               readonly=True,
               user_allow_other=True,
               foreground=False)

        mock_libfuse.fuse_main_real.assert_called_with(6, ANY, ANY,
                                                       ctypes.sizeof(fuseops),
                                                       None)

    @unittest.skipIf(skipTests, "Individual tests")
    @patch("vofs.vofs.FUSE.__init__")
    def test_readMyFuse(self, mock_fuse):
        mock_fuse.return_value = None
        buf = ctypes.create_string_buffer(4)
        fuse = MyFuse(VOFS("vos:/anode", "/tmp/vos_", None,
                           cache_limit=100,
                           cache_max_flush_threads=3),
                      "/tmp/vospace",
                      fsname="vos:",
                      nothreads=5,
                      foreground=False)
        fuse.raw_fi = True
        fuse.encoding = 'ascii'
        fuse.operations = Mock()
        fuse.operations.return_value = 3
        fip = Mock()
        fip.contents = 'somevalue'
        retsize = fuse.read("/some/path".encode('utf-8'), buf, 10, 1, fip)
        fuse.operations.assert_called_once_with(
            'read', '/some/path', 10, 1, 'somevalue', buf)
        self.assertEqual(3, retsize, "Wrong buffer size")

    @unittest.skipIf(skipTests, "Individual tests")
    @patch("vofs.vofs.FUSE.__init__")
    def test_writeMyFuse(self, mock_fuse):
        mock_fuse.return_value = None
        buf = ctypes.create_string_buffer(4)
        fuse = MyFuse(VOFS("vos:/anode", "/tmp/vos_", None,
                           cache_limit=100,
                           cache_max_flush_threads=3),
                      "/tmp/vospace",
                      fsname="vos:",
                      nothreads=5,
                      foreground=False)
        fuse.raw_fi = True
        fuse.encoding = 'ascii'
        fuse.operations = Mock()
        fuse.operations.return_value = 3
        fip = Mock()
        fh_mock = Mock()
        mock_contents = Mock(return_value='somevale', fh=fh_mock)
        fip.contents = mock_contents
        retsize = fuse.write("/some/path".encode('utf-8'), buf, 10, 1, fip)
        fuse.operations.assert_called_once_with(
            'write', '/some/path', buf, 10, 1, fh_mock)
        self.assertEqual(3, retsize, "Wrong buffer size")


class TestHandleWrapper(unittest.TestCase):
    @unittest.skipIf(skipTests, "Individual tests")
    def testAll(self):
        # Get the hand wrapper's id
        vofs.HandleWrapper.handle_list = {}
        self.assertEqual(len(vofs.HandleWrapper.handle_list), 0)
        handle = vofs.HandleWrapper(Object, False)
        self.assertEqual(len(vofs.HandleWrapper.handle_list), 1)
        handle2 = vofs.HandleWrapper(Object, False)
        self.assertEqual(len(vofs.HandleWrapper.handle_list), 2)
        self.assertEqual(handle.get_id(), id(handle))
        self.assertTrue(
            handle is vofs.HandleWrapper.file_handle(handle.get_id()))
        handle.release()
        self.assertEqual(len(vofs.HandleWrapper.handle_list), 1)
        handle2.release()
        self.assertEqual(len(vofs.HandleWrapper.handle_list), 0)
        with self.assertRaises(KeyError):
            self.assertTrue(handle is vofs.HandleWrapper.
                            file_handle(handle.get_id()))


def run():
    suite1 = unittest.TestLoader().loadTestsFromTestCase(TestVOFS)
    suite2 = unittest.TestLoader().loadTestsFromTestCase(TestMyIOProxy)
    suite3 = unittest.TestLoader().loadTestsFromTestCase(TestHandleWrapper)
    unittest.TestSuite([suite1, suite2, suite3])
    return unittest.TextTestRunner(verbosity=2).run(suite1)


if __name__ == "__main__":
    run()
