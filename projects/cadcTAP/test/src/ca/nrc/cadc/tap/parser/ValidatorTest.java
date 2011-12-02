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
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.tap.parser.navigator.ExpressionNavigator;
import ca.nrc.cadc.tap.parser.navigator.FromItemNavigator;
import ca.nrc.cadc.tap.parser.navigator.ReferenceNavigator;
import ca.nrc.cadc.tap.parser.navigator.SelectNavigator;
import ca.nrc.cadc.tap.parser.schema.TapSchemaColumnValidator;
import ca.nrc.cadc.tap.parser.schema.TapSchemaTableValidator;
import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.util.Log4jInit;
import org.apache.log4j.Logger;

/**
 * test query validator
 * 
 * @author Sailor Zhang
 *
 */
public class ValidatorTest
{
    private static final Logger log = Logger.getLogger(ValidatorTest.class);

    public String _query;

    ExpressionNavigator _en;
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
        Log4jInit.setLevel("ca.nrc.cadc", org.apache.log4j.Level.INFO);
        TAP_SCHEMA = TestUtil.loadDefaultTapSchema();
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
        _en = new ExpressionNavigator();
        _rn = new TapSchemaColumnValidator(TAP_SCHEMA);
        _fn = new TapSchemaTableValidator(TAP_SCHEMA);
        _sn = new SelectNavigator(_en, _rn, _fn);
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception
    {
    }

    private void doit(boolean expectValid) {
        Statement s = null;
        boolean exceptionHappens = false;
        try {
            log.debug("query: " + _query);
            s = ParserUtil.receiveQuery(_query);
            log.debug("statement: " + s);
            ParserUtil.parseStatement(s, _sn);
        } catch (Exception ae) {
            exceptionHappens = true;
            log.debug("exception: " + ae.getMessage());
        }
        Assert.assertTrue(expectValid != exceptionHappens);
    }

    @Test
    public void testAllTableColumnsBad() {
        boolean expectValid = false;
        _query = "select a.*, tap_schema.tables.* from tap_schema.alldatatypes , tap_schema.tables t where t_string = utype";
        doit(expectValid);
    }

    @Test
    public void testAllTableColumns() {
        boolean expectValid = true;
        _query = "select a.*, tap_schema.tables.* from tap_schema.alldatatypes a, tap_schema.tables t where t_string = utype";
        doit(expectValid);
    }
    @Test
    public void testAllTableColumns2() {
        boolean expectValid = true;
        _query = "select alldatatypes.*, tap_schema.tables.* from tap_schema.alldatatypes a, tap_schema.tables t where t_string = utype";
        doit(expectValid);
    }
    @Test
    public void testColumnBad() {
        boolean expectValid = false;
        _query = "select t_box, badCol from tap_schema.alldatatypes a, tap_schema.tables t where a.t_string = t.table_name";
        doit(expectValid);
    }
    @Test
    public void testFromTableBad() {
        boolean expectValid = false;
        _query = "select t_box, t.*, bad.* from tap_schema.alldatatypes a, tap_schema.tables t, badTable tad where a.t_string = t.table_name";
        doit(expectValid);
    }
    @Test
    public void testSubSelect() {
        boolean expectValid = true;
        _query = "select utype, t.description, t_long, alldatatypes.t_double " +
        		" from tap_schema.alldatatypes a, tap_schema.tables t  " +
        		" where a.t_string = t.table_name and a.t_long < 1234 " +
        		" and t.utype in (select t_string from tap_schema.alldatatypes where t_integer = 4321) " +
        		" order by t_long desc";
        doit(expectValid);
    }
    @Test
    public void testSubSelectAndWhereColumnBad() {
        boolean expectValid = false;
        _query = "select utype, t.description " +
                " from tap_schema.tables t  " +
                " where t.utype in (select t_string from tap_schema.alldatatypes where t_integer = 4321) " +
                " and t_long < 200 " +
                " order by t_long desc";
        doit(expectValid);
    }
    @Test
    public void testSubSelectAndWhereColumn() {
        boolean expectValid = true;
        _query = "select utype, t.description " +
                " from tap_schema.tables t  " +
                " where t.utype in (select t_string from tap_schema.alldatatypes where t_integer = 4321) " +
                " and table_name = 'abc' " +
                " order by schema_name desc";
        doit(expectValid);
    }
    @Test
    public void testSubSelectMulti() {
        boolean expectValid = true;
        _query = "select * from tap_schema.columns " +
        		"     where table_name in " +
        		"         (select table_name from tap_schema.tables " +
        		"             where utype in " +
        		"                     (select key_id " +
        		"                         from tap_schema.keys where key_id <> 'xyz'" + 
        		"                         and from_table = 'abcd') " +
        		"                 and table_name in " +
        		"                         (select key_id from tap_schema.keys where target_table = 'hello'))";
        doit(expectValid);
    }
    @Test
    public void testFunction() {
        boolean expectValid = true;
        _query = "select substring(utype, 2, 4), t.description " +
                " from tap_schema.tables t  " +
                " where lower(t.utype) in (select lower(t_string) from tap_schema.alldatatypes where abs(t_integer) = 4321) " +
                " and lower(table_name) = 'abc' " +
                " order by schema_name desc";
        doit(expectValid);
    }
    @Test
    public void testFunctionBad() {
        boolean expectValid = false;
        _query = "select substring(utypeBad, 2, 4), t.description " +
                " from tap_schema.tables t  " +
                " where lower(t.utypeBad) in (select lower(t_stringBad) from tap_schema.alldatatypes where abs(t_integerBad) = 4321) " +
                " and lower(table_nameBad) = 'abc' " +
                " order by schema_name desc";
        doit(expectValid);
    }
    
        
}
