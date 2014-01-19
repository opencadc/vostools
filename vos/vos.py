"""A set of Python Classes for connecting to and interacting with a VOSpace service.

   Connections to VOSpace are made using a SSL X509 certificat which is stored in a .pem file.
   The certificate is supplied by the user or by the CADC credential server

"""

import copy
import errno
import hashlib
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
import urllib2
import xml.etree.ElementTree as ET

from __version__ import version

logger = logging.getLogger('vos')
#logger.addHandler(logging.NullHandler())

# set a 1 MB buffer to keep the number of trips
# around the IO loop small

BUFSIZE = 8388608


# consts for dealing with transient errors
MAX_RETRY_DELAY = 128  # maximum delay between retries
DEFAULT_RETRY_DELAY = 30  # start delay between retries when Try_After not specified by server
MAX_RETRY_TIME = 900 # maximum time for retries before giving up...

SERVER = os.getenv('VOSPACE_WEBSERVICE', 'www.canfar.phys.uvic.ca')
CADC_GMS_PREFIX = "ivo://cadc.nrc.ca/gms#"


class urlparse:
    """Break the URL into parts.

    There is a difference between the 2.5 and 2.7 version of the urlparse.urlparse command, so here I roll my own..."""

    def __init__(self, url):

        m = re.match("(^(?P<scheme>[a-zA-Z]*):)?(//(?P<netloc>[^/]*))?(?P<path>/?.*)?", url)
        if not m.group:
            return None
        self.scheme = m.group('scheme')
        self.netloc = m.group('netloc')
        self.path = m.group('path')

    def __str__(self):
        return "[scheme: %s, netloc: %s, path: %s]" % (self.scheme, self.netloc, self.path)


class Connection:
    """Class to hold and act on the X509 certificate"""

    def __init__(self, certfile=None, http_debug=False):
        """Setup the Certificate for later usage

        cerdServerURL -- the location of the cadc proxy certificate server
        certfile      -- where to store the certificate, if None then ${HOME}/.ssl or a temporary filename
        http_debug -- set True to generate httplib debug statements

        The user must supply a valid certificate.
        """


        ## allow anonymous access if no certfile is specified...
        if certfile is not None and not os.access(certfile, os.F_OK):
            raise EnvironmentError(
                errno.EACCES, 
                "No certificate file found at %s\n (Perhaps use getCert to pull one)" % (certfile))
        self.certfile = certfile
        self.http_debug = http_debug

    def getConnection(self, url):
        """Create an HTTPSConnection object and return.  Uses the client certificate if None given.

        uri  -- a VOSpace uri (vos://cadc.nrc.ca~vospace/path)
        certFilename -- the name of the certificate pem file.
        """
        logger.debug("parsing url: %s" %(url))
        parts = urlparse(url)
        logger.debug("Got: %s " % ( str(parts)))
        ports = {"http": 80, "https": 443}
        certfile = self.certfile
        logger.debug("Trying to connect to %s://%s using %s" % (parts.scheme,parts.netloc,certfile))

        try:
            if parts.scheme=="https":
                connection = httplib.HTTPSConnection(parts.netloc,key_file=certfile,cert_file=certfile,timeout=60)
            else:
                connection = httplib.HTTPConnection(parts.netloc,timeout=60)
        except httplib.NotConnected as e:
            logger.error("HTTP connection to %s failed \n" % (parts.netloc))
            logger.error("%s \n" % (str(e)))
            raise OSError(errno.ENTCONN, "VOSpace connection failed", parts.netloc)

        if self.http_debug:
            connection.set_debuglevel(1)

        ## Try to open this connection. 
        timestart = time.time()
        logger.debug("Opening the connection")
        while True:
            try:
                connection.connect()
            except httplib.HTTPException as e:
                logger.critical("%s" % (str(e)))
                logger.critical("Retrying connection for 30 seconds")
                if time.time() - timestart > 1200:
                    raise e
            except Exception as e:
                logger.debug(str(e))
                ex = IOError()
                ex.errno = errno.ECONNREFUSED
                ex.strerror = str(e)
                ex.filename = parts.netloc
                raise ex
            break

        #logger.debug("Returning connection " )
        return connection



