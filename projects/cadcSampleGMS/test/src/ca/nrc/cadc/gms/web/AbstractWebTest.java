/**
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2010.                            (c) 2010.
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
 ************************************************************************
 */
package ca.nrc.cadc.gms.web;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.xml.sax.SAXException;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import com.meterware.httpunit.WebResponse;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.GetMethodWebRequest;

import java.io.IOException;
import java.io.StringReader;
import java.net.URL;

public class AbstractWebTest
{
    private final static Logger LOGGER =
            Logger.getLogger(AbstractWebTest.class);
    private static final String PARSER = "org.apache.xerces.parsers.SAXParser";    

    private String serviceSchema;
    private String serviceURL;
    private SAXBuilder parser;
    private SAXBuilder validatingParser;

    protected static final int REQUEST_TIMEOUT = 30;    


    public AbstractWebTest()
    {
        LOGGER.setLevel(Level.DEBUG);

        // Base URL of the service to be tested.
        final String serviceUrl = System.getProperty("service.url");
        if (serviceUrl == null)
        {
            throw new RuntimeException("service.url System property not set");
        }
        else
        {
            setServiceURL(serviceUrl);
        }

        LOGGER.info("serviceUrl: " + getServiceURL());

        final URL schemaURL = AbstractWebTest.class.getClassLoader().
                getResource("VOSIAvailability-v1.0.xsd");
        setServiceSchema(schemaURL.toString());

        LOGGER.info("Service Schema: " + getServiceSchema());

        setParser(new SAXBuilder(PARSER, false));
        getParser().setProperty(
                "http://apache.org/xml/properties/schema/external-schemaLocation",
                "http://www.ivoa.net/xml/VOSIAvailability/v1.0 "
                + getServiceSchema());

        setValidatingParser(new SAXBuilder(PARSER, true));
        getValidatingParser().setFeature(
                "http://xml.org/sax/features/validation", true);
        getValidatingParser().setFeature(
                "http://apache.org/xml/features/validation/schema", true);
        getValidatingParser().setFeature(
                "http://apache.org/xml/features/validation/schema-full-checking",
                true);
        getValidatingParser().setProperty(
                "http://apache.org/xml/properties/schema/external-schemaLocation",
                "http://www.ivoa.net/xml/VOSIAvailability/v1.0 "
                + getServiceSchema());        

        LOGGER.debug("Done with Abstract Web Test constructor.");
    }

    protected String get(final String resourceURL)
            throws IOException, SAXException
    {
        return get(new WebConversation(), resourceURL).getText();
    }

    protected WebResponse get(final WebConversation conversation,
                              final String resourceUrl)
        throws IOException, SAXException
    {
        LOGGER.debug("**************************************************");
        LOGGER.debug("HTTP GET: " + resourceUrl);
        final WebRequest getRequest = new GetMethodWebRequest(resourceUrl);
        conversation.clearContents();

        final WebResponse response = conversation.getResponse(getRequest);
        assertNotNull("GET response to " + resourceUrl + " is null", response);

        LOGGER.debug(getResponseHeaders(response));

        LOGGER.debug("Response code: " + response.getResponseCode());
        assertEquals("Non-200 GET response code to " + resourceUrl, 200,
                     response.getResponseCode());

        LOGGER.debug("Content-Type: " + response.getContentType());

        return response;
    }

    protected WebResponse post(final WebConversation conversation,
                               final WebRequest request)
        throws IOException, SAXException
    {
        // POST request to the phase resource.
        LOGGER.debug("**************************************************");
        LOGGER.debug("HTTP POST: " + request.getURL().toString());
        LOGGER.debug(getRequestParameters(request));

        conversation.clearContents();
        final WebResponse response = conversation.getResponse(request);
        assertNotNull("POST response to " + request.getURL().toString()
                      + " is null", response);

        LOGGER.debug(getResponseHeaders(response));

        LOGGER.debug("Response code: " + response.getResponseCode());
        assertEquals("POST response code to " + request.getURL().toString()
                     + " should be 303", 303, response.getResponseCode());

        // Get the redirect.
        final String location = response.getHeaderField("Location");
        LOGGER.debug("Location: " + location);
        assertNotNull("POST response to " + request.getURL().toString()
                      + " location header not set", location);

        return response;
    }

    public String getResponseHeaders(final WebResponse response)
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("Response headers:");
        sb.append("\r\n");

        for (final String header : response.getHeaderFieldNames())
        {
            sb.append("\t");
            sb.append(header);
            sb.append("=");
            sb.append(response.getHeaderField(header));
            sb.append("\r\n");
        }

        return sb.toString();
    }

    public String getRequestParameters(final WebRequest request)
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("Request parameters:");
        sb.append("\r\n");

        for (final String header : request.getRequestParameterNames())
        {
            sb.append("\t");
            sb.append(header);
            sb.append("=");
            sb.append(request.getParameter(header));
            sb.append("\r\n");
        }

        return sb.toString();
    }

    protected Document buildDocument(final String xml, final boolean validate)
        throws IOException, JDOMException
    {
        if (validate)
        {
            return getValidatingParser().build(new StringReader(xml));
        }
        else
        {
            return getParser().build(new StringReader(xml));
        }
    }    


    public String getServiceSchema()
    {
        return serviceSchema;
    }

    public void setServiceSchema(String serviceSchema)
    {
        this.serviceSchema = serviceSchema;
    }

    public String getServiceURL()
    {
        return serviceURL;
    }

    public void setServiceURL(final String serviceURL)
    {
        this.serviceURL = serviceURL;
    }

    public SAXBuilder getParser()
    {
        return parser;
    }

    public void setParser(final SAXBuilder parser)
    {
        this.parser = parser;
    }

    public SAXBuilder getValidatingParser()
    {
        return this.validatingParser;
    }

    public void setValidatingParser(final SAXBuilder validatingParser)
    {
        this.validatingParser = validatingParser;
    }
}
