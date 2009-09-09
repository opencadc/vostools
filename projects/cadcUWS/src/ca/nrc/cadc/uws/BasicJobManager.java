/******************************************************************************
 *
 *  Copyright (C) 2009                          Copyright (C) 2009
 *  National Research Council           Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6                     Ottawa, Canada, K1A 0R6
 *  All rights reserved                         Tous droits reserves
 *
 *  NRC disclaims any warranties,       Le CNRC denie toute garantie
 *  expressed, implied, or statu-       enoncee, implicite ou legale,
 *  tory, of any kind with respect      de quelque nature que se soit,
 *  to the software, including          concernant le logiciel, y com-
 *  without limitation any war-         pris sans restriction toute
 *  ranty of merchantability or         garantie de valeur marchande
 *  fitness for a particular pur-       ou de pertinence pour un usage
 *  pose.  NRC shall not be liable      particulier.  Le CNRC ne
 *  in any event for any damages,       pourra en aucun cas etre tenu
 *  whether direct or indirect,         responsable de tout dommage,
 *  special or general, consequen-      direct ou indirect, particul-
 *  tial or incidental, arising         ier ou general, accessoire ou
 *  from the use of the software.       fortuit, resultant de l'utili-
 *                                                              sation du logiciel.
 *
 *
 *  This file is part of cadcUWS.
 *  
 *  cadcUWS is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  cadcUWS is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with cadcUWS.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package ca.nrc.cadc.uws;

import java.util.Collection;
import java.util.Calendar;
import java.util.Date;


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
        return getJobPersistence().getJobs();
    }

    /**
     * Obtain the job with the given identifier.
     *
     * @param jobID Job Identifier.
     * @return Job instance, or null if none found.
     */
    public Job getJob(final long jobID)
    {
        return getJobPersistence().getJob(jobID);
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
        insertDefaultValues(job);
        return getJobPersistence().persist(job);
    }

    /**
     * Insert default values to those fields that require a value, but haven't
     * been given one.
     *
     * @param job The job to insert values for.
     */
    public void insertDefaultValues(final Job job)
    {
        if (job.getExecutionPhase() == null)
        {
            job.setExecutionPhase(ExecutionPhase.PENDING);
        }

        if (job.getExecutionDuration() == 0)
        {
            job.setExecutionDuration(600l);
        }

        // Default twenty-four hours.
        if (job.getDestructionTime() == null)
        {
            final Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            cal.add(Calendar.HOUR, 24);

            job.setDestructionTime(cal.getTime());
        }

        if (job.getQuote() == null)
        {
            final Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            cal.add(Calendar.HOUR, 20);

            job.setQuote(cal.getTime());
        }

        if (job.getStartTime() == null)
        {
            job.setStartTime(new Date());
        }

        if (job.getEndTime() == null)
        {
            final Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            cal.add(Calendar.HOUR, 20);

            job.setEndTime(cal.getTime());
        }
    }


    public JobPersistence getJobPersistence()
    {
        return jobPersistence;
    }

    public void setJobPersistence(final JobPersistence jobPersistence)
    {
        this.jobPersistence = jobPersistence;
    }
}
