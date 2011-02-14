
package ca.nrc.cadc.dlm;

import ca.nrc.cadc.util.Log4jInit;
import java.net.URI;
import java.util.Iterator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author pdowler
 */
public class IteratorTest 
{
    private static Logger log = Logger.getLogger(IteratorTest.class);
    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.dlm", Level.DEBUG);
    }

    @Test
    public void testIterateOK()
    {
        log.debug("testIterateOK");
        try
        {
            String[] uris = new String[]
            {
                "http://www.google.com",
                "ad:foo/bar"
            };

            Iterator<DownloadDescriptor> iter = DownloadUtil.iterateURLs(uris, null);
            long num = 0;
            while ( iter.hasNext() )
            {
                DownloadDescriptor dd = iter.next();
                num++;
                log.debug("found: " + dd);
                Assert.assertEquals(DownloadDescriptor.OK, dd.status);
                Assert.assertEquals("http", dd.url.getProtocol());
            }
            Assert.assertEquals(uris.length, num);
            
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testIterateDuplicates()
    {
        log.debug("testIterateDuplicates");
        try
        {
            String[] uris = new String[]
            {
                "http://www.google.com",
                "http://www.google.com"
            };

            Iterator<DownloadDescriptor> iter = DownloadUtil.iterateURLs(uris, null);
            long num = 0;
            while ( iter.hasNext() )
            {
                DownloadDescriptor dd = iter.next();
                num++;
                log.debug("found: " + dd);
                Assert.assertEquals(DownloadDescriptor.OK, dd.status);
                Assert.assertEquals("http", dd.url.getProtocol());
            }
            Assert.assertEquals(2, num);
            Assert.assertFalse(iter.hasNext());

            // now test with remvoeDuplicates==true
            iter = DownloadUtil.iterateURLs(uris, null, true);
            DownloadDescriptor dd = iter.next();
            log.debug("found: " + dd);
            Assert.assertEquals(DownloadDescriptor.OK, dd.status);
            Assert.assertEquals("http", dd.url.getProtocol());

            dd = iter.next();
            log.debug("found: " + dd);
            Assert.assertEquals(DownloadDescriptor.ERROR, dd.status);
            Assert.assertTrue(dd.error.contains("duplicate"));

            Assert.assertFalse(iter.hasNext());
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testIterateError()
    {
        log.debug("testIterateError");
        try
        {
            String[] uris = new String[]
            {
                "foo:bar/baz"
            };

            Iterator<DownloadDescriptor> iter = DownloadUtil.iterateURLs(uris, null);
            long num = 0;
            while ( iter.hasNext() )
            {
                DownloadDescriptor dd = iter.next();
                num++;
                log.debug("found: " + dd);
                Assert.assertEquals(DownloadDescriptor.ERROR, dd.status);
            }
            Assert.assertEquals(uris.length, num);

        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testIterateFragment()
    {
        try
        {
            String[] uris = new String[]
            {
                "ad:foo/bar",
                "ad:foo/baz"
            };
            String frag = "asf=true";
            Iterator<DownloadDescriptor> iter = DownloadUtil.iterateURLs(uris, frag);
            long num = 0;
            while ( iter.hasNext() )
            {
                DownloadDescriptor dd = iter.next();
                num++;
                log.debug("found: " + dd);
                Assert.assertEquals(DownloadDescriptor.OK, dd.status);
                Assert.assertEquals("http", dd.url.getProtocol());
                URI uri = new URI(dd.uri);
                Assert.assertEquals(frag, uri.getFragment());
                Assert.assertTrue(frag, dd.url.getQuery().contains(frag));
            }
            Assert.assertEquals(uris.length, num);

        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

}
