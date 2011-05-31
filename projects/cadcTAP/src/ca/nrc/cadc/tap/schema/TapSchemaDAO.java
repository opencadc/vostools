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

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Given a DataSource to a TAP_SCHEMA, returns a TapSchema object containing the TAP_SCHEMA data.
 * 
 */
public class TapSchemaDAO
{
    private static Logger log = Logger.getLogger(TapSchemaDAO.class);
    static {
        log.setLevel(Level.ERROR);
    }
    
    // SQL to select all rows from TAP_SCHEMA.schemas.
    private static final String SELECT_SCHEMAS =
            "select schema_name, description, utype " +
            "from tap_schema.schemas";

    // SQL to select all rows from TAP_SCHEMA.tables.
    private static final String SELECT_TABLES = 
            "select schema_name, table_name, description, utype " +
            " from tap_schema.tables";

    // SQL to select all rows from TAP_SCHEMA.colums.
    private static final String SELECT_COLUMNS = 
            "select table_name, column_name, description, utype, ucd, unit, datatype, size " +
            "from tap_schema.columns ";

    // SQL to select all rows from TAP_SCHEMA.keys.
    private static final String SELECT_KEYS =
            "select key_id, from_table, target_table,description,utype " +
            "from tap_schema.keys ";

    // SQL to select all rows from TAP_SCHEMA.key_columns.
    private static final String SELECT_KEY_COLUMNS = 
            "select key_id, from_column, target_column " +
            "from tap_schema.key_columns ";

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
     * Creates and returns a TapSchema object representing all of the data in TAP_SCHEMA.
     * 
     * @return TapSchema containing all of the data from TAP_SCHEMA.
     */
    public TapSchema get()
    {
        TapSchema tapSchema = new TapSchema();

        // List of TAP_SCHEMA.schemas
        tapSchema.schemaDescs = jdbc.query(SELECT_SCHEMAS, new SchemaMapper());

        // List of TAP_SCHEMA.tables
        List<TableDesc> tableDescs = jdbc.query(SELECT_TABLES, new TableMapper());

        // Add the Tables to the Schemas.
        addTablesToSchemas(tapSchema.schemaDescs, tableDescs);

        // List of TAP_SCHEMA.columns
        List<ColumnDesc> columnDescs = jdbc.query(SELECT_COLUMNS, new ColumnMapper());

        // Add the Columns to the Tables.
        addColumnsToTables(tableDescs, columnDescs);

        // List of TAP_SCHEMA.keys
        //tapSchema.keyDescs = jdbc.query(SELECT_KEYS, new KeyMapper());
        List<KeyDesc> keyDescs = jdbc.query(SELECT_KEYS, new KeyMapper());;

        // List of TAP_SCHEMA.key_columns
        List<KeyColumnDesc> keyColumnDescs = jdbc.query(SELECT_KEY_COLUMNS, new KeyColumnMapper());

        // Add the KeyColumns to the Keys.
        addKeyColumnsToKeys(keyDescs, keyColumnDescs);

        // connect foreign keys to the fromTable
        addForeignKeys(tapSchema, keyDescs);

        for (SchemaDesc s : tapSchema.schemaDescs) 
        {
            int num = 0;
            if (s.tableDescs != null)
                num = s.tableDescs.size();
            log.debug("schema " + s.schemaName + " has " + num + " tables");
        }
        
        return tapSchema;
    }

    /**
     * Creates Lists of Tables with a common Schema name, then adds the Lists to the Schemas.
     * 
     * @param schemaDescs List of Schemas.
     * @param tableDescs List of Tables.
     */
    private void addTablesToSchemas(List<SchemaDesc> schemaDescs, List<TableDesc> tableDescs)
    {
        for (TableDesc tableDesc : tableDescs)
        {
            for (SchemaDesc schemaDesc : schemaDescs)
            {
                if (tableDesc.schemaName.equals(schemaDesc.schemaName))
                {
                    schemaDesc.tableDescs.add(tableDesc);
                    break;
                }
            }
        }
    }

    /**
     * Creates Lists of Columns with a common Table name, then adds the Lists to the Tables.
     * 
     * @param tableDescs List of Tables.
     * @param columnDescs List of Columns.
     */
    private void addColumnsToTables(List<TableDesc> tableDescs, List<ColumnDesc> columnDescs)
    {
        for (ColumnDesc col : columnDescs)
        {
            for (TableDesc tableDesc : tableDescs)
            {
                if (col.tableName.equals(tableDesc.tableName))
                {
                    tableDesc.columnDescs.add(col);
                    break;
                }
            }
        }
    }

