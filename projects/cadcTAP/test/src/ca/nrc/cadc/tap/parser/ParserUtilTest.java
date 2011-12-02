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

package ca.nrc.cadc.tap.parser;


import ca.nrc.cadc.stc.Box;
import ca.nrc.cadc.stc.SpatialSubphrase;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.tap.parser.extractor.SelectListExtractor;
import ca.nrc.cadc.tap.parser.navigator.FromItemNavigator;
import ca.nrc.cadc.tap.parser.navigator.ReferenceNavigator;
import ca.nrc.cadc.tap.parser.navigator.SelectNavigator;
import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.util.Log4jInit;
import java.util.ArrayList;
import java.util.List;
import junit.framework.Assert;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;


/**
 * Unit tests fro ParserUtil methods.
 *
 * @author Patrick Dowler
 *
 */
public class ParserUtilTest
{
    private static Logger log = Logger.getLogger(ParserUtilTest.class);

    public String _query;

    SelectListExtractor _en;
    ReferenceNavigator _rn;
    FromItemNavigator _fn;
    SelectNavigator _sn;

    static TapSchema TAP_SCHEMA;

    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
        Log4jInit.setLevel("ca.nrc.cadc", Level.INFO);
    }

    /**
     * @throws java.lang.Exception
     */
    @AfterClass
    public static void tearDownAfterClass() throws Exception
    {
    }

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception
    {
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception
    {
    }

    private Function createFunction(String frame, String refpos, String flavor,
            double x, double y, double w, double h)
    {
        StringBuilder sb = new StringBuilder("'");
        if (frame != null)
            sb.append(frame).append(" ");
        if (refpos != null)
            sb.append(refpos).append(" ");
        if (flavor != null)
            sb.append(flavor);
        sb.append("'");
        List ex = new ArrayList();
        ex.add(new StringValue(sb.toString()));
        ex.add(new DoubleValue(Double.toString(x)));
        ex.add(new DoubleValue(Double.toString(y)));
        ex.add(new DoubleValue(Double.toString(w)));
        ex.add(new DoubleValue(Double.toString(h)));
        ExpressionList args = new ExpressionList(ex);
        Function f = new Function();
        f.setName("BOX");
        f.setParameters(args);
        return f;
    }
   
    @Test
    public void testAdqlToStcBoxFrame()
    {
        String frame = "ICRS";
        String refpos = null;
        String flavor = null;
        try
        {
            Function f = createFunction(frame, refpos, flavor, 3.0, 3.0, 1.0, 1.0);
            Box b = ParserUtil.convertToStcBox(f);
            Assert.assertEquals(b.getFrame(), frame);
            Assert.assertEquals(b.getRefPos(), SpatialSubphrase.DEFAULT_REFPOS);
            Assert.assertEquals(b.getFlavor(), SpatialSubphrase.DEFAULT_FLAVOR);
        }
        catch(Throwable t)
        {
            Assert.fail("unexpected exception " + t);
        }
    }
    @Test
    public void testAdqlToStcBoxFrameRefPos()
    {
        String frame = "ICRS";
        String refpos = "geocenter";
        String flavor = null;
        try
        {
            Function f = createFunction(frame, refpos, flavor, 3.0, 3.0, 1.0, 1.0);
            Box b = ParserUtil.convertToStcBox(f);
            Assert.assertEquals(b.getFrame().toUpperCase(), frame.toUpperCase());
            Assert.assertEquals(b.getRefPos().toUpperCase(), refpos.toUpperCase());
            Assert.assertEquals(b.getFlavor(), SpatialSubphrase.DEFAULT_FLAVOR);
        }
        catch(Throwable t)
        {
            Assert.fail("unexpected exception " + t);
        }
    }
    @Test
    public void testAdqlToStcBoxFrameRefPosFlavor()
    {
        String frame = "ICRS";
        String refpos = "gEoCeNtEr";
        String flavor = "SpHeRiCaL2";
        try
        {
            Function f = createFunction(frame, refpos, flavor, 3.0, 3.0, 1.0, 1.0);
            Box b = ParserUtil.convertToStcBox(f);
            Assert.assertEquals(b.getFrame().toUpperCase(), frame.toUpperCase());
            Assert.assertEquals(b.getRefPos().toUpperCase(), refpos.toUpperCase());
            Assert.assertEquals(b.getFlavor().toUpperCase(), flavor.toUpperCase());
        }
        catch(Throwable t)
        {
            Assert.fail("unexpected exception " + t);
        }
    }
    @Test
    public void testAdqlToStcBoxUnknownFrame()
    {
        String frame = "FOO";
        String refpos =  null;
        String flavor = null;
        try
        {
            Function f = createFunction(frame, refpos, flavor, 3.0, 3.0, 1.0, 1.0);
            Box b = ParserUtil.convertToStcBox(f);
            Assert.fail("expected an exception");
        }
        catch(IllegalArgumentException expected)
        {
            log.debug("expected exception: " + expected);
        }
        catch(Throwable t)
        {
            Assert.fail("unexpected exception " + t);
        }
    }

    // TODO: add tests with jsqlparser LongValue arguments
    
    // TODO: add tests that provoke the various exceptions
}
