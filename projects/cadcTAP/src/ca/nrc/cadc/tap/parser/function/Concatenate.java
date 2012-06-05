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

package ca.nrc.cadc.tap.parser.function;

import ca.nrc.cadc.tap.parser.OperatorVisitor;
import ca.nrc.cadc.tap.parser.region.pgsphere.function.Spoint;
import java.util.List;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitor;
import org.apache.log4j.Logger;

/**
 * Expression for a SELECT to concatenate multiple columns into a single column.
 *
 * @author jburke
 */
public class Concatenate extends BinaryExpression
{
    private static Logger log = Logger.getLogger(Concatenate.class);

    private static final String DEFAULT_SEPARATOR = "";

    protected String operator;
    protected String separator;

    /**
     * Concatenate the list of expressions with the operator
     * and use the default separator between the operator and the expressions.
     *
     * @param operator The operator between expressions.
     * @param expressions The expressions to concatenate togeather.
     */
    public Concatenate(String operator, List<Expression> expressions)
    {
        this(operator, expressions, DEFAULT_SEPARATOR);
    }

    /**
     * Concatenate the list of expressions with the operator
     * and the separator between the operator and the expressions.
     *
     * @param operator The operator between expressions.
     * @param expressions The expressions to concatenate togeather.
     * @param separator The separator between the operator and the expressions.
     */
    public Concatenate(String operator, List<Expression> expressions, String separator)
    {
        super();

        // Must be more than one expression in the List.
        if (expressions.size() < 2)
            throw new IllegalArgumentException("Operator requires 2 expressions, found " + expressions.size());

        this.operator = operator;
        this.separator = separator;

        // If an expression is an Spoint, flag the Spoint as an operand.
        Expression left = expressions.get(0);
        if (left instanceof Spoint)
            ((Spoint) left).setOperand(true);
        setLeftExpression(left);

        if(expressions.size() == 2)
        {
            Expression right = expressions.get(1);
            if (right instanceof Spoint)
                ((Spoint) right).setOperand(true);
            setRightExpression(right);
        }
        else
        {
            setRightExpression(new Concatenate(operator, expressions.subList(1, expressions.size()), separator));
        }
    }

    /**
     *
     * @return The operator between expressions.
     */
    public String getOperator()
    {
        return operator;
    }

    /**
     *
     * @return The separator used between the operator and the expressions.
     */
    public String getSeparator()
    {
        return separator;
    }

    /**
     *
     * @return The operator.
     */
    @Override
    public String getStringExpression()
    {
        return getOperator();
    }

    /**
     * Concatenate can only accept a QueryParser or one of its sub-classes.
     *
     * @param expressionVisitor
     */
    public void accept(ExpressionVisitor expressionVisitor)
    {
        log.debug("accept(" + expressionVisitor.getClass().getSimpleName() + "): " + this);
        if (expressionVisitor instanceof OperatorVisitor) // visitor pattern extension
            ((OperatorVisitor) expressionVisitor).visit(this);
        else
        {
            getLeftExpression().accept(expressionVisitor);
            getRightExpression().accept(expressionVisitor);
        }
    }
    
}
