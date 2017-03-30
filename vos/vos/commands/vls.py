"""Lists information about a VOSpace DataNode or the contents of a ContainerNode."""

import errno
import logging
import math
from vos.commonparser import CommonParser
import os
import sys
import time
from requests.exceptions import SSLError
from vos import vos, version

usage = "%prog vos:VOSpace/Node"
description = """lists file size and ownership of VOSpace/Node. Node can be dataNode, containerNode or linkNode."""

CADC_GMS_PREFIX = vos.CADC_GMS_PREFIX

def vls():

    parser = CommonParser(usage, description=description, add_help_option=False)
    parser.add_option("--help", action="store_true")
    parser.add_option("-l", "--long", action="store_true", help="verbose listing sorted by name")
    parser.add_option("-g", "--group", action="store_true", help="display group read/write information")
    parser.add_option("-h", "--human", action="store_true", help="make sizes human readable", default=False)
    parser.add_option("-S", "--Size", action="store_true", help="sort files by size", default=False)
    parser.add_option("-r", "--reverse", action="store_true", help="reverse the sort order", default=False)
    parser.add_option("-t", "--time", action="store_true", help="sort by time copied to VOSpace")

    (opt, args) = parser.parse_args()
    # We disabled -h/--help so vls can have a -h option.
    # Here we re-enable it but only with --help
    if opt.help:
        parser.print_help()
        sys.exit(0)
    parser.process_informational_options()

    if not len(args) > 0:
        parser.error("missing VOSpace argument")

    logger = logging.getLogger()
    logger.setLevel(parser.log_level)

    sortKey = (opt.time and "date") or (opt.Size and "size") or False

    try:
        client = vos.Client(vospace_certfile=opt.certfile,
                            vospace_token=opt.token)
    except Exception, e:
        logger.error("Connection failed:  %s" % (str(e)))
        sys.exit(-1)

    columns = ['permissions']
    if opt.long:
        columns.extend(['creator'])
    columns.extend(['readGroup', 'writeGroup', 'isLocked', 'size', 'date'])

    def get_terminal_size():
        """Get the size of the terminal

        @return: tuple(int, int) giving the row/column of the terminal screen.
        """

        def ioctl_gwinsz(fd):
            """

            @param fd: A file descriptor that points at the Screen Parameters
            @return: the termios screeen structure unpacked into an array.
            """
            try:
                import fcntl
                import termios
                import struct
                this_cr = struct.unpack('hh', fcntl.ioctl(fd, termios.TIOCGWINSZ, '1234'))
            except:
                return None
            return this_cr

        cr = ioctl_gwinsz(0) or ioctl_gwinsz(1) or ioctl_gwinsz(2)

        if not cr:
            try:
                td = os.open(os.ctermid(), os.O_RDONLY)
                cr = ioctl_gwinsz(td)
                td.close()
            except:
                pass
        if not cr:
            try:
                cr = (os.environ['LINES'], os.environ['COLUMNS'])
            except:
                cr = (25, 80)
        return int(cr[1]), int(cr[0])

    def size_format(size):
        """Format a size value for listing"""
        try:
            size = float(size)
        except Exception as ex:
            logger.debug(str(ex))
            size = 0.0
        if opt.human:
            size_unit = ['B', 'K', 'M', 'G', 'T']
            try:
                length = float(size)
                scale = int(math.log(length) / math.log(1024))
                length = "%.0f%s" % (length / (1024.0 ** scale), size_unit[scale])
            except:
                length = str(int(size))
        else:
            length = str(int(size))
        return "%12s " % length

    def date_format(epoch):
        """given a time object return a unix-ls like formatted string"""

        time_tuple = time.localtime(epoch)
        if time.localtime().tm_year != time_tuple.tm_year:
            return time.strftime('%b %d  %Y ', time_tuple)
        return time.strftime('%b %d %H:%S ', time_tuple)

    formats = {'permissions': lambda value: "%-11s" % value,
               'creator': lambda value: " %-20s" % value,
               'readGroup': lambda value: " %-15s" % ("'" + value.replace(CADC_GMS_PREFIX, "") + "'"),
               'writeGroup': lambda value: " %-15s" % ("'" + value.replace(CADC_GMS_PREFIX, "") + "'"),
               'isLocked': lambda value: " %-8s" % ("", "LOCKED")[value == "true"],
               'size': size_format,
               'date': date_format}

    for node in args:

        try:
            if not node[0:4] == "vos:" :
                raise OSError(errno.EBADF, "Invalid node name", node)
            logger.debug("getting listing of: %s" % str(node))
            infoList = client.get_info_list(node)
        except SSLError as error:
            logger.error(str(error))
            sys.exit(errno.EPERM)
        except Exception as e:
            logger.debug("{0}: {1}".format(type(e), str(e)))
            logger.error("{0}".format(e))
            err_no = getattr(e, 'errno', None)
            err_no = err_no is not None and err_no or errno.ENOMSG
            sys.exit(err_no)

        if sortKey:
            try:
                sorted_list = sorted(infoList, key=lambda name: name[1][sortKey], reverse=not opt.reverse)
            except:
                sorted_list = infoList 
            finally:
                infoList = sorted_list
 
        for item in infoList:
            name_string = item[0]
            if opt.long or opt.group:
                for col in columns:
                    value = item[1].get(col, None)
                    value = value is not None and value or ""
                    if col in formats:
                        sys.stdout.write(formats[col](value))
                if item[1]["permissions"][0] == 'l':
                    name_string = "%s -> %s" % (name_string, item[1]['target'])
            sys.stdout.write("%s\n" % name_string)
