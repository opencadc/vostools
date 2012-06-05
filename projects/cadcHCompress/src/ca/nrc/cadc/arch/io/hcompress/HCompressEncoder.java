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

import java.io.OutputStream;
import java.io.IOException;

/**
 * This class implements the H-compress compression technique.
 *
 * For error handling, the public classes throw exceptions but the
 * private classes return integer success/error codes as the the C
 * version of the press library. This helps to keep the code as
 * similar as possible.
 *
 * @version	    0.1
 * @author      Patrick Dowler
 */

final class HCompressEncoder
{
    public HCompressEncoder()
    {
		error_msg = new StringBuffer();
    }
    private StringBuffer error_msg;
    private int PR_SUCCESS = 0;
    private int PR_E_IO = -1;

    /**
     * Encode values in a[] (the H-transform) and write them to out.
     *
     * @return number of bytes written to out
     */

    public int encode(OutputStream out, int a[], int nx, int ny )
	throws IOException
    {
		int 	nel;
		int		nx2;
		int		ny2;
		int 	i;
		int		j;
		int		k;
		int		q;
		int		vmax[] = new int[3];
		int		nsign;
		int		bits_to_go;
		byte	nbitplanes[] = new byte[3];

		nel = nx * ny;


		//write first value of A (sum of all pixels -- the only value
		//which does not compress well)
		// NOTE: This is now done by HCompressOutputStream.compress()
		//out.writeInt(a[0]);
		//a[0] = 0;

		// allocate array for sign bits and save values, 8 per byte
		byte signbits[] = new byte[ (nel + 7) / 8 ];
		nsign = 0;
		bits_to_go = 8;
		signbits[0] = 0;
		for (i = 0; i < nel; i++)
		{
		    if (a[i] > 0)
	    	{
	    		// positive element, put zero at end of buffer
				signbits[nsign] <<= 1;
				bits_to_go -= 1;
		    }
		    else if (a[i] < 0)
	    	{
		 		// negative element, shift in a one
				signbits[nsign] <<= 1;
				signbits[nsign] |= 1;
				bits_to_go -= 1;
				// replace a by absolute value
				a[i] = -a[i];
		    }
		    if (bits_to_go == 0)
	    	{
				 // filled up this byte, go to the next one
				bits_to_go = 8;
				nsign += 1;
				signbits[nsign] = 0;
	    	}
		}

		if (bits_to_go != 8)
		{
	    	// some bits in last element move bits in last byte to bottom
			// and increment nsign
		    signbits[nsign] <<= bits_to_go;
		    nsign += 1;
		}

		//calculate number of bit planes for 3 quadrants
		//quadrant 0=bottom left, 1=bottom right or top left, 2=top right,
		for (q = 0; q < 3; q++)
		{
	   		vmax[q] = 0;
		}

		//get maximum absolute value in each quadrant
		nx2 = (nx + 1) / 2;
		ny2 = (ny + 1) / 2;
		j = 0;			/* column counter	 */
		k = 0;			/* row counter		 */
		for (i = 0; i < nel; i++)
		{
		    if ( (j >= ny2) && (k >= nx2) )
				q = 2;
		    else if ( (j >= ny2) || (k >= nx2) )
				q = 1;
		    else
				q = 0;

		    if (vmax[q] < a[i])
				vmax[q] = a[i];
		    if (++j >= ny)
		    {
				j = 0;
				k += 1;
		    }
		}

		// now calculate number of bits for each quadrant
		for (q = 0; q < 3; q++)
		{
	    	Integer bpp = new Integer( (int) ( Math.log( (vmax[q] + 1)) /
		    	Math.log(2.0) + 0.5 ) );
		    nbitplanes[q] = bpp.byteValue();
		    if ((vmax[q] + 1) > (1 << nbitplanes[q]))
	    	{
				nbitplanes[q] += 1;
		    }
		}
		// write nbitplanes
		out.write( nbitplanes );

	 	// write coded array
		int encode_status = doencode( out, a, nx, ny, nbitplanes );
		if ( encode_status != PR_SUCCESS )
		{
	    	throw new IOException("hcompress encoding failed");
		}

		// write sign bits
		if (nsign > 0)
		{
	    	// write nsign bytes from the signbits array
		    out.write( signbits, 0, nsign );
		}

		// return number of bytes:
		// 4: a[0] is an int
		// 3: nbits is a byte[3]
		// bitcount/8: huffman encoded image
		// nsign: signbits array
		return 4 + 3 + bitcount/8 + nsign;
    }

