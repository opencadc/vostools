#!/bin/tcsh -f

set THIS_DIR = `dirname $0`
set THIS_DIR = `cd $THIS_DIR && pwd`

if (! ${?VOSPACE_WEBSERVICE} ) then
	echo "VOSPACE_WEBSERVICE env variable not set, use default WebService URL"
else
	echo "WebService URL (VOSPACE_WEBSERVICE env variable): $VOSPACE_WEBSERVICE"
endif

if (! ${?CADC_TESTCERT_PATH} ) then
	echo "CADC_TESTCERT_PATH env variable not set. Must point to the location of x509_CADCAuthtest2.pem cert file"
    exit -1
else
    set CERTFILE = "$CADC_TESTCERT_PATH/x509_CADCAuthtest1.pem"
	echo "cert file:  ($CADC_TESTCERT_PATH env variable): $CERTFILE"
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

set MKDIRCMD = "vmkdir"
set CPCMD = "vcp"
set LSCMD = "vls"
set RMDIRCMD = "vrmdir"
set CERT = "--cert=$CERTFILE"

echo

foreach resource ($resources)
    echo "************* TESTING AGAINST $resource ****************"

    # vault uses CADCRegtest1, cavern uses home/cadcregtest1
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
  $LSCMD -v $CERT $BASE > /dev/null
  if ( $status == 0) then
      echo " [OK]"
  else
      echo -n ", creating base URI"
          $MKDIRCMD $CERT $BASE >& || echo " [FAIL]" && exit -1
      echo " [OK]"
  endif

  echo "*** starting test sequence for $CONTAINER ***"

  echo -n "Create container"
  $MKDIRCMD $CERT $CONTAINER || (echo " [FAIL]" ; exit -1)
  echo " [OK]"

  echo -n "Upload data node in excess of quota (expect error)"
  $CPCMD $CERT $THIS_DIR/something.png $CONTAINER/testdata.png | grep -qi "quota" && echo " [FAIL]" && exit -1
  echo " [OK]"


  echo -n "delete container "
  $RMDIRCMD $CERT $CONTAINER >& /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"
  echo
  echo "*** test sequence passed for resource $resource ***"
end

echo "*** ALL test sequences passed ***"

date
