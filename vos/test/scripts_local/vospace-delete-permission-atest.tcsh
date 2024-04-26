#!/bin/tcsh -f
set THIS_DIR = `dirname $0`
set THIS_DIR = `cd $THIS_DIR && pwd`


if (! ${?LOCAL_VOSPACE_WEBSERVICE} ) then
	echo "LOCAL_VOSPACE_WEBSERVICE env variable required"
	exit -1
else
  if ( ${?VOSPACE_WEBSERVICE} ) then
	  echo "VOSPACE_WEBSERVICE env variable cannot be set for local tests"
	  exit -1
	endif
	echo "WebService URL (LOCAL_VOSPACE_WEBSERVICE env variable): $LOCAL_VOSPACE_WEBSERVICE"
endif

if (! ${?CADC_TESTCERT_PATH} ) then
  echo "Missing CADC_TESTCERT_PATH location to cadc-auth.pem and cadc-auth-test.pem files"
  exit -1
else
	echo "cert files path:  ($CADC_TESTCERT_PATH env variable): $CADC_TESTCERT_PATH"
	set CERT =  "--cert=$CADC_TESTCERT_PATH/cadc-auth.pem"
  set CERT1 = "--cert=$CADC_TESTCERT_PATH/cadc-auth-test.pem"
endif

if($#argv == 0) then
    set resources = "vault cavern"
    echo "Testing against default resources: $resources"
else
    set resources = ($argv)
    echo "Testing against resources: $resources"
endif

set CHMODCMD = "vchmod -k"
set MKDIRCMD = "vmkdir -k"
set LSCMD = "vls -k -l"
set CPCMD = "vcp -k"
set RMDIRCMD = "vrmdir -k"
set RMCMD = "vrm -k"
set VTAGCMD = "vtag -k"

# group 3000 aka CADC_TEST_GROUP1 has members: CADCAuthtest1
set GROUP1 = "CADC_TEST_GROUP1"

echo "vchmod command: " $CHMODCMD $CERT
echo "vchmod command 1:    " $CHMODCMD $CERT1

echo

foreach resource ($resources)
    echo "************* TESTING AGAINST $resource ****************"

    echo $resource | grep "cavern" >& /dev/null
    if ( $status == 0) then
        set VOROOT = "cavern:"
        set TESTING_CAVERN = "true"
    else
        set VOROOT = "vos:"
    endif

    set HOME_BASE = "vostools-inttest"
    set VOHOME = "$VOROOT""$HOME_BASE"
    set BASE = $VOHOME

    echo -n ", creating base URI"
    $RMCMD -R $CERT $BASE > /dev/null
    $MKDIRCMD $CERT $BASE || echo " [FAIL]" && exit -1
    $VTAGCMD $CERT $BASE 'ivo://cadc.nrc.ca/vospace/core#inheritPermissions=true'
  echo " [OK]"

    set TIMESTAMP=`date +%Y-%m-%dT%H-%M-%S`
    set CONTAINER = $BASE/$TIMESTAMP

    echo -n "** checking base URI"
    $LSCMD $CERT $BASE > /dev/null
    if ( $status == 0) then
        echo " [OK]"
    else
        echo -n ", creating base URI"
            $MKDIRCMD $CERT $BASE || echo " [FAIL]" && exit -1
        echo " [OK]"
    endif

    echo -n "** setting home and base to public, no groups"
    $CHMODCMD $CERT o+r $VOHOME || echo " [FAIL1]" && exit -1
    $CHMODCMD $CERT o+r $BASE || echo " [FAIL2]" && exit -1
    echo " [OK]"
    echo
    echo "*** starting test sequence ***"
    echo
    echo "** test container: ${CONTAINER}"
    echo

    echo -n "create container "
    $MKDIRCMD $CERT $CONTAINER ||  echo " [FAIL1]" && exit -1
    $CHMODCMD $CERT o+r $CONTAINER ||  echo " [FAIL2]" && exit 1

    echo -n " verify "
    $LSCMD $CERT $CONTAINER > /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "create container/sub1 (inherit permissions) "
    $MKDIRCMD $CERT $CONTAINER/sub1 || echo " [FAIL]" && exit -1
    echo -n " verify "
    $LSCMD $CERT $CONTAINER/sub1 > /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "create container/sub1/sub2 "
    $MKDIRCMD $CERT $CONTAINER/sub1/sub2 || echo " [FAIL]" && exit -1
    $LSCMD $CERT $CONTAINER/sub1/sub2 > /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "put data node in sub2 "
    $CPCMD $CERT $THIS_DIR/something.png $CONTAINER/sub1/sub2/something.png || echo " [FAIL]" && exit -1
    echo -n " verify "
    $LSCMD $CERT $CONTAINER/sub1/sub2/something.png > /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "test delete container/sub1 (denied)"
    $RMCMD -R $CERT1 $CONTAINER/sub1/sub2 >& /dev/null && echo " [FAIL]" && exit -1
    echo -n " verify "
    $LSCMD $CERT $CONTAINER/sub1/sub2/something.png > /dev/null || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "test grant group write $GROUP1 -> delete container/sub1 (allowed)"
    $CHMODCMD -R $CERT g+w $CONTAINER $GROUP1 || echo " [FAIL]" && exit -1
    echo -n " verify "
    $LSCMD $CERT $CONTAINER/sub1/sub2/something.png | grep -q "$GROUP1" || echo " [FAIL1]" && exit -1
    $RMCMD -d -R $CERT1 $CONTAINER/sub1 >& /dev/null || echo " [FAIL2]" && exit -1
    $LSCMD $CERT $CONTAINER/sub1 >& /dev/null && echo " [FAIL3]" && exit -1
    echo " [OK]"
    endif

# start fresh

    echo -n "create container/sub1 "
    $MKDIRCMD $CERT $CONTAINER/sub1 || echo " [FAIL]" && exit -1
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
    $RMCMD -R $CERT $CONTAINER/sub1 >& /dev/null || echo " [FAIL]" && exit -1
    echo -n " verify "
    $LSCMD $CERT1 $CONTAINER/sub1 >& /dev/null && echo " [FAIL]" && exit -1
    echo " [OK]"
    echo
    echo "*** test sequence passed for resource $resource ***"
end

echo "*** ALL test sequence passed ***"
date
