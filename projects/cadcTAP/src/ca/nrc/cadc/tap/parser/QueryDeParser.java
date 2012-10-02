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

package ca.nrc.cadc.tap.parser;

import ca.nrc.cadc.tap.parser.function.Concatenate;
import ca.nrc.cadc.tap.parser.function.Operator;
import java.util.Iterator;
import java.util.List;
import net.sf.jsqlparser.expression.CaseExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.statement.select.SelectVisitor;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import org.apache.log4j.Logger;

/**
 * QueryParser overrides the JSQLParser ExpressionDeParser to support
 * de-parsing of cadcTAP Expressions.
 * 
 * @author jburke
 */
public class QueryDeParser extends ExpressionDeParser implements OperatorVisitor
{
    private static Logger log = Logger.getLogger(QueryDeParser.class);

    public QueryDeParser(SelectVisitor selectVisitor, StringBuffer buffer)
    {
        super(selectVisitor, buffer);
    }

    /**
     * De-parse arithmetic, conditional, and relational operators.
     * 
     * @param operator
     */
    public void visit(Operator operator)
    {
        log.debug("visit(" + operator.getClass().getSimpleName() + "): " + operator);
        if (operator.isNot())
            buffer.append("NOT ");
        operator.getLeftExpression().accept(this);
        buffer.append(" ");
        buffer.append(operator.getOperator());
        buffer.append(" ");
        operator.getRightExpression().accept(this);
    }

    /**
     * De-parse the concatenation of Expressions.
     * 
     * @param concatenate
     */
    public void visit(Concatenate concatenate)
    {
        concatenate.getLeftExpression().accept(this);
        buffer.append(concatenate.getOperator());
        buffer.append("'");
        buffer.append(concatenate.getSeparator());
        buffer.append("'");
        buffer.append(concatenate.getOperator());
        concatenate.getRightExpression().accept(this);
    }

    /**
     * Overridden to provide proper whitespace when prepending a NOT.
     * 
     * @param parenthesis
     */
    @Override
    public void visit(Parenthesis parenthesis)
    {
        log.debug("visit(Parenthesis) " + parenthesis);
        if (parenthesis.isNot())
        {
            if (buffer.charAt(buffer.length() - 1) != ' ')
                buffer.append(" ");
            buffer.append("NOT ");
        }
        buffer.append("(");
        parenthesis.getExpression().accept(this);
        buffer.append(")");
    }

