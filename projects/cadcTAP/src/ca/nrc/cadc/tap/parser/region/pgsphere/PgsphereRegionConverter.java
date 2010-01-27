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

import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;

import org.apache.log4j.Logger;

import ca.nrc.cadc.tap.parser.ParserUtil;
import ca.nrc.cadc.tap.parser.RegionFinder;
import ca.nrc.cadc.tap.parser.region.PredicateFunction;
import ca.nrc.cadc.tap.parser.region.pgsphere.function.Center;
import ca.nrc.cadc.tap.parser.region.pgsphere.function.Contains;
import ca.nrc.cadc.tap.parser.region.pgsphere.function.ContainsNot;
import ca.nrc.cadc.tap.parser.region.pgsphere.function.Coordsys;
import ca.nrc.cadc.tap.parser.region.pgsphere.function.Intersects;
import ca.nrc.cadc.tap.parser.region.pgsphere.function.IntersectsNot;
import ca.nrc.cadc.tap.parser.region.pgsphere.function.Lat;
import ca.nrc.cadc.tap.parser.region.pgsphere.function.Longitude;
import ca.nrc.cadc.tap.parser.region.pgsphere.function.Scircle;
import ca.nrc.cadc.tap.parser.region.pgsphere.function.Spoint;
import ca.nrc.cadc.tap.parser.region.pgsphere.function.Spoly;

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
    protected Expression handleRegionPredicate(BinaryExpression binaryExpression)
    {
        Expression rtn = binaryExpression;
        Expression left = binaryExpression.getLeftExpression();
        Expression right = binaryExpression.getRightExpression();

        boolean proceed = false;
        long value = 0;
        PredicateFunction predicateFunc = null;
        if ((binaryExpression instanceof EqualsTo))
        {
            if (isPredicate(left) && ParserUtil.isBinaryValue(right))
            {
                proceed = true;
                value = ((LongValue) right).getValue();
                predicateFunc = (PredicateFunction) left;
            } else if (ParserUtil.isBinaryValue(left) && isPredicate(right))
            {
                proceed = true;
                value = ((LongValue) left).getValue();
                predicateFunc = (PredicateFunction) right;
            }
        }

        if (proceed)
            rtn = (Expression) ((value == 1) ? predicateFunc : predicateFunc.negate());
        return rtn;
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
        Contains pgsFunc = new Contains(adqlFunction);
        return pgsFunc;
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
        Intersects pgsFunc = new Intersects(adqlFunction);
        return pgsFunc;
    }

    /**
     * This method is called when a POINT geometry value is found.
     * 
     * @param ex the POINT expression
     */
    protected Expression handlePoint(Function adqlFunction)
    {
        Spoint pgsFunc = new Spoint(adqlFunction);
        return pgsFunc;
    }

    /**
     * This method is called when a CIRCLE geometry value is found.
     * 
     * @param ex the CIRCLE expression
     */
    protected Expression handleCircle(Function adqlFunction)
    {
        Scircle pgsFunc = new Scircle(adqlFunction);
        return pgsFunc;
    }

    /**
     * This method is called when a POLYGON geometry value is found.
     * 
     * @param ex the POLYGON expression
     */
    protected Expression handlePolygon(Function adqlFunction)
    {
        Spoly pgsFunc = new Spoly(adqlFunction);
        return pgsFunc;
    }

    /**
     * This method is called when the CENTROID function is found.
     * 
     * @param ex the CENTROID expression
     */
    protected Expression handleCentroid(Function adqlFunction)
    {
        Center pgsFunc = new Center(adqlFunction);
        return pgsFunc;
    }

    /**
     * This method is called when COORD1 function is found.
     * 
     * @param ex the COORD1 expression
     */
    protected Expression handleCoord1(Function adqlFunction)
    {
        Longitude pgsFunc = new Longitude(adqlFunction);
        return pgsFunc;
    }

    /**
     * This method is called when COORD2 function is found.
     * 
     * @param ex the COORD2 expression
     */
    protected Expression handleCoord2(Function adqlFunction)
    {
        Lat pgsFunc = new Lat(adqlFunction);
        return pgsFunc;
    }

    /**
     * This method is called when COORDSYS function is found.
     * 
     * @param ex the COORDSYS expression
     */
    protected Expression handleCoordSys(Function adqlFunction)
    {
        Coordsys pgsFunc = new Coordsys(adqlFunction);
        return pgsFunc;
    }

    /**
     * Check whether the parameter is a predicate function.
     * 
     * @param expr
     * @return
     */
    protected boolean isPredicate(Expression expr)
    {
        return (expr instanceof PredicateFunction);
    }
}
