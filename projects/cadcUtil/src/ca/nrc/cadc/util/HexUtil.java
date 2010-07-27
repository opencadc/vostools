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

package ca.nrc.cadc.util;

/**
 * Conversion methods for numeric values -- byte[] -- hexadecimal.
 */
public class HexUtil
{
	private static final char[] HEXDIGIT =
	{
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'a', 'b', 'c', 'd', 'e', 'f'
    };

    // this hex to byte[] code taken mericlessly from http://mindprod.com/jgloss/hex.html
    private static byte[] correspondingNibble = new byte['f' + 1];
    static
    {
        // only 0..9 A..F a..f have meaning. rest are errors.
        for ( int i = 0; i <= 'f'; i++ )
            correspondingNibble[ i ] = -1;
        for ( int i = '0'; i <= '9'; i++ )
            correspondingNibble[ i ] = ( byte ) ( i - '0' );
        for ( int i = 'A'; i <= 'F'; i++ )
            correspondingNibble[ i ] = ( byte ) ( i - 'A' + 10 );
        for ( int i = 'a'; i <= 'f'; i++ )
            correspondingNibble[ i ] = ( byte ) ( i - 'a' + 10 );
    }
	
	/**
	 * Extract short at byte offset == 0.
     *
     * @param b
     * @return 16-bit integer value
	 */
	public static short toShort(byte[] b)
	{
		return toShort(b, 0);
	}

	/**
	 * Extract short at specified offset.
     *
     * @param b
     * @param offset
     * @return 16-bit integer value at specified offset
	 */
	public static short toShort(byte[] b, int offset)
	{
		return (short) (((b[offset] & 255) << 8) | ((b[offset + 1] & 255)));

	}

	/**
	 * Extract int at byte offset == 0.
     *
     * @param b
     * @return 32-bit integer value
	 */
	public static int toInt(byte[] b)
	{
		return toInt(b, 0);
	}

	/**
	 * Extract int at specified byte offset.
     *
     * @param b
     * @param offset
     * @return 32-bit integer value at specified offset
	 */
	public static int toInt(byte[] b, int offset)
	{
		return (((b[offset] & 255) << 24)
				| ((b[offset + 1] & 255) << 16)
				| ((b[offset + 2] & 255) << 8)
				| ((b[offset + 3] & 255)));
	}

	/**
	 * Extract long at byte offset == 0.
     *
     * @param b
     * @return 64-bit integer value
	 */
	public static long toLong(byte[] b)
	{
		return toLong(b, 0);
	}

	/**
	 * Extract long at specified byte offset.
     *
     * @param b
     * @param offset
     * @return 64-bit integer value at specified offset
	 */
	public static long toLong(byte[] b, int offset)
	{
		return (((b[offset] & 255L) << 56)
				| ((b[offset + 1] & 255L) << 48)
				| ((b[offset + 2] & 255L) << 40)
				| ((b[offset + 3] & 255L) << 32)
				| ((b[offset + 4] & 255L) << 24)
				| ((b[offset + 5] & 255L) << 16)
				| ((b[offset + 6] & 255L) << 8)
				| ((b[offset + 7] & 255L)));
	}

	/**
	 * Put byte into an array of length == 1.
     *
     * @param n
     * @return a minimum-sized byte[] containing n
	 */
	public static byte[] toBytes(byte n)
	{
		return new byte[] { n };
	}

	/**
	 * Encode a short as bytes and put into array of length == 2.
     *
     * @param n
     * @return a minimum-sized byte[] containing n
	 */
	public static byte[] toBytes(short n)
	{
		byte[] ret = new byte[2];
		ret[0] = (byte) (n >> 8);
		ret[1] = (byte) ((n << 8) >> 8);
		//ret[1] = (byte) (n & 0x00ff );
		return ret;
	}

	/**
	 * Encode a int as bytes and put into array of length == 4.
     *
     * @param n
     * @return a minimum-sized byte[] containing n
	 */
	public static byte[] toBytes(int n)
	{
		byte[] ret = new byte[4];
		ret[0] = (byte) (n >> 24);
		ret[1] = (byte) ((n << 8) >> 24);
		ret[2] = (byte) ((n << 16) >> 24);
		ret[3] = (byte) ((n << 24) >> 24);
		return ret;
	}
    
	/**
	 * Encode a long as bytes and put into array of length == 8.
     *
     * @param n
     * @return a minimum-sized byte[] containing n
	 */
	public static byte[] toBytes(long n)
	{
		byte[] ret = new byte[8];
		ret[0] = (byte) (n >> 56);
		ret[1] = (byte) ((n << 8) >> 56);
		ret[2] = (byte) ((n << 16) >> 56);
		ret[3] = (byte) ((n << 24) >> 56);
		ret[4] = (byte) ((n << 32) >> 56);
		ret[5] = (byte) ((n << 40) >> 56);
		ret[6] = (byte) ((n << 48) >> 56);
		ret[7] = (byte) ((n << 56) >> 56);
		return ret;
	}

    /**
	 * Encode arbitrary byte array as a hex string. There are always 2
	 * hex characters per byte in the array.
     *
     * @param b
     * @return a hexadecimal representation of b
	 */
	public static String toHex(byte[] b)
	{
		StringBuffer sb = new StringBuffer( 2*b.length );
		for (int i=0; i<b.length; i++)
			sb.append( toHex( b[i] ) );
		return sb.toString();
	}
    
