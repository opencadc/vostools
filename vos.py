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


class urlparse:
    """Break the URL into parts.

    There is a difference between the 2.5 and 2.7 version of the urlparse.urlparse command, so here I roll my own..."""

    def __init__(self,url):
        import re

        m=re.match("((?P<scheme>\S*):)?(//(?P<netloc>[^/]*))?(?P<path>/?[^#?]*)(#(?P<frag>[^?]*))?(\?(?P<query>.*))?",url)
        if not m.group:
            return None
        self.scheme=m.group('scheme')
        self.netloc=m.group('netloc')
        self.path=m.group('path')
        self.frag=m.group('frag')
        self.query=m.group('query')
        
    def __str__(self):
        return "[scheme: %s, netloc: %s, path: %s, frag: %s, query: %s]" % ( self.scheme, self.netloc, self.path,self.frag,self.query)


    

from urllib import FancyURLopener
class MyFancyURLopener(FancyURLopener):
    """A sub-class of FancyURLopenr that oeverwrides the password method 

    get_user_passwd()  -- check the .netrc file for users password, prompt if none
    """


    def get_user_passwd(self,host,realm,clear_cache=0):
        """retrieve user/password for ~/.netrc or prompt. Return (username,password)"""

        # Check if credentials are in credential cache... they can be added programmatically else where.
        key = realm + '@' + host.lower()
        if key in self.auth_cache:
            if clear_cache:
                del self.auth_cache[key]
            else:
                return self.auth_cache[key]

        # Look in the users .netrc file.
        import netrc,os
        netrcFilename=os.path.join(os.getenv('HOME'),'.netrc')
        if os.access(netrcFilename,os.R_OK):    
            auth=netrc.netrc().authenticators(host)
            user, passwd = auth[0],auth[2]
        # prompt the user, if need be.
        if not user or not passwd:
            user, passwd = self.prompt_user_passwd(host, realm)
        if user or passwd: self.auth_cache[key] = (user, passwd)

        # send back what we got.
        return user, passwd

