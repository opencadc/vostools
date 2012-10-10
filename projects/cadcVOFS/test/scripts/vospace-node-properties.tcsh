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
set RMDIRCMD = "python $CADC_ROOT/vrmdir"
set CHMODCMD = "python $CADC_ROOT/vchmod"
set CHECKJOB = "python $CADC_ROOT/checkJobPhase"


set CERT =  "--cert=$A/test-certificates/x509_CADCRegtest1.pem"


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
$CHMODCMD $CERT g-r $CONTAINER ||  echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $BASE | grep $TIMESTAMP | grep -q "drw-------" || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "create a sub-container"
$MKDIRCMD $CERT $CONTAINER/aaa || echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $CONTAINER | grep aaa | grep -q "drw-------" || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "create a sub-sub-container"
$MKDIRCMD $CERT $CONTAINER/aaa/bbb || echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $CONTAINER/aaa | grep bbb | grep -q "drw-------" || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "create anoter sub-container"
$MKDIRCMD $CERT $CONTAINER/ccc || echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $CONTAINER | grep ccc | grep -q "drw-------" || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "test vchmod with recursive option"
$CHMODCMD $CERT -R g+r $CONTAINER $GROUP1 ||  echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $BASE | grep $TIMESTAMP | grep $GROUP1 | grep -q "drw-r-----" || echo " [FAIL]" && exit -1
$LSCMD $CERT $CONTAINER | grep aaa | grep $GROUP1 | grep -q "drw-r-----" || echo " [FAIL]" && exit -1
$LSCMD $CERT $CONTAINER | grep ccc | grep $GROUP1 | grep -q "drw-r-----" || echo " [FAIL]" && exit -1
$LSCMD $CERT $CONTAINER/aaa | grep bbb | grep $GROUP1 | grep -q "drw-r-----" || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "make a sub-container public"
$CHMODCMD $CERT o+r $CONTAINER/aaa/bbb ||  echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $BASE | grep $TIMESTAMP | grep $GROUP1 | grep -q "drw-r-----" || echo " [FAIL]" && exit -1
$LSCMD $CERT $CONTAINER | grep aaa | grep $GROUP1 | grep -q "drw-r-----" || echo " [FAIL]" && exit -1
$LSCMD $CERT $CONTAINER | grep ccc | grep $GROUP1 | grep -q "drw-r-----" || echo " [FAIL]" && exit -1
$LSCMD $CERT $CONTAINER/aaa | grep bbb | grep $GROUP1 | grep -q "drw-r--r--" || echo " [FAIL]" && exit -1
echo " [OK]"

echo -n "recursively make all directories public"
$CHMODCMD $CERT -R o+r $CONTAINER ||  echo " [FAIL]" && exit -1
echo -n " verify "
$LSCMD $CERT $BASE | grep $TIMESTAMP | grep $GROUP1 | grep -q "drw-r--r--" || echo " [FAIL]" && exit -1
$LSCMD $CERT $CONTAINER | grep aaa | grep $GROUP1 | grep -q "drw-r--r--" || echo " [FAIL]" && exit -1
$LSCMD $CERT $CONTAINER | grep ccc | grep $GROUP1 | grep -q "drw-r--r--" || echo " [FAIL]" && exit -1
$LSCMD $CERT $CONTAINER/aaa | grep bbb | grep $GROUP1 | grep -q "drw-r--r--" || echo " [FAIL]" && exit -1
echo " [OK]"

#test interupt
echo -n "interrupt recursive vchmod"
set TESTDIR = "testrecursiveinterrupt"
set TESTPATH = $BASE/$TESTDIR
$LSCMD $CERT $BASE | grep $TESTDIR | grep -q $TESTDIR
if ($? != 0) then
    echo 
    echo "create 1000 directories in testrecursiveinterrupt directory prior to runing test"
    $MKDIRCMD $CERT $TESTPATH || echo " [FAIL]" && exit -1
    $CHMODCMD $CERT o-r $TESTPATH ||  echo " [FAIL]" && exit -1
    $CHMODCMD $CERT g-r $TESTPATH ||  echo " [FAIL]" && exit -1
    foreach dir (`seq 1000`)
        $MKDIRCMD $CERT $TESTPATH/"dir"$dir || echo " [FAIL]" && exit -1
    end
endif
echo -n " vchmod"
set logFile = "/tmp/vchmod-$TIMESTAMP.log"
rm $logFile >& /dev/null
$CHMODCMD $CERT -v -R g+r $TESTPATH $GROUP1 >& $logFile&
#give vchmod command time to start
set chmodPID = $!
sleep 2 
kill -s INT $chmodPID 
# give kill a chance to complete
sleep 2 
# find the job ID
set jobURL = `grep "nodeprops/" $logFile | head -n 1 | awk '{for(i=1;i<NF;++i)if($i~/nodeprops/)print $i}'`
#echo "$jobURL"
set jobID = `echo $jobURL | awk -F '/' '{print $(NF-1)}'` 
#echo "$jobID"
#rm $logFile >& /dev/null
#verify job has been aborted
set phase = `$CHECKJOB $CERT $jobURL`
#echo "$phase"
if $phase != 'ABORTED' then
    echo " [FAIL]" && exit -1
else
    echo " [OK]"
endif

#cleanup
$RMDIRCMD $CERT $CONTAINER || echo " [FAIL]" && exit -1

echo
echo "*** test sequence passed ***"

date
