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
 * @author jeff
 */
public class NotTest
{
    public static final String SPACE = "CIRCLE";
    public static final String FRAME = "ICRS";
    public static final String REFPOS = "BARYCENTER";
    public static final String FLAVOR = "SPHERICAL2";
    public static final String COORDPAIR = "1.0 2.0";
    public static final String RADIUS = "3.0";

    public static Circle circle;
    public static String phrase;

    private static final Logger LOG = Logger.getLogger(NotTest.class);
    static
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }

    public NotTest() {}

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        phrase = "NOT ( CIRCLE ICRS 1.0 2.0 3.0 )";

        circle = new Circle();
        circle.frame = "ICRS";
        circle.coordPair = new CoordPair(1D, 2D);
        circle.radius = 3D;
    }

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testFormat()
    {
        Not not = new Not();
        not.regions = circle;

        LOG.debug("format");
        LOG.debug("expected: " + phrase);
        String actual = STC.format(not);
        LOG.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        LOG.info("testFormat passed");
    }

    @Test
    public void testParse() throws Exception
    {
        LOG.debug("parse");
        LOG.debug("expected: " + phrase);
        Not not = (Not) STC.parse(phrase);
        String actual = STC.format(not);
        LOG.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        LOG.info("testParse passed");
    }

}
