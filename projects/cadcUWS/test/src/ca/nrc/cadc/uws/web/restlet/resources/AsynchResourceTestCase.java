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

import ca.nrc.cadc.uws.ErrorSummary;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobAttribute;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.uws.Result;
import ca.nrc.cadc.uws.UWS;

import static junit.framework.TestCase.*;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.net.URL;
import java.security.AccessControlException;

import org.jdom.Document;
import org.jdom.Element;


/**
 * TestCase for the Asynchronous Resource.
 */
public class AsynchResourceTestCase
{
    protected final String XML_NAMESPACE_PREFIX = "uws";
    protected final String XML_NAMESPACE_URI = "http://www.ivoa.net/xml/UWS/v1.0";
    protected final String HOST_PART = "http://myhost/mycontext";

    protected Date destructionTime;
    protected AsynchResource asynchResource;
    protected Document documentToBuild;
    protected Document expectedDocument;
    protected Job job;
    protected List<Result> results;
    protected List<Parameter> parameters;


    /**
     * Sets up the fixture, for example, open a network connection.
     * This method is called before a test is executed.
     *
     * @throws Exception for anything that goes wrong.
     */
    @Before
    public void setUp() throws Exception
    {
        final Calendar cal = Calendar.getInstance();
        cal.set(1997, Calendar.NOVEMBER, 25, 3, 21, 0);
        cal.set(Calendar.MILLISECOND, 0);

        results = new ArrayList<Result>();
        parameters = new ArrayList<Parameter>();

        final Calendar quoteCal = Calendar.getInstance();
        quoteCal.set(1977, Calendar.NOVEMBER, 25, 8, 30, 0);
        quoteCal.set(Calendar.MILLISECOND, 0);

        final List<Result> results = new ArrayList<Result>();
        final List<Parameter> parameters = new ArrayList<Parameter>();
        final ErrorSummary errorSummary =
                new ErrorSummary("SUMMARY", new URL("http://www.nrc.ca"));
        
        job = new Job("88l", ExecutionPhase.QUEUED, 88l, cal.getTime(),
                        quoteCal.getTime(), cal.getTime(), cal.getTime(), 
                        errorSummary, null, "RUN_ID", results, parameters, null);
        job.setRequestPath("/async");
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
                return new ArrayList<Job>(Arrays.asList(job));
            }

            /**
             * Build the host portion of any outgoing URL that will be intended for a
             * local call.  This is useful when building XML and wanting to call upon
             * a local Resource to build a portion of it.
             * <p/>
             * An example would look like: http://myhost/context
             *
             * @return String Host part of a URI.
             */
            @Override
            protected String getHostPart()
            {
                return HOST_PART;
            }
        };

        documentToBuild = new Document(); //XMLUnit.newControlParser().newDocument();
        expectedDocument = new Document(); //XMLUnit.newControlParser().newDocument();
        buildTestXML(expectedDocument);        
    }

    /**
     * Tears down the fixture, for example, close a network connection.
     * This method is called after a test is executed.
     */
    @After
    public void tearDown()
    {
        asynchResource = null;
        documentToBuild = null;
    }


    @Test
    public void buildXML() throws Exception
    {
        try
        {
            asynchResource.buildXML(documentToBuild);
            //assertTrue("Good document.", documentToBuild.equals(expectedDocument));
            assertFalse("expected AccessControlException", true);
        }
        catch(AccessControlException expected)
        {

        }
    }

    protected void buildTestXML(final Document document)
    {
        Element jobsElement = new Element(JobAttribute.JOBS.getAttributeName(), UWS.NS);
        jobsElement.addNamespaceDeclaration(UWS.NS);
        jobsElement.addNamespaceDeclaration(UWS.XLINK_NS);
        //jobsElement.setAttribute("schemaLocation", "http://www.ivoa.net/xml/UWS/v1.0 UWS.xsd", UWS.XSI_NS);

        Element jobRefElement = new Element(JobAttribute.JOB_REF.getAttributeName(), UWS.NS);
        jobRefElement.setAttribute("id", job.getID());
        //jobRefElement.setAttribute("xlink:href", HOST_PART + "/async/" + job.getID());
        jobRefElement.setAttribute("href", HOST_PART + "/async/" + job.getID(), UWS.XLINK_NS);

        Element jobRefPhaseElement = new Element(JobAttribute.EXECUTION_PHASE.getAttributeName(), UWS.NS);
        jobRefPhaseElement.addContent(job.getExecutionPhase().name());

        jobRefElement.addContent(jobRefPhaseElement);
        jobsElement.addContent(jobRefElement);
        
        document.addContent(jobsElement);
    }
}
