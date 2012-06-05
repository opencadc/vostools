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

import ca.nrc.cadc.io.BulkDataOutput;
import java.io.OutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * This class implements a stream filter for writing compressed data in
 * the H-Compress format. HCompressOutputStream must receive integer data
 * and sends bytes to the output stream. Currently, all of the data to be
 * compressed must be received before output to the underlying stream begins.
 *
 * An HCompressOutputStream writes the HCompress magic number, a few bytes of
 * additional information about the image data and then an unknown
 * number of bytes of compressed data. It does not do any padding or make any
 * other assumptions about the underlying destination. The flush and close methods
 * do chain to the underlying stream: maybe that isn't such a good idea...
 *
 * @see         HCompressInputStream
 * @version     0.2
 * @author      Patrick Dowler
 *
 */
public class HCompressOutputStream implements BulkDataOutput
{
    /**
     * H compress magic number.
     */
    private static final int HCOMPRESS_MAGIC = 0xdd99;

    private boolean waitingForHeader = true;
    private boolean headerWritten = false;

    private int nx = -1;
    private int ny = -1;
    private int scale = 0;
	private int encoded_bytes = 0;
	private int header_bytes = 0;

    private byte[] byte_singleton = new byte[1];
    private short[] short_singleton = new short[1];
    private int[] singleton = new int[1];

    private int[] ibuf;
    private int ibuf_data_len;
    private int ipos;

    private DataOutputStream datastream;
    private OutputStream ostream;

    /**
     * Creates a new output stream to write hcompressed data. If the
     * argument is a DataOutputStream, it is used directly. Otherwise,
     * it is wrapped in a DataOutputStream and used as if the caller used
     * the two-argument constructor.
     *
     * @param stream the underlying output stream
     * @param size the output buffer size
     */
    public HCompressOutputStream(OutputStream stream)
    {
		// we want to use a DataOutputStream
		if ( stream instanceof DataOutputStream )
			this.datastream = (DataOutputStream) stream;
		else
			this.datastream = new DataOutputStream(stream);
		this.ostream = stream;
		waitingForHeader = true;
    }

    /**
     * Creates a new output stream to write hcompressed data. The first
     * argument is used to write the hcompress header and the second argument
     * is used to write the compressed data itself. The first argument must
     * be writing directly to the second with no buffering in between or there
     * will be trouble.
     *
     * @param stream the underlying output stream
     * @param size the output buffer size
     */
    public HCompressOutputStream(DataOutputStream datastream, OutputStream stream)
    {
		this.datastream = datastream;
		this.ostream = stream;
		waitingForHeader = true;
    }


    /**
     * The data source must set the image dimensions and
     * scale factor so HCompressOutputStream knows how to
     * interpret the 1-D array as a 2-D image. The scale factor
     * controls the lossiness of the compression. Values of
     * 0 or 1 give lossless but poor compression. Values around
     * 500 to 1000 give a factor of 5-8 in compression with
     * modest loss of information. A scale of 5000 should compress
     * an image by a factor of 15-20, but with some noticeable
     * blockiness in the decompressed image (smoothing during
     * decompression may restore some of the image quality).
     *
     * @param xd	x dimension (number of rows?)
     * @param yd	y dimension (number of columns?)
     * @param sc	scale
     */
    public void setDimensions(int xd, int yd, int sc)
		throws IOException
    {
		nx = xd;
		ny = yd;
		scale = sc;
		waitingForHeader = false;
		headerWritten = false; // this header is new
    }

