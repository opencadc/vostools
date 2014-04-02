"""
A sparse file caching library
"""

import os
import sys
import io
import stat
import time
import threading
from Queue import Queue
import traceback
import hashlib
from os import O_RDONLY, O_WRONLY, O_RDWR, O_APPEND
import errno
from contextlib import nested
from errno import EACCES, EIO, ENOENT, EISDIR, ENOTDIR, ENOTEMPTY, EPERM, \
        EEXIST, ENODATA, ECONNREFUSED, EAGAIN, ENOTCONN
import ctypes

import vos
from SharedLock import SharedLock as SharedLock
from CacheMetaData import CacheMetaData as CacheMetaData
from logExceptions import logExceptions
import pdb
libcPath = ctypes.util.find_library('c')
libc = ctypes.cdll.LoadLibrary(libcPath)

_flush_thread_count = 0

# TODO optionally disable random reads - always read to the end of the file.

class CacheCondition(object):
    """This extends threading.condtion (or it would if it were a class):
       There is an optional timeout associated with the condition.
       The timout starts runing when the condition lock is acquired.
       The timeout throws the CacheTimeout exception.
    """
    def __init__(self, lock, timeout=None):
        self.timeout = timeout
        self.myCondition = threading.Condition(lock)
        self.threadSpecificData = threading.local()
        self.threadSpecificData.endTime = None

    def __enter__(self):
        """To support the with construct.
        """

        self.acquire()
        return self

    def __exit__(self, a1, a2, a3):
        """To support the with construct.
        """

        self.release()
        return

    def setTimeout(self):
        self.threadSpecificData.endTime = time.time() + self.timeout

    def clearTimeout(self):
        self.threadSpecificData.endTime = None

    def acquire(self, blocking=True):
        return self.myCondition.acquire(blocking)

    def release(self):
        self.myCondition.release()

    def wait(self):
        """Wait for the condition:"""

        if (not hasattr(self.threadSpecificData, 'endTime') or
                self.threadSpecificData.endTime is None):
            self.myCondition.wait()
        else:
            timeLeft = self.threadSpecificData.endTime - time.time()
            if timeLeft < 0:
                self.threadSpecificData.endTime = None
                raise CacheRetry("Condition varible timeout")
            else:
                self.myCondition.wait(timeLeft)

    def notify_all(self):
        self.myCondition.notify_all()


