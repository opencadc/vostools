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

package ca.nrc.cadc.arch.io.hcompress;

import ca.nrc.cadc.io.BulkDataInput;
import ca.nrc.cadc.arch.io.BadMagicNumberException;

import java.io.InputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.EOFException;

/**
 * <p>
 * This class implements a stream filter for reading compressed data in
 * the H-Compress format. HCompressInputStream must receive all of the
 * data to be uncompressed before its read() methods will have anything
 * to return (i.e. this is blocking IO in the extreme!). The best way
 * to find out if some data is hcompressed is to try to open an
 * HCompressInputStream and be ready to catch a BadMagicNumberException.
 * </p><p>
 * An HCompressInputStream reads the HCompress magic number, a few bytes of
 * additional information about the image data and then an unknown
 * number of bytes of compressed data. It does not consume any padding or make any
 * other assumptions about the underlying destination. The close method
 * chains to the underlying stream: maybe that isn't such a good idea...
 * </p><p>
 * The current version is limited to decompressing 16-bit integer data.
 * The <code>readShort</code> methods will correctly copy the data
 * into the supplied <code>short[]</code> but needs to use an
 * <code>int[]</code> internally. Thus, using <code>readInt</code>
 * and supplying an <code>int[]</code> uses less memory in total.
 * <p>
 * @see         HCompressOutputStream
 * @version     0.2
 * @author      Patrick Dowler
 */

public class HCompressInputStream implements BulkDataInput
{
    /**
     * Indicates end of input stream.
     */
    private boolean eos;

    /**
     * Indicates that the decompressor should smooth the image.
     */
    private int smooth = 0;

    /**
     * private data from the hcomp header.
     */
    private boolean waitingForHeader;
    private boolean use_direct;
    private boolean first_read;

    private int nx = -1;
    private int ny = -1;
    private int scale = 0;
    private int num_bytes = 0;
    private int header_bytes = 0;

    private int ibuf_data_len;
    private int ipos;
    private byte[] byte_singleton = new byte[1];
    private short[] short_singleton = new short[1];
    private int[] singleton = new int[1];
    private int[] ibuf;

	private InputStream istream;
    private DataInputStream datastream;

    /**
     * Creates a new input stream which can read hcompressed data. This
     * class uses a DataInputStream to read the preliminary hcompress
     * data (header fields). If the argument is a DataInputStream, that
     * will be used for all of the input. However, the decompression
     * only reads bytes so any input stream is sufficient for the bulk
     * of the input. As such, it is probably more efficient to pass
     * in an underlying input stream or, if the caller also uses a
     * DataInputStream, to use the two-argument constructor instead. If the
     * argument stream is not a DataInputStream, it is wrapped in a
     * DataInputStream and used as if the caller used the two-argument
     * constructor.
     *
     * @param stream the underlying input stream
     * @exception IOException if an I/O error has occurred
     * @exception BadMagicNumberException if the first two bytes
     * read are not the HCompress magic number
     */
    public HCompressInputStream(InputStream stream)
		throws BadMagicNumberException, IOException
    {
		if ( stream instanceof DataInputStream )
		{
	    	datastream = (DataInputStream) stream;
	    	istream = stream;
		}
		else
		{
	    	datastream = new DataInputStream(stream);
	    	istream = stream;
		}
		init();
    }

    /**
     * Creates a new input stream with the default buffer size. This
     * class uses a DataInputStream to write the preliminary hcompress
     * data (header fields). This constructor offers a more efficient
     * input mechanism because it allows this class to use the DataInputStream
     * to read the hcompress header and then to use the underlying stream
     * to read compressed data. The data stream must be reading from the same
     * underlying stream (the second arg) without any buffering in between or there
     * will be trouble.
     *
     * @param datastream input stream
     * @param istream the underlying input stream
     * @exception IOException if an I/O error has occurred
     * @exception BadMagicNumberException if the first two bytes
     * read are not the HCompress magic number
     */
	public HCompressInputStream(DataInputStream datastream, InputStream istream)
		throws BadMagicNumberException, IOException
	{
		this.datastream = datastream;
		this.istream = istream;
		init();
	}

	private void init()
		throws BadMagicNumberException, IOException
	{
		waitingForHeader = true;
		first_read = true;
		readHeader();
	}

	/**
	 * Clean up internal buffers (the large int[]).
	 */
	public void cleanup()
	{
		ibuf = null;
		System.gc();
	}

