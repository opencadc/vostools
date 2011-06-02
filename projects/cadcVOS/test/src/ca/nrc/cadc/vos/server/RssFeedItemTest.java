/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.nrc.cadc.vos.server;

import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.server.util.FixedSizeTreeSet;
import java.util.Date;
import java.util.Iterator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author jburke
 */
public class RssFeedItemTest
{
    private static final Logger log = Logger.getLogger(RssFeedItemTest.class);
    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.vos.server", Level.INFO);
    }
    
    public RssFeedItemTest() { }

    /**
     * Test of compareTo method, of class RssFeedItem.
     */
    @Test
    public void testCompareTo() {
        try
        {
            RssFeedItem older = new RssFeedItem(new Date(0L), null);
            RssFeedItem newer = new RssFeedItem(new Date(1000L), null);

            Assert.assertEquals(-1, newer.compareTo(older));
            Assert.assertEquals(0, newer.compareTo(newer));
            Assert.assertEquals(1, older.compareTo(newer));
            
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testDateSorting()
    {
        try
        {
            FixedSizeTreeSet<RssFeedItem> set = new FixedSizeTreeSet<RssFeedItem>();
            set.setMaxSize(3);

            ContainerNode parent = RssFeedTest.createContainerNode("/parent", null, 2011, 1, 1);
            DataNode child1 = RssFeedTest.createDataNode("/parent/child1", parent, 2011, 2, 1);
            DataNode child2 = RssFeedTest.createDataNode("/parent/child2", parent, 2011, 3, 1);
            DataNode child3 = RssFeedTest.createDataNode("/parent/child3", parent, 2010, 4, 1);

            set.add(new RssFeedItem(new Date(10000000l), parent));
            set.add(new RssFeedItem(new Date(20000000l), child1));
            set.add(new RssFeedItem(new Date(30000000l), child2));
            set.add(new RssFeedItem(new Date(40000000l), child3));

            Assert.assertFalse(set.isEmpty());
            Assert.assertTrue(set.size() == 3);

            Iterator<RssFeedItem> it = set.iterator();
            Assert.assertEquals(child3, it.next().node);
            Assert.assertEquals(child2, it.next().node);
            Assert.assertEquals(child1, it.next().node);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
}