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

/**
 * 
 */
package ca.nrc.cadc.tap.parser;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.tap.AdqlQuery;
import ca.nrc.cadc.tap.TapQuery;
import ca.nrc.cadc.tap.parser.converter.postgresql.PgFunctionNameConverter;
import ca.nrc.cadc.tap.parser.extractor.FunctionExpressionExtractor;
import ca.nrc.cadc.tap.parser.navigator.ReferenceNavigator;
import ca.nrc.cadc.tap.parser.region.pgsphere.PgsphereRegionConverter;
import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.uws.Parameter;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * test conversion of ADQL function to pgsphere implementation
 * @author Sailor Zhang
 *
 */
public class PgsRegionConverterTest
{
    private static Logger log = Logger.getLogger(PgsRegionConverterTest.class);
    static
    {
        Log4jInit.setLevel("ca.nrc.cadc", Level.INFO);
    }

    public String _query;
    public String _expected = "";

    private void doit() { doit(true); }
    
    private void doit(boolean eq)
    {
        try
        {
            Parameter para;
            para = new Parameter("QUERY", _query);
            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add(para);
            log.debug("input query: " + _query);

            TapQuery tapQuery = new AdqlPgsRegionQuery(); // inner class in this file
            tapQuery.setParameterList(paramList);
            String sql = tapQuery.getSQL();
            log.debug("actual: " + sql);
            log.debug("expected: " + _expected);
            
            if (eq) // sql equals expected
                assertEquals(_expected.toLowerCase(), sql.toLowerCase());
            else // contains expected
                Assert.assertTrue(sql.toLowerCase().indexOf(_expected.toLowerCase()) > 0);
        }
        catch(Throwable t)
        {
            t.printStackTrace();
            Assert.fail("unexpected exception: " + t);
        }
    }

    @Test
    public void testNone()
    {
        _query = "select a, b, c from someSchema.someTable where a = b and c is not null";
        _expected = _query;
        doit();
    }

    @Test
    public void testFunctionCoordsys()
    {
        _query = "select COORDSYS(a.t_spoint) from TAP_SCHEMA.AllDataTypes";
        _expected = "SELECT NULL FROM TAP_SCHEMA.AllDataTypes";
        doit();
    }

    @Test
    public void testFunctionCoord1()
    {
        _query = "select COORD1(a.t_spoint) from TAP_SCHEMA.AllDataTypes";
        _expected = "SELECT degrees(long(a.t_spoint)) FROM TAP_SCHEMA.AllDataTypes";
        doit();
    }

    @Test
    public void testFunctionCoord2()
    {
        _query = "select COORD2(a.t_spoint) from TAP_SCHEMA.AllDataTypes";
        _expected = "SELECT degrees(lat(a.t_spoint)) FROM TAP_SCHEMA.AllDataTypes";
        doit();
    }

    @Test
    public void testIntersectsColumn()
    {
        _query = "select * from someTable where INTERSECTS(some_col, other_col) = 1";
        _expected = "select * from someTable where some_col && other_col";
        doit();
    }

    @Test
    public void testNotIntersectsColumn()
    {
        _query = "select * from someTable where INTERSECTS(some_col, other_col) = 0";
        _expected = "select * from someTable where some_col !&& other_col";
        doit();
    }

    @Test
    public void testIntersectsValue()
    {
        _query = "select * from someTable where INTERSECTS(some_col, CIRCLE('',1,2,3)) = 1";
        _expected = "select * from someTable where some_col && scircle(spoint(radians(1), radians(2)), radians(3))";
        doit();
    }

    @Test
    public void testNotIntersectsValue()
    {
        _query = "select * from someTable where INTERSECTS(some_col, CIRCLE('',1,2,3)) = 0";
        _expected = "select * from someTable where some_col !&& scircle(spoint(radians(1), radians(2)), radians(3))";
        doit();

        _query = "select * from someTable where NOT (INTERSECTS(some_col, CIRCLE('',1,2,3)) = 1)";
        _expected = "select * from someTable where not (some_col && scircle(spoint(radians(1), radians(2)), radians(3)))";
        doit();
    }

    @Test
    public void testIntersectsRegion()
    {
        _query = "select * from someTable where INTERSECTS(some_col, REGION('CIRCLE 1.0 1.0 2.0')) = 1";
        _expected = "select * from someTable where some_col && scircle(spoint(radians(1.0), radians(1.0)), radians(2.0))";
        doit();
    }

    @Test
    public void testNotIntersectsRegion()
    {
        _query = "select * from someTable where INTERSECTS(some_col, REGION('CIRCLE 1.0 1.0 2.0')) = 0";
        _expected = "select * from someTable where some_col !&& scircle(spoint(radians(1.0), radians(1.0)), radians(2.0))";
        doit();
    }

    @Test
    public void testIntersectsRegionPos()
    {
        // spoint has special handling in a predicate
        _query = "select * from someTable where INTERSECTS(some_col, REGION('POSITION 1.0 2.0')) = 1";
        _expected = "select * from someTable where some_col && cast(spoint(radians(1.0), radians(2.0)) as scircle)";
        doit();
    }

