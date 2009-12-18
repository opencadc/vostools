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

import static junit.framework.TestCase.*;
import static org.easymock.EasyMock.*;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;

import ca.nrc.cadc.uws.*;

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

    private final String JOB_ID = "AT88MPH";
    private Job job;


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

        job = new Job(JOB_ID, ExecutionPhase.PENDING, 88l, cal.getTime(),
                      quoteCal.getTime(), cal.getTime(), cal.getTime(), null,
                      "USER", "RUN_ID", results, parameters);

        mockJobManager = createMock(JobManager.class);
        expect(mockJobManager.getJob(JOB_ID)).andReturn(job).anyTimes();
        expect(mockJobManager.persist(job)).andReturn(job).anyTimes();
        replay(mockJobManager);

        mockJobRunner = createMock(JobRunner.class);
        mockJobRunner.setJob(job);
        mockJobRunner.run();
        replay(mockJobRunner);

        testSubject = new JobSyncSubmissionResource()
        {
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
             * Obtain the current Job ID.
             *
             * @return long Job ID
             */
            @Override
            protected String getJobID()
            {
                return JOB_ID;
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
        };
    }

    @After
    public void tearDown()
    {
        testSubject = null;
    }


    @Test
    public void prepareJob()
    {
        assertEquals("Job Execution Phase should be PENDING.",
                     job.getExecutionPhase(), ExecutionPhase.PENDING);
        testSubject.prepareJob();
        assertEquals("Job Execution Phase should be QUEUED.",
                     job.getExecutionPhase(), ExecutionPhase.QUEUED);        
    }

    @Test
    public void executeJob()
    {
        assertNotNull("Job available", job);
        assertEquals("Job Execution Phase should be PENDING.",
                     job.getExecutionPhase(), ExecutionPhase.PENDING);        
        testSubject.executeJob();
        assertEquals("Job Execution Phase should be QUEUED.",
                     job.getExecutionPhase(), ExecutionPhase.QUEUED);
    }

    @Test
    public void pollRunningJob()
    {
        assertNotNull("Job available", job);
        testSubject.pollRunningJob(10000l);
    }

    @Test
    public void executeJobWhileJobRunning()
    {
        assertNotNull("Job available", job);
        job.setExecutionPhase(ExecutionPhase.QUEUED);
        assertEquals("Job Execution Phase should be QUEUED.",
                     job.getExecutionPhase(), ExecutionPhase.QUEUED);
        final long currTime = System.currentTimeMillis();
        testSubject.executeJob();
        assertTrue("Should be at least three minutes later...",
                   (System.currentTimeMillis() - currTime)
                   >= (3l * 60l * 1000l));
    }
}
