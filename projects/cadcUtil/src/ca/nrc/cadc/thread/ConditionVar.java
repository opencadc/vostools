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

/**
 * Simple condition variable implementation. The myWait() method
 * only blocks when the condition is false. The myNotify() method
 * sets the condition to true and notifies one waiter.
 *
 * @version $Revision: 327 $
 * @author $Author: pdowler $
 */
public class ConditionVar extends Object
{
	private boolean cond;
	public ConditionVar()
	{
		super();
		cond = false;
	}

	public synchronized void waitForTrue()
		throws InterruptedException
	{
		if (!cond)
			this.wait(); // release the monitor
	}
    
    public synchronized void waitForTrue(long maxWait)
		throws InterruptedException
	{
		if (!cond)
			this.wait(maxWait); // release the monitor
	}

	public synchronized void setNotify()
	{
		cond = true;
		this.notify();
	}
    
    public synchronized void setNotifyAll()
    {
        cond = true;
        this.notifyAll();
    }
    
	public synchronized void set(boolean val)
	{
		cond = val;
	}
}

// end of ConditionVar.java

