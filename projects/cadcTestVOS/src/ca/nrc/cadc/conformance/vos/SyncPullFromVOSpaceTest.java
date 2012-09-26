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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
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
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobReader;
import ca.nrc.cadc.vos.Direction;
import ca.nrc.cadc.vos.LinkNode;
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
public class SyncPullFromVOSpaceTest extends VOSTransferTest
{
    private static Logger log = Logger.getLogger(SyncPullFromVOSpaceTest.class);

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.conformance.vos", Level.INFO);
    }
    
    public SyncPullFromVOSpaceTest()
    {
        super(SYNC_TRANSFER_ENDPOINT);
    }

    @Test
    public void testPullFromVOSpace()
    {
        try
        {
            log.debug("testPullFromVOSpace");

            // Get a DataNode.
            TestNode dataNode = getSampleDataNode();
            dataNode.sampleNode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, new Long(1024).toString()));
            WebResponse response = put(VOSBaseTest.NODE_ENDPOINT,dataNode.sampleNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Create the Transfer.
            List<Protocol> protocols = new ArrayList<Protocol>();
            protocols.add(new Protocol(VOS.PROTOCOL_HTTP_GET));
            protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_GET));
            protocols.add(new Protocol("some:unknown:proto"));
            Transfer transfer = new Transfer(dataNode.sampleNode.getUri(), Direction.pullFromVoSpace, null, protocols);

            // Start the transfer.
            TransferResult result = doSyncTransfer(transfer);
            
            assertEquals("direction", Direction.pullFromVoSpace, result.transfer.getDirection());
            assertNotNull("protocols", result.transfer.getProtocols());
            
            // Invalid Protocol should not be returned.
            assertTrue("has some protocols", !result.transfer.getProtocols().isEmpty());
            String endpoint = null;
            for (Protocol p : result.transfer.getProtocols())
            {
                try 
                {
                    endpoint = p.getEndpoint();
                    new URL(endpoint);
                    log.debug("endpoint url: " + endpoint);
                }
                catch(MalformedURLException mex)
                {
                    fail("malformed endpoint URL: " + endpoint + ", " + mex);
                }
            }
            
            // Get the Job.
            response = get(result.location);
            assertEquals("GET of Job response code should be 200", 200, response.getResponseCode());
            
            // Job phase should be EXECUTING.
            JobReader jobReader = new JobReader();
            Job job = jobReader.read(new StringReader(response.getText()));
            assertEquals("Job phase should be EXECUTING", ExecutionPhase.EXECUTING, job.getExecutionPhase());
            
            // Delete the node
            response = delete(VOSBaseTest.NODE_ENDPOINT, dataNode.sampleNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("testPullFromVOSpace passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testPullLinkNodeFromVOSpace()
    {
        try
        {
            log.debug("testPullLinkNodeFromVOSpace");

            if (!supportLinkNodes)
            {
                log.debug("LinkNodes not supported, skipping test.");
                return;
            }
            
            // Get a DataNode.
            TestNode dataNode = getSampleDataNode();
            dataNode.sampleNode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, new Long(1024).toString()));
            WebResponse response = put(VOSBaseTest.NODE_ENDPOINT,dataNode.sampleNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Create a LinkNode to the DataNode
            LinkNode linkNode = getSampleLinkNode(dataNode.sampleNode);
            response = put(VOSBaseTest.NODE_ENDPOINT, linkNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Create the Transfer.
            List<Protocol> protocols = new ArrayList<Protocol>();
            protocols.add(new Protocol(VOS.PROTOCOL_HTTP_GET));
            protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_GET));
            protocols.add(new Protocol("some:unknown:proto"));
            Transfer transfer = new Transfer(dataNode.sampleNode.getUri(), Direction.pullFromVoSpace, protocols);

            // Start the transfer.
            TransferResult result = doSyncTransfer(transfer);

            assertEquals("direction", Direction.pullFromVoSpace, result.transfer.getDirection());
            assertNotNull("protocols", result.transfer.getProtocols());
            // that bogus one should not be here, so only 0 to 2 protocols
            assertTrue("has some protocols", !result.transfer.getProtocols().isEmpty());
            for (Protocol p : result.transfer.getProtocols())
            {
                try 
                {
                    URL actualURL = new URL(p.getEndpoint());
                    log.debug("endpoint url: " + actualURL);
                    assertTrue("URL not resolved: " +  actualURL, 
                            actualURL.getPath().contains(dataNode.sampleNode.getName()));
                }
                catch(MalformedURLException mex)
                {
                    fail("malformed endpoint URL: " + p.getEndpoint() + ", " + mex);
                }
            }

            // Delete the nodes
            response = delete(VOSBaseTest.NODE_ENDPOINT, linkNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());
            response = delete(VOSBaseTest.NODE_ENDPOINT, dataNode.sampleNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("testPullLinkNodeFromVOSpace passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testSyncPullWithLinkNodeExtTarget()
    {
        try
        {
            log.debug("testSyncPullWithLinkNodeExtTarget");

            if (!supportLinkNodes)
            {
                log.debug("LinkNodes not supported, skipping test.");
                return;
            }

            // Create a LinkNode to the DataNode
            LinkNode linkNode = getSampleLinkNode(new URI("www.google.com"));
            WebResponse response = put(VOSBaseTest.NODE_ENDPOINT, linkNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Create the Transfer.
            List<Protocol> protocols = new ArrayList<Protocol>();
            protocols.add(new Protocol(VOS.PROTOCOL_HTTP_GET));
            protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_GET));
            protocols.add(new Protocol("some:unknown:proto"));
            Transfer transfer = new Transfer(linkNode.getUri(), Direction.pullFromVoSpace, null, protocols);

            // Start the transfer.
            TransferResult result = doSyncTransfer(transfer);

            assertEquals("direction", Direction.pullFromVoSpace, result.transfer.getDirection());
            boolean hasProto = (result.transfer.getProtocols() != null && !result.transfer.getProtocols().isEmpty());
            assertEquals("protocols", false, hasProto);
            
            // get job details
            log.debug("Check job details at: " + result.location);
            response = get(result.location);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());

            // read the response transfer doc
            String xml = response.getText();
            log.debug("testSyncTransfer response from POST: \n\n" + xml);

            // Create a Job from Job XML.
            JobReader jobReader = new JobReader();
            Job job = jobReader.read(new ByteArrayInputStream(xml.getBytes()));

            assertEquals("Job error", ExecutionPhase.ERROR, job.getExecutionPhase());
            log.debug("Job Error message: " + job.getErrorSummary().getSummaryMessage());
            assertTrue("Link Exception", 
                    job.getErrorSummary().getSummaryMessage().
                    contains("Link Exception"));

            // Delete the node
            response = delete(VOSBaseTest.NODE_ENDPOINT, linkNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("pullJob passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
    @Test
    public void testSyncPullWithLinkNodeExtVOSTarget()
    {
        try
        {
            // this is similar to the previous test except that the 
            // target link of the LinkNode points to an external vos 
            log.debug("testSyncPullWithLinkNodeExtVOSTarget");

            if (!supportLinkNodes)
            {
                log.debug("LinkNodes not supported, skipping test.");
                return;
            }

            // Create a LinkNode to the DataNode
            LinkNode linkNode = getSampleLinkNode(new URI("vos://some.other.vos!vospace/"));
            WebResponse response = put(VOSBaseTest.NODE_ENDPOINT, linkNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());
            
            // Create the Transfer.
            List<Protocol> protocols = new ArrayList<Protocol>();
            protocols.add(new Protocol(VOS.PROTOCOL_HTTP_GET));
            protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_GET));
            protocols.add(new Protocol("some:unknown:proto"));
            Transfer transfer = new Transfer(linkNode.getUri(), Direction.pullFromVoSpace, null, protocols);

            // Start the transfer.
            TransferResult result = doSyncTransfer(transfer);

            assertEquals("direction", Direction.pullFromVoSpace, result.transfer.getDirection());
            boolean hasProto = (result.transfer.getProtocols() != null && !result.transfer.getProtocols().isEmpty());
            assertEquals("protocols", false, hasProto);
            
            // get job details
            log.debug("Check job details at: " + result.location);
            response = get(result.location);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());

            // read the response transfer doc
            String xml = response.getText();
            log.debug("testSyncTransfer response from POST: \n\n" + xml);

            // Create a Job from Job XML.
            JobReader jobReader = new JobReader();
            Job job = jobReader.read(new ByteArrayInputStream(xml.getBytes()));

            assertEquals("Job error", ExecutionPhase.ERROR, job.getExecutionPhase());
            log.debug("Job Error message: " + job.getErrorSummary().getSummaryMessage());
            assertTrue("Link Exception", 
                    job.getErrorSummary().getSummaryMessage().
                    contains("Link Exception"));


            // Delete the node
            response = delete(VOSBaseTest.NODE_ENDPOINT, linkNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

            log.info("pullJob passed.");
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

            // Create the Transfer.
            View view = new View(new URI("ivo://cadc.nrc.ca/vospace/view#bogus"));
            List<Protocol> protocols = new ArrayList<Protocol>();
            protocols.add(new Protocol(VOS.PROTOCOL_HTTP_PUT));
            protocols.add(new Protocol(VOS.PROTOCOL_HTTP_GET));
            Transfer transfer = new Transfer(dataNode.sampleNode.getUri(), Direction.pullFromVoSpace, view, protocols);

            // Start the transfer.
            TransferResult result = doSyncTransfer(transfer);

            assertEquals("direction", Direction.pullFromVoSpace, result.transfer.getDirection());
            
            // Should be no Protocols if Job in ERROR phase.
            assertTrue("no protocols", result.transfer.getProtocols() == null || result.transfer.getProtocols().isEmpty());
            
            // Get the Job.
            response = get(result.location);
            assertEquals("GET of Job response code should be 200", 200, response.getResponseCode());
            JobReader jobReader = new JobReader();
            Job job = jobReader.read(new StringReader(response.getText()));
            
            // Job phase should be ERROR.
            assertEquals("Job phase should be ERROR", ExecutionPhase.ERROR, job.getExecutionPhase());
            
            // ErrorSummary should be 'View Not Supported'.
            // TODO: Change 'ViewNotSupported' to 'View Not Supported' when UWS supports
            // error representations
            //assertEquals("View Not Supported", job.getErrorSummary().getSummaryMessage());
            assertTrue("ViewNotSupported", job.getErrorSummary().getSummaryMessage().startsWith("ViewNotSupported"));

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

    @Ignore("node not found fault not implemented")
    @Test
    public void NodeNotFoundFault()
    {
        try
        {
            log.debug("NodeNotFoundFault");

            // Get a DataNode, don't persist.
            TestNode testNode = getSampleDataNode();
            testNode.sampleNode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, new Long(1024).toString()));
            
            // Create the Transfer.
            List<Protocol> protocols = new ArrayList<Protocol>();
            protocols.add(new Protocol(VOS.PROTOCOL_HTTP_PUT));
            Transfer transfer = new Transfer(testNode.sampleNode.getUri(), Direction.pullFromVoSpace, null, protocols);

            // Start the transfer.
            TransferResult result = doSyncTransfer(transfer);

            assertEquals("direction", Direction.pullFromVoSpace, result.transfer.getDirection());
            
            // Should be no Protocols if Job in ERROR phase.
            assertTrue(result.transfer.getProtocols().isEmpty());
            
            // Get the Job.
            WebResponse response = get(result.location);
            assertEquals("GET of Job response code should be 200", 200, response.getResponseCode());
            JobReader jobReader = new JobReader();
            Job job = jobReader.read(new StringReader(response.getText()));
            
            // Job phase should be ERROR.
            assertEquals("Job phase should be ERROR", ExecutionPhase.ERROR, job.getExecutionPhase());
            
            // ErrorSummary should be 'Node Not Found'.
            assertEquals("Node Not Found", job.getErrorSummary().getSummaryMessage());

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
    public void protocolNotSupportedFault()
    {
        try
        {
            log.debug("protocolNotSupportedFault");

            // Get a DataNode.
            TestNode testNode = getSampleDataNode();
            testNode.sampleNode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, new Long(1024).toString()));
            WebResponse response = put(VOSBaseTest.NODE_ENDPOINT,testNode.sampleNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Create the Transfer.
            List<Protocol> protocols = new ArrayList<Protocol>();
            protocols.add(new Protocol(VOS.PROTOCOL_HTTP_PUT));
            Transfer transfer = new Transfer(testNode.sampleNode.getUri(), Direction.pullFromVoSpace, null, protocols);

            // Start the transfer.
            TransferResult result = doSyncTransfer(transfer);
            
            assertEquals("direction", Direction.pullFromVoSpace, result.transfer.getDirection());
            
            // Should be no Protocols if Job in ERROR phase.
            assertTrue(result.transfer.getProtocols().isEmpty());
            
            // Get the Job.
            response = get(result.location);
            assertEquals("GET of Job response code should be 200", 200, response.getResponseCode());
            JobReader jobReader = new JobReader();
            Job job = jobReader.read(new StringReader(response.getText()));
            
            // Job phase should be ERROR.
            assertEquals("Job phase should be ERROR", ExecutionPhase.ERROR, job.getExecutionPhase());
            
            // ErrorSummary should be 'Protocol Not Supported'.
            assertEquals("Protocol Not Supported", job.getErrorSummary().getSummaryMessage());

            // Delete the node
            response = delete(VOSBaseTest.NODE_ENDPOINT, testNode.sampleNode);
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
