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

public class PositionTest
{
    private static final Logger log = Logger.getLogger(PositionTest.class);
    static
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }

    public PositionTest() {}

    @Test
    public void testFormat() throws Exception
    {
        log.debug("testFormat");

        String phrase = "POSITION ICRS BARYCENTER SPHERICAL2 1.0 2.0";
        Position position = new Position("ICRS", "BARYCENTER", "SPHERICAL2", 1.0, 2.0);

        String actual = STC.format(position);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testFormat passed");
    }

    @Test
    public void testFormatLowerCase() throws Exception
    {
        log.debug("testFormatLowerCase");

        String phrase = "POSITION icrs barycenter spherical2 1.0 2.0";
        Position position = new Position("icrs", "barycenter", "spherical2", 1.0, 2.0);

        String actual = STC.format(position);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testFormatLowerCase passed");
    }

    @Test
    public void testFormatMixedCase() throws Exception
    {
        log.debug("testFormatMixedCase");

        String phrase = "POSITION Icrs BaryCenter Spherical2 1.0 2.0";
        Position position = new Position("Icrs", "BaryCenter", "Spherical2", 1.0, 2.0);

        String actual = STC.format(position);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testFormatMixedCase passed");
    }

    @Test
    public void testParse() throws Exception
    {
        log.debug("parse");

        String phrase = "POSITION ICRS BARYCENTER SPHERICAL2 1.0 2.0";
        Region space = STC.parse(phrase);
        String actual = STC.format(space);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testParse passed");
    }

    @Test
    public void testParseLowerCase() throws Exception
    {
        log.debug("testParseLowerCase");

        String phrase = "position icrs barycenter spherical2 1.0 2.0";
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

        String phrase = "Position Icrs BaryCenter Spherical2 1.0 2.0";
        Region space = STC.parse(phrase);
        String actual = STC.format(space);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testParseMixedCase passed");
    }

}
