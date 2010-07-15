/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2009.                            (c) 2009.
 * National Research Council            Conseil national de recherches
 * Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 * All rights reserved                  Tous droits reserves
 *
 * NRC disclaims any warranties         Le CNRC denie toute garantie
 * expressed, implied, or statu-        enoncee, implicite ou legale,
 * tory, of any kind with respect       de quelque nature que se soit,
 * to the software, including           concernant le logiciel, y com-
 * without limitation any war-          pris sans restriction toute
 * ranty of merchantability or          garantie de valeur marchande
 * fitness for a particular pur-        ou de pertinence pour un usage
 * pose.  NRC shall not be liable       particulier.  Le CNRC ne
 * in any event for any damages,        pourra en aucun cas etre tenu
 * whether direct or indirect,          responsable de tout dommage,
 * special or general, consequen-       direct ou indirect, particul-
 * tial or incidental, arising          ier ou general, accessoire ou
 * from the use of the software.        fortuit, resultant de l'utili-
 *                                      sation du logiciel.
 *
 *
 * @author jenkinsd
 * Dec 17, 2009 - 11:45:10 AM
 *
 * 
 * 
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.uws.web.restlet.resources;

import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobManager;
import ca.nrc.cadc.uws.JobRunner;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.uws.Result;
import static junit.framework.TestCase.*;
import static org.easymock.EasyMock.*;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;

import java.util.Calendar;
import java.util.List;
import java.util.ArrayList;

/**
 * Test the Synchronous Job Resource.
 */
public class JobSyncSubmissionResourceTest
{
    protected JobSyncSubmissionResource testSubject;
    protected JobManager mockJobManager;
    protected JobRunner mockJobRunner;

    private final long POLL_TIME = 3000L;
    private final String JOB_ID = "AT88MPH";
    private Job testJob;

    @Before
    public void setup()
    {
        final Calendar cal = Calendar.getInstance();
        cal.set(1997, Calendar.NOVEMBER, 25, 3, 21, 0);
        cal.set(Calendar.MILLISECOND, 0);

        final Calendar quoteCal = Calendar.getInstance();
        quoteCal.set(1977, Calendar.NOVEMBER, 25, 8, 30, 0);
        quoteCal.set(Calendar.MILLISECOND, 0);

        final List<Result> results = new ArrayList<Result>();
        final List<Parameter> parameters = new ArrayList<Parameter>();
        
        testJob = new Job(JOB_ID, ExecutionPhase.PENDING, 88l, cal.getTime(),
                      quoteCal.getTime(), cal.getTime(), cal.getTime(), null,
                      null, "RUN_ID", results, parameters, null);

        mockJobManager = createMock(JobManager.class);
        expect(mockJobManager.getJob(JOB_ID)).andReturn(testJob).anyTimes();
        expect(mockJobManager.persist(testJob)).andReturn(testJob).anyTimes();
        replay(mockJobManager);

        mockJobRunner = createMock(JobRunner.class);
        mockJobRunner.setJob(testJob);
        mockJobRunner.setJobManager(mockJobManager);
        expect(mockJobRunner.getJob()).andReturn(testJob).anyTimes();

        mockJobRunner.run();
        replay(mockJobRunner);

        testSubject = new JobSyncSubmissionResource()
        {
            @Override
            protected void doInit()
            {
                this.job = JobSyncSubmissionResourceTest.this.testJob;
            }
            
            /**
             * Obtain this Resource's Job Service.
             *
             * @return JobService instance, or null if none set.
             */
            @Override
            protected JobManager getJobManager()
            {
                return mockJobManager;
            }

            /**
             * Obtain a new instance of the Job Runner interface as defined in the
             * Context
             *
             * @return The JobRunner instance.
             */
            @Override
            protected JobRunner createJobRunner()
            {
                return mockJobRunner;
            }

            /**
             * Poll the current job.
             */
            @Override
            protected void pollRunningJob()
            {
                super.pollRunningJob(POLL_TIME);
            }
        };
        testSubject.doInit();
    }

    @After
    public void tearDown()
    {
        testSubject = null;
    }

    @Test
    public void executeJob()
    {
        assertNotNull("Job available", testJob);
        assertEquals("Job Execution Phase should be PENDING.",
                     testJob.getExecutionPhase(), ExecutionPhase.PENDING);
        testSubject.executeJob();
        assertEquals("Job Execution Phase should be QUEUED.",
                     testJob.getExecutionPhase(), ExecutionPhase.QUEUED);
    }

    @Test
    public void pollRunningJob()
    {
        assertNotNull("Job available", testJob);
        testSubject.pollRunningJob(10000l);
    }

    @Test
    public void executeJobWhileJobRunning()
    {
        assertNotNull("Job available", testJob);
        testJob.setExecutionPhase(ExecutionPhase.QUEUED);
        assertEquals("Job Execution Phase should be QUEUED.",
                     testJob.getExecutionPhase(), ExecutionPhase.QUEUED);
        final long currTime = System.currentTimeMillis();
        testSubject.executeJob();
        assertTrue("Should be at least "+POLL_TIME + "ms...",
                   (System.currentTimeMillis() - currTime)
                   >= (POLL_TIME));
    }
}
