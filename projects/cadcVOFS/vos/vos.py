"""A set of Python Classes for connecting to and interacting with a VOSpace service.

   Connections to VOSpace are made using a SSL X509 certificat which is stored in a .pem file.  
   The certificate is supplied by the user or by the CADC credential server

"""

import logging
import time
import threading
import sys
import os
import errno
import xml.etree.ElementTree as ET
import re
from __version__ import version
# set a 1 MB buffer to keep the number of trips
# around the IO loop small

BUFSIZE=8388608
#BUFSIZE=8192

SERVER=os.getenv('VOSPACE_WEBSERVICE', 'www.cadc.hia.nrc.gc.ca')

class urlparse:
    """Break the URL into parts.

    There is a difference between the 2.5 and 2.7 version of the urlparse.urlparse command, so here I roll my own..."""

    def __init__(self,url):
        import re

        m=re.match("(^(?P<scheme>[a-zA-Z]*):)?(//(?P<netloc>[^/]*))?(?P<path>/?.*)?",url)
        if not m.group:
            return None
        self.scheme=m.group('scheme')
        self.netloc=m.group('netloc')
        self.path=m.group('path')
        
    def __str__(self):
        #return "[scheme: %s, netloc: %s, path: %s, frag: %s, query: %s]" % ( self.scheme, self.netloc, self.path,self.frag,self.query)
        return "[scheme: %s, netloc: %s, path: %s]" % ( self.scheme, self.netloc, self.path)

    

class Connection:
    """Class to hold and act on the X509 certificate"""

    def __init__(self, certfile=None):
        """Setup the Certificate for later usage

        cerdServerURL --- the location of the cadc proxy certificate server
        certfile      --- where to store the certificate, if None then ${HOME}/.ssl or a temporary filename

        The user must supply a valid certificate. 
        """


        #if not certfile:
        #    ## No filename see if we can find a  HOME directory
        #    dirName=os.getenv('HOME')
        #    logging.debug("looking for certificate in %s" % ( dirName))
        #    if not dirName:
        #        raise IOError(errno.EEXIST,"HOME is not defined for your environment")
        #    certDir=os.path.join(dirName,'.ssl')
        #    logging.debug("looking for certificate in %s" % ( certDir))
        #    if not os.access(certDir,os.F_OK):
        #        os.mkdir(certDir)
        #    certfile = os.path.join(certDir,"cadcproxy.pem")
        #    logging.debug("looking for certificate in %s" % ( certfile))
        ## allow anonymous access if no certfile is specified...
        if certfile is not None and not os.access(certfile,os.F_OK):
            raise EnvironmentError(errno.EACCES,"No certifacte file found at %s\n (Perhaps use getCert to pull one)" %(certfile))

        self.certfile=certfile

        logging.debug("Using certificate file %s" % (str(self.certfile)))


    def getConnection(self,url):
        """Create an HTTPSConnection object and return.  Uses the client certificate if None given.

        uri  -- a VOSpace uri (vos://cadc.nrc.ca~vospace/path)
        certFilename -- the name of the certificate pem file.
        """
        parts=urlparse(url)
        ports={"http": 80, "https": 443}
        certfile=self.certfile
        #logging.debug("Trying to connect to %s://%s using %s" % (parts.scheme,parts.netloc,certfile))
        
        import httplib, ssl
        try:
            if parts.scheme=="https":
                connection = httplib.HTTPSConnection(parts.netloc,key_file=certfile,cert_file=certfile,timeout=600)
            else:
                connection = httplib.HTTPConnection(parts.netloc,timeout=600)
        except httplib.NotConnected as e:
            logging.error("HTTP connection to %s failed \n" % (parts.netloc))
            logging.error("%s \n" % ( str(e)))
            raise OSError(errno.ENTCONN,"VOSpace connection failed",parts.netloc)

        if logging.getLogger('root').getEffectiveLevel() == logging.DEBUG :
            connection.set_debuglevel(1)

        ## Try to open this connection. 
        timestart = time.time() 
        while True:
            try:
                connection.connect()
            except httplib.HTTPException as e:
                logging.critical("%s" % ( str(e)))
                logggin.critical("Retrying connection for 1200 seconds") 
                if time.time() - timestart  > 1200:
                    raise e
            except Exception as e:
                logging.error(str(e))
                logging.error("Perhaps your proxy certificate is expired?")
                ex=IOError()
                ex.errno=errno.ECONNREFUSED
                ex.strerror="VOSpace connection failed"
                ex.filename=parts.netloc
                raise ex
            break

        #logging.debug("Returning connection " )
        return connection



