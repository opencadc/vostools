import errno
import logging
import os
import sys
import ConfigParser
from shutil import copyfile


_ROOT = os.path.abspath(os.path.dirname(__file__))
_DEFAULT_CONFIG_PATH = os.path.join(_ROOT, 'data', 'default-vos-config')
_CONFIG_PATH = os.path.expanduser("~") + '/.config/vos/vos-config'
_SECTION = 'vos'

logger = logging.getLogger('config')
logger.setLevel(logging.DEBUG)

if sys.version_info[1] > 6:
    logger.addHandler(logging.NullHandler())


# def get_data(path):
#     return os.path.join(_ROOT, 'data', path)


def mkdir_p(path):
    try:
        os.makedirs(path)
    except OSError as exc:
        if exc.errno == errno.EEXIST and os.path.isdir(path):
            pass
        else:
            raise


class Config(object):

    def __init__(self, config_path=_CONFIG_PATH, default_config_path=_DEFAULT_CONFIG_PATH):
        logger.info("Using config file {0}.".format(config_path))

        # check config file exists and can be read
        if not os.path.isfile(config_path) and not os.access(config_path, os.R_OK):
            error = "Can not read {0}.".format(config_path)
            logger.error(error)
            raise IOError(error)

        self.parser = ConfigParser.ConfigParser()
        try:
            self.parser.readfp(open(default_config_path))
        except ConfigParser.Error as exc:
            logger.error("Error opening {0} because {1}.".format(default_config_path, exc.message))

        try:
            self.parser.read(config_path)
        except ConfigParser.Error as exc:
            logger.error("Error opening {0} because {1}.".format(config_path, exc.message))

    def get(self, key):
        try:
            return self.parser.get(_SECTION, key)
        except ConfigParser.NoOptionError:
            pass
        return None

    @staticmethod
    def write_config(config_path=_CONFIG_PATH, default_config_path=_DEFAULT_CONFIG_PATH):
        """
        :param config_path:
        :param default_config_path:
        :return:
        """

        # if not local config file then write the default config file
        if not os.path.isfile(config_path):
            mkdir_p(os.path.dirname(config_path))
            copyfile(default_config_path, config_path)
            return

        # read local config file
        parser = ConfigParser.ConfigParser()
        try:
            parser.read(config_path)
        except ConfigParser.Error as exc:
            logger.error("Error opening {0} because {1}.".format(config_path, exc.message))
            return

        # read default config file
        default_parser = ConfigParser.RawConfigParser()
        try:
            default_parser.read(default_config_path)
        except ConfigParser.Error as exc:
            logger.error("Error opening {0} because {1}.".format(default_config_path, exc.message))
            return

        # update config file with new options from the default config
        updated = False
        default_items = default_parser.items(_SECTION)
        for option, value in default_items:
            if not parser.has_option(_SECTION, option):
                parser.set(_SECTION, option, value)
                updated = True

        # remove old options not in the default config file?
        options = parser.options(_SECTION)
        for option in options:
            if not default_parser.has_option(_SECTION, option):
                parser.remove_option(_SECTION, option)

        # write updated config file
        if updated:
            try:
                config_file = open(config_path, 'w')
                parser.write(config_file)
                config_file.close()
            except Exception as exc:
                print exc.message
