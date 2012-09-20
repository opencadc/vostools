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
echo "###################"
echo "THIS TEST TO BE IMPLEMENTED AFTER VCHMOD BECOMES AVAILABLE"
exit
set CMD = "java ${LOCAL} -jar ${CADC_ROOT}/lib/cadcVOSClient.jar "
set CERT =  "--cert=$A/test-certificates/x509_CADCRegtest1.crt  --key=$A/test-certificates/x509_CADCRegtest1.key"
set CERT1 = "--cert=$A/test-certificates/x509_CADCAuthtest1.crt --key=$A/test-certificates/x509_CADCAuthtest1.key"
set CERT4 = "--cert=$A/test-certificates/x509_CADCAuthtest2.crt --key=$A/test-certificates/x509_CADCAuthtest2.key"

set CRED_CLIENT="java ${LOCAL} -jar ${CADC_ROOT}/lib/cred_wsPubClient.jar"

echo "create/set commands: " $CMD $CERT
echo "delete command 1:    " $CMD $CERT1
echo "delete command 2:    " $CMD $CERT2
echo "cred client:         " $CRED_CLIENT

# group 3000 aka CADC_TEST1-Staff has members: CADCAuthtest1
set GROUP1 = "ivo://cadc.nrc.ca/gms#CADC_TEST1-Staff"

# group 3100 aka CADC_TEST2-Staff has members: CADCAuthtest1, CADCAuthtest2
set GROUP2 = "ivo://cadc.nrc.ca/gms#CADC_TEST2-Staff"

# using a test dir makes it easier to cleanup a bunch of old/failed tests
set VOHOME = "vos://cadc.nrc.ca\!vospace/CADCRegtest1"
set BASE = "$VOHOME/atest"

set TIMESTAMP=`date +%Y-%m-%dT%H-%M-%S`
set CONTAINER = $BASE/$TIMESTAMP

echo -n "** checking base URI"
$CMD -v $CERT --view --target=$BASE > /dev/null
if ( $status == 0) then
    echo " [OK]"
else
    echo -n ", creating base URI"
        $CMD $CERT --create --target=$BASE || echo " [FAIL]" && exit -1
    echo " [OK]"
endif

echo -n "** delegating proxy certificates in CDP "
$CRED_CLIENT --delegate --daysvalid=1 --cert=$A/test-certificates/x509_CADCAuthtest1.pem | grep -qi 'certificate updated' || echo " [FAIL]" && exit -1 
$CRED_CLIENT --delegate --daysvalid=1 --cert=$A/test-certificates/x509_CADCAuthtest2.pem | grep -qi 'certificate updated' || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "** setting home and base to public"
$CMD $CERT --set --public --target=$VOHOME || echo " [FAIL]" && exit -1
$CMD $CERT --set --public --target=$BASE || echo " [FAIL]" && exit -1
echo " [OK]"
echo

echo "*** starting test sequence ***"
echo
echo "** test container: ${CONTAINER}"
echo

echo -n "create container "
$CMD $CERT --create --target=$CONTAINER --public=false --group-read="" --group-write=$GROUP2 ||  echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "create container/sub1 (inherit permissions) "
$CMD $CERT --create --target=$CONTAINER/sub1 || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/sub1 > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "create container/sub1/sub2 (group-write=$GROUP1) "
$CMD $CERT --create --target=$CONTAINER/sub1/sub2  --group-write=$GROUP1 || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/sub1/sub2 > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "put data node in sub2 "
$CMD $CERT --copy --src=something.png --dest=$CONTAINER/sub1/sub2/something.png || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/sub1/sub2/something.png > /dev/null || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/sub1/sub2/something.png | grep 'writable by: ' | grep -q $GROUP1 || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "test delete container/sub1 (denied)"
$CMD $CERT2 --delete --target=$CONTAINER/sub1/sub2 > /dev/null && echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/sub1/sub2/something.png > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "test delete container/sub1 (allowed)"
$CMD $CERT1 --delete --target=$CONTAINER/sub1 || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/sub1 > /dev/null && echo " [FAIL]" && exit -1
echo " [OK]"

# start fresh

echo -n "create container/sub1 "
$CMD $CERT --create --target=$CONTAINER/sub1 --public=false --group-read="" --group-write="$GROUP1" || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "put data node in container/sub1 (allowed) "
$CMD $CERT1 --copy --src=something.png --dest=$CONTAINER/sub1/something.png || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT1 --view --target=$CONTAINER/sub1/something.png > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "test root read "
$CMD $CERT --view --target=$CONTAINER/sub1 > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "test root delete"
$CMD $CERT --delete --target=$CONTAINER/sub1 || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT1 --view --target=$CONTAINER/sub1 > /dev/null && echo " [FAIL]" && exit -1
echo " [OK]"

echo
echo "*** test sequence passed ***"

date
