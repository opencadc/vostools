
package ca.nrc.cadc.dlm;

import ca.nrc.cadc.util.Log4jInit;
import java.net.URL;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author pdowler
 */
public class ManifestReaderTest 
{
    private static Logger log = Logger.getLogger(ManifestReaderTest.class);
    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.dlm", Level.INFO);
    }

    ManifestReader manifestReader = new ManifestReader();

    @Test
    public void testOK()
    {
        try
        {
            URL[] urls = new URL[]
            {
                new URL("http://www.example.com/"),
                new URL("http://www.cadc.hia.nrc.gc.ca/")
            };
            StringBuffer sb = new StringBuffer();
            for (URL u : urls)
            {
                sb.append("OK\t");
                sb.append(u.toString());
                sb.append("\tsomefile\n");
            }
            String manifest = sb.toString();

            Iterator<DownloadDescriptor> iter = manifestReader.read(manifest);
            int num = 0;
            while ( iter.hasNext() )
            {
                DownloadDescriptor dd = iter.next();
                
                log.debug("found: " + dd);
                Assert.assertEquals(DownloadDescriptor.OK, dd.status);
                Assert.assertEquals(urls[num], dd.url);
                Assert.assertEquals("somefile", dd.destination);
                num++;
            }
            Assert.assertEquals(2, num);

        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testERROR()
    {
        try
        {
            String[] msgs = new String[]
            {
                "permission denied: foo1/bar.png",
                "permission denied (failed to verify group membership): foo2/bar.png"
            };
            StringBuffer sb = new StringBuffer();
            for (String s : msgs)
            {
                sb.append("ERROR\t");
                sb.append(s);
                sb.append("\tsomefile\n");
            }
            String manifest = sb.toString();

            Iterator<DownloadDescriptor> iter = manifestReader.read(manifest);
            int num = 0;
            while ( iter.hasNext() )
            {
                DownloadDescriptor dd = iter.next();
                
                log.debug("found: " + dd);
                Assert.assertEquals(DownloadDescriptor.ERROR, dd.status);
                Assert.assertEquals(msgs[num], dd.error);
                Assert.assertEquals("somefile", dd.destination);
                num++;
            }
            Assert.assertEquals(2, num);

        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testNoDestination()
    {
        try
        {
            URL[] urls = new URL[]
            {
                new URL("http://www.example.com/"),
                new URL("http://www.cadc.hia.nrc.gc.ca/")
            };
            StringBuffer sb = new StringBuffer();
            for (URL u : urls)
            {
                sb.append("OK\t");
                sb.append(u.toString());
                sb.append("\n");
            }
            String manifest = sb.toString();

            Iterator<DownloadDescriptor> iter = manifestReader.read(manifest);
            int num = 0;
            while ( iter.hasNext() )
            {
                DownloadDescriptor dd = iter.next();

                log.debug("found: " + dd);
                Assert.assertEquals(DownloadDescriptor.OK, dd.status);
                Assert.assertEquals(urls[num], dd.url);
                Assert.assertNull(dd.destination);
                num++;
            }
            Assert.assertEquals(2, num);

        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testEmptyManifest()
    {
        try
        {
            StringBuffer sb = new StringBuffer();
            String manifest = sb.toString();

            Iterator<DownloadDescriptor> iter = manifestReader.read(manifest);

            long num = 0;
            Assert.assertFalse(iter.hasNext());
            iter.next(); // should throw
            Assert.fail("expected NoSuchElementException");
        }
        catch(NoSuchElementException expected)
        {
            log.debug("caught expected exception: " + expected);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testIllegalManifestEntry()
    {
        try
        {
            StringBuffer sb = new StringBuffer();
            sb.append("OK\thttp://www.example.com\n");
            sb.append("FOO\tillegal stuff\n");
            sb.append("ERROR\terror line\n");
            String manifest = sb.toString();

            Iterator<DownloadDescriptor> iter = manifestReader.read(manifest);
            DownloadDescriptor dd;

            dd = iter.next();
            Assert.assertEquals(DownloadDescriptor.OK, dd.status);
            dd = iter.next();
            Assert.assertEquals(DownloadDescriptor.ERROR, dd.status);
            Assert.assertTrue(dd.error.contains("FOO"));
            dd = iter.next();
            Assert.assertEquals(DownloadDescriptor.ERROR, dd.status);
        }
        catch(NoSuchElementException expected)
        {
            log.debug("caught expected exception: " + expected);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testSkipBlankLines()
    {
        try
        {
            URL exp = new URL("http://www.example.com");
            StringBuffer sb = new StringBuffer();
            sb.append("OK\t"+exp+"\n");
            sb.append("\n");
            sb.append("    \n");
            sb.append("OK\t"+exp+"\n");
            String manifest = sb.toString();

            Iterator<DownloadDescriptor> iter = manifestReader.read(manifest);
            DownloadDescriptor dd;

            dd = iter.next();
            Assert.assertEquals(DownloadDescriptor.OK, dd.status);
            Assert.assertEquals(exp, dd.url);
            dd = iter.next();
            Assert.assertEquals(DownloadDescriptor.OK, dd.status);
            Assert.assertEquals(exp, dd.url);
        }
        catch(NoSuchElementException expected)
        {
            log.debug("caught expected exception: " + expected);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
}
