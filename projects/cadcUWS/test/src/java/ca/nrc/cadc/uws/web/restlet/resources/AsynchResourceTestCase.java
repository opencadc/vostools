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

import junit.framework.TestCase;
import static org.easymock.EasyMock.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.Diff;
import ca.nrc.cadc.uws.*;
import ca.nrc.cadc.uws.util.DateUtil;

import java.util.*;


/**
 * TestCase for the Asynchronous Resource.
 */
public class AsynchResourceTestCase extends TestCase
{
    protected final String XML_NAMESPACE_PREFIX = "uws";


    protected Date destructionTime;
    protected AsynchResource asynchResource;
    protected Document documentToBuild;
    protected Document expectedDocument;
    protected Job mockJob;
    protected List<Result> results;
    protected List<Parameter> parameters;


    /**
     * Sets up the fixture, for example, open a network connection.
     * This method is called before a test is executed.
     */
    @Override
    protected void setUp() throws Exception
    {
        final Calendar cal = Calendar.getInstance();
        cal.set(1997, Calendar.NOVEMBER, 25, 3, 21, 0);
        cal.set(Calendar.MILLISECOND, 0);

        results = new ArrayList<Result>();
        parameters = new ArrayList<Parameter>();

        mockJob = createMock(Job.class);
        expect(mockJob.getJobId()).andReturn(88l).anyTimes();
//        expect(mockJob.getErrorSummary()).andReturn("ERROR_MESSAGE").anyTimes();
        expect(mockJob.getExecutionPhase()).
                andReturn(ExecutionPhase.EXECUTING).anyTimes();
        expect(mockJob.getExecutionDuration()).andReturn(88l).anyTimes();
        expect(mockJob.getDestructionTime()).andReturn(cal.getTime()).
                anyTimes();
        expect(mockJob.getOwner()).andReturn("USER").anyTimes();
        expect(mockJob.getRunId()).andReturn("RUNID").anyTimes();
        
        cal.set(1977, Calendar.NOVEMBER, 25, 8, 30, 0);
        expect(mockJob.getQuote()).andReturn(cal.getTime()).anyTimes();
        
        expect(mockJob.getResultsList()).andReturn(results).anyTimes();
        expect(mockJob.getParameterList()).andReturn(parameters).
                anyTimes();

        replay(mockJob);

        asynchResource = new AsynchResource()
        {
            /**
             * Obtain the current Job in the context of this Request.
             *
             * @return This Request's Job.
             */
            @Override
            protected List<Job> getJobs()
            {
                return new ArrayList<Job>(Arrays.asList(mockJob));
            }
        };

        documentToBuild = XMLUnit.newControlParser().newDocument();
        expectedDocument = XMLUnit.newControlParser().newDocument();
        buildTestXML(expectedDocument);        
    }

    /**
     * Tears down the fixture, for example, close a network connection.
     * This method is called after a test is executed.
     */
    @Override
    protected void tearDown() throws Exception
    {
        asynchResource = null;
        documentToBuild = null;
    }


    public void testBuildXML() throws Exception
    {
        asynchResource.buildXML(documentToBuild);

        final Diff diff = new Diff(expectedDocument, documentToBuild);
        assertTrue("Good document.", diff.identical());
    }

