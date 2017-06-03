#!python
"""set/read/(list) a property(ies) if a node.

The tag system is meant to allow tags, in addition to the standard
nodode properties. """
from __future__ import (absolute_import, division, print_function,
                        unicode_literals)

import pprint
from vos import version
from vos.commonparser import CommonParser
import logging, sys
from vos import vos
## handle interupts nicely
import signal

def signal_handler(signal, frame):
    raise KeyboardInterrupt("SIGINT signal handler")


def vtag():
    usage = """
      vtag [options] node [key[=value] [key[=value] ...]]

          Version: %s """ % (version.version)
    signal.signal(signal.SIGINT, signal_handler)

    parser = CommonParser(usage)

    parser.add_option('--remove', action="store_true", help='remove the listed property')

# Not yet supported
#    parser.add_option("-R", "--recursive", action='store_const', const=True,
#                        help="Recursive set read/write properties")
 

    (opt, args) = parser.parse_args()
    parser.process_informational_options()

    if len(args) == 0:
        parser.error("no vospace supplied")

    logger = logging.getLogger()
    logger.setLevel(parser.log_level)
    logger.addHandler(logging.StreamHandler())

    ## the node should be the first argument, the rest should contain the key/val pairs
    node = args.pop(0)

    props = []
    if opt.remove:
        ## remove signified by blank value in key=value listing
        for prop in args:
            if '=' not in prop:
                prop += "="
            props.append(prop)
    else:
        props = args

    try:
        """Lists/sets values of node properties based on context (length of props).

        node: vos.vos.Node object
        props: dictionary of properties to set.  If dict is zero length, list them all.

        """
        client = vos.Client(vospace_certfile=opt.certfile, vospace_token=opt.token)
        node = client.get_node(node)
        if len(props) == 0:
            # print all properties
            for key in node.props:
                print(key, node.props[key])
            return pprint.pprint(node.props)

        changed = False
        for prop in props:
            prop = prop.split('=')
            if len(prop) == 1:
                # get one property
                logger.debug("just print this one out %s" % (prop[0]))
                pprint.pprint(node.props.get(prop[0], None))
            elif len(prop) == 2:
                # update properties
                key, value = prop
                logger.debug("%s %s" % (len(key), len(value)))
                if len(value) == 0:
                    value = None
                logger.debug("%s: %s -> %s" % (key, node.props.get(key, None), value))
                if value != node.props.get(key, None):
                    node.props[key] = value
                    changed = True
            else:
                raise ValueError("Illigal keyword of value character ('=') used: %s" % ('='.join(prop)))

        if changed:
            client.add_props(node)
        return 0
    except KeyboardInterrupt:
        logger.debug("Received keyboard interrupt. Execution aborted...\n")
        pass
    except Exception as e:
        logger.error(str(e))

    sys.exit(-1)

