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

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 * This class implements a stream filter for writing numeric data to an
 * underlying output stream (of bytes). As an extension of DataOutputStream,
 * it adds the ability  to write large chunks of internal types at once in
 * binary format.
 *
 * @see		DataOutputStream
 * @see		OutputStream
 * @version 0.1
 * @author  Patrick Dowler
 *
 */
public class BulkDataOutputStream extends DataOutputStream
    implements BulkDataOutput
{
    protected boolean eos;

    /**
     * Constructor.
     *
     * @param istream an input stream with readInt() methods.
     */
    public BulkDataOutputStream(OutputStream is)
    {
		super(is);
		eos = false;
    }

    // write characters
    public void writeChar( char[] buf )
    	throws IOException
    {
		this.writeChar(buf,0,buf.length);
    }

    public void writeChar( char[] buf, int off, int len)
		throws IOException
    {
		int i = 0;
		while ( i < off+len )
		{
		    // use DataOutputStream.writeChar()
	    	this.writeChar(buf[i+off]);
		    i++;
		}
    }

    // write 8-bit signed integers
    public void writeByte(byte b)
    	throws IOException
    {
    	this.write(b);
    }

    public void writeByte( byte[] buf )
    	throws IOException
    {
		this.writeByte(buf,0,buf.length);
    }

    public void writeByte( byte[] buf, int off, int len)
		throws IOException
    {
		int i = 0;
		while ( i < off+len )
		{
		    // use DataOutputStream.writeByte()
	    	this.writeByte(buf[i+off]);
		    i++;
		}
    }

    // write 8-bit unsigned integers
    public void writeUnsignedByte( short i )
		throws IOException
    {
		this.writeByte( i + Byte.MIN_VALUE );
    }

    public void writeUnsignedByte( short[] buf )
    	throws IOException
    {
		this.writeUnsignedByte(buf,0,buf.length);
    }

    public void writeUnsignedByte( short[] buf, int off, int len)
		throws IOException
    {
		int i = 0;
		while ( i < off+len )
		{
		    this.writeUnsignedByte( buf[i+off] );
		    i++;
		}
    }

    // write 16-bit signed integers
    public void writeShort( short[] buf )
    	throws IOException
    {
		this.writeShort(buf,0,buf.length);
    }

    public void writeShort( short[] buf, int off, int len)
		throws IOException
    {
		int i = 0;
		while ( i < len )
		{
	    	// use DataOutputStream.writeShort()
		    this.writeShort(buf[i+off]);
		    i++;
		}
    }

    // write 16-bit unsigned integers
    public void writeUnsignedShort( int i )
    	throws IOException
    {
		this.writeShort( i + Short.MIN_VALUE );
    }

    public void writeUnsignedShort( int[] buf )
    	throws IOException
    {
		this.writeUnsignedShort(buf,0,buf.length);
    }

    public void writeUnsignedShort( int[] buf, int off, int len)
		throws IOException
    {
		int i = 0;
		while ( i < off+len )
		{
		    this.writeUnsignedShort(buf[i+off]);
		    i++;
		}
    }

    // write 32-bit signed integers
    public void writeInt( int[] buf )
    	throws IOException
    {
		this.writeInt(buf,0,buf.length);
    }

    public void writeInt( int[] buf, int off, int len)
		throws IOException
    {
		int i = 0;
		while ( i < off+len )
		{
		    // use DataOutputStream.writeInt()
		    this.writeInt(buf[i+off]);
	    	i++;
		}
    }

    // write 64-bit signed integers
    public void writeLong( long[] buf )
    	throws IOException
    {
		this.writeLong(buf,0,buf.length);
    }

    public void writeLong( long[] buf, int off, int len)
		throws IOException
    {
		int i = 0;
		while ( i < off+len )
		{
		    // use DataOutputStream.writeLong()
		    this.writeLong(buf[i+off]);
	    	i++;
		}
    }

    // write 32-bit floating point values
    public void writeFloat( float[] buf )
    	throws IOException
    {
		this.writeFloat(buf,0,buf.length);
    }

    public void writeFloat( float[] buf, int off, int len)
		throws IOException
    {
		int i = 0;
		while ( i < off+len )
		{
		    // use DataOutputStream.writeFloat()
		    this.writeFloat(buf[i+off]);
	    	i++;
		}
    }

    // write 64-bit floating point values
    public void writeDouble( double[] buf )
    	throws IOException
    {
		this.writeDouble(buf,0,buf.length);
    }

    public void writeDouble( double[] buf, int off, int len)
		throws IOException
    {
		int i = 0;
		while ( i < off+len )
		{
		    // use DataOutputStream.writeDouble()
		    this.writeDouble(buf[i+off]);
	    	i++;
		}
    }
}
