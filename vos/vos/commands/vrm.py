#!python
"""remove a vospace data node, fails if container node or node is locked."""
import logging
import six.moves.urllib.parse as urllibparse
from ..commonparser import set_logging_level_from_args, exit_on_exception, \
    CommonParser
from .. import vos, storage_inventory

urlparse = urllibparse.urlparse

DESCRIPTION = """remove a file or a vospace data node; fails if container
node or node is locked.

eg. vrm vos:/root/node   -- deletes a data node"""


def is_uri_string(id_str):
    """
    Takes a file identifier string and determines if it specifies a URI.
    At time of writing, wild card in path is considered invalid.

    :param id_str A identifier string
    :return True if we can use the identifier string to create a URI instance,
            False if the identifier string does not start with '<scheme>:'
    """

    if id_str is None:
        raise ValueError('Missing identifier: {}'.format(id_str))

    url = urlparse(id_str)
    if (len(url.scheme) == 0):
        # missing scheme
        return False
    elif (len(url.netloc) == 0) or (len(url.path) == 0) or \
            ('*' in url.path):
        raise ValueError('Invalid URL: {}'.format(id_str))
    else:
        # a valid url
        return True


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
    client = storage_inventory.Client(args.resource_id,
                                      certfile=args.certfile,
                                      token=args.token)
    for file in args.source:
        if file.startswith('vos:'):
            raise Exception(
                '{} is not a valid storage handle'.format(file))
        if file.endswith('/'):
            raise Exception(
                '{} is not a valid storage file handle'.format(file))
        elif client.isdir(file):
            raise Exception('{} is a directory'.format(file))
        elif is_uri_string(file):
            logging.info('deleting {}'.format(file))
            client.delete(file)
        else:
            raise Exception(
                '{} is not a valid storage file handle'.format(file))


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
