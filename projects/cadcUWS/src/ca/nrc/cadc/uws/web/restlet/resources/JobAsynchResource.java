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
        else if (pathInfo.endsWith("executionDuration"))
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
            throw new InvalidResourceException("No such Resource for POST > "
                                               + pathInfo);
        }

        getJobManager().persist(job);

        getResponse().setStatus(Status.REDIRECTION_SEE_OTHER);
        getResponse().setLocationRef(getContextPath() + "/async/"
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
     * Obtain the appropriate representation for the request.
     *
     * @return Representation instance.
     */
    protected Representation getRepresentation()
    {
        final Job job = getJob();
        String pathInfo = getRequest().getResourceRef().getPath().trim();

        if (pathInfo.endsWith("/"))
        {
            pathInfo = pathInfo.substring(0, pathInfo.length() - 1);
        }

        if (pathInfo.endsWith(Long.toString(job.getJobId())))
        {
            return toXML();
        }
        else
        {
            DateFormat df = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
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
            else if (pathInfo.endsWith("executionDuration"))
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

            return toXML(jobAttribute, text);
        }
    }

    /**
     * Obtain the XML Representation of a particular Attribute of a Job.
     *
     * @param jobAttribute      The Attribute to represent.
     * @param textContent       The text content for the Attribute.
     * @return                  The XML Representation.
     */
    protected Representation toXML(final JobAttribute jobAttribute,
                                   final String textContent)
    {
        try
        {
            final DomRepresentation rep =
                    new DomRepresentation(MediaType.TEXT_XML);
            final Document document = rep.getDocument();
            final Element elem =
                    document.createElementNS(XML_NAMESPACE_URI,
                                             jobAttribute.getAttributeName());
            elem.setPrefix(XML_NAMESPACE_PREFIX);
            elem.setTextContent(textContent);

            document.appendChild(elem);
            document.normalizeDocument();

            return rep;
        }
        catch (IOException e)
        {
            LOGGER.error("Unable to create representation.", e);
            throw new InvalidResourceException(
                    "Unable to create XML Document.", e);
        }
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
        DateFormat df = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
        
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

        // <uws:phase>
        final Element executionPhaseElement =
                document.createElementNS(XML_NAMESPACE_URI,
                                         JobAttribute.EXECUTION_PHASE.
                                                 getAttributeName());
        executionPhaseElement.setPrefix(XML_NAMESPACE_PREFIX);
        executionPhaseElement.setTextContent(job.getExecutionPhase().name());
        jobElement.appendChild(executionPhaseElement);

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

        // <uws:quote>
        final Element quoteElement =
                document.createElementNS(XML_NAMESPACE_URI,
                                         JobAttribute.QUOTE.
                                                 getAttributeName());
        quoteElement.setPrefix(XML_NAMESPACE_PREFIX);
        quoteElement.setTextContent(
                df.format(job.getQuote()) );
        jobElement.appendChild(quoteElement);

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

            errorSummaryElement.appendChild(errorSummaryMessageElement);
        }

        jobElement.appendChild(errorSummaryElement);
        
        // <uws:owner>
        final Element ownerNameElement =
                document.createElementNS(XML_NAMESPACE_URI,
                                         JobAttribute.OWNER_ID.
                                                 getAttributeName());
        ownerNameElement.setPrefix(XML_NAMESPACE_PREFIX);
        ownerNameElement.setAttribute("xsi:nil", "true");
        ownerNameElement.setTextContent(job.getOwner());
        jobElement.appendChild(ownerNameElement);

        // <uws:runId>
        final Element runIdElement =
                document.createElementNS(XML_NAMESPACE_URI,
                                         JobAttribute.RUN_ID.
                                                 getAttributeName());
        runIdElement.setPrefix(XML_NAMESPACE_PREFIX);
        runIdElement.setTextContent(job.getRunId());
        jobElement.appendChild(runIdElement);

        // <uws:results>
        final Element resultListElement =
                getRemoteElement(JobAttribute.RESULTS);

        if (resultListElement != null)
        {
            final Node importedResultList =
                    document.importNode(resultListElement, true);
            jobElement.appendChild(importedResultList);
        }

        // <uws:parameters>
        final Element parameterListElement =
                getRemoteElement(JobAttribute.PARAMETERS);

        if (parameterListElement != null)
        {
            final Node importedParameterList =
                    document.importNode(parameterListElement, true);
            jobElement.appendChild(importedParameterList);
        }
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
        return domRep.getDocument().getDocumentElement();
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
