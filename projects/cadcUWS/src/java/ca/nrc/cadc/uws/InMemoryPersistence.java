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

import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.uws.JobPersistence;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.*;


/**
 * Default implementation of the Job ORM.  It consists of an in memory Map.
 */
public class InMemoryPersistence implements JobPersistence
{
    private final static ConcurrentMap<Long, Job> JOBS =
            new ConcurrentHashMap<Long, Job>();

    static
    {
        final List<Parameter> params = new ArrayList<Parameter>();
        final Calendar quote = Calendar.getInstance();
        final Calendar startTime = Calendar.getInstance();
        final Calendar endTime = Calendar.getInstance();
        final Calendar destructionTime = Calendar.getInstance();

        params.add(new Parameter("PARAM1", "PARAM1_VAL"));
        params.add(new Parameter("PARAM2", "PARAM2_VAL"));

        quote.set(1977, Calendar.NOVEMBER, 25, 8, 30, 0);
        startTime.set(1977, Calendar.NOVEMBER, 25, 0, 10, 0);
        endTime.set(1977, Calendar.NOVEMBER, 25, 3, 21, 0);
        destructionTime.set(1977, Calendar.NOVEMBER, 25, 9, 0, 0);

        JOBS.put(88l, new Job(88l, ExecutionPhase.EXECUTING, 88l,
                              destructionTime.getTime(), quote.getTime(),
                              startTime.getTime(), endTime.getTime(), null,
                              null, "RUN88", null, null));
        JOBS.put(888l, new Job(888l, ExecutionPhase.EXECUTING, 888l,
                               destructionTime.getTime(), quote.getTime(),
                               startTime.getTime(), endTime.getTime(), null,
                               null, "RUN888", null, params));
        JOBS.put(8888l, new Job(8888l, ExecutionPhase.EXECUTING, 8888l,
                                destructionTime.getTime(), quote.getTime(),
                                startTime.getTime(), endTime.getTime(), null,
                                null, "RUN8888", null, null));        
    }


    /**
     * Default constructor.
     */
    public InMemoryPersistence()
    {
    }

    /**
     * Obtain a Job from the persistence layer.
     *
     * @param jobID The job identifier.
     * @return Job instance, or null if none found.
     */
    public Job getJob(final long jobID)
    {
        return getPersistentJobs().get(jobID);
    }

    public Collection<Job> getJobs()
    {
        return getPersistentJobs().values();
    }

    /**
     * Persist the given Job.
     *
     * @param job Job to persist.
     * @return The persisted Job, complete with a surrogate key, if
     *         necessary.
     */
    public Job persist(final Job job)
    {
        final Job persistentJob;
        final Long jobID;

        if (job.getJobId() != null)
        {
            jobID = job.getJobId();
            persistentJob = getJob(jobID);

            persistentJob.setExecutionPhase(job.getExecutionPhase());
            persistentJob.setDestructionTime(job.getDestructionTime());
            persistentJob.setExecutionDuration(job.getExecutionDuration());
        }
        else
        {
            jobID = generateNewID();
            persistentJob = new Job(jobID, job.getExecutionPhase(),
                                               job.getExecutionDuration(),
                                               job.getDestructionTime(),
                                               job.getQuote(),
                                               job.getStartTime(),
                                               job.getEndTime(),
                                               job.getError(),
                                               job.getOwner(), job.getRunId(),
                                               job.getResultsList(),
                                               job.getParameterList());
        }

        getPersistentJobs().put(jobID, persistentJob);

        return persistentJob;
    }

    private ConcurrentMap<Long, Job> getPersistentJobs()
    {
        return JOBS;
    }

    /**
     * Generate a new ID.
     *
     * @return  Long ID.
     */
    private Long generateNewID()
    {
        Long upper = 10000l;

        for (final Map.Entry<Long, Job> entry : getPersistentJobs().entrySet())
        {
            final Long key = entry.getKey();

            if (key > upper)
            {
                upper = entry.getKey();
            }
        }

        return upper + 100l;
    }
}
