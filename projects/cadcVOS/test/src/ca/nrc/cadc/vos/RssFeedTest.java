/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.nrc.cadc.vos;

import java.io.OutputStream;
import java.io.IOException;
import org.jdom.Document;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import java.util.Collection;
import ca.nrc.cadc.date.DateUtil;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Calendar;
import org.apache.log4j.Logger;
import org.jdom.Element;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author jburke
 */
public class RssFeedTest
{
    private static Logger log = Logger.getLogger(RssFeedTest.class);

    private static ContainerNode nodeA;
    private static Collection<Node> nodes;

    public RssFeedTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // container node /A
        nodeA = createContainerNode("/A", null, 2010, Calendar.MAY, 15);

        // container node /A/B1
        ContainerNode nodeAB1 = createContainerNode("/A/B1", nodeA, 2010, Calendar.MAY, 15);

        // container node /A/B2
        ContainerNode nodeAB2 = createContainerNode("/A/B2", nodeA, 2010, Calendar.MAY, 15);

        // data node /A/B3
        DataNode nodeAB3 = createDataNode("/A/B3", nodeA, 2010, Calendar.MAY, 15);

        // data node /A/B4
        DataNode nodeAB4 = createDataNode("/A/B4", nodeA, 2010, Calendar.MAY, 15);

        // container node /A/B1/C1
        ContainerNode nodeAB1C1 = createContainerNode("/A/B1/C1", nodeAB1, 2010, Calendar.MAY, 15);

        // container node /A/B1/C2
        ContainerNode nodeAB1C2 = createContainerNode("/A/B1/C2", nodeAB1, 2010, Calendar.MAY, 15);

        // data node /A/B1/C3
        DataNode nodeAB1C3 = createDataNode("/A/B1/C3", nodeAB1, 2010, Calendar.MAY, 15);

        // data node /A/B1/C4
        DataNode nodeAB1C4 = createDataNode("/A/B1/C4", nodeAB1, 2010, Calendar.MAY, 15);

        // data node /A/B2/C1
        DataNode nodeAB2C1 = createDataNode("/A/B2/C1", nodeAB2, 2010, Calendar.MAY, 15);

        // data node /A/B2/C2
        DataNode nodeAB2C2 = createDataNode("/A/B2/C2", nodeAB2, 2010, Calendar.MAY, 15);

        // container node /A/B1/C2/D1
        ContainerNode nodeAB1C2D1 = createContainerNode("/A/B1/C2/D1", nodeAB1C2, 2010, Calendar.MAY, 15);

        // data node /A/B1/C2/D1/E1
        DataNode nodeAB1C2D1E1 = createDataNode("/A/B2/C2/D1/E1", nodeAB1C2D1, 2010, Calendar.MAY, 15);

        // build node hierarchy
        // nodeAB1C2D1.setNodes(Arrays.asList((Node) nodeAB1C2D1E1));
        // nodeAB1C2.setNodes(Arrays.asList((Node) nodeAB1C2D1));
        // nodeAB2.setNodes(Arrays.asList((Node) nodeAB2C1, (Node) nodeAB2C2));
        // nodeAB1.setNodes(Arrays.asList((Node) nodeAB1C1, (Node) nodeAB1C2, (Node) nodeAB1C3, (Node) nodeAB1C4));
        // nodeA.setNodes(Arrays.asList((Node) nodeAB1, (Node) nodeAB2, (Node) nodeAB3, (Node) nodeAB4));

        nodes = new ArrayList<Node>();
        nodes.add(nodeAB1);
        nodes.add(nodeAB2);
        nodes.add(nodeAB3);
        nodes.add(nodeAB4);
        nodes.add(nodeAB1C1);
        nodes.add(nodeAB1C2);
        nodes.add(nodeAB1C3);
        nodes.add(nodeAB1C4);
        nodes.add(nodeAB2C1);
        nodes.add(nodeAB2C2);
        nodes.add(nodeAB1C2D1);
        nodes.add(nodeAB1C2D1E1);
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of createFeed method, of class RssFeed.
     */
    @Test
    public void testCreateFeed()
        throws Exception
    {
        log.debug("createFeed");

        Element feed = RssFeed.createFeed(nodeA, nodes);
        write(feed, System.out);
        
        log.info("createFeed passed");
    }

    /**
     * Test of createErrorFeed method, of class RssFeed.
     */
    @Test
    public void testCreateErrorFeed_Node_Throwable()
        throws Exception
    {
        log.debug("createErrorFeed_Node_Throwable");

        Throwable t = new Throwable("throwable error message");
        Element feed = RssFeed.createErrorFeed(nodeA, t);
        write(feed, System.out);
        
        log.info("createErrorFeed_Node_Throwable passed");
    }

    /**
     * Test of createErrorFeed method, of class RssFeed.
     */
    @Test
    public void testCreateErrorFeed_Node_String()
        throws Exception
    {
        log.debug("createErrorFeed_Node_String");

        String message = "Error message";
        Element feed = RssFeed.createErrorFeed(nodeA, message);
        write(feed, System.out);

        log.info("createErrorFeed_Node_String passed");
    }

    protected static ContainerNode createContainerNode(String path, ContainerNode parent, int year, int month, int date)
        throws URISyntaxException
    {
        VOSURI vosURI = new VOSURI(VOS.VOS_URI + path);
        ContainerNode cnode = new ContainerNode(vosURI);
        cnode.setParent(parent);
        cnode.setOwner("jburke");
        cnode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_TITLE, "Title of ContainerNode " + path));
        cnode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_DESCRIPTION, "Description of ContainerNode " + path));
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, month, date);
        String lastModified = DateUtil.toString(cal.getTime(), DateUtil.ISO_DATE_FORMAT, DateUtil.UTC);
        cnode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_LASTMODIFIED, lastModified));
        return cnode;
    }

    protected static DataNode createDataNode(String path, ContainerNode parent, int year, int month, int date)
        throws URISyntaxException
    {
        VOSURI vosURI = new VOSURI(VOS.VOS_URI + path);
        DataNode dnode = new DataNode(vosURI);
        dnode.setParent(parent);
        dnode.setOwner("jburke");
        dnode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_TITLE, "Title of DataNode " + path));
        dnode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_DESCRIPTION, "Description of DataNode " + path));
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, month, date);
        String lastModified = DateUtil.toString(cal.getTime(), DateUtil.ISO_DATE_FORMAT, DateUtil.UTC);
        dnode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_LASTMODIFIED, lastModified));
        return dnode;
    }

    protected void write(Element root, OutputStream out)
        throws IOException
    {
        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat());
        outputter.output(new Document(root), out);
    }


}