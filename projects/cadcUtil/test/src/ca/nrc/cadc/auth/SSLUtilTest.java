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
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.util.Calendar;
import java.util.GregorianCalendar;

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

    private static final String KEY_512 =
        "-----BEGIN RSA PRIVATE KEY-----\n" +
        "MIIBOgIBAAJBAOvm3yk/tr7/8ZaT584T54tOviYIpoWWRfwDgd176c0kTfTj43+C\n" +
        "BgxFcequf5mY51mgD7v38krRA3+xXi/igfsCAwEAAQJBAMqVrQXGcpDaScVPZV1j\n" +
        "WJAY4lDVUvQb1iQTev4SwPjqUy8H/f0Zt+Bezwf1LaxcHcCFA6QnDxHw6l99/5zw\n" +
        "p7kCIQD+4rfjcZyYUKwF0C2deKEgvZUjpiLYVyh/G4qKfT2sPwIhAOzu598CHLLn\n" +
        "LSZoBRJtjuhAr1zUrfkoBsNHQwTKi6tFAiBOpKtyXPKhOHrrTEFWzgqBLJ2gozkr\n" +
        "ITFYjqnfcycdRwIgbMW1L31hvYRCBxrEEVS4wclIeJ6vC+6jRC1ICEAQZN0CIFe+\n" +
        "Az22zN/URBRVBK32tI2axHy/j80Asysh+hxalp1F\n" +
        "-----END RSA PRIVATE KEY-----\n";
    private static final String KEY_1024 =
        "-----BEGIN RSA PRIVATE KEY-----\n" +
        "MIICXQIBAAKBgQDIcNIXiBAPuOjTNWJUbsI+TR1FSXNGM2bv2naU7pKQ0OWI+DDs\n" +
        "K1xlctgTi5WrfsMjPKoM+0zpVT5qjUrHyFatbTu9tYjLblPmi/yzOEOIloqZ8lF1\n" +
        "hrkUG98f8IBbgx4BbkXscVKdP9awngEpIYrZt3QXLUwUP+oF2PCGH5f+nwIDAQAB\n" +
        "AoGBAI5gVVuRspb4aaldSjNfWWqXrCsDOXasHHpTW9f+fu2O9PyOD3Iyerc1FHcN\n" +
        "t4rRyBrHhKMj/kXf3y4gnvW6QJY8MM+lHx7oubS8O5aqVexKa8dawQNHvMfLz9PU\n" +
        "OuN7X2+rvLS3+qPUtL2LiklCSsrr137M4OBNdfTcZKqAEaLBAkEA/QWsVzhx526D\n" +
        "HCBaJ6cgfo6Ravqjg17DDe/yt5iC+dQzGMozWJdHjOS/066aZNF4Els4iSVWdIT5\n" +
        "KqBmgjBFZQJBAMrMub59uqqHFGQzWgtOdQamiyeEwr48+a3xHUYy5p+1h7TWetHR\n" +
        "OQTFOwGfpv8h7RGd2TS3fxzK+G5LKIUChbMCQQDF+rpvROtbe017pJTmkg8K9+Mx\n" +
        "IgzvriZRsX7pyZwyf6e7rfufRj/mLtcqe2SznnOlaVtDdMPBSIrun7OWCs9BAkB6\n" +
        "V2b2dALYPQUgLZp0l7AhgvcPsBeLjF1TgdGXN73JO0nS3lDZos4zAojGQfoMj/rk\n" +
        "VcVi+A/G3utgHhcjppHhAkBpxQmU1fAB2wKVNtTh2puPmKt+g1wob1yZASRJEadZ\n" +
        "5QR7EgNAJtdlouvJcdnTXHJS9JazpcS3061+u2TfgIvx\n" +
        "-----END RSA PRIVATE KEY-----\n";
    private static final String KEY_2048 = 
        "-----BEGIN RSA PRIVATE KEY-----\n" +
        "MIIEpgIBAAKCAQEA4Sa+glBzaztCbgZ90uFm5sfjSWUtZCGTw1UwcIFsQtkBa1ut\n" +
        "gn1b2LtGevctPoXMreMq4f/5TNAtJuBOHP+2Xv+mIBBbm+zJwTAfk9I0/6wcNlkS\n" +
        "FKhSqkpvwSv6+ecQ6R5zUrQv1aMwI392GyAiY8Jo4J6UVGKe7+YY1yvWraVFZYzB\n" +
        "FeCNb9Qlo4X+uyZRjz0JJmJZE8H6USmrAa1DPQoWRBpJPJ/sIM1ejTz6lLyC3A54\n" +
        "e0Nh+z8dPfUVzBySOgPzypPbuyEaVEFlmm+PqyrfoSIgNQNeOY0SbyhN374xpSHz\n" +
        "PBPUPgy5qwidQB4+XN6YQlumz/i4+UnPdvR8jwIDAQABAoIBAQCmGGn0QptS4O2Z\n" +
        "s0pBNq0t1QoUTAKXWrniIMdSR/fwvJvycjhnCkmmckmFTzFebWBYazxoauijxPN6\n" +
        "OYEGnZIRNPF9t/OM7LrNvM2exDT65CIP6dePy7joDW+yBtroXpC4GRGkUm7zYKaT\n" +
        "mWUsj6EvDO1Hv1TXh8WOXqW2no2JnP7OgCcVCGSR4/o6vBaDlUUBSiA3HIG7LWwW\n" +
        "uin3LdZ8Z6CuwwlSdc758LIRlE5cGwU4Q4GH34KsMHJU0xkTaeSoYWFWo9xBTlQ1\n" +
        "KuLgrYHeomKItiQTeXmuD8hepqhBkrH7tgfPcDrwmOlgarrJ1YK5Ve+i2nyHWVlP\n" +
        "/g8DyrgxAoGBAP+08fzjxht5P7wW1E3QY+79NG5vO9uc+joG5X2U0Yu/98UWa1P5\n" +
        "d6cbHQQuML0g7MfgRK52h80x+0xPcNewOmCX8vkYLpOqW++UOxH0Czam6igxeprV\n" +
        "5WIYg5RB1QhbhQ5rRc4O+l61Yi9hdQBnJNfFbSqU373JLZpD6T5wdCXnAoGBAOFo\n" +
        "1I8PEZaxiNz3qGb5LHEeSHRqQHKBQMoUcjAThA8T/Dx7uo3EkrKptyxJdpNeJbnE\n" +
        "/GJsjXrElAHJqST1wmQzC3KLhgmKZliB4Ewk1foIfid36T0w/+RrlW2kwqinkr+H\n" +
        "UOCSKnsQE8+xeGZ68WxmrBDmGnf5AtS9dfa6cc8ZAoGBAItxhoFNSSyUS3Br1qz0\n" +
        "lnqutBgBKthRW5enSSDZtggK0Lg2yKLLqTeErqcn9UY+HUHGiE3Hr7jzp8HulG/a\n" +
        "14rzcfnq+QNn5Kja4fehaTgNgCYZDW5AdM2w5phD6kObfQzm7PM48coSChAiimaE\n" +
        "2O+d5zFQbE8X1XmJzTlSo9RDAoGBAKA/BXXasZdfCTyF+DuUgwq8C6hvbPe6edPv\n" +
        "6ynQhf6uJ5DcKUjl6aCIVQdwBpNHyCwkJYTXRVF09P+8XLpA2Pyg6U96b0TTFmVv\n" +
        "l4SqX1CMvxrR/YeaESFTdnznN9fsob/1tAKjBv5L9LmfokfAuWdmKocs/r4x0dhq\n" +
        "BLXt4EDpAoGBAKN61XJ+kwO6FkxuyTlbv458Bc9toFyPqSaafJeEp/p3KdLMlphr\n" +
        "0GzdeGNkrNfseVbSAjnlO2zmmhVe6Oz3oIR4d/5Hb8QEZi8f7nOZboufITyGTtYG\n" +
        "LfVRkN/AuTrxRxWQDbZOo55ACoJA3DH7/BMOXhf9RikjrvESLtCWzsf2\n" +
        "-----END RSA PRIVATE KEY-----\n";

    
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
    
    @Test
    public void testPrivateKeyParser() throws Exception
    {
        // tests the parser with different size keys
        // 512 bit
        byte[] privateKey = SSLUtil.getPrivateKey(KEY_512.getBytes());
        try
        {
            log.debug("test parsing of RSA 512 bit key: ");
            SSLUtil.parseKeySpec(privateKey);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
        
        // 1024 bit
        privateKey = SSLUtil.getPrivateKey(KEY_1024.getBytes());
        try
        {
            log.debug("test parsing of RSA 1024 bit key: ");
            SSLUtil.parseKeySpec(privateKey);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
        
        // 2048 bit
        privateKey = SSLUtil.getPrivateKey(KEY_2048.getBytes());
        try
        {
            log.debug("test parsing of RSA 2048 bit key: ");
            SSLUtil.parseKeySpec(privateKey);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
        
    }
    
    @Test
    public void testValidSubject() throws Exception
    {
        boolean thrown = false;
        
        // subject with no credentials
        Subject subject = new Subject();
        try
        {
            SSLUtil.validateSubject(subject, null);
        }
        catch(CertificateException ex)
        {
            thrown = true;
        }
        Assert.assertTrue("CertificateException expected", thrown);
        
        subject = SSLUtil.createSubject(SSL_CERT, SSL_KEY);
        
        // subject with valid credentials
        SSLUtil.validateSubject(subject, null);
        
        GregorianCalendar date = new GregorianCalendar();
        
        thrown = false;
        // Move the date way in the past so the certificate should
        // not be valid yet
        date.add(Calendar.YEAR, -15);
        try
        {
            SSLUtil.validateSubject(subject, date.getTime());
        }
        catch(CertificateNotYetValidException ex)
        {
            thrown = true;
        }
        Assert.assertTrue("CertificateNotYetValidException expected", thrown);
        
        thrown = false;
        // Move the date way in the future so the certificate should
        // not be valid anymore (double the number of years we moved
        // back in the previous step
        date.add(Calendar.YEAR, 30);
        try
        {
            SSLUtil.validateSubject(subject, date.getTime());
        }
        catch(CertificateExpiredException ex)
        {
            thrown = true;
        }
        Assert.assertTrue("CertificateExpiredException expected", thrown);
    }

}
