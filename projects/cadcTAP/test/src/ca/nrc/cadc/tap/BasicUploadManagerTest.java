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
*  $Revision: 1 $
*
************************************************************************
*/

package ca.nrc.cadc.tap;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.db.ConnectionConfig;
import ca.nrc.cadc.db.DBConfig;
import ca.nrc.cadc.db.DBUtil;
import ca.nrc.cadc.tap.schema.TableDesc;
import ca.nrc.cadc.tap.upload.ADQLIdentifierException;
import ca.nrc.cadc.tap.upload.JDOMVOTableParser;
import ca.nrc.cadc.tap.upload.UploadTable;
import ca.nrc.cadc.tap.upload.VOTableParser;
import ca.nrc.cadc.util.FileUtil;
import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.uws.Parameter;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import javax.sql.DataSource;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author jburke
 */
public class BasicUploadManagerTest
{
    public static final String BOOLEAN_FIELD = "boolean_field";
    public static final String DOUBLE_FIELD = "double_field";
    public static final String FLOAT_FIELD = "float_field";
    public static final String INT_FIELD = "int_field";
    public static final String LONG_FIELD = "long_field";
    public static final String SHORT_FIELD = "short_field";

    private static final Logger log = Logger.getLogger(BasicUploadManagerTest.class);
    
