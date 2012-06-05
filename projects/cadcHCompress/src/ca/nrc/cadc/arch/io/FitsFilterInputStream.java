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

package ca.nrc.cadc.arch.io;

import ca.nrc.cadc.arch.io.hcompress.HCompressInputStream;
import java.util.zip.GZIPInputStream;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.IOException;

/**
 * This class filters a FITS data stream and handles any
 * compression that it finds. It currently supports
 * <em>classic</em> hcompressed FITS (SIMPLE, BITPIX=16 only) and
 * gzipped FITS. It can also read normal FITS, in which case
 * it simply passes the data through unchanged, so it is safe
 * to use this class when the data <em>might</em> be compressed
 * (i.e. when reading the output an URL that has no recognizable
 * "file name" extension).
 *
 * @version $Revision: 1.1 $
 * @author $Author: pdowler $
 */
public class FitsFilterInputStream extends InputStream
{
	private int FITS_BLOCK_SIZE = 2880;
	private InputStream istream;
	private boolean foundEnd = false;
	private boolean eof = false;
	private int avail = 0;
	private int start = 0;

	private byte[] singleton = new byte[1];
	private byte[] buf = new byte[FITS_BLOCK_SIZE];
	private boolean isCompressed = true;
	private int imgPix = 0;
	private int bpp = 16;
	private Object img = null;

	// set this to true to get System.out messages
	private boolean debug = false;

	/**
	 * Create a filter that reads from the underlying
	 * input stream. The buffer size should be set
	 * to 2880 bytes (the FITS block size).
	 */
	public FitsFilterInputStream(BufferedInputStream istream)
		throws IOException
	{
		super();
		this.istream = istream;
		checkGZIP();
	}

	/**
	 * @return the next byte
	 */
	public int read()
		throws IOException
	{
		int c = this.read(singleton,0,1);
		if (c == 1)
			return (int) singleton[0];
		return -1;
	}

	/**
	 * @return the number of bytes read, or -1 if EOF has been reached
	 */
	@Override
    public int read(byte[] b)
		throws IOException
	{
		return this.read(b,0,b.length);
	}

	/**
	 * @return the number of bytes read, or -1 if EOF has been reached
	 */
	@Override
    public int read(byte[] b, int off, int len)
		throws IOException
	{
		if (eof)
			return -1;
		readBlock();
		int count = ( len < avail ? len : avail );
		System.arraycopy(buf,start,b,off,count);
		start += count;
		avail -= count;
		return count;
	}

	// check for the GZIP magic number
	private void checkGZIP()
		throws IOException
	{
		msg("checkGZIP");
		istream.mark(2);
		try
		{
			GZIPInputStream gzin = new GZIPInputStream(istream);
			msg("checkGZIP: success");
			istream = new BufferedInputStream(gzin,2880);
		}
		catch (IOException ioex)
		{
			istream.reset();
			msg("checkGZIP: failure");
		}
	}

