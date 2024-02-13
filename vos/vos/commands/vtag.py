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

"""Tags are annotations on VOSpace Nodes.  This module provides set/read/(list)
functions for property(ies) of a node.

The tag system is meant to allow tags, in addition to the standard node
properties. """
import logging
import pprint
import sys
from ..commonparser import CommonParser, set_logging_level_from_args, \
    exit_on_exception, URI_DESCRIPTION
from .. import vos

DESCRIPTION = """provides set/read/(list) functions for property(ies) of a
node.

{}

Properties are attributes on the node.  There can be users attributes or
system attributes.

Only user attributes can be set.

examples:

set at property:  vtag vos:RootNode/MyImage.fits quality=good
read a property:  vtag vos:RootNode/MyImage.fits quality
delete a property: vtag vos:RootNode/MyImage.fits quality=
                   or
                   vtag vos:RootNode/MyImage.fits quality --remove
list all property values:  vtag vos:RootNode/MyImage.fits

""".format(URI_DESCRIPTION)


def vtag():
    parser = CommonParser(description=DESCRIPTION)
    parser.add_argument('node', help='Node to set property (tag/attribute) on')
    parser.add_argument(
        'property',
        help="Property whose value will be read, set or deleted",
        nargs="*")
    parser.add_option('--remove', action="store_true",
                      help='remove the listed property')
    parser.add_option('-R', '--recursive', action="store_true",
                      help='perform the operation recursively on all the descendants')

    args = parser.parse_args()
    set_logging_level_from_args(args)

    # the node should be the first argument, the rest should contain
    # the key/val pairs
    node_arg = args.node

    props = []
    if args.remove:
        # remove signified by blank value in key=value listing
        for prop in args.property:
            if '=' not in prop:
                prop += "="
            props.append(prop)
    else:
        props = args.property

    try:
        client = vos.Client(
            vospace_certfile=args.certfile,
            vospace_token=args.token,
            insecure=args.insecure)
        node = client.get_node(node_arg)
        if len(props) == 0:
            # print all properties
            pprint.pprint(node.props)
        if args.recursive:
            node.props.clear()
            node.clear_properties()
            for prop in props:
                key, value = prop.split('=')
                if len(value) == 0:
                    value = None
                node.props[key] = value
            successes, failures = client.add_props(node, recursive=True)
            if args.recursive:
                if failures:
                    logging.error(
                        'WARN. updated count: {}, failed count: {}\n'.
                        format(successes, failures))
                    sys.exit(-1)
                else:
                    logging.info(
                        'DONE. updated count: {}\n'.format(successes))
        else:
            changed = False
            for prop in props:
                prop = prop.split('=')
                if len(prop) == 1:
                    # get one property
                    pprint.pprint(node.props.get(prop[0], None))
                elif len(prop) == 2:
                    # update properties
                    key, value = prop
                    if len(value) == 0:
                        value = None
                    if value != node.props.get(key, None):
                        node.props[key] = value
                        changed = True
                else:
                    raise ValueError(
                        "Illegal keyword of value character ('=') used: %s" % (
                            '='.join(prop)))

            if changed:
                client.add_props(node)
    except Exception as ex:
        exit_on_exception(ex)


vtag.__doc__ = DESCRIPTION
