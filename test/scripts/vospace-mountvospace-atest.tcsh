#!/bin/tcsh -f

if ( $0:h == $0 ) then
    set thisDir=$PWD
else
    set thisDir=$0:h
    if ( $thisDir !~ "/*" ) then
	set thisDir = $PWD/$thisDir
    endif
endif


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

if (! ${?CADC_PYTHON_TEST_TARGETS} ) then
    set CADC_PYTHON_TEST_TARGETS = 'python2.7 python2.6'
endif
echo "Testing for targets $CADC_PYTHON_TEST_TARGETS. Set CADC_PYTHON_TEST_TARGETS to change this."
echo "###################"

foreach pythonVersion ($CADC_PYTHON_TEST_TARGETS)
    echo "*************** test with $pythonVersion ************************"

    ## define the local cache size, number, and size of files to test exceeding the cache
    set VOS_CACHE = "/tmp/vos_cache"
    set CACHETEST_LIMIT = 10    # in MB
    set CACHETEST_NFILES = 5
    set CACHETEST_FSIZE = 3     # CACHETEST_FSIZE * CACHETEST_NFILES should be > CACHETEST_LIMIT

    @ CACHETEST_FSIZE_BYTES = $CACHETEST_FSIZE * 1000000
    set CACHETEST_FSIZE = ${CACHETEST_FSIZE}MB

    ## we cannot feasibly test the --xsv option, but it is here to fiddle with in development
    set LSCMD = "$pythonVersion $CADC_ROOT/scripts/vls"
    set MOUNTCMD = "$pythonVersion $CADC_ROOT/scripts/mountvofs --cache_limit=$CACHETEST_LIMIT"
    set MKDIRCMD = "$pythonVersion $CADC_ROOT/scripts/vmkdir"
    set RMCMD = "$pythonVersion $CADC_ROOT/scripts/vrm"
    set CPCMD = "$pythonVersion $CADC_ROOT/scripts/vcp"
    set RMDIRCMD = "$pythonVersion $CADC_ROOT/scripts/vrmdir"

    set CERTPATH = "$A/test-certificates/x509_CADCRegtest1.pem"
    set CERT = " --cert=$CERTPATH"
    set CERTFILE = " --certfile=$CERTPATH"

    echo "mount command: " $MOUNTCMD
    echo "cache test limit ${CACHETEST_LIMIT}MB, using $CACHETEST_NFILES files of size $CACHETEST_FSIZE"
    echo

    # using a test dir makes it easier to cleanup a bunch of old/failed tests
    set VOROOT = "vos:"
    set VOHOME = "$VOROOT""CADCRegtest1"
    set BASE = "$VOHOME/atest"

    set TIMESTAMP=`date +%Y-%m-%dT%H-%M-%S`
    set CONTAINER = $BASE/$TIMESTAMP
    set MOUNTPOINT=/tmp/vospace
    set MCONTAINER = "$MOUNTPOINT/$TIMESTAMP"

    echo "TIMESTAMP: $TIMESTAMP"

    echo -n "** checking base URI"
    $LSCMD $CERT $BASE > /dev/null
    if ( $status == 0) then
        echo " [OK]"
    else
        echo -n ", creating base URI"
        exit
            $CMD $CERT --create --target=$BASE || echo " [FAIL]" && exit -1
        echo " [OK]"
    endif
    echo "*** starting test sequence ***"
    echo

    echo -n "mount vospace"
    fusermount -u $MOUNTPOINT >& /dev/null
    rmdir $MOUNTPOINT >& /dev/null
    rm -fR $VOS_CACHE #clean the cache
    $MOUNTCMD $CERT --vospace="$BASE" --mountpoint=$MOUNTPOINT --cache_dir=$VOS_CACHE --log=/tmp/mountvofs.log -d || echo " [FAIL]" && exit -1
    sleep 3
    ls $MOUNTPOINT >& /dev/null || echo [FAIL] && exit -1
    echo " [OK]"

    echo -n "make new directory"
    mkdir $MCONTAINER
    ls $MCONTAINER || echo " [FAIL]" && exit -1
    $LSCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "view non-existent node "
    ls $MCONTAINER >& /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"


    echo -n "check recursive create (non-existant parents) "
    mkdir -p $MCONTAINER/foo/bar/baz >& /dev/null || echo " [FAIL]" && exit -1
    $LSCMD $CERT $CONTAINER/foo/bar/baz >& /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "copy file to existing container and non-existent data node "
    cp $thisDir/something.png $MCONTAINER || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "view existing data node "
    ls $MCONTAINER/something.png >& /dev/null || echo [FAIL] && exit -1
    $LSCMD $CERT $CONTAINER/something.png > /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "copy data node to local filesystem "
    cp $MCONTAINER/something.png something.png.2 || echo " [FAIL]" && exit -1
    cmp $thisDir/something.png something.png.2 || echo " [FAIL]" && exit -1
    rm -f something.png.2
    echo " [OK]"

    echo -n "copy/overwrite existing data node "
    cp $thisDir/something.png $MCONTAINER/something.png || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "delete existing data node "
    rm $MCONTAINER/something.png  >& /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"
    echo -n "view deleted/non-existent data node "
    ls $MCONTAINER/something.png >& /dev/null && echo " [FAIL]" && exit -1
    $LSCMD $CERT $CONTAINER/something.png >& /dev/null && echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "delete non-existent data node "
    rm $MCONTAINER/something.png >& /dev/null && echo " [FAIL]" && exit -1
    echo " [OK]"


    # --- test exceeding the local cache ---
    echo -n "copy cache test data to container"
    rm foo.dat >& /dev/null
    truncate -s $CACHETEST_FSIZE foo.dat >& /dev/null
    foreach i ( `seq $CACHETEST_NFILES` )
        echo -n "."
        $CPCMD $CERTFILE foo.dat $CONTAINER/foo$i.dat >& /dev/null || echo " [FAIL]" && exit -1
    end
    rm foo.dat >& /dev/null
    echo " [OK]"

    echo -n "access test data to exceed cache"
    foreach i ( `seq $CACHETEST_NFILES` )
        echo -n "."
        md5sum $MCONTAINER/foo$i.dat >& /dev/null || echo " [FAIL]" && exit -1
    end

    set FS_LAST = `stat -c %s $VOS_CACHE/data/$TIMESTAMP/foo$i.dat` || echo " [FAIL]" && exit -1
    if( $FS_LAST != $CACHETEST_FSIZE_BYTES ) then
        # The last file should now be cached
        echo " [FAIL]" && exit -1
    endif

    if( -e $VOS_CACHE/data/$TIMESTAMP/foo1.dat ) then
        # The first file should now be evicted from the cache
        echo " [FAIL]" && exit -1
    endif

    rm $MCONTAINER/foo*.dat >& /dev/null || echo " [FAIL]" && exit -1

    echo " [OK]"

    # --- finish test exceeding the local cache ---


    echo -n "delete non-empty container "
    rm $MCONTAINER >& /dev/null && echo " [FAIL]" && exit -1
    ls $MCONTAINER/foo >& /dev/null || echo " [FAIL]" && exit -1
    ls $MCONTAINER >& /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "delete empty container "
    rmdir $MCONTAINER/* >& /dev/null || echo " [FAIL]" && exit -1
    #rm $MCONTAINER/* >& /dev/null || echo " [FAIL]" && exit -1 #TODO activate when cp succeeds
    rmdir $MCONTAINER > /dev/null || echo " [FAIL]" && exit -1
    $LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
    echo " [OK]"
end

echo
echo "*** test sequence passed ***"

date
