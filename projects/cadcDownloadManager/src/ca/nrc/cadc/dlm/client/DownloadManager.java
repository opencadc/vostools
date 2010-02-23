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

package ca.nrc.cadc.dlm.client;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import ca.nrc.cadc.dlm.client.event.DownloadEvent;
import ca.nrc.cadc.dlm.client.event.DownloadListener;
import ca.nrc.cadc.thread.Queue;
import ca.nrc.cadc.thread.QueueUpdater;

/**
 * Handles the core download logic.
 * 
 * @author majorb
 *
 */
public class DownloadManager implements DownloadListener
{
    
    private static boolean testing = false;
    private boolean debug = false;
    
    static final int MAX_THREAD_COUNT = 11;
    static final int DEFAULT_THREAD_COUNT = 1;

    private ThreadPool pool;
    private ThreadControl threadControl;
    
    private List changeListeners;
    private List downloadListeners;
    
    private File destDir;
    
    public DownloadManager(ThreadControl threadControl, int initialThreadCount, File destinationDir)
    {
        
        this.threadControl = threadControl;

        if (initialThreadCount < 1)
            initialThreadCount = 1;
        if (initialThreadCount > MAX_THREAD_COUNT)
            initialThreadCount = MAX_THREAD_COUNT;
        
        setDestinationDir(destinationDir);
    }

    public void setDebug(boolean debug) { this.debug = debug; }
    
    private void msg(String s)
    {
         if (debug) System.out.println("[JDownloadManager] " + s);
    }

    public int getThreadCount() 
    {
        return ((Integer)threadControl.getValue()).intValue(); 
    }
    
    public void setThreadCount(int tc) 
    {
        threadControl.setValue(new Integer(tc));
    }

    public void setDestinationDir(File destDir) { this.destDir = destDir; }

    public File getDestinationDir() { return destDir; } 
    
    public void start()
    {
        if (pool == null)
        {
            msg("creating ThreadPool");
            this.pool = new ThreadPool();
            threadControl.addListener(pool);
        }
    }
    
    /**
     * Terminate all downloads and release resources.
     */
    public void stop()
    {
        if (pool != null)
        {    
            pool.terminate();
            pool = null;
        }
    }
    
    public void startThreadControl()
    {
        threadControl.start();
    }
    
    public void stopThreadControl()
    {
        threadControl.stop();
    }
    
    /**
     * Register for change events. This class fires events when configuration items 
     * (thread pool size, destination directory) are changed/set by the user.
     * 
     * @param cl the listener
     */
    public void addChangeListener(ChangeListener cl)
    {
        if (changeListeners == null)
            this.changeListeners = new ArrayList();
        changeListeners.add(cl);
    }
    
    public void removeChangeListener(ChangeListener cl)
    {
        if (changeListeners == null)
            return;
        changeListeners.remove(cl);
    }
    
    void fireChangeEvent()
    {
        if (changeListeners == null)
            return;
        ChangeEvent e = new ChangeEvent(this);
        for (int i = 0; i < changeListeners.size(); i++)
            ((ChangeListener) changeListeners.get(i)).stateChanged(e);
    }
    
    /**
     * Register for download events.
     *
     * @param dl the listener
     */
    public void addDownloadListener(DownloadListener dl)
    {
        if (dl == null)
            return;
        if (downloadListeners == null)
            this.downloadListeners = new ArrayList();
        downloadListeners.add(dl);
    }
    
    public void removeDownloadListener(DownloadListener dl)
    {
        if (dl == null)
            return;
        if (downloadListeners == null)
            return;
        downloadListeners.remove(dl);
    }
    
    // DownloadListener
    public void downloadEvent(DownloadEvent e)
    {
        msg("downloadEvent: " + e);
        // rebroadcast event to other listeners
        if (downloadListeners == null || downloadListeners.size() == 0)
            return;
        for (int i=0; i<downloadListeners.size(); i++)
        {
            DownloadListener dl = (DownloadListener) downloadListeners.get(i);
            dl.downloadEvent(e);
        }
    }

    public String getEventHeader()
    {
        return null;
    }
    
    public void addDownload(final Download dl)
    {
        dl.setDownloadListener(this);
        dl.destDir = getDestinationDir();
        pool.add(dl);
    }
    
    private String workerBasename = "JDownloadManager.WorkerThread: ";

    private class ThreadPool implements ThreadControlListener
    {
        private Queue tasks;
        private ArrayList threads;
        private int numWorkers;

        public ThreadPool()
        {
            this.tasks = new Queue();
            this.threads = new ArrayList(MAX_THREAD_COUNT);
            threadValueChanged(null); // init threads
        }
        void msg(String s)
        {
            if (debug) System.out.println("[ThreadPool] " + s);
        }
        
