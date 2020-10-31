"""Tags are annotations on VOSpace Nodes.  This module provides set/read/(list)
functions for property(ies) of a node.

The tag system is meant to allow tags, in addition to the standard node
properties. """
from __future__ import (absolute_import, division, print_function,
                        unicode_literals)
import pprint
from ..commonparser import CommonParser, set_logging_level_from_args, \
    exit_on_exception, URI_DESCRIPTION
from .. import vos

DESCRIPTION = """provides set/read/(list) functions for property(ies) of a
node.

{}

Properties are attributes on the node.  There can be users attributes or
system attributes.

Only user attributes can be set.

examples:

set at property:  vtag vos:RootNode/MyImage.fits quality=good
read a property:  vtag vos:RootNode/MyImage.fits quality
delete a property: vtag vos:RootNode/MyImage.fits quality=
                   or
                   vtag vos:RootNode/MyImage.fits quality --remove
list all property values:  vtag vos:RootNode/MyImage.fits

""".format(URI_DESCRIPTION)


def vtag():
    parser = CommonParser(description=DESCRIPTION)
    parser.add_argument('node', help='Node to set property (tag/attribute) on')
    parser.add_argument(
        'property',
        help="Property whose value will be read, set or deleted",
        nargs="*")
    parser.add_option('--remove', action="store_true",
                      help='remove the listed property')

    opt = parser.parse_args()
    args = opt
    set_logging_level_from_args(args)

    # the node should be the first argument, the rest should contain
    # the key/val pairs
    node = args.node

    props = []
    if args.remove:
        # remove signified by blank value in key=value listing
        for prop in args.property:
            if '=' not in prop:
                prop += "="
            props.append(prop)
    else:
        props = args.property

    try:
        client = vos.Client(
            vospace_certfile=opt.certfile,
            vospace_token=opt.token)
        node = client.get_node(node)
        if len(props) == 0:
            # print all properties
            pprint.pprint(node.props)

        changed = False
        for prop in props:
            prop = prop.split('=')
            if len(prop) == 1:
                # get one property
                pprint.pprint(node.props.get(prop[0], None))
            elif len(prop) == 2:
                # update properties
                key, value = prop
                if len(value) == 0:
                    value = None
                if value != node.props.get(key, None):
                    node.props[key] = value
                    changed = True
            else:
                raise ValueError(
                    "Illegal keyword of value character ('=') used: %s" % (
                        '='.join(prop)))

        if changed:
            client.add_props(node)
    except Exception as ex:
        exit_on_exception(ex)


vtag.__doc__ = DESCRIPTION
