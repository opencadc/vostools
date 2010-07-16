uniqueStr=`date +%b%d_%H.%M_%N | cut -c 1-15`
echo $uniqueStr

echo $uniqueStr > /tmp/$uniqueStr
ls -l /tmp >> /tmp/$uniqueStr

src=/tmp/$uniqueStr
echo src="$src"

dest=vos://cadc.nrc.ca!vospace/zhangsa/$uniqueStr
echo dest="$dest"

./runClient.sh "--copy --src=$src --dest=$dest"
