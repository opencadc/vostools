from vos.commands import vls, vcp, vmkdir, vrm, vrmdir, vchmod, vmv, vln, vsync
from vos.commands import vtag, vlock
from io import StringIO
from cadcutils import net
from mock import patch, Mock
import pytest
import sys
import os
import logging
import time
import filecmp
import shutil
import getpass
import hashlib
import tempfile

THIS_DIR = os.path.dirname(os.path.realpath(__file__))
DATA_DIR = os.path.join(THIS_DIR, "data")

cert1 = os.path.join(THIS_DIR, "certs/travis_inttest_proxy1.pem")
cert2 = os.path.join(THIS_DIR, "certs/travis_inttest_proxy2.pem")

user = getpass.getuser()
basedir = "vos:adriand"
quota_dir = 'vos:cadcinttest2' # TODO
testdir = os.path.join(basedir, "cadcIntTest")
container_name = '{}-{}'.format(user, time.strftime('%Y-%m-%dT%H-%M-%S'))
container = os.path.join(testdir, container_name)
tmp_dir = os.path.join('/tmp', 'test-{}'.format(container_name))

logger = logging.getLogger('vos')
logger.setLevel(logging.INFO)

# name of users
USER1 = 'cadcinttest1'
USER2 = 'cadcinttest2'
# access control groups
GR = 'CadcIT' # CadcIntTest1 and CadcIntTest2 members
GR1 = 'CadcIT1' # CadcIntTest1 member
GR2 = 'CadcIT2' # CadcIntTest2 member

one_test = False  # run just one test

class MyExitError(Exception):

    def __init__(self):
        self.message = "MyExitError"

# to capture the output of executing a command, sys.exit is patched to
# throw an MyExitError exception. The number of such exceptions is based
# on the number of commands and the number of times they are invoked
outputs = [MyExitError] * 100


@pytest.fixture(scope='module')
def work_dir():
    """
    creates work directory on vospace
    :return:
    """

    # check test sandbox dir exists and it's accessible by both users
    exec_cmd(vls, cert1, '{}'.format(testdir))
    exec_cmd(vls, cert2, '{}'.format(testdir))

    # create the test container specific to this run
    logger.info('Test container: {}'.format(container))
    stdout, sterr = exec_cmd(vls, cert1, '{}'.format(container), 1)
    assert 'NodeNotFound' in sterr

    exec_cmd(vmkdir, cert1, '{}'.format(container))
    stout, sterr = exec_cmd(vls, cert1, '-l {}'.format(testdir))
    assert_line(container_name, ['drw-rw----', USER1, GR, GR], stout)

    os.makedirs(tmp_dir)

    yield container

    # tear down part
    # remove test container
    print ('Tearing down test container')
    exec_cmd(vrmdir, cert1, container)

    shutil.rmtree(tmp_dir)


def assert_line(file, expected_fields, actual_output,
                ignore_timestamp=True):
    """
    Checks if expected fields are present in the actual line generated by
    an vls -l command
    :param file: name of the file to look for in actual output. If not present
    it checks every entry
    :param expected_fields: expected fields in the line. None values match
    all the values in the line.
    :param actual_output: actual generated line or lines
    :param ignore_timestamp: node timestamps are ignored in the comparison
    :raise AssertionError
    """
    found = False
    if not file:
        file = ''
    for line in actual_output.split('\n'):
        if (len(line.strip()) > 0) and\
            (('{} ->'.format(file) in line) or  # link
             (line.endswith(' {}'.format(file)))): # file or dir
            actual_fields = line.split()
            if ignore_timestamp:
                del actual_fields[5:8]
            for i, field in enumerate(expected_fields):
                if field is not None:
                    assert field == actual_fields[i]
            found = True
    assert found


def exec_cmd(command, cert=None, args=None, return_status=0):
    """
    Executes vos command and captures and returns the output
    :param command: function to run
    :param cert: certificate to use
    :param args: other args to pass to sys.argv as a string
    :param return_status: expected return status due to error
    :return: output of executing the command
    """
    cert_flag = ''
    if cert:
        cert_flag = '--certfile {}'.format(cert)
    sys.argv = '{} {} {}'.format(command.__name__, cert_flag, args).split()
    try:
        with patch('sys.stdout', new_callable=StringIO) as stdout_mock:
            with patch('sys.stderr', new_callable=StringIO) as stderr_mock:
                command()
    except SystemExit:
        # an early exit is expected only if return_status is not 0
        assert return_status != 0, stderr_mock.getvalue()
    output = stdout_mock.getvalue()
    stderr = stderr_mock.getvalue()
    logger.debug('Command output: {}'.format(output))
    logger.debug('Command err: {}'.format(stderr))
    return (output, stderr)


