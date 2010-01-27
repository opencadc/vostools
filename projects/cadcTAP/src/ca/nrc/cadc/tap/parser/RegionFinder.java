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
*  $Revision: 1 $
*
************************************************************************
*/

package ca.nrc.cadc.tap.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.InverseExpression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;

import org.apache.log4j.Logger;

import ca.nrc.cadc.tap.parser.navigator.SelectNavigator;

/**
 * This visitor finds all occurances of ADQL geometry constructs. The default
 * implementations of the protected <code>handle</code> methods throw an
 * UnsupportedOperationException so this visitor can be used as-is to detect
 * the presence of ADQL geometry constructs in the query.
 * 
 * @author pdowler
 */
public class RegionFinder extends SelectNavigator
{
    private static Logger log = Logger.getLogger(RegionFinder.class);

    public static String ICRS = "ICRS GEOCENTER";

    // ADQL region functions
    public static String CONTAINS = "CONTAINS";
    public static String INTERSECTS = "INTERSECTS";
    public static String BOX = "BOX";
    public static String POINT = "POINT";
    public static String CIRCLE = "CIRCLE";
    public static String POLYGON = "POLYGON";
    public static String REGION = "REGION";
    public static String AREA = "AREA";
    public static String CENTROID = "CENTROID";
    public static String COORDSYS = "COORDSYS";
    public static String COORD1 = "COORD1";
    public static String COORD2 = "COORD2";

    public RegionFinder()
    {
    }

    /**
     * Overwrite method in super class SelectNavigator.  
     * It navigates all parts of the select statement,
     * trying to locate all occurance of region functions.
     * 
     */
    @SuppressWarnings("unchecked")
    public void visit(PlainSelect plainSelect)
    {
        log.debug("visit(PlainSelect): " + plainSelect);

        // Visiting select items
        ListIterator i = plainSelect.getSelectItems().listIterator();
        while (i.hasNext())
        {
            Object obj = i.next();
            if (obj instanceof SelectExpressionItem)
            {
                SelectExpressionItem s = (SelectExpressionItem) obj;
                Expression ex = s.getExpression();
                Expression implExpression = convertToImplementation(ex);
                s.setExpression(implExpression);
            }
        }
        
        List<Join> joins = plainSelect.getJoins();
        if (joins != null)
        {
            for (Join join : joins)
            {
                Expression e = join.getOnExpression();
                Expression implExpression = convertToImplementation(e);
                log.debug("PlainSelect/JOIN: replacing " + e + " with " + implExpression);
                join.setOnExpression(implExpression);
            }
        }

        if (plainSelect.getWhere() != null)
        {
            Expression e = plainSelect.getWhere();
            Expression implExpression = convertToImplementation(e);
            log.debug("PlainSelect/WHERE: replacing " + e + " with " + implExpression);
            plainSelect.setWhere(implExpression);
        }

        if (plainSelect.getHaving() != null)
        {
            Expression e = plainSelect.getHaving();
            Expression implExpression = convertToImplementation(e);
            log.debug("PlainSelect/HAVING: replacing " + e + " with " + implExpression);
            plainSelect.setHaving(implExpression);
        }

        log.debug("visit(PlainSelect) done");
    }

