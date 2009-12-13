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
        sb.append(FILLFACTOR).append(" ");
        sb.append(FRAME).append(" ");
        sb.append(REFPOS).append(" ");
        sb.append(FLAVOR).append(" ");
        sb.append(POS).append(" ");
        sb.append(RADIUS).append(" ");
        sb.append(POSITION).append(" ");
        sb.append(UNIT).append(" ");
        sb.append(ERROR).append(" ");
        sb.append(RESOLUTION).append(" ");
        sb.append(SIZE).append(" ");
        sb.append(PIXSIZE).append(" ");
        sb.append(VELOCITY);
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
        circle.fill = 1.0D;
        circle.frame = FRAME;
        circle.refpos = REFPOS;
        circle.flavor = FLAVOR;
        circle.pos = new ArrayList<Double>();
        circle.pos.add(148.9);
        circle.pos.add(69.1);
        circle.radius = 2.0;
        circle.position = new ArrayList<Double>();
        circle.position.add(0.1);
        circle.position.add(0.2);
        circle.unit = "deg";
        circle.error = new ArrayList<Double>();
        circle.error.add(0.1);
        circle.error.add(0.2);
        circle.error.add(0.3);
        circle.error.add(0.4);
        circle.resln = new ArrayList<Double>();
        circle.resln.add(0.0001);
        circle.resln.add(0.0001);
        circle.resln.add(0.0003);
        circle.resln.add(0.0003);
        circle.size = new ArrayList<Double>();
        circle.size.add(0.5);
        circle.size.add(0.5);
        circle.size.add(0.67);
        circle.size.add(0.67);
        circle.pixsiz = new ArrayList<Double>();
        circle.pixsiz.add(0.00005);
        circle.pixsiz.add(0.00005);
        circle.pixsiz.add(0.00015);
        circle.pixsiz.add(0.00015);

        circle.velocity = new Velocity();
        circle.velocity.intervals = new ArrayList<VelocityInterval>();

        VelocityInterval interval = new VelocityInterval();
        interval.fill = 1.0;
        interval.lolimit = new ArrayList<Double>();
        interval.lolimit.add(1.0);
        interval.lolimit.add(2.0);
        interval.hilimit = new ArrayList<Double>();
        interval.hilimit.add(3.0);
        interval.hilimit.add(4.0);

        circle.velocity.intervals.add(interval);

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

        Space space = STC.parse(phrase);
        String actual = STC.format(space);
        LOG.debug("expected: " + phrase);
        LOG.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        LOG.info("testParse passed");
    }

}
