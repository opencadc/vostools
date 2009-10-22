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

/**
 * Class to represent a TAP_SCHEMA.columns table.
 * 
 */
public class Column
{
    /**
     * The table this column belongs to (not null).
     */
    public String tableName;

    /**
     * The Column name (not null).
     */
    public String columnName;

    /**
     * Describes the Column (can be null).
     */
    public String description;

    /**
     * The utype of the Column (can be null).
     */
    public String utype;

    /**
     * The UCD of Column (can be null).
     */
    public String ucd;

    /**
     * The unit used for Column values (can be null).
     */
    public String unit;

    /**
     * The ADQL datatype of Column (not null).
     */
    public String datatype;

    /**
     * The Column datatype size (Column width) (not null).
     */
    public Integer size;

    /**
     * Default no-arg constructor.
     */
    public Column()
    {
    }

    /**
     * Construct a Column using the specified parameters.
     * 
     * @param tableName The table this Column belongs to.
     * @param columnName The Column name.
     * @param description Describes the Column.
     * @param utype The utype of the Column.
     * @param ucd The UCD of Column.
     * @param unit The unit used for Column values.
     * @param datatype The ADQL datatype of Column.
     * @param size The Column datatype size (Column width).
     */
    public Column(String tableName,
                  String columnName,
                  String description,
                  String utype,
                  String ucd,
                  String unit,
                  String datatype,
                  Integer size)
    {
        this.tableName = tableName;
        this.columnName = columnName;
        this.description = description;
        this.utype = utype;
        this.ucd = ucd;
        this.unit = unit;
        this.datatype = datatype;
        this.size = size;
    }

    /**
     * Setters and getters.
     *
     */
    public final String getTableName()
    {
        return tableName;
    }

    public final void setTableName(String tableName)
    {
        this.tableName = tableName;
    }

    public final String getColumnName()
    {
        return columnName;
    }

    public final void setColumnName(String columnName)
    {
        this.columnName = columnName;
    }

    public final String getDescription()
    {
        return description;
    }

    public final void setDescription(String description)
    {
        this.description = description;
    }

    public final String getUtype()
    {
        return utype;
    }

    public final void setUtype(String utype)
    {
        this.utype = utype;
    }

    public final String getUcd()
    {
        return ucd;
    }

    public final void setUcd(String ucd)
    {
        this.ucd = ucd;
    }

    public final String getUnit()
    {
        return unit;
    }

    public final void setUnit(String unit)
    {
        this.unit = unit;
    }

    public final String getDatatype()
    {
        return datatype;
    }

    public final void setDatatype(String datatype)
    {
        this.datatype = datatype;
    }

    public final Integer getSize()
    {
        return size;
    }

    public final void setSize(Integer size)
    {
        this.size = size;
    }

    /**
     * @return String representation of the Column.
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Column[");
        sb.append(tableName).append(",");
        sb.append(columnName).append(",");
        sb.append(description == null ? "" : description).append(",");
        sb.append(utype == null ? "" : utype).append(",");
        sb.append(ucd == null ? "" : ucd).append(",");
        sb.append(unit == null ? "" : unit).append(",");
        sb.append(datatype).append(",");
        sb.append(size == null ? "" : size);
        sb.append("]");
        return sb.toString();
    }
}
