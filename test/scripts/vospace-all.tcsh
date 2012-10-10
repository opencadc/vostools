#!/bin/tcsh -f

echo
echo "*** start all tests ***"
echo "vospace-mountvospace-atest.tcsh"
vospace-mountvospace-atest.tcsh || echo "FAIL vospace-mountvospace-atest.tcsh" && exit -1 
echo "vospace-client-atest.tcsh"
vospace-client-atest.tcsh || echo "FAIL vospace-client-atest.tcsh" && exit -1
echo "vospace-move-atest.tcsh"
vospace-move-atest.tcsh || echo "FAIL vospace-move-atest.tcsh" && exit -1
echo "vospace-delete-permission-atest.tcsh"
vospace-delete-permission-atest.tcsh || echo "FAIL vospace-delete-permission-atest.tcsh" && exit -1
echo "vospace-quota-atest.tcsh"
vospace-quota-atest.tcsh || echo "FAIL vospace-quota-atest.tcsh" && exit -1
echo "vospace-link-atest.tcsh"
vospace-link-atest.tcsh || echo "FAIL vospace-link-atest.tcsh" && exit -1
echo "vospace-read-permission-atest.tcsh"
vospace-read-permission-atest.tcsh || echo "FAIL vospace-read-permission-atest.tcsh" && exit -1
echo "vospace-node-properties.tcsh"
vospace-node-properties.tcsh || echo "FAIL vospace-node-properties.tcsh" && exit -1


echo
echo "*** all test sequences passed ***"

date
