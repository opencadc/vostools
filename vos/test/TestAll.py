import Test_vos
import TestNodeCache
import TestCadcCache
import Test_vofs
import Test_vofile
import TestCacheMetaData

if not Test_vos.run().wasSuccessful(): 
    print "FAIL"
    exit()
if not TestNodeCache.run().wasSuccessful():
    print "FAIL"
    exit()
if not TestCadcCache.run().wasSuccessful():
    print "FAIL"
    exit()
if not Test_vofs.run().wasSuccessful():
    print "FAIL"
    exit()
if not Test_vofile.run().wasSuccessful():
    print "FAIL"
    exit()
if not TestCacheMetaData.run().wasSuccessful():
    print "FAIL"
    exit()
print "SUCCESS"
