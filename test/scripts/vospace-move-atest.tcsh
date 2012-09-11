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
set CERT1 = "--cert=$A/test-certificates/x509_CADCAuthtest1.crt --key=$A/test-certificates/x509_CADCAuthtest1.key"
set CERT2 = "--cert=$A/test-certificates/x509_CADCAuthtest2.crt --key=$A/test-certificates/x509_CADCAuthtest2.key"

set CRED_CLIENT="java ${LOCAL} -jar ${CADC_ROOT}/lib/cred_wsPubClient.jar"

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

echo -n "setup: create container "
$CMD $CERT --create --target=$CONTAINER --public --group-write=$GROUP1 ||  echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "setup: create container/a read-write to group 2"
$CMD $CERT --create --target=$CONTAINER/a --group-write=$GROUP2 || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/a > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "setup: create container/a/aa read-write to group 1"
$CMD $CERT --create --target=$CONTAINER/a/aa --group-write=$GROUP1 || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/a/aa > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "setup: upload data node aaa to container/a/aa "
$CMD $CERT --copy --src=something.png --dest=$CONTAINER/a/aa/aaa || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/a/aa/aaa > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "setup: upload data node b to container "
$CMD $CERT --copy --src=something.png --dest=$CONTAINER/b || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/b > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "setup: upload data node c to container read-write to group 2"
$CMD $CERT --create --target=$CONTAINER/c --group-write=$GROUP2 || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/c > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "setup: create container/d read-write to group 2"
$CMD $CERT --create --target=$CONTAINER/d --group-write=$GROUP2 || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/d > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "setup: create container/d read-write to group 1"
$CMD $CERT --create --target=$CONTAINER/e --group-write=$GROUP1 || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/e > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "check no write permission on source data node (fail)"
$CMD $CERT2 --move --src=$CONTAINER/b --dest=$CONTAINER/d > /dev/null && echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/b > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "check no recursive write permission on source container node (fail)"
$CMD $CERT2 --move --src=$CONTAINER/a --dest=$CONTAINER/d > /dev/null && echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/a > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "check no write permission on dest (fail)"
$CMD $CERT2 --move --src=$CONTAINER/c --dest=$CONTAINER/e > /dev/null && echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/c > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "test dest a data node (fail)"
$CMD $CERT1 --move --src=$CONTAINER/a --dest=$CONTAINER/b > /dev/null && echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/a > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "test circular move (fail)"
$CMD $CERT1 --move --src=$CONTAINER/a --dest=$CONTAINER/a/aa > /dev/null && echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/a > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "test root move (fail)"
$CMD $CERT1 --move --src=$BASE --dest=$CONTAINER/d > /dev/null && echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$BASE > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "test move container into container (pass)"
$CMD $CERT1 --move --src=$CONTAINER/a --dest=$CONTAINER/d > /dev/null || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/d/a > /dev/null || echo " [FAIL]" && exit -1
$CMD $CERT --view --target=$CONTAINER/d/a/aa > /dev/null || echo " [FAIL]" && exit -1
$CMD $CERT --view --target=$CONTAINER/d/a/aa/aaa > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "setup: move container back"
$CMD $CERT1 --move --src=$CONTAINER/d/a --dest=$CONTAINER > /dev/null || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/a > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "test move container with new name (pass)"
$CMD $CERT1 --move --src=$CONTAINER/a --dest=$CONTAINER/d/x > /dev/null || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/d/x > /dev/null || echo " [FAIL]" && exit -1
$CMD $CERT --view --target=$CONTAINER/d/x/aa > /dev/null || echo " [FAIL]" && exit -1
$CMD $CERT --view --target=$CONTAINER/d/x/aa/aaa > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "setup: move container back"
$CMD $CERT1 --move --src=$CONTAINER/d/x --dest=$CONTAINER/a > /dev/null || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/a > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "test move file into container (pass)"
$CMD $CERT1 --move --src=$CONTAINER/a/aa/aaa --dest=$CONTAINER/d > /dev/null || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/d/aaa > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "test move file with new name"
$CMD $CERT1 --move --src=$CONTAINER/d/aaa --dest=$CONTAINER/a/aa/bbb > /dev/null || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/a/aa/bbb > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "test rename file"
$CMD $CERT1 --move --src=$CONTAINER/a/aa/bbb --dest=$CONTAINER/a/aa/aaa > /dev/null || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/a/aa/aaa > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "move a vos container to local file system (fail)"
$CMD $CERT1 --move --src=$CONTAINER/a --dest=notused.txt > /dev/null && echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/a > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "move a vos data node to local file system (pass)"
$CMD $CERT1 --move --src=$CONTAINER/a/aa/aaa --dest=something2.png > /dev/null || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/a/aa/aaa > /dev/null && echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "move a local directory to vos (fail)"
mkdir testdir
$CMD $CERT1 --move --src=testdir --dest=$CONTAINER/a > /dev/null && echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/a/testdir > /dev/null && echo " [FAIL]" && exit -1
rmdir testdir
echo " [OK]"

echo -n "move a file to vos (success)"
$CMD $CERT1 --move --src=something2.png --dest=$CONTAINER/a/aa/aaa> /dev/null || echo " [FAIL]" && exit -1
echo -n " verify "
$CMD $CERT --view --target=$CONTAINER/a/aa/aaa > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "do a local file system move (fail--unsupported)"
cp -f something.png something2.png
$CMD $CERT --move --src=something2.png --dest=something3.png > /dev/null && echo " [FAIL]" && exit -1
rm something2.png
echo " [OK]"

echo
echo "*** test sequence passed ***"

date
