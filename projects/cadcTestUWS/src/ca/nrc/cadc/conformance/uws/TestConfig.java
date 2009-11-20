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
import com.meterware.httpunit.HeadMethodWebRequest;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
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
import static org.junit.Assert.*;

public class TestConfig
{
    private static Logger log = Logger.getLogger(TestConfig.class);

    private static final String SERVICE_SCHEMA_RESOURCE = "UWS-1.0.xsd";

    private static Validator validator;
    private static DocumentBuilder parser;

    protected static String serviceUrl;
    protected static String serviceSchema;

    public TestConfig()
    {
        // DEBUG is default.
        log.setLevel((Level)Level.INFO);
        
        // Base URL of the service to be tested.
        serviceUrl = System.getProperty("service.url");
        if (serviceUrl == null)
            throw new RuntimeException("service.url System property not set");
        log.debug("serviceUrl: " + serviceUrl);

        // Error handler for SAX parsing errors.
        ErrorHandler errorHandler = new MyErrorHandler();

        // Factory used to create a schema.
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        schemaFactory.setErrorHandler(errorHandler);

        // Load the schema file from the jar.
        InputStream inputStream = TestConfig.class.getClassLoader().getResourceAsStream(SERVICE_SCHEMA_RESOURCE);
        if (inputStream == null)
            throw new RuntimeException("Unable to load " + SERVICE_SCHEMA_RESOURCE + " from the jar.");
        try
        {
            serviceSchema = Util.inputStreamToString(inputStream);
            inputStream.close();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error reading " + SERVICE_SCHEMA_RESOURCE, e);
        }

        // Create a schema validator.
        Source source = new StreamSource(new StringReader(serviceSchema));
        Schema schema = null;
        try
        {
            schema = schemaFactory.newSchema(source);
        }
        catch (SAXException e)
        {
            throw new RuntimeException("SAX error parsing schema", e);
        }
        validator = schema.newValidator();
        validator.setErrorHandler(errorHandler);
        
        // Create a XML parser.
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        //factory.setValidating(true);
        //factory.setNamespaceAware(true);
        //factory.setSchema(schema);
        try
        {
            parser = factory.newDocumentBuilder();
        }
        catch (ParserConfigurationException e)
        {
            throw new RuntimeException("Error creating XML parser", e);
        }
    }

    protected Document xmlToDocument(String xml)
    {
        return sourceToDocument(new InputSource(new StringReader(xml)));
    }

    protected Document urlToDocument(String url)
    {
        return sourceToDocument(new InputSource(url));
    }

    protected Document sourceToDocument(InputSource source)
    {
        try
        {
            Document document = parser.parse(source);
            assertNotNull("XML parsing failed, document is null", document);
            return document;
        }
        catch (Exception e) {
            log.debug("XML parsing error: " + e.getMessage());
            fail("XML parsing error: " + e.getMessage());
        }
        return null;
    }

