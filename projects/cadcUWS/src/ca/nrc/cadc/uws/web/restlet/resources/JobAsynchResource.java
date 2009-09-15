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
import org.restlet.ext.xml.DomRepresentation;
import org.restlet.data.*;
import org.restlet.Client;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.text.ParseException;

import ca.nrc.cadc.uws.*;
import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.uws.util.StringUtil;
import ca.nrc.cadc.uws.util.BeanUtil;

import java.text.DateFormat;


/**
 * Asynchronous Job Resource.
 */
public class JobAsynchResource extends BaseJobResource
{
    private static final Logger LOGGER = Logger.getLogger(AsynchResource.class);


    /**
     * Accept POST requests.
     *
     * @param entity    The POST Request body.
     */
    @Post
    public void accept(final Representation entity)
    {
        final Job job = getJob();
        final Form form = new Form(entity);
        
        String pathInfo = getRequest().getResourceRef().getPath().trim();

        if (pathInfo.endsWith("/"))
        {
            pathInfo = pathInfo.substring(0, pathInfo.length() - 1);
        }

        if (pathInfo.endsWith("phase"))
        {
            final String phase =
                    form.getFirstValue(JobAttribute.EXECUTION_PHASE.
                            getAttributeName().toUpperCase());

            if (phase.equals("RUN"))
            {
                job.setExecutionPhase(ExecutionPhase.QUEUED);
                executeJob();
            }
            else if (phase.equals("ABORT"))
            {
                job.setExecutionPhase(ExecutionPhase.ABORTED);
            }
        }
        else if (pathInfo.endsWith("executionduration"))
        {
            job.setExecutionDuration(
                    Long.parseLong(form.getFirstValue(
                            JobAttribute.EXECUTION_DURATION.
                                    getAttributeName().toUpperCase())));
        }
        else if (pathInfo.endsWith("destruction"))
        {
            final String destructionDateString =
                    form.getFirstValue(JobAttribute.DESTRUCTION_TIME.
                            getAttributeName().toUpperCase());
            try
            {
                job.setDestructionTime(
                        DateUtil.toDate(destructionDateString,
                                        DateUtil.IVOA_DATE_FORMAT));
            }
            catch (ParseException e)
            {
                LOGGER.error("Could not create Date from given String '"
                             + destructionDateString + "'.  Please ensure the "
                             + "format conforms to the ISO 8601 Format ('"
                             + DateUtil.IVOA_DATE_FORMAT + "'.");
            }

        }
        else
        {
            // Default is we're POSTing Parameters to the Job.
            final Client client = new Client(getContext(), Protocol.HTTP);
            client.post(getHostPart() + "/async/" + job.getJobId()
                        + "/parameters", form.getWebRepresentation());
        }

        getJobManager().persist(job);

        getResponse().setStatus(Status.REDIRECTION_SEE_OTHER);
        getResponse().setLocationRef(getHostPart() + "/async/"
                                     + job.getJobId());
    }

    /**
     * Execute the current Job.  This method will set a new Job Runner with
     * every execution to make it ThreadSafe.
     */
    protected void executeJob()
    {
        final JobExecutor je = getJobExecutorService();
        final JobRunner jobRunner = createJobRunner();

        jobRunner.setJob(getJob());
        je.execute(jobRunner);
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
        final Job job = getJob();
        final DateFormat df =
                DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT,
                                       DateUtil.UTC);
        final String text;
        final JobAttribute jobAttribute;

