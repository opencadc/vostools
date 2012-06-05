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

package ca.nrc.cadc.io;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A NoisyBufferedInputStream adds progress reporting through
 * listener notification to the BufferedInputStream.
 *
 * The buffer size may be different from the reporting
 * size (number of bytes between notification events).
 * However, notification will occur at most once per chunk,
 * so the reporting size can't effectively be smaller than
 * the buffer size.
 *
 * @version 0.1
 * @author Patrick Dowler
 */
public class NoisyBufferedOutputStream extends BufferedOutputStream implements NoisyStream
{
	private long bytes = 0;
	private int blocks = 0;
	private int reportSize = 4096; // default 4K reporting size
	private List<NoisyStreamListener> listeners = new ArrayList<NoisyStreamListener>();

	/**
	* Constructor. Uses default buffer size of BufferedOutputStream
	* and default reporting block size (4K).
	*/
	public NoisyBufferedOutputStream(OutputStream out)
	{
		super(out);
	}

	/**
	* Constructor. Uses specified buffer size for BufferedOutputStream.
	*/
	public NoisyBufferedOutputStream(OutputStream out, int bufSize)
	{
		super(out, bufSize);
	}

	/**
	 * @return number of bytes read/written by the stream
	 */
	public long getByteCount()
	{
		return reportSize*blocks + bytes;
	}

	/**
	 * Sets the size increment between reporting events. Listeners
	 * are notified if more than numbytes have been written so far.
	 */
	public void setReportSize(int numbytes)
	{
		bytes = getByteCount();
		blocks = 0;
		reportSize = numbytes;
		doit(); // recompute blocks and bytes
	}

	/**
	 * Add a new listener and immediately notifies the new
	 * listener by calling its update method.
	 */
	public void addListener(NoisyStreamListener listener)
	{
		listeners.add(listener);
		listener.update(this);
	}

	/**
	 * Remove the specified listener.
	 */
	public void removeListener(NoisyStreamListener listener)
	{
		listeners.remove(listener);
	}

	/**
	 * Remove all listeners.
	 */
	public void removeListeners()
	{
		listeners.clear();
	}

	/**
	 * Notify all listeners that something changed and they
	 * should check the state of the stream.
	 */
	protected void notifyListeners()
	{
		Iterator i = listeners.iterator();
		while ( i.hasNext() )
		{
			NoisyStreamListener n = (NoisyStreamListener) i.next();
			n.update(this);
		}
	}

	public void write(int b)
		throws IOException
	{
		super.write(b);
		bytes++;
		doit();
	}

	public void write(byte[] b, int off, int len)
		throws IOException
	{
		super.write(b,off,len);
		bytes += Math.min( len, b.length-off);
		doit();
	}

	/**
	 * Closes the output stream and notifies all listeners.
	 */
	public void close()
		throws IOException
	{
		notifyListeners();
		super.close();
	}

	private void doit()
	{
		boolean changed = false;
		while ( bytes >= reportSize )
		{
			long b = bytes/reportSize;
			blocks += (int) b;
			bytes -= b*reportSize;
			changed = true;
		}
		if (changed)
			notifyListeners();
	}
}
