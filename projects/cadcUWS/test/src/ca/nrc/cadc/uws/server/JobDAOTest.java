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

package ca.nrc.cadc.uws.server;

import java.net.URI;
import java.net.URL;
import java.security.Principal;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.sql.DataSource;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import ca.nrc.cadc.auth.AuthenticationUtil;
import ca.nrc.cadc.auth.IdentityManager;
import ca.nrc.cadc.auth.X500IdentityManager;
import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.uws.ErrorSummary;
import ca.nrc.cadc.uws.ErrorType;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobInfo;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.uws.Result;

/**
 * Subclasses must be created to setup the JOB_SCHEMA and dataSource.
 * 
 * @author pdowler
 */
public abstract class JobDAOTest
{
    private static Logger log = Logger.getLogger(JobDAOTest.class);

    static DataSource dataSource;
    static JobDAO.JobSchema JOB_SCHEMA;

    // get rid of millis for comparisons since DBs not too reliable... still could spuriously fail ~1/1000
    static DateFormat compareFormat = DateUtil.getDateFormat("yyyy-MM-dd'T'HH:mm:ss", DateUtil.UTC);
    static DateFormat idFormat = DateUtil.getDateFormat("yyMMddHHmmss", DateUtil.UTC);
    static String RUNID = "123";

    static int id = 0;
    static String now = idFormat.format(new Date());

