# Test the NodeCache class

import unittest

from vos.NodeCache import NodeCache


class TestNodeCache(unittest.TestCase):
    """Test the NodeCache class.
    """

    def test_00_constructor(self):
        """Test basic operation of the NodeCache as a dict."""
        nodeCache = NodeCache()
        self.assertEqual(len(nodeCache.watchedNodes), 0)
        self.assertEqual(len(nodeCache.volatileNodes), 0)

        nodeCache['a'] = 'b'
        nodeCache['b'] = 'c'
        self.assertEqual(nodeCache['a'], 'b')
        self.assertEqual(len(nodeCache), 2)
        self.assertTrue('a' in nodeCache)
        del nodeCache['a']
        self.assertFalse('a' in nodeCache)
        self.assertEqual(len(nodeCache), 1)
        self.assertEqual(nodeCache['a'], None)

    def test_01_volatile(self):
        """ test marking part of the tree as volatile"""

        nodeCache = NodeCache()

        nodeCache['/a/b'] = 'a'
        nodeCache['/a/b/c'] = 'b'
        nodeCache['/a/b/c/'] = 'c'
        nodeCache['/a/b/c/d'] = 'd'
        self.assertTrue('/a/b' in nodeCache)
        self.assertTrue('/a/b/c' in nodeCache)
        self.assertTrue('/a/b/c/' in nodeCache)
        self.assertTrue('/a/b/c/d' in nodeCache)

        with nodeCache.volatile('/a/b/c/') as v:
            self.assertTrue('/a/b' in nodeCache)
            self.assertFalse('/a/b/c' in nodeCache)
            self.assertFalse('/a/b/c/' in nodeCache)
            self.assertFalse('/a/b/c/d' in nodeCache)
            self.assertTrue(v in nodeCache.volatileNodes)
            # Nested with the same path
            with nodeCache.volatile('/a/b/c') as v2:
                self.assertTrue(v in nodeCache.volatileNodes)
                self.assertTrue(v2 in nodeCache.volatileNodes)
            self.assertTrue(v in nodeCache.volatileNodes)
            self.assertFalse(v2 in nodeCache.volatileNodes)

        self.assertFalse(v in nodeCache.volatileNodes)
        self.assertEqual(len(nodeCache.volatileNodes), 0)

        with self.assertRaises(IOError):
            with nodeCache.volatile('/a/b/c') as v:
                self.assertTrue(v in nodeCache.volatileNodes)
                raise IOError('atest')
            self.assertTrue(False)
        self.assertFalse(v in nodeCache.volatileNodes)
        self.assertEqual(len(nodeCache.volatileNodes), 0)

    def test_01_watch(self):
        """Test creating a watch on a node."""

        nodeCache = NodeCache()

        with nodeCache.watch('/a/b/c') as w:
            self.assertTrue(w in nodeCache.watchedNodes)
            self.assertFalse(w.dirty)
            with nodeCache.watch('/a/b/c') as w2:
                self.assertFalse(w2.dirty)
                self.assertTrue(w in nodeCache.watchedNodes)
                self.assertTrue(w2 in nodeCache.watchedNodes)
            self.assertTrue(w in nodeCache.watchedNodes)
            self.assertFalse(w2 in nodeCache.watchedNodes)
        self.assertFalse(w in nodeCache.watchedNodes)
        self.assertFalse(w2 in nodeCache.watchedNodes)
        self.assertEqual(len(nodeCache.watchedNodes), 0)

        with self.assertRaises(IOError):
            with nodeCache.watch('/a/b/c') as w:
                self.assertTrue(w in nodeCache.watchedNodes)
                raise IOError('atest')
            self.assertTrue(False)
        self.assertFalse(w in nodeCache.watchedNodes)
        self.assertEqual(len(nodeCache.watchedNodes), 0)

        with nodeCache.watch('/a/b/c') as w:
            w.insert('d')
        self.assertEqual( nodeCache['/a/b/c'], 'd')
        self.assertEqual(len(nodeCache.watchedNodes), 0)

    def test_02_watchnvolatile(self):
        """test watch and volitile working together."""

        nodeCache = NodeCache()

        with nodeCache.watch('/a/b/c/') as w:
            w.insert('d')
            self.assertEqual( nodeCache['/a/b/c'], 'd')

            # Make a sub-tree volatile. This should not effect the watched
            # directory.
            with nodeCache.volatile('/a/b/c/d'):
                self.assertEqual( nodeCache['/a/b/c'], 'd')

            self.assertTrue('/a/b/c' in nodeCache)
            with nodeCache.volatile('/a/b/c'):
                self.assertFalse('/a/b/c' in nodeCache)
                w.insert('d')
                self.assertFalse('/a/b/c' in nodeCache)
            w.insert('d')
            self.assertFalse('/a/b/c' in nodeCache)

        # Set up a watch and then make a parent node volatile. Caching should be
        # disabled on the watched tree.
        with nodeCache.watch('/a/b/c') as w:
            self.assertFalse('/a/b/c' in nodeCache)
            w.insert('d')

            self.assertTrue('/a/b/c' in nodeCache)
            with nodeCache.volatile('/a/b/'):
                pass
            self.assertFalse('/a/b/c' in nodeCache)
            w.insert('d')
            self.assertFalse('/a/b/c' in nodeCache)

        # Watches are gone, it should now be possible to cache nodes again.
        with nodeCache.watch('/a/b/c') as w:
            self.assertFalse('/a/b/c' in nodeCache)
            w.insert('d')

            self.assertTrue('/a/b/c' in nodeCache)

        # Set up a volatile block first and ensure the cache is disabled.

        with nodeCache.volatile('/a/b/c'):
            self.assertFalse('/a/b/c' in nodeCache)
            with nodeCache.watch('/a/b/c') as w:
                w.insert('d')
                self.assertFalse('/a/b/c' in nodeCache)

            with nodeCache.watch('/a/b/c/d') as w:
                w.insert('d')
                self.assertFalse('/a/b/c/d' in nodeCache)

            with nodeCache.watch('/a/e/f/g') as w:
                w.insert('d')
                self.assertTrue('/a/e/f/g' in nodeCache)


def run():
    suite1 = unittest.TestLoader().loadTestsFromTestCase(TestNodeCache)
    allTests = unittest.TestSuite([suite1])
    return unittest.TextTestRunner(verbosity=2).run(allTests)

if __name__ == "__main__":
    run()