    /*
     * Huffman code values and number of bits in each code
     */

    private int code[] =
    {
		0x3e, 0x00, 0x01, 0x08, 0x02, 0x09, 0x1a, 0x1b,
		0x03, 0x1c, 0x0a, 0x1d, 0x0b, 0x1e, 0x3f, 0x0c
    };
    private int ncode[] =
    {
		6, 3, 3, 4, 3, 4, 5, 5,
		3, 5, 4, 5, 4, 5, 6, 4
    };

    private int output_nybble(OutputStream out, int c)
    {
	return output_nbits(out,c,4);
    }
    private int output_huffman(OutputStream out, int c)
    {
	return output_nbits(out,code[c],ncode[c]);
    }


    private int      bitcount;
    private int      bo_buffer;      /* The bit buffer.              */
    private int      bo_bits_to_go;
    private int      qt_bitbuffer;   /* Buffer for qtree_encode.     */
    private int      qt_bits_to_go;

    /*+
     ************************************************************************
     *
     *   Synopsis:
     *	 int	bufcopy( a, n, buffer, b, bmax )
     *
     *   Purpose:
     *	copy non-zero codes from array to buffer
     *
     *   Parameters:
     *	byte	a[]		: (in)	array to copy from.
     *	int	n		: (in)	size of array a.
     *	byte	buffer		: (in)	Output buffer.
     *	int	b		: (out)	Num. bytes in buffer.
     *	int	bmax		: (in)	Size of buffer.
     *
     *   Values Returned:
     *	int	1		: Buffer is full.
     *	int	0		: Buffer is not full.
     *
     *   References:
     *	Copied from hcompress fitsread function.
     *	Programmer: R. White         Date: 15 May 1991
     *
     ************************************************************************
     -*/

    // changed argument int *b to MutableInt so the value could be altered and
    // returned
    private int bufcopy(byte a[], int n, byte buffer[], MutableInt mb,int bmax)
    {
	int b = mb.intValue();
	int         i;

	for (i = 0; i < n; i++)
	{
	    if (a[i] != 0)
	    {

		/*
		 * add Huffman code for a[i] to buffer
		 */
		qt_bitbuffer |= code[a[i]] << qt_bits_to_go;
		qt_bits_to_go += ncode[a[i]];
		if (qt_bits_to_go >= 8)
		{
		    // added dodgy cast - pdd
		    buffer[b] = (byte) (qt_bitbuffer & 0xFF);
		    b += 1;

		    /*
		     * return warning code if we fill buffer
		     */
		    if (b >= bmax)
		    {
			mb.setValue(b);
			return (1);
		    }
		    qt_bitbuffer >>= 8;
		    qt_bits_to_go -= 8;
		}
	    }
	}
	mb.setValue(b);
	return (0);
    }

    /*+
     ************************************************************************
     *
     *   Synopsis:
     *	 int	doencode( char_out, a, nx, ny, nbitplanes )
     *
     *   Purpose:
     *	Encode 2-D array and write stream of characters to outfile.
     *
     *   Parameters:
     *	int	(*char_out)()	: (in)	Function to write data to output.
     *	int	a[]		: (in)	Image to encode.
     *	int	nx		: (in)	X dimension of the image.
     *	int	ny		: (in)	Y dimension of the image.
     *	byte	nbitplanes	: (in)	Number of bit planes in quadrants.
     *
     *   Values Returned:
     *	int	PR_SUCCESS	: Normal completion.
     *	int	PR_E_IO		: Error durring io.
     *	int	PR_E_MEMORY	: Memory allocation failure.
     *
     *   References:
     *	Copied from hcompress fitsread function.
     *	Programmer: R. White         Date: 24 April 1992
     *
     ************************************************************************
     -*/

