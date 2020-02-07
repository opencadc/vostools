#!/bin/tcsh -f

date
echo "###################"
/bin/rm -rf ~/.config/cadc-registry
if (! ${?VOSPACE_WEBSERVICE} ) then
	echo "VOSPACE_WEBSERVICE env variable not set, use default WebService URL"
    setenv VOSPACE_WEBSERVICE 'www.cadc-ccda.hia-iha.nrc-cnrc.gc.ca'
else
	echo "WebService URL (VOSPACE_WEBSERVICE env variable): ${VOSPACE_WEBSERVICE}"
endif

if (! ${?CADC_TESTCERT_PATH} ) then
	echo "CADC_TESTCERT_PATH env variable not set. Must point to the location of x509_CADCRegtest1.pem cert file"
    exit -1
else
    set CERTFILE = "$CADC_TESTCERT_PATH/x509_CADCRegtest1.pem"
	echo "cert file:  (CADC_TESTCERT_PATH env variable): $CERTFILE"
endif

if (! ${?CADC_DEBUG} ) then
    echo "Debugging is on.  Expect much output."
    set DEBUG_FLAG = "-d"
else
    set DEBUG_FLAG = ""
endif

if (! ${?TMPDIR} ) then
        echo "TMPDIR env variable not set, using /tmp"
        set TMPDIR = "/tmp"
else
        echo "Using ${TMPDIR} for temporary files"
endif

set SITE_1_RESOURCE_ID = "ivo://cadc.nrc.ca/minoc"
set SET_SITE_1_RESOURCE_ID = " --resource-id ${SITE_1_RESOURCE_ID}"

set THIS_DIR = `dirname $0`
set THIS_DIR = `cd $THIS_DIR && pwd`

set RMCMD = "vrm ${DEBUG_FLAG} ${SET_SITE_1_RESOURCE_ID}"
set CPCMD = "vcp ${DEBUG_FLAG} ${SET_SITE_1_RESOURCE_ID}"
set RMDIRCMD = "vrmdir ${DEBUG_FLAG}"

set CERT = " --cert=$CERTFILE"

# using a test dir makes it easier to cleanup a bunch of old/failed tests
set ROOT = "cadc:"
set HOME = "${ROOT}CADCRegtest1"
set BASE = "${HOME}/atest"

set TIMESTAMP=`date +%Y-%m-%dT%H-%M-%S`
set CONTAINER = "${BASE}/${TIMESTAMP}"

echo
echo "*** Starting test sequence ***"
echo
echo "** test container: ${CONTAINER}"
echo
echo "** test host: ${VOSPACE_WEBSERVICE}"
echo
echo "** test resource: ${SITE_1_RESOURCE_ID}"
echo


echo -n "Copy file to existing container and non-existent data node "
$CPCMD $CERT $THIS_DIR/something.png ${CONTAINER}/something.png || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "copy empty files"
rm -f /tmp/zerosize.txt
touch /tmp/zerosize.txt
$CPCMD $CERT /tmp/zerosize.txt ${CONTAINER}/ || echo " [FAIL]" && exit -1
# repeat
$CPCMD $CERT /tmp/zerosize.txt ${CONTAINER}/ || echo " [FAIL]" && exit -1
# change size
echo "test" > /tmp/zerosize.txt
$CPCMD $CERT /tmp/zerosize.txt ${CONTAINER}/ || echo " [FAIL]" && exit -1
# repeat
$CPCMD $CERT /tmp/zerosize.txt ${CONTAINER}/ || echo " [FAIL]" && exit -1
# make it back 0 size
/bin/cp /dev/null /tmp/zerosize.txt
$CPCMD $CERT /tmp/zerosize.txt ${CONTAINER}/ || echo " [FAIL]" && exit -1
# repeat
$CPCMD $CERT /tmp/zerosize.txt ${CONTAINER}/ || echo " [FAIL]" && exit -1
echo " [OK]"

echo
echo "*** Test sequence passed ***"

date
