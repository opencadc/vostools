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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import ca.nrc.cadc.tap.schema.Column;
import ca.nrc.cadc.tap.schema.Table;
import ca.nrc.cadc.uws.Parameter;
import javax.sql.DataSource;

public class UploadManager {
    
    public static final String SCHEMA = "TAP_SCHEMA";
    public static final String UPLOAD = "UPLOAD";
    
    private Map<String,Table>          metadata = new HashMap<String,Table>();
    private Map<String,List<String[]>> dataVals = new HashMap<String,List<String[]>>();
    
    private DataSource dataSource;
    
    public UploadManager(DataSource dataSource)
    {
        this.dataSource = dataSource;
    }
    
    public Map<String,Table> upload( List<Parameter> paramList, String jobID ) throws IOException, JDOMException {
        
        //  Extract and validate the UPLOAD parameters
        //  from the full parameter list.
        Map<String,URI> uploadParamPairs = getUploadParams( paramList );
        if ( uploadParamPairs == null )
            return null;
        
        if (dataSource == null)
            throw new UnsupportedOperationException(UPLOAD + ": not suported, cause: null DataSource");
        
        //  Read (into memory) the column names and values
        //  of tables named by the UPLOAD parameter(s).
        Iterator<String> uploadParamsIt = uploadParamPairs.keySet().iterator(); 
        try {
            while ( uploadParamsIt.hasNext() ) {
                String shortName = uploadParamsIt.next();
                String tableName = SCHEMA+"."+shortName+"_"+jobID;
                URI    tableURI  = uploadParamPairs.get(shortName);

                SAXBuilder sb      = new SAXBuilder("org.apache.xerces.parsers.SAXParser", false);
                URL        url     = tableURI.toURL();
                Document   doc     = sb.build(url);
                Element    voTable = doc.getRootElement();
                
                //  TODO: Why does getChild return null here?
                //  Element resource = voTable.getChild("RESOURCE");
                
                metadata.put( shortName, new Table()     );
                dataVals.put( shortName, new ArrayList() );
                
                readMetadata( shortName, tableName, voTable );
                readDataVals( shortName, tableName, voTable );
            }
        }
        catch ( MalformedURLException mue ) {
            throw ( new IllegalStateException( mue.getMessage() ) );
        }

        //  Create (in the database) the tables named in the parameter
        //  list and described in the URI-referenced XML files.
        Iterator<String> tableNamesIt = metadata.keySet().iterator();
        try {
            while ( tableNamesIt.hasNext() ) {
                String tableName = tableNamesIt.next();
                String sql = "create table "+tableName+" ( ";
                List<Column> columns = metadata.get(tableName).getColumns();
                for ( int i=0; i<columns.size(); i++ ) {
                    Column col = columns.get(i);
                    sql += col.columnName+" ";
                    sql += localDbType(col.datatype,col.size.toString());
                    if ( i+1<columns.size() )
                        sql += ", ";
                }
                sql += " )";
                //System.out.println( "SQL: "+sql );
            }
        }
        catch ( Exception sqle ) {
            throw ( new IllegalStateException( sqle.getMessage() ) );
        }
        
        //  Populate the newly created database tables with
        //  values from the URI-referenced XML files.
        /*
        tableNamesIt = metadata.keySet().iterator();
        try {
            while ( tableNamesIt.hasNext() ) {
                String tableName = tableNamesIt.next();
                String sqlFront = "insert into "+tableName+" ( ";
                List<Column> columns = metadata.get(tableName).getColumns();
                for ( int i=0; i<columns.size(); i++ ) {
                    Column col = columns.get(i);
                    sql += col.columnName;
                    if ( i+1<columns.size() )
                        sql += ", ";
                }
                sqlFront += " ) values ( ";
                String sqlBack = "";
                for ( int i=0; i<rowVals.size(); i++ ) {
                    for ( int j=0; i<columns.size(); i++ ) {
                        String value = rowVals.get(tableName).get(i)[j];
                    }
                    String value = rowVals.get(tableName).
                    Column col = columns.get(i);
                    sql += col.columnName+" ";
                    sql += localDbType(col.datatype,col.size.toString());
                    if ( i+1<columns.size() )
                        sql += ", ";
                }
                sql += " )";
                
                System.out.println( "SQL: "+sql );
            }
        }
        catch ( Exception sqle ) {
            throw ( new IllegalStateException( sqle.getMessage() ) );
        }
        */
        
        /*
        for ( Parameter param : uploadParams ) {
            // 1. validate
            
            // 2. open stream and read with JDOM parser, not stil
            String tableName = "TAP_UPLOAD."+param.getName()+"_"+jobID;
            //tables.put( tableName, readVOTable( new URL(param.getValue()) ) );
            
            // 3. create TAP_UPLOAD.theirName_jobID table in db
            
            // 4. create table metadata from tap schema (see votable doc)
        }
        */

        if ( true )
            throw new UnsupportedOperationException( UPLOAD+" parameter not supported" );
        
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
    
    private void readMetadata( String shortName, String tableName, Element el ) {
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
                col.datatype   = inner.getAttributeValue("datatype");
                //
                String sizeAttr = inner.getAttributeValue("width");
                //col.size       = Integer.valueOf(inner.getAttributeValue("width"));
                //  In the absence of a width, set the size arbitrarily until an official example is available.
                col.size = ( sizeAttr==null ) ? new Integer(32) : Integer.valueOf(sizeAttr);
                //
                Table table = metadata.get(shortName);
                if ( table.columns == null )
                    table.columns = new ArrayList<Column>();
                table.columns.add(col);
            }
            else
                readMetadata( shortName, tableName, inner );
        }
    }
    
    private void readDataVals( String shortName, String tableName, Element el ) {
        List<Element> els = el.getChildren();
        if ( els.size() < 1 )
            return;
        Iterator<Element> elsIt = els.iterator();
        while ( elsIt.hasNext() ) {
            Element inner = elsIt.next();
            if ( inner.getName().equals("TR") ) {
                List<Element> colVals = inner.getChildren();
                int numCols = metadata.get(shortName).columns.size();
                String [] row = new String[numCols];
                for ( int i=0; i<numCols; i++ )
                    row[i] = colVals.get(i).getValue();
                dataVals.get(shortName).add(row);
            }
            else
                readDataVals( shortName, tableName, inner );
        }
    }
    
    private String localDbType( String voType, String width ) {
        
        final String CHAR  = "char";
        final String INT   = "int";
        final String FLOAT = "float";
        
        final String VARCHAR = "varchar";
        final String INTEGER = "integer";
        final String REAL    = "real";
        
        String localType = "";
        
        if ( voType.equalsIgnoreCase(CHAR) ) {
            localType += VARCHAR;
            if ( width!=null ) {
                localType += "("+width+")";
            }
        }
        else if ( voType.equalsIgnoreCase(INT) ) {
            localType += INTEGER;
        }
        else if ( voType.equalsIgnoreCase(FLOAT) ) {
            localType += REAL;
        }
        else
            throw new IllegalStateException( "Invalid data type: "+voType );
        
        return localType;
    }

}
