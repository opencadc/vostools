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
 * Aug 7, 2009 - 11:05:31 AM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.uws.web.restlet;

import org.restlet.service.StatusService;
import org.restlet.data.Status;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.apache.log4j.Logger;


/**
 * Status service to handle errors more appropriately.
 */
public class UWSStatusService extends StatusService
{
    private static final Logger LOGGER =
            Logger.getLogger(UWSStatusService.class);


    /**
     * Constructor.
     *
     * @param enabled True if the service has been enabled.
     */
    public UWSStatusService(final boolean enabled)
    {
        super(enabled);
    }


    /**
     * Returns a status for a given exception or error. By default it unwraps
     * the status of {@link org.restlet.resource.ResourceException}. For other
     * exceptions or errors, it returns an
     * {@link org.restlet.data.Status#SERVER_ERROR_INTERNAL} status and logs a
     * severe message.<br>
     * <br>
     * In order to customize the default behavior, this method can be
     * overridden.
     *
     * @param throwable The exception or error caught.
     * @param request   The request handled.
     * @param response  The response updated.
     * @return The representation of the given status.
     */
    @Override
    public Status getStatus(final Throwable throwable, final Request request,
                            final Response response)
    {
        LOGGER.error("Unhandled exception or error intercepted", throwable);        
        return super.getStatus(throwable, request, response);
    }
}
