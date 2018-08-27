"""A set of Python Classes for connecting to and interacting with a VOSpace
   service.

   Connections to VOSpace are made using a SSL X509 certificat which is
   stored in a .pem file.
"""
from __future__ import (absolute_import, division, print_function,
                        unicode_literals)
from future.utils import bytes_to_native_str
import warnings
import copy
import errno
from datetime import datetime
import fnmatch
from enum import Enum

try:
    from cStringIO import StringIO
except ImportError:
    from io import StringIO
import requests
from requests.exceptions import HTTPError
import html2text
import logging
import mimetypes
import os
import re
import stat
import sys
import time
import urllib
import six
from xml.etree import ElementTree
from copy import deepcopy
from .node_cache import NodeCache

try:
    from .version import version
except ImportError:
    version = "unknown"
from cadcutils import net, exceptions, util
from . import md5_cache

try:
    from urllib import splittag
except ImportError:
    import six.moves.urllib.parse

    splittag = six.moves.urllib.parse.splittag

urlparse = six.moves.urllib.parse.urlparse
logger = logging.getLogger('vos')
logger.setLevel(logging.ERROR)

if sys.version_info[1] > 6:
    logger.addHandler(logging.NullHandler())

# ch = logging.StreamHandler()
# ch.setLevel(logging.DEBUG)
# logger.addHandler(ch)

BUFSIZE = 8388608  # Size of read/write buffer
MAX_RETRY_DELAY = 128  # maximum delay between retries
# start delay between retries when Try_After not sent by server.
DEFAULT_RETRY_DELAY = 30
MAX_RETRY_TIME = 900  # maximum time for retries before giving up...

VOSPACE_ARCHIVE = os.getenv("VOSPACE_ARCHIVE", "vospace")
HEADER_DELEG_TOKEN = 'X-CADC-DelegationToken'
HEADER_CONTENT_LENGTH = 'X-CADC-Content-Length'
HEADER_PARTIAL_READ = 'X-CADC-Partial-Read'

CADC_GMS_PREFIX = "ivo://cadc.nrc.ca/gms#"

VO_PROPERTY_URI_ISLOCKED = 'ivo://cadc.nrc.ca/vospace/core#islocked'
VO_VIEW_DEFAULT = 'ivo://ivoa.net/vospace/core#defaultview'
# CADC specific views
VO_CADC_VIEW_URI = 'ivo://cadc.nrc.ca/vospace/view'


# sorting-related uris
class SortNodeProperty(Enum):
    """ URIs of node properties used for sorting"""
    LENGTH = 'ivo://ivoa.net/vospace/core#length'
    DATE = 'ivo://ivoa.net/vospace/core#date'


CADC_VO_VIEWS = {'data': '{}#data'.format(VO_CADC_VIEW_URI),
                 'manifest': '{}#manifest'.format(VO_CADC_VIEW_URI),
                 'rss': '{}#rss'.format(VO_CADC_VIEW_URI),
                 'cutout': '{}#cutout'.format(VO_CADC_VIEW_URI)}

# md5sum of a size zero file
ZERO_MD5 = 'd41d8cd98f00b204e9800998ecf8427e'

_ROOT = os.path.abspath(os.path.dirname(__file__))
_DEFAULT_CONFIG_PATH = os.path.join(_ROOT, 'data', 'default-vos-config')
_CONFIG_PATH = os.path.expanduser("~") + '/.config/vos/vos-config'

# Pattern matching in filenames to extract out the RA/DEC/RADIUS part
FILENAME_PATTERN_MAGIC = re.compile(
    r'^(?P<filename>[/_\-=+!,;:@&*$.\w~]*)'  # legal filename string
    r'(?P<cutout>'  # Look for a cutout part
    r'(?P<pix>(\[\d*:?\d*\])?'
    r'(\[[+-]?\*?\d*:?[+-]?\d*,?[+-]?\*?\d*:?[+-]?\d*\]))'  # pixel
    r'|'  # OR
    r'(?P<wcs>'  # possible wcs cutout
    r'\((?P<ra>[+]?\d*(\.\d*)?),'  # ra part
    r'(?P<dec>[\-+]?\d*(\.\d*)?),'  # dec part
    r'(?P<rad>\d*(\.\d*)?)\))'  # radius of cutout
    r')?$'
    )
MAGIC_GLOB_CHECK = re.compile('[*?[]')

try:
    vos_config = util.Config(_CONFIG_PATH)
except IOError as e:
    # Assume this is the first invocation and the config file has not been
    # created yet => create it
    util.Config.write_config(_CONFIG_PATH, _DEFAULT_CONFIG_PATH)
    # now read parse it again
    vos_config = util.Config(_CONFIG_PATH)

logging.getLogger("requests").setLevel(logging.ERROR)


def convert_vospace_time_to_seconds(str_date):
    """A convenience method that takes a string from a vospace time field (UTC)
    and converts it to seconds since epoch local time.

    :param str_date: string to parse into a VOSpace time
    :type str_date: unicode
    :return: A datetime object for the provided string date
    :rtype: datetime
    """
    right = str_date.rfind(":") + 3
    mtime = time.mktime(time.strptime(str_date[0:right], '%Y-%m-%dT%H:%M:%S'))
    return mtime - round((datetime.utcnow() - datetime.now()).total_seconds())


class URLParser(object):
    """ Parse out the structure of a URL.

    There is a difference between the 2.5 and 2.7 version of the
    urlparse.urlparse command, so here I roll my own...
    """

    # TODO - ad: since 2.5 is no longer supported maybe it's time to get
    # rid of this
    def __init__(self, url):
        self.scheme = None
        self.netloc = None
        self.args = None
        self.path = None
        m = re.match(
            "(^(?P<scheme>[a-zA-Z]*):)?"
            "(//(?P<netloc>(?P<server>[^!~]*)[!~](?P<service>[^/]*)))?"
            "(?P<path>/?[^?]*)?(?P<args>\?.*)?", url)
        self.scheme = m.group('scheme')
        self.netloc = m.group('netloc')
        self.server = m.group('server')
        self.service = m.group('service')
        self.path = (m.group('path') is not None and m.group('path')) or ''
        self.args = (m.group('args') is not None and m.group('args')) or ''

    def __str__(self):
        return "[scheme: %s, netloc: %s, path: %s]" % (self.scheme,
                                                       self.netloc, self.path)


class Connection(object):
    """Class to hold and act on the X509 certificate"""

    def __init__(self, vospace_certfile=None, vospace_token=None,
                 http_debug=False,
                 resource_id=vos_config.get('vos', 'resourceID')):
        """Setup the Certificate for later usage

        vospace_certfile -- where to store the certificate, if None then
                         ${HOME}/.ssl or a temporary filename
        vospace_token -- token string (alternative to vospace_certfile)
        http_debug -- set True to generate debug statements (Deprecated)
        resource_id -- The resource ID of the vospace service. Defaults to
        CADC vos.

        If the user supplies an empty vospace_certificate, the connection
        will be 'anonymous'. If no certificate or token are provided, and
        attempt to find user/password combination in the .netrc file is made
        before the connection is downgraded to 'anonymous'
        """
        if http_debug is not False:
            warnings.warn(
                "Connection object no longer uses http_debug setting.",
                DeprecationWarning)
        self.vo_token = None
        session_headers = None
        self.resource_id = resource_id
        if vospace_token is not None:
            session_headers = {HEADER_DELEG_TOKEN: vospace_token}
            self.subject = net.Subject()
            self.vo_token = vospace_token
        else:
            cert = vospace_certfile
            if cert is not None:
                if len(cert) == 0:
                    logger.debug('Anonymous access (certfile=Anonymous)')
                    self.subject = net.Subject()
                elif not os.access(vospace_certfile, os.F_OK):
                    logger.warning(
                        "Could not access certificate at {0}.".format(cert))
                    cert = None
                else:
                    logger.debug(
                        'Authenticate with cert {}'.format(vospace_certfile))
                    self.subject = net.Subject(certificate=vospace_certfile)

            if cert is None:
                if os.access(os.path.join(os.environ['HOME'], ".netrc"),
                             os.F_OK):
                    logger.debug(
                        ('Authenticate with user/password '
                         'from $HOME/.netrc file'))
                    self.subject = net.Subject(netrc=True)
                else:
                    logger.warning(
                        ('No valid authentication found. '
                         'Reverting to anonymous.'))
                    self.subject = net.Subject()
        self.ws_client = net.BaseWsClient(resource_id, self.subject,
                                          'vos/' + version,
                                          host=os.getenv('VOSPACE_WEBSERVICE',
                                                         None),
                                          session_headers=session_headers)
        EndPoints.subject = self.subject

    @property
    def session(self):
        return self.ws_client._get_session()

    def get_connection(self, url=None):
        """Create an HTTPSConnection object and return.  Uses the client
        certificate if None given.

        :param url: a VOSpace uri
        """
        if url is not None:
            raise OSError(errno.ENOSYS,
                          "Connections are no longer set per URL.")
        return self.ws_client


