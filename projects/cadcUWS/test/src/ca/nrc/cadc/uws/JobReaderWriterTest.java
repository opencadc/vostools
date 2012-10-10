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

import ca.nrc.cadc.auth.AuthenticationUtil;
import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.uws.server.JobPersistenceUtil;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.security.Principal;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.security.auth.Subject;
import junit.framework.Assert;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author zhangsa
 *
 */
public class JobReaderWriterTest
{
    static Logger log = Logger.getLogger(JobReaderWriterTest.class);

    private String JOB_ID = "someJobID";
    private String RUN_ID = "someRunID";
    private String TEST_DATE = "2001-01-01T12:34:56.000";

    private DateFormat dateFormat;
    private Date baseDate;

    @BeforeClass
    public static void setUpBeforeClass() 
        throws Exception
    {
        Log4jInit.setLevel("ca.nrc.cadc", Level.INFO);
    }

    @Before
    public void setUp()
        throws Exception
    {
        this.dateFormat = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
        this.baseDate = dateFormat.parse(TEST_DATE);
    }

    Job createPendingJob()
        throws Exception
    {
        Job ret = new Job();
        ret.setExecutionPhase(ExecutionPhase.PENDING);
        JobPersistenceUtil.assignID(ret, JOB_ID);
        ret.setRunID(RUN_ID);
        ret.setQuote(new Date(baseDate.getTime() + 10000L));
        ret.setExecutionDuration(123L);
        ret.setDestructionTime(new Date(baseDate.getTime() + 300000L));
        return ret;
    }

    void setup(Job j)
    {
        final List<Parameter> parameters = new ArrayList<Parameter>();
        parameters.add(new Parameter("parName1", "parV1"));
        parameters.add(new Parameter("parName2", "parV2"));
        j.setParameterList(parameters);
    }

    void queue(Job j)
    {
        j.setExecutionPhase(ExecutionPhase.QUEUED);
    }

    void execute(Job j)
    {
        j.setStartTime(new Date(baseDate.getTime() + 300L));
        j.setExecutionPhase(ExecutionPhase.EXECUTING);
    }

    void complete(Job j)
        throws Exception
    {
        j.setExecutionPhase(ExecutionPhase.COMPLETED);
        List<Result> results = new ArrayList<Result>();
        results.add(new Result("rsName1", new URI("http://www.ivoa.net/url1"), true));
        results.add(new Result("rsName2", new URI("http://www.ivoa.net/url2"), false));
        j.setResultsList(results);
        j.setStartTime(new Date(baseDate.getTime() + 300L));
        j.setEndTime(new Date(baseDate.getTime() + 500L));
        
    }

    void fail(Job j)
        throws Exception
    {
        j.setExecutionPhase(ExecutionPhase.ERROR);
        j.setErrorSummary(new ErrorSummary("oops", ErrorType.FATAL, new URL("http://www.ivoa.net/oops")));
        j.setStartTime(new Date(baseDate.getTime() + 300L));
        j.setEndTime(new Date(baseDate.getTime() + 400L));
        
    }

    private String toXML(Job j)
        throws IOException
    {
        JobWriter w = new JobWriter();
        StringWriter sw = new StringWriter();
        w.write(j, sw);
        sw.close();
        String ret = sw.toString();
        log.debug("\n"+ret);
        return ret;
    }

    private void assertEquals(Job exp, Job act)
    {
        Assert.assertEquals("jobID", exp.getID(), act.getID());
        Assert.assertEquals("runID", exp.getRunID(), act.getRunID());
        Assert.assertEquals("phase", exp.getExecutionPhase(), act.getExecutionPhase());
        Assert.assertEquals("duration", exp.getExecutionDuration(), act.getExecutionDuration());
        Assert.assertEquals("quote", exp.getQuote(), act.getQuote());
        Assert.assertEquals("destruction", exp.getDestructionTime(), act.getDestructionTime());
        Assert.assertEquals("start", exp.getStartTime(), act.getStartTime());
        Assert.assertEquals("end", exp.getEndTime(), act.getEndTime());

        Assert.assertEquals("ownerID", exp.getOwnerID(), act.getOwnerID());

        assertEqualParameters(exp.getParameterList(), act.getParameterList());
        assertEqualResults(exp.getResultsList(), act.getResultsList());

        assertEqualError(exp.getErrorSummary(), act.getErrorSummary());

        assertEqualJobInfo(exp.getJobInfo(), act.getJobInfo());
    }

    private void assertEqualJobInfo(JobInfo exp, JobInfo act)
    {
        log.debug("expect: " + exp);
        log.debug("actual: " + act);
        if (exp == null || !exp.getValid())
        {
            Assert.assertNull("expect null jobInfo", act);
            return;
        }

        Assert.assertNotNull("expect non-null jobInfo", act);
        // TODO: compare content for equivalence (XML)
        //Assert.assertEquals("jobInfo content", exp.getContent(), act.getContent());
    }

    private void assertEqualSubject(Subject exp, Subject act)
    {
        if (exp == null)
        {
            Assert.assertNull("owner", act);
            return;
        }

        if (exp == null)
        {
            if (act != null && act.getPrincipals().size() > 0)
                throw new AssertionError("expected Subject with no Principals, found "
                        + act.getPrincipals().size());
            return;
        }
        if (act == null)
        {
            if (exp != null && exp.getPrincipals().size() > 0)
                throw new AssertionError("expected Subject with " + exp.getPrincipals().size()
                        + " Principals, found null Subject");
            return;
        }

        Assert.assertEquals(exp.getPrincipals().size(), act.getPrincipals().size());
        for (Principal p : exp.getPrincipals())
        {
            Assert.assertTrue("found principal", checkContains(p, act.getPrincipals()));
        }
    }