    static String REQUEST_PATH = "/foo/async";
    static String REMOTE_IP = "12.34.56.78";
    static Long DURATION = new Long(600L);
    static Date DESTRUCTION = new Date(System.currentTimeMillis() + 3600L); // +1 hour
    static Date QUOTE = new Date(System.currentTimeMillis() + 600L); // +10 min
    static StringIDGenerator idGenerator;
    static IdentityManager identManager;

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.uws.server", Level.INFO);
        idGenerator = new RandomStringGenerator(16);
        identManager = new X500IdentityManager();
    }

    private Job createJob()
    {
        id++;
        Job ret = new Job();
        ret.setExecutionPhase(ExecutionPhase.PENDING);
        ret.setExecutionDuration(DURATION);
        ret.setDestructionTime(DESTRUCTION);
        ret.setQuote(QUOTE);
        ret.setRequestPath(REQUEST_PATH);
        ret.setRemoteIP(REMOTE_IP);
        log.debug("created test job: " + ret.getID());
        return ret;
    }

    private JobDAO getDAO()
    {
        return new JobDAO(dataSource, JOB_SCHEMA, identManager, idGenerator);
    }
    
    @Test
    public void testPutGetMinimal()
    {
        Job job, after, persisted;

        try
        {
            job = createJob();

            JobDAO dao = getDAO();
            after = dao.put(job, null);
            compareJobs("returned", job, after);
            Assert.assertNotNull("jobID", after.getID());
            persisted = dao.get(job.getID());
            compareJobs("inserted", job, persisted);

            job.setExecutionPhase(ExecutionPhase.UNKNOWN);
            job.setStartTime(new Date());
            after = dao.put(job, null);
            compareJobs("returned", job, after);
            persisted = dao.get(job.getID());
            compareJobs("updated", job, persisted);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testPutGetError()
    {
        Job job, after, persisted;

        try
        {
            job = createJob();
            job.setExecutionPhase(ExecutionPhase.ERROR);
            ErrorSummary es = new ErrorSummary("summary message", ErrorType.FATAL);
            job.setErrorSummary(es);

            JobDAO dao = getDAO();
            after = dao.put(job, null);
            compareJobs("returned", job, after);
            persisted = dao.get(job.getID());
            compareJobs("persisted", job, persisted);

            // different type
            job = createJob();
            job.setExecutionPhase(ExecutionPhase.ERROR);
            es = new ErrorSummary("summary message", ErrorType.TRANSIENT);
            job.setErrorSummary(es);

            after = dao.put(job, null);
            compareJobs("returned", job, after);
            persisted = dao.get(job.getID());
            compareJobs("persisted", job, persisted);

            // with URL
            job = createJob();
            job.setExecutionPhase(ExecutionPhase.ERROR);
            es = new ErrorSummary("summary message", ErrorType.FATAL,
                    new URL("http://www.ivoa.net/oops"));
            job.setErrorSummary(es);

            after = dao.put(job, null);
            compareJobs("returned", job, after);
            persisted = dao.get(job.getID());
            compareJobs("persisted", job, persisted);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testPutGetOwner()
    {
        Job job, after, persisted;

        try
        {
            Principal principal = new X500Principal("CN=CADC Regtest1 10577,OU=CADC,O=HIA");
            Set<Principal> pset = new HashSet<Principal>();
            pset.add(principal);
            Subject owner = new Subject(true, pset, new HashSet(), new HashSet());

            job = createJob();
            JobDAO dao = getDAO();
            after = dao.put(job, owner);

            job.setOwnerID(principal.getName()); // using X500IdentityManager in test

            compareJobs("returned", job, after);
            persisted = dao.get(job.getID());
            compareJobs("persisted", job, persisted);
            Assert.assertNotNull(persisted.ownerSubject);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testPutGetWithParameters()
    {
        Job job, after, persisted;

        try
        {
            log.debug("parameter list: empty");
            job = createJob();
            job.setParameterList(new ArrayList<Parameter>());
            JobDAO dao = getDAO();
            after = dao.put(job, null);
            compareJobs("returned", job, after);
            persisted = dao.get(job.getID());
            dao.getDetails(persisted);
            compareJobs("persisted", job, persisted);

            log.debug("parameter list: different params");
            job = createJob();
            job.setParameterList(new ArrayList<Parameter>());
            job.getParameterList().add(new Parameter("FOO", "bar"));
            job.getParameterList().add(new Parameter("BAR", "baz"));
            
            after = dao.put(job, null);
            compareJobs("returned", job, after);
            persisted = dao.get(job.getID());
            dao.getDetails(persisted);
            compareJobs("persisted", job, persisted);

            log.debug("parameter list: repeated params");
            job = createJob();
            job.setParameterList(new ArrayList<Parameter>());
            job.getParameterList().add(new Parameter("FOO", "bar"));
            job.getParameterList().add(new Parameter("BAR", "baz"));
            job.getParameterList().add(new Parameter("FOO", "bar"));
            job.getParameterList().add(new Parameter("BAR", "baz"));
            after = dao.put(job, null);
            compareJobs("returned", job, after);
            persisted = dao.get(job.getID());
            dao.getDetails(persisted);
            compareJobs("persisted", job, persisted);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testAddParameters()
    {
        Job job, after, persisted;

        try
        {
            log.debug("parameter list: empty");
            job = createJob();
            job.setParameterList(new ArrayList<Parameter>());
            JobDAO dao = getDAO();
            after = dao.put(job, null);
            compareJobs("returned", job, after);
            persisted = dao.get(job.getID());
            dao.getDetails(persisted);
            compareJobs("persisted", job, persisted);

            log.debug("parameter list: different params");
            List<Parameter> params = new ArrayList<Parameter>();
            params.add(new Parameter("FOO", "bar"));
            params.add(new Parameter("BAR", "baz"));
            
            // add them to the job
            dao.addParameters(job.getID(), params);
            // now add to the job for comparison
            job.setParameterList(params);

            persisted = dao.get(job.getID());
            dao.getDetails(persisted);
            compareJobs("persisted", job, persisted);

            log.debug("parameter list: repeated params");
            // add them to the job again
            dao.addParameters(job.getID(), params);
            // now add to list for comparison
            params.add(new Parameter("FOO", "bar"));
            params.add(new Parameter("BAR", "baz"));

            compareJobs("returned", job, after);
            persisted = dao.get(job.getID());
            dao.getDetails(persisted);
            compareJobs("persisted", job, persisted);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testPutGetWithJobInfo()
    {
        Job job, after, persisted;
        try
        {
            // valid
            job = createJob();
            job.setJobInfo(new JobInfo("<foo/>", "text/xml", Boolean.TRUE));
            JobDAO dao = getDAO();
            after = dao.put(job, null);
            compareJobs("returned", job, after);
            persisted = dao.get(job.getID());
            //dao.getDetails(persisted);
            compareJobs("persisted", job, persisted);

            // valid, no type
            job = createJob();
            job.setJobInfo(new JobInfo("<foo/>", null, Boolean.TRUE));
            after = dao.put(job, null);
            compareJobs("returned", job, after);
            persisted = dao.get(job.getID());
            //dao.getDetails(persisted);
            compareJobs("persisted", job, persisted);

            // invalid
            job = createJob();
            job.setJobInfo(new JobInfo("<foo>", "text/xml", Boolean.FALSE));
            after = dao.put(job, null);
            compareJobs("returned", job, after);
            persisted = dao.get(job.getID());
            //dao.getDetails(persisted);
            compareJobs("persisted", job, persisted);

        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testPutGetWithResults()
    {
        log.debug("testPutGetWithResults");
        Job job, after, persisted;

        try
        {
            log.debug("parameter list: empty");
            job = createJob();
            job.setParameterList(new ArrayList<Parameter>());
            JobDAO dao = getDAO();
            after = dao.put(job, null);
            compareJobs("returned", job, after);
            persisted = dao.get(job.getID());
            dao.getDetails(persisted);
            compareJobs("persisted", job, persisted);

            log.debug("parameter list: different params");
            job = createJob();
            job.setResultsList(new ArrayList<Result>());
            job.getResultsList().add(new Result("r1", new URI("http://www.example.com/path/to/result.txt")));
            job.getResultsList().add(new Result("r2", new URI("http://www.example.com/path/to/other/result.txt")));

            after = dao.put(job, null);
            compareJobs("returned", job, after);
            persisted = dao.get(job.getID());
            dao.getDetails(persisted);
            compareJobs("persisted", job, persisted);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testPutGetWithMixedParametersResults()
    {
        log.debug("testPutGetWithMixedParametersResults");
        Job job, after, persisted;

        try
        {
            log.debug("parameter list: empty");
            job = createJob();
            job.setParameterList(new ArrayList<Parameter>());
            JobDAO dao = getDAO();
            after = dao.put(job, null);
            compareJobs("returned", job, after);
            persisted = dao.get(job.getID());
            dao.getDetails(persisted);
            compareJobs("persisted", job, persisted);

            log.debug("parameter list: different params");
            job = createJob();
            job.setParameterList(new ArrayList<Parameter>());
            job.getParameterList().add(new Parameter("FOO", "bar"));
            job.getParameterList().add(new Parameter("BAR", "baz"));
            job.setResultsList(new ArrayList<Result>());
            job.getResultsList().add(new Result("r1", new URI("http://www.example.com/path/to/result.txt")));
            job.getResultsList().add(new Result("r2", new URI("http://www.example.com/path/to/other/result.txt")));

            after = dao.put(job, null);
            compareJobs("returned", job, after);
            persisted = dao.get(job.getID());
            dao.getDetails(persisted);
            compareJobs("persisted", job, persisted);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testPhaseTransitionAllowed()
    {
        log.debug("testPhaseTransitionAllowed");
        Job job, after, persisted;
        try
        {
            // we will test some plausible transitions but the DAO does not enforce
            // UWS semantics
            JobDAO dao = getDAO();
            
            job = createJob();
            after = dao.put(job, null);
            compareJobs("returned", job, after);
            persisted = dao.get(job.getID());
            compareJobs("persisted", job, persisted);

            Assert.assertTrue("pending", ExecutionPhase.PENDING.equals(persisted.getExecutionPhase()));
            ExecutionPhase ep;
            Date startTime;
            Date endTime;

            // PENDING -> QUEUED
            ep = dao.set(persisted.getID(), ExecutionPhase.PENDING, ExecutionPhase.QUEUED, null);
            Assert.assertTrue("queued", ExecutionPhase.QUEUED.equals(ep));

            // QUEUED -> EXECUTING
            startTime = new Date();
            ep = dao.set(persisted.getID(), ExecutionPhase.QUEUED, ExecutionPhase.EXECUTING, startTime);
            Assert.assertTrue("executing", ExecutionPhase.EXECUTING.equals(ep));
            Job cur = dao.get(persisted.getID());
            Assert.assertEquals("phase", ExecutionPhase.EXECUTING, cur.getExecutionPhase());
            Assert.assertEquals("startTime", compareFormat.format(startTime), compareFormat.format(cur.getStartTime()));
            Assert.assertNull("endTime", cur.getEndTime());

            // EXECUTING -> ABORTED
            endTime = new Date();
            ep = dao.set(persisted.getID(), ExecutionPhase.EXECUTING, ExecutionPhase.ABORTED, endTime);
            Assert.assertTrue("aborted", ExecutionPhase.ABORTED.equals(ep));
            cur = dao.get(persisted.getID());
            Assert.assertEquals("phase", ExecutionPhase.ABORTED, cur.getExecutionPhase());
            compareDates("startTime", startTime, cur.getStartTime());
            compareDates("endTime", endTime, cur.getEndTime());

            // EXECUTING -> COMPLETED
            dao.set(persisted.getID(), ExecutionPhase.EXECUTING); // start state for test
            endTime = new Date();
            ep = dao.set(persisted.getID(), ExecutionPhase.EXECUTING, ExecutionPhase.COMPLETED, endTime);
            Assert.assertTrue("completed", ExecutionPhase.COMPLETED.equals(ep));
            cur = dao.get(persisted.getID());
            Assert.assertEquals("phase", ExecutionPhase.COMPLETED, cur.getExecutionPhase());
            compareDates("startTime", startTime, cur.getStartTime());
            compareDates("endTime", endTime, cur.getEndTime());

            // EXECUTING -> ERROR
            dao.set(persisted.getID(), ExecutionPhase.EXECUTING); // start state for test
            endTime = new Date();
            ep = dao.set(persisted.getID(), ExecutionPhase.EXECUTING, ExecutionPhase.ERROR, endTime);
            Assert.assertTrue("error", ExecutionPhase.ERROR.equals(ep));
            cur = dao.get(persisted.getID());
            Assert.assertEquals("phase", ExecutionPhase.ERROR, cur.getExecutionPhase());
            compareDates("startTime", startTime, cur.getStartTime());
            compareDates("endTime", endTime, cur.getEndTime());

            // EXECUTING -> ABORTED
            dao.set(persisted.getID(), ExecutionPhase.EXECUTING);
            endTime = new Date();
            ep = dao.set(persisted.getID(), ExecutionPhase.EXECUTING, ExecutionPhase.ABORTED, endTime);
            Assert.assertTrue("aborted", ExecutionPhase.ABORTED.equals(ep));
            cur = dao.get(persisted.getID());
            Assert.assertEquals("phase", ExecutionPhase.ABORTED, cur.getExecutionPhase());
            compareDates("startTime", startTime, cur.getStartTime());
            compareDates("endTime", endTime, cur.getEndTime());
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testPhaseTransitionFail()
    {
        log.debug("testPhaseTransitionFail");
        Job job, after, persisted;
        try
        {
            // simply test some phase transition requests that should fail due to
            // not being in the right starting phase
            job = createJob();
            JobDAO dao = getDAO();
            after = dao.put(job, null);
            compareJobs("returned", job, after);
            persisted = dao.get(job.getID());
            dao.getDetails(persisted);
            compareJobs("persisted", job, persisted);

            Assert.assertTrue("pending", ExecutionPhase.PENDING.equals(persisted.getExecutionPhase()));
            ExecutionPhase ep;

            // PENDING -> QUEUED when QUEUED
            dao.set(job.getID(), ExecutionPhase.QUEUED);
            ep = dao.set(job.getID(), ExecutionPhase.PENDING, ExecutionPhase.QUEUED, null);
            Assert.assertNull("PENDING -> QUEUED when QUEUED", ep);

            // QUEUED -> EXECUTING when EXECUTING
            dao.set(job.getID(), ExecutionPhase.EXECUTING);
            ep = dao.set(job.getID(), ExecutionPhase.QUEUED, ExecutionPhase.EXECUTING, null);
            Assert.assertNull("QUEUED -> EXECUTING when EXECUTING", ep);

            // EXECUTING -> COMPLETED when ABORTED
            dao.set(job.getID(), ExecutionPhase.ABORTED);
            ep = dao.set(job.getID(), ExecutionPhase.EXECUTING, ExecutionPhase.COMPLETED, null);
            Assert.assertNull("EXECUTING -> COMPLETED when ABORTED", ep);

            // EXECUTING -> COMPLETED when ERROR
            dao.set(job.getID(), ExecutionPhase.ERROR);
            ep = dao.set(job.getID(), ExecutionPhase.EXECUTING, ExecutionPhase.COMPLETED, null);
            Assert.assertNull("EXECUTING -> COMPLETED when ERROR", ep);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testPhaseTransitionResults()
    {
        log.debug("testPhaseTransitionResults");
        Job job, after, persisted;
        try
        {
            job = createJob();
            JobDAO dao = getDAO();
            after = dao.put(job, null);
            compareJobs("returned", job, after);
            persisted = dao.get(job.getID());
            dao.getDetails(persisted);
            compareJobs("persisted", job, persisted);

            Assert.assertTrue("pending", ExecutionPhase.PENDING.equals(persisted.getExecutionPhase()));
            ExecutionPhase ep;
            Date endTime;

            // EXECUTING -> COMPLETED + results
            dao.set(job.getID(), ExecutionPhase.EXECUTING);
            List<Result> results = new ArrayList<Result>();
            endTime = new Date();
            ep = dao.set(job.getID(), ExecutionPhase.EXECUTING, ExecutionPhase.COMPLETED, results, endTime);
            Assert.assertTrue("completed", ExecutionPhase.COMPLETED.equals(ep));
            persisted = dao.get(job.getID());
            dao.getDetails(persisted);

            job.setResultsList(results);
            job.setExecutionPhase(ExecutionPhase.COMPLETED);
            job.setEndTime(endTime);
            compareJobs("completed, 0 results", job, persisted);

            // again, but with some results this time
            results.add(new Result("r1", new URI("http://www.example.com/path/to/something")));
            results.add(new Result("r2", new URI("http://www.example.com/path/to/other/result.txt")));

            dao.set(job.getID(), ExecutionPhase.EXECUTING);
            endTime = new Date();
            ep = dao.set(job.getID(), ExecutionPhase.EXECUTING, ExecutionPhase.COMPLETED, results, endTime);
            Assert.assertTrue("completed", ExecutionPhase.COMPLETED.equals(ep));
            persisted = dao.get(job.getID());
            dao.getDetails(persisted);

            job.setResultsList(results);
            job.setExecutionPhase(ExecutionPhase.COMPLETED);
            job.setEndTime(endTime);
            compareJobs("completed, 2 results", job, persisted);
            
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testPhaseTransitionError()
    {
        log.debug("testPhaseTransitionError");
        Job job, after, persisted;
        try
        {
            job = createJob();
            JobDAO dao = getDAO();
            after = dao.put(job, null);
            compareJobs("returned", job, after);
            persisted = dao.get(job.getID());
            dao.getDetails(persisted);
            compareJobs("persisted", job, persisted);

            Assert.assertTrue("pending", ExecutionPhase.PENDING.equals(persisted.getExecutionPhase()));
            ExecutionPhase ep;

            // EXECUTING -> ERROR + error
            dao.set(job.getID(), ExecutionPhase.EXECUTING);
            ErrorSummary error = new ErrorSummary("oops", ErrorType.FATAL, new URL("http://www.ivoa.net/oops"));
            Date endTime = new Date();
            ep = dao.set(job.getID(), ExecutionPhase.EXECUTING, ExecutionPhase.ERROR, error, endTime);
            Assert.assertTrue("error", ExecutionPhase.ERROR.equals(ep));
            persisted = dao.get(job.getID());
            dao.getDetails(persisted);

            job.setErrorSummary(error);
            job.setExecutionPhase(ExecutionPhase.ERROR); 
            job.setEndTime(endTime);
            compareJobs("error, w/ summary", job, persisted);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testDelete()
    {
        Job job, after, persisted;

        try
        {
            job = createJob();

            JobDAO dao = getDAO();
            after = dao.put(job, null);
            String jobID = after.getID();

            compareJobs("returned", job, after);
            Assert.assertNotNull("jobID", after.getID());
            persisted = dao.get(jobID);
            compareJobs("inserted", job, persisted);

            dao.delete(jobID);
            try
            {
                persisted = dao.get(jobID);
            }
            catch(JobNotFoundException expected)
            {
                log.debug("caught expected exception: " + expected);
                return;
            }
            Assert.fail("failed to get expected exception: JobNotFoundException");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    private void compareDates(String str, Date exp, Date act)
    {
        if (exp == null)
            Assert.assertNull(act);
        else
        {
            Assert.assertNotNull(str, act);
            Assert.assertEquals(str, compareFormat.format(exp), compareFormat.format(act));
        }
    }
    private void compareJobs(String str, Job exp, Job act)
    {
        Assert.assertEquals(str+" phase", exp.getExecutionPhase(), act.getExecutionPhase());
        Assert.assertEquals(str+" duration", exp.getExecutionDuration(), act.getExecutionDuration());

        compareDates(str+" destruction", exp.getDestructionTime(), act.getDestructionTime());
        compareDates(str+" quote", exp.getQuote(), act.getQuote());
        compareDates(str+" startTime", exp.getStartTime(), act.getStartTime());
        compareDates(str+" endTime", exp.getEndTime(), act.getEndTime());

        Assert.assertEquals(str+" request path", exp.getRequestPath(), act.getRequestPath());
        Assert.assertEquals(str+" remote ip", exp.getRemoteIP(), act.getRemoteIP());
        Assert.assertEquals(str+" runid", exp.getRunID(), act.getRunID());
        
        if (exp.getErrorSummary() == null)
            Assert.assertNull(act.getErrorSummary());
        else
        {
            Assert.assertNotNull("error", act.getErrorSummary());
            Assert.assertEquals("error message", exp.getErrorSummary().getSummaryMessage(), act.getErrorSummary().getSummaryMessage());
            Assert.assertEquals("error type", exp.getErrorSummary().getErrorType(), act.getErrorSummary().getErrorType());
            Assert.assertEquals("error url", exp.getErrorSummary().getDocumentURL(), act.getErrorSummary().getDocumentURL());
        }

        if (exp.getOwnerID() == null)
            Assert.assertNull(act.getOwnerID());
        else
        {
            Assert.assertNotNull(act.getOwnerID());
            X500Principal ep = new X500Principal(exp.getOwnerID());
            X500Principal ap = new X500Principal(act.getOwnerID());
            Assert.assertTrue("principals", AuthenticationUtil.equals(ep, ap));
        }

        if ( exp.getParameterList() == null)
            Assert.assertTrue("param list", (act.getParameterList() == null || act.getParameterList().size() == 0));
        else
            Assert.assertTrue("number of params", (exp.getParameterList().size() == act.getParameterList().size()));

        if ( exp.getResultsList() == null)
            Assert.assertTrue("result list", (act.getResultsList() == null || act.getResultsList().size() == 0));
        else
            Assert.assertTrue("number of results", (exp.getResultsList().size() == act.getResultsList().size()));

        if (exp.getJobInfo() == null)
            Assert.assertNull(str+" jobinfo", act.getJobInfo());
        else
        {
            Assert.assertEquals(str+" runid", exp.getJobInfo().getContent(), act.getJobInfo().getContent());
            Assert.assertEquals(str+" runid", exp.getJobInfo().getContentType(), act.getJobInfo().getContentType());
            Assert.assertEquals(str+" runid", exp.getJobInfo().getValid(), act.getJobInfo().getValid());
        }
    }
}