        if (pathInfo.endsWith("execute"))
        {
            executeJob();
            text = Long.toString(job.getJobId());
            jobAttribute = JobAttribute.JOB_ID;
        }
        else if (pathInfo.endsWith("phase"))
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
            text = df.format(job.getDestructionTime());
            jobAttribute = JobAttribute.DESTRUCTION_TIME;
        }
        else if (pathInfo.endsWith("quote"))
        {
            text = df.format(job.getQuote());
            jobAttribute = JobAttribute.QUOTE;
        }
        else if (pathInfo.endsWith("owner"))
        {
            text = job.getOwner();
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
        final Job job = getJob();
        String pathInfo = getRequest().getResourceRef().getPath().trim();

        if (pathInfo.endsWith("/"))
        {
            pathInfo = pathInfo.substring(0, pathInfo.length() - 1);
        }

        if (!pathInfo.endsWith(Long.toString(job.getJobId())))
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
        final DateFormat df = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT,
                                                     DateUtil.UTC);
        final Job job = getJob();
        final Element jobElement =
                document.createElementNS(XML_NAMESPACE_URI,
                                         JobAttribute.JOB.
                                                 getAttributeName());
        jobElement.setPrefix(XML_NAMESPACE_PREFIX);

        jobElement.setAttribute("xmlns:xlink", "http://www.w3.org/1999/xlink");
        jobElement.setAttribute("xmlns:xs",
                                "http://www.w3.org/2001/XMLSchema");
        jobElement.setAttribute("xmlns:xsi",
                                "http://www.w3.org/2001/XMLSchema-instance");

        document.appendChild(jobElement);

        // <uws:jobId>
        final Element jobIdElement =
                document.createElementNS(XML_NAMESPACE_URI,
                                         JobAttribute.JOB_ID.
                                                 getAttributeName());
        jobIdElement.setPrefix(XML_NAMESPACE_PREFIX);
        jobIdElement.setTextContent(Long.toString(job.getJobId()));
        jobElement.appendChild(jobIdElement);

        // <uws:runId>
        final Element runIdElement =
                document.createElementNS(XML_NAMESPACE_URI,
                                         JobAttribute.RUN_ID.
                                                 getAttributeName());
        runIdElement.setPrefix(XML_NAMESPACE_PREFIX);
        runIdElement.setTextContent(job.getRunId());
        jobElement.appendChild(runIdElement);

        // <uws:ownerId>
        final Element ownerNameElement =
                document.createElementNS(XML_NAMESPACE_URI,
                                         JobAttribute.OWNER_ID.
                                                 getAttributeName());
        ownerNameElement.setPrefix(XML_NAMESPACE_PREFIX);
        if (job.getOwner() == null)
            ownerNameElement.setAttribute("xsi:nil", "true");
        else
            ownerNameElement.setTextContent(job.getOwner());
        jobElement.appendChild(ownerNameElement);

        // <uws:phase>
        final Element executionPhaseElement =
                document.createElementNS(XML_NAMESPACE_URI,
                                         JobAttribute.EXECUTION_PHASE.
                                                 getAttributeName());
        executionPhaseElement.setPrefix(XML_NAMESPACE_PREFIX);
        executionPhaseElement.setTextContent(job.getExecutionPhase().name());
        jobElement.appendChild(executionPhaseElement);

        // <uws:quote>
        final Element quoteElement =
                document.createElementNS(XML_NAMESPACE_URI,
                                         JobAttribute.QUOTE.
                                                 getAttributeName());
        quoteElement.setPrefix(XML_NAMESPACE_PREFIX);
        quoteElement.setTextContent(
                df.format(job.getQuote()) );
        jobElement.appendChild(quoteElement);

        // <uws:startTime>
        final Element startTimeElement =
                document.createElementNS(XML_NAMESPACE_URI,
                                         JobAttribute.START_TIME.
                                                getAttributeName());
        startTimeElement.setPrefix(XML_NAMESPACE_PREFIX);
        if (job.getStartTime() == null)
            startTimeElement.setAttribute("xsi:nil", "true");
        else
            startTimeElement.setTextContent(
                    df.format(job.getStartTime()) );
        jobElement.appendChild(startTimeElement);

        // <uws:endTime>
         final Element endTimeElement =
                document.createElementNS(XML_NAMESPACE_URI,
                                         JobAttribute.END_TIME.
                                                getAttributeName());
        endTimeElement.setPrefix(XML_NAMESPACE_PREFIX);
        if (job.getEndTime() == null)
            endTimeElement.setAttribute("xsi:nil", "true");
        else
            endTimeElement.setTextContent(
                    df.format(job.getEndTime()) );
        jobElement.appendChild(endTimeElement);       

        // <uws:executionDuration>
        final Element executionDurationElement =
                document.createElementNS(XML_NAMESPACE_URI,
                                         JobAttribute.EXECUTION_DURATION.
                                                 getAttributeName());
        executionDurationElement.setPrefix(XML_NAMESPACE_PREFIX);
        executionDurationElement.setTextContent(
                Long.toString(job.getExecutionDuration()));
        jobElement.appendChild(executionDurationElement);

        // <uws:destructionTime>
        final Element destructionTimeElement =
                document.createElementNS(XML_NAMESPACE_URI,
                                         JobAttribute.DESTRUCTION_TIME.
                                                 getAttributeName());
        destructionTimeElement.setPrefix(XML_NAMESPACE_PREFIX);
        destructionTimeElement.setTextContent(
                df.format(job.getDestructionTime()) );
        jobElement.appendChild(destructionTimeElement);

        // <uws:parameters>
        final Element parameterListElement =
                getRemoteElement(JobAttribute.PARAMETERS);

        if (parameterListElement != null)
        {
            final Node importedParameterList =
                    document.importNode(parameterListElement, true);
            jobElement.appendChild(importedParameterList);
        }

        // <uws:results>
        final Element resultListElement =
                getRemoteElement(JobAttribute.RESULTS);

        if (resultListElement != null)
        {
            final Node importedResultList =
                    document.importNode(resultListElement, true);
            jobElement.appendChild(importedResultList);
        }

        // <uws:errorSummary>
        final ErrorSummary errorSummary = job.getErrorSummary();
        final Element errorSummaryElement =
                document.createElementNS(XML_NAMESPACE_URI,
                                         JobAttribute.ERROR_SUMMARY.
                                                 getAttributeName());
        errorSummaryElement.setPrefix(XML_NAMESPACE_PREFIX);

        if (errorSummary != null)
        {
            final Element errorSummaryMessageElement =
                    document.createElementNS(XML_NAMESPACE_URI,
                                             JobAttribute.ERROR_SUMMARY_MESSAGE.
                                                     getAttributeName());
            errorSummaryMessageElement.setPrefix(XML_NAMESPACE_PREFIX);
            errorSummaryMessageElement.setTextContent(
                    errorSummary.getSummaryMessage());

            final Element errorDocumentURIElement =
                    document.createElementNS(XML_NAMESPACE_URI,
                                             JobAttribute.ERROR_SUMMARY_DETAIL_LINK.
                                                     getAttributeName());
            errorDocumentURIElement.setPrefix(XML_NAMESPACE_PREFIX);
            errorDocumentURIElement.setTextContent(
                    errorSummary.getDocumentURI() == null
                    ? ""
                    : errorSummary.getDocumentURI().toString());

            errorSummaryElement.appendChild(errorSummaryMessageElement);
            errorSummaryElement.appendChild(errorDocumentURIElement);
        }

        jobElement.appendChild(errorSummaryElement);
     }

    /**
     * Obtain the XML List element for the given Attribute.
     *
     * Remember, the Element returned here belongs to the Document from the
     * Response of the call to get the List.  This means that the client of
     * this method call will need to import the Element, via the
     * Document#importNode method, or an exception will occur. 
     *
     * @param jobAttribute      The Attribute to obtain XML for.
     * @return                  The Element, or null if none found.
     * @throws IOException      If the Document could not be formed from the
     *                          Representation.
     */
    private Element getRemoteElement(final JobAttribute jobAttribute)
            throws IOException
    {
        final StringBuilder elementURI = new StringBuilder(128);
        final Client client = new Client(getContext(), Protocol.HTTP);

        elementURI.append(getHostPart());
        elementURI.append("/async/");
        elementURI.append(getJobID());
        elementURI.append("/");
        elementURI.append(jobAttribute.getAttributeName());

        final Response response = client.get(elementURI.toString());
        final DomRepresentation domRep =
                new DomRepresentation(response.getEntity());
        final Document document = domRep.getDocument();

        document.normalizeDocument();

        return document.getDocumentElement();
    }


    protected JobExecutor getJobExecutorService()
    {
        return (JobExecutor) getContextAttribute(BeanUtil.UWS_EXECUTOR_SERVICE);
    }


    /**
     * Obtain a new instance of the Job Runner interface as defined in the
     * Context
     *
     * @return  The JobRunner instance.
     */
    @SuppressWarnings("unchecked")
    protected JobRunner createJobRunner()
    {
        if (!StringUtil.hasText(
                getContext().getParameters().getFirstValue(
                        BeanUtil.UWS_RUNNER)))
        {
            throw new InvalidServiceException(
                    "The JobRunner is mandatory!\n\n Please set the "
                    + BeanUtil.UWS_RUNNER + "context-param in the web.xml, "
                    + "or insert it into the Context manually.");
        }

        final String jobRunnerClassName =
                getContext().getParameters().getFirstValue(BeanUtil.UWS_RUNNER);
        final BeanUtil beanUtil = new BeanUtil(jobRunnerClassName);

        return (JobRunner) beanUtil.createBean();
    }
}
