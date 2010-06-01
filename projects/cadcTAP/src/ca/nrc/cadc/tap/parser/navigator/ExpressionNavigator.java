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

package ca.nrc.cadc.tap.parser.navigator;

import java.util.Iterator;

import org.apache.log4j.Logger;

import net.sf.jsqlparser.expression.AllComparisonExpression;
import net.sf.jsqlparser.expression.AnyComparisonExpression;
import net.sf.jsqlparser.expression.CaseExpression;
import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitor;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.InverseExpression;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.TimeValue;
import net.sf.jsqlparser.expression.TimestampValue;
import net.sf.jsqlparser.expression.WhenClause;
import net.sf.jsqlparser.expression.operators.arithmetic.Addition;
import net.sf.jsqlparser.expression.operators.arithmetic.Division;
import net.sf.jsqlparser.expression.operators.arithmetic.Multiplication;
import net.sf.jsqlparser.expression.operators.arithmetic.Subtraction;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.ItemsListVisitor;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItemVisitor;
import net.sf.jsqlparser.statement.select.SubSelect;

/**
 * This is a super class for expression navigation.  
 * It implements 3 visitors:  ExpressionVisitor, ItemsListVisitor, and SelectItemVisitor.
 * 
 * 
 * @author zhangsa
 *
 */
public class ExpressionNavigator extends SubNavigator implements ExpressionVisitor, ItemsListVisitor, SelectItemVisitor
{
    protected static Logger log = Logger.getLogger(ExpressionNavigator.class);

    public ExpressionNavigator()
    {
    }