	int doencode(OutputStream out, int[] a, int nx, int ny, byte[] nbitplanes)
    {
		int		nx2;
		int		ny2;

		nx2 = (nx+1)/2;
		ny2 = (ny+1)/2;

		// Initialize bit output
		start_outputing_bits();


	 	// write out the bit planes for each quadrant
		int status = PR_SUCCESS;

		// changed &a[offset] to a, offset in arg list

		status = qtree_encode(out,a,0,ny,nx2,ny2,nbitplanes[0] );
		if ( status != PR_SUCCESS )
	   		return status;

		status = qtree_encode(out,a,ny2,ny,nx2,ny/2,nbitplanes[1] );
		if ( status != PR_SUCCESS )
	    	return status;

		status = qtree_encode(out,a,ny*nx2,ny,nx/2,ny2,nbitplanes[1]);
		if ( status != PR_SUCCESS )
	    	return status;

		status = qtree_encode(out,a,ny*nx2+ny2,ny,nx/2,ny/2,nbitplanes[2]);
		if ( status != PR_SUCCESS )
	    	return status;

		// Add zero as an EOF symbol
		output_nybble( out, 0 );
		done_outputing_bits( out );

		return status;
    }

    /*+
     ************************************************************************
     *
     *   Synopsis:
     *	 int	done_outputing_bits( char_out )
     *
     *   Purpose:
     *	Flush the last bits in the bit buffer.
     *
     *   Parameters:
     *	int	(*char_out)()	: (in)	Function to write data to the output.
     *
     *   Values Returned:
     *	int	PR_SUCCESS	: Normal completion
     *	int	PR_E_IO		: Error during io.
     *	int	PR_E_MEMORY	: Memory allocation failure.
     *
     *   References:
     *	Copied from hcompress done_outputing_bits function.
     *	Programmer: R. White
     *
     ************************************************************************
     -*/

	int done_outputing_bits(OutputStream out)
    {
		byte	b;

		if ( bo_bits_to_go < 8 )
		{
	    	// added cast - pdd
		    b = (byte) ( bo_buffer<<bo_bits_to_go );
		    try
		    {
				out.write( b );
		    }
		    catch (IOException e)
	    	{
				return PR_E_IO;
		    }
		    // count the garbage bits too
	    	bitcount += bo_bits_to_go;
		}
		return( PR_SUCCESS );
    }


    /*+
     ************************************************************************
     *
     *   Synopsis:
     *	 int	output_nbits( char_out, bits, n )
     *
     *   Purpose:
     *	Writes n bits to the output.
     *
     *   Parameters:
     *	int	(*char_out)()	: (in)	Function to write bytes to output.
     *	int	bits		: (in)	Int containing bits to write.
     *	int	n		: (in)	Number of bits to write.
     *
     *   Values Returned:
     *	int	PR_SUCCESS	: Normal completion.
     *	int	PR_E_IO		: Error durring io.
     *	int	PR_E_MEMORY	: Memory allocation failure.
     *
     *   References:
     *	Copied from hcompress output_nbites function.
     *	Programmer: R. White
     *
     ************************************************************************
     -*/

    int output_nbits(OutputStream char_out, int bits, int n)
    {
		byte	b;

		// Insert bits at the end of the buffer.
		bo_buffer <<= n;
		bo_buffer |= ( bits & ( ( 1 << n ) - 1 ) );
		bo_bits_to_go -= n;
		if ( bo_bits_to_go <= 0 )
		{
		    // The buffer is full, write the top 8 bits.
	    	// added dodgy cast - pdd
		    b = (byte) ( (bo_buffer >> (-bo_bits_to_go)) & 0xff );
		    try
	    	{
				char_out.write( b );
		    }
		    catch (IOException e)
	    	{
				return PR_E_IO;
		    }
		    bo_bits_to_go += 8;
		}
		bitcount += n;
		return( PR_SUCCESS );
    }

    /*+
     ************************************************************************
     *
     *   Synopsis:
     *	 int	qtree_encode( char_out, a, n, nqx, nqy, nbitplanes )
     *
     *   Purpose:
     *	Encode values in quadrant of 2-D array using binary quadtree coding
     *	for each bit plane.  Assumes array is positive.
     *
     *   Parameters:
     *	int	(*char_out)()	: (in)	Function to write data to output.
     *	int	a[]		: (in)	Array to output.
     *	int	n		: (in)	Plysical dimension of rows in a.
     *	int	nqx		: (in)	Length of row.
     *	int	nqy		: (in)	Length of column.
     *	int	nbitplanes	: (in)	Number of bit planes to output.
     *
     *   Values Returned:
     *	int	PR_SUCCESS	: Normal completion.
     *	int	PR_E_IO		: Error during io.
     *	int	PR_E_MEMORY	: Memory allocation failure.
     *
     *   References:
     *	Copied from hcompress get_raw function.
     *	Programmer: R. White         Date: 15 May 1991
     *
     ************************************************************************
     -*/

