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

/**
 *
 * @author jburke
 */
public class SpectralIntervalTest
{
    private static final Logger log = Logger.getLogger(SpectralIntervalTest.class);
    static
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }

    public SpectralIntervalTest() {
    }

    @Test
    public void testNullUnit()
    {
        log.debug("testNullUnit");
        try
        {
            try
            {
                SpectralInterval interval = new SpectralInterval(1.0, 2.0, null);
                fail("null unit should have thrown IllegalArgumentException");
            }
            catch (IllegalArgumentException e)
            {
                log.debug("Null unit threw " + e.getMessage());
            }
            log.info("testNullUnit passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testFormatNull()
    {
        log.debug("testFormatNull");
        try
        {
            SpectralInterval interval = null;
            String actual = STC.format(interval);
            String expected = "";
            log.debug("expected: " + expected);
            log.debug("  actual: " + actual);
            assertEquals(expected, actual);
            log.info("testFormatNull passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testValidFormat()
    {
        log.debug("testValidFormat");
        try
        {
            SpectralInterval interval = new SpectralInterval(1000.5, 2000.5, SpectralUnit.Hz);

            String actual = STC.format(interval);
            String expected = "SpectralInterval 1000.5 2000.5 Hz";
            log.debug("expected: " + expected);
            log.debug("  actual: " + actual);
            assertEquals(expected, actual);
            log.info("testValidFormat passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testParseNullPhrase()
    {
        log.debug("testParseNullPhrase");
        try
        {
            String phrase = null;
            SpectralInterval interval = STC.parseSpectralInterval(phrase);
            assertNull("Null phrase should return null SpectralInterval", interval);
            log.info("testParseNullPhrase passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testParseEmptyPhrase()
    {
        log.debug("testParseEmptyPhrase");
        try
        {
            String phrase = "";
            SpectralInterval interval = STC.parseSpectralInterval(phrase);
            assertNull("Empty phrase should return null SpectralInterval", interval);
            log.info("testParseEmptyPhrase passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testInvalidPhrase()
    {
        log.debug("testInvalidPhrase");
        try
        {
            String phrase = "SpectralInterval Hz";
            try
            {
                SpectralInterval interval = STC.parseSpectralInterval(phrase);
                fail("Invalid phrase should throw StcsParsingException");
            }
            catch (StcsParsingException e)
            {
                log.debug("Invalid phrase threw " + e.getMessage());
            }

            phrase = "SpectralInterval 1.0";
            try
            {
                SpectralInterval interval = STC.parseSpectralInterval(phrase);
                fail("Invalid phrase limits should throw StcsParsingException");
            }
            catch (StcsParsingException e)
            {
                log.debug("Invalid phrase threw " + e.getMessage());
            }

            phrase = "SpectralInterval 1.0 2.0";
            try
            {
                SpectralInterval interval = STC.parseSpectralInterval(phrase);
                fail("Invalid phrase limits should throw StcsParsingException");
            }
            catch (StcsParsingException e)
            {
                log.debug("Invalid phrase threw " + e.getMessage());
            }

            phrase = "SpectralInterval 1.0 Hz";
            try
            {
                SpectralInterval interval = STC.parseSpectralInterval(phrase);
                fail("Invalid phrase limits should throw StcsParsingException");
            }
            catch (StcsParsingException e)
            {
                log.debug("Invalid phrase threw " + e.getMessage());
            }

            phrase = "SpectralInterval 1.0 2.0 3.0 Hz";
            try
            {
                SpectralInterval interval = STC.parseSpectralInterval(phrase);
                fail("Invalid phrase limits should throw StcsParsingException");
            }
            catch (StcsParsingException e)
            {
                log.debug("Invalid phrase threw " + e.getMessage());
            }

            log.info("testInvalidPhrase passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testParseMisspeltSpectralInterval()
    {
        log.debug("testParseMisspeltSpectralInterval");
        try
        {
            String phrase = "SpatialInterval 1000.5 2000.5 Hz";
            try
            {
                SpectralInterval interval = STC.parseSpectralInterval(phrase);
                fail("Misspelt SpectralInterval should have thrown IllegalArgumentException");
            }
            catch (StcsParsingException e)
            {
                log.debug("Misspelt SpectralInterval threw " + e.getMessage());
            }
            log.info("testParseMisspeltSpectralInterval passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testParseInvalidUnit()
    {
        log.debug("testParseInvalidUnit");
        try
        {
            // invalid unit
            String phrase = "SpectralInterval 1000.5 2000.5 lbs";
            try
            {
                SpectralInterval interval = STC.parseSpectralInterval(phrase);
                fail("Invalid unit should have thrown IllegalArgumentException");
            }
            catch (StcsParsingException e)
            {
                log.debug("Invalid unit threw " + e.getMessage());
            }

            // misspelt unit
            phrase = "SpectralInterval 1000.5 2000.5 hz";
            try
            {
                SpectralInterval interval = STC.parseSpectralInterval(phrase);
                fail("Invalid unit should have thrown IllegalArgumentException");
            }
            catch (StcsParsingException e)
            {
                log.debug("Invalid unit threw " + e.getMessage());
            }
            log.info("testParseInvalidUnit passed");
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
            String phrase = "SpectralInterval 1000.5 2000.5 Hz";
            SpectralInterval interval = STC.parseSpectralInterval(phrase);
            String actual = STC.format(interval);
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
    
}