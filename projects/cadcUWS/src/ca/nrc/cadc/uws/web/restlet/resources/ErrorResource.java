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
import org.restlet.representation.EmptyRepresentation;
import org.restlet.Client;
import org.restlet.resource.Get;
import org.restlet.data.Protocol;
import org.restlet.data.Response;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;

import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.ErrorSummary;
import ca.nrc.cadc.uws.web.WebRepresentationException;

import java.net.MalformedURLException;
import java.io.IOException;


/**
 * Resource to handle supplying the Error document of a Job.
 */
public class ErrorResource extends BaseJobResource
{
    private static final Logger LOGGER = Logger.getLogger(ErrorResource.class);

    /**
     * Obtain the appropriate representation for the request.
     *
     * @return Representation instance.
     */
    @Get()
    protected Representation getRepresentation()
    {
        final Representation representation;
        final Job job = getJob();
        final ErrorSummary errorSummary = job.getErrorSummary();

        if (errorSummary == null)
        {
            representation = new EmptyRepresentation();
        }
        else
        {
            representation = getRemoteError();
        }

        return representation;
    }

    /**
     * Assemble the XML for this Resource's Representation into the given
     * Document.
     *
     * @param document The Document to build up.
     * @throws java.io.IOException If something went wrong or the XML cannot be
     *                             built.
     */
    protected void buildXML(final Document document) throws IOException
    {
        // Do Nothing.
    }

    /**
     * Hit the Error's URI to get the detailed Error.
     *
     * @return  Representation of the Error.
     */
    protected Representation getRemoteError()
    {
        final Client client = new Client(getContext(), Protocol.ALL);

        try
        {
            final Response response =
                    client.get(getJob().getErrorSummary().getDocumentURI().
                            toURL().toString());

            return response.getEntity();
        }
        catch (MalformedURLException e)
        {
            LOGGER.error("Unable to create URL for Error document.", e);
            throw new WebRepresentationException(
                    "Unable to create URL for Error document.", e);
        }
    }
}
