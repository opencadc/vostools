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
from contextlib import nested
from errno import EACCES, EIO, ENOENT, EISDIR, ENOTDIR, ENOTEMPTY, EPERM, \
        EEXIST, ENODATA, ECONNREFUSED, EAGAIN, ENOTCONN
from SharedLock import SharedLock as SharedLock
from CacheMetaData import CacheMetaData as CacheMetaData
import vos.fuse
###from vos.fuse import FUSE, Operations, FuseOSError, LoggingMixIn


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


    def open(self, path, new, ioObject):
        """Open file with the desired modes

        Here we return a handle to the cached version of the file
        which is updated if older and out of synce with VOSpace.

	path - the path to the file.

	new - should be True if this is a completely new file, and False if reads 
	    should access the bytes from the file in the backing store.

	ioObject - the object that provides access to the backing store
        """

        fileHandle = self.getFileHandle(path, new, ioObject)
        ioObject.setCacheFile(fileHandle)

	
	if new:
	    fileHandle.fileModified = True
	    fileHandle.fullyCached = True
	    os.truncate(ioObject.cacheFileDescriptor)
	    fileHandle.metadata.delete()
	else:
	    size = ioObject.getSize()
	    blocks,blocks = ioObject.blockInfo(size, size)
	    fileHandle.metaData = CacheMetaData(fileHandle.cacheMetaDataFile, 
		    blocks, ioObject.getMD5())
	    fileHandle.fullycached = False

        self.checkCacheSpace()

        return fileHandle


    def getFileHandle(self, path, new, ioObject):
        """Find an existing file handle, or create one if necessary.
        """
        with self.cacheLock:
            try:
                newFileHandle = self.fileHandleDict[path]
		isNew = False
            except KeyError:
		isNew = True
                newFileHandle = FileHandle(path, self, ioObject)
                self.fileHandleDict[path] = newFileHandle
	    with newFileHandle.fileLock:
		if not isNew and new:
		    # We got an old file handle, but are creating a new file.
		    # Mark the old file handle as obsolete and create a new
		    # file handle.
		    with newFileHandle.fileLock:
			newFileHandle.obsolete = True
		    newFileHandle = FileHandle(path, self, ioObject)
		    del self.fileHandleDict[path]
		    self.fileHandleDict[path] = newFileHandle
		    newFileHandle.metaData.delete()

		newFileHandle.refCount += 1
        return newFileHandle


    @staticmethod
    def flag2mode(flags):
        """Convert os module flag bits to io module modes.
        """
        md = {O_RDONLY: 'r', O_WRONLY: 'w', O_RDWR: 'w+'}
        m = md[flags & (O_RDONLY | O_WRONLY | O_RDWR)]

        if flags | O_APPEND:
            m = m.replace('w', 'a', 1)

        return m


    def checkCacheSpace(self):
        """Clear the oldest files until cache_size < cache_limit"""


	# TODO - this really needs to be moved into a background thread which
	# wakes up when other methods think it needs to be done. Having multiple
	# threads do this is bad. It should also be done on a schedule to allow
	# for files which grow.
	(oldest_file, cacheSize) = self.determineCacheSize()
        while ( cacheSize/1024/1024 > self.maxCacheSize and oldest_file != None ) :
	    print cacheSize
	    with self.cacheLock:
		if oldest_file[len(self.dataDir):] not in self.fileHandleDict:
		    logging.debug("Removing file %s from the local cache" % 
			    oldest_file)
		    try:
			os.unlink(oldest_file)
			os.unlink(self.metaDataDir + oldest_file[len(self.dataDir):])
		    except OSError:
			pass
	    # TODO - Tricky - have to get a path to the meta data given
	    # the path to the data. metaData.remove(oldest_file)
	    (oldest_file, cacheSize) = self.determineCacheSize()


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
		print fp
		with self.cacheLock:
		    inFileHandleDict = (f[len(self.dataDir):] not in 
			    self.fileHandleDict)
                if (inFileHandleDict and oldest_time > os.stat(fp).st_atime):
                    oldest_time = os.stat(fp).st_atime
                    oldest_file = fp
                total_size += os.path.getsize(fp)
        return (oldest_file, total_size)


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


