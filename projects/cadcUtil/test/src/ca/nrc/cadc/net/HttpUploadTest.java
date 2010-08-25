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

import ca.nrc.cadc.auth.BasicX509TrustManager;
import ca.nrc.cadc.auth.RunnableAction;
import ca.nrc.cadc.auth.SSLUtil;
import ca.nrc.cadc.util.FileUtil;
import ca.nrc.cadc.util.Log4jInit;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.Random;
import javax.security.auth.Subject;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
/**
 *
 * @author pdowler
 */
public class HttpUploadTest 
{
    private static Logger log = Logger.getLogger(HttpUploadTest.class);
    private static String TEST_CERT_FN = "proxy.crt";
    private static String TEST_KEY_FN = "proxy.key";
    private static File SSL_CERT;
    private static File SSL_KEY;

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
        SSL_CERT = FileUtil.getFileFromResource(TEST_CERT_FN, HttpUploadTest.class);
        SSL_KEY = FileUtil.getFileFromResource(TEST_KEY_FN, HttpUploadTest.class);
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
        this.srcFile = File.createTempFile("public"+HttpUploadTest.class.getSimpleName(), ".in");
        Random rnd = new Random();
        FileOutputStream ostream = new FileOutputStream(srcFile);
        origBytes = new byte[1024*1024];
        rnd.nextBytes(origBytes);
        ostream.write(origBytes);
        ostream.close();
        srcFile.deleteOnExit();
        boolean local = true;

        InetAddress localhost = InetAddress.getLocalHost();
        String hostname = localhost.getCanonicalHostName();
        this.httpsURL = new URL("https://"+hostname+"/data/pub/TEST/"+srcFile.getName());
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception
    {
    }

    @Test
    public void testNullSrc() throws Exception
    {
        log.debug("TEST: testNullSrc");
        File src = null;
        URL dest = httpsURL;
        try
        {
            HttpUpload dl = new HttpUpload(src, dest);
            Assert.fail("expected IllegalArgumentException");
        }
        catch(IllegalArgumentException expected)
        {
            log.debug("caught expected: " + expected);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
    }

    @Test
    public void testNullDest() throws Exception
    {
        log.debug("TEST: testNullDest");
        File src = srcFile;
        URL dest = null;
        try
        {
            HttpUpload dl = new HttpUpload(src, dest);
            Assert.fail("expected IllegalArgumentException");
        }
        catch(IllegalArgumentException expected)
        {
            log.debug("caught expected: " + expected);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
    }

    @Test
    public void testUploadFileHTTPS() throws Exception
    {
        log.debug("TEST: testUploadHTTPS");
        URL dest = httpsURL;
        File src = srcFile;
        File tmp = null;
        String contentType = "application/octet-stream";
        try
        {
            Subject s = SSLUtil.createSubject(SSL_CERT, SSL_KEY);
            
            HttpUpload up = new HttpUpload(src, dest);
            up.setContentType(contentType);

            Subject.doAs(s, new RunnableAction(up));
            if (up.getThrowable() != null)
                log.error("run failed", up.getThrowable());
            Assert.assertNull("upload failure", up.getThrowable());

            URL check = dest;

            //URL check = new URL("http", dest.getHost(), dest.getPath());
            tmp = File.createTempFile("public"+HttpUploadTest.class.getSimpleName(), ".out");

            HttpDownload down = new HttpDownload(HttpUploadTest.class.getSimpleName(), check, tmp);
            down.setOverwrite(true);
            
            Subject.doAs(s, new RunnableAction(down));
            Assert.assertNull("download failure", down.getThrowable());

            Assert.assertEquals("content-length header", src.length(), down.getContentLength());

            // this really tests server-side functionality, so disable it for now
            //Assert.assertEquals("content-type header", contentType, down.getContentType());

            File out = down.getFile();
            Assert.assertNotNull("result file", out);
            Assert.assertEquals("file sizes", srcFile.length(), out.length());

            byte[] resultBytes = FileUtil.readFile(tmp);
            Assert.assertArrayEquals("bytes", origBytes, resultBytes);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
        finally
        {
            if (tmp != null && tmp.exists())
                tmp.delete();
        }
    }

    @Test
    public void testUploadStreamHTTPS() throws Exception
    {
        log.debug("TEST: testUploadHTTPS");
        URL dest = httpsURL;
        File src = srcFile;
        File tmp = null;
        String contentType = "application/octet-stream";
        try
        {
            Subject s = SSLUtil.createSubject(SSL_CERT, SSL_KEY);
            
            HttpUpload up = new HttpUpload(new FileInputStream(src), dest);
            up.setContentType(contentType);

            Subject.doAs(s, new RunnableAction(up));
            if (up.getThrowable() != null)
                log.error("run failed", up.getThrowable());
            Assert.assertNull("upload failure", up.getThrowable());

            //URL check = new URL("http", dest.getHost(), dest.getPath());
            URL check = dest;

            tmp = File.createTempFile("public"+HttpUploadTest.class.getSimpleName(), ".out");

            HttpDownload down = new HttpDownload(HttpUploadTest.class.getSimpleName(), check, tmp);
            down.setOverwrite(true);
            
            Subject.doAs(s, new RunnableAction(down));
            Assert.assertNull("download failure", down.getThrowable());

            Assert.assertEquals("content-length header", src.length(), down.getContentLength());
            
            // this really tests server-side functionality, so disable it for now
            //Assert.assertEquals("content-type header", contentType, down.getContentType());

            File out = down.getFile();
            Assert.assertNotNull("result file", out);
            Assert.assertEquals("file sizes", srcFile.length(), out.length());

            byte[] resultBytes = FileUtil.readFile(tmp);
            Assert.assertArrayEquals("bytes", origBytes, resultBytes);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
        finally
        {
            if (tmp != null && tmp.exists())
                tmp.delete();
        }
    }
}
