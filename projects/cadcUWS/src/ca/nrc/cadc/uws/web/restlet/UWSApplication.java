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

import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.Context;
import org.restlet.routing.Router;
import org.restlet.data.MediaType;
import ca.nrc.cadc.uws.util.BeanUtil;
import ca.nrc.cadc.uws.util.StringUtil;
import ca.nrc.cadc.uws.InvalidServiceException;
import ca.nrc.cadc.uws.JobManager;
import ca.nrc.cadc.uws.JobPersistence;


/**
 * The UWS Restlet Application.
 */
public class UWSApplication extends Application
{
    /**
     * Constructor. Note this constructor is convenient because you don't have
     * to provide a context like for {@link #Application(org.restlet.Context)}.
     * Therefore the context will initially be null. It's only when you attach
     * the application to a virtual host via one of its attach*() methods that
     * a proper context will be set.
     */
    public UWSApplication()
    {
        init();
    }

    /**
     * Constructor.
     *
     * @param context The context to use based on parent component context. This
     *                context should be created using the
     *                {@link org.restlet.Context#createChildContext()} method to
     *                ensure a proper isolation with the other applications.
     */
    public UWSApplication(final Context context)
    {
        super(context);
        init();
    }


    /**
     * Method to initialize this Application.
     */
    private void init()
    {
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
     * This method will also setup singleton Service objects in the Context.
     *
     * @return The root Restlet.
     */
    @Override
    public Restlet createRoot()
    {
        final Router router = new UWSRouter(getContext());

        getContext().getAttributes().put(
                BeanUtil.UWS_EXECUTOR_SERVICE,
                createBean(BeanUtil.UWS_EXECUTOR_SERVICE, true));

        final JobManager jobManager =
                (JobManager) createBean(BeanUtil.UWS_JOB_MANAGER_SERVICE,
                                        true);
        final JobPersistence jobPersistence =
                (JobPersistence) createBean(BeanUtil.UWS_PERSISTENCE, true);

        jobManager.setJobPersistence(jobPersistence);

        getContext().getAttributes().put(BeanUtil.UWS_JOB_MANAGER_SERVICE,
                                         jobManager);

        getContext().getAttributes().put(BeanUtil.UWS_PERSISTENCE,
                                         jobPersistence);        

        return router;
    }

    /**
     * Create the object needed.
     *
     * @param contextBeanName       The Bean name in the context.
     * @param mandatory             Whether this bean is Mandatory.
     * @return                      The Object instantiated.
     */
    protected Object createBean(final String contextBeanName,
                                final boolean mandatory)
    {
        if (mandatory && !StringUtil.hasText(
                getContext().getParameters().getFirstValue(contextBeanName)))
        {
            throw new InvalidServiceException(
                    "This bean is mandatory!\n\n Please set the "
                    + contextBeanName + "context-param in the web.xml, "
                    + "or insert it into the Context manually.");
        }

        final String clazz = getContext().getParameters().
                getFirstValue(contextBeanName);
        final BeanUtil beanUtil = new BeanUtil(clazz);

        return beanUtil.createBean();
    }
}