	/**
	 * Writes value as a 1-byte hex string (##). The
	 * returned string always has 2 hex digits.
	 * Note: Take on code from Sun/IBM.
     *
     * @param b
     * @return a hexadecimal representation of b
	 */
	static public String toHex(byte b)
	{
		// Returns hex String representation of byte b
		char[] array = { HEXDIGIT[(b >> 4) & 0x0f], HEXDIGIT[b & 0x0f] };
		return new String(array);
	}

	/**
	 * Writes value as a 2-byte hex string (####). The
	 * returned string always has 4 hex digits.
     *
     * @param val
     * @return a hexadecimal representation of val
	 */
	public static String toHex(short val)
	{
		//if  (val < 0)
		//	throw new IllegalArgumentException("can't convert negative value: "+val);
		// there is no Short.toHexString()
		String hex = Integer.toHexString(val);
		if (hex.length() > 4)
			hex = hex.substring(4);
		switch (hex.length())
		{
			case 1 :
				return "000" + hex;
			case 2 :
				return "00" + hex;
			case 3 :
				return "0" + hex;
			case 4 :
				return hex;
			default :
				throw new NumberFormatException(
					"Integer.toHexString(short) returned "
						+ hex
						+ " characters: "
						+ hex);
		}
	}

	/**
	 * Writes value as a 4-byte hex string (########). The
	 * returned string always has 8 hex digits.
     *
     * @param val
     * @return a hexadecimal representation of val
	 */
	public static String toHex(int val)
	{
		String hex = Integer.toHexString(val);
		int c = hex.length();
		if (c < 1 || c > 8)
			throw new NumberFormatException(
				"Integer.toHexString(int) returned "
					+ hex.length()
					+ " characters: "
					+ hex);

		StringBuffer sb = new StringBuffer(8);
		for (int i = 0; i < 8 - c; i++)
			sb.append("0");
		sb.append(hex);
		return sb.toString();
	}

	/**
	 * Writes value as a 8-byte hex string (################).
	 * The returned string always has 16 hex digits.
     *
     * @param val
     * @return a hexadecimal representation of val
	 */
	public static String toHex(long val)
	{
		String hex = Long.toHexString(val);
		int c = hex.length();
		if (c < 1 || c > 16)
			throw new NumberFormatException(
				"Long.toHexString(long) returned "
					+ hex.length()
					+ " characters: "
					+ hex);

		StringBuffer sb = new StringBuffer(16);
		for (int i = 0; i < 16 - c; i++)
			sb.append("0");
		sb.append(hex);
		return sb.toString();
	}

    /**
     * Convert a hex string to byte.
     *
     * @param hex
     * @return 8-bit integer value
     */
    public static byte toByte(String hex)
    {
        if (hex == null || hex.length() != 2)
            throw new IllegalArgumentException();
        byte[] b = HexUtil.toBytes(hex);
        return b[0];
    }

    /**
     * Convert a hex string to a short.
     *
     * @param hex
     * @return 16-bit integer value
     */
    public static short toShort(String hex)
    {
        if (hex == null || hex.length() != 4)
            throw new IllegalArgumentException();
        //return Short.parseShort(hex, 16);
        return HexUtil.toShort(HexUtil.toBytes(hex));
    }

    /**
     * Convert a hex string to an int.
     *
     * @param hex
     * @return 32-bit integer value
     */
    public static int toInt(String hex)
    {
        if (hex == null || hex.length() != 8)
            throw new IllegalArgumentException();
        //return Integer.parseInt(hex, 16);
        return HexUtil.toInt(HexUtil.toBytes(hex));
    }

    /**
     * Convert a hex string to a long.
     *
     * @param hex
     * @return 64-bit integer value
     */
    public static long toLong(String hex)
    {
        if (hex == null || hex.length() != 16)
            throw new IllegalArgumentException();
        //return Long.parseLong(hex, 16);
        return HexUtil.toLong(HexUtil.toBytes(hex));
    }

    /**
     * Convert a hex string to a byte[].
     *
     * @param hex
     * @return byte array
     */
    public static byte[] toBytes(String hex)
    {
        if (hex == null || hex.length() % 2 != 0)
            throw new IllegalArgumentException();
        byte[] ret = new byte[hex.length() / 2];

        //for (int i=0; i<ret.length; i++)
        //    ret[i] = HexUtil.toByte(hex.substring(i*2, 2));

        for ( int i = 0, j = 0; i < hex.length(); i += 2, j++ )
        {
            int high = charToNibble( hex.charAt( i ) );
            int low = charToNibble( hex.charAt( i + 1 ) );
            // You can store either unsigned 0..255 or signed -128..127 bytes in a byte type.
            ret[j] = ( byte ) ( ( high << 4 ) | low );
        }
        return ret;
    }

    private static int charToNibble( char c )
    {
        if ( c > 'f' )
        {
            throw new IllegalArgumentException( "Invalid hex character: " + c );
        }
        int nibble = correspondingNibble[ c ];
        if ( nibble < 0 )
        {
            throw new IllegalArgumentException( "Invalid hex character: " + c );
        }
        return nibble;
    }
}
