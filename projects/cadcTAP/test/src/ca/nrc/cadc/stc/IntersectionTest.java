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
public class IntersectionTest
{
    public static String phrase;
    public static Box box;
    public static Circle circle;
    public static Polygon polygon;
    public static Position position;

    private static final Logger LOG = Logger.getLogger(IntersectionTest.class);
    static
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }

    public IntersectionTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        phrase = "INTERSECTION ICRS ( BOX 1.0 2.0 3.0 4.0 CIRCLE 1.0 2.0 3.0 POLYGON 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 POSITION 1.0 2.0 )";

        box = new Box();
        box.coordPair = new CoordPair(1D, 2D);
        box.width = 3D;
        box.height = 4D;

        circle = new Circle();
        circle.coordPair = new CoordPair(1D, 2D);
        circle.radius = 3D;

        polygon = new Polygon();
        polygon.coordPairs = new ArrayList<CoordPair>();
        polygon.coordPairs.add(new CoordPair(1D, 2D));
        polygon.coordPairs.add(new CoordPair(3D, 4D));
        polygon.coordPairs.add(new CoordPair(5D, 6D));
        polygon.coordPairs.add(new CoordPair(7D, 8D));

        position = new Position();
        position.coordPair = new CoordPair(1D, 2D);
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

        Intersection intersection = new Intersection();
        intersection.frame = "ICRS";
        intersection.regions = new ArrayList<Region>();
        intersection.regions.add(box);
        intersection.regions.add(circle);
        intersection.regions.add(polygon);
        intersection.regions.add(position);

        String actual = STC.format(intersection);
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