	/**
	 * @return the width (x-dimension) of the image
	 */
	public int getWidth()
	{
		return nx;
	}
	/**
	 * @return the height (y-dimension) of the image
	 */
	public int getHeight()
	{
		return ny;
	}
	/**
	 * @return the compression scale (read from the hcompress header)
	 */
	public int getScale()
	{
		return scale;
	}

    /**
     * Enables or disables smoothing during decompression.
     *
     * @param enabled enables (disables) smoothing
     */
    public void setSmoothing(boolean enabled)
    {
		if ( enabled )
			smooth = 1;
		else
	    	smooth = 0;
    }

    /**
     * @return total number of bytes read from the underlying stream
     */
    public int getByteCount()
    {
    	return header_bytes + num_bytes;
    }

    /**
     * Reads the next 8-bit integer of uncompressed data. Blocks until enough
     * input is available for decompression.
     *
     * @return  the next integer in the stream
     * @exception IOException if an I/O error has occurred or the
     *                        input data is corrupt
     * @exception EOFException if the end of stream has been reached
     */
    public byte readByte()
		throws EOFException, IOException
    {
		int i = this.readByte(byte_singleton,0,1);
		if ( i == -1 )
	    	throw new EOFException();
		return byte_singleton[0];
    }

    /**
     * Reads uncompressed data into an array of bytes. Blocks until enough
     * input is available for decompression.
     *
     * @param buf the buffer into which the data is read
     * @return  the actual number of integers read, or -1 if the end of the
     *          input stream is reached
     * @exception IOException if an I/O error has occurred or the compressed
     *                        input data is corrupt
     */
    public int readByte( byte[] buf )
		throws IOException, EOFException
    {
		return this.readByte(buf, 0, buf.length );
    }

    /**
     * Reads uncompressed data into an array of bytes. Blocks until enough
     * input is available for decompression.
     *
     * @param buf the buffer into which the data is read
     * @param off the start offset of the data
     * @param len the maximum number of bytes read
     * @return  the actual number of integers read, or -1 if the end of the
     *          input stream is reached
     * @exception IOException if an I/O error has occurred or the compressed
     *                        input data is corrupt
     */
    public int readByte(byte buf[], int off, int len)
		throws IOException, EOFException
    {
		if (waitingForHeader )
	    	throw new IOException(
				"HCompressInputStream.setDimensions must be called before reading");

		if (eos)
	   		return -1;

		// On the first call to read(), we need to decompress ALL the data
		// from the input stream
		// - the call to decompress doesn't return until we have
		//   everything

		if ( first_read )
		{
	    	//if ( buf.length == nx*ny && ibuf == null )
	    	//{
			//	// buf is large enough to use directly
			//	ibuf = buf;
			//	use_direct = true;
	    	//}
	    	//else
	    	//{
				ibuf = new int[nx*ny];
				use_direct = false;
	    	//}
	    	ibuf_data_len = decompress();
	    	first_read = false;
		}
		if ( use_direct )
		{
	    	eos = true;
	    	return ibuf_data_len;
		}

		int n = len;
		// if we have less than n ints left, copy the remaining ones
		if ( ipos+len > ibuf.length )
		{
	    	n = ibuf.length-ipos;
		}


		// return a chunk of the uncompressed data
		if ( !use_direct)
		{
			// since we are going from int[] -> byte[], we need to assign
	    	//System.arraycopy(ibuf,ipos,buf,off,n);
	    	for (int i=0; i<n; i++)
	    		buf[i+off] = (byte) ibuf[i+ipos];
	    	ipos += n;
		}
		if ( ipos == ibuf.length )
	    	eos = true;
		return n;
    }

    /**
     * Reads the next 16-bit integer of uncompressed data. Blocks until enough
     * input is available for decompression.
     *
     * @return  the next integer in the stream
     * @exception IOException if an I/O error has occurred or the
     *                        input data is corrupt
     * @exception EOFException if the end of stream has been reached
     */
    public short readShort()
		throws EOFException, IOException
    {
		int i = this.readShort(short_singleton,0,1);
		if ( i == -1 )
	    	throw new EOFException();
		return short_singleton[0];
    }

