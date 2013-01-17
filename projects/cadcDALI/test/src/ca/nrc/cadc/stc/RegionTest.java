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
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author jburke
 */
public class RegionTest
{
    private static final Logger log = Logger.getLogger(RegionTest.class);
    static
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }

    public RegionTest() { }

    @Test
    public void testFormatNull()
    {
        log.debug("testFormatNull");
        try
        {
            Box box = null;
            String actual = STC.format(box);
            String expected = "";
            assertEquals("Formatting null Box should return empty string", expected, actual);
            log.info("testFormatNull passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testFormatNullRegion() throws Exception
    {
        log.debug("testFormatNullRegion");
        try
        {
            Box box = new Box(null, null, null, 1.0, 2.0, 3.0, 4.0);

            String actual = STC.format(box);
            String expected = "Box 1.0 2.0 3.0 4.0";
            log.debug("expected: " + expected);
            log.debug("  actual: " + actual);
            assertEquals(expected, actual);
            log.info("testFormatNullRegion passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testParseNull() throws Exception
    {
        log.debug("testParseNull");
        try
        {
            String phrase = null;

            Region region = STC.parseRegion(phrase);
            assertNull("Region should be null", region);
            log.info("testParseNull passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testParseEmptyString() throws Exception
    {
        log.debug("testParseEmptyString");
        try
        {
            String phrase = "";

            Region region = STC.parseRegion(phrase);
            assertNull("Region should be null", region);
            log.info("testParseEmptyString passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }
    
    @Test
    public void testNullArguments()
    {
        log.debug("testNullArguments");
        try
        {
            Circle circle = new Circle(null, null, null, 1.0, 2.0, 3.0);
            assertNotNull("Circle should not be null", circle);
            assertNull("frame should be null", circle.getFrame());
            assertNull("refpos should be null", circle.getRefPos());
            assertNull("flavor should be null", circle.getFlavor());

            log.info("testNullArguments passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testValidArguments()
    {
        log.debug("testValidArguments");
        try
        {
            Circle circle = new Circle(Frame.ICRS, ReferencePosition.GEOCENTER, Flavor.SPHERICAL2, 1.0, 2.0, 3.0);
            assertNotNull("Circle should not be null", circle);
            assertEquals("frame should be ICRS", Frame.ICRS, circle.getFrame());
            assertEquals("refpos should be GEOCENTER", ReferencePosition.GEOCENTER, circle.getRefPos());
            assertEquals("flavor should be SPHERICAL2", Flavor.SPHERICAL2, circle.getFlavor());

            log.info("testValidArguments passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    /**
     * Test class for testing null or empty name arguments.
     */
    private class TestRegion extends Region
    {
        public TestRegion(String name, Frame frame, ReferencePosition refpos, Flavor flavor)
        {
            super(name, frame, refpos, flavor);
        }
    }

    @Test
    public void testNullName()
    {
        log.debug("testNullName");
        try
        {
            try
            {
                TestRegion region = new TestRegion(null, Frame.ICRS, ReferencePosition.GEOCENTER, Flavor.SPHERICAL2);
                fail("Null name should throw IllegalArgumentException");
            }
            catch (IllegalArgumentException e)
            {
                log.debug("Null name threw exception " + e.getMessage());
            }

            log.info("testNullName passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testEmptyName()
    {
        log.debug("testEmptyName");
        try
        {
            try
            {
                TestRegion region = new TestRegion("", Frame.ICRS, ReferencePosition.GEOCENTER, Flavor.SPHERICAL2);
                fail("Empty name should throw IllegalArgumentException");
            }
            catch (IllegalArgumentException e)
            {
                log.debug("Empty name threw exception " + e.getMessage());
            }

            log.info("testEmptyName passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testNullCoordsys()
    {
        log.debug("testNullCoordsys");
        try
        {
            TestRegion region = new TestRegion("foo", null, null, null);
            assertNotNull("Region should not be null", region);
            assertNull("frame should be null", region.getFrame());
            assertNull("refpos should be null", region.getRefPos());
            assertNull("flavor should be null", region.getFlavor());

            log.info("testNullCoordsys passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testValidCoordsys()
    {
        log.debug("testValidCoordsys");
        try
        {
            TestRegion region = new TestRegion("foo", Frame.ICRS, ReferencePosition.GEOCENTER, Flavor.SPHERICAL2);
            assertNotNull("Region should not be null", region);
            assertEquals("frame should be ICRS", Frame.ICRS, region.getFrame());
            assertEquals("refpos should be GEOCENTER", ReferencePosition.GEOCENTER, region.getRefPos());
            assertEquals("flavor should be SPHERICAL2", Flavor.SPHERICAL2, region.getFlavor());

            log.info("testValidCoordsys passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testPartialCoordsys()
    {
        log.debug("testPartialCoordsys");
        try
        {
            TestRegion region = new TestRegion("foo", Frame.ICRS, null, null);
            assertNotNull("Region should not be null", region);
            assertEquals("frame should be ICRS", Frame.ICRS, region.getFrame());
            assertNull("refpos should be null", region.getRefPos());
            assertNull("flavor should be null", region.getFlavor());

            region = new TestRegion("foo", null, ReferencePosition.GEOCENTER, null);
            assertNotNull("Region should not be null", region);
            assertNull("frame should be null", region.getFrame());
            assertEquals("refpos should be GEOCENTER", ReferencePosition.GEOCENTER, region.getRefPos());
            assertNull("flavor should be null", region.getFlavor());

            region = new TestRegion("foo", null, null, Flavor.SPHERICAL2);
            assertNotNull("Region should not be null", region);
            assertNull("frame should be null", region.getFrame());
            assertNull("refpos should be null", region.getRefPos());
            assertEquals("flavor should be SPHERICAL2", Flavor.SPHERICAL2, region.getFlavor());

            region = new TestRegion("foo", Frame.ICRS, ReferencePosition.GEOCENTER, null);
            assertNotNull("Region should not be null", region);
            assertEquals("frame should be ICRS", Frame.ICRS, region.getFrame());
            assertEquals("refpos should be GEOCENTER", ReferencePosition.GEOCENTER, region.getRefPos());
            assertNull("flavor should be null", region.getFlavor());

            region = new TestRegion("foo", null, ReferencePosition.GEOCENTER, Flavor.SPHERICAL2);
            assertNotNull("Region should not be null", region);
            assertNull("frame should be null", region.getFrame());
            assertEquals("refpos should be GEOCENTER", ReferencePosition.GEOCENTER, region.getRefPos());
            assertEquals("flavor should be SPHERICAL2", Flavor.SPHERICAL2, region.getFlavor());

            region = new TestRegion("foo", Frame.ICRS, null, Flavor.SPHERICAL2);
            assertNotNull("Region should not be null", region);
            assertEquals("frame should be ICRS", Frame.ICRS, region.getFrame());
            assertNull("refpos should be null", region.getRefPos());
            assertEquals("flavor should be SPHERICAL2", Flavor.SPHERICAL2, region.getFlavor());

            log.info("testPartialCoordsys passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

}
