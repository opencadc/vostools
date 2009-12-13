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
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class BoxTest
{
    public static final String SPACE = "Box";
    public static final String FILLFACTOR = "fillfactor 1.0";
    public static final String FRAME = "ICRS";
    public static final String REFPOS = "BARYCENTER";
    public static final String FLAVOR = "SPHER2";
    public static final String POS = "148.9 69.1";
    public static final String BSIZE = "2.0 3.0";
    public static final String POSITION = "Position 0.1 0.2";
    public static final String UNIT = "unit deg";
    public static final String ERROR = "Error 0.1 0.2 0.3 0.4";
    public static final String RESOLUTION = "Resolution 0.0001 0.0001 0.0003 0.0003";
    public static final String SIZE = "Size 0.5 0.5 0.67 0.67";
    public static final String PIXSIZE = "PixSize 0.00005 0.00005 0.00015 0.00015";
    public static final String VELOCITY = "VelocityInterval fillfactor 1.0 1.0 2.0 3.0 4.0";

    public static String phrase;

    private static final Logger LOG = Logger.getLogger(BoxTest.class);
    static
    {
        Log4jInit.setLevel("ca", Level.INFO);
    }

    public BoxTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        StringBuilder sb = new StringBuilder();
        sb.append(SPACE).append(" ");
        sb.append(FILLFACTOR).append(" ");
        sb.append(FRAME).append(" ");
        sb.append(REFPOS).append(" ");
        sb.append(FLAVOR).append(" ");
        sb.append(POS).append(" ");
        sb.append(BSIZE).append(" ");
        sb.append(POSITION).append(" ");
        sb.append(UNIT).append(" ");
        sb.append(ERROR).append(" ");
        sb.append(RESOLUTION).append(" ");
        sb.append(SIZE).append(" ");
        sb.append(PIXSIZE).append(" ");
        sb.append(VELOCITY);
        phrase = sb.toString();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testFormat() throws Exception
    {
        LOG.debug("parse");

        Box box = new Box();
        box.fill = 1.0D;
        box.frame = FRAME;
        box.refpos = REFPOS;
        box.flavor = FLAVOR;
        box.pos = new ArrayList<Double>();
        box.pos.add(148.9);
        box.pos.add(69.1);
        box.bsize = new ArrayList<Double>();
        box.bsize.add(2.0);
        box.bsize.add(3.0);
        box.position = new ArrayList<Double>();
        box.position.add(0.1);
        box.position.add(0.2);
        box.unit = "deg";
        box.error = new ArrayList<Double>();
        box.error.add(0.1);
        box.error.add(0.2);
        box.error.add(0.3);
        box.error.add(0.4);
        box.resln = new ArrayList<Double>();
        box.resln.add(0.0001);
        box.resln.add(0.0001);
        box.resln.add(0.0003);
        box.resln.add(0.0003);
        box.size = new ArrayList<Double>();
        box.size.add(0.5);
        box.size.add(0.5);
        box.size.add(0.67);
        box.size.add(0.67);
        box.pixsiz = new ArrayList<Double>();
        box.pixsiz.add(0.00005);
        box.pixsiz.add(0.00005);
        box.pixsiz.add(0.00015);
        box.pixsiz.add(0.00015);

        box.velocity = new Velocity();
        box.velocity.intervals = new ArrayList<VelocityInterval>();

        VelocityInterval interval = new VelocityInterval();
        interval.fill = 1.0;
        interval.lolimit = new ArrayList<Double>();
        interval.lolimit.add(1.0);
        interval.lolimit.add(2.0);
        interval.hilimit = new ArrayList<Double>();
        interval.hilimit.add(3.0);
        interval.hilimit.add(4.0);

        box.velocity.intervals.add(interval);

        String actual = STC.format(box);
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
