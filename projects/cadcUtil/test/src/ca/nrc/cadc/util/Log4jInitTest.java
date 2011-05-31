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
        LOG.error("error msg 2.");
        Log4jInit.setLevel("APP1", "ca.nrc.cadc", Level.DEBUG);
        LOG.debug("debug msg1.");
        LOG.debug("debug msg2.");
        LOG.debug("debug msg3.");
        LOG.debug("debug msg11.");
        LOG.debug("debug msg12.");
        LOG.debug("debug msg13.");
        
        LOG.error("error msg 2.");
        LOG.error("error msg 3.");
    }

}