    // added offset to arg list: wherever a gets indexed, we need to add off
    int qtree_encode(OutputStream char_out, int a[], int off, int n,
	    int nqx, int nqy, int nbitplanes)
    {
	int	log2n;
	int	i;
	int	k;
	int	bit;
	int	bmax;
	int	nqmax;
	int	nqx2;
	int	nqy2;
	int	nx;
	int	ny;
	byte	scratch[];
	byte	buffer[];

	MutableInt bInt = new MutableInt(0);
	int status;

	// this replaces the goto statements!
	boolean bitplane_done = false;


	/*
	 * log2n is log2 of max(nqx,nqy) rounded up to next power of 2
	 */

	nqmax = (nqx > nqy) ? nqx : nqy;
	log2n = (int) ( Math.log(nqmax)/Math.log(2.0) + 0.5 );
	if (nqmax > (1 << log2n))
	{
	    log2n += 1;
	}


	/*
	 * initialize buffer point, max buffer size
	 */

	nqx2 = (nqx + 1) / 2;
	nqy2 = (nqy + 1) / 2;
	bmax = (nqx2 * nqy2 + 1) / 2;


	/*
	 * We're indexing A as a 2-D array with dimensions (nqx,nqy).
	 * Scratch is 2-D with dimensions (nqx/2,nqy/2) rounded up. Buffer
	 * is used to store string of codes for output.
	 */

	scratch = new byte[ 2 * bmax ];
	buffer = new byte[ bmax ];


	/*
	 * now encode each bit plane, starting with the top
	 */

	for (bit = nbitplanes - 1; bit >= 0; bit--)
	{
	    /*
	     * initial bit buffer
	     */

	    bInt.setValue(0);
	    qt_bitbuffer = 0;
	    qt_bits_to_go = 0;

	    bitplane_done = false;

	    /*
	     * on first pass copy A to scratch array
	     */
	    qtree_onebit(a, off, n, nqx, nqy, scratch, bit);
	    nx = (nqx + 1) >> 1;
	    ny = (nqy + 1) >> 1;

	    /*
	     * copy non-zero values to output buffer, which will be written
	     * in reverse order
	     */
	    status = bufcopy(scratch, nx * ny, buffer, bInt, bmax);
	    if ( status == 1 )
	    {
		/*
		 * quadtree is expanding data, change warning code and just
		 * fill buffer with bit-map
		 */

		status = write_bdirect( char_out, a, off, n, nqx, nqy,
			scratch, bit );
		if (status != PR_SUCCESS)
		    return status;
		continue;
	    }

	    /*
	     * do log2n reductions
	     */
	    for (k = 1; k < log2n && !bitplane_done; k++)
	    {
		qtree_reduce(scratch, ny, nx, ny, scratch);
		nx = (nx + 1) >> 1;
		ny = (ny + 1) >> 1;

		status = bufcopy(scratch, nx * ny, buffer, bInt, bmax);
		if ( status == 1 )
		{
		    status = write_bdirect( char_out, a, off,
			    n, nqx, nqy, scratch, bit );
		    if (status != PR_SUCCESS)
			return status;
		    bitplane_done = true;
		}
	    }
	    if (bitplane_done)
		continue;

	    /*
	     * OK, we've got the code in buffer Write quadtree warning
	     * code, then write buffer in reverse order
	     */

	    status = output_nybble(char_out, 0xF);
	    if ( status != PR_SUCCESS )
		return status;
	    if (bInt.intValue() == 0)
	    {
		if (qt_bits_to_go > 0)
		{

		    /*
		     * put out the last few bits
		     */
		    status = output_nbits( char_out,
			    qt_bitbuffer & ((1 << qt_bits_to_go) - 1),
			    qt_bits_to_go );
		    if (status != PR_SUCCESS)
			return status;
		}
		else
		{

		    /*
		     * have to write a zero nybble if there are no 1's in
		     * array
		     */
		    status = output_huffman(char_out, 0);
		    if (status != PR_SUCCESS)
			return status;
		}
	    }
	    else
	    {
		if (qt_bits_to_go > 0)
		{

		    /*
		     * put out the last few bits
		     */
		    status = output_nbits(char_out,
			    qt_bitbuffer & ((1 << qt_bits_to_go) - 1),
			    qt_bits_to_go );
		    if (status != PR_SUCCESS)
			return status;
		}
		for (i = bInt.intValue() - 1; i >= 0; i--)
		{
		    status = output_nbits( char_out, buffer[i], 8 );
		    if (status != PR_SUCCESS)
			return status;
		}
	    }
	} // for each bitplane

	return( PR_SUCCESS );
    }

