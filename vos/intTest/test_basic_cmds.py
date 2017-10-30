from vos.commands import vls, vcp, vmkdir, vrm, vrmdir, vchmod
from six import StringIO
from cadcutils import net
from mock import patch, Mock
import pytest
import sys
import os

THIS_DIR = os.path.dirname(os.path.realpath(__file__))

cert1 = os.path.join(THIS_DIR, "certs/travis_inttest_proxy1.pem")
cert2 = os.path.join(THIS_DIR, "certs/travis_inttest_proxy2.pem")

basedir = "vos:adriand"
testdir = os.path.join(basedir, "cadcIntTest")


class MyExitError(Exception):

    def __init__(self):
        self.message = "MyExitError"

# to capture the output of executing a command, sys.exit is patched to
# throw an MyExitError exception. The number of such exceptions is based
# on the number of commands and the number of times they are invoked
outputs = [MyExitError] * (100)

@patch('sys.exit', Mock(side_effect=outputs))
def testHomeDir():
    """
    test it can reach home directory
    :return:
    """

    sys.argv = 'vls -l --certfile {} {}'.format(cert1, basedir).split()
    with patch('sys.stdout', new_callable=StringIO) as stdout_mock:
        #with pytest.raises(MyExitError):
        vls()

    print("Found base dir with CadcIntTest1")

    sys.argv = 'vls -l --certfile {} {}'.format(cert2, basedir).split()
    with patch('sys.stdout', new_callable=StringIO) as stdout_mock:
        #with pytest.raises(MyExitError):
        vls()

    print("Found base dir with CadcIntTest2")

    sys.argv = 'vls -l --certfile {} {}'.format(cert1, testdir).split()
    with patch('sys.stdout', new_callable=StringIO) as stdout_mock:
        #with pytest.raises(MyExitError):
        vls()

    print("Found test dir with CadcIntTest1")

    sys.argv = 'vls -l --certfile {} {}'.format(cert2, testdir).split()
    with patch('sys.stdout', new_callable=StringIO) as stdout_mock:
        with pytest.raises(MyExitError):
            vls()
    print("No access to test dir with CadcIntTest2")
