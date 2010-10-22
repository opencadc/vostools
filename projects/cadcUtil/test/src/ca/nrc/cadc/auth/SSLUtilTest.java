/*
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2009.                            (c) 2009.
 *  Government of Canada                 Gouvernement du Canada
 *  National Research Council            Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 *  All rights reserved                  Tous droits réservés
 *
 *  NRC disclaims any warranties,        Le CNRC dénie toute garantie
 *  expressed, implied, or               énoncée, implicite ou légale,
 *  statutory, of any kind with          de quelque nature que ce
 *  respect to the software,             soit, concernant le logiciel,
 *  including without limitation         y compris sans restriction
 *  any warranty of merchantability      toute garantie de valeur
 *  or fitness for a particular          marchande ou de pertinence
 *  purpose. NRC shall not be            pour un usage particulier.
 *  liable in any event for any          Le CNRC ne pourra en aucun cas
 *  damages, whether direct or           être tenu responsable de tout
 *  indirect, special or general,        dommage, direct ou indirect,
 *  consequential or incidental,         particulier ou général,
 *  arising from the use of the          accessoire ou fortuit, résultant
 *  software.  Neither the name          de l'utilisation du logiciel. Ni
 *  of the National Research             le nom du Conseil National de
 *  Council of Canada nor the            Recherches du Canada ni les noms
 *  names of its contributors may        de ses  participants ne peuvent
 *  be used to endorse or promote        être utilisés pour approuver ou
 *  products derived from this           promouvoir les produits dérivés
 *  software without specific prior      de ce logiciel sans autorisation
 *  written permission.                  préalable et particulière
 *                                       par écrit.
 *
 *  This file is part of the             Ce fichier fait partie du projet
 *  OpenCADC project.                    OpenCADC.
 *
 *  OpenCADC is free software:           OpenCADC est un logiciel libre ;
 *  you can redistribute it and/or       vous pouvez le redistribuer ou le
 *  modify it under the terms of         modifier suivant les termes de
 *  the GNU Affero General Public        la “GNU Affero General Public
 *  License as published by the          License” telle que publiée
 *  Free Software Foundation,            par la Free Software Foundation
 *  either version 3 of the              : soit la version 3 de cette
 *  License, or (at your option)         licence, soit (à votre gré)
 *  any later version.                   toute version ultérieure.
 *
 *  OpenCADC is distributed in the       OpenCADC est distribué
 *  hope that it will be useful,         dans l’espoir qu’il vous
 *  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
 *  without even the implied             GARANTIE : sans même la garantie
 *  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
 *  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
 *  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
 *  General Public License for           Générale Publique GNU Affero
 *  more details.                        pour plus de détails.
 *
 *  You should have received             Vous devriez avoir reçu une
 *  a copy of the GNU Affero             copie de la Licence Générale
 *  General Public License along         Publique GNU Affero avec
 *  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
 *  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
 *                                       <http://www.gnu.org/licenses/>.
 *
 *  $Revision: 4 $
 *
 ************************************************************************
 */

package ca.nrc.cadc.auth;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyStore;

import javax.net.SocketFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.Subject;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.util.FileUtil;
import ca.nrc.cadc.util.Log4jInit;

/**
 * Unit tests for SSLUtil.
 *
 * @author pdowler
 */
