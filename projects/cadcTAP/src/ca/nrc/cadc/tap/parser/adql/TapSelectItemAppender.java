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

package ca.nrc.cadc.tap.parser.adql;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItemVisitor;
import ca.nrc.cadc.tap.parser.adql.validator.FromColumn;
import ca.nrc.cadc.tap.parser.adql.validator.PlainSelectInfo;

/**
 * @author zhangsa
 * 
 */
public class TapSelectItemAppender implements SelectItemVisitor
{
    PlainSelectInfo _plainSelectInfo;

    public TapSelectItemAppender(PlainSelectInfo psi)
    {
        _plainSelectInfo = psi;
    }

    /*
     * (non-Javadoc)
     * 
     * @see net.sf.jsqlparser.statement.select.SelectItemVisitor#visit(net.sf.jsqlparser.statement.select.AllColumns)
     */
    @Override
    public void visit(AllColumns allColumns)
    {
        TapSelectItem tapSelectItem = null;
        for (FromColumn fromColumn : _plainSelectInfo.getFromColumns())
        {
            tapSelectItem = new TapSelectItem(fromColumn);
            _plainSelectInfo.addTapSelectItem(tapSelectItem);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see net.sf.jsqlparser.statement.select.SelectItemVisitor#visit(net.sf.jsqlparser.statement.select.AllTableColumns)
     */
    @Override
    public void visit(AllTableColumns allTableColumns)
    {
        TapSelectItem tapSelectItem = null;

        Table table = allTableColumns.getTable();
        String tableName = table.getName();
        String schemaName = table.getSchemaName();

        for (FromColumn fromColumn : _plainSelectInfo.getFromColumns())
        {
            boolean toAdd = false;
            if (schemaName != null && !schemaName.equals(""))
            {
                // schema name presented in the allTableColumns. e.g. schemaA.tableA.*
                toAdd = tableName.equalsIgnoreCase(fromColumn.getTableName());
            } else
            {
                // No schema name presented in the allTableColumns. e.g. tableA.*, aliasA.*
                toAdd = tableName.equalsIgnoreCase(fromColumn.getTableAlias())
                        || tableName.equalsIgnoreCase(fromColumn.getTableName());
            }

            if (toAdd)
            {
                tapSelectItem = new TapSelectItem(fromColumn);
                _plainSelectInfo.addTapSelectItem(tapSelectItem);
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see net.sf.jsqlparser.statement.select.SelectItemVisitor#visit(net.sf.jsqlparser.statement.select.SelectExpressionItem)
     */
    @Override
    public void visit(SelectExpressionItem selectExpressionItem)
    {
        TapSelectItem tapSelectItem = null;

        String alias = selectExpressionItem.getAlias();
        String columnName = null;
        String tableName = null;
        Expression expression = selectExpressionItem.getExpression();
        if (expression instanceof Column)
        {
            Column column = (Column) expression;
            columnName = column.getColumnName();
            Table table = column.getTable();
            String schemaName = table.getSchemaName();

            if (schemaName != null && !schemaName.equals(""))
            {
                // schema name presented in the column expression. e.g. schemaA.tableA.columnA
                tableName = schemaName + "." + table.getName();
                tapSelectItem = new TapSelectItem(tableName, columnName, alias);
            } else
            {
                // No schema name presented in the column expression. e.g. tableA.columnA, aliasA.columnA, columnB
                tableName = table.getName();
                if (tableName != null)
                {
                    // table name or alias presented. e.g. tableA.columnA, aliasA.columnA
                    for (FromColumn fromColumn : _plainSelectInfo.getFromColumns())
                    {
                        if (tableName.equalsIgnoreCase(fromColumn.getTableAlias())
                                || tableName.equalsIgnoreCase(fromColumn.getTableName()))
                        {
                            tapSelectItem = new TapSelectItem(fromColumn);
                            tapSelectItem.setAlias(alias);
                            break;
                        }
                    }
                } else
                {
                    // only column name is presented. e.g. columnB, columnAliasC (refer to column alias in subselect)
                    for (FromColumn fromColumn : _plainSelectInfo.getFromColumns())
                    {
                        if (columnName.equalsIgnoreCase(fromColumn.getColumnAlias())
                                || columnName.equalsIgnoreCase(fromColumn.getColumnName()))
                        {
                            tapSelectItem = new TapSelectItem(fromColumn);
                            tapSelectItem.setAlias(alias);
                            break;
                        }
                    }

                }
            }
        }
        _plainSelectInfo.addTapSelectItem(tapSelectItem);
    }

}
