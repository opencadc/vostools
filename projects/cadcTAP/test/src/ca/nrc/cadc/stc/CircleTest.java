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

public class CircleTest
{
    public static final String SPACE = "CIRCLE";
    public static final String FRAME = "ICRS";
    public static final String REFPOS = "BARYCENTER";
    public static final String FLAVOR = "SPHERICAL2";
    public static final String COORDPAIR = "1.0 2.0";
    public static final String RADIUS = "3.0";

    public static String phrase;

    private static final Logger LOG = Logger.getLogger(CircleTest.class);
    static
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }

    public CircleTest() {}

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        StringBuilder sb = new StringBuilder();
        sb.append(SPACE).append(" ");
        sb.append(FRAME).append(" ");
        sb.append(REFPOS).append(" ");
        sb.append(FLAVOR).append(" ");
        sb.append(COORDPAIR).append(" ");
        sb.append(RADIUS);
        phrase = sb.toString();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testFormat() throws Exception
    {
        LOG.debug("parse");

        Circle circle = new Circle();
        circle.frame = FRAME;
        circle.refpos = REFPOS;
        circle.flavor = FLAVOR;
        circle.coordPair = new CoordPair(1D, 2D);
        circle.radius = 3D;

        String actual = STC.format(circle);
        LOG.debug("expected: " + phrase);
        LOG.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        LOG.info("testFormat passed");
    }

    @Test
    public void testParse() throws Exception
    {
        LOG.debug("parse");

        Region space = STC.parse(phrase);
        String actual = STC.format(space);
        LOG.debug("expected: " + phrase);
        LOG.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        LOG.info("testParse passed");
    }

}