    /**
     * Reads uncompressed data into an array of shorts. Blocks until enough
     * input is available for decompression.
     *
     * @param buf the buffer into which the data is read
     * @return  the actual number of integers read, or -1 if the end of the
     *          input stream is reached
     * @exception IOException if an I/O error has occurred or the compressed
     *                        input data is corrupt
     */
    public int readShort( short[] buf )
		throws IOException, EOFException
    {
		return this.readShort(buf, 0, buf.length );
    }

    /**
     * Reads uncompressed data into an array of shorts. Blocks until enough
     * input is available for decompression.
     *
     * @param buf the buffer into which the data is read
     * @param off the start offset of the data
     * @param len the maximum number of bytes read
     * @return  the actual number of integers read, or -1 if the end of the
     *          input stream is reached
     * @exception IOException if an I/O error has occurred or the compressed
     *                        input data is corrupt
     */
    public int readShort(short[] buf, int off, int len)
		throws IOException, EOFException
    {
		if (waitingForHeader )
	    	throw new IOException(
				"HCompressInputStream.setDimensions must be called before reading");

		if (eos)
	   		return -1;

		// On the first call to read(), we need to decompress ALL the data
		// from the input stream
		// - the call to decompress doesn't return until we have
		//   everything

		if ( first_read )
		{
	    	//if ( buf.length == nx*ny && ibuf == null )
	    	//{
			//	// buf is large enough to use directly
			//	ibuf = buf;
			//	use_direct = true;
	    	//}
	    	//else
	    	//{
				ibuf = new int[nx*ny];
				use_direct = false;
	    	//}
	    	ibuf_data_len = decompress();
	    	first_read = false;
		}
		if ( use_direct )
		{
	    	eos = true;
	    	return ibuf_data_len;
		}

		int n = len;
		// if we have less than n ints left, copy the remaining ones
		if ( ipos+len > ibuf.length )
		{
	    	n = ibuf.length-ipos;
		}


		// return a chunk of the uncompressed data
		if ( !use_direct)
		{
			// since we are going from int[] -> byte[], we need to assign
	    	//System.arraycopy(ibuf,ipos,buf,off,n);
	    	for (int i=0; i<n; i++)
	    		buf[i+off] = (short) ibuf[i+ipos];
	    	ipos += n;
		}
		if ( ipos == ibuf.length )
	    	eos = true;
		return n;
    }

    /**
     * Reads the next integer of uncompressed data. Blocks until enough
     * input is available for decompression.
     *
     * @return  the next integer in the stream
     * @exception IOException if an I/O error has occurred or the
     *                        input data is corrupt
     * @exception EOFException if the end of stream has been reached
     */
    public int readInt()
		throws EOFException, IOException
    {
		int i = this.readInt(singleton,0,1);
		if ( i == -1 )
	    	throw new EOFException();
		return singleton[0];
    }

    /**
     * Reads uncompressed data into an array of ints. Blocks until enough
     * input is available for decompression. This is equivalent to
     * readInt(int[] buf, int start, int len) with start == 0 and
     * len = buf.length.
     *
     * @param buf the buffer into which the data is read
     * @return  the actual number of integers read, or -1 if the end of the
     *          input stream is reached
     * @exception IOException if an I/O error has occurred or the compressed
     *                        input data is corrupt
     */
    public int readInt( int[] buf )
		throws IOException, EOFException
    {
		return this.readInt(buf, 0, buf.length );
    }

    /**
     * Reads uncompressed data into an array of ints. Blocks until enough
     * input is available for decompression. If the supplied array (buf)
     * is large enough to hold all of the uncompressed data, this array
     * is used directly by the decompressor, making the decompression
     * more efficient. Otherwise, the data is decompressed into a temporary
     * buffer and copied into the supplied array in chunks.
     *
     * @param buf the buffer into which the data is read
     * @param off the start offset of the data
     * @param len the maximum number of bytes read
     * @return  the actual number of integers read, or -1 if the end of the
     *          input stream is reached
     * @exception IOException if an I/O error has occurred or the compressed
     *                        input data is corrupt
     */
    public int readInt(int buf[], int off, int len)
		throws IOException, EOFException
    {
		if (waitingForHeader )
	    	throw new IOException(
				"HCompressInputStream.setDimensions must be called before reading");

		if (eos)
	   		return -1;

		// On the first call to read(), we need to decompress ALL the data
		// from the input stream
		// - the call to decompress doesn't return until we have
		//   everything

		if ( first_read )
		{
	    	if ( buf.length == nx*ny && ibuf == null )
	    	{
				// buf is large enough to use directly
				ibuf = buf;
				use_direct = true;
	    	}
	    	else
	    	{
				ibuf = new int[nx*ny];
				use_direct = false;
	    	}
	    	ibuf_data_len = decompress();
	    	first_read = false; // reported by Markus Dolensky
		}
		if ( use_direct )
		{
	    	eos = true;
	    	return ibuf_data_len;
		}

		int n = len;
		// if we have less than n ints left, copy the remaining ones
		if ( ipos+len > ibuf.length )
		{
	    	n = ibuf.length-ipos;
		}


		// return a chunk of the uncompressed data
		if ( !use_direct)
		{
	    	System.arraycopy(ibuf,ipos,buf,off,n);
	    	ipos += n;
		}
		if ( ipos == ibuf.length )
	    	eos = true;
		return n;
    }

