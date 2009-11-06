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

package ca.nrc.cadc.tap.parser.adql.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SubSelect;

import org.apache.log4j.Logger;

import ca.nrc.cadc.tap.parser.adql.AdqlUtil;
import ca.nrc.cadc.tap.parser.adql.TapSelectItemAppender;
import ca.nrc.cadc.tap.parser.adql.config.meta.ColumnMeta;
import ca.nrc.cadc.tap.parser.adql.config.meta.FunctionMeta;
import ca.nrc.cadc.tap.parser.adql.config.meta.TableMeta;
import ca.nrc.cadc.tap.parser.adql.exception.AdqlValidateException;
import ca.nrc.cadc.tap.parser.adql.validator.PlainSelectInfo;
import ca.nrc.cadc.tap.parser.adql.validator.SelectValidator;
import ca.nrc.cadc.tap.parser.adql.validator.SelectValidator.PlainSelectType;
import ca.nrc.cadc.tap.schema.TapSchema;

public abstract class AdqlConfig
{
    protected Logger log = Logger.getLogger(AdqlConfig.class);

    protected TapSchema _tapSchema;
    protected Map<String, ca.nrc.cadc.tap.schema.Table> _extraTables;

    protected String configName = "Default";
    protected List<FunctionMeta> functionMetas;
    protected List<TableMeta> tableMetas;

    protected boolean allowJoins; // Allow multiple tables in FROM clause
    // (including JOIN). Default: true.
    protected boolean allowUnion; // Allow UNION. Default: true.
    protected boolean allowGroupBy; // Allow GROUP BY. Default: true.
    protected boolean allowOrderBy; // Allow ORDER BY. Default: true.
    protected boolean allowLimit; // Allow LIMIT. Default: false (not an ADQL
    // construct)
    protected boolean allowTop; // Allow TOP. Default: true.
    protected boolean allowDistinct; // Allow DISTINCT. Default: true.
    protected boolean allowInto; // Allow SELECT INTO. Default: false (not an

    // ADQL construct)

    // protected boolean caseSensitive; // Whether column, table, and schema
    // names are case sensitive. -sz 2009-09-10

    public TableMeta findTableMeta(Table table)
    {
        TableMeta rtn = null;
        String schemaName = table.getSchemaName();
        String tableName = table.getName();
        for (TableMeta tm : this.tableMetas)
        {
            if (schemaName == null)
            {
                if (tm.getTableName().equals(tableName))
                {
                    rtn = tm;
                    break;
                }
            } else
            {
                if (tm.getSchemaName().equals(schemaName) && tm.getTableName().equals(tableName))
                {
                    rtn = tm;
                    break;
                }
            }
        }
        return rtn;
    }

    // Return a list of SelectExpressionItem's that contains the MeatORM columns
    // for the specified table.
    public List<SelectItem> getAllSelectItemsForTable(Table table)
    {
        TableMeta tableMeta = this.findTableMeta(table);
        List<SelectItem> selectItems = tableMeta.getAllColumnAsSelectItems(table);
        return selectItems;
    }

    /**
     * Check whether a column is in a table. Prefix such as schema and table name are used in checking.
     * 
     * @param column
     * @param table
     * @return
     */
    public boolean isColumnInTable(Column column, Table table)
    {
        /*
         * scenarios:
         * 
         * column: schemaName.tableName.columnName tableName.columnName alias.columnName columnName
         * 
         * table: s.t t
         */

        boolean rtn = false;
        boolean proceed = false;

        String s1, t1, s0, t0, a0;

        t1 = column.getTable().getName();
        t0 = table.getName();
        if (t1 == null)
        {
            proceed = true;
        } else
        {
            s1 = column.getTable().getSchemaName();
            s0 = table.getSchemaName();
            a0 = table.getAlias();

            if (s1 != null)
            {
                if (s1.equals(s0) && t1.equals(t0))
                    proceed = true;
            } else
            {
                if (t1.equals(t0) || t1.equals(a0))
                    proceed = true;
            }
        }

        if (proceed)
        {
            String columnName = column.getColumnName();
            TableMeta tableMeta = findTableMeta(table);
            for (ColumnMeta columnMeta : tableMeta.getColumnMetas())
            {
                if (columnMeta.getName().equals(columnName))
                {
                    rtn = true;
                    break;
                }
            }
        }
        return rtn;
    }

    /**
     * Determine whether a column is valid, i.e. exists in the tables of fromItem/Joins
     * 
     * Does not check against sub-select in the from part
     * 
     * Currently not used by anybody.
     * 
     * @param column
     * @param ps
     * @return
     */
    public boolean isColumnValid(Column column, PlainSelect ps)
    {
        boolean rtn = false;
        List<Table> tables = AdqlUtil.extractSelectFromTables(ps);
        if (tables != null)
        {
            for (Table table : tables)
            {
                if (isColumnInTable(column, table))
                {
                    rtn = true;
                    break;
                }
            }
        }
        return rtn;
    }

