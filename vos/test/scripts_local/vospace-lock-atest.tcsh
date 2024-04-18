#!/bin/tcsh -f

date
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

set LSCMD = "vls -l -k"
set MKDIRCMD = "vmkdir -k"
set RMCMD = "vrm -k"
set CPCMD = "vcp -k"

set MVCMD = "vmv -k"
set RMDIRCMD = "vrmdir -k"
set CHMODCMD = "vchmod -k"
set TAGCMD = "vtag -k"
set LNCMD = "vln -k"
set LOCKCMD = "vlock -k"

set SUCCESS = "false"
set LIST_ARGS = "ivo://cadc.nrc.ca/vospace/core#islocked"
set VLOCK_ARGS = "--lock"
set VUNLOCK_ARGS = "--unlock"
set VTAGCMD = "vtag -k"

echo "vls command: " $LSCMD $CERT
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

  set TEMPCONTAINER = $BASE/$TIMESTAMP"-temp"

  echo "** test container: ${CONTAINER}"
  echo
  echo "*** starting test sequence ***"
  echo

  echo "test case 1: "
  echo -n "create container "
  $MKDIRCMD $CERT $CONTAINER > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"
  echo -n "check unlocked container "
  $TAGCMD $CERT $CONTAINER $LIST_ARGS | grep -q true || set SUCCESS = "true"

  if ( ${SUCCESS} == "true" ) then
      set SUCCESS = "false"
      echo " [OK]"
  else
      echo " [FAIL]"
      exit -1
  endif

  echo

  echo "test case 2: "
  echo -n "lock container "
  if ( ${?TESTING_CAVERN} ) then
      echo " [SKIPPED, vos/issues/82]"
  else
      $LOCKCMD $CERT $CONTAINER $VLOCK_ARGS > /dev/null || echo " [FAIL]" && exit -1
      echo " [OK]"
      echo -n "check container is locked $CONTAINER"
      $TAGCMD $CERT $CONTAINER $LIST_ARGS | grep -q true || echo " [FAIL]" && exit -1
      echo " [OK]"
  endif

  echo

  echo "test case 3: "
  echo -n "unlock container "
  if ( ${?TESTING_CAVERN} ) then
      echo " [SKIPPED, vos/issues/82]"
  else
      $LOCKCMD $CERT $CONTAINER $VUNLOCK_ARGS > /dev/null || echo " [FAIL]" && exit -1
      echo " [OK]"
      echo -n "check unlocked container "
      $TAGCMD $CERT $CONTAINER $LIST_ARGS | grep -q None && set SUCCESS = "true"

      if ( ${SUCCESS} == "true" ) then
          set SUCCESS = "false"
          echo " [OK]"
      else
          echo " [FAIL]"
          exit -1
      endif
  endif

  echo

  echo "test case 4: "
  echo -n "create link "
  $CPCMD $CERT $THIS_DIR/something.png $CONTAINER/something.png > /dev/null || echo " [FAIL]" && exit -1
  $LNCMD $CERT $CONTAINER/something.png $CONTAINER/target > /dev/null || echo " [FAIL]" && exit -1
  $TAGCMD $CERT $CONTAINER/target $LIST_ARGS | grep -q true || set SUCCESS = "true"

  if ( ${SUCCESS} == "true" ) then
      set SUCCESS = "false"
      echo " [OK]"
  else
      echo " [FAIL]"
      exit -1
  endif
  echo

  echo "test case 5: "
  echo -n "lock link "
  if ( ${?TESTING_CAVERN} ) then
      echo " [SKIPPED, vos/issues/82]"
  else
      $LOCKCMD $CERT $CONTAINER/target $VLOCK_ARGS > /dev/null || echo " [FAIL]" && exit -1
      echo " [OK]"
      echo -n "check locked link "
      $TAGCMD $CERT $CONTAINER/target $LIST_ARGS | grep -q true || echo " [FAIL]" && exit -1
      echo " [OK]"
  endif
  echo

  echo "test case 6: "
  echo -n "unlock link "
  if ( ${?TESTING_CAVERN} ) then
      echo " [SKIPPED, vos/issues/82]"
  else
      $LOCKCMD $CERT $CONTAINER/target $VUNLOCK_ARGS> /dev/null || echo " [FAIL]" && exit -1
      echo " [OK]"
      echo -n "check unlocked link "
      $TAGCMD $CERT $CONTAINER/target $LIST_ARGS | grep -q None && set SUCCESS = "true"

      if ( ${SUCCESS} == "true" ) then
          set SUCCESS = "false"
          echo " [OK]"
      else
          echo " [FAIL]"
          exit -1
      endif
  endif
  echo

  echo "test case 7: "
  echo -n "check unlocked node "
  if ( ${?TESTING_CAVERN} ) then
      echo " [SKIPPED, vos/issues/82]"
  else
      $TAGCMD $CERT $CONTAINER/something.png $LIST_ARGS
      $TAGCMD $CERT $CONTAINER/something.png $LIST_ARGS | grep -q None && set SUCCESS = "true"
      if ( ${SUCCESS} == "true" ) then
          set SUCCESS = "false"
          echo " [OK]"
      else
          echo " [FAIL]"
          exit -1
      endif
  endif

  echo

  echo "test case 8: "
  echo -n "lock node "
  if ( ${?TESTING_CAVERN} ) then
      echo " [SKIPPED, vos/issues/82]"
  else
      $LOCKCMD $CERT $CONTAINER/something.png $VLOCK_ARGS > /dev/null || echo " [FAIL]" && exit -1
      echo " [OK]"
      echo -n "check locked node "
      $TAGCMD $CERT $CONTAINER/something.png $LIST_ARGS | grep -q true || echo " [FAIL]" && exit -1
      echo " [OK]"
  endif
  echo

  echo "test case 9: "
  echo -n "unlock node "
  if ( ${?TESTING_CAVERN} ) then
      echo " [SKIPPED, vos/issues/82]"
  else
      $LOCKCMD $CERT $CONTAINER/something.png $VUNLOCK_ARGS> /dev/null || echo " [FAIL]" && exit -1
      echo " [OK]"
      echo -n "check unlocked node "
      $TAGCMD $CERT $CONTAINER/something.png $LIST_ARGS | grep -q None && set SUCCESS = "true"
      if ( ${SUCCESS} == "true" ) then
          set SUCCESS = "false"
          echo " [OK]"
      else
          echo " [FAIL]"
          exit -1
      endif
  endif
  echo

  # clean up
  echo "test clean up "
  echo -n "delete local file "
  rm -f $THIS_DIR/something1.png || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "delete non-empty container "

  $RMCMD $CERT -R $CONTAINER >& /dev/null || echo " [FAIL]" && exit -1
  $TAGCMD $CERT $CONTAINER $LIST_ARGS >& /dev/null || set SUCCESS = "true"
  if ( ${SUCCESS} == "true" ) then
      set SUCCESS = "false"
      echo " [OK]"
  else
      echo " [FAIL]"
      exit -1
  endif
  echo
  echo "*** test sequence passed for resource $resource ***"
end

echo "*** ALL test sequences passed ***"
date
