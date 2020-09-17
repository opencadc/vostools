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
        self.resource_id = {}
        if self.get('vos', 'resourceID'):
            for resource in self.get('vos', 'resourceID').split('\n'):
                if resource:
                    rr = resource.split()
                    if len(rr) == 1:
                        prefix = 'vos'  # default prefix
                    elif len(rr) == 2:
                        prefix = rr[1]
                    else:
                        raise ValueError(
                            'Error parsing resourceID {}: '.format(resource))
                    if prefix in self.resource_id:
                        raise ValueError('Prefix {} not unique in config '
                                         'file'.format(prefix))
                    self.resource_id[prefix] = rr[0]
        if not self.resource_id:
            raise RuntimeError(
                'No resourceID found in config file')

    def get_resource_id(self, prefix=None):
        if prefix is None:
            if len(self.resource_id.values()) == 1:
                # return single resourceID
                return list(self.resource_id.values())[0]
            else:
                raise ValueError(
                    'Multiple resourceIDs in the config file. Use a prefix '
                    'to refer to one of them.')
        try:
            return self.resource_id[prefix]
        except KeyError:
            raise ValueError('{} prefix not found in the vos config file'.
                             format(prefix))


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
        ('# resourceID = <resourceID> [<prefix>(default vos)]\n'
         '# prefix is used to identify files and directories on that '
         'service with\n'
         '# the command line. e.g. vos:path/to/file is the path to a file '
         'on the VOSpace\n'
         '# service with the vos prefix. Prefixes in the config file must '
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