class Connection:
    """Class to hold and act on the X509 certificate"""

    def __init__(self,credServerURL="http://www.cadc.hia.nrc.gc.ca/cred/proxyCert",
                 certfile=None,overwrite=False,save=True,data={'daysValid': 1}):
        """Setup the Certificate for later usage

        cerdServerURL --- the location of the cadc proxy certificate server
        certfile      --- where to store the certificate, if None then ${HOME}/.ssl or a temporary filename
        overwrite     --- overwrite an existing and valid certificate?
        save          --- save the certificate for later use? if not then use a tempfilename.

        If the user would like to use an existing certificate that is valid then overwrite should be set to False and the 
        name of the valid certificate sent as certFileName.
        """

        ## figure out a filename and open that file for writing
        if not certfile:
            ## No filename see if we can find a  HOME directory
            dirName=os.getenv('HOME')
            logging.debug("looking for certificate in %s" % ( dirName))
            if not dirName:
                raise IOError("Can't find location for certificate file")
            certDir=os.path.join(dirName,'.ssl')
            logging.debug("looking for certificate in %s" % ( certDir))
            if not os.access(certDir,os.F_OK):
                os.mkdir(certDir)
            certfile = os.path.join(certDir,"cadcproxy.pem")
            logging.debug("looking for certificate in %s" % ( certfile))
        if not os.access(certfile,os.F_OK):
            overwrite=True

        logging.debug("requesting password")
        (self.username,self.passwd)=self.getUserPassword()

        self.certfile=certfile
        if overwrite:
            self.getCert()

	logging.info("Using certificate file %s" % (self.certfile))


    def getUserPassword(self,host='www.cadc-ccda.hia-iha.nrc-cnrc.gc.ca'):
        import netrc,getpass,os
        logging.debug("Getting the username/password for host (%s) from your .netrc filie " % ( host))
        if os.access(os.path.join(os.environ.get('HOME','/'),".netrc"),os.R_OK):
            auth=netrc.netrc().authenticators(host)
        else:
            auth=False
        if not auth:
            logging.debug("Didn't get username and password from .netrc so prompting" )
            # Get info from commandline
            sys.stdout.write("CADC Username: ")
            username=sys.stdin.readline().strip('\n')
            password=getpass.getpass().strip('\n')
        else:
            username=auth[0]
            password=auth[2]
        logging.debug("Using username %s and password '%d'" % ( username, hash(password)))
        return (username,password)

    def getCert(self,certHost='www.cadc-ccda.hia-iha.nrc-cnrc.gc.ca',
                certQuery="/cred/proxyCert?daysValid=1"):
        """Access the cadc certificate server"""

        import urllib2

        logging.debug("Pulling a short lived proxy certificate using your username and password")
        ## Example taken from voidspace.org.uk
        # create a password manager
        password_mgr = urllib2.HTTPPasswordMgrWithDefaultRealm()

        # Add the username and password.
        # If we knew the realm, we could use it instead of ``None``.
        top_level_url = "http://"+certHost
        password_mgr.add_password(None, top_level_url, self.username, self.passwd)

        handler = urllib2.HTTPBasicAuthHandler(password_mgr)

        # create "opener" (OpenerDirector instance)
        opener = urllib2.build_opener(handler)

        # Install the opener.   
        urllib2.install_opener(opener)

        # Now all calls to urllib2.urlopen use our opener.

        r= urllib2.urlopen("http://"+certHost+certQuery)
        w= file(self.certfile,'w')
        while True:
            buf=r.read()
            if not buf:
                break
            w.write(buf)
        w.close()
        r.close()
        return 

    def getConnection(self,url):
        """Create an HTTPSConnection object and return.  Uses the client certificate if None given.

        uri  -- a VOSpace uri (vos://cadc.nrc.ca~vospace/path)
        certFilename -- the name of the certificate pem file.
        """
        parts=urlparse(url)
        ports={"http": 80, "https": 443}
        certfile=self.certfile
        logging.info("Connecting to %s://%s using %s" % (parts.scheme,parts.netloc,certfile))
        
        import httplib
        try:
            if parts.scheme=="https":
                connection = httplib.HTTPSConnection(parts.netloc,key_file=certfile,cert_file=certfile,timeout=600)
            else:
                connection = httplib.HTTPConnection(parts.netloc,timeout=600)
        except httplib.NotConnected:
	    logging.error("HTTP connection to %s failed " % (parts.netloc))
            raise IOError("Failed while trying to connect to the VOSpace service")
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
    PROPERTIES='{%s}properties' % VOSNS
    PROPERTY='{%s}property' % VOSNS
    ACCEPTS='{%s}accepts' % VOSNS
    PROVIDES='{%s}provides' % VOSNS
    
    def __init__(self,node,nodeType="vos:DataNode",properties={},xml=None,subnodes=[]):
        """Create a Node object based on the DOM passed to the init method

        if node is a string then create a node named node of nodeType with properties
        """

        if type(node)==str:
            node=self.create(node,nodeType,properties,subnodes=subnodes)

        if node is None:
            raise LookupError("no node found or created?")

        self.node=node
        self.node.set('xmlns:vos',self.VOSNS)
        self.type=None
        self.props={}
        self.attr={}
        self.update()
        
    def update(self):
        """Update the convience links of this node as we update the xml file"""

        self.type=self.node.get(Node.TYPE)
        if self.type == None:
            logging.warning("Node type unknown, no node created")
            import xml.etree.ElementTree as ET
            logging.debug(ET.dump(self.node))
            return None

        self.nodeList=self.getNodeList()
        self.uri=self.node.get('uri')
        self.name=os.path.basename(self.uri)
        for propertiesNode in self.node.findall(Node.PROPERTIES):
            self.setProps(propertiesNode)
        self.isPublic=False
        if self.props.get('ispublic','false')=='true':
            self.isPublic=True
        self.groupwrite = self.props.get('groupwrite','NONE')
        self.groupread = self.props.get('groupread','NONE')
        self.setattr()

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
            if time.daylight:
                mtime=mtime-time.altzone
            else:
                mtime=mtime-time.timezone
        self.attr['st_ctime']=attr.get('st_ctime',mtime)
        self.attr['st_mtime']=attr.get('st_mtime',mtime)
        self.attr['st_atime']=atime
        
        ## set the MODE by orring together all flags from stat
        st_mode=0
        if node.type=='vos:ContainerNode':
            st_mode |= S_IFDIR
            self.attr['st_nlink']=len(node.nodeList)+2
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
	"""Change the node property 'key' to 'value'.  Return 1 if changed."""
        import urllib
        logging.debug("Before change node is : %s" % ( self))
        changed=0
	properties = self.node.findall(Node.PROPERTIES)
	for props in properties:
	    for prop in props.findall(Node.PROPERTY):
                  uri=prop.attrib.get('uri',None)
                  propName=urllib.splittag(uri)[1]
                  if propName != key:
                      continue
                  if prop.text != value:
                      prop.text=value
	              changed=1
                  logging.debug("After change node is : %s" %( self))
		  return changed
        ### must not have had this kind of property already, so set value
        propertyNode=ET.SubElement(properties[0],Node.PROPERTY)
        propertyNode.attrib['readOnly']="false"
        ### There should be a '#' in there someplace...
        propertyNode.attrib["uri"]="%s#%s" % (Node.IVOAURL,key)
        propertyNode.text=value
        logging.debug("After change node is : %s" %( self))
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

        logging.debug("Changing mode to %d" % ( mode))
        if  mode & (S_IROTH ) :   
            changed += self.setPublic('true')
        else:
            changed += self.setPublic('false')
 
	if  mode & (S_IRGRP ):
            
            changed += self.chrgrp(self.groupread)
        else:
            changed += self.chrgrp('NONE')

	if  mode & S_IWGRP :
	   changed += self.chwgrp(self.groupwrite)
        else:
	   changed += self.chwgrp('NONE')  

        logging.debug("%d -> %s" % ( changed, changed>0))
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
        #node.attrib["xmlns:xsi"]=Node.XSINS
        node.attrib[Node.TYPE]=nodeType
        node.attrib["busy"]="false"
        node.attrib["uri"]=uri

        ### create a properties section
        if not properties.has_key('type'):
            import mimetypes
            properties['type']=mimetypes.guess_type(uri)[0]
        propertiesNode=ET.SubElement(node,Node.PROPERTIES)
        for property in properties.keys():
            if not properties[property]==None :
                propertyNode=ET.SubElement(propertiesNode,Node.PROPERTY)
                propertyNode.attrib['readOnly']="false"
                ### There should be a '#' in there someplace...
                propertyNode.attrib["uri"]="%s#%s" % (Node.IVOAURL,property)
                if len(properties[property])>0:
                    propertyNode.text=properties[property]
        
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
                print subnode
        #logging.debug(ET.tostring(node,encoding="UTF-8"))

        return node

    def isdir(self):
        """Check if target is a container Node"""
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
        creator=string.lower(re.search('CN=([^,]*)',self.props['creator']).groups()[0].replace(' ','_'))
        perm=[]
        writeGroup=""
        readGroup=""
        for i in range(10):
            perm.append('-')
        perm[1]='r'
        perm[2]='w'
        if self.type=="vos:ContainerNode":
            perm[0]='d'
        if self.props.get('ispublic',"false")=="true":
            perm[-3]='r'
        writeGroup = self.props.get('groupwrite','NONE')
        if writeGroup != 'NONE':
            perm[5]='w'
        readGroup = self.props.get('groupread',"NONE")
        if readGroup != "NONE":
            perm[4]='r'
        return {"permisions": string.join(perm,''),
                "creator": creator,
                "readGroup": readGroup,
                "writeGroup": writeGroup,
                "size": float(self.props['length']),
                "date": date}

    def getNodeList(self):
        """Get a list of all the nodes held to by a ContainerNode return a list of Node objects"""
        nodeList=[]
        for nodesNode in self.node.findall(Node.NODES):
            for nodeNode in nodesNode.findall(Node.NODE):
                nodeList.append(Node(nodeNode))
        return nodeList

    def getInfoList(self,longList=True):
        """Retrieve a list of tupples of (NodeName, Info dict)"""
        infoList={}
        for node in self.nodeList:
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
    
    def __init__(self,URL,connector,method):

        self.closed=True
        self.resp=503
        self.connector=connector
        self.httpCon=None
	self.timeout=-1
        self.open(URL,method)
	

        
    def close(self):
        """close the connection"""
        if self.closed:
            return
        logging.debug("Closing connection")
        self.httpCon.send('0\r\n\r\n')
        self.resp=self.httpCon.getresponse()
        self.httpCon.close()
        self.closed=True
        logging.info("Connection closed")
        self.checkstatus()

    def checkstatus(self):
        """check the response status"""
        logging.info("status %d for URL %s" % ( self.resp.status,self.url))
        if self.resp.status not in (200, 201, 202, 303, 503):
            logging.debug(self.resp.read())
            raise IOError(self.resp.status,"unexpected server response %s (%d) for URL %s" % ( self.resp.reason, self.resp.status, self.url))

    def open(self,URL,method):
        """Open a connection to the given URL"""
        import ssl
        logging.info("Connecting to %s for (%s)" % (URL, method))
        self.url=URL
        self.httpCon = self.connector.getConnection(URL)
        if self.timeout < 0 : 
	    self.timeout=time.time()
        try:
            self.httpCon.connect()
        except ssl.SSLError as e:
            logging.critical("%s" % (e.strerror))
            if e.errno != 1:
                raise
            self.connector.getCert()
	    if time.time() - self.timeout  < 200:
                return self.open(URL,method)
        except httplib.HTTPException as e:
	    logging.critical("%s" % ( e.strerror))
	    if time.time() - self.timeout  < 1200:
	        return self.open(URL,method)
            raise
        self.closed=False
        self.httpCon.putrequest(method,URL)
        
        self.httpCon.putheader("Transfer-Encoding",'chunked')
        self.httpCon.putheader("Accept", "*/*")
        self.httpCon.endheaders()
	self.timeout = -1


    def read(self,size=None):
        """return size bytes from the connection response"""
        if not self.closed:
            self.close()
        if self.resp.status == 404:
            return None
        if self.resp.status == 303:
            URL = self.resp.getheader('Location',None)
            if not URL:
                raise IOError("Page appears to be missing...")
            self.open(URL,"GET")
            return self.read(size)
        if self.resp.status == 503:
            ## try again in Retry-After seconds or fail
            logging.warning("Server is too busy to send %s" % ( URL))
            ras=self.resp.getheader("Retry-After",None)
            if not ras:
                logging.warning("no retry-after in header, so raising error")
                raise IOError("Server overloaded")
            ras=int(ras)
            logging.warning("Retrying in %d seconds" % (int(ras)))
            time.sleep(int(ras))
            self.open(self.url,"GET")
            return self.read(size)
        return self.resp.read(size)


    def write(self,buf):
        """write buffer to the connection"""
        if not self.httpCon or self.closed:
            raise IOError("no connection to write too")
        self.httpCon.send('%X\r\n' % len(buf))
        self.httpCon.send(buf+'\r\n')
        return len(buf)


