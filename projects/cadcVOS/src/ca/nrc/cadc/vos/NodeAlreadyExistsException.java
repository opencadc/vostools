package ca.nrc.cadc.vos;

public class NodeAlreadyExistsException extends Exception
{

    private static final long serialVersionUID = -8893663061676929962L;

    /**
     * Constructor with message and cause.
     * 
     * @param message
     * @param cause
     */
    public NodeAlreadyExistsException(String message, Throwable cause)
    {
        super(message, cause);
    }

    /**
     * Constructor with message.
     * 
     * @param message
     */
    public NodeAlreadyExistsException(String message)
    {
        super(message);
    }

}