    /**
     * Creates Lists of KeyColumns with a common Key keyID, then adds the Lists to the Keys.
     * 
     * @param keyDescs List of Keys.
     * @param keyColumnDescs List of KeyColumns.
     */
    private void addKeyColumnsToKeys(List<KeyDesc> keyDescs, List<KeyColumnDesc> keyColumnDescs)
    {
        for (KeyColumnDesc keyColumnDesc : keyColumnDescs)
        {
            for (KeyDesc keyDesc : keyDescs)
            {
                if (keyColumnDesc.keyId.equals(keyDesc.keyId))
                {
                    keyDesc.keyColumnDescs.add(keyColumnDesc);
                    break;
                }
            }
        }
    }

    /**
     * Adds foreign keys (KeyDesc) to the from table.
     * 
     * @param ts
     */
    private void addForeignKeys(TapSchema ts, List<KeyDesc> keyDescs)
    {
        for (KeyDesc key : keyDescs)
        {
            for (SchemaDesc sd : ts.schemaDescs)
            {
                for (TableDesc td : sd.tableDescs)
                {
                    if ( key.fromTable.equals(td.tableName))
                    {
                        td.keyDescs.add(key);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Creates a List of Schema populated from the ResultSet.
     */
    private static final class SchemaMapper implements RowMapper
    {
        public Object mapRow(ResultSet rs, int rowNum) throws SQLException
        {
            SchemaDesc schemaDesc = new SchemaDesc();
            schemaDesc.schemaName = rs.getString("schema_name");
            schemaDesc.description = rs.getString("description");
            schemaDesc.utype = rs.getString("utype");
            schemaDesc.tableDescs = new ArrayList<TableDesc>();
            log.debug("found: " + schemaDesc);
            return schemaDesc;
        }
    }

    /**
     * Creates a List of Table populated from the ResultSet.
     */
    private static final class TableMapper implements RowMapper
    {
        public Object mapRow(ResultSet rs, int rowNum) throws SQLException
        {
            TableDesc tableDesc = new TableDesc();
            tableDesc.schemaName = rs.getString("schema_name");
            tableDesc.tableName = rs.getString("table_name");
            tableDesc.description = rs.getString("description");
            tableDesc.utype = rs.getString("utype");
            tableDesc.columnDescs = new ArrayList<ColumnDesc>();
            tableDesc.keyDescs = new ArrayList<KeyDesc>();
            log.debug("found: " + tableDesc);
            return tableDesc;
        }
    }

    /**
     * Creates a List of Column populated from the ResultSet.
     */
    private static final class ColumnMapper implements RowMapper
    {
        public Object mapRow(ResultSet rs, int rowNum) throws SQLException
        {
            ColumnDesc col = new ColumnDesc();
            col.tableName = rs.getString("table_name");
            col.columnName = rs.getString("column_name");            
            col.description = rs.getString("description");
            col.utype = rs.getString("utype");
            col.ucd = rs.getString("ucd");
            col.unit = rs.getString("unit");
            col.datatype = rs.getString("datatype");
            col.size = rs.getObject("size") == null ? null : Integer.valueOf(rs.getInt("size"));
            log.debug("found: " + col);
            return col;
        }
    }

    /**
     * Creates a List of Key populated from the ResultSet.
     */
    private static final class KeyMapper implements RowMapper
    {
        public Object mapRow(ResultSet rs, int rowNum) throws SQLException
        {
            KeyDesc keyDesc = new KeyDesc();
            keyDesc.keyId = rs.getString("key_id");
            keyDesc.fromTable = rs.getString("from_table");
            keyDesc.targetTable = rs.getString("target_table");
            keyDesc.description = rs.getString("description");
            keyDesc.utype = rs.getString("utype");
            keyDesc.keyColumnDescs = new ArrayList<KeyColumnDesc>();
            log.debug("found: " + keyDesc);
            return keyDesc;
        }
    }

    /**
     * Creates a List of KeyColumn populated from the ResultSet.
     */
    private static final class KeyColumnMapper implements RowMapper
    {
        public Object mapRow(ResultSet rs, int rowNum) throws SQLException
        {
            KeyColumnDesc keyColumnDesc = new KeyColumnDesc();
            keyColumnDesc.keyId = rs.getString("key_id");
            keyColumnDesc.fromColumn = rs.getString("from_column");
            keyColumnDesc.targetColumn = rs.getString("target_column");
            log.debug("found: " + keyColumnDesc);
            return keyColumnDesc;
        }
    }

}
