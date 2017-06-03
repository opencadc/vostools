#!python
"""Create a directory (ContainerNode) in the VOSpace repositotry"""
from __future__ import (absolute_import, division, print_function,
                        unicode_literals)

from vos.commonparser import CommonParser
import time
import os, sys, logging
from optparse import OptionParser
from vos import vos, version

def vrmdir():
    usage="""
            vrmdir vos:/root/node   -- deletes a container node

    Version: %s """ % (version.version)


    parser = CommonParser(usage)

    if len(sys.argv) == 1:
            parser.print_help()
            sys.exit()

    (opt, args) = parser.parse_args()
    parser.process_informational_options()

    try:
        client=vos.Client(vospace_certfile=opt.certfile,
                          vospace_token=opt.token)
    except Exception as e:
        logging.error("Connection failed:  %s" %  (str(e)))
        sys.exit(e.__getattribute__('errno', -1))

    try:
       for arg in args:
          if arg[0:4]!="vos:":
              logging.error("%s is not a valid VOSpace handle" % (arg))
              sys.exit(-1)
          if client.isdir(arg):
              logging.info("deleting %s" %(arg))
              client.delete(arg)
          elif client.isfile(arg):
              logging.error("%s is a file" % (arg))
              sys.exit(-1)
          else:
              logging.error("%s file not found" % (arg))
              sys.exit(-1)
    except Exception as e:
        import re
        if re.search('NodeLocked', str(e)) != None:
           logging.error("Use vlock to unlock %s before removing." %(arg))
        logging.error("Connection failed:  %s" %  (str(e)))
        sys.exit(-1)
