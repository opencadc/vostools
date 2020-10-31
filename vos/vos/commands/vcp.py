#!python
"""copy files vospace to local or local to VOSpace"""
from __future__ import (absolute_import, division, print_function,
                        unicode_literals)

try:
    from xml.etree.ElementTree import ParseError
except ImportError:
    ParseError = SyntaxError
import logging
import sys
import errno
import os
import re
import glob
import six.moves.urllib.parse as urllibparse
import time

import warnings
from cadcutils import exceptions
from .. import md5_cache
from .. import vos, storage_inventory
from ..commonparser import CommonParser, set_logging_level_from_args,\
    exit_on_exception, URI_DESCRIPTION


urlparse = urllibparse.urlparse

__all__ = ['vcp']

DESCRIPTION = """Copy files to and from VOSpace or inventory storage.
Always recursive for VOSpace. VOSpace service associated to the
requested container and storage service are discovered via registry search.


{}

vcp can be used to cutout particular parts of a FITS file if the VOSpace
server supports the action.

extensions and pixel locations accessed with [] brackets:
vcp vos:Node/filename.fits[3][1:100,1:100] ./
or
RA/DEC regions accessed vcp vos:Node/filename.fits(RA, DEC, RAD)
where RA, DEC and RAD are all given in degrees

For inventory storage, wildcards in fielname work:
vcp *.fits cadc:TEST/

For VOSpace, wildcards in the path or filename work also:
vcp vos:VOSPACE/foo/*.txt .

If no X509 certificate given on commnad line then location specified by
default service settings will be used.
""".format(URI_DESCRIPTION)


