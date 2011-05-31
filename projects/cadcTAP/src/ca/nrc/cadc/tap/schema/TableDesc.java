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

import java.util.ArrayList;
import java.util.List;

/**
 * Descriptor Class to represent a TAP_SCHEMA.tables table.
 * 
 */
public class TableDesc
{
    /**
     * The schema this Table belongs to.
     */
    public String schemaName;

    /**
     * The fully qualified Table name.
     */
    public String tableName;

    /**
     * Describes the Table.
     */
    public String description;

    /**
     * The utype of the Table.
     */
    public String utype;

    /**
     * List of columns belonging to this Table.
     */
    public List<ColumnDesc> columnDescs = new ArrayList<ColumnDesc>();

    /**
     * List of keys where this table is the <em>fromTable</em>.
     */
    public List<KeyDesc> keyDescs = new ArrayList<KeyDesc>();

    /**
     * Default no-arg constructor.
     */
    public TableDesc() {}

    /**
     * Construct a Table using the specified parameters.
     *
     * @param schemaName The schema this Table belongs to.
     * @param tableName The fully qualified Table name.
     * @param description Describes the Table.
     * @param utype The utype of the Table.
     */
    public TableDesc(String schemaName, String tableName, String description, String utype)
    {
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.description = description;
        this.utype = utype;
    }

    /**
     * Removes the schema, if it exists, from the table name.
     * 
     * @return Unqualified table name.
     */
    public String getSimpleTableName()
    {
        String simpleName = tableName;
        if (tableName.startsWith(schemaName + "."))
            simpleName = tableName.substring(tableName.indexOf(".")+1);
        return simpleName;
    }

    /**
     * Setters and getters.
     *
     */
    public final String getSchemaName()
    {
        return schemaName;
    }

    public final void setSchemaName(String schemaName)
    {
        this.schemaName = schemaName;
    }

    public final String getTableName()
    {
        return tableName;
    }

    public final void setTableName(String tableName)
    {
        this.tableName = tableName;
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

    public final List<ColumnDesc> getColumnDescs()
    {
        return columnDescs;
    }

    public final void setColumnDescs(List<ColumnDesc> columnDescs)
    {
        this.columnDescs = columnDescs;
    }

    public List<KeyDesc> getKeyDescs()
    {
        return keyDescs;
    }

    public void setKeyDescs(List<KeyDesc> keyDescs)
    {
        this.keyDescs = keyDescs;
    }

    /**
     * @return String representation of the Table.
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Table[");
        sb.append(schemaName == null ? "" : schemaName).append(",");
        sb.append(tableName).append(",");
        sb.append(description == null ? "" : description).append(",");
        sb.append(utype == null ? "" : utype).append(",");
        sb.append("columns[");
        if (columnDescs != null) {
            for (ColumnDesc col : columnDescs)
                sb.append(col);
        }
        sb.append("]]");
        return sb.toString();
    }

}
