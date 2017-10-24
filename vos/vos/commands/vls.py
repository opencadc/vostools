"""Lists information about a VOSpace DataNode or the contents of a
ContainerNode."""
from __future__ import (absolute_import, division, print_function,
                        unicode_literals)
import logging
import math
from ..commonparser import CommonParser, set_logging_level_from_args, \
    exit_on_exception
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
                    'isLocked': lambda value: " {:<8}".format("", "LOCKED")[
                        value == "true"],
                    'size': size_format,
                    'date': date_format}

DESCRIPTION = """lists the contents of a VOSpace Node.

Long listing provides the file size, ownership and read/write status of Node.

"""


def vls():
    parser = CommonParser(description=DESCRIPTION, add_help=False)
    parser.add_argument('node', nargs="+", help="VOSpace Node to list.")
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

        # determine if their is a sorting order
        sort_key = (opt.time and "date") or (opt.Size and "size") or False

        # create a client to send VOSpace command
        client = vos.Client(vospace_certfile=opt.certfile,
                            vospace_token=opt.token)

        for node in opt.node:
            if not node.startswith('vos:'):
                raise ArgumentError(opt.node,
                                    "Invalid node name: {}".format(node))
            logging.debug("getting listing of: %s" % str(node))
            info_list = client.get_info_list(node)

            if sort_key:
                # noinspection PyBroadException
                try:
                    sorted_list = sorted(info_list,
                                         key=lambda name: name[1][sort_key],
                                         reverse=not opt.reverse)
                except Exception:
                    sorted_list = info_list
                finally:
                    info_list = sorted_list

            for item in info_list:
                name_string = item[0]
                for col in columns:
                    value = item[1].get(col, None)
                    value = value is not None and value or ""
                    if col in __LIST_FORMATS__:
                        sys.stdout.write(__LIST_FORMATS__[col](value))
                    if item[1]["permissions"][0] == 'l':
                        name_string = "%s -> %s" % (
                            name_string, item[1]['target'])
                sys.stdout.write("%s\n" % name_string)
    except Exception as ex:
        exit_on_exception(ex)


vls.__doc__ = DESCRIPTION