    /**
     * Closes the underlying input stream. The <code>cleanup</code>
     * method is called.
     *
     * @exception IOException if an I/O error has occurred
     */
    public void close()
		throws IOException, EOFException
    {
		datastream.close();
		eos = true;
		cleanup();
    }

    /**
     * Reads the header information from the stream.
     */
    private void readHeader()
		throws BadMagicNumberException, IOException, EOFException
    {
		int magic = datastream.readUnsignedShort();

		if ( !HCompressUtil.isMagic(magic) )
            throw new BadMagicNumberException();

		nx = datastream.readInt();
		ny = datastream.readInt();
		scale = datastream.readInt();
		header_bytes = 14;

		// we need to store nx*nx integers
		ibuf = null;
		ibuf_data_len = 0;
		ipos = 0;

		// ready to start reading compressed data
		waitingForHeader = false;
		eos = false;
    }

    /**
     * Uncompresses data from "in" until end of stream is reached,
     * putting the resulting bytes into ibuf.
     *
     * @return number of integers read from the input stream
     */
    private int decompress()
		throws IOException, EOFException
    {
		HCompressDecoder hcd = new HCompressDecoder();

		// we read the first int here so we can pass the
		// underlying input stream to the decoder
		int sumall = datastream.readInt();
		num_bytes = hcd.decode( istream, ibuf, nx, ny, sumall );

		HCompressProcessor.undigitize( ibuf, nx, ny, scale );
		HCompressProcessor.hinv( ibuf, nx, ny, smooth, scale );

		// decode returns the number of bytes
		return nx*ny;
    }

	// unsupported modes from BulkDataInput

	/**
	 * Unsupported.
	 */
	public int readUnsignedByte(short[] buf)
	{
		return readUnsignedByte(buf,0,buf.length);
	}
	/**
	 * Unsupported.
	 */
	public int readUnsignedByte(short[] buf, int offset, int len)
		throws UnsupportedOperationException
	{
		throw new UnsupportedOperationException();
	}
	/**
	 * Unsupported.
	 */
	public int readUnsignedShort(int[] buf)
	{
		return readUnsignedShort(buf,0,buf.length);
	}
	/**
	 * Unsupported.
	 */
	public int readUnsignedShort(int[] buf, int offset, int len)
		throws UnsupportedOperationException
	{
		throw new UnsupportedOperationException();
	}
	/**
	 * Unsupported.
	 */
	public int readLong(long[] buf)
	{
		return readLong(buf,0,buf.length);
	}
	/**
	 * Unsupported.
	 */
	public int readLong(long[] buf, int offset, int len)
		throws UnsupportedOperationException
	{
		throw new UnsupportedOperationException();
	}

	/**
	 * Unsupported.
	 */
	public int readFloat(float[] buf)
	{
		return readFloat(buf,0,buf.length);
	}
	/**
	 * Unsupported.
	 */
	public int readFloat(float[] buf, int offset, int len)
		throws UnsupportedOperationException
	{
		throw new UnsupportedOperationException();
	}

	/**
	 * Unsupported.
	 */
	public int readDouble(double[] buf)
	{
		return readDouble(buf,0,buf.length);
	}
	public int readDouble(double[] buf, int offset, int len)
		throws UnsupportedOperationException
	{
		throw new UnsupportedOperationException();
	}

	/**
	 * Unsupported.
	 */
	public int readChar(char[] buf)
	{
		return readChar(buf,0,buf.length);
	}
	/**
	 * Unsupported.
	 */
	public int readChar(char[] buf, int offset, int len)
		throws UnsupportedOperationException
	{
		throw new UnsupportedOperationException();
	}
}
