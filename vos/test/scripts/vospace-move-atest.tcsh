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


set LSCMD = "vls"
set MKDIRCMD = "vmkdir"
set RMCMD = "vrm"
set CPCMD = "vcp"
set RMDIRCMD = "vrmdir"
set MVCMD = "vmv"
set CHMODCMD = "vchmod"


set CERT = "--cert=$CADC_TESTCERT_PATH/x509_CADCRegtest1.pem"
set CERT1 = "--cert=$CADC_TESTCERT_PATH/x509_CADCAuthtest1.pem"
set CERT2 = "--cert=$CADC_TESTCERT_PATH/x509_CADCAuthtest2.pem"

# group 3000 aka CADC_TEST_GROUP1 has members: CADCAuthtest1
set GROUP1 = "ivo://cadc.nrc.ca/gms#CADC_TEST_GROUP1"

# group 3100 aka CADC_TEST_GROUP2 has members: CADCAuthtest1, CADCAuthtest2
set GROUP2 = "ivo://cadc.nrc.ca/gms#CADC_TEST_GROUP2"

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

  set VOHOME = "$VOROOT$HOME_BASE"
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
  echo "** test container: ${CONTAINER}"
  echo

  echo -n "setup: create container "
  $MKDIRCMD $CERT $CONTAINER ||  echo " [FAIL]" && exit -1
  $CHMODCMD $CERT o-r $CONTAINER ||  echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "setup: create container/a read-write to group 2"
  $MKDIRCMD $CERT $CONTAINER/a || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/a > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "setup: create container/a/aa read-write to group 1"
  $MKDIRCMD $CERT $CONTAINER/a/aa || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/a/aa > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "setup: upload data node aaa to container/a/aa "
  $CPCMD $CERT $THIS_DIR/something.png $CONTAINER/a/aa/aaa || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/a/aa/aaa > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "setup: upload data node b to container "
  $CPCMD $CERT $THIS_DIR/something.png $CONTAINER/b || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/b > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "setup: upload data node c to container read-write to group 2"
  $MKDIRCMD $CERT $CONTAINER/c || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/c > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "setup: create container/d read-write to group 2"
  $MKDIRCMD $CERT $CONTAINER/d || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/d > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "setup: create container/d read-write to group 1"
  $MKDIRCMD $CERT $CONTAINER/e || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/e > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "check no write permission on source data node (fail)"
  $MVCMD $CERT2 $CONTAINER/b $CONTAINER/d >& /dev/null && echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/b > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "check no recursive write permission on source container node (fail)"
  $MVCMD $CERT2 $CONTAINER/a $CONTAINER/d >& /dev/null && echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/a > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "check no write permission on dest (fail)"
  $MVCMD $CERT2 $CONTAINER/c $CONTAINER/e >& /dev/null && echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/c > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "test dest a data node (fail)"
  $MVCMD $CERT $CONTAINER/a $CONTAINER/b >& /dev/null && echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/a > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "test circular move (fail)"
  $MVCMD $CERT $CONTAINER/a $CONTAINER/a/aa >& /dev/null && echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/a > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "test root move (fail)"
  $MVCMD $CERT $BASE $CONTAINER/d >& /dev/null && echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $BASE > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "test move container into container (pass)"
  $RMDIRCMD $CERT $CONTAINER/d/a >& /dev/null
  $MVCMD $CERT $CONTAINER/a $CONTAINER/d >& /dev/null || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/d/a > /dev/null || echo " [FAIL]" && exit -1
  $LSCMD $CERT $CONTAINER/d/a/aa > /dev/null || echo " [FAIL]" && exit -1
  $LSCMD $CERT $CONTAINER/d/a/aa/aaa > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "setup: move container back"
  $MVCMD $CERT $CONTAINER/d/a $CONTAINER >& /dev/null || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/a > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "test move container with new name (pass)"
  $MVCMD $CERT $CONTAINER/a $CONTAINER/d/x >& /dev/null || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/d/x > /dev/null || echo " [FAIL]" && exit -1
  $LSCMD $CERT $CONTAINER/d/x/aa > /dev/null || echo " [FAIL]" && exit -1
  $LSCMD $CERT $CONTAINER/d/x/aa/aaa > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "setup: move container back"
  $MVCMD $CERT $CONTAINER/d/x $CONTAINER/a >& /dev/null || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/a > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "test move file into container (pass)"
  $MVCMD $CERT $CONTAINER/a/aa/aaa $CONTAINER/d >& /dev/null || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/d/aaa > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "test move file with new name"
  $MVCMD $CERT $CONTAINER/d/aaa $CONTAINER/a/aa/bbb >& /dev/null || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/a/aa/bbb > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "test rename file"
  $MVCMD $CERT $CONTAINER/a/aa/bbb $CONTAINER/a/aa/aaa >& /dev/null || echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/a/aa/aaa > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "rename file when destination exists (fail)"
  $CPCMD $CERT $THIS_DIR/something.png $CONTAINER/a/aa/bbb || echo " [FAIL]" && exit -1
  $MVCMD $CERT $CONTAINER/a/aa/aaa $CONTAINER/a/aa/bbb >& /dev/null && echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "move a vos container to local file system (fail)"
  $MVCMD $CERT $CONTAINER/a notused.txt >& /dev/null && echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/a > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  #TODO not supported in vmv
  echo -n "move a vos data node to local file system (pass)"
  #echo "$MVCMD $CERT $CONTAINER/a/aa/aaa something2.png > /dev/null"
  #$MVCMD $CERT $CONTAINER/a/aa/aaa something2.png > /dev/null || echo " [FAIL]" && exit -1
  echo -n " verify "
  #$LSCMD $CERT $CONTAINER/a/aa/aaa > /dev/null && echo " [FAIL]" && exit -1
  echo " [TODO]"

  echo -n "move a local directory to vos (fail)"
  mkdir testdir
  $MVCMD $CERT testdir $CONTAINER/a >& /dev/null && echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $CONTAINER/a/testdir >& /dev/null && echo " [FAIL]" && exit -1
  rmdir testdir
  echo " [OK]"

  #TODO not supported in vmv
  echo -n "move a file to vos (success)"
  #$MVCMD $CERT something2.png $CONTAINER/a/aa/aaa> /dev/null || echo " [FAIL]" && exit -1
  echo -n " verify "
  #$LSCMD $CERT $CONTAINER/a/aa/aaa > /dev/null || echo " [FAIL]" && exit -1
  echo " [TODO]"

  echo -n "do a local file system move (fail--unsupported)"
  cp -f $THIS_DIR/something.png $THIS_DIR/something2.png
  $MVCMD $CERT $THIS_DIR/something2.png $THIS_DIR/something3.png >& /dev/null && echo " [FAIL]" && exit -1
  rm $THIS_DIR/something2.png
  echo " [OK]"
  echo
  echo "*** test sequence passed for resource $resource ***"
end

echo "*** ALL test sequences passed ***"
date