class Cache(object):
    """
    This class manages the cache for the vofs.
    """
    IO_BLOCK_SIZE = 2 ** 14

    def __init__(self, cacheDir, maxCacheSize, readOnly=False, timeout=60, maxFlushThreads=10):
        """Initialize the Cache Object

        Parameters:
        -----------
        cacheDir : string - The directory for the cache.
        maxCacheSize : int - The maximum cache size in megabytes
        readOnly : boolean - Is the cached data read-only
        maxFlushThreads : int - Maximum number of nodes to flush simultaneously
        """

        self.cacheDir = os.path.abspath(cacheDir)
        self.dataDir = os.path.join(self.cacheDir, "data")
        self.metaDataDir = os.path.join(self.cacheDir, "metaData")
        self.timeout = timeout
        self.maxCacheSize = maxCacheSize
        self.readOnly = readOnly
        self.fileHandleDict = {}
        # When cache locks and file locks need to be held at the same time,
        # always acquire the cache lock first.
        self.cacheLock = threading.RLock()

        # A thread queue will be shared by FileHandles to flush nodes.
        # Figure out how many threads will be available now, but defer
        # initializing the queue / worker threads until the filesystem
        # is initialized (see vofs.init)
        self.maxFlushThreads = maxFlushThreads
        self.flushNodeQueue = None

        if os.path.exists(self.cacheDir):
            if not os.path.isdir(self.cacheDir):
                raise CacheError("Path " + self.cacheDir + \
                        " is not a directory.")
            if not os.access(self.cacheDir, os.R_OK | os.W_OK | os.X_OK):
                raise CacheError("Existing path " + self.cacheDir + \
                        " does not have on of read, write or execute" +
                        " permission.")

        if os.path.exists(self.dataDir):
            if not os.path.isdir(self.dataDir):
                raise CacheError("Path " + self.dataDir + \
                        " is not a directory.")
            if not os.access(self.dataDir, os.R_OK | os.W_OK | os.X_OK):
                raise CacheError("Existing path " + self.dataDir + \
                        " does not have on of read, write or execute" +
                        " permission.")
        else:
            os.makedirs(self.dataDir, stat.S_IRWXU)

        if os.path.exists(self.metaDataDir):
            if not os.path.isdir(self.metaDataDir):
                raise CacheError("Path " + self.metaDataDir + \
                        " is not a directory.")
            if not os.access(self.metaDataDir, os.R_OK | os.W_OK | os.X_OK):
                raise CacheError("Existing path " + self.metaDataDir + \
                        " does not have on of read, write or execute" +
                        " permission.")
        else:
            os.makedirs(self.metaDataDir, stat.S_IRWXU)

    def __enter__(self):
        """
        This method allows Cache object to be used with the "with"
        construct.
        """
        return self

    def __exit__(self, type, value, traceback):
        """
        This method allows Cache object to be used with the "with"
        construct.
        """
        pass

    #@logExceptions()
    def open(self, path, isNew, mustExist, ioObject, trustMetaData):
        """Open file with the desired modes

        Here we return a handle to the cached version of the file
        which is updated if older and out of synce with VOSpace.

        path - the path to the file.

        isNew - should be True if this is a completely new file, and False if
            reads should access the bytes from the file in the backing store.
        mustExist - The open mode requires the file to exist.
        trustMetaData - Trust that meta data exists.

        ioObject - the object that provides access to the backing store
        """

        fileHandle = self.getFileHandle(path, isNew, ioObject)
        with fileHandle.fileLock:
            vos.logger.debug("Opening file %s: isnew %s: id %d" % (path, isNew,
                        id(fileHandle)))
            # If this is a new file, initialize the cache state, otherwise
            # leave it alone.
            if fileHandle.fullyCached is None:
                if isNew:
                    fileHandle.fileModified = True
                    fileHandle.fullyCached = True
                    fileHandle.fileSize = 0
                    fileHandle.gotHeader = True
                elif os.path.exists(fileHandle.cacheMetaDataFile):
                    fileHandle.metaData = CacheMetaData(
                            fileHandle.cacheMetaDataFile, None, None, None)
                    if (fileHandle.metaData.getNumReadBlocks() ==
                            len(fileHandle.metaData.bitmap)):
                        fileHandle.fullyCached = True
                        fileHandle.fileSize = os.path.getsize(
                                fileHandle.cacheMetaDataFile)
                    else:
                        fileHandle.fullyCached = False
                        fileHandle.fileSize = fileHandle.metaData.size
                    if trustMetaData:
                        fileHandle.gotHeader = True
                        fileHandle.fileSize = fileHandle.metaData.size
                    else:
                        fileHandle.gotHeader = False
                else:
                    fileHandle.metaData = None
                    fileHandle.gotHeader = False
                    fileHandle.fullyCached = False

                if (not fileHandle.fullyCached and
                        (fileHandle.metaData is None or
                        fileHandle.metaData.getNumReadBlocks() == 0)):
                    # If the cache file should be empty, empty it.
                    os.ftruncate(fileHandle.ioObject.cacheFileDescriptor, 0)
                    os.fsync(fileHandle.ioObject.cacheFileDescriptor)

            # For an existing file, start a data transfer to get the size and
            # md5sum unless the information is available and is trusted.
            if (fileHandle.refCount == 1 and not fileHandle.fileModified and 
                    (fileHandle.metaData is None or not trustMetaData)):
                fileHandle.readData(0, 0, None)
                while (not fileHandle.gotHeader and
                        fileHandle.readException is None):
                    fileHandle.fileCondition.wait()
            vos.logger.debug("done wait %s %s" % (fileHandle.gotHeader,
                    fileHandle.readException))
            if fileHandle.readException is not None:
                # If the file doesn't exist and is not required to exist, then
                # an ENOENT error is ok and not propegated. All other errors
                # are propegated.
                if not (isinstance(fileHandle.readException[1], IOError) and
                        fileHandle.readException[1].errno == errno.ENOENT and
                        not mustExist):
                    raise fileHandle.readException[0], \
                            fileHandle.readException[1], \
                            fileHandle.readException[2]
                # The file didn't exist on the backing store but its ok
                fileHandle.fullyCached = True
                fileHandle.gotHeader = True
                fileHandle.fileSize = 0

        self.checkCacheSpace()

        return fileHandle

    def getFileHandle(self, path, createFile, ioObject):
        """Find an existing file handle, or create one if necessary.
        """
        with self.cacheLock:
            try:
                newFileHandle = self.fileHandleDict[path]
                isNewFileHandle = False
            except KeyError:
                isNewFileHandle = True
                newFileHandle = FileHandle(path, self, ioObject)
                self.fileHandleDict[path] = newFileHandle
            if not isNewFileHandle and createFile:
                # We got an old file handle, but are creating a new file.
                # Mark the old file handle as obsolete and create a new
                # file handle.
                newFileHandle.obsolete = True
                if newFileHandle.metaData is not None:
                    try:
                        os.remove(newFileHandle.metaData.metaDataFile)
                    except OSError as e:
                        if e.errno != ENOENT:
                            raise
                newFileHandle = FileHandle(path, self, ioObject)
                del self.fileHandleDict[path]
                # Lock the newly acquired file handle to avoid any race
                # conditions after it is added to the dictionary and before
                # it is incremented.
                with newFileHandle.fileLock:
                    self.fileHandleDict[path] = newFileHandle
                    newFileHandle.refCount += 1
            else:
                newFileHandle.refCount += 1
        return newFileHandle

    def checkCacheSpace(self):
        """Clear the oldest files until cache_size < cache_limit"""

        # TODO - this really needs to be moved into a background thread which
        # wakes up when other methods think it needs to be done. Having
        # multiple threads do this is bad. It should also be done on a
        # schedule to allow for files which grow.
        (oldest_file, cacheSize) = self.determineCacheSize()
        while (cacheSize / 1024 / 1024 > self.maxCacheSize and
                oldest_file is not None):
            with self.cacheLock:
                if oldest_file[len(self.dataDir):] not in self.fileHandleDict:
                    vos.logger.debug("Removing file %s from the local cache" %
                            oldest_file)
                    try:
                        os.unlink(oldest_file)
                        os.unlink(self.metaDataDir +
                                oldest_file[len(self.dataDir):])
                    except OSError:
                        pass
                    self.removeEmptyDirs(os.path.dirname(oldest_file))
                    self.removeEmptyDirs(os.path.dirname(self.metaDataDir +
                                oldest_file[len(self.dataDir):]))
            # TODO - Tricky - have to get a path to the meta data given
            # the path to the data. metaData.remove(oldest_file)
            (oldest_file, cacheSize) = self.determineCacheSize()

    def removeEmptyDirs(self, dirName):
        if os.path.commonprefix((dirName, self.cacheDir)) != self.cacheDir:
            raise ValueError("Path '%s' is not in the cache." % dirName)

        thisDir = dirName
        while thisDir != self.cacheDir:
            try:
                os.rmdir(thisDir)
            except OSError as e:
                if e.errno == ENOTEMPTY:
                    return
                elif e.errno == ENOENT:
                    pass
                else:
                    raise

            thisDir = os.path.dirname(thisDir)

    def determineCacheSize(self):
        """Determine how much disk space is being used by the local cache"""
        # TODO This needs to be cleaned up. There has to be a more efficient
        # way to clean up the cache.

        start_path = self.dataDir
        total_size = 0

        self.atimes = {}
        oldest_time = time.time()
        oldest_file = None
        for dirpath, dirnames, filenames in os.walk(start_path):
            for f in filenames:
                fp = os.path.join(dirpath, f)
                with self.cacheLock:
                    inFileHandleDict = (fp[len(self.dataDir):] not in
                            self.fileHandleDict)
                if (inFileHandleDict and oldest_time > os.stat(fp).st_atime):
                    oldest_time = os.stat(fp).st_atime
                    oldest_file = fp
                total_size += os.path.getsize(fp)
        return (oldest_file, total_size)

    def unlinkFile(self, path):
        """Remove a file from the cache."""

        vos.logger.debug("unlink %s:" % path)

        if not os.path.isabs(path):
            raise ValueError("Path '%s' is not an absolute path." % path)

        with self.cacheLock:
            try:
                existingFileHandle = self.fileHandleDict[path]
            except KeyError:
                existingFileHandle = None

            if existingFileHandle is not None:
                with existingFileHandle.fileLock:
                    existingFileHandle.obsolete = True
                    del self.fileHandleDict[path]

            # Ignore errors that the file does not exist
            try:
                os.remove(self.metaDataDir + path)
            except OSError:
                pass
            try:
                os.remove(self.dataDir + path)
            except OSError:
                pass

            self.removeEmptyDirs(os.path.dirname(self.metaDataDir + path))
            self.removeEmptyDirs(os.path.dirname(self.dataDir + path))

    def renameFile(self, oldPath, newPath):
        """Rename a file in the cache."""

        if not os.path.isabs(oldPath):
            raise ValueError("Path '%s' is not an absolute path." % oldPath)
        if not os.path.isabs(newPath):
            raise ValueError("Path '%s' is not an absolute path." % newPath)
        newDataPath = self.dataDir + newPath
        newMetaDataPath = self.metaDataDir + newPath
        oldDataPath = self.dataDir + oldPath
        oldMetaDataPath = self.metaDataDir + oldPath
        if os.path.isdir(oldDataPath):
            raise ValueError("Path '%s' is a directory." % oldDataPath)
        if os.path.isdir(oldMetaDataPath):
            raise ValueError("Path '%s' is a directory." % oldMetaDataPath)

        with self.cacheLock:
            # Make sure the new directory exists.
            try:
                os.makedirs(os.path.dirname(newDataPath), stat.S_IRWXU)
            except OSError:
                pass
            try:
                os.makedirs(os.path.dirname(newMetaDataPath), stat.S_IRWXU)
            except OSError:
                pass

            try:
                existingFileHandle = self.fileHandleDict[oldPath]
                # If the file is active, rename its files with the lock held.
                with existingFileHandle.fileLock:
                    Cache.atomicRename((oldDataPath, newDataPath),
                            (oldMetaDataPath, newMetaDataPath))
                    existingFileHandle.cacheDataFile = \
                            os.path.abspath(newDataPath)
                    existingFileHandle.cacheMetaDataFile = \
                            os.path.abspath(newMetaDataPath)
            except KeyError:
                # The file is not active, rename the files but there is no
                # data structure to lock or fix.
                Cache.atomicRename((oldDataPath, newDataPath),
                        (oldMetaDataPath, newMetaDataPath))

    @staticmethod
    def atomicRename(*renames):
        """Atomically rename multiple paths. It isn't an error if one of the
           paths doesn't exist.
        """
        renamedList = []
        try:
            for pair in renames:
                if Cache.pathExists(pair[0]):
                    os.rename(pair[0], pair[1])
                    renamedList.append(pair)
        except:
            for pair in renamedList:
                os.rename(pair[1], pair[0])
            raise

    def renameDir(self, oldPath, newPath):
        """Rename a directory in the cache."""

        if not os.path.isabs(oldPath):
            raise ValueError("Path '%s' is not an absolute path." % oldPath)
        if not os.path.isabs(newPath):
            raise ValueError("Path '%s' is not an absolute path." % newPath)
        newDataPath = os.path.abspath(self.dataDir + newPath)
        newMetaDataPath = os.path.abspath(self.metaDataDir + newPath)
        oldDataPath = os.path.abspath(self.dataDir + oldPath)
        oldMetaDataPath = os.path.abspath(self.metaDataDir + oldPath)
        if os.path.isfile(oldDataPath):
            raise ValueError("Path '%s' is not a directory." % oldDataPath)
        if os.path.isfile(oldMetaDataPath):
            raise ValueError("Path '%s' is not a directory." % oldMetaDataPath)

        with self.cacheLock:
            # Make sure the new directory exists.
            try:
                os.makedirs(os.path.dirname(newDataPath), stat.S_IRWXU)
            except OSError:
                pass
            try:
                os.makedirs(os.path.dirname(newMetaDataPath), stat.S_IRWXU)
            except OSError:
                pass

            # Lock any active file in the cache. Lock them all so nothing tries
            # to open a file, do then rename, and then unlock them all. A happy
            # hunging ground for deadlocks.
            try:
                renamed = False
                lockedList = []
                for path in self.fileHandleDict:
                    if path.startswith(oldPath):
                        fh = self.fileHandleDict[path]
                        fh.fileLock.acquire()
                        lockedList.append(fh)
                Cache.atomicRename((oldDataPath, newDataPath),
                        (oldMetaDataPath, newMetaDataPath))
                renamed = True
            finally:
                for fh in lockedList:
                    if renamed:
                        # Change the data file name and meta data file name in
                        # the file handle.
                        start = len(oldDataPath)
                        fh.cacheDataFile = os.path.abspath(self.dataDir +
                                newPath + fh.cacheDataFile[start:])
                        start = len(oldMetaDataPath)
                        fh.cacheMetaDataFile = os.path.abspath(
                                self.metaDataDir + newPath +
                                fh.cacheMetaDataFile[start:])
                    fh.fileLock.release()

    def getAttr(self, path):
        """Get the attributes of a cached file.

        This method will only return attributes if the cached file's attributes
        are better than the backing store's attributes. I.e. if the file is
        open and has been modified.
        """
        vos.logger.debug("gettattr %s:" % path)

        with self.cacheLock:
            # Make sure the file state doesn't change in the middle.
            try:
                fileHandle = self.fileHandleDict[path]
            except KeyError:
                return None
            with fileHandle.fileLock:
                if fileHandle.fileModified:
                    vos.logger.debug("file modified: %s" %
                            fileHandle.fileModified)
                    f = os.stat(fileHandle.cacheDataFile)
                    vos.logger.debug("size = %d:" % f.st_size)
                    return dict((name, getattr(f, name))
                            for name in dir(f)
                            if not name.startswith('__'))
                else:
                    return None

    @staticmethod
    def pathExists(path):
        """Return true if the file exists"""

        try:
            os.stat(path)
        except Exception as e:
            if isinstance(e, OSError) and (e.errno == errno.EEXIST or
                    e.errno == errno.ENOENT):
                return False
            else:
                raise
        return True


