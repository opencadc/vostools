"""set the islocked property of a node.

A node is locked by setting the islocked property. When a node is
locked, it cannot be copied to, moved or deleted. """
from __future__ import (absolute_import, division, print_function,
                        unicode_literals)

import logging
import sys
from .. import vos
from ..commonparser import CommonParser, exit_on_exception, \
    set_logging_level_from_args, URI_DESCRIPTION

DESCRIPTION = """Places/Removes a write lock on a VOSpace Node or reports lock
status if no action requested.

{}

""".format(URI_DESCRIPTION)


def vlock():
    parser = CommonParser(description=DESCRIPTION)
    parser.add_argument(
        'node',
        help="node to request / view lock on. (eg. vos:RootNode/File.txt")
    action = parser.add_mutually_exclusive_group()
    action.add_argument("--lock", action="store_true", help="Lock the node")
    action.add_argument("--unlock", action="store_true",
                        help="unLock the node")

    try:
        opt = parser.parse_args()
        set_logging_level_from_args(opt)
        client = vos.Client(
            vospace_certfile=opt.certfile,
            vospace_token=opt.token)
        node = client.get_node(opt.node)
        if opt.lock or opt.unlock:
            lock = not opt.unlock and opt.lock
            node.is_locked = lock
            client.update(node)
        else:
            logging.info(node.is_locked)
            if not node.is_locked:
                sys.exit(-1)
    except Exception as ex:
        exit_on_exception(ex)


vlock.__doc__ = DESCRIPTION
