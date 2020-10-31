"""link one VOSpace Node to another."""
from __future__ import (absolute_import, division, print_function,
                        unicode_literals)
from ..commonparser import CommonParser, set_logging_level_from_args, \
    exit_on_exception, URI_DESCRIPTION
from .. import vos
from argparse import ArgumentError

DESCRIPTION = """
vln creates a new VOSpace entry (LinkNode, target) that has the same modes as
the source Node. It is useful for maintaining multiple copies of a Node in
many places at once without using up storage for the ''copies''; instead, a
link ''points'' to the original copy.

Only symbolic links are supported.

{}

vln vos:VOSpaceSource vos:VOSpaceTarget


examples:

    vln vos:vospace/junk.txt vos:vospace/linkToJunk.txt
    vln vos:vospace/directory vos:vospace/linkToDirectory
    vln http://external.data.source vos:vospace/linkToExternalDataSource

""".format(URI_DESCRIPTION)


def vln():
    parser = CommonParser(description=DESCRIPTION)
    parser.add_argument('source', help="location that link will point to.")
    parser.add_argument('target', help="location of the LinkNode")

    try:
        opt = parser.parse_args()
        set_logging_level_from_args(opt)

        if not vos.is_remote_file(opt.source) or \
                not vos.is_remote_file(opt.target):
            raise ArgumentError(
                None,
                "source must be vos node or http url, target must be vos node")
        client = vos.Client(
            vospace_certfile=opt.certfile,
            vospace_token=opt.token)
        client.link(opt.source, opt.target)
    except ArgumentError as ex:
        parser.print_usage()
        exit_on_exception(ex)
    except Exception as ex:
        exit_on_exception(ex)


vln.__doc__ = DESCRIPTION