class Node:
    """A VOSpace node"""

    IVOAURL="ivo://ivoa.net/vospace/core"
    CADCURL="ivo://cadc.nrc.ca/vospace/core"

    VOSNS="http://www.ivoa.net/xml/VOSpace/v2.0"
    XSINS="http://www.w3.org/2001/XMLSchema-instance"
    TYPE  ='{%s}type' % XSINS
    NODES ='{%s}nodes' % VOSNS
    NODE  ='{%s}node' % VOSNS
    PROTOCOL = '{%s}protocol' % VOSNS
    PROPERTIES='{%s}properties' % VOSNS 
    PROPERTY='{%s}property' % VOSNS
    ACCEPTS='{%s}accepts' % VOSNS
    PROVIDES='{%s}provides' % VOSNS
    ENDPOINT = '{%s}endpoint' % VOSNS
    TARGET = '{%s}target' % VOSNS

    def __init__(self,node,nodeType="vos:DataNode",properties={},xml=None,subnodes=[]):
        """Create a Node object based on the DOM passed to the init method

        if node is a string then create a node named node of nodeType with properties
        """

        if type(node)==unicode or type(node)==str:
            node=self.create(node,nodeType,properties,subnodes=subnodes)

        if node is None:
            raise LookupError("no node found or created?")

        self.node=node
        self.target = None
        self.node.set('xmlns:vos',self.VOSNS)
        self.type=None
        self.props={}
        self.attr={}
        self.xattr={}
        self._nodeList = None
        self.update()
        
    def update(self):
        """Update the convience links of this node as we update the xml file"""

        self.type=self.node.get(Node.TYPE)
        if self.type == None:
            logging.debug("Node type unknown, no node created")
            import xml.etree.ElementTree as ET
            logging.debug(ET.dump(self.node))
            return None
        if self.type == "vos:LinkNode":
            self.target = self.node.findtext(Node.TARGET)

        self.uri=self.node.get('uri')
        self.name=os.path.basename(self.uri)
        for propertiesNode in self.node.findall(Node.PROPERTIES):
            self.setProps(propertiesNode)
        self.isPublic=False
        if self.props.get('ispublic','false')=='true':
            self.isPublic=True
        self.groupwrite = self.props.get('groupwrite','')
        self.groupread = self.props.get('groupread','')
        self.setattr()
        self.setxattr()

    def setProperty(self,key,value):
        """Given a dictionary of props build a properies subelement"""
        properties=self.node.find(Node.PROPERTIES)
        uri="%s#%s" %(Node.IVOAURL,key)
        ET.SubElement(properties,Node.PROPERTY,
                      attrib={'uri': uri,'readOnly': 'false'}).text=value


    def __str__(self):
        import xml.etree.ElementTree as ET
        class dummy:
            pass
        data=[]
        file=dummy()
        file.write=data.append
        ET.ElementTree(self.node).write(file,encoding="UTF-8")
        return "".join(data)

    def setattr(self,attr={}):
        """return a dictionary of attributes associated with the file stored at node

        These attributes are determind from the node on VOSpace.
        """ 
        ## Get the flags for file mode settings.
        from stat import S_IFDIR, S_IFREG
        from stat import S_IRUSR, S_IRGRP, S_IROTH
        from stat import S_IWUSR, S_IWGRP, S_IWOTH
        from stat import S_IXUSR, S_IXGRP, S_IXOTH 
        from os import getgid, getuid

        self.attr={}
        node=self
            
        ## Only one date provided by VOSpace, so use this as all possible dates.
        sdate = node.props.get('date',None)
        atime=time.time()
        if not sdate:
            mtime = atime
        else:
            ### mktime is expecting a localtime but we're sending a UT date, so some correction will be needed
            mtime=time.mktime(time.strptime(sdate[0:-4],'%Y-%m-%dT%H:%M:%S'))
            mtime=mtime - time.mktime(time.gmtime()) + time.mktime(time.localtime())
        self.attr['st_ctime']=attr.get('st_ctime',mtime)
        self.attr['st_mtime']=attr.get('st_mtime',mtime)
        self.attr['st_atime']=atime
        
        ## set the MODE by orring together all flags from stat
        st_mode=0
        if node.type=='vos:ContainerNode':
            st_mode |= S_IFDIR
            self.attr['st_nlink']=len(node.getNodeList())+2
        else:
            self.attr['st_nlink']=1
            st_mode |= S_IFREG

        ## Set the OWNER permissions
        ## All files are read/write/execute by owner...
        st_mode |= S_IRUSR | S_IWUSR | S_IXUSR

        ## Set the GROUP permissions
        if node.props.get('groupwrite',"NONE")!="NONE":
            st_mode |= S_IWGRP
        if node.props.get('groupread',"NONE")!="NONE":
            st_mode |= S_IRGRP
            st_mode |= S_IXGRP

        ## Set the OTHER permissions
        if node.props.get('ispublic','false')=='true':
            ## If you can read the file then you can execute too.
            ## Public does NOT mean writeable.  EVER
            st_mode |= S_IROTH | S_IXOTH

        self.attr['st_mode']=attr.get('st_mode',st_mode)
    
        ## We set the owner and group bits to be those of the currently running process.  
        ## This is a hack since we don't have an easy way to figure these out.  TBD!
        self.attr['st_uid']=attr.get('st_uid',getuid())
        self.attr['st_gid']=attr.get('st_uid',getgid())
        self.attr['st_size']=attr.get('st_size',int(node.props.get('length',0)))

    def setxattr(self, attrs={}):
        """Initialize the attributes using the properties sent with the node"""
        for key in self.props:
            if key in Client.vosProperties:
                continue
            self.xattr[key]=self.props[key]
        return 

    def chwgrp(self,group):
        """Set the groupwrite value for this node"""
        self.groupwrite=group
        return self.changeProp('groupwrite',group)

    def chrgrp(self,group):
        """Set the groupread value for this node"""
        self.groupread=group
        return self.changeProp('groupread', group)

    def setPublic(self,value):
        logging.debug("Setting value of ispublic to %s" % (str(value)))
        return self.changeProp('ispublic', value)

 
    def changeProp(self,key,value):
        """Change the node property 'key' to 'value'. 

        Return 1 if changed.

        This function should be split into 'set' and 'delete'
        """
        import urllib
        #logging.debug("Before change node XML\n %s" % ( self))
        changed=0
        found=False
        properties = self.node.findall(Node.PROPERTIES)
        for props in properties:
            for prop in props.findall(Node.PROPERTY):
                  uri=prop.attrib.get('uri',None)
                  propName=urllib.splittag(uri)[1]
                  if propName != key:
                      continue 
                  found=True
                  if value is None:
                      ## this is actually a delete property
                      prop.attrib['xsi:nil']='true'
                      prop.attrib["xmlns:xsi"]=Node.XSINS
                      prop.text = ""
                      self.props[propName]=None
                  else:
                      prop.text=value
                      changed=1
        if found or value is None:
            return changed
        ### must not have had this kind of property already, so set value
        #logging.debug("Adding a property: %s" %(key))
        propertyNode=ET.SubElement(props,Node.PROPERTY)
        propertyNode.attrib['readOnly']="false"
        ### There should be a '#' in there someplace...
        propertyNode.attrib["uri"]="%s#%s" % (Node.IVOAURL,key)
        propertyNode.text=value
        #logging.debug("After change node XML\n %s" %( self))
        return 1


    def chmod(self,mode):
        """Set the MODE of this Node... 

        translates unix style MODE to voSpace and updates the properties...

        This function is quite limited.  We can make a file publicly 
        readable and we can set turn off group read/write permissions, 
        that's all. """

        import urllib
        
        from stat import S_IFDIR, S_IFREG
        from stat import S_IRUSR, S_IRGRP, S_IROTH
        from stat import S_IWUSR, S_IWGRP, S_IWOTH
        from stat import S_IXUSR, S_IXGRP, S_IXOTH
        changed=0

        #logging.debug("Changing mode to %d" % ( mode))
        if  mode & (S_IROTH ) :   
            changed += self.setPublic('true')
        else:
            changed += self.setPublic('false')
 
        if  mode & (S_IRGRP ):
            
            changed += self.chrgrp(self.groupread)
        else:
            changed += self.chrgrp('')

        if  mode & S_IWGRP :
           changed += self.chwgrp(self.groupwrite)
        else:
           changed += self.chwgrp('')  

        #logging.debug("%d -> %s" % ( changed, changed>0))
        return changed>0


    def create(self,uri,nodeType="vos:DataNode",properties={},subnodes=[]):
        """Build the XML needed to represent a VOSpace node returns an ElementTree represenation of the XML
        
        nodeType   -- the VOSpace node type, likely one of vos:DataNode or vos:ContainerNode
        properties -- a dictionary of the node properties, all assumed to be single words from the IVOA list
        """

        import xml.etree.ElementTree as ET

        ### Build the root node called 'node'
        node=ET.Element("node")
        node.attrib["xmlns"]=Node.VOSNS
        node.attrib["xmlns:vos"]=Node.VOSNS
        node.attrib[Node.TYPE]=nodeType
        node.attrib["uri"]=uri

        ### create a properties section
        if not properties.has_key('type'):
            import mimetypes
            properties['type']=mimetypes.guess_type(uri)[0]
            #logging.debug("set type to %s" % (properties['type']))
        propertiesNode=ET.SubElement(node,Node.PROPERTIES)
        for property in properties.keys():
            if not properties[property]==None :
                propertyNode=ET.SubElement(propertiesNode,Node.PROPERTY)
                propertyNode.attrib['readOnly']="false"
                ### There should be a '#' in there someplace...
                propertyNode.attrib["uri"]="%s#%s" % (Node.IVOAURL,property)
                if len(properties[property])>0:
                    propertyNode.text=properties[property]
        
        ## That's it for link nodes...
        if nodeType == "vos:LinkNode":
            return node

        ### create accepts
        accepts=ET.SubElement(node,Node.ACCEPTS)

        ET.SubElement(accepts,"view").attrib['uri']="%s#%s" % (Node.IVOAURL,"defaultview")

        ### create provides section
        provides=ET.SubElement(node,Node.PROVIDES)
        ET.SubElement(provides,"view").attrib['uri']="%s#%s" % (Node.IVOAURL,'defaultview')
        ET.SubElement(provides,"view").attrib['uri']="%s#%s" % (Node.CADCURL,'rssview')

        ### Only DataNode can have a dataview...
        if nodeType=="vos:DataNode":
            ET.SubElement(provides,"view").attrib['uri']="%s#%s" % (Node.CADCURL,'dataview')

        ### if this is a container node then we need to add an empy directory contents area...
        if nodeType=="vos:ContainerNode":
            nodeList=ET.SubElement(node,Node.NODES)
            for subnode in subnodes:
                nodeList.append(subnode.node)
        #logging.debug(ET.tostring(node,encoding="UTF-8"))

        return node

    def isdir(self):
        """Check if target is a container Node"""
        #logging.debug(self.type)
        if self.type=="vos:ContainerNode":
            return True
        return False

    def getInfo(self):
        """Organize some information about a node and return as dictionary"""
        import re,time,string,math,urllib
        date=time.mktime(time.strptime(self.props['date'][0:-4],'%Y-%m-%dT%H:%M:%S'))
        #if date.tm_year==time.localtime().tm_year:
        #    dateString=time.strftime('%d %b %H:%S',date)
        #else:
        #    dateString=time.strftime('%d %b  %Y',date)
        creator=string.lower(re.search('CN=([^,]*)',self.props.get('creator','CN=unknown_000,')).groups()[0].replace(' ','_')) 
        perm=[]
        writeGroup=""
        readGroup=""
        for i in range(10):
            perm.append('-')
        perm[1]='r'
        perm[2]='w'
        if self.type=="vos:ContainerNode":
            perm[0]='d'
        if self.type=="vos:LinkNode":
            perm[0]='l'
        if self.props.get('ispublic',"false")=="true":
            perm[-3]='r'
        writeGroup = self.props.get('groupwrite','NONE')
        if writeGroup != 'NONE':
            perm[5]='w'
        readGroup = self.props.get('groupread','NONE')
        if readGroup != 'NONE':
            perm[4]='r'
        #logging.debug("%s: %s" %( self.name,self.props))
        return {"permisions": string.join(perm,''),
                "creator": creator,
                "readGroup": readGroup,
                "writeGroup": writeGroup,
                "size": float(self.props.get('length',0)),
                "date": date,
                "target": self.target}

    def getNodeList(self):
        """Get a list of all the nodes held to by a ContainerNode return a list of Node objects"""
        if (self._nodeList is None):
            self._nodeList=[]
            for nodesNode in self.node.findall(Node.NODES):
                for nodeNode in nodesNode.findall(Node.NODE):
                    self.addChild(nodeNode)
        return self._nodeList

    def addChild(self,childEt):
        childNode = Node(childEt)
        self._nodeList.append(childNode)
        return(childNode)

    def getInfoList(self):
        """Retrieve a list of tupples of (NodeName, Info dict)"""
        infoList={}
        for node in self.getNodeList():
            infoList[node.name]=node.getInfo()
        if self.type=="vos:DataNode":
            infoList[self.name]=self.getInfo()
        return infoList.items()
    
    def setProps(self,props):
        """Set the properties of node, given the properties element of that node"""
        for propertyNode in props.findall(Node.PROPERTY):
            self.props[self.getPropName(propertyNode)]=self.getPropValue(propertyNode)
        return


    def getPropName(self,prop):
        """parse the property uri and get the name of the property"""
        import urllib
        (url,propName)=urllib.splittag(prop.get('uri'))
        return propName

    def getPropValue(self,prop):
        """Pull out the value part of node"""
        return prop.text


