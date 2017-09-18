#!python
"""A script for sending files to VOSpace via multiple connection streams."""
from __future__ import (absolute_import, division, print_function,
                        unicode_literals)

import hashlib
import os
import sys
from multiprocessing import Process, JoinableQueue
from vos.commonparser import CommonParser
import errno
import logging
import time
import signal
from vos import vos, version
from .. import md5_cache

DESCRIPTION = """A script for sending files to VOSpace via multiple connection streams.

The list of files is given on the command line have their MD5s generated and then compared to 
the contents of VOSpace.  Files that do not exist in the destination VOSpace area or files that
have different MD5 sums are then queued to be copied to VOSpace.  vsync launches mutlple threads
that listen to the queue and transfer files independently to VOSpace and report success if the 
file successfully copies to VOSpace.  

At the completion of vsync an error report indicates if there were failures.  Run vsync repeatedly 
until no errors are reported.

eg.  vsync --cache_nodes --recursive --verbose ./local_dir vos:VOSPACE/remote_dir

Using cache_nodes option will greatly improve the speed of repeated calls but does result in a 
cache database file: ${HOME}/.config/vos/node_cache.db
"""

HOME = os.getenv("HOME", "./")

def vsync():
    global_md5_cache = None
    def signal_handler(h_stream, h_frame):
        logging.debug("{} {}".format(h_stream, h_frame))
        logging.critical("Interrupt\n")
        sys.exit(-1)
    usage = """
      vsync [options] files vos:Destination/

          Version: %s """ % (version.version)
    # handle interupts nicely
    signal.signal(signal.SIGINT, signal_handler)

    startTime = time.time()

    parser = CommonParser(usage)
    parser.add_option('--ignore-checksum', action="store_true", help='dont check MD5 sum, use size and time instead')
    parser.add_option('--cache_nodes', action='store_true', help='cache node MD5 sum in an sqllite db')
    parser.add_option('--recursive', '-r', help="Do a recursive sync", action="store_true")
    parser.add_option('--nstreams', '-n', type=int, help="Number of streams to run (MAX: 30)", default=1)
    parser.add_option('--exclude', help="ignore directories or files containing this pattern", default=None)
    parser.add_option('--include', help="only include files matching this pattern", default=None)
    parser.add_option('--overwrite', help="overwrite copy on server regardless of modification/size/md5 checks", action="store_true")
    parser.add_option('--load_test', action="store_true", help="Used to stress test the VOServer, also set --nstreams to a large value")

    if len(sys.argv) == 1:
        parser.print_help()
        sys.exit()

    (opt, args) = parser.parse_args()
    parser.process_informational_options()

    logger = logging.getLogger()
    logger.setLevel(parser.log_level)
    logger.addHandler(logging.StreamHandler())

    if len(args) < 2:
        parser.error("requires one or more source files and a single destination directory")

    if opt.nstreams > 30 and not opt.load_test:
        parser.error("Maximum of 30 streams exceeded")

    if opt.cache_nodes:
        from vos import md5_cache
        md5Cache = md5_cache.MD5_Cache()


    dest = args.pop()
    if dest[0:4] != "vos:":
        parser.error("Only allows sync FROM local copy TO VOSpace")
    ## Currently we don't create nodes in sync and we don't sync onto files
    logger.info("Connecting to VOSpace")
    client = vos.Client(vospace_certfile=opt.certfile, vospace_token=opt.token)
    logger.info("Confirming Destination is a directory")
    destIsDir = client.isdir(dest)

    queue = JoinableQueue(maxsize=10 * opt.nstreams)
    goodDirs = []
    nodeDict = {}


    def compute_md5(filename, block_size=None):
        """
        Read through a file and compute that files MD5 checksum.
        :param filename: name of the file on disk
        :param block_size: number of bytes to read into memory, defaults to 2**19 bytes
        :return: md5 as a hexadecimal string
        """
        block_size = block_size is None and 2**19 or block_size
        return md5_cache.MD5Cache.compute_md5(filename, block_size=block_size)

    def fileMD5(filename):
        import os
        md5 = None
        if opt.cache_nodes:
            md5 = md5Cache.get(filename)
        if md5 is None or md5[2] < os.stat(filename).st_mtime:
            md5 = compute_md5(filename)
            if opt.cache_nodes:
                stat = os.stat(filename)
                md5Cache.update(filename, md5, stat.st_size, stat.st_mtime)
        else:
            md5 = md5[0]
        return md5

    class ThreadCopy(Process):
        def __init__(self, queue, client):
            super(ThreadCopy, self).__init__()
            self.client = client
            self.queue = queue
            self.filesSent = 0
            self.filesSkipped = 0
            self.bytesSent = 0
            self.bytesSkipped = 0
            self.filesErrored = 0

        def run(self):
            while True:
                (src, dest) = self.queue.get()
                requeue = (src, dest)
                srcMD5 = None
                stat = os.stat(src)
                if not opt.ignore_checksum and not opt.overwrite:
                    srcMD5 = fileMD5(src)
                if not opt.overwrite:
                    # Check if the file is the same
                    try:
                        nodeInfo = None
                        if opt.cache_nodes:
                            nodeInfo = md5Cache.get(dest)
                        if nodeInfo is None:
                            logger.debug("Getting node info from VOSpace")
                            logger.debug(str(nodeDict.keys()))
                            logger.debug(str(dest))
                            node = self.client.get_node(dest, limit=None)
                            destMD5 = node.props.get('MD5', 'd41d8cd98f00b204e9800998ecf8427e')
                            destLength = node.attr['st_size']
                            destTime = node.attr['st_ctime']
                            if opt.cache_nodes:
                                md5Cache.update(dest, destMD5, destLength, destTime)
                        else:
                            destMD5 = nodeInfo[0]
                            destLength = nodeInfo[1]
                            destTime = nodeInfo[2]
                        logger.debug("Dest MD5: %s " % (destMD5))
                        if (not opt.ignore_checksum and srcMD5 == destMD5) or (opt.ignore_checksum and destTime >= stat.st_mtime and destLength == stat.st_size) :
                            logger.info("skipping: %s  matches %s" % (src, dest))
                            self.filesSkipped += 1
                            self.bytesSkipped += destLength
                            self.queue.task_done()
                            continue
                    except (IOError, OSError) as node_error:
                        """Ignore the erorr"""
                        logger.debug(str(node_error))
                        pass
                logger.info("%s -> %s" % (src, dest))
                try:
                    self.client.copy(src, dest, send_md5=True)
                    node = self.client.get_node(dest, limit=None)
                    destMD5 = node.props.get('MD5', 'd41d8cd98f00b204e9800998ecf8427e')
                    destLength = node.attr['st_size']
                    destTime = node.attr['st_ctime']
                    if opt.cache_nodes:
                           md5Cache.update(dest, destMD5, destLength, destTime)
                    self.filesSent += 1
                    self.bytesSent += stat.st_size
                except (IOError, OSError) as e:
                    logger.error("Error writing %s to server, skipping" % (src))
                    logger.error(str(e))
                    import re
                    if re.search('NodeLocked',str(e)) != None:
                        logger.error("Use vlock to unlock the node before syncing to %s." % (dest))
                    try:
                        if e.errno == 104:
                            self.queue.put(requeue)
                    except Exception as e2:
                        logger.error("Error during requeue")
                        logger.error(str(e2))
                        pass
                    self.filesErrored += 1
                    pass
                self.queue.task_done()


    def mkdirs(dirs):


        logger.debug("%s %s" % (dirs, str(goodDirs)))
        ## if we've seen this before skip it.
        if dirs in goodDirs:
            return

        ## try and make a new directory and return
        ## failure indicates we should see if subdirs exist
        try:
            client.mkdir(dirs)
            logger.info("Made directory %s " % (dirs))
            goodDirs.append(dirs)
            return
        except OSError as e:
            exit_code = getattr(e, 'errno', -1)
            if exit_code != errno.EEXIST:
                raise e

        ## OK, must already have existed, add to list
        goodDirs.append(dirs)

        return


    def copy(source, dest):
        ## strip down dest until we find a part that exists
        ## and then build up the path.  Dest should include the filename
        if os.path.islink(source):
            logger.error("%s is a link, skipping" % (source))
            return
        if not os.access(source, os.R_OK):
            logger.error("Failed to open file %s, skipping" % (source))
            return
        import re
        if re.match('^[A-Za-z0-9\\._\\-\\(\\);:&\\*\\$@!+=\\/]*$', source) is None:
            logger.error("filename %s contains illegal characters, skipping" % (source))
            return

        dirname = os.path.dirname(dest)
        mkdirs(dirname)
        if opt.include is not None and not re.search(opt.include, source):
            return
        queue.put((source, dest), timeout=3600)

    def startStreams(nstreams, vospace_client):
        streams = []
        for i in range(nstreams):
            logger.info("Launching vospace connection stream %d" % (i))
            t = ThreadCopy(queue, client=vospace_client)
            t.daemon = True
            t.start()
            streams.append(t)
        return streams


    def buildFileList(basePath, destRoot='', recursive=False, ignore=None):
        """Build a list of files that should be copied into VOSpace"""
        import string

        spinner = ['-', '\\', '|', '/', '-', '\\', '|', '/']
        count = 0
        import re

        for (root, dirs, filenames) in os.walk(basePath):
            for thisDirname in dirs:
                if not recursive:
                    continue
                thisDirname = os.path.join(root, thisDirname)
                skip = False
                if ignore is not None:
                    for thisIgnore in ignore.split(','):
                        if not thisDirname.find(thisIgnore) < 0:
                            logger.info("excluding: %s " % (thisDirname))
                            skip = True
                            continue
                if skip:
                    continue
                cprefix = os.path.commonprefix((basePath, thisDirname))
                thisDirname = os.path.normpath(destRoot + "/" + thisDirname[len(cprefix):])
                mkdirs(thisDirname)
            for thisfilename in filenames:
                srcfilename = os.path.normpath(os.path.join(root, thisfilename))
                skip = False
                if ignore is not None:
                    for thisIgnore in ignore.split(','):
                        if not srcfilename.find(thisIgnore) < 0:
                            logger.info("excluding: %s " % (srcfilename))
                            skip = True
                            continue
                if skip:
                    continue
                cprefix = os.path.commonprefix((basePath, srcfilename))
                destfilename = os.path.normpath(destRoot + "/" + srcfilename[len(cprefix):])
                thisDirname = os.path.dirname(destfilename)
                mkdirs(thisDirname)

                count += 1
                if opt.verbose:
                    sys.stderr.write("Building list of files to transfer %s\r" % (spinner[count % len(spinner)]))
                copy(srcfilename, destfilename)
            if not recursive:
                return
        return


    streams = startStreams(opt.nstreams, vospace_client=client)

    ### build a complete file list given all the things on the command line
    for filename in args:
        filename = os.path.abspath(filename)
        thisRoot = dest
        if os.path.isdir(filename):
            if filename[-1] != "/" :
                if os.path.basename(filename) != os.path.basename(dest):
                    thisRoot = os.path.join(dest, os.path.basename(filename))
            mkdirs(thisRoot)
            nodeDict[thisRoot] = client.get_node(thisRoot, limit=None)
            try:
                buildFileList(filename, destRoot=thisRoot, recursive=opt.recursive, ignore=opt.exclude)
            except Exception as e:
                logger.error(str(e))
                logger.error("ignoring error")
        elif os.path.isfile(filename):
            if destIsDir:
                thisRoot = os.path.join(dest, os.path.basename(filename))
            copy(filename, thisRoot)
        else:
            logger.error("%s: No such file or directory." % (filename))


    logger.info("\nWaiting for transfers to complete.\nCTRL-\ to interrupt\n")

    queue.join()
    endTime = time.time()
    bytesSent = 0
    filesSent = 0
    bytesSkipped = 0
    filesSkipped = 0
    filesErrored = 0
    for stream in streams:
        bytesSent += stream.bytesSent
        bytesSkipped += stream.bytesSkipped
        filesSent += stream.filesSent
        filesSkipped += stream.filesSkipped
        filesErrored += stream.filesErrored

    logger.info("\n\n==== TRANSFER REPORT ====\n\n")

    if bytesSent > 0:
        rate = bytesSent / (endTime - startTime) / 1024.0
        logger.info("Sent %d files (%8.1f kbytes @ %8.3f kBytes/s)" % (filesSent, bytesSent / 1024.0, rate))
        speedUp = (bytesSkipped + bytesSent) / bytesSent
        logger.info("Speedup:  %f (skipped %d files)" % (speedUp, filesSkipped))

    if bytesSent == 0:
        logger.info("No files needed sending ")

    if filesErrored > 0:
        logger.info("Error transferring %d files, please try again" % (filesErrored))