@pytest.mark.skipif(one_test, reason='One test mode')
#@pytest.mark.skipif(True, reason='Need to fix')
def test_container(work_dir):
    """ Some basic operations on the test container. """
    # ensure that the work_dir is not publicly readable
    stout, sterr = exec_cmd(vls, cert1, '-l {}'.format(os.path.dirname(work_dir)))
    # the mask from the parent directory
    assert_line(container_name, ['drw-rw----', USER1, GR, GR], stout)

    # check permissions are inherited
    exec_cmd(vmkdir, cert1, '{}/{}'.format(work_dir, 'pub'))

    stout, sterr = exec_cmd(vls, cert1, '-l {}'.format(work_dir))
    assert_line('pub', ['drw-rw----', USER1, GR, GR], stout)

    # create a private directory with different permissions
    exec_cmd(vmkdir, cert1, '{}/{}'.format(work_dir, 'priv'))

    # change permissions
    # remove permissions
    exec_cmd(vchmod, cert1, 'g-rw {}/{}'.format(work_dir, 'priv'))

    stout, sterr = exec_cmd(vls, cert1, '-l {}'.format(work_dir))
    assert_line('priv', ['drw-------', USER1, 'NONE', 'NONE'], stout)

    # add permissions
    exec_cmd(vchmod, cert1, 'g+r {}/{} {}'.format(work_dir, 'priv', GR2))
    stout, sterr = exec_cmd(vls, cert1, '-l {}'.format(work_dir))
    assert_line('priv', ['drw-r-----', USER1, GR2, 'NONE'], stout)

    # change permission
    exec_cmd(vchmod, cert1, 'g+r {}/{} {}'.format(work_dir, 'priv', GR1))

    stout, sterr = exec_cmd(vls, cert1, '-l {}'.format(work_dir))
    assert_line('priv', ['drw-r-----', USER1, GR1, 'NONE'], stout)

    # remove empty directory
    stout, sterr = exec_cmd(vrmdir, cert1, '{}/priv'.format(work_dir))
    stout, sterr = exec_cmd(vls, cert1, '{}/priv'.format(work_dir), 1)
    assert 'NodeNotFound' in sterr

    # remove non-empty directory
    exec_cmd(vmkdir, cert1, '{}/pub/test'.format(work_dir))
    stout, sterr = exec_cmd(vls, cert1, '-l {}/pub'.format(work_dir))
    assert_line('test', ['drw-rw----', USER1, GR, GR], stout)

    exec_cmd(vrmdir, cert1, '{}/pub'.format(work_dir))
    stout, sterr = exec_cmd(vls, cert1, '{}/pub'.format(work_dir), 1)
    assert 'NodeNotFound' in sterr

    # delete non-existing directory
    exec_cmd(vrmdir, cert1, '{}/pub'.format(work_dir), 1)
    assert 'NodeNotFound' in sterr

    # delete non-existing file
    exec_cmd(vrm, cert1, '{}/nofile.txt'.format(work_dir), 1)
    assert 'NodeNotFound' in sterr


