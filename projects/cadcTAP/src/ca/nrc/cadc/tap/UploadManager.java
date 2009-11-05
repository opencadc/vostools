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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.tap.schema.Column;
import ca.nrc.cadc.tap.schema.Table;
import ca.nrc.cadc.uws.Parameter;

public class UploadManager {
    
    private static final Logger log = Logger.getLogger( UploadManager.class );

    private static final String BOOL   = "boolean";
    private static final String SHORT  = "short";
    private static final String INT    = "int";
    private static final String LONG   = "long";
    private static final String FLOAT  = "float";
    private static final String DOUBLE = "double";
    private static final String CHAR   = "char";
    private static final String UBYTE  = "unsignedByte";

    private static final String BLOB      = "adql:BLOB";
    private static final String CLOB      = "adql:CLOB";
    private static final String TIMESTAMP = "adql:TIMESTAMP";
    private static final String POINT     = "adql:POINT";
    private static final String REGION    = "adql:REGION";
    
    public static final String SCHEMA = "TAP_UPLOAD";
    public static final String UPLOAD = "UPLOAD";
    
    private Map<String,Table>          metadata;
    private Map<String,List<String[]>> dataRows;
    
    private DataSource dataSource;
    private Connection conn;
    
    public UploadManager(DataSource dataSource) {
        
        this.dataSource = dataSource;
        
        try {
            conn = dataSource.getConnection();
        }
        catch (SQLException e) {
            throw new IllegalStateException( "Failed to get connection from data source" );
        }
    }
    
    public Map<String,Table> upload( List<Parameter> paramList, String jobID ) throws IOException, JDOMException {

        log.debug( "Started table upload for jobID: "+jobID );

        metadata = new HashMap<String,Table>();
        dataRows = new HashMap<String,List<String[]>>();

        //  Extract and validate the UPLOAD parameters
        //  from the full parameter list.
        Map<String,URI> uploadParamPairs = getUploadParams( paramList );
        if ( uploadParamPairs == null )
            return null;
        
        if (dataSource == null)
            throw new UnsupportedOperationException(UPLOAD+" parameter not suported, cause: null DataSource");
        
        //  Read (into memory) the column names and values
        //  of tables named by the UPLOAD parameter(s).
        Iterator<String> uploadParamsIt = uploadParamPairs.keySet().iterator(); 
        try {
            while ( uploadParamsIt.hasNext() ) {
                String shortName = uploadParamsIt.next();
                String baseName  = ( shortName.toLowerCase().startsWith(SCHEMA.toLowerCase()+".") ) ? shortName : SCHEMA+"."+shortName;
                String tableName = baseName+"_"+jobID;
                URI    tableURI  = uploadParamPairs.get(shortName);
                
                SAXBuilder sb      = new SAXBuilder("org.apache.xerces.parsers.SAXParser", false);
                URL        url     = tableURI.toURL();
                Document   doc     = sb.build(url);
                Element    voTable = doc.getRootElement();
                
                metadata.put( baseName, new Table() );
                dataRows.put( baseName, new ArrayList<String[]>() );
                
                metadata.get( baseName ).tableName = tableName;
                
                readMetadata( baseName, tableName, voTable );
                readDataRows( baseName, tableName, voTable );
            }
        }
        catch ( MalformedURLException mue ) {
            throw ( new IllegalStateException( mue.getMessage() ) );
        }

        //  Create (in the database) the tables named in the parameter
        //  list and described in the URI-referenced XML files.
        Iterator<String> baseNamesIt = metadata.keySet().iterator();
        try {
            while ( baseNamesIt.hasNext() ) {
                String baseName = baseNamesIt.next();
                String tableName = metadata.get(baseName).getTableName();
                String sql = "create table "+tableName+" ( ";
                List<Column> columns = metadata.get(baseName).getColumns();
                for ( int i=0; i<columns.size(); i++ ) {
                    Column col = columns.get(i);
                    sql += col.columnName+" ";
                    sql += localDbType(col);
                    if ( i+1<columns.size() )
                        sql += ", ";
                }
                sql += " )";
                Statement  stmt = conn.createStatement();
                log.debug( "About to execute DDL: "+sql );
                stmt.execute(sql);
            }
        }
        catch ( Exception sqle ) {
            throw ( new IllegalStateException( sqle.getMessage() ) );
        }
        
        //  Populate the newly created database tables with
        //  values from the URI-referenced XML files.
        baseNamesIt = metadata.keySet().iterator();
        try {
            while ( baseNamesIt.hasNext() ) {
                String baseName = baseNamesIt.next();
                String tableName = metadata.get(baseName).getTableName();
                List<String[]> tableRows = dataRows.get(baseName);
                for ( int i=0; i<tableRows.size(); i++ ) {
                    String sql = "insert into "+tableName+" ( ";
                    List<Column> columns = metadata.get(baseName).getColumns();
                    for ( int j=0; j<columns.size(); j++ ) {
                        Column col = columns.get(j);
                        sql += col.columnName;
                        if ( j+1<columns.size() )
                            sql += ", ";
                    }
                    sql += " ) values ( ";
                    String [] dataCols = tableRows.get(i);
                    int numDataCols = dataCols.length;
                    for ( int j=0; j<numDataCols; j++ ) {
                        String value = dataCols[j];
                        sql += formatValue( value, columns.get(j).datatype );
                        if ( j+1 < numDataCols )
                            sql += ", ";
                    }
                    sql += " )";
                    Statement stmt = conn.createStatement();
                    log.debug( "About to execute DML: "+sql );
                    stmt.execute(sql);
                }
            }
        }
        catch ( Exception sqle ) {
            throw ( new IllegalStateException( sqle.getMessage() ) );
        }
        
        if ( false )  //  Toggle this as required until UPLOAD is here to stay
            throw new UnsupportedOperationException( UPLOAD+" parameter not supported" );

        log.debug( "Finished loading "+metadata.size()+" table(s) for jobID: "+jobID );

        return metadata;
    }
    