class CacheError(Exception):
    def __init__(self, value):
        self.value = value

    def __str__(self):
        return repr(self.value)


class CacheRetry(Exception):
    def __init__(self, value):
        self.value = value

    def __str__(self):
        return repr(self.value)


class CacheAborted(Exception):
    # Thrown when a cache operation should be aborted.

    def __init__(self, value):
        self.value = value

    def __str__(self):
        return repr(self.value)


class IOProxy(object):
    """
    This is an abstract class used to provide functionality to do IO for the
    cache. The methods which raise the NotImplementedError exception must be
    implemented by the end user.
    """

    def __init__(self):
        """
        The initializer indicates if the IO object supports random read or
        random write.
        """

        self.lock = threading.RLock()
        self.cacheFile = None
        self.cache = None
        self.cacheFileDescriptor = None
        self.currentWriteOffset = None

    def getMD5(self):
        """
        Return the MD5sum of the remote file.
        """
        raise NotImplementedError("IOProxy.getMD5")

    def getSize(self):
        """
        Return the size of the remote file.
        """
        raise NotImplementedError("IOProxy.getMD5")

    def delNode(self, force=False):
        """
        Delete the remote file.
        """
        raise NotImplementedError("IOProxy.delNode")

    def writeToBacking(self):
        """
        Write a file in the cache to the remote file.

        Return the md5sum of the file written.
        """
        raise NotImplementedError("IOProxy.writeToBacking")

    def readFromBacking(self, size=None, offset=0,
            blockSize=Cache.IO_BLOCK_SIZE):
        """
        Read a file from the remote system into cache.
        If size is None, write to the end of the file.
        offset is the offset into the file for the start of the read.
        blocksize is the recommended blocksize.

        The implementation must use the writeToCache method to write the data
        to the cache.
        """
        raise NotImplementedError("IOProxy.readFromBacking")

    def writeToCache(self, buffer, offset):
        """
        Function to write data to a the cache. This method should only be used
        by the implementation of the readFromBacking method.

        The method raises a CacheAborted exception if the preload should be
        aborted.
        """

        #vos.logger.debug("writing %d bytes at %d" % (len(buffer), offset))

        if (self.currentWriteOffset is not None and
                self.currentWriteOffset != offset):
            # Only allow seeks to block boundaries
            if (offset % self.cache.IO_BLOCK_SIZE != 0 or
                    (self.currentWriteOffset % self.cache.IO_BLOCK_SIZE != 0
                    and self.currentWriteOffset != self.cacheFile.fileSize)):
                raise CacheError("Only seeks to block boundaries are "
                    "permitted when writing to cache: %d %d %d %d" % (offset,
                    self.currentWriteOffset, self.cache.IO_BLOCK_SIZE,
                    self.cacheFile.fileSize))
            self.currentWriteOffset = offset

        if offset + len(buffer) > self.cacheFile.fileSize:
            raise CacheError("Attempt to populate cache past the end " +
                    "of the known file size: %d > %d." %
                    (offset + len(buffer), self.cacheFile.fileSize))

        with self.cacheFile.fileLock:
            os.lseek(self.cacheFileDescriptor, offset, os.SEEK_SET)
            # Write the data to the data file.
            nextByte = 0
            #byteBuffer = bytes(buffer)
            while nextByte < len(buffer):
                nextByte += os.write(self.cacheFileDescriptor,
                        buffer[nextByte:])

            # Set the mask bits corresponding to any completely read blocks.
            lastCompleteByte = offset + len(buffer)
            if lastCompleteByte != self.cacheFile.fileSize:
                lastCompleteByte = lastCompleteByte - (lastCompleteByte %
                        self.cache.IO_BLOCK_SIZE)
            firstBlock, numBlocks = self.blockInfo(offset, lastCompleteByte -
                    offset)
            if numBlocks > 0:
                self.cacheFile.metaData.setReadBlocks(firstBlock,
                        firstBlock + numBlocks - 1)
                self.cacheFile.fileCondition.notify_all()

            self.currentWriteOffset = offset + len(buffer)
            #vos.logger.debug("self.currentWriteOffset %d " %
            #        (self.currentWriteOffset))

            # Check to see if the current read has been aborted and if it
            # has, thow an exception
            if (self.cacheFile.readThread.aborted and
                    lastCompleteByte >=
                    self.cacheFile.readThread.mandatoryEnd and
                    lastCompleteByte <= self.cacheFile.fileSize):
                vos.logger.debug("reading to cache aborted for %s" %
                        self.cacheFile.path)
                raise CacheAborted("Read to cache aborted.")

        return nextByte

    def blockInfo(self, offset, size):
        """ Determine the blocks completed when "size" bytes starting at
        "offset" are written.
        """

        if size is None:
            return None, None

        firstBlock = offset / self.cache.IO_BLOCK_SIZE
        if size == 0:
            numBlocks = 0
        else:
            numBlocks = (((offset + size - 1) / self.cache.IO_BLOCK_SIZE) -
                    firstBlock + 1)
        return firstBlock, numBlocks

    def setCacheFile(self, cacheFile):
        self.cacheFile = cacheFile
        self.cache = cacheFile.cache


