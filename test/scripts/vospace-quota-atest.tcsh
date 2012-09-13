#!/bin/tcsh -f

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

set MKDIRCMD = "$CADC_ROOT/bin/vmkdir"
set CPCMD = "$CADC_ROOT/bin/vcp"
set LSCMD = "$CADC_ROOT/bin/vls"
set RMDIRCMD = "$CADC_ROOT/bin/vrmdir"
set CERT = "--cert=$A/test-certificates/x509_CADCAuthtest2.pem"

echo

# using a test dir makes it easier to cleanup a bunch of old/failed tests
set VOHOME = "vos:CADCAuthtest2"
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

echo "*** starting test sequence for $CONTAINER ***"

echo -n "Create container"
$MKDIRCMD $CERT $CONTAINER || (echo " [FAIL]" ; exit -1)
echo " [OK]"

echo -n "Upload data node in excess of quota (expect error)"
$CPCMD $CERT something.png $CONTAINER/testdata.png | grep -qi "quota" && echo " [FAIL]" && exit -1
echo " [OK]"


echo -n "delete container "
$RMDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
echo " [OK]"


echo
echo "*** test sequence passed ***"

date