class Node(object):
    """A VOSpace node"""

    IVOAURL = "ivo://ivoa.net/vospace/core"
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

        :param node: the name of the node to create or a string representing
        that node
        """
        self.uri = None
        self.name = None
        self.target = None
        self.groupread = None
        self.groupwrite = None
        self.is_public = None
        self.type = None
        self.props = {}
        self.attr = {}
        self.xattr = {}
        self._node_list = None
        self._endpoints = None

        if not subnodes:
            subnodes = []
        if not properties:
            properties = {}

        if node_type is None:
            node_type = Node.DATA_NODE

        if isinstance(node, bytes):
            node = bytes_to_native_str(node)
        if isinstance(node, six.text_type) or isinstance(node, str):
            node = self.create(node, node_type, properties, subnodes=subnodes)

        if node is None:
            raise LookupError("no node found or created?")

        self.node = node
        self.node.set('xmlns:vos', self.VOSNS)
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
            self.set_props(propertiesNode)
        self.is_public = False
        if self.props.get('ispublic', 'false') == 'true':
            self.is_public = True
        logger.debug(
            "{0} {1} -> {2}".format(self.uri, VO_PROPERTY_URI_ISLOCKED,
                                    self.props))
        self.groupwrite = self.props.get('groupwrite', '')
        self.groupread = self.props.get('groupread', '')
        logger.debug("Setting file attributes via setattr")
        self.setattr()
        logger.debug("Setting file x-attributes via setxattr")
        self.setxattr()

    def set_property(self, key, value):
        """Create a key/value pair Node.PROPERTY element.

        :param key: the property key
        :param value: the property value
        """
        properties = self.node.find(Node.PROPERTIES)
        uri = "%s#%s" % (Node.IVOAURL, key)
        ElementTree.SubElement(properties, Node.PROPERTY,
                               attrib={'uri': uri,
                                       'readOnly': 'false'}).text = value

    def __str__(self):
        """Convert the Node to a string representation of the Node"""

        class Dummy(object):
            pass

        data = []
        file_handle = Dummy()
        file_handle.write = data.append
        ElementTree.ElementTree(self.node).write(file_handle, encoding='UTF-8')
        # concatenate and decode the string
        return b''.join(data).decode('UTF-8')

    def setattr(self, attr=None):
        """return / augment a dictionary of attributes associated with the Node

        These attributes are determined from the node on VOSpace.
        :param attr: the  dictionary that holds the attributes
        """
        if not attr:
            attr = {}
        # Get the flags for file mode settings.

        self.attr = {}

        # Only one date provided by VOSpace, so use this as all possible dates.

        access_time = time.time()
        if not self.props.get('date', None):
            modified_time = access_time
        else:
            # mktime is expecting a localtime but we're sending a UT date, so
            # some correction will be needed
            modified_time = convert_vospace_time_to_seconds(
                self.props.get('date'))

        self.attr['st_ctime'] = attr.get('st_ctime', modified_time)
        self.attr['st_mtime'] = attr.get('st_mtime', modified_time)
        self.attr['st_atime'] = access_time

        # set the MODE by or'ing together all flags from stat
        st_mode = 0
        st_nlink = 1
        if self.type == 'vos:ContainerNode':
            st_mode |= stat.S_IFDIR
            st_nlink = max(2, len(self.get_info_list()) + 2)
            # if getInfoList length is < 0 we have a problem elsewhere, so
            # above hack solves that problem.
        elif self.type == 'vos:LinkNode':
            st_mode |= stat.S_IFLNK
        else:
            st_mode |= stat.S_IFREG
        self.attr['st_nlink'] = st_nlink

        # Set the OWNER permissions: all vospace Nodes have read/write/execute
        # by owner
        st_mode |= stat.S_IRUSR | stat.S_IWUSR | stat.S_IXUSR

        # Set the GROUP permissions
        if self.props.get('groupwrite', "NONE") != "NONE":
            st_mode |= stat.S_IWGRP
        if self.props.get('groupread', "NONE") != "NONE":
            st_mode |= stat.S_IRGRP
            st_mode |= stat.S_IXGRP

        # Set the OTHER permissions
        if self.props.get('ispublic', 'false') == 'true':
            # If you can read the file then you can execute too.
            # Public does NOT mean writeable.  EVER
            st_mode |= stat.S_IROTH | stat.S_IXOTH

        self.attr['st_mode'] = attr.get('st_mode', st_mode)

        # We set the owner and group bits to be those of the currently running
        # process. This is a hack since we don't have an easy way to figure
        # these out.
        # TODO Come up with a better approach to uid setting
        self.attr['st_uid'] = attr.get('st_uid', os.getuid())
        self.attr['st_gid'] = attr.get('st_uid', os.getgid())

        st_size = int(self.props.get('length', 0))
        self.attr['st_size'] = st_size > 0 and st_size or 0

        self.attr['st_blocks'] = self.attr['st_size'] // 512

    def setxattr(self, attrs=None):
        """Initialize the extended attributes using the Node properties that
        are not part of the core set.

        :param attrs: An input list of attributes being sent from an external
        source, not supported.
        """
        if attrs is not None:
            raise OSError(
                errno.ENOSYS,
                "No externally set extended Attributes for vofs yet.")

        for key in self.props:
            if key in Client.vosProperties:
                continue
            self.xattr[key] = self.props[key]

        return

    def chwgrp(self, group):
        """Set the groupwrite value to group for this node

        :param group: the uri of he group to give write access to.
        :type group: unicode
        """
        logger.debug("Setting groups to: {0}".format(group))
        if group is not None and len(group.split()) > 3:
            raise AttributeError(
                "Exceeded max of 4 write groups: {0}<-".format(group.split()))
        self.groupwrite = group
        return self.change_prop('groupwrite', group)

    def chrgrp(self, group):
        """Set the groupread value to group for this node

        :param group: the uri of the group to give read access to.
        :type group: unicode
        """
        if group is not None and len(group.split()) > 3:
            raise AttributeError(
                "Exceeded max of 4 read groups: {0}<-".format(group))

        self.groupread = group
        return self.change_prop('groupread', group)

    def set_public(self, value):
        """
        :param value: should the is_public flag be set? (true/false)

        :type value: unicode
        """
        return self.change_prop('ispublic', value)

    @staticmethod
    def fix_prop(prop):
        """Check if prop is a well formed uri and if not then make into one

        :param prop: the  property to expand into a  IVOA uri value for a
        property.
        :rtype unicode
        """
        (url, tag) = splittag(prop)
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

        parts = URLParser(url)
        if parts.path is None or tag is None:
            raise ValueError("Invalid VOSpace property uri: {0}".format(prop))

        return prop

    @staticmethod
    def set_prop():
        """Build the XML for a given node"""
        raise NotImplementedError('No set prop.')

    def change_prop(self, key, value):
        """Change the node property 'key' to 'value'.

        :param key: The property key to update
        :type key: unicode
        :param value: The value to give that property.
        :type value: unicode,None
        :return True/False depending on if the property value was updated.
        """
        # TODO split into 'set' and 'delete'
        uri = self.fix_prop(key)
        changed = False
        found = False
        properties = self.node.findall(Node.PROPERTIES)
        for props in properties:
            for prop in props.findall(Node.PROPERTY):
                if uri != prop.attrib.get('uri', None):
                    continue
                found = True
                if getattr(prop, 'text', None) == value:
                    break
                changed = True
                if value is None:
                    # this is actually a delete property
                    prop.attrib['xsi:nil'] = 'true'
                    prop.attrib["xmlns:xsi"] = Node.XSINS
                    prop.text = ""
                    self.props[self.get_prop_name(uri)] = None
                else:
                    prop.text = value
            if found:
                return changed
        # must not have had this kind of property already, so set value
        property_node = ElementTree.SubElement(properties[0], Node.PROPERTY)
        property_node.attrib['readOnly'] = "false"
        property_node.attrib['uri'] = uri
        property_node.text = value
        self.props[self.get_prop_name(uri)] = value
        return changed

    def chmod(self, mode):
        """Set the MODE of this Node...

        translates unix style MODE to voSpace and updates the properties...

        This function is quite limited.  We can make a file publicly
        readable and we can turn on/off group read/write permissions,
        that's all.

        :param mode: a stat MODE bit
        """

        changed = 0

        if mode & stat.S_IROTH:
            changed += self.set_public('true')
        else:
            changed += self.set_public('false')

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

    def create(self, uri, node_type="vos:DataNode", properties=None,
               subnodes=None):
        """Build the XML needed to represent a VOSpace node returns an
        ElementTree representation of the XML

        :param uri: The URI for this node.
        :type uri: str
        :param node_type: the type of VOSpace node, likely one of
        vos:DataNode, vos:ContainerNode, vos:LinkNode
        :type node_type: unicode
        :param properties:  a dictionary of the node properties, keys should
        be single words from the IVOA list
        :type properties: dict
        :param subnodes: Any children to attach to this node, only valid
        for vos:ContainerNode
        :type subnodes: [Node]
        """
        if not subnodes:
            subnodes = []
        elif node_type != 'vos:ContainerNode':
            raise ValueError("Only Container Nodes can have subnodes")

        if not properties:
            properties = {}

        # Build the root node called 'node'
        node = ElementTree.Element("node")
        node.attrib["xmlns"] = Node.VOSNS
        node.attrib["xmlns:vos"] = Node.VOSNS
        node.attrib[Node.TYPE] = node_type
        node.attrib["uri"] = uri

        # create a properties section
        if 'type' not in properties:
            properties['type'] = mimetypes.guess_type(uri)[0]
        properties_node = ElementTree.SubElement(node, Node.PROPERTIES)
        for prop in properties.keys():
            property_node = ElementTree.SubElement(properties_node,
                                                   Node.PROPERTY)
            property_node.attrib['readOnly'] = "false"
            property_node.attrib["uri"] = self.fix_prop(prop)
            if properties[prop] is None:
                # Setting the property value to None indicates that this is
                # actually a delete
                property_node.attrib['xsi:nil'] = 'true'
                property_node.attrib["xmlns:xsi"] = Node.XSINS
                property_node.text = ""
            elif len(str(properties[prop])) > 0:
                property_node.text = properties[prop]

        # That's it for link nodes...
        if node_type == "vos:LinkNode":
            return node

        # create accepts
        accepts = ElementTree.SubElement(node, Node.ACCEPTS)

        ElementTree.SubElement(accepts, "view").attrib['uri'] = VO_VIEW_DEFAULT

        provides = ElementTree.SubElement(node, Node.PROVIDES)
        ElementTree.SubElement(provides, "view").attrib[
            'uri'] = VO_VIEW_DEFAULT
        ElementTree.SubElement(provides, "view").attrib['uri'] = CADC_VO_VIEWS[
            'rss']

        # Only DataNode can have a dataview...
        if node_type == "vos:DataNode":
            ElementTree.SubElement(provides, "view").attrib['uri'] = \
                CADC_VO_VIEWS['data']

        # if this is a container node then add directory contents
        if node_type == "vos:ContainerNode":
            node_list = ElementTree.SubElement(node, Node.NODES)
            for sub_node in subnodes:
                node_list.append(sub_node.node)

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

    @property
    def is_locked(self):
        return self.islocked()

    @is_locked.setter
    def is_locked(self, lock):
        if lock == self.is_locked:
            return
        self.change_prop(VO_PROPERTY_URI_ISLOCKED, lock and "true" or "false")

    def islocked(self):
        """Check if target state is locked for update/delete."""
        return self.props.get(VO_PROPERTY_URI_ISLOCKED, "false") == "true"

    def get_info(self):
        """Organize some information about a node and return as dictionary"""
        date = convert_vospace_time_to_seconds(self.props['date'])
        creator = (re.search('CN=([^,]*)',
                             self.props.get('creator', 'CN=unknown_000,'))
                   .groups()[0].replace(' ', '_')).lower()
        perm = []
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
        write_group = self.props.get('groupwrite', 'NONE')
        if write_group != 'NONE':
            perm[5] = 'w'
        read_group = self.props.get('groupread', 'NONE')
        if read_group != 'NONE':
            perm[4] = 'r'
        is_locked = self.props.get(VO_PROPERTY_URI_ISLOCKED, "false")
        return {"permissions": ''.join(perm),
                "creator": creator,
                "readGroup": read_group,
                "writeGroup": write_group,
                "isLocked": is_locked,
                "size": float(self.props.get('length', 0)),
                "date": date,
                "target": self.target}

    @property
    def node_list(self):
        """Get a list of all the nodes held to by a ContainerNode return a
           list of Node objects

        :rtype: list
        """
        if self._node_list is None:
            self._node_list = []
            for nodesNode in self.node.findall(Node.NODES):
                for nodeNode in nodesNode.findall(Node.NODE):
                    self.add_child(nodeNode)
        return self._node_list

    def get_children(self, client, sort, order, limit=500):
        """ Gets an iterator over the nodes held to by a ContainerNode"""
        # IF THE CALLER KNOWS THEY DON'T NEED THE CHILDREN THEY
        # CAN SET LIMIT=0 IN THE CALL Also, if the number of nodes
        # on the firt call was less than 500, we likely got them
        # all during the init
        if not self.isdir():
            return

        if self.node_list is not None:
            # children already downloaded
            for i in self.node_list:
                yield i.uri

        # stream children
        xml_file = StringIO(
            client.open(self.uri, os.O_RDONLY,
                        limit=limit, sort=sort,
                        order=order).read().decode('UTF-8'))
        xml_file.seek(0)
        page = Node(ElementTree.parse(xml_file).getroot())
        nl = page.node_list
        current_index = 0
        run = True
        while run and nl:
            yield_node = nl[current_index]
            current_index = current_index + 1
            if current_index == len(nl):
                if len(nl) == limit:
                    # do another page read
                    xml_file = StringIO(
                        client.open(self.uri, os.O_RDONLY, next_uri=nl[-1].uri,
                                    sort=sort, order=order,
                                    limit=limit).read().decode('UTF-8'))
                    xml_file.seek(0)
                    page = Node(ElementTree.parse(xml_file).getroot())
                    nl = page.node_list
                    if len(nl) == 1 and nl[0].uri == yield_node.uri:
                        # that must be the last node
                        run = False
                    else:
                        # skip first returned entry as it is the same with
                        # the last one from the previous batch
                        current_index = 1
                else:
                    run = False
            with client.nodeCache.watch(yield_node.uri) as childWatch:
                childWatch.insert(yield_node)
            yield yield_node

    def add_child(self, child_element_tree):
        """
        Add a child node to a node list.
        :param child_element_tree: a node to add as a child.
        :type child_element_tree: ElementTree
        :return: Node
        """
        child_node = Node(child_element_tree)
        self.node_list.append(child_node)
        return child_node

    def clear_properties(self):
        logger.debug("clearing properties")
        properties_node_list = self.node.findall(Node.PROPERTIES)
        for properties_node in properties_node_list:
            for property_node in properties_node.findall(Node.PROPERTY):
                key = self.get_prop_name(property_node.get('uri'))
                if key in self.props:
                    del self.props[key]
                properties_node.remove(property_node)
        logger.debug("done clearing properties")
        return

    def get_info_list(self):
        """
        :rtype [(Node, dict)]
        :return a list of tuples containing the (NodeName, Info) about the
        node and its childern
        """
        info = {}
        for node in self.node_list:
            info[node.name] = node.get_info()
        if self.type == "vos:DataNode":
            info[self.name] = self.get_info()
        return info.items()

    def set_props(self, props):
        """Set the SubElement Node PROPERTY values of the given xmlx ELEMENT
        provided using the Nodes props dictionary.

        :param props: the xmlx element to set the Node PROPERTY of.
        """
        for property_node in props.findall(Node.PROPERTY):
            self.props[self.get_prop_name(
                property_node.get('uri'))] = self.get_prop_value(property_node)
        return

    @staticmethod
    def get_prop_name(prop):
        """parse the property uri and get the name of the property (strips off
        the url and just returns the tag)
        if this is an IVOA property, otherwise sends back the entry uri.

        :param prop: the uri of the property to get the name of.

        """
        (url, prop_name) = splittag(prop)
        if url == Node.IVOAURL:
            return prop_name
        return prop

    @staticmethod
    def get_prop_value(prop):
        """Pull out the value part of PROPERTY Element.

        :param prop: an XML Element that represents a Node PROPERTY.
        """
        return prop.text


class VOFile(object):
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
                 follow_redirect=True, byte_range=None,
                 possible_partial_read=False):
        self.closed = True
        assert isinstance(connector, Connection)
        self.connector = connector
        self.httpCon = None
        self.timeout = -1
        self.size = size
        self.md5sum = None
        self.totalFileSize = None
        self.maxRetries = 10000
        self.maxRetryTime = MAX_RETRY_TIME
        self.url = None
        self.method = None

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
        self.followRedirect = follow_redirect
        self._fpos = 0
        # initial values for retry parameters
        self.currentRetryDelay = DEFAULT_RETRY_DELAY
        self.totalRetryDelay = 0
        self.retries = 0
        self.fileSize = None
        self.request = None
        self.resp = None
        self.trans_encode = None
        # open the connection
        self._fobj = None
        self.open(self.URLs[self.urlIndex], method, byte_range=byte_range,
                  possible_partial_read=possible_partial_read)

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

    @staticmethod
    def flush():
        """
        Flush is a NO OP in VOFile: only really flush on close.
        @return:
        """
        return

    def close(self):
        """close the connection."""

        if not self.closed:
            try:
                if self.trans_encode is not None:
                    self.httpCon.send('0\r\n\r\n')
                    logger.debug("End of document sent.")
                logger.debug("getting response.")
                self.resp = self.connector.session.send(self.request)
                logger.debug("checking response status.")
                self.checkstatus()
            finally:
                self.closed = True
        return self.closed

    def checkstatus(self, codes=(200, 201, 202, 206, 302, 303, 503, 404, 416,
                                 416, 402, 408, 412, 504)):
        """check the response status.  If the status code doesn't match a
        value from the codes list then
        raise an Exception.

        :param codes: a list of http status_codes that are NOT failures but
        require some additional action.
        """
        if self.resp is None:
            return

        msgs = {404: "Node Not Found",
                401: "Not Authorized",
                409: "Conflict",
                423: "Locked",
                408: "Connection Timeout"}
        logger.debug(
            "status %d for URL %s" % (self.resp.status_code, self.url))
        if self.resp.status_code not in codes:
            logger.debug("Got status code: %s for %s" %
                         (self.resp.status_code, self.url))
            msg = self.resp.text
            if msg is not None:
                msg = html2text.html2text(msg, self.url).strip().replace('\n',
                                                                         ' ')
            logger.debug("Error message: {0}".format(msg))

            if self.resp.status_code in VOFile.errnos.keys() or (
                    msg is not None and "Node is busy" in msg):
                if msg is None or len(
                        msg) == 0 and self.resp.status_code in msgs:
                    msg = msgs[self.resp.status_code]
                if (self.resp.status_code == 401 and
                        self.connector.subject.anon and
                        self.connector.vo_token is None):
                    msg += " using anonymous access "
            exception = OSError(VOFile.errnos.get(self.resp.status_code,
                                                  self.resp.status_code), msg)
            if self.resp.status_code == 500 and "read-only" in msg:
                exception = OSError(errno.EPERM, "VOSpace in read-only mode.")
            raise exception

        # Get the file size. We use this HEADER-CONTENT-LENGTH as a
        # fallback to work around a server-side Java bug that limits
        # 'Content-Length' to a signed 32-bit integer (~2 gig files)
        try:
            self.size = int(self.resp.headers.get("Content-Length",
                                                  self.resp.headers.get(
                                                      HEADER_CONTENT_LENGTH,
                                                      0)))
        except ValueError:
            self.size = 0

        if self.resp.status_code == 200:
            self.md5sum = self.resp.headers.get("Content-MD5", None)
            self.totalFileSize = self.size

        return True

    def open(self, url, method="GET", byte_range=None,
             possible_partial_read=False):
        """Open a connection to the given URL
        :param url: The URL to be opened
        :type url: unicode
        :param method: HTTP Method to use on open (PUT/GET/POST)
        :type method: unicode
        :param byte_range: The range of byte_range to read, This is in open so
        we can set the header parameter.
        :type byte_range: unicode
        :param possible_partial_read:  Sometimes we kill during read, this
        tells the server that isn't an error.
        :type possible_partial_read: bool
        """
        logger.debug("Opening %s (%s)" % (url, method))
        self.url = url
        self.method = method

        request = requests.Request(self.method, url)

        self.trans_encode = None

        # Try to send a content length hint if this is a PUT.
        # otherwise send as a chunked PUT
        if method in ["PUT"]:
            try:
                self.size = int(self.size)
                request.headers.update({"Content-Length": str(self.size),
                                        HEADER_CONTENT_LENGTH: str(self.size)})
            except TypeError:
                self.size = None
                self.trans_encode = "chunked"
        elif method in ["POST", "DELETE"]:
            self.size = None
            self.trans_encode = "chunked"

        if method in ["PUT", "POST", "DELETE"]:
            content_type = "text/xml"
            if method == "PUT":
                ext = os.path.splitext(urllib.splitquery(url)[0])[1]
                if ext in ['.fz', '.fits', 'fit']:
                    content_type = 'application/fits'
                else:
                    content_type = mimetypes.guess_type(url)[0]
            if content_type is not None:
                request.headers.update({"Content-Type": content_type})
        if byte_range is not None and method == "GET":
            request.headers.update({"Range": byte_range})
        request.headers.update({"Accept": "*/*",
                                "Expect": "100-continue"})

        # set header if a partial read is possible
        if possible_partial_read and method == "GET":
            request.headers.update({HEADER_PARTIAL_READ: "true"})
        try:
            self.request = self.connector.session.prepare_request(request)
        except Exception as ex:
            logger.error(str(ex))

    def get_file_info(self):
        """Return information harvested from the HTTP header.

        :rtype (int, unicode)
        """
        return self.totalFileSize, self.md5sum

    def read(self, size=None, return_response=False):
        """return size bytes from the connection response

        :param return_response: should we return the response object or the
        bytes read?
        :param size: number of bytes to read from the file.
        """

        if self.resp is None:
            # this is original retry flag of the session
            orig_retry_flag = self.connector.session.retry
            try:
                if (len(self.URLs) > 1) and\
                   (self.urlIndex < len(self.URLs) - 1):
                    # there is more urls to try so don't bother retrying on
                    # transient errors
                    # return instead and try the next url
                    self.connector.session.retry = False
                self.resp = self.connector.session.send(self.request,
                                                        stream=True,
                                                        verify=False)
            except exceptions.HttpException as http_exception:
                # this is the path for all status_codes between 400 and 600
                if http_exception.orig_exception is not None:
                    self.resp = http_exception.orig_exception.response

                # restore the original retry flag of the session
                self.connector.session.retry = orig_retry_flag

                self.checkstatus()

                if isinstance(http_exception,
                              exceptions.UnauthorizedException) or \
                        isinstance(http_exception,
                                   exceptions.BadRequestException) or \
                        isinstance(http_exception,
                                   exceptions.ForbiddenException):
                    raise

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
                    self.resp = None
                    return self.read(size)
                else:
                    raise
            finally:
                # restore the original retry flag of the session
                self.connector.session.retry = orig_retry_flag

        # Get the file size. We use this HEADER-CONTENT-LENGTH as a
        # fallback to work around a server-side Java bug that limits
        # 'Content-Length' to a signed 32-bit integer (~2 gig files)
        try:
            self.size = int(self.resp.headers.get("Content-Length",
                                                  self.resp.headers.get(
                                                      HEADER_CONTENT_LENGTH,
                                                      0)))
        except Exception:
            self.size = 0

        if self.resp.status_code == 200:
            self.md5sum = self.resp.headers.get("Content-MD5", None)
            self.totalFileSize = self.size

        if self.resp is None:
            raise OSError(errno.EFAULT, "No response from VOServer")

        # check the most likely response first
        if self.resp.status_code == 200 or self.resp.status_code == 206:
            if return_response:
                return self.resp
            else:
                buff = self.resp.raw.read(size)
                size = size is not None and size < len(buff) and size or len(
                    buff)
                # logger.debug("Sending back {0} bytes".format(size))
                return buff[:size]
        elif self.resp.status_code == 303 or self.resp.status_code == 302:
            url = self.resp.headers.get('Location', None)
            logger.debug("Got redirect URL: {0}".format(url))
            self.url = url
            if not url:
                raise OSError(
                    errno.ENOENT,
                    "Got 303 on {0} but no Location value in header? [{1}]".
                    format(self.url, self.resp.content),
                    self.url)
            if self.followRedirect:
                # We open this new URL without the byte range and partial
                # read as we are following a service redirect and that service
                # redirect is to the object that satisfies the original
                # request.
                # TODO seperate out making the transfer reqest and reading
                # the response content.
                self.open(url, "GET")
                # logger.debug("Following redirected URL:  %s" % (URL))
                return self.read(size)
            else:
                # logger.debug("Got url:%s from redirect but not following" %
                # (self.url))
                return self.url

        # start from top of URLs with a delay
        self.urlIndex = 0
        logger.error("Servers busy {0} for {1}".format(self.resp.status_code,
                                                       self.URLs))
        msg = self.resp.text
        if msg is not None:
            msg = html2text.html2text(msg, self.url).strip()
        else:
            msg = "No Message Sent"
        logger.error("Message from VOSpace {0}: {1}".format(self.url, msg))
        try:
            # see if there is a Retry-After in the head...
            ras = int(self.resp.headers.get("Retry-After", 5))
        except ValueError:
            ras = self.currentRetryDelay
            if (self.currentRetryDelay * 2) < MAX_RETRY_DELAY:
                self.currentRetryDelay *= 2
            else:
                self.currentRetryDelay = MAX_RETRY_DELAY

        if ((self.retries < self.maxRetries) and
                (self.totalRetryDelay < self.maxRetryTime)):
            logger.error("Retrying in {0} seconds".format(ras))
            self.totalRetryDelay += ras
            self.retries += 1
            time.sleep(int(ras))
            self.open(self.URLs[self.urlIndex], "GET")
            self.resp = None
            return self.read(size)
        else:
            raise OSError(
                self.resp.status_code,
                "failed to connect to server after multiple attempts {0} {1}".
                format(self.resp.reason, self.resp.status_code),
                self.url)

    @staticmethod
    def write(buf):
        """write buffer to the connection

        :param buf: string to write to the file.
        """
        raise OSError(
            errno.ENOSYS,
            "Direct write to a VOSpaceFile is not supported, use "
            "copy instead.")


class EndPoints(object):
    VOSPACE_WEBSERVICE = os.getenv('VOSPACE_WEBSERVICE', None)

    VODataView = {'cadc.nrc.ca!vospace': 'ivo://cadc.nrc.ca/vospace',
                  'cadc.nrc.ca~vospace': 'ivo://cadc.nrc.ca/vospace'}

    # standard ids
    VO_PROPERTIES = 'vos://cadc.nrc.ca~vospace/CADC/std/VOSpace#nodeprops'
    VO_NODES = 'ivo://ivoa.net/std/VOSpace/v2.0#nodes'
    VO_TRANSFER = 'ivo://ivoa.net/std/VOSpace/v2.0#sync'

    subject = net.Subject()  # default subject is for anonymous access

    def __init__(self, resource_id_uri):
        """
        Determines the end points of a vospace service
        :param resource_id_uri: the resource id uri
        :type resource_id_uri: unicode
        """
        self.resource_id = resource_id_uri
        self.service = net.BaseWsClient(self.resource_id, EndPoints.subject,
                                        'vos/' + version,
                                        host=self.VOSPACE_WEBSERVICE)

    @property
    def properties(self):
        return self.service._get_url((EndPoints.VO_PROPERTIES, None))

    @property
    def uri(self):
        return self.resource_id

    @property
    def server(self):
        """
        Returns the server where the __nodes__ capability is deployed. Most
        of the time all the capabilities are deployed on the same server but
        sometimes they might not be.
        :return: The network location of the VOSpace server.
        """
        return urlparse(self.nodes).netloc

    @property
    def transfer(self):
        """
        The transfer service endpoint.
        :return: service location of the transfer service.
        :rtype: unicode
        """
        return self.service._get_url((EndPoints.VO_TRANSFER, None))

    @property
    def nodes(self):
        """
        :return: The Node service endpoint.
        """
        return self.service._get_url((EndPoints.VO_NODES, None))


class Client(object):
    """The Client object does the work"""

    VO_HTTPGET_PROTOCOL = 'ivo://ivoa.net/vospace/core#httpget'
    VO_HTTPPUT_PROTOCOL = 'ivo://ivoa.net/vospace/core#httpput'
    VO_HTTPSGET_PROTOCOL = 'ivo://ivoa.net/vospace/core#httpsget'
    VO_HTTPSPUT_PROTOCOL = 'ivo://ivoa.net/vospace/core#httpsput'
    DWS = '/data/pub/'
    VO_TRANSFER_PROTOCOLS = ['http', 'https']

    #  reserved vospace properties, not to be used for extended property
    #  setting
    vosProperties = ["description", "type", "encoding", "MD5", "length",
                     "creator", "date", "groupread", "groupwrite", "ispublic"]

    VOSPACE_CERTFILE = os.getenv("VOSPACE_CERTFILE", None)
    if VOSPACE_CERTFILE is None:
        for certfile in ['cadcproxy.pem', 'vospaceproxy.pem']:
            certpath = os.path.join(os.getenv("HOME", "."), '.ssl')
            certfilepath = os.path.join(certpath, certfile)
            if os.access(certfilepath, os.R_OK):
                VOSPACE_CERTFILE = certfilepath
            break

    def __init__(self, vospace_certfile=None, root_node=None, conn=None,
                 transfer_shortcut=False, http_debug=False,
                 secure_get=False, vospace_token=None):
        """This could/should be expanded to set various defaults

        :param vospace_certfile: x509 proxy certificate file location.
        Overrides certfile in conn.
        :type vospace_certfile: unicode
        :param vospace_token: token string (alternative to vospace_certfile)
        :type vospace_token: unicode
        :param root_node: the base of the VOSpace for uri references.
        :type root_node: unicode
        :param conn: a connection pool object for this Client
        :type conn: Session
        :param transfer_shortcut: if True then just assumed data web service
        urls
        :type transfer_shortcut: bool
        :param http_debug: turn on http debugging.
        :type http_debug: bool
        :param secure_get: Use HTTPS: ie. transfer contents of files using
        SSL encryption.
        :type secure_get: bool
        """

        if not isinstance(conn, Connection):
            vospace_certfile = vospace_certfile is None and\
                Client.VOSPACE_CERTFILE or vospace_certfile
            conn = Connection(vospace_certfile=vospace_certfile,
                              vospace_token=vospace_token,
                              http_debug=http_debug)

        self.protocol = vos_config.get('transfer', 'protocol')
        if self.protocol not in self.VO_TRANSFER_PROTOCOLS:
            raise SystemError(
                'Unsupported protocol {}. Valid protocols: {}. Update {}'.
                format(self.protocol, self.VO_TRANSFER_PROTOCOLS,
                       _CONFIG_PATH))

        self.conn = conn
        self.rootNode = root_node
        self.nodeCache = NodeCache()
        self.transfer_shortcut = transfer_shortcut
        self.secure_get = secure_get
        self._endpoints = {}

        return

    def glob(self, pathname):
        """Return a list of paths matching a pathname pattern.

        The pattern may contain simple shell-style wildcards a la
        fnmatch. However, unlike fnmatch, file names starting with a
        dot are special cases that are not matched by '*' and '?'
        patterns.

        :param pathname: path to glob.

        """
        return list(self.iglob(pathname))

    def iglob(self, pathname):
        """Return an iterator which yields the paths matching a pathname
        pattern.

        The pattern may contain simple shell-style wildcards a la fnmatch.
        However, unlike fnmatch, filenames
        starting with a dot are special cases that are not matched by '*'
        and '?' patterns.

        :param pathname: path to run glob against.
        :type pathname: unicode
        """
        dirname, basename = os.path.split(pathname)
        if not self.has_magic(pathname):
            if basename:
                self.get_node(pathname)
                yield pathname
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
        # drive or UNC path.  Prevent an infinite recursion if a drive or UNC
        # path contains magic characters (i.e. r'\\?\C:').
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
        """

        :param dirname: name of the directory to look for matches in.
        :type dirname: unicode
        :param pattern: pattern to match directory contents names against
        :type pattern: unicode
        :return:
        """
        if not dirname:
            dirname = self.rootNode
        if isinstance(pattern, six.string_types) and\
                not isinstance(dirname, six.string_types):
            dirname = str(dirname).encode(
                sys.getfilesystemencoding() or sys.getdefaultencoding())
        try:
            names = self.listdir(dirname, force=True)
        except os.error:
            return []
        if not pattern.startswith('.'):
            names = filter(lambda x: not x.startswith('.'), names)
        return fnmatch.filter(names, pattern)

    def glob0(self, dirname, basename):
        if basename == '':
            # `os.path.split()` returns an empty basename for paths ending
            # with a directory separator.  'q*x/' should match only
            # directories.
            if self.isdir(dirname):
                return [basename]
        else:
            if self.access(os.path.join(dirname, basename)):
                return [basename]
            else:
                raise OSError(errno.EACCES, "Permission denied: {0}".format(
                    os.path.join(dirname, basename)))
        return []

    def get_endpoints(self, uri):
        """
        Returns the end points or a vospace service corresponding to an uri

        The main purpose of this method is to cache the EndPoints for used
        services for performance reasons.

        :param uri: uri of and entry or of a resource id for which the end
        points are seek
        :return: corresponding EndPoint object
        """

        uri_parts = URLParser(uri)
        if uri_parts.scheme is not None:
            if uri_parts.scheme.startswith('vos'):
                if uri_parts.netloc is None:
                    resource_id = vos_config.get('vos', 'resourceID')
                else:
                    resource_id = 'ivo://{0}'.format(uri_parts.netloc).replace(
                        "!", "/").replace("~", "/")
            elif uri_parts.scheme.startswith('ivo'):
                resource_id = uri
            else:
                raise OSError('Unsupported scheme in {}'.format(uri))
        else:
            raise OSError('No scheme in {}'.format(uri))

        if resource_id not in self._endpoints:
            self._endpoints[resource_id] = EndPoints(resource_id)

        return self._endpoints[resource_id]

    @staticmethod
    def has_magic(s):
        return MAGIC_GLOB_CHECK.search(s) is not None

    # @logExceptions()
    def copy(self, source, destination, send_md5=False, disposition=False,
             fhead=None):
        """copy from source to destination.

        One of source or destination must be a vospace location and the other
        must be a local location.

        :param source: The source file to send to VOSpace or the VOSpace node
        to retrieve
        :type source: unicode
        :param destination: The VOSpace location to put the file to or the
        local destination.
        :type destination: Node
        :param send_md5: Should copy send back the md5 of the destination
        file or just the size?
        :type send_md5: bool
        :param disposition: Should the filename from content disposition be
        returned instead of size or MD5?
        :type disposition: bool
        :param fhead: Return just the headers of a FITS file
        :type fhead: bool
        :raises When a network problem occurs, it raises one of the
        HttpException exceptions declared in the
        cadcutils.exceptions module

        """
        # TODO: handle vospace to vospace copies.

        success = False
        copy_failed_message = ""
        destination_size = None
        destination_md5 = None
        source_md5 = None
        get_node_url_retried = False
        content_disposition = None
        if source[0:4] == "vos:":
            if destination is None:
                # Set the destination, initially, to the same directory as
                # the source (strip the vos:)
                destination = os.path.dirname(source)[4:]
            if os.path.isdir(destination):
                # We can't write to a directory so take file name from
                # content-disposition or
                # from filename part of source.
                disposition = True
            check_md5 = False
            cutout_match = FILENAME_PATTERN_MAGIC.search(source)
            if cutout_match is not None and cutout_match.group('cutout'):
                view = 'cutout'
                if cutout_match.group('pix'):
                    cutout = cutout_match.group('pix')
                elif cutout_match.group('wcs') is not None:
                    cutout = "CIRCLE ICRS {} {} {}".format(
                        cutout_match.group('ra'),
                        cutout_match.group('dec'),
                        cutout_match.group('rad'))
                else:
                    raise ValueError("Bad source name: ".format(source))
                source = cutout_match.group('filename')
            elif fhead:
                view = 'header'
                cutout = None
            else:
                view = 'data'
                cutout = None
                check_md5 = True
                source_md5 = \
                    self.get_node(source).props.get('MD5', ZERO_MD5)
                if os.path.isfile(destination):
                    # check if the local file is up to date before doing the
                    # transfer
                    destination_md5 = \
                        md5_cache.MD5Cache.compute_md5(destination)
                    if source_md5 == destination_md5:
                        logger.info(
                            'copy: src and dest are already the same')
                        destination_size = os.stat(destination).st_size
                        if disposition:
                            return None
                        if send_md5:
                            return destination_md5
                        return destination_size

            get_urls = self.get_node_url(source, method='GET', cutout=cutout,
                                         view=view)
            while not success:
                # If there are no urls available, drop through to full
                # negotiation if that wasn't already tried
                if len(get_urls) == 0:
                    if self.transfer_shortcut and not get_node_url_retried:
                        get_urls = self.get_node_url(source, method='GET',
                                                     cutout=cutout, view=view,
                                                     full_negotiation=True)
                        # remove the first one as we already tried that one.
                        get_urls.pop(0)
                        get_node_url_retried = True
                    else:
                        break
                get_url = get_urls.pop(0)
                try:
                    response = self.conn.session.get(get_url, timeout=(2, 5),
                                                     stream=True)
                    if disposition:
                        # Build the destination location from the
                        # content-disposition value, or source name.
                        content_disposition = response.headers.get(
                            'content-disposition', destination)
                        content_disposition = re.search('.*filename=(\S*).*',
                                                        content_disposition)
                        if content_disposition is not None:
                            content_disposition = content_disposition.group(
                                1).strip()
                        else:
                            content_disposition = os.path.split(source)[-1]
                        if os.path.isdir(destination):
                            destination = os.path.join(destination,
                                                       content_disposition)
                    source_md5 = response.headers.get('Content-MD5',
                                                      source_md5)
                    response.raise_for_status()
                    with open(destination, 'wb') as fout:
                        for chunk in response.iter_content(
                                chunk_size=512 * 1024):
                            if chunk:
                                fout.write(chunk)
                                fout.flush()
                    destination_size = os.stat(destination).st_size
                    if check_md5:
                        destination_md5 = md5_cache.MD5Cache.compute_md5(
                            destination)
                        logger.debug(
                            "{0} {1}".format(source_md5, destination_md5))
                        if destination_md5 != source_md5:
                            raise IOError(
                                'Source and destination md5 do not match: '
                                '{} vs. {}'.format(source_md5,
                                                   destination_md5))
                    success = True
                except Exception as ex:
                    copy_failed_message = str(ex)
                    logging.debug("Failed to GET {0}".format(get_url))
                    logging.debug("Got error {0}".format(ex))
                    continue
        else:
            success = True
            try:
                destination_node = self.get_node(destination)
                destination_md5 = destination_node.props.get('MD5', ZERO_MD5)
            except Exception as ex:
                destination_md5 = None
            source_md5 = md5_cache.MD5Cache.compute_md5(source)
            if source_md5 == destination_md5:
                logger.info(
                    'copy: src and dest are already the same -> '
                    'update node metadata')
                # post the node so that the modify time is updated
                self.update(destination_node)
                destination_size = os.stat(source).st_size
            elif source_md5 == ZERO_MD5:
                logger.info("destination: size is 0")
                destination_size = 0
                # TODO delete and recreate the node. Is there a better way
                # to delete just the content of the node?
                if destination_md5:
                    self.delete(destination)
                self.create(destination)
            else:
                # transfer the bytes
                put_urls = self.get_node_url(destination, 'PUT')
                success = False
                while not success:
                    if len(put_urls) == 0:
                        if self.transfer_shortcut and not get_node_url_retried:
                            put_urls = self.get_node_url(destination,
                                                         method='PUT',
                                                         full_negotiation=True)
                            # remove the first one as we already tried
                            # that one.
                            put_urls.pop(0)
                            get_node_url_retried = True
                        else:
                            break
                    put_url = put_urls.pop(0)
                    try:
                        with open(source, str('rb')) as fin:
                            self.conn.session.put(put_url, data=fin)
                        node = self.get_node(destination, limit=0, force=True)
                        destination_md5 = node.props.get('MD5', ZERO_MD5)
                        assert destination_md5 == source_md5
                    except Exception as ex:
                        copy_failed_message = str(ex)
                        logging.debug("FAILED to PUT to {0}".format(put_url))
                        logging.debug("Got error: {0}".format(ex))
                        continue
                    success = True
                    break

        if not success:
            raise OSError(errno.EFAULT,
                          "Failed copying {0} -> {1}\n{2}".
                          format(source, destination, copy_failed_message))
        if disposition:
            return content_disposition
        if send_md5:
            return destination_md5
        return destination_size

    def fix_uri(self, uri):
        """given a uri check if the authority part is there and if it isn't
        then add the VOSpace authority

        :param uri: The string that should be parsed into a proper URI, if
        possible.

        """
        parts = URLParser(uri)
        # TODO
        # implement support for local files (parts.scheme=None
        # and self.rootNode=None

        if parts.scheme is None:
            if self.rootNode is not None:
                uri = self.rootNode + uri
            else:
                return uri
        parts = URLParser(uri)
        if parts.scheme != "vos":
            # Just past this back, I don't know how to fix...
            return uri
        # Check that path name compiles with the standard
        logger.debug("Got value of args: {0}".format(parts.args))
        if parts.args is not None and parts.args != "":
            uri = \
                urlparse.parse_qs(
                    urlparse.urlparse(parts.args).query).get('link', None)[0]
            logger.debug("Got uri: {0}".format(uri))
            if uri is not None:
                return self.fix_uri(uri)

        # Check for filename values.
        path = FILENAME_PATTERN_MAGIC.match(os.path.normpath(parts.path))
        if path is None or path.group('filename') is None:
            raise OSError(errno.EINVAL, "Illegal vospace container name",
                          parts.path)
        logger.debug("Match : {}".format(path.groupdict()))

        filename = path.group('filename')

        # insert the default VOSpace server if none given
        host = parts.netloc
        if not host or host == '':
            # default host corresponds to the resource ID of the client
            host = self.conn.resource_id.replace('ivo://', '').replace('/',
                                                                       '!')

        path = os.path.normpath(filename).strip('/')
        uri = "{0}://{1}/{2}{3}".format(parts.scheme, host, path, parts.args)
        logger.debug("Returning URI: {0}".format(uri))
        return uri

    def get_node(self, uri, limit=0, force=False):
        """connect to VOSpace and download the definition of VOSpace node

        :param uri:   -- a voSpace node in the format vos:/VOSpaceName/nodeName
        :type uri: unicode
        :param limit: -- load children nodes in batches of limit
        :type limit: int, None
        :param force: force getting the node from the service, rather than
        returning a cached version.
        :return: The VOSpace Node
        :rtype: Node

        """
        logger.debug("Getting node {0}".format(uri))
        uri = self.fix_uri(uri)
        node = None
        if not force and uri in self.nodeCache:
            node = self.nodeCache[uri]
        if node is None:
            logger.debug("Getting node {0} from ws".format(uri))
            with self.nodeCache.watch(uri) as watch:
                # If this is vospace URI then we can request the node info
                # using the uri directly, but if this a URL then the metadata
                # comes from the HTTP header.
                if uri.startswith('vos:') or uri.startswith('ad:'):
                    vo_fobj = self.open(uri, os.O_RDONLY, limit=limit)
                    vo_xml_string = vo_fobj.read().decode('UTF-8')
                    xml_file = StringIO(vo_xml_string)
                    xml_file.seek(0)
                    dom = ElementTree.parse(xml_file)
                    node = Node(dom.getroot())
                elif uri.startswith('http'):
                    header = self.open(None, url=uri, mode=os.O_RDONLY,
                                       head=True)
                    header.read()
                    logger.debug(
                        "Got http headers: {0}".format(header.resp.headers))
                    properties = {
                        'type': header.resp.headers.get('Content-Type', 'txt'),
                        'date': time.strftime(
                            '%Y-%m-%dT%H:%M:%S GMT',
                            time.strptime(
                                header.resp.headers.get('Date', None),
                                '%a, %d %b %Y %H:%M:%S GMT')),
                        'groupwrite': None,
                        'groupread': None,
                        'ispublic': URLParser(
                            uri).scheme == 'https' and 'true' or 'false',
                        'length': header.resp.headers.get('Content-Length', 0)}
                    node = Node(node=uri, node_type=Node.DATA_NODE,
                                properties=properties)
                    logger.debug(str(node))
                else:
                    raise OSError(2, "Bad URI {0}".format(uri))
                watch.insert(node)
                # IF THE CALLER KNOWS THEY DON'T NEED THE CHILDREN THEY
                # CAN SET LIMIT=0 IN THE CALL Also, if the number of nodes
                # on the firt call was less than 500, we likely got them
                # all during the init
                if limit != 0 and node.isdir() and len(node.node_list) > 500:
                    next_uri = None
                    while next_uri != node.node_list[-1].uri:
                        next_uri = node.node_list[-1].uri
                        xml_file = StringIO(
                            self.open(uri, os.O_RDONLY, next_uri=next_uri,
                                      limit=limit).read().decode('UTF-8'))
                        xml_file.seek(0)
                        next_page = Node(ElementTree.parse(xml_file).getroot())
                        if len(next_page.node_list) > 0 and next_uri == \
                                next_page.node_list[0].uri:
                            next_page.node_list.pop(0)
                        node.node_list.extend(next_page.node_list)
        for childNode in node.node_list:
            with self.nodeCache.watch(childNode.uri) as childWatch:
                childWatch.insert(childNode)
        return node

    def get_node_url(self, uri, method='GET', view=None, limit=None,
                     next_uri=None, cutout=None, sort=None, order=None,
                     full_negotiation=None):
        """Split apart the node string into parts and return the correct URL
        for this node.

        :param uri: The VOSpace uri to get an associated url for.
        :type uri: unicode
        :param method: What will this URL be used to do: 'GET' the node,
        'PUT' or 'POST' to the node or 'DELETE' it
        :type method: unicode
        :param view: If this is a 'GET' which view of the node should the
        URL provide.
        :type view: unicode
        :param limit: If this is a container how many of the children should
        be returned? (None - Unlimited)
        :type limit: int, None
        :param next_uri: When getting a container we make repeated calls
        until all 'limit' children returned. next_uri tells the service what
        was the last child uri retrieved in the previous call.
        :type next_uri: unicode
        :param cutout: The cutout pattern to apply to the file at the service
        end: applies to view='cutout' only.
        :type cutout: str, None
        :param sort: node property to sort on
        :type sort: vos.NodeProperty, None
        :param order: Order of sorting, Ascending ('asc' - default) or
        Descending ('desc')
        :type order: unicode, None
        :param full_negotiation: Should we use the transfer UWS or do a GET
        and follow the redirect.
        :type full_negotiation: bool
        :raises When a network problem occurs, it raises one of the
        HttpException exceptions declared in the
        cadcutils.exceptions module
        """
        uri = self.fix_uri(uri)

        if sort is not None and not isinstance(sort, SortNodeProperty):
            raise TypeError('sort must be an instace of vos.NodeProperty Enum')
        if order not in [None, 'asc', 'desc']:
            raise ValueError('order must be either "asc" or "desc"')
        if view in ['data', 'cutout'] and method == 'GET':
            node = self.get_node(uri, limit=0)
            if node.islink():
                target = node.node.findtext(Node.TARGET)
                logger.debug("%s is a link to %s" % (node.uri, target))
                if target is None:
                    raise OSError(errno.ENOENT, "No target for link")
                parts = URLParser(target)
                if parts.scheme != "vos":
                    # This is not a link to another VOSpace node so lets just
                    # return the target as the url
                    url = target
                    if cutout is not None:
                        url = "{0}?cutout={1}".format(target, cutout)
                        logger.debug("Line 3.1.2")
                    logger.debug("Returning URL: {0}".format(url))
                    return [url]
                logger.debug("Getting URLs for: {0}".format(target))
                return self.get_node_url(target, method=method, view=view,
                                         limit=limit, next_uri=next_uri,
                                         cutout=cutout, sort=sort, order=order,
                                         full_negotiation=full_negotiation)

        logger.debug("Getting URL for: " + str(uri))

        parts = URLParser(uri)
        if parts.scheme.startswith('http'):
            return [uri]

        endpoints = self.get_endpoints(uri)

        # full_negotiation is an override, so it can be used to force either
        # shortcut (false) or full negotiation (true)
        if full_negotiation is not None:
            do_shortcut = not full_negotiation
        else:
            do_shortcut = self.transfer_shortcut

        if not do_shortcut and method == 'GET' and view in ['data', 'cutout']:
            return self._get(uri, view=view, cutout=cutout)

        if not do_shortcut and method == 'PUT':
            return self._put(uri)

        if (view == "cutout" and cutout is None) or (
                cutout is not None and view != "cutout"):
            raise ValueError(
                "For cutout, must specify a view=cutout and for view=cutout"
                "must specify cutout")

        if method == 'GET' and view not in ['data', 'cutout', 'header']:
            # This is a request for the URL of the Node, which returns an XML
            # document that describes the node.
            fields = {}
            if limit is not None:
                fields['limit'] = limit
            if sort is not None:
                fields['sort'] = sort.value
            if order is not None:
                fields['order'] = order
            if view is not None:
                fields['view'] = view
            if next_uri is not None:
                fields['uri'] = next_uri

            tmp_url = '{}/{}'.format(endpoints.nodes, parts.path.strip('/'))
            # include the parameters into the url. Use Request to get it rigth
            req = requests.Request(method, tmp_url, params=fields)
            prepped = req.prepare()
            url = prepped.url
            logger.debug('URL: {} ({})'.format(url, method))
            return url

        # This is the shortcut. We do a GET request on the service with the
        # parameters sent as arguments.

        direction = {'GET': 'pullFromVoSpace', 'PUT': 'pushToVoSpace'}

        # On GET override the protocol to be http (faster) unless a
        # secure_get is requested.
        protocol = {
            'GET': {'https': ((self.secure_get and Client.VO_HTTPSGET_PROTOCOL)
                              or Client.VO_HTTPGET_PROTOCOL),
                    'http': Client.VO_HTTPGET_PROTOCOL},
            'PUT': {'https': Client.VO_HTTPSPUT_PROTOCOL,
                    'http': Client.VO_HTTPPUT_PROTOCOL}}

        # build the url for that will request the url that provides access to
        # the node.

        url = endpoints.transfer
        args = {
            'TARGET': uri,
            'DIRECTION': direction[method],
            'PROTOCOL': protocol[method][self.protocol],
            'view': view}

        if cutout is not None:
            args['cutout'] = cutout
        headers = {"Content-type": "application/x-www-form-urlencoded",
                   "Accept": "text/plain"}

        response = self.conn.session.get(endpoints.transfer, params=args,
                                         headers=headers,
                                         allow_redirects=False)
        assert isinstance(response, requests.Response)
        logging.debug("Transfer Server said: {0}".format(response.content))

        if response.status_code == 303:
            # Normal case is a redirect
            url = response.headers.get('Location', None)
        elif response.status_code == 404:
            # The file doesn't exist
            raise OSError(errno.ENOENT, response.content, url)
        elif response.status_code == 409:
            raise OSError(errno.EREMOTE, response.content, url)
        elif response.status_code == 413:
            raise OSError(errno.E2BIG, response.content, url)
        else:
            logger.debug("Reverting to full negotiation")
            return self.get_node_url(uri,
                                     method=method,
                                     view=view,
                                     full_negotiation=True,
                                     limit=limit,
                                     next_uri=next_uri,
                                     cutout=cutout,
                                     sort=sort,
                                     order=order)

        logger.debug("Sending short cut url: {0}".format(url))
        return [url]

    def link(self, src_uri, link_uri):
        """Make link_uri point to src_uri.

        :param src_uri: the existing resource, either a vospace uri or a http
        url
        :type src_uri: unicode
        :param link_uri: the vospace node to create that will be a link to
        src_uri
        :type link_uri: unicode

        :raises When a network problem occurs, it raises one of the
        HttpException exceptions declared in the cadcutils.exceptions module
        """
        link_uri = self.fix_uri(link_uri)
        src_uri = self.fix_uri(src_uri)

        # if the link_uri points at an existing directory then we try and
        # make a link into that directory
        if self.isdir(link_uri):
            link_uri = os.path.join(link_uri, os.path.basename(src_uri))

        with self.nodeCache.volatile(src_uri), self.nodeCache.volatile(
                link_uri):
            link_node = Node(link_uri, node_type="vos:LinkNode")
            ElementTree.SubElement(link_node.node, "target").text = src_uri
        data = str(link_node)
        size = len(data)

        url = self.get_node_url(link_uri)
        logger.debug("Got linkNode URL: {0}".format(url))
        self.conn.session.put(url, data=data, headers={'size': str(size)})

    def move(self, src_uri, destination_uri):
        """Move src_uri to destination_uri.  If destination_uri is a
        containerNode then move src_uri into destination_uri

        :param src_uri: the VOSpace node to be moved.
        :type src_uri: unicode
        :param destination_uri: the VOSpace location to move to.
        :type destination_uri: unicode
        :return did the move succeed?
        :rtype bool
        """
        src_uri = self.fix_uri(src_uri)
        destination_uri = self.fix_uri(destination_uri)
        with self.nodeCache.volatile(src_uri), self.nodeCache.volatile(
                destination_uri):
            return self.transfer(src_uri, destination_uri, view='move')

    def _get(self, uri, view="defaultview", cutout=None):
        with self.nodeCache.volatile(uri):
            return self.transfer(uri, "pullFromVoSpace", view, cutout)

    def _put(self, uri):
        with self.nodeCache.volatile(uri):
            return self.transfer(uri, "pushToVoSpace", view="defaultview")

    def transfer(self, uri, direction, view=None, cutout=None):
        """Build the transfer XML document
        :param direction: is this a pushToVoSpace or a pullFromVoSpace ?
        :param uri: the uri to transfer from or to VOSpace.
        :param view: which view of the node (data/default/cutout/etc.) is
        being transferred
        :param cutout: a special parameter added to the 'cutout' view
        request. e.g. '[0][1:10,1:10]'

        :raises When a network problem occurs, it raises one of the
        HttpException exceptions declared in the
        cadcutils.exceptions module
        """
        endpoints = self.get_endpoints(uri)
        protocol = {"pullFromVoSpace": "{0}get".format(self.protocol),
                    "pushToVoSpace": "{0}put".format(self.protocol)}

        transfer_xml = ElementTree.Element("vos:transfer")
        transfer_xml.attrib['xmlns:vos'] = Node.VOSNS
        ElementTree.SubElement(transfer_xml, "vos:target").text = uri
        ElementTree.SubElement(transfer_xml, "vos:direction").text = direction

        if view == 'move':
            ElementTree.SubElement(transfer_xml,
                                   "vos:keepBytes").text = "false"
        else:
            if view == 'defaultview':
                ElementTree.SubElement(transfer_xml, "vos:view").attrib[
                    'uri'] = VO_VIEW_DEFAULT
            elif view is not None:
                vos_view = ElementTree.SubElement(transfer_xml, "vos:view")
                vos_view.attrib['uri'] = CADC_VO_VIEWS[view]
                if cutout is not None and view == 'cutout':
                    param = ElementTree.SubElement(vos_view, "vos:param")
                    param.attrib['uri'] = CADC_VO_VIEWS[view]
                    param.text = cutout
            protocol_element = ElementTree.SubElement(transfer_xml,
                                                      "vos:protocol")
            protocol_element.attrib['uri'] = "{0}#{1}".format(Node.IVOAURL,
                                                              protocol[
                                                                  direction])

        logging.debug(ElementTree.tostring(transfer_xml))
        logging.debug("Sending to : {}".format(endpoints.transfer))

        data = ElementTree.tostring(transfer_xml)
        resp = self.conn.session.post(endpoints.transfer,
                                      data=data,
                                      allow_redirects=False,
                                      headers={'Content-Type': 'text/xml'})

        logging.debug("{0}".format(resp))
        logging.debug("{0}".format(resp.text))
        if resp.status_code != 303:
            raise OSError(resp.status_code,
                          "Failed to get transfer service response.")
        transfer_url = resp.headers.get('Location', None)

        if self.conn.session.auth is not None and "auth" not in transfer_url:
            transfer_url = transfer_url.replace('/vospace/', '/vospace/auth/')

        logging.debug("Got back from transfer URL: %s" % transfer_url)

        # For a move this is the end of the transaction.
        if view == 'move':
            return not self.get_transfer_error(transfer_url, uri)

        # for get or put we need the protocol value
        xfer_resp = self.conn.session.get(transfer_url, allow_redirects=False)
        xfer_url = xfer_resp.headers.get('Location', None)
        if self.conn.session.auth is not None and "auth" not in xfer_url:
            xfer_url = xfer_url.replace('/vospace/', '/vospace/auth/')
        xml_string = self.conn.session.get(xfer_url).text
        logging.debug("Transfer Document: %s" % xml_string)
        transfer_document = ElementTree.fromstring(xml_string)
        logging.debug(
            "XML version: {0}".format(ElementTree.tostring(transfer_document)))
        all_protocols = transfer_document.findall(Node.PROTOCOL)
        if all_protocols is None or not len(all_protocols) > 0:
            return self.get_transfer_error(transfer_url, uri)

        result = []
        for protocol in all_protocols:
            for node in protocol.findall(Node.ENDPOINT):
                result.append(node.text)
        # if this is a connection to the 'rc' server then we reverse the
        # urllist to test the fail-over process
        if urlparse(endpoints.nodes).netloc.startswith('rc'):
            result.reverse()
        return result

    def get_transfer_error(self, url, uri):
        """Follow a transfer URL to the Error message
        :param url: The URL of the transfer request that had the error.
        :param uri: The uri that we were trying to transfer (get or put).

        :raises When a network problem occurs, it raises one of the
        HttpException exceptions declared in the
        cadcutils.exceptions module
        """
        error_codes = {'NodeNotFound': errno.ENOENT,
                       'RequestEntityTooLarge': errno.E2BIG,
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
        job_url = str.replace(url, "/results/transferDetails", "")

        try:
            phase_url = job_url + "/phase"
            sleep_time = 1
            roller = ('\\', '-', '/', '|', '\\', '-', '/', '|')
            phase = VOFile(phase_url, self.conn, method="GET",
                           follow_redirect=False).read().decode('utf-8')
            # do not remove the line below. It is used for testing
            logging.debug("Job URL: " + job_url + "/phase")
            while phase in ['PENDING', 'QUEUED', 'EXECUTING', 'UNKNOWN']:
                # poll the job. Sleeping time in between polls is doubling
                # each time until it gets to 32sec
                total_time_slept = 0
                if sleep_time <= 32:
                    sleep_time *= 2
                slept = 0
                if logger.getEffectiveLevel() == logging.INFO:
                    while slept < sleep_time:
                        sys.stdout.write(
                            "\r%s %s" % (phase, roller[total_time_slept %
                                                       len(roller)]))
                        sys.stdout.flush()
                        slept += 1
                        total_time_slept += 1
                        time.sleep(1)
                    sys.stdout.write("\r                    \n")
                else:
                    time.sleep(sleep_time)
                phase = self.conn.session.get(phase_url,
                                              allow_redirects=False).text
                logging.debug(
                    "Async transfer Phase for url %s: %s " % (url, phase))
        except KeyboardInterrupt:
            # abort the job when receiving a Ctrl-C/Interrupt from the client
            logging.error("Received keyboard interrupt")
            self.conn.session.post(job_url + "/phase",
                                   allow_redirects=False,
                                   data="PHASE=ABORT",
                                   headers={"Content-type": 'text/text'})
            raise KeyboardInterrupt
        status = VOFile(phase_url, self.conn, method="GET",
                        follow_redirect=False).read().decode('UTF-8')

        logger.debug("Phase:  {0}".format(status))
        if status in ['COMPLETED']:
            return False
        if status in ['HELD', 'SUSPENDED', 'ABORTED']:
            # re-queue the job and continue to monitor for completion.
            raise OSError("UWS status: {0}".format(status), errno.EFAULT)
        error_url = job_url + "/error"
        error_message = self.conn.session.get(error_url).text
        logger.debug(
            "Got transfer error {0} on URI {1}".format(error_message, uri))
        # Check if the error was that the link type is unsupported and try and
        # follow that link.
        target = re.search("Unsupported link target:(?P<target> .*)$",
                           error_message)
        if target is not None:
            return target.group('target').strip()
        raise OSError(error_codes.get(error_message, errno.EFAULT),
                      "{0}: {1}".format(uri, error_message))

    def open(self, uri, mode=os.O_RDONLY, view=None, head=False, url=None,
             limit=None, next_uri=None, size=None, cutout=None,
             byte_range=None, sort=None, order=None,
             full_negotiation=False, possible_partial_read=False):
        """Create a VOFile connection to the specified uri or url.

        :rtype : VOFile
        :param uri: The uri of the VOSpace resource to create a connection to,
        override by specifying url
        :type uri: unicode, None
        :param mode: The mode os.O_RDONLY or os.O_WRONLY to open the
        connection with.
        :type mode: bit
        :param view: The view of the VOSpace resource, one of: default, data,
        cutout
        :type view: unicode, None
        :param head: Just return the http header of this request.
        :type head: bool
        :param url: Ignore the uri (ie don't look up the url using
        get_node_url) and just connect to this url
        :type url: unicode, None
        :param limit: limit response from vospace to this many child nodes.
        relevant for containerNode type
        :type limit: int, None
        :param next_uri: The  uri of the last child node returned by a
        previous request on a containerNode
        :type next_uri: unicode, None
        :param size: The size of file to expect or be put to VOSpace
        :type size: int, None
        :param cutout: The cutout pattern to use during a get
        :type cutout: unicode, None
        :param byte_range: The range of bytes to request, rather than getting
        the entire file.
        :type byte_range: unicode, None
        :param sort: node property to sort on
        :type sort: vos.NodeProperty, None
        :param order: Sorting order. Values: asc for ascending (default), desc
        for descending
        :type order: unicode, None
        :param full_negotiation: force this interaction to use the full UWS
        interaction to get the url for the resource
        :type full_negotiation: bool
        :param possible_partial_read:
        """

        # sometimes this is called with mode from ['w', 'r']
        # really that's an error, but I thought I'd just accept those are
        # os.O_RDONLY

        if type(mode) == str:
            mode = os.O_RDONLY

        # the url of the connection depends if we are 'getting', 'putting' or
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
            raise OSError(errno.EOPNOTSUPP, "Invalid access mode", mode)

        if uri is not None and view in ['data', 'cutout']:
            # Check if this is a target node.
            try:
                node = self.get_node(uri)
                if node.type == "vos:LinkNode":
                    target = node.node.findtext(Node.TARGET)
                    logger.debug("%s is a link to %s" % (node.uri, target))
                    if target is None:
                        raise OSError(errno.ENOENT, "No target for link")
                    else:
                        parts = URLParser(target)
                        if parts.scheme == 'vos':
                            # This is a link to another VOSpace node so lets
                            # open that instead.
                            return self.open(target, mode, view, head, url,
                                             limit,
                                             next_uri, size, cutout,
                                             byte_range, sort, order)
                        else:
                            # A target external link
                            # TODO Need a way of passing along authentication.
                            if cutout is not None:
                                target = "{0}?cutout={1}".format(target,
                                                                 cutout)
                            return VOFile(
                                [target],
                                self.conn,
                                method=method,
                                size=size,
                                byte_range=byte_range,
                                possible_partial_read=possible_partial_read)
            except OSError as ose:
                if ose.errno in [2, 404]:
                    pass
                else:
                    raise ose

        if url is None:
            url = self.get_node_url(uri, method=method, view=view,
                                    limit=limit, next_uri=next_uri,
                                    cutout=cutout, sort=sort, order=order,
                                    full_negotiation=full_negotiation)
            if url is None:
                raise OSError(errno.EREMOTE)

        return VOFile(url, self.conn, method=method, size=size,
                      byte_range=byte_range,
                      possible_partial_read=possible_partial_read)

    def add_props(self, node):
        """Given a node structure do a POST of the XML to the VOSpace to
           update the node properties

            Makes a new copy of current local state, then gets a copy of
            what's on the server and
            then updates server with differences.

           :param node: the Node object to add some properties to.

           :raises When a network problem occurs, it raises one of the
           HttpException exceptions declared in the
        cadcutils.exceptions module
           """
        new_props = copy.deepcopy(node.props)
        old_props = self.get_node(node.uri, force=True).props
        for prop in old_props:
            if prop in new_props and old_props[prop] == new_props[prop] and \
                            old_props[prop] is not None:
                del (new_props[prop])
        node.node = node.create(node.uri, node_type=node.type,
                                properties=new_props)
        # Now write these new properties to the node location.
        url = self.get_node_url(node.uri, method='GET')
        data = str(node)
        size = len(data)
        self.conn.session.post(url, headers={'size': str(size)}, data=data)

    def create(self, uri):
        """
        Create a (Container/Link/Data) Node on the VOSpace server.

        :param uri: the Node that we are going to create on the server.
        :type  uri: vos.Node

        :raises When a network problem occurs, it raises one of the
        HttpException exceptions declared in the
        cadcutils.exceptions module
        """
        fixed_uri = self.fix_uri(uri)
        node = Node(fixed_uri)
        path = URLParser(fixed_uri).path
        url = '{}{}'.format(self.get_endpoints(fixed_uri).nodes, path)
        data = str(node)
        size = len(data)
        return Node(self.conn.session.put(url, data=data,
                                          headers={'size': str(size)}).content)

    def update(self, node, recursive=False):
        """Updates the node properties on the server. For non-recursive
           updates, node's properties are updated on the server. For
           recursive updates, node should only contain the properties to
           be changed in the node itself as well as all its children.

           :param node: the node to update.
           :param recursive: should this update be applied to all children?
           (True/False)

            :raises When a network problem occurs, it raises one of the
            HttpException exceptions declared in the
            cadcutils.exceptions module
           """
        # Let's do this update using the async transfer method
        url = self.get_node_url(node.uri)
        endpoints = self.get_endpoints(node.uri)
        if recursive:
            property_url = endpoints.properties
            logger.debug("prop URL: {0}".format(property_url))
            try:
                resp = self.conn.session.post(property_url,
                                              allow_redirects=False,
                                              data=str(node),
                                              headers={
                                                  'Content-type': 'text/xml'})
            except Exception as ex:
                logger.error(str(ex))
                raise ex
            if resp is None:
                raise OSError(errno.EFAULT, "Failed to connect VOSpace")
            logger.debug("Got prop-update response: {0}".format(resp.content))
            transfer_url = resp.headers.get('Location', None)
            logger.debug("Got job status redirect: {0}".format(transfer_url))
            # logger.debug(
            # "Got back %s from $Client.VOPropertiesEndPoint " % (con))
            # Start the job
            self.conn.session.post(transfer_url + "/phase",
                                   allow_redirects=False,
                                   data="PHASE=RUN",
                                   headers={'Content-type': "text/text"})
            self.get_transfer_error(transfer_url, node.uri)
        else:
            resp = self.conn.session.post(url,
                                          data=str(node),
                                          allow_redirects=False)
            logger.debug("update response: {0}".format(resp.content))
        return 0

    def mkdir(self, uri):
        """
        Create a ContainerNode on the service.  Raise OSError(EEXIST) if the
        container exists.

        :param uri: The URI of the ContainerNode to create on the service.
        :type uri: unicode

        :raises When a network problem occurs, it raises one of the
        HttpException exceptions declared in the
        cadcutils.exceptions module
        """
        uri = self.fix_uri(uri)
        node = Node(uri, node_type="vos:ContainerNode")
        url = self.get_node_url(uri)
        try:
            response = self.conn.session.put(url, data=str(node))
            response.raise_for_status()
        except HTTPError as http_error:
            if http_error.response.status_code != 409:
                raise http_error
            else:
                raise OSError(errno.EEXIST,
                              'ContainerNode {0} already exists'.format(uri))

    def delete(self, uri):
        """Delete the node
        :param uri: The (Container/Link/Data)Node to delete from the service.

        :raises When a network problem occurs, it raises one of the
        HttpException exceptions declared in the
        cadcutils.exceptions module
        """
        uri = self.fix_uri(uri)
        logger.debug("delete {0}".format(uri))
        with self.nodeCache.volatile(uri):
            url = self.get_node_url(uri, method='GET')
            response = self.conn.session.delete(url)
            response.raise_for_status()

    def get_children_info(self, uri, sort=None, order=None):
        """Returns an iterator over tuples of (NodeName, Info dict)
        :param uri: the Node to get info about.
        :param sort: node property to sort on (vos.NodeProperty)
        :param order: order of sorting: 'asc' - default or 'desc'
        """
        uri = self.fix_uri(uri)
        logger.debug(str(uri))
        node = self.get_node(uri, limit=0, force=True)
        logger.debug(str(node))
        while node.type == "vos:LinkNode":
            uri = node.target
            try:
                node = self.get_node(uri, limit=0, force=True)
            except Exception as exception:
                logger.error(str(exception))
                break
        if node.type in ["vos:DataNode", "vos:LinkNode"]:
            return [node]
        else:
            return node.get_children(self, sort, order, 500)

    def get_info_list(self, uri):
        """Retrieve a list of tuples of (NodeName, Info dict).
        Similar to the method above except that information is loaded
        directly into memory.
        :param uri: the Node to get info about.
        """
        info_list = []
        uri = self.fix_uri(uri)
        logger.debug(str(uri))
        node = self.get_node(uri, limit=None, force=True)
        logger.debug(str(node))
        while node.type == "vos:LinkNode":
            uri = node.target
            try:
                node = self.get_node(uri, limit=None, force=True)
            except Exception as exception:
                logger.error(str(exception))
                break
        for thisNode in node.node_list:
            info_list.append(thisNode)
        if node.type in ["vos:DataNode", "vos:LinkNode"]:
            info_list.append(node)
        return info_list

    def listdir(self, uri, force=False):
        """
        Walk through the directory structure a la os.walk.
        Setting force=True will make sure no cached results are used.
        Follows LinksNodes to their destination location.

        :param force: don't use cached values, retrieve from service.
        :param uri: The ContainerNode to get a listing of.
        :rtype [unicode]
        """
        # logger.debug("getting a listing of %s " % (uri))
        names = []
        logger.debug(str(uri))
        node = self.get_node(uri, limit=None, force=force)
        while node.type == "vos:LinkNode":
            uri = node.target
            # logger.debug(uri)
            node = self.get_node(uri, limit=None, force=force)
        for thisNode in node.node_list:
            names.append(thisNode.name)
        return names

    def _node_type(self, uri):
        """
        Recursively follow links until the base Node is found.
        :param uri: the VOSpace uri to recursively get the type of.
        :return: the type of Node
        :rtype: unicode
        """
        node = self.get_node(uri, limit=0)
        while node.type == "vos:LinkNode":
            uri = node.target
            if uri[0:4] == "vos:":
                node = self.get_node(uri, limit=0)
            else:
                return "vos:DataNode"
        return node.type

    def size(self, uri):
        node = self.get_node(uri, limit=0)
        while node.type == "vos:LinkNode":
            uri = node.target
            if uri[0:4] == "vos:":
                node = self.get_node(uri, limit=0)
            else:
                return int(requests.head(uri).headers.get('Content-Length', 0))
        return node.get_info()['size']

    def isdir(self, uri):
        """
        Check to see if the given uri is or is a link to a containerNode.

        :param uri: a VOSpace Node URI to test.
        :rtype: bool
        """
        try:
            return self._node_type(uri) == "vos:ContainerNode"
        except exceptions.NotFoundException:
            return False

    def isfile(self, uri):
        """
        Check if the given uri is or is a link to a DataNode

        :param uri: the VOSpace Node URI to test.
        :rtype: bool
        """
        try:
            return self._node_type(uri) == "vos:DataNode"
        except OSError as ex:
            if ex.errno == errno.ENOENT:
                return False
            raise ex

    def access(self, uri, mode=os.O_RDONLY):
        """Test if the give VOSpace uri can be accessed in the way requested.

        :param uri:  a VOSpace location.
        :param mode: os.O_RDONLY
        """

        if mode == os.O_RDONLY:
            try:
                self.get_node(uri, limit=0, force=True)
            except (exceptions.NotFoundException,
                    exceptions.AlreadyExistsException,
                    exceptions.UnauthorizedException,
                    exceptions.ForbiddenException):
                return False

        return isinstance(self.open(uri, mode=mode), VOFile)

    def status(self, uri, code=None):
        """Check to see if this given uri points at a containerNode.

        This is done by checking the view=data header and seeing if you
        get an error.
        :param uri: the VOSpace (Container/Link/Data)Node to check access
        status on.
        :param code: NOT SUPPORTED.
        """
        if not code:
            raise OSError(errno.ENOSYS,
                          "Use of 'code' option values no longer supported.")
        self.get_node(uri)
        return True

    def get_job_status(self, url):
        """ Returns the status of a job
        :param url: the URL of the UWS job to get status of.
        :rtype: unicode
        """
        return VOFile(url, self.conn, method="GET",
                      follow_redirect=False).read()