@pytest.mark.skipif(one_test, reason='One test mode')
def test_gobling(work_dir):
    """ Tests copying a file to the container and back."""
    local_file_name = 'something.png'
    remote_test_dir = '{}/{}'.format(work_dir, 'glob')
    exec_cmd(vmkdir, cert1, remote_test_dir)
    exec_cmd(vls, cert1, '-l {}'.format(remote_test_dir))

    # create the following structure and test globing on it:
    # glob/test1/t1.txt
    # glob/test1/t12.txt
    # glob/test2/t22.txt
    # glob/test2/t2/
    # glob/atest/at

    exec_cmd(vmkdir, cert1, '{}/test1'.format(remote_test_dir))
    (f, tmp) = tempfile.mkstemp()
    exec_cmd(vcp, cert1, '--quick {} {}/test.txt'.format(tmp, remote_test_dir))
    exec_cmd(vcp, cert1, '{} {}/test1/t1.txt'.format(tmp, remote_test_dir))
    exec_cmd(vcp, cert1, '{} {}/test1/t12.txt'.format(tmp, remote_test_dir))
    exec_cmd(vmkdir, cert1, '{}/test2'.format(remote_test_dir))
    exec_cmd(vmkdir, cert1, '{}/test2/t2'.format(remote_test_dir))
    exec_cmd(vcp, cert1, '{} {}/test2/t22.txt'.format(tmp, remote_test_dir))
    exec_cmd(vmkdir, cert1, '{}/atest'.format(remote_test_dir))
    exec_cmd(vmkdir, cert1, '{}/atest/at'.format(remote_test_dir))

    stout, sterr = exec_cmd(vls, cert1, '{}/test*'.format(remote_test_dir))
    expected = 'test.txt\n\ntest1:\nt1.txt\nt12.txt\n\ntest2:\nt2\nt22.txt\n'
    assert expected == stout

    # detail display
    stout, sterr = exec_cmd(vls, cert1, '-l {}/test*'.format(remote_test_dir))
    assert 'test1:\n' in stout
    assert 'test2:\n' in stout
    assert_line('test.txt', ['-rw-rw----', USER1, GR, GR, '0'], stout)
    assert_line('t1.txt', ['-rw-rw----', USER1, GR, GR, '0'], stout)
    assert_line('t12.txt', ['-rw-rw----', USER1, GR, GR, '0'], stout)
    assert_line('t2', ['drw-rw----', USER1, GR, GR, '0'], stout)
    assert_line('t22.txt', ['-rw-rw----', USER1, GR, GR, '0'], stout)

    stout, sterr = exec_cmd(vls, cert1, '-l {}/*test*'.format(remote_test_dir))
    assert 'test1:\n' in stout
    assert 'test2:\n' in stout
    assert 'atest:\n' in stout
    assert_line('test.txt', ['-rw-rw----', USER1, GR, GR, '0'], stout)
    assert_line('t1.txt', ['-rw-rw----', USER1, GR, GR, '0'], stout)
    assert_line('t12.txt', ['-rw-rw----', USER1, GR, GR, '0'], stout)
    assert_line('t2', ['drw-rw----', USER1, GR, GR, '0'], stout)
    assert_line('t22.txt', ['-rw-rw----', USER1, GR, GR, '0'], stout)
    assert_line('at', ['drw-rw----', USER1, GR, GR, '0'], stout)

    stout, sterr = exec_cmd(vls, cert1, '-l {}/?test*'.format(remote_test_dir))
    # only the content of atest displayed
    assert_line('at', ['drw-rw----', USER1, GR, GR, '0'], stout)

    stout, sterr = exec_cmd(vls, cert1, '-l {}/test[2,3]'.format(
        remote_test_dir))
    # only the content of test2 is displayed
    assert_line('t2', ['drw-rw----', USER1, GR, GR, '0'], stout)
    assert_line('t22.txt', ['-rw-rw----', USER1, GR, GR, '0'], stout)

    # test vcp
    tempdir = tempfile.mkdtemp()
    stout, sterr = exec_cmd(vcp, cert1, '-d {}/test1/t* {}'.format(
        remote_test_dir, tempdir), 1)
    local_t1 = os.path.join(tempdir, 't1.txt')
    local_t12 = os.path.join(tempdir, 't12.txt')
    assert os.path.isfile(local_t1)
    assert os.path.isfile(local_t12)

    # repeat
    os.remove(local_t1)
    os.remove(local_t12)
    exec_cmd(vcp, cert1, '-d {}/test1/t*2* {}/'.format(
        remote_test_dir, tempdir))
    assert not os.path.isfile(local_t1)
    assert os.path.isfile(local_t12)


#@pytest.mark.skipif(one_test, reason='One test mode')
def test_vls(work_dir):
    """ Tests directory and file listing. The directory is statically
    created and this test ensures that, whenever possible, 'vls' command uses
    the same rules as the 'ls' counterpart to display and sort the content"""
    lsdir = 'vlsdir'
    tempdir = tempfile.mkdtemp()
    rlsdir = '{}/DONOTDELETE/{}'.format(testdir,lsdir)
    exec_cmd(vcp, cert1, '{} {}'.format(rlsdir, tempdir))
    llsdir = '{}/{}'.format(tempdir, lsdir)

    def cmp_cmds(opt, args):
        # make directories absolute
        rargs = ' '.join(['{}/{}'.format(rlsdir, i) for i in args.split()])
        largs = ' '.join(['{}/{}'.format(llsdir, i) for i in args.split()])
        if not rargs:
            rargs = rlsdir
        if not largs:
            largs = llsdir
        assert os.popen('ls -1 {} {}'.format(opt, largs)).read().replace(
            '{}/'.format(llsdir), '') == \
            exec_cmd(vls, cert1, '{} {}'.format(opt, rargs))[0], \
            '[error]: {} (opt) - {} (args)'.format(opt, args)

    cmp_cmds('', '')  # default listing
    cmp_cmds('-r', '')
    cmp_cmds('', '*')
    cmp_cmds('-r', '*.txt')
    cmp_cmds('', 'a*')
    cmp_cmds('', '[ac]*')
    cmp_cmds('-r', 'a b a.txt')
    cmp_cmds('-S', '*.txt a/*.txt')
    # Note size of directories different in vosapce and os
    # Note 2 - can't compare timestamps since scp does not preserve them


