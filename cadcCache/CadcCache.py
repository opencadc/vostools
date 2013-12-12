"""A sparse file caching library
"""

import os
import io
import stat
import time
import logging
import vos.fuse
import sqlite3
from vos.fuse import FUSE, Operations, FuseOSError, LoggingMixIn
from threading import RLock
from os import O_RDONLY, O_WRONLY, O_RDWR, O_APPEND
from errno import EACCES, EIO, ENOENT, EISDIR, ENOTDIR, ENOTEMPTY, EPERM, \
        EEXIST, ENODATA, ECONNREFUSED, EAGAIN, ENOTCONN
import subprocess
from ctypes import create_string_buffer

READBUF=2**14

logger=logging.getLogger('CadcCache')
logger.addHandler(logging.NullHandler())



def flag2mode(flags):
    md = {O_RDONLY: 'r', O_WRONLY: 'w', O_RDWR: 'w+'}
    m = md[flags & (O_RDONLY | O_WRONLY | O_RDWR)]

    if flags | O_APPEND:
        m = m.replace('w', 'a', 1)

    return m

class CadcCache:
    READ_SLEEP = 1
    class CacheError(Exception):
        def __init__(self, value):
            self.value = value
        def __str__(self):
            return repr(self.value)


    class IOProxy:
        def __init__( self, randomRead = False, randomWrite = False ):
            self.randomRead = randomRead
            self.randomWrite = randomWrite
            self.fileStream = None

        def getMD5( self, path ):
            raise NotImplementedError( "IOProxy.getMD5" )

        def delNode( self, path, force = False ):
            raise NotImplementedError( "IOProxy.delNode" )

        def isLocked( self, path ):
            raise NotImplementedError( "IOProxy.isLocked" )

        def verifyMetaData( self, path, metaData ):
            raise NotImplementedError( "IOProxy.verifyMetaData" )

        def writeToBacking( self, cacheFileDescriptor ):
            raise NotImplementedError( "IOProxy.writeToBacking" )

        def readFromBacking( self, size = None, offset = None ):
            raise NotImplementedError( "IOProxy.readFromBacking" )

        def writeCache(self, buffer, offset):
            print "zero"
            print cacheFileStream
            print cacheFileStream.tell()
            print "zero"
            if (cacheFileStream.tell() != offset):
                pass
                #cacheFileStream.seek(offset )
            print "one"

            cacheFileStream.write( buffer )
            print "two"



    class FileMetaData:
        def __init__( self, md5sum, m_time ):
            self.md5sum = md5sum
            self.m_time = m_time

    class CacheFh:
        def __init__( self, fh, path, cache, ioObject ):
            self.fh = fh
            self.path = path
            self.cache = cache
            self.ioObject = ioObject
            self.fileLock = RLock()
            self.fs = io.open( fh, mode='r+b')

        def release(self):
            """Close the file, but if this was a holding spot for writes, 
            then write the file to the node"""
            import os

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
                #self.cache.pop(path,None)
            ## clear up the cache
            self.cache.clear_cache()
            return 

        def flushnode(self,path,fh):
            """Flush the data associated with this fh to the Node at path
            in VOSpace.
            
            Flushing the VOSpace object involves pushing the entire file
            back over the network. This should only be done when really
            needed as the network connection can be slow."""
            mode = flag2mode(self.cache.fh[fh]['flags'])
            if not ('w' in mode or 'a' in mode ):
                logging.debug("file was not opened for writing")
                return 

            if self.cache.readonly:
                logging.debug("File system is readonly... no flushnode allowed")
                return 

            inputfd = os.dup( fh )
            os.lseek( inputfd, 0, os.SEEK_SET )
            self.ioObject.writeToBacking( inputfd )
            os.close( inputfd )


        def fsync(self,path,datasync,fh):
            if self.cache.readonly:
                logging.debug("File system is readonly, no sync allowed")
                return 
            mode=''
            if self.cache.fh.get(fh,None) is not None:
                locked = self.cache.fh[fh].get('locked',False)
                if locked:
                    logging.info("%s is locked, no sync allowed" % path)
                    raise FuseOSError(EPERM)
                mode = flag2mode(self.cache.fh[fh]['flags'])
            if 'w' in mode or 'a' in mode:
                try:
                    os.fsync(fh)
                except Exception as e:
                    logging.critical("Failed to sync fh %d? %s" % ( fh, 
                            str( e ) ) )
                    pass

        def is_cached(self):
            return self.cache.cache.get(self.path,
                    {'cached': False}).get('cached', False)



        def metaData( self, fname ):
            if not os.access(fname,os.R_OK):
                raise FuseOSError(EACCES)
            with self.cache.cacheLock:
                sqlConn = sqlite3.connect(self.cache.cache_db)
                sqlConn.row_factory = sqlite3.Row
                cursor= sqlConn.cursor()
                cursor.execute("SELECT * FROM md5_cache WHERE fname = ?", 
                        (fname,))
                md5sum=cursor.fetchone()
                cursor.close()
                sqlConn.close()
                if md5sum is None or md5sum['st_mtime'] < os.stat(fname).st_mtime:
                    self.update_md5_db(fname)
                    return self.metaData(fname)

            return CadcCache.FileMetaData( md5sum['md5'], md5sum['st_mtime'] )


        def update_md5_db(self,fname):
            """Update a record in the cache MD5 database"""
            import hashlib
            with self.cache.cacheLock:
                md5 = hashlib.md5()  
                r=open(fname,'r')
                while True:
                    buf=r.read(READBUF)
                    if len(buf)==0:
                        break
                    md5.update(buf)
                r.close()
                st_mtime_end = os.stat(fname).st_mtime
                ## UPDATE the MD5 database
                sqlConn = sqlite3.connect(self.cache.cache_db)
                sqlConn.row_factory = sqlite3.Row
                cursor=sqlConn.cursor()
                sqlConn.commit()
                cursor.execute("DELETE FROM md5_cache WHERE fname = ?", 
                        (fname,))
                sqlConn.commit()
                if md5 is not None:
                    cursor.execute(
                            "INSERT INTO md5_cache (fname, md5, st_mtime) " + 
                            "VALUES ( ?, ?, ?)", 
                            (fname, md5.hexdigest(), st_mtime_end))
                sqlConn.commit()
                cursor.close()
                sqlConn.close()
                return 

        def read( self, size, offset ):
            """Read data from the cache. """

            ## raise an error if cache structure doens't exist
            if self.path not in self.cache.cache:
                logging.error("Tried to read but cached wasn't initialied for %s" 
                        % ( self.path))
                raise FuseOSError(EIO)

            with self.cache.cacheLock:
                if self.cache.cache[self.path]['writing'] :
                    ## the cache is currently being written to... so wait for 
                    ## bytes to arrive
                    logging.debug("checking if cache writing has gotten far enought")
                    st_size = os.stat(self.cache.cache[self.path]['fname']).st_size
                    logging.debug( "loop until %s + %s < %s or read from "
                            "vospace reaches EOF." % 
                            ( str(size), str(offset), str(st_size)))
                    while ( st_size < offset + size and 
                            self.cache.cache[self.path]['writing'] ):
                        logging.debug("sleeping for %d ..." % (READ_SLEEP))
                        time.sleep(READ_SLEEP)
                        logging.debug("wake up ")
                        st_size = os.stat(self.cache.cache[self.path]['fname']).st_size
                    logging.debug("Done waiting. %s + %s < %s or reached EOF" % 
                            ( str(size), str(offset), str(st_size)))
                    os.lseek(fh,offset,os.SEEK_SET)
                    logging.debug("sending back %s for %s" % ( str(fh), str(size)))
                    buf = create_string_buffer(size)
                    retsize = libc.read(fh,buf,size)
                    return buf


                logging.debug("%s is in data cache: %s " % 
                        ( self.path, self.is_cached()))
                    
                vosMD5 = self.ioObject.getMD5(self.path)
                cacheMD5 = self.get_md5_db(self.cache.cache[self.path]['fname'])
                print "md5sums: %s : %s" % ( vosMD5, cacheMD5['md5'] )
                if cacheMD5['md5'] == vosMD5 :
                    ## return a libc.read buffer
                    fs = self.fs
                    fs.seek(offset,os.SEEK_SET)
                    logging.debug("returning bytes %s starting at  %s" % 
                            ( offset, fs.tell()))
                    buf = fs.read(size)
                    retsize = len(buf)
                    return create_string_buffer( buf[:retsize], retsize )

                ## get a copy from VOSpace if the version we have is not 
                ## current or cached.
                print "writting: " + str( self.cache.cache[self.path]['writing']
                )
                if not self.cache.cache[self.path]['writing']:
                    self.cache.cache[self.path]['writing'] = True
                    thread.start_new_thread( self.load_into_cache, (self.path, int(fh)))

            return self.read( self.path, size, offset )

        def get_md5_db(self,fname):
            """Get the MD5 for this fname from the SQL cache"""
            sqlConn=sqlite3.connect(self.cache.cache_db)
            sqlConn.row_factory = sqlite3.Row
            cursor= sqlConn.cursor()
            cursor.execute("SELECT * FROM md5_cache WHERE fname = ?", (fname,))
            md5Row=cursor.fetchone()
            cursor.close()
            sqlConn.close()
            if md5Row is None or md5Row['st_mtime'] < os.stat(fname).st_mtime:
                self.update_md5_db(fname)
                return self.get_md5_db(fname)
            return md5Row



        def load_into_cache(self, path):
            """Load path from VOSpace and store into fh"""
            logging.debug("self: %s" % ( str(self.fh)))
            print "file handle: " + str( self.fh )
            os.fsync(self.fh)
            os.ftruncate(self.fh,0)
            self.ioObject.cacheFileStream = io.open(self.fh,'w')
            try:
                logging.debug("writing to %s" % ( self.cache.cache[path]['fname']))
                logging.debug("reading from %d" % ( self.fh ) )

                self.ioObject.readFromBacking()
                print "file handle: " + str( self.fh )
                os.fsync(self.fh)
                vosMD5 = self.ioObject.getMD5(path)
                cacheMD5 = self.get_md5_db(self.cache.cache[path]['fname'])
                if vosMD5 != cacheMD5['md5']:
                    logging.debug("vosMD5: %s cacheMD5: %s" % 
                            ( vosMD5, cacheMD5['md5']))
                    raise FuseOSError(EIO)
                self.cache.cache[path]['cached'] = True
                logging.debug("Finished caching file %s" %  ( str(self.fh)))
            except OSError as e:
                logging.error("ERROR: %s" % (str(e)))
                ex = FuseOSError(e.errno)
                ex.strerror = getattr(e,'strerror','failed while reading from %s' %
                        (path))
                raise ex
            finally:
                self.ioObject.cacheFileStream.close()
                del(self.ioObject.cacheFileStream)
                self.cache.cache[path]['writing'] = False
            return 


    def __init__( self, cache_dir, max_cache_size, readonly = False ):
        """Setup the cache"""

        logging.debug( "created cache at %s with size %d" % 
                ( cache_dir, max_cache_size) )
        self.cache = {}
        self.fh = {'None':False}
        self.readonly = readonly
        self.cacheLock = RLock()
        self.cache_dir = cache_dir
        self.data_dir = cache_dir + "/data"
        self.max_cache_size = max_cache_size

        if os.path.exists( self.cache_dir ):
            if not os.path.isdir( self.cache_dir ):
                raise CadcCache.CacheError( "Path " + self.cache_dir + \
                        " is not a directory." )
            if not os.access( self.cache_dir, os.R_OK | os.W_OK | os.X_OK ):
                raise CadcCache.CacheError( "Existing path " + self.cache_dir + \
                        " does not have on of read, write or execute permission." )

        if os.path.exists( self.data_dir ):
            if not os.path.isdir( self.data_dir ):
                raise CadcCache.CacheError( "Path " + self.data_dir + \
                        " is not a directory." )
            if not os.access( self.data_dir, os.R_OK | os.W_OK | os.X_OK ):
                raise CadcCache.CacheError( "Existing path " + self.data_dir + \
                        " does not have on of read, write or execute permission." )
        else:
            os.makedirs( self.data_dir, stat.S_IRWXU )


        self.cache_db = os.path.abspath(os.path.normpath(os.path.join(
                cache_dir,"#vofs_cache.db#")))

        ## initialize the md5Cache db
        sqlConn = sqlite3.connect(self.cache_db)
        sqlConn.execute("create table if not exists md5_cache ( fname text, md5 text, st_mtime int)")
        sqlConn.commit()
        sqlConn.close()


        self.total_size = self.determine_cache_size()

    def __enter__( self ):
        return self


    def __exit__( self, type, value, traceback ):
        pass


    def determine_cache_size(self):
        """Determine how much disk space is being used by the local cache"""
        start_path = self.data_dir
        total_size = 0

        self.atimes={}
        oldest_time=time.time()
        self.oldest_file = None
        for dirpath, dirnames, filenames in os.walk(start_path):
            for f in filenames:
                fp = os.path.join(dirpath, f)
                if oldest_time > os.stat(fp).st_atime and fp not in self.list_cache():
                    oldest_time = os.stat(fp).st_atime
                    self.oldest_file = fp
                total_size += os.path.getsize(fp)
        return total_size


    def list_cache(self):
        """Get a list of the cache files that are in active use"""
        activeCache=[]
        for path in self.cache:
            activeCache.append(self.cache[path]['fname'])
        return activeCache

    def clear_cache(self):
        """Clear the oldest files until cache_size < cache_limit"""
        while ( self.determine_cache_size() > self.max_cache_size and 
                self.oldest_file != None ) :
            logging.debug("Removing file %s from the local cache" % ( self.oldest_file))
            os.unlink(self.oldest_file)
            self.oldest_file=None



    def open(self, path, flags, ioObject ):
        """Open file with the desired modes

        Here we return a handle to the cached version of the file
        which is updated if older and out of synce with VOSpace.

        """

        logging.debug("Opening %s with flags %s" % ( path, flag2mode(flags)))

        ## see if this node exists
        try:
            node = ioObject.getMD5(path)
        except OSError as e:
            if e.errno == ENOENT:
                if flags == O_RDONLY:
                    # file openned for readonly doesn't exist
                    FuseOSError(ENOENT)
            else:
                raise FuseOSError(e.errno)
            
        ### check if this file is locked, if locked on vospace then don't open
        locked=False

        if ioObject.isLocked( path ):
             logging.info("%s is locked." % path)
             locked = True

        ## setup the data cache structure, which returns a 
        self.add_to_cache(path)

        ## open the cache file, return that handle to fuse layer.
        fh = os.open(self.cache[path]['fname'], os.O_RDWR )

        ## also open a direct io path to this handle, use that for file write 
        ## operations 
        fs = io.open(fh,mode='r+b')
        os.lseek(fh,0,os.SEEK_SET)

        ## the fh dictionary provides lookups to some critical info... 
        self.fh[fh]={'flags': flags, 'fs': fs, 'locked': locked}

        ioObject.cacheFh = CadcCache.CacheFh( fh, path, self, ioObject  )
        return ioObject.cacheFh


    def add_to_cache(self,path):
        """Add path to the cache reference.

        path: the vofs location of the file (str)."""

        fname = os.path.normpath(self.data_dir + path)
        with self.cacheLock:
            if path not in self.cache:
                self.cache[path] = {'fname': fname,
                                    'cached': False,
                                    'writing': False}

            ## build the path to this cache, if needed
            ## doing as an error fall through is more efficient
            try:
                dir_path = os.path.dirname(fname)
                os.makedirs(dir_path)
            except OSError as exc: 
                if exc.errno == EEXIST and os.path.isdir(dir_path):
                    pass
                else: 
                    logging.error("Failed to create cache directory: %s" %
                            ( dir_path))
                    logging.error(str(exc))
                    raise FuseOSError(exc.errno)

            ## Create the cache file, if it doesn't already exist
            ## or open for RDWR if the file already exists
            try:
                fh=None
                fh=os.open(fname,os.O_CREAT | os.O_RDWR)
            except OSError as e:
                e=FuseOSError(e.errno)
                e.strerror=getattr(e,'strerror','failed on open of %s' % ( path))
                e.message="Not able to write cache (Permission denied: %s)" % ( fname) 
                raise e
            finally:
                if fh != None:
                    os.close(fh)

        return
