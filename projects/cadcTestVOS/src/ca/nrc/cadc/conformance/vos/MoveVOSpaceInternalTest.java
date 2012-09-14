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
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobReader;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.LinkNode;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.Protocol;
import ca.nrc.cadc.vos.Transfer;
import ca.nrc.cadc.vos.TransferWriter;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test case for reading data from a service (pullFromVoSpace).
 * 
 * @author jburke
 */
public class MoveVOSpaceInternalTest extends VOSTransferTest
{
    private static Logger log = Logger
            .getLogger(MoveVOSpaceInternalTest.class);

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.conformance.vos", Level.INFO);
    }

    public MoveVOSpaceInternalTest()
    {
        super(ASYNC_TRANSFER_ENDPOINT);
    }

    @Test
    public void testSimpleMove()
    {
        try
        {
            log.debug("testSimpleMove");
            fail("update");
            // Get a DataNode.
            TestNode dataNode = getSampleDataNode();
            dataNode.sampleNode.getProperties().add(
                    new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH,
                            new Long(1024).toString()));
            WebResponse response = put(VOSBaseTest.NODE_ENDPOINT,
                    dataNode.sampleNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200,
                    response.getResponseCode());
            log.debug("SRC DataNode created");

            // Create a container node
            TestNode containerNode = getSampleContainerNode();
            response = put(VOSBaseTest.NODE_ENDPOINT, containerNode.sampleNode,
                    new NodeWriter());
            assertEquals("PUT response code should be 200", 200,
                    response.getResponseCode());
            log.debug("DEST ContainerNode created");

            // Create a Transfer.
            List<Protocol> protocols = new ArrayList<Protocol>();
            protocols.add(new Protocol(VOS.PROTOCOL_HTTP_GET));
            protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_GET));
            protocols.add(new Protocol("some:unknown:proto"));
            Transfer transfer = new Transfer(dataNode.sampleNode.getUri(),
                    containerNode.sampleNode.getUri(), false);

            // Get the transfer XML.
            TransferWriter writer = new TransferWriter();
            StringWriter sw = new StringWriter();
            writer.write(transfer, sw);

            // POST the XML to the transfer endpoint.
            response = post(sw.toString());
            assertEquals("POST response code should be 303", 303,
                    response.getResponseCode());

            // check the job
            // Get the header Location.
            String location = response.getHeaderField("Location");
            log.debug("Location: " + location);
            assertNotNull("Location header not set", location);
            assertTrue("../results/transferDetails location expected: ",
                    location.contains("vospace/transfers"));

            response = get(location);
            while (303 == response.getResponseCode())
            {
                location = response.getHeaderField("Location");
                assertNotNull("Location header not set", location);
                log.debug("New location: " + location);
                response = get(location);
            }
            assertEquals("GET response code should be 200", 200,
                    response.getResponseCode());

            // read the response transfer doc
            String xml = response.getText();
            log.debug("testSyncTransfer response from job GET: \n\n"
                    + xml);

            // Create a Job from Job XML.
            JobReader jobReader = new JobReader();
            Job job = jobReader.read(new ByteArrayInputStream(xml
                    .getBytes()));

            assertEquals("Job pending", ExecutionPhase.PENDING,
                    job.getExecutionPhase());

            // now run the job
            ByteArrayInputStream in = new ByteArrayInputStream(
                    "PHASE=RUN".getBytes("UTF-8"));
            WebRequest request = new PostMethodWebRequest(location
                    + "/phase", in, "text/xml");

            log.debug(getRequestParameters(request));

            WebConversation conversation = new WebConversation();
            conversation.setExceptionsThrownOnErrorStatus(false);
            response = conversation.sendRequest(request);
            assertEquals("POST response code should be 303", 303,
                    response.getResponseCode());

            // check the job again - it should be completed
            response = get(location);

            assertEquals("GET response code should be 200", 200,
                    response.getResponseCode());

            // read the response transfer doc
            xml = response.getText();
            log.debug("testSyncTransfer response from job GET: \n\n"
                    + xml);

            // Create a Job from Job XML.
            job = jobReader
                    .read(new ByteArrayInputStream(xml.getBytes()));

            assertEquals("Job done", ExecutionPhase.COMPLETED,
                    job.getExecutionPhase());

            // check node has been moved
            // old node gone
            response = get(VOSBaseTest.NODE_ENDPOINT, dataNode.sampleNode);
            assertEquals("GET response code should be 404", 404,
                    response.getResponseCode());

            response = get(VOSBaseTest.NODE_ENDPOINT, containerNode.sampleNode);
            assertEquals("GET response code should be 200", 200,
                    response.getResponseCode());

            log.debug("Container node: " + containerNode.sampleNode.getName());

            // new node there
            DataNode movedNode = new DataNode(new VOSURI(
                    containerNode.sampleNode.getUri() + "/" + dataNode.sampleNode.getName()));
            log.debug("Moved node URI: " + movedNode.getUri());

            response = get(VOSBaseTest.NODE_ENDPOINT, movedNode);
            assertEquals("GET response code should be 200", 200,
                    response.getResponseCode());

            // Delete the node
            response = delete(VOSBaseTest.NODE_ENDPOINT, movedNode);
            assertEquals("DELETE response code should be 200", 200,
                    response.getResponseCode());

            // Delete the container
            response = delete(VOSBaseTest.NODE_ENDPOINT, containerNode.sampleNode);
            assertEquals("DELETE response code should be 200", 200,
                    response.getResponseCode());

            log.info("testSimpleMove passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testSimpleMoveWithLink()
    {
        try
        {
            log.debug("testSimpleMoveWithLink");

            // Get a DataNode.
            LinkNode linkNode = getSampleLinkNode(new URI(
                    "www.google.com"));
            WebResponse response = put(VOSBaseTest.NODE_ENDPOINT,
                    linkNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200,
                    response.getResponseCode());
            log.debug("SRC DataNode created");

            // Create a container node
            TestNode containerNode = getSampleContainerNode();
            response = put(VOSBaseTest.NODE_ENDPOINT, containerNode.sampleNode,
                    new NodeWriter());
            assertEquals("PUT response code should be 200", 200,
                    response.getResponseCode());
            log.debug("DEST ContainerNode created");

            // Create a Transfer.
            List<Protocol> protocols = new ArrayList<Protocol>();
            protocols.add(new Protocol(VOS.PROTOCOL_HTTP_GET));
            protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_GET));
            protocols.add(new Protocol("some:unknown:proto"));
            Transfer transfer = new Transfer(linkNode.getUri(),
                    containerNode.sampleNode.getUri(), false);

            // Get the transfer XML.
            TransferWriter writer = new TransferWriter();
            StringWriter sw = new StringWriter();
            writer.write(transfer, sw);

            // POST the XML to the transfer endpoint.
            response = post(sw.toString());
            assertEquals("POST response code should be 303", 303,
                    response.getResponseCode());

            // check the job
            // Get the header Location.
            String location = response.getHeaderField("Location");
            log.debug("Location: " + location);
            assertNotNull("Location header not set", location);
            assertTrue("../results/transferDetails location expected: ",
                    location.contains("vospace/transfers"));

            response = get(location);
            while (303 == response.getResponseCode())
            {
                location = response.getHeaderField("Location");
                assertNotNull("Location header not set", location);
                log.debug("New location: " + location);
                response = get(location);
            }
            assertEquals("GET response code should be 200", 200,
                    response.getResponseCode());

            // read the response transfer doc
            String xml = response.getText();
            log.debug("testSyncTransfer response from job GET: \n\n"
                    + xml);

            // Create a Job from Job XML.
            JobReader jobReader = new JobReader();
            Job job = jobReader.read(new ByteArrayInputStream(xml
                    .getBytes()));

            assertEquals("Job pending", ExecutionPhase.PENDING,
                    job.getExecutionPhase());

            // now run the job
            ByteArrayInputStream in = new ByteArrayInputStream(
                    "PHASE=RUN".getBytes("UTF-8"));
            WebRequest request = new PostMethodWebRequest(location
                    + "/phase", in, "text/xml");

            log.debug(getRequestParameters(request));

            WebConversation conversation = new WebConversation();
            conversation.setExceptionsThrownOnErrorStatus(false);
            response = conversation.sendRequest(request);
            assertEquals("POST response code should be 303", 303,
                    response.getResponseCode());

            // check the job again - it should be completed
            response = get(location);

            assertEquals("GET response code should be 200", 200,
                    response.getResponseCode());

            // read the response transfer doc
            xml = response.getText();
            log.debug("testSyncTransfer response from job GET: \n\n"
                    + xml);

            // Create a Job from Job XML.
            job = jobReader
                    .read(new ByteArrayInputStream(xml.getBytes()));

            assertEquals("Job done", ExecutionPhase.COMPLETED,
                    job.getExecutionPhase());

            // check node has been moved
            // old node gone
            response = get(VOSBaseTest.NODE_ENDPOINT, linkNode);
            assertEquals("GET response code should be 404", 404,
                    response.getResponseCode());

            response = get(VOSBaseTest.NODE_ENDPOINT, containerNode.sampleNode);
            assertEquals("GET response code should be 200", 200,
                    response.getResponseCode());

            log.debug("Container node: " + containerNode.sampleNode.getName());

            // new node there
            DataNode movedNode = new DataNode(new VOSURI(
                    containerNode.sampleNode.getUri() + "/" + linkNode.getName()));
            log.debug("Moved node URI: " + movedNode.getUri());

            response = get(VOSBaseTest.NODE_ENDPOINT, movedNode);
            assertEquals("GET response code should be 200", 200,
                    response.getResponseCode());

            // Delete the node
            response = delete(VOSBaseTest.NODE_ENDPOINT, movedNode);
            assertEquals("DELETE response code should be 200", 200,
                    response.getResponseCode());

            // Delete the container
            response = delete(VOSBaseTest.NODE_ENDPOINT, containerNode.sampleNode);
            assertEquals("DELETE response code should be 200", 200,
                    response.getResponseCode());

            log.info("testSimpleMove passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testSimpleMoveWithLinkInPath()
    {
        try
        {
            log.debug("testSimpleMoveWithLinkInPath");

            // Get a DataNode.
            TestNode dataNode = getSampleDataNode();
            dataNode.sampleNode.getProperties().add(
                    new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH,
                            new Long(1024).toString()));
            WebResponse response = put(VOSBaseTest.NODE_ENDPOINT,
                    dataNode.sampleNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200,
                    response.getResponseCode());
            log.debug("SRC DataNode created");

            // Create a container node
            TestNode containerNode = getSampleContainerNode();
            response = put(VOSBaseTest.NODE_ENDPOINT, containerNode.sampleNode,
                    new NodeWriter());
            assertEquals("PUT response code should be 200", 200,
                    response.getResponseCode());
            log.debug("DEST ContainerNode created");
            
            // create a link to the container node
            LinkNode linkNode = getSampleLinkNode(containerNode.sampleNode);
            response = put(VOSBaseTest.NODE_ENDPOINT, linkNode,
                    new NodeWriter());
            assertEquals("PUT response code should be 200", 200,
                    response.getResponseCode());
            log.debug("DEST LinkNode created");

            // Create a Transfer.
            List<Protocol> protocols = new ArrayList<Protocol>();
            protocols.add(new Protocol(VOS.PROTOCOL_HTTP_GET));
            protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_GET));
            protocols.add(new Protocol("some:unknown:proto"));
            Transfer transfer = new Transfer(dataNode.sampleNode.getUri(),
                    linkNode.getUri(), false);

            // Get the transfer XML.
            TransferWriter writer = new TransferWriter();
            StringWriter sw = new StringWriter();
            writer.write(transfer, sw);

            // POST the XML to the transfer endpoint.
            response = post(sw.toString());
            assertEquals("POST response code should be 303", 303,
                    response.getResponseCode());

            // check the job
            // Get the header Location.
            String location = response.getHeaderField("Location");
            log.debug("Location: " + location);
            assertNotNull("Location header not set", location);
            assertTrue("../results/transferDetails location expected: ",
                    location.contains("vospace/transfers"));

            response = get(location);
            while (303 == response.getResponseCode())
            {
                location = response.getHeaderField("Location");
                assertNotNull("Location header not set", location);
                log.debug("New location: " + location);
                response = get(location);
            }
            assertEquals("GET response code should be 200", 200,
                    response.getResponseCode());

            // read the response transfer doc
            String xml = response.getText();
            log.debug("testSyncTransfer response from job GET: \n\n"
                    + xml);

            // Create a Job from Job XML.
            JobReader jobReader = new JobReader();
            Job job = jobReader.read(new ByteArrayInputStream(xml
                    .getBytes()));

            assertEquals("Job pending", ExecutionPhase.PENDING,
                    job.getExecutionPhase());

            // now run the job
            ByteArrayInputStream in = new ByteArrayInputStream(
                    "PHASE=RUN".getBytes("UTF-8"));
            WebRequest request = new PostMethodWebRequest(location
                    + "/phase", in, "text/xml");

            log.debug(getRequestParameters(request));

            WebConversation conversation = new WebConversation();
            conversation.setExceptionsThrownOnErrorStatus(false);
            response = conversation.sendRequest(request);
            assertEquals("POST response code should be 303", 303,
                    response.getResponseCode());

            // check the job again - it should be completed
            response = get(location);

            assertEquals("GET response code should be 200", 200,
                    response.getResponseCode());

            // read the response transfer doc
            xml = response.getText();
            log.debug("testSyncTransfer response from job GET: \n\n"
                    + xml);

            // Create a Job from Job XML.
            job = jobReader
                    .read(new ByteArrayInputStream(xml.getBytes()));

            assertEquals("Job done", ExecutionPhase.COMPLETED,
                    job.getExecutionPhase());

            // check node has been moved
            // old node gone
            response = get(VOSBaseTest.NODE_ENDPOINT, dataNode.sampleNode);
            assertEquals("GET response code should be 404", 404,
                    response.getResponseCode());

            response = get(VOSBaseTest.NODE_ENDPOINT, containerNode.sampleNode);
            assertEquals("GET response code should be 200", 200,
                    response.getResponseCode());

            log.debug("Container node: " + containerNode.sampleNode.getName());

            // new node there
            DataNode movedNode = new DataNode(new VOSURI(
                    containerNode.sampleNode.getUri() + "/" + dataNode.sampleNode.getName()));
            log.debug("Moved node URI: " + movedNode.getUri());

            response = get(VOSBaseTest.NODE_ENDPOINT, movedNode);
            assertEquals("GET response code should be 200", 200,
                    response.getResponseCode());

            // Delete the node
            response = delete(VOSBaseTest.NODE_ENDPOINT, movedNode);
            assertEquals("DELETE response code should be 200", 200,
                    response.getResponseCode());

            // Delete the container
            response = delete(VOSBaseTest.NODE_ENDPOINT, containerNode.sampleNode);
            assertEquals("DELETE response code should be 200", 200,
                    response.getResponseCode());
            
            // Delete the link node
            response = delete(VOSBaseTest.NODE_ENDPOINT, linkNode);
            assertEquals("DELETE response code should be 200", 200,
                    response.getResponseCode());

            log.info("testSimpleMove passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Ignore("get view=data not implemented")
    @Test
    public void getViewEqualsData()
    {
        try
        {
            log.debug("getViewEqualsData");

            fail("get view=data not implemented");

            log.info("getViewEqualsData passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception: " + unexpected);
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

            fail("node not found fault not implemented");

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

            fail("accepts/provides views not implemented");

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

            fail("accepts/provides views not implemented");

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
