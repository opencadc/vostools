"""
A sparse file caching library
"""

import os
import sys
import io
import stat
import time
import threading
import logging
import hashlib
from os import O_RDONLY, O_WRONLY, O_RDWR, O_APPEND
import errno
from contextlib import nested
from errno import EACCES, EIO, ENOENT, EISDIR, ENOTDIR, ENOTEMPTY, EPERM, \
        EEXIST, ENODATA, ECONNREFUSED, EAGAIN, ENOTCONN
import ctypes

from SharedLock import SharedLock as SharedLock
from CacheMetaData import CacheMetaData as CacheMetaData
from logExceptions import logExceptions
import pdb
libcPath = ctypes.util.find_library('c')
libc = ctypes.cdll.LoadLibrary(libcPath)


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
    IO_BLOCK_SIZE = 2**14
    def __init__(self, cacheDir, maxCacheSize, readOnly = False, timeout = 60):
        """Initialize the Cache Object

        Parameters:
        -----------
        cacheDir : string - The directory for the cache.
        maxCacheSize : int - The maximum cache size in megabytes
        readOnly : boolean - Is the cached data read-only
        """

        self.cacheDir = cacheDir
        self.dataDir = os.path.join(cacheDir, "data")
        self.metaDataDir = os.path.join(cacheDir, "metaData")
        self.timeout = timeout
        self.maxCacheSize = maxCacheSize
        self.readOnly = readOnly
        self.fileHandleDict = {}
        # When cache locks and file locks need to be held at the same time,
        # always acquire the cache lock first.
        self.cacheLock = threading.RLock()

        if os.path.exists(self.cacheDir):
            if not os.path.isdir(self.cacheDir):
                raise CacheError("Path " + self.cacheDir + \
                        " is not a directory.")
            if not os.access(self.cacheDir, os.R_OK | os.W_OK | os.X_OK):
                raise CacheError("Existing path " + self.cacheDir + \
                        " does not have on of read, write or execute permission.")

        if os.path.exists(self.dataDir):
            if not os.path.isdir(self.dataDir):
                raise CacheError("Path " + self.dataDir + \
                        " is not a directory.")
            if not os.access(self.dataDir, os.R_OK | os.W_OK | os.X_OK):
                raise CacheError("Existing path " + self.dataDir + \
                        " does not have on of read, write or execute permission.")
        else:
            os.makedirs(self.dataDir, stat.S_IRWXU)

        if os.path.exists(self.metaDataDir):
            if not os.path.isdir(self.metaDataDir):
                raise CacheError("Path " + self.metaDataDir + \
                        " is not a directory.")
            if not os.access(self.metaDataDir, os.R_OK | os.W_OK | os.X_OK):
                raise CacheError("Existing path " + self.metaDataDir + \
                        " does not have on of read, write or execute permission.")
        else:
            os.makedirs(self.metaDataDir, stat.S_IRWXU)


    def __enter__(self):
        """This method allows Cache object to be used with the "with" construct.
        """
        return self


    def __exit__(self, type, value, traceback):
        """This method allows Cache object to be used with the "with" construct.
        """
        pass


    def open(self, path, isNew, ioObject):
        """Open file with the desired modes

        Here we return a handle to the cached version of the file
        which is updated if older and out of synce with VOSpace.

        path - the path to the file.

        isNew - should be True if this is a completely new file, and False if 
            reads should access the bytes from the file in the backing store.

        ioObject - the object that provides access to the backing store
        """

        fileHandle = self.getFileHandle(path, isNew, ioObject)
        with fileHandle.fileLock:
            logging.debug("Opening file %s: isnew %s: id %d" % (path, isNew,
                        id(fileHandle)))


            
            if isNew:
                fileHandle.fileModified = True
                fileHandle.fullyCached = True
                fileHandle.fileSize = 0
            else:
                fileHandle.fileSize = fileHandle.ioObject.getSize()
                blocks, numBlock = fileHandle.ioObject.blockInfo(0, 
                        fileHandle.fileSize)
                fileHandle.metaData = CacheMetaData(
                        fileHandle.cacheMetaDataFile, 
                        numBlock, fileHandle.ioObject.getMD5())
                if fileHandle.metaData.getNumReadBlocks() == numBlock:
                    fileHandle.fullyCached = True
                else:
                    fileHandle.fullyCached = False

            if (fileHandle.metaData is None or 
                    fileHandle.metaData.getNumReadBlocks() == 0):
                # If the cache file should be empty, empty it.
                os.ftruncate(fileHandle.ioObject.cacheFileDescriptor, 0)
                os.fsync(fileHandle.ioObject.cacheFileDescriptor)

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
        # wakes up when other methods think it needs to be done. Having multiple
        # threads do this is bad. It should also be done on a schedule to allow
        # for files which grow.
        (oldest_file, cacheSize) = self.determineCacheSize()
        while ( cacheSize/1024/1024 > self.maxCacheSize and 
                oldest_file != None ) :
            with self.cacheLock:
                if oldest_file[len(self.dataDir):] not in self.fileHandleDict:
                    logging.debug("Removing file %s from the local cache" % 
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
            raise ValueError("Path '%s' is not in the cache." % dirName )

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

        self.atimes={}
        oldest_time=time.time()
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

        logging.debug("unlink %s:" % path)

        if not os.path.isabs(path):
            raise ValueError("Path '%s' is not an absolute path." % path )

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
            raise ValueError("Path '%s' is not an absolute path." % oldPath )
        if not os.path.isabs(newPath):
            raise ValueError("Path '%s' is not an absolute path." % newPath )
        newDataPath = self.dataDir + newPath
        newMetaDataPath = self.metaDataDir + newPath
        oldDataPath = self.dataDir + oldPath
        oldMetaDataPath = self.metaDataDir + oldPath
        if os.path.isdir(oldDataPath):
            raise ValueError("Path '%s' is a directory." % oldDataPath )
        if os.path.isdir(oldMetaDataPath):
            raise ValueError("Path '%s' is a directory." % oldMetaDataPath )


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
                    Cache.atomicRename((oldDataPath,newDataPath),
                            (oldMetaDataPath, newMetaDataPath))
                    existingFileHandle.cacheDataFile = \
                            os.path.abspath(newDataPath)
                    existingFileHandle.cacheMetaDataFile = \
                            os.path.abspath(newMetaDataPath)
            except KeyError:
                # The file is not active, rename the files but there is no
                # data structure to lock or fix.
                Cache.atomicRename((oldDataPath,newDataPath),
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
            raise ValueError("Path '%s' is not an absolute path." % oldPath )
        if not os.path.isabs(newPath):
            raise ValueError("Path '%s' is not an absolute path." % newPath )
        newDataPath = os.path.abspath(self.dataDir + newPath)
        newMetaDataPath = os.path.abspath(self.metaDataDir + newPath)
        oldDataPath = os.path.abspath(self.dataDir + oldPath)
        oldMetaDataPath = os.path.abspath(self.metaDataDir + oldPath)
        if os.path.isfile(oldDataPath):
            raise ValueError("Path '%s' is not a directory." % oldDataPath )
        if os.path.isfile(oldMetaDataPath):
            raise ValueError("Path '%s' is not a directory." % oldMetaDataPath )

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
                        fh.cacheMetaDataFile = os.path.abspath(self.metaDataDir + 
                                newPath + fh.cacheMetaDataFile[start:])
                    fh.fileLock.release()

    def getAttr(self, path):
        """Get the attributes of a cached file. 

        This method will only return attributes if the cached file's attributes 
        are better than the backing store's attributes. I.e. if the file is open
        and has been modified.
        """
        logging.debug("gettattr %s:" % path)

        with self.cacheLock:
            # Make sure the file state doesn't change in the middle.
            try:
                fileHandle = self.fileHandleDict[path]
            except KeyError:
                return None
            with fileHandle.fileLock:
                if fileHandle.fileModified:
                    f = os.stat(fileHandle.cacheDataFile )
                    logging.debug("size = %d:" % f.st_size)
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
            if isinstance(e, OSError) and ( e.errno == errno.EEXIST or 
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


    def delNode(self, force = False):
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


    def readFromBacking(self, size = None, offset = 0, 
            blockSize = Cache.IO_BLOCK_SIZE):
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

        #logging.debug("writing %d bytes at %d" % (len(buffer), offset))

        if (self.currentWriteOffset != None and 
                self.currentWriteOffset != offset):
            # Only allow seeks to block boundaries
            if (offset % self.cache.IO_BLOCK_SIZE != 0 or
                    (self.currentWriteOffset % self.cache.IO_BLOCK_SIZE != 0 and
                    self.currentWriteOffset != self.cacheFile.fileSize)):
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
            firstBlock, numBlocks  = self.blockInfo(offset, lastCompleteByte -
                    offset) 
            if numBlocks > 0:
                self.cacheFile.metaData.setReadBlocks(firstBlock, 
                        firstBlock + numBlocks - 1)
                self.cacheFile.fileCondition.notify_all()

            self.currentWriteOffset = offset + len(buffer)
            #logging.debug("self.currentWriteOffset %d " % 
            #        (self.currentWriteOffset))


            # Check to see if the current read has been aborted and if it 
            # has, thow an exception
            if (self.cacheFile.readThread.aborted and 
                    lastCompleteByte >= 
                    self.cacheFile.readThread.mandatoryEnd and
                    lastCompleteByte <= self.cacheFile.fileSize):
                logging.debug("reading to cache aborted for %s" %
                        self.cacheFile.path)
                raise CacheAborted("Read to cache aborted.")

        return nextByte 

    def blockInfo(self, offset, size):
        """ Determine the blocks completed when "size" bytes starting at 
        "offset" are written.
        """

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
        if not os.path.isabs(path):
            raise ValueError("Path '%s' is not an absolute path." % path )
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
        self.fileCondition = CacheCondition(self.fileLock, timeout=cache.timeout)
        # Lock for modifying content of a file. A shared lock should be acquired
        # whenever the data is modified. An exclusive lock should be acquired
        # when data is flushed to the backing store.
        self.writerLock = SharedLock()
        self.refCount = 0
        self.fileModified = False
        self.fullyCached = None
        # Is this file now obsoleted by a new file.
        self.obsolete = False
        # Is the file being flushed out to vospace right now?
        self.flushThread = None
        self.flushException = None
        self.readThread = None

    def __enter__(self):
        return self

    def __exit__(self,a1,a2,a3):
        self.release()

    def release(self):
        """Close the file.

        The last release of a modified file may respond by raising a CacheRetry
        exception if the flush to the backing store takes longer than the
        timeout specified for the cache. The caller should respond to this by
        repeating the call to release after satisfying the FUSE timeout
        requirement.
        """

        logging.debug("releasing node %s: id %d: refcount %d: modified %s: "
                "obsolete: %s" % 
                (self.path, id(self), self.refCount, self.fileModified, 
                self.obsolete))
        self.fileCondition.setTimeout()
        # using the condition lock acquires the fileLock
        with self.fileCondition:
            # If flushing is not already in progress, start the thread
            if (self.flushThread is None and self.refCount == 1 and 
                    self.fileModified and not self.obsolete):
                if self.metaData is not None:
                    md5 = self.metaData.md5sum
                else:
                    md5 = None

                logging.debug("Start flush thread")
                self.flushThread = threading.Thread(target=self.flushNode,
                        args=[])
                self.flushThread.start()

            # Tell any running read thread to exit
            if self.refCount == 1 and self.readThread != None:
                self.readThread.aborted = True
            while (self.flushThread != None or self.readThread != None):
                # Wait for the flush to complete. This will throw a CacheRetry
                # exception if the timeout is exeeded.
                self.fileCondition.wait()

            # Look for write failures.
            if self.flushException is not None:
                raise self.flushException[0], self.flushException[1], \
                        self.flushException[2]

        with nested(self.cache.cacheLock, self.fileLock):
            self.refCount -= 1
            logging.debug("closed file descriptor?")
            if self.refCount == 0:
                os.close(self.ioObject.cacheFileDescriptor)
                logging.debug("closed file descriptor %d" %
                        self.ioObject.cacheFileDescriptor)
                self.ioObject.cacheFileDescriptor = None
                if not self.obsolete:
                    # The entry in fileHandleDict may have been removed by 
                    # unlink, so don't panic
                    self.metaData.persist()
                    del self.cache.fileHandleDict[self.path]

        self.cache.checkCacheSpace()

        return

    def getFileInfo(self):
        """Get the current file information for the file."""

        info = os.fstat(self.ioObject.cacheFileDescriptor)
        return info.st_size, info.st_mtime

    def flushNode(self):
        """Flush the file to the backing store.
        """

        logging.debug("flushing node %s "  % self.path)
        self.flushException = None

        # Acquire the writer lock exclusivly. This will prevent the file from
        # being modified while it is being flushed.
        with self.writerLock(shared=False):
            try:
                # Get the md5sum of the cached file
                size, mtime = self.getFileInfo()

                # Write the file to vospace.

                with self.fileLock:
                    os.fsync(self.ioObject.cacheFileDescriptor)
                md5 = self.ioObject.writeToBacking()

                # Update the meta data md5
                blocks,blocks = self.ioObject.blockInfo(0, size)
                self.metaData = CacheMetaData(self.cacheMetaDataFile, blocks, 
                        md5)
                self.metaData.md5sum = md5
                self.metaData.persist()

            except Exception as e:
                self.flushException = sys.exc_info()
            finally:
                self.flushThread = None
                # Wake up any threads waiting for the flush to finish
                with self.fileCondition:
                    self.fileCondition.notify_all()

        return


    def write(self, data, size, offset):
        """Write data to the file.
        This method will raise a CacheRetry error if the response takes longer
        than the timeout.
        """

        logging.debug("writting %d bytes at %d to %d "  % (size, offset,
                self.ioObject.cacheFileDescriptor))

        # Acquire a shared lock on the file
        with self.writerLock(shared=True):

            # Ensure the entire file is in cache.
            # TODO (optimization) It isn't necessary to always read the file. 
            #      Only if the write would cause a gap in the written data. If 
            #      there is never a gap, the file would only need to be read on 
            #      release in order to fill in the end of the file (only if the 
            #      last data written is before the end of the old file. However,
            #      this creates a tricky problem of filling in only the part of 
            #      the last cache block which has not been written.
            #      Also, it isn't necessary to wait for the whole file to be
            #      read. The write could proceed when the blocks being written
            #      are read. Another argument to makeCached could be the blocks
            #      to wait for, separate from the last mandatory block.
            firstBlock,numBlocks = self.ioObject.blockInfo(offset, self.fileSize)
            self.makeCached(0, numBlocks)

            r = self.ioObject.cacheFileDescriptor
            with self.fileCondition:
                # seek and write.
                os.lseek(r, offset, os.SEEK_SET)
                wroteBytes =  libc.write(r, data, size)

                # Update file size if it changed
                if offset + size > self.fileSize:
                    self.fileSize = offset + size
                self.fileModified = True

        return wroteBytes


    #@logExceptions()
    def read( self, size, offset):
        """Read data from the file.
        This method will raise a CacheRetry error if the response takes longer
        than the timeout.

        TODO: Figure out a way to add a buffer to the parameters so the buffer
        isn't allocated for each read.
        """

        logging.debug("reading %d bytes at %d "  % (size, offset))

        self.fileCondition.setTimeout()

        #if offset > self.fileSize or size + offset > self.fileSize:
            #raise ValueError("Attempt to read beyond the end of file: %s > %s."
            #% (size + offset, self.fileSize))

        # Ensure the required blocks are in the cache
        firstBlock,numBlocks = self.ioObject.blockInfo(offset, size)
        self.makeCached(firstBlock,numBlocks)

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

        logging.debug("read %d %d bytes to %d - %d"  % (r, retsize, 
                startoffset, endoffset))

        return cbuffer

    #@logExceptions()
    def makeCached(self, firstBlock, numBlock):
        """Ensure the specified data is in the cache file.

        This method will raise a CacheRetry error if the response takes longer
        than the timeout.
        """

        # If the whole file is cached, return
        if self.fullyCached or numBlock == 0:
            return

        requiredRange = self.metaData.getRange(firstBlock, firstBlock +
                numBlock - 1)

        # If the required part of the file is cached, return
        if requiredRange == (None,None):
            return

        ## There is a current read thread and it will "soon" get to the required
        ## data, modify the mandatory read range of the read thread.

        # Acquiring self.fileCondition acquires self.fileLock
        with self.fileCondition:
            if self.readThread is not None:
                start = requiredRange[0] * Cache.IO_BLOCK_SIZE
                size = ((requiredRange[1] - requiredRange[0] + 1) *
                        Cache.IO_BLOCK_SIZE)
                startNewThread = self.readThread.isNewReadBest(start, size)

            while self.readThread is not None:
                if startNewThread:
                    logging.debug("aborting the read thread for %s" % self.path)
                    # abort the thread
                    self.readThread.aborted = True

                    # wait for the existing thread to exit. This may time out
                    self.fileCondition.wait()
                else:
                    while (self.metaData.getRange(firstBlock, firstBlock +
                            numBlock - 1) != (None,None) and
                            self.readThread is not None):
                        #logging.debug("needBlocks %s" %
                        #    str(self.metaData.getRange(firstBlock, firstBlock +
                        #    numBlock - 1)))
                        self.fileCondition.wait()

                if (self.metaData.getRange(firstBlock, firstBlock +
                        numBlock - 1) == (None,None)):
                    return

            # Make sure the required range hasn't changed
            requiredRange = self.metaData.getRange(firstBlock, firstBlock +
                    numBlock - 1)

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

            self.readThread = CacheReadThread(startByte, mandatorySize,
                    optionalSize, self)
            self.readThread.start()

            # Wait for the data be be available.
            while (self.metaData.getRange(firstBlock, firstBlock +
                    numBlock - 1) != (None,None) and
                    self.readThread is not None):
                self.fileCondition.wait()


    def fsync(self):
        with self.fileLock:
            if self.ioObject.cacheFileDescriptor is not None:
                with self.fileLock:
                    os.fsync(self.ioObject.cacheFileDescriptor)


    def truncate(self, length):
        # Acquire an exclusive lock on the file
        with self.writerLock(shared=False):
            with self.fileLock:
                if length == self.ioObject.getSize():
                    return

            # Ensure the required part of the file is in cache.
            firstBlock,numBlocks = self.ioObject.blockInfo(0,
                    min(length,self.ioObject.getSize()))
            self.makeCached(0, numBlocks)
            with self.fileLock:
                os.ftruncate(self.ioObject.cacheFileDescriptor, length)
                self.fileModified = True
                self.fullyCached = True


class CacheReadThread(threading.Thread):
    CONTINUE_MAX_SIZE = 1024*1024
    
    def __init__(self, start, mandatorySize, optionSize, fileHandle):
        """ CacheReadThread class is used to start data transfer from the back end
            in a separate thread. It also decides whether it can accommodate new 
            requests or new CacheReadThreads is required.
            
            start - start reading position
            mandatorySize - mandatory size that needs to be read
            optionSize - optional size that can be read beyond the mandatory
            fileHandle - file handle """
        threading.Thread.__init__(self,target=self.execute)
        self.startByte = start
        self.mandatoryEnd = start + mandatorySize
        self.optionSize = optionSize
        self.optionEnd = start + optionSize
        self.aborted = False
        self.fileHandle = fileHandle
        self.currentByte = start

    def setCurrentByte(self, byte):
        """ To set the current byte being successfully cached"""
        self.currentByte = byte


    def isNewReadBest(self, start, size):
        """To determine if a new read request can be satisfied with the existing
           thread or a new thread is required. It returns true if a new read is
           required or false otherwise.
           Must be called with the #fileLock acquired"""
        if start < self.startByte:
            return True
        if (start + size) > (self.optionEnd):
            self.mandatoryEnd = self.optionEnd
            return True
        readRef = max(self.mandatoryEnd, self.currentByte)
        if (start <= readRef) or ((start - readRef) 
                                      <= CacheReadThread.CONTINUE_MAX_SIZE):
            self.mandatoryEnd = max(self.mandatoryEnd, start + size)
            return False       
        return True

    def execute(self):
        self.fileHandle.ioObject.readFromBacking(self.optionSize, 
                self.startByte)
        with self.fileHandle.fileCondition:
            self.fileHandle.readThread = None
            firstBlock,numBlocks = self.fileHandle.ioObject.blockInfo(0,
                    self.fileHandle.fileSize)
            requiredRange = self.fileHandle.metaData.getRange(firstBlock, 
                    firstBlock + numBlocks - 1)
            if requiredRange == (None, None):
                self.fileHandle.fullyCached = True
                # TODO - The file is fully cached, verify that the file matches
                # the vospace content. Is that overly strict - the original VOFS
                # did this, but it was subject to a much smaller read window.
                # Also it is not invalid for the file to be replaced in vospace,
                # and for this client to continue to serve the existing file.
            self.fileHandle.fileCondition.notify_all()
