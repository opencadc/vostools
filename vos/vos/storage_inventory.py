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
from email.utils import parsedate_tz, mktime_tz
from datetime import datetime
from cadcutils.exceptions import NotFoundException

from six.moves.urllib.parse import urlparse

try:
    from .version import version
except ImportError:
    version = 'unknown'

from .vos import Transfer, EndPoints, Md5File, SSO_SECURITY_METHODS


logger = logging.getLogger('vos')
logger.setLevel(logging.ERROR)

# Header for token access
HEADER_DELEG_TOKEN = 'X-CADC-DelegationToken'

# Storage Inventory services
FILES = 'files'
LOCATE = 'locate'
CLOCK_TOLERANCE = 5 * 60  # seconds


def parsedate_to_datetime(data):
    dtuple = parsedate_tz(data)
    return datetime.fromtimestamp(mktime_tz(dtuple))


class StorageEndPoints(EndPoints):
    # Storage specific end points.
    FILES_STANDARD_ID = 'http://www.opencadc.org/std/storage#files-1.0'
    LOCATE_STANDARD_ID = 'http://www.opencadc.org/std/storage#locate-1.0'

    @property
    def transfer(self):
        """
        Denotes a global service capable of transfer negotiation
        :return:
        """
        try:
            return \
                self.conn.ws_client._get_url((self.LOCATE_STANDARD_ID, None))
        except KeyError:
            return None

    @property
    def files(self):
        """
        Denotes a local service where files are directly accessible
        :return:
        """
        try:
            return \
                self.conn.ws_client._get_url((self.FILES_STANDARD_ID, None))
        except KeyError:
            return None


