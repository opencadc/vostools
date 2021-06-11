# Test the vosconfig functionality
from mock import Mock, patch
import warnings

from vos import vosconfig
import pytest
import tempfile
import os

# To run individual tests, set the value of skipTests to True, and comment
# out the @unittest.skipIf line at the top of the test to be run.
skipTests = False


@patch('vos.vos.os.path.exists', Mock())
def test_update_config():
    # TODO - for some reason in Python 2.7 the warnings context is not working
    # although the warning is raised in the code.
    # test rename vospace resource
    # with warnings.catch_warnings(record=True) as w:
    #     vos.Connection(resource_id='ivo://cadc.nrc.ca/vospace')
    #     assert len(w) == 1
    #     assert issubclass(w[-1].category, UserWarning)
    #     assert 'Deprecated resource id ivo://cadc.nrc.ca/vospace. ' \
    #            'Use ivo://cadc.nrc.ca/vault instead' == str(w[-1].message)

    # Cause all warnings to always be triggered.
    warnings.simplefilter("always")
    with patch('vos.vos.open') as open_mock:
        old_content = 'blah'
        new_config_mock = Mock()
        open_mock.return_value.read.return_value = old_content
        open_mock.return_value.write = new_config_mock
        vosconfig._update_config()
    assert new_config_mock.called_once_with(old_content)

    # test rewrite vospace resource in config file
    new_config_mock.reset_mock()
    # Cause all warnings to always be triggered.
    warnings.simplefilter("always")
    with patch('vos.vos.open') as open_mock:
        old_content = 'blah\nresourceID=ivo://cadc.nrc.ca/vospace\nfoo'
        new_content = Mock()
        open_mock.return_value.read.return_value = old_content
        open_mock.return_value.write = new_content
        vosconfig._update_config()
    assert new_config_mock.called_once_with(old_content.replace(
        'vospace', 'vault'))

    # test rewrite transfer protocol in config file
    new_config_mock.reset_mock()
    protocol_text = \
        "# transfer protocol configuration is no longer supported\n"
    # Cause all warnings to always be triggered.
    warnings.simplefilter("always")
    with patch('vos.vos.open') as open_mock:
        old_content = 'blah\nprotocol=http\nfoo'
        new_content = Mock()
        open_mock.return_value.read.return_value = old_content
        open_mock.return_value.write = new_content
        vosconfig._update_config()
    assert new_config_mock.called_once_with(old_content.replace(
        'protocol', '{}#protocol'.format(protocol_text)))


@patch('vos.vosconfig.os.path.isfile', Mock())
def test_get_resource_id():
    """
    Tests getting resource ID under various cases
    :return:
    """
    # config file does not exist
    conf = vosconfig.VosConfig('somedir', 'somedir')
    assert conf.get_resource_id('vos') == 'ivo://cadc.nrc.ca/vault'
    with pytest.raises(ValueError) as e:
        conf.get_resource_id(None)
    assert str(e.value) == 'resource name required'
    with pytest.raises(ValueError) as e:
        conf.get_resource_id('nonexistent')
    assert str(e.value) == \
           'nonexistent resource name not found in the vos config file'

    # default 1 resource - vault - should pass
    config_content = '[vos]\nresourceID = ivo://cadc.nrc.ca/vault'
    temp_dir = tempfile.mkdtemp()
    config_file = os.path.join(temp_dir, 'vos-config')
    open(config_file, 'w').write(config_content)
    vosconfig.VosConfig(config_file)
    assert conf.get_resource_id('vos') == 'ivo://cadc.nrc.ca/vault'

    # default 1 resource without name should fail
    config_content = '[vos]\nresourceID = ivo://cadc.nrc.ca/vv\n'
    open(config_file, 'w').write(config_content)
    with pytest.raises(Exception) as e:
        vosconfig.VosConfig(config_file)
    assert str(e.value) == \
           "Error parsing config file - resource without name: " \
           "['ivo://cadc.nrc.ca/vv']"

    # default config defined and renamed in config file
    config_content = '[vos]\nresourceID = ivo://cadc.nrc.ca/vault vos\n' \
                     '  ivo://cadc.nrc.ca/vault cadcvos'
    open(config_file, 'w').write(config_content)
    conf = vosconfig.VosConfig(config_file)
    assert conf.get_resource_id('vos') == 'ivo://cadc.nrc.ca/vault'
    assert conf.get_resource_id('cadcvos') == 'ivo://cadc.nrc.ca/vault'

    # extra resource to the config file with proper name
    config_content = '[vos]\nresourceID = ivo://some.provider/vo spvo\n' \
                     '    ivo://some.other.provider/vo sopvo'
    open(config_file, 'w').write(config_content)
    conf = vosconfig.VosConfig(config_file)
    assert conf.get_resource_id('vos') == 'ivo://cadc.nrc.ca/vault'
    assert conf.get_resource_id('spvo') == 'ivo://some.provider/vo'
    assert conf.get_resource_id('sopvo') == 'ivo://some.other.provider/vo'

    # second name for vault in config file
    config_content = '[vos]\nresourceID = ivo://cadc.nrc.ca/vault vault'
    open(config_file, 'w').write(config_content)
    conf = vosconfig.VosConfig(config_file)
    assert conf.get_resource_id('vos') == 'ivo://cadc.nrc.ca/vault'
    assert conf.get_resource_id('vault') == 'ivo://cadc.nrc.ca/vault'

    # attempt to assign a reserved name (vos or arc)
    config_content = '[vos]\nresourceID = ivo://cadc.nrc.ca/vv vos'
    open(config_file, 'w').write(config_content)
    with pytest.raises(ValueError) as e:
        vosconfig.VosConfig(config_file)
    assert str(e.value) == \
        "Error parsing config file - name vos is reserved for " \
        "service ivo://cadc.nrc.ca/vault"

    # attempt to assign duplicate name
    config_content = '[vos]\nresourceID = ivo://cadc.nrc.ca/vv vv\n' \
                     '    ivo://cadc.nrc.ca/vv1 vv'
    open(config_file, 'w').write(config_content)
    with pytest.raises(ValueError) as e:
        vosconfig.VosConfig(config_file)
    assert str(e.value) == \
           "Error parsing config file - resource name vv not unique in " \
           "config file"
