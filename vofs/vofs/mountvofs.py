#!python
"""A FUSE based filesystem view of VOSpace."""

from sys import platform
import os
import logging

from vos import vos
from .version import version
from .vofs import VOFS
from .vofs import MyFuse
from vos.commonparser import CommonParser
DAEMON_TIMEOUT = 60


def mountvofs():

    parser = CommonParser(description='mount vospace as a filesystem.')

    # mountvofs specific options
    parser.add_option("--vospace", help="the VOSpace to mount", default="vos:")
    parser.add_option("--mountpoint",
                      help="the mountpoint on the local filesystem",
                      default="/tmp/vospace")
    parser.add_option("-f", "--foreground", action="store_true",
                      help="Mount the filesystem as a foreground opperation and " +
                           "produce copious amounts of debuging information")
    parser.add_option("--log", action="store",
                      help="File to store debug log to", default="/tmp/vos.err")
    parser.add_option("--cache_limit", action="store", type=int,
                      help="upper limit on local diskspace to use for file caching (in MBytes)",
                      default=50 * 2 ** (10 + 10 + 10))
    parser.add_option("--cache_dir", action="store",
                      help="local directory to use for file caching", default=None)
    parser.add_option("--readonly", action="store_true",
                      help="mount vofs readonly", default=False)
    parser.add_option("--cache_nodes", action="store_true", default=False,
                      help="cache dataNode properties, containerNodes are not cached")
    parser.add_option("--allow_other", action="store_true", default=False,
                      help="Allow all users access to this mountpoint")
    parser.add_option("--max_flush_threads", action="store", type=int,
                      help="upper limit on number of flush (upload) threads",
                      default=10)
    parser.add_option("--secure_get", action="store_true", default=False,
                      help="Ensure HTTPS instead of HTTP is used to retrieve data (slower)")
    parser.add_option("--nothreads", help="Only run in a single thread, causes some blocking.", action="store_true")

    (opt, args) = parser.parse_args()
    parser.process_informational_options()

    log_format = ("%(asctime)s %(thread)d vos-"+str(version)+" %(module)s.%(funcName)s.%(lineno)d %(message)s")

    lf = logging.Formatter(fmt=log_format)
    fh = logging.FileHandler(filename=os.path.abspath('/tmp/vos.exceptions'))
    fh.formatter = lf

    # send the 'logException' statements to a seperate log file.
    logger = logging.getLogger('exceptions')
    logger.handlers = []
    logger.setLevel(logging.ERROR)
    logger.addHandler(fh)

    fh = logging.FileHandler(filename=os.path.abspath(opt.log))
    fh.formatter = lf
    logger = logging.getLogger('vofs')
    logger.handlers = []
    logger.setLevel(parser.log_level)
    logger.addHandler(fh)

    vos_logger = logging.getLogger('vos')
    vos_logger.handlers = []
    vos_logger.setLevel(logging.ERROR)
    vos_logger.addHandler(fh)

    logger.debug("Checking connection to VOSpace ")
    if not os.access(opt.certfile, os.F_OK):
        # setting this to 'blank' instead of None since 'None' implies use the default.
        certfile = ""
    else:
        certfile = os.path.abspath(opt.certfile)

    conn = vos.Connection(vospace_certfile=certfile, vospace_token=opt.token)
    if opt.token:
        readonly = opt.readonly
        logger.debug("Got a token, connections should work")
    elif conn.ws_client.subject.anon:
        readonly = True
        logger.debug("No certificate/token, anonymous connections should work")
    else:
        readonly = opt.readonly
        logger.debug("Got authentication, connections should work")

    root = opt.vospace
    mount = os.path.abspath(opt.mountpoint)
    if opt.cache_dir is None:
        opt.cache_dir = os.path.normpath(os.path.join(
            os.getenv('HOME', '.'), root.replace(":", "_")))
    if not os.access(mount, os.F_OK):
        os.makedirs(mount)
    if platform == "darwin":
        fuse = MyFuse(VOFS(root, opt.cache_dir, opt, conn=conn,
                         cache_limit=opt.cache_limit, cache_nodes=opt.cache_nodes,
                         cache_max_flush_threads=opt.max_flush_threads,
                         secure_get=opt.secure_get),
                    mount,
                    fsname=root,
                    volname=root,
                    nothreads=opt.nothreads,
                    defer_permissions=True,
                    daemon_timeout=DAEMON_TIMEOUT,
                    readonly=readonly,
                    user_allow_other=opt.allow_other,
                    noapplexattr=True,
                    noappledouble=True,
                    debug=opt.foreground,
                    foreground=opt.foreground)
    else:
        fuse = MyFuse(VOFS(root, opt.cache_dir, opt, conn=conn,
                         cache_limit=opt.cache_limit, cache_nodes=opt.cache_nodes,
                         cache_max_flush_threads=opt.max_flush_threads,
                         secure_get=opt.secure_get),
                    mount,
                    fsname=root,
                    nothreads=opt.nothreads,
                    readonly=readonly,
                    user_allow_other=opt.allow_other,
                    foreground=opt.foreground)
