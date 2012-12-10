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

    public static final String SCHEMAS_TAB = "tap_schema.schemas";
    public static final String TABLES_TAB = "tap_schema.tables";
    public static final String COLUMNS_TAB = "tap_schema.columns";
    public static final String KEYS_TAB = "tap_schema.keys";
    public static final String KEY_COLUMNS_TAB = "tap_schema.key_columns";

    // SQL to select all rows from TAP_SCHEMA.schemas.
    private static final String SELECT_SCHEMAS =
            "select schema_name, description, utype " +
            "from " + SCHEMAS_TAB;
    private static final String ORDER_SCHEMAS = " ORDER BY schema_name";

    // SQL to select all rows from TAP_SCHEMA.tables.
    private static final String SELECT_TABLES = 
            "select schema_name, table_name, description, utype " +
            " from " + TABLES_TAB;
    private static final String ORDER_TABLES = " ORDER BY schema_name,table_name";

    // SQL to select all rows from TAP_SCHEMA.colums.
    private static final String SELECT_COLUMNS = 
            "select table_name, column_name, description, utype, ucd, unit, datatype, size, principal, indexed, std " +
            "from " + COLUMNS_TAB;
    private static final String ORDER_COLUMNS = " ORDER BY table_name,column_name";
    
    // SQL to select all rows from TAP_SCHEMA.keys.
    private static final String SELECT_KEYS =
            "select key_id, from_table, target_table,description,utype " +
            "from " + KEYS_TAB;
    private static final String ORDER_KEYS = " ORDER BY key_id,from_table,target_table";

    // SQL to select all rows from TAP_SCHEMA.key_columns.
    private static final String SELECT_KEY_COLUMNS = 
            "select key_id, from_column, target_column " +
            "from " + KEY_COLUMNS_TAB;
    private static final String ORDER_KEY_COLUMNS = " ORDER BY key_id, from_column, target_column";

    // Database connection.
    protected DataSource dataSource;
    protected boolean ordered;

    private TapSchemaDAO delegate;

    // Indicates function return datatype matches argument datatype.
    public static final String ARGUMENT_DATATYPE = "ARGUMENT_DATATYPE";

    /**
     * Construct a new TapSchemaDAO using the specified DataSource. As an extension
     * mechanism, this class will attempt to load a subclass and delegate to it. The
     * delegate class muct be named <code>ca.nrc.cadc.tap.schema.TapSchemaDAOImpl</code>
     * and have a no-arg constructor.
     * 
     * @param dataSource TAP_SCHEMA DataSource.
     */
    public TapSchemaDAO(DataSource dataSource)
    {
        this(dataSource, false);
    }

    public TapSchemaDAO(DataSource dataSource, boolean ordered)
    {
        this.dataSource = dataSource;
        this.ordered = ordered;
        String extensionClassName = TapSchemaDAO.class.getName() + "Impl";
        try
        {
            Class c = Class.forName(extensionClassName);
            this.delegate = (TapSchemaDAO) c.newInstance();
            log.debug("loaded: " + extensionClassName);
            delegate.dataSource = dataSource;
            delegate.ordered = ordered;
        }
        catch(Throwable t)
        {
            log.debug("failed to load: " + extensionClassName + ", using TapSchemaDAO directly", t);
        }
    }

    // delegate ctor
    protected TapSchemaDAO()
    {

    }

    /**
     * Creates and returns a TapSchema object representing all of the data in TAP_SCHEMA.
     * 
     * @return TapSchema containing all of the data from TAP_SCHEMA.
     */
    public final TapSchema get()
    {
        if (delegate != null)
            return delegate.get();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        TapSchema tapSchema = new TapSchema();
        String sql;

        // List of TAP_SCHEMA.schemas
        sql = appendWhere(SCHEMAS_TAB, SELECT_SCHEMAS);
        if (ordered) sql += ORDER_SCHEMAS;
        log.debug(sql);
        tapSchema.schemaDescs = jdbc.query(sql, new SchemaMapper());

        // List of TAP_SCHEMA.tables
        sql = appendWhere(TABLES_TAB, SELECT_TABLES);
        if (ordered) sql += ORDER_TABLES;
        log.debug(sql);
        List<TableDesc> tableDescs = jdbc.query(sql, new TableMapper());

        // Add the Tables to the Schemas.
        addTablesToSchemas(tapSchema.schemaDescs, tableDescs);

        // List of TAP_SCHEMA.columns
        sql = appendWhere(COLUMNS_TAB, SELECT_COLUMNS);
        if (ordered) sql += ORDER_COLUMNS;
        log.debug(sql);
        List<ColumnDesc> columnDescs = jdbc.query(sql, new ColumnMapper());

        // Add the Columns to the Tables.
        addColumnsToTables(tableDescs, columnDescs);

        // List of TAP_SCHEMA.keys
        sql = appendWhere(KEYS_TAB, SELECT_KEYS);
        if (ordered) sql += ORDER_KEYS;
        log.debug(sql);
        List<KeyDesc> keyDescs = jdbc.query(sql, new KeyMapper());

        // List of TAP_SCHEMA.key_columns
        sql = appendWhere(KEY_COLUMNS_TAB, SELECT_KEY_COLUMNS);
        if (ordered) sql += ORDER_KEY_COLUMNS;
        log.debug(sql);
        List<KeyColumnDesc> keyColumnDescs = jdbc.query(sql, new KeyColumnMapper());

        // Add the KeyColumns to the Keys.
        addKeyColumnsToKeys(keyDescs, keyColumnDescs);

        // connect foreign keys to the fromTable
        addForeignKeys(tapSchema, keyDescs);

        // Add the List of FunctionDescs.
        tapSchema.functionDescs = getFunctionDescs();

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
     * Append a where clause to the query that selects from the specified table.
     * The default impl does nothing (returns in the provieed SQL as-is).
     * </p>
     * <p>
     * If you want to implement some additional conditions, such as having private columns
     * only visible to certain authenticated and authorized users, you can append some
     * conditions (or re-write the query as long as the select-list is not altered) here.
     * 
     * @param sql
     * @return modified SQL
     */
    protected String appendWhere(String tapSchemaTablename, String sql)
    {
        return sql;
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
     * Get white-list of supported functions. TAP implementors that want to allow
     * additiopnal functions to be used in queries to be used should override this
     * method, call <code>super.getFunctionDescs()</code>, and then add additional
     * FunctionDesc descriptors to the list before returning it.
     *
     * @return white list of allowed functions
     */
    protected List<FunctionDesc> getFunctionDescs()
    {
        List<FunctionDesc> functionDescs = new ArrayList<FunctionDesc>();

        // ADQL functions.
        functionDescs.add(new FunctionDesc("AREA", "deg", "adql:DOUBLE"));
        functionDescs.add(new FunctionDesc("BOX", "", "adql:DOUBLE"));
        functionDescs.add(new FunctionDesc("CENTROID", "", "adql:POINT"));
        functionDescs.add(new FunctionDesc("CIRCLE", "", "adql:DOUBLE"));
        functionDescs.add(new FunctionDesc("CONTAINS", "", "adql:INTEGER"));
        functionDescs.add(new FunctionDesc("COORD1", "deg", "adql:DOUBLE"));
        functionDescs.add(new FunctionDesc("COORD2", "deg", "adql:DOUBLE"));
        functionDescs.add(new FunctionDesc("COORDSYS", "", "adql:VARCHAR"));
        functionDescs.add(new FunctionDesc("DISTANCE", "deg", "adql:DOUBLE"));
        functionDescs.add(new FunctionDesc("INTERSECTS", "", "adql:INTEGER"));
        functionDescs.add(new FunctionDesc("POINT", "", "adql:POINT"));
        functionDescs.add(new FunctionDesc("POLYGON", "", "adql:DOUBLE"));
        functionDescs.add(new FunctionDesc("REGION", "", "adql:REGION"));

        // ADQL reserved keywords that are functions.
        functionDescs.add(new FunctionDesc("ABS", ""));
        functionDescs.add(new FunctionDesc("ACOS", "radians", "adql:DOUBLE"));
        functionDescs.add(new FunctionDesc("ASIN", "radians", "adql:DOUBLE"));
        functionDescs.add(new FunctionDesc("ATAN", "radians", "adql:DOUBLE"));
        functionDescs.add(new FunctionDesc("ATAN2", "radians", "adql:DOUBLE"));
        functionDescs.add(new FunctionDesc("CEILING", "", "adql:INTEGER"));
        functionDescs.add(new FunctionDesc("COS", "radians", "adql:DOUBLE"));
        functionDescs.add(new FunctionDesc("COT", "radians", "adql:DOUBLE"));
        functionDescs.add(new FunctionDesc("DEGREES", "deg", "adql:DOUBLE"));
        functionDescs.add(new FunctionDesc("EXP", "", "adql:DOUBLE"));
        functionDescs.add(new FunctionDesc("FLOOR", "", "adql:INTEGER"));
        functionDescs.add(new FunctionDesc("LN", "", "adql:DOUBLE"));
        functionDescs.add(new FunctionDesc("LOG", "", "adql:DOUBLE"));
        functionDescs.add(new FunctionDesc("LOG10", "", "adql:DOUBLE"));
        functionDescs.add(new FunctionDesc("MOD", ""));
        /*
         * Part of the ADQL BNF, but currently not parseable pending bug
         * fix in the jsqlparser.
         *
         * functionDescs.add(new FunctionDesc("PI", "", "adql:DOUBLE"));
         */
        functionDescs.add(new FunctionDesc("POWER", ""));
        functionDescs.add(new FunctionDesc("RADIANS", "radians", "adql:DOUBLE"));
        /*
         * Part of the ADQL BNF, but currently not parseable pending bug
         * fix in the jsqlparser.
         *
         * functionDescs.add(new FunctionDesc("RAND", "", "adql:DOUBLE"));
         */
        functionDescs.add(new FunctionDesc("ROUND", ""));
        functionDescs.add(new FunctionDesc("SIN", "radians", "adql:DOUBLE"));
        functionDescs.add(new FunctionDesc("SQRT", ""));
        functionDescs.add(new FunctionDesc("TAN", "radians", "adql:DOUBLE"));
        /*
         * Part of the ADQL BNF, but currently not parseable.
         *
         * functionDescs.add(new FunctionDesc("TRUNCATE", "", "adql:DOUBLE"));
         */

        // SQL Aggregate functions.
        functionDescs.add(new FunctionDesc("AVG", ""));
        functionDescs.add(new FunctionDesc("COUNT", "", "adql:INTEGER"));
        functionDescs.add(new FunctionDesc("MAX", ""));
        functionDescs.add(new FunctionDesc("MIN", ""));
        functionDescs.add(new FunctionDesc("STDDEV", "", "adql:DOUBLE"));
        functionDescs.add(new FunctionDesc("SUM", ""));
        functionDescs.add(new FunctionDesc("VARIANCE", "", "adql:DOUBLE"));
        
        // SQL String functions.
//        functionDescs.add(new FunctionDesc("BIT_LENGTH", "", "adql:INTEGER"));
//        functionDescs.add(new FunctionDesc("CHARACTER_LENGTH", "", "adql:INTEGER"));
//        functionDescs.add(new FunctionDesc("LOWER", "", "adql:VARCHAR"));
//        functionDescs.add(new FunctionDesc("OCTET_LENGTH", "", "adql:INTEGER"));
//        functionDescs.add(new FunctionDesc("OVERLAY", "", "adql:VARCHAR")); //SQL92???
//        functionDescs.add(new FunctionDesc("POSITION", "", "adql:INTEGER"));
//        functionDescs.add(new FunctionDesc("SUBSTRING", "", "adql:VARCHAR"));
//        functionDescs.add(new FunctionDesc("TRIM", "", "adql:VARCHAR"));
//        functionDescs.add(new FunctionDesc("UPPER", "", "adql:VARCHAR"));

        // SQL Date functions.
//        functionDescs.add(new FunctionDesc("CURRENT_DATE", "", "adql:TIMESTAMP"));
//        functionDescs.add(new FunctionDesc("CURRENT_TIME", "", "adql:TIMESTAMP"));
//        functionDescs.add(new FunctionDesc("CURRENT_TIMESTAMP", "", "adql:TIMESTAMP"));
//        functionDescs.add(new FunctionDesc("EXTRACT", "", "adql:TIMESTAMPs"));
//        functionDescs.add(new FunctionDesc("LOCAL_DATE", "", "adql:TIMESTAMP"));   //SQL92???
//        functionDescs.add(new FunctionDesc("LOCAL_TIME", "", "adql:TIMESTAMP"));   //SQL92???
//        functionDescs.add(new FunctionDesc("LOCAL_TIMESTAMP", "", "adql:TIMESTAMP"));  //SQL92???

        

//        functionDescs.add(new FunctionDesc("BETWEEN", ""));
//        functionDescs.add(new FunctionDesc("CASE", ""));
//        functionDescs.add(new FunctionDesc("CAST", ""));
//        functionDescs.add(new FunctionDesc("COALESCE", ""));
//        functionDescs.add(new FunctionDesc("CONVERT", ""));
//        functionDescs.add(new FunctionDesc("TRANSLATE", ""));
        
        // Sub-selects
//        functionDescs.add(new FunctionDesc("ALL", ""));
//        functionDescs.add(new FunctionDesc("ANY", ""));
//        functionDescs.add(new FunctionDesc("EXISTS", ""));
//        functionDescs.add(new FunctionDesc("IN", ""));

        return functionDescs;
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
            //log.debug("found: " + schemaDesc);
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
            //log.debug("found: " + tableDesc);
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
            col.principal = intToBoolean(rs.getInt("principal"));
            col.indexed = intToBoolean(rs.getInt("indexed"));
            col.std = intToBoolean(rs.getInt("std"));
            //log.debug("found: " + col);
            return col;
        }

        private boolean intToBoolean(Integer i)
        {
            if (i == null)
                return false;
            return (i.intValue() == 1);
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
            //log.debug("found: " + keyDesc);
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
            //log.debug("found: " + keyColumnDesc);
            return keyColumnDesc;
        }
    }

}
