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


package ca.nrc.cadc.uws.web.restlet.resources;

import java.io.IOException;
import java.security.PrivilegedAction;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.List;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.restlet.data.Form;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobAttribute;
import ca.nrc.cadc.uws.JobWriter;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.uws.server.JobNotFoundException;
import ca.nrc.cadc.uws.server.JobPersistenceException;
import ca.nrc.cadc.uws.server.JobPhaseException;
import ca.nrc.cadc.uws.web.restlet.InvalidActionException;
import ca.nrc.cadc.uws.web.restlet.RestletJobCreator;


/**
 * Asynchronous Job Resource.
 */
public class JobAsynchResource extends BaseJobResource
{
    private static final Logger LOGGER = Logger.getLogger(JobAsynchResource.class);

    private static final String RUN = "RUN";
    private static final String ABORT = "ABORT";

    private DateFormat dateFormat;

    public JobAsynchResource()
    {
        super();
        this.dateFormat = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
    }

    /**
     * 
     * @author zhangsa
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
            if (job == null)
                job = getJobManager().get(jobID);
            StringRepresentation representation = null;

            final String pathInfo = getPathInfo();
            if (pathInfo.endsWith("phase"))
                representation = new StringRepresentation(job.getExecutionPhase().toString());
            else if (pathInfo.endsWith("executionduration"))
                representation = new StringRepresentation(Long.toString(job.getExecutionDuration()));
            else if (pathInfo.endsWith("destruction"))
                representation = new StringRepresentation(dateFormat.format(job.getDestructionTime()));
            else if (pathInfo.endsWith("quote"))
                representation = new StringRepresentation(dateFormat.format(job.getQuote()));
            else if (pathInfo.endsWith("owner"))
                representation = new StringRepresentation(job.getOwnerID());

            if (representation != null)
                return representation;

            return super.represent();
        }
        catch(JobPersistenceException ex)
        {
            throw new RuntimeException(ex);
        }
        catch(JobNotFoundException ex)
        {
            throw new RuntimeException(ex);
        }
    }

    @Delete
    public void delete(final Representation entity)
    {
        Subject subject = getSubject();
        if (subject == null) // anon
        {
            doDelete(entity);
        }
        else
        {
            Subject.doAs(subject, new PrivilegedAction<Object>()
            {
                public Object run()
                {
                    doDelete(entity);
                    return null;
                }
            } );
        }
    }

    private void doDelete(final Representation entity)
    {
        LOGGER.debug("delete() called. for job: " + jobID);
        try
        {   
            getJobManager().delete(jobID);
            redirectToJobList();
        }
        catch(JobPersistenceException ex)
        {
            throw new RuntimeException(ex);
        }
        catch(JobNotFoundException ex)
        {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * Accept POST requests.
     *
     * @param entity    The POST Request body.
     */
    @Post
    public void accept(final Representation entity)
    {
        final String pathInfo = getPathInfo();
        Subject subject = getSubject();
        if (subject == null) // anon
        {
            doAccept(pathInfo, entity);
        }
        else
        {
            Subject.doAs(subject, new PrivilegedAction<Object>()
            {
                public Object run()
                {
                    doAccept(pathInfo, entity);
                    return null;
                }
            } );
        }
    }

    private void doAccept(final String pathInfo, final Representation entity)
    {
        LOGGER.debug("doAccept: pathInfo=" + pathInfo);
        try
        {
            final Form form = new Form(entity);

            // phase changes
            if (pathInfo.endsWith("phase"))
            {
                String phase = form.getFirstValue(
                    JobAttribute.EXECUTION_PHASE.getAttributeName().toUpperCase());
                LOGGER.debug("request: PHASE="+phase);
                if (RUN.equalsIgnoreCase(phase))
                {
                    Job job = getJobManager().get(jobID);
                    job.setProtocol(protocol);
                    getJobManager().execute(job);
                }
                else if (ABORT.equalsIgnoreCase(phase))
                    getJobManager().abort(jobID);
                else
                    throw new InvalidActionException("invalid phase request: " + phase);
                redirectToJob();
                return;
            }

            // delete job hack
            if (pathInfo.endsWith(jobID))
            {
                String actionStr = form.getFirstValue("ACTION");
                LOGGER.debug("request: ACTION="+actionStr);
                if ("DELETE".equals(actionStr))
                {
                    LOGGER.debug("DELETE job through POST request. job: " + jobID);
                    getJobManager().delete(jobID);
                    redirectToJobList();
                    return;
                }
            }

            Long execDuration = null;
            Date destruction = null;
            Date quote = null;
            if (pathInfo.endsWith("executionduration"))
            {
                String str = form.getFirstValue(
                    JobAttribute.EXECUTION_DURATION.getAttributeName().toUpperCase());
                try
                {
                    execDuration = Long.parseLong(str);
                }
                catch(NumberFormatException nex)
                {
                    throw new InvalidActionException("failed to parse "
                            + JobAttribute.EXECUTION_DURATION.getAttributeName() + ": "
                            + str + " (expected an integer)");
                }
            }
            else if (pathInfo.endsWith("destruction"))
            {
                final String str =
                        form.getFirstValue(JobAttribute.DESTRUCTION_TIME.
                                getAttributeName().toUpperCase());
                try
                {
                    destruction = dateFormat.parse(str);
                }
                catch (ParseException e)
                {
                    throw new InvalidActionException("failed to parse "
                            + JobAttribute.DESTRUCTION_TIME.getAttributeName() + ": "
                            + str + " (expected format " + DateUtil.IVOA_DATE_FORMAT + ")");
                }
            }
            else if (pathInfo.endsWith("quote"))
            {
                final String str =
                        form.getFirstValue(JobAttribute.QUOTE.
                                getAttributeName().toUpperCase());
                try
                {
                    quote = dateFormat.parse(str);
                }
                catch (ParseException e)
                {
                    throw new InvalidActionException("failed to parse "
                            + JobAttribute.QUOTE.getAttributeName() + ": "
                            + str + " (expected format " + DateUtil.IVOA_DATE_FORMAT + ")");
                }
            }

            if (destruction != null || execDuration != null || quote != null)
            {
                LOGGER.debug("update " + jobID + ": " + destruction + "," + execDuration + "," + quote);
                getJobManager().update(jobID, destruction, execDuration, quote);
            }

            if ( pathInfo.endsWith(jobID) )
            {
                RestletJobCreator jobCreator = new RestletJobCreator(null);
                List<Parameter> parameters = jobCreator.getParameterList(form);
                if (!parameters.isEmpty())
                {
                    LOGGER.debug("update " + jobID + ": " + parameters.size() + " parameters");
                    getJobManager().update(jobID, parameters);
                }
            }
            redirectToJob();
        }
        catch(JobPersistenceException ex)
        {
            throw new RuntimeException(ex);
        }
        catch(JobPhaseException ex)
        {
            throw new RuntimeException(ex);
        }
        catch(JobNotFoundException ex)
        {
            throw new RuntimeException(ex);
        }
        finally { }
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
        JobWriter writer = new JobWriter();
        Element root = writer.getRootElement(job);
        document.setRootElement(root);
    }
}
