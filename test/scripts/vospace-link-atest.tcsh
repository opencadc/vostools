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

set LSCMD = "$CADC_ROOT/bin/vls"
set MKDIRCMD = "$CADC_ROOT/bin/vmkdir"
set RMCMD = "$CADC_ROOT/bin/vrm"
set CPCMD = "$CADC_ROOT/bin/vcp"
set RMDIRCMD = "$CADC_ROOT/bin/vrmdir"
set LNCMD = "$CADC_ROOT/bin/vln"

set CERT = " --cert=$A/test-certificates/x509_CADCRegtest1.pem"
set CERT2 = " --cert=$A/test-certificates/x509_CADCAuthtest1.pem"

echo "command: " $LNCMD $CERT
echo

# using a test dir makes it easier to cleanup a bunch of old/failed tests
set VOROOT = "vos:"
set VOHOME = "$VOROOT""CADCRegtest1"
set BASE = "$VOHOME/atest"

set TIMESTAMP=`date +%Y-%m-%dT%H-%M-%S`
set CONTAINER = $BASE/$TIMESTAMP

echo -n "** checking base URI"
$LSCMD -v $CERT $BASE > /dev/null
if ( $status == 0) then
	echo " [OK]"
else
	echo -n ", creating base URI"
        $MKDIRCMD $CERT $BASE || echo " [FAIL]" && exit -1
	echo " [OK]"
endif
echo -n "** setting home and base to public, no groups"
#$CMD $CERT --set --public --group-read="" --group-write="" --target=$VOHOME || echo " [FAIL]" && exit -1
echo -n " [TODO]"
#$CMD $CERT --set --public --group-read="" --group-write="" --target=$BASE || echo " [FAIL]" && exit -1
echo " [TODO]"
echo

echo "*** starting test sequence ***"
echo

echo -n "create base container"
$MKDIRCMD $CERT $CONTAINER > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "create container to be valid link target"
$MKDIRCMD $CERT $CONTAINER/target > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
 
echo -n "copy file to target container"
$CPCMD $CERT something.png $CONTAINER/target/something.png || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "create link to target container"
echo "$LNCMD $CERT $CONTAINER/target $CONTAINER/clink"
$LNCMD $CERT $CONTAINER/target $CONTAINER/clink > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "Follow the link to get the file"
$CPCMD $CERT $CONTAINER/clink/something.png /tmp || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "Follow the link without read permission and fail"
$CPCMD $CERT2 $CONTAINER/clink/something.png /tmp && echo " [FAIL]" && exit -1
echo " [OK]"
 
echo -n "create link to target file"
$LNCMD $CERT $CONTAINER/dlink $CONTAINER/target/something.png > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "Follow the link to get the file"
$CPCMD $CERT $CONTAINER/dlink /tmp || echo " [FAIL]" && exit -1
echo " [OK]"
 
echo -n "create link to unknown authority in URI"
$LNCMD $CERT vos://unknown.authority~vospace/unknown $CONTAINER/e1link > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "Follow the invalid link and fail"
$CPCMD $CERT $CONTAINER/e1link/somefile /tmp && echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "create link to unknown scheme in URI"
$LNCMD $CERT unknown://cadc.nrc.ca~vospace/CADCRegtest1 $CONTAINER/e2ink > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "Follow the invalid link and fail"
$CPCMD $CERT $CONTAINER/e2link/somefile /tmp && echo " [FAIL]" && exit -1
echo " [OK]"
 
echo -n "create link to external http URI"
$LNCMD $CERT http://www.google.ca $CONTAINER/e3link > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
 
echo -n "Follow the invalid link and fail"
$CPCMD $CERT $CONTAINER/e3link/somefile /tmp && echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "copy file to target through link"
$CPCMD $CERT something.png $CONTAINER/clink/something2.png || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "Get the file through the link"
$CPCMD $CERT $CONTAINER/clink/something2.png /tmp || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "Get the file through the target"
$CPCMD $CERT $CONTAINER/target/something2.png /tmp || echo " [FAIL]" && exit -1
echo " [OK]"

echo
echo "*** test sequence passed ***"

date
