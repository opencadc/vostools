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

import org.apache.log4j.Logger;

import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.PlainSelect;
import ca.nrc.cadc.tap.parser.ParserUtil;
import ca.nrc.cadc.tap.parser.adql.TapSelectItem;
import ca.nrc.cadc.tap.parser.exception.TapParserException;
import ca.nrc.cadc.tap.parser.navigator.SelectNavigator;

/**
 * @author zhangsa
 *
 */
public class TapSchemaUtil
{
    protected static Logger log = Logger.getLogger(TapSchemaUtil.class);

    public static List<TapSelectItem> getTapSelectItemList(TapSchema tapSchema, Table table) throws TapParserException
    {
        TableDesc tableDesc = findTableDesc(tapSchema, table);
        if (tableDesc != null)
            return getTapSelectItemList(tableDesc);
        else
            throw new TapParserException("Table: [" + table + "] does not exist.");
    }
    
    public static List<TapSelectItem> getTapSelectItemList(TableDesc tableDesc)
    {
        List<TapSelectItem> tsiList = new ArrayList<TapSelectItem>();
        TapSelectItem tsi = null;
        String tableName = tableDesc.getSimpleTableName();
        for (ColumnDesc cd : tableDesc.getColumnDescs())
        {
            tsi = new TapSelectItem(tableName, cd.getColumnName(), null);
            tsiList.add(tsi);
        }
        return tsiList;
    }

    public static TableDesc findTableDesc(TapSchema tapSchema, Table table)
    {
        TableDesc rtn = null;
        
        for (SchemaDesc sd : tapSchema.getSchemaDescs())
        {
            if (sd.getSchemaName().equalsIgnoreCase(table.getSchemaName()))
            {
                for (TableDesc td : sd.getTableDescs())
                {
                    if (td.getSimpleTableName().equalsIgnoreCase(table.getName()))
                    {
                        rtn = td;
                        break;
                    }
                }
            }
        }
        return rtn;
    }

    /**
     * @param tapSchema
     * @param column
     * @return
     */
    public static boolean isVaidColumn(TapSchema tapSchema, Column column)
    {
        boolean rtn = false;
        String cname = column.getColumnName();
        Table table = column.getTable();
        TableDesc td = findTableDesc(tapSchema, table);
        for (ColumnDesc cd : td.getColumnDescs())
        {
            if (cname.equalsIgnoreCase(cd.getColumnName())) {
                rtn = true;
                break;
            }
        }
        return rtn;
    }

    /**
     * @param tapSchema
     * @param plainSelect
     * @param columnName
     * @return
     * @throws TapParserException 
     */
    public static Table findTableForColumnName(TapSchema tapSchema, PlainSelect plainSelect, String columnName) throws TapParserException
    {
        TableDesc rtnTd = null;
        int matchCount = 0;
        TableDesc td;
        List<Table> fromTableList = ParserUtil.getFromTableList(plainSelect);
        for (Table fromTable : fromTableList)
        {
            td = findTableDesc(tapSchema, fromTable);
            if (td == null)
                throw new TapParserException("Table [" + fromTable + "] does not exist.");
            if (isValidColumnName(td, columnName)) 
            {
                matchCount++;
                rtnTd = td;
                continue;
            }
        }
        if (matchCount == 0)
            throw new TapParserException("Column [" + columnName + "] does not exist.");
        else if (matchCount > 1)
            throw new TapParserException("Column [" + columnName + "] is ambiguous.");
        
        Table rtn = getTable(rtnTd);
        return rtn;
    }

    /**
     * @param td
     * @param columnName
     * @return
     */
    private static boolean isValidColumnName(TableDesc td, String columnName)
    {
        boolean rtn = false;
        for (ColumnDesc cd : td.getColumnDescs())
        {
            if (columnName.equalsIgnoreCase(cd.getColumnName()))
            {
                rtn = true;
                break;
            }
        }
        return rtn;
    }

    /**
     * @param rtnTd
     * @return
     */
    private static Table getTable(TableDesc rtnTd)
    {
        
        Table rtn = null;
        if (rtnTd != null)
            rtn = new Table(rtnTd.getSchemaName(), rtnTd.getSimpleTableName());
        return rtn;
    }
}
