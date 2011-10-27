def getCert(certHost='www.cadc-ccda.hia-iha.nrc-cnrc.gc.ca',
            certQuery="/cred/proxyCert?daysValid=2"):
    """Access the cadc certificate server"""
    
    import urllib2
    
    ## Example taken from voidspace.org.uk
    # create a password manager
    password_mgr = urllib2.HTTPPasswordMgrWithDefaultRealm()

    (username,passwd)=getUserPassword(host=certHost)

    # Add the username and password.
    # If we knew the realm, we could use it instead of ``None``.
    top_level_url = "http://"+certHost
    password_mgr.add_password(None, top_level_url, username, passwd)
    
    handler = urllib2.HTTPBasicAuthHandler(password_mgr)
    
    # create "opener" (OpenerDirector instance)
    opener = urllib2.build_opener(handler)
    
    # Install the opener.   
    urllib2.install_opener(opener)

    # Now all calls to urllib2.urlopen use our opener.

    r= urllib2.urlopen("http://"+certHost+certQuery)
    w= file(certfile,'w')
    while True:
        buf=r.read()
        if not buf:
            break
        w.write(buf)
    w.close()
    r.close()
    return 

def getUserPassword(self,host='www.cadc-ccda.hia-iha.nrc-cnrc.gc.ca'):
    import netrc,getpass,os
    """"Getting the username/password for host from .netrc filie """
    if os.access(os.path.join(os.environ.get('HOME','/'),".netrc"),os.R_OK):
        auth=netrc.netrc().authenticators(host)
    else:
        auth=False
    if not auth:
        sys.stdout.write("CADC Username: ")
        username=sys.stdin.readline().strip('\n')
        password=getpass.getpass().strip('\n')
    else:
        username=auth[0]
        password=auth[2]
    return (username,password)



if __name__=='__main__':
    getCert()
