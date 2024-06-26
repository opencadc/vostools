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
	set CERT1 =  "--cert=$CADC_TESTCERT_PATH/cadc-auth-test.pem"
endif

if($#argv == 0) then
    set resources = "vault cavern"
    echo "Testing against default resources: $resources"
else
    set resources = ($argv)
    echo "Testing against resources: $resources"
endif

echo

set LSCMD = "vls -k"
set MKDIRCMD = "vmkdir -k"
set RMCMD = "vrm -k"
set CHMODCMD = "vchmod -k"
set VSYNCCMD = "vsync -k"
set VTAGCMD = "vtag -k"

set TEST_DIR = vsync_test
set ABS_TEST_DIR = /tmp/$TEST_DIR

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

  set TIMESTAMP=`date +%Y-%m-%dT%H-%M-%S`
  set CONTAINER = $BASE/$TIMESTAMP

  echo -n "** checking base URI"
  $RMCMD -R $CERT $BASE > /dev/null
  echo -n ", creating base URI"
      $MKDIRCMD $CERT $BASE || echo " [FAIL]" && exit -1
      $VTAGCMD $CERT $BASE 'ivo://cadc.nrc.ca/vospace/core#inheritPermissions=true'
  echo " [OK]"

  echo -n "** setting home and base to public, no groups"
  $CHMODCMD $CERT o+r $VOHOME || echo " [FAIL]" && exit -1
  $CHMODCMD $CERT o+r $BASE || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo "*** starting test sequence ***"
  echo
  echo "** test container: ${CONTAINER}"
  echo

  echo -n "setup: create container "
  $MKDIRCMD $CERT $CONTAINER ||  echo " [FAIL]" && exit -1
  $CHMODCMD $CERT o-r $CONTAINER ||  echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"


  echo -n "vsync a directory"
  rm -fR $ABS_TEST_DIR >& /dev/null
  mkdir $ABS_TEST_DIR
  $VSYNCCMD $CERT $ABS_TEST_DIR $CONTAINER || echo " [FAIL]" && exit -1
  $LSCMD $CERT $CONTAINER/$TEST_DIR || echo " [FAIL]" && exit -1
  echo " [OK]"


  echo -n "vsync a bunch of empty files"
  touch $ABS_TEST_DIR/a.txt
  touch $ABS_TEST_DIR/b.txt
  touch $ABS_TEST_DIR/c.txt
  touch $ABS_TEST_DIR/d.txt
  $VSYNCCMD $CERT $ABS_TEST_DIR $CONTAINER || echo " [FAIL]" && exit -1
  $LSCMD $CERT $CONTAINER/$TEST_DIR/a.txt >& /dev/null || echo " [FAIL]" && exit -1
  $LSCMD $CERT $CONTAINER/$TEST_DIR/b.txt >& /dev/null || echo " [FAIL]" && exit -1
  $LSCMD $CERT $CONTAINER/$TEST_DIR/c.txt >& /dev/null || echo " [FAIL]" && exit -1
  $LSCMD $CERT $CONTAINER/$TEST_DIR/d.txt >& /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "vsync a changed file"
  echo "Test" > $ABS_TEST_DIR/a.txt
  $VSYNCCMD $CERT $ABS_TEST_DIR $CONTAINER || echo " [FAIL]" && exit -1
  $LSCMD -l $CERT $CONTAINER/$TEST_DIR/a.txt | awk '{if ($5 == 0){ print " [FAIL]"; exit -1}}'
  $LSCMD -l $CERT $CONTAINER/$TEST_DIR/b.txt | awk '{if ($5 != 0){ print " [FAIL]"; exit -1}}'
  $LSCMD -l $CERT $CONTAINER/$TEST_DIR/c.txt | awk '{if ($5 != 0){ print " [FAIL]"; exit -1}}'
  $LSCMD -l $CERT $CONTAINER/$TEST_DIR/d.txt | awk '{if ($5 != 0){ print " [FAIL]"; exit -1}}'
  echo " [OK]"
  echo
  echo "*** test sequence passed for resource $resource ***"
end

echo "*** ALL test sequence passed ***"
date