class FileHandle(object):
    def __init__(self, path, cache, ioObject):
        self.path = path
        self.cache = cache
        vos.logger.debug("creating a new File Handle for %s" % path)
        if not os.path.isabs(path):
            raise ValueError("Path '%s' is not an absolute path." % path)
        self.cacheDataFile = os.path.abspath(cache.dataDir + path)
        self.cacheMetaDataFile = os.path.abspath(cache.metaDataDir + path)
        self.metaData = None
        self.ioObject = ioObject
        ioObject.setCacheFile(self)
        try:
            os.makedirs(os.path.dirname(self.cacheDataFile), stat.S_IRWXU)
        except OSError:
            pass
        self.ioObject.cacheFileDescriptor = os.open(self.cacheDataFile,
                os.O_RDWR | os.O_CREAT)
        info = os.fstat(self.ioObject.cacheFileDescriptor)
        # When cache locks and file locks need to be held at the same time,
        # always acquire the cache lock first.
        # Lock for modifing the FileHandle object.
        self.fileLock = threading.RLock()
        self.fileCondition = CacheCondition(self.fileLock,
                timeout=cache.timeout)
        # Lock for modifying content of a file. A shared lock should be
        # acquired whenever the data is modified. An exclusive lock should be
        # acquired when data is flushed to the backing store.
        self.writerLock = SharedLock()
        self.refCount = 0
        self.fileModified = False
        self.fullyCached = None
        # Is this file now obsoleted by a new file.
        self.obsolete = False
        # Is the file flush out to vospace queued right now?
        self.flushQueued = None
        self.flushException = None
        self.readThread = None
        self.gotHeader = False
        self.fileSize = None
        self.readException = None

    def __enter__(self):
        return self

    def __exit__(self, a1, a2, a3):
        self.release()

    def setHeader(self, size, md5):
        """ Attempt to set the file size and md5sum."""
        vos.logger.debug("size: %s md5: %s" % (size, md5))
        if self.gotHeader:
            return

        self.fileSize = size
        blocks, numBlock = self.ioObject.blockInfo(0, self.fileSize)

        # If the file had meta data and it hasn't change, abort the read
        # thread.
        if self.metaData is not None and (self.metaData.md5sum is None or
                self.metaData.md5sum == md5):
            with self.fileCondition:
                if self.readThread is not None:
                    self.readThread.aborted = True

        # If the md5sum isn't the same, the cache data is no good.
        if self.metaData is None or self.metaData.md5sum != md5:
            self.metaData = CacheMetaData(self.cacheMetaDataFile, numBlock,
                    md5, size)
            self.fullyCached = False

        if not self.fullyCached and self.metaData.getNumReadBlocks() == 0:
            # If the cache file should be empty, empty it.
            os.ftruncate(self.ioObject.cacheFileDescriptor, 0)
            os.fsync(self.ioObject.cacheFileDescriptor)
        self.gotHeader = True

    def setReadException(self):
        self.readException = sys.exc_info()

    def release(self):
        """Close the file.

        The last release of a modified file may respond by raising a CacheRetry
        exception if the flush to the backing store takes longer than the
        timeout specified for the cache. The caller should respond to this by
        repeating the call to release after satisfying the FUSE timeout
        requirement.
        """

        vos.logger.debug("releasing node %s: id %d: refcount %d: modified %s: "
                "obsolete: %s" %
                (self.path, id(self), self.refCount, self.fileModified,
                self.obsolete))
        self.fileCondition.setTimeout()
        # using the condition lock acquires the fileLock
        with self.fileCondition:
            # Tell any running read thread to exit
            if self.refCount == 1 and self.readThread is not None:
                self.readThread.aborted = True

            # If flushing is not already in progress, submit to the thread
            # queue.
            if (self.flushQueued is None and self.refCount == 1 and
                    self.fileModified and not self.obsolete):

                self.refCount += 1

                # Acquire the writer lock exclusively. This will prevent
                # the file from being modified while it is being
                # flushed. This used to be inside flushNode when it was
                # executed immediately in a new thread. However, now that
                # this is done by a thread queue there could be a large
                # delay, so the lock is acquired by the thread that puts
                # it in the queue and waits for it to finish. The lock
                # is released by the worker thread doing the flush (and
                # it needs to steal the lock in order to do so...)
                self.writerLock.acquire(shared=False)

                self.cache.flushNodeQueue.put(self)
                self.flushQueued = True;
                vos.logger.debug("queue size now %i" \
                                     % self.cache.flushNodeQueue.qsize())

            while (self.flushQueued is not None or
                   self.readThread is not None):
                # Wait for the flush to complete.
                vos.logger.debug("flushQueued: %s, readThread: %s" %
                        (self.flushQueued, self.readThread))
                vos.logger.debug("Waiting for flush to complete.")
                self.fileCondition.wait()

            # Look for write failures.
            if self.flushException is not None:
                raise self.flushException[0], self.flushException[1], \
                        self.flushException[2]
        self.deref()

        return

    def deref(self):
        with nested(self.cache.cacheLock, self.fileLock):
            self.refCount -= 1
            if self.refCount == 0:
                os.close(self.ioObject.cacheFileDescriptor)
                self.ioObject.cacheFileDescriptor = None
                if not self.obsolete:
                    # The entry in fileHandleDict may have been removed by
                    # unlink, so don't panic
                    if self.metaData is not None:
                        self.metaData.persist()
                    try:
                        del self.cache.fileHandleDict[self.path]
                    except KeyError:
                        pass
        return

    def getFileInfo(self):
        """Get the current file information for the file."""

        info = os.fstat(self.ioObject.cacheFileDescriptor)
        return info.st_size, info.st_mtime

    def flushNode(self):
        """Flush the file to the backing store.
        """

        global _flush_thread_count
        _flush_thread_count = _flush_thread_count + 1

        vos.logger.debug("flushing node %s, working thread count is %i " \
                             % (self.path,_flush_thread_count))
        self.flushException = None

        # Now that the flush has started we want this thread to own
        # the lock
        self.writerLock.steal()

        try:
            # Get the md5sum of the cached file
            size, mtime = self.getFileInfo()

            # Write the file to vospace.

            with self.fileLock:
                os.fsync(self.ioObject.cacheFileDescriptor)
            md5 = self.ioObject.writeToBacking()

            # Update the meta data md5
            blocks, numBlocks = self.ioObject.blockInfo(0, size)
            self.metaData = CacheMetaData(self.cacheMetaDataFile,
                                          numBlocks, md5, size)
            if numBlocks > 0:
                self.metaData.setReadBlocks(0, numBlocks - 1)
            self.metaData.md5sum = md5
            self.metaData.persist()

        except Exception as e:
            vos.logger.debug("Flush node failed")
            self.flushException = sys.exc_info()
        finally:
            self.flushQueued = None
            self.writerLock.release()
            self.fileModified = False
            self.deref()
            _flush_thread_count = _flush_thread_count - 1
            vos.logger.debug("finished flushing node %s, working thread count is %i " \
                             % (self.path,_flush_thread_count))
            # Wake up any threads waiting for the flush to finish
            with self.fileCondition:
                self.fileCondition.notify_all()

        self.cache.checkCacheSpace()

        return

    def write(self, data, size, offset):
        """Write data to the file.
        This method will raise a CacheRetry error if the response takes longer
        than the timeout.
        """

        vos.logger.debug("writting %d bytes at %d to %d " % (size, offset,
                self.ioObject.cacheFileDescriptor))

        if self.fileSize is None:
            self.fileSize = self.ioObject.getSize()

        # Acquire a shared lock on the file
        with self.writerLock(shared=True):

            # Ensure the entire file is in cache.
            # TODO (optimization) It isn't necessary to always read the file.
            #      Only if the write would cause a gap in the written data. If
            #      there is never a gap, the file would only need to be read on
            #      release in order to fill in the end of the file (only if the
            #      last data written is before the end of the old file.
            #      However, this creates a tricky problem of filling in only
            #      the part of the last cache block which has not been written.
            #      Also, it isn't necessary to wait for the whole file to be
            #      read. The write could proceed when the blocks being written
            #      are read. Another argument to makeCached could be the blocks
            #      to wait for, separate from the last mandatory block.
            self.makeCached(offset, self.fileSize)

            r = self.ioObject.cacheFileDescriptor
            with self.fileCondition:
                # seek and write.
                os.lseek(r, offset, os.SEEK_SET)
                wroteBytes = libc.write(r, data, size)
                if wroteBytes < 0:
                    raise CacheError("Failed to write to cache file")

                # Update file size if it changed
                if offset + size > self.fileSize:
                    self.fileSize = offset + size
                self.fileModified = True

        return wroteBytes

    #@logExceptions()
    def read(self, size, offset):
        """Read data from the file.
        This method will raise a CacheRetry error if the response takes longer
        than the timeout.

        TODO: Figure out a way to add a buffer to the parameters so the buffer
        isn't allocated for each read.
        """

        vos.logger.debug("reading %d bytes at %d " % (size, offset))

        self.fileCondition.setTimeout()

        # Ensure the required blocks are in the cache
        self.makeCached(offset, size)

        r = self.ioObject.cacheFileDescriptor
        with self.fileLock:
            # seek and read.
            os.lseek(r, offset, os.SEEK_SET)
            libc.lseek(r, offset, os.SEEK_SET)
            startoffset = os.lseek(r, 0, os.SEEK_CUR)
            cbuffer = ctypes.create_string_buffer(size)
            retsize = libc.read(r, cbuffer, size)
            if retsize < 0:
                raise CacheError("Failed to read from cache file")
            endoffset = os.lseek(r, 0, os.SEEK_CUR)
        if retsize != size:
            newcbuffer = ctypes.create_string_buffer(cbuffer[0:retsize],
                    retsize)
            cbuffer = newcbuffer

        return cbuffer

    #@logExceptions()
    def makeCached(self, offset, size):
        """Ensure the specified data is in the cache file.

        This method will raise a CacheRetry error if the response takes longer
        than the timeout.
        """
        firstBlock, numBlock = self.ioObject.blockInfo(offset, size)

        # If the whole file is cached, return
        if self.fullyCached or numBlock == 0:
            return

        lastBlock = firstBlock + numBlock - 1

        requiredRange = self.metaData.getRange(firstBlock, lastBlock)

        # If the required part of the file is cached, return
        if requiredRange == (None, None):
            return

        # There is a current read thread and it will "soon" get to the required
        # data, modify the mandatory read range of the read thread.

        # Acquiring self.fileCondition acquires self.fileLock
        with self.fileCondition:
            if self.readThread is not None:
                start = requiredRange[0] * Cache.IO_BLOCK_SIZE
                size = ((requiredRange[1] - requiredRange[0] + 1) *
                        Cache.IO_BLOCK_SIZE)
                startNewThread = self.readThread.isNewReadBest(start, size)

            while self.readThread is not None:
                if startNewThread:
                    vos.logger.debug("aborting the read thread for %s" %
                            self.path)
                    # abort the thread
                    self.readThread.aborted = True

                    # wait for the existing thread to exit. This may time out
                    self.fileCondition.wait()
                else:
                    while (self.metaData.getRange(firstBlock, lastBlock) !=
                            (None, None) and
                            self.readThread is not None):
                        #vos.logger.debug("needBlocks %s" %
                                #str(self.metaData.getRange(firstBlock,
                                #lastBlock)))
                        self.fileCondition.wait()

                if (self.metaData.getRange(firstBlock, lastBlock) ==
                        (None, None)):
                    return

            # Make sure the required range hasn't changed
            requiredRange = self.metaData.getRange(firstBlock, lastBlock)

            # No read thread running, start one.
            startByte = requiredRange[0] * Cache.IO_BLOCK_SIZE
            mandatorySize = min((requiredRange[1] + 1) * Cache.IO_BLOCK_SIZE,
                    self.fileSize) - startByte

            # Figure out where the optional end of the read should be.
            nextRead = self.metaData.getNextReadBlock(requiredRange[1])
            if nextRead == -1:
                # Reading right to the end of the file.
                optionalSize = self.fileSize - startByte
            else:
                # Reading to an intermediate end point.
                optionalSize = (nextRead * Cache.IO_BLOCK_SIZE) - startByte

            vos.logger.debug(" Starting a cache read thread for %d %d %d" %
                    (startByte, mandatorySize, optionalSize))
            self.readData(startByte, mandatorySize, optionalSize)

            # Wait for the data be be available.
            while (self.metaData.getRange(firstBlock, lastBlock) !=
                    (None, None) and self.readThread is not None):
                self.fileCondition.wait()

    def fsync(self):
        with self.fileLock:
            if self.ioObject.cacheFileDescriptor is not None:
                with self.fileLock:
                    os.fsync(self.ioObject.cacheFileDescriptor)

    def truncate(self, length):
        vos.logger.debug("Truncate %s" % (self.path, ))
        # Acquire an exclusive lock on the file
        with self.writerLock(shared=False):
            with self.fileLock:
                if self.fileSize is not None and length == self.fileSize:
                    return

            # Ensure the required part of the file is in cache.
            self.makeCached(0, min(length, self.fileSize))
            with self.fileLock:
                os.ftruncate(self.ioObject.cacheFileDescriptor, length)
                os.fsync(self.ioObject.cacheFileDescriptor)
                self.fileModified = True
                self.fullyCached = True
                self.fileSize = length

    def readData(self, startByte, mandatorySize, optionalSize):
        """Read the data range from the backing store in a thread"""

        vos.logger.debug("Getting data: %s %s %s" % (startByte, mandatorySize,
                optionalSize))
        self.readThread = CacheReadThread(startByte, mandatorySize,
                optionalSize, self)
        self.readThread.start()


