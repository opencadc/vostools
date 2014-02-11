import os
import shutil
import unittest
import stat
import copy
import pdb
from contextlib import nested
from vos import vofs, vos
from mock import Mock, MagicMock, patch
from vos.fuse import FuseOSError
from vos.CadcCache import Cache, CacheRetry, CacheAborted, FileHandle
from errno import EIO, EAGAIN, EPERM, ENOENT

skipTests = False

class Object(object):
    pass

class TestVOFS(unittest.TestCase):
    testMountPoint = "/tmp/testfs"
    testCacheDir = "/tmp/testCache"
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
    def testWrite1(self):
        """ Write to a read-only or locked file"""
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        fileHandle = vofs.HandleWrapper(None, True)
        # Write some data at the start of the file. File is read only so it
        # returns 0

        with self.assertRaises(FuseOSError) as e:
            testfs.write( "/dir1/dir2/file", "abcd", 4, 0, fileHandle)
        self.assertEqual(e.exception.errno, EPERM)

    @unittest.skipIf(skipTests, "Individual tests")
    def testWrite2(self):
        """ Write to a read-only file system"""
        opt.readonly = True
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        fileHandle = vofs.HandleWrapper(None, False)
        # Write some data at the start of the file.
        self.assertEqual(testfs.write( "/dir1/dir2/file", "abcd", 4, 0,
                fileHandle), 0)

    @unittest.skipIf(skipTests, "Individual tests")
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

    @unittest.skipIf(skipTests, "Individual tests")
    def testWrite4(self):
        """Test a timout during write"""

        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        fileHandle = vofs.HandleWrapper(Object(), False)
        fileHandle.cacheFileHandle.write = Mock()
        fileHandle.cacheFileHandle.write.side_effect = CacheRetry("fake")
        with self.assertRaises(FuseOSError) as e:
            testfs.write( "/dir1/dir2/file", "abcd", 4, 2048, fileHandle)
        self.assertEqual(e.exception.errno, EAGAIN)

    @unittest.skipIf(skipTests, "Individual tests")
    def testRead1(self):
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)

        # Read with a null file handle.
        with self.assertRaises(FuseOSError) as e:
            testfs.read( "/dir1/dir2/file", 4, 2048)
        self.assertEqual(e.exception.errno, EIO)

        # Read with a timeout.
        fileHandle = vofs.HandleWrapper(Object())
        fileHandle.cacheFileHandle.read = Mock()
        fileHandle.cacheFileHandle.read.side_effect = CacheRetry("fake")
        with self.assertRaises(FuseOSError) as e:
            testfs.read( "/dir1/dir2/file", 4, 2048, fileHandle)
        self.assertEqual(e.exception.errno, EAGAIN)

        # Read with success.
        fileHandle = vofs.HandleWrapper(Object())
        fileHandle.cacheFileHandle.read = Mock()
        fileHandle.cacheFileHandle.read.return_value = "abcd"
        self.assertEqual( testfs.read( "/dir1/dir2/file", 4, 2048, fileHandle),
                "abcd")
    
    @unittest.skipIf(skipTests, "Individual tests")
    def test_open(self):
        myVofs = vofs.VOFS("vos:", self.testCacheDir, opt)
        file = "/dir1/dir2/file"
        myVofs.cache.getAttr = Mock()
        myVofs.cache.getAttr.return_value = None
        
        # getNode return not found
        myVofs.getNode = Mock()
        myVofs.getNode.side_effect = FuseOSError(404)
        fh = myVofs.open( file, os.O_RDWR | os.O_CREAT, None)
        self.assertEqual(self.testCacheDir + "/data" + file, fh.cacheFileHandle.cacheDataFile)
        self.assertEqual(self.testCacheDir + "/metaData" + file, fh.cacheFileHandle.cacheMetaDataFile)
        self.assertFalse(fh.readOnly)

        # non existing file open in read/write mode should fail
        with self.assertRaises(FuseOSError) as e:
            myVofs.open(file, os.O_RDWR, None)
        self.assertEqual(e.exception.errno, ENOENT)
            
        # test with mocks so we can assert other parts of the function
        with patch('vos.vofs.MyIOProxy') as mock1:
            with patch('vos.CadcCache.Cache') as mock2:
                myVofs = vofs.VOFS("vos:", self.testCacheDir, opt)
                myVofs.getNode = Mock()
                myVofs.getNode.side_effect = FuseOSError(404)
                #myVofs.cache = mock2
                fh = myVofs.open( file, os.O_RDWR | os.O_CREAT, None)
                self.assertFalse(fh.readOnly)
                mock1.assert_called_with(myVofs, None)
                #mock2.open.assert_called_with(file, True, mock1)
                
        # test with local attributes
        myVofs.cache.getAttr = Mock()
        myVofs.cache.getAttr.return_value = Mock()
        fh = myVofs.open( file, os.O_RDWR | os.O_CREAT, None)
        self.assertEqual(None, fh.cacheFileHandle.ioObject.getMD5())
        self.assertEqual(None, fh.cacheFileHandle.ioObject.getSize())
        self.assertFalse(fh.readOnly)

        # test a read-only file
        myVofs.cache.getAttr = Mock()
        myVofs.cache.getAttr.return_value = Mock()
        fh = myVofs.open( file, os.O_RDONLY, None)
        self.assertTrue(fh.readOnly)
                
                
    @unittest.skipIf(skipTests, "Individual tests")
    def test_release(self):
        file = "/dir1/dir2/file"
        fh = MagicMock(name="filehandler")
        fh.cacheFileHandle.release.side_effect=CacheRetry(Exception())
        myVofs = vofs.VOFS("vos:", self.testCacheDir, opt)
        with self.assertRaises(FuseOSError) as e:
            myVofs.release(fh)
        self.assertEqual(e.exception.errno, EAGAIN)
            
        # when file modified locally, release should remove it from the list of
        # accessed files in VOFS
        fh = MagicMock(name="filehandler")
        fh.cacheFileHandle.path = file
        myVofs.node[file] = Mock(name="fileInfo")
        self.assertEqual(1, len(myVofs.node))
        fh.fileModified = True
        fh.release.return_value = True
        myVofs.release(fh)
        self.assertEqual(0, len(myVofs.node))


    @unittest.skipIf(skipTests, "Individual tests")
    def test_getattr(self):
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)

        # Get the attributes from vospace.
        node = Object()
        testfs.cache.getAttr = Mock(return_value=None)
        node.attr="attributes"
        testfs.getNode = Mock(return_value=node)
        self.assertEqual(testfs.getattr("/a/file/path"), "attributes")
        testfs.getNode.assert_called_once_with("/a/file/path", limit=0,
                force=True)
        testfs.cache.getAttr.assert_called_once_with("/a/file/path")

        # Get attributes from a file modified in the cache.
        testfs.cache.getAttr.reset_mock()
        testfs.getNode.reset_mock()
        self.assertFalse(testfs.getNode.called)
        testfs.cache.getAttr = Mock(return_value="different")
        self.assertEqual(testfs.getattr("/a/file/path2"), "different")
        testfs.cache.getAttr.assert_called_once_with("/a/file/path2")
        self.assertFalse(testfs.getNode.called)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_unlink(self):
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        path = "/a/file/path"

        # Unlink a file which is not in vospace.
        testfs.getNode = Mock(return_value = None)
        testfs.cache.unlink = Mock()
        testfs.client.delete = Mock()
        testfs.delNode = Mock()
        mocks = (testfs.getNode, testfs.cache.unlink, testfs.client.delete,
                testfs.delNode)
        testfs.unlink(path)
        testfs.getNode.assert_called_once_with(path, force=False, limit=1)
        testfs.cache.unlink.assert_called_once_with(path)
        self.assertFalse(testfs.client.delete.called)
        testfs.delNode.assert_called_once_with(path, force=True)

        for mock in mocks:
            mock.reset_mock()

        # Unlink a file which is in vospace.
        node = Object
        node.props = {'islocked': False}
        testfs.getNode.return_value = node
        testfs.unlink(path)
        testfs.getNode.assert_called_once_with(path, force=False, limit=1)
        testfs.cache.unlink.assert_called_once_with(path)
        testfs.client.delete.assert_called_once_with(path)
        testfs.delNode.assert_called_once_with(path, force=True)

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
        self.assertFalse(testfs.cache.unlink.called)
        self.assertFalse(testfs.client.delete.called)
        self.assertFalse(testfs.delNode.called)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_delNode(self):
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        path = "/a/file/path"
        testfs.delNode(path, force=True)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_mkdir(self):
        path = "/a/file/path"
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        testfs.client = Object()
        testfs.client.mkdir = Mock()
        testfs.client.update = Mock()
        testfs.chmod = Mock(wraps=testfs.chmod)
        node=Object
        node.groupread = "NONE"
        node.groupwrite = "NONE"
        node.attr = {'st_ctime': 1}
        parentNode=Object
        parentNode.props = Object
        parentNode.props.get = Mock(side_effect=SideEffect({
                ('islocked', False): False,
                }, name="node.props.get") )
        testfs.getNode = Mock(return_value=parentNode)
        testfs.getNode = Mock(side_effect=SideEffect({
                (os.path.dirname(path),): parentNode,
                (path,): node,
                }, name="testfs.getNode"))
        testfs.mkdir(path, stat.S_IRUSR)
        testfs.chmod.assert_called_once_with(path, stat.S_IRUSR)
        testfs.client.mkdir.assert_called_once_with(path)

        # Try to make a directory in a locked parent.
        testfs.chmod.reset_mock()
        testfs.client.mkdir.reset_mock()
        parentNode.props.get = Mock(side_effect=SideEffect({
                ('islocked', False): True,
                }, name="node.props.get") )
        with self.assertRaises(FuseOSError) as e:
            testfs.mkdir(path, stat.S_IRUSR)
        self.assertEqual(e.exception.errno, EPERM)
        self.assertFalse(testfs.chmod.called)
        self.assertFalse(testfs.client.mkdir.called)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_rmdir(self):
        path="/a/file/path"
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        testfs.delNode = Mock(wraps=testfs.delNode)
        testfs.client = Object()
        testfs.client.delete = Mock()
        node = Object()
        node.isdir = Mock(return_value = True)
        node.props = Object()
        node._nodeList = None
        node.props.get = Mock(side_effect=SideEffect({
                ('islocked', False): False,
                }, name="node.props.get") )
        node.type = "vos:ContainerNode"
        testfs.client.getNode = Mock(return_value = node)
        testfs.rmdir(path)
        testfs.client.delete.assert_called_once_with(path)
        testfs.delNode.assert_called_once_with(path,force=True)
        
        # Try deleting a node which is locked.
        node.props.get = Mock(side_effect=SideEffect({
                ('islocked', False): True,
                }, name="node.props.get") )
        testfs.client.delete.reset_mock()
        testfs.delNode.reset_mock()

        with self.assertRaises(FuseOSError) as e:
            testfs.rmdir(path)
        self.assertEqual(e.exception.errno, EPERM)
        self.assertFalse(testfs.client.delete.called)
        self.assertFalse(testfs.delNode.called)
        testfs.client = Object()


    @unittest.skipIf(skipTests, "Individual tests")
    def test_readdir(self):
        path="/a/file/path"
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        testfs.client = Object()
        node = Object()
        node.getNodeList = Mock(return_value = ())
        testfs.client.getNode = Mock(return_value = node)
        self.assertEqual(testfs.readdir(path, None), ['.', '..'])

        # Try again with a timeout
        testfs.condition.wait = Mock(side_effect=CacheRetry("test"))
        with self.assertRaises(FuseOSError) as e:
            self.assertEqual(testfs.readdir(path, None), ['.', '..'])
        self.assertEqual(e.exception.errno, EAGAIN)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_loaddir(self):
        path="/a/file/path"
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        testfs.client = Object()
        node = Object()
        node.getNodeList = Mock(return_value = ())
        testfs.condition.notify_all = Mock(wraps=testfs.condition.notify_all)
        testfs.client.getNode = Mock(return_value = node)
        testfs.load_dir(path)
        testfs.condition.notify_all.assert_called_once_with()

    @unittest.skipIf(skipTests, "Individual tests")
    def test_chmod(self):
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        node=Object
        node.groupread = "group"
        node.groupwrite = "group"
        node.attr = {'st_ctime': 1}
        testfs.getNode = Mock(return_value = node)
        node.chmod = Mock(return_value = True)
        testfs.client.update = Mock()
        mocks = (testfs.getNode, node.chmod, testfs.client.update)

        testfs.chmod("/a/file/path", stat.S_IRUSR)
        testfs.client.update.assert_called_once_with(node)
        self.assertEqual(testfs.getNode.call_count, 4)

        # Try again with unknown groups.
        node.groupread = "NONE"
        node.groupwrite = "NONE"

        for mock in mocks:
            mock.reset_mock()

        testfs.chmod("/a/file/path", stat.S_IRUSR)
        testfs.client.update.assert_called_once_with(node)
        self.assertEqual(testfs.getNode.call_count, 4)

        # And again with a failure from client update
        for mock in mocks:
            mock.reset_mock()

        testfs.client.update.side_effect=NotImplementedError("an error")

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
        node.isdir = Mock(return_value=False)
        testfs.node[file] = node

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
        node.isdir = Mock(return_value = False)
        node.props = Object
        node.props.get = Mock(side_effect=SideEffect({
                ('islocked', False): False,
                ('length',): 10,
                ('MD5',): 12354,
                }, name="node.props.get") )
        node.type = "vos:DataNode"
        testfs.client.getNode = Mock(return_value = node)
        fh = testfs.open( file, os.O_RDWR | os.O_CREAT, None)
        fh.cacheFileHandle.fsync = Mock(wraps=fh.cacheFileHandle.fsync)
        testfs.fsync(file, False, fh)
        fh.cacheFileHandle.fsync.assert_called_once_with()
        testfs.release(fh)
        
        # Try flushing on a read-only file.
        testfs.client.getNode = Mock(return_value = node)
        fh = testfs.open( file, os.O_RDONLY, None)
        fh.readOnly = True
        fh.cacheFileHandle.fsync = Mock(wraps=fh.cacheFileHandle.fsync)
        with self.assertRaises(FuseOSError) as e:
            testfs.fsync(file, False, fh)
        self.assertEqual(e.exception.errno, EPERM)
        self.assertEqual(fh.cacheFileHandle.fsync.call_count, 0)
        testfs.release(fh)

        # Try flushing on a read-only file system.
        myopt = copy.copy(opt)
        myopt.readonly = True
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, myopt)
        testfs.client = Object()
        testfs.client.getNode = Mock(return_value = node)
        fh = testfs.open( file, os.O_RDONLY, None)
        fh.cacheFileHandle.fsync = Mock(wraps=fh.cacheFileHandle.fsync)
        testfs.fsync(file, False, fh)
        self.assertEqual(fh.cacheFileHandle.fsync.call_count, 0)
        testfs.release(fh)

    @unittest.skipIf(skipTests, "Individual tests")
    def test_rename(self):
        src = "/dir1/dir2/file"
        dest = "/dir3/dir4/file2"

        # Successful rename
        testfs = vofs.VOFS(self.testMountPoint, self.testCacheDir, opt)
        testfs.client = Object()
        testfs.client.move = Mock(return_value=True)
        testfs.cache.renameFile = Mock(wraps=testfs.cache.renameFile)
        self.assertEqual(testfs.rename(src, dest),0)
        testfs.client.move.assert_called_once_with(src,dest)
        self.assertEqual(testfs.cache.renameFile.call_count, 1)

        # Rename failes on vopace
        testfs.client.move.reset_mock()
        testfs.cache.renameFile.reset_mock()
        testfs.client.move.return_value=False
        self.assertEqual(testfs.rename(src, dest),-1)
        testfs.client.move.assert_called_once_with(src,dest)
        self.assertEqual(testfs.cache.renameFile.call_count, 0)

        # Rename throws an exception
        testfs.client.move.reset_mock()
        testfs.cache.renameFile.reset_mock()
        testfs.client.move.side_effect=Exception("str")
        self.assertEqual(testfs.rename(src, dest), -1)
        testfs.client.move.assert_called_once_with(src,dest)
        self.assertEqual(testfs.cache.renameFile.call_count, 0)

        # Rename throws an exception because the node is locked.
        testfs.client.move.reset_mock()
        testfs.cache.renameFile.reset_mock()
        testfs.client.move.side_effect=Exception(
                "the node is NodeLocked so won't work")
        with self.assertRaises(FuseOSError) as e:
            self.assertEqual(testfs.rename(src, dest), -1)
        self.assertEqual(e.exception.errno, EPERM)
        testfs.client.move.assert_called_once_with(src,dest)
        self.assertEqual(testfs.cache.renameFile.call_count, 0)

    #@unittest.skipIf(skipTests, "Individual tests")
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
        node.isdir = Mock(return_value = False)
        node.props = Object
        node.props.get = Mock(side_effect=SideEffect({
                ('islocked', False): False,
                ('length',): 10,
                ('MD5',): 12354,
                }, name="node.props.get") )
        node.type = "vos:DataNode"
        node.uri = "vos:/dir1/dir2/file"
        testfs.client = Object()
        testfs.client.getNode = Mock(return_value = node)
        testfs.client.close = Mock()
        testfs.client.read = Mock(side_effect = mock_read)
        testfs.client.copy = Mock()
        vos_VOFILE = Object()
        vos_VOFILE.close = Mock()
        vos_VOFILE.read = Mock(side_effect=mock_read)
        testfs.client.open = Mock(return_value = vos_VOFILE)

        # Truncate a non-open file to 0 bytes
        testfs.cache.open = Mock(wraps=testfs.cache.open)
        ###testfs.truncate(file, 0)
        ###self.assertEqual(mockCacheOpen.call_args[0][0], file)
        ###self.assertEqual(mockCacheOpen.call_args[0][1], True)
        # TODO this should cause a bunch of errors as it tries to flush to
        ### VOSpace, but the error in open prevents the errors.
        ###mockFileRelease.assert_called_once_with()

        # Truncate a non-open file past the start of the file.
        testfs.cache.open.reset_mock()
        with patch('vos.CadcCache.FileHandle.release') as mockRelease:
            mockRelease.wraps = FileHandle.release
            with patch('vos.CadcCache.FileHandle.truncate') as mockTruncate:
                mockTruncate.wraps = FileHandle.truncate
                testfs.truncate(file, 5)
                self.assertEqual(testfs.cache.open.call_args[0][0], file)
                self.assertEqual(testfs.cache.open.call_args[0][1], False)
                mockTruncate.assert_called_once_with(5)
            mockRelease.assert_called_once_with()

        # Truncate with an exception returned by the CadcCache truncate
        testfs.cache.open.reset_mock()
        with patch('vos.CadcCache.FileHandle.release') as mockRelease:
            mockRelease.wraps = FileHandle.release
            with patch('vos.CadcCache.FileHandle.truncate') as mockTruncate:
                mockTruncate.side_effect = NotImplementedError("an error")
                with self.assertRaises(NotImplementedError):
                    testfs.truncate(file, 5)
                self.assertEqual(testfs.cache.open.call_args[0][0], file)
                self.assertEqual(testfs.cache.open.call_args[0][1], False)
            mockRelease.assert_called_once_with()

        # Truncate an already opened file given the file handle.
        with patch('vos.CadcCache.FileHandle.release') as mockRelease:
            mockRelease.wraps = FileHandle.release
            try:
                fh = testfs.open( file, os.O_RDWR | os.O_CREAT, None)
                testfs.cache.open.reset_mock()
                with patch('vos.CadcCache.FileHandle.truncate') as mockTruncate:
                    mockTruncate.wraps = FileHandle.truncate
                    testfs.truncate(file, 20, fh)
                    # Open and release should not be called, truncate should be
                    # called.
                    self.assertEqual(testfs.cache.open.call_count, 0)
                    mockTruncate.assert_called_once_with(20)
                self.assertEqual(mockRelease.call_count, 0)
            finally:
                testfs.release(fh)

        # Truncate aread only file handle.
        with patch('vos.CadcCache.FileHandle.release') as mockRelease:
            mockRelease.wraps = FileHandle.release
            try:
                fh = testfs.open( file, os.O_RDONLY, None)
                with patch('vos.CadcCache.FileHandle.truncate') as mockTruncate:
                    mockTruncate.wraps = FileHandle.truncate
                    with self.assertRaises(FuseOSError):
                        testfs.truncate(file, 20, fh)
                    # Open, release and truncate should not be called.
                    self.assertEqual(testfs.cache.open.call_count, 0)
                    self.assertEqual(mockTruncate.call_count, 0)
                self.assertEqual(mockRelease.call_count, 0)
            finally:
                testfs.release(fh)


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




