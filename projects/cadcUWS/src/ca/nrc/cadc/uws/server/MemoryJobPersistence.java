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

import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;

import ca.nrc.cadc.auth.IdentityManager;
import ca.nrc.cadc.auth.X500IdentityManager;
import ca.nrc.cadc.uws.ErrorSummary;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobRef;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.uws.Result;

/**
 *
 * @author pdowler
 */
public class MemoryJobPersistence implements JobPersistence, JobUpdater
{
    private static final Logger log = Logger.getLogger(MemoryJobPersistence.class);

    protected StringIDGenerator idGenerator;
    protected IdentityManager identityManager;
    private Thread jobCleaner;

    protected final Map<String,Job> jobs = new HashMap<String,Job>();

    public MemoryJobPersistence()
    {
        this(new RandomStringGenerator(16), new X500IdentityManager());
    }

    public MemoryJobPersistence(StringIDGenerator idGenerator, IdentityManager identityManager)
    {
        this.idGenerator = idGenerator;
        this.identityManager = identityManager;
    }

    /**
     * Enable the JobCleaner to automatically delete jobs after the destruction date.
     * If the value of checkInterval is positive, a thread will be spawned that checks
     * the job list every checkInterval (milliseconds) for old jobs. Jobs with a
     * destruction date in the past are deleted. If checkInterval is less than or
     * equal to 0, the current job cleaner thread is terminated and a new one is not
     * started (e.g. job cleaner is disabled).
     * 
     * @param checkInterval time between checks (in milliseconds)
     */
    public void setJobCleaner(long checkInterval)
    {
        terminate();
        if (checkInterval > 0)
        {
            this.jobCleaner = new Thread(new JobCleaner(checkInterval));
            jobCleaner.setDaemon(true);
            jobCleaner.start();
        }
    }

    public void terminate()
    {
        if (jobCleaner != null)
        {
            try
            {
                log.info("terminating JobCleaner...");
                jobCleaner.interrupt();
                jobCleaner.join();
            }
            catch(Throwable t)
            {
                log.error("failed to terminate jobCleaner thread", t);
            }
            finally
            {
                jobCleaner = null;
            }
        }
    }

    private class JobCleaner implements Runnable
    {
        long dt = 6000L;

        JobCleaner(long dt) { this.dt = dt; }

        public void run()
        {
            while(true)
            {
                Date now = new Date();
                try
                {
                    synchronized(jobs)
                    {
                        log.debug("looking for old jobs...");
                        Iterator<Map.Entry<String,Job>> iter = jobs.entrySet().iterator();
                        while ( iter.hasNext() )
                        {
                            if ( Thread.interrupted() )
                                return;
                            Map.Entry<String,Job>me = iter.next();
                            Date t = me.getValue().getDestructionTime();
                            if ( now.compareTo(t) > 0 ) // destruction time has past
                            {
                                log.debug("delete: " + me.getKey() + ", destruction = " + t);
                                iter.remove();
                            }
                        }
                    }
                }
                catch(ConcurrentModificationException ex)
                {
                    log.debug("caught ConcurrentModificationException, ignoring");
                }
                catch(Throwable t)
                {
                    log.error("ignoring failure while cleaning job list", t);
                }

                try { Thread.sleep(dt); }
                catch(InterruptedException ex) { return; }
            }
        }
    }


    public void addParameters(String jobID, List<Parameter> params)
        throws JobNotFoundException
    {
        expectNotNull("jobID", jobID);
        expectNotNull("params", params);
        Job job = getJobFromMap(jobID);
        job.getParameterList().addAll(params);
    }

    public void delete(String jobID)
    {
        expectNotNull("jobID", jobID);
        synchronized(jobs)
        {
            jobs.remove(jobID);
        }
    }

    public Job get(String jobID)
        throws JobNotFoundException
    {
        expectNotNull("jobID", jobID);
        Job job = getJobFromMap(jobID);
        Job ret = JobPersistenceUtil.deepCopy(job);
        ret.ownerSubject = job.ownerSubject;
        return ret;
    }

    public void getDetails(Job job)
    {
        // no-op
    }

    public ExecutionPhase getPhase(String jobID)
        throws JobNotFoundException
    {
        expectNotNull("jobID", jobID);
        Job job = getJobFromMap(jobID);
        return job.getExecutionPhase();
    }

    /**
     * Iterator over the jobs. Note that this could fail if the underlying job list
     * is modified while iterating.
     * 
     * @return
     */
    public Iterator<JobRef> iterator()
    {
        return JobPersistenceUtil.createImmutableIterator(jobs.values().iterator());
    }
    

    public Job put(Job job)
    {
        expectNotNull("job", job);

        AccessControlContext acContext = AccessController.getContext();
        Subject caller = Subject.getSubject(acContext);
        String ownerID = null;
        if (caller != null)
            ownerID = identityManager.toOwnerString(caller);
        job.setOwnerID(ownerID);
        if (job.getID() == null)
            JobPersistenceUtil.assignID(job, idGenerator.getID());
        Job keep = JobPersistenceUtil.deepCopy(job);
        if (ownerID != null)
            keep.ownerSubject = caller;
        synchronized(jobs)
        {
            jobs.put(keep.getID(), keep);
        }
        return job;
    }

    public void setPhase(String jobID, ExecutionPhase ep)
        throws JobNotFoundException
    {
        setPhase(jobID, null, ep);
    }

    public ExecutionPhase setPhase(String jobID, ExecutionPhase start, ExecutionPhase end)
        throws JobNotFoundException
    {
        return setPhase(jobID, start, end, null, null, null);
    }

    public ExecutionPhase setPhase(String jobID, ExecutionPhase start, ExecutionPhase end, Date date)
        throws JobNotFoundException
    {
        return setPhase(jobID, start, end, null, null, date);
    }

    public ExecutionPhase setPhase(String jobID, ExecutionPhase start, ExecutionPhase end, List<Result> results, Date date)
        throws JobNotFoundException
    {
        return setPhase(jobID, start, end, results, null, date);
    }

    public ExecutionPhase setPhase(String jobID, ExecutionPhase start, ExecutionPhase end, ErrorSummary error, Date date)
        throws JobNotFoundException
    {
        return setPhase(jobID, start, end, null, error, date);
    }

    private ExecutionPhase setPhase(String jobID, ExecutionPhase start, ExecutionPhase end,
            List<Result> results, ErrorSummary error, Date date)
        throws JobNotFoundException
    {
        Job job = getJobFromMap(jobID);
        expectNotNull("end", end);
        if (start == null || job.getExecutionPhase().equals(start))
        {
            job.setExecutionPhase(end);
            if (results != null)
                job.setResultsList(results);
            if (error != null)
                job.setErrorSummary(error);
            if (date != null)
            {
                if (ExecutionPhase.EXECUTING.equals(end))
                    job.setStartTime(date);
                else if ( JobPersistenceUtil.isFinalPhase(end) )
                    job.setEndTime(date);
            }
            return end;
        }
        return null;
    }

    private Job getJobFromMap(String jobID)
        throws JobNotFoundException
    {
        if (jobID == null)
            throw new IllegalArgumentException("jobID cannot be null");
        synchronized(jobs)
        {
            Job job = jobs.get(jobID);
            if (job == null)
                throw new JobNotFoundException("not found: " + jobID);
            return job;
        }
    }

    private void expectNotNull(String name, Object value)
    {
        if (value == null)
            throw new IllegalArgumentException(name + " cannot be null");
    }
}
