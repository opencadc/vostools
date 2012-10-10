#!/bin/tcsh -f

echo "###################"
if (! ${?CADC_ROOT} ) then
	set CADC_ROOT = "/usr/cadc/local"
endif
echo "using CADC_ROOT = $CADC_ROOT"

if (! ${?VOSPACE_WEBSERVICE} ) then
	echo "VOSPACE_WEBSERVICE env variable not set, use default WebService URL"
else
	echo "WebService URL (VOSPACE_WEBSERVICE env variable): $VOSPACE_WEBSERVICE"
endif
echo "###################"

set CHMODCMD = "python $CADC_ROOT/vchmod"
set MKDIRCMD = "python $CADC_ROOT/vmkdir"
set LSCMD = "python $CADC_ROOT/vls -l"
set CPCMD = "python $CADC_ROOT/vcp"
set RMDIRCMD = "python $CADC_ROOT/vrmdir"

set CERT =  "--cert=$A/test-certificates/x509_CADCRegtest1.pem"
set CERT1 = "--cert=$A/test-certificates/x509_CADCAuthtest1.pem"
set CERT2 = "--cert=$A/test-certificates/x509_CADCAuthtest2.pem"

echo "vchmod command: " $CHMODCMD $CERT
echo "vchmod command 1:    " $CHMODCMD $CERT1
echo "vchmod command 2:    " $CHMODCMD $CERT2

# group 3000 aka CADC_TEST1-Staff has members: CADCAuthtest1
set GROUP1 = "CADC_TEST1-Staff"

# group 3100 aka CADC_TEST2-Staff has members: CADCAuthtest1, CADCAuthtest2
set GROUP2 = "CADC_TEST2-Staff"

# using a test dir makes it easier to cleanup a bunch of old/failed tests
set VOHOME = "vos://cadc.nrc.ca\!vospace/CADCRegtest1"
set BASE = "$VOHOME/atest"

set TIMESTAMP=`date +%Y-%m-%dT%H-%M-%S`
set CONTAINER = $BASE/$TIMESTAMP

echo -n "** checking base URI"
$LSCMD $CERT $BASE > /dev/null
if ( $status == 0) then
    echo " [OK]"
else
    echo -n ", creating base URI"
        $MKDIRCMD $CERT $BASE || echo " [FAIL]" && exit -1
    echo " [OK]"
endif

echo -n "** setting home and base to public, no groups"
$CHMODCMD $CERT o+r $VOHOME || echo " [FAIL]" && exit -1
echo -n " [OK]"
$CHMODCMD $CERT o+r $BASE || echo " [FAIL]" && exit -1
echo " [OK]"
echo
echo "*** starting test sequence ***"
echo
echo "** test container: ${CONTAINER}"
echo

echo -n "create container "
$MKDIRCMD $CERT $CONTAINER ||  echo " [FAIL]" && exit -1
$CHMODCMD $CERT o+r $CONTAINER ||  echo " [FAIL]" && exit 1
$CHMODCMD $CERT g+w $CONTAINER $GROUP2 ||  echo " [FAIL]" && exit -1

echo -n " verify "
$LSCMD $CERT $CONTAINER > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "create container/sub1 (inherit permissions) "
$MKDIRCMD $CERT $CONTAINER/sub1 || echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $CONTAINER/sub1 > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "create container/sub1/sub2 (group-write=$GROUP1) "
$MKDIRCMD $CERT $CONTAINER/sub1/sub2 || echo " [FAIL]" && exit -1
$CHMODCMD $CERT g+w $CONTAINER/sub1/sub2 $GROUP1 || echo " [FAIL]" && exit -1
$LSCMD $CERT $CONTAINER/sub1/sub2 > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "put data node in sub2 "
$CPCMD $CERT something.png $CONTAINER/sub1/sub2/something.png || echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $CONTAINER/sub1/sub2/something.png > /dev/null || echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $CONTAINER/sub1/sub2/something.png | grep -q "$GROUP1" || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "test delete container/sub1 (denied)"
$RMDIRCMD $CERT2 $CONTAINER/sub1/sub2 >& /dev/null && echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $CONTAINER/sub1/sub2/something.png > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "test delete container/sub1 (allowed)"
$RMDIRCMD $CERT1 $CONTAINER/sub1 >& /dev/null || echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $CONTAINER/sub1 >& /dev/null && echo " [FAIL]" && exit -1
echo " [OK]"

# start fresh

echo -n "create container/sub1 "
$MKDIRCMD $CERT $CONTAINER/sub1 || echo " [FAIL]" && exit -1
$CHMODCMD $CERT o-r $CONTAINER/sub1 || echo " [FAIL]" && exit -1
$CHMODCMD $CERT g+w $CONTAINER/sub1 $GROUP1 || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "put data node in container/sub1 (allowed) "
$CPCMD $CERT1 something.png $CONTAINER/sub1/something.png || echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT1 $CONTAINER/sub1/something.png > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "test root read "
$LSCMD $CERT $CONTAINER/sub1 > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "test root delete"
$RMDIRCMD $CERT $CONTAINER/sub1 >& /dev/null || echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT1 $CONTAINER/sub1 >& /dev/null && echo " [FAIL]" && exit -1
echo " [OK]"

echo
echo "*** test sequence passed ***"

date
