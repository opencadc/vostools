"""Move from a VOSpace Node to a new location or rename the Node"""
from __future__ import (absolute_import, division, print_function,
                        unicode_literals)
from .. import vos
import logging
from ..commonparser import CommonParser, exit_on_exception, \
    set_logging_level_from_args, URI_DESCRIPTION
from six.moves.urllib.parse import urlparse

DESCRIPTION = """
move node to newNode, if newNode is a container  then move node into newNode.

{}

e.g. vmv vos:/root/node vos:/root/newNode   --

""".format(URI_DESCRIPTION)


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
        if urlparse(source).scheme != urlparse(dest).scheme:
            raise ValueError('Move between services not supported')
        client = vos.Client(
            vospace_certfile=args.certfile,
            vospace_token=args.token)
        logging.info("{} -> {}".format(source, dest))
        client.move(source, dest)
    except Exception as ex:
        exit_on_exception(ex)


vmv.__doc__ = DESCRIPTION
