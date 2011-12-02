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

package ca.nrc.cadc.tap.parser.region.pgsphere.function;

import java.util.ArrayList;
import java.util.List;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import ca.nrc.cadc.stc.CoordPair;
import ca.nrc.cadc.stc.Position;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.Parenthesis;
import org.apache.log4j.Logger;

/**
 * the PgSphere implementation of ADQL function POINT.
 * 
 * @author zhangsa
 * 
 */
public class Spoint extends Function
{
    private static final Logger log = Logger.getLogger(Spoint.class);

    private Expression coordsys;
    private Expression ra;
    private Expression dec;
    private boolean isOperand;

    public Spoint(Expression coordsys, Expression ra, Expression dec)
    {
        super();
        this.coordsys = coordsys;
        this.ra = ra;
        this.dec = dec;
        convertParameters();
    }

    public Spoint(Position position)
    {
        super();
        CoordPair coordPair = position.getCoordPair();
        ra = new DoubleValue(Double.toString(coordPair.getX()));
        dec = new DoubleValue(Double.toString(coordPair.getY()));
        convertParameters();
    }

    public void setOperand(boolean isOperand)
    {
        this.isOperand = isOperand;
    }

    public boolean isOperand()
    {
        return isOperand;
    }

    public String toVertex()
    {
         // force interpetation in degrees
        return "(" + ra + "d," + dec + "d)";
    }

    protected void convertParameters()
    {
        // RA
        List<Expression> longExp = new ArrayList<Expression>();
        longExp.add(ra);

        ExpressionList longParams = new ExpressionList();
        longParams.setExpressions(longExp);

        Function longFunc = new Function();
        longFunc.setName("radians");
        longFunc.setParameters(longParams);

        // DEC
        List<Expression> latExp = new ArrayList<Expression>();
        latExp.add(dec);

        ExpressionList latParams = new ExpressionList();
        latParams.setExpressions(latExp);

        // Radius
        Function latFunc = new Function();
        latFunc.setName("radians");
        latFunc.setParameters(latParams);

        // Spoint
        List<Expression> expressions = new ArrayList<Expression>();
        expressions.add(longFunc);
        expressions.add(latFunc);

        ExpressionList parameters = new ExpressionList();
        parameters.setExpressions(expressions);

        setName("spoint");
        setParameters(parameters);
    }

}
