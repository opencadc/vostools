#!/bin/tcsh -f

echo
echo "*** start all tests ***"
vospace-mountvospace-atest.tcsh || echo "FAIL vospace-mountvospace-atest.tcsh" && exit -1 
vospace-client-atest.tcsh || echo "FAIL vospace-client-atest.tcsh" && exit -1
vospace-move-atest.tcsh || echo "FAIL vospace-move-atest.tcsh" && exit -1
vospace-delete-permission-atest.tcsh || echo "FAIL vospace-delete-permission-atest.tcsh" && exit -1
vospace-quota-atest.tcsh || echo "FAIL vospace-quota-atest.tcsh" && exit -1
vospace-link-atest.tcsh || echo "FAIL vospace-link-atest.tcsh" && exit -1
vospace-read-permission-atest.tcsh || echo "FAIL vospace-read-permission-atest.tcsh" && exit -1


echo
echo "*** test sequence passed ***"

date
