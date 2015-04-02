"""A set of Python Classes for connecting to and interacting with a VOSpace
   service.

   Connections to VOSpace are made using a SSL X509 certificat which is
   stored in a .pem file. The certificate is supplied by the user or by the
   CADC credential server
"""

import copy
import errno
import fnmatch
import hashlib
from contextlib import nested
from cStringIO import StringIO
import html2text
import httplib
import logging
import mimetypes
import os
import re
import ssl
import stat
import string
import sys
import time
import urllib
import urlparse
from xml.etree import ElementTree
from copy import deepcopy
from NodeCache import NodeCache

try:
    _unicode = unicode
except NameError:
    # If Python is built without Unicode support, the unicode type
    # will not exist. Fake one.
    class _unicode(object):
        pass

from __version__ import version

logger = logging.getLogger('vos')
#logger.setLevel(logging.ERROR)
connection_count_logger = logging.getLogger('connections')
#connection_count_logger.setLevel(logging.ERROR)

if sys.version_info[1] > 6:
    connection_count_logger.addHandler(logging.NullHandler())
    logger.addHandler(logging.NullHandler())

BUFSIZE = 8388608  # Size of read/write buffer
MAX_RETRY_DELAY = 128  # maximum delay between retries
DEFAULT_RETRY_DELAY = 30  # start delay between retries when Try_After not
# specified by server
MAX_RETRY_TIME = 900  # maximum time for retries before giving up...
CONNECTION_TIMEOUT = 30  # seconds before HTTP connection should drop, should be less than DAEMON timeout in vofs
SERVER = os.getenv('VOSPACE_WEBSERVICE', 'www.canfar.phys.uvic.ca')
CADC_GMS_PREFIX = "ivo://cadc.nrc.ca/gms#"
VOSPACE_CERTFILE = os.getenv("VOSPACE_CERTFILE", os.path.join(os.getenv("HOME","."), '.ssl/cadcproxy.pem'))
VOSPACE_ARCHIVE = os.getenv("VOSPACE_ARCHIVE", "vospace")

HEADER_DELEG_TOKEN = 'X-CADC-DelegationToken'
CONNECTION_COUNTER = 0


class URLparse:
    """ Parse out the structure of a URL.

    There is a difference between the 2.5 and 2.7 version of the
    urlparse.urlparse command, so here I roll my own...
    """

    def __init__(self, url):
        self.scheme = None
        self.netloc = None
        self.args = None
        self.path = None
        m = re.match("(^(?P<scheme>[a-zA-Z]*):)?(//(?P<netloc>[^/]*))?"
                     "(?P<path>/?[^?]*)?(?P<args>\?.*)?", url)
        self.scheme = m.group('scheme')
        self.netloc = m.group('netloc')
        self.path = (m.group('path') is not None and m.group('path')) or ''
        self.args = (m.group('args') is not None and m.group('args')) or ''

    def __str__(self):
        return "[scheme: %s, netloc: %s, path: %s]" % (self.scheme,
                                                       self.netloc, self.path)


class Connection:
    """Class to hold and act on the X509 certificate"""

    def __init__(self, vospace_certfile=None, vospace_token=None,
                 http_debug=False):
        """Setup the Certificate for later usage

        cerdServerURL -- the location of the cadc proxy certificate server
        vospace_certfile -- where to store the certificate, if None then
                         ${HOME}/.ssl or a temporary filename
        vospace_token -- token string (alternative to vospace_certfile)
        http_debug -- set True to generate httplib debug statements

        The user must supply a valid certificate or connection will be 'anonymous'.
        """
        self.http_debug = http_debug
        self.logger = logging.getLogger('http')
        if sys.version_info[1] > 6:
            self.logger.addHandler(logging.NullHandler())
        self.logger.setLevel(logging.ERROR)

        # # tokens trump certs. We should only ever have token or certfile
        ## set in order to avoid confusion.
        self.vospace_certfile = None
        self.vospace_token = vospace_token
        if self.vospace_token is None:
            ## allow anonymous access if no certfile specified
            if vospace_certfile is not None and not os.access(vospace_certfile, os.F_OK):
                self.logger.critical(
                    "Could not read security certificate at {0}.  Reverting to anonymous.".format(vospace_certfile))
                vospace_certfile = None
            self.vospace_certfile = vospace_certfile

    def get_connection(self, url):
        """Create an HTTPSConnection object and return.  Uses the client
        certificate if None given.

        uri  -- a VOSpace uri (vos://cadc.nrc.ca~vospace/path)
        """

        global CONNECTION_COUNTER
        CONNECTION_COUNTER += 1

        parts = URLparse(url)
        connection_count_logger.debug("Opening connection {0} to {1}://{2} using {3}".format(CONNECTION_COUNTER,
                                                                                             parts.scheme, parts.netloc,
                                                                                             self.vospace_certfile))

        try:
            if parts.scheme == "https" and self.vospace_certfile is not None:
                connection = httplib.HTTPSConnection(parts.netloc,
                                                     key_file=self.vospace_certfile,
                                                     cert_file=self.vospace_certfile,
                                                     timeout=CONNECTION_TIMEOUT)
            else:
                connection = httplib.HTTPConnection(parts.netloc,
                                                    timeout=CONNECTION_TIMEOUT)
        except httplib.NotConnected as e:
            self.logger.error("HTTP connection to %s failed \n" % parts.netloc)
            self.logger.error("%s \n" % (str(e)))
            raise OSError(errno.ECONNREFUSED, "VOSpace connection failed",
                          parts.netloc)

        if self.http_debug:
            connection.set_debuglevel(1)

        # # Try to open this connection.
        start_time = time.time()
        self.logger.debug("Opening the connection")

        while True:
            try:
                self.logger.debug("Opening connection.")
                connection.connect()
                break
            except httplib.HTTPException as http_exception:
                self.logger.critical("%s" % (str(http_exception)))
                self.logger.critical("Retrying connection for {0} seconds".format(MAX_RETRY_TIME))
                if time.time() - start_time > MAX_RETRY_TIME:
                    raise http_exception
            except Exception as e:
                if getattr(e, 'errno', errno.EFAULT) == errno.ENOEXEC:
                    self.logger.error("Failed to connect to VOSpace: No network available?")
                else:
                    self.logger.error(str(e))
                break

        return connection


