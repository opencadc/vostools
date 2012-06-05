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

import java.io.InputStream;
import java.io.IOException;
import java.io.EOFException;

/**
 * This class implements the H-compress decompression technique.
 *
 * For error handling, the public classes throw exceptions but the
 * private classes return integer success/error codes as the the C
 * version of the press library. This helps to keep the code as
 * similar as possible.
 *
 * @version     0.1
 * @author      Patrick Dowler
 */

final class HCompressDecoder
{
    public HCompressDecoder()
    {
		error_msg = new StringBuffer();
		bytes_read = 0;
    }
    private int bytes_read;

/*+
************************************************************************
*
*   Synopsis:
*	static int decode( char_in, char_out, a, nx, ny, scale, format )
*
*   Purpose:
*	Reads codes from an hcompressed file and creates an array.
*
*   Parameters:
*	InputStream		in	: (in) bit source
*	int[]			a	: (out) buffer to fill with decoded data
*	int				nx	: (in) x-dimension of image
*	int				ny	: (in) y-dimension of image
*
*   Values Returned:
*		number of bytes read from the underlying inputstream
*
************************************************************************
-*/

public int decode
(
    InputStream in,			/* (in)  Source for reading data. 	*/
    int[]		a,			/* (out) Output array. 				*/
    int			nx,			/* (in) Size on x axis. 			*/
    int			ny,			/* (in) Size on y axis. 			*/
    int			sumall		/* (in) sum of all pixel values		*/
)
    throws IOException, EOFException
{
    int		nel;
    int		newfits = 0;
    byte	nbitplanes[];

    int		status;

    nbitplanes = new byte[3];

    // caller allocates the image array
    nel = nx*ny;

	// read sum of all pixels
	// this is done by the caller and passed in
    //sumall = in.readInt();

    // Read the number of bits in quadrants.
    for (int i=0; i<3; i++)
    {
		nbitplanes[i] = (byte) in.read();
    }

    //PR_CHECK( dodecode( char_in, *a, *nx, *ny, nbitplanes ) );
    status = dodecode(in,a,nx,ny,nbitplanes);
    if ( status != PR_SUCCESS )
    {
		// handle error - generically for now
		throw new IOException("hcompress decoding failed (" + status + ")");
    }


    // put sum of all pixels back into pixel 0
    a[0] = sumall;

	/*
	 * This isn't too meaningful since the actual values could
	 * have a smaller dynamic range than type 'int'
	 */
    //int bitcount = nx*ny*32;

    //return bitcount;
    return bytes_read;
}


    private StringBuffer error_msg;
    private int PR_SUCCESS = 0;
    private int PR_E_IO = -1;
    private int PR_E_CODE = -2;
    private int PR_E_BITPLANE = -3;


    private int input_nybble(InputStream in)
		throws IOException, EOFException
    {
		return input_nbits(in,4);
    }


