#!/bin/tcsh -f

set THIS_DIR = `dirname $0`
set THIS_DIR = `cd $THIS_DIR && pwd`

if ( ${?LOCAL_VOSPACE_WEBSERVICE} ) then
	echo "LOCAL_VOSPACE_WEBSERVICE env variable for local tests must be unset"
	exit -1
endif

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

if($#argv == 0) then
    set resources = "vault"
    echo "Testing against default resources: $resources"
else
    set resources = ($argv)
    echo "Testing against resources: $resources"
endif

echo

set LSCMD = "vls"
set CPCMD = "vcp"
set MKDIRCMD = "vmkdir"
set RMCMD = "vrm"

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

    set VOHOME = "$VOROOT""$HOME_BASE"

    set FITS_FILE_NAME = "test-alma-cube.fits"
    set FITS_FILE = $VOHOME"/"$FITS_FILE_NAME
    echo "FITS file - $FITS_FILE"

    echo -n "** check test FITS file exists"
    $LSCMD $CERT $FITS_FILE >& /dev/null || echo " [FAIL]" && exit -1
    if ( $status == 0) then
        echo " [OK]"
    else
        echo " [Missing]"
        exit -1
    endif

    set DEST = "/tmp/cutout_test.fits"

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
