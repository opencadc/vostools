
package ca.nrc.cadc.dlm;

import ca.nrc.cadc.util.Log4jInit;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
        Log4jInit.setLevel("ca.nrc.cadc", Level.INFO);
    }

    @Test
    public void testIterateOK()
    {
        log.debug("testIterateOK");
        try
        {
            StringBuilder sb = new StringBuilder();
            sb.append("http://www.google.com");
            sb.append(DownloadUtil.URI_SEPARATOR);
            sb.append("test://www.example.com/test");
            List<String> uris = DownloadUtil.decodeListURI(sb.toString());
            
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
            Assert.assertEquals(uris.size(), num);
            
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
            StringBuilder sb = new StringBuilder();
            sb.append("http://www.google.com");
            sb.append(DownloadUtil.URI_SEPARATOR);
            sb.append("http://www.google.com");
            List<String> uris = DownloadUtil.decodeListURI(sb.toString());

            Assert.assertEquals("uri setup", 2, uris.size());

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

            // now test with removeDuplicates==true
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
            String s = "foo:bar/baz";
            List<String> uris = DownloadUtil.decodeListURI(s);

            Iterator<DownloadDescriptor> iter = DownloadUtil.iterateURLs(uris, null);
            long num = 0;
            while ( iter.hasNext() )
            {
                DownloadDescriptor dd = iter.next();
                num++;
                log.debug("found: " + dd);
                Assert.assertEquals(DownloadDescriptor.ERROR, dd.status);
            }
            Assert.assertEquals(uris.size(), num);

        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testIterateParams()
    {
        try
        {
            String s = "test://www.example.com/test";
            List<String> uris = DownloadUtil.decodeListURI(s);

            String s2 = "runid=123&cutout=[1]&cutout=[2]";
            Map<String,List<String>> params = DownloadUtil.decodeParamMap(s2);

            Iterator<DownloadDescriptor> iter = DownloadUtil.iterateURLs(uris, params);
            long num = 0;
            while ( iter.hasNext() )
            {
                DownloadDescriptor dd = iter.next();
                num++;
                log.debug("found: " + dd);
                Assert.assertEquals(DownloadDescriptor.OK, dd.status);
                Assert.assertEquals("http", dd.url.getProtocol());
                URI uri = new URI(dd.uri);
                Assert.assertNotNull(dd.url);
                Assert.assertNotNull(dd.url.getQuery());
                Assert.assertTrue( dd.url.getQuery().length() >= s2.length());
                Assert.assertTrue(dd.url.getQuery().contains("runid=123"));
                Assert.assertTrue(dd.url.getQuery().contains("cutout="));
            }
            Assert.assertEquals(uris.size(), num);

        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

}
