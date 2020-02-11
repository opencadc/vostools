"""A set of Python Classes for connecting to and interacting with a VOSpace
   service.

   Connections to VOSpace are made using a SSL X509 certificat which is
   stored in a .pem file.
"""
from __future__ import (absolute_import, division, print_function,
                        unicode_literals)

import errno
import os
import logging
import re

from cadcutils import net, exceptions
from six.moves.urllib import parse

try:
    from .version import version
except ImportError:
    version = 'unknown'

from .vos import Connection, EndPoints, Transfer, Stream
from . import md5_cache

urlparse = parse.urlparse

# This is an encoder.  It will URL encode any URI component.
# It is used in creating the DELETE URL by appending the
# encoded URI to the end of the path.
#
quote_plus = parse.quote_plus

logger = logging.getLogger('vos')
logger.setLevel(logging.ERROR)

# Header for token access
HEADER_DELEG_TOKEN = 'X-CADC-DelegationToken'

# Create an agent for URL communications.
VOS_AGENT = 'vos/' + version

# Standard ID
STANDARD_ID = 'http://www.opencadc.org/std/storage#files-1.0'

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

RAVEN_URI = 'ivo://cadc.nrc.ca/raven'
MINOC_URI = 'ivo://cadc.nrc.ca/minoc'


