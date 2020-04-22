"""Delete a VOSpace ContainerNode (aka directory)"""
from ..commonparser import CommonParser, set_logging_level_from_args, \
    exit_on_exception
import logging
from vos import vos

DESCRIPTION = """deletes a VOSpace container node (aka directory)

e.g.   vrmdir vos:Root/MyContainer

CAUTION:  The container need not be empty."""


def vrmdir():
    parser = CommonParser(description=DESCRIPTION)
    parser.add_argument('nodes', help="Container nodes to delete from VOSpace",
                        nargs='+')

    args = parser.parse_args()

    set_logging_level_from_args(args)

    try:
        client = vos.Client(vospace_certfile=args.certfile,
                            vospace_token=args.token)
        for container_node in args.nodes:
            if not container_node.startswith("vos:"):
                raise ValueError(
                    "{} is not a valid VOSpace handle".format(container_node))
            if client.isdir(container_node):
                logging.info("deleting {}".format(container_node))
                client.delete(container_node)
            else:
                raise ValueError(
                    "{} is a not a container node".format(container_node))
    except Exception as ex:
        exit_on_exception(ex)


vrmdir.__doc__ = DESCRIPTION
