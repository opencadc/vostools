#!/bin/tcsh -f

date
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
set LSCMD = "python $CADC_ROOT/scripts/vls -l"
set MKDIRCMD = "python $CADC_ROOT/scripts/vmkdir"
set RMCMD = "python $CADC_ROOT/scripts/vrm"
set CPCMD = "python $CADC_ROOT/scripts/vcp"
set RMDIRCMD = "python $CADC_ROOT/scripts/vrmdir"
set CHMODCMD = "python $CADC_ROOT/scripts/vchmod"
set TAGCMD = "python $CADC_ROOT/scripts/vtag"
set LNCMD = "python $CADC_ROOT/scripts/vln"

set CERT = " --cert=$A/test-certificates/x509_CADCRegtest1.pem"

set LOCK_ARGS = "islocked true"

echo "vls command: " $LSCMD $CERT
echo

# using a test dir makes it easier to cleanup a bunch of old/failed tests
set VOROOT = "vos:"
set VOHOME = "$VOROOT""CADCRegtest1"
set BASE = "$VOHOME/atest/locktest"

set TIMESTAMP=`date +%Y-%m-%dT%H-%M-%S`
set CONTAINER = $BASE/$TIMESTAMP


echo -n "** checking base URI"
$LSCMD $CERT $BASE > /dev/null
if ( $status == 0) then
	echo " [OK]"
else
	echo -n ", creating base URI"
	exit
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

echo -n "lock vospace container "
$TAGCMD $CERT $CONTAINER $LOCK_ARGS > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "view locked vospace container "
$LSCMD $CERT $CONTAINER | grep LOCKED || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "lock link "
$CPCMD $CERT $CONTAINER/something.png > /dev/null || echo " [FAIL]" && exit -1
$LNCMD $CERT $CONTAINER/target $CONTAINER/something.png > /dev/null || echo " [FAIL]" && exit -1
$TAGCMD $CERT $CONTAINER/target $LOCK_ARGS > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "view locked link "
$LSCMD $CERT $CONTAINER/target | grep LOCKED || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "lock node "
$TAGCMD $CERT $CONTAINER/something.png $LOCK_ARGS > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "view locked node "
$LSCMD $CERT $CONTAINER/something.png | grep LOCKED || echo " [FAIL]" && exit -1
echo " [OK]"

echo
echo "*** test sequence passed ***"

date
