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
import ca.nrc.cadc.vos.LinkNode;
import ca.nrc.cadc.vos.NodeReader;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.VOSURI;
import com.meterware.httpunit.WebResponse;
import java.net.URI;
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
public class GetLinkNodeTest extends VOSNodeTest
{
    private static Logger log = Logger.getLogger(GetLinkNodeTest.class);
    
    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.conformance.vos", Level.INFO);
    }
    
    public GetLinkNodeTest()
    {
        super();
    }

    @Test
    public void getLinkNode()
    {
        try
        {
            log.debug("getLinkNode");

            // Target ContainerNode A.
            ContainerNode nodeA = getSampleContainerNode("A");

            // Add ContainerNode to the VOSpace.
            WebResponse response = put(VOSBaseTest.NODE_ENDPOINT, nodeA, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Child container node B.
            ContainerNode nodeB = new ContainerNode(new VOSURI(nodeA.getUri() + "/B"));
            response = put(VOSBaseTest.NODE_ENDPOINT, nodeB, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Child data node C.
            DataNode nodeC = new DataNode(new VOSURI(nodeB.getUri() + "/C"));
            response = put(VOSBaseTest.NODE_ENDPOINT, nodeC, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());
            
            // Child link node link2B
            LinkNode link2B = new LinkNode(new VOSURI(nodeA.getUri() + "/link2B"), nodeB.getUri().getURIObject());
            response = put(VOSBaseTest.NODE_ENDPOINT, link2B, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());
            
            // Child link node link2C
            LinkNode link2C = new LinkNode(new VOSURI(nodeA.getUri() + "/link2C"), nodeC.getUri().getURIObject());
            response = put(VOSBaseTest.NODE_ENDPOINT, link2C, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());
            
            // Child link node link2www
            LinkNode link2www = new LinkNode(new VOSURI(nodeA.getUri() + "/link2www"), new URI("http://localhost"));
            response = put(VOSBaseTest.NODE_ENDPOINT, link2www, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Get and validate data node C
            response = get(VOSBaseTest.NODE_ENDPOINT, nodeC);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());
            String xml = response.getText();
            log.debug("GET XML:\r\n" + xml);
            NodeReader reader = new NodeReader();
            reader.read(xml);
            
            // Get and validate link node link2B
            response = get(VOSBaseTest.NODE_ENDPOINT, link2B);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());
            xml = response.getText();
            log.debug("GET XML:\r\n" + xml);
            reader.read(xml);
            
            // Get and validate link node link2C
            response = get(VOSBaseTest.NODE_ENDPOINT, link2C);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());
            xml = response.getText();
            log.debug("GET XML:\r\n" + xml);
            reader.read(xml);

            // Get and validate link node path /A/link2B/C
            DataNode alink2BC = new DataNode(new VOSURI(nodeA.getUri().toString() + "/link2B/C"));
            response = get(VOSBaseTest.NODE_ENDPOINT, alink2BC);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());
            xml = response.getText();
            log.debug("GET XML:\r\n" + xml);
            reader.read(xml);
            
            // Get and validate link node link2C
            response = get(VOSBaseTest.NODE_ENDPOINT, link2www);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());
            xml = response.getText();
            log.debug("GET XML:\r\n" + xml);
            reader.read(xml);

            // Delete the nodes
            response = delete(nodeA);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("getLinkNode passed.");
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
    public void getMinLinkNode()
    {
        try
        {
            log.debug("getMinLinkNode");

            // Get a LinkNode.
            LinkNode node = getSampleLinkNode();

            // Add LinkNode to the VOSpace.
            WebResponse response = put(node);
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Request Parameters
            Map<String, String> parameters = new HashMap<String, String>();
            parameters.put("detail", "min");

            // Get the node from vospace
            response = get(node, parameters);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());

            // Get the response (an XML document)
            String xml = response.getText();
            log.debug("GET XML:\r\n" + xml);

            // Validate against the VOSPace schema.
            NodeReader reader = new NodeReader();
            LinkNode validatedNode = (LinkNode) reader.read(xml);

            // Node properties should be empty.
            assertEquals("Node properties should be empty", 0, validatedNode.getProperties().size());

            // Node child nodes should be empty.
//            assertEquals("Node child list should be empty", 0, validatedNode.getNodes().size());

            // Delete the node
            response = delete(node);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("getMinLinkNode passed.");
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
    public void getMaxLinkNode()
    {
        try
        {
            log.debug("getMaxLinkNode");

            // Get a LinkNode.
            LinkNode node = getSampleLinkNode();

            // Add LinkNode to the VOSpace.
            WebResponse response = put(node);
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Request Parameters
            Map<String, String> parameters = new HashMap<String, String>();
            parameters.put("detail", "max");

            // Get the node from vospace
            response = get(node, parameters);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());

            // Get the response (an XML document)
            String xml = response.getText();
            log.debug("GET XML:\r\n" + xml);

            // Validate against the VOSPace schema.
            NodeReader reader = new NodeReader();
            LinkNode validatedNode = (LinkNode) reader.read(xml);

            // Node properties should be empty.
            assertEquals("Node properties should have a single property", 1, validatedNode.getProperties().size());

            // Delete the node
            response = delete(node);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("getMaxLinkNode passed.");
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
    public void getPropertiesLinkNode()
    {
        try
        {
            log.debug("getPropertiesLinkNode");

            // Get a LinkNode.
            LinkNode node = getSampleLinkNode();

            // Add LinkNode to the VOSpace.
            WebResponse response = put(node);
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Request Parameters
            Map<String, String> parameters = new HashMap<String, String>();
            parameters.put("detail", "properties");

            // Get the node from vospace
            response = get(node, parameters);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());

            // Get the response (an XML document)
            String xml = response.getText();
            log.debug("GET XML:\r\n" + xml);

            // Validate against the VOSPace schema.
            NodeReader reader = new NodeReader();
            LinkNode validatedNode = (LinkNode) reader.read(xml);

            // Node properties should be empty.
            assertEquals("Node properties should have a single property", 1, validatedNode.getProperties().size());

            // Delete the node
            response = delete(node);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("getPropertiesLinkNode passed.");
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
     */
    @Ignore("uri detail parameter not currently implemented")
    @Test
    public void getUriOffsetNode()
    {
        try
        {
            log.debug("getUriOffsetNode");

            // Get a LinkNode.
            LinkNode node = getSampleLinkNode();

            // Add DataNode to the VOSpace.
            WebResponse response = put(node);
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Request Parameters to get the node plus an offset
            Map<String, String> parameters = new HashMap<String, String>();
            parameters.put("uri", node.getUri().toString());
            parameters.put("offset", "1");

            // Get the node from vospace
            response = get(node, parameters);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());

            // TODO: What response will be returned for parameters that can't
            //       be applied to a DataNode?
            // Get the response (an XML document)
            String xml = response.getText();
            log.debug("GET XML:\r\n" + xml);

            // Validate against the VOSPace schema.
            NodeReader reader = new NodeReader();
            reader.read(xml);

            // Delete the node
            response = delete(node);
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
    public void getLimitLinkNode()
    {
        try
        {
            log.debug("getLimitLinkNode");

            // Get a LinkNode.
            LinkNode node = getSampleLinkNode();

            // Add LinkNode to the VOSpace.
            WebResponse response = put(node);
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Request Parameters
            Map<String, String> parameters = new HashMap<String, String>();
            parameters.put("limit", "9");

            // Get the node from vospace
            response = get(node, parameters);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());

            // Get the response (an XML document)
            String xml = response.getText();
            log.debug("GET XML:\r\n" + xml);

            // Validate against the VOSpace schema.
            NodeReader reader = new NodeReader();
            LinkNode validatedNode = (LinkNode) reader.read(xml);

            // Node properties should be empty.
            assertEquals("Node properties should have a single property", 1, validatedNode.getProperties().size());

            // TODO validate that there are X number of child nodes in the target node.
            
            // Delete the node
            response = delete(node);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("getLimitLinkNode passed.");
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

            // Get a LinkNode.
            LinkNode node = getSampleLinkNode();
            
            // Add LinkNode to the VOSpace.
            WebResponse response = put(node);
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // TODO: how to get the node without permission to do so?
            response = get(node);
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
            LinkNode node = getSampleLinkNode("/A/B", new URI("http://www.google.com"));

            // Try and get the Node from the VOSpace.
            WebResponse response = get(node);
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
