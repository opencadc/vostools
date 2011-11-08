/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2010.                            (c) 2011.
 * National Research Council            Conseil national de recherches
 * Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 * All rights reserved                  Tous droits reserves
 *
 * NRC disclaims any warranties         Le CNRC denie toute garantie
 * expressed, implied, or statu-        enoncee, implicite ou legale,
 * tory, of any kind with respect       de quelque nature que se soit,
 * to the software, including           concernant le logiciel, y com-
 * without limitation any war-          pris sans restriction toute
 * ranty of merchantability or          garantie de valeur marchande
 * fitness for a particular pur-        ou de pertinence pour un usage
 * pose.  NRC shall not be liable       particulier.  Le CNRC ne
 * in any event for any damages,        pourra en aucun cas etre tenu
 * whether direct or indirect,          responsable de tout dommage,
 * special or general, consequen-       direct ou indirect, particul-
 * tial or incidental, arising          ier ou general, accessoire ou
 * from the use of the software.        fortuit, resultant de l'utili-
 *                                      sation du logiciel.
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */

package ca.nrc.cadc.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * Simple wrapper that keeps track of bytes read from the input stream. 
 * 
 * If a limit is provided, a ByteLimitExceededException will be thrown if that
 * limit is reached.
 * 
 * @version $Version$
 * @author majorb
 */
public class ByteCountInputStream extends InputStream implements ByteCounter
{
    
    private InputStream inputStream;
    private long byteCount = 0L;
    private Long byteLimit = null;

    /**
     * Constructor that takes the target input stream..
     * 
     * @param inputStream  The InputStream to wrap.
     * @param byteLimit    The quota space left to be written to, in bytes.
     */
    public ByteCountInputStream(final InputStream inputStream)
    {
        this.inputStream = inputStream;
    }
    
    /**
     * Constructor that takes the target input stream and a byte limit.
     * 
     * @param inputStream  The InputStream to wrap.
     * @param byteLimit    The limit to the number of bytes to read.
     */
    public ByteCountInputStream(final InputStream inputStream,
                                final long byteLimit)
    {
        this.inputStream = inputStream;
        if (byteLimit > 0)
            this.byteLimit = byteLimit;
    }
    
    /**
     * Return the number of bytes that were read.
     */
    @Override
    public long getByteCount()
    {
        return byteCount;
    }

    /**
     * The quota space left to be written to.
     *
     * @return      Quota space left, in bytes.
     */
    public Long getByteLimit()
    {
        return byteLimit;
    }

    @Override
    public int available() throws IOException
    {
        return inputStream.available();
    }
    
    @Override
    public void close() throws IOException
    {
        inputStream.close();
    }
    
    @Override
    public void mark(int readlimit)
    {
        inputStream.mark(readlimit);
    }
    
    @Override
    public boolean markSupported()
    {
        return inputStream.markSupported();
    }

    @Override
    public int read() throws IOException
    {
        if (hasReachedLimit())
            throw new ByteLimitExceededException(byteLimit);

        int value = inputStream.read();
        byteCount++;
        return value;
    }

    @Override
    public int read(byte[] b) throws IOException
    {
        if (hasReachedLimit())
            throw new ByteLimitExceededException(byteLimit);

        int bytesRead = inputStream.read(b);
        if (bytesRead != -1)
            byteCount += bytesRead;

        return bytesRead;
    }
    
    @Override
    public int read(byte[] b, int off, int len) throws IOException
    {
        if (hasReachedLimit())
            throw new ByteLimitExceededException(byteLimit);
               
        int bytesRead = inputStream.read(b, off, len);
        if (bytesRead != -1)
            byteCount += bytesRead;
        
        return bytesRead;
    }

    /**
     * Reset this stream, if supported.
     *
     * @throws IOException  If not supported, or something went wrong.
     *
     */
    @Override
    public void reset() throws IOException
    {
        inputStream.reset();
        byteCount = 0;
    }
    
    @Override
    public long skip(long n) throws IOException
    {
        return inputStream.skip(n);
    }

    /**
     * Obtain whether the byte limit has been reached.
     */
    private boolean hasReachedLimit()
    {
        if (byteLimit == null)
            return false;
        return byteCount >= byteLimit;
    }
}
