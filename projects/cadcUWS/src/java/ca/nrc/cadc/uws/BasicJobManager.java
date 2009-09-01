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
 * Jul 29, 2009 - 9:30:04 AM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.uws;

import ca.nrc.cadc.uws.JobPersistence;
import ca.nrc.cadc.uws.InMemoryPersistence;

import java.util.Collection;


/**
 * Default implementation of the Job Service.
 */
public class BasicJobManager implements JobManager
{
    private JobPersistence jobPersistence;


    /**
     * Obtain the Jobs that are available to the client.
     *
     * @return Collection of Job instances, or empty Collection, never NULL.
     */
    public Collection<Job> getJobs()
    {
        return getJobORM().getJobs();
    }

    /**
     * Obtain the job with the given identifier.
     *
     * @param jobID Job Identifier.
     * @return Job instance, or null if none found.
     */
    public Job getJob(final long jobID)
    {
        return getJobORM().getJob(jobID);
    }

    /**
     * Persist the given Job.
     *
     * @param job The Job to persist.
     * @return The persisted Job, populated, as necessary, with a
     *         unique identifier.
     */
    public Job persist(final Job job)
    {
        return getJobORM().persist(job);
    }


    public JobPersistence getJobORM()
    {
        if (jobPersistence == null)
        {
            setJobORM(new InMemoryPersistence());
        }
        
        return jobPersistence;
    }

    public void setJobORM(final JobPersistence jobPersistence)
    {
        this.jobPersistence = jobPersistence;
    }
}
