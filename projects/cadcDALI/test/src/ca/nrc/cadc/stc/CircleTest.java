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
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author jburke
 */
public class CircleTest
{
    private static final Logger log = Logger.getLogger(CircleTest.class);
    static
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }

    public CircleTest() {}

    @Test
    public void testFormatAll() throws Exception
    {
        log.debug("testFormatAll");

        Circle circle = new Circle("ICRS", "GEOCENTER", "SPHERICAL2", 1.0, 2.0, 3.0);

        String actual = STC.format(circle);
        String expected = "CIRCLE ICRS GEOCENTER SPHERICAL2 1.0 2.0 3.0";
        log.debug("expected: " + expected);
        log.debug("  actual: " + actual);
        assertEquals(expected, actual);
        log.info("testFormatAll passed");
    }

    @Test
    public void testFormatLowerCase() throws Exception
    {
        log.debug("testFormatLowerCase");

        Circle circle = new Circle("icrs", "geocenter", "spherical2", 1.0, 2.0, 3.0);

        String actual = STC.format(circle);
        String expected = "CIRCLE icrs geocenter spherical2 1.0 2.0 3.0";
        log.debug("expected: " + expected);
        log.debug("  actual: " + actual);
        assertEquals(expected, actual);
        log.info("testFormatLowerCase passed");
    }

    @Test
    public void testFormatMixedCase() throws Exception
    {
        log.debug("testFormatMixedCase");

        Circle circle = new Circle("Icrs", "GeoCenter", "Spherical2", 1.0, 2.0, 3.0);

        String actual = STC.format(circle);
        String expected = "CIRCLE Icrs GeoCenter Spherical2 1.0 2.0 3.0";
        log.debug("expected: " + expected);
        log.debug("  actual: " + actual);
        assertEquals(expected, actual);
        log.info("testFormatMixedCase passed");
    }

    @Test
    public void testFormatNone() throws Exception
    {
        log.debug("testFormatNone");

        Circle circle = new Circle(null, null, null, 1.0, 2.0, 3.0);

        String actual = STC.format(circle);
        String expected = "CIRCLE 1.0 2.0 3.0";
        log.debug("expected: " + expected);
        log.debug("  actual: " + actual);
        assertEquals(expected, actual);
        log.info("testFormatNone passed");
    }

    @Test
    public void testFormatOnlyFrame() throws Exception
    {
        log.debug("testFormatOnlyFrame");

        Circle circle = new Circle("ICRS", null, null, 1.0, 2.0, 3.0);

        String actual = STC.format(circle);
        String expected = "CIRCLE ICRS 1.0 2.0 3.0";
        log.debug("expected: " + expected);
        log.debug("  actual: " + actual);
        assertEquals(expected, actual);
        log.info("testFormatOnlyFrame passed");
    }

    @Test
    public void testFormatOnlyRefPos() throws Exception
    {
        log.debug("testFormatOnlyRefPos");

        Circle circle = new Circle(null, "GEOCENTER", null, 1.0, 2.0, 3.0);

        String actual = STC.format(circle);
        String expected = "CIRCLE GEOCENTER 1.0 2.0 3.0";
        log.debug("expected: " + expected);
        log.debug("  actual: " + actual);
        assertEquals(expected, actual);
        log.info("testFormatOnlyRefPos passed");
    }

    @Test
    public void testFormatOnlyFlavor() throws Exception
    {
        log.debug("testFormatOnlyFlavor");

        Circle circle = new Circle(null, null, "SPHERICAL2", 1.0, 2.0, 3.0);

        String actual = STC.format(circle);
        String expected = "CIRCLE SPHERICAL2 1.0 2.0 3.0";
        log.debug("expected: " + expected);
        log.debug("  actual: " + actual);
        assertEquals(expected, actual);
        log.info("testFormatOnlyFlavor passed");
    }


    @Test
    public void testParseAll() throws Exception
    {
        log.debug("testParseAll");

        String phrase = "CIRCLE ICRS GEOCENTER SPHERICAL2 1.0 2.0 3.0";
        Region space = STC.parse(phrase);
        String actual = STC.format(space);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testParseAll passed");
    }

    @Test
    public void testParseNone() throws Exception
    {
        log.debug("testParseNone");

        String phrase = "CIRCLE 1.0 2.0 3.0";
        Region space = STC.parse(phrase);
        String actual = STC.format(space);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testParseNone passed");
    }

    @Test
    public void testParseOnlyFrame() throws Exception
    {
        log.debug("testParseOnlyFrame");

        String phrase = "CIRCLE ICRS 1.0 2.0 3.0";
        Region space = STC.parse(phrase);
        String actual = STC.format(space);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testParseOnlyFrame passed");
    }

    @Test
    public void testParseOnlyRefPos() throws Exception
    {
        log.debug("testParseOnlyRefPos");

        String phrase = "CIRCLE GEOCENTER 1.0 2.0 3.0";
        Region space = STC.parse(phrase);
        String actual = STC.format(space);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testParseOnlyRefPos passed");
    }

    @Test
    public void testParseOnlyFlavor() throws Exception
    {
        log.debug("testParseOnlyFlavor");

        String phrase = "CIRCLE SPHERICAL2 1.0 2.0 3.0";
        Region space = STC.parse(phrase);
        String actual = STC.format(space);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testParseOnlyFlavor passed");
    }

    @Test
    public void testParseLowerCase() throws Exception
    {
        log.debug("testParseLowerCase");

        String phrase = "circle icrs geocenter spherical2 1.0 2.0 3.0";
        Region space = STC.parse(phrase);
        String actual = STC.format(space);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testParseLowerCase passed");
    }

    @Test
    public void testParseMixedCase() throws Exception
    {
        log.debug("testParseMixedCase");

        String phrase = "Circle Icrs GeoCenter Spherical2 1.0 2.0 3.0";
        Region space = STC.parse(phrase);
        String actual = STC.format(space);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testParseMixedCase passed");
    }
}
