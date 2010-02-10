/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.nrc.cadc.stc;

import ca.nrc.cadc.util.Log4jInit;
import java.util.ArrayList;
import java.util.List;
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
 * @author jburke
 */
public class IntersectionTest
{
    public static Box box;
    public static Circle circle;
    public static Polygon polygon;
    public static Position position;

    private static final Logger log = Logger.getLogger(IntersectionTest.class);
    static
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }

    public IntersectionTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        box = new Box(null, 1.0, 2.0, 3.0, 4.0);

        circle = new Circle(null, 1.0, 2.0, 3.0);

        List<CoordPair> coordPairs = new ArrayList<CoordPair>();
        coordPairs.add(new CoordPair(1.0, 2.0));
        coordPairs.add(new CoordPair(3.0, 4.0));
        coordPairs.add(new CoordPair(5.0, 6.0));
        coordPairs.add(new CoordPair(7.0, 8.0));
        polygon = new Polygon(null, coordPairs);

        position = new Position(null, 1.0, 2.0);
    }

    @Test
    public void testFormat()
    {
        log.debug("testFormat");

        String phrase = "INTERSECTION ICRS ( BOX 1.0 2.0 3.0 4.0 CIRCLE 1.0 2.0 3.0 POLYGON 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 POSITION 1.0 2.0 )";
        List<Region> regions = new ArrayList<Region>();
        regions.add(box);
        regions.add(circle);
        regions.add(polygon);
        regions.add(position);
        Intersection intersection = new Intersection("ICRS", regions);

        String actual = STC.format(intersection);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testFormat passed");
    }

    @Test
    public void testFormatLowerCase()
    {
        log.debug("testFormatLowerCase");

        String phrase = "INTERSECTION Icrs ( BOX 1.0 2.0 3.0 4.0 CIRCLE 1.0 2.0 3.0 POLYGON 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 POSITION 1.0 2.0 )";
        List<Region> regions = new ArrayList<Region>();
        regions.add(box);
        regions.add(circle);
        regions.add(polygon);
        regions.add(position);
        Intersection intersection = new Intersection("Icrs", regions);

        String actual = STC.format(intersection);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testFormatLowerCase passed");
    }

    @Test
    public void testParse() throws Exception
    {
        log.debug("testParse");

        String  phrase = "INTERSECTION ICRS ( BOX 1.0 2.0 3.0 4.0 CIRCLE 1.0 2.0 3.0 POLYGON 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 POSITION 1.0 2.0 )";
        Region space = STC.parse(phrase);
        String actual = STC.format(space);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testParse passed");
    }

    @Test
    public void testParseLowerCase() throws Exception
    {
        log.debug("testParseLowerCase");

        String  phrase = "intersection icrs ( box 1.0 2.0 3.0 4.0 circle 1.0 2.0 3.0 polygon 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 position 1.0 2.0 )";
        Region space = STC.parse(phrase);
        String actual = STC.format(space);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testParseLowerCase passed");
    }

    @Test
    public void testParseMixedCase() throws Exception
    {
        log.debug("testParseMixedCase");

        String  phrase = "Intersection Icrs ( Box 1.0 2.0 3.0 4.0 Circle 1.0 2.0 3.0 Polygon 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 Position 1.0 2.0 )";
        Region space = STC.parse(phrase);
        String actual = STC.format(space);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testParseMixedCase passed");
    }
}
