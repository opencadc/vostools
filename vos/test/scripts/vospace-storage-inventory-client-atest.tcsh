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
    set CERTFILE = "$CADC_TESTCERT_PATH/x509_CADCAuthtest1.pem"
	echo "cert file:  (CADC_TESTCERT_PATH env variable): $CERTFILE"
endif

if (! ${?CADC_DEBUG} ) then
    set DEBUG_FLAG = ""
else
    echo "Debugging is on.  Expect much output."
    set DEBUG_FLAG = "-d"
endif

if (! ${?TMPDIR} ) then
        echo "TMPDIR env variable not set, using /tmp"
        set TMPDIR = "/tmp"
else
        echo "Using ${TMPDIR} for temporary files"
endif

set SITE_1_RESOURCE_ID = "ivo://cadc.nrc.ca/alpha-site-ceph/minoc"
set SET_SITE_1_RESOURCE_ID = " --resource-id ${SITE_1_RESOURCE_ID}"

set THIS_DIR = `dirname $0`
set THIS_DIR = `cd $THIS_DIR && pwd`

set RMCMD = "vrm ${DEBUG_FLAG} ${SET_SITE_1_RESOURCE_ID}"
set CPCMD = "vcp ${DEBUG_FLAG} ${SET_SITE_1_RESOURCE_ID}"

set CERT = " --cert=$CERTFILE"

# using a test dir makes it easier to cleanup a bunch of old/failed tests
set ROOT = "cadc:TEST/"
set HOME = "${ROOT}CADCAuthtest1"
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

echo "LOCAL TESTS"
echo "-----------"
echo -n "Copy file to existing container and non-existent URI"
$CPCMD $CERT $THIS_DIR/something.png ${CONTAINER}/something.png || echo " [FAIL]" && exit -1
echo " [OK]"

 echo "-----------"
 echo -n "Copy existing file from existing URI"
 $CPCMD $CERT $CONTAINER/something.png /tmp/something.png || echo " [FAIL]" && exit -1
 cmp $THIS_DIR/something.png /tmp/something.png || echo " [FAIL]" && exit -1
 \rm -f /tmp/something.png
 echo " [OK]"

 echo "-----------"
 echo -n "Remove existing URI"
 $RMCMD $CERT $CONTAINER/something.png || echo " [FAIL]" && exit -1
 $CPCMD $CERT $CONTAINER/something.png /tmp/something.png.2 && echo " [FAIL]" && exit
 echo " [OK]"


# Cutouts not supported yet.
# jenkinsd 2020.02.11
#
# echo -n "Check pattern with cutout"
# $CPCMD $CERT "$BASE/test*.fits[1:10,1:10]" $TMPDIR || echo " [FAIL]" && exit -1
# echo " [OK]"

# Not supported yet.
# jenkinsd 2020.02.11
#
# echo -n "Copy empty files and should print message "
# rm -f /tmp/zerosize.txt
# touch /tmp/zerosize.txt
# $CPCMD $CERT /tmp/zerosize.txt ${CONTAINER}/ || echo " [FAIL]" && exit -1
# echo " [OK]"

# echo -n "delete existing data node "
# $RMCMD $CERT $CONTAINER/something.png  >& /dev/null || echo " [FAIL]" && exit -1
# echo " [OK]"

echo "-----------"
echo -n "Cleanup "
\rm -f $THIS_DIR/something.png.*
echo " [OK]"

echo
echo "*** Test sequence passed ***"

date