@pytest.mark.skipif(one_test, reason='One test mode')
def test_file_copy(work_dir):
    """ Tests copying a file to the container and back."""
    local_file_name = 'something.png'
    local_file = os.path.join(DATA_DIR, local_file_name)
    remote_file = '{}/{}'.format(work_dir, local_file_name)
    exec_cmd(vcp, cert1, '{} {}'.format(local_file, work_dir))
    stout, sterr = exec_cmd(vls, cert1, '-l {}'.format(work_dir))
    assert_line(local_file_name, ['-rw-rw----', USER1, GR, GR, '497927'], stout)

    tmp_file = os.path.join(tmp_dir, local_file_name)
    # copy the file back to the local file system and compare with original
    exec_cmd(vcp, cert1, '{} {}'.format(remote_file, tmp_dir))
    assert filecmp.cmp(local_file, tmp_file)

    # repeat the tests with the quick flag
    exec_cmd(vrm, cert1, '{}/{}'.format(work_dir, local_file_name))
    stout, sterr = exec_cmd(vls, cert1, '{}/{}'.format(container, local_file_name), 1)
    assert 'NodeNotFound' in sterr

    os.remove(tmp_file)
    exec_cmd(vcp, cert1, '--quick {} {}'.format(local_file, work_dir))
    stout, sterr = exec_cmd(vls, cert1, '-l {}'.format(work_dir))
    assert_line(local_file_name, ['-rw-rw----', USER1, GR, GR, '497927'], stout)

    # copy the file back to the local file system and compare with original
    exec_cmd(vcp, cert1, '--quick {} {}'.format(remote_file, tmp_dir))
    assert filecmp.cmp(local_file, tmp_file)

    # check download with a wildcard and override
    exec_cmd(vcp, cert1, '--quick {} {}'.\
        format(remote_file.replace('png', '*'), tmp_dir))

    assert filecmp.cmp(local_file, tmp_file)
    os.remove(tmp_file)


@pytest.mark.skipif(one_test, reason='One test mode')
def test_zero_lenght_files(work_dir):
    src_zerosize_file_name = 'src_zerosize.txt'
    src_zerosize_file = os.path.join(tmp_dir, src_zerosize_file_name)

    # create zero size file
    open(src_zerosize_file, 'w')
    exec_cmd(vcp, cert=cert1,
             args='--quick {} {}'.format(src_zerosize_file, work_dir))
    stout, sterr = exec_cmd(vls, cert=cert1, args='-l {}'.format(work_dir))
    assert_line(src_zerosize_file_name,
                ['-rw-rw----', USER1, GR, GR, '0', src_zerosize_file_name],
                stout)

    # repeat
    exec_cmd(vcp, cert=cert1,
             args='--quick {} {}'.format(src_zerosize_file, work_dir))
    stout, sterr = exec_cmd(vls, cert=cert1, args='-l {}'.format(work_dir))
    assert_line(src_zerosize_file_name,
                ['-rw-rw----', USER1, GR, GR, '0', src_zerosize_file_name],
                stout)

    # download
    dest_file = os.path.join(tmp_dir, 'dest_zerosize.txt')
    exec_cmd(vcp, cert1, '{}/{} {}'.format(work_dir,
             src_zerosize_file_name, dest_file, 1))
    assert filecmp.cmp(src_zerosize_file, dest_file)

    # change the size of the file and repeat
    with open(src_zerosize_file, 'w') as f:
        f.write('Test')
    exec_cmd(vcp, cert=cert1,
             args='--quick {} {}'.format(src_zerosize_file, work_dir))
    stout, sterr = exec_cmd(vls, cert=cert1, args='-l {}'.format(work_dir))
    assert_line(src_zerosize_file_name,
                ['-rw-rw----', USER1, GR, GR, '4', src_zerosize_file_name],
                stout)

    # download
    exec_cmd(vcp, cert1, '{}/{} {}'.format(work_dir,
                                           src_zerosize_file_name, dest_file))
    assert filecmp.cmp(src_zerosize_file, dest_file)

    # make it back to size zero and repeat
    os.remove(src_zerosize_file)
    open(src_zerosize_file, 'w')
    exec_cmd(vcp, cert=cert1,
            args='--quick {} {}'.format(src_zerosize_file, work_dir))
    stout, sterr = exec_cmd(vls, cert=cert1, args='-l {}'.format(work_dir))
    assert_line(src_zerosize_file_name,
                ['-rw-rw----', USER1, GR, GR, '0', src_zerosize_file_name],
                stout)
    # download
    exec_cmd(vcp, cert1, '{}/{} {}'.format(work_dir,
                                           src_zerosize_file_name, dest_file))
    assert filecmp.cmp(src_zerosize_file, dest_file)


@pytest.mark.skipif(one_test, reason='One test mode')
def test_cutout_downloads(work_dir):
    """
    Uploads a test file and performs cutout operations
    :param work_dir:
    :return:
    """
    src_cutout_file_name = 'cutout_test.fits'
    src_cutout_file = os.path.join(DATA_DIR, src_cutout_file_name)

    exec_cmd(vcp, cert1, '{} {}'.format(src_cutout_file, work_dir))
    stout, sterr = exec_cmd(vls, cert1, '-l {}'.format(work_dir))
    assert_line(src_cutout_file_name, ['-rw-rw----', USER1, GR, GR], stout)

    # download cutout
    cutout_file = os.path.join(tmp_dir, 'cut.fits')
    exec_cmd(vcp, cert1, '{}/{}[1] {}'.format(work_dir, src_cutout_file_name,
                                              cutout_file))

    assert '7eacb9c83d782bb4c927378a7073c9a1' == \
        hashlib.md5(open(cutout_file, 'rb').read()).hexdigest()
    os.remove(cutout_file)

    # repeat with different cutout and use wild cards
    cutout_file = os.path.join(tmp_dir, 'cut.fits')
    stout, sterr = exec_cmd(vcp, cert1, '{}/cut*fits[1:10,1:10] {}'.format(
        work_dir, cutout_file))

    assert '5e2a31e042bc26193089c3fd6287e5a8' == \
           hashlib.md5(open(cutout_file, 'rb').read()).hexdigest()
    os.remove(cutout_file)


