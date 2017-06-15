from __future__ import (absolute_import, division, print_function,
                        unicode_literals)
from six.moves.builtins import str
from six.moves.reprlib import repr, aRepr
import os
import unittest
from vofs.CacheMetaData import CacheMetaData


class TestCacheMetaData(unittest.TestCase):
    TEST_CACHE_PATH = "/tmp/.cacheMetadataTest/"

    def setUp(self):
        self.cleanupCacheMetadataDir()

    def tearDown(self):
        self.cleanupCacheMetadataDir()

    def cleanupCacheMetadataDir(self):
        for root, dirs, files in os.walk(TestCacheMetaData.TEST_CACHE_PATH, topdown=False):
            for name in files:
                os.remove(os.path.join(root, name))
            for name in dirs:
                os.rmdir(os.path.join(root, name))

    def testPersist(self):

        # create file
        file1 = CacheMetaData(TestCacheMetaData.TEST_CACHE_PATH + "file1", 10,
                              0x2345, 1024)
        self.assertEquals(TestCacheMetaData.TEST_CACHE_PATH +
                          "file1", file1.metaDataFile)
        self.assertEquals(10, file1.bitmap.length())
        file1.setReadBlocks(2, 5)
        self.assertEquals(4, file1.bitmap.count_bits())
        self.assertEquals("0011110000", str(file1.bitmap))
        self.assertEquals(0x2345, file1.md5sum)
        self.assertEquals(1024, file1.size)

        file1.persist()

        file2 = CacheMetaData(TestCacheMetaData.TEST_CACHE_PATH + "file1", 10,
                              0x2345, 1024)
        self.assertEquals(4, file2.bitmap.count_bits())
        self.assertEquals("0011110000", str(file2.bitmap))
        self.assertEquals(0x2345, file2.md5sum)
        self.assertEquals(1024, file2.size)

        # same file name, different md5 => reset bitmap
        file3 = CacheMetaData(TestCacheMetaData.TEST_CACHE_PATH + "file1", 10,
                              0x23456, 1025)
        self.assertEquals(0, file3.bitmap.count_bits())
        self.assertEquals("0000000000", str(file3.bitmap))
        self.assertEquals(0x23456, file3.md5sum)
        self.assertEquals(1025, file3.size)

        CacheMetaData.deleteCacheMetaData(
            TestCacheMetaData.TEST_CACHE_PATH + "file1")
        file4 = CacheMetaData(TestCacheMetaData.TEST_CACHE_PATH + "file1", 10,
                              0x2345, 1025)
        self.assertEquals(0, file4.bitmap.count_bits())
        self.assertEquals("0000000000", str(file4.bitmap))
        self.assertEquals(0x2345, file4.md5sum)
        self.assertEquals(1025, file4.size)

        file4.setReadBlocks(-8, -5)
        self.assertEquals("0011110000", str(file4.bitmap))

    def testRange(self):
        """ To test getRange functionality """

        # first file
        file1 = CacheMetaData(
            TestCacheMetaData.TEST_CACHE_PATH + "file1", 10, 0x2345, 1025)
        self.assertEquals(TestCacheMetaData.TEST_CACHE_PATH +
                          "file1", file1.metaDataFile)
        self.assertEquals(10, file1.bitmap.length())
        self.assertEquals(0x2345, file1.md5sum)
        self.assertEquals(1025, file1.size)

        # bitvector should be "0000000000"
        self.assertEquals(0, file1.getNumReadBlocks())
        self.assertEquals(10, file1.bitmap.length())

        file1.setReadBlock(1)
        self.assertEquals(1, file1.getBit(1))
        self.assertEquals(1, file1.bitmap.count_bits())
        self.assertEquals("0100000000", str(file1.bitmap))

        file1.setReadBlock(5)
        self.assertEquals(1, file1.getBit(5))
        self.assertEquals(2, file1.bitmap.count_bits())
        self.assertEquals(5, file1.getNextReadBlock(2))
        self.assertEquals(5, file1.getNextReadBlock(5))
        self.assertEquals("0100010000", str(file1.bitmap))

        # perform some tests on 0100010
        self.assertEquals((0, 0), file1.getRange(0, 0))
        self.assertEquals((2, 4), file1.getRange(2, 5))
        # same thing using negative index
        self.assertEquals((2, 4), file1.getRange(2, -5))
        self.assertEquals((2, 4), file1.getRange(-8, -5))
        self.assertEquals((2, 4), file1.getRange(-8, 5))

        file1.setReadBlock(2)
        file1.setReadBlock(4)
        self.assertEquals("0110110000", str(file1.bitmap))
        self.assertEquals((3, 3), file1.getRange(2, 5))

        file1.setReadBlock(3)
        self.assertEquals("0111110000", str(file1.bitmap))
        self.assertEquals((None, None), file1.getRange(2, 5))

        expected = 0
        try:
            file1.getRange(8, -7)
        except ValueError:
            expected = 1

        self.assertEquals(1, expected)

        expected = 0
        try:
            file1.setReadBlocks(8, -7)
        except ValueError:
            expected = 1

        file1_repr = str("CacheMetaData(metaDataFile='{}file1', " \
            "blocks=10, md5sum=9029, size=1025)".format(
                TestCacheMetaData.TEST_CACHE_PATH))
        aRepr.maxother = len(file1_repr) # avoid repr limitting the size of the return representation
        self.assertEquals(str(file1_repr), repr(file1))

        self.assertEquals(1, expected)
        file1.persist()
        self.assertTrue(os.path.exists(
            TestCacheMetaData.TEST_CACHE_PATH + "file1"))
        file1.delete()
        self.assertTrue(not os.path.exists(
            TestCacheMetaData.TEST_CACHE_PATH + "file1"))

        file2 = CacheMetaData(TestCacheMetaData.TEST_CACHE_PATH + "/test/file1",
                              10, 0x2345, 1024)
        self.assertEquals(TestCacheMetaData.TEST_CACHE_PATH +
                          "/test/file1", file2.metaDataFile)
        self.assertEquals(10, file2.bitmap.length())
        self.assertEquals(0x2345, file2.md5sum)
        file2.persist()
        file3 = CacheMetaData(TestCacheMetaData.TEST_CACHE_PATH + "/test/file1",
                              10, 0x2345, 1025)
        self.assertEquals(TestCacheMetaData.TEST_CACHE_PATH +
                          "/test/file1", file3.metaDataFile)
        self.assertEquals(10, file3.bitmap.length())
        self.assertEquals(0x2345, file3.md5sum)


def run():
    suite = unittest.TestLoader().loadTestsFromTestCase(TestCacheMetaData)
    return unittest.TextTestRunner(verbosity=2).run(suite)

if __name__ == '__main__':
    run()


if __name__ == '__main__':
    unittest.main()
