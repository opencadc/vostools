/*****************************************************************************
 *  
 *  Copyright (C) 2009				Copyright (C) 2009
 *  National Research Council		Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6			Ottawa, Canada, K1A 0R6
 *  All rights reserved				Tous droits reserves
 *  					
 *  NRC disclaims any warranties,	Le CNRC denie toute garantie
 *  expressed, implied, or statu-	enoncee, implicite ou legale,
 *  tory, of any kind with respect	de quelque nature que se soit,
 *  to the software, including		concernant le logiciel, y com-
 *  without limitation any war-		pris sans restriction toute
 *  ranty of merchantability or		garantie de valeur marchande
 *  fitness for a particular pur-	ou de pertinence pour un usage
 *  pose.  NRC shall not be liable	particulier.  Le CNRC ne
 *  in any event for any damages,	pourra en aucun cas etre tenu
 *  whether direct or indirect,		responsable de tout dommage,
 *  special or general, consequen-	direct ou indirect, particul-
 *  tial or incidental, arising		ier ou general, accessoire ou
 *  from the use of the software.	fortuit, resultant de l'utili-
 *  								sation du logiciel.
 *  
 *  
 *  This file is part of cadcUtil.
 *  
 *  CadcUtil is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  CadcUtil is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with cadcUtil.  If not, see <http://www.gnu.org/licenses/>.			
 *  
 *****************************************************************************/

package ca.nrc.cadc.thread;

import java.util.LinkedList;

/**
 * Simple implementation of a thread-safe queue (FIFO). The peek() 
 * and pop() methods block if the queue is empty and waits for a new Object
 * to be inserted.
 *
 * @version $Revision: 283 $
 * @author $Author: pdowler $
 */
public class Queue
{
	protected LinkedList<Object> list;

	public Queue()
	{
		this.list = new LinkedList<Object>();
	}

	/**
	 * Add an object to the bottom of the queue.
	 */
	public synchronized void push(Object obj)
	{
	    list.addLast(obj);  // add at the back
	    this.notify();      // wake up one waiting thread
	}

    /**
     * Look at the first object. This method blocks until an
     * object becomes available.
     *
     * @return the object from the front of the queue
     */
    public synchronized Object peek()
        throws InterruptedException
    {
        while (list.size() == 0)
            wait();
        return list.getFirst();
    }
    
	/**
	 * Get the first object. This method blocks until an
	 * object becomes available.
	 *
	 * @return the object from the front of the queue
	 */
	public synchronized Object pop()
		throws InterruptedException
	{
	    while (list.size() == 0)
	        wait();
        return list.removeFirst();
	}

    public synchronized boolean update(QueueUpdater qu)
    {
        boolean ret = qu.update(list);
        if (list.size() > 0) // hmmm, should a QU be able to insert stuff?
            this.notify();
        return ret;
    }
    
	/**
	 * This method is not particularly thread safe in the sense
	 * that it could return true and then a call to pop() could
	 * subsequently block if another thread grabs the object first.
	 * This method is useful if only one thread is taking objects
	 * out (for transferring objects between threads, for instance).
	 *
	 * @return true if there is at least one object stored
	 */
	public synchronized boolean isEmpty()
	{
		return list.isEmpty();
	}
}

// end of Queue.java

