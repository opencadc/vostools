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

/**
 * This class implements the H-compress pre- and post-processing
 * methods. All the methods are static.
 *
 * @version     0.1
 * @author      Patrick Dowler
 *
 */

final class HCompressProcessor
{
    private static String findMinMax(int a[], int nx, int ny)
    {
	int min = Integer.MAX_VALUE;
	int max = Integer.MIN_VALUE;
	int v;

	for (int i=0; i<nx; i++)
	    for (int j=0; j<ny; j++)
	    {
		v = a[i + j*nx];
		min = ( min < v ? min : v );
		max = ( max > v ? max : v );
	    }
	return Integer.toString(min) + " to " + Integer.toString(max);
    }


    public static void htrans(int a[], int nx, int ny)
    {
	int nmax, log2n, h0, hx, hy, hc, nxtop, nytop, i, j, k;
	int oddx, oddy;
	int shift, mask, mask2, prnd, prnd2, nrnd2;
	int s10, s00;
	int tmp[];

        /*
         * log2n is log2 of max(nx,ny) rounded up to next power of 2
         */

        nmax = (nx>ny) ? nx : ny;
        log2n = (int) ( Math.log(nmax)/Math.log(2.0) + 0.5 );
        if ( nmax > (1<<log2n) )
	{
	    log2n += 1;
        }

        /*
         * get temporary storage for shuffling elements
         */

	tmp = new int[ (nmax+1)/2 ];

        /*
         * set up rounding and shifting masks
         */

        shift = 0;
        mask  = -2;
        mask2 = mask << 1;
        prnd  = 1;
        prnd2 = prnd << 1;
        nrnd2 = prnd2 - 1;

        /*
         * do log2n reductions
         *
         * We're indexing a as a 2-D array with dimensions (nx,ny).
         */

        nxtop = nx;
        nytop = ny;

        for (k = 0; k<log2n; k++)
	{
	    oddx = nxtop % 2;
	    oddy = nytop % 2;
	    for (i = 0; i<nxtop-oddx; i += 2)
	    {
		s00 = i*ny;    /* s00 is index of a[i,j]       */
		s10 = s00+ny;  /* s10 is index of a[i+1,j]     */
		for (j = 0; j<nytop-oddy; j += 2)
		{
		    /*
                     * Divide h0,hx,hy,hc by 2 (1 the first time through).
		     */
		    h0 = (a[s10+1] + a[s10] + a[s00+1] + a[s00]) >> shift;
		    hx = (a[s10+1] + a[s10] - a[s00+1] - a[s00]) >> shift;
		    hy = (a[s10+1] - a[s10] + a[s00+1] - a[s00]) >> shift;
		    hc = (a[s10+1] - a[s10] - a[s00+1] + a[s00]) >> shift;
		    /*
		     * Throw away the 2 bottom bits of h0, bottom bit of hx,hy.
		     * To get rounding to be same for positive and negative
		     * numbers, nrnd2 = prnd2 - 1.
		     */
		    a[s10+1] = hc;
		    a[s10  ] = ( (hx>=0) ? (hx+prnd)  :  hx ) & mask ;
		    a[s00+1] = ( (hy>=0) ? (hy+prnd)  :  hy ) & mask ;
		    a[s00  ] = ( (h0>=0) ? (h0+prnd2) : (h0+nrnd2) ) & mask2;
		    s00 += 2;
		    s10 += 2;
		}
		if (oddy == 1)
		{
                  /*
		   * do last element in row if row length is odd
		   * s00+1, s10+1 are off edge
		   */
		    h0 = (a[s10] + a[s00]) << (1-shift);
		    hx = (a[s10] - a[s00]) << (1-shift);
		    a[s10  ] = ( (hx>=0) ? (hx+prnd)  :  hx ) & mask ;
		    a[s00  ] = ( (h0>=0) ? (h0+prnd2) : (h0+nrnd2) ) & mask2;
		    s00 += 1;
		    s10 += 1;
		}
	    }
	    if (oddx == 1)
	    {
		/*
		 * do last row if column length is odd
		 * s10, s10+1 are off edge
		 */
		s00 = i*ny;
		for (j = 0; j<nytop-oddy; j += 2)
		{
		    h0 = (a[s00+1] + a[s00]) << (1-shift);
		    hy = (a[s00+1] - a[s00]) << (1-shift);
		    a[s00+1] = ( (hy>=0) ? (hy+prnd)  :  hy ) & mask ;
		    a[s00  ] = ( (h0>=0) ? (h0+prnd2) : (h0+nrnd2) ) & mask2;
		    s00 += 2;
		}
		if (oddy == 1)
		{
		    /*
		     * do corner element if both row and column lengths are odd
		     * s00+1, s10, s10+1 are off edge
		     */
		    h0 = a[s00] << (2-shift);
		    a[s00  ] = ( (h0>=0) ? (h0+prnd2) : (h0+nrnd2) ) & mask2;
		}
	    }
	    /*
	     * now shuffle in each dimension to group coefficients by order
	     */
	    for (i = 0; i<nxtop; i++)
	    {
		//shuffle(&a[ny*i],nytop,1,tmp);
		HCompressProcessor.shuffle(a,ny*i,nytop,1,tmp);
	    }
	    for (j = 0; j<nytop; j++)
	    {
		//shuffle(&a[j],nxtop,ny,tmp);
		HCompressProcessor.shuffle(a,j,nxtop,ny,tmp);
	    }
	    /*
	     * image size reduced by 2 (round up if odd)
	     */
	    nxtop = (nxtop+1)>>1;
	    nytop = (nytop+1)>>1;
	    /*
	     * divisor doubles after first reduction
	     */
	    shift = 1;
	    /*
	     * masks, rounding values double after each iteration
	     */
	    mask  = mask2;
	    prnd  = prnd2;
	    mask2 = mask2 << 1;
	    prnd2 = prnd2 << 1;
	    nrnd2 = prnd2 - 1;
        }
    }

