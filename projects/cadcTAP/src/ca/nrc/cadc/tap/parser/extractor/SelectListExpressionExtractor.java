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

package ca.nrc.cadc.tap.parser.extractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import ca.nrc.cadc.tap.parser.ParserUtil;
import ca.nrc.cadc.tap.parser.TapSelectItem;
import ca.nrc.cadc.tap.parser.exception.TapParserException;
import ca.nrc.cadc.tap.parser.navigator.ExpressionNavigator;
import ca.nrc.cadc.tap.schema.TableDesc;
import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.tap.parser.schema.TapSchemaUtil;

/**
 * re-engineered version on 2009-12-01
 * 
 * @author zhangsa
 *
 */
public class SelectListExpressionExtractor extends ExpressionNavigator
{
    protected List<TapSelectItem> _tapSelectItemList = new ArrayList<TapSelectItem>();
    protected TapSchema _tapSchema;
    protected Map<String,TableDesc> _extraTablesMap;

    /**
     * @param tapSchema
     * @param extraTablesMap
     */
    public SelectListExpressionExtractor(TapSchema tapSchema, Map<String, TableDesc> extraTablesMap)
    {
        super();
        _tapSchema = tapSchema;
        _extraTablesMap = extraTablesMap;
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.statement.select.SelectItemVisitor#visit(net.sf.jsqlparser.statement.select.AllColumns)
     */
    @Override
    public void visit(AllColumns allColumns)
    {
        PlainSelect ps = _selectNavigator.getPlainSelect();
        List<Table> fromTableList = ParserUtil.getFromTableList(ps);

        try
        {
            List<TapSelectItem> tableTsiList;
            for (Table table : fromTableList)
            {
                tableTsiList = TapSchemaUtil.getTapSelectItemList(_tapSchema, table);
                _tapSelectItemList.addAll(tableTsiList);
            }
        } catch (TapParserException ex)
        {
            throw new UnsupportedOperationException(ex);
        }
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.statement.select.SelectItemVisitor#visit(net.sf.jsqlparser.statement.select.AllTableColumns)
     */
    @Override
    public void visit(AllTableColumns allTableColumns)
    {
        PlainSelect ps = _selectNavigator.getPlainSelect();
        String tableNameOrAlias = allTableColumns.getTable().getName();
        Table table = ParserUtil.findFromTable(ps, tableNameOrAlias);
        log.debug(table);

        try
        {
            List<TapSelectItem> tableTsiList = TapSchemaUtil.getTapSelectItemList(_tapSchema, table);
            _tapSelectItemList.addAll(tableTsiList);
        } catch (TapParserException ex)
        {
            throw new UnsupportedOperationException(ex);
        }
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.statement.select.SelectItemVisitor#visit(net.sf.jsqlparser.statement.select.SelectExpressionItem)
     */
    @Override
    public void visit(SelectExpressionItem selectExpressionItem)
    {
        log.debug("visit(selectExpressionItem)" + selectExpressionItem);
        String alias = selectExpressionItem.getAlias();
        String columnName = null;
        String schemaAndTableName = null;

        TapSelectItem tapSelectItem = null;

        try
        {
            Expression expression = selectExpressionItem.getExpression();
            if (expression instanceof Column)
            {
                Column column0 = (Column) expression;
                Column column2 = new Column(column0.getTable(), column0.getColumnName());

                columnName = column2.getColumnName();
                Table table = column2.getTable();
                String schemaName = table.getSchemaName();

                if (schemaName != null && !schemaName.equals(""))
                {
                    // schema name presented in the column expression. e.g. schemaA.tableA.columnA
                    schemaAndTableName = schemaName + "." + table.getName();
                    tapSelectItem = new TapSelectItem(schemaAndTableName, columnName, alias);
                } else
                {
                    // No schema name presented in the column expression. 
                    // e.g. tableA.columnA, aliasA.columnA, columnB
                    String tableNameOrAlias = table.getName();
                    if (tableNameOrAlias != null)
                    {
                        // table name or alias presented. e.g. tableA.columnA, aliasA.columnA
                        Table fromTable = ParserUtil.findFromTable(_selectNavigator.getPlainSelect(), tableNameOrAlias);
                        if (fromTable != null)
                        {
                            column2.setTable(fromTable);
                            if (TapSchemaUtil.isValidColumn(_tapSchema, column2))
                            {
                                schemaAndTableName = fromTable.getSchemaName() + "." + fromTable.getName();
                                tapSelectItem = new TapSelectItem(schemaAndTableName, columnName, alias); // all valid
                            }
                            else
                                throw new TapParserException("Column [ " + columnName + " ] does not exist.");
                        } else
                            throw new TapParserException("Table name/alias: [ " + tableNameOrAlias + " ] does not exist.");
                    } else
                    {
                        // only column name is presented. e.g. columnB, 
                        // as sub-select is not supported, does not consider columnAliasC (refer to column alias in subselect)
                        table = TapSchemaUtil.findTableForColumnName(_tapSchema, _selectNavigator.getPlainSelect(), columnName);
                        if (table != null)
                        {
                            schemaAndTableName = table.getSchemaName() + "." + table.getName();
                            tapSelectItem = new TapSelectItem(schemaAndTableName, columnName, alias);
                        }
                        else
                            throw new TapParserException("Column [ " + columnName + " ] does not exist.");
                    }
                }
            } else
            {
                if (alias != null && !alias.equals(""))
                    tapSelectItem = new TapSelectItem(alias);
                else
                    tapSelectItem = new TapSelectItem(expression.toString());
            }
            _tapSelectItemList.add(tapSelectItem);
        } catch (TapParserException ex)
        {
            throw new UnsupportedOperationException(ex);
        }
    }

    public List<TapSelectItem> getTapSelectItemList()
    {
        return _tapSelectItemList;
    }

    public void setTapSelectItemList(List<TapSelectItem> tapSelectItemList)
    {
        _tapSelectItemList = tapSelectItemList;
    }

    public TapSchema getTapSchema()
    {
        return _tapSchema;
    }

    public void setTapSchema(TapSchema tapSchema)
    {
        _tapSchema = tapSchema;
    }

    public Map<String, ca.nrc.cadc.tap.schema.TableDesc> getExtraTablesMap()
    {
        return _extraTablesMap;
    }

    public void setExtraTablesMap(Map<String, ca.nrc.cadc.tap.schema.TableDesc> extraTablesMap)
    {
        _extraTablesMap = extraTablesMap;
    }
}
