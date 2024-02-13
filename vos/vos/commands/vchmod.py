# ***********************************************************************
# ******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
# *************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
#
#  (c) 2024.                            (c) 2024.
#  Government of Canada                 Gouvernement du Canada
#  National Research Council            Conseil national de recherches
#  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
#  All rights reserved                  Tous droits réservés
#
#  NRC disclaims any warranties,        Le CNRC dénie toute garantie
#  expressed, implied, or               énoncée, implicite ou légale,
#  statutory, of any kind with          de quelque nature que ce
#  respect to the software,             soit, concernant le logiciel,
#  including without limitation         y compris sans restriction
#  any warranty of merchantability      toute garantie de valeur
#  or fitness for a particular          marchande ou de pertinence
#  purpose. NRC shall not be            pour un usage particulier.
#  liable in any event for any          Le CNRC ne pourra en aucun cas
#  damages, whether direct or           être tenu responsable de tout
#  indirect, special or general,        dommage, direct ou indirect,
#  consequential or incidental,         particulier ou général,
#  arising from the use of the          accessoire ou fortuit, résultant
#  software.  Neither the name          de l'utilisation du logiciel. Ni
#  of the National Research             le nom du Conseil National de
#  Council of Canada nor the            Recherches du Canada ni les noms
#  names of its contributors may        de ses  participants ne peuvent
#  be used to endorse or promote        être utilisés pour approuver ou
#  products derived from this           promouvoir les produits dérivés
#  software without specific prior      de ce logiciel sans autorisation
#  written permission.                  préalable et particulière
#                                       par écrit.
#
#  This file is part of the             Ce fichier fait partie du projet
#  OpenCADC project.                    OpenCADC.
#
#  OpenCADC is free software:           OpenCADC est un logiciel libre ;
#  you can redistribute it and/or       vous pouvez le redistribuer ou le
#  modify it under the terms of         modifier suivant les termes de
#  the GNU Affero General Public        la “GNU Affero General Public
#  License as published by the          License” telle que publiée
#  Free Software Foundation,            par la Free Software Foundation
#  either version 3 of the              : soit la version 3 de cette
#  License, or (at your option)         licence, soit (à votre gré)
#  any later version.                   toute version ultérieure.
#
#  OpenCADC is distributed in the       OpenCADC est distribué
#  hope that it will be useful,         dans l’espoir qu’il vous
#  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
#  without even the implied             GARANTIE : sans même la garantie
#  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
#  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
#  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
#  General Public License for           Générale Publique GNU Affero
#  more details.                        pour plus de détails.
#
#  You should have received             Vous devriez avoir reçu une
#  a copy of the GNU Affero             copie de la Licence Générale
#  General Public License along         Publique GNU Affero avec
#  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
#  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
#                                       <http://www.gnu.org/licenses/>.
#
#  $Revision: 4 $
#
# ***********************************************************************
#

"""set read/write properties of a node.

"""
from ..vos import Client
from ..vos import CADC_GMS_PREFIX
from ..commonparser import CommonParser, set_logging_level_from_args, \
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
                        vospace_token=opt.token,
                        insecure=opt.insecure)
        node = client.get_node(opt.node)
        node.props.clear()
        node.clear_properties()
        if 'readgroup' in props:
            node.chrgrp(props['readgroup'])
        if 'writegroup' in props:
            node.chwgrp(props['writegroup'])
        if 'ispublic' in props:
            node.set_public(props['ispublic'])
        logging.debug("Node: {0}".format(node))
        successes, failures = client.update(node, opt.recursive)
        if opt.recursive:
            if failures:
                logging.error('WARN. updated count: {}, failed count: {}\n'.
                              format(successes, failures))
                sys.exit(-1)
            else:
                logging.info('DONE. updated count: {}\n'.format(successes))
    except Exception as ex:
        exit_on_exception(ex)


vchmod.__doc__ = DESCRIPTION
