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
 * Aug 6, 2009 - 11:13:42 AM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.uws.web.threadpool;


/**
 * A task-oriented Thread.
 */
public class WorkerThread extends Thread
{
    private final ThreadPool pool;


    /**
     * Complete, and only Constructor.
     *
     * @param pool          The pool that this thread will be pulling tasks
     *                      from.
     * @param threadName    This Thread's name.
     */
    public WorkerThread(final ThreadPool pool, final String threadName)
    {
        this.pool = pool;
        setName(threadName);
    }

    /**
     * Execute the tasks of this Thread's pool.
     */
    public void run()
    {
        while (!getPool().getShutDownStatus()
               || !getPool().getQueue().isEmpty())
        {
            try
            {
                final Runnable runnable = getPool().getQueue().take();
                getPool().increaseThreadCount();
                runnable.run();
                getPool().decreaseThreadCount();
                
                synchronized (getPool())
                {
                    getPool().notifyAll();
                }
            }
            catch (InterruptedException e)
            {
                //e.printStackTrace();
                System.out.println("Thread " + getName() + " interrupted.");
                return;
            }
        }
    }

    public ThreadPool getPool()
    {
        return pool;
    }
}
