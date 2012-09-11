#!/bin/tcsh -f

if ( ${?CADC_ROOT} ) then
	echo "using CADC_ROOT = $CADC_ROOT"
else
	set CADC_ROOT = "/usr/cadc/local"
	echo "using CADC_ROOT = $CADC_ROOT"
endif
set TRUST = "-Dca.nrc.cadc.auth.BasicX509TrustManager.trust=true"
set LOCAL = "-Dca.nrc.cadc.reg.client.RegistryClient.host=www4.cadc-ccda.hia-iha.nrc-cnrc.gc.ca"
if ( ${?1} ) then
        if ( $1 == "localhost" ) then
                set LOCAL = "-Dca.nrc.cadc.reg.client.RegistryClient.local=true $TRUST"
        else if ( $1 == "devtest" ) then
                set LOCAL = "-Dca.nrc.cadc.reg.client.RegistryClient.host=devtest.cadc-ccda.hia-iha.nrc-cnrc.gc.ca $TRUST"
        else if ( $1 == "test" ) then
                set LOCAL = "-Dca.nrc.cadc.reg.client.RegistryClient.host=test.cadc-ccda.hia-iha.nrc-cnrc.gc.ca"
        else if ( $1 == "rc" ) then
                set LOCAL = "-Dca.nrc.cadc.reg.client.RegistryClient.host=rc.cadc-ccda.hia-iha.nrc-cnrc.gc.ca $TRUST"
        else if ( $1 == "rcdev" ) then
                set LOCAL = "-Dca.nrc.cadc.reg.client.RegistryClient.host=rcdev.cadc-ccda.hia-iha.nrc-cnrc.gc.ca $TRUST"
        endif
endif

set CMD = "java ${LOCAL} -jar ${CADC_ROOT}/lib/cadcVOSClient.jar"
set CERT = "--cert=$A/test-certificates/x509_CADCRegtest1.crt --key=$A/test-certificates/x509_CADCRegtest1.key"

set CMD = "java ${LOCAL} -jar ${CADC_ROOT}/lib/cadcVOSClient.jar"
set CERT =  "--cert=$A/test-certificates/x509_CADCRegtest1.crt  --key=$A/test-certificates/x509_CADCRegtest1.key"
set CERT1 = "--cert=$A/test-certificates/x509_CADCAuthtest1.crt --key=$A/test-certificates/x509_CADCAuthtest1.key"
set CERT2 = "--cert=$A/test-certificates/x509_CADCAuthtest2.crt --key=$A/test-certificates/x509_CADCAuthtest2.key"

set CRED_CLIENT="java ${LOCAL} -jar ${CADC_ROOT}/lib/cred_wsPubClient.jar"

echo "create/set commands: " $CMD $CERT
echo "read command 1: " $CMD $CERT1
echo "read command 2: " $CMD $CERT2
echo "cred client:    " $CRED_CLIENT

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

echo -n "** setting home and base to public"
$CMD $CERT --set --public --target=$VOHOME || echo " [FAIL]" && exit -1
$CMD $CERT --set --public --target=$BASE || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "** delegating proxy certificates in CDP "
$CRED_CLIENT --delegate --daysvalid=1 --cert=$A/test-certificates/x509_CADCAuthtest1.pem | grep -qi 'certificate updated' || echo " [FAIL]" && exit -1 
$CRED_CLIENT --delegate --daysvalid=1 --cert=$A/test-certificates/x509_CADCAuthtest2.pem | grep -qi 'certificate updated' || echo " [FAIL]" && exit -1
echo " [OK]"
echo

echo "*** starting test sequence ***"
echo
echo "test container: " $CONTAINER
echo

echo -n "create container (no permissions) "
$CMD $CERT --create --target=$CONTAINER --public=false --group-read="" --group-write="" || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "copy file to existing container and non-existent data node "
$CMD $CERT --copy --src=something.png --dest=$CONTAINER/something.png || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "testing read as CADCAuthtest1 (denied) "
$CMD $CERT1 --view --target=$CONTAINER/something.png > /dev/null && echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "set group-read of container and data node to $GROUP1 "
$CMD $CERT --set --target=$CONTAINER --group-read=$GROUP1 || echo " [FAIL set container]" && exit -1
$CMD $CERT --view --target=$CONTAINER | grep 'readable by:' | grep -q $GROUP1 || echo " [FAIL check container]" && exit -1
$CMD $CERT --set --target=$CONTAINER/something.png --group-read=$GROUP1 || echo " [FAIL set data]" && exit -1
$CMD $CERT --view --target=$CONTAINER/something.png | grep 'readable by:' | grep -q $GROUP1 || echo " [FAIL check data]" && exit -1
echo " [OK]"

