#!/bin/tcsh -f

date
echo "###################"
set THIS_DIR = `dirname $0`
set THIS_DIR = `cd $THIS_DIR && pwd`

if (! ${?VOSPACE_WEBSERVICE} ) then
	echo "VOSPACE_WEBSERVICE env variable not set, use default WebService URL"
else
	echo "WebService URL (VOSPACE_WEBSERVICE env variable): $VOSPACE_WEBSERVICE"
endif

if (! ${?CADC_TESTCERT_PATH} ) then
  echo "CADC_TESTCERT_PATH env variable not set. Must point to the location of x509_CADCRegtest1.pem cert file"
  exit -1
else
  set CERTFILE = "$CADC_TESTCERT_PATH/x509_CADCRegtest1.pem"
  echo "cert file:  (CADC_TESTCERT_PATH env variable): $CERTFILE"
endif

if (! ${?TMPDIR} ) then
        echo "TMPDIR env variable not set, using /tmp"
        set TMPDIR = "/tmp"
else
        echo "Using ${TMPDIR} for temporary files"
endif

set LSCMD = "vls -l"
set MKDIRCMD = "vmkdir"
set RMCMD = "vrm"
set CPCMD = "vcp"
set RMDIRCMD = "vrmdir"
set CHMODCMD = "vchmod"

set CERT = " --cert=$CERTFILE"

echo "vls command: " $LSCMD $CERT
echo

