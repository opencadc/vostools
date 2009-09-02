/******************************************************************************
 *
 *  Copyright (C) 2009                          Copyright (C) 2009
 *  National Research Council           Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6                     Ottawa, Canada, K1A 0R6
 *  All rights reserved                         Tous droits reserves
 *
 *  NRC disclaims any warranties,       Le CNRC denie toute garantie
 *  expressed, implied, or statu-       enoncee, implicite ou legale,
 *  tory, of any kind with respect      de quelque nature que se soit,
 *  to the software, including          concernant le logiciel, y com-
 *  without limitation any war-         pris sans restriction toute
 *  ranty of merchantability or         garantie de valeur marchande
 *  fitness for a particular pur-       ou de pertinence pour un usage
 *  pose.  NRC shall not be liable      particulier.  Le CNRC ne
 *  in any event for any damages,       pourra en aucun cas etre tenu
 *  whether direct or indirect,         responsable de tout dommage,
 *  special or general, consequen-      direct ou indirect, particul-
 *  tial or incidental, arising         ier ou general, accessoire ou
 *  from the use of the software.       fortuit, resultant de l'utili-
 *                                                              sation du logiciel.
 *
 *
 *  This file is part of cadcUWS.
 *
 *  cadcUWS is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  cadcUWS is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with cadcUWS.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package ca.nrc.cadc.uws.util;


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
}