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
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.auth.BasicX509TrustManager;
import ca.nrc.cadc.util.FileUtil;
import ca.nrc.cadc.util.Log4jInit;

/**
 * Unit tests for class HttpPost.
 */
public class HttpPostTest
{

    private static Logger log = Logger.getLogger(HttpPostTest.class);
    private static String TEST_CERT_FN = "proxy.crt";
    private static String TEST_KEY_FN = "proxy.key";
    private static File SSL_CERT;
    private static File SSL_KEY;

    private URL brokenHttpURL;
    private URL httpsURL;
    private File srcFile;
    private byte[] origBytes;

    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
        Log4jInit.setLevel("ca.nrc.cadc.net", Level.INFO);
        SSL_CERT = FileUtil.getFileFromResource(TEST_CERT_FN, HttpPostTest.class);
        SSL_KEY = FileUtil.getFileFromResource(TEST_KEY_FN, HttpPostTest.class);
        System.setProperty(BasicX509TrustManager.class.getName() + ".trust", "true");
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
        this.srcFile = File.createTempFile("public"+HttpPostTest.class.getSimpleName(), ".post");
        Random rnd = new Random();
        FileOutputStream ostream = new FileOutputStream(srcFile);
        origBytes = new byte[32*1024];
        rnd.nextBytes(origBytes);
        ostream.write(origBytes);
        ostream.close();
        srcFile.deleteOnExit();
        boolean local = true;

        InetAddress localhost = InetAddress.getLocalHost();
        String hostname = localhost.getCanonicalHostName();
        this.httpsURL = new URL("https://"+hostname+"/data/pub/TEST/"+srcFile.getName());
        this.brokenHttpURL = new URL("http://"+hostname+"/data/pub/NoSuchThing/"+srcFile.getName());
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception
    {
    }

    @Test
    public void testNullURL1() throws Exception
    {
        log.debug("TEST: testNullURL1");
        URL url = null;
        Map<String, Object> map = new HashMap<String, Object>(); 
        map.put("key", new Object());
        OutputStream os = new ByteArrayOutputStream();
        try
        {
            new HttpPost(url, map, os);
            Assert.fail("expected IllegalArgumentException");
        }
        catch(IllegalArgumentException expected)
        {
            log.debug("caught expected: " + expected);
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
    @Test
    public void testNullURL2() throws Exception
    {
        log.debug("TEST: testNullURL2");
        URL url = null;
        Map<String, Object> map = new HashMap<String, Object>(); 
        map.put("key", new Object());
        try
        {
            new HttpPost(url, map, false);
            Assert.fail("expected IllegalArgumentException");
        }
        catch(IllegalArgumentException expected)
        {
            log.debug("caught expected: " + expected);
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
    @Test
    public void testNullURL3() throws Exception
    {
        log.debug("TEST: testNullURL3");
        URL url = null;
        try
        {
            new HttpPost(url, "content", "contentType", false);
            Assert.fail("expected IllegalArgumentException");
        }
        catch(IllegalArgumentException expected)
        {
            log.debug("caught expected: " + expected);
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
}