    /*  Extract the UPLOAD parameters from the full parameter list.
     */
    private Map<String,URI> getUploadParams(List<Parameter> paramList) {

        List<String> uploadParamPairs  = TapUtil.findParameterValues( UPLOAD, paramList );
        Map<String,URI> uniqueTableParams = new HashMap<String,URI>();

        if ( uploadParamPairs != null ) {
            if ( uploadParamPairs.size() == 0 )
                throw new IllegalStateException( "Missing UPLOAD values" );
            else {
                for ( String pairStr : uploadParamPairs ) {
                    log.debug( "UPLOAD parameter name,value pair: "+pairStr );
                    if ( pairStr==null || pairStr.trim().length()==0 )
                        throw new IllegalStateException( "Name-value pair missing from UPLOAD parameter list: "+paramList );
                    String [] pair = pairStr.split(",");
                    String tableName  = null;
                    URI    tableURI   = null;
                    try {
                        tableName = pair[0];
                    }
                    catch ( IndexOutOfBoundsException iobe ) {
                        throw new IllegalStateException( "Table name missing from UPLOAD parameter: "+pairStr );
                    }
                    try {
                        tableURI = new URI(pair[1]);
                    }
                    catch ( IndexOutOfBoundsException iobe ) {
                        throw new IllegalStateException( "URI missing from UPLOAD parameter: "+pairStr );
                    }
                    catch ( URISyntaxException use ) {
                        throw new IllegalStateException( "UPLOAD parameter has invalid URI: "+tableURI );
                    }
                    if ( tableName==null || tableName.trim().length()==0 )
                        throw new IllegalStateException( "Table name missing from UPLOAD parameter: "+pairStr );
                    if ( tableName.startsWith(" ") || tableName.contains("  ") || tableName.endsWith(" ") )
                        throw new IllegalStateException( "Table tableName from UPLOAD parameter has invalid blanks: "+tableName );
                    if ( !tableURI.getScheme().equals("http") )
                        throw new IllegalStateException( "Table URI has unsupported protocol in UPLOAD parameter: "+tableURI );
                    if ( !tableURI.getHost().equals("localhost") )
                        throw new IllegalStateException( "Table URI points to an unsupported host: "+tableURI.getHost() );
                    if ( uniqueTableParams.containsKey(tableName) )
                        throw new IllegalStateException( "Duplicate table name in UPLOAD parameter: "+paramList );
                    uniqueTableParams.put( tableName, tableURI );
                } // end-for each upload param name-uri pair
            }
        } // end-if UPLOAD param found

        if ( uniqueTableParams.size() > 0 )
            return uniqueTableParams;
        else
            return null;
    }
    
