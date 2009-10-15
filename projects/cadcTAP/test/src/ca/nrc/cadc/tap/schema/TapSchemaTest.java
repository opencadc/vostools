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

package ca.nrc.cadc.tap.schema;

import javax.sql.DataSource;
import junit.framework.TestCase;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import ca.nrc.cadc.util.LoggerUtil;

public class TapSchemaTest extends TestCase
{
    private static final String JDBC_URL = "jdbc:postgresql://cvodb0/cvodb";
    private static final String USERNAME = "cadcuser51";
    private static final String PASSWORD = "MhU7nuvP5/67A:31:30";
    private static final boolean SUPPRESS_CLOSE = false;

    private static final String TAP_SCHEMA_NAME = "TAP_SCHEMA";

    private static Logger log;

    private TapSchema tapSchema;

    static
    {
        log = Logger.getLogger(TapSchemaTest.class);

        // default log level is debug.
        log.setLevel((Level)Level.DEBUG);
    }

    public TapSchemaTest(String testName)
    {
        super(testName);

    }

    @Override
    protected void setUp()
        throws Exception
    {
       LoggerUtil.initialize(new String[] { "test", "ca.nrc.cadc" }, new String[] { "-d" });
        Class.forName("org.postgresql.Driver");
        DataSource ds = new SingleConnectionDataSource(JDBC_URL, USERNAME, PASSWORD, SUPPRESS_CLOSE);
        TapSchemaDAO dao = new TapSchemaDAO(ds);
        tapSchema = dao.get();
    }

    @Override
    protected void tearDown()
    {

    }

    /**
     * TapSchema, TapSchema.schemas, and TapSchemas.keys must not be null;
     */
    public void testTapSchema()
        throws Exception
    {
        log.debug("Asserting TapSchmea is not null.");
        assertNotNull("TapSchema is null", tapSchema);

        log.debug("Asserting TapSchmea.schemas is not null.");
        assertNotNull("TapSchema.schemas is null", tapSchema.schemas);

        log.debug("Asserting TapSchmea.keys is not null.");
        assertNotNull("TapSchema.keys is null", tapSchema.keys);
    }

    /**
     * Should only be a single schema with schemaName TAP_SCHEMA.
     */
    public void testSchemas()
    { 
        int count = 0;
        for (Schema schema : tapSchema.schemas)
        {
            log.debug("Schema.schemaName: " + schema.schemaName);
            if (schema.schemaName.equals(TAP_SCHEMA_NAME))
                count++;
        }
        log.debug("Asserting TapSchmea should only have a single Schema named " + TAP_SCHEMA_NAME);
        assertEquals("Should only be a single schema with schemaName " + TAP_SCHEMA_NAME, 1, count);
    }

    /**
     * Every table should have the Schema name TAP_SCHEMA.
     */
    public void testTables()
    {
        for (Schema schema : tapSchema.schemas)
        {
            log.debug("Schema.schemaName: " + schema.schemaName);
            if (schema.schemaName.equals(TAP_SCHEMA_NAME))
            {
                log.debug("Schema.schemaName: " + schema.schemaName);
                log.debug("Asserting Schema.tables is not null.");
                assertNotNull("List of Schema tables must not be null", schema.tables);
                for (Table table : schema.tables)
                {
                    log.debug("Table.schemaName:" + table.schemaName);
                    log.debug("Asserting Table schema name equals Schema name.");
                    assertEquals("Table schemaName must be " + TAP_SCHEMA_NAME, TAP_SCHEMA_NAME, table.schemaName);
                }
            }
        }
    }

    /**
     * Every Column should have the Table name.
     */
    public void testTableColumns()
    {
        for (Schema schema : tapSchema.schemas)
        {
            log.debug("Schema.schemaName: " + schema.schemaName);
            if (schema.schemaName.equals(TAP_SCHEMA_NAME))
            {
                for (Table table : schema.tables)
                {
                    log.debug("Table:" + table.toString());
                    log.debug("Table.tableName:" + table.tableName);
                    log.debug("Asserting Table.columns is not null.");
                    assertNotNull("List of Table columns must not be null", table.columns);
                    for (Column column : table.columns)
                    {
                        log.debug("Column.tableName:" + column.tableName);
                        log.debug("Asserting Column table name equals Table name.");
                        assertEquals("Column table name must equal Table name", table.tableName, column.tableName);
                    }
                }
            }
        }
    }
    
}
