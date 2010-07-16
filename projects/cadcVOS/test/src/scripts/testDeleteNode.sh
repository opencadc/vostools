uniqueStr=`date +%b%d_%H.%M_%N | cut -c 1-15`
echo $uniqueStr

./runClient.sh "--create --target=vos://cadc.nrc.ca!vospace/zhangsa/$uniqueStr"

echo Node Created: vos://cadc.nrc.ca!vospace/zhangsa/$uniqueStr
echo Now delete it...

./runClient.sh "--delete --target=vos://cadc.nrc.ca!vospace/zhangsa/$uniqueStr"
