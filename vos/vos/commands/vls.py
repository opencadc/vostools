"""Lists information about a VOSpace DataNode or the contents of a
ContainerNode."""
from __future__ import (absolute_import, division, print_function,
                        unicode_literals)
import logging
import math
from ..commonparser import CommonParser, set_logging_level_from_args, \
    exit_on_exception, URI_DESCRIPTION
import sys
import time
from .. import vos
from argparse import ArgumentError

# this is a pointer to the module object instance itself.
this = sys.modules[__name__]

# we can explicitly make assignments on it
this.human = False

__all__ = ['vls']


def size_format(size):
    """Format a size value for listing"""
    try:
        size = float(size)
    except Exception as ex:
        logging.debug(str(ex))
        size = 0.0
    if this.human:
        size_unit = ['B', 'K', 'M', 'G', 'T']
        # noinspection PyBroadException
        try:
            length = float(size)
            scale = int(math.log(length) / math.log(1024))
            length = "%.0f%s" % (length / (1024.0 ** scale), size_unit[scale])
        except Exception:
            length = str(int(size))
    else:
        length = str(int(size))
    return "%12s " % length


def date_format(epoch):
    """given an epoch, return a unix-ls like formatted string"""

    time_tuple = time.localtime(epoch)
    if time.localtime().tm_year != time_tuple.tm_year:
        return time.strftime('%b %d  %Y ', time_tuple)
    return time.strftime('%b %d %H:%M ', time_tuple)


__LIST_FORMATS__ = {'permissions': lambda value: "{:<11}".format(value),
                    'creator': lambda value: " {:<20}".format(value),
                    'readGroup': lambda value: " {:<15}".format(
                        value.replace(vos.CADC_GMS_PREFIX, "")),
                    'writeGroup': lambda value: " {:<15}".format(
                        value.replace(vos.CADC_GMS_PREFIX, "")),
                    'isLocked': lambda value: " {:<8}".format(["", "LOCKED"][
                        value == "true"]),
                    'size': size_format,
                    'date': date_format}

DESCRIPTION = """lists the contents of a VOSpace Node.

{}

Long listing provides the file size, ownership and read/write status of Node.

""".format(URI_DESCRIPTION)


def _get_sort_key(node, sort):
    if sort == vos.SortNodeProperty.LENGTH:
        return int(node.props['length'])
    elif sort == vos.SortNodeProperty.DATE:
        return vos.convert_vospace_time_to_seconds(node.props['date'])
    else:
        return node.name


def vls():
    parser = CommonParser(description=DESCRIPTION, add_help=False)
    parser.add_argument('node', nargs="+", help="URI of VOSpace Node to list.")
    parser.add_option("--help", action="help", default='==SUPPRESS==',
                      help='show this help message and exit')
    parser.add_option("-l", "--long", action="store_true",
                      help="verbose listing sorted by name")
    parser.add_option("-g", "--group", action="store_true",
                      help="display group read/write information")
    parser.add_option("-h", "--human", action="store_true",
                      help="make sizes human readable", default=False)
    parser.add_option("-S", "--Size", action="store_true",
                      help="sort files by size", default=False)
    parser.add_option("-r", "--reverse", action="store_true",
                      help="reverse the sort order", default=False)
    parser.add_option("-t", "--time", action="store_true",
                      help="sort by time copied to VOSpace")

    try:
        opt = parser.parse_args()
        this.human = opt.human

        set_logging_level_from_args(opt)

        # set which columns will be printed
        columns = []
        if opt.long or opt.group:
            columns = ['permissions']
            if opt.long:
                columns.extend(['creator'])
            columns.extend(
                ['readGroup', 'writeGroup', 'isLocked', 'size', 'date'])

        files = []
        dirs = []

        # determine if their is a sorting order
        if opt.Size:
            sort = vos.SortNodeProperty.LENGTH
        elif opt.time:
            sort = vos.SortNodeProperty.DATE
        else:
            sort = None

        if sort is None and opt.reverse is False:
            order = None
        elif opt.reverse:
            order = 'asc' if sort else 'desc'
        else:
            order = 'desc' if sort else 'asc'

        for node in opt.node:
            if not vos.is_remote_file(file_name=node):
                raise ArgumentError(opt.node,
                                    "Invalid node name: {}".format(node))
            logging.debug("getting listing of: %s" % str(node))
            client = vos.Client(
                vospace_certfile=opt.certfile,
                vospace_token=opt.token)
            targets = client.glob(node)

            # segregate files from directories
            for target in targets:
                target_node = client.get_node(target)
                if target_node.isdir():
                    dirs.append((_get_sort_key(target_node, sort),
                                 target_node, target))
                else:
                    files.append((_get_sort_key(target_node, sort),
                                  target_node))

        for f in sorted(files, key=lambda ff: ff[0],
                        reverse=(order == 'desc')):
            _display_target(columns, f[1])

        for d in sorted(dirs, key=lambda dd: dd[0], reverse=(order == 'desc')):
            n = d[1]
            if (len(dirs) + len(files)) > 1:
                sys.stdout.write('\n{}:\n'.format(n.name))
                if opt.long:
                    sys.stdout.write('total: {}\n'.format(
                        int(n.get_info()['size'])))
            for row in client.get_children_info(d[2], sort, order):
                _display_target(columns, row)

    except Exception as ex:
        exit_on_exception(ex)


def _display_target(columns, row):
    name_string = row.name
    info = row.get_info()
    for col in columns:
        value = info.get(col, None)
        value = value is not None and value or ""
        if col in __LIST_FORMATS__:
            sys.stdout.write(__LIST_FORMATS__[col](value))
        if info["permissions"][0] == 'l':
            name_string = "%s -> %s" % (
                row.name, info['target'])
    sys.stdout.write("%s\n" % name_string)


vls.__doc__ = DESCRIPTION
