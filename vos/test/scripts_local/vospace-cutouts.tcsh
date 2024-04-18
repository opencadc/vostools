#!/bin/tcsh -f

date
echo "###################"
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
endif

if (! ${?TMPDIR} ) then
        echo "TMPDIR env variable not set, using /tmp"
        set TMPDIR = "/tmp"
else
        echo "Using ${TMPDIR} for temporary files"
endif

set LSCMD = "vls -k"
set CPCMD = "vcp -k"
set MKDIRCMD = "vmkdir -k"
set RMCMD = "vrm -k"
set CHMODCMD = "vchmod -k"
set VTAGCMD = "vtag -k"

if($#argv == 0) then
    set resources = "vault"
    echo "Testing against default resources: $resources"
else
    set resources = ($argv)
    echo "Testing against resources: $resources"
endif

foreach resource ($resources)
    echo "************* TESTING AGAINST $resource ****************"

    echo $resource | grep "cavern" >& /dev/null
    if ( $status == 0) then
        echo "cavern does not support cutouts at this point"
        exit
    else
        set VOROOT = "vos:"
        set HOME_BASE = "CADCAuthtest1"
    endif

    set HOME_BASE = "vostools-inttest"
    set VOHOME = "$VOROOT""$HOME_BASE"
    set BASE = $VOHOME

    set FITS_FILE_NAME = "test-alma-cube.fits"
    set FITS_FILE = $VOHOME"/"$FITS_FILE_NAME
    echo "FITS file - $FITS_FILE"

    echo -n "** creating base URI"
    $RMCMD -R $BASE > /dev/null
    $MKDIRCMD $BASE || echo " [FAIL]" && exit -1
    $VTAGCMD $BASE 'ivo://cadc.nrc.ca/vospace/core#inheritPermissions=true'
    $CHMODCMD o+w $BASE
    echo " [OK]"

    set DEST = "/tmp/cutout_test.fits"

    echo -n "** download test FITS"
    # test file is downloaded from the production vault
    set url = "https://ws-cadc.canfar.net/vault/files/CADCAuthtest1/$FITS_FILE_NAME"
    curl -L -s -o $DEST $url || echo " [FAIL]" && exit -1
    if ( $status == 0) then
        ls -l $DEST | grep -q 54414720 || echo " [FAIL]" && exit -1
        echo " [OK]"
    else
        echo " [Missing]"
        echo " "
        exit -1
    endif

    echo -n "** check test FITS file exists"
    $CPCMD $CERT $DEST $FITS_FILE >& /dev/null || echo " [FAIL]" && exit -1
    $LSCMD $CERT $FITS_FILE >& /dev/null || echo " [FAIL]" && exit -1
    if ( $status == 0) then
        echo " [OK]"
    else
        echo " [Missing]"
        exit -1
    endif

    echo -n "Pixel cutouts "
    rm $DEST >& /dev/null
    $CPCMD $CERT $FITS_FILE"[0][1:100]" $DEST
    ls -l $DEST | grep -q 18201600 || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "Coords cutouts "
    rm $DEST >& /dev/null
    $CPCMD $CERT $FITS_FILE"(246.52,-24.33,0.01)" $DEST
    ls -l $DEST | grep -q 11871360 || echo " [FAIL]" && exit -1
    echo " [OK]"

    echo -n "Head op "
    rm $DEST >& /dev/null
    $CPCMD $CERT --head $FITS_FILE $DEST
    ls -l $DEST | grep -q 53865 || echo " [FAIL]" && exit -1
    echo " [OK]"

    rm $DEST >& /dev/null
end

echo "*** ALL test sequence passed ***"
date
