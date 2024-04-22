# ***********************************************************************
# ******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
# *************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
#
#  (c) 2022.                            (c) 2022.
#  Government of Canada                 Gouvernement du Canada
#  National Research Council            Conseil national de recherches
#  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
#  All rights reserved                  Tous droits réservés
#
#  NRC disclaims any warranties,        Le CNRC dénie toute garantie
#  expressed, implied, or               énoncée, implicite ou légale,
#  statutory, of any kind with          de quelque nature que ce
#  respect to the software,             soit, concernant le logiciel,
#  including without limitation         y compris sans restriction
#  any warranty of merchantability      toute garantie de valeur
#  or fitness for a particular          marchande ou de pertinence
#  purpose. NRC shall not be            pour un usage particulier.
#  liable in any event for any          Le CNRC ne pourra en aucun cas
#  damages, whether direct or           être tenu responsable de tout
#  indirect, special or general,        dommage, direct ou indirect,
#  consequential or incidental,         particulier ou général,
#  arising from the use of the          accessoire ou fortuit, résultant
#  software.  Neither the name          de l'utilisation du logiciel. Ni
#  of the National Research             le nom du Conseil National de
#  Council of Canada nor the            Recherches du Canada ni les noms
#  names of its contributors may        de ses  participants ne peuvent
#  be used to endorse or promote        être utilisés pour approuver ou
#  products derived from this           promouvoir les produits dérivés
#  software without specific prior      de ce logiciel sans autorisation
#  written permission.                  préalable et particulière
#                                       par écrit.
#
#  This file is part of the             Ce fichier fait partie du projet
#  OpenCADC project.                    OpenCADC.
#
#  OpenCADC is free software:           OpenCADC est un logiciel libre ;
#  you can redistribute it and/or       vous pouvez le redistribuer ou le
#  modify it under the terms of         modifier suivant les termes de
#  the GNU Affero General Public        la “GNU Affero General Public
#  License as published by the          License” telle que publiée
#  Free Software Foundation,            par la Free Software Foundation
#  either version 3 of the              : soit la version 3 de cette
#  License, or (at your option)         licence, soit (à votre gré)
#  any later version.                   toute version ultérieure.
#
#  OpenCADC is distributed in the       OpenCADC est distribué
#  hope that it will be useful,         dans l’espoir qu’il vous
#  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
#  without even the implied             GARANTIE : sans même la garantie
#  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
#  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
#  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
#  General Public License for           Générale Publique GNU Affero
#  more details.                        pour plus de détails.
#
#  You should have received             Vous devriez avoir reçu une
#  a copy of the GNU Affero             copie de la Licence Générale
#  General Public License along         Publique GNU Affero avec
#  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
#  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
#                                       <http://www.gnu.org/licenses/>.
#
#  $Revision: 4 $
#
# ***********************************************************************
#

import os
import sys
from vos.commonparser import CommonParser, set_logging_level_from_args, \
    URI_DESCRIPTION, exit_on_exception
import logging
import time
import signal
import threading
import concurrent.futures
import re

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

global_md5_cache = None
node_dict = {}

# placeholder for data local to a thread
thread_local = threading.local()


def compute_md5(filename):
    """"
    Computes the md5 of a file and caches the value for subsequent calls
    """
    md5 = None
    if global_md5_cache is not None:
        md5 = global_md5_cache.get(filename)
    if md5 is None or md5[2] < os.stat(filename).st_mtime:
        md5 = md5_cache.MD5Cache.compute_md5(filename,
                                             block_size=2**19)
        if global_md5_cache is not None:
            stat = os.stat(filename)
            global_md5_cache.update(filename, md5, stat.st_size,
                                    stat.st_mtime)
    else:
        md5 = md5[0]
    return md5


def get_client(certfile, token, insecure):
    """
    Returns a VOS client instance for each thread. VOS Client uses requests
    session which is not thread safe hence creating one instance of this class
    for each thread
    :param certfile:
    :param token:
    :param insecure: do not check server SSL certs
    :return: vos.Client
    """
    if not hasattr(thread_local, "client"):
        thread_local.client = vos.Client(vospace_certfile=certfile,
                                         vospace_token=token,
                                         insecure=insecure)
    return thread_local.client


class TransferReport:
    """
    Report of a job.
    """
    def __init__(self):
        self.bytes_sent = 0
        self.files_sent = 0
        self.bytes_skipped = 0
        self.files_skipped = 0
        self.files_erred = 0

    def __eq__(self, other):
        return (self.bytes_sent == other.bytes_sent) and \
               (self.files_sent == other.files_sent) and \
               (self.bytes_skipped == other.bytes_skipped) and \
               (self.files_skipped == other.files_skipped) and \
               (self.files_erred == other.files_erred)


