package ca.nrc.cadc.vos;

public class LinkingException extends VOSException
{

    private static final long serialVersionUID = -2858002089551386253L;

    /**
     * Constructor with message and cause.
     * 
     * @param message
     * @param cause
     */
    public LinkingException(String message, Throwable cause)
    {
        super(message, cause);
    }

    /**
     * Constructor with message.
     * 
     * @param message
     */
    public LinkingException(String message)
    {
        super(message);
    }
}
