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

package ca.nrc.cadc.uws;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import junit.framework.Assert;

import org.apache.log4j.Logger;
import org.jdom.JDOMException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.uws.util.XmlUtil;

/**
 * @author zhangsa
 *
 */
public class JobReaderTest
{
    static Logger log = Logger.getLogger(JobReaderTest.class);

    private String JOB_ID = "AT88MPH";
    private Job _testJob;
    private String _xmlString;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        Log4jInit.setLevel("ca.nrc.cadc", org.apache.log4j.Level.DEBUG);
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {}

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        final Calendar cal = Calendar.getInstance();
        cal.set(1997, Calendar.NOVEMBER, 25, 3, 21, 0);
        cal.set(Calendar.MILLISECOND, 0);

        final Calendar quoteCal = Calendar.getInstance();
        quoteCal.set(1977, Calendar.NOVEMBER, 25, 8, 30, 0);
        quoteCal.set(Calendar.MILLISECOND, 0);

        final List<Result> results = new ArrayList<Result>();
        results.add(new Result("rsName1", new URL("http://www.ivoa.net/url1"), true));
        results.add(new Result("rsName2", new URL("http://www.ivoa.net/url2"), false));
        final List<Parameter> parameters = new ArrayList<Parameter>();
        parameters.add(new Parameter("parName1", "parV1"));
        parameters.add(new Parameter("parName2", "parV2"));

        /**
         * Constructor.
         *
         * @param jobID                 The unique Job ID.
         * @param executionPhase        The Execution Phase.
         * @param executionDuration     The Duration in clock seconds.
         * @param destructionTime       The date and time of destruction.
         * @param quote                 The quoted date of completion.
         * @param startTime             The start date of execution.
         * @param endTime               The end date of execution.
         * @param errorSummary          The error, if any.
         * @param owner                 The Owner of this Job.
         * @param runId                 The specific running ID.
         * @param resultsList           The List of Results.
         * @param parameterList         The List of Parameters.
         * @param requestPath           The http request path.
         */
        _testJob = new Job(JOB_ID, ExecutionPhase.PENDING, 88L, new Date(0L), quoteCal.getTime(), cal.getTime(), cal.getTime(),
                null, null, "RUN_ID", results, parameters, null);

        JobWriter jobWriter = new JobWriter(_testJob);
        _xmlString = jobWriter.toString();
        XmlUtil.validateXml(_xmlString, UWS.XSD_KEY, UWS.XSD_FILE_NAME);
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {}

    @Test
    public void testReader() throws IOException, JDOMException {
        log.debug(_xmlString);
        JobReader jobReader = new JobReader();
        Job job = jobReader.readFrom(_xmlString);

        Assert.assertEquals(job.getID(), JOB_ID);
        Assert.assertEquals(job.getExecutionPhase(), ExecutionPhase.PENDING);
        Assert.assertEquals(job.getExecutionDuration(), 88L);
        Assert.assertEquals(job.getDestructionTime(), new Date(0L));
        Assert.assertEquals(job.getParameterList().size(), 2);
        Assert.assertEquals(job.getResultsList().size(), 2);
        Assert.assertEquals(job.getRunID(), "RUN_ID");
    }
}
