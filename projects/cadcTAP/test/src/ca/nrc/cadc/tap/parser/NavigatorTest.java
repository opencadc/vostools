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

import net.sf.jsqlparser.statement.Statement;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.tap.parser.navigator.ExpressionNavigator;
import ca.nrc.cadc.tap.parser.navigator.FromItemNavigator;
import ca.nrc.cadc.tap.parser.navigator.ReferenceNavigator;
import ca.nrc.cadc.tap.parser.navigator.SelectNavigator;
import ca.nrc.cadc.util.Log4jInit;
import org.apache.log4j.Logger;

/**
 * general test of navigator
 *  
 * @author Sailor Zhang
 *
 */
public class NavigatorTest
{
    private static final Logger log = Logger.getLogger(NavigatorTest.class);

	public String _query;

    ExpressionNavigator _en;
    ReferenceNavigator _rn;
    FromItemNavigator _fn;
    SelectNavigator _sn;

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception
    {
        Log4jInit.setLevel("ca.nrc.cadc.tap.parser", org.apache.log4j.Level.INFO);
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception
    {
        _en = new ExpressionNavigator();
        _rn = new ReferenceNavigator();
        _fn = new FromItemNavigator();
        _sn = new SelectNavigator(_en, _rn, _fn);
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	private void doNavigate(boolean expectValid) {
		Statement s = null;
		boolean exceptionHappens = false;
		try
        {
            log.debug("query: " + _query);
		    s = ParserUtil.receiveQuery(_query);
		    ParserUtil.parseStatement(s, _sn);
		}
        catch (Exception ae)
        {
            exceptionHappens = true;
            log.debug("exception: " + ae.getMessage());
        }
        log.debug("statement: " + s);
        Assert.assertTrue(expectValid != exceptionHappens);
	}

	//@Test
	public void testColumnReference() {
		_query = "select top 123 "
			+ "ctype1, ctype2, ctype2 "
			+ " from observation.plane pp join observation.spatial as ss using (obsID) "
			+ " where cd1_1 > 432.1 "
			+ " group by BADctype2"
			+ " order by 22";
		boolean expectValid = false;
		doNavigate(expectValid);
	}

	//@Test
	public void testColumnIndexReference() {
		boolean expectValid = false;
		_query = "select top 123 "
			+ "ctype1, ctype2, ctype2 "
			+ " from observation.plane pp join observation.spatial as ss using (obsID) "
			+ " where cd1_1 > 432.1 "
			+ " order by 22";
		doNavigate(expectValid);
	}
	//@Test
	public void testNegativeColumnIndexReference() {
		boolean expectValid = false;
		_query = "select top 123 "
			+ "ctype1, ctype2, ctype2 "
			+ " from observation.plane pp join observation.spatial as ss using (obsID) "
			+ " where cd1_1 > 432.1 "
			+ " order by -2";
		doNavigate(expectValid);
	}
	//@Test
	public void testGoodColumnIndexReference() {
		boolean expectValid = true;
		_query = "select top 123 "
			+ "ctype1, ctype2, ctype2 "
			+ " from observation.plane pp join observation.spatial as ss using (obsID) "
			+ " where cd1_1 > 432.1 "
			+ " order by 2";
		doNavigate(expectValid);
	}
	//@Test
	public void testGoodAllTableColumnsSubSelect() {
		boolean expectValid = true;
		_query = "select qq.*"
			+ " from (select filter_lo, obsID from spectral) as qq, spatial pp"
			+ " where cd1_1 > 432.1 ";
		doNavigate(expectValid);
	}
	//@Test
	public void testGoodAllTableColumnsSubSelect1() {
		boolean expectValid = true;
		_query = "select qq.*"
			+ " from spatial pp, (select filter_lo, obsID from spectral) as qq"
			+ " where cd1_1 > 432.1 ";
		doNavigate(expectValid);
	}
	//@Test
	public void testGoodAllTableColumnsSubSelect2() {
		boolean expectValid = false;
		_query = "select qq.*"
			+ " from (select filter_lo, obsID from spectral as ss where obsID = pp.obsID) as qq, spatial pp"
			+ " where cd1_1 > 432.1 ";
		doNavigate(expectValid);
	}
	//@Test
	public void testGoodAllTableColumnsSubSelect4() {
		boolean expectValid = false;
		_query = "select qq.*"
			+ " from spatial pp, (select filter_lo, obsID from spectral as ss where obsID = pp.obsID) as qq"
			+ " where cd1_1 > 432.1 ";
		doNavigate(expectValid);
	}
	//@Test
	public void testGoodFromSubSelect() {
		boolean expectValid = true;
		_query = "select top 23 "
			+ "ctype1, ctype2, filter_lo "
			+ " from (select filter_lo, obsID from spectral)" +
					" as qq, spatial pp"
			+ " where cd1_1 > 432.1 "
			+ "    and pp.obsID = qq.obsID"
			+ " order by 2";
		doNavigate(expectValid);
	}
	//@Test
	public void testAmbiguousTable() {
		boolean expectValid = false;
		_query = "select project, shape from plane" ;
		doNavigate(expectValid);
	}
	//@Test
	public void testInvalidTable() {
		boolean expectValid = false;
		_query = "select project, shape from spectral" ;
		doNavigate(expectValid);
	}
	//@Test
	public void testValidTable() {
		boolean expectValid = true;
		_query = "select filter_lo, s.filter_hi from spectral s" ;
		doNavigate(expectValid);
	}
	//@Test
	public void testJoins() {
		boolean expectValid = false;
		_query = "select aa.aaa, bb.bbb, cc.ccc from tablea aa, tableb bb, tablec cc where aa.a = bb.c and bb.a=cc.d" ;
		_query = "select aa.aaa, bb.bbb, cc.ccc "
			+ " from (select aaa from tablea) as aa," +
					" tableb bb join (select ccc from tablec) using (ddd) where aa.a = bb.c and bb.a=cc.d" ;
		doNavigate(expectValid);
	}
	//@Test
	public void testAmbiguousTableName() {
		boolean expectValid = false;
		_query = "select s.*"
			+ " from plane , spectral s" 
			+ " where s.obsID = plane.obsID";
		doNavigate(expectValid);
	}
	//@Test
	public void testAmbiguousTableName2() {
		boolean expectValid = true;
		_query = "select s.*"
			+ " from observation.plane , spectral s" 
			+ " where s.obsID = plane.obsID";
		doNavigate(expectValid);
	}
	//@Test
	public void testAmbiguousWhereColumn() {
		boolean expectValid = false;
		_query = "select s.*"
			+ " from observation.plane , spectral s" 
			+ " where obsID = 333";
		doNavigate(expectValid);
	}
	//@Test
	public void testAmbiguousWhereColumn2() {
		boolean expectValid = true;
		_query = "select s.*"
			+ " from observation.plane , spectral s" 
			+ " where project = 333";
		doNavigate(expectValid);
	}
	//@Test
	public void testGoodWhereSubSelect() {
		boolean expectValid = true;
		_query = "select "
			+ "ctype1, ctype2"
			+ " from spatial pp "
			+ "  "
			+ " where cd1_1 > 432.1 "
			+ "    and pp.obsID in (select distinct obsID from spectral where filter_lo > 3212 and obsID = ctype1 or filter_lo < pp.ctype2) "
			+ "    and exists (select obsID from spectral where filter_lo > 3212 and obsID = ctype1 or filter_lo < pp.ctype2) "
			+ " order by 2";
		doNavigate(expectValid);
	}
	//@Test
	public void testBadWhereSubSelect() {
		boolean expectValid = false;
		_query = "select "
			+ "ctype1, ctype2"
			+ " from spatial pp "
			+ "  "
			+ " where cd1_1 > 432.1 "
			+ "    and pp.obsID in (select distinct obsID from spectral where filter_lo > 3212 and obsID = ctype1 or filter_lo < p.ctype2) "
			+ "    and exists (select obsID from spectral where filter_lo > 3212 and obsID = ctype3 or filter_lo < pp.ctype2) "
			+ " order by 2";
		doNavigate(expectValid);
	}
	//@Test
	public void testBadHaving() {
		// having without group_by
		boolean expectValid = false;
		_query = "select "
			+ "ctype1, ctype2"
			+ " from spatial pp "
			+ " where cd1_1 > 432.1 "
			+ " having ctype2 > 2"
			+ " order by 2";
		doNavigate(expectValid);
	}
	//@Test
	public void testGoodGroupBy() {
		boolean expectValid = true;
		_query = "select "
			+ "ctype1, ctype2"
			+ " from spatial pp "
			+ " where cd1_1 > 432.1 "
			+ " group by ctype1 "
			+ " having ctype2 > 2 "
			+ "    and ctype2 in (select distinct exptime from temporal where obsID = pp.obsID )"
			+ " order by 2";
		doNavigate(expectValid);
	}
	@Test
	public void testGoodOrderBy() {
		boolean expectValid = true;
		_query = "select "
			+ "ctype1, ctype2"
			+ " from spatial pp "
			+ " where cd1_1 > 432.1 "
			+ " group by ctype1 "
			+ " having ctype2 > 2 "
			+ "    and ctype2 in (select distinct exptime from temporal where obsID = pp.obsID )"
			+ " order by ctype2";
		doNavigate(expectValid);
	}
	//@Test
	public void testBadGroupBy() {
		boolean expectValid = false;
		_query = "select "
			+ "ctype1, ctype2"
			+ " from spatial pp "
			+ " where cd1_1 > 432.1 "
			+ " group by BADctype1 "
			+ " having BADctype2 > 2"
			+ "    and ctype2 in (select distinct exptime from temporal where obsID = pp.BADobsID )"
			+ " order by 2";
		doNavigate(expectValid);
	}

	@Test
	public void testSubSelect() {
		boolean expectValid = false;
		_query = "select top 123 "
			+ " pp.*, aa.*, shape, observation.temporal.*, observation.spatial.*, BADctype1, BADTABLE.ctype2, BADSCHEMA.BADTABLE.BADctype2 "
			+ " from observation.plane pp, (select col2 from spatial ) as qq"
			+ " where cd1_1 > 432.1";
		doNavigate(expectValid);
	}
	@Test
	public void testAllColumns() {
		boolean expectValid = true;
		_query = "select top 123 * "
			+ " from observation.plane pp join observation.spatial as ss using (obsID) "
			+ " where cd1_1 > 432.1";
		doNavigate(expectValid);
	}
	//@Test
	public void testGetTapSelectItems() {
		boolean expectValid = true;
		_query = "select top 123 "
			+ " pp.*, "
			+ " spatial.*, "
			+ " tt.exptime as et"
			+ " from observation.plane as pp,"
			+ "    spatial, "
			+ "    (select exptime from temporal) as tt "
			+ " where cd1_1 > 432.1";
		doNavigate(expectValid);
	}
	//@Test
	public void testFunction() {
        boolean expectValid = true;
        _query = "select long(32,44), lat(444,99) from TAP_SCHEMA.AllDataTypes ";
        doNavigate(expectValid);
	}
    //@Test
    public void testCaseSensitive1() {
        boolean expectValid = true;

        _query = " select POSITION_BOUNDS_CENTER as COORDS, "
            + " long(position_bounds_center), "
            + " lat(position_bounds_center) as DEC "
            + " from CAOM.Plane limit 1";

        doNavigate(expectValid);
    }
    @Test
    public void testSubselect() {
        boolean expectValid = true;

        _query = "select  t_string, aa.t_bytes, bb.* from tap_schema.alldatatypes as aa, tap_schema.tables as bb " +
        " where aa.t_string = bb.utype " +
        " and aa.t_string in (select utype from bb) " +
        " and bb.t_int in (select xxx from yyyy) " +
        " and pppp = qqqq";

        doNavigate(expectValid);
    }


}
