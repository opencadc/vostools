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

import ca.nrc.cadc.vos.VOS;
import java.util.List;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.NodeReader;
import ca.nrc.cadc.vos.VOSURI;
import com.meterware.httpunit.WebResponse;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test case for updating ContainerNodes.
 *
 * @author jburke
 */
public class UpdateContainerNodeTest extends VOSNodeTest
{
    private static Logger log = Logger.getLogger(UpdateContainerNodeTest.class);

    public UpdateContainerNodeTest()
    {
        super();
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
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void updateContainerNode()
    {
        try
        {
            log.debug("updateContainerNode");

            // Get a ContainerNode.
            ContainerNode node = getSampleContainerNode();
            
            // Add ContainerNode to the VOSpace.
            WebResponse response = put(node);
            assertEquals("PUT response code should be 201", 201, response.getResponseCode());

            // Update the node by adding new Property.
            NodeProperty nodeProperty = new NodeProperty("ivo://ivoa.net/vospace/core#description", "My new award winning images");
            nodeProperty.setReadOnly(true);
            node.getProperties().add(nodeProperty);
            response = post(node);
            assertEquals("POST response code should be 200", 200, response.getResponseCode());

            // Get the response (an XML document)
            String xml = response.getText();
            log.debug("POST XML:\r\n" + xml);

            // Validate against the VOSPace schema.
            NodeReader reader = new NodeReader();
            ContainerNode updatedNode = (ContainerNode) reader.read(xml);

            // Updated node should have 2 properties.
            assertEquals("", 2, updatedNode.getProperties().size());

            // Delete the node
            response = delete(node);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("updateContainerNode passed.");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    /**
     * To delete a Property, set the xsi:nil attribute to true
     */
    @Test
    public void updateContainerNodeDeleteProperty()
    {
        try
        {
            log.debug("updateContainerNodeValue");

            // Create a ContainerNode.
            ContainerNode node = new ContainerNode(new VOSURI(VOS.VOS_URI + "/A"));
            NodeProperty nodeProperty = new NodeProperty("ivo://ivoa.net/vospace/core#description", "My award winning images");
            nodeProperty.setReadOnly(true);
            node.getProperties().add(nodeProperty);

            // Add ContainerNode to the VOSpace.
            WebResponse response = put(node);
            assertEquals("PUT response code should be 201", 201, response.getResponseCode());

            // Get the response (an XML document)
            String xml = response.getText();
            log.debug("POST XML:\r\n" + xml);

            // Validate against the VOSPace schema.
            NodeReader reader = new NodeReader();
            ContainerNode updatedNode = (ContainerNode) reader.read(xml);

            // Mark the property as deleted.
            List<NodeProperty> properties = updatedNode.getProperties();
            for (NodeProperty property : properties)
                property.setMarkedForDeletion(true);
            
            response = post(updatedNode);
            assertEquals("POST response code should be 200", 200, response.getResponseCode());

            // Get the response (an XML document)
            xml = response.getText();
            log.debug("POST XML:\r\n" + xml);

            // Validate against the VOSPace schema.
            reader.read(xml);

            // Node properties should be empty.
            assertEquals("Node proerties should be empty", 0, updatedNode.getProperties().size());

            // Delete the node
            response = delete(updatedNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("updateContainerNodeValue passed.");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    /**
     * The service SHALL throw a HTTP 401 status code including a PermissionDenied
     * fault in the entity-body if the user does not have permissions to perform the operation
     */
//    @Test
    public void permissionDeniedFault()
    {
        try
        {
            log.debug("permissionDeniedFault");

            // Get a ContainerNode.
            ContainerNode node = getSampleContainerNode();

            // Add ContainerNode to the VOSpace.
            WebResponse response = put(node);
            assertEquals("PUT response code should be 201", 201, response.getResponseCode());

            // Update the node by adding new Property.
            NodeProperty nodeProperty = new NodeProperty("ivo://ivoa.net/vospace/core#description", "My new award winning images");
            nodeProperty.setReadOnly(true);
            node.getProperties().add(nodeProperty);

            // TODO: how to update a node without permissions?
            response = post(node);
            assertEquals("POST response code should be 401", 401, response.getResponseCode());

            // Response message body should be 'PermissionDenied'
            assertEquals("Response message body should be 'PermissionDenied'", "PermissionDenied", response.getResponseMessage());

            // Delete the node
            response = delete(node);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("permissionDeniedFault passed.");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    /**
     * The service SHALL throw a HTTP 401 status code including a PermissionDenied 
     * fault in the entity-body if the request attempts to modify a readonly Property
     */
    @Test
    public void updateReadOnlyPermissionDeniedFault()
    {
        try
        {
            log.debug("updateReadOnlyPermissionDeniedFault");

            // Create a ContainerNode.
            ContainerNode node = new ContainerNode(new VOSURI(VOS.VOS_URI + "/A"));
            NodeProperty nodeProperty = new NodeProperty("ivo://ivoa.net/vospace/core#description", "My award winning images");
            nodeProperty.setReadOnly(true);
            node.getProperties().add(nodeProperty);

            // Add ContainerNode to the VOSpace.
            WebResponse response = put(node);
            assertEquals("PUT response code should be 201", 201, response.getResponseCode());

            // Get the response (an XML document)
            String xml = response.getText();
            log.debug("POST XML:\r\n" + xml);

            // Validate against the VOSPace schema.
            NodeReader reader = new NodeReader();
            ContainerNode updatedNode = (ContainerNode) reader.read(xml);

            // Update the node by updating the read only property.
            List<NodeProperty> properties = updatedNode.getProperties();
            for (NodeProperty property : properties)
                property.setReadOnly(false);

            // Update the node
            response = post(updatedNode);
            assertEquals("POST response code should be 401", 401, response.getResponseCode());

            // Response message body should be 'PermissionDenied'
            assertEquals("Response message body should be 'PermissionDenied'", "PermissionDenied", response.getResponseMessage());

            // Delete the node
            response = delete(updatedNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("updateReadOnlyPermissionDeniedFault passed.");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    /**
     * The service SHALL throw a HTTP 404 status code including a NodeNotFound fault
     * in the entity-body if the target Node does not exist
     */
    @Test
    public void nodeNotFoundFault()
    {
        try
        {
            log.debug("nodeNotFoundFault");

            // Create a Node with a nonexistent parent node
            ContainerNode nodeAB = new ContainerNode(new VOSURI(VOS.VOS_URI + "/A/B"));

            // Try and get the Node from the VOSpace.
            WebResponse response = post(nodeAB);
            assertEquals("POST response code should be 404 for a node that doesn't exist", 404, response.getResponseCode());

            // Response message body should be 'NodeNotFound'
            assertEquals("Response message body should be 'NodeNotFound'", "NodeNotFound", response.getResponseMessage());

            log.info("nodeNotFoundFault passed.");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    /**
     * The service SHALL throw a HTTP 400 status code including an InvalidArgument fault
     * in the entity-body if a specified property value is invalid
     */
    @Test
    public void invalidArgumentFault()
    {
        try
        {
            log.debug("invalidArgumentFault");

            // Get a ContainerNode.
            ContainerNode node = getSampleContainerNode();

            // Add ContainerNode to the VOSpace.
            WebResponse response = put(node);
            assertEquals("PUT response code should be 201", 201, response.getResponseCode());

            // TODO: add an invalid Property
            NodeProperty nodeProperty = new NodeProperty("zzz://aaa.bbb/ccc/ddd", "My invalid property");
            nodeProperty.setReadOnly(false);
            node.getProperties().add(nodeProperty);

            // Update the node.
            response = post(node);
            assertEquals("POST response code should be 400 for a node with an invalid property", 400, response.getResponseCode());

            // Response message body should be 'NodeNotFound'
            assertEquals("Response message body should be 'NodeNotFound'", "NodeNotFound", response.getResponseMessage());

            // Delete the node
            response = delete(node);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("invalidArgumentFault passed.");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

}