def execute(src, dest, opt):
    """
    Transfer a file from source to destination
    :param src: local path to file to transfer
    :param dest: vospace location
    :param opt: command line parameters
    :return: TransferReport()
    """
    result = TransferReport()
    src_md5 = None
    stat = os.stat(src)
    if not opt.ignore_checksum and not opt.overwrite:
        src_md5 = compute_md5(src)
    client = get_client(opt.certfile, opt.token, opt.insecure)
    if not opt.overwrite:
        # Check if the file is the same
        try:
            node_info = None
            if opt.cache_nodes:
                node_info = global_md5_cache.get(dest)
            if node_info is None:
                logging.debug('Getting node info from VOSpace')
                logging.debug(str(node_dict.keys()))
                logging.debug(str(dest))
                node = client.get_node(dest, limit=None)
                dest_md5 = node.props.get(
                    'MD5', 'd41d8cd98f00b204e9800998ecf8427e')
                dest_length = node.attr['st_size']
                dest_time = node.attr['st_ctime']
                if opt.cache_nodes:
                    global_md5_cache.update(
                        dest,
                        dest_md5,
                        dest_length,
                        dest_time)
            else:
                dest_md5 = node_info[0]
                dest_length = node_info[1]
                dest_time = node_info[2]
            logging.debug('Destination MD5: {}'.format(
                dest_md5))
            if ((not opt.ignore_checksum and src_md5 == dest_md5) or
                    (opt.ignore_checksum and
                     dest_time >= stat.st_mtime and
                     dest_length == stat.st_size)):
                logging.info('skipping: {}  matches {}'.format(src, dest))
                result.files_skipped = 1
                result.bytes_skipped = dest_length
                return result
        except (transfer_exceptions.AlreadyExistsException,
                transfer_exceptions.NotFoundException):
            pass
    logging.info('{} -> {}'.format(src, dest))
    try:
        client.copy(src, dest)
        node = client.get_node(dest, limit=None)
        dest_md5 = node.props.get(
            'MD5', 'd41d8cd98f00b204e9800998ecf8427e')
        dest_length = node.attr['st_size']
        dest_time = node.attr['st_ctime']
        if opt.cache_nodes:
            global_md5_cache.update(dest, dest_md5, dest_length, dest_time)
        result.files_sent += 1
        result.bytes_sent += stat.st_size
        return result
    except (IOError, OSError) as exc:
        logging.error(
            'Error writing {} to server, skipping'.format(src))
        logging.debug(str(exc))
        if re.search('NodeLocked', str(exc)) is not None:
            logging.error(
                ('Use vlock to unlock the node before syncing '
                 'to {}').format(dest))
    result.files_erred += 1
    return result


def validate(path, include=None, exclude=None):
    """
    Determines whether a directory or filename should be included or not
    :param path: path to consider
    :param include: pattern for names to include
    :param exclude: pattern for names to exclude
    :return: True if filename is to be included, False otherwise
    """
    if re.match(r'^[A-Za-z0-9._\-();:&*$@!+=/]*$', path) is None:
        logging.error("filename {} contains illegal characters, "
                      "skipping".format(path))
        return False
    if include is not None and not re.search(include, path):
        logging.info("{} not included".format(path))
        return False
    if exclude:
        for thisIgnore in exclude.split(','):
            if not path.find(thisIgnore) < 0:
                logging.info("excluding: {}".format(path))
                return False
    return True


def prepare(src, dest, client):
    """
    If src is a directory it creates it otherwise prepares the transfer of file
    :param src: name of local file
    :param dest: name of location on VOSpace to copy file
    :param client: vos client to use for operations on the server (mkdir)
    :return: (src, dest) tuple to be sync if required or None otherwise
    """
    # strip down current_destination until we find a part that exists
    # and then build up the path.
    if os.path.islink(src):
        logging.error("{} is a link, skipping".format(src))
        return
    if not os.access(src, os.R_OK):
        logging.error(
            "Failed to open file {}, skipping".format(src))
        return

    if os.path.isdir(src):
        # make directory but nothing to transfer
        try:
            client.mkdir(dest)
            logging.info("Made directory {}".format(dest))
        except transfer_exceptions.AlreadyExistsException:
            # OK, must already have existed, add to list
            pass
        return
    return src, dest


