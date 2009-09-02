/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2009.                            (c) 2009.
 * National Research Council            Conseil national de recherches
 * Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 * All rights reserved                  Tous droits reserves
 *
 * NRC disclaims any warranties         Le CNRC denie toute garantie
 * expressed, implied, or statu-        enoncee, implicite ou legale,
 * tory, of any kind with respect       de quelque nature que se soit,
 * to the software, including           concernant le logiciel, y com-
 * without limitation any war-          pris sans restriction toute
 * ranty of merchantability or          garantie de valeur marchande
 * fitness for a particular pur-        ou de pertinence pour un usage
 * pose.  NRC shall not be liable       particulier.  Le CNRC ne
 * in any event for any damages,        pourra en aucun cas etre tenu
 * whether direct or indirect,          responsable de tout dommage,
 * special or general, consequen-       direct ou indirect, particul-
 * tial or incidental, arising          ier ou general, accessoire ou
 * from the use of the software.        fortuit, resultant de l'utili-
 *                                      sation du logiciel.
 *
 *
 * @author jenkinsd
 * Aug 4, 2009 - 7:44:11 AM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.uws.web.restlet.resources;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.restlet.representation.Representation;
import org.restlet.ext.xml.DomRepresentation;
import org.restlet.data.MediaType;
import org.apache.log4j.Logger;

import java.io.IOException;

import ca.nrc.cadc.uws.JobAttribute;
import ca.nrc.cadc.uws.Result;
import ca.nrc.cadc.uws.web.exceptions.WebRepresentationException;


/**
 * Resource to handle the Result List.
 */
public class ResultListResource extends BaseJobResource
{
    public static final Logger LOGGER =
            Logger.getLogger(ResultListResource.class);


    /**
     * Obtain the appropriate representation for the request.
     *
     * @return Representation instance.
     */
    protected Representation getRepresentation()
    {
        return toXML();
    }

    /**
     * Obtain the XML Representation of this Request.
     *
     * @return      The XML Representation, fully populated.
     */
    public Representation toXML()
    {
        try
        {
            final DomRepresentation rep =
                    new DomRepresentation(MediaType.TEXT_XML);
            buildXML(rep.getDocument());

            rep.getDocument().normalizeDocument();

            return rep;
        }
        catch (final IOException e)
        {
            LOGGER.error("Unable to create XML Document.");
            throw new WebRepresentationException(
                    "Unable to create XML Document.", e);
        }
    }

    /**
     * Assemble the XML for this Resource's Representation into the given
     * Document.
     *
     * @param document The Document to build up.
     */
    protected void buildXML(final Document document)
    {
        final Element resultsListElement =
                document.createElementNS(XML_NAMESPACE_URI,
                                         JobAttribute.RESULTS.
                                                 getAttributeName());
        resultsListElement.setPrefix(XML_NAMESPACE_PREFIX);

        for (final Result result : getJob().getResultsList())
        {
            final Element resultElement =
                    document.createElementNS(XML_NAMESPACE_URI,
                                             JobAttribute.RESULT.
                                                     getAttributeName());
            resultElement.setPrefix(XML_NAMESPACE_PREFIX);
            resultElement.setAttribute("id", result.getName());
            resultElement.setAttribute("xlink:href",
                                       result.getURL().toExternalForm());
            resultsListElement.appendChild(resultElement);
        }

        document.appendChild(resultsListElement);
    }
}
