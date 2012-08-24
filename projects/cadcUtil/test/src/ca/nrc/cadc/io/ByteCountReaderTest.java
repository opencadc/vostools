package ca.nrc.cadc.io;

import java.io.CharArrayReader;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

public class ByteCountReaderTest
{
    
    static char[] data;
    
    static
    {
        data = new char[100];
        Arrays.fill(data, 'a');
    }
    
    @Test
    public void testRead() throws Exception
    {
        Reader reader = new CharArrayReader(data);
        ByteCountReader byteCountReader = new ByteCountReader(reader, 10);
        for (int i=0; i<10; i++)
        {
            byteCountReader.read();
        }
        
        try
        {
            byteCountReader.read();
            Assert.fail("Should have received byte limit exceeded exception.");
        }
        catch (ByteLimitExceededException e)
        {
            // expected
        }
    }
    
    @Test
    public void testReadCharBuf() throws Exception
    {
        Reader reader = new CharArrayReader(data);
        ByteCountReader byteCountReader = new ByteCountReader(reader, 10);
        char[] cbuf = new char[5];
        for (int i=0; i<2; i++)
        {
            byteCountReader.read(cbuf);
        }
        
        try
        {
            byteCountReader.read(cbuf);
            Assert.fail("Should have received byte limit exceeded exception.");
        }
        catch (ByteLimitExceededException e)
        {
            // expected
        }
    }
    
    @Test
    public void testReadCharacterBufffer() throws Exception
    {
        Reader reader = new CharArrayReader(data);
        ByteCountReader byteCountReader = new ByteCountReader(reader, 10);
        CharBuffer charBuf = CharBuffer.allocate(5);
        for (int i=0; i<2; i++)
        {
            byteCountReader.read(charBuf);
            charBuf.clear();
        }
        
        try
        {
            byteCountReader.read(charBuf);
            Assert.fail("Should have received byte limit exceeded exception.");
        }
        catch (ByteLimitExceededException e)
        {
            // expected
        }
    }
    
    @Test
    public void testReadCharBufWithOffAndLen() throws Exception
    {
        Reader reader = new CharArrayReader(data);
        ByteCountReader byteCountReader = new ByteCountReader(reader, 10);
        char[] cbuf = new char[5];
        for (int i=0; i<2; i++)
        {
            byteCountReader.read(cbuf, 0, 5);
        }
        
        try
        {
            byteCountReader.read(cbuf, 0, 5);
            Assert.fail("Should have received byte limit exceeded exception.");
        }
        catch (ByteLimitExceededException e)
        {
            // expected
        }
    }

}
