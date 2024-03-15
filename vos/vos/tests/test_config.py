# ***********************************************************************
# ******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
# *************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
#
#  (c) 2023.                            (c) 2023.
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
# Test the vosconfig functionality
from unittest.mock import Mock, patch
import warnings

from vos import vosconfig
import pytest
import tempfile
import os

# To run individual tests, set the value of skipTests to True, and comment
# out the @unittest.skipIf line at the top of the test to be run.
skipTests = False


# @unittest.skip('Functionality no longer used')
@patch('vos.vos.os.path.exists', Mock())
def test_update_config():
    # Cause all warnings to always be triggered.
    warnings.simplefilter("always")
    with patch('builtins.open') as open_mock:
        old_content = 'blah'
        new_config_mock = Mock()
        open_mock.return_value.read.return_value = old_content
        open_mock.return_value.write = new_config_mock
        vosconfig._update_config()
    new_config_mock.assert_called_once_with(old_content)

    # test rewrite vospace resource in config file
    new_config_mock.reset_mock()
    # Cause all warnings to always be triggered.
    warnings.simplefilter("always")
    with patch('builtins.open') as open_mock:
        old_content = 'blah\nresourceID=ivo://cadc.nrc.ca/vospace\nfoo'
        new_content = Mock()
        open_mock.return_value.read.return_value = old_content
        open_mock.return_value.write = new_content
        vosconfig._update_config()
    new_content.assert_called_once_with(old_content.replace(
        'vospace', 'vault'))

    # test rewrite transfer protocol in config file
    new_config_mock.reset_mock()
    protocol_text = \
        "# transfer protocol configuration is no longer supported\n"
    # Cause all warnings to always be triggered.
    warnings.simplefilter("always")
    with patch('builtins.open') as open_mock:
        old_content = 'blah\nprotocol=http\nfoo'
        new_content = Mock()
        open_mock.return_value.read.return_value = old_content
        open_mock.return_value.write = new_content
        vosconfig._update_config()
    new_content.assert_called_once_with(old_content.replace(
        'protocol', '{}# protocol'.format(protocol_text)))


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