        public void add(Download task)
        {
            msg("queueing: " + task);
            tasks.push(task);
        }

        public void terminate()
        {
            msg("ThreadPool.terminate()");
            
            // terminate thread pool members
            numWorkers = 0;
            synchronized(threads) // vs sync block in WorkerThread.run() that calls threads.remove(this) after an interrupt
            {
                for (int i=0; i<threads.size(); i++)
                {
                    msg("ThreadPool.terminate() interrupting WorkerThread " + i);
                    WorkerThread wt = (WorkerThread) threads.get(i);
                    synchronized(wt)
                    {
                        msg("ThreadPool.terminate(): interrupting " + wt.getName());
                        wt.interrupt();
                    }
                }
            }

            // pop remaining tasks from queue and cancel them
            msg("ThreadPool.terminate() flushing queue");
            tasks.update(new QueueFlush());
            msg("ThreadPool.terminate() DONE");
        }
        
        public void threadValueChanged(Integer newValue)
        {
            msg("ThreadPool: stateChanged("+newValue+")");
            this.numWorkers = ((Integer) threadControl.getValue()).intValue();
            if (numWorkers < 1)
            {
                threadControl.setValue(new Integer(1));
                return;
            }
            if (numWorkers > MAX_THREAD_COUNT)
            {
                threadControl.setValue(new Integer(MAX_THREAD_COUNT));
                return;
            }
                
            msg("current thread count: " + threads.size() + " new size: " + numWorkers);
            if (threads.size() == numWorkers)
                return;
            
            synchronized(threads)
            {
                while (threads.size() < numWorkers)
                {
                    msg("adding worker thread");
                    WorkerThread t = new WorkerThread();
                    t.setPriority(Thread.MIN_PRIORITY); // mainly IO blocked anyway, so keep the UI thread happy
                    threads.add(t);
                    t.start();
                }
            }
            
            if (numWorkers == 11)
            {
                // TODO: play a little guitar riff?
            }
            
            // it is possible that the state didn't actually change in cases where 
            // we enforced the min or max values above, but this should be harmless
            fireChangeEvent();
        }

        // this updater runs through the queue, removes Downloads, and terminates them
        private class QueueFlush implements QueueUpdater
        {
            // this method has exclusive access to the queue contents, so we do not
            // have to worry about a WorkerThread taking something with pop()
            public boolean update(List list)
            {
                int count = 0;
                ListIterator i = list.listIterator();
                while ( i.hasNext() )
                {
                    Download dl = (Download) i.next();
                    msg("ThreadPool: terminating queued download");
                    dl.terminate();
                    i.remove();
                    count++;
                }
                return (count > 0);
            }
        }
    
        private class WorkerThread extends Thread
        {
            Download currentTask;
            
            WorkerThread()
            {
                super();
                setDaemon(true);
                setName(workerBasename);
            }

            // threads keep running as long as they are in the threads list
            public void run()
            {
                msg(workerBasename + "START");
                boolean cont = true;
                while (cont)
                {
                    try
                    {
                        Object tmp = tasks.pop(); // block here
                        synchronized(this) 
                        {
                            currentTask = (Download) tmp;
                        }
                        synchronized(threads) 
                        {
                            cont = threads.contains(this); 
                        }
                        if (cont)
                        {
                            msg(workerBasename + "still part of pool");
                            // set thread name so thread dumps are intelligible
                            setName(workerBasename + currentTask);
                            currentTask.run();
                        }
                        else
                        {
                            msg(workerBasename + "no longer part of pool");
                            synchronized(this) // vs sync block in terminate()
                            {
                                // make sure to clear interrupt flag from an interrupt() in stateChanged()
                                // in case it comes after pop() and before threads.contains()
                                interrupted();
                                // we should quit, so put task back
                                msg(workerBasename + "OOPS (put it back): " + tmp);
                                tasks.push(tmp);
                                currentTask = null;
                            }
                        }
                    }
                    catch (InterruptedException ignore)
                    {
                        // pop() was interrupted, let finally and while condition decide if we
                        // should loop or return
                    }
                    finally
                    {
                        setName(workerBasename);
                        synchronized(this) { currentTask = null; }
                        synchronized(threads)
                        {
                            if (threads.size() > numWorkers)
                            {
                                msg(workerBasename + "numWorkers=" + numWorkers + " threads.size() = " + threads.size());
                                threads.remove(this);
                            }
                            cont = threads.contains(this);
                        }
                    }
                }
                msg(workerBasename + "DONE");
            }
        }
    }

}
