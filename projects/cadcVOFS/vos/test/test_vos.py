# Test the vos module
import pytest

class TestVOS:

    def setup_class(self):
        self.testDir="vos:jkavelaars/testDir/1312701p.ccd00"
        self.testMD5={'.psfed': 'd41d8cd98f00b204e9800998ecf8427e', 'psf.dat': '37d8b1dac2a2cb800050e7d6d462d3dd', 'catalog.log.gz': 'eb8a3844e5a0d46c29f71971a76e788c', '.dbstuff': 'd41d8cd98f00b204e9800998ecf8427e', 'planted.list': '0331be244f6345787f474356af50c7c7', 'miniback.fz': 'eb54b000c6b06622225134d32567b466', 'psftuple.list': '941a69e60f25dd8bb69616f1407bf297', 'planted.fz': 'd35cb167065e02b61682c402f47fc0fa', 'all_coords.dat': '90bf0bbe4694f4a63cafb97c209c8ebb', 'match_usno.dat': '3e181e623acd55986e62197969044206', 'weight.fz': '7eed138dbdebbe5fe6284f5e82f6d47e', '.cataloged': 'd41d8cd98f00b204e9800998ecf8427e', 'cosmic.fits.gz': '9ef62158b68e31d71781daede446045d', 'prepare.log.gz': 'd0725078e37a7e5bb68150f0c351ac97', 'segmentation.fits.gz': 'b077c071335236bfa02f0d1af6549627', 'calibrated.fz': '6dde26de6f59706ed2129b48fa80902d', 'Manifest': 'c1e109dd0f4cb2e9d81a8476a6ed1b31', 'standalone_stars.list': '9ffd6748e4e32f8fb1c2033efa3c40e6', 'planted.log.gz': '18e1a4abf142b98f270c6113aa3dc8eb', '.apered': 'd41d8cd98f00b204e9800998ecf8427e', 'se.list': 'ab45f0971ab1ac1b3af8433b7ca00f36', 'aperse.list': '53f3c8fa612fac66fb177fc4b0eaef0d', '.planted': 'd41d8cd98f00b204e9800998ecf8427e', 'satur.fits.gz': '78db3a7bc49a2c84f09d1f01fd3eeafc', 'psfstars.list': '013e3685f5103d2f39693fb0d3e1e746', '.wcsed': 'd41d8cd98f00b204e9800998ecf8427e'}
        self.testFile='planted.fz'


    def test_download(self,tmpdir,vossetup):
        client=vossetup.client()
        dest=tmpdir.dirname
        with pytest.raises(IOError):
	   client.copy(self.testDir+"/"+self.testFile,dest)
	client.copy(self.testDir+"/"+self.testFile,dest+"/"+self.testFile)
        import hashlib
        md5=hashlib.md5()
        md5.update(file(dest+"/"+self.testFile).read())
        assert md5.hexdigest() ==  client.getNode(self.testDir+"/"+self.testFile).props.get('MD5')
        
    def test_upload(self,vossetup):
	import random,os
        import hashlib
	md5=hashlib.md5()
	client=vossetup.client()
	dest=self.testDir+"/../"+str(random.random())
	w=client.open(dest,os.O_WRONLY) 
        len=0
	while len<1000:
	   line=str(random.random())+"\n"
	   md5.update(line)
	   len += w.write(line)
	w.close()
        assert md5.hexdigest() == client.getNode(dest).props.get('MD5')
	assert client.delete(dest)
	
	      

    def test_listdir(self,vossetup):
        client=vossetup.client()
	assert client.isdir(self.testDir) == True
        for fname in client.listdir(self.testDir):
	    assert client.access(self.testDir+"/"+fname) == True
            assert fname in self.testMD5.keys()
            assert client.getNode(self.testDir+"/"+fname).props.get('MD5') == self.testMD5[fname]

		
