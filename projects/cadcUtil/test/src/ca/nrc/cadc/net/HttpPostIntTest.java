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

package ca.nrc.cadc.net;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.security.PrivilegedExceptionAction;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.Subject;

import junit.framework.Assert;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Test;

import ca.nrc.cadc.auth.BasicX509TrustManager;
import ca.nrc.cadc.auth.SSLUtil;
import ca.nrc.cadc.util.FileUtil;
import ca.nrc.cadc.util.Log4jInit;

/**
 * Integration tests for HttpPost.  These tests use the TestServlet
 * available in project cadcTestServlet.
 * 
 * @author majorb
 *
 */
public class HttpPostIntTest
{
    private static Logger log = Logger.getLogger(HttpPostIntTest.class);
    private static String TEST_CERT_FN = "proxy.crt";
    private static String TEST_KEY_FN = "proxy.key";
    private static File SSL_CERT;
    private static File SSL_KEY;
    
    String baseHttpUrl;
    String baseHttpsUrl;
    
    public HttpPostIntTest()
    {
        Log4jInit.setLevel("ca.nrc.cadc.net", Level.DEBUG);
        String hostname = NetUtil.getServerName(HttpPostIntTest.class);
        baseHttpUrl = "http://" + hostname + "/test";
        baseHttpsUrl = "https://" + hostname + "/test";
        SSL_CERT = FileUtil.getFileFromResource(TEST_CERT_FN, HttpDownloadTest.class);
        SSL_KEY = FileUtil.getFileFromResource(TEST_KEY_FN, HttpDownloadTest.class);
        System.setProperty(BasicX509TrustManager.class.getName() + ".trust", "true");
    }
    
    @Test
    public void testMapPostWithOutputStream() throws Exception
    {
        Subject s = SSLUtil.createSubject(SSL_CERT, SSL_KEY);
        PrivilegedExceptionAction<Object> p = new PrivilegedExceptionAction<Object>()
        {
            @Override
            public Object run() throws Exception
            {
                testMapPostWithOutputStream(baseHttpUrl);
                testMapPostWithOutputStream(baseHttpsUrl);
                return null;
            }
        };
        Subject.doAs(s, p);
    }
    
    @Test
    public void testNonRedirectingMapPost() throws Exception
    {
        Subject s = SSLUtil.createSubject(SSL_CERT, SSL_KEY);
        PrivilegedExceptionAction<Object> p = new PrivilegedExceptionAction<Object>()
        {
            @Override
            public Object run() throws Exception
            {
                testNonRedirectingMapPost(baseHttpUrl);
                testNonRedirectingMapPost(baseHttpsUrl);
                return null;
            }
        };
        Subject.doAs(s, p);
    }
    
    @Test
    public void testRedirectingMapPost() throws Exception
    {
        Subject s = SSLUtil.createSubject(SSL_CERT, SSL_KEY);
        PrivilegedExceptionAction<Object> p = new PrivilegedExceptionAction<Object>()
        {
            @Override
            public Object run() throws Exception
            {
                testRedirectingMapPost(baseHttpUrl);
                testRedirectingMapPost(baseHttpsUrl);
                return null;
            }
        };
        Subject.doAs(s, p);
    }
    
    @Test
    public void testNonRedirectingStringPost() throws Exception
    {
        Subject s = SSLUtil.createSubject(SSL_CERT, SSL_KEY);
        PrivilegedExceptionAction<Object> p = new PrivilegedExceptionAction<Object>()
        {
            @Override
            public Object run() throws Exception
            {
                testNonRedirectingStringPost(baseHttpUrl);
                testNonRedirectingStringPost(baseHttpsUrl);
                return null;
            }
        };
        Subject.doAs(s, p);
    }
    