	// make sure there are bytes in the buffer
	private void readBlock()
		throws IOException
	{
		//msg("readBlock: avail="+ avail + " start="+start);
		if (avail > 0)
			return;

		start = 0; // avail==0 already

		// stage 1: reading to END
		while ( !foundEnd && avail < FITS_BLOCK_SIZE )
		{
			byte hdrline[] = new byte[80];
    		int n = istream.read(hdrline,0,80);
	    	if ( n != 80 )
				throw new IOException("header line had " + n + " chars");
			System.arraycopy(hdrline,0,buf,avail,n);
			avail += n;
			String s = new String(hdrline,0,80);
			if ( s.startsWith("BITPIX") )
			{
				bpp = Integer.parseInt( s.substring(10,30).trim() );
				msg("readBlock: found BITPIX="+bpp);
			}
			foundEnd = s.startsWith("END");
			//msg("readBlock: (1) avail="+avail);
		}
		if ( avail == FITS_BLOCK_SIZE )
		{
			// read a full block
			//msg("readBlock: avail="+avail + " (done)");
			return;
		}

		if ( img == null && isCompressed )
		{
			// stage 2: check for and read compressed image
			istream.mark(2);
			try
			{
				//msg("readBlock: trying HCompressInputStream");
				HCompressInputStream hcin =
					new HCompressInputStream(istream);
				msg("readBlock: HCompress: success");
				int w =  hcin.getWidth();
				int h =  hcin.getHeight();
				int len = w*h;
				//msg("readBlock: allocating ["+len+"]");
				int c = -1;
				// as far as I know, only 16bpp works
				switch(bpp)
				{
					//case 8:
					//	byte[] bimg = new byte[len];
					//	img = bimg;
					//	c = hcin.readByte(bimg,0,bimg.length);
					//	break;
					case 16:
						short[] simg = new short[len];
						img = simg;
						c = hcin.readShort(simg,0,simg.length);
						break;
					//case 32:
					//	int[] iimg = new int[len];
					//	img = iimg;
					//	c = hcin.readInt(iimg,0,iimg.length);
					//	break;
					default:
						throw new IOException("unsupported pixel format");
				}
				imgPix = 0;

				// pad current block with blank lines
				//msg("readBlock: padding last header block");
				String s = " ";
				byte space[] = s.getBytes();
				while ( avail < FITS_BLOCK_SIZE )
					buf[avail++] = space[0];
				//msg("readBlock: (done)");
				return;
			}
			catch (BadMagicNumberException bm)
			{
				// not HCompressed
				msg("readBlock: HCompresss failed!");
				isCompressed = false;
				istream.reset();
			}
		}

		// transfer image bytes into buffer
		if ( img != null )
		{
			//msg("readBlock: copying image bytes");
			switch(bpp)
			{
				case 8:
					byte[] bi = (byte[]) img;
					while ( avail < FITS_BLOCK_SIZE &&
							imgPix < bi.length )
						buf[avail++] = bi[imgPix++];
					break;
				case 16:
					short[] si = (short[]) img;
					while ( avail < FITS_BLOCK_SIZE &&
							imgPix < si.length )
					{
						short s = si[imgPix++];
						buf[avail++] = (byte) ( (s >>> 8 ) & 0xff );
						buf[avail++] = (byte) ( (s >>> 0 ) & 0xff );
					}
					break;
				case 32:
					int[] ii = (int[]) img;
					while ( avail < FITS_BLOCK_SIZE &&
							imgPix < ii.length )
					{
						int i = ii[imgPix++];
						buf[avail++] = (byte) ( (i >>> 24 ) & 0xff );
						buf[avail++] = (byte) ( (i >>> 16 ) & 0xff );
						buf[avail++] = (byte) ( (i >>> 8 ) & 0xff );
						buf[avail++] = (byte) ( (i >>> 0 ) & 0xff );
					}
					break;
				default:
					throw new IOException("unsupported pixel format");
			}

			if ( avail < FITS_BLOCK_SIZE ) // end of image data
			{
				// pad current block with blank lines
				//msg("readBlock: paddinging last image block");
				String s = " ";
				byte space[] = s.getBytes();
				while ( avail < FITS_BLOCK_SIZE )
					buf[avail++] = space[0];
				eof = true;
			}
			return;
		}

		// try to read lines after END
		//msg("readBlock: reading lines after END");
		while ( avail < FITS_BLOCK_SIZE )
		{
			byte hdrline[] = new byte[80];
    		int n = istream.read(hdrline,0,80);
	    	if ( n != 80 )
				throw new IOException("header line had " + n + " chars");
			System.arraycopy(hdrline,0,buf,avail,n);
			avail += n;
		}
	}

	private void msg(String s)
	{
		if ( debug )
			System.out.println("FitsFilterInputStream."+s);
	}
}

// end of HCompressInputStream.java

