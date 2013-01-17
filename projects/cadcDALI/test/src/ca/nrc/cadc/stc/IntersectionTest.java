/*
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2009.                            (c) 2009.
 *  Government of Canada                 Gouvernement du Canada
 *  National Research Council            Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 *  All rights reserved                  Tous droits réservés
 *
 *  NRC disclaims any warranties,        Le CNRC dénie toute garantie
 *  expressed, implied, or               énoncée, implicite ou légale,
 *  statutory, of any kind with          de quelque nature que ce
 *  respect to the software,             soit, concernant le logiciel,
 *  including without limitation         y compris sans restriction
 *  any warranty of merchantability      toute garantie de valeur
 *  or fitness for a particular          marchande ou de pertinence
 *  purpose. NRC shall not be            pour un usage particulier.
 *  liable in any event for any          Le CNRC ne pourra en aucun cas
 *  damages, whether direct or           être tenu responsable de tout
 *  indirect, special or general,        dommage, direct ou indirect,
 *  consequential or incidental,         particulier ou général,
 *  arising from the use of the          accessoire ou fortuit, résultant
 *  software.  Neither the name          de l'utilisation du logiciel. Ni
 *  of the National Research             le nom du Conseil National de
 *  Council of Canada nor the            Recherches du Canada ni les noms
 *  names of its contributors may        de ses  participants ne peuvent
 *  be used to endorse or promote        être utilisés pour approuver ou
 *  products derived from this           promouvoir les produits dérivés
 *  software without specific prior      de ce logiciel sans autorisation
 *  written permission.                  préalable et particulière
 *                                       par écrit.
 *
 *  This file is part of the             Ce fichier fait partie du projet
 *  OpenCADC project.                    OpenCADC.
 *
 *  OpenCADC is free software:           OpenCADC est un logiciel libre ;
 *  you can redistribute it and/or       vous pouvez le redistribuer ou le
 *  modify it under the terms of         modifier suivant les termes de
 *  the GNU Affero General Public        la “GNU Affero General Public
 *  License as published by the          License” telle que publiée
 *  Free Software Foundation,            par la Free Software Foundation
 *  either version 3 of the              : soit la version 3 de cette
 *  License, or (at your option)         licence, soit (à votre gré)
 *  any later version.                   toute version ultérieure.
 *
 *  OpenCADC is distributed in the       OpenCADC est distribué
 *  hope that it will be useful,         dans l’espoir qu’il vous
 *  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
 *  without even the implied             GARANTIE : sans même la garantie
 *  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
 *  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
 *  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
 *  General Public License for           Générale Publique GNU Affero
 *  more details.                        pour plus de détails.
 *
 *  You should have received             Vous devriez avoir reçu une
 *  a copy of the GNU Affero             copie de la Licence Générale
 *  General Public License along         Publique GNU Affero avec
 *  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
 *  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
 *                                       <http://www.gnu.org/licenses/>.
 *
 *  $Revision: 4 $
 *
 ************************************************************************
 */
package ca.nrc.cadc.stc;

