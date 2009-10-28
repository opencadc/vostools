/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.nrc.cadc.tap.writer.formatter;

/**
 * Takes a
 * String, Boolean, Byte, Character, Double, Float, Long, or Short
 * object, and returns the String representation.
 */
public class DefaultFormatter implements Formatter
{
    /**
     * Takes the passed in Object and returns the String representation of that Object.
     * If the Object is not a:
     * String, Boolean, Byte, Character, Double, Float, Long, or Short,
     * an empty String is returned.
     *
     * @param object to format
     * @return String representation of the Object
     */
    public String format(Object object)
    {
        if (object == null)
            return "";
        return object.toString();
    }
    
}
