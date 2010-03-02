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
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.text.ParseException;

import ca.nrc.cadc.uws.web.InvalidActionException;
import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.uws.ErrorSummary;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.InvalidResourceException;
import ca.nrc.cadc.uws.JobAttribute;
import ca.nrc.cadc.uws.JobExecutor;
import ca.nrc.cadc.uws.JobRunner;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.uws.util.BeanUtil;
import java.security.Principal;

import java.text.DateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import javax.security.auth.Subject;
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
            Map<String,String> params = form.getValuesMap();
            // Clear out those Request parameters that are pre-defined.
            for (final JobAttribute jobAttribute : JobAttribute.values())
            {
                params.remove(jobAttribute.getAttributeName().toUpperCase());
            }

            // The remaining values are new Parameters to the Job.
            for (final Map.Entry<String, String> entry : params.entrySet())
            {
                job.addParameter(new Parameter(entry.getKey(),
                                               entry.getValue()));
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
    protected void buildAttributeXML(final Document document,
                                     final String pathInfo)
    {
        String text;
        final JobAttribute jobAttribute;

        //if (pathInfo.endsWith("execute")) // what is this??
        //{
        //    executeJob();
        //    text = job.getID();
        //    jobAttribute = JobAttribute.JOB_ID;
        //}
        //else
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
            throw new InvalidResourceException("No such Resource > "
                                               + pathInfo);
        }

        final Element elem =
                document.createElementNS(XML_NAMESPACE_URI,
                                         jobAttribute.getAttributeName());
        elem.setPrefix(XML_NAMESPACE_PREFIX);
        elem.setTextContent(text);

        document.appendChild(elem);
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
        final Element jobElement = 
                document.createElementNS(XML_NAMESPACE_URI, 
                                        JobAttribute.JOB.getAttributeName());
        jobElement.setPrefix(XML_NAMESPACE_PREFIX);

        jobElement.setAttribute("xmlns:xlink", "http://www.w3.org/1999/xlink");
        jobElement.setAttribute("xmlns:xs", "http://www.w3.org/2001/XMLSchema");
        jobElement.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");

        document.appendChild(jobElement);

        // <uws:jobId>
        final Element jobIdElement =
                document.createElementNS(XML_NAMESPACE_URI,
                     JobAttribute.JOB_ID.getAttributeName());
        jobIdElement.setPrefix(XML_NAMESPACE_PREFIX);
        jobIdElement.setTextContent(job.getID());
        jobElement.appendChild(jobIdElement);

        // <uws:runId>
        final Element runIdElement =
                document.createElementNS(XML_NAMESPACE_URI,
                     JobAttribute.RUN_ID.getAttributeName());
        runIdElement.setPrefix(XML_NAMESPACE_PREFIX);
        runIdElement.setTextContent(job.getRunID());
        jobElement.appendChild(runIdElement);

        // <uws:ownerId>
        final Element ownerNameElement =
                document.createElementNS(XML_NAMESPACE_URI,
                     JobAttribute.OWNER_ID.getAttributeName());
        ownerNameElement.setPrefix(XML_NAMESPACE_PREFIX);
        if (job.getOwner() == null)
            ownerNameElement.setAttribute("xsi:nil", "true");
        else
            ownerNameElement.setTextContent(format(job.getOwner()));
        jobElement.appendChild(ownerNameElement);

        // <uws:phase>
        final Element executionPhaseElement =
                document.createElementNS(XML_NAMESPACE_URI,
                     JobAttribute.EXECUTION_PHASE.getAttributeName());
        executionPhaseElement.setPrefix(XML_NAMESPACE_PREFIX);
        executionPhaseElement.setTextContent(job.getExecutionPhase().name());
        jobElement.appendChild(executionPhaseElement);

        // <uws:quote>
        final Element quoteElement =
                document.createElementNS(XML_NAMESPACE_URI,
                     JobAttribute.QUOTE.getAttributeName());
        quoteElement.setPrefix(XML_NAMESPACE_PREFIX);
        quoteElement.setTextContent( dateFormat.format(job.getQuote()) );
        jobElement.appendChild(quoteElement);

        // <uws:startTime>
        final Element startTimeElement =
                document.createElementNS(XML_NAMESPACE_URI,
                     JobAttribute.START_TIME.getAttributeName());
        startTimeElement.setPrefix(XML_NAMESPACE_PREFIX);
        if (job.getStartTime() == null)
            startTimeElement.setAttribute("xsi:nil", "true");
        else
            startTimeElement.setTextContent( dateFormat.format(job.getStartTime()) );
        jobElement.appendChild(startTimeElement);

        // <uws:endTime>
         final Element endTimeElement =
                document.createElementNS(XML_NAMESPACE_URI,
                     JobAttribute.END_TIME.getAttributeName());
        endTimeElement.setPrefix(XML_NAMESPACE_PREFIX);
        if (job.getEndTime() == null)
            endTimeElement.setAttribute("xsi:nil", "true");
        else
            endTimeElement.setTextContent( dateFormat.format(job.getEndTime()) );
        jobElement.appendChild(endTimeElement);       

        // <uws:executionDuration>
        final Element executionDurationElement =
                document.createElementNS(XML_NAMESPACE_URI,
                     JobAttribute.EXECUTION_DURATION.getAttributeName());
        executionDurationElement.setPrefix(XML_NAMESPACE_PREFIX);
        executionDurationElement.setTextContent(
                Long.toString(job.getExecutionDuration()));
        jobElement.appendChild(executionDurationElement);

        // <uws:destructionTime>
        final Element destructionTimeElement =
                document.createElementNS(XML_NAMESPACE_URI,
                     JobAttribute.DESTRUCTION_TIME.getAttributeName());
        destructionTimeElement.setPrefix(XML_NAMESPACE_PREFIX);
        destructionTimeElement.setTextContent( dateFormat.format(job.getDestructionTime()) );
        jobElement.appendChild(destructionTimeElement);

        // <uws:parameters>
        Element parameterListElement = ParameterListResource.getElement(document, job);
        jobElement.appendChild(parameterListElement);
        

        // <uws:results>
        Element resultListElement = ResultListResource.getElement(document, job);
        jobElement.appendChild(resultListElement);

        // <uws:errorSummary>
        final ErrorSummary errorSummary = job.getErrorSummary();
        final Element errorSummaryElement =
                document.createElementNS(XML_NAMESPACE_URI,
                     JobAttribute.ERROR_SUMMARY.getAttributeName());
        errorSummaryElement.setPrefix(XML_NAMESPACE_PREFIX);

        if (errorSummary != null)
        {
            final Element errorSummaryMessageElement =
                    document.createElementNS(XML_NAMESPACE_URI,
                         JobAttribute.ERROR_SUMMARY_MESSAGE.getAttributeName());
            errorSummaryMessageElement.setPrefix(XML_NAMESPACE_PREFIX);
            errorSummaryMessageElement.setTextContent(
                    errorSummary.getSummaryMessage());

            final Element errorDocumentURIElement =
                    document.createElementNS(XML_NAMESPACE_URI,
                         JobAttribute.ERROR_SUMMARY_DETAIL_LINK.getAttributeName());
            errorDocumentURIElement.setPrefix(XML_NAMESPACE_PREFIX);
            errorDocumentURIElement.setAttribute(
                    "xlink:href", errorSummary.getDocumentURL() == null
                    ? ""
                    : errorSummary.getDocumentURL().toString());

            errorSummaryElement.appendChild(errorSummaryMessageElement);
            errorSummaryElement.appendChild(errorDocumentURIElement);
        }

        jobElement.appendChild(errorSummaryElement);
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
