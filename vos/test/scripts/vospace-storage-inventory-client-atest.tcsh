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

#set ACCESS_PAGE=https://ws-cadc.canfar.net/ac/login
#set VOS_BASE = "vos://cadc.nrc.ca~vault"
set SITE_1_RESOURCE_ID = "ivo://cadc.nrc.ca/minoc"
set SET_SITE_1_RESOURCE_ID = " --resource-id ${SITE_1_RESOURCE_ID}"

set THIS_DIR = `dirname $0`
set THIS_DIR = `cd $THIS_DIR && pwd`

# Username / password for getting tokens
#echo "Enter credentials for a VOSpace account in which we will perform tests."
#echo -n "CADC Username: "
#set username = $<
#echo -n "Password: "
#stty -echo
#set password = $<
#echo
#stty echo

#set TOKEN = "`curl -s -d username=$username -d password=$password ${ACCESS_PAGE}'?'scope=${VOS_BASE}/${username}`"

set DIFFCMD = "diff -q"

set RMCMD = "vrm ${DEBUG_FLAG} ${SET_SITE_1_RESOURCE_ID}"
set CPCMD = "vcp ${DEBUG_FLAG} ${SET_SITE_1_RESOURCE_ID}"
set RMDIRCMD = "vrmdir ${DEBUG_FLAG}"

set CERT = " --cert=$CERTFILE"
#set TOKEN = "--token ${TOKEN}"

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

echo "-----------"
echo -n "Copy file to existing container and non-existent data node "
$CPCMD ${CERT} $THIS_DIR/something.png ${CONTAINER}/something.png || echo " [FAIL]" && exit -1
echo " [OK]"

# echo "-----------"
# echo -n "Copy existing file from existing container "
# $CPCMD $CERT $CONTAINER/something.png $THIS_DIR/something.png.2 || echo " [FAIL]" && exit -1
# cmp $THIS_DIR/something.png $THIS_DIR/something.png.2 || echo " [FAIL]" && exit -1
# echo " [OK]"

# echo "-----------"
# echo -n "Copy/overwrite existing data node "
# $CPCMD $CERT $THIS_DIR/something.png.2 $CONTAINER/something.png || echo " [FAIL]" && exit -1
# echo " [OK]"

# echo -n "Upload check quick copy "
# $CPCMD $CERT $THIS_DIR/something.png $CONTAINER/something.png.3|| echo " [FAIL]" && exit -1
# echo " [OK]"

# echo -n "Download check quick copy "
# $CPCMD $CERT $CONTAINER/something.png.3 $THIS_DIR/something.png.3 || echo " [FAIL]" && exit -1
# echo " [OK]"

# echo -n "Compare check quick copy "
# cmp $THIS_DIR/something.png $THIS_DIR/something.png.3 || echo " [FAIL]" && exit -1
# \rm -f $THIS_DIR/something.png.3
# echo " [OK]"

# echo -n "Check pattern matched copy"
# $CPCMD $CERT "$CONTAINER/something*" $TMPDIR || echo " [FAIL]" && exit -1
# $CPCMD $CERT $THIS_DIR/something* $CONTAINER/pattern_dest || echo " [FAIL]" && exit -1
# echo " [OK]"

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
