"""set the islocked property of a node.

A node is locked by setting the islocked property. When a node is
locked, it cannot be copied to, moved or deleted. """
from __future__ import (absolute_import, division, print_function,
                        unicode_literals)

import logging
import sys
from vos import vos
from vos.commonparser import CommonParser
import signal


def signal_handler(signum, frame):
    """
    
    :param signum: signal to catch 
    :param frame: frame signal was thrown in
    :raises KeyboardInterrupt
    """
    raise KeyboardInterrupt("SIGNAL {0} from {1} signal handler".format(signum, frame))


def vlock():
    """
    parse sys.argv for arguments, apply, remove or report the lock property tag to arguments. see --help for details.
    :return: 
    """

    signal.signal(signal.SIGINT, signal_handler)

    parser = CommonParser()

    parser.add_option("--lock", action="store_true", help="Lock the node")
    parser.add_option("--unlock", action="store_true", help="unLock the node")

    (opt, args) = parser.parse_args()
    parser.process_informational_options()

    logger = logging.getLogger()
    logger.setLevel(parser.log_level)
    logger.addHandler(logging.StreamHandler())

    exit_code = 0

    try:
        client = vos.Client(vospace_certfile=opt.certfile, vospace_token=opt.token)
        node = client.get_node(args[0])
        if opt.lock or opt.unlock:
            lock = not opt.unlock and opt.lock
            node.is_locked = lock
            client.update(node)
        else:
            print(node.is_locked)
            if not node.is_locked:
                exit_code = -1
    except KeyboardInterrupt:
        exit_code = -1
    except Exception as e:
        logger.error(str(e))
        exit_code = -1

    sys.exit(exit_code)