    /*+
     ************************************************************************
     *
     *   Synopsis:
     *	 void	qtree_onebit( a, n, nx, ny, b, bit )
     *
     *   Purpose:
     *	Do first quadtree reduction step on bit BIT of array A.
     *	Results put into b.
     *
     *   Parameters:
     *	int	a[]		:
     *	int	n		:
     *	int	nx		:
     *	int	ny		:
     *	byte	b		:
     *	int	bit		:
     *
     *   Values Returned:
     *	void
     *
     *   References:
     *	Copied from hcompress write_bdirect function.
     *	Programmer: R. White         Date: 15 May 1991
     *
     ************************************************************************
     -*/

    // added offset to start of a[]
    void qtree_onebit(int a[], int off, int n, int nx, int ny,
	    byte b[], int bit)
    {
	int         i;
	int		j;
	int		k;
	int         b0;
	int		b1;
	int		b2;
	int		b3;
	int         s10;
	int		s00;


	/*
	 * use selected bit to get amount to shift
	 */
	b0 = 1 << bit;
	b1 = b0 << 1;
	b2 = b0 << 2;
	b3 = b0 << 3;
	k = 0;			/* k is index of b[i/2,j/2]	 */
	for (i = 0; i < nx - 1; i += 2)
	{
	    s00 = n * i;		/* s00 is index of a[i,j]	 */
	    s10 = s00 + n;		/* s10 is index of a[i+1,j]	 */
	    for (j = 0; j < ny - 1; j += 2)
	    {
		// added cast to byte
		b[k] = (byte) ( ( (a[off + s10 + 1] & b0)
			| ((a[off + s10] << 1) & b1)
			| ((a[off + s00 + 1] << 2) & b2)
			| ((a[off + s00] << 3) & b3) ) >> bit );
		k += 1;
		s00 += 2;
		s10 += 2;
	    }
	    if (j < ny)
	    {

		/*
		 * row size is odd, do last element in row s00+1,s10+1 are
		 * off edge
		 */
		// added cast to byte
		b[k] = (byte) ( (((a[off + s10] << 1) & b1)
			| ((a[off + s00] << 3) & b3)) >> bit );
		k += 1;
	    }
	}
	if (i < nx)
	{

	    /*
	     * column size is odd, do last row s10,s10+1 are off edge
	     */
	    s00 = n * i;
	    for (j = 0; j < ny - 1; j += 2)
	    {
		// added cast to byte
		b[k] = (byte) ( (((a[off + s00 + 1] << 2) & b2)
			| ((a[off + s00] << 3) & b3)) >> bit );
		k += 1;
		s00 += 2;
	    }
	    if (j < ny)
	    {

		/*
		 * both row and column size are odd, do corner element
		 * s00+1, s10, s10+1 are off edge
		 */
		// added cast to byte
		b[k] = (byte) ( (((a[off + s00] << 3) & b3)) >> bit );
		k += 1;
	    }
	}
    }

    /*+
     ************************************************************************
     *
     *   Synopsis:
     *	 void	qtree_reduce( a, n, nx, ny, b )
     *
     *   Purpose:
     *	Do one quadtree reduction step on array a results put into b
     *	(which may be the same as a)
     *
     *   Parameters:
     *	byte	a[]		:
     *	int	n		:
     *	int	nx		:
     *	int	ny		:
     *
     *   Values Returned:
     *	void
     *
     *   References:
     *	Copied from hcompress write_bdirect function.
     *	Programmer: R. White         Date: 15 May 1991
     *
     ************************************************************************
     -*/