    // Inverse H-transform of NX x NY integer image
    public static void hinv(int a[], int nx, int ny)
    {
	HCompressProcessor.hinv(a,nx,ny,0,0);
    }

    public static void hinv(int a[], int nx, int ny, int smooth, int scale)
    {
	int nmax, log2n, i, j, k;
	int nxtop,nytop,nxf,nyf,c;
	int oddx,oddy;
	int shift, bit0, bit1, bit2, mask0, mask1, mask2,
	    prnd0, prnd1, prnd2, nrnd0, nrnd1, nrnd2, lowbit0, lowbit1;

	int h0, hx, hy, hc;

	int s10, s00;
	int tmp[];

	// need to init to keep javac quiet
	h0 = hx = hy = hc = 0;
	lowbit0 = 0;


	/*
         * log2n is log2 of max(nx,ny) rounded up to next power of 2
         */
        nmax = (nx>ny) ? nx : ny;
        log2n = (int) ( Math.log( (double) nmax )/Math.log(2.0) + 0.5 );
        if ( nmax > (1<<log2n) )
	{
	    log2n += 1;
        }

        /*
         * get temporary storage for shuffling elements
         */

	tmp = new int[ (nmax+1)/2 ];

	/*
         * set up masks, rounding parameters
         */

        shift  = 1;
        bit0   = 1 << (log2n - 1);
        bit1   = bit0 << 1;
        bit2   = bit0 << 2;
        mask0  = -bit0;
        mask1  = mask0 << 1;
        mask2  = mask0 << 2;
        prnd0  = bit0 >> 1;
        prnd1  = bit1 >> 1;
        prnd2  = bit2 >> 1;
        nrnd0  = prnd0 - 1;
        nrnd1  = prnd1 - 1;
        nrnd2  = prnd2 - 1;
        /*
         * round h0 to multiple of bit2
         */
        a[0] = (a[0] + ((a[0] >= 0) ? prnd2 : nrnd2)) & mask2;
        /*
         * do log2n expansions
         *
         * We're indexing a as a 2-D array with dimensions (nx,ny).
         */
        nxtop = 1;
        nytop = 1;
        nxf = nx;
        nyf = ny;
        c = 1<<log2n;

        for (k = log2n-1; k>=0; k--)
	{
	    /*
	     * this somewhat cryptic code generates the sequence
	     * ntop[k-1] = (ntop[k]+1)/2, where ntop[log2n] = n
	     */
	    c = c>>1;
	    nxtop = nxtop<<1;
	    nytop = nytop<<1;
	    if (nxf <= c) { nxtop -= 1; } else { nxf -= c; }
	    if (nyf <= c) { nytop -= 1; } else { nyf -= c; }

	    /*
	     * double shift and fix nrnd0 (because prnd0=0) on last pass
	     */
	    if (k == 0)
	    {
		nrnd0 = 0;
		shift = 2;
	    }
	    /*
	     * unshuffle in each dimension to interleave coefficients
	     */
	    for (i = 0; i<nxtop; i++)
	    {
		HCompressProcessor.unshuffle(a,ny*i,nytop,1,tmp);
	    }
	    for (j = 0; j<nytop; j++)
	    {
		HCompressProcessor.unshuffle(a,j,nxtop,ny,tmp);
	    }
	    /*
	     * smooth by interpolating coefficients if smooth != 0
	     */

	    if (smooth != 0)
		HCompressProcessor.hsmooth(a,nxtop,nytop,ny,scale);

	    oddx = nxtop % 2;
	    oddy = nytop % 2;
	    for (i = 0; i<nxtop-oddx; i += 2)
	    {
		s00 = ny*i;          /* s00 is index of a[i,j]       */
		s10 = s00+ny;        /* s10 is index of a[i+1,j]     */
		for (j = 0; j<nytop-oddy; j += 2)
		{
		    h0 = a[s00  ];
		    hx = a[s10  ];
		    hy = a[s00+1];
		    hc = a[s10+1];
                    /*
		     * round hx and hy to multiple of bit1
		     * and hc to multiple of bit0
		     * h0 is already a multiple of bit2
		     */
		    hx = (hx + ((hx >= 0) ? prnd1 : nrnd1)) & mask1;
		    hy = (hy + ((hy >= 0) ? prnd1 : nrnd1)) & mask1;
		    hc = (hc + ((hc >= 0) ? prnd0 : nrnd0)) & mask0;
                    /*
		     * propagate bit0 of hc to hx,hy
		     */
		    lowbit0 = hc & bit0;
		    hx = (hx >= 0) ? (hx - lowbit0) : (hx + lowbit0);
		    hy = (hy >= 0) ? (hy - lowbit0) : (hy + lowbit0);
		    /*
		     * Propagate bits 0 and 1 of hc,hx,hy to h0.
		     * This could be simplified if we assume h0>0, but then
		     * the inversion would not be lossless for image with
		     * negative pixels.
		     */
		    lowbit1 = (hc ^ hx ^ hy) & bit1;
		    h0 = (h0 >= 0)
			? (h0 + lowbit0 - lowbit1)
			: (h0 + ((lowbit0 == 0) ? lowbit1 :
				(lowbit0-lowbit1)));
		    /*
		     * Divide sums by 2 (4 last time)
		     */
		    a[s10+1] = (h0 + hx + hy + hc) >> shift;
		    a[s10  ] = (h0 + hx - hy - hc) >> shift;
		    a[s00+1] = (h0 - hx + hy - hc) >> shift;
		    a[s00  ] = (h0 - hx - hy + hc) >> shift;
		    s00 += 2;
		    s10 += 2;
		}
		if (oddy == 1)
		{
		    /*
		     * do last element in row if row length is odd
		     * s00+1, s10+1 are off edge
		     */
		    h0 = a[s00  ];
		    hx = a[s10  ];
		    hx = ((hx >= 0) ? (hx+prnd1) : (hx+nrnd1)) & mask1;

		    // this is in the press library
		    lowbit1 = hx & bit1;
		    h0 = (h0 >= 0) ? (h0 - lowbit1) : (h0 + lowbit1);

		    // this is used by hcomp in /usr/cadc/misc
		    // and does not reconstruct the image exactly
		    // for lossless compression

		    //lowbit1 = (hc ^ hx ^ hy) & bit1;
		    //h0 = (h0 >= 0)
		    //? (h0 + lowbit0 - lowbit1)
		    //: (h0 + ((lowbit0 == 0) ? lowbit1 :
		    //	(lowbit0-lowbit1)));

		    a[s10  ] = (h0 + hx) >> shift;
		    a[s00  ] = (h0 - hx) >> shift;
		}
	    }
	    if (oddx == 1)
	    {
		/*
		 * do last row if column length is odd
		 * s10, s10+1 are off edge
		 */
		s00 = ny*i;
		for (j = 0; j<nytop-oddy; j += 2)
		{
		    h0 = a[s00  ];
		    hy = a[s00+1];
		    hy = ((hy >= 0) ? (hy+prnd1) : (hy+nrnd1)) & mask1;

		    // this is used in the press library
		    lowbit1 = hy & bit1;
		    h0 = (h0 >= 0) ? (h0 - lowbit1) : (h0 + lowbit1);

		    // this is used in hcomp
		    // and does not reconstruct the image exactly
		    // for lossless compression

		    //lowbit1 = (hc ^ hx ^ hy) & bit1;
		    //h0 = (h0 >= 0)
		    //? (h0 + lowbit0 - lowbit1)
		    //: (h0 + ((lowbit0 == 0) ? lowbit1 :
		    //	(lowbit0-lowbit1)));

		    a[s00+1] = (h0 + hy) >> shift;
		    a[s00  ] = (h0 - hy) >> shift;
		    s00 += 2;
		}
		if (oddy == 1)
		{
		    /*
		     * do corner element if both row and column lengths are odd
		     * s00+1, s10, s10+1 are off edge
		     */

		    h0 = a[s00  ];
		    a[s00  ] = h0 >> shift;
		}
	    }
	    /*
	     * divide all the masks and rounding values by 2
	     */
	    bit2 = bit1;
	    bit1 = bit0;
	    bit0 = bit0 >> 1;
	    mask1 = mask0;
	    mask0 = mask0 >> 1;
	    prnd1 = prnd0;
	    prnd0 = prnd0 >> 1;
	    nrnd1 = nrnd0;
	    nrnd0 = prnd0 - 1;
        }
    }

