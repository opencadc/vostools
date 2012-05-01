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

import ca.nrc.cadc.uws.Job;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;

/**
 * Simple implementation of the JobExecutor Service for running asynchronous jobs 
 * in separate threads. This implementation creates a new instance of the JobRunner
 * for each job. For sync jobs, the job runner is run in the calling thread; for async
 * jobs, it starts a new Thread (daemon=true) for each job.
 *
 * @author pdowler
 */
public class ThreadExecutor extends AbstractExecutor
{
    private static final Logger log = Logger.getLogger(ThreadExecutor.class);

    private final Map<String,Thread> currentJobs = new HashMap<String,Thread>();
    private String threadBaseName;
    private int num = 0;
    
    /**
     * Constructor when using constructor dependency injection.
     *
     * @param jobUpdater
     * @param jobRunnerClass
     */
    public ThreadExecutor(JobUpdater jobUpdater, Class jobRunnerClass)
    {
        this(jobUpdater, jobRunnerClass, ThreadExecutor.class.getSimpleName());
    }

    /**
     * Constructor when using constructor dependency injection.
     *
     * @param jobUpdater
     * @param jobRunnerClass
     * @param threadBaseName base name for spawned threads
     */
    public ThreadExecutor(JobUpdater jobUpdater, Class jobRunnerClass, String threadBaseName)
    {
        super(jobUpdater, jobRunnerClass);
        if (threadBaseName == null)
            throw new IllegalArgumentException("threadBaseName cannot be null");
        this.threadBaseName = threadBaseName + "-";
    }

    /**
     * Abort the job. This implementation finds the job thread and if found,
     * calls the interrupt() method. Subclasses should override this if they have
     * also overriden syncExecute or asyncExecute.
     * 
     * @param jobID
     */
    protected void abortJob(String jobID)
    {
        synchronized(currentJobs)
        {
            Thread t = currentJobs.remove(jobID);
            if (t != null && t.isAlive())
                t.interrupt();
        }
    }

    /**
     * Execute the job asynchronously. This method spawns a new thread for every job.
     * The thread is marked as a daemon thread so it will not block application shutdown.
     * 
     * @param job
     * @param jobRunner
     */
    protected void executeAsync(final Job job, final JobRunner jobRunner)
    {
        Runnable r = new Runnable()
        {
            public void run()
            {
                jobRunner.run();
                synchronized(currentJobs)
                {
                    currentJobs.remove(job.getID());
                }
            }
        };
        
        Thread t = new Thread(r);
        t.setName(threadBaseName + Integer.toString(num++));
        t.setDaemon(true); // so the thread will not block application shutdown
        t.start();
        synchronized(currentJobs)
        {
            currentJobs.put(job.getID(), t);
        }
    }
}
