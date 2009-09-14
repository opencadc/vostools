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