    // digitize H-transform
    public static void digitize(int a[], int nx, int ny, int scale)
    {
	int d;

        /*
         * round to multiple of scale
         */
        if (scale <= 1)
	    return;
        d = ( scale+1 )/2 - 1;
        //for (p=a; p <= &a[nx*ny-1]; p++)
	//    *p = ((*p>0) ? (*p+d) : (*p-d))/scale;
	for (int i=0; i<nx*ny; i++)
	    a[i] = ( (a[i] > 0) ? ( a[i] + d ) : ( (a[i] - d) ) )/scale;
    }
    // undigitize H-transform
    public static void undigitize(int a[], int nx, int ny, int scale)
    {
        /*
         * multiply by scale
         */
        if (scale <= 1)
	    return;

        //for (p=a; p <= &a[nx*ny-1]; p++)
	//    *p = (*p)*scale;
	int max = nx*ny-1;
	int amax = a.length-1;

	for ( int i=0; i < nx*ny; i++ )
	    a[i] *= scale;

    }

    /**
     * private methods
     */

    // shuffle elements from off to off+len
    // change: use offset in a instead of pointer fudging
    private static void shuffle(int a[], int off, int n, int n2, int tmp[])
    {
	// copy odd elements to tmp
	int p1 = off + n2;
	int pt = 0;
	for (int i=1; i<n; i+=2)
	{
	    tmp[pt] = a[p1];
	    pt += 1;
	    p1 += n2+n2;
	}
	// compress even elements into first half of a
	p1 = off + n2;
	int p2 = off + n2 + n2;
	for (int i=2; i<n; i+=2)
	{
	    a[p1] = a[p2];
	    p1 += n2;
	    p2 += n2+n2;
	}
	// copy odd elements into second half of a
	pt = 0;
	for (int i=1; i<n; i+=2)
	{
	    a[p1] = tmp[pt];
	    p1 += n2;
	    pt += 1;
	}
    }

