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

package ca.nrc.cadc.tap.parser.adql.impl.postgresql.pgsphere.converter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.InverseExpression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ItemsList;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SubJoin;
import net.sf.jsqlparser.statement.select.SubSelect;
import ca.nrc.cadc.tap.parser.adql.AdqlUtil;
import ca.nrc.cadc.tap.parser.adql.config.Constants;
import ca.nrc.cadc.tap.parser.adql.converter.RegionConverter;
import ca.nrc.cadc.tap.parser.adql.impl.postgresql.pgsphere.function.Spoint;

/**
 * A SelectVisitor that traverses the query to find and modify ADQL region constructs.
 * 
 * @author pdowler, Sailor Zhang
 */
public class RegionConverterImpl extends RegionConverter
{
    public void visit(SubSelect subSelect)
    {
        log.debug("visit(SubSelect): " + subSelect);
        subSelect.getSelectBody().accept(this);
    }

    public void visit(SubJoin subjoin)
    {
        log.debug("visit(SubJoin): " + subjoin);
        // SubJoin not supported 
    }

    public void visit(PlainSelect ps)
    {
        log.debug("visit(PlainSelect): " + ps);

        if (ps.getSelectItems() != null)
        {
            ListIterator i = ps.getSelectItems().listIterator();
            while (i.hasNext())
            {
                Object obj = i.next();
                if (obj instanceof SelectExpressionItem)
                {
                    SelectExpressionItem s = (SelectExpressionItem) obj;
                    Expression ex = s.getExpression();
                    Expression pgsExpression = ConverterUtil.convertToPgsphere(ex);
                    s.setExpression(pgsExpression);
                }
            }
        }

        ps.getFromItem().accept(this); // sz: left-most fromItem

        if (ps.getJoins() != null)
        {
            Iterator i = ps.getJoins().iterator();
            while (i.hasNext())
            {
                Join j = (Join) i.next();
                if (j.getOnExpression() != null)
                {
                    Expression e = j.getOnExpression();
                    Expression pgsExpression = ConverterUtil.convertToPgsphere(e);
                    log.debug("PlainSelect/JOIN...ON: replacing " + e + " with " + pgsExpression);
                    j.setOnExpression(pgsExpression);
                }
                if (j.getRightItem() != null)
                {
                    FromItem ri = j.getRightItem();
                    ri.accept(this);
                }
            }
        }

        if (ps.getWhere() != null)
        {
            Expression e = ps.getWhere();
            Expression pgsExpression = ConverterUtil.convertToPgsphere(e);
            log.debug("PlainSelect/WHERE: replacing " + e + " with " + pgsExpression);
            ps.setWhere(pgsExpression);
        }

        if (ps.getHaving() != null)
        {
            Expression e = ps.getHaving();
            Expression pgsExpression = ConverterUtil.convertToPgsphere(e);
            log.debug("PlainSelect/HAVING: replacing " + e + " with " + pgsExpression);
            ps.setHaving(pgsExpression);
        }

        log.debug("visit(PlainSelect) done");
    }

    private Expression checkExpression(Expression e)
    {
        log.debug("checkExpression: " + e);
        // BinaryExpression, InverseExpression, Parenthesis, SubSelect
        // all have child expressions
        if (e instanceof BinaryExpression)
        {
            return checkBinaryExpression((BinaryExpression) e);
        }
        if (e instanceof Between)
        {
            return checkBetweenExpression((Between) e);
        }
        if (e instanceof InverseExpression)
        {
            return checkInverse((InverseExpression) e);
        }
        if (e instanceof InExpression)
        {
            return checkInExpression((InExpression) e);
        }
        if (e instanceof InExpression)
        {
            return checkInExpression((InExpression) e);
        }
        if (e instanceof Parenthesis)
        {
            return checkParenthesis((Parenthesis) e);
        }
        if (e instanceof SubSelect)
        {
            checkSubSelect((SubSelect) e);
        }
        if (e instanceof Function)
        {
            return checkFunction((Function) e);
        }
        return null;
    }

