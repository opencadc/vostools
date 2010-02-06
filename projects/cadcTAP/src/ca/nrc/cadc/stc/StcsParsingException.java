
package ca.nrc.cadc.stc;

/**
 *
 * @author pdowler
 */
public class StcsParsingException extends Exception
{
    public StcsParsingException(String msg)
    {
        super(msg);
    }

    public StcsParsingException(String msg, Throwable cause)
    {
        super(msg, cause);
    }
}
