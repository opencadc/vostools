// Created on 29-Aug-2006

package ca.nrc.cadc.util;

/**
 * @version $Version$
 * @author pdowler
 */
public class ArrayUtil
{
	/**
	 * Find a string in an array of specified items.
     *
	 * @param s         The value to find.
	 * @param as        The array.
     *
	 * @return          The location of s within the array, or -1 if not found.
	 */
	public static <O> int find(final O s, final O[] as)
	{
		if (isEmpty(as))
        {
			return -1;
        }
        else
        {
            for (int i = 0; i < as.length; i++)
            {
                if (s.equals(as[i]))
                {
                    return i;
                }
            }

            return -1;
        }
	}

    /**
     * Match a String in an array of Strings.
     *
     * @param regexp            The regexp string to match.
     * @param as                The array of Strings to match against.
     * @param caseInsensitive   Whether or not to perform a case insensitive
     *                          match.
     * @return          Location of regexp within the array, or -1 if not
     *                  found.
     */
    public static int matches(final String regexp, final String[] as,
                              final boolean caseInsensitive)
    {
        if (StringUtil.hasLength(regexp) && !isEmpty(as))
        {
            for (int i = 0; i < as.length; i++)
            {
                if (StringUtil.hasLength(as[i])
                    && StringUtil.matches(as[i], regexp, caseInsensitive))
                {
                    return i;
                }
            }
        }

        return -1;
    }

    /**
     * Obtain whether the given array is empty.
     * 
     * @param array     The array to check.
     * @return          True if it is empty, False otherwise.
     */
    public static <O> boolean isEmpty(final O... array)
    {
        return (array == null) || (array.length == 0);
    }

    /**
     * Obtain whether the given char array is empty.
     *
     * @param array     The array to check.
     * @return          True if it is empty, False otherwise.
     */
    public static boolean isEmpty(final char... array)
    {
        return (array == null) || (array.length == 0);
    }

    /**
     * Obtain whether the given int array is empty.
     *
     * @param array     The array to check.
     * @return          True if it is empty, False otherwise.
     */
    public static boolean isEmpty(final int... array)
    {
        return (array == null) || (array.length == 0);
    }

    /**
     * Obtain whether the given byte array is empty.
     *
     * @param array     The array to check.
     * @return          True if it is empty, False otherwise.
     */
    public static boolean isEmpty(final byte... array)
    {
        return (array == null) || (array.length == 0);
    }
}