	/**
	 * @return number of bytes written to output stream
	 */
	public int getByteCount()
	{
		return encoded_bytes + header_bytes;
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
     * Writes a single integer through the compression filter.
     *
     * @param i the integer to write
     * @exception IOException if an I/O error has occurred or the compressed
     *                        input data is corrupt
     */
    public void writeByte( byte i )
		throws IOException
    {
		byte_singleton[0] = i;
		this.writeByte(byte_singleton,0,1);
    }

    /**
     * Writes integer data through the compression filter. This
     * is equivalent to calling writeInt(buf, 0, buf.length).
     *
     * @param buf the buffer of data to write
     * @return  the actual number of bytes read, or -1 if the end of the
     *          compressed input stream is reached
     * @exception IOException if an I/O error has occurred or the compressed
     *                        input data is corrupt
     */
    public void writeByte(byte[] buf)
		throws IOException
    {
		this.writeByte(buf,0,buf.length);
    }

    /**
     * Writes integer data through the compression filter. If the supplied
     * array (buf) is large enough (i.e. it contains the all the data to
     * be compressed), it will be used directly by the compressor,
     * making the decompression more efficient. Otherwise, the data is
     * copied into a temporary buffer and compression occurs once all the
     * data has been received.
     *
     * @param buf the buffer into which the data is read
     * @param off the start offset of the data
     * @param len the maximum number of bytes to write
     * @exception IOException if an I/O error has occurred or the compressed
     *                        input data is corrupt
     */
    public void writeByte(byte[] buf, int offset, int len)
		throws IOException
    {
		// caller must set the header params first
		if ( waitingForHeader )
		{
	 	   throw new IOException("no image dimensions");
		}
		if ( !headerWritten )
			writeHeader();

		// Get bytes from buf and transfer to cbuf.
		ipos = ibuf_data_len;
		if (ibuf == null)
			ibuf = new int[nx*ny];

		// we have to copy: byte -> int
	    // System.arraycopy(ibuf, ipos, buf, off, len);
	    for (int i=0; i<len; i++)
	    	ibuf[i+ipos] = (int) buf[i+offset];
 		ibuf_data_len += len;

		// check if we are full
		if ( ibuf_data_len == ibuf.length )
		{
	    	// we have all the data: compress ibuf -> out
	    	encoded_bytes = compress();
		}
    }

    /**
     * Writes a single integer through the compression filter.
     *
     * @param i the integer to write
     * @exception IOException if an I/O error has occurred or the compressed
     *                        input data is corrupt
     */
    public void writeShort( short i )
		throws IOException
    {
		short_singleton[0] = i;
		this.writeShort(short_singleton,0,1);
    }

    /**
     * Writes integer data through the compression filter. This
     * is equivalent to calling writeInt(buf, 0, buf.length).
     *
     * @param buf the buffer of data to write
     * @return  the actual number of bytes read, or -1 if the end of the
     *          compressed input stream is reached
     * @exception IOException if an I/O error has occurred or the compressed
     *                        input data is corrupt
     */
    public void writeShort( short[] buf )
		throws IOException
    {
		this.writeShort(buf,0,buf.length);
    }

    /**
     * Writes integer data through the compression filter. If the supplied
     * array (buf) is large enough (i.e. it contains the all the data to
     * be compressed), it will be used directly by the compressor,
     * making the decompression more efficient. Otherwise, the data is
     * copied into a temporary buffer and compression occurs once all the
     * data has been received.
     *
     * @param buf the buffer into which the data is read
     * @param off the start offset of the data
     * @param len the maximum number of bytes to write
     * @exception IOException if an I/O error has occurred or the compressed
     *                        input data is corrupt
     */
    public void writeShort(short[] buf, int offset, int len)
		throws IOException
    {
		// caller must set the header params first
		if ( waitingForHeader )
		{
	 	   throw new IOException("no image dimensions");
		}
		if ( !headerWritten )
			writeHeader();

		// Get bytes from buf and transfer to cbuf.
		ipos = ibuf_data_len;
		if (ibuf == null)
			ibuf = new int[nx*ny];

		// we have to copy: byte -> int
	    // System.arraycopy(ibuf, ipos, buf, off, len);
	    for (int i=0; i<len; i++)
	    	ibuf[i+ipos] = (int) buf[i+offset];
 		ibuf_data_len += len;

		// check if we are full
		if ( ibuf_data_len == ibuf.length )
		{
	    	// we have all the data: compress ibuf -> out
	    	encoded_bytes = compress();
		}
    }

    /**
     * Writes a single integer through the compression filter.
     *
     * @param i the integer to write
     * @exception IOException if an I/O error has occurred or the compressed
     *                        input data is corrupt
     */
    public void writeInt( int i )
		throws IOException
    {
		singleton[0] = i;
		this.writeInt(singleton,0,1);
    }

    /**
     * Writes integer data through the compression filter. This
     * is equivalent to calling writeInt(buf, 0, buf.length).
     *
     * @param buf the buffer of data to write
     * @return  the actual number of bytes read, or -1 if the end of the
     *          compressed input stream is reached
     * @exception IOException if an I/O error has occurred or the compressed
     *                        input data is corrupt
     */
    public void writeInt( int[] buf )
		throws IOException
    {
		this.writeInt(buf,0,buf.length);
    }

    /**
     * Writes integer data through the compression filter. If the supplied
     * array (buf) is large enough (i.e. it contains the all the data to
     * be compressed), it will be used directly by the compressor,
     * making the decompression more efficient. Otherwise, the data is
     * copied into a temporary buffer and compression occurs once all the
     * data has been received.
     *
     * @param buf the buffer into which the data is read
     * @param off the start offset of the data
     * @param len the maximum number of bytes to write
     * @exception IOException if an I/O error has occurred or the compressed
     *                        input data is corrupt
     */
    public void writeInt(int[] buf, int off, int len)
		throws IOException
    {
		// caller must set the header params first
		if ( waitingForHeader )
		{
	 	   throw new IOException("no image dimensions");
		}
		if ( !headerWritten )
			writeHeader();

		// Get bytes from buf and transfer to cbuf.
		ipos = ibuf_data_len;
		if ( len == nx*ny )
		{
		    // buf is the whole image
		    ibuf = buf;
		    ibuf_data_len = len;
		}
		else
		{
			// buf is a chunk, we have to buffer :-(
		    if (ibuf == null)
		    	ibuf = new int[nx*ny];
		    System.arraycopy(ibuf, ipos, buf, off, len);
	 		ibuf_data_len += len;
		}

		// check if we are full
		if ( ibuf_data_len == ibuf.length )
		{
	    	// we have all the data: compress ibuf -> out
	    	encoded_bytes = compress();
		}
    }

    /**
     * Flush any buffers and write data to output stream. Since the
     * whole buffer gets compressed and written as soon as it is
     * received, there should be nothing to do here. This does cause
     * the underlying output stream to be flushed in any case.
     *
     * @exception IOException (from the underlying output stream)
     */
    public void flush()
		throws IOException
    {
		//if ( waitingForHeader )
		//    throw new IOException(
		//	"HCompressOutputStream closing without writing anything");

		// ibuf should be an existing array now
		//if ( ibuf_data_len < ibuf.length )
		//    throw new IOException(
		//	"HCompressOutputStream unexpectedly");

		datastream.flush();
    }

    /**
     * Closes the underlying output stream. The <code>cleanup</code>
     * method is called.
     *
     * @exception IOException (from the underlying output stream)
     */
    public void close()
		throws IOException
    {
		flush();
		datastream.close();
		cleanup();
    }

    /**
     * Informs the caller of marking and seeking is supported. This
     * currently always returns false.
     *
     * @return true if marking is supported, otherwise false
     */
    public boolean markSupported()
    {
		return false;
    }

    /**
     * Writes the header information to the stream.
     */
    private void writeHeader()
		throws IOException
    {
		if ( !headerWritten )
		{
			datastream.writeShort(HCompressUtil.HCOMPRESS_MAGIC);
			datastream.writeInt(nx);
			datastream.writeInt(ny);
			datastream.writeInt(scale);
			header_bytes = 14;

			ibuf = null;
			ibuf_data_len = 0;
			ipos = 0;
			waitingForHeader = false;
			headerWritten = true;
		}
    }

    /**
     * Compresses integer data in cbuf and write bytes to "out".
     *
     * @return number of bytes written to "out"
     */
    private int compress()
		throws IOException
    {
    	//System.out.println("HCompressOutputStream.compress: start");
		HCompressProcessor.htrans( ibuf, nx, ny );
		HCompressProcessor.digitize( ibuf, nx, ny, scale );

		HCompressEncoder hce = new HCompressEncoder();

		// we write the first element because it won't compress anyway
		datastream.writeInt(ibuf[0]);
		ibuf[0] = 0;
		int r = hce.encode( ostream, ibuf, nx, ny );
		//System.out.println("HCompressOutputStream.compress: returning " + r);
		return r;
    }


	// unsupported modes from BulkDataOutput

	/**
	 * Unsupported.
	 */
	public void writeUnsignedByte(short[] buf)
	{
		writeUnsignedByte(buf,0,buf.length);
	}
	/**
	 * Unsupported.
	 */
	public void writeUnsignedByte(short[] buf, int offset, int len)
		throws UnsupportedOperationException
	{
		throw new UnsupportedOperationException();
	}
	/**
	 * Unsupported.
	 */
	public void writeUnsignedShort(int[] buf)
	{
		writeUnsignedShort(buf,0,buf.length);
	}
	/**
	 * Unsupported.
	 */
	public void writeUnsignedShort(int[] buf, int offset, int len)
		throws UnsupportedOperationException
	{
		throw new UnsupportedOperationException();
	}
	/**
	 * Unsupported.
	 */
	public void writeLong(long[] buf)
	{
		writeLong(buf,0,buf.length);
	}
	/**
	 * Unsupported.
	 */
	public void writeLong(long[] buf, int offset, int len)
		throws UnsupportedOperationException
	{
		throw new UnsupportedOperationException();
	}

	/**
	 * Unsupported.
	 */
	public void writeFloat(float[] buf)
	{
		writeFloat(buf,0,buf.length);
	}
	/**
	 * Unsupported.
	 */
	public void writeFloat(float[] buf, int offset, int len)
		throws UnsupportedOperationException
	{
		throw new UnsupportedOperationException();
	}

	/**
	 * Unsupported.
	 */
	public void writeDouble(double[] buf)
	{
		writeDouble(buf,0,buf.length);
	}
	/**
	 * Unsupported.
	 */
	public void writeDouble(double[] buf, int offset, int len)
		throws UnsupportedOperationException
	{
		throw new UnsupportedOperationException();
	}

	/**
	 * Unsupported.
	 */
	public void writeChar(char[] buf)
	{
		writeChar(buf,0,buf.length);
	}
	/**
	 * Unsupported.
	 */
	public void writeChar(char[] buf, int offset, int len)
		throws UnsupportedOperationException
	{
		throw new UnsupportedOperationException();
	}
}