    public Expression checkSubSelect(SubSelect e)
    {
        e.getSelectBody().accept(this);
        return null;
    }

    public Expression checkBinaryExpression(BinaryExpression e)
    {
        Expression ret = null;
        // here we check all the BEs where we expect/allow use of region predicates
        if (e instanceof EqualsTo)
            ret = checkEqualsTo((EqualsTo) e);
        if (ret != null)
            return ret;

        // recurse and change in place
        Expression lhs = e.getLeftExpression();
        Expression repL = checkExpression(lhs);
        if (repL != null)
        {
            log.debug("checkBinaryExpression: replacing " + lhs + " with " + repL);
            e.setLeftExpression(repL);
        }

        Expression rhs = e.getRightExpression();
        Expression repR = checkExpression(rhs);
        if (repR != null)
        {
            log.debug("checkBinaryExpression: replacing " + rhs + " with " + repR);
            e.setRightExpression(repR);
        }
        return null;
    }

    public Expression checkBetweenExpression(Between e)
    {
        Expression ret = null;
        Expression ex, re;

        // recurse and change in place
        ex = e.getLeftExpression();
        re = checkExpression(ex);
        if (re != null)
        {
            log.debug("checkBetweenExpression: replacing " + ex + " with " + re);
            e.setLeftExpression(re);
        }

        ex = e.getBetweenExpressionStart();
        re = checkExpression(ex);
        if (re != null)
        {
            log.debug("checkBetweenExpression: replacing " + ex + " with " + re);
            e.setBetweenExpressionStart(re);
        }

        ex = e.getBetweenExpressionEnd();
        re = checkExpression(ex);
        if (re != null)
        {
            log.debug("checkBetweenExpression: replacing " + ex + " with " + re);
            e.setBetweenExpressionEnd(re);
        }

        return ret;
    }

    public Expression checkInExpression(InExpression e)
    {
        Expression ex = e.getLeftExpression();
        Expression re = checkExpression(ex);
        if (re != null)
        {
            log.debug("checkBetweenExpression: replacing " + ex + " with " + re);
            e.setLeftExpression(re);
        }

        ItemsList ilist = e.getItemsList();
        if (ilist instanceof SubSelect)
            return checkExpression((Expression) ilist);

        ExpressionList elist = (ExpressionList) ilist;
        if (elist.getExpressions() != null)
        {
            Iterator i = elist.getExpressions().iterator();
            checkExpression((Expression) i.next());
        }

        return null;
    }

    public Expression checkInverse(InverseExpression e)
    {
        Expression rep = checkExpression(e.getExpression());
        if (rep != null)
        {
            log.debug("checkInverse: replacing " + e.getExpression() + " with " + rep);
            e.setExpression(rep);
        }
        return null;
    }

    public Expression checkParenthesis(Parenthesis e)
    {
        Expression rep = checkExpression(e.getExpression());
        if (rep != null)
        {
            log.debug("checkParenthesis: replacing " + e.getExpression() + " with " + rep);
            e.setExpression(rep);
        }
        return null;
    }