class Client:
    """The Client object does the work"""

    VOServers={'cadc.nrc.ca!vospace': "www.cadc-ccda.hia-iha.nrc-cnrc.gc.ca",
               'cadc.nrc.ca~vospace': "www.cadc-ccda.hia-iha.nrc-cnrc.gc.ca"}

    vosProperties=["description", "type", "encoding", "MD5",
                   "groupread", "groupwrite", "ispublic"]


    def __init__(self,certFile=os.path.join(os.getenv('HOME'),'.ssl/cadcproxy.pem'),
                 overwrite=False,rootNode=None,conn=None):
        """This could/should be expanded to set various defaults"""
        if not conn:
            conn=Connection(certfile=certFile,overwrite=overwrite)
        self.conn=conn
        self.VOSpaceServer="cadc.nrc.ca!vospace"
        #self.urlopener=MyFancyURLopener(None,cert_file=self.cert.getFilename(),key_file=self.cert.getFilename())
        self.rootNode=rootNode
        return

    def fixURI(self,uri):
        """given a uri check if the server part is there and if it isn't update that"""
        logging.debug("trying to fix URL: %s" % ( uri))
        if uri[0:4] != "vos:":
            uri=self.rootNode+uri
        parts=urlparse(uri)
        if parts.scheme!="vos":
            logging.critical("%s This is not a valid vospace URI, no vos:" % (uri))
            return None
        ## insert the default VOSpace server if none given
        host=parts.netloc
        if not host or host=='':
            host=self.VOSpaceServer
        path=os.path.normpath(parts.path).strip('/')
        return "%s://%s/%s" % (parts.scheme, host, path)

    
    def getNode(self,uri):
        """connect to VOSpace and download the definition of vospace node

        target --- a voSpace node in the format vos:/vospaceName/nodeName
        """
        xmlObj=self.open(uri,os.O_RDONLY)
        dom=ET.parse(xmlObj)
        logging.debug("%s" %( str(dom)))
        return Node(dom.getroot())
                    

    def getNodeURL(self,uri,protocol="https", method='GET', view=None):            
        """Split apart the node string into parts and return the correct URL for this node"""

        uri   = self.fixURI(uri)
        parts = urlparse(uri)
        path  = parts.path.strip('/')
        server= Client.VOServers.get(parts.netloc)        

        logging.debug("URI for node: %s" %( uri))
        logging.debug("structure hardcoded for CADC vospace" )

        if method in ('PUT'):
            ### This part is hard coded for CADC VOSpace...
            return "%s://%s/data/pub/vospace/%s" % (protocol, server,parts.path.strip('/'))

        ### this is a GET so we might have to stick some data onto the URL...
        if view and method=='GET':
            import urllib
            data="?"+urllib.urlencode({'view': view})
        else:
            data=''
        return "%s://%s/vospace/nodes/%s%s" % ( protocol, server, parts.path.strip('/'), data)
                    
    def open(self, uri, mode, view=None):
        """Connect to URL and PUT contents of src to that connection return transfer status"""

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
        if not method:
            raise IOError("Invalid mode (%X) for open" % ( mode))
        URL=self.getNodeURL(uri, method=method, view=view)
        logging.debug(URL)
        return VOFile(URL,self.conn,method=method)

    def update(self,node):
        """Given a node structure do a POST of the XML to the VOSpace to update the node properties"""
        logging.debug("%s" % ( node.name))
        f=self.open(node.uri,mode=os.O_APPEND)
        f.write(str(node))
        f.close()

    def mkdir(self,uri):
        node = Node(self.fixURI(uri),nodeType="vos:ContainerNode")
        URL=self.getNodeURL(uri)
        f=VOFile(URL,self.conn,method="PUT")
        f.write(str(node))
        f.close()

    def delete(self,uri):
        """Delete the node"""
        logging.debug("%s" % (uri))
        self.open(uri,mode=os.O_TRUNC).close()






                          





                           



    