class VOFile:
    """A class for managing http connecctions"""
    
    def __init__(self,URL,connector,method,size=None, followRedirect=True):
        self.closed=True
        self.resp=503
        self.connector=connector
        self.httpCon=None
        self.timeout=-1
        self.size=size
        self.followRedirect = followRedirect
	self.name=os.path.basename(URL)
        self._fpos=0
        self.open(URL,method)
	logging.debug("Sending back VOFile object for file of size %s" %(str(self.size)))
        
    def tell(self):
	return self._fpos

    def seek(self,offset,loc=os.SEEK_SET):
	if loc == os.SEEK_CUR:
	   self._fpos += offset
        elif loc == os.SEEK_SET:
	   self._fpos = offset
        elif loc == os.SEEK_END:
	   self._fpos = self.size-offset
	return 
        
    def close(self,code=(200, 201, 202, 206, 302, 303, 503)):
        """close the connection"""
        #logging.debug("inside the close")
        if self.closed:
            return
        logging.debug("Closing connection")
        try:
            if self.transEncode is not None:
                self.httpCon.send('0\r\n\r\n')
            self.resp=self.httpCon.getresponse()
	    import time
	    time.sleep(0.1)
	    logging.warning("closing connection for %s" %(self.url))
            self.httpCon.close()
        except Exception as e:
	    raise IOError(errno.ENOTCONN,str(e))
        self.closed=True
        logging.debug("Connection closed")
        return self.checkstatus(codes=code)

    def checkstatus(self, codes=(200, 201, 202, 206, 302, 303, 503, 416)):
        """check the response status"""
        msgs = { 404: "Node Not Found",
                 401: "Not Authorized",
                 409: "Conflict"}
        errnos = { 404: errno.ENOENT,
                   401: errno.EACCES,
                   409: errno.EEXIST }
        logging.debug("status %d for URL %s" % ( self.resp.status,self.url))
        if self.resp.status not in codes:
	    logging.warning("Got status code: %s for %s" %(self.resp.status, self.url))
            from html2text import html2text
            msg = self.resp.read()
            if msg is not None:
                msg = html2text(msg,self.url).strip()
            logging.debug("Error message: %s" % (msg))
            if self.resp.status in [404, 401, 409]:
                if msg is None or len(msg) == 0:
                    msg = msgs[self.resp.status]
                if self.resp.status == 401 and self.connector.certfile is None:
                    msg += " using anonymous access "
                raise IOError(errnos[self.resp.status],msg,self.url)
            raise IOError(self.resp.status,msg,self.url)
        self.size=self.resp.getheader("Content-Length",0)
        return True

    def open(self,URL,method="GET", bytes=None):
        """Open a connection to the given URL"""
        import ssl,httplib, sys, mimetypes
        logging.debug("Opening %s (%s)" % (URL, method))
        self.url=URL
        self.httpCon = self.connector.getConnection(URL)
        #self.httpCon.set_debuglevel(2)
        self.closed=False
        #logging.debug("putting request")
        self.httpCon.putrequest(method,URL)
        userAgent='vos '+version
        if "mountvofs" in sys.argv[0]:
            userAgent='vofs '+version
        self.httpCon.putheader("User-Agent", userAgent)
        self.transEncode = None
	logging.debug("sending headers for file of size %s: " % ( str(self.size)))
        if method in ["PUT"]:
            try:
	       self.size = int(self.size)
               self.httpCon.putheader("Content-Length",self.size)
            except TypeError as e:
	        self.size = None
                self.transEncode = "chunked"
                self.httpCon.putheader("Transfer-Encoding",'chunked')
	elif method in ["POST", "DELETE"]:
	    self.size = None
            self.httpCon.putheader("Transfer-Encoding",'chunked')
            self.transEncode = "chunked"
	if method in ["PUT", "POST", "DELETE"]:
            contentType = "text/xml"
            if method == "PUT":
	        import urllib, os
	        ext = os.path.splitext(urllib.splitquery(URL)[0])[1]
	        logging.debug("Got extension %s" %( ext))
	        if ext in [ '.fz', '.fits', 'fit']:
	           contentType = 'application/fits'
	        else:
                   contentType = mimetypes.guess_type(URL)[0]
	        logging.debug("Guessed content type: %s" % (contentType))
	    if contentType is not None:
                self.httpCon.putheader("Content-Type",contentType)
        if bytes is not None and method=="GET" :
            logging.debug("Range: %s" %(bytes))
            self.httpCon.putheader("Range",bytes)
        self.httpCon.putheader("Accept", "*/*")
        self.httpCon.putheader("Expect", "100-continue")
        self.httpCon.endheaders()
	logging.warning("Opening connection for %s to %s" %(URL,method))
        logging.debug("Done setting headers")


    def read(self,size=None):
        """return size bytes from the connection response"""
	#logging.debug("Starting to read file by closing http(s) connection")
        if not self.closed:
            self.close()
        bytes=None
        #if size != None:
        #    bytes = "bytes=%d-" % ( self._fpos)
        #    bytes = "%s%d" % (bytes,self._fpos+size)
        #self.open(self.url,bytes=bytes,method="GET")
        #self.close(code=[200,206,303,302,503,404,416])
        if self.resp.status == 416:
            return ""
        # check the most likely response first
        if self.resp.status == 200:
            buff=self.resp.read(size)
            #logging.debug(buff)
            return buff
        if self.resp.status == 206:
            buff=self.resp.read(size)
            self._fpos += len(buff)
            logging.debug("left file pointer at: %d" %(self._fpos))
            return buff
        elif self.resp.status == 404:
            raise IOError(errno.ENFILE,self.resp.read())
        elif self.resp.status == 303 or self.resp.status == 302:
            URL = self.resp.getheader('Location',None)
            logging.debug("Got redirect URL: %s" % ( URL))
	    self.url = URL
            if not URL:
                logging.debug("Raising error?")
                raise IOError(errno.ENOENT,"No Location on redirect",self.url)
            if self.followRedirect:
                self.open(URL,"GET")
                logging.debug("Following redirected URL:  %s" % (URL))
                return self.read(size)
            else:
                logging.debug("Got url:%s from redirect but not following" % ( self.url))
                return self.url
        elif self.resp.status == 503:
            ## try again in Retry-After seconds or fail
            logging.error("Got 503: server busy on %s" % (self.url))
            logging.error("Message:  %s" % ( self.resp.read()))
            try:
              ras=int(self.resp.getheader("Retry-After",5))
	    except:
	      ras=5
            logging.error("retrying in %d seconds" % (ras))
            time.sleep(int(ras))
            self.open(self.url,"GET")
            return self.read(size)
        # line below can be removed after we are sure all codes
        # are caught
        raise IOError(self.resp.status,"unexpected server response %s (%d)" % ( self.resp.reason, self.resp.status),self.url)


    def write(self,buf):
        """write buffer to the connection"""
        if not self.httpCon or self.closed:
            raise OSError(errno.ENOTCONN,"no connection for write",self.url)
        ### If we are sending chunked then we need to frame the transfer
        if self.transEncode is not None:
            self.httpCon.send('%X\r\n' % len(buf))
            self.httpCon.send(buf+'\r\n')
        else:
            self.httpCon.send(buf)
        return len(buf)


