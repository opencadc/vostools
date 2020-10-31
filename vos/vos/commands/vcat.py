"""cat VOSpace DataNode to stdout"""
from __future__ import (absolute_import, division, print_function,
                        unicode_literals)
import sys
import logging
from ..vos import Client, is_remote_file
from ..commonparser import CommonParser, set_logging_level_from_args, \
    exit_on_exception, URI_DESCRIPTION


def _cat(uri, cert_filename=None, head=None):
    """Cat out the given uri stored in VOSpace.

    :param uri: the VOSpace URI that will be piped to stdout.
    :type uri: basestring
    :param cert_filename: filename of the PEM certificate used to gain access.
    :type cert_filename: basestring
    """

    fh = None
    try:
        if is_remote_file(uri):
            view = head and 'header' or 'data'
            fh = Client(vospace_certfile=cert_filename).open(uri, view=view)
            sys.stdout.write(fh.read(return_response=True).text)
            sys.stdout.write('\n\n')
        else:
            fh = open(uri, str("r"))
            sys.stdout.write(fh.read())
    finally:
        if fh:
            fh.close()


DESCRIPTION = """Write the content of source (eg. vos:Node/filename) to stdout.

{}

Accepts cutout syntax for FITS files; see vcp --help for syntax details
""".format(URI_DESCRIPTION)


def vcat():
    parser = CommonParser(description=DESCRIPTION)
    parser.add_argument("source", help="source to cat to stdout out.",
                        nargs="+")
    parser.add_argument("--head", action="store_true",
                        help="retrieve only the headers of file from vospace. "
                             "Might return an error if server does not "
                             "support operation on a given file type.")
    parser.add_argument("-q",
                        help="run quietly, exit on error without message",
                        action="store_true")

    args = parser.parse_args()
    set_logging_level_from_args(args)

    logger = logging.getLogger()

    exit_code = 0

    try:
        for uri in args.source:
            if not uri.startswith('vos') and args.head:
                logger.error('FITS header not supported for local source {}'.
                             format(uri))
                exit_code = 1
                continue
            try:
                _cat(uri, cert_filename=args.certfile, head=args.head)
            except Exception as e:
                exit_code = getattr(e, 'errno', -1)
                if not args.q:
                    logger.error(str(e))
    except KeyboardInterrupt as ke:
        exit_on_exception(ke)

    sys.exit(exit_code)


vcat.__doc__ = DESCRIPTION