    @Test
    public void testNotIntersectsRegionPos()
    {
        // spoint has special handling in a predicate
        _query = "select * from someTable where INTERSECTS(some_col, REGION('POSITION 1.0 2.0')) = 0";
        _expected = "select * from someTable where some_col !&& cast(spoint(radians(1.0), radians(2.0)) as scircle)";
        doit();
    }

    @Test
    public void testIntersectsPoint()
    {
        // spoint has special handling in a predicate
        _query = "select * from someTable where INTERSECTS(POINT('',1,2), other_col) = 1";
        _expected = "select * from someTable where cast(spoint(radians(1), radians(2)) as scircle) && other_col";
        doit();
    }

    @Test
    public void testNotIntersectsPoint()
    {
        // spoint has special handling in a predicate
        _query = "select * from someTable where INTERSECTS(POINT('',1,2), other_col) = 0";
        _expected = "select * from someTable where cast(spoint(radians(1), radians(2)) as scircle) !&& other_col";
        doit();
    }

    @Test
    public void testContainsColumn()
    {
        _query = "select * from someTable where CONTAINS(some_col, other_col) = 1";
        _expected = "select * from someTable where some_col @ other_col";
        doit();
    }

    @Test
    public void testNotContainsColumn()
    {
        _query = "select * from someTable where CONTAINS(some_col, other_col) = 0";
        _expected = "select * from someTable where some_col !@ other_col";
        doit();
    }

     @Test
    public void testContainsValue()
    {
        _query = "select * from someTable where CONTAINS(CIRCLE('',1,2,3), other_col) = 1";
        _expected = "select * from someTable where scircle(spoint(radians(1), radians(2)), radians(3)) @ other_col";
        doit();
    }

     @Test
    public void testNotContainsValue()
    {
        _query = "select * from someTable where CONTAINS(CIRCLE('',1,2,3), other_col) = 0";
        _expected = "select * from someTable where scircle(spoint(radians(1), radians(2)), radians(3)) !@ other_col";
        doit();

        _query = "select * from someTable where NOT (CONTAINS(CIRCLE('',1,2,3), other_col) = 0)";
        _expected = "select * from someTable where not (scircle(spoint(radians(1), radians(2)), radians(3)) !@ other_col)";
        doit();
    }

    @Test
    public void testContainsPoint()
    {
        // spoint has special handling in a predicate
        _query = "select * from someTable where CONTAINS(POINT('',1,2), other_col) = 1";
        _expected = "select * from someTable where cast(spoint(radians(1), radians(2)) as scircle) @ other_col";
        doit();
        _query = "select * from someTable where CONTAINS(other_col, POINT('',1,2)) = 1";
        _expected = "select * from someTable where other_col @ cast(spoint(radians(1), radians(2)) as scircle)";
        doit();
    }

    @Test
    public void testNotContainsPoint()
    {
        // spoint has special handling in a predicate
        _query = "select * from someTable where CONTAINS(POINT('',1,2), other_col) = 0";
        _expected = "select * from someTable where cast(spoint(radians(1), radians(2)) as scircle) !@ other_col";
        doit();
    }

    @Test
    public void testJoinAliasIntersects()
    {
        _query = "select * from someTable as t1 join otherTable as t2 on INTERSECTS(t1.someCol, t2.otherCol) = 1";
        _expected = "select * from someTable as t1 join otherTable as t2 on t1.someCol && t2.otherCol";
        doit();
    }

    @Test
    public void testPoint()
    {
        _query = "select POINT('ICRS GEOCENTER', 1, 2) from someTable";
        _expected = "spoint";
        doit(false);
    }

    @Test
    public void testCircle()
    {
        _query = "select CIRCLE('ICRS GEOCENTER', 10, 10, 1) from someTable";
        _expected = "scircle";
        doit(false);
    }

    @Test
    public void testBox()
    {
        _query = "select BOX('ICRS GEOCENTER', 10, 20, 1, 2) from someTable";
        _expected = "spoly";
        doit(false);
    }

    @Test
    public void testPolygon()
    {
        _query = "select POLYGON('ICRS GEOCENTER', 2,2,3,3,1,4) from someTable";
        _expected = "spoly";
        doit(false);
    }

    @Test
    public void testRegionBox()
    {
        _query = "select REGION('BOX ICRS GEOCENTER 11 22 10 20') from someTable";
        _expected = "spoly";
        doit(false);
    }

    @Test
    public void testRegionPolygon()
    {
        _query = "select REGION('POLYGON ICRS GEOCENTER 1 2 3 4 5 6 7 8') from someTable";
        _expected = "spoly";
        doit(false);
    }

    @Test
    public void testRegionCircle()
    {
        _query = "select REGION('CIRCLE ICRS GEOCENTER 11 22 0.5') from someTable";
        _expected = "scircle";
        doit(false);
    }

    @Test
    public void testRegionPosition()
    {
        _query = "select REGION('POSITION GALACTIC 11 22') from someTable";
        _expected = "spoint";
        doit(false);
    }
    
}

class AdqlPgsRegionQuery extends AdqlQuery
{
    @Override
    protected void init()
    {
        //super.init();
        super.navigatorList.add(new PgsphereRegionConverter());
    }
}