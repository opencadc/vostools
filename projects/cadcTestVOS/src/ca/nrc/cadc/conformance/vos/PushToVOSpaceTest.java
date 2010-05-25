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

import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobWriter;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeReader;
import ca.nrc.cadc.vos.Transfer;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.xml.XmlUtil;
import com.meterware.httpunit.WebResponse;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test case for sending data to a service (pushToVoSpace).
 *
 * @author jburke
 */
public class PushToVOSpaceTest extends VOSTransferTest
{
    private static Logger log = Logger.getLogger(PushToVOSpaceTest.class);

    static Job job;

    public PushToVOSpaceTest()
    {
        super();
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        job = new Job();
        job.setID("job123");
        job.addParameter(new Parameter("target", VOSBaseTest.VOSPACE_URI + "/A"));
        job.addParameter(new Parameter("direction", Transfer.Direction.pushToVoSpace.name()));
        job.addParameter(new Parameter("view", VOS.VIEW_DEFAULT));
        job.addParameter(new Parameter("protocol", VOS.PROTOCOL_HTTP_PUT));
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
    public void pushNewJob()
    {
        try
        {
            log.debug("pushNewJob");

            // POST parameters (UWS Job XML)
            JobWriter jobWriter = new JobWriter(job);
            String jobXML = jobWriter.toString();
            Map<String, String> parameters = new HashMap<String, String>();
            parameters.put("", jobXML);

            // POST the Job to the transfer endpoint.
            WebResponse response = post(parameters);
            assertEquals("POST response code should be 303", 303, response.getResponseCode());

            // Get the header Location.
            String location = response.getHeaderField("Location");
            assertNotNull("Location header not set", location);

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

                // Create a JDOM Document from XML and validate against the UWS schema.
                Document document = XmlUtil.validateXml(xml, UWS_SCHEMA, uwsSchemaUrl);

                // Get the phase
                Element root = document.getRootElement();
                Namespace namespace = root.getNamespace();
                String phase = root.getChildText("phase", namespace);

                if (phase.equals("COMPLETED"))
                {
                    // Get the result transferDetails
                    Element result = root.getChild("result", namespace);
                    assertNotNull("result element not found", result);

                    // id attribute should be transferDetails.
                    String id = result.getAttributeValue("id");
                    assertNotNull("id attribute not found", id);
                    assertEquals("id attribute should be 'transferDetails'", "transferDetails", id);

                    // Url to the Transfer node
                    String href = result.getAttributeValue("href", xlinkNamespace);
                    assertNotNull("xink:href attribute not found", href);

                    // Get the node
                    response = get(href);
                    assertEquals("GET response code should be 200", 200, response.getResponseCode());

                    // Get the response (an XML document)
                    xml = response.getText();
                    log.debug("GET XML:\r\n" + xml);

                    // Create a JDOM document from XML and validate against the VOSPace schema using the NodeReader.
                    NodeReader reader = new NodeReader();
                    Node node = reader.read(xml);

                    // Delete the node
                    response = delete(node);
                    assertEquals("DELETE response code should be 200", 200, response.getResponseCode());

                    break;
                }

                if (phase.equals("ERROR") || phase.equals("ABORTED"))
                {
                    fail("Job " + phase);
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

            log.info("pushNewJob passed.");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

//    @Test
    public void permissionDeniedFault()
    {
        try
        {
            log.debug("permissionDeniedFault");

            fail("not implemented");

            log.info("permissionDeniedFault passed.");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

//    @Test
    public void viewNotSupportedFault()
    {
        try
        {
            log.debug("viewNotSupportedFault");

            fail("accepts/provides views not implemented");

            log.info("viewNotSupportedFault passed.");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

//    @Test
    public void protocolNotSupportedFault()
    {
        try
        {
            log.debug("protocolNotSupportedFault");

            fail("accepts/provides views not implemented");

            log.info("protocolNotSupportedFault passed.");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

//    @Test
    public void invalidViewParameterFault()
    {
        try
        {
            log.debug("invalidViewParameterFault");

            fail("accepts/provides views not implemented");

            log.info("invalidViewParameterFault passed.");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

//    @Test
    public void invalidProtocolParameterFault()
    {
        try
        {
            log.debug("invalidProtocolParameterFault");

            fail("accepts/provides views not implemented");

            log.info("invalidProtocolParameterFault passed.");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

}
