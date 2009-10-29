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

package ca.nrc.cadc.tap.parser.adql.impl.postgresql.pgsphere.validator;

import java.util.Iterator;
import java.util.List;

import net.sf.jsqlparser.expression.AllComparisonExpression;
import net.sf.jsqlparser.expression.AnyComparisonExpression;
import net.sf.jsqlparser.expression.CaseExpression;
import net.sf.jsqlparser.expression.DateValue;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
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
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.SubSelect;
import ca.nrc.cadc.tap.parser.adql.AdqlUtil;
import ca.nrc.cadc.tap.parser.adql.config.meta.FunctionMeta;
import ca.nrc.cadc.tap.parser.adql.exception.AdqlValidateException;
import ca.nrc.cadc.tap.parser.adql.validator.ExpressionValidator;

/**
 * 
 * @author pdowler, Sailor Zhang
 */
public class ExpressionValidatorImpl extends ExpressionValidator
{
    /**
     * Visit a list of expressions. The implementation logs ther visit and then visits each expression (itself).
     * 
     * @param el
     */
    public void visit(ExpressionList el)
    {
        log.debug("visit(ExpressionList)");
        Iterator i = el.getExpressions().iterator();
        while (i.hasNext())
        {
            Expression e = (Expression) i.next();
            e.accept(this);
        }
    }

    /**
     * Visit a sub-select that is an item-list. Such a subselect is typically found in the where clause (e.g. used with the IN or
     * EXISTS operator). The implementation logs the visit at debug mode and visits the body of the sub-select (itself) using one of
     * the above SelectVisitor methods.
     * 
     * @param t
     */
    public void visit(SubSelect ss)
    {
        log.debug("visit(SubSelect): " + ss);
        this.selectValidator.visit(ss);
        // ss.getSelectBody().accept(this.selectValidator);
    }

    public void visit(ExistsExpression ee)
    {
        log.debug("visit(ExistsExpression): " + ee);
        ee.getRightExpression().accept(this);
    }

    public void visit(InExpression ie)
    {
        log.debug("visit(InExpression): " + ie);
        ie.getLeftExpression().accept(this);
        ie.getItemsList().accept(this);
    }

    // binary expressions: just check each side
    public void visit(EqualsTo op)
    {
        log.debug("visit(EqualsTo): " + op);
        op.getLeftExpression().accept(this);
        op.getRightExpression().accept(this);
    }

    public void visit(NotEqualsTo op)
    {
        log.debug("visit(NotEqualsTo): " + op);
        op.getLeftExpression().accept(this);
        op.getRightExpression().accept(this);
    }

    public void visit(Addition op)
    {
        log.debug("visit(Addition): " + op);
        op.getLeftExpression().accept(this);
        op.getRightExpression().accept(this);
    }

    public void visit(Division op)
    {
        log.debug("visit(Division): " + op);
        op.getLeftExpression().accept(this);
        op.getRightExpression().accept(this);
    }

    public void visit(Multiplication op)
    {
        log.debug("visit(Multiplication): " + op);
        op.getLeftExpression().accept(this);
        op.getRightExpression().accept(this);
    }

    public void visit(Subtraction op)
    {
        log.debug("visit(Subtraction): " + op);
        op.getLeftExpression().accept(this);
        op.getRightExpression().accept(this);
    }

    public void visit(AndExpression op)
    {
        log.debug("visit(AndExpression): " + op);
        op.getLeftExpression().accept(this);
        op.getRightExpression().accept(this);
    }

    public void visit(OrExpression op)
    {
        log.debug("visit(OrExpression): " + op);
        op.getLeftExpression().accept(this);
        op.getRightExpression().accept(this);
    }

    public void visit(GreaterThan op)
    {
        log.debug("visit(GreaterThan): " + op);
        op.getLeftExpression().accept(this);
        op.getRightExpression().accept(this);
    }

    public void visit(GreaterThanEquals op)
    {
        log.debug("visit(GreaterThanEquals): " + op);
        op.getLeftExpression().accept(this);
        op.getRightExpression().accept(this);
    }

