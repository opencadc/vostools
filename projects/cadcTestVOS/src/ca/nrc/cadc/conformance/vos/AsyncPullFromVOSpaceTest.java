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
import static org.junit.Assert.fail;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.vos.Direction;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.Protocol;
import ca.nrc.cadc.vos.Transfer;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.View;

import com.meterware.httpunit.WebResponse;

/**
 * Test case for reading data from a service (pullFromVoSpace).
 *
 * @author jburke
 */
public class AsyncPullFromVOSpaceTest extends VOSTransferTest
{
    private static Logger log = Logger.getLogger(AsyncPullFromVOSpaceTest.class);

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.conformance.vos", Level.INFO);
    }
    
    public AsyncPullFromVOSpaceTest()
    {
        super(ASYNC_TRANSFER_ENDPOINT);
    }

    /**
     * Test currently depends on the transferDetails being in the Job Results 
     * as soon as the Job is put in the EXECUTING phase. If the Job is set to
     * executing, and afterwards the Job Results are updated with the
     * transferDetails URI, the test may fail.
     */
    @Test
    public void testPullFromVOSpace()
    {
        try
        {
            log.debug("testPullFromVOSpace");

            // Get a DataNode.
            TestNode testNode = getSampleDataNode();
            testNode.sampleNode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, new Long(1024).toString()));
            WebResponse response = put(VOSBaseTest.NODE_ENDPOINT,testNode.sampleNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Request the Transfer.
            View view = new View(new URI(VOS.VIEW_DEFAULT));
            List<Protocol> protocols = new ArrayList<Protocol>();
            protocols.add(new Protocol(VOS.PROTOCOL_HTTP_GET));
            protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_GET));
            Transfer transfer = new Transfer(testNode.sampleNode.getUri(), Direction.pullFromVoSpace, view, protocols);

            // Start the transfer.
            TransferResult result = doAsyncTransfer(transfer);

            // Check if the Job was executing.
            ExecutionPhase phase = result.job.getExecutionPhase();
            if (phase == ExecutionPhase.EXECUTING)
            {
                // Get the Transfer endpoint and make sure it's a valid URL.
                try
                {
                    new URL(result.transfer.getEndpoint(VOS.PROTOCOL_HTTPS_GET));
                }
                catch (MalformedURLException e)
                {
                    fail("Invalid URL returned " + result.transfer.getEndpoint(VOS.PROTOCOL_HTTPS_GET));
                }
            }
            else
            {
                fail("Unexpected phase " + phase.name());
            }

            // Delete the node
            response = delete(VOSBaseTest.NODE_ENDPOINT, testNode.sampleNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("testPullFromVOSpace passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception: " + unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testPullLinkNodeFromVOSpace()
    {
        try
        {
            log.debug("testPullLinkNodeFromVOSpace");

            if (!resolveTargetNode)
            {
                log.debug("Resolving target LinkNodes not supported, skipping test.");
                return;
            }

            // Get a DataNode.
            TestNode testNode = getSampleDataNode();
            testNode.sampleNode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, new Long(1024).toString()));
            WebResponse response = put(VOSBaseTest.NODE_ENDPOINT,testNode.sampleNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Create a Transfer.
            View view = new View(new URI(VOS.VIEW_DEFAULT));
            List<Protocol> protocols = new ArrayList<Protocol>();
            protocols.add(new Protocol(VOS.PROTOCOL_HTTP_GET));
            protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_GET));
            Transfer transfer = new Transfer(testNode.sampleNodeWithLink.getUri(), Direction.pullFromVoSpace, view, protocols);

            // Start the transfer.
            TransferResult result = doAsyncTransfer(transfer);

            // Check if the Job was executing.
            ExecutionPhase phase = result.job.getExecutionPhase();
            if (phase == ExecutionPhase.EXECUTING)
            {
                // Get the Transfer endpoint and make sure it's a valid URL.
                try
                {
                    new URL(result.transfer.getEndpoint(VOS.PROTOCOL_HTTP_GET));
                }
                catch (MalformedURLException e)
                {
                    fail("Invalid URL returned " + result.transfer.getEndpoint(VOS.PROTOCOL_HTTP_GET));
                }
            }
            else
            {
                fail("Unexpected phase " + phase.name());
            }

            // Delete the node
            response = delete(VOSBaseTest.NODE_ENDPOINT, testNode.sampleNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("testPullLinkNodeFromVOSpace passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception: " + unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testPullFromVOSpaceWithLinkNodeInPath()
    {
        try
        {
            log.debug("testPullLinkNodeFromVOSpace");

            if (!resolvePathNodes)
            {
                log.debug("Resolving path LinkNodes not supported, skipping test.");
                return;
            }

            // Get a DataNode.
            TestNode testNode = getSampleDataNode();
            testNode.sampleNode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, new Long(1024).toString()));
            WebResponse response = put(VOSBaseTest.NODE_ENDPOINT,testNode.sampleNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Create a Transfer.
            View view = new View(new URI(VOS.VIEW_DEFAULT));
            List<Protocol> protocols = new ArrayList<Protocol>();
            protocols.add(new Protocol(VOS.PROTOCOL_HTTP_GET));
            protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_GET));
            Transfer transfer = new Transfer(testNode.sampleNodeWithLink.getUri(), Direction.pullFromVoSpace, view, protocols);

            // Start the transfer.
            TransferResult result = doAsyncTransfer(transfer);

            // Check if the Job was executing.
            ExecutionPhase phase = result.job.getExecutionPhase();
            if (phase == ExecutionPhase.EXECUTING)
            {
                // Get the Transfer endpoint and make sure it's a valid URL.
                try
                {
                    new URL(result.transfer.getEndpoint(VOS.PROTOCOL_HTTP_GET));
                }
                catch (MalformedURLException e)
                {
                    fail("Invalid URL returned " + result.transfer.getEndpoint(VOS.PROTOCOL_HTTP_GET));
                }
            }
            else
            {
                fail("Unexpected phase " + phase.name());
            }
            
            // Delete the node
            response = delete(VOSBaseTest.NODE_ENDPOINT, testNode.sampleNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("testPullLinkNodeFromVOSpace passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Ignore("not implemented")
    @Test
    public void permissionDeniedFault()
    {
        try
        {
            log.debug("permissionDeniedFault");

            fail("not implemented");

            log.info("permissionDeniedFault passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception: " + unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Ignore("node not found fault not implemented")
    @Test
    public void NodeNotFoundFault()
    {
        try
        {
            log.debug("NodeNotFoundFault");

            // Get a DataNode, don't persist.
            TestNode dataNode = getSampleDataNode();
            dataNode.sampleNode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, new Long(1024).toString()));

            // Create a Transfer.
            List<Protocol> protocols = new ArrayList<Protocol>();
            protocols.add(new Protocol(VOS.PROTOCOL_HTTP_PUT));
            Transfer transfer = new Transfer(dataNode.sampleNode.getUri(), Direction.pullFromVoSpace, null, protocols);

            // Start the transfer.
            TransferResult result = doAsyncTransfer(transfer);

            // Job should be in ERROR phase.
            ExecutionPhase phase = result.job.getExecutionPhase();
            if (phase == ExecutionPhase.ERROR)
            {
                // ErrorSummary should be 'Node Not Found'.
                assertEquals("Node Not Found", result.job.getErrorSummary().getSummaryMessage());
            }
            else
            {
                fail("Job phase should be ERROR, unexpected phase " + phase.name());
            }

            log.info("NodeNotFoundFault passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception: " + unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Ignore("accepts/provides views not implemented")
    @Test
    public void viewNotSupportedFault()
    {
        try
        {
            log.debug("viewNotSupportedFault");

            // Get a DataNode.
            TestNode dataNode = getSampleDataNode();
            dataNode.sampleNode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, new Long(1024).toString()));
            WebResponse response = put(VOSBaseTest.NODE_ENDPOINT,dataNode.sampleNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Create a Transfer.
            View view = new View(new URI("ivo://cadc.nrc.ca/vospace/view#bogus"));
            List<Protocol> protocols = new ArrayList<Protocol>();
            protocols.add(new Protocol(VOS.PROTOCOL_HTTP_PUT));
            Transfer transfer = new Transfer(dataNode.sampleNode.getUri(), Direction.pullFromVoSpace, view, protocols);

            // Start the transfer.
            TransferResult result = doAsyncTransfer(transfer);

            // Job should be in ERROR phase.
            ExecutionPhase phase = result.job.getExecutionPhase();
            if (phase == ExecutionPhase.ERROR)
            {
                // ErrorSummary should be 'View Not Supported'.
                assertEquals("View Not Supported", result.job.getErrorSummary().getSummaryMessage());
            }
            else
            {
                fail("Job phase should be ERROR, unexpected phase " + phase.name());
            }

            // Delete the node
            response = delete(VOSBaseTest.NODE_ENDPOINT, dataNode.sampleNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("viewNotSupportedFault passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception: " + unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Ignore("accepts/provides views not implemented")
    @Test
    public void protocolNotSupportedFault()
    {
        try
        {
            log.debug("protocolNotSupportedFault");

            // Get a DataNode.
            TestNode dataNode = getSampleDataNode();
            dataNode.sampleNode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, new Long(1024).toString()));
            WebResponse response = put(VOSBaseTest.NODE_ENDPOINT,dataNode.sampleNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Create a Transfer.
            List<Protocol> protocols = new ArrayList<Protocol>();
            protocols.add(new Protocol("http://localhost/path"));
            Transfer transfer = new Transfer(dataNode.sampleNode.getUri(), Direction.pullFromVoSpace, null, protocols);

            // Start the transfer.
            TransferResult result = doAsyncTransfer(transfer);

            // Job should be in ERROR phase.
            ExecutionPhase phase = result.job.getExecutionPhase();
            if (phase == ExecutionPhase.ERROR)
            {
                // ErrorSummary should be 'Protocol Not Supported'.
                    assertEquals("Protocol Not Supported", result.job.getErrorSummary().getSummaryMessage());
            }
            else
            {
                fail("Job phase should be ERROR, unexpected phase " + phase.name());
            }

            // Delete the node
            response = delete(VOSBaseTest.NODE_ENDPOINT, dataNode.sampleNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("protocolNotSupportedFault passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception: " + unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Ignore("accepts/provides views not implemented")
    @Test
    public void invalidViewParameterFault()
    {
        try
        {
            log.debug("invalidViewParameterFault");

            fail("accepts/provides views not implemented");

            log.info("invalidViewParameterFault passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception: " + unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Ignore("accepts/provides views not implemented")
    @Test
    public void invalidProtocolParameterFault()
    {
        try
        {
            log.debug("invalidProtocolParameterFault");

            fail("accepts/provides views not implemented");

            log.info("invalidProtocolParameterFault passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception: " + unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

}
