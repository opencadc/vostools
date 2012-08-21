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
import ca.nrc.cadc.uws.Result;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Direction;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.NodeWriter;
import ca.nrc.cadc.vos.Protocol;
import ca.nrc.cadc.vos.Transfer;
import ca.nrc.cadc.vos.TransferReader;
import ca.nrc.cadc.vos.TransferWriter;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.View;
import com.meterware.httpunit.WebResponse;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import static org.junit.Assert.*;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test case for sending data to a service (pushToVoSpace).
 *
 * @author jburke
 */
public class AsyncPushToVOSpaceTest extends VOSTransferTest
{
    private static Logger log = Logger.getLogger(AsyncPushToVOSpaceTest.class);

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.conformance.vos", Level.INFO);
    }

    public AsyncPushToVOSpaceTest()
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
    public void testPushToVOSpace()
    {
        try
        {
            log.debug("testPushToVOSpace");
            
            // Get a DataNode.
            DataNode dataNode = getSampleDataNode();
            dataNode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, new Long(1024).toString()));
            WebResponse response = put(VOSBaseTest.NODE_ENDPOINT,dataNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Create a Transfer.
            View view = new View(new URI(VOS.VIEW_DEFAULT));
            List<Protocol> protocols = new ArrayList<Protocol>();
            protocols.add(new Protocol(VOS.PROTOCOL_HTTP_PUT));
            protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_PUT));
            Transfer transfer = new Transfer(dataNode.getUri(), Direction.pushToVoSpace,view, protocols);
            
            // Get the transfer XML.
            TransferWriter writer = new TransferWriter();
            StringWriter sw = new StringWriter();
            writer.write(transfer, sw);
            
            // POST the XML to the transfer endpoint.
            response = post(sw.toString());
            assertEquals("POST response code should be 303", 303, response.getResponseCode());

            // Get the header Location.
            String location = response.getHeaderField("Location");
            assertNotNull("Location header not set", location);
            
            // Follow all the redirects.
            response = get(location);
            while (303 == response.getResponseCode())
            {
                location = response.getHeaderField("Location");
                assertNotNull("Location header not set", location);
                log.debug("New location: " + location);
                response = get(location);
            }
            
            // read the response job doc.
            String xml = response.getText();
            log.debug("Job response from GET: \n\n" + xml);

            // Create a Job from Job XML.
            JobReader reader = new JobReader();
            Job job = reader.read(new StringReader(xml));
            assertEquals("Job pending", ExecutionPhase.PENDING, job.getExecutionPhase());
            
            // Run the job.
            Map<String, String> parameters = new HashMap<String, String>();
            parameters.put("PHASE", "RUN");
            response = post(location + "/phase", parameters);
            assertEquals("POST response code should be 303", 303, response.getResponseCode());

            // Follow the redirect until phase is EXECUTING, ERROR, or ABORTED.
            int count = 0;
            boolean done = false;
            while (!done)
            {
                // Follow the redirect.
                response = get(location);
                assertEquals("GET response code should be 200", 200, response.getResponseCode());

                // Get the response (an XML document)
                xml = response.getText();
                log.debug("GET XML:\r\n" + xml);

                // Get the Job.
                JobReader jobReader = new JobReader();
                job = jobReader.read(new StringReader(response.getText()));

                if (job.getExecutionPhase() == ExecutionPhase.EXECUTING)
                {
                    // Get the results.
                    List<Result> results = job.getResultsList();
                    
                    // Find id of transferDetails
                    URI resultUri = null;
                    for (Result result : results)
                    {
                        if (result.getName().equals("transferDetails"))
                        {
                            resultUri = result.getURI();
                            break;
                        }
                    }
                    assertNotNull("transferDetails Result URI not found", resultUri);
                    
                    // Get the Transfer document.
                    response = get(resultUri.toString());
                    assertEquals("GET response code should be 200", 200, response.getResponseCode());

                    // Get the Transfer XML.
                    xml = response.getText();
                    log.debug("GET XML:\r\n" + xml);

                    // Create a Transfer from Transfer XML.
                    TransferReader transferReader = new TransferReader();
                    transfer = transferReader.read(xml);

                    // Get the Transfer endpoint and make sure it's a valid URL. 
                    new URL(transfer.getEndpoint(VOS.PROTOCOL_HTTP_PUT));                    
                    break;
                }

                if (job.getExecutionPhase() == ExecutionPhase.ERROR || 
                    job.getExecutionPhase() == ExecutionPhase.ABORTED)
                {
                    fail("Job " + job.getExecutionPhase());
                    break;
                }

                // Allow 30 seconds the the job to complete.
                if (count++ > 30)
                {
                    fail("Job timeout");
                    break;
                }

                // Sleep for a second.
                Thread.sleep(1000);
            }

            // Delete the node
            response = delete(VOSBaseTest.NODE_ENDPOINT, dataNode);
            assertEquals("DELETE response code should be 200", 200, response.getResponseCode());
            
            log.info("testPushToVOSpace passed.");
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

    @Ignore("accepts/provides views not implemented")
    @Test
    public void viewNotSupportedFault()
    {
        try
        {
            log.debug("viewNotSupportedFault");

            // Get a DataNode.
            DataNode dataNode = getSampleDataNode();
            dataNode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, new Long(1024).toString()));
            WebResponse response = put(VOSBaseTest.NODE_ENDPOINT,dataNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Create a Transfer.
            View view = new View(new URI("ivo://cadc.nrc.ca/vospace/view#bogus"));
            List<Protocol> protocols = new ArrayList<Protocol>();
            protocols.add(new Protocol(VOS.PROTOCOL_HTTP_PUT));
            Transfer transfer = new Transfer(dataNode.getUri(), Direction.pushToVoSpace, view, protocols);
            
            // Get the transfer XML.
            TransferWriter writer = new TransferWriter();
            StringWriter sw = new StringWriter();
            writer.write(transfer, sw);
            
            // POST the XML to the transfer endpoint.
            response = post(sw.toString());
            assertEquals("POST response code should be 303", 303, response.getResponseCode());

            // Get the header Location.
            String location = response.getHeaderField("Location");
            assertNotNull("Location header not set", location);
            
            // Follow all the redirects.
            response = get(location);
            while (303 == response.getResponseCode())
            {
                location = response.getHeaderField("Location");
                assertNotNull("Location header not set", location);
                log.debug("New location: " + location);
                response = get(location);
            }
            assertEquals("POST response code should be 200", 200, response.getResponseCode());
            
            // Run the job.
            Map<String, String> parameters = new HashMap<String, String>();
            parameters.put("PHASE", "RUN");
            response = post(location + "/phase", parameters);
            assertEquals("POST response code should be 303", 303, response.getResponseCode());

            // Follow the redirect until phase is COMPLETED, ERROR, or ABORTED.
            int count = 0;
            boolean done = false;
            while (!done)
            {
                // Follow the redirect.
                response = get(location);
                assertEquals("GET response code should be 200", 200, response.getResponseCode());

                // Get the response (an XML document)
                String xml = response.getText();
                log.debug("GET XML:\r\n" + xml);

                // Get the Job.
                JobReader jobReader = new JobReader();
                Job job = jobReader.read(new StringReader(response.getText()));

                if (job.getExecutionPhase().equals("COMPLETED") || 
                    job.getExecutionPhase().equals("ABORTED"))
                {
                    fail("Job phase should be ERROR");         
                    break;
                }

                if (job.getExecutionPhase().equals("ERROR"))
                {
                    // ErrorSummary should be 'View Not Supported'.
                    assertEquals("View Not Supported", job.getErrorSummary().getSummaryMessage());
                    break;
                }

                // Allow 30 seconds the the job to complete.
                if (count++ > 30)
                {
                    fail("Job timeout");
                    break;
                }

                // Sleep for a second.
                Thread.sleep(1000);
            }

            // Delete the node
            response = delete(VOSBaseTest.NODE_ENDPOINT, dataNode);
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
            DataNode dataNode = getSampleDataNode();
            dataNode.getProperties().add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, new Long(1024).toString()));
            WebResponse response = put(VOSBaseTest.NODE_ENDPOINT,dataNode, new NodeWriter());
            assertEquals("PUT response code should be 200", 200, response.getResponseCode());

            // Create a Transfer.
            List<Protocol> protocols = new ArrayList<Protocol>();
            protocols.add(new Protocol("http://localhost/path"));
            Transfer transfer = new Transfer(dataNode.getUri(), Direction.pushToVoSpace, protocols);
            
            // Get the transfer XML.
            TransferWriter writer = new TransferWriter();
            StringWriter sw = new StringWriter();
            writer.write(transfer, sw);
            
            // POST the XML to the transfer endpoint.
            response = post(sw.toString());
            assertEquals("POST response code should be 303", 303, response.getResponseCode());

            // Get the header Location.
            String location = response.getHeaderField("Location");
            assertNotNull("Location header not set", location);
            
            // Follow all the redirects.
            response = get(location);
            while (303 == response.getResponseCode())
            {
                location = response.getHeaderField("Location");
                assertNotNull("Location header not set", location);
                log.debug("New location: " + location);
                response = get(location);
            }
            assertEquals("POST response code should be 200", 200, response.getResponseCode());
            
            // Run the job.
            Map<String, String> parameters = new HashMap<String, String>();
            parameters.put("PHASE", "RUN");
            response = post(location + "/phase", parameters);
            assertEquals("POST response code should be 303", 303, response.getResponseCode());

            // Follow the redirect until phase is COMPLETED, ERROR, or ABORTED.
            int count = 0;
            boolean done = false;
            while (!done)
            {
                // Follow the redirect.
                response = get(location);
                assertEquals("GET response code should be 200", 200, response.getResponseCode());

                // Get the response (an XML document)
                String xml = response.getText();
                log.debug("GET XML:\r\n" + xml);

                // Get the Job.
                JobReader jobReader = new JobReader();
                Job job = jobReader.read(new StringReader(response.getText()));

                if (job.getExecutionPhase().equals("COMPLETED") || 
                    job.getExecutionPhase().equals("ABORTED"))
                {
                    fail("Job phase should be ERROR");         
                    break;
                }

                if (job.getExecutionPhase().equals("ERROR"))
                {
                    // ErrorSummary should be 'Protocol Not Supported'.
                    assertEquals("Protocol Not Supported", job.getErrorSummary().getSummaryMessage());
                    break;
                }

                // Allow 30 seconds the the job to complete.
                if (count++ > 30)
                {
                    fail("Job timeout");
                    break;
                }

                // Sleep for a second.
                Thread.sleep(1000);
            }

            // Delete the node
            response = delete(VOSBaseTest.NODE_ENDPOINT, dataNode);
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
