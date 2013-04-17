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

package ca.nrc.cadc.vos.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.server.util.FixedSizeTreeSet;

/**
 *
 * @author jburke
 */
public class RssFeedTest
{
    private static Logger log = Logger.getLogger(RssFeedTest.class);
    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.vos.server", Level.INFO);
    }
    
    private static String VOS_URI =  "vos://example.com!vospace";
    private static String BASE_URL = "http://example.com/vospace/nodes";
    
    private static ContainerNode nodeA;
    private static Collection<RssFeedItem> nodes;
    private static String NODE_OWNER = "SampleOwner";

    private static DateFormat dateFormat = DateUtil.getDateFormat(DateUtil.ISO_DATE_FORMAT, DateUtil.UTC);

    public RssFeedTest() { }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // container node /A
        nodeA = createContainerNode("/A", null, 2010, Calendar.MAY, 1);

        // container node /A/B1
        ContainerNode nodeAB1 = createContainerNode("/A/B1", nodeA, 2010, Calendar.MAY, 2);

        // container node /A/B2
        ContainerNode nodeAB2 = createContainerNode("/A/B2", nodeA, 2010, Calendar.MAY, 2);

        // data node /A/B3
        DataNode nodeAB3 = createDataNode("/A/B3", nodeA, 2010, Calendar.MAY, 3);

        // data node /A/B4
        DataNode nodeAB4 = createDataNode("/A/B4", nodeA, 2010, Calendar.MAY, 3);

        // container node /A/B1/C1
        ContainerNode nodeAB1C1 = createContainerNode("/A/B1/C1", nodeAB1, 2010, Calendar.MAY, 4);

        // container node /A/B1/C2
        ContainerNode nodeAB1C2 = createContainerNode("/A/B1/C2", nodeAB1, 2010, Calendar.MAY, 5);

        // data node /A/B1/C3
        DataNode nodeAB1C3 = createDataNode("/A/B1/C3", nodeAB1, 2010, Calendar.MAY, 6);

        // data node /A/B1/C4
        DataNode nodeAB1C4 = createDataNode("/A/B1/C4", nodeAB1, 2010, Calendar.MAY, 7);

        // data node /A/B2/C1
        DataNode nodeAB2C1 = createDataNode("/A/B2/C1", nodeAB2, 2010, Calendar.MAY, 8);

        // data node /A/B2/C2
        DataNode nodeAB2C2 = createDataNode("/A/B2/C2", nodeAB2, 2010, Calendar.MAY, 9);

        // container node /A/B1/C2/D1
        ContainerNode nodeAB1C2D1 = createContainerNode("/A/B1/C2/D1", nodeAB1C2, 2010, Calendar.MAY, 10);

        // data node /A/B1/C2/D1/E1
        DataNode nodeAB1C2D1E1 = createDataNode("/A/B2/C2/D1/E1", nodeAB1C2D1, 2010, Calendar.MAY, 10);

        // build node hierarchy
        // nodeAB1C2D1.setNodes(Arrays.asList((Node) nodeAB1C2D1E1));
        // nodeAB1C2.setNodes(Arrays.asList((Node) nodeAB1C2D1));
        // nodeAB2.setNodes(Arrays.asList((Node) nodeAB2C1, (Node) nodeAB2C2));
        // nodeAB1.setNodes(Arrays.asList((Node) nodeAB1C1, (Node) nodeAB1C2, (Node) nodeAB1C3, (Node) nodeAB1C4));
        // nodeA.setNodes(Arrays.asList((Node) nodeAB1, (Node) nodeAB2, (Node) nodeAB3, (Node) nodeAB4));

        nodes = new ArrayList<RssFeedItem>();
        nodes.add(new RssFeedItem(null, nodeAB1));
        nodes.add(new RssFeedItem(null, nodeAB2));
        nodes.add(new RssFeedItem(null, nodeAB3));
        nodes.add(new RssFeedItem(null, nodeAB4));
        nodes.add(new RssFeedItem(null, nodeAB1C1));
        nodes.add(new RssFeedItem(null, nodeAB1C2));
        nodes.add(new RssFeedItem(null, nodeAB1C3));
        nodes.add(new RssFeedItem(null, nodeAB1C4));
        nodes.add(new RssFeedItem(null, nodeAB2C1));
        nodes.add(new RssFeedItem(null, nodeAB2C2));
        nodes.add(new RssFeedItem(null, nodeAB1C2D1));
        nodes.add(new RssFeedItem(null, nodeAB1C2D1E1));
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

        Element feed = RssFeed.createFeed(nodeA, nodes, BASE_URL);
        //TODO: assert something?
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(feed, out);
        log.debug(out.toString());
        
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
        Element feed = RssFeed.createErrorFeed(nodeA, t, BASE_URL);
        //TODO: assert something?
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(feed, out);
        log.debug(out.toString());
        
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
        Element feed = RssFeed.createErrorFeed(nodeA, message, BASE_URL);
        //TODO: assert something?
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(feed, out);
        log.debug(out.toString());

        log.info("createErrorFeed_Node_String passed");
    }

    /**
     * Test of createFeed method, of class RssFeed.
     */
    @Test
    public void testCreateFeedByDate()
        throws Exception
    {
        log.debug("testCreateFeedByDate");

        ContainerNode parent = createContainerNode("/parent", null, 2010, 0, 1);
        DataNode child1 = createDataNode("/parent/child1", parent, 2010, 1, 1);
        DataNode child2 = createDataNode("/parent/child2", parent, 2010, 2, 1);
        DataNode child3 = createDataNode("/parent/child3", parent, 2010, 3, 1);

        FixedSizeTreeSet set = new FixedSizeTreeSet();
        set.setMaxSize(4);

        set.add(new RssFeedItem(new Date(0L), parent));
        set.add(new RssFeedItem(new Date(1000000000L), child1));
        set.add(new RssFeedItem(new Date(3000000000L), child3));
        set.add(new RssFeedItem(new Date(2000000000L), child2));
        
        Element feed = RssFeed.createFeed(nodeA, set, BASE_URL);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(feed, out);
        log.debug(out.toString());

        Element channel = feed.getChild("channel");
        List<Element> items = channel.getChildren("item");
        Iterator<Element> it = items.iterator();

        DateFormat localFormat = DateUtil.getDateFormat(DateUtil.ISO_DATE_FORMAT, DateUtil.LOCAL);

        Element item1 = it.next();
        Element pubDate1 = item1.getChild("pubDate");
        Date expected1 = localFormat.parse("2010-04-01 00:00:00.000");
        Date actual1 = dateFormat.parse(pubDate1.getText());
        Assert.assertEquals(expected1, actual1);

        Element item2 = it.next();
        Element pubDate2 = item2.getChild("pubDate");
        Date expected2 = localFormat.parse("2010-03-01 00:00:00.000");
        Date actual2 = dateFormat.parse(pubDate2.getText());
        Assert.assertEquals(expected2, actual2);

        Element item3 = it.next();
        Element pubDate3 = item3.getChild("pubDate");
        Date expected3 = localFormat.parse("2010-02-01 00:00:00.000");
        Date actual3 = dateFormat.parse(pubDate3.getText());
        Assert.assertEquals(expected3, actual3);

        Element item4 = it.next();
        Element pubDate4 = item4.getChild("pubDate");
        Date expected4 = localFormat.parse("2010-01-01 00:00:00.000");
        Date actual4 = dateFormat.parse(pubDate4.getText());
        Assert.assertEquals(expected4, actual4);

        log.info("testCreateFeedByDate passed");
    }

    static ContainerNode createContainerNode(String path, ContainerNode parent, int year, int month, int date)
        throws URISyntaxException
    {
        VOSURI vosURI = new VOSURI(VOS_URI + path);
        ContainerNode cnode = new ContainerNode(vosURI);
        cnode.setParent(parent);
        cnode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CREATOR, NODE_OWNER));
        cnode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_TITLE, "Title of ContainerNode " + path));
        cnode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_DESCRIPTION, "Description of ContainerNode " + path));
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, month, date);
        String lastModified = dateFormat.format(cal.getTime());
        cnode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_DATE, lastModified));
        return cnode;
    }

    static DataNode createDataNode(String path, ContainerNode parent, int year, int month, int date)
        throws URISyntaxException
    {
        VOSURI vosURI = new VOSURI(VOS_URI + path);
        DataNode dnode = new DataNode(vosURI);
        dnode.setParent(parent);
        dnode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CREATOR, NODE_OWNER));
        dnode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_TITLE, "Title of DataNode " + path));
        dnode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_DESCRIPTION, "Description of DataNode " + path));
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, month, date);
        String lastModified = dateFormat.format(cal.getTime());
        dnode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_DATE, lastModified));
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