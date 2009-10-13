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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Given a DataSource to a TAP_SCHEMA, returns a TapSchema
 * object containing the TAP_SCHEMA data.
 * 
 */
public class TapSchemaDAO
{
    // SQL to select all rows from TAP_SCHEMA.schemas.
    private static final String SELECT_SCHEMAS =
            "select " +
                "schema_name, description, utype " +
            "from " +
                "tap_schema.schemas " +
            "order by schema_name";

    // SQL to select all rows from TAP_SCHEMA.tables.
    private static final String SELECT_TABLES =
            "select " +
                "schema_name, table_name, description, utype " +
            "from " +
                "tap_schema.tables " +
            "order by schema_name";

    // SQL to select all rows from TAP_SCHEMA.colums.
    private static final String SELECT_COLUMNS =
            "select " +
                "table_name, column_name, description, utype, ucd, unit, datatype, size " +
            "from " +
                "tap_schema.columns " +
            "order by table_name";

    // SQL to select all rows from TAP_SCHEMA.keys.
    private static final String SELECT_KEYS =
            "select " +
                "key_id, from_table, target_table " +
            "from " +
                "tap_schema.keys " +
            "order by key_id";

    // SQL to select all rows from TAP_SCHEMA.key_columns.
    private static final String SELECT_KEY_COLUMNS =
            "select " +
                "key_id, from_column, target_column " +
            "from " +
                "tap_schema.key_columns " +
            "order by key_id";

    // Database connection.
    private JdbcTemplate jdbc;

    /**
     * Construct a new TapSchemaDAO using the specified DataSource.
     *
     * @param dataSource TAP_SCHEMA DataSource.
     */
    public TapSchemaDAO(DataSource dataSource)
    {
        jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * Creates and returns a TapSchema object representing all of the
     * data in TAP_SCHEMA.
     *
     * @return TapSchema containing all of the data from TAP_SCHEMA.
     */
    public TapSchema get()
    {
        TapSchema tapSchema = new TapSchema();
        
        // List of TAP_SCHEMA.schemas
        tapSchema.schemas = jdbc.query(SELECT_SCHEMAS, new SchemaMapper());

        // List of TAP_SCHEMA.tables
        List<Table> tables = jdbc.query(SELECT_TABLES, new TableMapper());

        // Add the Tables to the Schemas.
        addTablesToSchemas(tapSchema.schemas, tables);

        // List of TAP_SCHEMA.columns
        List<Column> columns = jdbc.query(SELECT_COLUMNS, new ColumnMapper());

        // Add the Columns to the Tables.
        addColumnsToTables(tables, columns);

        // List of TAP_SCHEMA.keys
        tapSchema.keys = jdbc.query(SELECT_KEYS, new KeyMapper());

        // List of TAP_SCHEMA.key_columns
        List<KeyColumn> keyColumns = jdbc.query(SELECT_KEY_COLUMNS, new KeyColumnMapper());

        // Add the KeyColumns to the Keys.
        addKeyColumnsToKeys(tapSchema.keys, keyColumns);

        return tapSchema;
    }

    /**
     * Creates Lists of Tables with a common Schema name, then adds the Lists to the Schemas.
     *
     * @param schemas List of Schemas.
     * @param tables List of Tables.
     */
    private void addTablesToSchemas(List<Schema> schemas, List<Table> tables)
    {
        for (Table table : tables)
        {
            for (Schema schema : schemas)
            {
                if (table.schemaName.equals(schema.schemaName))
                {
                    if (schema.tables == null)
                        schema.tables = new ArrayList();
                    schema.tables.add(table);
                    break;
                }
            }
        }
    }

    /**
     * Creates Lists of Columns with a common Table name, then adds the Lists to the Tables.
     *
     * @param tables List of Tables.
     * @param columns List of Columns.
     */
    private void addColumnsToTables(List<Table> tables, List<Column> columns)
    {
        for (Column column : columns)
        {
            for (Table table : tables)
            {
                if (column.tableName.equals(table.tableName))
                {
                    if (table.columns == null)
                        table.columns = new ArrayList();
                    table.columns.add(column);
                    break;
                }
            }
        }
    }

    /**
     * Creates Lists of KeyColumns with a common Key keyID, then adds the Lists to the Keys.
     *
     * @param keys List of Keys.
     * @param keyColumns List of KeyColumns.
     */
    private void addKeyColumnsToKeys(List<Key> keys, List<KeyColumn> keyColumns)
    {
        for (KeyColumn keyColumn : keyColumns)
        {
            for (Key key : keys)
            {
                if (keyColumn.keyId.equals(key.keyId))
                {
                    if (key.keyColumns == null)
                        key.keyColumns = new ArrayList();
                    key.keyColumns.add(keyColumn);
                    break;
                }
            }
        }
    }

    /**
     * Creates a List of Schema populated from the ResultSet.
     */
    private static final class SchemaMapper implements RowMapper
    {
        public Object mapRow(ResultSet rs, int rowNum)
            throws SQLException
        {
            Schema schema = new Schema();
            schema.schemaName = rs.getString("schema_name");
            schema.description = rs.getString("description");
            schema.utype = rs.getString("utype");
            return schema;
        }
    }

    /**
     * Creates a List of Table populated from the ResultSet.
     */
    private static final class TableMapper implements RowMapper
    {
        public Object mapRow(ResultSet rs, int rowNum)
            throws SQLException
        {
            Table table = new Table();
            table.schemaName = rs.getString("schema_name");
            table.tableName = rs.getString("table_name");
            table.description = rs.getString("description");
            table.utype = rs.getString("utype");
            return table;
        }
    }

    /**
     * Creates a List of Column populated from the ResultSet.
     */
    private static final class ColumnMapper implements RowMapper
    {
        public Object mapRow(ResultSet rs, int rowNum)
            throws SQLException
        {
            Column column = new Column();
            column.tableName = rs.getString("table_name");
            column.columnName = rs.getString("column_name");
            column.description = rs.getString("description");
            column.utype = rs.getString("utype");
            column.ucd = rs.getString("ucd");
            column.unit = rs.getString("unit");
            column.datatype = rs.getString("datatype");
            column.size = rs.getInt("size");
            return column;
        }
    }

    /**
     * Creates a List of Key populated from the ResultSet.
     */
    private static final class KeyMapper implements RowMapper
    {
        public Object mapRow(ResultSet rs, int rowNum)
            throws SQLException
        {
            Key key = new Key();
            key.keyId = rs.getString("key_id");
            key.fromTable = rs.getString("from_table");
            key.targetTable = rs.getString("target_table");
            return key;
        }
    }

    /**
     * Creates a List of KeyColumn populated from the ResultSet.
     */
    private static final class KeyColumnMapper implements RowMapper
    {
        public Object mapRow(ResultSet rs, int rowNum)
            throws SQLException
        {
            KeyColumn keyColumn = new KeyColumn();
            keyColumn.keyId = rs.getString("key_id");
            keyColumn.fromColumn = rs.getString("from_column");
            keyColumn.targetColumn = rs.getString("target_column");
            return keyColumn;
        }
    }

}
