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
package ca.nrc.cadc.gms.server.web.restlet;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Reference;
import org.restlet.data.Status;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.restlet.security.User;

import ca.nrc.cadc.gms.WebRepresentationException;

/**
 * Base resource.
 */
public abstract class AbstractResource extends ServerResource
{
    private final static Logger LOGGER =
            Logger.getLogger(AbstractResource.class);

    protected final static String XML_PREFIX = "gms";


    /**
     * Hidden constructor for JavaBean tools.
     */
    AbstractResource()
    {
        super();
    }

    /**
     * Obtain the username of the currently authenticated User.
     *
     * @return  String authenticated username.
     */
    protected String getAuthenticatedUsername()
    {
        final User loggedInUser = getClientInfo().getUser();
        return loggedInUser.getIdentifier();
    }

    /**
     * Generate an XML representation of an error response.
     *
     * @param errorMessage
     *            the error message.
     * @param errorCode
     *            the error code.
     *
     * @return  The Error Representation XML.
     */
    protected Representation generateErrorRepresentation(
            final String errorMessage, final String errorCode)
    {
        // Generate the output representation
        final Document document = new Document();
        final Element errorElement = new Element("error");
        document.addContent(errorElement);

        final Element codeElement = new Element("code");
        codeElement.setText(errorCode);
        errorElement.addContent(codeElement);

        final Element messageElement = new Element("message");
        messageElement.setText(errorMessage);
        errorElement.addContent(messageElement);

        return new JDOMRepresentation(MediaType.TEXT_XML, document);
    }

    /**
     * Obtain the XML Representation of this Request.
     *
     * @return      The XML Representation, fully populated.
     */
    @Get("xml")
    public Representation represent()
    {
        try
        {
            // get the resource and return nothing if this fails or
            // if this is an HTTP HEAD request
            if (!obtainResource() || getMethod().equals(Method.HEAD))
            {
                return null;
            }

            Document document = new Document();
            buildXML(document);
            return new JDOMRepresentation(MediaType.TEXT_XML, document);
        }
        catch (final IOException e)
        {
            setExisting(false);
            LOGGER.error("Unable to create XML Document.");
            throw new WebRepresentationException(
                    "Unable to create XML Document.", e);
        }
        catch (final URISyntaxException e)
        {
            setExisting(false);
            LOGGER.error("Unable to create the group URI.");
            throw new WebRepresentationException(
                    "Unable to create XML Document.", e);
        }
    }
    
    /**
     * Get a reference to the resource identified by the user.
     * @return TODO
     * 
     * @throws FileNotFoundException If the resource doesn't exist.
     */
    protected abstract boolean obtainResource()
                throws FileNotFoundException, URISyntaxException;

    /**
     * Assemble the XML for this Resource's Representation into the given
     * Document.
     *
     * @param document The Document to build up.
     * @throws java.io.IOException If something went wrong or the XML cannot be
     *                             built.
     */
    protected abstract void buildXML(final Document document)
                throws IOException;

    /**
     * Build the host portion of any outgoing URL that will be intended for a
     * local call.  This is useful when building XML and wanting to call upon
     * a local Resource to build a portion of it.
     *
     * An example would look like: http://myhost/context
     *
     * @return      String Host part of a URI.
     */
    protected String getHostPart()
    {
        final StringBuilder elementURI = new StringBuilder(128);
        final Reference ref = getRequest().getResourceRef();

        elementURI.append(ref.getSchemeProtocol().getSchemeName());
        elementURI.append("://");
        elementURI.append(ref.getHostDomain());
        elementURI.append(getContextPath());

        return elementURI.toString();
    }

    /**
     * Obtain the equivalent of the Servlet Context Path.  This is usually
     * the context of the current web application, or the part of the URL
     * that comes after the host:port.
     *
     * In the example of http://myhost/myapp, this method would return
     * /myapp.
     *
     * @return      String Context Path.
     */
    protected String getContextPath()
    {
        final Reference ref = getRequest().getResourceRef();
        final String[] pieces = ref.getPath().split("/");
        final String pathPrepend;

        if (!ref.getPath().startsWith("/"))
        {
            pathPrepend = "/" + pieces[0];
        }
        else
        {
            pathPrepend = "/" + pieces[1];
        }

        return pathPrepend;
    }

    /**
     * Obtain a null safe array of values from the form for the given name.
     *
     * @param name      The field name.
     * @return          String[] array, with no null values.
     */
    protected String[] getNullSafeValuesArray(final String name)
    {
        final List<String> returnValues = new ArrayList<String>();
        final String[] values = getForm().getValuesArray(name);

        for (final String value : values)
        {
            if (value != null)
            {
                returnValues.add(value);
            }
        }

        return returnValues.toArray(new String[returnValues.size()]);
    }

    /**
     * Obtain the Form object, checking for a GETted Query Form first, then to
     * the POSTed Form.
     *
     * @return      A Form object.
     */
    protected Form getForm()
    {
        final Form form;

        if (getMethod().equals(Method.GET))
        {
            form = getQuery();
        }
        else
        {
            form = new Form(getRequest().getEntity());
        }

        return form;
    }

    /**
     * Process the exception and return something appropriate to the client.
     *
     * @param throwable     The exception to log.
     * @param status        The HTTP Status.
     * @param message       The message to return.
     */
    protected void processError(final Throwable throwable,
                                final Status status, final String message)
    {
        LOGGER.error(message + throwable.getMessage());
        LOGGER.debug(message, throwable);
        if (getMethod().equals(Method.HEAD))
        {
            getResponse().setEntity(
                    new EmptyRepresentation());
        }
        else
        {
            getResponse().setEntity(
                    new StringRepresentation(message, MediaType.TEXT_PLAIN));
        }
        getResponse().setStatus(status);
        
    }

    /**
     * Process the scenario of the user trying to access future functionality.
     *
     * @param message   The message to send to the user.
     */
    protected void processNotImplemented(final String message)
    {
        LOGGER.warn(message);

        getResponse().setEntity(
                new StringRepresentation(message, MediaType.TEXT_PLAIN));
        getResponse().setStatus(Status.SERVER_ERROR_NOT_IMPLEMENTED);
    }

    /**
     * Convenience method for obtaining an attribute from the Restlet Request.
     *
     * @param attributeName     Key to access an attribute.
     * @return                  Object associated with the given key.
     */
    public Object getRequestAttribute(final String attributeName)
    {
        return getRequest().getAttributes().get(attributeName);
    }
    
    /**
     * Obtain an OutputStream to write to.  This can be overridden.
     * @return      An OutputStream instance.
     */
    protected OutputStream getOutputStream()
    {
        return new ByteArrayOutputStream(256);
    }

    /**
     * Parse a Document from the given String.
     *
     * @param writtenData   The String data.
     * @return          The Document object.
     */
    protected Document parseDocument(final String writtenData)
    {
        final SAXBuilder parser = new SAXBuilder();

        try
        {
            return parser.build(new StringReader(writtenData));
        }
        catch (IOException e)
        {
            final String message = "Unable to parse document.";
            LOGGER.error(message, e);
            throw new WebRepresentationException(message, e);
        }
        catch (JDOMException e)
        {
            final String message = "Unable to parse document.";
            LOGGER.error(message, e);
            throw new WebRepresentationException(message, e);
        }
    }
    
}