    /**
     * Override to add missing ELSE keyword in base class.
     * 
     * @param caseExpression
     */
    @Override
    public void visit(CaseExpression caseExpression)
    {
        log.debug("visit(" +  caseExpression.getClass().getSimpleName() + ") " + caseExpression);
        buffer.append("CASE ");
        Expression switchExp = caseExpression.getSwitchExpression();
        if( switchExp != null )
        {
            switchExp.accept(this);
        }

        List clauses = caseExpression.getWhenClauses();
        for (Iterator iter = clauses.iterator(); iter.hasNext();)
        {
            Expression exp = (Expression) iter.next();
            exp.accept(this);
        }

        Expression elseExp = caseExpression.getElseExpression();
        if( elseExp != null )
        {
            buffer.append(" ELSE ");
            elseExp.accept(this);
        }

        buffer.append(" END");
    }
}

    /**
     * The following are overridden for debugging purposes only.
     */
    /*
    @Override
    public void visit(Addition addition)
    {
        log.debug("visit(" +  addition.getClass().getSimpleName() + ") " + addition);
        super.visit(addition);
    }

    @Override
    public void visit(AndExpression andExpression)
    {
        log.debug("visit(" +  andExpression.getClass().getSimpleName() + ") " + andExpression);
        super.visit(andExpression);
    }

    @Override
    public void visit(Between between)
    {
        log.debug("visit(" +  between.getClass().getSimpleName() + ") " + between);
        super.visit(between);
    }

    @Override
    public void visit(Division division)
    {
        log.debug("visit(" +  division.getClass().getSimpleName() + ") " + division);
        super.visit(division);
    }

    @Override
    public void visit(DoubleValue doubleValue)
    {
        log.debug("visit(" +  doubleValue.getClass().getSimpleName() + ") " + doubleValue);
        super.visit(doubleValue);
    }

    @Override
    public void visit(EqualsTo equalsTo)
    {
        log.debug("visit(" +  equalsTo.getClass().getSimpleName() + ") " + equalsTo);
        super.visit(equalsTo);
    }

    @Override
    public void visit(Function function)
    {
        log.debug("visit(" +  function.getClass().getSimpleName() + ") " + function);
        super.visit(function);
    }

    @Override
    public void visit(GreaterThan greaterThan)
    {
        log.debug("visit(" +  greaterThan.getClass().getSimpleName() + ") " + greaterThan);
        super.visit(greaterThan);
    }

    @Override
    public void visit(GreaterThanEquals greaterThanEquals)
    {
        log.debug("visit(" +  greaterThanEquals.getClass().getSimpleName() + ") " + greaterThanEquals);
        super.visit(greaterThanEquals);
    }

    @Override
    public void visit(InExpression inExpression)
    {
        log.debug("visit(" +  inExpression.getClass().getSimpleName() + ") " + inExpression);
        super.visit(inExpression);
    }

    @Override
    public void visit(InverseExpression inverseExpression)
    {
        log.debug("visit(" +  inverseExpression.getClass().getSimpleName() + ") " + inverseExpression);
        super.visit(inverseExpression);
    }

    @Override
    public void visit(IsNullExpression isNullExpression)
    {
        log.debug("visit(" +  isNullExpression.getClass().getSimpleName() + ") " + isNullExpression);
        super.visit(isNullExpression);
    }

    @Override
    public void visit(JdbcParameter jdbcParameter)
    {
        log.debug("visit(" +  jdbcParameter.getClass().getSimpleName() + ") " + jdbcParameter);
        super.visit(jdbcParameter);
    }

    @Override
    public void visit(LikeExpression likeExpression)
    {
        log.debug("visit(" +  likeExpression.getClass().getSimpleName() + ") " + likeExpression);
        super.visit(likeExpression);
    }

    @Override
    public void visit(ExistsExpression existsExpression)
    {
        log.debug("visit(" +  existsExpression.getClass().getSimpleName() + ") " + existsExpression);
        super.visit(existsExpression);
    }

    @Override
    public void visit(LongValue longValue)
    {
        log.debug("visit(" +  longValue.getClass().getSimpleName() + ") " + longValue);
        super.visit(longValue);
    }

    @Override
    public void visit(MinorThan minorThan)
    {
        log.debug("visit(" +  minorThan.getClass().getSimpleName() + ") " + minorThan);
        super.visit(minorThan);
    }

    @Override
    public void visit(MinorThanEquals minorThanEquals)
    {
        log.debug("visit(" +  minorThanEquals.getClass().getSimpleName() + ") " + minorThanEquals);
        super.visit(minorThanEquals);
    }

    @Override
    public void visit(Multiplication multiplication)
    {
        log.debug("visit(" +  multiplication.getClass().getSimpleName() + ") " + multiplication);
        super.visit(multiplication);
    }

    @Override
    public void visit(NotEqualsTo notEqualsTo)
    {
        log.debug("visit(" +  notEqualsTo.getClass().getSimpleName() + ") " + notEqualsTo);
        super.visit(notEqualsTo);
    }

    @Override
    public void visit(NullValue nullValue)
    {
        log.debug("visit(" +  nullValue.getClass().getSimpleName() + ") " + nullValue);
        super.visit(nullValue);
    }

    @Override
    public void visit(OrExpression orExpression)
    {
        log.debug("visit(" +  orExpression.getClass().getSimpleName() + ") " + orExpression);
        super.visit(orExpression);
    }

    @Override
    public void visit(StringValue stringValue)
    {
        log.debug("visit(" +  stringValue.getClass().getSimpleName() + ") " + stringValue);
        super.visit(stringValue);
    }

    @Override
    public void visit(Subtraction subtraction)
    {
        log.debug("visit(" +  subtraction.getClass().getSimpleName() + ") " + subtraction);
        super.visit(subtraction);
    }

    @Override
    public void visit(SubSelect subSelect)
    {
        log.debug("visit(" +  subSelect.getClass().getSimpleName() + ") " + subSelect);
        super.visit(subSelect);
    }

    @Override
    public void visit(Column tableColumn)
    {
        log.debug("visit(" +  tableColumn.getClass().getSimpleName() + ") " + tableColumn);
        super.visit(tableColumn);
    }

    @Override
    public void visit(ExpressionList expressionList)
    {
        log.debug("visit(" +  expressionList.getClass().getSimpleName() + ") " + expressionList);
        super.visit(expressionList);
    }

    @Override
    public void visit(DateValue dateValue)
    {
        log.debug("visit(" +  dateValue.getClass().getSimpleName() + ") " + dateValue);
        super.visit(dateValue);
    }

    @Override
    public void visit(TimestampValue timestampValue)
    {
        log.debug("visit(" +  timestampValue.getClass().getSimpleName() + ") " + timestampValue);
        super.visit(timestampValue);
    }

    @Override
    public void visit(TimeValue timeValue)
    {
        log.debug("visit(" +  timeValue.getClass().getSimpleName() + ") " + timeValue);
        super.visit(timeValue);
    }

    @Override
    public void visit(WhenClause whenClause)
    {
        log.debug("visit(" +  whenClause.getClass().getSimpleName() + ") " + whenClause);
        super.visit(whenClause);
    }

    @Override
    public void visit(AllComparisonExpression allComparisonExpression)
    {
        log.debug("visit(" +  allComparisonExpression.getClass().getSimpleName() + ") " + allComparisonExpression);
        super.visit(allComparisonExpression);
    }

    @Override
    public void visit(AnyComparisonExpression anyComparisonExpression)
    {
        log.debug("visit(" +  anyComparisonExpression.getClass().getSimpleName() + ") " + anyComparisonExpression);
        super.visit(anyComparisonExpression);
    }
    */