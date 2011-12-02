/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.nrc.cadc.tap.parser;

import ca.nrc.cadc.tap.parser.region.pgsphere.function.Spoint;
import ca.nrc.cadc.tap.parser.region.pgsphere.function.Spoly;
import java.util.List;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.statement.select.SelectVisitor;
import org.apache.log4j.Logger;

/**
 * De-parser for PostgreSQL PGSphere functions.
 * 
 * @author jburke
 */
public class PgsphereDeParser extends QueryDeParser
{
    private static Logger log = Logger.getLogger(PgsphereDeParser.class);

    public PgsphereDeParser(SelectVisitor selectVisitor, StringBuffer buffer)
    {
        super(selectVisitor, buffer);
    }

    /**
     * De-parses PGSphere functions, else passes the function
     * to the super class for de-parsing.
     *
     * @param function
     */
    @SuppressWarnings("unchecked")
    @Override
    public void visit(Function function)
    {
        log.debug("visit(" + function.getClass().getSimpleName() + "): " + function);

        /**
         * De-parse a spoint, wrapping a cast around the spoint if
         * the spoint is used as an operand in another function.
         */
        if(function instanceof Spoint)
        {
            Spoint spoint = (Spoint) function;
            if (spoint.isOperand())
            {
                buffer.append("cast(");
                super.visit(spoint);
                buffer.append(" as scircle)");
            }
            else
            {
                super.visit(spoint);
            }
        }

        /**
         * De-parse a spoly.
         */
        else if(function instanceof Spoly)
        {
            Spoly spoly = (Spoly) function;
            buffer.append(spoly.getName());
            buffer.append(" '{");
            List<Expression> expressions = spoly.getParameters().getExpressions();
            String deli = "";
            for (Expression expression : expressions)
            {
                buffer.append(deli);
                deli = ",";
                if (expression instanceof StringValue)
                {
                    StringValue stringValue = (StringValue) expression;
                    stringValue.accept(this);
                }
                else if (expression instanceof Spoint)
                {
                    Spoint spoint = (Spoint) expression;
                    buffer.append(spoint.toVertex());
                }
            }
            buffer.append("}'");
        }
        else
        {
            super.visit(function);
        }
    }

}
