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

package ca.nrc.cadc.uws.web.restlet;

import org.restlet.routing.Router;
import org.restlet.Context;
import org.apache.log4j.Logger;
import ca.nrc.cadc.uws.web.restlet.resources.*;
import ca.nrc.cadc.uws.util.StringUtil;
import ca.nrc.cadc.uws.InvalidResourceException;


/**
 * Default and only Router to map URLs to Resources.
 */
public class UWSRouter extends Router
{
    private final static Logger LOGGER = Logger.getLogger(UWSRouter.class);

    protected final static String UWS_ANY_RESOURCE = "ca.nrc.cadc.any.resource";
    protected final static String UWS_ANY_NAME = "ca.nrc.cadc.any.name";
    
    /**
     * Constructor.
     *
     * @param context The context.
     */
    public UWSRouter(final Context context)
    {
        super(context);

        attach("/async", AsynchResource.class);
        attach("/async/{jobID}", JobAsynchResource.class);
        attach("/async/{jobID}/phase", JobAsynchResource.class);
        attach("/async/{jobID}/executionDuration",
               JobAsynchResource.class);
        attach("/async/{jobID}/destruction", JobAsynchResource.class);
        attach("/async/{jobID}/error", ErrorResource.class);
        attach("/async/{jobID}/quote", JobAsynchResource.class);
        attach("/async/{jobID}/results", ResultListResource.class);
        attach("/async/{jobID}/parameters", ParameterListResource.class);
        attach("/async/{jobID}/owner", JobAsynchResource.class);
        attach("/async/{jobID}/execute", JobAsynchResource.class);

        if (StringUtil.hasText(
                context.getParameters().getFirstValue(UWS_ANY_NAME)))
        {
            final String anyClassName =
                    context.getParameters().getFirstValue(UWS_ANY_RESOURCE);

            if (!StringUtil.hasText(anyClassName))
            {
                throw new InvalidResourceException(
                        "The 'ANY' Server Resource is mandatory if the "
                        + UWS_ANY_NAME + " property is set.  Please set the "
                        + UWS_ANY_RESOURCE + " context-param in the web.xml "
                        + "to a ServerResource instance in the classpath, "
                        + "or set it in the Context programatically.");
            }

            try
            {
                attach("/async/{jobID}/"
                       + context.getParameters().getFirstValue(UWS_ANY_NAME),
                       Class.forName(anyClassName));
            }
            catch (ClassNotFoundException e)
            {
                LOGGER.error("No such class! >> " + anyClassName, e);
                throw new InvalidResourceException("No such class! >> "
                                                   + anyClassName, e);
            }
        }
    }
}
