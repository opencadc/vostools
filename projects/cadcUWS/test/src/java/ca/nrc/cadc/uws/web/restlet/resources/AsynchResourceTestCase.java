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

import junit.framework.TestCase;
import static org.easymock.EasyMock.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.Diff;
import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobAttribute;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.uws.Result;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;



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
        expect(mockJob.getJobId()).andReturn("88l").anyTimes();
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
                                  "http://www.ivoa.net/xml/UWS/v1.0 UWS.xsd");
        jobElement.setAttributeNS("xlmns", "xml",
                                  "http://www.w3.org/XML/1998/namespace");
        jobElement.setAttributeNS("xlmns", "uws",
                                  "http://www.ivoa.net/xml/UWS/v1.0");
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
        jobIdElement.setNodeValue(mockJob.getJobId());
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