    // check for and handle the region predicate functions
    // currently limited to contains|intersects = 0|1
    public Expression checkEqualsTo(EqualsTo op)
    {
        Expression lhs = op.getLeftExpression();
        Expression rhs = op.getRightExpression();
        String fname = null;
        if (lhs instanceof Function)
        {
            Function func = (Function) lhs;
            if (Constants.REGION_PREDICATES.contains(func.getName().toUpperCase()))
            {
                fname = func.getName().toUpperCase();
            }
        }
        if (rhs instanceof Function)
        {
            Function func = (Function) lhs;
            if (Constants.REGION_PREDICATES.contains(func.getName().toUpperCase()))
            {
                fname = func.getName().toUpperCase();
            }
        }

        // only check further if one function is a region function
        if (fname != null)
        {
            Function lhsFunc = null;
            LongValue lhsConstant = null;
            if (op.getLeftExpression() instanceof Function)
            {
                lhsFunc = (Function) op.getLeftExpression();
            } else if (op.getLeftExpression() instanceof LongValue)
            {
                lhsConstant = (LongValue) op.getLeftExpression();
            }
            Function rhsFunc = null;
            LongValue rhsConstant = null;
            if (op.getRightExpression() instanceof Function)
            {
                rhsFunc = (Function) op.getRightExpression();
            } else if (op.getRightExpression() instanceof LongValue)
            {
                rhsConstant = (LongValue) op.getRightExpression();

                // sanity check: we can't really handle this kind of use
            }

            if (lhsFunc != null && rhsFunc != null)
            {
                throw new UnsupportedOperationException("multiple function use in = predicate");
            }
            Function func = lhsFunc;
            if (func == null)
            {
                func = rhsFunc;
            }
            LongValue constant = lhsConstant;
            if (constant == null)
            {
                constant = rhsConstant;
            }
            log.debug("EqualsTo: found " + func + " = " + constant);
            boolean pred = (constant.getValue() == 1);
            String predStr = "";
            if (!pred)
            {
                predStr = "NOT ";
            }
            if (Constants.CONTAINS.equals(fname))
            {
                // single argument
                List expr = func.getParameters().getExpressions();
                if (expr.size() != 2)
                    throw new IllegalArgumentException("CONTAINS: requires 2 region arguments");
                Expression ex1 = (Expression) expr.get(0);
                Expression ex2 = (Expression) expr.get(1);

                Expression ret = createContains(ex1, ex2, pred);

                log.debug("converting CONTAINS(" + ex1 + "," + ex2 + ") = " + constant + " -> " + ret);
                return ret;
            } else if (Constants.INTERSECTS.equals(fname))
            {
                // single argument
                List expr = func.getParameters().getExpressions();
                if (expr.size() != 2)
                    throw new IllegalArgumentException("INTERSECTS: requires 2 region arguments");
                Expression ex1 = (Expression) expr.get(0);
                Expression ex2 = (Expression) expr.get(1);

                Expression ret = createIntersects(ex1, ex2, pred);

                log.debug("converting INTERSECTS(" + ex1 + "," + ex2 + ") = " + constant + " -> " + ret);
                return ret;
            }
        }
        return null;
    }

    // handle function call in select list
    private Expression checkSelectedFunction(Function func)
    {
        if (Constants.REGION_FUNCTIONS.contains(func.getName().toUpperCase()))
            return checkRegionFunction(func);

        return null;
    }

    // handle function call outside select list
    private Expression checkFunction(Function function)
    {
        Expression rtn = null;
        if (AdqlUtil.isAdqlGeometricalFunction(function))
            rtn = checkRegionFunction(function);
        return rtn;
    }

    private Expression checkRegionFunction(Function function)
    {
        Expression rtn = null;
        String fname = function.getName().toUpperCase();

        if (Constants.POINT.equalsIgnoreCase(fname))
        {
            Spoint spoint = new Spoint(function);

            rtn = spoint;
        }
        return rtn;

        /*
         * TODO:sz 2009-10-28 if (Constants.COORDSYS.equals(fname)) { return doScalarRegionFunction(function, null, "ICRS"); } if
         * (Constants.AREA.equals(fname)) { return doScalarRegionFunction(function, "area", null); } if
         * (Constants.CVAL1.equals(fname)) { return doScalarRegionFunction(function, "cval1", null); } if
         * (Constants.CVAL2.equals(fname)) { return doScalarRegionFunction(function, "cval2", null); }
         * log.warn("checkRegionFunction did not handle " + function); return null; // actually a bug to get here
         */
    }

    // return the single function argument, throws an IllegalArgumentException otherwise
    private Expression getSingleArg(Function func)
    {
        if (func.getParameters() == null || func.getParameters().getExpressions() == null)
            throw new IllegalArgumentException(func.getName() + ": requires a region argument");

        List expr = func.getParameters().getExpressions();
        if (expr.size() != 1)
        {
            throw new IllegalArgumentException(func.getName() + ": requires a region argument");
        }
        return (Expression) expr.get(0); // the function arg
    }

