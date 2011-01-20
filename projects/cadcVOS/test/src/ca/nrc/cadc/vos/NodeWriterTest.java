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

import java.io.OutputStreamWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.custommonkey.xmlunit.Diff;
import org.jdom.Element;

import org.jdom.Namespace;
import org.junit.*;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.vos.VOS.NodeBusyState;

/**
 *
 * @author jburke
 */
public class NodeWriterTest extends AbstractCADCVOSTest<NodeWriter>
{
    private static Logger log = Logger.getLogger(NodeWriterTest.class);

    static
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }

    static ContainerNode containerNode;
    static DataNode dataNode;


    public NodeWriterTest()
    {
    }


    /**
     * Set and initialize the Test Subject.
     *
     * @throws Exception If anything goes awry.
     */
    @Override
    protected void initializeTestSubject() throws Exception
    {
        setTestSubject(new NodeWriter());
    }


    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // List of NodeProperty
        List<NodeProperty> properties = new ArrayList<NodeProperty>();
        NodeProperty nodeProperty =
                new NodeProperty("ivo://ivoa.net/vospace/core#description",
                                 "My award winning images");
        nodeProperty.setReadOnly(true);
        properties.add(nodeProperty);

        // List of Node
        List<Node> nodes = new ArrayList<Node>();
        nodes.add(new DataNode(
                new VOSURI("vos://cadc.nrc.ca!vospace/mydir/ngc4323")));
        nodes.add(new DataNode(
                new VOSURI("vos://cadc.nrc.ca!vospace/mydir/ngc5796")));
        nodes.add(new DataNode(
                new VOSURI("vos://cadc.nrc.ca!vospace/mydir/ngc6801")));

        // ContainerNode
        containerNode = new ContainerNode(
                new VOSURI("vos://cadc.nrc.ca!vospace/dir/subdir"));
        containerNode.setProperties(properties);
        containerNode.setNodes(nodes);

        // DataNode
        dataNode = new DataNode(
                new VOSURI("vos://cadc.nrc.ca!vospace/dir/subdir"));
        dataNode.setProperties(properties);
        dataNode.setBusy(NodeBusyState.busyWithWrite);
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of write method, of class NodeWriter.
     *
     * @throws Exception    Any problems should be reported.
     */
    @Test
    public void write_ContainerNode_StringBuilder() throws Exception
    {
        log.debug("write_ContainerNode_StringBuilder");
        StringBuilder sb = new StringBuilder();
        NodeWriter instance = new NodeWriter();
        instance.write(containerNode, sb);

        log.debug(sb.toString());

        final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                           "<node xmlns=\"http://www.ivoa.net/xml/VOSpace/v2" +
                           ".0\" xmlns:vos=\"http://www.ivoa" +
                           ".net/xml/VOSpace/v2.0\" xmlns:xsi=\"http://www.w3" +
                           ".org/2001/XMLSchema-instance\" uri=\"vos://cadc" +
                           ".nrc.ca!vospace/dir/subdir\" " +
                           "xsi:type=\"vos:ContainerNode\">\n" +
                           "  <properties />\n" +
                           "  <accepts>\n" +
                           "    <view uri=\"ivo://ivoa" +
                           ".net/vospace/core#defaultview\" />\n" +
                           "  </accepts>\n" +
                           "  <provides>\n" +
                           "    <view uri=\"ivo://ivoa" +
                           ".net/vospace/core#defaultview\" />\n" +
                           "    <view uri=\"ivo://cadc.nrc" +
                           ".ca/vospace/core#rssview\" />\n" +
                           "  </provides>\n" +
                           "  <capabilities />\n" +
                           "  <nodes>\n" +
                           "    <node uri=\"vos://cadc.nrc" +
                           ".ca!vospace/mydir/ngc4323\" " +
                           "xsi:type=\"vos:DataNode\">\n" +
                           "      <properties />\n" +
                           "      <accepts>\n" +
                           "        <view uri=\"ivo://ivoa" +
                           ".net/vospace/core#defaultview\" />\n" +
                           "      </accepts>\n" +
                           "      <provides>\n" +
                           "        <view uri=\"ivo://ivoa" +
                           ".net/vospace/core#defaultview\" />\n" +
                           "        <view uri=\"ivo://cadc.nrc" +
                           ".ca/vospace/core#dataview\" />\n" +
                           "      </provides>\n" +
                           "      <capabilities />\n" +
                           "    </node>\n" +
                           "    <node uri=\"vos://cadc.nrc" +
                           ".ca!vospace/mydir/ngc5796\" " +
                           "xsi:type=\"vos:DataNode\">\n" +
                           "      <properties />\n" +
                           "      <accepts>\n" +
                           "        <view uri=\"ivo://ivoa" +
                           ".net/vospace/core#defaultview\" />\n" +
                           "      </accepts>\n" +
                           "      <provides>\n" +
                           "        <view uri=\"ivo://ivoa" +
                           ".net/vospace/core#defaultview\" />\n" +
                           "        <view uri=\"ivo://cadc.nrc" +
                           ".ca/vospace/core#dataview\" />\n" +
                           "      </provides>\n" +
                           "      <capabilities />\n" +
                           "    </node>\n" +
                           "    <node uri=\"vos://cadc.nrc" +
                           ".ca!vospace/mydir/ngc6801\" " +
                           "xsi:type=\"vos:DataNode\">\n" +
                           "      <properties />\n" +
                           "      <accepts>\n" +
                           "        <view uri=\"ivo://ivoa.net/vospace/core#defaultview\" />\n" +
                           "      </accepts>\n" +
                           "      <provides>\n" +
                           "        <view uri=\"ivo://ivoa.net/vospace/core#defaultview\" />\n" +
                           "        <view uri=\"ivo://cadc.nrc.ca/vospace/core#dataview\" />\n" +
                           "      </provides>\n" +
                           "      <capabilities />\n" +
                           "    </node>\n" +
                           "  </nodes>\n" +
                           "</node>\n"
                           + "";

        final Diff diff = new Diff(XML, sb.toString());
        assertTrue("Documents should be similar.", diff.similar());
    }

    @Test
    public void write_DataNode_StringBuilder() throws Exception
    {
        log.debug("write_DataNode_StringBuilder");
        StringBuilder sb = new StringBuilder();
        NodeWriter instance = new NodeWriter();
        instance.write(dataNode, sb);
        log.debug(sb.toString());

        final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                           "<node xmlns=\"http://www.ivoa.net/xml/VOSpace/v2" +
                           ".0\" xmlns:vos=\"http://www.ivoa" +
                           ".net/xml/VOSpace/v2.0\" xmlns:xsi=\"http://www.w3" +
                           ".org/2001/XMLSchema-instance\" uri=\"vos://cadc" +
                           ".nrc.ca!vospace/dir/subdir\" " +
                           "xsi:type=\"vos:DataNode\" busy=\"true\">\n" +
                           "  <properties />\n" +
                           "  <accepts>\n" +
                           "    <view uri=\"ivo://ivoa" +
                           ".net/vospace/core#defaultview\" />\n" +
                           "  </accepts>\n" +
                           "  <provides>\n" +
                           "    <view uri=\"ivo://ivoa" +
                           ".net/vospace/core#defaultview\" />\n" +
                           "    <view uri=\"ivo://cadc.nrc.ca/vospace/core#dataview\" />\n" +
                           "  </provides>\n" +
                           "  <capabilities />\n" +
                           "</node>\n" +
                           "";

        // validate the XML
        final Diff diff = new Diff(XML, sb.toString());
        assertTrue("Documents should be similar.", diff.similar());

        log.info("write_DataNode_StringBuilder passed");
    }

    /**
     * Test of write method, of class NodeWriter.
     */
    @Test
    public void write_ContainerNode_OutputStream()
    {
        try
        {
            log.debug("write_ContainerNode_OutputStream");
            NodeWriter instance = new NodeWriter();
            instance.write(containerNode, System.out);
            log.info("write_ContainerNode_OutputStream passed");
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
    public void write_DataNode_OutputStream()
    {
        try
        {
            log.debug("write_DataNode_OutputStream");
            NodeWriter instance = new NodeWriter();
            instance.write(dataNode, System.out);
            log.info("write_DataNode_OutputStream passed");
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
    public void write_ContainerNode_Writer()
    {
        try
        {
            log.debug("write_ContainerNode_Writer");
            NodeWriter instance = new NodeWriter();
            instance.write(containerNode, new OutputStreamWriter(System.out, "UTF-8"));
            log.info("write_ContainerNode_Writer passed");
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
    public void write_DataNode_Writer()
    {
        try
        {
            log.debug("write_DataNode_Writer");
            NodeWriter instance = new NodeWriter();
            instance.write(dataNode, new OutputStreamWriter(System.out, "UTF-8"));
            log.info("write_DataNode_Writer passed");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    @Test
    public void getPropertiesElement() throws Exception
    {
        final NodeProperty mockNodePropertyOne =
                createMock(NodeProperty.class);
        final NodeProperty mockNodePropertyTwo =
                createMock(NodeProperty.class);
        final NodeProperty mockNodePropertyThree =
                createMock(NodeProperty.class);
        final NodeProperty mockNodePropertyFour =
                createMock(NodeProperty.class);

        final List<NodeProperty> properties = new ArrayList<NodeProperty>();
        final Node mockNode = createMock(Node.class);

        properties.add(mockNodePropertyOne);
        properties.add(mockNodePropertyTwo);
        properties.add(mockNodePropertyThree);
        properties.add(mockNodePropertyFour);

        expect(mockNode.getProperties()).andReturn(properties).once();

        expect(mockNodePropertyOne.getPropertyURI()).andReturn("vos:PROP_ONE").
                once();
        expect(mockNodePropertyOne.getPropertyValue()).andReturn(
                "PROP_ONE_VAL").once();
        expect(mockNodePropertyOne.isReadOnly()).andReturn(true).once();

        expect(mockNodePropertyTwo.getPropertyURI()).andReturn("vos:PROP_TWO").
                once();
        expect(mockNodePropertyTwo.getPropertyValue()).andReturn(
                "PROP_TWO_VAL").once();
        expect(mockNodePropertyTwo.isReadOnly()).andReturn(false).once();

        expect(mockNodePropertyThree.getPropertyURI()).
                andReturn("vos:PROP_THREE").once();
        expect(mockNodePropertyThree.getPropertyValue()).andReturn(
                "PROP_THREE_VAL").once();
        expect(mockNodePropertyThree.isReadOnly()).andReturn(true).once();

        expect(mockNodePropertyFour.getPropertyURI()).andReturn("vos:PROP_FOUR").
                once();
        expect(mockNodePropertyFour.getPropertyValue()).andReturn(
                "PROP_FOUR_VAL").once();
        expect(mockNodePropertyFour.isReadOnly()).andReturn(true).once();

        replay(mockNode, mockNodePropertyOne, mockNodePropertyTwo,
               mockNodePropertyThree, mockNodePropertyFour);
        final Element element = getTestSubject().getPropertiesElement(mockNode);
        assertEquals("Should be four children.", 4,
                     element.getChildren().size());
        verify(mockNode, mockNodePropertyOne, mockNodePropertyTwo,
               mockNodePropertyThree, mockNodePropertyFour);

        // TEST 2
        reset(mockNode, mockNodePropertyOne, mockNodePropertyTwo,
              mockNodePropertyThree, mockNodePropertyFour);

        expect(mockNode.getProperties()).andReturn(properties).once();

        expect(mockNodePropertyOne.getPropertyURI()).andReturn("vos:PROP_ONE").
                once();

        expect(mockNodePropertyTwo.getPropertyURI()).andReturn("vos:PROP_TWO").
                once();
        expect(mockNodePropertyTwo.getPropertyValue()).andReturn(
                "PROP_TWO_VAL").once();
        expect(mockNodePropertyTwo.isReadOnly()).andReturn(false).once();

        expect(mockNodePropertyThree.getPropertyURI()).
                andReturn("vos:PROP_THREE").once();

        expect(mockNodePropertyFour.getPropertyURI()).
                andReturn("vos:PROP_FOUR").once();
        expect(mockNodePropertyFour.getPropertyValue()).andReturn(
                "PROP_FOUR_VAL").once();
        expect(mockNodePropertyFour.isReadOnly()).andReturn(true).once();

        replay(mockNode, mockNodePropertyOne, mockNodePropertyTwo,
               mockNodePropertyThree, mockNodePropertyFour);

        final Element element2 =
                getTestSubject().getPropertiesElement(mockNode, "vos:PROP_TWO",
                                                      "vos:PROP_FOUR");
        assertEquals("Should be two children.", 2,
                     element2.getChildren().size());
        verify(mockNode, mockNodePropertyOne, mockNodePropertyTwo,
               mockNodePropertyThree, mockNodePropertyFour);
    }

    @Test
    public void getNodesElement() throws Exception
    {
        final Search.Results mockResults = createMock(Search.Results.class);
        getTestSubject().setResults(mockResults);

        @SuppressWarnings("unchecked")
        final List<Node> childNodes = new ArrayList<Node>();
        final VOSURI mockVOSURI1 = createMock(VOSURI.class);
        final VOSURI mockVOSURI2 = createMock(VOSURI.class);
        final VOSURI mockVOSURI3 = createMock(VOSURI.class);
        final VOSURI mockVOSURI4 = createMock(VOSURI.class);
        final VOSURI mockVOSURI5 = createMock(VOSURI.class);

        final Node mockChildNode1 = createMock(Node.class);
        expect(mockChildNode1.getUri()).andReturn(mockVOSURI1).once();

        final Node mockChildNode2 = createMock(Node.class);
        expect(mockChildNode2.getUri()).andReturn(mockVOSURI2).once();

        final Node mockChildNode3 = createMock(Node.class);
        expect(mockChildNode3.getUri()).andReturn(mockVOSURI3).once();

        final Node mockChildNode4 = createMock(Node.class);
        expect(mockChildNode4.getUri()).andReturn(mockVOSURI4).once();

        final Node mockChildNode5 = createMock(Node.class);
        expect(mockChildNode5.getUri()).andReturn(mockVOSURI5).once();

        final Node mockChildNode6 = createMock(Node.class);
        final Node mockChildNode7 = createMock(Node.class);
        final Node mockChildNode8 = createMock(Node.class);

        childNodes.add(mockChildNode1);
        childNodes.add(mockChildNode2);
        childNodes.add(mockChildNode3);
        childNodes.add(mockChildNode4);
        childNodes.add(mockChildNode5);
        childNodes.add(mockChildNode6);
        childNodes.add(mockChildNode7);
        childNodes.add(mockChildNode8);

        final ContainerNode mockContainerNode =
                createMock(ContainerNode.class);

        expect(mockContainerNode.getNodes()).andReturn(childNodes).once();
        expect(mockResults.getLimit()).andReturn(5).times(3);
        expect(mockResults.getDetail()).andReturn(Search.Results.Detail.MIN).
                times(10);

        replay(mockContainerNode, mockResults, mockChildNode1, mockVOSURI1,
               mockChildNode2, mockVOSURI2, mockChildNode3, mockVOSURI3,
               mockChildNode4, mockVOSURI4, mockChildNode5, mockVOSURI5,
               mockChildNode6, mockChildNode7, mockChildNode8);
        final Element element =
                getTestSubject().getNodesElement(mockContainerNode);
        verify(mockContainerNode, mockResults, mockChildNode1, mockVOSURI1,
               mockChildNode2, mockVOSURI2, mockChildNode3, mockVOSURI3,
               mockChildNode4, mockVOSURI4, mockChildNode5, mockVOSURI5,
               mockChildNode6, mockChildNode7, mockChildNode8);

        assertEquals("Should only be 5 child elements.", 5,
                     element.getChildren().size());
    }

    @Test
    public void testFormat() throws Exception
    {
        final Element mockAcceptsElement = createMock(Element.class);
        final Element mockProvidesElement = createMock(Element.class);
        final Element mockPropertiesElement = createMock(Element.class);
        final Element mockChildNodesElement = createMock(Element.class);
        final Element mockCapabilitiesElement = createMock(Element.class);

        setTestSubject(new NodeWriter()
        {
            /**
             * Build the properties Element of a Node.
             *
             * @param node              Node.             The node to get
             * properties for.
             * @param propertyURIFilter URIs to filter on (inclusive).
             * @return properties Element.
             */
            @Override
            protected Element getPropertiesElement(Node node,
                                                   String... propertyURIFilter)
            {
                return mockPropertiesElement;
            }

            /**
             * Build the nodes Element of a ContainerNode.
             *
             * @param node Node.
             * @return nodes Element.
             */
            @Override
            protected Element getNodesElement(ContainerNode node)
            {
                return mockChildNodesElement;
            }

            /**
             * Build the accepts Element of a Node.
             *
             * @param node Node.
             * @return accepts Element.
             */
            @Override
            protected Element getAcceptsElement(Node node)
            {
                return mockAcceptsElement;
            }

            /**
             * Build the accepts Element of a Node.
             *
             * @param node Node.
             * @return accepts Element.
             */
            @Override
            protected Element getProvidesElement(Node node)
            {
                return mockProvidesElement;
            }

            /**
             * Build the capabilities Element of a Node.
             * <p/>
             * This option is not supported, but is necessary to appear in
             * some cases.
             *
             * @param node The node to build from.
             * @return The resulting Element.
             */
            @Override
            protected Element getCapabilitiesElement(Node node)
            {
                return mockCapabilitiesElement;
            }
        });

        final Element mockNodeElement = createMock(Element.class);
        Node mockNode = createMock(ContainerNode.class);
        NodeWriter.NodeElementFormatter nodeElementFormatter =
                getTestSubject().new NodeElementFormatter(mockNodeElement,
                                                          mockNode, false);

        final Search.Results mockResults = createMock(Search.Results.class);
        VOSURI mockVOSURI = createMock(VOSURI.class);
        String uriString = mockVOSURI.toString();
        String typeString = mockNode.getClass().getSimpleName();
        final Namespace namespace =
                Namespace.getNamespace("xsi",
                                       "http://www.w3.org/2001/XMLSchema-instance");

        getTestSubject().setResults(mockResults);

        // Expectations
        expect(mockNode.getUri()).andReturn(mockVOSURI).once();
        expect(mockResults.getDetail()).andReturn(Search.Results.Detail.MAX).
                times(2);

        expect(mockNodeElement.addContent(mockAcceptsElement)).andReturn(
                mockNodeElement).once();
        expect(mockNodeElement.addContent(mockProvidesElement)).andReturn(
                mockNodeElement).once();
        expect(mockNodeElement.addContent(mockCapabilitiesElement)).andReturn(
                mockNodeElement).once();

        expect(mockNodeElement.setAttribute("uri", uriString)).
                andReturn(mockNodeElement).once();
        expect(mockNodeElement.setAttribute("type", "vos:" + typeString,
                                            namespace)).
                andReturn(mockNodeElement).once();
        expect(mockNodeElement.addContent(mockPropertiesElement)).
                andReturn(mockNodeElement).once();
        expect(mockNodeElement.addContent(mockChildNodesElement)).
                andReturn(mockNodeElement).once();

        replay(mockNodeElement, mockChildNodesElement, mockAcceptsElement,
               mockProvidesElement, mockCapabilitiesElement, mockNode,
               mockResults, mockVOSURI);
        nodeElementFormatter.format();
        verify(mockNodeElement, mockChildNodesElement, mockAcceptsElement,
               mockProvidesElement, mockCapabilitiesElement, mockNode,
               mockResults, mockVOSURI);


        // TEST 2
        reset(mockNodeElement, mockResults, mockNode);

        final VOSURI mockDataNodeVOSURI = createMock(VOSURI.class);
        final Node mockDataNode = createMock(DataNode.class);
        nodeElementFormatter =
                getTestSubject().new NodeElementFormatter(mockNodeElement,
                                                          mockDataNode, false
                                                          );
        typeString = mockDataNode.getClass().getSimpleName();

        uriString = mockDataNodeVOSURI.toString();
        getTestSubject().setResults(mockResults);

        // Expectations
        expect(mockDataNode.getUri()).andReturn(mockDataNodeVOSURI).once();
        expect(mockResults.getDetail()).andReturn(
                Search.Results.Detail.PROPERTIES).times(2);

        expect(mockNodeElement.setAttribute("uri", uriString)).
                andReturn(mockNodeElement).once();
        expect(mockNodeElement.setAttribute("type", "vos:" + typeString,
                                            namespace)).
                andReturn(mockNodeElement).once();
        expect(mockNodeElement.addContent(mockPropertiesElement)).
                andReturn(mockNodeElement).once();

        replay(mockNodeElement, mockDataNode, mockResults, mockDataNodeVOSURI);
        nodeElementFormatter.format();
        verify(mockNodeElement, mockDataNode, mockResults, mockDataNodeVOSURI);
    }
}