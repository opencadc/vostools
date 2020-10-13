"""
A common commandline parser for the VOS command line tool set.
"""
import logging
import argparse
import os
import signal
import sys
import traceback
from .version import version
from .vosconfig import _CONFIG_PATH


def signal_handler(signum, frame):
    """Exit without calling cleanup handlers, flushing stdio buffers, etc. """
    logging.info('Received signal {}. Exiting.'.format(signum))
    os._exit(signum)


signal.signal(signal.SIGINT, signal_handler)    # Ctrl-C
# Disabled due to unexpected SIGPIPE signals that do not warn the user
# signal.signal(signal.SIGPIPE, signal_handler)   # Pipe gone (head, more etc)


def exit_on_exception(ex, message=None):
    """
    Exit program due to an exception, print the exception and exit with error
    code.
    :param ex:
    :param message: error message to display
    :return:
    """
    # Note: this could probably be updated to use an appropriate logging
    # handler instead of writing to stderr
    if message:
        sys.stderr.write('ERROR:: {}\n'.format(message))
    else:
        sys.stderr.write('ERROR:: {}\n'.format(str(ex)))
    tb = traceback.format_exc()
    logging.debug(tb)
    sys.exit(getattr(ex, 'errno', -1)) if getattr(ex, 'errno',
                                                  -1) else sys.exit(-1)


def set_logging_level_from_args(args):
    """Display version, set logging verbosity"""

    # Logger verbosity
    if args.debug:
        args.log_level = logging.DEBUG
    elif args.verbose:
        args.log_level = logging.INFO
    elif args.warning:
        args.log_level = logging.WARNING
    else:
        args.log_level = logging.ERROR

    log_format = "%(levelname)s %(module)s %(message)s"
    if args.log_level < logging.INFO:
        log_format = (
            "%(levelname)s %(asctime)s %(thread)d vos-" + str(version) +
            " %(module)s.%(funcName)s.%(lineno)d %(message)s")
    logging.basicConfig(format=log_format, level=args.log_level)
    logger = logging.getLogger('root')
    logger.setLevel(args.log_level)

    if args.vos_debug:
        logger = logging.getLogger('vos')
        logger.setLevel(logging.DEBUG)

    if sys.version_info[1] > 6:
        logging.getLogger().addHandler(logging.NullHandler())


class CommonParser(argparse.ArgumentParser):
    """Class to hold and parse common command-line arguments for vos clients"""

    add_option = argparse.ArgumentParser.add_argument

    def __init__(self, *args, **kwargs):
        # call the parent constructor
        if os.path.isfile(_CONFIG_PATH):
            epilog = 'Default service settings in {}.'.format(_CONFIG_PATH)
        else:
            epilog = ''
        super(CommonParser, self).__init__(
            *args,
            formatter_class=argparse.RawDescriptionHelpFormatter,
            epilog=epilog,
            **kwargs)
        # inherit the VOS client version
        self.version = version
        self.log_level = logging.ERROR

        # now add on the common parameters
        self.add_argument(
            "--certfile",
            help="filename of your CADC X509 authentication certificate",
            default=os.path.join(os.getenv("HOME", "."), ".ssl/cadcproxy.pem"))
        self.add_argument(
            "--token",
            help="authentication token string (alternative to certfile)",
            default=None)
        self.add_argument("--version", action="version",
                          version=version)
        self.add_argument("-d", "--debug", action="store_true", default=False,
                          help="print on command debug messages.")
        self.add_argument("--vos-debug", action="store_true",
                          help="Print on vos debug messages.")
        self.add_argument("-v", "--verbose", action="store_true",
                          default=False,
                          help="print verbose messages")
        self.add_argument("-w", "--warning", action="store_true",
                          default=False,
                          help="print warning messages only")


URI_DESCRIPTION = \
    'Remote resources are identified either by their full ' \
    'URIs (ivo://cadc.nrc.ca/vault) or by a user configured name in the ' \
    'config file.\n' \
    'Some names are reserved:\n' \
    '     vos for ivo:/cadc.nrc.ca/vault and\n' \
    '     arc for ivo:cadc.nc.ca/arbutus-cavern.\n' \
    'Thus, arc:somepath is a shorter version of the full URI ' \
    'vos://cadc.nrc.ca~arbutus-cavern/somepath'
