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
 * Jul 29, 2009 - 9:32:45 AM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.uws;



/**
 * Default implementation of the JobExecutor Service.
 */
public class ThreadExecutor implements JobExecutor
{
    public static final String PROP_POOL_SIZE =
            "ca.nrc.cadc.executor.pool.size";
    public static final int DEFAULT_POOL_SIZE = 5;


    private JobRunner jobRunner;


    /**
     * Hidden no-arg constructor for JavaBean tools like Reflection.
     */
    ThreadExecutor()
    {
        
    }

    /**
     * Constructor for this Service.
     *
     * @param jobRunner The Job Runner to execute jobs.
     */
    public ThreadExecutor(final JobRunner jobRunner)
    {
        setJobRunner(jobRunner);
    }


    /**
     * Execute the given Job.
     *
     * @param job The Job to execute.  No nulls area permitted.
     */
    public void execute(final Job job)
    {
        new Thread(getJobRunner()).start();
    }



    public JobRunner getJobRunner()
    {
        return jobRunner;
    }

    public void setJobRunner(final JobRunner jobRunner)
    {
        this.jobRunner = jobRunner;
    }
}
