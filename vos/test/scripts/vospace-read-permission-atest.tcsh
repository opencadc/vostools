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

echo

set LSCMD = "vls -l"
set MKDIRCMD = "vmkdir"
set RMCMD = "vrm"
set CPCMD = "vcp"
set RMDIRCMD = "vrmdir"
set MVCMD = "vmv"
set CHMODCMD = "vchmod"


set CERT =  "--cert=$CADC_TESTCERT_PATH/x509_CADCRegtest1.pem"
set CERT1 = "--cert=$CADC_TESTCERT_PATH/x509_CADCAuthtest1.pem"
set CERT2 = "--cert=$CADC_TESTCERT_PATH/x509_CADCAuthtest2.pem"


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
  $LSCMD $CERT $BASE > /dev/null
  if ( $status == 0) then
      echo " [OK]"
  else
      echo -n ", creating base URI"
          $MKDIRCMD $CERT $BASE || echo " [FAIL]" && exit -1
      echo " [OK]"
  endif

  echo -n "** setting home and base to public"
  $CHMODCMD $CERT o+r $VOHOME || echo " [FAIL]" && exit -1
  $CHMODCMD $CERT o+r $BASE || echo " [FAIL]" && exit -1
  echo " [OK]"
  echo

  echo "*** starting test sequence ***"
  echo
  echo "test container: " $CONTAINER
  echo

  echo -n "create container (no permissions) "
  $MKDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
  $CHMODCMD $CERT o-r $CONTAINER ||  echo " [FAIL]" && exit -1
  $CHMODCMD $CERT g-rw $CONTAINER ||  echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "copy file to existing container and non-existent data node "
  $CPCMD $CERT $THIS_DIR/something.png $CONTAINER/something.png || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "testing read as CADCAuthtest1 (denied) "
  $LSCMD $CERT1 $CONTAINER/something.png >& /dev/null && echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "set group-read of container and data node to $GROUP1 "
  $CHMODCMD $CERT g+r $CONTAINER $GROUP1 || echo " [FAIL set container]" && exit -1
  $CHMODCMD $CERT g+r $CONTAINER/something.png $GROUP1 || echo " [FAIL set data]" && exit -1
  $LSCMD $CERT $CONTAINER/something.png | grep "\-rw-r-----" | grep -q "$GROUP1" || echo " [FAIL check container]" && exit -1
  echo " [OK]"

  echo -n "testing read as CADCAuthtest1 vs $GROUP1 (allowed) "
  $LSCMD $CERT1 $CONTAINER/something.png > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "testing read as CADCAuthtest2 vs $GROUP1 (denied) "
  $LSCMD $CERT2 $CONTAINER/something.png >& /dev/null && echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "set group-read of container to $GROUP2 "
  $CHMODCMD $CERT g+r $CONTAINER $GROUP2 || echo " [FAIL set container]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $BASE | grep $TIMESTAMP | grep 'drw-r-----' | grep -q $GROUP2 || echo " [FAIL check container]" && exit -1
  echo -n " set group-read of data to $GROUP2 "
  $CHMODCMD $CERT g+r $CONTAINER/something.png $GROUP2 || echo " [FAIL set data]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/something.png | grep '\-rw-r-----' | grep -q $GROUP2 || echo " [FAIL check data]" && exit -1
  echo " [OK]"

  echo -n "testing read as CADCAuthtest1 vs $GROUP2 (allowed) "
  $LSCMD $CERT1 $CONTAINER/something.png > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "testing read as CADCAuthtest2 vs $GROUP2 (allowed) "
  $LSCMD $CERT2 $CONTAINER/something.png > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "delete test container as CADCAuthtest2 (denied) "
  $RMDIRCMD $CERT2 $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "delete test container (allowed)"
  $RMDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
  echo " [OK]"

  # start fresh

  echo -n "create container with --public "
  $MKDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
  $CHMODCMD $CERT o+r $CONTAINER || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK] "

  echo -n "copy file (inherit public)  "
  $CPCMD $CERT $THIS_DIR/something.png $CONTAINER/something.png || echo " [FAIL]" && exit -1
  echo -n " verify "
  if ( ${?TESTING_CAVERN} ) then
      echo " [SKIPPED, permissioin inheritance not supported] "
  else
      $LSCMD $CERT1 $CONTAINER/something.png | grep -q '\-rw----r--' || echo " [FAIL]" && exit -1
      echo " [OK] "
  endif

  echo -n "cleanup"
  $RMDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
  echo " [OK] "

  # start fresh

  echo -n "create container "
  $MKDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
  $CHMODCMD $CERT o+r $CONTAINER || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $BASE | grep $TIMESTAMP | grep -q 'drw----r--' || echo " [FAIL]" && exit -1
  echo " [OK] "

  echo -n "copy file with --public "
  $CPCMD $CERT $THIS_DIR/something.png $CONTAINER/something.png || echo " [FAIL]" && exit -1
  $CHMODCMD $CERT o+r $CONTAINER/something.png || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/something.png | grep -q '\-rw----r--' || echo " [FAIL]" && exit -1
  echo " [OK] "

  echo -n "cleanup"
  $RMDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
  echo " [OK] "

  # starting fresh

  echo -n "create container with --group-read=${GROUP1} "
  $MKDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
  $CHMODCMD $CERT o-r $CONTAINER || echo " [FAIL]" && exit -1
  $CHMODCMD $CERT g+r $CONTAINER $GROUP1 || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $BASE | grep $TIMESTAMP > /dev/null || grep -q 'drw-r-----' || echo " [FAIL]" && exit -1
  echo " [OK] "

  echo -n "copy file (inherit group-read)  "
  $CPCMD $CERT $THIS_DIR/something.png $CONTAINER/something.png || echo " [FAIL]" && exit -1
  echo -n " verify "
  if ( ${?TESTING_CAVERN} ) then
      echo " [SKIPPED, permissioin inheritance not supported] "
  else
      $LSCMD $CERT1 $CONTAINER/something.png | grep '\-rw-r-----' | grep -q $GROUP1 || echo " [FAIL]" && exit -1
      echo " [OK] "
  endif

  echo -n "cleanup"
  $RMDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
  echo " [OK] "

  # start fresh

  echo -n "create container "
  $MKDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
  $CHMODCMD $CERT o-r $CONTAINER || echo " [FAIL CHMOD]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $BASE | grep $TIMESTAMP | grep -q 'drw-------' || echo " [FAIL]" && exit -1
  echo " [OK] "

  echo -n "copy file with --group-read "
  $CPCMD $CERT $THIS_DIR/something.png $CONTAINER/something.png || echo " [FAIL]" && exit -1
  $CHMODCMD $CERT g+r $CONTAINER/something.png $GROUP1 || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/something.png | grep '\-rw-r-----' | grep -q $GROUP1 || echo " [FAIL]" && exit -1
  echo " [OK] "

  echo -n "cleanup"
  $RMDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
  echo " [OK] "

  # fresh start

  echo -n "create container with --public=false "
  $MKDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
  $CHMODCMD $CERT o-r $CONTAINER || echo " [FAIL CHMOD]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $BASE | grep $TIMESTAMP | grep -q 'drw-------' || echo " [FAIL]" && exit -1
  echo " [OK] "

  echo -n "try to set --public as CADCAuthtest1 (denied) "
  $CHMODCMD $CERT1 o+r $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $BASE | grep $TIMESTAMP | grep -q 'drw-------' || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "cleanup"
  $RMDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
  echo " [OK] "
  echo
  echo "*** test sequence passed for resource $resource ***"
end

echo "*** ALL test sequences passed ***"

date
