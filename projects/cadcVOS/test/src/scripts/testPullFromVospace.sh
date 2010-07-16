uniqueStr=`date +%b%d_%H.%M_%N | cut -c 1-15`
echo $uniqueStr

echo $uniqueStr > /tmp/$uniqueStr
ls -l /tmp >> /tmp/$uniqueStr

src=/tmp/$uniqueStr
echo src="$src"

dest=vos://cadc.nrc.ca!vospace/zhangsa/$uniqueStr
echo dest="$dest"

# do a push to vospace first
./runClient.sh "--copy --src=$src --dest=$dest"

echo '******************************' 
echo 
echo do a pull from vospace, to download the one just uploaded
echo 
echo do download now
echo 
echo '******************************' 
echo 

src=$dest
echo src="$src"

uniqueStr2=`date +%b%d_%H.%M_%N | cut -c 1-15`
dest=/tmp/$uniqueStr2
echo dest="$dest"

./runClient.sh "--copy --src=$src --dest=$dest"
   