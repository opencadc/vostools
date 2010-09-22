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
 * @author jeff
 */
public class UnionTest
{
    public static Box box;
    public static Circle circle;
    public static Polygon polygon;
    public static Position position;

    private static final Logger log = Logger.getLogger(UnionTest.class);
    static
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }

    public UnionTest() {
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

        String phrase = "UNION ICRS ( BOX 1.0 2.0 3.0 4.0 CIRCLE 1.0 2.0 3.0 POLYGON 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 POSITION 1.0 2.0 )";

        List<Region> regions = new ArrayList<Region>();
        regions.add(box);
        regions.add(circle);
        regions.add(polygon);
        regions.add(position);
        Union union = new Union("ICRS", null, null, regions);

        String actual = STC.format(union);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testFormat passed");
    }

    @Test
    public void testFormatLowerCase()
    {
        log.debug("testFormatLowerCase");

        String phrase = "UNION icrs ( BOX 1.0 2.0 3.0 4.0 CIRCLE 1.0 2.0 3.0 POLYGON 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 POSITION 1.0 2.0 )";

        List<Region> regions = new ArrayList<Region>();
        regions.add(box);
        regions.add(circle);
        regions.add(polygon);
        regions.add(position);
        Union union = new Union("icrs", null, null, regions);

        String actual = STC.format(union);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testFormatLowerCase passed");
    }

    @Test
    public void testFormatMixedCase()
    {
        log.debug("testFormatMixedCase");

        String phrase = "UNION Icrs ( BOX 1.0 2.0 3.0 4.0 CIRCLE 1.0 2.0 3.0 POLYGON 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 POSITION 1.0 2.0 )";

        List<Region> regions = new ArrayList<Region>();
        regions.add(box);
        regions.add(circle);
        regions.add(polygon);
        regions.add(position);
        Union union = new Union("Icrs", null, null, regions);

        String actual = STC.format(union);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testFormatMixedCase passed");
    }

    @Test
    public void testParse() throws Exception
    {
        log.debug("testParse");

        String phrase = "UNION ICRS ( BOX 1.0 2.0 3.0 4.0 CIRCLE 1.0 2.0 3.0 POLYGON 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 POSITION 1.0 2.0 )";
        
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

        String phrase = "union icrs ( box 1.0 2.0 3.0 4.0 circle 1.0 2.0 3.0 polygon 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 position 1.0 2.0 )";

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

        String phrase = "Union Icrs ( Box 1.0 2.0 3.0 4.0 Circle 1.0 2.0 3.0 Polygon 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 Position 1.0 2.0 )";

        Region space = STC.parse(phrase);
        String actual = STC.format(space);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testParseMixedCase passed");
    }

    @Test
    public void testRegionDatatype() throws Exception
    {
        log.debug("testRegionDatatype");

        String phrase = "UNION ICRS GEOCENTER ( POLYGON 333.2791651497387 0.7845913924934536 333.2791634406593 0.5434017765673644 332.29753124484876 0.5402904151546579 332.29752111368873 0.7814800606654444 POLYGON 332.2815701240659 0.5252547632945266 333.2834390185924 0.04593646286895137 332.2988535605711 0.042944906951163375 332.29751188195274 0.5226421761327771 POLYGON 331.28354109914096 0.02812723821034524 333.28353930108085 -0.2130624075237364 332.3009376019881 -0.2164694179186597 332.3009275377211 0.02472018291649647 )";

        Region space = STC.parse(phrase);
        String actual = STC.format(space);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testRegionDatatype passed");
    }

}
