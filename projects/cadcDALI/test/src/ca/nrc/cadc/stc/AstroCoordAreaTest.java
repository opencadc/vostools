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
public class AstroCoordAreaTest
{
    private static final Logger log = Logger.getLogger(AstroCoordAreaTest.class);
    static
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }

    public AstroCoordAreaTest() { }

    @Test
    public void testNullArguments()
    {
        log.debug("testNullArguments");
        try
        {
            AstroCoordArea area = new AstroCoordArea(null, null);
            assertNotNull("AstroCoordArea should not be null", area);
            assertNull("Region should be null", area.getRegion());
            assertNull("SpectralInterval should be null", area.getSpectralInterval());

            log.info("testNullArguments passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testNullRegion()
    {
        log.debug("testNullRegion");
        try
        {
            SpectralInterval interval = new SpectralInterval(1000.5, 2000.5, SpectralUnit.Hz);
            AstroCoordArea area = new AstroCoordArea(null, interval);
            assertNotNull("AstroCoordArea should not be null", area);
            assertNull("Region should be null", area.getRegion());
            assertNotNull("SpectralInterval should not be null", area.getSpectralInterval());
            assertEquals(interval, area.getSpectralInterval());

            log.info("testNullRegion passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testNullSpectralInterval()
    {
        log.debug("testNullSpectralInterval");
        try
        {
            Circle circle = new Circle(Frame.ICRS, ReferencePosition.GEOCENTER, Flavor.SPHERICAL2, 1.0, 2.0, 3.0);
            AstroCoordArea area = new AstroCoordArea(circle, null);
            assertNotNull("AstroCoordArea should not be null", area);
            assertNotNull("Region should not be null", area.getRegion());
            assertEquals(circle, area.getRegion());
            assertNull("SpectralInterval should be null", area.getSpectralInterval());

            log.info("testNullSpectralInterval passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testFormatNullAstroCoordArea()
    {
        log.debug("testFormatNullAstroCoordArea");
        try
        {
            AstroCoordArea area = null;
            String actual = STC.format(area);
            String expected = "";
            assertEquals("AstroCoordArea should be empty", expected, actual);
            log.info("testFormatNullAstroCoordArea passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testFormatNullRegion()
    {
        log.debug("testFormatNullRegion");
        try
        {
            SpectralInterval interval = new SpectralInterval(1000.5, 2000.5, SpectralUnit.Hz);
            AstroCoordArea area = new AstroCoordArea(null, interval);
            String expected = "SpectralInterval 1000.5 2000.5 Hz";
            String actual = STC.format(area);
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
    public void testFormatNullSpectralInterval()
    {
        log.debug("testFormatNullSpectralInterval");
        try
        {
            Circle circle = new Circle(Frame.ICRS, ReferencePosition.GEOCENTER, Flavor.SPHERICAL2, 1.0, 2.0, 3.0);
            AstroCoordArea area = new AstroCoordArea(circle, null);
            String expected = "Circle ICRS GEOCENTER SPHERICAL2 1.0 2.0 3.0";
            String actual = STC.format(area);
            log.debug("expected: " + expected);
            log.debug("  actual: " + actual);
            assertEquals(expected, actual);
            log.info("testFormatNullSpectralInterval passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testFormatValidAstroCoordArea()
    {
        log.debug("testFormatValidAstroCoordArea");
        try
        {
            Circle circle = new Circle(Frame.ICRS, ReferencePosition.GEOCENTER, Flavor.SPHERICAL2, 1.0, 2.0, 3.0);
            SpectralInterval interval = new SpectralInterval(1000.5, 2000.5, SpectralUnit.Hz);
            AstroCoordArea area = new AstroCoordArea(circle, interval);
            String expected = "Circle ICRS GEOCENTER SPHERICAL2 1.0 2.0 3.0 SpectralInterval 1000.5 2000.5 Hz";
            String actual = STC.format(area);
            log.debug("expected: " + expected);
            log.debug("  actual: " + actual);
            assertEquals(expected, actual);
            log.info("testFormatValidAstroCoordArea passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testParseRegion()
    {
        log.debug("testParseRegion");
        try
        {
            String phrase = "Circle ICRS GEOCENTER SPHERICAL2 1.0 2.0 3.0";
            Circle expected = new Circle(Frame.ICRS, ReferencePosition.GEOCENTER, Flavor.SPHERICAL2, 1.0, 2.0, 3.0);

            AstroCoordArea area = STC.parseAstroCoordArea(phrase);
            assertNotNull(area);
            assertTrue("Region should be a Circle", (area.getRegion() instanceof Circle));
            Circle actual = (Circle) area.getRegion();
            assertEquals(expected.getName(), actual.getName());
            assertEquals(expected.getFrame(), actual.getFrame());
            assertEquals(expected.getRefPos(), actual.getRefPos());
            assertEquals(expected.getFlavor(), actual.getFlavor());
            assertEquals(expected.getCoordPair().getX(), actual.getCoordPair().getX(), 0.0);
            assertEquals(expected.getCoordPair().getY(), actual.getCoordPair().getY(), 0.0);
            assertEquals(expected.getRadius(), actual.getRadius(), 0.0);
            assertNull("SpectralInterval should be null", area.getSpectralInterval());
            log.info("testParseRegion passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testParseSpectralInterval()
    {
        log.debug("testParseSpectralInterval");
        try
        {
            String phrase = "SpectralInterval 1000.5 2000.5 Hz";
            SpectralInterval expected = new SpectralInterval(1000.5, 2000.5, SpectralUnit.Hz);

            AstroCoordArea area = STC.parseAstroCoordArea(phrase);
            assertNotNull(area);
            assertNull("Region should be null", area.getRegion());
            assertNotNull("SpectralInterval should not be null", area.getSpectralInterval());
            SpectralInterval actual = area.getSpectralInterval();
            assertEquals(expected.getLoLimit(), actual.getLoLimit(), 0.0);
            assertEquals(expected.getHiLimit(), actual.getHiLimit(), 0.0);
            assertEquals(expected.getUnit(), actual.getUnit());

            log.info("testParseSpectralInterval passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testParseValidAstroCoordArea()
    {
        log.debug("testParseValidAstroCoordArea");
        try
        {
            Circle expectedCircle = new Circle(Frame.ICRS, ReferencePosition.GEOCENTER, Flavor.SPHERICAL2, 1.0, 2.0, 3.0);
            SpectralInterval expectedInterval = new SpectralInterval(1000.5, 2000.5, SpectralUnit.Hz);

            String phrase = "Circle ICRS GEOCENTER SPHERICAL2 1.0 2.0 3.0 SpectralInterval 1000.5 2000.5 Hz";
            AstroCoordArea area = STC.parseAstroCoordArea(phrase);
            assertNotNull(area);
            assertNotNull("Region should not be null", area.getRegion());
            assertNotNull("SpectralInterval should not be null", area.getSpectralInterval());
            
            assertTrue("Region should be a Circle", (area.getRegion() instanceof Circle));
            Circle actualCircle = (Circle) area.getRegion();
            assertEquals(expectedCircle.getName(), actualCircle.getName());
            assertEquals(expectedCircle.getFrame(), actualCircle.getFrame());
            assertEquals(expectedCircle.getRefPos(), actualCircle.getRefPos());
            assertEquals(expectedCircle.getFlavor(), actualCircle.getFlavor());
            assertEquals(expectedCircle.getCoordPair().getX(), actualCircle.getCoordPair().getX(), 0.0);
            assertEquals(expectedCircle.getCoordPair().getY(), actualCircle.getCoordPair().getY(), 0.0);
            assertEquals(expectedCircle.getRadius(), actualCircle.getRadius(), 0.0);

            SpectralInterval actualInterval = area.getSpectralInterval();
            assertEquals(expectedInterval.getLoLimit(), actualInterval.getLoLimit(), 0.0);
            assertEquals(expectedInterval.getHiLimit(), actualInterval.getHiLimit(), 0.0);
            assertEquals(expectedInterval.getUnit(), actualInterval.getUnit());
            log.info("testParseValidAstroCoordArea passed");
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

}