@pytest.mark.skipif(one_test, reason='One test mode')
def test_move(work_dir):
    local_test_file = os.path.join(DATA_DIR, 'something.png')
    exec_cmd(vmkdir, cert1, '{}/a'.format(work_dir))
    exec_cmd(vmkdir, cert1, '{}/a/aa'.format(work_dir))
    exec_cmd(vcp, cert1, '{} {}/a/aa/aaa'.format(local_test_file, work_dir))
    stout, sterr = exec_cmd(vls, cert1, '-l {}/a/aa'.format(work_dir))
    assert_line('aaa', ['-rw-rw----', USER1, GR, GR, '497927'], stout)

    # mv (rename) file
    exec_cmd(vmv, cert1, '{0}/a/aa/aaa {0}/a/aa/bbb'.format(work_dir))
    stout, sterr = exec_cmd(vls, cert1, '-l {}/a/aa'.format(work_dir))
    assert_line('bbb', ['-rw-rw----', USER1, GR, GR, '497927'], stout)

    # mv (rename) subdirectory
    exec_cmd(vmv, cert1, '{0}/a/aa {0}/a/bb'.format(work_dir))
    stout, sterr = exec_cmd(vls, cert1, '-l {}/a/bb'.format(work_dir))
    assert_line('bbb', ['-rw-rw----', USER1, GR, GR, '497927'], stout)

    # mv (rename) parent directory
    exec_cmd(vmv, cert1, '{0}/a {0}/b'.format(work_dir))
    stout, sterr = exec_cmd(vls, cert1, '-l {}/b/bb'.format(work_dir))
    assert_line('bbb', ['-rw-rw----', USER1, GR, GR, '497927'], stout)

    # mv data from one directory to another
    exec_cmd(vcp, cert1, '{} {}/aaa'.format(local_test_file, work_dir))
    exec_cmd(vmv, cert1, '{0}/aaa {0}/b/bb/'.format(work_dir))
    stout, sterr = exec_cmd(vls, cert1, '-l {}/b/bb'.format(work_dir))
    assert_line('aaa', ['-rw-rw----', USER1, GR, GR, '497927'], stout)
    assert_line('bbb', ['-rw-rw----', USER1, GR, GR, '497927'], stout)
    stout, sterr = exec_cmd(vls, cert1, '{}/aaa'.format(work_dir), 1)
    assert 'NodeNotFound' in sterr

    # Error cases
    # mv data when destination data node exists (duplicate node error)
    # mv data node to existing data node
    stout, sterr = exec_cmd(vmv, cert1, '{0}/b/bb/aaa {0}/b/bb/bbb'.format(work_dir), 1)
    assert 'DuplicateNode destination is not a container' in sterr
    stout, sterr = exec_cmd(vls, cert1, '-l {}/b/bb'.format(work_dir))
    assert_line('aaa', ['-rw-rw----', USER1, GR, GR, '497927'], stout)
    assert_line('bbb', ['-rw-rw----', USER1, GR, GR, '497927'], stout)

    # mv container node to existing data node
    exec_cmd(vmkdir, cert1, '{}/a'.format(work_dir))
    stout, sterr = exec_cmd(vmv, cert1, '{0}/a {0}/b/bb/bbb'.format(work_dir), 1)
    assert 'DuplicateNode destination is not a container' in sterr

    # circular move
    stout, sterr = exec_cmd(vmv, cert1, '{0}/b {0}/b/bb'.format(work_dir), 1)
    # not a very informative message
    assert 'Invalid Argument (target node is not a DataNode)' in sterr

    # no source node
    stout, sterr = exec_cmd(vmv, cert1, '{0}/cc {0}/b'.format(work_dir), 1)
    assert 'NodeNotFound' in sterr

    # no destination node
    stout, sterr = exec_cmd(vmv, cert1, '{0}/b {0}/c/cc'.format(work_dir), 1)
    assert 'NodeNotFound' in sterr

    # source is local directory
    stout, sterr = exec_cmd(vmv, cert1, '/tmp {}'.format(work_dir), 1)
    assert 'No scheme in /tmp' in sterr

    # destination is local directory
    stout, sterr = exec_cmd(vmv, cert1, '{}/b /tmp'.format(work_dir), 1)
    # Another non information message
    assert 'InternalFault (invalid direction: Direction[/tmp])' in sterr

    # source and destination are local directories
    stout, sterr = exec_cmd(vmv, cert1, '. /tmp', 1)
    assert 'No scheme in .' in sterr


