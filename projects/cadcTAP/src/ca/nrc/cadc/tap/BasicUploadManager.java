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
package ca.nrc.cadc.tap;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.stc.Polygon;
import ca.nrc.cadc.stc.Position;
import ca.nrc.cadc.stc.Region;
import ca.nrc.cadc.stc.STC;
import ca.nrc.cadc.stc.StcsParsingException;
import ca.nrc.cadc.tap.parser.region.pgsphere.function.Spoint;
import ca.nrc.cadc.tap.parser.region.pgsphere.function.Spoly;
import ca.nrc.cadc.tap.schema.ColumnDesc;
import ca.nrc.cadc.tap.schema.TableDesc;
import ca.nrc.cadc.tap.upload.DatabaseDataTypeFactory;
import ca.nrc.cadc.tap.upload.JDOMVOTableParser;
import ca.nrc.cadc.tap.upload.UploadParameters;
import ca.nrc.cadc.tap.upload.UploadTable;
import ca.nrc.cadc.tap.upload.VOTableParser;
import ca.nrc.cadc.tap.upload.VOTableParserException;
import ca.nrc.cadc.tap.upload.datatype.ADQLDataType;
import ca.nrc.cadc.tap.upload.datatype.DatabaseDataType;
import ca.nrc.cadc.uws.Parameter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.log4j.Logger;

/**
 *
 * @author jburke
 */
public class BasicUploadManager implements UploadManager
{
    private static final Logger log = Logger.getLogger(BasicUploadManager.class);
    
    // Number of rows to insert per commit.
    private static final int NUM_ROWS_PER_COMMIT = 100;
    
    /**
     * DataSource for the DB.
     */
    protected DataSource dataSource;
    
    /**
     * IVOA DateFormat
     */
    protected DateFormat dateFormat;
    
    /**
     * Maximum number of rows allowed in the UPLOAD VOTable.
     */
    protected int maxUploadRows;

    /**
     * Default constructor.
     */
    private BasicUploadManager() { }
    
    protected BasicUploadManager(int maxUploadRows)
    {
        this.maxUploadRows = maxUploadRows;
        dateFormat = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
    }

    /**
     * Set the DataSource used for creating and populating tables.
     * @param ds
     */
    public void setDataSource(DataSource ds)
    {
        this.dataSource = ds;
    }

    /**
     * Find and process all UPLOAD requests.
     *
     * @param paramList list of all parameters passed to the service.
     * @param jobID the UWS jobID.
     * @return map of service generated upload table name to user-specified table metadata
     */
    public Map<String, TableDesc> upload(List<Parameter> paramList, String jobID)
    {
        log.debug("upload jobID " + jobID);
        
        if (dataSource == null)
            throw new IllegalStateException("failed to get DataSource");

        // Map of database table name to table descriptions.
        Map<String, TableDesc> metadata = new HashMap<String, TableDesc>();

        // Statements
        Statement stmt = null;
        PreparedStatement ps = null;
        Connection con = null;
        boolean txn = false;
        UploadTable cur = null;

        //FormatterFactory factory = DefaultFormatterFactory.getFormatterFactory();
        //factory.setJobID(jobID);
        //factory.setParamList(params);

        try
        {
            // Get upload table names and URI's from the request parameters.
            UploadParameters uploadParameters = new UploadParameters(paramList, jobID);
            if (uploadParameters.uploadTables.isEmpty())
            {
                log.debug("No upload tables found in paramList");
                return metadata;
            }

            // acquire connection
            con = dataSource.getConnection();
            
            // DataType containing mapping of java.sql.Types
            // to database data type names.
            DatabaseDataType databaseDataType = DatabaseDataTypeFactory.getDatabaseDataType(con);

            con.setAutoCommit(false);
            txn = true;

            // Process each table.
            for (UploadTable uploadTable : uploadParameters.uploadTables)
            {
                cur = uploadTable;
                
                // XML parser
                // TODO: make configurable.
                log.debug(uploadTable);
                VOTableParser parser = getVOTableParser(uploadTable);

                // Get the Table description.
                TableDesc tableDesc = parser.getTableDesc();

                // Fully qualified name of the table in the database.
                String databaseTableName = getDatabaseTableName(uploadTable);
                
                // Update the returned Metadata.
                metadata.put(databaseTableName, tableDesc);

                // Build the SQL to create the table.
                String tableSQL = getCreateTableSQL(tableDesc, databaseTableName, databaseDataType);
                log.debug("Create table SQL: " + tableSQL);

                // Create the table.
                stmt = con.createStatement();
                stmt.executeUpdate(tableSQL);
                
                // Grant select access for others to query.
                String grantSQL = getGrantSelectTableSQL(databaseTableName);
                if (grantSQL != null && !grantSQL.isEmpty())
                {
                    log.debug("Grant select SQL: " + grantSQL);
                    stmt.executeUpdate(grantSQL);
                }
                
                // commit the create and grant
                con.commit();

                // Get a PreparedStatement that populates the table.
                String insertSQL = getInsertTableSQL(tableDesc, databaseTableName); 
                ps = con.prepareStatement(insertSQL);
                log.debug("Insert table SQL: " + insertSQL);

                // Populate the table from the VOTable tabledata rows.
                int numRows = 0;
                Iterator it = parser.iterator();
                while (it.hasNext())
                {
                    // Get the data for the next row.
                    String[] row = (String[]) it.next();

                    // Update the PreparedStatement with the row data.
                    updatePreparedStatement(ps, tableDesc.columnDescs, row);

                    // Execute the update.
                    ps.executeUpdate();
                    
                    // commit every NUM_ROWS_PER_COMMIT rows
                    if (numRows != 0 && (numRows % NUM_ROWS_PER_COMMIT) == 0)
                    {
                        log.debug(NUM_ROWS_PER_COMMIT + " rows committed");
                        con.commit();
                    }
                    
                    // Check if we've reached exceeded the max number of rows.
                    numRows++;
                    if (numRows == maxUploadRows)
                        throw new UnsupportedOperationException("Exceded maximum number of allowed rows: " + maxUploadRows);
                }
                
                // Commit remaining rows.
                con.commit();
                
                log.debug(numRows + " rows inserted into " + databaseTableName);
            }
            txn = false;
        }
        catch(StcsParsingException ex)
        {
            throw new RuntimeException("failed to parse table " + cur.tableName + " from " + cur.uri, ex);
        }
        catch(VOTableParserException ex)
        {
            throw new RuntimeException("failed to parse table " + cur.tableName + " from " + cur.uri, ex);
        }
        catch(IOException ex)
        {

            throw new RuntimeException("failed to read table " + cur.tableName + " from " + cur.uri, ex);
        }
        catch (SQLException e)
        {
            throw new RuntimeException("failed to create and load table in DB", e);
        }
        finally
        {
            try
            {
                if (con != null)
                    con.rollback();
            }
            catch (SQLException ignore) { }
            if (stmt != null)
            {
                try
                {
                    stmt.close();
                }
                catch (SQLException ignore) { }
            }
            if (ps != null)
            {
                try
                {
                    ps.close();
                }
                catch (SQLException ignore) { }
            }
            if (con != null)
            {
                try
                {
                    con.close();
                }
                catch (SQLException ignore) { }
            }
        }
        return metadata;
    }

