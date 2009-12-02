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

package ca.nrc.cadc.tap.parser.finder;

import java.util.Iterator;

import javax.swing.text.html.HTMLDocument.HTMLReader.ParagraphAction;

import org.apache.log4j.Logger;

import ca.nrc.cadc.tap.parser.navigator.ExpressionNavigator;
import ca.nrc.cadc.tap.parser.navigator.SelectNavigator.VisitingPart;

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
 * @author zhangsa
 *
 */
public class ExpressionFinder extends ExpressionNavigator
{
    protected static Logger log = Logger.getLogger(ExpressionFinder.class);

    public ExpressionFinder clone()
    {
        ExpressionFinder rtn = (ExpressionFinder) super.clone();
        return rtn;
    }

    public ExpressionFinder()
    {
        // TODO Auto-generated constructor stub
    }

    // by default, use methods in super class
    //
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

    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.InverseExpression)
     */
    @Override
    public void visit(InverseExpression inverseExpression)
    {
        super.visit(inverseExpression);
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
        super.visit(parenthesis);
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
    public void visit(Addition expr)
    {
        super.visit(expr);
        expr.getLeftExpression().accept(this);
        expr.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.arithmetic.Division)
     */
    @Override
    public void visit(Division expr)
    {
        super.visit(expr);
        expr.getLeftExpression().accept(this);
        expr.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.arithmetic.Multiplication)
     */
    @Override
    public void visit(Multiplication expr)
    {
        super.visit(expr);
        expr.getLeftExpression().accept(this);
        expr.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.arithmetic.Subtraction)
     */
    @Override
    public void visit(Subtraction expr)
    {
        super.visit(expr);
        expr.getLeftExpression().accept(this);
        expr.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.conditional.AndExpression)
     */
    @Override
    public void visit(AndExpression expr)
    {
        super.visit(expr);
        expr.getLeftExpression().accept(this);
        expr.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.conditional.OrExpression)
     */
    @Override
    public void visit(OrExpression expr)
    {
        super.visit(expr);
        expr.getLeftExpression().accept(this);
        expr.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.relational.Between)
     */
    @Override
    public void visit(Between expr)
    {
        super.visit(expr);
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
        super.visit(expr);
        expr.getLeftExpression().accept(this);
        expr.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.relational.GreaterThan)
     */
    @Override
    public void visit(GreaterThan expr)
    {
        super.visit(expr);
        expr.getLeftExpression().accept(this);
        expr.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals)
     */
    @Override
    public void visit(GreaterThanEquals expr)
    {
        super.visit(expr);
        expr.getLeftExpression().accept(this);
        expr.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.relational.InExpression)
     */
    @Override
    public void visit(InExpression expr)
    {
        super.visit(expr);
        expr.getLeftExpression().accept(this);
        expr.getItemsList().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.relational.IsNullExpression)
     */
    @Override
    public void visit(IsNullExpression expr)
    {
        super.visit(expr);
        expr.getLeftExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.relational.LikeExpression)
     */
    @Override
    public void visit(LikeExpression expr)
    {
        super.visit(expr);
        expr.getLeftExpression().accept(this);
        expr.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.relational.MinorThan)
     */
    @Override
    public void visit(MinorThan expr)
    {
        super.visit(expr);
        expr.getLeftExpression().accept(this);
        expr.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.relational.MinorThanEquals)
     */
    @Override
    public void visit(MinorThanEquals expr)
    {
        super.visit(expr);
        expr.getLeftExpression().accept(this);
        expr.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.relational.NotEqualsTo)
     */
    @Override
    public void visit(NotEqualsTo expr)
    {
        super.visit(expr);
        expr.getLeftExpression().accept(this);
        expr.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.schema.Column)
     */
    @Override
    public void visit(Column tableColumn)
    {
        tableColumn.accept(_selectNavigator.getReferenceNavigator());
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.statement.select.SubSelect)
     */
    @Override
    public void visit(SubSelect subSelect)
    {
        super.visit(subSelect);
        this._selectNavigator.getFromItemNavigator().visit(subSelect);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.CaseExpression)
     */
    @Override
    public void visit(CaseExpression ce)
    {
        super.visit(ce);
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
        super.visit(expr);
        expr.getThenExpression().accept(this);
        expr.getWhenExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.operators.relational.ExistsExpression)
     */
    @Override
    public void visit(ExistsExpression expr)
    {
        super.visit(expr);
        expr.getRightExpression().accept(this);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.AllComparisonExpression)
     */
    @Override
    public void visit(AllComparisonExpression expr)
    {
        super.visit(expr);
        expr.GetSubSelect().accept(this._selectNavigator.getFromItemNavigator());
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.ExpressionVisitor#visit(net.sf.jsqlparser.expression.AnyComparisonExpression)
     */
    @Override
    public void visit(AnyComparisonExpression expr)
    {
        super.visit(expr);
        expr.GetSubSelect().accept(this._selectNavigator.getFromItemNavigator());
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.expression.operators.relational.ItemsListVisitor#visit(net.sf.jsqlparser.expression.operators.relational.ExpressionList)
     */
    @Override
    public void visit(ExpressionList el)
    {
        super.visit(el);
        Iterator i = el.getExpressions().iterator();
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
        super.visit(allColumns);
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.statement.select.SelectItemVisitor#visit(net.sf.jsqlparser.statement.select.AllTableColumns)
     */
    @Override
    public void visit(AllTableColumns allTableColumns)
    {
        super.visit(allTableColumns);
        this._selectNavigator.getFromItemNavigator().visit(allTableColumns.getTable());
    }

    /* (non-Javadoc)
     * @see net.sf.jsqlparser.statement.select.SelectItemVisitor#visit(net.sf.jsqlparser.statement.select.SelectExpressionItem)
     */
    @Override
    public void visit(SelectExpressionItem sei)
    {
        super.visit(sei);
        sei.getExpression().accept(this);
    }

}