echo -n "testing read as CADCAuthtest1 vs $GROUP1 (allowed) "
$CMD $CERT1 --view --target=$CONTAINER/something.png > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "testing read as CADCAuthtest2 vs $GROUP1 (denied) "
$CMD $CERT2 --view --target=$CONTAINER/something.png > /dev/null && echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "set group-read of container to $GROUP2 "
$CMD $CERT --set --target=$CONTAINER --group-read=$GROUP2 || echo " [FAIL set container]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER | grep 'readable by:' | grep -q $GROUP2 || echo " [FAIL check container]" && exit -1
echo -n " set group-read of data to $GROUP2 "
$CMD $CERT --set --target=$CONTAINER/something.png --group-read=$GROUP2 || echo " [FAIL set data]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/something.png | grep 'readable by:' | grep -q $GROUP2 || echo " [FAIL check data]" && exit -1
echo " [OK]"

echo -n "testing read as CADCAuthtest1 vs $GROUP2 (allowed) "
$CMD $CERT1 --view --target=$CONTAINER/something.png > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "testing read as CADCAuthtest2 vs $GROUP2 (allowed) "
$CMD $CERT2 --view --target=$CONTAINER/something.png > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "delete test container as CADCAuthtest2 (denied)"
$CMD $CERT2 --delete --target=$CONTAINER > /dev/null && echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "delete test container (allowed)"
$CMD $CERT --delete --target=$CONTAINER || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER > /dev/null && echo " [FAIL]" && exit -1
echo " [OK]"

# start fresh

echo -n "create container with --public "
$CMD $CERT --create --target=$CONTAINER --public=--group-write="" --group-read="" || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER > /dev/null || echo " [FAIL]" && exit -1
echo -n " [OK] "

echo -n "copy file (inherit public)  "
$CMD $CERT --copy --src=something.png --dest=$CONTAINER/something.png || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT1 --view --target=$CONTAINER/something.png | grep -q 'readable by anyone: true' || echo " [FAIL]" && exit -1
echo -n " [OK] "

echo -n "cleanup"
$CMD $CERT --delete --target=$CONTAINER || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER > /dev/null && echo " [FAIL]" && exit -1
echo " [OK] "

# start fresh

echo -n "create container "
$CMD $CERT --create --target=$CONTAINER --public=false --group-write="" --group-read="" || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER | grep -q 'readable by anyone: false' || echo " [FAIL]" && exit -1
echo -n " [OK] "

echo -n "copy file with --public "
$CMD $CERT --copy --src=something.png --dest=$CONTAINER/something.png --public || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/something.png | grep -q 'readable by anyone: true' || echo " [FAIL]" && exit -1
echo -n " [OK] "

echo -n "cleanup"
$CMD $CERT --delete --target=$CONTAINER || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER > /dev/null && echo " [FAIL]" && exit -1
echo " [OK] "

# starting fresh

echo -n "create container with --group-read=${GROUP1} "
$CMD $CERT --create --target=$CONTAINER --public=false --group-write="" --group-read=$GROUP1 || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER > /dev/null || echo " [FAIL]" && exit -1
echo -n " [OK] "

echo -n "copy file (inherit group-read)  "
$CMD $CERT --copy --src=something.png --dest=$CONTAINER/something.png || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT1 --view --target=$CONTAINER/something.png | grep 'readable by:' | grep -q $GROUP1 || echo " [FAIL]" && exit -1
echo -n " [OK] "

echo -n "cleanup"
$CMD $CERT --delete --target=$CONTAINER || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER > /dev/null && echo " [FAIL]" && exit -1
echo " [OK] "

# start fresh

echo -n "create container "
$CMD $CERT --create --target=$CONTAINER --public=false --group-write="" --group-read="" || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER | grep 'readable by:' | grep -q 'ivo' && echo " [FAIL]" && exit -1
echo -n " [OK] "

echo -n "copy file with --group-read "
$CMD $CERT --copy --src=something.png --dest=$CONTAINER/something.png --group-read=$GROUP1 || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/something.png | grep 'readable by:' | grep -q $GROUP1 || echo " [FAIL]" && exit -1
echo -n " [OK] "

echo -n "cleanup"
$CMD $CERT --delete --target=$CONTAINER || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER > /dev/null && echo " [FAIL]" && exit -1
echo " [OK] "

# fresh start

echo -n "create container with --public=false "
$CMD $CERT --create --target=$CONTAINER --public=false --group-write="" --group-read="" || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER | grep -q 'readable by anyone: false' || echo " [FAIL]" && exit -1
echo -n " [OK] "

echo -n "try to set --public as CADCAuthtest1 (denied) "
$CMD $CERT1 --set --target=$CONTAINER --public > /dev/null && echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER | grep -q 'readable by anyone: false' || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "cleanup"
$CMD $CERT --delete --target=$CONTAINER || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER > /dev/null && echo " [FAIL]" && exit -1
echo " [OK] "

echo
echo "*** test sequence passed ***"

date
