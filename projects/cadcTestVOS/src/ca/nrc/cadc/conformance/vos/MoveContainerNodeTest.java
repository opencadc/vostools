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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.matchers.JUnitMatchers;

import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.uws.ErrorSummary;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.Transfer;
import ca.nrc.cadc.vos.VOSURI;

import com.meterware.httpunit.WebResponse;

/**
 * Test case for moving ContainerNodes.
 *
 * @author jburke
 */
public class MoveContainerNodeTest extends VOSTransferTest
{
    private static Logger log = Logger.getLogger(MoveContainerNodeTest.class);

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.conformance.vos", Level.INFO);
    }
    
    public MoveContainerNodeTest()
    {
        super(ASYNC_TRANSFER_ENDPOINT);
    }
    
    /**
     * When the source is a ContainerNode, all its children 
     * (the full contents of the container) SHALL get copied, 
     * i.e. this is a deep recursive copy.
     */
    @Test
    public void moveContainerNodeToContainerNode()
    {
        try
        {
            log.debug("moveContainerNodeToContainerNode");

            // Target ContainerNode A.
            TestNode targetNode = getSampleContainerNode("A");

            // Add ContainerNode to the VOSpace.
            WebResponse response = put(VOSBaseTest.NODE_ENDPOINT, targetNode.sampleNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Child container node B.
            ContainerNode nodeAB = new ContainerNode(new VOSURI(targetNode.sampleNode.getUri() + "/B"));
            response = put(VOSBaseTest.NODE_ENDPOINT, nodeAB, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Child data node C.
            DataNode nodeABC = new DataNode(new VOSURI(nodeAB.getUri() + "/C"));
            response = put(VOSBaseTest.NODE_ENDPOINT, nodeABC, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());
            
            // Get a destination ContainerNode Z.
            TestNode destinationNode = getSampleContainerNode("Z");
            response = put(VOSBaseTest.NODE_ENDPOINT, destinationNode.sampleNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Do the move.
            Transfer transfer = new Transfer(targetNode.sampleNode.getUri(), destinationNode.sampleNode.getUri(), false);
            TransferResult result = doTransfer(transfer);
            
            // Wait for job to complete
            Thread.sleep(5000);
            
            // Phase should be COMPLETED
            assertEquals("Phase should be COMPLETED", ExecutionPhase.COMPLETED, result.job.getExecutionPhase());

            // Check node has been moved and old node gone
            response = get(VOSBaseTest.NODE_ENDPOINT, nodeAB);
            assertEquals("GET response code should be 404", 404, response.getResponseCode());

            response = get(VOSBaseTest.NODE_ENDPOINT, targetNode.sampleNode);
            assertEquals("GET response code should be 200", 404, response.getResponseCode());
            
            // Get the moved nodes.
            response = get(VOSBaseTest.NODE_ENDPOINT, destinationNode.sampleNode);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());
            
            ContainerNode nodeA = new ContainerNode(new VOSURI(destinationNode.sampleNode.getUri().toString() + "/" + targetNode.sampleNode.getName()));
            response = get(VOSBaseTest.NODE_ENDPOINT, nodeA);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());
            
            ContainerNode nodeB = new ContainerNode(new VOSURI(nodeA.getUri().toString() + "/B"));
            response = get(VOSBaseTest.NODE_ENDPOINT, nodeB);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());
            
            DataNode nodeC = new DataNode(new VOSURI(nodeB.getUri().toString() + "/C"));
            response = get(VOSBaseTest.NODE_ENDPOINT, nodeC);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());

            // Delete the node.
            response = delete(VOSBaseTest.NODE_ENDPOINT, destinationNode.sampleNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("moveContainerNodeToContainerNode passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    /**
     * When the source is a ContainerNode, all its children
     * (the full contents of the container) SHALL get copied,
     * i.e. this is a deep recursive copy.
     */
    @Test
    public void moveContainerNodeToContainerNodeUsingLinkNodes()
    {
        try
        {
            log.debug("moveContainerNodeToContainerNodeUsingLinkNodes");

            if (!supportLinkNodes)
            {
                log.debug("LinkNodes not supported, skipping test.");
                return;
            }

            // Target ContainerNode A.
            TestNode targetNode = getSampleContainerNode("A");

            // Add ContainerNode to the VOSpace.
            WebResponse response = put(VOSBaseTest.NODE_ENDPOINT, targetNode.sampleNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Child container node B.
            ContainerNode nodeAB = new ContainerNode(new VOSURI(targetNode.sampleNode.getUri() + "/B"));
            response = put(VOSBaseTest.NODE_ENDPOINT, nodeAB, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Child data node C.
            DataNode nodeABC = new DataNode(new VOSURI(nodeAB.getUri() + "/C"));
            response = put(VOSBaseTest.NODE_ENDPOINT, nodeABC, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Get a destination ContainerNode Z.
            TestNode destinationNode = getSampleContainerNode("Z");
            response = put(VOSBaseTest.NODE_ENDPOINT, destinationNode.sampleNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Do the move without LinkNodes in the paths.
            Transfer transfer = new Transfer(targetNode.sampleNodeWithLink.getUri(), destinationNode.sampleNodeWithLink.getUri(), false);
            TransferResult result = doTransfer(transfer);
            
            // Wait for job to complete
            Thread.sleep(5000);

            // If the service supports LinkNodes and it resolves parent LinkNodes.
            if (resolvePathNodes)
            {
                // Phase should be COMPLETED
                assertEquals("Phase should be COMPLETED", ExecutionPhase.COMPLETED, result.job.getExecutionPhase());

                // Check node has been moved and old node gone
                response = get(VOSBaseTest.NODE_ENDPOINT, nodeAB);
                assertEquals("GET response code should be 404", 404, response.getResponseCode());

                response = get(VOSBaseTest.NODE_ENDPOINT, targetNode.sampleNodeWithLink);
                assertEquals("GET response code should be 200", 404, response.getResponseCode());

                // Get the moved nodes.
                response = get(VOSBaseTest.NODE_ENDPOINT, destinationNode.sampleNodeWithLink);
                assertEquals("GET response code should be 200", 200, response.getResponseCode());

                ContainerNode nodeA = new ContainerNode(new VOSURI(destinationNode.sampleNodeWithLink.getUri().toString() + "/" + targetNode.sampleNodeWithLink.getName()));
                response = get(VOSBaseTest.NODE_ENDPOINT, nodeA);
                assertEquals("GET response code should be 200", 200, response.getResponseCode());

                ContainerNode nodeB = new ContainerNode(new VOSURI(nodeA.getUri().toString() + "/B"));
                response = get(VOSBaseTest.NODE_ENDPOINT, nodeB);
                assertEquals("GET response code should be 200", 200, response.getResponseCode());

                DataNode nodeC = new DataNode(new VOSURI(nodeB.getUri().toString() + "/C"));
                response = get(VOSBaseTest.NODE_ENDPOINT, nodeC);
                assertEquals("GET response code should be 200", 200, response.getResponseCode());
            }
            else
            {
                // Phase should be ERROR
                assertEquals("Phase should be ERROR", ExecutionPhase.ERROR, result.job.getExecutionPhase());

                // Get the ErrorSummary.
                ErrorSummary errorSummary = result.job.getErrorSummary();
                String message = errorSummary.getSummaryMessage();
                // TOO what should be the message?
//                assertEquals("ErrorSummary message should be Duplicate Node", "Duplicate Node", message);
            }

            // Delete the node.
            response = delete(VOSBaseTest.NODE_ENDPOINT, destinationNode.sampleNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("moveContainerNodeToContainerNodeUsingLinkNodes passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    /**
     * User does not have permissions to perform the operation.
     * errorSummary Permission Denied
     * Fault representation PermissionDenied
     */
    @Ignore("Currently unable to test")
    @Test
    public void permissionDeniedFault()
    {
        try
        {
            log.debug("permissionDeniedFault");

            // Target ContainerNode A.
            TestNode targetNode = getSampleContainerNode("A");

            // Add ContainerNode to the VOSpace.
            WebResponse response = put(VOSBaseTest.NODE_ENDPOINT, targetNode.sampleNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Get a destination ContainerNode.
            TestNode destinationNode = getSampleContainerNode();
            response = put(VOSBaseTest.NODE_ENDPOINT, destinationNode.sampleNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Do the move.
            Transfer transfer = new Transfer(targetNode.sampleNode.getUri(), destinationNode.sampleNode.getUri(), false);
            TransferResult result = doTransfer(transfer);

            // Phase should be ERROR.
            assertEquals("Phase should be ERROR", ExecutionPhase.ERROR, result.job.getExecutionPhase());
            
            // TODO: Modify UWS to allow separate error summary and text representation fields
            //       (summary with spaces, representation without)
            // This assertion is commented out until then.
            // Get the ErrorSummary.
            ErrorSummary errorSummary = result.job.getErrorSummary();
            String message = errorSummary.getSummaryMessage();
            //assertEquals("ErrorSummary message should be Permission Denied", "Permission Denied", message);
            assertEquals("ErrorSummary message should be PermissionDenied", "PermissionDenied", message);
            
            // Get the error endpoint.
            response = get(result.location + "/error");
            assertEquals("GET response code should be 200", 200, response.getResponseCode());
            
            // Error should contain 'PermissionDenied'
            assertThat(response.getText().trim(), JUnitMatchers.containsString("PermissionDenied"));
            
            // Delete the nodes
            response = delete(VOSBaseTest.NODE_ENDPOINT, targetNode.sampleNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());
            response = delete(VOSBaseTest.NODE_ENDPOINT, destinationNode.sampleNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("permissionDeniedFault passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception: " + unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
    /**
     * Source node does not exist
     * errorSummary Node Not Found
     * Fault representation NodeNotFound
     */
    @Test
    public void containerNotFoundFault()
    {
        try
        {
            log.debug("containerNotFoundFault");

            // Target ContainerNode A, don't persist.
            TestNode targetNode = getSampleContainerNode("A");

            // Get a destination ContainerNode.
            TestNode destinationNode = getSampleContainerNode("Z");
            WebResponse response = put(VOSBaseTest.NODE_ENDPOINT, destinationNode.sampleNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Do the move.
            Transfer transfer = new Transfer(targetNode.sampleNode.getUri(), destinationNode.sampleNode.getUri(), false);
            TransferResult result = doTransfer(transfer);

            // Phase should be ERROR.
            assertEquals("Phase should be ERROR", ExecutionPhase.ERROR, result.job.getExecutionPhase());
            
            // TODO: Modify UWS to allow separate error summary and text representation fields
            //       (summary with spaces, representation without)
            // This assertion is commented out until then.
            // Get the ErrorSummary.
            ErrorSummary errorSummary = result.job.getErrorSummary();
            String message = errorSummary.getSummaryMessage();
            //assertEquals("ErrorSummary message should be Node Not Found", "Node Not Found", message);
            assertEquals("ErrorSummary message should be NodeNotFound", "NodeNotFound", message);
            
            // Get the error endpoint.
            response = get(result.location + "/error");
            assertEquals("GET response code should be 200", 200, response.getResponseCode());
            
            // Error should contain 'NodeNotFound'
            assertThat(response.getText().trim(), JUnitMatchers.containsString("NodeNotFound"));
            
            // Delete the nodes
            response = delete(VOSBaseTest.NODE_ENDPOINT, destinationNode.sampleNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("containerNotFoundFault passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception: " + unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
    /**
     * A specified URI is invalid
     * errorSummary Invalid URI
     * Fault representation InvalidURI
     */
    @Ignore("Currently unable to test")
    @Test
    public void invalidURI()
    {
        try
        {
            log.debug("invalidURI");

            // Target ContainerNode A.
            TestNode targetNode = getSampleContainerNode("A");

            // Add ContainerNode to the VOSpace.
            WebResponse response = put(VOSBaseTest.NODE_ENDPOINT, targetNode.sampleNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Get a destination ContainerNode.
            TestNode destinationNode = getSampleContainerNode();
            response = put(VOSBaseTest.NODE_ENDPOINT, destinationNode.sampleNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Do the move.
            Transfer transfer = new Transfer(targetNode.sampleNode.getUri(), destinationNode.sampleNode.getUri(), false);
            TransferResult result = doTransfer(transfer);

            // Phase should be ERROR.
            assertEquals("Phase should be ERROR", ExecutionPhase.ERROR, result.job.getExecutionPhase());
            
            // TODO: Modify UWS to allow separate error summary and text representation fields
            //       (summary with spaces, representation without)
            // This assertion is commented out until then.
            // Get the ErrorSummary.
            ErrorSummary errorSummary = result.job.getErrorSummary();
            String message = errorSummary.getSummaryMessage();
            //assertEquals("ErrorSummary message should be Duplicate Node", "Duplicate Node", message);
            assertEquals("ErrorSummary message should be DuplicateNode", "DuplicateNode", message);
            
            // Get the error endpoint.
            response = get(result.location + "/error");
            assertEquals("GET response code should be 200", 200, response.getResponseCode());
            
            // Error should contain 'DuplicateNode'
            assertThat(response.getText().trim(), JUnitMatchers.containsString("DuplicateNode"));
            
            // Delete the nodes
            response = delete(VOSBaseTest.NODE_ENDPOINT, targetNode.sampleNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());
            response = delete(VOSBaseTest.NODE_ENDPOINT, destinationNode.sampleNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("invalidURI passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception: " + unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
}
