#!/bin/tcsh -f

date

if ( ${?CADC_ROOT} ) then
	echo "using CADC_ROOT = $CADC_ROOT"
else
	set CADC_ROOT = "/usr/cadc/local"
	echo "using CADC_ROOT = $CADC_ROOT"
endif

set TRUST = "-Dca.nrc.cadc.auth.BasicX509TrustManager.trust=true"
set LOCAL = ""
if ( ${?1} ) then
        if ( $1 == "localhost" ) then
                set LOCAL = "-Dca.nrc.cadc.reg.client.RegistryClient.local=true $TRUST"
        else if ( $1 == "devtest" ) then
                set LOCAL = "-Dca.nrc.cadc.reg.client.RegistryClient.host=devtest.cadc-ccda.hia-iha.nrc-cnrc.gc.ca $TRUST"
        else if ( $1 == "test" ) then
                set LOCAL = "-Dca.nrc.cadc.reg.client.RegistryClient.host=test.cadc-ccda.hia-iha.nrc-cnrc.gc.ca"
        else if ( $1 == "rc" ) then
                set LOCAL = "-Dca.nrc.cadc.reg.client.RegistryClient.host=rc.cadc-ccda.hia-iha.nrc-cnrc.gc.ca $TRUST"
        else if ( $1 == "rcdev" ) then
                set LOCAL = "-Dca.nrc.cadc.reg.client.RegistryClient.host=rcdev.cadc-ccda.hia-iha.nrc-cnrc.gc.ca $TRUST"
        endif
endif

## we cannot feasibly test the --xsv option, but it is here to fiddle with in development
set LSCMD = "$CADC_ROOT/bin/vls"
set MKDIRCMD = "$CADC_ROOT/bin/vmkdir"
set RMCMD = "$CADC_ROOT/bin/vrm"
set CPCMD = "$CADC_ROOT/bin/vcp"
set RMDIRCMD = "$CADC_ROOT/bin/vrmdir"

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
        $CMD $CERT --create --target=$BASE || echo " [FAIL]" && exit -1
	echo " [OK]"
endif
#TODO
echo -n "** setting home and base to public, no groups"
#$CMD $CERT --set --public --group-read="" --group-write="" --target=$VOHOME || echo " [FAIL]" && exit -1
echo -n " [TODO]"
#$CMD $CERT --set --public --group-read="" --group-write="" --target=$BASE || echo " [FAIL]" && exit -1
echo " [TODO]"
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

#TODO vchmod
echo -n "verify public=false after create "
#$CMD $CERT --view --target=$CONTAINER | grep -q 'readable by anyone: false' || echo " [FAIL]" && exit -1
echo "[TODO]"

echo -n "check set permission properties "
#$CMD $CERT --set --public --group-read=test:g1 --group-write=test:g2 --target=$CONTAINER || echo " [FAIL]" && exit -1
#$CMD $CERT --view --target=$CONTAINER | grep -q 'readable by anyone: true' || echo " [FAIL]" && exit -1
#$CMD $CERT --view --target=$CONTAINER | grep -q 'readable by: test:g1' || echo " [FAIL]" && exit -1
#$CMD $CERT --view --target=$CONTAINER | grep -q 'writable by: test:g2' || echo " [FAIL]" && exit -1
echo "[TODO]"

echo -n "check inherit permission properties "
#$CMD $CERT --create --target=$CONTAINER/pub || echo " [FAIL]" && exit -1
#$CMD $CERT --view --target=$CONTAINER/pub | grep -q 'readable by anyone: true' || echo " [FAIL]" && exit -1
#$CMD $CERT --view --target=$CONTAINER/pub | grep -q 'readable by: test:g1' || echo " [FAIL]" && exit -1
#$CMD $CERT --view --target=$CONTAINER/pub | grep -q 'writable by: test:g2' || echo " [FAIL]" && exit -1
echo "[TODO]"

echo -n "check inherit certain properties "
#$CMD $CERT --create --target=$CONTAINER/priv --group-read=test:g3 || echo " [FAIL]" && exit -1
#$CMD $CERT --view --target=$CONTAINER/priv | grep -q 'readable by anyone: true' || echo " [FAIL]" && exit -1
#$CMD $CERT --view --target=$CONTAINER/priv | grep -q 'readable by: test:g3' || echo " [FAIL]" && exit -1
#$CMD $CERT --view --target=$CONTAINER/priv | grep -q 'writable by: test:g2' || echo " [FAIL]" && exit -1
echo "[TODO]"

echo -n "check recursive create (non-existant parents) "
$MKDIRCMD $CERT $CONTAINER/foo/bar/baz || echo " [FAIL]" && exit -1
$LSCMD $CERT $CONTAINER/foo/bar/baz > /dev/null || echo " [FAIL]" #TODO add -p option&& exit -1
echo "[OK]"

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
$RMCMD $CERT $CONTAINER/something.png || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "view deleted/non-existent data node "
$LSCMD $CERT $CONTAINER/something.png >& /dev/null && echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "delete non-existent data node "
$RMCMD $CERT $CONTAINER/something.png >& /dev/null && echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "delete non-empty container "
$RMDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
$LSCMD $CERT $CONTAINER/something2.png >& /dev/null && echo " [FAIL]" && exit -1
$LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "delete empty container "
$MKDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
$LSCMD $CERT $CONTAINER > /dev/null || echo " [FAIL]" && exit -1
$RMDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
$LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
echo " [OK]"

echo
echo "*** test sequence passed ***"

date