@pytest.mark.skipif(one_test, reason='One test mode')
def test_access(work_dir):
    # test user only access directory
    user1_dir = 'user1'
    test_file_name = 'something.png'
    test_file = os.path.join(DATA_DIR, test_file_name)
    exec_cmd(vmkdir, cert1, '{}/{}'.format(work_dir, user1_dir))
    exec_cmd(vchmod, cert1, 'og-wr {}/{}'.format(work_dir, user1_dir))
    exec_cmd(vcp, cert1, '{} {}/{}/'.format(test_file, work_dir, user1_dir))
    stout, sterr = exec_cmd(vls, cert1, '-l {}/{}'.format(work_dir, user1_dir))
    assert_line(test_file_name, ['-rw-------', USER1, 'NONE', 'NONE', '497927'], stout)

    #TODO user2 cannot list, copy from/to, move from/to the directory, delete
    stout, sterr = exec_cmd(vcp, cert2, '{}/{}/{} .'.format(work_dir, user1_dir, test_file_name), return_status=1)
    assert 'PermissionDenied' in sterr
    stout, sterr = exec_cmd(vcp, cert2, '{} {}/{}/test.txt'.format(test_file, work_dir, user1_dir), return_status=1)
    assert 'PermissionDenied' in sterr
    stout, sterr = exec_cmd(vmkdir, cert2, '{}/{}/somedir'.format(work_dir, user1_dir), return_status=1)
    assert 'PermissionDenied' in sterr
    stout, sterr = exec_cmd(vmv, cert2, '{1}/{2}/{0} {1}/{2}/test.txt'.format(
        test_file_name, work_dir, user1_dir), return_status=1)
    assert 'PermissionDenied' in sterr

    # repeat after adding read/write access to gr1 (user 2 not a member)

    # repeat after adding gr2 as read group

    # repeat after adding gr2 as write group

    # repeat after make public


