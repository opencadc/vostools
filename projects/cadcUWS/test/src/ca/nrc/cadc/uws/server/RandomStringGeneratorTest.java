
package ca.nrc.cadc.uws.server;

import ca.nrc.cadc.util.Log4jInit;
import java.util.HashSet;
import java.util.Set;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author pdowler
 */
public class RandomStringGeneratorTest
{
    private static Logger log = Logger.getLogger(RandomStringGeneratorTest.class);

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.uws", Level.INFO);
    }

    @Test
    public void testGenID()
    {
        try
        {
            int len = 13;
            RandomStringGenerator gen1 = new RandomStringGenerator(len);
            RandomStringGenerator gen2 = new RandomStringGenerator(len);

            Set<String> ids = new HashSet<String>();
            for (int i=0; i<10000; i++)
            {
                ids.add(gen1.getID());
                ids.add(gen2.getID());
            }
            log.debug("testGenerateID_NoSeed: generated " + ids.size() + " unique ID strings");
            Assert.assertEquals("detect collision", 20000, ids.size());
            for (String id : ids)
            {
                log.debug("generated ID: " + id);
                Assert.assertTrue("length=="+len, id.length() == len);
                Assert.assertTrue("starts with letter", Character.isLetter(id.charAt(0)));
            }
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testGenMixedCaseID()
    {
        try
        {
            int len = 8;
            String lower = "abcdefghijklmnopqrstuvwxyz";
            String upper = lower.toUpperCase();
            String allowedChars = lower + upper + "1234567890";
            RandomStringGenerator gen1 = new RandomStringGenerator(len, allowedChars);
            RandomStringGenerator gen2 = new RandomStringGenerator(len, allowedChars);

            Set<String> ids = new HashSet<String>();
            for (int i=0; i<10000; i++)
            {
                ids.add(gen1.getID());
                ids.add(gen2.getID());
            }
            log.debug("testGenerateID_NoSeed: generated " + ids.size() + " unique ID strings");
            Assert.assertEquals("detect collision", 20000, ids.size());
            for (String id : ids)
            {
                log.debug("generated ID: " + id);
                Assert.assertTrue("length=="+len, id.length() == len);
                Assert.assertTrue("starts with letter", Character.isLetter(id.charAt(0)));
            }
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
}