import ca.nrc.cadc.util.Log4jInit;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
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

    public IntersectionTest() { }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        box = new Box(null, null, null, 1.0, 2.0, 3.0, 4.0);

        circle = new Circle(null, null, null, 1.0, 2.0, 3.0);

        List<CoordPair> coordPairs = new ArrayList<CoordPair>();
        coordPairs.add(new CoordPair(1.0, 2.0));
        coordPairs.add(new CoordPair(3.0, 4.0));
        coordPairs.add(new CoordPair(5.0, 6.0));
        coordPairs.add(new CoordPair(7.0, 8.0));
        polygon = new Polygon(null, null, null, coordPairs);

        position = new Position(null, null, null, 1.0, 2.0);
    }

    @Test
    public void testInvalidRegions()
    {
        log.debug("testInvalidRegions");
        try
        {
            try
            {
                Intersection intersection = new Intersection(null, null, null, null);
                fail("Null Region should throw Exception");
            }
            catch (IllegalArgumentException e)
            {
                log.debug("Null Region threw exception " + e.getMessage());
            }

            List<Region> regions = new ArrayList<Region>();
            try
            {
                Intersection intersection = new Intersection(null, null, null, regions);
                fail("Empty Region should throw Exception");
            }
            catch (IllegalArgumentException e)
            {
                log.debug("Empty Region threw exception " + e.getMessage());
            }

            regions.add(new Circle(null, null, null, 1.0, 2.0, 3.0));
            try
            {
                Intersection intersection = new Intersection(null, null, null, regions);
                fail("Single Region should throw Exception");
            }
            catch (IllegalArgumentException e)
            {
                log.debug("Single Region threw exception " + e.getMessage());
            }

            String phrase = "Intersection ICRS ( )";
            try
            {
                Region intersection = STC.parseRegion(phrase);
                fail("Empty Intersection should throw Exception");
            }
            catch (StcsParsingException e)
            {
                log.debug("Empty Intersection threw exception " + e.getMessage());
            }

            phrase = "Intersection ICRS ( Circle 1.0 2.0 3.0 )";
            try
            {
                Region intersection = STC.parseRegion(phrase);
                fail("Single Region should throw Exception");
            }
            catch (StcsParsingException e)
            {
                log.debug("Single Region threw exception " + e.getMessage());
            }

            log.info("testInvalidRegions passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testFormat()
    {
        log.debug("testFormat");
        try
        {            
            List<Region> regions = new ArrayList<Region>();
            regions.add(box);
            regions.add(circle);
            regions.add(polygon);
            regions.add(position);
            Intersection intersection = new Intersection(Frame.ICRS, ReferencePosition.GEOCENTER, Flavor.SPHERICAL2, regions);

            String expected = "Intersection ICRS GEOCENTER SPHERICAL2 ( Box 1.0 2.0 3.0 4.0 Circle 1.0 2.0 3.0 Polygon 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 Position 1.0 2.0 )";
            String actual = STC.format(intersection);
            log.debug("expected: " + expected);
            log.debug("  actual: " + actual);
            assertEquals(expected, actual);
            log.info("testFormat passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testParse()
    {
        log.debug("testParse");
        try
        {
            String  phrase = "Intersection ICRS ( Box 1.0 2.0 3.0 4.0 Circle 1.0 2.0 3.0 Polygon 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 Position 1.0 2.0 )";
            Region space = STC.parse(phrase);
            String actual = STC.format(space);
            log.debug("expected: " + phrase);
            log.debug("  actual: " + actual);
            assertEquals(phrase, actual);
            log.info("testParse passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testParseLowerCase()
    {
        log.debug("testParseLowerCase");
        try
        {
            String  phrase = "intersection icrs ( box 1.0 2.0 3.0 4.0 circle 1.0 2.0 3.0 polygon 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 position 1.0 2.0 )";
            String  expected = "Intersection ICRS ( Box 1.0 2.0 3.0 4.0 Circle 1.0 2.0 3.0 Polygon 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 Position 1.0 2.0 )";
            Region space = STC.parse(phrase);
            String actual = STC.format(space);
            log.debug("expected: " + expected);
            log.debug("  actual: " + actual);
            assertEquals(expected, actual);
            log.info("testParseLowerCase passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testParseMixedCase()
    {
        log.debug("testParseMixedCase");
        try
        {
            String  phrase = "Intersection Icrs ( Box 1.0 2.0 3.0 4.0 Circle 1.0 2.0 3.0 Polygon 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 Position 1.0 2.0 )";
            String  expected = "Intersection ICRS ( Box 1.0 2.0 3.0 4.0 Circle 1.0 2.0 3.0 Polygon 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 Position 1.0 2.0 )";
            Region space = STC.parse(phrase);
            String actual = STC.format(space);
            log.debug("expected: " + expected);
            log.debug("  actual: " + actual);
            assertEquals(expected, actual);
            log.info("testParseMixedCase passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }
}
