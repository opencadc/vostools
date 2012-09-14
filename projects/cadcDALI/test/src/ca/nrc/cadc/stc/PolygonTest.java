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
import org.junit.Test;
import static org.junit.Assert.*;

public class PolygonTest
{
    private static final Logger log = Logger.getLogger(PolygonTest.class);
    static
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }

    public PolygonTest() { }

    @Test
    public void testFormat() throws Exception
    {
        log.debug("testFormat");
        String phrase = "POLYGON ICRS BARYCENTER SPHERICAL2 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0";

        List<CoordPair> coordPairs = new ArrayList<CoordPair>();
        coordPairs.add(new CoordPair(1.0, 2.0));
        coordPairs.add(new CoordPair(3.0, 4.0));
        coordPairs.add(new CoordPair(5.0, 6.0));
        coordPairs.add(new CoordPair(7.0, 8.0));
        Polygon polygon = new Polygon("ICRS BARYCENTER SPHERICAL2", coordPairs);

        String actual = STC.format(polygon);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testFormat passed");
    }

    @Test
    public void testFormatLowerCase() throws Exception
    {
        log.debug("testFormatLowerCase");
        String phrase = "POLYGON icrs barycenter spherical2 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0";

        List<CoordPair> coordPairs = new ArrayList<CoordPair>();
        coordPairs.add(new CoordPair(1.0, 2.0));
        coordPairs.add(new CoordPair(3.0, 4.0));
        coordPairs.add(new CoordPair(5.0, 6.0));
        coordPairs.add(new CoordPair(7.0, 8.0));
        Polygon polygon = new Polygon("icrs barycenter spherical2", coordPairs);

        String actual = STC.format(polygon);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testFormatLowerCase passed");
    }

    @Test
    public void testFormatMixedCase() throws Exception
    {
        log.debug("testFormatMixedCase");
        String phrase = "POLYGON Icrs Barycenter Spherical2 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0";

        List<CoordPair> coordPairs = new ArrayList<CoordPair>();
        coordPairs.add(new CoordPair(1.0, 2.0));
        coordPairs.add(new CoordPair(3.0, 4.0));
        coordPairs.add(new CoordPair(5.0, 6.0));
        coordPairs.add(new CoordPair(7.0, 8.0));
        Polygon polygon = new Polygon("Icrs Barycenter Spherical2", coordPairs);

        String actual = STC.format(polygon);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testFormatMixedCase passed");
    }

    @Test
    public void testParse() throws Exception
    {
        log.debug("testParse");

        String phrase = "POLYGON ICRS BARYCENTER SPHERICAL2 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0";

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

        String phrase = "polygon icrs barycenter spherical2 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0";

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

        String phrase = "Polygon Icrs Barycenter Spherical2 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0";

        Region space = STC.parse(phrase);
        String actual = STC.format(space);
        log.debug("expected: " + phrase);
        log.debug("  actual: " + actual);
        assertEquals(phrase, actual);
        log.info("testParseMixedCase passed");
    }

}
