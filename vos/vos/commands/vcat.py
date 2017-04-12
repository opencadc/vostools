"""cat files from vospace to stdout"""

from __future__ import (absolute_import, division, print_function,
                        unicode_literals)
from optparse import OptionParser
import sys
import os
import logging
from vos.commonparser import CommonParser
from vos import vos, version


def _cat(vospace_uri, cert_filename=None):
    """Cat out the given uri."""

    fh = None
    try:
        if vospace_uri[0:4] == "vos:":
            fh = vos.Client(vospace_certfile=cert_filename).open(vospace_uri, view='data')
        else:
            fh = open(vospace_uri, 'r')
        sys.stdout.write(fh.read())
    finally:
        if fh:
            fh.close()


def vcat():
    usage = "%prog [options] vos:VOSpace/node_name"
    description = "Writes the content of vos:VOSpace/node_name to stdout."

    parser = CommonParser(usage, description=description)
    parser.add_option("-q", help="run quietly, exit on error without message", action="store_true")

    (opt, args) = parser.parse_args()
    parser.process_informational_options()

    if not len(args) > 0:
        parser.error("no argument given")

    logger = logging.getLogger()

    exit_code = 0

    for uri in args:
        try:
            _cat(uri, cert_filename=opt.certfile)
        except Exception as e:
            exit_code = getattr(e, 'errno', -1)
            if not opt.q:
                logger.error(str(e))

    sys.exit(exit_code)