    @Test
    public void testRedirectingStringPost() throws Exception
    {
        Subject s = SSLUtil.createSubject(SSL_CERT, SSL_KEY);
        PrivilegedExceptionAction<Object> p = new PrivilegedExceptionAction<Object>()
        {
            @Override
            public Object run() throws Exception
            {
                testRedirectingStringPost(baseHttpUrl);
                testRedirectingStringPost(baseHttpsUrl);
                return null;
            }
        };
        Subject.doAs(s, p);
    }
    
    private void testMapPostWithOutputStream(String url) throws Exception
    {
        Map<String, Object> params = new HashMap<String, Object>();
        Long longValue = new Long(3);
        Date dateValue = new Date();
        params.put("stringObject", "stringValue");
        params.put("longObject", longValue);
        params.put("dateObject", dateValue);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        HttpPost httpPost = new HttpPost(new URL(url + "?redirect=false"), params, outputStream);
        httpPost.setMaxRetries(4);
        httpPost.run();
        log.debug("throwable: " + httpPost.getThrowable());
        Assert.assertNull("Wrong throwable", httpPost.getThrowable());
        Assert.assertNull("Wrong redirect URL", httpPost.getRedirectURL());
        Assert.assertEquals("Wrong number of retries.", 2, httpPost.getRetriesPerformed());
        String response = outputStream.toString("UTF-8");
        String[] pairs = response.split("&");
        boolean stringCorrect = false;
        boolean longCorrect = false;
        boolean dateCorrect = false;
        for (String pair : pairs)
        {
            String[] keyValue = pair.split("=");
            if (keyValue[0].equals("stringObject"))
            {
                stringCorrect = keyValue[1].equals("stringValue");
            }
            if (keyValue[0].equals("longObject"))
            {
                longCorrect = keyValue[1].equals(longValue.toString());
            }
            if (keyValue[0].equals("dateObject"))
            {
                dateCorrect = keyValue[1].equals(dateValue.toString());
            }
        }
        Assert.assertTrue("wrong string content", stringCorrect);
        Assert.assertTrue("wrong long content", longCorrect);
        Assert.assertTrue("wrong date content", dateCorrect);
        
        
        // same test but with the test servlet redirecting
        outputStream = new ByteArrayOutputStream();
        httpPost = new HttpPost(new URL(url + "?redirect=true"), params, outputStream);
        httpPost.setMaxRetries(4);
        httpPost.run();
        log.debug("throwable: " + httpPost.getThrowable());
        Assert.assertNull("Wrong throwable", httpPost.getThrowable());
        Assert.assertNull("Wrong redirect URL", httpPost.getRedirectURL());
        Assert.assertEquals("Wrong number of retries.", 2, httpPost.getRetriesPerformed());
        response = outputStream.toString("UTF-8");
        pairs = response.split("&");
        stringCorrect = false;
        longCorrect = false;
        dateCorrect = false;
        for (String pair : pairs)
        {
            String[] keyValue = pair.split("=");
            if (keyValue[0].equals("stringObject"))
            {
                stringCorrect = keyValue[1].equals("stringValue");
            }
            if (keyValue[0].equals("longObject"))
            {
                longCorrect = keyValue[1].equals(longValue.toString());
            }
            if (keyValue[0].equals("dateObject"))
            {
                dateCorrect = keyValue[1].equals(dateValue.toString());
            }
        }
        Assert.assertTrue("wrong string content", stringCorrect);
        Assert.assertTrue("wrong long content", longCorrect);
        Assert.assertTrue("wrong date content", dateCorrect);
    }
    
