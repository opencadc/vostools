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


set LSCMD = "python $CADC_ROOT/vls -l"
set MKDIRCMD = "python $CADC_ROOT/vmkdir"
set RMCMD = "python $CADC_ROOT/vrm"
set CPCMD = "python $CADC_ROOT/vcp"
set RMDIRCMD = "python $CADC_ROOT/vrmdir"
set MVCMD = "python $CADC_ROOT/vmv"
set CHMODCMD = "python $CADC_ROOT/vchmod"


set CERT =  "--cert=$A/test-certificates/x509_CADCRegtest1.pem"
set CERT1 = "--cert=$A/test-certificates/x509_CADCAuthtest1.pem"
set CERT2 = "--cert=$A/test-certificates/x509_CADCAuthtest2.pem"


# group 3000 aka CADC_TEST1-Staff has members: CADCAuthtest1
set GROUP1 = "CADC_TEST1-Staff"

# group 3100 aka CADC_TEST2-Staff has members: CADCAuthtest1, CADCAuthtest2
set GROUP2 = "CADC_TEST2-Staff"

# using a test dir makes it easier to cleanup a bunch of old/failed tests
set VOHOME = "vos:CADCRegtest1"
set BASE = "$VOHOME/atest"

set TIMESTAMP=`date +%Y-%m-%dT%H-%M-%S`
set CONTAINER = $BASE/$TIMESTAMP

echo -n "** checking base URI"
$LSCMD $CERT $BASE > /dev/null
if ( $status == 0) then
    echo " [OK]"
else
    echo -n ", creating base URI"
        $MKDIRCMD $CERT $BASE || echo " [FAIL]" && exit -1
    echo " [OK]"
endif

echo -n "** setting home and base to public"
$CHMODCMD $CERT o+r $VOHOME || echo " [FAIL]" && exit -1
$CHMODCMD $CERT o+r $BASE || echo " [FAIL]" && exit -1
echo " [OK]"
echo

echo "*** starting test sequence ***"
echo
echo "test container: " $CONTAINER
echo

echo -n "create container (no permissions) "
$MKDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
$CHMODCMD $CERT o-r $CONTAINER ||  echo " [FAIL]" && exit -1
$CHMODCMD $CERT g-rw $CONTAINER ||  echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "copy file to existing container and non-existent data node "
$CPCMD $CERT something.png $CONTAINER/something.png || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "testing read as CADCAuthtest1 (denied) "
$LSCMD $CERT1 $CONTAINER/something.png >& /dev/null && echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "set group-read of container and data node to $GROUP1 "
$CHMODCMD $CERT g+r $CONTAINER $GROUP1 || echo " [FAIL set container]" && exit -1
$CHMODCMD $CERT g+r $CONTAINER/something.png $GROUP1 || echo " [FAIL set data]" && exit -1
$LSCMD $CERT $CONTAINER/something.png | grep "\-rw-r-----" | grep -q "$GROUP1" || echo " [FAIL check container]" && exit -1
echo " [OK]"

echo -n "testing read as CADCAuthtest1 vs $GROUP1 (allowed) "
$LSCMD $CERT1 $CONTAINER/something.png > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "testing read as CADCAuthtest2 vs $GROUP1 (denied) "
$LSCMD $CERT2 $CONTAINER/something.png >& /dev/null && echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "set group-read of container to $GROUP2 "
$CHMODCMD $CERT g+r $CONTAINER $GROUP2 || echo " [FAIL set container]" && exit -1
echo -n " verify "
$LSCMD $CERT $BASE | grep $TIMESTAMP | grep 'drw-r-----' | grep -q $GROUP2 || echo " [FAIL check container]" && exit -1
echo -n " set group-read of data to $GROUP2 "
$CHMODCMD $CERT g+r $CONTAINER/something.png $GROUP2 || echo " [FAIL set data]" && exit -1
echo -n " verify "
$LSCMD $CERT $CONTAINER/something.png | grep '\-rw-r-----' | grep -q $GROUP2 || echo " [FAIL check data]" && exit -1
echo " [OK]"

