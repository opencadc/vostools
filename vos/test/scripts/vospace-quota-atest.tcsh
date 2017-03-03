#!/bin/tcsh -f
if (! ${?VOSPACE_WEBSERVICE} ) then
	echo "VOSPACE_WEBSERVICE env variable not set, use default WebService URL"
else
	echo "WebService URL (VOSPACE_WEBSERVICE env variable): $VOSPACE_WEBSERVICE"
endif

if (! ${?CADC_TESTCERT_PATH} ) then
	echo "CADC_TESTCERT_PATH env variable not set. Must point to the location of x509_CADCAuthtest2.pem cert file"
    exit -1
else
    set CERTFILE = "$CADC_TESTCERT_PATH/x509_CADCAuthtest2.pem"
	echo "cert file:  ($CADC_TESTCERT_PATH env variable): $CERTFILE"
endif


set THIS_DIR = `dirname $0`
set THIS_DIR = `cd $THIS_DIR && pwd`

set MKDIRCMD = "vmkdir"
set CPCMD = "vcp"
set LSCMD = "vls"
set RMDIRCMD = "vrmdir"
set CERT = "--cert=$CERTFILE"

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
        $MKDIRCMD $CERT $BASE >& || echo " [FAIL]" && exit -1
    echo " [OK]"
endif

echo "*** starting test sequence for $CONTAINER ***"

echo -n "Create container"
$MKDIRCMD $CERT $CONTAINER || (echo " [FAIL]" ; exit -1)
echo " [OK]"

echo -n "Upload data node in excess of quota (expect error)"
$CPCMD $CERT $THIS_DIR/something.png $CONTAINER/testdata.png | grep -qi "quota" && echo " [FAIL]" && exit -1
echo " [OK]"


echo -n "delete container "
$RMDIRCMD $CERT $CONTAINER >& /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"
echo
echo "*** test sequence passed ***"

date
