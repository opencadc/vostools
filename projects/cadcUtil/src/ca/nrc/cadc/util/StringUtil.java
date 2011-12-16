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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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
     * @see java.lang.Character#isWhitespace
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
     * Method that mimics a Shell parsing the command line of an application.
     * The String command line is parsed and tokenized based on whitespaces.
     * Similar to a shell, consideration is given to special characters such
     * as ' and \. \ escapes the immediately following whitespace or ' and
     * characters inside '' have no special meanings.
     * @param toParse command line string to parse
     * @return set of tokens representing the command line arguments.
     */
    public static String[] parseCmdLineStr(final String toParse)
    {
        ArrayList<String> tokens = new ArrayList<String>();
        int currentCharIndex = 0;
        StringBuffer buffer = new StringBuffer();
        char previous; // previous char
        char current = ' ';
        String thisToParse = toParse.trim();
        boolean insideQuotes = false;
        while( currentCharIndex < thisToParse.length() )
        {
            previous = current;
            current = thisToParse.charAt(currentCharIndex++);
            
            if(insideQuotes)
            {
                if(current=='\'')
                {
                    insideQuotes = false;
                }
                else
                {
                    buffer.append(current);
                }
            }
            else
            {
                if((current==' ') || (current=='\t'))
                {
                    if(previous!='\\')
                    {
                        // end of an argument
                        tokens.add(buffer.toString());
                        buffer = new StringBuffer();
                    }
                    else
                    {
                        // space is escaped - treat it as a whitespace and
                        // replace the previous /
                        buffer.setCharAt(buffer.length()-1, current);
                    }
                }
                else if (current=='\'')
                {
                    if(previous!='\\')
                    {
                        // start quotes
                        insideQuotes = true;
                    }
                    else
                    {
                        // ' esaped - replace the previous \
                        buffer.setCharAt(buffer.length()-1, current);
                    }
                }
                else
                {
                    buffer.append(current);
                }
            }        
        }
        if(buffer.length() > 0)
        {
            tokens.add(buffer.toString());
        }
        
        return tokens.toArray(new String[tokens.size()]);
    }
    
    /**
     * List elements of a string array.
     * 
     * @param strArr String array.
     * @return all elements in the format as ["str1", "str2", ...]
     * @author zhangsa
     */
    public static String toString(String[] strArr)
    {
        if (strArr == null)
            return "";
        
        String deli = "";
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (String str : strArr)
        {
            sb.append(deli).append("\"").append(str).append("\"");
            if ("".equals(deli)) deli = ", ";
        }
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * Get a string which is the N-time repeat of the input string, i.e. repeat("ab", 3) => "ababab".
     * 
     * @param str
     * @param num
     * @return
     * @author zhangsa
     */
    public static String repeat(String str, int num)
    {
        if (num <= 0 || str == null) return "";
        StringBuffer sb = new StringBuffer();
        for (int i = num; i-- > 0;)
            sb.append(str);
        return sb.toString();
    }

    /**
     * Read an input stream into a string.  The input cannot be of binary contents.
     *
     * @param inputStream
     * @param charsetName
     * @return String
     * @throws IOException
     * @author zhangsa
     */
    public static String readFromInputStream(InputStream inputStream, String charsetName)
        throws IOException
    {
        StringBuffer sb = new StringBuffer();
        BufferedReader buf = null;
        try
        {
            buf = new BufferedReader(new InputStreamReader(inputStream, charsetName));
            String line = buf.readLine();
            while(line != null)
            {
                sb.append(line);
                sb.append("\n");
                line = buf.readLine();
            }
        }
        finally
        {
            if (buf != null) try
            {
                buf.close();
            }
            catch (IOException ignored)
            {
            }
        }
        return sb.toString();
    }

    /**
     * Obtain whether the input matches the given regexp.
     *
     * @param input             The String to match against.
     * @param regexp            The regexp to calculate the match.
     * @param caseInsensitive   Whether it's a case insensitive match or not.
     * @return                  True if it matches, false otherwise.
     */
    public static boolean matches(final String input, final String regexp,
                                  final boolean caseInsensitive)
    {
        final Pattern p;

        if (caseInsensitive)
        {
            p = Pattern.compile(".*" + regexp + ".*", Pattern.CASE_INSENSITIVE);
        }
        else
        {
            p = Pattern.compile(".*" + regexp + ".*");
        }

        final Matcher m = p.matcher(input);
        return m.matches();
    }
}
