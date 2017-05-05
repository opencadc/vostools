#!python
"""Create a directory (ContainerNode) in the VOSpace repositotry"""
from __future__ import (absolute_import, division, print_function,
                        unicode_literals)

from vos import vos
import sys
import logging
from vos.commonparser import CommonParser
import signal
from vos import version

def signal_handler(signum, frame):
    raise KeyboardInterrupt("SIGINT signal handler")

def vmv():
    signal.signal(signal.SIGINT, signal_handler)
    usage = """
    vmv vos:/root/node vos:/root/newNode   -- move node to newNode, if newNode is a container then moving node into newNode.

Version: %s """ % (version.version)

    parser = CommonParser(usage)

    if len(sys.argv) == 1:
        parser.print_help()
        sys.exit()

    (opt, args) = parser.parse_args()
    parser.process_informational_options()

    logger = logging.getLogger()
    logger.setLevel(parser.log_level)

    if len(args) != 2:
        parser.error("You must supply a source and destination")

    try:
        client = vos.Client(vospace_certfile=opt.certfile,
                            vospace_token=opt.token)
    except Exception as e:
        logger.error("Connection failed:  %s" % (str(e)))
        sys.exit(e.__getattribute__('errno',-1))

    source = args[0]
    dest = args[1]

    try:
        logger.info("%s -> %s" % ( source, dest))
        client.move(source, dest)
    except KeyboardInterrupt:
        logger.error("Received keyboard interrupt. Execution aborted...\n")
        sys.exit(-1)
    except Exception as e:
        import re
        if re.search('NodeLocked', str(e)) != None:
            logger.error('Use vlock to unlock nodes before moving.')
        logger.error(str(e))
        sys.exit(-1)

