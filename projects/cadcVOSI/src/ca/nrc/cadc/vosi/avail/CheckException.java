
package ca.nrc.cadc.vosi.avail;

/**
 * Exception thrown by the run() method of
 * @author pdowler
 */
public class CheckException extends Exception
{
    private static final long serialVersionUID = 201003271230L;

    public CheckException(String msg)
    {
        this(msg, null);
    }
    public CheckException(String msg, Throwable cause)
    {
        super(msg, cause);
    }

    @Override
    public String getMessage()
    {
        if (super.getCause() != null)
            return super.getMessage() + ", cause: " + super.getCause();
        return super.getMessage();
    }
}