    private void testNonRedirectingMapPost(String url) throws Exception
    {
        Map<String, Object> params = new HashMap<String, Object>();
        Long longValue = new Long(3);
        Date dateValue = new Date();
        params.put("stringObject", "stringValue");
        params.put("longObject", longValue);
        params.put("dateObject", dateValue);
        HttpPost httpPost = new HttpPost(new URL(url + "?redirect=false"), params, false);
        httpPost.setMaxRetries(4);
        httpPost.run();
        log.debug("throwable: " + httpPost.getThrowable());
        Assert.assertNull("Wrong throwable", httpPost.getThrowable());
        Assert.assertNull("Wrong redirect URL", httpPost.getRedirectURL());
        Assert.assertEquals("Wrong number of retries.", 2, httpPost.getRetriesPerformed());
        String response = httpPost.getResponseBody();
        String[] pairs = response.split("&");
        boolean stringCorrect = false;
        boolean longCorrect = false;
        boolean dateCorrect = false;
        for (String pair : pairs)
        {
            String[] keyValue = pair.split("=");
            if (keyValue[0].equals("stringObject"))
            {
                stringCorrect = keyValue[1].equals("stringValue");
            }
            if (keyValue[0].equals("longObject"))
            {
                longCorrect = keyValue[1].equals(longValue.toString());
            }
            if (keyValue[0].equals("dateObject"))
            {
                dateCorrect = keyValue[1].equals(dateValue.toString());
            }
        }
        Assert.assertTrue("wrong string content", stringCorrect);
        Assert.assertTrue("wrong long content", longCorrect);
        Assert.assertTrue("wrong date content", dateCorrect);
        
        
        // same test but with the test servlet redirecting
        httpPost = new HttpPost(new URL(url + "?redirect=true"), params, false);
        httpPost.setMaxRetries(4);
        httpPost.run();
        log.debug("throwable: " + httpPost.getThrowable());
        Assert.assertNull("Wrong throwable", httpPost.getThrowable());
        Assert.assertEquals("Wrong redirect URL", url + "?post=true", httpPost.getRedirectURL().toString());
        Assert.assertEquals("Wrong number of retries.", 2, httpPost.getRetriesPerformed());
        Assert.assertNull("Wrong response", httpPost.getResponseBody());
    }
    
    private void testRedirectingMapPost(String url) throws Exception
    {
        Map<String, Object> params = new HashMap<String, Object>();
        Long longValue = new Long(3);
        Date dateValue = new Date();
        params.put("stringObject", "stringValue");
        params.put("longObject", longValue);
        params.put("dateObject", dateValue);
        HttpPost httpPost = new HttpPost(new URL(url + "?redirect=false"), params, true);
        httpPost.setMaxRetries(4);
        httpPost.run();
        log.debug("throwable: " + httpPost.getThrowable());
        Assert.assertNull("Wrong throwable", httpPost.getThrowable());
        Assert.assertNull("Wrong redirect URL", httpPost.getRedirectURL());
        Assert.assertEquals("Wrong number of retries.", 2, httpPost.getRetriesPerformed());
        String response = httpPost.getResponseBody();
        String[] pairs = response.split("&");
        boolean stringCorrect = false;
        boolean longCorrect = false;
        boolean dateCorrect = false;
        for (String pair : pairs)
        {
            String[] keyValue = pair.split("=");
            if (keyValue[0].equals("stringObject"))
            {
                stringCorrect = keyValue[1].equals("stringValue");
            }
            if (keyValue[0].equals("longObject"))
            {
                longCorrect = keyValue[1].equals(longValue.toString());
            }
            if (keyValue[0].equals("dateObject"))
            {
                dateCorrect = keyValue[1].equals(dateValue.toString());
            }
        }
        Assert.assertTrue("wrong string content", stringCorrect);
        Assert.assertTrue("wrong long content", longCorrect);
        Assert.assertTrue("wrong date content", dateCorrect);
        
        
        // same test but with the test servlet redirecting
        httpPost = new HttpPost(new URL(url + "?redirect=true"), params, true);
        httpPost.setMaxRetries(4);
        httpPost.run();
        log.debug("throwable: " + httpPost.getThrowable());
        Assert.assertNull("Wrong throwable", httpPost.getThrowable());
        Assert.assertNull("Wrong redirect URL", httpPost.getRedirectURL());
        Assert.assertEquals("Wrong number of retries.", 2, httpPost.getRetriesPerformed());
        response = httpPost.getResponseBody();
        pairs = response.split("&");
        stringCorrect = false;
        longCorrect = false;
        dateCorrect = false;
        for (String pair : pairs)
        {
            String[] keyValue = pair.split("=");
            if (keyValue[0].equals("stringObject"))
            {
                stringCorrect = keyValue[1].equals("stringValue");
            }
            if (keyValue[0].equals("longObject"))
            {
                longCorrect = keyValue[1].equals(longValue.toString());
            }
            if (keyValue[0].equals("dateObject"))
            {
                dateCorrect = keyValue[1].equals(dateValue.toString());
            }
        }
        Assert.assertTrue("wrong string content", stringCorrect);
        Assert.assertTrue("wrong long content", longCorrect);
        Assert.assertTrue("wrong date content", dateCorrect);
    }
    
