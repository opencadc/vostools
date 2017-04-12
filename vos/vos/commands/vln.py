#!python

"""vls:  list the contents of a voSpace"""
from __future__ import (absolute_import, division, print_function,
                        unicode_literals)

from vos.commonparser import CommonParser
import logging
import sys
from vos import vos

def vln():
    usage = """
      vln vos:VOSpaceSource vos:VOSpaceTarget


    examples:

    vln vos:vospace/junk.txt vos:vospace/linkToJunk.txt
    vln vos:vospace/directory vos:vospace/linkToDirectory
    vln http://external.data.source vos:vospace/linkToExternalDataSource

    """

    parser = CommonParser(usage)

    if len(sys.argv) == 1:
        parser.print_help()
        sys.exit()

    (opt, args) = parser.parse_args()
    parser.process_informational_options()

    logger = logging.getLogger()
    logger.setLevel(parser.log_level)
    logger.addHandler(logging.StreamHandler())

    if len(args) != 2:
        parser.error("You must specify a source file and a target file")
        sys.exit(-1)

    if args[1][0:4] != "vos:":
        parser.error("The target to source must be in vospace")
        sys.exit(-1)

    logger.debug("Connecting to vospace using certificate %s" % opt.certfile)

    try:
        client = vos.Client(vospace_certfile=opt.certfile, vospace_token=opt.token)
    except Exception as e:
        logger.error("VOS connection failed:  {0}".format(e))
        sys.exit(-1)

    try:
        client.link(args[0], args[1])
    except Exception as e:
        logger.error("Failed to make link from %s to %s" % (args[0], args[1]))
        logger.error(getattr(e, 'strerror', 'Unknown Error'))
        sys.exit(-1)
