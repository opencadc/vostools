# ***********************************************************************
# ******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
# *************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
#
#  (c) 2022.                            (c) 2022.
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

"""
Custom config class that supports multiple resource IDs
"""

import os
import warnings
import argparse
import sys

from cadcutils.util import Config


_ROOT = os.path.abspath(os.path.dirname(__file__))
_DEFAULT_CONFIG_PATH = os.path.join(_ROOT, 'data', 'default-vos-config')

_CONFIG_PATH = os.path.expanduser("~") + '/.config/vos/vos-config'
if os.getenv('VOSPACE_CONFIG_FILE', None):
    _CONFIG_PATH = os.getenv('VOSPACE_CONFIG_FILE')


class VosConfig(Config):

    def __init__(self, config_path, default_config_path=None):
        if os.path.isfile(config_path):
            super(VosConfig, self).__init__(config_path=config_path)
        else:
            super(VosConfig, self).__init__(config_path=default_config_path)
        # default vos resource shortcut
        self.resource_id = {'vos': 'ivo://cadc.nrc.ca/vault'}
        default_rname = set(self.resource_id.keys())
        if self.get('vos', 'resourceID'):
            for resource in self.get('vos', 'resourceID').split('\n'):
                if resource:
                    rr = resource.split()
                    if len(rr) == 1:
                        if rr[0].strip() not in self.resource_id.values():
                            raise ValueError(
                                'Error parsing config file - resource without '
                                'name: {}'.format(rr)
                            )
                        else:
                            continue
                    elif len(rr) == 2:
                        rname = rr[1]
                    else:
                        raise ValueError(
                            'Error parsing resourceID {}: '.format(resource))
                    if rname in default_rname:
                        if self.resource_id[rname] == rr[0]:
                            continue
                        raise ValueError(
                            'Error parsing config file - name {} is reserved '
                            'for service {}'.format(
                                rname, self.resource_id[rname]))
                    if rname in self.resource_id:
                        raise ValueError('Error parsing config file - resource'
                                         ' name {} not unique in '
                                         'config file'.format(rname))
                    self.resource_id[rname] = rr[0]

    def get_resource_id(self, resource_name):
        """
        returns the service ID corresponding to a name.
        :param resource_name:
        :return:
        """
        if not resource_name:
            raise ValueError('resource name required')
        try:
            return self.resource_id[resource_name]
        except KeyError:
            raise ValueError(
                '{} resource name not found in the vos config file'.
                format(resource_name))


def _update_config():
    """
    Updates old format config files
    """
    # temporary function to deal with:
    # - renaming of the ivo://cadc.nrc.ca/vospace to ivo://cadc.nrc.ca/vault
    # - commenting out transfer protocol
    # - support for multiple services
    multitple_services_1 = \
        '# List of VOSpace services known to vos, one entry per line:'
    multitple_services_2 = \
        ('# resourceID = <resourceID> [<resource_name>(default vos)]\n'
         '# one entry per row, indentation needed to all but the first row'
         '# resource_name is a short form used to identify files and '
         'directoris on that service with\n'
         '# a short URI. For example, with vos as the name for resource ID '
         'ivo:cadc.nrc.ca/vault,\n'
         '# vos:file/path and ivo://cadc.nrc.ca^vault/file/path '
         ' equivalents URIs'
         '# Resource names in the config file must '
         'be unique.\n')
    if os.path.exists(_CONFIG_PATH):
        try:
            protocol_text = \
                '# transfer protocol configuration is no longer supported'
            changed = False
            config_content = open(_CONFIG_PATH, 'r').read()
            if 'ivo://cadc.nrc.ca/vospace' in config_content:
                config_content = config_content.replace(
                    'ivo://cadc.nrc.ca/vospace',
                    'ivo://cadc.nrc.ca/vault')
                changed = True
            if protocol_text not in config_content and \
                    'protocol' in config_content:
                config_content = config_content.replace(
                    'protocol',
                    '{}\n# protocol'.format(protocol_text))
                changed = True
            if multitple_services_1 not in config_content:
                config_content = config_content.replace(
                    '[vos]', '[vos]\n{}\n{}'.format(multitple_services_1,
                                                    multitple_services_2))
                changed = True

            if changed:
                open(_CONFIG_PATH, 'w').write(config_content)
        except Exception as e:
            warnings.warn('Error trying to access {} config file: {}'.format(
                _CONFIG_PATH, str(e)))
            pass


_update_config()
vos_config = VosConfig(_CONFIG_PATH, _DEFAULT_CONFIG_PATH)


def vos_config_main():
    """
    This is the entry point for the vos-config command to deal with config
    """
    parser = argparse.ArgumentParser(
        description='vos utility to handle the vos config file. This\n'
                    'file is not required unless vos functionality needs\n'
                    'customization (e.g. add resourceIDs for vospace\n'
                    'services other than ivo://cadc.nrc.ca/vault. The\n'
                    'default location is ~/.config/vos/vos-config. The\n'
                    '$VOSPACE_CONFIG_FILE environment variable can be used\n'
                    'to point to a different config file.')
    parser.add_argument('-g', '--generate', action='store_true',
                        help='generate a default config file')

    args = parser.parse_args()

    if args.generate:
        if os.path.isfile(_CONFIG_PATH):
            print('Config file {} already exists.'.format(_CONFIG_PATH))
            sys.exit(-1)
        try:
            Config.write_config(_CONFIG_PATH, _DEFAULT_CONFIG_PATH)
        except Exception as e:
            print('Failed to generate {}: {}'.format(_CONFIG_PATH, str(e)))
            sys.exit(-1)
        print('\nvos config file {} generated to be customized. This\n'
              'configuration is going to be used by vos from now.\n'.
              format(_CONFIG_PATH))
        config_file = _CONFIG_PATH
    else:
        if os.path.isfile(_CONFIG_PATH):
            config_file = _CONFIG_PATH
            print('\nvos is using the configuration file {}.\n'.
                  format(_CONFIG_PATH))
        else:
            config_file = _DEFAULT_CONFIG_PATH
            print('\nvos is using no configuration file but if it was,\n'
                  'if would look as below. To generate a config file,\n'
                  'customize and use it, run this command with the -g\n'
                  'flag.\n')
    print('File content:')
    print('-'*20)
    print()
    print(open(config_file).read())
