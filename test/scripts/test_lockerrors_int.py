#!/usr/bin/env python2.7

"""Integration test wrapped in unit test infrastructure.
Expects vospace_ws to be running on localhost, and
CADCRegtest1 vospace in database in DEVSYBASE.

The commands to create the database content for this test 
are in the script setup_test_lockerrors_int.sh.
"""

import os
import re
import unittest
from subprocess import Popen, PIPE, STDOUT

class VCLITest(unittest.TestCase):
    """
    Base class for running v* scripts
    """

    def setUp(self):
        # nothing to do for now
        pass

    def tearDown(self):
        # nothing to do for now
        pass

    def run_cmd(self, args, print_output=False, use_debug=False, expected_errno=255, time_execution=False):
        """
        This method will run the script in a subprocess,
        and pass it the given command line argument string.
        """ 

        self.use_debug = ''
        if use_debug:
            self.use_debug = '-d'

        self.time_execution = ''
        if time_execution:
            self.time_execution = 'time '
            
        cmd = '%s %s %s %s %s' %(self.time_execution, self.script_run, args, self.use_debug, self.test_cert_param)
        if print_output:
            print 'Running [%s]' % cmd

        try:
            p = Popen(cmd, shell=True, stdout=PIPE, stderr=STDOUT)
            output = p.communicate()[0]
            if print_output:
                print output

            self.assertEquals(p.returncode, expected_errno, 'got returncode %d, expected %d' % (p.returncode, expected_errno))
            regex = re.search('NodeLocked', output)
            self.assertTrue(regex != None, 'did not find "NodeLocked"' )

        except Exception, e:
            print 'Error running [%s]' % cmd
            print output
            self.fail(e)


class VcmdTest(VCLITest):
    """The execution of the commands under test should all fail
    with NodeLocked errors."""

    def setUp(self):
        self.script_run = 'python'
        self.test_vos = 'vos:CADCRegtest1/int_test'
        self.test_cert_param = '--cert=$A/test-certificates/x509_CADCRegtest1.pem'
        self.vcp_cmd = '../../scripts/vcp'
        self.vrm_cmd = '../../scripts/vrm'
        self.vsync_cmd = '../../scripts/vsync'
        self.vrmdir_cmd = '../../scripts/vrmdir'
        self.vmv_cmd = '../../scripts/vmv'

    def test_vcp_locked_data_node_dest(self):
        self.run_cmd('%s test1.out %s/test1.out' % (self.vcp_cmd, self.test_vos), expected_errno=1)

    def test_vcp_locked_container_node_dest(self):
        self.run_cmd('%s test1.out %s/locked_dir' % (self.vcp_cmd, self.test_vos), expected_errno=1)

    def test_vcp_locked_link_node_locked_data_node_dest(self):
        self.run_cmd('%s test1.out %s/locked_link_to_test1.out' % (self.vcp_cmd, self.test_vos), expected_errno=1)

    def test_vcp_unlocked_link_node_locked_data_node_dest(self):
        self.run_cmd('%s test1.out %s/unlocked_link_to_test1.out' % (self.vcp_cmd, self.test_vos), expected_errno=1)

    def test_vrm_locked_data_node_dest(self):
        self.run_cmd('%s %s/test1.out' % (self.vrm_cmd, self.test_vos), expected_errno=167)
 
    def test_vrm_locked_link_node_dest(self):
        self.run_cmd('%s %s/locked_link_to_test1.out' % (self.vrm_cmd, self.test_vos), expected_errno=167)

    def test_vrmdir_locked_container_node_dest(self):
        self.run_cmd('%s %s/locked_dir' % (self.vrmdir_cmd, self.test_vos))

    def test_vmv_locked_source_data_node(self):
        self.run_cmd('%s %s/test1.out %s/test2.out' % (self.vmv_cmd, self.test_vos, self.test_vos))
 
    def test_vmv_locked_dest_data_node(self):
        self.run_cmd('%s %s/test2.out %s/test1.out' % (self.vmv_cmd, self.test_vos, self.test_vos))
 
    def test_vmv_locked_dest_container_node(self):
        self.run_cmd('%s %s/test2.out %s/locked_dir' % (self.vmv_cmd, self.test_vos, self.test_vos))

    def test_vmv_locked_source_link_node(self):
        self.run_cmd('%s %s/locked_link_to_test2.out %s/test4.out' % (self.vmv_cmd, self.test_vos, self.test_vos))
 
    def test_vmv_locked_dest_parent_container_node(self):
        self.run_cmd('%s %s/test2.out %s/locked_dir/test2.out' % (self.vmv_cmd, self.test_vos, self.test_vos))

    def test_vsync_locked_dest_data_node(self):
        self.run_cmd('%s test1.out %s/test1.out' % (self.vsync_cmd, self.test_vos), expected_errno=0)
 
    def test_vsync_locked_dest_container_node(self):
        self.run_cmd('%s test1.out %s/locked_dir' % (self.vsync_cmd, self.test_vos), expected_errno=0)

    def test_vsync_locked_dest_container_node(self):
        self.run_cmd('%s test1.out %s/locked_dir' % (self.vsync_cmd, self.test_vos), expected_errno=0)

    def test_vsync_locked_dest_parent_container_node(self):
        self.run_cmd('%s test1.out %s/locked_dir/test2.out' % (self.vsync_cmd, self.test_vos), expected_errno=0)

#class FcmdTest(VcmdTest):
#    
#    def setUp(self):
#        self.script_run = ''
#        self.test_vos = '/tmp/vospace/int_test'
#        self.test_cert_param = ''
#        self.vcp_cmd = 'cp'

if __name__ == '__main__':
    os.environ['VOSPACE_WEBSERVICE'] = 'localhost'
    #os.environ['VOSPACE_WEBSERVICE'] = 'mach277.cadc.dao.nrc.ca'

#    # fuse test set-up
#    try:
#        # precautionary unmount
#        p = Popen('fusermount -u /tmp/vospace', shell=True, stdout=PIPE, stderr=STDOUT)
#        (output,returncode) = p.communicate()
#    except Exception, e:
#        # mostly, ignore errors
#        print output
#        print p.returncode
#        print str(e)
#
#    try:
#        p = Popen('../../scripts/mountvofs --vospace vos:CADCRegtest1 -d', shell=True, stdout=PIPE, stderr=STDOUT)
#        (output,returncode) = p.communicate()
#    except Exception, e:
#        print output
#        print p.returncode
#        print str(e)
#        import sys
#        sys.exit(-1)

    unittest.main()
