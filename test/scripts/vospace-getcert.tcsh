#!/bin/tcsh -f

echo "###################"
if (! ${?CADC_ROOT} ) then
	set CADC_ROOT = "/usr/cadc/local"
endif
echo "using CADC_ROOT = $CADC_ROOT"

if (! ${?VOSPACE_WEBSERVICE} ) then
	echo "VOSPACE_WEBSERVICE env variable not set, use default WebService URL"
else
	echo "WebService URL (VOSPACE_WEBSERVICE env variable): $VOSPACE_WEBSERVICE"
endif

if (! ${?CADC_PYTHON_TEST_TARGETS} ) then
    set CADC_PYTHON_TEST_TARGETS = 'python2.6 python2.7'
endif
echo "Testing for targets $CADC_PYTHON_TEST_TARGETS. Set CADC_PYTHON_TEST_TARGETS to change this."
echo "###################"

set certFile = "/tmp/cert.pem"

foreach pythonVersion ($CADC_PYTHON_TEST_TARGETS)
    echo "*************** test with $pythonVersion ************************"

    set GETCERTCMD = "$pythonVersion $CADC_ROOT/scripts/getCert --daysValid=1 --cert-file=$certFile"
    set TESTCMD = "$pythonVersion $CADC_ROOT/scripts/vls --cert=$certFile vos:/CADCRegtest1/cadcVOFSTest_DONOTDELETE"

    echo

    rm $certFile >& /dev/null
    echo -n "** getcert"
    $GETCERTCMD || echo " [FAIL]" && exit -1
    if (! -f $certFile) then
        echo " [FAIL]" && exit -1
    endif
    $TESTCMD > /dev/null 
    set success = $?
    if ( $success != 0) then
        echo " [FAIL]" && exit -1
    endif

    echo " [OK]"
end

echo
echo "*** test sequence passed ***"

date
