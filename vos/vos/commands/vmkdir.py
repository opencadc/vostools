#!python
"""Create a directory (ContainerNode) in the VOSpace repository"""

import os
import logging
from ..commonparser import CommonParser, set_logging_level_from_args, \
    exit_on_exception, URI_DESCRIPTION
from .. import vos

DESCRIPTION = """creates a new VOSpace ContainerNode (aka directory).

{}

eg vmkdir vos:RootNode/NewContainer""".format(URI_DESCRIPTION)


def vmkdir():
    parser = CommonParser(description=DESCRIPTION)
    parser.add_argument('container_node', action='store',
                        help='Name of the container node to craete.')
    parser.add_argument("-p", action="store_true",
                        help="Create intermediate directories as required.")

    args = parser.parse_args()

    set_logging_level_from_args(args)

    logging.info(
        "Creating ContainerNode (directory) {}".format(args.container_node))

    try:
        this_dir = args.container_node
        client = vos.Client(
            vospace_certfile=args.certfile,
            vospace_token=args.token)

        dir_names = []
        if args.p:
            while not client.access(this_dir):
                dir_names.append(os.path.basename(this_dir))
                this_dir = os.path.dirname(this_dir)
            while len(dir_names) > 0:
                this_dir = os.path.join(this_dir, dir_names.pop())
                client.mkdir(this_dir)
        else:
            client.mkdir(this_dir)

    except Exception as ex:
        exit_on_exception(ex)


vmkdir.__doc__ = DESCRIPTION