    protected void buildTestXML(final Document document)
    {
        final Element jobElement =
                document.createElementNS(XML_NAMESPACE_PREFIX,
                                         JobAttribute.JOB.
                                                 getAttributeName());
        jobElement.setAttributeNS("xsi", "schemaLocation",
                                  "http://www.ivoa.net/xml/UWS/v1.0rc3 UWS.xsd");
        jobElement.setAttributeNS("xlmns", "xml",
                                  "http://www.w3.org/XML/1998/namespace");
        jobElement.setAttributeNS("xlmns", "uws",
                                  "http://www.ivoa.net/xml/UWS/v1.0rc3");
        jobElement.setAttributeNS("xlmns", "xlink",
                                  "http://www.w3.org/1999/xlink");
        jobElement.setAttributeNS("xlmns", "xsi",
                                  "http://www.w3.org/2001/XMLSchema-instance");

        document.appendChild(jobElement);

        // <uws:jobId>
        final Element jobIdElement =
                document.createElementNS(XML_NAMESPACE_PREFIX,
                                         JobAttribute.JOB_ID.
                                                 getAttributeName());
        jobIdElement.setNodeValue(Long.toString(mockJob.getJobId()));
        jobElement.appendChild(jobIdElement);

        // <uws:phase>
        final Element executionPhaseElement =
                document.createElementNS(XML_NAMESPACE_PREFIX,
                                         JobAttribute.EXECUTION_PHASE.
                                                 getAttributeName());
        executionPhaseElement.setNodeValue(
                mockJob.getExecutionPhase().name().toLowerCase());
        jobElement.appendChild(executionPhaseElement);

        // <uws:executionDuration>
        final Element executionDurationElement =
                document.createElementNS(XML_NAMESPACE_PREFIX,
                                         JobAttribute.EXECUTION_DURATION.
                                                 getAttributeName());
        executionDurationElement.setNodeValue(
                Long.toString(mockJob.getExecutionDuration()));
        jobElement.appendChild(executionDurationElement);

        // <uws:destructionTime>
        final Element destructionTimeElement =
                document.createElementNS(XML_NAMESPACE_PREFIX,
                                         JobAttribute.DESTRUCTION_TIME.
                                                 getAttributeName());
        destructionTimeElement.setNodeValue(
                DateUtil.toString(mockJob.getDestructionTime(),
                                  "yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
        jobElement.appendChild(destructionTimeElement);

        // <uws:quote>
        final Element quoteElement =
                document.createElementNS(XML_NAMESPACE_PREFIX,
                                         JobAttribute.QUOTE.
                                                 getAttributeName());
        quoteElement.setNodeValue(DateUtil.toString(mockJob.getQuote(),
                                  "yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
        jobElement.appendChild(quoteElement);

        // <uws:errorSummary>
        final Element errorSummaryElement =
                document.createElementNS(XML_NAMESPACE_PREFIX,
                                         JobAttribute.ERROR_SUMMARY.
                                                 getAttributeName());
        final Element errorSummaryMessageElement =
                document.createElementNS(XML_NAMESPACE_PREFIX,
                                         JobAttribute.ERROR_SUMMARY.
                                                 getAttributeName());
        errorSummaryMessageElement.setNodeValue("SUMMARY");

        final Element errorSummaryDetailLinkElement =
                document.createElementNS(XML_NAMESPACE_PREFIX,
                                         JobAttribute.ERROR_SUMMARY.
                                                 getAttributeName());
        errorSummaryDetailLinkElement.setAttributeNS("xlink", "href",
                                                     "LINK");
//        errorSummaryDetailLinkElement.setNodeValue(mockJob.getErrorSummary());

        errorSummaryElement.appendChild(errorSummaryMessageElement);
        errorSummaryElement.appendChild(errorSummaryDetailLinkElement);
        jobElement.appendChild(errorSummaryElement);

        // <uws:owner>
        final Element ownerNameElement =
                document.createElementNS(XML_NAMESPACE_PREFIX,
                                         JobAttribute.OWNER_ID.
                                                 getAttributeName());
        ownerNameElement.setAttributeNS("xsi", "nil", "true");
        ownerNameElement.setNodeValue(mockJob.getOwner());
        jobElement.appendChild(ownerNameElement);

        // <uws:runId>
        final Element runIdElement =
                document.createElementNS(XML_NAMESPACE_PREFIX,
                                         JobAttribute.RUN_ID.
                                                 getAttributeName());
        runIdElement.setNodeValue(mockJob.getRunId());
        jobElement.appendChild(runIdElement);

        // <uws:results>
        final Element resultsListElement =
                document.createElementNS(XML_NAMESPACE_PREFIX,
                                         JobAttribute.RESULTS.
                                                 getAttributeName());

        for (final Result result : mockJob.getResultsList())
        {
            final Element resultElement =
                    document.createElementNS(XML_NAMESPACE_PREFIX,
                                             JobAttribute.RESULT.
                                                     getAttributeName());
            resultElement.setAttribute("id", result.getName());
            resultElement.setAttributeNS("xlink", "href",
                                         result.getURL().toExternalForm());
            resultsListElement.appendChild(resultElement);
        }

        jobElement.appendChild(resultsListElement);

        // <uws:parameters>
        final Element parametersListElement =
                document.createElementNS(XML_NAMESPACE_PREFIX,
                                         JobAttribute.PARAMETERS.
                                                 getAttributeName());

        for (final Parameter parameter : mockJob.getParameterList())
        {
            final Element parameterElement =
                    document.createElementNS(XML_NAMESPACE_PREFIX,
                                             JobAttribute.PARAMETER.
                                                     getAttributeName());
            parameterElement.setAttribute("id", parameter.getName());
            parameterElement.setNodeValue(parameter.getValue());
            parametersListElement.appendChild(parameterElement);
        }

        jobElement.appendChild(parametersListElement);

        document.normalizeDocument();
    }
}