class Client(object):
    """
      The storage_inventory.Client to interact with a Storage Inventory
      system.
    """

    def __init__(self, resource_id, certfile=None, token=None):
        """
        :param resource_id:     The service Resource ID to use.
        :param certfile: x509 proxy certificate file location.
        Overrides certfile in conn.
        :type certfile: unicode
        :param token: token string (alternative to certfile)
        :type token: unicode
        """

        self.resource_id = resource_id
        self.token = token
        # unlike VOSpace client a storage client can only talk to one storage
        # service (identified by the resource_id) at the time
        self._endpoints = StorageEndPoints(
                resource_id, vospace_certfile=certfile,
                vospace_token=token)

    def get_endpoints(self):
        return self._endpoints

    def get_session(self):
        return self.get_endpoints().session

    def copy(self, source, destination, send_md5=False, disposition=False,
             head=None):
        """
            Copy from the source to the destination.

            One of source or destination must be a vospace location and the
            other must be a local location.

            :param source: The source file to send to VOSpace or the VOSpace
            node to retrieve
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
            logging.debug(
                'Source is remote.  Downloading from {} to {}'.format(
                    source, destination))
            metadata = self._get(source, destination)
        elif is_destination_remote:
            logging.debug(
                'Destination is remote.  Uploading from {} to {}'.format(
                    source, destination))
            if not os.path.isfile(source):
                raise AttributeError('{} not found'.format(source))
            metadata = self._put(source, destination)
        else:
            raise ValueError('Unable to process copying {} to {}'.format(
                                source, destination))
        if send_md5:
            return metadata.get('content_md5')
        elif disposition:
            return metadata.get('content_disposition')
        else:
            return metadata.get('content_length')

    def delete(self, uri):
        """Delete the Artifact
        :param uri: The Artifact URI to delete.

        :raises When a network problem occurs, it raises one of the
        HttpException exceptions declared in the
        cadcutils.exceptions module
        """
        logger.debug("delete {0}".format(uri))
        url = '{}/{}'.format(
            self._get_ws_client()._get_url((self.standard_id, None)), uri)
        response = self.conn.session.delete(url)
        response.raise_for_status()

    def transfer(self, uri, direction, view=None, cutout=None):
        trans = Transfer(self.get_endpoints())
        return trans.transfer(
            self.get_endpoints().transfer, uri, direction, view, cutout,
            security_methods=[SSO_SECURITY_METHODS['tls-with-certificate']])

    def _get_urls(self, uri, direction):
        """
        Returns a list of URLs corresponding to the URI, transfer direction
        and type of service (global with transfer negotiation or local)
        :param uri:
        :param direction:
        :return:
        """
        if self.get_endpoints().transfer:
            urls = self.transfer(uri, direction)
        elif self.get_endpoints().files:
            urls = ['{}/{}'.format(self.get_endpoints().files, uri)]
        else:
            raise AttributeError(
                'Resource ID {} does not support transfer or files '
                'services'.format(self.get_endpoints().resource_id))
        return urls

    def _get(self, source, destination):
        """
        :param source: The source Artifact to download (URI).
        :type uri:  unicode
        :param destination: The destination to write the data to (path).
        :type destination: unicode
        :return: destination size in bytes
        :rtype long
        """
        meta = self.get_metadata(source)
        rsize = meta['content_length']
        rdate = meta['content_date']
        if os.path.isfile(destination):
            lsize = os.stat(destination).st_size
            ldate = datetime.fromtimestamp(os.stat(destination).st_mtime)
            if rdate and (ldate - rdate).seconds > CLOCK_TOLERANCE and \
                    rsize and rsize == lsize:
                # consider source and destination as identical
                logger.debug(
                    'GET: Skip - source {} and destination {} identical '
                    '(same size and local date is more recent'.format(
                        source, destination))
                return meta
        # TODO check the md5 here?
        download_urls = self._get_urls(source, Transfer.DIRECTION_PULL_FROM)
        stream = Stream(self.get_session())
        logger.debug('Downloading {} from {}'.format(source, download_urls))
        for url in download_urls:
            try:
                result = stream.download(url, source, None, destination, True)
                return self._extract_metadata(source, result)
            except Exception as e:
                logger.debug('Failed to get {} from URL {} - {}'.
                             format(source, url, str(e)))
        raise OSError(
            'Failed to get file {}'.format(source))

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
        meta = self.get_metadata(destination)
        if meta:
            rsize = meta['content_length']
            rdate = meta['content_date']
            lsize = os.stat(source).st_size
            ldate = datetime.fromtimestamp(os.stat(source).st_mtime)
            if rdate and (rdate - ldate).seconds > CLOCK_TOLERANCE and rsize \
                    and rsize == lsize:
                # consider source and destination as identical
                logger.debug(
                    'PUT: Skip - source {} and destination {} identical '
                    '(same size and remote date is more recent'.format(
                        source, destination))
                return meta
        # TODO check the md5 here?
        put_urls = self._get_urls(destination, Transfer.DIRECTION_PUSH_TO)
        if not put_urls:
            raise OSError(errno.EFAULT,
                          'BUG: Failed to copy {0} -> {1} - No PUT URLs '
                          'negotiated'.format(source, destination))
        logger.debug('PUT urls: {}'.format(put_urls))
        stream = Stream(self.get_session())
        logger.debug('Uploading {}'.format(source))
        for put_url in put_urls:
            try:
                success = stream.upload(put_url, destination, source,
                                        self.get_metadata)
                return success
            except Exception as e:
                logger.debug('Error uploading URI {} to location {} - {}'.
                             format(source, put_url, str(e)))
        raise OSError('Failed to copy {0} -> {1}'.format(source, destination))

    def _is_remote(self, uri):
        _uri = urlparse(uri)
        return len(_uri.scheme) > 0 and _uri.scheme != 'file'

    def get_metadata(self, uri, get_url=None):
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
        if get_url:
            get_urls = [get_url]
        else:
            get_urls = self._get_urls(uri, Transfer.DIRECTION_PULL_FROM)
        logger.debug('HEAD urls: {}'.format(get_urls))
        response = None
        for get_url in get_urls:
            try:
                response = self.get_endpoints().session.head(get_url)
                break
            except NotFoundException:
                return None
            except Exception as e:
                logger.debug('Error calling HEAD on location {} - {}'.
                             format(get_url, str(e)))
        if not response:
            raise OSError(errno.EFAULT,
                          "Failed to get metadata for {0}".
                          format(uri))
        response.raise_for_status()
        return self._extract_metadata(uri, response)

    def _extract_metadata(self, uri, response):
        headers = response.headers
        date = headers.get('Date', None)
        if date:
            date = parsedate_to_datetime(date)
        length = headers.get('Content-Length', None)
        length = int(length) if length else None
        return {
            'content_disposition': os.path.split(uri)[-1],
            'content_encoding': headers.get('Content-Encoding', None),
            'content_length': length,
            'content_md5': headers.get('Content-MD5', None),
            'content_type': headers.get('Content-Type', None),
            'content_date': date
        }


class Stream(object):
    # 512 Kilobyte default chunk size.
    DEFAULT_CHUNK_SIZE = 512 * 1024

    # md5sum of a size zero file
    ZERO_MD5 = 'd41d8cd98f00b204e9800998ecf8427e'

    def __init__(self, session, chunk_size=DEFAULT_CHUNK_SIZE):
        """
        Handle transfer related business.  This is here to be reused as needed.
        """
        self.session = session
        self.chunk_size = chunk_size

    def download(self, get_url, source, source_md5, destination, disposition):
        """
        Stream bytes from the provided URL and write them to the given
        destination.

        :param get_url: The URL to GET from.
        :type get_url: unicode
        :param source: The source URI/file
        :type soruce: unicode
        :param source_md5: The MD5 hash value
        :type source_md5: unicode
        :param destination: The destination path to write to.
        :type destination: unicode
        :param disposition: Flag whether to honour the Content-Disposition
        header
        :type disposition: bool
        """
        response = self.session.get(get_url, timeout=(2, 5), stream=True)
        if disposition:
            # Build the destination location from the
            # content-disposition value, or source name.
            content_disposition = response.headers.get(
                'content-disposition', destination)
            content_disposition = re.search(r'.*filename="(\S*)".*',
                                            content_disposition)
            if content_disposition is not None:
                content_disposition = content_disposition.group(1).strip()
            else:
                content_disposition = os.path.split(source)[-1]
            if os.path.isdir(destination):
                destination = os.path.join(destination, content_disposition)
        source_md5 = response.headers.get('Content-MD5', source_md5)
        response.raise_for_status()
        with Md5File(destination, 'wb') as fout:
            for chunk in response.iter_content(chunk_size=self.chunk_size):
                if chunk:
                    fout.write(chunk)
                    fout.flush()
        dest_size = os.stat(destination).st_size
        src_size = response.headers.get('Content-Length', None)
        if source_md5:
            if source_md5 != fout.md5_checksum:
                os.remove(destination)  # TODO keep original when override?
                raise OSError(
                    'Source and destination md5 for {} do not match: '
                    '{} vs. {}'.format(source, source_md5, fout.md5_checksum))
            else:
                return response
        if src_size is not None:
            if int(src_size) != dest_size:
                os.remove(destination)  # TODO keep original when override?
                raise OSError('Source and destination sizes for {} do not '
                              'match: {} vs. {}'.format(source, src_size,
                                                        dest_size))
            else:
                return response
        raise OSError('BUG: No size or MD5 sum sent by remote server for {}'.
                      format(source))

    def upload(self, put_url, artifact_uri, source,
               get_metadata_fn):
        """
        Upload the source data to the put_url.

        :param put_url: The Service URL accepting PUTs.
        :type put_url: unicode
        :param artifact_uri: The URI of the destination.
        :type artifact_uri: unicode
        :param source: The source data (file)
        :type source: unicode
        :param source_md5: The MD5 hash value
        :type source_md5: unicode
        :param get_metadata_fn:  Function to obtain metadata information
        :type get_metadata_fn: function
        :return: Metadata dict for the uploaded Artifact.
        :rtype {}
        """

        with Md5File(source, str('rb')) as fin:
            response = self.session.put(put_url, data=fin)
        response.raise_for_status()

        metadata = get_metadata_fn(artifact_uri, put_url)
        destination_md5 = metadata.get('content_md5', None)
        dest_size = metadata.get('content_length', None)
        if destination_md5:
            if destination_md5 != fin.md5_checksum:
                raise OSError(
                    "Source md5 ({}) != destination md5 ({})".
                    format(fin.md5_checksum, destination_md5))
            else:
                return metadata
        if dest_size is not None:
            src_size = os.stat(source).st_size
            if int(dest_size) != src_size:
                raise OSError('Source and destination sizes for {} do not '
                              'match: {} vs. {}'.format(source, src_size,
                                                        dest_size))
            else:
                return metadata
        raise OSError('BUG: No size or MD5 sum sent by remote server for {}'.
                      format(artifact_uri))
