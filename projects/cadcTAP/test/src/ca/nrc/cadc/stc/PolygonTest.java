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

public class PolygonTest
{
    public static final String SPACE = "Polygon";
    public static final String FILLFACTOR = "fillfactor 1.0";
    public static final String FRAME = "ICRS";
    public static final String REFPOS = "BARYCENTER";
    public static final String FLAVOR = "SPHER2";
    public static final String POS = "148.9 69.1 76.4 22.8";
    public static final String POSITION = "Position 0.1 0.2";
    public static final String UNIT = "unit deg";
    public static final String ERROR = "Error 0.1 0.2 0.3 0.4";
    public static final String RESOLUTION = "Resolution 0.0001 0.0001 0.0003 0.0003";
    public static final String SIZE = "Size 0.5 0.5 0.67 0.67";
    public static final String PIXSIZE = "PixSize 0.00005 0.00005 0.00015 0.00015";
    public static final String VELOCITY = "VelocityInterval fillfactor 1.0 1.0 2.0 3.0 4.0";

    public static String phrase;

    private static final Logger LOG = Logger.getLogger(PolygonTest.class);
    static
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }

    public PolygonTest() {}

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

        Polygon polygon = new Polygon();
        polygon.fill = 1.0D;
        polygon.frame = FRAME;
        polygon.refpos = REFPOS;
        polygon.flavor = FLAVOR;
        polygon.pos = new ArrayList<Double>();
        polygon.pos.add(148.9);
        polygon.pos.add(69.1);
        polygon.pos.add(76.4);
        polygon.pos.add(22.8);
        polygon.position = new ArrayList<Double>();
        polygon.position.add(0.1);
        polygon.position.add(0.2);
        polygon.unit = "deg";
        polygon.error = new ArrayList<Double>();
        polygon.error.add(0.1);
        polygon.error.add(0.2);
        polygon.error.add(0.3);
        polygon.error.add(0.4);
        polygon.resln = new ArrayList<Double>();
        polygon.resln.add(0.0001);
        polygon.resln.add(0.0001);
        polygon.resln.add(0.0003);
        polygon.resln.add(0.0003);
        polygon.size = new ArrayList<Double>();
        polygon.size.add(0.5);
        polygon.size.add(0.5);
        polygon.size.add(0.67);
        polygon.size.add(0.67);
        polygon.pixsiz = new ArrayList<Double>();
        polygon.pixsiz.add(0.00005);
        polygon.pixsiz.add(0.00005);
        polygon.pixsiz.add(0.00015);
        polygon.pixsiz.add(0.00015);

        polygon.velocity = new Velocity();
        polygon.velocity.intervals = new ArrayList<VelocityInterval>();

        VelocityInterval interval = new VelocityInterval();
        interval.fill = 1.0;
        interval.lolimit = new ArrayList<Double>();
        interval.lolimit.add(1.0);
        interval.lolimit.add(2.0);
        interval.hilimit = new ArrayList<Double>();
        interval.hilimit.add(3.0);
        interval.hilimit.add(4.0);

        polygon.velocity.intervals.add(interval);

        String actual = STC.format(polygon);
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
