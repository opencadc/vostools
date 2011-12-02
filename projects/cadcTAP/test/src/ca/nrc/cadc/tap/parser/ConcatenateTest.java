/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.nrc.cadc.tap.parser;

import ca.nrc.cadc.tap.parser.function.Concatenate;
import ca.nrc.cadc.util.Log4jInit;
import java.util.ArrayList;
import java.util.List;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import net.sf.jsqlparser.util.deparser.SelectDeParser;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author jburke
 */
public class ConcatenateTest
{
    private static Logger log = Logger.getLogger(ConcatenateTest.class);
    static
    {
        Log4jInit.setLevel("ca.nrc.cadc.tap.parser", Level.INFO);
    }

    public ConcatenateTest() { }

    /**
     * Test of concatenation using Operator.
     */
    @Test
    public void test()
    {
        Table table = new Table("schema", "table");
        Column columnA = new Column(table, "A");
        Column columnB = new Column(table, "B");
        Column columnC = new Column(table, "C");

        List<Expression> expressions = new ArrayList<Expression>();
        expressions.add(columnA);
        expressions.add(columnB);
        expressions.add(columnC);

        Concatenate concatenate = new Concatenate("||", expressions, "/");

        StringBuilder sb = new StringBuilder();
        sb.append("select ");
        sb.append(columnA.getWholeColumnName());
        sb.append(concatenate.getOperator());
        sb.append("'");
        sb.append(concatenate.getSeparator());
        sb.append("'");
        sb.append(concatenate.getOperator());
        sb.append(columnB.getWholeColumnName());
        sb.append(concatenate.getOperator());
        sb.append("'");
        sb.append(concatenate.getSeparator());
        sb.append("'");
        sb.append(concatenate.getOperator());
        sb.append(columnC.getWholeColumnName());
        String expResult = sb.toString();

        SelectExpressionItem expressionItem = new SelectExpressionItem();
        expressionItem.setExpression(concatenate);

        List<SelectExpressionItem> selectItems = new ArrayList<SelectExpressionItem>();
        selectItems.add(expressionItem);

        PlainSelect plainSelect = new PlainSelect();
        plainSelect.setSelectItems(selectItems);

        StringBuffer buffer = new StringBuffer();
        SelectDeParser deParser = new SelectDeParser();
        deParser.setBuffer(buffer);
        ExpressionDeParser expressionDeParser = new QueryDeParser(deParser, buffer);
        deParser.setExpressionVisitor(expressionDeParser);

        plainSelect.accept(deParser);
        String actual = deParser.getBuffer().toString().trim();

        log.debug("expected: [" + expResult + "]");
        log.debug("actual: [" + actual + "]");
        Assert.assertEquals(expResult.toLowerCase(), actual.toLowerCase());
    }

}