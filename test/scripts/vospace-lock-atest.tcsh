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
set MVCMD = "python $CADC_ROOT/scripts/vmv"
set RMDIRCMD = "python $CADC_ROOT/scripts/vrmdir"
set CHMODCMD = "python $CADC_ROOT/scripts/vchmod"
set TAGCMD = "python $CADC_ROOT/scripts/vtag"
set LNCMD = "python $CADC_ROOT/scripts/vln"
set LOCKCMD = "python $CADC_ROOT/scripts/vlock"

set CERT = " --cert=$A/test-certificates/x509_CADCRegtest1.pem"

set SUCCESS = "false"
set LIST_ARGS = "--list"
set LOCK_ARGS = "islocked true"
set UNLOCK_ARGS = "islocked false"

echo "vls command: " $LSCMD $CERT
echo

# using a test dir makes it easier to cleanup a bunch of old/failed tests
set VOROOT = "vos:"
set VOHOME = "$VOROOT""CADCRegtest1"
set BASE = "$VOHOME/atest/locktest"

set TIMESTAMP=`date +%Y-%m-%dT%H-%M-%S`
set CONTAINER = $BASE/$TIMESTAMP
set TEMPCONTAINER = $BASE/$TIMESTAMP"-temp"


echo "test setup"
echo -n "** checking base URI "
$LSCMD $CERT $BASE > /dev/null
if ( $status == 0) then
	echo " [OK]"
else
	echo -n ", creating base URI"
	exit
        $MKDIRCMD $CERT $BASE || echo " [FAIL]" && exit -1
	echo " [OK]"
endif
echo -n "** setting home to public, no groups "
$CHMODCMD $CERT o+r $VOHOME || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "** setting base to public, no groups "
$CHMODCMD $CERT o+r $BASE || echo " [FAIL]" && exit -1
echo " [OK]"
echo "** test container: ${CONTAINER}"
echo
echo "*** starting test sequence ***"
echo

echo "test case 1: "
echo -n "create container "
$MKDIRCMD $CERT $CONTAINER > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check unlocked container "
$TAGCMD $CERT $CONTAINER $LIST_ARGS | grep -q 'islocked' || set SUCCESS = "true"
if ( ${SUCCESS} == "true" ) then
    set SUCCESS = "false"
    echo " [OK]"
else
    echo " [FAIL]"
    exit -1
endif
echo

echo "test case 2: "
echo -n "lock container "
$LOCKCMD $CERT $CONTAINER $LOCK_ARGS > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check container is locked "
$TAGCMD $CERT $CONTAINER $LIST_ARGS | grep -q 'islocked = true' || echo " [FAIL]" && exit -1
echo " [OK]"

echo "test case 3: "
echo -n "unlock container "
$LOCKCMD $CERT $CONTAINER $UNLOCK_ARGS > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check unlocked container "
$TAGCMD $CERT $CONTAINER $LIST_ARGS | grep -q 'islocked' || set SUCCESS = "true"
if ( ${SUCCESS} == "true" ) then
    set SUCCESS = "false"
    echo " [OK]"
else
    echo " [FAIL]"
    exit -1
endif
echo

echo "test case 4: "
echo -n "create link "
$CPCMD $CERT something.png $CONTAINER/something.png > /dev/null || echo " [FAIL]" && exit -1
$LNCMD $CERT $CONTAINER/something.png $CONTAINER/target > /dev/null || echo " [FAIL]" && exit -1
$TAGCMD $CERT $CONTAINER/target $LIST_ARGS | grep -q 'islocked' || set SUCCESS = "true"
if ( ${SUCCESS} == "true" ) then
    set SUCCESS = "false"
    echo " [OK]"
else
    echo " [FAIL]"
    exit -1
endif
echo

echo "test case 5: "
echo -n "lock link "
$LOCKCMD $CERT $CONTAINER/target $LOCK_ARGS > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check locked link "
$TAGCMD $CERT $CONTAINER/target $LIST_ARGS | grep -q 'islocked = true' || echo " [FAIL]" && exit -1
echo " [OK]"
echo

echo "test case 6: "
echo -n "unlock link "
$LOCKCMD $CERT $CONTAINER/target $UNLOCK_ARGS> /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check unlocked link "
$TAGCMD $CERT $CONTAINER/target $LIST_ARGS | grep -q 'islocked' || set SUCCESS = "true"
if ( ${SUCCESS} == "true" ) then
    set SUCCESS = "false"
    echo " [OK]"
else
    echo " [FAIL]"
    exit -1
endif
echo

echo "test case 7: "
echo -n "check unlocked node "
$TAGCMD $CERT $CONTAINER/something.png $LIST_ARGS | grep -q 'islocked' || set SUCCESS = "true"
if ( ${SUCCESS} == "true" ) then
    set SUCCESS = "false"
    echo " [OK]"
else
    echo " [FAIL]"
    exit -1
endif
echo

echo "test case 8: "
echo -n "lock node "
$LOCKCMD $CERT $CONTAINER/something.png $LOCK_ARGS > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check locked node "
$TAGCMD $CERT $CONTAINER/something.png $LIST_ARGS | grep -q 'islocked = true' || echo " [FAIL]" && exit -1
echo " [OK]"
echo

echo "test case 9: "
echo -n "unlock node "
$LOCKCMD $CERT $CONTAINER/something.png $UNLOCK_ARGS> /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check unlocked node "
$TAGCMD $CERT $CONTAINER/something.png $LIST_ARGS | grep -q 'islocked' || set SUCCESS = "true"
if ( ${SUCCESS} == "true" ) then
    set SUCCESS = "false"
    echo " [OK]"
else
    echo " [FAIL]"
    exit -1
endif
echo

echo "test case 8: "
echo -n "lock node "
$LOCKCMD $CERT $CONTAINER/something.png $LOCK_ARGS > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check locked node "
$TAGCMD $CERT $CONTAINER/something.png $LIST_ARGS | grep -q 'islocked = true' || echo " [FAIL]" && exit -1
echo " [OK]"
echo

echo "test case 9: "
echo -n "unlock node "
$LOCKCMD $CERT $CONTAINER/something.png $UNLOCK_ARGS> /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check unlocked node "
$TAGCMD $CERT $CONTAINER/something.png $LIST_ARGS | grep -q 'islocked' || set SUCCESS = "true"
if ( ${SUCCESS} == "true" ) then
    set SUCCESS = "false"
    echo " [OK]"
else
    echo " [FAIL]"
    exit -1
endif
echo

# clean up
echo "test clean up "
echo -n "delete local file "
rm -f something1.png || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "delete non-empty container "
$RMDIRCMD $CERT $CONTAINER >& /dev/null || echo " [FAIL]" && exit -1
$TAGCMD $CERT $CONTAINER $LIST_ARGS >& /dev/null || set SUCCESS = "true"
if ( ${SUCCESS} == "true" ) then
    set SUCCESS = "false"
    echo " [OK]"
else
    echo " [FAIL]"
    exit -1
endif

echo
echo "*** test sequence passed ***"

date
