"""Move from a VOSpace Node to a new location or rename the Node"""
from __future__ import (absolute_import, division, print_function,
                        unicode_literals)
from .. import vos
import logging
from ..commonparser import CommonParser, exit_on_exception, \
    set_logging_level_from_args, get_scheme

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
        source = args.source
        dest = args.destination
        if not vos.is_remote_file(source):
            raise ValueError('Source {} is not a remote node'.format(source))
        if not vos.is_remote_file(dest):
            raise ValueError(
                'Destination {} is not a remote node'.format(dest))
        scheme1 = get_scheme(source)
        scheme2 = get_scheme(dest)
        if scheme1 != scheme2:
            raise ValueError('Move between services not supported')
        client = vos.Client(
            resource_id=vos.vos_config.get_resource_id(scheme1),
            vospace_certfile=args.certfile,
            vospace_token=args.token)
        logging.info("{} -> {}".format(source, dest))
        client.move(source, dest)
    except Exception as ex:
        exit_on_exception(ex)


vmv.__doc__ = DESCRIPTION
