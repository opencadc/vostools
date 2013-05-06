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

import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobReader;
import ca.nrc.cadc.uws.Result;
import ca.nrc.cadc.vos.Transfer;
import ca.nrc.cadc.vos.TransferParsingException;
import ca.nrc.cadc.vos.TransferReader;
import ca.nrc.cadc.vos.TransferWriter;
import ca.nrc.cadc.xml.XmlUtil;
import com.meterware.httpunit.WebResponse;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.xml.sax.SAXException;

/**
 * Base class for testing Transfer resources.
 * 
 * @author jburke
 */
public class VOSTransferTest extends VOSBaseTest
{
    private static Logger log = Logger.getLogger(VOSTransferTest.class);

    protected static final String UWS_SCHEMA = "http://www.ivoa.net/xml/UWS/v1.0";
    
    protected String uwsSchemaUrl;
    protected Namespace xlinkNamespace;

    public static final String SYNC_TRANSFER_ENDPOINT = "/synctrans";
    public static final String ASYNC_TRANSFER_ENDPOINT = "/transfers";

    public VOSTransferTest(String jobResource)
    {
        super(jobResource);
        
        uwsSchemaUrl = XmlUtil.getResourceUrlString("UWS-v1.0.xsd", VOSTransferTest.class);
        xlinkNamespace = Namespace.getNamespace("http://www.w3.org/1999/xlink");
    }

    protected TransferResult doTransfer(Transfer transfer)
        throws IOException, SAXException, JDOMException, ParseException
    {
        // Get the transfer XML.
        TransferWriter writer = new TransferWriter();
        StringWriter sw = new StringWriter();
        writer.write(transfer, sw);

        // POST the XML to the transfer endpoint.
        WebResponse response = post(sw.toString());
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

        // poll the phase
        // We want to test to succeed even when the web service is very slow,
        // so the timeout is increased to 180 seconds in total.
        WebResponse phaseResp = get(location + "/phase");
        String phase = phaseResp.getText();
        log.debug("phase: " + phase);
        int tries = 0;
        while (tries < 60
                && ( ExecutionPhase.QUEUED.getValue().equals(phase)
                    || ExecutionPhase.EXECUTING.getValue().equals(phase) ) )
        {
            try { Thread.sleep(3000L); }
            catch(InterruptedException ex) { throw new RuntimeException("polling loop interrupted", ex); }
            phaseResp = get(location + "/phase");
            phase = phaseResp.getText();
            log.debug("phase: " + phase);
            tries++;

        }

        // get and read the response job doc.
        response = get(location);

        xml = response.getText();
        log.debug("Job response from GET: \n\n" + xml);

        // Create a Job from Job XML.
        job = reader.read(new StringReader(xml));

        return new TransferResult(transfer, job, location);
    }

    protected TransferResult doSyncTransfer(Transfer transfer)
        throws IOException, SAXException, TransferParsingException
    {
        // Get the transfer XML.
        TransferWriter writer = new TransferWriter();
        StringWriter sw = new StringWriter();
        writer.write(transfer, sw);

        // POST the XML to the transfer endpoint.
        WebResponse response = post(sw.toString());
        assertEquals("POST response code should be 303", 303, response.getResponseCode());

        // Get the header Location.
        String location = response.getHeaderField("Location");
        assertNotNull("Location header not set", location);
        assertTrue("../results/transferDetails location expected: ",
                location.endsWith("/results/transferDetails"));

        // Parse out the path to the Job.
        int index = location.indexOf("/results/transferDetails");
        String jobPath = location.substring(0, index);

        // Follow all the redirects.
        response = get(location);
        while (303 == response.getResponseCode())
        {
            location = response.getHeaderField("Location");
            assertNotNull("Location header not set", location);
            log.debug("New location: " + location);
            response = get(location);
        }
        assertEquals("GET response code should be 200", 200, response.getResponseCode());

        // Get the Transfer XML.
        String xml = response.getText();
        log.debug("GET XML :\r\n" + xml);

        // Create a Transfer from Transfer XML.
        TransferReader reader = new TransferReader();
        transfer = reader.read(xml);
        return new TransferResult(transfer, null, jobPath);
    }

    protected TransferResult doAsyncTransfer(Transfer transfer)
        throws IOException, SAXException, JDOMException, ParseException, InterruptedException, TransferParsingException
    {
        // Get the transfer XML.
        TransferWriter writer = new TransferWriter();
        StringWriter sw = new StringWriter();
        writer.write(transfer, sw);

        // POST the XML to the transfer endpoint.
        WebResponse response = post(sw.toString());
        assertEquals("POST response code should be 303", 303, response.getResponseCode());

        // Get the header Location.
        String location = response.getHeaderField("Location");
        assertNotNull("Location header not set", location);

        // Follow the redirect.
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

        // Wait until phase equals EXECUTING.
        int count = 0;
        boolean done = false;
        while (!done)
        {
            // Follow the redirect.
            response = get(location);
            assertEquals("GET response code should be 200", 200, response.getResponseCode());

            // Get the response (an XML document)
            xml = response.getText();
            log.debug("Job XML: \n\n" + xml);

            // Get the Job.
            JobReader jobReader = new JobReader();
            job = jobReader.read(new StringReader(xml));
            log.debug("Job phase: " + job.getExecutionPhase().getValue());

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
                break;
            }

            if (job.getExecutionPhase() == ExecutionPhase.ERROR ||
                job.getExecutionPhase() == ExecutionPhase.ABORTED)
            {
                break;
            }

            if (count++ > 10)
            {
                fail("Job timeout");
                break;
            }

            // Sleep for a second.
            Thread.sleep(1000);
        }

        return new TransferResult(transfer, job, location);
    }

    protected class TransferResult
    {
        public Transfer transfer;
        public Job job;
        public String location;

        TransferResult(Transfer transfer, Job job, String location)
        {
            this.location = location;
            this.transfer = transfer;
            this.job = job;
        }
    }
}