    private void testNonRedirectingStringPost(String url) throws Exception
    {
        // test a non-redirecting servlet
        String content = "postContent";
        String contentType = "text/xml";
        HttpPost httpPost = new HttpPost(new URL(url + "?redirect=false"), content, contentType, false);
        httpPost.setMaxRetries(4);
        httpPost.run();
        log.debug("throwable: " + httpPost.getThrowable());
        Assert.assertNull("Wrong throwable", httpPost.getThrowable());
        Assert.assertEquals("Wrong size", "postContent".length(), httpPost.getResponseBody().length());
        Assert.assertEquals("Wrong content", "postContent", httpPost.getResponseBody());
        Assert.assertNull("Wrong redirect URL", httpPost.getRedirectURL());
        Assert.assertEquals("Wrong number of retries.", 2, httpPost.getRetriesPerformed());
        
        // same test but with the test servlet redirecting
        httpPost = new HttpPost(new URL(url + "?redirect=true"), content, contentType, false);
        httpPost.setMaxRetries(4);
        httpPost.run();
        Assert.assertNull("Wrong content", httpPost.getResponseBody());
        Assert.assertEquals("Wrong redirect URL size", (url + "?post=true").length(), httpPost.getRedirectURL().toString().length());
        Assert.assertEquals("Wrong redirect URL", url + "?post=true", httpPost.getRedirectURL().toString());
        Assert.assertEquals("Wrong number of retries.", 2, httpPost.getRetriesPerformed());
        Assert.assertNull("Wrong throwable", httpPost.getThrowable());
    }
    
    private void testRedirectingStringPost(String url) throws Exception
    {
        // test a non-redirecting servlet
        String content = "postContent";
        String contentType = "text/xml";
        HttpPost httpPost = new HttpPost(new URL(url + "?redirect=false"), content, contentType, true);
        httpPost.setMaxRetries(4);
        httpPost.run();
        log.debug("throwable: " + httpPost.getThrowable());
        Assert.assertNull("Wrong throwable", httpPost.getThrowable());
        Assert.assertEquals("Wrong size", "postContent".length(), httpPost.getResponseBody().length());
        Assert.assertEquals("Wrong content", "postContent", httpPost.getResponseBody());
        Assert.assertNull("Wrong redirect URL", httpPost.getRedirectURL());
        Assert.assertEquals("Wrong number of retries.", 2, httpPost.getRetriesPerformed());
        
        // same test but with the test servlet redirecting
        httpPost = new HttpPost(new URL(url + "?redirect=true"), content, contentType, true);
        httpPost.setMaxRetries(4);
        httpPost.run();
        Assert.assertNull("Wrong throwable", httpPost.getThrowable());
        Assert.assertEquals("Wrong size", "postContent".length(), httpPost.getResponseBody().length());
        Assert.assertEquals("Wrong content", "postContent", httpPost.getResponseBody());
        Assert.assertNull("Wrong redirect URL", httpPost.getRedirectURL());
        Assert.assertEquals("Wrong number of retries.", 2, httpPost.getRetriesPerformed());
    }
    

}
