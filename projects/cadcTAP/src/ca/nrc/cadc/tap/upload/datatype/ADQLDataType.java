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

import ca.nrc.cadc.tap.upload.VOTableParserException;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;

/**
 * Provides a mapping of VOTable data types to ADQL data types.
 * 
 * @author jburke
 */
public class ADQLDataType
{
    private static final Logger log = Logger.getLogger(ADQLDataType.class);

    public static final Integer MAX_WIDTH = 4096;
    
    public static final String SHORT = "SHORT";
    public static final String INT = "INT";
    public static final String LONG = "LONG";
    public static final String FLOAT = "FLOAT";
    public static final String DOUBLE = "DOUBLE";
    public static final String CHAR = "CHAR";
    
    public static final String ADQL_SMALLINT = "adql:SMALLINT";
    public static final String ADQL_INTEGER = "adql:INTEGER";
    public static final String ADQL_BIGINT = "adql:BIGINT";
    public static final String ADQL_REAL = "adql:REAL";
    public static final String ADQL_DOUBLE = "adql:DOUBLE";
    public static final String ADQL_CHAR = "adql:CHAR";
    public static final String ADQL_VARCHAR = "adql:VARCHAR";
    public static final String ADQL_TIMESTAMP = "adql:TIMESTAMP";
    public static final String ADQL_CLOB = "adql:CLOB";
    public static final String ADQL_POINT = "adql:POINT";
    public static final String ADQL_REGION = "adql:REGION";

    
    private static Map<String, String> adqlTypes;
    static
    {
        adqlTypes = new HashMap<String, String>();
        adqlTypes.put(SHORT, ADQL_SMALLINT);
        adqlTypes.put(INT, ADQL_INTEGER);
        adqlTypes.put(LONG, ADQL_BIGINT);
        adqlTypes.put(FLOAT, ADQL_REAL);
        adqlTypes.put(DOUBLE, ADQL_DOUBLE);

        adqlTypes.put(SHORT.toLowerCase(), ADQL_SMALLINT);
        adqlTypes.put(INT.toLowerCase(), ADQL_INTEGER);
        adqlTypes.put(LONG.toLowerCase(), ADQL_BIGINT);
        adqlTypes.put(FLOAT.toLowerCase(), ADQL_REAL);
        adqlTypes.put(DOUBLE.toLowerCase(), ADQL_DOUBLE);

        adqlTypes.put(ADQL_SMALLINT, ADQL_SMALLINT);
        adqlTypes.put(ADQL_INTEGER, ADQL_INTEGER);
        adqlTypes.put(ADQL_BIGINT, ADQL_BIGINT);
        adqlTypes.put(ADQL_REAL, ADQL_REAL);
        adqlTypes.put(ADQL_DOUBLE, ADQL_DOUBLE);
        adqlTypes.put(ADQL_CHAR, ADQL_CHAR);
        adqlTypes.put(ADQL_VARCHAR, ADQL_VARCHAR);
        adqlTypes.put(ADQL_TIMESTAMP, ADQL_TIMESTAMP);
        adqlTypes.put(ADQL_CLOB, ADQL_CLOB);
        adqlTypes.put(ADQL_POINT, ADQL_POINT);
        adqlTypes.put(ADQL_REGION, ADQL_REGION);
    }
    
    private static Map<String, Integer> sqlTypes;
    static
    {
        sqlTypes = new HashMap<String, Integer>();
        sqlTypes.put(ADQL_SMALLINT, Types.SMALLINT);
        sqlTypes.put(ADQL_INTEGER, Types.INTEGER);
        sqlTypes.put(ADQL_BIGINT, Types.BIGINT);
        sqlTypes.put(ADQL_REAL, Types.REAL);
        sqlTypes.put(ADQL_DOUBLE, Types.DOUBLE);
        sqlTypes.put(ADQL_CHAR, Types.CHAR);
        sqlTypes.put(ADQL_VARCHAR, Types.VARCHAR);
        sqlTypes.put(ADQL_TIMESTAMP, Types.TIMESTAMP);
    }

    public static class XType
    {
        public String xtype;
	public Integer size;
    }

    /**
     * Returns the ADQL data type for given a VOTable data type.
     * 
     * @param datatype the data type.
     * @param width the width of the data type.
     * @return ADQL data type for the VOTable data type.
     * @throws VOTableParserException
     */
    public static String getDataType(String datatype, String width)
        throws VOTableParserException
    {
        if (datatype == null)
            return null;

        // Get the ADQL data type from the datatype or xtype
        String adqlType = adqlTypes.get(datatype);

        // Handle char datatypes separately
        if (adqlType == null && datatype.equalsIgnoreCase(CHAR))
        {
            if (width == null || width.isEmpty())
            {
                adqlType = ADQL_VARCHAR;
            }
            else
            {
                if (width.indexOf("*") == -1)
                    adqlType = ADQL_CHAR;
                else
                    adqlType = ADQL_VARCHAR;
            }
        }
        if (adqlType == null)
            throw new VOTableParserException("unknown data type " + datatype);
	log.debug("getDataType: " + datatype + "," + width + " -> " + adqlType);
        return adqlType;
    }
    
    /**
     * Returns the java.sql.Types for the given ADQL data type.
     * 
     * @param datatype ADQL data type
     * @return java.sql.Types value
     */
    public static Integer getSQLType(String datatype)
    {
        return sqlTypes.get(datatype);
    }
    
    /**
     * Returns an Integer for a given width. If no width is given, if the
     * width is empty, or if the width is *, an arbitrary fixed width is
     * returned.
     * 
     * @param width the width of the data type.
     * @return Integer value of the width.
     */
    public static Integer getWidth(String width)
    {
        Integer size = null;
        if (width == null)
            size = null;
	else if (width.trim().length() == 0)
	    size = null;
	else if (width.equals("*"))
	    size = Integer.MAX_VALUE;
	else if (width.endsWith("*"))
            size = Integer.parseInt(width.substring(0, width.lastIndexOf("*")));
        else
            size = Integer.parseInt(width);
        
	if (size != null && size > MAX_WIDTH)
            size = MAX_WIDTH;
	log.debug("getWidth: " + width + " -> " + size);
        return size;
    }
    
}