    // handle region->scalar (REGION_FUNCTION): COORDSYS, CVAL1, CVAL2, AREA
    private Expression doScalarRegionFunction(Function func, String otherColumn, String defaultValue)
    {
        log.debug("doRegionFunction: " + func);
        if (!Constants.REGION_FUNCTIONS.contains(func.getName().toUpperCase()))
        {
            return null;
        }
        Expression ex = getSingleArg(func);

        if (ex instanceof Column)
        {
            if (defaultValue != null)
            {
                StringValue ret = new StringValue("'" + defaultValue + "'"); // need the tick marks
                log.debug("converting " + func + " -> " + ret);
                return ret;
            }
            if (otherColumn != null) // HACK: this should be a lookup in a map
            {
                Column c = (Column) ex;
                log.debug("c.getTable: " + c.getTable() + "  c.getColumnName: " + c.getColumnName());
                Column ret = new Column(c.getTable(), otherColumn);
                log.debug("converting " + func + " -> " + ret);
                return ret;
            }
            throw new RuntimeException("BUG: columnSuffix and defaultValue both null");
        }
        if (ex instanceof Function)
        {
            // eg. func( ex )
            // eg. COORDSYS( CIRCLE(...) )
            // eg. AREA( CIRCLE(...) )
            // eg. CVAL1( CENTROID(CIRCLE) )

            // eg. something like CVAL1( CENTROID(t.shape) ) is semi-useful in the
            // SELECT clause
            Function ff = (Function) ex;
            if (Constants.CENTROID.equals(ff.getName().toUpperCase()))
            {
                ex = getSingleArg(ff);

                if (ex instanceof Column)
                {
                    String fname = func.getName().toUpperCase(); // outer function
                    if (Constants.COORDSYS.equals(fname) && defaultValue != null)
                    {
                        StringValue ret = new StringValue("'" + defaultValue + "'"); // need the tick marks
                        log.debug("converting " + func + " -> " + ret);
                        return ret;
                    }
                    if (otherColumn != null) // HACK: this won't work at all, but CENTROID is disabled
                    {
                        otherColumn = otherColumn + "_center";
                        Column c = (Column) ex;
                        Column ret = new Column(c.getTable(), otherColumn);
                        log.debug("converting " + func + " -> " + ret);
                        return ret;
                    }
                    throw new RuntimeException("BUG: columnSuffix and defaultValue both null");
                }
            }

            // NOTE: this is mostly useless except for making the parser work hard
            throw new UnsupportedOperationException("using region function " + func.getName() + " with function args");
        }
        return null; // probably a bug to get here
    }

