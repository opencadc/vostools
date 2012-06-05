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

import java.io.DataInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.EOFException;

/**
 * This class implements a stream filter for reading from an underlying
 * input stream and converting it to various internal data types. As
 * an extension of DataInputStream, it adds the ability  to read large
 * chunks of data into arrays.
 *
 * All the readXXX methods block until they either fill the
 * supplied buffer or reach end-of-file. Thus, they will return
 * the buffer size on every call except possibly the last
 * successful one, where the read count could be less. A return
 * of 0 means EOF was detected on the first read. This behaviour is
 * usefully different from DataInputStream.readFully because the
 * method returns normally when EOF is first reached instead of
 * throwing an EOFException.
 *
 * @see		DataInputStream
 * @version 0.1
 * @author  Patrick Dowler
 */
public class BulkDataInputStream extends DataInputStream
    implements BulkDataInput
{
    protected boolean eos;

    /**
     * Constructor.
     *
     * @param istream an input stream with readInt() methods.
     */
    public BulkDataInputStream(InputStream is)
    {
		super(is);
		eos = false;
    }

    // read characters
    public int readChar( char[] buf )
		throws IOException, EOFException
    {
		return this.readChar(buf,0,buf.length);
    }

    public int readChar( char[] buf, int off, int len)
		throws IOException, EOFException
    {
		if ( eos )
		    throw new EOFException();

		int i = 0;
		while ( !eos && i < len )
		{
	    	try
	    	{
				// use DataInputStream.readChar()
				buf[i+off] = this.readChar();
				i++;
		    }
		    catch (EOFException e)
	    	{
				eos = true;
	    	}
		}
		return i;
    }

    // read 8-bit signed integers
    public int readByte( byte[] buf )
		throws IOException, EOFException
    {
		return this.readByte(buf,0,buf.length);
    }

    public int readByte( byte[] buf, int off, int len)
		throws IOException, EOFException
    {
		if ( eos )
		    throw new EOFException();

		int i = 0;
		while ( !eos && i < len )
		{
	    	try
	    	{
				// use DataInputStream.readByte()
				buf[i+off] = this.readByte();
				i++;
		    }
		    catch (EOFException e)
	    	{
				eos = true;
	    	}
		}
		return i;
    }

    // read 8-bit unsigned integers
    public int readUnsignedByte( short[] buf )
		throws IOException, EOFException
    {
		return this.readUnsignedByte(buf,0,buf.length);
    }

    public int readUnsignedByte( short[] buf, int off, int len)
		throws IOException, EOFException
    {
		if ( eos )
		    throw new EOFException();

		int i = 0;
		while ( !eos && i < len )
		{
		    try
		    {
				// int <- DataInputStream.readUnsignedByte()
				buf[i+off] = (short) this.readUnsignedByte();
				i++;
		    }
		    catch (EOFException e)
		    {
				eos = true;
		    }
		}
		return i;
    }

    // read 16-bit signed integers
    public int readShort( short[] buf )
		throws IOException, EOFException
    {
		return this.readShort(buf,0,buf.length);
    }

    public int readShort( short[] buf, int off, int len)
		throws IOException, EOFException
	{
		if ( eos )
		    throw new EOFException();

		int i = 0;
		while ( i < len && !eos )
		{
		    try
	    	{
				// use DataInputStream.readShort()
				buf[i+off] = this.readShort();
				//byte b1 = this.readByte();
				//byte b2 = this.readByte();
				//buf[i+off] = (short) ((short)(b1)<<8) | ((short)(b2) & 0xff);
				i++;
		    }
		    catch (EOFException e)
		    {
				eos = true;
		    }
		}
		return i;
    }

    // read 16-bit unsigned integers
    public int readUnsignedShort( int[] buf )
		throws IOException, EOFException
    {
		return this.readUnsignedShort(buf,0,buf.length);
    }

    public int readUnsignedShort( int[] buf, int off, int len)
		throws IOException, EOFException
    {
		if ( eos )
		    throw new EOFException();

		int i = 0;
		while ( !eos && i < len )
		{
		    try
		    {
				// use DataInputStream.readUnsignedShort()
				buf[i+off] = this.readUnsignedShort();
				i++;
		    }
		    catch (EOFException e)
		    {
				eos = true;
		    }
		}
		return i;
    }

    // read 32-bit signed integers
    public int readInt( int[] buf )
		throws IOException, EOFException
    {
		return this.readInt(buf,0,buf.length);
    }

    public int readInt( int[] buf, int off, int len)
		throws IOException, EOFException
    {
		if ( eos )
		    throw new EOFException();

		int i = 0;
		while ( !eos && i < len )
		{
		    try
		    {
				// use DataInputStream.readInt()
				buf[i+off] = this.readInt();
				i++;
		    }
		    catch (EOFException e)
		    {
				eos = true;
		    }
		}
		return i;
    }

    // read 64-bit signed integers
    public int readLong( long[] buf )
		throws IOException, EOFException
    {
		return this.readLong(buf,0,buf.length);
    }

    public int readLong( long[] buf, int off, int len)
		throws IOException, EOFException
    {
		if ( eos )
		    throw new EOFException();

		int i = 0;
		while ( !eos && i < len )
		{
		    try
		    {
				// use DataInputStream.readLong()
				buf[i+off] = this.readLong();
				i++;
		    }
		    catch (EOFException e)
		    {
				eos = true;
		    }
		}
		return i;
    }

    // read 32-bit floating point values
    public int readFloat( float[] buf )
		throws IOException, EOFException
    {
		return this.readFloat(buf,0,buf.length);
    }

    public int readFloat( float[] buf, int off, int len)
		throws IOException, EOFException
    {
		if ( eos )
		    throw new EOFException();

		int i = 0;
		while ( !eos && i < len )
		{
		    try
		    {
				// use DataInputStream.readFloat()
				buf[i+off] = this.readFloat();
				i++;
		    }
		    catch (EOFException e)
		    {
				eos = true;
		    }
		}
		return i;
    }

    // read 64-bit floating point values
    public int readDouble( double[] buf )
		throws IOException, EOFException
    {
		return this.readDouble(buf,0,buf.length);
    }

    public int readDouble( double[] buf, int off, int len)
		throws IOException, EOFException
    {
		if ( eos )
		    throw new EOFException();

		int i = 0;
		while ( !eos && i < len )
		{
		    try
		    {
				// use DataInputStream.readDouble()
				buf[i+off] = this.readDouble();
				i++;
		    }
		    catch (EOFException e)
		    {
				eos = true;
		    }
		}
		return i;
    }
}

