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

import ca.nrc.cadc.tap.Validator.TapParams;
import ca.nrc.cadc.tap.schema.Table;
import ca.nrc.cadc.uws.Parameter;

public class UploadManager {
    
    public static final String UPLOAD = TapParams.UPLOAD.toString();
    
    public Map<String,Table> upload( List<Parameter> paramList, String jobID ) throws MalformedURLException, IOException, JDOMException {
        
        HashMap<String,Table> tables = new HashMap<String,Table>();
        // <TAP_UPLOAD.theirName, table>

        List<String> uploadParamPairs = getUploadParams( paramList );
        
        if ( true )
            throw new UnsupportedOperationException( UPLOAD+" parameter not supported at this time" );
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
        
        // Do stuff here.
        
        return tables;
    }
    
    /*  Extract the UPLOAD parameters from the full parameter list.
     */
    private List<String> getUploadParams(List<Parameter> paramList) {

        List<String> uploadParamPairs  = TapUtil.findParameterValues( UPLOAD, paramList );
        List<String> uniqueTableParams = new ArrayList<String>();

        if ( uploadParamPairs != null ) {
            if ( uploadParamPairs.size() == 0 )
                throw new IllegalStateException( "Missing UPLOAD values" );
            else {
                for ( String pairStr : uploadParamPairs ) {
                    if ( pairStr==null || pairStr.trim().length()==0 )
                        throw new IllegalStateException( "Name-value pair missing from UPLOAD parameter list: "+paramList );
                    String [] pair = pairStr.split(",");
                    String tableName  = null;
                    String tableURI = null;
                    try {
                        tableName = pair[0];
                    }
                    catch ( IndexOutOfBoundsException iobe ) {
                        throw new IllegalStateException( "Table name missing from UPLOAD parameter: "+pairStr );
                    }
                    try {
                        tableURI = pair[1];
                    }
                    catch ( IndexOutOfBoundsException iobe ) {
                        throw new IllegalStateException( "URI missing from UPLOAD parameter: "+pairStr );
                    }
                    if ( tableName==null || tableName.trim().length()==0 )
                        throw new IllegalStateException( "Table name missing from UPLOAD parameter: "+pairStr );
                    if ( tableName.startsWith(" ") || tableName.contains("  ") || tableName.endsWith(" ") )
                        throw new IllegalStateException( "Table tableName from UPLOAD parameter has invalid blanks: "+tableName );
                    if ( tableURI==null || tableURI.trim().length()==0 )
                        throw new IllegalStateException( "URI missing from UPLOAD parameter: "+pairStr );
                    if ( !tableURI.startsWith("http:") )
                        throw new IllegalStateException( "Table URI has unsupported protocol in UPLOAD parameter: "+tableURI );
                    if ( tableNameIsDuplicate(pairStr,uniqueTableParams) )
                        throw new IllegalStateException( "Duplicate table name in UPLOAD parameter: "+paramList );
                    uniqueTableParams.add(pairStr);
                } // end-for each upload param name-uri pair
            }
        } // end-if UPLOAD param found

        return uploadParamPairs;
    }
    
    
    private boolean tableNameIsDuplicate( String newPairStr,
                                          List<String> uniqueTableParams )
    {
        String newTableName = newPairStr.split(",")[0];
        
        Iterator<String> uploadIt = uniqueTableParams.iterator();
        while ( uploadIt.hasNext() )
        {
            String oldTableName = uploadIt.next().split(",")[0];
            if ( oldTableName.equals(newTableName) )
                return true;
        }
        
        return false;
    }

    /*
    private static Table readVOTable( URL url ) throws IOException, JDOMException {
        
        SAXBuilder sb = new SAXBuilder("org.apache.xerces.parsers.SAXParser", false);
        
        Document doc = sb.build(url);

        Element votable = doc.getRootElement();
        System.out.println(votable + "\n  is in " + votable.getNamespace());
        List els = votable.getChildren();
        Iterator i = els.iterator();
        while ( i.hasNext() )
        {
            Element e = (Element) i.next();
            System.out.println(e + "\n  is in " + e.getNamespace());
        }
        
        return null;
    }
    */
}
