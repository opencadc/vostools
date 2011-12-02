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

package ca.nrc.cadc.tap.parser.region.pgsphere;

import java.util.List;

import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;

import org.apache.log4j.Logger;

import ca.nrc.cadc.stc.Box;
import ca.nrc.cadc.stc.Circle;
import ca.nrc.cadc.stc.Polygon;
import ca.nrc.cadc.stc.Position;
import ca.nrc.cadc.stc.STC;
import ca.nrc.cadc.stc.StcsParsingException;
import ca.nrc.cadc.tap.parser.ParserUtil;
import ca.nrc.cadc.tap.parser.RegionFinder;
import ca.nrc.cadc.tap.parser.function.Operator;
import ca.nrc.cadc.tap.parser.region.pgsphere.function.Center;
import ca.nrc.cadc.tap.parser.region.pgsphere.function.Lat;
import ca.nrc.cadc.tap.parser.region.pgsphere.function.Longitude;
import ca.nrc.cadc.tap.parser.region.pgsphere.function.Scircle;
import ca.nrc.cadc.tap.parser.region.pgsphere.function.Spoint;
import ca.nrc.cadc.tap.parser.region.pgsphere.function.Spoly;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;

/**
 * Convert ADQL functions into PgSphere implementation.
 * 
 * @author pdowler, zhangsa
 */
public class PgsphereRegionConverter extends RegionFinder
{
    @SuppressWarnings("unused")
    private static Logger log = Logger.getLogger(PgsphereRegionConverter.class);

    public PgsphereRegionConverter()
    {
    }

    /**
     * This method is called when a REGION PREDICATE function is one of the arguments in a binary expression, 
     * and after the direct function conversion.
     * 
     * Supported functions: CONTAINS, INTERSECTS
     * 
     * Examples:
     * 
     * CONTAINS() = 0 
     * CONTAINS() = 1 
     * 1 = CONTAINS() 
     * 0 = CONTAINS()
     *
     * Supported comparison operators are =, !=, <, >, <=, >=
     */
    @Override
    protected Expression handleRegionPredicate(BinaryExpression binaryExpression)
    {
        log.debug("handleRegionPredicate(" + binaryExpression.getClass().getSimpleName() + "): " + binaryExpression);

        if (!(binaryExpression instanceof EqualsTo ||
            binaryExpression instanceof NotEqualsTo ||
            binaryExpression instanceof MinorThan ||
            binaryExpression instanceof GreaterThan ||
            binaryExpression instanceof MinorThanEquals ||
            binaryExpression instanceof GreaterThanEquals))
            return binaryExpression;

        Expression left = binaryExpression.getLeftExpression();
        Expression right = binaryExpression.getRightExpression();

        Operator operator = null;
        long value = 0;
        if (isOperator(left) && ParserUtil.isBinaryValue(right))
        {
            operator = (Operator) left;
            value = ((LongValue) right).getValue();
        }
        else if (ParserUtil.isBinaryValue(left) && isOperator(right))
        {
            operator = (Operator) right;
            value = ((LongValue) left).getValue();
        }
        else
        {
            return binaryExpression;
        }

        if (value == 0)
            operator.negate();
        return operator;
    }

    /**
     * This method is called when a CONTAINS is found outside of a predicate.
     * This could occur if the query had CONTAINS(...) in the select list or as
     * part of an arithmetic expression or aggregate function (since CONTAINS 
     * returns a numeric value). 
     * 
     */
    @Override
    protected Expression handleContains(Expression left, Expression right)
    {
        return new Operator("@", "!@", left, right);
    }

    /**
     * This method is called when a INTERSECTS is found outside of a predicate.
     * This could occur if the query had INTERSECTS(...) in the select list or as
     * part of an arithmetic expression or aggregate function (since INTERSECTS 
     * returns a numeric value). 
     * 
     */
    @Override
    protected Expression handleIntersects(Expression left, Expression right)
    {
        return new Operator("&&", "!&&", left, right);
    }

    /**
     * This method is called when a POINT geometry value is found.
     * 
     */
    @Override
    protected Expression handlePoint(Expression coordsys, Expression ra, Expression dec)
    {
        return new Spoint(coordsys, ra, dec);
    }

    /**
     * This method is called when a CIRCLE geometry value is found.
     * 
     */
    @Override
    protected Expression handleCircle(Expression coordsys, Expression ra, Expression dec, Expression radius)
    {
        return new Scircle(coordsys, ra, dec, radius);
    }

    /**
     * This method is called when a POLYGON geometry value is found.
     * 
     */
    @Override
    protected Expression handlePolygon(List<Expression> expressions)
    {
        return new Spoly(expressions);
    }

    /**
     * This method is called when the CENTROID function is found.
     * 
     */
    @Override
    protected Expression handleCentroid(Function adqlFunction)
    {
        return new Center(adqlFunction);
    }

    /**
     * This method is called when COORD1 function is found.
     * 
     */
    @Override
    protected Expression handleCoord1(Function adqlFunction)
    {
        return new Longitude(adqlFunction);
    }

    /**
     * This method is called when COORD2 function is found.
     * 
     */
    @Override
    protected Expression handleCoord2(Function adqlFunction)
    {
        return new Lat(adqlFunction);
    }

    /**
     * This method is called when COORDSYS function is found.
     */
    @Override
    protected Expression handleCoordSys(Function adqlFunction)
    {
        return new NullValue();
    }

    protected boolean isOperator(Expression expression)
    {
        return (expression instanceof Operator);
    }
    /**
     * Convert ADQL BOX to PGS spoly.
     * 
     * Only handle BOX() with constant parameters.
     * 
     */
    @Override
    protected Expression handleBox(Function adqlFunction)
    {
        Box box = ParserUtil.convertToStcBox(adqlFunction);
        Polygon polygon = new Polygon(box);
        return new Spoly(polygon);
    }

    @Override
    protected Expression handleRegion(Function adqlFunction)
    {
        List<Expression> params = adqlFunction.getParameters().getExpressions();
        StringValue strV = (StringValue) params.get(0);
        String regionParamStr = strV.getValue();
        String[] tokens = regionParamStr.split(" ");
        String fname = tokens[0].toUpperCase();

        if (Box.NAME.equalsIgnoreCase(fname))
        {
            Box box;
            try
            {
                box = (Box) STC.parse(regionParamStr);
            }
            catch (StcsParsingException e)
            {
                throw new IllegalArgumentException(e);
            }
            Polygon polygon = new Polygon(box);
            return new Spoly(polygon);
        }
        else if (Polygon.NAME.equalsIgnoreCase(fname))
        {
            Polygon polygon;
            try
            {
                polygon = (Polygon) STC.parse(regionParamStr);
            }
            catch (StcsParsingException e)
            {
                throw new IllegalArgumentException(e);
            }
            return new Spoly(polygon);
        }
        else if (Circle.NAME.equalsIgnoreCase(fname))
        {
            Circle circle;
            try
            {
                circle = (Circle) STC.parse(regionParamStr);
            }
            catch (StcsParsingException e)
            {
                throw new IllegalArgumentException(e);
            }
            return new Scircle(circle);
        }
        else if (Position.NAME.equalsIgnoreCase(fname))
        {
            Position position;
            try
            {
                position = (Position) STC.parse(regionParamStr);
            }
            catch (StcsParsingException e)
            {
                throw new IllegalArgumentException(e);
            }
            return new Spoint(position);
        }
        else
        {
            return super.handleRegion(adqlFunction);
        }

    }
}
