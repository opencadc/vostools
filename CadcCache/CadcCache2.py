"""
A sparse file caching library
"""

import threading
import logging
from os import O_RDONLY, O_WRONLY, O_RDWR, O_APPEND
from contextlib import nested

class Cache(object):
    """ 
    This class manages the cache for the vofs. 
    """
    IO_BLOCK_SIZE = 2**14
    def __init__(self, cacheDir, maxCacheSize, readOnly = False):
	"""Initialize the Cache Object

	Parameters:
	-----------
	cacheDir : string - The directory for the cache.
	maxCacheSize : int - The maximum cache size in megabytes
	readOnly : boolean - Is the cached data read-only
	"""

        self.cacheDir = cacheDir
        self.dataDir = cacheDir + "/data/"
        self.metaDataDir = cacheDir + "/metaData/"
        self.maxCacheSize = maxCacheSize
	self.readOnly = readOnly
	self.fileHandleDict = {}
	# When cache locks and file locks need to be held at the same time,
	# always acquire the file lock first.
	self.cacheLock = threading.RLock()


    def __enter__(self):
	"""This method allows Cache object to be used with the "with" construct.
	"""
        return self


    def __exit__(self, type, value, traceback):
	"""This method allows Cache object to be used with the "with" construct.
	"""
        pass

    def open(self, path, flags, ioObject):
        """Open file with the desired modes

        Here we return a handle to the cached version of the file
        which is updated if older and out of synce with VOSpace.
        """

        logging.debug("Opening %s with flags %s" % (path, self.flag2mode(flags)))

        # see if this node exists
        try:
            node = ioObject.getMD5()
        except OSError as e:
            if e.errno == ENOENT:
                if flags == O_RDONLY:
                    # file openned for readonly doesn't exist
                    FuseOSError(ENOENT)
            else:
                raise FuseOSError(e.errno)
            
        # check if this file is locked, if locked on vospace then don't open
        locked=False

        if ioObject.isLocked():
             logging.info("%s is locked." % path)
             locked = True

	
	cacheFile = self.getFileHandle(path, ioObject)
        ioObject.setCacheFile(cacheFile)

	self.checkCacheSpace()

        return cacheFile


    def getFileHandle(self, path, ioObject):
	"""Find an existing file handle, or create one if necessary.
	"""
	with self.cacheLock:
	    try:
		newFileHandle = self.fileHandleDict[path]
	    except KeyError:
		newFileHandle = FileHandle(path, self, ioObject)
		self.fileHandleDict[path] = newFileHandle
	    with newFileHandle.fileLock:
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
	# TODO
	pass


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
	self.metaData = None
	self.currentByte = None


    def getMD5(self):
	""" 
	Return the MD5sum of the remote file.
	""" 
	raise NotImplementedError("IOProxy.getMD5")


    def getSize(self):
	""" 
	Return the MD5sum of the remote file.
	""" 
	raise NotImplementedError("IOProxy.getMD5")


    def delNode(self, force = False):
	""" 
	Delete the remote file.
	"""
	raise NotImplementedError("IOProxy.delNode")


    def isLocked(self):
	""" 
	Determine if the remote file is locked.
	"""
	raise NotImplementedError("IOProxy.isLocked")


    def writeToBacking(self):
	""" 
	Write a file in the cache to the remote file. CacheFileDescriptor is
	a file descriptor which can be used to read data from the cache file.
	
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
	self.ioObject = ioObject
	# When cache locks and file locks need to be held at the same time,
	# always acquire the file lock first.
	self.fileLock = threading.RLock()
	self.refCount = 0

    def release(self):
	"""Close the file"""

	with self.fileLock:
	    self.refCount -= 1


	    # TODO save file if necessary
	    pass

	with nested(self.cache.cacheLock, self.fileLock):
	    if self.refCount == 0:
		del self.cache.fileHandleDict[self.path]

	self.cache.checkCacheSpace()

	return

	## get the MODE of the original open, if 'w/a/w+/a+' we should 
	## write to VOSpace we do that here before closing the filehandle 
	## since we delete the reference to the file handle at this point.
	mode = os.O_RDONLY
	if self.cache.fh.get(self.fh,None) is not None:
	    mode = self.cache.fh[self.fh]['flags']

	## remove references to this file handle.
	writing_wait = 0
	while self.cache.cache[self.path]['writing'] :
	    time.sleep(self.cache.READ_SLEEP) 
	    writing_wait += self.cache.READ_SLEEP

	## On close, if this was a WRITE opperation then update VOSpace
	## unless the VOSpace is actually newer than this cache file.
	### copy the staging file to VOSpace if needed
	logging.debug("node %s currently open with mode %s, releasing" % 
		( self.path, mode))
	if mode & ( os.O_RDWR | os.O_WRONLY | os.O_APPEND | os.O_CREAT ):
	    ## check if the cache MD5 is up-to-date
	    if not self.ioObject.verifyMetaData( self.path,
		    self.metaData( self.cache.data_dir + self.path) ):
		logging.debug("PUSHING contents of %s to VOSpace location %s " % 
			(self.cache.cache[self.path]['fname'],self.path))
		## replace VOSpace copy with cache version.
		self.fsync(self.path,False,self.fh)

		if not self.cache.cache[self.path]['writing']:
		    self.cache.cache[self.path]['writing']=True
		    try:
			self.flushnode(self.path,self.fh)
		    except Exception as e:
			logging.error("ERROR trying to flush node: %s" % 
				(str(e)))
			raise vos.fuse.FuseOSError(EAGAIN)
		    finally:
			self.cache.cache[self.path]['writing']=False

	##  now close the fh
	try:
	    os.close(self.fh)
	except Exception as e:
	    ex = FuseOSError(getattr(e,'errno',EIO))
	    ex.strerror = getattr(ex,'strerror','failed while closing %s' %
		    (self.path))
	    logging.debug(str(e))
	    raise ex
	finally:
	    self.cache.fh.pop(self.fh,None)
	    self.ioObject.delNode(self.path)
	    self.cache.cache.pop(self.path,None)
	    self.fh = None
	    self.path = None
	## clear up the cache
	self.cache.clear_cache()
	return 