    void qtree_reduce(byte a[], int n, int nx, int ny, byte b[])
    {
	int         i;
	int		j;
	int		k;
	int         s10;
	int		s00;
	int	i0, i1, i2, i3;

	k = 0;			/* k is index of b[i/2,j/2]	 */
	for (i = 0; i < nx - 1; i += 2)
	{
	    s00 = n * i;		/* s00 is index of a[i,j]	 */
	    s10 = s00 + n;		/* s10 is index of a[i+1,j]	 */
	    for (j = 0; j < ny - 1; j += 2)
	    {
		// added cast to byte
		i0 = i1 = i2 = i3 = 0;
		if (a[s10 + 1] != 0) i0 = 1;
		if (a[s10] != 0) i1 = 1;
		if (a[s00 + 1] != 0) i2 = 1;
		if (a[s00] != 0) i3 = 1;
		b[k] = (byte) ( i0
			| (i1 << 1)
			| (i2 << 2)
			| (i3 << 3) );
		k += 1;
		s00 += 2;
		s10 += 2;
	    }
	    if (j < ny)
	    {

		/*
		 * row size is odd, do last element in row s00+1,s10+1 are
		 * off edge
		 */
		// added cast to byte
		i0 = i1 = 0;
		if (a[s10] != 0) i0 = 1;
		if (a[s00] != 0) i1 = 1;
		b[k] = (byte) ( (i0 << 1) | (i1 << 3) );
		k += 1;
	    }
	}
	if (i < nx)
	{

	    /*
	     * column size is odd, do last row s10,s10+1 are off edge
	     */
	    s00 = n * i;
	    for (j = 0; j < ny - 1; j += 2)
	    {
		// added cast to byte
		i0 = i1 = 0;
		if (a[s00 + 1] != 0) i0 = 1;
		if (a[s00] != 0) i1 = 1;
		b[k] = (byte) ( (i0 << 2) | (i1 << 3) );
		k += 1;
		s00 += 2;
	    }
	    if (j < ny)
	    {

		/*
		 * both row and column size are odd, do corner element
		 * s00+1, s10, s10+1 are off edge
		 */
		// added cast to byte
		i0 = 0;
		if (a[s00] != 0) i0 = 1;
		b[k] = (byte) ( (i0 << 3) );
		k += 1;
	    }
	}
    }

    /*+
     ************************************************************************
     *
     *   Synopsis:
     *	 void	start_outputing_bits()
     *
     *   Purpose:
     *	Initializes the bit output functions.
     *
     *   Parameters:
     *	void
     *
     *   Values Returned:
     *	void
     *
     *   References:
     *	Copied from hcompress start_outputing_bits function.
     *	Programmer: R. White
     *
     ************************************************************************
     -*/

	void start_outputing_bits()
    {
		bo_buffer = 0;
		bo_bits_to_go = 8;
		bitcount = 0;
	}

    /*+
     ************************************************************************
     *
     *   Synopsis:
     *	 int	write_bdirect( char_out, a, n, nqx, nqy, scratch, bit )
     *
     *   Purpose:
     *
     *   Parameters:
     *	int	(*char_out)()	: (in)	Function to output data.
     *	int	a[]		: (in)
     *	int	n		: (in)
     *	int	nqx		: (in)
     *	int	nqy		: (in)
     *	byte	scratch		: (in)
     *	int	bit		: (in)
     *
     *   Values Returned:
     *	int	PR_SUCCESS	: Normal completion.
     *	int	PR_E_IO		: Error during io.
     *	int	PR_E_MEMORY	: Memory allocation failure.
     *
     *   References:
     *	Copied from hcompress write_bdirect function.
     *	Programmer: R. White         Date: 15 May 1991
     *
     ************************************************************************
     -*/

    // added offset in a[]
    int write_bdirect(OutputStream char_out, int a[], int off, int n,
	    int nqx, int nqy, byte scratch[], int bit)
    {
		int         i;

		/*
		 * Write the direct bitmap warning code
		 */

		output_nybble( char_out, 0x0 );


		/*
		 * Copy A to scratch array (again!), packing 4 bits/nybble
		 */

		qtree_onebit(a, off, n, nqx, nqy, scratch, bit);


		/*
		 * write to outfile
	 	*/

		for (i = 0; i < ((nqx + 1) / 2) * ((nqy + 1) / 2); i++)
		{
		    output_nybble( char_out, scratch[i] );
		}

		return( PR_SUCCESS );
    }

	class MutableInt
	{
    	private int value;

	    public MutableInt(int v)
    	{
			value = v;
	    }
	    public MutableInt(Integer v)
    	{
			value = v.intValue();
    	}
	    public void setValue(int v)
    	{
			value = v;
    	}
	    public int intValue()
    	{
			return value;
    	}
	    public String toString()
    	{
			return Integer.toString(value);
    	}
	}
} // end of HCompressCoder class


