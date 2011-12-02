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

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import ca.nrc.cadc.tap.parser.navigator.ExpressionNavigator;
import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.tap.parser.schema.TapSchemaUtil;
import ca.nrc.cadc.tap.schema.ColumnDesc;
import ca.nrc.cadc.tap.schema.FunctionDesc;
import ca.nrc.cadc.tap.schema.ParamDesc;
import ca.nrc.cadc.tap.schema.TapSchemaDAO;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;

/**
 * Extract a list of TapSelectItem from query.
 * 
 * @author zhangsa
 *
 */
public class SelectListExpressionExtractor extends ExpressionNavigator
{
    protected TapSchema tapSchema;
    protected List<ParamDesc> selectList;

    /**
     * @param tapSchema
     */
    public SelectListExpressionExtractor(TapSchema tapSchema)
    {
        super();
        this.tapSchema = tapSchema;
        this.selectList = new ArrayList<ParamDesc>();
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.statement.select.SelectItemVisitor#visit(net.sf.jsqlparser.statement.select.AllColumns)
     */
    @Override
    public void visit(AllColumns allColumns)
    {
        throw new UnsupportedOperationException("AllColumns must have previously been visited");
//        PlainSelect ps = selectNavigator.getPlainSelect();
//        List<Table> fromTableList = ParserUtil.getFromTableList(ps);
//
//        try
//        {
//            List<TapSelectItem> tableTsiList;
//            for (Table table : fromTableList)
//            {
//                tableTsiList = TapSchemaUtil.getTapSelectItemList(this.tapSchema, table);
//                this.tapSelectItemList.addAll(tableTsiList);
//            }
//        }
//        catch (TapParserException ex)
//        {
//            throw new UnsupportedOperationException(ex);
//        }
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.statement.select.SelectItemVisitor#visit(net.sf.jsqlparser.statement.select.AllTableColumns)
     */
    @Override
    public void visit(AllTableColumns allTableColumns)
    {
        throw new UnsupportedOperationException("AllTableColumns must have previously been visited");
//        PlainSelect ps = selectNavigator.getPlainSelect();
//        String tableNameOrAlias = allTableColumns.getTable().getName();
//        Table table = ParserUtil.findFromTable(ps, tableNameOrAlias);
//        log.debug(table);
//
//        try
//        {
//            List<TapSelectItem> tableTsiList = TapSchemaUtil.getTapSelectItemList(this.tapSchema, table);
//            this.tapSelectItemList.addAll(tableTsiList);
//        }
//        catch (TapParserException ex)
//        {
//            throw new UnsupportedOperationException(ex);
//        }
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.statement.select.SelectItemVisitor#visit(net.sf.jsqlparser.statement.select.SelectExpressionItem)
     */
    @Override
    public void visit(SelectExpressionItem selectExpressionItem)
    {
        log.debug("visit(selectExpressionItem)" + selectExpressionItem);
        
        ParamDesc paramDesc = null;
        PlainSelect plainSelect = selectNavigator.getPlainSelect();
        String alias = selectExpressionItem.getAlias();

        Expression expression = selectExpressionItem.getExpression();
        if (expression instanceof Column)
        {
            Column column = (Column) expression;
            log.debug("visit(column) " + column);

            ColumnDesc columnDesc = TapSchemaUtil.findColumnDesc(tapSchema, plainSelect, column);
            paramDesc = new ParamDesc(columnDesc, alias);
        }
        else if (expression instanceof Function)
        {
            Function function = (Function) expression;
            log.debug("visit(function) " + function);

            FunctionDesc functionDesc = getFunctionDesc(function, plainSelect);
            paramDesc = new ParamDesc(functionDesc, alias);
        }
        else
        {
            String datatype = getDatatypeFromExpression(expression);
            if (alias == null || alias.isEmpty())
                paramDesc = new ParamDesc(expression.toString(), expression.toString(), datatype);
            else
                paramDesc = new ParamDesc(expression.toString(), alias, datatype);
        }
        selectList.add(paramDesc);
    }

    public List<ParamDesc> getSelectList()
    {
        return selectList;
    }

    public void setSelectList(List<ParamDesc> selectList)
    {
        this.selectList = selectList;
    }

    public TapSchema getTapSchema()
    {
        return this.tapSchema;
    }

    public void setTapSchema(TapSchema tapSchema)
    {
        this.tapSchema = tapSchema;
    }

    private FunctionDesc getFunctionDesc(Function function, PlainSelect plainSelect)
    {
        FunctionDesc functionDesc = TapSchemaUtil.findFunctionDesc(tapSchema, function);
        if (functionDesc.datatype.equals(TapSchemaDAO.ARGUMENT_DATATYPE))
        {
            String datatype = null;
            ExpressionList parameters = function.getParameters();
            for (Object parameter : parameters.getExpressions())
            {
                if (parameter instanceof Column)
                {
                    ColumnDesc columnDesc = TapSchemaUtil.findColumnDesc(tapSchema, plainSelect, (Column) parameter);
                    if (columnDesc != null)
                        datatype = columnDesc.datatype;
                }
                else if (parameter instanceof Function)
                {
                    Function nestedFunction = (Function) parameter;
                    log.debug("vist(nested Function " + nestedFunction);
                    FunctionDesc nestedFunctionDesc = TapSchemaUtil.findFunctionDesc(tapSchema, (Function) parameter);
                    if (nestedFunctionDesc.datatype.equals(TapSchemaDAO.ARGUMENT_DATATYPE))
                    {
                        FunctionDesc recursiveFunctionDesc = getFunctionDesc(nestedFunction, plainSelect);
                        if (recursiveFunctionDesc != null)
                            datatype = recursiveFunctionDesc.datatype;
                    }
                    else
                    {
                        datatype = nestedFunctionDesc.datatype;
                    }
                }
                else
                {
                    datatype = getDatatypeFromExpression((Expression) parameter);
                }

                if (datatype != null)
                {
                    functionDesc.datatype = datatype;
                    break;
                }
            }
        }
        return functionDesc;
    }

    private String getDatatypeFromExpression(Expression expression)
    {
        return "adql:VARCHAR";
    }
    
}
