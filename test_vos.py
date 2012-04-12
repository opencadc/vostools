# Test the vos module

class TestVOS:
    def test_listdir(self,vossetup):
        client=vossetup.client()
        testDir="vos:jkavelaars/testDir"
        testList=['bigFile.bin','fileOne.txt','fileTwo.txt','md5_sums.txt']
        md5Dict={'bigFile.bin': '3f8fb6e2783bae21b9de4b2c90559edc',
                 'fileOne.txt': 'b602183573352abf933bc7ca85fd0629',
                 'fileTwo.txt': '3b0ea049a78d4a349993eeeca0c7b508'}
        dirlist=client.listdir(testDir)
        dirlist.sort()
        testList.sort()
        assert dirlist==testList


		
