#!/bin/tcsh -f

if (! ${?VOSPACE_WEBSERVICE} ) then
        setenv VOSPACE_WEBSERVICE 'www.cadc-ccda.hia-iha.nrc-cnrc.gc.ca'
	echo "VOSPACE_WEBSERVICE env variable not #set, use default WebService URL $VOSPACE_WEBSERVICE"
else
	echo "WebService URL (VOSPACE_WEBSERVICE env variable): $VOSPACE_WEBSERVICE"
endif

set ACCESS_PAGE=https://ws-cadc.canfar.net/ac/login
echo "Using access page: $ACCESS_PAGE"

set THIS_DIR = `dirname $0`
set THIS_DIR = `cd $THIS_DIR && pwd`

# Username / password for getting tokens
echo "Enter credentials for a VOSpace account in which we will perform tests."
echo -n "CADC Username: "
set username = $<
echo -n "Password: "
stty -echo
set password = $<
echo
stty echo


set LSCMD = "vls -l"
set MKDIRCMD = "vmkdir"
set RMCMD = "vrm"
set CPCMD = "vcp"
set RMDIRCMD = "vrmdir"
set CHMODCMD = "vchmod"
echo

# using a test dir makes it easier to cleanup a bunch of old/failed tests
set VOROOT = "vos://"
# use resourceID in vos-config to determine the base URI
# vault uses CADCRegtest1, cavern uses home/cadcregtest1
set RESOURCE_ID = `grep "^resourceID" "$HOME/.config/vos/vos-config" | awk '{print $3}'`
set HOST = `echo $RESOURCE_ID | cut -d"/" -f3`
echo $RESOURCE_ID | grep "cavern" >& /dev/null
if ( $status == 0) then
    set VOS_BASE = "~cavern/home/""$username"
    set TESTING_CAVERN = "true"
    echo "** using cavern"
else
    set VOS_BASE = "~vault/""$username"
    echo "** using vault"
endif
set VOHOME = "$VOROOT""$HOST""$VOS_BASE"
set BASE = "$VOHOME/atest"

set TIMESTAMP=`date +%Y-%m-%dT%H-%M-%S`
set CONTAINER = $BASE/$TIMESTAMP

echo

echo "Test CONTAINER: $CONTAINER"

# Start with a token scoped to user's entire tree
set TOKEN = "`curl -s -d username=$username -d password=$password ${ACCESS_PAGE}'?'scope=${VOHOME}`"

echo -n "create containers"
if ( ${?TESTING_CAVERN} ) then
    echo " [SKIPPED, scoped tokens not supported]"
else
    $MKDIRCMD --token "$TOKEN" -p $CONTAINER/A > /dev/null || echo " [FAIL]" && exit -1
    $MKDIRCMD --token "$TOKEN" $CONTAINER/B > /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"
endif

echo -n "set permissions"
if ( ${?TESTING_CAVERN} ) then
    echo " [SKIPPED, scoped tokens not supported]"
else
    $CHMODCMD --token="$TOKEN" o+r $CONTAINER || echo " [FAIL]" && exit -1
    $CHMODCMD --token="$TOKEN" o+r $CONTAINER/A || echo " [FAIL]" && exit -1
    $CHMODCMD --token="$TOKEN" o+r $CONTAINER/B || echo " [FAIL]" && exit -1
    echo " [OK]"
endif

# Get a new token scoped only to the /B subdir
set TOKEN = "`curl -s -d username=$username -d password=$password ${ACCESS_PAGE}'?'scope=${VOHOME}/atest/$TIMESTAMP/B`"

echo -n "copy file to unscoped tree fails"
$CPCMD --token="$TOKEN" $THIS_DIR/something.png $CONTAINER/A/ >& /dev/null
if ( $status == 0 ) then
    echo " [FAIL]" && exit -1
endif
echo " [OK]"

echo -n "copy a file to scoped tree"
if ( ${?TESTING_CAVERN} ) then
    echo " [SKIPPED, scoped tokens not supported]"
else
    $CPCMD --token="$TOKEN" $THIS_DIR/something.png $CONTAINER/B/ || echo " [FAIL]" && exit -1
    echo " [OK]"
endif

echo -n "check that the file got there"
if ( ${?TESTING_CAVERN} ) then
    echo " [SKIPPED, scoped tokens not supported]"
else
    $LSCMD --token="$TOKEN" $CONTAINER/B | grep -q 'something.png' || echo " [FAIL]" && exit -1
    echo " [OK]"
endif

echo -n "create sub container with file in it"
if ( ${?TESTING_CAVERN} ) then
    echo " [SKIPPED, scoped tokens not supported]"
else
    $MKDIRCMD --token="$TOKEN" $CONTAINER/B/test > /dev/null || echo " [FAIL]" && exit -1
    $CPCMD --token="$TOKEN" $THIS_DIR/something.png $CONTAINER/B/test/ || echo " [FAIL]" && exit -1
    $LSCMD --token="$TOKEN" $CONTAINER/B/test | grep -q 'something.png' || echo " [FAIL]" && exit -1
    echo " [OK]"
endif

echo -n "remove the file in the sub container"
if ( ${?TESTING_CAVERN} ) then
    echo " [SKIPPED, scoped tokens not supported]"
else
    $RMCMD --token="$TOKEN" $CONTAINER/B/test/something.png || echo " [FAIL]" && exit -1
    echo " [OK]"
endif

echo -n "remove the sub container"
if ( ${?TESTING_CAVERN} ) then
    echo " [SKIPPED, scoped tokens not supported]"
else
    $RMDIRCMD --token="$TOKEN" $CONTAINER/B/test || echo " [FAIL]" && exit -1
    $LSCMD --token="$TOKEN" $CONTAINER/B | grep -q test
    if ( $status == 0 ) then
        echo " [FAIL]" && exit -1
    endif
    echo " [OK]"
endif

echo
echo "*** test sequence passed ***"

date
