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
## we cannot feasibly test the --xsv option, but it is here to fiddle with in development
set LSCMD = "python $CADC_ROOT/vls"
set MOUNTCMD = "python $CADC_ROOT/mountvofs"
set MKDIRCMD = "python $CADC_ROOT/vmkdir"
set RMCMD = "python $CADC_ROOT/vrm"
set CPCMD = "python $CADC_ROOT/vcp"
set RMDIRCMD = "python $CADC_ROOT/vrmdir"

set CERT = " --cert=$A/test-certificates/x509_CADCRegtest1.pem"

echo "mount command: " $MOUNTCMD
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
rm -fR /tmp/vos_cache #clean the cache
$MOUNTCMD $CERT --vospace="vos:CADCRegtest1/atest" --mountpoint=$MOUNTPOINT --cache_dir=/tmp/vos_cache --log=/tmp/mountvofs.log -d || echo " [FAIL]" && exit -1
ls $MOUNTPOINT >& /dev/null || echo [FAIL] && exit -1
echo " [OK]"

echo -n "make new directory"
mkdir $MOUNTPOINT/$TIMESTAMP
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
cp something.png $MCONTAINER || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "view existing data node "
ls $MCONTAINER/something.png >& /dev/null || echo [FAIL] && exit -1
$LSCMD $CERT $CONTAINER/something.png > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

#TODO this sleep is to allow the file to be uploaded on the server, since
# a bug brings vofs down if the node is busy. To be removed when
# the vofs bug is fixed.
echo "[TODO] - pause to allow file to get uploaded first" 

echo -n "copy data node to local filesystem "
cp $MCONTAINER/something.png something.png.2 || echo " [FAIL]" && exit -1
cmp something.png something.png.2 || echo " [FAIL]" && exit -1
rm -f something.png.2
echo " [OK]"

echo -n "copy/overwrite existing data node "
cp something.png $MCONTAINER/something.png || echo " [FAIL]" && exit -1
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

echo
echo "*** test sequence passed ***"

date