class Node:
    """A VOSpace node"""

    IVOAURL = "ivo://ivoa.net/vospace/core"
    CADCURL = "ivo://cadc.nrc.ca/vospace/core"
    ISLOCKED = CADCURL + "#islocked"

    VOSNS = "http://www.ivoa.net/xml/VOSpace/v2.0"
    XSINS = "http://www.w3.org/2001/XMLSchema-instance"
    TYPE = '{%s}type' % XSINS
    NODES = '{%s}nodes' % VOSNS
    NODE = '{%s}node' % VOSNS
    PROTOCOL = '{%s}protocol' % VOSNS
    PROPERTIES = '{%s}properties' % VOSNS
    PROPERTY = '{%s}property' % VOSNS
    ACCEPTS = '{%s}accepts' % VOSNS
    PROVIDES = '{%s}provides' % VOSNS
    ENDPOINT = '{%s}endpoint' % VOSNS
    TARGET = '{%s}target' % VOSNS
    DATA_NODE = "vos:DataNode"
    LINK_NODE = "vos:LinkNode"
    CONTAINER_NODE = "vos:ContainerNode"

    def __init__(self, node, node_type=None, properties=None, subnodes=None):
        """Create a Node object based on the DOM passed to the init method

        if node is a string then create a node named node of nodeType with
        properties
        """
        if not subnodes:
            subnodes = []
        if not properties:
            properties = {}

        if node_type is None:
            node_type = Node.DATA_NODE

        if type(node) == unicode or type(node) == str:
            node = self.create(node, node_type, properties, subnodes=subnodes)

        if node is None:
            raise LookupError("no node found or created?")

        self.uri = None
        self.name = None
        self.node = node
        self.target = None
        self.node.set('xmlns:vos', self.VOSNS)
        self.type = None
        self.props = {}
        self.attr = {}
        self.xattr = {}
        self._nodeList = None
        self.update()

    def __eq__(self, node):
        if not isinstance(node, Node):
            return False

        return self.props == node.props

    def update(self):
        """Update the convience links of this node as we update the xml file"""

        self.type = self.node.get(Node.TYPE)
        if self.type is None:
            # logger.debug("Node type unknown, no node created")
            return None
        if self.type == "vos:LinkNode":
            self.target = self.node.findtext(Node.TARGET)

        self.uri = self.node.get('uri')
        self.name = os.path.basename(self.uri)
        for propertiesNode in self.node.findall(Node.PROPERTIES):
            self.setProps(propertiesNode)
        self.isPublic = False
        if self.props.get('ispublic', 'false') == 'true':
            self.isPublic = True
        self.isLocked = False
        if self.props.get(Node.ISLOCKED, 'false') == 'true':
            self.isLocked = True
        self.groupwrite = self.props.get('groupwrite', '')
        self.groupread = self.props.get('groupread', '')
        self.setattr()
        self.setxattr()

    def set_property(self, key, value):
        """Given a dictionary of props build a properties sub-element"""
        properties = self.node.find(Node.PROPERTIES)
        uri = "%s#%s" % (Node.IVOAURL, key)
        ElementTree.SubElement(properties, Node.PROPERTY,
                               attrib={'uri': uri, 'readOnly': 'false'}).text = value

    def __str__(self):
        class dummy:
            pass

        data = []
        file = dummy()
        file.write = data.append
        ElementTree.ElementTree(self.node).write(file, encoding="UTF-8")
        return "".join(data)

    def setattr(self, attr={}):
        """return a dictionary of attributes associated with the file stored
        at node

        These attributes are determind from the node on VOSpace.
        """
        # # Get the flags for file mode settings.

        self.attr = {}

        ## Only one date provided by VOSpace, so use this as all possible
        ## dates.
        sdate = self.props.get('date', None)
        atime = time.time()
        if not sdate:
            mtime = atime
        else:
            ### mktime is expecting a localtime but we're sending a UT date, so
            ### some correction will be needed
            mtime = time.mktime(time.strptime(sdate[0:-4],
                                              '%Y-%m-%dT%H:%M:%S'))
            mtime = mtime - time.mktime(time.gmtime()) + \
                    time.mktime(time.localtime())
        self.attr['st_ctime'] = attr.get('st_ctime', mtime)
        self.attr['st_mtime'] = attr.get('st_mtime', mtime)
        self.attr['st_atime'] = atime

        ## set the MODE by orring together all flags from stat
        st_mode = 0

        st_nlink = 1
        if self.type == 'vos:ContainerNode':
            st_mode |= stat.S_IFDIR
            st_nlink = max(2, len(self.getInfoList()) + 2)
            # if getInfoList length is < 0 we have a problem elsewhere, so above hack solves that problem.
        elif self.type == 'vos:LinkNode':
            st_mode |= stat.S_IFLNK
        else:
            st_mode |= stat.S_IFREG
        self.attr['st_nlink'] = st_nlink

        ## Set the OWNER permissions
        ## All files are read/write/execute by owner...
        st_mode |= stat.S_IRUSR | stat.S_IWUSR | stat.S_IXUSR

        ## Set the GROUP permissions
        if self.props.get('groupwrite', "NONE") != "NONE":
            st_mode |= stat.S_IWGRP
        if self.props.get('groupread', "NONE") != "NONE":
            st_mode |= stat.S_IRGRP
            st_mode |= stat.S_IXGRP

        ## Set the OTHER permissions
        if self.props.get('ispublic', 'false') == 'true':
            ## If you can read the file then you can execute too.
            ## Public does NOT mean writeable.  EVER
            st_mode |= stat.S_IROTH | stat.S_IXOTH

        self.attr['st_mode'] = attr.get('st_mode', st_mode)

        ## We set the owner and group bits to be those of the currently
        ## running process.
        ## This is a hack since we don't have an easy way to figure these out.
        ## TBD!
        self.attr['st_uid'] = attr.get('st_uid', os.getuid())
        self.attr['st_gid'] = attr.get('st_uid', os.getgid())

        st_size = int(self.props.get('length', 0))
        self.attr['st_size'] = st_size > 0 and st_size or 0

        self.attr['st_blocks'] = self.attr['st_size'] / 512

    def setxattr(self, attrs={}):
        """Initialize the attributes using the properties sent with the node"""
        for key in self.props:
            if key in Client.vosProperties:
                continue
            self.xattr[key] = self.props[key]
        return

    def chwgrp(self, group):
        """Set the groupwrite value for this node"""
        if (group != None) and (group.count(CADC_GMS_PREFIX) > 3):
            raise AttributeError("Exceeded max of 4 write groups: " +
                                 group.replace(CADC_GMS_PREFIX, ""))
        self.groupwrite = group
        return self.changeProp('groupwrite', group)

    def chrgrp(self, group):
        """Set the groupread value for this node"""
        if (group != None) and (group.count(CADC_GMS_PREFIX) > 3):
            raise AttributeError("Exceeded max of 4 read groups: " +
                                 group.replace(CADC_GMS_PREFIX, ""))
        self.groupread = group
        return self.changeProp('groupread', group)

    def setPublic(self, value):
        # logger.debug("Setting value of ispublic to %s" % (str(value)))
        return self.changeProp('ispublic', value)

    def fix_prop(self, prop):
        """Check if prop is a well formed uri and if not then make into one"""
        (url, tag) = urllib.splittag(prop)
        if tag is None and url in ['title',
                                   'creator',
                                   'subject',
                                   'description',
                                   'publisher',
                                   'contributer',
                                   'date',
                                   'type',
                                   'format',
                                   'identifier',
                                   'source',
                                   'language',
                                   'relation',
                                   'coverage',
                                   'rights',
                                   'availableSpace',
                                   'groupread',
                                   'groupwrite',
                                   'publicread',
                                   'quota',
                                   'length',
                                   'MD5',
                                   'mtime',
                                   'ctime',
                                   'ispublic']:
            tag = url
            url = Node.IVOAURL
            prop = url + "#" + tag

        parts = URLparse(url)
        if parts.path is None or tag is None:
            raise ValueError("Invalid VOSpace property uri: %s" % (prop))

        return prop

    def setProp(self):
        """Build the XML for a given node"""

    def changeProp(self, key, value):
        """Change the node property 'key' to 'value'.

        Return 1 if changed.

        This function should be split into 'set' and 'delete'
        """
        # logger.debug("Before change node XML\n %s" % (self))
        uri = self.fix_prop(key)
        changed = 0
        found = False
        properties = self.node.findall(Node.PROPERTIES)
        for props in properties:
            for prop in props.findall(Node.PROPERTY):
                if uri != prop.attrib.get('uri', None):
                    continue
                found = True
                changed = 1
                if value is None:
                    ## this is actually a delete property
                    prop.attrib['xsi:nil'] = 'true'
                    prop.attrib["xmlns:xsi"] = Node.XSINS
                    prop.text = ""
                    self.props[self.getPropName(uri)] = None
                else:
                    prop.text = value
        #logger.debug("key %s changed? %s (1 == yes)" % (key, changed))
        if found or value is None:
            return changed
        ### must not have had this kind of property already, so set value
        #logger.debug("Adding a property: %s" %(key))
        propertyNode = ElementTree.SubElement(props, Node.PROPERTY)
        propertyNode.attrib['readOnly'] = "false"
        ### There should be a '#' in there someplace...
        # propertyNode.attrib["uri"] = "%s#%s" % (Node.IVOAURL, key)
        propertyNode.attrib['uri'] = uri
        propertyNode.text = value
        self.props[self.getPropName(uri)] = value
        #logger.debug("After change node XML\n %s" %(self))
        return 1

    def chmod(self, mode):
        """Set the MODE of this Node...

        translates unix style MODE to voSpace and updates the properties...

        This function is quite limited.  We can make a file publicly
        readable and we can set turn off group read/write permissions,
        that's all. """

        changed = 0

        if mode & stat.S_IROTH:
            changed += self.setPublic('true')
        else:
            changed += self.setPublic('false')

        if mode & stat.S_IRGRP:
            changed += self.chrgrp(self.groupread)
        else:
            changed += self.chrgrp('')

        if mode & stat.S_IWGRP:
            changed += self.chwgrp(self.groupwrite)
        else:
            changed += self.chwgrp('')

        # logger.debug("%d -> %s" % (changed, changed>0))
        return changed > 0

    def create(self, uri, nodeType="vos:DataNode", properties={}, subnodes=[]):
        """Build the XML needed to represent a VOSpace node returns an
           ElementTree represenation of the XML

        nodeType   -- the VOSpace node type, likely one of vos:DataNode or
                      vos:ContainerNode
        properties -- a dictionary of the node properties, all assumed to be
                      single words from the IVOA list
        """

        # ## Build the root node called 'node'
        node = ElementTree.Element("node")
        node.attrib["xmlns"] = Node.VOSNS
        node.attrib["xmlns:vos"] = Node.VOSNS
        node.attrib[Node.TYPE] = nodeType
        node.attrib["uri"] = uri

        ### create a properties section
        if 'type' not in properties:
            properties['type'] = mimetypes.guess_type(uri)[0]
            #logger.debug("set type to %s" % (properties['type']))
        propertiesNode = ElementTree.SubElement(node, Node.PROPERTIES)
        for property in properties.keys():
            propertyNode = ElementTree.SubElement(propertiesNode,
                                                  Node.PROPERTY)
            propertyNode.attrib['readOnly'] = "false"
            ### There should be a '#' in there someplace...
            propertyNode.attrib["uri"] = "%s" % self.fix_prop(property)
            if properties[property] is None:
                ## this is actually a delete property
                propertyNode.attrib['xsi:nil'] = 'true'
                propertyNode.attrib["xmlns:xsi"] = Node.XSINS
                propertyNode.text = ""
            elif len(str(properties[property])) > 0:
                propertyNode.text = properties[property]

        ## That's it for link nodes...
        if nodeType == "vos:LinkNode":
            return node

        ### create accepts
        accepts = ElementTree.SubElement(node, Node.ACCEPTS)

        ElementTree.SubElement(accepts, "view").attrib['uri'] = \
            "%s#%s" % (Node.IVOAURL, "defaultview")

        provides = ElementTree.SubElement(node, Node.PROVIDES)
        ElementTree.SubElement(provides, "view").attrib['uri'] = \
            "%s#%s" % (Node.IVOAURL, 'defaultview')
        ElementTree.SubElement(provides, "view").attrib['uri'] = \
            "%s#%s" % (Node.CADCURL, 'rssview')

        ### Only DataNode can have a dataview...
        if nodeType == "vos:DataNode":
            ElementTree.SubElement(provides, "view").attrib['uri'] = \
                "%s#%s" % (Node.CADCURL, 'dataview')

        ### if this is a container node then we need to add an empy directory
        ### contents area...
        if nodeType == "vos:ContainerNode":
            nodeList = ElementTree.SubElement(node, Node.NODES)
            for subnode in subnodes:
                nodeList.append(subnode.node)

        return node

    def isdir(self):
        """Check if target is a container Node"""
        # logger.debug(self.type)
        if self.type == "vos:ContainerNode":
            return True
        return False

    def islink(self):
        """Check if target is a link Node"""
        # logger.debug(self.type)
        if self.type == "vos:LinkNode":
            return True
        return False

    def islocked(self):
        """Check if target state is locked for update/delete."""
        return self.props[Node.ISLOCKED] == "true"

    def getInfo(self):
        """Organize some information about a node and return as dictionary"""
        date = time.mktime(time.strptime(self.props['date'][0:-4],
                                         '%Y-%m-%dT%H:%M:%S'))
        # if date.tm_year==time.localtime().tm_year:
        #    dateString=time.strftime('%d %b %H:%S',date)
        #else:
        #    dateString=time.strftime('%d %b  %Y',date)
        creator = string.lower(re.search('CN=([^,]*)',
                                         self.props.get('creator', 'CN=unknown_000,'))
                               .groups()[0].replace(' ', '_'))
        perm = []
        writeGroup = ""
        readGroup = ""
        for i in range(10):
            perm.append('-')
        perm[1] = 'r'
        perm[2] = 'w'
        if self.type == "vos:ContainerNode":
            perm[0] = 'd'
        if self.type == "vos:LinkNode":
            perm[0] = 'l'
        if self.props.get('ispublic', "false") == "true":
            perm[-3] = 'r'
            perm[-2] = '-'
        writeGroup = self.props.get('groupwrite', 'NONE')
        if writeGroup != 'NONE':
            perm[5] = 'w'
        readGroup = self.props.get('groupread', 'NONE')
        if readGroup != 'NONE':
            perm[4] = 'r'
        isLocked = self.props.get(Node.ISLOCKED, "false")
        return {"permissions": string.join(perm, ''),
                "creator": creator,
                "readGroup": readGroup,
                "writeGroup": writeGroup,
                "isLocked": isLocked,
                "size": float(self.props.get('length', 0)),
                "date": date,
                "target": self.target}

    def getNodeList(self):
        """Get a list of all the nodes held to by a ContainerNode return a
           list of Node objects"""
        if (self._nodeList is None):
            self._nodeList = []
            for nodesNode in self.node.findall(Node.NODES):
                for nodeNode in nodesNode.findall(Node.NODE):
                    self.addChild(nodeNode)
        return self._nodeList

    def addChild(self, childEt):
        childNode = Node(childEt)
        self._nodeList.append(childNode)
        return (childNode)

    def clearProps(self):
        logger.debug("Clearing Props")
        properties_node_list = self.node.findall(Node.PROPERTIES)
        for properties_node in properties_node_list:
            for property in properties_node.findall(Node.PROPERTY):
                key = self.getPropName(property.get('uri'))
                if key in self.props:
                    del self.props[key]
                properties_node.remove(property)
        logger.debug("Done Clearing Props")
        return

    def getInfoList(self):
        """Retrieve a list of tupples of (NodeName, Info dict)"""
        infoList = {}
        for node in self.getNodeList():
            infoList[node.name] = node.getInfo()
        if self.type == "vos:DataNode":
            infoList[self.name] = self.getInfo()
        return infoList.items()

    def setProps(self, props):
        """Set the properties of node, given the properties element of that
           node"""
        for propertyNode in props.findall(Node.PROPERTY):
            self.props[self.getPropName(propertyNode.get('uri'))] = \
                self.getPropValue(propertyNode)
        return

    def getPropName(self, prop):
        """parse the property uri and get the name of the property"""
        (url, propName) = urllib.splittag(prop)
        if url == Node.IVOAURL:
            return propName
        return prop

    def getPropValue(self, prop):
        """Pull out the value part of node"""
        return prop.text


