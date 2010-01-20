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
 * Dec 15, 2009 - 10:07:29 AM
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
import org.restlet.Request;
import org.restlet.data.Reference;
import org.restlet.representation.EmptyRepresentation;
import ca.nrc.cadc.uws.*;

import java.util.Calendar;
import java.util.ArrayList;
import java.util.List;

import javax.security.auth.Subject;


public class JobAsynchResourceExecutionTest
{
    protected JobRunner mockJobRunner;
    protected JobExecutor mockJobExecutor;
    protected JobAsynchResource testSubject;


    @Before
    public void setup()
    {
        mockJobRunner = createMock(JobRunner.class);

        mockJobExecutor = createMock(JobExecutor.class);
        mockJobExecutor.execute(mockJobRunner, null);
        mockJobExecutor.execute(mockJobRunner, null);
        replay(mockJobExecutor);
    }

    @After
    public void tearDown()
    {
        testSubject = null;
    }


    /**
     * Test checking for job execution.
     */
    @Test
    public void executeJob()
    {
        final Calendar cal = Calendar.getInstance();
        cal.set(1997, Calendar.NOVEMBER, 25, 3, 21, 0);
        cal.set(Calendar.MILLISECOND, 0);

        final Calendar quoteCal = Calendar.getInstance();
        quoteCal.set(1977, Calendar.NOVEMBER, 25, 8, 30, 0);
        quoteCal.set(Calendar.MILLISECOND, 0);

        final List<Result> results = new ArrayList<Result>();
        final List<Parameter> parameters = new ArrayList<Parameter>();
        
        final Subject subject = new Subject();

        final Job testJob =
                new Job("88l", ExecutionPhase.QUEUED, 88l, cal.getTime(),
                        quoteCal.getTime(), cal.getTime(), cal.getTime(), null,
                        "USER", "RUN_ID", results, parameters, subject);

        testSubject = new JobAsynchResource()
        {
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

            @Override
            protected JobExecutor getJobExecutorService()
            {
                return mockJobExecutor;
            }

            /**
             * Obtain the current Job in the context of this Request.
             *
             * @return This Request's Job.
             */
            @Override
            protected Job getJob()
            {
                return testJob;
            }
        };        

        mockJobRunner.setJob(testJob);
        replay(mockJobRunner);

        testSubject.executeJob();
    }

    @Test
    public void accept() throws Exception
    {
        final Calendar cal = Calendar.getInstance();
        cal.set(1997, Calendar.NOVEMBER, 25, 3, 21, 0);
        cal.set(Calendar.MILLISECOND, 0);

        final Calendar quoteCal = Calendar.getInstance();
        quoteCal.set(1977, Calendar.NOVEMBER, 25, 8, 30, 0);
        quoteCal.set(Calendar.MILLISECOND, 0);

        final List<Result> results = new ArrayList<Result>();
        final List<Parameter> parameters = new ArrayList<Parameter>();
        
        final Subject subject = new Subject();

        final Job testJob =
                new Job("88l", ExecutionPhase.QUEUED, 88l, cal.getTime(),
                        quoteCal.getTime(), cal.getTime(), cal.getTime(), null,
                        "USER", "RUN_ID", results, parameters, subject);

        testSubject = new JobAsynchResource()
        {
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

            @Override
            protected JobExecutor getJobExecutorService()
            {
                return mockJobExecutor;
            }

            @Override
            public Request getRequest()
            {
                final Request request = new Request();
                request.setResourceRef(
                        new Reference("http://www.mysite.ca/reference"));
                request.setEntity(null);

                return request;
            }

            /**
             * Obtain the current Job in the context of this Request.
             *
             * @return This Request's Job.
             */
            @Override
            protected Job getJob()
            {
                return testJob;
            }
        };        

        try
        {
            testSubject.accept(null);
            fail("Not allowed to POST to already running job.");
        }
        catch (final Throwable t)
        {
            // GOOD!
        }
    }
}
