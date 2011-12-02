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
import static org.junit.Assert.fail;
import net.sf.jsqlparser.statement.Statement;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.nrc.cadc.tap.parser.converter.AllColumnConverter;
import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.util.Log4jInit;
import org.apache.log4j.Logger;

/**
 * Test all column converter.
 * 
 * @author Sailor Zhang
 *
 */
public class AllColumnConverterTest
{
    private static final Logger log = Logger.getLogger(AllColumnConverterTest.class);

    public String _query;
    public String _expected = "";

    AllColumnConverter _sn;

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

        _sn = new AllColumnConverter(TAP_SCHEMA);
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception
    {
    }

    private void doit()
    {
        Statement s = null;
        try
        {
            s = ParserUtil.receiveQuery(_query);
            log.debug("query: " + _query);
            ParserUtil.parseStatement(s, _sn);
        }
        catch (Exception ae)
        {
            ae.printStackTrace(System.out);
            fail(ae.toString());
        }
        String sql = s.toString();
        log.debug("expected: " + _expected);
        log.debug("actual: " + sql);
        assertEquals(_expected.toLowerCase(), sql.toLowerCase());
    }

    @Test
    public void testBasic()
    {
        _query = " select * from tap_schema.tables";
        _expected = "select tap_schema.tables.schema_name, tap_schema.tables.table_name, tap_schema.tables.utype, tap_schema.tables.description from tap_schema.tables";
        doit();
    }

    @Test
    public void testAlias()
    {
        _query = " select aa.* from tap_schema.tables as aa";
        _expected = "select aa.schema_name, aa.table_name, aa.utype, aa.description from tap_schema.tables as aa";
        doit();
    }

    @Test
    public void testJoin()
    {
        _query = "select * from tap_schema.keys as aa, tap_schema.tables as bb " +
        		" where aa.key_id = bb.utype";
        _expected = "select aa.key_id, aa.from_table, aa.target_table, aa.utype, aa.description, bb.schema_name, bb.table_name, bb.utype, bb.description from tap_schema.keys as aa , tap_schema.tables as bb where aa.key_id = bb.utype";
        doit();
    }
}
