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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.ext.xml.DomRepresentation;
import org.restlet.data.MediaType;
import org.restlet.data.Form;
import org.restlet.data.Status;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.text.ParseException;
import java.net.URI;
import java.net.URISyntaxException;

import ca.nrc.cadc.uws.*;
import ca.nrc.cadc.uws.Error;
import ca.nrc.cadc.uws.util.DateUtil;
import ca.nrc.cadc.uws.util.StringUtil;
import ca.nrc.cadc.uws.web.validators.FormValidator;
import ca.nrc.cadc.uws.web.restlet.validators.JobFormValidatorImpl;
import ca.nrc.cadc.uws.web.WebRepresentationException;


/**
 * Resource to handle Asynchronous calls.
 */
public class AsynchResource extends UWSResource
{
    private final static Logger LOGGER = Logger.getLogger(UWSResource.class);


    /**
     * Accept POST requests.
     *
     * @param entity    The POST Request body.
     */
    @Post
    public void accept(final Representation entity)
    {
        final Form form = new Form(entity);
        final Map<String, String> errors = validate(form);
        
        if (!errors.isEmpty())
        {
            generateErrorRepresentation(errors);
            return;
        }

        final Job job;
        final String phase = form.getFirstValue(
                JobAttribute.EXECUTION_PHASE.getAttributeName().toUpperCase());
        final String duration = form.getFirstValue(
                JobAttribute.EXECUTION_DURATION.getAttributeName().
                        toUpperCase());
        final String owner = form.getFirstValue(
                JobAttribute.OWNER_ID.getAttributeName().toUpperCase());
        final String runID = form.getFirstValue(
                JobAttribute.RUN_ID.getAttributeName().toUpperCase());
//        final String[] parameters =
//                form.getValuesArray(JobAttribute.PARAMETERS.getAttributeName().
//                                            toUpperCase());
//
//        for (final String param : parameters)
//        {
//
//        }        

        try
        {
            final String destruction = form.getFirstValue(
                    JobAttribute.DESTRUCTION_TIME.getAttributeName().
                            toUpperCase());
            final Date destructionDate =
                    DateUtil.toDate(destruction, DateUtil.ISO8601_DATE_FORMAT);

            final String quote = form.getFirstValue(
                    JobAttribute.QUOTE.getAttributeName().toUpperCase());
            final Date quoteDate = DateUtil.toDate(quote,
                                                   DateUtil.ISO8601_DATE_FORMAT);

            final String start = form.getFirstValue(
                    JobAttribute.START_TIME.getAttributeName().toUpperCase());
            final Date startDate;

            if (StringUtil.hasText(start))
            {
                startDate = DateUtil.toDate(start,
                                            DateUtil.ISO8601_DATE_FORMAT);
            }
            else
            {
                startDate = null;
            }

            final String errorMessage = form.getFirstValue(
                    JobAttribute.ERROR_SUMMARY_MESSAGE.getAttributeName().
                            toUpperCase());
            final String errorDocumentURI = form.getFirstValue(
                    JobAttribute.ERROR_SUMMARY_DETAIL_LINK.getAttributeName().
                            toUpperCase());
            final Error error = new Error(errorMessage,
                                          new URI(errorDocumentURI));            

            job = new Job(null, ExecutionPhase.valueOf(phase.toUpperCase()),
                          Long.parseLong(duration), destructionDate, quoteDate,
                          startDate, null, error, owner, runID, null, null);
        }
        catch (ParseException e)
        {
            LOGGER.error("Unable to create Job! ", e);
            throw new WebRepresentationException("Unable to create Job!", e);
        }
        catch (URISyntaxException e)
        {
            LOGGER.error("The Error URI is invalid.", e);
            throw new WebRepresentationException("The Error URI is invalid.",
                                                 e);
        }

        final Job persistedJob = getJobService().persist(job);
        getResponse().setStatus(Status.REDIRECTION_SEE_OTHER);
        getResponse().setLocationRef(getContextPath() + "/async/"
                                     + persistedJob.getJobId());
    }

    /**
     * Validate the POST data.
     *
     * @param form        The form data to validate.
     * @return  True if the Form is fine for creation, False otherwise.
     */
    protected Map<String, String> validate(final Form form)
    {
        final FormValidator validator = new JobFormValidatorImpl(form);
        return validator.validate();
    }

    /**
     * Generate the error Representation.
     *
     * @param errors        Errors in the form.
     */
    protected void generateErrorRepresentation(final Map<String, String> errors)
    {
        final StringBuilder errorMessage = new StringBuilder(128);
        getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);

        errorMessage.append("Errors found during Job Creation: \n");

        for (final Map.Entry<String, String> error : errors.entrySet())
        {
            errorMessage.append("\n");
            errorMessage.append(error.getKey());
            errorMessage.append(": ");
            errorMessage.append(error.getValue());
        }

        getResponse().setEntity(
                new StringRepresentation(errorMessage.toString()));
    }

    /**
     * Obtain the XML Representation of this Request.
     *
     * @return      The XML Representation, fully populated.
     */
    @Get("xml")
    public Representation toXML()
    {
        try
        {
            final DomRepresentation rep =
                    new DomRepresentation(MediaType.TEXT_XML);
            buildXML(rep.getDocument());

            rep.getDocument().normalizeDocument();

            return rep;
        }
        catch (final IOException e)
        {
            LOGGER.error("Unable to create XML Document.");
            throw new WebRepresentationException(
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
        final Element jobsElement =
                document.createElementNS(XML_NAMESPACE_URI,
                                         JobAttribute.JOBS.getAttributeName());

        jobsElement.setAttribute("xmlns:xlink",
                                 "http://www.w3.org/1999/xlink");
        jobsElement.setAttribute("xmlns:xsi",
                                 "http://www.w3.org/2001/XMLSchema-instance");

        jobsElement.setPrefix(XML_NAMESPACE_PREFIX);

        for (final Job job : getJobs())
        {
            final Element jobRefElement =
                    document.createElementNS(XML_NAMESPACE_URI,
                                             JobAttribute.JOB_REF.
                                                     getAttributeName());
            
            jobRefElement.setPrefix(XML_NAMESPACE_PREFIX);
            jobRefElement.setAttribute("id", Long.toString(job.getJobId()));
            jobRefElement.setAttribute("xlink:href",
                                       getHostPart() + "/async/"
                                       + job.getJobId());
            
            final Element jobRefPhaseElement =
                    document.createElementNS(XML_NAMESPACE_URI,
                                             JobAttribute.EXECUTION_PHASE.
                                                     getAttributeName());
            jobRefPhaseElement.setPrefix(XML_NAMESPACE_PREFIX);
            jobRefPhaseElement.setTextContent(job.getExecutionPhase().name());

            jobRefElement.appendChild(jobRefPhaseElement);
            jobsElement.appendChild(jobRefElement);
        }

        document.appendChild(jobsElement);
    }

    /**
     * Obtain all of the Jobs available to list to the client.
     *
     * @return      List of Job objects.
     */
    protected List<Job> getJobs()
    {
        return new ArrayList<Job>(getJobService().getJobs());
    }
}
