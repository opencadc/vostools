#!/bin/tcsh -f
set THIS_DIR = `dirname $0`
set THIS_DIR = `cd $THIS_DIR && pwd`


if (! ${?VOSPACE_WEBSERVICE} ) then
	echo "VOSPACE_WEBSERVICE env variable not set, use default WebService URL"
else
	echo "WebService URL (VOSPACE_WEBSERVICE env variable): $VOSPACE_WEBSERVICE"
endif

if (! ${?CADC_TESTCERT_PATH} ) then
  echo "Missing CADC_TESTCERT_PATH location to cadc-auth.pem and cadc-auth-test.pem files"
  exit -1
else
	echo "cert files path:  ($CADC_TESTCERT_PATH env variable): $CADC_TESTCERT_PATH"
	set CERT =  "--cert=$CADC_TESTCERT_PATH/cadc-auth.pem"
	set CERT1 =  "--cert=$CADC_TESTCERT_PATH/cadc-auth-test.pem"
endif


set GROUP = "opencadc-vospace-test"

if($#argv == 0) then
    set resources = "vault cavern"
    echo "Testing against default resources: $resources"
else
    set resources = ($argv)
    echo "Testing against resources: $resources"
endif


set LSCMD = "vls -lk"
set MKDIRCMD = "vmkdir -k"
set RMDIRCMD = "vrmdir -k"
set RMCMD = "vrm -k"
set CHMODCMD = "vchmod -k"
set VTAGCMD = "vtag -k"


echo

foreach resource ($resources)
    echo "************* TESTING AGAINST $resource ****************"

    echo $resource | grep "cavern" >& /dev/null
    if ( $status == 0) then
        set VOROOT = "cavern:"
        set TESTING_CAVERN = "true"
    else
        set VOROOT = "vos:"
    endif

  set HOME_BASE = "vostools-inttest"
  set VOHOME = "$VOROOT""$HOME_BASE"
  set BASE = $VOHOME

  set TIMESTAMP=`date +%Y-%m-%dT%H-%M-%S`
  set CONTAINER = $BASE/$TIMESTAMP

  echo -n "** checking base URI"
#  $RMCMD -R $CERT $BASE > /dev/null
#  echo -n ", creating base URI"
#      $MKDIRCMD $CERT $BASE || echo " [FAIL]" && exit -1
#      $VTAGCMD $CERT $BASE 'ivo://cadc.nrc.ca/vospace/core#inheritPermissions=true'
#  echo " [OK]"

  echo -n "** setting home and base to public, no groups"
  $CHMODCMD $CERT o+r $VOHOME || echo " [FAIL]" && exit -1
  $CHMODCMD $CERT o+r $BASE || echo " [FAIL]" && exit -1
  echo " [OK]"

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
  $MKDIRCMD $CERT $CONTAINER || echo " [FAIL1]" && exit -1
  $CHMODCMD $CERT o-r $CONTAINER ||  echo " [FAIL2]" && exit -1
  $CHMODCMD $CERT g-r $CONTAINER ||  echo " [FAIL3]" && exit -1
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

#  echo -n "test vchmod with recursive option"
#
#  $CHMODCMD $CERT -R g+r $CONTAINER $GROUP ||  echo " [FAIL]" && exit -1
#  echo -n " verify "
#  $LSCMD $CERT $BASE | grep $TIMESTAMP | grep $GROUP | grep -q "drw-r-----" || echo " [FAIL]" && exit -1
#  $LSCMD $CERT $CONTAINER | grep aaa | grep $GROUP | grep -q "drw-r-----" || echo " [FAIL]" && exit -1
#  $LSCMD $CERT $CONTAINER | grep ccc | grep $GROUP | grep -q "drw-r-----" || echo " [FAIL]" && exit -1
#  $LSCMD $CERT $CONTAINER/aaa | grep bbb | grep $GROUP | grep -q "drw-r-----" || echo " [FAIL]" && exit -1
#  echo " [OK]"

  echo -n "test vchmod with multiple groups"

  set MULTIGROUP = "ABC $GROUP"
  $CHMODCMD $CERT g+r $CONTAINER/aaa "$MULTIGROUP" ||  echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $BASE | grep $TIMESTAMP | grep $GROUP | grep -q "drw-r-----" || echo " [FAIL]" && exit -1
  echo ""
  $LSCMD $CERT $CONTAINER | grep aaa | grep "$MULTIGROUP" | grep -q "drw-r-----" || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "make a sub-container public"

  $CHMODCMD $CERT o+r $CONTAINER/aaa/bbb ||  echo " [FAIL]" && exit -1
  echo -n " verify "
  $LSCMD $CERT $BASE | grep $TIMESTAMP | grep $GROUP | grep -q "drw-r-----" || echo " [FAIL1]" && exit -1
  $LSCMD $CERT $CONTAINER | grep aaa | grep "$MULTIGROUP" | grep -q "drw-r-----" || echo " [FAIL2]" && exit -1
  $LSCMD $CERT $CONTAINER | grep ccc | grep $GROUP | grep -q "drw-r-----" || echo " [FAIL3]" && exit -1
  $LSCMD $CERT $CONTAINER/aaa | grep bbb | grep "$MULTIGROUP" | grep -q "drw-r--r--" || echo " [FAIL4]" && exit -1
  echo " [OK]"

#  echo -n "recursively make all directories public"
#  $CHMODCMD $CERT -R o+r $CONTAINER ||  echo " [FAIL]" && exit -1
#  echo -n " verify "
#  $LSCMD $CERT $BASE | grep $TIMESTAMP | grep $GROUP | grep -q "drw-r--r--" || echo " [FAIL1]" && exit -1
#  $LSCMD $CERT $CONTAINER | grep aaa | grep "$MULTIGROUP" | grep -q "drw-r--r--" || echo " [FAIL2]" && exit -1
#  $LSCMD $CERT $CONTAINER | grep ccc | grep $GROUP | grep -q "drw-r--r--" || echo " [FAIL3]" && exit -1
#  $LSCMD $CERT $CONTAINER/aaa | grep bbb | grep "$MULTIGROUP" | grep -q "drw-r--r--" || echo " [FAIL4]" && exit -1
#  echo " [OK]"

  # test interupt
#  echo -n "interrupt recursive vchmod"
#
#  set TESTDIR = "testrecursiveinterrupt"
#  set TESTPATH = $BASE/$TESTDIR
#  $LSCMD $CERT $BASE | grep $TESTDIR | grep -q $TESTDIR
#  if ($? != 0) then
#      echo
#      echo "create 1000 directories in testrecursiveinterrupt directory prior to runing test"
#      $MKDIRCMD $CERT $TESTPATH || echo " [FAIL]" && exit -1
#      $CHMODCMD $CERT o-r $TESTPATH ||  echo " [FAIL]" && exit -1
#      $CHMODCMD $CERT g-r $TESTPATH ||  echo " [FAIL]" && exit -1
#      foreach dir (`seq 1000`)
#          $MKDIRCMD $CERT $TESTPATH/"dir"$dir || echo " [FAIL]" && exit -1
#      end
#  endif
#  echo -n " vchmod"
#  set logFile = "/tmp/vchmod-$TIMESTAMP.log"
#  rm $logFile >& /dev/null
#  echo $CHMODCMD $CERT -d -R g+r $TESTPATH $GROUP
#  $CHMODCMD $CERT -d -R g+r $TESTPATH $GROUP >& $logFile&
#  give vchmod command time to start
#  set chmodPID = $!
#  set jobURL = `grep "nodeprops/" $logFile | head -n 1 | awk '{print $NF}'`
#  while ($jobURL == "")
#      echo "Sleep for 3s..."
#      sleep 3
#      set jobURL = `grep "nodeprops/" $logFile | head -n 1 | awk '{print $NF}'`
#  end
#  echo $jobURL
#  kill -s INT $chmodPID
#  # give kill a chance to complete
#  sleep 2
#  #rm $logFile >& /dev/null
#  #verify job has been aborted
#  #TODO to be implemented in Python
#  #set phase = `$CHECKJOB $CERT $jobURL`
#  #if $phase != 'ABORTED' then
#  #echo " [FAIL]" && exit -1
#  #else
#  echo " [OK]"
#  #endif

  #cleanup
  $RMCMD -R $CERT $CONTAINER || echo " [FAIL]" && exit -1
  echo
  echo "*** test sequence passed for resource $resource ***"
end

echo "*** DONE test sequences passed ***"

date
