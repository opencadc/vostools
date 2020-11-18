#!/bin/tcsh -f

set THIS_DIR = `dirname $0`
set THIS_DIR = `cd $THIS_DIR && pwd`

if (! ${?VOSPACE_WEBSERVICE} ) then
        setenv VOSPACE_WEBSERVICE 'www.cadc-ccda.hia-iha.nrc-cnrc.gc.ca'
	echo "VOSPACE_WEBSERVICE env variable not #set, use default WebService URL $VOSPACE_WEBSERVICE"
else
	echo "WebService URL (VOSPACE_WEBSERVICE env variable): $VOSPACE_WEBSERVICE"
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

set ACCESS_PAGE=https://ws-cadc.canfar.net/ac/login
echo "Using access page: $ACCESS_PAGE"

foreach resource ($resources)
    echo "************* TESTING AGAINST $resource ****************"

    # vault uses CADCRegtest1, cavern uses home/cadcregtest1
    echo $resource | grep "cavern" >& /dev/null
    if ( $status == 0) then
      echo "** using cavern, scoped token not supported, skipping test"
    else
        set VOROOT = "vos:"

      # Username / password for getting tokens
      echo "Enter credentials for a VOSpace account in which we will perform tests."
      echo -n "CADC Username: "
      set username = $<
      echo -n "Password: "
      stty -echo
      set password = $<
      echo
      stty echo


      set LSCMD = "vls -l"
      set MKDIRCMD = "vmkdir"
      set RMCMD = "vrm"
      set CPCMD = "vcp"
      set RMDIRCMD = "vrmdir"
      set CHMODCMD = "vchmod"
      echo

      set VOHOME = "$VOROOT$username"
      # token needs the full URI
      set VOHOME_TOKEN = "vos://cadc.nrc.ca~vault/$username"
      set BASE = "$VOHOME/atest"

      set TIMESTAMP=`date +%Y-%m-%dT%H-%M-%S`
      set CONTAINER = $BASE/$TIMESTAMP

      echo

      echo "Test CONTAINER: $CONTAINER"

      # Start with a token scoped to user's entire tree
      set TOKEN = "`curl -s -d username=$username -d password=$password ${ACCESS_PAGE}'?'scope=${VOHOME_TOKEN}`"

          echo -n "create containers"
          $MKDIRCMD --token "$TOKEN" -p $CONTAINER/A > /dev/null || echo " [FAIL]" && exit -1
          $MKDIRCMD --token "$TOKEN" $CONTAINER/B > /dev/null || echo " [FAIL]" && exit -1
          echo " [OK]"

          echo -n "set permissions"
          $CHMODCMD --token="$TOKEN" o+r $CONTAINER || echo " [FAIL]" && exit -1
          $CHMODCMD --token="$TOKEN" o+r $CONTAINER/A || echo " [FAIL]" && exit -1
          $CHMODCMD --token="$TOKEN" o+r $CONTAINER/B || echo " [FAIL]" && exit -1
          echo " [OK]"

      # Get a new token scoped only to the /B subdir
      set TOKEN = "`curl -s -d username=$username -d password=$password ${ACCESS_PAGE}'?'scope=${VOHOME_TOKEN}/atest/$TIMESTAMP/B`"

      echo -n "copy file to unscoped tree fails"
      $CPCMD --token="$TOKEN" $THIS_DIR/something.png $CONTAINER/A/ >& /dev/null
      if ( $status == 0 ) then
          echo " [FAIL]" && exit -1
      endif
      echo " [OK]"

      echo -n "copy a file to scoped tree - Fails because service does not return the proper securityMethod (AD Nov 17 2020. Also see https://github.com/opencadc/vos/issues/88)"
      $CPCMD --token="$TOKEN" $THIS_DIR/something.png $CONTAINER/B/ || echo " [FAIL]" && exit -1
      echo " [OK]"

      echo -n "check that the file got there"
      $LSCMD --token="$TOKEN" $CONTAINER/B | grep -q 'something.png' || echo " [FAIL]" && exit -1
      echo " [OK]"

      echo -n "create sub container with file in it"
      $MKDIRCMD --token="$TOKEN" $CONTAINER/B/test > /dev/null || echo " [FAIL]" && exit -1
      $CPCMD --token="$TOKEN" $THIS_DIR/something.png $CONTAINER/B/test/ || echo " [FAIL]" && exit -1
      $LSCMD --token="$TOKEN" $CONTAINER/B/test | grep -q 'something.png' || echo " [FAIL]" && exit -1
      echo " [OK]"

      echo -n "remove the file in the sub container"
      $RMCMD --token="$TOKEN" $CONTAINER/B/test/something.png || echo " [FAIL]" && exit -1
      echo " [OK]"

      echo -n "remove the sub container"
      $RMDIRCMD --token="$TOKEN" $CONTAINER/B/test || echo " [FAIL]" && exit -1
      $LSCMD --token="$TOKEN" $CONTAINER/B | grep -q test
      if ( $status == 0 ) then
          echo " [FAIL]" && exit -1
      endif
      echo " [OK]"

      echo
      echo "*** test sequence passed for resource $resource ***"
  endif
end

echo "*** ALL test sequences passed ***"

date
