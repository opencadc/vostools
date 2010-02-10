/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.nrc.cadc.stc;

import ca.nrc.cadc.util.Log4jInit;
import java.util.ArrayList;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author jburke
 */
public class NotTest
{
    private static final Logger log = Logger.getLogger(NotTest.class);
    static
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }

    public NotTest() {}

    @Test
    public void testFormat()
    {
        log.debug("testFormat");

        String phrase = "NOT ( CIRCLE ICRS 1.0 2.0 3.0 )";
        Region circle = new Circle("ICRS", 1.0, 2.0, 3.0);
        Not not = new Not(circle);

        log.debug("expected: " + phrase);
        String actual = STC.format(not);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testFormat passed");
    }

    @Test
    public void testFormatLowerCase()
    {
        log.debug("testFormatLowerCase");

        String phrase = "NOT ( CIRCLE icrs 1.0 2.0 3.0 )";
        Region circle = new Circle("icrs", 1.0, 2.0, 3.0);
        Not not = new Not(circle);

        log.debug("expected: " + phrase);
        String actual = STC.format(not);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testFormatLowerCase passed");
    }

    @Test
    public void testFormatMixedCase()
    {
        log.debug("testFormatMixedCase");

        String phrase = "NOT ( CIRCLE Icrs 1.0 2.0 3.0 )";
        Region circle = new Circle("Icrs", 1.0, 2.0, 3.0);
        Not not = new Not(circle);

        log.debug("expected: " + phrase);
        String actual = STC.format(not);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testFormatMixedCase passed");
    }

    @Test
    public void testParse() throws Exception
    {
        log.debug("testParse");

        String phrase = "NOT ( CIRCLE ICRS 1.0 2.0 3.0 )";

        log.debug("expected: " + phrase);
        Not not = (Not) STC.parse(phrase);
        String actual = STC.format(not);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testParse passed");
    }

    @Test
    public void testParseLowerCase() throws Exception
    {
        log.debug("testParseLowerCase");

        String phrase = "not ( circle icrs 1.0 2.0 3.0 )";

        log.debug("expected: " + phrase);
        Not not = (Not) STC.parse(phrase);
        String actual = STC.format(not);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testParseLowerCase passed");
    }

    @Test
    public void testParseMixedCase() throws Exception
    {
        log.debug("testParseMixedCase");

        String phrase = "Not ( Circle Icrs 1.0 2.0 3.0 )";

        log.debug("expected: " + phrase);
        Not not = (Not) STC.parse(phrase);
        String actual = STC.format(not);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testParseMixedCase passed");
    }

}
