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
    public void testInvalidCoordPairs()
    {
        log.debug("testInvalidCoordPairs");
        try
        {
            try
            {
                Polygon polygon = new Polygon(null, null, null, null);
                fail("Null CoordPair should throw Exception");
            }
            catch (IllegalArgumentException e)
            {
                log.debug("Null CoordPair threw exception " + e.getMessage());
            }

            List<CoordPair> coordPairs = new ArrayList<CoordPair>();
            try
            {
                Polygon polygon = new Polygon(null, null, null, coordPairs);
                fail("Empty CoordPairs should throw Exception");
            }
            catch (IllegalArgumentException e)
            {
                log.debug("Empty CoordPairs threw exception " + e.getMessage());
            }

            coordPairs.add(new CoordPair(1.0, 2.0));
            coordPairs.add(new CoordPair(3.0, 4.0));
            try
            {
                Polygon polygon = new Polygon(null, null, null, coordPairs);
                fail("Less than 3 CoordPairs should throw Exception");
            }
            catch (IllegalArgumentException e)
            {
                log.debug("Less than 3 CoordPairs threw exception " + e.getMessage());
            }

            String phrase = "Polygon ICRS ( )";
            try
            {
                Region polygon = STC.parseRegion(phrase);
                fail("Empty CoordPairs should throw Exception");
            }
            catch (StcsParsingException e)
            {
                log.debug("Empty CoordPairs threw exception " + e.getMessage());
            }

            phrase = "Polygon ICRS ( 1.0 2.0 3.0 4.0 )";
            try
            {
                Region polygon = STC.parseRegion(phrase);
                fail("Less than 3 CoordPairs should throw Exception");
            }
            catch (StcsParsingException e)
            {
                log.debug("Less than 3 CoordPairs threw exception " + e.getMessage());
            }

            log.info("testInvalidCoordPairs passed");
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
            List<CoordPair> coordPairs = new ArrayList<CoordPair>();
            coordPairs.add(new CoordPair(1.0, 2.0));
            coordPairs.add(new CoordPair(3.0, 4.0));
            coordPairs.add(new CoordPair(5.0, 6.0));
            coordPairs.add(new CoordPair(7.0, 8.0));
            Polygon polygon = new Polygon(Frame.ICRS, ReferencePosition.BARYCENTER, Flavor.SPHERICAL2, coordPairs);

            String expected = "Polygon ICRS BARYCENTER SPHERICAL2 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0";
            String actual = STC.format(polygon);
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
            String phrase = "Polygon ICRS BARYCENTER SPHERICAL2 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0";

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
            String phrase = "polygon icrs barycenter spherical2 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0";
            String expected = "Polygon ICRS BARYCENTER SPHERICAL2 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0";

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
            String phrase = "Polygon Icrs Barycenter Spherical2 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0";
            String expected = "Polygon ICRS BARYCENTER SPHERICAL2 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0";

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