    private Expression createIntersects(Expression ex1, Expression ex2, boolean pred)
    {
        Expression[] a1 = null;
        Expression[] a2 = null;
        if (ex1 instanceof Column)
        {
            Column c = (Column) ex1;
            a1 = columnToColumns(c);
        } else if (ex1 instanceof Function)
        {
            Function f = (Function) ex1;
            a1 = functionToColumns(f);
        }
        // check if we groked it
        if (a1 == null)
            throw new IllegalArgumentException("createIntersects: illegal argument: " + ex1);

        if (ex2 instanceof Column)
        {
            Column c = (Column) ex2;
            a2 = columnToColumns(c);
        } else if (ex2 instanceof Function)
        {
            Function f = (Function) ex2;
            a2 = functionToColumns(f);
        }
        // check if we groked it
        if (a2 == null)
            throw new IllegalArgumentException("createIntersects: illegal argument: " + ex2);

        // check array lengths: 3 for point and 6 for cube
        if (a1.length == 3)
            a1 = new Expression[] { a1[0], a1[0], a1[1], a1[1], a1[2], a1[2] };
        if (a2.length == 3)
            a2 = new Expression[] { a2[0], a2[0], a2[1], a2[1], a2[2], a2[2] };

        if (pred) // true
        {
            BinaryExpression xlo = new MinorThanEquals();
            BinaryExpression xhi = new MinorThanEquals();
            BinaryExpression ylo = new MinorThanEquals();
            BinaryExpression yhi = new MinorThanEquals();
            BinaryExpression zlo = new MinorThanEquals();
            BinaryExpression zhi = new MinorThanEquals();

            xlo.setLeftExpression(a1[0]);
            xlo.setRightExpression(a2[1]);
            xhi.setLeftExpression(a2[0]);
            xhi.setRightExpression(a1[1]);
            Expression x = new AndExpression(xlo, xhi);

            ylo.setLeftExpression(a1[2]);
            ylo.setRightExpression(a2[3]);
            yhi.setLeftExpression(a2[2]);
            yhi.setRightExpression(a1[3]);
            Expression y = new AndExpression(ylo, yhi);

            zlo.setLeftExpression(a1[4]);
            zlo.setRightExpression(a2[5]);
            zhi.setLeftExpression(a2[4]);
            zhi.setRightExpression(a1[5]);
            Expression z = new AndExpression(zlo, zhi);

            Expression e = new AndExpression(x, y);
            e = new AndExpression(e, z);
            return e;
        }

        // not intersects()
        BinaryExpression xlo = new MinorThan();
        BinaryExpression xhi = new MinorThan();
        BinaryExpression ylo = new MinorThan();
        BinaryExpression yhi = new MinorThan();
        BinaryExpression zlo = new MinorThan();
        BinaryExpression zhi = new MinorThan();

        xlo.setLeftExpression(a2[1]);
        xlo.setRightExpression(a1[0]);
        xhi.setLeftExpression(a1[1]);
        xhi.setRightExpression(a2[0]);
        Expression x = new OrExpression(xlo, xhi);

        ylo.setLeftExpression(a2[3]);
        ylo.setRightExpression(a1[2]);
        yhi.setLeftExpression(a1[3]);
        yhi.setRightExpression(a2[2]);
        Expression y = new OrExpression(ylo, yhi);

        zlo.setLeftExpression(a2[5]);
        zlo.setRightExpression(a1[4]);
        zhi.setLeftExpression(a1[5]);
        zhi.setRightExpression(a2[4]);
        Expression z = new OrExpression(zlo, zhi);

        Expression e = new OrExpression(x, y);
        e = new OrExpression(e, z);
        return e;
    }