    /**
     * Populate and Update contents of plainSelectInfo
     * 
     * @param plainSelectInfo
     * @param plainSelect
     * @throws AdqlValidateException
     */
    public void populatePlainSelectInfo(SelectValidator selectValidator, PlainSelect plainSelect) throws AdqlValidateException
    {
        PlainSelectInfo plainSelectInfo = selectValidator.getPlainSelectInfo();
        PlainSelectType type = selectValidator.getPlainSelectType();

        if (plainSelectInfo == null)
            plainSelectInfo = new PlainSelectInfo();
        FromItem fromItem;
        fromItem = plainSelect.getFromItem();
        if (fromItem instanceof Table)
            plainSelectInfo.addFromTable((Table) fromItem, this);
        else if (fromItem instanceof SubSelect)
            plainSelectInfo.addFromSubSelect((SubSelect) fromItem, this);

        List<Join> joins = plainSelect.getJoins();
        if (joins != null)
        {
            for (Join join : joins)
            {
                fromItem = join.getRightItem();
                if (fromItem instanceof Table)
                    plainSelectInfo.addFromTable((Table) fromItem, this);
                else if (fromItem instanceof SubSelect)
                    plainSelectInfo.addFromSubSelect((SubSelect) fromItem, this);
            }
        }

        // populate tapSelectItems

        // Only populate tapSelectItems if the plainSelect is at ROOT level.
        if (type == PlainSelectType.ROOT_SELECT)
        {
            TapSelectItemAppender tapSelectItemAppender = new TapSelectItemAppender(plainSelectInfo);
            List<SelectItem> selectItems = plainSelect.getSelectItems();
            for (SelectItem selectItem : selectItems)
            {
                selectItem.accept(tapSelectItemAppender);
            }
        }
        return;
    }

    /**
     * Determine whether a column is ambiguous. Does not check against sub-select in the from part.
     * 
     * Not used by anybody.
     * 
     * @param column
     * @param ps
     * @return
     */
    public boolean isColumnAmbiguous(Column column, PlainSelect ps)
    {
        boolean rtn = true;
        if (column.getTable().getSchemaName() != null) // full qualified name
            // presented
            rtn = false;
        else if (column.getTable().getName() != null)
        { // TABLE.COLUMN is used.
            String tableName = column.getTable().getName();
            int count = 0;

            List<Table> fromTables = AdqlUtil.extractSelectFromTables(ps);
            if (fromTables != null)
            {
                for (Table fromTable : fromTables)
                {
                    if (fromTable.getName().equals(tableName) && isColumnInTable(column, fromTable))
                        count++;
                }
            }
            rtn = (count > 1);
        } else
        { // No schema or table name is used as prefix
            int count = 0;

            List<Table> fromTables = AdqlUtil.extractSelectFromTables(ps);
            if (fromTables != null)
            {
                for (Table fromTable : fromTables)
                {
                    if (isColumnInTable(column, fromTable))
                        count++;
                }
            }
            rtn = (count > 1);
        }
        return rtn;
    }

    /**
     * If the same table name is used in two schemas, The invoker should always use full qualified name i.e. schemaName.tableName
     * 
     * @param table
     * @return
     */
    public boolean isTableAmbiguous(Table table)
    {
        boolean rtn = false;
        String schemaName = table.getSchemaName();
        String tableName = table.getName();
        boolean found = false;
        if (schemaName == null)
        {
            for (TableMeta tm : this.tableMetas)
            {
                if (tm.getTableName().equals(tableName))
                {
                    if (!found)
                        found = true;
                    else
                    { // already found, duplicated table name
                        rtn = true;
                        break;
                    }
                }
            }
        } else
            rtn = false;
        return rtn;
    }

    /**
     * Check whether table is in the List of TableMeta.
     * 
     * @param t
     * @return
     */
    public boolean isTableValid(Table table)
    {
        boolean rtn = false;
        String schemaName = table.getSchemaName();
        String tableName = table.getName();
        for (TableMeta tm : this.tableMetas)
        {
            if (tm.getTableName().equals(tableName))
            {
                if (schemaName == null || schemaName.equals(tm.getSchemaName()))
                {
                    rtn = true;
                    break;
                }
            }
        }
        return rtn;
    }

    /**
     * Constructor
     */
    public AdqlConfig()
    {
        this.functionMetas = new ArrayList<FunctionMeta>();
        this.tableMetas = new ArrayList<TableMeta>();
    }

    // Getters and Setters -------------------------------

    public final List<FunctionMeta> getFunctionMetas()
    {
        return functionMetas;
    }

    public final List<TableMeta> getTableMetas()
    {
        return tableMetas;
    }

    public final boolean isAllowJoins()
    {
        return allowJoins;
    }

    public final void setAllowJoins(boolean allowJoins)
    {
        this.allowJoins = allowJoins;
    }

    public final boolean isAllowUnion()
    {
        return allowUnion;
    }

    public final void setAllowUnion(boolean allowUnion)
    {
        this.allowUnion = allowUnion;
    }

    public final boolean isAllowGroupBy()
    {
        return allowGroupBy;
    }

    public final void setAllowGroupBy(boolean allowGroupBy)
    {
        this.allowGroupBy = allowGroupBy;
    }

    public final boolean isAllowOrderBy()
    {
        return allowOrderBy;
    }

    public final void setAllowOrderBy(boolean allowOrderBy)
    {
        this.allowOrderBy = allowOrderBy;
    }

    public final boolean isAllowLimit()
    {
        return allowLimit;
    }

    public final void setAllowLimit(boolean allowLimit)
    {
        this.allowLimit = allowLimit;
    }

    public final boolean isAllowTop()
    {
        return allowTop;
    }

    public final void setAllowTop(boolean allowTop)
    {
        this.allowTop = allowTop;
    }

    public final boolean isAllowDistinct()
    {
        return allowDistinct;
    }

    public final void setAllowDistinct(boolean allowDistinct)
    {
        this.allowDistinct = allowDistinct;
    }

    public final boolean isAllowInto()
    {
        return allowInto;
    }

    public final void setAllowInto(boolean allowInto)
    {
        this.allowInto = allowInto;
    }
}
