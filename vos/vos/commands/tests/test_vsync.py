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

import tempfile
import os
import importlib
import datetime
import pytest
from unittest.mock import Mock, patch
import hashlib

from vos.commands.vsync import validate, prepare, build_file_list, execute, \
    TransferReport, compute_md5
from cadcutils import exceptions as transfer_exceptions
from vos.vos import ZERO_MD5


def module_patch(*args):
    """
    Need to use this instead of mock.patch because vsync module has a function
    vsync defined.
    Credit: https://stackoverflow.com/questions/52324568/how-to-mock-a-
    function-called-in-a-function-inside-a-module-with-the-same-name
    :param args:
    :return:
    """
    target = args[0]
    components = target.split('.')
    for i in range(len(components), 0, -1):
        try:
            # attempt to import the module
            imported = importlib.import_module('.'.join(components[:i]))

            # module was imported, let's use it in the patch
            result = patch(*args)
            result.getter = lambda: imported
            result.attribute = '.'.join(components[i:])
            return result
        except Exception:
            pass

    # did not find a module, just return the default mock
    return patch(*args)


def test_compute_md5():
    tmp_file = tempfile.NamedTemporaryFile()
    assert compute_md5(tmp_file.name) == ZERO_MD5

    content = b'abc'
    open(tmp_file.name, 'wb').write(content)
    md5 = hashlib.md5()
    md5.update(content)
    assert compute_md5(tmp_file.name) == md5.hexdigest()
    # try again to use cache
    assert compute_md5(tmp_file.name) == md5.hexdigest()
    # make cache stalled
    content = b'cba'
    open(tmp_file.name, 'wb').write(content)
    md5 = hashlib.md5()
    md5.update(content)
    assert compute_md5(tmp_file.name) == md5.hexdigest()


def test_validate():
    assert validate('somepath')
    assert validate('somepath', exclude='.')
    assert not validate('.hiddenfile', exclude='.')
    assert not validate('file.fits.tmp', exclude='tmp')
    assert not validate('file.fits.tmp', exclude='exe,tmp')
    assert validate('somepath', include='.?me.?')
    assert not validate('sopath', include='.?me.?')
    # exclude wins
    assert not validate('somepath', include='.?me.?', exclude='me')
    # illegal characters
    assert not validate('ab[cd')


def test_prepare():
    client = Mock()
    vos_location = 'vos:someservice/somedir'
    tmp_file = tempfile.NamedTemporaryFile()
    assert tmp_file, vos_location == prepare(
        tmp_file.name, vos_location, client)
    assert not client.mkdir.called

    tmp_dir = tempfile.TemporaryDirectory()
    src_dir = os.path.join(tmp_dir.name, 'vsyncsrc')
    os.mkdir(src_dir)
    assert not prepare(src_dir, vos_location, client)
    client.mkdir.assert_called_with(vos_location)

    # simlinks are not synced
    client.mkdir.reset_mock()
    link_file = os.path.join(src_dir, 'filelink')
    os.symlink(tmp_file.name, link_file)
    assert not prepare(link_file, vos_location, client)
    assert not client.mkdir.called

    # directory exists on the server
    client.mkdir.reset_mock()
    client.mkdir.side_effect = transfer_exceptions.AlreadyExistsException
    tmp_dir = tempfile.TemporaryDirectory()
    assert not prepare(tmp_dir.name, vos_location, client)
    client.mkdir.assert_called_with(vos_location)