class VOFile:
    """
    A class for managing http connections

    Attributes:
    maxRetries - maximum number of retries when transient errors encountered.
                 When set too high (as the default value is) the number of
                 retries are time limited (max 15min)
    maxRetryTime - maximum time to retry for when transient errors are
                   encountered
    """

    errnos = {404: errno.ENOENT,
              401: errno.EACCES,
              409: errno.EEXIST,
              423: errno.EPERM,
              408: errno.EAGAIN}
    # ## if we get one of these codes, retry the command... ;-(
    retryCodes = (503, 408, 504, 412)

    def __init__(self, url_list, connector, method, size=None,
                 followRedirect=True, range=None, possible_partial_read=False):
        self.closed = True
        assert isinstance(connector, Connection)
        self.connector = connector
        self.httpCon = None
        self.timeout = -1
        self.size = size
        self.md5sum = None
        self.maxRetries = 10000
        self.maxRetryTime = MAX_RETRY_TIME
        # TODO
        # Make all the calls to open send a list of URLs
        # this should be redone during a cleanup. Basically, a GET might
        # result in multiple URLs (list of URLs) but VOFile is also used to
        # retrieve schema files and other info.

        # All the calls should pass a list of URLs. Make sure that we
        # make a deep copy of the input list so that we don't
        # accidentally modify the caller's copy.
        if isinstance(url_list, list):
            self.URLs = deepcopy(url_list)
        else:
            self.URLs = [url_list]
        self.urlIndex = 0
        self.followRedirect = followRedirect
        self._fpos = 0
        self.open(self.URLs[self.urlIndex], method, bytes=range, possible_partial_read=possible_partial_read)
        # initial values for retry parameters
        self.currentRetryDelay = DEFAULT_RETRY_DELAY
        self.totalRetryDelay = 0
        self.retries = 0
        self.fileSize = None

    def tell(self):
        return self._fpos

    def seek(self, offset, loc=os.SEEK_SET):
        if loc == os.SEEK_CUR:
            self._fpos += offset
        elif loc == os.SEEK_SET:
            self._fpos = offset
        elif loc == os.SEEK_END:
            self._fpos = int(self.size) - offset
        return

    def flush(self):
        """
        Flush is a NO OP in VOFile, we only really flush on close.
        @return:
        """
        return

    def close(self):
        """close the connection"""
        global CONNECTION_COUNTER
        if self.closed:
            return self.closed
        connection_count_logger.debug("Closing http connection".format(CONNECTION_COUNTER))
        try:
            logger.debug("closing the connection.")
            if self.transEncode is not None:
                self.httpCon.send('0\r\n\r\n')
                logger.debug("End of document sent.")
            logger.debug("getting response.")
            self.resp = self.httpCon.getresponse()
            logger.debug("checking response status.")
            self.checkstatus()
            logger.debug("Finishing close.")
        finally:
            self.closed = True
            logger.debug("actually closing.")
            self.httpCon.close()
            logger.debug("closed.")
        return self.closed

    def checkstatus(self, codes=(200, 201, 202, 206, 302, 303, 503, 416,
                                 416, 402, 408, 412, 504)):
        """check the response status"""
        msgs = {404: "Node Not Found",
                401: "Not Authorized",
                409: "Conflict",
                423: "Locked",
                408: "Connection Timeout"}
        logger.debug("status %d for URL %s" % (self.resp.status, self.url))
        if self.resp.status not in codes:
            logger.debug("Got status code: %s for %s" %
                         (self.resp.status, self.url))
            msg = self.resp.read()
            if msg is not None:
                msg = html2text.html2text(msg, self.url).strip()
            logger.debug("Error message: {0}".format(msg))
            if self.resp.status in VOFile.errnos.keys() or (msg is not None and "Node is busy" in msg):
                if msg is None or len(msg) == 0:
                    msg = msgs[self.resp.status]
                if self.resp.status == 401 and self.connector.vospace_certfile is None and self.connector.vospace_token is None:
                    msg += " using anonymous access "
            exception = IOError(VOFile.errnos.get(self.resp.status,
                                            self.resp.status), msg, self.url)
            if self.resp.status == 500 and "read-only" in msg:
                exception = IOError(errno.EPERM)
            raise exception

        # Get the file size. We use this 'X-CADC-Content-Length' as a
        # fallback to work around a server-side Java bug that limits
        # 'Content-Length' to a signed 32-bit integer (~2 gig files)
        self.size = self.resp.getheader("Content-Length", 
                                        self.resp.getheader("X-CADC-Content-Length",0))

        if self.resp.status == 200:
            self.md5sum = self.resp.getheader("Content-MD5", None)
            self.totalFileSize = int(self.size)
        return True

    def open(self, URL, method="GET", bytes=None, possible_partial_read=False):
        """Open a connection to the given URL"""
        logger.debug("Opening %s (%s)" % (URL, method))
        self.url = URL
        self.httpCon = self.connector.get_connection(URL)

        self.closed = False
        self.httpCon.putrequest(method, URL)
        userAgent = 'vos ' + version
        if "mountvofs" in sys.argv[0]:
            userAgent = 'vofs ' + version
        self.httpCon.putheader("User-Agent", userAgent)
        self.transEncode = None

        # Add token to header if present
        if self.connector.vospace_token:
            self.httpCon.putheader(HEADER_DELEG_TOKEN,
                                   self.connector.vospace_token)

        if method in ["PUT"]:
            try:
                self.size = int(self.size)
                self.httpCon.putheader("Content-Length", self.size)
                self.httpCon.putheader("X-CADC-Content-Length", self.size)
            except TypeError as e:
                self.size = None
                self.transEncode = "chunked"
                self.httpCon.putheader("Transfer-Encoding", 'chunked')
        elif method in ["POST", "DELETE"]:
            self.size = None
            self.httpCon.putheader("Transfer-Encoding", 'chunked')
            self.transEncode = "chunked"
        if method in ["PUT", "POST", "DELETE"]:
            contentType = "text/xml"
            if method == "PUT":
                ext = os.path.splitext(urllib.splitquery(URL)[0])[1]
                if ext in ['.fz', '.fits', 'fit']:
                    contentType = 'application/fits'
                else:
                    contentType = mimetypes.guess_type(URL)[0]
            if contentType is not None:
                self.httpCon.putheader("Content-Type", contentType)
        if bytes is not None and method == "GET":
            self.httpCon.putheader("Range", bytes)
        self.httpCon.putheader("Accept", "*/*")
        self.httpCon.putheader("Expect", "100-continue")

        # set header if a partial read is possible
        if possible_partial_read and method == "GET":
            self.httpCon.putheader("X-CADC-Partial-Read", "true")

        self.httpCon.endheaders()

    def getFileInfo(self):
        """Return information harvested from the HTTP header"""
        return (self.totalFileSize, self.md5sum)

    def read(self, size=None):
        """return size bytes from the connection response"""

        #logger.debug("Starting to read file by closing http(s) connection")

        read_error = None
        if not self.closed:
            try:
                self.close()
            except (httplib.HTTPException, ssl.SSLError) as exception:
                logger.debug("Caught {0}: {1}".format(type(exception), str(exception)))
                raise exception
            except IOError as exception:
                logger.debug(type(exception))
                logger.debug(str(exception))
        if self.resp.status == 416:
            return ""
        # check the most likely response first
        if self.resp.status == 200:
            buff = self.resp.read(size)
            #logger.debug(buff)
            return buff
        if self.resp.status == 206:
            buff = self.resp.read(size)
            self._fpos += len(buff)
            #logger.debug("left file pointer at: %d" % (self._fpos))
            return buff
        elif self.resp.status == 303 or self.resp.status == 302:
            URL = self.resp.getheader('Location', None)
            logger.debug("Got redirect URL: %s" % (URL))
            self.url = URL
            if not URL:
                #logger.debug("Raising error?")
                raise IOError(errno.ENOENT, "Got 303 on {0} but no Location value in header? [{1}]".format(self.url,
                                                                                                         self.resp.read()),
                              self.url)
            if self.followRedirect:
                self.open(URL, "GET")
                #logger.debug("Following redirected URL:  %s" % (URL))
                return self.read(size)
            else:
                #logger.debug("Got url:%s from redirect but not following" %
                #(self.url))
                return self.url
        elif self.resp.status in VOFile.retryCodes:
            # Note: 404 (File Not Found) might be returned when:
            # 1. file deleted or replaced
            # 2. file migrated from cache
            # 3. hardware failure on storage node
            # For 3. it is necessary to try the other URLs in the list
            #   otherwise this the failed URL might show up even after the
            #   caller tries to re-negotiate the transfer.
            # For 1. and 2., calls to the other URLs in the list might or
            #   might not succeed.
            if self.urlIndex < len(self.URLs) - 1:
                # go to the next URL
                self.urlIndex += 1
                self.open(self.URLs[self.urlIndex], "GET")
                return self.read(size)
        else:
            self.URLs.pop(self.urlIndex)  # remove url from list
            if len(self.URLs) == 0:
                # no more URLs to try...
                if read_error is not None:
                    raise read_error
                if self.resp.status == 404:
                    raise IOError(errno.ENOENT, self.resp.read())
                else:
                    raise IOError(errno.EIO,
                                  "unexpected server response %s (%d)" %
                                  (self.resp.reason, self.resp.status), self.url)
            if self.urlIndex < len(self.URLs):
                self.open(self.URLs[self.urlIndex], "GET")
                return self.read(size)

        ## start from top of URLs with a delay
        self.urlIndex = 0
        logger.error("Got %d: servers busy on %s" %
                     (self.resp.status, self.URLs))
        msg = self.resp.read()
        if msg is not None:
            msg = html2text.html2text(msg, self.url).strip()
        else:
            msg = "No Message Sent"
        logger.error("Message from last server (%s):  %s" % (self.url, msg))
        try:
            ### see if there is a Retry-After in the head...
            ras = int(self.resp.getheader("Retry-After", 5))
        except:
            ras = self.currentRetryDelay
            if (self.currentRetryDelay * 2) < MAX_RETRY_DELAY:
                self.currentRetryDelay = self.currentRetryDelay * 2
            else:
                self.currentRetryDelay = MAX_RETRY_DELAY

        if ((self.retries < self.maxRetries) and
                (self.totalRetryDelay < self.maxRetryTime)):
            logger.error("retrying in %d seconds" % (ras))
            self.totalRetryDelay = self.totalRetryDelay + ras
            self.retries = self.retries + 1
            time.sleep(int(ras))
            self.open(self.URLs[self.urlIndex], "GET")
            return self.read(size)
        else:
            raise IOError(self.resp.status,
                          "failed to connect to server after multiple attempts"
                          " %s (%d)" % (self.resp.reason, self.resp.status),
                          self.url)

    def write(self, buf):
        """write buffer to the connection"""
        if not self.httpCon or self.closed:
            raise OSError(errno.ENOTCONN, "no connection for write", self.url)
        ### If we are sending chunked then we need to frame the transfer
        if self.transEncode is not None:
            self.httpCon.send('%X\r\n' % len(buf))
            self.httpCon.send(buf + '\r\n')
        else:
            self.httpCon.send(buf)
        return len(buf)


