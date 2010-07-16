CLASSPATH=~/work/lib
echo CLASSPATH: $CLASSPATH
cur=`pwd`
cd ~/work/lib
java \
	-Dca.nrc.cadc.auth.BasicX509TrustManager.trust=true \
	-Dca.nrc.cadc.reg.client.RegistryClient.local=true \
	-jar cadcVOSClient.jar -d \
	--cert=/home/cadc/zhangsa/.globus/szProxy.crt \
	--key=/home/cadc/zhangsa/.globus/szProxy.key \
	$@
cd $cur

   
   