class Client:
    """The Client object does the work"""

    VOServers={'cadc.nrc.ca!vospace': SERVER,
               'cadc.nrc.ca~vospace': SERVER}

    VOTransfer='/vospace/synctrans' 

    ### reservered vospace properties, not to be used for extended property setting
    vosProperties=["description", "type", "encoding", "MD5", "length", "creator","date",
                   "groupread", "groupwrite", "ispublic"]


    def __init__(self,certFile=os.path.join(os.getenv('HOME'),'.ssl/cadcproxy.pem'),
                 rootNode=None,conn=None,archive='vospace'):
        """This could/should be expanded to set various defaults"""
        if certFile is not None and not os.access(certFile,os.F_OK):
            ### can't get this certfile
            logging.debug("Failed to access certfile %s " % ( certFile))
            logging.debug("Using anonymous mode, try getCert if you want to use authenitcation")
            certFile = None
        if certFile is None:
            self.protocol="http"
        else:
            self.protocol="https"
        if not conn:
            conn=Connection(certfile=certFile)
        self.conn=conn
        self.VOSpaceServer="cadc.nrc.ca!vospace"
        self.rootNode=rootNode
        self.archive=archive
        return

    def copy(self,src,dest,sendMD5=False):
        """copy to/from vospace"""
        import os,hashlib
    
        checkSource=False
        if src[0:4]=="vos:":
            srcNode = self.getNode(src)
            srcSize = srcNode.attr['st_size']
            srcMD5 = srcNode.props.get('MD5','d41d8cd98f00b204e9800998ecf8427e')
            fin=self.open(src,os.O_RDONLY,view='data')
            fout=open(dest,'w')
            checkSource = True
        else:
            srcSize=os.lstat(src).st_size
            fin=open(src,'r')
            fout=self.open(dest,os.O_WRONLY,size=srcSize)
    
        destSize=0
        md5=hashlib.md5()
        while True:
            buf=fin.read(BUFSIZE)
            # In this tight loop I don't think you want logging
            # logging.debug("Read %d bytes from %s" % ( len(buf),src))
            if len(buf)==0:
                break
            fout.write(buf)
            md5.update(buf)
            destSize+=len(buf)
        fout.close()
        fin.close()

        if checkSource: 
            if srcNode.type != "vos:LinkNode" :
                checkMD5 = srcMD5
            else:
                ### this is a hack .. we should check the data integraty of links too... just not sure how
                checkMD5 = md5.hexdigest()
        else:
            checkMD5 = self.getNode(dest).props.get('MD5','d41d8cd98f00b204e9800998ecf8427e')
        
        if sendMD5:
            if checkMD5 != md5.hexdigest():
		logging.error("MD5s don't match ( %s -> %s ) " % ( src, dest))
                raise OSError(errno.EIO,"MD5s don't match",src)
            return md5.hexdigest()
        if destSize != srcSize and not srcNode.type == 'vos:LinkNode'  :
	    logging.error("sizes don't match ( %s -> %s ) " % ( src, dest))
            raise IOError(errno.EIO,"sizes don't match",src)
        return destSize


    def fixURI(self,uri):
        """given a uri check if the authority part is there and if it isn't then add the CADC vospace authority"""
        from errno import EINVAL
        parts=urlparse(uri)
	if parts.scheme is None:
            uri=self.rootNode+uri
        parts=urlparse(uri)
        if parts.scheme!="vos":
	    # Just past this back, I don't know how to fix...
	    return uri
            #raise IOError(errno.EINVAL,"Invalid vospace URI",uri)
        import re
        ## Check that path name compiles with the standard
        filename=os.path.basename(parts.path)
        #logging.debug("Checking file name: %s" %( filename))
        #logging.debug("Result: %s" % (re.match("^[\_\-\(\)\=\+\!\,\;\:\@\&\*\$\.\w\~]*$",filename)))
        if not re.match("^[\_\-\(\)\=\+\!\,\;\:\@\&\*\$\.\w\~]*$",filename):
            raise IOError(errno.EINVAL,"Illegal vospace container name",filename)

        ## insert the default VOSpace server if none given
        host=parts.netloc
        if not host or host=='':
            host=self.VOSpaceServer
        path=os.path.normpath(parts.path).strip('/')
        return "%s://%s/%s" % (parts.scheme, host, path)

    
    def getNode(self,uri,limit=0):
        """connect to VOSpace and download the definition of vospace node

        uri   --- a voSpace node in the format vos:/vospaceName/nodeName
        limit --- load children nodes in batches of limit
        """
        #logging.debug("Getting node %s" % ( uri))
        xmlObj=self.open(uri,os.O_RDONLY, limit=0)
        dom=ET.parse(xmlObj)
        #logging.debug("%s" %( str(dom)))
        node = Node(dom.getroot())
        # If this is a container node and the nodlist has not already been set, try to load the children.
        # this would be better deferred until the children are actually needed, however that would require
        # access to a connection when the children are accessed, and thats not easy.
        # IF THE CALLER KNOWS THEY DON'T NEED THE CHILDREN THEY CAN SET LIMIT=0 IN THE CALL
        if node.isdir() and limit>0:
            node._nodeList=[]
            #logging.debug("Loading children")
            nextURI = None
            again = True
            while again:
                again = False
                getChildrenXMLDoc=self.open(uri,os.O_RDONLY, nextURI=nextURI)
                getChildrenDOM = ET.parse(getChildrenXMLDoc)
                for nodesNode in getChildrenDOM.findall(Node.NODES):
                    for child in nodesNode.findall(Node.NODE):
                        if child.get('uri') != nextURI:
                            childNode = node.addChild(child)
                            nextURI = childNode.uri
                            #logging.debug("added child %s" % childNode.uri)
                            again = True
        return(node)
                    

    def getNodeURL(self,uri, method='GET', view=None, limit=0, nextURI=None):            
        """Split apart the node string into parts and return the correct URL for this node"""
        import urllib

        uri   = self.fixURI(uri)
        if method == 'GET' and view == 'data':
            logging.debug("Using _get ")
            return self._get(uri)


        if method in ('PUT'):
            logging.debug("Using _put")
            return self._put(uri)

        parts = urlparse(uri)
        path  = parts.path.strip('/')
        server= Client.VOServers.get(parts.netloc)        
        #logging.debug("Node URI: %s" %( uri))


        ### this is a GET so we might have to stick some data onto the URL...
        fields={}
        if limit is not None:
            fields['limit']=limit
        if view is not None:
            fields['view'] = view
        if nextURI is not None:
            fields['uri'] = nextURI
        data=""
        if len(fields) >0 : 
               data="?"+urllib.urlencode(fields)
        URL = "%s://%s/vospace/nodes/%s%s" % ( self.protocol, server, parts.path.strip('/'), data)
        #logging.debug("Node URL %s (%s)" % (URL, method))
        return URL

    def link(self,srcURI,linkURI):
        """Make linkURI point to srcURI"""
        linkNode=Node(self.fixURI(linkURI),nodeType="vos:LinkNode")
        ET.SubElement(linkNode.node,"target").text=self.fixURI(srcURI)
        URL=self.getNodeURL(linkURI)
        f=VOFile(URL,self.conn,method="PUT",size=len(str(linkNode)))
        f.write(str(linkNode))
        return f.close()
        

    def move(self,srcURI,destURI):
        """Move srcUri to targetUri"""
        transfer=ET.Element("transfer")
        transfer.attrib['xmlns']=Node.VOSNS
        transfer.attrib['xmlns:vos']=Node.VOSNS
        ET.SubElement(transfer,"target").text=self.fixURI(srcURI)
        ET.SubElement(transfer,"direction").text=self.fixURI(destURI)
        ET.SubElement(transfer,"keepBytes").text="false"

        url = "%s://%s/%s" % ( self.protocol, SERVER, Client.VOTransfer)
        con=self.open(srcURI,URL=url,mode=os.O_APPEND,size=len(ET.tostring(transfer)))
        con.write(ET.tostring(transfer))
        con.read()
        if  con.resp.status==200:
           return True
        return  False

    def _get(self,uri):
        return self.transfer(uri,"pullFromVoSpace")
    
    def _put(self,uri):
        return self.transfer(uri,"pushToVoSpace")

    def transfer(self,uri,direction):
        """Build the transfer XML document"""
        protocol = {"pullFromVoSpace": "%sget" % ( self.protocol ) ,
                    "pushToVoSpace": "%sput" % (self.protocol ) }
        transferXML = ET.Element("transfer")
        transferXML.attrib['xmlns'] = Node.VOSNS
        transferXML.attrib['xmlns:vos'] = Node.VOSNS
        ET.SubElement(transferXML,"target").text=uri
        ET.SubElement(transferXML,"direction").text = direction
        ET.SubElement(transferXML,"view").attrib['uri']="%s#%s" % ( Node.IVOAURL, "defaultview")
        ET.SubElement(transferXML,"protocol").attrib['uri']="%s#%s" % ( Node.IVOAURL, protocol[direction] )
        logging.debug("Transfer XML doc: %s" % (ET.tostring(transferXML)))
        url = "%s://%s%s" % ( self.protocol, SERVER, Client.VOTransfer)
        con = VOFile(url, self.conn, method = "POST", followRedirect=False)
        con.write(ET.tostring(transferXML)) 
        transURL = con.read()
        logging.debug("Got back %s from trasnfer " % ( con))
        con = VOFile(transURL, self.conn, method = "GET", followRedirect=True)
        F = ET.parse(con)
	
        P = F.find(Node.PROTOCOL)
        logging.debug("Transfer protocol: %s" % (str(P)))
        if P is None:
            self.getTransferError(transURL, uri)
            raise OSError(errno.EBUSY)
        return P.findtext(Node.ENDPOINT)

    def getTransferError(self,url, uri):
	"""Follow a transfer URL to the Error message"""
        import re
        match = re.match('(.*)/results/transferDetails',url)
        if match is None:
            raise OSError(errno.ENOENT,"Bad Error response for VOSpace")
        errorURL = match.group(1)+"/error"
	con = VOFile(errorURL, self.conn, method = "GET")
	errorMessage = con.read()
	logging.debug("Got transfer error %s on URI %s" % ( errorMessage, uri))
	import errno
	errorCodes = { 'NodeNotFound': errno.ENOENT,
                       'PermissionDenied': errno.EACCES,
                       'OperationNotSupported': errno.EOPNOTSUPP,
                       'InternalFault': errno.EFAULT,
                       'ProtocolNotSupported': errno.EPFNOSUPPORT,
                       'ViewNotSupported': errno.ENOSYS,
                       'InvalidArgument': errno.EINVAL,
                       'InvalidURI': errno.EFAULT,
                       'InvalidData': errno.EOPNOTSUPP,
                       'TransferFailed': errno.EIO }
        raise OSError(errorCodes.get(errorMessage,errno.ENOENT), "%s: %s" % ( uri, errorMessage)) 

                    
    def open(self, uri, mode=os.O_RDONLY, view=None, head=False, URL=None, limit=None, nextURI=None,size=None):
        """Connect to the uri as a VOFile object"""
	
	### sometimes this is called with mode from ['w', 'r']
        ### really that's an error, but I thought I'd just accept those are os.O_RDONLY
	if type(mode)==str:
	   mode=os.O_RDONLY

        # the URL of the connection depends if we are 'getting', 'putting' or 'posting'  data
        method=None
        if mode == os.O_RDONLY:
            method="GET"
        elif mode & ( os.O_WRONLY | os.O_CREAT) :
            method="PUT"
        elif mode & os.O_APPEND :
            method="POST"
        elif mode & os.O_TRUNC: 
            method="DELETE"
        if head:
            method="HEAD"
        if not method:
            raise IOError(errno.EOPNOTSUPP,"Invalid access mode", mode)
        if URL is None:
            ### we where given one, see if getNodeURL can figure this out.
            URL=self.getNodeURL(uri, method=method, view=view,limit=limit,nextURI=nextURI)
        logging.debug(URL)
        if URL is None:
            ## Dang... getNodeURL failed... maybe this is a LinkNode?
            ## if this is a LinkNode then maybe there is a Node.TARGET I could try instead...
            node = self.getNode(uri)
            if node.type == "vos:LinkNode":
                ## yeah...
                target = node.node.findtext(Node.TARGET)
                logging.debug(target)
                if target is None:
                    ### hmm. well, that shouldn't have happened.
                    return None
                import re
                if re.search("^vos\://cadc\.nrc\.ca[!~]vospace",target) is not None:
                    ### try opening this target directly, cross your fingers.
                    return self.open(target,mode,view,head,URL,limit,nextURI,size)
                else: 
                    ### hmm. just try and open the target, maybe python will understand it.
                    logging.debug("Opening %s with urllib2" % ( target))
                    import urllib2
                    return urllib2.urlopen(target)
        else:
            return VOFile(URL,self.conn,method=method,size=size)
        return None


    def addProps(self,node):
        """Given a node structure do a POST of the XML to the VOSpace to update the node properties"""
        #logging.debug("Updating %s" % ( node.name))
        #logging.debug(str(node.props))
        ## Get a copy of what's on the server
        storedNode=self.getNode(node.uri)
        for prop in storedNode.props:
            if prop in node.props and storedNode.props[prop]==node.props[prop] and node.props[prop] is not None:
                del(node.props[prop])
        for properties in node.node.findall(Node.PROPERTIES):
            node.node.remove(properties)
        #logging.debug(str(node))
        properties=ET.Element(Node.PROPERTIES)
        #logging.debug(node.props)
        for prop in node.props:
            property=ET.SubElement(properties,Node.PROPERTY,attrib={'readOnly': 'false', 'uri': "%s#%s" % (Node.IVOAURL, prop)})
            if node.props[prop] is None:
                property.attrib['xsi:nil']='true'
                property.attrib["xmlns:xsi"]=Node.XSINS
                property.text=""
            else:
                property.text=node.props[prop]
        node.node.insert(0,properties)
        #logging.debug(str(node))
        f=self.open(node.uri,mode=os.O_APPEND, size=len(str(node)))
        f.write(str(node))
        f.close()
        return 

    def create(self,node):
        f = self.open(node.uri,mode=os.O_CREAT, size=len(str(node)))
        f.write(str(node))
        return f.close()

    def update(self,node):
        ##logging.debug(str(node))
        ## Now, remove from the node.node properties those entries that
        ## match the stored values
        if 1==2:
          for properties in storedNode.node.findall(Node.PROPERTIES):
            for prop in properties.findall(Node.PROPERTY):
                propName = prop.get('uri',None)
                if propName is not None:
                    for properties2 in node.node.findall(Node.PROPERTIES):
                        for prop2 in properties2.findall(Node.PROPERTY):
                            propName2=prop2.get('uri',None)
                            if propName == propName2:
                                if prop2.text==prop.text:
                                    properties2.remove(prop2)
        #logging.debug(str(node))
        f=self.open(node.uri,mode=os.O_APPEND,size=len(str(node)))
        f.write(str(node))
        f.close()

    def mkdir(self,uri):
        node = Node(self.fixURI(uri),nodeType="vos:ContainerNode")
        URL=self.getNodeURL(uri)
        f=VOFile(URL,self.conn,method="PUT",size=len(str(node)))
        f.write(str(node))
        return f.close()
    
    def delete(self,uri):
        """Delete the node"""
        logging.debug("%s" % (uri))
        return self.open(uri,mode=os.O_TRUNC).close()

    def getInfoList(self,uri):
        """Retrieve a list of tupples of (NodeName, Info dict)"""
        infoList={}
        node = self.getNode(uri,limit=1)
        logging.debug(str(node))
        while node.type == "vos:LinkNode":
            uri = node.target
            node = self.getNode(uri, limit=1)
            logging.debug(uri)
        logging.debug(str(node.getNodeList()))
        for thisNode in node.getNodeList():
            logging.debug(str(thisNode.name))
            infoList[thisNode.name]=thisNode.getInfo()
        if node.type=="vos:DataNode":
            infoList[node.name]=node.getInfo()
        return infoList.items()


    def listdir(self,uri):
        """Walk through the directory structure a al os.walk"""
        #logging.debug("getting a listing of %s " % ( uri))
        names=[]
        node = self.getNode(uri,limit=500)
        logging.debug(str(node))
        while node.type == "vos:LinkNode":
            uri = node.target
            logging.debug(uri)
            node = self.getNode(uri,limit=500)
        for thisNode in node.getNodeList():
            names.append(thisNode.name)
        return names

    def isdir(self,uri):
        """Check to see if this given uri points at a containerNode or is a link to one."""
	try:
	    node=self.getNode(uri,limit=0)
            logging.debug(node.type)
            while node.type == "vos:LinkNode":
                uri=node.target
                logging.debug(uri)
                if uri[0:4] == "vos:":
                    logging.debug(uri)
                    node=self.getNode(uri,limit=0)
                else:
                    return False
            if node.type == "vos:ContainerNode":
                return True
	except Exception as e:
            logging.debug(str(e))
        return False

    def isfile(self,uri):
        try:
	    return self.status(uri)
	except:
	    return False

    def access(self,uri,mode=os.O_RDONLY):
        """Test for existance"""
	try:
            dum=self.getNode(uri)
	    return True
        except Exception as e:
            logging.debug(str(e))
	    return False

    def status(self,uri,code=[200,303,302]):
        """Check to see if this given uri points at a containerNode.

        This is done by checking the view=data header and seeing if you get an error.
        """
        return self.open(uri,view='data',head=True).close(code=code)
        

