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
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import junit.framework.Assert;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.util.Log4jInit;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.Principal;
import java.text.DateFormat;
import java.util.HashSet;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import org.apache.log4j.Level;

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

    @AfterClass
    public static void tearDownAfterClass() throws Exception {}

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp()
        throws Exception
    {
        this.dateFormat = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
        this.baseDate = dateFormat.parse(TEST_DATE);
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {}


    Job createPendingJob()
        throws Exception
    {
        Job ret = new Job();
        ret.setExecutionPhase(ExecutionPhase.PENDING);
        ret.setID(JOB_ID);
        ret.setRunID(RUN_ID);
        ret.setQuote(new Date(baseDate.getTime() + 10000L));
        ret.setExecutionDuration(123L);
        ret.setDestructionTime(new Date(baseDate.getTime() + 300000L));
        ret.setOwner(null);
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
        List<Result> results = new ArrayList<Result>();
        results.add(new Result("rsName1", new URL("http://www.ivoa.net/url1"), true));
        results.add(new Result("rsName2", new URL("http://www.ivoa.net/url2"), false));
        j.setResultsList(results);
        j.setStartTime(new Date(baseDate.getTime() + 300L));
        j.setEndTime(new Date(baseDate.getTime() + 500L));
        j.setExecutionPhase(ExecutionPhase.COMPLETED);
    }

    void fail(Job j)
        throws Exception
    {
        ErrorSummary err = new ErrorSummary("oops", ErrorType.FATAL, true);
        err.setDocumentURL(new URL("http://www.ivoa.net/oops"));
        j.setErrorSummary(err);
        j.setStartTime(new Date(baseDate.getTime() + 300L));
        j.setEndTime(new Date(baseDate.getTime() + 400L));
        j.setExecutionPhase(ExecutionPhase.ERROR);
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
        Assert.assertEquals(exp.getID(), act.getID());
        Assert.assertEquals(exp.getRunID(), act.getRunID());
        Assert.assertEquals(exp.getExecutionPhase(), act.getExecutionPhase());
        Assert.assertEquals(exp.getExecutionDuration(), act.getExecutionDuration());
        Assert.assertEquals(exp.getQuote(), act.getQuote());
        Assert.assertEquals(exp.getDestructionTime(), act.getDestructionTime());
        Assert.assertEquals(exp.getStartTime(), act.getStartTime());
        Assert.assertEquals(exp.getEndTime(), act.getEndTime());

        assertEqualSubject(exp.getOwner(), act.getOwner());

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
        Assert.assertEquals("jobInfo content", exp.getContent(), act.getContent());
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
//        Assert.assertEquals(exp.getDocumentURL(), act.getDocumentURL());
    }

    private void assertEqualResults(List<Result> exp, List<Result> act)
    {
        if (exp == null)
        {
            Assert.assertNull("results", act);
            return;
        }
        Assert.assertEquals(exp.size(), act.size());

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
            log.debug("unexpected exception", unexpected);
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
            log.debug("unexpected exception", unexpected);
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
            log.debug("unexpected exception", unexpected);
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
            log.debug("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testWithOwner()
    {
        log.debug("testWithOwner");
        try
        {
            Set<X500Principal> principals = new HashSet<X500Principal>();
            Set<Object> pub = new HashSet();
            Set<Object> priv = new HashSet();
            principals.add(new X500Principal("CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US"));
            Subject s = new Subject(true, principals, pub, priv);

            Job job = createPendingJob();
            job.setOwner(s);
            test(job);
        }
        catch(Exception unexpected)
        {
            log.debug("unexpected exception", unexpected);
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
            log.debug("unexpected exception", unexpected);
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
            String xml = "<foo />";
            String type = "text/xml";
            JobInfo info = new JobInfo(xml, type, true);
            job.setJobInfo(info);
            test(job);
        }
        catch(Exception unexpected)
        {
            log.debug("unexpected exception", unexpected);
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
            String xml = "<foo>";
            String type = "text/xml";
            JobInfo info = new JobInfo(xml, type, Boolean.FALSE);
            job.setJobInfo(info);
            test(job);
        }
        catch(Exception unexpected)
        {
            log.debug("unexpected exception", unexpected);
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
            job.setID(JOB_ID);
            job.setRunID(RUN_ID);
            job.setQuote(new Date(baseDate.getTime() + 10000L));
            job.setExecutionDuration(123L);
            job.setDestructionTime(new Date(baseDate.getTime() + 300000L));
            job.addParameter(new Parameter("empty parameter", ""));
            job.setOwner(null);
            test(job);
        }
        catch(Exception unexpected)
        {
            log.debug("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
}
