# ***********************************************************************
# ******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
# *************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
#
#  (c) 2024.                            (c) 2024.
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

"""copy files vospace to local or local to VOSpace"""
from .. import md5_cache
from .. import vos
from ..commonparser import CommonParser, set_logging_level_from_args, \
    exit_on_exception, URI_DESCRIPTION

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
import time
import warnings
from cadcutils import exceptions

__all__ = ['vcp']

DESCRIPTION = """Copy files to and from VOSpace. Always recursive.
VOSpace service associated to the requested container is discovered via
registry search.

{}

vcp can be used to cutout particular parts of a FITS file if the VOSpace
server supports the action.

extensions and pixel locations accessed with [] brackets:
vcp vos:Node/filename.fits[3][1:100,1:100] ./
or
RA/DEC regions accessed vcp vos:Node/filename.fits(RA, DEC, RAD)
where RA, DEC and RAD are all given in degrees

Wildcards in the path or filename work also:
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
        help="DEPRECATED",
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

    args = parser.parse_args()

    set_logging_level_from_args(args)

    dest = args.destination
    this_destination = dest

    if args.overwrite:
        warnings.warn("the --overwrite option is no longer supported")

    client = vos.Client(
        vospace_certfile=args.certfile, vospace_token=args.token,
        insecure=args.insecure)

    if not client.is_remote_file(dest):
        dest = os.path.abspath(dest)

    cutout_pattern = re.compile(
        r'(.*?)(?P<cutout>(\[[\-+]?[\d*]+(:[\-+]?[\d*]+)?'
        r'(,[\-+]?[\d*]+(:[\-+]?[\d*]+)?)?\])+)$')

    ra_dec_cutout_pattern = re.compile(r"([^()]*?)"
                                       r"(?P<cutout>\("
                                       r"(?P<ra>[\-+]?\d*(\.\d*)?),"
                                       r"(?P<dec>[\-+]?\d*(\.\d*)?),"
                                       r"(?P<rad>\d*(\.\d*)?)\))?")

    # Warnings:
    # vcp destination specified with a trailing '/' implies ContainerNode
    #
    #    If destination has trailing '/' and exists but is a DataNode then
    #    error message is returned:  "Invalid Argument (target node is not a
    # DataNode)"
    #
    #    vcp currently only works on the CADC VOSpace server.
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
        if client.is_remote_file(filename):
            return client.isdir(filename)
        else:
            return os.path.isdir(filename)

    def islink(filename):
        logging.debug("Doing an islink on %s" % filename)
        if client.is_remote_file(filename):
            try:
                return get_node(filename).islink()
            except exceptions.NotFoundException:
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
        if client.is_remote_file(filename):
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

        if client.is_remote_file(dirname):
            return client.listdir(dirname, force=True)
        else:
            return os.listdir(dirname)

    def mkdir(filename):
        logging.debug("Making directory %s " % filename)
        if client.is_remote_file(filename):
            return client.mkdir(filename)
        else:
            return os.mkdir(filename)

    def get_md5(filename):
        logging.debug("getting the MD5 for %s" % filename)
        if client.is_remote_file(filename):
            return get_node(filename).props.get('MD5', vos.ZERO_MD5)
        else:
            return md5_cache.MD5Cache.compute_md5(filename)

    def lglob(pathname):
        if client.is_remote_file(pathname):
            return client.glob(pathname)
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
                            "File %s exists.  Overwrite? (y/n): " %
                            destination_name)
                        ans = sys.stdin.readline().strip()
                        if ans != 'y':
                            raise Exception("File exists")

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
                        client.copy(source_name, destination_name, head=head)
                        logging.debug("Call to copy returned")
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
                            raise client_exception

        except OSError as os_exception:
            logging.debug(str(os_exception))
            if getattr(os_exception, 'errno', -1) == errno.EINVAL:
                # not a valid uri, just skip those...
                logging.warning("%s: Skipping" % str(os_exception))
                Nonlocal.exit_code += getattr(os_exception, 'errno', -1)
            else:
                exit_on_exception(os_exception)

    # main loop
    # Set source to the initial value of args so that if we have any issues
    # in the try before source gets defined at least we know where we were
    # starting.
    source = args.source[0]
    try:
        for source_pattern in args.source:

            if args.head and not client.is_remote_file(source_pattern):
                logging.error("head only works for source files in vospace")
                continue

            # define this empty cutout string.  Then we strip possible cutout
            # strings off the end of the pattern before matching.  This allows
            # cutouts on the vos service. The shell does pattern matching for
            # local files, so don't run glob on local files.
            if not client.is_remote_file(source_pattern):
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
                    sources = [s + cutout for s in sources]
            for source in sources:
                if not client.is_remote_file(source):
                    source = os.path.abspath(source)
                # the source must exist, of course...
                if not access(source, os.R_OK):
                    raise Exception("Can't access source: %s " % source)

                if not args.follow_links and islink(source):
                    logging.info("{}: Skipping (symbolic link)".format(source))
                    continue

                # copying inside VOSpace not yet implemented
                if client.is_remote_file(source) and \
                   client.is_remote_file(dest):
                    raise Exception(
                        "Can not (yet) copy from VOSpace to VOSpace.")

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
                    # we're copying into a directory
                    this_destination = os.path.join(dest,
                                                    os.path.basename(source))
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
