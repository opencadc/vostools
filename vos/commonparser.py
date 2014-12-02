
import logging
import optparse
import os
import sys
import vos
from __version__ import version

class CommonParser(optparse.OptionParser):
    """Class to hold and parse common command-line arguments for vos clients"""

    def __init__(self, *args, **kwargs):
        # call the parent constructor
        optparse.OptionParser.__init__(self, *args, **kwargs)

        # inherit the VOS client version
        self.version = version

        # now add on the common parameters
        self.add_option("--certfile",
                        help="location of your CADC security certificate file",
                        default=os.path.join(os.getenv("HOME", "."),
                                             ".ssl/cadcproxy.pem"))
        self.add_option("--token",
                        help="token string (alternative to certfile)",
                        default=None)
        self.add_option("--version", action="store_true",
                        default=False,
                        help="Print the version (%s)" % version)
        self.add_option("-d", "--debug", action="store_true", default=False,
                        help="Print debug level log messages")
        self.add_option("-v", "--verbose", action="store_true", default=False,
                        help="Print verbose level log messages")
        self.add_option("-w", "--warning", action="store_true", default=False,
                        help="Print warning level log messages")

    def process_informational_options(self):
        """Display version, set logging verbosity"""
        (opt, args) = self.parse_args()

        if opt.version:
            self.print_version()
            sys.exit(0)

        # Logger verbosity
        if opt.verbose:
            self.log_level = logging.INFO
        elif opt.debug:
            self.log_level = logging.DEBUG
        elif opt.warning:
            self.log_level = logging.WARNING
        else:
            self.log_level = logging.ERROR
