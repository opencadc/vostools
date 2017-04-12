#!python
"""Create a directory (ContainerNode) in the VOSpace repositotry"""
from __future__ import (absolute_import, division, print_function,
                        unicode_literals)

from vos import vos
import time
import os, sys, logging
from vos.commonparser import CommonParser
from vos import vos, version


def vrm():
    usage="""
            vrm vos:/root/node   -- deletes a data node

    Version: %s """ % (version.version)



    parser=CommonParser(usage)

    if len(sys.argv) == 1:
            parser.print_help()
            sys.exit()

    (opt, args)=parser.parse_args()
    parser.process_informational_options()

    logger = logging.getLogger()
    logger.setLevel(parser.log_level)

    try:
        client=vos.Client(vospace_certfile=opt.certfile, vospace_token=opt.token)
    except Exception as e:
        logger.error("Connection failed:  %s" %  (str(e)))
        exit_code = getattr(e, 'errno', -1)
        sys.exit(exit_code)

    for arg in args:
        if arg[0:4]!="vos:":
            logger.error("%s is not a valid VOSpace handle" % (arg))
        try:
            if client.isfile(arg) or client.get_node(arg).islink():
                logger.info("deleting %s" %(arg))
                client.delete(arg)
            elif client.isdir(arg):
                logger.error("%s is a directory" % (arg))
            elif client.access(arg):
                logger.info("deleting link %s" %(arg))
                client.delete(arg)
            else:
                logger.error("%s file not found" % (arg))
                sys.exit(-1)
        except Exception as e:
            import re
            if re.search('NodeLocked',str(e)) != None:
                logger.error("Use vlock to unlock %s before deleting." % (arg))
            exit_code = getattr(e, 'errno', -1)
            logger.error("Failed trying to delete {0}: {1}".format( arg, str(e)))
            sys.exit(exit_code)
