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

import com.meterware.httpunit.GetMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

public class TestConfig extends TestCase
{
    private static final String PROPERTIES_DIRECTORY_ROOT = "RPS";
    private static final String PROPERTIES_DIRECTORY_PATH = "/installed/cadcTestUWS/test";

    public static final String ACCEPT_XML = "text/xml";

    public static final String[] PHASES =
    {
        "PENDING", "QUEUED", "EXECUTING", "COMPLETED", "ERROR", "ABORTED"
    };

    protected static Logger log;

    static
    {
        log = Logger.getLogger(TestConfig.class);

        // default log level is debug.
        log.setLevel((Level)Level.INFO);
    }

    public TestConfig(String testName)
    {
        super(testName);
    }

    protected File[] getPropertiesFiles(String className)
    {
        URI fileURI = null;
        try
        {
            String rootDirectory = System.getenv(PROPERTIES_DIRECTORY_ROOT);
            fileURI = new URI("file://" + rootDirectory + PROPERTIES_DIRECTORY_PATH);
        }
        catch (Exception e)
        {
            log.error(e.getMessage());
            return null;
        }
        File testDir = new File(fileURI);
        log.debug("properties files directory: " + testDir.getAbsolutePath());
        if (!testDir.canRead())
        {
            log.error("");
            return null;
        }
        PropertiesFilenameFilter filter = new PropertiesFilenameFilter(className);
        File[] files = testDir.listFiles(filter);
        if (files.length == 0)
        {
            log.error("");
            return null;
        }
        return files;
    }

