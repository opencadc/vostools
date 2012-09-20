#!/bin/tcsh -f

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

set MKDIRCMD = "$CADC_ROOT/scripts/vmkdir"
set CPCMD = "$CADC_ROOT/scripts/vcp"
set LSCMD = "$CADC_ROOT/scripts/vls"
set RMDIRCMD = "$CADC_ROOT/scripts/vrmdir"
set CERT = "--cert=$A/test-certificates/x509_CADCAuthtest2.pem"

echo

# using a test dir makes it easier to cleanup a bunch of old/failed tests
set VOHOME = "vos:CADCAuthtest2"
set BASE = "$VOHOME/atest"

set TIMESTAMP=`date +%Y-%m-%dT%H-%M-%S`
set CONTAINER = $BASE/$TIMESTAMP


echo -n "** checking base URI"
$LSCMD -v $CERT $BASE >& /dev/null
if ( $status == 0) then
	echo " [OK]"
else
	echo -n ", creating base URI"
        $MKDIRCMD $CERT $BASE >& || echo " [FAIL]" && exit -1
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
$RMDIRCMD $CERT $CONTAINER >& /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"


echo
echo "*** test sequence passed ***"

date
