#!python
"""set read/write properties of a node.

"""
from __future__ import (absolute_import, division, print_function,
                        unicode_literals)

from vos.commonparser import CommonParser
import signal
from vos import vos, version
import logging
import sys
import os


def signal_handler(signum, frame):
    raise KeyboardInterrupt("SIGNAL {0} from {1} signal handler".format(signum, frame))


def vchmod():

    signal.signal(signal.SIGINT, signal_handler)
    usage = """vchmod mode node [read/write group names in quotes - maximum of 4 each]

    Accepted modes: (og|go|o|g)[+-=](rw|wr|r\w)

    Sets read/write properties of a node.
    
    Version: %s """ % version.version

    parser = CommonParser(usage)

    parser.add_option("-R", "--recursive", action='store_const', const=True,
                      help="Recursive set read/write properties")

    if len(sys.argv) == 1:
        parser.print_help()
        sys.exit()

    (opt, args) = parser.parse_args()
    parser.process_informational_options()

    logger = logging.getLogger()

    if len(args) < 2:
        parser.error("Requires mode and node arguments")
    cmdArgs = dict(zip(['mode', 'node'], args[0:2]))
    groupNames = args[2:]
    import re

    mode = re.match(r"(?P<who>^og|^go|^o|^g)(?P<op>[\+\-=])(?P<what>rw$|wr$|r$|w$)", cmdArgs['mode'])
    if not mode:
        parser.print_help()
        logger.error("\n\nAccepted modes: (og|go|o|g)[+-=](rw|wr|r\w)\n\n")
        sys.exit(-1)

    props = {}
    if 'o' in mode.group('who'):
        if not mode.group('what') == 'r':  # read only
            parser.print_help()
            logger.error("\n\nPublic access is readonly, no public write available \n\n")
            sys.exit(-1)
        if mode.group('op') == '-':
            props['ispublic'] = 'false'
        else:
            props['ispublic'] = 'true'
    if 'g' in mode.group('who'):
        if '-' == mode.group('op'):
            if not len(groupNames) == 0:
                parser.print_help()
                logger.error("\n\nNames of groups not required with remove permission\n\n")
                sys.exit(-1)
            if 'r' in mode.group('what'):
                props['readgroup'] = None
            if "w" in mode.group('what'):
                props['writegroup'] = None
        else:
            if not len(groupNames) == len(mode.group('what')):
                parser.print_help()
                logger.error("\n\n%s group names required for %s\n\n" %
                             (len(mode.group('what')), mode.group('what')))
                sys.exit(-1)
            if mode.group('what').find('r') > -1:
                # remove duplicate whitespaces
                rgroups = " ".join(groupNames[mode.group('what').find('r')].split())
                props['readgroup'] = (vos.CADC_GMS_PREFIX +
                                      rgroups.replace(" ", " " + vos.CADC_GMS_PREFIX))
            if mode.group('what').find('w') > -1:
                wgroups = " ".join(groupNames[mode.group('what').find('w')].split())
                props['writegroup'] = (vos.CADC_GMS_PREFIX +
                                       wgroups.replace(" ", " " + vos.CADC_GMS_PREFIX))
    logger.debug("Properties: %s" % (str(props)))
    logger.debug("Node: %s" % cmdArgs['node'])
    returnCode = 0
    try:
        client = vos.Client(vospace_certfile=opt.certfile,
                            vospace_token=opt.token)
        node = client.get_node(cmdArgs['node'])
        if opt.recursive:
            node.props.clear()
            node.clear_properties()
            # del node.node.findall(vos.Node.PROPERTIES)[0:]
        if 'readgroup' in props:
            node.chrgrp(props['readgroup'])
        if 'writegroup' in props:
            node.chwgrp(props['writegroup'])
        if 'ispublic' in props:
            node.set_public(props['ispublic'])
        logger.debug("Node: {0}".format(node))
        returnCode = client.update(node, opt.recursive)
    except KeyboardInterrupt as ke:
        logger.error("Received keyboard interrupt. Execution aborted...\n")
        sys.exit(getattr(ke, 'errno', -1))
    except Exception as e:
        logger.error('Error {}: '.format(str(e)))
        sys.exit(getattr(e, 'errno', -1))
    sys.exit(returnCode)