class CacheReadThread(threading.Thread):
    CONTINUE_MAX_SIZE = 1024 * 1024 * 4

    def __init__(self, start, mandatorySize, optionSize, fileHandle):
        """ CacheReadThread class is used to start data transfer from the back
            end in a separate thread. It also decides whether it can
            accommodate new requests or new CacheReadThreads is required.

            start - start reading position
            mandatorySize - mandatory size that needs to be read
            optionSize - optional size that can be read beyond the mandatory
            fileHandle - file handle """
        threading.Thread.__init__(self, target=self.execute)
        self.startByte = start
        self.mandatoryEnd = start + mandatorySize
        self.optionSize = optionSize
        if optionSize is not None:
            self.optionEnd = start + optionSize
        else:
            self.optionEnd = None
        self.aborted = False
        self.fileHandle = fileHandle
        self.currentByte = start
        self.traceback = traceback.extract_stack()

    def setCurrentByte(self, byte):
        """ To set the current byte being successfully cached"""
        self.currentByte = byte

    def isNewReadBest(self, start, size):
        """
        To determine if a new read request can be satisfied with the
        existing thread or a new thread is required. It returns true if a
        new read is required or false otherwise.
        Must be called with the #fileLock acquired
        """

        if start < self.startByte:
            return True
        if self.optionEnd is not None and (start + size) > (self.optionEnd):
            self.mandatoryEnd = self.optionEnd
            return True
        readRef = max(self.mandatoryEnd, self.currentByte)
        if (start <= readRef) or ((start - readRef)
                                      <= CacheReadThread.CONTINUE_MAX_SIZE):
            self.mandatoryEnd = max(self.mandatoryEnd, start + size)
            return False
        return True

    #@logExceptions()
    def execute(self):
        try:
            self.fileHandle.readException = None
            self.fileHandle.ioObject.readFromBacking(self.optionSize,
                    self.startByte)
            with self.fileHandle.fileCondition:
                vos.logger.debug("setFullyCached? %s %s %s %s" % (self.aborted,
                self.startByte, self.optionSize, self.fileHandle.fileSize))
                if self.aborted:
                    return
                elif (self.startByte == 0 and
                        (self.optionSize is None or
                        self.optionSize == self.fileHandle.fileSize)):
                    self.fileHandle.fullyCached = True
                    vos.logger.debug("setFullyCached")
                elif self.fileHandle.fileSize == 0:
                    self.fileHandle.fullyCached = True
                else:
                    if self.fileHandle.fileSize is not None:
                        firstBlock, numBlocks = self.fileHandle.ioObject. \
                                blockInfo(0, self.fileHandle.fileSize)
                        requiredRange = self.fileHandle.metaData.getRange(
                                firstBlock, firstBlock + numBlocks - 1)
                        if requiredRange == (None, None):
                            self.fileHandle.fullyCached = True
                    # TODO - The file is fully cached, verify that the file
                    # matches the vospace content. Is that overly strict - the
                    # original VOFS did this, but it was subject to a much
                    # smaller read window. Also it is not invalid for the file
                    # to be replaced in vospace, and for this client to
                    # continue to serve the existing file.
        except:
            vos.logger.error("Exception in thread started at:\n%s" % \
                     ''.join(traceback.format_list(self.traceback)))
            self.fileHandle.setReadException()
            raise
        finally:
            vos.logger.debug("read thread finished")
            with self.fileHandle.fileCondition:
                if self.fileHandle.readThread is not None:
                    self.fileHandle.readThread = None
                    self.fileHandle.fileCondition.notify_all()


class FlushNodeQueue(Queue):
    """
    This class implements a thread queue for flushing nodes
    """

    def __init__(self, maxFlushThreads=100):
        """Initialize the FlushNodeQueue Object

        Parameters:
        -----------
        maxFlushThreads : int - Maximum number of flush threads
        """

        Queue.__init__(self)

        # Start the worker threads
        self.maxFlushThreads = maxFlushThreads
        for i in range(self.maxFlushThreads):
            t = threading.Thread(target=self.worker)
            t.daemon = True
            t.start()

        vos.logger.debug("started a FlushNodeQueue with %i workers" \
                             % self.maxFlushThreads)

    def join(self):
        vos.logger.debug("FlushNodeQueue waiting until all work is done")
        Queue.join(self)

    def worker(self):
        """A worker is a thin wrapper for FileHandle.flushNode()
        """
       # vos.logger.debug("Worker has started")
        while True:
            fileHandle = self.get()
            #vos.logger.debug("Worker has work")
            fileHandle.flushNode()
            self.task_done()