def build_file_list(paths, vos_root, recursive=False, include=None,
                    exclude=None):
    """
    Build a list of files that should be copied into VOSpace
    :param paths: source paths
    :param vos_root: directory container on vospace service to sync to
    :param recursive: True if recursive sync, False otherwise
    :param include: patterns to include
    :param exclude: comma separated strings to exclude when occuring in names
    :return: set of expanded (src, dest) pairs
    """

    count = 0
    results = []  # order is important to create the directories first
    vos_root = vos_root.strip('/')
    for path in paths:
        content = False
        if path.endswith('/'):
            # vsync just the content and not the source dir
            content = True
            base_path = os.path.abspath(path)
            path = path[:-1]
        else:
            base_path = os.path.dirname(path)
        path = os.path.abspath(path)
        rel_path = os.path.relpath(path, base_path)
        if not os.path.exists(path):
            raise ValueError('{} not found'.format(path))
        if os.path.isfile(path):
            results.append((path, '{}/{}'.format(vos_root, rel_path)))
            continue
        elif not content:
            results.append((path, '{}/{}'.format(vos_root, rel_path)))
        for (root, dirs, filenames) in os.walk(path):
            if recursive:
                for this_dirname in dirs:
                    this_dirname = os.path.join(root, this_dirname)
                    rel_dirname = os.path.relpath(this_dirname, base_path)
                    if not validate(rel_dirname, include=include,
                                    exclude=exclude):
                        continue
                    results.append((this_dirname, '{}/{}'.format(
                        vos_root, rel_dirname)))
            for this_filename in filenames:
                srcfilename = os.path.normpath(os.path.join(root,
                                                            this_filename))
                rel_name = os.path.relpath(srcfilename, base_path)
                if not validate(rel_name, include=include, exclude=exclude):
                    continue
                count += 1
                results.append((srcfilename, '{}/{}'.format(
                    vos_root, rel_name)))
            if not recursive:
                break
    # remove duplicates while maintaining the order
    return list(dict.fromkeys(results))


def vsync():

    def signal_handler(h_stream, h_frame):
        logging.debug('{} {}'.format(h_stream, h_frame))
        logging.critical('Interrupt\n')
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
        global global_md5_cache
        global_md5_cache = md5_cache.MD5Cache(cache_db=opt.cache_filename)

    destination = opt.destination
    try:
        client = vos.Client(
            vospace_certfile=opt.certfile, vospace_token=opt.token,
            insecure=opt.insecure)
        if not client.is_remote_file(destination):
            parser.error("Only allows sync FROM local copy TO VOSpace")
        # Currently we don't create nodes in sync and we don't sync onto files
        logging.info("Connecting to VOSpace")
        logging.info("Confirming Destination is a directory")
        if client.isfile(destination):
            if len(opt.files) == 1:
                if os.path.isfile(opt.files):
                    files = [(opt.files, destination)]
                else:
                    raise RuntimeError(
                        'Cannot sync directory into a remote file')
            else:
                raise RuntimeError(
                    'Cannot sync multiple sources into a single remote file')
        else:
            files = build_file_list(paths=opt.files,
                                    vos_root=destination,
                                    recursive=opt.recursive,
                                    include=opt.include,
                                    exclude=opt.exclude)

        # build the list of transfers
        transfers = []
        for src_path, vos_dest in files:
            transfer = prepare(src_path, vos_dest, client)
            if transfer:
                transfers.append(transfer)

        # main execution loop
        futures = []
        with concurrent.futures.ThreadPoolExecutor(max_workers=opt.nstreams) \
                as executor:
            for file_src, vos_dest in transfers:
                futures.append(executor.submit(
                    execute, file_src, vos_dest, opt))

        logging.info(
            ("Waiting for transfers to complete "
             r"********  CTRL-\ to interrupt  ********"))

        end_time = time.time()
        end_result = TransferReport()
        for r in concurrent.futures.as_completed(futures):
            res = r.result()
            end_result.bytes_sent += res.bytes_sent
            end_result.bytes_skipped += res.bytes_skipped
            end_result.files_sent += res.files_sent
            end_result.files_skipped += res.files_skipped
            end_result.files_erred += res.files_erred

        logging.info("==== TRANSFER REPORT ====")

        if end_result.bytes_sent > 0:
            rate = end_result.bytes_sent / (end_time - start_time) / 1024.0
            logging.info("Sent {} files ({} kbytes @ {} kBytes/s)".format(
                end_result.files_sent,
                round(end_result.bytes_sent / 1024.0, 2),
                round(rate, 2)))
            speed_up = (end_result.bytes_skipped + end_result.bytes_sent) / \
                end_result.bytes_sent
            logging.info("Speedup:  {} (skipped {} files)".format(
                speed_up, end_result.files_skipped))
        if end_result.bytes_sent == 0:
            logging.info("No files needed sending ")

        if end_result.files_erred > 0:
            logging.info(
                "Error transferring {} files, please try again".format(
                    end_result.files_erred))
    except Exception as ex:
        exit_on_exception(ex)


vsync.__doc__ = DESCRIPTION
