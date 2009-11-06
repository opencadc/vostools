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

/**
 * 
 */
package ca.nrc.cadc.tap.parser.adql.validator;

import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

/**
 * Hold all necessary fields related to a "column" in the FROM part of a SELECT statement.
 * 
 * Including normal column and column from a sub-select.
 * 
 * @author zhangsa
 * 
 */
public class FromColumn
{

    private String _tableAlias;
    private String _schemaName;
    private String _tableName;
    private String _columnName;
    private String _columnAlias;

    public boolean matches(Column c1)
    {
        String c1Name = c1.getColumnName();
        if (c1Name == null || c1Name.equals(""))
            return false;

        boolean rtn = false;
        if (c1Name.equalsIgnoreCase(_columnAlias) || c1Name.equalsIgnoreCase(_columnName))
        {
            Table t1 = c1.getTable();
            if (t1 != null && t1.getName() != null && !t1.getName().equals(""))
            {
                String t1Name = t1.getName();
                String s1Name = t1.getSchemaName();
                if (t1Name.equalsIgnoreCase(_tableAlias) || t1Name.equalsIgnoreCase(_tableName))
                {
                    if (s1Name != null && !s1Name.equals(""))
                    {
                        if (s1Name.equalsIgnoreCase(_schemaName))
                            rtn = true;
                    } else
                        rtn = true;
                }
            } else
                rtn = true;
        }
        return rtn;
    }

    @Override
    public String toString()
    {
        return "FromColumn [columnAlias=" + _columnAlias + ", columnName=" + _columnName + ", schemaName=" + _schemaName
                + ", tableAlias=" + _tableAlias + ", tableName=" + _tableName + "]\r\n";
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((_columnAlias == null) ? 0 : _columnAlias.hashCode());
        result = prime * result + ((_columnName == null) ? 0 : _columnName.hashCode());
        result = prime * result + ((_schemaName == null) ? 0 : _schemaName.hashCode());
        result = prime * result + ((_tableAlias == null) ? 0 : _tableAlias.hashCode());
        result = prime * result + ((_tableName == null) ? 0 : _tableName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FromColumn other = (FromColumn) obj;
        if (_columnAlias == null)
        {
            if (other._columnAlias != null)
                return false;
        } else if (!_columnAlias.equalsIgnoreCase(other._columnAlias))
            return false;
        if (_columnName == null)
        {
            if (other._columnName != null)
                return false;
        } else if (!_columnName.equalsIgnoreCase(other._columnName))
            return false;
        if (_schemaName == null)
        {
            if (other._schemaName != null)
                return false;
        } else if (!_schemaName.equalsIgnoreCase(other._schemaName))
            return false;
        if (_tableAlias == null)
        {
            if (other._tableAlias != null)
                return false;
        } else if (!_tableAlias.equalsIgnoreCase(other._tableAlias))
            return false;
        if (_tableName == null)
        {
            if (other._tableName != null)
                return false;
        } else if (!_tableName.equalsIgnoreCase(other._tableName))
            return false;
        return true;
    }

    public final String getTableAlias()
    {
        return _tableAlias;
    }

    public final void setTableAlias(String tableAlias)
    {
        this._tableAlias = tableAlias;
    }

    public final String getSchemaName()
    {
        return _schemaName;
    }

    public final void setSchemaName(String schemaName)
    {
        this._schemaName = schemaName;
    }

    public final String getTableName()
    {
        return _tableName;
    }

    public final void setTableName(String tableName)
    {
        this._tableName = tableName;
    }

    public final String getColumnName()
    {
        return _columnName;
    }

    public final void setColumnName(String columnName)
    {
        this._columnName = columnName;
    }

    public final String getColumnAlias()
    {
        return _columnAlias;
    }

    public final void setColumnAlias(String columnAlias)
    {
        this._columnAlias = columnAlias;
    }

    public FromColumn(String tableAlias, String schemaName, String tableName, String columnName, String columnAlias)
    {
        super();
        this._tableAlias = tableAlias;
        this._schemaName = schemaName;
        this._tableName = tableName;
        this._columnName = columnName;
        this._columnAlias = columnAlias;
    }

    /**
     * @return
     */
    public String getTableQualifiedName()
    {
        StringBuffer sb = new StringBuffer();
        if (_schemaName != null)
            sb.append(_schemaName).append(".");
        sb.append(_tableName);
        return sb.toString();
    }

}
