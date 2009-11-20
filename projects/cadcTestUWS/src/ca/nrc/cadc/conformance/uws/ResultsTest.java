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

package ca.nrc.cadc.conformance.uws;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import java.io.IOException;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import static org.junit.Assert.*;

public class ResultsTest extends TestConfig
{
    private static Logger log = Logger.getLogger(ResultsTest.class);

    private static final String CLASS_NAME = "ResultsTest";

    protected TestPropertiesList testPropertiesList;

    public ResultsTest()
        throws IOException
    {
        super();

        // DEBUG is default.
        log.setLevel((Level)Level.INFO);
        
        String propertiesDirectory = System.getProperty("properties.directory");
        if (propertiesDirectory == null)
            throw new RuntimeException("properties.directory System property not set");
        testPropertiesList = new TestPropertiesList(propertiesDirectory, CLASS_NAME);
    }

    @Test
    public void testResults()
        throws Exception
    {
        if (testPropertiesList.propertiesList.size() == 0)
            fail("missing properties file for " + CLASS_NAME);

        // For each properties file.
        for (TestProperties properties : testPropertiesList.propertiesList)
        {
            log.debug("**************************************************");
            log.debug("processing properties file: " + properties.filename);
            log.debug(properties);
            log.debug("**************************************************");

            // Create a new Job.
            WebConversation conversation = new WebConversation();
            String jobId = createJob(conversation, properties.parameters);

            // POST request to the phase resource.
            String resourceUrl = serviceUrl + "/" + jobId + "/phase";
            WebRequest postRequest = new PostMethodWebRequest(resourceUrl);
            postRequest.setParameter("PHASE", "RUN");
            WebResponse response = post(conversation, postRequest);

            // Get the redirect.
            String location = response.getHeaderField("Location");
            log.debug("Location: " + location);
            assertNotNull(properties.filename + " POST response to " + resourceUrl + " location header not set", location);
//            assertEquals(propertiesFilename + " POST response to " + resourceUrl + " location header incorrect", baseUrl + "/" + jobId, location);

            // Follow the redirect.
            response = get(conversation, location);

            // Validate the XML against the schema.
            log.debug("XML:\r\n" + response.getText());
            buildDocument(response.getText(), true);

            // Job resource for this jobId.
            resourceUrl = serviceUrl + "/" + jobId;

            // Loop until the phase is either COMPLETED, ERROR or ABORTED.
            Element root = null;
            NodeList list = null;
            boolean done = false;
            while (!done)
            {
                // Wait for 1 second.
                Thread.sleep(1000);

                // GET the resource.
                response = get(conversation, resourceUrl);

                // Create DOM document from XML.
                log.debug("XML:\r\n" + response.getText());
                Document document = buildDocument(response.getText(), false);

                // Root element of the document.
                root = document.getDocumentElement();
                assertNotNull(properties.filename + " no XML returned from GET of " + resourceUrl, root);

                // Get the phase element.
                list = root.getElementsByTagName("uws:phase");
                assertEquals(properties.filename + " phase element should only have a single element in XML returned from GET of " + resourceUrl, 1, list.getLength());
                Element phase = (Element) list.item(0);
                String phaseText = phase.getTextContent();

                // COMPLETED phase, continue with test.
                if (phaseText.equals("COMPLETED"))
                    break;

                // Fail if phase is ERROR or ABORTED.
                else if (phaseText.equals("ERROR") || phaseText.equals("ABORTED"))
                    fail(properties.filename + " phase should be ERROR, not " + phaseText + ", in XML returned from GET of " + resourceUrl);

                // Check phase, if still PENDING or QUEUED after x seconds, fail.
                else if (phaseText.equals("PENDING") || phaseText.equals("QUEUED") || phaseText.equals("EXECUTING"))
                    continue;
            }

            list = root.getElementsByTagName("uws:results");
            assertEquals(properties.filename + " uws:results element should only have a single element in XML returned from GET of " + resourceUrl, 1, list.getLength());

            list = root.getElementsByTagName("uws:result");
            validateResults(conversation, response, properties, list);

            deleteJob(conversation, jobId);

            log.info("ResultsTest.testResults completed.");
        }
    }

    protected void validateResults(WebConversation conversation, WebResponse response, TestProperties properties, NodeList list)
        throws Exception
    {
        for (int i = 0; i < list.getLength(); i++)
        {
            // Get the result url.
            Element element = (Element) list.item(i);
            head(conversation, element.getAttribute("xlink:href"));
        }
    }

}
