#!/bin/tcsh -f

date
if (! ${?VOSPACE_WEBSERVICE} ) then
	echo "VOSPACE_WEBSERVICE env variable not set, use default WebService URL"
else
	echo "WebService URL (VOSPACE_WEBSERVICE env variable): $VOSPACE_WEBSERVICE"
endif
if (! ${?CADC_TESTCERT_PATH} ) then
	echo "CADC_TESTCERT_PATH env variable not set. Must point to the location of x509_CADCRegtest1.pem cert file"
    exit -1
else
    set CERTFILE = "$CADC_TESTCERT_PATH/x509_CADCRegtest1.pem"
	echo "cert file:  ($CADC_TESTCERT_PATH env variable): $CERTFILE"
endif

set THIS_DIR = `dirname $0`
set THIS_DIR = `cd $THIS_DIR && pwd`

set LSCMD = "vls -l"
set MKDIRCMD = "vmkdir"
set RMCMD = "vrm"
set CPCMD = "vcp"

set MVCMD = "vmv"
set RMDIRCMD = "vrmdir"
set CHMODCMD = "vchmod"
set TAGCMD = "vtag"
set LNCMD = "vln"
set LOCKCMD = "vlock"

set CERT = " --cert=$CERTFILE"

set SUCCESS = "false"
set LIST_ARGS = "ivo://cadc.nrc.ca/vospace/core#islocked"
set VLOCK_ARGS = "--lock"
set VUNLOCK_ARGS = "--unlock"

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
$TAGCMD $CERT $CONTAINER $LIST_ARGS | grep -q true || set SUCCESS = "true"

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
$LOCKCMD $CERT $CONTAINER $VLOCK_ARGS > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check container is locked $CONTAINER"
$TAGCMD $CERT $CONTAINER $LIST_ARGS 
echo "$TAGCMD $CERT $CONTAINER $LIST_ARGS "
$TAGCMD $CERT $CONTAINER $LIST_ARGS | grep -q true || echo " [FAIL]" && exit -1
echo " [OK]"

echo "test case 3: "
echo -n "unlock container "
$LOCKCMD $CERT $CONTAINER $VUNLOCK_ARGS > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check unlocked container "
$TAGCMD $CERT $CONTAINER $LIST_ARGS | grep -q None && set SUCCESS = "true"

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
$CPCMD $CERT $THIS_DIR/something.png $CONTAINER/something.png > /dev/null || echo " [FAIL]" && exit -1
$LNCMD $CERT $CONTAINER/something.png $CONTAINER/target > /dev/null || echo " [FAIL]" && exit -1
$TAGCMD $CERT $CONTAINER/target $LIST_ARGS | grep -q true || set SUCCESS = "true"

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
$LOCKCMD $CERT $CONTAINER/target $VLOCK_ARGS > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check locked link "
$TAGCMD $CERT $CONTAINER/target $LIST_ARGS | grep -q true || echo " [FAIL]" && exit -1
echo " [OK]"
echo

echo "test case 6: "
echo -n "unlock link "
$LOCKCMD $CERT $CONTAINER/target $VUNLOCK_ARGS> /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check unlocked link "
$TAGCMD $CERT $CONTAINER/target $LIST_ARGS | grep -q None && set SUCCESS = "true"

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
$TAGCMD $CERT $CONTAINER/something.png $LIST_ARGS
$TAGCMD $CERT $CONTAINER/something.png $LIST_ARGS | grep -q None && set SUCCESS = "true"
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
$LOCKCMD $CERT $CONTAINER/something.png $VLOCK_ARGS > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check locked node "
$TAGCMD $CERT $CONTAINER/something.png $LIST_ARGS | grep -q true || echo " [FAIL]" && exit -1
echo " [OK]"
echo

echo "test case 9: "
echo -n "unlock node "
$LOCKCMD $CERT $CONTAINER/something.png $VUNLOCK_ARGS> /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check unlocked node "
$TAGCMD $CERT $CONTAINER/something.png $LIST_ARGS | grep -q None && set SUCCESS = "true"
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
$LOCKCMD $CERT $CONTAINER/something.png $VLOCK_ARGS > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check locked node "
$TAGCMD $CERT $CONTAINER/something.png $LIST_ARGS | grep -q true || echo " [FAIL]" && exit -1
echo " [OK]"
echo

echo "test case 9: "
echo -n "unlock node "
$LOCKCMD $CERT $CONTAINER/something.png $VUNLOCK_ARGS> /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo -n "check unlocked node "
$TAGCMD $CERT $CONTAINER/something.png $LIST_ARGS | grep -q None && set SUCCESS = "true"

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
rm -f $THIS_DIR/something1.png || echo " [FAIL]" && exit -1
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
