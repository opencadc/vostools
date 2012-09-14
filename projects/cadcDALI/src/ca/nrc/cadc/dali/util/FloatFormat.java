/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.nrc.cadc.dali.util;

/**
 * Formats a Float into a String.
 *
 */
public class FloatFormat implements Format<Float>
{
    /**
     * Takes the passed in Float and returns the String representation of that Float.
     * If the Float is null an empty String is returned.
     *
     * @param object to format
     * @return String representation of the Object
     */
    public String format(Float object)
    {
        if (object == null)
        {
            return "";
        }
        return object.toString();
    }

    /**
     * Parses a String to a Float.
     *
     * @param s the String to parse.
     * @return Float value of the String.
     */
    public Float parse(String s)
    {
        if (s == null || s.isEmpty())
        {
            return null;
        }
        return Float.valueOf(s);
    }
    
}