def vcp():
    # TODO split this into main and methods

    class Nonlocal():
        # this is just a workaround the lack of nonlocal in Python 2.7
        # should be refactored when 2.7 support is dropped
        exit_code = 0

    parser = CommonParser(description=DESCRIPTION)
    parser.add_argument(
        "--resource-id", default=None,
        help="resource URI to use to lookup the storage inventory service "
             "(Minoc)")
    parser.add_argument(
        "source", nargs="+",
        help="file/directory/dataNode/containerNode to copy from.")
    parser.add_argument(
        "destination",
        help="file/directory/dataNode/containerNode to copy to")
    parser.add_argument(
        "--exclude", default=None,
        help="skip files that match pattern (overrides include)")
    parser.add_argument(
        "--include", default=None,
        help="only copy files that match pattern")
    parser.add_argument(
        "-i", "--interrogate", action="store_true",
        help="Ask before overwriting files")
    parser.add_argument(
        "--overwrite", action="store_true",
        help="DEPRECATED")
    parser.add_argument(
        "--quick", action="store_true",
        help="assuming CANFAR VOSpace, only comptible with CANFAR VOSpace.",
        default=False)
    parser.add_argument(
        "-L", "--follow-links",
        help="follow symbolic links. Default is to not follow links.",
        action="store_true",
        default=False)
    parser.add_argument(
        "--ignore", action="store_true", default=False,
        help="ignore errors and continue with recursive copy")
    parser.add_argument(
        "--head", action="store_true",
        help="copy only the headers of a file from vospace. Format of the "
             "returned files is text (and not FITS). Might return an error if "
             "the server does not support the operation on a given "
             "file type")
    parser.add_argument(
        "--secure", action="store_true", default=True,
        help="transfer contents of files using SSL encryption")

    args = parser.parse_args()

    set_logging_level_from_args(args)

    dest = args.destination
    this_destination = dest

    if args.overwrite:
        warnings.warn("the --overwrite option is no longer supported")

    is_storage = args.resource_id is not None

    if is_storage:
        client = storage_inventory.Client(resource_id=args.resource_id,
                                          certfile=args.certfile,
                                          token=args.token)
    else:
        client = vos.Client(vospace_certfile=args.certfile,
                            vospace_token=args.token,
                            transfer_shortcut=args.quick)

    if not vos.is_remote_file(dest, args.resource_id):
        dest = os.path.abspath(dest)

    Nonlocal.exit_code = 0

    cutout_pattern = re.compile(
        r'(.*?)(?P<cutout>(\[[\-+]?[\d*]+(:[\-+]?[\d*]+)?'
        r'(,[\-+]?[\d*]+(:[\-+]?[\d*]+)?)?\])+)$')

    ra_dec_cutout_pattern = re.compile(r"([^()]*?)"
                                       r"(?P<cutout>\("
                                       r"(?P<ra>[\-+]?\d*(\.\d*)?),"
                                       r"(?P<dec>[\-+]?\d*(\.\d*)?),"
                                       r"(?P<rad>\d*(\.\d*)?)\))?")

    # Warnings (applies to VOSpace only):
    # vcp destination specified with a trailing '/' implies ContainerNode
    #
    #    If destination has trailing '/' and exists but is a DataNode then
    #    error message is returned:  "Invalid Argument (target node is not a
    # DataNode)"
    #
    # Version: %s """ % (version.version)

    pass

    def get_node(filename, limit=None):
        """Get node, from cache if possible"""
        return client.get_node(filename, limit=limit)

    # here are a series of methods that choose between calling the system
    # version or the vos version of various
    # function, based on pattern matching.
    # TODO: Put these function in a separate module.

    def isdir(filename):
        logging.debug("Doing an isdir on %s" % filename)
        if vos.is_remote_file(filename, args.resource_id):
            return client.isdir(filename)
        else:
            return os.path.isdir(filename)

    def islink(filename):
        logging.debug("Doing an islink on %s" % filename)
        if vos.is_remote_file(filename, args.resource_id):
            try:
                return get_node(filename).islink()
            except exceptions.NotFoundException:
                return False
        elif vos.is_remote_file(filename, args.resource_id):
            return False
        else:
            return os.path.islink(filename)

    def access(filename, mode):
        """
        Check if the file can be accessed.
        @param filename: name of file or vospace node to check
        @param mode: what access mode should be checked: see os.access
        @return: True/False
        """
        logging.debug("checking for access %s " % filename)
        if is_storage and vos.is_remote_file(filename, args.resource_id):
            try:
                md = client.get_metadata(filename)
                logging.debug('Found metadata {}'.format(md))
                return True
            except (
                exceptions.NotFoundException, exceptions.ForbiddenException,
                    exceptions.UnauthorizedException):
                return False
        elif vos.is_remote_file(filename, args.resource_id):
            try:
                node = get_node(filename, limit=0)
                return node is not None
            except (
                exceptions.NotFoundException, exceptions.ForbiddenException,
                    exceptions.UnauthorizedException):
                return False

        else:
            return os.access(filename, mode)

    def listdir(dirname):
        """Walk through the directory structure a al os.walk"""
        logging.debug("getting a dirlist %s " % dirname)

        if vos.is_remote_file(dirname, args.resource_id):
            return client.listdir(dirname, force=True)
        else:
            return os.listdir(dirname)

    def mkdir(filename):
        logging.debug("Making directory %s " % filename)
        if vos.is_remote_file(filename, args.resource_id):
            return client.mkdir(filename)
        else:
            return os.mkdir(filename)

    def get_md5(filename):
        logging.debug("getting the MD5 for %s" % filename)
        if vos.is_remote_file(filename, args.resource_id):
            return get_node(filename).props.get('MD5', vos.ZERO_MD5)
        elif vos.is_remote_file(filename, args.resource_id):
            return client.get_metadata(filename).get('content_md5',
                                                     vos.ZERO_MD5)
        else:
            return md5_cache.MD5Cache.compute_md5(filename)

    def lglob(pathname):
        if vos.is_remote_file(pathname, args.resource_id):
            return client.glob(pathname)
        elif vos.is_remote_file(pathname, args.resource_id):
            return [pathname]
        else:
            return glob.glob(pathname)

    def copy(source_name, destination_name, exclude=None, include=None,
             interrogate=False, overwrite=False, ignore=False, head=False):
        """
        Send source_name to destination, possibly looping over contents if
        source_name points to a directory.

        source_name can specify cutout parameters if source is in VOSpace.
        Cutout parameters are passed to vos.Client
        vos.Client supports (RA,DEC,RAD) [in degrees] and [x1:x2,y1:y2]
        (in pixels)

        :param source_name: filename of the source to copy, can be a container
        or data node or directory or filename
        :param destination_name: where to copy the source to.
        :param exclude: pattern match against source names that will be
        excluded from recursive copy.
        :param include: only pattern match against source names that will be
        copied.
        :param interrogate: prompt before overwrite.
        :param overwrite: Should we overwrite existing destination?
        :param ignore: ignore errors during recursive copy, just continue.
        :param head: copy just the FITS headers
        :return:
        :raise e:
        """
        # determine if this is a directory we are copying so need to be
        # recursive
        try:
            if not args.follow_links and islink(source_name):
                logging.info(
                    "{}: Skipping (symbolic link)".format(source_name))
                return
            if isdir(source_name):
                # make sure the destination exists...
                if not isdir(destination_name):
                    mkdir(destination_name)
                # for all files in the current source directory copy them to
                # the destination directory
                for filename in listdir(source_name):
                    logging.debug("%s -> %s" % (filename, source_name))
                    copy(os.path.join(source_name, filename),
                         os.path.join(destination_name, filename),
                         exclude, include, interrogate, overwrite, ignore,
                         head)
            else:
                if interrogate:
                    if access(destination_name, os.F_OK):
                        sys.stderr.write(
                            "File {} exists.  Overwrite? (y/n): ".
                            format(destination_name))
                        ans = sys.stdin.readline().strip()
                        if ans != 'y':
                            raise Exception("File exists")
                if not access(os.path.dirname(destination_name), os.F_OK):
                    raise OSError(
                        errno.EEXIST, "vcp: ContainerNode {} does not exist".
                        format(os.path.dirname(destination_name)))
                if not isdir(os.path.dirname(destination_name)) and not \
                        islink(os.path.dirname(destination_name)):
                    raise OSError(
                        errno.ENOTDIR,
                        "vcp: {} is not a ContainerNode or LinkNode".
                        format(os.path.dirname(destination_name)))
                skip = False
                if exclude is not None:
                    for thisIgnore in exclude.split(','):
                        if not destination_name.find(thisIgnore) < 0:
                            skip = True
                            continue

                if include is not None:
                    skip = True
                    for thisIgnore in include.split(','):
                        if not destination_name.find(thisIgnore) < 0:
                            skip = False
                            continue

                if not skip:
                    logging.info("%s -> %s " % (source_name, destination_name))
                niters = 0
                while not skip:
                    try:
                        logging.debug(
                            "Starting call to copy with client {}".format(
                                type(client)))
                        client.copy(source_name, destination_name,
                                    send_md5=True, head=head)
                        logging.debug(
                            "Call to copy returned from {} to {}".format(
                                source_name, destination_name))
                        break
                    except Exception as client_exception:
                        logging.debug("{}".format(client_exception))
                        if getattr(client_exception, 'errno', -1) == 104:
                            # 104 is connection reset by peer.
                            # Try again on this error
                            logging.warning(str(client_exception))
                            Nonlocal.exit_code += \
                                getattr(client_exception, 'errno', -1)
                        elif getattr(client_exception, 'errno',
                                     -1) == errno.EIO:
                            # retry on IO errors
                            logging.warning(
                                "{0}: Retrying".format(client_exception))
                            pass
                        elif ignore:
                            if niters > 100:
                                logging.error(
                                    "%s (skipping after %d attempts)" % (
                                        str(client_exception), niters))
                                skip = True
                            else:
                                logging.error(
                                    "%s (retrying)" % str(client_exception))
                                time.sleep(5)
                                niters += 1
                        else:
                            import traceback
                            traceback.print_exc(file=sys.stdout)
                            raise client_exception

        except OSError as os_exception:
            logging.debug(str(os_exception))
            if getattr(os_exception, 'errno', -1) == errno.EINVAL:
                # not a valid uri, just skip those...
                logging.warning("%s: Skipping" % str(os_exception))
                Nonlocal.exit_code += getattr(os_exception, 'errno', -1)
            else:
                raise os_exception

    def copy_storage(source, destination):
        client.copy(source, destination, send_md5=True)

    # main loop
    # Set source to the initial value of args so that if we have any issues
    # in the try before source gets defined at least we know where we were
    # starting.
    source = args.source[0]
    try:
        if is_storage:
            copy_storage(source, args.destination)
            return
        for source_pattern in args.source:

            if args.head and not vos.is_remote_file(source_pattern,
                                                    args.resource_id):
                logging.error("head only works for source files in vospace")
                continue

            # define this empty cutout string.  Then we strip possible cutout
            # strings off the end of the pattern before matching.  This allows
            # cutouts on the vos service. The shell does pattern matching for
            # local files, so don't run glob on local files.
            if vos.is_remote_file(source_pattern, args.resource_id):
                sources = [source_pattern]
            else:
                cutout_match = cutout_pattern.search(source_pattern)
                cutout = None
                if cutout_match is not None:
                    source_pattern = cutout_match.group(1)
                    cutout = cutout_match.group('cutout')
                else:
                    ra_dec_match = ra_dec_cutout_pattern.search(source_pattern)
                    if ra_dec_match is not None:
                        cutout = ra_dec_match.group('cutout')
                logging.debug("cutout: {}".format(cutout))
                logging.debug('GLOBbing {}'.format(source_pattern))
                sources = lglob(source_pattern)
                if cutout is not None:
                    # stick back on the cutout pattern if there was one.
                    sources = [s + cutout for s in sources]

                logging.debug('Sources are {}'.format(sources))
            for source in sources:
                if not vos.is_remote_file(source, args.resource_id):
                    logging.debug('Correcting path.')
                    source = os.path.abspath(source)
                # the source must exist, of course...
                # TODO if not access(source, os.R_OK):
                #     raise Exception("Can't access source: %s " % source)

                logging.debug('Source {} can be accessed.'.format(source))

                if args.follow_links and islink(source):
                    logging.info("{}: Skipping (symbolic link)".format(source))
                    continue

                # copying inside VOSpace not yet implemented
                if vos.is_remote_file(source, args.resource_id) and \
                        vos.is_remote_file(dest, args.resource_id):
                    raise Exception(
                        "Can not (yet) copy from server to server.")

                this_destination = dest
                if isdir(source):
                    if not args.follow_links and islink(source):
                        continue
                    logging.debug("%s is a directory or link to one" % source)
                    # To mimic unix fs behaviours if copying a directory and
                    # the destination directory exists then the actual
                    # destination in a recursive copy is the destination +
                    # source basename.
                    # This has an odd behaviour if more than one directory is
                    # given as a source and the copy is recursive.
                    if access(dest, os.F_OK):
                        if not isdir(dest):
                            raise Exception(
                                "Can't write a directory (%s) to a file (%s)" %
                                (source, dest))
                        # directory exists so we append the end of source to
                        # that (UNIX behaviour)
                        this_destination = os.path.normpath(
                            os.path.join(dest, os.path.basename(source)))
                    elif len(args.source) > 1:
                        raise Exception(
                            ("vcp can not copy multiple things into a"
                             "non-existent location (%s)") % dest)
                elif dest[-1] == '/' or isdir(dest):
                    logging.debug(
                        'Checking if {} is a directory.'.format(dest))
                    # we're copying into a directory
                    this_destination = os.path.join(dest,
                                                    os.path.basename(source))

                logging.debug('Main copy from {} to {}'.format(
                    source, this_destination))
                copy(source, this_destination, exclude=args.exclude,
                     include=args.include,
                     interrogate=args.interrogate, overwrite=args.overwrite,
                     ignore=args.ignore, head=args.head)

    except KeyboardInterrupt as ke:
        logging.info("Received keyboard interrupt. Execution aborted...\n")
        Nonlocal.exit_code = getattr(ke, 'errno', -1)
    except ParseError:
        Nonlocal.exit_code = errno.EREMOTE
        msg = "Failure at server while copying {0} -> {1}\n".format(source,
                                                                    dest)
        exit_on_exception(msg)
    except Exception as e:
        if re.search('NodeLocked', str(e)) is not None:
            msg = "Use vlock to unlock the node before copying to {}.".format(
                this_destination)
            exit_on_exception(e, msg)
        elif getattr(e, 'errno', -1) == errno.EREMOTE:
            msg = "Failure at remote server while copying {0} -> {1}\n".format(
                    source, dest)
            exit_on_exception(e, msg)
        else:
            exit_on_exception(e)
    if Nonlocal.exit_code:
        sys.exit(Nonlocal.exit_code)


vcp.__doc__ = DESCRIPTION
