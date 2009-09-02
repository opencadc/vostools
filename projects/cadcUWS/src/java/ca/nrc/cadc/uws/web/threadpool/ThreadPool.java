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

package ca.nrc.cadc.uws.web.threadpool;


import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * ThreadPool implementation.
 */
public class ThreadPool
{
    private Thread[] threadPool;
    private BlockingQueue<Runnable> queue;
    private int threadCount = 0;
    private boolean shutDown = false;


    /**
     * Constructor.
     *
     * @param size      Initial size of the pool.
     */
    public ThreadPool(final int size)
    {
        threadPool = new Thread[size];
    }

    /**
     * Single initialization of this Pool.
     */
    public void init()
    {
        setQueue(new LinkedBlockingQueue<Runnable>(100));

        for (int i = 0; i < getPool().length; i++)
        {
            getPool()[i] = new WorkerThread(this, "WorkerThread" + i);
            getPool()[i].start();
        }
    }

    /**
     * Submit a new task to the Queue.
     * @param runnable      The Task to execute.
     */
    public void submitTask(final Runnable runnable)
    {
        try
        {
            synchronized (this)
            {
                if (shutDown)
                {
                    System.out.println("Sorry, ThreadPool is closed...");
                    return;
                }
            }

            getQueue().put(runnable);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    public BlockingQueue<Runnable> getQueue()
    {
        return queue;
    }

    protected void setQueue(final BlockingQueue<Runnable> queue)
    {
        this.queue = queue;
    }

    public Thread[] getPool()
    {
        return threadPool;
    }

    public int getThreadCount()
    {
        return threadCount;
    }

    public void setThreadCount(final int threadCount)
    {
        this.threadCount = threadCount;
    }

    public synchronized void increaseThreadCount()
    {
        setThreadCount(getThreadCount() + 1);
    }

    public synchronized void decreaseThreadCount()
    {
        setThreadCount(getThreadCount() - 1);
    }

    /**
     * Shutdown this pool, effectively halting all executions.
     */
    public synchronized void shutDown()
    {
        shutDown = true;
        while ((getThreadCount() > 0) || !getQueue().isEmpty())
        {
            try
            {
                wait();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }

        for (final Thread thread : getPool())
        {
            if (thread.isAlive())
            {
                thread.interrupt();
            }
        }
    }

    public synchronized boolean getShutDownStatus()
    {
        return shutDown;
    }
}


