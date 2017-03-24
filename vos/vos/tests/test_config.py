import os
import tempfile
import uuid
import unittest2 as unittest
from .. import config


class TestConfig(unittest.TestCase):
    """Test the vos Config class.
    """

    def test_config(self):
        """
        Test that a new config file is written
        """

        config_file_path = tempfile.gettempdir() + '/' + str(uuid.uuid4())
        default_config_path = tempfile.gettempdir() + '/' + str(uuid.uuid4())
        try:
            default_config_file = open(default_config_path, 'w')
            default_config_file.write("{}\n".format("[vos]"))
            default_config_file.write("{}\n".format("foo = bar"))
            default_config_file.close()

            config.Config.write_config(config_file_path, default_config_file.name)

            config_file = open(config_file_path, 'r')
            lines = config_file.readlines()
            # self.assertTrue(len(lines) == 2)
            self.assertEquals("[vos]", lines[0].strip())
            self.assertEquals("foo = bar", lines[1].strip())
            config_file.close()

            default_config_file = open(default_config_path, 'a')
            default_config_file.write("{}\n".format("a = 123"))
            default_config_file.close()

            config.Config.write_config(config_file_path, default_config_file.name)

            config_file = open(config_file_path, 'r')
            lines = config_file.readlines()
            # self.assertTrue(len(lines) == 3)
            self.assertEquals("[vos]", lines[0].strip())
            self.assertEquals("foo = bar", lines[1].strip())
            self.assertEquals("a = 123", lines[2].strip())
            config_file.close()

            # remove last line of default config file
            lines = open(default_config_path, 'r').readlines()
            del lines[-1]
            open(default_config_path, 'w').writelines(lines)

            config.Config.write_config(config_file_path, default_config_file.name)

            config_file = open(config_file_path, 'r')
            lines = config_file.readlines()
            # self.assertTrue(len(lines) == 2)
            self.assertEquals("[vos]", lines[0].strip())
            self.assertEquals("foo = bar", lines[1].strip())
            config_file.close()

        finally:
            os.remove(config_file_path)
            os.remove(default_config_path)