    public ExpressionNavigator clone()
    {
        ExpressionNavigator rtn = (ExpressionNavigator) super.clone();
        return rtn;
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.NullValue)
     */
    @Override
    public void visit(NullValue nullValue)
    {
        log.debug("visit(NullValue)" + nullValue);

    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.Function)
     */
    @Override
    public void visit(Function function)
    {
        log.debug("visit(function)" + function);
        if (function.getParameters() != null)
            function.getParameters().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.InverseExpression)
     */
    @Override
    public void visit(InverseExpression inverseExpression)
    {
        log.debug("visit(inverseExpression)" + inverseExpression);
        inverseExpression.getExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.JdbcParameter)
     */
    @Override
    public void visit(JdbcParameter jdbcParameter)
    {
        log.debug("visit(jdbcParameter)" + jdbcParameter);

    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.DoubleValue)
     */
    @Override
    public void visit(DoubleValue doubleValue)
    {
        log.debug("visit(doubleValue)" + doubleValue);

    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.LongValue)
     */
    @Override
    public void visit(LongValue longValue)
    {
        log.debug("visit(longValue)" + longValue);

    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.DateValue)
     */
    @Override
    public void visit(DateValue dateValue)
    {
        log.debug("visit(dateValue)" + dateValue);

    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.TimeValue)
     */
    @Override
    public void visit(TimeValue timeValue)
    {
        log.debug("visit(timeValue)" + timeValue);

    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.TimestampValue)
     */
    @Override
    public void visit(TimestampValue timestampValue)
    {
        log.debug("visit(timestampValue)" + timestampValue);

    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.Parenthesis)
     */
    @Override
    public void visit(Parenthesis parenthesis)
    {
        log.debug("visit(parenthesis)" + parenthesis);
        parenthesis.getExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.StringValue)
     */
    @Override
    public void visit(StringValue stringValue)
    {
        log.debug("visit(stringValue)" + stringValue);

    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.arithmetic.Addition)
     */
    @Override
    public void visit(Addition addition)
    {
        log.debug("visit(addition)" + addition);
        addition.getLeftExpression().accept(this);
        addition.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.arithmetic.Division)
     */
    @Override
    public void visit(Division division)
    {
        log.debug("visit(division)" + division);
        division.getLeftExpression().accept(this);
        division.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.arithmetic.Multiplication)
     */
    @Override
    public void visit(Multiplication multiplication)
    {
        log.debug("visit(multiplication)" + multiplication);
        multiplication.getLeftExpression().accept(this);
        multiplication.getRightExpression().accept(this);

    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.arithmetic.Subtraction)
     */
    @Override
    public void visit(Subtraction subtraction)
    {
        log.debug("visit(subtraction)" + subtraction);
        subtraction.getLeftExpression().accept(this);
        subtraction.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.conditional.AndExpression)
     */
    @Override
    public void visit(AndExpression andExpression)
    {
        log.debug("visit(andExpression)" + andExpression);
        andExpression.getLeftExpression().accept(this);
        andExpression.getRightExpression().accept(this);

    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.conditional.OrExpression)
     */
    @Override
    public void visit(OrExpression orExpression)
    {
        log.debug("visit(orExpression)" + orExpression);
        orExpression.getLeftExpression().accept(this);
        orExpression.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.relational.Between)
     */
    @Override
    public void visit(Between expr)
    {
        log.debug("visit(between)" + expr);
        expr.getLeftExpression().accept(this);
        expr.getBetweenExpressionStart().accept(this);
        expr.getBetweenExpressionEnd().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.relational.EqualsTo)
     */
    @Override
    public void visit(EqualsTo expr)
    {
        log.debug("visit(equalsTo)" + expr);
        expr.getLeftExpression().accept(this);
        expr.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.relational.GreaterThan)
     */
    @Override
    public void visit(GreaterThan expr)
    {
        log.debug("visit(greaterThan)" + expr);
        expr.getLeftExpression().accept(this);
        expr.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals)
     */
    @Override
    public void visit(GreaterThanEquals expr)
    {
        log.debug("visit(greaterThanEquals)" + expr);
        expr.getLeftExpression().accept(this);
        expr.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.relational.InExpression)
     */
    @Override
    public void visit(InExpression expr)
    {
        log.debug("visit(inExpression)" + expr);
        expr.getLeftExpression().accept(this);
        expr.getItemsList().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.relational.IsNullExpression)
     */
    @Override
    public void visit(IsNullExpression isNullExpression)
    {
        log.debug("visit(isNullExpression)" + isNullExpression);
        isNullExpression.getLeftExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.relational.LikeExpression)
     */
    @Override
    public void visit(LikeExpression expr)
    {
        log.debug("visit(likeExpression)" + expr);
        expr.getLeftExpression().accept(this);
        expr.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.relational.MinorThan)
     */
    @Override
    public void visit(MinorThan expr)
    {
        log.debug("visit(minorThan)" + expr);
        expr.getLeftExpression().accept(this);
        expr.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.relational.MinorThanEquals)
     */
    @Override
    public void visit(MinorThanEquals expr)
    {
        log.debug("visit(minorThanEquals)" + expr);
        expr.getLeftExpression().accept(this);
        expr.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.relational.NotEqualsTo)
     */
    @Override
    public void visit(NotEqualsTo expr)
    {
        log.debug("visit(notEqualsTo)" + expr);
        expr.getLeftExpression().accept(this);
        expr.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.schema.Column)
     */
    @Override
    public void visit(Column tableColumn)
    {
        tableColumn.accept(selectNavigator.getReferenceNavigator());
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.statement.select.SubSelect)
     */
    @Override
    public void visit(SubSelect subSelect)
    {
        log.debug("visit(subSelect)" + subSelect);
        this.selectNavigator.getFromItemNavigator().visit(subSelect);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.CaseExpression)
     */
    @Override
    public void visit(CaseExpression ce)
    {
        log.debug("visit(caseExpression)" + ce);
        // case
        if (ce.getSwitchExpression() != null)
            ce.getSwitchExpression().accept(this);
        // when
        if (ce.getWhenClauses() != null)
        {
            Iterator i = ce.getWhenClauses().iterator();
            while (i.hasNext())
            {
                WhenClause wc = (WhenClause) i.next();
                wc.accept(this);
            }
        }
        // else
        if (ce.getElseExpression() != null)
            ce.getElseExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.WhenClause)
     */
    @Override
    public void visit(WhenClause expr)
    {
        log.debug("visit(whenClause)" + expr);
        expr.getThenExpression().accept(this);
        expr.getWhenExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.relational.ExistsExpression)
     */
    @Override
    public void visit(ExistsExpression existsExpression)
    {
        log.debug("visit(existsExpression)" + existsExpression);
        existsExpression.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.AllComparisonExpression)
     */
    @Override
    public void visit(AllComparisonExpression allComparisonExpression)
    {
        log.debug("visit(allComparisonExpression)" + allComparisonExpression);
        allComparisonExpression.GetSubSelect().accept(this.selectNavigator.getFromItemNavigator());
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.AnyComparisonExpression)
     */
    @Override
    public void visit(AnyComparisonExpression anyComparisonExpression)
    {
        log.debug("visit(anyComparisonExpression)" + anyComparisonExpression);
        anyComparisonExpression.GetSubSelect().accept(this.selectNavigator.getFromItemNavigator());
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.operators.relational.ItemsListVisitor#visit(net.sf.jsqlparser.expression.operators.relational.ExpressionList)
     */
    @Override
    public void visit(ExpressionList expressionList)
    {
        log.debug("visit(expressionList)" + expressionList);
        Iterator i = expressionList.getExpressions().iterator();
        while (i.hasNext())
        {
            Expression e = (Expression) i.next();
            e.accept(this);
        }
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.statement.select.SelectItemVisitor#visit(net.sf.jsqlparser.statement.select.AllColumns)
     */
    @Override
    public void visit(AllColumns allColumns)
    {
        log.debug("visit(AllColumns " + allColumns + ")");
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.statement.select.SelectItemVisitor#visit(net.sf.jsqlparser.statement.select.AllTableColumns)
     */
    @Override
    public void visit(AllTableColumns allTableColumns)
    {
        log.debug("visit(allTableColumns)" + allTableColumns);
        this.selectNavigator.getFromItemNavigator().visit(allTableColumns.getTable());
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.statement.select.SelectItemVisitor#visit(net.sf.jsqlparser.statement.select.SelectExpressionItem)
     */
    @Override
    public void visit(SelectExpressionItem selectExpressionItem)
    {
        log.debug("visit(selectExpressionItem)" + selectExpressionItem);
        selectExpressionItem.getExpression().accept(this);
    }
}
