#!python
"""remove a vospace data node, fails if container node or node is locked."""
import logging
from ..commonparser import set_logging_level_from_args, exit_on_exception, \
    CommonParser
from .. import vos
from cadcutils.util import utils

DESCRIPTION = """remove a file or a vospace data node; fails if container
node or node is locked.

eg. vrm vos:/root/node   -- deletes a data node"""


def delete_nodes(args):
    client = vos.Client(vospace_certfile=args.certfile,
                        vospace_token=args.token)
    for node in args.source:
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


def delete_files(args):
    # TODO: alinga-- Change Client to StorageClient when it is available
    client = vos.Client(storage_certfile=args.certfile,
                        storage_token=args.token,
                        resource_id=args.resource_id)
    for file in args.source:
        if file.startswith('vos:'):
            raise Exception(
                '{} is not a valid storage handle'.format(file))
        if file.endswith('/'):
            raise Exception(
                '{} is not a valid storage file handle'.format(file))
        elif client.isdir(file):
            raise Exception('{} is a directory'.format(file))
        elif utils.is_uri_string(file):
            logging.info('deleting {}'.format(file))
            client.delete(file)


def vrm():
    parser = CommonParser(description=DESCRIPTION)
    parser.add_argument(
        "--resource-id", default=None,
        help="resource ID of the Storage Inventory service to be used")
    parser.add_argument(
        'source',
        help='file, dataNode or linkNode to delete from VOSpace',
        nargs='+')

    args = parser.parse_args()
    set_logging_level_from_args(args)

    try:
        if args.resource_id is None:
            delete_nodes(args)
        else:
            delete_files(args)

    except Exception as ex:
        exit_on_exception(ex)


vrm.__doc__ = DESCRIPTION
