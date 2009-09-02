/******************************************************************************
 *
 *  Copyright (C) 2009                          Copyright (C) 2009
 *  National Research Council           Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6                     Ottawa, Canada, K1A 0R6
 *  All rights reserved                         Tous droits reserves
 *
 *  NRC disclaims any warranties,       Le CNRC denie toute garantie
 *  expressed, implied, or statu-       enoncee, implicite ou legale,
 *  tory, of any kind with respect      de quelque nature que se soit,
 *  to the software, including          concernant le logiciel, y com-
 *  without limitation any war-         pris sans restriction toute
 *  ranty of merchantability or         garantie de valeur marchande
 *  fitness for a particular pur-       ou de pertinence pour un usage
 *  pose.  NRC shall not be liable      particulier.  Le CNRC ne
 *  in any event for any damages,       pourra en aucun cas etre tenu
 *  whether direct or indirect,         responsable de tout dommage,
 *  special or general, consequen-      direct ou indirect, particul-
 *  tial or incidental, arising         ier ou general, accessoire ou
 *  from the use of the software.       fortuit, resultant de l'utili-
 *                                                              sation du logiciel.
 *
 *
 *  This file is part of cadcUWS.
 *
 *  cadcUWS is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  cadcUWS is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with cadcUWS.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package ca.nrc.cadc.uws.web.restlet.resources;

import org.restlet.representation.Representation;
import org.restlet.ext.xml.DomRepresentation;
import org.restlet.data.MediaType;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;

import ca.nrc.cadc.uws.web.WebRepresentationException;
import ca.nrc.cadc.uws.JobAttribute;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.Error;


public class ErrorResource extends BaseJobResource
{
    private static final Logger LOGGER = Logger.getLogger(ErrorResource.class);


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
        final Job job = getJob();
        final Error error = job.getError();

        // <uws:errorSummary>
        final Element errorSummaryElement =
                document.createElementNS(XML_NAMESPACE_URI,
                                         JobAttribute.ERROR_SUMMARY.
                                                 getAttributeName());
        errorSummaryElement.setPrefix(XML_NAMESPACE_PREFIX);

        if (error != null)
        {
            final Element errorSummaryMessageElement =
                    document.createElementNS(XML_NAMESPACE_URI,
                                             JobAttribute.ERROR_SUMMARY_MESSAGE.
                                                     getAttributeName());
            errorSummaryMessageElement.setPrefix(XML_NAMESPACE_PREFIX);
            errorSummaryMessageElement.setTextContent(
                    error.getSummaryMessage());

            final Element errorSummaryDetailLinkElement =
                    document.createElementNS(XML_NAMESPACE_URI,
                                             JobAttribute.ERROR_SUMMARY_DETAIL_LINK.
                                                     getAttributeName());
            errorSummaryDetailLinkElement.setPrefix(XML_NAMESPACE_PREFIX);
            errorSummaryDetailLinkElement.setAttribute("xlink:href",
                                                       error.getDocumentURI().
                                                               toString());

            errorSummaryElement.appendChild(errorSummaryMessageElement);
            errorSummaryElement.appendChild(errorSummaryDetailLinkElement);
        }

        document.appendChild(errorSummaryElement);
    }
}
