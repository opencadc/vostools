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
import java.util.List;

import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.InverseExpression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;

import org.apache.log4j.Logger;

import ca.nrc.cadc.tap.parser.adql.impl.postgresql.pgsphere.Constants;
import ca.nrc.cadc.tap.parser.adql.impl.postgresql.pgsphere.PgsphereUtil;
import ca.nrc.cadc.tap.parser.adql.impl.postgresql.pgsphere.function.Center;
import ca.nrc.cadc.tap.parser.adql.impl.postgresql.pgsphere.function.Contains;
import ca.nrc.cadc.tap.parser.adql.impl.postgresql.pgsphere.function.ContainsNot;
import ca.nrc.cadc.tap.parser.adql.impl.postgresql.pgsphere.function.Coordsys;
import ca.nrc.cadc.tap.parser.adql.impl.postgresql.pgsphere.function.Intersects;
import ca.nrc.cadc.tap.parser.adql.impl.postgresql.pgsphere.function.IntersectsNot;
import ca.nrc.cadc.tap.parser.adql.impl.postgresql.pgsphere.function.Lat;
import ca.nrc.cadc.tap.parser.adql.impl.postgresql.pgsphere.function.Longitude;
import ca.nrc.cadc.tap.parser.adql.impl.postgresql.pgsphere.function.Scircle;
import ca.nrc.cadc.tap.parser.adql.impl.postgresql.pgsphere.function.Spoint;
import ca.nrc.cadc.tap.parser.adql.impl.postgresql.pgsphere.function.Spoly;

/**
 * @author zhangsa
 * 
 */
public class ConverterUtil
{
    protected static Logger log = Logger.getLogger(ConverterUtil.class);

    @SuppressWarnings({ "unchecked"})
    public static ExpressionList convertToPgsphere(ExpressionList adqlExprList)
    {
        List<Expression> adqlExprs = adqlExprList.getExpressions();
        List<Expression> pgsExprs = new ArrayList<Expression>();
        Expression e1 = null;
        Expression e2 = null;

        for (int i = 0; i < adqlExprs.size(); i++)
        {
            e1 = adqlExprs.get(i);
            e2 = convertToPgsphere(e1);
            pgsExprs.add(e2);
        }
        return new ExpressionList(pgsExprs);
    }

    public static Expression convertToPgsphere(Function adqlFunction)
    {
        Expression pgsExpr = adqlFunction;
        log.debug("convertToPgsphere(Function): " + adqlFunction);
        adqlFunction.getParameters();

        if (PgsphereUtil.isAdqlRegion(adqlFunction))
        {
            String fname = adqlFunction.getName().toUpperCase();

            if (Constants.POINT.equalsIgnoreCase(fname))
            {
                Spoint pgsFunc = new Spoint(adqlFunction);
                pgsExpr = pgsFunc;
            } else if (Constants.CIRCLE.equalsIgnoreCase(fname))
            {
                Scircle pgsFunc = new Scircle(adqlFunction);
                pgsExpr = pgsFunc;
            } else if (Constants.POLYGON.equalsIgnoreCase(fname))
            {
                Spoly pgsFunc = new Spoly(adqlFunction);
                pgsExpr = pgsFunc;
            } else if (Constants.CONTAINS.equalsIgnoreCase(fname))
            {
                Contains pgsFunc = new Contains(adqlFunction);
                pgsExpr = pgsFunc;
            } else if (Constants.INTERSECTS.equalsIgnoreCase(fname))
            {
                Intersects pgsFunc = new Intersects(adqlFunction);
                pgsExpr = pgsFunc;
            } else if (Constants.CENTROID.equalsIgnoreCase(fname))
            {
                Center pgsFunc = new Center(adqlFunction);
                pgsExpr = pgsFunc;
            } else if (Constants.COORDSYS.equalsIgnoreCase(fname))
            {
                Coordsys pgsFunc = new Coordsys(adqlFunction);
                pgsExpr = pgsFunc;
            } else if (Constants.COORD1.equalsIgnoreCase(fname))
            {
                Longitude pgsFunc = new Longitude(adqlFunction);
                pgsExpr = pgsFunc;
            } else if (Constants.COORD2.equalsIgnoreCase(fname))
            {
                Lat pgsFunc = new Lat(adqlFunction);
                pgsExpr = pgsFunc;
            }
        }
        return pgsExpr;
    }

    /**
     * @param ex
     * @return
     */
    public static Expression convertToPgsphere(Expression adqlExpr)
    {
        log.debug("convertToPgsphere(Expression): " + adqlExpr);

        Expression pgsExpr = adqlExpr;

        if (adqlExpr instanceof Function)
        {
            Function expr1 = (Function) adqlExpr;
            pgsExpr = convertToPgsphere(expr1);
        } else if (adqlExpr instanceof BinaryExpression)
        {
            BinaryExpression expr1 = (BinaryExpression) adqlExpr;
            Expression left = expr1.getLeftExpression();
            Expression right = expr1.getRightExpression();

            Expression left2 = convertToPgsphere(left);
            Expression right2 = convertToPgsphere(right);

            expr1.setLeftExpression(left2);
            expr1.setRightExpression(right2);
            pgsExpr = expr1;

            pgsExpr = convertPredicateFunction((BinaryExpression) pgsExpr);
        } else if (adqlExpr instanceof InverseExpression)
        {
            InverseExpression expr1 = (InverseExpression) adqlExpr;
            Expression child = expr1.getExpression();
            Expression child2 = convertToPgsphere(child);
            expr1.setExpression(child2);
            pgsExpr = expr1;
        } else if (adqlExpr instanceof Parenthesis)
        {
            Parenthesis expr1 = (Parenthesis) adqlExpr;
            Expression child = expr1.getExpression();
            Expression child2 = convertToPgsphere(child);
            expr1.setExpression(child2);
            pgsExpr = expr1;
        }
        return pgsExpr;
    }

    /**
     * Supported Functions: Contains, Intersects
     * 
     * Supported Scenarios:
     * 
     * CONTAINS() = 0 
     * CONTAINS() = 1 
     * 1 = CONTAINS() 
     * 0 = CONTAINS()
     * 
     * @param pgsExpr
     */
    private static Expression convertPredicateFunction(BinaryExpression pgsExpr)
    {
        Expression rtn = pgsExpr;
        Expression left = pgsExpr.getLeftExpression();
        Expression right = pgsExpr.getRightExpression();

        boolean proceed = false;
        long value = 0;
        Expression expr = null;
        if ((pgsExpr instanceof EqualsTo))
        {
            if (isPredicate(left) && isBinaryValue(right))
            {
                proceed = true;
                value = ((LongValue) right).getValue();
                expr = left;
            } else if (isBinaryValue(left) && isPredicate(right))
            {
                proceed = true;
                value = ((LongValue) left).getValue();
                expr = right;
            }
        }

        if (proceed)
            rtn = (value == 1) ? expr : negate(expr);
        return rtn;
    }

    public static boolean isPredicate(Expression expr)
    {
        return (expr instanceof Contains || expr instanceof Intersects);
    }

    public static boolean isBinaryValue(Expression expr)
    {
        boolean rtn = false;
        if (expr instanceof LongValue)
        {
            long l = ((LongValue) expr).getValue();
            rtn = ((l == 0) || (l == 1));
        }
        return rtn;
    }

    public static Expression negate(Expression expr)
    {
        Expression rtn = null;
        if (expr instanceof Contains)
            rtn = new ContainsNot((Contains) expr);
        else if (expr instanceof Intersects)
            rtn = new IntersectsNot((Intersects) expr);
        return rtn;
    }
}