    // contains(a, b) means "does b contain a" which seems backwards
    private Expression createContains(Expression ex1, Expression ex2, boolean pred)
    {
        Expression[] a1 = null;
        Expression[] a2 = null;
        if (ex1 instanceof Column)
        {
            Column c = (Column) ex1;
            a1 = columnToColumns(c);
        } else if (ex1 instanceof Function)
        {
            Function f = (Function) ex1;
            a1 = functionToColumns(f);
        }
        // check if we groked it
        if (a1 == null)
            throw new IllegalArgumentException("createContains: illegal argument: " + ex1);

        if (ex2 instanceof Column)
        {
            Column c = (Column) ex2;
            a2 = columnToColumns(c);
        } else if (ex2 instanceof Function)
        {
            Function f = (Function) ex2;
            a2 = functionToColumns(f);
        }
        // check if we groked it
        if (a2 == null)
            throw new IllegalArgumentException("createContains: illegal argument: " + ex2);

        // check array lengths: 3 for point and 6 for cube
        if (a1.length == 3)
            a1 = new Expression[] { a1[0], a1[0], a1[1], a1[1], a1[2], a1[2] };
        if (a2.length == 3)
            a2 = new Expression[] { a2[0], a2[0], a2[1], a2[1], a2[2], a2[2] };

        if (pred) // true: a2 contains a1
        {
            BinaryExpression xlo = new MinorThanEquals();
            BinaryExpression xhi = new MinorThanEquals();
            BinaryExpression ylo = new MinorThanEquals();
            BinaryExpression yhi = new MinorThanEquals();
            BinaryExpression zlo = new MinorThanEquals();
            BinaryExpression zhi = new MinorThanEquals();

            xlo.setLeftExpression(a2[0]);
            xlo.setRightExpression(a1[0]);
            xhi.setLeftExpression(a1[1]);
            xhi.setRightExpression(a2[1]);
            Expression x = new AndExpression(xlo, xhi);

            ylo.setLeftExpression(a2[2]);
            ylo.setRightExpression(a1[2]);
            yhi.setLeftExpression(a1[3]);
            yhi.setRightExpression(a2[3]);
            Expression y = new AndExpression(ylo, yhi);

            zlo.setLeftExpression(a2[4]);
            zlo.setRightExpression(a1[4]);
            zhi.setLeftExpression(a1[5]);
            zhi.setRightExpression(a2[5]);
            Expression z = new AndExpression(zlo, zhi);

            Expression e = new AndExpression(x, y);
            e = new AndExpression(e, z);
            return e;
        }

        // false: a2 does not contain a1
        BinaryExpression xlo = new MinorThan();
        BinaryExpression xhi = new MinorThan();
        BinaryExpression ylo = new MinorThan();
        BinaryExpression yhi = new MinorThan();
        BinaryExpression zlo = new MinorThan();
        BinaryExpression zhi = new MinorThan();

        xlo.setLeftExpression(a1[0]);
        xlo.setRightExpression(a2[0]);
        xhi.setLeftExpression(a2[1]);
        xhi.setRightExpression(a1[1]);
        Expression x = new OrExpression(xlo, xhi);

        ylo.setLeftExpression(a1[2]);
        ylo.setRightExpression(a2[2]);
        yhi.setLeftExpression(a2[3]);
        yhi.setRightExpression(a1[3]);
        Expression y = new OrExpression(ylo, yhi);

        zlo.setLeftExpression(a1[4]);
        zlo.setRightExpression(a2[4]);
        zhi.setLeftExpression(a2[5]);
        zhi.setRightExpression(a1[5]);
        Expression z = new OrExpression(zlo, zhi);

        Expression e = new OrExpression(x, y);
        e = new OrExpression(e, z);
        return e;
    }

    // convert a single geometry column to 6 bounding cube columns
    // TODO: could return xyz[3] for point column type
    private Expression[] columnToColumns(Column c)
    {
        if (isPointType(c))
            return new Expression[] { new Column(c.getTable(), c.getColumnName() + "_x"),
                    new Column(c.getTable(), c.getColumnName() + "_y"), new Column(c.getTable(), c.getColumnName() + "_z") };

        if (isShapeType(c))
            return new Expression[] {
                    // HACK ALERT: this is CVO database schema specific
                    new Column(c.getTable(), "x1"), new Column(c.getTable(), "x2"), new Column(c.getTable(), "y1"),
                    new Column(c.getTable(), "y2"), new Column(c.getTable(), "z1"), new Column(c.getTable(), "z2") };

        throw new IllegalArgumentException("column " + c.getColumnName() + " is not a geometry type");
    }

    private Expression[] centroidToColumns(Column c)
    {
        if (isPointType(c))
            return new Expression[] { new Column(c.getTable(), c.getColumnName() + "_x"),
                    new Column(c.getTable(), c.getColumnName() + "_y"), new Column(c.getTable(), c.getColumnName() + "_z") };

        if (isShapeType(c))
            return new Expression[] { new Column(c.getTable(), c.getColumnName() + "_cx"),
                    new Column(c.getTable(), c.getColumnName() + "_cx"), new Column(c.getTable(), c.getColumnName() + "_cz"), };

        throw new IllegalArgumentException("column " + c.getColumnName() + " is not a geometry type");
    }

