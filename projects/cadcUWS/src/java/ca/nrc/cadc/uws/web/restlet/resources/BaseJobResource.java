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
 * Jul 14, 2009 - 11:12:26 AM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.uws.web.restlet.resources;

import ca.nrc.cadc.uws.Job;
import org.restlet.resource.Get;
import org.restlet.representation.Representation;


/**
 * Base Job Resource to obtain Jobs.
 */
public abstract class BaseJobResource extends UWSResource
{
    /**
     * Obtain the current Job in the context of this Request.
     *
     * @return      This Request's Job.
     */
    protected Job getJob()
    {
        return getJobService().getJob(getJobID());
    }

    /**
     * Obtain the XML Representation of this Request.
     *
     * @return      The XML Representation, fully populated.
     */
    @Get()
    public Representation represent()
    {
        return getRepresentation();
    }

    /**
     * Obtain the appropriate representation for the request.
     *
     * @return      Representation instance.
     */
    protected abstract Representation getRepresentation();

    /**
     * Obtain the current Job ID.
     *
     * @return  long Job ID
     */
    protected long getJobID()
    {
        return Long.parseLong(getRequestAttribute("jobID"));
    }

    protected String getRequestAttribute(final String attributeName)
    {
        return (String) getRequestAttributes().get(attributeName);
    }
}
