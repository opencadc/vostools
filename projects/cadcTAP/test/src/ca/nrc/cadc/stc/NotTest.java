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
    public static final String SPACE = "Circle";
    public static final String FILLFACTOR = "fillfactor 1.0";
    public static final String FRAME = "ICRS";
    public static final String REFPOS = "BARYCENTER";
    public static final String FLAVOR = "SPHER2";
    public static final String POS = "148.9 69.1";
    public static final String RADIUS = "2.0";
    public static final String POSITION = "Position 0.1 0.2";
    public static final String UNIT = "unit deg";
    public static final String ERROR = "Error 0.1 0.2 0.3 0.4";
    public static final String RESOLUTION = "Resolution 0.0001 0.0001 0.0003 0.0003";
    public static final String SIZE = "Size 0.5 0.5 0.67 0.67";
    public static final String PIXSIZE = "PixSize 0.00005 0.00005 0.00015 0.00015";
    public static final String VELOCITY = "VelocityInterval fillfactor 1.0 1.0 2.0 3.0 4.0";

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
        phrase = "Not ( Circle ICRS 148.9 69.1 2.0 )";

        circle = new Circle();
        circle.frame = "ICRS";
        circle.pos = new ArrayList<Double>();
        circle.pos.add(148.9);
        circle.pos.add(69.1);
        circle.radius = 2.0;
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
        not.space = circle;

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
