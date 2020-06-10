"""
Custom config class that supports multiple resource IDs
"""

from cadcutils.util import Config

class VosConfig(Config):

    def __init__(self, config_path, default_config_path=None):
        super(VosConfig, self).__init__(
            config_path=config_path, default_config_path=default_config_path)
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
                            'Error parsing resourceID: '.format(resource))
                    if prefix in self.resource_id:
                        raise ValueError(
                            'Prefix {} not unique in config file'.format(prefix))
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
                    'Multiple resourceIDs in the config file. No default.')
        return self.resource_id.get(prefix, None)



