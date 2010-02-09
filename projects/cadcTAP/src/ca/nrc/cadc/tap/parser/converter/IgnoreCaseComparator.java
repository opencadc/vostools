
package ca.nrc.cadc.tap.parser.converter;

import java.util.Comparator;

/**
 * Simple comparator for case-insenstive String comparison.
 * 
 * @author pdowler
 */
public class IgnoreCaseComparator implements Comparator<String>
{
    public int compare(String lhs, String rhs)
    {
        if (lhs == null && rhs == null)
            return 0;
        // null is less than non-null
        if (lhs == null)
            return -1;
        if (rhs == null)
            return 1;
        return lhs.toLowerCase().compareTo(rhs.toLowerCase());
    }
}
