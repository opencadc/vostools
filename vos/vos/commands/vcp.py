#!python
"""copy files from / to vospace directly without using the FUSE layer"""
import traceback

from vos import version
from vos import vos
from vos.commonparser import CommonParser
from vos.vos import EndPoints
try:
    from xml.etree.ElementTree import ParseError
except:
    from exceptions import SyntaxError as ParseError

import logging
import sys
import errno
import os
import hashlib
import signal
import re
import glob
import traceback
import time
from cadcutils import exceptions


def signal_handler(signum, frame):
    raise KeyboardInterrupt("SIGINT signal handler. {0} {1}".format(signum, frame))


def vcp():
    ## handle interrupts nicely
    signal.signal(signal.SIGINT, signal_handler)

    parser = CommonParser("%prog filename vos:rootNode/destination",
                          description=("Copy a file or directory (always recursive) to a "
                                       "VOSpace location.  Try to be UNIX like. "))
    parser.add_option("--exclude", default=None, help="exclude files that match pattern")
    parser.add_option("--include", default=None, help="only include files that match pattern (overrides exclude)")
    parser.add_option("-i", "--interrogate", action="store_true", help="Ask before overwriting files")
    parser.add_option("--overwrite", action="store_true",
                      help="don't check destination MD5, just overwrite even if source matches destination")
    parser.add_option("--quick", action="store_true",
                      help="Use default CADC urls, for speed.  Will fail if CADC changes data storage mechanism",
                      default=False)
    parser.add_option("-L", "--follow-links",
                      help="Should recursive copy follow symbolic links? Default is to not follow links.",
                      action="store_true",
                      default=False)
    parser.add_option("--ignore", action="store_true", default=False,
                      help="ignore errors and continue with recursive copy")

    (opt, args) = parser.parse_args()
    parser.process_informational_options()

    if len(args) < 2:
        parser.error("Must give a source and a destination")

    dest = args.pop()
    this_destination = dest

    if dest[0:4] != 'vos:':
        dest = os.path.abspath(dest)

    client = vos.Client(vospace_certfile=opt.certfile,
                        vospace_token=opt.token,
                        transfer_shortcut=opt.quick)

    exit_code = 0

    cutout_pattern = re.compile(r'(.*?)(?P<cutout>(\[[\-\+]?[\d\*]+(:[\-\+]?[\d\*]+)?(,[\-\+]?[\d\*]+(:[\-\+]?[\d\*]+)?)?\])+)$')

    ra_dec_cutout_pattern = re.compile("([^\(\)]*?)"
                                       "(?P<cutout>\("
                                       "(?P<ra>[\-\+]?\d*(\.\d*)?),"
                                       "(?P<dec>[\-\+]?\d*(\.\d*)?),"
                                       "(?P<rad>\d*(\.\d*)?)\))?")

    # Warnings:
    # vcp destination specified with a trailing '/' implies ContainerNode
    #
    #    If destination has trailing '/' and exists but is a DataNode then
    #    error message is returned:  "Invalid Argument (target node is not a DataNode)"
    #
    #    vcp currently only works on the CADC VOSpace server.
    # Version: %s """ % (version.version)


    def size(filename):
        if filename[0:4] == "vos:":
            return client.size(filename)
        return os.stat(filename).st_size


    def create(filename):
        if filename[0:4] == "vos:":
            return client.create(vos.Node(client.fix_uri(filename)))
        else:
            open(filename,'w').close()
    pass


    def get_node(filename, limit=None):
        """Get node, from cache if possible"""
        return client.get_node(filename, limit=limit)


    # here are a series of methods that choose between calling the system version or the vos version of various
    # function, based on pattern matching.
    # TODO: Put these function in a separate module.

    def isdir(filename):
        logging.debug("Doing an isdir on %s" % filename)
        if filename[0:4] == "vos:":
            return client.isdir(filename)
        else:
            return os.path.isdir(filename)


    def islink(filename):
        logging.debug("Doing an islink on %s" % filename)
        if filename[0:4] == "vos:":
            try:
                return get_node(filename).islink()
            except:
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
        if filename[0:4] == "vos:":
            try:
                node = get_node(filename, limit=0)
                return node is not None
            except (exceptions.NotFoundException, exceptions.ForbiddenException,
                    exceptions.UnauthorizedException) as ex:
                return False
        else:
            return os.access(filename, mode)


    def listdir(dirname):
        """Walk through the directory structure a al os.walk"""
        logging.debug("getting a dirlist %s " % dirname)

        if dirname[0:4] == "vos:":
            return client.listdir(dirname, force=True)
        else:
            return os.listdir(dirname)


    def mkdir(filename):
        logging.debug("Making directory %s " % filename)
        if filename[0:4] == 'vos:':
            return client.mkdir(filename)
        else:
            return os.mkdir(filename)


    def lglob(pathname):
        """
        Call system glob if not vos path.
        @param pathname: the pathname (aka pattern) to glob with.
        @return: list of matched filenames.
        """
        if pathname[0:4] == "vos:":
            return client.glob(pathname)
        else:
            return glob.glob(pathname)


    def get_md5(filename):
        logging.debug("getting the MD5 for %s" % filename)
        if filename[0:4] == 'vos:':
            md5 = get_node(filename).props.get('MD5', 'd41d8cd98f00b204e9800998ecf8427e')
        else:
            md5 = hashlib.md5()
            fin = file(filename, 'r')
            while True:
                buff = fin.read()
                if len(buff) == 0:
                    break
                md5.update(buff)
            md5 = md5.hexdigest()
        return md5


    def lglob(pathname):
        if pathname[0:4] == "vos:":
            return client.glob(pathname)
        else:
            return glob.glob(pathname)


    def copy(source_name, destination_name, exclude=None, include=None, interrogate=False, overwrite=False, ignore=False):
        """

        :param source_name:
        :param destination_name:
        :param exclude:
        :param include:
        :return: :raise e:
        """
        global exit_code
        ## determine if this is a directory we are copying so need to be recursive
        try:
            if not opt.follow_links and islink(source_name):
                #return link(source_name, destination_name)
                logging.info("{}: Skipping (symbolic link)".format(source_name))
                return
            if isdir(source_name):
                ## make sure the destination exists...
                if not isdir(destination_name):
                    mkdir(destination_name)
                ## for all files in the current source directory copy them to the destination directory
                for filename in listdir(source_name):
                    logging.debug("%s -> %s" % (filename, source_name))
                    copy(os.path.join(source_name, filename), os.path.join(destination_name, filename),
                         exclude, include, interrogate, overwrite, ignore)
            else:
                if interrogate:
                    if access(destination_name, os.F_OK):
                        sys.stderr.write("File %s exists.  Overwrite? (y/n): " % destination_name)
                        ans = sys.stdin.readline().strip()
                        if ans != 'y':
                            raise Exception("File exists")

                if not overwrite and access(destination_name, os.F_OK):
                    ### check if the MD5 of dest and source mathc, if they do then skip
                    if get_md5(destination_name) == get_md5(source_name):
                        logging.info("%s matches %s, skipping" % (source_name, destination_name))
                        return

                if not access(os.path.dirname(destination_name), os.F_OK):
                    raise OSError(errno.EEXIST, "vcp: ContainerNode %s does not exist" % os.path.dirname(destination_name))

                if not isdir(os.path.dirname(destination_name)) and not islink(os.path.dirname(destination_name)):
                    raise OSError(errno.ENOTDIR,
                                  "vcp: %s is not a ContainerNode or LinkNode" % os.path.dirname(destination_name))

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
                        logging.debug("Starting call to copy")
                        client.copy(source_name, destination_name, send_md5=True)
                        logging.debug("Call to copy returned")
                        break
                    except Exception as client_exception:
                        logging.debug("{}".format(client_exception))
                        if getattr(client_exception, 'errno', -1) == 104:
                            # 104 is connection reset by peer.  Try again on this error
                            logging.warning(str(client_exception))
                            exit_code = getattr(client_exception, 'errno', -1)
                        elif getattr(client_exception, 'errno', -1) == errno.EIO:
                            # retry on IO errors
                            logging.warning("{0}: Retrying".format(client_exception))
                            pass
                        elif ignore:
                            if niters > 100:
                                logging.error("%s (skipping after %d attempts)" % (str(client_exception), niters))
                                skip = True
                            else:
                                logging.error("%s (retrying)" % str(client_exception))
                                time.sleep(5)
                                niters += 1
                        else:
                            raise client_exception
                    except Exception as ex:
                        logging.debug("{}".format(ex))
                        raise

        except OSError as os_exception:
            logging.debug(str(os_exception))
            if getattr(os_exception, 'errno', -1) == errno.EINVAL:
                # not a valid uri, just skip those...
                logging.warning("%s: Skipping" % str(os_exception))
            else:
                raise os_exception


    # main loop
    try:
        for source_pattern in args:
            # define this empty cutout string.  Then we strip possible cutout strings off the end of the
            # pattern before matching.  This allows cutouts on the vos service.
            # The shell does pattern matching for local files, so don't run glob on local files.
            if source_pattern[0:4] != "vos:":
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
                sources = lglob(source_pattern)
                if cutout is not None:
                    # stick back on the cutout pattern if there was one.
                    sources = [s+cutout for s in sources]
            for source in sources:
                if source[0:4] != "vos:":
                    source = os.path.abspath(source)
                # the source must exist, of course...
                if not access(source, os.R_OK):
                    raise Exception("Can't access source: %s " % source)

                if not opt.follow_links and islink(source):
                    logging.info("{}: Skipping (symbolic link)".format(source))
                    continue

                # copying inside VOSpace not yet implemented
                if source[0:4] == 'vos:' and dest[0:4] == 'vos:':
                    raise Exception("Can not (yet) copy from VOSpace to VOSpace.")

                this_destination = dest
                if isdir(source):
                    if not opt.follow_links and islink(source) :
                        continue
                    logging.debug("%s is a directory or link to one" % source)
                    # To mimic unix fs behaviours if copying a directory and
                    # the destination directory exists then the actual
                    # destination in a recursive copy is the destination +
                    # source basename.
                    # This has an odd behaviour if more than one directory is given as a source and the copy is recursive.
                    if access(dest, os.F_OK):
                        if not isdir(dest):
                            raise Exception("Can't write a directory (%s) to a file (%s)" % (source, dest))
                        # directory exists so we append the end of source to that (UNIX behaviour)
                        this_destination = os.path.normpath(os.path.join(dest, os.path.basename(source)))
                    elif len(args) > 1:
                        raise Exception("vcp can not copy multiple things into a non-existent location (%s)" % dest)
                elif dest[-1] == '/' or isdir(dest):
                    # we're copying into a directory
                    this_destination = os.path.join(dest, os.path.basename(source))
                copy(source, this_destination, exclude=opt.exclude, include=opt.include,
                     interrogate=opt.interrogate, overwrite=opt.overwrite, ignore=opt.ignore)

    except KeyboardInterrupt as ke:
        logging.info("Received keyboard interrupt. Execution aborted...\n")
        exit_code = getattr(ke, 'errno', -1)
    except ParseError as pe:
        exit_code = errno.EREMOTE
        logging.error("Failure at server while copying {0} -> {1}".format(source, dest))
    except Exception as e:
        message = str(e)
        if re.search('NodeLocked', str(e)) is not None:
            logging.error("Use vlock to unlock the node before copying to %s." % this_destination)
        elif getattr(e, 'errno', -1) == errno.EREMOTE:
            logging.error("Failure at remote server while copying {0} -> {1}".format(source, dest))
        else:
            logging.debug("Exception throw: %s %s" % (type(e), str(e)))
            logging.debug(traceback.format_exc())
            logging.error(message)
        exit_code = getattr(e, 'errno', -1)

    sys.exit(exit_code)