    private static void unshuffle(int a[], int off, int n, int n2, int tmp[])
    {
	int i;
	int nhalf;
	int p1, p2, pt;

	//copy 2nd half of array to tmp
	nhalf = (n+1)>>1;
        pt = 0;
        p1 = off + n2*nhalf;    /* pointer to a[off+i] */
        for (i=nhalf; i<n; i++)
	{
	    tmp[pt] = a[p1];
	    p1 += n2;
	    pt += 1;
        }
        // distribute 1st half of array to even elements
        p2 = off + n2*(nhalf-1);      /* a[off+i]   */

	// << has lower precedence than +
        p1 = off + ( ( n2*(nhalf-1) ) << 1 ); /* a[off+2*i] */
        for (i=nhalf-1; i >= 0; i--)
	{
	    a[p1] = a[p2];
	    p2 -= n2;
	    p1 -= (n2+n2);
        }
        // now distribute 2nd half of array (in tmp) to odd elements
        pt = 0;
        p1 = off + n2;     /* pointer to a[i] */
        for (i=1; i<n; i += 2)
	{
	    a[p1] = tmp[pt];
	    p1 += (n2+n2);
	    pt += 1;
        }
    }

    // Smooth H-transform image by adjusting coefficients toward
    // interpolated values
    private static void hsmooth(int a[], int nxtop, int nytop,
	    int ny, int scale)
    {
	int i, j;
	int ny2, s10, s00, diff, dmax, dmin, s, smax;
	int hm, h0, hp, hmm, hpm, hmp, hpp, hx2, hy2;
	int m1,m2;

        /*
         * Maximum change in coefficients is determined by scale factor.
         * Since we rounded during division (see digitize.c), the biggest
         * permitted change is scale/2.
         */
        smax = (scale >> 1);
        if (smax <= 0)
	    return;
        ny2 = ny << 1;

        /*
         * We're indexing a as a 2-D array with dimensions (nxtop,ny) of which
         * only (nxtop,nytop) are used.  The coefficients on the edge of the
         * array are not adjusted (which is why the loops below start at 2
         * instead of 0 and end at nxtop-2 instead of nxtop.)
         */

        /*
         * Adjust x difference hx
         */
        for (i = 2; i<nxtop-2; i += 2)
	{
	    s00 = ny*i;          /* s00 is index of a[i,j]       */
	    s10 = s00+ny;        /* s10 is index of a[i+1,j]     */
	    for (j = 0; j<nytop; j += 2)
	    {
		/*
		 * hp is h0 (mean value) in next x zone, hm is h0 in
		 * previous x zone
		 */
		hm = a[s00-ny2];
		h0 = a[s00];
		hp = a[s00+ny2];
		/*
		 * diff = 8 * hx slope that would match h0 in neighboring zones
		 */
		diff = hp-hm;
		/*
		 * monotonicity constraints on diff
		 */
		dmax = Math.max( Math.min( (hp-h0), (h0-hm) ), 0 ) << 2;
		dmin = Math.min( Math.max( (hp-h0), (h0-hm) ), 0 ) << 2;
		/*
		 * if monotonicity would set slope = 0 then don't change hx.
		 * note dmax>=0, dmin<=0.
		 */

		if (dmin < dmax)
		{
		    diff = Math.max( Math.min(diff, dmax), dmin);
		    /*
		     * Compute change in slope limited to range +/- smax.
		     * Careful with rounding negative numbers when using
		     * shift for divide by 8.
		     */
		    s = diff-(a[s10]<<3);
		    s = (s>=0) ? (s>>3) : ((s+7)>>3) ;
		    s = Math.max( Math.min(s, smax), -smax);
		    a[s10] = a[s10]+s;
		}
		s00 += 2;
		s10 += 2;
	    }
        }
        /*
         * Adjust y difference hy
         */
        for (i = 0; i<nxtop; i += 2)
	{
	    s00 = ny*i+2;
	    s10 = s00+ny;
	    for (j = 2; j<nytop-2; j += 2)
	    {
		hm = a[s00-2];
		h0 = a[s00];
		hp = a[s00+2];
		diff = hp-hm;
		dmax = Math.max( Math.min( (hp-h0), (h0-hm) ), 0 ) << 2;
		dmin = Math.min( Math.max( (hp-h0), (h0-hm) ), 0 ) << 2;
		if (dmin < dmax)
		{
		    diff = Math.max( Math.min(diff, dmax), dmin);
		    s = diff-(a[s00+1]<<3);
		    s = (s>=0) ? (s>>3) : ((s+7)>>3) ;
		    s = Math.max( Math.min(s, smax), -smax);
		    a[s00+1] = a[s00+1]+s;
		}
		s00 += 2;
		s10 += 2;
	    }
        }
        /*
         * Adjust curvature difference hc
         */
        for (i = 2; i<nxtop-2; i += 2)
	{
	    s00 = ny*i+2;
	    s10 = s00+ny;
	    for (j = 2; j<nytop-2; j += 2)
	    {
		/*
		 * ------------------    y
		 * | hmp |    | hpp |    |
		 * ------------------    |
		 * |     | h0 |     |    |
		 * ------------------    -------x
		 * | hmm |    | hpm |
		 * ------------------
		 */
		hmm = a[s00-ny2-2];
		hpm = a[s00+ny2-2];
		hmp = a[s00-ny2+2];
		hpp = a[s00+ny2+2];
		h0  = a[s00];
		/*
		 * diff = 64 * hc value that would match h0 in
		 * neighboring zones
		 */
		diff = hpp + hmm - hmp - hpm;
		/*
		 * 2 times x,y slopes in this zone
		 */
		hx2 = a[s10  ]<<1;
		hy2 = a[s00+1]<<1;
		/*
		 * monotonicity constraints on diff
		 */
		m1 = Math.min( Math.max(hpp-h0,0)-hx2-hy2,
			Math.max(h0-hpm,0)+hx2-hy2);
		m2 = Math.min( Math.max(h0-hmp,0)-hx2+hy2,
			Math.max(hmm-h0,0)+hx2+hy2);
		dmax = Math.min(m1,m2) << 4;
		m1 = Math.max( Math.min(hpp-h0,0)-hx2-hy2,
			Math.min(h0-hpm,0)+hx2-hy2);
		m2 = Math.max( Math.min(h0-hmp,0)-hx2+hy2,
			Math.min(hmm-h0,0)+hx2+hy2);
		dmin = Math.max(m1,m2) << 4;
		/*
		 * if monotonicity would set slope = 0 then don't change hc.
		 */
		if (dmin < dmax)
		{
		    diff = Math.max( Math.min(diff, dmax), dmin);
                    /*
		     * Compute change in slope limited to range +/- smax.
		     * Careful with rounding negative numbers when using
		     * shift for divide by 64.
		     */
		    s = diff-(a[s10+1]<<6);
		    s = (s>=0) ? (s>>6) : ((s+63)>>6) ;
		    s = Math.max( Math.min(s, smax), -smax);
		    a[s10+1] = a[s10+1]+s;
		}
		s00 += 2;
		s10 += 2;
	    }
        }
    }

};

