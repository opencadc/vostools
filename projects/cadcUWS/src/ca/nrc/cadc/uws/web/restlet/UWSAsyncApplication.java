/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2009.                            (c) 2009.
*  Government of Canada                 Gouvernement du Canada
*  National Research Council            Conseil national de recherches
*  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
*  All rights reserved                  Tous droits réservés
*                                       
*  NRC disclaims any warranties,        Le CNRC dénie toute garantie
*  expressed, implied, or               énoncée, implicite ou légale,
*  statutory, of any kind with          de quelque nature que ce
*  respect to the software,             soit, concernant le logiciel,
*  including without limitation         y compris sans restriction
*  any warranty of merchantability      toute garantie de valeur
*  or fitness for a particular          marchande ou de pertinence
*  purpose. NRC shall not be            pour un usage particulier.
*  liable in any event for any          Le CNRC ne pourra en aucun cas
*  damages, whether direct or           être tenu responsable de tout
*  indirect, special or general,        dommage, direct ou indirect,
*  consequential or incidental,         particulier ou général,
*  arising from the use of the          accessoire ou fortuit, résultant
*  software.  Neither the name          de l'utilisation du logiciel. Ni
*  of the National Research             le nom du Conseil National de
*  Council of Canada nor the            Recherches du Canada ni les noms
*  names of its contributors may        de ses  participants ne peuvent
*  be used to endorse or promote        être utilisés pour approuver ou
*  products derived from this           promouvoir les produits dérivés
*  software without specific prior      de ce logiciel sans autorisation
*  written permission.                  préalable et particulière
*                                       par écrit.
*                                       
*  This file is part of the             Ce fichier fait partie du projet
*  OpenCADC project.                    OpenCADC.
*                                       
*  OpenCADC is free software:           OpenCADC est un logiciel libre ;
*  you can redistribute it and/or       vous pouvez le redistribuer ou le
*  modify it under the terms of         modifier suivant les termes de
*  the GNU Affero General Public        la “GNU Affero General Public
*  License as published by the          License” telle que publiée
*  Free Software Foundation,            par la Free Software Foundation
*  either version 3 of the              : soit la version 3 de cette
*  License, or (at your option)         licence, soit (à votre gré)
*  any later version.                   toute version ultérieure.
*                                       
*  OpenCADC is distributed in the       OpenCADC est distribué
*  hope that it will be useful,         dans l’espoir qu’il vous
*  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
*  without even the implied             GARANTIE : sans même la garantie
*  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
*  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
*  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
*  General Public License for           Générale Publique GNU Affero
*  more details.                        pour plus de détails.
*                                       
*  You should have received             Vous devriez avoir reçu une
*  a copy of the GNU Affero             copie de la Licence Générale
*  General Public License along         Publique GNU Affero avec
*  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 4 $
*
************************************************************************
*/


package ca.nrc.cadc.uws.web.restlet;

import ca.nrc.cadc.uws.server.JobManager;
import ca.nrc.cadc.uws.web.InlineContentHandler;
import org.restlet.Restlet;
import org.restlet.Context;
import org.apache.log4j.Logger;
import org.restlet.Application;
import org.restlet.data.MediaType;
import org.restlet.routing.Router;

import ca.nrc.cadc.uws.web.restlet.resources.AsynchResource;
import ca.nrc.cadc.uws.web.restlet.resources.ErrorResource;
import ca.nrc.cadc.uws.web.restlet.resources.JobAsynchResource;
import ca.nrc.cadc.uws.web.restlet.resources.ParameterListResource;
import ca.nrc.cadc.uws.web.restlet.resources.ResultListResource;
import ca.nrc.cadc.uws.web.restlet.resources.ResultResource;

/**
 * The UWS Restlet Application to handle Asynchronous calls.
 */
public class UWSAsyncApplication extends Application
{
    private static final Logger log = Logger.getLogger(UWSAsyncApplication.class);

    public final static String UWS_JOB_MANAGER = JobManager.class.getName();
    public final static String UWS_INLINE_CONTENT_HANDLER = InlineContentHandler.class.getName();
    
    /**
     * Constructor. Note this constructor is convenient because you don't have
     * to provide a context like for {@link #Application(org.restlet.Context)}.
     * Therefore the context will initially be null. It's only when you attach
     * the application to a virtual host via one of its attach*() methods that
     * a proper context will be set.
     */
    public UWSAsyncApplication()
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
    public UWSAsyncApplication(final Context context)
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

    protected class JobRouter extends Router
    {
        public JobRouter(final Context context)
        {
            super(context);

            log.debug("attaching / -> AsynchResource");
            attach("", AsynchResource.class);
            log.debug("attaching /{jobID} -> JobAsynchResource");
            attach("/{jobID}", JobAsynchResource.class);
            
            //TemplateRoute jobRoute = attach("/{jobPath}", JobAsynchResource.class);
            //Map<String, Variable> jobRouteVariables = nodeRoute.getTemplate().getVariables();
            //jobRouteVariables.put("jobPath", new Variable(Variable.TYPE_ALL));
            
            attach("/{jobID}/phase", JobAsynchResource.class);
            attach("/{jobID}/executionduration", JobAsynchResource.class);
            attach("/{jobID}/destruction", JobAsynchResource.class);
            attach("/{jobID}/quote", JobAsynchResource.class);
            attach("/{jobID}/owner", JobAsynchResource.class);
            
            attach("/{jobID}/parameters", ParameterListResource.class);
            attach("/{jobID}/error", ErrorResource.class);
            attach("/{jobID}/results", ResultListResource.class);
            attach("/{jobID}/results/{resultID}", ResultResource.class);
        }
    }



    /**
     * This method does the setup of the restlet application. It loads a JobManager
     * implementation and stores it as a context attribute and then creates and
     * returns a JobRouter.
     *
     * @return The root Restlet.
     */
    @Override
    public Restlet createInboundRoot()
    {
        Context ctx = getContext();

        // load impl class and create the JobManager and attach to the context
        String cname = null;
        try
        {
            cname = getContext().getParameters().getFirstValue(UWS_JOB_MANAGER);
            Class c = Class.forName(cname);
            JobManager jm = (JobManager) c.newInstance();
            ctx.getAttributes().put(UWS_JOB_MANAGER, jm);
            log.info("created " + UWS_JOB_MANAGER + ": " + cname);          
        }
        catch (Exception ex)
        {
            log.error("CONFIGURATION ERROR: failed to instantiate JobManager implementation: "+  cname);
        }

        try
        {
            cname = getContext().getParameters().getFirstValue(UWS_INLINE_CONTENT_HANDLER);
            if (cname == null)
            {
                log.info("CONFIGURATION INFO: " + UWS_INLINE_CONTENT_HANDLER + " not configured in web.xml");
            }
            else
            {
                Class c = Class.forName(cname);
                ctx.getAttributes().put(UWS_INLINE_CONTENT_HANDLER, c);
                log.info("loaded " + UWS_INLINE_CONTENT_HANDLER + ": " + cname);
            }
        }
        catch (Exception ex)
        {
            log.error("CONFIGURATION ERROR: failed to load InlineContentHandler class: " +  cname);
        }
        return new JobRouter(ctx);
    }

    @Override
    public void stop()
        throws Exception
    {
        super.stop();
        JobManager jm = (JobManager) getContext().getAttributes().get(UWS_JOB_MANAGER);
        if (jm != null)
            jm.terminate();
        getContext().getAttributes().clear();
    }
}
