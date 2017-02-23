"""interact with the CADC GMS

"""

import sys
import getpass
import argparse
try:
    from OpenSSL import crypto
except ImportError as e:
    sys.stderr.write("\n\nModule requires OpenSSL. \n\nPerhaps try: easy_install pyOpenSSL\n\n")
    sys.exit(-1)
import cgi
import httplib
import os
import string
import sys
import tempfile
import urllib2
import urllib
import logging

_SERVER = 'www.cadc-ccda.hia-iha.nrc-cnrc.gc.ca'
_GMS = '/gms/members/'
_PROXY = '/cred/proxyCert'


def getCert(username,password,
            certHost=_SERVER,
            certfile=None,
            certQuery=_PROXY):
    """Access the cadc certificate server and request a certficate on behalf of the user.

    userid/password are sent clear text to the CADC certificat service.

    returnd object is the users CADC X509 certificate
    """

    if certfile is None:
        certfile=tempfile.NamedTemporaryFile()

    # Add the username and password.
    # If we knew the realm, we could use it instead of ``None``.
    password_mgr = urllib2.HTTPPasswordMgrWithDefaultRealm()

    ## set the URL to the non-SSL version.
    top_level_url = "http://"+certHost
    logging.debug(top_level_url)
    password_mgr.add_password(None, top_level_url, username, password)    
    handler = urllib2.HTTPBasicAuthHandler(password_mgr)
    logging.debug(str(handler))

    # create "opener" (OpenerDirector instance)
    opener = urllib2.build_opener(handler)
    
    # Install the opener.   
    urllib2.install_opener(opener)

    # build the url that with 'GET' a certificat using user_id/password info
    url="http://"+certHost+certQuery
    logging.debug(url)

    r=None
    try:
        r = opener.open(url)
    except urllib2.HTTPError as e:
        logging.debug(url)
        logging.debug(str(e))
        return False

    logging.debug(str(r))

    if r is not None:
        while True :
            buf=r.read()
            logging.debug(buf)
            if not buf:
                break
            certfile.write(buf)
        r.close()

    return certfile


def getGroupsURL(certfile):
    """Provide the URL that GMS calls should be made on, based on the contents of the 
    certfile.  The URL is a 'GET' post to the HTTPS GMS server.


    Connectcing to this URL using the supplied X509 
    the GMS will respond with an XML document that describes the groups 
    the X509 can access as a memeber.
    """

    
    GMS="https://"+_SERVER+_GMS

    certfile.seek(0)
    buf = certfile.read()

    x509 = crypto.load_certificate(crypto.FILETYPE_PEM,buf)
    sep = ""
    dn = ""
    parts=[]
    for i in x509.get_issuer().get_components():
        #print i
        if i[0] in parts:
            continue
        parts.append(i[0])
        dn=i[0]+"="+i[1]+sep+dn
        sep=","

    return GMS+urllib.quote(dn)


def isMember(userid, password, group):
    """Test to see if the given userid/password combo is an authenticated member of group.

    userid: CADC Username (str)
    password: CADC Password (str)
    group: CADC GMS group (str)

    This is achieved by connecting to the membership service using SSL cert pulled from the 
    certificate service using the userid/password combination provided
    
    """
    try:
        certfile = getCert(userid,password)

        group_url = getGroupsURL(certfile)+"/"+group
        logging.debug("group url: %s" % ( group_url))

        con = httplib.HTTPSConnection(_SERVER, 
                                      443, 
                                      key_file=certfile.name,
                                      cert_file=certfile.name,
                                      timeout=600)

        con.connect()
        con.request("GET",group_url)
        resp = con.getresponse()
        if resp.status == 200:
            return True
    except Exception as e:
        logging.error(str(e))

    return False


def form_stub():
    """An example of how a form might use this module.
    
    Form should have 'userid', 'passwd' and 'group' values set.

    """
    form=cgi.FieldStorage()
    userid=form['userid'].value
    password=form['passwd'].value
    group=form['group'].value

    return isMember(userid, password, group)


if __name__=='__main__':
    ## Grab the needed info from the command line or via prompts
    
    parser = argparse.ArgumentParser(description="Verify CANFAR user is a member of a CANFAR group.")
    parser.add_argument("--user", "-u", type=str, help="CADC username.")
    parser.add_argument("--pswd", "-p", type=str, help="CADC password.")
    parser.add_argument("--group", "-g", help="CADC group to test membership in.")

    args = parser.parse_args()

    group = ( args.group is not None and args.group ) or raw_input("CADC Group: ")
    user = ( args.user is not None and args.user ) or raw_input("CADC Username: ")
    password = ( args.pswd is not None and args.pswd ) or getpass.getpass()

    logging.basicConfig(level=logging.CRITICAL)
    logging.debug("user: %s" % ( user))
    logging.debug("group: %s" % ( group))
    print isMember(user, password, group)