class TestMyIOProxy(unittest.TestCase):
    @unittest.skipIf(skipTests, "Individual tests")
    def testWriteToBacking(self):
        # Submit a write request for the whole file.
        with Cache(TestVOFS.testCacheDir, 100, timeout=1) as testCache:
            client = Object
            client.copy = Mock()
            vofsObj = Mock()
            vofsObj.client = client
            node = Object
            node.uri = "vos:/dir1/dir2/file"
            node.props={"MD5": 12345}
            vofsObj.getNode = Mock(return_value=node)
            testProxy = vofs.MyIOProxy(vofsObj, None)
            path = "/dir1/dir2/file"
            with FileHandle(path, testCache, testProxy) as \
                    testFileHandle:
                testProxy.cacheFile = testFileHandle
                self.assertEqual(testProxy.writeToBacking(), 12345)
            client.copy.assert_called_once_with( testCache.dataDir + 
                    "/dir1/dir2/file", node.uri)

    @unittest.skipIf(skipTests, "Individual tests")
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
            client.open = Mock(return_value = vos_VOFILE)
            myVofs = Mock()
            myVofs.client = client
            testProxy = vofs.MyIOProxy(myVofs, None)
            path = "/dir1/dir2/file"
            with FileHandle(path, testCache, testProxy) as \
                    testFileHandle:
                testProxy.writeToCache = Mock(return_value = 4)
                testProxy.cacheFile = testFileHandle
                testProxy.cacheFile.readThread = Object()
                testProxy.cacheFile.readThread.aborted = False
                try:

                    # Submit a request for the whole file
                    testProxy.readFromBacking()
                    client.open.assert_called_once_with(path, mode=os.O_RDONLY, 
                            view="data", size=None, range=None)
                    self.assertEqual(vos_VOFILE.close.call_count, 1)
                    self.assertEqual(vos_VOFILE.read.call_count, 2)

                    # Submit a range request
                    vos_VOFILE.close.reset_mock()
                    vos_VOFILE.read.reset_mock()
                    callCount[0] = 0
                    testProxy.readFromBacking(100,200)
                    self.assertEqual(client.open.call_count, 1)
                    vos_VOFILE.open.assert_called_once_with("url0", 
                            bytes=(100,200))
                    self.assertEqual(vos_VOFILE.close.call_count, 1)
                    self.assertEqual(vos_VOFILE.read.call_count, 2)

                    # Submit a request which gets aborted.
                    vos_VOFILE.open.reset_mock()
                    vos_VOFILE.close.reset_mock()
                    vos_VOFILE.read.reset_mock()
                    callCount[0] = 0
                    testProxy.writeToCache.side_effect = CacheAborted("aborted")
                    testProxy.readFromBacking(100,200)
                    self.assertEqual(client.open.call_count, 1)
                    vos_VOFILE.open.assert_called_once_with("url0", 
                            bytes=(100,200))
                    self.assertEqual(vos_VOFILE.close.call_count, 1)
                    self.assertEqual(vos_VOFILE.read.call_count, 1)

                finally:
                    testProxy.cacheFile.readThread = None


suite1 = unittest.TestLoader().loadTestsFromTestCase(TestVOFS)
suite2 = unittest.TestLoader().loadTestsFromTestCase(TestMyIOProxy)
alltests = unittest.TestSuite([suite1, suite2])
unittest.TextTestRunner(verbosity=2).run(alltests)