class Client:
    """The Client object does the work"""

    VOServers = {'cadc.nrc.ca!vospace': SERVER,
                 'cadc.nrc.ca~vospace': SERVER}

    VOTransfer = '/vospace/synctrans'
    VOProperties = '/vospace/nodeprops'
    VO_HTTPGET_PROTOCOL = 'ivo://ivoa.net/vospace/core#httpget'
    VO_HTTPPUT_PROTOCOL = 'ivo://ivoa.net/vospace/core#httpput'
    VO_HTTPSGET_PROTOCOL = 'ivo://ivoa.net/vospace/core#httpsget'
    VO_HTTPSPUT_PROTOCOL = 'ivo://ivoa.net/vospace/core#httpsput'
    DWS = '/data/pub/'

    # ## reserved vospace properties, not to be used for extended property
    ### setting
    vosProperties = ["description", "type", "encoding", "MD5", "length",
                     "creator", "date", "groupread", "groupwrite", "ispublic"]

    def __init__(self, vospace_certfile=None, rootNode=None, conn=None,
                 cadc_short_cut=False, http_debug=False,
                 secure_get=False, vospace_token=None):
        """This could/should be expanded to set various defaults

        vospace_certfile: CADC x509 proxy certificate file location. Overrides certfile in conn.
        vospace_token: token string (alternative to vospace_certfile)
        rootNode: the base of the VOSpace for uri references.
        conn: a connection pool object for this Client
        archive: the name of the archive to associated with GET requests
        cadc_short_cut: if True then just assumed data web service urls
        secure_get: Use HTTPS: ie. transfer contents of files using SSL encryption.
        """

        if not isinstance(conn, Connection):
            vospace_certfile = vospace_certfile is None and VOSPACE_CERTFILE or vospace_certfile
            conn = Connection(vospace_certfile=vospace_certfile,
                              vospace_token=vospace_token,
                              http_debug=http_debug)

        if conn.vospace_certfile:
            logger.debug("Using certificate file: {0}".format(vospace_certfile))
        if conn.vospace_token:
            logger.debug("Using vospace token: " + conn.vospace_token)

        vospace_certfile = conn.vospace_certfile
        # Set the protocol
        if vospace_certfile is None:
            self.protocol = "http"
        else:
            self.protocol = "https"

        self.conn = conn
        self.VOSpaceServer = "cadc.nrc.ca!vospace"
        self.rootNode = rootNode
        self.nodeCache = NodeCache()
        self.cadc_short_cut = cadc_short_cut
        self.secure_get = secure_get

        return


    def glob(self, pathname):
        """Return a list of paths matching a pathname pattern.

    The pattern may contain simple shell-style wildcards a la
    fnmatch. However, unlike fnmatch, filenames starting with a
    dot are special cases that are not matched by '*' and '?'
    patterns.

        """
        return list(self.iglob(pathname))

    def iglob(self, pathname):
        """Return an iterator which yields the paths matching a pathname pattern.

        The pattern may contain simple shell-style wildcards a la
        fnmatch. However, unlike fnmatch, filenames starting with a
        dot are special cases that are not matched by '*' and '?'
        patterns.

        """
        dirname, basename = os.path.split(pathname)
        if not self.has_magic(pathname):
            if basename:
                if self.access(pathname):
                    yield pathname
                else:
                    raise IOError(errno.EACCES, "Permission denied: {0}".format(pathname))
            else:
                # Patterns ending with a slash should match only directories
                if self.iglob(dirname):
                    yield pathname
            return
        if not dirname:
            for name in self.glob1(self.rootNode, basename):
                yield name
            return
        # `os.path.split()` returns the argument itself as a dirname if it is a
        # drive or UNC path.  Prevent an infinite recursion if a drive or UNC path
        # contains magic characters (i.e. r'\\?\C:').
        if dirname != pathname and self.has_magic(dirname):
            dirs = self.iglob(dirname)
        else:
            dirs = [dirname]
        if self.has_magic(basename):
            glob_in_dir = self.glob1
        else:
            glob_in_dir = self.glob0
        for dirname in dirs:
            for name in glob_in_dir(dirname, basename):
                yield os.path.join(dirname, name)

    # These 2 helper functions non-recursively glob inside a literal directory.
    # They return a list of basenames. `glob1` accepts a pattern while `glob0`
    # takes a literal basename (so it only has to check for its existence).

    def glob1(self, dirname, pattern):
        if not dirname:
            dirname = self.rootNode
        if isinstance(pattern, _unicode) and not isinstance(dirname, unicode):
            dirname = unicode(dirname, sys.getfilesystemencoding() or
                              sys.getdefaultencoding())
        try:
            names = self.listdir(dirname, force=True)
        except os.error:
            return []
        if pattern[0] != '.':
            names = filter(lambda x: x[0] != '.', names)
        return fnmatch.filter(names, pattern)

    def glob0(self, dirname, basename):
        if basename == '':
            # `os.path.split()` returns an empty basename for paths ending with a
            # directory separator.  'q*x/' should match only directories.
            if self.isdir(dirname):
                return [basename]
        else:
            if self.access(os.path.join(dirname, basename)):
                return [basename]
            else:
                raise IOError(errno.EACCES, "Permission denied: {0}".format(os.path.join(dirname, basename)))
        return []

    magic_check = re.compile('[*?[]')
    @classmethod
    def has_magic(cls, s):
        return cls.magic_check.search(s) is not None

    #@logExceptions()
    def copy(self, src, dest, sendMD5=False):
        """copy from src to dest vospace.

        src:  a VOSpace or local-filesystem location.
        dest: a VOSpace or local-filesystem location.

        One of src or dest must be a vospace location and the other must be a local location.

        TODO: handle vospace to vospace copies.
        TODO: handle vospace to vospace copies.

        """

        checkSource = False
        srcNode = None
        cutout = None

        if src[0:4] == "vos:":
            match = re.search("([^\[\]]*)(\[.*\])$", src)
            logging.debug("Getting {0} with match {1}".format(src, match))
            if match is not None:
                src = match.group(1)
                cutout = match.group(2)
                logger.debug("Trying to access the file {1} using cutout {0}".format(src, cutout))
                sendMD5 = False
            srcNode = self.getNode(src)
            srcSize = srcNode.attr['st_size']
            srcMD5 = srcNode.props.get('MD5',
                                       'd41d8cd98f00b204e9800998ecf8427e')
            if cutout is not None:
                fin = self.open(src, os.O_RDONLY, view='cutout', cutout=cutout)
            else:
                fin = self.open(src, os.O_RDONLY, view='data')
            fout = open(dest, 'w')
            checkSource = True
        else:
            srcSize = os.stat(src).st_size
            fin = open(src, 'r')
            fout = self.open(dest, os.O_WRONLY, size=srcSize)

        destSize = 0
        md5 = hashlib.md5()
        # wrap the read statements in a try/except repeat
        # if we get this far into copy then the node exists
        # and the error is likely a transient timeout issue
        try:
            while True:
                buf = fin.read(BUFSIZE)
                if len(buf) == 0:
                    break
                fout.write(buf)
                md5.update(buf)
                destSize += len(buf)
        except IOError as e:
            logger.debug(str(e))
            if srcNode is not None and srcNode.uri in self.nodeCache:
                # remove from cache and retry
                with self.nodeCache.volatile(srcNode.uri):
                    return self.copy(src, dest, sendMD5=sendMD5)
            else:
                raise
        finally:
            fout.close()
            fin.close()

        if checkSource:
            if srcNode.type != "vos:LinkNode":
                checkMD5 = srcMD5
            else:
                # TODO
                # this is a hack ..
                # we should check the data integraty of links too
                # just not sure how
                checkMD5 = md5.hexdigest()
        else:
            checkMD5 = self.getNode(dest,
                                    force=True).props.get(
                'MD5', 'd41d8cd98f00b204e9800998ecf8427e')

        if sendMD5:
            if checkMD5 != md5.hexdigest():
                logger.debug(("MD5s don't match ( %s -> %s ) "
                              % (checkMD5, md5.hexdigest())))

                raise OSError(errno.EIO, "MD5s don't match", src)
            return md5.hexdigest()

        if (destSize != srcSize and (srcNode is not None) and
                (srcNode.type != 'vos:LinkNode') and cutout is None):
            logger.error("sizes don't match (%s (%i) -> %s (%i)) " %
                         (src, srcSize, dest, destSize))
            raise IOError(errno.EIO, "sizes don't match", src)
        return destSize

    def fixURI(self, uri):
        """given a uri check if the authority part is there and if it isn't
        then add the CADC vospace authority

        """
        parts = URLparse(uri)
        # TODO
        # implement support for local files (parts.scheme=None
        # and self.rootNode=None

        if parts.scheme is None:
            uri = self.rootNode + uri
        parts = URLparse(uri)
        if parts.scheme != "vos":
            # Just past this back, I don't know how to fix...
            return uri
        ## Check that path name compiles with the standard
        logger.debug("Got value of args: {0}".format(parts.args))
        if parts.args is not None and parts.args != "":
            uri = urlparse.parse_qs(urlparse.urlparse(parts.args).query).get('link', None)[0]
            logger.debug("Got uri: {0}".format(uri))
            if uri is not None:
                return self.fixURI(uri)
        # Check for 'cutout' syntax values.
        path = re.match("(?P<fname>[^\[]*)(?P<ext>(\[\d*\:?\d*\])?"
                        "(\[\d*\:?\d*,?\d*\:?\d*\])?)", parts.path)
        filename = os.path.basename(path.group('fname'))
        if not re.match("^[\_\-\(\)\=\+\!\,\;\:\@\&\*\$\.\w\~]*$", filename):
            raise IOError(errno.EINVAL, "Illegal vospace container name",
                          filename)
        path = path.group('fname')
        ## insert the default VOSpace server if none given
        host = parts.netloc
        if not host or host == '':
            host = self.VOSpaceServer
        path = os.path.normpath(path).strip('/')
        return "%s://%s/%s%s" % (parts.scheme, host, path, parts.args)

    def getNode(self, uri, limit=0, force=False):
        """connect to VOSpace and download the definition of vospace node

        uri   -- a voSpace node in the format vos:/vospaceName/nodeName
        limit -- load children nodes in batches of limit
        """
        logger.debug("Getting node {0}".format(uri))
        uri = self.fixURI(uri)
        node = None
        if not force and uri in self.nodeCache:
            node = self.nodeCache[uri]
        if node is None:
            logger.debug("Getting node {0} from ws".format(uri))
            with self.nodeCache.watch(uri) as watch:
                # If this is vospace URI then we can request the node info
                # using the uri directly, but if this a URL then the metadata
                # comes from the HTTP header.
                if uri[0:4] == 'vos:':
                    xml_file = StringIO(self.open(uri, os.O_RDONLY,
                                                  limit=limit).read())
                    xml_file.seek(0)
                    dom = ElementTree.parse(xml_file)
                    node = Node(dom.getroot())
                elif uri.startswith('http'):
                    header = self.open(None, URL=uri, mode=os.O_RDONLY, head=True)
                    header.read()
                    logger.debug("Got http headers: {0}".format(header.resp.getheaders()))
                    properties = {'type': header.resp.getheader('content-type', 'txt'),
                                  'date': time.strftime(
                                      '%Y-%m-%dT%H:%M:%S GMT',
                                      time.strptime(header.resp.getheader('date', None),
                                                    '%a, %d %b %Y %H:%M:%S GMT')),
                                  'groupwrite': None,
                                  'groupread': None,
                                  'ispublic': URLparse(uri).scheme == 'https' and 'true' or 'false',
                                  'length': header.resp.getheader('content-length', 0)}
                    node = Node(node=uri, node_type=Node.DATA_NODE, properties=properties)
                    logger.debug(str(node))
                else:
                    raise OSError(2, "Bad URI {0}".format(uri))
                watch.insert(node)
                # IF THE CALLER KNOWS THEY DON'T NEED THE CHILDREN THEY
                # CAN SET LIMIT=0 IN THE CALL Also, if the number of nodes
                # on the firt call was less than 500, we likely got them
                # all during the init
                if (limit != 0 and node.isdir() and
                            len(node.getNodeList()) > 500):
                    nextURI = None
                    while nextURI != node.getNodeList()[-1].uri:
                        nextURI = node.getNodeList()[-1].uri
                        xml_file = StringIO(self.open(uri, os.O_RDONLY,
                                                      nextURI=nextURI, limit=limit).read())
                        xml_file.seek(0)
                        next_page = Node(ElementTree.parse(xml_file).getroot())
                        if (len(next_page.getNodeList()) > 0 and
                                    nextURI == next_page.getNodeList()[0].uri):
                            next_page.getNodeList().pop(0)
                        node.getNodeList().extend(next_page.getNodeList())
                        logger.debug("Next URI currently %s" % (nextURI))
                        logger.debug("Last URI currently %s" % (
                            node.getNodeList()[-1].uri))
        for childNode in node.getNodeList():
            logger.debug("child URI %s" % (childNode.uri))
            with self.nodeCache.watch(childNode.uri) as childWatch:
                childWatch.insert(childNode)
        return node

    def getNodeURL(self, uri, method='GET', view=None, limit=0, nextURI=None,
                   cutout=None,
                   full_negotiation=None):
        """Split apart the node string into parts and return the correct
           URL for this node"""

        uri = self.fixURI(uri)
        logger.debug("Getting URL for: " + str(uri))

        # full_negotiation is an override, so it can be used to
        # force either shortcut (false) or full negotiation (true)
        if full_negotiation is not None:
            do_shortcut = not full_negotiation
        else:
            do_shortcut = self.cadc_short_cut

        logger.debug("do_shortcut=%i method=%s view=%s" % (do_shortcut,
                                                           method, view))

        if not do_shortcut and method == 'GET' and view in ['data', 'cutout']:
            return self._get(uri, view=view, cutout=cutout)

        if not do_shortcut and method in ('PUT'):
            return self._put(uri)

        if view == "cutout":
            if cutout is None:
                raise ValueError("For view=cutout, must specify a cutout "
                                 "value of the form"
                                 "[extension number][x1:x2,y1:y2]")

        parts = URLparse(uri)
        logger.debug("parts: " + str(parts))

        # see if we have a VOSpace server that goes with this URI in our
        # look up list
        server = Client.VOServers.get(parts.netloc, None)
        if server is None:
            return uri

        URL = None
        if (do_shortcut and ((method == 'GET' and
                                      view in ['data', 'cutout']) or method == "PUT")):
            ## only get here if do_shortcut == True
            # find out the URL to the CADC data server
            direction = {'GET': 'pullFromVoSpace', 'PUT': 'pushToVoSpace'}

            # We override the GET protocol to use HTTP (faster)
            # unless a secure_get is requested.
            protocol = {
                'GET':
                    {'https':
                         (self.secure_get and
                          Client.VO_HTTPSGET_PROTOCOL) or
                         Client.VO_HTTPGET_PROTOCOL,
                     'http': Client.VO_HTTPGET_PROTOCOL},
                'PUT':
                    {'https': Client.VO_HTTPSPUT_PROTOCOL,
                     'http': Client.VO_HTTPPUT_PROTOCOL}}

            url = "%s://%s%s" % (self.protocol, SERVER, "")
            logger.debug("URL: %s" % (url))

            args = {
                'TARGET': self.fixURI(uri),
                'DIRECTION': direction[method],
                'PROTOCOL': protocol[method][self.protocol],
                'view': view}

            if cutout is not None:
                args['cutout'] = cutout
            form = urllib.urlencode(args)
            headers = {"Content-type": "application/x-www-form-urlencoded",
                       "Accept": "text/plain"}
            # Add token to header if present
            if self.conn.vospace_token:
                headers[HEADER_DELEG_TOKEN] = self.conn.vospace_token

            httpCon = self.conn.get_connection(url)
            httpCon.request("POST", Client.VOTransfer, form, headers)
            try:
                response = httpCon.getresponse()

                if response.status == 303:
                    # Normal case is a redirect
                    URL = response.getheader('Location', None)
                elif response.status == 404:
                    # The file doesn't exist
                    raise IOError(errno.ENOENT, response.read(), url)
                elif response.status == 409:
                    raise IOError(errno.EREMOTE, response.read(), url)
                else:
                    logger.warning("GET/PUT shortcut not working. POST to %s"
                                   " returns: %s.  Reverting to full negotiation" %
                                   (Client.VOTransfer, response.status))
                    return self.getNodeURL(uri, method=method, view=view,
                                           limit=limit, nextURI=nextURI, cutout=cutout)
            except Exception as e:
                logger.debug(str(e))
            finally:
                httpCon.close()

            logger.debug("Sending short cut url: %s" % (URL))
            return URL

        ### this is a GET so we might have to stick some data onto the URL...
        fields = {}
        if limit is not None:
            fields['limit'] = limit
        if view is not None:
            fields['view'] = view
        if nextURI is not None:
            fields['uri'] = nextURI
        data = ""
        if len(fields) > 0:
            data = "?" + urllib.urlencode(fields)
        URL = "%s://%s/vospace/nodes/%s%s" % (self.protocol, server,
                                              parts.path.strip('/'), data)
        logger.debug("URL: %s (%s)" % (URL, method))
        return URL

    def link(self, srcURI, linkURI):
        """Make linkURI point to srcURI"""
        if (self.isdir(linkURI)):
            linkURI = os.path.join(linkURI, os.path.basename(srcURI))
        linkNode = Node(self.fixURI(linkURI), node_type="vos:LinkNode")
        ElementTree.SubElement(linkNode.node, "target").text = \
            self.fixURI(srcURI)
        URL = self.getNodeURL(linkURI)
        f = VOFile(URL, self.conn, method="PUT", size=len(str(linkNode)))
        f.write(str(linkNode))
        return f.close()

    def move(self, srcURI, destURI):
        """Move srcUri to targetUri"""
        logger.debug("Moving %s to %s" % (srcURI, destURI))
        transfer = ElementTree.Element("transfer")
        transfer.attrib['xmlns'] = Node.VOSNS
        transfer.attrib['xmlns:vos'] = Node.VOSNS
        ElementTree.SubElement(transfer, "target").text = self.fixURI(srcURI)
        ElementTree.SubElement(transfer, "direction").text = \
            self.fixURI(destURI)
        ElementTree.SubElement(transfer, "keepBytes").text = "false"

        url = "%s://%s%s" % (self.protocol, SERVER, Client.VOTransfer)
        con = VOFile(url, self.conn, method="POST", followRedirect=False)
        with nested(self.nodeCache.volatile(self.fixURI(srcURI)),
                    self.nodeCache.volatile(self.fixURI(destURI))):
            con.write(ElementTree.tostring(transfer))
            transURL = con.read()
            if not self.getTransferError(transURL, srcURI):
                return True
        return False

    def _get(self, uri, view="defaultview", cutout=None):
        if view is None:
            view = "defaultview"
        return self.transfer(uri, "pullFromVoSpace", view, cutout)

    def _put(self, uri):
        return self.transfer(uri, "pushToVoSpace")

    def transfer(self, uri, direction, view="defaultview", cutout=None):
        """Build the transfer XML document"""
        protocol = {"pullFromVoSpace": "%sget" % (self.protocol),
                    "pushToVoSpace": "%sput" % (self.protocol)}
        views = {"defaultview": "%s#%s" % (Node.IVOAURL, "defaultview"),
                 "data": "ivo://cadc.nrc.ca/vospace/view#data",
                 "cutout": "ivo://cadc.nrc.ca/vospace/view#cutout"
        }
        transfer_xml = ElementTree.Element("transfer")
        transfer_xml.attrib['xmlns'] = Node.VOSNS
        transfer_xml.attrib['xmlns:vos'] = Node.VOSNS
        ElementTree.SubElement(transfer_xml, "target").text = uri
        ElementTree.SubElement(transfer_xml, "direction").text = direction
        ElementTree.SubElement(transfer_xml, "view").attrib['uri'] = \
            views.get(view, views["defaultview"])
        if cutout is not None:
            ElementTree.SubElement(transfer_xml, "cutout").attrib['uri'] = \
                cutout
        ElementTree.SubElement(transfer_xml, "protocol").attrib['uri'] = \
            "%s#%s" % (Node.IVOAURL, protocol[direction])
        logger.debug(ElementTree.tostring(transfer_xml))
        url = "%s://%s%s" % (self.protocol, SERVER, Client.VOTransfer)
        con = VOFile(url, self.conn, method="POST", followRedirect=False)
        con.write(ElementTree.tostring(transfer_xml))
        transURL = con.read()
        logger.debug("Got back %s from trasnfer " % (transURL))
        con = StringIO(VOFile(transURL, self.conn, method="GET",
                              followRedirect=True).read())
        con.seek(0)
        logger.debug(con.read())
        con.seek(0)
        transfer_document = ElementTree.parse(con)
        logger.debug("Transfer Document: %s" % transfer_document)
        all_protocols = transfer_document.findall(Node.PROTOCOL)
        if all_protocols is None:
            return self.getTransferError(transURL, uri)

        result = []
        for protocol in all_protocols:
            for node in protocol.findall(Node.ENDPOINT):
                result.append(node.text)
        return result

    def getTransferError(self, url, uri):
        """Follow a transfer URL to the Error message"""
        errorCodes = {'NodeNotFound': errno.ENOENT,
                      'PermissionDenied': errno.EACCES,
                      'OperationNotSupported': errno.EOPNOTSUPP,
                      'InternalFault': errno.EFAULT,
                      'ProtocolNotSupported': errno.EPFNOSUPPORT,
                      'ViewNotSupported': errno.ENOSYS,
                      'InvalidArgument': errno.EINVAL,
                      'InvalidURI': errno.EFAULT,
                      'TransferFailed': errno.EIO,
                      'DuplicateNode.': errno.EEXIST,
                      'NodeLocked': errno.EPERM}
        jobURL = str.replace(url, "/results/transferDetails", "")
        try:
            phaseURL = jobURL + "/phase"
            sleepTime = 1
            roller = ('\\', '-', '/', '|', '\\', '-', '/', '|')
            phase = VOFile(phaseURL, self.conn, method="GET",
                           followRedirect=False).read()
            # do not remove the line below. It is used for testing
            logger.debug("Job URL: " + jobURL + "/phase")
            while phase in ['PENDING', 'QUEUED', 'EXECUTING', 'UNKNOWN']:
                # poll the job. Sleeping time in between polls is doubling
                # each time until it gets to 32sec
                totalSlept = 0
                if (sleepTime <= 32):
                    sleepTime = 2 * sleepTime
                    slept = 0
                    if logger.getEffectiveLevel() == logging.INFO:
                        while slept < sleepTime:
                            sys.stdout.write("\r%s %s" % (phase,
                                                          roller[totalSlept % len(roller)]))
                            sys.stdout.flush()
                            slept += 1
                            totalSlept += 1
                            time.sleep(1)
                        sys.stdout.write("\r                    \n")
                    else:
                        time.sleep(sleepTime)
                phase = VOFile(phaseURL, self.conn, method="GET",
                               followRedirect=False).read()
                logger.debug("Async transfer Phase for url %s: %s " %
                             (url, phase))
        except KeyboardInterrupt:
            # abort the job when receiving a Ctrl-C/Interrupt from the client
            logger.error("Received keyboard interrupt")
            con = VOFile(jobURL + "/phase", self.conn, method="POST",
                         followRedirect=False)
            con.write("PHASE=ABORT")
            con.read()
            raise KeyboardInterrupt
        status = VOFile(phaseURL, self.conn, method="GET",
                        followRedirect=False).read()
        logger.debug("Phase:  %s" % (status))
        if status in ['COMPLETED']:
            return False
        if status in ['HELD', 'SUSPENDED', 'ABORTED']:
            ## requeue the job and continue to monitor for completion.
            raise OSError("UWS status: %s" % (status), errno.EFAULT)
        errorURL = jobURL + "/error"
        con = VOFile(errorURL, self.conn, method="GET")
        errorMessage = con.read()
        logger.debug("Got transfer error %s on URI %s" % (errorMessage, uri))
        target = re.search("Unsupported link target:(?P<target> .*)$",
                           errorMessage)
        if target is not None:
            return target.group('target').strip()
        raise OSError(errorCodes.get(errorMessage, errno.ENOENT),
                      "%s: %s" % (uri, errorMessage))

    def open(self, uri, mode=os.O_RDONLY, view=None, head=False, URL=None,
             limit=None, nextURI=None, size=None, cutout=None, range=None,
             full_negotiation=False, possible_partial_read=False):
        """Connect to the uri as a VOFile object"""

        ### sometimes this is called with mode from ['w', 'r']
        ### really that's an error, but I thought I'd just accept those are
        ### os.O_RDONLY

        if type(mode) == str:
            mode = os.O_RDONLY

        # the URL of the connection depends if we are 'getting', 'putting' or
        # 'posting'  data
        method = None
        if mode == os.O_RDONLY:
            method = "GET"
        elif mode & (os.O_WRONLY | os.O_CREAT):
            method = "PUT"
        elif mode & os.O_APPEND:
            method = "POST"
        elif mode & os.O_TRUNC:
            method = "DELETE"
        if head:
            method = "HEAD"
        if not method:
            raise IOError(errno.EOPNOTSUPP, "Invalid access mode", mode)
        target = None
        if uri is not None and view in ['data', 'cutout']:
            try:
                node = self.getNode(uri)
                if node.type == "vos:LinkNode":
                    target = node.node.findtext(Node.TARGET)
                    if target is None:
                        raise IOError(errno.ENOENT, "No target for link")
            except IOError as e:
                if e.errno in [2, 404]:
                    pass
                else:
                    raise e
        if URL is None:
            if target is not None:
                logger.debug("%s is a link to %s" % (node.uri, target))
                if (re.search("^vos\://cadc\.nrc\.ca[!~]vospace", target)
                    is not None):
                    # TODO
                    # the above re.search should use generic VOSpace uri
                    # search, not CADC specific. i
                    ## Since this is an CADC vospace link, just follow it.
                    return self.open(target, mode, view, head, URL, limit,
                                     nextURI, size, cutout, range)
                else:
                    # A target external to VOSpace, open the target directly
                    # TODO
                    # Need a way of passing along authentication.
                    if cutout is not None:
                        target = "{0}?cutout={1}".format(target, cutout)
                    return VOFile([target], self.conn, method=method,
                                  size=size, range=range, possible_partial_read=possible_partial_read)
            else:
                URL = self.getNodeURL(uri, method=method, view=view,
                                      limit=limit, nextURI=nextURI, cutout=cutout, full_negotiation=full_negotiation)
                if URL is None:
                    raise IOError(errno.EREMOTE)

        return VOFile(URL, self.conn, method=method, size=size, range=range,
                      possible_partial_read=possible_partial_read)

    def addProps(self, node):
        """Given a node structure do a POST of the XML to the VOSpace to
           update the node properties"""
        #logger.debug("Updating %s" % (node.name))
        #logger.debug(str(node.props))
        ## Get a copy of what's on the server
        new_props = copy.deepcopy(node.props)
        old_props = self.getNode(node.uri, force=True).props
        for prop in old_props:
            if (prop in new_props and old_props[prop] == new_props[prop] and
                        old_props[prop] is not None):
                del (new_props[prop])
        node.node = node.create(node.uri, nodeType=node.type,
                                properties=new_props)
        logger.debug(str(node))
        f = self.open(node.uri, mode=os.O_APPEND, size=len(str(node)))
        f.write(str(node))
        f.close()
        return

    def create(self, node):
        f = self.open(node.uri, mode=os.O_CREAT, size=len(str(node)))
        f.write(str(node))
        return f.close()

    def update(self, node, recursive=False):
        """Updates the node properties on the server. For non-recursive
           updates, node's properties are updated on the server. For
           recursive updates, node should only contain the properties to
           be changed in the node itself as well as all its children. """
        ## Let's do this update using the async tansfer method
        URL = self.getNodeURL(node.uri)
        if recursive:
            propURL = "%s://%s%s" % (self.protocol, SERVER,
                                     Client.VOProperties)
            con = VOFile(propURL, self.conn, method="POST",
                         followRedirect=False)
            con.write(str(node))
            transURL = con.read()
            # logger.debug("Got back %s from $Client.VOProperties " % (con))
            # Start the job
            con = VOFile(transURL + "/phase", self.conn, method="POST",
                         followRedirect=False)
            con.write("PHASE=RUN")
            con.close()
            self.getTransferError(transURL, node.uri)
        else:
            con = VOFile(URL, self.conn, method="POST", followRedirect=False)
            con.write(str(node))
            con.read()
        return 0
        #f=self.open(node.uri,mode=os.O_APPEND,size=len(str(node)))
        #f.write(str(node))
        #f.close()

    def mkdir(self, uri):
        node = Node(self.fixURI(uri), node_type="vos:ContainerNode")
        URL = self.getNodeURL(uri)
        f = VOFile(URL, self.conn, method="PUT", size=len(str(node)))
        f.write(str(node))
        return f.close()

    def delete(self, uri):
        """Delete the node"""
        logger.debug("delete %s" % (uri))
        with self.nodeCache.volatile(self.fixURI(uri)):
            return self.open(uri, mode=os.O_TRUNC).close()

    def getInfoList(self, uri):
        """Retrieve a list of tupples of (NodeName, Info dict)"""
        infoList = {}
        logger.debug(str(uri))
        node = self.getNode(uri, limit=None)
        logger.debug(str(node))
        while node.type == "vos:LinkNode":
            uri = node.target
            try:
                node = self.getNode(uri, limit=None)
            except Exception as e:
                logger.error(str(e))
                break
        for thisNode in node.getNodeList():
            # logger.debug(str(thisNode.name))
            infoList[thisNode.name] = thisNode.getInfo()
        if node.type in ["vos:DataNode", "vos:LinkNode"]:
            infoList[node.name] = node.getInfo()
        return infoList.items()

    def listdir(self, uri, force=False):
        """
        Walk through the directory structure a al os.walk.
        Setting force=True will make sure no caching of results are used.
        """
        #logger.debug("getting a listing of %s " % (uri))
        names = []
        logger.debug(str(uri))
        node = self.getNode(uri, limit=None, force=force)
        while node.type == "vos:LinkNode":
            uri = node.target
            # logger.debug(uri)
            node = self.getNode(uri, limit=None, force=force)
        for thisNode in node.getNodeList():
            names.append(thisNode.name)
        return names

    def isdir(self, uri):
        """Check to see if the given uri points at a containerNode or is
           a link to one.

           uri: a vospace URI
           """
        try:
            node = self.getNode(uri, limit=0)
            while node.type == "vos:LinkNode":
                uri = node.target
                if uri[0:4] == "vos:":
                    node = self.getNode(uri, limit=0)
                else:
                    return False
            if node.type == "vos:ContainerNode":
                return True
        except:
            pass
        return False

    def isfile(self, uri):
        try:
            return self.status(uri)
        except:
            return False

    def access(self, uri, mode=os.O_RDONLY):
        """Test if the give vospace uri can be access if the given mode.

        uri:  a vospace location.
        mode: os.O_RDONLY

        NOTE: Currently mode is ignored and only read-access is checked.
        """
        try:
            dum = self.getNode(uri)
            return True
        except Exception as e:
            logger.debug(str(e))
            return False

    def status(self, uri, code=[200, 303, 302]):
        """Check to see if this given uri points at a containerNode.

        This is done by checking the view=data header and seeing if you
        get an error.
        """
        return self.open(uri, view='data', head=True).close()

    def getJobStatus(self, url):
        """ Returns the status of a job """
        return VOFile(url, self.conn, method="GET",
                      followRedirect=False).read()