    protected Document buildDocument(String xml, boolean validate)
    {
        try
        {
            if (validate)
            {
                XMLReader reader = XMLReaderFactory.createXMLReader();
                InputSource source = new InputSource(new StringReader(xml));
                SAXSource saxSource = new SAXSource(reader, source);
                validator.validate(saxSource);
            }

            Document document = parser.parse(new InputSource(new StringReader(xml)));
            assertNotNull("XML parsing failed, document is null", document);
            return document;
        }
        catch (Exception e) {
            log.debug("XML parsing error: " + e.getMessage());
            fail("XML parsing error: " + e.getMessage());
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

    protected String createJob(WebConversation conversation)
        throws IOException, SAXException
    {
        return createJob(conversation, null);
    }

    protected String createJob(WebConversation conversation, Map<String, String> parameters)
        throws IOException, SAXException
    {
        String jobId = null;
        log.debug("**************************************************");
        log.debug("HTTP POST: " + serviceUrl);

        // Create a new Job with default values.
        WebRequest postRequest = new PostMethodWebRequest(serviceUrl);

        // Add parameters if available.
        if (parameters != null)
        {
            Set<Map.Entry<String, String>> values = parameters.entrySet();
            Iterator<Map.Entry<String, String>> iterator = values.iterator();
            while (iterator.hasNext())
            {
                Map.Entry<String, String> entry = iterator.next();
                postRequest.setParameter(entry.getKey(), entry.getValue());
            }
        }

        log.debug(Util.getRequestParameters(postRequest));

        WebResponse response = conversation.getResponse(postRequest);
        assertNotNull("POST response to " + serviceUrl + " is null", response);

        log.debug(Util.getResponseHeaders(response));

        log.debug("Response code: " + response.getResponseCode());
        assertEquals("POST response code to " + serviceUrl + " should be 303", 303, response.getResponseCode());

        // Get the redirect.
        String location = response.getHeaderField("Location");
        log.debug("Location: " + location);
        assertNotNull("POST response to " + serviceUrl + " location header not set", location);

        // Parse the jobId from the redirect URL.
        URL locationUrl = new URL(location);
        String path = locationUrl.getPath();
        String[] paths = path.split("/");
        jobId = paths[paths.length - 1];
        log.debug("jobId: " + jobId);
        assertNotNull("jobId not found", jobId);

        // Check the Location header.
//        assertEquals(propertiesFilename + " POST response to " + baseUrl + " location header incorrect", baseUrl + "/" + jobId, location);

        // Follow the redirect.
        response = get(conversation, location);

        // Validate the XML against the schema and get a DOM Document.
        log.debug("XML:\r\n" + response.getText());
        Document document = buildDocument(response.getText(), true);

        Element root = document.getDocumentElement();
        assertNotNull("XML returned from GET of " + location + " missing root element", root);

        // Job should have exactly one Execution Phase.
        NodeList list = root.getElementsByTagName("uws:phase");
        assertEquals("XML returned from GET of " + location + " missing uws:phase element", 1, list.getLength());

        // Job should have exactly one Execution Duration.
        list = root.getElementsByTagName("uws:executionDuration");
        assertEquals("XML returned from GET of " + location + " missing uws:executionDuration element", 1, list.getLength());

        // Job should have exactly one Deletion Time.
        list = root.getElementsByTagName("uws:destruction");
        assertEquals("XML returned from GET of " + location + " missing uws:destruction element", 1, list.getLength());

        // Job should have exactly one Quote.
        list = root.getElementsByTagName("uws:quote");
        assertEquals("XML returned from GET of " + location + " missing uws:quote element", 1, list.getLength());

        // Job should have exactly one Results List.
        list = root.getElementsByTagName("uws:results");
        assertEquals("XML returned from GET of " + location + " missing uws:results element", 1, list.getLength());

        // Job should have zero or one Error.
        list = root.getElementsByTagName("uws:error");
        assertTrue("XML returned from GET of " + location + " invalid number of uws:error elements", list.getLength() == 0 || list.getLength() == 1);

        return jobId;
    }

    protected WebResponse head(WebConversation conversation, String resourceUrl)
        throws IOException, SAXException
    {
        log.debug("**************************************************");
        log.debug("HTTP HEAD: " + resourceUrl);
        WebRequest getRequest = new HeadMethodWebRequest(resourceUrl);
        conversation.clearContents();
        WebResponse response = conversation.getResponse(getRequest);
        assertNotNull("HEAD response to " + resourceUrl + " is null", response);

        log.debug(Util.getResponseHeaders(response));

        log.debug("Response code: " + response.getResponseCode());
        assertEquals("Non-200 GET response code to " + resourceUrl, 200, response.getResponseCode());

        return response;
    }

    protected WebResponse get(WebConversation conversation, String resourceUrl)
        throws IOException, SAXException
    {
        log.debug("**************************************************");
        log.debug("HTTP GET: " + resourceUrl);
        WebRequest getRequest = new GetMethodWebRequest(resourceUrl);
        conversation.clearContents();
        WebResponse response = conversation.getResponse(getRequest);
        assertNotNull("GET response to " + resourceUrl + " is null", response);

        log.debug(Util.getResponseHeaders(response));

        log.debug("Response code: " + response.getResponseCode());
        assertEquals("Non-200 GET response code to " + resourceUrl, 200, response.getResponseCode());

        log.debug("Content-Type: " + response.getContentType());
//        assertEquals("GET response Content-Type header to " + resourceUrl + " is incorrect", "text/xml", response.getContentType());

        return response;
    }

    protected WebResponse post(WebConversation conversation, WebRequest request)
        throws IOException, SAXException
    {
        // POST request to the phase resource.
        log.debug("**************************************************");
        log.debug("HTTP POST: " + request.getURL().toString());
        log.debug(Util.getRequestParameters(request));

        conversation.clearContents();
        WebResponse response = conversation.getResponse(request);
        assertNotNull("POST response to " + request.getURL().toString() + " is null", response);

        log.debug(Util.getResponseHeaders(response));

        log.debug("Response code: " + response.getResponseCode());
        assertEquals("POST response code to " + request.getURL().toString() + " should be 303", 303, response.getResponseCode());

        // Get the redirect.
        String location = response.getHeaderField("Location");
        log.debug("Location: " + location);
        assertNotNull("POST response to " + request.getURL().toString() + " location header not set", location);
//      assertEquals(POST response to " + resourceUrl + " location header incorrect", baseUrl + "/" + jobId, location);

        return response;
    }

    /*
     * Default Job deletion uses an HTTP POST request.
     */
    protected WebResponse deleteJob(WebConversation conversation, String jobId)
    {
        log.debug("Job deletion resource not available");
        return null;
    }

    /*
     * Delete a job using an HTTP POST request.
     */
    protected WebResponse deleteJobWithPostRequest(WebConversation conversation, String jobId)
        throws IOException, SAXException
    {
        String resourceUrl = serviceUrl + "/" + jobId;
        log.debug("**************************************************");
        log.debug("HTTP POST: " + resourceUrl);
        WebRequest postRequest = new PostMethodWebRequest(serviceUrl + "/" + jobId);
        postRequest.setParameter("ACTION", "DELETE");
        return deleteJob(conversation, postRequest, resourceUrl);
    }

    /*
     * Delete a Job using a HTTP DELETE request.
     */
    protected WebResponse deleteJobWithDeleteRequest(WebConversation conversation, String jobId)
        throws IOException, SAXException
    {
        String resourceUrl = serviceUrl + "/" + jobId;
        log.debug("**************************************************");
        log.debug("HTTP DELETE: " + resourceUrl);
        WebRequest deleteRequest = new DeleteMethodWebRequest(serviceUrl + "/" + jobId);
        return deleteJob(conversation, deleteRequest, resourceUrl);
    }

    private WebResponse deleteJob(WebConversation conversation, WebRequest request, String resourceUrl)
        throws IOException, SAXException
    {
        log.debug(Util.getRequestParameters(request));

        conversation.clearContents();
        WebResponse response = conversation.getResponse(request);
        assertNotNull("Response to request is null", response);

        log.debug(Util.getResponseHeaders(response));

        log.debug("response code: " + response.getResponseCode());
        assertEquals("Response code should be 303", 303, response.getResponseCode());

        // Get the redirect.
        String location = response.getHeaderField("Location");
        log.debug("Location: " + location);
        assertNotNull("Response location header not set", location);
        assertEquals("Response to location header incorrect", resourceUrl, location);

        // Follow the redirect.
        log.debug("**************************************************");
        log.debug("HTTP GET: " + location);
        WebRequest getRequest = new GetMethodWebRequest(location);
        conversation.clearContents();
        response = conversation.getResponse(getRequest);
        assertNotNull("GET response to " + location + " is null", response);

        log.debug(Util.getResponseHeaders(response));

        log.debug("response code: " + response.getResponseCode());
        assertEquals("Non-200 GET response code to " + location, 200, response.getResponseCode());

        log.debug("Content-Type: " + response.getContentType());
        assertEquals("GET response Content-Type header to " + location + " is incorrect", "text/xml", response.getContentType());

        // Validate the XML against the schema.
        log.debug("XML:\r\n" + response.getText());
        buildDocument(response.getText(), true);

        return response;
    }
    
}