    public void visit(LikeExpression op)
    {
        log.debug("visit(LikeExpression): " + op);
        op.getLeftExpression().accept(this);
        op.getRightExpression().accept(this);
    }

    public void visit(MinorThan op)
    {
        log.debug("visit(MinorThan): " + op);
        op.getLeftExpression().accept(this);
        op.getRightExpression().accept(this);
    }

    public void visit(MinorThanEquals op)
    {
        log.debug("visit(MinorThanEquals): " + op);
        op.getLeftExpression().accept(this);
        op.getRightExpression().accept(this);
    }

    public void visit(Between b)
    {
        log.debug("visit(Between): " + b);
        b.getLeftExpression().accept(this);
        b.getBetweenExpressionStart().accept(this);
        b.getBetweenExpressionEnd().accept(this);
    }

    public void visit(IsNullExpression op)
    {
        log.debug("visit(IsNullExpression): " + op);
        op.getLeftExpression().accept(this);
    }

    public void visit(Function f)
    {
        log.debug("visit(Function): " + f);
        String name = f.getName();
        List<FunctionMeta> functionMetas = config.getFunctionMetas();
        FunctionMeta functionMeta = new FunctionMeta(name);
        if (functionMetas.contains(functionMeta))
        {
            if (f.getParameters() != null)
                f.getParameters().accept(this);
        } else
        {
            log.debug(functionMetas);
            addException(new AdqlValidateException(name + " is not a supported function."));
        }
        return;
    }

    public void visit(InverseExpression ie)
    {
        log.debug("visit(InverseExpression): " + ie);
        ie.getExpression().accept(this);
    }

    // values
    public void visit(JdbcParameter jp)
    {
        log.debug("visit(JdbcParameter): " + jp);
    }

    public void visit(NullValue n)
    {
        log.debug("visit(NullValue): " + n);
    }

    public void visit(DoubleValue d)
    {
        log.debug("visit(DoubleValue): " + d);
    }

    public void visit(LongValue i)
    {
        log.debug("visit(LongValue): " + i);
    }

    public void visit(DateValue d)
    {
        log.debug("visit(DateValue): " + d);
    }

    public void visit(TimeValue t)
    {
        log.debug("visit(TimeValue): " + t);
    }

    public void visit(TimestampValue t)
    {
        log.debug("visit(TimestampValue): " + t);
    }

    public void visit(Parenthesis p)
    {
        log.debug("visit(Parenthesis): " + p);
        p.getExpression().accept(this);
    }

    public void visit(StringValue s)
    {
        log.debug("visit(StringValue): " + s);
    }

    // see also AdqlColumnReferenceVisitor -pat
    /**
     * Validate: 1, column is valid 2, column is not ambiguous
     */
    public void visit(Column c)
    {
        log.debug("visit(Column): " + AdqlUtil.detailOf(c));
        try
        {
            this.validateColumn(c);
        } catch (AdqlValidateException ex)
        {
            addException(ex);
        }
    }

    public void visit(CaseExpression ce)
    {
        log.debug("visit(CaseExpression): " + ce);
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

    public void visit(WhenClause wc)
    {
        log.debug("visit(WhenClause): " + wc);
        if (wc.getWhenExpression() != null)
            wc.getWhenExpression().accept(this);
        if (wc.getThenExpression() != null)
            wc.getThenExpression().accept(this);
    }

    public void visit(AllComparisonExpression ace)
    {
        log.debug("visit(AllComparisonExpression): " + ace);
        if (ace.GetSubSelect() != null && ace.GetSubSelect().getSelectBody() != null)
            ace.GetSubSelect().getSelectBody().accept(this.selectValidator);
    }

    public void visit(AnyComparisonExpression ace)
    {
        log.debug("visit(AnyComparisonExpression): " + ace);
        if (ace.GetSubSelect() != null && ace.GetSubSelect().getSelectBody() != null)
            ace.GetSubSelect().getSelectBody().accept(this.selectValidator);
    }
}