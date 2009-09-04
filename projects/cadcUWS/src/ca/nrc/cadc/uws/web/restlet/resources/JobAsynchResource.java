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
import org.restlet.representation.StringRepresentation;
import org.restlet.ext.xml.DomRepresentation;
import org.restlet.data.*;
import org.restlet.Client;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.util.Map;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import ca.nrc.cadc.uws.*;
import ca.nrc.cadc.uws.InvalidResourceException;
import ca.nrc.cadc.uws.InvalidServiceException;
import ca.nrc.cadc.uws.JobExecutor;
import ca.nrc.cadc.uws.util.DateUtil;
import ca.nrc.cadc.uws.util.StringUtil;


/**
 * Asynchronous Job Resource.
 */
public class JobAsynchResource extends BaseJobResource
{
    private static final Logger LOGGER = Logger.getLogger(AsynchResource.class);

    protected JobExecutor jobExecutor;


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
            job.setExecutionDuration(
                    Long.parseLong(form.getFirstValue(
                            JobAttribute.EXECUTION_DURATION.
                                    getAttributeName().toUpperCase())));
        }
        else
        {
            throw new InvalidResourceException("No such Resource for POST > "
                                               + pathInfo);
        }

        getJobService().persist(job);

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

        je.setJobRunner(createJobRunner());
        je.execute(getJob());
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
            final String text;

            if (pathInfo.endsWith("execute"))
            {
                getJobExecutorService().execute(job);
                text = Long.toString(job.getJobId());
            }
            else if (pathInfo.endsWith("phase"))
            {
                text = job.getExecutionPhase().name();
            }
            else if (pathInfo.endsWith("executionDuration"))
            {
                text = Long.toString(job.getExecutionDuration());
            }
            else if (pathInfo.endsWith("destruction"))
            {
                text = DateUtil.toString(job.getDestructionTime(),
                                         DateUtil.ISO8601_DATE_FORMAT);
            }
            else if (pathInfo.endsWith("quote"))
            {
                text = DateUtil.toString(job.getQuote(),
                                         DateUtil.ISO8601_DATE_FORMAT);
            }
            else if (pathInfo.endsWith("owner"))
            {
                text = job.getOwner();
            }
            else
            {
                throw new InvalidResourceException("No such Resource > "
                                                   + pathInfo);
            }

            return new StringRepresentation(text);
        }
    }

    /**
     * Obtain the XML representation of this job.
     * 
     * @return  XML Representation.
     */
    protected Representation toXML()
    {
        try
        {
            final DomRepresentation rep =
                    new DomRepresentation(MediaType.TEXT_XML);
            buildXML(rep.getDocument());

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
                DateUtil.toString(job.getDestructionTime(),
                                  DateUtil.ISO8601_DATE_FORMAT));
        jobElement.appendChild(destructionTimeElement);

        // <uws:quote>
        final Element quoteElement =
                document.createElementNS(XML_NAMESPACE_URI,
                                         JobAttribute.QUOTE.
                                                 getAttributeName());
        quoteElement.setPrefix(XML_NAMESPACE_PREFIX);
        quoteElement.setTextContent(
                DateUtil.toString(job.getQuote(),
                                  DateUtil.ISO8601_DATE_FORMAT));
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
        if (jobExecutor == null)
        {
            setJobExecutorService(createJobExecutorService());
        }

        return jobExecutor;
    }

    public void setJobExecutorService(
            final JobExecutor jobExecutor)
    {
        this.jobExecutor = jobExecutor;
    }

    /**
     * Pull a new instance of the JobExecutorService implementation.
     *
     * @return  A JobExecutorService instance.
     */
    @SuppressWarnings("unchecked")
    protected JobExecutor createJobExecutorService()
    {
        if (!StringUtil.hasText(
                getContext().getParameters().getFirstValue(
                        UWS_EXECUTOR_SERVICE)))
        {
            throw new InvalidServiceException(
                    "Executor Service is mandatory!\n\n Please set the "
                    + UWS_EXECUTOR_SERVICE + "context-param in the web.xml, "
                    + "or insert it into the Context manually.");
        }
        
        final String executorClass = getContext().getParameters().
                getFirstValue(UWS_EXECUTOR_SERVICE);

        try
        {
            final Class<JobExecutor> clazz =
                    (Class<JobExecutor>) Class.forName(executorClass);
            final Constructor<JobExecutor>[] cons =
                    (Constructor<JobExecutor>[]) clazz.getConstructors();

            for (final Constructor<JobExecutor> con : cons)
            {
                final Class[] paramTypes = con.getParameterTypes();

                if ((paramTypes.length == 1)
                    && (paramTypes[0].equals(Map.class)))
                {
                    return (JobExecutor) Class.forName(executorClass).
                            getConstructor(Map.class).newInstance(
                            getContext().getParameters().getValuesMap());
                }
            }

            return (JobExecutor) Class.forName(executorClass).
                    newInstance();
        }
        catch (InvocationTargetException e)
        {
            LOGGER.error("Constructor threw exception >> " + executorClass, e);
            throw new InvalidServiceException("Constructor threw exception >> "
                                              + executorClass, e);
        }
        catch (NoSuchMethodException e)
        {
            LOGGER.error("No such Constructor for >> " + executorClass, e);
            throw new InvalidServiceException("No such Constructor for >> "
                                              + executorClass, e);
        }
        catch (ClassNotFoundException e)
        {
            LOGGER.error("No such Executor Service >> " + executorClass, e);
            throw new InvalidServiceException("No such Executor Service >> "
                                              + executorClass, e);
        }
        catch (IllegalAccessException e)
        {
            LOGGER.error("Class or Constructor is inaccessible for "
                         + "Executor Service >> " + executorClass, e);
            throw new InvalidServiceException("Class or Constructor is "
                                              + "inaccessible for Executor "
                                              + "Service >> " + executorClass,
                                              e);
        }
        catch (InstantiationException e)
        {
            LOGGER.error("Cannot create Executor Service instance >> "
                         + executorClass, e);
            throw new InvalidServiceException("Cannot create Executor Service "
                                              + "instance >> " + executorClass,
                                              e);
        }
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
                        UWS_RUNNER)))
        {
            throw new InvalidServiceException(
                    "The JobRunner is mandatory!\n\n Please set the "
                    + UWS_RUNNER + "context-param in the web.xml, "
                    + "or insert it into the Context manually.");
        }

        final String jobRunnerClass = getContext().getParameters().
                getFirstValue(UWS_RUNNER);

        try
        {
            return (JobRunner) Class.forName(jobRunnerClass).
                    newInstance();
        }
        catch (ClassNotFoundException e)
        {
            LOGGER.error("No such JobRunner >> " + jobRunnerClass, e);
            throw new InvalidServiceException("No such JobRunner >> "
                                              + jobRunnerClass, e);
        }
        catch (IllegalAccessException e)
        {
            LOGGER.error("Class or Constructor is inaccessible for "
                         + "JobRunner >> " + jobRunnerClass, e);
            throw new InvalidServiceException("Class or Constructor is "
                                              + "inaccessible for JobRunner "
                                              + ">> " + jobRunnerClass, e);
        }
        catch (InstantiationException e)
        {
            LOGGER.error("Cannot create JobRunner instance >> "
                         + jobRunnerClass, e);
            throw new InvalidServiceException("Cannot create JobRunner "
                                              + "instance >> " + jobRunnerClass,
                                              e);
        }
    }
}