    /**
     * Convert an expression and all parameters of it, 
     * using provided implementation in the sub-class.
     * 
     * @param Expression
     * @return Expression converted by implementation 
     */
    public Expression convertToImplementation(Expression adqlExpr)
    {
        log.debug("convertToImplementation(Expression): " + adqlExpr);

        Expression implExpr = adqlExpr;

        if (adqlExpr instanceof Function)
        {
            Function adqlFunction = (Function) adqlExpr;
            
            // Convert parameters of the function first.
            ExpressionList adqlExprList = adqlFunction.getParameters();
            ExpressionList implExprList = convertToImplementation(adqlExprList);
            adqlFunction.setParameters(implExprList); // method in Function

            implExpr = convertToImplementation(adqlFunction);
        } else if (adqlExpr instanceof BinaryExpression)
        {
            BinaryExpression expr1 = (BinaryExpression) adqlExpr;
            Expression left = expr1.getLeftExpression();
            Expression right = expr1.getRightExpression();

            Expression left2 = convertToImplementation(left);
            Expression right2 = convertToImplementation(right);

            expr1.setLeftExpression(left2);
            expr1.setRightExpression(right2);
            implExpr = expr1;

            implExpr = handleRegionPredicate((BinaryExpression) implExpr);
        } else if (adqlExpr instanceof InverseExpression)
        {
            InverseExpression expr1 = (InverseExpression) adqlExpr;
            Expression child = expr1.getExpression();
            Expression child2 = convertToImplementation(child);
            expr1.setExpression(child2);
            implExpr = expr1;
        } else if (adqlExpr instanceof Parenthesis)
        {
            Parenthesis expr1 = (Parenthesis) adqlExpr;
            Expression child = expr1.getExpression();
            Expression child2 = convertToImplementation(child);
            expr1.setExpression(child2);
            implExpr = expr1;
        }
        return implExpr;
    }

    /**
     * Convert a list of expressions and all parameters of them 
     * using provided implementation in the sub-class.
     * 
     * @param Expression List
     * @return converted expression list
     */
    @SuppressWarnings("unchecked")
    public ExpressionList convertToImplementation(ExpressionList adqlExprList)
    {
        List<Expression> adqlExprs = adqlExprList.getExpressions();
        List<Expression> implExprs = new ArrayList<Expression>();
        Expression e1 = null;
        Expression e2 = null;

        for (int i = 0; i < adqlExprs.size(); i++)
        {
            e1 = adqlExprs.get(i);
            e2 = convertToImplementation(e1);
            implExprs.add(e2);
        }
        return new ExpressionList(implExprs);
    }

    /**
     * If the function is an ADQL Function,
     * convert it using implementation in sub-class.
     * 
     * @param adqlFunction
     * @return converted expression
     */
    public Expression convertToImplementation(Function adqlFunction)
    {
        log.debug("convertToImplementation(Function): " + adqlFunction);

        Expression implExpr = adqlFunction;
        String fname = adqlFunction.getName().toUpperCase();

        if (AREA.equalsIgnoreCase(fname))
        {
            implExpr = handleArea(adqlFunction);
        } else if (BOX.equalsIgnoreCase(fname))
        {
            implExpr = handleBox(adqlFunction);
        } else if (CENTROID.equalsIgnoreCase(fname))
        {
            implExpr = handleCentroid(adqlFunction);
        } else if (CIRCLE.equalsIgnoreCase(fname))
        {
            implExpr = handleCircle(adqlFunction);
        } else if (CONTAINS.equalsIgnoreCase(fname))
        {
            implExpr = handleContains(adqlFunction);
        } else if (COORD1.equalsIgnoreCase(fname))
        {
            implExpr = handleCoord1(adqlFunction);
        } else if (COORD2.equalsIgnoreCase(fname))
        {
            implExpr = handleCoord2(adqlFunction);
        } else if (COORDSYS.equalsIgnoreCase(fname))
        {
            implExpr = handleCoordSys(adqlFunction);
        } else if (INTERSECTS.equalsIgnoreCase(fname))
        {
            implExpr = handleIntersects(adqlFunction);
        } else if (POINT.equalsIgnoreCase(fname))
        {
            implExpr = handlePoint(adqlFunction);
        } else if (POLYGON.equalsIgnoreCase(fname))
        {
            implExpr = handlePolygon(adqlFunction);
        } else if (REGION.equalsIgnoreCase(fname))
        {
            implExpr = handleRegion(adqlFunction);
        }
        return implExpr;
    }

