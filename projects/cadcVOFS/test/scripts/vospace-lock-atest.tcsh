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


echo -n "test setup\n"
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
echo -n "** setting home and base to public, no groups "
$CHMODCMD $CERT o+r $VOHOME || echo " [FAIL]" && exit -1
echo -n " [OK]"
$CHMODCMD $CERT o+r $BASE || echo " [FAIL]" && exit -1
echo " [OK]"
echo "** test container: ${CONTAINER}"
echo
echo "*** starting test sequence ***"
echo

echo -n "test case 1: create container "
$MKDIRCMD $CERT $CONTAINER > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check unlocked container "
$TAGCMD $CERT $CONTAINER $LIST_ARGS | grep -q 'islocked = false' || echo " [FAIL]" && exit -1
echo " [OK]"
echo

echo -n "test case 2: lock container "
$LOCKCMD $CERT $CONTAINER $LOCK_ARGS > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check container is locked "
$TAGCMD $CERT $CONTAINER $LIST_ARGS | grep -q 'islocked = true' || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "test case 3: move locked container "
$MVCMD $CERT $CONTAINER $TEMPCONTAINER > /dev/null || set SUCCESS = "true"
if ( ${SUCCESS} == "true" ) then
    set SUCCESS = "false"
    echo " [OK]"
else
    echo " [FAIL]"
    exit -1
endif
echo

echo -n "test case 4: move to a locked container "
$MVCMD $CERT somedir/ $CONTAINER > /dev/null || set SUCCESS = "true"
if ( ${SUCCESS} == "true" ) then
    set SUCCESS = "false"
    echo " [OK]"
else
    echo " [FAIL]"
    exit -1
endif
echo

echo -n "test case 5: copy to a locked container "
$CPCMD $CERT somedir/ $CONTAINER > /dev/null || set SUCCESS = "true"
if ( ${SUCCESS} == "true" ) then
    set SUCCESS = "false"
    echo " [OK]"
else
    echo " [FAIL]"
    exit -1
endif
echo

echo -n "test case 6: unlock container "
$LOCKCMD $CERT $CONTAINER $UNLOCK_ARGS > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check unlocked container "
$TAGCMD $CERT $CONTAINER $LIST_ARGS | grep -q 'islocked = false' || echo " [FAIL]" && exit -1
echo " [OK]"
echo

echo -n "test case 7: create link "
$CPCMD $CERT something.png $CONTAINER/something.png > /dev/null || echo " [FAIL]" && exit -1
$LNCMD $CERT $CONTAINER/something.png $CONTAINER/target > /dev/null || echo " [FAIL]" && exit -1
$TAGCMD $CERT $CONTAINER/target $LIST_ARGS | grep -q 'islocked = false' || echo " [FAIL]" && exit -1
echo " [OK]"
echo

echo -n "test case 8: lock link "
$LOCKCMD $CERT $CONTAINER/target $LOCK_ARGS > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check locked link "
$TAGCMD $CERT $CONTAINER/target $LIST_ARGS | grep -q 'islocked = true' || echo " [FAIL]" && exit -1
echo " [OK]"
echo

echo -n "test case 9: move locked link "
$MVCMD $CERT $CONTAINER/target $CONTAINER/target1 > /dev/null || set SUCCESS = "true"
if ( ${SUCCESS} == "true" ) then
    set SUCCESS = "false"
    echo " [OK]"
else
    echo " [FAIL]"
    exit -1
endif
echo 

echo -n "test case 10: unlock link "
$LOCKCMD $CERT $CONTAINER/target $UNLOCK_ARGS> /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check unlocked link "
$TAGCMD $CERT $CONTAINER/target $LIST_ARGS | grep -q 'islocked = false' || echo " [FAIL]" && exit -1
echo " [OK]"
echo

echo -n "test case 11: check unlocked node "
$TAGCMD $CERT $CONTAINER/something.png $LIST_ARGS | grep -q 'islocked = false' || echo " [FAIL]" && exit -1
echo " [OK]"
echo

echo -n "test case 12: lock node "
$LOCKCMD $CERT $CONTAINER/something.png $LOCK_ARGS > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check locked node "
$TAGCMD $CERT $CONTAINER/something.png $LIST_ARGS | grep -q 'islocked = true' || echo " [FAIL]" && exit -1
echo " [OK]"
echo

echo -n "test case 13: copy to a locked node "
$CPCMD $CERT something.png $CONTAINER/something.png > /dev/null || set SUCCESS = "true"
if ( ${SUCCESS} == "true" ) then
    set SUCCESS = "false"
    echo " [OK]"
else
    echo " [FAIL]"
    exit -1
endif
echo 

echo -n "test case 14: move to a locked node "
$MVCMD $CERT something.png $CONTAINER/something.png > /dev/null || set SUCCESS = "true"
if ( ${SUCCESS} == "true" ) then
    set SUCCESS = "false"
    echo " [OK]"
else
    echo " [FAIL]"
    exit -1
endif
echo 

echo -n "test case 15: move a locked node "
$MVCMD $CERT $CONTAINER/something.png $CONTAINER/something.png1 > /dev/null || set SUCCESS = "true"
if ( ${SUCCESS} == "true" ) then
    set SUCCESS = "false"
    echo " [OK]"
else
    echo " [FAIL]"
    exit -1
endif
echo 

echo -n "test case 16: unlock node "
$LOCKCMD $CERT $CONTAINER/something.png $UNLOCK_ARGS> /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check unlocked node "
$TAGCMD $CERT $CONTAINER/something.png $LIST_ARGS | grep -q 'islocked = false' || echo " [FAIL]" && exit -1
echo " [OK]"
echo

# clean up
echo -n "test clean up: delete container "
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
