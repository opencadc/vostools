import configparser
import time
import os
import tempfile

from vos import storage_inventory


cp = configparser.ConfigParser()
cp.read('config/test_config')
config = cp['GENERAL']
global_config = cp['GLOBAL']
site_config = cp['SITE']

def update_config():
    global cp, config, global_config, site_config
    cp = configparser.ConfigParser()
    cp.read('./config/test_config')
    config = cp['GENERAL']
    global_config = cp['GLOBAL']
    site_config = cp['SITE']

def get_config():
    return config

if config.get('REG', None):
    os.environ['VOSPACE_WEBSERVICE'] = config.get('REG')
os.environ['CURL_CA_BUNDLE'] = ''


class SiteTester():
    def __init__(self, *args, **kwargs):
        update_config()
        self.site_anon_client = storage_inventory.Client(
            site_config['RESOURCE_ID'])
        try:
            self.site_auth_client = storage_inventory.Client(
            site_config['RESOURCE_ID'], certfile='./config/cadcproxy.pem')
        except KeyError:
            raise AttributeError('CERT_FILE not set in the config file')
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
        self.file_size = int(site_config.get('MIN_FILESIZE', 10))
        self.max_file_size_order = \
            int(site_config.get('MAX_FILE_SIZE_ORDER', 0))
        self.current_size_order = 0

    def get_next_file_size(self):
        # returns the next size of the file.
        # round-robin
        next_size = self.file_size * 10**self.current_size_order
        # or random
        # next_size = self.file_size * \
        #             random.randrange(0,self.max_file_size_order+1)
        if self.current_size_order == self.max_file_size_order:
            # start again
            self.current_size_order = 0
        else:
            self.current_size_order += 1
        return next_size

    def put_file(self, domain_uri, file_size):
        source = tempfile.NamedTemporaryFile(
            dir=site_config.get('UPLOAD_DIR', None)).name
        open(source, 'a').truncate(file_size)
        file_uri = '{}/perftests/{}'.format(domain_uri, source)
        self.site_auth_client.copy(source, file_uri)
        return file_uri

    def get_file(self, file_uri):
        """
        Gets a file and redirects it to /dev/null
        :return:
        """
        download_dir = site_config.get('UPLOAD_DIR', None)
        if download_dir and download_dir != '/dev/null':
            dest = download_dir
        else:
            dest = tempfile.TemporaryFile(dir=download_dir).name
        self.site_auth_client.copy(file_uri, dest)

    def delete_file(self, file_uri):
        self.site_auth_client.delete(file_uri)

if __name__ == '__main__':
    st = SiteTester()
    #st.get_file('cadc:TEST/perftests//var/folders/w4/pkjw20_n4j37m0lv5bnz6k6w0000gr/T/tmp95bhrrp1')
    for i in range(3):
        start = time.time()
        file_uri = st.put_file('cadc:TEST-CEPH4K-PRIV', st.get_next_file_size())
        print('PUT: {}'.format(int((time.time() - start) * 1000)))
        start = time.time()
        st.get_file(file_uri)
        print('GET: {}'.format(int((time.time() - start) * 1000)))
        start = time.time()
        st.delete_file(file_uri)
        print('DELETE: {}'.format(int((time.time() - start) * 1000)))
