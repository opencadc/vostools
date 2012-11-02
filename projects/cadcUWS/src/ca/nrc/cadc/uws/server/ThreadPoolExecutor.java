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

import ca.nrc.cadc.auth.RunnableAction;
import ca.nrc.cadc.uws.Job;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.security.auth.Subject;
import org.apache.log4j.Logger;

/**
 * JobExecutor implementation that uses a pool of threads for execution. The
 * pool limits the number of jobs that run simultaneously, which may be beneficial
 * if resources are limited or contention would make aggregate execution times worse.
 * 
 * @author pdowler
 */
public class ThreadPoolExecutor extends AbstractExecutor
{
    private static final Logger log = Logger.getLogger(ThreadPoolExecutor.class);

    private final Map<String,CurrentJob> currentJobs = new HashMap<String,CurrentJob>();
    
    private java.util.concurrent.ThreadPoolExecutor threadPool;
    private String poolName;
    private Subject poolSubject;

    /**
     * Constructor. Uses a default priority comparator (FIFO) and default poolName.
     *
     * @param jobUpdater JobUpdater implementation
     * @param jobRunnerClass JobRunner implementation class
     * @param poolSize minimum (initial) number of running threads
     */
    public ThreadPoolExecutor(JobUpdater jobUpdater, Class jobRunnerClass, int poolSize)
    {
        super(jobUpdater, jobRunnerClass);
        init(poolSize, ThreadPoolExecutor.class.getSimpleName());
    }

    /**
     * Constructor. Uses a default priority comparator (FIFO).
     *
     * @param jobUpdater JobUpdater implementation
     * @param jobRunnerClass JobRunner implementation class
     * @param poolName name of this pool (base name for threads)
     * @param poolSize minimum (initial) number of running threads
     */
    public ThreadPoolExecutor(JobUpdater jobUpdater, Class jobRunnerClass,
            int poolSize, String poolName)
    {
        super(jobUpdater, jobRunnerClass);
        init(poolSize, poolName);
    }

    @Override
    public void terminate()
        throws InterruptedException
    {
        log.info("shutting down ThreadPool...");
        threadPool.shutdown();
        threadPool.awaitTermination(120, TimeUnit.SECONDS);
        log.info("shutting down ThreadPool... [OK]");
    }

    private void init(int poolSize, String poolName)
    {
        
        if (poolName == null)
            throw new IllegalArgumentException("poolName cannot be null");
        if (poolSize < 1)
            throw new IllegalArgumentException("poolSize must be > 0");
        this.poolName = poolName + "-";
        
        this.threadPool = new java.util.concurrent.ThreadPoolExecutor(poolSize, poolSize,
                Long.MAX_VALUE, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(),
                new DaemonThreadFactory());

        // TODO: could implement a background thread that scans the currentJob map for jobs
        // where currentTime - startTime > executionDuration and kill them off; sort of like
        // the thread in InMemoryPersistence deletes jobs past their destructionTime
    }

    private class DaemonThreadFactory implements ThreadFactory
    {
        private int num = 1;
        public Thread newThread(Runnable r)
        {
            Thread t = new Thread(r);
            t.setName(poolName + Integer.toString(num++));
            log.debug("created: " + t.getName());
            t.setDaemon(true); // so the thread will not block application shutdown
            return t;
        }
    }

    /**
     * Execute async job. This method hands the job off to a thread pool for
     * execution.
     *
     * @param job
     * @param jobRunner
     */
    @Override
    protected final void executeAsync(Job job, JobRunner jobRunner)
    {
        AccessControlContext acContext = AccessController.getContext();
        Subject caller = Subject.getSubject(acContext);

        final CurrentJob cj = new CurrentJob(job, jobRunner, caller);
        synchronized(currentJobs)
        {
            this.currentJobs.put(job.getID(), cj);
        }
        // IMPORTANT: run the submit using an internal poolSubject so lazily
        // spawned threads do not inherit the AccessControlContext of the caller
        cj.future = Subject.doAs(poolSubject, new PrivilegedAction<Future>()
            {
                public Future run()
                {
                    return threadPool.submit(cj);
                }
            });
    }

    /**
     * Abort tyhe job. This method removes the executing job from the thread pool
     * list of queued jobs. If the job already started executing, this method does
     * nothing.
     *
     * @param jobID
     */
    @Override
    protected final void abortJob(String jobID)
    {
        synchronized(currentJobs)
        {
            CurrentJob r = currentJobs.remove(jobID);
            if (r != null)
            {
                if (r.future != null)
                {
                    r.future.cancel(true);     // try to interrupt running
                    //futureJobs.remove(r.future);
                }
                threadPool.remove(r.runnable); // try to remove queued
            }
        }
    }

    /**
     * Simple wrapper to contain the job, jobRunner, and time.
     */
    public final class CurrentJob implements Runnable
    {
        public Job job;
        public JobRunner runnable;
        private Future future;
        private Subject subject;
        
        CurrentJob(Job job, JobRunner runnable, Subject subject)
        {
            this.job = job;
            this.runnable = runnable;
            this.subject = subject;
        }

        @Override
        public int hashCode() { return job.getID().hashCode(); }

        @Override
        public boolean equals(Object o)
        {
            if (o instanceof CurrentJob)
            {
                CurrentJob cj = (CurrentJob) o;
                return job.getID().equals(cj.job.getID());
            }
            return false;
        }

        public void run()
        {
            if (subject == null)
                runnable.run();
            else
                Subject.doAs(subject, new RunnableAction(runnable));
            synchronized(currentJobs)
            {
                currentJobs.remove(job.getID());
            }
        }

    }
}