class IOProxy(object):
    """ 
    This is an abstract class used to provide functionality to do IO for the
    cache. The methods which raise the NotImplementedError exception must be
    implemented by the end user.
    """


    def __init__(self, randomRead = False, randomWrite = False):
        """ 
        The initializer indicates if the IO object supports random read or
        random write.
        """

        self.randomRead = randomRead
        self.randomWrite = randomWrite
        self.lock = threading.RLock()
        self.condition = threading.Condition(self.lock)
        self.cacheFile = None
        self.cache = None
        self.currentByte = None


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


    def writeToBacking(self, md5, size, mtime):
        """ 
        Write a file in the cache to the remote file. 
        
        The implementation of this method must use the readFromCache method 
        to get data from the cache file.
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

    def verifyMetaData(self, md5sum):
	"""
	Verify the md5sum of the file in vospace has not changed.
	"""
        raise NotImplementedError("IOProxy.verifyMetaData")



    def writeToCache(self, buffer, offset):
        """ 
        Function to write data to a the cache. This method should only be used
        by the implementation of the readFromBacking method.
        """

        if self.cacheFileStream.tell() != offset:
            # Only allow seeks to block boundaries
            if (offset % self.cache.IO_BLOCK_SIZE != 0):
                raise CacheError("Only seeks to block boundaries are "
                    "permitted when writing to cache.")
            self.cacheFileStream.seek(offset)

        # Write the data to the data file.
        nextByte = 0
        byteBuffer = bytes(buffer)
        while nextByte < len(byteBuffer):
            nextByte = nextByte + self.cacheFileStream.write(byteBuffer[nextByte:])

        # Set the mask bits corresponding to any completely read blocks.
        firstBlock, numBlocks  = self.blockInfo(offset, len(buffer))
        if numBlocks > 0:
            with self.condition:
                self.condition.acquire()
                #TODO self.metaData.setBlocksRead(firstBlock, numBLocks)
                self.condition.notify()


        self.currentByte = nextByte

        return nextByte 

    def blockInfo(self, offset, size):
        """ Determine the blocks completed when "size" bytes starting at 
        "offset" are written.
        """

        firstBlock = offset / self.cache.IO_BLOCK_SIZE
        numBlocks = ((offset + size) / self.cache.IO_BLOCK_SIZE) - firstBlock
        return firstBlock, numBlocks


    def setCacheFile(self, cacheFile):
        self.cacheFile = cacheFile
        self.cache = cacheFile.cache

    def readFromCache(self, offset, size):
        if (self.cacheFileStream.tell() != offset):
            self.cacheFileStream.seek(offset)

        return self.cacheFileStream.read(size)


class FileHandle(object):
    def __init__(self, path, cache, ioObject):
        self.path = path
        self.cache = cache
	if not os.path.isabs(path):
	    raise ValueError("Path '%s' is not an absolute path." % path )
	#TODO don't overwrite meta data file with an out of date file
	self.cacheDataFile = cache.dataDir + path
	self.cacheMetaDataFile = cache.metaDataDir + path
	self.metaData = None
        self.ioObject = ioObject
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
	self.fileCondition = threading.Condition(self.fileLock)
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

    def release(self):
        """Close the file.

	The last release of a modified file may respond by raising a CacheRetry
	exception if the flush to the backing store takes longer than the
	timeout specified for the cache. The caller should respond to this by
	repeating the call to release after satisfying the FUSE timeout
	requirement.
	"""

        logging.debug("releasing node %s: refcount %d: modified %s" % 
		(self.path, self.refCount, self.fileModified))
        with self.fileLock:
	    # If flushing is not already in progress, wait for it to finish.
            if (self.flushThread is None and self.refCount == 1 and 
		    self.fileModified and not self.obsolete):
		if self.metaData is not None:
		    md5 = self.metaData.md5sum
		else:
		    md5 = None

                self.flushThread = threading.Thread(target=self.flushNode,
			args=[])
		self.flushThread.start()
	    startTime = time.time()
	    now = time.time()
	    while self.flushThread != None and (self.cache.timeout is None or 
		    (now - startTime) < self.cache.timeout):
		# Wait for the flush to complete but no more than the total time
		if self.cache.timeout is None:
		    self.fileCondition.wait()
		else:
		    self.fileCondition.wait(self.cache.timeout - 
			    (now - startTime))
		now = time.time()

	    if self.flushThread != None:
		raise CacheRetry

	    # Look for write failures.
	    if self.flushException is not None:
		raise self.flushException[0], self.flushException[1], \
			self.flushException[2]

        with nested(self.cache.cacheLock, self.fileLock):
	    self.refCount -= 1
            if self.refCount == 0:
		os.close(self.ioObject.cacheFileDescriptor)
		self.ioObject.cacheFileDescriptor = None
		# The entry in fileHandleDict may have been removed by unlink,
		# so don't panic
		try:
		    del self.cache.fileHandleDict[self.path]
		except KeyError:
		    pass

        self.cache.checkCacheSpace()

        return

    def getmd5(self):
	"""Get the current md5sum of the file."""
	md5 = hashlib.md5()
	size = 0

	#TODO Don't open by file name
	with os.fdopen(os.dup(self.ioObject.cacheFileDescriptor),'r') as r:
	    while True:
		buff=r.read(Cache.IO_BLOCK_SIZE)
		if len(buff) == 0:
		    break
		md5.update(buff)
		size += len(buff)

	print "size", size
	info = os.fstat(self.ioObject.cacheFileDescriptor)
	return md5.hexdigest(), size, info.st_mtime

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
		md5, size, mtime = self.getmd5()

		# Write the file to vospace.

		self.ioObject.writeToBacking(md5, size, mtime)

		# Update the meta data md5
		blocks,blocks = self.ioObject.blockInfo(size, size)
		self.metaData = CacheMetaData(self.cacheMetaDataFile, blocks, 
			md5)

		#TODO self.metaData.setMD5(md5)

		
	    except Exception as e:
		self.flushException = sys.exc_info()
	    finally:
		self.flushThread = None
		# Wake up any threads waiting for the flush to finish
		with self.fileCondition:
		    self.fileCondition.notify_all()

	return


    def write(self, data, size, offset):
        #TODO write data
        raise NotImplementedError("TODO")