    // convert a single geometry value (constructor) to 3 or 6 bounding cube values
    private Expression[] functionToColumns(Function f)
    {
        String fname = f.getName().toUpperCase();
        log.debug("functionToColumns: " + fname);
        Expression[] ret = null;
        if (Constants.REGION_GEOM_FUNCTIONS.contains(fname))
        {
            List expr = f.getParameters().getExpressions();

            // for simplicity/safety we will enforce all constants, all columns,
            // or all scalar geometry function calls only -- but no mix and match
            boolean fromConstants = false;
            boolean fromColumns = false;
            boolean fromFunctions = false;

            // arg0: coordinate system
            Expression e = (Expression) expr.get(0);
            String csys = null;
            if (e instanceof StringValue)
            {
                fromConstants = true;
                csys = ((StringValue) e).getValue();
            } else if (e instanceof Column)
            {
                fromColumns = true;
                log.debug("functionToColumns: TODO: check that " + e + " is really a geometry column");
                csys = "ICRS"; // all geometry is in ICRS
            } else if (e instanceof Function)
            {
                fromFunctions = true;
                Function ef = (Function) e;
                if (Constants.COORDSYS.equals(ef.getName().toUpperCase()))
                {
                    Expression cse = checkRegionFunction(ef);
                    if (cse != null)
                        csys = ((StringValue) cse).getValue();
                    else
                        throw new IllegalArgumentException("functionToColumns: first argument must be a coordinate system, found "
                                + e);
                } else
                    throw new IllegalArgumentException("functionToColumns: first argument must be a coordinate system, found " + e);
            } else
                throw new IllegalArgumentException("functionToColumns: first arg must be coord sys string, found " + e);

            log.debug("fromConstants: " + fromConstants + ", fromColumns: " + fromColumns + ", fromFunctions: " + fromFunctions);

            if (Constants.POINT.equals(fname) || Constants.CIRCLE.equals(fname))
            {
                if (Constants.POINT.equals(fname) && expr.size() != 3)
                    throw new IllegalArgumentException("POINT: requires 3 arguments");

                if (Constants.CIRCLE.equals(fname) && expr.size() != 4)
                    throw new IllegalArgumentException("CIRCLE: requires 4 arguments");

                // arg1 and arg2: coordinates
                Expression x = (Expression) expr.get(1);
                Expression y = (Expression) expr.get(2);
                if (fromConstants)
                {
                    try
                    {
                        /*
                         * TODO:sz GeomUtil should not be used. 2009-10-27 Commented out to pass compilation.
                         * 
                         * double[] longlat = new double[] { toDouble(x), toDouble(y) }; double[] icrs = toICRS(csys, longlat);
                         * double[] xyz = GeomUtil.toUnitSphere(icrs[0], icrs[1]);
                         * 
                         * if (Constants.CIRCLE.equals(fname)) { Expression r = (Expression) expr.get(3); double rad =
                         * Math.toRadians(toDouble(r)); // radius ~ arc length on unit sphere
                         * 
                         * ret = new Expression[6]; for (int i = 0; i < ret.length; i++) ret[i] = new DoubleValue("0.0");
                         * ((DoubleValue) ret[0]).setValue(xyz[0] - rad); ((DoubleValue) ret[1]).setValue(xyz[0] + rad);
                         * ((DoubleValue) ret[2]).setValue(xyz[1] - rad); ((DoubleValue) ret[3]).setValue(xyz[1] + rad);
                         * ((DoubleValue) ret[4]).setValue(xyz[2] - rad); ((DoubleValue) ret[5]).setValue(xyz[2] + rad); } else { //
                         * POINT ret = new Expression[3]; for (int i = 0; i < ret.length; i++) ret[i] = new DoubleValue("0.0");
                         * ((DoubleValue) ret[0]).setValue(xyz[0]); ((DoubleValue) ret[1]).setValue(xyz[1]); ((DoubleValue)
                         * ret[2]).setValue(xyz[2]); }
                         */
                    } catch (ClassCastException cex)
                    {
                        throw new UnsupportedOperationException(
                                "mixing columns, constants, and functions in geometry constructor: " + f);
                    }
                }
                if (fromColumns) // needed when coordinates are stored in separate columns
                {
                    try
                    {
                        Column cx = (Column) x;
                        Column cy = (Column) y;
                        // log.debug("functionToColumns: TODO: check that " + cx + " is really a coordinate column");
                        // log.debug("functionToColumns: TODO: check that " + cy + " is really a coordinate column");
                        throw new UnsupportedOperationException("functionToColumns: long/lat -> XYZ columns");
                    } catch (ClassCastException cex)
                    {
                        throw new UnsupportedOperationException(
                                "mixing columns, constants, and functions in geometry constructor: " + f);
                    }
                }
                if (fromFunctions) // needed when coordinates are extracted from a point | centroid
                {
                    try
                    {
                        Function fx = (Function) x; // CVAL1
                        Function fy = (Function) y; // CVAL2
                        if (Constants.COORD1.equals(fx.getName().toUpperCase())
                                && Constants.COORD2.equals(fy.getName().toUpperCase()))
                        {
                            Expression ex = getSingleArg(fx);
                            Expression ey = getSingleArg(fy);
                            // allow only the same geometry column or CENTROID() thereof
                            if (ex instanceof Column && ey instanceof Column)
                            {
                                // CVAL1(pos),CVAL2(pos)
                                Column cx = (Column) ex;
                                Column cy = (Column) ey;
                                log.debug("checking if " + cx + " == " + cy);
                                if (cx.getWholeColumnName().equals(cy.getWholeColumnName()))
                                {
                                    // TODO: check for CIRCLE and radius
                                    ret = columnToColumns(cx);
                                } else
                                    throw new UnsupportedOperationException("mxing coordinates from different region columns");
                            } else if (ex instanceof Function && ey instanceof Function)
                            {
                                // CVAL1(CENTROID(col1)) and CVAL2(CENTROID(col2)) is only legal possibility
                                Function cx = (Function) ex;
                                Function cy = (Function) ey;
                                if (!Constants.CENTROID.equals(cx.getName().toUpperCase()))
                                    throw new IllegalArgumentException("illegal argument for " + fx);
                                if (!Constants.CENTROID.equals(cy.getName().toUpperCase()))
                                    throw new IllegalArgumentException("illegal argument for " + fy);

                                Expression ex2 = getSingleArg(cx);
                                Expression ey2 = getSingleArg(cy);

                                Column cx2 = (Column) ex2;
                                Column cy2 = (Column) ey2;
                                if (cx2.getWholeColumnName().equals(cy2.getWholeColumnName()))
                                {
                                    // both funcs refer to a single column
                                    return centroidToColumns(cx2);
                                }
                            }
                        }

                    } catch (ClassCastException cex)
                    {
                        throw new UnsupportedOperationException(
                                "mixing columns, constants, and functions in geometry constructor: " + f);
                    }
                }
            }
            if (Constants.POLYGON.equals(fname))
            {
                throw new UnsupportedOperationException("POLYGON");
            }
            if (Constants.REGION.equals(fname))
            {
                throw new UnsupportedOperationException("REGION");
            }
        }
        return ret;
    }

    private double toDouble(Expression e)
    {
        if (e instanceof LongValue)
            return (double) ((LongValue) e).getValue();
        if (e instanceof DoubleValue)
            return ((DoubleValue) e).getValue();
        throw new ClassCastException();
    }

    // TODO: transform coordinate values to ICRS
    private double[] toICRS(String csys, double[] longlat)
    {
        if ("ICRS".equalsIgnoreCase(csys))
            return longlat;

        throw new UnsupportedOperationException("converting " + csys + " -> ICRS");
    }

    // TODO: implement these using table metadata
    private boolean isPointType(Column c)
    {
        return c.getColumnName().equals("position");
    }

    private boolean isShapeType(Column c)
    {
        return c.getColumnName().equals("shape");
    }

}
