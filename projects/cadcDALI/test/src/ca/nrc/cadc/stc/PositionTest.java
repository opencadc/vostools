/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.nrc.cadc.stc;

import ca.nrc.cadc.util.Log4jInit;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
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
    public void testFormatValid()
    {
        log.debug("testFormatValid");
        try
        {
            Position position = new Position(Frame.ICRS, ReferencePosition.BARYCENTER, Flavor.SPHERICAL2, 1.0, 2.0);
            String expected = "Position ICRS BARYCENTER SPHERICAL2 1.0 2.0";

            String actual = STC.format(position);
            log.debug("expected: " + expected);
            log.debug("  actual: " + actual);
            assertEquals(expected, actual);
            log.info("testFormatValid passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testParse()
    {
        log.debug("testParse");
        try
        {
            String phrase = "Position ICRS BARYCENTER SPHERICAL2 1.0 2.0";
            Region space = STC.parse(phrase);
            String actual = STC.format(space);
            log.debug("expected: " + phrase);
            log.debug("  actual: " + actual);
            assertEquals(phrase, actual);
            log.info("testParse passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testParseLowerCase()
    {
        log.debug("testParseLowerCase");
        try
        {
            String phrase = "position icrs barycenter spherical2 1.0 2.0";
            String expected = "Position ICRS BARYCENTER SPHERICAL2 1.0 2.0";
            Region space = STC.parse(phrase);
            String actual = STC.format(space);
            log.debug("expected: " + expected);
            log.debug("  actual: " + actual);
            assertEquals(expected, actual);
            log.info("testParseLowerCase passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testParseMixedCase()
    {
        log.debug("testParseMixedCase");
        try
        {
            String phrase = "Position Icrs BaryCenter Spherical2 1.0 2.0";
            String expected = "Position ICRS BARYCENTER SPHERICAL2 1.0 2.0";
            Region space = STC.parse(phrase);
            String actual = STC.format(space);
            log.debug("expected: " + expected);
            log.debug("  actual: " + actual);
            assertEquals(expected, actual);
            log.info("testParseMixedCase passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testInvalidPositionPhrase()
    {
        log.debug("testInvalidPositionPhrase");
        try
        {
            try
            {
                String phrase = "Position";
                Region space = STC.parse(phrase);
                fail("Invalid phrase should throw StcsParsingException");
            }
            catch (StcsParsingException e)
            {
                log.debug("Invalid phrase threw exception " + e.getMessage());
            }

            try
            {
                String phrase = "Position 1.0";
                Region space = STC.parse(phrase);
                fail("Invalid phrase should throw StcsParsingException");
            }
            catch (StcsParsingException e)
            {
                log.debug("Invalid phrase threw exception " + e.getMessage());
            }

            try
            {
                String phrase = "Position ICRS";
                Region space = STC.parse(phrase);
                fail("Invalid phrase should throw StcsParsingException");
            }
            catch (StcsParsingException e)
            {
                log.debug("Invalid phrase threw exception " + e.getMessage());
            }

            log.info("testInvalidPositionPhrase passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }
    
}
