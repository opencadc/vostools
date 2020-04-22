""" keep track of vospace nodes that have been already been accessed during
the current session."""
import threading
import logging

logger = logging.getLogger('vos')


# logger.setLevel(logging.ERROR)

class NodeCache(dict):
    """ A dictionary like object that provides the ability to look up a
    VOSpace nodes metadata.

    usage:
         # Create a node cache:
         nodeCache = NodeCache()

         with nodeCache.volatile(nodeURI):
            # Do things that might change the node value.
            # The cache will be cleared on entry

         with nodeCache.watch(nodeURI) as watch:
             # do things which shouldn't be cached when the node is
             # volatile.
             watch.insert(node)
             # The node will not be cached if the tree became volatile
             # at any point while the nodeURI was being watched.
    """

    def __init__(self, *args):
        """ Initialize the node cache."""
        dict.__init__(self, args)
        self.lock = threading.Lock()
        self.watched_nodes = []
        self.volatile_nodes = []

    def watch(self, uri):
        """Factory that returns a watch 2015 09 09.39169 for the given uri.

        :param uri: the VOSpace uri to watch
        """
        return self.Watch(self, uri.rstrip('/'))

    def volatile(self, uri):
        """Factory for volatile objects.

        :param uri: the VOSpace uri to tag as volatile
        """
        return self.Volatile(self, uri.rstrip('/'))

    def __missing__(self, key):
        """Attempting to access a non-cached node returns None rather than
           raising an exception.

        :param key: the key in the dict being accessed (i.e. uri)
        """
        return None

    def __setitem__(self, key, value):
        """If an node is directly inserted into the cache, automatically create
           a watch.

        :param key: the uri of the node being inserted into the dictionary.
        :param value:  the actual node object that will be stored.
        """
        with self.watch(key) as w:
            w.insert(value)

    def __getitem__(self, key):
        return dict.__getitem__(self, key.rstrip('/'))

    def __contains__(self, key):
        return dict.__contains__(self, key.rstrip('/'))

    class Volatile(object):
        """ Objects that mark a code segment where a uri is volatile and
            the Node in the cache shouldn't be used."""

        def __init__(self, node_cache, uri):
            """

            :param node_cache: the NOdeCache object this Volatile object is in.
            :type node_cache: NodeCache
            :param uri: the VOSpace uri that references this node in the
            NodeCache
            :type uri: str
            """
            self.node_cache = node_cache
            self.uri = uri.rstrip('/')

        def __enter__(self):
            """ Mark any sub-trees being watched as being dirty
                add to self.nodeCache.volatileNodes.
                Remove any cached nodes in the volatile subtree.
            """

            with self.node_cache.lock:
                # Add this volatile object to a list of all active volatile
                # objects.
                self.node_cache.volatile_nodes.append(self)

                # Remove any cached nodes in the volatile sub-tree.
                for uri in list(self.node_cache.keys()):
                    if uri.startswith(self.uri):
                        del self.node_cache[uri]

                # Clear the parent node as well to force an update
                parent = self.uri[:self.uri.rfind("/")]
                self.node_cache.pop(parent, None)

                # Mark any watched nodes in the volatile sub-tree dirty
                for watchedNode in self.node_cache.watched_nodes:
                    if watchedNode.uri.startswith(self.uri) or\
                            (watchedNode.uri == parent):
                        watchedNode.dirty = True

            return self

        def __exit__(self, exc_type, exc_value, traceback):
            """ Remove this volitile object from the list of active volatiles.
            """
            with self.node_cache.lock:
                self.node_cache.volatile_nodes.remove(self)

    class Watch(object):
        """ Objects that mark a code segment where a node has been read from
            vospace, and is intended to be cached.
        """

        def __init__(self, node_cache, uri):
            """

            :param node_cache: the NodeCache object containing the uri to
            watch.
             :type node_cache: NodeCache
            :param uri: the uri in the NodeCache that will be watched.
            :type uri: str
            """
            self.node_cache = node_cache
            self.uri = uri
            self.dirty = False

        def __enter__(self):
            with self.node_cache.lock:
                # Add this watch object to the list of active watch objects.
                self.node_cache.watched_nodes.append(self)

                # Check to see if this watch object is in an existing volatile
                # tree. If it is, mark this watch object as dirty.
                for this_volatile in self.node_cache.volatile_nodes:
                    if self.uri.startswith(this_volatile.uri):
                        self.dirty = True
                        return self
            return self

        def __exit__(self, exc_type, exc_value, traceback):
            with self.node_cache.lock:
                self.node_cache.watched_nodes.remove(self)

        def insert(self, value):
            """ Insert an value, likely node object, into the cache, but only
            if the watch is not dirty."""
            if not self.dirty:
                # noinspection PyCallByClass
                dict.__setitem__(self.node_cache, self.uri, value)
