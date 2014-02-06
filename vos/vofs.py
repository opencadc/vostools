#!/usr/bin/env python2.7

"""A FUSE based filesystem view of VOSpace."""

from sys import argv, exit, platform
import time
import vos
import fuse
from fuse import FUSE, Operations, FuseOSError, LoggingMixIn
import tempfile
from threading import Lock
from errno import EACCES, EIO, ENOENT, EISDIR, ENOTDIR, ENOTEMPTY, EPERM, EEXIST, ENODATA, ECONNREFUSED, EAGAIN, ENOTCONN
ENOATTR=93
import os
import io
import vos
from os import O_RDONLY, O_WRONLY, O_RDWR, O_APPEND
import logging
import sqlite3
from __version__ import version
from ctypes import cdll
from ctypes.util import find_library
import urlparse 
from CadcCache import Cache, CacheRetry, CacheAborted, IOProxy

def flag2mode(flags):
    md = {O_RDONLY: 'r', O_WRONLY: 'w', O_RDWR: 'w+'}
    m = md[flags & (O_RDONLY | O_WRONLY | O_RDWR)]

    if flags | O_APPEND:
        m = m.replace('w', 'a', 1)

    return m

class MyIOProxy(IOProxy):
    def __init__(self, vofs, node):
        self.vofs = vofs
        self.node = node

    def writeToBacking(self):
        """ 
        Write a file in the cache to the remote file. 
        """
        logging.debug("PUSHING contents of %s to VOSpace location %s " % 
                (self.cacheFile.cacheDataFile, self.cacheFile.path))

        self.vofs.client.copy(self.cacheFile.cacheDataFile, 
                self.vofs.getNode(self.cacheFile.path).uri)
        return self.vofs.getNode(self.cacheFile.path).props.get("MD5")

    def readFromBacking(self, size = None, offset = 0, 
            blockSize = Cache.IO_BLOCK_SIZE):
        """ 
        Read from VOSpace into cache
        """

        # TODO read a range
        if size is not None or offset != 0:
            range = (size, offset)
        else:
            range = None

        r = self.vofs.client.open(self.cacheFile.path, mode=os.O_RDONLY, 
                view="data", size=size, range=range)
        try:
            logging.debug("reading from %s" % ( str(r)))
            while True:
                buff = r.read(blockSize)
                if not buff:
                    break
                try:
                    self.writeToCache(buff, offset)
                except CacheAborted:
                    # The transfer was aborted.
                    break
                offset += len(buff)
        finally:
            r.close()
            
        logging.debug("Wrote: %d bytes to cache for %s" % (offset,
                self.cacheFile.path))
 
    def getMD5(self):
        if self.node is None:
            return None
        else:
            return self.node.props.get('MD5')
    
    def getSize(self):
        if self.node is None:
            return None
        else:
            return self.node.props.get('length')


class HandleWrapper(object):
    """ Wrapper for cache file handlers. Each wrapper represents an open request.
    Multiple wrappers of the same file share the same cache file handler. """
    def __init__(self, cacheFileHandle, readOnly = False):
        self.cacheFileHandle = cacheFileHandle
        self.readOnly = readOnly


