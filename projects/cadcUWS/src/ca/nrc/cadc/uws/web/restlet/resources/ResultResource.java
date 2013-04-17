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

import java.io.IOException;
import java.security.PrivilegedAction;

import javax.security.auth.Subject;

import org.jdom2.Document;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;

import ca.nrc.cadc.net.TransientException;
import ca.nrc.cadc.uws.Result;
import ca.nrc.cadc.uws.server.JobNotFoundException;
import ca.nrc.cadc.uws.server.JobPersistenceException;
import ca.nrc.cadc.uws.web.restlet.InvalidResourceException;


public class ResultResource extends BaseJobResource
{
    /**
     * Obtain the XML Representation of this Request.
     *
     */
    @Get
    @Override
    public Representation represent()
    {
        Subject subject = getSubject();
        if (subject == null) // anon
        {
            return doRepresent();
        }

        return (Representation) Subject.doAs(subject,
            new PrivilegedAction<Object>()
            {
                public Object run()
                {
                    return doRepresent();
                }
            } );
    }

    private Representation doRepresent()
    {
        try
        {
            String resultID = getRequestAttribute("resultID");
            if (job == null)
                this.job = getJobManager().get(jobID);
            for (Result result : job.getResultsList())
                if (result.getName().equals(resultID))
                {
                    redirectSeeOther(result.getURI().toASCIIString());
                    return null;
                }
            throw new InvalidResourceException("not found: " + jobID + "/results/" + resultID);
        }
        catch(JobNotFoundException ex)
        {
            throw new RuntimeException(ex);
        }
        catch (TransientException t)
        {
            return generateRetryRepresentation(t);
        }
        catch(JobPersistenceException ex)
        {
            throw new RuntimeException(ex);
        }
    }

    protected void buildXML(final Document document) throws IOException
    {
        // Do nothing.
    }
}
