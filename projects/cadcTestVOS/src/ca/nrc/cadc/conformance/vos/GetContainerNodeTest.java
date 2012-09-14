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

package ca.nrc.cadc.conformance.vos;

import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeReader;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.VOSURI;
import com.meterware.httpunit.WebResponse;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import static org.junit.Assert.*;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.matchers.JUnitMatchers;

/**
 * Test case for creating ContainerNodes.
 *
 * @author jburke
 */
public class GetContainerNodeTest extends VOSNodeTest
{
    private static Logger log = Logger.getLogger(GetContainerNodeTest.class);

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.conformance.vos", Level.INFO);
    }
    
    public GetContainerNodeTest()
    {
        super();
    }

    @Test
    public void getContainerNode()
    {
        try
        {
            log.debug("getContainerNode");

            // Get a ContainerNode.
            TestNode testNode = getSampleContainerNode();

            // Add ContainerNode to the VOSpace.
            WebResponse response = put(testNode.sampleNode);
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Add a child DataNode.
            DataNode nodeAB = new DataNode(new VOSURI(testNode.sampleNode.getUri() + "/B"));
            response = put(VOSBaseTest.NODE_ENDPOINT, nodeAB, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());
            
            // Get the node from vospace
            response = get(testNode.sampleNode);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());

            // Get the response (an XML document)
            String xml = response.getText();
            log.debug("GET XML:\r\n" + xml);

            // Validate against the VOSPace schema.
            NodeReader reader = new NodeReader();
            Node returnedNode = reader.read(xml);
            assertTrue("Node returned from getContainerNode should be a ContainerNode",
                        returnedNode instanceof ContainerNode);
            ContainerNode validatedNode = (ContainerNode) returnedNode;

            // Nodes should have a single Node.
            assertEquals("Sample Node should have a single child Node", 1, validatedNode.getNodes().size());

            // If the service supports LinkNodes and it resolves parent LinkNodes.
            if (supportLinkNodes && resolvePathNodes)
            {
                // Get the node from vospace
                response = get(testNode.sampleNodeWithLink);
                assertEquals("GET response code should be 200", 200, response.getResponseCode());

                // Get the response (an XML document)
                xml = response.getText();
                log.debug("GET XML:\r\n" + xml);

                // Validate against the VOSPace schema.
                reader = new NodeReader();
                reader.read(xml);
            }

            // Delete the node
            response = delete(testNode.sampleNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("getContainerNode passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception: " + unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    /**
     * min: the returned record for the node contains minimum detail with all
     * optional parts removed - the node type should be returned
     * e.g. <Node uri="vos://service/name" xsi:type="Node"/>
     * 
     * Detail parameter not currently supported.
     */
    @Ignore("min detail parameter not currently implemented")
    @Test
    public void getMinContainerNode()
    {
        try
        {
            log.debug("getMinContainerNode");

            // Get a ContainerNode.
            TestNode node = getSampleContainerNode();

            // Add ContainerNode to the VOSpace.
            WebResponse response = put(node.sampleNode);
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Request Parameters
            Map<String, String> parameters = new HashMap<String, String>();
            parameters.put("detail", "min");

            // Get the node from vospace
            response = get(node.sampleNode, parameters);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());

            // Get the response (an XML document)
            String xml = response.getText();
            log.debug("GET XML:\r\n" + xml);

            // Validate against the VOSPace schema.
            NodeReader reader = new NodeReader();
            ContainerNode validatedNode = (ContainerNode) reader.read(xml);

            // Node properties should be empty.
            assertEquals("Node properties should be empty", 0, validatedNode.getProperties().size());

            // Node child nodes should be empty.
            assertEquals("Node child list should be empty", 0, validatedNode.getNodes().size());

            // Delete the node
            response = delete(node.sampleNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("getMinContainerNode passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception: " + unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    } 

    /*
     * max: the returned record for the node contains the maximum detail, 
     * including any xsi:type specific extensions
     * 
     * Detail parameter not currently supported.
     */
    @Ignore("max detail parameter not currently implemented")
    @Test
    public void getMaxContainerNode()
    {
        try
        {
            log.debug("getMaxContainerNode");

            // Get a ContainerNode.
            TestNode node = getSampleContainerNode();

            // Add ContainerNode to the VOSpace.
            WebResponse response = put(node.sampleNode);
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Request Parameters
            Map<String, String> parameters = new HashMap<String, String>();
            parameters.put("detail", "max");

            // Get the node from vospace
            response = get(node.sampleNode, parameters);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());

            // Get the response (an XML document)
            String xml = response.getText();
            log.debug("GET XML:\r\n" + xml);

            // Validate against the VOSPace schema.
            NodeReader reader = new NodeReader();
            ContainerNode validatedNode = (ContainerNode) reader.read(xml);

            // Node properties should be empty.
            assertEquals("Node properties should have a single property", 1, validatedNode.getProperties().size());

            // Node child nodes should be empty.
            assertEquals("Node child list should have 3 child nodes", 3, validatedNode.getNodes().size());

            // Delete the node
            response = delete(node.sampleNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("getMaxContainerNode passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception: " + unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
    /*
     * properties: the returned record for the node contains the basic node 
     * element with a list of properties but no xsi:type specific extensions
     * 
     * Detail parameter not currently supported.
     */
    @Ignore("properties detail parameter not currently implemented")
    @Test
    public void getPropertiesContainerNode()
    {
        try
        {
            log.debug("getPropertiesContainerNode");

            // Get a ContainerNode.
            TestNode node = getSampleContainerNode();

            // Add ContainerNode to the VOSpace.
            WebResponse response = put(node.sampleNode);
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Request Parameters
            Map<String, String> parameters = new HashMap<String, String>();
            parameters.put("detail", "properties");

            // Get the node from vospace
            response = get(node.sampleNode, parameters);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());

            // Get the response (an XML document)
            String xml = response.getText();
            log.debug("GET XML:\r\n" + xml);

            // Validate against the VOSPace schema.
            NodeReader reader = new NodeReader();
            ContainerNode validatedNode = (ContainerNode) reader.read(xml);

            // Node properties should be empty.
            assertEquals("Node properties should have a single property", 1, validatedNode.getProperties().size());

            // Node child nodes should be empty.
            assertEquals("Node child list should have 3 child nodes", 3, validatedNode.getNodes().size());

            // Delete the node
            response = delete(node.sampleNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("getPropertiesContainerNode passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception: " + unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    /**
     * If a "uri" and "offset" are specified in the request then the returned list 
     * will consist of the subset of children which begins at the node matching 
     * the specified value of the "uri" parameter and with cardinality matching 
     * the specified value of the "offset" parameter drawn from an ordered sequence 
     * of all children.
     * 
     * Not currently supported.
     */
    @Ignore("uri parameter not currently implemented")
    @Test
    public void getUriOffsetNode()
    {
        try
        {
            log.debug("getUriOffsetNode");

            // Parent node.
            TestNode nodeA = getSampleContainerNode("/A");

            // Add ContainerNode to the VOSpace.
            WebResponse response = put(nodeA.sampleNode);
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Child node B.
            ContainerNode nodeAB = new ContainerNode(new VOSURI(nodeA.sampleNode.getUri() + "/B"));
            response = put(nodeAB);
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Child node C.
            ContainerNode nodeABC = new ContainerNode(new VOSURI(nodeAB.getUri() + "/C"));
            response = put(nodeABC);
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Child node D.
            ContainerNode nodeABCD = new ContainerNode(new VOSURI(nodeABC.getUri() + "/D"));
            response = put(nodeABCD);
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Request Parameters to get child nodes B & C only
            Map<String, String> parameters = new HashMap<String, String>();
            parameters.put("uri", nodeAB.getUri().toString());
            parameters.put("offset", "1");

            // Get the node from vospace
            response = get(nodeAB, parameters);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());

            // Get the response (an XML document)
            String xml = response.getText();
            log.debug("GET XML:\r\n" + xml);

            // Validate against the VOSPace schema.
            NodeReader reader = new NodeReader();
            ContainerNode validatedNode = (ContainerNode) reader.read(xml);

            // Node child nodes should only have a single node.
            assertEquals("Node child list should have 1 child node", 1, validatedNode.getNodes().size());

            // Child node should be nodeABC.
            Node child = validatedNode.getNodes().get(0);
            assertEquals("", nodeABC.getUri(), child.getUri());

            // Delete the nodes.
            response = delete(nodeABCD);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());
            response = delete(nodeABC);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());
            response = delete(nodeAB);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());
            response = delete(nodeA.sampleNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("getUriOffsetNode passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception: " + unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    /*
     * limit with an integer value indicating the maximum number of results in the response.
     * 
     * No limit indicates a request for an unpaged list. However the server 
     * MAY still impose its own limit on the size of an individual response, 
     * splitting the results into more than one page if required.
     * 
     * limit parameter not currently supported.
     */
    @Ignore("limit parameter not currently implemented")
    @Test
    public void getLimitContainerNode()
    {
        try
        {
            log.debug("getLimitContainerNode");

            // Get a ContainerNode.
            TestNode node = getSampleContainerNode();

            // Add ContainerNode to the VOSpace.
            WebResponse response = put(node.sampleNode);
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Request Parameters
            Map<String, String> parameters = new HashMap<String, String>();
            parameters.put("limit", "9");

            // Get the node from vospace
            response = get(node.sampleNode, parameters);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());

            // Get the response (an XML document)
            String xml = response.getText();
            log.debug("GET XML:\r\n" + xml);

            // Validate against the VOSpace schema.
            NodeReader reader = new NodeReader();
            ContainerNode validatedNode = (ContainerNode) reader.read(xml);

            // Node properties should be empty.
            assertEquals("Node properties should have a single property", 1, validatedNode.getProperties().size());

            // Node child nodes should be empty.
            assertEquals("Node child list should have 3 child nodes", 3, validatedNode.getNodes().size());

            // TODO validate that there are X number of child nodes.
            
            // Delete the node
            response = delete(node.sampleNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("getLimitContainerNode passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception: " + unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
    /**
     * The service SHALL throw a HTTP 401 status code including a PermissionDenied 
     * fault in the entity-body if the user does not have permissions to perform the operation
     */
    @Ignore("Currently unable to test authorization")
    @Test
    public void permissionDeniedFault()
    {
        try
        {
            log.debug("permissionDeniedFault");

            // Get a ContainerNode.
            TestNode node = getSampleContainerNode();
            
            // Add ContainerNode to the VOSpace.
            WebResponse response = put(node.sampleNode);
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // TODO: how to get the node without permission to do so?
            response = get(node.sampleNode);
            assertEquals("GET response code should be 401", 401, response.getResponseCode());

            // Response message body should be 'PermissionDenied'
            assertEquals("Response message body should be 'PermissionDenied'", "PermissionDenied", response.getResponseMessage());

            log.info("permissionDeniedFault passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception: " + unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
    /**
     * The service SHALL throw a HTTP 404 status code including a NodeNotFound 
     * fault in the entity-body if the target Node does not exist
     */
    @Test
    public void nodeNotFoundFault()
    {
        try
        {
            log.debug("nodeNotFoundFault");

            // Create a Node with a nonexistent parent node
            TestNode nodeAB = getSampleContainerNode("/A/B");

            // Try and get the Node from the VOSpace.
            WebResponse response = get(nodeAB.sampleNode);
            assertEquals("GET response code should be 404 for a node that doesn't exist", 404, response.getResponseCode());

            // Response entity body should contain 'NodeNotFound'
            assertThat(response.getText().trim(), JUnitMatchers.containsString("NodeNotFound"));

            log.info("nodeNotFoundFault passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception: " + unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
}
