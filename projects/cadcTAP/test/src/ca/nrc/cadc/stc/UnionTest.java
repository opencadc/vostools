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
 * @author jeff
 */
public class UnionTest
{
    public static String phrase;
    public static Box box;
    public static Circle circle;
    public static Polygon polygon;
    public static Position position;

    private static final Logger LOG = Logger.getLogger(UnionTest.class);
    static
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }

    public UnionTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        phrase = "Union ( Box ICRS 148.9 69.1 2.0 3.0 Circle ICRS 148.9 69.1 2.0 Polygon ICRS Position ICRS )";
        
        box = new Box();
        box.frame = "ICRS";
        box.pos = new ArrayList<Double>();
        box.pos.add(148.9);
        box.pos.add(69.1);
        box.bsize = new ArrayList<Double>();
        box.bsize.add(2.0);
        box.bsize.add(3.0);

        circle = new Circle();
        circle.frame = "ICRS";
        circle.pos = new ArrayList<Double>();
        circle.pos.add(148.9);
        circle.pos.add(69.1);
        circle.radius = 2.0;

        polygon = new Polygon();
        polygon.frame = "ICRS";

        position = new Position();
        position.frame = "ICRS";
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testFormat()
    {
        LOG.debug("format");

        Union union = new Union();
        union.spaces = new ArrayList<Space>();
        union.spaces.add(box);
        union.spaces.add(circle);
        union.spaces.add(polygon);
        union.spaces.add(position);

        String actual = STC.format(union);
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
