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

import org.restlet.resource.Post;
import org.restlet.representation.Representation;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.text.ParseException;

import ca.nrc.cadc.uws.web.InvalidActionException;
import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.InvalidResourceException;
import ca.nrc.cadc.uws.JobAttribute;
import ca.nrc.cadc.uws.JobExecutor;
import ca.nrc.cadc.uws.JobRunner;
import ca.nrc.cadc.uws.JobWriter;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.uws.UWS;
import ca.nrc.cadc.uws.util.BeanUtil;
import java.security.Principal;

import java.text.DateFormat;
import java.util.Date;
import java.util.Set;
import javax.security.auth.Subject;
import org.jdom.Document;
import org.jdom.Element;
import org.restlet.data.Form;


/**
 * Asynchronous Job Resource.
 */
public class JobAsynchResource extends BaseJobResource
{
    private static final Logger LOGGER = Logger.getLogger(AsynchResource.class);
    private DateFormat dateFormat;

    public JobAsynchResource()
    {
        super();
        this.dateFormat = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
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
        Form form = new Form(entity);

        if (!jobModificationAllowed(form))
        {
            String message = "No further modifications are allowed for this Job";
            if (jobIsActive())
            {
                message += " unless it is to abort it";
            }
            throw new InvalidActionException(message);
        }

        JobRunner jobRunner = null;

        final String phase = form.getFirstValue(
                JobAttribute.EXECUTION_PHASE.getAttributeName().toUpperCase());

        if (pathInfo.endsWith("phase"))
        {
            if (phase.equals("RUN"))
            {
                if (jobIsPending())
                {
                    jobRunner = createJobRunner();
                    jobRunner.setJobManager(getJobManager());
                    jobRunner.setJob(job);
                    job.setExecutionPhase(ExecutionPhase.QUEUED);
                }
            }
            else if (phase.equals("ABORT"))
            {
                if (!jobHasRun())
                    job.setExecutionPhase(ExecutionPhase.ABORTED);
            }
            else
                throw new InvalidActionException("unexpected value for PHASE: " + phase);
        }
        else  if (pathInfo.endsWith("executionduration"))
        {
            String str = form.getFirstValue(JobAttribute.EXECUTION_DURATION.getAttributeName().toUpperCase());
            try
            {
                Long val = Long.parseLong(str);
                job.setExecutionDuration(val);
            }
            catch(NumberFormatException nex)
            {
                throw new InvalidActionException("failed to parse "
                        + JobAttribute.EXECUTION_DURATION.getAttributeName() + ": "
                        + str + " (expected a long integer)");
            }
                    
        }
        else if (pathInfo.endsWith("destruction"))
        {
            final String str =
                    form.getFirstValue(JobAttribute.DESTRUCTION_TIME.
                            getAttributeName().toUpperCase());
            try
            {
                Date val = dateFormat.parse(str);
                job.setDestructionTime(val);
            }
            catch (ParseException e)
            {
                throw new InvalidActionException("failed to parse "
                        + JobAttribute.DESTRUCTION_TIME.getAttributeName() + ": "
                        + str + " (expected format " + DateUtil.IVOA_DATE_FORMAT + ")");
            }
        }
        else
        {
            Set<String> paramNames = form.getNames();
            for (String p : paramNames)
            {
                if ( !JobAttribute.isValue(p))
                {
                    String[] vals = form.getValuesArray(p, true);
                    for (String v : vals)
                        job.addParameter(new Parameter(p, v));
                }
            }
        }

        this.job = getJobManager().persist(job);

        if (jobRunner != null)
            getJobExecutorService().execute(jobRunner, job.getOwner());

        redirectSeeOther(getHostPart() + job.getRequestPath() + "/" + job.getID());
    }

    /**
     * Obtain the XML Representation of a particular Attribute of a Job.
     *
     * @param document      The Document to build up.
     * @param pathInfo      Information on the current Path.
     */
    protected void buildAttributeXML(final Document document, final String pathInfo)
    {
        String text;
        final JobAttribute jobAttribute;

        if (pathInfo.endsWith("phase"))
        {
            text = job.getExecutionPhase().name();
            jobAttribute = JobAttribute.EXECUTION_PHASE;
        }
        else if (pathInfo.endsWith("executionduration"))
        {
            text = Long.toString(job.getExecutionDuration());
            jobAttribute = JobAttribute.EXECUTION_DURATION;
        }
        else if (pathInfo.endsWith("destruction"))
        {
            text = dateFormat.format(job.getDestructionTime());
            jobAttribute = JobAttribute.DESTRUCTION_TIME;
        }
        else if (pathInfo.endsWith("quote"))
        {
            text = dateFormat.format(job.getQuote());
            jobAttribute = JobAttribute.QUOTE;
        }
        else if (pathInfo.endsWith("owner"))
        {
            text = format(job.getOwner());
            jobAttribute = JobAttribute.OWNER_ID;
        }
        else
        {
            throw new InvalidResourceException("No such Resource > " + pathInfo);
        }

        Element element = new Element(jobAttribute.getAttributeName(), UWS.NS);
        element.addContent(text);
        document.addContent(element);
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
        final String pathInfo = getPathInfo();

        if (!pathInfo.endsWith(job.getID()))
        {
            buildAttributeXML(document, pathInfo);
        }
        else
        {
            buildJobXML(document);
        }
    }

    /**
     * Build the XML for the entire Job.
     * 
     * @param document The Document to build up.
     * @throws java.io.IOException If something went wrong or the XML cannot be
     *                             built.
     */
    protected void buildJobXML(final Document document) throws IOException
    {
        JobWriter writer = new JobWriter(job);
        document.setRootElement(writer.getDocument().detachRootElement());
    }

    protected JobExecutor getJobExecutorService()
    {
        return (JobExecutor) getContextAttribute(BeanUtil.UWS_EXECUTOR_SERVICE);
    }

    private String format(Subject s)
    {
        if (s != null)
        {
            Set<Principal> principals = s.getPrincipals();
            if (principals.size() > 0)
            {
                Principal p = principals.iterator().next();
                return p.getName();
            }
        }
        return "";
    }
}
