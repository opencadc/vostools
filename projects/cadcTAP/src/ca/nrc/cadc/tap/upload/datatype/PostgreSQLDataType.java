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

package ca.nrc.cadc.tap.upload.datatype;

import ca.nrc.cadc.tap.schema.ColumnDesc;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;

/**
 *
 * @author jburke
 */
public class PostgreSQLDataType implements DatabaseDataType
{
    private static Logger log = Logger.getLogger(PostgreSQLDataType.class);
    
    /**
     * Mapping of ADQL data types to PostgreSQL data types. Subclasses can (must)
     * add a mapping for ADQL_POINT and ADQL_REGION if they support this use.
     */
    protected static Map<String, String> dataTypes;
    static
    {
        dataTypes = new HashMap<String, String>();
        dataTypes.put(ADQLDataType.ADQL_SMALLINT, "SMALLINT");
        dataTypes.put(ADQLDataType.ADQL_INTEGER, "INTEGER");
        dataTypes.put(ADQLDataType.ADQL_BIGINT, "BIGINT");
        dataTypes.put(ADQLDataType.ADQL_REAL, "REAL");
        dataTypes.put(ADQLDataType.ADQL_DOUBLE, "DOUBLE PRECISION");
        dataTypes.put(ADQLDataType.ADQL_CHAR, "CHAR");
        dataTypes.put(ADQLDataType.ADQL_VARCHAR, "VARCHAR");
        dataTypes.put(ADQLDataType.ADQL_TIMESTAMP, "TIMESTAMP");
        dataTypes.put(ADQLDataType.ADQL_CLOB, "VARCHAR");
        
        // HACK: this is temporary until codebase is refactored to allow for
        // easier customisation of these oplugins; for now we just list
        // the pg_sphere types explicitly... 
        
        // DOWNSIDE: if someone has postgresql and does not have pg_sphere an
        // uploaded table with a point or region will cause the create table
        // to fail, which looks like an internal error rather than an
        // unsupported operation
        dataTypes.put(ADQLDataType.ADQL_POINT, "spoint");
        dataTypes.put(ADQLDataType.ADQL_REGION, "spoly");
    }
    
    /**
     *
     */
    public PostgreSQLDataType() 
    {

    }

    /**
     * Given a ADQL data type, return the database
     * specific data type.
     *
     * @param columnDesc ADQL description of the column
     * @return database specific data type
     */
    public String getDataType(ColumnDesc columnDesc)
    {
        log.debug("getDataType: " + columnDesc);
        String dataType = dataTypes.get(columnDesc.datatype);
        if (dataType.equals("CHAR") || dataType.equals("VARCHAR"))
        {
            if (columnDesc.datatype.equals(ADQLDataType.ADQL_CLOB) || columnDesc.size == null)
	        dataType += "(4096)"; // HACK: arbitrary sensible limit
            else
            	dataType += "(" + columnDesc.size + ")";
        }
        return dataType;
    }

}
