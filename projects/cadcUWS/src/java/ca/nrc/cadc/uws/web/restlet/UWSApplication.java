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
 * Jul 29, 2009 - 10:01:42 AM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.uws.web.restlet;

import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.Context;
import org.restlet.data.MediaType;


/**
 * The UWS Restlet Application.
 */
public class UWSApplication extends Application
{
    /**
     * Constructor.
     *
     * @param context The context to use based on parent component context. This
     *                context should be created using the
     *                {@link org.restlet.Context#createChildContext()} method to ensure a proper
     *                isolation with the other applications.
     */
    public UWSApplication(final Context context)
    {
        super(context);

        setStatusService(new UWSStatusService(true));

        // Make XML the preferred choice.
        getMetadataService().addExtension(MediaType.TEXT_XML.getName(),
                                          MediaType.TEXT_XML, true);
    }

    /**
     * Creates a root Restlet that will receive all incoming calls. In general,
     * instances of Router, Filter or Handler classes will be used as initial
     * application Restlet. The default implementation returns null by default.
     * This method is intended to be overridden by subclasses.
     *
     * @return The root Restlet.
     */
    @Override
    public Restlet createRoot()
    {
        return new UWSRouter(getContext());
    }
}
