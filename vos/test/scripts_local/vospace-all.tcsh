#!/bin/tcsh -f

set THIS_DIR = `dirname $0`
set THIS_DIR = `cd $THIS_DIR && pwd`

#echo "Enter path to the CADC test certificates"
#echo -n "Cert path: "
#set certpath = "$<"
#setenv CADC_TESTCERT_PATH ${certpath}
#
#echo "cert files path:  $CADC_TESTCERT_PATH"
echo
echo "*** start all tests ***"
echo "vospace-client-atest.tcsh"
set args = ($argv)
$THIS_DIR/vospace-client-atest.tcsh $args || echo "FAIL vospace-client-atest.tcsh" && exit -1
echo "vospace-move-atest.tcsh"
$THIS_DIR/vospace-move-atest.tcsh $args || echo "FAIL vospace-move-atest.tcsh" && exit -1
echo "vospace-delete-permission-atest.tcsh"
$THIS_DIR/vospace-delete-permission-atest.tcsh $args || echo "FAIL vospace-delete-permission-atest.tcsh" && exit -1
echo "vospace-vsync-atest.tcsh"
$THIS_DIR/vospace-vsync-atest.tcsh $args || echo "FAIL vospace-vsync-atest.tcsh" && exit -1
#echo "vospace-quota-atest.tcsh"
#$THIS_DIR/vospace-quota-atest.tcsh $args || echo "FAIL vospace-quota-atest.tcsh" && exit -1
echo "vospace-link-atest.tcsh"
$THIS_DIR/vospace-link-atest.tcsh $args || echo "FAIL vospace-link-atest.tcsh" && exit -1
echo "vospace-read-permission-atest.tcsh"
$THIS_DIR/vospace-read-permission-atest.tcsh $args || echo "FAIL vospace-read-permission-atest.tcsh" && exit -1
echo "vospace-node-properties.tcsh"
$THIS_DIR/vospace-node-properties.tcsh $args || echo "FAIL vospace-node-properties.tcsh" && exit -1
echo "vospace-lock-atest.tcsh"
$THIS_DIR/vospace-lock-atest.tcsh $args || echo "FAIL vospace-lock-atest.tcsh" && exit -1

echo
echo "*** all test sequences passed ***"

date
