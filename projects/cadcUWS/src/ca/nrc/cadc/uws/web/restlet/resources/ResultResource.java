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
 * Dec 15, 2009 - 11:42:09 AM
 *
 * 
 * 
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.uws.web.restlet.resources;

import org.w3c.dom.Document;
import org.restlet.resource.Get;
import org.restlet.representation.Representation;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.Client;
import org.restlet.Response;
import org.restlet.data.Protocol;

import java.io.IOException;
import java.net.URL;

import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.Result;


public class ResultResource extends BaseJobResource
{
    /**
     * Obtain the XML Representation of this Request.
     *
     * @return The XML Representation, fully populated.
     */
    @Get
    @Override
    public Representation represent()
    {
        final Representation representation;

        if (getResult() == null)
        {
            representation = new EmptyRepresentation();
        }
        else
        {
            representation = getRemoteResult();
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
        // Do nothing.
    }

    /**
     * Obtain the current Result ID being requested.
     *
     * @return  String result ID.
     */
    protected String getResultID()
    {
        return getRequestAttribute("resultID");
    }

    /**
     * Obtain the current requested Result.
     *
     * @return      Result instance, or null if none found.
     */
    protected Result getResult()
    {
        final Job job = getJob();

        for (final Result result : job.getResultsList())
        {
            if (result.getName().equals(getResultID()))
            {
                return result;
            }
        }

        return null;
    }

    /**
     * Obtain the remote Result.
     *
     * @return  Representation of the remote Result.
     */
    protected Representation getRemoteResult()
    {
        final URL url = getResult().getURL();
        final Client client =
                new Client(getContext(),
                           Protocol.valueOf(url.getProtocol().toUpperCase()));
        final Response response = client.get(url.toString());

        return response.getEntity();
    }
}
