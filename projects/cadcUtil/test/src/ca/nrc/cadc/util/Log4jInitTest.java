package ca.nrc.cadc.util;


import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class Log4jInitTest
{
    private static final Logger LOG = Logger.getLogger(Log4jInitTest.class);

    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
        Log4jInit.setLevel("ca.nrc.cadc", Level.DEBUG);
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception
    {
    }

    @Before
    public void setUp() throws Exception
    {
    }

    @After
    public void tearDown() throws Exception
    {
    }
    
    @Test
    public void changeLayoutFormatTest()
    {
        LOG.debug("debug msg1.");
        LOG.debug("debug msg2.");
        LOG.debug("debug msg3.");
        Log4jInit.setConsoleInfoLogLayoutFormat("CHANGED %-4r [%t] %-5p %c{1} %x - %m\n");
        LOG.debug("debug msg11.");
        LOG.debug("debug msg12.");
        LOG.debug("debug msg13.");
        
        LOG.error("error msg 1.");
        LOG.error("error msg 2.");
        LOG.error("error msg 3.");
        Log4jInit.setConsoleErrorLogLayoutFormat("CHANGED AGAIN. %-4r [%t] %-5p %c{1} %x - %m\n");
        LOG.error("error msg 11.");
        LOG.error("error msg 12.");
        LOG.error("error msg 13.");
    }

}
