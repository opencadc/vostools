#!/bin/tcsh -f

date
echo "###################"
if (! ${?VOSPACE_WEBSERVICE} ) then
	echo "VOSPACE_WEBSERVICE env variable not set, use default WebService URL"
else
	echo "WebService URL (VOSPACE_WEBSERVICE env variable): $VOSPACE_WEBSERVICE"
endif

if (! ${?TMPDIR} ) then
        echo "TMPDIR env variable not set, using /tmp"
        set TMPDIR = "/tmp"
else
        echo "Using ${TMPDIR} for temporary files"
endif

if (! ${?CADC_PYTHON_TEST_TARGETS} ) then
    set CADC_PYTHON_TEST_TARGETS = 'python2.6 python2.7'
endif
echo "Testing for targets $CADC_PYTHON_TEST_TARGETS. Set CADC_PYTHON_TEST_TARGETS to change this."
echo "###################"

foreach pythonVersion ($CADC_PYTHON_TEST_TARGETS)
    echo "*************** test with $pythonVersion ************************"

    set LSCMD = "$pythonVersion $CADC_ROOT/scripts/vls -l"
    set MKDIRCMD = "$pythonVersion $CADC_ROOT/scripts/vmkdir"
    set RMCMD = "$pythonVersion $CADC_ROOT/scripts/vrm"
    set CPCMD = "$pythonVersion $CADC_ROOT/scripts/vcp"
    set RMDIRCMD = "$pythonVersion $CADC_ROOT/scripts/vrmdir"
    set CHMODCMD = "$pythonVersion $CADC_ROOT/scripts/vchmod"

    set CERT = " --cert=$A/test-certificates/x509_CADCRegtest1.pem"

    echo "vls command: " $LSCMD $CERT
    echo

    # using a test dir makes it easier to cleanup a bunch of old/failed tests
    set VOROOT = "vos:"
    set VOHOME = "$VOROOT""CADCRegtest1"
    set BASE = "$VOHOME/atest"

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

    echo -n "view vospace root container "
    $LSCMD $CERT $VOROOT > /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "view non-existent node "
    $LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
    echo " [OK]"
    echo -n "create private container "
    $MKDIRCMD $CERT $CONTAINER > /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "view created container "
    $LSCMD $CERT $CONTAINER > /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "verify public=false after create "
    $LSCMD $CERT $BASE | grep $TIMESTAMP | grep -q 'drw----r--' || echo " [FAIL]" && exit -1
    echo "[OK]"

    echo -n "check set permission properties "
    $CHMODCMD $CERT g+rw $CONTAINER test:g1 test:g2 || echo " [FAIL]" && exit -1
    $LSCMD $CERT $BASE | grep $TIMESTAMP | grep -q 'drw-rw-r--' || echo " [FAIL]" && exit -1
    $LSCMD $CERT $BASE | grep $TIMESTAMP | grep -q 'test:g1' || echo " [FAIL]" && exit -1
    $LSCMD $CERT $BASE | grep $TIMESTAMP | grep -q 'test:g2' || echo " [FAIL]" && exit -1
    echo "[OK]"

    echo -n "check inherit permission properties "
    $MKDIRCMD $CERT $CONTAINER/pub || echo " [FAIL]" && exit -1
    $LSCMD $CERT $CONTAINER | grep pub | grep -q 'drw-rw-r--' || echo " [FAIL]" && exit -1
    $LSCMD $CERT $CONTAINER | grep pub | grep -q 'test:g1' || echo " [FAIL]" && exit -1
    $LSCMD $CERT $CONTAINER | grep pub | grep -q 'test:g2' || echo " [FAIL]" && exit -1
    echo "[OK]"

    echo -n "check inherit + change certain properties "
    $MKDIRCMD $CERT $CONTAINER/priv || echo " [FAIL]" && exit -1
    $CHMODCMD $CERT g+r $CONTAINER/priv test:g3 || echo " [FAIL]" && exit -1
    $LSCMD $CERT $CONTAINER | grep priv | grep -q 'drw-rw-r--' || echo " [FAIL]" && exit -1
    $LSCMD $CERT $CONTAINER | grep priv | grep -q 'test:g3' || echo " [FAIL]" && exit -1
    $LSCMD $CERT $CONTAINER | grep priv | grep -q 'test:g2' || echo " [FAIL]" && exit -1
    echo "[OK]"

    echo -n "check recursive create (non-existant parents) "
    #$MKDIRCMD $CERT $CONTAINER/foo/bar/baz >& /dev/null || echo " [FAIL]" && exit -1
    #$LSCMD $CERT $CONTAINER/foo/bar/baz >& /dev/null # || echo " [FAIL]" TODO add -p option&& exit -1
    echo "[TODO]"

    echo -n "copy file to existing container and non-existent data node "
    $CPCMD $CERT something.png $CONTAINER/something.png || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "view existing data node "
    $LSCMD $CERT $CONTAINER/something.png > /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "copy data node to local filesystem "
    $CPCMD $CERT $CONTAINER/something.png something.png.2 || echo " [FAIL]" && exit -1
    cmp something.png something.png.2 || echo " [FAIL]" && exit -1
    \rm -f something.png.2
    echo " [OK]"

    echo -n "Check quick copy"
    $CPCMD $CERT --quick something.png $CONTAINER/something.png.3|| echo " [FAIL]" && exit -1
    $CPCMD $CERT --quick $CONTAINER/something.png.3 something.png.3 || echo " [FAIL]" && exit -1
    cmp something.png something.png.3 || echo " [FAIL]" && exit -1
    \rm -f something.png.3
    echo " [OK]"

    echo -n "Check pattern matched copy"
    $CPCMD $CERT "$CONTAINER/something*" $TMPDIR || echo " [FAIL]" && exit -1
    $MKDIRCMD $CERT $CONTAINER/pattern_dest
    $CPCMD $CERT something* $CONTAINER/pattern_dest || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "Check pattern with cutout"
    $CPCMD $CERT "$BASE/test*.fits[1:10,1:10]" $TMPDIR || echo " [FAIL]" && exit -1
    echo " [OK]"

    $CPCMD $CERT $CONTAINER/something.png something.png.2 || echo " [FAIL]" && exit -1

    echo -n "copy/overwrite existing data node "
    $CPCMD $CERT something.png $CONTAINER/something.png || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "set content-type during copy "
    #$CMD $CERT --copy --src=something.png --dest=$CONTAINER/something2.png --content-type=image/png || echo " [FAIL]" && exit -1
    #$CMD $CERT --view --target=$CONTAINER/something2.png | grep -q 'type: image/png' || echo " [FAIL]" && exit -1
    echo " [TODO]"

    echo -n "set content-type during copy/overwrite "
    #$CMD $CERT --copy --src=something.png --dest=$CONTAINER/something.png --content-type=JUNK || echo " [FAIL]" && exit -1
    #$CMD $CERT --view --target=$CONTAINER/something.png | grep -q 'type: JUNK' || echo " [FAIL]" && exit -1
    echo " [TODO]"

    echo -n "set content-type of existing data node "
    #$CMD $CERT --set --target=$CONTAINER/something.png --content-type=image/png || echo " [FAIL]" && exit -1
    #$CMD $CERT --view --target=$CONTAINER/something.png | grep -q 'type: image/png' || echo " [FAIL]" && exit -1
    echo " [TODO]"

    echo -n "delete existing data node "
    $RMCMD $CERT $CONTAINER/something.png  >& /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "view deleted/non-existent data node "
    $LSCMD $CERT $CONTAINER/something.png >& /dev/null && echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "delete non-existent data node "
    $RMCMD $CERT $CONTAINER/something.png >& /dev/null && echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "delete non-empty container "
    $RMDIRCMD $CERT $CONTAINER >& /dev/null || echo " [FAIL]" && exit -1
    $LSCMD $CERT $CONTAINER/something2.png >& /dev/null && echo " [FAIL]" && exit -1
    $LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "delete empty container "
    $MKDIRCMD $CERT $CONTAINER >& /dev/null || echo " [FAIL]" && exit -1
    $LSCMD $CERT $CONTAINER > /dev/null || echo " [FAIL]" && exit -1
    $RMDIRCMD $CERT $CONTAINER >& /dev/null || echo " [FAIL]" && exit -1
    $LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
    echo " [OK]"
end

echo
echo "*** test sequence passed ***"

date
