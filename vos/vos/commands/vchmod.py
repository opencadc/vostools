#!python
"""set read/write properties of a node.

"""
from __future__ import (absolute_import, division, print_function,
                        unicode_literals)
from ..vos import Client
from ..vos import CADC_GMS_PREFIX
from ..commonparser import CommonParser, set_logging_level_from_args,\
    URI_DESCRIPTION
from ..commonparser import exit_on_exception
import logging
import sys
import re
from argparse import ArgumentError


def __mode__(mode):
    """

    :param mode:
    :return: mode dictionary
     :rtype: re.groupdict
    """
    _mode = re.match(r"(?P<who>og|go|o|g)(?P<op>[+\-=])(?P<what>rw|wr|r|w)",
                     mode)
    if _mode is None:
        raise ArgumentError(_mode, 'Invalid mode: {}'.format(mode))
    return _mode.groupdict()


DESCRIPTION = """Set the read and write permission on VOSpace nodes.

{}

Permission string specifies the mode change to make.

Changes to 'o' set the public permission, so only o+r and o-r are allowed.

Changes to 'g' set the group permissions, g-r, g-w, g-rw to remove a group
permission setting (removes all groups) and g+r, g+w, g+rw to add a group
permission setting.  If Adding group permission then the applicable group
must be included.

e.g. vchmod g+r vos:RootNode/MyFile.txt  "Group1 Group2"

Set read access to groups Group1 and Group2 (upto 4 groups can be specified).

Permission setting is recursive, if a GroupB is part of GroupA then permissions
given to members of GroupA are also provided to members of GroupB.
""".format(URI_DESCRIPTION)


def vchmod():
    # TODO:  seperate the sys.argv parsing from the actual command.

    parser = CommonParser(description=DESCRIPTION)
    parser.add_argument(
        'mode', type=__mode__,
        help=r'permission setting accepted modes: (og|go|o|g)[+-=](rw|wr|r\w)')
    parser.add_argument(
        "node",
        help="node to set mode on, eg: vos:Root/Container/file.txt")
    parser.add_argument(
        'groups', nargs="*",
        help="name of group(s) to assign read/write permission to")
    parser.add_option(
        "-R", "--recursive", action='store_const', const=True,
        help="Recursive set read/write properties")

    opt = parser.parse_args()

    set_logging_level_from_args(opt)

    group_names = opt.groups

    mode = opt.mode

    props = {}
    try:
        if 'o' in mode['who']:
            if mode['op'] == '-':
                props['ispublic'] = 'false'
            else:
                props['ispublic'] = 'true'
        if 'g' in mode['who']:
            if '-' == mode['op']:
                if not len(group_names) == 0:
                    raise ArgumentError(
                        opt.groups,
                        "Names of groups not valid with remove permission")
                if 'r' in mode['what']:
                    props['readgroup'] = None
                if "w" in mode['what']:
                    props['writegroup'] = None
            else:
                if not len(group_names) == len(mode['what']):
                    name = len(mode['what']) > 1 and "names" or "name"
                    raise ArgumentError(None,
                                        "{} group {} required for {}".format(
                                            len(mode['what']), name,
                                            mode['what']))
                if mode['what'].find('r') > -1:
                    # remove duplicate whitespaces
                    read_groups = " ".join(
                        group_names[mode['what'].find('r')].split())
                    props['readgroup'] = \
                        (CADC_GMS_PREFIX +
                         read_groups.replace(" ", " " + CADC_GMS_PREFIX))
                if mode['what'].find('w') > -1:
                    wgroups = " ".join(
                        group_names[mode['what'].find('w')].split())
                    props['writegroup'] = \
                        (CADC_GMS_PREFIX +
                         wgroups.replace(" ", " " + CADC_GMS_PREFIX))
    except ArgumentError as er:
        parser.print_usage()
        logging.error(str(er))
        sys.exit(er)

    logging.debug("Setting {} on {}".format(props, opt.node))

    try:
        client = Client(vospace_certfile=opt.certfile,
                        vospace_token=opt.token)
        node = client.get_node(opt.node)
        if opt.recursive:
            node.props.clear()
            node.clear_properties()
            # del node.node.findall(vos.Node.PROPERTIES)[0:]
        if 'readgroup' in props:
            node.chrgrp(props['readgroup'])
        if 'writegroup' in props:
            node.chwgrp(props['writegroup'])
        if 'ispublic' in props:
            node.set_public(props['ispublic'])
        logging.debug("Node: {0}".format(node))
        status = client.update(node, opt.recursive)
        if status:
            sys.exit(status)
    except Exception as ex:
        exit_on_exception(ex)


vchmod.__doc__ = DESCRIPTION
