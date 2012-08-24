package ca.nrc.cadc.io;

import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;

/**
 * A reader that counts bytes and optionally imposes a limit on the number
 * that can be read.
 * 
 * @author majorb
 *
 */
public class ByteCountReader extends Reader implements ByteCounter
{
    
    private Reader reader;
    private long byteCount = 0L;
    private Long byteLimit = null;
    
    public ByteCountReader(final Reader reader)
    {
        this.reader = reader;
    }
    
    public ByteCountReader(final Reader reader, final long byteLimit)
    {
        this.reader = reader;
        if (byteLimit > 0)
            this.byteLimit = byteLimit;
    }
    
    @Override
    public int read() throws IOException
    {
        if (hasReachedLimit())
            throw new ByteLimitExceededException(byteLimit);

        int value = reader.read();
        byteCount++;
        return value;
    }
    
    @Override
    public int read(char[] cbuf) throws IOException
    {
        if (hasReachedLimit())
            throw new ByteLimitExceededException(byteLimit);
        
        int charsRead = reader.read(cbuf);
        
        if (charsRead != -1)
            byteCount += charsRead;
        
        return charsRead;
    }
    
    @Override
    public int read(char[] cbuf, int off, int len) throws IOException
    {
        if (hasReachedLimit())
            throw new ByteLimitExceededException(byteLimit);
               
        int charsRead = reader.read(cbuf, off, len);
        if (charsRead != -1)
            byteCount += charsRead;
        
        return charsRead;
    }
    
    @Override
    public int read(CharBuffer target) throws IOException
    {
        if (hasReachedLimit())
            throw new ByteLimitExceededException(byteLimit);
        
        int charsRead = reader.read(target);
        
        if (charsRead != -1)
            byteCount += charsRead;
        
        return charsRead;
    }

    @Override
    public long getByteCount()
    {
        return byteCount;
    }
    
    @Override
    public void mark(int readAheadLimit) throws IOException
    {
        reader.mark(readAheadLimit);
    }
    
    @Override
    public boolean markSupported()
    {
        return reader.markSupported();
    }

    @Override
    public void close() throws IOException
    {
        reader.close();
    }
    
    @Override
    public boolean ready() throws IOException
    {
        return reader.ready();
    }
            
    
    @Override
    public void reset() throws IOException
    {
        reader.reset();
    }
    
    @Override
    public long skip(long n) throws IOException
    {
        return reader.skip(n);
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