    /**
     * Create the SQL to grant select privileges for the UPLOAD table.
     * 
     * @param databaseTableName fully qualified table name.
     * @return 
     */
    protected String getGrantSelectTableSQL(String databaseTableName)
    {
        return null;
    }
    
    /**
     * Get a VOTableParser for an UploadTable.
     * 
     * @param uploadTable containing the table name and URI.
     * @return VOTableParser for the UploadTable.
     * @throws IOException 
     */
    protected VOTableParser getVOTableParser(UploadTable uploadTable)
        throws IOException
    {
        VOTableParser parser = new JDOMVOTableParser();
        parser.setTableName(SCHEMA + "." + uploadTable.tableName);
        parser.setInputStream(uploadTable.uri.toURL().openStream());
        return parser;
    }
    
    /**
     * Constructs the database table name from the schema, upload table name,
     * and the jobID.
     * 
     * @return the database table name.
     */
    public String getDatabaseTableName(UploadTable uploadTable)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(uploadTable.tableName);
        sb.append("_");
        sb.append(uploadTable.jobID);
        if (!uploadTable.tableName.toUpperCase().startsWith(SCHEMA + "."))
        {
            sb.insert(0, ".");
            sb.insert(0, SCHEMA);
        }
        return sb.toString();
    }
    
    /**
     * Create the SQL required to create a table described by the TableDesc.
     *
     * @param tableDesc describes the table.
     * @param databaseTableName fully qualified table name.
     * @param databaseDataType map of SQL types to database specific data types.
     * @return SQL to create the table.
     * @throws SQLException
     */
    protected String getCreateTableSQL(TableDesc tableDesc, String databaseTableName, DatabaseDataType databaseDataType)
        throws SQLException
    {
        StringBuilder sb = new StringBuilder();
        sb.append("create table ");
        sb.append(databaseTableName);
        sb.append(" ( ");
        for (int i = 0; i < tableDesc.columnDescs.size(); i++)
        {
            ColumnDesc columnDesc = tableDesc.columnDescs.get(i);
            sb.append(columnDesc.columnName);
            sb.append(" ");
            sb.append(databaseDataType.getDataType(columnDesc));
            sb.append(" null ");
            if (i + 1 < tableDesc.columnDescs.size())
                sb.append(", ");
        }
        sb.append(" ) ");
        return sb.toString();
    }
    
    /**
     * Create the SQL required to create a PreparedStatement
     * to insert into the table described by the TableDesc.
     * 
     * @param tableDesc describes the table.
     * @return SQL to create the table.
     */
    protected String getInsertTableSQL(TableDesc tableDesc, String databaseTableName)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("insert into ");
        sb.append(databaseTableName);
        sb.append(" ( ");
        for (int i = 0; i < tableDesc.columnDescs.size(); i++)
        {
            ColumnDesc columnDesc = tableDesc.columnDescs.get(i);
            sb.append(columnDesc.columnName);
            if (i + 1 < tableDesc.columnDescs.size())
                sb.append(", ");
        }
        sb.append(" ) values ( ");
        for (int i = 0; i < tableDesc.columnDescs.size(); i++)
        {
            sb.append("?");
            if (i + 1 < tableDesc.columnDescs.size())
                sb.append(", ");
        }
        sb.append(" ) ");
        return sb.toString();
    }

    /**
     * Updated the PreparedStatement with the row data using the ColumnDesc to
     * determine each column data type.
     *
     * @param ps the prepared statement.
     * @param columnDescs List of ColumnDesc for this table.
     * @param row Array containing the data to be inserted into the database.
     * @throws SQLException if the statement is closed or if the parameter index type doesn't match.
     */
    protected void updatePreparedStatement(PreparedStatement ps, List<ColumnDesc> columnDescs, String[] row)
        throws SQLException, StcsParsingException
    {
        for (int i = 0; i < row.length; i++)
        {
            ColumnDesc columnDesc = columnDescs.get(i);
            log.debug("update ps: " + columnDesc.columnName + "[" + columnDesc.datatype + "] = " + row[i]);

            String value = row[i];
            if (value == null)
                ps.setNull(i + 1, ADQLDataType.getSQLType(columnDesc.datatype));
            else if (columnDesc.datatype.equals(ADQLDataType.ADQL_SMALLINT))
                ps.setShort(i + 1, Short.parseShort(value));
            else if (columnDesc.datatype.equals(ADQLDataType.ADQL_INTEGER))
                ps.setInt(i + 1, Integer.parseInt(value));
            else if (columnDesc.datatype.equals(ADQLDataType.ADQL_BIGINT))
                ps.setLong(i + 1, Long.parseLong(value));
            else if (columnDesc.datatype.equals(ADQLDataType.ADQL_REAL))
                ps.setFloat(i + 1, Float.parseFloat(value));
            else if (columnDesc.datatype.equals(ADQLDataType.ADQL_DOUBLE))
                ps.setDouble(i + 1, Double.parseDouble(value));
            else if (columnDesc.datatype.equals(ADQLDataType.ADQL_CHAR))
                ps.setString(i + 1, value);
            else if (columnDesc.datatype.equals(ADQLDataType.ADQL_VARCHAR))
                ps.setString(i + 1, value);
            else if (columnDesc.datatype.equals(ADQLDataType.ADQL_CLOB))
	        ps.setString(i + 1, value);
	    else if (columnDesc.datatype.equals(ADQLDataType.ADQL_TIMESTAMP))
            {
                try
                {
                    Date date = dateFormat.parse(value);
                    ps.setTimestamp(i + 1, new Timestamp(date.getTime()));
                }
                catch (ParseException e)
                {
                    throw new SQLException("failed to parse timestamp " + value, e);
                }
            }
            else if (columnDesc.datatype.equals(ADQLDataType.ADQL_POINT))
            {
                Region r = STC.parse(value);
                if (r instanceof Position)
                {
                    Position pos = (Position) r;
                    Object o = getPointObject(pos);
                    ps.setObject(i+1, o);
                }
                else
                    throw new IllegalArgumentException("failed to parse " + value + " as an " + ADQLDataType.ADQL_POINT);
            }
            else if (columnDesc.datatype.equals(ADQLDataType.ADQL_REGION))
            {
                Region reg = STC.parse(value);
                Object o = getRegionObject(reg);
                ps.setObject(i+1, o);
            }
            else
                throw new SQLException("Unsupported ADQL data type " + columnDesc.datatype);
        }
    }

    /**
     * Convert the string representation of the specified ADQL POINT into an object.
     *
     * @param pos
     * @throws SQLException
     * @return an object suitable for use with PreparedStatement.setObject(int,Object)
     */
    protected Object getPointObject(Position pos)
        throws SQLException
    {
        throw new UnsupportedOperationException("cannot convert ADQL POINT (STC-S Position) -> internal database type");
    }

    /**
     * Convert the string representation of the specified ADQL POINT into an object.
     *
     * @param reg
     * @throws SQLException
     * @return an object suitable for use with PreparedStatement.setObject(int,Object)
     */
    protected Object getRegionObject(Region reg)
        throws SQLException
    {
        throw new UnsupportedOperationException("cannot convert ADQL REGION (STC-S Region) -> internal database type");
    }

}
