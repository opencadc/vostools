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
package ca.nrc.cadc.tap.parser.adql;

import static org.junit.Assert.fail;
import net.sf.jsqlparser.statement.Statement;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.tap.parser.adql.exception.AdqlException;
import ca.nrc.cadc.tap.parser.adql.impl.postgresql.pgsphere.AdqlManagerImpl;
import ca.nrc.cadc.util.LoggerUtil;

/**
 * 
 * @author Sailor Zhang
 *
 */
public class AdqlParserTest {
	private AdqlParser adqlParser;
	public String adqlInput;
	public String sqlOutput;
	
	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
        LoggerUtil.initialize(new String[] { "test", "ca.nrc.cadc" }, new String[] { "-d" });
		
		this.adqlInput = "select shape, ctype1, ctype2 from observation.plane p,  observation.spatial s where p.obsID =s.obsID and cd1_1 > 432.1";
		this.adqlInput = "select shape, ctype1, ctype2 from observation.plane join observation.spatial using (obsID) where cd1_1 > 432.1";

		this.adqlInput = "select top 123 shape, observation.spatial.*, BADctype1, BADTABLE.ctype2, BADSCHEMA.BADTABLE.BADctype2 from observation.plane join observation.spatial using (obsID) where cd1_1 > 432.1";