    private boolean checkContains(Principal p, Set<Principal> set)
    {
        for (Principal op : set)
            if ( AuthenticationUtil.equals(p, op) )
                return true;
        return false;
    }

    private void assertEqualError(ErrorSummary exp, ErrorSummary act)
    {
        if (exp == null)
        {
            Assert.assertNull("error", act);
            return;
        }

        Assert.assertEquals(exp.getSummaryMessage(), act.getSummaryMessage());
        Assert.assertEquals(exp.getHasDetail(), act.getHasDetail());
        // although ErrorSummary has a getDocumentURL, it is not transferred via
        // the XML serialisation
    }

    private void assertEqualParameters(List<Parameter> exp, List<Parameter> act)
    {
        if (exp == null)
        {
            Assert.assertNull("parameters", act);
            return;
        }
        Assert.assertEquals("number of parameters", exp.size(), act.size());

    }
    private void assertEqualResults(List<Result> exp, List<Result> act)
    {
        if (exp == null)
        {
            Assert.assertNull("results", act);
            return;
        }
        Assert.assertEquals("number of results", exp.size(), act.size());

    }

    public void test(Job job)
        throws Exception
    {
        String xml = toXML(job);
        JobReader r = new JobReader();
        Job job2 = r.read(new StringReader(xml));
        assertEquals(job, job2);
    }

    @Test
    public void testPending()
    {
        log.debug("testPending");
        try
        {
            Job job = createPendingJob();
            test(job);

        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testQueued()
    {
        log.debug("testQueued");
        try
        {
            Job job = createPendingJob();
            queue(job);
            test(job);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testExecuting()
    {
        log.debug("testExecuting");
        try
        {
            Job job = createPendingJob();
            execute(job);
            test(job);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testCompleted()
    {
        log.debug("testCompleted");
        try
        {
            Job job = createPendingJob();
            complete(job);
            test(job);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testWithOwnerID()
    {
        log.debug("testWithOwner");
        try
        {
            Job job = createPendingJob();
            job.setOwnerID("CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US");
            test(job);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testFailed()
    {
        log.debug("testFailed");
        try
        {
            Job job = createPendingJob();
            fail(job);
            test(job);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testWithValidJobInfo()
    {
        log.debug("testWithValidJobInfo");
        try
        {
            Job job = createPendingJob();
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo />";
            String type = "text/xml";
            JobInfo info = new JobInfo(xml, type, true);
            job.setJobInfo(info);
            test(job);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testWithInvalidJobInfo()
    {
        log.debug("testWithInvalidJobInfo");
        try
        {
            Job job = createPendingJob();
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><foo>";
            String type = "text/xml";
            JobInfo info = new JobInfo(xml, type, Boolean.FALSE);
            job.setJobInfo(info);
            test(job);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testWithEmptyJobParameter()
    {
        log.debug("testWithEmptyJobParameter");
        try
        {
            Job job = new Job();
            job.setExecutionPhase(ExecutionPhase.PENDING);
            JobPersistenceUtil.assignID(job, JOB_ID);
            job.setRunID(RUN_ID);
            job.setQuote(new Date(baseDate.getTime() + 10000L));
            job.setExecutionDuration(123L);
            job.setDestructionTime(new Date(baseDate.getTime() + 300000L));
            job.setParameterList(new ArrayList<Parameter>());
            job.getParameterList().add(new Parameter("empty parameter", ""));
            job.setOwnerID(null);
            test(job);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testJobReaderWithInvalidJobInfoDocument()
    {
        log.debug("testJobReaderWithInvalidJobInfoDocument");
        try
        {
            // Create a Job.
            Job job = createPendingJob();

            // Create a JobInfo with an invalid document.
            StringBuilder content = new StringBuilder();
            content.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            content.append("<foons:foo xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
            content.append("           xmlns:foons=\"http://localhost/foo.xsd\">");
            content.append("</foons:foo>");
            JobInfo jobInfo = new JobInfo(content.toString(), "text/xml", true);
            job.setJobInfo(jobInfo);

            // Write Job to XML.
            String xml = toXML(job);

            // Create a vaidating JobReader, without a schema for the JobInfo content.
            Map<String, String> map = new HashMap<String, String>();
            map.put("http://localhost/foo.xsd", "file:test/src/resources/bar.xsd");
            JobReader jobReader = new JobReader(map);
            try
            {
                jobReader.read(new StringReader(xml));
                Assert.fail("JobReader should've thrown an exception for unknown schema in JobInfo");
            }
            catch (Exception ignore) { }
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testJobReaderWithValidJobInfoDocument()
    {
        log.debug("testJobReaderWithValidJobInfoDocument");
        try
        {
            // Create a Job.
            Job job = createPendingJob();

            // Create a JobInfo with an invalid document.
            StringBuilder content = new StringBuilder();
            content.append("<?xml version=\"1.0\" ?>");
            content.append("<foons:foo xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
            content.append("           xmlns:foons=\"http://localhost/foo.xsd\">");
            content.append("</foons:foo>");
            JobInfo jobInfo = new JobInfo(content.toString(), "text/xml", true);
            job.setJobInfo(jobInfo);

            // Write Job to XML.
            String xml = toXML(job);

            // Create a vaidating JobReader with a schema for the JobInfo content.
            Map<String, String> map = new HashMap<String, String>();
            map.put("http://localhost/foo.xsd", "file:test/src/resources/foo.xsd");
            JobReader jobReader = new JobReader(map);
            try
            {
                jobReader.read(new StringReader(xml));
            }
            catch (Exception e)
            {
                Assert.fail("JobReader should not throw exception " + e.getMessage());
            }
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
}
