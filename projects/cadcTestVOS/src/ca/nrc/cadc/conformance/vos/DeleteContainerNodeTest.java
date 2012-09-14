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
import ca.nrc.cadc.vos.LinkNode;
import com.meterware.httpunit.WebResponse;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import static org.junit.Assert.*;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.matchers.JUnitMatchers;

/**
 * Test case for deleting ContainerNodes.
 *
 * @author jburke
 */
public class DeleteContainerNodeTest extends VOSNodeTest
{
    private static Logger log = Logger.getLogger(DeleteContainerNodeTest.class);

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.conformance.vos", Level.INFO);
    }
    
    public DeleteContainerNodeTest()
    {
        super();
    }

    @Test
    public void deleteContainerNode()
    {
        try
        {
            log.debug("deleteContainerNode");

            // Get a ContainerNode.
            TestNode node = getSampleContainerNode();

            // Add ContainerNode to the VOSpace.
            WebResponse response = put(node.sampleNode);
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Delete the node.
            response = delete(node.sampleNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            // Try and get the node from vospace
            response = get(node.sampleNode);
            assertEquals("GET response code should be 404 for a node that doesn't exist", 404, response.getResponseCode());
            
            log.info("deleteContainerNode passed.");
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
    @Ignore("Currently unable to test")
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

            // TODO: how do you delete a node without permissions?
            response = delete(node.sampleNode);
            assertEquals("DELETE response code should be 401", 401, response.getResponseCode());
        
            // Response entity body should contain 'PermissionDenied'
            assertThat(response.getText().trim(), JUnitMatchers.containsString("PermissionDenied"));
            
            // Check that the node wasn't created
            response = get(node.sampleNode);
            assertEquals("GET response code should be 404 for a deleted node", 404, response.getResponseCode());

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

            // Create a Node that should not exist.
            TestNode node = getSampleContainerNode();

            // Try and delete the Node from the VOSpace.
            WebResponse response = delete(node.sampleNode);
            assertEquals("DELETE response code should be 404 for a node that doesn't exist", 404, response.getResponseCode());

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
    
    /**
     * If a parent node in the URI path does not exist then the service 
     * MUST throw a HTTP 404 status code including a ContainerNotFound 
     * fault in the entity-body
     */
    @Test
    public void containerNotFoundFault()
    {
        try
        {
            log.debug("containerNotFoundFault");

            // Create a Node path /A/B
            TestNode node = getSampleContainerNode("/A/B");

            // Try and delete the Node from the VOSpace.
            WebResponse response = delete(node.sampleNode);
            assertEquals("DELETE response code should be 404 for a invalid Node path", 404, response.getResponseCode());

            // Response entity body should contain 'ContainerNotFound'
            assertThat(response.getText().trim(), JUnitMatchers.containsString("ContainerNotFound"));
            
            log.info("containerNotFoundFault passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception: " + unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    /**
     * If a parent node in the URI path is a LinkNode, the service MUST 
     * throw a HTTP 400 status code including a LinkFound fault in the entity-body.
     */
//    @Ignore("Currently not supported")
    @Test
    public void linkFoundFault()
    {
        try
        {
            log.debug("linkFoundFault");

            if (supportLinkNodes)
            {
                log.debug("LinkNodes not supported, skipping test.");
                return;
            }

            // Get a ContainerNode.
            TestNode node = getSampleContainerNode();
            
            // Add ContainerNode as target to a LinkNode.
            LinkNode linkNode = getSampleLinkNode("", node.sampleNode.getUri().getURIObject());

            // Try and delete the Node from the VOSpace.
            WebResponse response = delete(node.sampleNode);
            assertEquals("DELETE response code should be 400 for a LinkNode in the target node path", 400, response.getResponseCode());

            // Response entity body should contain 'LinkFound'
            assertThat(response.getText().trim(), JUnitMatchers.containsString("LinkFound"));
            
            log.info("linkFoundFault passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception: " + unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
}