echo -n "testing read as CADCAuthtest1 vs $GROUP2 (allowed) "
$LSCMD $CERT1 $CONTAINER/something.png > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "testing read as CADCAuthtest2 vs $GROUP2 (allowed) "
$LSCMD $CERT2 $CONTAINER/something.png > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "delete test container as CADCAuthtest2 (denied)"
$RMDIRCMD $CERT2 $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $CONTAINER > /dev/null || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "delete test container (allowed)"
$RMDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
echo " [OK]"

# start fresh

echo -n "create container with --public "
$MKDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
$CHMODCMD $CERT o+r $CONTAINER || echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $CONTAINER > /dev/null || echo " [FAIL]" && exit -1
echo -n " [OK] "

echo -n "copy file (inherit public)  "
$CPCMD $CERT something.png $CONTAINER/something.png || echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT1 $CONTAINER/something.png | grep -q '\-rw----r--' || echo " [FAIL]" && exit -1
echo -n " [OK] "

echo -n "cleanup"
$RMDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
echo " [OK] "

# start fresh

echo -n "create container "
$MKDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
$CHMODCMD $CERT o+r $CONTAINER || echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $BASE | grep $TIMESTAMP | grep -q 'drw----r--' || echo " [FAIL]" && exit -1
echo -n " [OK] "

echo -n "copy file with --public "
$CPCMD $CERT something.png $CONTAINER/something.png || echo " [FAIL]" && exit -1
$CHMODCMD $CERT o+r $CONTAINER/something.png || echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $CONTAINER/something.png | grep -q '\-rw----r--' || echo " [FAIL]" && exit -1
echo -n " [OK] "

echo -n "cleanup"
$RMDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
echo " [OK] "

# starting fresh

echo -n "create container with --group-read=${GROUP1} "
$MKDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
$CHMODCMD $CERT o-r $CONTAINER || echo " [FAIL]" && exit -1
$CHMODCMD $CERT g+r $CONTAINER $GROUP1 || echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $BASE | grep $TIMESTAMP > /dev/null || grep -q 'drw-r-----' || echo " [FAIL]" && exit -1
echo -n " [OK] "

echo -n "copy file (inherit group-read)  "
$CPCMD $CERT something.png $CONTAINER/something.png || echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT1 $CONTAINER/something.png | grep '\-rw-r-----' | grep -q $GROUP1 || echo " [FAIL]" && exit -1
echo -n " [OK] "

echo -n "cleanup"
$RMDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
echo " [OK] "

# start fresh

echo -n "create container "
$MKDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
$CHMODCMD $CERT o-r $CONTAINER || echo " [FAIL CHMOD]" && exit -1
echo -n " verify "
$LSCMD $CERT $BASE | grep $TIMESTAMP | grep -q 'drw-------' || echo " [FAIL]" && exit -1
echo -n " [OK] "

echo -n "copy file with --group-read "
$CPCMD $CERT something.png $CONTAINER/something.png || echo " [FAIL]" && exit -1
$CHMODCMD $CERT g+r $CONTAINER/something.png $GROUP1 || echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $CONTAINER/something.png | grep '\-rw-r-----' | grep -q $GROUP1 || echo " [FAIL]" && exit -1
echo -n " [OK] "

echo -n "cleanup"
$RMDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
echo " [OK] "

# fresh start

echo -n "create container with --public=false "
$MKDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
$CHMODCMD $CERT o-r $CONTAINER || echo " [FAIL CHMOD]" && exit -1
echo -n " verify "
$LSCMD $CERT $BASE | grep $TIMESTAMP | grep -q 'drw-------' || echo " [FAIL]" && exit -1
echo -n " [OK] "

echo -n "try to set --public as CADCAuthtest1 (denied) "
$CHMODCMD $CERT1 o+r $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $BASE | grep $TIMESTAMP | grep -q 'drw-------' || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "cleanup"
$RMDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
echo " [OK] "

echo
echo "*** test sequence passed ***"

date
