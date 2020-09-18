#!/bin/tcsh -f
set THIS_DIR = `dirname $0`
set THIS_DIR = `cd $THIS_DIR && pwd`


if (! ${?VOSPACE_WEBSERVICE} ) then
	echo "VOSPACE_WEBSERVICE env variable not set, use default WebService URL"
else
	echo "WebService URL (VOSPACE_WEBSERVICE env variable): $VOSPACE_WEBSERVICE"
endif

if (! ${?CADC_TESTCERT_PATH} ) then
	echo "CADC_TESTCERT_PATH env variable not set. Must point to the location of test cert files"
    exit -1
else
	echo "cert files path:  ($CADC_TESTCERT_PATH env variable): $CADC_TESTCERT_PATH"
endif

if (! ${?VOSPACE_CONFIG_FILE} ) then
    echo "VOSPACE_CONFIG_FILE env variable not set. Using /tmp/vos-config"
    $THIS_DIR/set_config_file || echo "FAIL set_config_file" && exit -1
    setenv VOSPACE_CONFIG_FILE /tmp/test-vos-config
else
    echo "Using VOSPACE_CONFIG_FILE: $VOSPACE_CONFIG_FILE"
endif

if($#argv == 0) then
    set resources = "vault cavern"
    echo "Testing against default resources: $resources"
else
    set resources = ($argv)
    echo "Testing against resources: $resources"
endif

set CHMODCMD = "vchmod"
set MKDIRCMD = "vmkdir"
set LSCMD = "vls -l"
set CPCMD = "vcp"
set RMDIRCMD = "vrmdir"

set CERT =  "--cert=$CADC_TESTCERT_PATH/x509_CADCRegtest1.pem"
set CERT1 = "--cert=$CADC_TESTCERT_PATH/x509_CADCAuthtest1.pem"
set CERT2 = "--cert=$CADC_TESTCERT_PATH/x509_CADCAuthtest2.pem"

echo "vchmod command: " $CHMODCMD $CERT
echo "vchmod command 1:    " $CHMODCMD $CERT1
echo "vchmod command 2:    " $CHMODCMD $CERT2

echo

# group 3000 aka CADC_TEST_GROUP1 has members: CADCAuthtest1
set GROUP1 = "CADC_TEST_GROUP1"

# group 3100 aka CADC_TEST_GROUP2 has members: CADCAuthtest1, CADCAuthtest2
set GROUP2 = "CADC_TEST_GROUP2"

foreach resource ($resources)
    echo "************* TESTING AGAINST $resource ****************"

    # vault uses CADCRegtest1, cavern uses home/cadcregtest1
    echo $resource | grep "cavern" >& /dev/null
    if ( $status == 0) then
    set HOME_BASE = "home/cadcregtest1"
        set VOROOT = "arc:"
        set TESTING_CAVERN = "true"
    else
        set VOROOT = "vos:"
        set HOME_BASE = "CADCRegtest1"
    endif

    set VOHOME = "$VOROOT""$HOME_BASE"
    set BASE = "$VOHOME/atest"

    set TIMESTAMP=`date +%Y-%m-%dT%H-%M-%S`
    set CONTAINER = $BASE/$TIMESTAMP

    echo -n "** checking base URI"
    echo "$LSCMD $CERT $BASE"
    $LSCMD $CERT $BASE > /dev/null
    if ( $status == 0) then
        echo " [OK]"
    else
        echo -n ", creating base URI"
            $MKDIRCMD $CERT $BASE || echo " [FAIL]" && exit -1
        echo " [OK]"
    endif

    echo -n "** setting home and base to public, no groups"
    $CHMODCMD $CERT o+r $VOHOME || echo " [FAIL]" && exit -1
    echo -n " [OK]"
    $CHMODCMD $CERT o+r $BASE || echo " [FAIL]" && exit -1
    echo " [OK]"
    echo
    echo "*** starting test sequence ***"
    echo
    echo "** test container: ${CONTAINER}"
    echo

    echo -n "create container "
    $MKDIRCMD $CERT $CONTAINER ||  echo " [FAIL]" && exit -1
    $CHMODCMD $CERT o+r $CONTAINER ||  echo " [FAIL]" && exit 1
    $CHMODCMD $CERT g+w $CONTAINER $GROUP2 ||  echo " [FAIL]" && exit -1

    echo -n " verify "
    $LSCMD $CERT $CONTAINER > /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "create container/sub1 (inherit permissions) "
    $MKDIRCMD $CERT $CONTAINER/sub1 || echo " [FAIL]" && exit -1
    echo -n " verify "
    $LSCMD $CERT $CONTAINER/sub1 > /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "create container/sub1/sub2 (group-write=$GROUP1) "
    $MKDIRCMD $CERT $CONTAINER/sub1/sub2 || echo " [FAIL]" && exit -1
    $CHMODCMD $CERT g+w $CONTAINER/sub1/sub2 $GROUP1 || echo " [FAIL]" && exit -1
    $LSCMD $CERT $CONTAINER/sub1/sub2 > /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "put data node in sub2 "
    $CPCMD $CERT $THIS_DIR/something.png $CONTAINER/sub1/sub2/something.png || echo " [FAIL]" && exit -1
    echo -n " verify "
    $LSCMD $CERT $CONTAINER/sub1/sub2/something.png > /dev/null || echo " [FAIL]" && exit -1
    echo -n " verify "
    if ( ${?TESTING_CAVERN} ) then
        echo " [SKIPPED, permission inheritance not supported]"
    else
        $LSCMD $CERT $CONTAINER/sub1/sub2/something.png | grep -q "$GROUP1" || echo " [FAIL]" && exit -1
        echo " [OK]"
    endif

    echo -n "test delete container/sub1 (denied)"
    $RMDIRCMD $CERT2 $CONTAINER/sub1/sub2 >& /dev/null && echo " [FAIL]" && exit -1
    echo -n " verify "
    $LSCMD $CERT $CONTAINER/sub1/sub2/something.png > /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "test delete container/sub1 (allowed)"
    if ( ${?TESTING_CAVERN} ) then
        echo " [SKIPPED, permission inheritance not supported]"
    else
        $RMDIRCMD $CERT1 $CONTAINER/sub1 >& /dev/null || echo " [FAIL]" && exit -1
        echo -n " verify "
        $LSCMD $CERT $CONTAINER/sub1 >& /dev/null && echo " [FAIL]" && exit -1
        echo " [OK]"
    endif

# start fresh

    echo -n "create container/sub1 "
    if ( ${?TESTING_CAVERN} ) then
        echo " [SKIPPED, permission inheritance not supported]"
    else
        $MKDIRCMD $CERT $CONTAINER/sub1 || echo " [FAIL]" && exit -1
    endif
    $CHMODCMD $CERT o-r $CONTAINER/sub1 || echo " [FAIL]" && exit -1
    $CHMODCMD $CERT g+w $CONTAINER/sub1 $GROUP1 || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "put data node in container/sub1 (allowed) "
    $CPCMD $CERT1 $THIS_DIR/something.png $CONTAINER/sub1/something.png || echo " [FAIL]" && exit -1
    echo -n " verify "
    $LSCMD $CERT1 $CONTAINER/sub1/something.png > /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "test root read "
    $LSCMD $CERT $CONTAINER/sub1 > /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "test root delete"
    $RMDIRCMD $CERT $CONTAINER/sub1 >& /dev/null || echo " [FAIL]" && exit -1
    echo -n " verify "
    $LSCMD $CERT1 $CONTAINER/sub1 >& /dev/null && echo " [FAIL]" && exit -1
    echo " [OK]"
    echo
    echo "*** test sequence passed for resource $resource ***"
end

echo "*** ALL test sequence passed ***"
date
