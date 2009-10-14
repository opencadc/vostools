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

package ca.nrc.cadc.uws.sample;

import ca.nrc.cadc.conformance.uws.*;
import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ResultsTest extends TestConfig
{
    private static Logger log = Logger.getLogger(ResultsTest.class);

    private static final String CLASS_NAME = "ResultsTest";

    private File[] propertiesFiles;

    public ResultsTest(String testName)
    {
        super(testName);

        propertiesFiles = getPropertiesFiles(CLASS_NAME);
    }

    public void testResults()
        throws Exception
    {
        if (propertiesFiles == null)
            fail("missing properties file for " + CLASS_NAME);

        // For each properties file.
        for (int i = 0; i < propertiesFiles.length; i++)
        {
            File propertiesFile = propertiesFiles[i];
            String propertiesFilename = propertiesFile.getName();
            log.debug("**************************************************");
            log.debug("processing properties file: " + propertiesFilename);
            log.debug("**************************************************");

            // Load the properties file.
            Properties properties = new Properties();
            FileReader reader = new FileReader(propertiesFile);
            properties.load(reader);

            // Base URL to the UWS service.
            String baseUrl = properties.getProperty("ca.nrc.cadc.conformance.uws.baseUrl");
            log.debug(propertiesFilename + " ca.nrc.cadc.conformance.uws.baseUrl: " + baseUrl);
            assertNotNull("ca.nrc.cadc.conformance.uws.baseUrl property is not set in properties file " + propertiesFilename, baseUrl);

            // URL to the UWS schema used for validation.
            String schemaUrl = properties.getProperty("ca.nrc.cadc.conformance.uws.schemaUrl");
            log.debug(propertiesFilename + " ca.nrc.cadc.conformance.uws.schemaUrl: " + schemaUrl);
            assertNotNull("ca.nrc.cadc.conformance.uws.schemaUrl property is not set in properties file " + propertiesFilename, schemaUrl);

            // Result element id value.
            String id = properties.getProperty("id");
            log.debug(propertiesFilename + " id: " + id);
            assertNotNull("id property is not set in properties file " + propertiesFilename, id);

            // Result element xlink:href value.
            String link = properties.getProperty("href");
            assertNotNull("href property is not set in properties file " + propertiesFilename, link);
            String href = link.replace("localhost", hostName);
            log.debug(propertiesFilename + " href: " + href);

            // Create a new Job.
            WebConversation conversation = new WebConversation();
            WebResponse response = null;
            Map parameters = new HashMap();
            parameters.put("PASS", "TRUE");
            parameters.put("RUNFOR", "1");
            String jobId = createJob(conversation, response, parameters, baseUrl, schemaUrl, propertiesFilename);

            // POST request to the phase resource.
            String resourceUrl = baseUrl + "/" + jobId + "/phase";
            log.debug("**************************************************");
            log.debug("HTTP POST: " + resourceUrl);
            WebRequest postRequest = new PostMethodWebRequest(resourceUrl);
            postRequest.setParameter("PHASE", "RUN");
            log.debug(getRequestParameters(postRequest));

            conversation.clearContents();
            response = conversation.getResponse(postRequest);
            assertNotNull(propertiesFilename + " POST response to " + resourceUrl + " is null", response);

            log.debug(getResponseHeaders(response));

            log.debug("response code: " + response.getResponseCode());
            assertEquals(propertiesFilename + " POST response code to " + resourceUrl + " should be 303", 303, response.getResponseCode());

            // Get the redirect.
            String location = response.getHeaderField("Location");
            log.debug("Location: " + location);
            assertNotNull(propertiesFilename + " POST response to " + resourceUrl + " location header not set", location);
//            assertEquals(propertiesFilename + " POST response to " + resourceUrl + " location header incorrect", baseUrl + "/" + jobId, location);

            // Follow the redirect.
            log.debug("**************************************************");
            log.debug("HTTP GET: " + location);
            WebRequest getRequest = new GetMethodWebRequest(location);
            conversation.clearContents();
            response = conversation.getResponse(getRequest);
            assertNotNull(propertiesFilename + " GET response to " + location + " is null", response);

            log.debug(getResponseHeaders(response));

            log.debug("response code: " + response.getResponseCode());
            assertEquals(propertiesFilename + " non-200 GET response code to " + location, 200, response.getResponseCode());

            log.debug("Content-Type: " + response.getContentType());
            assertEquals(propertiesFilename + " GET response Content-Type header to " + location + " is incorrect", ACCEPT_XML, response.getContentType());

            // Validate the XML against the schema.
            log.debug("XML:\r\n" + response.getText());
            buildDocument(schemaUrl, response.getText());

            // Get the results resource for this jobId.
            log.debug("**************************************************");
            log.debug("HTTP GET: " + location + "/results");
            getRequest = new GetMethodWebRequest(location + "/results");
            conversation.clearContents();
            response = conversation.getResponse(getRequest);
            assertNotNull(propertiesFilename + " GET response to " + resourceUrl + "/results is null", response);

            log.debug(getResponseHeaders(response));

            log.debug("response code: " + response.getResponseCode());
            assertEquals(propertiesFilename + " non-200 GET response code to " + resourceUrl, 200, response.getResponseCode());

            log.debug("Content-Type: " + response.getContentType());
            assertEquals(propertiesFilename + " GET response Content-Type header to " + resourceUrl + " is incorrect", ACCEPT_XML, response.getContentType());

            // Create DOM document from XML.
            log.debug("XML:\r\n" + response.getText());
            Document document = buildDocument(response.getText());

            Element root = document.getDocumentElement();
            assertNotNull(propertiesFilename + " XML returned from GET of " + resourceUrl + " missing root element", root);

            NodeList result = root.getElementsByTagName("uws:result");
            assertEquals(propertiesFilename + " uws:result element should only have a single element in XML returned from GET of " + resourceUrl, 1, result.getLength());

            NamedNodeMap attributes = result.item(0).getAttributes();
            log.debug("attributes: " + attributes.getLength());

            Node idNode = attributes.getNamedItem("id");
            log.debug(propertiesFilename + " result element id attribute: " + idNode.getNodeValue());
            assertEquals(propertiesFilename + " result element id attribute incorrect for GET of " + resourceUrl, id, idNode.getNodeValue());
            

            Node hrefNode = attributes.getNamedItem("xlink:href");
            log.debug(propertiesFilename + " result element xlink:href attribute:: " + hrefNode.getNodeValue());
            assertEquals(propertiesFilename + " result element xlink:href attribute incorrect for GET of " + resourceUrl, href, hrefNode.getNodeValue());

            deleteJob(conversation, response, jobId, propertiesFilename);
        }
    }

}
