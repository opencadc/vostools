"""Move from a VOSpace Node to a new location or rename the Node"""
from __future__ import (absolute_import, division, print_function,
                        unicode_literals)
from .. import vos
import logging
from ..commonparser import CommonParser, exit_on_exception, \
    set_logging_level_from_args

DESCRIPTION = """
move node to newNode, if newNode is a container  then move node into newNode.

e.g. vmv vos:/root/node vos:/root/newNode   --

"""


def vmv():
    parser = CommonParser(description=DESCRIPTION)
    parser.add_argument("source", help="The name of the node to move.",
                        action='store')
    parser.add_argument("destination",
                        help="VOSpace destination to move source to.")

    args = parser.parse_args()
    set_logging_level_from_args(args)

    try:
        client = vos.Client(vospace_certfile=args.certfile,
                            vospace_token=args.token)
        source = args.source
        dest = args.destination
        logging.info("{} -> {}".format(source, dest))
        client.move(source, dest)
    except Exception as ex:
        exit_on_exception(ex)


vmv.__doc__ = DESCRIPTION