		AdqlManager manager = new AdqlManagerImpl(null, null);
		this.adqlParser = new AdqlParser(manager);
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}




	private void doValidate(boolean expectValid) {
		Statement s = null;
		boolean exceptionHappens = false;
		try {
			s = adqlParser.validate(adqlInput);
		} catch (AdqlException ae) {
			exceptionHappens = true;
			ae.printStackTrace(System.out);
			if (expectValid)
				fail(ae.toString());
			else
				assert(true);
		}
		System.out.println(s);
		if (expectValid) {
			if (exceptionHappens)
				assert(false);
			else
				assert(true);
		} else {
			if (exceptionHappens)
				assert(true);
			else
				assert(false);
		}
	}

	private void doConvert(boolean expectValid) {
		Statement s=null;
		try {
			s = adqlParser.validate(adqlInput);
			System.out.println(s.toString());
			s = adqlParser.convert(s);
			System.out.println(s.toString());
		} catch (AdqlException ae) {
			ae.printStackTrace(System.out);
			fail(ae.toString());
		}
	}

	//@Test
	public void testColumnReference() {
		this.adqlInput = "select top 123 "
			+ "ctype1, ctype2, ctype2 "
			+ " from observation.plane pp join observation.spatial as ss using (obsID) "
			+ " where cd1_1 > 432.1 "
			+ " group by BADctype2"
			+ " order by 22";
		boolean expectValid = false;
		doValidate(expectValid);
	}

	//@Test
	public void testColumnIndexReference() {
		boolean expectValid = false;
		this.adqlInput = "select top 123 "
			+ "ctype1, ctype2, ctype2 "
			+ " from observation.plane pp join observation.spatial as ss using (obsID) "
			+ " where cd1_1 > 432.1 "
			+ " order by 22";
		doValidate(expectValid);
	}
	//@Test
	public void testNegativeColumnIndexReference() {
		boolean expectValid = false;
		this.adqlInput = "select top 123 "
			+ "ctype1, ctype2, ctype2 "
			+ " from observation.plane pp join observation.spatial as ss using (obsID) "
			+ " where cd1_1 > 432.1 "
			+ " order by -2";
		doValidate(expectValid);
	}
	//@Test
	public void testGoodColumnIndexReference() {
		boolean expectValid = true;
		this.adqlInput = "select top 123 "
			+ "ctype1, ctype2, ctype2 "
			+ " from observation.plane pp join observation.spatial as ss using (obsID) "
			+ " where cd1_1 > 432.1 "
			+ " order by 2";
		doValidate(expectValid);
	}
	//@Test
	public void testGoodAllTableColumnsSubSelect() {
		boolean expectValid = true;
		this.adqlInput = "select qq.*"
			+ " from (select filter_lo, obsID from spectral) as qq, spatial pp"
			+ " where cd1_1 > 432.1 ";
		doValidate(expectValid);
	}
	//@Test
	public void testGoodAllTableColumnsSubSelect1() {
		boolean expectValid = true;
		this.adqlInput = "select qq.*"
			+ " from spatial pp, (select filter_lo, obsID from spectral) as qq"
			+ " where cd1_1 > 432.1 ";
		doValidate(expectValid);
	}
	//@Test
	public void testGoodAllTableColumnsSubSelect2() {
		boolean expectValid = false;
		this.adqlInput = "select qq.*"
			+ " from (select filter_lo, obsID from spectral as ss where obsID = pp.obsID) as qq, spatial pp"
			+ " where cd1_1 > 432.1 ";
		doValidate(expectValid);
	}
	//@Test
	public void testGoodAllTableColumnsSubSelect4() {
		boolean expectValid = false;
		this.adqlInput = "select qq.*"
			+ " from spatial pp, (select filter_lo, obsID from spectral as ss where obsID = pp.obsID) as qq"
			+ " where cd1_1 > 432.1 ";
		doValidate(expectValid);
	}
	//@Test
	public void testGoodFromSubSelect() {
		boolean expectValid = true;
		this.adqlInput = "select top 23 "
			+ "ctype1, ctype2, filter_lo "
			+ " from (select filter_lo, obsID from spectral) as qq, spatial pp"
			+ " where cd1_1 > 432.1 "
			+ "    and pp.obsID = qq.obsID"
			+ " order by 2";
		doValidate(expectValid);
	}
	//@Test
	public void testAmbiguousTable() {
		boolean expectValid = false;
		this.adqlInput = "select project, shape from plane" ;
		doValidate(expectValid);
	}
	//@Test
	public void testInvalidTable() {
		boolean expectValid = false;
		this.adqlInput = "select project, shape from spectral" ;
		doValidate(expectValid);
	}
	//@Test
	public void testValidTable() {
		boolean expectValid = true;
		this.adqlInput = "select filter_lo, s.filter_hi from spectral s" ;
		doValidate(expectValid);
	}
	//@Test
	public void testJoins() {
		boolean expectValid = false;
		this.adqlInput = "select aa.aaa, bb.bbb, cc.ccc from tablea aa, tableb bb, tablec cc where aa.a = bb.c and bb.a=cc.d" ;
		this.adqlInput = "select aa.aaa, bb.bbb, cc.ccc "
			+ " from (select aaa from tablea) as aa," +
					" tableb bb join (select ccc from tablec) using (ddd) where aa.a = bb.c and bb.a=cc.d" ;
		doValidate(expectValid);
	}
	//@Test
	public void testAmbiguousTableName() {
		boolean expectValid = false;
		this.adqlInput = "select s.*"
			+ " from plane , spectral s" 
			+ " where s.obsID = plane.obsID";
		doValidate(expectValid);
	}
	//@Test
	public void testAmbiguousTableName2() {
		boolean expectValid = true;
		this.adqlInput = "select s.*"
			+ " from observation.plane , spectral s" 
			+ " where s.obsID = plane.obsID";
		doValidate(expectValid);
	}
	//@Test
	public void testAmbiguousWhereColumn() {
		boolean expectValid = false;
		this.adqlInput = "select s.*"
			+ " from observation.plane , spectral s" 
			+ " where obsID = 333";
		doValidate(expectValid);
	}
	//@Test
	public void testAmbiguousWhereColumn2() {
		boolean expectValid = true;
		this.adqlInput = "select s.*"
			+ " from observation.plane , spectral s" 
			+ " where project = 333";
		doValidate(expectValid);
	}
	//@Test
	public void testGoodWhereSubSelect() {
		boolean expectValid = true;
		this.adqlInput = "select "
			+ "ctype1, ctype2"
			+ " from spatial pp "
			+ "  "
			+ " where cd1_1 > 432.1 "
			+ "    and pp.obsID in (select distinct obsID from spectral where filter_lo > 3212 and obsID = ctype1 or filter_lo < pp.ctype2) "
			+ "    and exists (select obsID from spectral where filter_lo > 3212 and obsID = ctype1 or filter_lo < pp.ctype2) "
			+ " order by 2";
		doValidate(expectValid);
	}
	//@Test
	public void testBadWhereSubSelect() {
		boolean expectValid = false;
		this.adqlInput = "select "
			+ "ctype1, ctype2"
			+ " from spatial pp "
			+ "  "
			+ " where cd1_1 > 432.1 "
			+ "    and pp.obsID in (select distinct obsID from spectral where filter_lo > 3212 and obsID = ctype1 or filter_lo < p.ctype2) "
			+ "    and exists (select obsID from spectral where filter_lo > 3212 and obsID = ctype3 or filter_lo < pp.ctype2) "
			+ " order by 2";
		doValidate(expectValid);
	}
	//@Test
	public void testBadHaving() {
		// having without group_by
		boolean expectValid = false;
		this.adqlInput = "select "
			+ "ctype1, ctype2"
			+ " from spatial pp "
			+ " where cd1_1 > 432.1 "
			+ " having ctype2 > 2"
			+ " order by 2";
		doValidate(expectValid);
	}
	//@Test
	public void testGoodGroupBy() {
		boolean expectValid = true;
		this.adqlInput = "select "
			+ "ctype1, ctype2"
			+ " from spatial pp "
			+ " where cd1_1 > 432.1 "
			+ " group by ctype1 "
			+ " having ctype2 > 2 "
			+ "    and ctype2 in (select distinct exptime from temporal where obsID = pp.obsID )"
			+ " order by 2";
		doValidate(expectValid);
	}
	//@Test
	public void testGoodOrderBy() {
		boolean expectValid = true;
		this.adqlInput = "select "
			+ "ctype1, ctype2"
			+ " from spatial pp "
			+ " where cd1_1 > 432.1 "
			+ " group by ctype1 "
			+ " having ctype2 > 2 "
			+ "    and ctype2 in (select distinct exptime from temporal where obsID = pp.obsID )"
			+ " order by ctype2";
		doValidate(expectValid);
	}
	//@Test
	public void testBadGroupBy() {
		boolean expectValid = false;
		this.adqlInput = "select "
			+ "ctype1, ctype2"
			+ " from spatial pp "
			+ " where cd1_1 > 432.1 "
			+ " group by BADctype1 "
			+ " having BADctype2 > 2"
			+ "    and ctype2 in (select distinct exptime from temporal where obsID = pp.BADobsID )"
			+ " order by 2";
		doValidate(expectValid);
	}

	//@Test
	public void testAllTableColumns() {
		boolean expectValid = false;
		this.adqlInput = "select top 123 "
			+ " pp.*, aa.*, shape, observation.temporal.*, observation.spatial.*, BADctype1, BADTABLE.ctype2, BADSCHEMA.BADTABLE.BADctype2 "
			+ " from observation.plane pp join observation.spatial as ss using (obsID) "
			+ " where cd1_1 > 432.1";
		doValidate(expectValid);
	}
	//@Test
	public void testAllColumns() {
		boolean expectValid = false;
		this.adqlInput = "select top 123 "
			+ "*, aa.*, shape, observation.temporal.*, observation.spatial.*, BADctype1, BADTABLE.ctype2, BADSCHEMA.BADTABLE.BADctype2 "
			+ " from observation.plane pp join observation.spatial as ss using (obsID) "
			+ " where cd1_1 > 432.1";
		doValidate(expectValid);
	}
    //@Test
    public void testSpoint() {
        boolean expectValid = true;
        this.adqlInput = "select spoint '( 10.1d, -90d)' from observation.plane";
        doValidate(expectValid);
    }
    //@Test
    public void testBox() {
        boolean expectValid = true;
        this.adqlInput = "select BOX('ICRS GEOCENTER', 25.4, -20.0, 10, 10) from observation.plane";
        doValidate(expectValid);
    }
    @Test
    public void testContains() {
        boolean expectValid = true;
        this.adqlInput = "select * from observation.plane where CONTAINS(POINT('ICRS GEOCENTER', 25.0, -19.5), CIRCLE('ICRS GEOCENTER', 25.4, -20.0, 1)) >= 1";
        doValidate(expectValid);
    }
}