if($#argv == 0) then
    set resources = "vault cavern"
    echo "Testing against default resources: $resources"
else
    set resources = ($argv)
    echo "Testing against resources: $resources"
endif

foreach resource ($resources)
    echo "************* TESTING AGAINST $resource ****************"

  # use resourceID in vos-config to determine the base URI
  # vault uses CADCRegtest1, cavern uses home/cadcregtest1
  echo $resource | grep "cavern" >& /dev/null
  if ( $status == 0) then
      set VOROOT = "arc:"
      set HOME_BASE = "home/cadcregtest1"
      set TESTING_CAVERN = "true"
  else
      set VOROOT = "vos:"
      set HOME_BASE = "CADCRegtest1"
  endif
  set VOHOME = "$VOROOT""$HOME_BASE"
  set BASE = "$VOHOME/atest"

  set TIMESTAMP=`date +%Y-%m-%dT%H-%M-%S`
  set CONTAINER = $BASE/$TIMESTAMP


  echo -n "** checking base URI"
  $LSCMD $CERT $BASE > /dev/null
  if ( $status == 0) then
      echo " [OK]"
  else
      echo -n ", creating base URI"
      exit
          $MKDIRCMD $CERT $BASE || echo " [FAIL]" && exit -1
      echo " [OK]"
  endif
  echo -n "** setting home and base to public, no groups"
  $CHMODCMD $CERT o+r $VOHOME || echo " [FAIL]" && exit -1
  echo -n " [OK]"
  $CHMODCMD $CERT o+r $BASE || echo " [FAIL]" && exit -1
  echo " [OK]"
  echo
  echo "*** starting test sequence ***"
  echo
  echo "** test container: ${CONTAINER}"
  echo

  echo -n "view vospace root container "
  #$LSCMD $CERT $VOROOT > /dev/null || echo " [FAIL]" && exit -1
  #echo " [OK]"
  echo "[SKIP]"

  echo -n "view non-existent node "
  $LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "create private container "
  $MKDIRCMD $CERT $CONTAINER > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "view created container "
  $LSCMD $CERT $CONTAINER > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "verify public=false after create "
  if ( ${?TESTING_CAVERN} ) then
      echo " [SKIPPED, permission inheitance not supported]"
  else
      $LSCMD $CERT $BASE | grep $TIMESTAMP | grep -q 'drw----r--' || echo " [FAIL]" && exit -1
      echo " [OK]"
  endif

  echo -n "check set permission properties "
  if ( ${?TESTING_CAVERN} ) then
      echo " [SKIPPED, permission inheitance not supported]"
  else
      $CHMODCMD $CERT g+rw $CONTAINER test:g1 test:g2 || echo " [FAIL]" && exit -1
      $LSCMD $CERT $BASE | grep $TIMESTAMP | grep -q 'drw-rw-r--' || echo " [FAIL]" && exit -1
      $LSCMD $CERT $BASE | grep $TIMESTAMP | grep -q 'test:g1' || echo " [FAIL]" && exit -1
      $LSCMD $CERT $BASE | grep $TIMESTAMP | grep -q 'test:g2' || echo " [FAIL]" && exit -1
      echo " [OK]"
  endif

  echo -n "check inherit permission properties "
  if ( ${?TESTING_CAVERN} ) then
      echo " [SKIPPED, permission inheitance not supported]"
  else
      $MKDIRCMD $CERT $CONTAINER/pub || echo " [FAIL]" && exit -1
      $LSCMD $CERT $CONTAINER | grep pub | grep -q 'drw-rw-r--' || echo " [FAIL]" && exit -1
      $LSCMD $CERT $CONTAINER | grep pub | grep -q 'test:g1' || echo " [FAIL]" && exit -1
      $LSCMD $CERT $CONTAINER | grep pub | grep -q 'test:g2' || echo " [FAIL]" && exit -1
      echo " [OK]"
  endif

  echo -n "check inherit + change certain properties "
  if ( ${?TESTING_CAVERN} ) then
      echo " [SKIPPED, permission inheitance not supported]"
  else
      $MKDIRCMD $CERT $CONTAINER/priv || echo " [FAIL]" && exit -1
      $CHMODCMD $CERT g+r $CONTAINER/priv test:g3 || echo " [FAIL]" && exit -1
      $LSCMD $CERT $CONTAINER | grep priv | grep -q 'drw-rw-r--' || echo " [FAIL]" && exit -1
      $LSCMD $CERT $CONTAINER | grep priv | grep -q 'test:g3' || echo " [FAIL]" && exit -1
      $LSCMD $CERT $CONTAINER | grep priv | grep -q 'test:g2' || echo " [FAIL]" && exit -1
      echo " [OK]"
  endif

  echo -n "check recursive create (non-existant parents) "
  #$MKDIRCMD $CERT $CONTAINER/foo/bar/baz >& /dev/null || echo " [FAIL]" && exit -1
  #$LSCMD $CERT $CONTAINER/foo/bar/baz >& /dev/null # || echo " [FAIL]" TODO add -p option&& exit -1
  echo "[TODO]"

  echo -n "copy file to existing container and non-existent data node "
  $CPCMD $CERT $THIS_DIR/something.png $CONTAINER/something.png || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "copy empty files"
  rm -f /tmp/zerosize.txt
  touch /tmp/zerosize.txt
  $CPCMD $CERT /tmp/zerosize.txt $CONTAINER || echo " [FAIL]" && exit -1
  $LSCMD $CERT $CONTAINER/zerosize.txt | awk '{print $5}'| grep "0" >& /dev/null || echo " [FAIL]" && exit -1
  # repeat
  $CPCMD $CERT /tmp/zerosize.txt $CONTAINER || echo " [FAIL]" && exit -1
  $LSCMD $CERT $CONTAINER/zerosize.txt | awk '{print $5}'| grep "0" >& /dev/null || echo " [FAIL]" && exit -1
  # change size
  echo "test" > /tmp/zerosize.txt
  $CPCMD $CERT /tmp/zerosize.txt $CONTAINER || echo " [FAIL]" && exit -1
  $LSCMD $CERT $CONTAINER/zerosize.txt | awk '{print $5}'| grep "0" >& /dev/null && echo " [FAIL]" && exit -1
  # repeat
  $CPCMD $CERT /tmp/zerosize.txt $CONTAINER || echo " [FAIL]" && exit -1
  $LSCMD $CERT $CONTAINER/zerosize.txt | awk '{print $5}'| grep "0" >& /dev/null && echo " [FAIL]" && exit -1
  # make it back 0 size
  /bin/cp /dev/null /tmp/zerosize.txt
  $CPCMD $CERT /tmp/zerosize.txt $CONTAINER || echo " [FAIL]" && exit -1
  $LSCMD $CERT $CONTAINER/zerosize.txt | awk '{print $5}'| grep "0" >& /dev/null || echo " [FAIL]" && exit -1
  # repeat
  $CPCMD $CERT /tmp/zerosize.txt $CONTAINER || echo " [FAIL]" && exit -1
  $LSCMD $CERT $CONTAINER/zerosize.txt | awk '{print $5}'| grep "0" >& /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"



  echo -n "view existing data node "
  $LSCMD $CERT $CONTAINER/something.png > /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "copy data node to local filesystem "
  $CPCMD $CERT $CONTAINER/something.png $THIS_DIR/something.png.2 || echo " [FAIL]" && exit -1
  cmp $THIS_DIR/something.png $THIS_DIR/something.png.2 || echo " [FAIL]" && exit -1
  \rm -f $THIS_DIR/something.png.2
  echo " [OK]"

  echo -n "Check quick copy"
  $CPCMD $CERT --quick $THIS_DIR/something.png $CONTAINER/something.png.3|| echo " [FAIL]" && exit -1
  $CPCMD $CERT --quick $CONTAINER/something.png.3 $THIS_DIR/something.png.3 || echo " [FAIL]" && exit -1
  cmp $THIS_DIR/something.png $THIS_DIR/something.png.3 || echo " [FAIL]" && exit -1
  \rm -f $THIS_DIR/something.png.3
  echo " [OK]"

  echo -n "Check pattern matched copy"
  $CPCMD $CERT "$CONTAINER/something*" $TMPDIR || echo " [FAIL]" && exit -1
  $MKDIRCMD $CERT $CONTAINER/pattern_dest
  $CPCMD $CERT $THIS_DIR/something* $CONTAINER/pattern_dest || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "Check pattern with cutout"
  $CPCMD $CERT "$BASE/test*.fits[1:10,1:10]" $TMPDIR || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "Do a real cutout of a known file"
  #$CPCMD $CERT "vos:CADCRegtest1/DONOTDELETE_VOSPACE_CUTOUT_TEST.fits(34.436194,19.34665,0.01)" $TMPDIR/testcutout || echo " [FAIL]" && exit -1
  #if (`cat $TMPDIR/testcutout | md5` != "cb7d6a829277975d1016a769970ec45a") then
      #	echo " [FAIL]" && exit -1
      #endif
      #\rm -f $TMPDIR/testcutout
      #echo "[OK]"
  echo "[SKIP]"

  $CPCMD $CERT $CONTAINER/something.png $THIS_DIR/something.png.2 || echo " [FAIL]" && exit -1

  echo -n "copy/overwrite existing data node "
  $CPCMD $CERT $THIS_DIR/something.png $CONTAINER/something.png || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "set content-type during copy "
  #$CMD $CERT --copy --src=$THIS_DIR/something.png --dest=$CONTAINER/something2.png --content-type=image/png || echo " [FAIL]" && exit -1
  #$CMD $CERT --view --target=$CONTAINER/something2.png | grep -q 'type: image/png' || echo " [FAIL]" && exit -1
  echo " [TODO]"

  echo -n "set content-type during copy/overwrite "
  #$CMD $CERT --copy --src=$THIS_DIR/something.png --dest=$CONTAINER/something.png --content-type=JUNK || echo " [FAIL]" && exit -1
  #$CMD $CERT --view --target=$CONTAINER/something.png | grep -q 'type: JUNK' || echo " [FAIL]" && exit -1
  echo " [TODO]"

  echo -n "set content-type of existing data node "
  #$CMD $CERT --set --target=$CONTAINER/something.png --content-type=image/png || echo " [FAIL]" && exit -1
  #$CMD $CERT --view --target=$CONTAINER/something.png | grep -q 'type: image/png' || echo " [FAIL]" && exit -1
  echo " [TODO]"

  echo -n "delete existing data node "
  $RMCMD $CERT $CONTAINER/something.png  >& /dev/null || echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "view deleted/non-existent data node "
  $LSCMD $CERT $CONTAINER/something.png >& /dev/null && echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "delete non-existent data node "
  $RMCMD $CERT $CONTAINER/something.png >& /dev/null && echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "delete non-empty container "
  $RMDIRCMD $CERT $CONTAINER >& /dev/null || echo " [FAIL]" && exit -1
  $LSCMD $CERT $CONTAINER/something2.png >& /dev/null && echo " [FAIL]" && exit -1
  $LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
  echo " [OK]"

  echo -n "delete empty container "
  $MKDIRCMD $CERT $CONTAINER >& /dev/null || echo " [FAIL]" && exit -1
  $LSCMD $CERT $CONTAINER > /dev/null || echo " [FAIL]" && exit -1
  $RMDIRCMD $CERT $CONTAINER >& /dev/null || echo " [FAIL]" && exit -1
  $LSCMD $CERT $CONTAINER >& /dev/null && echo " [FAIL]" && exit -1
  echo " [OK]"
  echo
  echo "*** test sequence passed for resource $resource ***"
end

echo "*** ALL test sequences passed ***"
date
