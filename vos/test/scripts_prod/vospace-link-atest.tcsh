#!/bin/tcsh -f

set THIS_DIR = `dirname $0`
set THIS_DIR = `cd $THIS_DIR && pwd`

if ( ${?LOCAL_VOSPACE_WEBSERVICE} ) then
	echo "LOCAL_VOSPACE_WEBSERVICE env variable for local tests must be unset"
	exit -1
endif

if (! ${?CADC_TESTCERT_PATH} ) then
	echo "CADC_TESTCERT_PATH env variable not set. Must point to the location of test cert files"
    exit -1
else
	echo "cert files path:  ($CADC_TESTCERT_PATH env variable): $CADC_TESTCERT_PATH"
endif

if($#argv == 0) then
    set resources = "vault cavern"
    echo "Testing against default resources: $resources"
else
    set resources = ($argv)
    echo "Testing against resources: $resources"
endif

date

set LSCMD = "vls"
set MKDIRCMD = "vmkdir"
set RMCMD = "vrm"
set CPCMD = "vcp"
set RMDIRCMD = "vrmdir"
set LNCMD = "vln"
set CHMODCMD = "vchmod"

set CERT = " --cert=$CADC_TESTCERT_PATH/x509_CADCAuthtest1.pem"
set CERT2 = " --cert=$CADC_TESTCERT_PATH/x509_CADCAuthtest2.pem"

echo "command: " $LNCMD $CERT
echo

foreach resource ($resources)
    echo "************* TESTING AGAINST $resource ****************"

    echo $resource | grep "cavern" >& /dev/null
    if ( $status == 0) then
        set HOME_BASE = "home/cadcauthtest1"
        set VOROOT = "arc:"
        set TESTING_CAVERN = "true"
    else
        set VOROOT = "vos:"
        set HOME_BASE = "CADCAuthtest1"
    endif

  set VOHOME = "$VOROOT""$HOME_BASE"
  set BASE = "$VOHOME/atest"

  set TIMESTAMP=`date +%Y-%m-%dT%H-%M-%S`
  set CONTAINER = $BASE/$TIMESTAMP

  echo -n "** checking base URI"
  $LSCMD -v $CERT $BASE >& /dev/null
  if ( $status == 0) then
      echo " [OK]"
  else
      echo -n ", creating base URI"
          $MKDIRCMD $CERT $BASE >& /dev/null || echo " [FAIL]" && exit -1
      echo " [OK]"
  endif
  echo -n "** setting home and base to public, no groups"
  $CHMODCMD $CERT o+r $VOHOME || echo " [FAIL]" && exit -1
  $CHMODCMD $CERT o+r $BASE || echo " [FAIL]" && exit -1
  echo " [OK]"


  echo "*** starting test sequence ***"
  echo

  echo -n "create base container"
  $MKDIRCMD $CERT $CONTAINER > /dev/null || echo " [FAIL]" && exit -1
  $CHMODCMD $CERT o-r $CONTAINER > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "create container to be valid link target"
  $MKDIRCMD $CERT $CONTAINER/target > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "copy file to target container"
  $CPCMD $CERT $THIS_DIR/something.png $CONTAINER/target/something.png || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "create link to target container"
  $LNCMD $CERT $CONTAINER/target $CONTAINER/clink >& /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "list container"
  # content displayed unless -l and no / at the end
  # content displayed
  $LSCMD $CERT $CONTAINER/clink | grep -q something || echo " [FAIL]" && exit -1
  $LSCMD $CERT $CONTAINER/clink/ | grep -q something || echo " [FAIL]" && exit -1
  $LSCMD $CERT -l $CONTAINER/clink/ | grep -q something || echo " [FAIL]" && exit -1
  # case where the content of the target is not displayed just the link
  $LSCMD $CERT -l $CONTAINER/clink | grep 'clink ->' | grep -q target || echo " [FAIL]" && exit -1
  echo " [OK]"


  echo -n "follow the link to get the file"
  $CPCMD $CERT $CONTAINER/clink/something.png /tmp || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "follow the link without read permission and fail"
  $CPCMD $CERT2 $CONTAINER/clink/something.png /tmp >& /dev/null && echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "create link to target file"
  $LNCMD $CERT $CONTAINER/target/something.png $CONTAINER/dlink >& /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "follow the link to get the file"
  $CPCMD $CERT $CONTAINER/dlink /tmp || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "create link to external vos URI"
  $RMCMD $CERT $CONTAINER/e1link >& /dev/null
  $LNCMD $CERT vos://cadc.nrc.ca~arc/unknown $CONTAINER/e1link >& /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "follow the invalid link and fail"
  $CPCMD $CERT $CONTAINER/e1link/somefile /tmp >& /dev/null && echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "create link to unknown scheme in URI"
  #TODO not sure why this is not working anymore
  #$LNCMD $CERT unknown://cadc.nrc.ca~vault/CADCRegtest1 $CONTAINER/e2link >& /dev/null && echo " [FAIL]" && exit -1
  echo " [SKIPPED - TODO]"

  echo -n "follow the invalid link and fail"
  $CPCMD $CERT $CONTAINER/e2link/somefile /tmp  >& /dev/null && echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "create link to external http URI"
  if ( ${?TESTING_CAVERN} ) then
      echo " [SKIPPED, vos/issues/83]"
  else
      $LNCMD $CERT https://www.google.ca $CONTAINER/e3link > /dev/null || echo " [FAIL]" && exit -1
      echo " [OK]"
  endif

  echo -n "Follow the invalid link and fail"
  $CPCMD $CERT $CONTAINER/e3link/somefile /tmp  >& /dev/null && echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "copy file to target through link"
  $CPCMD  $CERT $THIS_DIR/something.png $CONTAINER/clink/something2.png || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "Get the file through the link"
  $CPCMD  $CERT $CONTAINER/clink/something2.png /tmp || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "Get the file through the target"
  $CPCMD   $CERT $CONTAINER/target/something2.png /tmp || echo " [FAIL]" && exit -1
  echo " [OK]"
  echo
  echo "*** test sequence passed for resource $resource ***"
end

echo "*** DONE test sequence passed ***"
date
