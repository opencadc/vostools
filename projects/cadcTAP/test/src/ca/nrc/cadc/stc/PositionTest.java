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
    public static final String SPACE = "Position";
    public static final String FRAME = "ICRS";
    public static final String REFPOS = "BARYCENTER";
    public static final String FLAVOR = "SPHER2";
    public static final String POS = "148.9 69.1";
    public static final String UNIT = "unit deg";
    public static final String ERROR = "Error 0.1 0.2 0.3 0.4";
    public static final String RESOLUTION = "Resolution 0.0001 0.0001 0.0003 0.0003";
    public static final String SIZE = "Size 0.5 0.5 0.67 0.67";
    public static final String PIXSIZE = "PixSize 0.00005 0.00005 0.00015 0.00015";
    public static final String VELOCITY = "VelocityInterval fillfactor 1.0 1.0 2.0 3.0 4.0";

    public static String phrase;

    private static Logger LOG = Logger.getLogger(PositionTest.class);
    static
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }

    public PositionTest() {}

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        StringBuilder sb = new StringBuilder();
        sb.append(SPACE).append(" ");
        sb.append(FRAME).append(" ");
        sb.append(REFPOS).append(" ");
        sb.append(FLAVOR).append(" ");
        sb.append(POS).append(" ");
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

        Position position = new Position();
        position.fill = 1.0D;
        position.frame = FRAME;
        position.refpos = REFPOS;
        position.flavor = FLAVOR;
        position.pos = new ArrayList<Double>();
        position.pos.add(148.9);
        position.pos.add(69.1);
        position.unit = "deg";
        position.error = new ArrayList<Double>();
        position.error.add(0.1);
        position.error.add(0.2);
        position.error.add(0.3);
        position.error.add(0.4);
        position.resln = new ArrayList<Double>();
        position.resln.add(0.0001);
        position.resln.add(0.0001);
        position.resln.add(0.0003);
        position.resln.add(0.0003);
        position.size = new ArrayList<Double>();
        position.size.add(0.5);
        position.size.add(0.5);
        position.size.add(0.67);
        position.size.add(0.67);
        position.pixsiz = new ArrayList<Double>();
        position.pixsiz.add(0.00005);
        position.pixsiz.add(0.00005);
        position.pixsiz.add(0.00015);
        position.pixsiz.add(0.00015);

        position.velocity = new Velocity();
        position.velocity.intervals = new ArrayList<VelocityInterval>();

        VelocityInterval interval = new VelocityInterval();
        interval.fill = 1.0;
        interval.lolimit = new ArrayList<Double>();
        interval.lolimit.add(1.0);
        interval.lolimit.add(2.0);
        interval.hilimit = new ArrayList<Double>();
        interval.hilimit.add(3.0);
        interval.hilimit.add(4.0);

        position.velocity.intervals.add(interval);

        String actual = STC.format(position);
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
