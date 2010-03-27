
package ca.nrc.cadc.vosi.avail;

/**
 * Intrface for clases that check for the correct function of a resource.
 * 
 * @author pdowler
 */
public interface CheckResource
{
    public void check()
        throws CheckException;
}
