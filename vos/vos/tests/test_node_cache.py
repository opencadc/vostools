from __future__ import (absolute_import, division, print_function,
                        unicode_literals)
import unittest
from vos.node_cache import NodeCache


class TestNodeCache(unittest.TestCase):
    """Test the NodeCache class.
    """

    def test_00_constructor(self):
        """Test basic operation of the NodeCache as a dict."""
        node_cache = NodeCache()
        self.assertEqual(len(node_cache.watched_nodes), 0)
        self.assertEqual(len(node_cache.volatile_nodes), 0)

        node_cache['a'] = 'b'
        node_cache['b'] = 'c'
        self.assertEqual(node_cache['a'], 'b')
        self.assertEqual(len(node_cache), 2)
        self.assertTrue('a' in node_cache)
        del node_cache['a']
        self.assertFalse('a' in node_cache)
        self.assertEqual(len(node_cache), 1)
        self.assertEqual(node_cache['a'], None)

    def test_01_volatile(self):
        """ test marking part of the tree as volatile"""

        node_cache = NodeCache()

        node_cache['/a/b'] = 'a'
        node_cache['/a/b/c'] = 'b'
        node_cache['/a/b/c/'] = 'c'
        node_cache['/a/b/c/d'] = 'd'
        self.assertTrue('/a/b' in node_cache)
        self.assertTrue('/a/b/c' in node_cache)
        self.assertTrue('/a/b/c/' in node_cache)
        self.assertTrue('/a/b/c/d' in node_cache)

        with node_cache.volatile('/a/b/c/') as v:
            self.assertTrue('/a/b' not in node_cache)
            self.assertFalse('/a/b/c' in node_cache)
            self.assertFalse('/a/b/c/' in node_cache)
            self.assertFalse('/a/b/c/d' in node_cache)
            self.assertTrue(v in node_cache.volatile_nodes)
            # Nested with the same path
            with node_cache.volatile('/a/b/c') as v2:
                self.assertTrue(v in node_cache.volatile_nodes)
                self.assertTrue(v2 in node_cache.volatile_nodes)
            self.assertTrue(v in node_cache.volatile_nodes)
            self.assertFalse(v2 in node_cache.volatile_nodes)

        self.assertFalse(v in node_cache.volatile_nodes)
        self.assertEqual(len(node_cache.volatile_nodes), 0)

        with self.assertRaises(IOError):
            with node_cache.volatile('/a/b/c') as v:
                self.assertTrue(v in node_cache.volatile_nodes)
                raise IOError('atest')
            self.assertTrue(False)
        self.assertFalse(v in node_cache.volatile_nodes)
        self.assertEqual(len(node_cache.volatile_nodes), 0)

    def test_01_watch(self):
        """Test creating a watch on a node."""

        node_cache = NodeCache()

        with node_cache.watch('/a/b/c') as w:
            self.assertTrue(w in node_cache.watched_nodes)
            self.assertFalse(w.dirty)
            with node_cache.watch('/a/b/c') as w2:
                self.assertFalse(w2.dirty)
                self.assertTrue(w in node_cache.watched_nodes)
                self.assertTrue(w2 in node_cache.watched_nodes)
            self.assertTrue(w in node_cache.watched_nodes)
            self.assertFalse(w2 in node_cache.watched_nodes)
        self.assertFalse(w in node_cache.watched_nodes)
        self.assertFalse(w2 in node_cache.watched_nodes)
        self.assertEqual(len(node_cache.watched_nodes), 0)

        with self.assertRaises(IOError):
            with node_cache.watch('/a/b/c') as w:
                self.assertTrue(w in node_cache.watched_nodes)
                raise IOError('atest')
            self.assertTrue(False)
        self.assertFalse(w in node_cache.watched_nodes)
        self.assertEqual(len(node_cache.watched_nodes), 0)

        with node_cache.watch('/a/b/c') as w:
            w.insert('d')
        self.assertEqual(node_cache['/a/b/c'], 'd')
        self.assertEqual(len(node_cache.watched_nodes), 0)

    def test_02_watchnvolatile(self):
        """test watch and volatile working together."""

        node_cache = NodeCache()

        with node_cache.watch('/a/b/c/') as w:
            w.insert('d')
            self.assertEqual(node_cache['/a/b/c'], 'd')

            # Make a sub-tree volatile including the parent directory
            with node_cache.volatile('/a/b/c/d'):
                self.assertEqual(node_cache['/a/b/c'], None)

            w.dirty = False
            w.insert('d')
            self.assertTrue('/a/b/c' in node_cache)
            with node_cache.volatile('/a/b/c'):
                self.assertFalse('/a/b/c' in node_cache)
                w.insert('d')
                self.assertFalse('/a/b/c' in node_cache)
            w.insert('d')
            self.assertFalse('/a/b/c' in node_cache)

        # Set up a watch and then make a parent node volatile. Caching should
        # be disabled on the watched tree.
        with node_cache.watch('/a/b/c') as w:
            self.assertFalse('/a/b/c' in node_cache)
            w.insert('d')

            self.assertTrue('/a/b/c' in node_cache)
            with node_cache.volatile('/a/b/'):
                pass
            self.assertFalse('/a/b/c' in node_cache)
            w.insert('d')
            self.assertFalse('/a/b/c' in node_cache)

        # Watches are gone, it should now be possible to cache nodes again.
        with node_cache.watch('/a/b/c') as w:
            self.assertFalse('/a/b/c' in node_cache)
            w.insert('d')

            self.assertTrue('/a/b/c' in node_cache)

        # Set up a volatile block first and ensure the cache is disabled.

        with node_cache.volatile('/a/b/c'):
            self.assertFalse('/a/b/c' in node_cache)
            with node_cache.watch('/a/b/c') as w:
                w.insert('d')
                self.assertFalse('/a/b/c' in node_cache)

            with node_cache.watch('/a/b/c/d') as w:
                w.insert('d')
                self.assertFalse('/a/b/c/d' in node_cache)

            with node_cache.watch('/a/e/f/g') as w:
                w.insert('d')
                self.assertTrue('/a/e/f/g' in node_cache)


def run():
    suite1 = unittest.TestLoader().loadTestsFromTestCase(TestNodeCache)
    all_tests = unittest.TestSuite([suite1])
    return unittest.TextTestRunner(verbosity=2).run(all_tests)