@pytest.mark.skipif(one_test, reason='One test mode')
@pytest.mark.skipif(True, reason='To fix')
def test_link(work_dir):
    test_file_name = 'something.png'
    test_file = os.path.join(DATA_DIR, test_file_name)
    exec_cmd(vmkdir, cert1, '-d {}/target'.format(work_dir))
    exec_cmd(vln, cert1, '{0}/target {0}/link'.format(work_dir))
    #upload file through the directory link
    exec_cmd(vcp, cert1, '{} {}/link/'.format(test_file, work_dir))
    # check it's there
    stout, sterr = exec_cmd(vls, cert1, '-l {}/target'.format(work_dir))
    assert_line(test_file_name, ['-rw-rw----', USER1, GR, GR, '497927'], stout)

    # access file through the link as user2
    # list the link
    stout, sterr = exec_cmd(vls, cert2, '-l {}'.format(work_dir))
    # list the target
    assert_line('link',
                ['lrw-rw----', USER1, GR, GR,
                 '0', 'link', '->', '{}/target'.format(work_dir).replace(
                    'vos:', 'vos://cadc.nrc.ca!vospace/')], stout)
    stout, sterr = exec_cmd(vls, cert2, '-l {}/link/'.format(work_dir))
    assert_line(test_file_name, ['-rw-rw----', USER1, GR, GR, '497927'], stout)
    # download
    exec_cmd(vcp, cert2, '{}/link/{} /{}/linktest.png'.format(
        work_dir, test_file_name, tmp_dir))
    assert filecmp.cmp(test_file, '{}/linktest.png'.format(tmp_dir))
    os.remove('/{}/linktest.png'.format(tmp_dir))

    # repeat the tests with a link to the file
    exec_cmd(vln, cert1, '{0}/target/{1} {0}/lsomething.png'.format(
        work_dir, test_file_name))

    # access file through the link as user2
    # list
    stout, sterr = exec_cmd(vls, cert2, '-l {}'.format(work_dir))
    assert_line('lsomething.png',
                ['lrw-rw----', USER1, GR, GR, '0', 'lsomething.png', '->',
                 '{}/target/{}'.format(work_dir, test_file_name).replace(
                    'vos:', 'vos://cadc.nrc.ca!vospace/')], stout)
    # download (vcp only follows links with -L flag)
    exec_cmd(vcp, cert2, '{}/lsomething.png {}/linktest.png'.format(
        work_dir, tmp_dir))
    assert not os.path.isfile('{}/linktest.png'.format(tmp_dir))

    exec_cmd(vcp, cert2, '-L {}/lsomething.png {}/linktest.png'.format(
        work_dir, tmp_dir))
    assert filecmp.cmp(test_file, '{}/linktest.png'.format(tmp_dir))
    os.remove('/{}/linktest.png'.format(tmp_dir))

    # test link of link
    # directory
    exec_cmd(vln, cert1, '{0}/link {0}/link1'.format(work_dir))
    stout, sterr = exec_cmd(vls, cert2, '-l {}'.format(work_dir))
    assert_line('link1', ['lrw-rw----', USER1, GR, GR, '0', 'link1', '->',
                          '{}/link'.format(work_dir).replace(
                          'vos:', 'vos://cadc.nrc.ca!vospace/')], stout)
    stout, sterr = exec_cmd(vls, cert2, '-l {}/link1/'.format(work_dir))
    assert_line(test_file_name, ['-rw-rw----', USER1, GR, GR, '497927'], stout)
    exec_cmd(vcp, cert2, '-L {}/link1/{} {}/linktest.png'.format(
        work_dir, test_file_name, tmp_dir))
    assert filecmp.cmp(test_file, '{}/linktest.png'.format(tmp_dir))
    os.remove('/{}/linktest.png'.format(tmp_dir))
    # file
    exec_cmd(vln, cert1, '{0}/lsomething.png {0}/lsomething1.png'.format(
        work_dir))
    exec_cmd(vcp, cert2, '-L {}/lsomething1.png {}/linktest.png'.format(
        work_dir, tmp_dir))
    assert filecmp.cmp(test_file, '{}/linktest.png'.format(tmp_dir))
    os.remove('/{}/linktest.png'.format(tmp_dir))

    #TODO enable
    # change destination permission to remove access
    exec_cmd(vchmod, cert1, 'go-rw {}/target/something.png'.format(work_dir))
    # vcp should failed event when performed through the links (dir or file)
    stout, sterr = exec_cmd(vcp, cert2, '-d {}/link/{} /{}/linktest.png'.format(
        work_dir, test_file_name, tmp_dir), 1)
    assert 'PermissionDenied' in sterr
    stout, sterr = exec_cmd(vcp, cert2, '-L {}/lsomething.png /{}/linktest.png'.format(
        work_dir, test_file_name, tmp_dir), 1)
    assert 'PermissionDenied' in sterr
    # even vls should fail
    stout, sterr = exec_cmd(vls, cert2, '{}/link/{}'.format(work_dir, test_file_name), 1)
    assert 'PermissionDenied' in sterr
    #TODO add tests for links of links


    # remove link to file as directory - fail
    stout, sterr = exec_cmd(vrm, cert1, '{}/lsomething1.png/'.format(work_dir), 1)
    assert '{}/lsomething1.png/ is not a directory'.format(work_dir) in sterr
    exec_cmd(vls, cert1, '{}/lsomething1.png'.format(work_dir))

    exec_cmd(vrm, cert1, '{}/lsomething1.png'.format(work_dir))
    stout, sterr = exec_cmd(vls, cert1, '{}/lsomething1.png'.format(work_dir), 1)
    assert 'NodeNotFound' in sterr

    # remove link to directory
    exec_cmd(vrm, cert1, '{}/link'.format(work_dir))
    stout, sterr = exec_cmd(vls, cert1, '{}/link'.format(work_dir), 1)
    assert 'NodeNotFound' in sterr

    # Fail cases

    # Target does not exist
    stout, sterr = exec_cmd(vls, cert1, '{0}/notarget {0}/nolink'.format(work_dir), 1)
    assert 'NodeNotFound' in sterr

    # Unknown authority
    exec_cmd(vln, cert1,
             'vos://unknown.authority~vospace/unknown {}/badlink'.format(
                 work_dir))

    # vls succeeds when listing the link (no end backslash) ...
    stout, sterr = exec_cmd(vls, cert1, '-l {}/badlink'.format(work_dir))
    assert_line('badlink',
                ['lrw-rw----', USER1, GR, GR, '0', 'badlink',
                 '->', 'vos://unknown.authority~vospace/unknown'], stout)
    # ... but fail when trying to list the content (end backslash)
    stout, sterr = exec_cmd(vls, cert1, '-l {}/badlink/'.format(work_dir), 1)
    assert 'Resource ID ivo://unknown.authority/vospace not found' in sterr

    # vcp a file fails
    stout, sterr = exec_cmd(vcp, cert2, '{}/badlink/somefile .'.format(work_dir), 1)
    assert 'UnreadableLinkTarget' in sterr

    exec_cmd(vrm, cert1, '{}/badlink'.format(work_dir))
    stout, sterr = exec_cmd(vls, cert1, '{}/badlink'.format(work_dir), 1)
    assert 'NodeNotFound' in sterr

    # unkown scheme, vln does not even create the link
    stout, sterr = exec_cmd(vln, cert1,
             'unknown://cadc.nrc.ca~vospace/CADCRegtest1 {}/badlink'.format(
                 work_dir), 1)
    assert 'source must be vos node or http url, target must be vos node' in sterr
    assert not os.path.islink('{}/badlink'.format(work_dir))

    # similar outcome when target is not vos node
    stout, sterr = exec_cmd(vln, cert1, '{} .'.format(work_dir), 1)
    assert 'source must be vos node or http url, target must be vos node' in sterr
    assert not os.path.islink('{}/badlink'.format(work_dir))


    # External link
    exec_cmd(vln, cert1,
             'http://www.google.ca {}/extlink'.format(
                 work_dir))
    # vls succeeds. Should it?
    stout, sterr = exec_cmd(vls, cert1, '-l {}/extlink'.format(work_dir))
    assert_line('extlink', ['lrw-rw----', USER1, GR, GR, '0', 'extlink', '->',
                            'http://www.google.ca'], stout)
    stout, sterr = exec_cmd(vls, cert1, '-l {}/extlink/'.format(work_dir))
    assert_line('google', ['-rw-rw----', 'unknown_000'], stout)
    exec_cmd(vrm, cert1, '{}/extlink'.format(work_dir))

    exec_cmd(vln, cert1,
             'http://www.blah.blah {}/badlink'.format(
                 work_dir))
    # vls succeeds. Should it?
    stout, sterr = exec_cmd(vls, cert1, '-l {}/badlink'.format(work_dir))
    assert_line('badlink', ['lrw-rw----', USER1, GR, GR, '0', 'badlink', '->',
                            'http://www.blah.blah'], stout)
    stout, sterr = exec_cmd(vls, cert1, '-l {}/badlink/'.format(work_dir), 1)
    assert 'NewConnectionError' in sterr

    # vcp a file fails
    stout, sterr = exec_cmd(vcp, cert2, '{}/badlink/somefile .'.format(work_dir), 1)
    assert 'UnreadableLinkTarget' in sterr
    exec_cmd(vrm, cert1, '{}/badlink'.format(work_dir))
    stout, sterr = exec_cmd(vls, cert1, '{}/badlink'.format(work_dir), 1)
    assert 'NodeNotFound' in sterr


