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
 */
public class VelocityTest
{
    public static final String VELOCITYINTERVAL_1 = "VelocityInterval fillfactor 1.0 1.0 2.0 3.0 4.0";
    public static final String VELOCITYINTERVAL_2 = "VelocityInterval fillfactor 0.5 1.5 2.5 3.5 4.5";
    public static final String VELOCITY = "Velocity 10.1";
    public static final String UNIT = "unit m/s";
    public static final String ERROR = "Error 0.1 0.2";
    public static final String RESOLUTION = "Resolution 2.1 3.1";
    public static final String PIXSIZE = "PixSize 4.2 5.2";

    public static String phrase;

    private static final Logger LOG = Logger.getLogger(VelocityTest.class);
    static
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }

    public VelocityTest() {}

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        StringBuilder sb = new StringBuilder();
        sb.append(VELOCITYINTERVAL_1).append(" ");
        sb.append(VELOCITYINTERVAL_2).append(" ");
        sb.append(VELOCITY).append(" ");
        sb.append(UNIT).append(" ");
        sb.append(ERROR).append(" ");
        sb.append(RESOLUTION).append(" ");
        sb.append(PIXSIZE);
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
        LOG.debug("format");

        Velocity velocity = new Velocity();
        velocity.intervals = new ArrayList<VelocityInterval>();

        VelocityInterval interval1 = new VelocityInterval();
        interval1.fill = 1.0;
        interval1.lolimit = new ArrayList<Double>();
        interval1.lolimit.add(1.0);
        interval1.lolimit.add(2.0);
        interval1.hilimit = new ArrayList<Double>();
        interval1.hilimit.add(3.0);
        interval1.hilimit.add(4.0);

        VelocityInterval interval2 = new VelocityInterval();
        interval2.fill = 0.5;
        interval2.lolimit = new ArrayList<Double>();
        interval2.lolimit.add(1.5);
        interval2.lolimit.add(2.5);
        interval2.hilimit = new ArrayList<Double>();
        interval2.hilimit.add(3.5);
        interval2.hilimit.add(4.5);

        velocity.intervals.add(interval1);
        velocity.intervals.add(interval2);

        velocity.vel = 10.1;
        velocity.unit = "m/s";
        velocity.error = new ArrayList<Double>();
        velocity.error.add(0.1);
        velocity.error.add(0.2);
        velocity.resln = new ArrayList<Double>();
        velocity.resln.add(2.1);
        velocity.resln.add(3.1);
        velocity.pixsiz = new ArrayList<Double>();
        velocity.pixsiz.add(4.2);
        velocity.pixsiz.add(5.2);

        String actual = STC.format(velocity);
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