    private void readMetadata( String baseName, String tableName, Element el ) {       
        List<Element> els = el.getChildren();
        if ( els.size() < 1 )
            return;
        Iterator<Element> elsIt = els.iterator();
        while ( elsIt.hasNext() ) {
            Element inner = elsIt.next();
            Column col = new Column();
            col.tableName = tableName;
            if ( inner.getName().equals("FIELD") ) {
                col.columnName = inner.getAttributeValue("name");
                String xtype = inner.getAttributeValue("xtype");
                if ( xtype == null )
                    col.datatype = inner.getAttributeValue("datatype");
                else
                    col.datatype = xtype;
                String sizeAttr = inner.getAttributeValue("width");
                if ( sizeAttr==null || sizeAttr.length()==0 )
                    col.size = 1;
                else if ( sizeAttr.equals("*") )
                    col.size = 512;
                else if ( sizeAttr.endsWith("*") )
                    col.size = Integer.parseInt( sizeAttr.substring( 0, sizeAttr.lastIndexOf("*") ) );
                else
                    col.size = Integer.parseInt(sizeAttr);
                Table table = metadata.get(baseName);
                if ( table.columns == null )
                    table.columns = new ArrayList<Column>();
                table.columns.add(col);
                log.debug( "Finished reading column metadata: "+col.toString() );
            }
            else
                readMetadata( baseName, tableName, inner );
        }
    }
    
    private void readDataRows( String baseName, String tableName, Element el ) {
        List<Element> els = el.getChildren();
        if ( els.size() < 1 )
            return;
        Iterator<Element> elsIt = els.iterator();
        while ( elsIt.hasNext() ) {
            Element inner = elsIt.next();
            if ( inner.getName().equals("TR") ) {
                List<Element> colVals = inner.getChildren();
                int numCols = metadata.get(baseName).columns.size();
                String [] row = new String[numCols];
                for ( int i=0; i<numCols; i++ ) {
                    String value = colVals.get(i).getValue();
                    if ( metadata.get(baseName).columns.get(i).datatype.equals(TIMESTAMP) )
                        try {
                            row[i] = DateUtil.toString( DateUtil.toDate(value,DateUtil.IVOA_DATE_FORMAT), DateUtil.IVOA_DATE_FORMAT);
                        }
                        catch ( ParseException pe ) {
                            throw new IllegalStateException( "UPLOAD parameter value for table: "+baseName+
                                                             " is in an invalid datetime format: "+value );
                        }
                    else
                        row[i] = value;
                    log.debug( "Finished reading value: "+row[i] );
                }
                dataRows.get(baseName).add(row);
                log.debug( "Finished reading row of data" );
            }
            else
                readDataRows( baseName, tableName, inner );
        }
    }
    
    private String localDbType( Column col ) {
        
        HashMap<String,String> typeMap  = new HashMap<String,String>();
        
        typeMap.put( BOOL,    BOOL );
        typeMap.put( SHORT,  "smallint" );
        typeMap.put( INT,    "integer" );
        typeMap.put( LONG,   "bigint" );
        typeMap.put( FLOAT,  "real" );
        typeMap.put( DOUBLE, "double precision" );
        //
        typeMap.put( CHAR,       CHAR );
        typeMap.put( UBYTE,     "byta" );
        typeMap.put( BLOB,      "TODO: unknown" );
        typeMap.put( CLOB,      "TODO: unknown" );
        typeMap.put( TIMESTAMP, "timestamp" );
        typeMap.put( POINT,     "spoint" );
        typeMap.put( REGION,    "spoly" );
        
        String  type  = col.datatype;
        Integer width = col.size;
        
        if ( width == 1 )
            return typeMap.get(type);

        if ( type.equals(SHORT)  ||
             type.equals(INT)    ||
             type.equals(LONG)   ||
             type.equals(FLOAT)  ||
             type.equals(DOUBLE) ) {
            //  Numeric type with size > 1
            return "bytea";
        }

        if ( type.equals(CHAR) )
            return "varchar("+width+")";

        throw new IllegalStateException( "Could not determine local DB datatype for: datatype="+type+", width="+width );
    }
    
    private String formatValue( String value, String datatype ) {
        if ( datatype.equals(CHAR) )
            return "'"+escapeTicks(value)+"'";
        else if ( datatype.equals(TIMESTAMP) )
            return "'"+value+"'";
        else
            return value;
    }
    
    private String escapeTicks( String value ) {
        if ( value.contains("'") ) {
            String [] allParts = value.split("'");
            StringBuffer escapedValue = new StringBuffer();
            for ( int partNum=0; partNum<allParts.length; partNum++ ) {
                String part = allParts[partNum];
                escapedValue.append(part);
                if ( partNum+1 < allParts.length )
                    escapedValue.append("\"'\"");
            }
            return escapedValue.toString();
        }
        else
            return value;
    }

}
