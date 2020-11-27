import configparser
import vos
import requests
from random import randrange
import os

from vos import storage_inventory


cp = configparser.ConfigParser()
cp.read('./test_config')
config = cp['GENERAL']
global_config = cp['GLOBAL']
local_config = cp['LOCAL']


if config.get('REG', None):
    os.environ['VOSPACE_WEBSERVICE'] = config.get('REG')
os.environ['CURL_CA_BUNDLE'] = ''


class GlobalTester():
    def __init__(self, *args, **kwargs):
        self.global_anon_client = storage_inventory.Client(
            global_config['RESOURCE_ID'])
        try:
            self.global_auth_client = storage_inventory.Client(
            global_config['RESOURCE_ID'], certfile='./config/cadcproxy.pem')
        except KeyError:
            raise AttributeError('RESOURCE_ID not set in the config file')
        self.gpub_uris = None
        self.gpriv_uris = None
        try:
            self.anon_only = config['ANON_TESTS_ONLY'] is not None
        except KeyError:
            self.anon_only = False
        try:
            self.auth_only = config['AUTH_TESTS_ONLY'] is not None
        except KeyError:
            self.auth_only = False

    def anon_get_pub(self):
        """
        Negotiate a transfer request for a random public file
        :return:
        """
        if self.auth_only:
            return
        pub_uri = self._get_next_pub_uri()
        #print('Get pub {}'.format(pub_uri))
        self.global_anon_client._get_urls(pub_uri,
                                          vos.vos.Transfer.DIRECTION_PULL_FROM)

    def anon_get_priv(self):
        """
        Negotiate anon for private files
        :return:
        """
        if self.auth_only:
            return
        priv_uri = self._get_next_priv_uri()
        #print('Get priv {}'.format(priv_uri))
        self.global_anon_client._get_urls(priv_uri,
                                          vos.vos.Transfer.DIRECTION_PULL_FROM)



    def auth_get_pub(self):
        """
        Auth user negotiate a transfer request for a random public file
        :return:
        """
        if self.anon_only:
            return
        pub_uri = self._get_next_pub_uri()
        # print('Get pub {}'.format(pub_uri))
        self.global_auth_client._get_urls(pub_uri,
                                          vos.vos.Transfer.DIRECTION_PULL_FROM)

    def auth_get_priv(self):
        """
        Auth client negotiate anon for private files
        :return:
        """
        if self.anon_only:
            return
        priv_uri = self._get_next_priv_uri()
        # print('Get priv {}'.format(priv_uri))
        self.global_auth_client._get_urls(priv_uri,
                                          vos.vos.Transfer.DIRECTION_PULL_FROM)

    def anon_put(self):
        """
        Anon client negotiate anon puts. Fails
        :return:
        """
        if self.auth_only:
            return
        self.global_anon_client._get_urls('cadc:TEST/fred.fits',
                                          vos.vos.Transfer.DIRECTION_PUSH_TO)

    def auth_put(self):
        """
        Anon client negotiate auth puts.
        :return:
        """
        if self.anon_only:
            return
        self.global_auth_client._get_urls('cadc:TEST/fred.fits',
                                          vos.vos.Transfer.DIRECTION_PUSH_TO)

    def _get_next_pub_uri(self):
        """
        Creates a list of public IRIS URIs a returns a random one
        :return:
        """
        if not self.gpub_uris:
            result = requests.get(
                global_config['LUSKAN_URL'], params=
                {'QUERY': "select top {} uri from inventory.artifact where "
                      "uri like 'ad:IRIS%'".format(
                    global_config['NUM_URIS']),
             'LANG': 'ADQL', 'FORMAT': 'csv'})
            result.raise_for_status()
            self.gpub_uris = result.text.split()[1:]  # remove header uri
        return self.gpub_uris[randrange(len(self.gpub_uris))]

    def _get_next_priv_uri(self):
        """
        Creates a list of private CFHT URIs a returns a random one
        :return:
        """
        if not self.gpriv_uris:
            result = requests.get(
                global_config['ARGUS_URL'], params={
                    'QUERY': "select top 10 a.uri from "
                             "caom2.Observation o join caom2.Plane p on "
                             "o.obsID=p.obsID join caom2.Artifact a on "
                             "p.planeID=a.planeID where "
                             "dataRelease>'2021-01-01T00:00:00.000' and "
                             "collection='CFHT' and "
                             "o.maxLastModified<'2020-08-01T00:00:00.000'".
                        format(global_config['NUM_URIS']),
                    'LANG': 'ADQL', 'FORMAT': 'csv'})
            result.raise_for_status()
            self.gpriv_uris = result.text.split()[1:]  # remove header uri
        return self.gpriv_uris[randrange(len(self.gpriv_uris))]


if __name__ == '__main__':
    gt = GlobalTester()
    # gt.anon_get_pub()
    # gt.anon_get_priv()
    #gt.anon_put()
