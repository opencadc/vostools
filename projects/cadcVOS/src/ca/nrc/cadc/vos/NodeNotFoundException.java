package ca.nrc.cadc.vos;

public class NodeNotFoundException extends Exception
{

    private static final long serialVersionUID = -3911716636812923950L;

    /**
     * Constructor with message and cause.
     * 
     * @param message
     * @param cause
     */
    public NodeNotFoundException(String message, Throwable cause)
    {
        super(message, cause);
    }

    /**
     * Constructor with message.
     * 
     * @param message
     */
    public NodeNotFoundException(String message)
    {
        super(message);
    }

}
