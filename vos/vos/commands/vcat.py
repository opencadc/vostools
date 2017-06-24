"""cat VOSpace DataNode to stdout"""
from __future__ import (absolute_import, division, print_function,
                        unicode_literals)
import sys
import logging
from vos.commonparser import CommonParser
from vos import vos


def _cat(uri, cert_filename=None):
    """Cat out the given uri stored in VOSpace.
    
    :param uri: the VOSpace URI that will be piped to stdout.
    :type uri: str
    :param cert_filename: filename of the PEM certificate used to gain access.
    :type cert_filename: str
    """

    fh = None
    try:
        if uri[0:4] == "vos:":
            fh = vos.Client(vospace_certfile=cert_filename).open(uri, view='data')
        else:
            fh = open(uri, str("r"))
        sys.stdout.write(fh.read())
    finally:
        if fh:
            fh.close()


def vcat():
    """cat a given file to stdout.
    
    this method is a command line tool.
    """
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
