#!python
"""remove a vospace data node, fails if container node or node is locked."""
import logging
from ..commonparser import set_logging_level_from_args, exit_on_exception, \
    CommonParser
from .. import vos

DESCRIPTION = """remove a vospace data node; fails if container node or node is locked.

eg. vrm vos:/root/node   -- deletes a data node"""


def vrm():
    parser = CommonParser(description=DESCRIPTION)
    parser.add_argument('node',
                        help='dataNode or linkNode to delete from VOSpace',
                        nargs='+')

    args = parser.parse_args()
    set_logging_level_from_args(args)

    try:
        client = vos.Client(vospace_certfile=args.certfile,
                            vospace_token=args.token)
        for node in args.node:
            if not node.startswith('vos:'):
                raise Exception(
                    '{} is not a valid VOSpace handle'.format(node))
            if not node.endswith('/'):
                if client.get_node(node).islink():
                    logging.info('deleting link {}'.format(node))
                    client.delete(node)
                elif client.isfile(node):
                    logging.info('deleting {}'.format(node))
                    client.delete(node)
            elif client.isdir(node):
                raise Exception('{} is a directory'.format(node))
            else:
                raise Exception('{} is not a directory'.format(node))

    except Exception as ex:
        exit_on_exception(ex)


vrm.__doc__ = DESCRIPTION
