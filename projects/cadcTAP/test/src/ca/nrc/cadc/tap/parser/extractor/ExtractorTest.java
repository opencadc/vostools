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

package ca.nrc.cadc.tap.parser.extractor;

import ca.nrc.cadc.tap.parser.ParserUtil;
import ca.nrc.cadc.tap.parser.TestUtil;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import net.sf.jsqlparser.statement.Statement;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.tap.parser.navigator.FromItemNavigator;
import ca.nrc.cadc.tap.parser.navigator.ReferenceNavigator;
import ca.nrc.cadc.tap.parser.navigator.SelectNavigator;
import ca.nrc.cadc.tap.schema.ColumnDesc;
import ca.nrc.cadc.tap.schema.FunctionDesc;
import ca.nrc.cadc.tap.schema.ParamDesc;
import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.util.Log4jInit;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;

/**
 * test the extraction of select list
 * 
 * @author Sailor Zhang
 *
 */
public class ExtractorTest
{
    private static final Logger log = Logger.getLogger(ExtractorTest.class);

    private static final String ADQL_DOUBLE = "adql:DOUBLE";
    private static final String ADQL_VARCHAR = "adql:VARCHAR";

    SelectListExpressionExtractor _en;
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
        Log4jInit.setLevel("ca.nrc.cadc.tap", Level.INFO);
        TAP_SCHEMA = TestUtil.loadDefaultTapSchema();
    }

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception
    {
        _en = new SelectListExpressionExtractor(TAP_SCHEMA);
        _rn = null;
        _fn = null;
        _sn = new SelectListExtractor(_en, _rn, _fn);
    }

    private void doit(String query, List<ParamDesc> expectedList)
    {
        boolean isValidQuery = true;
        doit(query, expectedList, isValidQuery);
    }

    private void doit(String query, List<ParamDesc> expectedList, boolean isValidQuery)
    {
        log.debug("query: "  + query);
        Statement s = null;
        try
        {
            boolean exceptionThrown = false;
            s = ParserUtil.receiveQuery(query);
            try
            {
                ParserUtil.parseStatement(s, _sn);
                log.debug("statement: " + s);
                List<ParamDesc> selectList = _en.getSelectList();
                for (int i = 0; i < expectedList.size(); i++)
                {
                    ParamDesc expected = expectedList.get(0);
                    ParamDesc actual = selectList.get(0);
                    log.debug("expected: " + expected);
                    log.debug("actual: " + actual);

                    Assert.assertEquals(expected.name, actual.name);
                    Assert.assertEquals(expected.alias, actual.alias);
                    Assert.assertEquals(expected.description, actual.description);
                    Assert.assertEquals(expected.datatype, actual.datatype);
                    Assert.assertEquals(expected.utype, actual.utype);
                    Assert.assertEquals(expected.size, actual.size);
                    Assert.assertEquals(expected.unit, actual.unit);
                    Assert.assertEquals(expected.ucd, actual.ucd);
                }
            }
            catch (Exception e)
            {
                exceptionThrown = true;
                if (!isValidQuery)
                    log.debug("expected exception: " + e.getMessage());
                else
                    log.error(e);
            }
            Assert.assertTrue(isValidQuery != exceptionThrown);
        } catch (Exception ae)
        {
            ae.printStackTrace(System.out);
            fail(ae.toString());
        }
    }

    @Test
    public void testAll()
    {
        String query = "select t.table_name as tn, keys.from_table from tap_schema.tables t, tap_schema.keys where t.utype=keys.utype";

        List<ParamDesc> expectedList = new ArrayList<ParamDesc>();
        ColumnDesc columnDesc = new ColumnDesc("tables", "table_name", null, null, null, null, ADQL_VARCHAR, 16);
        ParamDesc paramDesc = new ParamDesc(columnDesc, "tn");
        expectedList.add(paramDesc);
        columnDesc = new ColumnDesc("keys", "from_table", null, null, null, null, ADQL_VARCHAR, 16);
        paramDesc = new ParamDesc(columnDesc, null);
        expectedList.add(paramDesc);
        
        doit(query, expectedList);
    }

    @Test
    public void testQuotedCol()
    {
        String query = "select \"table_name\" from tap_schema.tables";

        List<ParamDesc> expectedList = new ArrayList<ParamDesc>();
        ColumnDesc columnDesc = new ColumnDesc("tables", "table_name", null, null, null, null, ADQL_VARCHAR, 16);
        ParamDesc paramDesc = new ParamDesc(columnDesc, null);
        expectedList.add(paramDesc);

        doit(query, expectedList);
    }

    @Test
    public void testColumnExpression()
    {
        String query = "select position_center_ra from caom.siav1";

        List<ParamDesc> expectedList = new ArrayList<ParamDesc>();
        ColumnDesc columnDesc = new ColumnDesc("siav1", "position_center_ra", null, null, null, null, ADQL_DOUBLE, null);
        ParamDesc paramDesc = new ParamDesc(columnDesc, null);
        expectedList.add(paramDesc);

        doit(query, expectedList);
    }

    @Test
    public void testFunctionExpression()
    {
        String query = "select area(position_center_ra) from caom.siav1";

        List<ParamDesc> expectedList = new ArrayList<ParamDesc>();
        FunctionDesc functionDesc = new FunctionDesc("AREA", null, ADQL_DOUBLE);
        ParamDesc paramDesc = new ParamDesc(functionDesc, null);
        expectedList.add(paramDesc);

        doit(query, expectedList);
    }

    @Test
    public void testAllColumnmsExpression()
    {
        boolean isValidQuery = false;
        String query = "select * from caom.siav1";
        doit(query, null, isValidQuery);
    }

    @Test
    public void testAllTableColumnmsExpression()
    {
        boolean isValidQuery = false;
        String query = "select siav1.* from caom.siav1";
        doit(query, null, isValidQuery);
    }

    @Test
    public void testArgumentDatatypeFunctionExpression()
    {
        String query = "select max(position_center_ra) from caom.siav1";

        List<ParamDesc> expectedList = new ArrayList<ParamDesc>();
        FunctionDesc functionDesc = new FunctionDesc("MAX", null, ADQL_DOUBLE);
        ParamDesc paramDesc = new ParamDesc(functionDesc, null);
        expectedList.add(paramDesc);

        doit(query, expectedList);
    }

    @Test
    public void testNestedFunctionExpression()
    {
        String query = "select max(area(position_center_ra)) from caom.siav1";

        List<ParamDesc> expectedList = new ArrayList<ParamDesc>();
        FunctionDesc functionDesc = new FunctionDesc("MAX", null, ADQL_DOUBLE);
        ParamDesc paramDesc = new ParamDesc(functionDesc, null);
        expectedList.add(paramDesc);

        doit(query, expectedList);
    }

    @Test
    public void testNestedFunctionExpression2()
    {
        String query = "select area(max(position_center_ra)) from caom.siav1";

        List<ParamDesc> expectedList = new ArrayList<ParamDesc>();
        FunctionDesc functionDesc = new FunctionDesc("AREA", null, ADQL_DOUBLE);
        ParamDesc paramDesc = new ParamDesc(functionDesc, null);
        expectedList.add(paramDesc);

        doit(query, expectedList);
    }

    @Test
    public void testRecursiveNestedFunctionExpression()
    {
        String query = "select max(min(avg(position_center_ra))) from caom.siav1";

        List<ParamDesc> expectedList = new ArrayList<ParamDesc>();
        FunctionDesc functionDesc = new FunctionDesc("MAX", null, ADQL_DOUBLE);
        ParamDesc paramDesc = new ParamDesc(functionDesc, null);
        expectedList.add(paramDesc);

        doit(query, expectedList);
    }

    @Test
    public void testConstantExpression()
    {
        String query = "select 1 from caom.siav1";

        List<ParamDesc> expectedList = new ArrayList<ParamDesc>();
        ParamDesc paramDesc = new ParamDesc("1", "1", ADQL_VARCHAR);
        expectedList.add(paramDesc);

        doit(query, expectedList);
    }

    @Test
    public void testConstantWithAliasExpression()
    {
        String query = "select 1 as one from caom.siav1";

        List<ParamDesc> expectedList = new ArrayList<ParamDesc>();
        ParamDesc paramDesc = new ParamDesc("1", "one", ADQL_VARCHAR);
        expectedList.add(paramDesc);

        doit(query, expectedList);
    }

    @Test
    public void testFunctionWithConstantExpression()
    {
        String query = "select max(1) from caom.siav1";

        List<ParamDesc> expectedList = new ArrayList<ParamDesc>();
        FunctionDesc functionDesc = new FunctionDesc("MAX", null, ADQL_VARCHAR);
        ParamDesc paramDesc = new ParamDesc(functionDesc, null);
        expectedList.add(paramDesc);

        doit(query, expectedList);
    }

    @Test
    public void testFunctionWithConstantAndAliasExpression()
    {
        String query = "select max(1) as foo from caom.siav1";

        List<ParamDesc> expectedList = new ArrayList<ParamDesc>();
        FunctionDesc functionDesc = new FunctionDesc("MAX", null, ADQL_VARCHAR);
        ParamDesc paramDesc = new ParamDesc(functionDesc, "foo");
        expectedList.add(paramDesc);

        doit(query, expectedList);
    }

}