public class SSLUtilTest
{
    private static Logger log = Logger.getLogger(SSLUtilTest.class);
    private static String TEST_CERT_FN = "proxy.crt";
    private static String TEST_KEY_FN = "proxy.key";
    private static String TEST_PEM_FN = "proxy.pem";
    private static File SSL_CERT;
    private static File SSL_KEY;
    private static File SSL_PEM;

    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
        Log4jInit.setLevel("ca.nrc.cadc.auth", Level.INFO);
        SSL_CERT = FileUtil.getFileFromResource(TEST_CERT_FN, SSLUtilTest.class);
        SSL_KEY = FileUtil.getFileFromResource(TEST_KEY_FN, SSLUtilTest.class);
        SSL_PEM = FileUtil.getFileFromResource(TEST_PEM_FN, SSLUtilTest.class);
    }

    /**
     * @throws java.lang.Exception
     */
    @AfterClass
    public static void tearDownAfterClass() throws Exception
    {
    }

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception
    {
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception
    {
    }

    @Test
    public void testReadCert() throws Exception
    {
        try
        {
            KeyStore ks = SSLUtil.getKeyStore(SSL_CERT, SSL_KEY);
            SSLUtil.printKeyStoreInfo(ks);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
    }
    
    @Test
    public void testReadPem() throws Exception
    {
        try
        {
            X509CertificateChain chain = SSLUtil.readPemCertificateAndKey(SSL_PEM);
            Assert.assertNotNull("Null chain", chain);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
    }

    //@Test
    public void testGetKMF() throws Exception
    {
        try
        {
            KeyStore ks = SSLUtil.getKeyStore(SSL_CERT, SSL_KEY);
            KeyManagerFactory kmf = SSLUtil.getKeyManagerFactory(ks);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
    }

    //@Test
    public void testGetContext() throws Exception
    {
        try
        {
            KeyStore ks = SSLUtil.getKeyStore(SSL_CERT, SSL_KEY);
            KeyStore ts = null;
            KeyManagerFactory kmf = SSLUtil.getKeyManagerFactory(ks);
            TrustManagerFactory tmf = SSLUtil.getTrustManagerFactory(ts);
            SSLContext ctx = SSLUtil.getContext(kmf, tmf, ks);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
    }

    @Test
    public void testGetSocketFactoryFromNull() throws Exception
    {
        try
        {
            SocketFactory sf;
            
            X509CertificateChain chain = null;
            sf = SSLUtil.getSocketFactory(chain);
            Assert.assertNotNull("SSLSocketFactory from null X509CertificateChain", sf);

            Subject sub = null;
            sf = SSLUtil.getSocketFactory(sub);
            Assert.assertNull("SSLSocketFactory from null Subject", sf);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
    }

    @Test
    public void testGetSocketFactoryFromFile() throws Exception
    {
        try
        {
            SocketFactory sf;

            sf = SSLUtil.getSocketFactory(SSL_CERT, SSL_KEY);
            Assert.assertNotNull("SSLSocketFactory from cert/key file", sf);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
    }

    @Test
    public void testInitSSL() throws Exception
    {
        try
        {
            SSLUtil.initSSL(SSL_CERT, SSL_KEY);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
    }

    public void testHTTPS(URL url) throws Exception
    {
        HttpURLConnection.setFollowRedirects(false);

        SSLSocketFactory sf = SSLUtil.getSocketFactory(SSL_CERT, SSL_KEY);
        URLConnection con = url.openConnection();

        log.debug("URLConnection type: " + con.getClass().getName());
        HttpsURLConnection ucon = (HttpsURLConnection) con;
        ucon.setSSLSocketFactory(sf);
        log.debug("status: " + ucon.getResponseCode());
        log.debug("content-length: " + ucon.getContentLength());
        log.debug("content-type: " + ucon.getContentType());
    }

    @Test
    public void testGoogleHTTPS() throws Exception
    {
        try
        {
            URL url = new URL("https://www.google.com/");
            log.debug("test URL: " + url);
            testHTTPS(url);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
    }

    @Test
    public void testCadcHTTPS() throws Exception
    {
        try
        {
            URL url = new URL("https://www.cadc-ccda.hia-iha.nrc-cnrc.gc.ca/");
            log.debug("test URL: " + url);
            testHTTPS(url);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
    }

    // Note: this test requies some custom setup: an http server running on
    // localhost with an invalid (e.g. self-signed) server certificate for
    // SSL (https) and SSL config requiring a client certificate to get the
    // root document - this is only here to test the BasicX509TrustManager
    // local work-around for developers

    //@Test
    public void testInvalidServerHTTPS() throws Exception
    {
        InetAddress localhost = InetAddress.getLocalHost();
        String hostname = localhost.getCanonicalHostName();
        URL url = new URL("https://" + hostname + "/");

        try
        {
            log.debug("test URL: " + url);
            testHTTPS(url);
            Assert.fail("expected an SSLHandshakeException but did not fail");
        }
        catch (SSLHandshakeException expected)
        {
            log.debug("caught expected exception: " + expected);
        }

        System.setProperty(BasicX509TrustManager.class.getName() + ".trust", "true");
        try
        {
            log.debug("test URL: " + url);
            testHTTPS(url);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
    }

}