class Client(object):
    """
      The storage_inventory.Client to interact with the Storage Inventory
      system.
    """

    def __init__(self, resource_id, certfile=None, conn=None,
                 secure_get=False, token=None):
        """
        :param resource_id:     The service Resource ID to use.  Defaults to
        the Global Raven Site.
        :param certfile: x509 proxy certificate file location.
        Overrides certfile in conn.
        :type certfile: unicode
        :param token: token string (alternative to certfile)
        :type token: unicode
        :param conn: a connection pool object for this Client
        :type conn: Session
        :param http_debug: turn on http debugging.
        :type http_debug: bool
        :param secure_get: Use HTTPS: ie. transfer contents of files using
        SSL encryption.
        :type secure_get: bool
        """

        self.resource_id = resource_id
        self.secure_get = secure_get
        self.token = token

        if conn:
            self.conn = conn
        else:
            self.conn = Connection(vospace_certfile=certfile,
                                   vospace_token=token,
                                   http_debug=True,
                                   resource_id=resource_id)

    def copy(self, source, destination, send_md5=False, disposition=False,
             head=None):
        """
            Copy from the source to the destination.

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
            :param head: Return just the headers of a file.
            :type head: bool
            :raises When a network problem occurs, it raises one of the
            HttpException exceptions declared in the
            cadcutils.exceptions module
        """

        if not source:
            raise ValueError('Source is mandatory.')
        elif not destination:
            raise ValueError('Destination is mandatory.')

        logger.debug('Checking sanity of inputs.')
        is_source_remote = self._is_remote(source)
        is_destination_remote = self._is_remote(destination)

        if is_source_remote and is_destination_remote:
            raise ValueError('Unable to process server to server copying.')
        elif is_source_remote:
            logger.debug('Source is remote.  Downloading from {} to {}'.format(source, destination))
            destination_size = self._get(source, destination)
            if send_md5:
                return md5_cache.MD5Cache.compute_md5(destination)
            elif disposition:
                # TODO: Should this return the name of the destination instead?
                # TODO: jenkinsd 2020.02.05
                #
                return None
            else:
                return destination_size
        elif is_destination_remote:
            logger.debug('Destination is remote.  Uploading from {} to {}'.format(source, destination))
            metadata = self._put(source, destination)
            if send_md5:
                return metadata.get('content_md5')
            elif disposition:
                return metadata.get('content_disposition')
            else:
                return metadata.get('content_length')
        else:
            raise ValueError('Unable to process copying {} to {}'.format(
                                source, destination))

    def delete(self, uri):
        """Delete the Artifact
        :param uri: The Artifact URI to delete.

        :raises When a network problem occurs, it raises one of the
        HttpException exceptions declared in the
        cadcutils.exceptions module
        """
        logger.debug("delete {0}".format(uri))
        url = '{}/{}'.format(self.get_endpoints()[self.resource_id].nodes,
                             quote_plus(uri))
        response = self.conn.session.delete(url)
        response.raise_for_status()

    def get_endpoints(self):
        return {self.resource_id: EndPoints(self.resource_id)}

    def transfer(self, uri, direction, view=None, cutout=None):
        transfer_url = '{}/{}'.format(
            self._get_ws_client()._get_url((STANDARD_ID, None)), uri)
        trans = Transfer()
        return trans.transfer(transfer_url, uri, direction, self.conn,
                              self._get_protocol(), view, cutout)

    def get_transfer_error(self, uri, url):
        trans = Transfer()
        return trans.get_transfer_error(self.conn, uri, url)

    def _get(self, source, destination):
        """
        :param source: The source Artifact to download (URI).
        :type uri:  unicode
        :param destination: The destination to write the data to (path).
        :type destination: unicode
        :return: destination size in bytes
        :rtype long
        """
        source_md5 = self.get_metadata(source).get('content_md5')
        download_url = '{}/{}'.format(
            self._get_ws_client()._get_url((STANDARD_ID, None)), source)

        stream = Stream(self.conn)
        logger.debug('Downloading {}'.format(source))
        return stream.download(download_url, source, source_md5, destination,
                               True, True)

    def _put(self, source, destination):
        """
        Perform the upload and verify the MD5 post write.

        :param source: The source Artifact to download (URI).
        :type uri:  unicode
        :param destination: The destination to write the data to (path).
        :type destination: unicode
        :return: metadata dict for the artifact.
        :rtype {}
        """
        source_md5 = md5_cache.MD5Cache.compute_md5(source)

        upload_url = '{}/{}'.format(
            self._get_ws_client()._get_url((STANDARD_ID, None)), destination)
        stream = Stream(self.conn)
        logger.debug('Uploading {}'.format(source))
        return stream.upload(upload_url, destination, source, source_md5,
                             self.get_metadata)

    def _get_protocol(self):
        if self.secure_get:
            return 'https'
        else:
            return 'http'

    def _is_remote(self, uri):
        _uri = urlparse(uri)
        return _uri.scheme and _uri.scheme != 'file'

    def _get_ws_client(self, session_headers=None):
        return net.BaseWsClient(self.resource_id, EndPoints.subject, VOS_AGENT,
                                host=os.getenv('VOSPACE_WEBSERVICE', None),
                                session_headers=session_headers)

    def get_metadata(self, uri):
        """
        Obtain the general metadata for the artifact/node identified by the
        given uri.

        :param uri: An Artifact URI.
        :type uri: unicode
        :return: A dict with the following keys:
                - content_disposition: unicode filename disposition
                - content_encoding: unicode encoding value (i.e. gzip)
                - content_length: long value; count of bytes
                - content_md5: unicode hash value
                - content_type: unicode content type string
                                (i.e. application/fits)
        :rtype: {}
        """
        logger.debug('vcp::get_metadata')
        session_headers = {HEADER_DELEG_TOKEN: self.token}
        ws_client = self._get_ws_client()
        ws_client.session_headers = session_headers

        response = ws_client.head(resource=(STANDARD_ID, uri))
        headers = response.headers

        return {
            'content_disposition': os.path.split(uri)[-1],
            'content_encoding': headers.get('Content-Encoding'),
            'content_length': headers.get('Content-Length'),
            'content_md5': headers.get('Content-MD5'),
            'content_type': headers.get('Content-Type')
        }

    def isdir(self, uri):
        """
        Check to see if the given uri is or is a link to a containerNode.

        :param uri: a URI string to test.
        :rtype: bool
        """
        try:
            _uri = urlparse(uri)
            path = _uri.path

            if not _uri.scheme or _uri.scheme == 'file':
                return os.path.isdir(path) or path.endswith('/')
            else:
                return path.endswith('/')
        except exceptions.NotFoundException:
            return False

    def isfile(self, uri):
        """
        Check if the given uri is or is a link to a DataNode

        :param uri: the VOSpace Node URI to test.
        :rtype: bool
        """
        try:
            _uri = urlparse(uri)
            path = _uri.path

            if not _uri.scheme or _uri.scheme == 'file':
                return os.path.isfile(path) or not path.endswith('/')
            else:
                return not path.endswith('/') and len(path.split('/')) > 1
        except OSError as ex:
            if ex.errno == errno.ENOENT:
                return False
            raise ex
