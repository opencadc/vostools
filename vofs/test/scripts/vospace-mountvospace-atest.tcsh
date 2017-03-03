#!/bin/tcsh -f

if ( $0:h == $0 ) then
    set thisDir=$PWD
else
    set thisDir=$0:h
    if ( $thisDir !~ "/*" ) then
	set thisDir = $PWD/$thisDir
    endif
endif

set TAILCMD = 'tail -c 1'

if ( `uname -s` == "Darwin" ) then
     set STATCMD = 'stat -f %z'
     set UMOUNTCMD = 'umount'
     set MD5CMD = 'md5'
else
     set STATCMD = 'stat -c %s'
     set UMOUNTCMD = 'fusermount -u'
     set MD5CMD = 'md5sum'
endif

if ($#argv == 1) then
    setenv CADC_TESTCERT_PATH $1
else
    if ($#argv == 0) then
        echo "Enter path to the CADC test certificates"
        echo -n "Cert path: "
        set certpath = "$<"
        setenv CADC_TESTCERT_PATH ${certpath}
    else
        echo "usage: vospace-all [cadc_test_cert_path]"
        exit -1
    endif
endif

echo "cert files path:  $CADC_TESTCERT_PATH"

date

if (! ${?VOSPACE_WEBSERVICE} ) then
	echo "VOSPACE_WEBSERVICE env variable not set, use default WebService URL"
else
	echo "WebService URL (VOSPACE_WEBSERVICE env variable): $VOSPACE_WEBSERVICE"
endif

set CERTFILE = "$CADC_TESTCERT_PATH/x509_CADCRegtest1.pem"

## define the local cache size, number, and size of files to test exceeding the cache
set VOS_CACHE = "/tmp/vos_cache"
set CACHETEST_LIMIT = 10    # in MB
set CACHETEST_NFILES = 10
set CACHETEST_FSIZE = 3     # CACHETEST_FSIZE * CACHETEST_NFILES should be > CACHETEST_LIMIT

@ CACHETEST_FSIZE_BYTES = $CACHETEST_FSIZE * 1000000
set CACHETEST_FSIZE = ${CACHETEST_FSIZE}MB

## we cannot feasibly test the --xsv option, but it is here to fiddle with in development
set LSCMD = "vls"
set MOUNTCMD = "mountvofs --cache_limit=$CACHETEST_LIMIT --cache_nodes"
set MKDIRCMD = "vmkdir"
set RMCMD = "vrm"
set CPCMD = "vcp"
set RMDIRCMD = "vrmdir"
set CERT = " --certfile=$CERTFILE"
set LOGFILE = "/tmp/mountvofs.log"
set ANONLOGFILE = "/tmp/mountvofs_anon.log"
set BIGSTATICFILE = "transfer/bigfile.bin"

echo "mount command: " $MOUNTCMD
echo "testing version: " `$MOUNTCMD --version`
echo "cache test limit ${CACHETEST_LIMIT}MB, using $CACHETEST_NFILES files of size $CACHETEST_FSIZE"
echo

# using a test dir makes it easier to cleanup a bunch of old/failed tests
set VOROOT = "vos:"
set VOHOME = "$VOROOT""CADCRegtest1"
set BASE = "$VOHOME/atest"
set STATIC = "$VOHOME/vospace-static-test"

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

# Clean up from last time
${UMOUNTCMD}  $MOUNTPOINT >& /dev/null
rmdir $MOUNTPOINT >& /dev/null
rm -fR $VOS_CACHE #clean the cache

# For the anonymous mount test we use a separate temporary log
echo -n "mount vospace anonymously"
rm $ANONLOGFILE >& /dev/null
$MOUNTCMD --certfile=anonymous --mountpoint=$MOUNTPOINT --cache_dir=$VOS_CACHE --log=$ANONLOGFILE -d >& /dev/null || echo " [FAIL]" && exit -1
sleep 3
ls $MOUNTPOINT >& /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "check that https protocol not used for anonymous mount "
set HAVEHTTPS = `grep -v https_test $ANONLOGFILE | grep -c https`
if ( $HAVEHTTPS != 0 ) then
 echo " [FAIL]" 
     echo "HAVEHTTPS: ${?HAVEHTTPS} ${HAVEHTTPS} "
     exit -1
endif
echo " [OK]"

echo -n "unmount anonymous vospace"
${UMOUNTCMD} $MOUNTPOINT >& /dev/null || echo " [FAIL]" && exit-1
rmdir $MOUNTPOINT >& /dev/null
rm -fR $VOS_CACHE #clean the cache
cat $ANONLOGFILE >> $LOGFILE  # since the original gets removed
echo " [OK]"

echo -n "mount vospace using certificate"
$MOUNTCMD $CERT --vospace="$BASE" --mountpoint=$MOUNTPOINT --cache_dir=$VOS_CACHE --log=$LOGFILE -d >& /dev/null || echo " [FAIL]" && exit -1
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
cat /dev/zero | head -c $CACHETEST_FSIZE_BYTES /dev/zero > foo.dat
ls -l foo.dat
foreach i ( `seq $CACHETEST_NFILES` )
    echo -n "."
    echo "$CPCMD $CERT foo.dat $CONTAINER/foo$i.dat "
    $CPCMD $CERT foo.dat $CONTAINER/foo$i.dat >& /dev/null || echo " [FAIL]" && exit -1
end
rm foo.dat >& /dev/null
echo " [OK]"

echo -n "access test data to exceed cache"
foreach i ( `seq $CACHETEST_NFILES` )
    sleep 1
    echo -n "."
    $MD5CMD $MCONTAINER/foo$i.dat || echo " [FAIL]" && exit -1
end

set FS_LAST = `${STATCMD} $VOS_CACHE/data/$TIMESTAMP/foo$i.dat` || echo " [FAIL]" && exit -1
if( $FS_LAST != $CACHETEST_FSIZE_BYTES ) then
    # The last file should now be cached
    echo " last file wrong size [FAIL]" && exit -1
endif

ls -l $VOS_CACHE/data/$TIMESTAMP/
if( -e $VOS_CACHE/data/$TIMESTAMP/foo1.dat ) then
    # The first file should now be evicted from the cache
    echo " first file still exists [FAIL]" && exit -1
endif

rm $MCONTAINER/foo*.dat >& /dev/null || echo " [FAIL]" && exit -1

echo " [OK]"

# --- finish test exceeding the local cache ---

echo -n "delete non-empty container "
ls $MCONTAINER
rm -rf $MCONTAINER >& /dev/null || echo " [FAIL]" && exit -1
ls $MCONTAINER/foo >& /dev/null && echo " [FAIL]" && exit -1
ls $MCONTAINER >& /dev/null && echo " [FAIL]" && exit -1
$LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "delete empty container "
mkdir $MCONTAINER
rmdir $MCONTAINER > /dev/null || echo " [FAIL]" && exit -1
$LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "unmount vospace"
${UMOUNTCMD} $MOUNTPOINT >& /dev/null || echo " [FAIL]" && exit-1
echo " [OK]"

# --- test tail on a really big file ---

echo -n "mount vospace static data location readonly "
$MOUNTCMD $CERT --readonly --vospace="$STATIC" --mountpoint=$MOUNTPOINT --cache_dir=$VOS_CACHE --log=$LOGFILE -d >& /dev/null || echo " [FAIL]" && exit -1
sleep 3
ls $MOUNTPOINT >& /dev/null || echo [FAIL] && exit -1
echo " [OK]"

echo -n "tail request on a large file "
$TAILCMD $MOUNTPOINT/$BIGSTATICFILE >& /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "unmount vospace"
${UMOUNTCMD} $MOUNTPOINT >& /dev/null || echo " [FAIL]" && exit-1
rmdir $MOUNTPOINT >& /dev/null
rm -fR $VOS_CACHE #clean the cache
echo " [OK]"
echo
echo "*** test sequence passed ***"

date
