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

package ca.nrc.cadc.tap.writer.formatter;

import ca.nrc.cadc.stc.Polygon;
import ca.nrc.cadc.util.Log4jInit;
import java.sql.ResultSet;
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
public class SPolyFormatterTest
{
    private static final Logger LOG = Logger.getLogger(SPointFormatterTest.class);
    static
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }
    private static final String SPOLYGON = " {(0.0349065850398866 , 0.0349065850398866),(0.0349065850398866 , 0.0698131700797732),(0.0523598775598299 , 0.0523598775598299)}";
    private static final String STCS_POLYGON = "Polygon ICRS 2.0 2.0 2.0 4.0 3.0 3.0";

    public SPolyFormatterTest() { }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
    }

    @Before
    public void setUp() { }

    @After
    public void tearDown() { }

    /**
     * Test of format method, of class SPolyFormatter.
     */
    @Test
    public void testFormat_Object()
    {
        LOG.debug("testFormat");
        Object object = SPOLYGON;
        SPolyFormatter instance = new SPolyFormatter();
        String expResult = STCS_POLYGON;
        String result = instance.format(object);
        assertEquals(expResult, result);
        LOG.info("testFormat passed");
    }

    /**
     * Test of getPolygon method, of class SPolyFormatter.
     */
    @Test
    public void testGetPosition()
    {
        LOG.debug("testGetPolygon");
        Object object = SPOLYGON;
        SPolyFormatter instance = new SPolyFormatter();
        Polygon polygon = instance.getPolygon(object);
        assertEquals("Polygon", Polygon.NAME);
        assertEquals("ICRS", polygon.frame);
        assertEquals(new Double(2.0000000000000004), polygon.pos.get(0));
        assertEquals(new Double(2.0000000000000004), polygon.pos.get(1));
        assertEquals(new Double(2.0000000000000004), polygon.pos.get(2));
        assertEquals(new Double(4.000000000000001), polygon.pos.get(3));
        assertEquals(new Double(3.0000000000000004), polygon.pos.get(4));
        assertEquals(new Double(3.0000000000000004), polygon.pos.get(5));
        LOG.info("testGetPolygon passed");
    }

}