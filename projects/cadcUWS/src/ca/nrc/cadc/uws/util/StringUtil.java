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


package ca.nrc.cadc.uws.util;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;


/**
 * Useful utility methods dealing with Strings.  Not terribly Object Oriented
 * as this class contains no state or identity, but it's nice not having to
 * write this stuff over and over.
 *
 * Thanks to Rod Johnson for contributing.
 */
public class StringUtil
{
    /**
     * Check if a String has length.
     * <p><pre>
     * StringUtil.hasLength(null) = false
     * StringUtil.hasLength("") = false
     * StringUtil.hasLength(" ") = true
     * StringUtil.hasLength("Hello") = true
     * </pre>
     *
     * @param str the String to check, may be null
     * @return <code>true</code> if the String is not null and has length
     */
    public static boolean hasLength(String str)
    {
        return ((str != null) && (str.length() > 0));
    }

    /**
     * Check if a String has text. More specifically, returns <code>true</code>
     * if the string not <code>null<code>, it's <code>length is > 0</code>, and
     * it has at least one non-whitespace character.
     * <p><pre>
     * StringUtil.hasText(null) = false
     * StringUtil.hasText("") = false
     * StringUtil.hasText(" ") = false
     * StringUtil.hasText("12345") = true
     * StringUtil.hasText(" 12345 ") = true
     * </pre>
     *
     * @param str the String to check, may be null
     * @return <code>true</code> if the String is not null, length > 0,
     *         and not whitespace only
     * @see Character#isWhitespace
     */
    public static boolean hasText(String str)
    {
        if (!hasLength(str))
        {
            return false;
        }

        for (int i = 0; i < str.length(); i++)
        {
            if (!Character.isWhitespace(str.charAt(i)))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Trim leading whitespace from the given String.
     *
     * @param str the String to check
     * @return the trimmed String
     * @see Character#isWhitespace
     */
    public static String trimLeadingWhitespace(String str)
    {
        if (str.length() == 0)
        {
            return str;
        }

        StringBuffer buf = new StringBuffer(str);
        while ((buf.length() > 0) &&
               Character.isWhitespace(buf.charAt(0)))
        {
            buf.deleteCharAt(0);
        }

        return buf.toString();
    }

    /**
     * Trim trailing whitespace from the given String.
     * @param str the String to check
     * @return the trimmed String
     * @see Character#isWhitespace
     */
    public static String trimTrailingWhitespace(String str)
    {
        if (str.length() == 0)
        {
            return str;
        }

        StringBuffer buf = new StringBuffer(str);
        while ((buf.length() > 0) &&
               Character.isWhitespace(buf.charAt(buf.length() - 1)))
        {
            buf.deleteCharAt(buf.length() - 1);
        }

        return buf.toString();
	}

    public static boolean contains(final String searchString, final String crit)
    {
        if (hasLength(searchString) && hasLength(crit))
        {
            return searchString.indexOf(crit) >= 0;
        }

        return false;
    }

    public static boolean startsWith(final String searchString, final String crit)
    {
        if (hasLength(searchString) && hasLength(crit))
        {
            return searchString.startsWith(crit);
        }

        return false;
    }

    /**
     * Obtain whether the searchString is in the given array.
     *
     * @param searchString
     * @param crit
     * @return True if the searchString is in the crit array, False otherwise.
     */
    public static boolean containedIn(final String searchString,
                                      final String[] crit)
    {
        if (hasLength(searchString))
        {
            for (int i = 0; i < crit.length; i++)
            {
                if (crit[i].equals(searchString))
                {
                    return true;
                }
            }
        }

        return false;
    }
    
    /**
     * Read an input stream into a string.  The input cannot be of binary contents.
     * 
     * @param inputStream
     * @return String
     * @throws IOException
     * @author zhangsa
     */
    public static String readFromInputStream(InputStream inputStream) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        BufferedInputStream bufIS = null;
        try
        {
            bufIS = new BufferedInputStream(inputStream);
            int numBytes;
            byte[] buffer;
            
            do {
                buffer = new byte[1024];
                numBytes = bufIS.read(buffer);
                
                if (numBytes > 0)
                {
                    String str = new String(buffer, 0, numBytes);
                    sb.append(str);
                }
            } while (numBytes > 0);
        }
        finally
        {
            if (bufIS != null) try
            {
                bufIS.close();
            }
            catch (IOException ignored)
            {
            }
        }
        return sb.toString();
    }
}