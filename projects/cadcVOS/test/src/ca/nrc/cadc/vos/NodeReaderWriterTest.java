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

package ca.nrc.cadc.vos;

import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.vos.VOS.NodeBusyState;

/**
 * Test read-write of Nodes using the NodeReader and NodeWriter. Every test here
 * performs a round trip: create node, write as xml, read with xml schema
 * validation enabled, compare to original node.
 * 
 * @author jburke
 */
public class NodeReaderWriterTest
{
    private static Logger log = Logger.getLogger(NodeReaderWriterTest.class);
    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.vos", Level.INFO);
    }

    // TODO: make lists of nodes for a variety of test scenarios
    ContainerNode containerNode;
    DataNode dataNode;
    LinkNode linkNode;
    UnstructuredDataNode unstructuredDataNode;
    StructuredDataNode structuredDataNode;

    public NodeReaderWriterTest()
    {
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {

    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
    }

    @Before
    public void setUp() throws Exception
    {
        // List of NodeProperty
        List<NodeProperty> properties = new ArrayList<NodeProperty>();
        NodeProperty nodeProperty = new NodeProperty("ivo://ivoa.net/vospace/core#description",
                "My award winning images");
        nodeProperty.setReadOnly(true);
        properties.add(nodeProperty);

        // List of Node
        List<Node> nodes = new ArrayList<Node>();
        nodes.add(new DataNode(new VOSURI("vos://cadc.nrc.ca!vospace/dir/ngc4323")));
        nodes.add(new DataNode(new VOSURI("vos://cadc.nrc.ca!vospace/dir/ngc5796")));
        nodes.add(new DataNode(new VOSURI("vos://cadc.nrc.ca!vospace/dir/ngc6801")));

        // ContainerNode
        containerNode = new ContainerNode(new VOSURI("vos://cadc.nrc.ca!vospace/dir"));
        containerNode.setProperties(properties);
        containerNode.setNodes(nodes);

        // LinkNode
        linkNode = new LinkNode(new VOSURI("vos://cadc.nrc.ca!vospace/dir/somefile"),
        		new URI("vos://cadc.nrc.ca!vospace/dir/sometarget"));
        linkNode.setProperties(properties);
        
        // UnstructuredDataNode
        unstructuredDataNode = new UnstructuredDataNode(new VOSURI("vos://cadc.nrc.ca!vospace/dir/somefile"));
        unstructuredDataNode.setProperties(properties);
        unstructuredDataNode.setBusy(NodeBusyState.busyWithWrite);
        // TODO: add some standard props here
        
        // StructuredDataNode
        structuredDataNode = new StructuredDataNode(new VOSURI("vos://cadc.nrc.ca!vospace/dir/somefile"));
        structuredDataNode.setProperties(properties);
        structuredDataNode.setBusy(NodeBusyState.busyWithWrite);
        // TODO: add some standard props here
        
        // DataNode
        dataNode = new DataNode(new VOSURI("vos://cadc.nrc.ca!vospace/dir/somefile"));
        dataNode.setProperties(properties);
        dataNode.setBusy(NodeBusyState.busyWithWrite);
        // TODO: add some standard props here
    }

    @After
    public void tearDown()
    {
    }

    private void comparePropertyList(List<NodeProperty> p1, List<NodeProperty> p2)
    {
        Assert.assertEquals("properties.size()", p1.size(), p2.size());
        for (NodeProperty np1 : p1)
        {
            boolean found = false;
            for (NodeProperty np2 : p2)
            {
                log.debug("looking for " + np1);
                if (np1.getPropertyURI().equals(np2.getPropertyURI())
                        && np1.getPropertyValue().equals(np2.getPropertyValue()))
                {
                    found = true;
                    break; // inner loop
                }
            }
            Assert.assertTrue("found" + np1, found);
        }
    }

    private void compareContainerNodes(ContainerNode n1, ContainerNode n2)
    {
        List<Node> cn1 = n1.getNodes();
        List<Node> cn2 = n2.getNodes();

        Assert.assertEquals("nodes.size()", cn1.size(), cn2.size());
        for (int i = 0; i < cn1.size(); i++)
            // order should be preserved since we use list
            compareNodes(cn1.get(i), cn2.get(i));
    }

    private void compareDataNodes(DataNode n1, DataNode n2)
    {
        Assert.assertEquals("busy", n1.getBusy(), n2.getBusy());
        Assert.assertEquals("structured", n1.isStructured(), n2.isStructured());
    }

    private void compareLinkNodes(LinkNode n1, LinkNode n2)
    {
        Assert.assertEquals("target", n1.getTarget(), n2.getTarget());
    }
    
    private void compareURIList(List<URI> l1, List<URI> l2)
    {
        Assert.assertTrue(l1.containsAll(l2));
        Assert.assertTrue(l2.containsAll(l1));
    }

    private void compareNodes(Node n1, Node n2)
    {
        Assert.assertEquals("same class", n1.getClass(), n2.getClass());
        Assert.assertEquals("VOSURI", n1.getUri(), n2.getUri());
        Assert.assertEquals("owner", n1.getPropertyValue(VOS.PROPERTY_URI_CREATOR),
                n2.getPropertyValue(VOS.PROPERTY_URI_CREATOR));
        comparePropertyList(n1.getProperties(), n2.getProperties());
        compareURIList(n1.accepts, n2.accepts);
        compareURIList(n1.provides, n2.provides);
        if (n1 instanceof ContainerNode)
            compareContainerNodes((ContainerNode) n1, (ContainerNode) n2);
        else if (n1 instanceof DataNode)
            compareDataNodes((DataNode) n1, (DataNode) n2);
        else if (n1 instanceof LinkNode)
            compareLinkNodes((LinkNode) n1, (LinkNode) n2);
        else
            throw new UnsupportedOperationException("no test comparison for node type "
                    + n1.getClass().getName());

    }

    /**
     * Test of write method, of class NodeWriter.
     */
    @Test
    public void writeValidContainerNode()
    {
        try
        {
            log.debug("writeValidContainerNode");
            StringBuilder sb = new StringBuilder();
            NodeWriter instance = new NodeWriter();
            instance.write(containerNode, sb);
            log.debug(sb.toString());

            // validate the XML
            NodeReader reader = new NodeReader();
            Node n2 = reader.read(sb.toString());

            compareNodes(containerNode, n2);
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    @Test
    public void writeValidDataNode()
    {
        try
        {
            log.debug("writeValidDataNode");
            StringBuilder sb = new StringBuilder();
            NodeWriter instance = new NodeWriter();
            instance.write(dataNode, sb);
            log.debug(sb.toString());

            // validate the XML
            NodeReader reader = new NodeReader();
            Node n2 = reader.read(sb.toString());

            compareNodes(dataNode, n2);
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    @Test
    public void writeValidUnstructuredDataNode()
    {
        try
        {
            log.debug("writeValidUnstructuredDataNode");
            StringBuilder sb = new StringBuilder();
            NodeWriter instance = new NodeWriter();
            instance.write(unstructuredDataNode, sb);
            log.debug(sb.toString());

            // validate the XML
            NodeReader reader = new NodeReader();
            Node n2 = reader.read(sb.toString());

            compareNodes(unstructuredDataNode, n2);
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    @Test
    public void writeValidStructuredDataNode()
    {
        try
        {
            log.debug("writeValidStructuredDataNode");
            StringBuilder sb = new StringBuilder();
            NodeWriter instance = new NodeWriter();
            instance.write(structuredDataNode, sb);
            log.debug(sb.toString());

            // validate the XML
            NodeReader reader = new NodeReader();
            Node n2 = reader.read(sb.toString());

            compareNodes(structuredDataNode, n2);
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }
    
    @Test
    public void writeValidLinkNode()
    {
        try
        {
            log.debug("writeValidLinkNode");
            StringBuilder sb = new StringBuilder();
            NodeWriter instance = new NodeWriter();
            instance.write(linkNode, sb);
            log.debug(sb.toString());

            // validate the XML
            NodeReader reader = new NodeReader();
            Node n2 = reader.read(sb.toString());

            compareNodes(linkNode, n2);
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    /**
     * Test of write method, of class NodeWriter.
     */
    @Test
    public void writeToOutputStream()
    {
        try
        {
            log.debug("writeToOutputStream");
            NodeWriter instance = new NodeWriter();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            instance.write(dataNode, bos);
            bos.close();

            // validate the XML
            NodeReader reader = new NodeReader();
            Node n2 = reader.read(new ByteArrayInputStream(bos.toByteArray()));

            compareNodes(dataNode, n2);
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    /**
     * Test of write method, of class NodeWriter.
     */
    @Test
    public void writeToWriter()
    {
        try
        {
            log.debug("writeToWriter");
            NodeWriter instance = new NodeWriter();
            StringWriter sw = new StringWriter();
            instance.write(dataNode, sw);
            sw.close();

            log.debug(sw.toString());

            // validate the XML
            NodeReader reader = new NodeReader();
            Node n2 = reader.read(sw.toString());

            compareNodes(dataNode, n2);
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    @Test
    public void writeMaxDetailContainerNode()
    {
        try
        {
            // ContainerNode
            Node n = createDetailedNode();

            // write it
            NodeWriter instance = new NodeWriter();
            StringWriter sw = new StringWriter();
            instance.write(n, sw);
            sw.close();

            log.debug(sw.toString());

            // validate the XML
            NodeReader reader = new NodeReader();
            Node n2 = reader.read(sw.toString());

            compareNodes(n, n2);

        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    @Test
    public void testNoSchemaValidation()
    {
        try
        {
            StringBuffer sb = new StringBuffer();

            sb.append("<node xmlns=\"http://www.ivoa.net/xml/VOSpace/v2.0\" \n");
            sb.append("      xmlns:vos=\"http://www.ivoa.net/xml/VOSpace/v2.0\" \n");
            sb.append("      xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n");
            sb.append("      uri=\"vos://cadc.nrc.ca!vospace/dir/somefile\" \n");
            sb.append("      xsi:type=\"vos:DataNode\" busy=\"true\"> \n");
            sb.append("<accepts />\n"); // invalid in xsd sequence
            sb.append("<properties />\n");
            sb.append("</node>\n");
            String xml = sb.toString();

            log.debug(xml);

            // make sure this is not valid
            try
            {
                NodeReader reader = new NodeReader(true);
                Node n = reader.read(xml);
                fail("test XML is actually valid - test is broken");
            }
            catch (NodeParsingException expected)
            {
            }

            // read without validation
            NodeReader reader = new NodeReader(false);
            Node n = reader.read(xml);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            log.error("unexpected exception", t);
            fail(t.getMessage());
        }
    }

    // sample node with lots of detail in it
    private ContainerNode createDetailedNode() throws URISyntaxException
    {
        // ContainerNode
        ContainerNode cn = new ContainerNode(new VOSURI("vos://cadc.nrc.ca!vospace/testContainer"));

        Node n;
        List<NodeProperty> properties;

        // add a DataNode with some props
        n = new DataNode(new VOSURI("vos://cadc.nrc.ca!vospace/testContainer/ngc4323"));
        properties = new ArrayList<NodeProperty>();
        properties.add(new NodeProperty("ivo://ivoa.net/vospace/core#size", "123"));
        properties.add(new NodeProperty("ivo://ivoa.net/vospace/core#content-type", "image/fits"));
        n.setProperties(properties);
        List<URI> accepts = new ArrayList<URI>();
        List<URI> provides = new ArrayList<URI>();
        accepts.add(new URI("ivo://cadc.nrc.ca/vospace/view#view1"));
        accepts.add(new URI("ivo://cadc.nrc.ca/vospace/view#view2"));
        provides.add(new URI("ivo://cadc.nrc.ca/vospace/view#something"));
        provides.add(new URI("ivo://cadc.nrc.ca/vospace/view#anotherthing"));
        n.setAccepts(accepts);
        n.setProvides(provides);
        
        cn.getNodes().add(n);

        // add a ContainerNode with some props
        ContainerNode cn2 = new ContainerNode(new VOSURI(
                "vos://cadc.nrc.ca!vospace/testContainer/foo"));
        properties = new ArrayList<NodeProperty>();
        properties.add(new NodeProperty("ivo://ivoa.net/vospace/core#read-group",
                "ivo://cadc.nrc.ca/gms/groups#bar"));
        cn2.setProperties(properties);
        cn.getNodes().add(cn2);
        
        // add a LinkNode with some props
        LinkNode ln = new LinkNode (new VOSURI(
        		"vos://cadc.nrc.ca!vospace/testContainer/aLink"), 
        		new URI("vos://cadc.nrc.ca!vospace/testContainer/baz"));
        properties = new ArrayList<NodeProperty>();
        properties.add(new NodeProperty("ivo://ivoa.net/vospace/core#read-group",
                "ivo://cadc.nrc.ca/gms/groups#bar"));
        ln.setProperties(properties);
        cn2.getNodes().add(ln);

        // add another DataNode below
        n = new DataNode(new VOSURI("vos://cadc.nrc.ca!vospace/testContainer/baz"));
        properties = new ArrayList<NodeProperty>();
        properties.add(new NodeProperty("ivo://ivoa.net/vospace/core#size", "123"));
        properties.add(new NodeProperty("ivo://ivoa.net/vospace/core#content-type", "text/plain"));
        n.setProperties(properties);
        cn2.getNodes().add(n);

        return cn;
    }
}
