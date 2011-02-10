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

import ca.nrc.cadc.auth.RunnableAction;
import ca.nrc.cadc.auth.SSLUtil;
import ca.nrc.cadc.util.FileUtil;
import ca.nrc.cadc.util.Log4jInit;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
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
public class HttpDownloadTest 
{
    private static Logger log = Logger.getLogger(HttpDownloadTest.class);
    private static String TEST_CERT_FN = "proxy.crt";
    private static String TEST_KEY_FN = "proxy.key";
    private static File SSL_CERT;
    private static File SSL_KEY;

    private URL httpURL;
    private URL httpsURL;
    private File tmpDir;
    
    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
        Log4jInit.setLevel("ca.nrc.cadc.net", Level.INFO);
        SSL_CERT = FileUtil.getFileFromResource(TEST_CERT_FN, HttpDownloadTest.class);
        SSL_KEY = FileUtil.getFileFromResource(TEST_KEY_FN, HttpDownloadTest.class);
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
        this.httpURL = new URL("http://www.cadc-ccda.hia-iha.nrc-cnrc.gc.ca/robots.txt");
        this.httpsURL = new URL("https://www.cadc-ccda.hia-iha.nrc-cnrc.gc.ca/robots.txt");
        this.tmpDir = new File(System.getProperty("user.dir"));
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
        log.debug("TEST: testNullArgs");
        URL src = null;
        File dest = tmpDir;
        try
        {
            HttpDownload dl = new HttpDownload(src, dest);
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
        log.debug("TEST: testNullArgs");
        URL src = httpURL;
        File dest = null;
        try
        {
            HttpDownload dl = new HttpDownload(src, dest);
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
    public void testDownloadFileToFile() throws Exception
    {
        log.debug("TEST: testDownloadFileToFile");
        URL src = httpURL;
        File dest = new File(tmpDir, "robots.txt");
        try
        {
            if (dest.exists())
                dest.delete();
            Assert.assertTrue("dest file does not exist before download", !dest.exists());
            HttpDownload dl = new HttpDownload(src, dest);
            dl.setOverwrite(false);
            dl.run();
            File out = dl.getFile();
            Assert.assertNotNull("result file", out);
            Assert.assertTrue("dest file exists after download", out.exists());
            Assert.assertTrue("dest file size > 0", out.length() > 0);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
    }

    @Test
    public void testDownloadFileToDir() throws Exception
    {
        log.debug("TEST: testDownloadFileToDir");
        URL src = httpURL;
        File dest = new File(System.getProperty("user.dir"));
        try
        {
            HttpDownload dl = new HttpDownload(src, dest);
            dl.setOverwrite(true);
            dl.run();
            File out = dl.getFile();
            Assert.assertNotNull("result file", out);
            Assert.assertTrue("dest file exists after download", out.exists());
            Assert.assertTrue("dest file size > 0", out.length() > 0);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
    }

    @Test
    public void testDownloadFileWithCreateDir() throws Exception
    {
        log.debug("TEST: testDownloadFileWithCreateDir");
        URL src = httpURL;
        File base = new File("/tmp/"+HttpDownloadTest.class.getSimpleName());
        File dest = new File(base, "bar/baz/robots.txt");
        try
        {
            File tmp = dest;
            for (int i=0; i<4; i++)
            {
                File par = tmp.getParentFile();
                if (tmp.exists())
                    tmp.delete();
                tmp = par;
            }
            Assert.assertTrue("base dir does not exist before download", !base.exists());
            HttpDownload dl = new HttpDownload(src, dest);
            dl.setOverwrite(true);
            dl.run();
            File out = dl.getFile();
            Assert.assertNotNull("result file", out);
            Assert.assertTrue("dest file exists after download", out.exists());
            Assert.assertTrue("dest file size > 0", out.length() > 0);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
    }

    @Test
    public void testDownloadFileToRelativeFile() throws Exception
    {
        log.debug("TEST: testDownloadFileToRelativeFile");
        URL src = httpURL;
        File dest = new File("robots.txt");
        try
        {
            // relative fails if file does not exist
            if (dest.exists())
                dest.delete();
            Assert.assertTrue("dest file does not exist before download", !dest.exists());
            HttpDownload dl = new HttpDownload(src, dest);
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
    public void testDownloadFileToRelativeDir() throws Exception
    {
        log.debug("TEST: testDownloadFileToRelativeDir");
        URL src = httpURL;
        File dest = new File("build/tmp");
        try
        {
            HttpDownload dl = new HttpDownload(src, dest);
            dl.setOverwrite(true);
            dl.run();
            File out = dl.getFile();
            Assert.assertNotNull("result file", out);
            Assert.assertTrue("dest file exists after download", out.exists());
            Assert.assertTrue("dest file size > 0", out.length() > 0);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
    }

    @Test
    public void testDownloadToOutputStream() throws Exception
    {
        log.debug("TEST: testDownloadToOutputStream");
        
        try
        {
            URL src = httpURL;
            ByteArrayOutputStream dest = new ByteArrayOutputStream(8192);
            HttpDownload dl = new HttpDownload(src, dest);
            dl.run();
            dest.close();
            byte[] out = dest.toByteArray();
            Assert.assertNull(dl.getFile());
            Assert.assertNotNull("dest stream after download", out);
            Assert.assertTrue("size > 0", out.length > 0);
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testDownloadHTTPS() throws Exception
    {
        log.debug("TEST: testDownloadHTTPS");
        URL src = httpsURL;
        File dest = new File(tmpDir, "robots.txt");
        try
        {
            if (dest.exists())
                dest.delete();
            Assert.assertTrue("dest file does not exist before download", !dest.exists());
            HttpDownload dl = new HttpDownload(src, dest);
            dl.setOverwrite(false);

            Subject s = SSLUtil.createSubject(SSL_CERT, SSL_KEY);
            Subject.doAs(s, new RunnableAction(dl));

            Assert.assertNull("HttpDownload failed: " + dl.getThrowable(), dl.getThrowable());

            File out = dl.getFile();
            Assert.assertNotNull("result file", out);
            Assert.assertTrue("dest file exists after download", out.exists());
            Assert.assertTrue("dest file size > 0", out.length() > 0);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
    }
}