    protected String getResponseHeaders(WebResponse response)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Response headers:");
        sb.append("\r\n");
        String[] headers = response.getHeaderFieldNames();
        for (int i = 0; i < headers.length; i++)
        {
            sb.append("\t");
            sb.append(headers[i]);
            sb.append("=");
            sb.append(response.getHeaderField(headers[i]));
            sb.append("\r\n");
        }
        return sb.toString();
    }

    protected String getRequestParameters(WebRequest request)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Request parameters:");
        sb.append("\r\n");
        String[] headers = request.getRequestParameterNames();
        for (int i = 0; i < headers.length; i++)
        {
            sb.append("\t");
            sb.append(headers[i]);
            sb.append("=");
            sb.append(request.getParameter(headers[i]));
            sb.append("\r\n");
        }
        return sb.toString();
    }

    protected boolean validatePhase(String value)
    {
        for (int i = 0; i < PHASES.length; i++)
        {
            if (PHASES[i].equals(value))
                return true;
        }
        return false;
    }

    protected Document buildDocument(String xml)
    {
        try
        {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder parser = factory.newDocumentBuilder();
            Document document = parser.parse(new InputSource(new StringReader(xml)));
            assertNotNull(document);
            return document;
        }
        catch (MalformedURLException mue) {
            fail(mue.getMessage());
        }
        catch (ParserConfigurationException pce) {
            fail(pce.getMessage());
        }
        catch (SAXException se) {
            fail(se.getMessage());
        }
        catch (IOException ioe)
        {
            fail(ioe.getMessage());
        }
        return null;
    }

    protected Document buildDocument(String schemaUrl, String xml)
    {
        ErrorHandler errorHandler = new MyErrorHandler();
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        sf.setErrorHandler(errorHandler);
        try
        {
            XMLReader reader = XMLReaderFactory.createXMLReader();
            SAXSource saxSource = new SAXSource(reader, new InputSource(new StringReader(xml)));

            Schema schema = sf.newSchema(new URL(schemaUrl));
            Validator validator = schema.newValidator();
            validator.setErrorHandler(errorHandler);
            validator.validate(saxSource);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
//            factory.setValidating(true);
//            factory.setNamespaceAware(true);
//            factory.setSchema(schema);
            DocumentBuilder parser = factory.newDocumentBuilder();
            Document document = parser.parse(new InputSource(new StringReader(xml)));
            assertNotNull(document);
            return document;
        }
        catch (MalformedURLException mue) {
            fail(mue.getMessage());
        }
        catch (ParserConfigurationException pce) {
            fail(pce.getMessage());
        }
        catch (SAXException se) {
            fail(se.getMessage());
        }
        catch (IOException ioe)
        {
            fail(ioe.getMessage());
        }
        return null;
    }

    class MyErrorHandler implements ErrorHandler
    {
        public void warning(SAXParseException e) throws SAXException
        {
            show("Warning", e);
            throw (e);
        }

        public void error(SAXParseException e) throws SAXException
        {
            show("Error", e);
            throw (e);
        }

        public void fatalError(SAXParseException e) throws SAXException {
            show("Fatal Error", e);
            throw (e);
        }

        private void show(String type, SAXParseException e)
        {
            log.debug(type + ": " + e.getMessage());
            log.debug("Line " + e.getLineNumber() + " Column " + e.getColumnNumber());
            log.debug("System ID: " + e.getSystemId());
        }
    }

    protected String createJob(WebConversation conversation, WebResponse response, 
                               String baseUrl, String schemaUrl, String propertiesFilename)
        throws IOException, SAXException
    {
        return createJob(conversation, response, null, baseUrl, schemaUrl);
    }

    protected String createJob(WebConversation conversation, WebResponse response, Map parameters, 
                               String baseUrl, String schemaUrl, String propertiesFilename)
        throws IOException, SAXException
    {
        String jobId = null;
        log.debug("**************************************************");
        log.debug("HTTP POST: " + baseUrl);

        // Create a new Job with default values.
        WebRequest postRequest = new PostMethodWebRequest(baseUrl);

        // Add parameters if available.
        if (parameters != null)
        {
            Set keys = parameters.keySet();
            for (Iterator it = keys.iterator(); it.hasNext(); )
            {
                String key = (String) it.next();
                String value = (String) parameters.get(key);
                postRequest.setParameter(key, value);
            }
        }

        log.debug(getRequestParameters(postRequest));

        response = conversation.getResponse(postRequest);
        assertNotNull(propertiesFilename + " POST response to " + baseUrl + " is null", response);

        log.debug(getResponseHeaders(response));

        log.debug("response code: " + response.getResponseCode());
        assertEquals(propertiesFilename + " POST response code to " + baseUrl + " should be 303", 303, response.getResponseCode());

        // Get the redirect.
        String location = response.getHeaderField("Location");
        log.debug("Location: " + location);
        assertNotNull(propertiesFilename + " POST response to " + baseUrl + " location header not set", location);

        // Parse the jobId from the redirect URL.
        URL locationUrl = new URL(location);
        String path = locationUrl.getPath();
        String[] paths = path.split("/");
        jobId = paths[paths.length - 1];
        log.debug("jobId: " + jobId);
        assertNotNull("jobId not found", jobId);

        // Check the Location header.
        assertEquals(propertiesFilename + " POST response to " + baseUrl + " location header incorrect", baseUrl + "/" + jobId, location);

        // Follow the redirect.
        log.debug("**************************************************");
        log.debug("HTTP GET: " + location);
        WebRequest getRequest = new GetMethodWebRequest(location);
        response = conversation.getResponse(getRequest);
        assertNotNull(propertiesFilename + " GET response to " + location + " is null", response);

        log.debug(getResponseHeaders(response));

        log.debug("response code: " + response.getResponseCode());
        assertEquals(propertiesFilename + " non-200 GET response code to " + location, 200, response.getResponseCode());

        log.debug("Content-Type: " + response.getContentType());
        assertEquals(propertiesFilename + " GET response Content-Type header to " + location + " is incorrect", ACCEPT_XML, response.getContentType());

        // Validate the XML against the schema and get a DOM Document.
        log.debug("XML:\r\n" + response.getText());
        Document document = buildDocument(schemaUrl, response.getText());

        Element root = document.getDocumentElement();
        assertNotNull(propertiesFilename + " XML returned from GET of " + location + " missing root element", root);

        // Job should have exactly one Execution Phase.
        NodeList list = root.getElementsByTagName("uws:phase");
        assertEquals(propertiesFilename + " XML returned from GET of " + location + " missing uws:phase element", 1, list.getLength());

        // Job should have exactly one Execution Duration.
        list = root.getElementsByTagName("uws:executionDuration");
        assertEquals(propertiesFilename + " XML returned from GET of " + location + " missing uws:executionDuration element", 1, list.getLength());

        // Job should have exactly one Deletion Time.
        list = root.getElementsByTagName("uws:destruction");
        assertEquals(propertiesFilename + " XML returned from GET of " + location + " missing uws:destruction element", 1, list.getLength());

        // Job should have exactly one Quote.
        list = root.getElementsByTagName("uws:quote");
        assertEquals(propertiesFilename + " XML returned from GET of " + location + " missing uws:quote element", 1, list.getLength());

        // Job should have exactly one Results List.
        list = root.getElementsByTagName("uws:results");
        assertEquals(propertiesFilename + " XML returned from GET of " + location + " missing uws:results element", 1, list.getLength());

        // Job should have zero or one Error.
        list = root.getElementsByTagName("uws:error");
        assertTrue(propertiesFilename + " XML returned from GET of " + location + " invalid number of uws:error elements", list.getLength() == 0 || list.getLength() == 1);

        return jobId;
    }

    /*
     * Default Job deletion uses an HTTP POST request.
     */
    protected void deleteJob(WebConversation conversation, WebResponse response, String jobId, String propertiesFile)
    {
        log.debug("Job deletion resource not available");
//        deleteJobWithPostRequest(conversation, response, jobId, propertiesFile);
    }

    /*
     * Delete a job using an HTTP POST request.
     */
    protected void deleteJobWithPostRequest(WebConversation conversation, WebResponse response, String baseUrl, 
                                            String schemaUrl, String jobId, String propertiesFilename)
        throws IOException, SAXException
    {
        String resourceUrl = baseUrl + "/" + jobId;
        log.debug("**************************************************");
        log.debug("HTTP POST: " + resourceUrl);
        WebRequest postRequest = new PostMethodWebRequest(baseUrl + "/" + jobId);
        postRequest.setParameter("ACTION", "DELETE");
        deleteJob(conversation, response, postRequest, schemaUrl, resourceUrl, propertiesFilename);
    }

    /*
     * Delete a Job using a HTTP DELETE request.
     */
    protected void deleteJobWithDeleteRequest(WebConversation conversation, WebResponse response, String baseUrl, 
                                              String schemaUrl, String jobId, String propertiesFilename)
        throws IOException, SAXException
    {
        String resourceUrl = baseUrl + "/" + jobId;
        log.debug("**************************************************");
        log.debug("HTTP DELETE: " + resourceUrl);
        WebRequest deleteRequest = new DeleteMethodWebRequest(baseUrl + "/" + jobId);
        deleteJob(conversation, response, deleteRequest, schemaUrl, resourceUrl, propertiesFilename);
    }

    private void deleteJob(WebConversation conversation, WebResponse response, WebRequest request, 
                           String schemaUrl, String resourceUrl, String propertiesFilename)
        throws IOException, SAXException
    {
        log.debug(getRequestParameters(request));

        conversation.clearContents();
        response = conversation.getResponse(request);
        assertNotNull(propertiesFilename + " response to is null", response);

        log.debug(getResponseHeaders(response));

        log.debug("response code: " + response.getResponseCode());
        assertEquals(propertiesFilename + " response code should be 303", 303, response.getResponseCode());

        // Get the redirect.
        String location = response.getHeaderField("Location");
        log.debug("Location: " + location);
        assertNotNull(propertiesFilename + " response location header not set", location);
        assertEquals(propertiesFilename + " response to location header incorrect", resourceUrl, location);

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
    }
    
}
