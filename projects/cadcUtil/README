This is the OpenCADC utility library. 

It includes classes that are very general purpose and used in multiple other OpenCADC
projects. 


* Note about the SSL support in ca.nrc.cadc.auth

The SSLUtil class is intended to support IVOA Single Sign On (SSO) so the
current functionaility is aimed at mutual authentication: both the client
and the server have X509 certificates. We assume the client is actually using 
a short-lived, no-password proxy certificate. The createProxyCert script uses
openssl to create a proxy certificate and associated priovate key for this 
purpose.

The SSLUtilTest code assumes that you have a valid proxy certificate named proxy.crt, 
associated private key (proxy.key) and a file that contains both the certificate and the
key (proxy.pem) in the classpath (e.g. in build/test/class). All three types of files 
can be created with the script:

./scripts/createProxyCert <your real cert> <your private key> <days> build/test/class/proxy

The test/resources/proxy.pem file can be used with curl, assuming your version of curl 
is built with SSL support.

The SSL tests try to connect to 3 different https URLS:
https://www.google.com/
https://<FQDN of localhost>/
https://www.cadc-ccda.hia-iha.nrc-cnrc.gc.ca/

Google has a valid server certificate and does not challenge for a client certificate.

We assume localhost has an invalid (eg self-signed) server certificate and the test is
intended to fail to trust the server. This is typical of a developer workstation, for
example. A second test with this server passes when a special system property is set 
(see BasicX509TrustManager); this is intended for use in test code only and not as a
work-around for using real services with invalid certificates!!

CADC's public web servers have valid server certificates and require client authentication via
certificate so they act as a decent SSO setup test.

Note: The local host test is currently disabled in the test code (pdowler 2010-07-15)

