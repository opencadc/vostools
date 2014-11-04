#!/bin/tcsh -f

echo "###################"
if (! ${?CADC_ROOT} ) then
	set CADC_ROOT = "/usr/cadc/local"
endif
echo "using CADC_ROOT = $CADC_ROOT"

if (! ${?VOSPACE_WEBSERVICE} ) then
        setenv VOSPACE_WEBSERVICE 'www.canfar.phys.uvic.ca'
	echo "VOSPACE_WEBSERVICE env variable not set, use default WebService URL $VOSPACE_WEBSERVICE"
else
	echo "WebService URL (VOSPACE_WEBSERVICE env variable): $VOSPACE_WEBSERVICE"
endif

if (! ${?CADC_PYTHON_TEST_TARGETS} ) then
    set CADC_PYTHON_TEST_TARGETS = 'python2.6 python2.7'
endif
echo "Testing for targets $CADC_PYTHON_TEST_TARGETS. Set CADC_PYTHON_TEST_TARGETS to change this."
echo "###################"

# OS-specific
if ( `uname -s` == "Darwin" ) then
     set UMOUNTCMD = 'umount'
else
     set UMOUNTCMD = 'fusermount -u'
endif

set ACCESS_PAGE=${VOSPACE_WEBSERVICE}/access/login
echo "Using access page: $ACCESS_PAGE"
set VOS_BASE = "vos://cadc.nrc.ca~vospace"

# Username / password for getting cookies
echo "Enter credentials for a VOSpace account in which we will perform tests."
echo -n "CADC Username: "
set username = $<
echo -n "Password: "
stty -echo
set password = $<
echo
stty echo


foreach pythonVersion ($CADC_PYTHON_TEST_TARGETS)
    echo "*************** test with $pythonVersion ************************"

    # for mountvofs
    set MOUNTPOINT = /tmp/vospace
    set VOS_CACHE = "/tmp/vos_cache"

    set LSCMD = "$pythonVersion $CADC_ROOT/scripts/vls -l"
    set MKDIRCMD = "$pythonVersion $CADC_ROOT/scripts/vmkdir"
    set RMCMD = "$pythonVersion $CADC_ROOT/scripts/vrm"
    set CPCMD = "$pythonVersion $CADC_ROOT/scripts/vcp"
    set RMDIRCMD = "$pythonVersion $CADC_ROOT/scripts/vrmdir"
    set CHMODCMD = "$pythonVersion $CADC_ROOT/scripts/vchmod"
    set MOUNTCMD = "$pythonVersion $CADC_ROOT/scripts/mountvofs"
    echo

    # using a test dir makes it easier to cleanup a bunch of old/failed tests
    set VOROOT = "vos:"
    set VOHOME = "$VOROOT""$username"
    set BASE = "$VOHOME/atest"

    set TIMESTAMP=`date +%Y-%m-%dT%H-%M-%S`
    set CONTAINER = $BASE/$TIMESTAMP

    echo "Test CONTAINER: $CONTAINER"

    # Start with a cookie scoped to user's entire tree
    set COOKIE_RAW = `curl -s -d username=$username -d password=$password "${ACCESS_PAGE}?scope=${VOS_BASE}/${username}"`
    set COOKIE = `python -c "import urllib; print urllib.quote_plus('''$COOKIE_RAW''')"`

    echo -n "create containers"

    $MKDIRCMD --cookie="$COOKIE" -p $CONTAINER/A > /dev/null || echo " [FAIL]" && exit -1
    $MKDIRCMD --cookie="$COOKIE" $CONTAINER/B > /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "set permissions"
    $CHMODCMD --cookie="$COOKIE" o+r $CONTAINER || echo " [FAIL]" && exit -1
    $CHMODCMD --cookie="$COOKIE" o+r $CONTAINER/A || echo " [FAIL]" && exit -1
    $CHMODCMD --cookie="$COOKIE" o+r $CONTAINER/B || echo " [FAIL]" && exit -1
    echo " [OK]"

    # Get a new token scoped only to the /B subdir
    set COOKIE_RAW = `curl -s -d username=$username -d password=$password "${ACCESS_PAGE}?scope=${VOS_BASE}/${username}/atest/$TIMESTAMP/B"`
    set COOKIE = `python -c "import urllib; print urllib.quote_plus('''$COOKIE_RAW''')"`

    echo -n "copy file to unscoped tree fails"
    $CPCMD --cookie="$COOKIE" something.png $CONTAINER/A/ >& /dev/null
    if ( $status == 0 ) then
        echo " [FAIL]" && exit -1
    endif
    echo " [OK]"

    echo -n "copy a file to scoped tree"
    $CPCMD --cookie="$COOKIE" something.png $CONTAINER/B/ || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "check that the file got there"
    $LSCMD --cookie="$COOKIE" $CONTAINER/B | grep -q 'something.png' || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "create sub container with file in it"
    $MKDIRCMD --cookie="$COOKIE" $CONTAINER/B/test > /dev/null || echo " [FAIL]" && exit -1
    $CPCMD --cookie="$COOKIE" something.png $CONTAINER/B/test/ || echo " [FAIL]" && exit -1
    $LSCMD --cookie="$COOKIE" $CONTAINER/B/test | grep -q 'something.png' || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "remove the file in the sub container"
    $RMCMD --cookie="$COOKIE" $CONTAINER/B/test/something.png || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "remove the sub container"
    $RMDIRCMD --cookie="$COOKIE" $CONTAINER/B/test || echo " [FAIL]" && exit -1
    $LSCMD --cookie="$COOKIE" $CONTAINER/B | grep -q test
    if ( $status == 0 ) then
        echo " [FAIL]" && exit -1
    endif
    echo " [OK]"

    echo -n "mount vospace"
    $UMOUNTCMD $MOUNTPOINT >& /dev/null
    rmdir $MOUNTPOINT >& /dev/null
    rm -fR $VOS_CACHE #clean the cache
    $MOUNTCMD --cookie="$COOKIE" --cache_dir=$VOS_CACHE --mountpoint=$MOUNTPOINT --vospace=${VOS_BASE}/${username}/atest/$TIMESTAMP/B || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "copy file in to mounted filesystem"
    cp something.png $MOUNTPOINT/something2.png || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "unmount vospace"
    sleep 1
    $UMOUNTCMD $MOUNTPOINT || echo " [FAIL]" && exit -1
    echo " [OK]"

end

echo
echo "*** test sequence passed ***"

date