    /**
    * This method is called when a REGION PREDICATE function is one of the arguments in a binary expression, 
    * and after the direct function convertion.
    * 
    * Supported functions: CINTAINS, INTERSECTS
    * 
    * Examples:
    * 
     * CONTAINS() = 0 
     * CONTAINS() = 1 
     * 1 = CONTAINS() 
     * 0 = CONTAINS()
     * 
    * @param biExpr the binary expression which one side is a converted region function
    */
    protected Expression handleRegionPredicate(BinaryExpression biExpr)
    {
        throw new UnsupportedOperationException("Region predicate not supported");
    }

    /**
     * This method is called when a CONTAINS is found outside of a predicate.
     * This could occurr if the query had CONTAINS(...) in the select list or as
     * part of an arithmetic expression or aggregate function (since CONTAINS 
     * returns a numeric value). 
     * 
     * @param ex the CONTAINS expression
     */
    protected Expression handleContains(Function adqlFunction)
    {
        throw new UnsupportedOperationException("CONTAINS not supported");
    }

    /**
     * This method is called when a INTERSECTS is found outside of a predicate.
     * This could occurr if the query had INTERSECTS(...) in the select list or as
     * part of an arithmetic expression or aggregate function (since INTERSECTS 
     * returns a numeric value). 
     * 
     * @param ex the CONTAINS expression
     */
    protected Expression handleIntersects(Function adqlFunction)
    {
        throw new UnsupportedOperationException("INTERSECTS not supported");
    }

    /**
     * This method is called when a POINT geometry value is found.
     * 
     * @param ex the POINT expression
     */
    protected Expression handlePoint(Function adqlFunction)
    {
        throw new UnsupportedOperationException("POINT not supported");
    }

    /**
     * This method is called when a CIRCLE geometry value is found.
     * 
     * @param ex the CIRCLE expression
     */
    protected Expression handleCircle(Function adqlFunction)
    {
        throw new UnsupportedOperationException("CIRCLE not supported");
    }

    /**
     * This method is called when a BOX geometry value is found.
     * 
     * @param ex the BOX expression
     */
    protected Expression handleBox(Function adqlFunction)
    {
        throw new UnsupportedOperationException("BOX not supported");
    }

    /**
     * This method is called when a POLYGON geometry value is found.
     * 
     * @param ex the POLYGON expression
     */
    protected Expression handlePolygon(Function adqlFunction)
    {
        throw new UnsupportedOperationException("POLYGON not supported");
    }

    /**
     * This method is called when a REGION geometry value is found.
     * 
     * @param ex the REGION expression
     */
    protected Expression handleRegion(Function adqlFunction)
    {
        throw new UnsupportedOperationException("REGION not supported");
    }

    /**
     * This method is called when the CENTROID function is found.
     * 
     * @param ex the CENTROID expression
     */
    protected Expression handleCentroid(Function adqlFunction)
    {
        throw new UnsupportedOperationException("CENTROID not supported");
    }

    /**
     * This method is called when AREA function is found.
     * 
     * @param ex the AREA expression
     */
    protected Expression handleArea(Function adqlFunction)
    {
        throw new UnsupportedOperationException("AREA not supported");
    }

    /**
     * This method is called when COORD1 function is found.
     * 
     * @param ex the COORD1 expression
     */
    protected Expression handleCoord1(Function adqlFunction)
    {
        throw new UnsupportedOperationException("COORD1 not supported");
    }

    /**
     * This method is called when COORD2 function is found.
     * 
     * @param ex the COORD2 expression
     */
    protected Expression handleCoord2(Function adqlFunction)
    {
        throw new UnsupportedOperationException("COORD2 not supported");
    }

    /**
     * This method is called when COORDSYS function is found.
     * 
     * @param ex the COORDSYS expression
     */
    protected Expression handleCoordSys(Function adqlFunction)
    {
        throw new UnsupportedOperationException("COORDSYS not supported");
    }
}