@pytest.mark.skipif(one_test, reason='One test mode')
def test_vsync(work_dir):
    """
    Tests vsync functionality
    :param work_dir:
    :return:
    """
    #
    os.makedirs('{}/tosync'.format(tmp_dir))
    exec_cmd(vsync, cert1, '{}/tosync {}/'.format(tmp_dir, work_dir))
    stout, sterr = exec_cmd(vls, cert1, '-l {}'.format(work_dir))
    assert_line('tosync', ['drw-rw----', USER1, GR, GR, '0'], stout)
    # run it again to make sure it does not block
    exec_cmd(vsync, cert1, '{}/tosync {}/'.format(tmp_dir, work_dir))

    # rsync a few empty files
    open('{}/tosync/test1.txt'.format(tmp_dir), 'w+').close()
    open('{}/tosync/test2.txt'.format(tmp_dir), 'w+').close()
    open('{}/tosync/test3.txt'.format(tmp_dir), 'w+').close()
    exec_cmd(vsync, cert1, '-r {}/tosync {}/'.format(tmp_dir, work_dir), 1)
    stout, sterr = exec_cmd(vls, cert1, '-l {}/tosync'.format(work_dir))
    assert_line('test1.txt', ['-rw-rw----', USER1, GR, GR, '0'], stout)
    assert_line('test2.txt', ['-rw-rw----', USER1, GR, GR, '0'], stout)
    assert_line('test3.txt', ['-rw-rw----', USER1, GR, GR, '0'], stout)

    # change size
    with open('{}/tosync/test2.txt'.format(tmp_dir), 'w') as f:
        f.write("This is a test")

    with open('{}/tosync/test3.txt'.format(tmp_dir), 'w') as f:
        f.write("This is yet anoter test")

    exec_cmd(vsync, cert1, '{}/tosync {}/'.format(tmp_dir, work_dir))
    stout, sterr = exec_cmd(vls, cert1, '-l {}/tosync'.format(work_dir))
    assert_line('test1.txt', ['-rw-rw----', USER1, GR, GR, '0'], stout)
    assert_line('test2.txt', ['-rw-rw----', USER1, GR, GR, '14'], stout)
    assert_line('test3.txt', ['-rw-rw----', USER1, GR, GR, '23'], stout)


@pytest.mark.skipif(one_test, reason="One test mode")
@pytest.mark.skipif(True, reason="Need to create vospace allocation")
def test_quota():
    # TODO create a very small (10-100kB) vospace for cert2 user
    stout, sterr = exec_cmd(
        vcp, cert2, '{}/something.png {}'.format(testdir, quota_dir), 1)
    assert 'quota' in sterr


@pytest.mark.skipif(one_test, reason="One test mode")
def test_properties(work_dir):
    lock_flag = 'ivo://cadc.nrc.ca/vospace/core#islocked'

    exec_cmd(vmkdir, cert1, '{}/testlock'.format(work_dir))
    stout, sterr = exec_cmd(vtag, cert1,
                            '{}/testlock {}'.format(work_dir, lock_flag))
    assert 'None' in stout

    # lock the directory
    exec_cmd(vlock, cert1, '--lock {}/testlock'.format(work_dir))
    stout, sterr = exec_cmd(vtag, cert1,
                            '{}/testlock {}'.format(work_dir, lock_flag))
    assert 'true' in stout

    # unlock the directory
    exec_cmd(vlock, cert1, '--unlock {}/testlock'.format(work_dir))
    stout, sterr = exec_cmd(vtag, cert1,
                            '{}/testlock {}'.format(work_dir, lock_flag))
    assert 'None' in stout