    /* THE BIT BUFFER */
    private int  buffer;	/* Bits waiting to be input		 */
    private int  bits_to_go;	/* Number of bits still in buffer	 */

/*+
************************************************************************
*
*   Synopsis:
*	static int	dodecode( char_in, a, nx, ny, nbitplanes )
*
*   Purpose:
*	Decode stream of characters and return array
*	This version encodes the different quadrants separately
*
*   Parameters:
*	int	(*char_in)()	: (in)	Function to get the next data.
*	int	a[]		: (in)  Array to be created.
*	int	nx		: (in)  X axis dimension.
*	int	ny		: (in)  Y axis dimension.
*	int	nbitplanes	: (in)	Number of bitplanes in quadrants.
*
*   Values Returned:
*	int	PR_SUCCESS	: Normal completion.
*	int	PR_E_BITPLANE: Bad bit plane.
*	int	PR_E_CODE	: Bad format code.
*	int	PR_E_IO		: Error during io.
*	int	PR_E_MEMORY	: Memory allocation failure.
*
*   References:
*	Copied from function dodecode from the hcompress program.
*	Programmer: R. White		Date: 9 May 1991
*
************************************************************************
-*/

private int	dodecode
(
    InputStream in,
    int		a[],
    int		nx,
    int		ny,
    byte	nbitplanes[]
)
{
    byte	bit;		/* The value of a bit.			*/
    int		i;
    int		nel;
    int		nx2;
    int		ny2;

    int status;

    nel = nx*ny;
    nx2 = (nx+1)/2;
    ny2 = (ny+1)/2;


    /*
     * initialize a to zero
     */

    for ( i = 0; i < nel; i++ )
    {
	a[i] = 0;
    }


    /*
     * Initialize bit input
     */

    start_inputing_bits();


    /*
     * read bit planes for each quadrant
     */

    // added an offset within a[] to arg list
    status = qtree_decode( in, a, 0, ny, nx2,  ny2,  nbitplanes[0] );
    if ( status != PR_SUCCESS )
	return status;

    status = qtree_decode( in, a, ny2, ny, nx2,  ny/2, nbitplanes[1] );
    if ( status != PR_SUCCESS )
	return status;

    status = qtree_decode( in, a, ny*nx2, ny, nx/2, ny2, nbitplanes[1] );
    if ( status != PR_SUCCESS )
	return status;

    status = qtree_decode( in, a, ny*nx2+ny2, ny, nx/2, ny/2, nbitplanes[2] );
    if ( status != PR_SUCCESS )
	return status;

    /*
     * make sure there is an EOF symbol (nybble=0) at end
     */

    try
    {
	int eof_nybble = input_nybble( in );
	if ( eof_nybble != 0 )
	{
	    return PR_E_BITPLANE;
	}
    }
    catch (IOException ioe)
    {
	return PR_E_IO;
    }


    /*
     * now get the sign bits
     * Re-initialize bit input
     */

    start_inputing_bits();
    for ( i = 0; i < nel; i++)
    {
	if ( a[i] != 0 )
	{
	    try
	    {
		bit = (byte) ( input_bit( in ) );
	    }
	    catch (IOException ioe)
	    {
		return PR_E_IO;
	    }

	    if ( bit != 0 )
	    {
		a[i] = -a[i];
	    }
	}
    }

    return( PR_SUCCESS );
}

/*+
************************************************************************
*
*   Synopsis:
*	static int	input_bit( char_in )
*
*   Purpose:
*	input a bit.
*
*   Parameters:
*	int	(*char_in)()	: (in)	Gets data from input.
*
*   Values Returned:
*	int	value		: if value > 0, the bit value.
*	int	PR_E_IO		: Error during io.
*	int	PR_E_EOI	: End of input.
*
************************************************************************
-*/

private int	input_bit
(
    InputStream in
)
    throws IOException, EOFException
{

    if (true)
	return input_nbits(in,1);

    byte	b;

    if ( bits_to_go == 0 )
    {
	/*
	 *  Read the next byte if no
	 */

	b = (byte) in.read();
	buffer = (int) b;
	bits_to_go = 8;
    }


    /*
     * Return the next bit
     */

    bits_to_go -= 1;
    return ( ( buffer >> bits_to_go ) & 1 );
}

/*+
************************************************************************
*
*   Synopsis:
*	static int	input_huffman( char_in )
*
*   Purpose:
*	 Huffman decoding for fixed codes
*
*	 Coded values range from 0-15
*
*	 Huffman code values ( hex ):
*
*		3e, 00, 01, 08, 02, 09, 1a, 1b,
*		03, 1c, 0a, 1d, 0b, 1e, 3f, 0c
*
*	 and number of bits in each code:
*
*		6,  3,  3,  4,  3,  4,  5,  5,
*		3,  5,  4,  5,  4,  5,  6,  4
*		Statement of purpose.
*
*   Parameters:
*	int	(*char_in)()	: (in)	Function to read data from input.
*
*   Values Returned:
*	int	code		: if value > 0.
*	int	PR_E_IO		: Error during io.
*
*   References:
*	Copied from the hcompress function input_huffman.
*	Programmer: R. White		Date: 7 May 1991
*
************************************************************************
-*/

private int	input_huffman
(
    InputStream in
)
    throws IOException, EOFException
{
    /* static int	input_bit(); */

    int		bit;
    int		code;


    /*
     * get first 3 bits to start
     */

    code = input_nbits( in, 3 );
    if ( code < 4 )
    {
	/*
	 * this is all we need return 1,2,4,8 for c=0,1,2,3
	 */

	return ( 1 << code );
    }


    /*
     * get the next bit
     */

    bit = input_bit( in );
    code = bit | ( code << 1 );
    if ( code < 13 )    {
	/*
	 * OK, 4 bits is enough
	 */

	switch ( code )
	{
	  case 8:
	    return ( 3 );
	  case 9:
	    return ( 5 );
	  case 10:
	    return ( 10 );
	  case 11:
	    return ( 12 );
	  case 12:
	    return ( 15 );
	}
    }


    /*
     * get yet another bit
     */

    bit = input_bit( in );
    code = bit | ( code << 1 );
    if ( code < 31 )
    {
	/*
	 * OK, 5 bits is enough
	 */

	switch ( code )
	{
	  case 26:
	    return ( 6 );
	  case 27:
	    return ( 7 );
	  case 28:
	    return ( 9 );
	  case 29:
	    return ( 11 );
	  case 30:
	    return ( 13 );
	}
    }


    /*
     * need the 6th bit
     */

    bit = input_bit( in );
    code = bit | ( code << 1 );
    if ( code == 62 )
    {
	return ( 0 );
    }
    else
    {
	return ( 14 );
    }
}

/*+
************************************************************************
*
*   Synopsis:
*	static int	input_nbits( char_in, n )
*
*   Purpose:
*	Input n bits, n <= 8.
*
*   Parameters:
*	int	(*char_in)()	: (in)	Function to read data from input.
*	int	n		: (in)	Number of bytes to read.
*
*   Values Returned:
*	int	value		: if > 0, value of the bits
*	int	PR_E_EOI	: End of input detected.
*	int	PR_E_IO		: Error during io.
*
************************************************************************
-*/

private int	input_nbits
(
    InputStream in,
    int		n
)
    throws IOException, EOFException
{
    int		b;

    if ( bits_to_go < n )
    {
		//need another byte's worth of bits
		buffer <<= 8;

		b = in.read(); // could throw an IOException
		if ( b == -1 )
	    	throw new IOException("unexpected end-of-stream");
		bytes_read++;
		buffer |= b;
		bits_to_go += 8;
    }


    // now pick off the first n bits
    bits_to_go -= n;
    return ( buffer >> bits_to_go ) & ( ( 1 << n ) - 1 ) ;
}

/*+
************************************************************************
*
*   Synopsis:
*	static void	qtree_bitins( a, nx, ny, b, n, bit )
*
*   Purpose:
*	Copy 4-bit values from a[( nx+1 )/2,( ny+1 )/2] to b[nx,ny], expanding
*	each value to 2x2 pixels and inserting into bitplane BIT of B.
*	A,B may NOT be same array ( it wouldn't make sense to be inserting
*	bits into the same array anyway. )
*
*   Parameters:
*	byte	a[]		: (in)	Input array.
*	int	nx		: (in)	X dimension.
*	int	ny		: (in)	Y dimension.
*	int	b[]		: (out)	Output array.
*	int	n		: (in)	Declare y dimension of b.
*	int	bit		: (in)
*
*   Values Returned:
*	void
*
*   References:
*	Copied from the hcompress function qtree_bitins.
*	Programmer: R. White		Date: 7 May 1991
*
************************************************************************
-*/

// added arg 'off' as offset for writing to b[]
private void	qtree_bitins
(
    byte	a[],
    int		nx,
    int		ny,
    int		b[],
    int		off,
    int		n,
    int		bit
)
{
    int		i;
    int		j;
    int		b00;
    int		b10;
    int		k;
    int		tmp;
    int		mask;


    mask = 1 << bit;

    /*
     * expand each 2x2 block
     */

    //    ptr_k = a; /* ptr_k   is index of a[i/2,j/2]	 */
    k = 0;

    // NOTE: replace *ptr_b?? with b[ptr_b??] and *ptr_k with a[ptr_k]
    // NOTE: we have to replace b[index] with b[off+index]

    for ( i = 0; i < nx - 1; i += 2 )
    {
	// ptr_b?? are offsets in b[]
	//for ( ptr_b00 = b + n * i, ptr_b10 = ptr_b00 + n;
	//	ptr_b00 < b + n * i + ny - 1  ; ptr_b00 += 2, ptr_b10 += 2 )
	for ( b00 = off + n * i, b10 = b00 + n;
	      b00 < off + n * i + ny - 1;
	      b00 += 2, b10 += 2 )
	{
	    tmp = a[k] << bit;
	    b[b10 + 1] |= tmp & mask;
	    b[b10] |= ( tmp >> 1 ) & mask;
	    b[b00 + 1] |= ( tmp >> 2 ) & mask;
	    b[b00] |= ( tmp >> 3 ) & mask;
	    k++;
	}
	//if ( ptr_b00 < b + n * i + ny )
	if ( b00 < off + n * i + ny )
	/* if ( j < ny )*/
	{
	    /*
	     * row size is odd, do last element in row s00+1, s10+1 are
	     * off edge
	     */

	    b[b10] |= ( ( a[k] >> 1 ) & 1 ) << bit;
	    b[b00] |= ( ( a[k] >> 3 ) & 1 ) << bit;
	    k++;
	}
    }
    if ( i < nx )
    {
	/*
	 * column size is odd, do last row s10, s10+1 are off edge
	 */

	//ptr_b00 = b + n * i;
	b00 = off + n * i;
	for ( j = 0; j < ny - 1; j += 2 )
	{
	    b[b00 + 1] |= ( ( a[k] >> 2 ) & 1 ) << bit;
	    b[b00] |= ( ( a[k] >> 3 ) & 1 ) << bit;
	    b00 += 2;
	    k++;
	}
	if ( j < ny )
	{
	    /*
	     * both row and column size are odd, do corner element
	     * s00+1, s10, s10+1 are off edge
	     */

	    b[b00] |= ( ( a[k] >> 3 ) & 1 ) << bit;
	    a[k] += 1;
	}
    }
}

/*+
************************************************************************
*
*   Synopsis:
*	static void	qtree_copy( a, nx, ny, b, n )
*
*   Purpose:
*	copy 4-bit values from a[( nx+1 )/2,( ny+1 )/2] to b[nx,ny], expanding
*	each value to 2x2 pixels
*	a,b may be same array
*
*   Parameters:
*	byte	a[]		: (in)	Source array
*	int	nx		: (in)	X dimension of destination.
*	int	ny		: (in)	Y dimension of destination.
*	byte	b[]		: (out)	Destination array.
*	int	n		: (in)	Declared y dimension of b.
*
*   Values Returned:
*	void
*
*   References:
*	Copied from the hcompress function qtree_copy.
*	Programmer: R. White		Date: 7 May 1991
*
************************************************************************
-*/

private void	qtree_copy
(
    byte	a[],
    int		nx,
    int		ny,
    byte	b[],
    int		n
)
{
    int		i;
    int		j;
    int		k;
    int		nx2;
    int		ny2;
    int		s00;
    int		s10;


    /*
     * first copy 4-bit values to b start at end in case a,b are same
     * array
     */

    nx2 = ( nx + 1 ) / 2;
    ny2 = ( ny + 1 ) / 2;
    k = ny2 * ( nx2 - 1 ) + ny2 - 1;	/* k   is index of a[i,j]	*/
    for ( i = nx2 - 1; i >= 0; i-- )
    {
	s00 = 2 * ( n * i + ny2 - 1 );	/* s00 is index of b[2*i,2*j]	*/
	for ( j = ny2 - 1; j >= 0; j-- )
	{
	    b[s00] = a[k];
	    k -= 1;
	    s00 -= 2;
	}
    }


    /*
     * now expand each 2x2 block
     */

    for ( i = 0; i < nx - 1; i += 2 )
    {
	s00 = n * i;		/* s00 is index of b[i,j]	 */
	s10 = s00 + n;		/* s10 is index of b[i+1,j]	 */
	for ( j = 0; j < ny - 1; j += 2 )
	{
	    b[s10 + 1] = (byte) ( b[s00] & (byte) 1 );
	    b[s10] = (byte) ( ( b[s00] >> 1 ) & 1 );
	    b[s00 + 1] = (byte) ( ( b[s00] >> 2 ) & 1 );
	    b[s00] = (byte) ( ( b[s00] >> 3 ) & 1 );
	    s00 += 2;
	    s10 += 2;
	}
	if ( j < ny )
	{
	    /*
	     * row size is odd, do last element in row s00+1, s10+1 are
	     * off edge
	     */

	    b[s10] = (byte) ( ( b[s00] >> 1 ) & 1 );
	    b[s00] = (byte) ( ( b[s00] >> 3 ) & 1 );
	}
    }
    if ( i < nx )
    {
	/*
	 * column size is odd, do last row s10, s10+1 are off edge
	 */

	s00 = n * i;
	for ( j = 0; j < ny - 1; j += 2 )
	{
	    b[s00 + 1] = (byte) ( ( b[s00] >> 2 ) & 1 );
	    b[s00] = (byte) ( ( b[s00] >> 3 ) & 1 );
	    s00 += 2;
	}
	if ( j < ny )
	{
	    /*
	     * both row and column size are odd, do corner element
	     * s00+1, s10, s10+1 are off edge
	     */

	    b[s00] = (byte) ( ( b[s00] >> 3 ) & 1 );
	}
    }
}

/*+
************************************************************************
*
*   Synopsis:
*	static int	qtree_decode( char_in, a, n, nqx, nqy, nbitplanes )
*
*   Purpose:
*	Read stream of codes from char_in and construct bit planes
*	in quadrant of 2-D array using binary quadtree coding
*
*   Parameters:
*	int	(*char_in)()	: (in)	Function to get the next byte.
*	int	a[]		: (out)	Array to fill.
*	int	n		: (in)  Declared y dimension of a.
*	int	nqx		: (in)	Partial length of row to decode.
*	int	nqy		: (in)	Partial length of column to decode.
*	int	nbitplanes	: (in)	Number of bitplanes to decode.
*
*   Values Returned:
*	int	PR_SUCCESS	: Normal completion.
*	int	PR_E_CODE	: Bad format code.
*	int	PR_E_IO		: Error during io.
*	int	PR_E_MEMORY	: Memory allocation failure.
*
*   References:
*	Copied from the hcompress function qtree_decode.
*	Programmer: R. White		Date: 7 May 1991
*
************************************************************************
-*/

private int	qtree_decode
(
    InputStream in,
    int		a[],
    int		off,	/* offset within a[] to start at */
    int		n,
    int		nqx,
    int		nqy,
    int		nbitplanes
)
{
    int		log2n;
    int		k;
    int		bit;
    int		b;
    int		nqmax;
    int		nx;
    int		ny;
    int		nfx;
    int		nfy;
    int		c;
    int		nqx2;
    int		nqy2;
    byte	scratch[];
    int		status;


    /*
     * log2n is log2 of max(nqx,nqy) rounded up to next power of 2
     */

    nqmax = ( nqx > nqy ) ? nqx : nqy;
    log2n = (int) ( Math.log( (double) nqmax ) / Math.log( 2.0 ) + 0.5 );

    if ( nqmax > ( 1 << log2n ) )
    {
	log2n += 1;
    }


    /*
     * allocate scratch array for working space
     */

    nqx2 = ( nqx + 1 ) / 2;
    nqy2 = ( nqy + 1 ) / 2;
    scratch = new byte[nqx2 * nqy2];


    /*
     * now decode each bit plane, starting at the top A is assumed to
     * be initialized to zero
     */

    for ( bit = nbitplanes - 1; bit >= 0; bit-- )
    {
	/*
	 * Was bitplane quadtree-coded or written directly?
	 */

	try
	{
	    b = input_nybble( in );
	}
	catch (IOException ioe)
	{
	    return PR_E_IO;
	}
	if ( b == 0 )
	{
	    /*
	     * bit map was written directly
	     */

	    status = read_bdirect( in, a, off, n, nqx, nqy,
		    scratch, bit );
	    if ( status != PR_SUCCESS )
		return status;
	}
	else if ( b != 0xf )
	{
	    return ( PR_E_CODE );
	}
	else
	{
	    /*
	     * bitmap was quadtree-coded, do log2n expansions
	     *
	     * read first code
	     */

	    try
	    {
		scratch[0] = (byte) ( input_huffman( in ) );
	    }
	    catch (IOException ioe)
	    {
		return PR_E_IO;
	    }

	    /*
	     * now do log2n expansions, reading codes from file as
	     * necessary
	     */

	    nx = 1;
	    ny = 1;
	    nfx = nqx;
	    nfy = nqy;
	    c = 1 << log2n;
	    for ( k = 1; k < log2n; k++ )
	    {
		/*
		 * this somewhat cryptic code generates the sequence
		 * n[k-1] = ( n[k]+1 )/2 where n[log2n]=nqx or nqy
		 */

		c = c >> 1;
		nx = nx << 1;
		ny = ny << 1;
		if ( nfx <= c )
		{
		    nx -= 1;
		}
		else
		{
		    nfx -= c;
		}
		if ( nfy <= c )
		{
		    ny -= 1;
		}
		else
		{
		    nfy -= c;
		}
		status = qtree_expand( in, scratch, nx, ny, scratch );
		if ( status != PR_SUCCESS )
		    return status;
	    }


	    /*
	     * now copy last set of 4-bit codes to bitplane bit of
	     * array a
	     */

	    // added off to arg list
	    qtree_bitins( scratch, nqx, nqy, a, off, n, bit );
	}

	// this would be a good place to signal that another
	// bitplane was done if the receiver was doing some
	// incremental display and the receiver can read a[]...

    }
    //    free( scratch );

    return( PR_SUCCESS );
}

/*+
************************************************************************
*
*   Synopsis:
*	static int	qtree_expand( char_in, a, nx, ny, b )
*
*   Purpose:
*	do one quadtree expansion step on array a[( nqx+1 )/2,( nqy+1 )/2]
*	results put into b[nqx,nqy] ( which may be the same as a )
*
*   Parameters:
*	int	(*char_in)()	: (in)	Function to read data from input
*	byte	a[]		: (in)	Array of data to expand.
*	int	nx		: (in)	X dimension of a.
*	int	ny		: (in)	Y dimension of a.
*	byte	b[]		: (out) Expanded data.
*
*   Values Returned:
*	int	PR_SUCCESS	: Normal completion.
*	int	PR_E_IO		: Error during io.
*
*   References:
*	Copied from the hcompress function qtree_expand.
*	Programmer: R. White		Date: 7 May 1991
*
************************************************************************
-*/

private int	qtree_expand
(
    InputStream in,
    byte	a[],
    int		nx,
    int		ny,
    byte	b[]
)
{
    /* static int  input_huffman(); */
    /* static void qtree_copy(); */

    int         i;


    /*
     * first copy a to b, expanding each 4-bit value
     */

    qtree_copy( a, nx, ny, b, ny );


    /*
     * now read new 4-bit values into b for each non-zero element
     */

    for ( i = nx * ny - 1; i >= 0; i-- )
    {
	if ( b[i] != 0 )
	{
	    try
	    {
		b[i] = (byte) ( input_huffman( in ) );
	    }
	    catch (IOException ioe)
	    {
		return PR_E_IO;
	    }
	}
    }

    return( PR_SUCCESS );
}

/*+
************************************************************************
*
*   Synopsis:
*	static int	read_bdirect( char_in, a, n, nqx, nqy, scratch, bit )
*
*   Purpose:
*	Reads an image packed 4 bits per byte.
*
*   Parameters:
*	int	(*char_in)()	: (in)	Function to read data.
*	int	a[]		: (out)
*	int	n		: (in)  Declared y dimension of a.
*	int	nqx		: (in)	X dimension of a.
*	int	nqy		: (in)	Y dimension of a.
*	byte	scratch[]	: (in)
*	int	bit		: (in)
*
*   Values Returned:
*	int	PR_SUCCESS	: Normal completion.
*	int	PR_E_IO		: Error during io.
*
*   References:
*	Copied from the hcompress function qtree_bdirect.
*	Programmer: R. White		Date: 7 May 1991
*
************************************************************************
-*/

private int	read_bdirect
(
    InputStream in,
    int		a[],
    int 	off,
    int		n,
    int		nqx,
    int		nqy,
    byte	scratch[],
    int		bit
)
{
    /* static void qtree_bitins(); */

    int		i;


    /*
     * read bit image packed 4 pixels/nybble
     */

    for ( i = 0; i < ( ( nqx + 1 ) / 2 ) * ( ( nqy + 1 ) / 2 ); i++ )
    {
	try
	{
	    scratch[i] = (byte) ( input_nybble( in ) );
	}
	catch (IOException ioe)
	{
	    return PR_E_IO;
	}
    }


    /*
     * insert in bitplane BIT of image A
     */

    qtree_bitins( scratch, nqx, nqy, a, off, n, bit );

    return( PR_SUCCESS );
}

/*+
************************************************************************
*
*   Synopsis:
*	static void	start_inputing_bits()
*
*   Purpose:
*	Initialize bit input.
*
*   Parameters:
*	void
*
*   Values Returned:
*	void
*
************************************************************************
-*/

private void start_inputing_bits()
{

    /*
     * Buffer starts out with no bits in it
     */
    buffer = 0;
    bits_to_go = 0;
}

}

