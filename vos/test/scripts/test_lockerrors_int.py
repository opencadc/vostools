# ***********************************************************************
# ******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
# *************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
#
#  (c) 2022.                            (c) 2022.
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
            print('Running [%s]' % cmd)

        try:
            p = Popen(cmd, shell=True, stdout=PIPE, stderr=STDOUT)
            output = p.communicate()[0]
            if print_output:
                print(output)

            self.assertEquals(p.returncode, expected_errno, 'got returncode %d, expected %d' % (p.returncode, expected_errno))
            regex = re.search('NodeLocked', output)
            self.assertTrue(regex != None, 'did not find "NodeLocked"' )

        except Exception as e:
            print('Error running [%s]' % cmd)
            print(output)
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