def test_build_file_list():
    def check_list(expected, actual):
        """
        checks lists of expected files vs actual. Order is determined by
        the os.walk function and it's not deterministic so we test just
        the existence of elements in the list
        """
        assert len(actual) == len(expected)
        for item in expected:
            assert item in actual

    tmp_dir = tempfile.TemporaryDirectory()
    src_dir_name = 'syncsrc'
    src_dir = os.path.join(tmp_dir.name, src_dir_name)
    os.mkdir(src_dir)
    # normally name of the src directory is part of this but we keep this
    # for simplicity
    vos_root = 'vos:someservice/somepath'

    def get_vos_path(path, sync_dir):
        if sync_dir.endswith('/'):
            base_dir = sync_dir
        else:
            base_dir = os.path.dirname(sync_dir)
        uri_path = os.path.relpath(path, base_dir)
        return '{}/{}'.format(vos_root, uri_path)

    check_list([(src_dir, get_vos_path(src_dir, src_dir))],
               build_file_list([src_dir], vos_root))

    file1 = 'file1'
    file1_path = os.path.join(src_dir, file1)
    open(file1_path, 'w').write('test')

    expected_list = [(src_dir, get_vos_path(src_dir, src_dir)),
                     (file1_path, get_vos_path(file1_path, src_dir))]
    check_list(expected_list, build_file_list([src_dir], vos_root))

    dir1 = 'dir1'
    dir1_path = os.path.join(src_dir, dir1)
    os.mkdir(dir1_path)
    file2 = 'file2'
    file2_path = os.path.join(dir1_path, file2)
    open(file2_path, 'w').write('test')
    dir2 = 'dir2'
    dir2_path = os.path.join(src_dir, dir2)
    os.mkdir(dir2_path)

    # if not recursive we get the same result as the previous test
    check_list(expected_list, build_file_list([src_dir], vos_root))

    # now recursive
    expected_list = \
        [(src_dir, get_vos_path(src_dir, src_dir)),
         (dir1_path, get_vos_path(dir1_path, src_dir)),
         (dir2_path, get_vos_path(dir2_path, src_dir)),
         (file1_path, get_vos_path(file1_path, src_dir)),
         (file2_path, get_vos_path(file2_path, src_dir))]
    check_list(expected_list, build_file_list(
        [src_dir], vos_root, recursive=True))

    # repeat but now add "/" at the end of the source. The sync just
    # the content of the dir and not the dir itself
    src_dir_content = src_dir + '/'
    expected_list_content = \
        [(dir1_path, get_vos_path(dir1_path, src_dir_content)),
         (dir2_path, get_vos_path(dir2_path, src_dir_content)),
         (file1_path, get_vos_path(file1_path, src_dir_content)),
         (file2_path, get_vos_path(file2_path, src_dir_content))]
    check_list(expected_list_content, build_file_list(
        [src_dir_content], vos_root, recursive=True))

    # path='syncsrc' and vos_root='ivo://someservice/somepath' should generate
    # the same list as path='syncsrc/' and
    # vos_root='ivo://someservice/somepath/syncsrc' with the exception of
    # the entry corresponding to the 'syncsrc' directory which is not
    # generated in the second case (but assumed to already exist on server)
    expected_list.pop(0)
    check_list(expected_list, build_file_list(
        [src_dir_content], '{}/{}'.format(vos_root, src_dir_name),
        recursive=True))

    # filtered results
    expected_list = \
        [(src_dir, get_vos_path(src_dir, src_dir)),
         (dir1_path, get_vos_path(dir1_path, src_dir)),
         (file1_path, get_vos_path(file1_path, src_dir)),
         (file2_path, get_vos_path(file2_path, src_dir))]
    check_list(expected_list, build_file_list(
        [src_dir], vos_root, recursive=True, include="1"))

    # repeat with no recursive
    expected_list = \
        [(src_dir, get_vos_path(src_dir, src_dir)),
         (file1_path, get_vos_path(file1_path, src_dir))]
    check_list(expected_list, build_file_list(
        [src_dir], vos_root, recursive=False, include="1"))

    # filter with exclude
    expected_list = \
        [(src_dir, get_vos_path(src_dir, src_dir)),
         (dir1_path, get_vos_path(dir1_path, src_dir)),
         (file1_path, get_vos_path(file1_path, src_dir))]
    check_list(expected_list, build_file_list(
        [src_dir], vos_root, recursive=True, exclude="2"))

    # redo while doubling up the list
    check_list(expected_list, build_file_list(
        [src_dir]*2, vos_root, recursive=True, exclude="2"))

    # sync src_dir + a file
    expected_list.append((file1_path, '{}/{}'.format(vos_root, file1)))
    check_list(expected_list, build_file_list(
        [src_dir, file1_path], vos_root, recursive=True, exclude="2"))

    # error when the src file does not exist
    with pytest.raises(ValueError):
        build_file_list([src_dir, 'bogus'], vos_root)


def test_transfer_report():
    tr = TransferReport()
    assert not tr.files_erred
    assert not tr.files_sent
    assert not tr.files_skipped
    assert not tr.bytes_sent
    assert not tr.bytes_skipped


@module_patch('vos.commands.vsync.get_client')
def test_execute(get_client):
    now = datetime.datetime.timestamp(datetime.datetime.now())
    node = Mock(props={'MD5': 'beef'}, attr={'st_size': 3, 'st_ctime': now})
    get_node_mock = Mock(return_value=node)
    client_mock = Mock()
    client_mock.get_node = get_node_mock
    get_client.return_value = client_mock
    tmp_file = tempfile.NamedTemporaryFile()

    class Options:
        pass

    options = Options
    options.overwrite = True
    options.ignore_checksum = True
    options.certfile = None
    options.token = None
    options.cache_nodes = False
    options.insecure = False
    expected_report = TransferReport()
    expected_report.files_sent = 1
    assert expected_report == execute(tmp_file.name,
                                      'vos:service/path', options)

    # put some content in the file
    open(tmp_file.name, 'w').write('ABC')
    expected_report.bytes_sent = 3
    assert expected_report == execute(tmp_file.name,
                                      'vos:service/path', options)

    # no override, same md5 and older remote time = > no update
    now = datetime.datetime.timestamp(datetime.datetime.now())
    node.attr['st_ctime'] = now
    md5 = compute_md5(tmp_file.name)
    node.props['MD5'] = md5
    options.overwrite = False
    expected_report = TransferReport()
    expected_report.files_skipped = 1
    expected_report.bytes_skipped = 3
    assert expected_report == execute(tmp_file.name,
                                      'vos:service/path', options)

    # mismatched md5 but ignore checksum => no update
    node.props['MD5'] = 'beef'
    assert expected_report == execute(tmp_file.name,
                                      'vos:service/path', options)

    # mismached md5 and no ignore checksum => update
    options.ignore_checksum = False
    expected_report = TransferReport()
    expected_report.files_sent = 1
    expected_report.bytes_sent = 3
    assert expected_report == execute(tmp_file.name,
                                      'vos:service/path', options)

    # ignore checksum but mismatched size => update
    options.ignore_checksum = True
    node.props['MD5'] = md5
    node.attr['st_size'] = 7
    assert expected_report == execute(tmp_file.name,
                                      'vos:service/path', options)

    # stalled remote copy => update
    node.attr['st_size'] = 3
    node.attr['st_ctime'] = now - 10000
    assert expected_report == execute(tmp_file.name,
                                      'vos:service/path', options)

    # OSErrors on update
    client_mock.copy.side_effect = OSError('NodeLocked')
    expected_report = TransferReport()
    expected_report.files_erred = 1
    assert expected_report == execute(tmp_file.name,
                                      'vos:service/path', options)
