from __future__ import (absolute_import, division, print_function,
                        unicode_literals)

import os
import sys
from multiprocessing import Process, JoinableQueue
from vos.commonparser import CommonParser, set_logging_level_from_args, \
    URI_DESCRIPTION
import logging
import time
import signal
from vos import vos
from cadcutils import exceptions as transfer_exceptions
from .. import md5_cache

DESCRIPTION = """A script for sending files to VOSpace via multiple connection
streams.

{}

The list of files is given on the command line have their MD5s generated and
then compared to  the contents of VOSpace.  Files that do not exist in the
destination VOSpace area or files that have different MD5 sums are then queued
to be copied to VOSpace.  vsync launches mutlple threads that listen to the
queue and transfer files independently to VOSpace and report success if the
file successfully copies to VOSpace.

At the completion of vsync an error report indicates if there were failures.
Run vsync repeatedly until no errors are reported.

eg:
  vsync --cache_nodes --recursive --verbose ./local_dir vos:VOSPACE/remote_dir

Using cache_nodes option will greatly improve the speed of repeated calls but
does result in a  cache database file: $HOME/.config/vos/node_cache.db
""".format(URI_DESCRIPTION)

HOME = os.getenv("HOME", "./")


def vsync():
    global_md5_cache = None

    def signal_handler(h_stream, h_frame):
        logging.debug("{} {}".format(h_stream, h_frame))
        logging.critical("Interrupt\n")
        sys.exit(-1)

    # handle interrupts nicely
    signal.signal(signal.SIGINT, signal_handler)

    start_time = time.time()
    parser = CommonParser(description=DESCRIPTION)
    parser.add_option('files', nargs='+', help='Files to copy to VOSpace')
    parser.add_option('destination', help='VOSpace location to sync files to')
    parser.add_option('--ignore-checksum', action="store_true",
                      help='dont check MD5 sum, use size and time instead')
    parser.add_option('--cache_nodes', action='store_true',
                      help='cache node MD5 sum in an sqllite db')
    parser.add_option('--cache_filename',
                      help="Name of file to use for node cache",
                      default="{}/.config/vos/node_cache.db".format(HOME))
    parser.add_option('--recursive', '-r', help="Do a recursive sync",
                      action="store_true")
    parser.add_option('--nstreams', '-n', type=int,
                      help="Number of streams to run (MAX: 30)", default=5)
    parser.add_option(
        '--exclude',
        help="ignore directories or files containing this pattern",
        default=None)
    parser.add_option('--include',
                      help="only include files matching this pattern",
                      default=None)
    parser.add_option(
        '--overwrite',
        help=("overwrite copy on server regardless of modification/size/md5 "
              "checks"),
        action="store_true")

    opt = parser.parse_args()
    set_logging_level_from_args(opt)

    if opt.nstreams > 30:
        parser.error("Maximum of 30 streams exceeded")

    if opt.cache_nodes:
        global_md5_cache = md5_cache.MD5Cache(cache_db=opt.cache_filename)

    destination = opt.destination
    if not vos.is_remote_file(destination):
        parser.error("Only allows sync FROM local copy TO VOSpace")
    # Currently we don't create nodes in sync and we don't sync onto files
    logging.info("Connecting to VOSpace")
    client = vos.Client(
        vospace_certfile=opt.certfile, vospace_token=opt.token)
    logging.info("Confirming Destination is a directory")
    dest_is_dir = client.isdir(destination)

    queue = JoinableQueue(maxsize=10 * opt.nstreams)
    good_dirs = []
    node_dict = {}

    def compute_md5(this_filename, block_size=None):
        """
        Read through a file and compute that files MD5 checksum.
        :param this_filename: name of the file on disk
        :param block_size: number of bytes to read into memory,
        defaults to 2**19 bytes
        :return: md5 as a hexadecimal string
        """
        block_size = block_size is None and 2 ** 19 or block_size
        return md5_cache.MD5Cache.compute_md5(this_filename,
                                              block_size=block_size)

    def file_md5(this_filename):
        import os
        md5 = None
        if global_md5_cache is not None:
            md5 = global_md5_cache.get(this_filename)
        if md5 is None or md5[2] < os.stat(this_filename).st_mtime:
            md5 = compute_md5(this_filename)
            if global_md5_cache is not None:
                stat = os.stat(this_filename)
                global_md5_cache.update(this_filename, md5, stat.st_size,
                                        stat.st_mtime)
        else:
            md5 = md5[0]
        return md5

    class ThreadCopy(Process):
        def __init__(self, this_queue):
            super(ThreadCopy, self).__init__()
            self.client = vos.Client(
                vospace_certfile=opt.certfile,
                vospace_token=opt.token)
            self.queue = this_queue
            self.filesSent = 0
            self.filesSkipped = 0
            self.bytesSent = 0
            self.bytesSkipped = 0
            self.filesErrored = 0

        def run(self):
            while True:
                (current_source, current_destination) = self.queue.get()
                requeue = (current_source, current_destination)
                src_md5 = None
                stat = os.stat(current_source)
                if not opt.ignore_checksum and not opt.overwrite:
                    src_md5 = file_md5(current_source)
                if not opt.overwrite:
                    # Check if the file is the same
                    try:
                        node_info = None
                        if opt.cache_nodes:
                            node_info = global_md5_cache.get(
                                current_destination)
                        if node_info is None:
                            logging.debug("Getting node info from VOSpace")
                            logging.debug(str(node_dict.keys()))
                            logging.debug(str(current_destination))
                            node = self.client.get_node(current_destination,
                                                        limit=None)
                            current_destination_md5 = node.props.get(
                                'MD5', 'd41d8cd98f00b204e9800998ecf8427e')
                            current_destination_length = node.attr['st_size']
                            current_destination_time = node.attr['st_ctime']
                            if opt.cache_nodes:
                                global_md5_cache.update(
                                    current_destination,
                                    current_destination_md5,
                                    current_destination_length,
                                    current_destination_time)
                        else:
                            current_destination_md5 = node_info[0]
                            current_destination_length = node_info[1]
                            current_destination_time = node_info[2]
                        logging.debug("Destination MD5: {}".format(
                            current_destination_md5))
                        if ((not opt.ignore_checksum and src_md5 ==
                                current_destination_md5) or
                            (opt.ignore_checksum and
                            current_destination_time >= stat.st_mtime and
                                current_destination_length == stat.st_size)):
                            logging.info("skipping: %s  matches %s" % (
                                current_source, current_destination))
                            self.filesSkipped += 1
                            self.bytesSkipped += current_destination_length
                            self.queue.task_done()
                            continue
                    except (transfer_exceptions.AlreadyExistsException,
                            transfer_exceptions.NotFoundException):
                        pass
                logging.info(
                    "%s -> %s" % (current_source, current_destination))
                try:
                    self.client.copy(current_source, current_destination,
                                     send_md5=True)
                    node = self.client.get_node(current_destination,
                                                limit=None)
                    current_destination_md5 = node.props.get(
                        'MD5', 'd41d8cd98f00b204e9800998ecf8427e')
                    current_destination_length = node.attr['st_size']
                    current_destination_time = node.attr['st_ctime']
                    if opt.cache_nodes:
                        global_md5_cache.update(current_destination,
                                                current_destination_md5,
                                                current_destination_length,
                                                current_destination_time)
                    self.filesSent += 1
                    self.bytesSent += stat.st_size
                except (IOError, OSError) as exc:
                    logging.error(
                        "Error writing {} to server, skipping".format(
                            current_source))
                    logging.error(str(exc))
                    import re
                    if re.search('NodeLocked', str(exc)) is not None:
                        logging.error(
                            ("Use vlock to unlock the node before syncing "
                             "to {}").format(current_destination))
                    try:
                        if exc.errno == 104:
                            self.queue.put(requeue)
                    except Exception as e2:
                        logging.error("Error during requeue")
                        logging.error(str(e2))
                        pass
                    self.filesErrored += 1
                    pass
                self.queue.task_done()

    def mkdirs(directory):
        """Recursively make all nodes in the path to directory.

        :param directory: str, vospace location of ContainerNode (directory)
        to make
        :return:
        """

        logging.debug("%s %s" % (directory, str(good_dirs)))
        # if we've seen this before skip it.
        if directory in good_dirs:
            return

        # try and make a new directory and return
        # failure indicates we should see if subdirectories exist
        try:
            client.mkdir(directory)
            logging.info("Made directory {}".format(directory))
            good_dirs.append(directory)
            return
        except transfer_exceptions.AlreadyExistsException:
            pass

        # OK, must already have existed, add to list
        good_dirs.append(directory)

        return

    def copy(current_source, current_destination):
        """
        Copy current_source from local file system to current_destination.

        :param current_source: name of local file
        :param current_destination: name of localtion on VOSpace to copy file
        to (includes filename part)
        :return: None
        """
        # strip down current_destination until we find a part that exists
        # and then build up the path.
        if os.path.islink(current_source):
            logging.error("{} is a link, skipping".format(current_source))
            return
        if not os.access(current_source, os.R_OK):
            logging.error(
                "Failed to open file {}, skipping".format(current_source))
            return
        import re
        if re.match(r'^[A-Za-z0-9._\-();:&*$@!+=/]*$', current_source) is None:
            logging.error(
                "filename %s contains illegal characters, skipping" %
                current_source)
            return

        dirname = os.path.dirname(current_destination)
        mkdirs(dirname)
        if opt.include is not None and not re.search(opt.include,
                                                     current_source):
            return
        queue.put((current_source, current_destination), timeout=3600)

    def start_streams(no_streams):
        list_of_streams = []
        for i in range(no_streams):
            logging.info("Launching VOSpace connection stream %d" % i)
            t = ThreadCopy(queue)
            t.daemon = True
            t.start()
            list_of_streams.append(t)
        return list_of_streams

    def build_file_list(base_path, destination_root='', recursive=False,
                        ignore=None):
        """Build a list of files that should be copied into VOSpace"""

        spinner = ['-', '\\', '|', '/', '-', '\\', '|', '/']
        count = 0

        for (root, dirs, filenames) in os.walk(base_path):
            for this_dirname in dirs:
                if not recursive:
                    continue
                this_dirname = os.path.join(root, this_dirname)
                skip = False
                if ignore is not None:
                    for thisIgnore in ignore.split(','):
                        if not this_dirname.find(thisIgnore) < 0:
                            logging.info("excluding: %s " % this_dirname)
                            skip = True
                            continue
                if skip:
                    continue
                cprefix = os.path.commonprefix((base_path, this_dirname))
                this_dirname = os.path.normpath(
                    destination_root + "/" + this_dirname[len(cprefix):])
                mkdirs(this_dirname)
            for thisfilename in filenames:
                srcfilename = os.path.normpath(
                    os.path.join(root, thisfilename))
                skip = False
                if ignore is not None:
                    for thisIgnore in ignore.split(','):
                        if not srcfilename.find(thisIgnore) < 0:
                            logging.info("excluding: %s " % srcfilename)
                            skip = True
                            continue
                if skip:
                    continue
                cprefix = os.path.commonprefix((base_path, srcfilename))
                destfilename = os.path.normpath(
                    destination_root + "/" + srcfilename[len(cprefix):])
                this_dirname = os.path.dirname(destfilename)
                mkdirs(this_dirname)

                count += 1
                if opt.verbose:
                    sys.stderr.write(
                        "Building list of files to transfer %s\r" % (
                            spinner[count % len(spinner)]))
                copy(srcfilename, destfilename)
            if not recursive:
                return
        return

    streams = start_streams(opt.nstreams)

    # build a complete file list given all the things on the command line
    for filename in opt.files:
        filename = os.path.abspath(filename)
        this_root = destination
        if os.path.isdir(filename):
            if filename[-1] != "/":
                if os.path.basename(filename) != os.path.basename(destination):
                    this_root = os.path.join(destination,
                                             os.path.basename(filename))
            mkdirs(this_root)
            node_dict[this_root] = client.get_node(this_root, limit=None)
            try:
                build_file_list(filename, destination_root=this_root,
                                recursive=opt.recursive, ignore=opt.exclude)
            except Exception as e:
                logging.error(str(e))
                logging.error("ignoring error")
        elif os.path.isfile(filename):
            if dest_is_dir:
                this_root = os.path.join(destination,
                                         os.path.basename(filename))
            copy(filename, this_root)
        else:
            logging.error("%s: No such file or directory." % filename)

    logging.info(
        ("Waiting for transfers to complete "
         r"********  CTRL-\ to interrupt  ********"))

    queue.join()
    end_time = time.time()
    bytes_sent = 0
    files_sent = 0
    bytes_skipped = 0
    files_skipped = 0
    files_erred = 0
    for stream in streams:
        bytes_sent += stream.bytesSent
        bytes_skipped += stream.bytesSkipped
        files_sent += stream.filesSent
        files_skipped += stream.filesSkipped
        files_erred += stream.filesErrored

    logging.info("==== TRANSFER REPORT ====")

    if bytes_sent > 0:
        rate = bytes_sent / (end_time - start_time) / 1024.0
        logging.info("Sent %d files (%8.1f kbytes @ %8.3f kBytes/s)" % (
            files_sent, bytes_sent / 1024.0, rate))
        speed_up = (bytes_skipped + bytes_sent) / bytes_sent
        logging.info(
            "Speedup:  %f (skipped %d files)" % (speed_up, files_skipped))

    if bytes_sent == 0:
        logging.info("No files needed sending ")

    if files_erred > 0:
        logging.info(
            "Error transferring %d files, please try again" % files_erred)


vsync.__doc__ = DESCRIPTION
