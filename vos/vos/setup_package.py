import os
from cadcutils.util.config import Config

_ROOT = os.path.abspath(os.path.dirname(__file__))
_DEFAULT_CONFIG_PATH = os.path.join(_ROOT, 'data', 'default-vos-config')
_CONFIG_PATH = os.path.expanduser("~") + '/.config/vos/vos-config'


def post_install_hook(foo):
    Config.write_config(_CONFIG_PATH, _DEFAULT_CONFIG_PATH)


def post_develop_hook(foo):
    Config.write_config(_CONFIG_PATH, _DEFAULT_CONFIG_PATH)


vos_config = Config(_CONFIG_PATH)
