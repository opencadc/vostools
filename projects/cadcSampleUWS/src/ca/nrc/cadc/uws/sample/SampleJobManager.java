
package ca.nrc.cadc.uws.sample;

import ca.nrc.cadc.uws.server.JobExecutor;
import ca.nrc.cadc.uws.server.JobPersistence;
import ca.nrc.cadc.uws.server.MemoryJobPersistence;
import ca.nrc.cadc.uws.server.ThreadExecutor;
import ca.nrc.cadc.uws.server.SimpleJobManager;
import org.apache.log4j.Logger;

/**
 *
 * @author pdowler
 */
public class SampleJobManager extends SimpleJobManager
{
    private static Logger log = Logger.getLogger(SampleJobManager.class);

    /**
     * Sample job manager implementation. This class extends the SimpleJobManager
     * and sets up the persistence and executor classes in the constructor. It uses
     * the MemoryJobPersistence implementation and the ThreadExecutor implementation
     * and thus can handle both sync and async jobs.
     */
    public SampleJobManager()
    {
        super();
        MemoryJobPersistence jobPersist = new MemoryJobPersistence();
        JobExecutor jobExec = new ThreadExecutor(jobPersist, HelloWorld.class);
        super.setJobPersistence(jobPersist);
        super.setJobExecutor(jobExec);
    }
}