    private static DataSource ds;
    private static Map<String, String> types;
    private static Date date;

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.tap", org.apache.log4j.Level.INFO);
        Log4jInit.setLevel("ca.nrc.cadc.tap.upload", org.apache.log4j.Level.INFO);
    }
    
    public BasicUploadManagerTest() { }

    @BeforeClass
    public static void setUpClass()
        throws Exception
    {
        DBConfig dbrc = new DBConfig();
        ConnectionConfig conf = dbrc.getConnectionConfig("TAP_UPLOAD_TEST", "cadctest");
        ds = DBUtil.getDataSource(conf, true, true);

        types = new HashMap<String, String>();
        types.put("short_datatype", "int2");
        types.put("smallint_xtype", "int2");
        types.put("int_datatype", "int4");
        types.put("integer_xtype", "int4");
        types.put("long_datatype", "int8");
        types.put("bigint_xtype", "int8");
        types.put("float_datatype", "float4");
        types.put("real_xtype", "float4");
        types.put("double_datatype", "float8");
        types.put("double_xtype", "float8");
        types.put("char_datatype", "varchar");
        types.put("char_xtype", "bpchar");
        types.put("varchar_datatype", "varchar");
        types.put("varchar_xtype", "varchar");
        types.put("timestamp_xtype", "timestamp");
        types.put("point_xtype", "spoint");
        types.put("region_xtype", "spoly");
        
        date = DateUtil.flexToDate("2011-01-01T00:00:00.000", DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC));
    }

    @AfterClass
    public static void tearDownClass()
        throws Exception { }

    @Before
    public void setUp() { }

    @After
    public void tearDown() { }

    @Test
    public void testUploadEmptyTable()
    {
        try
        {
            log.debug("testUploadEmptyTable");

            // New instance of UploadManager.
            FileUploadManagerImpl uploadManager = new FileUploadManagerImpl();
            uploadManager.setDataSource(ds);
            uploadManager.setFilename("EmptyTable.xml");

            // Create a List of upload parameters.
            List<Parameter> paramList = new ArrayList<Parameter>();
            Parameter parameter = new Parameter("UPLOAD", "testUploadEmptyTable,http://localhost/foo");
            paramList.add(parameter);

            // Create a JobID
            String jobID = Long.toString(System.currentTimeMillis());
            log.debug("testUploadEmptyTable jobID: " + jobID);

            // Upload the VOTable.
            Map<String, TableDesc> tableDescs = uploadManager.upload(paramList, jobID);

            // Should return a single TableDesc
            Assert.assertNotNull(tableDescs);
            Assert.assertEquals(1, tableDescs.size());

            // Get the upload table name and TableDesc
            Set<String> keySet = tableDescs.keySet();
            String tableName = keySet.iterator().next();
            TableDesc tableDesc = tableDescs.get(tableName);

            Assert.assertEquals(0, tableDesc.columnDescs.size());

            log.info("testUploadEmptyTable passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testUploadAllTypesNoRows()
    {
        try
        {
            log.debug("testUploadAllTypesNoRows");

            // New instance of UploadManager.
            FileUploadManagerImpl uploadManager = new FileUploadManagerImpl();
            uploadManager.setDataSource(ds);
            uploadManager.setFilename("AllTypes-NoRows.xml");

            // Create a List of upload parameters.
            List<Parameter> paramList = new ArrayList<Parameter>();
            Parameter parameter = new Parameter("UPLOAD", "testURIUploadAllTypesNoRows,http://localhost/foo");
            paramList.add(parameter);

            // Create a JobID
            String jobID = Long.toString(System.currentTimeMillis());
            log.debug("testURIUploadAllTypesNoRows jobID: " + jobID);

            // Upload the VOTable.
            Map<String, TableDesc> tableDescs = uploadManager.upload(paramList, jobID);

            // Should return a single TableDesc
            Assert.assertNotNull("TableDesc should not be null", tableDescs);
            Assert.assertEquals("TableDesc list should contain single TableDesc", 1, tableDescs.size());

            // Get the upload table name and TableDesc
            Set<String> keySet = tableDescs.keySet();
            String tableName = keySet.iterator().next();
            log.debug("test table name: " + tableName);
            TableDesc tableDesc = tableDescs.get(tableName);
            log.debug("TableDesc " + tableDesc);

            // Get the table from the database
            StringBuilder sb = new StringBuilder();
            sb.append("select * from ");
            sb.append(tableName);
            Connection con = ds.getConnection();
            Statement  stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery(sb.toString());

            // Check the table column names.
            ResultSetMetaData metadata = rs.getMetaData();
            for (int i = 1; i <= metadata.getColumnCount(); i++)
            {
                String columnName = metadata.getColumnName(i);
                String columnTypeName = metadata.getColumnTypeName(i);
                if (types.containsKey(columnName))
                {
                    log.debug("columnName: " + columnName + ", type: " + columnTypeName);
                    String typeName = types.get(columnName);
                    Assert.assertEquals(typeName, columnTypeName);
                }
                else
                {
                    Assert.fail("invalid database column name " + columnName);
                }
            }

            log.info("testUploadAllTypesNoRows passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testUploadAllTypes()
    {
        try
        {
            log.debug("testUploadAllTypes");

            // New instance of UploadManager.
            FileUploadManagerImpl uploadManager = new FileUploadManagerImpl();
            uploadManager.setDataSource(ds);
            uploadManager.setFilename("AllTypes.xml");

            // Create a List of upload parameters.
            List<Parameter> paramList = new ArrayList<Parameter>();
            Parameter parameter = new Parameter("UPLOAD", "testURIUploadAllTypes,http://localhost/foo");
            paramList.add(parameter);

            // Create a JobID
            String jobID = Long.toString(System.currentTimeMillis());
            log.debug("testURIUploadAllTypes jobID: " + jobID);

            // Upload the VOTable.
            Map<String, TableDesc> tableDescs = uploadManager.upload(paramList, jobID);

            // Should return a single TableDesc
            Assert.assertNotNull("TableDesc should not be null", tableDescs);
            Assert.assertEquals("TableDesc list should contain single TableDesc", 1, tableDescs.size());

            // Get the upload table name and TableDesc
            Set<String> keySet = tableDescs.keySet();
            String tableName = keySet.iterator().next();
            log.debug("test table name: " + tableName);
            TableDesc tableDesc = tableDescs.get(tableName);
            log.debug("TableDesc " + tableDesc);

            // Get the table from the database
            StringBuilder sb = new StringBuilder();
            sb.append("select * from ");
            sb.append(tableName);
            Connection con = ds.getConnection();
            Statement  stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery(sb.toString());

            // Check the table column names.
            ResultSetMetaData metadata = rs.getMetaData();
            for (int i = 1; i <= metadata.getColumnCount(); i++)
            {
                String columnName = metadata.getColumnName(i);
                String columnTypeName = metadata.getColumnTypeName(i);
                if (types.containsKey(columnName))
                {
                    log.debug("columnName: " + columnName + ", type: " + columnTypeName);
                    String typeName = types.get(columnName);
                    Assert.assertEquals(typeName, columnTypeName);
                }
                else
                {
                    Assert.fail("invalid database column name " + columnName);
                }
            }

            // Check the table data.
            rs.next();
            Assert.assertEquals(1, rs.getShort("short_datatype"));
            Assert.assertEquals(1, rs.getShort("smallint_xtype"));
            Assert.assertEquals(2, rs.getInt("int_datatype"));
            Assert.assertEquals(2, rs.getInt("integer_xtype"));
            Assert.assertEquals(3, rs.getLong("long_datatype"));
            Assert.assertEquals(3, rs.getLong("bigint_xtype"));
            Assert.assertEquals(4.4, rs.getFloat("float_datatype"), 1);
            Assert.assertEquals(4.4, rs.getFloat("real_xtype"), 1);
            Assert.assertEquals(5.5, rs.getDouble("double_datatype"), 1);
            Assert.assertEquals(5.5, rs.getDouble("double_xtype"), 1);
            Assert.assertEquals("char_datatype", rs.getString("char_datatype").trim());
            Assert.assertEquals("char_xtype", rs.getString("char_xtype").trim());
            Assert.assertEquals("varchar_xtype", rs.getString("varchar_xtype").trim());
            Assert.assertEquals(new Timestamp(date.getTime()), rs.getTimestamp("timestamp_xtype"));

            log.info("testUploadAllTypes passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
    @Test
    public void testInvalidTableNames()
    {
        try
        {
            log.debug("testInvalidTableNames");

            // New instance of UploadManager.
            FileUploadManagerImpl uploadManager = new FileUploadManagerImpl();
            uploadManager.setDataSource(ds);
            uploadManager.setFilename("AllTypes.xml");

            // Create a JobID
            String jobID = Long.toString(System.currentTimeMillis());
            
            // no table name
            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add(new Parameter("UPLOAD", ",http://localhost/foo"));
            try
            {
                Map<String, TableDesc> tableDescs = uploadManager.upload(paramList, jobID);
                Assert.fail("empty table name didn't cause an Exception");
            }
            catch (RuntimeException ignore) { }

            // table names with a spaces
            paramList.clear();
            paramList.add(new Parameter("UPLOAD", " ,http://localhost/foo"));
            try
            {
                Map<String, TableDesc> tableDescs = uploadManager.upload(paramList, jobID);
                Assert.fail("table name with a space didn't cause an Exception");
            }
            catch (RuntimeException ignore) { }
            
            paramList.clear();
            paramList.add(new Parameter("UPLOAD", "table name,http://localhost/foo"));
            try
            {
                Map<String, TableDesc> tableDescs = uploadManager.upload(paramList, jobID);
                Assert.fail("table name with a space didn't cause an Exception");
            }
            catch (RuntimeException ignore) { }

            paramList.clear();
            paramList.add(new Parameter("UPLOAD", "tablename ,http://localhost/foo"));
            try
            {
                Map<String, TableDesc> tableDescs = uploadManager.upload(paramList, jobID);
                Assert.fail("table name with a space didn't cause an Exception");
            }
            catch (RuntimeException ignore) { }
            
            // Invalid table names
            paramList.clear();
            paramList.add(new Parameter("UPLOAD", "1table_name,http://localhost/foo"));
            try
            {
                Map<String, TableDesc> tableDescs = uploadManager.upload(paramList, jobID);
                Assert.fail("table name starting with a digit didn't cause an Exception");
            }
            catch (RuntimeException ignore) { }
            
            paramList.clear();
            paramList.add(new Parameter("UPLOAD", "_tablename,http://localhost/foo"));
            try
            {
                Map<String, TableDesc> tableDescs = uploadManager.upload(paramList, jobID);
                Assert.fail("table name starting with an underscore didn't cause an Exception");
            }
            catch (RuntimeException ignore) { }
            
            paramList.clear();
            paramList.add(new Parameter("UPLOAD", "tablename!,http://localhost/foo"));
            try
            {
                Map<String, TableDesc> tableDescs = uploadManager.upload(paramList, jobID);
                Assert.fail("table name with a exclaimation didn't cause an Exception");
            }
            catch (RuntimeException ignore) { }
            
            paramList.clear();
            paramList.add(new Parameter("UPLOAD", "?tablename,http://localhost/foo"));
            try
            {
                Map<String, TableDesc> tableDescs = uploadManager.upload(paramList, jobID);
                Assert.fail("table name with a question mark didn't cause an Exception");
            }
            catch (RuntimeException ignore) { }
            
            paramList.clear();
            paramList.add(new Parameter("UPLOAD", "&tablename,http://localhost/foo"));
            try
            {
                Map<String, TableDesc> tableDescs = uploadManager.upload(paramList, jobID);
                Assert.fail("table name with an ampersand didn't cause an Exception");
            }
            catch (RuntimeException ignore) { }
            
            log.info("testInvalidTableNames passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
    @Test
    public void testColumnNameStartsWithDigit()
    {
        try
        {
            log.debug("testColumnNameStartsWithDigit");
            
            // Create a JobID
            String jobID = Long.toString(System.currentTimeMillis());
            
            // New instance of UploadManager.
            FileUploadManagerImpl uploadManager = new FileUploadManagerImpl();
            uploadManager.setDataSource(ds);
            uploadManager.setFilename("ColumnNameStartsWithDigit.xml");

            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add(new Parameter("UPLOAD", "tablename,http://localhost/foo"));
            
            try
            {
                Map<String, TableDesc> tableDescs = uploadManager.upload(paramList, jobID);
                Assert.fail("column name starting with a digit didn't cause an Exception");
            }   
            catch (RuntimeException e)
            {
                Throwable cause = e.getCause();
                while(cause.getCause() != null)
                    cause = cause.getCause();
                if (cause instanceof ADQLIdentifierException)
                    log.debug("caught expected: " + cause);
                else
                    Assert.fail(e.getCause().getMessage());
            }

            log.info("testColumnNameStartsWithDigit passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
    @Test
    public void testColumnNameStartsWithUnderscore()
    {
        try
        {
            log.debug("testColumnNameStartsWithUnderscore");
            
            // Create a JobID
            String jobID = Long.toString(System.currentTimeMillis());
            
            // New instance of UploadManager.
            FileUploadManagerImpl uploadManager = new FileUploadManagerImpl();
            uploadManager.setDataSource(ds);
            uploadManager.setFilename("ColumnNameStartsWithUnderscore.xml");

            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add(new Parameter("UPLOAD", "tablename,http://localhost/foo"));
            
            try
            {
                Map<String, TableDesc> tableDescs = uploadManager.upload(paramList, jobID);
                Assert.fail("column name starting with an underscore didn't cause an Exception");
            }   
            catch (RuntimeException e)
            {
                Throwable cause = e.getCause();
                while(cause.getCause() != null)
                    cause = cause.getCause();
                if (cause instanceof ADQLIdentifierException)
                    log.debug("caught expected: " + cause);
                else
                    Assert.fail(e.getCause().getMessage());
            }

            log.info("testColumnNameStartsWithUnderscore passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    // TODO: current implementation trims the leading and trailing whitespace
    // from an attribute value so this test case silently works... unclear if this
    // is correct behaviour, JDOM-specific, or what effect it would have (eg the
    // extra space would be innocuous whitespace in the ADQL query as well).
    //@Test
    public void testColumnNameStartsWithSpace()
    {
        try
        {
            log.debug("testColumnNameStartsWithSpace");
            
            // Create a JobID
            String jobID = Long.toString(System.currentTimeMillis());
            
            // New instance of UploadManager.
            FileUploadManagerImpl uploadManager = new FileUploadManagerImpl();
            uploadManager.setDataSource(ds);
            uploadManager.setFilename("ColumnNameStartsWithSpace.xml");

            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add(new Parameter("UPLOAD", "tablename,http://localhost/foo"));
            
            try
            {
                Map<String, TableDesc> tableDescs = uploadManager.upload(paramList, jobID);
                Assert.fail("column name starting with a space didn't cause an Exception");
            }
            catch (RuntimeException e)
            {
                Throwable cause = e.getCause();
                while(cause.getCause() != null)
                    cause = cause.getCause();
                if (cause instanceof ADQLIdentifierException)
                    log.debug("caught expected: " + cause);
                else
                    Assert.fail(e.getCause().getMessage());
            }

            log.info("testColumnNameStartsWithSpace passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
    @Test
    public void testColumnNameContainsSpace()
    {
        try
        {
            log.debug("testColumnNameContainsSpace");
            
            // Create a JobID
            String jobID = Long.toString(System.currentTimeMillis());
            
            // New instance of UploadManager.
            FileUploadManagerImpl uploadManager = new FileUploadManagerImpl();
            uploadManager.setDataSource(ds);
            uploadManager.setFilename("ColumnNameContainsSpace.xml");

            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add(new Parameter("UPLOAD", "tablename,http://localhost/foo"));
            
            try
            {
                Map<String, TableDesc> tableDescs = uploadManager.upload(paramList, jobID);
                Assert.fail("column name containing a space didn't cause an Exception");
            }
            catch (RuntimeException e)
            {
                Throwable cause = e.getCause();
                while(cause.getCause() != null)
                    cause = cause.getCause();
                if (cause instanceof ADQLIdentifierException)
                    log.debug("caught expected: " + cause);
                else
                    Assert.fail(e.getCause().getMessage());
            }

            log.info("testColumnNameContainsSpace passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    // see note above for testColumnNameStartsWithSpace
    //@Test
    public void testColumnNameEndsWithSpace()
    {
        try
        {
            log.debug("testColumnNameEndsWithSpace");
            
            // Create a JobID
            String jobID = Long.toString(System.currentTimeMillis());
            
            // New instance of UploadManager.
            FileUploadManagerImpl uploadManager = new FileUploadManagerImpl();
            uploadManager.setDataSource(ds);
            uploadManager.setFilename("ColumnNameEndsWithSpace.xml");

            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add(new Parameter("UPLOAD", "tablename,http://localhost/foo"));
            
            try
            {
                Map<String, TableDesc> tableDescs = uploadManager.upload(paramList, jobID);
                Assert.fail("column name ending with a space didn't cause an Exception");
            }
            catch (RuntimeException e)
            {
                Throwable cause = e.getCause();
                while(cause.getCause() != null)
                    cause = cause.getCause();
                if (cause instanceof ADQLIdentifierException)
                    log.debug("caught expected: " + cause);
                else
                    Assert.fail(e.getCause().getMessage());
            }

            log.info("testColumnNameEndsWithSpace passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }

    @Test
    public void testColumnNameStartsWithInvalidLetter()
    {
        try
        {
            log.debug("testColumnNameStartsWithInvalidLetter");
            
            // Create a JobID
            String jobID = Long.toString(System.currentTimeMillis());
            
            // New instance of UploadManager.
            FileUploadManagerImpl uploadManager = new FileUploadManagerImpl();
            uploadManager.setDataSource(ds);
            uploadManager.setFilename("ColumnNameStartsWithInvalidLetter.xml");

            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add(new Parameter("UPLOAD", "tablename,http://localhost/foo"));
            
            try
            {
                Map<String, TableDesc> tableDescs = uploadManager.upload(paramList, jobID);
                Assert.fail("column name starting with an invalid letter didn't cause an Exception");
            }
            catch (RuntimeException e)
            {
                Throwable cause = e.getCause();
                while(cause.getCause() != null)
                    cause = cause.getCause();
                if (cause instanceof ADQLIdentifierException)
                    log.debug("caught expected: " + cause);
                else
                {
                    log.error("unexpected exception", e);
                    Assert.fail("unexpected exception: " + e);
                }
            }

            log.info("testColumnNameStartsWithInvalidLetter passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
    @Test
    public void testColumnNameContainsInvalidLetter()
    {
        try
        {
            log.debug("testColumnNamContainsInvalidLetter");
            
            // Create a JobID
            String jobID = Long.toString(System.currentTimeMillis());
            
            // New instance of UploadManager.
            FileUploadManagerImpl uploadManager = new FileUploadManagerImpl();
            uploadManager.setDataSource(ds);
            uploadManager.setFilename("ColumnNameContainsInvalidLetter.xml");

            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add(new Parameter("UPLOAD", "tablename,http://localhost/foo"));
            
            try
            {
                Map<String, TableDesc> tableDescs = uploadManager.upload(paramList, jobID);
                Assert.fail("column name containing an invalid letter didn't cause an Exception");
            }
            catch (RuntimeException e)
            {
                Throwable cause = e.getCause();
                while(cause.getCause() != null)
                    cause = cause.getCause();
                if (cause instanceof ADQLIdentifierException)
                    log.debug("caught expected: " + cause);
                else
                    Assert.fail(e.getCause().getMessage());
            }

            log.info("testColumnNameContainsInvalidLetter passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
    @Test
    public void testColumnNameEndsWithInvalidLetter()
    {
        try
        {
            log.debug("testColumnNameEndsWithInvalidLetter");
            
            // Create a JobID
            String jobID = Long.toString(System.currentTimeMillis());
            
            // New instance of UploadManager.
            FileUploadManagerImpl uploadManager = new FileUploadManagerImpl();
            uploadManager.setDataSource(ds);
            uploadManager.setFilename("ColumnNameEndsWithInvalidLetter.xml");

            List<Parameter> paramList = new ArrayList<Parameter>();
            paramList.add(new Parameter("UPLOAD", "tablename,http://localhost/foo"));
            
            try
            {
                Map<String, TableDesc> tableDescs = uploadManager.upload(paramList, jobID);
                Assert.fail("column name ending with an invalid letter didn't cause an Exception");
            }
            catch (RuntimeException e)
            {
                if (e.getCause() instanceof MissingResourceException)
                    Assert.fail(e.getCause().getMessage());
            }

            log.info("testColumnNameEndsWithInvalidLetter passed.");
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
    private class FileUploadManagerImpl extends BasicUploadManager
    {
        String filename;

        public FileUploadManagerImpl()
        {
            super(999);
        }


        @Override
        protected VOTableParser getVOTableParser(UploadTable uploadTable)
            throws IOException
        {
            VOTableParser parser = new JDOMVOTableParser();
            parser.setTableName(uploadTable.tableName);
            File file = FileUtil.getFileFromResource(filename, BasicUploadManagerTest.class);
            parser.setInputStream(new BufferedInputStream(new FileInputStream(file)));
            return parser;
        }
        
        protected void setFilename(String filename)
        {
            this.filename = filename;
        }
        
    }
        
}
