/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2011.                            (c) 2011.
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
*  $Revision: 5 $
*
************************************************************************
*/

package ca.nrc.cadc.uws.server;

import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.uws.ErrorSummary;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.Result;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author pdowler
 */
public class ThreadExecutorTest 
{
    private static Logger log = Logger.getLogger(ThreadExecutorTest.class);

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.uws.server", Level.INFO);
    }

    @Test
    public void testInvalidArgs()
    {
        try
        {
            try
            {
                ThreadExecutor exec = new ThreadExecutor(null, null);
                Assert.fail("expected IllegalArgumentException: null JobUpdater");
            }
            catch(IllegalArgumentException expected)
            {
                log.debug("caught expected: " + expected);
            }
            
            try
            {
                ThreadExecutor exec = new ThreadExecutor(new TestJobUpdater(), null);
                Assert.fail("expected IllegalArgumentException: null JobRunner.class");
            }
            catch(IllegalArgumentException expected)
            {
                log.debug("caught expected: " + expected);
            }
            
            try
            {
                ThreadExecutor exec = new ThreadExecutor(null, TestJobRunner.class);
                Assert.fail("expected IllegalArgumentException: null JobUpdater");
            }
            catch(IllegalArgumentException expected)
            {
                log.debug("caught expected: " + expected);
            }

            try
            {
                ThreadExecutor exec = new ThreadExecutor(new TestJobUpdater(), TestJobRunner.class, null);
                Assert.fail("expected IllegalArgumentException: null threadBaseName");
            }
            catch(IllegalArgumentException expected)
            {
                log.debug("caught expected: " + expected);
            }

            // success
            ThreadExecutor exec = new ThreadExecutor(new TestJobUpdater(), TestJobRunner.class);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testExec()
    {
        try
        {
            TestJobUpdater ju = new TestJobUpdater();
            ThreadExecutor exec = new ThreadExecutor(ju, TestJobRunner.class);
            Job job = new TestJob(100L);
            ju.jobs.put(job.getID(), job);
            
            exec.execute(job);
            Thread.sleep(150L);
            ExecutionPhase actual = ju.getPhase(job.getID());
            Assert.assertEquals("phase", ExecutionPhase.COMPLETED, actual);
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testAbort()
    {
        try
        {
            TestJobUpdater ju = new TestJobUpdater();
            ThreadExecutor exec = new ThreadExecutor(ju, TestJobRunner.class);
            Job job = new TestJob(100L);
            ju.jobs.put(job.getID(), job);
            
            long t1 = System.currentTimeMillis();
            exec.execute(job);
            Thread.sleep(20L);
            exec.abort(job);
            long dt = System.currentTimeMillis() - t1;
            log.debug("aborted job: " + dt + "ms");
            ExecutionPhase actual = ju.getPhase(job.getID());
            Assert.assertEquals("phase", ExecutionPhase.ABORTED, actual);
            Assert.assertTrue("duration < 30ms", dt < 30L); // test job sleep was actually interrupted
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    public static class TestJob extends Job
    {
        String id;
        Long dt;
        
        public TestJob(String id, long dt)
        {
            this.id = id;
            this.dt = new Long(dt);
            this.setExecutionPhase(ExecutionPhase.PENDING);
        }

        public TestJob(long dt) { this("abc123", dt); }

        @Override
        public String getID() { return id; }

        @Override
        public Long getExecutionDuration()
        {
            return dt;
        }
    }
    
    public static class TestJobUpdater implements JobUpdater
    {
        Map<String,Job> jobs = new HashMap<String,Job>();

        public ExecutionPhase getPhase(String jobID) throws JobNotFoundException, JobPersistenceException
        {
            Job j=  jobs.get(jobID);
            if (j == null)
                throw new JobNotFoundException(jobID);
            return j.getExecutionPhase();
        }

        public ExecutionPhase setPhase(String jobID, ExecutionPhase start, ExecutionPhase end) throws JobNotFoundException, JobPersistenceException
        {
            Job j=  jobs.get(jobID);
            if (j == null)
                throw new JobNotFoundException(jobID);
            ExecutionPhase phase = j.getExecutionPhase();
            if (phase.equals(start))
                j.setExecutionPhase(end);
            return j.getExecutionPhase();
        }

        public ExecutionPhase setPhase(String jobID, ExecutionPhase start, ExecutionPhase end, Date date) throws JobNotFoundException, JobPersistenceException
        {
            return setPhase(jobID, start, end);
        }

        public ExecutionPhase setPhase(String jobID, ExecutionPhase start, ExecutionPhase end, List<Result> results, Date date) throws JobNotFoundException, JobPersistenceException
        {
            return setPhase(jobID, start, end);
        }

        public ExecutionPhase setPhase(String jobID, ExecutionPhase start, ExecutionPhase end, ErrorSummary error, Date date) throws JobNotFoundException, JobPersistenceException
        {
            return setPhase(jobID, start, end);
        }
    }
    
    public static class TestJobRunner implements JobRunner
    {
        private Job job;
        private JobUpdater jobUpdater;
        private SyncOutput syncOutput;
        
        public void run()
        {
            try
            {
                jobUpdater.setPhase(job.getID(), ExecutionPhase.QUEUED, ExecutionPhase.EXECUTING);
                Thread.sleep(job.getExecutionDuration().longValue());
                jobUpdater.setPhase(job.getID(), ExecutionPhase.EXECUTING, ExecutionPhase.COMPLETED);
            }
            catch(Throwable t)
            {
                try
                {
                    jobUpdater.setPhase(job.getID(), ExecutionPhase.EXECUTING, ExecutionPhase.ERROR);
                }
                catch(Throwable ignore) { } // no fail in test impl
            }

        }

        public void setJob(Job job)
        {
            this.job = job;
        }

        public void setJobUpdater(JobUpdater jobUpdater)
        {
            this.jobUpdater = jobUpdater;
        }

        public void setSyncOutput(SyncOutput syncOutput)
        {
            this.syncOutput = syncOutput;
        }
    }
}