class VOFS(LoggingMixIn, Operations):
#class VOFS(Operations):
    """The VOFS filesystem opperations class.  Requires the vos (VOSpace) python package.

    To use this you will also need a VOSpace account from the CADC.
    """
    ### VOSpace doesn't support these so I've disabled these operations.
    chown = None
    link = None
    mknode = None
    rmdir = None
    symlink = None
    getxattr = None
    listxattr = None
    removexattr = None
    setxattr = None


    def __init__(self, root, cache_dir, options, conn=None, 
            cache_limit=1024, cache_nodes=False):
        """Initialize the VOFS.  
        
        cache_limit is in MB.
        The style here is to use dictionaries to contain information
        about the Node.  The full VOSpace path is used as the Key for
        most of these dictionaries."""

        self.cache_nodes = cache_nodes

        # This dictionary contains the Node data about the VOSpace node in question
        self.node = {}
        # Standard attribtutes of the Node
        # Where in the file system this Node is currently located
        self.loading_dir={}

        # Command line options.
        self.opt = options

        # What is the 'root' of the VOSpace? (eg vos:MyVOSpace) 
        self.root = root

        # VOSpace is a bit slow so we do some cahcing.
        self.cache = Cache(cache_dir, cache_limit, False, 60)
        
        ## All communication with the VOSpace goes through this client connection.
        try:
           self.client = vos.Client(rootNode=root,conn=conn)
        except Exception as e:
           e=FuseOSError(e.errno)
           e.filename=root
           e.strerror=getattr(e,'strerror','failed while making mount')
           raise e

        self.rwlock = Lock()


    def __call__(self, op, path, *args):
        return super(VOFS, self).__call__(op, path, *args)

    def __del__(self):
        self.node=None

    def delNode(self,path,force=False):
        """Delete the references associated with this Node"""
        if not self.cache_nodes or force :
            self.node.pop(path,None)
        add_to_cache

    def access(self, path, mode):
        """Check if path is accessible.  

        Only checks read access, mode is currently ignored"""
        logging.debug("Checking if -->%s<-- is accessible" %(path))
        try:
            self.getNode(path)
        except:
            return -1
        return 0


    def chmod(self, path, mode):
        """Set the read/write groups on the VOSpace node based on chmod style modes.

        This function is a bit funny as the VOSpace spec sets the name
        of the read and write groups instead of having mode setting as
        a separate action.  A chmod that adds group permission thus
        becomes a chgrp action.  

        Here I use the logic that the new group will be inherited from
        the container group information.
        """
        logging.debug("Changing mode for %s to %d" % ( path, mode))

        node = self.getNode(path)
        parent = self.getNode(os.path.dirname(path))

        if node.groupread == "NONE":
            node.groupread=parent.groupread
        if node.groupwrite == "NONE":
            node.groupwrite=parent.groupwrite
        # The 'node' object returned by getNode has a chmod method
        # that we now call to set the mod, since we set the group(s)
        # above.  NOTE: If the parrent doesn't have group then NONE is
        # passed up and the groupwrite and groupread will be set tovos:
        # the string NONE.
        if node.chmod(mode):
            # Now set the time of change/modification on the path...
            # TODO: This has to be broken. Attributes may come from Cache if the
            # file is modified. Even if they don't come from the cache, 
            # the getAttr method calls getNode with force=True, which returns 
            # a different Node object than "node". The st_ctime value will be 
            # updated on the newly replaced node in self.node[path] but
            # not in node, then node is pushed to vospace without the st_time
            # change, and then it is pulled back, overwriting the change that
            # was made in self.node[path]. Plus modifying the mtime of the file
            # is not conventional Unix behaviour. The mtime of the parent
            # directory would be changed.
            self.getattr(path)['st_ctime']=time.time()
            ## if node.chmod returns false then no changes were made.
            try:
                self.client.update(node)
                self.getNode(path, force=True)
            except Exception as e:
                logging.debug(str(e))
                logging.debug(type(e))
                e=FuseOSError(getattr(e,'errno',EIO))
                e.filename=path
                e.strerror=getattr(e,'strerror','failed to chmod on %s' %(path))
                raise e

        
    def create(self, path, flags):
        """Create a node. Currently ignores the ownership mode"""
        import re,os

        logging.debug("Creating a node: %s with flags %s" % (path, str(flags)))

        # Create is handle by the client. 
        # This should fail if the basepath doesn't exist
        try: 
            self.client.open(path,os.O_CREAT).close()
            
            node = self.getNode(path)
            parent=  self.getNode(os.path.dirname(path))

            # Force inheritance of group settings. 
            node.groupread = parent.groupread
            node.groupwrite = parent.groupwrite
            if node.chmod(flags):
                ## chmod returns True if the mode changed but doesn't do update.
                self.client.update(node)
                node = self.getNode(path,force=True)
                
        except Exception as e:
            logging.error(str(e))
            logging.error("Error trying to create Node %s" %(path))
            f=FuseOSError(getattr(e,'errno',EIO))
            f.strerror=getattr(e,'strerror','failed to create %s' %(path))
            raise f

        ## now we can just open the file in the usual way and return the handle
        return self.open(path,os.O_WRONLY)

  
    def fsync(self,path,datasync,fh):
        if self.opt.readonly:
            logging.debug("File system is readonly, no sync allowed")
            return 

        if fh.readOnly:
            raise FuseOSError(EPERM)

        fh.cacheFileHandle.fsync()


    def getNode(self,path,force=False,limit=0):
        """Use the client and pull the node from VOSpace.  
        
        Currently force=False is the default... so we never check
        VOSpace to see if the node metadata is different from what we
        have.  This doesn't keep the node metadata current but is
        faster if VOSpace is slow.
        """

      
        logging.debug("force? -> %s path -> %s" % ( force, path))
        ### force if this is a container we've not looked in before
        if path in self.node and not force:
            node = self.node[path]
            if node.isdir() and not limit==0 and not len(node.getNodeList()) > 0 :
               force = True
            if not force:
               logging.debug("Sending back cached metadata for %s" % ( path))
               return node
            

        ## Pull the node meta data from VOSpace.
        try:
            logging.debug("requesting node %s from VOSpace" % ( path))
            self.node[path]=self.client.getNode(path,force=True,limit=limit)
        except Exception as e:
            logging.error(str(e))
            logging.error(type(e))
            ex=FuseOSError(getattr(e,'errno',ENOENT))
            ex.filename=path
            ex.strerror=getattr(e,'strerror','Error getting %s' % (path))
            raise ex

        if self.node[path].isdir() and self.node[path]._nodeList is not None:
            for node in self.node[path]._nodeList:
               subPath=os.path.join(path,node.name)
               self.node[subPath]=node
        return self.node[path]

    def getattr(self, path, fh=None):
        """Build some attributes for this file, we have to make-up some stuff"""

        logging.debug("getting attributes of %s" % ( path))
        # Try to get the attributes from the cache first. This will only return
        # a result if the files has been modified and not flushed to vospace.
        cacheFileAttrs = self.cache.getAttr(path)
        if cacheFileAttrs is not None:
            return cacheFileAttrs

        return self.getNode(path, limit=0, force=True).attr


    def mkdir(self, path, mode):
        """Create a container node in the VOSpace at the correct location.

        set the mode after creation. """
        #try:
        if 1==1:
            parentNode = self.getNode(os.path.dirname(path), force=False, limit=1)
            if parentNode and parentNode.props.get('islocked', False):
                logging.info("Parent node of %s is locked." % path)
                raise FuseOSError(EPERM)
            self.client.mkdir(path)
            self.chmod(path,mode)
        #except Exception as e:
        #   logging.error(str(e))
        #   ex=FuseOSError(e.errno)
        #   ex.filename=path
        #   ex.strerror=e.strerror
        #   raise ex
        return

    
    def open(self, path, flags, *mode):
        """Open file with the desired modes

        Here we return a handle to the cached version of the file
        which is updated if older and out of synce with VOSpace.

        """

        logging.debug("Opening %s with flags %s" % ( path, flag2mode(flags)))
        node = None
        
        
        cacheFileAttrs = self.cache.getAttr(path)
        if cacheFileAttrs is None:
            # file in the cache not in the process of being modified. 
            # see if this node already exists in the cache; if not get info from vospace 
            try:
                node = self.getNode(path)
            except Exception as e:
                raise
                if e.errno == 404:
                    # file does not exist
                    if not flags & os.O_CREAT:
                        # file doesn't exist unless created
                        raise FuseOSError(ENOENT)
                else:
                    raise FuseOSError(e.errno)
            
        ### check if this file is locked, if locked on vospace then don't open
        locked=False

        if node and node.props.get('islocked', False):
             logging.info("%s is locked." % path)
             locked = True


        if node and not locked:
             if node.type == "vos:DataNode":
                  parentNode = self.getNode(os.path.dirname(path),force=False,limit=1)
                  if parentNode.props.get('islocked', False):
                       logging.info("%s is locked by parent node." % path)
                       locked=True
             elif node.type == "vos:LinkNode":
                 try:
                     ## sometimes targetNodes aren't internall... so then not locked
                      targetNode = self.getNode(node.target,force=False,limit=1)  
                      if targetNode.props.get('islocked', False):
                          logging.info("%s target node is locked." % path)
                          locked=True
                      else:
                          targetParentNode = self.getNode(os.path.dirname(node.target),force=False,limit=1)
                          if targetParentNode.props.get('islocked', False):
                              logging.info("%s is locked by target parent node." % path)
                              locked=True
                 except Exception as e:
                      logging.error("Got an error while checking for lock: "+str(e))
                      pass

        if locked & flags & os.O_WRONLY:
            # file is locked, cannot write
            FuseOSError(ENOENT)
        
        myProxy = MyIOProxy(self, node)
        return HandleWrapper(self.cache.open(path, node == None, myProxy), 
                flags & os.O_RDONLY)

    def read(self, path, size=0, offset=0, fh=None):
        """ Read the required bytes from the file and return a buffer containing
        the bytes.
        """

        ## Read from the requested filehandle, which was set during 'open'
        if fh is None:
            raise FuseOSError(EIO)

        try:
            return fh.cacheFileHandle.read(size, offset)
        except CacheRetry:
            raise FuseOSError(EAGAIN)

        
    def readlink(self, path):
        """Return a string representing the path to which the symbolic link points.

	path: filesystem location that is a link

	returns the file that path is a link to. 

	Currently doesn't provide correct capabilty for VOSpace FS.
        """
        return self.getNode(path).target
        

    def readdir(self, path, fh):
        """Send a list of entries in this directory"""
        logging.debug("Getting directory list for %s " % ( path))
        ## reading from VOSpace can be slow, we'll do this in a thread
        import thread, time
        if not self.loading_dir.get(path,False):
            self.loading_dir[path]=True
            thread.start_new_thread(self.load_dir,(path, ))
        thread_wait=0
        while self.loading_dir.get(path,False):
            time.sleep(READ_SLEEP)
            thread_wait += READ_SLEEP
            logging.error("Waiting ... %ds" % ( thread_wait))
            if thread_wait > DAEMON_TIMEOUT - 3:
                e = FuseOSError(EAGAIN)
                e.strerror = "Timeout waiting for directory listing"
                raise e
        return ['.','..'] + [e.name.encode('utf-8') for e in self.getNode(path,force=False,limit=None).getNodeList() ]

    def load_dir(self,path):
        """Load the dirlist from VOSpace"""
        logging.debug("Starting getNodeList thread")
        self.getNode(path,force=not self.opt.cache_nodes,limit=None).getNodeList()
        self.loading_dir[path] = False
        logging.debug("Got listing for %s" %(path))
        return 

    def release(self, fh):
        """Close the file"""
        try:
            
            if (fh.cacheFileHandle.fileModified) and (fh.path in self.node):
                #local copy modified. remove from list
                del self.node[fh.path]
            fh.cacheFileHandle.release()
        except CacheRetry, e:
            #it's taking too long. Notify FUSE
            raise FuseOSError(EAGAIN)
        except Exception, e:
            #unexpected problem
            raise
            raise FuseOSError(EIO)
        return 


    def rename(self,src,dest):
        """Rename a data node into a new container"""
        logging.debug("Original %s -> %s" % ( src,dest))
        #if not self.client.isfile(src):
        #   return -1
        #if not self.client.isdir(os.path.dirname(dest)):
        #    return -1
        try:
            logging.debug("Moving %s to %s" % ( src,dest))
            result=self.client.move(src,dest)
            logging.debug(str(result))
            if result:
                srcPath=os.path.normpath(self.cache_dir+src)
                destPath=os.path.normpath(self.cache_dir+dest)
                if os.access(srcPath,os.F_OK):
                    # only rename in cache if the source exists
                    dirs=os.path.dirname(destPath)
                    if not os.path.exists(dirs):
                        os.makedirs(dirs)
                    os.rename(srcPath,destPath)
                return 0
            return -1
        except Exception, e:
            logging.error("%s" % str(e))
            import re
            if re.search('NodeLocked', str(e)) != None:
                raise FuseOSError(EPERM)
            return -1
    

    def rmdir(self,path):
        node=self.getNode(path)
        #if not node.isdir():
        #    raise FuseOSError(ENOTDIR)
        #if len(node.getNodeList())>0:
        #    raise FuseOSError(ENOTEMPTY)
        if node and node.props.get('islocked', False):
            logging.info("%s is locked." % path)
            raise FuseOSError(EPERM)
        fname=os.path.normpath(self.cache_dir+path)
        if os.access(fname,os.F_OK):
            os.rmdir(fname)
        self.client.delete(path)
        self.delNode(path,force=True)

        
    def statfs(self, path):
        node=self.getNode(path)
        block_size=512
        bytes=2**33
        free=2**33
        
        if 'quota' in node.props:
            bytes=int(node.props.get('quota',2**33))
            used=int(node.props.get('length',2**33))
            free=bytes-used
        sfs={}
        sfs['f_bsize']=block_size
        sfs['f_frsize']=block_size
        sfs['f_blocks']=int(bytes/block_size)
        sfs['f_bfree']=int(free/block_size)
        sfs['f_bavail']=int(free/block_size)
        sfs['f_files']=len(node.getNodeList())
        sfs['f_ffree']=2*10
        sfs['f_favail']=2*10
        sfs['f_flags']=0
        sfs['f_namemax']=256
        return sfs
            
    
    def truncate(self, path, length, fh=None):
        """Perform a file truncation to length bytes"""
        logging.debug("Attempting to truncate %s (%d)" % ( path,length))


        ## do we have a fildes?  If so then don't close on truncate
        close=False
        if fh is None:
            logging.debug("don't have an open file handle, so creating one")
            close=True
            fh=self.open(path,os.O_RDWR)
	    self.add_to_cache(path)

        ## Check if we have a valid cached version of this file.
        ## but don't use standard read since we only want at most 'length' of this file
        if length > 0 :
            vosMD5  = self.getNode(path).props.get('MD5','d41d8cd98f00b204e9800998ecf8427e')
            cacheMD5 = self.get_md5_db(self.cache[path]['fname'])
            if not self.is_cached(path) and cacheMD5['md5'] != vosMD5 : 
                ## cache file  (really we don't need the entire file, just upto length
                ## TODO:  Fix this loop so it can exit on certain errors.
                success=False
                while not success:
                    os.lseek(fh,0,os.SEEK_SET)
                    try:
                        r = self.client.open(path,mode=os.O_RDONLY,view='data')
                        fpos=0
                        while fpos < length:
                            buf=r.read(READBUF)
                            if not buf :
                                ## stop reading at end of file.
                                success=True
                                break
                            chunk=min(length-fpos,len(READBUF)) 
                            if os.write(fh,buf[:chunk])!=chunk:
                                raise FuseOSError(EIO)
                            fpos=fpos+chunk
                            if fpos >= length:
                                success=True
                                break
                    except Exception as e:
                        logging.error("Failed during truncate cache bulding (%s), trying again"  % ( str(e)))
                    finally: 
                        r.close()

        ## now we can truncate the file.
        os.ftruncate(fh,length)
        ## set cached true since this version is now the keeper.
        self.cache[path]['cached'] = True
        if close :
            ### fuse normally calls truncate without first opening the file
            self.release(path,fh)
        ## Update the access/mod times 
        self.utimens(path)
        return

    def unlink(self,path):
    	node = self.getNode(path, force=False, limit=1)
        if node and node.props.get('islocked', False):
            logging.info("%s is locked." % path)
            raise FuseOSError(EPERM)
        self.cache.unlink(path)
        if node:
            self.client.delete(path)
        self.delNode(path,force=True)


    def utimens(self, path, times=None):
        """Set the access and modification times of path"""
        logging.debug("Setting the access and modification times for %s " % ( path))
        logging.debug("%s" % (str(times)))
        if times is None:
          logging.debug("No times specified so using the current system time for access and modifcation")
          t=time.time()
          times = (t,t)
        else:
          logging.debug("Setting the access and modification times using times provided")
        logging.debug("Getting cache file name")
        fname=os.path.normpath(self.cache_dir+path)
        logging.debug("Setting access times on cached version at location %s" % ( fname))
        if os.access(fname,os.W_OK):
            logging.debug("Setting access times on cached version at location %s" % ( fname))
            try:
                os.utime(fname,times)
            except Exception as e:
                ex = FuseOSError(getattr(e,'errno',EIO))
                ex.strerror = getattr(e,'strerror','failed to set times on %s' %(path))
                raise ex
        logging.debug("Attempting to set the st_mtime and st_atime attributes")
        self.getattr(path)['st_mtime']=times[1]
        self.getattr(path)['st_atime']=times[0]
        return 

    def write(self, path, data, size, offset, fh=None):
        import ctypes

        if self.opt.readonly:
            logging.debug("File system is readonly.. so writing 0 bytes\n")
            return 0


        if fh.readOnly:
            logging.debug("file was not opened for writing")
            raise FuseOSError(EPERM)

        logging.debug("%s -> %s" % ( path,fh))
        logging.debug("%d --> %d" % ( offset, offset+size))

        try:
            return fh.cacheFileHandle.write(data, size, offset)
        except CacheRetry:
            raise FuseOSError(EAGAIN)
        
        