class Node:
    """A VOSpace node"""

    IVOAURL = "ivo://ivoa.net/vospace/core"
    CADCURL = "ivo://cadc.nrc.ca/vospace/core"
    ISLOCKED = CADCURL+"#islocked"

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

    def __init__(self, node, nodeType="vos:DataNode", properties={}, xml=None, subnodes=[]):
        """Create a Node object based on the DOM passed to the init method

        if node is a string then create a node named node of nodeType with properties
        """

        if type(node) == unicode or type(node) == str:
            node = self.create(node, nodeType, properties, subnodes=subnodes)

        if node is None:
            raise LookupError("no node found or created?")

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
        return self.props == node.props

    def update(self):
        """Update the convience links of this node as we update the xml file"""

        self.type = self.node.get(Node.TYPE)
        if self.type == None:
            #logger.debug("Node type unknown, no node created")
            #logger.debug(ET.dump(self.node))
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

    def setProperty(self, key, value):
        """Given a dictionary of props build a properies subelement"""
        properties = self.node.find(Node.PROPERTIES)
        uri = "%s#%s" % (Node.IVOAURL, key)
        ET.SubElement(properties, Node.PROPERTY,
                      attrib={'uri': uri, 'readOnly': 'false'}).text = value


    def __str__(self):
        class dummy:
            pass
        data = []
        file = dummy()
        file.write = data.append
        ET.ElementTree(self.node).write(file, encoding="UTF-8")
        return "".join(data)

    def setattr(self, attr={}):
        """return a dictionary of attributes associated with the file stored at node

        These attributes are determind from the node on VOSpace.
        """
        ## Get the flags for file mode settings.

        self.attr = {}
        node = self

        ## Only one date provided by VOSpace, so use this as all possible dates.
        sdate = node.props.get('date', None)
        atime = time.time()
        if not sdate:
            mtime = atime
        else:
            ### mktime is expecting a localtime but we're sending a UT date, so some correction will be needed
            mtime = time.mktime(time.strptime(sdate[0:-4], '%Y-%m-%dT%H:%M:%S'))
            mtime = mtime - time.mktime(time.gmtime()) + time.mktime(time.localtime())
        self.attr['st_ctime'] = attr.get('st_ctime', mtime)
        self.attr['st_mtime'] = attr.get('st_mtime', mtime)
        self.attr['st_atime'] = atime

        ## set the MODE by orring together all flags from stat
        st_mode = 0
        self.attr['st_nlink'] = 1

        if node.type == 'vos:ContainerNode':
            st_mode |= stat.S_IFDIR
            self.attr['st_nlink'] = len(node.getNodeList()) + 2
        elif node.type == 'vos:LinkNode':
            st_mode |= stat.S_IFLNK
        else:
            st_mode |= stat.S_IFREG


        ## Set the OWNER permissions
        ## All files are read/write/execute by owner...
        st_mode |= stat.S_IRUSR | stat.S_IWUSR | stat.S_IXUSR

        ## Set the GROUP permissions
        if node.props.get('groupwrite', "NONE") != "NONE":
            st_mode |= stat.S_IWGRP
        if node.props.get('groupread', "NONE") != "NONE":
            st_mode |= stat.S_IRGRP
            st_mode |= stat.S_IXGRP

        ## Set the OTHER permissions
        if node.props.get('ispublic', 'false') == 'true':
            ## If you can read the file then you can execute too.
            ## Public does NOT mean writeable.  EVER
            st_mode |= stat.S_IROTH | stat.S_IXOTH

        self.attr['st_mode'] = attr.get('st_mode', st_mode)

        ## We set the owner and group bits to be those of the currently running process.  
        ## This is a hack since we don't have an easy way to figure these out.  TBD!
        self.attr['st_uid'] = attr.get('st_uid', os.getuid())
        self.attr['st_gid'] = attr.get('st_uid', os.getgid())
        self.attr['st_size'] = attr.get('st_size', int(node.props.get('length', 0)))
        self.attr['st_blocks'] = self.attr['st_size']/512

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
        #logger.debug("Setting value of ispublic to %s" % (str(value)))
        return self.changeProp('ispublic', value)

    def fix_prop(self,prop):
        """Check if prop is a well formed uri and if not then make into one"""
        (url,tag) = urllib.splittag(prop)
        if tag is None and url in  ['title',
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
                                    'mtime',
                                    'ctime',
                                    'ispublic']:
            tag = url
            url = Node.IVOAURL
            prop = url+"#"+tag

        parts = urlparse(url)
        if parts.path is None or tag is None:
            raise ValueError("Invalid VOSpace property uri: %s" % ( prop))

        return prop
   
    def setProp(self):
        """Build the XML for a given node"""

    def changeProp(self, key, value):
        """Change the node property 'key' to 'value'.

        Return 1 if changed.

        This function should be split into 'set' and 'delete'
        """
        #logger.debug("Before change node XML\n %s" % ( self))
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
        propertyNode = ET.SubElement(props, Node.PROPERTY)
        propertyNode.attrib['readOnly'] = "false"
        ### There should be a '#' in there someplace...
        # propertyNode.attrib["uri"] = "%s#%s" % (Node.IVOAURL, key)
        propertyNode.attrib['uri'] = uri
        propertyNode.text = value
        self.props[self.getPropName(uri)] = value
        #logger.debug("After change node XML\n %s" %( self))
        return 1


    def chmod(self, mode):
        """Set the MODE of this Node...

        translates unix style MODE to voSpace and updates the properties...

        This function is quite limited.  We can make a file publicly
        readable and we can set turn off group read/write permissions,
        that's all. """

        changed = 0

        #logger.debug("Changing mode to %d" % ( mode))
        if  mode & (stat.S_IROTH) :
            changed += self.setPublic('true')
        else:
            changed += self.setPublic('false')

        if  mode & (stat.S_IRGRP):

            changed += self.chrgrp(self.groupread)
        else:
            changed += self.chrgrp('')

        if  mode & stat.S_IWGRP :
           changed += self.chwgrp(self.groupwrite)
        else:
           changed += self.chwgrp('')

        #logger.debug("%d -> %s" % ( changed, changed>0))
        return changed > 0


    def create(self, uri, nodeType="vos:DataNode", properties={}, subnodes=[]):
        """Build the XML needed to represent a VOSpace node returns an ElementTree represenation of the XML

        nodeType   -- the VOSpace node type, likely one of vos:DataNode or vos:ContainerNode
        properties -- a dictionary of the node properties, all assumed to be single words from the IVOA list
        """


        ### Build the root node called 'node'
        node = ET.Element("node")
        node.attrib["xmlns"] = Node.VOSNS
        node.attrib["xmlns:vos"] = Node.VOSNS
        node.attrib[Node.TYPE] = nodeType
        node.attrib["uri"] = uri

        ### create a properties section
        if not properties.has_key('type'):
            properties['type'] = mimetypes.guess_type(uri)[0]
            #logger.debug("set type to %s" % (properties['type']))
        propertiesNode = ET.SubElement(node, Node.PROPERTIES)
        for property in properties.keys():
            propertyNode = ET.SubElement(propertiesNode, Node.PROPERTY)
            propertyNode.attrib['readOnly'] = "false"
            ### There should be a '#' in there someplace...
            propertyNode.attrib["uri"] = "%s" % self.fix_prop(property)
            if properties[property] is None:
                ## this is actually a delete property                                                                                                                                                
                propertyNode.attrib['xsi:nil'] = 'true'
                propertyNode.attrib["xmlns:xsi"] = Node.XSINS
                propertyNode.text = ""
            elif len(properties[property]) > 0:
                propertyNode.text = properties[property]
                    

        ## That's it for link nodes...
        if nodeType == "vos:LinkNode":
            return node

        ### create accepts
        accepts = ET.SubElement(node, Node.ACCEPTS)

        ET.SubElement(accepts, "view").attrib['uri'] = "%s#%s" % (Node.IVOAURL, "defaultview")

        ### create provides section
        provides = ET.SubElement(node, Node.PROVIDES)
        ET.SubElement(provides, "view").attrib['uri'] = "%s#%s" % (Node.IVOAURL, 'defaultview')
        ET.SubElement(provides, "view").attrib['uri'] = "%s#%s" % (Node.CADCURL, 'rssview')

        ### Only DataNode can have a dataview...
        if nodeType == "vos:DataNode":
            ET.SubElement(provides, "view").attrib['uri'] = "%s#%s" % (Node.CADCURL, 'dataview')

        ### if this is a container node then we need to add an empy directory contents area...
        if nodeType == "vos:ContainerNode":
            nodeList = ET.SubElement(node, Node.NODES)
            for subnode in subnodes:
                nodeList.append(subnode.node)
        #logger.debug(ET.tostring(node,encoding="UTF-8"))

        return node

    def isdir(self):
        """Check if target is a container Node"""
        #logger.debug(self.type)
        if self.type == "vos:ContainerNode":
            return True
        return False

    def islink(self):
        """Check if target is a link Node"""
        #logger.debug(self.type)
        if self.type == "vos:LinkNode":
            return True
        return False

    def islocked(self):
        """Check if target state is locked for update/delete."""
        return self.props[Node.ISLOCKED] == "true"

    def getInfo(self):
        """Organize some information about a node and return as dictionary"""
        date = time.mktime(time.strptime(self.props['date'][0:-4], '%Y-%m-%dT%H:%M:%S'))
        #if date.tm_year==time.localtime().tm_year:
        #    dateString=time.strftime('%d %b %H:%S',date)
        #else:
        #    dateString=time.strftime('%d %b  %Y',date)
        creator = string.lower(re.search('CN=([^,]*)', self.props.get('creator', 'CN=unknown_000,')).groups()[0].replace(' ', '_'))
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
        #logger.debug("%s: %s" %( self.name,self.props))
        return {"permisions": string.join(perm, ''),
                "creator": creator,
                "readGroup": readGroup,
                "writeGroup": writeGroup,
                "isLocked": isLocked,
                "size": float(self.props.get('length', 0)),
                "date": date,
                "target": self.target}

    def getNodeList(self):
        """Get a list of all the nodes held to by a ContainerNode return a list of Node objects"""
        if (self._nodeList is None):
            self._nodeList = []
            for nodesNode in self.node.findall(Node.NODES):
                for nodeNode in nodesNode.findall(Node.NODE):
                    self.addChild(nodeNode)
        return self._nodeList

    def addChild(self, childEt):
        childNode = Node(childEt)
        self._nodeList.append(childNode)
        return(childNode)

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
        """Set the properties of node, given the properties element of that node"""
        for propertyNode in props.findall(Node.PROPERTY):
            self.props[self.getPropName(propertyNode.get('uri'))] = self.getPropValue(propertyNode)
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
    maxRetries - maximum number of retries when transient errors encountered. When set
    too high (as the default value is) the number of retries are time limited (max 15min)
    maxRetryTime - maximum time to retry for when transient errors are encountered
    """
   
    ### if we get one of these codes, retry the command... ;-(
    retryCodes = (503, 408, 504, 412) 

    def __init__(self, URLs, connector, method, size=None, followRedirect=True):
        self.closed = True
        self.resp = 503
        self.connector = connector
        self.httpCon = None
        self.timeout = -1
        self.size = size
        self.maxRetries = 10000
        self.maxRetryTime = MAX_RETRY_TIME
        # this should be redone during a cleanup. Basically, a GET might result in multiple
        # URLs (list of URLs) but VOFile is also used to retrieve schema files and other info.
        # All the calls should pass a list of URLs
        if isinstance(URLs, list):
            self.origURLs = URLs
        else:
            self.origURLs = [URLs]
        self.URLs = list(self.origURLs) #copy
        self.urlIndex = 0
        self.followRedirect = followRedirect
        self._fpos = 0
        self.open(self.URLs[self.urlIndex], method)
        # initial values for retry parameters
        self.currentRetryDelay = DEFAULT_RETRY_DELAY
        self.totalRetryDelay = 0
        self.retries = 0

	#logger.debug("Sending back VOFile object for file of size %s" % (str(self.size)))

    def tell(self):
        return self._fpos

    def seek(self, offset, loc=os.SEEK_SET):
        if loc == os.SEEK_CUR:
            self._fpos += offset
        elif loc == os.SEEK_SET:
            self._fpos = offset
        elif loc == os.SEEK_END:
            self._fpos = self.size - offset
        return

    def close(self, code=(200, 201, 202, 206, 302, 303, 503, 416, 402, 408, 412, 504)):
        """close the connection"""
        if self.closed:
            return True
        logger.debug("Closing connection")
        try:
            if self.transEncode is not None:
                self.httpCon.send('0\r\n\r\n')
            self.resp = self.httpCon.getresponse()
            return self.checkstatus(codes=code)
        except ssl.SSLError as e:
            raise IOError(errno.EAGAIN, str(e))
        except IOError as e:
            raise e
        except Exception as e:
            raise IOError(errno.ENOTCONN, str(e))
        finally:
            self.httpCon.close()
            self.closed = True
            logger.debug("Connection closed")
        return True
        

    def checkstatus(self, codes=(200, 201, 202, 206, 302, 303, 503, 416, 416, 402, 408, 412, 504)):
        """check the response status"""
        msgs = { 404: "Node Not Found",
                 401: "Not Authorized",
                 409: "Conflict",
                 408: "Connection Timeout"}
        errnos = { 404: errno.ENOENT,
                   401: errno.EACCES,
                   409: errno.EEXIST,
                   408: errno.EAGAIN }
        logger.debug("status %d for URL %s" % (self.resp.status, self.url))
        if self.resp.status not in codes:
            logger.debug("Got status code: %s for %s" % (self.resp.status, self.url))
            msg = self.resp.read()
            if msg is not None:
                msg = html2text.html2text(msg, self.url).strip()
            logger.debug("Error message: %s" % (msg))
            if self.resp.status in errnos.keys():
                if msg is None or len(msg) == 0:
                    msg = msgs[self.resp.status]
                if self.resp.status == 401 and self.connector.certfile is None:
                    msg += " using anonymous access "
            raise IOError(self.resp.status, msg, self.url)
        self.size = self.resp.getheader("Content-Length", 0)
        return True

    def open(self, URL, method="GET", bytes=None):
        """Open a connection to the given URL"""
        logger.debug("Opening %s (%s)" % (URL, method))
        self.url = URL
        #logger.debug("Established connection")
        self.httpCon = self.connector.getConnection(URL)
        #logger.debug("Established connection")

        #self.httpCon.set_debuglevel(2)
        self.closed = False
        #logger.debug("putting request")
        self.httpCon.putrequest(method, URL)
        userAgent = 'vos ' + version
        if "mountvofs" in sys.argv[0]:
            userAgent = 'vofs ' + version
        self.httpCon.putheader("User-Agent", userAgent)
        self.transEncode = None
        #logger.debug("sending headers for file of size: %s " % (str(self.size)))
        if method in ["PUT"]:
            try:
                self.size = int(self.size)
                self.httpCon.putheader("Content-Length", self.size)
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
                #logger.debug("Got extension %s" % (ext))
                if ext in [ '.fz', '.fits', 'fit']:
                    contentType = 'application/fits'
                else:
                    contentType = mimetypes.guess_type(URL)[0]
                    #logger.debug("Guessed content type: %s" % (contentType))
            if contentType is not None:
                #logger.debug("Content-Type: %s" % str(contentType))
                self.httpCon.putheader("Content-Type", contentType)
        if bytes is not None and method == "GET" :
            #logger.debug("Range: %s" % (bytes))
            self.httpCon.putheader("Range", bytes)
        self.httpCon.putheader("Accept", "*/*")
        self.httpCon.putheader("Expect", "100-continue")
        self.httpCon.endheaders()
        #logger.debug("Opening connection for %s to %s" % (URL, method))
        #logger.debug("Done setting headers")


    def read(self, size=None):
        """return size bytes from the connection response"""
        #logger.debug("Starting to read file by closing http(s) connection")
        if not self.closed:
            try:
                self.close()
            except IOError:
                logger.info("Error on URL: %s" % (self.url) )
                # gets might have other URLs to try on, so keep going ...
        bytes = None
        errnos = { 404: errno.ENOENT,
                   401: errno.EACCES,
                   409: errno.EEXIST,
                   408: errno.EAGAIN }
        #if size != None:
        #    bytes = "bytes=%d-" % ( self._fpos)
        #    bytes = "%s%d" % (bytes,self._fpos+size)
        #self.open(self.url,bytes=bytes,method="GET")
        #self.close(code=[200,206,303,302,503,404,416])
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
        elif self.resp.status == 404:
            raise IOError(errnos[self.resp.status], self.resp.read())
        elif self.resp.status == 303 or self.resp.status == 302:
            URL = self.resp.getheader('Location', None)
            logger.debug("Got redirect URL: %s" % (URL))
            self.url = URL
            if not URL:
                #logger.debug("Raising error?")
                raise IOError(errno.ENOENT, "No Location on redirect", self.url)
            if self.followRedirect:
                self.open(URL, "GET")
                #logger.debug("Following redirected URL:  %s" % (URL))
                return self.read(size)
            else:
                #logger.debug("Got url:%s from redirect but not following" % (self.url))
                return self.url
        elif self.resp.status in VOFile.retryCodes:
            if self.urlIndex < len(self.URLs)-1:
                # go to the next URL
                self.urlIndex += 1
                self.open(self.URLs[self.urlIndex], "GET")
                return self.read(size)
        else: 
            self.URLs.pop(self.urlIndex) #remove url from list
            if len(self.URLs) == 0:
                # no more URLs to try...
                raise IOError(self.resp.status, "unexpected server response %s (%d)" % (self.resp.reason, self.resp.status), self.url)
            if self.urlIndex < len(self.URLs):
                self.open(self.URLs[self.urlIndex], "GET")
                return self.read(size)
                            
        ## start from top of URLs with a delay
        self.urlIndex = 0        
        logger.error("Got %d: servers busy on %s" % (self.resp.status, self.URLs))
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
                    
        if (self.retries < self.maxRetries) and (self.totalRetryDelay < self.maxRetryTime):
            logger.error("retrying in %d seconds" % (ras))
            self.totalRetryDelay = self.totalRetryDelay + ras
            self.retries = self.retries + 1
            time.sleep(int(ras))
            self.open(self.URLs[self.urlIndex], "GET")
            return self.read(size)
        else:
            raise IOError(self.resp.status, "failed to connect to server after multiple attempts %s (%d)" % (self.resp.reason, self.resp.status), self.url)

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

    ### reservered vospace properties, not to be used for extended property setting
    vosProperties = ["description", "type", "encoding", "MD5", "length", "creator", "date",
                   "groupread", "groupwrite", "ispublic" ]


    def __init__(self, certFile=os.path.join(os.getenv('HOME'), '.ssl/cadcproxy.pem'),
                 rootNode=None, conn=None, archive='vospace', cadc_short_cut=False,
                 http_debug=False):
        """This could/should be expanded to set various defaults

        certFile: CADC proxy certficate location.
        rootNode: the base of the VOSpace for uri references.
        conn: a connection pool object for this Client
        archive: the name of the archive to associated with GET requests
        cadc_short_cut: if True then just assumed data web service urls
        http_debug: if True, then httplib will print debug statements
        
        """
        if certFile is not None and not os.access(certFile, os.F_OK):
            ### can't get this certfile
            #logger.debug("Failed to access certfile %s " % (certFile))
            #logger.debug("Using anonymous mode, try getCert if you want to use authentication")
            certFile = None
        if certFile is None:
            self.protocol = "http"
        else:
            self.protocol = "https"
        if not conn:
            conn = Connection(certfile=certFile, http_debug=http_debug)
        self.conn = conn
        self.VOSpaceServer = "cadc.nrc.ca!vospace"
        self.rootNode = rootNode
        self.archive = archive
        self.nodeCache={}
        self.cadc_short_cut = cadc_short_cut
        return

    def copy(self, src, dest, sendMD5=False):
        """copy to/from vospace"""

        checkSource = False
        if src[0:4] == "vos:":
            srcNode = self.getNode(src)
            srcSize = srcNode.attr['st_size']
            srcMD5 = srcNode.props.get('MD5', 
                                       'd41d8cd98f00b204e9800998ecf8427e')
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
            logger.error(str(e))
            return self.copy(src,dest,sendMD5=sendMD5)
        finally:
            fout.close()
            fin.close()


        if checkSource:
            if srcNode.type != "vos:LinkNode" :
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
        if destSize != srcSize and not srcNode.type == 'vos:LinkNode'  :
            logger.error("sizes don't match ( %s -> %s ) " % (src, dest))
            raise IOError(errno.EIO, "sizes don't match", src)
        return destSize

    def fixURI(self, uri):
        """given a uri check if the authority part is there and if it isn't
        then add the CADC vospace authority
        
        """
        parts = urlparse(uri)
        # TODO 
        # implement support for local files (parts.scheme=None
        # and self.rootNode=None

        if parts.scheme is None:
            uri = self.rootNode + uri
        parts = urlparse(uri)
        if parts.scheme != "vos":
            # Just past this back, I don't know how to fix...
            return uri
        ## Check that path name compiles with the standard

        # Check for 'cutout' syntax values.
        path = re.match("(?P<fname>[^\[]*)(?P<ext>(\[\d*\:?\d*\])?(\[\d*\:?\d*,?\d*\:?\d*\])?)",parts.path)
        filename = os.path.basename(path.group('fname'))
        if not re.match("^[\_\-\(\)\=\+\!\,\;\:\@\&\*\$\.\w\~]*$", filename):
            raise IOError(errno.EINVAL, "Illegal vospace container name", filename)
        path = path.group('fname')
        ## insert the default VOSpace server if none given
        host = parts.netloc
        if not host or host == '':
            host = self.VOSpaceServer
        path = os.path.normpath(path).strip('/')
        return "%s://%s/%s" % (parts.scheme, host, path)


    def getNode(self, uri, limit=0, force=False):
        """connect to VOSpace and download the definition of vospace node

        uri   -- a voSpace node in the format vos:/vospaceName/nodeName
        limit -- load children nodes in batches of limit
        """
        #logger.debug("Limit: %s " % ( str(limit)))
        #logger.debug("Getting node %s" % ( uri))
        uri = self.fixURI(uri)
        if force or uri not in self.nodeCache:
            xml_file = self.open(uri, os.O_RDONLY, limit=limit)
            dom = ET.parse(xml_file)
            node = Node(dom.getroot())
            # IF THE CALLER KNOWS THEY DON'T NEED THE CHILDREN THEY
            # CAN SET LIMIT=0 IN THE CALL Also, if the number of nodes
            # on the firt call was less than 500, we likely got them
            # all during the init
            if limit != 0 and node.isdir() and len(node.getNodeList()) > 500 :
                nextURI = None
                while nextURI != node.getNodeList()[-1].uri:
                    nextURI = node.getNodeList()[-1].uri
                    xml_file = self.open(uri, os.O_RDONLY, nextURI=nextURI, limit=limit)
                    next_page = Node(ET.parse(xml_file).getroot())
                    if len(next_page.getNodeList()) > 0 and nextURI == next_page.getNodeList()[0].uri:
                        next_page.getNodeList().pop(0)
                    node.getNodeList().extend(next_page.getNodeList())
                    logger.debug("Next URI currently %s" % ( nextURI))
                    logger.debug("Last URI currently %s" % ( node.getNodeList()[-1].uri ) )
            self.nodeCache[uri] = node            
            for node in self.nodeCache[uri].getNodeList():
                self.nodeCache[node.uri]=node
        return self.nodeCache[uri]


    def getNodeURL(self, uri, method='GET', view=None, limit=0, nextURI=None, cutout=None):
        """Split apart the node string into parts and return the correct URL for this node"""
        uri = self.fixURI(uri)

        if not self.cadc_short_cut and method == 'GET' and view == 'data':
            return self._get(uri)

        if not self.cadc_short_cut and method in ('PUT'):
            # logger.debug("Using _put")
            return self._put(uri)

        parts = urlparse(uri)
        path = parts.path.strip('/')
        server = Client.VOServers.get(parts.netloc,None)

        if server is None:
            return uri
        logger.debug("Node URI: %s, server: %s, parts: %s " %( uri, server, str(parts)))
        URL = None
        if self.cadc_short_cut and ((method == 'GET' and view in ['data', 'cutout']) or method == "PUT") :
            ## only get here if cadc_short_cut == True
            # find out the URL to the CADC data server
            direction = "pullFromVoSpace" if method == 'GET' else "pushToVoSpace"
            transProtocol = ''
            if self.protocol == 'http':
                if method == 'GET':
                    transProtocol = Client.VO_HTTPGET_PROTOCOL
                else:
                    transProtocol = Client.VO_HTTPPUT_PROTOCOL
            else:
                if method == 'GET':
                    transProtocol = Client.VO_HTTPSGET_PROTOCOL
                else:
                    transProtocol = Client.VO_HTTPSPUT_PROTOCOL
 
            url = "%s://%s%s" % (self.protocol, SERVER, "")
            logger.debug("URL: %s" % (url))

            form = urllib.urlencode({'TARGET' : self.fixURI(uri), 'DIRECTION' : direction, 'PROTOCOL' : transProtocol})
            headers = {"Content-type": "application/x-www-form-urlencoded", "Accept": "text/plain"}
            httpCon = self.conn.getConnection(url)
            httpCon.request("POST", Client.VOTransfer, form, headers)
            try:
                response = httpCon.getresponse()
                if response.status == 303:
                    URL = response.getheader('Location', None)
                else:
                    logger.error("GET/PUT shortcut not working. POST to %s returns: %s" % \
                            (Client.VOTransfer, response.status))
                    return self.getNodeURL(uri, method=method, view=view, limit=limit, nextURI=nextURI, cutout=False)
            except Exception as e:
                logger.error(str(e))
            finally: 
                httpCon.close()          
  
            if view == "cutout":
                if cutout is None:
                    raise ValueError("For view=cutout, must specify a cutout "
                                     "value of the form"
                                     "[extension number][x1:x2,y1:y2]")

                parts = urlparse(uri)
                URL="https://www.cadc-ccda.hia-iha.nrc-cnrc.gc.ca/data/pub/vospace/%s" % ( parts.path)
                ext = "&" if "?" in URL else "?"
                URL += ext + "cutout=" + cutout

            sys.stderr.write(URL)
            logger.debug("Sending short cuturl: %s" %( URL))
            return URL

        if view == "cutout":

            if cutout is None:
                raise ValueError("For view=cutout, must specify a cutout "
                                "value of the form"
                                "[extension number][x1:x2,y1:y2]")

            urlbase = self._get(uri)[0]
            
            basepath = urlparse(urlbase).path
            ext = "&" if "?" in basepath else "?"
            return [urlbase + ext + "cutout=" + cutout]

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
        logger.debug("data: %s" % data)
        logger.debug("Fields: %s" % str(fields))
        URL = "%s://%s/vospace/nodes/%s%s" % (self.protocol, server, parts.path.strip('/'), data)
        logger.debug("Node URL %s (%s)" % (URL, method))
        return URL

    def link(self, srcURI, linkURI):
        """Make linkURI point to srcURI"""
        if (self.isdir(linkURI)) :
            linkURI = os.path.join(linkURI, os.path.basename(srcURI))
        linkNode = Node(self.fixURI(linkURI), nodeType="vos:LinkNode")
        ET.SubElement(linkNode.node, "target").text = self.fixURI(srcURI)
        URL = self.getNodeURL(linkURI)
        f = VOFile(URL, self.conn, method="PUT", size=len(str(linkNode)))
        f.write(str(linkNode))
        return f.close()


    def move(self, srcURI, destURI):
        """Move srcUri to targetUri"""
        logger.debug("Moving %s to %s" % (srcURI, destURI))
        transfer = ET.Element("transfer")
        transfer.attrib['xmlns'] = Node.VOSNS
        transfer.attrib['xmlns:vos'] = Node.VOSNS
        ET.SubElement(transfer, "target").text = self.fixURI(srcURI)
        ET.SubElement(transfer, "direction").text = self.fixURI(destURI)
        ET.SubElement(transfer, "keepBytes").text = "false"

        url = "%s://%s%s" % (self.protocol, SERVER, Client.VOTransfer)
        con = VOFile(url, self.conn, method="POST" , followRedirect=False)
        con.write(ET.tostring(transfer))
        transURL = con.read()
        if  not self.getTransferError(transURL, srcURI):
            return True
        return  False

    def _get(self, uri):
        return self.transfer(uri, "pullFromVoSpace")

    def _put(self, uri):
        return self.transfer(uri, "pushToVoSpace")

    def transfer(self, uri, direction):
        """Build the transfer XML document"""
        protocol = {"pullFromVoSpace": "%sget" % (self.protocol) ,
                    "pushToVoSpace": "%sput" % (self.protocol) }
        transferXML = ET.Element("transfer")
        transferXML.attrib['xmlns'] = Node.VOSNS
        transferXML.attrib['xmlns:vos'] = Node.VOSNS
        ET.SubElement(transferXML, "target").text = uri
        ET.SubElement(transferXML, "direction").text = direction
        ET.SubElement(transferXML, "view").attrib['uri'] = "%s#%s" % (Node.IVOAURL, "defaultview")
        ET.SubElement(transferXML, "protocol").attrib['uri'] = "%s#%s" % (Node.IVOAURL, protocol[direction])
        url = "%s://%s%s" % (self.protocol, SERVER, Client.VOTransfer)
        con = VOFile(url, self.conn, method="POST", followRedirect=False)
        con.write(ET.tostring(transferXML))
        transURL = con.read()
        logger.debug("Got back %s from trasnfer " % (con))
        con = VOFile(transURL, self.conn, method="GET", followRedirect=True)
        F = ET.parse(con)

        P = F.find(Node.PROTOCOL)
        logger.debug("Transfer protocol: %s" % (str(F)))
        if P is None:
            return self.getTransferError(transURL, uri)
        result = []
        for node in P.findall(Node.ENDPOINT):
            result.append(node.text)
        return result

    def getTransferError(self, url, uri):
        """Follow a transfer URL to the Error message"""
        errorCodes = { 'NodeNotFound': errno.ENOENT,
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
            roller = ( '\\' ,'-','/','|','\\','-','/','|' )
            phase = VOFile(phaseURL, self.conn, method="GET", followRedirect=False).read() 
            # do not remove the line below. It is used for testing
            logger.debug("Job URL: " + jobURL + "/phase")
            while phase in ['PENDING', 'QUEUED', 'EXECUTING', 'UNKNOWN' ]:
                # poll the job. Sleeping time in between polls is doubling each time 
                # until it gets to 32sec
                totalSlept = 0
                if(sleepTime <= 32):
                    sleepTime = 2 * sleepTime
                    slept = 0
                    if logger.getEffectiveLevel() == logging.INFO :
                        while slept < sleepTime:
                            sys.stdout.write("\r%s %s" % (phase, roller[totalSlept % len(roller)]))
                            sys.stdout.flush()
                            slept += 1
                            totalSlept += 1
                            time.sleep(1)
                        sys.stdout.write("\r                    \n")
                    else:
                        time.sleep(sleepTime)
                phase = VOFile(phaseURL, self.conn, method="GET", followRedirect=False).read() 
                logger.debug("Async transfer Phase for url %s: %s " % (url,  phase))
        except KeyboardInterrupt:
            # abort the job when receiving a Ctrl-C/Interrupt from the client
            logger.error("Received keyboard interrupt")
            con = VOFile(jobURL + "/phase", self.conn, method="POST", followRedirect=False)
            con.write("PHASE=ABORT")
            con.read()
            raise KeyboardInterrupt
        status = VOFile(phaseURL, self.conn, method="GET", followRedirect=False).read()
        logger.debug("Phase:  %s" % (status))
        if status in ['COMPLETED']:
            return False
        if status in ['HELD' , 'SUSPENDED', 'ABORTED']:
            ## requeue the job and continue to monitor for completion.
            raise OSError("UWS status: %s" % (status), errno.EFAULT)
        errorURL = jobURL + "/error"
        con = VOFile(errorURL, self.conn, method="GET")
        errorMessage = con.read()
        logger.debug("Got transfer error %s on URI %s" % (errorMessage, uri))
        target = re.search("Unsupported link target:(?P<target> .*)$", errorMessage)
        if target is not None:
            return target.group('target').strip()
        raise OSError(errorCodes.get(errorMessage, errno.ENOENT), "%s: %s" %( uri, errorMessage ))


    def open(self, uri, mode=os.O_RDONLY, view=None, head=False, URL=None, limit=None, nextURI=None, size=None, cutout=None):
        """Connect to the uri as a VOFile object"""

        ### sometimes this is called with mode from ['w', 'r']
        ### really that's an error, but I thought I'd just accept those are os.O_RDONLY

        logger.debug("URI: %s" % ( uri))
        logger.debug("URL: %s" %(URL))

        if type(mode) == str:
            mode = os.O_RDONLY

        # the URL of the connection depends if we are 'getting', 'putting' or 'posting'  data
        method = None
        if mode == os.O_RDONLY:
            method = "GET"
        elif mode & (os.O_WRONLY | os.O_CREAT) :
            method = "PUT"
        elif mode & os.O_APPEND :
            method = "POST"
        elif mode & os.O_TRUNC:
            method = "DELETE"
        if head:
            method = "HEAD"
        if not method:
            raise IOError(errno.EOPNOTSUPP, "Invalid access mode", mode)
        if URL is None:
            ### we where given one, see if getNodeURL can figure this out.
            URL = self.getNodeURL(uri, method=method, view=view, limit=limit, nextURI=nextURI, cutout=cutout)
        if URL is None:
            ## Dang... getNodeURL failed... maybe this is a LinkNode?
            ## if this is a LinkNode then maybe there is a Node.TARGET I could try instead...
            node = self.getNode(uri)
            if node.type == "vos:LinkNode":
                logger.debug("appears that %s is a linkNode" % ( node.uri))
                target = node.node.findtext(Node.TARGET)
                logger.debug(target)
                if target is None:
                    #logger.debug("Why is target None?")
                    ### hmm. well, that shouldn't have happened.
                    return None
                if re.search("^vos\://cadc\.nrc\.ca[!~]vospace", target) is not None:
                    #logger.debug("Opening %s with VOFile" %(target))
                    ### try opening this target directly, cross your fingers.
                    return self.open(target, mode, view, head, URL, limit, nextURI, size, cutout)
                else:
                    ### hmm. just try and open the target, maybe python will understand it.
                    #logger.debug("Opening %s with urllib2" % (target))
                    return urllib2.urlopen(target)
        else:
            return VOFile(URL, self.conn, method=method, size=size)
        return None


    def addProps(self, node):
        """Given a node structure do a POST of the XML to the VOSpace to update the node properties"""
        #logger.debug("Updating %s" % ( node.name))
        #logger.debug(str(node.props))
        ## Get a copy of what's on the server
        new_props = copy.deepcopy(node.props)
        old_props = self.getNode(node.uri,force=True).props
        for prop in old_props:
            if prop in new_props and old_props[prop] == new_props[prop] and old_props[prop] is not None:
                del(new_props[prop])
        node.node = node.create(node.uri, nodeType=node.type, properties=new_props)
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
        """Updates the node properties on the server. For non-recursive updates, node's
           properties are updated on the server. For recursive updates, node should
           only contain the properties to be changed in the node itself as well as
           all its children. """
        ## Let's do this update using the async tansfer method
        URL = self.getNodeURL(node.uri)
        if recursive:
            propURL = "%s://%s%s" % (self.protocol, SERVER, Client.VOProperties)
            con = VOFile(propURL, self.conn, method="POST", followRedirect=False)
            con.write(str(node))
            transURL = con.read()
            # logger.debug("Got back %s from $Client.VOProperties " % (con))
            # Start the job
            con = VOFile(transURL + "/phase", self.conn, method="POST", followRedirect=False)
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
        node = Node(self.fixURI(uri), nodeType="vos:ContainerNode")
        URL = self.getNodeURL(uri)
        f = VOFile(URL, self.conn, method="PUT", size=len(str(node)))
        f.write(str(node))
        return f.close()

    def delete(self, uri):
        """Delete the node"""
        # logger.debug("%s" % (uri))
        return self.open(uri, mode=os.O_TRUNC).close()

    def getInfoList(self, uri):
        """Retrieve a list of tupples of (NodeName, Info dict)"""
        infoList = {}
        node = self.getNode(uri, limit=None)
        #logger.debug(str(node))
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
        if node.type in [ "vos:DataNode", "vos:LinkNode" ]:
            infoList[node.name] = node.getInfo()
        return infoList.items()

    def listdir(self, uri, force=False):
        """
        Walk through the directory structure a al os.walk.
        Setting force=True will make sure no caching of results are used.
        """
        #logger.debug("getting a listing of %s " % ( uri))
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
        """Check to see if this given uri points at a containerNode or is a link to one."""
        try:
            node = self.getNode(uri, limit=0)
            # logger.debug(node.type)
            while node.type == "vos:LinkNode":
                uri = node.target
                # logger.debug(uri)
                if uri[0:4] == "vos:":
                    # logger.debug(uri)
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
        """Test for existance"""
        try:
            dum = self.getNode(uri)
            return True
        except Exception as e:
            # logger.debug(str(e))
            return False

    def status(self, uri, code=[200, 303, 302]):
        """Check to see if this given uri points at a containerNode.

        This is done by checking the view=data header and seeing if you get an error.
        """
        return self.open(uri, view='data', head=True).close(code=code)

    def getJobStatus(self, url):
        """ Returns the status of a job """
        return VOFile(url, self.conn, method="GET",
                                    followRedirect=False).read()
